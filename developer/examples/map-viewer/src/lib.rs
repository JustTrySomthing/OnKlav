use onklav_ui::{*,serde_json::{json,Value}};
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"tiles":[],"open":false,"revision":0,"status":"Loading sample…","at":0});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let v=&e["reply"]["value"];let mut request=Value::Null;
 match a{
 "start"=>request=collections::get("maps_fixture","tiles","sample.loaded"),
 "sample.loaded" if ok=>{s["revision"]=v["revision"].clone();s["tiles"]=v["data"]["tiles"].as_array().map(|a|json!(a)).unwrap_or(json!([]));s["status"]=json!("Ready");},
 "sample.create" if s["tiles"].as_array().unwrap().len()<4=>{s["at"]=json!(s["tiles"].as_array().unwrap().len());request=blobs::begin("Demo tile.png","image/png",false,"tile.created");},
 "tile.created" if ok=>{s["staging"]=v["id"].clone();let sources:[&[u8];4]=[include_bytes!("../../../../docs/fixtures/maps/1-0-0.png"),include_bytes!("../../../../docs/fixtures/maps/1-0-1.png"),include_bytes!("../../../../docs/fixtures/maps/1-1-0.png"),include_bytes!("../../../../docs/fixtures/maps/1-1-1.png")];request=blobs::append_bytes(s["staging"].as_str().unwrap(),0,sources[s["at"].as_u64().unwrap()as usize],"tile.appended").unwrap();},
 "tile.appended" if ok=>request=blobs::finish(s["staging"].as_str().unwrap(),None,"tile.finished"),
 "tile.finished" if ok=>{let at=s["at"].as_u64().unwrap()as u32;s["tiles"].as_array_mut().unwrap().push(maps::tile(at/2,at%2,v["id"].as_str().unwrap()));s["at"]=json!(at+1);request=collections::transact("maps_fixture",s["revision"].as_u64().unwrap_or(0),1,None,vec![collections::put("tiles",json!({"tiles":s["tiles"]}))],"sample.saved");},
 "sample.saved" if ok=>{s["revision"]=v["revision"].clone();s["status"]=json!(if s["tiles"].as_array().unwrap().len()==4{"Sample created".to_string()}else{format!("Tile {} of4 created",s["tiles"].as_array().unwrap().len())});},
 "sample.open"=>s["open"]=json!(true),"map.back"=>s["open"]=json!(false),_=>{}
 }
 if e.get("reply").is_some()&&!ok{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Sample action failed"));}
 let mut frame=if s["open"]==true{let provider=maps::provider("demo","Synthetic sample","https://example.org/{z}/{x}/{y}.png","OnKlav synthetic fixture — not geographic data","https://example.org/license",false,true);Screen::new("Map viewer",s.clone()).body(vec![maps::viewer("sample.map",provider,0.,0.,1,s["tiles"].as_array().unwrap().clone(),vec![maps::marker(0.,0.,"Sample center")])]).back("map.back").finish()}else{Screen::new("Map viewer",s.clone()).body(vec![label("sample.status",s["status"].as_str().unwrap_or("")),button(if s["tiles"].as_array().unwrap().len()<4{"sample.create"}else{"sample.open"},if s["tiles"].as_array().unwrap().len()<4{"Create synthetic tiles"}else{"View demo map"})]).finish()};if !request.is_null(){frame["effect"]=request;}frame
}
onklav_ui::app!(step);
