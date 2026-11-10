//! Paginated document list/detail controller with draft and deletion recovery.
use crate::*;
use serde_json::{json,Value};
pub fn documents(title:&str,input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"route":"list","query":"","offset":0,"items":[],"total":0,"doc":{},"dirty":false,"status":"Opening…","confirm":null});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut req=Value::Null;let mut close=false;
 match a{
 "start"|"refresh"=>req=services::page(s["query"].as_str().unwrap_or(""),s["offset"].as_u64().unwrap_or(0)as usize,"listed"),
 "listed" if e["reply"]["ok"]==true=>{for k in ["items","total","offset"]{s[k]=e["reply"]["value"][k].clone();}s["route"]=json!("list");s["dirty"]=json!(false);s["status"]=json!("Ready");},
 "query"=>{s["query"]=json!(plain(e["value"].as_str().unwrap_or(""),80));s["offset"]=json!(0);req=services::page(s["query"].as_str().unwrap_or(""),0,"listed");},
 "next"|"previous"=>{let n=s["offset"].as_u64().unwrap_or(0)as usize;req=services::page(s["query"].as_str().unwrap_or(""),if a=="next"{n+40}else{n.saturating_sub(40)},"listed");},
 "new"=>req=services::new_document("opened"),
 _ if a.starts_with("open.")=>req=services::load(&a[5..],"opened"),
 "opened"|"saved" if e["reply"]["ok"]==true=>{s["doc"]=e["reply"]["value"].clone();s["route"]=json!("edit");s["dirty"]=json!(false);s["status"]=json!(if a=="saved"{"Saved"}else{"Ready"});},
 "title"|"body"=>{s["doc"][if a=="title"{"title"}else{"text"}]=json!(plain(e["value"].as_str().unwrap_or(""),if a=="title"{80}else{100000}));s["dirty"]=json!(true);s["status"]=json!("Unsaved changes");},
 "save"=>req=services::save(&s["doc"],"saved"),
 "delete"=>s["confirm"]=json!("delete"),"confirm.cancel"=>s["confirm"]=Value::Null,
 "confirm.accept"=>{if s["confirm"]=="delete"{req=services::delete(s["doc"]["id"].as_str().unwrap_or(""),"deleted")}else{req=services::page(s["query"].as_str().unwrap_or(""),0,"listed")}s["confirm"]=Value::Null;},
 "deleted" if e["reply"]["ok"]==true=>req=services::page(s["query"].as_str().unwrap_or(""),0,"listed"),
 "app.back"=>if s["route"]=="edit"{if s["dirty"]==true{s["confirm"]=json!("back")}else{req=services::page(s["query"].as_str().unwrap_or(""),s["offset"].as_u64().unwrap_or(0)as usize,"listed")}}else{close=true},_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=e["reply"]["error"].clone();}
 let mut f=if s["route"]=="edit"{
  let mut nodes=vec![field("title","Title",s["doc"]["title"].as_str().unwrap_or("")),editor("body","Details",s["doc"]["text"].as_str().unwrap_or("")),label("status",s["status"].as_str().unwrap_or("")),primary_button("save","Save")];
  if s["doc"]["updated"].as_u64().unwrap_or(0)>0{nodes.push(button("delete","Delete document"));}Screen::new(title,s.clone()).body(nodes)
 }else{
  let mut nodes=vec![field("query","Search documents",s["query"].as_str().unwrap_or(""))];
  for item in s["items"].as_array().into_iter().flatten().take(40){nodes.push(record(&format!("open.{}",item["id"].as_str().unwrap_or("")),item["title"].as_str().unwrap_or("Untitled"),item["preview"].as_str().unwrap_or("")));}
  if s["items"].as_array().is_none_or(|v|v.is_empty()){nodes.push(label("empty","No documents yet. Choose New to get started."));}
  let n=s["offset"].as_u64().unwrap_or(0);if n>0{nodes.push(button("previous","Previous page"));}if n+40<s["total"].as_u64().unwrap_or(0){nodes.push(button("next","Next page"));}
  nodes.push(label("status",s["status"].as_str().unwrap_or("")));
  Screen::new(title,s.clone()).body(nodes).actions(vec![icon("new","New document","add"),icon("refresh","Refresh","refresh")]).scroll_key(&format!("list:{}:{n}",s["query"].as_str().unwrap_or("")))
 };
 f=f.effect(req).back("app.back");if s["confirm"].is_string(){let delete=s["confirm"]=="delete";f=f.confirm_labeled(if delete{"Delete document?"}else{"Discard changes?"},if delete{"Remove this app's private document?"}else{"Your saved document will be kept."},"confirm.accept","confirm.cancel",if delete{"Delete"}else{"Discard"});}if close{f=f.close()}f.finish()
}
