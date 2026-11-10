//! Private, atomic voice archive. Guest UI can list/delete, never forge outcomes.
use crate::Result;
use serde::{Deserialize, Serialize};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::path::Path;
const DAY:u64=86_400_000;
#[derive(Clone,Debug,PartialEq,Eq,Serialize,Deserialize)]
#[serde(rename_all="snake_case")]
enum State {Queued,Sending,Carrier,Delivered,Heard,Failed,Uncertain,Received}
#[derive(Clone,Serialize,Deserialize,PartialEq,Eq)]
#[serde(deny_unknown_fields)]
struct Binding {id:String,session:String,peer:String,subscription:i32,outgoing:bool}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Clip {binding:Binding,sequence:u32,mime:String,audio:String,digest:String,millis:u32,
    created:u64,heard_at:Option<u64>,state:State,attempt:Option<String>,sending_at:Option<u64>,#[serde(default)] restored:bool}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Seen {binding:Binding,digest:String,created:u64}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Archive {version:u32,clips:Vec<Clip>,seen:Vec<Seen>}
impl Default for Archive {fn default()->Self{Self{version:1,clips:vec![],seen:vec![]}}}
fn now()->u64{std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64}
fn hex(b:&[u8])->String{b.iter().map(|x|format!("{x:02x}")).collect()}
fn bytes(s:&str)->Result<Vec<u8>> {
    if s.len()>131072||s.len()%2!=0||!s.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return Err("Invalid saved voice bytes".into())}
    (0..s.len()).step_by(2).map(|i|u8::from_str_radix(&s[i..i+2],16).map_err(|_|"Invalid voice bytes".into())).collect()
}
fn binding(v:&Value)->Result<Binding>{let b:Binding=serde_json::from_value(v.clone()).map_err(|_|"Invalid clip identity")?;
    if !crate::valid_id(&b.id)||!crate::valid_id(&b.session)||b.subscription< -1||crate::messaging::phone(&b.peer)?!=b.peer{return Err("Invalid clip binding".into())}Ok(b)}
fn validate(a:&Archive)->Result<()> {
    if a.version!=1||a.clips.len()>100||a.seen.len()>1000{return Err("Invalid voice archive limits".into())}
    let mut ids=std::collections::BTreeSet::new();
    for s in &a.seen {binding(&json!(s.binding))?;if !ids.insert(&s.binding.id)||s.digest.len()!=64||!s.digest.bytes().all(|c|c.is_ascii_hexdigit()){return Err("Invalid duplicate ledger".into())}}
    ids.clear();
    for c in &a.clips {let raw=bytes(&c.audio)?;let ms=crate::walkie::audio_duration(&raw,&c.mime)?;
        let proof=a.seen.iter().any(|s|s.binding==c.binding&&s.digest==c.digest);
        if !ids.insert(&c.binding.id)||!proof||ms!=c.millis||ms<300||ms>if c.binding.outgoing{12000}else{13000}
            ||hex(&Sha256::digest(&raw))!=c.digest||c.sequence>1_000_000
            ||c.attempt.as_ref().is_some_and(|s|!crate::valid_id(s))
            ||(c.state==State::Heard)!=c.heard_at.is_some()
            ||(c.binding.outgoing && c.state==State::Received)
            ||(!c.binding.outgoing && !matches!(c.state,State::Received|State::Heard))
            ||(c.binding.outgoing && !matches!(c.state,State::Queued) && c.attempt.is_none())
            ||(c.state==State::Sending && c.sending_at.is_none()) {return Err("Invalid voice archive record".into())}
    }Ok(())
}
fn load(root:&Path)->Result<Archive>{let p=root.join("walkie-clips.json");if !p.exists(){return Ok(Archive::default())}
    let a:Archive=serde_json::from_slice(&crate::sdk::read_bounded(&p,15*1024*1024)?).map_err(|_|"Voice archive could not be opened")?;validate(&a)?;Ok(a)}
fn save(root:&Path,a:&Archive)->Result<()> {validate(a)?;let b=serde_json::to_vec(a).map_err(|e|e.to_string())?;let p=root.join("walkie-clips.json");
    if b.len()>15*1024*1024||crate::sdk::private_size(root,&p)?+b.len() as u64>16*1024*1024{return Err("App private storage limit reached".into())}crate::sdk::atomic(&p,&b)}
fn prune(a:&mut Archive,t:u64)->bool {
    let before=(a.clips.len(),a.seen.len());let mut changed=false;
    a.clips.retain(|c|c.restored||(t.saturating_sub(c.created)<7*DAY&&c.heard_at.is_none_or(|h|t.saturating_sub(h)<DAY)));
    for c in &mut a.clips {if c.state==State::Sending&&c.sending_at.is_some_and(|at|t.saturating_sub(at)>=90_000){c.state=State::Uncertain;changed=true;}}
    // Keep proofs for retained clips, even if the wall clock moved backwards.
    a.seen.retain(|s|t.saturating_sub(s.created)<14*DAY||a.clips.iter().any(|c|c.binding.id==s.binding.id));
    changed||before!=(a.clips.len(),a.seen.len())
}
fn summaries(a:&Archive)->Value {json!(a.clips.iter().rev().map(|c|json!({"id":c.binding.id,"peer":c.binding.peer,"session":c.binding.session,
    "subscription":c.binding.subscription,"outgoing":c.binding.outgoing,"sequence":c.sequence,"millis":c.millis,"created":c.created,"state":if c.restored{json!("restored")}else{json!(c.state)},
    "retry_warning":c.state==State::Uncertain})).collect::<Vec<_>>())}
/// Trusted component lookup; audio is never returned through a guest service.
pub fn component(root:&Path,id:&str,audio:bool)->Result<Value>{
 let mut a=load(root)?;let changed=prune(&mut a,now());let c=a.clips.iter().find(|c|c.binding.id==id).ok_or("Voice clip no longer exists")?;
 let out=if audio{json!({"binding":c.binding,"restored":c.restored,"state":c.state,"sequence":c.sequence,"mime":c.mime,"bytes":bytes(&c.audio)?})}else{json!({"binding":c.binding,"restored":c.restored,"state":c.state,"sequence":c.sequence,"attempt":c.attempt})};if changed{save(root,&a)?;}Ok(out)
}
/// UI services are deliberately a separate entry point from trusted host writes.
pub fn service(root:&Path,op:&str,args:&Value)->Result<Value>{service_at(root,op,args,now())}
fn service_at(root:&Path,op:&str,args:&Value,t:u64)->Result<Value>{let mut a=load(root)?;let changed=prune(&mut a,t);
    match op {
        "walkie.clips"=>{},
        "walkie.clip.delete"=>{let id=args["id"].as_str().filter(|s|crate::valid_id(s)).ok_or("Invalid clip identity")?;a.clips.retain(|c|c.binding.id!=id);},
        "walkie.clips.clear"=>a.clips.clear(),
        _=>return Err("Unsupported voice archive UI operation".into()),
    }
    let out=summaries(&a);if changed||op!="walkie.clips"{save(root,&a)?;}Ok(out)
}
/// Only called by sdk::dispatch's trusted host action, never by a Wasm effect.
pub fn host(root:&Path,args:&Value)->Result<Value>{host_at(root,args,now())}
fn host_at(root:&Path,args:&Value,t:u64)->Result<Value>{let mut a=load(root)?;prune(&mut a,t);
    let b=binding(&args["binding"])?;let op=args["operation"].as_str().ok_or("Missing archive operation")?;
    if op=="ingest" {
        let raw=args["bytes"].as_array().filter(|v|v.len()<=65536).ok_or("Invalid audio bytes")?.iter()
            .map(|v|v.as_u64().filter(|n|*n<=255).map(|n|n as u8).ok_or_else(||"Invalid audio byte".to_string())).collect::<Result<Vec<_>>>()?;
        let mime=args["mime"].as_str().ok_or("Missing audio type")?;
        let millis=crate::walkie::audio_duration(&raw,mime)?;
        if millis<300||millis>if b.outgoing{12000}else{13000}{return Err("Voice clip duration is outside the limit".into())}
        let sequence=args["sequence"].as_u64().filter(|n|*n<=1_000_000).ok_or("Invalid clip sequence")? as u32;
        let digest=hex(&Sha256::digest(&raw));
        if let Some(old)=a.seen.iter().find(|s|s.binding.id==b.id){if old.binding!=b||old.digest!=digest{return Err("Clip identity already bound to different audio or sender".into())}save(root,&a)?;return Ok(json!({"duplicate":true}));}
        if a.clips.len()==100 {a.clips.remove(0);}
        while a.seen.len()>=1000 {let at=a.seen.iter().position(|s|!a.clips.iter().any(|c|c.binding.id==s.binding.id)).ok_or("Duplicate ledger full")?;a.seen.remove(at);}
        a.seen.push(Seen{binding:b.clone(),digest:digest.clone(),created:t});
        let state=if b.outgoing{State::Queued}else{State::Received};
        a.clips.push(Clip{binding:b,sequence,mime:mime.into(),audio:hex(&raw),digest,millis,created:t,heard_at:None,state,attempt:None,sending_at:None,restored:false});
        save(root,&a)?;return Ok(json!({"duplicate":false}));
    }
    let c=a.clips.iter_mut().find(|c|c.binding.id==b.id).ok_or("Voice clip no longer exists")?;
    if c.binding!=b{return Err("Voice callback peer/session/subscription mismatch".into())}
    if c.restored&&!matches!(op,"audio"|"played"){return Err("Restored clips are playback only".into())}
    let mut result=json!({"updated":true});
    match op {
        "audio"=>{let out=json!({"mime":c.mime,"bytes":bytes(&c.audio)?});save(root,&a)?;return Ok(out)},
        "attempt" if b.outgoing&&matches!(c.state,State::Queued|State::Failed|State::Uncertain)=>{
            let token=uuid::Uuid::new_v4().simple().to_string();c.attempt=Some(token.clone());c.sending_at=Some(t);c.state=State::Sending;result=json!({"attempt":token});
        },
        "carrier.result" if b.outgoing=>{
            if args["attempt"].as_str()!=c.attempt.as_deref()||c.attempt.is_none(){return Err("Stale voice carrier attempt".into())}
            let state=match args["result"].as_str(){Some("carrier")=>State::Carrier,Some("failed")=>State::Failed,Some("uncertain")=>State::Uncertain,_=>return Err("Invalid carrier outcome".into())};
            if matches!(c.state,State::Sending|State::Uncertain){c.state=state}else{result=json!({"updated":false});}
        },
        "receipt" if b.outgoing&&c.attempt.is_some()=>{
            match args["result"].as_str(){Some("heard")=>{c.state=State::Heard;if c.heard_at.is_none(){c.heard_at=Some(t);}},Some("delivered") if c.state!=State::Heard=>c.state=State::Delivered,Some("delivered")=>{},_=>return Err("Invalid voice receipt".into())}
        },
        "played" if !b.outgoing=>{c.state=State::Heard;if c.heard_at.is_none(){c.heard_at=Some(t);}},
        _=>return Err("Invalid voice archive transition".into()),
    }
    save(root,&a)?;Ok(result)
}
#[cfg(test)] pub(crate) mod tests {
    use super::*;
    pub(crate) fn fixture(n:u32,outgoing:bool)->Value {
        let mut bytes=b"#!AMR\n".to_vec();for _ in 0..20 {bytes.push((7<<3)|4);bytes.extend([0;31]);}
        json!({"operation":"ingest","binding":{"id":format!("{n:032x}"),"session":"a".repeat(32),"peer":"+15550101234","subscription":2,"outgoing":outgoing},"bytes":bytes,"mime":"audio/amr","sequence":n})
    }
    #[test] fn clips_are_durable_scoped_validated_deleted_and_deduplicated() {
        let d=tempfile::tempdir().unwrap();let other=tempfile::tempdir().unwrap();let root=d.path();let t=10*DAY;let f=fixture(1,false);
        assert_eq!(host_at(root,&f,t).unwrap()["duplicate"],false);
        assert_eq!(host_at(root,&f,t+1).unwrap()["duplicate"],true);
        let rows=service_at(root,"walkie.clips",&json!({}),t+2).unwrap();assert_eq!(rows[0]["state"],"received");assert!(rows[0]["audio"].is_null());assert_eq!(rows[0]["millis"],400);
        assert_eq!(service_at(other.path(),"walkie.clips",&json!({}),t).unwrap(),json!([]));
        let before=std::fs::read(root.join("walkie-clips.json")).unwrap();
        for key in ["peer","session","subscription","outgoing"] {let mut bad=f.clone();bad["binding"][key]=match key {"peer"=>json!("+15559999999"),"session"=>json!("b".repeat(32)),"outgoing"=>json!(true),_=>json!(3)};assert!(host_at(root,&bad,t).is_err());}
        let mut bad=f.clone();bad["bytes"][10]=json!(1);assert!(host_at(root,&bad,t).is_err());
        for raw in [json!([]),json!([256]),json!([1,2,3])] {let mut bad=fixture(2,false);bad["bytes"]=raw;assert!(host_at(root,&bad,t).is_err());}
        assert_eq!(before,std::fs::read(root.join("walkie-clips.json")).unwrap());
        assert!(service_at(root,"walkie.ingest",&f,t).is_err());
        service_at(root,"walkie.clip.delete",&json!({"id":f["binding"]["id"]}),t+3).unwrap();
        assert_eq!(host_at(root,&f,t+4).unwrap()["duplicate"],true);assert_eq!(service_at(root,"walkie.clips",&json!({}),t+5).unwrap(),json!([]));
        assert_eq!(host_at(root,&f,t+14*DAY).unwrap()["duplicate"],false);
        std::fs::write(root.join("walkie-clips.json"),b"{bad").unwrap();assert!(service_at(root,"walkie.clips",&json!({}),t).is_err());
    }
    #[test] fn outcomes_bind_attempts_and_keep_receiver_proof() {
        let d=tempfile::tempdir().unwrap();let r=d.path();let f=fixture(1,true);let t=10*DAY;host_at(r,&f,t).unwrap();
        let mut cmd=json!({"binding":f["binding"],"operation":"attempt"});let one=host_at(r,&cmd,t).unwrap()["attempt"].clone();
        assert_eq!(service_at(r,"walkie.clips",&json!({}),t+90_000).unwrap()[0]["state"],"uncertain");
        let two=host_at(r,&cmd,t+90_001).unwrap()["attempt"].clone();assert_ne!(one,two);
        cmd["operation"]=json!("carrier.result");cmd["attempt"]=one;cmd["result"]=json!("failed");assert!(host_at(r,&cmd,t+90_002).is_err());
        cmd["attempt"]=two;cmd["result"]=json!("carrier");host_at(r,&cmd,t+90_003).unwrap();assert_eq!(service_at(r,"walkie.clips",&json!({}),t+90_004).unwrap()[0]["state"],"carrier");
        let mut receipt=json!({"operation":"receipt","binding":f["binding"],"result":"delivered"});host_at(r,&receipt,t+90_005).unwrap();
        cmd["result"]=json!("failed");host_at(r,&cmd,t+90_006).unwrap();assert_eq!(service_at(r,"walkie.clips",&json!({}),t+90_007).unwrap()[0]["state"],"delivered");
        receipt["result"]=json!("heard");host_at(r,&receipt,t+90_008).unwrap();receipt["result"]=json!("delivered");host_at(r,&receipt,t+90_009).unwrap();
        assert_eq!(service_at(r,"walkie.clips",&json!({}),t+90_010).unwrap()[0]["state"],"heard");
        cmd["operation"]=json!("attempt");assert!(host_at(r,&cmd,t+90_011).is_err());
        assert_eq!(service_at(r,"walkie.clips",&json!({}),t+90_008+DAY).unwrap(),json!([]));
    }
    #[test] fn expiry_limits_clear_and_quota_preserve_duplicate_proofs() {
        let d=tempfile::tempdir().unwrap();let r=d.path();let t=10*DAY;
        for n in 1..=101 {host_at(r,&fixture(n,false),t+n as u64).unwrap();}
        let rows=service_at(r,"walkie.clips",&json!({}),t+102).unwrap();assert_eq!(rows.as_array().unwrap().len(),100);assert!(rows.as_array().unwrap().iter().all(|c|c["id"]!=format!("{:032x}",1)));
        assert_eq!(host_at(r,&fixture(1,false),t+103).unwrap()["duplicate"],true);
        service_at(r,"walkie.clips.clear",&json!({}),t+104).unwrap();assert_eq!(host_at(r,&fixture(100,false),t+105).unwrap()["duplicate"],true);
        let mut a=load(r).unwrap();a.seen.clear();for n in 1..=1000 {let f=fixture(n,false);a.seen.push(Seen{binding:binding(&f["binding"]).unwrap(),digest:"0".repeat(64),created:t});}save(r,&a).unwrap();
        host_at(r,&fixture(1001,false),t+106).unwrap();assert_eq!(load(r).unwrap().seen.len(),1000);
        assert_eq!(service_at(r,"walkie.clips",&json!({}),t+106+7*DAY).unwrap(),json!([]));
        let before=std::fs::read(r.join("walkie-clips.json")).unwrap();
        std::fs::write(r.join("quota.bin"),vec![0;16*1024*1024]).unwrap();assert!(host_at(r,&fixture(1002,false),t+107).is_err());assert_eq!(before,std::fs::read(r.join("walkie-clips.json")).unwrap());
    }
    #[test] fn trusted_playback_reads_real_bytes_and_marks_only_incoming_played() {
        let d=tempfile::tempdir().unwrap();let r=d.path();let f=fixture(1,false);let t=10*DAY;host_at(r,&f,t).unwrap();
        let mut cmd=json!({"operation":"audio","binding":f["binding"]});assert_eq!(host_at(r,&cmd,t).unwrap()["bytes"],f["bytes"]);
        cmd["operation"]=json!("played");host_at(r,&cmd,t+1).unwrap();host_at(r,&cmd,t+100).unwrap();
        assert_eq!(service_at(r,"walkie.clips",&json!({}),t+DAY).unwrap()[0]["state"],"heard");
        assert_eq!(service_at(r,"walkie.clips",&json!({}),t+DAY+1).unwrap(),json!([]));
    }
}

#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]struct PortableClip{id:String,peer:String,outgoing:bool,mime:String,audio:String,digest:String,millis:u32,created:u64}
fn portable(v:&Value)->Result<Vec<PortableClip>>{let clips:Vec<PortableClip>=serde_json::from_value(v.clone()).map_err(|_|"Invalid saved Walkie clips")?;if clips.len()>100{return Err("Voice archive limit reached".into())}let mut ids=std::collections::BTreeSet::new();for c in &clips{let raw=bytes(&c.audio)?;if !crate::valid_id(&c.id)||!ids.insert(&c.id)||crate::messaging::phone(&c.peer)?!=c.peer||crate::walkie::audio_duration(&raw,&c.mime)?!=c.millis||c.millis<300||c.millis>if c.outgoing{12000}else{13000}||hex(&Sha256::digest(&raw))!=c.digest||c.created>i64::MAX as u64{return Err("Invalid saved Walkie clip".into())}}Ok(clips)}
pub(crate) fn backup_export(root:&Path)->Result<Value>{let a=load(root)?;let v=json!(a.clips.into_iter().map(|c|PortableClip{id:c.binding.id,peer:c.binding.peer,outgoing:c.binding.outgoing,mime:c.mime,audio:c.audio,digest:c.digest,millis:c.millis,created:c.created}).collect::<Vec<_>>());portable(&v)?;Ok(v)}
fn backup_merge(root:&Path,v:&Value)->Result<(Archive,usize,usize)>{let clips=portable(v)?;let total=clips.len();let mut a=load(root)?;let mut added=0;for c in clips{if a.seen.iter().any(|s|s.binding.id==c.id){continue}let binding=Binding{id:c.id,session:uuid::Uuid::new_v4().simple().to_string(),peer:c.peer,subscription:-1,outgoing:c.outgoing};a.seen.push(Seen{binding:binding.clone(),digest:c.digest.clone(),created:c.created});a.clips.push(Clip{binding,sequence:0,mime:c.mime,audio:c.audio,digest:c.digest,millis:c.millis,created:c.created,heard_at:None,state:if c.outgoing{State::Failed}else{State::Received},attempt:if c.outgoing{Some(uuid::Uuid::new_v4().simple().to_string())}else{None},sending_at:None,restored:true});added+=1;}validate(&a)?;let b=serde_json::to_vec(&a).map_err(|e|e.to_string())?;if b.len()>15*1024*1024||crate::sdk::private_size(root,&root.join("walkie-clips.json"))?+b.len() as u64>16*1024*1024{return Err("Restored voice archive exceeds storage limit".into())}Ok((a,added,total-added))}
pub(crate) fn backup_plan(root:&Path,v:&Value)->Result<(usize,usize,u64)>{let(a,n,k)=backup_merge(root,v)?;Ok((n,k,serde_json::to_vec(&a).map_err(|e|e.to_string())?.len() as u64))}
pub(crate) fn backup_commit(root:&Path,v:&Value)->Result<()>{let(a,_,_)=backup_merge(root,v)?;save(root,&a)}
