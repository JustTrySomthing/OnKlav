# Current source update

Music Maker and its authoring tools are retired. Existing game tracks are retained. See [bundling your own game music](developer/games/GAME-MUSIC.md).

# Latest APK: OnKlav 0.100.73-rc1 / code 182

Verified optimized ARM64 build with the current app sources, source-matched Rust core, and Shroom 0.2.3. Shroom includes the new wordmark, combat effects, extraction sequence, and lower-octave Crown of Decay soundtrack. See docs/SHROOM.md for the game and docs/BUILD-AND-PERFORMANCE.md for recovery after a chat switch.

# Latest source: Game SDK0.8 action/RPG foundations

Four single-player action starters; reusable health, inventory, cooldowns, flags, progression, checkpoints, enemy steering/pathfinding, twin-stick aim and touch preferences. See developer/games/DEVELOPER-START-HERE.md. Existing host/app/game work preserved. No new APK is required.

# Latest source: reusable action-game SDK 0.7

Landscape platformer and first-person starters, multitouch joystick/D-pad/look/actions, validated saves and shared pause controls. See developer/games/TOUCH-CONTROLS.md. Existing app and game fixes preserved; no host binary changed.

# Latest source: Cats tactical AI and touch-drop repair

Private bounded bitboard search defends early forks; finger release commits once and clears the preview, with proper synthetic mouse ownership and cancellation. See docs/CATS-TACTICS-AND-TOUCH-REPAIR.md. APK0.100.62 predates this source update. All prior Panda/game/Video Edit work is preserved.

# Latest APK: OnKlav 0.100.62-rc1 / code 171

Quick ARM64 update with Panda falling-block drainage, progressive varied courses, local structure activation and larger top labels. All previous game and Video Edit updates preserved. See docs/RELEASE-0.100.62.md and docs/apk62-verification.json. Original signer retained.

# Latest source: Panda falling-block drain and varied progressive courses

Scored debris fully leaves view before another shot; independent structures wake locally; courses grow from15 to40 pieces across varied heights/platforms; stronger later supports; larger You/Rival labels. See docs/PANDA-COURSES-AND-FALLING-BLOCKS.md. APK0.100.61 predates these changes.

# Latest APK: OnKlav 0.100.61-rc1 / code 170

Quick ARM64 update includes all game replay/tournament/control repairs, the latest Panda performance/scoreboard changes and preserved Video Edit. Original signer retained. See docs/RELEASE-0.100.61.md and docs/apk61-verification.json.

# Latest source: game controls, instant replay and tournaments

Air Hockey touch takeover/drag repaired; Cats tactical safety improved; all result replay buttons start directly. Blackjack ten hands plus tie-breaks; Cats and Dominos first to three round wins. Saves and prior Panda/Video work preserved. See docs/GAME-REPLAY-AND-TOURNAMENT-REPAIRS.md. APK 0.100.60 predates this update.

# Latest source: Panda performance and scoreboards

120 Hz impact-activated flight physics, one-tenth displayed scores, zero-start charge, simplified blue-rival score cards and clearer results. See docs/PANDA-PERFORMANCE-AND-SCOREBOARD.md. Latest APK 0.100.60 predates this source refinement.

# Latest APK: OnKlav 0.100.60-rc1 / code 169

ARM64 update includes Panda volleys, timing/audio repairs and helmet-matched cannon, together with preserved Video Edit footer/portrait fixes. Original signer retained. See docs/RELEASE-0.100.60.md and docs/apk60-verification.json.

# Latest source: Panda volleys, pacing and audio repairs

Player completes all three shots (and earned bonuses) before the rival. Cannon matches the active helmet. Shot timing, drawing allocations and collision audio repaired. See docs/PANDA-VOLLEY-PACING.md. Latest APK remains 0.100.59 and predates this source update.

# Latest APK: OnKlav 0.100.59-rc1 / code 168

Verified quick ARM64 APK fixes Video Edit: one four-button dock, compact popup menus and larger portrait preview. The merged Panda reboot/audit is preserved. Original signer retained. See docs/RELEASE-0.100.59.md and docs/apk59-verification.json.

# Latest APK: OnKlav 0.100.58-rc1 / code 167

Verified quick ARM64 APK includes the merged Panda solo reboot/audit and audited Video Edit improvements. Original signer retained. See docs/RELEASE-0.100.58.md and docs/apk58-verification.json.

# Latest source: Panda reboot and audited Video Edit merged

Both branches are combined. Panda solo reboot and its audit fixes are preserved, together with the compact Video Edit workspace, adaptive previews, short-window controls and orientation/callback repairs. See docs/VIDEO-PANDA-MERGE.md and docs/VIDEO-EDIT-COMPACT-REBOOT.md. No new APK is implied.

Latest review: Panda reboot audit fixes are documented in docs/PANDA-AUDIT-2026-11-04.md. This does not imply a new APK.

# Latest source: Panda solo reboot

See docs/PANDA-SOLO-REBOOT.md for the recovered work, original owner request, completed implementation and validation. This source update does not imply a new APK.

# Latest source: clean game entry and Quit

Source0.100.57/code166. List-only Games; native Start/Resume choice before one splash; no retained menu intent; foreground remains paused; acknowledged Quit discards the session and blocks late re-saving. All six games and canonical Game SDK updated. No new APK in this pass. See docs/GAME-ENTRY-FLOW.md.

# Latest source: app and game refinements

Background-free header X, direct highlighted Speaker toggle, slower Cats vs Dogs, stronger Neon Dominos effects and rival wording, persistent Naval Fleet selection, compact green solo Start. Source0.100.56/code165; no new APK (latest delivered0.100.55/code164). Includes the prior per-game save and animated purple loader work. Checks and limits: docs/UI-GAME-REFINEMENTS.md.

# Latest source: polished Games loading screen

Purple full-screen loader, transparent white logo flight/overshoot/bounce, animated Games lettering, clear real-file progress and a short cached-preference-aware cue. Shared default across all six games and SDK, with reduced motion and custom studio PNG support. Browser animation/audio/accessibility-state checks, six real game starts, fourteen SDK methods and actual default/studio exports pass. Shared JS/WASM unchanged. See docs/GAME-LOADER-POLISH.md. Source0.100.56/code165; latest delivered APK0.100.55/code164 predates this loader and per-game saves. No new APK.

# Latest source: per-game cartridge saves

Independent saves for each title/publisher replace the global paused-game lock. Standard Resume/New/Cancel, confirmed safe New, host-acknowledged return, permanent data slots, previous-checkpoint recovery and stale-writer protection now cover all six games, including Hockey. Friend sessions retain match-scoped identity/journals; different titles do not block each other. SDK0.6 requires host0.100.56/code165. 25 native cases, 12 phone-size game save flows, shared session/model checks and all three portable SDK projects passed. See docs/GAME-SAVES.md and docs/game-saves-verification.json. Latest delivered APK0.100.55/code164 predates this source; no APK built.

# Latest APK —0.100.55-rc1/code164

Quick ARM64 APK includes the Crash Test Panda gameplay refinement. Assembly/signature/package/content/ZIP/16KB checks passed. Original signer retained. See docs/RELEASE-0.100.55.md and docs/apk55-verification.json. Earlier no-APK statements below describe the source-only stage.

# Latest source — Crash Test Panda gameplay refinement

12/15/18-block seeded courses, higher varied platforms, weighted materials, upper-sky birds before launch, elevated white aiming circle, lower cannon, bounded reserve rack, green Continue and20-second clocks. Standard game build/export and2102 Panda checks pass; original rules/saves retained. See docs/PANDA-GAMEPLAY-REFINEMENT.md. No new APK; latest delivered0.100.54/code163.

# Latest APK:0.100.54-rc1 / code163

Built and verified quick ARM64 update with original signer. Includes the refined Neon Dominos UI/gameplay and all preceding app/game fixes. See docs/RELEASE-0.100.54.md and docs/apk54-verification.json. Earlier no-APK notes below are historical.

# Latest source: Neon Dominos gameplay refinement

All-visible wrapping hand, larger adaptive table, direct one-tap/drag placement, distinct-end choice, gold new-draw highlight, compact scoreboard and smoother tile/reflow animation.60,324 layout and4,628 model checks plus two phone-size touch matrices passed. See docs/NEON-DOMINOS.md. APK0.100.53/code162 predates this source; no new APK built.

# Latest APK:0.100.53-rc1 / code162

Built and verified quick ARM64 update with original signer. Includes Neon Dominos and all preceding app/game fixes. See docs/RELEASE-0.100.53.md and docs/apk53-verification.json. Earlier no-APK notes below are historical.

# Latest source: Neon Dominos

New included single-player Neon Draw match against Nova: race to100, animated tiles/draws, neon scoreboard/progress, unboxed feedback, original music/effects, zoom and durable save/resume.4,628 model checks,2 Android cases and phone-size browser/reduced-motion flows passed. See docs/NEON-DOMINOS.md. Latest APK0.100.52/code161 predates this source; no new APK built.

# Latest APK:0.100.52-rc1 / code161

Built and verified quick ARM64 update with the original signer. Includes all completed game/audio/catalog/session refinements since51. See docs/RELEASE-0.100.52.md and docs/apk52-verification.json. Earlier no-APK notes below are historical.

# Latest source: Cats/Panda polish, audio continuity and game identity

Larger unboxed Cats feedback; original-style Panda cannon/loading/charge meter, build timer, larger8/10/12-block courses and occasional collision bonuses; shared audio safeguards; equal catalog cards/persistent scrollbar and explicit same-game recovery. See docs/GAMES-FINAL-REFINEMENT.md. No new APK;0.100.51/code160 predates these changes.

# Latest source: Air Hockey polish and Sound freeze repair

Preserved puck motion through Sound/Settings/Pause and saved return, recovered older stalled rallies, improved touch/render smoothness, unboxed neon goal/final effects and original upbeat music. Shared controls and SDK template tests now protect pause intent and rigid-body motion. See docs/AIR-HOCKEY-FINAL-POLISH.md. No new APK; delivered0.100.51/code160 predates this source.

# Latest source: Naval presentation refinement and purple loader

Shared game/SDK loading bar uses logo purple. Naval has orange actions, a higher dock, clearer spacing, unboxed animated reports, longer result holds, a lower shell arc with flight sound and a new louder score. See docs/NAVAL-PRESENTATION-REFINEMENT.md. No new APK; delivered0.100.51/code160 predates this source.

# Latest APK: OnKlav0.100.51-rc1 / code160

Built and verified quick ARM64 update with the original signer. Includes Crash Test Panda first in the included games, polished Cats vs Dogs and Blackjack, and all preceding Naval/friend-game repairs. See docs/RELEASE-0.100.51.md and docs/apk51-verification.json. Earlier no-APK notes below are historical.

# Latest source: Crash Test Panda included remake

Crash Test Panda is the first preinstalled game, with preserved characters/three environments, a full build-and-launch campaign, rotating rigid-body physics, music/effects, animated score/feedback and Rocket/Heavy/Shockwave powers. Solo and SMS friends use the canonical SDK controls. See docs/CRASH-TEST-PANDA.md. No new APK; delivered0.100.50/code159 predates Cats vs Dogs, Blackjack and Panda.

# Latest source: Blackjack polish

Emerald card table, animated deal/hole-card reveal, original effects, modern totals/wins scoreboard and centered hand reports for classic solo and ten-hand friend play. Canonical SDK toolbar/dialogs and legacy saved games preserved. See docs/BLACKJACK-POLISH.md. Focused model/browser checks and shared export passed. No new APK; delivered0.100.50/code159 predates this source.

# Latest source: Cats vs Dogs polish

One modern solo/friend board with original pet art, gravity/bounce effects, audible cues, touch targeting, portrait chain scoreboard, winning-line/confetti reports and a bounded tactical AI. Shared SDK toolbar/dialogs and compatible saved turn journals are preserved. See docs/CATS-DOGS-POLISH.md. Shared export and focused model/browser checks passed. No new APK; latest delivered0.100.50/code159 predates this source.

# Latest APK: OnKlav0.100.50-rc1 / code159

Built and verified quick ARM64 update with the original signer. Includes Naval combat/deployment polish and all preceding start/friend-dialog fixes. See docs/RELEASE-0.100.50.md and docs/apk50-verification.json. Earlier no-APK notes below are historical.

# Latest source: Naval Battle combat and deployment polish

Restored arcing shells and original fire/hit/miss/sink audio; incoming fleet view; explosions, blinking/red wrecks; centered combat reports; fleet HUD and distinct top-down ships with disappearing dock pieces. See docs/NAVAL-COMBAT-POLISH.md. Shared export and focused model/browser checks passed. No new APK; latest delivered0.100.49/code158 predates this source.

# Latest APK: OnKlav0.100.49-rc1 / code158

Built and verified optimized ARM64 update with the original signer. Includes Naval start recovery, restored original friend dialogs and durable invitation cancellation/retry. See docs/RELEASE-0.100.49.md and docs/apk49-verification.json. Earlier no-APK notes below are historical; physical phone acceptance remains separate.

# Latest source: game-start recovery and original friend dialogs

Repaired the Naval start dead end, pending-invitation cancellation/retry and the original Inner Circle recipient/side/connection/reverification flow. See docs/GAME-START-AND-FRIEND-DIALOG-REPAIR.md. No new APK; latest delivered remains0.100.48/code157 and predates these repairs.

# Latest APK: OnKlav0.100.48-rc1 / code157

Built and verified optimized ARM64 update with the original signer, Naval remake, Hockey single-only start and SMS friend games. See docs/RELEASE-0.100.48.md and docs/apk48-verification.json. Earlier no-APK notes below are historical; physical phone acceptance remains separate.

Current source:0.100.48-rc1/code157. SDK0.5; Naval remake and SMS friend games: docs/NAVAL-AND-FRIEND-GAMES.md. Latest built APK remains0.100.47/code156; no157 APK built this increment.

# Latest APK: OnKlav0.100.47-rc1 / code156

Built and verified optimized ARM64 update with original signer, saved-game catalog detection fix and all earlier game controls. See docs/RELEASE-0.100.47.md and docs/apk47-verification.json. Earlier no-APK notes below are historical; physical phone acceptance remains separate.

# Latest source: saved-game catalog status fix / next host156

Replaces process-cached paused-game preferences with a locked atomic marker so the catalog sees Back/save immediately. Existing checkpoints and all game UI/audio are preserved. Three focused framework-host regressions pass; see docs/GAME-SESSION-STATUS.md. No new APK; latest155 predates this correction. Earlier notes below are historical.

# Latest APK: OnKlav0.100.46-rc1 / code155

Built and verified optimized ARM64 update with original signer, latest game controls and Game SDK0.4. See docs/RELEASE-0.100.46.md and docs/apk46-verification.json. Earlier no-APK notes below are historical; physical phone acceptance remains separate.

# Latest source: Game controls correction / SDK0.4 / next host155

Original catalog start dialog after explicit New discard; same-game saved return/resume; real streamed audio/music; centered Help; paused label/Play pulse; branded splash and gear. See docs/GAME-CONTROLS-0.4.md. No new APK; latest154 predates these changes. Earlier status below is historical.

# Latest APK: OnKlav0.100.45-rc1 / code154

Built and verified optimized ARM64 update with original signer, latest game controls v2 and Game SDK0.3.1. See docs/RELEASE-0.100.45.md and docs/apk45-verification.json. Earlier no-APK notes below are historical; physical phone acceptance remains separate.

# Latest source: controls v2 / Game SDK0.3 / next host154

Direct Pause/Play; silent Sound/Settings with Done returning to play; exact Resume/Quit and New/Resume dialogs; real supported-mode start menus and global saved-game routing. See docs/GAME-CONTROLS-V2.md. No new APK; latest delivered remains0.100.44/code153. Earlier interaction descriptions are historical.

# Latest source: controls v2 / Game SDK0.3 / next host154

Direct Pause/Play; silent Sound/Settings with Done returning to play; exact Resume/Quit and New/Resume dialogs; real supported-mode start menus and global saved-game routing. See docs/GAME-CONTROLS-V2.md. No new APK; latest delivered remains0.100.44/code153. Earlier interaction descriptions are historical.

# Latest APK/source: OnKlav0.100.44-rc1 / code153

Game fullscreen/duplicate-header and stuck-resume fixes are complete. Shared X/Return/Pause and Help/Audio/Settings controls, resumable checkpoints and Game SDK0.2 are included. See docs/RELEASE-0.100.44.md and docs/GAME-CHROME.md. Earlier version/no-APK notes below are historical; physical Samsung gesture acceptance remains separate.

# Latest APK/source: OnKlav0.100.43-rc1 / code152

Air Hockey solo remake and shared-engine cleanup are finished and included in the verified optimized APK. Game SDK0.1.1 matches the same engine. See docs/RELEASE-0.100.43.md and docs/AIR-HOCKEY-REMAKE.md. Earlier recovery/no-APK/engine-pending notes below are historical; handset acceptance remains open.

# Latest source: recovered Air Hockey solo remake

New original rink/paddle/puck artwork, sound, goal feedback and responsive controls.79 hockey checks +12 shared rules and two browser layout checks passed. Same shared Godot4.7.2 engine/SDK0.1.0; no APK. See docs/AIR-HOCKEY-REMAKE.md for recovery limits and remaining engine/device work.

# Latest source: shared Godot4.7.2 game platform / Game SDK0.1

All games share one engine. Developers sign and share their own packages; users decide what to install. No owner approval. See docs/GAME-SDK.md and developer/games/README.md. No new APK; APK151 predates this source.

# Latest source: ADK 0.4.1 current-host refresh

50 authoring/distribution checks and all14 template builds/scenarios passed; independent extracted-starter smoke passed. Reference APK151 is separate from guest protocol96. See docs/ADK-REFRESH.md and developer/ADK-VERIFICATION.json. No new APK.

# Latest APK: 0.100.42-quick / code151

Includes rounded X dismissal controls, protected system/app-managed Files entries and all preceding Files workflow refinements. Original package/signer retained; assembly and APK verification passed. See docs/RELEASE-0.100.42.md and docs/apk42-verification.json. Earlier no-APK notes below are historical.

# Latest source: rounded X close controls and protected Files pencils

14 focused checks and compilation passed. See docs/CLOSE-CONTROLS-PROTECTED-FILES.md and docs/close-protected-files-verification.json. No new APK for this increment; latest APK remains 0.100.41.

# Latest APK: 0.100.41-quick / code150

Includes all completed Files workflow refinements and review fixes. Original package/signer retained; assembly and APK verification passed. See docs/RELEASE-0.100.41.md and docs/apk41-verification.json. Earlier no-APK notes below are historical.

# Latest source: comprehensive Files workflow review

36 unique focused host checks passed, production compilation passed and changed runtime classes match Kotlin output. See docs/FILES-COMPLETE-REVIEW.md and docs/files-complete-verification.json. Includes picker consistency, Android Trash bulk/recovery, compatibility, safe folder selection and parent navigation. No new APK.

# Latest source: Files review fixes

See docs/FILES-REVIEW.md and docs/files-review-verification.json. Six focused host checks passed; file copy cleanup, safe recursive deletion, Trash search and move chooser controls repaired. No new APK.

# Latest source: Files storage tabs

See docs/FILES-STORAGE-TABS.md. OnKlav / Android tabs, balanced toolbar icons, gallery-style pencil overlays, Android Internal files link and recoverable Android Trash. No new APK for this increment.

# Latest APK: 0.100.40-quick / code149

Includes the call/link/Files source refinements. Compilation and APK verification passed; see docs/RELEASE-0.100.40.md.

# Latest source: call/link/Files refinements (not yet APK-built)

See docs/CALL-LINKS-FILES-REFINEMENTS.md. Files header cleanup and OnKlave Files label, clickable message web links, and guarded Telecom/video teardown. Android validation and handset crash confirmation are pending; 0.100.39 APK predates these edits.

# Latest built APK: 0.100.39-rc1 / code148

See docs/RELEASE-0.100.39.md and docs/apk39-verification.json. All pending changes through Clone/slider icons are included. Earlier no-APK notes below are historical.

# Latest: Files and provider simplification

See docs/FILES-SIMPLIFICATION.md and docs/files-simplification-verification.json. This source update removes unused provider implementations, simplifies automatic media filing and replaces individual Android folder connections with direct browsing. No new APK was requested.

# Latest source — GMX removed completely

Email now offers Yahoo, Google and Private domain. GMX setup, presets and help are removed; existing local GMX accounts, credentials and local mail data are removed through resumable cleanup on Email open/background check. Remote mail and other accounts are untouched. 34 selected/shared checks passed, with compilation and runtime verification. See docs/GMX-REMOVAL.md. No new APK.

# Latest source — Email choices and Cloud Files sharing

Yahoo and Google app-password enrollment with clickable instructions, simple pCloud/Koofr setup and free-plan links, and Messages/Groups cloud-file sharing from the paperclip menu. Contact-card selection is replaced. No OAuth option, automatic sending or new APK. 47 selected/shared checks passed with compilation/runtime verification; cloud setup renders inspected. Verification: docs/EMAIL-CLOUD-SHARING.md. Prior fixes retained.

# Latest source — second review, Cloud and Calendar

Five further repairs: transactional Cloud account changes across windows, cancellation-safe commit acknowledgement, duplicate/replayed Calendar Snooze protection, fresh Calendar exports and timezone-correct reminder navigation. See docs/APP-REVIEW-2026-11-02-SECOND.md and its verification JSON. 48 focused/shared host checks passed, with production/test compilation and runtime-class verification. All prior repairs remain; no new APK.

# Latest source — application review, 2 November 2026

Five application defects and one build-check interruption repaired. Application fixes: system media-command conflicts, call-waiting alert suppression, call-control state mismatch, delayed outgoing call-screen launch and failed MMS recovery batch isolation/deadline preservation. 57 selected/shared Android host cases plus 38 Python tooling checks passed. Production/test compilation and runtime class verification passed. See docs/APP-REVIEW-2026-11-02.md and its verification JSON. Earlier SMS, call and landscape Camera fixes remain included. No new APK.

# Latest source — short SMS network-error reporting

SMS result 17 is identified as a network error. The multipart warning now depends on the staged part count, and older single-part failure cards receive the corrected explanation without changing their receipts/status. No automatic resend or SIM switching was added; the physical network/modem cause remains unconfirmed. All 36 selected/shared host checks passed, along with production/test compilation and runtime class verification. See docs/SMS-NETWORK-ERROR.md and its verification JSON. Call and landscape Camera repairs remain included; no new APK.

# Latest source — Phone call presentation and cleanup

Incoming calls have one notification-driven presentation path; the ringing banner is suppressed while the OnKlav answer screen is visible. Ended calls leave live state immediately and notifications clear on disconnect, removal, destruction, unbind and orphan recovery. Live-call channels no longer create an app-icon badge. All 41 selected/shared host checks pass, production/test compilation passed and the runtime bundle matches compiled Kotlin. See docs/PHONE-CALL-LIFECYCLE.md and its verification JSON for checks and device limits. No new APK; the saved landscape Camera correction is retained.

# Latest source — landscape Camera full-screen correction

Landscape live Camera now tells the home shell to hide its bottom navigation and release all outer screen insets, so the Camera workspace reaches every screen edge. Portrait and Captures restore the normal shell. APK 0.100.38 predates this correction; no new APK requested. See docs/CAMERA-LANDSCAPE-FULLSCREEN.md.

# Latest APK: OnKlav 0.100.38-rc1 / code147

Optimized ARM64 update with the original signer. Includes all Phone/Clock/inset screenshot fixes. 22 preceding host checks, release lint and APK verification passed. See docs/RELEASE-0.100.38.md and docs/apk38-verification.json. Physical rotation/cutout qualification remains separate. Earlier no-APK notes below are historical.

# Latest source — Phone/Clock/inset refinements, 2 November 2026

Dialer number/actions now center within flexible regions around the keypad. Clock uses the shared message-bubble theme brush. Full-page status/navigation bars and landscape Camera side insets cover wallpaper correctly. See docs/PHONE-CLOCK-INSET-REFINEMENTS.md and docs/screenshot-refinements-verification.json. No new APK; 0.100.37-rc1/code146 remains the latest delivered build. Earlier notes below are history.

# Latest APK: OnKlav 0.100.37-rc1 / code 146

Fast unshrunk ARM64 update with the original signer. Simplified non-scrolling responsive Phone keypad and unified Save/Call/Delete controls, header Speed dial/Voicemail, 10% smaller key targets, and extra Apps header clearance. See docs/RELEASE-0.100.37.md and docs/apk37-verification.json. Earlier notes below are history.

# Latest APK: OnKlav 0.100.36-rc1 / code 145

Built and verified ARM64 owner-testing APK with the original package/signer; install as an update. Includes all completed Phone detail/header/keypad/contact/recent refinements plus the bold, high-contrast jump arrow shared by Messages and Groups. Existing audit repairs, root navigation, desktop folders and app features are retained. 14 Phone checks and 2 new arrow checks passed; full production/test lint reports zero errors. Assembly and APK verification passed. See docs/RELEASE-0.100.36.md and docs/apk36-verification.json.

Earlier progress/no-APK notes below are historical and superseded by this APK. Physical device/carrier qualification remains separate.

# Phone/Camera root navigation and dialer polish — 2 November 2026

Phone and Camera now open directly within the persistent main bottom menu, without slide-out routes or root back arrows. Phone has compact top tabs, a padded centered Call action, and a direct Android Default Phone consent button for the existing OnKlav incoming/ongoing call UI. See docs/PHONE-CAMERA-ROOT-NAVIGATION.md and docs/phone-camera-root-verification.json for focused verification and device limits. 24 focused Android host tests, production/test compilation, runtime class matching and rendered-screen review passed. Source only; no new APK. Previous six audit repairs remain included.

# Latest source — six audit repairs completed

B01–B06 are fixed and verified: 61 unique Android host regressions plus all 30 Python tooling tests pass. Production and all test sources compile; runtime bytes match compiled Kotlin. Read docs/AUDIT-REPAIRS-2026-11-01.md and docs/audit-repairs-verification.json. No new APK was requested/built; latest delivered remains 0.100.34-rc1/code143 and predates these repairs. Native/SDK/game outputs remain source-matched; original signing identities are unchanged. Earlier status notes below are historical and must not reopen completed audit findings.

# Latest source audit — 1 November 2026

Current APK remains 0.100.34-rc1/code143. The audit adds six open functional findings plus two test/documentation items to TODO.md; no application code changed and no APK was built. Read docs/APP-DEEP-REVIEW-2026-11-01.md before choosing the next repair. Evidence separates 67 passing functional/tooling cases, 3 diagnosed tooling failures, and 4 reproduced defect observations; full-module Android checks were blocked by missing cached dependencies. Earlier audit findings below describe previous review increments and do not supersede this review.

# Latest APK: OnKlav 0.100.34-rc1 / code 143

Fast unshrunk ARM64 debug build, original signer, install as an update. Includes the screenshot-matched two-page desktop defaults, expanded Utilities/Create membership, destination-page gap handling before overflow and deliberate edge dwell before page changes. All prior Phone/Cloud/Write fixes remain included. 23 prebuild checks passed; full production/test lint reports zero errors. Assembly and APK verification passed. See docs/RELEASE-0.100.34.md, docs/apk34-verification.json and docs/DESKTOP-DEFAULT-LAYOUT.md.

Earlier source notes below are retained as progress history; their completed changes are included in this APK.

Latest source: Cloud Files native HTTPS WebDAV app with pCloud US/EU, Koofr, 4shared and custom server setup; encrypted sign-in, browse/search/new folder, streamed upload/download through Files, progress and account management. Google Drive UI retired; backup/restore preserved under Backups. 14 focused host checks and full production/test lint passed with no errors. Live provider/phone qualification remains; no new APK. See docs/CLOUD-FILES.md.

Latest source: Main navigation Phone/Camera/Apps/Messages/Groups; Settings and Games desktop apps; every Apps-button tap opens page one; named drag-and-drop folders with nine-icon swipeable pages and one-time Utilities/Create defaults. 11 focused host data/UI checks and full production/test lint passed with no errors. No new APK. See docs/DESKTOP-FOLDERS.md.

Latest source: Phone everyday-use review: live number formatting, visible Voicemail and eight-slot Speed dial, confirmed contact deletion, ringtone settings and save feedback. 17 focused host/UI checks and full production/test lint passed with no errors. Real-phone/carrier qualification remains. No new APK. See docs/PHONE.md.

Latest work: Phone native dialer with Keypad/Recents/Contacts, Telecom call UI and setup integration. 13 focused host/UI tests and full production/test lint passed. Real-device/carrier qualification remains. See docs/PHONE.md. No new APK.

Latest source: Email enrollment simplified to GMX and Private domain, GMX secure presets and setup directions. Existing accounts and Calendar work retained. See docs/EMAIL-GMX-PRIVATE-DOMAIN.md. No new APK.

Latest source: Calendar UI review, clearer Add event/search, accessible wrapping color controls, direct all-day Week navigation and date ranges, simplified matching outline calendar icon. See docs/CALENDAR-UI-REVIEW.md. No new APK; 0.100.31 predates Calendar.

Latest source: Calendar offline native app with Day/Week/Month/Agenda, search, multi-day and repeating events, five independent silent/sounded reminders, local color calendars, .ics import/export and standard event-insert intents. See docs/CALENDAR.md and docs/calendar-verification.json. No new APK; 0.100.31 predates Calendar. Phone reminder/interop/store acceptance remains separate.

Latest APK: **OnKlav 0.100.31-rc1 / code 140**, optimized ARM64 with original signer. Includes the Email, Vault restart and sharper Stamp refinements below. See docs/RELEASE-0.100.31.md and docs/apk31-verification.json. Earlier no-APK notes are historical. Physical-phone/provider/security qualification remains separate.

Latest source: Vault password-only/incomplete setup now restarts with new password + three answers, without asking for the old password; completed Vaults remain protected. Stamp adds persisted Hardness with a sharper 90% default and edge-only feathering. See docs/VAULT-STAMP-CORRECTIONS.md. No new APK.

Latest source: Email new-account setup now offers Gmail and Custom domain only; no Proton webmail option or other provider presets. Existing accounts are preserved. Custom domain explains hosting settings and uses a normal Password label. No new APK.

Latest source: Email everyday workflow polish: account-isolated conversations, recent-recipient suggestions, responsive composer and simplified setup, safer draft flushing/send revision checks and background cache/schedule fixes. See docs/EMAIL-EVERYDAY-POLISH.md and docs/email-polish-verification.json. No new APK; 0.100.30 predates these changes. Real-provider and physical-phone qualification remain pending.

Latest APK: **OnKlav 0.100.30-rc1 / code 139**, optimized ARM64, original signer. All source changes listed below are included. See docs/RELEASE-0.100.30.md and docs/apk30-verification.json. Earlier no-APK notes describe implementation-time status. Physical-phone qualification remains pending.

Latest source: Messenger same-conversation notification input fix, automatic Inbox pagination, high-contrast latest arrow; Authentic atomic password-plus-three-question enrollment and encrypted reset; compact retouch chips, larger outline Voice Record icon, Social Coming Soon placeholder. 21 focused unique host checks passed. See docs/MESSENGER-AUTHENTIC-UI-REFINEMENTS.md. Existing encrypted Authentic accounts are retained and require their password once to finish recovery enrollment. No APK; 0.100.29 predates current source changes.

Latest source: Image Edit keeps drawing tools visible during Retouch, removes Drawing back button, adds aligned soft-edge Stamp between Red eye and Heal, broadens pink/purple red-eye handling and reports actual correction/no-match results. Production/test compilation and 23 focused checks passed. See docs/PHOTO-STAMP-AND-RETOUCH.md. No new APK; 0.100.29 predates this and Video Edit surface polish. Phone qualification pending.

Latest source: Video Edit surface polish based on phone screenshots: themed tool cards, direct Add clips/audio, quieter transport/timeline styling, compact labeled Export. Production compilation and 10 focused host UI checks passed. See docs/VIDEO-SURFACE-POLISH.md. No new APK; 0.100.29 predates this UI pass. Physical-phone qualification pending.

Latest APK: **OnKlav 0.100.29-rc1 / code 138**, optimized ARM64, original signer. All source features listed below are included. See docs/RELEASE-0.100.29.md and docs/apk29-verification.json. Earlier no-APK notes describe implementation-time status. Physical-phone qualification remains pending.

Latest source: Authentic included offline authenticator, with standard OTP codes, QR/manual setup, Google transfer import, protected local accounts and encrypted backups. See docs/AUTHENTIC.md. No new APK; physical-provider/security qualification pending.

Latest source: Code Edit included app, with native line-numbered syntax editing, file tabs, inline Find/Replace, indentation, draft recovery and signed Files save-back. See docs/CODE-EDIT.md. No new APK.

Latest source: Image Edit Retouch tools (red-eye and healing brushes), shared Undo/Redo and Layers persistence. See docs/PHOTO-RETOUCH.md. No new APK.

Latest source: Video Edit selection, tool discovery, save/draft feedback and unavailable-action guidance refined. See docs/VIDEO-UI-POLISH.md. No new APK.

Latest source: Video Edit mobile workflow rebuilt around a stable preview, focused dock, section tabs, staged text/canvas and guarded destructive edits. Production compilation and 28 unique focused host checks passed. See docs/VIDEO-PHONE-WORKFLOW.md and docs/video-phone-verification.json. No new APK; physical-phone qualification remains pending.

Latest source: cleaner utility/folder icons and shorter Edit/Record display labels. See docs/ICON-AND-NAME-REFINEMENTS.md. No new APK;0.100.28 predates these changes and Vault recovery.

Latest source: Vault three-question recovery implemented;15 focused host tests passed. See docs/VAULT-RECOVERY.md and docs/vault-recovery-verification.json. No new APK;0.100.28-rc1 predates these changes.

Latest APK: OnKlav0.100.28-rc1/code137, original signer. See docs/RELEASE-0.100.28.md. Current source refinements: docs/SETUP-POWER-RADIO-WRITE-REFINEMENTS.md.

Utility icons updated in source: Format Swap, Screen Recorder, Voice Recorder and Vault. See docs/UTILITY-ICON-REFRESH.md and docs/evidence/utility-icons/utility-icons-preview.png. No new APK;0.100.27 predates these icons.

Current APK: **0.100.27-rc1 / code136**, optimized ARM64, original signer. Built and verified; install as an update. See [docs/RELEASE-0.100.27.md](docs/RELEASE-0.100.27.md) and [docs/apk27-verification.json](docs/apk27-verification.json). Phone qualification remains pending. Earlier no-APK notes below describe their implementation-time status.

Docu View/screen-light fade: see [docs/DOCU-VIEW.md](docs/DOCU-VIEW.md). Source only; no new APK.

# Latest source — Voice Recorder and Screen Recorder

Two included, themed recording apps with persistent Start/Pause/Stop controls, real input metering, pause-aware timers, foreground/background recording, notification controls, searchable playback libraries, sharing, copy export and signed Files saving. Voice supports M4A and WAV; Screen supports instant/countdown, two video presets and silent/device/mic/both audio. Interrupted/unfiled originals are retained; service shutdown gates a new Start. Also fixed media backup validation above 256 MiB within the existing 512 MiB archive limit. Production compilation, 16 unique staged host checks and 11 native backup checks passed; matching host/ARM64 working JNI rebuilt. Physical capture/codec/A-V-sync/long-recording acceptance remains pending. No new APK built. See docs/RECORDERS.md and docs/recorder-verification.json. Preserve prior work below.

# Latest source — Vault and folder-protection retirement

Vault is an included password-encrypted private rich-text document: confirmed password setup/visibility, basic formatting/links/images, autosave, explicit Save feedback, save-and-lock on exit/background, password changes, and encrypted App Documents backups. New folder protection is removed from Files; existing encrypted folders retain password-based access/removal to avoid stranding data. No plaintext Vault document or recovery key is stored. Production compilation, 14 focused Rust backup checks and 10 unique staged host crypto/JNI/UI checks passed; matching host/ARM64 working JNI rebuilt. Physical phone and independent security qualification remain pending; no new APK built. See docs/VAULT.md and docs/vault-verification.json. Preserve prior work below.

# Latest source — Groups routing and unread fixes

Newly created groups go to Inbox; other people’s group texts are discovered from phone participants and routed to Groups. New unblocked SMS/MMS receipts restore Archive/Trash and mark unread; duplicate/notification-only refreshes preserve later read/archive actions. Group settings, pin/mute, first-arrival binding and notification routing are preserved. Current Rust messaging checks23 and staged host checks27 passed; matching host/ARM64 working JNI rebuilt. Physical carrier acceptance remains pending; no new APK built. See docs/INCOMING-GROUPS-FIX.md and docs/incoming-groups-verification.json. Preserve prior work below.

# Latest source — Video Editor everyday review

Reviewed and refined trim/speed/keyframe correctness, gesture/preview stability, project-aspect preview, draft export/canvas settings, short-screen layouts, unsaved-edit recovery/Undo, audio fades/mute, timeline selection, large-backup preflight and light-theme visibility. Production compilation and staged focused checks passed (37 unique checks; final theme check passed). Physical phone export/audio/video/performance acceptance remains open. No new APK built. See docs/VIDEO-EDITOR-DAILY-REVIEW.md and docs/video-daily-review-verification.json. Preserve all prior work below.

# Latest source — Compress

Compress is an included desktop utility for smaller JPG/WebP images, playable MP4 video and MP3 audio, and ZIP files/folders. Simple quality/size controls, sticky projected-size feedback, actual savings, cancellation, OnKlav Files saves and Share saved copy are implemented. Production compilation and eight focused host checks passed, including actual signed Files folder traversal. Physical phone codec/share acceptance remains open; no new APK built. See docs/COMPRESS.md and docs/compress-verification.json. Preserve all prior work below.

# Latest source — Format Swap

Format Swap is an included desktop utility with Audio/Video/Image/Document choices, numbered File → Format → Save progression, real local conversion, OnKlav Files destinations, quality/resize options and explicit completion. Production compilation and nine focused host checks passed. PDF and real phone codec/file-provider acceptance remain open; no APK built. See docs/FORMAT-SWAP.md and docs/format-swap-verification.json. Preserve all prior work below.

# Speech safety follow-up — source complete

Tightened shared TTS marker and abbreviation rules to retain standalone units/initials/Roman-looking words and prevent cross-line expansion. Production compilation and 21 focused checks passed. Voices/models/playback unchanged; no new APK. See docs/SPEECH-TEXT-CLEANUP.md. Preserve all earlier work below.

# Latest source — shared speech reading cleanup

Common titles/initials/number labels, emoji/smileys, standalone Roman/section/divider markers and invisible book characters are handled centrally for SDK speech. Original UTF-16 positions are mapped through the speech-only projection; source text and existing voices/models remain unchanged. Production compilation and 19 focused checks passed. See docs/SPEECH-TEXT-CLEANUP.md and docs/speech-reading-verification.json. No APK built; preserve all prior Write/Radio/Power/editor/messaging work.

# Latest source — Write selection refinement and deep review

Inline color/highlight palette replaces toolbar rows, retaining selection, keyboard and page position. Native selection-menu handoff, backwards ranges, undo grouping, open failure recovery, background/final saves and DOCX import feedback improved. Production compilation and 16 focused checks passed. Remaining source findings/features are in docs/WRITE-APP-DEEP-REVIEW.md; verification is in docs/write-app-review-verification.json. Preserve prior Radio, Power/setup and editor/messaging fixes. No new APK built.

# Latest source — Write toolbar and Radio handoff

Toolbar rearranged; Lists/Align/Indent separated, Redo above Undo at right. Radio preparation waits for old stream release before probing, and probe sockets cancel before close. See docs/WRITE-TOOLBAR-RADIO-HANDOFF.md for verification and limits. Preserve all previous Power/setup, Photo, messaging and editor work. No new APK built.

# Latest work — Power and setup

Power system utility and setup version 4 are implemented. See docs/POWER-AND-SETUP.md. No new APK requested or built. Preserve all Photo, messaging, audit and editor fixes below.

# Current recovery — Photo and audit fixes

Source fixes for all seven audit findings and Photo effects are implemented. See docs/PHOTO-AND-AUDIT-FIXES.md. No new APK built. Earlier open-audit descriptions below are historical and superseded by this source work.

# New-message Inbox rule and application review — source complete

New unblocked SMS/MMS restores Archive or Trash to Inbox. Blocked arrivals are retained outside Inbox and normal/picture-retry alerts are suppressed. Pin/mute metadata stays intact. 21 unique functional host regressions passed; the duplicate direct-store harness is not counted twice. Final production compilation and runtime bytes matched. No new APK; latest delivered remains 0.100.26-rc1/code135.

The deep application review is in docs/APP-DEEP-REVIEW-2026-10-30.md and docs/app-audit-verification.json. Seven functional findings remain open. First priorities: outgoing receipt compaction (1000-record send ceiling), recoverable receipt filing, shared transactional exports, and Clock save/schedule rollback. Five audit observations reproduced four finding groups; observations demonstrate defects, not passing desired behavior. Full-source debug lint reached the 300s cap without a final report; no full lint pass is claimed. Keep bounded checks and all prior editor work.

# Video Editor refinements — source complete

Frame stepping, exact playhead/audio position, zoom-aware snapping, persistent audio selection and lane highlighting, vertical timeline scrolling, cached proxy reuse, M4A duration fallback, and recoverable atomic saves are implemented. 14 focused production backend checks and 9 isolated production-UI checks passed. The UI checks use explicit test-only backend/player fixtures; they are not real codec acceptance. See docs/VIDEO-EDITOR-REFINEMENTS.md and docs/video-editor-refinements-verification.json.

Latest delivered APK remains 0.100.26-rc1/code135; no new APK requested or created. Preserve incoming Archive→Inbox, all prior editor work, original signers and shared speech. Physical Samsung codec/playback/export/sound-quality/IME/rotation acceptance remains separate. Do not repeat passed broad suites.

# Video Editor mobile UI — source complete

Responsive portrait/landscape workspace, four tool groups, bounded scrolling inspector, larger targets/track lanes, one selected audio track and compact status/fullscreen fallback.5 isolated production-UI screen checks passed with test-only backend/player fixtures; full production build and phone media acceptance remain untested. No new APK; latest delivered0.100.26/code135. Preserve incoming Archive→Inbox fix and all prior work. See docs/VIDEO-EDITOR-MOBILE-UI.md.

# Incoming archive fix — source complete

New incoming SMS/MMS moves an archived conversation back to Inbox, preserving pin/mute metadata and excluding blocked senders/Trash.4 focused real-store/Rust/Robolectric checks passed; full-module notification tests blocked by missing restored dependencies. See docs/INCOMING-ARCHIVE-FIX.md. No new APK; latest delivered remains0.100.26/code135.

# APK 0.100.26-rc1 / code135 — built and verified

Includes the completed Audio Editor mobile layout, usability refinements and deep-review fixes. Original signer and optimized ARM64 single-APK packaging retained. Previous28 focused checks retained; optimized APK assembly and signature/version/contents/16KB alignment checks passed. Physical phone and sound-quality acceptance remain separate.

# Audio Editor deep review — source fixes complete

Import adoption/cancellation, export descriptor failure handling, Undo/Redo persistence, reverse waveform and fade bounds corrected. Buffered WAV reads retain float headroom; dry comparison, headphone-disconnect cleanup, early MP3/WAV limits and rate-aware DC filtering improved. UI labels clarified.28 unique focused checks passed; runtime matches compilation. See docs/AUDIO-EDITOR-AUDIT.md and docs/audio-editor-audit-verification.json. Noise-profile/pitch alignment and broader sound-quality/device acceptance remain explicit follow-ups. No new APK.

Keep all prior work, original identities, optimized packaging and selected shared voices. Native/SDK/game/speech outputs remain reusable. Do not repeat passed broad suites; next requested APK follows0.100.25/code134.

# Audio Editor final usability refinements — source complete

Focused waveform/return/Android Back, explicit short-screen access and a compact tool chooser; exact values with safe unit/range validation and history/slider synchronization; long-press labels; full selectable error/status snapshots.8 unique focused checks passed (5 UI +3 numeric); revised Back/details cases rerun alone, earlier passes retained. Runtime matches compilation; screenshots reviewed. See docs/AUDIO-EDITOR-REFINEMENTS.md and docs/audio-editor-refinements-verification.json.

No new APK. No connected Galaxy S21+; actual phone/IME/rotation/audio/performance acceptance remains open. Preserve original signer,0.100.25 optimized packaging, selected shared voices, native/SDK/game identities, migrations and all previous work. No repeat broad suites needed.

# Audio Editor layout refinement — source complete

Settings stay in a bounded independent panel; playback/history remain reachable. Three visible choices per category, compact waveform selection,48dp icon/slider targets and full-row switches; wide landscape uses two panes. Larger text has wider tool buttons and unbroken selection times. All existing DSP/export/ownership behavior retained.

Eight unique focused checks passed (five retained engine/host, three final small-phone/landscape/160% Compose UI cases); screenshots reviewed, correct resource mappings and byte-matched compiled/runtime Kotlin. See docs/AUDIO-EDITOR-LAYOUT.md and docs/audio-editor-layout-verification.json. No new APK; next requested build follows0.100.25/code134, retains original signer and optimized packaging. No repeat broad suite; physical handset acceptance remains separate. Preserve all prior work and shared voices.

# Continue OnKlav

Current work:0.100.25-rc1/code134. User requested smaller size, cleanup and responsiveness without feature removal. Exactly two selected Nano0/2 voice rows and English data are now in the single shared pack. The FP32 graph, selected style rows, speed priors and retained pronunciation bytes are unchanged; only metadata reindexes original2 to compact row1. Stable app-facing choices remain Male/default2 and Female/0.

Screen-off UI_HIDDEN no longer unloads the warm speech model; genuine memory pressure still does. Removed the unused Rhino JSR-223 adapter; NewPipe still uses the full Rhino core directly. Release uses the existing optimized variant with JNI/Godot/mail/NewPipe/Rhino keep rules. No app/game, recovery/migration or supported feature is removed.

Read AGENTS.md, TODO.md and docs/SIZE-AND-RESPONSIVENESS.md. Current phase: native rebuild/byte-level pack proof and22 host/3 native checks passed; optimized APK0.100.25-rc1/code134 assembled/verified with original signer. Final size120,158,675bytes,34.18% smaller than the previous debug deliverable. All23 apps/non-speech assets retained unchanged. See docs/size-responsiveness-verification.json. Save current sources before a long build. Keep original signer/package and required dependency source/licenses.
