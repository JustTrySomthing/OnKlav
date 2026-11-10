# Resume 0.29.0 — gated restore transaction backend

Source 0.29.0-dev/code36. Read HISTORY-BACKUP.md and TODO.md.
Use build_native.py --host-only then check_android.py --tests-only; one Gradle
writer, bounded checks, no emulator retry loop or per-item APK. Preserve signing
keys and unchanged SDK package hashes. Current test evidence: evidence/v0.29.0.

## 0.29.0 — per-message outcome journal and gated write executor

Implemented backend, not enabled history restore. Kotlin HistoryRestore validates
trusted typed Inbox/Sent input, rejects old provider/thread/subscription IDs,
serializes transactions, hashes bounded content and calls Rust history_journal
through JNI. Rust atomically writes/fsyncs per-message intent/outcome files and
records the actual returned SMS/MMS ID plus ownership receipt before child writes.
Kotlin HistoryRestoreProvider supplies fixed-URI Android parent/address/part writes,
host thread resolution, streamed media and read-back checks. Its immutable
MMS_READY=false/default-role/READ_SMS guard blocks production insertion before any
journal begin or provider write. No role request, sending, SDK guest service or
archive-provided URI is introduced. Settings Backups calls recoverState/status on
entry and shows an interruption warning if review remains pending.

Known incomplete inserts use compensating cleanup of that exact new parent and
its children, after checking the stored identity/creator proof. This is NOT an
atomic cross-provider SQL transaction. Cleanup checks parent/child absence; changed
ownership, inaccessible data or incomplete cleanup stay pending for review. A lost
insert acknowledgement (including process death before durable returned-ID receipt)
is uncertain: never search for/delete a content match or automatically retry.
Committed receipts survive restart, block replay of the same archive/source, and
cannot be cleaned. Stop/permission loss fail the operation and attempt guarded
cleanup; failed cleanup preserves the receipt and blocks the next message. Restart
status performs journal transitions only; it never silently deletes provider rows.

Journal bounds: 1000 retained records, 4096 bytes per JSON file, one in-flight
transaction. Corrupt records and symlink storage fail closed. Atomic temp files
left by a crash are ignored; durable phase and provider receipt decide recovery.
No record compaction/forget control exists yet. Bulk restore must add durable final
mapping handoff and safe compaction rather than discard receipts. Source payload
limits: 1 MiB text, 256 recipients/addresses/parts, 64 MiB per binary part and
128 MiB aggregate media per message; binary staging must remain private.

Verification separates injected deterministic provider execution from actual
Android provider behavior. Tests exercise commit/new IDs, blocked replay, MMS child
failure, lost acknowledgements, simulated process interruption, cancellation,
permission loss, changed ownership, cleanup failure and production closed gate.
The Android adapter compiles but real provider insert/creator normalization,
child cascades/orphan media, binary streaming, OEM behavior and carrier acceptance
are unverified. Parent ownership read-back checks stable identity fields, not every
optional provider field. Provider calls may still block on OEM behavior; no real
process kill/power-loss or phone acceptance has been performed.

Next: archive-to-write coordinator must revalidate archive/comparison, bind checked
payload/recipients, review candidate decisions and record a durable final ID map.
Then merge eligible folder/flag metadata with existing-entry precedence and add
journal handoff/compaction. No such archive apply orchestration or metadata merge
exists yet. Restore messages stays disabled and MMS_READY stays false. No test
phones available; never count fixture checks as live acceptance. Drive, bundled
voices, profile sharing, external games and the full migration remain unfinished.
No APK or Android ABI rebuild; last verified APK remains 0.14.0/code21.

Final current verification (.29): 107 Rust +85 Kotlin host tests passed (192),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.
