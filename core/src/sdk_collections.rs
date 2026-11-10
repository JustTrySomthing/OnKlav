//! Bounded app-private collections. One atomic snapshot is the transaction boundary.
use crate::Result;
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{collections::{BTreeMap,BTreeSet},fs,path::{Path,PathBuf}};
const MAX_BYTES:u64=2*1024*1024;

#[derive(Default,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Collection {schema:u32,revision:u64,rows:BTreeMap<String,Value>,indexes:BTreeMap<String,BTreeMap<String,BTreeSet<String>>>}
fn name(v:&str)->bool{!v.is_empty()&&v.len()<=64&&v.bytes().all(|c|c.is_ascii_alphanumeric()||c==b'_'||c==b'-')}
fn id(v:&str)->bool{!v.is_empty()&&v.len()<=96&&v.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c))}
fn path(root:&Path,n:&str)->Result<PathBuf>{if !name(n){return Err("Use a collection name of1–64 letters, digits, underscores or hyphens".into())}Ok(root.join(format!("collection-{n}.json")))}
fn scalar(v:&Value)->Result<String>{if v.is_array()||v.is_object()||v.to_string().len()>1024{return Err("Index values must be bounded scalar values".into())}serde_json::to_string(v).map_err(|e|e.to_string())}
fn indexed(rows:&BTreeMap<String,Value>,fields:impl Iterator<Item=String>)->Result<BTreeMap<String,BTreeMap<String,BTreeSet<String>>>>{
 let mut result=BTreeMap::new();
 for field in fields {if !name(&field)||result.len()>=8{return Err("Use at most8 indexes with simple field names".into())}let mut index:BTreeMap<String,BTreeSet<String>>=BTreeMap::new();
  for (key,row)in rows {if let Some(value)=row.get(&field){index.entry(scalar(value)?).or_default().insert(key.clone());}}
  result.insert(field,index);
 }Ok(result)
}
fn validate(c:&Collection)->Result<()>{
 if c.schema==0||c.revision==0||c.rows.len()>2000||c.indexes.len()>8{return Err("Invalid collection metadata or row limit".into())}
 for(key,value)in &c.rows{if !id(key)||!value.is_object()||value.to_string().len()>16384{return Err("Rows require a stable ID and an object up to16KiB".into())}}
 if indexed(&c.rows,c.indexes.keys().cloned())?!=c.indexes{return Err("Collection index is damaged; data was not changed".into())}Ok(())
}
fn load(p:&Path)->Result<Collection>{
 match fs::symlink_metadata(p){
  Err(e)if e.kind()==std::io::ErrorKind::NotFound=>Ok(Collection{schema:1,..Default::default()}),
  Err(e)=>Err(e.to_string()),Ok(_)=>{let c:Collection=serde_json::from_slice(&crate::sdk::read_bounded(p,MAX_BYTES)?).map_err(|e|format!("Collection could not be read: {e}"))?;validate(&c)?;Ok(c)}
 }
}
fn revision(c:&Collection,args:&Value)->Result<()>{if args["expected_revision"].as_u64()!=Some(c.revision){return Err("Collection changed. Reload before saving; no changes were applied".into())}Ok(())}
fn metadata(c:&Collection)->Value{json!({"schema":c.schema,"revision":c.revision,"count":c.rows.len(),"indexes":c.indexes.keys().collect::<Vec<_>>()})}
pub(crate) fn service(root:&Path,service:&str,args:&Value)->Result<Value>{
 let allowed:&[&str]=match service{
  "collections.usage"=>&[],"collections.info"=>&["name"],"collections.get"=>&["name","id"],
  "collections.query"=>&["name","index","value","after","limit","expected_revision"],
  "collections.transact"=>&["name","expected_revision","schema","indexes","operations"],
  "collections.clear"|"collections.backup"=>&["name","expected_revision"],"collections.restore"=>&["name","expected_revision","schema","blob"],_=>return Err("Unsupported collection service".into())};
 let object=args.as_object().ok_or("Collection arguments must be an object")?;
 if object.keys().any(|k|!allowed.contains(&k.as_str()))||args.to_string().len()>262144{return Err("Unsupported or oversized collection arguments".into())}
 if service=="collections.usage"{return Ok(json!({"used_bytes":crate::sdk::private_size(root,&root.join("unused"))?,"quota_bytes":crate::sdk_storage::limits(root)?.documents,"collection_bytes":MAX_BYTES,"rows_per_collection":2000,"collections":32}))}
 let p=path(root,args["name"].as_str().ok_or("Choose a collection name")?)?;let mut c=load(&p)?;
 match service{
  "collections.info"=>Ok(metadata(&c)),
  "collections.get"=>{let key=args["id"].as_str().filter(|k|id(k)).ok_or("Choose a valid record ID")?;Ok(json!({"revision":c.revision,"schema":c.schema,"id":key,"data":c.rows.get(key)}))},
  "collections.query"=>{
   if args.get("expected_revision").is_some(){revision(&c,args)?;}
   let limit=match args.get("limit"){None=>40,Some(v)=>v.as_u64().filter(|v|(1..=40).contains(v)).ok_or("Page size must be1–40")? as usize};
   let after=match args.get("after"){None=>"",Some(v)=>v.as_str().filter(|v|v.is_empty()||id(v)).ok_or("Invalid page cursor")?};
   let keys=if let Some(field)=args.get("index"){
    let index=c.indexes.get(field.as_str().ok_or("Choose an index name")?).ok_or("Index is not configured")?;
    let value=args.get("value").ok_or("Choose an index value")?;
    index.get(&scalar(value)?).cloned().unwrap_or_default()
   }else{if args.get("value").is_some(){return Err("Choose an index for this value".into())}c.rows.keys().cloned().collect()};
   let mut items=Vec::new();let mut bytes=0;let mut more=false;
   for key in keys.iter().filter(|key|key.as_str()>after){
    let item=json!({"id":key,"data":c.rows[key]});let size=item.to_string().len();
    if items.len()>=limit||bytes+size>200000{more=true;break}bytes+=size;items.push(item);
   }
   let next=if more{items.last().and_then(|v|v["id"].as_str())}else{None};
   Ok(json!({"schema":c.schema,"revision":c.revision,"items":items,"total":keys.len(),"next":next,"indexed":args.get("index").is_some()}))
  },
  "collections.backup"=>{revision(&c,args)?;let bytes=serde_json::to_vec(&json!({"format":"onklav.collection-backup.v1","schema":c.schema,"rows":c.rows,"indexes":c.indexes.keys().collect::<Vec<_>>()})).map_err(|e|e.to_string())?;crate::sdk_storage::save_bytes(root,&format!("{}.json",args["name"].as_str().unwrap()),"application/json",&bytes)},
  "collections.restore"=>{revision(&c,args)?;let bytes=crate::sdk_storage::read_all(root,args["blob"].as_str().ok_or("Choose an imported backup file")?,MAX_BYTES+4096)?;let backup:Value=serde_json::from_slice(&bytes).map_err(|e|format!("Backup could not be read: {e}"))?;let fields=backup.as_object().ok_or("Invalid collection backup")?;if fields.len()!=4||backup["format"]!="onklav.collection-backup.v1"{return Err("Unsupported collection backup".into())}let schema=backup["schema"].as_u64().filter(|n|*n>0&&*n>=c.schema as u64&&*n<=u32::MAX as u64&&args["schema"].as_u64()==Some(*n)).ok_or("Backup schema is not supported by this import")?;let rows:BTreeMap<String,Value>=serde_json::from_value(backup["rows"].clone()).map_err(|e|e.to_string())?;let fields:Vec<String>=serde_json::from_value(backup["indexes"].clone()).map_err(|e|e.to_string())?;if fields.iter().collect::<BTreeSet<_>>().len()!=fields.len(){return Err("Duplicate backup index".into())}let imported=Collection{schema:schema as u32,revision:c.revision.checked_add(1).ok_or("Collection revision exhausted")?,indexes:indexed(&rows,fields.into_iter())?,rows};validate(&imported)?;let bytes=serde_json::to_vec(&imported).map_err(|e|e.to_string())?;if bytes.len()as u64>MAX_BYTES||crate::sdk::private_size(root,&p)?+bytes.len()as u64>crate::sdk_storage::limits(root)?.documents{return Err("Imported collection exceeds storage limits; data was not changed".into())}if !p.exists(){let count=std::fs::read_dir(root).map_err(|e|e.to_string())?.filter_map(|e|e.ok()).filter(|e|e.file_name().to_str().is_some_and(|n|n.starts_with("collection-")&&n.ends_with(".json"))).count();if count>=32{return Err("App collection limit reached".into())}}crate::sdk::atomic(&p,&bytes)?;Ok(metadata(&imported))},
  "collections.clear"=>{revision(&c,args)?;if c.revision==0{return Ok(json!({"cleared":true,"revision":0}))}c.rows.clear();c.indexes=indexed(&c.rows,c.indexes.keys().cloned())?;c.revision=c.revision.checked_add(1).ok_or("Collection revision exhausted")?;let bytes=serde_json::to_vec(&c).map_err(|e|e.to_string())?;crate::sdk::atomic(&p,&bytes)?;Ok(json!({"cleared":true,"revision":c.revision}))},
  "collections.transact"=>{
   revision(&c,args)?;
   let v=args["schema"].as_u64().filter(|v|*v>=c.schema as u64&&*v<=u32::MAX as u64).ok_or("Declare a supported schema version; versions must not go backwards")?;c.schema=v as u32;
   let operations=args["operations"].as_array().filter(|v|!v.is_empty()&&v.len()<=64).ok_or("Use1–64 transaction operations")?;
   for op in operations{
    let key=op["id"].as_str().filter(|v|id(v)).ok_or("Choose a valid record ID")?;
    let fields=op.as_object().ok_or("Invalid transaction operation")?;
    match op["operation"].as_str(){
     Some("put") if fields.len()==3&&op.get("data").is_some()=>{let row=&op["data"];if !row.is_object()||row.to_string().len()>16384{return Err("Record must be an object up to16KiB; transaction was not saved".into())}c.rows.insert(key.into(),row.clone());},
     Some("delete") if fields.len()==2=>{c.rows.remove(key);},_=>return Err("Use put or delete operations with documented fields".into())
    }
   }
   let fields=if let Some(indexes)=args.get("indexes"){
    let values=indexes.as_array().filter(|v|v.len()<=8).ok_or("Use at most8 indexes")?;
    let names:Vec<String>=values.iter().map(|v|v.as_str().map(str::to_owned).ok_or("Index names must be text")).collect::<std::result::Result<_,_>>()?;
    if names.iter().collect::<BTreeSet<_>>().len()!=names.len(){return Err("Duplicate index name".into())}names
   }else{c.indexes.keys().cloned().collect()};
   c.indexes=indexed(&c.rows,fields.into_iter())?;c.revision=c.revision.checked_add(1).ok_or("Collection revision exhausted")?;validate(&c)?;
   let encoded=serde_json::to_vec(&c).map_err(|e|e.to_string())?;
   if encoded.len() as u64>MAX_BYTES||crate::sdk::private_size(root,&p)?+encoded.len()as u64>crate::sdk_storage::limits(root)?.documents{return Err("Private collection or app storage quota reached; data was not changed".into())}
   if !p.exists(){let count=fs::read_dir(root).map_err(|e|e.to_string())?.take(4096).filter_map(|e|e.ok()).filter(|e|e.file_name().to_str().is_some_and(|n|n.starts_with("collection-")&&n.ends_with(".json"))).count();if count>=32{return Err("App collection limit reached".into())}}
   crate::sdk::atomic(&p,&encoded)?;Ok(metadata(&c))
  },_=>Err("Unsupported collection service".into())
 }
}

#[cfg(test)]mod tests{
 use super::*;
 fn put(root:&Path,rev:u64,operations:Value)->Result<Value>{service(root,"collections.transact",&json!({"name":"episodes","expected_revision":rev,"schema":1,"indexes":["show"],"operations":operations}))}
 #[test]fn collections_transaction_query_reopen_and_conflicts(){let d=tempfile::tempdir().unwrap();let r=d.path();let result=put(r,0,json!([{"operation":"put","id":"one","data":{"show":"a","position":0}},{"operation":"put","id":"two","data":{"show":"b"}}])).unwrap();assert_eq!(result["revision"],1);let q=service(r,"collections.query",&json!({"name":"episodes","index":"show","value":"a"})).unwrap();assert_eq!(q["items"][0]["id"],"one");assert_eq!(q["total"],1);assert_eq!(q["indexed"],true);assert!(put(r,0,json!([{"operation":"delete","id":"one"}])).is_err());assert_eq!(service(r,"collections.info",&json!({"name":"episodes"})).unwrap()["count"],2);}
 #[test]fn collections_failed_batch_keeps_all_old_rows(){let d=tempfile::tempdir().unwrap();let r=d.path();put(r,0,json!([{"operation":"put","id":"one","data":{"show":"a"}}])).unwrap();let before=fs::read(path(r,"episodes").unwrap()).unwrap();assert!(put(r,1,json!([{"operation":"delete","id":"one"},{"operation":"put","id":"bad","data":[]}])).is_err());assert_eq!(before,fs::read(path(r,"episodes").unwrap()).unwrap());}
 #[test]fn collections_paging_and_schema_upgrades_are_explicit(){let d=tempfile::tempdir().unwrap();let r=d.path();let ops=json!([{"operation":"put","id":"a","data":{}},{"operation":"put","id":"b","data":{}},{"operation":"put","id":"c","data":{}}]);put(r,0,ops).unwrap();let page=service(r,"collections.query",&json!({"name":"episodes","limit":2})).unwrap();assert_eq!(page["next"],"b");let last=service(r,"collections.query",&json!({"name":"episodes","after":page["next"],"limit":2})).unwrap();assert_eq!(last["items"][0]["id"],"c");assert!(last["next"].is_null());service(r,"collections.transact",&json!({"name":"episodes","expected_revision":1,"schema":2,"operations":[{"operation":"delete","id":"c"}]})).unwrap();assert!(service(r,"collections.transact",&json!({"name":"episodes","expected_revision":2,"schema":1,"operations":[{"operation":"delete","id":"b"}]})).is_err());}
 #[test]fn collections_paths_corruption_and_quotas_fail_closed(){let d=tempfile::tempdir().unwrap();let r=d.path();assert!(service(r,"collections.info",&json!({"name":"../other"})).is_err());fs::write(path(r,"episodes").unwrap(),b"broken").unwrap();assert!(put(r,0,json!([{"operation":"put","id":"one","data":{}}])).is_err());fs::remove_file(path(r,"episodes").unwrap()).unwrap();fs::write(r.join("other-private-data"),vec![0u8;(16*1024*1024) as usize]).unwrap();assert!(put(r,0,json!([{"operation":"put","id":"one","data":{}}])).is_err());assert!(!path(r,"episodes").unwrap().exists());}
 #[cfg(unix)]#[test]fn collections_symlink_cannot_read_or_overwrite_another_app(){use std::os::unix::fs::symlink;let d=tempfile::tempdir().unwrap();let a=d.path().join("a");let b=d.path().join("b");fs::create_dir(&a).unwrap();fs::create_dir(&b).unwrap();put(&b,0,json!([{"operation":"put","id":"private","data":{"show":"b"}}])).unwrap();let target=path(&b,"episodes").unwrap();let before=fs::read(&target).unwrap();symlink(&target,path(&a,"episodes").unwrap()).unwrap();assert!(service(&a,"collections.info",&json!({"name":"episodes"})).is_err());assert!(put(&a,0,json!([{"operation":"delete","id":"private"}])).is_err());assert_eq!(before,fs::read(target).unwrap());}
 #[test]fn collections_clear_and_consistent_pages_reject_old_revisions(){let d=tempfile::tempdir().unwrap();let r=d.path();put(r,0,json!([{"operation":"put","id":"one","data":{}}])).unwrap();let cleared=service(r,"collections.clear",&json!({"name":"episodes","expected_revision":1})).unwrap();assert_eq!(cleared["revision"],2);assert!(put(r,1,json!([{"operation":"put","id":"one","data":{}}])).is_err());assert!(service(r,"collections.query",&json!({"name":"episodes","expected_revision":1})).is_err());assert_eq!(service(r,"collections.info",&json!({"name":"episodes"})).unwrap()["count"],0);}

 #[test]fn storage_collection_backup_restore_is_scoped_and_revision_checked(){let d=tempfile::tempdir().unwrap();let r=d.path();put(r,0,json!([{"operation":"put","id":"one","data":{"show":"example","progress":12}}])).unwrap();let backup=service(r,"collections.backup",&json!({"name":"episodes","expected_revision":1})).unwrap();service(r,"collections.clear",&json!({"name":"episodes","expected_revision":1})).unwrap();assert!(service(r,"collections.restore",&json!({"name":"episodes","expected_revision":1,"schema":1,"blob":backup["id"]})).is_err());let restored=service(r,"collections.restore",&json!({"name":"episodes","expected_revision":2,"schema":1,"blob":backup["id"]})).unwrap();assert_eq!(restored["revision"],3);assert_eq!(service(r,"collections.get",&json!({"name":"episodes","id":"one"})).unwrap()["data"]["progress"],12);let other=d.path().join("other");fs::create_dir(&other).unwrap();assert!(service(&other,"collections.restore",&json!({"name":"episodes","expected_revision":0,"schema":1,"blob":backup["id"]})).is_err());}
 #[test]fn storage_invalid_backup_preserves_collection(){let d=tempfile::tempdir().unwrap();let r=d.path();put(r,0,json!([{"operation":"put","id":"one","data":{}}])).unwrap();let blob=crate::sdk_storage::save_bytes(r,"bad.json","application/json",b"not a backup").unwrap();let before=fs::read(path(r,"episodes").unwrap()).unwrap();assert!(service(r,"collections.restore",&json!({"name":"episodes","expected_revision":1,"schema":1,"blob":blob["id"]})).is_err());assert_eq!(before,fs::read(path(r,"episodes").unwrap()).unwrap());}

}
