//! Unreleased native vault API. Requires storage.secrets and an explicit button
//! action. Never place returned credentials in UI, diagnostics or ordinary records.
use crate::*;
pub fn get(key:&str,then:&str)->Value{effect("secrets.get",json!({"key":key}),then)}
pub fn set(key:&str,value:&str,then:&str)->Value{effect("secrets.set",json!({"key":key,"value":value}),then)}
pub fn delete(key:&str,then:&str)->Value{effect("secrets.delete",json!({"key":key}),then)}
