//! App-scoped offline Walkie preferences and prepared recipients.
//! No guest operation can claim a connection, record audio, or forge delivery.
use crate::Result;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::path::Path;
#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Preferences { auto_play: bool, auto_send: bool, beeps: bool }
impl Default for Preferences {
    fn default() -> Self { Self { auto_play: true, auto_send: true, beeps: true } }
}
#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Prepared { id: String, peer: String, subscription: i32, created: u64, #[serde(default)] label: String }
#[derive(Default, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Store { preferences: Preferences, recent: Vec<Prepared>, selected: Option<String> }
fn valid(s: &Store) -> bool {
    let mut ids = std::collections::BTreeSet::new();
    s.recent.len() <= 80 && s.recent.iter().all(|r| crate::valid_id(&r.id)
        && ids.insert(&r.id) && crate::messaging::phone(&r.peer).is_ok_and(|p| p == r.peer)
        && r.subscription >= -1 && r.label.encode_utf16().count() <= 80 && !r.label.chars().any(char::is_control))
        && s.selected.as_ref().is_none_or(|id| s.recent.iter().any(|r| &r.id == id))
}
fn load(root: &Path) -> Result<Store> {
    let p = root.join("walkie.json");
    if !p.exists() { return Ok(Store::default()); }
    let s: Store = serde_json::from_slice(&crate::sdk::read_bounded(&p, 65536)?)
        .map_err(|_| "Walkie saved state could not be opened")?;
    if !valid(&s) { return Err("Invalid Walkie saved state".into()); }
    Ok(s)
}
fn save(root: &Path, s: &Store) -> Result<()> {
    if !valid(s) { return Err("Invalid Walkie saved state".into()); }
    let p = root.join("walkie.json");
    let b = serde_json::to_vec(s).map_err(|e| e.to_string())?;
    if b.len() > 65536 || crate::sdk::private_size(root, &p)? + b.len() as u64 > 16*1024*1024 {
        return Err("App private storage limit reached".into());
    }
    crate::sdk::atomic(&p, &b)
}
pub(crate) fn selected(root:&Path)->Result<Value>{let s=load(root)?;let r=s.recent.iter().find(|r|Some(&r.id)==s.selected.as_ref()).ok_or("Save a recipient before connecting")?;Ok(json!({"id":r.id,"peer":r.peer,"subscription":r.subscription}))}
fn snapshot(root: &Path, s: &Store) -> Result<Value> {
    Ok(json!({"session":crate::walkie_session::snapshot(root)?,"preferences":s.preferences,"recent":s.recent,"selected":s.selected,
        "connection":"offline","transport_ready":crate::walkie_session::TRANSPORT_READY,"clips":[]}))
}
pub fn service(root: &Path, op: &str, args: &Value) -> Result<Value> {
    if ["walkie.clips","walkie.clip.delete","walkie.clips.clear"].contains(&op){let out=crate::walkie_clips::service(root,op,args)?;if op=="walkie.clip.delete"{crate::walkie_session::clear_hint(root,args["id"].as_str())?}else if op=="walkie.clips.clear"{crate::walkie_session::clear_hint(root,None)?}return Ok(out)}
    let mut s = load(root)?;
    match op {
        "walkie.state" => return snapshot(root,&s),
        "walkie.preferences" => s.preferences = serde_json::from_value(args.clone())
            .map_err(|_| "Provide all three Walkie preference switches")?,
        "walkie.prepare" => {
            let peer = crate::messaging::phone(args["peer"].as_str().ok_or("Choose a recipient")?)?;
            let subscription = match args.get("subscription") { None => -1, Some(v) => v.as_i64().ok_or("Invalid subscription")? };
            if !(-1..=i32::MAX as i64).contains(&subscription) { return Err("Invalid subscription".into()); }
            let label = match args.get("label") { None => None, Some(v) => {
                let name=v.as_str().ok_or("Invalid recipient name")?;
                if name.encode_utf16().count()>80 || name.chars().any(char::is_control) { return Err("Invalid recipient name".into()); }
                Some(name.to_owned())
            }};
            let prior = s.recent.iter().position(|r| r.peer == peer && r.subscription == subscription as i32)
                .map(|at| s.recent.remove(at));
            let mut r = prior.unwrap_or_else(|| Prepared {id:uuid::Uuid::new_v4().simple().to_string(), peer,
                label:String::new(),subscription:subscription as i32,created:std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64});
            if let Some(label)=label { r.label=label; }
            if s.selected.as_deref()!=Some(r.id.as_str()){
                let live=crate::walkie_session::snapshot(root)?;
                if !(live["incoming"]==true&&live["peer"]==r.peer&&live["subscription"]==r.subscription&&["incoming","accept_queued","accept_sending","connected"].contains(&live["phase"].as_str().unwrap_or(""))){crate::walkie_session::end(root,"recipient_changed")?;}
            }
            s.selected = Some(r.id.clone()); s.recent.insert(0,r); s.recent.truncate(80);
        },
        "walkie.select" => {
            let id = args["id"].as_str().ok_or("Choose a saved recipient")?;
            if !s.recent.iter().any(|r| r.id == id) { return Err("Saved recipient no longer exists".into()); }
            if s.selected.as_deref()!=Some(id){crate::walkie_session::end(root,"recipient_changed")?;}
            s.selected = Some(id.into());
        },
        "walkie.end" => {crate::walkie_session::end(root,"cancelled")?;s.selected = None;},
        "walkie.clear_recent" => { crate::walkie_session::end(root,"recipient_changed")?;s.recent.clear(); s.selected = None; },
        "walkie.accept" => return crate::walkie_session::host(root,&json!({"operation":"accept","id":args["id"],"peer":args["peer"],"subscription":args["subscription"]})),
        "walkie.decline" => crate::walkie_session::decline_local(root,args["id"].as_str().ok_or("Choose an invitation")?)?,
        "walkie.connect" => return crate::walkie_session::host(root,&json!({"operation":"begin"})),
        _ => return Err("Unsupported Walkie operation".into()),
    }
    save(root, &s)?; snapshot(root,&s)
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn durable_preferences_recipients_and_offline_only() {
        let a=tempfile::tempdir().unwrap(); let b=tempfile::tempdir().unwrap(); let root=a.path();
        let initial=service(root,"walkie.state",&json!({})).unwrap();
        assert_eq!(initial["preferences"]["auto_play"],true);
        service(root,"walkie.preferences",&json!({"auto_play":false,"auto_send":false,"beeps":true})).unwrap();
        let x=service(root,"walkie.prepare",&json!({"peer":"+1 (555) 010-1234","subscription":2})).unwrap();
        assert_eq!(x["recent"][0]["peer"],"+15550101234");
        assert_eq!(service(root,"walkie.prepare",&json!({"peer":"+15550101234","subscription":2})).unwrap()["selected"],x["selected"]);
        assert_eq!(service(root,"walkie.state",&json!({})).unwrap()["preferences"]["auto_send"],false);
        let before=std::fs::read(root.join("walkie.json")).unwrap();
        for op in ["walkie.connect","walkie.received","walkie.connected","walkie.send"] {
            assert!(service(root,op,&json!({"connected":true})).is_err());
        }
        assert_eq!(before,std::fs::read(root.join("walkie.json")).unwrap());
        assert_eq!(service(b.path(),"walkie.state",&json!({})).unwrap()["recent"],json!([]));
        service(root,"walkie.end",&json!({})).unwrap();
        assert!(service(root,"walkie.state",&json!({})).unwrap()["selected"].is_null());
        service(root,"walkie.select",&json!({"id":x["selected"]})).unwrap();
        service(root,"walkie.clear_recent",&json!({})).unwrap();
        assert!(service(root,"walkie.select",&json!({"id":x["selected"]})).is_err());
    }
    #[test] fn contact_labels_are_bounded_durable_and_old_recipients_still_load() {
        let d=tempfile::tempdir().unwrap();let root=d.path();
        let old=json!({"preferences":{"auto_play":true,"auto_send":true,"beeps":true},"recent":[{"id":"a".repeat(32),"peer":"5551234","subscription":-1,"created":1}],"selected":"a".repeat(32)});
        std::fs::write(root.join("walkie.json"),serde_json::to_vec(&old).unwrap()).unwrap();
        assert_eq!(service(root,"walkie.state",&json!({})).unwrap()["recent"][0]["label"],"");
        let named=service(root,"walkie.prepare",&json!({"peer":"5551234","label":"Charles"})).unwrap();
        assert_eq!(named["selected"],old["selected"]);
        assert_eq!(service(root,"walkie.state",&json!({})).unwrap()["recent"][0]["label"],"Charles");
        // Typing the same number must not erase its saved display name.
        assert_eq!(service(root,"walkie.prepare",&json!({"peer":"5551234"})).unwrap()["recent"][0]["label"],"Charles");
        let before=std::fs::read(root.join("walkie.json")).unwrap();
        for label in [json!("x".repeat(81)),json!("bad\nname"),json!(7)] {
            assert!(service(root,"walkie.prepare",&json!({"peer":"5551234","label":label})).is_err());
        }
        assert_eq!(before,std::fs::read(root.join("walkie.json")).unwrap());
    }
    #[test] fn bounded_state_invalid_inputs_and_corruption_fail_closed() {
        let d=tempfile::tempdir().unwrap(); let r=d.path();
        for args in [json!({"peer":"../evil"}),json!({"peer":"5551234","subscription":2147483648i64}),json!({"peer":"5551234","subscription":-2})] { assert!(service(r,"walkie.prepare",&args).is_err()); }
        assert!(service(r,"walkie.preferences",&json!({"auto_play":true})).is_err());
        for n in 0..85 {service(r,"walkie.prepare",&json!({"peer":format!("555{n:04}")})).unwrap();}
        assert_eq!(service(r,"walkie.state",&json!({})).unwrap()["recent"].as_array().unwrap().len(),80);
        std::fs::write(r.join("walkie.json"),b"{broken").unwrap();
        assert!(service(r,"walkie.state",&json!({})).is_err());
        assert_eq!(std::fs::read(r.join("walkie.json")).unwrap(),b"{broken");
    }
}

#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]struct Portable{preferences:Preferences,recipients:Vec<Recipient>}
#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]struct Recipient{peer:String,label:String}
pub(crate) fn backup_export(root:&Path)->Result<Value>{let s=load(root)?;let mut seen=std::collections::BTreeSet::new();Ok(json!({"preferences":s.preferences,"recipients":s.recent.into_iter().filter(|r|seen.insert(r.peer.clone())).map(|r|Recipient{peer:r.peer,label:r.label}).collect::<Vec<_>>()}))}
fn backup_merge(root:&Path,v:&Value)->Result<(Store,usize,usize)>{let p:Portable=serde_json::from_value(v.clone()).map_err(|_|"Invalid Walkie preferences backup")?;if p.recipients.len()>80{return Err("Too many Walkie recipients".into())}let mut seen=std::collections::BTreeSet::new();for r in &p.recipients{if crate::messaging::phone(&r.peer)?!=r.peer||!seen.insert(&r.peer)||r.label.encode_utf16().count()>80||r.label.chars().any(char::is_control){return Err("Invalid Walkie recipient".into())}}let count=p.recipients.len();let mut s=load(root)?;if !root.join("walkie.json").exists(){s.preferences=p.preferences;}let mut added=0;for r in p.recipients{if !s.recent.iter().any(|old|old.peer==r.peer){s.recent.push(Prepared{id:uuid::Uuid::new_v4().simple().to_string(),peer:r.peer,label:r.label,subscription:-1,created:0});added+=1;}}if !valid(&s){return Err("Restored Walkie recipients exceed the limit".into())}Ok((s,added,count-added))}
pub(crate) fn backup_plan(root:&Path,v:&Value)->Result<(usize,usize,u64)>{let(s,n,k)=backup_merge(root,v)?;let len=serde_json::to_vec(&s).map_err(|e|e.to_string())?.len() as u64;if len>65536{return Err("Walkie preferences exceed limit".into())}Ok((n,k,len))}
pub(crate) fn backup_commit(root:&Path,v:&Value)->Result<()>{let(s,_,_)=backup_merge(root,v)?;save(root,&s)}
