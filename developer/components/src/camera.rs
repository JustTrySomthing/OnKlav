//! Unreleased native still-photo capture. Requires camera.capture and a tap.
//! Returns opaque Files image metadata. Viewing it also needs files.manage.
use crate::*;
pub fn capture(then:&str)->Value{effect("camera.capture",json!({}),then)}
