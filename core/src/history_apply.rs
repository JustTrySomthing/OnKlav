//! Host-only archive preparation/outcome handoff. Never grants provider access.
use crate::{history_backup,history_journal,Result};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::{BTreeMap,HashMap},fs::{self,File},io::{BufRead,BufReader,Read,Write},path::Path};
fn rows(path:&Path)->Result<Vec<Value>> {
    history_backup::regular(path,128*1024*1024)?;
    let mut reader=BufReader::new(File::open(path).map_err(|e|e.to_string())?);let mut out=Vec::new();
    loop {let mut line=Vec::new();let n=(&mut reader).take(2_097_153).read_until(b'\n',&mut line).map_err(|e|e.to_string())?;if n==0{break}if n>2_097_152{return Err("Prepared record exceeds limits".into())}out.push(serde_json::from_slice(&line).map_err(|e|e.to_string())?);if out.len()>500_000{return Err("Prepared record count exceeds limits".into())}}
    Ok(out)
}
fn sha(path:&Path)->Result<String>{history_backup::regular(path,128*1024*1024)?;let mut f=File::open(path).map_err(|e|e.to_string())?;let mut hash=Sha256::new();let mut b=[0;65536];loop{let n=f.read(&mut b).map_err(|e|e.to_string())?;if n==0{break}hash.update(&b[..n]);}Ok(format!("{:x}",hash.finalize()))}
pub(super) fn prepare(stage:&Path,archive:&str)->Result<usize> {
    let plan=rows(&stage.join("comparison.jsonl"))?;let mut inputs:BTreeMap<String,Value>=BTreeMap::new();
    for row in &plan {if row["kind"]=="message"&&row["decision"]=="new"{let key=row["source"].as_str().ok_or("Missing planned source")?;inputs.insert(key.into(),Value::Null);}}
    // Bound retained journal capacity before preparing payloads or touching providers.
    if inputs.len()>1000{return Err("Restore batch exceeds 1000-message journal capacity".into())}
    for row in rows(&stage.join("checked/records.jsonl"))? {
        history_backup::check_cancel(stage)?;
        let kind=row["kind"].as_str().ok_or("Missing kind")?;let id=row["id"].as_str().ok_or("Missing identity")?;
        let key=if matches!(kind,"sms"|"mms"){format!("{kind}:{id}")}else{format!("mms:{}",row["parent"].as_str().ok_or("Missing parent")?)};
        let Some(input)=inputs.get_mut(&key)else{continue};
        if matches!(kind,"sms"|"mms") {
            if !input.is_null(){return Err("Repeated prepared parent".into())}
            // Keep only identity-independent provider fields. Kotlin revalidates all input.
            let allowed=if kind=="sms"{"address body date date_sent type subject read seen status locked service_center protocol reply_path_present error_code"}else{"date date_sent msg_box m_type sub sub_cs m_id v ct_t m_cls pri read seen locked text_only"};
            let fields:serde_json::Map<String,Value>=row["fields"].as_object().ok_or("Missing fields")?.iter().filter(|(k,_)|allowed.split(' ').any(|v|v==k.as_str())).map(|(k,v)|(k.clone(),v.clone())).collect();
            *input=json!({"source":key,"archive_sha":archive,"kind":kind,"fields":fields,"addresses":[],"parts":[]});
        }else{
            if input.is_null(){return Err("Prepared child precedes parent".into())}
            let list=if kind=="address"{"addresses"}else{"parts"};let values=input[list].as_array_mut().ok_or("Invalid prepared children")?;
            if values.len()>=256{return Err("Restore child limit exceeded".into())}
            if kind=="address"{values.push(row["fields"].clone())}else{let mut fields=row["fields"].clone();fields.as_object_mut().ok_or("Missing part fields")?.remove("_id");values.push(json!({"fields":fields,"file":row.get("file")}));}
        }
    }
    let mut f=File::options().create_new(true).write(true).open(stage.join("prepared.jsonl")).map_err(|e|e.to_string())?;
    for input in inputs.values(){history_backup::check_cancel(stage)?;if input.is_null(){return Err("Missing planned message".into())}let line=format!("{input}\n");if line.len()>2_097_152{return Err("Prepared message exceeds limits".into())}f.write_all(line.as_bytes()).map_err(|e|e.to_string())?;}f.sync_all().map_err(|e|e.to_string())?;
    fs::write(stage.join("prepared.json"),json!({"archive_sha":archive,"plan_sha":sha(&stage.join("comparison.jsonl"))?,"payload_sha":sha(&stage.join("prepared.jsonl"))?}).to_string()).map_err(|e|e.to_string())?;
    Ok(inputs.len())
}
pub(super) fn finish(root:&Path,stage:&Path,job:&str)->Result<Value> {
    if crate::valid_id(job)&&crate::history_completed::find(root,job)?.is_some(){return Err("Restore already completed and archived".into())}
    history_backup::regular(&stage.join("prepared.json"),4096)?;
    let marker:Value=serde_json::from_reader(File::open(stage.join("prepared.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    if marker["plan_sha"]!=sha(&stage.join("comparison.jsonl"))?||marker["payload_sha"]!=sha(&stage.join("prepared.jsonl"))?{return Err("Prepared restore changed".into())}
    let archive=marker["archive_sha"].as_str().ok_or("Missing archive identity")?;
    let receipts=history_journal::dispatch(root,&json!({"action":"receipts","archive_sha":archive}))?;
    let committed:HashMap<&str,&Value>=receipts["items"].as_array().ok_or("Missing outcomes")?.iter().map(|r|Ok((r["source"].as_str().ok_or("Missing outcome source")?,r))).collect::<Result<_>>()?;
    let directory=root.join("history-restore-maps");fs::create_dir_all(&directory).map_err(|e|e.to_string())?;history_backup::dir(&directory)?;
    if fs::read_dir(&directory).map_err(|e|e.to_string())?.filter_map(|e|e.ok()).filter(|e|e.path().extension().is_some_and(|s|s=="jsonl")).count()>=100{return Err("Retained restore map limit reached".into())}
    let destination=directory.join(format!("{job}.jsonl"));if destination.exists(){return Err("Restore map already retained".into())}
    let temp=directory.join(format!("{}.tmp",uuid::Uuid::new_v4().simple()));
    let result=(||{let mut f=File::options().create_new(true).write(true).open(&temp).map_err(|e|e.to_string())?;
        writeln!(f,"{}",json!({"format":"onklav-restore-outcomes","version":1,"archive_sha":archive,"plan_sha":marker["plan_sha"],"metadata_applied":false,"phone_revalidated":false})).map_err(|e|e.to_string())?;
        let(mut inserted,mut pending)=(0usize,0usize);
        for mut row in rows(&stage.join("comparison.jsonl"))? {
            if row["kind"]!="message"{continue}let source=row["source"].as_str().ok_or("Missing source")?;
            if row["decision"]=="new" {if let Some(receipt)=committed.get(source){row["decision"]=json!("inserted");row["target"]=receipt["target"].clone();row["transaction"]=receipt["id"].clone();inserted+=1;}else{row["decision"]=json!("pending");pending+=1;}}
            writeln!(f,"{row}").map_err(|e|e.to_string())?;
        }f.sync_all().map_err(|e|e.to_string())?;fs::rename(&temp,&destination).map_err(|e|e.to_string())?;File::open(&directory).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;
        Ok(json!({"map":job,"inserted":inserted,"pending":pending,"metadata_applied":false,"phone_revalidated":false}))})();if result.is_err(){let _=fs::remove_file(temp);}result
}

fn checked_marker(stage:&Path)->Result<Value>{
    history_backup::regular(&stage.join("prepared.json"),4096)?;
    let marker:Value=serde_json::from_reader(File::open(stage.join("prepared.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    if marker["plan_sha"]!=sha(&stage.join("comparison.jsonl"))?||marker["payload_sha"]!=sha(&stage.join("prepared.jsonl"))?{return Err("Prepared restore changed".into())}Ok(marker)
}
fn write_merged_map(directory:&Path,path:&Path,mut records:Vec<Value>,receipt:&crate::messaging::HistoryMerge)->Result<Value>{
    records[0]["metadata_applied"]=json!(true);records[0]["phone_revalidated"]=json!(true);
    records[0]["post_plan_sha"]=json!(receipt.post_plan_sha);records[0]["flags_imported"]=json!(receipt.flags_imported);records[0]["threads_imported"]=json!(receipt.threads_imported);
    let temp=directory.join(format!("{}.tmp",uuid::Uuid::new_v4().simple()));
    let result=(||{let mut f=File::options().create_new(true).write(true).open(&temp).map_err(|e|e.to_string())?;for row in records {writeln!(f,"{row}").map_err(|e|e.to_string())?;}f.sync_all().map_err(|e|e.to_string())?;fs::rename(&temp,path).map_err(|e|e.to_string())?;File::open(directory).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(json!({"metadata_applied":true,"phone_revalidated":true,"flags_imported":receipt.flags_imported,"threads_imported":receipt.threads_imported}))})();if result.is_err(){let _=fs::remove_file(temp);}result
}
pub(super) fn repair_ack(root:&Path,id:&str)->Result<Value>{
    if let Some(row)=crate::history_completed::find(root,id)?{crate::history_completed::check_copy(root,&row)?;if crate::messaging::history_receipt(root,id)?.as_ref()!=Some(&row.receipt){return Err("Archived metadata receipt mismatch".into())}return Ok(crate::history_completed::summary(&row))}
    let records=crate::history_recovery::saved_map(root,id)?;
    let receipt=crate::messaging::history_receipt(root,id)?.ok_or("No committed metadata receipt; review this restore before continuing")?;
    if json!(receipt.archive_sha)!=records[0]["archive_sha"]||json!(receipt.plan_sha)!=records[0]["plan_sha"]||records.iter().skip(1).any(|r|r["decision"]=="pending"){return Err("Saved restore and metadata receipt do not agree".into())}
    let dir=root.join("history-restore-maps");write_merged_map(&dir,&dir.join(format!("{id}.jsonl")),records,&receipt)
}
pub(super) fn merge(root:&Path,stage:&Path,job:&str)->Result<Value>{merge_inner(root,stage,job,None)}
pub(super) fn resume_merge(root:&Path,stage:&Path,record:&str,expected:&str)->Result<Value>{merge_inner(root,stage,record,Some(expected))}
fn merge_inner(root:&Path,stage:&Path,job:&str,resume:Option<&str>)->Result<Value>{
    history_backup::check_cancel(stage)?;let mut marker=checked_marker(stage)?;
    let saved=if let Some(sha)=resume{Some(crate::history_resume::bound(root,job,sha)?)}else{None};
    if let Some(rows)=&saved{
        if rows[0]["archive_sha"]!=marker["archive_sha"]{return Err("Choose the original backup for this record".into())}
        marker["plan_sha"]=rows[0]["plan_sha"].clone();
    }
    if let Some(row)=crate::history_completed::find(root,job)?{
        crate::history_completed::check_copy(root,&row)?;
        if crate::messaging::history_receipt(root,job)?.as_ref()!=Some(&row.receipt){return Err("Archived metadata receipt mismatch".into())}
        if row.receipt.archive_sha!=marker["archive_sha"]||row.receipt.plan_sha!=marker["plan_sha"]{return Err("Archived restore identity mismatch".into())}
        return Ok(crate::history_completed::summary(&row))
    }
    let directory=root.join("history-restore-maps");history_backup::dir(&directory)?;let map=directory.join(format!("{job}.jsonl"));let records=if let Some(saved)=saved{saved}else{rows(&map)?};
    let header=records.first().ok_or("Missing restore outcome header")?;
    if header["format"]!="onklav-restore-outcomes"||header["version"]!=1||header["archive_sha"]!=marker["archive_sha"]||header["plan_sha"]!=marker["plan_sha"]{return Err("Restore outcome identity mismatch".into())}
    // The metadata and its completion receipt share one atomic state commit.
    // If map acknowledgement failed, repair it from that durable receipt only.
    if let Some(receipt)=crate::messaging::history_receipt(root,job)? {
        if json!(receipt.archive_sha)!=marker["archive_sha"]||json!(receipt.plan_sha)!=marker["plan_sha"]{return Err("Restore metadata identity mismatch".into())}
        return write_merged_map(&directory,&map,records,&receipt)
    }
    if header["metadata_applied"]!=false||records.iter().skip(1).any(|r|r["decision"]=="pending"){return Err("Incomplete or inconsistent restore outcomes".into())}
    if history_journal::dispatch(root,&json!({"action":"status"}))?["pending"]!=0{return Err("Resolve interrupted inserts before metadata merge".into())}
    let post=stage.join("post");history_backup::validate_at(&post,stage)?;crate::history_import::strict_records(&post,stage)?;
    let source=stage.join("checked");history_backup::validate_at(&source,stage)?;crate::history_import::strict_records(&source,stage)?;
    let output=stage.join("post-comparison.jsonl");if output.exists(){history_backup::regular(&output,128*1024*1024)?;fs::remove_file(&output).map_err(|e|e.to_string())?;}
    let comparison=crate::history_plan::compare_to(&source,&post,stage,&output)?;let fresh=rows(&output)?;
    let matches:HashMap<&str,&Value>=fresh.iter().filter(|r|r["kind"]=="message").map(|r|Ok((r["source"].as_str().ok_or("Missing post source")?,r))).collect::<Result<_>>()?;
    if resume.is_some()&&(matches.len()!=records.len()-1||records.iter().skip(1).any(|r|r["source"].as_str().is_none_or(|s|!matches.contains_key(s)))){return Err("Saved record does not cover the original backup".into())}
    let mut verified=std::collections::HashSet::new();let mut deferred_threads=std::collections::HashSet::new();
    for row in records.iter().skip(1){history_backup::check_cancel(stage)?;let key=row["source"].as_str().ok_or("Missing outcome source")?;
        if row["decision"]=="inserted"||row["decision"]=="match" {
            let now=matches.get(key).ok_or("Restored message is missing from the post-write snapshot")?;
            if now["decision"]!="match"||now["target"]!=row["target"]{return Err("Restored message changed or became ambiguous; metadata was not applied".into())}
            verified.insert(key.to_string());
        }else if let Some(thread)=row["source_thread"].as_str(){deferred_threads.insert(thread.to_string());}
    }
    let mut flags=BTreeMap::new();let mut threads=BTreeMap::new();
    for row in &fresh {history_backup::check_cancel(stage)?;
        if row["kind"]=="flag_binding" {let key=row["source"].as_str().ok_or("Missing flag source")?;if verified.contains(key){let target=matches[key];if let Some(thread)=target["target_thread"].as_str().and_then(|v|v.parse::<u64>().ok()).filter(|n|*n>0&&*n<=i64::MAX as u64){flags.insert(key.to_string(),(row["target"].as_str().ok_or("Missing flag target")?.to_string(),thread));}}}
        if row["kind"]=="thread_binding" {let key=row["source"].as_str().ok_or("Missing thread source")?;if !deferred_threads.contains(key){threads.insert(key.to_string(),row["target"].as_str().ok_or("Missing thread target")?.to_string());}}
    }
    history_backup::regular(&source.join("metadata.json"),8_000_000)?;let metadata:Value=serde_json::from_reader(File::open(source.join("metadata.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    if let Some(expected)=resume{if crate::history_resume::digest(root,job)?!=expected{return Err("Saved restore record changed during verification".into())}}
    let receipt=crate::messaging::HistoryMerge{archive_sha:marker["archive_sha"].as_str().ok_or("Missing archive identity")?.to_string(),plan_sha:marker["plan_sha"].as_str().ok_or("Missing original plan identity")?.to_string(),post_plan_sha:comparison["plan_sha256"].as_str().ok_or("Missing post plan identity")?.to_string(),flags_imported:0,threads_imported:0};
    let receipt=crate::messaging::merge_history(root,job,receipt,&metadata["metadata"],&flags,&threads,stage)?;
    write_merged_map(&directory,&map,records,&receipt)
}

#[cfg(test)]mod tests {
    use super::*;
    fn fixture()->(tempfile::TempDir,std::path::PathBuf){let t=tempfile::tempdir().unwrap();let s=t.path().join("job");fs::create_dir_all(s.join("checked/media")).unwrap();
        fs::write(s.join("comparison.jsonl"),concat!("{\"kind\":\"message\",\"source\":\"sms:1\",\"decision\":\"new\",\"target\":null}\n","{\"kind\":\"message\",\"source\":\"sms:2\",\"decision\":\"match\",\"target\":\"sms:88\"}\n")).unwrap();
        fs::write(s.join("checked/records.jsonl"),"{\"kind\":\"sms\",\"id\":\"1\",\"fields\":{\"_id\":\"1\",\"thread_id\":\"9\",\"sub_id\":\"4\",\"creator\":\"old.app\",\"body\":\"hello\",\"address\":\"555\",\"date\":\"1\",\"type\":\"1\"}}\n").unwrap();(t,s)}
    #[test]fn prepared_payload_excludes_old_provider_authorities(){let(_t,s)=fixture();assert_eq!(prepare(&s,&"a".repeat(64)).unwrap(),1);let v=rows(&s.join("prepared.jsonl")).unwrap();assert_eq!(v[0]["fields"]["body"],"hello");for key in ["_id","thread_id","sub_id","creator"]{assert!(v[0]["fields"].get(key).is_none())}}
    #[test]fn outcome_map_uses_only_committed_receipts_and_preserves_existing_targets(){let(t,s)=fixture();prepare(&s,&"a".repeat(64)).unwrap();let row=history_journal::dispatch(t.path(),&json!({"action":"begin","kind":"sms","source":"sms:1","archive_sha":"a".repeat(64),"content_sha":"b".repeat(64)})).unwrap();let id=&row["id"];
        history_journal::dispatch(t.path(),&json!({"action":"insert_intent","id":id})).unwrap();history_journal::dispatch(t.path(),&json!({"action":"row","id":id,"target":"sms:91","receipt":"c".repeat(64)})).unwrap();
        let partial=finish(t.path(),&s,"partial").unwrap();assert_eq!(partial["pending"],1);assert_eq!(partial["inserted"],0);
        history_journal::dispatch(t.path(),&json!({"action":"commit","id":id})).unwrap();let result=finish(t.path(),&s,"complete").unwrap();assert_eq!(result["inserted"],1);assert_eq!(result["metadata_applied"],false);
        let rows=rows(&t.path().join("history-restore-maps/complete.jsonl")).unwrap();assert_eq!(rows[1]["target"],"sms:91");assert_eq!(rows[2]["target"],"sms:88");assert!(finish(t.path(),&s,"complete").is_err());}
    #[test]fn changed_plan_or_payload_cannot_publish_a_durable_map(){let(t,s)=fixture();prepare(&s,&"a".repeat(64)).unwrap();fs::write(s.join("prepared.jsonl"),"{}\n").unwrap();assert!(finish(t.path(),&s,"changed").is_err());assert!(!t.path().join("history-restore-maps/changed.jsonl").exists());}
    #[test]fn source_candidates_without_parent_or_over_capacity_fail_before_writes(){let(_t,s)=fixture();fs::write(s.join("checked/records.jsonl"),"").unwrap();assert!(prepare(&s,&"a".repeat(64)).is_err());let rows=(1..=1001).map(|i|format!("{}\n",json!({"kind":"message","source":format!("sms:{i}"),"decision":"new"}))).collect::<String>();fs::write(s.join("comparison.jsonl"),rows).unwrap();assert!(prepare(&s,&"a".repeat(64)).is_err());}
}
