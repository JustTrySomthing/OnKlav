# Resume 0.36.0 — finish metadata for a recorded message batch

Source 0.36.0-dev/code43. No APK/mobile ABI build. MMS_READY stays false and
OnKlav does not request default SMS role. Real restoration/continuation remains
disabled pending phone/MMS acceptance. Use host-only native and Kotlin checks.

Settings Backups > Restore settings and recovery > saved Restore record now has
Reopen original backup when metadata is unfinished. Rust resume_info validates
the bounded saved map and returns its original archive SHA, original plan SHA,
record ID and full map SHA. Pending map rows or any interrupted journal insertion
block this operation before Files or phone reads. Inserted outcomes require their
matching committed journal transaction, archive/source/actual target, including
receipts retained by the .33 archive. Completed metadata uses Finish saved record
instead; it cannot enter a new metadata continuation.

The new ReferenceDialog route offers Choose original backup and explicit READ_SMS
when needed, then Kotlin HistoryResume.review -> HistoryImport.inspectResume ->
fresh bounded SAF copy, full existing ZIP/hash/schema verification and observed
phone snapshot -> Rust history_resume.compare. The archive SHA must match the
saved record. Every original source must be covered exactly; each saved inserted
or matched message must remain a unique content match at the exact recorded ID.
Wrong archives, absent/changed/wrong-ID/ambiguous targets and changed map bytes
fail closed. The temporary extracted history and comparison are always cleaned.
No message bodies, addresses or provider paths are returned in recovery summaries.

A successful recheck opens the .35 one-use confirmation, additionally bound to
record ID/map SHA/archive SHA. It shows the reviewed phone identities and says
that this continuation inserts no messages. Cancel/disposal ends the ticket.
Normal HistoryApply.applyChecked/runReviewed explicitly refuse resume tickets,
so they cannot accidentally start a second insert batch. The Continue/Restore
action remains disabled by the unchanged MMS acceptance gate; the production
HistoryResume.applyChecked repeats the gate before a phone snapshot or SAF open.

The controlled continuation path holds the existing restore operation lock,
checks current identities and saved-map binding, consumes the ticket once,
reopens/checks the archive and compares again against the reviewed fresh plan.
It then performs another observed snapshot through the existing post-write
verification path. Native history_import/resume_merge validates the prepared
marker, original map digest and journal receipts, keeps the original map/plan/job
identity, verifies exact targets/content again, and rechecks the map digest before
state commit. It uses the existing current-entry-preserving metadata merger and
its atomic completion receipt, then acknowledges the original outcome map. It
never calls the insertion or deletion executor and creates no additional outcome
map. Current flags/folders win; imported reminders remain disabled. Metadata
receipts continue to prevent replay/resurrection and can later be compacted.

This is metadata-only continuation for a completed message batch. It does not
resume missing/pending messages, resolve unknown or changed insertion outcomes,
or retry failed message inserts automatically. Original review/skip decisions
remain deferred; their full records are retained. A commit followed by an
acknowledgement/observation failure must be reviewed using the durable receipt,
not blindly reapplied. Private filesystem/JNI and observer checks do not make
Android providers and private state one atomic transaction.

Four new Rust tests cover read-only record binding, exact target/coverage/archive
checks, changed/pending/unknown records, and required inserted journal proofs
before/after compaction. Five new controlled-provider/JNI tests exercise actual
archive reopening and successful metadata-only completion under the original
job, no insertion/deletion or replay after later edits, wrong archive/changed map,
wrong target and provider change rejection, pending-batch blocking and the real
production gate, wrong-path ticket rejection and changed identities. A new
Compose/JNI case follows saved detail > Reopen original backup > Close without
changing saved data or requesting an automatic retry. Tests are not phone/carrier
or rendered/pixel parity acceptance.

Next: manual resolution of unknown/changed results and safe continuation of
partially inserted batches; real provider snapshot/commit race, role/permission/
process/MMS/OEM/phone acceptance before enabling writes. Settings Drive, bundled
voices, profile sharing and external games, Walkie acceptance, original games
and the full migration remain open. No test phones are available. Last verified
APK remains 0.14.0-dev/code21; no new APK was built.

Final current verification (.36): 134 Rust +113 Kotlin host tests passed (247),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.
