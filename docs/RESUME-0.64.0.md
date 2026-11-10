# 0.64.0 — concrete lifecycle fixes, source only

User asked for recovery, notification, permission and app-removal fixes. No APKs.
Kotlin-only changes reuse exact current 0.63 host JNI and signed SDK/game proofs;
mobile rebuild remains deferred. Last delivered APK is still 0.61.0/code69.

MessageNotifications: thread/reminder/attention tags isolate IDs; distinct data
URIs isolate content/read/reply PendingIntents even for colliding Long hashes.
Reminder cancellation uses the new tag plus old untagged ID for compatibility.
Blocked incoming senders cancel an existing thread notification. Receiver ignores
unknown actions and blank remote replies; Mark Read checks role + READ_SMS before
and between SMS/MMS updates, then updates local state. Successful queued replies
with later read failures show Reply Queued, preserving unread without implying
send failure/retry. Provider multi-table writes are not claimed transactional.

SdkNativeData: profile cleanup subtracts confirmed results from the latest queue
under the carrier lock, retaining reentrant/new cleanup work. DeleteProfile false
is complete only if the provider confirms absence; rejected extant profiles stay
pending. Walkie cleanup uses a generation acknowledgement. Long profile/job cleanup
runs outside carrier lock, preserving existing lock order. Removal rotation and
durable journals retain their prior idempotent recovery paths.

Tests: new focused RecoveryNotificationTest plus existing TransportRecoveryTest.
The initial 11 checks passed before adding explicit queued-reply and missing-
profile edge coverage. Current source-verification.json records final results.
No full Rust/UI/emulator loop, no APK and no phone/carrier acceptance claimed.
Built-in app-document backup stays closed; this is Group 3 progress, not whole
port completion. Keep no-APK preference until user explicitly changes it.

Final: 6/6 notification/permission/cleanup cases and 7/7 existing transport
recovery cases pass (13 distinct). Final targeted invocation took 126.8s; no broad
suite or Rust rerun. Transport evidence predates only the disjoint queued-reply
and profile-provider refinements; final focused cases cover those refinements.
