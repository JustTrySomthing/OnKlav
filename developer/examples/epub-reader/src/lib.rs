use onklav_ui::{*,serde_json::{json,Value}};
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"id":null,"reader":null,"open":false,"revision":0,"status":"Loading sample…","staging":null});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let v=&e["reply"]["value"];let mut request=Value::Null;
 match a{
 "start"=>request=collections::get("epub_fixture","book","sample.loaded"),
 "sample.loaded" if ok=>{s["revision"]=v["revision"].clone();s["id"]=v["data"]["id"].clone();s["status"]=json!("Ready");},
 "sample.create"=>request=blobs::begin("Sample.epub","application/epub+zip",false,"sample.created"),
 "sample.created" if ok=>{s["staging"]=v["id"].clone();let bytes=include_bytes!("../../../../docs/fixtures/text-profile.epub");let hex=bytes.iter().map(|b|format!("{b:02x}")).collect::<String>();request=blobs::append(s["staging"].as_str().unwrap(),0,&hex,"sample.appended");},
 "sample.appended" if ok=>request=blobs::finish(s["staging"].as_str().unwrap(),None,"sample.finished"),
 "sample.finished" if ok=>{s["id"]=v["id"].clone();request=collections::transact("epub_fixture",s["revision"].as_u64().unwrap_or(0),1,None,vec![collections::put("book",json!({"id":s["id"]}))],"sample.saved");},
 "sample.saved" if ok=>{s["revision"]=v["revision"].clone();s["status"]=json!("Sample created");},
 "sample.open"=>s["open"]=json!(true),_=>{}
 }
 let mut frame=if s["open"]==true&&s["id"].is_string(){let mut frame=epub_ui::reader("EPUB reader",json!({"state":s["reader"],"event":e}),s["id"].as_str().unwrap());s["reader"]=frame["state"].clone();if frame["close_requested"]==true{s["open"]=json!(false);frame["close_requested"]=json!(false);}frame}else{Screen::new("EPUB reader",s.clone()).body(vec![label("sample.status",s["status"].as_str().unwrap_or("")),button(if s["id"].is_null(){"sample.create"}else{"sample.open"},if s["id"].is_null(){"Create sample book"}else{"Read sample book"})]).finish()};
 frame["state"]=s;if !request.is_null(){frame["effect"]=request;}frame
}
onklav_ui::app!(step);
