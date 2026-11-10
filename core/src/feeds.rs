//! App-scoped feed data services. UI/navigation belongs to the SDK program.
use crate::Result;
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{collections::{BTreeMap,BTreeSet},path::Path};
#[derive(Clone,Default,Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
pub struct Article { pub url:String,pub title:String,pub body:String,pub source:String,pub feed:String,pub category:String,pub published:u64 }
#[derive(Clone,Default,Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
struct Feed {url:String,name:String,category:String,enabled:bool,etag:String,modified:String,checked:u64,error:String,articles:Vec<Article>}
#[derive(Clone,Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
struct Settings {interval:u64,wifi:bool,summaries:bool,unread:bool,oldest:bool}
impl Default for Settings {fn default()->Self{Self{interval:30,wifi:false,summaries:true,unread:false,oldest:false}}}
#[derive(Serialize,Deserialize)]
#[serde(default,deny_unknown_fields)]
struct State {version:u32,feeds:Vec<Feed>,saved:BTreeMap<String,Article>,read:Vec<String>,settings:Settings}
impl Default for State {fn default()->Self{Self{version:1,feeds:catalog().into_iter().map(|v|Feed{url:v["url"].as_str().unwrap().into(),name:v["name"].as_str().unwrap().into(),category:v["category"].as_str().unwrap().into(),enabled:v["enabled"]==true,..Feed::default()}).collect(),saved:BTreeMap::new(),read:Vec::new(),settings:Settings::default()}}}
pub const CATEGORIES:[&str;10]=["US News","World News","Technology","Science","Health","Business","Arts & Culture","Environment","Sports","My Feeds"];
pub fn catalog()->Vec<Value>{[
("NPR U.S. News","US News","https://feeds.npr.org/1003/rss.xml",true),
("PBS NewsHour","US News","https://www.pbs.org/newshour/feeds/rss/headlines",false),
("BBC World","World News","https://feeds.bbci.co.uk/news/world/rss.xml",true),
("DW World","World News","https://rss.dw.com/rdf/rss-en-world",false),
("Ars Technica","Technology","https://feeds.arstechnica.com/arstechnica/index",true),
("The Verge","Technology","https://www.theverge.com/rss/index.xml",false),
("Hackaday","Technology","https://hackaday.com/blog/feed/",false),
("ScienceDaily Science","Science","https://www.sciencedaily.com/rss/top/science.xml",true),
("NASA","Science","https://www.nasa.gov/feed/",false),
("NPR Health","Health","https://feeds.npr.org/1128/rss.xml",false),
("ScienceDaily Health","Health","https://www.sciencedaily.com/rss/top/health.xml",false),
("NPR Business","Business","https://feeds.npr.org/1006/rss.xml",false),
("BBC Business","Business","https://feeds.bbci.co.uk/news/business/rss.xml",false),
("NPR Arts & Life","Arts & Culture","https://feeds.npr.org/1008/rss.xml",false),
("Smithsonian","Arts & Culture","https://www.smithsonianmag.com/rss/latest_articles/",false),
("ScienceDaily Environment","Environment","https://www.sciencedaily.com/rss/top/environment.xml",false),
("Yale Environment 360","Environment","https://e360.yale.edu/feed.xml",false),
("BBC Sport","Sports","https://feeds.bbci.co.uk/sport/rss.xml",false)
].iter().map(|(name,category,url,enabled)|json!({"id":key(url),"name":name,"category":category,"url":url,"enabled":enabled})).collect()}
pub fn key(s:&str)->String{format!("{:x}",Sha256::digest(s.as_bytes()))}
pub fn https(url:&str)->bool{if url.len()>4096||!url.starts_with("https://")||url.chars().any(|c|c.is_control()||c.is_whitespace()||c=='\\'){return false}let host=url[8..].split(['/', '?', '#']).next().unwrap_or("");!host.is_empty()&&!host.contains('@')&&host.bytes().all(|b|b.is_ascii_alphanumeric()||b".-:[]".contains(&b))}
fn now()->u64{std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis() as u64}
fn category(v:&Value)->String{v.as_str().filter(|s|CATEGORIES.contains(s)).unwrap_or("My Feeds").into()}
fn load(root:&Path)->Result<State>{let p=root.join("feeds.json");if !p.exists(){return Ok(State::default())}let s:State=serde_json::from_slice(&super::sdk::read_bounded(&p,8*1024*1024)?).map_err(|_|"Saved reader data could not be opened. Export or reset it.".to_string())?;if s.version!=1||s.feeds.len()>32||s.saved.len()>200||s.read.len()>4000{return Err("Invalid saved feed data".into())}Ok(s)}
fn save(root:&Path,s:&mut State)->Result<()> {
 let mut units=s.read.iter().map(|x|x.encode_utf16().count()).sum::<usize>();while s.read.len()>4000||units>128000{units-=s.read.remove(0).encode_utf16().count();}
 let mut all:Vec<(u64,String)>=s.feeds.iter().flat_map(|f|f.articles.iter().map(|a|(a.published,a.url.clone()))).collect();all.sort_by(|a,b|b.0.cmp(&a.0));let keep:BTreeSet<String>=all.into_iter().take(600).map(|(_,id)|id).collect();for f in &mut s.feeds{f.articles.retain(|a|keep.contains(&a.url));}
 let mut b=serde_json::to_vec(s).map_err(|e|e.to_string())?;if b.len()>8*1024*1024{for f in &mut s.feeds{f.articles.clear();f.checked=0;f.etag.clear();f.modified.clear();}b=serde_json::to_vec(s).map_err(|e|e.to_string())?;}if b.len()>8*1024*1024{return Err("Reader storage limit reached. Remove some bookmarks.".into())}
 if super::sdk::private_size(root,&root.join("feeds.json"))? + b.len() as u64 > 16*1024*1024 {return Err("App private storage quota reached".into())}super::sdk::atomic(&root.join("feeds.json"),&b)
}
fn metadata(s:&State)->Value{let feeds:Vec<Value>=s.feeds.iter().map(|f|json!({"id":key(&f.url),"url":f.url,"name":f.name,"category":f.category,"enabled":f.enabled,"checked":f.checked,"error":f.error,"articles":f.articles.len()})).collect();json!({"feeds":feeds,"settings":s.settings,"bookmarks":s.saved.len(),"categories":CATEGORIES,"now":now()})}
fn article(s:&State,id:&str)->Option<Article>{s.saved.values().chain(s.feeds.iter().flat_map(|f|f.articles.iter())).find(|a|key(&a.url)==id).cloned()}
/// One-time host adoption of this rewrite's own URL-only preferences, never legacy app data.
pub fn adopt_own(root:&Path,raw:&str)->Result<Value>{
 if root.join("feeds.json").exists(){return Ok(json!({"adopted":false}))}
 if raw.len()>262144{return Err("Previous feed list is too large; it has been retained".into())}
 let urls:Vec<String>=serde_json::from_str(raw).map_err(|_|"Previous feed list could not be read; it has been retained".to_string())?;
 let mut seen=BTreeSet::new();let mut feeds=Vec::new();
 for url in urls {
  if !https(&url){return Err("Previous feed list contains an unsupported address; it has been retained".into())}
  if !seen.insert(url.clone()){continue}
  if feeds.len()>=32{return Err("Previous feed list exceeds 32 sources; it has been retained".into())}
  feeds.push(Feed{name:crate::plain(url[8..].split(['/', '?', '#']).next().unwrap_or("Feed"),80),url,category:"My Feeds".into(),enabled:true,..Feed::default()});
 }
 let count=feeds.len();let mut state=State{feeds,..State::default()};save(root,&mut state)?;
 Ok(json!({"adopted":true,"count":count}))
}
pub fn prepare(root:&Path,args:&Value)->Result<Value>{let s=load(root)?;let url=args["url"].as_str().ok_or("Missing feed address")?;if !https(url){return Err("Enter an HTTPS feed address".into())}let f=s.feeds.iter().find(|f|f.url==url);if args["add"]==true{if f.is_some(){return Err("This feed is already in your list".into())}if s.feeds.len()>=32{return Err("Up to 32 feeds. Remove one first.".into())}}else if !f.is_some_and(|f|f.enabled){return Err("This feed is removed or paused".into())}
 Ok(json!({"url":url,"add":args["add"]==true,"name":crate::plain(args["name"].as_str().unwrap_or(""),80),"category":category(&args["category"]),"wifi":s.settings.wifi,"etag":f.map(|f|f.etag.as_str()).unwrap_or(""),"modified":f.map(|f|f.modified.as_str()).unwrap_or("")}))}
pub fn commit(root:&Path,args:&Value,response:&Value)->Result<Value>{let mut s=load(root)?;let url=args["url"].as_str().ok_or("Missing feed")?;let add=args["add"]==true;let pos=match s.feeds.iter().position(|f|f.url==url){Some(p) if !add=>p,Some(_)=>return Err("This feed is already in your list".into()),None if add&&s.feeds.len()<32=>{
 let title=crate::plain(args["name"].as_str().filter(|s|!s.trim().is_empty()).or_else(||response["title"].as_str().filter(|s|!s.trim().is_empty())).unwrap_or(url[8..].split('/').next().unwrap_or(url)),80);s.feeds.push(Feed{url:url.into(),name:title,category:category(&args["category"]),enabled:true,..Feed::default()});s.feeds.len()-1},_=>return Err("Feed list changed. Try again.".into())};
 let f=&mut s.feeds[pos];if !f.enabled{return Err("Feed is paused".into())}if response["unchanged"]!=true{
 let rows=response["items"].as_array().ok_or("Unsupported feed response")?;if rows.len()>40{return Err("Feed item limit exceeded".into())}let mut seen=BTreeSet::new();let mut articles=Vec::new();for v in rows {let u=v["url"].as_str().ok_or("Missing article address")?;let title=crate::plain(v["title"].as_str().unwrap_or(""),240);if !https(u)||title.trim().is_empty()||!seen.insert(u.to_string()){continue}articles.push(Article{url:u.into(),title,body:crate::plain(v["body"].as_str().unwrap_or(""),6000),source:f.name.clone(),feed:url.into(),category:f.category.clone(),published:v["published"].as_u64().unwrap_or(0)});}f.articles=articles;}
 f.checked=now();f.error.clear();f.etag=crate::plain(response["etag"].as_str().unwrap_or(""),512).replace(['\r','\n'],"");f.modified=crate::plain(response["modified"].as_str().unwrap_or(""),512).replace(['\r','\n'],"");save(root,&mut s)?;Ok(metadata(&s))}
pub fn failure(root:&Path,args:&Value,message:&str)->Result<()> {let mut s=load(root)?;if let Some(f)=s.feeds.iter_mut().find(|f|Some(f.url.as_str())==args["url"].as_str()){f.error=crate::plain(message,240);save(root,&mut s)?}Ok(())}
pub fn service(root:&Path,op:&str,args:&Value)->Result<Value>{
 let mut s=if op=="feeds.reset"{State::default()}else{load(root)?};let id=args["id"].as_str().unwrap_or("");let mut changed=false;
 let result=match op{
 "feeds.state"=>metadata(&s),
 "feeds.catalog"=>{let mut c=catalog();for v in &mut c{v["enabled"]=json!(s.feeds.iter().any(|f|f.url==v["url"]&&f.enabled));}json!(c)},
 "feeds.query"=>{let query=args["query"].as_str().unwrap_or("").to_lowercase();let cat=args["category"].as_str().unwrap_or("");let fid=args["feed"].as_str().unwrap_or("");let saved=args["bookmarks"]==true;let mut seen=BTreeSet::new();let mut list:Vec<&Article>=if saved{s.saved.values().collect()}else{s.feeds.iter().filter(|f|f.enabled&&(fid.is_empty()||key(&f.url)==fid)&&(cat.is_empty()||f.category==cat)).flat_map(|f|f.articles.iter()).collect()};list.retain(|a|seen.insert(a.url.clone())&&(!s.settings.unread||!s.read.contains(&a.url))&&(a.title.to_lowercase().contains(&query)||a.body.to_lowercase().contains(&query)||a.source.to_lowercase().contains(&query)));list.sort_by(|a,b|if s.settings.oldest{a.published.cmp(&b.published)}else{b.published.cmp(&a.published)}.then_with(||a.url.cmp(&b.url)));let total=list.len();let offset=args["offset"].as_u64().unwrap_or(0).min(600) as usize;let rows:Vec<Value>=list.into_iter().skip(offset).take(20).map(|a|json!({"id":key(&a.url),"title":a.title,"source":a.source,"published":a.published,"preview":if s.settings.summaries{crate::plain(&a.body.replace('\n'," "),240)}else{String::new()},"read":s.read.contains(&a.url),"bookmarked":s.saved.contains_key(&a.url)})).collect();json!({"items":rows,"total":total,"offset":offset})},
 "feeds.article"=>{let a=article(&s,id).ok_or("This article is no longer cached")?;if !s.read.contains(&a.url){s.read.push(a.url.clone());changed=true;}json!({"article":a,"id":id,"bookmarked":s.saved.contains_key(&a.url)})},
 "feeds.bookmark"=>{let a=article(&s,id).ok_or("This article is no longer cached")?;if s.saved.remove(&a.url).is_none(){if s.saved.len()>=200{return Err("Up to 200 bookmarks. Remove one first.".into())}s.saved.insert(a.url.clone(),a);}changed=true;json!(true)},
 "feeds.follow"=>{let source=catalog().into_iter().find(|v|v["id"]==id).ok_or("Unknown feed")?;let enabled=args["enabled"]==true;if let Some(f)=s.feeds.iter_mut().find(|f|f.url==source["url"]){f.enabled=enabled;}else if enabled{if s.feeds.len()>=32{return Err("Up to 32 feeds".into())}s.feeds.push(Feed{url:source["url"].as_str().unwrap().into(),name:source["name"].as_str().unwrap().into(),category:source["category"].as_str().unwrap().into(),enabled:true,..Feed::default()});}changed=true;json!(true)},
 "feeds.edit"=>{let f=s.feeds.iter_mut().find(|f|key(&f.url)==id).ok_or("Feed was removed")?;let name=crate::plain(args["name"].as_str().unwrap_or(""),80);if name.trim().is_empty(){return Err("Enter a feed name".into())}f.name=name;f.category=category(&args["category"]);f.enabled=args["enabled"]==true;for a in &mut f.articles{a.source=f.name.clone();a.category=f.category.clone();}changed=true;json!(true)},
 "feeds.remove"=>{s.feeds.retain(|f|key(&f.url)!=id);changed=true;json!(true)},
 "feeds.settings"=>{let settings:Settings=serde_json::from_value(args.clone()).map_err(|e|e.to_string())?;if ![0,15,30,60].contains(&settings.interval){return Err("Invalid refresh interval".into())}s.settings=settings;changed=true;json!(true)},
 "feeds.clear_cache"=>{for f in &mut s.feeds{f.articles.clear();f.checked=0;f.etag.clear();f.modified.clear();}changed=true;json!(true)},
 "feeds.reset_read"=>{s.read.clear();changed=true;json!(true)},
 "feeds.reset"=>{changed=true;json!(true)},
 _=>return Err("Unsupported feed data operation".into())};if changed{save(root,&mut s)?}Ok(result)
}
#[cfg(test)]mod tests{
 use super::*;
 fn response()->Value{json!({"items":[{"url":"https://example.org/story","title":"New story","body":"Offline body","published":4}]})}
 #[test]fn adopt_own_sources_is_offline_atomic_idempotent_and_preserves_existing_data(){
  let d=tempfile::tempdir().unwrap();let root=d.path();
  assert!(adopt_own(root,r#"["https://example.org/feed","file:///secret"]"#).is_err());assert!(!root.join("feeds.json").exists());
  assert!(adopt_own(root,"[false]").is_err());
  let sources:Vec<_>=(0..33).map(|i|format!("https://example.org/{i}")).collect();
  assert!(adopt_own(root,&serde_json::to_string(&sources).unwrap()).is_err());assert!(!root.join("feeds.json").exists());
  assert_eq!(adopt_own(root,r#"["https://example.org/feed","https://example.org/feed"]"#).unwrap()["count"],1);
  let initial=std::fs::read(root.join("feeds.json")).unwrap();
  assert_eq!(adopt_own(root,"malformed").unwrap()["adopted"],false);assert_eq!(std::fs::read(root.join("feeds.json")).unwrap(),initial);
  let meta=service(root,"feeds.state",&json!({})).unwrap();assert_eq!(meta["feeds"].as_array().unwrap().len(),1);assert_eq!(meta["feeds"][0]["checked"],0);assert_eq!(meta["feeds"][0]["category"],"My Feeds");
  let empty=tempfile::tempdir().unwrap();adopt_own(empty.path(),"[]").unwrap();assert!(service(empty.path(),"feeds.state",&json!({})).unwrap()["feeds"].as_array().unwrap().is_empty());
 }
 #[test]fn feed_lifecycle_offline_bookmarks_filters_and_removal(){let d=tempfile::tempdir().unwrap();let root=d.path();let args=json!({"url":"https://example.org/feed","add":true,"name":"Example","category":"Technology"});prepare(root,&args).unwrap();commit(root,&args,&response()).unwrap();let page=service(root,"feeds.query",&json!({"query":"offline"})).unwrap();assert_eq!(page["total"],1);let id=page["items"][0]["id"].as_str().unwrap();service(root,"feeds.article",&json!({"id":id})).unwrap();let mut settings=metadata(&load(root).unwrap())["settings"].clone();settings["unread"]=json!(true);service(root,"feeds.settings",&settings).unwrap();assert_eq!(service(root,"feeds.query",&json!({})).unwrap()["total"],0);service(root,"feeds.bookmark",&json!({"id":id})).unwrap();service(root,"feeds.remove",&json!({"id":key("https://example.org/feed")})).unwrap();assert_eq!(service(root,"feeds.article",&json!({"id":id})).unwrap()["article"]["body"],"Offline body");service(root,"feeds.reset_read",&json!({})).unwrap();assert_eq!(service(root,"feeds.query",&json!({"bookmarks":true})).unwrap()["total"],1);assert!(commit(root,&json!({"url":"https://example.org/feed"}),&response()).is_err());}
 #[test]fn feed_identity_limits_and_isolation(){let a=tempfile::tempdir().unwrap();let b=tempfile::tempdir().unwrap();assert!(prepare(a.path(),&json!({"url":"file:///etc/passwd","add":true})).is_err());commit(a.path(),&json!({"url":"https://example.org/feed","add":true,"name":"Example"}),&response()).unwrap();assert_eq!(service(b.path(),"feeds.query",&json!({})).unwrap()["total"],0);assert!(prepare(a.path(),&json!({"url":"https://example.org/feed","add":true})).is_err());assert!(service(a.path(),"feeds.settings",&json!({"interval":5})).is_err());assert!(https("https://example.org/x"));assert!(!https("https://a@localhost/x"));}
}

#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]
struct PortableFeed{url:String,name:String,category:String,enabled:bool,articles:Vec<Article>}
#[derive(Serialize,Deserialize)]#[serde(deny_unknown_fields)]
struct Portable{feeds:Vec<PortableFeed>,bookmarks:Vec<Article>,read:Vec<String>,settings:Settings}
fn portable(s:&State)->Portable{Portable{feeds:s.feeds.iter().map(|f|PortableFeed{url:f.url.clone(),name:f.name.clone(),category:f.category.clone(),enabled:f.enabled,articles:f.articles.clone()}).collect(),bookmarks:s.saved.values().cloned().collect(),read:s.read.clone(),settings:s.settings.clone()}}
fn checked_portable(v:&Value)->Result<Portable>{
 let p:Portable=serde_json::from_value(v.clone()).map_err(|_|"Invalid RSS backup records")?;
 fn text(s:&str,n:usize)->bool{s.encode_utf16().count()<=n&&crate::plain(s,n)==s}
 fn article(a:&Article)->bool{https(&a.url)&&https(&a.feed)&&!a.title.trim().is_empty()&&text(&a.title,240)&&text(&a.body,6000)&&text(&a.source,80)&&CATEGORIES.contains(&a.category.as_str())&&a.published<=i64::MAX as u64}
 if p.feeds.len()>32||p.bookmarks.len()>200||p.read.len()>4000||!([0,15,30,60].contains(&p.settings.interval)){return Err("RSS backup exceeds its limits".into())}
 let mut feeds=BTreeSet::new();let mut articles=BTreeSet::new();for f in &p.feeds{
  if !https(&f.url)||!feeds.insert(&f.url)||f.name.trim().is_empty()||!text(&f.name,80)||!CATEGORIES.contains(&f.category.as_str())||f.articles.len()>40{return Err("Invalid RSS source in backup".into())}
  let mut urls=BTreeSet::new();for a in &f.articles{if !article(a)||a.feed!=f.url||!urls.insert(&a.url){return Err("Invalid cached RSS article".into())}articles.insert(&a.url);}
 }if articles.len()>600{return Err("RSS cache limit exceeded".into())}
 let mut saved=BTreeSet::new();for a in &p.bookmarks{if !article(a)||!saved.insert(&a.url){return Err("Invalid RSS bookmark".into())}}
 let mut read=BTreeSet::new();if p.read.iter().any(|u|!https(u)||!read.insert(u))||p.read.iter().map(|u|u.encode_utf16().count()).sum::<usize>()>128000{return Err("Invalid RSS read status".into())}
 Ok(p)
}
fn restore_state(root:&Path,v:&Value)->Result<(State,usize,usize)>{
 let p=checked_portable(v)?;let total=p.feeds.len()+p.bookmarks.len()+p.read.len();let pristine=!root.join("feeds.json").try_exists().map_err(|e|e.to_string())?;
 let mut s=if pristine{State{feeds:vec![],saved:BTreeMap::new(),read:vec![],settings:p.settings.clone(),version:1}}else{let s=load(root)?;checked_portable(&serde_json::to_value(portable(&s)).map_err(|e|e.to_string())?)?;s};
 let mut added=0;
 for f in p.feeds{if !s.feeds.iter().any(|old|old.url==f.url){s.feeds.push(Feed{url:f.url,name:f.name,category:f.category,enabled:f.enabled,articles:f.articles,..Feed::default()});added+=1}}
 for a in p.bookmarks{if !s.saved.contains_key(&a.url){s.saved.insert(a.url.clone(),a);added+=1}}
 for url in p.read{if !s.read.contains(&url){s.read.push(url);added+=1}}
 checked_portable(&serde_json::to_value(portable(&s)).map_err(|e|e.to_string())?)?;
 let bytes=serde_json::to_vec(&s).map_err(|e|e.to_string())?;
 if bytes.len()>8*1024*1024||crate::sdk::private_size(root,&root.join("feeds.json"))?+bytes.len() as u64>16*1024*1024{return Err("Restored RSS data would exceed its storage limit".into())}
 Ok((s,added,total-added))
}
pub(crate) fn backup_export(root:&Path)->Result<Value>{let p=serde_json::to_value(portable(&load(root)?)).map_err(|e|e.to_string())?;checked_portable(&p)?;Ok(p)}
pub(crate) fn backup_plan(root:&Path,v:&Value)->Result<crate::library_backup::Plan>{let (_,new,kept)=restore_state(root,v)?;Ok(crate::library_backup::Plan{new,kept,targets:BTreeMap::new()})}
pub(crate) fn backup_commit(host:&Path,root:&Path,v:&Value)->Result<()>{let (s,_,_)=restore_state(root,v)?;crate::library_backup::check(host)?;crate::sdk::atomic(&root.join("feeds.json"),&serde_json::to_vec(&s).map_err(|e|e.to_string())?)}
