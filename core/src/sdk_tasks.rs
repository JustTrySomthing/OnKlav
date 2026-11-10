//! Signed background-transfer/media contracts. Android executes host jobs, never guest code.
use crate::{Result,sdk::NetworkPolicy};use serde_json::{Value,json};
fn object<'a>(v:&'a Value,keys:&[&str])->Result<&'a serde_json::Map<String,Value>>{let a=v.as_object().ok_or("Arguments must be an object")?;if a.keys().any(|k|!keys.contains(&k.as_str())){return Err("Unsupported task/media argument".into())}Ok(a)}
fn text(v:&Value,max:usize)->bool{v.as_str().is_some_and(|s|!s.trim().is_empty()&&s.encode_utf16().count()<=max&&!s.chars().any(char::is_control))}
fn id(v:&Value)->bool{v.as_str().is_some_and(|s|uuid::Uuid::parse_str(s).is_ok()&&s.len()==36)}
pub(crate) fn request(p:Option<&NetworkPolicy>,service:&str,a:&Value)->Result<()>{
 match service{
 "jobs.list"|"media.status"=>{if !object(a,&[])?.is_empty(){return Err("No arguments expected".into())}},
 "jobs.status"|"jobs.cancel"=>{object(a,&["id"])?;if !id(&a["id"]){return Err("Choose a job handle".into())}},
 "jobs.schedule"=>{object(a,&["key","request","delay_seconds","unmetered","charging"])?;crate::sdk_http::request(p,&a["request"])?;if !matches!(a["request"]["method"].as_str().unwrap_or("GET"),"GET"|"HEAD"){return Err("Background HTTP jobs only support GET/HEAD".into())}if a["delay_seconds"].as_u64().is_none_or(|n|n>2_592_000){return Err("Choose a delay up to30 days".into())}},
 "downloads.start"=>{object(a,&["key","url","name","mime","max_bytes","sha256","cache","unmetered","charging","account"])?;let mut http=json!({"url":a["url"]});if let Some(v)=a.get("account"){http["account"]=v.clone()}crate::sdk_http::request(p,&http)?;if !text(&a["name"],128)||a["name"].as_str().unwrap().contains(['/','\\'])||!text(&a["mime"],128)||a["max_bytes"].as_u64().is_none_or(|n|n==0||n>1_073_741_824){return Err("Choose bounded download name, MIME and size".into())}if a.get("sha256").is_some_and(|v|v.as_str().is_none_or(|s|s.len()!=64||!s.bytes().all(|b|b.is_ascii_hexdigit()))){return Err("Invalid download checksum".into())}},
 "media.play"=>{object(a,&["queue","index","position_ms","speed"])?;let q=a["queue"].as_array().filter(|q|!q.is_empty()&&q.len()<=64).ok_or("Queue1–64 media items")?;if a["index"].as_u64().unwrap_or(0)>=q.len()as u64{return Err("Invalid queue position".into())}for item in q{object(item,&["file","url","title","account"])?;if !text(&item["title"],120)||item.get("file").is_some()==item.get("url").is_some(){return Err("Media item needs a title and one source".into())}if item.get("file").is_some(){if item["file"].as_str().is_none_or(|s|!crate::valid_id(s))||item.get("account").is_some(){return Err("Invalid private media handle".into())}}else{let mut http=json!({"url":item["url"]});if let Some(v)=item.get("account"){http["account"]=v.clone()}crate::sdk_http::request(p,&http)?;}}},
 "media.control"=>{object(a,&["operation","position_ms","speed","minutes"])?;if !matches!(a["operation"].as_str(),Some("play"|"pause"|"stop"|"next"|"previous"|"seek"|"speed"|"sleep")){return Err("Unsupported media control".into())}if a["operation"]=="sleep"&&a["minutes"].as_u64().is_none_or(|m|m>120){return Err("Choose a sleep timer up to120 minutes".into())}if a["operation"]=="seek"&&a["position_ms"].as_u64().is_none(){return Err("Choose a seek position".into())}if a["operation"]=="speed"&&a.get("speed").is_none(){return Err("Choose playback speed".into())}},
 _=>return Err("Unsupported background/media service".into())
 }
 if matches!(service,"jobs.schedule"|"downloads.start"){
  if a["key"].as_str().is_none_or(|s|s.is_empty()||s.len()>48||!s.bytes().all(|b|b.is_ascii_alphanumeric()||b"._-".contains(&b))){return Err("Choose a bounded job key".into())}
  for k in ["unmetered","charging","cache"]{if a.get(k).is_some_and(|v|!v.is_boolean()){return Err("Task flags must be booleans".into())}}
 }
 if a.get("speed").is_some_and(|v|v.as_f64().is_none_or(|n|!(0.5..=3.0).contains(&n))){return Err("Playback speed must be0.5–3".into())}
 if a.get("position_ms").is_some_and(|v|v.as_u64().is_none_or(|n|n>2_147_483_647)){return Err("Invalid playback position".into())}Ok(())
}
pub(crate) fn reply(service:&str,v:&Value)->Result<()>{
 if v.to_string().len()>65536{return Err("Task response exceeds limit".into())}
 if service=="jobs.cancel"{object(v,&["cancelled"])?;if !v["cancelled"].is_boolean(){return Err("Invalid cancellation response".into())}return Ok(())}
 if service.starts_with("media."){object(v,&["state","position_ms","duration_ms","index","count","speed","title","error","video","aspect","sleep_remaining_ms","live"])?;if !matches!(v["state"].as_str(),Some("idle"|"preparing"|"playing"|"paused"|"ended"|"error")){return Err("Invalid playback state".into())}return Ok(())}
 let row=|r:&Value|->Result<()>{object(r,&["id","key","kind","state","bytes","total","file","error","attempts"])?;if !id(&r["id"])||!matches!(r["state"].as_str(),Some("queued"|"running"|"retry"|"completed"|"failed"|"cancelled")){return Err("Invalid task result".into())}Ok(())};
 if service=="jobs.list"{for r in v.as_array().filter(|a|a.len()<=8).ok_or("Invalid task list")?{row(r)?}}else{row(v)?}Ok(())
}
#[cfg(test)]mod tests{use super::*;#[test]fn tasks_are_bounded_and_never_accept_paths_or_mutating_background_http(){let p=crate::sdk::NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![]};let a=json!({"key":"sync","request":{"url":"https://api.example.com/feed"},"delay_seconds":0});request(Some(&p),"jobs.schedule",&a).unwrap();let mut b=a;b["request"]["method"]=json!("POST");assert!(request(Some(&p),"jobs.schedule",&b).is_err());assert!(request(Some(&p),"media.play",&json!({"queue":[{"title":"Bad","file":"/etc/passwd"}]})).is_err());assert!(request(Some(&p),"media.control",&json!({"operation":"speed","speed":20})).is_err());assert!(reply("jobs.status",&json!({"id":uuid::Uuid::new_v4().to_string(),"state":"running","path":"/private"})).is_err());}}
