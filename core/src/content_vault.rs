//! Host-controlled folder access policy. Cryptography is performed by the
//! Android coordinator; guests cannot call these host operations or obtain keys.
use super::*;
use std::sync::{Mutex,OnceLock};
static CLOSING:OnceLock<Mutex<BTreeSet<(PathBuf,String)>>>=OnceLock::new();
static OPEN:OnceLock<Mutex<BTreeSet<(PathBuf,String)>>>=OnceLock::new();
fn sessions()->&'static Mutex<BTreeSet<(PathBuf,String)>>{OPEN.get_or_init(||Mutex::new(BTreeSet::new()))}
pub(super) fn opened(root:&Path,id:&str)->bool{sessions().lock().map(|s|s.contains(&(root.to_path_buf(),id.into()))).unwrap_or(false)}
fn set_closing(root:&Path,id:&str,enabled:bool)->Result<()>{let mut s=CLOSING.get_or_init(||Mutex::new(BTreeSet::new())).lock().map_err(|_|"Folder access needs recovery")?;let key=(root.to_path_buf(),id.into());if enabled{s.insert(key);}else{s.remove(&key);}Ok(())}
pub(super) fn finishing_write(root:&Path,index:&Index,id:&str)->bool{scope(index,id).is_some_and(|id|CLOSING.get_or_init(||Mutex::new(BTreeSet::new())).lock().map(|s|s.contains(&(root.to_path_buf(),id))).unwrap_or(false))&&index.entries.get(id).is_some_and(|e|e.mime=="application/vnd.onklav.write+json")}
fn set_open(root:&Path,id:&str,open:bool)->Result<()>{let mut s=sessions().lock().map_err(|_|"Folder access needs recovery")?;let key=(root.to_path_buf(),id.into());if open{s.insert(key);}else{s.remove(&key);}Ok(())}

/// Trash keeps its original vault association. A move out of an unlocked vault
/// is explicit and releases the item; a stale membership must not lock that copy.
pub(super) fn scope(index:&Index,id:&str)->Option<String>{
 let mut cursor=id.to_owned();let mut seen=BTreeSet::new();
 for _ in 0..64{
  if cursor.is_empty()||!seen.insert(cursor.clone()){break}
  let Some(e)=index.entries.get(&cursor)else{
   return index.entries.values().find(|e|e.vault.as_ref().is_some_and(|ids|ids.iter().any(|s|s==id))).map(|e|e.id.clone());
  };
  if e.vault.is_some(){return Some(e.id.clone())}
  cursor=if e.parent=="trash"{e.from.clone().unwrap_or_default()}else{e.parent.clone()};
 }
 None
}
pub(super) fn readable(root:&Path,index:&Index,id:&str)->bool{scope(index,id).is_none_or(|id|opened(root,&id))}
pub(super) fn guard(root:&Path,index:&Index,id:&str)->Result<()>{if readable(root,index,id){Ok(())}else{Err("This folder is locked. Open it in Files and enter its password.".into())}}
pub(super) fn guard_tree(root:&Path,index:&Index,id:&str)->Result<()>{guard(root,index,id)?;for child in descendants(index,id){guard(root,index,&child)?;}Ok(())}
pub(super) fn archive_path(root:&Path,id:&str)->Result<PathBuf>{if !crate::valid_id(id){return Err("Invalid encrypted folder identity".into())}Ok(directory(base(root)?.join("vaults"))?.join(format!("{id}.bin")))}
fn eligible(index:&Index,id:&str)->bool{index.entries.get(id).is_some_and(|e|e.folder&&e.owner=="user"&&!protected(id)&&!trashed(index,id).unwrap_or(true))}
pub(super) fn members(index:&Index,id:&str)->BTreeSet<String>{index.entries.keys().filter(|k|k.as_str()==id||scope(index,k).as_deref()==Some(id)).cloned().collect()}
fn manifest(root:&Path,index:&Index,id:&str)->Result<Value>{
 let ids=members(index,id);let entries:Vec<_>=ids.iter().map(|id|index.entries[id].clone()).collect();
 if entries.iter().any(|e|e.note||e.id!=id&&e.vault.is_some()){return Err("Nested encrypted folders and legacy Notes cannot be protected together".into())}
 if entries.len()>1000{return Err("Protect up to 1,000 entries in one folder".into())}
 let mut entries=entries;
 if let Some(root_entry)=entries.iter_mut().find(|e|e.id==id){root_entry.vault=Some(ids.iter().cloned().collect());}
 let mut cleanup:BTreeSet<String>=index.entries.get(id).and_then(|e|e.vault.clone()).unwrap_or_default().into_iter().collect();cleanup.extend(ids.clone());
 cleanup.retain(|key|!index.entries.contains_key(key)||scope(index,key).as_deref()==Some(id));
 let value=json!({"folder":id,"entries":entries,"cleanup_ids":cleanup});
 if serde_json::to_vec(&value).map_err(|e|e.to_string())?.len()>700_000{return Err("Folder metadata is too large to protect".into())}
 let _=root;Ok(value)
}
fn restored(index:&Index,id:&str,args:&Value)->Result<Index>{
 let entries:Vec<Entry>=serde_json::from_value(args["entries"].clone()).map_err(|_|"Invalid protected folder records")?;
 if entries.is_empty()||entries.len()>1000{return Err("Invalid protected folder size".into())}
 let current=index.entries.get(id).filter(|e|e.vault.is_some()).ok_or("Encrypted folder is unavailable")?;
 let mut next=index.clone();let mut seen=BTreeSet::new();
 for mut e in entries{
  if !crate::valid_id(&e.id)||!seen.insert(e.id.clone())||e.note||protected(&e.id)||e.mime.len()>200||e.mime.chars().any(char::is_control)||e.owner.len()>200||e.owner.chars().any(char::is_control)||e.bytes>MAX_FILE{return Err("Invalid protected folder entry".into())}
  valid_name(&e.name)?;
  if e.id==id{continue}
  if !current.vault.as_ref().is_some_and(|ids|ids.contains(&e.id)){return Err("Protected file identity is not in this folder".into())}
  if e.vault.is_some()||e.folder&&(e.bytes!=0||e.mime!="inode/directory"){return Err("Invalid nested protected folder".into())}
  if let Some(old)=index.entries.get(&e.id){if scope(index,&old.id).as_deref()!=Some(id){return Err("A protected folder entry conflicts with an existing file".into())}}else{e.owner="user".into();}
  next.entries.insert(e.id.clone(),e);
 }
 if !seen.contains(id)||next.entries.len()>MAX_ENTRIES{return Err("Protected folder identity changed".into())}
 for key in &seen{if key!=id&&scope(&next,key).as_deref()!=Some(id){return Err("Protected folder escapes its original location".into())}}
 let mut union:BTreeSet<_>=current.vault.clone().unwrap_or_default().into_iter().collect();union.extend(seen);next.entries.get_mut(id).unwrap().vault=Some(union.into_iter().collect());
 for e in next.entries.values(){trashed(&next,&e.id)?;}
 Ok(next)
}
pub(super) fn validate_entry(e:&Entry)->Result<()>{if let Some(ids)=&e.vault{if !e.folder||protected(&e.id)||ids.len()>MAX_ENTRIES||!ids.iter().all(|id|crate::valid_id(id))||!ids.iter().any(|id|id==&e.id){return Err("Invalid protected folder metadata".into())}}Ok(())}

pub(super) fn host(root:&Path,args:&Value)->Result<Value>{
 let mut index=load(root)?;let op=args["action"].as_str().ok_or("Choose folder operation")?;
 if op=="all"{return Ok(json!({"folders":index.entries.values().filter(|e|e.vault.is_some()).map(|e|json!({"id":e.id,"bytes":e.bytes,"open":opened(root,&e.id)})).collect::<Vec<_>>()}))}
 if op=="visibility"{
  let mut hidden=BTreeSet::new();
  for e in index.entries.values(){if let Some(ids)=&e.vault{for id in ids{if !index.entries.contains_key(id)||!readable(root,&index,id)||trashed(&index,id).unwrap_or(true){if let Ok(id)=uuid::Uuid::parse_str(id){hidden.insert(id.to_string());}}}}}
  for e in index.entries.values(){if e.mime=="application/vnd.onklav.write+json"&&(!readable(root,&index,&e.id)||trashed(&index,&e.id).unwrap_or(true)){if let Ok(id)=uuid::Uuid::parse_str(&e.id){hidden.insert(id.to_string());}}}
  return Ok(json!({"hidden":hidden}))
 }
 let raw=args["id"].as_str().unwrap_or("");let id=if op=="document"||op=="delete_document"||op=="save_document"{uuid::Uuid::parse_str(raw).map_err(|_|"Invalid document identity")?.simple().to_string()}else{raw.into()};
 if op=="access"||op=="document"||op=="save_document"{if !(op=="save_document"&&finishing_write(root,&index,&id)){guard(root,&index,&id)?;}if (op=="document"||op=="save_document")&&((index.entries.contains_key(&id)&&trashed(&index,&id)?)||(!index.entries.contains_key(&id)&&scope(&index,&id).is_some())){return Err("Restore this document in Files first".into())}return Ok(json!({"allowed":true}))}
 if op=="delete_document"{guard(root,&index,&id)?;if index.entries.get(&id).is_some_and(|e|e.mime=="application/vnd.onklav.write+json")&&!trashed(&index,&id)?{service(root,"library.trash",&json!({"id":id}))?;}return Ok(json!({"done":true}))}
 if op=="validate_cleanup"{
  let root_entry=index.entries.get(&id).filter(|e|e.vault.is_some()).ok_or("Encrypted folder is unavailable")?;
  let ids:Vec<String>=serde_json::from_value(args["ids"].clone()).map_err(|_|"Invalid cleanup identities")?;if ids.len()>MAX_ENTRIES{return Err("Too many cleanup entries".into())}
  for key in ids{
   if !crate::valid_id(&key){return Err("Invalid cleanup identity".into())}
   if index.entries.contains_key(&key){if scope(&index,&key).as_deref()!=Some(id.as_str()){return Err("Recovery data belongs to another folder".into())}}
   else if !root_entry.vault.as_ref().unwrap().contains(&key){let uuid=uuid::Uuid::parse_str(&key).map_err(|_|"Invalid recovery identity")?;let paths=[root.join(format!("write/documents/{uuid}.json")),root.join(format!("write/documents/{uuid}.json.bak")),root.join(format!("write/documents/{uuid}.json.new")),root.join(format!("notes/{key}.json")),root.join(format!("app-sdk/data/app.onklav.notes/notes/{key}.json")),base(root)?.join(format!("notes/{key}.json"))];if paths.iter().any(|p|p.exists()){return Err("Recovery files conflict with existing documents".into())}}
  }
  return Ok(json!({"done":true}))
 }
 if op=="status"{
  let vault=scope(&index,&id);let entry=vault.as_deref().and_then(|v|index.entries.get(v));
  return Ok(json!({"protectable":eligible(&index,&id)&&vault.is_none(),"encrypted":vault.is_some(),"locked":vault.as_deref().is_some_and(|v|!opened(root,v)),"id":vault.as_deref().unwrap_or(&id),"name":entry.or_else(||index.entries.get(&id)).map(|e|e.name.as_str()).unwrap_or("Folder")}))
 }
 let e=index.entries.get(&id).cloned().ok_or("Folder is unavailable")?;
 match op{
  "prepare"=>{set_closing(root,&id,false)?;
   if e.vault.is_none(){if !eligible(&index,&id)||scope(&index,&id).is_some(){return Err("Only user-created folders outside other encrypted folders can be protected".into())}if descendants(&index,&id).iter().any(|k|index.entries[k].vault.is_some()){return Err("This folder contains an encrypted folder".into())}index.entries.get_mut(&id).unwrap().vault=Some(vec![id.clone()]);}
   let capture=manifest(root,&index,&id)?;let mut all:BTreeSet<_>=e.vault.unwrap_or_default().into_iter().collect();all.extend(members(&index,&id));index.entries.get_mut(&id).unwrap().vault=Some(all.into_iter().collect());
   set_open(root,&id,false)?;save(root,&index)?;Ok(capture)
  },
  "capture"=>{if e.vault.is_none(){return Err("Folder is not encrypted".into())}manifest(root,&index,&id)},
  "restore_entries"=>{let next=restored(&index,&id,args)?;set_open(root,&id,false)?;save(root,&next)?;Ok(json!({"done":true}))},
  "publish"=>{if e.vault.is_none(){return Err("Folder protection was removed".into())}let bytes=fs::metadata(archive_path(root,&id)?).map_err(|e|e.to_string())?.len();if bytes>MAX_FILE||bytes<148{return Err("Encrypted folder archive is incomplete".into())}set_open(root,&id,false)?;set_closing(root,&id,false)?;let mut live=members(&index,&id);for key in e.vault.unwrap_or_default(){if !index.entries.contains_key(&key){live.insert(key);}}index.entries.retain(|key,_|key==&id||!live.contains(key));let root_entry=index.entries.get_mut(&id).unwrap();root_entry.bytes=bytes;root_entry.vault=Some(live.into_iter().collect());save(root,&index)?;Ok(json!({"done":true}))},
  "open"=>{if e.vault.is_none(){return Err("Folder is not encrypted".into())}for item in members(&index,&id){let child=&index.entries[&item];if !child.folder{let m=fs::symlink_metadata(object(root,&item)?).map_err(|e|e.to_string())?;if !m.is_file()||m.len()!=child.bytes{return Err("Protected file restoration is incomplete".into())}}}index.entries.get_mut(&id).unwrap().bytes=0;save(root,&index)?;set_closing(root,&id,false)?;set_open(root,&id,true)?;Ok(json!({"done":true}))},
  "close_reads"=>{set_open(root,&id,false)?;set_closing(root,&id,true)?;Ok(json!({"done":true}))},
  "freeze"=>{set_open(root,&id,false)?;set_closing(root,&id,false)?;manifest(root,&index,&id)},
  "rollback"=>{if e.vault.is_none()||e.bytes!=0||archive_path(root,&id)?.exists(){return Err("Protection cannot be rolled back".into())}for key in members(&index,&id){let item=&index.entries[&key];if !item.folder&&!object(root,&key)?.is_file(){return Err("Original files need recovery".into())}}index.entries.get_mut(&id).unwrap().vault=None;save(root,&index)?;set_open(root,&id,false)?;Ok(json!({"done":true}))},
  "remove"=>{if e.vault.is_none(){return Ok(json!({"done":true}))}if !opened(root,&id){return Err("Unlock this folder before removing protection".into())}index.entries.get_mut(&id).unwrap().vault=None;index.entries.get_mut(&id).unwrap().bytes=0;save(root,&index)?;set_open(root,&id,false)?;Ok(json!({"done":true}))},
  _=>Err("Unsupported encrypted-folder operation".into())
 }
}

#[cfg(test)]mod tests{
 use super::*;
 fn folder(root:&Path,parent:&str,name:&str)->String{service(root,"library.folder",&json!({"parent":parent,"name":name})).unwrap()["id"].as_str().unwrap().into()}
 fn put(root:&Path,parent:&str,name:&str)->String{let mut i=load(root).unwrap();insert(root,&mut i,parent,name,"text/plain","user",b"secret text").unwrap()["id"].as_str().unwrap().into()}
 fn op(root:&Path,action:&str,id:&str)->Value{host(root,&json!({"action":action,"id":id})).unwrap()}
 #[test]fn content_vault_closes_every_shared_path_and_hides_names(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let parent=folder(r,"documents","Parent");let v=folder(r,&parent,"Private");let child=folder(r,&v,"Secrets");let file=put(r,&child,"secret.txt");
  let snapshot=op(r,"prepare",&v);assert_eq!(snapshot["entries"].as_array().unwrap().len(),3);
  for (action,args)in [("library.list",json!({"folder":v})),("library.info",json!({"id":file})),("library.read_text",json!({"id":file})),("library.rename",json!({"id":v,"name":"changed"})),("library.move",json!({"id":v,"parent":"downloads"})),("library.trash",json!({"id":v})),("library.copy",json!({"id":parent,"parent":"downloads"})),("library.compress",json!({"id":parent})),("library.folder",json!({"parent":v,"name":"New"}))]{assert!(service(r,action,&args).is_err(),"{action}");}
  assert!(resolve(r,&file).is_err());assert!(share(r,&parent).is_err());assert!(service(r,"library.list",&json!({"query":"secret"})).unwrap()["items"].as_array().unwrap().is_empty());
  assert_eq!(service(r,"library.info",&json!({"id":v})).unwrap()["locked"],true);
  fs::write(archive_path(r,&v).unwrap(),vec![9;200]).unwrap();op(r,"publish",&v);op(r,"publish",&v);
  let raw=fs::read_to_string(base(r).unwrap().join("index.json")).unwrap();assert!(!raw.contains("secret.txt"));assert!(!raw.contains("Secrets"));assert!(occupied(&load(r).unwrap(),&file));
  host(r,&json!({"action":"restore_entries","id":v,"entries":snapshot["entries"]})).unwrap();op(r,"open",&v);assert_eq!(resolve(r,&file).unwrap()["name"],"secret.txt");op(r,"close_reads",&v);assert!(resolve(r,&file).is_err());
 }
 #[test]fn content_vault_trash_membership_and_write_autosave_are_guarded(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let v=folder(r,"documents","Private");let file=put(r,&v,"draft.write");let mut i=load(r).unwrap();i.entries.get_mut(&file).unwrap().mime="application/vnd.onklav.write+json".into();save(r,&i).unwrap();op(r,"prepare",&v);op(r,"open",&v);service(r,"library.trash",&json!({"id":file})).unwrap();
  op(r,"close_reads",&v);assert!(host(r,&json!({"action":"document","id":file})).is_err());assert!(service(r,"library.restore",&json!({"id":file})).is_err());assert!(service(r,"library.purge",&json!({"id":file})).is_err());
  op(r,"open",&v);service(r,"library.restore",&json!({"id":file})).unwrap();op(r,"close_reads",&v);assert!(host(r,&json!({"action":"save_document","id":file})).is_ok());assert!(host(r,&json!({"action":"document","id":file})).is_err());op(r,"freeze",&v);assert!(host(r,&json!({"action":"save_document","id":file})).is_err());
 }
 #[test]fn content_vault_backups_contain_only_ciphertext_and_reserve_member_ids(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let v=folder(r,"documents","Private");let file=put(r,&v,"secret.txt");let snapshot=op(r,"prepare",&v);op(r,"open",&v);assert!(backup::export(r).is_err());op(r,"freeze",&v);fs::write(archive_path(r,&v).unwrap(),vec![7;200]).unwrap();op(r,"publish",&v);
  let(meta,paths)=backup::export(r).unwrap();assert!(!meta.to_string().contains("secret.txt"));assert_eq!(paths.len(),1);assert_eq!(paths[&format!("files/{v}")],archive_path(r,&v).unwrap());
  let other=tempfile::tempdir().unwrap();let blobs=BTreeMap::from([(format!("files/{v}"),crate::library_backup::Blob{bytes:200,sha256:"a".repeat(64)})]);let plan=backup::plan(other.path(),&meta,&blobs).unwrap();assert_eq!(plan.new,1);
  let mut bad=snapshot["entries"].clone();bad.as_array_mut().unwrap().iter_mut().find(|e|e["id"]==file).unwrap()["id"]=json!("a".repeat(32));assert!(host(r,&json!({"action":"restore_entries","id":v,"entries":bad})).is_err());
  assert!(host(r,&json!({"action":"validate_cleanup","id":v,"ids":["documents"]})).is_err());
 }
 #[test]fn content_vault_rejects_system_nested_and_foreign_recovery(){
  let d=tempfile::tempdir().unwrap();let r=d.path();assert!(host(r,&json!({"action":"prepare","id":"documents"})).is_err());let v=folder(r,"documents","Private");let n=folder(r,&v,"Nested");op(r,"prepare",&n);assert!(host(r,&json!({"action":"prepare","id":v})).is_err());
  let foreign="a".repeat(32);let uuid=uuid::Uuid::parse_str(&foreign).unwrap();let path=r.join(format!("write/documents/{uuid}.json"));fs::create_dir_all(path.parent().unwrap()).unwrap();fs::write(path,b"unrelated").unwrap();assert!(host(r,&json!({"action":"validate_cleanup","id":n,"ids":[foreign]})).is_err());
 }
}
