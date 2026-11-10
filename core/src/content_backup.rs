use super::*;
use crate::library_backup::{Blob,Plan};
const PORTABLE_ROOTS:[&str;6]=["downloads","images","documents","audio","video","notes"];
fn records(v:&Value)->Result<Vec<Entry>>{
 let entries:Vec<Entry>=serde_json::from_value(v.clone()).map_err(|_|"Invalid Files backup records")?;
 if entries.len()>MAX_ENTRIES{return Err("Too many Files backup records".into())}
 let mut ids=BTreeSet::new();for e in &entries{
  valid_name(&e.name)?;vault::validate_entry(e)?;if !crate::valid_id(&e.id)||!ids.insert(e.id.clone())||e.note||e.from.is_some()||e.mime.len()>200||e.mime.chars().any(char::is_control)||e.owner.len()>200||e.owner.chars().any(char::is_control)||e.bytes>if e.mime.starts_with("audio/")||e.mime.starts_with("video/"){2*1024*1024*1024}else{MAX_FILE}||(e.folder&&((e.vault.is_none()&&e.bytes!=0)||e.mime!="inode/directory")){return Err("Invalid Files backup record".into())}
 }
 let mut reserved=BTreeSet::new();for e in &entries{if let Some(ids)=&e.vault{for id in ids{if !reserved.insert(id){return Err("Overlapping encrypted backup identities".into())}}}}
 for e in &entries{if let Some(ids)=&e.vault{if entries.iter().any(|other|other.id!=e.id&&ids.contains(&other.id)){return Err("Encrypted backup contains exposed member records".into())}}}
 let map:BTreeMap<_,_>=entries.iter().map(|e|(e.id.as_str(),e)).collect();
 for e in &entries{let mut parent=e.parent.as_str();let mut seen=BTreeSet::new();for _ in 0..64{
  if PORTABLE_ROOTS.contains(&parent){break}if !seen.insert(parent){return Err("Cyclic backup folder".into())}
  parent=map.get(parent).filter(|p|p.folder).ok_or("Missing backup folder")?.parent.as_str();
 }if !PORTABLE_ROOTS.contains(&parent){return Err("Backup folders are too deep".into())}}
 Ok(entries)
}
pub(crate) fn export(root:&Path)->Result<(Value,BTreeMap<String,PathBuf>)>{
 let index=load(root)?;if index.entries.values().any(|e|e.vault.is_some()&&(vault::opened(root,&e.id)||e.bytes==0)){return Err("Lock encrypted folders before backing up Files".into())}let mut entries=Vec::new();let mut paths=BTreeMap::new();
 for e in index.entries.values(){if e.vault.is_none()&&vault::scope(&index,&e.id).is_some(){continue}if protected(&e.id)||e.note||trashed(&index,&e.id)?{continue}let mut e=e.clone();e.from=None;
  if !e.folder||e.vault.is_some(){let p=if e.vault.is_some(){vault::archive_path(root,&e.id)?}else{object(root,&e.id)?};let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !m.is_file()||m.len()!=e.bytes{return Err("Files content is missing or changed".into())}paths.insert(format!("files/{}",e.id),p);}
  entries.push(e);
 }let value=serde_json::to_value(entries).map_err(|e|e.to_string())?;records(&value)?;Ok((value,paths))
}
fn proposed(root:&Path,v:&Value,blobs:&BTreeMap<String,Blob>)->Result<(Index,Plan)>{
 let entries=records(v)?;let mut index=load(root)?;let expected:BTreeSet<_>=entries.iter().filter(|e|!e.folder||e.vault.is_some()).map(|e|format!("files/{}",e.id)).collect();
 if expected!=blobs.keys().cloned().collect(){return Err("Files backup payload mismatch".into())}
 for e in &entries{if (!e.folder||e.vault.is_some())&&blobs[&format!("files/{}",e.id)].bytes!=e.bytes{return Err("Files backup size mismatch".into())}}
 let mut pending:Vec<_>=entries.iter().filter(|e|!occupied(&index,&e.id)).cloned().collect();let count=pending.len();
 if super::count(&index)+count>MAX_ENTRIES{return Err("Restored Files would exceed the library limit".into())}
 let mut targets=BTreeMap::new();
 for entry in &pending{if let Some(ids)=&entry.vault{for id in ids{if occupied(&index,id){return Err("An encrypted backup conflicts with existing Files entries".into())}else{let uuid=uuid::Uuid::parse_str(id).map_err(|_|"Invalid encrypted backup identity")?;let private=root.join(format!("write/documents/{uuid}.json"));if private.exists()||private.with_extension("json.bak").exists(){return Err("An encrypted backup conflicts with an existing Write document".into())}}}}}
 while !pending.is_empty(){let before=pending.len();let mut next=Vec::new();for mut e in pending{
  if e.parent=="notes"&&!index.entries.contains_key("notes"){e.parent=app_folder(&mut index,"documents","app.onklav.write","Write");}if !index.entries.contains_key(&e.parent){next.push(e);continue}folder(&index,&e.parent)?;
  e.name=unique(&index,&e.parent,&e.name,"");valid_name(&e.name)?;e.owner="user".into();
  if !e.folder||e.vault.is_some(){targets.insert(format!("files/{}",e.id),if e.vault.is_some(){vault::archive_path(root,&e.id)?}else{object(root,&e.id)?});}
  index.entries.insert(e.id.clone(),e);
 }if next.len()==before{return Err("Conflicting backup folder structure".into())}pending=next;}
 if super::count(&index)>MAX_ENTRIES{return Err("Restored folders exceed the library limit".into())}
 for e in index.entries.values(){trashed(&index,&e.id)?;}
 Ok((index,Plan{new:count,kept:entries.len()-count,targets}))
}
pub(crate) fn plan(root:&Path,v:&Value,blobs:&BTreeMap<String,Blob>)->Result<Plan>{Ok(proposed(root,v,blobs)?.1)}
pub(crate) fn commit(root:&Path,v:&Value,blobs:&BTreeMap<String,Blob>)->Result<()>{let (index,_)=proposed(root,v,blobs)?;crate::library_backup::check(root)?;save(root,&index)}
pub(crate) fn combined_capacity(root:&Path,added:usize)->Result<()>{if super::count(&load(root)?)+added>MAX_ENTRIES{Err("Combined Notes and Files exceed the library limit".into())}else{Ok(())}}
