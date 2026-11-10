//! RSS UI/controller compiled to its own internal app package.
use crate::{node,effect,frame,plain,serde_json::{json,Value}};
fn field(id:&str,value:&Value,hint:&str,limit:usize)->Value{json!({"kind":"field","id":id,"value":value.as_str().unwrap_or(""),"hint":hint,"limit":limit})}
fn button(id:&str,text:&str)->Value{node("button",id,text)}
fn choice(id:&str,text:&str,checked:bool)->Value{json!({"kind":"toggle","id":id,"text":text,"checked":checked})}
fn select(id:&str,text:&str,value:&Value,options:Vec<Value>)->Value{json!({"kind":"select","id":id,"text":text,"value":value,"options":options})}
fn categories()->Vec<Value>{["US News","World News","Technology","Science","Health","Business","Arts & Culture","Environment","Sports","My Feeds"].into_iter().map(|c|json!({"value":c,"text":c})).collect()}
fn query(s:&Value,bookmarks:bool)->Value{effect("feeds.query",json!({"query":if bookmarks{&s["drawer_query"]}else{&s["query"]},"category":s["category"],"feed":s["feed"],"offset":s[if bookmarks{"saved_page"}else{"page"}].as_u64().unwrap_or(0)*20,"bookmarks":bookmarks}),if bookmarks{"bookmarks.loaded"}else{"listed"})}
fn metadata()->Value{effect("feeds.state",json!({}),"loaded")}
fn fetch_next(s:&mut Value)->Value{let f=s["queue"].as_array_mut().and_then(|a|if a.is_empty(){None}else{Some(a.remove(0))});if let Some(f)=f{s["status"]=json!(format!("Updating {}…",f["name"].as_str().unwrap_or("Feed")));effect("feeds.fetch",json!({"url":f["url"]}),"fetched")}else{s["status"]=json!("Updated Just Now");query(s,false)}}
fn request_refresh(s:&mut Value,force:bool)->Value{let now=s["meta"]["now"].as_u64().unwrap_or(0);let interval=s["meta"]["settings"]["interval"].as_u64().unwrap_or(30);let jobs:Vec<Value>=s["meta"]["feeds"].as_array().into_iter().flatten().filter(|f|f["enabled"]==true&&(force||interval>0&&(f["checked"].as_u64().unwrap_or(0)==0||now.saturating_sub(f["checked"].as_u64().unwrap_or(0))>=interval*60000))).cloned().collect();s["queue"]=json!(jobs);if jobs.is_empty(){s["status"]=json!("No Feeds Due for Refresh");query(s,false)}else{fetch_next(s)}}
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"meta":{},"listing":{"items":[],"total":0},"bookmarks":{"items":[],"total":0},"catalog":[],"category":"","feed":"","query":"","drawer_query":"","drawer_mode":"","menu_mode":"","page":0,"saved_page":0,"article":null,"queue":[],"status":"Loading Saved Articles…","auto":true,"confirm":null,"edit":null});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let v=e["value"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let value=&e["reply"]["value"];let mut request=Value::Null;
 match a{
 "start"|"retry"=>request=metadata(),
 "loaded" if ok=>{s["meta"]=value.clone();if s["auto"]==true{s["auto"]=json!(false);request=request_refresh(&mut s,false);}else{request=query(&s,false);}},
 "listed" if ok=>{s["listing"]=value.clone();},
 "bookmarks.loaded" if ok=>{s["bookmarks"]=value.clone();},
 "refresh"=>request=request_refresh(&mut s,true),
 "fetched"=>{if ok{s["meta"]=value.clone();}else{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Source Unavailable"));if s["status"].as_str().is_some_and(|x|x.contains("stopped")||x.contains("access is off")){s["queue"]=json!([]);}}if s["queue"].as_array().is_some_and(|a|!a.is_empty()){request=fetch_next(&mut s);}else{request=query(&s,false);}},
 "query"=>{s["query"]=json!(plain(v,240));s["page"]=json!(0);request=query(&s,false);},
 "category"=>{s["category"]=json!(v);s["feed"]=json!("");s["page"]=json!(0);request=query(&s,false);},
 "page.next"|"page.previous"|"saved.page.next"|"saved.page.previous"=>{let saved=a.starts_with("saved.");let key=if saved{"saved_page"}else{"page"};let p=s[key].as_u64().unwrap_or(0);s[key]=json!(if a.ends_with("page.next"){p+1}else{p.saturating_sub(1)});request=query(&s,saved);},
 "drawer.feeds"|"drawer.your"|"settings.feeds"=>{s["drawer_mode"]=json!("feeds");s["drawer_query"]=json!("");s["menu_mode"]=json!("");request=metadata();},
 "drawer.bookmarks"=>{s["drawer_mode"]=json!("bookmarks");s["drawer_query"]=json!("");s["saved_page"]=json!(0);s["menu_mode"]=json!("");request=query(&s,true);},
 "drawer.close"=>{s["drawer_mode"]=json!("");request=query(&s,false);},
 "drawer.query"=>{s["drawer_query"]=json!(plain(v,240));s["saved_page"]=json!(0);if s["drawer_mode"]=="bookmarks"{request=query(&s,true);}},
 "drawer.catalog"=>{s["drawer_mode"]=json!("catalog");s["drawer_query"]=json!("");request=effect("feeds.catalog",json!({}),"catalog.loaded");},
 "catalog.loaded" if ok=>{s["catalog"]=value.clone();},
 "drawer.add"=>{s["menu_mode"]=json!("add");s["drawer_mode"]=json!("");s["new_url"]=json!("");s["new_name"]=json!("");s["new_category"]=json!("My Feeds");},
 "new.url"=>s["new_url"]=json!(plain(v,4096)),"new.name"=>s["new_name"]=json!(plain(v,80)),"new.category"=>s["new_category"]=json!(v),
 "new.add"=>{request=effect("feeds.fetch",json!({"url":s["new_url"],"name":s["new_name"],"category":s["new_category"],"add":true}),"added");},
 "added" if ok=>{s["meta"]=value.clone();s["menu_mode"]=json!("");s["status"]=json!("Feed Added");request=query(&s,false);},
 "article.back"=>{s["article"]=Value::Null;request=query(&s,false);},
 "article.loaded" if ok=>{s["article"]=value.clone();s["drawer_mode"]=json!("");},
 "article.bookmark"=>request=effect("feeds.bookmark",json!({"id":s["article"]["id"]}),"article.marked"),
 "article.marked" if ok=>request=effect("feeds.article",json!({"id":s["article"]["id"]}),"article.loaded"),
 "article.web"=>request=effect("web.open",json!({"url":s["article"]["article"]["url"]}),"web.closed"),
 "article.share"=>request=effect("share.text",json!({"name":"Article","text":format!("{}\n{}",s["article"]["article"]["title"].as_str().unwrap_or("Article"),s["article"]["article"]["url"].as_str().unwrap_or(""))}),"shared"),
 "article.copy"=>request=effect("clipboard.write",json!({"text":s["article"]["article"]["url"]}),"shared"),
 "settings"=>{s["menu_mode"]=json!("settings");s["drawer_mode"]=json!("");s["settings"]=s["meta"]["settings"].clone();},
 "settings.done"=>request=effect("feeds.settings",s["settings"].clone(),"settings.saved"),
 "settings.saved" if ok=>{s["menu_mode"]=json!("");request=metadata();},
 "menu.close"=>s["menu_mode"]=json!(""),
 "edit.name"=>s["edit"]["name"]=json!(plain(v,80)),"edit.category"=>s["edit"]["category"]=json!(v),"edit.enabled"=>s["edit"]["enabled"]=json!(v=="true"),
 "edit.save"=>request=effect("feeds.edit",s["edit"].clone(),"edit.saved"),
 "edit.saved" if ok=>{s["menu_mode"]=json!("");request=metadata();},
 "confirm.cancel"=>s["confirm"]=Value::Null,
 "confirm.accept"=>{let op=s["confirm"]["op"].as_str().unwrap_or("");request=effect(op,s["confirm"]["args"].clone(),"confirmed");s["confirm"]=Value::Null;},
 "confirmed" if ok=>{s["menu_mode"]=json!("");if s["drawer_mode"]=="bookmarks"{request=query(&s,true);}else{request=metadata();}},
 "clear.cache"|"reset.read"|"feed.remove"=>{let (title,op,args)=match a{"clear.cache"=>("Clear Offline Cache?","feeds.clear_cache",json!({})),"reset.read"=>("Reset Read Marks?","feeds.reset_read",json!({})),_=>("Remove Feed?","feeds.remove",json!({"id":s["edit"]["id"]}))};s["confirm"]=json!({"title":title,"text":if a=="feed.remove"{"Saved article bookmarks are kept."}else{"Feeds and bookmarks are kept."},"op":op,"args":args});},
 _ if a.starts_with("settings.")=>{let key=&a[9..];if key=="interval"{s["settings"][key]=json!(v.parse::<u64>().unwrap_or(30));}else{s["settings"][key]=json!(v=="true");}},
 _ if a.starts_with("open:")||a.starts_with("saved:")=>{request=effect("feeds.article",json!({"id":a.split_once(':').unwrap().1}),"article.loaded");},
 _ if a.starts_with("bookmark:")||a.starts_with("savedmark:")=>{request=effect("feeds.bookmark",json!({"id":a.split_once(':').unwrap().1}),"marked");},
 "marked" if ok=>request=query(&s,s["drawer_mode"]=="bookmarks"),
 _ if a.starts_with("filter:")=>{s["feed"]=json!(&a[7..]);s["category"]=json!("");s["page"]=json!(0);s["drawer_mode"]=json!("");s["article"]=Value::Null;request=query(&s,false);},
 _ if a.starts_with("edit:")=>{s["edit"]=s["meta"]["feeds"].as_array().into_iter().flatten().find(|f|f["id"]==&a[5..]).cloned().unwrap_or(Value::Null);s["menu_mode"]=json!("edit");s["drawer_mode"]=json!("");},
 _ if a.starts_with("follow:")=>request=effect("feeds.follow",json!({"id":&a[7..],"enabled":v=="true"}),"followed"),
 "followed" if ok=>request=effect("feeds.catalog",json!({}),"catalog.loaded"),
 _=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could Not Complete Action"));}
 let actions=[("settings","RSS Settings","settings"),("drawer.bookmarks","Bookmarks","bookmark"),("drawer.feeds","RSS","rss")].into_iter().map(|(id,text,icon)|{let mut n=button(id,text);n["icon"]=json!(icon);n}).collect();
 let mut body=Vec::new();if s["article"].is_object(){let x=&s["article"]["article"];body.push(button("article.back","Back to Articles"));body.push(json!({"kind":"label","id":"article.title","text":x["title"],"font":23,"strong":true}));body.push(node("label","article.source",x["source"].as_str().unwrap_or("")));body.push(json!({"kind":"label","id":"article.body","text":x["body"].as_str().filter(|x|!x.is_empty()).unwrap_or("This Feed Provides a Headline Only."),"font":17,"selectable":true}));body.push(node("label","article.note","Feed text · Open the publisher for the full article."));body.extend([button("article.web","Read Full Article"),button("article.share","Share Article"),button("article.copy","Copy Article Link"),button("article.bookmark",if s["article"]["bookmarked"]==true{"Bookmarked"}else{"Bookmark"})]);}else{
 let mut cats=vec![json!({"value":"","text":"All"})];cats.extend(categories());body.push(json!({"kind":"chips","id":"category","value":s["category"],"options":cats}));let mut search=field("query",&s["query"],"Search Articles",240);search["row"]=json!("search");body.push(search);body.push(json!({"kind":"icon","id":"refresh","text":"Refresh Articles","icon":"refresh","row":"search","compact":true}));body.push(node("label","status",s["status"].as_str().unwrap_or("All Feeds")));rows(&mut body,&s["listing"],false);pages(&mut body,&s["listing"],s["page"].as_u64().unwrap_or(0),false);}
 let mut drawer=vec![field("drawer.query",&s["drawer_query"],if s["drawer_mode"]=="bookmarks"{"Search Bookmarks"}else{"Search Feeds"},240)];
 match s["drawer_mode"].as_str().unwrap_or(""){
 "bookmarks"=>{rows(&mut drawer,&s["bookmarks"],true);pages(&mut drawer,&s["bookmarks"],s["saved_page"].as_u64().unwrap_or(0),true);},
 "catalog"=>{drawer.push(button("drawer.your","Your Feeds"));let q=s["drawer_query"].as_str().unwrap_or("").to_lowercase();for f in s["catalog"].as_array().into_iter().flatten().filter(|f|format!("{} {}",f["name"],f["category"]).to_lowercase().contains(&q)){drawer.push(choice(&format!("follow:{}",f["id"].as_str().unwrap_or("")),&format!("{} · {}",f["name"].as_str().unwrap_or(""),f["category"].as_str().unwrap_or("")),f["enabled"]==true));}},
 "feeds"=>{drawer.push(button("drawer.catalog","Choose Sources"));drawer.push(button("drawer.add","Add a Feed"));let q=s["drawer_query"].as_str().unwrap_or("").to_lowercase();for f in s["meta"]["feeds"].as_array().into_iter().flatten().filter(|f|format!("{} {}",f["name"],f["category"]).to_lowercase().contains(&q)){drawer.push(json!({"kind":"record","id":format!("filter:{}",f["id"].as_str().unwrap_or("")),"text":f["name"],"detail":if f["enabled"]!=true{"Paused"}else if f["error"].as_str().is_some_and(|s|!s.is_empty()){f["error"].as_str().unwrap()}else{f["category"].as_str().unwrap_or("")},"trailing":{"id":format!("edit:{}",f["id"].as_str().unwrap_or("")),"text":format!("Edit {}",f["name"].as_str().unwrap_or("Feed")),"icon":"edit"}}));}},_=>{}
 }
 let mode=s["menu_mode"].as_str().unwrap_or("");let mut menu=Vec::new();match mode{
 "settings"=>{menu.push(select("settings.interval","Refresh When Opened",&json!(s["settings"]["interval"].as_u64().unwrap_or(30).to_string()),[("0","Manually"),("15","Every 15 Minutes"),("30","Every 30 Minutes"),("60","Every Hour")].into_iter().map(|(v,t)|json!({"value":v,"text":t})).collect()));menu.push(node("label","settings.background","No Background Refresh or Notifications"));for (key,label) in [("wifi","Wi-Fi Only"),("summaries","Show Summaries"),("unread","Unread Only"),("oldest","Oldest First")]{menu.push(choice(&format!("settings.{key}"),label,s["settings"][key]==true));}menu.extend([button("settings.feeds","Manage Feeds"),button("reset.read","Reset Read Marks"),button("clear.cache","Clear Offline Cache"),node("label","settings.privacy","Feeds and bookmarks stay on this phone. Sources receive normal web requests when refreshed."),button("settings.done","Done"),button("menu.close","Cancel")]);},
 "add"=>{menu.extend([field("new.url",&s["new_url"],"RSS or Atom Feed Address",4096),field("new.name",&s["new_name"],"Name (Optional)",80),select("new.category","Category",&s["new_category"],categories()),button("new.add","Add"),button("menu.close","Cancel")]);},
 "edit"=>{menu.extend([field("edit.name",&s["edit"]["name"],"Feed Name",80),select("edit.category","Category",&s["edit"]["category"],categories()),choice("edit.enabled","Active",s["edit"]["enabled"]==true),button("feed.remove","Remove Feed"),button("edit.save","Save"),button("menu.close","Cancel")]);},_=>{}
 }
 let mut f=frame("RSS Reader",s.clone(),actions,body);
 f["scroll_key"]=json!(if s["article"].is_object(){format!("article:{}",s["article"]["id"].as_str().unwrap_or(""))}else{json!(["articles",s["category"],s["feed"],s["query"],s["page"]]).to_string()});
 f["drawer_scroll_key"]=json!(json!([s["drawer_mode"],s["drawer_query"],s["saved_page"]]).to_string());
 if s["article"].is_object(){f["back_action"]=json!("article.back");}
 f["effect"]=request;f["drawer"]=json!(drawer);f["drawer_open"]=json!(!s["drawer_mode"].as_str().unwrap_or("").is_empty());f["drawer_title"]=json!(match s["drawer_mode"].as_str(){Some("bookmarks")=>"Bookmarks",Some("catalog")=>"Choose Sources",_=>"Your Feeds"});f["drawer_close"]=json!("drawer.close");f["menu"]=json!(menu);f["menu_open"]=json!(!mode.is_empty());f["menu_close"]=json!("menu.close");f["menu_title"]=json!(match mode{"settings"=>"RSS Settings","edit"=>"Edit Feed",_=>"Add a Feed"});f["revision_key"]=json!(s["article"]["id"].as_str().unwrap_or("list"));if s["confirm"].is_object(){f["confirm"]=json!({"title":s["confirm"]["title"],"text":s["confirm"]["text"],"accept":"confirm.accept","cancel":"confirm.cancel"});}f
}
fn rows(out:&mut Vec<Value>,page:&Value,bookmarks:bool){for a in page["items"].as_array().into_iter().flatten(){let id=a["id"].as_str().unwrap_or("");out.push(json!({"kind":"record","card":true,"id":format!("{}:{id}",if bookmarks{"saved"}else{"open"}),"text":a["title"],"detail":format!("{}\n{}",a["source"].as_str().unwrap_or(""),a["preview"].as_str().unwrap_or("")),"strong":a["read"]!=true,"trailing":{"id":format!("{}:{id}",if bookmarks{"savedmark"}else{"bookmark"}),"text":if a["bookmarked"]==true{"Remove Bookmark"}else{"Bookmark"},"icon":"bookmark","selected":a["bookmarked"]==true}}));}if page["total"].as_u64().unwrap_or(0)==0{out.push(node("label",if bookmarks{"empty.bookmarks"}else{"empty.articles"},if bookmarks{"No Bookmarks"}else{"No Articles Here Yet · Tap Refresh or Choose Feeds"}));}}
fn pages(out:&mut Vec<Value>,page:&Value,current:u64,saved:bool){if current>0{out.push(button(if saved{"saved.page.previous"}else{"page.previous"},"Previous Articles"));}if (current+1)*20<page["total"].as_u64().unwrap_or(0){out.push(button(if saved{"saved.page.next"}else{"page.next"},"More Articles"));}}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
