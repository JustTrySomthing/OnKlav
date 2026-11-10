pub mod content;
mod library_backup;
mod document_backup;
mod reader_backup;
mod walkie_carrier;
pub mod walkie_session;
pub mod walkie_clips;
pub mod walkie_store;
pub mod walkie;
pub mod drawing;
pub mod videos;
pub mod browser;
pub mod feeds;
mod messaging;
pub mod sdk;
mod sdk_collections;
mod sdk_storage;
mod sdk_http;
mod sdk_accounts;
mod sdk_tasks;
mod sdk_calendar;
mod group_wire;
mod mms;
mod history_backup;
mod history_import;
mod history_plan;
mod history_journal;
mod history_apply;
mod history_recovery;
mod history_completed;
mod history_resume;
use jni::{objects::{JClass, JString}, sys::jstring, JNIEnv};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{collections::HashSet, fs::{self, File, OpenOptions}, io::{Read, Write}, path::{Path, PathBuf}, time::{SystemTime, UNIX_EPOCH}};

const MAX_TEXT: usize = 100_000;
const MAX_DOC: u64 = 1_000_000;
const MAX_NOTES: usize = 2_000;
type Result<T> = std::result::Result<T, String>;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Note { pub id: String, pub title: String, pub text: String, pub updated: u64 }

/// Android gives the native core its own private files directory, never a package-supplied root.
fn folder(root: &Path) -> Result<PathBuf> {
    let p = root.join("notes");
    fs::create_dir_all(&p).map_err(|e| e.to_string())?;
    if fs::symlink_metadata(&p).map_err(|e| e.to_string())?.file_type().is_symlink() { return Err("Invalid private storage".into()); }
    Ok(p)
}
fn valid_id(id: &str) -> bool { id.len() == 32 && id.bytes().all(|x| x.is_ascii_hexdigit() && !x.is_ascii_uppercase()) }
fn location(root: &Path, id: &str) -> Result<PathBuf> {
    if !valid_id(id) { return Err("Invalid note ID".into()); }
    Ok(folder(root)?.join(format!("{id}.json")))
}
/// Preserve Unicode scalars, but count the same UTF-16 units used by Android text fields.
pub fn plain(s: &str, limit: usize) -> String {
    let mut count = 0;
    s.chars().filter(|c| !c.is_control() || *c == '\n' || *c == '\t').take_while(|c| {
        count += c.len_utf16(); count <= limit
    }).collect()
}
fn validate(n: &Note) -> Result<()> {
    if !valid_id(&n.id) || n.title.encode_utf16().count() > 80 || n.text.encode_utf16().count() > MAX_TEXT { return Err("Note exceeds its limits".into()); }
    if plain(&n.title, 80) != n.title || plain(&n.text, MAX_TEXT) != n.text { return Err("Unsupported control character".into()); }
    Ok(())
}
fn read_note(root: &Path, id: &str) -> Result<Note> {
    let path = location(root, id)?;
    let meta = fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
    if !meta.is_file() || meta.len() > MAX_DOC { return Err("Invalid note file".into()); }
    let n: Note = serde_json::from_slice(&fs::read(path).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    validate(&n)?; if n.id != id { return Err("Note identity mismatch".into()); } Ok(n)
}
fn list_notes(root: &Path) -> Result<Vec<Note>> {
    let mut items = Vec::new();
    let entries = fs::read_dir(folder(root)?).map_err(|e| e.to_string())?;
    for entry in entries {
        let e = entry.map_err(|e| e.to_string())?;
        let name = e.file_name().to_string_lossy().to_string();
        if !name.ends_with(".json") { continue; }
        if items.len() >= MAX_NOTES { return Err("Too many saved notes".into()); }
        // Do not turn an unreadable file into a silently successful empty library.
        items.push(read_note(root, name.trim_end_matches(".json"))?);
    }
    items.sort_by(|a,b| b.updated.cmp(&a.updated).then(a.id.cmp(&b.id))); Ok(items)
}
fn save_note(root: &Path, n: &Note) -> Result<()> {
    validate(n)?;
    let path = location(root, &n.id)?;
    if !path.exists() && list_notes(root)?.len() >= MAX_NOTES { return Err("Saved-note limit reached".into()); }
    let tmp = path.with_extension(format!("{}.tmp", uuid::Uuid::new_v4().simple()));
    let result = (|| {
        let mut file = OpenOptions::new().write(true).create_new(true).open(&tmp).map_err(|e| e.to_string())?;
        file.write_all(&serde_json::to_vec(n).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
        file.sync_all().map_err(|e| e.to_string())?;
        fs::rename(&tmp, &path).map_err(|e| e.to_string())?;
        File::open(folder(root)?).and_then(|f| f.sync_all()).map_err(|e| e.to_string())?;
        Ok(())
    })();
    if result.is_err() { let _ = fs::remove_file(tmp); } result
}
pub fn safe_archive_path(s: &str, directory: bool) -> bool {
    let p = if directory { s.strip_suffix('/').unwrap_or(s) } else { s };
    !p.is_empty() && p.len() <= 240 && !p.starts_with('/') && !p.contains(['\\', ':', '\0']) && p.split('/').all(|s| !s.is_empty() && s != "." && s != "..")
}

/// Inspection is deliberately separate from trust, installation and execution.
fn inspect_archive(path: &Path) -> Result<Value> {
    let f = File::open(path).map_err(|e| e.to_string())?;
    if f.metadata().map_err(|e| e.to_string())?.len() > 64*1024*1024 { return Err("Archive is too large".into()); }
    let mut archive = zip::ZipArchive::new(f).map_err(|e| e.to_string())?;
    if archive.len() > 256 { return Err("Too many archive entries".into()); }
    let mut names = HashSet::new(); let mut files = HashSet::new(); let mut total = 0u64; let mut manifest = None;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| e.to_string())?;
        let name = entry.name().to_string();
        if !safe_archive_path(&name, entry.is_dir()) || !names.insert(name.trim_end_matches('/').to_string()) { return Err("Unsafe or duplicate archive path".into()); }
        if entry.unix_mode().is_some_and(|m| m & 0o170000 == 0o120000) { return Err("Symbolic links are not accepted".into()); }
        if entry.is_dir() { continue; }
        files.insert(name.clone());
        if entry.size() > 32*1024*1024 { return Err("Oversize archive entry".into()); }
        let mut buf = [0u8;8192]; let mut read = 0u64; let mut captured = Vec::new();
        loop {
            let n = entry.read(&mut buf).map_err(|e| e.to_string())?; if n == 0 { break; }
            read += n as u64; total += n as u64;
            if read > 32*1024*1024 || total > 64*1024*1024 { return Err("Expanded archive exceeds limits".into()); }
            if name == "manifest.json" {
                if read > 16384 { return Err("Manifest exceeds limits".into()); }
                captured.extend_from_slice(&buf[..n]);
            }
        }
        if name == "manifest.json" { manifest = Some(captured); }
    }
    let manifest: Value = serde_json::from_slice(&manifest.ok_or("Missing manifest.json")?).map_err(|e| e.to_string())?;
    if manifest["schema"] != "onklav.package.v1" || !matches!(manifest["kind"].as_str(), Some("app"|"game")) { return Err("Unsupported package format".into()); }
    let id = manifest["id"].as_str().ok_or("Missing package ID")?;
    if id.len()>200 || id.split('.').count()<2 || !id.split('.').all(|p| !p.is_empty() && p.as_bytes()[0].is_ascii_lowercase() && p.bytes().all(|c|c.is_ascii_lowercase()||c.is_ascii_digit())) { return Err("Invalid package ID".into()); }
    for field in ["name","version"] {
        let text = manifest[field].as_str().ok_or("Missing package metadata")?;
        if text.trim().is_empty()||text.encode_utf16().count()>200 {return Err("Invalid package metadata".into());}
    }
    if manifest["kind"]=="game" && !matches!(manifest["profile"].as_str(),Some("arcade"|"turn_based")) {return Err("Invalid game profile".into());}
    let entry = manifest["entry"].as_str().ok_or("Missing entry")?;
    if !safe_archive_path(entry, false) || !files.contains(entry) { return Err("Package entry is missing".into()); }
    let mut f = File::open(path).map_err(|e| e.to_string())?; let mut hash=Sha256::new(); let mut buf=[0;8192];
    loop { let n=f.read(&mut buf).map_err(|e|e.to_string())?; if n==0 {break;} hash.update(&buf[..n]); }
    Ok(json!({"manifest":manifest,"sha256":format!("{:x}",hash.finalize()),"entries":names.len(),"expanded_bytes":total,"signature_verified":false,"execution_allowed":false}))
}
pub fn dispatch(root: &Path, request: &str) -> Value {
    let result = (|| -> Result<Value> {
        if request.len() > 1_100_000 { return Err("Request exceeds limits".into()); }
        let req: Value = serde_json::from_str(request).map_err(|e| e.to_string())?;
        match req["op"].as_str().ok_or("Missing operation")? {
            "folder_vault" => content::folder_vault(root,&req),
            "notes_to_write" => content::migrate_notes_to_write(root,&req),
            "sdk" => sdk::dispatch(root,&req),
            "walkie_rules" => walkie::dispatch(&req),
            "group_wire" => group_wire::dispatch(&req),
            "mms" => mms::dispatch(root,&req),
            "history_backup" => history_backup::dispatch(root,&req),
            "history_import" => history_import::dispatch(root,&req),
            "history_recovery" => history_recovery::dispatch(root,&req),
            "history_journal" => history_journal::dispatch(root,&req),
            "chat" => messaging::dispatch(root,&req),
            "new" => Ok(json!(Note{id:uuid::Uuid::new_v4().simple().to_string(), title:String::new(), text:String::new(), updated:0})),
            "list" => { let query=req["query"].as_str().unwrap_or("").to_lowercase(); Ok(json!(list_notes(root)?.into_iter().filter(|n|n.title.to_lowercase().contains(&query)||n.text.to_lowercase().contains(&query)).collect::<Vec<_>>())) },
            "load" => Ok(json!(read_note(root,req["id"].as_str().ok_or("Missing ID")?)?)),
            "save" => { let mut note:Note=serde_json::from_value(req["note"].clone()).map_err(|e|e.to_string())?;note.updated=SystemTime::now().duration_since(UNIX_EPOCH).map_err(|e|e.to_string())?.as_millis() as u64;save_note(root,&note)?;Ok(json!(note)) },
            "delete" => { fs::remove_file(location(root,req["id"].as_str().ok_or("Missing ID")?)?).map_err(|e|e.to_string())?;Ok(json!(true)) },
            "plain" => Ok(json!(plain(req["text"].as_str().ok_or("Missing text")?,MAX_TEXT))),
            "inspect" => { let name=req["file"].as_str().ok_or("Missing filename")?; if name!="inspection.zip" {return Err("Invalid staging file".into());} inspect_archive(&root.join(name)) },
            _ => Err("Unsupported operation".into())
        }
    })();
    match result {Ok(v)=>json!({"ok":true,"value":v}),Err(e)=>json!({"ok":false,"error":e})}
}

#[no_mangle]
pub extern "system" fn Java_app_onklav_shared_NativeCore_dispatch(mut env: JNIEnv, _: JClass, root: JString, request: JString) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<String> {
        let root: String = env.get_string(&root).map_err(|e|e.to_string())?.into();
        let req: String = env.get_string(&request).map_err(|e|e.to_string())?.into();
        Ok(dispatch(Path::new(&root), &req).to_string())
    }));
    let text = match result { Ok(Ok(s))=>s, Ok(Err(e))=>json!({"ok":false,"error":e}).to_string(),Err(_)=>json!({"ok":false,"error":"Native operation failed"}).to_string() };
    match env.new_string(text) {Ok(s)=>s.into_raw(),Err(_)=>std::ptr::null_mut()}
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn unicode_limits() { assert_eq!(plain("a😀b",3),"a😀");assert_eq!(plain("a\0b\nc\td",100),"ab\nc\td"); }
    #[test] fn archive_paths() { for bad in ["../x","a//b","/x","C:/x","a\\b","a/./b",""] {assert!(!safe_archive_path(bad,false),"{bad}");}assert!(safe_archive_path("art/icon.webp",false));assert!(safe_archive_path("art/",true)); }
    #[test] fn storage_lifecycle_and_search() {
        let dir=tempfile::tempdir().unwrap();let root=dir.path();let n=dispatch(root,r#"{"op":"new"}"#)["value"].clone();
        let mut note:Note=serde_json::from_value(n).unwrap();note.title="Family".into();note.text="A saved 🐈 note".into();
        assert_eq!(dispatch(root,&json!({"op":"save","note":note}).to_string())["ok"],true);
        assert_eq!(dispatch(root,r#"{"op":"list","query":"SAVED"}"#)["value"].as_array().unwrap().len(),1);
        assert_eq!(read_note(root,&note.id).unwrap().text,note.text);
        assert_eq!(dispatch(root,&json!({"op":"delete","id":note.id}).to_string())["ok"],true);assert!(list_notes(root).unwrap().is_empty());
    }
    #[test] fn traversal_and_invalid_data_do_not_replace_notes() {
        let dir=tempfile::tempdir().unwrap();assert!(location(dir.path(),"../secrets").is_err());
        let note=Note{id:"a".repeat(32),title:"Good".into(),text:"Original".into(),updated:1};save_note(dir.path(),&note).unwrap();
        let mut bad=note.clone();bad.text="x".repeat(MAX_TEXT+1);assert!(save_note(dir.path(),&bad).is_err());assert_eq!(read_note(dir.path(),&note.id).unwrap().text,"Original");
    }
    #[test] fn broken_document_is_reported() { let dir=tempfile::tempdir().unwrap();let p=location(dir.path(),&"a".repeat(32)).unwrap();fs::write(p,b"invalid").unwrap();assert!(list_notes(dir.path()).is_err()); }
    fn package(root: &Path, manifest: &Value, extra: &[(&str,&[u8])]) {
        let file=File::create(root.join("inspection.zip")).unwrap();let mut zip=zip::ZipWriter::new(file);
        let options=zip::write::SimpleFileOptions::default();
        zip.start_file("manifest.json",options).unwrap();zip.write_all(manifest.to_string().as_bytes()).unwrap();
        for (name,data) in extra {zip.start_file(*name,options).unwrap();zip.write_all(data).unwrap();}zip.finish().unwrap();
    }
    fn manifest() -> Value {json!({"schema":"onklav.package.v1","id":"app.onklav.example","name":"Example","version":"1","kind":"game","profile":"arcade","entry":"main.gd"})}
    #[test] fn inspect_valid_archive_never_grants_execution() {let dir=tempfile::tempdir().unwrap();package(dir.path(),&manifest(),&[("main.gd",b"extends Node")]);let result=inspect_archive(&dir.path().join("inspection.zip")).unwrap();assert_eq!(result["signature_verified"],false);assert_eq!(result["execution_allowed"],false);assert_eq!(result["entries"],2);assert_eq!(result["sha256"].as_str().unwrap().len(),64);}
    #[test] fn invalid_archives_reject_paths_missing_entry_and_metadata() {
        let dir=tempfile::tempdir().unwrap();let mut j=manifest();package(dir.path(),&j,&[("../main.gd",b"bad")]);assert!(inspect_archive(&dir.path().join("inspection.zip")).is_err());
        package(dir.path(),&j,&[]);assert!(inspect_archive(&dir.path().join("inspection.zip")).is_err());
        j["id"]=json!("../invalid");package(dir.path(),&j,&[("main.gd",b"test")]);assert!(inspect_archive(&dir.path().join("inspection.zip")).is_err());
        j=manifest();j["profile"]=json!("unsupported");package(dir.path(),&j,&[("main.gd",b"test")]);assert!(inspect_archive(&dir.path().join("inspection.zip")).is_err());
    }
}

mod sdk_devices;

mod sdk_handoff;

mod epub_xml;
mod sdk_epub;
mod sdk_ebooks;

mod sdk_maps;

mod sdk_map_cache;

mod sdk_podcasts;

mod sdk_mime;

mod sdk_radio;

mod sdk_podcast_library;

mod sdk_places;
mod weather_cities;
