//! Reusable private editor and preferences form, including unsaved-change recovery.
use super::*;
pub fn document(title:&str,input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"doc":{"id":"","title":"","text":"","updated":0},"dirty":false,"status":"Opening…","discard":null});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut req=Value::Null;let mut close=false;
 match a{
 "start"=>req=services::current("loaded"),
 "loaded"|"saved" if e["reply"]["ok"]==true=>{s["doc"]=e["reply"]["value"].clone();s["dirty"]=json!(false);s["status"]=json!(if a=="saved"{"Saved"}else{"Ready"});},
 "title"|"body"=>{s["doc"][if a=="body"{"text"}else{"title"}]=json!(plain(e["value"].as_str().unwrap_or(""),if a=="body"{100000}else{80}));s["dirty"]=json!(true);s["status"]=json!("Unsaved changes");},
 "save"=>req=services::save(&s["doc"],"saved"),
 "new"|"reload"|"app.back"=>{if s["dirty"]==true{s["discard"]=json!(a)}else if a=="app.back"{close=true}else{req=if a=="new"{services::new_document("loaded")}else{services::current("loaded")};}},
 "discard.cancel"=>s["discard"]=Value::Null,
 "discard.accept"=>{let target=s["discard"].as_str().unwrap_or("");if target=="app.back"{close=true}else{req=if target=="new"{services::new_document("loaded")}else{services::current("loaded")};}s["discard"]=Value::Null;},_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=e["reply"]["error"].clone();}
 let mut f=Screen::new(title,s.clone()).actions(vec![icon("new","New","add"),icon("reload","Reload","refresh")]).body(vec![field("title","Title",s["doc"]["title"].as_str().unwrap_or("")),editor("body","Write something…",s["doc"]["text"].as_str().unwrap_or("")),label("status",s["status"].as_str().unwrap_or("")),primary_button("save","Save")]).effect(req).back("app.back");
 if s["discard"].is_string(){f=f.confirm("Discard changes?","Your saved document will be kept.","discard.accept","discard.cancel");}if close{f=f.close()}f.finish()
}
pub fn settings(title:&str,input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"doc":{},"preferences":{"show_details":true,"order":"newest"},"dirty":false,"status":"Opening…","discard":false});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut req=Value::Null;let mut close=false;
 match a{
 "start"=>req=services::current("loaded"),
 "loaded" if e["reply"]["ok"]==true=>{s["doc"]=e["reply"]["value"].clone();if let Ok(v)=serde_json::from_str::<Value>(s["doc"]["text"].as_str().unwrap_or("")){if v.is_object(){s["preferences"]=v;}}s["status"]=json!("Ready");s["dirty"]=json!(false);},
 "show_details"=>{s["preferences"][a]=json!(e["value"]=="true");s["dirty"]=json!(true);s["status"]=json!("Unsaved changes");},
 "order"=>{if e["value"]=="newest"||e["value"]=="oldest"{s["preferences"][a]=e["value"].clone();s["dirty"]=json!(true);s["status"]=json!("Unsaved changes");}},
 "save"=>{let mut doc=s["doc"].clone();doc["title"]=json!("Preferences");doc["text"]=json!(s["preferences"].to_string());req=services::save(&doc,"saved");},
 "saved" if e["reply"]["ok"]==true=>{s["doc"]=e["reply"]["value"].clone();s["dirty"]=json!(false);s["status"]=json!("Saved");},
 "app.back"=>if s["dirty"]==true{s["discard"]=json!(true)}else{close=true},
 "discard.cancel"=>s["discard"]=json!(false),"discard.accept"=>close=true,_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=e["reply"]["error"].clone();}
 let p=&s["preferences"];let mut f=Screen::new(title,s.clone()).body(vec![toggle("show_details","Show details",p["show_details"]==true),choices("select","order","Order",p["order"].as_str().unwrap_or("newest"),&[("newest","Newest first"),("oldest","Oldest first")]),label("status",s["status"].as_str().unwrap_or("")),primary_button("save","Save")]).effect(req).back("app.back");if s["discard"]==true{f=f.confirm("Discard changes?","Unsaved preferences will be lost.","discard.accept","discard.cancel");}if close{f=f.close()}f.finish()
}
