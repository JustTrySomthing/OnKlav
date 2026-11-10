//! Unreleased bounded HTTPS requests to signed origins. Requires network.http.
use crate::*;
pub fn get(url:&str,then:&str)->Value{effect("http.request",json!({"url":url,"method":"GET"}),then)}
pub fn request(url:&str,method:&str,headers:Value,body:Option<&str>,then:&str)->Value{
 let mut args=json!({"url":url,"method":method,"headers":headers});if let Some(text)=body{args["body"]=json!(text);}effect("http.request",args,then)
}
pub fn post_json(url:&str,data:&Value,then:&str)->Value{request(url,"POST",json!({"Content-Type":"application/json"}),Some(&data.to_string()),then)}
/// HTTP error statuses are valid responses; handle status before interpreting data.
pub fn parse_json(response:&Value)->Result<Value,String>{
 let text=response["text"].as_str().ok_or("Missing HTTP text response")?;
 if text.len()>131072{return Err("HTTP response exceeds128KiB".into())}
 serde_json::from_str(text).map_err(|_|"Service returned invalid JSON".into())
}
/// Bounded binary GET (up to256KiB) to an existing signed HTTPS origin.
pub fn get_binary(url:&str,headers:Value,then:&str)->Value{effect("http.request",json!({"url":url,"method":"GET","headers":headers,"response":"hex"}),then)}
/// Stream one immutable private blob through the signed HTTPS broker; storage.private required.
pub fn upload(url:&str,file:&str,mime:&str,account:&str,then:&str)->Value{effect("http.request",json!({"url":url,"method":"POST","upload":file,"headers":{"Content-Type":mime},"account":account}),then)}
