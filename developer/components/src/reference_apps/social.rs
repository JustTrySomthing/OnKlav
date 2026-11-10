//! Reserved Social app. No feed, storage, networking or additional functionality.
use crate::{frame,node,serde_json::Value};
fn step(_:Value)->Value {
    frame("Social",Value::Null,vec![],vec![node("label","hello","Hello World")])
}


/// Shared controller using the included app behavior and the caller's title.
pub fn controller(title:&str,input:Value)->Value{let mut f=step(input);f["title"]=crate::serde_json::json!(title);f}
