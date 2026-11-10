//! Bounded RFC5545 UTC and all-day DATE import/export.
//! Named timezones, recurrence, calendar-provider access and scheduling are separate.
use crate::serde_json::{Value,json};

fn unescape(value:&str)->Result<String,String>{
 let mut out=String::new();let mut chars=value.chars();
 while let Some(c)=chars.next(){if c=='\\'{out.push(match chars.next(){Some('n'|'N')=>'\n',Some(c@('\\'|','|';'))=>c,_=>return Err("Invalid calendar text escape".into())})}else{out.push(c)}}Ok(out)
}
/// Import the supported UTC-only subset atomically. Unsupported properties,
/// nested components, local times and recurrence are errors, never dropped.
/// Returns event objects suitable for an explicit collection transaction.
pub fn import_utc(input:&str)->Result<Vec<Value>,String>{import_inner(input,false)}
/// Import mixed UTC and all-day DATE events atomically. DATE ends are exclusive.
pub fn import_events(input:&str)->Result<Vec<Value>,String>{import_inner(input,true)}
fn import_inner(input:&str,allow_date:bool)->Result<Vec<Value>,String>{
 if input.len()>262144||input.chars().any(|c|c.is_control()&&!matches!(c,'\r'|'\n'|'\t')){return Err("Invalid or oversized calendar".into())}
 let mut lines:Vec<String>=Vec::new();
 for line in input.lines(){
  if line.starts_with([' ','\t']){let previous=lines.last_mut().ok_or("Calendar begins with a continuation")?;previous.push_str(&line[1..]);if previous.len()>20000{return Err("Calendar line exceeds its limit".into())}}
  else{if line.len()>20000||lines.len()>=2000{return Err("Calendar exceeds line limits".into())}lines.push(line.to_owned())}
 }
 let mut state=0;let mut event=json!({});let mut result=Vec::new();let mut version=false;let mut stamp=String::new();let mut fields=std::collections::BTreeSet::new();let mut headers=std::collections::BTreeSet::new();
 for line in lines{
  match line.as_str(){
   "BEGIN:VCALENDAR" if state==0=>{state=1;continue},
   "BEGIN:VEVENT" if state==1=>{if result.len()>=200{return Err("Calendar exceeds 200 events".into())}state=2;event=json!({});fields.clear();stamp.clear();continue},
   "END:VEVENT" if state==2=>{
    let start_date=event["start_date_type"]==true;let end_date=event["end_date_type"]==true;
    event.as_object_mut().unwrap().remove("start_date_type");event.as_object_mut().unwrap().remove("end_date_type");
    if start_date!=end_date{return Err("Event start/end must use the same date type".into())}
    if start_date{event["all_day"]=json!(true);}
    if !utc(&stamp){return Err("Event needs a valid UTC DTSTAMP".into())}export_inner(&[event.clone()],&stamp,allow_date)?;result.push(event.clone());state=1;continue},
   "END:VCALENDAR" if state==1=>{state=3;continue},_=>{}
  }
  let (key,value)=line.split_once(':').ok_or("Invalid calendar property")?;let key=key.to_ascii_uppercase();
  if state==1{
   if !headers.insert(key.clone()){return Err("Duplicate calendar header".into())}
   match key.as_str(){"VERSION" if value=="2.0"=>version=true,"PRODID" if !value.is_empty()=>{},"CALSCALE" if value=="GREGORIAN"=>{},_=>return Err("Unsupported calendar header".into())}
  }else if state==2{
   let base=key.split(';').next().unwrap_or("");
   if !fields.insert(base.to_owned()){return Err("Duplicate event property".into())}
   if matches!(key.as_str(),"DTSTART;VALUE=DATE"|"DTEND;VALUE=DATE"){
    if !allow_date{return Err("All-day dates require import_events".into())}
    event[if base=="DTSTART"{"start"}else{"end"}]=json!(value);
    event[if base=="DTSTART"{"start_date_type"}else{"end_date_type"}]=json!(true);continue
   }
   let field=match key.as_str(){"UID"=>"uid","DTSTART"=>"start","DTEND"=>"end","SUMMARY"=>"summary","DESCRIPTION"=>"description","DTSTAMP"=>{stamp=value.into();continue},_=>return Err("Unsupported event property, timezone or recurrence".into())};
   event[field]=json!(if matches!(field,"uid"|"summary"|"description"){unescape(value)?}else{value.to_owned()});
  }else{return Err("Calendar data outside its container".into())}
 }
 if state!=3||!version||!headers.contains("PRODID"){return Err("Incomplete calendar".into())}
 export_inner(&result,"20000101T000000Z",allow_date)?;Ok(result)
}

/// Strict Gregorian YYYYMMDD date, including leap-year validation.
pub fn valid_date(s:&str)->bool{
 if s.len()!=8||!s.bytes().all(|c|c.is_ascii_digit()){return false}
 let n=|a:usize,z:usize|s[a..z].parse::<u32>().unwrap_or(0);
 let(y,m,d)=(n(0,4),n(4,6),n(6,8));let days=match m{1|3|5|7|8|10|12=>31,4|6|9|11=>30,2 if y%4==0&&(y%100!=0||y%400==0)=>29,2=>28,_=>0};y>0&&d>0&&d<=days
}
fn utc(s:&str)->bool{
 let b=s.as_bytes();if b.len()!=16||!s.is_ascii()||b[8]!=b'T'||b[15]!=b'Z'||b.iter().enumerate().any(|(i,c)|i!=8&&i!=15&&!c.is_ascii_digit()){return false}
 let n=|a:usize,z:usize|s[a..z].parse::<u32>().unwrap_or(99);
 valid_date(&s[..8])&&n(9,11)<24&&n(11,13)<60&&n(13,15)<60
}
/// Validate one supported event without writing or changing it.
pub fn validate_event(event:&Value)->Result<(),String>{validate(event,true)}
fn validate(event:&Value,allow_date:bool)->Result<(),String>{
 let obj=event.as_object().ok_or("Calendar event must be an object")?;
 if obj.keys().any(|k|!["uid","start","end","summary","description"].contains(&k.as_str())&&!(allow_date&&k=="all_day")){return Err("Unsupported calendar event field; timezone/recurrence data is not discarded".into())}
 if event.get("all_day").is_some_and(|v|!v.is_boolean()){return Err("all_day must be a boolean".into())}
 let get=|key:&str|event[key].as_str().ok_or_else(||format!("Calendar event needs string {key}"));
 let(uid,start,end,summary)=(get("uid")?,get("start")?,get("end")?,get("summary")?);
 let all_day=event["all_day"]==true;
 if uid.is_empty()||uid.len()>128||uid.chars().any(char::is_control){return Err("Use a stable event UID of1–128 bytes without controls".into())}
 if !(if all_day{valid_date(start)&&valid_date(end)}else{utc(start)&&utc(end)})||end<=start{return Err("Use valid dates/times and an end after the start (all-day end is exclusive)".into())}
 if summary.trim().is_empty(){return Err("Enter an event title".into())}text(summary,1024)?;
 if obj.contains_key("description"){text(get("description")?,8192)?;}Ok(())
}
fn text(s:&str,max:usize)->Result<String,String>{
 if s.len()>max||s.chars().any(|c|c.is_control()&&!matches!(c,'\n'|'\r'|'\t')){return Err("Calendar text exceeds its limit or contains unsupported controls".into())}
 Ok(s.replace("\r\n","\n").replace('\r',"\n").replace('\\',"\\\\").replace(';',"\\;").replace(',',"\\,").replace('\n',"\\n"))
}
fn line(output:&mut String,value:&str){
 let mut bytes=0;for c in value.chars(){if bytes+c.len_utf8()>75{output.push_str("\r\n ");bytes=1;}output.push(c);bytes+=c.len_utf8();}output.push_str("\r\n");
}
/// Each event has uid, start, end and summary; optional description. Times must
/// be YYYYMMDDTHHMMSSZ. End is exclusive. Caller supplies an explicit UTC stamp.
/// Unknown keys fail rather than silently discarding recurrence or timezone data.
pub fn export_utc(events:&[Value],stamp:&str)->Result<String,String>{export_inner(events,stamp,false)}
/// Export UTC and all-day events. all_day:true uses YYYYMMDD start/end with an
/// exclusive end; timed events use YYYYMMDDTHHMMSSZ. No timezone guessing.
pub fn export_events(events:&[Value],stamp:&str)->Result<String,String>{export_inner(events,stamp,true)}
fn export_inner(events:&[Value],stamp:&str,allow_date:bool)->Result<String,String>{
 if events.len()>200||!utc(stamp){return Err("Use up to 200 events and a valid explicit UTC timestamp".into())}
 let mut output=String::from("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//OnKlav//Calendar Helper//EN\r\nCALSCALE:GREGORIAN\r\n");
 let mut seen=std::collections::BTreeSet::new();
 for event in events{
  validate(event,allow_date)?;let obj=event.as_object().unwrap();
  let get=|key:&str|event[key].as_str().ok_or_else(||format!("Calendar event needs string {key}"));
  let(uid,start,end,summary)=(get("uid")?,get("start")?,get("end")?,get("summary")?);
  if !seen.insert(uid){return Err("Duplicate calendar event UID".into())}
  let date_type=if event["all_day"]==true{";VALUE=DATE"}else{""};
  line(&mut output,"BEGIN:VEVENT");line(&mut output,&format!("UID:{}",text(uid,128)?));line(&mut output,&format!("DTSTAMP:{stamp}"));line(&mut output,&format!("DTSTART{date_type}:{start}"));line(&mut output,&format!("DTEND{date_type}:{end}"));line(&mut output,&format!("SUMMARY:{}",text(summary,1024)?));
  if obj.contains_key("description"){line(&mut output,&format!("DESCRIPTION:{}",text(get("description")?,8192)?));}
  line(&mut output,"END:VEVENT");if output.len()>262000{return Err("Calendar export exceeds 256 KiB".into())}
 }
 line(&mut output,"END:VCALENDAR");Ok(output)
}

#[cfg(test)]mod tests{
 use super::*;use crate::serde_json::json;
 fn day()->Value{json!({"uid":"holiday@example.org","start":"20280229","end":"20280301","all_day":true,"summary":"Day off"})}
 #[test]fn mixed_utc_and_all_day_roundtrip_preserves_exclusive_end_and_unicode(){
  let mut d=day();d["description"]=json!("😀".repeat(100)+"\nText, with; escapes\\");let events=vec![event(),d];
  let out=export_events(&events,"20261025T010000Z").unwrap();assert!(out.contains("DTSTART;VALUE=DATE:20280229\r\nDTEND;VALUE=DATE:20280301"));assert_eq!(import_events(&out).unwrap(),events);assert!(out.split("\r\n").all(|l|l.len()<=75));
  assert!(import_utc(&out).is_err());assert!(export_utc(&[day()],"20261025T010000Z").is_err());
 }
 #[test]fn all_day_import_rejects_duplicate_mixed_date_types_and_invalid_days_atomically(){
  let valid=export_events(&[event(),day()],"20261025T010000Z").unwrap();
  for bad in [valid.replace("DTEND;VALUE=DATE:20280301","DTEND:20280301T000000Z"),valid.replace("DTSTART;VALUE=DATE:20280229","DTSTART;VALUE=DATE:20280229\r\nDTSTART:20280229T000000Z"),valid.replace("20280229\r\nDTEND;VALUE=DATE","20270229\r\nDTEND;VALUE=DATE"),valid.replace("DTEND;VALUE=DATE:20280301","DTEND;VALUE=DATE:20280229"),valid.replace(";VALUE=DATE:",";VALUE=DATE;TZID=UTC:")]{assert!(import_events(&bad).is_err(),"{bad}");}
  for d in ["00000101","20290229","20280230","20281301","20280001","20280100","😀😀"]{assert!(!valid_date(d));}
  for v in [json!("true"),json!(null),json!(1)]{let mut d=day();d["all_day"]=v;assert!(validate_event(&d).is_err());}
 }
 #[test]fn calendar_bounds_and_duplicate_uids_fail_without_partial_results(){
  let stamp="20261025T010000Z";assert!(export_events(&vec![day();201],stamp).is_err());assert!(export_events(&[day(),day()],stamp).is_err());assert!(import_events(&"x".repeat(262145)).is_err());
  let mut d=day();d["summary"]=json!("x".repeat(1025));assert!(export_events(&[d],stamp).is_err());
  let mut d=day();d["description"]=json!("x".repeat(8193));assert!(export_events(&[d],stamp).is_err());
 }
 fn event()->Value{json!({"uid":"meeting@example.org","start":"20280229T100000Z","end":"20280229T110000Z","summary":"A, B; C","description":"Line one\nLine two"})}
 #[test]fn imports_folded_unicode_export_without_changing_event_data(){let mut e=event();e["summary"]=json!("😀".repeat(100));let encoded=export_utc(&[e.clone()],"20261025T010000Z").unwrap();assert_eq!(vec![e],import_utc(&encoded).unwrap());}
 #[test]fn imports_reject_unsupported_or_partial_calendars_without_partial_results(){let valid=export_utc(&[event()],"20261025T010000Z").unwrap();for bad in [valid.replace("DTSTART:","DTSTART;TZID=America/Chicago:"),valid.replace("END:VEVENT","RRULE:FREQ=DAILY\r\nEND:VEVENT"),valid.replace("SUMMARY:","SUMMARY:duplicate\r\nSUMMARY:"),valid.replace("END:VCALENDAR\r\n",""),valid.clone()+"BEGIN:VEVENT\r\n",valid.replace("END:VEVENT","BEGIN:VALARM\r\nEND:VEVENT")]{assert!(import_utc(&bad).is_err());}assert!(import_utc(&(valid.clone()+&valid)).is_err());}
 #[test]fn exports_escaped_text_and_folds_unicode_without_injecting_properties(){let mut e=event();e["summary"]=json!("😀".repeat(100)+"\r\nBEGIN:VEVENT");let out=export_utc(&[e],"20261025T010000Z").unwrap();assert!(out.contains("\\nBEGIN:VEVENT"));assert_eq!(1,out.matches("\r\nBEGIN:VEVENT\r\n").count());assert!(out.split("\r\n").all(|l|l.len()<=75));assert!(out.contains("DESCRIPTION:Line one\\nLine two"));}
 #[test]fn rejects_invalid_dates_intervals_and_unsupported_recurrence(){let stamp="20261025T010000Z";for start in ["20270229T100000Z","20281301T100000Z","20280229T240000Z","20280229T100060Z","20280229T100000","00000229T100000Z"]{let mut e=event();e["start"]=json!(start);assert!(export_utc(&[e],stamp).is_err(),"{start}");}let mut e=event();e["rrule"]=json!("FREQ=DAILY");assert!(export_utc(&[e],stamp).is_err());assert!(export_utc(&[event(),event()],stamp).is_err());let mut e=event();e["end"]=e["start"].clone();assert!(export_utc(&[e],stamp).is_err());}
}

/// Android owns the final calendar choice, edit/review and Save. handed_off is
/// not a claim that the user saved the event. All-day ends are exclusive UTC dates.
pub fn insert_android(title:&str,description:&str,start_ms:u64,end_ms:u64,timezone:&str,all_day:bool,then:&str)->Value{
 crate::effect("calendar.insert",crate::json!({"title":title,"description":description,"start_ms":start_ms,"end_ms":end_ms,"timezone":timezone,"all_day":all_day}),then)
}
