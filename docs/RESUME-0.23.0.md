# Resume 0.23.0 — Walkie carrier/audio integration, acceptance still open

Source 0.23.0-dev/code30; signed WalkieTalkie v7. This is an internal SDK
package, never a separate Android APK. No mobile ABI or APK build this turn.
The last verified APK remains 0.14.0. Do not relabel it as this source version.

The user requested a fully operational Walkie. That goal is NOT reached.
When asked about testing phones with active SIMs, the user answered “None
available”. Real SMS/MMS, microphone/playback quality, dual-SIM and Android
lifecycle acceptance cannot be demonstrated here. MMS_READY remains false;
default SMS role is unchanged. No real messages were sent. Do not flip this
gate or describe fixture success as operational carrier/audio acceptance.

## Implemented production path

Apps desktop -> signed Rust/Wasm Walkie v7 -> generic SDK components and scoped
Rust services. Direct Connect/Accept/Retry produces a communications.walkie
request. Kotlin host setup/role/grant/policy checks and a host-owned canonical
recipient confirmation precede the trusted carrier command. Wasm cannot supply
ready flags, arbitrary numbers or transport commands. Communications defaults off.

SdkWalkieTransport -> NativeCore SDK walkie_carrier -> Rust walkie_carrier ->
private walkie_session / walkie_clips. Host facts require MMS verification,
default role, send/receive permissions, an allowed peer and actual subscription.
Phone/SIM/session/attempt bindings are checked on claim, receipt and completion.
The final handoff revalidates the claim and live permissions under AppSdk's
carrier lock, serialized with SDK grant/update/removal. Revoked work cannot use
an earlier authorization to send. Android uses SmsManager for control SMS and
MMS/PDU/private transfer provider for audio. Explicit private result broadcasts
correlate callback jobs; ambiguous sends are not automatically retried.

Incoming SMS/MMS interception routes verified Walkie controls and audio through
the same policy and Rust validator. Ordinary SMS/MMS retains the messaging path.
One current session, bounded daily automatic replies, cooldowns, nonces,
explicit consent, simultaneous-invitation ordering and stale callback rejection
remain enforced. Peer proof can precede delayed carrier callbacks without a
later failure undoing stronger proof. This is carrier-correlated identity, not
cryptographic authentication or end-to-end encryption.

Connected floor request/grant/release gates the shared microphone. A generation
and lease protect cancellation; capture starts only after ownership plus a
180 ms warm-up. The connected 190dp hold-to-talk dial, optional beeps, Auto Send,
foreground Auto Play, incoming unheard ordering, T/H receipts and archive retry
are wired into the signed app. Audio stays in the host, outside Wasm. Offline
review has an explicit Send path when transport is verified; ordinary recipients
receive a normal voice MMS rather than platform control metadata. Retry requires
confirmation and warns about duplicates. Existing clip limits remain enforced.

## Validation and build repair

See source-verification.json and evidence/v0.23.0 for exact final test counts.
Protocol/carrier success cases use synthetic trusted facts and two fixture
peers. Host audio tests use the fixture backend; actual signed Wasm/JNI paths
and capability checks are exercised. They are not handset acceptance.

An incremental AGP runtime JAR retained an obsolete queue() signature after
Kotlin compiled queue(Boolean), causing NoSuchMethodError. check_android.py now
recreates the generated runtime bundle after compilation and checks every
compiled Kotlin class against its test runtime JAR. No source rollback or
permanent cache purge is needed. An unrelated Reminder UI timeout occurred in
an earlier broad run and passed once isolated; preserve that evidence and keep
UI test stability open rather than treating repeated retries as a fix.

## Remaining work before an operational claim

1. Verify underlying Messages MMS send/receive on two real phones and carriers,
   role/setup, permission denial/revocation and actual subscription selection.
   Only then consider enabling the host transport gate; review all paths first.
2. Exercise real Walkie invitations both ways, simultaneous invites, acceptance,
   cancellation, late/failed callbacks, floor contention, offline Send/Retry,
   duplicate delivery, receipt precedence and blocked contacts on those phones.
3. Finish background/Doze/reboot/deadline scheduling and recovery acceptance.
   Active/pending sessions currently interrupt on process restart. Callback
   expiry is swept when kicked, not by a guaranteed background scheduler.
4. Resolve late voice arriving after a session ends: the current connected-session
   validator rejects it from the Walkie archive; normal MMS fallback needs an
   explicit original-behavior decision and device checks. Audit pending job/cache
   cleanup during package removal/update/revocation and concurrency.
5. Verify microphone/codec/playback/focus, warm-up/beeps, Auto Play/Send,
   accessibility, phone contact picker, notification policy and full Inner Circle
   2.2.85 visual/behavior parity. Profile archive I/O and UI smoothness on hardware.
   Some archive reads still parse the bounded archive; no speed claim is made.
6. Independent protocol and SDK boundary review; stabilize intermittent host UI
   waits. No fully operational or 100% complete claim until these are resolved.

## Resume commands

Restore env.sh and /root/.cargo/env, use the retained publisher key outside this
tree. SDK changes: build_sdk_apps.py before native tests. Rust changes:
build_native.py --host-only, then check_android.py --tests-only. Keep checks
bounded, one at a time. A future requested phone build must regenerate both
Android ABI libraries before assembling/verifying the one OnKlav APK. Preserve
signing continuity. No extra source approval is required to continue Walkie.

Final verification: 84 Rust +52 Kotlin host tests pass (136), including
the final full app/shared invocation. Runtime class freshness verified. No real
carrier or audio acceptance; no APK built.
