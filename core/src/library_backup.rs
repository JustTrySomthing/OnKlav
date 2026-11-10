//! Host-only portable Files/Annotation archives; no executable or permission restore.
use crate::Result;
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::{BTreeMap,BTreeSet},fs::{self,File,OpenOptions},io::{Read,Write},path::{Path,PathBuf}};
use zip::{ZipArchive,ZipWriter,CompressionMethod,write::SimpleFileOptions};
pub(crate) const MAX:u64=512*1024*1024;
#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]
pub(crate) struct Blob{pub bytes:u64,pub sha256:String}
#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]
struct Manifest{format:String,version:u32,kind:String,records:Value,blobs:BTreeMap<String,Blob>}
pub(crate) struct Plan{pub new:usize,pub kept:usize,pub targets:BTreeMap<String,PathBuf>}
pub(crate) fn check(root:&Path)->Result<()>{if root.join("library-backup.cancel").exists(){Err("Backup operation stopped".into())}else{Ok(())}}
fn regular(p:&Path,max:u64)->Result<u64>{let m=fs::symlink_metadata(p).map_err(|e|e.to_string())?;if !m.is_file()||m.len()>max{return Err("Invalid or oversized backup file".into())}Ok(m.len())}
fn stream(root:&Path,mut input:impl Read,mut output:impl Write,max:u64)->Result<Blob>{
 let mut size=0;let mut hash=Sha256::new();let mut buf=[0;65536];loop{check(root)?;let n=input.read(&mut buf).map_err(|e|e.to_string())?;if n==0{break}size+=n as u64;if size>max{return Err("Backup size limit exceeded".into())}hash.update(&buf[..n]);output.write_all(&buf[..n]).map_err(|e|e.to_string())?;}
 Ok(Blob{bytes:size,sha256:format!("{:x}",hash.finalize())})
}
fn same(a:&Blob,b:&Blob)->bool{a.bytes==b.bytes&&a.sha256==b.sha256}
fn hash(root:&Path,p:&Path,max:u64)->Result<Blob>{regular(p,max)?;stream(root,File::open(p).map_err(|e|e.to_string())?,std::io::sink(),max)}
fn plan(root:&Path,data:&Path,m:&Manifest)->Result<Plan>{match m.kind.as_str(){"files"=>crate::content::backup::plan(root,&m.records,&m.blobs),"annotation"=>crate::drawing::backup_plan(data,&m.records,&m.blobs),"documents"=>crate::document_backup::plan(root,&m.records,&m.blobs),"rss"|"reader"|"browser"|"youtube"|"walkie"=>{if !m.blobs.is_empty(){return Err("Unexpected document backup payload".into())}match m.kind.as_str(){"rss"=>crate::feeds::backup_plan(data,&m.records),"reader"=>crate::reader_backup::plan(data,&m.records),"browser"=>crate::browser::backup_plan(data,&m.records),"youtube"=>crate::videos::backup_plan(data,&m.records),_=>crate::document_backup::walkie_plan(data,&m.records)}},_=>Err("Unsupported library backup".into())}}
fn read(root:&Path,data:&Path,p:&Path,kind:&str)->Result<(ZipArchive<File>,Manifest,String,Plan)>{
 let digest=hash(root,p,MAX)?.sha256;let mut zip=ZipArchive::new(File::open(p).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
 if zip.len()>16001{return Err("Too many backup entries".into())}
 let mut names=BTreeSet::new();for i in 0..zip.len(){check(root)?;let e=zip.by_index(i).map_err(|e|e.to_string())?;
  if !names.insert(e.name().to_string())||e.is_dir()||e.compression()!=CompressionMethod::Stored||e.size()!=e.compressed_size()||e.size()>MAX||e.unix_mode().is_some_and(|m|!matches!(m&0o170000,0|0o100000)){return Err("Unsupported or duplicate backup entry".into())}
 }
 let mut bytes=Vec::new();{let e=zip.by_name("manifest.json").map_err(|_|"Missing library backup manifest")?;stream(root,e,&mut bytes,if kind=="documents"{96}else{24}*1024*1024)?;}
 let m:Manifest=serde_json::from_slice(&bytes).map_err(|_|"Invalid library backup manifest")?;
 if m.format!="onklav-library-backup"||m.version!=1||m.kind!=kind||m.blobs.len()+1!=names.len(){return Err("Choose a matching OnKlav library backup".into())}
 let mut total=0;for (name,b) in &m.blobs{check(root)?;if name=="manifest.json"||!names.contains(name)||b.bytes>MAX||b.sha256.len()!=64||!b.sha256.bytes().all(|x|x.is_ascii_hexdigit()&&!x.is_ascii_uppercase()){return Err("Invalid backup entry".into())}
  total+=b.bytes;if total>MAX-24*1024*1024{return Err("Backup exceeds the 512 MiB limit".into())}
  let got=stream(root,zip.by_name(name).map_err(|e|e.to_string())?,std::io::sink(),b.bytes)?;if !same(b,&got){return Err("Backup content failed its integrity check".into())}
 }
 if kind=="documents"&&m.blobs.contains_key("vault/document.bin"){let mut head=[0u8;40];zip.by_name("vault/document.bin").map_err(|_|"Missing Vault payload")?.read_exact(&mut head).map_err(|_|"Invalid Vault payload")?;if &head[..8]!=b"OKVAULT1"||&head[8..]!=b"6f6e6b6c61767661756c74646f637631"{return Err("Invalid Vault identity".into())}}
 let plan=plan(root,data,&m)?;
 if kind=="annotation"||kind=="documents"{let images=if kind=="documents"{&m.records["annotation"]}else{&m.records};for record in images.as_array().ok_or("Invalid image records")?{
  let name=format!("images/{}.jpg",record["id"].as_str().ok_or("Missing image identity")?);let mut image=Vec::new();stream(root,zip.by_name(&name).map_err(|e|e.to_string())?,&mut image,600*1024)?;
  crate::drawing::backup_validate_image(record,&image)?;
 }}
 // Even unindexed remnants are preserved unless they exactly match a retry.
 for (name,target) in &plan.targets{if target.try_exists().map_err(|e|e.to_string())?{if !same(&hash(root,target,MAX)?,&m.blobs[name]){return Err("Existing content conflicts with this backup; nothing was restored".into())}}else if fs::symlink_metadata(target).is_ok(){return Err("Invalid restore destination".into())}}
 Ok((zip,m,digest,plan))
}
pub(crate) fn dispatch(root:&Path,data:&Path,j:&Value)->Result<Value>{
 check(root)?;let kind=j["kind"].as_str().filter(|k|["files","annotation","rss","reader","browser","youtube","walkie","documents"].contains(k)).ok_or("Choose a library backup")?;
 let op=j["operation"].as_str().unwrap_or("");
 if op=="export"{
  let (records,paths)=match kind{"files"=>crate::content::backup::export(root)?,"annotation"=>crate::drawing::backup_export(data)?,"rss"=>(crate::feeds::backup_export(data)?,BTreeMap::new()),"reader"=>(crate::reader_backup::export(data)?,BTreeMap::new()),"browser"=>(crate::browser::backup_export(data)?,BTreeMap::new()),"youtube"=>(crate::videos::backup_export(data)?,BTreeMap::new()),"walkie"=>(crate::document_backup::walkie_export(data)?,BTreeMap::new()),_=>crate::document_backup::export(root)?};
  let mut blobs=BTreeMap::new();let mut total=0;for (name,path) in &paths{let b=hash(root,path,MAX)?;total+=b.bytes;if total>MAX-24*1024*1024{return Err("Library backup exceeds 512 MiB".into())}blobs.insert(name.clone(),b);}
  let m=Manifest{format:"onklav-library-backup".into(),version:1,kind:kind.into(),records,blobs};
  let manifest=serde_json::to_vec(&m).map_err(|e|e.to_string())?;if manifest.len()>if kind=="documents"{96}else{24}*1024*1024{return Err("Backup manifest is too large".into())}
  let output=root.join("library-backup-export.zip");let tmp=root.join("library-backup-export.tmp");
  let result=(||{let mut zip=ZipWriter::new(File::create(&tmp).map_err(|e|e.to_string())?);let opts=SimpleFileOptions::default().compression_method(CompressionMethod::Stored).unix_permissions(0o600);
   zip.start_file("manifest.json",opts).map_err(|e|e.to_string())?;zip.write_all(&manifest).map_err(|e|e.to_string())?;
   for (name,path) in paths{check(root)?;zip.start_file(&name,opts).map_err(|e|e.to_string())?;let got=stream(root,File::open(path).map_err(|e|e.to_string())?,&mut zip,m.blobs[&name].bytes)?;if !same(&got,&m.blobs[&name]){return Err("Content changed during backup".into())}}
   zip.finish().and_then(|f|f.sync_all().map_err(Into::into)).map_err(|e|e.to_string())?;
   let (_,_,_,p)=read(root,data,&tmp,kind)?;check(root)?;fs::rename(&tmp,&output).map_err(|e|e.to_string())?;
   Ok(json!({"items":p.new+p.kept,"bytes":regular(&output,MAX)?}))})();let _=fs::remove_file(tmp);return result
 }
 if !["inspect","restore"].contains(&op){return Err("Unsupported library backup operation".into())}
 let (mut zip,m,digest,p)=read(root,data,&root.join("library-backup-import.zip"),kind)?;
 if op=="restore"{
  if j["sha256"]!=digest{return Err("Backup changed after review".into())}
  for (name,target) in &p.targets{check(root)?;if target.exists(){continue}let tmp=target.with_extension(format!("{}.tmp",uuid::Uuid::new_v4().simple()));
   let result:Result<()>=(||{let mut f=OpenOptions::new().write(true).create_new(true).open(&tmp).map_err(|e|e.to_string())?;let got=stream(root,zip.by_name(name).map_err(|e|e.to_string())?,&mut f,m.blobs[name].bytes)?;if !same(&got,&m.blobs[name]){return Err("Backup changed during restoration".into())}f.sync_all().map_err(|e|e.to_string())?;check(root)?;fs::rename(&tmp,target).map_err(|e|e.to_string())?;File::open(target.parent().ok_or("Invalid restore destination")?).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();let _=fs::remove_file(tmp);result?;
  }
  check(root)?;match kind{"files"=>crate::content::backup::commit(root,&m.records,&m.blobs)?,"annotation"=>crate::drawing::backup_commit(root,data,&m.records,&m.blobs)?,"rss"=>crate::feeds::backup_commit(root,data,&m.records)?,"reader"=>crate::reader_backup::commit(root,data,&m.records)?,"browser"=>crate::browser::backup_commit(root,data,&m.records)?,"youtube"=>crate::videos::backup_commit(root,data,&m.records)?,"walkie"=>crate::document_backup::walkie_commit(root,data,&m.records)?,_=>crate::document_backup::commit(root,&m.records,&m.blobs)?}
 }
 Ok(json!({"items":p.new+p.kept,"new":p.new,"kept":p.kept,"bytes":m.blobs.values().map(|b|b.bytes).sum::<u64>(),"sha256":digest}))
}

#[cfg(test)]mod tests{
 use super::*;
 fn data(root:&Path)->PathBuf{let p=root.join("annotation");fs::create_dir_all(&p).unwrap();p}
 fn call(root:&Path,data:&Path,kind:&str,operation:&str,sha:&str)->Result<Value>{dispatch(root,data,&json!({"kind":kind,"operation":operation,"sha256":sha}))}
 fn transfer(a:&Path,b:&Path){fs::copy(a.join("library-backup-export.zip"),b.join("library-backup-import.zip")).unwrap();}
 fn folder(r:&Path,parent:&str,name:&str)->String{crate::content::service(r,"library.folder",&json!({"parent":parent,"name":name})).unwrap()["id"].as_str().unwrap().into()}
 fn file(r:&Path,parent:&str,name:&str,body:&str)->String{fs::write(r.join("content-import.tmp"),body).unwrap();crate::content::import_staged(r,parent,name,"text/plain","user","User",false).unwrap()["id"].as_str().unwrap().into()}
 fn rewrite(root:&Path,edit:impl FnOnce(&mut Manifest),extra:Option<&str>){
  let p=root.join("library-backup-import.zip");let mut input=ZipArchive::new(File::open(&p).unwrap()).unwrap();let mut items=BTreeMap::new();for i in 0..input.len(){let mut e=input.by_index(i).unwrap();let mut b=Vec::new();e.read_to_end(&mut b).unwrap();items.insert(e.name().to_string(),b);}
  let mut m:Manifest=serde_json::from_slice(&items["manifest.json"]).unwrap();edit(&mut m);items.insert("manifest.json".into(),serde_json::to_vec(&m).unwrap());drop(input);
  if let Some(extra)=extra{items.insert(extra.into(),b"bad".to_vec());}
  let mut output=ZipWriter::new(File::create(p).unwrap());for (name,b) in items{output.start_file(name,SimpleFileOptions::default().compression_method(CompressionMethod::Stored)).unwrap();output.write_all(&b).unwrap();}output.finish().unwrap();
 }
 #[test]fn media_above_the_old_256_mib_limit_fits_the_existing_512_mib_archive(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let r=a.path();let staged=r.join("content-import.tmp");let f=File::create(&staged).unwrap();f.set_len(257*1024*1024).unwrap();drop(f);
  let saved=crate::content::import_staged(r,"video","Long recording.mp4","video/mp4","user","User",false).unwrap();let id=saved["id"].as_str().unwrap();let ad=data(r);call(r,&ad,"files","export","").unwrap();transfer(r,b.path());let bd=data(b.path());let review=call(b.path(),&bd,"files","inspect","").unwrap();call(b.path(),&bd,"files","restore",review["sha256"].as_str().unwrap()).unwrap();assert_eq!(fs::metadata(b.path().join("onklav-content/objects").join(id)).unwrap().len(),257*1024*1024);
 }
 #[test]fn files_preserve_tree_bytes_and_existing_ids_exclude_trash_notes_and_packages(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());let dir=folder(a.path(),"documents","Work");let id=file(a.path(),&dir,"hello.txt","portable bytes 🐈");let trash=file(a.path(),"images","discard.txt","not exported");crate::content::service(a.path(),"library.trash",&json!({"id":trash})).unwrap();
  crate::content::note_save(a.path(),&crate::Note{id:"a".repeat(32),title:"Note separate".into(),text:"not in Files backup".into(),updated:1}).unwrap();
  assert_eq!(call(a.path(),&ad,"files","export","").unwrap()["items"],2);transfer(a.path(),b.path());
  let review=call(b.path(),&bd,"files","inspect","").unwrap();assert_eq!(review["new"],2);let sha=review["sha256"].as_str().unwrap();assert!(call(b.path(),&bd,"files","restore","wrong").is_err());call(b.path(),&bd,"files","restore",sha).unwrap();
  let path=crate::content::resolve(b.path(),&id).unwrap()["path"].as_str().unwrap().to_string();assert_eq!(fs::read_to_string(&path).unwrap(),"portable bytes 🐈");assert_eq!(crate::content::service(b.path(),"library.list",&json!({"folder":dir})).unwrap()["total"],1);
  fs::write(&path,"keep current").unwrap();assert_eq!(call(b.path(),&bd,"files","restore",sha).unwrap()["kept"],2);assert_eq!(fs::read_to_string(&path).unwrap(),"keep current");
  crate::content::service(b.path(),"library.trash",&json!({"id":dir})).unwrap();assert_eq!(call(b.path(),&bd,"files","restore",sha).unwrap()["kept"],2);
 }
 #[test]fn files_invalid_graph_hash_extra_entry_and_cancel_never_publish_records(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());file(a.path(),"documents","a.txt","body");call(a.path(),&ad,"files","export","").unwrap();transfer(a.path(),b.path());
  rewrite(b.path(),|m|m.records[0]["parent"]=json!("system"),None);assert!(call(b.path(),&bd,"files","inspect","").is_err());
  transfer(a.path(),b.path());rewrite(b.path(),|m|m.blobs.values_mut().next().unwrap().sha256="0".repeat(64),None);assert!(call(b.path(),&bd,"files","inspect","").is_err());
  transfer(a.path(),b.path());rewrite(b.path(),|_|{},Some("../escape"));assert!(call(b.path(),&bd,"files","inspect","").is_err());
  transfer(a.path(),b.path());let r=call(b.path(),&bd,"files","inspect","").unwrap();fs::write(b.path().join("library-backup.cancel"),"").unwrap();assert!(call(b.path(),&bd,"files","restore",r["sha256"].as_str().unwrap()).is_err());
  assert_eq!(crate::content::service(b.path(),"library.list",&json!({"folder":"documents"})).unwrap()["total"],1); // only built-in Notes
 }
 #[test]fn annotated_images_remain_editable_with_history_and_matching_dimensions(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());let d=crate::drawing::import(&ad,&json!({"image":crate::drawing::encode(include_bytes!("../../docs/fixtures/annotation-base.jpg"))})).unwrap();let id=d["id"].as_str().unwrap();
  crate::drawing::service(&ad,"drawing.edit",&json!({"id":id,"revision":0,"op":"stroke","mark":{"tool":"pen","color":4294901760u32,"width":4,"x":0,"y":0,"points":[[1,2],[20,30]]}})).unwrap();
  call(a.path(),&ad,"annotation","export","").unwrap();transfer(a.path(),b.path());let review=call(b.path(),&bd,"annotation","inspect","").unwrap();let sha=review["sha256"].as_str().unwrap();fs::copy(ad.join(format!("drawing-{id}.jpg")),bd.join(format!("drawing-{id}.jpg"))).unwrap();call(b.path(),&bd,"annotation","restore",sha).unwrap();
  assert_eq!(crate::drawing::snapshot(&bd,id,1,true).unwrap()["count"],1);crate::drawing::service(&bd,"drawing.undo",&json!({"id":id,"revision":1})).unwrap();assert_eq!(call(b.path(),&bd,"annotation","restore",sha).unwrap()["kept"],1);assert_eq!(crate::drawing::snapshot(&bd,id,2,false).unwrap()["count"],0);
  let c=tempfile::tempdir().unwrap();let cd=data(c.path());transfer(a.path(),c.path());rewrite(c.path(),|m|m.records[0]["width"]=json!(121),None);assert!(call(c.path(),&cd,"annotation","inspect","").is_err());assert_eq!(fs::read_dir(cd).unwrap().count(),0);
 }
 #[test]fn conflicting_unpublished_files_and_trashed_parents_fail_before_publication(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());let dir=folder(a.path(),"documents","Work");let id=file(a.path(),&dir,"a.txt","body");call(a.path(),&ad,"files","export","").unwrap();transfer(a.path(),b.path());
  let objects=crate::content::base(b.path()).unwrap().join("objects");fs::create_dir_all(&objects).unwrap();fs::write(objects.join(&id),"unrelated remnant").unwrap();assert!(call(b.path(),&bd,"files","inspect","").is_err());assert_eq!(fs::read_to_string(objects.join(&id)).unwrap(),"unrelated remnant");
  fs::write(objects.join(&id),"body").unwrap();let review=call(b.path(),&bd,"files","inspect","").unwrap();call(b.path(),&bd,"files","restore",review["sha256"].as_str().unwrap()).unwrap();
  crate::content::service(b.path(),"library.trash",&json!({"id":dir})).unwrap();let new=file(a.path(),&dir,"new.txt","new");call(a.path(),&ad,"files","export","").unwrap();transfer(a.path(),b.path());assert!(call(b.path(),&bd,"files","inspect","").is_err());assert!(!objects.join(new).exists());
 }

 #[test]fn duplicate_maximum_length_names_cannot_poison_the_files_index(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());let name="x".repeat(180);file(a.path(),"documents",&name,"incoming");file(b.path(),"documents",&name,"current");
  call(a.path(),&ad,"files","export","").unwrap();transfer(a.path(),b.path());let review=call(b.path(),&bd,"files","inspect","").unwrap();call(b.path(),&bd,"files","restore",review["sha256"].as_str().unwrap()).unwrap();
  let rows=crate::content::service(b.path(),"library.list",&json!({"folder":"documents"})).unwrap();assert_eq!(rows["total"],3);
  assert!(rows["items"].as_array().unwrap().iter().all(|e|e["name"].as_str().unwrap().encode_utf16().count()<=180));
 }

 #[test]fn rss_backup_restores_offline_state_preserves_current_settings_and_is_repeatable(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());
  crate::feeds::adopt_own(&ad,"[]").unwrap();let args=json!({"url":"https://example.org/rss","add":true,"name":"Original","category":"Technology"});
  crate::feeds::commit(&ad,&args,&json!({"etag":"private-http-validator","items":[{"url":"https://example.org/story","title":"Offline story","body":"Offline body 🐈","published":10}]})).unwrap();
  let id=crate::feeds::key("https://example.org/story");crate::feeds::service(&ad,"feeds.bookmark",&json!({"id":id})).unwrap();crate::feeds::service(&ad,"feeds.article",&json!({"id":id})).unwrap();
  crate::feeds::service(&ad,"feeds.settings",&json!({"interval":60,"wifi":true,"summaries":true,"unread":false,"oldest":false})).unwrap();
  call(a.path(),&ad,"rss","export","").unwrap();transfer(a.path(),b.path());let review=call(b.path(),&bd,"rss","inspect","").unwrap();assert_eq!(review["new"],3);let sha=review["sha256"].as_str().unwrap();call(b.path(),&bd,"rss","restore",sha).unwrap();
  let meta=crate::feeds::service(&bd,"feeds.state",&json!({})).unwrap();assert_eq!(meta["bookmarks"],1);assert_eq!(meta["settings"]["interval"],60);assert_eq!(meta["feeds"][0]["checked"],0);assert!(!fs::read_to_string(bd.join("feeds.json")).unwrap().contains("private-http-validator"));
  assert_eq!(crate::feeds::service(&bd,"feeds.query",&json!({"bookmarks":true})).unwrap()["items"][0]["read"],true);
  crate::feeds::service(&bd,"feeds.settings",&json!({"interval":0,"wifi":true,"summaries":false,"unread":false,"oldest":true})).unwrap();
  crate::feeds::service(&bd,"feeds.edit",&json!({"id":crate::feeds::key("https://example.org/rss"),"name":"Keep local name","category":"My Feeds","enabled":false})).unwrap();
  assert_eq!(call(b.path(),&bd,"rss","restore",sha).unwrap()["kept"],3);let current=crate::feeds::service(&bd,"feeds.state",&json!({})).unwrap();assert_eq!(current["settings"]["interval"],0);assert_eq!(current["feeds"][0]["name"],"Keep local name");
 }
 #[test]fn rss_backup_rejects_invalid_sources_and_overfull_merges_without_changing_data(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());crate::feeds::adopt_own(&ad,"[\"https://example.org/new\"]").unwrap();call(a.path(),&ad,"rss","export","").unwrap();transfer(a.path(),b.path());
  rewrite(b.path(),|m|m.records["feeds"][0]["url"]=json!("file:///private"),None);assert!(call(b.path(),&bd,"rss","inspect","").is_err());assert!(!bd.join("feeds.json").exists());
  let urls:Vec<_>=(0..32).map(|n|format!("https://example.org/{n}")).collect();crate::feeds::adopt_own(&bd,&serde_json::to_string(&urls).unwrap()).unwrap();let before=fs::read(bd.join("feeds.json")).unwrap();transfer(a.path(),b.path());assert!(call(b.path(),&bd,"rss","inspect","").is_err());assert_eq!(before,fs::read(bd.join("feeds.json")).unwrap());
 }
 #[test]fn reader_backup_uses_one_accessible_draft_keeps_cleared_text_and_repairs_interrupted_pointer(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());let n=crate::Note{id:"b".repeat(32),title:"Draft".into(),text:"Read me 🐈".into(),updated:12};crate::save_note(&ad,&n).unwrap();
  call(a.path(),&ad,"reader","export","").unwrap();transfer(a.path(),b.path());let review=call(b.path(),&bd,"reader","inspect","").unwrap();let sha=review["sha256"].as_str().unwrap();
  crate::sdk::atomic(&bd.join("last.json"),"a".repeat(32).as_bytes()).unwrap();crate::save_note(&bd,&n).unwrap(); // interrupted file-before-pointer commit
  call(b.path(),&bd,"reader","restore",sha).unwrap();assert_eq!(fs::read_to_string(bd.join("last.json")).unwrap(),n.id);
  let cleared=crate::Note{text:String::new(),updated:13,..n};crate::save_note(&bd,&cleared).unwrap();assert_eq!(call(b.path(),&bd,"reader","restore",sha).unwrap()["kept"],1);assert_eq!(crate::read_note(&bd,&cleared.id).unwrap().text,"");
  let c=tempfile::tempdir().unwrap();let cd=data(c.path());transfer(a.path(),c.path());rewrite(c.path(),|m|m.records["draft"]["text"]=json!("🐈".repeat(25001)),None);assert!(call(c.path(),&cd,"reader","inspect","").is_err());assert!(crate::list_notes(&cd).unwrap().is_empty());
 }

 #[test]fn browser_backup_preserves_tabs_settings_and_current_entries_with_atomic_limit_checks(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());
  crate::browser::service(&ad,"browser.navigate",&json!({"address":"https://example.org/saved"})).unwrap();crate::browser::service(&ad,"browser.bookmark",&json!({})).unwrap();
  crate::browser::service(&ad,"browser.settings",&json!({"home":"https://example.org/home","newtab":"https://example.org/new","engine":"Bing"})).unwrap();
  call(a.path(),&ad,"browser","export","").unwrap();transfer(a.path(),b.path());let r=call(b.path(),&bd,"browser","inspect","").unwrap();assert_eq!(r["new"],2);let sha=r["sha256"].as_str().unwrap();call(b.path(),&bd,"browser","restore",sha).unwrap();
  let state=crate::browser::service(&bd,"browser.state",&json!({})).unwrap();assert_eq!(state["tabs"][0]["url"],"https://example.org/saved");assert_eq!(state["engine"],"Bing");
  crate::browser::service(&bd,"browser.navigate",&json!({"address":"https://example.org/current"})).unwrap();crate::browser::service(&bd,"browser.settings",&json!({"home":"https://example.org/local","newtab":"https://example.org/local","engine":"Google"})).unwrap();
  assert_eq!(call(b.path(),&bd,"browser","restore",sha).unwrap()["kept"],2);let current=crate::browser::service(&bd,"browser.state",&json!({})).unwrap();assert_eq!(current["engine"],"Google");assert_eq!(current["tabs"][0]["url"],"https://example.org/current");
  let c=tempfile::tempdir().unwrap();let cd=data(c.path());for _ in 0..7{crate::browser::service(&cd,"browser.new",&json!({})).unwrap();}let before=fs::read(cd.join("browser.json")).unwrap();transfer(a.path(),c.path());assert!(call(c.path(),&cd,"browser","inspect","").is_err());assert_eq!(before,fs::read(cd.join("browser.json")).unwrap());
  transfer(a.path(),c.path());rewrite(c.path(),|m|m.records["tabs"][0]["url"]=json!("javascript:bad"),None);assert!(call(c.path(),&cd,"browser","inspect","").is_err());
 }
 #[test]fn youtube_backup_merges_history_and_bookmarks_without_copying_viewer_or_accounts(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let ad=data(a.path());let bd=data(b.path());let args=json!({"url":"https://youtu.be/AbC_def-123","title":"Saved title"});crate::videos::service(&ad,"videos.visit",&args).unwrap();crate::videos::service(&ad,"videos.bookmark",&args).unwrap();
  let exported=crate::videos::backup_export(&ad).unwrap();assert!(exported.get("viewer").is_none());let viewer=crate::videos::service(&bd,"videos.bookmark",&json!({"url":"https://youtube.com/","title":"Home"})).unwrap()["viewer"].clone();
  call(a.path(),&ad,"youtube","export","").unwrap();transfer(a.path(),b.path());let r=call(b.path(),&bd,"youtube","inspect","").unwrap();assert_eq!(r["new"],2);let sha=r["sha256"].as_str().unwrap();call(b.path(),&bd,"youtube","restore",sha).unwrap();assert_eq!(crate::videos::service(&bd,"videos.state",&json!({})).unwrap()["viewer"],viewer);
  crate::videos::service(&bd,"videos.visit",&json!({"url":"https://youtu.be/AbC_def-123","title":"Local title"})).unwrap();assert_eq!(call(b.path(),&bd,"youtube","restore",sha).unwrap()["kept"],2);assert_eq!(crate::videos::service(&bd,"videos.query",&json!({"history":true})).unwrap()["items"][0]["title"],"Local title");
  transfer(a.path(),b.path());rewrite(b.path(),|m|m.records["bookmarks"][0]["url"]=json!("https://youtube.com/signin"),None);let before=fs::read(bd.join("videos.json")).unwrap();assert!(call(b.path(),&bd,"youtube","inspect","").is_err());assert_eq!(before,fs::read(bd.join("videos.json")).unwrap());
  transfer(a.path(),b.path());rewrite(b.path(),|m|{let row=m.records["history"][0].clone();m.records["history"].as_array_mut().unwrap().push(row);},None);assert!(call(b.path(),&bd,"youtube","inspect","").is_err());
 }

}
