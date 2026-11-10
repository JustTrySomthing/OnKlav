# Resume 0.17.0 — Walkie SDK shell, source only

Walkie continuation is authorized. Continue bounded pieces without another
app-start question. Source is 0.17.0-dev/code24. See WALKIE-PORT.md for the exact
production call path and feature/gap ledger. Social stays permission-free Hello
World. The full rewrite, Walkie and SDK remain incomplete.

WalkieTalkie.onklav-a is installed by the same curated SDK bootstrap as the other
apps, with storage.private only. sdk/apps/walkie is the Rust/Wasm controller;
core/src/walkie_store.rs owns atomic scoped preferences/prepared recipients.
The 190dp offline dial is shared Compose UI, not microphone access. Connect
returns unavailable without effects or invitations. Recent recipients are not
connected sessions. Preferences and recipient data persist; voice cannot resume
as connected after restart. No guest can fabricate connection/delivery status.

Next: implement scoped clip metadata/blob validation, expiry/dedup and explicit
carrier ledger, then a shared revocable recording/playback broker with Android
lifecycle/focus tests; integrate authenticated peer/subscription-bound sessions
and SMS/MMS only once the messenger gate can be verified. Preserve original
reference dial/verification/review/history behavior and resolve recorder jitter.
MMS_READY=false; do not request default SMS role or claim voice acceptance.

Run build_sdk_apps.py with retained migration-private/apps.publisher.key,
build_native.py --host-only, check_android.py --tests-only. Source/test report
is docs/source-verification.json; evidence docs/evidence/v0.17.0. Current Linux
JNI matches host-native-provenance.json. Mobile JNI and APK reports remain the
historical 0.14.0 checkpoint; source ZIP excludes obsolete mobile Rust libraries.
No APK/mobile build is requested. Preserve existing publisher and Android keys.
Before a later phone build regenerate both Android Rust ABIs and then verify
that APK against that new source; do not relabel the old artifact.

58 Rust +43 unique Kotlin checks pass across broad/isolated runs. The single
broad invocation has an intermittent Groups contact-picker failure; isolated
Groups+Walkie/shared pass. Preserve this distinction; do not claim a wholly
green full-suite run. Final selected XML is in evidence/v0.17.0/final-suite-results.
