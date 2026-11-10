//! Host-themed agenda and validated event editor. Persistence is explicit: the
//! parent consumes a Save outcome and commits through its chosen storage service.
use crate::*;
use crate::calendar::{validate_event,valid_date};

fn date(s:&str)->String{if s.len()>=8&&s.is_ascii(){format!("{}-{}-{}",&s[..4],&s[4..6],&s[6..8])}else{String::new()}}
fn compact(s:&str)->Option<String>{let b=s.as_bytes();if b.len()!=10||!s.is_ascii()||b[4]!=b'-'||b[7]!=b'-'{return None}let d=format!("{}{}{}",&s[..4],&s[5..7],&s[8..]);valid_date(&d).then_some(d)}
fn time(s:&str)->String{if s.len()==16&&s.is_ascii(){format!("{}:{}:{}",&s[9..11],&s[11..13],&s[13..15])}else{"09:00:00".into()}}
fn stamp(d:&str,t:&str)->Result<String,String>{
 let d=compact(d).ok_or("Use a valid date in YYYY-MM-DD format")?;
 let b=t.as_bytes();if !t.is_ascii()||!matches!(b.len(),5|8)||b[2]!=b':'||(b.len()==8&&b[5]!=b':')||b.iter().enumerate().any(|(i,c)|i!=2&&i!=5&&!c.is_ascii_digit()){return Err("Use a UTC time in HH:MM or HH:MM:SS format".into())}
 let result=format!("{d}T{}{}{}Z",&t[..2],&t[3..5],if t.len()==8{&t[6..]}else{"00"});Ok(result)
}
fn last_day(end:&str)->String{
 let y=end[..4].parse::<u32>().unwrap();let m=end[4..6].parse::<u32>().unwrap();let d=end[6..8].parse::<u32>().unwrap();
 if d>1{return format!("{y:04}{m:02}{:02}",d-1)}
 let(y,m)=if m==1{(y-1,12)}else{(y,m-1)};
 let d=(28..=31).rev().find(|d|valid_date(&format!("{y:04}{m:02}{d:02}"))).unwrap();format!("{y:04}{m:02}{d:02}")
}
/// A bounded, chronological 20-row agenda. Row actions identify the original
/// input index (`calendar.open.N`), even after sorting. Keep that snapshot until
/// the action is handled; re-resolve the event UID before any storage mutation.
pub fn agenda(title:&str,events:&[Value],offset:usize)->Result<Value,String>{
 if events.len()>200{return Err("Use at most200 events in an agenda snapshot".into())}
 let mut seen=std::collections::BTreeSet::new();for e in events{validate_event(e)?;if !seen.insert(e["uid"].as_str().unwrap()){return Err("Duplicate agenda event UID".into())}}
 let mut ordered:Vec<_>=events.iter().enumerate().collect();ordered.sort_by(|(ia,a),(ib,b)|a["start"].as_str().cmp(&b["start"].as_str()).then_with(||ia.cmp(ib)));
 let offset=if ordered.is_empty(){0}else{(offset/20*20).min((ordered.len()-1)/20*20)};
 let mut body=Vec::new();let mut previous=String::new();
 for(index,event)in ordered.iter().skip(offset).take(20){
  let start=event["start"].as_str().unwrap();let end=event["end"].as_str().unwrap();let day=date(start);
  if day!=previous{body.push(heading(&format!("calendar.day.{index}"),&day));previous=day;}
  let detail=if event["all_day"]==true{let last=last_day(end);if last==start{"All day".into()}else{format!("All day · through {}",date(&last))}}else{format!("{} – {} {} UTC",time(start),date(end),time(end))};
  body.push(record(&format!("calendar.open.{index}"),event["summary"].as_str().unwrap(),&detail));
 }
 if events.is_empty(){body.push(empty_state("calendar.empty","No events yet. Choose New event to get started."));}
 if offset>0{body.push(button("calendar.previous","Previous events"));}if offset+20<events.len(){body.push(button("calendar.next","More events"));}
 body.push(label("calendar.count",&format!("{} events",events.len())));
 Ok(Screen::new(title,json!({"offset":offset})).body(body).actions(vec![icon("calendar.new","New event","add"),icon("calendar.refresh","Refresh","refresh")]).scroll_key(&format!("calendar.{offset}")).back("calendar.back").finish())
}

/// Reusable editor reducer. `initial` is a valid event with a stable UID. Keep
/// the returned state until save/cancel. No host write occurs here. On Save,
/// state.outcome is {kind:"save",event:...}; on cancellation it is {kind:"cancel"}.
/// A parent clears the editor only after its storage transaction succeeds, and
/// retains the returned state on permission/storage/revision errors.
pub fn event_editor(title:&str,input:Value,initial:&Value)->Value{
 if let Err(error)=validate_event(initial){let cancel=matches!(input["event"]["action"].as_str(),Some("calendar.cancel"|"calendar.back"));return Screen::new(title,json!({"outcome":if cancel{json!({"kind":"cancel"})}else{Value::Null}})).body(vec![label("calendar.error",&format!("Cannot edit this event: {error}")),button("calendar.cancel","Back")]).back("calendar.cancel").finish()}
 let mut s=input["state"].clone();
 if s["format"]!="onklav.calendar.editor.v1"||s["original"]!=*initial{
  s=json!({"format":"onklav.calendar.editor.v1","original":initial,"summary":initial["summary"],"description":initial["description"].as_str().unwrap_or(""),"start_date":date(initial["start"].as_str().unwrap()),"end_date":date(initial["end"].as_str().unwrap()),"start_time":time(initial["start"].as_str().unwrap()),"end_time":if initial["all_day"]==true{"10:00:00".to_owned()}else{time(initial["end"].as_str().unwrap())},"all_day":initial["all_day"]==true,"dirty":false,"confirm":false,"error":"","outcome":null});
 }
 s["outcome"]=Value::Null;
 let e=&input["event"];let a=e["action"].as_str().unwrap_or("");
 match a{
 "calendar.summary"|"calendar.description"|"calendar.start_date"|"calendar.end_date"|"calendar.start_time"|"calendar.end_time"=>{
  let key=&a[9..];let limit=match key{"summary"=>1024,"description"=>8192,"start_date"|"end_date"=>10,_=>8};
  s[key]=json!(plain(e["value"].as_str().unwrap_or(""),limit));s["dirty"]=json!(true);s["error"]=json!("");
 },
 "calendar.all_day" if matches!(e["value"].as_str(),Some("true"|"false"))=>{s["all_day"]=json!(e["value"]=="true");s["dirty"]=json!(true);s["error"]=json!("");},
 "calendar.save"=>{
  let get=|k:&str|s[k].as_str().unwrap_or("");let all_day=s["all_day"]==true;
  let convert=|d:&str,t:&str|if all_day{compact(d).ok_or("Use a valid date in YYYY-MM-DD format".into())}else{stamp(d,t)};
  let candidate=(||->Result<Value,String>{let mut event=initial.clone();event["summary"]=json!(get("summary").trim());event["description"]=s["description"].clone();if all_day{event["all_day"]=json!(true);}else{event.as_object_mut().unwrap().remove("all_day");}event["start"]=json!(convert(get("start_date"),get("start_time"))?);event["end"]=json!(convert(get("end_date"),get("end_time"))?);validate_event(&event)?;Ok(event)})();
  match candidate{Ok(event)=>s["outcome"]=json!({"kind":"save","event":event}),Err(error)=>s["error"]=json!(error)}
 },
 "calendar.cancel"|"calendar.back"=>{if s["dirty"]==true{s["confirm"]=json!(true)}else{s["outcome"]=json!({"kind":"cancel"})}},
 "calendar.discard" if s["confirm"]==true=>{s["confirm"]=json!(false);s["outcome"]=json!({"kind":"cancel"})},
 "calendar.keep"=>s["confirm"]=json!(false),_=>{}
 }
 let get=|k:&str|s[k].as_str().unwrap_or("");let all_day=s["all_day"]==true;
 let mut nodes=vec![field("calendar.summary","Event title",get("summary")).limit(1024),toggle("calendar.all_day","All day",all_day),label("calendar.start_label","Start date (YYYY-MM-DD)"),field("calendar.start_date","YYYY-MM-DD",get("start_date")).limit(10)];
 if !all_day{nodes.extend([label("calendar.start_time_label","Start time in UTC (HH:MM or HH:MM:SS)"),field("calendar.start_time","HH:MM",get("start_time")).limit(8)]);}
 nodes.extend([label("calendar.end_label",if all_day{"End date (YYYY-MM-DD, first day after the event)"}else{"End date (YYYY-MM-DD)"}),field("calendar.end_date","YYYY-MM-DD",get("end_date")).limit(10)]);
 if !all_day{nodes.extend([label("calendar.end_time_label","End time in UTC (HH:MM or HH:MM:SS)"),field("calendar.end_time","HH:MM",get("end_time")).limit(8)]);}
 nodes.extend([editor("calendar.description","Details",get("description")).limit(8192),label("calendar.error",get("error")),primary_button("calendar.save","Save event"),button("calendar.cancel","Cancel")]);
 let mut frame=Screen::new(title,s.clone()).body(nodes).back("calendar.back").scroll_key("calendar.editor");
 if s["confirm"]==true{frame=frame.confirm_labeled("Discard changes?","Your saved event will be kept.","calendar.discard","calendar.keep","Discard");}frame.finish()
}

#[cfg(test)] mod tests{
 use super::*;
 fn day()->Value{json!({"uid":"one@example.org","start":"20280229","end":"20280301","all_day":true,"summary":"Day off","description":"Keep me"})}
 fn timed()->Value{json!({"uid":"two@example.org","start":"20280229T103045Z","end":"20280229T113059Z","summary":"Meeting"})}
 fn step(state:Value,action:&str,value:&str,initial:&Value)->Value{event_editor("Calendar",json!({"state":state,"event":{"action":action,"value":value}}),initial)}
 #[test]fn editor_preserves_exact_seconds_and_utc_api_compatibility(){let original=timed();let f=step(Value::Null,"start","",&original);let saved=step(f["state"].clone(),"calendar.save","",&original);let e=&saved["state"]["outcome"]["event"];assert_eq!(e["start"],original["start"]);assert_eq!(e["end"],original["end"]);assert!(crate::calendar::export_utc(&[e.clone()],"20261025T010000Z").is_ok());assert!(saved["effect"].is_null());}
 #[test]fn editor_invalid_input_retains_draft_and_never_emits_save(){let original=day();let f=step(Value::Null,"start","",&original);let changed=step(f["state"].clone(),"calendar.end_date","2028-02-29",&original);let failed=step(changed["state"].clone(),"calendar.save","",&original);assert!(failed["state"]["outcome"].is_null());assert_eq!(failed["state"]["end_date"],"2028-02-29");assert!(!failed["state"]["error"].as_str().unwrap().is_empty());assert_eq!(failed["state"]["original"],original);assert!(failed["effect"].is_null());}
 #[test]fn editor_discard_requires_confirmation_and_cancel_keeps_values(){let original=day();let f=step(Value::Null,"calendar.summary","Changed",&original);let back=step(f["state"].clone(),"calendar.back","",&original);assert_eq!(back["confirm"]["title"],"Discard changes?");assert!(back["state"]["outcome"].is_null());let keep=step(back["state"].clone(),"calendar.keep","",&original);assert_eq!(keep["state"]["summary"],"Changed");assert!(keep["confirm"].is_null());let cancel=step(back["state"].clone(),"calendar.discard","",&original);assert_eq!(cancel["state"]["outcome"]["kind"],"cancel");}
 #[test]fn editor_all_day_toggle_preserves_hidden_times_and_validates_converted_dates(){let original=timed();let f=step(Value::Null,"start","",&original);let all=step(f["state"].clone(),"calendar.all_day","true",&original);assert!(!all["body"].as_array().unwrap().iter().any(|n|n["id"]=="calendar.start_time"));let timed_again=step(all["state"].clone(),"calendar.all_day","false",&original);let saved=step(timed_again["state"].clone(),"calendar.save","",&original);assert_eq!(saved["state"]["outcome"]["event"]["start"],original["start"]);let invalid=step(all["state"].clone(),"calendar.save","",&original);assert!(invalid["state"]["outcome"].is_null());let end=step(all["state"].clone(),"calendar.end_date","2028-03-01",&original);let saved=step(end["state"].clone(),"calendar.save","",&original);assert_eq!(saved["state"]["outcome"]["event"]["all_day"],true);}
 #[test]fn editor_preserves_uid_and_rejects_foreign_unsupported_event_data(){let mut initial=day();initial["rrule"]=json!("FREQ=DAILY");let rejected=step(Value::Null,"calendar.save","",&initial);assert!(rejected["state"]["outcome"].is_null());assert_eq!(step(Value::Null,"calendar.cancel","",&initial)["state"]["outcome"]["kind"],"cancel");let original=day();let f=step(Value::Null,"calendar.summary","A new title",&original);let saved=step(f["state"].clone(),"calendar.save","",&original);assert_eq!(saved["state"]["outcome"]["event"]["uid"],original["uid"]);}
 #[test]fn editor_invalid_unicode_dates_and_invalid_times_fail_without_panics(){let original=timed();for (field,value) in [("calendar.start_date","😀😀😀"),("calendar.start_date","2029-02-29"),("calendar.start_time","24:00"),("calendar.start_time","12:60"),("calendar.start_time","12:00:60"),("calendar.start_time","abcd:efg"),("calendar.summary","")]{let f=step(Value::Null,field,value,&original);assert!(step(f["state"].clone(),"calendar.save","",&original)["state"]["outcome"].is_null(),"{field}={value}");}}
 #[test]fn agenda_sorts_and_preserves_original_indices_and_all_day_display(){let f=agenda("Calendar",&[timed(),day()],0).unwrap();let rows:Vec<_>=f["body"].as_array().unwrap().iter().filter(|n|n["kind"]=="record").collect();assert_eq!(rows[0]["id"],"calendar.open.1");assert_eq!(rows[0]["detail"],"All day");assert_eq!(rows[1]["id"],"calendar.open.0");assert!(rows[1]["detail"].as_str().unwrap().ends_with("UTC"));let mut multi=day();multi["end"]=json!("20280302");assert!(agenda("Calendar",&[multi],0).unwrap()["body"].to_string().contains("through 2028-03-01"));}
 #[test]fn agenda_bounds_pagination_and_duplicate_detection(){let mut events=Vec::new();for i in 0..41{let mut d=day();d["uid"]=json!(format!("event-{i}"));events.push(d);}let first=agenda("Calendar",&events,0).unwrap();assert_eq!(first["body"].as_array().unwrap().iter().filter(|n|n["kind"]=="record").count(),20);let last=agenda("Calendar",&events,usize::MAX).unwrap();assert_eq!(last["state"]["offset"],40);assert_eq!(last["body"].as_array().unwrap().iter().filter(|n|n["kind"]=="record").count(),1);assert!(agenda("Calendar",&[day(),day()],0).is_err());assert!(agenda("Calendar",&vec![day();201],0).is_err());assert!(agenda("Calendar",&[],0).unwrap()["body"].to_string().contains("No events yet"));}
 #[test]fn ui_uses_standard_theme_owned_widgets_without_fixed_dimensions_or_colors(){let initial=day();for frame in [agenda("Calendar",&[initial.clone()],0).unwrap(),step(Value::Null,"start","",&initial)]{for n in frame["body"].as_array().unwrap(){assert!(matches!(n["kind"].as_str(),Some("label"|"record"|"field"|"editor"|"toggle"|"button")));for property in ["color","background","width","height","font_size"]{assert!(n.get(property).is_none());}}}}
}
