//! Portable single saved Read Aloud draft; no voice identity or playback state.
use crate::{Note,Result};
use serde::{Serialize,Deserialize};
use serde_json::{json,Value};
use std::{collections::BTreeMap,path::Path};
#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]struct Portable{draft:Option<Note>}
fn checked(v:&Value)->Result<Portable>{let p:Portable=serde_json::from_value(v.clone()).map_err(|_|"Invalid Read Aloud backup")?;if let Some(n)=&p.draft{crate::validate(n)?;if n.text.encode_utf16().count()>50000||n.updated==0||n.updated>i64::MAX as u64{return Err("Read Aloud draft exceeds its limits".into())}}Ok(p)}
fn current(root:&Path)->Result<Option<Note>>{
 let last=root.join("last.json");if last.try_exists().map_err(|e|e.to_string())?{
  let id=String::from_utf8(crate::sdk::read_bounded(&last,32)?).map_err(|_|"Read Aloud draft identity needs recovery")?;if !crate::valid_id(&id){return Err("Read Aloud draft identity needs recovery".into())}
  let path=crate::location(root,&id)?;if path.try_exists().map_err(|e|e.to_string())?{return crate::read_note(root,&id).map(Some)}return Ok(None)
 }Ok(crate::list_notes(root)?.into_iter().next())
}
fn proposed(root:&Path,v:&Value)->Result<(Portable,bool)>{
 let p=checked(v)?;let has_current=current(root)?.is_some();
 if let Some(n)=&p.draft{if !has_current{
  let target=crate::location(root,&n.id)?;
  if target.try_exists().map_err(|e|e.to_string())?{let old=crate::read_note(root,&n.id)?;if json!(old)!=json!(n){return Err("Existing Read Aloud data conflicts with this backup".into())}}
  else if crate::list_notes(root)?.len()>=crate::MAX_NOTES{return Err("Read Aloud document limit reached".into())}
  let bytes=serde_json::to_vec(n).map_err(|e|e.to_string())?.len() as u64;
  if crate::sdk::private_size(root,&target)?+bytes+if root.join("last.json").exists(){0}else{32}>16*1024*1024{return Err("Read Aloud storage limit reached".into())}
 }}Ok((p,has_current))
}
pub(crate) fn export(root:&Path)->Result<Value>{let v=json!({"draft":current(root)?});checked(&v)?;Ok(v)}
pub(crate) fn plan(root:&Path,v:&Value)->Result<crate::library_backup::Plan>{let (p,exists)=proposed(root,v)?;let count=usize::from(p.draft.is_some());Ok(crate::library_backup::Plan{new:if exists{0}else{count},kept:if exists{count}else{0},targets:BTreeMap::new()})}
pub(crate) fn commit(host:&Path,root:&Path,v:&Value)->Result<()>{let(p,exists)=proposed(root,v)?;if !exists{if let Some(n)=p.draft{
 crate::library_backup::check(host)?;let target=crate::location(root,&n.id)?;if !target.exists(){crate::sdk::atomic(&target,&serde_json::to_vec(&n).map_err(|e|e.to_string())?)?;}
 crate::library_backup::check(host)?;crate::sdk::atomic(&root.join("last.json"),n.id.as_bytes())?;
 }}Ok(())}
