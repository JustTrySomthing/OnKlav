# Current Walkie checkpoint — package13 / host0.68

The signed SDK package keeps its publisher and saved preferences/recipients.
Panel title uses explicit theme onSurface. Saved/contact-handoff names replace
numbers when available, in both the panel and connection-error dialog. There is
no extra number row for a named recipient. Floating panels/dialogs use centered
motion; drawers/pages continue sliding. Fresh Walkie host/presentation checks
pass; physical two-phone/carrier acceptance remains open. See RESUME-0.68.0.md.
The sections below retain the implementation and historical evidence.

# Walkie-Talkie — conversion started, not a finished app

Charles requested the Social placeholder first, then the next app conversion.
Walkie-Talkie is the next app. Continuation within Walkie is already authorized;
no additional app-start approval is needed for its next bounded chunk.

## Reference reviewed

Inner Circle 2.2.85 WalkieDialog/Service, Wire, Floor, Audio, CaptureRules,
Preferences, Journal, Timeout and Result were inspected as read-only behavior
references. The voice flow is tap-to-talk using SMS control frames and MMS audio,
not an internet streaming call. The original UI has a 190dp dial, verification/
connect/leave-message paths, auto-play/auto-send toggles, Send/Discard review,
visible waiting/listen states and recent clip/history controls.

## Implemented first chunk

`core/src/walkie.rs` is a fresh Rust implementation of the control codec,
microphone-ownership transitions, capture/autoplay rules and structural AMR/
AMR-WB validation. Typed enums replace stringly controlled states; frame/session/
serial/payload bounds are enforced. Encoding revalidates its result. Floor
transitions reject stale epochs, conflicting equal-epoch ownership, stale guest
releases and cancelled request replay. Occupied ownership is never stolen by a
timer. Counter exhaustion fails before mutation rather than wrapping.

AMR sizes derive from speech-bit counts and octet storage framing, checked
against RFC 4867 (https://www.rfc-editor.org/rfc/rfc4867.html) and the original
AMR/AMR-WB reference format. Files are bounded to 65,536 bytes and 650 frames;
truncated/reserved/header-invalid frames and unsupported MIME types fail closed.
Returned duration is structural only, not proof of decodable/audible speech.
The original strict zero-header-padding policy is retained. The initial capture
rule conservatively accepts confirmed clips from 300 through 12,000ms; recorder
stop/jitter tolerance must be resolved when integrating real recording. Structural
receive validation allows up to 13,000ms, matching the reference receive ceiling.

Actual host integration: `NativeCore.dispatch(op=walkie_rules) -> Rust
walkie::dispatch -> frame/floor/capture/audio rules`. This is a pure host rules
API: no paths, Android handles, sending, microphone or playback effects.
A Kotlin test calls the real Linux JNI library to verify frame decoding,
coordinator ownership, stale-release rejection and cancelled-capture decisions;
it also checks that this API creates no files. Rust tests cover all control
variants, malformed boundaries, floor replay/cancellation/exhaustion, capture/
autoplay safety and NB/WB frame/duration/size checks.

These rules are groundwork, not a production Walkie UI/SDK package or complete
session service. No app currently records/transmits voice through this module.
It must not be counted as an eighth completed SDK app.

## Next authorized chunk

1. Build a real Walkie Rust/Wasm controller and durable scoped preferences,
   sessions, clip metadata, expiry/deduplication and carrier outcome state.
2. Add shared, revocable SDK microphone/record/playback components with Android
   focus/lifecycle cleanup and cancellation tests. Bind record readiness and
   Send/Discard controls to real broker state; never display a fake connection.
3. Recreate the original connection/contact/verification/dial/recent-clip UI.
4. Integrate authenticated peer/subscription-bound SMS control and AMR MMS audio
   only through the host messaging broker. Keep queued/carrier/received/heard/
   uncertain/failure distinct. Resolve blocked contacts, invitation/idle timers,
   receiver persistence, delayed ordering, receipts and retry duplicate warnings.
5. Complete MMS receive/send/carrier checks before changing the default SMS role
   or enabling the live path. MMS_READY remains false in this checkpoint.

No microphone/SMS/MMS/contact capability was added to the SDK in this chunk.
No real external messages, recording, audio playback, default-role change,
carrier test, APK or mobile native build occurred. Source and Linux/JVM tests
are separate from all those acceptance boundaries. No legacy Java is bundled.

## 0.17.0 — SDK shell and offline durable state

The next bounded chunk is implemented. Production path: original desktop Walkie
entry -> AppSdk curated installer -> signed `WalkieTalkie.onklav-a` ->
`sdk/apps/walkie` Rust/Wasm controller -> `walkie.*` private-storage broker ->
`core/src/walkie_store.rs` -> atomic app-scoped `walkie.json`. Kotlin/Compose
renders the shared declarative widgets; there is no app-specific Android Activity,
Java source, separate APK, Android permission, or guest device handle.

Implemented and exercised: initial offline 190dp concentric dial with original
radio glyph, 18sp centered status, side-by-side Auto Play/Auto Send, staged
Auto Play/Auto Send/Beeps settings with Save/Close, immediate quick preferences,
normalized manual recipient entry, durable selected recipient, Quit, left recent
recipient drawer, reselect and confirmed clear. All three preferences default on.
Only `storage.private` is requested. The store bounds saved recipients to 80,
validates persisted data, fails closed on corruption and preserves previous bytes
on validation errors. Its atomic file counts toward the shared 16 MiB quota.
Recipients are explicitly prepared/offline, not established voice sessions.
History is labelled Recent Recipients rather than pretending to contain calls.

The shared `voice_dial` widget currently accepts only disabled/offline state.
The host and Rust dispatcher reject events on disabled nodes. No tap can acquire
a microphone or claim a grant. `walkie.connect` returns a precise unavailable
error and sends nothing. Guest services for connected/received/send do not exist.
Restart never restores a supposed live connection. No pending host effect is
created by Connect; no capability, carrier or default-role gate was opened.

Tests cover the actual signed Wasm package, restart preferences/recipient state,
staged cancellation, storage revocation/session invalidation/removal, disabled
actions, corruption, bounds, normalized duplicate recipients, isolated app storage
and prohibition of simulated connection/receipt/send. A Compose framework-host
test opens the original desktop entry, checks the offline dial/disabled Connect,
saves settings and a recipient, observes honest Connect failure, reopens and
clears recent recipients. These are Linux JNI/JVM checks, not audio/carrier tests.

Still pending: contact picker and verification/invitation UI; live dialog spacing
and dial transitions; authenticated active-session state; clip storage/expiry/
tombstones/delivery ledger; revocable microphone/player broker; Send/Discard,
foreground/focus cleanup, incoming audio/history, retries/receipts/order; SMS/MMS
transport and device/carrier acceptance. The manual recipient form and offline
shell are a development step, not full original visual/behavior parity. The
settings retention text describes intended enabled-service behavior; this
checkpoint stores no audio. Original rules remain production-ready helpers only
to the extent tested, not proof of audible or carrier functionality.

Verification: 58 Rust +43 unique Kotlin tests pass across broad/isolated runs.
The full suite's Groups contact-picker test remains intermittent; the final
isolated Groups+Walkie and shared run passes. See source-verification.json and
evidence/v0.17.0/final-suite-results. No single full-suite green run is claimed.
The test bootstrap coordination now awaits SDK setup before replacing its UI.
Quick preference saves preserve a typed, unsaved recipient number (regression
covered through the signed Wasm controller). The internal package requires a
host built from this 0.17.0 source; the historical 0.14.0 APK lacks voice_dial.

## 0.18.0 — first to-do item: private clip archive

`TODO.md` tracks the requested ordered migration work. Item 1 implements
`core/src/walkie_clips.rs`, consumed by the real signed SDK controller's Voice
Clips left drawer. Six clips/page, exact outcome labels, refresh, confirmed
single deletion/clear and empty state are implemented. Rows have no pretend
playback action. No demo clip is added in production.

An atomic archive contains bounded structural AMR/audio metadata and duplicate
proofs. Clip limits/expiry/dedup/retention and outcome transitions are described
in RESUME-0.18.0.md. Audio bytes never enter a guest frame or UI list. Normal
storage revocation, removal and app quota apply. Deleting audio preserves its
bounded duplicate proof. A damaged archive fails closed; it is not overwritten.

Trusted host calls use the existing serialized SDK dispatch with fixed Walkie
scope and current installed/storage checks. Wasm effects have only list/delete/
clear; they cannot invoke ingest/audio/attempt/carrier-result/receipt/played.
This is a host ingestion boundary for the next broker task, not a communications
implementation. Real receiver/session authentication, recording/playback, Send/
Discard, notifications, retry warning UI and SMS/MMS remain unimplemented.
MMS_READY stays false. No role, real microphone, real carrier or Android provider
change occurs. Test AMR data is a structural fixture, not evidence of audible
speech or real delivery. Archive expiry/recovery executes on access; future
Android background scheduling is not claimed here.

Tests exercise real archive persistence, exact duplicate binding, deletion/clear
and replay, expiry boundaries, maximum clips/proofs, quota/corruption, trusted
byte reads, incoming heard retention, outgoing retry tokens, timeout uncertainty,
receiver proof precedence and full signed Wasm history/paging/delete/clear with
revocation/removal. The Compose test seeds a trusted fixture through NativeCore,
checks the real drawer/status/delete confirmation, then verifies duplicate
suppression. This tests host integration; no simulated network is presented as
working voice transport.

Final verification: 63 Rust +43 Kotlin tests passed in the current full host
run (106 total). TODO item 1 is complete; no APK/mobile/handset/carrier or
recording/playback acceptance occurred. See source-verification.json.

## 0.19.0 — foreground recording/playback broker (TODO item 2)

Generic voice_capture/voice_clip SDK widgets now call host-only Rust component
checks through the Kotlin model. SdkVoice.kt owns Android MediaRecorder/Player,
audio focus, lifecycle cleanup, permission prompting, private cache files and
host-only draft buffers. See RESUME-0.19.0.md for call path and limitations.
Users explicitly grant audio.record/audio.playback; curated bootstrap does not
pregrant either. Android microphone authorization is separate. No recording or
playback starts solely from a guest action or permission-result callback.

A valid saved recipient is required. Native checks bind a draft to current app
session/widget/recipient; input edits or another modal invalidate active voice.
Capture is bounded to 12 seconds/64 KiB. Valid encoder tail frames can be trimmed
only after the entire structural input passes validation. Local review supports
Preview, Queue voice clip and Discard; Queue does not invoke a carrier/send attempt.
Incoming successful authorized playback marks the private clip heard; decoder
errors, stop/cancel/background/revocation/late callbacks never do. Own outgoing
playback keeps its queued/carrier/delivery state. No receipt SMS is sent.

The original live dial, microphone-floor handshake, Auto Send/Auto Play during
connected chat, warm-up/beeps, transport and exact appearance remain later work.
The dial remains visibly OFFLINE. All voice acceptance here is source/Linux/JVM
fixture testing, not captured/audible handset speech, cellular acceptance or a
phone APK. No new APK or mobile native ABI build is made.

Final evidence: 65 Rust +48 Kotlin host tests passed (113), including the current
full app/shared run. The joint playback test asserts both the Rust heard state
and successful UI completion response. Playback completion refreshes history
through a non-gesture guest callback. Playback preparation/completion has a 20s
host timeout. TODO item 2 implementation is complete, with phone audio acceptance
still pending; no APK/mobile rebuild occurred. The shared atomic archive's
large-library read/write latency still needs device profiling; no speed or audio
quality improvement is claimed. See source-verification.json and final XML logs.

## 0.20.0 — selected contact broker (TODO 3a)

Walkie v4 adds an explicit default-off contacts.pick capability and Android phone
row chooser. Only the selected bounded name/normalized number reaches Rust/Wasm;
current tokens and grants guard both provider reads and completion. Optional names
persist with old-record compatibility. Typed recipients remain supported. Selection
does not verify or connect the peer and sends no messages. Full TODO 3 remains open;
live session controls/timeouts and original dial/warm-up/beeps are next.
See RESUME-0.20.0.md and source-verification.json. No APK/mobile rebuild.

Final verification: 67 Rust +50 unique Kotlin host checks pass across the final
broad run and focused Groups/shared run (117 unique checks). The final broad app
run had one Groups failure; it passed in isolation. This is NOT a single green
full-suite result. TODO 6 stays open. Two earlier Walkie picker waits were traced
to pending model updates not observed by the paused-clock UI harness; explicit
Snapshot.sendApplyNotifications() before virtual frames fixed the final broad
Walkie flow. Earlier failures and final XML are retained in evidence/v0.20.0.
No APK, mobile ABI, handset, live connection or real contact/carrier proof.

## 0.21.0 — outgoing session foundation (partial TODO 3b)

Rust now persists one bounded outgoing control session with phone/SIM/nonce
binding, queued/claimed/accepted distinctions, verify/invite/idle timeouts,
replay rejection and restart interruption. The signed app consumes recovery
status and shows the compact unavailable-connection dialog. Actual transport
remains disabled and cannot be enabled by guest JSON; machine success paths
are fixtures, not a live connection or cryptographic peer authentication.
See RESUME-0.21.0.md for the exact production path and remaining broker,
receiver/consent, blocked/rate/lifecycle, dial/audio and carrier gaps. Item 3b
and full item 3 remain unchecked. No APK/mobile build or default role change.

Final evidence: all 73 Rust +50 Kotlin host tests pass (123), including one
current green full app/shared host invocation and the actual signed-Wasm/JNI
connection dialog/contact flow. The new session success paths use fixture
transport/time; production transport remains closed. No APK/mobile rebuild,
real invitation, live call or handset/carrier acceptance.

## 0.22.0 — incoming consent/permission foundation (3b remains partial)

Default-off communications.walkie grant gates invitations independently of audio.
The private Rust incoming controller verifies matched probe-reply/send state,
requires explicit consent, bounds automatic replies and rejects stale/foreign
controls. Signed Wasm renders the invitation and local decline; revocation cancels
pending state. All production receive/send calls remain disabled, with successful
control paths exercised only by test fixtures. Actual Android carrier receiver,
host blocked-policy/confirmation, simultaneous invitation and lifecycle handling
remain open. See RESUME-0.22.0.md and source-verification.json. No APK.

Final verification: 78 Rust +50 Kotlin host tests passed (128), including the
current full app/shared host run. Incoming success paths are fixture transport;
signed Wasm/JNI permissions/dialog and existing contact/audio flows are exercised.
No APK/mobile ABI rebuild, real incoming invitation/send, handset or live voice
acceptance. Full TODO 3b remains partial. Final XML/logs are in evidence/v0.22.0.

## 0.23.0 — Walkie carrier/audio integration; not operationally accepted

Signed Walkie v7 now calls the host-owned carrier bridge for invitations,
consent, floor-controlled audio, offline Send, explicit Retry and receipts.
Rust validates peer/SIM/session/attempt and live grants at the final handoff;
Kotlin owns SmsManager, protected receiver/results, private audio and policy.
Warm-up, beeps, foreground Auto Play and Auto Send are integrated. Successful
carrier/audio tests use fixtures. MMS_READY remains false; default role unchanged.
No phones are available, no real messages/audio were verified and no APK built.
Background/restart/late-arrival behavior, cleanup review and full original UI
acceptance remain open. TODO 3/5 stay incomplete. See RESUME-0.23.0.md and
source-verification.json for production paths, limitations and final evidence.

## 0.41.0 — four-area continuation

Implemented durable Messenger callback/provider recovery, older group reaction
parent context and bounded history search, durable Walkie result/background
recovery and late-clip fallback, internal Browser handoff, paged Notes search,
shared speech settings/ownership, own Reader draft adoption, Annotation viewport
restoration and per-app website-profile isolation/cleanup.

Verified 145 Kotlin/framework-host and 144 freshly rebuilt Rust tests
(289 total), with one host APK (code48). This does not complete carrier,
two-phone audio or exact reference acceptance. MMS/default SMS role remain
disabled. See RESUME-0.41.0.md for production paths and explicit remaining work.
