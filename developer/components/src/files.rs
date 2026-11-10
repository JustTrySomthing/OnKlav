//! One shared opaque-ID file chooser and the host's existing media viewer.
use super::*;
pub fn manager(title:&str,input:Value)->Value{reference_apps::files::controller(title,input)}
pub fn open(id:&str,then:&str)->Value{effect("library.open",json!({"id":id}),then)}
/// Read selected UTF-8 text through opaque identity. Requires files.manage and a tap.
pub fn read_text(id:&str,then:&str)->Value{effect("library.read_text",json!({"id":id}),then)}
/// Normalize line separators for editing without changing the original snapshot.
pub fn text_for_editor(file:&Value)->String{file["text"].as_str().unwrap_or("").replace("\r\n","\n").replace('\r',"\n")}
/// Saves only the revision that was read. Preserves a uniform line style and UTF-8 BOM.
/// A conflict returns an error instead of silently overwriting a newer edit.
pub fn write_text(file:&Value,text:&str,then:&str)->Value{
 let content=match file["line_ending"].as_str(){Some("crlf")=>text.replace("\r\n","\n").replace('\r',"\n").replace('\n',"\r\n"),Some("cr")=>text.replace("\r\n","\n").replace('\n',"\r"),_=>text.into()};
 effect("library.write_text",json!({"id":file["id"],"revision":file["revision"],"utf8_bom":file["utf8_bom"]==true,"text":content}),then)
}
/// Opens Android's file selection through the host; the app never receives a URI.
/// An empty destination uses Downloads. Selection/copy limits remain host enforced.
pub fn import_files(folder:&str,then:&str)->Value{effect("library.import",json!({"folder":if folder.is_empty(){"downloads"}else{folder}}),then)}
#[derive(Clone,Copy)]enum Selection{Metadata,Viewer,Text}
pub fn with_picker(title:&str,input:Value,app:fn(Value)->Value)->Value{browser(title,input,app,Selection::Metadata)}
pub fn with_viewer(title:&str,input:Value,app:fn(Value)->Value)->Value{browser(title,input,app,Selection::Viewer)}
/// Shared chooser + bounded UTF-8 read. App receives file.text.loaded with the
/// snapshot in event.value, or file.text.error with event.reply. Binary files,
/// cancellation and failed reads never replace the app's previous document.
pub fn with_text_picker(title:&str,input:Value,app:fn(Value)->Value)->Value{browser(title,input,app,Selection::Text)}
fn browser(title:&str,input:Value,app:fn(Value)->Value,selection:Selection)->Value{
 let mut root=input["state"].clone();if root["sdk.files.wrapper"]!=true{root=json!({"sdk.files.wrapper":true,"app":null,"picker":null});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut ev=e.clone();let mut req=Value::Null;let mut selected=Value::Null;
 if a=="sdk.files.open"{root["picker"]=json!({"folder":"","parent":"","query":"","offset":0,"total":0,"items":[],"title":"Files","status":""});}
 if root["picker"].is_object(){let p=&mut root["picker"];match a{
 "sdk.files.open"|"sdk.files.refresh"=>req=effect("library.list",json!({"folder":p["folder"],"query":p["query"],"offset":p["offset"]}),"sdk.files.listed"),
 "sdk.files.import"=>{let folder=p["folder"].as_str().filter(|v|!v.is_empty()).unwrap_or("downloads").to_string();p["import_folder"]=json!(folder);req=import_files(&folder,"sdk.files.imported");},
 "sdk.files.imported" if e["reply"]["ok"]==true=>{p["folder"]=p["import_folder"].clone();p["query"]=json!("");p["offset"]=json!(0);req=effect("library.list",json!({"folder":p["folder"]}),"sdk.files.listed");},
 "sdk.files.listed" if e["reply"]["ok"]==true=>{for key in ["items","total","parent","title","folder"]{p[key]=e["reply"]["value"][key].clone();}p["status"]=json!("");},
 "sdk.files.query"=>{p["query"]=json!(plain(e["value"].as_str().unwrap_or(""),80));p["offset"]=json!(0);req=effect("library.list",json!({"folder":p["folder"],"query":p["query"]}),"sdk.files.listed");},
 "sdk.files.up"=>{p["folder"]=p["parent"].clone();p["offset"]=json!(0);p["query"]=json!("");req=effect("library.list",json!({"folder":p["folder"]}),"sdk.files.listed");},
 "sdk.files.next"|"sdk.files.previous"=>{let n=p["offset"].as_u64().unwrap_or(0);p["offset"]=json!(if a=="sdk.files.next"{n+40}else{n.saturating_sub(40)});req=effect("library.list",json!({"folder":p["folder"],"query":p["query"],"offset":p["offset"]}),"sdk.files.listed");},
 _ if a.starts_with("sdk.files.entry.")=>{if let Some(item)=p["items"].as_array().and_then(|rows|rows.iter().find(|r|r["id"]==&a[16..])).cloned(){if item["folder"]==true{p["folder"]=item["id"].clone();p["query"]=json!("");p["offset"]=json!(0);req=effect("library.list",json!({"folder":p["folder"]}),"sdk.files.listed");}else{selected=item;}}},_=>{}
 }if e["reply"]["ok"]==false{if a!="sdk.files.imported"{p["items"]=json!([]);}p["status"]=e["reply"]["error"].clone();}}
 if a=="sdk.files.cancel"||selected.is_object(){root["picker"]=Value::Null;}
 if selected.is_object(){match selection{
 Selection::Metadata=>ev=json!({"action":"file.selected","value":selected}),
 Selection::Viewer|Selection::Text=>{root["selected"]=selected.clone();let id=selected["id"].as_str().unwrap_or("");req=if matches!(selection,Selection::Text){read_text(id,"sdk.files.text.read")}else{open(id,"sdk.files.opened")};ev=json!({"action":"sdk.render"});}
 }}
 else if a=="sdk.files.text.read"{
  if e["reply"]["ok"]==true{ev=json!({"action":"file.text.loaded","value":e["reply"]["value"]});root["file_error"]=Value::Null;}
  else{ev=json!({"action":"file.text.error","value":root["selected"],"reply":e["reply"]});if !e["reply"]["access"].is_object(){root["file_error"]=e["reply"]["error"].clone();}}
  root["selected"]=Value::Null;
 }
 else if a=="sdk.files.opened"{ev=json!({"action":"file.opened","value":root["selected"],"reply":e["reply"]});root["selected"]=Value::Null;if e["reply"]["ok"]==false{root["file_error"]=e["reply"]["error"].clone();}}
 else if a.starts_with("sdk.files."){ev=json!({"action":"sdk.render"});}
 let mut f=app(json!({"state":root["app"],"event":ev}));root["app"]=f["state"].clone();let mut actions=f["actions"].as_array().cloned().unwrap_or_default();actions.insert(0,icon("sdk.files.open","Open Files","folder"));f["actions"]=json!(actions);
 if root["picker"].is_object(){let p=&root["picker"];let mut rows=vec![field("sdk.files.query","Search Files",p["query"].as_str().unwrap_or("")),label("sdk.files.location",p["title"].as_str().unwrap_or("Files"))];if p["folder"]!=""{rows.push(button("sdk.files.up","Parent Folder"));}rows.push(button("sdk.files.refresh","Refresh"));rows.push(button("sdk.files.import","Import from Android"));
 if let Some(items)=p["items"].as_array(){for item in items.iter().filter(|i|i["id"]!="system"&&i["id"]!="trash"&&i["id"]!="external"&&(i["folder"]==true||i["protected"]!=true)).take(40){let mut n=record(&format!("sdk.files.entry.{}",item["id"].as_str().unwrap_or("")),item["name"].as_str().unwrap_or("File"),item["detail"].as_str().unwrap_or(""));n["leading_icon"]=json!(if item["folder"]==true{"folder"}else{"file"});rows.push(n);}}
 if p["offset"].as_u64().unwrap_or(0)>0{rows.push(button("sdk.files.previous","Previous Files"));}if p["offset"].as_u64().unwrap_or(0)+40<p["total"].as_u64().unwrap_or(0){rows.push(button("sdk.files.next","More Files"));}if let Some(status)=p["status"].as_str().filter(|s|!s.is_empty()){rows.push(label("sdk.files.status",status));}
 f["body"]=json!([]);f["actions"]=json!([]);f["menu"]=json!([]);f["menu_open"]=json!(false);f["confirm"]=Value::Null;f["drawer_open"]=json!(true);f["drawer_title"]=json!("Files");f["drawer_close"]=json!("sdk.files.cancel");f["drawer"]=json!(rows);
 }
 if !req.is_null(){f["effect"]=req;}if a=="sdk.files.error.close"{root["file_error"]=Value::Null;}if let Some(error)=root["file_error"].as_str(){f["menu_open"]=json!(true);f["menu_title"]=json!("Could not open file");f["menu_close"]=json!("sdk.files.error.close");f["menu"]=json!([label("sdk.files.error",error),button("sdk.files.error.close","Close")]);}
 f["state"]=root;f["title"]=json!(title);f
}

#[cfg(test)]mod tests{
 use super::*;
 fn app(input:Value)->Value{let mut state=input["state"].clone();if !state.is_object(){state=json!({"text":"previous text"})}if input["event"]["action"]=="file.text.loaded"{state["text"]=input["event"]["value"]["text"].clone();}Screen::new("Test",state).finish()}
 fn ready()->Value{json!({"sdk.files.wrapper":true,"app":{"text":"previous text"},"picker":{"folder":"downloads","items":[{"id":"opaque","name":"Text.txt","folder":false}]}})}
 #[test]fn selected_text_is_read_once_then_delivered_as_a_snapshot(){let selected=with_text_picker("Test",json!({"state":ready(),"event":{"action":"sdk.files.entry.opaque"}}),app);assert_eq!(selected["effect"]["service"],"library.read_text");assert_eq!(selected["effect"]["args"],json!({"id":"opaque"}));assert_eq!(selected["state"]["app"]["text"],"previous text");let loaded=with_text_picker("Test",json!({"state":selected["state"],"event":{"action":"sdk.files.text.read","reply":{"ok":true,"value":{"id":"opaque","text":"loaded","revision":"revision"}}}}),app);assert_eq!(loaded["state"]["app"]["text"],"loaded");assert!(loaded["effect"].is_null());assert!(loaded["state"]["selected"].is_null());}
 #[test]fn failed_text_reads_keep_the_previous_app_data_and_offer_a_shared_dialog(){let failed=with_text_picker("Test",json!({"state":ready(),"event":{"action":"sdk.files.text.read","reply":{"ok":false,"error":"Choose a UTF-8 text file"}}}),app);assert_eq!(failed["state"]["app"]["text"],"previous text");assert_eq!(failed["menu_open"],true);let dismissed=with_text_picker("Test",json!({"state":failed["state"],"event":{"action":"sdk.files.error.close"}}),app);assert_ne!(dismissed["menu_open"],true);assert_eq!(dismissed["state"]["app"]["text"],"previous text");}
 #[test]fn import_uses_an_opaque_folder_and_cancellation_keeps_the_listing(){assert_eq!(import_files("","imported")["args"],json!({"folder":"downloads"}));let importing=with_picker("Test",json!({"state":ready(),"event":{"action":"sdk.files.import"}}),app);assert_eq!(importing["effect"]["service"],"library.import");let cancelled=with_picker("Test",json!({"state":importing["state"],"event":{"action":"sdk.files.imported","reply":{"ok":false,"error":"No files were selected"}}}),app);assert_eq!(cancelled["state"]["picker"]["items"].as_array().unwrap().len(),1);assert_eq!(cancelled["state"]["app"]["text"],"previous text");assert!(cancelled["effect"].is_null());}
 #[test]fn completed_import_refreshes_the_destination_without_reading_automatically(){let mut state=ready();state["picker"]["folder"]=json!("");let importing=with_text_picker("Test",json!({"state":state,"event":{"action":"sdk.files.import"}}),app);let imported=with_text_picker("Test",json!({"state":importing["state"],"event":{"action":"sdk.files.imported","reply":{"ok":true,"value":{"imported":1}}}}),app);assert_eq!(imported["effect"]["service"],"library.list");assert_eq!(imported["effect"]["args"]["folder"],"downloads");assert_eq!(imported["state"]["app"]["text"],"previous text");}
}
