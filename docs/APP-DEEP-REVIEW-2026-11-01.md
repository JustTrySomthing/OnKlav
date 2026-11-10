# Current status — all six functional findings repaired

B01–B06 are fixed in source. 61 unique Android host cases and all 30 tooling tests pass; production/test compilation and runtime matching pass. See AUDIT-REPAIRS-2026-11-01.md and audit-repairs-verification.json. The latest APK predates these fixes; no new APK built. Physical-device/provider qualification remains open. The original audit below is historical evidence.

---

# OnKlav deep review — 1 November 2026

Reviewed the current 0.100.34-rc1/code143 source from checkpoint version 285. This is an audit and issue-list update: no application code changed, no new APK built. Previous fixes are retained. Six functional findings and two engineering/documentation issues are open below.

## Findings and repair order

| ID | Priority | Open issue | Evidence |
| --- | --- | --- | --- |
| B01 | High | Some exports still publish partial files or report success before Files has finished filing | Current writer/provider control flow; earlier A01 protocol reproduction remains applicable |
| B02 | High | Cloud/backup size limits exceed what internal Files destinations can accept | Cross-component bounds confirmed in source |
| B03 | High | A backward phone-clock change can leave a fresh text in Archive/Trash and fail to mark it unread | Actual source-verified Rust JNI reproduction |
| B04 | Medium | Importing a timestamp repeat cutoff can schedule an extra Calendar occurrence | Executed production Calendar parser/recurrence code |
| B05 | Medium | Cloud account storage can write valid settings that its own reader refuses to reopen | Executed production encrypted store; synthetic keys/accounts |
| B06 | High | Calendar can report “Event saved” after reminder scheduling failed | Executed production model/store/scheduler with fault injection |
| Q01 | Medium | Three build-planner tests and the standalone incoming-store harness are stale | Executed failures and isolated fixture diagnosis |
| Q02 | Low | Active entry documents contain conflicting release/open-issue claims | Current README/TODO/START-HERE content |

### B01 — transactional export coverage is incomplete

`CalendarModel.kt:50`, `CloudModel.kt:48`, `SdkPrivateFilesEffects.kt:58`, `SettingsBackup.kt:71`, `LibraryBackup.kt:47`, and `NotesBackup.kt:34` still use ordinary `openOutputStream(...).use` without `writeFiledExport`/final filing acknowledgement. The shared helper at `FileSelectionStore.kt:123` exists and is already used by Write, editors, Authentic, recorders and other exporters.

On an exception during copying, ordinary cleanup closes the destination normally. `FileSelectionProvider.openFile` forwards that normal close as a successful completion signal; `FileSelectionStore.completed` can then import the incomplete bytes as a saved file. These callers can also report completion before asynchronous import fails. This reopens **coverage of A01**, not its already-corrected individual writer implementations. The old audit's partial-DOCX protocol observation is retained evidence, not counted as a new test here.

Cloud additionally deletes its full staged download in `finally`, including on a failed save. Notes/Library delete their generated backup staging and attempt `DocumentsContract.deleteDocument`; the internal selection provider does not support delete. A backup cancellation can therefore leave a partial ZIP/JSON filed as a normal item.

Repair: move remaining internal-selection exports to explicit error-close and durable filing acknowledgement; preserve full recoverable data and display truthful errors. **Do not hold `AppSdk.staging` while waiting for filing:** Files import takes that same mutex. Notes/Library backup flows need staged ownership and lock ordering designed before adopting the helper. Add cancellation, disk-full, filing rejection and slow-import coverage. No arbitrary provider-document deletion.

### B02 — accepted transfer sizes conflict at the final save boundary

Cloud advertises/transfers up to 2 GiB (`CloudDav.MAX_TRANSFER`). App Documents/Files backups allow 512 MiB (`LibraryBackup.MAX`). Internal Files import allows 2 GiB only for `audio/*` and `video/*`; other MIME types use `FilesBroker.MAX_FILE`, **256 MiB** (`FileSelectionSession.importSaved`, `FileSelectionStore.kt:40`).

A 300 MiB ZIP or PDF can finish downloading, be copied to a selected internal destination, and then fail its filing step. A valid 300 MiB generated backup has the same problem. B01 can make the UI claim success first. This is a source-confirmed limit conflict; no huge file/network transfer was performed here. Connected external providers have their own boundaries and are not all subject to this internal-import limit.

Repair: establish one supported size policy through selection, staging, native import and final filing, or reject unsupported destinations before transferring/copying. Cover 256 MiB ±1 and the supported upper bounds with bounded/sparse fixtures. Do not silently truncate or merely change UI text.

### B03 — receive identity depends on increasing wall time

`core/src/messaging.rs:157–164` applies arrival filing only when `at > last_incoming_at`. SMS and MMS receipt paths pass persisted wall-clock receive milliseconds (`MessagingTransport.kt:287,595`). The journal then marks filing complete even if the arrival predicate did nothing.

Actual source-verified JNI sequence: receive at 2000; archive and mark read at 2001; simulate a distinct fresh receipt after the clock moves backward, at 1000. The conversation remains **Archive, unread=false**. A trashed conversation has the same predicate. This does not delete the provider message; it breaks Inbox/unread routing. Existing equal/older replay safeguards are desirable and must stay.

Repair: use durable receipt identity plus an ordering rule resilient to wall-clock correction. Distinguish a fresh receipt from an old recovery replay; simply removing the timestamp guard would resurrect old archived history. Add rollback, equal-time distinct receipts, delayed MMS, duplicate and user-archive interleavings. Native rebuild required for a Rust fix.

### B04 — timestamp UNTIL becomes an inclusive whole date

`CalendarIcs.parseEvent` converts an RRULE timestamp `UNTIL` to a `LocalDate`. `CalendarRules` then includes the entire date without comparing event time with the original cutoff.

Production parser probe: daily UTC event at 10:00 from 1 November; cutoff `20261103T090000Z`. Import reports zero skipped events and creates occurrences on 1, 2 **and 3 November at 10:00**. The last is after the declared cutoff. Existing self-roundtrip/date-only tests do not exercise this case.

Repair: preserve an instant cutoff or compute the last valid local recurrence date correctly, including zone/DST conversion; otherwise explicitly reject/report the unsupported rule rather than silently changing it. Keep all-day/date-only behavior.

### B05 — encrypted cloud storage has mismatched write/read bounds

`CloudStore.save` validates at most 20 accounts but does not bound the serialized encrypted envelope. `load` rejects over 150,000 bytes. Field lengths and JSON escaping permit a larger valid payload.

Executed probe: 20 valid accounts with synthetic 4,096-character backslash passwords produce **166,673 bytes**. Save succeeds; both subsequent `load()` and `save(emptyList())` fail because save first loads the existing envelope. Old account data can be replaced with an unreadable envelope. These were synthetic credentials and in-memory test AES keys, not live accounts or Android Keystore qualification.

Repair: serialize and validate the byte budget before replacing the old envelope; align read/write capacity and show an actionable error. Test UTF-8/escaping and near-limit incremental account additions, retaining the previous ciphertext on rejection. Never add a plaintext fallback.

### B06 — Calendar saves hide failed reminder scheduling

`CalendarModel.mutate` commits the event, then wraps `CalendarReminders.cleanup` in `runCatching` and ignores its result. `recover` cancels the current single alarm before scheduling the next. A scheduling refusal can therefore leave durable reminders with no scheduled callback while `save()` reports “Event saved”. The receiver's recovery-error preference is not written on this path.

Fault injection against the actual model/store/scheduler made `AlarmManager.setExactAndAllowWhileIdle` throw. The event was durably present, `failed=false`, and UI message **Event saved**. Calling recover directly reproduced the thrown scheduling failure. This is a simulated refusal, not a reproduced Samsung alarm outage. Reopening Calendar may recover a transient failure; no automatic success is assumed.

Repair: keep the saved event, surface reminder scheduling failure separately, and provide explicit retry/reconciliation. Handle permission races/fallback without silently cancelling working coverage. Test pending-alarm preservation, missing permissions and scheduler/service errors. This is separate from Clock's previously fixed save rollback.

## Engineering and documentation

**Q01.** Python discovery ran 30 tests: 27 passed, three failed in `test_sdk_build_plan.SelectiveSdkBuildTest`. Its fixture excludes Counter/Annotation from host assets but still includes retired Notes; production `RETIRED_BUNDLES` includes Annotation **and Notes**. Isolated diagnosis: the unchanged fixture reports `sdk_current=False`; removing only its Notes bundle yields True. Update the fixture and assert retirement of both apps; do not loosen production freshness checks. `check_incoming_archive_store.py` also no longer compiles standalone: current ChatStore references MessageRepository/group-registration code that this old harness does not provide. These are test-tool defects, not evidence that current packaged apps are stale.

**Q02.** README identifies 0.100.25 and 0.100.27 as current, while actual current source/start header is 0.100.34. TODO/START-HERE preserve older “seven open findings” and “no new APK” passages beneath newer completion notes. Consolidate the active release/issue summary and clearly label or move historical entries. This review's top-level TODO/START notes point here; older evidence remains intact.

## Executed checks and scope

- **31 focused functional host checks passed:** Calendar dates/recurrence/import/storage (15), reminders (3), model concurrency/import/undo (5), and sparse launcher placement (8). Production source compiled in a focused harness; only MainActivity's unused notification target is a stub. SecretKeys declarations are exact extracted production code. No rendered UI or phone behavior is claimed.
- **9 actual source-verified Rust JNI functional cases passed:** unblocked Inbox/Archive/Trash routing with pin/mute retention, blocked routing, duplicate receipt preservation, distinct opaque blocked senders, and Unicode draft persistence. No SMS/MMS was transmitted.
- **27 of 30 Python build/backup/checkpoint tooling tests passed.** Three diagnosed fixture failures remain Q01. YouTube script tests also printed successful local checks; not added to the numbered total.
- **4 defect observations reproduced:** B03 via JNI and B04/B05/B06 via focused host execution. Observation tests pass when a defect is present; they are not desired-behavior regression passes.
- Full Android test compilation/execution was attempted offline, then retried once after restoring the saved dependency supplement. Both stopped on missing exact cached dependencies (including POI 5.5.1, Camera 1.5.3, Media3 1.9.3, mail 1.6.7, Tink 1.24.0, ZXing 3.5.4). No full-module test/lint pass or new APK is claimed. The stale standalone incoming harness failed compilation separately. No lengthy network retry loop.

Broader source inspection covered exported Android components/permissions, setup, Files/provider saves and recovery, backup/migration boundaries, incoming transport/group integration, Calendar, Cloud HTTPS/root/credential boundaries, Phone/contacts/call controls, recording lifecycle, Vault, media/network recovery and previous audit fixes. Signed native/SDK/game reuse plan remains current. This is not exhaustive execution of all 301 host Kotlin files or an independent security/gameplay audit.

Still open from prior reviews: physical Samsung/carrier/Doze/reboot/network-switch/codec/Bluetooth/IME qualification; live email/WebDAV providers; independent security/store/SDK licensing checks; full-track/named Audio projects, durable background exports/downloads, video missing-media relinking, and richer/full-resolution image editing. Those are preserved existing qualification/feature work, not six additional new defects.

Evidence: `docs/evidence/app-audit-2026-11-01/`; repeatable probes: `docs/fixtures/app-audit-2026-11-01/`. Repair B01/B02/B03/B06 first, then B04/B05 and test/docs maintenance. All findings remain open; this task did not implement fixes.
