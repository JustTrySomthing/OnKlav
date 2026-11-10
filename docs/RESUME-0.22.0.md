# Resume 0.22.0 — incoming Walkie consent and permission foundation

Source 0.22.0-dev/code29; signed Walkie v6. Source/SDK checkpoint only. No APK
or mobile ABI rebuild. Historical last verified host APK remains 0.14.0.
TODO 3b is still partial: this does not enable incoming SMS, sending or live voice.

Production: default-off communications.walkie is declared/reviewed by the normal
SDK installer and Settings > Apps permission manager. Rust checks it on guest
Connect/Accept and trusted session commands except state/end; guest actions need
a direct button gesture. Grant changes invalidate SDK tokens, cancel private
sessions/proofs; revocation is saved even if old session data is unreadable.
Adding communications permission fails closed if session cancellation cannot be
persisted. Package updates/removal also cancel pending state. Contact and microphone/playback
permissions stay separate. No guest can pass a ready flag or host policy allow.
The production transport gate remains false for every probe/receive/send call.

Private incoming machine (fixture transport only): a strict !OKW1 P probe reserves
one C reply, bounded to 30/day globally and a process-monotonic 2s between replies to the same phone/SIM.
Quota survives cancellation/process restart; pending proof expires after 45s or
on restart. Recent 64 probe nonces prevent repeated replies in a bounded replay
window. Only one pending verification/current call is allowed. Claim is persisted
before exposing the reply, accepted send confirmation is required before matching
I can open an invitation. Exact phone/SIM/nonce and host allow-policy required.
Unsolicited, malformed, legacy, foreign, blocked or expired input cannot join.
Duplicate I does not extend its 45s deadline. Accept queues A, never marks a call
connected until the matching host send outcome; Decline queues D in the staged
machine. Ambiguous sends and stale callbacks do not retry or connect.

SDK UI: scoped snapshot -> signed Wasm invitation dialog, actual sender number,
18sp status and side-by-side Decline/Accept. Permission-off and closed-transport
Accept leave the invitation unjoined. Local Decline dismisses/ends it without
claiming a response was sent. Expired/stale IDs cannot decline a replacement.
Production cannot yet create an incoming invitation because receiver transport
is disabled. Tests seed this state only through cfg(test) fixture helpers, never
an exported debug command or UI simulator. No actual contact/message is sent.

Remaining before enable: actual carrier/Android receiver/send/result broker,
OS role/SIM and OnKlav blocked-policy routing, host-owned confirmation tied to the
real sender, lifecycle/background timers, simultaneous-invite policy, independent
protocol review and two-phone acceptance. Correlation is not cryptographic peer
identity. Do not flip TRANSPORT_READY on by itself. MMS_READY stays false and
default SMS role is unchanged. Connected floor/audio/warm-up/beeps/receipts and
TODO 5 remain open. No device performance/quality claim.

Tests exercise fixtures for successful incoming sequence, explicit consent,
binding/replay/timeout/restart, daily/cooldown limits, blocked/malformed/busy input,
private host grant checks, real signed Wasm dialog/local decline and revocation.
Full production paths and exact final counts are in source-verification.json.

Final verification: 78 Rust +50 Kotlin host tests passed (128), including the
current full app/shared host run. Incoming success paths are fixture transport;
signed Wasm/JNI permissions/dialog and existing contact/audio flows are exercised.
No APK/mobile ABI rebuild, real incoming invitation/send, handset or live voice
acceptance. Full TODO 3b remains partial. Final XML/logs are in evidence/v0.22.0.
