//! Read-only bounded host timezone expansion. No calendar/device data access.
use crate::Result;
use serde_json::Value;
const MAX_MS:u64=253402300799999;
fn keys(v:&Value,allowed:&[&str])->Result<()>{let o=v.as_object().ok_or("Expected a calendar object")?;if o.keys().any(|k|!allowed.contains(&k.as_str())){return Err("Unsupported calendar argument or response field".into())}Ok(())}
fn number(v:&Value,k:&str,min:u64,max:u64)->Result<u64>{v[k].as_u64().filter(|n|*n>=min&&*n<=max).ok_or_else(||format!("Calendar {k} is out of range"))}
fn local(v:&Value,k:&str)->Result<()>{let s=v[k].as_str().ok_or("Missing local date/time")?;let b=s.as_bytes();if b.len()!=19||!s.is_ascii()||b[4]!=b'-'||b[7]!=b'-'||b[10]!=b'T'||b[13]!=b':'||b[16]!=b':'||b.iter().enumerate().any(|(i,c)|![4,7,10,13,16].contains(&i)&&!c.is_ascii_digit()){return Err("Use local YYYY-MM-DDTHH:MM:SS".into())}Ok(())}
pub(crate) fn request(a:&Value)->Result<()>{
 keys(a,&["start","timezone","frequency","interval","count","duration_seconds","duration_days","window_start_ms","window_end_ms","limit","gap","overlap"])?;
 local(a,"start")?;let zone=a["timezone"].as_str().ok_or("Choose a timezone")?;if zone.is_empty()||zone.len()>128||!zone.bytes().all(|b|b.is_ascii_alphanumeric()||b"/_+-:.".contains(&b)){return Err("Invalid timezone".into())}
 if !matches!(a["frequency"].as_str(),Some("daily"|"weekly"|"monthly"|"yearly"))||!matches!(a["gap"].as_str(),Some("skip"|"forward"))||!matches!(a["overlap"].as_str(),Some("earlier"|"later")){return Err("Choose a supported recurrence and explicit DST policies".into())}
 number(a,"interval",1,365)?;number(a,"count",1,1000)?;number(a,"limit",1,200)?;
 if a.get("duration_days").is_some(){if a.get("duration_seconds").is_some(){return Err("Choose timed or all-day duration, not both".into())}number(a,"duration_days",1,366)?;if !a["start"].as_str().unwrap().ends_with("T00:00:00"){return Err("All-day recurrence starts at local midnight".into())}}else{number(a,"duration_seconds",1,604800)?;}
 let from=number(a,"window_start_ms",0,MAX_MS)?;let to=number(a,"window_end_ms",1,MAX_MS)?;if to<=from{return Err("Choose an increasing recurrence window".into())}Ok(())
}
pub(crate) fn reply(a:&Value,v:&Value)->Result<()>{
 keys(v,&["timezone","occurrences","truncated"])?;if v["timezone"]!=a["timezone"]||!v["truncated"].is_boolean(){return Err("Invalid recurrence response".into())}
 let rows=v["occurrences"].as_array().filter(|r|r.len()<=a["limit"].as_u64().unwrap_or(0)as usize).ok_or("Invalid recurrence rows")?;let mut previous=None;
 for r in rows{keys(r,&["start_ms","end_ms","local_start","local_end","offset_seconds","end_offset_seconds"])?;let start=number(r,"start_ms",a["window_start_ms"].as_u64().unwrap(),a["window_end_ms"].as_u64().unwrap()-1)?;let end=number(r,"end_ms",1,MAX_MS)?;if end<=start||previous.is_some_and(|p|start<=p){return Err("Invalid recurrence ordering or interval".into())}previous=Some(start);local(r,"local_start")?;local(r,"local_end")?;for k in ["offset_seconds","end_offset_seconds"]{if r[k].as_i64().is_none_or(|n|!(-64800..=64800).contains(&n)){return Err("Invalid timezone offset".into())}}}Ok(())
}
#[cfg(test)]mod tests{
 use super::*;use serde_json::json;
 fn args()->Value{json!({"start":"2026-03-07T09:00:00","timezone":"America/Chicago","frequency":"daily","interval":1,"count":4,"duration_seconds":3600,"window_start_ms":0,"window_end_ms":253402300799999u64,"limit":200,"gap":"skip","overlap":"earlier"})}
 #[test]fn recurrence_bounds_and_unknown_fields_fail_closed(){assert!(request(&args()).is_ok());for(k,v)in[("count",json!(1001)),("limit",json!(201)),("interval",json!(0)),("frequency",json!("secondly")),("gap",json!("guess")),("timezone",json!("../bad?")),("duration_days",json!(2)),("rrule",json!("FREQ=DAILY")),("start",json!("😀😀"))]{let mut a=args();a[k]=v;assert!(request(&a).is_err(),"{k}");}}
 #[test]fn recurrence_reply_rejects_private_fields_and_out_of_window_intervals(){let a=args();let good=json!({"timezone":"America/Chicago","occurrences":[{"start_ms":1000,"end_ms":2000,"local_start":"1970-01-01T00:00:01","local_end":"1970-01-01T00:00:02","offset_seconds":0,"end_offset_seconds":0}],"truncated":false});assert!(reply(&a,&good).is_ok());let mut bad=good.clone();bad["path"]=json!("private");assert!(reply(&a,&bad).is_err());let mut bad=good.clone();bad["occurrences"][0]["end_ms"]=json!(500);assert!(reply(&a,&bad).is_err());}
}
