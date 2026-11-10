//! Inner Circle carrier envelope compatibility, reimplemented in Rust.
//! Unknown/ambiguous events remain visible instead of being silently discarded.
use crate::Result;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
const PREFIX:&str="/\"0\"1:";
#[derive(Clone,Debug)]
struct Envelope<'a>{kind:&'a str,id:&'a str,target:&'a str,code:&'a str,body:&'a str}
fn symbol(code:&str)->Option<String>{
 let short=match code {"L"=>Some("😂"),"S"=>Some("😊"),"T"=>Some("👍"),"F"=>Some("🤞"),"H"=>Some("❤️"),_=>None};
 if let Some(s)=short{return Some(s.into())}
 let parts:Vec<_>=code.strip_prefix('U')?.split('.').collect();if parts.is_empty()||parts.len()>25{return None}
 let mut s=String::new();
 for p in parts {if !(2..=6).contains(&p.len())||!p.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c)){return None}let cp=u32::from_str_radix(p,16).ok()?;let c=char::from_u32(cp)?;
 if !matches!(cp,0x2000..=0x3300|0x1f000..=0x1faff|0xfe0e..=0xfe0f|0xe0020..=0xe007f|0xa9|0xae|0x23|0x2a|0x30..=0x39)||c.is_whitespace()||c.is_control(){return None}s.push(c);}
 Some(s)
}
pub(crate) fn valid_reaction(emoji:&str)->bool {
 if emoji.is_empty(){return true}
 let code=format!("U{}",emoji.chars().map(|c|format!("{:x}",c as u32)).collect::<Vec<_>>().join("."));
 symbol(&code).is_some()
}
fn parse(text:&str)->Option<Envelope<'_>>{
 let rest=text.strip_prefix(PREFIX)?;let (header,body)=rest.split_once('\n')?;
 if PREFIX.len()+header.len()>300{return None}let parts:Vec<_>=header.split(':').collect();if parts.len()<2||!crate::valid_id(parts[1]){return None}
 let mut e=Envelope{kind:parts[0],id:parts[1],target:"",code:"",body};
 match e.kind {"P" if parts.len()==2=>{},"C" if parts.len()==3&&crate::valid_id(parts[2])&&!body.trim().is_empty()=>{e.target=parts[2]},"R" if parts.len()==4&&crate::valid_id(parts[2])&&body.is_empty()&&(parts[3]=="-"||symbol(parts[3]).is_some())=>{e.target=parts[2];e.code=parts[3]},_=>return None}Some(e)
}
fn key(text:&str)->Option<String>{if text.trim().is_empty(){None}else{Some(format!("{:x}",Sha256::digest(text.as_bytes()))[..32].into())}}
fn display(text:&str)->String {match parse(text){None=>text.into(),Some(e) if e.kind=="R"=>if e.code=="-"{"Removed a group reaction".into()}else{format!("{} reacted to a group post",symbol(e.code).unwrap_or_default())},Some(e)=>e.body.into()}}
pub fn dispatch(j:&Value)->Result<Value>{
 match j["action"].as_str().ok_or("Missing wire operation")? {
 "post"=>{let text=j["text"].as_str().ok_or("Missing post text")?;if text.encode_utf16().count()>9500{return Err("Group post exceeds its limit".into())}Ok(json!(format!("{PREFIX}P:{}\n{text}",uuid::Uuid::new_v4().simple())))},
 "reaction"=>{let target=j["target"].as_str().ok_or("Missing reaction target")?;let emoji=j["emoji"].as_str().ok_or("Missing reaction")?;let code=if emoji.is_empty(){"-".into()}else{format!("U{}",emoji.chars().map(|c|format!("{:x}",c as u32)).collect::<Vec<_>>().join("."))};if !crate::valid_id(target)||(code!="-"&&symbol(&code).is_none()){return Err("Invalid group reaction".into())}Ok(json!(format!("{PREFIX}R:{}:{target}:{code}\n",uuid::Uuid::new_v4().simple())))},
 "targets"=>{let rows=j["rows"].as_array().ok_or("Missing group rows")?;if rows.len()>10000{return Err("Too many group rows".into())}let mut targets=BTreeSet::new();for row in rows{if let Some(e)=row["text"].as_str().and_then(parse).filter(|e|e.kind=="R"||e.kind=="C"){if targets.len()>=64{break}targets.insert(e.target.to_owned());}}Ok(json!(targets))},
 "feed"=>{
 let rows=j["rows"].as_array().ok_or("Missing group rows")?;if rows.len()>10000{return Err("Too many group rows".into())}
 let mut rows=rows.clone();rows.sort_by_key(|r|r["date"].as_u64().unwrap_or(0));
 let mut occurrences=BTreeMap::new();let mut targets=BTreeMap::new();let mut texts=BTreeMap::new();let mut ids=BTreeSet::new();
 for row in &rows {let id=row["id"].as_str().ok_or("Missing row identity")?;if !ids.insert(id){return Err("Duplicate group row identity".into())}let text=row["text"].as_str().ok_or("Missing row text")?;if text.len()>100000{return Err("Group row exceeds limit".into())}texts.insert(id,display(text));let e=parse(text);if e.as_ref().is_some_and(|e|e.kind=="R"){continue}if let Some(k)=e.map(|e|e.id.into()).or_else(||key(text)){*occurrences.entry(k.clone()).or_insert(0)+=1;targets.insert(k,row);}}
 let mut keys=BTreeMap::new();let mut reactions:BTreeMap<String,BTreeMap<String,String>>=BTreeMap::new();
 for (k,row) in &targets {if occurrences[k]==1{keys.insert(row["id"].as_str().unwrap(),k.clone());reactions.insert(k.clone(),BTreeMap::new());}}
 let mut visible=Vec::new();let mut events=BTreeSet::new();
 for row in &rows {let id=row["id"].as_str().unwrap();let text=row["text"].as_str().unwrap();
  if let Some(e)=parse(text).filter(|e|e.kind=="R") {
   let target=targets.get(e.target);let known_actor=row["sent"]==true||row["sender"].as_str().is_some_and(|v|crate::messaging::phone(v).is_ok());let valid=known_actor&&occurrences.get(e.target)==Some(&1)&&row["settled"]==true&&row["media"]==false&&target.is_some_and(|t|t["settled"]==true);
   if valid {let actor=if row["sent"]==true {"You".into()} else {row["sender"].as_str().unwrap_or("Unknown").chars().filter(|c|!" ()-".contains(*c)).collect::<String>()};
    if events.insert((actor.clone(),e.id.to_string())){let r=reactions.get_mut(e.target).unwrap();if e.code=="-"{r.remove(&actor);}else{r.insert(actor,symbol(e.code).unwrap());}}continue;
   }
  }visible.push(id);
 }
 Ok(json!({"visible":visible,"texts":texts,"keys":keys,"reactions":reactions}))
 },_=>Err("Unsupported group wire operation".into())
 }
}
#[cfg(test)] mod tests {
 use super::*;
 fn row(id:&str,text:&str,date:u64)->Value{json!({"id":id,"text":text,"date":date,"settled":true,"media":false,"sent":false,"sender":"+1 (555) 0101"})}
 #[test] fn parent_targets_are_bounded_and_unknown_actors_stay_visible(){let target="a".repeat(32);let event=format!("{PREFIX}R:{}:{target}:H\n","b".repeat(32));let mut unknown=row("r",&event,2);unknown["sender"]=json!("");let post=format!("{PREFIX}P:{target}\nParent");let result=dispatch(&json!({"action":"targets","rows":[unknown.clone()]})).unwrap();assert_eq!(result,json!([target]));let view=dispatch(&json!({"action":"feed","rows":[row("p",&post,1),unknown]})).unwrap();assert_eq!(view["visible"],json!(["p","r"]));}
 #[test] fn strict_parse_preserves_unknown_text(){for s in ["/\"0\"1:P:bad\ntext","/\"0\"1:X:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\ntext","/\"0\"1:R:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb:U61\n"]{assert_eq!(display(s),s)}let post=dispatch(&json!({"action":"post","text":"Hello 🐈"})).unwrap();assert_eq!(display(post.as_str().unwrap()),"Hello 🐈");}
 #[test] fn reactions_are_actor_scoped_idempotent_and_removable(){let id="a".repeat(32);let p=format!("{PREFIX}P:{id}\nHello");let r=format!("{PREFIX}R:{}:{id}:H\n","b".repeat(32));let remove=format!("{PREFIX}R:{}:{id}:-\n","c".repeat(32));let j=dispatch(&json!({"action":"feed","rows":[row("p",&p,1),row("r",&r,2),row("duplicate",&r,3)]})).unwrap();assert_eq!(j["visible"],json!(["p"]));assert_eq!(j["reactions"][&id]["+15550101"],"❤️");let j=dispatch(&json!({"action":"feed","rows":[row("p",&p,1),row("r",&r,2),row("remove",&remove,4)]})).unwrap();assert_eq!(j["reactions"][&id],json!({}));}
 #[test] fn missing_ambiguous_or_media_targets_never_hide_events(){let id="a".repeat(32);let p=format!("{PREFIX}P:{id}\nHello");let r=format!("{PREFIX}R:{}:{id}:H\n","b".repeat(32));for rows in [vec![row("r",&r,2)],vec![row("p",&p,1),row("p2",&p,1),row("r",&r,2)],vec![row("p",&p,1),{let mut x=row("r",&r,2);x["media"]=json!(true);x}]]{let expected=rows.len();let j=dispatch(&json!({"action":"feed","rows":rows})).unwrap();assert_eq!(j["visible"].as_array().unwrap().len(),expected);}}
}
