//! Walkie conversion phase one: bounded control messages, microphone ownership and AMR checks.
//! These rules authorize no SMS, MMS, microphone or playback action by themselves.
use crate::Result;
use serde::{Deserialize,Serialize};
const MAX_SERIAL:u32=1_000_000;
fn serial(raw:&str)->Result<u32>{if raw.is_empty()||raw.len()>7||!raw.bytes().all(|b|b.is_ascii_digit()){return Err("Invalid Walkie Sequence".into())}raw.parse::<u32>().ok().filter(|n|*n<=MAX_SERIAL).ok_or_else(||"Invalid Walkie Sequence".into())}
#[derive(Debug,Clone,PartialEq,Eq,Serialize,Deserialize)]
#[serde(tag="kind",content="request")]
pub enum Owner {Free,Host,Guest(u32)}
impl Owner {
 fn parse(raw:&str)->Result<Self>{match raw {"F"=>Ok(Self::Free),"H"=>Ok(Self::Host),_=>{let request=raw.strip_prefix("G,").ok_or("Invalid Microphone Owner")?;let request=serial(request)?;if request==0{return Err("Invalid Microphone Request".into())}Ok(Self::Guest(request))}}}
 fn wire(&self)->String{match self {Self::Free=>"F".into(),Self::Host=>"H".into(),Self::Guest(n)=>format!("G,{n}")}}
}
#[derive(Debug,Clone,PartialEq,Eq,Serialize,Deserialize)]
pub enum Control {Check,Invite,Accept,Decline,Presence(bool),Floor(Owner),Request,Cancel,Release,Query,Acknowledge,Received(String),Heard(String)}
#[derive(Debug,Clone,PartialEq,Eq,Serialize,Deserialize)]
pub struct Frame {pub session:String,pub serial:u32,pub control:Control}
impl Frame {
 pub fn parse(raw:&str)->Result<Self>{
  if raw.len()>140||!raw.is_ascii(){return Err("Invalid Walkie Control Frame".into())}
  let fields:Vec<_>=raw.split('|').collect();if fields.len()!=5||fields[0]!="!ICW1"||!crate::valid_id(fields[2]){return Err("Invalid Walkie Control Frame".into())}
  let n=serial(fields[3])?;let value=fields[4];let control=match fields[1] {
   "C"|"I"|"A"|"D" if n==0&&value=="wt1"=>match fields[1]{"C"=>Control::Check,"I"=>Control::Invite,"A"=>Control::Accept,_=>Control::Decline},
   "V" if n>0&&["open","away"].contains(&value)=>Control::Presence(value=="open"),
   "S" if n>0=>Control::Floor(Owner::parse(value)?),
   "R" if n>0&&value=="ask"=>Control::Request,
   "X" if n>0&&value=="cancel"=>Control::Cancel,
   "L" if n>0&&value=="done"=>Control::Release,
   "Q" if value=="wt1"=>Control::Query,"Y" if value=="wt1"=>Control::Acknowledge,
   "T" if crate::valid_id(value)=>Control::Received(value.into()),"H" if crate::valid_id(value)=>Control::Heard(value.into()),
   _=>return Err("Invalid Walkie Control Payload".into())};
  Ok(Self{session:fields[2].into(),serial:n,control})
 }
 pub fn encode(&self)->Result<String>{let (kind,data)=match &self.control {
  Control::Check=>("C","wt1".into()),Control::Invite=>("I","wt1".into()),Control::Accept=>("A","wt1".into()),Control::Decline=>("D","wt1".into()),Control::Presence(v)=>("V",if *v{"open"}else{"away"}.into()),Control::Floor(o)=>("S",o.wire()),Control::Request=>("R","ask".into()),Control::Cancel=>("X","cancel".into()),Control::Release=>("L","done".into()),Control::Query=>("Q","wt1".into()),Control::Acknowledge=>("Y","wt1".into()),Control::Received(v)=>("T",v.clone()),Control::Heard(v)=>("H",v.clone())};
  let raw=format!("!ICW1|{kind}|{}|{}|{data}",self.session,self.serial);if Frame::parse(&raw)?!=*self{return Err("Invalid Walkie Control".into())}Ok(raw)
 }
}
#[derive(Debug,Clone,PartialEq,Eq,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Floor {pub epoch:u32,pub owner:Owner,pub cancelled:u32}
impl Default for Floor {fn default()->Self{Self{epoch:0,owner:Owner::Free,cancelled:0}}}
impl Floor {
 fn replace(&mut self,owner:Owner)->Result<()> {if self.epoch>=MAX_SERIAL{return Err("Microphone Sequence Exhausted. Reconnect.".into())}self.epoch+=1;self.owner=owner;Ok(())}
 pub fn host_take(&mut self)->Result<bool>{if self.owner!=Owner::Free{return Ok(false)}self.replace(Owner::Host)?;Ok(true)}
 pub fn grant(&mut self,request:u32)->Result<bool>{if request==0||request>MAX_SERIAL{return Err("Invalid Microphone Request".into())}if request<=self.cancelled{return Ok(false)}if self.owner==Owner::Guest(request){return Ok(true)}if self.owner!=Owner::Free{return Ok(false)}self.replace(Owner::Guest(request))?;Ok(true)}
 pub fn guest_release(&mut self,epoch:u32)->Result<bool>{if !matches!(self.owner,Owner::Guest(_))||epoch!=self.epoch{return Ok(false)}self.replace(Owner::Free)?;Ok(true)}
 pub fn host_release(&mut self)->Result<bool>{if self.owner!=Owner::Host{return Ok(false)}self.replace(Owner::Free)?;Ok(true)}
 pub fn cancel(&mut self,request:u32)->Result<bool>{if request==0||request>MAX_SERIAL{return Err("Invalid Microphone Request".into())}if self.owner==Owner::Guest(request){self.replace(Owner::Free)?;self.cancelled=self.cancelled.max(request);return Ok(true)}self.cancelled=self.cancelled.max(request);Ok(false)}
 pub fn apply(&mut self,epoch:u32,owner:Owner)->Result<bool>{if epoch>MAX_SERIAL||matches!(owner,Owner::Guest(n) if n==0||n>MAX_SERIAL){return Err("Invalid Remote Microphone State".into())}if epoch<self.epoch||(epoch==self.epoch&&owner!=self.owner){return Ok(false)}self.epoch=epoch;self.owner=owner;Ok(true)}
}
#[derive(Debug,PartialEq,Eq)]
pub enum Capture {Discard,Review,Send}
pub fn capture(recorded:bool,confirmed:bool,millis:u32,automatic:bool)->Capture {if !recorded||!confirmed||!(300..=12000).contains(&millis){Capture::Discard}else if automatic{Capture::Send}else{Capture::Review}}
pub fn autoplay(enabled:bool,foreground:bool,connected:bool,busy:bool,draft:bool,legacy:bool)->bool{enabled&&foreground&&connected&&!busy&&!draft&&!legacy}
/// RFC 4867 single-channel storage; the stricter original zero-padding-header policy is retained.
/// Returns structural duration, not a claim that speech can be decoded or heard.
pub fn audio_duration(bytes:&[u8],mime:&str)->Result<u32>{
 let (magic,bits):(&[u8],&[i32])=match mime {"audio/amr"=>(b"#!AMR\n",&[95,103,118,134,148,159,204,244,39,-1,-1,-1,-1,-1,-1,0]),"audio/amr-wb"=>(b"#!AMR-WB\n",&[132,177,253,285,317,365,397,461,477,40,-1,-1,-1,-1,0,0]),_=>return Err("Unsupported Walkie Audio Type".into())};
 if bytes.len()>65536||!bytes.starts_with(magic)||bytes.len()<=magic.len(){return Err("Invalid Walkie Audio".into())}let mut at=magic.len();let mut frames=0u32;
 while at<bytes.len(){let header=bytes[at];let kind=((header>>3)&15) as usize;let size=bits[kind];if header&0x83!=0||size<0{return Err("Invalid AMR Frame".into())}let length=1+(size as usize).div_ceil(8);if length>bytes.len()-at{return Err("Truncated AMR Frame".into())}at+=length;frames+=1;if frames>650{return Err("Voice Clip Is Too Long".into())}}
 Ok(frames*20)
}
/// Validate the entire recorder output before trimming at an AMR frame boundary.
/// Encoders may flush a few frames after the 12-second stop deadline.
pub fn capture_audio(bytes:&[u8],mime:&str)->Result<Vec<u8>> {
 audio_duration(bytes,mime)?;
 let (magic,bits):(&[u8],&[usize])=if mime=="audio/amr"{(b"#!AMR\n",&[95,103,118,134,148,159,204,244,39,0,0,0,0,0,0,0])}else{(b"#!AMR-WB\n",&[132,177,253,285,317,365,397,461,477,40,0,0,0,0,0,0])};
 let mut at=magic.len();for _ in 0..600 {if at==bytes.len(){break}let kind=((bytes[at]>>3)&15) as usize;at+=1+bits[kind].div_ceil(8);}
 let trimmed=bytes[..at].to_vec();if audio_duration(&trimmed,mime)?<300{return Err("Recording was too short; speak for at least a moment".into())}Ok(trimmed)
}
/// Host rules API only. It has no paths, device handles, transport or executable effects.
pub fn dispatch(j:&serde_json::Value)->Result<serde_json::Value>{
 use serde_json::json;
 match j["action"].as_str().ok_or("Missing Walkie Rule")? {
  "frame.decode"=>Ok(json!(Frame::parse(j["text"].as_str().ok_or("Missing Control Frame")?)?)),
  "frame.encode"=>{let f:Frame=serde_json::from_value(j["frame"].clone()).map_err(|_|"Invalid Control Frame")?;Ok(json!(f.encode()?))},
  "floor.transition"=>{let mut f=if j["floor"].is_null(){Floor::default()}else{serde_json::from_value::<Floor>(j["floor"].clone()).map_err(|_|"Invalid Microphone State")?};if f.epoch>MAX_SERIAL||f.cancelled>MAX_SERIAL||matches!(f.owner,Owner::Guest(n) if n==0||n>MAX_SERIAL){return Err("Invalid Microphone State".into())}let token=match j.get("token"){Some(v)=>v.as_u64().filter(|n|*n<=MAX_SERIAL as u64).ok_or("Invalid Microphone Token")? as u32,None=>0};let changed=match j["transition"].as_str(){Some("host.take")=>f.host_take()?,Some("host.release")=>f.host_release()?,Some("guest.grant")=>f.grant(token)?,Some("guest.release")=>f.guest_release(token)?,Some("guest.cancel")=>f.cancel(token)?,Some("remote.apply")=>{let owner:Owner=serde_json::from_value(j["owner"].clone()).map_err(|_|"Invalid Microphone Owner")?;f.apply(token,owner)?},_=>return Err("Unsupported Microphone Transition".into())};Ok(json!({"floor":f,"changed":changed}))},
  "capture.finish"=>{let ms=j["millis"].as_u64().and_then(|n|u32::try_from(n).ok()).unwrap_or(0);Ok(json!({"decision":match capture(j["recorded"]==true,j["confirmed"]==true,ms,j["automatic"]==true){Capture::Discard=>"discard",Capture::Review=>"review",Capture::Send=>"send"}}))},
  "audio.duration"=>{let raw=j["bytes"].as_array().filter(|b|b.len()<=65536).ok_or("Invalid Audio Bytes")?;let bytes=raw.iter().map(|n|n.as_u64().filter(|n|*n<=255).map(|n|n as u8).ok_or_else(||"Invalid Audio Byte".to_string())).collect::<Result<Vec<_>>>()?;Ok(json!({"millis":audio_duration(&bytes,j["mime"].as_str().ok_or("Missing Audio Type")?)?}))},
  _=>Err("Unsupported Walkie Rule".into())
 }
}
#[cfg(test)]mod tests {
 use super::*;
 #[test]fn control_roundtrip_and_invalid_frames(){let id="a".repeat(32);for (kind,n,data) in [("C",0,"wt1"),("I",0,"wt1"),("A",0,"wt1"),("D",0,"wt1"),("V",1,"open"),("V",2,"away"),("S",3,"H"),("S",3,"F"),("S",4,"G,12"),("R",5,"ask"),("X",6,"cancel"),("L",7,"done"),("Q",0,"wt1"),("Y",2,"wt1"),("T",0,id.as_str()),("H",0,id.as_str())]{let raw=format!("!ICW1|{kind}|{id}|{n}|{data}");let p=Frame::parse(&raw).unwrap();assert_eq!(Frame::parse(&p.encode().unwrap()).unwrap(),p)}for tail in ["C|{id}|1|wt1","R|{id}|0|ask","S|{id}|1|G,0","S|{id}|1|G,1000001","V|{id}|1|other","T|{id}|0|invalid","I|{id}|0|wt1|extra","Q|{id}|1000001|wt1"]{assert!(Frame::parse(&format!("!ICW1|{}",tail.replace("{id}",&id))).is_err())}assert!(Frame::parse(&format!("!ICW1|C|{}|0|wt1","A".repeat(32))).is_err());}
 #[test]fn floor_cancel_replay_epoch_and_no_steal(){let mut f=Floor::default();assert!(f.grant(10).unwrap());let epoch=f.epoch;assert!(!f.host_take().unwrap());assert!(f.grant(10).unwrap());assert_eq!(epoch,f.epoch);assert!(!f.guest_release(epoch-1).unwrap());assert!(f.cancel(10).unwrap());assert!(!f.grant(10).unwrap());assert!(f.host_take().unwrap());assert!(!f.apply(epoch,Owner::Free).unwrap());assert!(!f.apply(f.epoch,Owner::Free).unwrap());assert!(f.host_release().unwrap());let saved=serde_json::to_string(&f).unwrap();let restored:Floor=serde_json::from_str(&saved).unwrap();assert_eq!(f,restored);f.epoch=MAX_SERIAL;let before=f.clone();assert!(f.host_take().is_err());assert_eq!(before,f);}
 #[test]fn capture_and_autoplay_never_promote_cancelled_or_background_audio(){assert_eq!(capture(true,true,300,true),Capture::Send);assert_eq!(capture(true,true,12000,false),Capture::Review);for n in [0,299,12001]{assert_eq!(capture(true,true,n,true),Capture::Discard)}assert_eq!(capture(false,true,500,true),Capture::Discard);assert_eq!(capture(true,false,500,true),Capture::Discard);assert!(autoplay(true,true,true,false,false,false));for flags in [(false,true,true,false,false,false),(true,false,true,false,false,false),(true,true,false,false,false,false),(true,true,true,true,false,false),(true,true,true,false,true,false),(true,true,true,false,false,true)]{assert!(!autoplay(flags.0,flags.1,flags.2,flags.3,flags.4,flags.5))}}
 #[test]fn capture_trims_only_valid_complete_frames_and_rejects_bad_tail(){
 for (mime,magic,size,header) in [("audio/amr",b"#!AMR\n".as_slice(),32,60u8),("audio/amr-wb",b"#!AMR-WB\n".as_slice(),61,68u8)] {
 let mut b=magic.to_vec();for _ in 0..625{b.push(header);b.extend(std::iter::repeat_n(0,size-1));}
 let clean=capture_audio(&b,mime).unwrap();assert_eq!(audio_duration(&clean,mime).unwrap(),12000);
 let mut bad=b.clone();bad.push(128);assert!(capture_audio(&bad,mime).is_err());b.truncate(magic.len()+14*size);assert!(capture_audio(&b,mime).is_err());
 }}
 #[test]fn amr_structural_duration_and_size_boundaries(){for (mime,magic,size,header) in [("audio/amr",b"#!AMR\n".as_slice(),32,7u8<<3),("audio/amr-wb",b"#!AMR-WB\n".as_slice(),61,8u8<<3)]{let mut bytes=magic.to_vec();for _ in 0..600{bytes.push(header|4);bytes.extend(std::iter::repeat_n(0,size-1));}assert_eq!(audio_duration(&bytes,mime).unwrap(),12000);let mut truncated=bytes.clone();truncated.pop();assert!(audio_duration(&truncated,mime).is_err());bytes[magic.len()]|=128;assert!(audio_duration(&bytes,mime).is_err());}assert!(audio_duration(&vec![0;65537],"audio/amr").is_err());assert!(audio_duration(b"#!AMR\n", "audio/amr").is_err());assert!(audio_duration(b"#!AMR\n", "audio/other").is_err());}
}
