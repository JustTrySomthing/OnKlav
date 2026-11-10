//! Bounded radio playlist parsing only. Playback and downloads remain host services.
//! These helpers never fetch links; signed origins and public DNS must still be
//! enforced by the broker when a host streaming service becomes available.
use crate::serde_json::{json,Value};
use std::collections::BTreeMap;

fn https(value:&str)->bool{
 if value.len()>4096||value.chars().any(|c|c.is_whitespace()||c.is_control()||c=='\\')||value.contains('#'){return false}
 let Some(rest)=value.strip_prefix("https://")else{return false};
 let authority=rest.split(['/', '?']).next().unwrap_or("");
 let host=authority.strip_suffix(":443").unwrap_or(authority);
 let normalized=host.to_ascii_lowercase();
 host.len()<=253&&!normalized.ends_with(".local")&&!normalized.ends_with(".localhost")&&host.contains('.')&&host.bytes().any(|c|c.is_ascii_alphabetic())&&host.split('.').all(|part|!part.is_empty()&&part.len()<=63&&!part.starts_with('-')&&!part.ends_with('-')&&part.bytes().all(|c|c.is_ascii_alphanumeric()||c==b'-'))
}
fn row(url:&str,title:&str)->Result<Value,String>{
 if !https(url){return Err("Playlist entries must use public DNS HTTPS URLs".into())}
 let title=title.trim();if title.chars().any(char::is_control)||title.chars().count()>240{return Err("Invalid playlist title".into())}
 Ok(json!({"url":url,"title":title}))
}
/// UTF-8 M3U or PLS, at most 64 KiB and 40 unique streams. HLS is deliberately
/// rejected: media segment playlists must go through a qualified media engine.
pub fn parse(text:&str)->Result<Vec<Value>,String>{
 if text.len()>65536{return Err("Playlist exceeds 64 KiB".into())}
 let text=text.strip_prefix('\u{feff}').unwrap_or(text);
 if text.chars().any(|c|c.is_control()&&!matches!(c,'\r'|'\n'|'\t')){return Err("Invalid playlist text".into())}
 let mut entries=Vec::new();
 if text.lines().find(|l|!l.trim().is_empty()).is_some_and(|l|l.trim().eq_ignore_ascii_case("[playlist]")){
  let mut rows:BTreeMap<u16,(String,String)>=BTreeMap::new();
  for line in text.lines().skip_while(|l|l.trim().is_empty()).skip(1){
   let line=line.trim();if line.is_empty()||line.starts_with(';'){continue}
   let (key,value)=line.split_once('=').ok_or("Invalid PLS entry")?;let key=key.trim().to_ascii_lowercase();
   if key=="version"||key=="numberofentries"{continue}
   let (kind,index)=if let Some(i)=key.strip_prefix("file"){("file",i)}else if let Some(i)=key.strip_prefix("title"){("title",i)}else if key.starts_with("length"){continue}else{return Err("Unsupported PLS field".into())};
   let index:u16=index.parse().map_err(|_|"Invalid PLS index")?;if index==0||rows.len()>=40&&!rows.contains_key(&index){return Err("Playlist exceeds 40 streams".into())}
   let item=rows.entry(index).or_default();let dest=if kind=="file"{&mut item.0}else{&mut item.1};if !dest.is_empty(){return Err("Duplicate PLS field".into())}*dest=value.trim().into();
  }
  for (url,title) in rows.values(){entries.push(row(url,title)?)}
 }else{
  let mut title="";
  for line in text.lines(){let line=line.trim();if line.is_empty(){continue}
   if line.starts_with("#EXT-X-"){return Err("HLS needs a host media engine".into())}
   if line.starts_with("#EXTINF:"){title=line.split_once(',').map(|(_,v)|v).ok_or("Invalid M3U title")?;continue}
   if line.starts_with('#'){continue}
   if entries.len()>=40{return Err("Playlist exceeds 40 streams".into())}entries.push(row(line,title)?);title="";
  }
 }
 let mut seen=std::collections::BTreeSet::new();entries.retain(|r|seen.insert(r["url"].as_str().unwrap_or("").to_owned()));
 if entries.is_empty(){return Err("Playlist contains no streams".into())}Ok(entries)
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn m3u_and_pls_metadata_are_bounded_and_ordered(){let m=parse("\u{feff}#EXTM3U\n#EXTINF:-1,Radio One\nhttps://radio.example.com/live\nhttps://radio.example.com/live").unwrap();assert_eq!(m.len(),1);assert_eq!(m[0]["title"],"Radio One");let p=parse("[playlist]\nFile2=https://radio.example.com/two\nTitle2=Two\nFile1=https://radio.example.com/one\nNumberOfEntries=2\nVersion=2").unwrap();assert_eq!(p[0]["url"],"https://radio.example.com/one");assert_eq!(p[1]["title"],"Two");}
 #[test]fn hostile_and_unsupported_sources_fail_closed(){for t in ["http://radio.example.com/live","https://u:p@radio.example.com/","https://127.0.0.1/live","file:///tmp/audio","#EXTM3U\n#EXT-X-TARGETDURATION:10\nhttps://radio.example.com/1.ts","[playlist]\nFile1=https://radio.example.com/one\nFile1=https://radio.example.com/two","[playlist]\nTitle1=Missing URL", "https://radio.example.com:444/live"]{assert!(parse(t).is_err(),"{t}");}assert!(parse(&"x".repeat(65537)).is_err());let many=(0..41).map(|n|format!("https://radio.example.com/{n}\n")).collect::<String>();assert!(parse(&many).is_err());}
}
