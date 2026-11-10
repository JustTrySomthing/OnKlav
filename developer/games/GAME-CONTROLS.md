# Current entry contract — host166

Games opens to its list. Only explicit Play presents Start or Resume/New Game/Cancel before any engine load. Start/Resume shows the loader once and then enters play directly. Foregrounding a running game stays paused without opening an entry dialog. New retains the old checkpoint until the replacement saves. Confirmed Quit drains writes, tombstones only that session, awaits acknowledgement and closes; it must disable later session checkpoints. Permanent profiles survive. Shared helpers handle these rules; do not create another launch screen or retained catalog menu request.

# Animated Games entrance

The default purple Games loader now uses the background-free white logo, a forward-flight/overshoot/settle entrance, animated lettering, visible real progress and a short preference-aware sound cue. Reduced motion and explicit studio PNG splash art are supported. See [LOADER.md](LOADER.md).

# Required game controls — SDK0.6.1 / minimum host166

All bundled games and templates use the same Godot4.7.2 shared engine and canonical GameChrome/OnKlavGame helpers. No developer account or owner approval is required. The current entry contract requires host0.100.57/code166. See [SAVES.md](SAVES.md).

## One control row

Use one title-free row: background-free X, return arrow, Pause/Play on the left; Help, Sound, Settings on the right. The shared gear is a real gear; icon proportions and 48px touch targets are consistent. Android status/navigation and OnKlav navigation stay hidden during play; Android transient navigation remains available by edge swipe.

- Manual Pause checkpoints and freezes the game, changes the icon to Play, displays **Game paused** in the center, and gently pulses that Play button over 2.4 seconds. Play clears the label/pulse and resumes directly. Reduce motion disables the pulse. Neither action opens a dialog.
- Sound and Settings pause silently, without the manual pause label or any underlying pause dialog. Done resumes gameplay directly. Mute and game volume control Master; music uses Music and effects use Effects. The default soundtrack and interaction sound are real audio. Authors may replace the soundtrack while retaining these buses/preferences.
- Help is a centered shared dialog, never a slide-out page. Done resumes directly.
- X asks exactly **Are you sure you want to quit? You will lose your progress.** One horizontal row has green **Resume** left and red **Quit** right. Quit clears the current save and exits. Resume returns directly to play.
- The toolbar return arrow and Android Back pause, checkpoint, and return to OnKlav without a dialog. System Home/backgrounding also pauses/checkpoints. Do not auto-resume on foregrounding.

## Saved game and original start dialog

Clicking Play on the same saved game opens **Would you like to resume the current game or start a new game?** with **New** left and **Resume** right. Resume restores that game's saved state. It must not redirect to another game's saved session.

**New asks for confirmation**, then returns to the original game-specific catalog start dialog. Keep the old session until the new session initializes and saves successfully. Cancel preserves that save. No extra in-game mode selector is added. The native fresh-start flag is consumed once. Each game's automatic slot is independent; another title's paused game never blocks this title.

Register only implemented modes. Single-only packages show a compact green Start button (48dp minimum touch height, 144–204dp width) and Single Player Only. Packages declaring single + turn_sms offer Play a Friend and Single Player through the same catalog and Messages magic-wand invitation flow. See SMS-TURNS.md. An accepted friend session selects its actual friend mode without adding a mode chooser. Return preserves the match; New/Quit terminates it. Remote updates may be journaled while paused, but must not advance animation or local simulation behind a modal.

## Integration

Create the scene first, then attach:

```gdscript
OnKlavGame.attach_session(self, snapshot, restore, start_single, HELP,
    OnKlavGame.SAVE, {"Single player": start_single})
```

Callbacks are real Callables. `snapshot() -> Dictionary` returns validated JSON within the2MiB slot bound; `restore(data) -> bool` validates before mutation; start callbacks reset model/UI. Call checkpoint after important moves as well as the periodic checkpoint. `_onklav_mode` is reserved and persists a registered mode; legacy flat checkpoints remain readable. Do not add parallel save/pause/exit handlers or another toolbar. Invalid/unreadable saves remain intact until the user explicitly chooses New/discard. Failed clearing must not acknowledge successful New/Quit.

The WebView host is offline, isolated, and exposes no Android objects, credentials or arbitrary files. Host lifecycle messages affect only the current game. JavaScriptBridge booleans are normalized explicitly; do not compare a bridge result directly to GDScript true. Standalone preview filesystem synchronization is asynchronous. Validate storage errors and test real-device process loss. Android host commits require acknowledgement before exit; the shared controller handles this. Clearing app data removes saved progress.

## Branding and author upgrades

The default boot image is the OnKlav logo with **Games** underneath, on a dark backdrop. Godot's default splash is replaced. Developers may supply their studio logo/name by setting `boot_splash/image` to their own PNG and choosing an appropriate background. Preserve the controls and lifecycle independently of branding. Upgrade retains an explicit studio splash configuration, backs up recognized SDK helpers, and refuses to overwrite customized helpers silently.

Use the supplied matching export template. Build/sign/share `.onklav-g` content packages, not APKs or private engines. Increase version_code when publishing changes, preserve your private publisher key, and test Pause/Play, silent options, Help, both Back routes, same-game resume, confirmed New with safe replacement plus original catalog start, Cancel, Quit, audio/mute/music and storage failure. Native Windows/macOS setup and physical Samsung gesture/audio/process-recreation acceptance remain separate from automated checks; see VERIFICATION.json for actual evidence.

## Motion and temporary sheets

Use OnKlavGame to pause the SceneTree; it retains native rigid-body velocity. Manually setting RigidBody2D.freeze can zero velocity. Custom lifecycles must capture transform, linear/angular velocity and pending contact before freezing, restore at the physics synchronization boundary, and checkpoint captured motion while paused. Never infer a healthy rally solely from a PLAYING flag: verify actual position and velocity after Sound/Settings Done, Pause and save/reload.

GameChrome preserves the prior running/paused intent of Sound and Settings sheets. Ignore unknown host probes without cancelling that intent. Background/return interrupts remain paused until explicit resume. Run the portable tests.gd regression: it checks real rigid-body motion through tree pause and temporary dialogs. Keep canonical helpers or merge recognized updates through gamekit upgrade.


## Optional gameplay controls

SDK0.7 adds an opt-in joystick/D-pad, look area and action buttons; see TOUCH-CONTROLS.md. These complement the canonical toolbar. Finished-session Play again must call OnKlavGame.replay() directly, with no new-session confirmation or instructions.
