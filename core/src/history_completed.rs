//! Durable, bounded handoff for completed restore maps and metadata receipts.
//! Write the receipt archive first; raw evidence is pruned only afterward.
use crate::{history_backup,history_journal,history_recovery,messaging,Result};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::BTreeMap,fs::{self,File,OpenOptions},io::{Read,Write},path::Path,sync::Mutex};
static LOCK:Mutex<()>=Mutex::new(());
pub(super) const MAX_COMPLETED:usize=1000;
const MAX_BYTES:u64=2*1024*1024;
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
pub(super) struct Completed {
    pub id:String,pub receipt:messaging::HistoryMerge,pub map_sha:String,
    pub inserted:usize,pub matched:usize,pub review:usize,pub skipped:usize,
}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Archive {version:u32,records:Vec<Completed>}
fn hash(s:&str)->bool{s.len()==64&&s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))}
fn validate(key:&str,r:&Completed)->Result<()> {
    let p=&r.receipt;
    if !crate::valid_id(key)||key!=r.id||!hash(&r.map_sha)||!hash(&p.archive_sha)||!hash(&p.plan_sha)||!hash(&p.post_plan_sha)
        ||[r.inserted,r.matched,r.review,r.skipped].iter().any(|n|*n>200_000)
        ||r.review!=0||r.inserted+r.matched+r.review+r.skipped>200_000||p.flags_imported>20_000||p.threads_imported>5000 {
        return Err("Invalid completed restore receipt".into())
    }Ok(())
}
// No archive mutex here: readers (including the messenger state mutex holder)
// see one atomically renamed file. This avoids reverse lock ordering.
pub(super) fn records(root:&Path)->Result<BTreeMap<String,Completed>> {
    let path=root.join("history-completed.archive");
    match fs::symlink_metadata(&path){Err(e) if e.kind()==std::io::ErrorKind::NotFound=>return Ok(BTreeMap::new()),Err(e)=>return Err(e.to_string()),Ok(_)=>{}}
    history_backup::regular(&path,MAX_BYTES)?;
    let a:Archive=serde_json::from_reader(File::open(path).map_err(|e|e.to_string())?).map_err(|_|"Invalid completed restore archive")?;
    if a.version!=1||a.records.len()>MAX_COMPLETED{return Err("Completed restore archive limit reached".into())}
    let mut out=BTreeMap::new();for row in a.records{validate(&row.id,&row)?;if out.insert(row.id.clone(),row).is_some(){return Err("Duplicate completed restore identity".into())}}Ok(out)
}
pub(super) fn find(root:&Path,id:&str)->Result<Option<Completed>> {
    if !crate::valid_id(id){return Err("Invalid completed restore identity".into())}Ok(records(root)?.remove(id))
}
pub(super) fn summary(r:&Completed)->Value {json!({"id":r.id,"inserted":r.inserted,"matched":r.matched,"pending":0,"review":r.review,"skipped":r.skipped,
    "metadata_applied":true,"ack_pending":false,"compacted":true,"phone_revalidated":true,"flags_imported":r.receipt.flags_imported,"threads_imported":r.receipt.threads_imported})}
fn map_sha(path:&Path)->Result<String>{history_backup::regular(path,128*1024*1024)?;let mut f=File::open(path).map_err(|e|e.to_string())?;let mut h=Sha256::new();let mut b=[0;65536];loop{let n=f.read(&mut b).map_err(|e|e.to_string())?;if n==0{break}h.update(&b[..n]);}Ok(format!("{:x}",h.finalize()))}
pub(super) fn raw_ids(root:&Path)->Result<Vec<String>>{
    let dir=root.join("history-restore-maps");match fs::symlink_metadata(&dir){Err(e) if e.kind()==std::io::ErrorKind::NotFound=>return Ok(Vec::new()),Err(e)=>return Err(e.to_string()),Ok(_)=>{}}
    history_backup::dir(&dir)?;let mut ids=Vec::new();
    for e in fs::read_dir(dir).map_err(|e|e.to_string())?{let e=e.map_err(|e|e.to_string())?;let name=e.file_name().to_string_lossy().to_string();if let Some(id)=name.strip_suffix(".jsonl"){
        if !crate::valid_id(id)||ids.len()>=100{return Err("Invalid saved restore records".into())}ids.push(id.to_string());
    }}ids.sort();Ok(ids)
}
// When both copies survive a crash, never hide a changed raw file behind a summary.
pub(super) fn check_copy(root:&Path,r:&Completed)->Result<()> {
    let path=root.join("history-restore-maps").join(format!("{}.jsonl",r.id));
    match fs::symlink_metadata(&path){Err(e) if e.kind()==std::io::ErrorKind::NotFound=>return Ok(()),Err(e)=>return Err(e.to_string()),Ok(_)=>{}}
    history_backup::dir(&root.join("history-restore-maps"))?;
    if map_sha(&path)?!=r.map_sha{return Err("Completed restore copies conflict; evidence retained".into())}Ok(())
}
fn candidate(root:&Path,id:&str)->Result<Option<Completed>>{
    let rows=history_recovery::saved_map(root,id)?;let h=&rows[0];
    let Some(receipt)=messaging::history_receipt(root,id)? else {
        if h["metadata_applied"]==true{return Err("Completed metadata receipt is missing".into())}return Ok(None)
    };
    if json!(receipt.archive_sha)!=h["archive_sha"]||json!(receipt.plan_sha)!=h["plan_sha"]{return Err("Completed restore identity mismatch".into())}
    // Pending, need-review and unacknowledged operations remain in full for recovery.
    if h["metadata_applied"]!=true||rows.iter().skip(1).any(|r|r["decision"]=="pending"||r["decision"]=="review"){return Ok(None)}
    if h["phone_revalidated"]!=true||h["post_plan_sha"]!=receipt.post_plan_sha||h["flags_imported"]!=receipt.flags_imported||h["threads_imported"]!=receipt.threads_imported{return Err("Completed restore acknowledgement mismatch".into())}
    let journal=history_journal::dispatch(root,&json!({"action":"receipts","archive_sha":receipt.archive_sha}))?;
    let committed:BTreeMap<&str,&Value>=journal["items"].as_array().ok_or("Missing restore receipts")?.iter().map(|v|Ok((v["id"].as_str().ok_or("Missing transaction")?,v))).collect::<Result<_>>()?;
    for row in rows.iter().skip(1).filter(|r|r["decision"]=="inserted"){
        let txn=row["transaction"].as_str().ok_or("Inserted restore lacks a transaction receipt")?;
        let proof=committed.get(txn).ok_or("Inserted restore receipt is missing")?;
        if proof["source"]!=row["source"]||proof["target"]!=row["target"]{return Err("Inserted restore receipt does not match its outcome".into())}
    }
    let count=|d:&str|rows.iter().skip(1).filter(|r|r["decision"]==d).count();
    let row=Completed{id:id.into(),receipt,map_sha:map_sha(&root.join("history-restore-maps").join(format!("{id}.jsonl")))?,inserted:count("inserted"),matched:count("match"),review:count("review"),skipped:count("skip")};validate(id,&row)?;Ok(Some(row))
}
fn save(root:&Path,records:BTreeMap<String,Completed>)->Result<()> {
    if records.len()>MAX_COMPLETED{return Err("Completed restore archive is full; no records removed".into())}
    for (key,row) in &records{validate(key,row)?;}
    let bytes=serde_json::to_vec(&Archive{version:1,records:records.into_values().collect()}).map_err(|e|e.to_string())?;if bytes.len() as u64>MAX_BYTES{return Err("Completed restore archive byte limit reached".into())}
    let temp=root.join(format!("completed-{}.tmp",uuid::Uuid::new_v4().simple()));
    let result=(||{let mut f=OpenOptions::new().create_new(true).write(true).open(&temp).map_err(|e|e.to_string())?;
        f.write_all(&bytes).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;fs::rename(&temp,root.join("history-completed.archive")).map_err(|e|e.to_string())?;
        File::open(root).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();if result.is_err(){let _=fs::remove_file(temp);}result
}
fn prepare(root:&Path)->Result<BTreeMap<String,Completed>> {
    let mut all=records(root)?;
    for id in raw_ids(root)? {
        if let Some(old)=all.get(&id){check_copy(root,old)?;if messaging::history_receipt(root,&id)?.as_ref()!=Some(&old.receipt){return Err("Completed receipt copies conflict".into())}}
        else if let Some(row)=candidate(root,&id)?{all.insert(id,row);}
    }Ok(all)
}
fn prune(root:&Path,all:&BTreeMap<String,Completed>)->Result<usize>{
    // Validated, durable archive must exist before the messenger drops receipts.
    messaging::compact_history_receipts(root)?;
    let mut count=0;
    for id in raw_ids(root)? {if let Some(row)=all.get(&id){check_copy(root,row)?;fs::remove_file(root.join("history-restore-maps").join(format!("{id}.jsonl"))).map_err(|e|e.to_string())?;count+=1;}}
    let dir=root.join("history-restore-maps");if dir.exists(){File::open(dir).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;}Ok(count)
}
pub(super) fn compact(root:&Path)->Result<Value>{let _guard=LOCK.lock().map_err(|_|"Restore compaction unavailable")?;
    let all=prepare(root)?;save(root,all.clone())?;let count=prune(root,&all)?;
    Ok(json!({"compacted":count,"archived":all.len(),"active_remaining":100-raw_ids(root)?.len(),"archive_remaining":MAX_COMPLETED-all.len(),"provider_writes":false}))
}

#[cfg(test)]mod tests {
    use super::*;
    fn request(root:&Path,action:&str,fields:Value)->Value{let mut j=fields;j["action"]=json!(action);history_journal::dispatch(root,&j).unwrap()}
    fn fixture(root:&Path,id:&str,inserted:bool){
        let mut backup=messaging::dispatch(root,&json!({"action":"backup_export"})).unwrap();
        backup["messages"]["sms:1"]=json!({"date":1,"starred":true});
        let mut flags=BTreeMap::new();flags.insert("sms:1".into(),("sms:91".into(),70));
        let receipt=messaging::merge_history(root,id,messaging::HistoryMerge{archive_sha:"a".repeat(64),plan_sha:"b".repeat(64),post_plan_sha:"c".repeat(64),flags_imported:0,threads_imported:0},&backup,&flags,&BTreeMap::new(),root).unwrap();
        let mut row=json!({"kind":"message","source":"sms:1","target":"sms:91","decision":"match"});
        if inserted{
            let t=request(root,"begin",json!({"kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"d".repeat(64)}));
            let txn=t["id"].clone();request(root,"insert_intent",json!({"id":txn}));request(root,"row",json!({"id":txn,"target":"sms:91","receipt":"e".repeat(64)}));request(root,"commit",json!({"id":txn}));
            row["decision"]=json!("inserted");row["transaction"]=txn;
        }
        let h=json!({"format":"onklav-restore-outcomes","version":1,"archive_sha":receipt.archive_sha,"plan_sha":receipt.plan_sha,"metadata_applied":true,"phone_revalidated":true,"post_plan_sha":receipt.post_plan_sha,"flags_imported":receipt.flags_imported,"threads_imported":receipt.threads_imported});
        let dir=root.join("history-restore-maps");fs::create_dir_all(&dir).unwrap();fs::write(dir.join(format!("{id}.jsonl")),format!("{h}\n{row}\n")).unwrap();
    }
    fn state(root:&Path)->Value{messaging::dispatch(root,&json!({"action":"snapshot"})).unwrap()}
    #[test]fn completed_handoff_keeps_summary_journal_and_metadata_replay_protection(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let id="1".repeat(32);fixture(r,&id,true);
        messaging::dispatch(r,&json!({"action":"draft","key":"70","text":"keep private draft"})).unwrap();
        let mut before=state(r);before["history_merges"]=json!({});
        assert_eq!(compact(r).unwrap()["compacted"],1);assert!(raw_ids(r).unwrap().is_empty());assert_eq!(state(r),before);
        let detail=history_recovery::dispatch(r,&json!({"action":"map_detail","id":id})).unwrap();assert_eq!(detail["inserted"],1);assert_eq!(detail["compacted"],true);assert!(detail.get("source").is_none());
        assert_eq!(history_recovery::dispatch(r,&json!({"action":"overview"})).unwrap()["total"],1);
        request(r,"compact",json!({}));assert_eq!(request(r,"receipts",json!({"archive_sha":"a".repeat(64)}))["items"].as_array().unwrap().len(),1);
        assert!(history_journal::dispatch(r,&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"d".repeat(64)})).is_err());
        messaging::dispatch(r,&json!({"action":"remove_message","key":"sms:91"})).unwrap();let bytes=fs::read(r.join("messenger.json")).unwrap();
        let mut backup=messaging::dispatch(r,&json!({"action":"backup_export"})).unwrap();backup["messages"]["sms:1"]=json!({"starred":true});
        let flags=BTreeMap::from([("sms:1".into(),("sms:91".into(),70))]);let receipt=messaging::history_receipt(r,&id).unwrap().unwrap();
        messaging::merge_history(r,&id,receipt.clone(),&backup,&flags,&BTreeMap::new(),r).unwrap();assert_eq!(bytes,fs::read(r.join("messenger.json")).unwrap());
        assert!(crate::history_apply::repair_ack(r,&id).unwrap()["compacted"].as_bool().unwrap());assert_eq!(bytes,fs::read(r.join("messenger.json")).unwrap());
        let mut changed=receipt;changed.plan_sha="f".repeat(64);assert!(messaging::merge_history(r,&id,changed,&backup,&flags,&BTreeMap::new(),r).is_err());
    }
    #[test]fn interruption_before_and_after_metadata_prune_can_finish_without_replay(){
        for prune_metadata in [false,true]{let d=tempfile::tempdir().unwrap();let r=d.path();let id="2".repeat(32);fixture(r,&id,false);
            save(r,prepare(r).unwrap()).unwrap();if prune_metadata{messaging::compact_history_receipts(r).unwrap();}
            assert!(messaging::history_receipt(r,&id).unwrap().is_some());assert_eq!(raw_ids(r).unwrap().len(),1);
            assert_eq!(compact(r).unwrap()["compacted"],1);assert_eq!(compact(r).unwrap()["compacted"],0);assert!(state(r)["history_merges"].as_object().unwrap().is_empty());assert!(state(r)["messages"]["sms:91"]["starred"].as_bool().unwrap());
        }
    }
    #[test]fn incomplete_and_unacknowledged_maps_are_retained_in_full(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let id="3".repeat(32);fixture(r,&id,false);let path=r.join("history-restore-maps").join(format!("{id}.jsonl"));
        let original=fs::read_to_string(&path).unwrap();let mut rows=history_recovery::saved_map(r,&id).unwrap();rows[0]["metadata_applied"]=json!(false);fs::write(&path,rows.iter().map(|r|format!("{r}\n")).collect::<String>()).unwrap();
        assert_eq!(compact(r).unwrap()["compacted"],0);assert!(state(r)["history_merges"].get(&id).is_some());
        fs::write(&path,&original).unwrap();rows=history_recovery::saved_map(r,&id).unwrap();rows[1]["decision"]=json!("pending");rows[1]["target"]=Value::Null;let pending=rows.iter().map(|r|format!("{r}\n")).collect::<String>();fs::write(&path,&pending).unwrap();
        assert_eq!(compact(r).unwrap()["compacted"],0);assert_eq!(fs::read_to_string(&path).unwrap(),pending);
        rows[1]["decision"]=json!("review");let review=rows.iter().map(|r|format!("{r}\n")).collect::<String>();fs::write(&path,&review).unwrap();
        assert_eq!(compact(r).unwrap()["compacted"],0);assert_eq!(fs::read_to_string(&path).unwrap(),review);assert!(state(r)["history_merges"].get(&id).is_some());
        let txn=request(r,"begin",json!({"kind":"sms","source":"sms:8","archive_sha":"a".repeat(64),"content_sha":"d".repeat(64)}));request(r,"insert_intent",json!({"id":txn["id"]}));request(r,"recover",json!({"id":txn["id"]}));compact(r).unwrap();assert_eq!(request(r,"get",json!({"id":txn["id"]}))["phase"],"uncertain");
    }
    #[test]fn forged_insert_or_conflicting_raw_copy_never_loses_evidence(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let id="4".repeat(32);fixture(r,&id,false);let path=r.join("history-restore-maps").join(format!("{id}.jsonl"));let original=fs::read_to_string(&path).unwrap();
        let forged=original.replace("\"decision\":\"match\"","\"decision\":\"inserted\"");fs::write(&path,&forged).unwrap();let before=fs::read(r.join("messenger.json")).unwrap();assert!(compact(r).is_err());assert_eq!(before,fs::read(r.join("messenger.json")).unwrap());assert!(!r.join("history-completed.archive").exists());
        fs::write(&path,&original).unwrap();save(r,prepare(r).unwrap()).unwrap();fs::write(&path,format!("{original}\n")).unwrap();assert!(compact(r).is_err());assert!(history_recovery::dispatch(r,&json!({"action":"map_detail","id":id})).is_err());assert_eq!(before,fs::read(r.join("messenger.json")).unwrap());assert!(path.exists());
    }
    #[test]fn full_active_receipts_and_maps_are_reclaimed_but_archive_overflow_is_refused(){
        let d=tempfile::tempdir().unwrap();let r=d.path();for n in 1..=100{fixture(r,&format!("{n:032x}"),false);}
        assert_eq!(raw_ids(r).unwrap().len(),100);assert_eq!(state(r)["history_merges"].as_object().unwrap().len(),100);assert_eq!(compact(r).unwrap()["active_remaining"],100);assert!(state(r)["history_merges"].as_object().unwrap().is_empty());
        fixture(r,&format!("{:032x}",101),false);
        let page=history_recovery::dispatch(r,&json!({"action":"overview","offset":100})).unwrap();assert_eq!(page["total"],101);assert_eq!(page["maps"].as_array().unwrap().len(),1);
        let mut archive=records(r).unwrap();let row=archive.values().next().unwrap().clone();for n in 102..=1001{let mut r=row.clone();r.id=format!("{n:032x}");archive.insert(r.id.clone(),r);}assert_eq!(archive.len(),1000);save(r,archive).unwrap();
        let bytes=fs::read(r.join("messenger.json")).unwrap();assert!(compact(r).is_err());assert_eq!(raw_ids(r).unwrap().len(),1);assert_eq!(bytes,fs::read(r.join("messenger.json")).unwrap());assert_eq!(records(r).unwrap().len(),1000);
    }
    #[test]fn conflicting_active_metadata_receipt_blocks_all_handoff_consumers(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let id="7".repeat(32);fixture(r,&id,false);save(r,prepare(r).unwrap()).unwrap();
        let path=r.join("messenger.json");let mut state:Value=serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();state["history_merges"][&id]["plan_sha"]=json!("f".repeat(64));fs::write(&path,state.to_string()).unwrap();let before=fs::read(&path).unwrap();
        assert!(compact(r).is_err());assert!(messaging::history_receipt(r,&id).is_err());assert!(crate::history_apply::repair_ack(r,&id).is_err());assert!(history_recovery::dispatch(r,&json!({"action":"map_detail","id":id})).is_err());
        assert_eq!(before,fs::read(path).unwrap());assert_eq!(raw_ids(r).unwrap().len(),1);
    }
    #[test]fn corrupt_duplicate_and_symlink_archives_fail_closed(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let id="5".repeat(32);fixture(r,&id,false);save(r,prepare(r).unwrap()).unwrap();let path=r.join("history-completed.archive");let row=records(r).unwrap().remove(&id).unwrap();
        fs::write(&path,json!({"version":1,"records":[row.clone(),row]}).to_string()).unwrap();assert!(records(r).is_err());assert!(messaging::history_receipt(r,&id).is_err());assert!(compact(r).is_err());
        fs::write(&path,b"bad").unwrap();assert!(compact(r).is_err());assert_eq!(raw_ids(r).unwrap().len(),1);
        #[cfg(unix)]{fs::remove_file(&path).unwrap();let other=r.join("outside");fs::write(&other,b"untouched").unwrap();std::os::unix::fs::symlink(&other,&path).unwrap();assert!(compact(r).is_err());assert_eq!(fs::read(other).unwrap(),b"untouched");}
    }
}
