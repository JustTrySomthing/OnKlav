# Resume 0.31.0 — post-write metadata verification/merge

Source 0.31.0-dev/code38. Read HISTORY-BACKUP.md and TODO.md.
Use build_native.py --host-only then check_android.py --tests-only. Keep one Gradle
writer, bounded phases, no emulator loop and no per-item APK. Preserve keys and
unchanged internal SDK programs. Current evidence: evidence/v0.31.0.

## 0.31.0 — post-write verification and metadata merge

The gate-closed Kotlin HistoryApply coordinator now captures a second complete
provider snapshot after successful per-message insertion and durable outcome
handoff. HistoryBackup.reading observes SMS/MMS changes and, in this phase only,
cancels the operation when a change arrives. Permission/role/Stop checks surround
the capture and native merge. No provider writes happen during post verification.

Rust history_import merge -> history_apply rechecks the private preparation hashes
and outcome-map identity, rejects pending journal/message outcomes, validates the
source/post record sets and invokes the existing content/recipient/binary-digest
matcher against the post snapshot. Every original match and committed insertion
must uniquely match the exact expected provider row ID. Missing, changed,
ambiguous or relocated IDs fail the whole metadata step. Committed messages remain;
there is no rollback of earlier successful history inserts after verification fails.

Only dated unique flag bindings are eligible. Their thread fields are replaced by
actual post-snapshot thread IDs. Folder/pin/mute bindings require wholly resolved
source threads and no collision between multiple old threads and one new thread.
An original review/skip message prevents importing its thread, even if the later
snapshot happens to resolve it. Unresolved/missing/zero-date metadata is deferred,
not guessed. No old provider IDs or stored thread numbers are used as destinations.

Rust messaging.merge_history holds the existing state mutex, loads the latest
state and adds only absent destination entries. Existing false flags, reactions,
reminders and folders win as complete entries, including edits made during inserts.
Imported reminders are disabled (remind_at=0), and imported thread unread/read_at
overrides are cleared. This does not schedule old notifications. Groups, blocked
numbers, drafts, media drafts, outgoing sends, permissions and accounts are not
restored by this history step; existing private state is retained.

The messenger state and bounded per-job merge receipt are saved in the same atomic
fsync/rename operation. The native cancel marker is checked immediately before the
state rename. A receipt prevents replay of the same operation after acknowledgement
failure, including resurrection after a later flag deletion. Receipts are internal,
excluded from portable metadata export, and capped at 100. The separate outcome
map is atomically marked metadata_applied=true/phone_revalidated=true with the post
comparison hash and import counts. If that acknowledgement fails, state plus receipt
remain committed; the same-job native recovery path repairs acknowledgement without
reapplying metadata. This is not a cross-file or cross-provider atomic transaction.
New target thread IDs live in merged messenger entries; the per-message outcome
body remains the original ID/outcome ledger, not a full compacted metadata database.

Verification: three new Rust tests cover state/receipt idempotence, deletion after
merge/replay, cancellation with byte-identical original state, current-entry
precedence, retained drafts and exclusion of receipts from portable backups. Four
new Kotlin full-pipeline tests cover SMS/MMS post-ID/thread remapping, reminder and
unread disarming, edits during inserts, changed content/wrong IDs, and provider
mutation during post capture. Existing coordinator success tests now expose the
controlled inserted rows through the fixture provider for a real second capture
and matcher. All write providers are injected; actual Android writes remain gated.

Remaining: explicit recovery/review/local-identity configuration UI, outcome/journal/
merge-receipt handoff and compaction, actual OEM provider/role/permission/process/
phone/carrier acceptance. Provider observation is best-effort: a race remains
between the last cancel/access check and state commit, and permission/role changes
are checked at host boundaries, not atomically with the Rust commit. No speed,
rendered parity, real process-death or live phone acceptance claim is made. No test
phones are available. Restore UI remains disabled and MMS_READY=false. No APK or
mobile ABI build; verified historical APK remains .14.0/code21. Other Settings
services and the full platform migration remain unfinished.

Final current verification (.31): 114 Rust +95 Kotlin host tests passed (209),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.
