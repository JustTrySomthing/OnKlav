//! Files controller: all paths are host-owned opaque identities and all UI uses SDK nodes.
use crate::{effect,frame,node,plain,serde_json::{json,Value}};
fn listing(s:&Value)->Value{if s["folder"]=="external"||s["external_mode"]==true{effect("library.external",json!({"operation":"list","folder":s["folder"],"query":s["query"],"offset":s["offset"]}),"listed")}else{effect("library.list",json!({"folder":s["folder"],"query":s["query"],"offset":s["offset"]}),"listed")}}
fn op(s:&Value,action:&str,args:Value,then:&str)->Value{if s["external_mode"]==true{let mut a=args;a["operation"]=json!(action);effect("library.external",a,then)}else{effect(&format!("library.{action}"),args,then)}}
fn button(id:&str,text:&str,icon:&str)->Value{let mut b=node("button",id,text);if !icon.is_empty(){b["icon"]=json!(icon)}b}
fn editable(item:&Value)->bool {item["protected"]!=true&&item["editable"]!=false&&item["read_only"]!=true&&item["writable"]!=false&&!item["id"].as_str().unwrap_or("").starts_with("system")&&item["connected_root"]!=true}
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"folder":"","parent":"","title":"Files","query":"","offset":0,"items":[],"total":0,"status":"","selected":null,"menu":false,"name":"","dialog":"","destination":null,"external_mode":false,"preview":null,"confirm":""});}
 let ev=&input["event"];let action=ev["action"].as_str().unwrap_or("");let val=ev["value"].as_str().unwrap_or("");let ok=ev["reply"]["ok"]==true;let reply=&ev["reply"]["value"];let mut request=Value::Null;
 match action{
 "start"|"refresh"|"retry"=>{s["status"]=json!("");request=listing(&s)},
 "listed" if ok=>{s["items"]=reply["items"].clone();s["total"]=reply["total"].clone();s["parent"]=reply["parent"].clone();s["title"]=reply["title"].clone();s["folder"]=reply["folder"].clone();s["external_mode"]=json!(reply["external"]==true);s["in_trash"]=json!(reply["trash"]==true);s["status"]=json!("");},
 "query"=>{s["query"]=json!(plain(val,80));s["offset"]=json!(0);request=listing(&s)},
 "home"=>{s["folder"]=json!("");s["query"]=json!("");s["offset"]=json!(0);s["external_mode"]=json!(false);s["selected"]=Value::Null;s["preview"]=Value::Null;s["destination"]=Value::Null;request=listing(&s)},
 "up"=>{s["folder"]=s["parent"].clone();if s["folder"]==""{s["external_mode"]=json!(false);}s["query"]=json!("");s["offset"]=json!(0);request=listing(&s)},
 "next"=>{s["offset"]=json!(s["offset"].as_u64().unwrap_or(0)+40);request=listing(&s)},
 "previous"=>{s["offset"]=json!(s["offset"].as_u64().unwrap_or(0).saturating_sub(40));request=listing(&s)},
 "new.folder"=>{s["selected"]=Value::Null;s["status"]=json!("");s["dialog"]=json!("new");s["name"]=json!("");s["menu"]=json!(true);},
 "rename"=>{s["status"]=json!("");s["dialog"]=json!("rename");s["name"]=s["selected"]["name"].clone();s["menu"]=json!(true);},
 "name"=>{s["name"]=json!(plain(val,180));s["status"]=json!("");},
 "name.save"=>{let rename=s["dialog"]=="rename";request=op(&s,if rename{"rename"}else{"folder"},json!({"id":s["selected"]["id"],"parent":if s["folder"]==""{json!("documents")}else{s["folder"].clone()},"name":s["name"]}),"changed");},
 "menu.close"=>{s["status"]=json!("");s["menu"]=json!(false);s["dialog"]=json!("");s["selected"]=Value::Null;s["preview"]=Value::Null;s["confirm"]=json!("");},
 "copy"=>{s["clipboard"]=json!({"id":s["selected"]["id"],"name":s["selected"]["name"],"external":s["external_mode"]==true});s["menu"]=json!(false);s["status"]=json!("Copied. Open a destination folder and choose Paste.");},
 "paste"|"paste.here"=>{let parent=if action=="paste"&&s["selected"]["folder"]==true{s["selected"]["id"].clone()}else{s["folder"].clone()};if s["clipboard"]["external"]==json!(s["external_mode"]==true){request=op(&s,"copy",json!({"id":s["clipboard"]["id"],"parent":parent}),"changed");}else{s["status"]=json!("Paste within the same storage location. Use Import or Save a Copy for Android files.");}},
 "compress"=>{request=op(&s,"compress",json!({"id":s["selected"]["id"]}),"changed");},
 "share"=>{request=op(&s,"share",json!({"id":s["selected"]["id"]}),"opened");},
 "move"=>{s["destination"]=json!({"id":s["selected"]["id"],"name":s["selected"]["name"],"action":action});s["menu"]=json!(false);s["selected"]=Value::Null;s["preview"]=Value::Null;s["folder"]=json!(if s["external_mode"]==true{"external"}else{""});s["query"]=json!("");s["offset"]=json!(0);request=listing(&s);},
 "destination.cancel"=>{s["destination"]=Value::Null;request=listing(&s)},
 "destination.accept"=>{let d=&s["destination"];request=op(&s,d["action"].as_str().unwrap_or("move"),json!({"id":d["id"],"parent":s["folder"]}),"moved");},
 "moved" if ok=>{s["destination"]=Value::Null;s["status"]=json!("Done");request=listing(&s)},
 "trash"=>{s["confirm"]=json!("trash");},
 "restore.all"=>{request=effect("library.restore_all",json!({}),"changed");},
 "empty.trash"=>{s["confirm"]=json!("empty");},
 "purge"=>{s["confirm"]=json!("purge");},
 "confirm.cancel"=>{s["confirm"]=json!("");},
 "confirm.accept"=>{let op_name=if s["confirm"]=="empty"{"empty"}else if s["confirm"]=="purge"{"purge"}else{"trash"};request=op(&s,op_name,json!({"id":s["selected"]["id"]}),"changed");s["confirm"]=json!("");},
 "disconnect"=>{request=op(&s,"disconnect",json!({"id":s["selected"]["id"]}),"changed");},
 "restore"=>{request=op(&s,"restore",json!({"id":s["selected"]["id"]}),"changed");},
 "import"=>{request=effect("library.import",json!({"folder":s["folder"]}),"changed")},
 "external.connect"=>{request=effect("library.external",json!({"operation":"connect"}),"changed")},
 "external.browse"=>{request=effect("library.external",json!({"operation":"browse"}),"changed")},
 "open"=>{request=op(&s,"open",json!({"id":s["selected"]["id"]}),"opened")},
 "export"=>{request=op(&s,"export",json!({"id":s["selected"]["id"]}),"opened")},
 "changed" if ok=>{if s["dialog"]=="new"&&s["folder"]==""{s["folder"]=json!("documents");}s["dialog"]=json!("");s["menu"]=json!(false);s["selected"]=Value::Null;s["preview"]=Value::Null;request=listing(&s);},
 "previewed" if ok=>{s["preview"]=reply.clone();s["menu"]=json!(true);s["dialog"]=json!("preview");},
 "preview"=>{request=op(&s,"info",json!({"id":s["selected"]["id"]}),"previewed")},
 "opened" if ok=>{s["menu"]=json!(false);s["selected"]=Value::Null;s["preview"]=Value::Null;request=listing(&s);},
 _ if action.starts_with("entry:")=>{let id=&action[6..];let item=s["items"].as_array().and_then(|a|a.iter().find(|r|r["id"]==id)).cloned();if let Some(item)=item{
 if item["folder"]==true{s["folder"]=item["id"].clone();s["query"]=json!("");s["offset"]=json!(0);request=listing(&s)}else{s["selected"]=item;request=op(&s,"open",json!({"id":s["selected"]["id"]}),"opened");}
 }},
 _ if action.starts_with("options:")=>{let id=&action[8..];s["selected"]=s["items"].as_array().and_then(|a|a.iter().find(|r|r["id"]==id)).cloned().unwrap_or(Value::Null);s["dialog"]=json!("options");s["menu"]=json!(true);},
 _=>{}
 }
 if ev["reply"]["ok"]==false{if action=="listed"{s["items"]=json!([]);s["selected"]=Value::Null;s["menu"]=json!(false);}s["status"]=json!(ev["reply"]["error"].as_str().unwrap_or("Could not complete this file action. Try again."));}
 let choosing=s["destination"].is_object();let external=s["external_mode"]==true||s["folder"]=="external";let trash=s["folder"]=="trash"||s["in_trash"]==true;let system=s["folder"].as_str().unwrap_or("").starts_with("system");let home=s["folder"]=="";
 let mut actions=vec![button("home","Files Home","home")];
 if !home{actions.push(button("up","Parent Folder","back"));}
 let header_actions=actions.len();actions.push(button("refresh","Refresh","refresh"));
 let mut search=json!({"kind":"field","id":"query","value":s["query"],"hint":"Search Files","limit":80});if !trash&&!system&&s["folder"]!="external"{search["trailing"]=json!({"id":"new.folder","text":"Create folder","icon":"folder.add"});}let mut body=vec![search,node("label","location",s["title"].as_str().unwrap_or("Files"))];
 if choosing{body.push(node("label","destination.caption",&format!("Choose a folder for {}",s["destination"]["name"].as_str().unwrap_or("this item"))));if !home&&s["folder"]!="external"&&!trash&&!system{body.push(button("destination.accept","Use This Folder",""))}body.push(button("destination.cancel","Cancel Move or Copy",""));}
 else if trash{if s["folder"]=="trash"{body.push(button("restore.all","Restore All",""));body.push(button("empty.trash","Empty Trash",""));}else{body.push(node("label","trash.help","Restore items before changing them."));}}
 else if external{body.push(node("label","external.help","Connect a folder to manage it here. Browse Android Files to copy files from Downloads or other locations into OnKlav Downloads. Disconnecting a folder keeps its Android files."));body.push(button("external.connect","Connect Android Folder",""));body.push(button("external.browse","Browse Android Files",""));}
 else if !home&&!trash&&!system{body.push(button("import","Import Files",""));}
 if system{body.push(node("label","system.help","System files are protected. Your documents and media are in the other folders."));}
 if let Some(items)=s["items"].as_array(){for item in items{if choosing&&(item["folder"]!=true||["system","trash"].contains(&item["id"].as_str().unwrap_or(""))||(!external&&item["id"]=="external")){continue}let id=item["id"].as_str().unwrap_or("");let mut row=node("record",&format!("entry:{id}"),item["name"].as_str().unwrap_or("File"));row["detail"]=item["detail"].clone();row["leading_icon"]=json!(if item["folder"]==true{"folder"}else{"file"});if !choosing&&!system&&editable(item){row["trailing"]=json!({"id":format!("options:{id}"),"text":format!("Options for {}",item["name"].as_str().unwrap_or("file")),"icon":"edit"});}if !choosing&&item["connected_root"]==true{row["trailing"]=json!({"id":format!("options:{id}"),"text":"Connected folder settings","icon":"settings"});}body.push(row);}}
 if s["menu"]!=true&&s["clipboard"].is_object()&&!choosing&&!trash&&!system&&!home&&s["folder"]!="external"{body.push(button("paste.here","Paste Here",""));}
 if s["status"].as_str().unwrap_or("").is_empty()&&s["items"].as_array().is_some_and(|a|a.is_empty()){body.push(node("label","empty",if s["query"].as_str().unwrap_or("").is_empty(){if trash{"Trash is Empty"}else{"This Folder is Empty"}}else{"No Matching Files"}));}
 if s["offset"].as_u64().unwrap_or(0)>0{body.push(button("previous","Previous Files",""))}if s["offset"].as_u64().unwrap_or(0)+40<s["total"].as_u64().unwrap_or(0){body.push(button("next","More Files",""))}
 if s["status"].as_str().is_some_and(|s|!s.is_empty()){body.push(node("label","status",s["status"].as_str().unwrap()));body.push(button("retry","Try Again",""));}
 let mut f=frame("Files",s.clone(),actions,body);f["effect"]=request;f["header_actions"]=json!(header_actions);if !home{f["back_action"]=json!("up");}else if choosing{f["back_action"]=json!("destination.cancel");}f["menu_open"]=s["menu"].clone();f["menu_close"]=json!("menu.close");f["menu_title"]=s["selected"]["name"].as_str().map(|x|json!(x)).unwrap_or(json!(if home{"New Folder in Documents"}else{"New Folder"}));
 let selected=&s["selected"];let mut menu=vec![];let can_paste=s["clipboard"].is_object()&&s["clipboard"]["external"]==json!(external)&&!trash&&!system&&s["folder"]!="external";
 if s["dialog"]=="new"||s["dialog"]=="rename"{menu.push(json!({"kind":"field","id":"name","value":s["name"],"hint":if s["dialog"]=="new"{"Folder Name"}else{"File Name"},"limit":180}));menu.push(button("name.save","Save",""));}
 else if s["dialog"]=="preview"{menu.push(node("label","properties",s["preview"]["properties"].as_str().unwrap_or("")));menu.push(node("label","preview.location",s["preview"]["location"].as_str().unwrap_or("")));let mut text=node("label","preview.text",s["preview"]["preview"].as_str().unwrap_or(""));text["selectable"]=json!(true);menu.push(text);if selected["protected"]!=true&&selected["folder"]!=true{menu.push(button("open","Open",""));}}
 else if selected.is_object(){
 if selected["trash"]==true{menu.push(button("restore","Restore",""));menu.push(button("purge","Delete Permanently",""));}
 else if selected["protected"]==true{if can_paste&&selected["folder"]==true&&!["system","trash","external"].contains(&selected["id"].as_str().unwrap_or("")){menu.push(button("paste","Paste",""));}menu.push(button("preview","Show Properties",""));}
 else {let root=selected["connected_root"]==true;
 if !root{menu.push(button("copy","Copy",""));}
 let mut paste=button("paste","Paste","");paste["enabled"]=json!(can_paste);menu.push(paste);
 if !root{menu.push(button("trash","Delete",""));menu.push(button("rename","Rename",""));menu.push(button("move","Move",""));}
 menu.push(button("preview","Show Properties",""));menu.push(button("share","Share",""));menu.push(button("compress","Compress",""));
 if root{menu.push(button("disconnect","Disconnect Folder",""));}
 if selected["folder"]!=true{menu.push(button("export","Save a Copy to Android",""));}
 }}

 if s["status"].as_str().is_some_and(|s|!s.is_empty()){menu.push(node("label","menu.status",s["status"].as_str().unwrap()));}menu.push(button("menu.close","Close",""));f["menu"]=json!(menu);
 if s["confirm"]!=""{let permanent=s["confirm"]=="purge"||s["confirm"]=="empty"||external;f["confirm"]=json!({"title":if permanent{"Delete Permanently?"}else{"Move to Trash?"},"text":if s["confirm"]=="empty"{"This permanently deletes every item in Trash. This cannot be undone."}else if permanent{"This permanently deletes the selected file or folder and its contents. This cannot be undone."}else{"You can restore this item from Trash. Folders include their contents."},"accept":"confirm.accept","cancel":"confirm.cancel"});}
 f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f["file_browser"]=json!(true);f}

#[cfg(test)]mod tests{use super::*;#[test]fn folders_have_the_same_edit_menu_and_copy_paste_targets(){let s=json!({"folder":"documents","query":"","offset":0,"items":[{"id":"a","name":"Folder","folder":true}],"selected":null,"menu":false});let f=controller("Files",json!({"state":s}));assert_eq!(f["body"][0]["trailing"]["icon"],"folder.add");assert_eq!(f["body"][1]["kind"],"label");let f=controller("Files",json!({"state":f["state"],"event":{"action":"options:a"}}));for name in ["Copy","Paste","Delete","Rename","Move","Show Properties","Share","Compress"]{assert!(f["menu"].as_array().unwrap().iter().any(|n|n["text"]==name));}let f=controller("Files",json!({"state":f["state"],"event":{"action":"copy"}}));assert!(f["effect"].is_null());let f=controller("Files",json!({"state":f["state"],"event":{"action":"paste.here"}}));assert_eq!(f["effect"]["service"],"library.copy");assert_eq!(f["effect"]["args"]["parent"],"documents");}}
