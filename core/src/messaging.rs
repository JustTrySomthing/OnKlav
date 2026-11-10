//! Private messenger state. Android owns the SMS/MMS provider and carrier APIs;
//! this module owns durable drafts, folders, flags and idempotent send outcomes.
use crate::{Result, plain};
use serde::{Deserialize, Serialize};
use serde_json::{Value,json};
use std::{collections::{BTreeMap,BTreeSet},fs::{self,File,OpenOptions},io::Write,path::Path,sync::Mutex};
static STATE_LOCK: Mutex<()> = Mutex::new(());
#[derive(Default,Serialize,Deserialize,Clone,Debug)]
#[serde(default,deny_unknown_fields)]
pub struct Thread {pub folder:String,pub pinned:bool,pub muted:bool,pub unread:Option<bool>,pub read_at:u64,pub last_incoming_at:u64}
#[derive(Default,Serialize,Deserialize,Clone,Debug)]
#[serde(default,deny_unknown_fields)]
pub struct Flags {pub date:u64,pub thread:u64,pub reminder_title:String,pub reminder_text:String,pub starred:bool,pub locked:bool,pub reaction:String,pub remind_at:u64}
#[derive(Serialize,Deserialize,Clone,Debug)]
#[serde(deny_unknown_fields)]
pub struct Outgoing {pub id:String,pub address:String,pub text:String,pub sub:i64,pub started:u64,pub parts:usize,pub sent:BTreeSet<usize>,pub failed:BTreeSet<usize>,pub delivered:BTreeSet<usize>,pub delivery_failed:bool,pub error:String,pub status:String,#[serde(default)]pub kind:String,#[serde(default)]pub provider_uri:String,#[serde(default)]pub provider_synced:bool}
#[derive(Default,Serialize,Deserialize,Clone,Debug)]
#[serde(default,deny_unknown_fields)]
pub struct State {pub history_merges:BTreeMap<String,HistoryMerge>,pub groups:BTreeMap<String,Group>,pub threads:BTreeMap<String,Thread>,pub messages:BTreeMap<String,Flags>,pub drafts:BTreeMap<String,String>,pub media_drafts:BTreeMap<String,Vec<Value>>,pub blocked:BTreeSet<String>,pub outgoing:BTreeMap<String,Outgoing>}
#[derive(Serialize,Deserialize,Clone,Debug)]
#[serde(deny_unknown_fields)]
pub struct Group { pub id:String, pub name:String, pub members:Vec<String>, pub thread:u64 }
#[derive(Serialize,Deserialize,Clone,Debug,PartialEq)]
#[serde(deny_unknown_fields)]
pub struct HistoryMerge {pub archive_sha:String,pub plan_sha:String,pub post_plan_sha:String,pub flags_imported:usize,pub threads_imported:usize}
fn group_key(g:&Group)->String { if g.thread>0 { g.thread.to_string() } else { format!("group:{}",g.id) } }
fn key(s:&str)->bool { !s.is_empty()&&s.len()<=96&&s.bytes().all(|c|c.is_ascii_alphanumeric()||b":+-_;".contains(&c)) }
pub fn phone(s:&str)->Result<String>{
 if s.len()>64 || s.chars().any(|c|!c.is_ascii_digit()&&!"+ ()-.".contains(c)){return Err("Enter a valid phone number".into())}
 let digits:String=s.chars().filter(|c|c.is_ascii_digit()).collect();
 if !(3..=15).contains(&digits.len()){return Err("Enter a complete phone number".into())}
 Ok(if s.trim().starts_with('+'){format!("+{digits}")}else{digits})
}
fn block_key(s:&str)->String {
 let value=s.trim();if value.chars().any(|c|!c.is_ascii_digit()&&!"+ ()-.".contains(c)){return format!("sender:{}",value.to_lowercase())}
 let d:String=value.chars().filter(|c|c.is_ascii_digit()).collect();if d.len()==11&&d.starts_with('1'){d[1..].into()}else{d}
}
fn sender_key(s:&str)->Result<String>{
 let value=s.trim();if value.is_empty()||value.len()>128||value.chars().any(|c|c.is_control()||";,".contains(c)){return Err("Invalid sender identity".into())}
 if let Some(opaque)=value.strip_prefix("sender:"){if opaque.is_empty(){return Err("Invalid sender identity".into())}return Ok(format!("sender:{}",opaque.to_lowercase()))}
 let result=block_key(value);if !result.starts_with("sender:"){phone(value)?;}Ok(result)
}
// Completed sends leave the bounded hot ledger, but exact receipts remain durable for
// late callbacks and duplicate identities. The Android provider retains visible status.
fn archived(root:&Path,id:&str)->Result<Option<Outgoing>>{
 if !crate::valid_id(id){return Err("Invalid send identity".into())}
 let path=root.join("send-receipts").join(format!("{id}.json"));
 if !path.exists(){return Ok(None)}
 let meta=fs::symlink_metadata(&path).map_err(|e|e.to_string())?;
 if !meta.is_file()||meta.len()>200_000{return Err("Invalid send receipt".into())}
 let o:Outgoing=serde_json::from_slice(&fs::read(path).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
 if o.id!=id{return Err("Send receipt identity mismatch".into())}Ok(Some(o))
}
fn compact_outgoing(root:&Path,s:&mut State)->Result<()>{
 if s.outgoing.len()<900{return Ok(())}
 let mut done:Vec<_>=s.outgoing.values().filter(|o|(o.status=="Delivered"&&o.provider_uri.is_empty())||(o.provider_synced&&matches!(o.status.as_str(),"Delivered"|"Sent"))).map(|o|(o.started,o.id.clone())).collect();done.sort();
 for (_,id) in done {
  if s.outgoing.len()<800{break}
  let dir=root.join("send-receipts");fs::create_dir_all(&dir).map_err(|e|e.to_string())?;
  let tmp=dir.join(format!("{id}.tmp"));let target=dir.join(format!("{id}.json"));
  let bytes=serde_json::to_vec(&s.outgoing[&id]).map_err(|e|e.to_string())?;
  let mut f=File::create(&tmp).map_err(|e|e.to_string())?;f.write_all(&bytes).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;
  fs::rename(tmp,target).map_err(|e|e.to_string())?;File::open(&dir).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;
  s.outgoing.remove(&id);
 }Ok(())
}

fn reconcile_outgoing(o:&mut Outgoing){
 o.sent.extend(o.delivered.iter().copied());
 o.failed.retain(|part|!o.sent.contains(part));
 if o.delivered.len()==o.parts{o.delivery_failed=false;}
 o.status=if o.delivered.len()==o.parts{"Delivered"}else if !o.failed.is_empty(){"Failed"}else if o.delivery_failed{"Delivery failed"}else if o.sent.len()==o.parts{"Sent"}else if o.status=="Status unknown"{"Status unknown"}else{"Sending"}.into();
 if o.failed.is_empty()&&!o.delivery_failed&&o.status!="Status unknown"{o.error.clear();}
}
fn load(root:&Path)->Result<State>{let p=root.join("messenger.json");if !p.exists(){return Ok(State::default())}let meta=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !meta.is_file()||meta.len()>8_000_000{return Err("Invalid messenger state".into())}let mut s:State=serde_json::from_slice(&fs::read(p).map_err(|e|e.to_string())?).map_err(|e|format!("Could not read messenger state: {e}"))?;for o in s.outgoing.values_mut(){reconcile_outgoing(o);}Ok(s)}
fn save(root:&Path,s:&State)->Result<()> {save_guarded(root,s,None)}
fn save_guarded(root:&Path,s:&State,cancel:Option<&Path>)->Result<()> {
 if s.history_merges.len()>100||s.groups.len()>500||s.threads.len()>5000||s.messages.len()>20000||s.drafts.len()>500||s.outgoing.len()>1000||s.blocked.len()>5000{return Err("Messenger state limit reached".into())}
 let bytes=serde_json::to_vec(s).map_err(|e|e.to_string())?;if bytes.len()>8_000_000{return Err("Messenger state is too large".into())}
 fs::create_dir_all(root).map_err(|e|e.to_string())?;let tmp=root.join(format!("chat-{}.tmp",uuid::Uuid::new_v4().simple()));
 let result=(||{let mut f=OpenOptions::new().write(true).create_new(true).open(&tmp).map_err(|e|e.to_string())?;f.write_all(&bytes).and_then(|_|f.sync_all()).map_err(|e|e.to_string())?;if let Some(cancel)=cancel{crate::history_backup::check_cancel(cancel)?;}fs::rename(&tmp,root.join("messenger.json")).map_err(|e|e.to_string())?;File::open(root).and_then(|f|f.sync_all()).map_err(|e|e.to_string())?;Ok(())})();if result.is_err(){let _=fs::remove_file(tmp);}result
}
fn string<'a>(j:&'a Value,k:&str)->Result<&'a str>{j[k].as_str().ok_or_else(||format!("Missing {k}"))}
fn identity(j:&Value)->Result<String>{let s=string(j,"key")?;if !key(s){return Err("Invalid messenger identity".into())}Ok(s.into())}
// Portable metadata only. Never restore in-flight sends, media URIs or permissions.
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct MetadataBackup { format:String, version:u32, groups:BTreeMap<String,Group>, threads:BTreeMap<String,Thread>, messages:BTreeMap<String,Flags>, blocked:BTreeSet<String> }
fn metadata_backup(s:&State)->MetadataBackup { MetadataBackup{format:"onklav-message-metadata".into(),version:1,groups:s.groups.clone(),threads:s.threads.clone(),messages:s.messages.clone(),blocked:s.blocked.clone()} }
fn checked_backup(v:&Value)->Result<MetadataBackup> {
 let b:MetadataBackup=serde_json::from_value(v.clone()).map_err(|_|"Choose an OnKlav app metadata backup")?;
 if b.format!="onklav-message-metadata"||b.version!=1||b.groups.len()>500||b.threads.len()>5000||b.messages.len()>20000||b.blocked.len()>5000{return Err("Unsupported or oversized backup".into())}
 for (id,g) in &b.groups {if !crate::valid_id(id)||g.id!=*id||g.name.trim().is_empty()||plain(&g.name,60)!=g.name||!(2..=256).contains(&g.members.len()){return Err("Invalid saved group".into())}let normalized=g.members.iter().map(|p|phone(p).map(|v|block_key(&v))).collect::<Result<BTreeSet<_>>>()?;if normalized.len()!=g.members.len(){return Err("Duplicate saved group member".into())}}
 for (id,t) in &b.threads {if !key(id)||!["","Inbox","Archive","Trash"].contains(&t.folder.as_str()){return Err("Invalid saved folder".into())}}
 for (id,m) in &b.messages {if !key(id)||plain(&m.reminder_title,100)!=m.reminder_title||plain(&m.reminder_text,500)!=m.reminder_text||!crate::group_wire::valid_reaction(&m.reaction){return Err("Invalid saved message flag".into())}}
 for p in &b.blocked {if sender_key(p)?!=*p{return Err("Invalid blocked number in backup".into())}}
 Ok(b)
}
// Trusted history path only; never an SDK/JSON messenger operation.
fn resolved_receipt(root:&Path,s:&State,job:&str)->Result<Option<HistoryMerge>> {
 let archived=crate::history_completed::find(root,job)?.map(|r|r.receipt);
 let active=s.history_merges.get(job).cloned();
 if active.is_some()&&archived.is_some()&&active!=archived{return Err("Conflicting metadata receipt copies".into())}Ok(active.or(archived))
}
pub(super) fn history_receipt(root:&Path,job:&str)->Result<Option<HistoryMerge>> {let _lock=STATE_LOCK.lock().map_err(|_|"Messenger storage unavailable")?;resolved_receipt(root,&load(root)?,job)}
pub(super) fn compact_history_receipts(root:&Path)->Result<()> {
 let _lock=STATE_LOCK.lock().map_err(|_|"Messenger storage unavailable")?;let mut s=load(root)?;let all=crate::history_completed::records(root)?;
 for (id,row) in &all{if let Some(active)=s.history_merges.get(id){if active!=&row.receipt{return Err("Conflicting metadata receipt copies".into())}}}
 let before=s.history_merges.len();s.history_merges.retain(|id,_|!all.contains_key(id));if before!=s.history_merges.len(){save(root,&s)?;}Ok(())
}
pub(super) fn merge_history(root:&Path,job:&str,mut receipt:HistoryMerge,backup:&Value,flags:&BTreeMap<String,(String,u64)>,threads:&BTreeMap<String,String>,cancel:&Path)->Result<HistoryMerge> {
 let _lock=STATE_LOCK.lock().map_err(|_|"Messenger storage unavailable")?;let mut s=load(root)?;
 if let Some(old)=resolved_receipt(root,&s,job)?{if old.archive_sha!=receipt.archive_sha||old.plan_sha!=receipt.plan_sha{return Err("Restore receipt identity mismatch".into())}return Ok(old.clone())}
 if !crate::valid_id(job)||s.history_merges.len()>=100{return Err("Restore metadata receipt limit reached".into())}
 let b=checked_backup(backup)?;receipt.flags_imported=0;receipt.threads_imported=0;
 for (source,(target,thread)) in flags {if let Some(m)=b.messages.get(source){if !s.messages.contains_key(target){let mut m=m.clone();m.thread=*thread;m.remind_at=0;s.messages.insert(target.clone(),m);receipt.flags_imported+=1;}}}
 for (source,target) in threads {if let Some(t)=b.threads.get(source){if !s.threads.contains_key(target){let mut t=t.clone();t.unread=None;t.read_at=0;t.last_incoming_at=0;s.threads.insert(target.clone(),t);receipt.threads_imported+=1;}}}
 s.history_merges.insert(job.into(),receipt.clone());save_guarded(root,&s,Some(cancel))?;Ok(receipt)
}
pub fn dispatch(root:&Path,j:&Value)->Result<Value>{
 let _guard=STATE_LOCK.lock().map_err(|_|"Messenger storage unavailable")?;let mut s=load(root)?;
 let action=string(j,"action")?;if !matches!(action,"snapshot"|"backup_export"|"backup_check"){compact_outgoing(root,&mut s)?;}
 match action {
  "snapshot"=>return Ok(json!(s)),
  "backup_export"=>return Ok(json!(metadata_backup(&s))),
  "backup_check"=>{let b=checked_backup(&j["backup"])?;return Ok(json!({"groups":b.groups.len(),"threads":b.threads.len(),"messages":b.messages.len(),"blocked":b.blocked.len()}))},
  "backup_restore"=>{let b=checked_backup(&j["backup"])?;
   // Preserve current entries on conflict. Imported reminder timestamps and provider
   // thread IDs cannot be trusted on another phone; never schedule or target them.
   for (id,mut g) in b.groups {g.thread=0;s.groups.entry(id).or_insert(g);}
   // Folder and message flags are restored only on their source phone by the host.
   if j["same_device"]==true {for (id,mut t) in b.threads {t.last_incoming_at=0;s.threads.entry(id).or_insert(t);}}
   if j["same_device"]==true {for (id,mut m) in b.messages {m.remind_at=0;m.thread=0;s.messages.entry(id).or_insert(m);}}
   s.blocked.extend(b.blocked);
  },
  "group_save"=>{
   let id=string(j,"id")?;if !crate::valid_id(id){return Err("Invalid group identity".into())}
   let name=string(j,"name")?.trim();if name.is_empty()||name.encode_utf16().count()>60||plain(name,60)!=name{return Err("Use a group name between 1 and 60 characters".into())}
   let members:Vec<String>=j["members"].as_array().ok_or("Missing members")?.iter().map(|v|phone(v.as_str().unwrap_or(""))).collect::<Result<_>>()?;
   let unique:BTreeSet<String>=members.iter().map(|p|block_key(p)).collect();
   let discovered=j["discovered"]==true;
   let maximum=if discovered{256}else{10};
   if !(2..=maximum).contains(&members.len())||unique.len()!=members.len(){return Err("Choose 2–10 different people (received groups support up to 256)".into())}
   let created=!s.groups.contains_key(id);
   let mut g=Group{id:id.into(),name:name.into(),members,thread:j["thread"].as_u64().ok_or("Missing group thread")?};
   if let Some(old)=s.groups.get(id){
    let previous:BTreeSet<String>=old.members.iter().map(|p|block_key(p)).collect();
    if previous==unique && old.thread>0 {g.thread=old.thread;}
    if old.thread==0 && previous==unique && g.thread>0 {
     if let Some(flags)=s.threads.remove(&group_key(old)){s.threads.entry(group_key(&g)).or_insert(flags);}
    }
   }
   if created&&!discovered {s.threads.entry(group_key(&g)).or_default().folder="Inbox".into();}
   s.groups.insert(id.into(),g);
  },
  "group_remove"=>{let id=string(j,"id")?;if !crate::valid_id(id){return Err("Invalid group identity".into())}s.groups.remove(id);},
  "arrival"=>{
   let k=identity(j)?;let at=j["at"].as_u64().filter(|v|*v>0).ok_or("Missing receive timestamp")?;
   let sender=sender_key(string(j,"address")?)?;let blocked=s.blocked.contains(&sender);
   let t=s.threads.entry(k).or_default();
   // Filing and its receipt share the atomic messenger state write. A recovered older
   // arrival cannot undo a later user Archive/Trash action or a newer receipt.
   if at>t.last_incoming_at {t.last_incoming_at=at;if at>=t.read_at{t.unread=Some(true);t.read_at=at;}if blocked{if t.folder.is_empty()||t.folder=="Inbox"{t.folder="Archive".into();}}else{t.folder="Inbox".into();}}
  },
  "thread"=>{let k=identity(j)?;let t=s.threads.entry(k).or_default();if let Some(folder)=j["folder"].as_str(){if !["Inbox","Archive","Trash"].contains(&folder){return Err("Invalid folder".into())}t.folder=folder.into()}
   if let Some(v)=j["pinned"].as_bool(){t.pinned=v}if let Some(v)=j["muted"].as_bool(){t.muted=v}if let Some(v)=j["unread"].as_bool(){t.unread=Some(v);t.read_at=j["at"].as_u64().ok_or("Missing read timestamp")?;}
  },
  "message"=>{let k=identity(j)?;let m=s.messages.entry(k).or_default();if let Some(v)=j["date"].as_u64(){m.date=v}if let Some(v)=j["thread"].as_u64(){m.thread=v}if let Some(v)=j["reminder_title"].as_str(){m.reminder_title=plain(v,100)}if let Some(v)=j["reminder_text"].as_str(){m.reminder_text=plain(v,500)}if let Some(v)=j["starred"].as_bool(){m.starred=v}if let Some(v)=j["locked"].as_bool(){m.locked=v}
   if let Some(v)=j["reaction"].as_str(){if !crate::group_wire::valid_reaction(v){return Err("Unsupported reaction".into())}m.reaction=v.into()}
   if let Some(v)=j["remind_at"].as_u64(){m.remind_at=v}
  },
  "draft"=>{let k=identity(j)?;let text=string(j,"text")?;if text.encode_utf16().count()>10000||plain(text,10000)!=text{return Err("Draft exceeds its limits".into())}if text.is_empty(){s.drafts.remove(&k);}else{s.drafts.insert(k,text.into());}},
  "draft_media"=>{let k=identity(j)?;let items=j["parts"].as_array().ok_or("Missing attachments")?;if items.len()>10{return Err("Use up to 10 attachments".into())}for p in items{if !crate::valid_id(p["file"].as_str().unwrap_or(""))||p["mime"].as_str().is_none_or(|s|s.len()>100){return Err("Invalid draft attachment".into())}}if items.is_empty(){s.media_drafts.remove(&k);}else{s.media_drafts.insert(k,items.clone());}},
  "blocked"=>{let p=sender_key(string(j,"address")?)?;if j["blocked"].as_bool().ok_or("Missing block state")?{s.blocked.insert(p);}else{s.blocked.remove(&p);}},
  "stage"=>{
   let id=string(j,"id")?;if !crate::valid_id(id)||(s.outgoing.contains_key(id)||archived(root,id)?.is_some()){return Err("Duplicate or invalid send identity".into())}
   let addresses:Vec<String>=string(j,"address")?.split(';').map(phone).collect::<Result<_>>()?;if addresses.len()>10{return Err("Use up to 10 recipients".into())}let address=addresses.join(";");if addresses.iter().any(|a|s.blocked.contains(&block_key(a))){return Err("This recipient is blocked".into())}
   let text=string(j,"text")?;let parts=j["parts"].as_u64().ok_or("Missing part count")? as usize;
   if (text.trim().is_empty()&&j["kind"]!="mms")||text.encode_utf16().count()>10000||plain(text,10000)!=text||!(1..=20).contains(&parts){return Err("Message exceeds its limits".into())}
   s.outgoing.insert(id.into(),Outgoing{id:id.into(),address,text:text.into(),sub:j["sub"].as_i64().unwrap_or(-1),started:j["at"].as_u64().ok_or("Missing send timestamp")?,parts,sent:BTreeSet::new(),failed:BTreeSet::new(),delivered:BTreeSet::new(),delivery_failed:false,error:String::new(),status:"Sending".into(),kind:j["kind"].as_str().unwrap_or("sms").into(),provider_uri:j["provider_uri"].as_str().unwrap_or("").into(),provider_synced:false});
  },
  "provider_synced"=>{let id=string(j,"id")?;if let Some(o)=s.outgoing.get_mut(id){o.provider_synced=true;}},
  "outcome"=>{let id=string(j,"id")?;if !s.outgoing.contains_key(id){if let Some(o)=archived(root,id)?{s.outgoing.insert(id.into(),o);}}let o=s.outgoing.get_mut(id).ok_or("Unknown send identity")?;let part=j["part"].as_u64().ok_or("Missing part")? as usize;if part>=o.parts{return Err("Invalid callback part".into())}
   let ok=j["success"].as_bool().ok_or("Missing callback outcome")?;
   // A positive receipt is evidence for this exact part, even when it arrives
   // after a transient failure. Duplicate/stale negative receipts cannot undo it.
   if j["delivery"].as_bool().unwrap_or(false){
    if ok{o.delivered.insert(part);o.sent.insert(part);o.failed.remove(&part);}
    else if !o.delivered.contains(&part){o.delivery_failed=true;o.error=plain(j["error"].as_str().unwrap_or("Delivery failed"),300);}
   }else if ok{o.sent.insert(part);o.failed.remove(&part);}
   else if !o.sent.contains(&part)&&!o.delivered.contains(&part){o.failed.insert(part);o.error=plain(j["error"].as_str().unwrap_or("Carrier did not accept this message"),300);}
   reconcile_outgoing(o);o.provider_synced=false;
  },
  "recover"=>{let now=j["at"].as_u64().ok_or("Missing timestamp")?;for o in s.outgoing.values_mut(){if o.status=="Sending"&&now.saturating_sub(o.started)>15*60*1000{o.status="Status unknown".into();o.error="No final carrier result. Check the conversation before sending again.".into();}}},
  "remove_message"=>{let k=identity(j)?;if s.messages.get(&k).is_some_and(|f|f.locked){return Err("Unlock this message before deleting it".into())}s.messages.remove(&k);},
  _=>return Err("Unsupported messenger operation".into())
 }
 save(root,&s)?;Ok(json!(s))
}
#[cfg(test)] mod tests {
 use super::*;
 #[test] fn completed_receipts_compact_without_losing_late_callbacks_or_duplicate_guards(){
  let d=tempfile::tempdir().unwrap();let first="0".repeat(32);
  dispatch(d.path(),&json!({"action":"stage","id":first,"address":"5550123","text":"kept","parts":1,"at":1})).unwrap();
  let mut state=load(d.path()).unwrap();let mut sample=state.outgoing[&first].clone();sample.sent.insert(0);sample.delivered.insert(0);reconcile_outgoing(&mut sample);
  state.outgoing.clear();for n in 0..1000{let id=format!("{n:032x}");let mut o=sample.clone();o.id=id.clone();o.started=n;state.outgoing.insert(id,o);}save(d.path(),&state).unwrap();
  let next="f".repeat(32);let result=dispatch(d.path(),&json!({"action":"stage","id":next,"address":"5550123","text":"new","parts":1,"at":1001})).unwrap();
  assert_eq!(result["outgoing"][&next]["status"],"Sending");assert!(load(d.path()).unwrap().outgoing.len()<1000);assert!(archived(d.path(),&first).unwrap().is_some());
  assert!(dispatch(d.path(),&json!({"action":"stage","id":first,"address":"5550123","text":"repeat","parts":1,"at":1002})).is_err());
  let late=dispatch(d.path(),&json!({"action":"outcome","id":first,"part":0,"success":false,"delivery":false})).unwrap();assert_eq!(late["outgoing"][&first]["status"],"Delivered");
 }
 #[test] fn opaque_sender_blocks_are_distinct_and_backup_round_trips(){
  let d=tempfile::tempdir().unwrap();dispatch(d.path(),&json!({"action":"blocked","address":"BANK","blocked":true})).unwrap();
  let state=load(d.path()).unwrap();assert!(state.blocked.contains("sender:bank"));assert!(!state.blocked.contains(&block_key("DELIVERY")));assert_eq!(block_key("+1 (555) 123-4567"),"5551234567");
  let backup=dispatch(d.path(),&json!({"action":"backup_export"})).unwrap();dispatch(d.path(),&json!({"action":"backup_check","backup":backup})).unwrap();assert!(phone("BANK").is_err());
 }
 #[test] fn receive_filing_receipt_is_atomic_and_old_replays_do_not_undo_user_archiving(){
  let d=tempfile::tempdir().unwrap();let arrival=json!({"action":"arrival","key":"7","address":"BANK","at":100});
  dispatch(d.path(),&arrival).unwrap();dispatch(d.path(),&json!({"action":"thread","key":"7","folder":"Trash","pinned":true,"muted":true})).unwrap();
  let replay=dispatch(d.path(),&arrival).unwrap();assert_eq!(replay["threads"]["7"]["folder"],"Trash");
  let fresh=dispatch(d.path(),&json!({"action":"arrival","key":"7","address":"BANK","at":101})).unwrap();assert_eq!(fresh["threads"]["7"]["folder"],"Inbox");assert_eq!(fresh["threads"]["7"]["pinned"],true);assert_eq!(fresh["threads"]["7"]["muted"],true);
  dispatch(d.path(),&json!({"action":"blocked","address":"BANK","blocked":true})).unwrap();let blocked=dispatch(d.path(),&json!({"action":"arrival","key":"7","address":"BANK","at":102})).unwrap();assert_eq!(blocked["threads"]["7"]["folder"],"Archive");
 }
 #[test] fn picker_reactions_persist_and_remove_without_rejecting_supported_emoji(){
 let d=tempfile::tempdir().unwrap();
 for emoji in ["🥰","😊","🤞","👩🏽‍💻","🏳️‍🌈","🇺🇸","1️⃣",""] {
 dispatch(d.path(),&json!({"action":"message","key":"sms:77","reaction":emoji})).unwrap();
 assert_eq!(load(d.path()).unwrap().messages["sms:77"].reaction,emoji);
 }
 for bad in ["script","hello\n"," "] {assert!(dispatch(d.path(),&json!({"action":"message","key":"sms:77","reaction":bad})).is_err());}
 }
 #[test] fn metadata_backup_accepts_supported_picker_reactions_without_losing_flags(){
  let source=tempfile::tempdir().unwrap();let target=tempfile::tempdir().unwrap();
  for (i,emoji) in ["😍","😀","🥰","😊","🤞","👩🏽‍💻","🏳️‍🌈","🇺🇸","1️⃣",""].iter().enumerate(){
   dispatch(source.path(),&json!({"action":"message","key":format!("sms:{i}"),"reaction":emoji,"starred":true,"date":12,"thread":17})).unwrap();
  }
  let backup=dispatch(source.path(),&json!({"action":"backup_export"})).unwrap();
  dispatch(target.path(),&json!({"action":"backup_check","backup":backup})).unwrap();
  dispatch(target.path(),&json!({"action":"backup_restore","backup":backup,"same_device":true})).unwrap();
  assert_eq!(load(source.path()).unwrap().messages.len(),10);
  let restored=dispatch(target.path(),&json!({"action":"backup_export"})).unwrap();
  let mut expected=backup["messages"].clone();for flags in expected.as_object_mut().unwrap().values_mut(){flags["thread"]=json!(0);}
  assert_eq!(expected,restored["messages"]);
  let before=fs::read(target.path().join("messenger.json")).unwrap();
  for invalid in ["hello","script"," ","😀\n"]{
   let mut invalid_backup=backup.clone();invalid_backup["messages"]["sms:0"]["reaction"]=json!(invalid);
   assert!(dispatch(target.path(),&json!({"action":"backup_restore","backup":invalid_backup,"same_device":true})).is_err());
   assert_eq!(before,fs::read(target.path().join("messenger.json")).unwrap());
  }
 }
 #[test] fn metadata_restore_is_checked_atomic_and_never_replays_sends_or_reminders(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();
  dispatch(a.path(),&json!({"action":"message","key":"sms:1","starred":true,"remind_at":999999})).unwrap();
  dispatch(a.path(),&json!({"action":"blocked","address":"5551234567","blocked":true})).unwrap();
  let backup=dispatch(a.path(),&json!({"action":"backup_export"})).unwrap();
  assert!(backup.get("outgoing").is_none());assert!(backup.get("drafts").is_none());
  dispatch(b.path(),&json!({"action":"backup_restore","backup":backup})).unwrap();
  assert!(load(b.path()).unwrap().messages.is_empty());
  dispatch(b.path(),&json!({"action":"backup_restore","backup":backup,"same_device":true})).unwrap();
  let restored=load(b.path()).unwrap();assert!(restored.messages["sms:1"].starred);assert_eq!(restored.messages["sms:1"].remind_at,0);assert!(restored.outgoing.is_empty());
  let mut invalid_block=backup.clone();invalid_block["blocked"]=json!(["+1 (555) 123-4567"]);
  assert!(dispatch(b.path(),&json!({"action":"backup_check","backup":invalid_block})).is_err());
  let mut bad=backup;bad["threads"]=json!({"../bad":{"folder":"Elsewhere"}});
  let before=fs::read(b.path().join("messenger.json")).unwrap();
  assert!(dispatch(b.path(),&json!({"action":"backup_restore","backup":bad})).is_err());assert_eq!(before,fs::read(b.path().join("messenger.json")).unwrap());
 }

 #[test] fn drafts_folders_and_flags_survive_reload(){let d=tempfile::tempdir().unwrap();dispatch(d.path(),&json!({"action":"thread","key":"12","folder":"Archive","pinned":true})).unwrap();dispatch(d.path(),&json!({"action":"draft","key":"12","text":"Hello 🐈"})).unwrap();dispatch(d.path(),&json!({"action":"message","key":"sms:2","starred":true,"locked":true})).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.drafts["12"],"Hello 🐈");assert!(s.threads["12"].pinned);assert!(dispatch(d.path(),&json!({"action":"remove_message","key":"sms:2"})).is_err());}
 #[test] fn multipart_callbacks_are_idempotent_and_late_success_reconciles(){let d=tempfile::tempdir().unwrap();let id="a".repeat(32);dispatch(d.path(),&json!({"action":"stage","id":id,"address":"+1 (555) 555-0123","text":"message","parts":2,"at":1})).unwrap();let callback=json!({"action":"outcome","id":id,"part":0,"success":true});dispatch(d.path(),&callback).unwrap();dispatch(d.path(),&callback).unwrap();assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Sending");dispatch(d.path(),&json!({"action":"outcome","id":id,"part":1,"success":false})).unwrap();assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Failed");dispatch(d.path(),&json!({"action":"outcome","id":id,"part":1,"success":true})).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.outgoing[&id].status,"Sent");assert!(s.outgoing[&id].error.is_empty());}
 #[test] fn delivery_and_unknown_result_never_imply_success(){let d=tempfile::tempdir().unwrap();let id="b".repeat(32);dispatch(d.path(),&json!({"action":"stage","id":id,"address":"5550123","text":"message","parts":1,"at":1})).unwrap();dispatch(d.path(),&json!({"action":"recover","at":1000000})).unwrap();assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Status unknown");dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":true,"delivery":true})).unwrap();assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Delivered");}
 #[test] fn delayed_delivery_overrides_failure_and_survives_reopen(){
  let d=tempfile::tempdir().unwrap();let id="c".repeat(32);
  dispatch(d.path(),&json!({"action":"stage","id":id,"address":"5550123","text":"message","parts":1,"at":1})).unwrap();
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":false,"error":"No service"})).unwrap();
  assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Failed");
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":true,"delivery":true})).unwrap();
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":false,"error":"stale failure"})).unwrap();
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":false,"delivery":true})).unwrap();
  let s=dispatch(d.path(),&json!({"action":"snapshot"})).unwrap();
  assert_eq!(s["outgoing"][&id]["status"],"Delivered");assert_eq!(s["outgoing"][&id]["error"],"");
 }
 #[test] fn one_delivered_part_does_not_hide_another_failed_part(){
  let d=tempfile::tempdir().unwrap();let id="d".repeat(32);
  dispatch(d.path(),&json!({"action":"stage","id":id,"address":"5550123","text":"message","parts":2,"at":1})).unwrap();
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":1,"success":false,"error":"No service"})).unwrap();
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":true,"delivery":true})).unwrap();
  assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Failed");
 }
 #[test] fn complete_delivery_clears_earlier_delivery_failure(){
  let d=tempfile::tempdir().unwrap();let id="e".repeat(32);
  dispatch(d.path(),&json!({"action":"stage","id":id,"address":"5550123","text":"message","parts":1,"at":1})).unwrap();
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":false,"delivery":true})).unwrap();
  assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Delivery failed");
  dispatch(d.path(),&json!({"action":"outcome","id":id,"part":0,"success":true,"delivery":true})).unwrap();
  let s=load(d.path()).unwrap();assert_eq!(s.outgoing[&id].status,"Delivered");assert!(s.outgoing[&id].error.is_empty());
 }
 #[test] fn reopening_repairs_legacy_failure_when_positive_receipts_were_saved(){
  let d=tempfile::tempdir().unwrap();let id="f".repeat(32);
  dispatch(d.path(),&json!({"action":"stage","id":id,"address":"5550123","text":"message","parts":1,"at":1})).unwrap();
  let mut s=load(d.path()).unwrap();let o=s.outgoing.get_mut(&id).unwrap();
  o.sent.insert(0);o.failed.insert(0);o.status="Failed".into();o.error="old failure".into();save(d.path(),&s).unwrap();
  let s=dispatch(d.path(),&json!({"action":"snapshot"})).unwrap();
  assert_eq!(s["outgoing"][&id]["status"],"Sent");assert_eq!(s["outgoing"][&id]["error"],"");
  dispatch(d.path(),&json!({"action":"recover","at":1000000})).unwrap();
  assert_eq!(load(d.path()).unwrap().outgoing[&id].status,"Sent");
 }
 #[test] fn invalid_updates_preserve_previous_state(){let d=tempfile::tempdir().unwrap();dispatch(d.path(),&json!({"action":"draft","key":"5","text":"kept"})).unwrap();for j in [json!({"action":"draft","key":"../x","text":"bad"}),json!({"action":"thread","key":"5","folder":"elsewhere"}),json!({"action":"message","key":"sms:2","reaction":"script"})]{assert!(dispatch(d.path(),&j).is_err())}assert_eq!(load(d.path()).unwrap().drafts["5"],"kept");}
}

#[cfg(test)] mod group_tests {
 use super::*;
 fn group(id:&str, members:Value, thread:u64)->Value {json!({"action":"group_save","id":id,"name":"Family","members":members,"thread":thread})}
 #[test] fn groups_reload_and_rename_keep_thread(){let d=tempfile::tempdir().unwrap();let id="c".repeat(32);let mut j=group(&id,json!(["+1 (555) 555-0101","5555550102"]),9);dispatch(d.path(),&j).unwrap();j["name"]=json!("Friends");j["thread"]=json!(44);dispatch(d.path(),&j).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.groups[&id].name,"Friends");assert_eq!(s.groups[&id].thread,9);assert_eq!(s.groups[&id].members[0],"+15555550101");}
 #[test] fn membership_change_keeps_old_conversation_flags(){let d=tempfile::tempdir().unwrap();let id="d".repeat(32);dispatch(d.path(),&group(&id,json!(["5550101","5550102"]),9)).unwrap();dispatch(d.path(),&json!({"action":"thread","key":"9","folder":"Archive","muted":true})).unwrap();dispatch(d.path(),&group(&id,json!(["5550101","5550103"]),10)).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.groups[&id].thread,10);assert!(s.threads["9"].muted);assert!(!s.threads.contains_key("10"));}
 #[test] fn invalid_group_changes_are_atomic(){let d=tempfile::tempdir().unwrap();let id="e".repeat(32);dispatch(d.path(),&group(&id,json!(["5550101","5550102"]),9)).unwrap();for members in [json!(["5550101"]),json!(["+15555550101","5555550101"]),json!(["bad","5550102"])]{assert!(dispatch(d.path(),&group(&id,members,9)).is_err());}let mut j=group(&id,json!(["5550101","5550102"]),9);j["name"]=json!(" ");assert!(dispatch(d.path(),&j).is_err());assert_eq!(load(d.path()).unwrap().groups[&id].name,"Family");}
 #[test] fn binding_transfers_controls_and_delete_preserves_messages(){let d=tempfile::tempdir().unwrap();let id="f".repeat(32);dispatch(d.path(),&group(&id,json!(["5550101","5550102"]),0)).unwrap();dispatch(d.path(),&json!({"action":"thread","key":format!("group:{id}"),"folder":"Archive","pinned":true})).unwrap();dispatch(d.path(),&group(&id,json!(["5550101","5550102"]),9)).unwrap();dispatch(d.path(),&json!({"action":"draft","key":"9","text":"keep"})).unwrap();dispatch(d.path(),&json!({"action":"group_remove","id":id})).unwrap();let s=load(d.path()).unwrap();assert!(s.groups.is_empty());assert!(s.threads["9"].pinned);assert_eq!(s.threads["9"].folder,"Archive");assert_eq!(s.drafts["9"],"keep");}
}
#[cfg(test)]mod history_merge_tests {
 use super::*;
 fn receipt()->HistoryMerge{HistoryMerge{archive_sha:"a".repeat(64),plan_sha:"b".repeat(64),post_plan_sha:"c".repeat(64),flags_imported:0,threads_imported:0}}
 fn setup()->(tempfile::TempDir,Value,BTreeMap<String,(String,u64)>,BTreeMap<String,String>){let t=tempfile::tempdir().unwrap();let source=tempfile::tempdir().unwrap();dispatch(source.path(),&json!({"action":"message","key":"sms:1","date":1,"starred":true,"thread":5,"remind_at":999})).unwrap();dispatch(source.path(),&json!({"action":"thread","key":"5","folder":"Archive","unread":true,"at":10})).unwrap();let b=dispatch(source.path(),&json!({"action":"backup_export"})).unwrap();(t,b,BTreeMap::from([("sms:1".into(),("sms:90".into(),70))]),BTreeMap::from([("5".into(),"70".into())]))}
 #[test]fn metadata_and_receipt_are_atomic_and_replay_never_resurrects_deleted_flags(){let(t,b,f,threads)=setup();let job="1".repeat(32);merge_history(t.path(),&job,receipt(),&b,&f,&threads,t.path()).unwrap();let state=load(t.path()).unwrap();assert!(state.messages["sms:90"].starred);assert_eq!(state.messages["sms:90"].thread,70);assert_eq!(state.messages["sms:90"].remind_at,0);assert_eq!(state.threads["70"].unread,None);assert_eq!(state.threads["70"].read_at,0);dispatch(t.path(),&json!({"action":"remove_message","key":"sms:90"})).unwrap();merge_history(t.path(),&job,receipt(),&b,&f,&threads,t.path()).unwrap();assert!(!load(t.path()).unwrap().messages.contains_key("sms:90"));assert_eq!(load(t.path()).unwrap().history_merges.len(),1);}
 #[test]fn cancellation_keeps_state_and_completion_receipt_unchanged(){let(t,b,f,threads)=setup();dispatch(t.path(),&json!({"action":"draft","key":"70","text":"keep"})).unwrap();let before=fs::read(t.path().join("messenger.json")).unwrap();fs::write(t.path().join("cancel"),"").unwrap();assert!(merge_history(t.path(),&"2".repeat(32),receipt(),&b,&f,&threads,t.path()).is_err());assert_eq!(before,fs::read(t.path().join("messenger.json")).unwrap());assert!(load(t.path()).unwrap().history_merges.is_empty());}
 #[test]fn current_metadata_and_private_drafts_win_and_receipts_are_not_portable(){let(t,b,f,threads)=setup();dispatch(t.path(),&json!({"action":"message","key":"sms:90","starred":false,"remind_at":123})).unwrap();dispatch(t.path(),&json!({"action":"thread","key":"70","folder":"Inbox"})).unwrap();dispatch(t.path(),&json!({"action":"draft","key":"70","text":"keep"})).unwrap();let r=merge_history(t.path(),&"3".repeat(32),receipt(),&b,&f,&threads,t.path()).unwrap();assert_eq!(r.flags_imported,0);assert_eq!(r.threads_imported,0);let state=load(t.path()).unwrap();assert!(!state.messages["sms:90"].starred);assert_eq!(state.messages["sms:90"].remind_at,123);assert_eq!(state.drafts["70"],"keep");assert!(dispatch(t.path(),&json!({"action":"backup_export"})).unwrap().get("history_merges").is_none());}
 #[test] fn new_group_resurfaces_existing_thread_but_discovery_and_rename_preserve_filing(){
  let d=tempfile::tempdir().unwrap();let id="a".repeat(32);
  dispatch(d.path(),&json!({"action":"thread","key":"99","folder":"Trash","pinned":true,"muted":true})).unwrap();
  dispatch(d.path(),&json!({"action":"group_save","id":id,"name":"Family","members":["5550101","5550102"],"thread":99})).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.threads["99"].folder,"Inbox");assert!(s.threads["99"].pinned&&s.threads["99"].muted);
  dispatch(d.path(),&json!({"action":"thread","key":"99","folder":"Archive"})).unwrap();dispatch(d.path(),&json!({"action":"group_save","id":id,"name":"Family","members":["5550101","5550102"],"thread":99})).unwrap();assert_eq!(load(d.path()).unwrap().threads["99"].folder,"Archive");
  let other="b".repeat(32);dispatch(d.path(),&json!({"action":"group_save","id":other,"name":"Received","members":["5550101","5550102"],"thread":99,"discovered":true})).unwrap();assert_eq!(load(d.path()).unwrap().threads["99"].folder,"Archive");
 }
 #[test] fn arrival_sets_unread_once_without_overriding_a_later_read_on_replay(){
  let d=tempfile::tempdir().unwrap();let arrival=json!({"action":"arrival","key":"9","address":"5550101","at":100});
  dispatch(d.path(),&arrival).unwrap();assert_eq!(load(d.path()).unwrap().threads["9"].unread,Some(true));
  dispatch(d.path(),&json!({"action":"thread","key":"9","unread":false,"at":110,"folder":"Archive"})).unwrap();dispatch(d.path(),&arrival).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.threads["9"].unread,Some(false));assert_eq!(s.threads["9"].folder,"Archive");
  dispatch(d.path(),&json!({"action":"arrival","key":"9","address":"5550101","at":120})).unwrap();let s=load(d.path()).unwrap();assert_eq!(s.threads["9"].unread,Some(true));assert_eq!(s.threads["9"].folder,"Inbox");
 }

}
