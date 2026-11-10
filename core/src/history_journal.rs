//! Trusted host per-message restore journal. This records outcomes, never grants
//! provider permission or treats an interrupted insert as safe to repeat.
use crate::Result;
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{fs::{self,File,OpenOptions},io::Write,path::{Path,PathBuf},sync::Mutex};
static LOCK:Mutex<()>=Mutex::new(());
const MAX_RECORDS:usize=1000;
#[derive(Clone,Serialize,Deserialize,PartialEq,Debug)]
#[serde(rename_all="snake_case")]
enum Phase{Prepared,Inserting,RowKnown,Completing,Committed,Cleaning,CleanupRequired,RolledBack,Uncertain,NeedsReview}
#[derive(Clone,Serialize,Deserialize,PartialEq,Debug)]
#[serde(deny_unknown_fields)]
struct Transaction {version:u32,id:String,source:String,archive_sha:String,content_sha:String,kind:String,phase:Phase,target:Option<String>,receipt:Option<String>,#[serde(default,skip_serializing_if="Option::is_none")] resolution:Option<String>}
fn hash(s:&str)->bool{s.len()==64&&s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))}
fn key(s:&str,kind:&str)->bool{s.strip_prefix(&format!("{kind}:")).is_some_and(|n|n.parse::<u64>().is_ok_and(|i|i>0&&i<=i64::MAX as u64&&i.to_string()==n))}
fn base(root:&Path)->Result<PathBuf>{let p=root.join("history-restore-journal");fs::create_dir_all(&p).map_err(|e|e.to_string())?;if !fs::symlink_metadata(&p).map_err(|e|e.to_string())?.is_dir(){return Err("Invalid restore journal storage".into())}Ok(p)}
fn path(root:&Path,id:&str)->Result<PathBuf>{if !crate::valid_id(id){return Err("Invalid restore transaction".into())}Ok(base(root)?.join(format!("{id}.json")))}
fn validate(t:&Transaction)->Result<()> {
    if t.version!=1||!crate::valid_id(&t.id)||!matches!(t.kind.as_str(),"sms"|"mms")||!key(&t.source,&t.kind)||!hash(&t.archive_sha)||!hash(&t.content_sha)
        ||t.target.as_ref().is_some_and(|s|!key(s,&t.kind))||t.receipt.as_ref().is_some_and(|s|!hash(s))||t.target.is_some()!=t.receipt.is_some(){return Err("Invalid restore journal record".into())}
    if t.resolution.as_deref().is_some_and(|r|r!="verified_absent"||t.phase!=Phase::RolledBack||t.target.is_none()){return Err("Invalid restore resolution".into())}
    if matches!(t.phase,Phase::RowKnown|Phase::Completing|Phase::Committed|Phase::Cleaning|Phase::CleanupRequired)&&t.target.is_none(){return Err("Missing provider receipt".into())}Ok(())
}
fn load(root:&Path,id:&str)->Result<Transaction>{let p=path(root,id)?;let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !m.is_file()||m.len()>4096{return Err("Invalid restore journal file".into())}let t:Transaction=serde_json::from_slice(&fs::read(p).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;validate(&t)?;if t.id!=id{return Err("Restore transaction identity mismatch".into())}Ok(t)}
fn raw_records(root:&Path)->Result<Vec<Transaction>> {
    let mut found=Vec::new();for entry in fs::read_dir(base(root)?).map_err(|e|e.to_string())? {
        let entry=entry.map_err(|e|e.to_string())?;let name=entry.file_name().to_string_lossy().to_string();
        if let Some(id)=name.strip_suffix(".json"){if found.len()>=MAX_RECORDS{return Err("Restore journal limit reached".into())}found.push(load(root,id)?);}
    }found.sort_unstable_by(|a,b|a.id.cmp(&b.id));Ok(found)
}
// The archive is an immutable-outcome ledger, never a reset. Commit it before
// pruning raw records; identical duplicates are accepted after interrupted prune.
const MAX_ARCHIVED:usize=10000;
const MAX_ARCHIVE_BYTES:u64=8*1024*1024;
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Archive{version:u32,records:Vec<Transaction>}
fn terminal(t:&Transaction)->bool{matches!(t.phase,Phase::Committed|Phase::RolledBack)}
fn archived(root:&Path)->Result<Vec<Transaction>>{
    let p=base(root)?.join("terminal.archive");
    let m=match fs::symlink_metadata(&p){Ok(m)=>m,Err(e) if e.kind()==std::io::ErrorKind::NotFound=>return Ok(Vec::new()),Err(e)=>return Err(e.to_string())};
    if !m.is_file()||m.len()>MAX_ARCHIVE_BYTES{return Err("Invalid restore receipt archive".into())}
    let a:Archive=serde_json::from_slice(&fs::read(p).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    if a.version!=1||a.records.len()>MAX_ARCHIVED{return Err("Restore receipt archive limit reached".into())}
    let mut ids=std::collections::BTreeSet::new();
    let mut sources=std::collections::BTreeSet::new();
    for t in &a.records{validate(t)?;if !terminal(t)||!ids.insert(t.id.clone())||t.phase==Phase::Committed&&!sources.insert((t.archive_sha.clone(),t.source.clone())){return Err("Invalid archived restore identity".into())}}
    Ok(a.records)
}
fn records(root:&Path)->Result<Vec<Transaction>>{
    let mut all=std::collections::BTreeMap::new();
    let mut committed=std::collections::BTreeSet::new();
    for t in archived(root)?.into_iter().chain(raw_records(root)?){
        if let Some(prior)=all.get(&t.id){if prior!=&t{return Err("Conflicting restore receipt copies".into())}continue}
        if t.phase==Phase::Committed&&!committed.insert((t.archive_sha.clone(),t.source.clone())){return Err("Duplicate committed restore source".into())}
        all.insert(t.id.clone(),t);
    }Ok(all.into_values().collect())
}
fn save_archive(root:&Path,rows:Vec<Transaction>)->Result<()>{
    if rows.len()>MAX_ARCHIVED{return Err("Restore receipt archive limit reached; no records removed".into())}
    let bytes=serde_json::to_vec(&Archive{version:1,records:rows}).map_err(|e|e.to_string())?;
    if bytes.len() as u64>MAX_ARCHIVE_BYTES{return Err("Restore receipt archive byte limit reached".into())}
    let dir=base(root)?;let tmp=dir.join(format!("{}.tmp",uuid::Uuid::new_v4().simple()));
    let result=(||{let mut f=OpenOptions::new().create_new(true).write(true).open(&tmp).map_err(|e|e.to_string())?;
        f.write_all(&bytes).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;
        fs::rename(&tmp,dir.join("terminal.archive")).map_err(|e|e.to_string())?;
        File::open(&dir).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();
    if result.is_err(){let _=fs::remove_file(tmp);}result
}
fn compact(root:&Path)->Result<Value>{
    let all=records(root)?;let raw=raw_records(root)?;
    let count=raw.iter().filter(|t|terminal(t)).count();
    save_archive(root,all.into_iter().filter(terminal).collect())?;
    // An interrupted unlink leaves duplicate evidence; readers reconcile it.
    for t in raw.iter().filter(|t|terminal(t)){fs::remove_file(path(root,&t.id)?).map_err(|e|e.to_string())?;}
    File::open(base(root)?).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;
    Ok(json!({"compacted":count,"remaining":MAX_RECORDS-raw_records(root)?.len(),"provider_writes":false}))
}
fn save(root:&Path,t:&Transaction)->Result<()> {
    validate(t)?;let destination=path(root,&t.id)?;let tmp=base(root)?.join(format!("{}.tmp",uuid::Uuid::new_v4().simple()));
    let result=(||{let mut f=OpenOptions::new().create_new(true).write(true).open(&tmp).map_err(|e|e.to_string())?;
        f.write_all(&serde_json::to_vec(t).map_err(|e|e.to_string())?).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;
        fs::rename(&tmp,destination).map_err(|e|e.to_string())?;File::open(base(root)?).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();if result.is_err(){let _=fs::remove_file(tmp);}result
}
fn text<'a>(j:&'a Value,key:&str)->Result<&'a str>{j[key].as_str().ok_or_else(||format!("Missing journal {key}"))}
pub fn dispatch(root:&Path,j:&Value)->Result<Value> {
    let _guard=LOCK.lock().map_err(|_|"Restore journal unavailable")?;let action=text(j,"action")?;
    if action=="compact"{return compact(root)}
    if action=="receipts" {let archive=text(j,"archive_sha")?;if !hash(archive){return Err("Invalid archive identity".into())}return Ok(json!({"items":records(root)?.into_iter().filter(|t|t.archive_sha==archive&&t.phase==Phase::Committed).map(|t|json!(t)).collect::<Vec<_>>()}))}
    if action=="pending" {return Ok(json!({"items":records(root)?.into_iter().filter(|t|!matches!(t.phase,Phase::Committed|Phase::RolledBack)).map(|t|json!({"id":t.id,"phase":t.phase,"target":t.target})).collect::<Vec<_>>()}))}
    if action=="status" {
        let rows=records(root)?;return Ok(json!({"committed":rows.iter().filter(|t|t.phase==Phase::Committed).count(),"rolled_back":rows.iter().filter(|t|t.phase==Phase::RolledBack).count(),
            "pending":rows.iter().filter(|t|!matches!(t.phase,Phase::Committed|Phase::RolledBack)).count(),"remaining":MAX_RECORDS-raw_records(root)?.len(),"archived":archived(root)?.len(),"provider_writes":false}));
    }
    if action=="begin" {
        let rows=records(root)?;if raw_records(root)?.len()>=MAX_RECORDS{return Err("Restore journal limit reached; preserve outcomes before continuing".into())}
        if rows.iter().any(|t|!matches!(t.phase,Phase::Committed|Phase::RolledBack)){return Err("Resolve the interrupted restore before another insert".into())}
        let source=text(j,"source")?.to_string();let archive=text(j,"archive_sha")?.to_string();let content=text(j,"content_sha")?.to_string();
        if rows.iter().any(|t|t.source==source&&t.archive_sha==archive&&t.phase==Phase::Committed){return Err("This source message already has a committed restore receipt".into())}
        let t=Transaction{version:1,id:uuid::Uuid::new_v4().simple().to_string(),kind:text(j,"kind")?.to_string(),source,archive_sha:archive,content_sha:content,phase:Phase::Prepared,target:None,receipt:None,resolution:None};save(root,&t)?;return Ok(json!(t));
    }
    let id=text(j,"id")?;let mut t=records(root)?.into_iter().find(|t|t.id==id).ok_or("Unknown restore transaction")?;
    if action=="get"||action=="recover"&&terminal(&t){return Ok(json!(t))}
    match action {
        "insert_intent" if t.phase==Phase::Prepared=>t.phase=Phase::Inserting,
        "no_effect" if matches!(t.phase,Phase::Prepared|Phase::Inserting)=>t.phase=Phase::RolledBack,
        "row" if t.phase==Phase::Inserting=>{t.target=Some(text(j,"target")?.to_string());t.receipt=Some(text(j,"receipt")?.to_string());t.phase=Phase::RowKnown;},
        "complete_intent" if t.phase==Phase::RowKnown=>t.phase=Phase::Completing,
        "commit" if matches!(t.phase,Phase::RowKnown|Phase::Completing)=>t.phase=Phase::Committed,
        "cleanup_intent" if matches!(t.phase,Phase::RowKnown|Phase::Completing|Phase::CleanupRequired)=>t.phase=Phase::Cleaning,
        "cleaned" if t.phase==Phase::Cleaning=>t.phase=Phase::RolledBack,
        // Host-only acknowledgement after explicit repeated parent/child absence
        // checks. Never accepts a guessed target or resolves an unknown insert.
        "reviewed_absent" if t.phase==Phase::NeedsReview&&t.target.is_some()
            &&t.target.as_deref()==Some(text(j,"target")?)&&t.receipt.as_deref()==Some(text(j,"receipt")?)=>{
                t.phase=Phase::RolledBack;t.resolution=Some("verified_absent".into());
            },
        "review" if !matches!(t.phase,Phase::Committed|Phase::RolledBack)=>t.phase=Phase::NeedsReview,
        "uncertain" if t.phase==Phase::Inserting=>t.phase=Phase::Uncertain,
        "recover"=>{t.phase=match t.phase{Phase::Prepared=>Phase::RolledBack,Phase::Inserting=>Phase::Uncertain,Phase::RowKnown|Phase::Completing|Phase::Cleaning=>Phase::CleanupRequired,other=>other};},
        _=>return Err("Invalid restore journal transition".into())
    }
    save(root,&t)?;Ok(json!(t))
}
#[cfg(test)]mod tests {
    use super::*;
    fn begin(root:&Path)->Value{dispatch(root,&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).unwrap()}
    fn step(root:&Path,id:&Value,action:&str)->Result<Value>{dispatch(root,&json!({"action":action,"id":id}))}
    #[test]fn uncertain_insert_blocks_replay_and_cannot_be_claimed_successful(){let t=tempfile::tempdir().unwrap();let row=begin(t.path());let id=&row["id"];step(t.path(),id,"insert_intent").unwrap();assert_eq!(step(t.path(),id,"recover").unwrap()["phase"],"uncertain");assert!(step(t.path(),id,"commit").is_err());assert!(dispatch(t.path(),&json!({"action":"begin","kind":"sms","source":"sms:2","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).is_err());}
    #[test]fn committed_receipt_survives_restart_and_cannot_be_cleaned_or_reinserted(){let t=tempfile::tempdir().unwrap();let row=begin(t.path());let id=&row["id"];step(t.path(),id,"insert_intent").unwrap();dispatch(t.path(),&json!({"action":"row","id":id,"target":"sms:91","receipt":"c".repeat(64)})).unwrap();step(t.path(),id,"commit").unwrap();assert_eq!(step(t.path(),id,"recover").unwrap()["target"],"sms:91");assert!(step(t.path(),id,"cleanup_intent").is_err());assert!(dispatch(t.path(),&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).is_err());}
    #[test]fn known_incomplete_rows_recover_for_cleanup_and_never_change_ids(){let t=tempfile::tempdir().unwrap();let row=begin(t.path());let id=&row["id"];step(t.path(),id,"insert_intent").unwrap();dispatch(t.path(),&json!({"action":"row","id":id,"target":"sms:91","receipt":"c".repeat(64)})).unwrap();step(t.path(),id,"complete_intent").unwrap();assert_eq!(step(t.path(),id,"recover").unwrap()["phase"],"cleanup_required");assert!(dispatch(t.path(),&json!({"action":"row","id":id,"target":"sms:92","receipt":"d".repeat(64)})).is_err());step(t.path(),id,"cleanup_intent").unwrap();step(t.path(),id,"cleaned").unwrap();assert_eq!(step(t.path(),id,"recover").unwrap()["phase"],"rolled_back");}
    #[test]fn invalid_receipts_and_corruption_fail_closed(){let t=tempfile::tempdir().unwrap();let row=begin(t.path());let id=&row["id"];step(t.path(),id,"insert_intent").unwrap();assert!(dispatch(t.path(),&json!({"action":"row","id":id,"target":"mms:5","receipt":"c".repeat(64)})).is_err());assert_eq!(step(t.path(),id,"get").unwrap()["phase"],"inserting");fs::write(path(t.path(),id.as_str().unwrap()).unwrap(),b"bad").unwrap();assert!(dispatch(t.path(),&json!({"action":"status"})).is_err());}
    fn commit(root:&Path)->Value{let t=begin(root);let id=&t["id"];step(root,id,"insert_intent").unwrap();dispatch(root,&json!({"action":"row","id":id,"target":"sms:91","receipt":"c".repeat(64)})).unwrap();step(root,id,"commit").unwrap()}
    #[test]fn compaction_preserves_receipt_consumers_and_replay_guard(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let t=commit(r);
        assert_eq!(dispatch(r,&json!({"action":"compact"})).unwrap()["compacted"],1);
        assert!(raw_records(r).unwrap().is_empty());assert_eq!(step(r,&t["id"],"get").unwrap(),t);
        assert_eq!(dispatch(r,&json!({"action":"receipts","archive_sha":"a".repeat(64)})).unwrap()["items"][0],t);
        assert!(dispatch(r,&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).is_err());
        assert!(step(r,&t["id"],"cleanup_intent").is_err());step(r,&t["id"],"recover").unwrap();assert!(raw_records(r).unwrap().is_empty());
    }
    #[test]fn interrupted_archive_handoff_is_idempotent_and_preserves_unknowns(){
        let d=tempfile::tempdir().unwrap();let r=d.path();commit(r);
        // Simulate a crash after archive fsync but before any raw unlink.
        save_archive(r,records(r).unwrap()).unwrap();assert_eq!(records(r).unwrap().len(),1);
        dispatch(r,&json!({"action":"compact"})).unwrap();assert_eq!(records(r).unwrap().len(),1);
        let t=dispatch(r,&json!({"action":"begin","kind":"sms","source":"sms:2","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).unwrap();
        step(r,&t["id"],"insert_intent").unwrap();step(r,&t["id"],"recover").unwrap();
        dispatch(r,&json!({"action":"compact"})).unwrap();assert_eq!(step(r,&t["id"],"get").unwrap()["phase"],"uncertain");
        assert_eq!(dispatch(r,&json!({"action":"pending"})).unwrap()["items"].as_array().unwrap().len(),1);
        assert!(dispatch(r,&json!({"action":"begin","kind":"sms","source":"sms:3","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).is_err());
    }
    #[test]fn full_active_journal_frees_slots_but_archive_bound_never_discards_receipts(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let mut t:Transaction=serde_json::from_value(commit(r)).unwrap();
        // Fill active slots with terminal cleanup outcomes, retaining the commit.
        for n in 1..MAX_RECORDS{let row=Transaction{version:1,id:format!("{n:032x}"),source:format!("sms:{}",n+1),archive_sha:"a".repeat(64),content_sha:"b".repeat(64),kind:"sms".into(),phase:Phase::RolledBack,target:None,receipt:None,resolution:None};save(r,&row).unwrap();}
        assert_eq!(dispatch(r,&json!({"action":"status"})).unwrap()["remaining"],0);
        assert_eq!(compact(r).unwrap()["remaining"],1000);
        assert_eq!(dispatch(r,&json!({"action":"receipts","archive_sha":"a".repeat(64)})).unwrap()["items"].as_array().unwrap().len(),1);
        let mut rows=archived(r).unwrap();for n in MAX_RECORDS..MAX_ARCHIVED{t.id=format!("{n:032x}");t.phase=Phase::RolledBack;rows.push(t.clone());}save_archive(r,rows).unwrap();
        t.id="f".repeat(32);save(r,&t).unwrap();let before=fs::read(base(r).unwrap().join("terminal.archive")).unwrap();
        assert!(compact(r).is_err());assert_eq!(raw_records(r).unwrap().len(),1);assert_eq!(before,fs::read(base(r).unwrap().join("terminal.archive")).unwrap());
    }
    #[test]fn corrupt_or_conflicting_archive_cannot_free_capacity(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let t=commit(r);save_archive(r,records(r).unwrap()).unwrap();
        let mut raw=load(r,t["id"].as_str().unwrap()).unwrap();raw.target=Some("sms:92".into());save(r,&raw).unwrap();
        assert!(dispatch(r,&json!({"action":"compact"})).is_err());assert_eq!(raw_records(r).unwrap().len(),1);
        fs::write(base(r).unwrap().join("terminal.archive"),b"bad").unwrap();
        for action in ["status","pending","compact"]{assert!(dispatch(r,&json!({"action":action})).is_err());}
        assert_eq!(raw_records(r).unwrap().len(),1);
    }
    #[cfg(unix)]#[test]fn symlink_archive_cannot_be_overwritten(){let d=tempfile::tempdir().unwrap();let r=d.path();commit(r);let other=d.path().join("outside");fs::write(&other,b"untouched").unwrap();std::os::unix::fs::symlink(&other,base(r).unwrap().join("terminal.archive")).unwrap();assert!(dispatch(r,&json!({"action":"compact"})).is_err());assert_eq!(fs::read(other).unwrap(),b"untouched");assert_eq!(raw_records(r).unwrap().len(),1);}
    #[cfg(unix)] #[test] fn symlink_storage_fails_closed(){let t=tempfile::tempdir().unwrap();let other=tempfile::tempdir().unwrap();std::os::unix::fs::symlink(other.path(),t.path().join("history-restore-journal")).unwrap();assert!(dispatch(t.path(),&json!({"action":"status"})).is_err());assert!(fs::read_dir(other.path()).unwrap().next().is_none());}

    #[test]fn reviewed_absence_keeps_evidence_through_compaction(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let t=begin(r);let id=&t["id"];
        step(r,id,"insert_intent").unwrap();dispatch(r,&json!({"action":"row","id":id,"target":"sms:91","receipt":"c".repeat(64)})).unwrap();step(r,id,"review").unwrap();
        assert!(dispatch(r,&json!({"action":"reviewed_absent","id":id,"target":"sms:92","receipt":"c".repeat(64)})).is_err());
        assert!(dispatch(r,&json!({"action":"reviewed_absent","id":id,"target":"sms:91","receipt":"d".repeat(64)})).is_err());
        let resolved=dispatch(r,&json!({"action":"reviewed_absent","id":id,"target":"sms:91","receipt":"c".repeat(64)})).unwrap();
        assert_eq!(resolved["phase"],"rolled_back");assert_eq!(resolved["resolution"],"verified_absent");assert_eq!(resolved["source"],"sms:1");
        compact(r).unwrap();assert_eq!(step(r,id,"get").unwrap(),resolved);assert_eq!(step(r,id,"recover").unwrap(),resolved);
        assert!(dispatch(r,&json!({"action":"receipts","archive_sha":"a".repeat(64)})).unwrap()["items"].as_array().unwrap().is_empty());
        // Resolution only removes the global block. It does not perform a retry.
        assert_eq!(dispatch(r,&json!({"action":"status"})).unwrap()["pending"],0);
    }
    #[test]fn unknown_and_committed_outcomes_cannot_be_resolved_as_absent(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let t=begin(r);let id=&t["id"];step(r,id,"insert_intent").unwrap();step(r,id,"recover").unwrap();
        for phase in ["uncertain","needs_review"]{
            if phase=="needs_review"{step(r,id,"review").unwrap();}
            assert!(dispatch(r,&json!({"action":"reviewed_absent","id":id,"target":"sms:91","receipt":"c".repeat(64)})).is_err());
            assert_eq!(step(r,id,"get").unwrap()["phase"],phase);
        }
        let d=tempfile::tempdir().unwrap();let t=commit(d.path());
        assert!(dispatch(d.path(),&json!({"action":"reviewed_absent","id":t["id"],"target":"sms:91","receipt":"c".repeat(64)})).is_err());
    }
    #[test]fn invalid_resolution_marker_cannot_clear_pending_evidence(){
        let d=tempfile::tempdir().unwrap();let r=d.path();let t=begin(r);let mut row:Transaction=serde_json::from_value(t).unwrap();
        row.resolution=Some("verified_absent".into());assert!(save(r,&row).is_err());row.phase=Phase::RolledBack;assert!(save(r,&row).is_err());
        row.target=Some("sms:91".into());row.receipt=Some("c".repeat(64));row.resolution=Some("user_claim".into());assert!(save(r,&row).is_err());
    }

}
