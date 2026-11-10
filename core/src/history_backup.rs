//! Host-only provider snapshot packaging. No provider writes, guest service or restore.
use crate::Result;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{collections::{BTreeMap, HashSet}, fs::{self, File}, io::{BufRead, BufReader, Read, Write}, path::{Path, PathBuf}};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};
const MAX_RECORDS: usize = 500_000;
const MAX_MESSAGES: usize = 200_000;
const MAX_TEXT: u64 = 128 * 1024 * 1024;
const MAX_MEDIA: u64 = 2 * 1024 * 1024 * 1024;
const MAX_PART: u64 = 64 * 1024 * 1024;
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Record { kind: String, id: String, parent: Option<String>, #[serde(deserialize_with="unique_fields")] fields: BTreeMap<String, Value>, file: Option<String> }
fn unique_fields<'de,D:serde::Deserializer<'de>>(d:D)->std::result::Result<BTreeMap<String,Value>,D::Error> {
    struct Fields;
    impl<'de> serde::de::Visitor<'de> for Fields {
        type Value=BTreeMap<String,Value>;
        fn expecting(&self,f:&mut std::fmt::Formatter)->std::fmt::Result {f.write_str("unique history fields")}
        fn visit_map<A:serde::de::MapAccess<'de>>(self,mut map:A)->std::result::Result<Self::Value,A::Error> {
            let mut fields=BTreeMap::new();
            while let Some((key,value))=map.next_entry::<String,Value>()? {
                if fields.len()>=64||fields.insert(key,value).is_some(){return Err(serde::de::Error::custom("Duplicate or excessive history fields"))}
            }Ok(fields)
        }
    }d.deserialize_map(Fields)
}
#[derive(Serialize, Deserialize, Clone)]
#[serde(deny_unknown_fields)]
pub(super) struct Entry { pub name: String, pub bytes: u64, pub sha256: String }
pub(super) fn regular(path: &Path, max: u64) -> Result<u64> {
    let m=fs::symlink_metadata(path).map_err(|e|e.to_string())?;
    if !m.is_file() || m.len()>max { return Err("Invalid or oversized backup file".into()); } Ok(m.len())
}
pub(super) fn dir(path: &Path) -> Result<()> {
    if !fs::symlink_metadata(path).map_err(|e|e.to_string())?.is_dir() { return Err("Invalid backup directory".into()); } Ok(())
}
pub(super) fn check_cancel(stage: &Path) -> Result<()> { if stage.join("cancel").exists() {Err("Backup stopped".into())}else{Ok(())} }
fn numeric(s: &str) -> bool { !s.is_empty() && s.len()<=20 && s.bytes().all(|b|b.is_ascii_digit()) }
fn stream(stage: &Path, mut input: impl Read, mut output: impl Write) -> Result<(u64,String)> {
    let mut bytes=0; let mut digest=Sha256::new(); let mut buf=[0u8;65536];
    loop { check_cancel(stage)?; let n=input.read(&mut buf).map_err(|e|e.to_string())?; if n==0 {break}
        bytes+=n as u64; if bytes>MAX_MEDIA+MAX_TEXT {return Err("Backup size limit exceeded".into())}
        digest.update(&buf[..n]); output.write_all(&buf[..n]).map_err(|e|e.to_string())?;
    } Ok((bytes,format!("{:x}",digest.finalize())))
}
pub(super) struct Validated {
    pub files: Vec<String>, pub sms: usize, pub mms: usize, pub parts: usize,
    pub media_bytes: u64, pub metadata: Value,
}
pub(super) fn validate(stage: &Path) -> Result<Validated> {validate_at(stage,stage)}
pub(super) fn validate_at(stage: &Path, cancel: &Path) -> Result<Validated> {
    dir(stage)?; dir(&stage.join("media"))?; check_cancel(cancel)?;
    regular(&stage.join("records.jsonl"),MAX_TEXT)?;
    let mut lines=BufReader::new(File::open(stage.join("records.jsonl")).map_err(|e|e.to_string())?);
    let mut ids=HashSet::new(); let mut mms=HashSet::new(); let mut media_names=HashSet::new(); let mut files=vec!["records.jsonl".to_string(),"metadata.json".to_string()];
    let(mut sms,mut mm,mut parts,mut rows,mut media_bytes)=(0,0,0,0,0u64);
    loop {
        check_cancel(cancel)?; let mut line=Vec::new();
        let n=(&mut lines).take(1_048_577).read_until(b'\n',&mut line).map_err(|e|e.to_string())?;
        if n==0 {break} rows+=1;
        if n>1_048_576 || rows>MAX_RECORDS || !line.ends_with(b"\n") {return Err("Backup record limit exceeded".into())}
        let r:Record=serde_json::from_slice(&line).map_err(|e|e.to_string())?;
        if !numeric(&r.id) || !ids.insert(format!("{}:{}:{}",r.kind,r.parent.as_deref().unwrap_or(""),r.id)) || r.fields.len()>64 || r.fields.iter().any(|(k,v)| k.len()>64 || !(v.is_null()||v.is_string()||v.is_number())) {return Err("Invalid backup record".into())}
        match r.kind.as_str() {
            "sms"|"mms" => {
                if r.parent.is_some() || r.file.is_some() || r.fields.get("_id").and_then(Value::as_str)!=Some(&r.id) {return Err("Invalid message identity".into())}
                let box_key=if r.kind=="sms" {"type"} else {"msg_box"};
                let box_value=r.fields.get(box_key).and_then(Value::as_str).ok_or("Missing message box")?;
                if box_value=="3" {return Err("Drafts are excluded from history backups".into())}
                if r.kind=="sms" {sms+=1} else {mm+=1;mms.insert(r.id.clone());}
                if sms+mm>MAX_MESSAGES {return Err("Message limit exceeded".into())}
            },
            "address"|"part" => {
                if !r.parent.as_ref().is_some_and(|p|mms.contains(p)) {return Err("Missing MMS parent".into())}
                if r.kind=="address" && r.file.is_some() {return Err("Invalid address data".into())}
                if r.kind=="part" {
                    parts+=1; if parts>100_000 {return Err("Backup exceeds 100,000 MMS parts".into())}
                    if r.fields.get("_id").and_then(Value::as_str)!=Some(&r.id) {return Err("Invalid part identity".into())}
                    if let Some(name)=r.file {
                        if name!=format!("media/{}.bin",r.id) || !media_names.insert(name.clone()) {return Err("Invalid attachment path".into())}
                        media_bytes+=regular(&stage.join(&name),MAX_PART)?;
                        if media_bytes>MAX_MEDIA {return Err("Media limit exceeded".into())} files.push(name);
                    }
                }
            },_=>return Err("Unknown history record".into())
        }
    }
    regular(&stage.join("metadata.json"),8_000_000)?;
    let metadata:Value=serde_json::from_reader(File::open(stage.join("metadata.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    if metadata["format"]!="onklav-app-backup" || metadata["version"]!=1 {return Err("Invalid app metadata".into())}
    // This also verifies the metadata shape, without restoring anything.
    Ok(Validated { files, sms, mms:mm, parts, media_bytes,
        metadata:crate::messaging::dispatch(stage,&json!({"action":"backup_check","backup":metadata["metadata"]}))? })
}
fn pack(stage: &Path) -> Result<Value> {
    let Validated { files, sms, mms:mm, parts, .. }=validate(stage)?;
    let output=stage.join("history.zip");
    let result=(|| {
        let file=File::options().write(true).create_new(true).open(&output).map_err(|e|e.to_string())?;
        let mut zip=ZipWriter::new(file); let mut entries=Vec::new();
        for name in files {
            regular(&stage.join(&name),MAX_MEDIA)?;
            zip.start_file(&name,SimpleFileOptions::default().compression_method(CompressionMethod::Stored)).map_err(|e|e.to_string())?;
            let (bytes,sha256)=stream(stage,File::open(stage.join(&name)).map_err(|e|e.to_string())?,&mut zip)?;
            entries.push(Entry{name,bytes,sha256});
        }
        let index=json!({"format":"onklav-message-history","version":1,"sms":sms,"mms":mm,"parts":parts,"entries":entries,
            "date_units":{"sms":"milliseconds","mms":"seconds"},"encrypted":false,"restore_supported":false});
        zip.start_file("index.json",SimpleFileOptions::default().compression_method(CompressionMethod::Stored)).map_err(|e|e.to_string())?;
        zip.write_all(&serde_json::to_vec(&index).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
        zip.finish().map_err(|e|e.to_string())?.sync_all().map_err(|e|e.to_string())?;
        // Reopen and read every byte. ZIP CRC and the independent SHA-256 index must agree.
        let mut verify=ZipArchive::new(File::open(&output).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
        if verify.len()!=entries.len()+1 {return Err("Archive entry mismatch".into())}
        for e in entries {
            let item=verify.by_name(&e.name).map_err(|e|e.to_string())?;
            if item.size()!=e.bytes {return Err("Archive size mismatch".into())}
            let (bytes,hash)=stream(stage,item,std::io::sink())?;
            if bytes!=e.bytes || hash!=e.sha256 {return Err("Archive checksum mismatch".into())}
        }
        let mut saved=String::new(); verify.by_name("index.json").map_err(|e|e.to_string())?.take(64*1024*1024).read_to_string(&mut saved).map_err(|e|e.to_string())?;
        if serde_json::from_str::<Value>(&saved).map_err(|e|e.to_string())?!=index {return Err("Archive index mismatch".into())}
        check_cancel(stage)?; Ok(json!({"sms":sms,"mms":mm,"parts":parts,"bytes":fs::metadata(&output).map_err(|e|e.to_string())?.len(),"verified":true}))
    })();
    if result.is_err() {let _=fs::remove_file(output);} result
}
pub fn dispatch(root:&Path,req:&Value)->Result<Value> {
    let id=req["job"].as_str().ok_or("Missing backup job")?;
    if !crate::valid_id(id) || req["action"]!="pack" {return Err("Invalid backup operation".into())}
    let base=root.join("history-export");dir(&base)?;let stage:PathBuf=base.join(id);pack(&stage)
}
#[cfg(test)] mod tests {
    use super::*;
    fn fixture()->(tempfile::TempDir,PathBuf) {
        let t=tempfile::tempdir().unwrap();let s=t.path().join("job");fs::create_dir_all(s.join("media")).unwrap();
        let m=crate::messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap();
        fs::write(s.join("metadata.json"),json!({"format":"onklav-app-backup","version":1,"metadata":m}).to_string()).unwrap();(t,s)
    }
    #[test] fn streams_provider_records_and_verifies_archive() {
        let(_t,s)=fixture();
        fs::write(s.join("records.jsonl"),concat!("{\"kind\":\"sms\",\"id\":\"1\",\"fields\":{\"_id\":\"1\",\"type\":\"1\",\"body\":\"hello\"}}\n",
        "{\"kind\":\"mms\",\"id\":\"2\",\"fields\":{\"_id\":\"2\",\"msg_box\":\"1\"}}\n",
        "{\"kind\":\"part\",\"id\":\"9\",\"parent\":\"2\",\"fields\":{\"_id\":\"9\",\"ct\":\"image/png\"},\"file\":\"media/9.bin\"}\n")).unwrap();
        fs::write(s.join("media/9.bin"),b"fixture-media").unwrap();let r=pack(&s).unwrap();assert_eq!(r["verified"],true);assert_eq!(r["sms"],1);assert_eq!(r["mms"],1);
        let mut z=ZipArchive::new(File::open(s.join("history.zip")).unwrap()).unwrap();let mut data=Vec::new();z.by_name("media/9.bin").unwrap().read_to_end(&mut data).unwrap();assert_eq!(data,b"fixture-media");
    }
    #[test] fn rejects_escape_orphan_missing_and_drafts() {
        let(_t,s)=fixture();
        for record in [json!({"kind":"part","id":"2","parent":"1","fields":{},"file":"../../secret"}),json!({"kind":"sms","id":"1","fields":{"_id":"1","type":"3"}}),json!({"kind":"unknown","id":"1","fields":{}})] {
            fs::write(s.join("records.jsonl"),format!("{record}\n")).unwrap();assert!(pack(&s).is_err());assert!(!s.join("history.zip").exists());
        }
        let parent=json!({"kind":"mms","id":"1","fields":{"_id":"1","msg_box":"1"}});
        for name in ["../../secret", "media/wrong.bin", "media/2.bin"] {
            let part=json!({"kind":"part","id":"2","parent":"1","fields":{"_id":"2","ct":"image/png"},"file":name});
            fs::write(s.join("records.jsonl"),format!("{parent}\n{part}\n")).unwrap();assert!(pack(&s).is_err());assert!(!s.join("history.zip").exists());
        }
        fs::write(s.join("records.jsonl"),format!("{parent}\n{parent}\n")).unwrap();assert!(pack(&s).is_err());
    }
    #[test] fn cancelled_and_oversized_record_do_not_publish() {
        let(_t,s)=fixture();fs::write(s.join("records.jsonl"),vec![b'a';1_048_577]).unwrap();assert!(pack(&s).is_err());
        fs::write(s.join("cancel"),b"").unwrap();assert_eq!(pack(&s).unwrap_err(),"Backup stopped");assert!(!s.join("history.zip").exists());
    }
    #[cfg(unix)] #[test] fn symlink_media_is_rejected() {
        let(_t,s)=fixture();fs::write(s.join("records.jsonl"),"{\"kind\":\"mms\",\"id\":\"1\",\"fields\":{\"_id\":\"1\",\"msg_box\":\"1\"}}\n{\"kind\":\"part\",\"id\":\"2\",\"parent\":\"1\",\"fields\":{\"_id\":\"2\"},\"file\":\"media/2.bin\"}\n").unwrap();
        std::os::unix::fs::symlink(s.join("metadata.json"),s.join("media/2.bin")).unwrap();assert!(pack(&s).is_err());
    }
}
