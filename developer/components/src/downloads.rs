//! Validated resumable transfers directly into private immutable files.
use crate::*;
pub struct Download(Value);
impl Download{
 pub fn new(key:&str,url:&str,name:&str,mime:&str)->Self{Self(json!({"key":key,"url":url,"name":name,"mime":mime,"max_bytes":67108864}))}
 pub fn max_bytes(mut self,n:u64)->Self{self.0["max_bytes"]=json!(n);self}
 pub fn sha256(mut self,hash:&str)->Self{self.0["sha256"]=json!(hash);self}
 pub fn cache(mut self,value:bool)->Self{self.0["cache"]=json!(value);self}
 pub fn unmetered(mut self,value:bool)->Self{self.0["unmetered"]=json!(value);self}
 pub fn charging(mut self,value:bool)->Self{self.0["charging"]=json!(value);self}
 pub fn account(mut self,handle:&str)->Self{self.0["account"]=json!(handle);self}
 pub fn start(self,then:&str)->Value{effect("downloads.start",self.0,then)}
}
