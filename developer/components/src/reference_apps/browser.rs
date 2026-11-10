//! Browser application state/UI. The web engine is an SDK component, never a guest native bridge.
use crate::{collections,effect,frame,node,plain,serde_json::{json,Value}};
fn button(id:&str,text:&str)->Value{node("button",id,text)}
fn icon(id:&str,text:&str,icon:&str)->Value{json!({"kind":"icon","id":id,"text":text,"icon":icon,"row":"navigation"})}
fn field(id:&str,value:&Value,hint:&str,limit:u64)->Value{json!({"kind":"field","id":id,"value":value,"hint":hint,"limit":limit})}
fn current(s:&Value)->&Value{&s["meta"]["tabs"][s["meta"]["selected"].as_u64().unwrap_or(0) as usize]}
fn command(s:&mut Value,c:&str){s["seq"]=json!(s["seq"].as_u64().unwrap_or(0).wrapping_add(1));s["command"]=json!(c);}
fn list(s:&Value)->Value{effect("browser.bookmarks",json!({"query":s["query"],"offset":s["page"].as_u64().unwrap_or(0)*20}),"bookmarks.loaded")}
const HISTORY:&str="browser_history_v1";
fn history_save(s:&Value)->Value{collections::transact(HISTORY,s["history_revision"].as_u64().unwrap_or(0),1,None,vec![collections::put("entries",json!({"items":s["history"]}))],"history.saved")}
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"meta":null,"address":"","editing":false,"seq":0,"command":"load","menu":"","drawer":false,"query":"","page":0,"bookmarks":{"items":[],"total":0},"confirm":null,"back":false,"forward":false,"status":""});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let v=e["value"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let reply=&e["reply"]["value"];let mut request=Value::Null;
 match a{
 "start"|"retry"=>request=collections::get(HISTORY,"entries","history.loaded"),
 "history.loaded"=>{if ok{s["history_revision"]=reply["revision"].clone();s["history"]=reply["data"]["items"].as_array().map(|a|json!(a)).unwrap_or(json!([]));}if !s["meta"].is_object(){request=effect("browser.state",json!({}),"loaded");}},
 "history"=>{if s["drawer"]==true&&s["drawer_mode"]=="history"{s["drawer"]=json!(false);}else{s["drawer"]=json!(true);s["drawer_mode"]=json!("history");s["menu"]=json!("");s["history_page"]=json!(0);s["status"]=json!("");request=collections::get(HISTORY,"entries","history.loaded");}},
 "history.more"=>s["history_page"]=json!(s["history_page"].as_u64().unwrap_or(0)+1),
 "history.previous"=>s["history_page"]=json!(s["history_page"].as_u64().unwrap_or(0).saturating_sub(1)),
 "history.clear"=>s["confirm"]=json!({"kind":"history","title":"Clear History?","text":"Remove visited pages from this browser’s history? Bookmarks and website sign-ins are kept."}),
 "history.saved" if ok=>s["history_revision"]=reply["revision"].clone(),
 "history.cleared" if ok=>{s["history_revision"]=reply["revision"].clone();s["history"]=json!([]);s["history_page"]=json!(0);s["history_suppressed"]=current(&s)["url"].clone();let all=s["clearing_data"]==true;s["clearing_data"]=json!(false);command(&mut s,if all{"clear"}else{"history.clear"});s["back"]=json!(false);s["forward"]=json!(false);},
 _ if a.starts_with("history.open:")=>{if let Ok(i)=a[13..].parse::<usize>(){if let Some(item)=s["history"].as_array().and_then(|a|a.get(i)){request=effect("browser.navigate",json!({"address":item["url"]}),"navigated");s["history_suppressed"]=Value::Null;}}},
 "loaded"|"navigated" if ok=>{s["meta"]=reply.clone();s["address"]=current(&s)["url"].clone();s["editing"]=json!(false);s["menu"]=json!("");s["drawer"]=json!(false);s["back"]=json!(false);s["forward"]=json!(false);command(&mut s,"load");},
 "address"=>{s["address"]=json!(plain(v,4096));s["editing"]=json!(true);},
 "go"=>{s["history_suppressed"]=Value::Null;request=effect("browser.navigate",json!({"address":s["address"]}),"navigated");},
 "home"=>request=effect("browser.navigate",json!({"address":s["meta"]["home"]}),"navigated"),
 "web.changed"=>{s["back"]=e["value"]["back"].clone();s["forward"]=e["value"]["forward"].clone();request=effect("browser.visited",e["value"].clone(),"visited");},
 "visited" if ok=>{s["meta"]=reply.clone();if s["editing"]!=true{s["address"]=current(&s)["url"].clone();}let t=current(&s).clone();if t["url"]!=s["history_suppressed"]{s["history_suppressed"]=Value::Null;let mut items=s["history"].as_array().cloned().unwrap_or_default();items.retain(|h|h["url"]!=t["url"]);items.insert(0,json!({"url":t["url"],"title":t["title"]}));items.truncate(100);while json!({"items":items}).to_string().len()>15000{items.pop();}s["history"]=json!(items);request=history_save(&s);}},
 "back"|"forward"|"reload"=>command(&mut s,a),
 "tabs"=>s["menu"]=json!("tabs"),"tab.new"=>request=effect("browser.new",json!({}),"navigated"),
 "settings"=>{s["menu"]=json!("settings");s["settings"]=json!({"home":s["meta"]["home"],"newtab":s["meta"]["newtab"],"engine":s["meta"]["engine"]});},
 "settings.home"|"settings.newtab"|"settings.engine"=>s["settings"][a.split_once('.').unwrap().1]=json!(plain(v,4096)),
 "settings.save"=>request=effect("browser.settings",s["settings"].clone(),"settings.saved"),
 "settings.saved" if ok=>{s["meta"]=reply.clone();s["menu"]=json!("");},
 "menu.close"=>{if s["menu"]=="find"{command(&mut s,"find.close");}s["menu"]=json!("");},
 "find"=>{s["menu"]=json!("find");s["find"]=json!("");},"find.text"=>{s["find"]=json!(plain(v,512));command(&mut s,"find");},"find.next"|"find.previous"=>command(&mut s,a),
 "bookmarks"=>{s["drawer_mode"]=json!("bookmarks");s["drawer"]=json!(true);s["menu"]=json!("");s["page"]=json!(0);request=list(&s);},
 "drawer.close"=>s["drawer"]=json!(false),"bookmark.add"=>request=effect("browser.bookmark",json!({}),"bookmark.changed"),
 "bookmark.changed" if ok=>{s["meta"]=reply.clone();request=list(&s);},"bookmarks.loaded" if ok=>s["bookmarks"]=reply.clone(),
 "bookmarks.query"=>{s["query"]=json!(plain(v,240));s["page"]=json!(0);request=list(&s);},"bookmarks.more"=>{s["page"]=json!(s["page"].as_u64().unwrap_or(0)+1);request=list(&s);},"bookmarks.previous"=>{s["page"]=json!(s["page"].as_u64().unwrap_or(0).saturating_sub(1));request=list(&s);},
 "share"=>request=effect("share.text",json!({"text":current(&s)["url"],"inline":true}),"shared"),"external"=>request=effect("links.external",json!({"url":current(&s)["url"]}),"shared"),
 "clear"=>s["confirm"]=json!({"kind":"data","title":"Clear Browsing Data?","text":"Remove this app’s website cookies, local website storage and history. Bookmarks and preferences are kept."}),
 "confirm.cancel"=>s["confirm"]=Value::Null,
 "confirm.accept"=>{if s["confirm"]["kind"]=="data"||s["confirm"]["kind"]=="history"{s["clearing_data"]=json!(s["confirm"]["kind"]=="data");request=collections::clear(HISTORY,s["history_revision"].as_u64().unwrap_or(0),"history.cleared");if s["clearing_data"]==true{s["menu"]=json!("");}}else{request=effect("browser.remove",json!({"id":s["confirm"]["id"]}),"bookmark.changed");}s["confirm"]=Value::Null;},
 _ if a.starts_with("tab.open:")=>request=effect("browser.select",json!({"id":&a[9..]}),"navigated"),
 _ if a.starts_with("tab.close:")=>request=effect("browser.close",json!({"id":&a[10..]}),"navigated"),
 _ if a.starts_with("bookmark.open:")=>{if let Some(b)=s["bookmarks"]["items"].as_array().into_iter().flatten().find(|b|b["id"]==&a[14..]){request=effect("browser.navigate",json!({"address":b["url"]}),"navigated");}},
 _ if a.starts_with("bookmark.remove:")=>s["confirm"]=json!({"kind":"bookmark","id":&a[16..],"title":"Remove Bookmark?","text":"Remove this page from Bookmarks?"}),
 _=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could Not Complete Action"));}else if a=="go"||a=="home"{s["status"]=json!("");}
 let mut action=button("bookmarks","Bookmarks");action["icon"]=json!("bookmark");let mut body=Vec::new();
 if s["meta"].is_object(){let mut address=field("address",&s["address"],"Search or Enter Address",4096);address["row"]=json!("address");address["submit"]=json!("go");body.push(address);let mut go=icon("go","Go","go");go["row"]=json!("address");go["compact"]=json!(true);body.push(go);body.push(json!({"kind":"web","id":"browser.page","url":current(&s)["url"],"tab":current(&s)["id"],"on_change":"web.changed","seq":s["seq"],"command":if s["command"]=="history.clear"{json!("find.close")}else{s["command"].clone()},"clear_history":s["command"]=="history.clear","find":s["find"],"javascript":true}));for (id,text,glyph) in [("back","Back","back"),("forward","Forward","forward"),("home","Home","home"),("reload","Reload","refresh"),("tabs","Tabs","tabs"),("settings","Browser Settings","settings")]{let mut n=icon(id,text,glyph);if id=="tabs"{n["badge"]=json!(s["meta"]["tabs"].as_array().map_or(1,|a|a.len()).to_string());}if id=="back"||id=="forward"{n["enabled"]=s[id].clone();}body.push(n);}}
 else{body.push(button("retry","Retry Open"));}
 if s["status"].as_str().is_some_and(|x|!x.is_empty()){body.push(node("label","status",s["status"].as_str().unwrap()));}
 let mut drawer=vec![button("bookmark.add","Bookmark This Page"),field("bookmarks.query",&s["query"],"Search Bookmarks",240)];for b in s["bookmarks"]["items"].as_array().into_iter().flatten(){let id=b["id"].as_str().unwrap();drawer.push(json!({"kind":"record","id":format!("bookmark.open:{id}"),"text":b["title"],"detail":b["url"],"delete":format!("bookmark.remove:{id}")}));}if s["bookmarks"]["total"]==0{drawer.push(node("label","empty","No Bookmarks Yet"));}if s["page"].as_u64().unwrap_or(0)>0{drawer.push(button("bookmarks.previous","Previous Bookmarks"));}if (s["page"].as_u64().unwrap_or(0)+1)*20<s["bookmarks"]["total"].as_u64().unwrap_or(0){drawer.push(button("bookmarks.more","More Bookmarks"));}
 let mode=s["menu"].as_str().unwrap_or("");let mut menu=Vec::new();match mode{
 "tabs"=>{for t in s["meta"]["tabs"].as_array().into_iter().flatten(){let id=t["id"].as_str().unwrap();menu.push(json!({"kind":"record","id":format!("tab.open:{id}"),"text":t["title"].as_str().filter(|s|!s.is_empty()).unwrap_or(t["url"].as_str().unwrap_or("Tab")),"detail":t["url"],"delete":format!("tab.close:{id}")}));}menu.push(button("tab.new",if s["meta"]["tabs"].as_array().map_or(0,|a|a.len())<8{"+ New Tab"}else{"8-Tab Limit Reached"}));},
 "settings"=>{menu.extend([button("share","Share Page"),button("external","Open in Browser"),button("find","Find on Page"),node("label","home.label","Home Page"),field("settings.home",&s["settings"]["home"],"Home Page",4096),node("label","newtab.label","New Tab Page"),field("settings.newtab",&s["settings"]["newtab"],"New Tab Page",4096),json!({"kind":"select","id":"settings.engine","text":"Search Engine","value":s["settings"]["engine"],"options":[{"value":"DuckDuckGo","text":"DuckDuckGo"},{"value":"Google","text":"Google"},{"value":"Bing","text":"Bing"}]}),button("clear","Clear Browsing Data"),button("settings.save","Save")]);},
 "find"=>menu.extend([field("find.text",&s["find"],"Find Text",512),button("find.previous","Previous"),button("find.next","Next")]),_=>{}}
 if !mode.is_empty(){menu.push(button("menu.close",if mode=="settings"{"Cancel"}else{"Done"}));}
 let history=s["drawer_mode"]=="history";
 if history {drawer=vec![button("history.clear","Clear History")];drawer[0]["enabled"]=json!(s["history"].as_array().is_some_and(|a|!a.is_empty()));let page=s["history_page"].as_u64().unwrap_or(0)as usize;for(i,h)in s["history"].as_array().into_iter().flatten().enumerate().skip(page*20).take(20){drawer.push(json!({"kind":"record","id":format!("history.open:{i}"),"text":h["title"].as_str().filter(|t|!t.trim().is_empty()).unwrap_or(h["url"].as_str().unwrap_or("Page")),"detail":h["url"]}));}let total=s["history"].as_array().map_or(0,|a|a.len());if total==0{drawer.push(node("label","history.empty","No History Yet"));}if page>0{drawer.push(button("history.previous","Previous Pages"));}if(page+1)*20<total{drawer.push(button("history.more","More Pages"));}}
 if s["status"].as_str().is_some_and(|s|!s.is_empty()){drawer.push(node("label","drawer.status",s["status"].as_str().unwrap()));}
 let mut history_action=button("history","History");history_action["icon"]=json!("history");
 let mut f=frame("Browser",s.clone(),vec![action,history_action],body);f["effect"]=request;f["drawer"]=json!(drawer);f["drawer_open"]=s["drawer"].clone();f["drawer_title"]=json!(if history{"History"}else{"Bookmarks"});f["drawer_close"]=json!("drawer.close");f["menu"]=json!(menu);f["menu_open"]=json!(!mode.is_empty());f["menu_title"]=json!(match mode{"tabs"=>"Tabs","find"=>"Find on Page",_=>"Browser Settings"});f["menu_close"]=json!("menu.close");if s["confirm"].is_object(){f["confirm"]=json!({"title":s["confirm"]["title"],"text":s["confirm"]["text"],"accept":"confirm.accept","cancel":"confirm.cancel","accept_label":if s["confirm"]["kind"]=="data"||s["confirm"]["kind"]=="history"{"Clear"}else{"Remove"}});}f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
