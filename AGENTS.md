# OnKlav current engineering instructions

Current work: Docu View/screen-light fade (docs/DOCU-VIEW.md); Voice/Screen Recorders (docs/RECORDERS.md); Vault/folder-protection retirement (docs/VAULT.md); Groups routing/unread (docs/INCOMING-GROUPS-FIX.md); Video Editor everyday review (docs/VIDEO-EDITOR-DAILY-REVIEW.md); Compress (docs/COMPRESS.md); Format Swap (docs/FORMAT-SWAP.md); shared speech reading cleanup (docs/SPEECH-TEXT-CLEANUP.md); Write deep review and selection refinement (docs/WRITE-APP-DEEP-REVIEW.md); Write toolbar and Radio handoff (docs/WRITE-TOOLBAR-RADIO-HANDOFF.md), plus Power/setup (docs/POWER-AND-SETUP.md). Latest delivered APK is 0.100.62-rc1/code171 quick ARM64; current source entry fixes are 0.100.57-rc1/code166 (docs/GAME-ENTRY-FLOW.md); latest audit repairs are in docs/AUDIT-REPAIRS-2026-11-01.md and START-HERE.md. Prior editor checks remain in docs/VIDEO-EDITOR-REFINEMENTS.md. Size/build baseline is in docs/SIZE-AND-RESPONSIVENESS.md. Historical progress is in docs/history/pre-0.100.24; it must not override current source or instructions.

- Preserve the single APK, package and original Android/publisher signing identities. Never generate a replacement signer or put keys in source. Internal apps/games are signed OnKlav packages, not separate APKs.
- Keep Kotlin/Compose Android UI, Rust core/services and Godot games. No legacy Java application or copied Inner Circle module. Upstream Android/JVM/native dependencies remain necessary.
- Host permissions are requested during setup. External apps use explicit revocable broker capabilities; do not grant guests all host permissions. Deliberate revocations remain effective.
- All ordinary open/save/multi-file actions use OnKlav Files. Android Files exposes direct shared-storage categories after its one-time Android permission; do not reintroduce the individual folder chooser into the main Files or attachment UI.
- Speech uses one English-only host Nano FP32 model with two compact voice rows and one native backend. Only speaker2 (Male, default) and speaker0 (Female, owner-requested label) are exposed; both upstream speakers are male. Original speaker IDs0/2 map to compact rows0/1; do not change speed priors or graph weights. Do not reintroduce Kokoro, system TextToSpeech, per-app models or the abandoned audition engine. See docs/NEURAL-SPEECH.md and sdk/README.md.
- Recorders share one user-started foreground-service lease. Keep real input metering, pause-aware timers, explicit per-session screen consent, non-reused projection tokens and visible Stop controls. Publish the idle/ready UI state only after capture is released and service shutdown completes; preserve unfiled/interrupted originals on errors. Completed clips go through the signed Files broker; never bypass Files revocations or expose arbitrary paths. See docs/RECORDERS.md.
- Vault is a host-private, password-encrypted document, never a Files entry. Keep decrypted contents in memory; do not introduce plaintext working/recovery files, stored passwords, Keystore/master-key bypasses or sensitive saved-instance state. Three-question recovery is an authorized second encrypted key wrap; treat a valid password-only Vault as incomplete setup and start over with a new password plus all three answers (owner-requested); completed Vaults still require the password or all three answers. See docs/VAULT-RECOVERY.md. Re-lock on leaving/backgrounding. New folder protection is retired; preserve legacy unlock/remove-protection and old backup recovery. See docs/VAULT.md.
- Keep active backup/document migration and Notes recovery functionality. These protect existing user data and are not abandoned app code. Never copy/delete private reference-app data silently.
- New groups start in Inbox. Received multi-person SMS/MMS threads belong in Groups regardless of who created them; keep group unread/read state aligned with Messages. Discovery alone must not resurface archived history. See docs/INCOMING-GROUPS-FIX.md.
- Every new unblocked SMS/MMS must restore Archive/Trash to Inbox. Blocked arrivals are retained in Archive unless already in Trash; suppress conversation and MMS retry alerts. Preserve pin/mute and duplicate-delivery safeguards. See docs/INCOMING-INBOX-FIX.md; receipt recovery is implemented; see docs/PHOTO-AND-AUDIT-FIXES.md.
- Preserve established compact controls/themes, message behavior, smooth drawer/page animation and one-running-renderer behavior with independent saved sessions. Full pages enter/exit right; dialogs use centered scale/fade.
- Reuse hash-verified native/SDK/game outputs when inputs match. Native source changes require rebuilding affected libraries and honest provenance. Never forge proofs to bypass stale code.
- Use bounded focused checks and build.py --variant debug for iteration; deliver the optimized release variant for size/performance comparisons after reflection/JNI/package validation. Do not repeat broad UI/emulator loops. Full lint includes production and test source. Distinguish host checks and APK validation from physical phone/carrier/performance acceptance.
- Default workflow: python3 scripts/build.py --plan, then the authorized build. For selected signed SDK changes use scripts/build_sdk_apps.py --plan and --app NAME. Keep the shared Web runtime contract aligned with the Game SDK; see docs/GAME-SDK.md.
- Preserve required third-party licenses and corresponding source. Public SDK/release licensing and independent device/security qualification remain open.
- Save the updated source checkpoint durably and keep completed work across interruptions.

- Preserve Video Edit stable preview allocation and focused dock; tool selection must not shrink preview. Keep explicit trim/transform modes, target labels, staged text/canvas, and destructive confirmation. See docs/VIDEO-PHONE-WORKFLOW.md.

- Preserve the explicit Video Edit selection strip, labeled choices, fixed-height save feedback and direct recovery routes in unavailable settings. See docs/VIDEO-UI-POLISH.md.

- Retouch corrections are source-normalized operations replayed before geometry/color; preserve original pixels, Undo/Redo/Layers, gesture cancellation and work limits. Keep Red eye/Clone/Heal inside Tools rather than crowding the main toolbar. See docs/PHOTO-RETOUCH.md.

- Code Edit is a native local text editor. Preserve selected-Files-only access, digest-checked journaled save-back, atomic recovered drafts, text/encoding/work limits and IME layout. Do not execute opened code or introduce a web editor/runtime dependency. See docs/CODE-EDIT.md.

- Authentic secrets must remain in its password-encrypted private envelope. Preserve complete-stream verification, atomic saves, monotonic HOTP merge/advancement, all-fragment import confirmation, redacted errors, secure-window/lifecycle locking and temporary owner-checked clipboard codes. Do not add plaintext backups, seed logging, silent remote sync or password bypasses. See docs/AUTHENTIC.md.

- Video Edit surface polish uses consistent tool cards, compact labeled Export and the existing stable workspace allocation. Keep Add clips/audio routed through OnKlav Files. See docs/VIDEO-SURFACE-POLISH.md.

- Image Edit must keep the ordinary tool row visible during Retouch; no Drawing back button. Clone Stamp stores normalized source anchors with frozen per-stroke sampling, aligned UI offset, feathering, Undo/Redo/Layers and existing work limits. Report actual red-eye/no-match results. See docs/PHOTO-STAMP-AND-RETOUCH.md.

- Authentic setup now requires three recovery answers before access. Reuse the authenticated VaultRecovery second key wrap with Authentic identity; preserve existing encrypted accounts during authenticated legacy enrollment. Fresh incomplete setup writes no password-only file. Reset must verify all answers and the full ciphertext before rewriting. See docs/MESSENGER-AUTHENTIC-UI-REFINEMENTS.md.
- Messenger same-peer incoming intents must preserve draft readiness; an address-keyed loader cannot recover a false readiness flag on an unchanged address. Keep automatic Inbox pagination with lookahead/exhaustion and preserve newly typed drafts across send completion.

- Preserve Email account-isolated reference threading, bounded off-thread recipient suggestions, draft revision/phase safeguards, cache pagination retention and live revocable grant checks. See docs/EMAIL-EVERYDAY-POLISH.md. No new APK for this refinement.

- Email offers only Yahoo, Google and Private domain, with password/app-password sign-in and official help. GMX and its migration/purge code, other retired presets and Email OAuth are removed at the owner’s request. Do not reintroduce them. Keep generic third-party SDK capabilities separate. See docs/FILES-SIMPLIFICATION.md.

- Calendar is a host-native offline app. Preserve exclusive all-day end dates, zone-aware recurring wall times, per-occurrence exceptions, atomic validated storage and one-next-alarm scheduling. All import/export uses OnKlav Files. Keep silent and alarm-tone channels separate; report missing Android permission, unsupported ICS data and real-device qualification honestly. Calendar export provides its backup; it is not yet included in Rust App Documents Backup. See docs/CALENDAR.md.

- Calendar UI: keep the Calendar name and simple theme-tinted outline icon; preserve accessible wrapping color choices, explicit Add event, and visible all-day cards when entering Day view. See docs/CALENDAR-UI-REVIEW.md.

- Phone is a native Telecom default-dialer UI. Keep ACTION_DIAL/VIEW as prefill-only, place calls via TelecomManager, and retain the protected exported InCallService plus private call controls. Default Phone role must precede call-log/runtime requests in setup v5. Preserve system ringing/emergency handling, modern/legacy audio routes and user-confirmed contact/history changes. See docs/PHONE.md.

- Main navigation is Phone/Camera/Apps/Messages/Groups. Settings and Games are desktop apps; Settings must stay recoverably visible. Every Apps-button tap resets the apps pager to its first page. Desktop folders use same-window drag overlays, nine icons per page, persisted unique membership and one-time Utilities/Create seeding. Deleting a folder restores apps without deleting app data. See docs/DESKTOP-FOLDERS.md.

- Cloud Files is the native HTTPS WebDAV app. Keep credentials in its device-bound encrypted no-backup envelope, confine authenticated requests to the configured origin/root, reject redirects and unsafe XML, preserve create-only uploads and explicit Files selection/export. Google Drive implementation is removed; provider-neutral Backups and existing data recovery remain. See docs/CLOUD-FILES.md.

- Phone/Camera are bottom-navigation destinations only, excluded from Apps/folder rendering. Reserve the whole folder cell during app drags before reordering. Phone settings must not launch external sound/carrier/blocked screens; use internal voicemail access-number and blocked-number management. Preserve system-owned consent/emergency handling. See docs/PHONE-DESKTOP-POLISH.md.

- Desktop defaults match the two owner reference pages (docs/DESKTOP-DEFAULT-LAYOUT.md). Preserve arrangement_version 2 as a one-time migration, user customization afterwards, page-local gap use before overflow, and deliberate edge dwell before page changes.

- Audit repairs: persist failed-writer state before error-close; await Files filing only after releasing AppSdk.staging. Only explicit owner recovery may file interrupted bytes. Keep destination size preflight/stream limits and Cloud destination-before-download/retry behavior. SMS/MMS must persist/reuse logical filing order separately from provider dates; never reallocate on replay or repeated MMS callbacks. Calendar must surface scheduling failure and retain the old schedule on refusal; preserve timestamp UNTIL cutoff handling and Cloud envelope byte budgets. See docs/AUDIT-REPAIRS-2026-11-01.md.

- Phone and Camera are direct root bottom-menu destinations: no right-slide route or root exit arrow. Preserve compact Phone top tabs, padded Call row, parent-owned insets and the direct user-approved Default Phone role action. Standalone add-call/dial activities and internal Camera gallery navigation retain meaningful back actions. See docs/PHONE-CAMERA-ROOT-NAVIGATION.md.

- Phone detail polish: preserve themed circular quick-call controls at the far right of contact/recent rows, explicit number selection for multiple numbers, compact bounded scrolling detail dialogs and caller privacy/confirmation safeguards. Keypad keys now use 54/66.6 dp normal target heights (10% smaller), adapt to available workspace height, and stay in one non-scrolling workspace with Save/Call/Delete. Speed dial and voicemail are header actions. See docs/PHONE-DETAIL-POLISH.md.

- Phone uses HomeHeader to match other root tabs. Phone/Messages number keys share DialPadColors with visible outlines and contrast-aware digit ink. Preserve tall keys, input/long-press semantics, root menu spacing, standalone back action, and themed row quick calls. See docs/PHONE-CHROME-KEYPAD.md.

- Regular and group feeds share ChatFeedAction. Keep the bold vector latest-arrow, theme-tinted circle and contrast-selected ink; preserve the existing jump/history reset and at-latest Activities callbacks. See docs/CHAT-LATEST-ARROW.md.

- Phone keypad has no helper text, inline shortcut links, paste button or number-options overflow. Keep header speed dial/voicemail, one centered number display, and Delete beside Call. The whole keypad/control group resizes together; never restore an independently scrolling keypad beneath pinned call controls. Apps grid has 10 dp header clearance. See docs/RELEASE-0.100.37.md.

- Screenshot refinements: keep flexible centered number/action regions around the non-scrolling Phone keypad. Clock highlighted surfaces use shared sentBrush/sentInk and honor gradient-off. Paint full-page system-bar backplates over wallpaper; paint embedded Camera black before horizontal safe insets. See docs/PHONE-CLOCK-INSET-REFINEMENTS.md.

- Embedded Camera reports its immersive state to the home shell. Landscape live capture hides HomeNavigation and removes outer safe-drawing/IME padding and system-bar backplates; portrait/Captures restore them. Keep black camera coverage and camera-local safe padding for floating controls. See docs/CAMERA-LANDSCAPE-FULLSCREEN.md.

- Phone incoming presentation is notification-driven; do not reintroduce the Phone workspace auto-launch. Suppress the ringing notification only while the owner-tracked call activity is resumed. Live-call channels have no badge and ongoing calls are quiet. Treat callback DISCONNECTED as terminal immediately, retain raw calls only for removal/missed-call details, clear on unbind, and prevent late callbacks/older service instances from restoring live state. Preserve other active calls and separate missed-call notifications. See docs/PHONE-CALL-LIFECYCLE.md.

- SMS errors must use the actual staged part count, never text length, to mention other parts. Preserve raw diagnostic codes and distinguish network result17 from length/encoding failures. Legacy error display formatting must not mutate receipts or mark a failed send successful. Keep confirmed retries and monotonic late-success recovery; do not automatically resend ambiguous failures. See docs/SMS-NETWORK-ERROR.md.

- Review C01–C05: unsupported/early system media commands must not fail playback; guest controls remain strict. Alert once per newly ringing call, not once per notification ID. Call UI/actions share callback state and delayed outgoing screens open only once. MMS recovery retains its previous deadline until replacement commits, isolates each job failure and backs off journal failures at least 30 seconds. See docs/APP-REVIEW-2026-11-02.md.

- Android check cache recovery is limited to one recognized generated-transform metadata failure inside GRADLE_USER_HOME, preserving the original total timeout and failure log. Never retry a compiler/test failure or clear dependency/user data. The quarantine helper rejects other roots and symlink targets.

- Follow-up D01–D05: Cloud account modifications merge against fresh encrypted disk state under a shared lock; once local commit begins, publish its acknowledgement despite cancellation. Never extend NonCancellable to network transfers. Calendar exports use fresh disk snapshots; snoozes have stable replay-safe child identity with legacy timestamp compatibility; timed reminder selection uses device display day while recurrence identity keeps the original date. See docs/APP-REVIEW-2026-11-02-SECOND.md.

- Cloud enrollment offers pCloud (US/EU) and Koofr with free-plan/signup help and plain storage wording. Keep the pCloud/Koofr connection and sharing flows. Messages/Groups paperclip replaces contact-card selection with Cloud Files sharing: create only after confirmation, validate provider links, retain password-protected link details, review before adding to the draft, never send automatically. Sharing APIs use fixed provider HTTPS origins and never forward account credentials to returned public links. See docs/EMAIL-CLOUD-SHARING.md.

- Files simplification: automatic media goes directly into Images/Audio/Video. Retain IDs, custom/protected folders and Trash during consolidation. Typed attachment requests start in the matching root. Keep the native list/thumbnail browser and pencil menus on mutable entries for both OnKlav and Android Files, fresh grants and bounded previews/operations. See docs/FILES-SIMPLIFICATION.md.

- Game entries resolve only the selected title's saves/friend sessions; another title's paused save must never block or redirect launch. Preserve the original Choose your opponent → contact/number → side → automatic connection flow, with durable pending cancellation and fresh-window Try Again. Hold-to-reverify is independent of match creation. See docs/GAME-START-AND-FRIEND-DIALOG-REPAIR.md.

- Naval combat commits model state before queued visual presentation. Preserve original naval cues on the shared Effects bus, silent paused arrivals, automatic defending-fleet view, proof-only enemy wreck geometry and backward-readable schema1 wreck groups. Placed top-down ships leave the unboxed deployment dock. See docs/NAVAL-COMBAT-POLISH.md.

- Cats vs Dogs solo/friend play routes through cat_dog/main.gd with the shared F0–F6 model journal and canonical SDK chrome/dialogs. Commit before animation; keep paused arrivals silent, the tactical AI bounded, the result footer allocation stable and JSON numbers normalized during save adoption/replay. Preserve prior solo boards and friend tapes. See docs/CATS-DOGS-POLISH.md.

- Blackjack: preserve the shared solo/friend scene, canonical controls and standard New/Resume; classic solo naturals/soft17 dealer policy, legacy snapshot comparison rules and unchanged H/S/N ten-hand friend protocol. Commit before presentation, hide the correct opponent card, queue paused arrivals silently, and restore without replayed cues. See docs/BLACKJACK-POLISH.md.

- Crash Test Panda: single player only with complete player then rival volleys at prebuilt independently damaged targets. No building or new friend matches. Retain fixed-step bounded physics, canonical SDK controls/audio/profile and validated legacy solo-save migration. See docs/PANDA-SOLO-REBOOT.md.

- Shared game/SDK loader uses logo purple #603D77 via loading.css/loading.py and the themed export template; keep JS/WASM unchanged for theme-only updates and record the template hash. Naval preserves neutral utility controls, orange Deploy/Fire, separate above-board unboxed reports, readable outcome holds, low shell flight/air cue and its shared-bus score. See docs/NAVAL-PRESENTATION-REFINEMENT.md.

- Air Hockey: capture puck position/velocity/pending contact before manually freezing; paused saves must use captured motion, and resume applies it at the physics boundary. Retain stalled legacy-rally recovery, bounded responsive touch, render interpolation and unboxed feedback. Shared temporary controls preserve prior pause intent; host probes cannot cancel Done. See docs/AIR-HOCKEY-FINAL-POLISH.md.

- Final game refinement: preserve unboxed Cats feedback, shared audio safeguards, equal catalog cards/persistent scrollbar and explicit named game recovery. Panda now follows the solo reboot rather than the retired build timer/rack rules. See docs/PANDA-SOLO-REBOOT.md.

- Neon Dominos: included solo-only game, first to100 versus your rival. Keep the28-tile partition/validated draw/pass/award model, own-hand/public-chain AI, commit-before-animation saves, readable two-end play, original art/audio and canonical controls. Exclude it from friend shortcuts/tokens. See docs/NEON-DOMINOS.md.

- Dominos refinement: preserve the all-visible wrapping rack (maximum21), automatically fitted readable horizontal chain, one-tap unambiguous play and explicit two-end selection, drag cancel on suspension, fresh-draw gold marker and optional schema1-compatible UI hint. Do not restore scroll/zoom/duplicate-footer clutter. Run dominos/layout_tests.gd as well as model checks.

- Panda revision3/pt3 is historical: keep its model/solver only for old solo-save import and regression coverage. New gameplay and rare top-quarter birds follow docs/PANDA-SOLO-REBOOT.md.

- Game saves: host cartridge storage by game/publisher, solo and match-ID friend slots, separate data.* profile slots, atomic acknowledged commit/previous backup and stale-writer leases. Standard Resume/New/Cancel and confirmed New retain old data until new initialization/save succeed. Only one renderer runs; many games may have saves. Never restore a global paused-game lock. Preserve original legacy imports and confirmed friend New/Quit; see docs/GAME-SAVES.md.

- Default game loader: preserve logo-purple full bleed, transparent established white mark, flight/overshoot/settle, Games letter entrance, real-byte visible progress, accessible error/reduced-motion states and short cached-preference-aware cue. Engine.start gating must prevent game time behind the decorative hold. Honor explicit studio PNG artwork through gamekit export. Keep shared JS/WASM bytes unchanged for shell-only changes and record template hashes. See docs/GAME-LOADER-POLISH.md.

- Current UI/game refinements: use background-free header X controls; direct callback-driven Speaker On/Off without route dialog; compact green solo Start. Preserve readable Cats turn holds, bounded/reduced-motion neon effects and rival wording, and Naval selected-disabled Fleet styles. See docs/UI-GAME-REFINEMENTS.md.

- Game entry: Games always opens to its list; only explicit Play invokes the native preflight before any WebView/loader. Consume New/Resume once, one splash, no retained game_start_menu. Foreground stays paused without entry dialogs. Quit disables checkpoints, drains writes, acknowledges a session tombstone and explicitly closes; the host rejects late session saves. SDK packages require code166. See docs/GAME-ENTRY-FLOW.md.

- Panda solo reboot supersedes all earlier Panda build/friend rules: single player only, no building, complete player then rival volleys at identical independently damaged targets, twelve prebuilt courses, rare one-pass birds wholly in the top quarter. Keep validated legacy solo-flight migration, canonical controls/profile/audio, once-only damage/chain/fuse rewards and deterministic save continuation. Run panda/duel_tests.gd along with legacy Panda checks. See docs/PANDA-SOLO-REBOOT.md.

- Panda audit: solo-only entry must ignore retained friend records/intents, with history preserved; structural collisions participate in damage only in the new solver. Keep acknowledged single-flight profile retries through suspension, resize/owner-specific touch cancellation, strict nonmutating flight restore and separate material audio throttles. Run panda/audit_tests.gd and the injected profile-write browser recovery check. See docs/PANDA-AUDIT-2026-11-04.md.

- Combined checkpoint: preserve the Panda solo reboot/audit and the audited compact Video Edit workspace together. Video tools keep stable preview bounds; short windows expose Timeline in a dialog; photo thumbnails honor EXIF orientation; late preview callbacks must belong to the current player. See docs/VIDEO-PANDA-MERGE.md and docs/VIDEO-EDIT-COMPACT-REBOOT.md.

- Latest APK: 0.100.58-rc1/code167 quick ARM64, merged Panda reboot/audit and audited Video Edit. Keep original signer and package. See docs/RELEASE-0.100.58.md and docs/apk58-verification.json.

- Video Edit footer repair supersedes the permanent quick-tool rail: only four category buttons at home, popup tool menus, one shared timeline/inspector footer, no separate idle inspector allocation. Keep stable tool-independent preview bounds and More > Timeline while editing. See docs/VIDEO-FOOTER-REPAIR.md.

- Latest APK: 0.100.59-rc1/code168 quick ARM64, corrected Video Edit footer/portrait preview plus the merged Panda reboot/audit. Keep original signer and package. See docs/RELEASE-0.100.59.md and docs/apk59-verification.json.

- Panda volley/pacing revision: finish the player shot bank before the rival; include bonus shots, skip cleared targets, preserve committed old flights. Use monotonic bounded flight timing excluding pause, cached drawing styles and non-stealing collision voices. Run panda/volley_tests.gd as well as existing suites. See docs/PANDA-VOLLEY-PACING.md.

- Latest APK: 0.100.60-rc1/code169 ARM64 with Panda volley/pacing/audio repairs and preserved Video Edit. See docs/RELEASE-0.100.60.md and docs/apk60-verification.json.

- Panda performance/scoreboard revision: new flights use 120 Hz impact-activated tower physics with explicit saved revision/awake state; keep old airborne 240 Hz/legacy shots exact. All visible points use Duel.points (one tenth raw units); retain internal save precision. Zero idle charge, compact result button, simple score cards and shadowed results. Run panda/performance_tests.gd plus prior suites. See docs/PANDA-PERFORMANCE-AND-SCOREBOARD.md.

- Game replay/tournaments supersede older single-board/hand and first-to100 instructions: result buttons restart directly through OnKlavGame.replay (Hockey direct restart); never reopen entry/confirmation/help. Real finger overrides emulated mouse ownership in Hockey. Cats and Dominos are first to three round wins; Blackjack ten hands plus tie-breaks. Retain validated legacy saves, peer ordered N continuation/rematch journals, save acknowledgements and host/guest ownership. Run game_repair_tests.gd, full game checks and the real-touch/result-button browser checks. See docs/GAME-REPLAY-AND-TOURNAMENT-REPAIRS.md.

- Latest APK: 0.100.61-rc1/code170 quick ARM64 includes game replay/tournament/control repairs, Panda performance/scoreboard changes and preserved Video Edit; original package/signer retained. See docs/RELEASE-0.100.61.md and docs/apk61-verification.json.

- Panda course/collapse revision supersedes older20-piece/three-platform bounds: new120 Hz revision3 flights use local connected-group waking, up to40 pieces/4 platforms/45 bodies, complete visible scored-debris drainage and post-blast awake snapshots. Keep the legacy solver24-body bound and old flight rates/geometry. New courses progressively increase complexity; stage0 remains the intro. Run panda/course_tests.gd plus prior suites and real exported-game checks. See docs/PANDA-COURSES-AND-FALLING-BLOCKS.md.

- Latest APK: 0.100.62-rc1/code171 quick ARM64 includes Panda course/collapse revision and all earlier game/Video Edit changes; original package/signer retained. See docs/RELEASE-0.100.62.md and docs/apk62-verification.json.

- Cats AI/touch repair: use isolated 64-bit bitboards (never Vector2i for49 bits), deterministic4096-node iterative search with mandatory tactics independent of budget; never mutate the live board. Touch release commits once and clears preview before emission; real touch owns over emulated mouse, secondary fingers cannot steal it, idle hover cannot resurrect a preview, and suspension/lock/resize/cancel clear ownership. Run cat_dog/input_ai_tests.gd and exported check_cats_input_ai_browser.py. See docs/CATS-TACTICS-AND-TOUCH-REPAIR.md.

- Music Maker authoring is retired. Preserve the included game soundtracks, the Music bus, and the Game SDK playback and bundle APIs. See developer/games/GAME-MUSIC.md.
