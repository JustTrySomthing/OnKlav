use onklav_ui::{*,serde_json::{json,Value}};
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"status":"Synthetic fixtures; no live provider configured","open":false,"map":maps::viewer("map",maps::provider("demo","Synthetic tiles","https://tiles.example.org/{z}/{x}/{y}.png","Demo tiles — not geographic data","https://tiles.example.org/license",false,true),0.,0.,1,vec![],vec![])});}
 let a=input["event"]["action"].as_str().unwrap_or("");let reply=&input["event"]["reply"];let v=&reply["value"];let mut effect=Value::Null;
 let p=maps_geo::Provider::new("https://geo.example.org/search","Demo geocoder — fixture data").unwrap();
 match a{
 "demo.seed"=>{let bytes=include_bytes!("../../../../docs/fixtures/maps/1-0-0.png");let response=json!({"status":200,"hex":bytes.iter().map(|b|format!("{b:02x}")).collect::<String>(),"headers":{"content-type":"image/png","cache-control":"max-age=86400","etag":"\"demo\""}});effect=maps::cache_response(&s["map"],0,0,response,"demo.stored");},
 "demo.stored" if reply["ok"]==true=>{s["map"]["tiles"]=json!([maps::tile(0,0,v["file"].as_str().unwrap())]);s["status"]=json!("Cached fixture");},
 "demo.lookup"=>effect=maps::cached(&s["map"],0,0,false,"demo.looked"),
 "demo.offline"=>effect=maps::cached(&s["map"],0,0,true,"demo.offline.loaded"),
 "demo.looked" if reply["ok"]==true=>s["status"]=json!(if v["hit"]==true{"Cache hit"}else{"Cache miss"}),
 "demo.offline.loaded" if reply["ok"]==true=>s["status"]=json!(if v["hit"]==true{"Offline cached tile"}else{"Offline tile unavailable"}),
 "demo.clear"=>effect=maps::clear_cache("demo.cleared"),
 "demo.cleared" if reply["ok"]==true=>{s["map"]["tiles"]=json!([]);s["status"]=json!("Cache cleared");},
 "demo.places"=>{s["places"]=maps_geo::places(&p,&json!({"status":200,"text":json!([{"lat":"0","lon":"0","display_name":"Demo center — fixture"}]).to_string()})).unwrap();s["status"]=json!("Geocoder fixture parsed");},
 "demo.route"=>{let route=maps_geo::route_result(&p,&json!({"status":200,"text":json!({"code":"Ok","routes":[{"distance":1200,"duration":180,"geometry":{"type":"LineString","coordinates":[[-1.,-1.],[1.,1.]]}}]}).to_string()})).unwrap();s["map"]=maps_geo::with_route(s["map"].clone(),&route).unwrap();s["status"]=json!("Route fixture parsed");},
 "map.open"=>s["open"]=json!(true),"map.back"=>s["open"]=json!(false),
 "provider.search"=>effect=maps_geo::search(&p,"Demo place","provider.searched").unwrap(),
 "provider.searched"=>s["status"]=json!("Live provider is not configured in this fixture"),_=>{}
 }
 if reply["ok"]==false&&a.starts_with("demo."){s["status"]=json!("Cache access off or unavailable");}
 let mut frame=if s["open"]==true{Screen::new("Demo map",s.clone()).body(vec![s["map"].clone()]).back("map.back").finish()}else{let mut body=vec![label("demo.notice","Synthetic map/cache/adapter fixtures; no live geographic data"),label("demo.status",s["status"].as_str().unwrap_or("")),button("demo.seed","Cache demo tile"),button("demo.lookup","Look up cached tile"),button("demo.offline","Read offline cache"),button("demo.places","Parse demo places"),button("demo.route","Parse demo route"),button("map.open","View demo map"),button("demo.clear","Clear tile cache"),button("provider.search","Request configured provider")];if s["places"].is_object(){body.extend(maps_geo::place_rows(&s["places"],"place"));}Screen::new("Map services",s.clone()).body(body).scrollable().finish()};if !effect.is_null(){frame["effect"]=effect;}frame
}
onklav_ui::app!(step);
