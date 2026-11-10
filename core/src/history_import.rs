//! Read-only inspection of untrusted history ZIPs. Never authorizes a provider write.
use crate::{history_backup::{self, Entry}, Result};
use serde::Deserialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{collections::{HashMap, HashSet}, fs::{self, File}, io::{BufRead, BufReader, Read, Write, Seek, SeekFrom}, path::Path};
use zip::{CompressionMethod, ZipArchive};
const MAX_ARCHIVE:u64=2_400_000_000;
const MAX_INDEX:u64=24*1024*1024;
const MAX_MEDIA:u64=2*1024*1024*1024;
const MAX_TEXT:u64=128*1024*1024;
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Units {sms:String,mms:String}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Index {
    format:String,version:u32,sms:usize,mms:usize,parts:usize,entries:Vec<Entry>,
    date_units:Units,encrypted:bool,restore_supported:bool,
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Metadata {format:String,version:u32,origin:String,created:i64,metadata:Value}
fn media_name(name:&str)->bool {
    name.strip_prefix("media/").and_then(|n|n.strip_suffix(".bin")).is_some_and(id)
}
fn id(s:&str)->bool {s.parse::<u64>().is_ok_and(|v|v<=i64::MAX as u64&&v.to_string()==s)}
fn cap(name:&str)->Result<u64> {match name {
    "records.jsonl"=>Ok(MAX_TEXT),"metadata.json"=>Ok(8_000_000),_ if media_name(name)=>Ok(64*1024*1024),_=>Err("Unsupported backup entry".into())
}}
fn copy_checked(stage:&Path,mut input:impl Read,mut out:impl Write,limit:u64)->Result<(u64,String)> {
    let mut hash=Sha256::new();let mut size=0;let mut buffer=[0;65536];
    loop {history_backup::check_cancel(stage)?;let n=input.read(&mut buffer).map_err(|e|e.to_string())?;if n==0{break}
        size+=n as u64;if size>limit{return Err("Backup entry exceeds limits".into())}
        hash.update(&buffer[..n]);out.write_all(&buffer[..n]).map_err(|e|e.to_string())?;
    }Ok((size,format!("{:x}",hash.finalize())))
}
// Only the provider columns emitted by OnKlav v1 are accepted. No _data paths,
// unexpected nested values, arbitrary URI/selection or executable fields.
const SMS:&str="_id thread_id address person date date_sent protocol read status type reply_path_present subject body service_center locked sub_id error_code creator seen";
const MMS:&str="_id thread_id date date_sent msg_box read m_id sub sub_cs ct_t ct_l exp m_cls m_type v m_size pri rr rpt_a resp_st st tr_id retr_st retr_txt retr_txt_cs read_status ct_cls resp_txt d_tm d_rpt locked sub_id creator seen text_only";
const PART:&str="_id seq ct name chset cd fn cid cl text";
const ADDRESS:&str="address type charset";
fn number(fields:&Value,key:&str)->Result<u64> {
    let s=fields[key].as_str().ok_or_else(||format!("Missing history {key}"))?;
    s.parse::<u64>().map_err(|_|format!("Invalid history {key}"))
}
pub(super) fn strict_records(stage:&Path,cancel:&Path)->Result<Value> {
    let(mut inbox,mut sent,mut other,mut pending)=(0usize,0usize,0usize,0usize);
    let mut part_ids=HashSet::new();
    let mut reader=BufReader::new(File::open(stage.join("records.jsonl")).map_err(|e|e.to_string())?);
    loop {
        history_backup::check_cancel(cancel)?;let mut line=Vec::new();
        let n=(&mut reader).take(1_048_577).read_until(b'\n',&mut line).map_err(|e|e.to_string())?;
        if n==0{break}if n>1_048_576{return Err("History record exceeds limits".into())}
        let r:Value=serde_json::from_slice(&line).map_err(|e|e.to_string())?;
        if !r["id"].as_str().is_some_and(id) {return Err("Invalid provider identity".into())}
        if let Some(p)=r.get("parent") {if !p.as_str().is_some_and(id){return Err("Invalid parent identity".into())}}
        let kind=r["kind"].as_str().ok_or("Missing history kind")?;
        let allowed=match kind {"sms"=>SMS,"mms"=>MMS,"part"=>PART,"address"=>ADDRESS,_=>return Err("Unknown history record".into())};
        let fields=r["fields"].as_object().ok_or("Invalid history fields")?;
        for (name,value) in fields {
            if !allowed.split(' ').any(|v|v==name) || !(value.is_string()||value.is_null()) {return Err("Unsupported history field".into())}
        }
        let f=&r["fields"];
        match kind {
            "sms"|"mms"=>{
                let date=number(f,"date")?;
                if (if kind=="mms" {date.checked_mul(1000)}else{Some(date)}).is_none_or(|n|n>i64::MAX as u64) {return Err("Invalid message date".into())}
                let b=number(f,if kind=="sms" {"type"}else{"msg_box"})?;
                if b==0||b==3||b>if kind=="sms"{6}else{5} {return Err("Unsupported message box".into())}
                if kind=="sms"&&!fields.contains_key("body") {return Err("Missing SMS body".into())}
                if b==1{inbox+=1}else if b==2{sent+=1}else{other+=1}
                if kind=="mms"&&f["m_type"].as_str()==Some("130"){pending+=1}
            },
            "part"=>{
                if !part_ids.insert(r["id"].as_str().unwrap().to_string()){return Err("Duplicate MMS part identity".into())}
                let mime=f["ct"].as_str().ok_or("Missing part MIME type")?;
                if mime.is_empty()||mime.len()>256||mime.chars().any(char::is_control){return Err("Invalid part MIME type".into())}
                if r.get("file").is_none()&&!f["text"].is_string(){return Err("Missing part content".into())}
            },
            "address"=>{
                if !fields.contains_key("address")||!matches!(number(f,"type")?,129|130|137|151){return Err("Invalid MMS address record".into())}
            },_=>unreachable!()
        }
    }
    Ok(json!({"inbox":inbox,"sent":sent,"other":other,"undownloaded":pending}))
}
// Bound the central directory *before* ZipArchive allocates its entry table.
// ZIP64 is needed for legitimate exports with more than 65,535 media files.
fn directory_preflight(source:&Path,cancel:&Path)->Result<()> {
    let mut file=BufReader::new(File::open(source).map_err(|e|e.to_string())?);
    let size=file.get_ref().metadata().map_err(|e|e.to_string())?.len();
    if size<22||size>MAX_ARCHIVE{return Err("Choose an OnKlav history ZIP".into())}
    let tail_size=size.min(65_557);file.seek(SeekFrom::End(-(tail_size as i64))).map_err(|e|e.to_string())?;
    let mut tail=vec![0;tail_size as usize];file.read_exact(&mut tail).map_err(|e|e.to_string())?;
    fn u16_at(b:&[u8],i:usize)->u16 {u16::from_le_bytes(b[i..i+2].try_into().unwrap())}
    fn u32_at(b:&[u8],i:usize)->u32 {u32::from_le_bytes(b[i..i+4].try_into().unwrap())}
    fn u64_at(b:&[u8],i:usize)->u64 {u64::from_le_bytes(b[i..i+8].try_into().unwrap())}
    let at=(0..=tail.len()-22).rev().find(|&i|tail[i..i+4]==[80,75,5,6]&&i+22+u16_at(&tail,i+20) as usize==tail.len()).ok_or("Missing ZIP directory")?;
    let eocd=&tail[at..];let eocd_at=size-tail_size+at as u64;
    if u16_at(eocd,4)!=0||u16_at(eocd,6)!=0||u16_at(eocd,8)!=u16_at(eocd,10){return Err("Split ZIPs are unsupported".into())}
    let(mut count,mut bytes,mut offset,mut end)=(u16_at(eocd,10) as u64,u32_at(eocd,12) as u64,u32_at(eocd,16) as u64,eocd_at);
    if count==65535||bytes==u32::MAX as u64||offset==u32::MAX as u64 {
        if eocd_at<20{return Err("Invalid ZIP64 locator".into())}
        file.seek(SeekFrom::Start(eocd_at-20)).map_err(|e|e.to_string())?;let mut locator=[0;20];file.read_exact(&mut locator).map_err(|e|e.to_string())?;
        if locator[..4]!=[80,75,6,7]||u32_at(&locator,4)!=0||u32_at(&locator,16)!=1{return Err("Invalid ZIP64 locator".into())}
        let record_at=u64_at(&locator,8);if record_at.checked_add(56).is_none_or(|n|n>eocd_at-20){return Err("Invalid ZIP64 directory".into())}
        file.seek(SeekFrom::Start(record_at)).map_err(|e|e.to_string())?;let mut z64=[0;56];file.read_exact(&mut z64).map_err(|e|e.to_string())?;
        let record_size=u64_at(&z64,4);
        if z64[..4]!=[80,75,6,6]||record_size<44||record_size>1024||record_at.checked_add(12+record_size)!=Some(eocd_at-20)||u32_at(&z64,16)!=0||u32_at(&z64,20)!=0||u64_at(&z64,24)!=u64_at(&z64,32){return Err("Invalid ZIP64 directory".into())}
        count=u64_at(&z64,32);bytes=u64_at(&z64,40);offset=u64_at(&z64,48);end=record_at;
    }
    if !(3..=100_003).contains(&count)||bytes>32*1024*1024||offset.checked_add(bytes)!=Some(end){return Err("ZIP directory exceeds limits".into())}
    file.seek(SeekFrom::Start(offset)).map_err(|e|e.to_string())?;
    for _ in 0..count {
        history_backup::check_cancel(cancel)?;let mut header=[0;46];file.read_exact(&mut header).map_err(|e|e.to_string())?;
        let name=u16_at(&header,28) as u64;let extra=u16_at(&header,30) as u64;let comment=u16_at(&header,32);
        if header[..4]!=[80,75,1,2]||name==0||name>80||extra>1024||comment!=0||u16_at(&header,34)!=0{return Err("Unsupported ZIP directory entry".into())}
        let next=file.stream_position().map_err(|e|e.to_string())?.checked_add(name+extra).ok_or("Invalid directory extent")?;
        if next>end{return Err("Invalid directory extent".into())}file.seek(SeekFrom::Start(next)).map_err(|e|e.to_string())?;
    }
    if file.stream_position().map_err(|e|e.to_string())?!=end{return Err("ZIP directory count mismatch".into())}Ok(())
}
fn inspect(stage:&Path,compare:bool,expected:Option<&str>)->Result<Value> {
    history_backup::dir(stage)?;history_backup::check_cancel(stage)?;
    let source=stage.join("source.zip");history_backup::regular(&source,MAX_ARCHIVE)?;
    directory_preflight(&source,stage)?;
    let (_,archive_sha)=copy_checked(stage,File::open(&source).map_err(|e|e.to_string())?,std::io::sink(),MAX_ARCHIVE)?;
    if let Some(expected)=expected{if expected!=archive_sha{return Err("Backup changed since checking. Choose it again.".into())}}
    let mut zip=ZipArchive::new(File::open(&source).map_err(|e|e.to_string())?).map_err(|_|"Choose an OnKlav history ZIP")?;
    if zip.len()<3||zip.len()>100_003{return Err("Invalid backup entry count".into())}
    let mut names=HashSet::new();let mut extents=Vec::new();
    for i in 0..zip.len() {
        history_backup::check_cancel(stage)?;let entry=zip.by_index(i).map_err(|e|e.to_string())?;
        let name=entry.name();
        let maximum=if name=="index.json"{MAX_INDEX}else{cap(name)?};
        if !names.insert(name.to_string())||entry.is_dir()||entry.size()>maximum||entry.compression()!=CompressionMethod::Stored||entry.compressed_size()!=entry.size()
            ||entry.unix_mode().is_some_and(|m|!matches!(m&0o170000,0|0o100000)) {return Err("Unsupported or duplicate ZIP entry".into())}
        let start=entry.data_start();let end=start.checked_add(entry.compressed_size()).ok_or("Invalid ZIP extent")?;
        if end>fs::metadata(&source).map_err(|e|e.to_string())?.len(){return Err("Truncated backup entry".into())}
        extents.push((start,end));
    }
    extents.sort_unstable();if extents.windows(2).any(|p|p[0].1>p[1].0){return Err("Overlapping backup entries".into())}
    let index:Index={
        let mut entry=zip.by_name("index.json").map_err(|_|"Missing backup index")?;
        let mut bytes=Vec::new();(&mut entry).take(MAX_INDEX+1).read_to_end(&mut bytes).map_err(|e|e.to_string())?;
        if bytes.len() as u64>MAX_INDEX{return Err("Backup index exceeds limits".into())}
        serde_json::from_slice(&bytes).map_err(|_|"Invalid backup index")?
    };
    if index.format!="onklav-message-history"||index.version!=1||index.encrypted||index.date_units.sms!="milliseconds"||index.date_units.mms!="seconds"
        ||index.sms.checked_add(index.mms).is_none_or(|n|n>200_000)||index.parts>100_000||index.entries.len()+1!=zip.len() {return Err("Unsupported history format or limits".into())}
    // restore_supported is the exporter version's informational flag, never authority.
    let _=index.restore_supported;
    let mut declared=HashMap::new();let mut media_bytes=0u64;
    for e in &index.entries {
        let maximum=cap(&e.name)?;
        if !names.contains(&e.name)||e.bytes>maximum||e.sha256.len()!=64||!e.sha256.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))||declared.insert(e.name.clone(),e.bytes).is_some() {return Err("Invalid backup index entry".into())}
        if media_name(&e.name){media_bytes=media_bytes.checked_add(e.bytes).ok_or("Media limit exceeded")?;if media_bytes>MAX_MEDIA{return Err("Media limit exceeded".into())}}
    }
    if !declared.contains_key("records.jsonl")||!declared.contains_key("metadata.json"){return Err("Missing backup records or metadata".into())}
    let unpacked=stage.join("checked");fs::create_dir(&unpacked).map_err(|e|e.to_string())?;fs::create_dir(unpacked.join("media")).map_err(|e|e.to_string())?;
    // All output names were checked above. Fresh private directories + create_new;
    // never zip.extract(), never join an arbitrary archive path or follow links.
    for e in &index.entries {
        history_backup::check_cancel(stage)?;let entry=zip.by_name(&e.name).map_err(|e|e.to_string())?;
        if entry.size()!=e.bytes{return Err("Backup entry size mismatch".into())}
        let mut out=File::options().create_new(true).write(true).open(unpacked.join(&e.name)).map_err(|e|e.to_string())?;
        let (size,hash)=copy_checked(stage,entry.take(e.bytes+1),&mut out,e.bytes)?;
        if size!=e.bytes||hash!=e.sha256{return Err("Backup checksum mismatch".into())}
        out.sync_all().map_err(|e|e.to_string())?;
    }
    history_backup::check_cancel(stage)?;
    let validated=history_backup::validate_at(&unpacked,stage)?;
    let envelope:Metadata=serde_json::from_reader(File::open(unpacked.join("metadata.json")).map_err(|e|e.to_string())?).map_err(|_|"Invalid metadata envelope")?;
    if envelope.format!="onklav-app-backup"||envelope.version!=1||uuid::Uuid::parse_str(&envelope.origin).is_err()||envelope.created<0||!envelope.metadata.is_object(){return Err("Invalid metadata envelope".into())}
    if validated.sms!=index.sms||validated.mms!=index.mms||validated.parts!=index.parts||validated.media_bytes!=media_bytes||validated.files.len()!=declared.len()||validated.files.iter().any(|n|!declared.contains_key(n)) {return Err("Backup content does not match its index".into())}
    let categories=strict_records(&unpacked,stage)?;
    history_backup::check_cancel(stage)?;
    let comparison=if compare {
        let current=stage.join("current");history_backup::dir(&current)?;
        history_backup::validate_at(&current,stage)?;strict_records(&current,stage)?;
        Some(crate::history_plan::compare(&unpacked,&current,stage)?)
    }else{None};
    Ok(json!({"comparison":comparison,"format":"onklav-message-history","version":1,"sms":validated.sms,"mms":validated.mms,"parts":validated.parts,
        "media_bytes":media_bytes,"metadata":validated.metadata,"categories":categories,"sha256":archive_sha,
        "verified":true,"provider_writes":false,"restore_available":false}))
}
pub fn dispatch(root:&Path,request:&Value)->Result<Value> {
    let job=request["job"].as_str().ok_or("Missing import job")?;
    if !crate::valid_id(job)||!matches!(request["action"].as_str(),Some("inspect"|"compare"|"prepare"|"finish"|"merge"|"resume_merge")){return Err("Invalid history import operation".into())}
    let base=root.join("history-import");history_backup::dir(&base)?;let stage=base.join(job);
    if request["action"]=="resume_merge"{history_backup::dir(&stage)?;return crate::history_apply::resume_merge(root,&stage,request["record"].as_str().ok_or("Missing restore record")?,request["map_sha"].as_str().ok_or("Missing saved record identity")?)}
    if request["action"]=="merge"{history_backup::dir(&stage)?;return crate::history_apply::merge(root,&stage,job)}
    if request["action"]=="finish"{history_backup::dir(&stage)?;return crate::history_apply::finish(root,&stage,job)}
    let prepare=request["action"]=="prepare";
    let compare=request["action"]=="compare"||prepare;
    let expected=if compare {let s=request["expected_sha"].as_str().ok_or("Check the backup before comparing")?;
        if s.len()!=64||!s.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return Err("Invalid checked-backup identity".into())}Some(s)
    }else{None};
    let result=inspect(&stage,compare,expected).and_then(|mut result|{if prepare&&result["comparison"]["new"].as_u64().unwrap_or(0)<=1000{let n=crate::history_apply::prepare(&stage,result["sha256"].as_str().ok_or("Missing archive identity")?)?;result["prepared"]=json!(n);}if let Some(id)=request["record"].as_str(){result["resume"]=crate::history_resume::compare(root,&stage,id,request["map_sha"].as_str().ok_or("Missing saved record identity")?,&result)?;}Ok(result)});
    // Reviewed information is returned; no raw history survives this operation.
    let checked=stage.join("checked");if checked.exists()&&(!prepare||result.is_err()){let cleanup=fs::remove_dir_all(&checked).map_err(|e|e.to_string());if result.is_ok(){cleanup?;}}
    result
}

#[cfg(test)]mod tests {
    use super::*;
    use zip::{ZipWriter,write::SimpleFileOptions};
    const JOB:&str="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    fn fixture()->(tempfile::TempDir,std::path::PathBuf,Vec<(String,Vec<u8>)>) {
        let t=tempfile::tempdir().unwrap();let stage=t.path().join("history-import").join(JOB);fs::create_dir_all(&stage).unwrap();
        let metadata=crate::messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap();
        let records=concat!(
            "{\"kind\":\"sms\",\"id\":\"1\",\"fields\":{\"_id\":\"1\",\"date\":\"1700000000123\",\"type\":\"1\",\"body\":\"hello\"}}\n",
            "{\"kind\":\"mms\",\"id\":\"2\",\"fields\":{\"_id\":\"2\",\"date\":\"1700000000\",\"msg_box\":\"2\"}}\n",
            "{\"kind\":\"address\",\"id\":\"0\",\"parent\":\"2\",\"fields\":{\"address\":\"5550101234\",\"type\":\"151\"}}\n",
            "{\"kind\":\"part\",\"id\":\"9\",\"parent\":\"2\",\"fields\":{\"_id\":\"9\",\"ct\":\"image/png\"},\"file\":\"media/9.bin\"}\n");
        let entries=vec![("records.jsonl".into(),records.as_bytes().to_vec()),("metadata.json".into(),json!({"format":"onklav-app-backup","version":1,"origin":"12345678-1234-4234-8234-123456789abc","created":1700000000000i64,"metadata":metadata}).to_string().into_bytes()),("media/9.bin".into(),b"media fixture".to_vec())];
        (t,stage,entries)
    }
    fn archive(stage:&Path,entries:&[(String,Vec<u8>)],edit:impl FnOnce(&mut Value)) {
        let indexed:Vec<Value>=entries.iter().map(|(n,b)|json!({"name":n,"bytes":b.len(),"sha256":format!("{:x}",Sha256::digest(b))})).collect();
        let mut index=json!({"format":"onklav-message-history","version":1,"sms":1,"mms":1,"parts":1,"entries":indexed,"date_units":{"sms":"milliseconds","mms":"seconds"},"encrypted":false,"restore_supported":false});edit(&mut index);
        let mut zip=ZipWriter::new(File::create(stage.join("source.zip")).unwrap());
        for (n,b) in entries {zip.start_file(n,SimpleFileOptions::default().compression_method(CompressionMethod::Stored)).unwrap();zip.write_all(b).unwrap();}
        zip.start_file("index.json",SimpleFileOptions::default().compression_method(CompressionMethod::Stored)).unwrap();zip.write_all(index.to_string().as_bytes()).unwrap();zip.finish().unwrap();
    }
    fn call(root:&Path)->Result<Value> {dispatch(root,&json!({"action":"inspect","job":JOB}))}
    #[test]fn checked_review_returns_counts_without_retaining_extracted_history_or_writing_messages() {
        let(t,s,e)=fixture();archive(&s,&e,|_|{});let before=crate::messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap();
        let r=call(t.path()).unwrap();assert_eq!(r["sms"],1);assert_eq!(r["parts"],1);assert_eq!(r["categories"]["sent"],1);assert_eq!(r["restore_available"],false);assert_eq!(r["provider_writes"],false);assert!(!s.join("checked").exists());assert!(s.join("source.zip").exists());
        assert_eq!(before,crate::messaging::dispatch(t.path(),&json!({"action":"backup_export"})).unwrap());
    }
    #[test]fn zip64_directory_is_supported_but_forged_entry_counts_fail_before_zip_allocation() {
        let(t,s,e)=fixture();archive(&s,&e,|_|{});
        let original=fs::read(s.join("source.zip")).unwrap();let at=original.len()-22;
        let mut footer=original[at..].to_vec();let mut bytes=original[..at].to_vec();
        let count=u16::from_le_bytes(footer[10..12].try_into().unwrap()) as u64;
        let cd_size=u32::from_le_bytes(footer[12..16].try_into().unwrap()) as u64;
        let cd_at=u32::from_le_bytes(footer[16..20].try_into().unwrap()) as u64;
        let mut z64=vec![80,75,6,6];z64.extend(44u64.to_le_bytes());z64.extend(45u16.to_le_bytes());z64.extend(45u16.to_le_bytes());
        z64.extend(0u32.to_le_bytes());z64.extend(0u32.to_le_bytes());z64.extend(count.to_le_bytes());z64.extend(count.to_le_bytes());z64.extend(cd_size.to_le_bytes());z64.extend(cd_at.to_le_bytes());
        bytes.extend(&z64);bytes.extend([80,75,6,7]);bytes.extend(0u32.to_le_bytes());bytes.extend((at as u64).to_le_bytes());bytes.extend(1u32.to_le_bytes());
        footer[8..12].copy_from_slice(&[255,255,255,255]);bytes.extend(footer);
        fs::write(s.join("source.zip"),&bytes).unwrap();directory_preflight(&s.join("source.zip"),&s).unwrap();assert_eq!(call(t.path()).unwrap()["verified"],true);
        bytes[at+24..at+32].copy_from_slice(&1_000_000u64.to_le_bytes());bytes[at+32..at+40].copy_from_slice(&1_000_000u64.to_le_bytes());
        fs::write(s.join("source.zip"),bytes).unwrap();assert!(directory_preflight(&s.join("source.zip"),&s).unwrap_err().contains("limits"));assert!(call(t.path()).is_err());
    }
    #[test]fn corrupt_hash_counts_and_unsupported_versions_fail_with_cleanup() {
        let(t,s,e)=fixture();
        archive(&s,&e,|i|i["entries"][2]["sha256"]=json!("0".repeat(64)));assert!(call(t.path()).unwrap_err().contains("checksum"));assert!(!s.join("checked").exists());
        archive(&s,&e,|i|i["sms"]=json!(20));assert!(call(t.path()).unwrap_err().contains("index"));assert!(!s.join("checked").exists());
        archive(&s,&e,|i|i["version"]=json!(999));assert!(call(t.path()).is_err());
        archive(&s,&e,|i|i["date_units"]["mms"]=json!("milliseconds"));assert!(call(t.path()).is_err());
    }
    #[test]fn escape_extra_missing_and_forged_sizes_are_rejected() {
        let(t,s,e)=fixture();
        for name in ["../outside","media/../outside","media/01.bin","/tmp/outside","media/9.bin/child"] {
            let mut bad=e.clone();bad.push((name.into(),b"data".to_vec()));archive(&s,&bad,|_|{});assert!(call(t.path()).is_err());assert!(!t.path().join("outside").exists());
        }
        archive(&s,&e,|i|i["entries"][2]["bytes"]=json!(64*1024*1024+1));assert!(call(t.path()).is_err());
        archive(&s,&e,|i|{i["entries"].as_array_mut().unwrap().pop();});assert!(call(t.path()).is_err());
        let mut missing=e.clone();missing.pop();archive(&s,&missing,|_|{});assert!(call(t.path()).is_err());assert!(!s.join("checked").exists());
    }
    #[test]fn malformed_records_unknown_fields_duplicate_fields_and_ids_fail_even_with_valid_hashes() {
        let(t,s,e)=fixture();
        let original=String::from_utf8(e[0].1.clone()).unwrap();
        for body in [
            original.replace("\"body\":\"hello\"","\"body\":\"hello\",\"_data\":\"/private/path\""),
            original.replace("\"date\":\"1700000000123\"","\"date\":\"18446744073709551615\""),
            original.replace("\"body\":\"hello\"","\"body\":\"hello\",\"body\":\"other\""),
            original.replace("\"parent\":\"2\"","\"parent\":\"3\""),
            original.replace("\"type\":\"1\"","\"type\":\"3\""),
            original.clone()+"{\"kind\":\"sms\",\"id\":\"1\",\"fields\":{\"_id\":\"1\",\"date\":\"1\",\"type\":\"1\",\"body\":\"x\"}}\n",
        ] {
            let mut bad=e.clone();bad[0].1=body.into_bytes();archive(&s,&bad,|_|{});assert!(call(t.path()).is_err());assert!(!s.join("checked").exists());
        }
    }
    #[test]fn cancellation_compression_and_symlink_entry_do_not_produce_review() {
        let(t,s,e)=fixture();archive(&s,&e,|_|{});
        let mut bytes=fs::read(s.join("source.zip")).unwrap();let eocd=bytes.len()-22;
        bytes[eocd+8..eocd+12].copy_from_slice(&[255,255,255,255]);fs::write(s.join("source.zip"),bytes).unwrap();assert!(call(t.path()).is_err());
        archive(&s,&e,|_|{});fs::write(s.join("cancel"),b"").unwrap();assert_eq!(call(t.path()).unwrap_err(),"Backup stopped");fs::remove_file(s.join("cancel")).unwrap();
        let mut zip=ZipWriter::new(File::create(s.join("source.zip")).unwrap());
        for (n,b) in &e {zip.start_file(n,SimpleFileOptions::default().compression_method(CompressionMethod::Deflated)).unwrap();zip.write_all(b).unwrap();}
        zip.start_file("index.json",SimpleFileOptions::default()).unwrap();zip.write_all(b"{}").unwrap();zip.finish().unwrap();assert!(call(t.path()).unwrap_err().contains("ZIP entry"));
        let mut zip=ZipWriter::new(File::create(s.join("source.zip")).unwrap());zip.add_symlink("records.jsonl","/outside",SimpleFileOptions::default()).unwrap();zip.start_file("metadata.json",SimpleFileOptions::default()).unwrap();zip.write_all(b"{}").unwrap();zip.start_file("index.json",SimpleFileOptions::default()).unwrap();zip.write_all(b"{}").unwrap();zip.finish().unwrap();assert!(call(t.path()).is_err());assert!(!s.join("checked").exists());
    }
}
