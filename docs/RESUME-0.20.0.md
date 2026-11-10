# Resume 0.20.0 — Walkie contact selection, source only

Bounded TODO item 3a, not completion of live sessions. Source 0.20.0-dev/code27;
signed Walkie v4 adds contacts.pick. The retained publisher/signing identities are
unchanged. No APK or mobile ABI rebuild; historical APK remains 0.14.0.

Production: signed Rust/Wasm Choose Contact -> direct-gesture contacts.pick
capability -> generic Kotlin SdkAppScreen activity-result broker -> Android
ACTION_PICK/Phone.CONTENT_URI -> SdkContacts selected URI query (name/number only)
-> current-token/current-grant reauthorization -> Rust selected_contact normalization
-> Wasm walkie.prepare -> atomic scoped walkie.json -> saved recipient and history.
No READ_CONTACTS prompt, full contact enumeration or provider URI reaches Wasm.
contacts.pick defaults OFF, separately enabled in Settings > Apps. Typed numbers
continue to work with the capability disabled. Cancel/error keeps the recipient.
Revocation/update/uninstall invalidate pending SDK sessions and reject late replies.
Recipient names are bounded to 80 UTF-16 units, control-free, and optional when
loading old records. Re-selecting the same normalized phone/SIM retains its ID.
Contact selection is not remote app compatibility verification or identity proof.
Connect remains offline/unavailable, sends nothing and makes no session claims.

Tests: signed-package Rust integration exercises permission-off, chosen row,
normalization, durable reopen, malformed result, cancellation, stale/replayed token
and revocation. Store test covers old unnamed records, stable IDs and invalid
labels without mutation. Kotlin provider tests cover selected URI/projection,
cursor closure, bounded names/phones, missing rows and checks before/after query.
Walkie Compose/JNI test follows the actual button -> Android picker launch ->
provider result -> Rust persistence. Provider contact data is fixture data; no
physical contact-provider acceptance is claimed.

Next: item 3b durable/live verification/session controls and timeout/recovery;
3c original connected dial/warm-up/beeps and receipts. Full item 3 stays unchecked.
MMS_READY=false, default SMS role unchanged, no external messages sent. Real
carrier/two-phone/voice/focus/accessibility/performance acceptance remains open.
Use build_sdk_apps.py with retained publisher, build_native.py --host-only,
check_android.py --tests-only; package/save existing source/SDK/TODO/Walkie identities.
See source-verification.json and evidence/v0.20.0 for final check results.

Final verification: 67 Rust +50 unique Kotlin host checks pass across the final
broad run and focused Groups/shared run (117 unique checks). The final broad app
run had one Groups failure; it passed in isolation. This is NOT a single green
full-suite result. TODO 6 stays open. Two earlier Walkie picker waits were traced
to pending model updates not observed by the paused-clock UI harness; explicit
Snapshot.sendApplyNotifications() before virtual frames fixed the final broad
Walkie flow. Earlier failures and final XML are retained in evidence/v0.20.0.
No APK, mobile ABI, handset, live connection or real contact/carrier proof.
