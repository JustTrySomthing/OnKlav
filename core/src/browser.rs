//! Scoped browser metadata; websites never receive this store or the Wasm host API.
use crate::Result;
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::path::Path;
const HOME:&str="https://duckduckgo.com/";
#[derive(Clone,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Tab{id:String,url:String,title:String}
#[derive(Clone,Serialize,Deserialize)]
#[serde(deny_unknown_fields)]
struct Bookmark{id:String,url:String,title:String}
#[derive(Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
struct State{version:u32,tabs:Vec<Tab>,selected:usize,home:String,newtab:String,engine:String,bookmarks:Vec<Bookmark>}
fn tab(url:&str)->Tab{Tab{id:uuid::Uuid::new_v4().simple().to_string(),url:url.into(),title:String::new()}}
impl Default for State{fn default()->Self{Self{version:1,tabs:vec![tab(HOME)],selected:0,home:HOME.into(),newtab:HOME.into(),engine:"DuckDuckGo".into(),bookmarks:vec![]}}}
fn url(raw:&str)->Result<String>{if !crate::feeds::https(raw){return Err("Enter an HTTPS Web Address".into())}Ok(raw.into())}
fn encode(s:&str)->String{s.bytes().map(|b|if b.is_ascii_alphanumeric()||b"-._~".contains(&b){(b as char).to_string()}else{format!("%{b:02X}")}).collect()}
pub fn address(raw:&str,engine:&str)->Result<String>{let raw=raw.trim();if raw.is_empty()||raw.encode_utf16().count()>4096{return Err("Enter a Web Address or Search".into())}if let Some(rest)=raw.strip_prefix("http://"){let split=rest.find(['/','?','#']).unwrap_or(rest.len());let host=&rest[..split];let host=host.strip_suffix(":80").unwrap_or(host);return url(&format!("https://{host}{}",&rest[split..]))}if raw.starts_with("https://"){return url(raw)}if raw.contains("://")||raw.split_once(':').is_some_and(|(head,_)|head.chars().all(|c|c.is_ascii_alphabetic()||c=='+'||c=='-')){return Err("This Link Needs Another App".into())}if !raw.contains(char::is_whitespace)&&raw.contains('.') {return url(&format!("https://{raw}"))}let prefix=match engine{"Google"=>"https://www.google.com/search?q=","Bing"=>"https://www.bing.com/search?q=",_=>"https://duckduckgo.com/?q="};url(&format!("{prefix}{}",encode(raw))) }
fn valid(s:&State)->bool{s.version==1&&(1..=8).contains(&s.tabs.len())&&s.selected<s.tabs.len()&&s.bookmarks.len()<=200&&["DuckDuckGo","Google","Bing"].contains(&s.engine.as_str())&&crate::feeds::https(&s.home)&&crate::feeds::https(&s.newtab)&&s.tabs.iter().all(|t|crate::valid_id(&t.id)&&crate::feeds::https(&t.url)&&t.title.encode_utf16().count()<=160)&&s.bookmarks.iter().all(|b|b.id==crate::feeds::key(&b.url)&&crate::feeds::https(&b.url)&&b.title.encode_utf16().count()<=160)}
fn load(root:&Path)->Result<State>{let path=root.join("browser.json");if !path.exists(){return Ok(State::default())}let state:State=serde_json::from_slice(&crate::sdk::read_bounded(&path,3*1024*1024)?).map_err(|_|"Browser Data Could Not Be Opened")?;if !valid(&state){return Err("Invalid Browser Data".into())}Ok(state)}
fn save(root:&Path,s:&State)->Result<()> {if !valid(s){return Err("Invalid Browser State".into())}let bytes=serde_json::to_vec(s).map_err(|e|e.to_string())?;let p=root.join("browser.json");if bytes.len()>3*1024*1024||crate::sdk::private_size(root,&p)?+bytes.len() as u64>16*1024*1024{return Err("App Private Storage Limit Reached".into())}crate::sdk::atomic(&p,&bytes)}
fn meta(s:&State)->Value{json!({"tabs":s.tabs,"selected":s.selected,"home":s.home,"newtab":s.newtab,"engine":s.engine,"bookmarks":s.bookmarks.len()})}
pub fn service(root:&Path,op:&str,args:&Value)->Result<Value>{let mut s=load(root)?;let mut changed=true;let result=match op {
 "browser.state"=>meta(&s),
 "browser.navigate"=>{s.tabs[s.selected].url=address(args["address"].as_str().unwrap_or(""),&s.engine)?;s.tabs[s.selected].title.clear();meta(&s)},
 "browser.visited"=>{let id=args["id"].as_str().ok_or("Missing Tab")?;let t=s.tabs.iter_mut().find(|t|t.id==id).ok_or("Tab Is Closed")?;t.url=url(args["url"].as_str().unwrap_or(""))?;t.title=crate::plain(args["title"].as_str().unwrap_or(""),160);meta(&s)},
 "browser.new"=>{if s.tabs.len()>=8{return Err("8-Tab Limit Reached".into())}s.tabs.push(tab(&s.newtab));s.selected=s.tabs.len()-1;meta(&s)},
 "browser.select"|"browser.close"=>{let id=args["id"].as_str().ok_or("Missing Tab")?;let at=s.tabs.iter().position(|t|t.id==id).ok_or("Tab Is Closed")?;if op=="browser.select"{s.selected=at;}else{if s.tabs.len()==1{return Err("Close Browser to Close Its Last Tab".into())}s.tabs.remove(at);if at<s.selected{s.selected-=1;}s.selected=s.selected.min(s.tabs.len()-1);}meta(&s)},
 "browser.settings"=>{let engine=args["engine"].as_str().unwrap_or("");if !["DuckDuckGo","Google","Bing"].contains(&engine){return Err("Choose a Search Engine".into())}s.home=address(args["home"].as_str().unwrap_or(""),"DuckDuckGo")?;s.newtab=address(args["newtab"].as_str().unwrap_or(""),"DuckDuckGo")?;s.engine=engine.into();meta(&s)},
 "browser.bookmark"=>{let t=&s.tabs[s.selected];if s.bookmarks.iter().any(|b|b.url==t.url){return Err("Already Bookmarked".into())}if s.bookmarks.len()>=200{return Err("Bookmark Limit Reached (200)".into())}s.bookmarks.push(Bookmark{id:crate::feeds::key(&t.url),url:t.url.clone(),title:if t.title.trim().is_empty(){t.url.clone().chars().take(160).collect()}else{t.title.clone()}});meta(&s)},
 "browser.remove"=>{s.bookmarks.retain(|b|Some(b.id.as_str())!=args["id"].as_str());meta(&s)},
 "browser.bookmarks"=>{changed=false;let query=args["query"].as_str().unwrap_or("").to_lowercase();let list:Vec<_>=s.bookmarks.iter().filter(|b|b.title.to_lowercase().contains(&query)||b.url.to_lowercase().contains(&query)).collect();let offset=args["offset"].as_u64().unwrap_or(0).min(200) as usize;json!({"total":list.len(),"items":list.into_iter().skip(offset).take(20).collect::<Vec<_>>()})},
 _=>return Err("Unsupported Browser Data Operation".into())};if changed{save(root,&s)?}Ok(result)}
#[cfg(test)]mod tests{use super::*;
 #[test]fn scoped_tabs_bookmarks_settings_and_navigation(){let d=tempfile::tempdir().unwrap();let other=tempfile::tempdir().unwrap();let r=d.path();service(r,"browser.navigate",&json!({"address":"rust & kotlin"})).unwrap();let state=service(r,"browser.state",&json!({})).unwrap();assert!(state["tabs"][0]["url"].as_str().unwrap().contains("rust%20%26%20kotlin"));service(r,"browser.bookmark",&json!({})).unwrap();assert!(service(r,"browser.bookmark",&json!({})).is_err());for _ in 1..8{service(r,"browser.new",&json!({})).unwrap();}assert!(service(r,"browser.new",&json!({})).is_err());assert_eq!(service(other.path(),"browser.state",&json!({})).unwrap()["bookmarks"],0);let state=service(r,"browser.state",&json!({})).unwrap();let id=state["tabs"][0]["id"].clone();service(r,"browser.close",&json!({"id":id})).unwrap();assert!(service(r,"browser.visited",&json!({"id":id,"url":HOME})).is_err());assert_eq!(service(r,"browser.bookmarks",&json!({"query":"rust"})).unwrap()["total"],1);assert!(address("javascript:alert(1)","Google").is_err());assert!(address("https://a@evil.org/","Google").is_err());assert!(service(r,"browser.settings",&json!({"home":HOME,"newtab":HOME,"engine":"Unknown"})).is_err());}
}

#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]
struct Portable{tabs:Vec<Tab>,selected:usize,home:String,newtab:String,engine:String,bookmarks:Vec<Bookmark>}
fn portable(s:State)->Portable{Portable{tabs:s.tabs,selected:s.selected,home:s.home,newtab:s.newtab,engine:s.engine,bookmarks:s.bookmarks}}
fn checked_backup(v:&Value)->Result<State>{
 let p:Portable=serde_json::from_value(v.clone()).map_err(|_|"Invalid Browser backup")?;
 let s=State{version:1,tabs:p.tabs,selected:p.selected,home:p.home,newtab:p.newtab,engine:p.engine,bookmarks:p.bookmarks};
 let mut tabs=std::collections::BTreeSet::new();let mut marks=std::collections::BTreeSet::new();
 if !valid(&s)||s.tabs.iter().any(|t|!tabs.insert(&t.id)||t.title!=crate::plain(&t.title,160))||s.bookmarks.iter().any(|b|!marks.insert(&b.id)||b.title!=crate::plain(&b.title,160)){return Err("Invalid or oversized Browser backup".into())}Ok(s)
}
fn merged_backup(root:&Path,v:&Value)->Result<(State,usize,usize)>{
 let incoming=checked_backup(v)?;let total=incoming.tabs.len()+incoming.bookmarks.len();let mut added=0;
 let s=if !root.join("browser.json").try_exists().map_err(|e|e.to_string())?{added=total;incoming}else{
  let mut current=checked_backup(&serde_json::to_value(portable(load(root)?)).map_err(|e|e.to_string())?)?;
  for t in incoming.tabs{if !current.tabs.iter().any(|old|old.id==t.id){current.tabs.push(t);added+=1}}
  for b in incoming.bookmarks{if !current.bookmarks.iter().any(|old|old.id==b.id){current.bookmarks.push(b);added+=1}}
  current
 };
 if !valid(&s){return Err("Restored Browser data would exceed its tab or bookmark limit".into())}
 let bytes=serde_json::to_vec(&s).map_err(|e|e.to_string())?;
 if bytes.len()>3*1024*1024||crate::sdk::private_size(root,&root.join("browser.json"))?+bytes.len() as u64>16*1024*1024{return Err("Restored Browser data would exceed its storage limit".into())}
 Ok((s,added,total-added))
}
pub(crate) fn backup_export(root:&Path)->Result<Value>{let v=serde_json::to_value(portable(load(root)?)).map_err(|e|e.to_string())?;checked_backup(&v)?;Ok(v)}
pub(crate) fn backup_plan(root:&Path,v:&Value)->Result<crate::library_backup::Plan>{let(_,new,kept)=merged_backup(root,v)?;Ok(crate::library_backup::Plan{new,kept,targets:std::collections::BTreeMap::new()})}
pub(crate) fn backup_commit(host:&Path,root:&Path,v:&Value)->Result<()>{let(s,_,_)=merged_backup(root,v)?;crate::library_backup::check(host)?;save(root,&s)}
