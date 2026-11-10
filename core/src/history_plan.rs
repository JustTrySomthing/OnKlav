//! Read-only content matching for a future provider merge. IDs are mapping results,
//! never evidence of identity. There is no provider writer/apply operation here.
use crate::{history_backup,Result};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::{BTreeMap,HashMap,HashSet},fs::File,io::{BufRead,BufReader,Read,Write},path::Path};
#[derive(Clone)]
struct Message {key:String,thread:Option<String>,date:u64,hash:Option<String>,reason:&'static str}
struct Mms {message:Message,header:String,addresses:Vec<String>,parts:Vec<String>,complete:bool}
fn digest(v:&Value)->String {format!("{:x}",Sha256::digest(v.to_string().as_bytes()))}
fn numeric(v:&Value)->Option<String> {v.as_str().and_then(|s|s.parse::<u64>().ok().filter(|n|*n>0&&*n<=i64::MAX as u64&&n.to_string()==s).map(|_|s.to_string()))}
fn address(v:&Value)->Option<String> {
    let s=v.as_str()?;if s.is_empty(){return None}
    // Ignore only ordinary phone presentation separators. Keep +, country codes,
    // leading zeroes and non-phone/alphanumeric senders distinct.
    if s.bytes().all(|c|c.is_ascii_digit()||b"+ ()-.".contains(&c)) {
        let compact:String=s.chars().filter(|c|!" ()-.".contains(*c)).collect();
        if compact.is_empty(){None}else{Some(compact)}
    }else{Some(s.to_string())}
}
fn file_hash(stage:&Path,name:&str,cancel:&Path)->Result<String> {
    history_backup::regular(&stage.join(name),64*1024*1024)?;
    let mut file=File::open(stage.join(name)).map_err(|e|e.to_string())?;let mut hash=Sha256::new();let mut buffer=[0;65536];
    loop {history_backup::check_cancel(cancel)?;let n=file.read(&mut buffer).map_err(|e|e.to_string())?;if n==0{break}hash.update(&buffer[..n]);}
    Ok(format!("{:x}",hash.finalize()))
}
fn messages(stage:&Path,cancel:&Path)->Result<Vec<Message>> {
    // Both staged inputs are already checked by the shared importer/export validator.
    let mut reader=BufReader::new(File::open(stage.join("records.jsonl")).map_err(|e|e.to_string())?);
    let mut result=Vec::new();let mut mms:HashMap<String,Mms>=HashMap::new();
    loop {
        history_backup::check_cancel(cancel)?;let mut line=Vec::new();let n=(&mut reader).take(1_048_577).read_until(b'\n',&mut line).map_err(|e|e.to_string())?;
        if n==0{break}if n>1_048_576{return Err("History record exceeds limits".into())}
        let row:Value=serde_json::from_slice(&line).map_err(|e|e.to_string())?;let kind=row["kind"].as_str().ok_or("Missing record kind")?;
        let id=row["id"].as_str().ok_or("Missing record ID")?;let f=&row["fields"];
        match kind {
            "sms"|"mms"=>{
                let date=f["date"].as_str().and_then(|v|v.parse::<u64>().ok()).ok_or("Invalid message date")?;
                let date_ms=if kind=="mms"{date.checked_mul(1000).ok_or("Invalid MMS date")?}else{date};
                let mut message=Message{key:format!("{kind}:{id}"),thread:numeric(&f["thread_id"]),date:date_ms,hash:None,reason:"incomplete"};
                let box_value=f[if kind=="sms"{"type"}else{"msg_box"}].as_str().unwrap_or("");
                if !matches!(box_value,"1"|"2"){message.reason="unsent";result.push(message);continue}
                if kind=="sms" {
                    if let (Some(sender),Some(body))=(address(&f["address"]),f["body"].as_str()) {
                        message.hash=Some(digest(&json!(["sms-content-v1",box_value,date,sender,body,f["subject"]])));message.reason="";
                    }result.push(message);
                } else {
                    let mtype=f["m_type"].as_str().unwrap_or("");
                    if mtype=="130" {message.reason="undownloaded";result.push(message);continue}
                    let complete=matches!(mtype,"128"|"132");
                    let header=digest(&json!(["mms-content-v1",box_value,date,mtype,f["sub"],f["sub_cs"],f["m_id"]]));
                    mms.insert(id.to_string(),Mms{message,header,addresses:Vec::new(),parts:Vec::new(),complete});
                }
            },
            "address"|"part"=>{
                let parent=row["parent"].as_str().ok_or("Missing MMS parent")?;
                let Some(parent)=mms.get_mut(parent)else{continue};
                if kind=="address" {
                    if let Some(value)=address(&f["address"]){parent.addresses.push(digest(&json!([f["type"],value,f["charset"]])))}else{parent.complete=false}
                }else{
                    let seq=f["seq"].as_str().and_then(|s|s.parse::<i64>().ok());if seq.is_none(){parent.complete=false}
                    let hash=match row.get("file").and_then(Value::as_str){Some(name)=>Some(file_hash(stage,name,cancel)?),None=>None};
                    parent.parts.push(digest(&json!([seq,f["ct"],f["chset"],f["name"],f["cd"],f["fn"],f["cid"],f["cl"],f["text"],hash])));
                }
            },_=>return Err("Unknown history record".into())
        }
    }
    for (_,mut item) in mms {
        if item.complete&&!item.addresses.is_empty()&&!item.parts.is_empty() {
            item.addresses.sort_unstable();item.parts.sort_unstable();
            item.message.hash=Some(digest(&json!([item.header,item.addresses,item.parts])));item.message.reason="";
        }result.push(item.message);
    }
    result.sort_unstable_by(|a,b|a.key.cmp(&b.key));Ok(result)
}
#[derive(Default)]struct ThreadMap {targets:HashSet<String>,deferred:bool}
pub(super) fn compare(source:&Path,current:&Path,cancel:&Path)->Result<Value> {compare_to(source,current,cancel,&cancel.join("comparison.jsonl"))}
pub(super) fn compare_to(source:&Path,current:&Path,cancel:&Path,path:&Path)->Result<Value> {
    let old=messages(source,cancel)?;let existing=messages(current,cancel)?;
    let mut source_counts:HashMap<&str,usize>=HashMap::new();let mut targets:HashMap<&str,Vec<&Message>>=HashMap::new();
    for m in &old{if let Some(h)=&m.hash{*source_counts.entry(h).or_default()+=1}}
    for m in &existing{if let Some(h)=&m.hash{targets.entry(h).or_default().push(m)}}
    let mut mapped:HashMap<String,&Message>=HashMap::new();let mut threads:BTreeMap<String,ThreadMap>=BTreeMap::new();
    let mut out=File::options().create_new(true).write(true).open(&path).map_err(|e|e.to_string())?;
    let(mut matched,mut insert,mut ambiguous,mut skipped)=(0usize,0usize,0usize,0usize);
    let mut plan_hash=Sha256::new();
    let mut emit=|row:Value|->Result<()> {
        history_backup::check_cancel(cancel)?;let line=format!("{row}\n");out.write_all(line.as_bytes()).map_err(|e|e.to_string())?;plan_hash.update(line.as_bytes());Ok(())
    };
    for m in &old {
        history_backup::check_cancel(cancel)?;let mut target=None;
        let decision=match &m.hash {
            None=>{if matches!(m.reason,"unsent"|"undownloaded"){skipped+=1;"skip"}else{ambiguous+=1;"review"}},
            Some(h)=>{
                let possible=targets.get(h.as_str());let target_count=possible.map_or(0,Vec::len);
                if source_counts[h.as_str()]>1||target_count>1{ambiguous+=1;"review"}
                else if target_count==1{matched+=1;target=Some(possible.unwrap()[0]);mapped.insert(m.key.clone(),target.unwrap());"match"}
                else{insert+=1;"new"}
            }
        };
        if let Some(id)=&m.thread {
            let binding=threads.entry(id.clone()).or_default();
            if let Some(t)=target.and_then(|t|t.thread.as_ref()){binding.targets.insert(t.clone());}else{binding.deferred=true}
        }
        emit(json!({"kind":"message","source":m.key,"decision":decision,"reason":m.reason,"target":target.map(|t|&t.key),"source_thread":m.thread,"target_thread":target.and_then(|t|t.thread.as_ref())}))?;
    }
    let meta:Value=serde_json::from_reader(File::open(source.join("metadata.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    let flags=meta["metadata"]["messages"].as_object().ok_or("Missing message flags")?;
    let mut flags_mapped=0;
    for (key,flag) in flags {
        // Zero/old dates cannot prove that metadata still belongs to this provider row.
        let source=old.binary_search_by(|m|m.key.cmp(key)).ok().map(|i|&old[i]);
        if let (Some(source),Some(target))=(source,mapped.get(key)) {
            if flag["date"].as_u64()==Some(source.date)&&source.date>0 {
                flags_mapped+=1;emit(json!({"kind":"flag_binding","source":key,"target":target.key}))?;
            }
        }
    }
    let folders=meta["metadata"]["threads"].as_object().ok_or("Missing folders")?;
    let mut candidate_threads=BTreeMap::new();let mut uses:HashMap<String,usize>=HashMap::new();
    for source in folders.keys() {
        if let Some(binding)=threads.get(source){if !binding.deferred&&binding.targets.len()==1 {
            let target=binding.targets.iter().next().unwrap().clone();*uses.entry(target.clone()).or_default()+=1;candidate_threads.insert(source.clone(),target);
        }}
    }
    let mut threads_mapped=0;
    for (source,target) in candidate_threads {
        if uses[&target]==1{threads_mapped+=1;emit(json!({"kind":"thread_binding","source":source,"target":target}))?;}
    }
    drop(emit);out.sync_all().map_err(|e|e.to_string())?;
    Ok(json!({"matched":matched,"new":insert,"review":ambiguous,"skipped":skipped,"phone_messages":existing.len(),"flags_mapped":flags_mapped,"flags_deferred":flags.len()-flags_mapped,
        "threads_mapped":threads_mapped,"threads_deferred":folders.len()-threads_mapped,"plan_sha256":format!("{:x}",plan_hash.finalize()),"provider_writes":false,"restore_available":false}))
}

#[cfg(test)]mod tests {
    use super::*;use std::fs;
    fn sms(id:&str,body:&str,thread:&str)->Value {json!({"kind":"sms","id":id,"fields":{"_id":id,"address":"+1 (555) 010-1234","body":body,"date":"1700000000123","type":"1","thread_id":thread}})}
    fn fixture()->(tempfile::TempDir,std::path::PathBuf,std::path::PathBuf) {
        let t=tempfile::tempdir().unwrap();let a=t.path().join("source");let b=t.path().join("current");fs::create_dir_all(a.join("media")).unwrap();fs::create_dir_all(b.join("media")).unwrap();
        let metadata=crate::messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap();
        for stage in [&a,&b]{fs::write(stage.join("metadata.json"),json!({"format":"onklav-app-backup","version":1,"origin":"12345678-1234-4234-8234-123456789abc","created":1,"metadata":metadata}).to_string()).unwrap();}
        (t,a,b)
    }
    fn rows(p:&Path,rows:&[Value]) {fs::write(p.join("records.jsonl"),rows.iter().map(|r|format!("{r}\n")).collect::<String>()).unwrap();}
    fn plan(p:&Path)->Vec<Value> {fs::read_to_string(p.join("comparison.jsonl")).unwrap().lines().map(|s|serde_json::from_str(s).unwrap()).collect()}
    #[test]fn provider_ids_and_read_flags_are_not_identity_and_mapping_is_content_based() {
        let(t,a,b)=fixture();rows(&a,&[sms("1","existing","5"),sms("2","new","5")]);
        let mut same=sms("90","existing","70");same["fields"]["address"]=json!("+15550101234");same["fields"]["read"]=json!("1");same["fields"]["sub_id"]=json!("99");
        rows(&b,&[same,sms("2","different content despite same ID","71")]);
        let r=compare(&a,&b,t.path()).unwrap();assert_eq!(r["matched"],1);assert_eq!(r["new"],1);assert_eq!(r["provider_writes"],false);
        let output=plan(t.path());assert_eq!(output[0]["target"],"sms:90");assert_eq!(output[0]["target_thread"],"70");assert_eq!(output[1]["decision"],"new");
        assert!(!fs::read_to_string(t.path().join("comparison.jsonl")).unwrap().contains("existing"));
    }
    #[test]fn repeated_source_or_target_content_is_never_collapsed_or_arbitrarily_bound() {
        let(t,a,b)=fixture();rows(&a,&[sms("1","same","5"),sms("2","same","5")]);rows(&b,&[sms("90","same","70")]);
        let r=compare(&a,&b,t.path()).unwrap();assert_eq!(r["review"],2);assert_eq!(r["matched"],0);assert_eq!(r["new"],0);
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();rows(&a,&[sms("1","same","5")]);rows(&b,&[sms("90","same","70"),sms("91","same","70")]);
        assert_eq!(compare(&a,&b,t.path()).unwrap()["review"],1);
    }
    fn mms(id:&str,part:&str,thread:&str)->Vec<Value> {vec![
        json!({"kind":"mms","id":id,"fields":{"_id":id,"date":"1700000000","msg_box":"1","thread_id":thread,"m_type":"132","m_id":"carrier-message","sub":"subject","sub_cs":"106"}}),
        json!({"kind":"address","id":"0","parent":id,"fields":{"address":"5550101234","type":"137","charset":"106"}}),
        json!({"kind":"part","id":part,"parent":id,"fields":{"_id":part,"ct":"image/png","seq":"1","name":"photo.png"},"file":format!("media/{part}.bin")})]}
    #[test]fn mms_uses_actual_media_bytes_and_recipients_not_provider_part_ids() {
        let(t,a,b)=fixture();rows(&a,&mms("1","11","5"));rows(&b,&mms("90","99","70"));fs::write(a.join("media/11.bin"),b"same bytes").unwrap();fs::write(b.join("media/99.bin"),b"same bytes").unwrap();
        assert_eq!(compare(&a,&b,t.path()).unwrap()["matched"],1);
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();fs::write(b.join("media/99.bin"),b"changed bytes").unwrap();assert_eq!(compare(&a,&b,t.path()).unwrap()["new"],1);
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();fs::write(b.join("media/99.bin"),b"same bytes").unwrap();let mut changed=mms("90","99","70");changed[1]["fields"]["address"]=json!("5559999999");rows(&b,&changed);assert_eq!(compare(&a,&b,t.path()).unwrap()["new"],1);
    }
    #[test]fn metadata_binds_only_dated_unique_messages_and_wholly_resolved_nonconflicting_threads() {
        let(t,a,b)=fixture();rows(&a,&[sms("1","existing","5")]);rows(&b,&[sms("90","existing","70")]);
        let mut metadata:Value=serde_json::from_slice(&fs::read(a.join("metadata.json")).unwrap()).unwrap();
        metadata["metadata"]["messages"]=json!({"sms:1":{"date":1700000000123u64},"sms:2":{"date":1}});metadata["metadata"]["threads"]=json!({"5":{},"6":{}});
        fs::write(a.join("metadata.json"),metadata.to_string()).unwrap();let r=compare(&a,&b,t.path()).unwrap();assert_eq!(r["flags_mapped"],1);assert_eq!(r["flags_deferred"],1);assert_eq!(r["threads_mapped"],1);assert_eq!(r["threads_deferred"],1);
        assert!(plan(t.path()).iter().any(|r|r["kind"]=="flag_binding"&&r["target"]=="sms:90"));
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();rows(&a,&[sms("1","existing","5"),sms("3","not yet present","5")]);assert_eq!(compare(&a,&b,t.path()).unwrap()["threads_mapped"],0);
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();rows(&a,&[sms("1","existing","5"),sms("3","other","6")]);rows(&b,&[sms("90","existing","70"),sms("91","other","70")]);assert_eq!(compare(&a,&b,t.path()).unwrap()["threads_mapped"],0);
    }
    #[test]fn unsent_pending_and_incomplete_content_are_not_new_candidates_and_order_is_deterministic() {
        let(t,a,b)=fixture();let mut unsent=sms("1","queued","5");unsent["fields"]["type"]=json!("6");let mut incomplete=sms("2","unknown sender","5");incomplete["fields"]["address"]=Value::Null;
        let pending=json!({"kind":"mms","id":"3","fields":{"_id":"3","date":"1700000000","msg_box":"1","m_type":"130"}});
        let records=vec![unsent,incomplete,pending,sms("4","ready","5")];rows(&a,&records);rows(&b,&[]);
        let first=compare(&a,&b,t.path()).unwrap();assert_eq!(first["skipped"],2);assert_eq!(first["review"],1);assert_eq!(first["new"],1);
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();rows(&a,&records.into_iter().rev().collect::<Vec<_>>());assert_eq!(first["plan_sha256"],compare(&a,&b,t.path()).unwrap()["plan_sha256"]);
        fs::remove_file(t.path().join("comparison.jsonl")).unwrap();fs::write(t.path().join("cancel"),b"").unwrap();assert_eq!(compare(&a,&b,t.path()).unwrap_err(),"Backup stopped");assert!(!t.path().join("comparison.jsonl").exists());
    }
}
