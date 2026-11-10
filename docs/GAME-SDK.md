# Default Games loader update

The shared SDK/default bundled-game loader now uses the animated purple entrance described in GAME-LOADER-POLISH.md. Game saves and canonical controls remain intact. Pending host source remains0.100.56/code165; no new APK.

# Per-game saves update

Game SDK0.6 and host0.100.56/code165 provide independent per-game/publisher cartridge saves, acknowledged return, permanent data slots and safe New/Resume. See GAME-SAVES.md. Latest delivered APK0.100.55/code164 predates this source. Older notes below are historical.

# Shared loading-bar theme

The default game loader uses OnKlav logo purple #603D77. The shared export template embeds templates/shared/loading.css through loading.py; engine rebuilds preserve it. This is an HTML theme change with unchanged JS/WASM and SDK APIs.

# Bundled reference update: Crash Test Panda

Panda is the first preinstalled game and is now single player only: prebuilt demolition courses, alternating local player/rival shots and no building phases. It demonstrates fixed-step physics, persistent independent targets, profile stars and canonical session/chrome/audio. See PANDA-SOLO-REBOOT.md. Other turn-based games continue to demonstrate intent-only SMS transport; the shared engine contract is unchanged. No new APK in this increment.

# Bundled reference update: Blackjack

Blackjack now uses one polished solo/friend scene, canonical session controls, turn channel and Effects bus. No SDK API or binary contract changed. Preserve classic solo natural rules and compatible equal-player friend rules. See BLACKJACK-POLISH.md.

# Bundled reference update: Cats vs Dogs

Cats vs Dogs now uses one polished solo/friend scene with the canonical helpers, current top bar/dialogs, turn channel and shared audio buses. No SDK API or binary contract changed. See CATS-DOGS-POLISH.md. Latest delivered APK0.100.50/code159 predates this source increment; earlier release notes below are historical.

# Current SDK0.5 / minimum host157

See NAVAL-AND-FRIEND-GAMES.md and developer/games/SMS-TURNS.md. Air Hockey is single only; the other three games offer solo/SMS friend play. Naval Battle uses the rebuilt shared-engine interface. Latest built APK is0.100.47/code156, which predates this work. Earlier entries below are historical.

# Current SDK0.4 / minimum host155

See GAME-CONTROLS-0.4.md and developer/games/GAME-CONTROLS.md. New discards the old match and opens the original catalog start dialog; the duplicate mode chooser is removed. Streamed sound/music and default studio-overridable OnKlav Games splash are supplied. No new APK for this source increment. Earlier descriptions below are history.

# Current Game SDK0.3 / controls v2

The required interaction contract is developer/games/GAME-CONTROLS.md. Host154 source is ready; a new Android APK was not built this increment. See GAME-CONTROLS-V2.md for current verification. Older pause-menu descriptions below are historical.

# Current Game SDK0.3 / controls v2

The required interaction contract is developer/games/GAME-CONTROLS.md. Host154 source is ready; a new Android APK was not built this increment. See GAME-CONTROLS-V2.md for current verification. Older pause-menu descriptions below are historical.

# Game SDK0.2 / standard fullscreen controls and sessions

Host0.100.44/code153 supplies immersive Android navigation, a title-free common game row and explicit save/return/resume/new-game/quit. See developer/games/GAME-CONTROLS.md and docs/GAME-CHROME.md. The corrected Godot4.7.2 engine remains shared once. Earlier versions below are historical.

# Game SDK0.1.1 / shared-engine cleanup update

Host0.100.43/code152 includes the Air Hockey solo remake and one corrected shared Godot4.7.2 Web runtime. The cleanup patch releases completed worker groups in single-thread builds. SDK0.1.1 supplies the same export template as developer tooling, and `upgrade PROJECT` preserves source/key/identity with rollback and backup. See developer/games/README.md and docs/godot-web-engine.json. Physical-phone qualification remains open. Earlier foundation scope below is historical.

# Shared Godot game platform and Game SDK0.1

Current source moves every bundled and developer-made game to one shared Godot4.7.2 stable single-threaded WebGL2/Compatibility runtime. The native Godot4.5.1 AAR/activity/legacy PCK launch path is removed. The source asset inventory, build planner and APK verifier now require the shared JS/WASM and bundled web content and reject the retired native engine. No APK was requested/built for this work; delivered APK151 predates it.

Developers can create2D,3D and deterministic local turn-based projects with developer/games/gamekit.py, test/import/boot them, export or preview them in a browser, build signed .onklav-g packages and share them directly. The engine is supplied once by OnKlav and its exact hashes are enforced; game packages omit those binaries. The SDK contains guides, lifecycle/save helpers, templates, signing/verification tools, package-format/performance documentation and current Godot notices. No developer account or owner approval exists.

Users choose Install in the Games manager after selecting a package through OnKlav Files and reviewing its metadata/publisher. Installed games join Games. Save to share exports the original signed file through Files. Updates require the same publisher and greater version_code, and commit only after complete content verification. Removal retains saved progress and update identity. Signature checks use ECDSA-P256/SHA256 supported on existing Android API29+ targets, not an Android13-only provider.

Game JavaScript/WebAssembly runs in the WebView renderer sandbox in a separate :games process, with a separate WebView profile and a game-ID/publisher HTTPS origin. No JavaScript/Android bridge exists. File/content access, file selection/download, device permissions, external navigation and ordinary network requests are denied; a document-start policy removes peer/socket APIs before game code runs. Unsupported old WebViews fail closed with an update message. Service worker fallback network is blocked. Only GET resources from that current game origin may be served, from the held verified ZIP or shared engine. No archive extraction into host paths occurs. This is containment, not an independent security audit or a guarantee against all browser/OS flaws.

A single game activity asks before replacing the current game. Host controls remain available outside game content for pause/close and renderer failure/unresponsiveness. The game surface pauses on backgrounding and does not automatically resume. Game templates checkpoint bounded, versioned private state and distinguish missing/corrupt/failed saves. Native save originals are retained; native-to-web migration is still unfinished. WebView/OS quotas and async persistent storage require real-device recovery testing.

Verified scope is recorded in game-sdk-verification.json and evidence/game-sdk. Browser tests use the actual4.7.2 WebAssembly engine and matching resource/security headers in Chromium, not a mock renderer. Desktop browser success does not prove Android WebView interception, handset audio/touch/performance or lifecycle acceptance. Screenshots are internal review evidence only, not distributed personal files.

Remaining: physical Samsung/WebView performance/audio/touch/process-loss acceptance; native Windows/macOS authoring qualification; independent security/release qualification; native-save import; original-game remakes beyond the existing Air Hockey solo port; network/SMS friend sessions and privileged Files/TTS/account brokers for games. The first public game surface is offline/local multiplayer. No completed remake/friend-game or mobile smoothness claim is made.

## Motion and temporary sheets

Use OnKlavGame to pause the SceneTree; it retains native rigid-body velocity. Manually setting RigidBody2D.freeze can zero velocity. Custom lifecycles must capture transform, linear/angular velocity and pending contact before freezing, restore at the physics synchronization boundary, and checkpoint captured motion while paused. Never infer a healthy rally solely from a PLAYING flag: verify actual position and velocity after Sound/Settings Done, Pause and save/reload.

GameChrome preserves the prior running/paused intent of Sound and Settings sheets. Ignore unknown host probes without cancelling that intent. Background/return interrupts remain paused until explicit resume. Run the portable tests.gd regression: it checks real rigid-body motion through tree pause and temporary dialogs. Keep canonical helpers or merge recognized updates through gamekit upgrade.
