//! One-way Notes adoption. Commit the index only after every destination verifies.
//! Original note JSON is retained privately, outside the active content records.
use super::*;

fn verified_copy(path:&Path,value:&Value)->Result<()> {
    for candidate in [path.to_path_buf(),path.with_extension("json.bak")] {
        if candidate.exists() {
            let existing:Value=serde_json::from_slice(&crate::sdk::read_bounded(&candidate,8_000_000)?).map_err(|_|"Existing Write document needs recovery")?;
            if existing!=*value {return Err("An existing Write document differs. Originals were kept; migration needs recovery.".into())}
        }
    }
    if !path.exists(){crate::sdk::atomic(path,&serde_json::to_vec(value).map_err(|e|e.to_string())?)?;}
    let check:Value=serde_json::from_slice(&crate::sdk::read_bounded(path,8_000_000)?).map_err(|_|"Write copy could not be verified")?;
    if check!=*value{return Err("Write copy did not verify; original Notes were kept".into())}Ok(())
}

pub(super) fn migrate(root:&Path)->Result<Value>{
    let mut index=load(root)?;
    if index.notes_retired&&!index.entries.values().any(|e|e.note){return Ok(json!({"complete":true,"converted":0}))}
    // Adopt the oldest host-owned Notes store directly. No retired guest needs
    // installing or broader grants just to migrate the user's own documents.
    if !index.notes_retired{
        for note in crate::list_notes(root)?{
            if index.entries.contains_key(&note.id){continue}
            crate::save_note(&base(root)?,&note)?;check_note_copy(root,&base(root)?,&note.id)?;
            let name=unique(&index,"notes",&note_name(&note),&note.id);
            index.entries.insert(note.id.clone(),Entry{id:note.id.clone(),parent:"notes".into(),name,mime:"text/plain".into(),owner:"app.onklav.notes".into(),folder:false,note:true,bytes:(note.title.len()+note.text.len())as u64,from:None,vault:None});
        }
        save(root,&index)?;
    }
    let source:Vec<Entry>=index.entries.values().filter(|e|e.note).cloned().collect();
    let write_folder=app_folder(&mut index,"documents","app.onklav.write","Write");
    let private=directory(directory(root.join("write"))?.join("documents"))?;
    let mut converted=0;
    for old in source {
        let note=crate::read_note(&base(root)?,&old.id)?;
        let uuid=uuid::Uuid::parse_str(&old.id).map_err(|_|"Invalid legacy Note identity")?.to_string();
        let title=if note.title.trim().is_empty(){"Untitled"}else{&note.title};
        let value=json!({"schema":"onklav.write.v1","id":uuid,"title":title,"text":note.text,"marks":[],"modified":note.updated});
        let in_trash=trashed(&index,&old.id)?;
        if !in_trash{verified_copy(&private.join(format!("{uuid}.json")),&value)?;}
        verified_copy(&object(root,&old.id)?,&value)?;
        let name=unique(&index,if in_trash{"trash"}else{&write_folder},&format!("{}.write",note_name(&note).trim_end_matches(".txt")),&old.id);
        let size=serde_json::to_vec(&value).map_err(|e|e.to_string())?.len() as u64;
        index.entries.insert(old.id.clone(),Entry{id:old.id,parent:if in_trash{"trash".into()}else{write_folder.clone()},name,mime:"application/vnd.onklav.write+json".into(),owner:"app.onklav.write".into(),folder:false,note:false,bytes:size,from:if in_trash{Some(write_folder.clone())}else{None},vault:None});
        converted+=1;
    }
    if !index.entries.values().any(|e|e.parent=="notes"){index.entries.remove("notes");}
    index.notes_retired=true;
    save(root,&index)?;
    Ok(json!({"complete":true,"converted":converted,"folder":write_folder}))
}

pub(super) fn linked(root:&Path,args:&Value)->Result<Value>{
 let mut index=load(root)?;let raw=args["id"].as_str().ok_or("Missing document identity")?;
 let id=if args["action"]=="sync"{uuid::Uuid::parse_str(raw).map_err(|_|"Invalid Write identity")?.simple().to_string()}else{raw.to_owned()};
 if !(args["action"]=="sync"&&vault::finishing_write(root,&index,&id)){vault::guard(root,&index,&id)?;}
 let Some(e)=index.entries.get(&id).filter(|e|!e.folder&&!e.note&&e.mime=="application/vnd.onklav.write+json").cloned()else{if args["action"]=="sync"{return Ok(json!({"linked":false}))}return Err("This is not a linked Write document".into())};
 if trashed(&index,&id)?{return Err("Restore this document from Trash first".into())}
 let uuid=uuid::Uuid::parse_str(&id).map_err(|_|"Invalid Write identity")?.to_string();let private=directory(directory(root.join("write"))?.join("documents"))?.join(format!("{uuid}.json"));
 if args["action"]=="sync"{
  let bytes=crate::sdk::read_bounded(&private,8_000_000)?;let value:Value=serde_json::from_slice(&bytes).map_err(|_|"Invalid Write document")?;
  if value["schema"]!="onklav.write.v1"||value["id"]!=uuid{return Err("Write identity changed".into())}
  crate::sdk::atomic(&object(root,&id)?,&bytes)?;index.entries.get_mut(&id).unwrap().bytes=bytes.len() as u64;save(root,&index)?;
 }else{
  let mut value:Value=serde_json::from_slice(&crate::sdk::read_bounded(&object(root,&e.id)?,8_000_000)?).map_err(|_|"Invalid Write document")?;
  if value["schema"]!="onklav.write.v1"||!value["text"].is_string()||!value["marks"].is_array(){return Err("Invalid Write document".into())}
  value["id"]=json!(uuid);
  // A copied/restored .write file receives its own library identity. Never
  // substitute an unrelated private document selected by untrusted JSON.
  if private.exists(){let local:Value=serde_json::from_slice(&crate::sdk::read_bounded(&private,8_000_000)?).map_err(|_|"Local Write document needs recovery")?;if local!=value{return Err("Write has different local changes. Open Write and save them before reopening this file, or import the file as a copy.".into())}}
  else{verified_copy(&private,&value)?;}
 }

 Ok(json!({"id":uuid,"linked":true}))
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn notes_conversion_is_repeatable_and_keeps_originals_and_trash(){
  let dir=tempfile::tempdir().unwrap();let root=dir.path();
  let a=Note{id:uuid::Uuid::new_v4().simple().to_string(),title:"My note".into(),text:"Keep every word 🐈\nSecond line".into(),updated:123};
  let b=Note{id:uuid::Uuid::new_v4().simple().to_string(),title:"Deleted".into(),text:"Recovery".into(),updated:124};
  note_save(root,&a).unwrap();note_save(root,&b).unwrap();service(root,"library.trash",&json!({"id":b.id})).unwrap();
  let before=fs::read(crate::location(&base(root).unwrap(),&a.id).unwrap()).unwrap();
  let result=migrate(root).unwrap();assert_eq!(result["converted"],2);assert!(load(root).unwrap().notes_retired);
  assert_eq!(before,fs::read(crate::location(&base(root).unwrap(),&a.id).unwrap()).unwrap());
  let index=load(root).unwrap();assert!(!index.entries[&a.id].note);assert!(trashed(&index,&b.id).unwrap());assert!(!index.entries.contains_key("notes"));
  let uuid=uuid::Uuid::parse_str(&a.id).unwrap().to_string();let copy:Value=serde_json::from_slice(&fs::read(root.join(format!("write/documents/{uuid}.json"))).unwrap()).unwrap();assert_eq!(copy["text"],a.text);
  assert!(!root.join(format!("write/documents/{}.json",uuid::Uuid::parse_str(&b.id).unwrap())).exists());
  assert_eq!(migrate(root).unwrap()["converted"],0);assert!(note_list(root).unwrap().is_empty());assert!(note_save(root,&a).is_err());
 }
 #[test]fn conflict_keeps_index_and_originals_retryable(){
  let dir=tempfile::tempdir().unwrap();let root=dir.path();let note=Note{id:uuid::Uuid::new_v4().simple().to_string(),title:"Original".into(),text:"Original text".into(),updated:2};note_save(root,&note).unwrap();
  let private=directory(directory(root.join("write")).unwrap().join("documents")).unwrap();let id=uuid::Uuid::parse_str(&note.id).unwrap();fs::write(private.join(format!("{id}.json")),b"{\"text\":\"User edited\"}").unwrap();
  assert!(migrate(root).is_err());let index=load(root).unwrap();assert!(!index.notes_retired);assert!(index.entries[&note.id].note);assert_eq!(note_read(root,&note.id).unwrap().text,note.text);
 }
}
