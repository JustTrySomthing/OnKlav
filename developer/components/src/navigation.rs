//! Bounded serialized routes for app-specific screens. Native Back/chrome stay host-owned.
use crate::serde_json::{json,Value};
pub struct Navigation{routes:Vec<String>}
impl Navigation{
 pub fn restore(state:&Value,home:&str)->Self{let mut routes=state.as_array().map(|v|v.iter().filter_map(Value::as_str).filter(|s|!s.is_empty()&&s.len()<=96).take(16).map(str::to_owned).collect::<Vec<_>>()).unwrap_or_default();if routes.is_empty(){routes.push(if !home.is_empty()&&home.len()<=96{home.to_owned()}else{"home".into()})}Self{routes}}
 pub fn current(&self)->&str{self.routes.last().map(String::as_str).unwrap_or("")}
 pub fn go(&mut self,route:&str)->bool{if self.current()==route{return true}if route.is_empty()||route.len()>96||self.routes.len()>=16{return false}self.routes.push(route.into());true}
 pub fn back(&mut self)->bool{if self.routes.len()>1{self.routes.pop();true}else{false}}
 pub fn state(&self)->Value{json!(self.routes)}
}
