# Resume 0.21.0 — outgoing session foundation, source only

Source 0.21.0-dev/code28, signed Walkie v5. User authorized the next bounded
Walkie step without an APK. TODO 3b is PARTIAL, not live chat completion.
Only Linux JNI is current; historical mobile libraries/APK remain 0.14.0.

Production consumption: SDK walkie.state -> scoped walkie_store snapshot ->
walkie_session recovery -> signed Rust/Wasm recovery status. Connect ->
walkie_session::host -> compile-time closed transport gate -> no invitation
sent -> shared Kotlin ReferenceDialog with centered recipient/status and side by
side Cancel/Try Again. Dial remains OFFLINE. Quit/clear/reselect cancel session
state. Trusted NativeCore sdk/action=walkie_session is fixed to installed Walkie
with storage.private; guest effects cannot issue host commands or forge outcomes.
Only state/end work today; no network operation can be enabled by JSON args.

Private outgoing machine: one current session, atomic <=4096-byte scoped file,
shared 16 MiB quota, corruption fails closed. Explicit nonnegative SIM binding;
fresh random 32-hex session ID, exact normalized sender/SIM/session checks;
probe queued/claimed/checking -> invite queued/claimed/inviting -> accepted
control session -> terminal. Queued is never called sent; send claim persisted
before a wire frame is returned, one attempt only; failure/uncertainty terminate
without automatic resend. Duplicate C/A replies cannot extend deadlines or issue
another invitation; premature/stale/replayed/foreign/legacy frames are rejected.
18-second verify/queue deadlines, 45-second invite acceptance, five-minute idle.
Production deadlines use process-monotonic time. New process always interrupts
old active state; no resume or resend of ambiguous sends. Expiry is evaluated on
access, not an installed background timer. Connected-control state is only a
unit-test fixture while transport is closed, not audio/floor permission.

New staged protocol uses !OKW1 P/C/I/A/D/Q with random session correlation.
The existing typed strict frame codec handles bodies after explicit namespace
validation. Legacy !ICW1 input cannot establish an OnKlav session. No legacy Java
implementation is copied. This is app/session correlation, NOT cryptographic
remote identity. Receiver, incoming invitation consent, simultaneous-invite rules,
blocked/rate policies and actual transport are not implemented by this chunk.

Before enabling: explicit communications capability and OS role/SIM/fail-closed
broker; host-derived current selected recipient/SIM; foreground/lifecycle session
ownership and alarms/recovery; incoming probe/invite approval/blocked/rate policy;
carrier send/result routing and two-phone verification. Do NOT just flip
TRANSPORT_READY. MMS_READY=false and default role unchanged. TODO 3c original
connected dial/floor/warm-up/beeps/receipts and task 5 live voice remain open.

Tests use fixture controls/time, actual signed Wasm, Linux JNI and Compose host.
No external message, microphone/speaker or physical phone is exercised. New
machine cases cover sequencing, one active call, source/SIM/nonce binding,
timeouts, duplicate/replay, ambiguous attempt, cancellation, restart, corruption
and quota. Signed SDK integration covers host gate, revoked storage, guest
isolation, persisted interruption and the unavailability dialog. Host UI tests
cover dialog and contact selection. See source-verification.json for final
results and evidence/v0.21.0; Groups remains an intermittent test task 6.

Final evidence: all 73 Rust +50 Kotlin host tests pass (123), including one
current green full app/shared host invocation and the actual signed-Wasm/JNI
connection dialog/contact flow. The new session success paths use fixture
transport/time; production transport remains closed. No APK/mobile rebuild,
real invitation, live call or handset/carrier acceptance.
