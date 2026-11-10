//! Dedicated signed-out viewer. Navigation, library and interaction logic live in this package.
use crate::{effect,frame,node,serde_json::{json,Value}};
const HOME:&str="https://www.youtube.com/";
fn button(id:&str,text:&str)->Value{node("button",id,text)}
fn cmd(s:&mut Value,op:&str){s["seq"]=json!(s["seq"].as_u64().unwrap_or(0)+1);s["command"]=json!(op);}
fn epoch(s:&mut Value){s["epoch"]=json!(s["epoch"].as_u64().unwrap_or(0)+1);}
fn query(s:&Value)->Value{effect("videos.query",json!({"history":s["drawer"]=="history","channels":s["drawer"]=="channels","videos":s["drawer"]=="bookmarks","offset":s["page"].as_u64().unwrap_or(0)*20}),"listed")}
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"meta":null,"url":HOME,"title":"","video":"","suppressed":"","seq":0,"epoch":0,"command":"load","drawer":"","menu":"","confirm":null,"page":0,"listing":{"items":[],"total":0},"status":"","back":false,"exit":false,"cache_epoch":0});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let reply=&e["reply"]["value"];let mut request=Value::Null;
 match a {
 "start"|"retry.open"=>request=effect("videos.state",json!({}),"opened"),
 "opened" if ok=>{s["meta"]=reply.clone();cmd(&mut s,"load");},
 "host.video.open"=>{s["url"]=e["url"].clone();s["channel"]=json!("");s["channel_title"]=json!("");s["video"]=e["video"].clone();s["title"]=json!("");s["suppressed"]=json!("");s["drawer"]=json!("");s["menu"]=json!("");s["confirm"]=Value::Null;s["back"]=json!(false);epoch(&mut s);cmd(&mut s,"load");},
 "home"=>{s["drawer"]=json!("");s["menu"]=json!("");s["confirm"]=Value::Null;s["channel"]=json!("");s["channel_title"]=json!("");s["url"]=json!(HOME);s["video"]=json!("");s["title"]=json!("");s["suppressed"]=json!("");s["status"]=json!("");epoch(&mut s);cmd(&mut s,"load");},
 "web.changed"=>{let value=&e["value"];let video=&value["video"];if s["video"]!=*video||(video==""&&s["url"]!=value["url"])||value["channel"].as_str().is_some_and(|c|!c.is_empty()){s["channel"]=value["channel"].clone();s["channel_title"]=value["channel_title"].clone();}if s["video"]!=*video{s["suppressed"]=json!("");}s["url"]=value["url"].clone();s["title"]=value["title"].clone();s["video"]=video.clone();s["back"]=value["back"].clone();s["status"]=json!("");request=effect("videos.visit",json!({"url":s["url"],"title":s["title"],"record":s["video"]!=s["suppressed"]}),"visited");},
 "visited" if ok=>{s["meta"]=reply.clone();if s["drawer"]!=""{request=query(&s);}},
 "web.notice"=>{s["menu"]=json!(match e["value"]["kind"].as_str(){Some("account")=>"account",Some("external")=>"external",_=>"problem"});s["external"]=e["value"]["url"].clone();},
 "history"|"bookmarks"|"channels"=>{if s["drawer"]==a{s["drawer"]=json!("");}else{s["drawer"]=json!(a);s["page"]=json!(0);s["status"]=json!("");request=query(&s);}},
 "listed" if ok=>s["listing"]=reply.clone(),
 "drawer.close"=>s["drawer"]=json!(""),
 "list.more"=>{s["page"]=json!(s["page"].as_u64().unwrap_or(0)+1);request=query(&s);},"list.previous"=>{s["page"]=json!(s["page"].as_u64().unwrap_or(0).saturating_sub(1));request=query(&s);},
 "channel.add"=>{if s["channel"].as_str().is_some_and(|c|!c.is_empty()){request=effect("videos.bookmark",json!({"url":s["channel"],"title":s["channel_title"]}),"marked");}else{s["status"]=json!("Open a video or channel, then try again.");}},
 "bookmark.add"=>request=effect("videos.bookmark",json!({"url":s["url"],"title":s["title"]}),"marked"),
 "marked" if ok=>{s["meta"]=reply.clone();s["status"]=json!("Bookmarked");request=query(&s);},
 "history.clear"=>s["confirm"]=json!({"op":"clear","title":"Clear All History?","text":"Remove this viewer’s video history? Bookmarks are kept.","accept":"Clear History"}),
 "viewer.reset"=>s["confirm"]=json!({"op":"reset","title":"Reset YouTube Viewer?","text":"Clear this viewer’s website data and video history? Your bookmarks and regular browser are kept.","accept":"Reset"}),
 "confirm.cancel"=>s["confirm"]=Value::Null,
 "confirm.accept"=>{let op=s["confirm"]["op"].as_str().unwrap_or("").to_string();if op=="clear"||op=="reset"{s["suppressed"]=s["video"].clone();epoch(&mut s);s["resetting"]=json!(op=="reset");request=effect(if op=="reset"{"videos.reset"}else{"videos.clear_history"},json!({}),"cleared");}else{if s["drawer"]=="history"&&s["confirm"]["video"]==s["video"]{s["suppressed"]=s["video"].clone();}epoch(&mut s);request=effect("videos.remove",json!({"history":s["drawer"]=="history","id":s["confirm"]["id"]}),"removed");}s["confirm"]=Value::Null;},
 "cleared" if ok=>{s["meta"]=reply.clone();s["cache_epoch"]=json!(s["cache_epoch"].as_u64().unwrap_or(0)+1);if s["resetting"]==true{s["url"]=json!(HOME);s["title"]=json!("");s["video"]=json!("");s["drawer"]=json!("");cmd(&mut s,"reset");}request=effect("images.clear",json!({}),"cache.cleared");},
 "cache.cleared"=>{if s["drawer"]!=""{request=query(&s);}s["status"]=json!(if ok{"History Cleared"}else{"History Cleared; Thumbnail Cache Could Not Be Cleared"});},
 "removed" if ok=>{s["meta"]=reply.clone();request=query(&s);},
 "menu.close"=>s["menu"]=json!(""),"problem.retry"=>{s["menu"]=json!("");cmd(&mut s,"reload");},"viewer.close"=>s["exit"]=json!(true),
 "youtube.external"=>request=effect("links.external",json!({"url":s["url"],"target":"youtube"}),"external.done"),
 "link.external"=>request=effect("links.external",json!({"url":s["external"]}),"external.done"),
 "external.done" if ok=>s["menu"]=json!(""),"page.back"=>{epoch(&mut s);cmd(&mut s,"back");},
 _ if a.starts_with("open:")=>{if let Some(item)=s["listing"]["items"].as_array().into_iter().flatten().find(|item|item["id"]==&a[5..]).cloned(){s["channel"]=json!("");s["channel_title"]=json!("");s["url"]=item["url"].clone();s["title"]=item["title"].clone();s["suppressed"]=json!("");s["drawer"]=json!("");epoch(&mut s);cmd(&mut s,"load");}},
 _ if a.starts_with("remove:")=>{if let Some(item)=s["listing"]["items"].as_array().into_iter().flatten().find(|item|item["id"]==&a[7..]){s["confirm"]=json!({"op":"remove","id":item["id"],"video":item["video"],"title":if s["drawer"]=="history"{"Remove from History?"}else{"Remove Bookmark?"},"text":item["title"],"accept":"Remove"});}},
 _ if a.starts_with("copy:")=>{if let Some(item)=s["listing"]["items"].as_array().into_iter().flatten().find(|item|item["id"]==&a[5..]){request=effect("clipboard.write",json!({"text":item["url"]}),"copied");}},"copied" if ok=>s["status"]=json!("Link Copied"),_=>{}
 }
 if e["reply"]["ok"]==false&&a!="cache.cleared"{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could Not Complete Action"));}
 let actions=vec![json!({"kind":"button","id":"home","text":"YouTube Home","icon":"home","long_action":"viewer.reset","long_label":"Reset YouTube Viewer"}),json!({"kind":"button","id":"history","text":"History","icon":"history"}),json!({"kind":"button","id":"channels","text":"Channels","icon":"channels"}),json!({"kind":"button","id":"bookmarks","text":"Bookmarks","icon":"bookmark"})];
 let body=if s["meta"].is_object(){vec![json!({"kind":"web","id":"youtube.page","url":s["url"],"tab":s["meta"]["viewer"],"on_change":"web.changed","on_notice":"web.notice","seq":s["seq"],"epoch":s["epoch"],"command":s["command"],"javascript":true,"provider":"youtube","metadata":true,"progress_inline":false})]}else{vec![button("retry.open","Retry Open")]};
 let history=s["drawer"]=="history";let channels=s["drawer"]=="channels";let mut drawer=vec![button(if history{"history.clear"}else if channels{"channel.add"}else{"bookmark.add"},if history{"Clear All History"}else if channels{"Add Current Channel"}else if s["video"]!=""{"Add Video Bookmark"}else{"Add Video Bookmark"})];if !history&&!channels{drawer[0]["enabled"]=json!(s["video"].as_str().is_some_and(|v|!v.is_empty()));}if s["status"].as_str().is_some_and(|v|!v.is_empty()){drawer.push(node("label","drawer.status",s["status"].as_str().unwrap()));}
 let mut previous=Value::Null;for item in s["listing"]["items"].as_array().into_iter().flatten(){let id=item["id"].as_str().unwrap();drawer.push(json!({"kind":"media_record","id":format!("open:{id}"),"text":item["title"],"url":item["url"],"video":item["video"],"time":item["time"],"previous_time":previous,"sections":history,"copy":format!("copy:{id}"),"delete":format!("remove:{id}"),"cache_epoch":s["cache_epoch"]}));previous=item["time"].clone();}
 if s["listing"]["total"]==0{drawer.push(node("label","empty.library",if history{"No Video History Yet"}else if channels{"No Channels Yet"}else{"No Bookmarks Yet"}));}if s["page"].as_u64().unwrap_or(0)>0{drawer.push(button("list.previous","Previous Videos"));}if (s["page"].as_u64().unwrap_or(0)+1)*20<s["listing"]["total"].as_u64().unwrap_or(0){drawer.push(button("list.more","More Videos"));}
 let mode=s["menu"].as_str().unwrap_or("");let mut menu=match mode{"account"=>vec![node("label","account.info","This viewer stays signed out. Use the YouTube app for account history or videos that require sign-in."),button("menu.close","Keep Watching"),button("youtube.external","Open YouTube App")],"external"=>vec![node("label","external.info","This link opens outside the signed-out YouTube viewer."),button("menu.close","Keep Watching"),button("link.external","Open in Browser")],"problem"=>vec![node("label","problem.info","YouTube Could Not Load. Check Your Connection and Try Again."),button("problem.retry","Retry"),button("viewer.close","Close")],_=>vec![]};
 if !mode.is_empty() && s["status"].as_str().is_some_and(|v|!v.is_empty()){menu.insert(0,node("label","menu.status",s["status"].as_str().unwrap()));}
 let mut f=frame("YouTube",s.clone(),actions,body);f["effect"]=request;f["full_bleed"]=json!(true);f["header_loading"]=json!(true);f["hide_header_landscape"]=json!(true);f["close_requested"]=s["exit"].clone();f["drawer"]=json!(drawer);f["drawer_open"]=json!(s["drawer"]!="");f["drawer_title"]=json!(if history{"History"}else if channels{"Channels"}else{"Bookmarks"});f["drawer_close"]=json!("drawer.close");f["drawer_width"]=json!(0.92);f["drawer_max_width"]=json!(420);f["menu"]=json!(menu);f["menu_open"]=json!(!mode.is_empty());f["menu_title"]=json!(if mode=="account"{"YouTube Sign-In"}else{"YouTube"});f["menu_close"]=json!("menu.close");if s["confirm"].is_object(){f["confirm"]=json!({"title":s["confirm"]["title"],"text":s["confirm"]["text"],"accept":"confirm.accept","cancel":"confirm.cancel","accept_label":s["confirm"]["accept"]});}f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}

#[cfg(test)]mod channel_tests{
 use super::*;
 #[test]fn late_metadata_preserves_current_owner_but_home_navigation_clears_it(){
  let mut s=controller("YouTube",json!({}))["state"].clone();s["url"]=json!("https://www.youtube.com/watch?v=AbC_def-123");s["video"]=json!("AbC_def-123");s["channel"]=json!("https://www.youtube.com/@Owner");
  let f=controller("YouTube",json!({"state":s,"event":{"action":"web.changed","value":{"url":"https://www.youtube.com/watch?v=AbC_def-123","video":"AbC_def-123","channel":""}}}));assert_eq!(f["state"]["channel"],"https://www.youtube.com/@Owner");
  let mut s=f["state"].clone();s["url"]=json!("https://www.youtube.com/@Owner");s["video"]=json!("");
  let f=controller("YouTube",json!({"state":s,"event":{"action":"web.changed","value":{"url":"https://www.youtube.com/","video":"","channel":""}}}));assert_eq!(f["state"]["channel"],"");
 }
}
