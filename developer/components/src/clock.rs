//! Permission-free read-only device clock, timezone and display preferences.
use crate::*;
pub fn read(then: &str) -> Value { effect("clock.read", json!({}), then) }
