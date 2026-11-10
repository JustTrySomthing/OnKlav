//! Unreleased notifications. The host delivers reminders without running guest code.
//! Delivery is inexact and may be delayed by Android; scheduling is not a delivery receipt.
//! Requires notifications.post. Posting/scheduling also require an explicit tap
//! and Android notification access; read-only queries never prompt.
use crate::*;
pub fn show(key:&str,title:&str,text:&str,then:&str)->Value{effect("notifications.show",json!({"key":key,"title":title,"text":text}),then)}
pub fn cancel(key:&str,then:&str)->Value{effect("notifications.cancel",json!({"key":key}),then)}

/// Schedule one reminder60 seconds to30 days from now. The same key replaces it.
/// Cancel removes both the scheduled reminder and any visible notification.
pub fn schedule(key:&str,title:&str,text:&str,delay_seconds:u64,then:&str)->Value{effect("notifications.schedule",json!({"key":key,"title":title,"text":text,"delay_seconds":delay_seconds}),then)}

/// Absolute Unix milliseconds, at least1 second and at most366 days ahead.
/// Host-owned inexact delivery; reply includes due_ms and exact:false.
pub fn schedule_at(key:&str,title:&str,text:&str,at_ms:u64,then:&str)->Value{effect("notifications.schedule_at",json!({"key":key,"title":title,"text":text,"at_ms":at_ms}),then)}
/// Read-only status, including pending/overdue/registered and Android access.
/// pending:false means no queued record; it is not a delivery receipt.
pub fn status(key:&str,then:&str)->Value{effect("notifications.status",json!({"key":key}),then)}
pub fn pending(then:&str)->Value{effect("notifications.pending",json!({}),then)}

/// Bounded repeat policy; missed="latest" posts once after recovery, "skip" skips
/// occurrences more than five minutes late. count includes the first occurrence.
pub fn schedule_with_policy(key:&str,title:&str,text:&str,at_ms:u64,repeat_seconds:u64,count:u32,missed:&str,snooze_seconds:u64,then:&str)->Value{
 effect("notifications.schedule_at",json!({"key":key,"title":title,"text":text,"at_ms":at_ms,"repeat_seconds":repeat_seconds,"repeat_count":count,"missed":missed,"snooze_seconds":snooze_seconds}),then)
}
