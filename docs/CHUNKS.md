# Historical migration chunk ledger

Current instructions: ../AGENTS.md, RESUME-0.45.1.md and ../TODO.md.
The numbered/versioned entries below describe past work, not the current task.
Current side pages/drawers move from and back to the RIGHT.

## Original chunk plan (historical)

Charles asked to stop the long, stuck attempts and break the rewrite down.
Keep the existing architecture and Inner Circle 2.2.85 design. Do not start a
different design or add features. Preserve this project's identity and signing.

1. **Home, Apps grid and Settings UI restoration (original chunk).** Preserve the original
   staged editor, typography, palettes, artwork, dialogs and theme management.
   Restore sparse paged app positions, original navigation/action/settings glyphs,
   original game thumbnails/cards and separate Settings package managers. See
   HOME-REFERENCE.md; keep unfinished service controls explicitly unavailable.
   Build and verify the single host APK; do not claim all screens have parity.

   **Completed architecture correction:** Only OnKlav produces an
   installable APK. Tools and Godot are internal libraries; retain the real Rust
   storage path. Preserve the requested left drawer and original popup style.
   Verify the consolidated host and record exact test limits. The retired
   standalone Notes APK is not the product architecture.
2. **SDK foundation.** Follow PLATFORM-CONTRACT.md: internal app/game/theme
   packages, original Settings management routes, host-mediated capabilities,
   identity/signing and update rules, and restricted runtimes. Do not execute
   untrusted Godot packs under the host UID before the boundary is implemented.
3. **Messaging foundation.** Port and verify one transport operation per chunk:
   SMS receiving/sending and callbacks, then MMS parsing/receiving/sending and
   attachments. Enable the default SMS role only after MMS works. Then port
   Groups, Social and Walkie-Talkie in separate bounded chunks using the same
   communication boundary. Preserve the original screens and behavior.
4. **Other native apps and shared settings.** Finish Browser, YouTube, Read Aloud,
   Annotation and RSS one app per chunk against the reference. Complete common
   preferences/integration and remaining original settings without extra UI.
5. **Original Godot games.** Port one original game at a time, preserving its
   artwork, controls, rules, one-current-match behavior and friend transport.
   Include Panda's original behavior. The current engine checks are tests and
   must not be substituted for those ports.
6. **Integration and acceptance.** Compare every status-ledger row to the
   reference, verify on the actual handset/carrier/accounts, and measure startup,
   memory and frame behavior before claiming improved speed or stability.
   User-facing creator tools follow the working runtime and platform.

Build first, then emulate. Do not run heavy Gradle/R8 jobs alongside software
emulation in this 8 GiB environment. Optional emulator UI commands must not abort
functional checks. Bound every device operation and the entire check. One failed
bounded attempt needs a recorded result, not an endless automatic retry loop.
Keep completed code and an installable APK available at each meaningful chunk;
do not deliver another source-only milestone or pretend unimplemented rows work.

## 0.9.0 desktop and messenger continuation

Desktop press indication removed; finger anchor retained; item placement springs
and edge/settle pager tweens added. Messenger UI and real Rust private state now
connect to provider history, SMS send callbacks, drafts, folders, flags, media,
message actions, reminders and Setup. Rust MMS codec and Android carrier paths
are implemented but MMS/default-role stay gated pending validation. See
MESSAGING-PORT.md; do not describe this as complete feature parity. Prioritize
independent carrier fixtures, raw recovery/provider tests and real phone/SIM
acceptance, then remaining messenger UI operations and reference details.

## 0.9.1 bounded continuation

Recovered the saved source/signing identity and restored build tools. Ported the
reference custom reply reminder chooser and manager actions, preserving Rust
state and the MMS/default-role gate. See RESUME-0.9.1.md and current verification
for exact build and device results. Continue raw transport/provider validation
and the remaining reference operations one bounded chunk at a time.

## 0.9.2 verification completed

Use scripts/check_android.py and restore the checksummed cache after a workspace
reset. The framework-host reminder test passes actual date/time widgets, Rust
storage, queued-alarm guard, manager rescheduling/removal and message routing.
No emulator boot is part of this default check. Preserve installed-APK/carrier
acceptance as a separate pending boundary. See RESUME-0.9.2.md.

## 0.9.3 Groups continuation

Groups management now uses Kotlin/Compose and real Rust durable state. Original
carrier post/reaction parsing is implemented in Rust and consumed by group
conversations. See GROUPS-PORT.md for the feature-by-feature implementation and
remaining gaps. Run scripts/build_native.py after Rust changes, then
scripts/check_android.py. Native provenance includes every Rust source and all
three JNI libraries; a cached older host JNI must not be used with this source.
MMS/default-role and friend-activity transports remain gated/unfinished.

## 0.10.0 SDK app foundation and Notes

Notes is now a separately compiled Rust/Wasm .onklav-a app, installed and removed
inside OnKlav. Counter proves the same external developer path without a host
rebuild. Read APPS-PORT.md and sdk/README.md before continuing the remaining
seven app ports. Keep the separate private app publisher key as well as the
existing Android signing key. External Godot execution remains disabled.

## 0.11.0 RSS and build/test repair

RSS is now a real signed Rust/Wasm internal package. See RSS-PORT.md for services,
verified paths and precise gaps. Six app ports remain. Debug R8 is disabled and
tests/build/verification run in separate bounded phases. SDK ViewModels bind to
the current Application; host UI tests wait for ready controls and use semantic
click callbacks. Do not repeat full-APK assembly after a failed test phase.

## 0.12.0 Read Aloud SDK continuation

Read Aloud controller is independently compiled into ReadAloud.onklav-a with generic host speech components and private Rust records. See READER-PORT.md. 67 tests and APK checks pass. Five SDK app conversions remain; bundled original voices, own old draft adoption, device/carrier and full parity stay explicitly open.

## 0.13.0 Browser SDK continuation

Browser is now a signed Rust/Wasm SDK app over scoped Rust metadata and a generic host web component. See BROWSER-PORT.md. 74 tests and single-APK checks pass. Compiler and test phases have separate 180s limits. Four SDK app conversions remain, alongside website-data cleanup, full reference/device acceptance and earlier voice/carrier/game gaps.

## 0.14.0 YouTube SDK continuation

One-app chunk: dedicated signed-out YouTube Rust/Wasm controller, scoped Rust video library and shared host media/provider widgets. See YOUTUBE-PORT.md for exact production path and gaps. 81 tests and single-APK checks pass. Annotation, Social and Walkie-Talkie remain. Do not substitute a Browser label or count live playback as tested by framework-host UI tests.

## 0.15.0 Annotation source-only continuation

The user now requests tests/source saving per app and approval before the next;
APKs are batched later. Annotation is a signed Rust/Wasm package with scoped
Rust image library/mark history and shared native canvas/image services.
50 Rust +40 Kotlin tests pass. See ANNOTATION-PORT.md, annotation-verification.json
and RESUME-0.15.0.md. Only Linux JNI is rebuilt; the old 0.14.0 APK/mobile report
is historical. Social and Walkie-Talkie remain. Do not start another app without
the user's requested approval.

## 0.16.0 Social placeholder and Walkie start

Charles clarified Social is a placeholder: only Rust Hello World, zero grants.
Its actual signed package and desktop route are verified. Walkie conversion is
authorized and started with Rust control/floor/capture/AMR rules plus a pure
host JNI API; no voice UI, recording or transport is claimed. 55 Rust +42 Kotlin
tests pass. See SOCIAL-PLACEHOLDER.md, WALKIE-PORT.md and RESUME-0.16.0.md.
APK/mobile builds remain deferred. Continue the next Walkie chunk without
asking to start it again.

## 0.17.0 Walkie SDK shell, source only

Actual signed Rust/Wasm Walkie controller with atomic scoped preferences and
80 prepared recipients; shared 190dp offline dial, staged settings, immediate
quick switches, Quit and left recent-recipient drawer with confirmed clear.
Connect sends nothing and reports unavailable. No microphone/carrier grants.
58 Rust +43 unique Kotlin checks pass across broad and isolated runs. The broad
run has an intermittent Groups contact-picker failure; Groups/Walkie/shared pass
in the isolated final run. This is not a single green full-suite claim. Test
startup now awaits SDK initialization; earlier incorrect Clear test target was
fixed, and quick preference changes preserve an unsaved recipient field.
See WALKIE-PORT.md, RESUME-0.17.0.md and source-verification.json. No APK/mobile
build. Voice recording/playback, authenticated sessions, clip store/ledger and
SMS/MMS remain unfinished. Continue within authorized Walkie scope.

## 0.18.0 — requested to-do list and first task

TODO.md orders remaining work. Only item 1 is implemented in this chunk: Rust
atomic scoped Walkie clip archive with bounded structural AMR, per-clip identity,
expiry/dedup, attempt-bound outcomes and receiver-proof precedence. Signed SDK
Wasm consumes list/delete/clear in a paged left Voice Clips drawer. Trusted host
archive actions are fixed to installed Walkie/storage grant and serialized with
SDK operations; guest effects cannot inject audio, read bytes or forge outcomes.
The UI closes a failed archive read and exposes its error rather than claiming
an empty library. Tests cover core archive, actual signed controller and Compose
history/delete/replay flow. See source-verification.json for final exact checks.
No live voice or carrier path is enabled, no APK/mobile rebuild occurs. Next item
is recording/playback broker. Keep later to-do items unchecked.

## 0.19.0 — foreground voice broker (TODO item 2)

Shared generic Kotlin capture/player components now broker private audio for the
signed Rust/Wasm Walkie app. Explicit audio.record/audio.playback grants default
off during curated bootstrap; Android microphone permission is separate and no
permission-result callback starts recording. Current widget/session/recipient
checks, single-owner focus, foreground cleanup, generation tokens, 12s/64KiB
limits, structural trim, preview and local queue/discard are implemented. Incoming
successful authorized playback marks the private clip heard and refreshes history;
own outgoing playback does not prove remote heard. Completion response was aligned
with the model's object/frame contract and is asserted in the real JNI test.
65 Rust +48 Kotlin tests pass (113 total) in the final full host run. Device audio
is fixture-backed in tests; no real microphone/speaker/codec/focus/carrier or APK
acceptance is claimed. Warm-up/beeps/live dial/session/transport remain later tasks.
The expanded full host suite is capped at 240s with progress; compile phases stay
180s. See RESUME-0.19.0.md, WALKIE-PORT.md and source-verification.json.

## 0.20.0 — selected contact broker (TODO 3a)

Walkie v4 adds an explicit default-off contacts.pick capability and Android phone
row chooser. Only the selected bounded name/normalized number reaches Rust/Wasm;
current tokens and grants guard both provider reads and completion. Optional names
persist with old-record compatibility. Typed recipients remain supported. Selection
does not verify or connect the peer and sends no messages. Full TODO 3 remains open;
live session controls/timeouts and original dial/warm-up/beeps are next.
See RESUME-0.20.0.md and source-verification.json. No APK/mobile rebuild.

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
