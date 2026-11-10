# Resume 0.33.0 — completed journal compaction

Source 0.33.0-dev/code40. No APK or mobile ABI build. Phone writes and default
SMS role remain gated. Read HISTORY-BACKUP.md, SETTINGS-PORT.md and TODO.md.

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

Workspace recovery fixes: restore_build_cache.py now selects current host-native
provenance when available instead of comparing the current Linux fallback against
historical mobile provenance. build_native.py --host-only no longer requires an
unrelated Android NDK installation. Android ABI builds still require the NDK.
The saved environment has no Rust toolchain; this recovery installed Rust/Cargo
1.99.1 with the minimal profile and used the existing Cargo.lock with --locked.
Restore retained keys from the private backups; never generate replacements.

Next: safe outcome-map/metadata-receipt handoff and capacity management, then final
apply/resume and identity-review binding. Unknown outcomes still need explicit
manual resolution design. Provider race handling and real phone/MMS acceptance
remain blockers. No test phones are available; Restore stays disabled.

The first cold Android app compile stopped on unreadable Gradle transform
metadata. The specific generated transform was quarantined (not source or
dependency artifacts); the retained failure log is
evidence/v0.33.0/initial-android-cache-failure.log.

Final current verification (.33): 123 Rust +100 Kotlin host tests passed (223),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.
