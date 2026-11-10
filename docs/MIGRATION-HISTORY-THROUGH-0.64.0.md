# Historical migration checklist — superseded by TODO.md

Unchecked historical entries are not evidence of missing current implementation.

# OnKlav migration to-do

Current developer-only batches: docs/FINISH-NON-GAME.md. Historical unchecked
items below can include completed implementation awaiting external acceptance.
Do not use them as a fresh implementation backlog without checking current code.


Current source checkpoint: 0.64.0-dev/code72; no APKs unless requested. See docs/RESUME-0.63.0.md. Last delivered APK: 0.61.0/code69. See docs/RESUME-0.62.0.md. See docs/RESUME-0.61.0.md. See docs/RESUME-0.60.0.md. See docs/RESUME-0.59.0.md. See docs/RESUME-0.58.0.md. See docs/RESUME-0.57.0.md. See docs/RESUME-0.56.0.md. See docs/RESUME-0.55.0.md. See docs/RESUME-0.54.0.md. See docs/RESUME-0.53.1.md. See docs/RESUME-0.53.0.md.
Games remain deferred until non-game reliability and remaining functions are addressed.
Historical sections below describe their numbered builds; yellow stars were superseded
by theme-colored filled stars in 0.49. Current host/phone evidence is recorded in
source-verification.json; hardware codecs and carrier delivery require handset checks.
See docs/NON-GAME-REVIEW.md for priorities and current implementation boundaries.

## 0.64.0 — concrete notification / permission / removal recovery fixes

- [x] Separate thread/reminder/attention notification identities; stable per-thread action intents.
- [x] Mark Read rechecks SMS role/access between provider writes and before local flags.
- [x] Queued reply remains queued when read access changes; blank reply does not mark read.
- [x] Removal cleanup preserves newly enqueued profile work and retains failed deletions.
- [x] Walkie cleanup generations avoid clearing a newer removal request; no new lock inversion.
- [x] Retry already-missing profiles safely; existing durable transport/removal checks retained.

Source only; no APKs. Built-in app-document backup remains complete.

## 0.63.0 — Built-in app-document backup complete

- [x] One App Documents Backup/restore covers Notes, Files, Annotation, RSS, Reader, Browser, YouTube and Walkie data.
- [x] Walkie audio restores as playback-only: no queued sends, old SIM/session bindings, reconnects, autoplay or receipts.
- [x] Merge existing data safely; validate the complete archive before store commits; retries keep completed data.
- [x] Existing metadata/message-history and Appearance theme backups remain separate available paths.
- [x] Close this backup implementation batch. No APK requested or produced.

Continue the non-backup port checklist; do not invent another app-backup batch.

## 0.62.0 — Browser and YouTube backups, two source steps

- [x] Browser tabs, selected tab, bookmarks and settings; current entries/settings win.
- [x] YouTube bookmarks/history; preserve local viewer identity and existing entries.
- [x] Typed schema, URL/duplicate/quota checks; atomic merges, no silent trimming.
- [x] Exclude website cookies, sign-in/profile data and playback state.
- [ ] Next: Walkie durable archive portability, then SDK backup integration review.

APK intentionally skipped for this source checkpoint. Four broader work groups remain.

## 0.61.0 — RSS and Read Aloud backups, Group 2 progress

- [x] RSS sources/cache/bookmarks/read marks/settings in checked portable ZIPs.
- [x] Merge missing RSS data; current settings/data win; all limits validate before one atomic write.
- [x] Read Aloud single accessible saved draft; existing draft (including cleared text) wins.
- [x] Reader interrupted-pointer retry and Unicode/document validation; no playback/voice state restored.
- [ ] Next: Browser and YouTube backup coverage, then Walkie durable archive portability.

Full SDK-data integration and four broader developer work groups remain open.

## 0.60.0 — Files and Annotation backups, Group 2 progress

- [x] Settings Files ZIP backup: active ordinary files, folders and original IDs/bytes.
- [x] Editable Annotation backup: image, marks, revision and undo/redo history.
- [x] Review counts/digest, validate every payload/schema/hash before restoration.
- [x] Keep existing IDs and Trash; reject conflicting new children and orphan payloads.
- [x] Bounded streaming, Stop, failed-provider cleanup and retry of durable partial writes.
- [ ] Next: RSS and Read Aloud backup coverage; Browser/YouTube/Walkie stores remain.

Full SDK-data backup and four broader developer work groups remain open.

## 0.59.0 — Notes backup and restore, Group 2 progress

- [x] Settings > Backups > Notes Backup: save, check counts and confirm merge.
- [x] Strict whole-backup validation, review digest, bounds and live-app restore guard.
- [x] Existing notes (including Trash) win; add missing active notes into Notes.
- [x] Durable per-note merge and retry; provider failure cleanup; no grants/packages restored.
- [ ] Next: shared Files and annotated-image backup/restore coverage.
- [ ] Full SDK-document backup remains incomplete; RSS/Browser/Reader/Walkie data also remain.

Four broader developer work groups remain in FINISH-NON-GAME.md.

## 0.58.0 — Annotation gesture recovery, Group 1 progress

- [x] Require a fresh gesture after cancellation, disable, tool or document updates.
- [x] Ignore stale edit completion callbacks during a newer stroke.
- [x] Reset viewport restoration when changing pictures.
- [x] Review existing authorized picker/export paths; retain provider acceptance gap.
- [ ] Next: Settings SDK-document backup and restore coverage review (Group 2).

Four broader developer work groups remain open; no phone/carrier acceptance claimed.

## 0.57.0 — RSS, Group 1 progress

- [x] One-time offline adoption of own previous RSS source list, with old data retained.
- [x] Existing SDK data wins; publisher/storage grant and all-or-nothing bounds.
- [x] Separate article/bookmark pages; closing drawers keeps the article page.
- [x] Article/list/drawer scroll positions and Android Back to articles.
- [ ] Next: Annotation gesture/provider/pending-action recovery review.

Four broader developer work groups remain in FINISH-NON-GAME.md.

## 0.56.0 — Browser/web lifecycle, Group 1 progress

- [x] Browser Android Back follows page history; drawers/dialogs keep priority.
- [x] Renderer loss closes fullscreen and Reload recreates the isolated surface.
- [x] Ignore stale web callbacks; clear old errors when new navigation starts.
- [x] Bind asynchronous clear/reset reload to current command/view/navigation/grant.
- [ ] Next: inspect RSS source-list adoption and scroll retention; then Annotation.

Four broader non-game developer work groups remain open in FINISH-NON-GAME.md.

## 0.55.0 — Read Aloud, Group 1 progress

- [x] Visible over-limit notice, retaining existing text.
- [x] Previous/Next/seek editor cursor positioning.
- [x] Cancel queued speech on newer playback, stop, close or revoked access.
- [x] Reject stale initialization after voice setup Retry.
- [ ] Complete remaining Group 1 built-in app gaps after checking current code.
- [ ] Group 2: remaining Settings service implementations.
- [ ] Group 3: concrete messaging/Walkie/SDK lifecycle defects.
- [ ] Group 4: final cross-area reconciliation, checks and APK.

Each production chunk retains its own verified APK. This batch does not close
full application parity; phone/carrier/account acceptance is tracked separately.

## 0.48.0 — media and phone-test enablement

- [x] Separate full-conversation media query and three-column square Gallery.
- [x] Internal swipe/zoom image viewer, GIF playback, audio/video controls and save/share.
- [x] Yellow starred icons in feed, gallery and viewer.
- [x] Explicit handset carrier trial: manual default-role/SIM/permissions, real SMS/MMS/Walkie paths.
- [x] Drive/provider file upload and metadata/history backup/restore entry points.
- [ ] Physical-phone gallery/GIF/player/gesture and saved-file acceptance.
- [ ] Real carrier SMS/MMS and two-phone Walkie acceptance; release gate stays unverified.
- [ ] Direct Google OAuth and automatic public sharing links; full SDK-document backup.
- [ ] Existing Files/Notes return-navigation host-test issue remains unresolved.

## 0.47.0 — Files

- [x] SDK Files app and Apps icon using shared theme and renderer.
- [x] Shared content folders, canonical Notes migration, Trash and file management.
- [x] Scoped Android folders, import/export, read-only open and permission checks.
- [x] App text/image exports and public Browser downloads use OnKlav folders.
- [ ] Physical-phone picker/provider/media/accessibility and performance acceptance.
- [ ] Migrate further app-specific public content where appropriate.

## 0.46.0 — non-game application review and reliability

- [x] Review non-game production paths and reconcile the current gap list.
- [x] Contacts: resume/permission refresh, truthful loading/unavailable/search
      states, retry and cancellation-aware atomic query snapshots.
- [x] Apps manager: separate bootstrap from catalogue updates, loading/retry,
      gated package actions and correct cancellation behavior.
- [x] Storage: catch complete load failures, retry and lifecycle refresh.
- [x] Walkie settings: visible preference-load failure/retry and cancellation.
- [ ] Finish phone/carrier SMS/MMS/default-role and provider acceptance.
- [ ] Verify Notes, Browser, YouTube, RSS, Reader and Annotation on phone;
      finish remaining reference behavior, media/audio/provider recovery gaps.
- [ ] Finish live Walkie and two-phone acceptance, plus remaining Settings
      account/Drive/backup and SDK release-readiness work.
- [ ] Measure startup, app opening, frames and memory on the same phone.
- [ ] Games: deferred; resume only after the non-game application is working well.

## 0.45.1 — cleanup and build-proof corrections

- [x] Consolidate current README/agent guidance; label historical chunk/status
      notes and retain superseded root notes outside active instructions.
- [x] Complete SDK proof requires all nine packages, exact inputs and per-app
      freshness; host checks and APK planning now use the same validation.
- [x] Reject duplicate package entries and malformed proof/record shapes.
- [x] Document external publisher-key storage and key continuity for authors.
- [x] 20 Python checks pass; real host/starter no-op plans and reuse checked.
- [x] Confirm production Android input snapshot unchanged; keep verified 0.45
      APK without rebuilding or relabeling it. No legacy Java app source found.

## 0.45.0 — reliability and selective development builds

- [x] Resolve Groups cancellation popup/contact workflow failure; loading,
      provider failure, retry and permission/resume refresh are explicit.
- [x] Separate startup bootstrap from its own catalogue epoch updates.
- [x] Stop boundary auto-scroll work and add privacy-safe SDK trace sections.
- [x] Build/sign only stale SDK apps; restore bundled copies without Cargo/key;
      preserve per-package proofs and enforce update identity/version rules.
- [x] Freeze production assembly inputs and reject mixed source checkpoints.
- [x] Full app host suite: 139 passed, zero failures/skips; 12 shared checks
      verified; 16 Python checks pass. Standalone SDK reuse smoke checked.
- [x] Verified 0.45 optimized ARM64 APK: 38,250,173 bytes; warm unchanged
      assembly plus verification 23.40 seconds.
- [ ] Measure same-handset startup, app opening, frames, scrolling and memory.
- [ ] Complete remaining migration, carrier, games and authoring acceptance.

See docs/RESUME-0.45.0.md and docs/VERIFICATION-0.45.0.md. The 0.44 failure
below is historical and resolved in the current host checks.

## 0.44.0 — build, APK size and startup correction

- [x] Reuse source-matched native/SDK/game artifacts; make broad host tests an
      explicit phase, with bounded commands, logs and no automatic retry loops.
- [x] ARM64 phone packaging and a non-debuggable optimized/profileable host
      variant, preserving the .rewrite identity and existing Android signer.
- [x] Remove the complete extended icon dependency; retain only six exact used
      upstream vectors plus icons-core. Remove the tools-wide keep-all rule.
- [x] Remove forced first-draw delay and cache successful bundled-app setup
      without caching or bypassing live permissions. Missing registry and
      package/permission/removal changes invalidate setup reuse.
- [x] Assemble/verify the optimized host: 38,246,689 bytes, 70.2% smaller than
      the last 128,249,221-byte dual-architecture debug APK.
- [x] Retain verified mobile JNI in source checkpoints for faster resumption;
      supply an explicit-device bounded startup/frame/memory evidence collector.
- [ ] Measure old/new startup, navigation, app opening, scrolling and memory on
      the same handset. Host tests and package size are not phone speed proof.
- [ ] Resolve the previously documented intermittent Groups contact-sheet host
      test and complete the wider handset/transport/SDK authoring acceptance.

See docs/RESUME-0.44.0.md, docs/BUILD-AND-PERFORMANCE.md and the current
verification reports for exact fresh checks and build timing. Existing migration
tasks below remain open. No transport gate, app publisher or signing key changed.

## 0.43.0 — original Air Hockey solo port (partial task 15)

- [x] Replace first-to-seven exercise with `ah2` ten-total-goal rules, manual
      Serve, draw/outcomes, horizontal control and bounded imperfect AI.
- [x] Godot collisions, reference rink/scorebar/toolbar, pause/help/quit/restart,
      generated tones/mute, background pause and one current saved match.
- [x] 72 new Air Hockey checks +12 existing game-rule checks; export and boot
      the production PCK. Source/SDK checkpoint only; no new APK.
- [ ] Air Hockey handset/pixel/gesture/audio/Back/process-kill and fairness
      acceptance. Solo implementation does not complete all of task 15.
- [ ] Air Hockey friend invitation/verification/status, protocol/host bridge,
      recovery and two-phone carrier play. Next bounded implementation task.

See docs/AIR-HOCKEY-PORT.md and docs/RESUME-0.43.0.md. Core/JNI and SDK inputs
are unchanged, hash-verified against prior proofs; prior tests were not rerun.
The last verified APK remains 0.42/code49. MMS/default role stay disabled.

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

## Historical migration ledger


Work one bounded item at a time. Save tested source and SDK checkpoints. Do not
build an APK for each item. A later requested phone/batch build produces only
the OnKlav host APK; apps and games remain internal packages. Preserve Inner
Circle 2.2.85 design and existing signing keys. Social stays Hello World.

- [x] 1. Walkie private clip archive: bounded validated audio, metadata, expiry,
  duplicate protection, truthful outcome ledger, SDK history/delete/clear, tests.
  This is storage work; it does not enable recording or carrier delivery.
- [x] 2. Walkie shared microphone/playback broker: explicit revocable permissions,
  focus/lifecycle cancellation, 12-second capture and local Queue/Discard review.
  Implemented in 0.19.0; actual carrier Send is task 5, phone audio acceptance task 21.
- [ ] 3. Walkie live session/contact verification, original dial/status flow and warm-up/beeps;
  peer/subscription-bound control handling, timeout/recovery and receipts.
  - [x] 3a. Host-owned phone contact selection with explicit revocable SDK grant,
    selected-name/number-only result, durable recipient names and old-state migration.
  - [ ] 3b. Live verification/session control broker and timeout/recovery.
    Durable outgoing state/rules and closed-gate UI are implemented in 0.21.0;
    Incoming verification/consent rules, bounded replies and revocable permission
    are added in 0.22.0. 0.23.0 adds the Android receiver/send/result broker, policy routing and
    simultaneous invitation rules behind MMS_READY=false. Lifecycle/background
    recovery, late arrival/cleanup review and real phone acceptance remain open.
  - [ ] 3c. Connected dial, warm-up/beeps and receipts are integrated in 0.23.0;
    original visual parity and actual microphone/playback/floor behavior need phones.
- [ ] 4. Complete Messages SMS/MMS transport and carrier verification; keep MMS
  and default-role gate closed until receive/send are verified.
- [ ] 5. Finish Walkie transport integration, clip ordering/retry/notification
  behavior and original UI parity; verify real voice between two phones.
  0.23.0 implements gated carrier handoff, offline Send, explicit Retry, incoming
  ordering and receipts. Pending: background/restart, late MMS and cleanup audit,
  real carrier/audio acceptance; see RESUME-0.23.0.md. Not 100% operational.
- [ ] 6. Stabilize intermittent Groups/contact-picker/startup and Reminder UI host waits.
- [ ] 7. Audit/fix Notes: original UI, export/share and lifecycle/phone checks.
- [ ] 8. Audit/fix Browser: original behavior, website profiles, media and cleanup.
- [ ] 9. Audit/fix YouTube: playback, private mode/rotation, handoff and cleanup.
- [ ] 10. Audit/fix Read Aloud: voice choices/shared engine and audio/lifecycle.
- [ ] 11. Audit/fix Annotation: gestures, rotation/process recovery and providers.
- [ ] 12. Audit/fix RSS: original cards/search, feed adoption and Browser handoff.
- [ ] 13. Finish Messages and Groups original feature/layout gaps, blocked/spam/
  reactions, historical group context and real carrier/dual-SIM acceptance.
- [ ] 14. Audit all Settings/Appearance/package managers; implement remaining
  services and verify original dialogs, controls and theme installation.
  - [x] 14a. Settings/Appearance UI implementation pass: navigation, staged values,
    compact Quiet Hours controls, local storage/metadata backup, Walkie settings,
    help routes, offline voice controls and adaptive dialogs (0.24.0).
  - [ ] 14b. Complete remaining services: Drive account/uploads, SMS/MMS
    history restore/provider acceptance, original speech voices, profile sharing,
    external game installation. Verify full visual/OEM/handset behavior.
    See SETTINGS-PORT.md; this UI pass does not close all of item 14.
- [ ] 15. Complete the original Air Hockey Godot port.
  - [x] 15a. Original `ah2` solo rules, Godot rink, controls/UI/lifecycle and
    private current checkpoint implemented and engine-checked in 0.43.0.
  - [ ] 15b. Friend protocol/host invitation/status/recovery and live play.
  - [ ] 15c. Handset/reference/fairness/audio/accessibility acceptance.
- [ ] 16. Complete the original Naval Battle Godot port.
- [ ] 17. Complete the original Four in a Row Godot port.
- [ ] 18. Complete the original Micro Blackjack Godot port.
- [ ] 19. Complete the original Panda Godot port.
- [ ] 20. Finish app/game SDK authoring, package lifecycle, permission boundaries,
  examples and security review; keep untrusted Godot execution disabled meanwhile.
- [ ] 21. After requested batch build: regenerate mobile Rust libraries, build
  and verify one signed OnKlav APK; test on phone and measure performance,
  accessibility, recovery and reference parity. Host tests are not handset proof.

Item 1 completed in source 0.18.0: 63 Rust +43 Kotlin host tests pass. No APK.
Item 2 software implementation completed in 0.19.0: 65 Rust +48 Kotlin host tests.
Real phone audio remains unverified. Item 3 is in progress; 3a contact selection is implemented in 0.20.0. Next is 3b live verification/session control. Remaining tasks are not counted as done
when a shell, pure rule or engine exercise exists. See docs/REWRITE-STATUS.md.

0.20.0 verifies 3a with 67 Rust +50 unique Kotlin checks across broad/focused runs.
The broad run had a Groups failure, passing in isolation; task 6 remains open.

0.21.0 adds the outgoing session foundation and connection dialog: 73 Rust +50
Kotlin host tests pass in the current full run. 3b remains partial; no APK.

0.22.0 adds incoming consent/permission rules and UI; 78 Rust +50 Kotlin checks
pass in the current full run. 3b stays partial pending actual carrier integration.

0.23.0 integrates the gated Walkie carrier/audio paths and repairs stale runtime
JAR verification. User has no available SIM-equipped test phones; real operation
cannot be accepted here. Items 3 and 5 remain open; see RESUME-0.23.0.md for
specific unfinished behavior and source-verification.json for final test counts.
No APK built. A signed .onklav-a is the internal SDK app package.

0.24.0 completes the Settings/Appearance UI implementation pass (14a), with
remaining backend and phone acceptance listed under 14b. No APK built.

Final current verification: 86 Rust +61 Kotlin tests pass (147), zero failures.
Full app/shared host invocation passed; runtime classes match current compiled Kotlin.
No APK/mobile ABI build, real carrier/voice/account or phone/pixel acceptance.
Settings UI implementation pass complete; remaining services are explicitly tracked.

0.25.0 implements Automatic YouTube Previews in shared Messages/Groups, with a
working default-off Privacy switch and explicit internal YouTube v2 handoff.
Remaining Settings services: Drive, full SMS/MMS backups, bundled voices, profile
sharing and external games. Provider/device visual/playback acceptance remains.
The next independent Settings item is full message-backup export; role/carrier
write acceptance stays gated. No per-item APK build.

Final current verification: 87 Rust +65 Kotlin host tests passed (152), zero failures.
Full app/shared host invocation green; current runtime-class freshness verified.
YouTube v2 retains publisher; all other SDK package hashes unchanged.
No APK/mobile ABI rebuild, real provider/page/playback, account or carrier acceptance.

0.26.0 implements the history export portion of 14b: SMS/MMS records, addresses,
downloaded media and app metadata -> Rust-verified ZIP -> SAF. Progress, Stop,
permission/provider-change guards and cleanup are included. History restore
remains unfinished; OEM/phone acceptance remains unverified. No per-item APK.
Next bounded Settings task: strict history import inspection/review and restore
mapping, with provider-write/default-role acceptance still gated. See
HISTORY-BACKUP.md and RESUME-0.26.0.md. Full 14b remains unchecked.

Final current verification: 91 Rust +69 Kotlin host tests passed (160), zero failures.
Full app/shared invocation and runtime-class freshness checks passed. No APK/mobile
ABI build, real history/provider/phone/carrier/account or rendered parity acceptance.

0.27.0 completes strict history ZIP inspection and read-only review: real Rust
archive/hash/schema validation through Kotlin SAF, progress/Stop, cleanup and
summary. Choose backup to restore now works for checking; Restore messages stays
disabled. No READ_SMS, provider changes, metadata merge, role change or APK.

History restore remaining under 14b:
- [x] Export provider history/media (.26).
- [x] Inspect an imported v1 ZIP and show a checked summary (.27).
- [x] Read-only content comparison and existing-message/eligible flag/thread mapping
  candidates (.28); repeated/ambiguous matches are held for review.
- [x] Per-message Rust journal and gated Kotlin executor/Android adapter: actual
  returned-ID receipt, compensating cleanup, cancellation and restart classification
  (.29), exercised with injected providers; real Android writes remain disabled.
- [x] Gate-closed archive coordinator: fresh checked archive/plan, all-payload
  preflight, host MMS identities, actual committed-ID and partial outcome maps (.30).
  Tested with injected writes, not actual Android provider acceptance.
- [x] Post-write snapshot/content verification and conservative folder/flag remap
  with current-entry precedence and atomic state/completion receipt (.31).
  Exercised with controlled providers; observation races/real acceptance remain.
- [x] Recovery review/configuration UI: staged host local identities, interrupted
  insert review, saved-record counts/pages and receipt-backed acknowledgement (.32).
  Unknown results remain paused; guarded phone cleanup stays disabled.
- [ ] Durable outcome/journal/metadata-receipt handoff and compaction with anti-replay
  evidence; final apply/resume confirmation, identity-review binding and manual
  resolution of unknown/changed outcomes.
- [ ] Actual MMS/default-role/provider/handset acceptance before enabling writes.

14b and the full rewrite remain incomplete. See HISTORY-BACKUP.md. Other remaining
Settings services are Drive, bundled voices, profile sharing and external games.

Final current verification: 97 Rust +72 Kotlin host tests passed (169), zero failures.
Final complete app/shared invocation was forced with current JNI; runtime-class
freshness verified. No APK/mobile ABI build, actual user-history/provider/account/
phone/carrier or rendered parity acceptance. History provider restore remains unavailable.

0.28.0 adds Compare with phone to checked backup review. Rust produces conservative
content-based duplicate/new/review/skip and ID-binding candidates from a real
Kotlin provider snapshot. No writes; restore still unavailable. The comparison
map is temporary, not a durable apply authorization. Next: provider-write broker,
per-message journal/rollback/recovery, actual inserted-ID recording and final
metadata remap. 14b/full migration remain open; phone/MMS acceptance still required.

Final current verification: 102 Rust +76 Kotlin host tests passed (178), zero failures.
Complete app/shared invocation passed with current JNI; runtime-class
freshness verified. No APK/mobile ABI build, actual user-history/provider/account/
phone/carrier or rendered parity acceptance. History provider restore remains unavailable.

0.29.0: restore transaction backend implemented; source remains a development
checkpoint. Unknown insertion outcomes block retries; committed records survive
recovery, known incomplete inserts permit guarded compensating cleanup. This is
not atomic cross-provider rollback or a complete archive restore. Next: archive
apply coordinator, durable final ID/metadata merge and journal compaction, then
real phone/MMS acceptance before enabling writes. See RESUME-0.29.0.md.

Final current verification (.29): 107 Rust +85 Kotlin host tests passed (192),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.30.0 connects checked archive preparation to the per-message executor and saves
durable outcome maps, including partial failures/Stop. Real writes remain blocked.
Next: post-write revalidation and folder/flag mapping with existing-entry precedence,
then recovery/configuration/compaction and phone acceptance. No APK.

Final current verification (.30): 111 Rust +91 Kotlin host tests passed (202),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.31.0 completes the gated post-write verification/metadata merge backend. A changed
row prevents metadata application; existing flags/folders win and imported reminders
are disabled. Next: explicit recovery/review/configuration and durable handoff/
compaction. Phone/provider acceptance remains required; no APK built.

Final current verification (.31): 114 Rust +95 Kotlin host tests passed (209),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.32.0 adds restore configuration and recovery review controls. Saving local phone
identities and finishing a receipt-backed saved record are operational private-data
actions; phone writes remain disabled. Next: durable handoff/compaction and final
apply/resume/review flow, then real phone acceptance. No APK.

Final current verification (.32): 118 Rust +99 Kotlin host tests passed (217),
zero failures. Complete app/shared invocation passed with current JNI; runtime
class freshness verified. No APK/mobile ABI build, actual Android provider writes,
phone/carrier or rendered parity acceptance. Restore messages remains disabled.

0.33.0 adds explicit completed-journal compaction with retained anti-replay receipts.
Unknown/incomplete inserts remain active. Raw journal slots can be reclaimed;
archive is bounded to 10000 terminal receipts/8 MiB and refuses overflow. Saved
outcome maps and metadata receipts are not compacted. Next: their durable handoff
and capacity management, then final apply/resume/identity confirmation and phones.
Item 14b and the full migration remain incomplete. No APK. See RESUME-0.33.0.md.

Final current verification (.33): 123 Rust +100 Kotlin host tests passed (223),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

0.34.0 completes bounded completed-outcome-map/metadata-receipt handoff and
compaction. Active slots are freed only after a durable archive retains completion
summaries and metadata replay protection. Interrupted records stay in full; the
existing journal retains actual inserted IDs/proofs. Next: final explicit apply/
resume and identity-review binding, manual unknown/changed outcome resolution,
then provider/phone acceptance. Item 14b and the full rewrite remain open. No APK.
See RESUME-0.34.0.md for limits and verification scope.

Final current verification (.34): 130 Rust +101 Kotlin host tests passed (231),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

0.35.0 adds the final restore-confirmation screen and typed, one-use archive/plan/
identity review binding to the existing coordinator. Restore remains disabled.
A changed configuration or phone plan needs review again. Cancel/reuse do not
start a provider snapshot; a mid-operation identity change retains the journal
and partial map for recovery. Next: record-specific reopen/resume, unknown/changed
outcome resolution and real phone/provider acceptance. 14b/full rewrite stay open.
No APK. See RESUME-0.35.0.md.

Final current verification (.35): 130 Rust +107 Kotlin host tests passed (237),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

0.36.0 adds record-specific original-backup reopening and gated metadata-only
continuation after a completed message batch. Saved archive/map identities and
actual message IDs/content are checked, with a new one-use review. It finishes
the original map/job without inserting/deleting messages or replaying metadata.
Pending/unknown insertion batches remain paused. Next: manual outcome resolution,
safe partial-message continuation and real provider/phone acceptance. 14b and the
full rewrite remain open. No APK. See RESUME-0.36.0.md.

Final current verification (.36): 134 Rust +113 Kotlin host tests passed (247),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

0.37.0 adds explicit review for a recorded needs_review insertion whose exact
parent and parts were already removed. Verified absence retains an auditable
cleanup receipt, performs no deletion/retry, and survives compaction. Unknown
insert results and present changed rows remain blocked. Partial-message
continuation and real provider/phone acceptance remain open; 14b/full rewrite
stay unchecked. No APK. See RESUME-0.37.0.md.

Final current verification (.37): 137 Rust +119 Kotlin host tests passed (256),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

0.38.0 connects explicit partial-message continuation to the saved-record flow.
Only reviewed missing pending sources enter the executor; completed receipts are
reconciled after process interruption and the original record is checkpointed on
Stop/failure before metadata completion. Unknown insert results still block all
continuation and present changed rows remain protected. Phone/provider/MMS
acceptance is required before enabling writes. Item 14b and full migration stay
open. No APK. See RESUME-0.38.0.md.

Final current verification (.38): 141 Rust +125 Kotlin host tests passed (266),
zero failures across the complete app/shared invocation. Current JNI and runtime
class freshness verified. No APK/mobile ABI build, real provider writes, phone/
carrier or rendered parity acceptance. Restore messages remains disabled.

Requested 0.38.0 APK batch: current Rust rebuilt for both Android ABIs and SDK
apps rebuilt with the retained publisher; 266 tests and APK signature/content/
alignment checks passed. OnKlav-0.38.0-debug.apk is the only Android installer.
Phone/carrier acceptance is pending; MMS/default-role gates remain closed.
Await requested approval to begin SMS/MMS, Groups and Walkie transport completion.
See RESUME-0.38.0-APK.md and verification.json. No migration checkbox was closed
merely by compiling an APK.

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

## Current acceptance list for the authorized four areas

Software changes for 0.41 are described in RESUME-0.41.0.md. These remain open:

- [ ] Real SMS/MMS send/receive, multipart failures, dual-SIM and reboot/recovery
      acceptance before enabling MMS/default messenger role.
- [ ] Device notification delivery and Android background/power restrictions.
- [ ] Carrier group MMS/reactions, original friend activities and exact reference
      behavior/layout acceptance.
- [ ] Two-phone Walkie consent, full-duplex/floor timing, late arrivals, actual
      microphone/speaker/audio focus, package removal and background acceptance.
- [ ] Existing SDK apps: handset website/media/account/audio/provider behavior,
      original voice assets and remaining reference visual/accessibility parity.
- [ ] Atomic cleanup on partially failed MMS attachment decoding; reconcile
      native cleanup failure after committed package removal.
- [ ] Independent lifecycle/cleanup/security review.

No test fixture is counted as live carrier or phone acceptance. Social remains
the specifically requested Hello World app. Original games and creator expansion
are separate unfinished migration work.

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

Historical 0.47 check stalled on returning from Notes to Files. 0.49 replaces the activity-intent handoff, clears focus/selection, returns direct-note Back to Files and releases SDK busy state. The signed guest/model handoff check passes; full transition acceptance remains a handset check.

## 0.49.0 — current phone feedback

Theme-filled stars/gallery controls, contact photos, loading caches and in-process Files handoff are implemented. Verify Files→Notes→Back, media playback and contact/theme rendering on a handset; earlier return-to-Files UI-idling failure is not phone acceptance. See RESUME-0.49.0.md. Games remain deferred.

## 0.50.0 — conversation controls

Inbox pin buttons, reference timestamp placement, direct reaction Emoji picker, stateful mute/pin controls, history search dialog/jump and individual-message shared Reader speech are implemented. See RESUME-0.50.0.md. Phone visual/audio/notification and existing carrier acceptance remain open. Games remain deferred.

## 0.51.0 — current phone feedback build

Retained tab data, 20-message/24-media pages, gallery snapshot/player safety, full-screen controls, reaction animation/corner badges, speech activity, contact photo fallback and group return behavior are implemented. Walkie package v8 has a simpler selected-recipient view. See RESUME-0.51.0.md for paths/checks. Handset performance/crash/visual/audio and carrier/two-phone acceptance remain open; games stay deferred.
