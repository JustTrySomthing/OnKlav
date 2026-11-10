//! Bounded host timezone arithmetic, independent of calendar permissions and scheduling.
use crate::*;
pub struct Rule(Value);
impl Rule{
 pub fn timed(start:&str,timezone:&str,frequency:&str,duration_seconds:u64,count:u16)->Self{Self(json!({"start":start,"timezone":timezone,"frequency":frequency,"interval":1,"duration_seconds":duration_seconds,"count":count,"gap":"skip","overlap":"earlier","limit":200}))}
 pub fn all_day(date:&str,timezone:&str,frequency:&str,duration_days:u16,count:u16)->Self{let mut rule=Self::timed(&format!("{date}T00:00:00"),timezone,frequency,1,count);rule.0.as_object_mut().unwrap().remove("duration_seconds");rule.0["duration_days"]=json!(duration_days);rule}
 pub fn interval(mut self,value:u16)->Self{self.0["interval"]=json!(value);self}
 pub fn dst(mut self,gap:&str,overlap:&str)->Self{self.0["gap"]=json!(gap);self.0["overlap"]=json!(overlap);self}
 pub fn limit(mut self,value:u16)->Self{self.0["limit"]=json!(value);self}
 pub fn between(mut self,start_ms:u64,end_ms:u64,then:&str)->Value{self.0["window_start_ms"]=json!(start_ms);self.0["window_end_ms"]=json!(end_ms);effect("calendar.expand",self.0,then)}
}

#[cfg(test)]mod tests{
 use super::*;
 #[test]fn builders_keep_dst_choices_and_civil_duration_explicit(){let r=Rule::all_day("2026-03-08","America/Chicago","weekly",1,5).interval(2).between(0,2000000000000,"done");assert_eq!(r["service"],"calendar.expand");assert_eq!(r["args"]["start"],"2026-03-08T00:00:00");assert!(r["args"].get("duration_seconds").is_none());assert_eq!(r["args"]["duration_days"],1);assert_eq!(r["args"]["gap"],"skip");let r=Rule::timed("2026-11-01T01:30:00","America/Chicago","daily",3600,2).dst("forward","later").limit(1).between(0,2000000000000,"done");assert_eq!(r["args"]["overlap"],"later");assert_eq!(r["args"]["limit"],1);}
 #[test]fn reminders_helpers_do_not_export_host_owners_or_make_delivery_claims(){let r=notifications::schedule_at("one","Title","Body",2000000000000,"done");assert_eq!(r["service"],"notifications.schedule_at");assert!(r["args"].get("owner").is_none());assert_eq!(notifications::pending("done")["args"],json!({}));assert_eq!(notifications::status("one","done")["args"],json!({"key":"one"}));}
}
