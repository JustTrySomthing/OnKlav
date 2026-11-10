//! Notes application logic. This is compiled into Notes.onklav-a, not into the APK.
use crate::{node,effect,frame,plain,serde_json::{json,Value}};
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"note":null,"records":[],"offset":0,"total":0,"query":"","drawer":false,"menu":false,"status":"Opening…","persisted":false,"confirm":null})}
 let event=&input["event"];let action=event["action"].as_str().unwrap_or("");let value=event["value"].as_str().unwrap_or("");let mut request=Value::Null;
 let ok=event["reply"]["ok"]==true;let reply=event["reply"]["value"].clone();
 match action {
  "start"|"retry"=>{request=effect("records.current",json!({}),"opened");},
  "initial" if ok=>{s["records"]=reply.clone();if let Some(first)=reply.as_array().and_then(|a|a.first()){request=effect("records.load",json!({"id":first["id"]}),"opened");}else{request=effect("records.new",json!({}),"created");}},
  "new"=>{request=effect("records.new",json!({}),"created");},
  "created"|"opened" if ok=>{s["note"]=reply;s["drawer"]=json!(false);s["menu"]=json!(false);s["persisted"]=json!(s["note"]["updated"].as_u64().unwrap_or(0)>0);s["status"]=json!("Saved");},
  "note.title"|"note.text"=>{let field=if action=="note.title"{"title"}else{"text"};let text=plain(value,if field=="title"{80}else{100000});s["note"][field]=json!(if field=="title"{text.replace(['\n','\t']," ")}else{text});if s["persisted"]==true||s["note"]["title"]!=""||s["note"]["text"]!=""{request=effect("records.save",json!({"note":s["note"]}),"saved");}},
  "save"=>{request=effect("records.save",json!({"note":s["note"]}),"saved");},
  "saved" if ok=>{s["note"]=reply;s["persisted"]=json!(true);s["status"]=json!("Saved");},
  "drawer"=>{s["drawer"]=json!(true);s["menu"]=json!(false);request=effect("records.page",json!({"query":s["query"],"offset":s["offset"]}),"listed");},
  "library.query"=>{s["offset"]=json!(0);s["query"]=json!(plain(value,80));request=effect("records.page",json!({"query":s["query"],"offset":s["offset"]}),"listed");},
  "library.next"=>{s["offset"]=json!(s["offset"].as_u64().unwrap_or(0)+40);request=effect("records.page",json!({"query":s["query"],"offset":s["offset"]}),"listed");},
  "library.previous"=>{s["offset"]=json!(s["offset"].as_u64().unwrap_or(0).saturating_sub(40));request=effect("records.page",json!({"query":s["query"],"offset":s["offset"]}),"listed");},
  "listed" if ok=>{s["records"]=reply["items"].clone();s["total"]=reply["total"].clone();},
  "close.drawer"=>{s["drawer"]=json!(false);},
  "settings"=>{s["menu"]=json!(true);s["export_menu"]=json!(false);s["about"]=json!(false);}
  "export.header"=>{s["menu"]=json!(true);s["export_menu"]=json!(true);}
  "about"=>{s["about"]=json!(true);},
  "close.menu"=>{s["menu"]=json!(false);},
  "copy"=>{request=effect("clipboard.write",json!({"text":s["note"]["text"]}),"copied");},
  "paste"=>{s["cursor"]=event["selection"].clone();request=effect("clipboard.read",json!({}),"pasted");},
  "pasted" if ok=>{s["menu"]=json!(false);let current=s["note"]["text"].as_str().unwrap_or("");let cursor=s["cursor"].as_u64().unwrap_or(current.encode_utf16().count() as u64) as usize;let mut units=0;let byte=current.char_indices().find_map(|(i,c)|{if units>=cursor{Some(i)}else{units+=c.len_utf16();None}}).unwrap_or(current.len());let inserted=plain(reply.as_str().unwrap_or(""),100000usize.saturating_sub(current.encode_utf16().count()));s["note"]["text"]=json!(format!("{}{}{}",&current[..byte],inserted,&current[byte..]));request=effect("records.save",json!({"note":s["note"]}),"saved");},
  "export"|"share"=>{request=effect(if action=="export"{"files.export"}else{"share.text"},json!({"text":format!("{}\n\n{}",s["note"]["title"].as_str().filter(|s|!s.trim().is_empty()).unwrap_or("Untitled Note"),s["note"]["text"].as_str().unwrap_or("")),"name":s["note"]["title"].as_str().filter(|s|!s.trim().is_empty()).unwrap_or("Untitled Note")}),"exported");},
  "copied"|"exported" if ok=>{s["menu"]=json!(false);s["status"]=json!(if action=="copied"{"Copied"}else{"Done"});},
  "delete.confirm"=>{if let Some(id)=s["confirm"].as_str(){request=effect("records.delete",json!({"id":id}),"deleted");}s["confirm"]=Value::Null;},
  "delete.cancel"=>{s["confirm"]=Value::Null;},
  "deleted" if ok=>{if s["deleted_current"]==true{request=effect("records.new",json!({}),"created");}else{request=effect("records.page",json!({"query":s["query"],"offset":s["offset"]}),"listed");}},
  _ if action.starts_with("open:")=>{request=effect("records.load",json!({"id":&action[5..]}),"opened");},
  _ if action.starts_with("delete:")=>{s["confirm"]=json!(&action[7..]);s["deleted_current"]=json!(s["note"]["id"]==&action[7..]);},
  _=>{}
 }
 if event["reply"]["ok"]==false{s["status"]=json!(event["reply"]["error"].as_str().unwrap_or("Could not complete action"));}
 let mut actions=vec![node("button","export.header","Export"),node("button","settings","Notes Settings"),node("button","drawer","Saved Notes")];
 for (n,icon) in actions.iter_mut().zip(["download","settings","folder"]){n["icon"]=json!(icon);}
 actions.rotate_right(1); let note=&s["note"];let body=if note.is_object(){vec![json!({"kind":"field","id":"note.title","value":note["title"],"hint":"Untitled Note","limit":80,"font":20}),json!({"kind":"editor","id":"note.text","value":note["text"],"hint":"Write or Paste Your Note…","limit":100000}),node("label","status",&format!("{} / 100,000 · {}",note["text"].as_str().unwrap_or("").encode_utf16().count(),s["status"].as_str().unwrap_or("")))]}else{vec![node("label","status",s["status"].as_str().unwrap_or("Opening…")),node("button","retry","Retry Open")]};
 let mut f=frame("Notes",s.clone(),actions,body);f["effect"]=request;f["header_actions"]=json!(3);
 f["drawer_title"]=json!("Saved Notes");f["drawer_close"]=json!("close.drawer");f["menu_close"]=json!("close.menu");f["menu_title"]=json!(if s["export_menu"]==true{"Export Notes"}else{"Notes"});f["revision_key"]=s["note"]["id"].clone();f["drawer_open"]=s["drawer"].clone();f["menu_open"]=s["menu"].clone();
 let mut drawer=vec![node("button","close.drawer","Close Saved Notes"),node("button","new","+ New"),json!({"kind":"field","id":"library.query","value":s["query"],"hint":"Search Saved Notes","limit":80})];
 for n in s["records"].as_array().into_iter().flatten(){let id=n["id"].as_str().unwrap_or("");let title=n["title"].as_str().filter(|s|!s.trim().is_empty()).or_else(||n["preview"].as_str().filter(|s|!s.trim().is_empty())).unwrap_or("Untitled Note");drawer.push(json!({"kind":"record","id":format!("open:{id}"),"text":title,"detail":n["preview"],"delete":format!("delete:{id}")}));}
 if s["records"].as_array().is_none_or(|a|a.is_empty()){drawer.push(node("label","empty.library","No Saved Notes"));}
 if s["offset"].as_u64().unwrap_or(0)>0{drawer.push(node("button","library.previous","Previous Notes"));}
 if s["offset"].as_u64().unwrap_or(0)+40<s["total"].as_u64().unwrap_or(0){drawer.push(node("button","library.next","More Notes"));}
 f["drawer"]=json!(drawer);
 f["menu"]=if s["export_menu"]==true{json!([node("button","export","Save to Files"),node("button","share","Share Text"),node("button","close.menu","Close")])}else if s["about"]==true{json!([node("label","about.text","Saved privately on this phone. Use Download to save a copy in Files, or Share to send it."),node("button","close.menu","Close")])}else{json!([node("button","paste","Paste"),node("button","copy","Copy Text"),node("button","about","About Saved Notes"),node("button","close.menu","Close")])};
 if s["confirm"].is_string(){f["confirm"]=json!({"title":"Delete Note?","text":"This removes the saved note from OnKlav.","accept":"delete.confirm","cancel":"delete.cancel"});}
 f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
