//! Annotation's controller. Native services provide images and rendering, never app routing.
use crate::{effect,frame,node,plain,serde_json::{json,Value}};
fn button(id:&str,text:&str)->Value{node("button",id,text)}
fn query(s:&Value)->Value{effect("drawing.list",json!({"query":s["query"],"offset":s["page"].as_u64().unwrap_or(0)*40}),"listed")}
fn step(input:Value)->Value {
 let mut s=input["state"].clone();if !s.is_object(){s=json!({"doc":null,"tool":"move","color":4293476930u32,"width":4,"fit":0,"menu":"","drawer":false,"query":"","page":0,"list":{"items":[],"total":0},"note":null,"confirm":null,"status":"Open a Picture · Two Fingers to Zoom"});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let value=e["value"].as_str().unwrap_or("");let ok=e["reply"]["ok"]==true;let r=&e["reply"]["value"];let mut request=Value::Null;
 let args=||json!({"id":s["doc"]["id"],"revision":s["doc"]["revision"]});
 match a {
 "start"=>request=effect("drawing.current",json!({}),"opened"),
 "opened"|"imported" if ok=>{s["doc"]=r.clone();s["drawer"]=json!(false);s["menu"]=json!("");s["status"]=json!(if r.is_null(){"Open a Picture · Two Fingers to Zoom"}else{"Saved · Two Fingers to Zoom"});},
 "image.open"|"library.new"=>request=effect("images.import",json!({}),"imported"),
 "image.title"|"rename.title"=>{let mut ar=args();ar["title"]=json!(plain(value,80));request=effect("drawing.rename",ar,"saved");},
 "saved" if ok=>{s["doc"]=r.clone();s["status"]=json!("Saved");},
 "canvas.changed"=>{let v=&e["value"];if v["op"]=="text"{s["note"]=v.clone();s["menu"]=json!("text");}else{s["doc"]=v["doc"].clone();s["status"]=json!("Saved");}},
 "canvas.undo"|"canvas.redo"=>request=effect(if a=="canvas.undo"{"drawing.undo"}else{"drawing.redo"},args(),"saved"),
 "canvas.tools"|"settings.tools"=>s["menu"]=json!("tools"),
 "tools.mode"=>{if ["move","pen","highlight","text"].contains(&value){s["tool"]=json!(value);s["menu"]=json!("");}},
 "tools.erase"=>{s["tool"]=json!("erase");s["menu"]=json!("");},
 "tools.color"=>if let Ok(c)=value.parse::<u32>(){if [0xffffdd41u32,0xff36bd7a,0xffe94242,0xff000000,0xffffffff].contains(&c){s["color"]=json!(c);}},
 "tools.width"=>if let Ok(w)=value.parse::<u32>(){s["width"]=json!(w.clamp(1,12));},
 "note.text"=>s["note"]["mark"]["text"]=json!(plain(value,400)),
 "note.accept"=>{if !s["note"]["mark"]["text"].as_str().unwrap_or("").trim().is_empty(){let mut ar=args();ar["mark"]=s["note"]["mark"].clone();if s["note"]["index"].is_u64(){ar["index"]=s["note"]["index"].clone();}request=effect("images.text",ar,"text.saved");}},
 "text.saved" if ok=>{s["doc"]=r.clone();s["tool"]=json!("move");s["note"]=Value::Null;s["menu"]=json!("");s["status"]=json!("Saved");},
 "note.delete"=>{let mut ar=args();ar["op"]=json!("erase");ar["index"]=s["note"]["index"].clone();request=effect("drawing.edit",ar,"text.saved");},
 "library"=>{s["drawer"]=json!(true);s["page"]=json!(0);request=query(&s);},
 "library.query"=>{s["query"]=json!(plain(value,80));s["page"]=json!(0);request=query(&s);},
 "library.more"=>{s["page"]=json!(s["page"].as_u64().unwrap_or(0)+1);request=query(&s);},
 "library.previous"=>{s["page"]=json!(s["page"].as_u64().unwrap_or(0).saturating_sub(1));request=query(&s);},
 "listed" if ok=>s["list"]=r.clone(),"drawer.close"=>s["drawer"]=json!(false),
 "export"=>s["menu"]=json!("export"),"settings"=>s["menu"]=json!("settings"),"about"=>s["menu"]=json!("about"),
 "rename"=>s["menu"]=json!("rename"),"menu.close"=>{s["menu"]=json!("");s["note"]=Value::Null;},
 "export.files"|"export.share"|"export.copy"=>{let mut ar=args();ar["mode"]=json!(&a[7..]);request=effect("images.export",ar,"exported");},
 "exported" if ok=>{s["menu"]=json!("");s["status"]=json!("Done");},
 "delete.cancel"=>s["confirm"]=Value::Null,
 "delete.accept"=>request=effect("drawing.delete",json!({"id":s["confirm"]["id"]}),"deleted"),
 "deleted" if ok=>{if s["confirm"]["id"]==s["doc"]["id"]{s["doc"]=Value::Null;s["status"]=json!("Open a Picture to Start");}s["confirm"]=Value::Null;request=query(&s);},
 _ if a.starts_with("open:")=>request=effect("drawing.load",json!({"id":&a[5..]}),"opened"),
 _ if a.starts_with("delete:")=>s["confirm"]=json!({"id":&a[7..]}),_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=json!(e["reply"]["error"].as_str().unwrap_or("Could Not Complete"));}
 let mut actions=vec![button("export","Export Image"),button("settings","Annotation Settings"),button("library","Saved Images")];for (n,i) in actions.iter_mut().zip(["download","settings","folder"]){n["icon"]=json!(i);}
 actions.rotate_right(1); let mut body=vec![json!({"kind":"field","id":"image.title","hint":"Image Title","value":s["doc"]["title"].as_str().unwrap_or(""),"limit":80,"font":17,"row":"image.top","enabled":s["doc"].is_object()}),json!({"kind":"button","id":"image.open","text":"Open","row":"image.top","compact":true})];
 body.push(json!({"kind":"canvas","id":"image.canvas","document":s["doc"],"tool":s["tool"],"color":s["color"],"width":s["width"],"on_change":"canvas.changed","undo_action":"canvas.undo","redo_action":"canvas.redo","tools_action":"canvas.tools"}));
 body.push(node("label","status",&format!("{} · {} Marks",s["status"].as_str().unwrap_or(""),s["doc"]["count"].as_u64().unwrap_or(0))));
 let mode=s["menu"].as_str().unwrap_or("");let mut menu=match mode {
 "tools"=>vec![json!({"kind":"chips","id":"tools.mode","value":s["tool"],"options":[{"value":"move","text":"Move"},{"value":"pen","text":"Pen"},{"value":"highlight","text":"Highlight"},{"value":"text","text":"Aa"}]}),json!({"kind":"palette","id":"tools.color","value":s["color"]}),button("tools.erase","Erase a Mark"),json!({"kind":"slider","id":"tools.width","text":"Stroke Width","value":s["width"]}),button("menu.close","Done")],
 "text"=>{let mut nodes=vec![json!({"kind":"editor","id":"note.text","value":s["note"]["mark"]["text"],"limit":400,"hint":"Text Note"}),button("note.accept",if s["note"]["index"].is_u64(){"Save"}else{"Add"})];if s["note"]["index"].is_u64(){nodes.push(button("note.delete","Delete"));}nodes.push(button("menu.close","Cancel"));nodes},
 "rename"=>if s["doc"].is_object(){vec![json!({"kind":"field","id":"rename.title","value":s["doc"]["title"],"limit":80,"hint":"Image Title"}),button("menu.close","Done")]}else{vec![node("label","rename.empty","Open a Picture First"),button("menu.close","Close")]},
 "export"=>vec![button("export.files","Save to Files"),button("export.share","Share"),button("export.copy","Copy"),button("menu.close","Cancel")],
 "settings"=>vec![button("settings.tools","Tools"),button("rename","Rename Image"),button("about","About Saved Images"),button("menu.close","Close")],
 "about"=>vec![node("label","about.text","Saved privately on this phone. Use Download to save a copy in Files, or Share to send it."),button("menu.close","Done")],_=>vec![]};
 if !mode.is_empty()&&e["reply"]["ok"]==false{menu.insert(0,node("label","menu.error",s["status"].as_str().unwrap_or("")));}
 let mut drawer=vec![button("library.new","+ New"),json!({"kind":"field","id":"library.query","value":s["query"],"hint":"Search Saved Items","limit":80})];
 for item in s["list"]["items"].as_array().into_iter().flatten(){let id=item["id"].as_str().unwrap();drawer.push(json!({"kind":"image_record","id":format!("open:{id}"),"document":id,"revision":item["revision"],"text":item["title"],"time":item["updated"],"delete":format!("delete:{id}")}));}
 if s["list"]["total"]==0{drawer.push(node("label","library.empty",if s["query"]==""{"No Saved Items Yet"}else{"No Matching Items"}));}if s["page"].as_u64().unwrap_or(0)>0{drawer.push(button("library.previous","Previous Images"));}if (s["page"].as_u64().unwrap_or(0)+1)*40<s["list"]["total"].as_u64().unwrap_or(0){drawer.push(button("library.more","More Images"));}
 let mut f=frame("Annotation",s.clone(),actions,body);f["effect"]=request;f["header_actions"]=json!(3);f["revision_key"]=s["doc"]["id"].clone();f["drawer_open"]=s["drawer"].clone();f["drawer_title"]=json!("Saved Images");f["drawer_close"]=json!("drawer.close");f["drawer"]=json!(drawer);f["menu_open"]=json!(!mode.is_empty());f["menu_title"]=json!(match mode{"tools"=>"Tools","text"=>"Text Note","export"=>"Export","rename"=>"Rename Image",_=>"Annotation"});f["menu_close"]=json!("menu.close");f["menu"]=json!(menu);f["menu_bottom"]=json!(mode=="tools");if s["confirm"].is_object(){f["confirm"]=json!({"title":"Delete Saved Item?","text":"This removes the saved image and its marks from OnKlav.","accept":"delete.accept","cancel":"delete.cancel"});}f
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
