extends Control
var cube: MeshInstance3D
func _ready() -> void:
	var view := SubViewportContainer.new(); view.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); view.stretch = true; add_child(view)
	var world := SubViewport.new(); world.size = Vector2i(600, 1000); world.own_world_3d = true; view.add_child(world)
	var camera := Camera3D.new(); camera.position = Vector3(0, 1, 4); world.add_child(camera)
	cube = MeshInstance3D.new(); cube.mesh = BoxMesh.new(); world.add_child(cube)
	var light := DirectionalLight3D.new(); light.rotation_degrees = Vector3(-40, -30, 0); world.add_child(light)
	var title := Label.new(); title.position = Vector2(24, 16); title.text = "Touch or drag to rotate"; title.add_theme_font_size_override("font_size", 26); add_child(title)
	OnKlavGame.attach_session(self, snapshot, restore, new_game, "Drag to rotate the cube. Its angle is saved when you leave.")
func _process(delta: float) -> void:
	if is_instance_valid(OnKlavGame.chrome) and not OnKlavGame.chrome.reduce_motion: cube.rotate_y(delta * .4)
func _input(event: InputEvent) -> void:
	if OnKlavGame.paused: return
	if event is InputEventScreenDrag: cube.rotate_y(event.relative.x * .01)
	elif event is InputEventMouseMotion and event.button_mask & MOUSE_BUTTON_MASK_LEFT: cube.rotate_y(event.relative.x * .01)
func snapshot() -> Dictionary: return {"angle": fposmod(cube.rotation.y, TAU)}
func restore(state: Dictionary) -> bool:
	var angle: Variant = state.get("angle")
	if not (angle is float or angle is int) or not is_finite(float(angle)) or angle < 0 or angle > TAU: return false
	cube.rotation.y = float(angle); return true
func new_game() -> void: cube.rotation = Vector3.ZERO
