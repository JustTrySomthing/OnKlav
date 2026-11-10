//! Host-only continuation of a recorded restore; committed IDs are never replayed.
use crate::{history_backup,history_journal,history_recovery,messaging,Result};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::BTreeMap,fs::File,io::Read,path::Path};
fn hash(s:&str)->bool{s.len()==64&&s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))}
pub(super) fn digest(root:&Path,id:&str)->Result<String>{
    if !crate::valid_id(id){return Err("Invalid restore record".into())}
    history_backup::dir(&root.join("history-restore-maps"))?;
    let path=root.join("history-restore-maps").join(format!("{id}.jsonl"));history_backup::regular(&path,128*1024*1024)?;
    let mut f=File::open(path).map_err(|e|e.to_string())?;let mut h=Sha256::new();let mut b=[0;65536];loop{let n=f.read(&mut b).map_err(|e|e.to_string())?;if n==0{break}h.update(&b[..n]);}Ok(format!("{:x}",h.finalize()))
}
// Recover the map from durable commit receipts, including a crash between a
// provider commit and map acknowledgement. No content match can invent a receipt.
fn effective(root:&Path,id:&str,expected:&str)->Result<Vec<Value>>{
    if !hash(expected)||digest(root,id)?!=expected{return Err("Saved restore record changed; reopen and review again".into())}
    let mut rows=history_recovery::saved_map(root,id)?;
    if rows[0]["metadata_applied"]!=false||messaging::history_receipt(root,id)?.is_some(){return Err("This restore already has saved metadata; finish its saved record instead".into())}
    let receipts=history_journal::dispatch(root,&json!({"action":"receipts","archive_sha":rows[0]["archive_sha"]}))?;
    let committed:BTreeMap<&str,&Value>=receipts["items"].as_array().ok_or("Missing journal receipts")?.iter().map(|r|Ok((r["source"].as_str().ok_or("Missing transaction source")?,r))).collect::<Result<_>>()?;
    for row in rows.iter_mut().skip(1){
        if row["decision"]=="pending"{if let Some(receipt)=row["source"].as_str().and_then(|s|committed.get(s)){
            row["decision"]=json!("inserted");row["target"]=receipt["target"].clone();row["transaction"]=receipt["id"].clone();
        }}
        if row["decision"]=="inserted"{
            let receipt=row["source"].as_str().and_then(|s|committed.get(s)).ok_or("Committed insertion receipt is missing")?;
            if receipt["id"]!=row["transaction"]||receipt["target"]!=row["target"]{return Err("Recorded insertion does not match its receipt".into())}
        }
    }
    if digest(root,id)?!=expected{return Err("Saved restore record changed during review".into())}Ok(rows)
}
fn pending(rows:&[Value])->Result<Vec<String>>{
    let mut out=rows.iter().skip(1).filter(|r|r["decision"]=="pending").map(|r|r["source"].as_str().map(str::to_string).ok_or_else(||"Missing pending source".to_string())).collect::<Result<Vec<_>>>()?;
    if out.len()>1000{return Err("Pending restore exceeds batch limits".into())}out.sort();Ok(out)
}
pub(super) fn bound(root:&Path,id:&str,expected:&str)->Result<Vec<Value>>{
    let rows=effective(root,id,expected)?;
    if history_journal::dispatch(root,&json!({"action":"status"}))?["pending"]!=0{return Err("Resolve interrupted insertions before continuing this record".into())}
    pending(&rows)?;Ok(rows)
}
pub(super) fn info(root:&Path,id:&str)->Result<Value>{let sha=digest(root,id)?;let rows=bound(root,id,&sha)?;
    Ok(json!({"id":id,"map_sha":sha,"archive_sha":rows[0]["archive_sha"],"plan_sha":rows[0]["plan_sha"],"pending_sources":pending(&rows)?,"provider_writes":false}))
}
// Acknowledge only committed receipts, even on Stop or while a later insertion
// remains uncertain. Keep pending/unknown records and the original plan/job.
pub(super) fn checkpoint(root:&Path,id:&str,expected:&str)->Result<Value>{
    use std::{fs::{self,OpenOptions},io::Write};
    let rows=effective(root,id,expected)?;let dir=root.join("history-restore-maps");let path=dir.join(format!("{id}.jsonl"));
    let original=history_recovery::saved_map(root,id)?;
    if original!=rows{
        let tmp=dir.join(format!("{}.tmp",uuid::Uuid::new_v4().simple()));
        let result=(||{let mut f=OpenOptions::new().write(true).create_new(true).open(&tmp).map_err(|e|e.to_string())?;
            for row in &rows{writeln!(f,"{row}").map_err(|e|e.to_string())?;}f.sync_all().map_err(|e|e.to_string())?;
            if digest(root,id)?!=expected{return Err("Saved restore changed before checkpoint".to_string())}
            fs::rename(&tmp,&path).map_err(|e|e.to_string())?;File::open(&dir).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();
        if result.is_err(){let _=fs::remove_file(tmp);}result?;
    }
    Ok(json!({"id":id,"map_sha":digest(root,id)?,"pending":pending(&rows)?.len(),"provider_writes":false}))
}
pub(super) fn compare(root:&Path,stage:&Path,id:&str,expected:&str,review:&Value)->Result<Value>{
    let rows=bound(root,id,expected)?;
    if rows[0]["archive_sha"]!=review["sha256"]{return Err("Choose the original backup for this restore record".into())}
    let path=stage.join("comparison.jsonl");history_backup::regular(&path,128*1024*1024)?;
    use std::io::{BufRead,BufReader};let mut fresh=BTreeMap::new();let mut reader=BufReader::new(File::open(path).map_err(|e|e.to_string())?);
    loop{history_backup::check_cancel(stage)?;let mut line=Vec::new();let n=(&mut reader).take(8193).read_until(b'\n',&mut line).map_err(|e|e.to_string())?;if n==0{break}if n>8192{return Err("Comparison row exceeds limits".into())}
        let row:Value=serde_json::from_slice(&line).map_err(|e|e.to_string())?;
        if row["kind"]=="message"{let source=row["source"].as_str().ok_or("Missing comparison source")?.to_string();if fresh.insert(source,row).is_some(){return Err("Duplicate comparison source".into())}}
    }
    if fresh.len()!=rows.len()-1{return Err("Saved record does not cover the original backup".into())}
    let mut verified=0;for row in rows.iter().skip(1){history_backup::check_cancel(stage)?;
        let now=fresh.get(row["source"].as_str().ok_or("Missing saved source")?).ok_or("Saved source absent from backup")?;
        if matches!(row["decision"].as_str(),Some("inserted"|"match")){
            if now["decision"]!="match"||now["target"]!=row["target"]{return Err("A recorded message changed, disappeared or became ambiguous; nothing was resumed".into())}verified+=1;
        }
    }
    for row in rows.iter().skip(1).filter(|r|r["decision"]=="pending"){
        let now=&fresh[row["source"].as_str().ok_or("Missing pending source")?];
        if now["decision"]!="new"{return Err("A pending message is present or ambiguous; review its outcome before continuing".into())}
    }
    Ok(json!({"id":id,"map_sha":expected,"verified_targets":verified,"pending_sources":pending(&rows)?,"provider_writes":false}))
}

#[cfg(test)]mod tests{
    use super::*;use std::fs;
    fn fixture()->(tempfile::TempDir,String,std::path::PathBuf){let d=tempfile::tempdir().unwrap();let id="1".repeat(32);let dir=d.path().join("history-restore-maps");fs::create_dir(&dir).unwrap();let stage=d.path().join("stage");fs::create_dir(&stage).unwrap();
        let header=json!({"format":"onklav-restore-outcomes","version":1,"archive_sha":"a".repeat(64),"plan_sha":"b".repeat(64),"metadata_applied":false,"phone_revalidated":false});
        let row=json!({"kind":"message","source":"sms:1","target":"sms:91","decision":"match"});fs::write(dir.join(format!("{id}.jsonl")),format!("{header}\n{row}\n")).unwrap();fs::write(stage.join("comparison.jsonl"),format!("{row}\n")).unwrap();(d,id,stage)
    }
    #[test]fn original_record_digest_and_exact_targets_are_reviewed_without_metadata_effects(){let(d,id,s)=fixture();let before=messaging::dispatch(d.path(),&json!({"action":"backup_export"})).unwrap();let i=info(d.path(),&id).unwrap();assert_eq!(compare(d.path(),&s,&id,i["map_sha"].as_str().unwrap(),&json!({"sha256":"a".repeat(64)})).unwrap()["verified_targets"],1);assert_eq!(before,messaging::dispatch(d.path(),&json!({"action":"backup_export"})).unwrap());assert!(!d.path().join("messenger.json").exists());}
    #[test]fn other_archives_missing_sources_and_changed_targets_cannot_resume(){let(d,id,s)=fixture();let sha=digest(d.path(),&id).unwrap();assert!(compare(d.path(),&s,&id,&sha,&json!({"sha256":"f".repeat(64)})).is_err());for rows in ["".to_string(),format!("{}\n",json!({"kind":"message","source":"sms:1","target":"sms:92","decision":"match"})),format!("{}\n",json!({"kind":"message","source":"sms:2","target":"sms:91","decision":"match"}))]{fs::write(s.join("comparison.jsonl"),rows).unwrap();assert!(compare(d.path(),&s,&id,&sha,&json!({"sha256":"a".repeat(64)})).is_err());}}
    #[test]fn changed_record_pending_messages_and_unknown_results_remain_paused(){let(d,id,_)=fixture();let path=d.path().join("history-restore-maps").join(format!("{id}.jsonl"));let sha=digest(d.path(),&id).unwrap();let original=fs::read_to_string(&path).unwrap();fs::write(&path,original.replace("\"decision\":\"match\"","\"decision\":\"pending\"").replace("\"target\":\"sms:91\"","\"target\":null")).unwrap();assert!(bound(d.path(),&id,&sha).is_err());assert_eq!(info(d.path(),&id).unwrap()["pending_sources"],json!(["sms:1"]));fs::write(&path,&original).unwrap();let t=history_journal::dispatch(d.path(),&json!({"action":"begin","kind":"sms","source":"sms:2","archive_sha":"a".repeat(64),"content_sha":"c".repeat(64)})).unwrap();for action in ["insert_intent","recover"]{history_journal::dispatch(d.path(),&json!({"action":action,"id":t["id"]})).unwrap();}assert!(info(d.path(),&id).is_err());assert_eq!(fs::read_to_string(path).unwrap(),original);}
    #[test]fn inserted_targets_require_their_committed_journal_proof_even_after_compaction(){let(d,id,s)=fixture();let path=d.path().join("history-restore-maps").join(format!("{id}.jsonl"));let mut lines=history_recovery::saved_map(d.path(),&id).unwrap();lines[1]["decision"]=json!("inserted");lines[1]["transaction"]=json!("2".repeat(32));let save=|rows:&Vec<Value>|fs::write(&path,rows.iter().map(|v|format!("{v}\n")).collect::<String>()).unwrap();save(&lines);assert!(info(d.path(),&id).is_err());
        let t=history_journal::dispatch(d.path(),&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"c".repeat(64)})).unwrap();history_journal::dispatch(d.path(),&json!({"action":"insert_intent","id":t["id"]})).unwrap();history_journal::dispatch(d.path(),&json!({"action":"row","id":t["id"],"target":"sms:91","receipt":"d".repeat(64)})).unwrap();history_journal::dispatch(d.path(),&json!({"action":"commit","id":t["id"]})).unwrap();history_journal::dispatch(d.path(),&json!({"action":"compact"})).unwrap();lines[1]["transaction"]=t["id"].clone();save(&lines);let i=info(d.path(),&id).unwrap();assert_eq!(compare(d.path(),&s,&id,i["map_sha"].as_str().unwrap(),&json!({"sha256":"a".repeat(64)})).unwrap()["verified_targets"],1);
    }
    fn pending_fixture()->(tempfile::TempDir,String,std::path::PathBuf){let(d,id,s)=fixture();let mut rows=history_recovery::saved_map(d.path(),&id).unwrap();rows[1]["decision"]=json!("pending");rows[1]["target"]=Value::Null;fs::write(d.path().join("history-restore-maps").join(format!("{id}.jsonl")),rows.iter().map(|r|format!("{r}\n")).collect::<String>()).unwrap();(d,id,s)}
    fn receipt(root:&Path,source:&str,archive:&str)->Value{
        let t=history_journal::dispatch(root,&json!({"action":"begin","kind":"sms","source":source,"archive_sha":archive,"content_sha":"c".repeat(64)})).unwrap();
        for action in ["insert_intent","row","commit"]{history_journal::dispatch(root,&json!({"action":action,"id":t["id"],"target":"sms:91","receipt":"d".repeat(64)})).unwrap();}t
    }
    #[test]fn only_reviewed_missing_sources_can_continue(){let(d,id,s)=pending_fixture();let i=info(d.path(),&id).unwrap();let sha=i["map_sha"].as_str().unwrap();
        for decision in ["match","review","skip"]{fs::write(s.join("comparison.jsonl"),format!("{}\n",json!({"kind":"message","source":"sms:1","decision":decision,"target":"sms:91"}))).unwrap();assert!(compare(d.path(),&s,&id,sha,&json!({"sha256":"a".repeat(64)})).is_err());}
        fs::write(s.join("comparison.jsonl"),format!("{}\n",json!({"kind":"message","source":"sms:1","decision":"new","target":null}))).unwrap();
        assert_eq!(compare(d.path(),&s,&id,sha,&json!({"sha256":"a".repeat(64)})).unwrap()["pending_sources"],json!(["sms:1"]));
    }
    #[test]fn crash_before_map_acknowledgement_never_replays_committed_source(){let(d,id,_)=pending_fixture();let old=digest(d.path(),&id).unwrap();let t=receipt(d.path(),"sms:1",&"a".repeat(64));
        history_journal::dispatch(d.path(),&json!({"action":"compact"})).unwrap();let i=info(d.path(),&id).unwrap();assert_eq!(i["map_sha"],old);assert_eq!(i["pending_sources"],json!([]));
        let result=checkpoint(d.path(),&id,&old).unwrap();assert_ne!(result["map_sha"],old);let rows=history_recovery::saved_map(d.path(),&id).unwrap();assert_eq!(rows[1]["transaction"],t["id"]);assert_eq!(rows[1]["target"],"sms:91");assert_eq!(rows[0]["plan_sha"],"b".repeat(64));
        assert!(checkpoint(d.path(),&id,&old).is_err());assert_eq!(checkpoint(d.path(),&id,result["map_sha"].as_str().unwrap()).unwrap(),result);
    }
    #[test]fn checkpoint_preserves_unknown_outcomes_and_does_not_promote_foreign_archive(){let(d,id,_)=pending_fixture();let old=digest(d.path(),&id).unwrap();receipt(d.path(),"sms:1",&"f".repeat(64));assert_eq!(info(d.path(),&id).unwrap()["pending_sources"],json!(["sms:1"]));
        let t=history_journal::dispatch(d.path(),&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"c".repeat(64)})).unwrap();history_journal::dispatch(d.path(),&json!({"action":"insert_intent","id":t["id"]})).unwrap();history_journal::dispatch(d.path(),&json!({"action":"recover","id":t["id"]})).unwrap();
        assert!(info(d.path(),&id).is_err());assert_eq!(checkpoint(d.path(),&id,&old).unwrap()["pending"],1);assert_eq!(digest(d.path(),&id).unwrap(),old);assert_eq!(history_journal::dispatch(d.path(),&json!({"action":"status"})).unwrap()["pending"],1);
    }
    #[test]fn changed_map_cannot_be_overwritten_by_checkpoint(){let(d,id,_)=pending_fixture();let old=digest(d.path(),&id).unwrap();receipt(d.path(),"sms:1",&"a".repeat(64));let p=d.path().join("history-restore-maps").join(format!("{id}.jsonl"));let bytes=fs::read(&p).unwrap();let changed=String::from_utf8(bytes).unwrap().replace(&"b".repeat(64),&"e".repeat(64));fs::write(&p,&changed).unwrap();assert!(checkpoint(d.path(),&id,&old).is_err());assert_eq!(fs::read_to_string(p).unwrap(),changed);}
}
