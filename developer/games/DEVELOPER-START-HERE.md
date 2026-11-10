# Build a single-player action game (SDK 0.14.2)

The kit supports 2D and lightweight 3D through the shared Godot 4.7.2 Compatibility/Web engine. Start from working code, keep the shared toolbar, and replace the sample geometry/art and rules. These are small foundations, not complete commercial games or a guarantee of performance for arbitrary 3D scenes.

## Choose a starter

| Template | Working example | First extension points |
| --- | --- | --- |
| `platformer` | Collision, gravity, buffered/coyote jump, dash, moving platform, checkpoint flag, patrol enemy, hazard and goal/replay | `platform`, `_physics_process`, `start_new`, `snapshot`/`restore` |
| `firstperson` | Collision, move/look/jump/sprint, ray weapons, pursuing enemies, healing pickup, usable door, cleared-room transition | `block`, `spawn_enemies`, `fire`, `interact` |
| `topdown` | Follow camera, collision, melee, pursuers, potion/gold inventory, guide dialogue/quest flag, room exit | `body`, `action`, `interact`, `spawn_enemies` |
| `arena` | Independent move/aim sticks, ranged attacks, dash, loot, health, three escalating waves and completion/replay | `attack`, `_physics_process`, `state.wave` |

The arena starter is a top-down dungeon action foundation. A 3D/isometric Gauntlet-style game can reuse its state/wave rules with the `firstperson` starter's 3D bodies and a separate overhead Camera3D. No prebuilt 3D isometric campaign is included.

```sh
python3 -m pip install -r requirements.txt
python3 gamekit.py new MyDungeon --id app.yourstudio.dungeon --name "My Dungeon" --template arena
python3 gamekit.py check MyDungeon
python3 gamekit.py preview MyDungeon
python3 gamekit.py keygen ../my-private-publisher.key
python3 gamekit.py build MyDungeon --key ../my-private-publisher.key --output MyDungeon.onklav-g
python3 gamekit.py verify MyDungeon.onklav-g
```

Install the exact Godot version stated above; use `--godot /path/to/Godot` before the command or set GODOT_BIN. No Android SDK or separate APK is needed. Install the signed game using OnKlav Files through Games. Keep the same private publisher key outside source for updates, increase `version_code`, and verify the resulting package. Never distribute your key. The SDK archive includes signed starter examples under `examples/`; their test publisher identity is not your studio identity. Generate your own key for your games.

## Controls and accessibility

Landscape is the action-template default. Use `--orientation sensor` when creating a game that supports rotation. The host hides Android system bars, which users can reveal with a swipe. Respect canonical content insets and keep the toolbar available. Place touch controls inside your session content rather than over the toolbar. Do not lock the whole host app to landscape.

`touch_controls.gd` supports joystick/D-pad, floating or fixed movement, simultaneous actions/look, and independent twin-stick aim. `controls.settings()` returns size, opacity, handedness, sensitivity and D-pad mode. Validate with `apply_settings`, save this dictionary in your snapshot, and call `show_settings(self)` for the included preference sheet. Position is mirrored by handedness; fully arbitrary drag-and-drop button placement is not implemented. Set custom button/joystick positions by overriding `home`, `aim_home`, and `button_rect` in a subclass. Preserve touch ownership/reset behavior. Do not add another generic pause/help/settings toolbar.

Each starter includes a Controls button. RPG: WASD movement, Space attack, E use/talk or consume a potion away from NPC/exit, I inventory, H potion, Shift dash. Arena uses the right stick to aim/fire independently; Space attacks in the current facing direction. Platformer: A/D/arrows, Space jump, Shift dash. First person: WASD, drag look, Space jump, Shift sprint, Ctrl fire, E use and Q switch weapons. The heavy weapon trades firing speed for damage. Settings, pause, focus loss and resize clear held input. Test hands-on Android multitouch before publication.

## Reusable components

`gameplay_state.gd` is a RefCounted model with health/damage/healing/defeat signal, bounded inventory, quest/world flags, cooldown gates, weapon selection, level/wave/score and checkpoint coordinates. Call `tick(dt)` only during active simulation. `ready_for("attack", .3)` both tests and starts a cooldown. `snapshot()` is JSON-compatible; `valid()` validates without mutation; `restore()` applies only a complete valid model. Inventory IDs and flags are bounded; games define their own meanings. The sample weapon list has two entries; extend validation together when adding more slots.

`enemy_steering.gd` provides collision-aware CharacterBody pursuit in 2D/3D and bounded four-neighbour grid pathfinding. Pursuit alone does not find a way around complex obstacles. For tile rooms, build a blocked-cell dictionary, request `grid_path`, and steer toward successive cell centres. Replan at a bounded rate, not for every enemy every frame. For complex 3D navigation, use Godot navigation regions/agents and bake the map; this kit does not supply baked navigation assets.

## Build levels and persistent worlds

Give every room, enemy and pickup a stable ID. Save room ID, player state, defeated IDs, pickups, quest flags, inventory and relevant cooldowns. Validate the entire snapshot before changing live nodes; do not partially restore a corrupt world. Add schema migrations when changing formats. The examples validate their own bounded lists and call the existing acknowledged host save bridge through OnKlavGame. Use `checkpoint()` after significant changes; handle `save_failed` visibly. Do not create another file/save bridge.

The examples build small rooms procedurally. Replace this with authored `.tscn` scenes or TileMapLayer resources. Keep a persistent model/controller and swap only level content under it; attach the shared session once. Stop/clear touch input before replacing content; defer freeing physics bodies outside physics callbacks. Test completing a room, saving, reopening, restoring enemies/pickups and dying/replaying. Platform checkpoint respawn retains model progress; Play again starts a fresh session directly.

## Performance and release

Use simple collision shapes, cap active enemies/projectiles, pool frequent effects, limit lights/shadows/material variants and texture sizes, and unload unused levels. The examples cap enemy counts (12 top-down, 6 first-person); choose budgets from device measurements. `PERFORMANCE.md` explains the shared runtime limits. Include reduced-motion alternatives and independent audio settings through the shared helper.

Run Python tooling tests plus the portable Godot suites in `tests/`. `gameplay_components_tests.gd` covers state, cooldowns, bounded pathfinding and touch settings. `topdown_templates_tests.gd` exercises actual RPG/arena simulation; `advanced_starters_tests.gd` checks platformer/FPS additions. Run these with `--headless --path GENERATED_PROJECT --script /absolute/path/to/test.gd`. `touch_controls_tests.gd` uses the platformer preset.

Before sharing broadly, test on real Android: first launch/install/update; system bars and safe areas; two/three fingers; left-handed/D-pad/size settings; audio interruption; focus/background/return; save errors and process recreation; death/replay; sustained performance and battery use. Desktop and software-rendered browser checks are not handset acceptance. Windows/macOS setup and independent release/security review remain unqualified.

Third-party notices are under `licenses/`. The project owner must establish the SDK's own redistribution/template-code license before public distribution; this kit does not invent that legal grant. Developers must license their own code, art and music. No Doom or Gauntlet assets are included. Keep all engine/package constraints, permission restrictions and supported modes honest; fast action is single player, not SMS multiplayer.

## Game music

Create or source your own tracks and bundle them with your game. The SDK handles playback, looping and the Music bus. See [Game music](GAME-MUSIC.md).
