# Audit repairs — 1 November 2026

The six functional findings B01–B06 from APP-DEEP-REVIEW-2026-11-01.md are repaired in source. The installed/delivered APK remains 0.100.34-rc1/code143 and predates these repairs. No new APK was requested or built.

## Changes

| Finding | Corrected behavior |
| --- | --- |
| B01: incomplete exports | Calendar, Cloud Files, metadata/Notes/Library backups, private SDK files, package export and the backup file copier use the shared error-close/final-filing protocol. Internal destinations also persist a failed-writer marker before close, so a normal descriptor callback cannot file an interrupted copy. Recovered bytes remain available; only the owner's explicit recovery action may file such a copy. Backup writers release `AppSdk.staging` before awaiting Files, preventing a filing deadlock. Failed Cloud saves retain the full staged download for retry while Cloud Files remains open. |
| B02: inconsistent size limits | Destination checks use the native Files limits: 256 MiB for ordinary files and 2 GiB for audio/video. Connected phone providers retain their own limits. Cloud chooses its destination before network transfer, checks known sizes immediately, and bounds both known- and unknown-length streams. Backups over 256 MiB explicitly require a connected phone folder; they are rejected before opening an incompatible internal destination. Streamed writes enforce the destination limit too. Supported larger transfers are preserved through connected folders. |
| B03: clock rollback | SMS/MMS persist one monotonically increasing filing order before inserting the message, separately from the real provider receive date. The order is seeded from existing native receive/read state; read actions use the same logical time domain. SMS retains its raw recovery record before allocating the order. MMS initializes its per-receipt order atomically and reuses it on repeated callbacks. Recovery replays reuse their persisted order, preserving later Archive/Trash decisions. Pre-upgrade receipts without an order use their original time conservatively. |
| B04: repeat cutoff | Timestamp UNTIL imports compute the last permitted local recurrence date by comparing the event's actual zoned start with the cutoff instant. Exact cutoff matches and date-only cutoffs remain inclusive. Timed cutoffs on all-day events are explicitly reported unsupported instead of silently changed. |
| B05: encrypted account storage | Cloud account envelopes have one consistent 1 MiB read/write budget, checked before AtomicFile replacement. All currently valid 20-account inputs, including heavily escaped passwords, fit and round-trip. Valid larger envelopes written by the former inconsistent writer can reopen. Corrupt/oversized data remains protected from overwrite; no plaintext fallback or key bypass was added. |
| B06: reminder scheduling | Calendar keeps saved events and reports a separate reminder failure with a Retry reminders action. A failed new schedule no longer preemptively cancels the existing callback. Retry clears the warning after successful scheduling. Durable saves, exclusive all-day ends, wall-time recurrence and notification/sound separation remain intact. |

B03 is repaired at the host receipt-journal boundary. Rust's arrival guard still expects ordered filing values; every production SMS/MMS filing call now supplies the durable host order. Provider dates and send dates are unchanged. Native, signed SDK and game sources/binaries remain source-matched and reusable; no native rebuild is required for this implementation.

## Verification

**61 unique Android host cases and all 30 Python tooling tests passed.** The affected final rerun passed all eight cases, including both prior failures and the added recovery case. Production and all test sources compiled; runtime class bytes matched compiled Kotlin. No full-suite/UI/device or fresh lint pass is implied.

Final case counts, suite results, source hashes and compilation status are recorded in `audit-repairs-verification.json`. Evidence is under `docs/evidence/audit-repairs/`.

The first executed Android run passed 58 of 60 cases. One failure exposed reliance on the descriptor error channel; the durable failed-writer marker fixes that path, with an additional normal-close/owner-recovery regression. The other was an outdated Notes-backup fixture launching the retired Notes app; it now uses Files to test the same live-session restore guard. Previously passing cases are retained and only affected cases are rerun. These are application/framework host checks, not an install on a physical phone.

The test environment needed restoration of the saved JDK/SDK/dependencies and exact missing AndroidX/runtime artifacts. Small missing artifacts were verified against the primary repository's published hashes and exposed through a temporary local Maven repository; no dependency version was substituted. Production and all test-source compilation use the actual project. Initial dependency/cache failures are environment evidence, not accepted product-test passes.

The three Python planner failures from Q01 are corrected by omitting retired Notes from the test fixture; all 30 tooling tests pass. The older standalone incoming-store harness remains superseded by the actual Android/JNI regressions and is still a maintenance item. Active documentation now identifies the current APK and source-repair state; older audit observations remain as historical evidence.

Physical Samsung/carrier/reboot/Doze/network handoff, real WebDAV/email providers, device codec/UI behavior and independent security qualification remain open. No carrier messages were sent, live account credentials used, or unrelated app features removed by these tests.
