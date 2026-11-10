//! Typed Android handoffs. No guest filesystem paths, URIs, packages or owners.
use crate::Result;
use serde_json::Value;
pub fn supported(s:&str)->bool{matches!(s,"handoff.handlers"|"handoff.file"|"handoff.text")}
pub fn mime(s:&str)->bool{s.len()<=100&&s.split('/').count()==2&&s.split('/').all(|p|!p.is_empty()&&p.bytes().all(|b|b.is_ascii_lowercase()||b.is_ascii_digit()||b".+-".contains(&b)))}
pub fn request(s:&str,a:&Value)->Result<()>{
 let keys:&[&str]=match s{"handoff.handlers"=>&["operation","mime"],"handoff.file"=>&["operation","id"],"handoff.text"=>&["text"],_=>return Err("Unsupported handoff".into())};
 let o=a.as_object().ok_or("Handoff arguments must be an object")?;if o.len()!=keys.len()||o.keys().any(|k|!keys.contains(&k.as_str())){return Err("Unexpected handoff arguments".into())}
 if s!="handoff.text"&&!matches!(a["operation"].as_str(),Some("view"|"share")){return Err("Choose view or share".into())}
 if s=="handoff.handlers"&&a["mime"].as_str().is_none_or(|s|!mime(s)){return Err("Choose an exact MIME type".into())}
 if s=="handoff.file"&&a["id"].as_str().is_none_or(|s|s.len()!=32||!s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))){return Err("Choose a private file handle".into())}
 if s=="handoff.text"&&a["text"].as_str().is_none_or(|s|s.is_empty()||s.encode_utf16().count()>50000||s.chars().any(|c|c.is_control()&&!matches!(c,'\n'|'\r'|'\t'))){return Err("Share1–50000 text units".into())}Ok(())
}
pub fn reply(s:&str,v:&Value)->Result<()>{let keys=if s=="handoff.handlers"{vec!["available"]}else{vec!["handed_off","completed"]};let o=v.as_object().ok_or("Invalid handoff response")?;if o.len()!=keys.len()||o.keys().any(|k|!keys.contains(&k.as_str()))||keys.iter().any(|k|!v[*k].is_boolean())||s!="handoff.handlers"&&(v["handed_off"]!=true||v["completed"]!=false){return Err("Invalid handoff result; external completion is unknown".into())}Ok(())}
#[cfg(test)]mod tests{use super::*;use serde_json::json;
 #[test]fn typed_requests_reject_authority_and_inventory_injection(){assert!(request("handoff.handlers",&json!({"operation":"view","mime":"application/pdf"})).is_ok());for mime in ["*/*","text/*","text/plain; charset=utf-8","text/plain\n","file:///secret"]{assert!(request("handoff.handlers",&json!({"operation":"view","mime":mime})).is_err())}assert!(request("handoff.file",&json!({"operation":"share","id":"a".repeat(32),"owner":"another"})).is_err());assert!(request("handoff.file",&json!({"operation":"execute","id":"a".repeat(32)})).is_err());assert!(reply("handoff.handlers",&json!({"available":true,"packages":[]})).is_err());assert!(reply("handoff.file",&json!({"handed_off":true,"completed":true})).is_err());}
}
