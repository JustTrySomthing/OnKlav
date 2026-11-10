//! Read Aloud controller, independently compiled into an internal SDK package.
use crate::{node,effect,frame,plain,serde_json::{json,Value}};
fn step(input:Value)->Value {
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"note":null,"status":"Opening…","settings":false,"clear":false});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let reply=&e["reply"]["value"];let mut request=Value::Null;
 match a {
 "start"|"retry"=>request=effect("records.current",json!({}),"opened"),
 "opened" if ok=>{s["note"]=reply.clone();s["note"]["text"]=json!(plain(s["note"]["text"].as_str().unwrap_or(""),50000));s["status"]=json!("Ready");},
 "text"=>{s["note"]["text"]=json!(plain(e["value"].as_str().unwrap_or(""),50000));request=effect("records.save",json!({"note":s["note"]}),"saved");},
 "saved" if ok=>{s["note"]=reply.clone();s["status"]=json!("Ready");},
 "paste"=>{s["selection"]=e["selection"].clone();s["selection_end"]=e["selection_end"].clone();request=effect("clipboard.read",json!({}),"pasted");},
 "pasted" if ok=>{let text=s["note"]["text"].as_str().unwrap_or("");let len=text.encode_utf16().count();let x=s["selection"].as_u64().unwrap_or(len as u64) as usize;let y=s["selection_end"].as_u64().unwrap_or(x as u64) as usize;let start=x.min(y).min(len);let end=x.max(y).min(len);let boundary=|at:usize|{let mut units=0;for (b,c) in text.char_indices(){if units>=at{return b}units+=c.len_utf16();}text.len()};let b=boundary(start);let c=boundary(end);let paste=plain(reply.as_str().unwrap_or(""),100000);if len-text[b..c].encode_utf16().count()+paste.encode_utf16().count()>50000{s["status"]=json!("Up to 50,000 Characters. Paste a Smaller Section; Your Current Text Was Kept.");}else{s["note"]["text"]=json!(format!("{}{}{}",&text[..b],paste,&text[c..]));request=effect("records.save",json!({"note":s["note"]}),"saved");}},
 "clear"=>s["clear"]=json!(true),"clear.cancel"=>s["clear"]=json!(false),
 "clear.accept"=>{s["clear"]=json!(false);s["note"]["text"]=json!("");request=effect("records.save",json!({"note":s["note"]}),"saved");},
 "settings"=>s["settings"]=json!(true),"settings.close"=>s["settings"]=json!(false),_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could Not Complete Action"));}
 let text=s["note"]["text"].as_str().unwrap_or("");let mut settings=node("button","settings","Voice and Speed Settings");settings["icon"]=json!("settings");
 let body=if s["note"].is_object(){vec![json!({"kind":"editor","id":"text","value":text,"hint":"Type or Paste Text to Read…","limit":50000,"reject_overflow":true,"speech_highlight":true}),json!({"kind":"speech","id":"reader.playback","value":text}),node("label","status",if s["status"]=="Ready"{""}else{s["status"].as_str().unwrap_or("")})]}else{vec![node("label","opening",s["status"].as_str().unwrap_or("Opening…")),node("button","retry","Retry Open")]};
 let mut f=frame("Read Aloud",s.clone(),vec![settings,node("button","clear","Clear"),node("button","paste","Paste")],body);f["effect"]=request;f["header_actions"]=json!(1);f["counter"]=json!(format!("{} / 50,000",text.encode_utf16().count()));f["menu_open"]=s["settings"].clone();f["menu_title"]=json!("Voice and Speed Settings");f["menu_close"]=json!("settings.close");if s["settings"]==true{f["menu"]=json!([{"kind":"speech_settings","id":"reader.voices"},node("button","settings.close","Done")]);}
 if s["clear"]==true{f["confirm"]=json!({"title":"Clear Text?","text":"Remove the text saved in Read Aloud on this phone.","accept":"clear.accept","accept_label":"Clear","cancel":"clear.cancel"});}f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
