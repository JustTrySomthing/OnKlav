# Current reconciliation — 0.65.0 source only

Use TODO.md and RESUME-0.65.0.md as the current status. Historical entries below
are checkpoint records and are not a fresh list of missing implementations.
Eight-app document backup is complete; retain the installed speech engine.
Profile sharing was not in the original local-only Profile. Current source adds
Drive authorization/upload/public links and recovery, shared speech Settings,
bounded Files duplicates, provider-first read updates, Browser Find counts and
complete per-profile clear, and YouTube Back/rotation behavior. Existing capture,
viewer, group history/reactions, Browser handoff and Walkie transport/recovery are
implemented. Phone/account/release qualification is still unclosed; no APK built.

## Historical records follow

# Built-in app-document backup completed — 0.63.0 source only

Settings Backups > App Documents Backup now saves/restores all eight built-in
app-document stores together, including Walkie audio, recipients and preferences.
Existing data wins. Walkie recordings restore for manual playback only, without
replay/reconnect/autoplay, SIM/session/receipt/attempt state. The combined archive
is checked before any store commits; failed partial writes are retryable.
Messages metadata/history and Appearance themes keep their existing separate
backup paths. No accounts, credentials, permissions or packages are restored.
The product explains these boundaries in the combined-backup dialog.

Implementation is complete for this scope. Source only: no APKs until requested.
See RESUME-0.63.0.md and source-verification.json for final checks and limits.

# Browser and YouTube backups — 0.62.0 source checkpoint

Settings Backups has Browser Backup and YouTube Backup. Both use the existing
checked ZIP/review/confirmation/session guard/Stop/provider cleanup path.
Browser includes tabs (stable IDs/URLs/titles), selected tab, home/new-tab/search
settings and bookmarks. Pristine data takes saved selection/settings; current
selection/settings and existing IDs win in a merge. Missing tabs/bookmarks add
only within eight-tab/200-bookmark and 3 MiB/16 MiB store limits.

YouTube includes bookmarks and history, with canonical signed-out page/video
URLs and title/time validation. Existing URLs/video identities win, missing
history is ordered by time, and 200-bookmark/1,000-history limits reject overfull
merges without truncation. Viewer identity is generated/retained locally; it is
never serialized into the backup. No WebView cookies, profiles, sign-in data,
permissions, playback state or network operation is imported. Private URLs and
titles remain sensitive: the existing unencrypted-backup notice applies.

Typed validation and merged quotas precede one atomic store write. Source-only
checkpoint requested by user: no APK assembled, no old APK relabelled. Latest
delivered APK remains 0.61.0/code69. Tests/evidence: RESUME-0.62.0.md and
source-verification.json. Walkie portability and full SDK integration remain.

# RSS and Read Aloud backup continuation — 0.61.0

Settings Backups now includes RSS Backup and Read Aloud Backup. They reuse the
checked Stored ZIP format, review digest, explicit confirmation, Stop, active
SDK-session guard and provider/staging cleanup from 0.60.0. No guest capability,
SDK package, voice engine or icon changes are required.

RSS portably includes source metadata/enabled states, cached articles, bookmarks,
read marks and refresh/display settings. HTTP validators, last-check dates and
fetch errors are excluded for imported sources. Pristine data takes the saved
settings/source list, including explicit empty lists. Existing data merges only
missing source/bookmark/read identities; current settings and existing source
cache/bookmarks win. Entire merged URL/text/category/count/storage validation
precedes one atomic write. Restore does not fetch, trim caches or drop read marks
just to fit: an overfull merge rejects unchanged. Limits remain 32 feeds, 200
bookmarks, 4,000/128,000-unit read marks, 600 unique cached article URLs and 8 MiB
state / 16 MiB app private quota, with 40 articles per source.

Read Aloud exposes one current draft, so backup includes that saved draft only.
Restore keeps any existing saved draft, including cleared text, instead of storing
unreachable additional drafts. Fresh restore writes the validated document and
current pointer; a file-before-pointer interruption is safe to retry. The 50,000
UTF-16 editor limit and 16 MiB private quota are enforced. Shared installed voice
identities/settings, playback position/state and runtime permissions stay local.
Older rewrite-preference adoption remains the normal app-open flow; this backup
covers canonical SDK records and never reads private Inner Circle data.

Next: Browser/YouTube backups and Walkie portability. This is not full SDK-data
backup; process-death/real-provider and phone acceptance remain separate.
See RESUME-0.61.0.md / source-verification.json for actual production evidence.

# Current backup continuation — 0.60.0

Settings > Backups now has Files Backup and Annotated Images Backup child dialogs,
in addition to Notes Backup and existing metadata/history flows. Files exports
active ordinary files/folders; Notes, Trash, System packages and Android folder
links are excluded. Annotation exports editable JPEGs, marks and undo/redo, with
last-open/viewport/runtime/grant state excluded. These are separate portable ZIPs.
Existing IDs (including Trash) win. Same-name new Files items get unique names.
New children of a conflicting/trashed existing folder reject before publication.

Host path: LibraryBackupDialog -> LibraryBackup / AppSdk.staging -> SDK host-only
library_backup -> Rust library_backup -> content::backup or drawing backup helpers.
No guest capability or package permission was added. New archive has strict v1
kind/schema, exact Stored entry set, CRC and per-payload SHA checks. The entire
archive digest binds restore to review. File/graph/mark/history/JPEG-dimension,
quota, symlink-entry and count checks run before publishing imported records.

512 MiB archive cap, 256 MiB per ordinary file, 24 MiB manifest, bounded item
counts and 64 KiB binary copying; Annotation retains its existing 16 MiB private
library cap and 600 KiB JPEG limit. Staging/output/archive copying needs roughly
twice backup size; restore additionally needs space for new library content.
The UI explains privacy/space and offers Stop. Exports verify private ZIPs before
provider copying, require a successful close and attempt incomplete-output deletion.
No remote-provider readback is claimed. Cached reviews expire after a day and are
removed on dismissal; process-death resumption and device/provider acceptance remain.

Files publishes one atomic index after durable payload copying. Interrupted copies
may leave unindexed payloads, retained for safe retry. Annotation publishes each
complete image document after its JPEG, so a failure can leave a partial merge.
Retries reuse exactly matching payloads and keep existing records; conflicting
unindexed bytes reject rather than overwrite. Restore requires all SDK sessions
closed. No automatic restore, grants, credentials, sends or package install occurs.

RSS/Reader/Browser/YouTube/Walkie stores and full SDK backup integration remain.
See RESUME-0.60.0.md and source-verification.json for current evidence.

# Current Settings continuation — 0.59.0

Settings > Storage > Backups now includes a separate Notes Backup child dialog.
It exports active canonical Notes (titles/text/IDs/timestamps), checks all records
and limits before restore, reviews new/existing counts, and merges only missing
IDs after confirmation. Existing records, including Trash, remain unchanged.
Restored notes enter Notes; folder organization and Trash are excluded. JSON is
unencrypted, bounded to 16 MiB/2,000 notes. SDK packages/grants/credentials/runtime
state and other apps' documents are excluded. Existing metadata v1 is unchanged.

Host path: NotesBackupDialog -> NotesBackup -> AppSdk.sync(notes_backup) -> Rust
SDK session mutex -> content::notes_backup -> canonical Notes files + Files index.
Staging never crosses JNI as a giant JSON request. Restore checks the reviewed
SHA, validates all documents/quota first and rejects active SDK sessions. Individual
notes commit atomically; one index reconciliation follows. Filesystem failure can
leave a partial merge; the confirmation explains retry, and existing IDs win.
Provider exports require a successful close; failure attempts document deletion
and reports when cleanup could not be confirmed. Private staging is cleaned in
finally; process-death/real-provider acceptance remains open.

History restore is already implemented behind explicit carrier-test/role gates;
old unavailable statements below describe earlier checkpoints. Full SDK-data
backup remains open: Files/blobs/folders, Annotation, RSS, Reader, Browser/YouTube
and Walkie durable stores require distinct portability rules. Drive provider
upload is already implemented; direct account/OAuth service remains separate.

## Historical Settings implementation records

# Settings and Appearance — 0.24.0

Read-only reference: Inner Circle 2.2.85 Settings menu, AppearanceDialog,
BackgroundPicker/theme transfer, WalkiePreferences, QuietHoursUi,
NotificationSettingsUi, SpamScreen, StorageUi, BackupDialog and GamePreferences.
Only visual/behavior requirements were used. All implementation is new
Kotlin/Compose and Rust; no Java application source or old app binary is included.

## UI and production paths

| Entry | Implementation / behavior |
| --- | --- |
| Settings page | Original ordering/artwork and 64dp tiles; narrow screens and large text use a single column, including Appearance/Profile shortcuts. |
| Appearance | Staged message preview, original font sizes/styles, colors, gradients, wallpaper/overlays, original artwork, named/preset themes, data-only .onklav-t import/export/share and help. Apply persists; Cancel discards the draft. Busy child navigation is blocked; missing document/image pickers report an error. |
| Dialogs | Shared 18dp rounded surface, reference inset actions, scrollable body and persistent footer. Height is bounded by the actual dialog viewport and keyboard; footer actions remain horizontally reachable when text cannot fit. |
| Profile | Existing local name/number/birthday/photo editor and validation, with discard confirmation. Profile sharing remains a separate unfinished service. |
| Setup / Help | Existing OS access/role status, phone number, notification access, SIM selection and recovery controls. Help's Sending SIM and pending-picture routes now use real screens, not placeholder text. MMS/default-role gates stay unchanged. |
| Sounds | Notifications uses staged persisted preferences. Chat Sounds reads conversations off the UI thread, searches them and opens a registered per-conversation Android channel. Incoming notifications use the channel when global sound/vibration/badge preferences allow it; global restrictions take precedence. |
| Quiet Hours | Original 80dp From/Until controls and 54dp Repeat control, system-local time formatting, staged repeat-days child dialog. Save rejects zero days/equal endpoints and verifies persistence. Cancel never commits edits. |
| Reminders | Existing real message reminder manager, schedule/reschedule/remove/open-message flow, rather than unreachable old placeholder code. |
| Security | Spam warning preference and trusted-sender reset; local suspicious-link wording checks only with contacts permission and an unknown sender. Messages displays a review warning with explicit Trust/Block actions. No automatic deletion/moving or uploads. Blocked Senders uses Rust state; Blocked Messages reads retained matching conversations and opens them. |
| Storage | Asynchronous bounded attachment report (20 largest, at most 10,000 inspected), provider-size unknown reporting, open original conversation. Explicit confirmed cleanup removes only export copies over two days old; symlinks, live voice, MMS files, drafts, themes and other app data are excluded. |
| Backups | SAF export/inspect/confirm/restore of bounded Rust-validated app metadata. No message bodies, permissions, pending sends, app documents or media. Reminder excerpts are stripped by the host exporter. Merge preserves existing entries; cross-install imports skip phone-local folders/message flags; imported reminders never schedule. SMS/MMS history ZIP export and read-only import checking/review are available in a dedicated child dialog; history restore is still unavailable. See HISTORY-BACKUP.md. |
| Read Aloud | Installed offline voice choices with locale/name, speed, preview error feedback and lifecycle stop. Uses the same reader preferences as message speech. Original bundled voice packs remain unported. |
| Apps | Existing real signed SDK installer, package export/share, removal and revocable capability manager; stays distinct from the launcher Apps tab. |
| Games | Existing included catalog/visibility/package inspection; Game Settings now saves sound preference and the original 30–60 second timeout in 5-second increments. These preferences await the game ports; external Godot installation/execution remains disabled. |
| Walkie Talkie | Host-only SDK walkie_preferences -> installed fixed Walkie identity + storage.private grant -> Rust walkie_store; same auto-play/send/beep values used by the signed app. No communications/microphone permission is added. History opens the actual app archive workflow. Carrier operation remains gated. |
| Privacy | Corrected policy for the current restricted SDK, private data, explicit app grants and network providers. Automatic message YouTube previews now use an explicit default-off switch, bounded image requests and cancellation; Play remains user-initiated. |
| Google Drive | Existing original-style large-file sharing screen with consent/privacy explanation. Connection remains disabled because the Drive account/upload service is not ported; no fake sign-in or external upload. |

## Verification boundary

Final counts and results are in source-verification.json and evidence/v0.24.0.
Tests cover real Rust/JNI metadata backup validation/merge, grants denied/revoked
for host Walkie preference access, saved Settings behavior, Quiet Hours child
Cancel/Done/Save, Appearance draft/Apply/Cancel and nested background Cancel,
large-text reachability, cache exclusions and local spam policy. Robolectric PixelCopy screenshot capture timed out and was removed from the
functional tests; no rendered comparison or phone acceptance is claimed. The
backup JNI test now uses the same native-graphics sandbox configuration as the
other JNI/Compose tests, avoiding a second native-library classloader.

No APK/mobile ABI rebuild, sign-in, SMS-role change, actual carrier send,
real voice, account upload or private Inner Circle data import was performed.
The full platform is not complete. Remaining Settings services are Google Drive,
SMS/MMS history restore and provider acceptance, original bundled speech
voices, profile sharing and external game installation/runtime. Device/OEM
permission pickers, notification channels, provider queries, speech/audio and
full reference visual/accessibility parity still need handset acceptance.

Final current verification: 86 Rust +61 Kotlin tests pass (147), zero failures.
Full app/shared host invocation passed; runtime classes match current compiled Kotlin.
No APK/mobile ABI build, real carrier/voice/account or phone/pixel acceptance.
Settings UI implementation pass complete; remaining services are explicitly tracked.

## 0.25.0 — automatic message YouTube previews

Privacy's default-off Automatic YouTube Previews switch is connected to shared
Messages/Groups video cards. Explicit Play stays available when thumbnails are
off. Bounded validated-ID CDN requests, cancellation/cache invalidation and an
internal signed YouTube SDK v2 handoff replace the disabled control. Existing
YouTube v1 installs use a reviewed Update Included App action. No silent grants,
APK, real provider/playback/account/carrier acceptance. See RESUME-0.25.0.md and
source-verification.json; other Settings services and handset checks stay open.

Final current verification: 87 Rust +65 Kotlin host tests passed (152), zero failures.
Full app/shared host invocation green; current runtime-class freshness verified.
YouTube v2 retains publisher; all other SDK package hashes unchanged.
No APK/mobile ABI rebuild, real provider/page/playback, account or carrier acceptance.

## 0.26.0 — SMS/MMS history export

Settings's Messages and Media Backup now exports actual SMS/MMS provider records,
addresses, text/SMIL and downloaded binary parts through Kotlin IO and real Rust
streamed ZIP/checksum verification. Includes app metadata, SAF save, progress,
Stop, permission/change/error guards and private staging cleanup. No provider
writes or APK. History restore remains disabled. Best-effort provider observation
is not a cross-table transaction; OEM/handset acceptance is pending. Limits and
exact production/schema/verification boundaries are in HISTORY-BACKUP.md.

Final current verification: 91 Rust +69 Kotlin host tests passed (160), zero failures.
Full app/shared invocation and runtime-class freshness checks passed. No APK/mobile
ABI build, real history/provider/phone/carrier/account or rendered parity acceptance.

## 0.27.0 — checked history import review

Choose backup to restore now opens an OnKlav history ZIP for read-only checking.
Kotlin owns SAF copying/progress/Stop; Rust strictly validates generated paths,
ZIP attributes/method/extents/limits, hashes/CRC, record/metadata shapes and counts.
Private copies are removed before a compact summary is shown. No SMS permission
or default role is needed for inspection. Restore messages remains disabled;
provider merge/ID mapping/recovery and handset/MMS acceptance remain unfinished.
MMS_READY/default role are unchanged. See HISTORY-BACKUP.md and RESUME-0.27.0.md.

Final current verification: 97 Rust +72 Kotlin host tests passed (169), zero failures.
Final complete app/shared invocation was forced with current JNI; runtime-class
freshness verified. No APK/mobile ABI build, actual user-history/provider/account/
phone/carrier or rendered parity acceptance. History provider restore remains unavailable.

## 0.28.0 — read-only duplicate comparison

Backup review now offers an explicit READ_SMS Compare with phone action. Shared
Kotlin capture and Rust content matching compare SMS/MMS (including recipients
and exact media bytes) across changed provider IDs. Unique matches yield existing
message and conservative flag/thread binding candidates; ambiguity is held for
review, unsent/undownloaded rows skipped. The reviewed ZIP hash is bound, failed
comparisons clear prior results, and all private snapshots/maps are removed.
Only counts reach the UI. Nothing is restored or sent; provider merge/recovery,
new-ID/final metadata mapping and handset/MMS acceptance remain open. No APK.
See HISTORY-BACKUP.md and RESUME-0.28.0.md for exact boundaries.

Final current verification: 102 Rust +76 Kotlin host tests passed (178), zero failures.
Complete app/shared invocation passed with current JNI; runtime-class
freshness verified. No APK/mobile ABI build, actual user-history/provider/account/
phone/carrier or rendered parity acceptance. History provider restore remains unavailable.

0.29.0 adds a gated Kotlin restore executor/Android adapter and durable Rust
per-message journal; Settings reads/recover-classifies pending outcomes. This is
backend progress, not enabled restore. Known incomplete writes can be cleaned;
unknown insert results block retries, committed IDs remain recorded. Archive
apply orchestration, final metadata merge, journal compaction and real phone/MMS
acceptance remain open. See RESUME-0.29.0.md and HISTORY-BACKUP.md. No APK.

Final current verification (.29): 107 Rust +85 Kotlin host tests passed (192),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.30.0 adds the gate-closed archive-to-executor coordinator and durable partial/
complete message outcome maps derived from committed journal IDs. Fresh archive/
plan binding, all-candidate preflight and host local MMS identities are required.
The Restore UI and phone writes remain disabled. Post-write provider revalidation,
folder/flag merge, recovery/configuration UI, compaction and phone/MMS acceptance
remain open. See RESUME-0.30.0.md and HISTORY-BACKUP.md. No APK.

Final current verification (.30): 111 Rust +91 Kotlin host tests passed (202),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.31.0 adds post-insert provider capture/content matching and conservative folder/
flag merge with current-entry precedence and atomic state/completion receipts.
Imported reminders and stale thread unread overrides are disabled. Fixture tests
are not actual provider/phone acceptance; Restore UI and MMS gate remain disabled.
Recovery/configuration UI, durable handoff/compaction and phone acceptance remain.
See RESUME-0.31.0.md and HISTORY-BACKUP.md. No APK.

Final current verification (.31): 114 Rust +95 Kotlin host tests passed (209),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.32.0 adds ReferenceDialog restore settings/recovery review, staged private local
MMS identities, interrupted-insert review and saved outcome count/detail pages.
Receipt-backed Finish saved record only repairs acknowledgement; unknown inserts
stay paused. Cleanup and Restore messages remain gate-closed. Compaction, actual
resume/apply review and real phone acceptance remain unfinished. No APK.
See RESUME-0.32.0.md and HISTORY-BACKUP.md.

Final current verification (.32): 118 Rust +99 Kotlin host tests passed (217),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.33.0 — journal compaction

Settings Backups > Messages and media > Restore settings and recovery now offers
Compact completed journal records, with Cancel/Compact confirmation. Kotlin uses
HistoryRestore.exclusive -> HistoryRecovery.compact -> trusted NativeCore
history_recovery/compact_journal -> Rust history_journal/compact. This is a private
storage operation; it requests no provider access or permissions.

The bounded terminal.archive retains complete committed/rolled-back transactions,
including archive/source identity, actual target ID and ownership proof. Readers
validate the archive and reconcile identical copies from an interrupted handoff.
Compaction fsyncs the replacement archive and directory before unlinking any
terminal raw records, then syncs the directory again. A crash before deletion
leaves duplicate evidence and a repeat can finish pruning. Conflicting copies,
corrupt data, symlink storage and archive limits fail closed. Nonterminal records
are never archived or removed, including uncertain and needs-review outcomes.

All receipt consumers and begin/replay checks read both stores. A compacted
commit cannot be inserted again or cleaned; recover does not re-expand terminal
records. Status preserves total outcome counts while reporting active slots and
archived counts separately. At most 1000 raw records and 10000 archived terminal
receipts (8 MiB maximum) are retained. A full archive refuses further compaction;
there is no reset/forget to discard duplicate protection. This is bounded journal
compaction only: saved outcome maps and messenger metadata receipts remain intact
at their existing 100-record limits. No capacity claim is made for those stores.

Five new Rust tests exercise the real receipt/replay consumers, interruption after
archive commit, unknown preservation, conflicting/corrupt/symlink archive refusal,
full active-journal capacity recovery and full-archive refusal without evidence
loss. One Compose/JNI check exercises Cancel/Compact, retained receipts/replay
protection and uncertain outcome retention. Host tests do not establish filesystem
power-loss, OEM or handset acceptance.


See RESUME-0.33.0.md for limits, recovery fixes and remaining work.

Final current verification (.33): 123 Rust +100 Kotlin host tests passed (223),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.34.0 — completed restore handoff

Settings Backups > Messages and media > Restore settings and recovery now offers
Compact completed restore records with explicit Cancel/Compact confirmation.
Kotlin HistoryRecovery.compactCompleted under HistoryRestore.exclusive calls
trusted NativeCore history_recovery/compact_completed -> Rust history_completed.
This complements the .33 terminal-message journal archive; it does not grant
provider access, perform a restore, or alter the installed internal SDK programs.

Only complete, acknowledged maps with matching durable metadata receipts qualify.
Rust validates every raw map; pending, need-review or unacknowledged records stay in full.
Inserted rows additionally require a matching committed journal transaction for
archive, source, actual target and transaction ID. Missing/forged receipts, invalid
headers/rows and changed acknowledgement counts/hashes fail closed. Committed
message receipts are retained in the existing raw/compacted journal.

The private history-completed.archive contains versioned completion summaries,
original-map SHA-256 and the full metadata completion receipt (archive/plan/post
hashes and imported counts). It contains no message bodies, addresses or media.
It is bounded to 1000 completed restores/2 MiB. Active maps and active metadata
receipts retain their existing 100-record caps. Overflow fails before pruning;
there is no reset/forget that drops replay protection. All maps are prevalidated
before handoff. Pending/unknown journal entries are untouched.

Handoff order: fsync replacement archive, atomic rename and directory fsync;
under messenger state mutex, reload latest state, validate duplicate receipts and
atomically remove only receipt entries already durably archived; then validate
raw-map hashes again, unlink only those completed maps and sync their directory.
Other messenger state, including flags, folders, drafts, reminders and outgoing
operations, is preserved. A crash before either prune stage leaves duplicate
evidence. Repeating compaction finishes those stages without reapplying metadata.
Conflicting raw/archive or active/archive copies, duplicate archive IDs, malformed
archives and symlink storage fail closed. Archive readers use atomic snapshots
without acquiring the compactor mutex while holding the messenger mutex.

Messenger receipt lookup and merge replay checks consult both stores. Replaying
the same job cannot resurrect a later deleted flag after its active receipt is
compacted. Archived acknowledgement repair and merge return the saved result,
validate surviving copies and never recreate full maps or reapply metadata.
Finish refuses an already archived job. Archive summaries remain visible in the
same recovery list and detail dialog; stable pages of 20 include archived IDs,
with bounded offsets up to 1100. Interrupted records keep their full detail.


See RESUME-0.34.0.md for verification scope and remaining work.

Final current verification (.34): 130 Rust +101 Kotlin host tests passed (231),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.35.0 — reviewed restore confirmation

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


See RESUME-0.35.0.md for tests and remaining limits.

Final current verification (.35): 130 Rust +107 Kotlin host tests passed (237),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.36.0 — recorded metadata continuation

Settings Backups > Restore settings and recovery > saved Restore record now has
Reopen original backup when metadata is unfinished. Rust resume_info validates
the bounded saved map and returns its original archive SHA, original plan SHA,
record ID and full map SHA. Pending map rows or any interrupted journal insertion
block this operation before Files or phone reads. Inserted outcomes require their
matching committed journal transaction, archive/source/actual target, including
receipts retained by the .33 archive. Completed metadata uses Finish saved record
instead; it cannot enter a new metadata continuation.

The new ReferenceDialog route offers Choose original backup and explicit READ_SMS
when needed, then Kotlin HistoryResume.review -> HistoryImport.inspectResume ->
fresh bounded SAF copy, full existing ZIP/hash/schema verification and observed
phone snapshot -> Rust history_resume.compare. The archive SHA must match the
saved record. Every original source must be covered exactly; each saved inserted
or matched message must remain a unique content match at the exact recorded ID.
Wrong archives, absent/changed/wrong-ID/ambiguous targets and changed map bytes
fail closed. The temporary extracted history and comparison are always cleaned.
No message bodies, addresses or provider paths are returned in recovery summaries.

A successful recheck opens the .35 one-use confirmation, additionally bound to
record ID/map SHA/archive SHA. It shows the reviewed phone identities and says
that this continuation inserts no messages. Cancel/disposal ends the ticket.
Normal HistoryApply.applyChecked/runReviewed explicitly refuse resume tickets,
so they cannot accidentally start a second insert batch. The Continue/Restore
action remains disabled by the unchanged MMS acceptance gate; the production
HistoryResume.applyChecked repeats the gate before a phone snapshot or SAF open.

The controlled continuation path holds the existing restore operation lock,
checks current identities and saved-map binding, consumes the ticket once,
reopens/checks the archive and compares again against the reviewed fresh plan.
It then performs another observed snapshot through the existing post-write
verification path. Native history_import/resume_merge validates the prepared
marker, original map digest and journal receipts, keeps the original map/plan/job
identity, verifies exact targets/content again, and rechecks the map digest before
state commit. It uses the existing current-entry-preserving metadata merger and
its atomic completion receipt, then acknowledges the original outcome map. It
never calls the insertion or deletion executor and creates no additional outcome
map. Current flags/folders win; imported reminders remain disabled. Metadata
receipts continue to prevent replay/resurrection and can later be compacted.

This is metadata-only continuation for a completed message batch. It does not
resume missing/pending messages, resolve unknown or changed insertion outcomes,
or retry failed message inserts automatically. Original review/skip decisions
remain deferred; their full records are retained. A commit followed by an
acknowledgement/observation failure must be reviewed using the durable receipt,
not blindly reapplied. Private filesystem/JNI and observer checks do not make
Android providers and private state one atomic transaction.


See RESUME-0.36.0.md for verification and remaining work.

Final current verification (.36): 134 Rust +113 Kotlin host tests passed (247),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.37.0 — review of an already removed recorded insertion

A changed/incomplete insertion with an actual recorded target now has an explicit
Check already removed message review. The host verifies that exact parent and
all MMS children are absent twice before acknowledging rolled_back, with a durable
verified_absent marker and all original evidence retained through compaction.
Present rows, children, read/access/cancellation failures and reappearance stay
pending. Unknown-ID and committed outcomes cannot use this path. No insert,
delete or automatic retry occurs; saved partial maps remain unchanged. The real
Check action remains MMS-gated and unavailable pending phone acceptance.
See RESUME-0.37.0.md for production path, tests and remaining limitations.

Final current verification (.37): 137 Rust +119 Kotlin host tests passed (256),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

## 0.38.0 — partial-message continuation under the original restore record

The saved-record flow now reviews and inserts only its still-missing pending
sources. Completed targets are checked by exact ID/content and never reinserted;
original review/skip rows stay deferred. Durable journal receipts recover commits
lost before map acknowledgement. Stop/error checkpoints update the original map
without clearing unknown inserts; unknown outcomes stay blocked. A second
observed snapshot precedes the original-job metadata merge. The real Restore
control remains disabled pending phone/MMS acceptance. No APK was built.
See RESUME-0.38.0.md for the production path, tests and remaining blockers.

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

## 0.39.0 — requested chrome, motion and branding correction

Full-width header/navigation paint, explicit system-bar backplates, eased popup/
drawer entrances with retained exit contents, and the new purple/white vector
O/K speech-bubble launcher/splash are in the verified debug APK (code46).
127 current Kotlin tests plus 141 unchanged Rust checks; 268 total, zero final
failures. No skips or increased timeouts. Phone visual/carrier acceptance and the
full migration remain open. See RESUME-0.39.0.md and verification.json.

## 0.40.0 — right-side motion and Messenger UI

Requested screen/drawer exit retention, right-side motion, drag ghost removal,
keypad/phone formatting, contacts layout/shortcuts and Trash controls are in the
verified debug APK (code47). 135 current Kotlin checks passed; 141 Rust checks
are reused unchanged and source/library hashes verified. No physical-device,
carrier or exact pixel/frame-time acceptance. See RESUME-0.40.0.md. Phone-write
and live transport gates remain closed; full migration remains unfinished.
