//! OnKlav app ABI v1. Guest code has no OS imports; UI/services cross a JSON boundary.
pub use serde_json;
use serde_json::{json,Value};
pub fn node(kind:&str,id:&str,text:&str)->Value{json!({"kind":kind,"id":id,"text":text})}
pub fn effect(service:&str,args:Value,then:&str)->Value{json!({"service":service,"args":args,"then":then})}
pub fn frame(title:&str,state:Value,actions:Vec<Value>,body:Vec<Value>)->Value{json!({"title":title,"state":state,"actions":actions,"body":body,"drawer":[],"menu":[],"effect":null})}
pub fn plain(s:&str,max:usize)->String{let mut n=0;s.chars().filter(|c|!c.is_control()||*c=='\n'||*c=='\t').take_while(|c|{n+=c.len_utf16();n<=max}).collect()}
/// The host drops the complete bounded Wasm instance after each call. These ABI
/// buffers intentionally live until then; packages do not retain host pointers.
#[macro_export]
macro_rules! app {
 ($step:path) => {
  #[no_mangle] pub extern "C" fn onklav_alloc(len:u32)->u32 {let mut b=vec![0u8;len as usize];let p=b.as_mut_ptr();std::mem::forget(b);p as u32}
  #[no_mangle] pub unsafe extern "C" fn onklav_step(ptr:u32,len:u32)->u64 {
   let input=std::slice::from_raw_parts(ptr as *const u8,len as usize);
   let request:$crate::serde_json::Value=$crate::serde_json::from_slice(input).expect("Invalid host request");
   let mut output=$crate::serde_json::to_vec(&$step(request)).expect("Invalid app response");
   let out=output.as_mut_ptr() as u32;let size=output.len() as u32;std::mem::forget(output);((out as u64)<<32)|size as u64
  }
 };
}
