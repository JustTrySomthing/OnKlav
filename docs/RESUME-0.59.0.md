# 0.59.0 — Settings Notes backup

Notes Backup is a separate child of existing Settings metadata backup, preserving
metadata JSON v1 and existing history trial/recovery. Generic host SDK operation
notes_backup -> content::notes_backup uses canonical Notes (not the obsolete
app-sdk private Notes directory). Active notes only, 2,000/16 MiB, strict schema,
duplicate/UTF-16/control checks, full preflight, SHA review, existing-ID keep
(including Trash), missing notes restored into Notes. No grants/packages/sends.

Restore rejects all active SDK sessions before changes. Each document uses an
atomic durable write; the index is reconciled once, avoiding repeated full scans.
An I/O failure can leave some notes restored; retries keep them and finish others.
The confirmation explains this rather than promising multi-document atomicity.

The first compile had an integer-type mismatch; the two new checks then exposed
empty exclusion-ID handling in quota calculation. The interrupted run and compile
logs are retained. Fixed the shared quota helper to allow no exclusion; focused
checks pass before the one final full Rust/native rebuild. Do not count earlier
interrupted checks as a full pass. See source-verification.json for final evidence.

Kotlin NotesBackup serializes private staging through AppSdk.staging, uses SAF
for save/open, binds restore to the reviewed bytes/hash, cleans staging and tries
to delete incomplete provider output. Actual remote-provider/process-death and
phone acceptance remain open. New host/module only; no SDK app or icon change.
Signing identities preserved. Only OnKlav is an APK; games remain deferred.

Next bounded batch: shared Files and annotated-image backup/restore coverage.
Full SDK backup remains incomplete (other app stores and portability semantics).
Four broader developer groups remain open in FINISH-NON-GAME.md.

First APK assembly hit a disappearing temporary Godot library entry inside the
generated merged-native output. Retained the failure log, cleared only generated
merged/stripped native outputs and retried once without source changes.

Final: 173/173 Rust +3/3 provider/JNI +1/1 Settings navigation checks pass.
APK retry assembled in 40.17s and verified in 3.38s. Existing signer and ARM64
identity retained; no extra app APKs or handset/carrier acceptance implied.
