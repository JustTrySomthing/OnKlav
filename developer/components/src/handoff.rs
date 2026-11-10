//! User-reviewed Android handoffs. A successful reply means chooser opened, not
//! that another application completed a save/send. Private handles never leave OnKlav.
use crate::*;
pub fn handlers(operation:&str,mime:&str,then:&str)->Value{effect("handoff.handlers",json!({"operation":operation,"mime":mime}),then)}
pub fn open(id:&str,then:&str)->Value{effect("handoff.file",json!({"operation":"view","id":id}),then)}
pub fn share(id:&str,then:&str)->Value{effect("handoff.file",json!({"operation":"share","id":id}),then)}
pub fn text(text:&str,then:&str)->Value{effect("handoff.text",json!({"text":text}),then)}
#[cfg(test)]mod tests{use super::*;#[test]fn typed_helpers_never_supply_paths_receivers_or_owners(){let f=share("aabb","done");assert_eq!(f["service"],"handoff.file");assert_eq!(f["args"],json!({"operation":"share","id":"aabb"}));assert_eq!(handlers("view","application/pdf","done")["args"]["mime"],"application/pdf");assert_eq!(text("Hi","done")["args"],json!({"text":"Hi"}));}}
