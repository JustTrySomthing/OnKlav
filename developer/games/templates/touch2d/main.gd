extends Control
var score := 0
var label: Label
func _ready() -> void:
	var box := VBoxContainer.new()
	box.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	box.offset_left = 24; box.offset_right = -24; box.offset_top = 16
	box.add_theme_constant_override("separation", 24)
	add_child(box)
	label = Label.new(); label.add_theme_font_size_override("font_size", 32); box.add_child(label)
	var target := Button.new(); target.text = "Tap to score"; target.custom_minimum_size.y = 100; box.add_child(target)
	target.pressed.connect(func(): score = mini(score + 1, 1000000); render(); OnKlavGame.checkpoint())
	render()
	OnKlavGame.attach_session(self, snapshot, restore, new_game, "Tap the target to score. Use the shared top row to pause, save and return, or quit.")
func render() -> void: label.text = "Score: %d" % score
func snapshot() -> Dictionary: return {"score": score}
func restore(state: Dictionary) -> bool:
	var raw: Variant = state.get("score")
	if not (raw is int or raw is float) or not is_finite(float(raw)) or raw != floor(raw) or raw < 0 or raw > 1000000: return false
	score = int(raw); render(); return true
func new_game() -> void: score = 0; render()
