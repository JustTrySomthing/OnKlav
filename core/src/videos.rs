//! App-scoped video library metadata. Websites receive no access to this file.
use crate::Result;
use serde::{Serialize,Deserialize};
use serde_json::{json,Value};
use std::path::Path;
pub const HOME:&str="https://www.youtube.com/";
pub fn host(url:&str)->Option<&str>{if url.len()>2048||!crate::feeds::https(url){return None}let a=url[8..].split(['/', '?', '#']).next()?;let h=a.strip_suffix(":443").unwrap_or(a);if h.contains(':'){return None}Some(h)}
pub fn page(url:&str)->bool{host(url).is_some_and(|h|["youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","consent.youtube.com","youtu.be"].contains(&h))}
pub fn account(url:&str)->bool {let Some(h)=host(url) else{return false};let rest=&url[8..];let at=rest.find(['/','?','#']).unwrap_or(rest.len());let p=if rest.as_bytes().get(at)==Some(&b'/'){&rest[at..]}else{"/"}.split(['?','#']).next().unwrap_or("/");h=="accounts.google.com"||((h=="youtube.com"||h.ends_with(".youtube.com"))&&(p=="/signin"||p.starts_with("/signin/")||p=="/account"||p.starts_with("/account_")||p=="/ServiceLogin"))}
fn decoded(s:&str)->String{let mut b=Vec::new();let raw=s.as_bytes();let mut i=0;while i<raw.len(){if raw[i]==b'%'&&i+2<raw.len(){if let (Some(a),Some(c))=((raw[i+1] as char).to_digit(16),(raw[i+2] as char).to_digit(16)){b.push((a*16+c) as u8);i+=3;continue}}b.push(if raw[i]==b'+'{b' '}else{raw[i]});i+=1;}String::from_utf8(b).unwrap_or_default()}
pub fn video_id(url:&str)->String{if !page(url)||account(url){return String::new()}let h=host(url).unwrap();let rest=&url[8..];let at=rest.find(['/','?','#']).unwrap_or(rest.len());let path=if rest.as_bytes().get(at)==Some(&b'/'){&rest[at..]}else{"/"};let clean_path=path.split(['?','#']).next().unwrap_or("/");let id=if h=="youtu.be"{clean_path.trim_start_matches('/').split('/').next().unwrap_or("").to_string()}else if clean_path=="/watch"{path.split_once('?').map(|(_,q)|q.split('#').next().unwrap_or("").split('&').find_map(|p|{let (k,v)=p.split_once('=')?;(decoded(k)=="v").then(||decoded(v))}).unwrap_or_default()).unwrap_or_default()}else{let parts:Vec<_>=clean_path.split('/').collect();if parts.len()>=3&&["shorts","live","embed"].contains(&parts[1]){parts[2].into()}else{String::new()}};if id.len()==11&&id.bytes().all(|b|b.is_ascii_alphanumeric()||b"_-".contains(&b)){id}else{String::new()}}
/// Canonical channel homepage; video links and off-site URLs never qualify.
pub fn channel(raw:&str)->String{
 if !page(raw)||account(raw){return String::new()}
 let h=host(raw).unwrap();if !["youtube.com","www.youtube.com","m.youtube.com","music.youtube.com"].contains(&h){return String::new()}
 let rest=&raw[8..];let path=rest.find('/').map(|i|&rest[i..]).unwrap_or("/").split(['?','#']).next().unwrap_or("/");
 let parts:Vec<_>=path.trim_matches('/').split('/').collect();
 let take=if parts.first().is_some_and(|p|p.starts_with('@')&&p.len()>1){1}else if parts.len()>=2&&["channel","c","user"].contains(&parts[0])&&!parts[1].is_empty(){2}else{return String::new()};
 if parts[..take].iter().any(|p|*p=="."||*p==".."||p.len()>200||p.contains('%')||!p.chars().all(|c|c.is_alphanumeric()||"@_.-".contains(c))){return String::new()}
 format!("https://www.youtube.com/{}",parts[..take].join("/"))
}
pub fn clean_title(raw:&str)->String {let text:String=raw.chars().take(1024).map(|c|if c.is_control()||matches!(c,'\u{200b}'..='\u{200f}'|'\u{202a}'..='\u{202e}'|'\u{2060}'..='\u{206f}'|'\u{feff}'){ ' ' }else{c}).collect();let text=text.split_whitespace().collect::<Vec<_>>().join(" ");let lower=text.to_lowercase();let end=if lower.ends_with("youtube"){let at=text.len()-7;let before=text[..at].trim_end();if before.ends_with(['-','–','—']){before[..before.char_indices().last().unwrap().0].trim_end()}else if at==0{""}else{&text}}else{&text};crate::plain(end,160)}
#[derive(Clone,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Item {url:String,video:String,title:String,time:u64}
#[derive(Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
struct Library{version:u32,viewer:String,bookmarks:Vec<Item>,history:Vec<Item>}
impl Default for Library {fn default()->Self{Self{version:1,viewer:uuid::Uuid::new_v4().simple().to_string(),bookmarks:vec![],history:vec![]}}}
fn now()->u64{std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64}
fn item(url:&str,title:&str,time:u64)->Result<Item>{if !page(url)||account(url){return Err("Use a Signed-Out YouTube Page".into())}let video=video_id(url);Ok(Item{url:if video.is_empty(){url.into()}else{format!("https://www.youtube.com/watch?v={video}")},video,title:clean_title(title),time})}
fn valid(s:&Library)->bool{s.version==1&&crate::valid_id(&s.viewer)&&s.history.len()<=1000&&s.bookmarks.len()<=200&&s.history.iter().chain(&s.bookmarks).all(|i|page(&i.url)&&!account(&i.url)&&i.video==video_id(&i.url)&&i.title.encode_utf16().count()<=160)&&s.history.iter().all(|i|!i.video.is_empty())}
fn load(root:&Path)->Result<Library>{let p=root.join("videos.json");if !p.exists(){return Ok(Library::default())}let s:Library=serde_json::from_slice(&crate::sdk::read_bounded(&p,3*1024*1024)?).map_err(|_|"Video Library Could Not Be Opened")?;if !valid(&s){return Err("Invalid Video Library".into())}Ok(s)}
fn save(root:&Path,s:&Library)->Result<()> {if !valid(s){return Err("Invalid Video Library".into())}let b=serde_json::to_vec(s).map_err(|e|e.to_string())?;let p=root.join("videos.json");if b.len()>3*1024*1024||crate::sdk::private_size(root,&p)?+b.len() as u64>16*1024*1024{return Err("App Private Storage Limit Reached".into())}crate::sdk::atomic(&p,&b)}
fn meta(s:&Library)->Value{json!({"viewer":s.viewer,"bookmarks":s.bookmarks.len(),"history":s.history.len(),"now":now()})}
pub fn service(root:&Path,op:&str,args:&Value)->Result<Value>{let mut s=load(root)?;let mut changed=true;let value=match op {
 "videos.state"=>meta(&s),
 "videos.visit"=>{let next=item(args["url"].as_str().unwrap_or(""),args["title"].as_str().unwrap_or(""),now())?;if !next.video.is_empty(){let mut next=next;if next.title.is_empty(){if let Some(old)=s.history.iter().find(|i|i.video==next.video){next.title=old.title.clone();}}if !next.title.is_empty(){for b in &mut s.bookmarks{if b.video==next.video{b.title=next.title.clone();}}}if args["record"]!=false{let unchanged=s.history.first().is_some_and(|i|i.video==next.video&&i.title==next.title&&next.time.saturating_sub(i.time)<60000);if !unchanged{s.history.retain(|i|i.video!=next.video);s.history.insert(0,next);s.history.truncate(1000);}}}meta(&s)},
 "videos.bookmark"=>{let next=item(args["url"].as_str().unwrap_or(""),args["title"].as_str().unwrap_or(""),now())?;if s.bookmarks.iter().any(|i|i.url==next.url){return Err("Already Bookmarked".into())}if s.bookmarks.len()>=200{return Err("Bookmark Limit Reached (200)".into())}s.bookmarks.insert(0,next);meta(&s)},
 "videos.remove"=>{let history=args["history"]==true;let id=args["id"].as_str().unwrap_or("");let list=if history{&mut s.history}else{&mut s.bookmarks};list.retain(|i|crate::feeds::key(&i.url)!=id);meta(&s)},
 "videos.clear_history"|"videos.reset"=>{s.history.clear();meta(&s)},
 "videos.query"=>{changed=false;let history=args["history"]==true;let source=if history{&s.history}else{&s.bookmarks};let query=args["query"].as_str().unwrap_or("").to_lowercase();let list:Vec<_>=source.iter().filter(|i|args["channels"]!=true||!channel(&i.url).is_empty()).filter(|i|args["videos"]!=true||!i.video.is_empty()).filter(|i|i.title.to_lowercase().contains(&query)||i.url.to_lowercase().contains(&query)).collect();let total=list.len();let offset=args["offset"].as_u64().unwrap_or(0).min(1000) as usize;let rows:Vec<_>=list.into_iter().skip(offset).take(20).map(|i|json!({"id":crate::feeds::key(&i.url),"url":i.url,"video":i.video,"title":if i.title.is_empty(){if i.video.is_empty(){"YouTube Page"}else{"YouTube Video"}}else{&i.title},"time":i.time})).collect();json!({"items":rows,"total":total,"offset":offset})},
 _=>return Err("Unsupported Video Library Operation".into())};if changed{save(root,&s)?}Ok(value)}
#[cfg(test)]mod tests{use super::*;
 #[test]fn channel_homepages_are_canonical_and_channels_filter_preserves_video_bookmarks(){
 for (raw,want) in [("https://m.youtube.com/@Example/videos?view=0","https://www.youtube.com/@Example"),("https://www.youtube.com/channel/UC123/featured","https://www.youtube.com/channel/UC123"),("https://youtube.com/user/Example","https://www.youtube.com/user/Example")]{assert_eq!(channel(raw),want);}
 for raw in ["https://youtube.com.evil.org/@Example","https://youtube.com/watch?v=AbC_def-123","https://youtube.com/signin","https://youtu.be/@Example","https://youtube.com/channel/../watch","https://youtube.com/%40Example"]{assert!(channel(raw).is_empty(),"{raw}");}
 let d=tempfile::tempdir().unwrap();let r=d.path();
 service(r,"videos.bookmark",&json!({"url":"https://www.youtube.com/@Example","title":"Example"})).unwrap();
 service(r,"videos.bookmark",&json!({"url":"https://youtu.be/AbC_def-123","title":"Video"})).unwrap();
 assert_eq!(service(r,"videos.query",&json!({"channels":true})).unwrap()["total"],1);
 assert_eq!(service(r,"videos.query",&json!({})).unwrap()["total"],2);
 let backup=backup_export(r).unwrap();assert_eq!(backup["bookmarks"].as_array().unwrap().len(),2);
 let channels=service(r,"videos.query",&json!({"channels":true})).unwrap();service(r,"videos.remove",&json!({"id":channels["items"][0]["id"]})).unwrap();
 assert_eq!(service(r,"videos.query",&json!({"channels":true})).unwrap()["total"],0);assert_eq!(service(r,"videos.query",&json!({})).unwrap()["total"],1);
 }
 #[test]fn video_only_bookmarks_filter_before_pagination_without_deleting_channels(){
 let d=tempfile::tempdir().unwrap();for i in 0..25{service(d.path(),"videos.bookmark",&json!({"url":format!("https://www.youtube.com/@Channel{i}"),"title":"Channel"})).unwrap();}
 service(d.path(),"videos.bookmark",&json!({"url":"https://youtu.be/AbC_def-123","title":"Video"})).unwrap();
 let videos=service(d.path(),"videos.query",&json!({"videos":true})).unwrap();assert_eq!(videos["total"],1);assert_eq!(videos["items"][0]["video"],"AbC_def-123");assert_eq!(service(d.path(),"videos.query",&json!({"channels":true})).unwrap()["total"],25);
 }
 #[test]fn links_titles_and_account_boundaries(){for u in ["https://youtu.be/AbC_def-123","https://m.youtube.com/watch?v=AbC_def-123&feature=x","https://www.youtube.com/shorts/AbC_def-123","https://music.youtube.com/live/AbC_def-123","https://www.youtube.com/embed/AbC_def-123"]{assert_eq!(video_id(u),"AbC_def-123");}for u in ["http://youtu.be/AbC_def-123","https://youtube.com.evil.org/watch?v=AbC_def-123","https://x@youtube.com/watch?v=AbC_def-123","https://youtube.com:8443/watch?v=AbC_def-123","https://youtube.com/watch?v=short"]{assert!(video_id(u).is_empty());}assert!(account("https://accounts.google.com/signin"));assert!(account("https://m.youtube.com/account_settings"));assert!(!account("https://www.youtube.com/watch?v=AbC_def-123"));assert_eq!(clean_title("  A\n title — YouTube "),"A title");assert_eq!(clean_title("YouTube"),"");assert_eq!(clean_title(&"🐈".repeat(200)).encode_utf16().count(),160);}
 #[test]fn library_is_durable_scoped_normalized_and_preserves_bookmarks(){let d=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();let r=d.path();service(r,"videos.visit",&json!({"url":"https://youtu.be/AbC_def-123","title":"First - YouTube"})).unwrap();service(r,"videos.bookmark",&json!({"url":"https://www.youtube.com/shorts/AbC_def-123","title":"First"})).unwrap();assert!(service(r,"videos.bookmark",&json!({"url":"https://youtu.be/AbC_def-123"})).is_err());service(r,"videos.visit",&json!({"url":"https://www.youtube.com/watch?v=AbC_def-123","title":"Correct title - YouTube"})).unwrap();assert_eq!(service(r,"videos.query",&json!({"history":true})).unwrap()["total"],1);assert_eq!(service(r,"videos.query",&json!({})).unwrap()["items"][0]["title"],"Correct title");service(r,"videos.reset",&json!({})).unwrap();assert_eq!(service(r,"videos.state",&json!({})).unwrap()["history"],0);assert_eq!(service(r,"videos.state",&json!({})).unwrap()["bookmarks"],1);assert_eq!(service(b.path(),"videos.state",&json!({})).unwrap()["bookmarks"],0);assert!(service(r,"videos.visit",&json!({"url":"https://www.youtube.com/signin"})).is_err());}
}

#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]struct Portable{bookmarks:Vec<Item>,history:Vec<Item>}
fn checked_backup(v:&Value)->Result<Portable>{
 let p:Portable=serde_json::from_value(v.clone()).map_err(|_|"Invalid YouTube backup")?;
 if p.bookmarks.len()>200||p.history.len()>1000{return Err("YouTube backup exceeds its limits".into())}
 for (history,items) in [(false,&p.bookmarks),(true,&p.history)]{let mut seen=std::collections::BTreeSet::new();for i in items{
  let normalized=item(&i.url,&i.title,i.time)?;
  if normalized.url!=i.url||normalized.video!=i.video||i.title!=crate::plain(&i.title,160)||i.time>i64::MAX as u64||(history&&i.video.is_empty())||!seen.insert(&i.url){return Err("Invalid or duplicate YouTube backup entry".into())}
 }}Ok(p)
}
fn merged_backup(root:&Path,v:&Value)->Result<(Library,usize,usize)>{
 let p=checked_backup(v)?;let total=p.bookmarks.len()+p.history.len();let mut current=load(root)?;
 checked_backup(&json!({"bookmarks":current.bookmarks,"history":current.history}))?;
 let mut added=0;for i in p.bookmarks{if !current.bookmarks.iter().any(|old|old.url==i.url){current.bookmarks.push(i);added+=1}}
 for i in p.history{if !current.history.iter().any(|old|old.video==i.video){current.history.push(i);added+=1}}
 current.history.sort_by(|a,b|b.time.cmp(&a.time).then(a.url.cmp(&b.url)));
 let bytes=serde_json::to_vec(&current).map_err(|e|e.to_string())?;
 if !valid(&current)||bytes.len()>3*1024*1024||crate::sdk::private_size(root,&root.join("videos.json"))?+bytes.len() as u64>16*1024*1024{return Err("Restored YouTube data would exceed its storage limit".into())}
 Ok((current,added,total-added))
}
pub(crate) fn backup_export(root:&Path)->Result<Value>{let s=load(root)?;let v=json!({"bookmarks":s.bookmarks,"history":s.history});checked_backup(&v)?;Ok(v)}
pub(crate) fn backup_plan(root:&Path,v:&Value)->Result<crate::library_backup::Plan>{let(_,new,kept)=merged_backup(root,v)?;Ok(crate::library_backup::Plan{new,kept,targets:std::collections::BTreeMap::new()})}
pub(crate) fn backup_commit(host:&Path,root:&Path,v:&Value)->Result<()>{let(s,_,_)=merged_backup(root,v)?;crate::library_backup::check(host)?;save(root,&s)}
