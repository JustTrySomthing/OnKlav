# Application review and repairs — 2 November 2026

This increment reviews shared media/session controls and network handoff, Phone call-state/notification ownership, incoming MMS recovery, recorder teardown/draft preservation, Calendar edits/reminders/import deduplication, and the recent SMS receipt/error corrections. It is a targeted source review with selected framework-host checks, not an exhaustive certification of every app, real modem, provider or codec.

## Verified source defects and repairs

| ID | Trigger and former behavior | Corrected behavior |
| --- | --- | --- |
| C01 | A headset or system controller sent seek/next/previous to live Radio, or a seek before a podcast was ready/outside its duration. The strict control rejection was caught as a playback failure and disposed healthy playback. | The system-control boundary ignores commands unsupported for the current media/state. Supported play/pause/stop still operate. Signed guest/direct controls retain their strict validation. |
| C02 | The shared ongoing-call notification ID was updated for a new waiting caller with OnlyAlertOnce always enabled. A new incoming call could be treated like a repeated notification update. | The first visible notification for each ringing call may alert; repeated updates of that same caller remain quiet. Visible call-screen suppression, no-badge channels and minimized-screen full-screen guards remain intact. |
| C03 | The screen used the authoritative callback state, but Answer/Hold/DTMF and swap checks still read Call.state. With an older getter value, visible controls could do nothing or take the wrong branch. | Snapshot and call-control state checks share the callback-backed state function, including terminal guards. |
| C04 | An outgoing Call first arrived as NEW with unknown direction and only became outgoing during CONNECTING/DIALING. Its initial screen was suppressed, and later callbacks never opened it. | Connecting/dialing/account-selection callbacks open the private OnKlav call screen once for a non-incoming call. Known incoming direction cannot use this shortcut, and repeated callbacks do not relaunch. |
| C05 | One MMS recovery journal refused a write. The deadline was removed before processing; an exception could lose that job's recovery deadline, abort the remaining batch and skip rescheduling. | Keep the previous deadline until the action commits its successor; catch each job independently, retain/rewrite the failing deadline, continue other pictures, and enforce at least 30 seconds before retrying a journal failure. Network download retry limits and the prohibition on outgoing resends remain unchanged. |

C06 (tooling): repeated Gradle invocations reproduced a generated-transform metadata-read failure that stopped checks before test compilation. The checker now quarantines only the named generated artifact and retries once, preserving its first log and original total timeout. It never retries a product compile/test failure.

No new defect was established in the recorder/Calendar paths inspected in this increment. Their prior known device/provider qualification limits are retained, rather than treating inspection as a new device-test pass.

## Validation

All 57 unique selected/shared Android host cases passed across bounded staged runs: PhoneCallLifecycleTest14, RadioContinuityTest5, AudioHandoffTest5, MmsDownloadRecoveryTest12, SmsSettlementTest4 and shared17. Production and all test sources compiled; runtime bytes match compiled Kotlin. The final Phone-only rerun corrected an unknown-direction fixture initialized to Android’s incoming value0 instead of UNKNOWN=-1; unchanged passing suites were retained. A nullable test assertion and the dependency metadata interruption were also corrected; initial evidence is retained. Final counts, production/test compilation, runtime class verification and source hashes are recorded in app-review-2026-11-02-verification.json. Seven added Android regressions cover these five application findings, including both live-radio and podcast instances of C01. The call action fixture intercepts Telecom commands; media fixtures simulate decoding and no real carrier messages/calls are made.

The five existing Python tooling suites completed all 30 tests successfully. Eight new cache-repair/checker tests also pass: only the named generated transform can be quarantined; unrelated dependencies/roots and symlink targets are preserved. The Android checker now repairs this recognized environment failure at most once, within its original deadline, while ordinary compile/test failures still stop immediately. This addresses a recurring restored-workspace build interruption observed during this review. Unchanged native, speech, internal app and game artifacts are retained with their original provenance/signing identities. No APK was requested or built.

## Remaining qualification

The recurring SMS17/modem2 physical network failure remains unconfirmed; its prior wording/receipt fix is preserved. Physical Samsung call waiting/headsets/rotation, Wi-Fi/mobile handoff/Doze, provider operations and real recording/media codecs remain separate acceptance work. This review does not claim the app is free of all bugs.
