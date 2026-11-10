# Follow-up status

All seven confirmed functional findings below now have source fixes. See PHOTO-AND-AUDIT-FIXES.md for changes, verification and remaining feature gaps. Original findings below are retained as audit evidence.

# OnKlav application review — 30 October 2026

The deleted-conversation receiving bug is fixed in source. The wider review found **seven remaining functional issues**, including four high-priority reliability problems. Two additional UI refinements, a test-coverage gap, and feature/configuration gaps are listed separately. These findings are not claims that every part of OnKlav has been exercised on a phone.

Latest delivered APK remains **0.100.26-rc1 / code135**. No new APK was requested or built. The saved source includes all preceding Video Editor, Audio Editor, incoming Archive, shared-voice and size work.

## Receiving rule fixed in this increment

- A new unblocked SMS or downloaded MMS returns its conversation from **Archive or Trash to Inbox**, including a thread whose old messages were deleted. Existing pin and mute choices survive.
- A blocked arrival already in Archive or Trash stays there. A blocked arrival in Inbox, or with no previous folder metadata, is retained in Archive. No messages are permanently deleted by this rule.
- Blocked arrivals do not generate a conversation alert. MMS download/retry attention alerts now also check the blocked-sender list and remove an earlier attention alert when necessary.
- After unblocking, the next new message resurfaces the conversation normally. Invalid thread IDs and ordinary messages already in Inbox do not create unnecessary metadata writes.

Implementation: `ChatStore.kt:52`, `MessageNotifications.kt:63` and `MessageNotifications.kt:143`. Nine receiving cases and the dedicated blocked-picture case cover the new rule; eleven existing MMS recovery cases also passed. The receipt-recovery edge case A02 below remains distinct from normal successful receiving.

## Remaining functional issues

| ID | Priority | Finding | Evidence level |
| --- | --- | --- | --- |
| A00 | High | Outgoing receipt records accumulate until the next send is rejected | Reproduced against the actual Rust JNI |
| A01 | High | Failed export copies can be filed as saved; some success notices precede filing | Protocol behavior reproduced; affected writers traced |
| A02 | High | A crash or filing failure after SMS/MMS provider insertion can leave receiving recovery unfinished | Confirmed control-flow analysis |
| A03 | High | Clock scheduling and durable saves can disagree after a save failure | Two fault-injection reproductions |
| A04 | Medium | Conversation search and Trash-wide actions only cover loaded conversation pages | Confirmed UI/repository control flow |
| A05 | Medium | Different alphanumeric sender identities collapse to the same empty number key | Reproduced using production identity functions |
| A06 | Medium | Email Reply All cannot preserve original Cc recipients | Confirmed header model and composition code |

### A00 — sending eventually reaches a permanent record limit

`core/src/messaging.rs:19,45,130–148` retains every staged outgoing record. Successful Sent/Delivered records are not removed or compacted. `remove_message` removes message flags, not outgoing receipts. `save_guarded` rejects more than 1,000 outgoing records, with a separate 8 MB serialized-state ceiling that can be reached sooner with long texts.

A synthetic isolated state containing 1,000 completed/delivered records was loaded by the source-matched JNI. Staging one more returned `{"ok":false,"error":"Messenger state limit reached"}`. Existing state remained intact; no SMS, MMS or other carrier send was performed. Evidence: `docs/evidence/app-audit/A00-send-limit.json`.

**Fix direction:** compact completed receipt records safely while preserving unresolved sends, provider status, and enough durable receipt identity to handle delayed callbacks without resending. Do not simply increase the limit or discard all receipt history. This requires a Rust change and matched native rebuild; it was not bundled into the Kotlin receiving fix.

### A01 — export completion is inconsistent across apps

`FileSelectionStore.kt:84–92` files a save on a successful descriptor-close callback. Several callers use `openOutputStream(...).use { ... }`. If encoding/copying throws inside that block, normal resource cleanup still closes the stream without reporting a descriptor error. The provider then has no reliable indication that the bytes are incomplete.

A probe wrote a 15-byte DOCX fragment to an actual staged Files destination and delivered the same normal-close completion signal. Files imported it and marked it `saved`. This proves the protocol gap, not that a particular existing user document was damaged. Evidence: `docs/evidence/app-audit/A01.json`.

Affected patterns include `WriteEditor.kt:140`, `VideoEditorModel.kt:250–257`, `MailModel.kt:97`, and `SdkFilesEffects.kt:57–65`. Attempts to call `DocumentsContract.deleteDocument` do not solve internal saves: this selection provider is a ContentProvider whose `delete` is unsupported (`FileSelectionStore.kt:119`). Cleanup can therefore fail after a partial file was filed.

There is a second inconsistency: several apps report success immediately after stream close, while the Files import runs asynchronously. Photo Editor explicitly waits for the destination state to become `saved`; other exporters generally do not. Audio Editor already has the better `closeWithError` failure pattern in `AudioEditorStorage.kt:15`, but it still needs the shared final-filing acknowledgement.

**Fix direction:** one shared transactional export writer, explicit error-close on cancellation/failure, a final Files filing acknowledgement, and truthful recovery/status handling. Retain recovered bytes; never blindly delete arbitrary selected provider URIs.

### A02 — provider insertion and Inbox filing are not one recoverable transaction

`MessagingTransport.kt:243–280` detects duplicate SMS rows and only calls `MessageNotifications.incoming` inside `if (!duplicate)`. If a provider insertion succeeds and the process dies or folder persistence fails before filing finishes, the retry sees the inserted duplicate and skips folder filing. The raw recovery file can then be deleted without completing Inbox restoration.

For MMS, completed metadata is committed around line583, before the incoming-folder/notification call near line621. That call is wrapped in `runCatching`, and a later retry exits immediately for a completed job. A folder-write failure or inability to recover the thread ID can therefore be silently left unfinished.

**Fix direction:** durable, separately acknowledged provider-insert, conversation-filing and notification phases. Recover filing without reinserting messages or resurfacing an unrelated later duplicate broadcast. This is a storage/failure-window issue; normal Archive/Trash receiving is fixed and tested above.

### A03 — an alarm can still appear enabled after its schedule is lost

`ClockEngine.kt:60–70` changes AlarmManager scheduling before committing Clock state. A failed alarm edit cancels the newly scheduled PendingIntent in its catch block; that PendingIntent also replaced the previous schedule for the same alarm ID. The old alarm remains enabled in stored state with no scheduled callback. A failed new timer save has the inverse problem: a callback remains scheduled although no timer was stored. Cancellation/deletion paths have related ordering risks.

Fault injection reproduced both cases against production ClockDisk: an enabled saved 07:30 alarm with **zero** scheduled alarms after a failed edit, and **one** scheduled timer callback with **zero** saved timers. Evidence: `A03-alarm.json` and `A03-timer.json` under `docs/evidence/app-audit/`.

**Fix direction:** transactional scheduling with rollback to the previous alarm/timer and reconciliation on entry/recovery. A failed save must not silently cancel an existing alarm or leave a phantom callback.

### A04 — “All” and search have a narrower scope than their labels suggest

The Messenger initially loads 40 provider conversations and merges later pages on demand (`MessengerPane.kt:265–270,506`). Inbox/Archive/Trash filtering and the conversation search then operate on `rows`. Restore All uses the loaded `trashed` list near line451; Empty Trash builds IDs from loaded `rows` near line748. Unloaded Trash conversations are untouched. Search can say “No matching conversations” before older matching conversations have been loaded.

Pinned conversations already have an explicit lookup path; this review does **not** claim pins are missing just because they are outside the first page. Existing per-thread “Search messages” is a different function from this conversation-list search.

**Fix direction:** folder/search queries and bulk target enumeration independent of the display page. Keep streaming/paging for rendering, recheck folders/locks before destructive work, and retain threads that still contain locked messages. The existing Empty Trash row-removal code also needs reconciliation when a thread changes folders during the operation.

### A05 — sender matching assumes every identity is a number

`Messages.kt:18–24` strips everything except digits, then drops empty keys when comparing recipient sets. Production `sameRecipients("BANK", "DELIVERY")` returned true because both normalize to an empty set. The Rust `phone` validator (`core/src/messaging.rs:28`) also rejects nonnumeric sender names, so the existing block operation cannot represent such identities.

Evidence: `docs/evidence/app-audit/A05.json`. This does not prove Android has merged those provider conversations. It proves the app's matching predicate cannot distinguish them and that its blocking model cannot cover that sender form.

**Fix direction:** separate canonical telephone numbers from opaque/alphanumeric sender identities, use consistent identity rules in matching/blocking/drafts, and test email-style MMS senders and localized digits. Preserve existing US-number normalization.

### A06 — Reply All drops Cc participants

`MailProtocol.kt:44` captures From and To but does not capture original Cc. `MailHeader` (`MailData.kt:24`) has no Cc field. `MailModel.kt:86` builds Reply All from To and From only. A message addressed To the owner and Cc another person therefore produces a reply that leaves that person out.

**Fix direction:** preserve original Cc in header/body metadata and build de-duplicated To/Cc reply sets, excluding the owner's account and respecting Reply-To. Never expose Bcc.

## UI and usability refinements

1. **Calculator controls are not fully represented by the native presentation.** The signed controller still supports sign toggle and previous/next calculation actions, but `CalcWorkspace.kt` does not expose those three controls. History itself is still reachable from the shared header action and drawer; it is not missing. Reconcile the keypad with supported features without crowding small screens.
2. **The empty conversation list can show two separate empty-state messages.** `MessengerPane.kt:457` adds a folder/search-specific message and lines507–517 add another generic empty message. During search, the generic “choose a contact” text can contradict the intended search state. Use one loading/empty/search/error presentation.
3. **Some older UI tests target the previous UI.** For example, `UtilityAppsHostTest` expects old calculator `sdk-calc.*` tags and controls absent from the native layout; `VideoEditorUiTest` retains an older preview-height expectation. Current focused editor checks are explicitly isolated and source-matched. Do not treat historical UI reports or a successful test compilation as current full UI acceptance. Update the affected tests, including navigation, font size and keyboard cases, before broad-suite claims.

## Important feature/configuration gaps, not newly discovered bugs

- **Audio Editor:** preview is explicitly limited to 12 seconds (`AudioPreview.kt:11`), and opening another source replaces the single saved working project. Full-track/loop audition and named editable projects would substantially improve everyday editing. Existing edits and exports are not removed.
- **Background work:** YouTube transfers and editor exports do not have a unified durable background/resume queue. YouTube's dialog job is cancelled when its composition is disposed; Video Editor cancels work on lifecycle stop. Audiobooks already have a dedicated foreground download service and keep completed chapters. Bring other long operations up to that standard where appropriate.
- **Video missing-media repair:** missing media is now reported and edits are kept, with portable backup restore available. There is no per-source Relink workflow to repair a single missing file while preserving the project identity.
- **Social:** the installed/bundled Social controller is explicitly reserved and displays Hello World. It is not a completed social feed. Hide/label the unfinished app or define its intended scope before treating it as delivered functionality.
- **Microsoft mail sign-in:** the generated resource in this reviewed build has an empty Microsoft client ID. `MailOAuth.microsoft` therefore reports that sign-in is not configured. This is a build/provider configuration gap, not proof that IMAP/password mail is broken.

## Areas checked where older complaints were not reasserted without evidence

- Default-network recovery observes the validated OS-selected route; there is no forced Wi-Fi process binding in the reviewed recovery path. Podcast feed handling has an explicit downloaded/offline fallback. Actual Wi-Fi/mobile and screen-off acceptance still needs physical-device evidence.
- Ordinary open/save pickers route through OnKlav Files. The direct system tree picker is limited to connecting a phone folder. External-folder deletion has a permanent-delete confirmation; it was not incorrectly reported as an unannounced Trash operation.
- Photo save-back has a previous-version backup/replacement journal; its image processing uses bounded working data and catches memory failures. These protections were retained.
- Setup requests host permissions centrally and included apps receive their declared OnKlav capabilities. Explicit Android refusal and deliberate capability revocation remain effective. They are not bugs to bypass.
- Active Notes/backup recovery and document migration are required data protection, not abandoned code. Original Android/publisher identities, the single APK, shared speech pack and games are unchanged.
- The source documents that both selected upstream speech speakers are male although the owner-requested UI labels are Male and Female. This is an intentional label choice preserved from the user's instruction; the review did not replace a selected voice or reintroduce another model.

## Verification and limits

**21 unique functional regressions passed:** nine incoming-folder/block cases, one blocked-picture notification case, and eleven existing MMS recovery cases. The separate direct-store harness exercised the same nine cases and is not counted again. Production Kotlin and test sources compiled; runtime class matching is recorded in `app-audit-verification.json`.

**Five audit observations** reproduced the send-record limit, partial-file completion protocol, two alarm/timer save failures, and alphanumeric identity collision. These observations demonstrate defects; they are not functional acceptance tests. Probe code stays under `docs/fixtures/app-audit/`, outside the default regression suite. The first two Clock probes had a fluent-editor fault-injector error; that fixture was corrected and only the affected observations rerun. The retained initial report is not counted as a product regression failure.

A debug lint pass including production and test source was attempted with a **300-second limit**, reached analysis of the production and test variants, and was stopped without a final report. **No full lint pass is claimed.** No long retry loop was run. No new APK, phone installation, carrier transmission, provider-account sign-in, or real encoder/network-handoff acceptance was performed. Games received integration/source-boundary inspection, not a full gameplay audit. This is a deep source/risk review, not an independent security certification or a guarantee that every bug has been found.

## Recommended repair order

1. Fix outgoing receipt retention/compaction and the receiving filing journal (A00, A02), with late-callback/idempotence coverage and a source-matched Rust rebuild.
2. Unify transactional export/error-close/final-filing behavior (A01).
3. Fix Clock rollback/reconciliation after persistence failures (A03).
4. Make conversation search and bulk folder actions page-independent, then repair sender identities and Reply All (A04–A06).
5. Reconcile the native calculator and empty states, update stale UI tests, and implement the editing/background-work gaps in separate bounded changes.
