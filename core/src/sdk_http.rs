//! Signed exact-origin policy for bounded public HTTPS requests.
use crate::{Result,sdk::NetworkPolicy};
use serde_json::Value;
fn validate_accounts(p:&NetworkPolicy)->Result<()>{
 if p.accounts.len()>4{return Err("Declare up to four account providers".into())}
 let mut ids=std::collections::BTreeSet::new();
 for a in &p.accounts{
  if a.id.is_empty()||a.id.len()>48||!a.id.as_bytes()[0].is_ascii_lowercase()||!a.id.bytes().all(|b|b.is_ascii_lowercase()||b.is_ascii_digit()||b==b'-')||!ids.insert(&a.id){return Err("Invalid or duplicate account provider identity".into())}
  if a.label.trim().is_empty()||a.label.encode_utf16().count()>60||a.label.chars().any(char::is_control)||a.client_id.is_empty()||a.client_id.len()>256||!a.client_id.bytes().all(|b|(33..=126).contains(&b)){return Err("Invalid account provider label or public client ID".into())}
  for url in [&a.authorization_endpoint,&a.token_endpoint].into_iter().chain(a.revocation_endpoint.iter()){
   let o=origin(url)?;
   if !p.origins.contains(&o)||!url.starts_with(&(o+"/"))||url.contains('?')||!url.bytes().all(|b|b.is_ascii_alphanumeric()||b":/._~-%".contains(&b)){return Err("Account endpoints must use declared canonical HTTPS origins and fixed paths".into())}
  }
  if a.scopes.is_empty()||a.scopes.len()>32||a.scopes.iter().any(|s|s.is_empty()||s.len()>128||!s.bytes().all(|b|b==33||(35..=91).contains(&b)||(93..=126).contains(&b)))||a.scopes.iter().collect::<std::collections::BTreeSet<_>>().len()!=a.scopes.len(){return Err("Declare unique bounded account scopes".into())}
  if a.api_origins.is_empty()||a.api_origins.len()>8||a.api_origins.iter().any(|o|!p.origins.contains(o))||a.api_origins.iter().collect::<std::collections::BTreeSet<_>>().len()!=a.api_origins.len(){return Err("Account API origins must be unique signed network origins".into())}
 }
 Ok(())
}
pub(crate) fn origin(value:&str)->Result<String>{
 if value.len()>4096||value.chars().any(|c|c.is_whitespace()||c.is_control()||c=='\\')||value.contains('#'){return Err("Use a bounded public HTTPS URL without fragments".into())}
 let rest=value.strip_prefix("https://").ok_or("Use HTTPS")?;let authority=rest.split(['/', '?']).next().unwrap_or("").to_ascii_lowercase();let host=authority.strip_suffix(":443").unwrap_or(&authority);
 if host.len()>253||!host.contains('.')||host.ends_with(".local")||host.ends_with(".localhost")||host.bytes().all(|b|b.is_ascii_digit()||b==b'.')||host.split('.').any(|label|label.is_empty()||label.len()>63||label.starts_with('-')||label.ends_with('-')||!label.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'-')){return Err("Use a public DNS HTTPS origin on port443".into())}Ok(format!("https://{host}"))
}
pub(crate) fn validate_policy(policy:Option<&NetworkPolicy>,declared:bool)->Result<()>{
 match(policy,declared){(None,false)=>Ok(()),(Some(p),true) if !p.origins.is_empty()&&p.origins.len()<=8=>{let mut seen=std::collections::BTreeSet::new();for value in &p.origins{if origin(value)?!=*value||!seen.insert(value){return Err("Network origins must be unique canonical HTTPS origins without paths".into())}}validate_accounts(p)?;Ok(())},_=>Err("network.http requires a signed network policy with1–8 exact origins".into())}
}
pub(crate) fn request(policy:Option<&NetworkPolicy>,args:&Value)->Result<()>{
 if args.get("response").is_some_and(|v|v!="text"&&v!="hex"){return Err("Choose text or bounded hex HTTP response".into())}
 let p=policy.ok_or("This app does not declare HTTP origins")?;let object=args.as_object().ok_or("HTTP arguments must be an object")?;
 if object.keys().any(|k|!["url","method","headers","body","account","response","upload"].contains(&k.as_str()))||args.to_string().len()>140000{return Err("Unsupported or oversized HTTP arguments".into())}
 let url=args["url"].as_str().ok_or("Choose an HTTPS URL")?;if !p.origins.contains(&origin(url)?){return Err("HTTPS origin is not declared by this signed app".into())}
 if let Some(v)=args.get("account"){crate::sdk_accounts::handle(v)?;if p.accounts.is_empty(){return Err("No signed account providers".into())}}
 let method=args["method"].as_str().unwrap_or("GET");if !["GET","HEAD","POST","PUT","PATCH","DELETE"].contains(&method){return Err("Unsupported HTTP method".into())}
 if let Some(upload)=args.get("upload"){if method!="POST"||args.get("body").is_some()||upload.as_str().is_none_or(|s|s.len()!=32||!s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))){return Err("Use one private file upload with POST and no text body".into())}}
 if let Some(body)=args.get("body"){let text=body.as_str().ok_or("HTTP body must be UTF-8 text")?;if text.len()>65536||matches!(method,"GET"|"HEAD"){return Err("HTTP body exceeds64KiB or is not allowed for this method".into())}}
 if let Some(headers)=args.get("headers"){let headers=headers.as_object().filter(|h|h.len()<=8).ok_or("Use up to8 HTTP headers")?;let mut seen=std::collections::BTreeSet::new();for(key,value)in headers{let name=key.to_ascii_lowercase();if !["accept","accept-language","content-type","if-none-match","if-modified-since","x-request-id"].contains(&name.as_str())||!seen.insert(name){return Err("Only documented noncredential HTTP headers are allowed".into())}let value=value.as_str().ok_or("HTTP headers must be text")?;if value.len()>512||!value.bytes().all(|b|(32..=126).contains(&b)){return Err("Invalid HTTP header value".into())}}}
 Ok(())
}
pub(crate) fn reply(value:&Value)->Result<()>{
 let object=value.as_object().ok_or("Invalid HTTP result")?;if object.keys().any(|k|!["status","text","hex","headers"].contains(&k.as_str()))||!(100..=599).contains(&value["status"].as_u64().unwrap_or(0)){return Err("Invalid HTTP status or result".into())}
 if let Some(binary)=value.get("hex"){let hex=binary.as_str().ok_or("Invalid HTTP binary result")?;if value.get("text").is_some()||hex.len()>524288||hex.len()%2!=0||!hex.bytes().all(|b|b.is_ascii_hexdigit()){return Err("HTTP binary result exceeds256KiB or contains invalid bytes".into())}}
 else{let text=value["text"].as_str().ok_or("Missing HTTP text result")?;if text.len()>131072||text.chars().any(|c|c.is_control()&&!['\n','\r','\t'].contains(&c)){return Err("HTTP text result exceeds limits".into())}}
 let headers=value["headers"].as_object().filter(|h|h.len()<=9).ok_or("Invalid HTTP response headers")?;
 for(key,value)in headers{if !["content-type","etag","last-modified","retry-after","cache-control","location","date","expires","age"].contains(&key.as_str())||value.as_str().is_none_or(|v|v.len()>512||!v.bytes().all(|b|(32..=126).contains(&b))){return Err("Invalid HTTP response header".into())}}
 Ok(())
}
#[cfg(test)]mod tests{
 use super::*;use serde_json::json;
 fn policy()->NetworkPolicy{NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![]}}
 fn account_policy()->NetworkPolicy{let mut p=policy();p.accounts.push(crate::sdk::AccountProvider{id:"example".into(),label:"Example account".into(),authorization_endpoint:"https://api.example.com/oauth/authorize".into(),token_endpoint:"https://api.example.com/oauth/token".into(),client_id:"public-client".into(),scopes:vec!["openid".into(),"profile".into()],api_origins:p.origins.clone(),revocation_endpoint:None});p}
 #[test]fn wrap_upload_request_rejects_paths_mixed_bodies_and_non_post(){let p=policy();let mut a=json!({"url":"https://api.example.com/upload","method":"POST","upload":"a".repeat(32)});request(Some(&p),&a).unwrap();a["body"]=json!("mixed");assert!(request(Some(&p),&a).is_err());a.as_object_mut().unwrap().remove("body");a["method"]=json!("GET");assert!(request(Some(&p),&a).is_err());a["method"]=json!("POST");a["upload"]=json!("../private");assert!(request(Some(&p),&a).is_err());}
 #[test]fn http_binary_responses_are_bounded_and_remain_signed_origin_scoped(){let p=policy();request(Some(&p),&json!({"url":"https://api.example.com/tile","response":"hex"})).unwrap();assert!(request(Some(&p),&json!({"url":"https://other.example.com/tile","response":"hex"})).is_err());assert!(request(Some(&p),&json!({"url":"https://api.example.com/tile","response":"file"})).is_err());reply(&json!({"status":200,"hex":"00ff80","headers":{"age":"10","date":"Wed, 21 Oct 2015 07:28:00 GMT","expires":"Wed, 21 Oct 2015 07:29:00 GMT"}})).unwrap();for hex in ["0".to_string(),"zz".to_string(),"00".repeat(262145)]{assert!(reply(&json!({"status":200,"hex":hex,"headers":{}})).is_err());}assert!(reply(&json!({"status":200,"text":"x","hex":"00","headers":{}})).is_err());}
 #[test]fn http_accounts_are_optional_and_cannot_widen_signed_origins(){validate_policy(Some(&policy()),true).unwrap();let p=account_policy();validate_policy(Some(&p),true).unwrap();let mut bad=p.clone();bad.accounts[0].api_origins.push("https://other.example.com".into());assert!(validate_policy(Some(&bad),true).is_err());let mut bad=p.clone();bad.accounts[0].token_endpoint="https://other.example.com/token".into();assert!(validate_policy(Some(&bad),true).is_err());}
 #[test]fn http_accounts_reject_duplicate_scopes_secrets_and_mutable_endpoints(){let p=account_policy();for url in ["http://api.example.com/token","https://api.example.com/token?redirect_uri=other","https://user@api.example.com/token"]{let mut b=p.clone();b.accounts[0].token_endpoint=url.into();assert!(validate_policy(Some(&b),true).is_err());}let mut b=p.clone();b.accounts[0].scopes.push("openid".into());assert!(validate_policy(Some(&b),true).is_err());let mut b=p.clone();b.accounts.push(b.accounts[0].clone());assert!(validate_policy(Some(&b),true).is_err());let mut v=serde_json::to_value(&p).unwrap();v["accounts"][0]["client_secret"]=json!("forbidden");assert!(serde_json::from_value::<NetworkPolicy>(v).is_err());}
 #[test]fn http_signed_origin_scope_rejects_paths_credentials_ports_and_wildcards(){for origin in ["http://api.example.com","https://api.example.com/","https://api.example.com:443","https://user@api.example.com","https://*.example.com","https://127.0.0.1","https://api.example.com:8443"]{assert!(validate_policy(Some(&NetworkPolicy{origins:vec![origin.into()],accounts:vec![]}),true).is_err(),"{origin}");}validate_policy(Some(&policy()),true).unwrap();assert!(validate_policy(None,true).is_err());assert!(validate_policy(Some(&policy()),false).is_err());}
 #[test]fn http_requests_cannot_escape_origins_or_smuggle_credentials(){let p=policy();request(Some(&p),&json!({"url":"https://API.EXAMPLE.COM:443/items?q=test"})).unwrap();for url in ["https://api.example.com.evil.org/","https://api.example.com@evil.org/","https://other.example.com/","https://api.example.com/#fragment"]{assert!(request(Some(&p),&json!({"url":url})).is_err());}for header in ["Authorization","Cookie","Host","Proxy-Authorization","X-Api-Key"]{assert!(request(Some(&p),&json!({"url":"https://api.example.com/","headers":{(header):"secret"}})).is_err());}assert!(request(Some(&p),&json!({"url":"https://api.example.com/","method":"GET","body":"x"})).is_err());}
 #[test]fn http_response_bounds_exclude_cookies_and_unbounded_text(){reply(&json!({"status":404,"text":"Missing","headers":{"content-type":"text/plain"}})).unwrap();assert!(reply(&json!({"status":200,"text":"ok","headers":{"set-cookie":"secret"}})).is_err());assert!(reply(&json!({"status":200,"text":"x".repeat(131073),"headers":{}})).is_err());}
}
