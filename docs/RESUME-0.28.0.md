# Resume 0.28.0 — read-only duplicate comparison and ID mapping candidates

Source-only 0.28.0-dev/code35. No APK/mobile ABI rebuild; verified historical APK
remains 0.14.0. No SDK program changed; package hashes/publishers are unchanged.
User said Work continue after .27. Bounded operation: content matching and existing
message/flag/thread ID mapping candidates. Estimate 20–30 minutes; give progress
and revised ETA during checks, no silent long loop or per-item APK.

Production: Settings Storage > Backups > Messages and Media Backup > choose ZIP >
Review > Compare with phone -> explicit READ_SMS -> Kotlin HistoryImport re-copy
with expected archive SHA + shared HistoryBackup.reading/capture provider snapshot
-> trusted NativeCore history_import compare -> strict source/current validators
-> Rust history_plan -> counts-only Compose summary -> full private job cleanup.
No insert/update/delete/send/default-role request or metadata mutation. A provider
row ID is never duplicate evidence. Read HISTORY-BACKUP.md for matching/skip/
ambiguity policies, fingerprint contents, metadata constraints and limits.

Native compare requires the previously checked archive SHA; a changed file fails.
No comparison/apply SDK guest service exists. The private JSONL map is removed
with staging before returning to UI; it is not reusable authorization or a durable
restore plan. Freshness/provider observation remains best-effort. Any future
apply must revalidate data and record real new provider IDs; do not reuse counts
or untrusted archive IDs for writing. MMS_READY remains false, role unchanged.

Run build_native.py --host-only before check_android.py --tests-only. Same native
Robolectric sandbox, no PixelCopy/emulator. One Gradle/cache writer at a time.
Current results are in source-verification.json and docs/evidence/v0.28.0. Preserve
existing signing keys; do not rebuild unchanged SDK programs or upload Walkie again.
Save TODO/source/SDK starter preserving identities after all checks pass.

Next bounded restore work: a provider-write transaction broker and per-message
outcome journal, atomic rollback of incomplete MMS child/parent writes, cancellation,
restart recovery and new-ID recording, all behind role/permission/MMS acceptance
guards. Then complete post-insert folder/flag remap with existing-entry precedence.
No test phones are available; do not enable the role/gate or claim live acceptance.
Drive/bundled voices/profile sharing/external games/other TODOs remain unfinished.

Final current verification: 102 Rust +76 Kotlin host tests passed (178), zero failures.
Complete app/shared invocation passed with current JNI; runtime-class
freshness verified. No APK/mobile ABI build, actual user-history/provider/account/
phone/carrier or rendered parity acceptance. History provider restore remains unavailable.
