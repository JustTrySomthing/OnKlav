# Resume 0.35.0 — reviewed restore confirmation

Source 0.35.0-dev/code42. No APK or Android ABI build. MMS_READY remains false;
no role change, real provider writes or enabled Restore action. Use the bounded
host-only checks, current source-matched JNI and retained signing identity.

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

Five new controlled-provider/JNI tests cover reviewed whole-pipeline success and
one-use rejection, missing comparison/identities/oversized review and Cancel,
configuration change before reads, changed phone plan before insertion, and a
mid-operation identity change retaining a known incomplete result. The production
gate test now uses the new entry and still checks no provider snapshot/journal
mutation. A new Compose/JNI check shows exact reviewed identities/counts, disabled
Restore and Cancel invalidation. Fixtures are not phone or rendered parity proof.

Remaining: record-specific archive reopen/resume/review; manual resolution of
unknown/changed outcomes; configuration/read/commit race and actual provider,
role/permission/process/MMS/OEM/handset acceptance before enabling phone writes.
No test phones are available. Settings services, Walkie acceptance, original game
ports and the full migration remain incomplete. Last verified APK remains .14.0.

Verification note: the initial full host run passed all new cases but the existing
known-incomplete cleanup UI wait timed out. Its log and XML are retained under
evidence/v0.35.0/initial-*. The offscreen test pump now forces each registered
window's layout on every pass, including when Compose reports no pending layout;
newly attached dialog roots can otherwise remain unmeasured. No production
cleanup/permission logic was loosened. This is not broad Groups/OEM UI acceptance.

Final current verification (.35): 130 Rust +107 Kotlin host tests passed (237),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.
