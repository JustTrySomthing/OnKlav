//! Shared status and pagination patterns; callers retain their controller state.
use crate::*;
pub fn message(id:&str,text:&str)->Node{let mut n=label(id,text);n["status"]=json!(true);n}
pub fn loading(id:&str,text:&str)->Vec<Node>{vec![message(id,text)]}
pub fn error(id:&str,text:&str,retry:&str,retry_label:&str)->Vec<Node>{vec![message(id,text),button(retry,retry_label)]}
pub fn empty(id:&str,text:&str,action:&str,action_label:&str)->Vec<Node>{vec![empty_state(id,text),button(action,action_label)]}
/// IDs are <id>.previous and <id>.next. Handle them in the app's reducer;
/// rendering a page never initiates a request or discards a draft.
pub fn page_actions(id:&str,offset:u64,total:u64,page_size:u64)->Vec<Node>{
 let size=page_size.clamp(1,40);
 vec![if offset==0{disabled(button(&format!("{id}.previous"),"Previous"))}else{button(&format!("{id}.previous"),"Previous")},
 if offset.saturating_add(size)>=total{disabled(button(&format!("{id}.next"),"Next"))}else{button(&format!("{id}.next"),"Next")}]
}
