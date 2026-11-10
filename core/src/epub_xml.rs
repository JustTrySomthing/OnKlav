//! Bounded XML tokenizer for the text-only EPUB profile. No DTD/entity resolver.
use crate::Result;
use std::collections::BTreeMap;
#[derive(Debug)]pub enum Event{Open(String,BTreeMap<String,String>),Text(String),Close(String)}
pub fn local(s:&str)->&str{s.rsplit(':').next().unwrap_or(s)}
pub fn attr<'a>(a:&'a BTreeMap<String,String>,k:&str)->Option<&'a str>{a.get(k).map(String::as_str)}
pub fn attr_local<'a>(a:&'a BTreeMap<String,String>,k:&str)->Option<&'a str>{a.iter().find(|(n,_)|local(n)==k).map(|(_,v)|v.as_str())}
fn valid_char(c:char)->bool{matches!(c,'\t'|'\n'|'\r')||c>=' '&&!matches!(c,'\u{fffe}'|'\u{ffff}')}
fn decode(s:&str)->Result<String>{let mut out=String::new();let mut rest=s;while let Some(i)=rest.find('&'){out.push_str(&rest[..i]);let tail=&rest[i+1..];let end=tail.find(';').filter(|n|*n<=16).ok_or("Invalid XML entity")?;let c=match &tail[..end]{"amp"=>'&',"lt"=>'<',"gt"=>'>',"quot"=>'"',"apos"=>'\'',"nbsp"=>'\u{00a0}',"copy"=>'©',"reg"=>'®',"mdash"=>'—',"ndash"=>'–',"hellip"=>'…',"lsquo"=>'‘',"rsquo"=>'’',"ldquo"=>'“',"rdquo"=>'”',"bull"=>'•',v if v.starts_with("#x")=>char::from_u32(u32::from_str_radix(&v[2..],16).map_err(|_|"Invalid XML character")?).filter(|c|valid_char(*c)).ok_or("Invalid XML character")?,v if v.starts_with('#')=>char::from_u32(v[1..].parse().map_err(|_|"Invalid XML character")?).filter(|c|valid_char(*c)).ok_or("Invalid XML character")?,_=>return Err("Unsupported named XML entity; use numeric entities".into())};out.push(c);rest=&tail[end+1..];}out.push_str(rest);Ok(out)}
fn name(s:&str)->bool{!s.is_empty()&&s.len()<=128&&s.bytes().enumerate().all(|(i,b)|b.is_ascii_alphabetic()||b==b'_'||b==b':'||i>0&&(b.is_ascii_digit()||b".-".contains(&b)))}
pub fn parse(source:&str)->Result<Vec<Event>>{
 if source.len()>1_048_576||source.chars().any(|c|!valid_char(c)){return Err("Unsupported XML size or character".into())}
 let mut s=source.trim_start_matches('\u{feff}');let mut stack=Vec::<String>::new();let mut roots=0;let mut out=Vec::new();
 while !s.is_empty(){if out.len()>100000{return Err("XML node limit reached".into())}
  if !s.starts_with('<'){let n=s.find('<').unwrap_or(s.len());let text=decode(&s[..n])?;if stack.is_empty()&&!text.trim().is_empty(){return Err("Text outside XML root".into())}out.push(Event::Text(text));s=&s[n..];continue}
  if s.starts_with("<!--"){let n=s.find("-->").ok_or("Unclosed XML comment")?;if s[4..n].contains("--"){return Err("Invalid XML comment".into())}s=&s[n+3..];continue}
  if s.starts_with("<![CDATA["){let n=s.find("]]>").ok_or("Unclosed XML CDATA")?;if stack.is_empty(){return Err("CDATA outside XML root".into())}out.push(Event::Text(s[9..n].into()));s=&s[n+3..];continue}
  if s.starts_with("<?"){let n=s.find("?>").ok_or("Unclosed XML instruction")?;if s[..n].to_ascii_lowercase().contains("utf-16"){return Err("Use UTF-8 EPUB XML".into())}s=&s[n+2..];continue}
  if s.starts_with("<!DOCTYPE "){let n=s.find('>').filter(|n|*n<=1024).ok_or("Invalid XML doctype")?;if !stack.is_empty()||roots>0||s[1..n].contains(['[','<']){return Err("Internal DTD/entity declarations are unsupported".into())}s=&s[n+1..];continue}
  if s.starts_with("<!"){return Err("DTD/entity declarations are unsupported".into())}
  let mut quote=None;let mut end=None;for(i,c)in s.char_indices().skip(1){if let Some(q)=quote{if c==q{quote=None}}else if c=='\''||c=='"'{quote=Some(c)}else if c=='>'{end=Some(i);break}}
  let n=end.ok_or("Unclosed XML tag")?;let raw=&s[1..n];s=&s[n+1..];
  if let Some(tag)=raw.strip_prefix('/'){let tag=tag.trim();if !name(tag)||stack.pop().as_deref()!=Some(tag){return Err("Mismatched XML tags".into())}out.push(Event::Close(tag.into()));continue}
  let empty=raw.ends_with('/');let raw=if empty{&raw[..raw.len()-1]}else{raw};let split=raw.find(char::is_whitespace).unwrap_or(raw.len());let tag=&raw[..split];if !name(tag){return Err("Invalid XML name".into())}
  let mut attrs=BTreeMap::new();let mut rest=&raw[split..];while !rest.trim().is_empty(){rest=rest.trim_start();let n=rest.find(|c:char|c=='='||c.is_whitespace()).ok_or("Invalid XML attribute")?;let key=&rest[..n];if !name(key){return Err("Invalid XML attribute name".into())}rest=rest[n..].trim_start().strip_prefix('=').ok_or("Missing XML attribute value")?.trim_start();let q=rest.chars().next().filter(|c|*c=='\''||*c=='"').ok_or("Quote XML attribute values")?;rest=&rest[1..];let end=rest.find(q).ok_or("Unclosed XML attribute")?;if rest[..end].contains('<'){return Err("Invalid XML attribute".into())}let value=decode(&rest[..end])?;if value.len()>8192||attrs.len()>=64||attrs.insert(key.into(),value).is_some(){return Err("Duplicate or oversized XML attribute".into())}rest=&rest[end+1..];if !rest.is_empty()&&!rest.starts_with(char::is_whitespace){return Err("Separate XML attributes with whitespace".into())}}
  if stack.is_empty(){roots+=1;if roots>1{return Err("Multiple XML roots".into())}}if stack.len()>=64{return Err("XML nesting limit reached".into())}
  out.push(Event::Open(tag.into(),attrs));if empty{out.push(Event::Close(tag.into()))}else{stack.push(tag.into())}
 }
 if roots!=1||!stack.is_empty(){return Err("Missing or unclosed XML root".into())}Ok(out)
}
