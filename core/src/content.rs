//! Host-owned virtual content tree. Guests receive opaque IDs, never OS paths.
#[path="content_vault.rs"] mod vault;
pub fn folder_vault(root:&Path,args:&Value)->Result<Value>{vault::host(root,args)}
#[path="content_write_migration.rs"] mod write_migration;
pub fn migrate_notes_to_write(root:&Path,args:&Value)->Result<Value>{match args["action"].as_str().unwrap_or("migrate"){"status"=>notes_to_write_status(root),"linked"|"sync"=>write_migration::linked(root,args),_=>write_migration::migrate(root)}}
pub fn notes_to_write_status(root:&Path)->Result<Value>{let i=load(root)?;Ok(json!({"complete":i.notes_retired&&!i.entries.values().any(|e|e.note)}))}
#[path="content_backup.rs"] pub(crate) mod backup;
use crate::{Note,Result};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::{BTreeMap,BTreeSet},fs,io::{Read,Write},path::{Path,PathBuf}};
const MAX_ENTRIES:usize=10_000;
pub const MAX_FILE:u64=256*1024*1024;
const ROOTS:[(&str,&str);7]=[("downloads","Downloads"),("images","Images"),("documents","Documents"),("audio","Audio"),("video","Video"),("system","OnKlav System Files"),("trash","Trash")];
#[derive(Clone,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Entry {id:String,parent:String,name:String,mime:String,owner:String,folder:bool,note:bool,bytes:u64,#[serde(default)] from:Option<String>,#[serde(default,skip_serializing_if="Option::is_none")] vault:Option<Vec<String>>}
#[derive(Clone,Default,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Index {entries:BTreeMap<String,Entry>,#[serde(default)] notes_migrated:bool,#[serde(default)] notes_retired:bool,#[serde(default)] media_flattened:bool}
fn directory(p:PathBuf)->Result<PathBuf>{fs::create_dir_all(&p).map_err(|e|e.to_string())?;if !fs::symlink_metadata(&p).map_err(|e|e.to_string())?.is_dir(){return Err("Content storage is unavailable".into())}Ok(p)}
pub fn base(root:&Path)->Result<PathBuf>{directory(root.join("onklav-content"))}
fn object(root:&Path,id:&str)->Result<PathBuf>{if !crate::valid_id(id){return Err("Invalid file identity".into())}Ok(directory(base(root)?.join("objects"))?.join(id))}
fn valid_name(name:&str)->Result<()>{if name.trim()!=name||name.is_empty()||name.starts_with('.')||name.encode_utf16().count()>180||name.chars().any(|c|c.is_control()||"/\\:".contains(c)){return Err("Use a name without slashes, control characters or a leading dot".into())}Ok(())}
fn protected(id:&str)->bool{ROOTS.iter().any(|(r,_)|*r==id)||id=="notes"}
fn occupied(index:&Index,id:&str)->bool{index.entries.contains_key(id)||index.entries.values().any(|e|e.vault.as_ref().is_some_and(|ids|ids.iter().any(|v|v==id)))}
fn count(index:&Index)->usize{let mut ids:BTreeSet<_>=index.entries.keys().cloned().collect();for e in index.entries.values(){if let Some(members)=&e.vault{ids.extend(members.iter().cloned());}}ids.len()}
fn save(root:&Path,index:&Index)->Result<()>{for e in index.entries.values(){trashed(index,&e.id)?;}if count(index)>MAX_ENTRIES{return Err("File limit reached".into())}crate::sdk::atomic(&base(root)?.join("index.json"),&serde_json::to_vec(index).map_err(|e|e.to_string())?)}
fn trashed(index:&Index,id:&str)->Result<bool>{let mut cursor=id;for _ in 0..64{if cursor=="trash"{return Ok(true)}if cursor.is_empty(){return Ok(false)}cursor=&index.entries.get(cursor).ok_or("Folder is unavailable")?.parent;}Err("Folder nesting is too deep".into())}
fn folder<'a>(index:&'a Index,id:&str)->Result<&'a Entry>{let e=index.entries.get(id).filter(|e|e.folder).ok_or("Choose a folder")?;if id=="system"||trashed(index,id)?{return Err("Choose an active content folder".into())}Ok(e)}
fn note_name(n:&Note)->String{let raw=crate::plain(n.title.trim(),80).chars().map(|c|if c.is_control()||"/\\:".contains(c){'_'}else{c}).collect::<String>();let title=raw.trim_start_matches('.').trim();format!("{}.txt",if title.is_empty(){"Untitled Note"}else{title})}
fn unique(index:&Index,parent:&str,name:&str,exclude:&str)->String{
 let occupied:std::collections::HashSet<String>=index.entries.values().filter(|e|e.id!=exclude&&e.parent==parent).map(|e|e.name.to_lowercase()).collect();
 let available=|name:&str|!occupied.contains(&name.to_lowercase());
 if available(name){return name.into()}
 // Count UTF-16 units like Android and never split a surrogate pair. Reserve
 // suffix space before shortening either part, including unusually long extensions.
 fn prefix(value:&str,limit:usize)->String{let mut units=0;value.chars().take_while(|c|{units+=c.len_utf16();units<=limit}).collect()}
 let (stem,ext)=name.rsplit_once('.').filter(|(s,_)|!s.is_empty()).map(|(s,e)|(s,format!(".{e}"))).unwrap_or((name,String::new()));
 for n in 2..=MAX_ENTRIES+2{
  let suffix=format!(" ({n})");let budget=180-suffix.len();
  // The first scalar can need two UTF-16 units. Truncating an extension
  // must not leave a trailing space that the next index load would reject.
  let first=stem.chars().next().map_or(1,char::len_utf16);
  let ext=prefix(&ext,budget-first).trim_end().to_string();
  let stem=prefix(stem,budget-ext.encode_utf16().count());
  let candidate=format!("{stem}{suffix}{ext}");
  if available(&candidate){return candidate}
 }
 // The bounded index has fewer entries than candidate names.
 unreachable!("Unique file name space exhausted")
}
fn load(root:&Path)->Result<Index>{
 let dir=base(root)?;let path=dir.join("index.json");let mut dirty=!path.exists();let mut index=if path.exists(){serde_json::from_slice::<Index>(&crate::sdk::read_bounded(&path,8*1024*1024)?).map_err(|_|"Files index needs recovery; existing content was kept")?}else{Index::default()};
 if count(&index)>MAX_ENTRIES{return Err("File limit reached".into())}
 for (id,name) in ROOTS{if !index.entries.contains_key(id){dirty=true;}index.entries.entry(id.into()).or_insert(Entry{id:id.into(),parent:String::new(),name:name.into(),mime:"inode/directory".into(),owner:"system".into(),folder:true,note:false,bytes:0,from:None,vault:None});}
 if index.entries["system"].name!="OnKlav System Files"{index.entries.get_mut("system").unwrap().name="OnKlav System Files".into();dirty=true;}
 if !index.notes_retired {if !index.entries.contains_key("notes"){dirty=true;}index.entries.entry("notes".into()).or_insert(Entry{id:"notes".into(),parent:"documents".into(),name:"Notes".into(),mime:"inode/directory".into(),owner:"app.onklav.notes".into(),folder:true,note:false,bytes:0,from:None,vault:None});}
 for (id,e) in &index.entries{vault::validate_entry(e)?;valid_name(&e.name)?;if e.id!=*id||(!protected(id)&&!crate::valid_id(id))||(!e.parent.is_empty()&&!index.entries.contains_key(&e.parent)){return Err("Files index needs recovery; existing content was kept".into())}trashed(&index,id)?;}
 if !index.notes_migrated{dirty=true;
  let legacy=root.join("app-sdk/data/app.onklav.notes");
  if legacy.exists(){for n in crate::list_notes(&legacy)?{if !crate::location(&dir,&n.id)?.exists(){crate::save_note(&dir,&n)?;check_note_copy(&legacy,&dir,&n.id)?;}}
   let last=legacy.join("last.json");if last.exists()&&!dir.join("last.json").exists(){crate::sdk::atomic(&dir.join("last.json"),&crate::sdk::read_bounded(&last,32)?)?;}}
  index.notes_migrated=true;
 }
 // Recover a note saved just before an index write failed. Never duplicate data.
 if !index.notes_retired {for item in fs::read_dir(crate::folder(&dir)?).map_err(|e|e.to_string())?{let item=item.map_err(|e|e.to_string())?;let name=item.file_name().to_string_lossy().into_owned();if let Some(id)=name.strip_suffix(".json"){if !index.entries.contains_key(id){let n=crate::read_note(&dir,id)?;let name=unique(&index,"notes",&note_name(&n),&n.id);index.entries.insert(n.id.clone(),Entry{id:n.id.clone(),parent:"notes".into(),name,mime:"text/plain".into(),owner:"app.onklav.notes".into(),folder:false,note:true,bytes:(n.title.len()+n.text.len())as u64,from:None,vault:None});dirty=true;}}}
 }
 if !index.media_flattened {
  let ids:Vec<String>=index.entries.values().filter(|e|!e.folder&&media_root(&e.mime).is_some()).map(|e|e.id.clone()).collect();
  for id in ids {let e=index.entries[&id].clone();if trashed(&index,&id)?{continue}let mut parent=e.parent.clone();let mut automatic=false;let mut guarded=false;
   for _ in 0..64 {if parent.is_empty(){break}let ancestor=&index.entries[&parent];if ancestor.vault.is_some()||(ancestor.owner=="user"&&!protected(&ancestor.id)){guarded=true;break}if ancestor.folder&&ancestor.owner!="user"&&matches!(ancestor.parent.as_str(),"images"|"audio"|"video"|"downloads"){automatic=true}parent=ancestor.parent.clone();}
   if automatic&&!guarded {let target=media_root(&e.mime).unwrap();let name=unique(&index,target,&e.name,&id);let item=index.entries.get_mut(&id).unwrap();item.parent=target.into();item.name=name;}
  }
  // Remove only now-empty automatic media containers; user folders stay intact.
  loop {let empty:Vec<String>=index.entries.values().filter(|e|e.folder&&!protected(&e.id)&&e.owner!="user"&&e.vault.is_none()&&matches!(e.parent.as_str(),"images"|"audio"|"video"|"downloads")&&!index.entries.values().any(|v|v.parent==e.id)).map(|e|e.id.clone()).collect();if empty.is_empty(){break}for id in empty{index.entries.remove(&id);}}
  index.media_flattened=true;dirty=true;
 }
 if dirty{save(root,&index)?;}Ok(index)
}

fn check_note_copy(old:&Path,new:&Path,id:&str)->Result<()>{let a=crate::read_note(old,id)?;let b=crate::read_note(new,id)?;if a.id!=b.id||a.title!=b.title||a.text!=b.text||a.updated!=b.updated{return Err("Note migration did not verify; originals are retained".into())}Ok(())}
pub fn notes_root(root:&Path)->Result<PathBuf>{load(root)?;base(root)}
pub fn note_list(root:&Path)->Result<Vec<Note>>{let index=load(root)?;let mut notes=Vec::new();for e in index.entries.values(){if e.note&&!trashed(&index,&e.id)?{notes.push(crate::read_note(&base(root)?,&e.id)?);}}notes.sort_by(|a,b|b.updated.cmp(&a.updated).then(a.id.cmp(&b.id)));Ok(notes)}
pub fn note_read(root:&Path,id:&str)->Result<Note>{let index=load(root)?;if index.notes_retired{return Err("Notes documents now open in Write".into())}if trashed(&index,id)?{return Err("This note is in Trash. Restore it in Files first.".into())}crate::read_note(&base(root)?,id)}
pub fn note_bytes(root:&Path,exclude_id:&str)->Result<u64>{let dir=base(root)?;let excluded=if exclude_id.is_empty(){None}else{Some(crate::location(&dir,exclude_id)?)};let mut total=0u64;for item in fs::read_dir(crate::folder(&dir)?).map_err(|e|e.to_string())?{let path=item.map_err(|e|e.to_string())?.path();if excluded.as_ref()==Some(&path){continue}let meta=fs::symlink_metadata(&path).map_err(|e|e.to_string())?;if !meta.is_file(){return Err("Invalid note storage entry".into())}total=total.checked_add(meta.len()).ok_or("Note storage limit reached")?;}Ok(total)}
pub fn note_save(root:&Path,n:&Note)->Result<()>{let mut index=load(root)?;if index.notes_retired{return Err("Notes was replaced by Write".into())}if index.entries.contains_key(&n.id)&&trashed(&index,&n.id)?{return Err("This note is in Trash. Restore it in Files first.".into())}
 crate::save_note(&base(root)?,n)?;let parent=index.entries.get(&n.id).map(|e|e.parent.clone()).unwrap_or("notes".into());let name=unique(&index,&parent,&note_name(n),&n.id);index.entries.insert(n.id.clone(),Entry{id:n.id.clone(),parent,name,mime:"text/plain".into(),owner:"app.onklav.notes".into(),folder:false,note:true,bytes:(n.title.len()+n.text.len())as u64,from:None,vault:None});save(root,&index)
}
pub fn delete_notes(root:&Path)->Result<()>{let mut index=load(root)?;let ids:Vec<String>=index.entries.values().filter(|e|e.note).map(|e|e.id.clone()).collect();for id in ids{let p=crate::location(&base(root)?,&id)?;if p.exists(){fs::remove_file(p).map_err(|e|e.to_string())?;}index.entries.remove(&id);}let last=base(root)?.join("last.json");if last.exists(){fs::remove_file(last).map_err(|e|e.to_string())?;}save(root,&index)}
fn virtual_location(index:&Index,id:&str)->Result<String>{let mut names=Vec::new();let mut cursor=id;for _ in 0..64{if cursor.is_empty(){names.reverse();return Ok(names.join(" › "))}let e=index.entries.get(cursor).ok_or("Folder is unavailable")?;names.push(e.name.clone());cursor=&e.parent;}Err("Folder nesting is too deep".into())}
fn row(root:&Path,index:&Index,e:&Entry)->Result<Value>{let location=virtual_location(index,&e.parent)?;let detail=if e.folder{"Folder".into()}else{format!("{} · {}\n{}",if e.note{"Note"}else{&e.mime},size(e.bytes),location)};Ok(json!({"id":e.id,"parent":e.parent,"name":e.name,"folder":e.folder,"note":e.note,"mime":e.mime,"bytes":e.bytes,"trash":trashed(index,&e.id)?,"protected":protected(&e.id),"location":location,"detail":detail,"encrypted":e.vault.is_some(),"locked":e.vault.is_some()&&!vault::readable(root,index,&e.id),"protectable":e.folder&&e.owner=="user"&&!protected(&e.id)}))}
fn size(bytes:u64)->String{if bytes>=1024*1024{format!("{:.1} MB",bytes as f64/(1024.*1024.))}else if bytes>=1024{format!("{:.1} KB",bytes as f64/1024.)}else{format!("{bytes} bytes")}}
fn s<'a>(args:&'a Value,key:&str)->Result<&'a str>{args[key].as_str().ok_or_else(||format!("Choose {key}"))}
fn descendants(index:&Index,id:&str)->BTreeSet<String>{let mut found=BTreeSet::from([id.into()]);for _ in 0..64{let before=found.len();for e in index.entries.values(){if found.contains(&e.parent){found.insert(e.id.clone());}}if before==found.len(){break}}found}
fn roots(root:&Path,index:&Index)->Value{let mut items:Vec<Value>=ROOTS.iter().map(|(id,_)|row(root,index,&index.entries[*id]).unwrap()).collect();items.insert(6,json!({"id":"external","name":"Android Files","folder":true,"protected":true,"detail":"Photos, images, audio, video, documents and downloads"}));json!({"folder":"","title":"Files","parent":"","items":items,"total":items.len(),"offset":0})}
fn hex(bytes:&[u8])->String{bytes.iter().map(|b|format!("{b:02x}")).collect()}
const MAX_TEXT_UNITS:usize=100_000;
const MAX_TEXT_BYTES:u64=400_000;
fn text_snapshot(root:&Path,index:&Index,id:&str)->Result<Value>{
 let e=index.entries.get(id).filter(|e|!e.folder).ok_or("Choose an ordinary text file or note")?;
 if trashed(index,id)?{return Err("Restore this file from Trash before reading or editing it".into())}
 let extension=e.name.rsplit('.').next().unwrap_or("").to_ascii_lowercase();
 if !e.note&&!e.mime.starts_with("text/")&&!matches!(e.mime.as_str(),"application/json"|"application/xml"|"application/javascript"|"application/x-ndjson")&&!matches!(extension.as_str(),"txt"|"csv"|"tsv"|"json"|"jsonl"|"ndjson"|"md"|"xml"|"yaml"|"yml"|"toml"|"ini"|"log"){return Err("Choose a UTF-8 text file; binary/media files use the shared viewer".into())}
 let (text,revision,bom)=if e.note{
  let note=crate::read_note(&base(root)?,id)?;let revision=hex(&Sha256::digest(serde_json::to_vec(&note).map_err(|e|e.to_string())?));(note.text,revision,false)
 }else{
  let path=object(root,id)?;let meta=fs::symlink_metadata(&path).map_err(|e|e.to_string())?;
  if !meta.is_file(){return Err("Text file storage is unavailable".into())}if meta.len()>MAX_TEXT_BYTES{return Err("Text files are limited to100,000 UTF-16 units /400KB".into())}
  let bytes=crate::sdk::read_bounded(&path,MAX_TEXT_BYTES)?;let revision=hex(&Sha256::digest(&bytes));let bom=bytes.starts_with(&[239,187,191]);let data=if bom{&bytes[3..]}else{&bytes};
  (String::from_utf8(data.to_vec()).map_err(|_|"Choose a valid UTF-8 text file; no lossy conversion was performed")?,revision,bom)
 };
 let units=text.encode_utf16().count();if units>MAX_TEXT_UNITS{return Err("Text files are limited to100,000 UTF-16 units".into())}if text.contains('\0'){return Err("This looks like binary data rather than an editable text file".into())}
 let remainder=text.replace("\r\n","");let styles=[("crlf",text.contains("\r\n")),("lf",remainder.contains('\n')),("cr",remainder.contains('\r'))];let present:Vec<_>=styles.iter().filter(|(_,present)|*present).collect();let newline=match present.as_slice(){[]=>"none",[style]=>style.0,_=>"mixed"};
 Ok(json!({"id":id,"name":e.name,"mime":e.mime,"text":text,"revision":revision,"utf16_units":units,"utf8_bom":bom,"line_ending":newline,"note":e.note}))
}
fn write_text(root:&Path,index:&mut Index,args:&Value)->Result<Value>{
 let id=s(args,"id")?;let expected=s(args,"revision")?;
 if expected.len()!=64||!expected.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return Err("Read the file first and retain its revision before saving".into())}
 let old=text_snapshot(root,index,id)?;
 if old["revision"]!=expected{return Err("This file changed after it was opened. Reload it before saving; your edit was not written".into())}
 let text=s(args,"text")?;if text.encode_utf16().count()>MAX_TEXT_UNITS||text.contains('\0'){return Err("Use at most100,000 UTF-16 units of text without NUL characters".into())}
 let entry=index.entries.get(id).cloned().ok_or("File is unavailable")?;
 if entry.note{
  let mut note=crate::read_note(&base(root)?,id)?;note.text=text.into();note.updated=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map_err(|e|e.to_string())?.as_millis()as u64;
  crate::validate(&note)?;crate::save_note(&base(root)?,&note)?;index.entries.get_mut(id).unwrap().bytes=(note.title.len()+note.text.len())as u64;
 }else{
  let bom=match args.get("utf8_bom"){Some(v)=>v.as_bool().ok_or("utf8_bom must be true or false")?,None=>old["utf8_bom"]==true};
  let mut bytes=Vec::new();if bom{bytes.extend_from_slice(&[239,187,191]);}bytes.extend_from_slice(text.as_bytes());if bytes.len()as u64>MAX_TEXT_BYTES{return Err("Text file exceeds400KB".into())}
  crate::sdk::atomic(&object(root,id)?,&bytes)?;index.entries.get_mut(id).unwrap().bytes=bytes.len()as u64;
 }
 if let Err(error)=save(root,index){return Err(format!("Text was saved, but Files metadata could not be updated: {error}. Reopen the file before retrying."))}
 text_snapshot(root,index,id)
}

fn copy_tree(root:&Path,index:&mut Index,id:&str,parent:&str)->Result<()> {
 let ids=descendants(index,id);if count(index)+ids.len()>MAX_ENTRIES{return Err("File limit reached".into())}
 let mut remaining=ids.clone();let mut mapped:BTreeMap<String,String>=BTreeMap::new();let mut paths=Vec::new();let mut added=Vec::new();let mut bytes=0u64;
 let result=(||{while !remaining.is_empty(){let before=remaining.len();for old_id in remaining.clone(){let old=index.entries[&old_id].clone();let dest=if old_id==id{parent.to_string()}else if let Some(p)=mapped.get(&old.parent){p.clone()}else{continue};
 let new=uuid::Uuid::new_v4().simple().to_string();let mut e=old.clone();e.vault=None;if e.folder{e.bytes=0;}e.id=new.clone();e.parent=dest.clone();e.name=unique(index,&dest,&e.name,"");e.from=None;
 if !old.folder{let src=if old.note{let n=crate::read_note(&base(root)?,&old.id)?;format!("{}\n\n{}",n.title,n.text).into_bytes()}else{Vec::new()};let out=object(root,&new)?;paths.push(out.clone());
 if old.note{bytes+=src.len()as u64;if bytes>MAX_FILE{return Err("Copy up to256MB at a time".into())}fs::write(&out,&src).map_err(|e|e.to_string())?;e.bytes=src.len()as u64;e.note=false;e.owner="user".into();}else{let src=object(root,&old.id)?;let m=fs::symlink_metadata(&src).map_err(|e|e.to_string())?;if !m.is_file(){return Err("File is unavailable".into())}bytes=bytes.checked_add(m.len()).ok_or("Copy is too large")?;if bytes>MAX_FILE{return Err("Copy up to256MB at a time".into())}fs::copy(&src,&out).map_err(|e|e.to_string())?;}sync_file(&out)?;}
 mapped.insert(old_id.clone(),new.clone());added.push(new.clone());index.entries.insert(new,e);remaining.remove(&old_id);
 }if remaining.len()==before{return Err("Folder tree is invalid".into())}}save(root,index)})();
 if result.is_err(){for id in added{index.entries.remove(&id);}for p in paths{let _=fs::remove_file(p);}}result
}
fn archive_name(name:&str)->String{let mut units=0;let short:String=name.chars().take_while(|c|{units+=c.len_utf16();units<=176}).collect();format!("{}.zip",short.trim_end())}
fn archive(root:&Path,index:&Index,id:&str,path:&Path)->Result<()> {
 let file=fs::File::create(path).map_err(|e|e.to_string())?;let mut zip=zip::ZipWriter::new(file);let opts=zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);let mut total=0u64;
 let base_parent=&index.entries.get(id).ok_or("File is unavailable")?.parent;
 for child in descendants(index,id){let e=&index.entries[&child];let mut parts=vec![e.name.clone()];let mut parent=&e.parent;while parent!=base_parent{let p=&index.entries[parent];parts.push(p.name.clone());parent=&p.parent;}parts.reverse();let name=parts.join("/");
 if e.folder{zip.add_directory(format!("{name}/"),opts).map_err(|e|e.to_string())?;}else{zip.start_file(name,opts).map_err(|e|e.to_string())?;
 if e.note{let n=crate::read_note(&base(root)?,&e.id)?;let data=format!("{}\n\n{}",n.title,n.text);total+=data.len()as u64;if total>MAX_FILE{return Err("Compress up to256MB at a time".into())}zip.write_all(data.as_bytes()).map_err(|e|e.to_string())?;}else{let p=object(root,&e.id)?;let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !m.is_file(){return Err("File is unavailable".into())}total=total.checked_add(m.len()).ok_or("Archive is too large")?;if total>MAX_FILE{return Err("Compress up to256MB at a time".into())}std::io::copy(&mut fs::File::open(p).map_err(|e|e.to_string())?,&mut zip).map_err(|e|e.to_string())?;}}
 }let out=zip.finish().map_err(|e|e.to_string())?;out.sync_all().map_err(|e|e.to_string())?;if out.metadata().map_err(|e|e.to_string())?.len()>MAX_FILE{return Err("Compressed file exceeds256MB".into())}Ok(())
}
pub fn share(root:&Path,id:&str)->Result<Value>{let index=load(root)?;vault::guard_tree(root,&index,id)?;let e=index.entries.get(id).ok_or("File is unavailable")?;if !e.folder{return resolve(root,id)}if id=="system"||trashed(&index,id)?{return Err("Choose an active personal folder".into())}let path=base(root)?.join(format!("share-{}.zip",uuid::Uuid::new_v4().simple()));if let Err(e)=archive(root,&index,id,&path){let _=fs::remove_file(&path);return Err(e)}Ok(json!({"path":path,"name":archive_name(&e.name),"mime":"application/zip","temporary":true,"id":id}))}
pub fn service(root:&Path,action:&str,args:&Value)->Result<Value>{
 let mut index=load(root)?;
 for field in ["id","folder","parent"]{if let Some(id)=args[field].as_str(){
  if action=="library.info"&&index.entries.get(id).is_some_and(|e|e.vault.is_some())&&!vault::readable(root,&index,id){return row(root,&index,&index.entries[id])}
  vault::guard(root,&index,id)?;
 }}
 if matches!(action,"library.rename"|"library.move"|"library.trash"|"library.restore"|"library.purge"|"library.copy"|"library.compress"){if let Some(id)=args["id"].as_str(){vault::guard_tree(root,&index,id)?;}}
 if action=="library.move"{let id=s(args,"id")?;let parent=s(args,"parent")?;if vault::scope(&index,parent).is_some()&&descendants(&index,id).iter().any(|id|index.entries[id].vault.is_some()){return Err("Encrypted folders cannot be nested".into())}}

 match action{
 "library.read_text"=>text_snapshot(root,&index,s(args,"id")?),
 "library.write_text"=>write_text(root,&mut index,args),
 "library.restore_all"|"library.empty"=>{let ids=index.entries.values().filter(|e|e.parent=="trash").map(|e|e.id.clone()).collect::<Vec<_>>();let mut done=0;for id in ids{if let Err(error)=service(root,if action=="library.empty"{"library.purge"}else{"library.restore"},&json!({"id":id})){return Err(format!("{done} items completed. Remaining items were kept: {error}"))}done+=1;}Ok(json!({"completed":done}))},
 "library.list"=>{let id=args["folder"].as_str().unwrap_or("");let query=args["query"].as_str().unwrap_or("");if query.encode_utf16().count()>80{return Err("Search is too long".into())}if id.is_empty()&&query.is_empty(){return Ok(roots(root,&index))}if id=="system"{return Ok(json!({"folder":"system","title":"OnKlav System Files","parent":"","items":[{"id":"system-apps","name":"Installed Applications","folder":true,"protected":true,"detail":"Read-only signed SDK packages"},{"id":"system-info","name":"About OnKlav System Files","folder":false,"protected":true,"detail":"Protected application and runtime information"}],"total":2,"offset":0}))}
 if id=="system-apps"{let rows=system_packages(root)?.into_iter().filter(|e|e["name"].as_str().unwrap_or("").to_lowercase().contains(&query.to_lowercase())).collect::<Vec<_>>();let total=rows.len();let offset=args["offset"].as_u64().unwrap_or(0).min(10000)as usize;let items=rows.into_iter().skip(offset).take(40).collect::<Vec<_>>();return Ok(json!({"folder":id,"title":"Installed Applications","parent":"system","items":items,"total":total,"offset":offset}))}
 let parent=if id.is_empty(){""}else{&index.entries.get(id).filter(|e|e.folder).ok_or("Folder is unavailable")?.parent};
 let mut rows:Vec<&Entry>=index.entries.values().filter(|e|if !vault::readable(root,&index,&e.id)&&e.vault.is_none(){false}else if !query.is_empty(){(!protected(&e.id)||e.id=="notes")&&e.name.to_lowercase().contains(&query.to_lowercase())&&trashed(&index,&e.id).is_ok_and(|t|t==(id=="trash"))}else{e.parent==id}).collect();rows.sort_by(|a,b|b.folder.cmp(&a.folder).then(a.name.to_lowercase().cmp(&b.name.to_lowercase())).then(a.id.cmp(&b.id)));let total=rows.len();let offset=args["offset"].as_u64().unwrap_or(0).min(MAX_ENTRIES as u64)as usize;let items=rows.into_iter().skip(offset).take(40).map(|e|row(root,&index,e)).collect::<Result<Vec<_>>>()?;
 Ok(json!({"folder":id,"title":index.entries.get(id).map(|e|e.name.as_str()).unwrap_or("Search Files"),"parent":parent,"items":items,"total":total,"offset":offset,"trash":if id.is_empty(){false}else{trashed(&index,id)?}}))},
 "library.info"=>{let id=s(args,"id")?;if id.starts_with("package-"){let entry=system_packages(root)?.into_iter().find(|p|p["id"]==id).ok_or("This app package is no longer installed")?;return Ok(json!({"id":id,"name":entry["name"],"protected":true,"preview":serde_json::to_string_pretty(&entry["manifest"]).map_err(|e|e.to_string())?}))}if id=="system-info"{return Ok(json!({"id":id,"name":"OnKlav System","protected":true,"preview":"OnKlav uses Kotlin/Compose, a Rust shared core and signed SDK apps. Its settings, package registry and runtime data are protected. Manage apps in Settings > Apps. Personal content belongs in Downloads, Images, Documents, Audio or Video. Android Files provides direct access to shared phone files after storage access is enabled."}))}let e=index.entries.get(id).ok_or("File is unavailable")?;let mut result=row(root,&index,e)?;let children=descendants(&index,id);let bytes:u64=children.iter().map(|c|index.entries[c].bytes).sum();result["properties"]=json!(format!("Name: {}\nType: {}\nLocation: {}\nSize: {}{}",e.name,if e.folder{"Folder"}else{&e.mime},virtual_location(&index,&e.parent)?,size(bytes),if e.folder{format!("\nItems: {}",children.len()-1)}else{String::new()}));if !e.folder&&(e.note||e.mime.starts_with("text/")){let bytes=if e.note{let n=crate::read_note(&base(root)?,id)?;format!("{}\n\n{}",n.title,n.text).into_bytes()}else{let p=object(root,id)?;let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !m.is_file(){return Err("File is unavailable".into())}let mut data=Vec::new();fs::File::open(p).map_err(|e|e.to_string())?.take(24_000).read_to_end(&mut data).map_err(|e|e.to_string())?;data};result["preview"]=json!(crate::plain(&String::from_utf8_lossy(&bytes),12_000));}Ok(result)},
 "library.folder"=>{let parent=s(args,"parent")?;folder(&index,parent)?;let name=s(args,"name")?;valid_name(name)?;if count(&index)>=MAX_ENTRIES{return Err("File limit reached".into())}if unique(&index,parent,name,"")!=name{return Err("A folder or file already uses that name".into())}let id=uuid::Uuid::new_v4().simple().to_string();index.entries.insert(id.clone(),Entry{id:id.clone(),parent:parent.into(),name:name.into(),mime:"inode/directory".into(),owner:"user".into(),folder:true,note:false,bytes:0,from:None,vault:None});save(root,&index)?;Ok(json!({"id":id}))},
 "library.compress"=>{let id=s(args,"id")?;let e=index.entries.get(id).ok_or("File is unavailable")?.clone();if protected(id)||trashed(&index,id)?{return Err("Choose an active personal file or folder".into())}if count(&index)>=MAX_ENTRIES{return Err("File limit reached".into())}let new=uuid::Uuid::new_v4().simple().to_string();let path=object(root,&new)?;let result=(||{archive(root,&index,id,&path)?;let bytes=fs::metadata(&path).map_err(|e|e.to_string())?.len();let name=unique(&index,&e.parent,&archive_name(&e.name),"");index.entries.insert(new.clone(),Entry{id:new.clone(),parent:e.parent,name,mime:"application/zip".into(),owner:"user".into(),folder:false,note:false,bytes,from:None,vault:None});save(root,&index)?;Ok(json!({"id":new}))})();if result.is_err(){let _=fs::remove_file(path);}result},
 "library.rename"|"library.move"|"library.trash"|"library.restore"|"library.purge"|"library.copy"=>{
 let id=s(args,"id")?;if protected(id){return Err("This built-in folder is protected".into())}let old=index.entries.get(id).cloned().ok_or("File is unavailable")?;let deleted=trashed(&index,id)?;
 if action=="library.purge"{if descendants(&index,id).iter().any(|key|index.entries[key].vault.is_some()){return Err("Remove folder protection before permanently deleting this folder".into())}if !deleted{return Err("Move this item to Trash first".into())}for child in if old.vault.is_some(){vault::members(&index,id)}else{descendants(&index,id)}{let e=&index.entries[&child];if !e.folder{let p=if e.note{crate::location(&base(root)?,&child)?}else{object(root,&child)?};if p.exists(){let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !m.is_file(){return Err("Unexpected file type; nothing outside the selected file was removed".into())}fs::remove_file(p).map_err(|e|e.to_string())?;}}}return purge_commit(root,index,id);}
 if deleted&&!matches!(action,"library.restore"){return Err("Restore this item before changing it".into())}
 if action=="library.rename"{let name=s(args,"name")?;valid_name(name)?;if unique(&index,&old.parent,name,id)!=name{return Err("That name is already in use".into())}if old.note{let mut note=crate::read_note(&base(root)?,id)?;note.title=name.strip_suffix(".txt").unwrap_or(name).into();crate::validate(&note)?;crate::save_note(&base(root)?,&note)?;}index.entries.get_mut(id).unwrap().name=name.into();}
 else if action=="library.trash"{let e=index.entries.get_mut(id).unwrap();e.from=Some(e.parent.clone());e.parent="trash".into();}
 else {let parent=if action=="library.restore"{if !deleted{return Err("This item is not in Trash".into())}old.from.as_deref().filter(|p|folder(&index,p).is_ok()).unwrap_or("documents")}else{s(args,"parent")?};folder(&index,parent)?;if descendants(&index,id).contains(parent){return Err("A folder cannot be moved inside itself".into())}let name=unique(&index,parent,&old.name,if action=="library.copy"{""}else{id});
 if action=="library.copy"{copy_tree(root,&mut index,id,parent)?;return Ok(json!({"done":true}));}
 else {let e=index.entries.get_mut(id).unwrap();e.parent=parent.into();e.name=name;e.from=None;}}
 save(root,&index)?;Ok(json!(true))},
 _=>Err("Unsupported Files operation".into())
 }
}
fn purge_commit(root:&Path,mut index:Index,id:&str)->Result<Value>{let all=if index.entries.get(id).is_some_and(|e|e.vault.is_some()){vault::members(&index,id)}else{descendants(&index,id)};
 for key in &all{let e=&index.entries[key];let mut paths=Vec::new();if e.vault.is_some(){paths.push(vault::archive_path(root,key)?);}if e.mime=="application/vnd.onklav.write+json"{let uuid=uuid::Uuid::parse_str(key).map_err(|_|"Invalid document identity")?;for suffix in ["json","json.bak","json.new"]{paths.push(root.join(format!("write/documents/{uuid}.{suffix}")));}for path in [format!("notes/{key}.json"),format!("app-sdk/data/app.onklav.notes/notes/{key}.json"),format!("onklav-content/notes/{key}.json")]{paths.push(root.join(path));}}
 for path in paths{if path.exists(){let meta=fs::symlink_metadata(&path).map_err(|e|e.to_string())?;if !meta.is_file(){return Err("Unexpected recovery file type".into())}fs::remove_file(path).map_err(|e|e.to_string())?;}}}
 index.entries.retain(|k,_|!all.contains(k));save(root,&index)?;Ok(json!(true))}
fn sync_file(path:&Path)->Result<()>{fs::File::open(path).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;fs::File::open(path.parent().ok_or("Invalid content directory")?).and_then(|f|f.sync_all()).map_err(|e|e.to_string())}
fn app_folder(index:&mut Index,parent:&str,owner:&str,title:&str)->String{if let Some(e)=index.entries.values().find(|e|e.folder&&e.parent==parent&&e.owner==owner){return e.id.clone()}let id=uuid::Uuid::new_v4().simple().to_string();let safe=crate::plain(title,80).chars().map(|c|if c.is_control()||"/\\:".contains(c){'_'}else{c}).collect::<String>().trim().trim_start_matches('.').to_string();let name=unique(index,parent,if safe.is_empty(){"App Files"}else{&safe},"");index.entries.insert(id.clone(),Entry{id:id.clone(),parent:parent.into(),name,mime:"inode/directory".into(),owner:owner.into(),folder:true,note:false,bytes:0,from:None,vault:None});id}
pub fn store_text(root:&Path,owner:&str,title:&str,name:&str,text:&str)->Result<Value>{if text.encode_utf16().count()>100_100{return Err("Document is too large".into())}let mut index=load(root)?;let parent=app_folder(&mut index,"documents",owner,title);save(root,&index)?;let safe=crate::plain(&name.replace(['/','\\',':'],"_"),100).trim().trim_start_matches('.').to_string();let name=if safe.is_empty(){"Document.txt".into()}else if safe.ends_with(".txt"){safe}else{format!("{safe}.txt")};insert(root,&mut index,&parent,&name,"text/plain",owner,text.as_bytes())}
fn insert(root:&Path,index:&mut Index,parent:&str,name:&str,mime:&str,owner:&str,bytes:&[u8])->Result<Value>{vault::guard(root,index,parent)?;folder(index,parent)?;valid_name(name)?;if count(&index)>=MAX_ENTRIES{return Err("File limit reached".into())}let id=uuid::Uuid::new_v4().simple().to_string();let name=unique(index,parent,name,"");crate::sdk::atomic(&object(root,&id)?,bytes)?;index.entries.insert(id.clone(),Entry{id:id.clone(),parent:parent.into(),name:name.clone(),mime:mime.into(),owner:owner.into(),folder:false,note:false,bytes:bytes.len()as u64,from:None,vault:None});save(root,index)?;Ok(json!({"id":id,"name":name,"parent":parent}))}
fn media_root(mime:&str)->Option<&'static str>{if mime.starts_with("image/"){Some("images")}else if mime.starts_with("audio/"){Some("audio")}else if mime.starts_with("video/"){Some("video")}else{None}}
pub fn import_staged(root:&Path,parent:&str,name:&str,mime:&str,owner:&str,title:&str,app_owned:bool)->Result<Value>{let mut index=load(root)?;let parent=if app_owned{if let Some(media)=media_root(mime){media.into()}else{app_folder(&mut index,parent,owner,title)}}else{parent.into()};folder(&index,&parent)?;vault::guard(root,&index,&parent)?;valid_name(name)?;if mime.len()>200||mime.chars().any(char::is_control){return Err("Invalid file type".into())}let staged=root.join("content-import.tmp");let meta=fs::symlink_metadata(&staged).map_err(|e|e.to_string())?;let limit=if mime.starts_with("audio/")||mime.starts_with("video/"){2*1024*1024*1024}else{MAX_FILE};if !meta.is_file()||meta.len()>limit{return Err("This file exceeds the supported import size".into())}if count(&index)>=MAX_ENTRIES{return Err("File limit reached".into())}let id=uuid::Uuid::new_v4().simple().to_string();let target=object(root,&id)?;fs::File::open(&staged).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;fs::rename(&staged,&target).map_err(|e|e.to_string())?;fs::File::open(target.parent().ok_or("Invalid content directory")?).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;let name=unique(&index,&parent,name,"");index.entries.insert(id.clone(),Entry{id:id.clone(),parent:parent.clone(),name:name.clone(),mime:mime.into(),owner:owner.into(),folder:false,note:false,bytes:meta.len(),from:None,vault:None});save(root,&index)?;Ok(json!({"id":id,"parent":parent,"name":name}))}
fn system_packages(root:&Path)->Result<Vec<Value>>{
 let path=root.join("app-sdk/registry.json");if !path.exists(){return Ok(vec![])}
 let registry:Value=serde_json::from_slice(&crate::sdk::read_bounded(&path,2*1024*1024)?).map_err(|_|"Installed app information is unavailable")?;
 let mut rows=Vec::new();for app in registry["apps"].as_object().ok_or("Installed app information is unavailable")?.values(){if app["installed"]!=true{continue}let hash=app["hash"].as_str().ok_or("Invalid installed package")?;if hash.len()!=64||!hash.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b)){return Err("Invalid installed package".into())}let m=&app["manifest"];rows.push(json!({"id":format!("package-{hash}"),"name":format!("{}.onklav-a",m["name"].as_str().unwrap_or("App")),"folder":false,"protected":true,"mime":"application/octet-stream","detail":format!("Signed SDK app · Version {}",m["version"]),"manifest":m}));}rows.sort_by(|a,b|a["name"].as_str().cmp(&b["name"].as_str()));Ok(rows)
}
/// Host-only resolution is never returned as a guest service reply.
pub fn resolve(root:&Path,id:&str)->Result<Value>{
 if let Some(hash)=id.strip_prefix("package-"){let entry=system_packages(root)?.into_iter().find(|e|e["id"]==id).ok_or("This app package is no longer installed")?;let path=root.join("app-sdk/packages").join(format!("{hash}.onklav-a"));let bytes=crate::sdk::read_bounded(&path,700_000)?;if format!("{:x}",Sha256::digest(&bytes))!=hash{return Err("This installed package is damaged".into())}return Ok(json!({"path":path,"name":entry["name"],"mime":"application/octet-stream","note":false,"id":id}))}
 let index=load(root)?;vault::guard(root,&index,id)?;let e=index.entries.get(id).filter(|e|!e.folder).ok_or("Choose a file")?;if trashed(&index,id)?{return Err("Restore this file before opening it".into())}let path=if e.note{let n=crate::read_note(&base(root)?,id)?;let p=directory(base(root)?.join("open"))?.join(format!("{id}.txt"));crate::sdk::atomic(&p,format!("{}\n\n{}",n.title,n.text).as_bytes())?;p}else{let p=object(root,id)?;if !fs::symlink_metadata(&p).map_err(|e|e.to_string())?.is_file(){return Err("File is unavailable".into())}p};Ok(json!({"path":path,"name":e.name,"mime":e.mime,"note":e.note,"id":e.id}))}

/// Camera's native host surface can access only its own active captures.
pub fn owned_media(root:&Path,owner:&str,operation:&str,args:&Value)->Result<Value>{
 let index=load(root)?;
 let allowed=|e:&&Entry|e.owner==owner&&vault::readable(root,&index,&e.id)&&!e.folder&&(e.mime=="image/jpeg"||e.mime=="video/mp4")&&trashed(&index,&e.id).is_ok_and(|v|!v);
 if operation=="list"{let mut rows=index.entries.values().filter(allowed).collect::<Vec<_>>();rows.sort_by(|a,b|b.name.cmp(&a.name).then(b.id.cmp(&a.id)));let total=rows.len();let offset=args["offset"].as_u64().unwrap_or(0).min(MAX_ENTRIES as u64)as usize;let items=rows.into_iter().skip(offset).take(60).map(|e|resolve(root,&e.id)).collect::<Result<Vec<_>>>()?;return Ok(json!({"items":items,"total":total,"offset":offset}))}
 let id=args["id"].as_str().ok_or("Choose a capture")?;index.entries.get(id).filter(allowed).ok_or("This capture is unavailable")?;
 match operation{"resolve"=>resolve(root,id),"trash"=>service(root,"library.trash",&json!({"id":id})),_=>Err("Unknown Camera action".into())}
}

#[cfg(test)] mod tests {
 use super::*;
 fn note(id:u64,title:&str,text:&str)->Note{Note{id:format!("{id:032x}"),title:title.into(),text:text.into(),updated:1}}
 fn list(root:&Path,id:&str)->Value{service(root,"library.list",&json!({"folder":id})).unwrap()}
 fn create(root:&Path,parent:&str,name:&str)->String{service(root,"library.folder",&json!({"parent":parent,"name":name})).unwrap()["id"].as_str().unwrap().into()}
 fn put(root:&Path,parent:&str,name:&str,text:&str)->String{let mut i=load(root).unwrap();insert(root,&mut i,parent,name,"text/plain","test.app",text.as_bytes()).unwrap()["id"].as_str().unwrap().into()}
 #[test]fn text_api_reads_full_utf8_and_preserves_bom_with_conflict_checks(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let text=format!("header,amount\r\n{}", "row,🐈\r\n".repeat(3000));let id=put(r,"downloads","data.csv",&text);let path=object(r,&id).unwrap();let mut bytes=vec![239,187,191];bytes.extend_from_slice(text.as_bytes());fs::write(&path,bytes).unwrap();
  let file=service(r,"library.read_text",&json!({"id":id})).unwrap();assert_eq!(file["text"],text);assert_eq!(file["utf8_bom"],true);assert_eq!(file["line_ending"],"crlf");assert!(file.get("path").is_none());assert!(file.get("uri").is_none());
  let saved=service(r,"library.write_text",&json!({"id":id,"revision":file["revision"],"text":"new,row\r\n"})).unwrap();assert_eq!(saved["text"],"new,row\r\n");assert_eq!(saved["utf8_bom"],true);
  let error=service(r,"library.write_text",&json!({"id":id,"revision":file["revision"],"text":"stale edit"})).unwrap_err();assert!(error.contains("changed"));assert_eq!(service(r,"library.read_text",&json!({"id":id})).unwrap()["text"],"new,row\r\n");
 }
 #[test]fn text_api_classifies_all_newline_styles(){
  let d=tempfile::tempdir().unwrap();let r=d.path();for (text,style) in [("one","none"),("one\ntwo","lf"),("one\rtwo","cr"),("one\r\ntwo","crlf"),("one\r\ntwo\rthree","mixed"),("one\ntwo\rthree","mixed")]{let id=put(r,"downloads","style.txt",text);let file=service(r,"library.read_text",&json!({"id":id})).unwrap();assert_eq!(file["line_ending"],style);assert_eq!(file["text"],text);}
 }
 #[test]fn text_api_rejects_oversize_binary_trash_and_unread_revisions(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let id=put(r,"downloads","large.txt",&"x".repeat(100001));assert!(service(r,"library.read_text",&json!({"id":id})).unwrap_err().contains("100,000"));
  let id=put(r,"downloads","invalid.txt","safe");fs::write(object(r,&id).unwrap(),[255,254]).unwrap();assert!(service(r,"library.read_text",&json!({"id":id})).unwrap_err().contains("UTF-8"));
  let id=put(r,"downloads","live.txt","keep");assert!(service(r,"library.write_text",&json!({"id":id,"text":"replace","revision":""})).is_err());assert_eq!(fs::read(object(r,&id).unwrap()).unwrap(),b"keep");service(r,"library.trash",&json!({"id":id})).unwrap();assert!(service(r,"library.read_text",&json!({"id":id})).is_err());assert!(service(r,"library.read_text",&json!({"id":"system-info"})).is_err());assert!(service(r,"library.read_text",&json!({"id":"../outside"})).is_err());
 }
 #[test]fn text_api_edits_notes_without_losing_their_titles(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let note=Note{id:"a".repeat(32),title:"Keep title".into(),text:"Before".into(),updated:1};note_save(r,&note).unwrap();let file=service(r,"library.read_text",&json!({"id":note.id})).unwrap();let saved=service(r,"library.write_text",&json!({"id":note.id,"revision":file["revision"],"text":"After"})).unwrap();assert_eq!(saved["text"],"After");assert_eq!(note_read(r,&note.id).unwrap().title,"Keep title");assert_ne!(saved["revision"],file["revision"]);
 }
 #[cfg(unix)]#[test]fn text_api_never_follows_a_link_out_of_content_storage(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let id=put(r,"downloads","linked.txt","public");let path=object(r,&id).unwrap();let outside=r.join("private.txt");fs::write(&outside,b"keep private").unwrap();fs::remove_file(&path).unwrap();std::os::unix::fs::symlink(&outside,&path).unwrap();assert!(service(r,"library.read_text",&json!({"id":id})).is_err());assert!(service(r,"library.write_text",&json!({"id":id,"revision":"a".repeat(64),"text":"bad"})).is_err());assert_eq!(fs::read(outside).unwrap(),b"keep private");
 }
 #[test] fn duplicate_maximum_length_names_remain_readable_after_copy_import_and_restore(){
  let d=tempfile::tempdir().unwrap();let r=d.path();
  for name in [format!("{}.txt","a".repeat(176)),format!("a.{}","x".repeat(178)),format!("{}.txt","🐈".repeat(88)),format!("🐈.{}","x".repeat(177)),format!("a.{} z", "x".repeat(175))]{
   let id=put(r,"downloads",&name,"original");
   service(r,"library.copy",&json!({"id":id,"parent":"downloads"})).unwrap();
   fs::write(r.join("content-import.tmp"),b"imported").unwrap();
   import_staged(r,"downloads",&name,"text/plain","test.app","Test",false).unwrap();
   service(r,"library.trash",&json!({"id":id})).unwrap();
   service(r,"library.restore",&json!({"id":id})).unwrap();
   let index=load(r).unwrap();let mut names=BTreeSet::new();
   for e in index.entries.values().filter(|e|e.parent=="downloads"){
    valid_name(&e.name).unwrap();assert!(names.insert(e.name.to_lowercase()));
    let bytes=fs::read(object(r,&e.id).unwrap()).unwrap();assert!(bytes==b"original"||bytes==b"imported");
   }
  }
 }
 #[test] fn many_case_insensitive_collisions_find_the_next_name_without_a_full_scan_per_candidate(){
  let mut index=Index::default();
  for n in 1..=9_000{let id=format!("{n:032x}");let name=if n==1{"FILE.txt".into()}else{format!("FILE ({n}).txt")};index.entries.insert(id.clone(),Entry{id,parent:"downloads".into(),name,mime:"text/plain".into(),owner:"test".into(),folder:false,note:false,bytes:0,from:None,vault:None});}
  assert_eq!(unique(&index,"downloads","file.txt",""),"file (9001).txt");
 }
 #[test] fn roots_and_notes_live_in_the_shared_library(){let d=tempfile::tempdir().unwrap();let r=d.path();assert_eq!(list(r,"")["items"].as_array().unwrap().len(),8);note_save(r,&note(1,"Hello","Actual note")).unwrap();let rows=list(r,"notes");assert_eq!(rows["items"][0]["name"],"Hello.txt");assert_eq!(note_read(r,&format!("{:032x}",1)).unwrap().text,"Actual note");assert!(crate::location(&base(r).unwrap(),&format!("{:032x}",1)).unwrap().is_file());}
 #[test] fn existing_notes_are_verified_migrated_once_and_originals_remain(){let d=tempfile::tempdir().unwrap();let r=d.path();let old=r.join("app-sdk/data/app.onklav.notes");crate::save_note(&old,&note(1,"Existing","Keep me")).unwrap();assert_eq!(note_list(r).unwrap().len(),1);let mut changed=note_read(r,&format!("{:032x}",1)).unwrap();changed.text="Edited in the shared store".into();note_save(r,&changed).unwrap();assert_eq!(note_read(r,&changed.id).unwrap().text,changed.text);assert_eq!(crate::read_note(&old,&changed.id).unwrap().text,"Keep me");service(r,"library.trash",&json!({"id":changed.id})).unwrap();assert!(note_list(r).unwrap().is_empty());assert!(note_list(r).unwrap().is_empty());}
 #[test] fn note_move_rename_trash_restore_and_permanent_delete_share_one_record(){let d=tempfile::tempdir().unwrap();let r=d.path();let n=note(1,"Original","Same body 🐈");note_save(r,&n).unwrap();let folder=create(r,"documents","Work");service(r,"library.move",&json!({"id":n.id,"parent":folder})).unwrap();service(r,"library.rename",&json!({"id":n.id,"name":"Renamed.txt"})).unwrap();assert_eq!(note_read(r,&n.id).unwrap().title,"Renamed");assert_eq!(list(r,&folder)["items"][0]["name"],"Renamed.txt");service(r,"library.trash",&json!({"id":folder})).unwrap();assert!(note_list(r).unwrap().is_empty());assert!(note_save(r,&n).is_err());service(r,"library.restore",&json!({"id":folder})).unwrap();assert_eq!(note_list(r).unwrap()[0].text,n.text);service(r,"library.trash",&json!({"id":n.id})).unwrap();service(r,"library.purge",&json!({"id":n.id})).unwrap();assert!(note_list(r).unwrap().is_empty());}
 #[test] fn folders_reject_traversal_collisions_cycles_and_system_changes(){let d=tempfile::tempdir().unwrap();let r=d.path();for name in ["../escape","a/b","a\\b",".hidden",""," bad","bad\n"]{assert!(service(r,"library.folder",&json!({"parent":"documents","name":name})).is_err());}let a=create(r,"documents","Work");let b=create(r,&a,"Children");assert!(service(r,"library.move",&json!({"id":a,"parent":b})).is_err());assert!(service(r,"library.folder",&json!({"parent":"documents","name":"work"})).is_err());for id in ["documents","system","trash","notes"]{assert!(service(r,"library.trash",&json!({"id":id})).is_err());}assert!(service(r,"library.folder",&json!({"parent":"system","name":"Hack"})).is_err());}
 #[test] fn recursive_copy_and_zip_preserve_nested_files_and_notes(){let d=tempfile::tempdir().unwrap();let r=d.path();let a=create(r,"downloads","Trip");let b=create(r,&a,"Photos");put(r,&b,"hello.txt","original");let n=note(42,"Packing","Bring books");note_save(r,&n).unwrap();service(r,"library.move",&json!({"id":n.id,"parent":a})).unwrap();service(r,"library.copy",&json!({"id":a,"parent":"documents"})).unwrap();let docs=list(r,"documents");let copied=docs["items"].as_array().unwrap().iter().find(|e|e["name"]=="Trip").unwrap()["id"].as_str().unwrap();assert_eq!(list(r,copied)["total"],2);assert_eq!(note_list(r).unwrap().len(),1);
 let result=service(r,"library.compress",&json!({"id":a})).unwrap();let mut zip=zip::ZipArchive::new(fs::File::open(object(r,result["id"].as_str().unwrap()).unwrap()).unwrap()).unwrap();let mut text=String::new();zip.by_name("Trip/Photos/hello.txt").unwrap().read_to_string(&mut text).unwrap();assert_eq!(text,"original");assert!(zip.by_name("Trip/Packing.txt").is_ok());assert_eq!(list(r,&b)["total"],1);let info=service(r,"library.info",&json!({"id":a})).unwrap();assert!(info["properties"].as_str().unwrap().contains("Items: 3"));let shared=share(r,&a).unwrap();assert_eq!(shared["temporary"],true);assert!(Path::new(shared["path"].as_str().unwrap()).is_file());}
 #[cfg(unix)] #[test] fn recursive_copy_and_compress_fail_without_partial_outputs_for_links(){let d=tempfile::tempdir().unwrap();let r=d.path();let a=create(r,"downloads","Unsafe");let id=put(r,&a,"linked.txt","old");let p=object(r,&id).unwrap();fs::remove_file(&p).unwrap();let secret=r.join("private");fs::write(&secret,"keep").unwrap();std::os::unix::fs::symlink(&secret,p).unwrap();assert!(service(r,"library.copy",&json!({"id":a,"parent":"documents"})).is_err());assert_eq!(list(r,"documents")["total"],1);assert!(service(r,"library.compress",&json!({"id":a})).is_err());assert_eq!(list(r,"downloads")["total"],1);assert_eq!(fs::read_to_string(secret).unwrap(),"keep");}
 #[test] fn copied_files_are_independent_and_purge_leaves_other_content(){let d=tempfile::tempdir().unwrap();let r=d.path();let folder=create(r,"downloads","Keep");let a=put(r,&folder,"a.txt","original");put(r,"downloads","unrelated.txt","stay");service(r,"library.copy",&json!({"id":a,"parent":"documents"})).unwrap();let copied=list(r,"documents")["items"].as_array().unwrap().iter().find(|e|e["name"]=="a.txt").unwrap()["id"].as_str().unwrap().to_string();service(r,"library.trash",&json!({"id":folder})).unwrap();service(r,"library.purge",&json!({"id":folder})).unwrap();assert_eq!(fs::read(object(r,&copied).unwrap()).unwrap(),b"original");assert_eq!(list(r,"downloads")["items"][0]["name"],"unrelated.txt");}
 #[test] fn restore_handles_a_removed_parent_without_overwriting_names(){let d=tempfile::tempdir().unwrap();let r=d.path();let folder=create(r,"documents","Temporary");let id=put(r,&folder,"same.txt","keep");service(r,"library.trash",&json!({"id":id})).unwrap();service(r,"library.trash",&json!({"id":folder})).unwrap();service(r,"library.purge",&json!({"id":folder})).unwrap();put(r,"documents","same.txt","other");service(r,"library.restore",&json!({"id":id})).unwrap();let names:Vec<_>=list(r,"documents")["items"].as_array().unwrap().iter().map(|e|e["name"].as_str().unwrap().to_string()).collect();assert!(names.contains(&"same.txt".into())&&names.contains(&"same (2).txt".into()));}
 #[test] fn corrupt_index_fails_closed_and_never_erases_file_bytes(){let d=tempfile::tempdir().unwrap();let r=d.path();let id=put(r,"downloads","kept.txt","valuable");fs::write(base(r).unwrap().join("index.json"),b"broken").unwrap();assert!(service(r,"library.list",&json!({})).is_err());assert_eq!(fs::read(object(r,&id).unwrap()).unwrap(),b"valuable");}
 #[test] fn unicode_and_unusual_note_titles_cannot_poison_the_index(){let d=tempfile::tempdir().unwrap();let r=d.path();for (i,title) in ["...","a/b","\n\t","🐈 Notes"].iter().enumerate(){note_save(r,&note((i+1)as u64,title,"body")).unwrap();}assert_eq!(note_list(r).unwrap().len(),4);assert_eq!(list(r,"notes")["total"],4);store_text(r,"test.app","../Bad\nApp","../name","export").unwrap();assert!(service(r,"library.list",&json!({})).is_ok());}
 #[test] fn pagination_and_search_include_files_beyond_the_first_page(){let d=tempfile::tempdir().unwrap();let r=d.path();for i in 0..45{put(r,"downloads",&format!("File {i:02}.txt"),"body");}assert_eq!(list(r,"downloads")["items"].as_array().unwrap().len(),40);assert_eq!(service(r,"library.list",&json!({"folder":"downloads","offset":40})).unwrap()["items"].as_array().unwrap().len(),5);assert_eq!(service(r,"library.list",&json!({"query":"File 44"})).unwrap()["total"],1);}
 #[test] fn staged_import_is_bounded_and_keeps_the_android_original(){let d=tempfile::tempdir().unwrap();let r=d.path();let p=r.join("content-import.tmp");let original=r.join("android-original");fs::write(&original,b"image bytes").unwrap();fs::copy(&original,&p).unwrap();let result=import_staged(r,"images","photo.jpg","image/jpeg","test.app","Camera",true).unwrap();assert!(!p.exists());assert_eq!(fs::read(original).unwrap(),b"image bytes");let file=resolve(r,result["id"].as_str().unwrap()).unwrap();assert_eq!(fs::read(file["path"].as_str().unwrap()).unwrap(),b"image bytes");}
 #[cfg(unix)] #[test] fn symlinks_cannot_redirect_content_reads_or_imports(){let d=tempfile::tempdir().unwrap();let r=d.path();let other=tempfile::tempdir().unwrap();std::os::unix::fs::symlink(other.path(),r.join("onklav-content")).unwrap();assert!(service(r,"library.list",&json!({})).is_err());assert!(fs::read_dir(other.path()).unwrap().next().is_none());}
 #[test] fn a_corrupt_trashed_note_does_not_block_other_files_or_active_notes(){let d=tempfile::tempdir().unwrap();let r=d.path();let n=note(1,"Corrupt later","body");note_save(r,&n).unwrap();note_save(r,&note(2,"Kept","safe")).unwrap();service(r,"library.trash",&json!({"id":n.id})).unwrap();fs::write(crate::location(&base(r).unwrap(),&n.id).unwrap(),b"broken").unwrap();assert_eq!(list(r,"")["items"].as_array().unwrap().len(),8);assert_eq!(note_list(r).unwrap().len(),1);service(r,"library.purge",&json!({"id":n.id})).unwrap();assert_eq!(note_list(r).unwrap()[0].text,"safe");}

 #[test] fn excessive_nesting_is_rejected_before_it_can_damage_the_index(){let d=tempfile::tempdir().unwrap();let r=d.path();let mut parent="documents".to_string();let mut rejected=false;for _ in 0..80{match service(r,"library.folder",&json!({"parent":parent,"name":"Nested"})){Ok(value)=>{parent=value["id"].as_str().unwrap().into();assert!(service(r,"library.list",&json!({})).is_ok());},Err(_)=>{rejected=true;break;}}}assert!(rejected);assert!(service(r,"library.list",&json!({})).is_ok());}

 #[test] fn shared_index_metadata_does_not_consume_the_notes_private_quota(){let d=tempfile::tempdir().unwrap();let r=d.path();let n=note(1,"Private","body");note_save(r,&n).unwrap();let file=crate::location(&base(r).unwrap(),&n.id).unwrap();let expected=fs::metadata(file).unwrap().len();fs::write(base(r).unwrap().join("unrelated-global-metadata.tmp"),vec![b' ';2*1024*1024]).unwrap();assert_eq!(note_bytes(r,&format!("{:032x}",2)).unwrap(),expected);assert_eq!(note_bytes(r,&n.id).unwrap(),0);}

}

/// Host Settings backup of active Notes only. No packages, grants or runtime state.
pub(crate) fn notes_backup(root:&Path,action:&str,expected:&str)->Result<Value>{
 const LIMIT:usize=16*1024*1024;
 #[derive(Serialize,Deserialize)]
 #[serde(deny_unknown_fields)]
 struct Backup{format:String,version:u32,notes:Vec<Note>}
 fn checked(b:&Backup)->Result<()>{
  if b.format!="onklav-notes-backup"||b.version!=1||b.notes.len()>crate::MAX_NOTES{return Err("Choose a supported OnKlav Notes backup".into())}
  let mut ids=BTreeSet::new();for n in &b.notes{crate::validate(n)?;if !ids.insert(&n.id){return Err("Duplicate note in backup".into())}}Ok(())
 }
 if action=="export"{
  let b=Backup{format:"onklav-notes-backup".into(),version:1,notes:note_list(root)?};checked(&b)?;
  let bytes=serde_json::to_vec(&b).map_err(|e|e.to_string())?;
  if bytes.len()>LIMIT{return Err("Notes backup exceeds the 16 MiB limit".into())}
  crate::sdk::atomic(&root.join("notes-backup-export.json"),&bytes)?;
  return Ok(json!({"notes":b.notes.len(),"bytes":bytes.len()}))
 }
 if action!="inspect"&&action!="restore"{return Err("Unsupported Notes backup operation".into())}
 let bytes=crate::sdk::read_bounded(&root.join("notes-backup-import.json"),LIMIT as u64)?;
 let digest=format!("{:x}",Sha256::digest(&bytes));
 if action=="restore"&&expected!=digest{return Err("Notes backup changed after review".into())}
 let b:Backup=serde_json::from_slice(&bytes).map_err(|_|"Choose an OnKlav Notes backup")?;checked(&b)?;
 let mut index=load(root)?;let data=base(root)?;
 let fresh:Vec<&Note>=b.notes.iter().filter(|n|!occupied(&index,&n.id)).collect();
 let existing=crate::list_notes(&data)?;
 let added:usize=fresh.iter().map(|n|serde_json::to_vec(n).map(|v|v.len()).unwrap_or(LIMIT+1)).sum();
 if existing.len()+fresh.len()>crate::MAX_NOTES||index.entries.len()+fresh.len()>MAX_ENTRIES||note_bytes(root,"")?+added as u64>LIMIT as u64{return Err("Restored notes would exceed the library limit".into())}
 if action=="restore"{
  let was_retired=index.notes_retired;
  let restored_parent=if was_retired{Some(app_folder(&mut index,"documents","app.onklav.write","Write"))}else{None};
  // Keep each document durable and retryable; reconcile the shared index once.
  // Calling note_save here would repeatedly scan/rewrite the entire library.
  for n in &fresh{crate::sdk::atomic(&crate::location(&data,&n.id)?,&serde_json::to_vec(n).map_err(|e|e.to_string())?)?;}
  if let Some(parent)=restored_parent{for n in &fresh{let name=unique(&index,&parent,&note_name(n),&n.id);index.entries.insert(n.id.clone(),Entry{id:n.id.clone(),parent:parent.clone(),name,mime:"text/plain".into(),owner:"app.onklav.notes".into(),folder:false,note:true,bytes:(n.title.len()+n.text.len())as u64,from:None,vault:None});}save(root,&index)?;write_migration::migrate(root)?;}else{load(root)?;}
 }
 Ok(json!({"notes":b.notes.len(),"new":fresh.len(),"kept":b.notes.len()-fresh.len(),"sha256":digest}))
}

#[cfg(test)]
mod notes_backup_tests{
 use super::*;
 fn n(id:&str,text:&str)->Note{Note{id:id.repeat(32),title:"A note".into(),text:text.into(),updated:12}}
 fn stage(root:&Path,notes:Value){fs::write(root.join("notes-backup-import.json"),json!({"format":"onklav-notes-backup","version":1,"notes":notes}).to_string()).unwrap();}
 #[test]fn notes_backup_roundtrip_keeps_current_and_trashed_notes_and_is_repeatable(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();note_save(a.path(),&n("a","portable 🐈")).unwrap();note_save(a.path(),&n("b","trash")).unwrap();
  service(a.path(),"library.trash",&json!({"id":"b".repeat(32)})).unwrap();
  assert_eq!(notes_backup(a.path(),"export","").unwrap()["notes"],1);
  fs::copy(a.path().join("notes-backup-export.json"),b.path().join("notes-backup-import.json")).unwrap();
  let review=notes_backup(b.path(),"inspect","").unwrap();assert_eq!(review["new"],1);
  let sha=review["sha256"].as_str().unwrap();assert!(notes_backup(b.path(),"restore","wrong").is_err());notes_backup(b.path(),"restore",sha).unwrap();
  assert_eq!(note_list(b.path()).unwrap()[0].text,"portable 🐈");note_save(b.path(),&n("a","keep current")).unwrap();
  assert_eq!(notes_backup(b.path(),"restore",sha).unwrap()["kept"],1);assert_eq!(note_list(b.path()).unwrap()[0].text,"keep current");
  service(b.path(),"library.trash",&json!({"id":"a".repeat(32)})).unwrap();assert_eq!(notes_backup(b.path(),"restore",sha).unwrap()["kept"],1);assert!(note_list(b.path()).unwrap().is_empty());
 }
 #[test]fn notes_backup_validates_every_record_before_import_and_rejects_symlinks(){
  let d=tempfile::tempdir().unwrap();let r=d.path();note_save(r,&n("a","keep")).unwrap();
  stage(r,json!([n("b","new"),n("c","bad\u{0000}")]));assert!(notes_backup(r,"inspect","").is_err());assert_eq!(note_list(r).unwrap().len(),1);
  stage(r,json!([n("b","new"),n("b","duplicate")]));assert!(notes_backup(r,"inspect","").is_err());
  stage(r,json!([n("b","new")]));let sha=notes_backup(r,"inspect","").unwrap()["sha256"].as_str().unwrap().to_string();stage(r,json!([n("c","changed")]));assert!(notes_backup(r,"restore",&sha).is_err());assert_eq!(note_list(r).unwrap().len(),1);
  fs::remove_file(r.join("notes-backup-import.json")).unwrap();std::os::unix::fs::symlink(r.join("onklav-content/index.json"),r.join("notes-backup-import.json")).unwrap();assert!(notes_backup(r,"inspect","").is_err());
 }
}

#[cfg(test)] mod youtube_download_media_tests {
 use super::*;
 #[test] fn youtube_download_large_media_is_streamed_into_shared_video_folder(){let d=tempfile::tempdir().unwrap();let r=d.path();let staged=r.join("content-import.tmp");let f=fs::File::create(&staged).unwrap();let size=MAX_FILE+1024;f.set_len(size).unwrap();drop(f);let saved=import_staged(r,"video","Clip.mp4","video/mp4","app.onklav.youtube","YouTube",true).unwrap();let resolved=resolve(r,saved["id"].as_str().unwrap()).unwrap();assert_eq!(fs::metadata(resolved["path"].as_str().unwrap()).unwrap().len(),size);assert!(!staged.exists());let list=service(r,"library.list",&json!({"folder":"video"})).unwrap();assert_eq!(list["items"][0]["name"],"Clip.mp4");assert_eq!(saved["parent"],"video");}
 #[test] fn youtube_download_nonmedia_keeps_existing_import_limit(){let d=tempfile::tempdir().unwrap();let r=d.path();let staged=r.join("content-import.tmp");let f=fs::File::create(&staged).unwrap();f.set_len(MAX_FILE+1).unwrap();drop(f);assert!(import_staged(r,"documents","Too big.txt","text/plain","app.onklav.youtube","YouTube",true).is_err());assert!(staged.exists());}
}

#[cfg(test)] mod simple_media_tests {
 use super::*;
 #[test] fn automatic_media_saves_go_directly_to_shared_roots_and_keep_duplicate_names(){let d=tempfile::tempdir().unwrap();let r=d.path();for (mime,folder,name) in [("image/jpeg","images","Photo.jpg"),("audio/mpeg","audio","Audio.mp3"),("video/mp4","video","Video.mp4")] {for _ in 0..2{fs::write(r.join("content-import.tmp"),b"fixture").unwrap();let a=import_staged(r,"downloads",name,mime,"example.app","Example",true).unwrap();assert_eq!(a["parent"],folder);}let list=service(r,"library.list",&json!({"folder":folder})).unwrap();assert_eq!(list["total"],2);assert!(list["items"].as_array().unwrap().iter().all(|v|v["folder"]==false));}}
 #[test] fn existing_automatic_media_is_flattened_but_user_folders_and_ids_survive(){let d=tempfile::tempdir().unwrap();let r=d.path();let mut i=load(r).unwrap();let auto=app_folder(&mut i,"images","app.camera","Camera");let id=insert(r,&mut i,&auto,"Photo.jpg","image/jpeg","app.camera",b"fixture").unwrap()["id"].as_str().unwrap().to_string();i.media_flattened=false;save(r,&i).unwrap();let user=service(r,"library.folder",&json!({"parent":"images","name":"My folder"})).unwrap()["id"].as_str().unwrap().to_string();let after=load(r).unwrap();assert_eq!(after.entries[&id].parent,"images");assert!(!after.entries.contains_key(&auto));assert!(after.entries.contains_key(&user));assert_eq!(fs::read(object(r,&id).unwrap()).unwrap(),b"fixture");}
}
