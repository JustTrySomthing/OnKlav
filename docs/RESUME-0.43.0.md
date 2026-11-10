# OnKlav 0.43.0 — Air Hockey solo game continuation

The next bounded task replaces the Air Hockey engine exercise with the original
`ah2` solo game rewritten in GDScript on Godot's physics engine. Ten total goals,
manual Serve, draw/result rules, horizontal control, imperfect AI, reference
rink/scorebar/toolbar, pause/help/quit/restart, audio and one current saved match
are implemented. See AIR-HOCKEY-PORT.md for production paths and limits.

72 new Air Hockey checks +12 existing game-rule checks pass. The exported
production PCK boots the new screen/model/checkpoint. `games-provenance.json`
binds the PCK to current input hashes. `scripts/build.py` now runs the new checks
before future APK exports. Core/JNI and app SDK inputs match their unchanged
proofs; prior 0.42 test evidence is retained rather than counted as a fresh run.
No APK or mobile native build was made; the last verified APK is 0.42/code49.
0.43 source version is code50, reserved for the next requested batch build.

Task 15 is partial: solo implemented and engine-checked, handset and friend play
not accepted. Group game selection alone remains incomplete. The next bounded
work is Air Hockey friend-session protocol/host bridge and invitation/status
routing against the original; keep delivery disabled pending actual carrier
acceptance. Do not call first-to-seven exercises a port, enable untrusted game
execution, enable MMS/default role, or claim the complete migration is done.
No per-game APK. Use bounded checks, update progress, and keep the same source,
SDK and checklist file identities when saving.

Reproduce without Android tools:

```sh
GODOT_BIN=/path/to/Godot_v4.5.1-stable_linux.x86_64
"$GODOT_BIN" --headless --path games/engine-checks --fixed-fps 120 --script res://air_hockey/tests.gd
"$GODOT_BIN" --headless --path games/engine-checks --script res://rules_tests.gd
"$GODOT_BIN" --headless --path games/engine-checks --export-pack 'Android Pack' "$PWD/android/games/src/main/assets/onklav-games.pck"
python scripts/package_source.py /absolute/delivery/path
```

After changing Godot input files, regenerate games-provenance.json from those
inputs and the freshly tested/exported PCK; package_source refuses stale inputs.
Keep the existing signing backup for a future requested APK. No need to recover
the Android build cache just to work on this game-only checkpoint.
