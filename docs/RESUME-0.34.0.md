# Resume 0.34.0 — completed restore record handoff

Source 0.34.0-dev/code41. No APK/mobile ABI build. MMS_READY=false, no default
SMS role change and no actual phone writes. Read HISTORY-BACKUP.md and TODO.md.
Use build_native.py --host-only followed by check_android.py --tests-only.

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

Seven new Rust tests cover actual receipt/journal consumers, metadata no-replay
following deletion, interruption before/after metadata pruning, preservation of
incomplete/unknown records, forged inserted receipts, conflicting raw/metadata
copies, corrupt/duplicate/symlink archives, full active-map/receipt reclamation,
archive overflow refusal and pagination beyond 100. A new Compose/JNI host test
exercises Cancel/Compact, retained metadata/drafts and archived detail navigation.
These are controlled filesystem/framework checks, not power-loss/OEM acceptance.

Remaining: final explicit apply/resume and identity-review binding; manual review
of unknown/changed insertion outcomes; provider snapshot/commit race handling and
real phone/MMS/permission/role/process acceptance before enabling restoration.
The full rewrite and Settings services (Drive, bundled voices, profile sharing,
external games), Walkie acceptance and game ports remain open. No phones are
available for carrier acceptance. No APK was built for this chunk.

Verification environment note: the first Android app/test compile failed reading
a Gradle temporary Binary store (reported in the daemon log, not the truncated
client log). Retained initial-android-compile-cache-failure.log and
initial-gradle-daemon-failure.txt distinguish it from a source compiler error.
Gradle's generated .tmp directory was quarantined and redirected to a fresh
local temporary directory before the next bounded invocation. This is a build
environment mitigation, not a claim that all Gradle/cache faults are fixed.

Final current verification (.34): 130 Rust +101 Kotlin host tests passed (231),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.
