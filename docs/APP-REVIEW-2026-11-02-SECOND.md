# Cloud and Calendar follow-up review — 2 November 2026

Scope: Cloud account persistence/cancellation, Calendar exports/reminder navigation/snooze actions, and Clock persistence/recovery. This is another bounded source review and selected host validation, not an exhaustive device/provider qualification. Cloud HTTP cancellation was inspected and already retained its active call after headers; that path was not changed. No additional Clock defect was established in this pass.

## Repaired findings

| ID | Trigger and former behavior | Repair |
| --- | --- | --- |
| D01 | A Cloud Files window added/removed an account using its old in-memory account list after another window had changed the encrypted store. It could overwrite the newer accounts. Store locking was per instance. | One process-wide account lock plus a read/merge/save transaction shared by all CloudStore instances. Connect/disconnect merge with the fresh encrypted disk list. No encryption, key or credential-scope bypass. |
| D02 | Cancel arrived while a Cloud account commit was running on IO. The disk write could finish, but cancellable return to Main skipped the acknowledgement, leaving stale accounts/selection in the same model. | The short local commit and its in-memory acknowledgement form one non-cancellable tail after authentication and an active-job check. Cancellation before the commit still stops normally. Once a commit starts it is completed and reflected in the model; subsequent account changes read disk afresh. No remote transfer is made non-cancellable. |
| D03 | Duplicate/replayed Snooze actions appended new timestamp-keyed child reminders, allowing duplicate alarms or resurrecting an already-fired snooze. | Stable successor identity; recognize both current and older timestamp descendants in pending/delivered receipts. Duplicate actions preserve the original deadline; a genuine Snooze action on the newly delivered child remains allowed. |
| D04 | Calendar export serialized a stale model snapshot and omitted events already committed by another window. | Read one consistent fresh event snapshot from CalendarDisk under its existing lock before serializing/exporting through the same Files protocol. |
| D05 | Opening a timed reminder from another timezone selected the event's original date, even when the device's Calendar displays that instant on the previous/next day. | Keep the original occurrence date for recurring edits while selecting the device display day for timed reminders. All-day dates remain unchanged. |

## Validation

All 48 focused/shared host checks passed (31 app checks executed; 17 unchanged shared checks reused by Gradle). Production and all test-source compilation passed, and the runtime bundle matches compiled Kotlin classes. Selected suites: CloudAccountRepairTest, CloudFilesTest, CloudExportRepairTest, CalendarModelTest, CalendarRemindersTest and CalendarScheduleRepairTest. Ten added regressions cover stale windows, actual concurrent store instances, cancellation during connect/disconnect commits, fresh export, cross-zone navigation, duplicate/replayed Snooze and timestamp-key compatibility. Final results, exact source hashes and runtime class verification are recorded in second-review-2026-11-02-verification.json.

The first run timed out after compilation and partial test execution; one new replay fixture used uptime instead of RTC and was corrected. A subsequent cache-metadata failure persisted through the automatic retry; the exact generated transform was quarantined separately before the successful final 149.8-second run. Initial and final logs and final suite XMLs are retained in docs/evidence/review2-2026-11-02.

The concurrency/cancellation gates and cloud responses are local fixtures using fake credentials. No live accounts, carrier messages or external credentials are used. Native/SDK/game source and signing identities remain unchanged. No APK requested or built; all prior call/media/MMS/SMS/camera repairs are preserved.

Real Samsung/Calendar notification/OEM, providers, network transitions, encryption-key hardware and carrier diagnosis remain separate device acceptance. The underlying SMS17/modem2 incident remains unconfirmed.
