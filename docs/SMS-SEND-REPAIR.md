# Plain-text sending repair — work in progress

User reports plain SMS failing; exact on-device result is not yet available. This source change does not establish the cause on the phone.

Resolve current sending SIM before staging. Keep active explicit choices, use the live system default, recover a stale saved choice or unset default when exactly one active SIM exists, and refuse ambiguous dual-SIM choices or transfer retries on an inactive pinned SIM. Use the single-part Android API for one-part messages; preserve multipart callbacks for longer messages.

Persist Android noDefault and modem error metadata in carrier receipts. Translate send failure codes and render the durable outgoing error under the message header, including after reopening. No automatic resend. Previous permissions, Clock, Camera, YouTube and playback work retained.

Eight isolated JVM/JUnit SIM-selection regression cases passed using the actual production resolver. The full Android host-check attempt stopped at its 180-second limit during resource/build preparation before Kotlin compilation or selected Android cases completed. Error-mapping test source is included but not executed. Full lint also reached its separate 60-second limit during build preparation; it did not pass. No new APK built. Physical carrier sending remains unverified.

## Delayed outcome follow-up

User reports Failed, inability to retry, then spontaneous delivery around five minutes later. Exact modem/carrier reason remains unknown. Confirmed source defects: the ledger made a failure sticky even after later positive receipts; the menu only prepared a draft.

Late positive send/delivery callbacks now reconcile their exact part, clearing obsolete errors. Stale negatives cannot undo positive evidence for that part; a delivered part never hides another failed part. Added four persistence/multipart/legacy-reopen cases and updated the prior sticky-failure regression. Legacy saved contradictory results are reconciled on load, while unknown outcomes retain their warning. Retry Text now opens a duplicate-delivery warning and submits one new text on confirmation, rechecking pending carrier receipts and latest durable status first. It preserves any unrelated composer draft. Older untracked messages and MMS keep an explicitly named Copy to Draft action. There is no automatic resend.

Core Rust source changed: Linux/Android native outputs must be rebuilt before use; the checkpoint marks them stale. Final validation: all 10 focused messaging state tests passed via cargo test --lib --offline --locked. An initial offline attempt lacked errno; an online attempt fetched dependencies but its separate regular-library archive failed on a zero-length object. Its completed test executable passed 9 cases; after the legacy repair, the final test-only Cargo invocation passed all 10. These earlier failures remain recorded. Android compileDebugKotlin reached its 120-second limit during app compilation; not passed. The explicit working native rebuild stops because NDK 27.2.12479018 is absent, and the Android Rust target is also not installed. No new APK. Do not rerun broad checks without addressing these build blockers.
