# SMS/MMS history backup — current 0.28.0 status

New Kotlin/Compose and Rust implementation; Inner Circle 2.2.85's backup dialog
is a read-only behavioral reference. No old implementation or Java source is used.
Only OnKlav is an APK. This is a host Settings service, not a package capability.

Settings > Storage > Backups > Messages and Media Backup opens the compact
reference dialog. Save messages and media requests READ_SMS when needed and uses
Android's CreateDocument(application/zip). Export reads the existing provider;
it neither requests the default role, sends messages nor writes provider rows.
Choose backup to restore opens the read-only check/review flow described below.
Restore messages remains disabled, with a clear unfinished explanation.
The separate app-metadata JSON backup/restore remains available.

## Production path

HistoryBackupDialog -> HistoryBackup.export (IO) -> ContentResolver SMS/MMS,
MMS address and part rows -> fixed private UUID staging files -> NativeCore
op=history_backup/action=pack -> Rust history_backup schema/identity/parent/size
checks -> streamed ZIP with per-entry SHA-256 index -> reopen/read every entry
and check ZIP CRC/SHA/index -> SAF copy -> close -> remove private staging.
No whole history or binary part is loaded into memory. Only a provider cursor
window, one bounded record, 64 KiB copy buffers, ID sets and the bounded archive
index are held. SMS timestamps remain milliseconds and MMS timestamps seconds;
MIME, charset, part sequence, SMIL/text, address type and optional provider
columns are preserved. Provider internal _data filesystem paths are excluded.

The exporter observes SMS, MMS and combined provider changes. An observed change,
permission revocation, null/failed query, unreadable attachment, exceeded limit,
copy failure or Stop aborts rather than reporting a partial success. Android
providers do not offer a portable cross-table read transaction: observation is
best-effort and OEM notification behavior still needs real-device acceptance.
Do not claim a transactionally atomic provider snapshot.

Stop cancels query signals, closes the current stream and writes the private
marker checked by Rust between streaming blocks. Dismissal/recomposition disposal
cancels the active operation. A process crash can leave app-private staging;
the next export removes only our UUID staging directories without following
symlinks. One export runs at a time. Error/cancel attempts to delete the newly
created SAF document; if a document provider refuses deletion, the UI explains
that an incomplete document may need manual removal. The private ZIP is fully
verified before copying; a successful output close is required. A separate
readback of a remote document provider is not claimed.

## Version 1 format and limits

ZIP uses stored entries, including:

- records.jsonl: one bounded record per SMS, MMS, MMS address or MMS part.
  kind/id/optional parent/fields/optional file; field values preserve provider
  strings or null. MMS precedes its address/part records. IDs are decimal.
- media/<provider-part-id>.bin: exact streamed bytes; generated names never use
  message filenames or provider paths.
- metadata.json: existing onklav-app-backup v1 envelope; Rust validates the inner
  metadata, exported reminder excerpts/timestamps remain stripped.
- index.json: onklav-message-history v1; SMS/MMS/part totals, original timestamp
  units, encrypted=false, restore_supported=false, entry names/bytes/SHA-256.

Limits fail the export rather than truncating: 200,000 messages, 500,000 total
records, 100,000 MMS parts, 1 MiB per JSONL record, 128 MiB total record text, 64 MiB per binary part,
2 GiB total binary media and 8 MB app metadata. This is lower than the original
4 GB limit and is explicitly shown in the new dialog. Private staging plus the
ZIP require roughly twice the content size until the save finishes; insufficient
space fails with cleanup. ZIPs are unencrypted and include private text/numbers.

RCS/iMessage, contacts, drafts, SDK documents, permissions, queued OnKlav sends,
and media not yet downloaded into the Android MMS provider are excluded.
Undownloaded MMS notification metadata can be recorded, but export does not fetch
its carrier URL or claim its missing media is saved. Completed exports are not
sent anywhere by OnKlav; the user chooses the document destination.

## Verification and remaining work

Rust tests exercise a real ZIP with SMS/MMS and exact binary bytes, independent
hash/index verification, orphan/escape/draft/unknown rejection, oversized records,
Stop and symlink rejection. Framework-host fixtures exercise ContentResolver ->
real current JNI -> saved ZIP, Unicode, date units, recipient types, text/media,
provider failure/change, cancellation, permission removal and private cleanup.
Compose covers the actual nested Settings route and explicitly disabled restore.
Current final counts/evidence are recorded in source-verification.json.

No actual phone/provider or private Inner Circle history has been read, no account
used, no SMS role changed, no MMS gate enabled, no APK/mobile native build done.
Handset/OEM acceptance, process-death/provider recovery and history restore with
strict archive inspection, duplicate/parent mapping, explicit review, default-role
and provider-write guards remain open. Full backup/restore parity is not complete.

# 0.27.0 — import inspection and review (no message restore yet)

Choose backup to restore now opens Files and checks an existing OnKlav v1 history
ZIP. It does not require READ_SMS or the default role. Kotlin HistoryImport owns
the document read signal, 64 KiB bounded copy, private UUID job, progress/Stop and
cleanup. Rust history_import owns untrusted archive/record validation. The native
API accepts only inspect, never a provider-write/apply request. No SDK guest
capability exposes it. The generic runtime and all signed app packages are unchanged.

A complete source ZIP is copied privately first, with a 2.4 GB cap. Rust checks
its SHA-256, a manually bounded central directory before allocating the ZIP entry
table (including bounded ZIP64 counts), entry count, canonical generated names, regular-file attributes,
nonoverlapping data extents, Stored-only v1 method and bounded index. Directory,
symlink, duplicate, extra/missing, encrypted and compressed entries fail. Version 1
export uses Stored entries; this is deliberately not a general-purpose ZIP importer.
Index size is capped at 24 MiB; entry/index sizes, hashes, totals and date units
must agree. Decompression bombs are rejected by the Stored-only policy rather
than trusting claimed output sizes. No arbitrary zip.extract/path joins are used.

After those checks, only approved names are streamed to fresh create_new private
files. Every entry is read fully to check ZIP CRC, independently indexed SHA-256
and actual sizes. The common export/inspection validator checks message/part IDs,
MMS parents, record/text/media limits and app metadata. Duplicate record field
keys now fail both paths. Import additionally checks allowed provider columns,
string/null values, canonical nonnegative IDs, bounded dates, message boxes,
MMS MIME/address types and content, global part uniqueness, and the metadata
format/version/UUID/timestamp envelope. Provider _data paths, unknown fields and malformed records are rejected. No
provider URI or SQL selection is taken from archive data. Original MMS metadata
may contain inert content-location strings; these are never followed or fetched.

Counts returned for review include SMS/MMS/parts, actual binary media bytes,
Inbox/Sent/other states, undownloaded notifications and metadata totals. Only
counts/fingerprint leave the core; no raw message text or extracted media appears
in the UI/logs. Extracted copies and the private source are removed before review
is shown. The selected original is never changed or deleted. Copy/check failure,
Stop and disposal clean up too; crash remnants are cleared at the next check
without following nested symlinks. Disk requirements can approach twice the
archive size; storage exhaustion fails without changing messages.

Checksums establish internal integrity, not authorship. The exporter's legacy
restore_supported flag is informational and never enables writes. The review
shows Restore messages disabled and explains the development-version limit.
Nothing is restored, scheduled, sent, downloaded, installed or granted. App
metadata is checked, not merged during history inspection; its separate existing
JSON restore remains unchanged. A provider merge still needs duplicate/message
ID mapping, folder/flag remapping, per-message transactional insertion/rollback,
cancellation/recovery and role/permission checks at every provider boundary.

The existing MMS_READY=false/default-role restriction remains in force. Do not
request the role or enable restore based on host fixtures or an archive flag.
Real SIM-equipped phones remain unavailable. Full history restore, OEM/document
provider/process-death testing and original full backup parity stay unfinished.
Tests/evidence: source-verification.json and docs/evidence/v0.27.0.

# 0.28.0 — read-only phone comparison and mapping candidates

Review message backup now offers Compare with phone. This is an explicit read of
SMS/MMS history and downloaded parts, requiring READ_SMS; checking the selected
ZIP alone still needs no SMS access. The same Kotlin provider capture is used by
export and comparison, under one read-operation lock with shared cancellation,
permission rechecks and provider-change observation. No default role is requested.

Kotlin re-copies the selected original to a fresh private job and captures current
phone records/media without exporting a second ZIP. Rust revalidates the archive,
checks its SHA-256 against the previously reviewed file, validates the provider
snapshot, then calls history_plan. The original file changing invalidates the
comparison. Observer/permission failures, Stop or errors invalidate results and
remove all staged source/current/media/mapping files. Inspection/comparison does
not persist a grant, read contacts, write provider rows or merge app metadata.

## Matching policy

Provider row/part/thread IDs, read/seen state, creator and installation-local SIM
IDs cannot establish cross-install message identity. SMS candidates use a SHA-256
of kind, box/direction, original timestamp, sender/address, body and subject. Phone
presentation separators are ignored, but +, country prefixes, leading zeroes and
alphanumeric senders remain distinct. This is content matching, not cryptographic
proof of globally unique message identity; conservative unmatched/ambiguous cases
are exposed for later review.

MMS candidates include date/box, completed message type, subject/charset and carrier
message ID, plus sorted recipient/type/charset records and part-content digests.
Part digests include sequence (including SMIL's negative sequence), MIME/charset,
name/disposition/filename/content-ID/location/text and actual binary SHA-256.
Provider part IDs and generated archive filenames are excluded. Reordering queried
address/part records does not change a match; changed recipient or media bytes does.
Missing sender/body/MMS structure or sequence stays for review. Only Inbox/Sent
are new/match candidates. Queued/failed/other unsent states and undownloaded MMS
notifications are skipped rather than resumed or sent.

A match requires exactly one source and one target with that content fingerprint.
Repeated source or target content is held for review, never arbitrarily collapsed
or paired. Reused equal row IDs with different content do not match. New candidates
have no target ID until a future provider insertion succeeds.

Rust writes a bounded private comparison.jsonl with message decisions and matched
source/target IDs, plus eligible flag/thread bindings. Only counts return through
JNI. Flags need a unique matched message and a matching positive saved date;
missing/zero/stale dates are deferred. Folder/thread bindings require every source
message in that thread to match one current thread. Unmatched/ambiguous messages,
multiple target threads and multiple old threads competing for one current thread
prevent automatic binding. Metadata entries without corresponding provider rows
are deferred. These are mapping candidates, not applied flags or folder changes.

The UI shows Already Present, New Candidates, Need Review, Skipped and eligible
metadata mapping counts. Old comparison results are cleared before a new run so
failure cannot leave a previous result looking fresh. A compact shared dialog
keeps Stop/Close behavior and explicitly disabled Restore messages. The file and
phone are unchanged. Extracted copies/comparison map are deleted before review.
The comparison file is not a durable executable restore authorization; any future
apply operation must recheck source/snapshot and record real newly inserted IDs.

Storage/record/part bounds are unchanged. Comparison can temporarily require a
source ZIP, extracted source and current phone snapshot (up to about three history
copies). Binary bytes are streamed, not held in memory; compact content/ID indices
are bounded by 200,000 messages per snapshot. Insufficient space fails with cleanup.
Provider observation is best-effort, not a cross-provider transaction. No speed,
OEM, actual-user-history, default-role or handset acceptance claim is made.

Verification covers real Kotlin fixture-provider -> current JNI comparison across
changed IDs, matching SMS/MMS bytes, folder/flag candidates, unchanged original and
metadata, permission denial, provider changes, cancellation and reviewed-file hash
binding. Rust covers repeated ambiguity, reused IDs, changed MMS recipients/media,
conservative metadata bindings, skipped/incomplete states and deterministic plans.
Full counts/current evidence: source-verification.json and evidence/v0.28.0.

Remaining: transactional provider inserts/part/address writes, cancellation and
rollback, restart recovery, recording real new IDs and final metadata remapping
with existing-entry precedence, plus MMS/default-role/phone acceptance before
writes are enabled. Full history restore, Settings and platform migration remain
incomplete. No APK or mobile ABI rebuild for this source-only checkpoint.

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

## 0.32.0 — restore configuration and recovery review controls

Settings Backups > Messages and media backup now opens Restore settings and
recovery. An interrupted-insert warning also has a direct Review interrupted
restore action. These use the existing ReferenceDialog, compact typography,
scrolling body, footer controls and staged Save/Cancel behavior.

Kotlin HistoryRecovery -> trusted NativeCore history_recovery provides host-owned
configuration and private recovery summaries. Local phone identities are stored
atomically by Rust in a versioned 4096-byte private configuration file. The editor
accepts up to 10 complete 7–15 digit numbers, optional leading +, and ordinary
presentation separators; it strips separators, rejects duplicate canonical values,
control/URI/partial-number input and preserves the previous file on invalid Save.
Country codes/leading zeroes are not inferred or rewritten. Cancel makes no write,
and saving an empty editor clears only these settings. Corrupt/symlink config fails
closed instead of silently resetting it. The gate-closed production applyChecked
entry reads this saved host configuration; callers no longer supply arbitrary
local identity sets. Settings are not exported as permissions or metadata backups.
The eventual apply-review UI must still bind identity configuration to the final
user review; saving identities does not authorize or enable a restore.

Recovery entry runs journal classification/status only, then shows committed and
cleaned counts, interrupted-insert descriptions and saved records. Unknown insert
results remain paused, with no Clear, Forget or Retry control. Known incomplete
inserts have an explicit confirmation/ownership-checked cleanup path, but the button
and broker remain disabled by MMS_READY=false and the same role/access guards.
This UI never searches for a content match to delete and never discards receipts.

Saved records are listed in stable pages of 20 (max 100 retained). Overview reads
only bounded headers and metadata completion receipts; selecting a record validates
its bounded JSONL decisions and shows counts only, not message bodies, addresses,
provider paths or archive content. Invalid/duplicate sources, target-kind mismatch,
unknown decisions, missing/mismatched metadata completion receipt and invalid map
paths fail closed. A record with a durable metadata receipt but an incomplete
acknowledgement offers Finish saved record. Rust reloads the record and matching
receipt, then atomically acknowledges the outcome file. Messenger state, provider
messages, metadata and reminders are not replayed or changed by this action. This
local acknowledgement repair works while phone writes remain disabled.

Four new Rust tests cover configuration normalization/limits/invalid-save retention,
corrupt/symlink settings, counts-only record review and receipt-required no-replay
acknowledgement, pagination and malformed records. Four new Compose/JNI host tests
exercise navigation, staged Save/Cancel/invalid input and persistence, unknown
insertion review with no retry/clear, disabled known-row cleanup, and finishing a
saved record with byte-identical messenger state. Existing production gate tests
still prove blocked entry before SAF/provider reads. No pixel/OEM/phone acceptance.

Remaining: actual resume/apply confirmation and local-identity review binding,
manual resolution of unknown/changed insert outcomes, durable outcome/journal/
metadata-receipt handoff and safe compaction. The current review controls do not
resume a failed metadata merge from a deleted private staging job; reopening and
freshly validating the archive still needs its explicit apply flow. Raw records
remain capped at 1000 journal entries/100 maps/100 metadata receipts; nothing is
cleared to bypass those bounds. Next bounded storage work is durable compaction
with retained anti-replay evidence, then the final apply/recovery workflow. Real
provider/role/permission/MMS/phone acceptance and race handling remain blockers.
Restore messages stays disabled; no test phones available. No APK/mobile ABI build;
last verified APK remains .14.0/code21. Other migration and Settings tasks remain open.

## 0.33.0 — journal compaction

Settings Backups > Messages and media > Restore settings and recovery now offers
Compact completed journal records, with Cancel/Compact confirmation. Kotlin uses
HistoryRestore.exclusive -> HistoryRecovery.compact -> trusted NativeCore
history_recovery/compact_journal -> Rust history_journal/compact. This is a private
storage operation; it requests no provider access or permissions.

The bounded terminal.archive retains complete committed/rolled-back transactions,
including archive/source identity, actual target ID and ownership proof. Readers
validate the archive and reconcile identical copies from an interrupted handoff.
Compaction fsyncs the replacement archive and directory before unlinking any
terminal raw records, then syncs the directory again. A crash before deletion
leaves duplicate evidence and a repeat can finish pruning. Conflicting copies,
corrupt data, symlink storage and archive limits fail closed. Nonterminal records
are never archived or removed, including uncertain and needs-review outcomes.

All receipt consumers and begin/replay checks read both stores. A compacted
commit cannot be inserted again or cleaned; recover does not re-expand terminal
records. Status preserves total outcome counts while reporting active slots and
archived counts separately. At most 1000 raw records and 10000 archived terminal
receipts (8 MiB maximum) are retained. A full archive refuses further compaction;
there is no reset/forget to discard duplicate protection. This is bounded journal
compaction only: saved outcome maps and messenger metadata receipts remain intact
at their existing 100-record limits. No capacity claim is made for those stores.

Five new Rust tests exercise the real receipt/replay consumers, interruption after
archive commit, unknown preservation, conflicting/corrupt/symlink archive refusal,
full active-journal capacity recovery and full-archive refusal without evidence
loss. One Compose/JNI check exercises Cancel/Compact, retained receipts/replay
protection and uncertain outcome retention. Host tests do not establish filesystem
power-loss, OEM or handset acceptance.


See RESUME-0.33.0.md for limits, recovery fixes and remaining work.

Final current verification (.33): 123 Rust +100 Kotlin host tests passed (223),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.34.0 — completed restore handoff

Settings Backups > Messages and media > Restore settings and recovery now offers
Compact completed restore records with explicit Cancel/Compact confirmation.
Kotlin HistoryRecovery.compactCompleted under HistoryRestore.exclusive calls
trusted NativeCore history_recovery/compact_completed -> Rust history_completed.
This complements the .33 terminal-message journal archive; it does not grant
provider access, perform a restore, or alter the installed internal SDK programs.

Only complete, acknowledged maps with matching durable metadata receipts qualify.
Rust validates every raw map; pending, need-review or unacknowledged records stay in full.
Inserted rows additionally require a matching committed journal transaction for
archive, source, actual target and transaction ID. Missing/forged receipts, invalid
headers/rows and changed acknowledgement counts/hashes fail closed. Committed
message receipts are retained in the existing raw/compacted journal.

The private history-completed.archive contains versioned completion summaries,
original-map SHA-256 and the full metadata completion receipt (archive/plan/post
hashes and imported counts). It contains no message bodies, addresses or media.
It is bounded to 1000 completed restores/2 MiB. Active maps and active metadata
receipts retain their existing 100-record caps. Overflow fails before pruning;
there is no reset/forget that drops replay protection. All maps are prevalidated
before handoff. Pending/unknown journal entries are untouched.

Handoff order: fsync replacement archive, atomic rename and directory fsync;
under messenger state mutex, reload latest state, validate duplicate receipts and
atomically remove only receipt entries already durably archived; then validate
raw-map hashes again, unlink only those completed maps and sync their directory.
Other messenger state, including flags, folders, drafts, reminders and outgoing
operations, is preserved. A crash before either prune stage leaves duplicate
evidence. Repeating compaction finishes those stages without reapplying metadata.
Conflicting raw/archive or active/archive copies, duplicate archive IDs, malformed
archives and symlink storage fail closed. Archive readers use atomic snapshots
without acquiring the compactor mutex while holding the messenger mutex.

Messenger receipt lookup and merge replay checks consult both stores. Replaying
the same job cannot resurrect a later deleted flag after its active receipt is
compacted. Archived acknowledgement repair and merge return the saved result,
validate surviving copies and never recreate full maps or reapply metadata.
Finish refuses an already archived job. Archive summaries remain visible in the
same recovery list and detail dialog; stable pages of 20 include archived IDs,
with bounded offsets up to 1100. Interrupted records keep their full detail.


See RESUME-0.34.0.md for verification scope and remaining work.

Final current verification (.34): 130 Rust +101 Kotlin host tests passed (231),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.35.0 — reviewed restore confirmation

Settings Backups > Messages and media > Choose backup > Compare with phone now
has Review restoration. This opens the existing compact ReferenceDialog style
with counts (new/already-present/need-review/skipped), saved local phone identities,
metadata precedence/reminder rules and Stop/partial-outcome explanation. Cancel
ends the review. Restore is visibly disabled by the existing MMS acceptance gate;
the production coordinator repeats that gate before SAF/provider access. Opening
confirmation reads private configuration only and requests no permissions.

HistoryConfirmation.prepare captures the selected content URI, immutable checked
Review (archive SHA plus phone comparison plan SHA), copied canonical local
identity list and private host-root identity. A comparison is mandatory, hash
fields must be canonical, new candidates must fit the 1000-message batch bound,
and an archive containing MMS requires saved local identities. Numbers are shown
exactly as reviewed. No country code is inferred. The object is transient,
non-serializable and not an SDK capability or durable resume authorization.
Closing, cancelling, replacing the comparison or disposing the parent ends it.

HistoryApply.applyChecked now takes that typed confirmation instead of unrelated
caller-supplied archive/plan hashes. After the unchanged production access gate,
runReviewed holds the restore operation lock, checks current configuration/root,
consumes the confirmation once, and runs the real existing archive pipeline with
its reviewed archive/plan and identities. A fresh SAF copy, strict archive check,
observed provider comparison, exact plan equality and all-payload preflight still
precede insertion. Reuse or a cancelled review fails before SAF/provider snapshot.
Different local identities also fail before SAF. Every coordinator/provider
check rechecks the reviewed identities; normal settings saves serialize under the
same operation lock. A simulated identity change at a provider boundary stops the
operation, preserves the actual-ID journal entry and partial outcome map, and
blocks cleanup/retry rather than guessing which recipients or rows to use.

The screen is connected to this production entry, progress, Stop and error paths.
After an attempt it clears the comparison so another attempt requires comparison
and review again. This does not implement record-specific resume or resolve an
unknown insertion outcome. It does not reapply metadata from a saved outcome file
without reopening and validating the original archive. It does not add a retry,
forget/reset control or silently change the default SMS role.


See RESUME-0.35.0.md for tests and remaining limits.

Final current verification (.35): 130 Rust +107 Kotlin host tests passed (237),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.36.0 — recorded metadata continuation

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


See RESUME-0.36.0.md for verification and remaining work.

Final current verification (.36): 134 Rust +113 Kotlin host tests passed (247),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.37.0 — review of an already removed recorded insertion

A changed/incomplete insertion with an actual recorded target now has an explicit
Check already removed message review. The host verifies that exact parent and
all MMS children are absent twice before acknowledging rolled_back, with a durable
verified_absent marker and all original evidence retained through compaction.
Present rows, children, read/access/cancellation failures and reappearance stay
pending. Unknown-ID and committed outcomes cannot use this path. No insert,
delete or automatic retry occurs; saved partial maps remain unchanged. The real
Check action remains MMS-gated and unavailable pending phone acceptance.
See RESUME-0.37.0.md for production path, tests and remaining limitations.

Final current verification (.37): 137 Rust +119 Kotlin host tests passed (256),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.38.0 — partial-message continuation under the original restore record

The saved-record flow now reviews and inserts only its still-missing pending
sources. Completed targets are checked by exact ID/content and never reinserted;
original review/skip rows stay deferred. Durable journal receipts recover commits
lost before map acknowledgement. Stop/error checkpoints update the original map
without clearing unknown inserts; unknown outcomes stay blocked. A second
observed snapshot precedes the original-job metadata merge. The real Restore
control remains disabled pending phone/MMS acceptance. No APK was built.
See RESUME-0.38.0.md for the production path, tests and remaining blockers.

Verification note (.38): the first full Kotlin invocation passed all 25 history
apply tests and the new partial-record UI route, but timed out in the pre-existing
Groups contact-picker wait and unknown-result recovery wait. Full initial logs/
XML are retained in docs/evidence/v0.38.0/initial. The Groups harness now traverses
every newly opened offscreen root, including when no Compose measure pass is
pending; recovery button tests invoke their actual semantics OnClick instead of
unreliable offscreen window coordinates. No test or assertion was removed and no
timeout was increased. The full invocation is rerun after those harness fixes.
An attempted continuation now explicitly requires Close/reopen for fresh review
if it fails, avoiding reuse of a binding changed by a partial checkpoint.

Final current verification (.38): 141 Rust +125 Kotlin host tests passed (266),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.
