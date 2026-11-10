//! Host-run, durable bounded HTTPS GET/HEAD jobs; never background Wasm execution.
use crate::*;
pub fn schedule(key:&str,request:Value,delay_seconds:u64,then:&str)->Value{let args=request.get("args").cloned().unwrap_or(request);effect("jobs.schedule",json!({"key":key,"request":args,"delay_seconds":delay_seconds}),then)}
pub fn status(id:&str,then:&str)->Value{effect("jobs.status",json!({"id":id}),then)}
pub fn cancel(id:&str,then:&str)->Value{effect("jobs.cancel",json!({"id":id}),then)}
pub fn list(then:&str)->Value{effect("jobs.list",json!({}),then)}
