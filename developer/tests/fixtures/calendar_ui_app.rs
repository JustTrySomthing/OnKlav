//! Disposable component/broker fixture, not a bundled reference application.
use onklav_ui::{calendar,calendar_ui,collections,label,serde_json::{json,Value}};
fn step(input:Value)->Value{
 let mut s=input["state"].clone();if !s.is_object(){let events=calendar::import_events("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:OnKlav fixture\r\nBEGIN:VEVENT\r\nUID:fixture@example.org\r\nDTSTAMP:20261025T000000Z\r\nDTSTART;VALUE=DATE:20261025\r\nDTEND;VALUE=DATE:20261026\r\nSUMMARY:Calendar sample\r\nDESCRIPTION:Original details\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n").unwrap();s=json!({"route":"agenda","events":events,"edit":null,"revision":0,"status":"","offset":0,"saving":null});}
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");let mut request=Value::Null;
 match a{
 "start"|"calendar.refresh"=>request=collections::get("calendar_fixture","event","loaded"),
 "loaded" if e["reply"]["ok"]==true=>{let v=&e["reply"]["value"];s["revision"]=v["revision"].clone();if v["data"].is_object(){s["events"]=json!([v["data"].clone()]);}s["route"]=json!("agenda");s["status"]=json!("");},
 "calendar.open.0" if s["route"]=="agenda"=>{s["route"]=json!("edit");s["edit"]=Value::Null;},
 "saved" if e["reply"]["ok"]==true=>{s["revision"]=e["reply"]["value"]["revision"].clone();s["events"]=json!([s["saving"].clone()]);s["saving"]=Value::Null;s["route"]=json!("agenda");s["status"]=json!("Saved");},_=>{}
 }
 if e["reply"]["ok"]==false{s["status"]=e["reply"]["error"].clone();}
 let mut frame=if s["route"]=="edit"{
  let f=calendar_ui::event_editor("Calendar fixture",json!({"state":s["edit"],"event":input["event"]}),&s["events"][0]);s["edit"]=f["state"].clone();
  match f["state"]["outcome"]["kind"].as_str(){Some("save")=>{s["saving"]=f["state"]["outcome"]["event"].clone();request=collections::transact("calendar_fixture",s["revision"].as_u64().unwrap_or(0),1,None,vec![collections::put("event",s["saving"].clone())],"saved");},Some("cancel")=>{s["route"]=json!("agenda");s["edit"]=Value::Null;},_=>{}}f
 }else{calendar_ui::agenda("Calendar fixture",s["events"].as_array().unwrap(),0).unwrap()};
 if s["route"]=="agenda"{frame=calendar_ui::agenda("Calendar fixture",s["events"].as_array().unwrap(),0).unwrap();frame["actions"]=json!([]);}
 frame["body"].as_array_mut().unwrap().push(label("fixture.status",s["status"].as_str().unwrap_or("")));frame["state"]=s;frame["effect"]=request;frame
}
onklav_ui::app!(step);
