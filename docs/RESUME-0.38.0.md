# Resume 0.38.0 — explicit partial-message restore continuation

Source 0.38.0-dev/code45. No APK/mobile native ABI build. MMS_READY remains false;
no default SMS role change. Real restore/continuation still requires phone/MMS
acceptance. The full OnKlav rewrite is not finished.

Saved partial restore records can now reopen their original archive and review
remaining message insertions, rather than stopping at metadata-only continuation.
The same compact Files/review/confirmation/Stop/Cancel route is retained. Review
shows the exact remaining candidate count and explains that completed messages
are kept. No automatic retry, new outcome record or guest restore service exists.

Rust history_resume binds the original map SHA/archive/job/plan and reconciles
pending rows only with actual committed journal receipts for the same archive
and source, including compacted receipts. Recorded inserted rows must match their
original transaction and actual target. A crash after commit but before map ack
therefore cannot turn a committed message into a new candidate. Unknown or other
nonterminal journal entries still block reopening. Another archive's receipt
cannot promote a pending source. Corrupt/changed maps fail closed.

The fresh original-archive comparison must cover every saved source. Completed/
matched sources must remain uniquely matched at their exact recorded IDs. Each
remaining pending source must be eligible and uniquely absent (decision new).
A pending message that appears, becomes ambiguous, or becomes unsupported stops
review; it is never guessed to be this operation's insertion. Original review/
skip rows remain deferred and are not selected even if fresh comparison changes.
Pending source lists are bounded to 1000 and included in the one-use confirmation
binding together with the reviewed plan, configuration and saved map.

Production call path: Settings saved restore > Reopen original backup >
HistoryResume.binding/review > HistoryImport.inspectResume + Rust comparison >
HistoryConfirmation.prepare > gate-closed applyChecked > shared operation lock,
identity/map/one-use checks > fresh withResume archive+phone plan equality >
HistoryApply.preparedInputs selects and preflights only saved pending sources >
journal capacity checks > existing HistoryRestore.execute with actual returned
ID journaled before child writes and configuration checks at provider boundaries.
Previously committed messages never enter the insertion loop. The generic new
batch coordinator continues to reject resume tickets.

On completion, failure or Stop, native history_recovery/resume_checkpoint derives
acknowledgements solely from durable committed receipts, compares the old map
SHA, fsyncs/renames/fsyncs the original map and retains pending rows. A lost insert
acknowledgement remains uncertain with its record retained. No cleanup outcome is
mistaken for a commit. Checkpointing can preserve earlier commits even when a later
insert remains uncertain; it does not clear that block. A process death before
checkpoint is reconciled read-only on reopening and acknowledged during the next
explicit continuation. The original job/plan/header and deferred rows survive.

After all selected pending messages complete, the updated binding must have none
remaining. A second observed provider capture and the existing exact target/
content verification precede metadata merge under the original job, with current
flags/folders winning and reminders disabled. Metadata plus completion receipt
remain atomic in private state; map acknowledgement uses that same receipt.
Provider state and private state are still not a single cross-provider transaction.

New tests cover pending-source review, archive-specific committed reconciliation,
crash-before-ack, compaction, unchanged original plan, unknown outcomes and map
mutation. Kotlin fixture/JNI cases exercise a real generated archive, one remaining
SMS after a committed MMS, Stop then metadata-only continuation without duplicate
insertion, lost acknowledgement blocking, pending appearance/wrong target rejection
and recovered commit without replay. Compose verifies partial-record reopen/count
and Close without mutation. These are host fixtures, not phone/carrier/rendered
reference acceptance. The Android adapter remains blocked before SAF/provider work.

Remaining blockers: unknown insert results cannot be safely cleared without a
trustworthy receipt/reconciliation mechanism; present changed messages remain
protected. Actual role/permission/process/provider/OEM/MMS/handset testing and
snapshot/check/commit races require acceptance before enabling restoration.
No test phones are available. Settings Drive/bundled voices/profile sharing,
external games, Walkie transport acceptance, original game ports and full UI/
SDK migration remain open. Last verified APK stays 0.14.0-dev/code21.

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
