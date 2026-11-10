# Animated Games entrance

The default purple Games loader now uses the background-free white logo, a forward-flight/overshoot/settle entrance, animated lettering, visible real progress and a short preference-aware sound cue. Reduced motion and explicit studio PNG splash art are supported. See [LOADER.md](LOADER.md).

# OnKlav Game SDK 0.14.2
 
See [MOBILE-AND-DIAGNOSTICS.md](MOBILE-AND-DIAGNOSTICS.md) for migration, responsive controls, and package reports.
 
See [SHARED-GAME-SYSTEMS.md](SHARED-GAME-SYSTEMS.md) for permanent progress, touch gestures, authored level packs and SMS turn recovery.
 
See [ENGINE-COMPATIBILITY.md](ENGINE-COMPATIBILITY.md) for retained engines, package verification and update checks.
 
See [GENRE-STARTERS.md](GENRE-STARTERS.md) for the playable puzzle, lane runner and idle examples.
 
See [RELEASE-READINESS.md](RELEASE-READINESS.md) for the supported game profiles, compatibility rules and release qualification.

Build and share games for OnKlav with one shared Godot4.7.2 WebGL2/Compatibility engine. No developer account or platform-owner approval is required. Developers sign their own games; users decide what to install. Games are .onklav-g files, not separate Android APKs.

This revision requires **host0.100.57/code166** for native entry choices before loading and acknowledged per-game saves. The previously delivered APK0.100.56/code165 predates the entry fix. See [SAVES.md](SAVES.md).

## Setup and first project

Use Python3.11+, install requirements.txt, and install the standard Godot4.7.2 editor (no .NET/C#). A verified matching Web export template is included as developer tooling and copied into generated projects; game packages omit shared engine binaries. Android SDK/Gradle/APK signing tools are unnecessary for game authors.

```sh
python3 gamekit.py new MyGame --id app.yourname.mygame --name "My Game" --template touch2d
python3 gamekit.py check MyGame
python3 gamekit.py run MyGame
python3 gamekit.py preview MyGame
python3 gamekit.py keygen ../my-private-publisher.key
python3 gamekit.py build MyGame --key ../my-private-publisher.key --output MyGame.onklav-g
python3 gamekit.py verify MyGame.onklav-g
```

Templates: touch2d, scene3d, turn, platformer, firstperson, topdown, arena, puzzle, idle, runner, racing, defense, rhythm, match3, merge, sports, builder, breakout, wordquiz, cards, adventure, tactics, campaign, mobile. The action starters include reusable multitouch controls and default to landscape. See [DEVELOPER-START-HERE.md](DEVELOPER-START-HERE.md) for all four action starters and reusable gameplay components. See [TOUCH-CONTROLS.md](TOUCH-CONTROLS.md) for input API details. Open project.godot in Godot to develop. Use `--godot /path/to/Godot` before the command when it is not on PATH. On Windows use `py -3` instead of python3; on macOS select the executable inside Godot.app/Contents/MacOS. Linux was exercised; native Windows/macOS acceptance is still open.

## Required controls, dialogs and mode registration

[GAME-CONTROLS.md](GAME-CONTROLS.md) is the required interaction contract, including exact text, button placement/colors, callback API, mode registration and recovery rules.

- One title-free row: X / Return / Pause-Play on the left; Help / Sound / Settings on the right.
- Pause is a direct state/icon toggle with no dialog.
- Sound and Settings silently pause; Done returns directly to gameplay.
- Quit has one question and exactly two horizontal actions: green Resume left, red Quit right.
- Return/Android Back silently saves and pauses. Games always reopens to the list. Only explicit Play offers Start or Resume/New Game/Cancel for that selected title, before loading.
- Finished-session replay calls `OnKlavGame.replay()` directly, without entry or confirmation screens. Replacing an unfinished session through New opens that game's original start dialog and keeps the old save until the new session saves successfully. Cancel preserves it. Do not advertise an unimplemented friend mode.

Create your scene UI, then call `OnKlavGame.attach_session` with snapshot, validating restore, start callbacks and help. Register actual modes using the optional dictionary. Call checkpoint after important moves; the helper also checkpoints periodically and on return. Reserve `_onklav_*` snapshot keys for the helper. Use the provided helpers instead of rebuilding dialogs or adding another toolbar. Music belongs on the Music bus, effects on Effects; start audio after session attachment. Honor the shared reduced-motion preference.

## Check, export and preview

Check imports resources, runs tests.gd and boots the actual main scene with bounded deadlines. Startup must attach the standard session controller. Build repeats these checks, verifies the exact engine/controls fingerprint, exports, omits shared binaries, signs content with ECDSA-P256/SHA256 and verifies the package. `export MyGame --output WebBuild` creates a standalone Web export. Preview serves an export on localhost and stops with Ctrl+C. Desktop checks do not establish physical-phone smoothness.

## Share, install and update

Send the signed file normally. In a compatible OnKlav host, install through the Games package manager using OnKlav Files; review name/version/publisher first. Games can be saved to share or removed. Updates keep the original private publisher key and increase version_code. Losing your key prevents normal updates; keep a private backup outside project folders and never share it. Public publisher fingerprints are identities, not endorsements. Removal retains saved progress and update identity.

For an older project, run `upgrade PROJECT`. It backs up metadata/preset/template/recognized SDK helpers and rolls back failed writes, preserving authored game source, ID/version and publisher key. It adds missing reusable helpers, including mobile layout and save migration. It refuses to overwrite customized helpers; merge those explicitly. Archive an existing .onklav-engine-backup before another upgrade. Existing game code must register its real modes and obey the new control contract before distribution; do not assume helper replacement alone validates your gameplay. Increase version_code yourself and run check/build again.

## Boundaries, limits and verification

Games run in a separate OnKlav process and sandboxed WebView renderer, with a per-game/publisher origin. File/content access, external navigation, cookies, device permission requests and network are blocked. There is no Android object/credential/path bridge. Local controls can only pause/return/close their own activity or set its keep-awake flag. Custom HTML has the same restrictions. An optional host-owned SMS turn broker is available only for explicitly accepted friend matches; see [SMS-TURNS.md](SMS-TURNS.md). Internet multiplayer, Files/TTS/account game brokers, C#/GDExtensions and native-save import are not implemented. Do not advertise unsupported APIs.

Package bounds:64MiB compressed,128MiB expanded,64MiB/member,128 entries;100 retained identities and1GiB installed storage. Host saves are private per game/publisher, acknowledged and backed by a previous checkpoint; standalone previews use origin storage. Both have limits and clearing app data removes progress. Report errors and preserve recoverable originals. See [FORMAT.md](FORMAT.md), [PERFORMANCE.md](PERFORMANCE.md) and VERIFICATION.json. Run `python3 -m unittest discover -s tests -v` for tooling tests and exercise every declared gameplay mode on real devices before sharing.

The bundled games use the same engine. Air Hockey is single player only. Naval Battle has a new touch fleet-placement/targeting interface, solo computer opponent and private-fleet SMS turns. Cats vs Dogs and Blackjack retain solo play and implement SMS friend play. Physical Samsung gesture/audio/performance/process-loss acceptance and independent security/release qualification remain open. Godot/toolchain notices are in licenses/. The included engine template is tooling, not an extra runtime in your game. Your game code/art licenses are your responsibility; this kit does not invent a platform-owner license.


## Choose supported play modes
 
The supported choices are single-player and single-player plus SMS-based friend turns. Pass-and-play and real-time multiplayer are outside the SDK profile.

The default `new` project is single player only: the host shows one wide Start button with Single Player Only underneath. For turn-based friend play:

```sh
python3 gamekit.py new MyTurns --id app.yourname.myturns --name "My Turns" --template turn --play-mode turn_sms
```

This signs `play_modes=["single","turn_sms"]` and requires `profile="turn_based"`. The host offers Play a Friend and Single Player. Implement both advertised modes, use the shared TurnChannel, validate each move, and checkpoint the model plus channel cursors together. The included turn template demonstrates solo computer play and SMS friend play. Do not use SMS for hockey, fast action, continuous physics or frequent position updates. Developers choose the supported modes; users choose which signed games to install, without platform-owner approval.

## Included Panda reference

Crash Test Panda is single player only and demonstrates prebuilt demolition puzzles, three-shot local player/rival volleys, fixed-step physics, exact in-flight checkpoints, profile stars and shared audio/session controls. See ../../docs/PANDA-SOLO-REBOOT.md. For friend games, use Naval Battle or Cats vs Dogs as intent-only SMS examples; never stream physics positions through SMS. The Panda reboot adds no SDK API or engine binary contract.

## Motion and temporary sheets

Use OnKlavGame to pause the SceneTree; it retains native rigid-body velocity. Manually setting RigidBody2D.freeze can zero velocity. Custom lifecycles must capture transform, linear/angular velocity and pending contact before freezing, restore at the physics synchronization boundary, and checkpoint captured motion while paused. Never infer a healthy rally solely from a PLAYING flag: verify actual position and velocity after Sound/Settings Done, Pause and save/reload.

GameChrome preserves the prior running/paused intent of Sound and Settings sheets. Ignore unknown host probes without cancelling that intent. Background/return interrupts remain paused until explicit resume. Run the portable tests.gd regression: it checks real rigid-body motion through tree pause and temporary dialogs. Keep canonical helpers or merge recognized updates through gamekit upgrade.

Machine-readable entry rules are in entry-protocol.json. onklav-controls.json is an immutable legacy engine fingerprint retained for existing signed-package compatibility; the current entry contract supersedes its old new_game_policy descriptor. No engine JS/WASM binary changed.

## Game music

Create or source your own tracks and bundle them with your game. The SDK handles playback, looping and the Music bus. See [Game music](GAME-MUSIC.md).
