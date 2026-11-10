# Resume 0.30.0 — archive-to-executor coordinator

Source 0.30.0-dev/code37. Bounded, source-only checkpoint. Use
build_native.py --host-only then check_android.py --tests-only. One Gradle writer,
no emulator loop, no per-item APK, preserve keys and unchanged SDK packages.
Read HISTORY-BACKUP.md and TODO.md. Current evidence is evidence/v0.30.0.

## 0.30.0 — archive coordinator and durable outcome maps

Kotlin HistoryApply.applyChecked now connects the checked archive flow to the
per-message executor. It checks the unchanged MMS/default-role/READ_SMS gate before
SAF access, requires the reviewed archive SHA and comparison plan SHA, re-copies the
chosen file and captures a fresh observed phone snapshot through HistoryImport.
Rust history_import prepare strictly validates ZIP/records/media, runs history_plan,
and calls history_apply to create private typed payloads for only unique new
candidates. Match/review/skip decisions are not inserted. A changed reviewed plan
fails before any insertion. There is no SDK guest operation or enabled UI action.
Existing inspect/compare UI paths remain read-only and discard temporary data.

Prepared parent fields omit old _id/thread_id/sub_id/creator and other provider
control columns; part _id and provider paths are removed. Kotlin rejects unexpected
fields and validates every payload and binary hash before the first write. SMS
uses its checked address. MMS requires a host-supplied set of reviewed local
identities (including identities on the old device), never archive-derived self
identity. Sent recipients use To/Cc/Bcc; Inbox peers include the sender and other
recipients excluding those local identities. Missing sender/identities, unresolved
insert-address-token or empty peers fails preflight. The configuration/confirmation
UI for these identities remains unfinished; do not guess identities from an archive.

Only after all payloads and journal capacity pass does the serialized coordinator
call HistoryRestore.execute, with the provider gate checked for every message.
Known incomplete failure is cleaned per .29; previous commits remain. Stop halts
remaining inserts and records partial outcomes. A missing previously committed
source on a fresh phone snapshot blocks replay; it is not silently reinserted.
The Android adapter also checks Stop again after resolving a thread, before insert.

On success or caught exception, Rust history_import finish binds the original
private plan/payload file hashes and derives new target IDs only from committed
same-archive/source journal receipts. It atomically fsyncs a private JSONL outcome
map containing all message decisions: observed existing match IDs, actual inserted
IDs/transaction IDs, pending candidates, review and skip. It stores no bodies,
addresses or media. Partial maps keep pending decisions explicit. Failure to write
a map leaves the original committed journal receipts intact; real process death
before handoff also relies on the journal. No receipts are erased or compacted.
Temporary ZIP/snapshot/checked media/payloads are removed on return or failure.

Limits: at most 1000 newly inserted candidates per prepared operation and the
remaining 1000-record journal capacity; 100 retained outcome maps, checked before
writes. Prepared JSONL is capped at 128 MiB and 2 MiB per message; the .29 per-message
text/media/child bounds also apply. Larger or already full operations fail closed.
No automatic chunking or compaction yet. Outcome maps explicitly say
metadata_applied=false and phone_revalidated=false. They are durable outcomes, not
fresh permission to apply metadata. A new post-write snapshot and conservative
thread mapping are still required before folder/flag merging.

Verification includes six full Kotlin fixture-SAF -> real Rust ZIP validator and
matcher -> prepared payload -> executor -> journal -> durable map tests. They cover
SMS/MMS binary bytes and resolved peers, exact new IDs, unchanged metadata, archive/
plan/identity failures before inserts, partial Stop, second-message cleanup,
committed-source replay blocking, fresh existing matches and the production closed
gate before any SAF/provider reads. Four Rust tests cover omitted old authorities,
committed-only/partial maps, changed preparation and capacity/missing-parent bounds.
Provider writes are injected; this is not acceptance of the real Android adapter.

Remaining: post-write provider revalidation and final folder/flag merge with
existing-entry precedence; resumable outcome-map handling/compaction and explicit
recovery/review/configuration UI; original MMS/default-role/provider/OEM/handset
acceptance. Snapshot-to-write races are not an atomic provider transaction and
remain a write-enablement blocker. No test phones are available. Restore messages
stays disabled and MMS_READY=false. No APK/mobile ABI rebuild; last verified APK
is still .14.0/code21. Drive, bundled voices, profile sharing, external games,
Walkie operational acceptance and the rest of the migration remain open.

Final current verification (.30): 111 Rust +91 Kotlin host tests passed (202),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.
