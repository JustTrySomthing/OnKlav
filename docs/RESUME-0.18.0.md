# Resume 0.18.0 — Walkie private clip archive, source only

The user requested a to-do list and completion of only its first item, with no
APK per item. TODO.md is the ordered work list; item 1 is clip persistence,
metadata/outcomes and SDK management. Do not silently mark the whole Walkie port
complete. Next is the revocable microphone/player broker (item 2).

Production UI: AppsDesktop Walkie -> signed SDK Wasm -> walkie.clips/delete/clear
storage.private services -> Rust walkie_clips atomic private archive. The left
Voice Clips drawer shows six rows/page and precise status; Delete/Clear confirm.
Rows do not pretend to play audio. No microphone or transport capability exists.

Trusted future device broker entry: NativeCore op=sdk/action=walkie_archive,
fixed app.onklav.walkie scope, installed/storage-grant checks and SDK mutex.
This action is not in the Wasm service allowlist. It accepts bounded structural
AMR via ingest, returns private bytes through audio, starts token-bound attempts,
and accepts matching carrier.result/receipt/played updates. The future broker
must validate/authenticate the real session and source before ingest/receipts,
and bind playback completion/focus to played. This archive action itself sends,
records and plays nothing and establishes no verified live session.

Archive schema v1 is one atomically replaced JSON file containing hex audio and
metadata/proofs. This avoids metadata/file crash inconsistency, at the cost of
rewriting and validating a bounded archive rather than individual blob files.
100 clips, 1000 duplicate proofs, <=15 MiB archive inside the 16 MiB app quota;
maximum 65536 audio bytes per clip, 300..12000ms outgoing /13000ms incoming.
Heard clips expire after 24h or 7d from creation, whichever comes first; others
expire after 7d. Duplicate proofs expire at 14d and survive deletion/clear/eviction.
Expiry and 90s sending->uncertain recovery run on archive access; an Android
background scheduling policy remains in the later broker task. No Android
provider data is deleted.

Retry tokens prevent old attempt callbacks; peer/session/subscription/direction
binding is checked. Carrier acceptance is not delivered. Receiver proof survives
late carrier failures and duplicate heard proof does not extend retention.
The seven-state outgoing ledger adds Received for incoming clips. Actual retry UI,
notifications and authenticated transport remain later tasks; uncertain retry
must warn about duplicates before any future send.

Build signed Walkie v2 with retained publisher; fresh hosts install it, already
installed v1 requires normal signed package update (bootstrap does not silently
update installed apps). APK/mobile native build stays deferred. Retain historical
0.14.0 APK reports. Current source version 0.18.0-dev/code25. Use restored-env,
build_sdk_apps.py, build_native.py --host-only, check_android.py --tests-only.
Do not repeat flaky full-suite runs indefinitely; preserve exact current results
and distinguish them from older 0.17.0 combined evidence. See source-verification.

Final verification: 63 Rust +43 Kotlin tests passed in the current full host
run (106 total). TODO item 1 is complete; no APK/mobile/handset/carrier or
recording/playback acceptance occurred. See source-verification.json.
