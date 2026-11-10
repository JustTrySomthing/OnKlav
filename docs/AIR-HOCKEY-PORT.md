# Air Hockey — trusted bundled solo port, 0.43.0

The original `ah2` single-player game is now rewritten in Godot 4.5.1 / GDScript.
It replaces the first-to-seven engine exercise. Godot owns rigid-body collision,
continuous collision detection and rail response; game rules and presentation
are new GDScript. No original Java implementation or embedded Inner Circle app
is included. This is not the completed friend-game or external game SDK port.

## Production path

OnKlav Games / Settings trusted catalog -> Kotlin `GameActivity` (`hockey`) ->
embedded `onklav-games.pck` -> `main.tscn` / `main.gd` ->
`air_hockey/main.gd` -> match model, Godot `SubViewport`/`World2D` rink and
`RigidBody2D` puck. The existing non-exported engine activity remains inside the
one OnKlav APK, in the host-owned `:engine` process. It shares the host UID and
is not an untrusted-code sandbox. No separate game APK or executable external
`.onklav-g` package was produced.

## Reference behavior implemented

Read-only Inner Circle 2.2.85 references: HockeyActivity, HockeyEngine,
HockeyView, HockeyScorebar, HockeySounds, GameChrome, GameControlButton,
GamePause, GameHelp and GameSocial. Their implementation is not shipped.

- Ten goals total, higher score wins; 5–5 is a draw. Explicit Serve between
  goals, no automatic re-serve or eleventh round.
- Horizontal lower-half paddle movement, first-finger ownership, keyboard
  alternatives; original defense lines, goal mouth and normalized radii.
- Godot physics at 120 Hz, bounded puck steering, delayed imperfect opponent
  observation/prediction, speed and acceleration limits. Paddle teleports and
  re-serves synchronize through `_integrate_forces` rather than fighting the
  physics server's last transform.
- Original dark/cyan rink, blue/coral players, grid/center markings, bounded
  puck trail, compact 48-unit toolbar, 58-unit scorebar, two-line round counter,
  manual 172×44 green pulsing Serve control, help/pause/quit/restart and results.
  Layout uses a 432-unit logical UI fitted to the host viewport.
- Generated short contact/serve/result tones, mute control saved privately;
  pause/background stop audio. No claim of handset audio/voice parity.
- Pause freezes physics; background/focus loss requires explicit Resume.
  The Android Back notification opens quit confirmation, rather than relying
  on Godot's automatic back-exit. Modal content stays through right-side
  420 ms entry / 360 ms return motion.
- One bounded private current-match JSON checkpoint, written on serve, goal,
  pause and every four active seconds. Resume starts paused. Completed or
  explicitly quit matches remove the checkpoint; restart replaces it. No match
  history. An existing Air Hockey match blocks another game request with
  Resume / Cancel. Corrupt/unsupported saves require explicit discard or Exit.
  Failed reads/writes/removals are surfaced, not counted as success.

## Verification and limits

72 new Godot checks pass, including ten-goal/draw/outcome rules, atomic model
validation, bounded checkpoint I/O, invalid-save retention, actual Godot rail
rebounds, goal mouth/one-score gating, paddle steering, freeze/resume,
first-finger ownership, production screen Serve/pause/restart/result and
single-current-match routing. Twelve unchanged exercise rule checks pass.
The exported production PCK boots the actual Air Hockey screen, model and
checkpoint successfully with Godot 4.5.1. Final logs contain no script errors
or teardown leak warnings. Input/test scripts are excluded from the PCK.

The first run found seven failed physics checks: direct body teleports were
being overwritten by synchronization, and a pause assertion sampled before
its in-flight freeze boundary. Teleports now apply inside the integrator;
the pause assertion settles the in-flight tick, then verifies no movement.
A shutdown-only WAV warning was resolved by stopping/detaching streams on
screen exit and letting the audio thread drain for 100 ms in the test harness.
This is bounded functional testing, not a phone or performance claim.

Remaining: handset rendering/touch/audio/Back/process-kill acceptance and
pixel/large-font/accessibility parity; original fairness/balance acceptance;
friend invitation/verification/status, turn/exchange protocol, timeout/recovery,
chat/return integration and real two-phone play. Other original games and the
untrusted package/permission boundary remain unfinished. MMS/default-role
remain disabled. No APK, Android compile or mobile Rust rebuild in this item.
