//! Strict guest account requests and public results; tokens remain host-owned.
use crate::{Result,sdk::NetworkPolicy};
use serde_json::Value;
pub(crate) fn handle(v:&Value)->Result<()>{let h=v.as_str().ok_or("Choose an account handle")?;if h.len()!=36||uuid::Uuid::parse_str(h).is_err(){return Err("Invalid account handle".into())}Ok(())}
pub(crate) fn request(policy:Option<&NetworkPolicy>,service:&str,args:&Value)->Result<()>{
 let p=policy.ok_or("Missing signed account providers")?;
 let a=args.as_object().ok_or("Account arguments must be an object")?;
 match service {
  "accounts.connect" if a.len()==1&&args["provider"].as_str().is_some_and(|id|p.accounts.iter().any(|x|x.id==id))=>Ok(()),
  "accounts.list" if a.is_empty()=>Ok(()),
  "accounts.refresh"|"accounts.disconnect" if a.len()==1&&a.contains_key("account")=>handle(&args["account"]),
  _=>Err("Invalid or unsupported account request".into())
 }
}
fn account(v:&Value,policy:&NetworkPolicy)->Result<()>{
 let a=v.as_object().ok_or("Invalid account result")?;
 let handle=v["account"].as_str().ok_or("Missing account handle")?;
 if a.len()!=3||!a.keys().all(|k|["account","provider","label"].contains(&k.as_str()))||uuid::Uuid::parse_str(handle).is_err()||handle.len()!=36{return Err("Invalid public account result".into())}
 let provider=policy.accounts.iter().find(|p|v["provider"]==p.id).ok_or("Unknown account provider")?;
 if v["label"]!=provider.label{return Err("Account provider label mismatch".into())}Ok(())
}
pub(crate) fn reply(policy:Option<&NetworkPolicy>,service:&str,v:&Value)->Result<()>{
 let p=policy.ok_or("Missing signed account providers")?;
 if service=="accounts.disconnect"{return if v.as_object().is_some_and(|a|a.len()==2)&&v["disconnected"].is_boolean()&&v["revocation_submitted"].is_boolean(){Ok(())}else{Err("Invalid disconnect result".into())}}
 if service=="accounts.list"{let a=v.as_array().filter(|a|a.len()<=8).ok_or("Invalid account list")?;for v in a{account(v,p)?;}Ok(())}else{account(v,p)}
}
#[cfg(test)]mod tests{
 use super::*;use serde_json::json;
 #[test]fn accounts_strict_requests_and_secret_free_results(){
  let p=crate::sdk::NetworkPolicy{origins:vec!["https://api.example.com".into()],accounts:vec![crate::sdk::AccountProvider{id:"example".into(),label:"Example".into(),authorization_endpoint:"https://api.example.com/auth".into(),token_endpoint:"https://api.example.com/token".into(),client_id:"public".into(),scopes:vec!["profile".into()],api_origins:vec!["https://api.example.com".into()],revocation_endpoint:None}]};
  request(Some(&p),"accounts.connect",&json!({"provider":"example"})).unwrap();assert!(request(Some(&p),"accounts.connect",&json!({"provider":"other"})).is_err());assert!(request(Some(&p),"accounts.connect",&json!({"provider":"example","url":"https://evil.example"})).is_err());
  let mut v=json!({"account":uuid::Uuid::new_v4().to_string(),"provider":"example","label":"Example"});reply(Some(&p),"accounts.connect",&v).unwrap();v["access_token"]=json!("secret");assert!(reply(Some(&p),"accounts.connect",&v).is_err());assert!(reply(Some(&p),"accounts.list",&json!([v])).is_err());
 }
}
