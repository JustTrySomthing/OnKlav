//! Trusted recovery/configuration UI data; never grants or performs phone writes.
use crate::{history_journal,history_backup,messaging,Result};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{collections::BTreeSet,fs::{self,File,OpenOptions},io::{BufRead,BufReader,Read,Write},path::Path,sync::Mutex};
static LOCK:Mutex<()>=Mutex::new(());
#[derive(Serialize,Deserialize,Default)]
#[serde(deny_unknown_fields)]
struct Config{version:u32,identities:Vec<String>}
fn identities(values:&Value)->Result<Vec<String>> {
    let values=values.as_array().ok_or("Enter one local phone number per line")?;if values.len()>10{return Err("Use at most 10 local phone identities".into())}
    let mut seen=BTreeSet::new();let mut out=Vec::new();
    for value in values {let value=value.as_str().ok_or("Invalid local phone identity")?.trim();
        if value.is_empty()||value.len()>64||!value.bytes().all(|c|c.is_ascii_digit()||b"+ ()-.".contains(&c)){return Err("Enter complete local phone numbers, with country codes where used".into())}
        let compact:String=value.chars().filter(|c|!" ()-.".contains(*c)).collect();let digits=compact.strip_prefix('+').unwrap_or(&compact);
        if !(7..=15).contains(&digits.len())||!digits.bytes().all(|c|c.is_ascii_digit())||!seen.insert(compact.clone()){return Err("Use unique complete local phone numbers".into())}out.push(compact);
    }Ok(out)
}
fn config(root:&Path)->Result<Config>{let path=root.join("history-restore-settings.json");if !path.try_exists().map_err(|e|e.to_string())?{if fs::symlink_metadata(&path).is_ok(){return Err("Invalid restore configuration storage".into())}return Ok(Config{version:1,identities:Vec::new()})}
    history_backup::regular(&path,4096)?;let data:Config=serde_json::from_reader(File::open(path).map_err(|e|e.to_string())?).map_err(|_|"Invalid restore configuration")?;
    if data.version!=1||identities(&json!(data.identities))?!=data.identities{return Err("Invalid restore configuration".into())}Ok(data)
}
fn save_config(root:&Path,values:&Value)->Result<Value>{let _previous=config(root)?;let data=Config{version:1,identities:identities(values)?};
    let temp=root.join(format!("restore-config-{}.tmp",uuid::Uuid::new_v4().simple()));let result=(||{let mut f=OpenOptions::new().write(true).create_new(true).open(&temp).map_err(|e|e.to_string())?;f.write_all(&serde_json::to_vec(&data).map_err(|e|e.to_string())?).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;fs::rename(&temp,root.join("history-restore-settings.json")).map_err(|e|e.to_string())?;File::open(root).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(json!(data))})();if result.is_err(){let _=fs::remove_file(temp);}result
}
pub(super) fn header(value:&Value)->Result<()> {fn hash(v:&Value)->bool{v.as_str().is_some_and(|s|s.len()==64&&s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b)))}
    if value["format"]!="onklav-restore-outcomes"||value["version"]!=1||!hash(&value["archive_sha"])||!hash(&value["plan_sha"])||!value["metadata_applied"].is_boolean()||!value["phone_revalidated"].is_boolean(){return Err("Invalid saved restore header".into())}Ok(())
}
fn map_header(path:&Path)->Result<Value>{history_backup::regular(path,128*1024*1024)?;let mut line=String::new();BufReader::new(File::open(path).map_err(|e|e.to_string())?).take(4097).read_line(&mut line).map_err(|e|e.to_string())?;if line.len()>4096||!line.ends_with('\n'){return Err("Invalid saved restore header".into())}let row:Value=serde_json::from_str(&line).map_err(|_|"Invalid saved restore header")?;header(&row)?;Ok(row)}
fn receipt(root:&Path,id:&str,h:&Value)->Result<Option<messaging::HistoryMerge>>{let receipt=messaging::history_receipt(root,id)?;if let Some(r)=&receipt{if json!(r.archive_sha)!=h["archive_sha"]||json!(r.plan_sha)!=h["plan_sha"]{return Err("Saved restore receipt identity mismatch".into())}}Ok(receipt)}
pub(super) fn saved_map(root:&Path,id:&str)->Result<Vec<Value>>{
    if !crate::valid_id(id){return Err("Invalid saved restore identity".into())}let dir=root.join("history-restore-maps");history_backup::dir(&dir)?;let path=dir.join(format!("{id}.jsonl"));let h=map_header(&path)?;
    let mut rows=vec![h];let mut seen=BTreeSet::new();let mut reader=BufReader::new(File::open(path).map_err(|e|e.to_string())?);let mut first=String::new();reader.read_line(&mut first).map_err(|e|e.to_string())?;
    loop{let mut line=Vec::new();let n=(&mut reader).take(8193).read_until(b'\n',&mut line).map_err(|e|e.to_string())?;if n==0{break}if n>8192||rows.len()>200_000{return Err("Saved restore record exceeds limits".into())}
        let row:Value=serde_json::from_slice(&line).map_err(|_|"Invalid saved restore record")?;
        let source=row["source"].as_str().ok_or("Missing saved source")?;let(kind,id)=source.split_once(':').ok_or("Invalid saved source")?;
        fn key(value:&str,kind:&str)->bool{value.strip_prefix(&format!("{kind}:")).is_some_and(|s|s.parse::<i64>().is_ok_and(|n|n>0&&n.to_string()==s))}
        if !matches!(kind,"sms"|"mms")||!key(source,kind)||!seen.insert(source.to_string())||id.is_empty()||row["kind"]!="message"{return Err("Invalid saved restore source".into())}
        match row["decision"].as_str(){Some("match"|"inserted")=>{if !row["target"].as_str().is_some_and(|s|key(s,kind)){return Err("Invalid saved target".into())}},Some("pending"|"review"|"skip")=>{if !row["target"].is_null(){return Err("Unexpected saved target".into())}},_=>return Err("Invalid saved restore decision".into())}rows.push(row);
    }Ok(rows)
}
pub fn dispatch(root:&Path,j:&Value)->Result<Value>{let _guard=LOCK.lock().map_err(|_|"Recovery settings unavailable")?;
    match j["action"].as_str().ok_or("Missing recovery action")? {
        "resume_checkpoint"=>crate::history_resume::checkpoint(root,j["id"].as_str().ok_or("Missing restore record")?,j["map_sha"].as_str().ok_or("Missing saved record identity")?),
        "resume_info"=>crate::history_resume::info(root,j["id"].as_str().ok_or("Missing restore record")?),
        "compact_completed"=>crate::history_completed::compact(root),
        "compact_journal"=>history_journal::dispatch(root,&json!({"action":"compact"})),
        "config_get"=>Ok(json!(config(root)?)),"config_save"=>save_config(root,&j["identities"]),
        "overview"=>{
            let offset=j["offset"].as_u64().unwrap_or(0) as usize;if offset>crate::history_completed::MAX_COMPLETED+100{return Err("Invalid recovery page".into())}
            let pending=history_journal::dispatch(root,&json!({"action":"pending"}))?;let status=history_journal::dispatch(root,&json!({"action":"status"}))?;
            let archived=crate::history_completed::records(root)?;let raw=crate::history_completed::raw_ids(root)?;
            let ids:BTreeSet<String>=raw.iter().cloned().chain(archived.keys().cloned()).collect();
            let dir=root.join("history-restore-maps");let mut maps=Vec::new();
            for id in ids.iter().skip(offset).take(20){
                if let Some(row)=archived.get(id){crate::history_completed::check_copy(root,row)?;if messaging::history_receipt(root,id)?.as_ref()!=Some(&row.receipt){return Err("Completed metadata receipt mismatch".into())}maps.push(crate::history_completed::summary(row));}
                else {let h=map_header(&dir.join(format!("{id}.jsonl")))?;let r=receipt(root,id,&h)?;if h["metadata_applied"]==true&&r.is_none(){return Err("Saved metadata completion receipt is missing".into())}maps.push(json!({"id":id,"metadata_applied":r.is_some(),"ack_pending":r.is_some()&&h["metadata_applied"]==false,"compacted":false}));}
            }
            Ok(json!({"pending":pending["items"],"status":status,"maps":maps,"total":ids.len(),"active_maps":raw.len(),"archived_maps":archived.len(),"offset":offset,"has_more":offset+maps.len()<ids.len()}))
        },
        "map_detail"=>{let id=j["id"].as_str().ok_or("Missing restore record")?;
            if let Some(row)=crate::history_completed::find(root,id)?{crate::history_completed::check_copy(root,&row)?;if messaging::history_receipt(root,id)?.as_ref()!=Some(&row.receipt){return Err("Completed receipt mismatch".into())}return Ok(crate::history_completed::summary(&row))}
            let rows=saved_map(root,id)?;let r=receipt(root,id,&rows[0])?;if rows[0]["metadata_applied"]==true&&r.is_none(){return Err("Missing metadata completion receipt".into())}
            let count=|d:&str|rows.iter().skip(1).filter(|v|v["decision"]==d).count();Ok(json!({"id":id,"inserted":count("inserted"),"matched":count("match"),"pending":count("pending"),"review":count("review"),"skipped":count("skip"),"metadata_applied":r.is_some(),"ack_pending":r.is_some()&&rows[0]["metadata_applied"]==false,"flags_imported":r.as_ref().map_or(0,|r|r.flags_imported),"threads_imported":r.as_ref().map_or(0,|r|r.threads_imported)}))},
        "repair_ack"=>crate::history_apply::repair_ack(root,j["id"].as_str().ok_or("Missing restore record")?),
        _=>Err("Unsupported recovery operation".into())
    }
}

#[cfg(test)]mod tests{
    use super::*;
    fn call(root:&Path,action:&str)->Result<Value>{dispatch(root,&json!({"action":action}))}
    fn map(root:&Path,id:&str){let dir=root.join("history-restore-maps");fs::create_dir_all(&dir).unwrap();fs::write(dir.join(format!("{id}.jsonl")),format!("{}\n{}\n",json!({"format":"onklav-restore-outcomes","version":1,"archive_sha":"a".repeat(64),"plan_sha":"b".repeat(64),"metadata_applied":false,"phone_revalidated":false}),json!({"kind":"message","source":"sms:1","decision":"inserted","target":"sms:91"}))).unwrap();}
    #[test]fn identities_are_canonical_private_bounded_and_invalid_save_preserves_previous(){let t=tempfile::tempdir().unwrap();assert_eq!(call(t.path(),"config_get").unwrap()["identities"],json!([]));let saved=dispatch(t.path(),&json!({"action":"config_save","identities":["+1 (555) 010-1234","5550100999"]})).unwrap();assert_eq!(saved["identities"],json!(["+15550101234","5550100999"]));let before=fs::read(t.path().join("history-restore-settings.json")).unwrap();for values in [json!(["123"]),json!(["++15550101234"]),json!(["+15550101234","+1 (555) 010-1234"]),json!(["content://sms/1"]),json!(vec!["1";11])]{assert!(dispatch(t.path(),&json!({"action":"config_save","identities":values})).is_err());assert_eq!(before,fs::read(t.path().join("history-restore-settings.json")).unwrap());}assert!(crate::messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap().get("identities").is_none());}
    #[test]fn corrupt_config_and_symlink_storage_fail_closed(){let t=tempfile::tempdir().unwrap();fs::write(t.path().join("history-restore-settings.json"),"bad").unwrap();assert!(call(t.path(),"config_get").is_err());assert!(dispatch(t.path(),&json!({"action":"config_save","identities":[]})).is_err());#[cfg(unix)]{fs::remove_file(t.path().join("history-restore-settings.json")).unwrap();std::os::unix::fs::symlink(t.path().join("absent"),t.path().join("history-restore-settings.json")).unwrap();assert!(call(t.path(),"config_get").is_err());}}
    #[test]fn detail_is_counts_only_and_ack_repair_requires_committed_matching_receipt(){let t=tempfile::tempdir().unwrap();let id="1".repeat(32);map(t.path(),&id);let request=json!({"action":"map_detail","id":id});let detail=dispatch(t.path(),&request).unwrap();assert_eq!(detail["inserted"],1);assert_eq!(detail["metadata_applied"],false);assert!(detail.get("source").is_none());assert!(dispatch(t.path(),&json!({"action":"repair_ack","id":id})).is_err());let backup=messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap();let r=messaging::HistoryMerge{archive_sha:"a".repeat(64),plan_sha:"b".repeat(64),post_plan_sha:"c".repeat(64),flags_imported:0,threads_imported:0};messaging::merge_history(t.path(),&id,r,&backup,&Default::default(),&Default::default(),t.path()).unwrap();messaging::dispatch(t.path(),&json!({"action":"draft","key":"70","text":"keep current edit"})).unwrap();let before=fs::read(t.path().join("messenger.json")).unwrap();assert_eq!(dispatch(t.path(),&request).unwrap()["ack_pending"],true);dispatch(t.path(),&json!({"action":"repair_ack","id":id})).unwrap();assert_eq!(dispatch(t.path(),&request).unwrap()["ack_pending"],false);assert_eq!(before,fs::read(t.path().join("messenger.json")).unwrap());}
    #[test]fn review_is_paginated_and_invalid_records_cannot_be_acknowledged(){let t=tempfile::tempdir().unwrap();for i in 1..=21{map(t.path(),&format!("{i:032x}"));}let first=call(t.path(),"overview").unwrap();assert_eq!(first["maps"].as_array().unwrap().len(),20);assert_eq!(first["has_more"],true);assert_eq!(dispatch(t.path(),&json!({"action":"overview","offset":20})).unwrap()["maps"].as_array().unwrap().len(),1);let path=t.path().join("history-restore-maps").join(format!("{:032x}.jsonl",1));let mut f=OpenOptions::new().append(true).open(&path).unwrap();writeln!(f,"{{\"kind\":\"message\",\"source\":\"sms:1\",\"decision\":\"pending\"}}").unwrap();assert!(dispatch(t.path(),&json!({"action":"map_detail","id":format!("{:032x}",1)})).is_err());assert!(dispatch(t.path(),&json!({"action":"repair_ack","id":"../outside"})).is_err());}
}
