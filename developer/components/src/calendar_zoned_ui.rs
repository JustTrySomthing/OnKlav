//! Named-zone editor. Android's timezone database resolves civil times. UTC event
//! storage remains compatible; the parent can store the chosen zone separately.
use crate::*;
fn iso(stamp:&str)->String{format!("{}-{}-{}T{}:{}:{}",&stamp[0..4],&stamp[4..6],&stamp[6..8],&stamp[9..11],&stamp[11..13],&stamp[13..15])}
pub fn event_editor(title:&str,input:Value,initial:&Value,timezone:&str)->Value{
 if initial["all_day"]==true||crate::calendar::validate_event(initial).is_err(){return crate::calendar_ui::event_editor(title,input,initial)}
 let mut s=input["state"].clone();let action=input["event"]["action"].as_str().unwrap_or("");
 if s["format"]!="onklav.calendar.zoned.editor.v1"||s["original"]!=*initial{
  s=json!({"format":"onklav.calendar.zoned.editor.v1","original":initial,"timezone":timezone,"gap":"reject","overlap":"earlier","draft":null,"local":null,"phase":"localize","error":"","outcome":null});
  return Screen::new(title,s).body(vec![label("calendar.loading","Loading local event times…")]).effect(effect("calendar.localize",json!({"start":initial["start"],"end":initial["end"],"timezone":timezone}),"calendar.localized")).finish()
 }
 s["outcome"]=Value::Null;
 if action=="calendar.localized"{
  if input["event"]["reply"]["ok"]==true{let v=&input["event"]["reply"]["value"];let mut local=initial.clone();local["start"]=v["start"].clone();local["end"]=v["end"].clone();if crate::calendar::validate_event(&local).is_ok(){s["local"]=local;s["phase"]=json!("edit")}else{s["error"]=json!("Invalid local calendar response")}}
  else{s["error"]=json!("Could not load local times. Cancel and choose a valid timezone.")}
 }
 if s["local"].is_null(){if action=="calendar.cancel"{s["outcome"]=json!({"kind":"cancel"});}return Screen::new(title,s.clone()).body(vec![label("calendar.error",s["error"].as_str().unwrap_or("Loading local times")),button("calendar.cancel","Cancel")]).back("calendar.cancel").finish()}
 let mut event=input["event"].clone();
 if matches!(action,"calendar.timezone"|"calendar.gap"|"calendar.overlap"){s["draft"]["dirty"]=json!(true);s["error"]=json!("");}
 match action{
 "calendar.timezone"=>{s["timezone"]=json!(plain(event["value"].as_str().unwrap_or(""),128));event=Value::Null},
 "calendar.gap"=>{s["gap"]=event["value"].clone();event=Value::Null},
 "calendar.overlap"=>{s["overlap"]=event["value"].clone();event=Value::Null},
 "calendar.resolved"=>{event=Value::Null;if input["event"]["reply"]["ok"]==true{let v=&input["event"]["reply"]["value"];let mut saved=s["candidate"].clone();saved["start"]=v["start"].clone();saved["end"]=v["end"].clone();if crate::calendar::validate_event(&saved).is_ok(){s["outcome"]=json!({"kind":"save","event":saved,"timezone":s["timezone"]})}else{s["error"]=json!("Invalid resolved calendar response")}}else{s["error"]=json!("This time or timezone is invalid. Check the clock-change choices.")}},_=>{}
 }
 let mut f=crate::calendar_ui::event_editor(title,json!({"state":s["draft"],"event":event}),&s["local"]);
 s["draft"]=f["state"].clone();
 let outcome=f["state"]["outcome"].clone();let mut pending=None;
 if outcome["kind"]=="save"{let candidate=&outcome["event"];if candidate["all_day"]==true{s["outcome"]=outcome.clone()}else{s["candidate"]=candidate.clone();pending=Some(effect("calendar.resolve",json!({"start":iso(candidate["start"].as_str().unwrap()),"end":iso(candidate["end"].as_str().unwrap()),"timezone":s["timezone"],"gap":s["gap"],"overlap":s["overlap"]}),"calendar.resolved"));}}
 else if outcome["kind"]=="cancel"{s["outcome"]=outcome;}
 let body=f["body"].as_array_mut().unwrap();
 for node in body.iter_mut(){if let Some(text)=node["text"].as_str(){if text.contains("in UTC"){node["text"]=json!(text.replace("in UTC","in selected timezone"))}}}
 body.insert(0,field("calendar.timezone","Timezone (for example America/Chicago)",s["timezone"].as_str().unwrap_or("")).limit(128));
 body.insert(1,choices("select","calendar.gap","Clock jumps forward",s["gap"].as_str().unwrap_or("reject"),&[("reject","Choose another time"),("forward","Move forward through the gap")]));
 body.insert(2,choices("select","calendar.overlap","Clock repeats an hour",s["overlap"].as_str().unwrap_or("earlier"),&[("earlier","First occurrence"),("later","Second occurrence")]));
 body.push(label("calendar.zone_error",s["error"].as_str().unwrap_or("")));f["state"]=s;if let Some(effect)=pending{f["effect"]=effect;}f
}

#[cfg(test)]mod tests{
 use super::*;
 fn initial()->Value{json!({"uid":"one","start":"20261101T063000Z","end":"20261101T083000Z","summary":"Meeting"})}
 #[test]fn named_zone_editor_resolves_explicitly_and_preserves_saved_utc_until_parent_commit(){
  let original=initial();let first=event_editor("Calendar",json!({}),&original,"America/Chicago");assert_eq!(first["effect"]["service"],"calendar.localize");
  let local=event_editor("Calendar",json!({"state":first["state"],"event":{"action":"calendar.localized","reply":{"ok":true,"value":{"start":"20261101T013000Z","end":"20261101T023000Z"}}}}),&original,"America/Chicago");
  let resolve=event_editor("Calendar",json!({"state":local["state"],"event":{"action":"calendar.save"}}),&original,"America/Chicago");assert_eq!(resolve["effect"]["service"],"calendar.resolve");assert_eq!(resolve["effect"]["args"]["start"],"2026-11-01T01:30:00");assert!(resolve["state"]["outcome"].is_null());assert_eq!(resolve["state"]["original"],original);
  let failed=event_editor("Calendar",json!({"state":resolve["state"],"event":{"action":"calendar.resolved","reply":{"ok":false}}}),&original,"America/Chicago");assert!(failed["state"]["outcome"].is_null());assert_eq!(failed["state"]["draft"]["summary"],"Meeting");
  let saved=event_editor("Calendar",json!({"state":resolve["state"],"event":{"action":"calendar.resolved","reply":{"ok":true,"value":{"start":"20261101T063000Z","end":"20261101T083000Z"}}}}),&original,"America/Chicago");for key in ["uid","start","end","summary"]{assert_eq!(saved["state"]["outcome"]["event"][key],original[key]);}assert_eq!(saved["state"]["outcome"]["timezone"],"America/Chicago");
 }
}
