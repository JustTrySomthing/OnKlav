# Reusable action controls (SDK 0.7)

Create a landscape action game without rebuilding touch input:

```sh
python3 gamekit.py new MyPlatformer --id app.example.platformer --name "My Platformer" --template platformer
python3 gamekit.py new MyArena --id app.example.arena --name "My Arena" --template firstperson
```

Both are single-player starters. Landscape is the default; `--orientation sensor` allows rotation. Existing templates retain portrait defaults. The Android host already honors the signed orientation and immersive fullscreen. The canonical pause, help, audio, settings, save/return and quit row remains available.

## Component API

Copy `touch_controls.gd` from a generated project, or use `gamekit upgrade` to install recognized helpers. Customized helpers are protected against overwrite.

```gdscript
const Touch = preload("res://touch_controls.gd")
var controls = Touch.new()
func _ready():
    controls.configure("firstperson") # or platformer
    add_child(controls)
    controls.action_pressed.connect(on_action)
    controls.look_changed.connect(on_look)
func on_action(action: String):
    if action == "fire": fire()
func on_look(delta: Vector2):
    rotate_camera(delta)
```

Read `input_vector` each physics tick. `is_held("sprint")` supports continuous actions; `action_pressed` supports short taps. Signals also include `movement_changed` and `action_released`. Movement is normalized with a configurable dead zone. Set `movement_style="dpad"` for directional buttons, `floating_stick=false` for a fixed joystick, `horizontal_only=true` for side scrolling, and `look_sensitivity` for camera sensitivity. Call `reset_input()` on custom interruptions. Setting `enabled=false` clears ownership and ignores new input.

Controls fill their parent content rectangle. Place them below canonical chrome; layout adapts to available width and height. Left movement, right look, and separate action buttons retain independent finger ownership. Cancellation, release outside the control, resize, hiding, focus loss and shared session suspension clear held input. Synthetic mouse events following touch cannot double-trigger actions. Mouse dragging provides desktop testing. Labels are drawn from the action names; games can theme the drawing functions without changing input ownership.

## Starter behavior

- Platformer: actual collision bodies, gravity, buffered jump, coyote time, dash, elevated platforms, goal and direct replay. Keyboard A/D or arrows, Space, Shift.
- First person: actual 3D collision, movement, pitch-limited look, raycast fire, target removal, jumping, sprinting and a nearby usable door. Keyboard WASD, Space, Shift, Ctrl, E; drag to look.
- Both validate saves before mutation and clear touch state on pause. They use the existing acknowledged session/save bridge and shared engine; they add no permissions or network access.

These are minimal playable foundations, not finished games. Replace their geometry, art, enemies and progression. Keep rendering and physics bounded; test frame pacing, multitouch, safe areas, audio and interruption recovery on physical Android devices before release. Desktop/Web checks do not establish phone performance.

## Focused regressions

Run the Python tooling tests from the SDK directory. Generate each starter, then run Godot headlessly with `--path PROJECT --script /absolute/path/tests/action_templates_tests.gd`. Run `tests/touch_controls_tests.gd` against either generated project. `gamekit check`, `export`, `build`, and `verify` remain the package pipeline.


SDK0.8 adds `topdown` and `arena` presets, independent `aim_vector`/`aim_changed`, configurable size/opacity/handedness/sensitivity/D-pad, validated `settings()`/`apply_settings()` persistence and an optional `show_settings(parent)` sheet. See DEVELOPER-START-HERE.md for examples and extension boundaries.
