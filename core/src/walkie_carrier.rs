//! Trusted host carrier bridge. Guest services never reach this entry point.
//! Android supplies current role/permission/SIM/policy facts on every operation.
use crate::Result;
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::path::Path;
pub(crate) fn ready(j:&Value)->Result<i32>{
 let f=&j["facts"];
 if f["mms_verified"]!=true&&f["carrier_test_enabled"]!=true{return Err("Enable carrier phone testing before using Walkie".into())}
 for key in ["default_role","send_sms","receive_sms"] {
  if f[key]!=true{return Err("Complete messaging Setup and carrier verification before using Walkie. No invitation was sent.".into())}
 }
 if f["allowed"]!=true{return Err("This recipient is blocked or could not be checked".into())}
 f["subscription"].as_i64().filter(|v|(0..=i32::MAX as i64).contains(v)).map(|v|v as i32).ok_or("Choose an active SIM".into())
}
pub(crate) fn service(root:&Path,j:&Value)->Result<Value>{
 let op=j["operation"].as_str().ok_or("Missing carrier operation")?;
 if op=="state"{return crate::walkie_session::snapshot(root)}
 if op=="peek"{return crate::walkie_session::peek_control(root)}
 let sub=ready(j)?;
 if let Some(peer)=j["peer"].as_str(){if crate::messaging::phone(peer)?!=crate::messaging::phone(j["facts"]["peer"].as_str().ok_or("Missing checked peer")?)?{return Err("Carrier policy recipient mismatch".into())}}
 let mut a=j.clone();a["subscription"]=json!(sub);a["allowed"]=json!(true);
 match op {
  "begin"=>{let selected=crate::walkie_store::selected(root)?;a["peer"]=selected["peer"].clone();if a["peer"]!=j["facts"]["peer"]{return Err("Selected recipient changed after confirmation".into())}if selected["subscription"].as_i64().is_some_and(|s|s>=0&&s!=sub as i64){return Err("The saved recipient uses a different SIM".into())}crate::walkie_session::carrier(root,&a)},
  "authorize.send"=>{
   if j["kind"]=="clip"{let clip=crate::walkie_clips::component(root,j["clip"].as_str().ok_or("Missing clip")?,false)?;
    if clip["state"]!="sending"||clip["attempt"]!=j["attempt"]||clip["binding"]["peer"]!=j["facts"]["peer"]||clip["binding"]["subscription"]!=sub{return Err("Voice send authorization expired".into())}
   }else{crate::walkie_session::authorize_claim(root,&a)?;}Ok(json!({"authorized":true}))
  },
  "accept"=>{let current=crate::walkie_session::snapshot(root)?;if current["id"]!=j["id"]||current["peer"]!=j["peer"]||current["peer"]!=j["facts"]["peer"]||current["subscription"]!=sub{return Err("Invitation recipient changed".into())}crate::walkie_store::service(root,"walkie.prepare",&json!({"peer":current["peer"],"subscription":sub}))?;crate::walkie_session::carrier(root,&a)},
  "classify"=>{let raw=j["wire"].as_str().ok_or("Missing control")?;if raw.starts_with("!OKW1|P|"){let p:Vec<_>=raw.split('|').collect();if p.len()!=5||!crate::valid_id(p[2])||p[3]!="0"||p[4]!="wt1"{return Err("Invalid probe".into())}}else{crate::walkie::Frame::parse(&raw.strip_prefix("!OKW1|").map(|s|format!("!ICW1|{s}")).ok_or("Invalid protocol")?)?;}Ok(json!({"valid":true}))},
  "next"=>{let pending=crate::walkie_session::peek_control(root)?;if !pending.is_null()&&(pending["peer"]!=j["facts"]["peer"]||pending["subscription"]!=sub){return Err("Control recipient changed".into())}crate::walkie_session::next_control(root)},
  "receive"=>{
   let raw=a["wire"].as_str().ok_or("Missing control")?;
   if raw.starts_with("!OKW1|P|")||raw.starts_with("!OKW1|I|"){a["operation"]=json!("incoming");return crate::walkie_session::carrier(root,&a)}
   let frame=crate::walkie::Frame::parse(&raw.strip_prefix("!OKW1|").map(|s|format!("!ICW1|{s}")).ok_or("Invalid control protocol")?)?;
   if let crate::walkie::Control::Received(id)|crate::walkie::Control::Heard(id)=&frame.control {
    let clip=crate::walkie_clips::component(root,id,false)?;let b=&clip["binding"];
    if b["outgoing"]!=true||b["session"]!=frame.session||b["peer"]!=crate::messaging::phone(a["peer"].as_str().ok_or("Missing sender")?)?||b["subscription"]!=sub{return Err("Receipt sender/session/SIM mismatch".into())}
    return crate::walkie_clips::host(root,&json!({"operation":"receipt","binding":b,"result":if matches!(frame.control,crate::walkie::Control::Heard(_)){"heard"}else{"delivered"}}))
   }
   a["id"]=json!(frame.session);crate::walkie_session::carrier(root,&a)
  },
  "clip.send"=>{
   let id=j["clip"].as_str().ok_or("Choose a voice clip")?;let clip=crate::walkie_clips::component(root,id,true)?;let b=&clip["binding"];
   let state=crate::walkie_session::snapshot(root)?;
   if b["outgoing"]!=true||b["subscription"]!=sub||b["peer"]!=j["facts"]["peer"]{return Err("Voice clip recipient/SIM changed".into())}
   let live=state["phase"]=="connected"&&b["session"]==state["id"]&&b["peer"]==state["peer"];
   if !live {let selected=crate::walkie_store::selected(root)?;if selected["id"]!=b["session"]||selected["peer"]!=b["peer"]{return Err("Choose this saved recipient before retrying the voice message".into())}}
   let attempt=crate::walkie_clips::host(root,&json!({"operation":"attempt","binding":b}))?;
   let bytes=clip["bytes"].as_array().ok_or("Missing audio")?.iter().map(|v|v.as_u64().filter(|n|*n<=255).map(|n|n as u8).ok_or("Invalid audio".into())).collect::<Result<Vec<_>>>()?;
   let metadata=if live{format!("!OKV1|{}|{}|{}|{}|{}",b["session"].as_str().unwrap(),id,clip["sequence"].as_u64().unwrap_or(0),clip["mime"].as_str().unwrap(),format!("{:x}",Sha256::digest(&bytes)))}else{"Voice message".into()};
   Ok(json!({"binding":b,"attempt":attempt["attempt"],"mime":clip["mime"],"bytes":clip["bytes"],"text":metadata}))
  },
  "clip.result"=>crate::walkie_clips::host(root,&json!({"operation":"carrier.result","binding":j["binding"],"attempt":j["attempt"],"result":j["result"]})),
  "clip.receive"=>{
   let text=j["text"].as_str().ok_or("Missing voice metadata")?;
   if text.len()>220||!text.is_ascii(){return Err("Invalid voice metadata".into())}
   let f:Vec<_>=text.split('|').collect();
   if f.len()!=6||f[0]!="!OKV1"||!crate::valid_id(f[1])||!crate::valid_id(f[2])||!["audio/amr","audio/amr-wb"].contains(&f[4])||f[5].len()!=64||!f[5].bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return Err("Invalid voice metadata".into())}
   if f[3].is_empty()||f[3].len()>7||!f[3].bytes().all(|c|c.is_ascii_digit()){return Err("Invalid voice sequence".into())}
   let sequence=f[3].parse::<u32>().ok().filter(|n|*n<=1_000_000).ok_or("Invalid voice sequence")?;
   let peer=crate::messaging::phone(j["peer"].as_str().ok_or("Missing sender")?)?;let state=crate::walkie_session::snapshot(root)?;
   if !["connected","ended"].iter().any(|p|state["phase"]==*p)||state["id"]!=f[1]||state["peer"]!=peer||state["subscription"]!=sub{return Err("Voice clip sender/session/SIM mismatch".into())}
   let raw=j["bytes"].as_array().filter(|v|v.len()<=65536).ok_or("Voice clip too large")?.iter().map(|v|v.as_u64().filter(|n|*n<=255).map(|n|n as u8).ok_or("Invalid audio".into())).collect::<Result<Vec<_>>>()?;
   if format!("{:x}",Sha256::digest(&raw))!=f[5]{return Err("Voice clip digest mismatch".into())}
   crate::walkie::audio_duration(&raw,f[4])?;
   if state["phase"]=="ended" {return Ok(json!({"fallback":true}))}
   let b=json!({"id":f[2],"session":f[1],"peer":peer,"subscription":sub,"outgoing":false});
   let out=crate::walkie_clips::host(root,&json!({"operation":"ingest","binding":b,"sequence":sequence,"mime":f[4],"bytes":j["bytes"]}))?;
   crate::walkie_session::receipt(root,&b,false)?;Ok(out)
  },
  _=>crate::walkie_session::carrier(root,&a)
 }
}

#[cfg(test)] mod tests {
 use super::*;
 const A:&str="+15550000001";const B:&str="+15550000002";
 fn facts(peer:&str,sub:i32)->Value{json!({"mms_verified":true,"default_role":true,"send_sms":true,"receive_sms":true,"allowed":true,"peer":peer,"subscription":sub})}
 fn call(root:&Path,op:&str,peer:&str,sub:i32,extra:Value)->Result<Value>{let mut a=extra;a["operation"]=json!(op);a["facts"]=facts(peer,sub);service(root,&a)}
 fn next(root:&Path,peer:&str,sub:i32)->Value{call(root,"next",peer,sub,json!({})).unwrap()}
 fn deliver(from:&Path,to:&Path,source:&str,peer:&str,send_sub:i32,recv_sub:i32)->Value {
  let packet=next(from,peer,send_sub);assert!(!packet.is_null());
  // Deliberately deliver before the platform's SENT callback: carrier ordering is not guaranteed.
  let received=call(to,"receive",source,recv_sub,json!({"peer":source,"wire":packet["wire"]})).unwrap();
  let mut callback=packet["callback"].clone();let op=callback["operation"].as_str().unwrap().to_owned();callback["attempt"]=packet["attempt"].clone();callback["outcome"]=json!("accepted");
  call(from,&op,peer,send_sub,callback).unwrap();received
 }
 fn pair()->(tempfile::TempDir,tempfile::TempDir){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();
  crate::walkie_store::service(a.path(),"walkie.prepare",&json!({"peer":B,"subscription":2})).unwrap();
  crate::walkie_store::service(b.path(),"walkie.prepare",&json!({"peer":A,"subscription":3})).unwrap();
  call(a.path(),"begin",B,2,json!({})).unwrap();
  deliver(a.path(),b.path(),A,B,2,3);deliver(b.path(),a.path(),B,A,3,2);deliver(a.path(),b.path(),A,B,2,3);
  let invite=crate::walkie_session::snapshot(b.path()).unwrap();
  call(b.path(),"accept",A,3,json!({"peer":A,"id":invite["id"]})).unwrap();
  deliver(b.path(),a.path(),B,A,3,2);
  assert_eq!(crate::walkie_session::snapshot(a.path()).unwrap()["phase"],"connected");assert_eq!(crate::walkie_session::snapshot(b.path()).unwrap()["phase"],"connected");(a,b)
 }
 #[test] fn two_peer_driver_connects_with_early_peer_replies_and_grants_one_microphone(){
  let (a,b)=pair();let s=crate::walkie_session::snapshot(a.path()).unwrap();
  call(a.path(),"floor.request",B,2,json!({"id":s["id"],"peer":B})).unwrap();
  assert_eq!(crate::walkie_session::snapshot(a.path()).unwrap()["can_record"],true);
  deliver(a.path(),b.path(),A,B,2,3);
  assert_eq!(crate::walkie_session::snapshot(b.path()).unwrap()["can_record"],false);
  call(a.path(),"floor.release",B,2,json!({"id":s["id"],"peer":B})).unwrap();deliver(a.path(),b.path(),A,B,2,3);
  call(b.path(),"floor.request",A,3,json!({"id":s["id"],"peer":A})).unwrap();
  assert_eq!(crate::walkie_session::snapshot(b.path()).unwrap()["can_record"],false);
  deliver(b.path(),a.path(),B,A,3,2);deliver(a.path(),b.path(),A,B,2,3);
  assert_eq!(crate::walkie_session::snapshot(b.path()).unwrap()["can_record"],true);assert_eq!(crate::walkie_session::snapshot(a.path()).unwrap()["can_record"],false);
 }
 #[test] fn ended_session_voice_falls_back_only_after_binding_hash_and_audio_validation(){
  let (a,b)=pair();let state=crate::walkie_session::snapshot(a.path()).unwrap();let id="d".repeat(32);
  let mut bytes=b"#!AMR\n".to_vec();for _ in 0..20{bytes.push(60);bytes.extend([0;31]);}
  let binding=json!({"id":id,"session":state["id"],"peer":B,"subscription":2,"outgoing":true});
  crate::walkie_clips::host(a.path(),&json!({"operation":"ingest","binding":binding,"bytes":bytes,"mime":"audio/amr","sequence":1})).unwrap();
  let packet=call(a.path(),"clip.send",B,2,json!({"clip":id})).unwrap();
  crate::walkie_session::end(b.path(),"cancelled").unwrap();
  let incoming=json!({"peer":A,"text":packet["text"],"bytes":packet["bytes"]});
  assert_eq!(call(b.path(),"clip.receive",A,3,incoming.clone()).unwrap()["fallback"],true);
  let mut corrupt=incoming.clone();corrupt["bytes"][10]=json!(255);assert!(call(b.path(),"clip.receive",A,3,corrupt).is_err());
  assert!(call(b.path(),"clip.receive",B,3,incoming).is_err());
  assert!(crate::walkie_clips::service(b.path(),"walkie.clips",&json!({})).unwrap().as_array().unwrap().is_empty());
 }
 #[test] fn actual_audio_envelope_delivery_dedup_and_receiver_proof(){
  let (a,b)=pair();let s=crate::walkie_session::snapshot(a.path()).unwrap();let id="c".repeat(32);
  let mut bytes=b"#!AMR\n".to_vec();for _ in 0..20{bytes.push(60);bytes.extend([0;31]);}
  let binding=json!({"id":id,"session":s["id"],"peer":B,"subscription":2,"outgoing":true});
  crate::walkie_clips::host(a.path(),&json!({"operation":"ingest","binding":binding,"bytes":bytes,"mime":"audio/amr","sequence":1})).unwrap();
  let packet=call(a.path(),"clip.send",B,2,json!({"clip":id})).unwrap();
  let incoming=json!({"peer":A,"text":packet["text"],"bytes":packet["bytes"]});
  assert_eq!(call(b.path(),"clip.receive",A,3,incoming.clone()).unwrap()["duplicate"],false);
  assert_eq!(call(b.path(),"clip.receive",A,3,incoming.clone()).unwrap()["duplicate"],true);
  deliver(b.path(),a.path(),B,A,3,2);
  let rows=crate::walkie_clips::service(a.path(),"walkie.clips",&json!({})).unwrap();assert_eq!(rows[0]["state"],"delivered");
  call(a.path(),"clip.result",B,2,json!({"binding":binding,"attempt":packet["attempt"],"result":"failed"})).unwrap();
  assert_eq!(crate::walkie_clips::service(a.path(),"walkie.clips",&json!({})).unwrap()[0]["state"],"delivered");
  let mut bad=incoming;bad["bytes"][10]=json!(42);assert!(call(b.path(),"clip.receive",A,3,bad).is_err());
 }
 #[test] fn peer_confirmation_wins_over_late_failed_sms_callback(){
  let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();
  crate::walkie_store::service(a.path(),"walkie.prepare",&json!({"peer":B,"subscription":2})).unwrap();
  call(a.path(),"begin",B,2,json!({})).unwrap();let probe=next(a.path(),B,2);
  call(b.path(),"receive",A,3,json!({"peer":A,"wire":probe["wire"]})).unwrap();let reply=next(b.path(),A,3);
  call(a.path(),"receive",B,2,json!({"peer":B,"wire":reply["wire"]})).unwrap();
  let mut late=probe["callback"].clone();late["attempt"]=probe["attempt"].clone();late["outcome"]=json!("failed");
  assert_eq!(call(a.path(),"sent",B,2,late).unwrap()["phase"],"invite_queued");
  let invite=next(a.path(),B,2);assert_eq!(call(b.path(),"receive",A,3,json!({"peer":A,"wire":invite["wire"]})).unwrap()["phase"],"incoming");
  let mut late=reply["callback"].clone();late["attempt"]=reply["attempt"].clone();late["outcome"]=json!("failed");
  assert_eq!(call(b.path(),"probe.sent",A,3,late).unwrap()["phase"],"incoming");
 }
 #[test] fn standalone_voice_message_uses_plain_mms_and_retry_attempt_is_not_a_receipt(){
  let d=tempfile::tempdir().unwrap();let r=d.path();
  let selected=crate::walkie_store::service(r,"walkie.prepare",&json!({"peer":B,"subscription":2})).unwrap();
  let mut bytes=b"#!AMR\n".to_vec();for _ in 0..20{bytes.push(60);bytes.extend([0;31]);}
  let binding=json!({"id":"c".repeat(32),"session":selected["selected"],"peer":B,"subscription":2,"outgoing":true});
  crate::walkie_clips::host(r,&json!({"operation":"ingest","binding":binding,"sequence":0,"mime":"audio/amr","bytes":bytes})).unwrap();
  let p=call(r,"clip.send",B,2,json!({"clip":binding["id"]})).unwrap();assert_eq!(p["text"],"Voice message");
  call(r,"clip.result",B,2,json!({"binding":binding,"attempt":p["attempt"],"result":"uncertain"})).unwrap();
  let retry=call(r,"clip.send",B,2,json!({"clip":binding["id"]})).unwrap();assert_ne!(p["attempt"],retry["attempt"]);
  assert!(call(r,"clip.result",B,2,json!({"binding":binding,"attempt":p["attempt"],"result":"carrier"})).is_err());
  assert_eq!(crate::walkie_clips::service(r,"walkie.clips",&json!({})).unwrap()[0]["state"],"sending");
 }
 #[test] fn cancellation_invalidates_an_already_claimed_control(){
  let d=tempfile::tempdir().unwrap();let r=d.path();crate::walkie_store::service(r,"walkie.prepare",&json!({"peer":B,"subscription":2})).unwrap();
  call(r,"begin",B,2,json!({})).unwrap();let mut p=next(r,B,2);p["operation"]=json!("authorize.send");p["facts"]=facts(B,2);assert!(service(r,&p).is_ok());
  crate::walkie_session::end(r,"cancelled").unwrap();assert!(service(r,&p).is_err());
 }
 #[test] fn explicit_carrier_trial_keeps_role_and_permission_checks(){let mut j=json!({"facts":facts(B,2)});j["facts"]["mms_verified"]=json!(false);assert!(ready(&j).is_err());j["facts"]["carrier_test_enabled"]=json!(true);assert_eq!(ready(&j).unwrap(),2);j["facts"]["default_role"]=json!(false);assert!(ready(&j).is_err());j["facts"]["default_role"]=json!(true);j["facts"]["send_sms"]=json!(false);assert!(ready(&j).is_err());}
 #[test] fn every_readiness_fact_and_changed_recipient_fail_before_claim(){
  let d=tempfile::tempdir().unwrap();crate::walkie_store::service(d.path(),"walkie.prepare",&json!({"peer":B,"subscription":2})).unwrap();
  for field in ["mms_verified","default_role","send_sms","receive_sms","allowed"] {
   let mut a=json!({"operation":"begin","facts":facts(B,2)});a["facts"][field]=json!(false);assert!(service(d.path(),&a).is_err());assert!(!d.path().join("walkie-session.json").exists());
  }
  assert!(call(d.path(),"begin",A,2,json!({})).is_err());
 }
}
