extends MarginContainer
## Keep the host's session chrome outside this game-content panel.
signal preferences_changed
var text_scale := 1.0
var reduce_motion := false
var swapped := false
var safe_insets := Vector4.ZERO

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	update_layout()

## Insets are in local UI units, ordered left, top, right, bottom.
## Leave at zero when the host already provides an inset viewport.
func set_safe_insets(insets: Vector4) -> void:
	safe_insets = insets.clamp(Vector4.ZERO, Vector4.ONE * 120)
	update_layout()

func update_layout() -> void:
	for edge in [["left", safe_insets.x], ["top", safe_insets.y], ["right", safe_insets.z], ["bottom", safe_insets.w]]:
		add_theme_constant_override("margin_" + edge[0], 16 + roundi(edge[1]))
	var scaled := Theme.new()
	scaled.default_font_size = roundi(20 * text_scale)
	theme = scaled
	preferences_changed.emit()

func action_for(slot: int) -> String:
	return "interact" if (slot == 0) != swapped else "back"

func set_touch_size(button: Button) -> void:
	button.custom_minimum_size.y = roundi(52 * text_scale)

func motion_duration(seconds: float) -> float:
	return 0.0 if reduce_motion else maxf(0.0, seconds)

func preferences() -> Dictionary:
	return {"text_scale": text_scale, "reduce_motion": reduce_motion, "swapped": swapped}

func apply_preferences(state: Dictionary) -> bool:
	var scale_value: Variant = state.get("text_scale")
	if not (scale_value is int or scale_value is float) or not is_finite(float(scale_value)) or scale_value < 1 or scale_value > 1.6:
		return false
	if not state.get("reduce_motion") is bool or not state.get("swapped") is bool:
		return false
	text_scale = float(scale_value)
	reduce_motion = state.reduce_motion
	swapped = state.swapped
	update_layout()
	return true
