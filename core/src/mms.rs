//! Bounded MMS/WSP wire codec, implemented in Rust. No Java PDU runtime.
//! Wire field numbers follow OMA MMS encapsulation / WSP registry (see docs).
use crate::Result;
use serde_json::{json,Value};
use std::{fs,path::{Path,PathBuf}};
const LIMIT:usize=10*1024*1024;
fn text(out:&mut Vec<u8>,s:&str){if s.as_bytes().first().is_some_and(|b|*b>=128){out.push(127)}out.extend(s.as_bytes());out.push(0)}
fn uint(out:&mut Vec<u8>,mut v:usize){let mut groups=vec![(v&127)as u8];v>>=7;while v>0{groups.push(((v&127)as u8)|128);v>>=7}out.extend(groups.iter().rev())}
fn length(out:&mut Vec<u8>,n:usize){if n<=30{out.push(n as u8)}else{out.push(31);uint(out,n)}}
fn integer(out:&mut Vec<u8>,v:u64){let a=v.to_be_bytes();let first=a.iter().position(|b|*b!=0).unwrap_or(7);out.push((8-first)as u8);out.extend(&a[first..])}
fn encoded(out:&mut Vec<u8>,s:&str){let mut value=vec![0xea];text(&mut value,s);length(out,value.len());out.extend(value)}
#[derive(Clone,Debug)] struct Part {mime:String,name:String,charset:u64,data:Vec<u8>}
#[derive(Default,Debug)] struct Pdu {kind:u8,transaction:String,from:String,to:Vec<String>,date:u64,subject:String,location:String,status:u8,message_id:String,parts:Vec<Part>}
struct Reader<'a>{b:&'a [u8],p:usize}
impl<'a> Reader<'a>{
 fn new(b:&'a[u8])->Self{Self{b,p:0}}
 fn byte(&mut self)->Result<u8>{let v=*self.b.get(self.p).ok_or("Truncated MMS")?;self.p+=1;Ok(v)}
 fn peek(&self)->Result<u8>{self.b.get(self.p).copied().ok_or("Truncated MMS".into())}
 fn take(&mut self,n:usize)->Result<&'a[u8]>{let end=self.p.checked_add(n).ok_or("MMS length overflow")?;let b=self.b.get(self.p..end).ok_or("Truncated MMS field")?;self.p=end;Ok(b)}
 fn uint(&mut self)->Result<usize>{let mut value=0usize;for _ in 0..5{let b=self.byte()?;value=value.checked_mul(128).and_then(|v|v.checked_add((b&127)as usize)).ok_or("MMS length overflow")?;if b&128==0{return Ok(value)}}Err("Invalid MMS integer".into())}
 fn length(&mut self)->Result<usize>{let b=self.byte()?;if b==31{self.uint()}else if b<=30{Ok(b as usize)}else{Err("Invalid MMS value length".into())}}
 fn integer(&mut self)->Result<u64>{if self.peek()?&128!=0{return Ok((self.byte()?&127) as u64)}let n=self.byte()?as usize;if n>8{return Err("MMS integer too large".into())}let mut v=0;for b in self.take(n)?{v=(v<<8)|*b as u64}Ok(v)}
 fn string(&mut self)->Result<Vec<u8>>{if [127,34].contains(&self.peek()?){self.p+=1}let start=self.p;while self.byte()?!=0{if self.p-start>8192{return Err("MMS string too long".into())}}Ok(self.b[start..self.p-1].to_vec())}
 fn encoded(&mut self)->Result<String>{if self.peek()?<=31{let n=self.length()?;let b=self.take(n)?;let mut r=Reader::new(b);let charset=r.integer()?;let raw=r.string()?;decode(&raw,charset)}else{decode(&self.string()?,106)}}
 fn skip(&mut self)->Result<()>{let b=self.peek()?;if b<=31{let n=self.length()?;self.take(n)?;}else if b<128{self.string()?;}else{self.byte()?;}Ok(())}
 fn mime(&mut self)->Result<String>{let b=self.peek()?;if b>=128{self.byte()?;Ok(match b&127 {3=>"text/plain",7=>"text/x-vcard",0x1d=>"image/gif",0x1e=>"image/jpeg",0x1f=>"image/tiff",0x20=>"image/png",0x21=>"image/vnd.wap.wbmp",0x23=>"application/vnd.wap.multipart.mixed",0x26=>"application/vnd.wap.multipart.alternative",0x33=>"application/vnd.wap.multipart.related",0x27=>"application/xml",0x28=>"text/xml",_=>"application/octet-stream"}.into())}else{Ok(String::from_utf8_lossy(&self.string()?).to_lowercase())}}
 fn content(&mut self)->Result<(String,String,u64)>{if self.peek()?>31{return Ok((self.mime()?,String::new(),106))}let n=self.length()?;let mut r=Reader::new(self.take(n)?);let mime=r.mime()?;let mut name=String::new();let mut charset=106;while r.p<r.b.len(){match r.byte()?{0x81=>charset=r.integer()?,0x85|0x86|0x97|0x98=>name=String::from_utf8_lossy(&r.string()?).into_owned(),_=>r.skip()?}}Ok((mime,name,charset))}
}
fn decode(b:&[u8],charset:u64)->Result<String>{Ok(match charset{3|4=>b.iter().map(|v|char::from(*v)).collect(),1000|1013|1014|1015=>{if b.len()%2!=0{return Err("Invalid UTF-16 MMS text".into())}let le=charset==1014||b.starts_with(&[255,254]);let skip=if b.starts_with(&[255,254])||b.starts_with(&[254,255]){2}else{0};let words:Vec<u16>=b[skip..].chunks_exact(2).map(|c|if le{u16::from_le_bytes([c[0],c[1]])}else{u16::from_be_bytes([c[0],c[1]])}).collect();String::from_utf16_lossy(&words)},0|106=>String::from_utf8_lossy(b).into_owned(),_=>return Err(format!("Unsupported MMS text charset {charset}"))})}
fn multipart(r:&mut Reader,depth:usize,parts:&mut Vec<Part>)->Result<()> {
 if depth>3{return Err("MMS nesting too deep".into())}let count=r.uint()?;if count>64||parts.len()+count>64{return Err("Too many MMS parts".into())}
 for index in 0..count{let h=r.uint()?;let n=r.uint()?;if h>32768||n>LIMIT{return Err("MMS part exceeds limits".into())}let mut header=Reader::new(r.take(h)?);let (mime,mut name,charset)=header.content()?;
  while header.p<header.b.len(){let field=header.byte()?;if field==0x8e||field==0xc0{name=String::from_utf8_lossy(&header.string()?).trim_matches(['<','>']).into();}else if field==0xc8{let enc=String::from_utf8_lossy(&header.string()?).to_ascii_lowercase();if !["binary","7bit","8bit"].contains(&enc.as_str()){return Err("Encoded MMS part needs a compatible decoder".into())}}else if field<128{header.p-=1;let key=String::from_utf8_lossy(&header.string()?).to_lowercase();if key=="content-transfer-encoding"{let enc=String::from_utf8_lossy(&header.string()?).to_lowercase();if !["binary","7bit","8bit"].contains(&enc.as_str()){return Err("Encoded MMS part needs a compatible decoder".into())}}else{header.skip()?}}else{header.skip()?}}
  let bytes=r.take(n)?;if mime.contains("multipart"){multipart(&mut Reader::new(bytes),depth+1,parts)?}else{if name.is_empty(){name=format!("attachment{index}")}parts.push(Part{mime,name,charset,data:bytes.to_vec()});}
 }Ok(())
}
fn parse(bytes:&[u8])->Result<Pdu>{
 if bytes.len()>LIMIT{return Err("MMS exceeds 10 MB".into())}let mut r=Reader::new(bytes);let mut p=Pdu::default();let mut fields=0;let mut content_seen=false;
 while r.p<r.b.len(){fields+=1;if fields>128{return Err("Too many MMS headers".into())}let field=r.byte()?;match field{
  0x8c=>p.kind=r.byte()?,0x8d=>{r.byte()?;},0x98=>p.transaction=String::from_utf8_lossy(&r.string()?).into(),0x8b=>p.message_id=String::from_utf8_lossy(&r.string()?).into(),
  0x89=>{let n=r.length()?;let mut v=Reader::new(r.take(n)?);if v.byte()?==0x80{p.from=v.encoded()?.split("/TYPE=").next().unwrap_or("").into()}},
  0x97|0x82=>{if p.to.len()>=20{return Err("Too many MMS recipients".into())}p.to.push(r.encoded()?.split("/TYPE=").next().unwrap_or("").into())},
  0x85=>p.date=r.integer()?,0x96=>p.subject=r.encoded()?,0x83=>p.location=String::from_utf8_lossy(&r.string()?).into(),0x92|0x95|0x99=>p.status=r.byte()?,
  0x84=>{content_seen=true;let(mime,_,charset)=r.content()?;if mime.contains("multipart"){multipart(&mut r,0,&mut p.parts)?}else{let rest=r.take(r.b.len()-r.p)?.to_vec();p.parts.push(Part{mime,name:"message".into(),charset,data:rest})}break},
  _=>{if field<128{r.p-=1;r.string()?;}r.skip()?;}
 }}
 if ![128,129,130,131,132,133,134,135,136].contains(&p.kind){return Err("Unsupported MMS message type".into())}
 if [128,132].contains(&p.kind)&&!content_seen{return Err("MMS body is missing".into())}
 if p.kind==130&&p.location.is_empty(){return Err("Missing carrier MMS location".into())}
 Ok(p)
}
fn compose(transaction:&str,recipients:&[String],body:&str,media:&[Part],now:u64)->Result<Vec<u8>> {
 if recipients.is_empty()||recipients.len()>10||media.len()>10||body.encode_utf16().count()>10000{return Err("MMS composition limits exceeded".into())}
 let mut out=vec![0x8c,128,0x98];text(&mut out,transaction);out.extend([0x8d,0x92,0x85]);integer(&mut out,now);out.extend([0x89,1,0x81]);
 for address in recipients{let normalized=crate::messaging::phone(address)?;out.push(0x97);encoded(&mut out,&format!("{normalized}/TYPE=PLMN"));}
 out.extend([0x8a,0x80,0x86,0x81,0x90,0x81,0x84]);
 let mut content=vec![0xb3,0x8a];text(&mut content,"<smil.xml>");content.push(0x89);text(&mut content,"application/smil");length(&mut out,content.len());out.extend(content);
 let mut parts=Vec::new();let mut smil=String::from("<smil><body>");
 if !body.is_empty(){parts.push(Part{mime:"text/plain".into(),name:"text.txt".into(),charset:106,data:body.as_bytes().to_vec()});smil.push_str("<par><text src=\"text.txt\"/></par>");}
 for(index,p)in media.iter().enumerate(){let mut p=p.clone();let ext=match p.mime.as_str(){"image/jpeg"=>"jpg","image/png"=>"png","image/gif"=>"gif","video/mp4"=>"mp4","video/3gpp"=>"3gp","audio/amr"=>"amr","audio/amr-wb"=>"awb","audio/3gpp"=>"3gp","audio/mpeg"=>"mp3","audio/mp4"=>"m4a","audio/ogg"=>"ogg","text/x-vcard"=>"vcf",_=>return Err("Unsupported MMS attachment type".into())};p.name=format!("attachment{index}.{ext}");let tag=if p.mime.starts_with("image/"){"img"}else if p.mime.starts_with("audio/"){"audio"}else if p.mime.starts_with("video/"){"video"}else{"ref"};smil.push_str(&format!("<par dur=\"8000ms\"><{tag} src=\"{}\"/></par>",p.name));parts.push(p);}
 smil.push_str("</body></smil>");parts.insert(0,Part{mime:"application/smil".into(),name:"smil.xml".into(),charset:106,data:smil.into_bytes()});uint(&mut out,parts.len());
 for part in parts{let mut ct=Vec::new();text(&mut ct,&part.mime);ct.push(0x85);text(&mut ct,&part.name);if part.mime.starts_with("text/"){ct.extend([0x81,0xea]);}let mut h=Vec::new();length(&mut h,ct.len());h.extend(ct);h.extend([0xc0,34]);text(&mut h,&format!("<{}>",part.name));h.push(0x8e);text(&mut h,&part.name);uint(&mut out,h.len());uint(&mut out,part.data.len());out.extend(h);out.extend(part.data);if out.len()>LIMIT{return Err("MMS exceeds 10 MB".into())}}
 Ok(out)
}
fn folder(root:&Path)->Result<PathBuf>{let dir=root.join("mms");fs::create_dir_all(&dir).map_err(|e|e.to_string())?;Ok(dir)}
fn file(root:&Path,id:&str,ext:&str)->Result<PathBuf>{if !crate::valid_id(id){return Err("Invalid MMS identity".into())}Ok(folder(root)?.join(format!("{id}.{ext}")))}
fn read(root:&Path,id:&str,ext:&str)->Result<Vec<u8>>{let p=file(root,id,ext)?;let m=fs::symlink_metadata(&p).map_err(|e|e.to_string())?;if !m.is_file()||m.len()>LIMIT as u64{return Err("Invalid MMS file".into())}fs::read(p).map_err(|e|e.to_string())}
// Validate every text field before publishing any attachments. Roll back every
// newly owned file (including a partially written one) if disk publication fails.
fn materialize(root:&Path,p:Pdu,mut write:impl FnMut(&mut fs::File,&[u8])->Result<()>)->Result<Value>{
 let mut parts=Vec::new();let mut blobs=Vec::new();
 for part in p.parts {
  if part.mime=="application/smil"{continue}
  if part.mime=="text/plain"{parts.push(json!({"mime":part.mime,"text":decode(&part.data,part.charset)?,"name":part.name}));}
  else {let id=uuid::Uuid::new_v4().simple().to_string();let path=file(root,&id,"blob")?;
   parts.push(json!({"mime":part.mime,"name":crate::plain(&part.name,120),"file":id,"bytes":part.data.len()}));blobs.push((path,part.data));}
 }
 let mut owned=Vec::new();
 let published:Result<()>= (||{for (path,bytes) in blobs{
  let mut f=fs::OpenOptions::new().write(true).create_new(true).open(&path).map_err(|e|e.to_string())?;
  owned.push(path);write(&mut f,&bytes)?;f.sync_all().map_err(|e|e.to_string())?;
 }Ok(())})();
 if let Err(error)=published{let mut failed=false;for path in owned{if fs::remove_file(path).is_err(){failed=true}}
  return Err(if failed{format!("{error}; attachment cleanup needs attention")}else{error});}
 Ok(json!({"kind":p.kind,"transaction":p.transaction,"from":p.from,"to":p.to,"date":p.date,"subject":p.subject,"location":p.location,"status":p.status,"message_id":p.message_id,"parts":parts}))
}
pub fn dispatch(root:&Path,j:&Value)->Result<Value>{let id=j["id"].as_str().ok_or("Missing MMS identity")?;
 match j["action"].as_str().ok_or("Missing MMS operation")?{
 "compose"=>{let recipients:Vec<String>=serde_json::from_value(j["recipients"].clone()).map_err(|e|e.to_string())?;let mut parts=Vec::new();for a in j["parts"].as_array().ok_or("Missing MMS parts")?{let data=read(root,a["file"].as_str().ok_or("Missing attachment identity")?,"blob")?;parts.push(Part{mime:a["mime"].as_str().ok_or("Missing attachment type")?.into(),name:String::new(),charset:106,data});}let bytes=compose(id,&recipients,j["text"].as_str().unwrap_or(""),&parts,j["date"].as_u64().ok_or("Missing MMS date")?)?;let count=bytes.len();fs::write(file(root,id,"pdu")?,bytes).map_err(|e|e.to_string())?;Ok(json!({"file":id,"bytes":count}))},
 "parse"=>{use std::io::Write;materialize(root,parse(&read(root,id,"pdu")?)?,|f,b|f.write_all(b).map_err(|e|e.to_string()))},
 "ack"=>{let tr=j["transaction"].as_str().ok_or("Missing transaction")?;if tr.len()>200||tr.contains('\0'){return Err("Invalid MMS transaction".into())}let mut bytes=vec![0x8c,131,0x98];text(&mut bytes,tr);bytes.extend([0x8d,0x92,0x95,0x81]);fs::write(file(root,id,"pdu")?,bytes).map_err(|e|e.to_string())?;Ok(json!({"file":id}))},
 _=>Err("Unsupported MMS operation".into())
 }}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn independent_notification_fixture(){let b=[0x8c,130,0x98,b'x',0,0x8d,0x92,0x89,7,0x80,b'1',b'2',b'3',b'4',b'5',0,0x83,b'h',b't',b't',b'p',b':',b'/',b'/',b'm',b'/',b'x',0];let p=parse(&b).unwrap();assert_eq!(p.kind,130);assert_eq!(p.from,"12345");assert_eq!(p.location,"http://m/x");}
 #[test]fn compose_wire_and_multipart_unicode(){let parts=vec![Part{mime:"image/jpeg".into(),name:"ignored".into(),charset:106,data:vec![255,216,255,217]}];let wire=compose("transaction",&["+15555550123".into()],"Hello 🐈",&parts,1234).unwrap();assert_eq!(&wire[..2],&[0x8c,128]);let p=parse(&wire).unwrap();assert_eq!(p.to,vec!["+15555550123"]);assert_eq!(decode(&p.parts[1].data,p.parts[1].charset).unwrap(),"Hello 🐈");assert_eq!(p.parts[2].data,parts[0].data);assert_eq!(p.parts[0].mime,"application/smil");}
 #[test]fn every_truncation_is_bounded(){let wire=compose("t",&["5550123".into()],"text",&[],123).unwrap();for n in 0..wire.len(){let result=std::panic::catch_unwind(||parse(&wire[..n]));assert!(result.is_ok());}let mut bad=wire;bad.truncate(10);assert!(parse(&bad).is_err());}
 #[test]fn unsafe_identity_cannot_write(){let d=tempfile::tempdir().unwrap();assert!(dispatch(d.path(),&json!({"action":"ack","id":"../outside","transaction":"x"})).is_err());}
 #[test]fn decoded_parts_publish_only_after_validation_and_roll_back_partial_writes(){
  use std::io::Write;
  let d=tempfile::tempdir().unwrap();let root=d.path();
  let part=|mime:&str,charset,data:Vec<u8>|Part{mime:mime.into(),charset,name:"part".into(),data};
  let p=Pdu{parts:vec![part("image/png",106,vec![1]),part("text/plain",1000,vec![0xff])],..Default::default()};
  assert!(materialize(root,p,|_,_|panic!("Must validate before writing")).is_err());
  let foreign=file(root,&"a".repeat(32),"blob").unwrap();fs::write(&foreign,b"Keep").unwrap();
  let p=Pdu{parts:vec![part("image/png",106,vec![1]),part("image/png",106,vec![2])],..Default::default()};
  let mut count=0;assert!(materialize(root,p,|f,b|{count+=1;f.write_all(b).unwrap();if count==2{Err("Disk full".into())}else{Ok(())}}).is_err());
  assert_eq!(fs::read_dir(folder(root).unwrap()).unwrap().count(),1);assert_eq!(fs::read(foreign).unwrap(),b"Keep");
 }

}
