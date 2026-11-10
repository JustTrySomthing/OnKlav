//! Shared host player, queue, focus, resume, service and lock-screen controls.
use crate::*;
pub fn file(id:&str,title:&str)->Value{json!({"file":id,"title":title})}
pub fn stream(url:&str,title:&str)->Value{json!({"url":url,"title":title})}
pub fn account(mut item:Value,handle:&str)->Value{item["account"]=json!(handle);item}
pub fn play(queue:Vec<Value>,then:&str)->Value{effect("media.play",json!({"queue":queue}),then)}
pub fn status(then:&str)->Value{effect("media.status",json!({}),then)}
pub fn control(operation:&str,then:&str)->Value{effect("media.control",json!({"operation":operation}),then)}
pub fn seek(position_ms:u64,then:&str)->Value{effect("media.control",json!({"operation":"seek","position_ms":position_ms}),then)}
pub fn speed(value:f64,then:&str)->Value{effect("media.control",json!({"operation":"speed","speed":value}),then)}
/// Themed native video surface, play/seek/queue/speed controls and live progress.
pub fn player(id:&str)->Node{node("media_player",id,"")}

/// Stop the current owned player after0–120 minutes (0 cancels).
pub fn sleep(minutes:u8,then:&str)->Value{effect("media.control",json!({"operation":"sleep","minutes":minutes}),then)}
