# Resume 0.32.0 — configuration and recovery review

Source 0.32.0-dev/code39. Read HISTORY-BACKUP.md and TODO.md. Use
build_native.py --host-only then check_android.py --tests-only. One Gradle writer,
bounded phases, no emulator loop and no per-item APK. Preserve signing keys and
unchanged internal app programs. Current evidence: evidence/v0.32.0.

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

Final current verification (.32): 118 Rust +99 Kotlin host tests passed (217),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.
