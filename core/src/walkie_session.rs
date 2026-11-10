//! Durable outgoing session controller. Transport is deliberately disabled.
//! Host-only commands below prepare state; fixtures are not carrier acceptance.
use crate::{Result,walkie::{Control,Frame,Floor,Owner}};
use serde::{Serialize,Deserialize};
use serde_json::{json,Value};
use std::{path::Path,sync::OnceLock};

pub const TRANSPORT_READY:bool=false;
const VERIFY:u64=18_000;
const INVITE:u64=45_000;
const IDLE:u64=300_000;
// Deadlines use this process's monotonic clock; changing the phone's wall clock
// cannot extend a call. Persisted state from another process is always interrupted.
fn now()->u64 {static START:OnceLock<std::time::Instant>=OnceLock::new();START.get_or_init(std::time::Instant::now).elapsed().as_millis().min(u64::MAX as u128) as u64}
fn process()-> &'static str {static ID:OnceLock<String>=OnceLock::new();ID.get_or_init(||uuid::Uuid::new_v4().simple().to_string())}
fn fresh()->String {uuid::Uuid::new_v4().simple().to_string()}
#[derive(Clone,Debug,PartialEq,Serialize,Deserialize)]
#[serde(rename_all="snake_case")]
enum Phase {ProbeQueued,ProbeSending,Checking,InviteQueued,InviteSending,Inviting,Incoming,AcceptQueued,AcceptSending,DeclineQueued,DeclineSending,Connected,Ended}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Session {#[serde(default)] unheard:String,#[serde(default)] confirmed:Vec<String>,#[serde(default)] floor:Floor,#[serde(default)] request:u32,#[serde(default)] waiting:u32,#[serde(default)] incoming:bool,id:String,peer:String,subscription:i32,process:String,phase:Phase,deadline:u64,observed:u64,attempt:Option<String>,reason:String}
#[derive(Default,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Store {
 #[serde(default)] current:Option<Session>, #[serde(default)] proof:Option<Proof>,
 #[serde(default)] seen:Vec<String>, #[serde(default)] quota:Quota,
 #[serde(default)] replies:Vec<ReplyRate>, #[serde(default)] reply_process:String, #[serde(default)] outbox:Vec<ControlJob>,
}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct ControlJob {id:String,peer:String,subscription:i32,wire:String,attempt:Option<String>}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Proof {id:String,peer:String,subscription:i32,process:String,deadline:u64,observed:u64,phase:String,attempt:Option<String>}
#[derive(Default,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Quota {day:u64,answered:u32}
#[derive(Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct ReplyRate {peer:String,subscription:i32,last:u64}
fn day()->u64 {std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs()/86400}
fn valid_store(s:&Store)->bool {
 s.outbox.len()<=16&&s.outbox.iter().all(|j|crate::valid_id(&j.id)&&j.subscription>=0&&crate::messaging::phone(&j.peer).is_ok_and(|p|p==j.peer)&&j.wire.len()<=140&&j.wire.is_ascii()&&j.attempt.as_ref().is_none_or(|id|crate::valid_id(id)))&&s.current.as_ref().is_none_or(Session::valid)&&s.seen.len()<=64&&s.seen.iter().all(|id|crate::valid_id(id))
 &&s.seen.iter().collect::<std::collections::BTreeSet<_>>().len()==s.seen.len()&&s.quota.answered<=30
 &&s.replies.len()<=16&&(s.reply_process.is_empty()||crate::valid_id(&s.reply_process))
 &&s.replies.iter().all(|r|r.subscription>=0&&crate::messaging::phone(&r.peer).is_ok_and(|p|p==r.peer))
 &&s.proof.as_ref().is_none_or(|p|crate::valid_id(&p.id)&&crate::valid_id(&p.process)&&p.subscription>=0
  &&crate::messaging::phone(&p.peer).is_ok_and(|n|n==p.peer)&&p.deadline>p.observed&&p.deadline-p.observed<=INVITE
  &&s.seen.contains(&p.id)&&match p.phase.as_str(){"queued"|"verified"=>p.attempt.is_none(),"sending"=>p.attempt.as_ref().is_some_and(|id|crate::valid_id(id)),_=>false})
}
impl Session {
 fn end(&mut self,reason:&str){self.phase=Phase::Ended;self.reason=reason.into();self.attempt=None;self.deadline=0;}
 fn recover(&mut self,time:u64,owner:&str)->bool {
  if self.phase==Phase::Ended{return false}
  if self.process!=owner {self.end("interrupted");return true}
  if time<self.observed {self.end("clock_changed");return true}
  if time>=self.deadline {let reason=if self.phase==Phase::Connected{"idle_timeout"}else{"timeout"};self.end(reason);return true}
  false
 }
 fn valid(&self)->bool {
  (self.unheard.is_empty()||crate::valid_id(&self.unheard))&&self.confirmed.len()<=8&&self.confirmed.iter().all(|id|crate::valid_id(id))&&!matches!(self.floor.owner,Owner::Guest(n) if n==0||n>1_000_000)&&self.floor.epoch<=1_000_000&&self.floor.cancelled<=1_000_000&&self.request<=1_000_000&&self.waiting<=self.request&&crate::valid_id(&self.id)&&crate::valid_id(&self.process)&&self.subscription>=0
   &&crate::messaging::phone(&self.peer).is_ok_and(|p|p==self.peer)
   &&match self.phase {
    Phase::Ended=>self.deadline==0&&self.attempt.is_none()&&["cancelled","recipient_changed","interrupted","clock_changed","timeout","idle_timeout","declined","remote_ended","failed","uncertain"].contains(&self.reason.as_str()),
    _=>self.reason.is_empty()&&self.deadline>self.observed&&self.deadline-self.observed<=IDLE
      &&if matches!(self.phase,Phase::ProbeSending|Phase::InviteSending|Phase::AcceptSending|Phase::DeclineSending){self.attempt.as_ref().is_some_and(|id|crate::valid_id(id))}else{self.attempt.is_none()}
   }
 }
}
fn load(root:&Path)->Result<Store>{
 let p=root.join("walkie-session.json");if !p.exists(){return Ok(Store::default())}
 let s:Store=serde_json::from_slice(&crate::sdk::read_bounded(&p,8192)?).map_err(|_|"Walkie session could not be opened")?;
 if !valid_store(&s){return Err("Invalid Walkie session state".into())}Ok(s)
}
fn save(root:&Path,s:&Store)->Result<()> {
 if !valid_store(&s){return Err("Invalid Walkie session state".into())}
 let p=root.join("walkie-session.json");let b=serde_json::to_vec(s).map_err(|e|e.to_string())?;
 if b.len()>8192||crate::sdk::private_size(root,&p)?+b.len() as u64>16*1024*1024{return Err("App private storage limit reached".into())}
 crate::sdk::atomic(&p,&b)
}
fn view(s:&Store)->Value {let mut value=match &s.current {
 None=>json!({"phase":"offline","transport_ready":TRANSPORT_READY}),
 Some(c)=>json!({"phase":c.phase,"id":c.id,"peer":c.peer,"subscription":c.subscription,"reason":c.reason,"unheard":c.unheard,"incoming":c.incoming,"floor":c.floor,"waiting":c.waiting,"can_record":c.phase==Phase::Connected&&if c.incoming{matches!(c.floor.owner,Owner::Guest(n) if n==c.waiting&&n>0)}else{c.floor.owner==Owner::Host},"transport_ready":TRANSPORT_READY})
};let next=s.current.as_ref().map(|c|c.deadline).into_iter().chain(s.proof.as_ref().map(|p|p.deadline)).filter(|n|*n>0).min().unwrap_or(0);value["next_wakeup"]=json!(next);value}
fn recovered(root:&Path,time:u64,owner:&str)->Result<Store>{
 let mut s=load(root)?;let restarted=s.current.as_ref().is_some_and(|c|c.process!=owner);let mut changed=s.current.as_mut().is_some_and(|c|c.recover(time,owner));if restarted&&!s.outbox.is_empty(){s.outbox.clear();changed=true;}
 if s.proof.as_ref().is_some_and(|p|p.process!=owner||time<p.observed||time>=p.deadline){s.proof=None;changed=true;}
 if changed{save(root,&s)?}Ok(s)
}
pub fn snapshot(root:&Path)->Result<Value>{Ok(view(&recovered(root,now(),process())?))}
pub fn end(root:&Path,reason:&str)->Result<()> {
 let mut s=load(root)?;let mut changed=s.proof.take().is_some()||!s.outbox.is_empty();s.outbox.clear();
 if let Some(c)=s.current.as_ref().filter(|c|c.phase==Phase::Connected){let wire=format!("!OKW1|Q|{}|0|wt1",c.id);queue(&mut s,&wire)?;}
 if let Some(c)=s.current.as_mut().filter(|c|c.phase!=Phase::Ended){c.end(reason);changed=true;}if changed{save(root,&s)?}Ok(())
}
/// Trusted Android carrier only; readiness is checked by walkie_carrier and the host.
pub(crate) fn carrier(root:&Path,args:&Value)->Result<Value>{
 if ["floor.request","floor.release"].contains(&args["operation"].as_str().unwrap_or("")){return local_floor(root,args)}
 if args["operation"]=="control.sent" {
  let mut s=recovered(root,now(),process())?;let token=args["attempt"].as_str().ok_or("Missing control attempt")?;
  let at=s.outbox.iter().position(|j|j.id==args["id"]&&j.peer==args["peer"]&&Some(j.subscription as i64)==args["subscription"].as_i64()&&j.attempt.as_deref()==Some(token)).ok_or("Stale control callback")?;
  let failed=args["outcome"]!="accepted";let job=s.outbox.remove(at);
  if failed&&["S","R","X","L"].iter().any(|kind|job.wire.starts_with(&format!("!OKW1|{kind}|"))){if let Some(c)=s.current.as_mut(){c.end("uncertain")}s.outbox.clear();}
  save(root,&s)?;return Ok(view(&s))
 }
 run(root,args,now(),process(),true)
}
pub(crate) fn authorize_claim(root:&Path,j:&Value)->Result<()> {
 let s=recovered(root,now(),process())?;
 let token=j["attempt"].as_str().ok_or("Missing send attempt")?;
 let binding=|id:&str,peer:&str,sub:i32,attempt:Option<&str>|j["id"]==id&&j["peer"]==peer&&j["subscription"].as_i64()==Some(sub as i64)&&attempt==Some(token);
 let current=s.current.as_ref().is_some_and(|c|matches!(c.phase,Phase::ProbeSending|Phase::InviteSending|Phase::AcceptSending|Phase::DeclineSending)&&binding(&c.id,&c.peer,c.subscription,c.attempt.as_deref()));
 let proof=s.proof.as_ref().is_some_and(|p|p.phase=="sending"&&binding(&p.id,&p.peer,p.subscription,p.attempt.as_deref()));
 let queued=s.outbox.iter().any(|p|binding(&p.id,&p.peer,p.subscription,p.attempt.as_deref()));
 if !current&&!proof&&!queued{return Err("Carrier send authorization expired".into())}Ok(())
}
pub(crate) fn peek_control(root:&Path)->Result<Value>{let s=recovered(root,now(),process())?;
 if let Some(p)=s.proof.as_ref().filter(|p|p.phase=="queued"){return Ok(json!({"peer":p.peer,"subscription":p.subscription}))}
 if let Some(c)=s.current.as_ref().filter(|c|matches!(c.phase,Phase::ProbeQueued|Phase::InviteQueued|Phase::AcceptQueued|Phase::DeclineQueued)){return Ok(json!({"peer":c.peer,"subscription":c.subscription}))}
 if let Some(j)=s.outbox.first().filter(|j|j.attempt.is_none()){return Ok(json!({"peer":j.peer,"subscription":j.subscription}))}Ok(Value::Null)
}
pub(crate) fn next_control(root:&Path)->Result<Value>{
 let mut s=recovered(root,now(),process())?;
 if let Some(p)=s.proof.as_ref().filter(|p|p.phase=="queued"){
  let mut a=json!({"operation":"probe.claim","id":p.id,"peer":p.peer,"subscription":p.subscription,"allowed":true});
  let mut out=run(root,&a,now(),process(),true)?;a["operation"]=json!("probe.sent");out["callback"]=a;return Ok(out)
 }
 if let Some(c)=s.current.as_ref().filter(|c|matches!(c.phase,Phase::ProbeQueued|Phase::InviteQueued|Phase::AcceptQueued|Phase::DeclineQueued)) {
  let a=json!({"operation":"claim","id":c.id,"peer":c.peer,"subscription":c.subscription});let mut out=run(root,&a,now(),process(),true)?;
  out["callback"]=json!({"operation":"sent","id":c.id,"peer":c.peer,"subscription":c.subscription});return Ok(out)
 }
 if let Some(j)=s.outbox.first_mut().filter(|j|j.attempt.is_none()) {
  let token=fresh();j.attempt=Some(token.clone());let out=json!({"id":j.id,"peer":j.peer,"subscription":j.subscription,"attempt":token,"wire":j.wire,"callback":{"operation":"control.sent","id":j.id,"peer":j.peer,"subscription":j.subscription}});save(root,&s)?;return Ok(out)
 }
 Ok(Value::Null)
}
fn next_unheard(root:&Path,c:&Session)->Result<String> {
 let rows=crate::walkie_clips::service(root,"walkie.clips",&json!({}))?;
 Ok(rows.as_array().into_iter().flatten().filter(|r|r["outgoing"]==false&&r["state"]=="received"&&r["session"]==c.id&&r["peer"]==c.peer&&r["subscription"]==c.subscription).min_by_key(|r|(r["sequence"].as_u64().unwrap_or(0),r["created"].as_u64().unwrap_or(0))).and_then(|r|r["id"].as_str()).unwrap_or("").into())
}
pub(crate) fn clear_hint(root:&Path,id:Option<&str>)->Result<()> {let mut s=load(root)?;if let Some(c)=s.current.as_mut().filter(|c|!c.unheard.is_empty()&&id.is_none_or(|id|c.unheard==id)){c.unheard=next_unheard(root,c)?;save(root,&s)?}Ok(())}
pub(crate) fn receipt(root:&Path,b:&Value,heard:bool)->Result<()> {
 let mut s=recovered(root,now(),process())?;let id=b["id"].as_str().filter(|id|crate::valid_id(id)).ok_or("Invalid receipt")?;
 let frame=Frame{session:b["session"].as_str().ok_or("Missing receipt session")?.into(),serial:0,control:if heard{Control::Heard(id.into())}else{Control::Received(id.into())}};
 let wire=frame.encode()?.replacen("!ICW1","!OKW1",1);
 if let Some(c)=s.current.as_mut(){if heard&&c.unheard==id||!heard&&c.unheard.is_empty(){c.unheard=next_unheard(root,c)?;}}
 if s.outbox.iter().any(|j|j.wire==wire&&j.peer==b["peer"]&&Some(j.subscription as i64)==b["subscription"].as_i64()){return Ok(())}
 if s.outbox.len()>=16{return Err("Pending control queue is full".into())}
 s.outbox.push(ControlJob{id:fresh(),peer:crate::messaging::phone(b["peer"].as_str().ok_or("Missing recipient")?)?,subscription:b["subscription"].as_i64().filter(|n|(0..=i32::MAX as i64).contains(n)).ok_or("Missing receipt SIM")? as i32,wire,attempt:None});save(root,&s)
}
/// Local decline never sends a control message while communications are disabled.
pub fn decline_local(root:&Path,id:&str)->Result<()> {
 let mut s=recovered(root,now(),process())?;
 let c=s.current.as_mut().filter(|c|c.id==id&&c.incoming&&c.phase==Phase::Incoming).ok_or("Invitation is no longer awaiting your choice")?;
 c.end("declined");save(root,&s)
}
#[cfg(test)]
pub(crate) fn fixture_incoming(root:&Path,peer:&str)->Value {
 let t=now();let owner=process();let id=fresh();
 let mut a=json!({"operation":"incoming","allowed":true,"peer":peer,"subscription":2,"wire":format!("!OKW1|P|{id}|0|wt1")});run(root,&a,t,owner,true).unwrap();
 a["operation"]=json!("probe.claim");a["id"]=json!(id);let claim=run(root,&a,t,owner,true).unwrap();
 a["operation"]=json!("probe.sent");a["attempt"]=claim["attempt"].clone();a["outcome"]=json!("accepted");run(root,&a,t,owner,true).unwrap();
 a["operation"]=json!("incoming");a["wire"]=json!(format!("!OKW1|I|{id}|0|wt1"));run(root,&a,t,owner,true).unwrap()
}
/// Only this entry point is reachable by the SDK host. No caller-supplied ready flag.
pub fn host(root:&Path,args:&Value)->Result<Value>{run(root,args,now(),process(),TRANSPORT_READY)}
fn run(root:&Path,args:&Value,time:u64,owner:&str,ready:bool)->Result<Value>{
 let op=args["operation"].as_str().ok_or("Missing session operation")?;
 if op=="state"{return Ok(view(&recovered(root,time,owner)?))}
 if op=="end"{end(root,"cancelled")?;return Ok(view(&load(root)?))}
 if !ready{return Err("Voice connection unavailable: live session and SMS/MMS integration is not ready. No invitation was sent.".into())}
 let mut store=recovered(root,time,owner)?;
 if ["incoming","probe.claim","probe.sent"].contains(&op){let out=incoming(&mut store,args,time,owner,day())?;save(root,&store)?;return Ok(out)}
 if op=="begin" {
  if store.current.as_ref().is_some_and(|c|c.phase!=Phase::Ended)||store.proof.is_some(){return Err("End the current Walkie session first".into())}
  let peer=crate::messaging::phone(args["peer"].as_str().ok_or("Choose a recipient")?)?;
  let subscription=args["subscription"].as_i64().filter(|n|(0..=i32::MAX as i64).contains(n)).ok_or("Choose an active SIM before connecting")? as i32;
  store.current=Some(Session{unheard:String::new(),confirmed:vec![],floor:Floor::default(),request:0,waiting:0,incoming:false,id:fresh(),peer,subscription,process:owner.into(),phase:Phase::ProbeQueued,deadline:time.saturating_add(VERIFY),observed:time,attempt:None,reason:String::new()});
 }else{
  let c=store.current.as_mut().ok_or("No current Walkie session")?;
  if c.phase==Phase::Ended{return Err("Walkie session has ended".into())}
  if args["id"]!=c.id||args["peer"].as_str().and_then(|p|crate::messaging::phone(p).ok()).as_deref()!=Some(c.peer.as_str())||args["subscription"].as_i64()!=Some(c.subscription as i64){return Err("Walkie session recipient or SIM mismatch".into())}
  match op {
   "claim"=>{
    let kind=match c.phase{Phase::ProbeQueued=>"P",Phase::InviteQueued=>"I",Phase::AcceptQueued=>"A",Phase::DeclineQueued=>"D",_=>return Err("Session control already claimed or unavailable".into())};
    let token=fresh();c.attempt=Some(token.clone());c.phase=match kind{"P"=>Phase::ProbeSending,"I"=>Phase::InviteSending,"A"=>Phase::AcceptSending,_=>Phase::DeclineSending};c.observed=time;
    let wire=format!("!OKW1|{kind}|{}|0|wt1",c.id);
    // Persist before exposing an outbound frame. Ambiguous restart never reclaims it.
    let output=json!({"id":c.id,"peer":c.peer,"subscription":c.subscription,"attempt":token,"wire":wire});save(root,&store)?;return Ok(output)
   },
   "sent"=>{
    if args["attempt"].as_str().is_some_and(|token|c.confirmed.iter().any(|id|id==token)){return Ok(view(&store))}
    if !matches!(c.phase,Phase::ProbeSending|Phase::InviteSending|Phase::AcceptSending|Phase::DeclineSending)||args["attempt"].as_str()!=c.attempt.as_deref(){return Err("Stale session send attempt".into())}
    match args["outcome"].as_str(){
     Some("accepted")=>{let old=c.phase.clone();c.attempt=None;c.observed=time;match old{
      Phase::ProbeSending=>c.phase=Phase::Checking,
      Phase::InviteSending=>{c.phase=Phase::Inviting;c.deadline=time.saturating_add(INVITE)},
      Phase::AcceptSending=>{c.phase=Phase::Connected;c.deadline=time.saturating_add(IDLE)},
      Phase::DeclineSending=>c.end("declined"),_=>unreachable!()
     }},
     Some("failed")=>c.end("failed"),Some("uncertain")=>c.end("uncertain"),_=>return Err("Invalid session send outcome".into())
    }
   },
   "accept"|"decline"=>{
    if !c.incoming||c.phase!=Phase::Incoming{return Err("Invitation is no longer awaiting your choice".into())}
    c.phase=if op=="accept"{Phase::AcceptQueued}else{Phase::DeclineQueued};c.observed=time;
   },
   "receive"=>{
    let raw=args["wire"].as_str().ok_or("Missing session control")?;
    if !raw.starts_with("!OKW1|"){return Err("Unsupported session protocol".into())}
    let f=Frame::parse(&raw.replacen("!OKW1","!ICW1",1))?;
    if f.session!=c.id{return Err("Stale session control".into())}
    if matches!(f.control,Control::Floor(_)|Control::Request|Control::Cancel|Control::Release){
     if c.phase==Phase::AcceptSending&&c.incoming{
      if let Some(token)=c.attempt.take(){c.confirmed.push(token)}c.phase=Phase::Connected;c.observed=time;c.deadline=time.saturating_add(IDLE);
     }
     if c.phase!=Phase::Connected{return Err("No connected microphone session".into())}
     let mut announce=false;
     match &f.control{
      Control::Floor(owner) if c.incoming=>{c.floor.apply(f.serial,owner.clone())?;},
      Control::Request if !c.incoming=>{c.floor.grant(f.serial)?;announce=true;},
      Control::Cancel if !c.incoming=>{c.floor.cancel(f.serial)?;announce=true;},
      Control::Release if !c.incoming=>{c.floor.guest_release(f.serial)?;announce=true;},
      _=>return Err("Invalid microphone control direction".into())
     }
     c.observed=time;c.deadline=time.saturating_add(IDLE);
     if announce&&c.floor.epoch>0{let wire=Frame{session:c.id.clone(),serial:c.floor.epoch,control:Control::Floor(c.floor.owner.clone())}.encode()?.replacen("!ICW1","!OKW1",1);queue(&mut store,&wire)?;}
     save(root,&store)?;return Ok(view(&store))
    }
    if f.serial!=0{return Err("Stale session control".into())}
    match (&c.phase,f.control) {
     (Phase::Checking|Phase::ProbeSending,Control::Check)=>{if let Some(token)=c.attempt.take(){c.confirmed.push(token)}c.phase=Phase::InviteQueued;c.observed=time;c.deadline=time.saturating_add(VERIFY)},
     (Phase::InviteQueued|Phase::InviteSending|Phase::Inviting|Phase::Connected,Control::Check)=>return Ok(view(&store)),
     (Phase::Inviting|Phase::InviteSending,Control::Accept)=>{if let Some(token)=c.attempt.take(){c.confirmed.push(token)}c.phase=Phase::Connected;c.observed=time;c.deadline=time.saturating_add(IDLE)},
     (Phase::Connected,Control::Accept)=>return Ok(view(&store)),
     (Phase::Checking|Phase::InviteQueued|Phase::InviteSending|Phase::Inviting|Phase::Connected,Control::Decline)=>c.end("declined"),
     (Phase::Connected|Phase::Incoming|Phase::AcceptQueued|Phase::AcceptSending|Phase::DeclineQueued|Phase::DeclineSending,Control::Query)=>c.end("remote_ended"),
     _=>return Err("Session control is not valid in the current phase".into())
    }
   },
   _=>return Err("Unsupported session operation".into())
  }
 }
 save(root,&store)?;Ok(view(&store))
}

fn queue(s:&mut Store,wire:&str)->Result<()> {
 let c=s.current.as_ref().ok_or("No current session")?;
 if s.outbox.iter().any(|j|j.wire==wire&&j.peer==c.peer){return Ok(())}
 if s.outbox.len()>=16{return Err("Control queue is full".into())}
 s.outbox.push(ControlJob{id:fresh(),peer:c.peer.clone(),subscription:c.subscription,wire:wire.into(),attempt:None});Ok(())
}
fn local_floor(root:&Path,args:&Value)->Result<Value>{
 let t=now();let mut s=recovered(root,t,process())?;
 let c=s.current.as_mut().filter(|c|c.phase==Phase::Connected&&c.id==args["id"]&&c.peer==args["peer"]&&Some(c.subscription as i64)==args["subscription"].as_i64()).ok_or("Microphone session changed")?;
 let control=if args["operation"]=="floor.request"{
  if c.incoming{
   if c.waiting>0{return Ok(view(&s))}
   if c.floor.owner!=Owner::Free{return Err("The other person is talking".into())}
   if c.request>=1_000_000{return Err("Reconnect before requesting the microphone".into())}c.request+=1;c.waiting=c.request;
   Some(Frame{session:c.id.clone(),serial:c.waiting,control:Control::Request})
  }else{if !c.floor.host_take()?{return Ok(view(&s))}Some(Frame{session:c.id.clone(),serial:c.floor.epoch,control:Control::Floor(c.floor.owner.clone())})}
 }else if c.incoming{
  if c.waiting==0{return Ok(view(&s))}let request=c.waiting;c.waiting=0;
  Some(Frame{session:c.id.clone(),serial:request,control:Control::Cancel})
 }else{if !c.floor.host_release()?{return Ok(view(&s))}Some(Frame{session:c.id.clone(),serial:c.floor.epoch,control:Control::Floor(c.floor.owner.clone())})};
 c.observed=t;c.deadline=t.saturating_add(IDLE);
 if let Some(f)=control{queue(&mut s,&f.encode()?.replacen("!ICW1","!OKW1",1))?}save(root,&s)?;Ok(view(&s))
}
fn incoming(s:&mut Store,args:&Value,time:u64,owner:&str,today:u64)->Result<Value>{
 if args["allowed"]!=true{return Err("Incoming Walkie control is blocked by host policy".into())}
 let peer=crate::messaging::phone(args["peer"].as_str().ok_or("Missing sender")?)?;
 let sub=args["subscription"].as_i64().filter(|n|(0..=i32::MAX as i64).contains(n)).ok_or("Missing receiving SIM")? as i32;
 let op=args["operation"].as_str().unwrap_or("");
 if op=="incoming" {
  let raw=args["wire"].as_str().ok_or("Missing incoming control")?;
  if raw.len()>140||!raw.is_ascii(){return Err("Invalid incoming Walkie frame".into())}
  let fields:Vec<_>=raw.split('|').collect();
  if fields.len()!=5||fields[0]!="!OKW1"||!["P","I"].contains(&fields[1])||!crate::valid_id(fields[2])||fields[3]!="0"||fields[4]!="wt1"{return Err("Invalid incoming Walkie frame".into())}
  let id=fields[2];
  if fields[1]=="P" {
   if s.current.as_ref().is_some_and(|c|c.phase!=Phase::Ended&&!(c.peer==peer&&c.subscription==sub&&!c.incoming&&matches!(c.phase,Phase::ProbeQueued|Phase::ProbeSending|Phase::Checking|Phase::InviteQueued|Phase::InviteSending|Phase::Inviting)))||s.proof.is_some(){return Err("Another Walkie request is pending".into())}
   if s.seen.iter().any(|old|old==id){return Err("Repeated Walkie verification request".into())}
   if today<s.quota.day||today==s.quota.day&&s.quota.answered>=30{return Err("Daily Walkie verification reply limit reached".into())}
   if s.reply_process==owner&&s.replies.iter().any(|r|r.peer==peer&&r.subscription==sub&&(time<r.last||time-r.last<2000)){return Err("Walkie verification reply rate limit reached".into())}
   if today>s.quota.day{s.quota=Quota{day:today,answered:0}}
   if s.reply_process!=owner{s.replies.clear();s.reply_process=owner.into()}
   s.quota.answered+=1;
   s.replies.retain(|r|r.peer!=peer||r.subscription!=sub);s.replies.push(ReplyRate{peer:peer.clone(),subscription:sub,last:time});if s.replies.len()>16{s.replies.remove(0);}
   s.seen.push(id.into());if s.seen.len()>64{s.seen.remove(0);}
   s.proof=Some(Proof{id:id.into(),peer,subscription:sub,process:owner.into(),deadline:time.saturating_add(INVITE),observed:time,phase:"queued".into(),attempt:None});
   return Ok(json!({"verification_reply":"queued"}))
  }
  if let Some(c)=&s.current {if c.id==id&&c.peer==peer&&c.subscription==sub&&c.incoming&&matches!(c.phase,Phase::Incoming|Phase::AcceptQueued|Phase::AcceptSending|Phase::DeclineQueued|Phase::DeclineSending|Phase::Connected){return Ok(view(s))}}
  if let Some(c)=s.current.as_ref().filter(|c|c.phase!=Phase::Ended){
   if c.incoming||c.peer!=peer||c.subscription!=sub||c.phase==Phase::Connected{return Err("End the current Walkie session first".into())}
   if c.id.as_str()<id {
    let wire=format!("!OKW1|D|{id}|0|wt1");queue(s,&wire)?;s.proof=None;return Ok(view(s))
   }
  }
  let p=s.proof.as_ref().filter(|p|p.id==id&&p.peer==peer&&p.subscription==sub&&["verified","sending"].contains(&p.phase.as_str())).ok_or("Invitation has no current matching verification")?;
  s.current=Some(Session{unheard:String::new(),confirmed:p.attempt.iter().cloned().collect(),floor:Floor::default(),request:0,waiting:0,incoming:true,id:p.id.clone(),peer,subscription:sub,process:owner.into(),phase:Phase::Incoming,deadline:time.saturating_add(INVITE),observed:time,attempt:None,reason:String::new()});s.proof=None;
  return Ok(view(s))
 }
 if op=="probe.sent"&&s.current.as_ref().is_some_and(|c|c.id==args["id"]&&c.peer==peer&&c.subscription==sub&&args["attempt"].as_str().is_some_and(|a|c.confirmed.iter().any(|n|n==a))){return Ok(view(s))}
 let p=s.proof.as_mut().filter(|p|args["id"]==p.id&&p.peer==peer&&p.subscription==sub).ok_or("Verification recipient or SIM mismatch")?;
 match op {
  "probe.claim"=>{
   if p.phase!="queued"{return Err("Verification reply already claimed".into())}
   let token=fresh();p.attempt=Some(token.clone());p.phase="sending".into();p.observed=time;
   Ok(json!({"id":p.id,"peer":p.peer,"subscription":p.subscription,"attempt":token,"wire":format!("!OKW1|C|{}|0|wt1",p.id)}))
  },
  "probe.sent"=>{
   if p.phase!="sending"||args["attempt"].as_str()!=p.attempt.as_deref(){return Err("Stale verification reply attempt".into())}
   match args["outcome"].as_str(){Some("accepted")=>{p.phase="verified".into();p.attempt=None;p.observed=time},Some("failed"|"uncertain")=>s.proof=None,_=>return Err("Invalid verification reply outcome".into())}
   Ok(json!({"verification_reply":"processed"}))
  },_=>Err("Unsupported incoming operation".into())
 }
}

#[cfg(test)] mod tests {
 use super::*;
 const OWNER:&str="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
 fn begin(root:&Path,time:u64)->Value{run(root,&json!({"operation":"begin","peer":"+15550101234","subscription":2}),time,OWNER,true).unwrap()}
 fn req(s:&Value,op:&str)->Value {json!({"operation":op,"id":s["id"],"peer":s["peer"],"subscription":s["subscription"]})}
 fn receive(root:&Path,s:&Value,kind:&str,time:u64)->Result<Value>{let mut a=req(s,"receive");a["wire"]=json!(format!("!OKW1|{kind}|{}|0|wt1",s["id"].as_str().unwrap()));run(root,&a,time,OWNER,true)}
 fn sent(root:&Path,s:&Value,time:u64)->Value {let claim=run(root,&req(s,"claim"),time,OWNER,true).unwrap();let mut a=req(s,"sent");a["attempt"]=claim["attempt"].clone();a["outcome"]=json!("accepted");run(root,&a,time+1,OWNER,true).unwrap()}
 fn inbound(id:&str,kind:&str)->Value {json!({"operation":"incoming","allowed":true,"peer":"+15550101234","subscription":2,"wire":format!("!OKW1|{kind}|{id}|0|wt1")})}
 fn verify(root:&Path,id:&str,time:u64) {
  let mut a=inbound(id,"P");run(root,&a,time,OWNER,true).unwrap();
  a["id"]=json!(id);a["operation"]=json!("probe.claim");let claim=run(root,&a,time+1,OWNER,true).unwrap();assert!(claim["wire"].as_str().unwrap().starts_with("!OKW1|C|"));
  assert!(run(root,&a,time+1,OWNER,true).is_err());
  a["operation"]=json!("probe.sent");a["attempt"]=claim["attempt"].clone();a["outcome"]=json!("accepted");run(root,&a,time+2,OWNER,true).unwrap();
 }
 #[test] fn incoming_requires_confirmed_matching_probe_and_explicit_consent(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let id="c".repeat(32);
  assert!(run(r,&inbound(&id,"I"),1000,OWNER,true).is_err());verify(r,&id,1000);
  let mut bad=inbound(&id,"I");bad["peer"]=json!("5559999");assert!(run(r,&bad,1003,OWNER,true).is_err());bad["peer"]=json!("+15550101234");bad["subscription"]=json!(3);assert!(run(r,&bad,1003,OWNER,true).is_err());
  let invited=run(r,&inbound(&id,"I"),1003,OWNER,true).unwrap();assert_eq!(invited["phase"],"incoming");assert!(invited["incoming"].as_bool().unwrap());
  let before=std::fs::read(r.join("walkie-session.json")).unwrap();run(r,&inbound(&id,"I"),1004,OWNER,true).unwrap();assert_eq!(before,std::fs::read(r.join("walkie-session.json")).unwrap());
  assert!(run(r,&req(&invited,"claim"),1004,OWNER,true).is_err());
  assert_eq!(run(r,&req(&invited,"accept"),1004,OWNER,true).unwrap()["phase"],"accept_queued");
  assert_eq!(sent(r,&invited,1005)["phase"],"connected");
 }
 #[test] fn incoming_decline_timeout_restart_and_replay_cannot_join(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let id="c".repeat(32);verify(r,&id,1000);
  let invited=run(r,&inbound(&id,"I"),1003,OWNER,true).unwrap();run(r,&req(&invited,"decline"),1004,OWNER,true).unwrap();assert_eq!(sent(r,&invited,1005)["reason"],"declined");
  assert!(run(r,&inbound(&id,"I"),1007,OWNER,true).is_err());assert!(run(r,&inbound(&id,"P"),1007,OWNER,true).is_err());
  let id="d".repeat(32);verify(r,&id,50000);let invited=run(r,&inbound(&id,"I"),50003,OWNER,true).unwrap();
  assert_eq!(view(&recovered(r,95003,OWNER).unwrap())["reason"],"timeout");assert!(run(r,&req(&invited,"accept"),95004,OWNER,true).is_err());
  let id="e".repeat(32);verify(r,&id,100000);let invited=run(r,&inbound(&id,"I"),100003,OWNER,true).unwrap();
  assert_eq!(view(&recovered(r,100004,&"f".repeat(32)).unwrap())["reason"],"interrupted");assert!(run(r,&req(&invited,"accept"),100005,OWNER,true).is_err());
 }
 #[test] fn blocked_malformed_unconfirmed_expired_and_busy_probes_do_not_auto_reply(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let id="c".repeat(32);let mut a=inbound(&id,"P");a["allowed"]=json!(false);assert!(run(r,&a,1000,OWNER,true).is_err());assert!(!r.join("walkie-session.json").exists());
  for wire in [format!("!ICW1|P|{id}|0|wt1"),format!("!OKW1|P|{id}|1|wt1"),"x".repeat(141)] {a["allowed"]=json!(true);a["wire"]=json!(wire);assert!(run(r,&a,1000,OWNER,true).is_err());}
  a=inbound(&id,"P");run(r,&a,1000,OWNER,true).unwrap();assert!(run(r,&inbound(&id,"I"),1001,OWNER,true).is_err());assert!(run(r,&inbound(&"d".repeat(32),"P"),1001,OWNER,true).is_err());
  assert!(run(r,&inbound(&id,"I"),46000,OWNER,true).is_err());assert!(load(r).unwrap().proof.is_none());
  assert!(run(r,&inbound(&id,"P"),47000,OWNER,true).is_err());
  let s=begin(r,50000);let mut foreign=inbound(&"e".repeat(32),"P");foreign["peer"]=json!("+15559999999");assert!(run(r,&foreign,50001,OWNER,true).is_err());end(r,"cancelled").unwrap();assert!(receive(r,&s,"A",50002).is_err());
 }
 #[test] fn automatic_reply_limits_survive_cancellation_and_restart(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let id="c".repeat(32);verify(r,&id,1000);end(r,"cancelled").unwrap();
  assert!(run(r,&inbound(&"d".repeat(32),"P"),2000,OWNER,true).is_err());
  // All attempts reserve quota before exposing any automatic reply.
  for n in 1..30 {let id=format!("{n:032x}");run(r,&inbound(&id,"P"),1000+n*3000,OWNER,true).unwrap();end(r,"cancelled").unwrap();}
  let a=inbound(&"e".repeat(32),"P");assert!(run(r,&a,200000,&"f".repeat(32),true).is_err());assert_eq!(load(r).unwrap().quota.answered,30);
 }
 #[test] fn outgoing_verification_invite_accept_and_duplicate_do_not_extend_deadlines(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let s=begin(r,1000);
  assert!(receive(r,&s,"A",1001).is_err());assert!(receive(r,&s,"C",1001).is_err());
  sent(r,&s,1002);assert_eq!(receive(r,&s,"C",1004).unwrap()["phase"],"invite_queued");
  let before=std::fs::read(r.join("walkie-session.json")).unwrap();receive(r,&s,"C",1005).unwrap();assert_eq!(before,std::fs::read(r.join("walkie-session.json")).unwrap());
  assert!(receive(r,&s,"A",1005).is_err());sent(r,&s,1006);
  assert_eq!(receive(r,&s,"A",1008).unwrap()["phase"],"connected");
  let before=std::fs::read(r.join("walkie-session.json")).unwrap();receive(r,&s,"A",1010).unwrap();assert_eq!(before,std::fs::read(r.join("walkie-session.json")).unwrap());
  assert_eq!(receive(r,&s,"Q",1011).unwrap()["reason"],"remote_ended");assert!(receive(r,&s,"A",1012).is_err());
 }
 #[test] fn mismatched_sender_sim_id_legacy_and_replayed_attempt_fail_without_mutation(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let s=begin(r,1000);sent(r,&s,1001);
  let mut a=req(&s,"receive");a["wire"]=json!(format!("!OKW1|C|{}|0|wt1",s["id"].as_str().unwrap()));let before=std::fs::read(r.join("walkie-session.json")).unwrap();
  for (key,value) in [("peer",json!("+15559999999")),("subscription",json!(3)),("id",json!("b".repeat(32))),("wire",json!(format!("!ICW1|C|{}|0|wt1",s["id"].as_str().unwrap()))),("wire",json!(format!("!OKW1|C|{}|0|wt1","b".repeat(32))))] {let mut bad=a.clone();bad[key]=value;assert!(run(r,&bad,1003,OWNER,true).is_err());assert_eq!(before,std::fs::read(r.join("walkie-session.json")).unwrap());}
  assert!(run(r,&req(&s,"claim"),1003,OWNER,true).is_err());
  let mut bad=req(&s,"sent");bad["attempt"]=json!("b".repeat(32));bad["outcome"]=json!("accepted");assert!(run(r,&bad,1003,OWNER,true).is_err());
 }
 #[test] fn timeout_cancellation_restart_clock_rollback_and_one_active_session(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let s=begin(r,1000);
  assert!(run(r,&json!({"operation":"begin","peer":"5554321","subscription":3}),1001,OWNER,true).is_err());
  assert_eq!(view(&recovered(r,19000,OWNER).unwrap())["reason"],"timeout");assert!(receive(r,&s,"A",19001).is_err());
  let s=begin(r,20000);end(r,"cancelled").unwrap();assert!(receive(r,&s,"A",20001).is_err());
  begin(r,30000);assert_eq!(view(&recovered(r,30001,&"b".repeat(32)).unwrap())["reason"],"interrupted");
  begin(r,40000);assert_eq!(view(&recovered(r,39999,OWNER).unwrap())["reason"],"clock_changed");
 }
 #[test] fn invitations_expire_and_uncertain_sends_cannot_retry_automatically(){
  let d=tempfile::tempdir().unwrap();let r=d.path();let s=begin(r,1000);sent(r,&s,1001);receive(r,&s,"C",1003).unwrap();sent(r,&s,1004);
  assert_eq!(view(&recovered(r,46005,OWNER).unwrap())["reason"],"timeout");assert!(receive(r,&s,"A",46006).is_err());
  let s=begin(r,50000);let claim=run(r,&req(&s,"claim"),50001,OWNER,true).unwrap();let mut a=req(&s,"sent");a["attempt"]=claim["attempt"].clone();a["outcome"]=json!("uncertain");assert_eq!(run(r,&a,50002,OWNER,true).unwrap()["reason"],"uncertain");assert!(run(r,&req(&s,"claim"),50003,OWNER,true).is_err());
 }
 #[test] fn idle_expiry_corruption_quota_and_closed_production_gate(){
  let d=tempfile::tempdir().unwrap();let r=d.path();
  assert!(host(r,&json!({"operation":"begin","peer":"5551234","subscription":2,"ready":true})).is_err());assert!(!r.join("walkie-session.json").exists());
  let s=begin(r,1000);sent(r,&s,1001);receive(r,&s,"C",1003).unwrap();sent(r,&s,1004);receive(r,&s,"A",1006).unwrap();assert_eq!(view(&recovered(r,301006,OWNER).unwrap())["reason"],"idle_timeout");
  std::fs::write(r.join("walkie-session.json"),b"{broken").unwrap();assert!(snapshot(r).is_err());assert_eq!(std::fs::read(r.join("walkie-session.json")).unwrap(),b"{broken");
  std::fs::remove_file(r.join("walkie-session.json")).unwrap();std::fs::write(r.join("large.bin"),vec![0;16*1024*1024]).unwrap();assert!(run(r,&json!({"operation":"begin","peer":"5551234","subscription":2}),50000,OWNER,true).is_err());
 }
}
