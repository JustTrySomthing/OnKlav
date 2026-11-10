extends Control
## Shared layout for the small genre examples. The host owns session chrome.
const Rules = preload("res://genre_rules.gd")
var column: VBoxContainer
var status: Label
var field: Control
var actions: HBoxContainer
var replay_button: Button

func make_layout() -> void:
	column = VBoxContainer.new()
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.add_theme_constant_override("separation", 12)
	add_child(column)
	status = Label.new()
	status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(status)
	field = Control.new()
	field.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(field)
	actions = HBoxContainer.new()
	column.add_child(actions)
	replay_button = Button.new()
	replay_button.text = "Play again"
	replay_button.custom_minimum_size.y = 52
	replay_button.pressed.connect(OnKlavGame.replay)
	replay_button.hide()
	column.add_child(replay_button)

func action(text: String, callback: Callable) -> Button:
	var button := Button.new()
	button.text = text
	button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	button.custom_minimum_size.y = 56
	button.pressed.connect(callback)
	actions.add_child(button)
	return button

func attach(help: String) -> void:
	OnKlavGame.attach_session(self, snapshot, restore, restart, help)

func snapshot() -> Dictionary:
	return {}

func restore(_state: Dictionary) -> bool:
	return false

func restart() -> void:
	pass

func finite_range(value: Variant, low: float, high: float) -> bool:
	return (value is int or value is float) and is_finite(float(value)) and value >= low and value <= high
