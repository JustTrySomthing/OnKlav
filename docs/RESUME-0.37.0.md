# Resume 0.37.0 — review an already removed incomplete insertion

Source 0.37.0-dev/code44. No APK or Android ABI rebuild. MMS_READY stays false;
default SMS role unchanged. Real checks remain disabled until phone acceptance.

Settings > Backups > Restore settings and recovery now offers Check already
removed message for a needs_review insertion only when it has a durable recorded
provider target. The ReferenceDialog explains what is checked, permits Cancel,
and disables Check behind the existing MMS readiness gate. Unknown-ID outcomes
have no such action. This is not a delete control and never retries restoration.

Production path: HistoryRecovery.confirmRemovedChecked > immutable Android
HistoryRestoreProvider access gate > HistoryRestore.confirmRemoved under the
shared operation lock > native journal get > require needs_review plus recorded
actual target/parent proof > twice check exact-target ownership is ABSENT and
provider.removed verifies parent plus MMS address/part absence > final Stop/access
check > NativeCore history_journal/reviewed_absent. Only the trusted host can
invoke this path; no archive target, inferred content match or guest capability.

Rust accepts that transition only from needs_review with a recorded target/proof
matching the original journal. It retains the full source/archive/content/target/
proof evidence, changes phase to rolled_back and records resolution verified_absent.
The optional marker is backwards compatible with unmarked v1 journal records,
strictly validates its value/terminal phase/receipt and survives journal compaction.
Unknown/no-target/committed transactions cannot take this transition. Marker
corruption fails closed. Resolved rows are cleanup outcomes, never committed
message receipts. No write is issued to SMS/MMS and no new insert is scheduled.

A present owned or changed message, remaining children, reappearance on the
second check, cancellation, access loss or provider errors leaves review pending.
No records are forgotten. Resolving a proved absent known row removes its global
journal block; any later restoration still needs its separate fresh archive/phone
comparison and one-use confirmation. Existing partial outcome maps are preserved;
this does not implement continuation of partially inserted batches.

Verification: three new Rust cases cover recorded-evidence binding/compaction,
unknown/committed exclusion and invalid resolution markers. Five new Kotlin
executor fixture cases cover successful absence acknowledgement without insert/
delete/retry, unknown/committed with no provider reads, present rows, children,
reappearance, cancellation, access loss/error and the unchanged production gate.
One new Compose/JNI case verifies the review/Cancel/gate route; the existing
unknown case also checks the action is absent. These are controlled host tests,
not real Android-provider, handset, carrier or rendered reference acceptance.

Remaining: unknown insert results still require a trustworthy reconciliation
mechanism and stay blocked. Present changed rows cannot be cleared or deleted
through this action. Partial-message continuation and provider snapshot/commit
race, permissions/role/process/OEM/MMS acceptance remain unfinished. Settings
Drive/bundled voices/profile sharing/external games, Walkie acceptance, original
games and full migration remain open. No phones are available. Last verified
APK remains 0.14.0-dev/code21.

Final current verification (.37): 137 Rust +119 Kotlin host tests passed (256),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.
