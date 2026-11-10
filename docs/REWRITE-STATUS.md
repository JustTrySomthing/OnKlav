# Current reconciliation — 0.68.0 release candidate

Use TODO.md, RESUME-0.68.0.md and source-verification.json for current status.
The0.67 broad theme/layout, memory/provider-security, backup and legacy cleanup
is retained. The current follow-up fixes centered dialog/panel motion, the
Read/Unread draft and save flow, reference wand, Contacts add action, Walkie
contrast/contact-name display and optimized Godot native bridge retention.
24 affected/shared checks pass. No broad test/emulator loop was repeated.
Games feature work remains deferred; only the demonstrated launch-packaging
regression is addressed. Phone/account/carrier and independent SDK security
qualifications remain unclosed. Historical records below are prior evidence,
not competing current build instructions.

## Historical records follow

# Rewrite status — development checkpoint, not a finished product

Current continuation: see RESUME-0.48.0.md and source-verification.json for the latest
production changes and test evidence. Historical sections below describe earlier
checkpoints and do not supersede that record.

Charles has deferred game remakes; use NON-GAME-REVIEW.md for current priorities.

The user asked to finish the entire rewrite. This checkpoint makes real progress
but does not fulfill that complete scope. Do not present it as finished.

| Area | New implementation | Remaining work |
| --- | --- | --- |
| Old implementation removal | New project excludes all 405 legacy Java files and the old library dependency | Verify this invariant on every source/APK checkpoint |
| Five-tab shell | Kotlin/Compose Messages, Groups, Apps, Games, Settings; sparse paged drag/drop desktop, original glyphs, compact controls; separate debug identity | Full reference visual/interaction parity; further large-font/handset/accessibility acceptance |
| Shared core | Rust atomic private document/messenger state, bounds/ID validation, search, deletion, archive inspection and MMS wire codec; actual Kotlin JNI consumers | Public ABI/schema contract, broader acceptance and independent review of the implemented signing/installation/trust workflow |
| Files | Signed Rust/Wasm SDK explorer, central content storage, canonical Notes, Trash, protected System packages, scoped Android folders; see FILES-APP.md | Phone/provider/process/accessibility acceptance and further public-content integration |
| Notes | Rust Wasm SDK controller and generic Kotlin/Compose renderer; 80/100000 UTF-16 limits, 500ms autosave, reopen, search/delete drawer, copy/paste, Files export and sharing; real Rust storage; SDK private documents, installer/removal/permissions; see APPS-PORT.md | Clean visual/handset acceptance, broader lifecycle/export/sharing cases and reference comparison |
| Browser | Signed Rust/Wasm SDK controller, scoped Rust tab/bookmark/settings store, generic Compose web component, host-derived website profiles and live web/clipboard gates; see BROWSER-PORT.md | Device website/profile/media acceptance, full original behavior/style, prior data adoption and acceptance of native website-data cleanup |
| YouTube | Signed Rust/Wasm SDK controller; scoped Rust video history/bookmarks; generic Compose viewer/media rows; signed-out host policy, fixed ad handling and fullscreen support; see YOUTUBE-PORT.md | Handset playback/profile/account/ad acceptance, exact reference style/private mode/rotation, acceptance of internal Browser handoff and native data cleanup |
| Read Aloud | Signed Rust/Wasm SDK controller; scoped Rust drafts, original 50k editor/Paste/Clear/transport layout; generic Kotlin offline speech component with live capability/lifecycle checks; see READER-PORT.md | Original bundled voice identities/shared messenger engine, acceptance of prior own-draft adoption and exact handset/audio/accessibility parity |
| Annotation | Signed Rust/Wasm SDK controller; Rust durable image library, text/stroke/move/erase edits and undo/redo; generic Kotlin canvas and image import/export components; see ANNOTATION-PORT.md | Handset gesture/accessibility/exact-style and rotation/process/OS-provider recovery acceptance; included in current host APK, phone acceptance remains open |
| RSS | Signed Rust/Wasm SDK controller, scoped Rust offline cache/bookmarks/read/settings, Kotlin HTTP/XML broker; see RSS-PORT.md | Exact cards/search-row/scroll parity, own prior Kotlin feed-list import, publisher/handset acceptance and final Browser surface |
| Conversation media | Three-column full-history MMS media grid, internal image/GIF pager and audio/video controls, save/share/settings, theme-filled stars | Handset rendering, gestures, media providers and exact original polish acceptance; see MEDIA-CARRIER-0.48.0.md |
| Messages | Kotlin/Compose inbox/chat UI, SMS/MMS provider queries, saved drafts/folders/flags through Rust JNI; SMS multipart outcome ledger; attachments/gallery/starred/copy/forward/lock/remind; Setup, notification and Quiet Hours integration; Rust MMS codec and gated Android transport | Explicit default-off handset trial exposes manual default-role selection; real carrier receiving/sending acceptance remains open; real carrier/dual-SIM/notifications/recovery acceptance, camera/voice capture, exact UI parity, full spam/blocked/incoming-reaction behavior; see MESSAGING-PORT.md |
| Groups | Kotlin/Compose list/editor/contact picker/members sheet; real Rust durable saved groups and controls; shared conversation UI; Rust post/reaction envelope compatibility | Carrier group MMS/default-role gate remains closed; handset acceptance of bounded historical search/parent context, friend activities and exact visual/lifecycle parity; see GROUPS-PORT.md |
| Social | Permission-free Rust/Wasm Hello World placeholder as explicitly requested | No feed or social service requested; see SOCIAL-PLACEHOLDER.md |
| Walkie-Talkie | Signed Rust/Wasm SDK offline shell, scoped Rust preferences/recipients and bounded private clip archive/outcome ledger, shared Compose dial/settings/history and host-owned foreground recorder/player with explicit audio grants; typed wire/floor/audio/capture rules | Phone audio/focus/lifecycle acceptance, authenticated live sessions, background scheduling, warm-up/beeps, original full UI and SMS/MMS integration; see WALKIE-PORT.md |
| Godot | Host-embedded Godot library, curated trusted bundled PCK, original Air Hockey solo rules/UI/current-match lifecycle on Godot physics plus unfinished exercises for other game types | Air Hockey handset/fairness acceptance; other original games and Panda artwork/control/AI/campaign/audio/accessibility; friend play; two internal game package proofs and crash-containment tests |
| Settings | Staged Appearance preview/Apply/Cancel, 6 font styles, 15 palettes, gradients, 28 original backgrounds/overlays, 20 presets, named themes, bounded data-only theme transfer, shared dialog/chrome styling and Settings icons | Many settings service implementations and integration; reference layouts and separate app/game manager catalogs restored, working hide/show; see HOME-REFERENCE.md; see UI-PARITY.md |
| Developer SDK | Signed .onklav-a Rust/Wasm packages; restricted interpreter, Kotlin renderer, Rust permission/private-document broker, installer/update/remove/revoke/export; Notes and external Counter example | Initial development preview only; Walkie app port, acceptance of implemented network/media/TTS/drawing brokers, remaining live communications, custom icons and blob collection, independent security review and external Godot package runtime |
| Performance/stability | No language-only improvement claims | On-device baseline, startup/frame/memory/crash metrics and evidence-based fixes |

## Architecture correction

Only OnKlav is an APK. Tools and Godot are host libraries; built-ins open inside
OnKlav. The previously delivered standalone Notes APK is retired as an incorrect
delivery model. See PLATFORM-CONTRACT.md for the internal package formats,
original Settings management paths and required capability boundary. The initial app SDK runtime/installer/broker is implemented; broader app services
and the separate game SDK remain unfinished. See APPS-PORT.md and sdk/README.md.
The trusted engine process shares the host UID: it is not a security sandbox.

## Evidence boundary

Current non-game reliability checkpoint is 0.47.0-dev/code54.
Use source-verification.json for the fresh cleanup checks and apk-verification.json
for the exact last APK. Native JNI, SDK Wasm and Godot inputs are unchanged and
source-matched. Historical version sections below are not current build claims.
Framework-host tests and APK checks are separate from handset/carrier acceptance.
MMS_READY remains false; the current default messenger is unchanged.

## Resume priorities

Finish carrier MMS/default-role and messenger acceptance in MESSAGING-PORT.md,
and continue the Walkie app port and SDK services in bounded chunks, preserving the original Settings managers and
Appearance theme installation. Continue each reference app, transport operation
against the gap ledger. Game remakes are explicitly deferred until non-game work is ready. Engine exercises must not
be substituted for the original games. Preserve the latest right-side drawer motion and original
popup formatting requested by Charles. Do not return to per-app APK delivery.

Historical 0.20.0 source added host-owned Walkie contact selection and durable recipient names (TODO 3a); live verification/session transport remains incomplete. See RESUME-0.20.0.md.

## 0.21.0 — outgoing session foundation (partial TODO 3b)

Rust now persists one bounded outgoing control session with phone/SIM/nonce
binding, queued/claimed/accepted distinctions, verify/invite/idle timeouts,
replay rejection and restart interruption. The signed app consumes recovery
status and shows the compact unavailable-connection dialog. Actual transport
remains disabled and cannot be enabled by guest JSON; machine success paths
are fixtures, not a live connection or cryptographic peer authentication.
See RESUME-0.21.0.md for the exact production path and remaining broker,
receiver/consent, blocked/rate/lifecycle, dial/audio and carrier gaps. Item 3b
and full item 3 remain unchecked. No APK/mobile build or default role change.

## 0.22.0 — incoming consent/permission foundation (3b remains partial)

Default-off communications.walkie grant gates invitations independently of audio.
The private Rust incoming controller verifies matched probe-reply/send state,
requires explicit consent, bounds automatic replies and rejects stale/foreign
controls. Signed Wasm renders the invitation and local decline; revocation cancels
pending state. All production receive/send calls remain disabled, with successful
control paths exercised only by test fixtures. Actual Android carrier receiver,
host blocked-policy/confirmation, simultaneous invitation and lifecycle handling
remain open. See RESUME-0.22.0.md and source-verification.json. No APK.

## 0.23.0 — Walkie carrier/audio integration; not operationally accepted

Signed Walkie v7 now calls the host-owned carrier bridge for invitations,
consent, floor-controlled audio, offline Send, explicit Retry and receipts.
Rust validates peer/SIM/session/attempt and live grants at the final handoff;
Kotlin owns SmsManager, protected receiver/results, private audio and policy.
Warm-up, beeps, foreground Auto Play and Auto Send are integrated. Successful
carrier/audio tests use fixtures. MMS_READY remains false; default role unchanged.
No phones are available, no real messages/audio were verified and no APK built.
Background/restart/late-arrival behavior, cleanup review and full original UI
acceptance remain open. TODO 3/5 stay incomplete. See RESUME-0.23.0.md and
source-verification.json for production paths, limitations and final evidence.

## 0.24.0 — Settings and Appearance UI cleanup

Original Settings entry order/artwork and Appearance/theme flows are retained.
Real persisted Walkie preferences, chat notification channels, blocked-message
review, safe storage reporting/cleanup, checked metadata backup/restore and local
spam warnings replace old Settings shells. Quiet Hours restores compact time and
repeat controls; dialogs and shortcuts adapt to small viewports/large text.
Read Aloud handles offline voices/preview lifecycle. Game preferences persist
for the pending ports. Full service parity is not claimed: see SETTINGS-PORT.md,
RESUME-0.24.0.md and source-verification.json. No APK or carrier/account operation.

## 0.25.0 — automatic message YouTube previews

Privacy's default-off Automatic YouTube Previews switch is connected to shared
Messages/Groups video cards. Explicit Play stays available when thumbnails are
off. Bounded validated-ID CDN requests, cancellation/cache invalidation and an
internal signed YouTube SDK v2 handoff replace the disabled control. Existing
YouTube v1 installs use a reviewed Update Included App action. No silent grants,
APK, real provider/playback/account/carrier acceptance. See RESUME-0.25.0.md and
source-verification.json; other Settings services and handset checks stay open.

## 0.26.0 — SMS/MMS history export

Settings's Messages and Media Backup now exports actual SMS/MMS provider records,
addresses, text/SMIL and downloaded binary parts through Kotlin IO and real Rust
streamed ZIP/checksum verification. Includes app metadata, SAF save, progress,
Stop, permission/change/error guards and private staging cleanup. No provider
writes or APK. History restore remains disabled. Best-effort provider observation
is not a cross-table transaction; OEM/handset acceptance is pending. Limits and
exact production/schema/verification boundaries are in HISTORY-BACKUP.md.

## 0.27.0 — checked history import review

Choose backup to restore now opens an OnKlav history ZIP for read-only checking.
Kotlin owns SAF copying/progress/Stop; Rust strictly validates generated paths,
ZIP attributes/method/extents/limits, hashes/CRC, record/metadata shapes and counts.
Private copies are removed before a compact summary is shown. No SMS permission
or default role is needed for inspection. Restore messages remains disabled;
provider merge/ID mapping/recovery and handset/MMS acceptance remain unfinished.
MMS_READY/default role are unchanged. See HISTORY-BACKUP.md and RESUME-0.27.0.md.

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

0.29.0 adds a gated Kotlin restore executor/Android adapter and durable Rust
per-message journal; Settings reads/recover-classifies pending outcomes. This is
backend progress, not enabled restore. Known incomplete writes can be cleaned;
unknown insert results block retries, committed IDs remain recorded. Archive
apply orchestration, final metadata merge, journal compaction and real phone/MMS
acceptance remain open. See RESUME-0.29.0.md and HISTORY-BACKUP.md. No APK.

0.30.0 adds the gate-closed archive-to-executor coordinator and durable partial/
complete message outcome maps derived from committed journal IDs. Fresh archive/
plan binding, all-candidate preflight and host local MMS identities are required.
The Restore UI and phone writes remain disabled. Post-write provider revalidation,
folder/flag merge, recovery/configuration UI, compaction and phone/MMS acceptance
remain open. See RESUME-0.30.0.md and HISTORY-BACKUP.md. No APK.

0.31.0 adds post-insert provider capture/content matching and conservative folder/
flag merge with current-entry precedence and atomic state/completion receipts.
Imported reminders and stale thread unread overrides are disabled. Fixture tests
are not actual provider/phone acceptance; Restore UI and MMS gate remain disabled.
Recovery/configuration UI, durable handoff/compaction and phone acceptance remain.
See RESUME-0.31.0.md and HISTORY-BACKUP.md. No APK.

0.32.0 adds ReferenceDialog restore settings/recovery review, staged private local
MMS identities, interrupted-insert review and saved outcome count/detail pages.
Receipt-backed Finish saved record only repairs acknowledgement; unknown inserts
stay paused. Cleanup and Restore messages remain gate-closed. Compaction, actual
resume/apply review and real phone acceptance remain unfinished. No APK.
See RESUME-0.32.0.md and HISTORY-BACKUP.md.

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

## 0.38.0 — partial-message continuation under the original restore record

The saved-record flow now reviews and inserts only its still-missing pending
sources. Completed targets are checked by exact ID/content and never reinserted;
original review/skip rows stay deferred. Durable journal receipts recover commits
lost before map acknowledgement. Stop/error checkpoints update the original map
without clearing unknown inserts; unknown outcomes stay blocked. A second
observed snapshot precedes the original-job metadata merge. The real Restore
control remains disabled pending phone/MMS acceptance. No APK was built.
See RESUME-0.38.0.md for the production path, tests and remaining blockers.

## Requested 0.38.0 Android APK build

The current code is now in a verified debug APK with both Android Rust ABIs and
rebuilt signed internal apps. 266 host/native tests and signature/content/16 KB
alignment checks passed. This supersedes the historical 0.14.0 latest-APK note;
it does not close phone/carrier or full-migration acceptance. MMS/default role
remain gated. See RESUME-0.38.0-APK.md and current verification.json.

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

## 0.41.0 — four-area continuation

Implemented durable Messenger callback/provider recovery, older group reaction
parent context and bounded history search, durable Walkie result/background
recovery and late-clip fallback, internal Browser handoff, paged Notes search,
shared speech settings/ownership, own Reader draft adoption, Annotation viewport
restoration and per-app website-profile isolation/cleanup.

Verified 145 Kotlin/framework-host and 144 freshly rebuilt Rust tests
(289 total), with one host APK (code48). This does not complete carrier,
two-phone audio or exact reference acceptance. MMS/default SMS role remain
disabled. See RESUME-0.41.0.md for production paths and explicit remaining work.

## 0.42.0 — design, activity selection and cleanup

- [x] MMS validation precedes decoded-attachment publication; partial writes roll
      back only owned files, and cleanup failures are surfaced.
- [x] Durable app-removal intent, idempotent core cleanup retry, one-time native
      profile rotation, retryable thumbnail cleanup and guarded startup.
- [x] Individual group member selection, original activity tiles, internal Walkie
      handoff, modal game catalog and return to the same conversation.
- [x] Shared original SDK toolbar/navigation glyphs and rounded fields, selected
      conversation tabs, RSS v2 source-first cards and compact search/refresh row.
- [ ] Friend-game invitation/verification/status and play await original game
      ports/protocol; the picker alone does not close that work.
- [ ] Handset pixel/frame/large-font/accessibility, live carrier/dual-SIM/provider
      and two-phone voice acceptance; original bundled speech-engine/voice parity.
- [ ] Independent security/lifecycle review and the remaining wider migration.

148 Kotlin/framework-host and 145 Rust checks passed (293 total). One verified
host APK, code49. MMS/default-role remain disabled. See RESUME-0.42.0.md.

## 0.43.0 — original Air Hockey solo port

Original `ah2` ten-total-goal solo rules, reference rink/scorebar/toolbar,
horizontal controls, delayed AI, real Godot collisions, manual Serve,
pause/help/quit/restart/results, audio/mute and private one-current checkpoint
replace the generic first-to-seven exercise. 72 new engine checks and 12 prior
rule checks pass; exported PCK production boot passes. No new Android APK or
Rust/SDK rebuild. Prior core and SDK proofs still match. Friend transport and
handset/pixel/audio/fairness acceptance remain open; task 15 remains partial.
See AIR-HOCKEY-PORT.md and RESUME-0.43.0.md.
