//! Themed GUI builders and reusable controllers. The host owns rendering and OS access.
pub use onklav_app::{effect,frame,node,plain,serde_json};
#[doc(hidden)]pub use onklav_app as __abi;
/// Every app uses the same host-rendered permission guidance automatically.
#[macro_export]
macro_rules! app {($step:path)=>{
 fn __onklav_recovery_step(input:$crate::serde_json::Value)->$crate::serde_json::Value{$crate::permissions::with_recovery(input,$step)}
 $crate::__abi::app!(__onklav_recovery_step);
};}
use serde_json::{json,Value};
pub mod reference_apps;
pub mod files;
pub mod apps;
pub mod forms;
pub mod form_schema;
pub mod form_values;
pub mod records;
pub mod secrets;
pub mod camera;
pub mod notifications;
pub mod permissions;
pub mod collection;
pub mod collections;
pub mod blobs;
pub mod http;
pub mod accounts;
pub mod jobs;
pub mod downloads;
pub mod media;
pub mod reading;
pub mod playlists;
pub mod calendar;
pub mod navigation;
pub mod localization;
pub mod status;
pub type Node=Value;
pub fn label(id:&str,text:&str)->Node{node("label",id,text)}
pub fn heading(id:&str,text:&str)->Node{let mut n=label(id,text);n["strong"]=json!(true);n["heading"]=json!(true);n}
pub fn button(id:&str,text:&str)->Node{node("button",id,text)}
pub fn primary_button(id:&str,text:&str)->Node{let mut n=button(id,text);n["primary"]=json!(true);n}
pub fn disabled(mut n:Node)->Node{n["enabled"]=json!(false);n}
pub fn field(id:&str,hint:&str,value:&str)->Node{let mut n=node("field",id,"");n["hint"]=json!(hint);n["value"]=json!(value);n["limit"]=json!(80);n}
pub fn editor(id:&str,hint:&str,value:&str)->Node{let mut n=field(id,hint,value);n["kind"]=json!("editor");n["limit"]=json!(100000);n}
pub fn record(id:&str,title:&str,detail:&str)->Node{let mut n=node("record",id,title);n["detail"]=json!(detail);n}
pub fn icon(id:&str,text:&str,symbol:&str)->Node{let mut n=node("icon",id,text);n["icon"]=json!(symbol);n}
pub fn toggle(id:&str,text:&str,checked:bool)->Node{let mut n=node("toggle",id,text);n["checked"]=json!(checked);n}
pub fn choices(kind:&str,id:&str,text:&str,value:&str,options:&[(&str,&str)])->Node{assert!(kind=="select"||kind=="chips");let mut n=node(kind,id,text);n["value"]=json!(value);n["options"]=json!(options.iter().map(|(v,t)|json!({"value":v,"text":t})).collect::<Vec<_>>());n}
pub fn speech(id:&str,text:&str)->Node{let mut n=node("speech",id,"");n["value"]=json!(plain(text,50000));n}
pub fn speech_settings(id:&str)->Node{node("speech_settings",id,"")}
pub fn web(id:&str,url:&str,tab:&str,on_change:&str,seq:u64)->Node{let mut n=node("web",id,"");n["url"]=json!(url);n["tab"]=json!(tab);n["on_change"]=json!(on_change);n["seq"]=json!(seq);n["command"]=json!("load");n}
pub fn canvas(id:&str,document:Value,tool:&str,color:u32,width:u8,on_change:&str)->Node{let mut n=node("canvas",id,"");n["document"]=document;n["tool"]=json!(tool);n["color"]=json!(color);n["width"]=json!(width.clamp(1,12));n["on_change"]=json!(on_change);n}
pub fn image_record(id:&str,document:&str)->Node{let mut n=node("image_record",id,"");n["document"]=json!(document);n}
pub fn slider(id:&str,text:&str,value:u8)->Node{let mut n=node("slider",id,text);n["value"]=json!(value.clamp(1,12));n}
pub fn palette(id:&str,color:u32)->Node{let mut n=node("palette",id,"");n["value"]=json!(color);n}
pub fn voice_capture(id:&str,peer:&str,on_change:&str)->Node{let mut n=node("voice_capture",id,"");n["peer"]=json!(peer);n["on_change"]=json!(on_change);n}
pub fn voice_clip(id:&str,clip:&str)->Node{let mut n=node("voice_clip",id,"");n["clip"]=json!(clip);n}
pub fn voice_dial(id:&str)->Node{let mut n=disabled(node("voice_dial",id,""));n["phase"]=json!("offline");n}
pub fn media_record(id:&str,text:&str,url:&str,video:&str,time:u64)->Node{let mut n=node("media_record",id,text);n["url"]=json!(url);n["video"]=json!(video);n["time"]=json!(time);n}
/// Fluent, host-supported properties. Styling remains the user's theme.
pub trait NodeExt{
 fn enabled(self,value:bool)->Self;
 fn limit(self,value:usize)->Self;
 fn row(self,id:&str)->Self;
 fn trailing(self,id:&str,text:&str,icon:&str)->Self;
 fn long_action(self,id:&str,label:&str)->Self;
}
impl NodeExt for Node{
 fn enabled(mut self,value:bool)->Self{self["enabled"]=json!(value);self}
 fn limit(mut self,value:usize)->Self{self["limit"]=json!(value.min(100000));self}
 fn row(mut self,id:&str)->Self{self["row"]=json!(id);self}
 fn trailing(mut self,id:&str,text:&str,icon:&str)->Self{self["trailing"]=json!({"id":id,"text":text,"icon":icon});self}
 fn long_action(mut self,id:&str,label:&str)->Self{self["long_action"]=json!(id);self["long_label"]=json!(label);self}
}
pub fn empty_state(id:&str,text:&str)->Node{let mut n=label(id,text);n["center"]=json!(true);n}
/// Robust extraction for app reducers; OS callbacks remain non-gesture events.
pub struct Event<'a>{pub action:&'a str,pub value:&'a Value,pub reply:&'a Value}
impl<'a> Event<'a>{
 pub fn from_input(input:&'a Value)->Self{let e=&input["event"];Self{action:e["action"].as_str().unwrap_or(""),value:&e["value"],reply:&e["reply"]}}
 pub fn text(&self)->&str{self.value.as_str().unwrap_or("")}
 pub fn error(&self)->Option<&str>{if self.reply["ok"]==false{Some(self.reply["error"].as_str().unwrap_or("Could not complete this action"))}else{None}}
}
pub struct Screen(Value);
impl Screen{
 pub fn new(title:&str,state:Value)->Self{Self(frame(title,state,vec![],vec![]))}
 pub fn body(mut self,n:Vec<Node>)->Self{self.0["body"]=json!(n);self}
 pub fn actions(mut self,n:Vec<Node>)->Self{self.0["actions"]=json!(n);self}
 pub fn effect(mut self,n:Value)->Self{self.0["effect"]=n;self}
 pub fn back(mut self,id:&str)->Self{self.0["back_action"]=json!(id);self}
 pub fn scroll_key(mut self,id:&str)->Self{self.0["scroll_key"]=json!(id);self}
 pub fn drawer(mut self,title:&str,close:&str,n:Vec<Node>)->Self{self.0["drawer_open"]=json!(true);self.0["drawer_title"]=json!(title);self.0["drawer_close"]=json!(close);self.0["drawer"]=json!(n);self}
 pub fn dialog(mut self,title:&str,close:&str,n:Vec<Node>)->Self{self.0["menu_open"]=json!(true);self.0["menu_title"]=json!(title);self.0["menu_close"]=json!(close);self.0["menu"]=json!(n);self}
 pub fn confirm(mut self,title:&str,text:&str,accept:&str,cancel:&str)->Self{self.0["confirm"]=json!({"title":title,"text":text,"accept":accept,"cancel":cancel,"accept_label":"Continue"});self}
 pub fn confirm_labeled(mut self,title:&str,text:&str,accept:&str,cancel:&str,label:&str)->Self{self.0["confirm"]=json!({"title":title,"text":text,"accept":accept,"cancel":cancel,"accept_label":label});self}
 pub fn scrollable(mut self)->Self{self.0["scroll_body"]=json!(true);self}
 pub fn close(mut self)->Self{self.0["close_requested"]=json!(true);self}
 pub fn finish(self)->Value{self.0}
}
pub mod services{
 use super::*;
 pub fn current(then:&str)->Value{effect("records.current",json!({}),then)}
 pub fn new_document(then:&str)->Value{effect("records.new",json!({}),then)}
 pub fn save(doc:&Value,then:&str)->Value{effect("records.save",json!({"note":doc}),then)}
 pub fn load(id:&str,then:&str)->Value{effect("records.load",json!({"id":id}),then)}
 pub fn page(query:&str,offset:usize,then:&str)->Value{effect("records.page",json!({"query":query,"offset":offset}),then)}
 pub fn delete(id:&str,then:&str)->Value{effect("records.delete",json!({"id":id}),then)}
 pub fn clipboard_read(then:&str)->Value{effect("clipboard.read",json!({}),then)}
 pub fn clipboard_write(text:&str,then:&str)->Value{effect("clipboard.write",json!({"text":text}),then)}
 pub fn share(name:&str,text:&str,then:&str)->Value{effect("share.text",json!({"name":name,"text":text}),then)}
 pub fn export(name:&str,text:&str,then:&str)->Value{effect("files.export",json!({"name":name,"text":text}),then)}
 pub fn contact(then:&str)->Value{effect("contacts.pick",json!({}),then)}
}
#[cfg(test)] mod tests{
 use super::*;
 #[test]fn host_navigation_and_dialogs(){let f=Screen::new("App",json!({})).body(vec![field("name","Name","x")]).actions(vec![icon("open","Files","folder")]).dialog("Dialog","dismiss",vec![label("message","Hello")]).confirm("Continue?","Review changes","yes","no").finish();assert_eq!(f["menu_close"],"dismiss");assert_eq!(f["confirm"]["accept"],"yes");assert!(f.get("presentation").is_none());}
 #[test]fn reference_controller_matches_included_notes(){let f=reference_apps::notes::controller("My Notes",json!({"state":null,"event":{"action":"start"}}));assert_eq!(f["title"],"My Notes");assert_eq!(f["effect"]["service"],"records.current");}
}

#[cfg(test)]mod tests_shared;

pub mod clock;

pub mod calendar_ui;

pub mod recurrence;

pub mod devices;
pub mod calendar_zoned_ui;

pub mod handoff;

pub mod reading_ui;

pub mod epub;
pub mod epub_ui;

pub mod pdf;

pub mod maps;
pub mod maps_geo;
pub mod podcasts;
pub mod radio;
pub mod email;
pub mod email_content;
pub mod email_compose;
pub mod weather;
pub mod email_transfer;
pub mod email_recovery;
pub mod email_session;
