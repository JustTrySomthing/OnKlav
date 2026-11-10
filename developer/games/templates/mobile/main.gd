extends Control
const Mobile = preload("res://mobile_layout.gd")
const Migration = preload("res://save_migration.gd")
const Rules = preload("res://genre_rules.gd")
var panel: MarginContainer
var columns: GridContainer
var status: Label
var buttons: Array[Button] = []
var level := "harbor"
var inventory: Array[String] = []

func _ready() -> void:
	panel = Mobile.new()
	add_child(panel)
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	panel.add_child(scroll)
	var content := VBoxContainer.new()
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	content.add_theme_constant_override("separation", 14)
	scroll.add_child(content)
	status = Label.new()
	status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	content.add_child(status)
	columns = GridContainer.new()
	content.add_child(columns)
	add_button("Interact", use_slot.bind(0))
	add_button("Back", use_slot.bind(1))
	add_button("Large text", toggle_pref.bind("text_scale"))
	add_button("Swap controls", toggle_pref.bind("swapped"))
	add_button("Reduce motion", toggle_pref.bind("reduce_motion"))
	add_button("Load older save example", load_legacy)
	panel.preferences_changed.connect(render)
	resized.connect(render)
	render()
	OnKlavGame.attach_session(self, snapshot, restore, restart, "Collect the harbor key. Swap the touch controls, enlarge text or reduce motion. Load the older-save example to see the renamed level and new inventory field.")

func add_button(title: String, action: Callable) -> void:
	var button := Button.new()
	button.text = title
	button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	button.pressed.connect(action)
	columns.add_child(button)
	buttons.append(button)

func render() -> void:
	columns.columns = 2 if size.x > size.y and size.x >= 650 else 1
	status.text = "%s · %s\nText %d%% · Motion %s" % [level.capitalize(), ", ".join(inventory) if not inventory.is_empty() else "No items", int(panel.text_scale * 100), "reduced" if panel.reduce_motion else "full"]
	for button in buttons: panel.set_touch_size(button)
	for index in range(2): buttons[index].text = panel.action_for(index).capitalize()

func use_slot(index: int) -> void:
	if panel.action_for(index) == "interact":
		if "key" not in inventory: inventory.append("key")
		level = "lighthouse"
	else:
		level = "harbor"
	render()
	if panel.motion_duration(0.2) > 0:
		status.modulate.a = 0.65
		create_tween().tween_property(status, "modulate:a", 1.0, panel.motion_duration(0.2))
	else:
		status.modulate.a = 1.0
	OnKlavGame.checkpoint()

func toggle_pref(key: String) -> void:
	var next: Dictionary = panel.preferences()
	next[key] = (1.5 if next[key] == 1.0 else 1.0) if key == "text_scale" else not next[key]
	panel.apply_preferences(next)
	OnKlavGame.checkpoint()

func load_legacy() -> void:
	if restore({"schema": 1, "level": "old_docks", "found_key": true}):
		OnKlavGame.checkpoint()

func migrate_v1(state: Dictionary) -> Variant:
	if state.get("level") not in ["old_docks", "lighthouse"] or not state.get("found_key") is bool: return null
	return {"schema": 2, "level": "harbor" if state.level == "old_docks" else state.level,
		"inventory": ["key"] if state.found_key else [], "preferences": panel.preferences()}

func valid_state(state: Dictionary) -> bool:
	if state.get("level") not in ["harbor", "lighthouse"] or not state.get("inventory") is Array or not state.get("preferences") is Dictionary: return false
	return state.inventory == [] or state.inventory == ["key"]

func snapshot() -> Dictionary:
	return {"schema": 2, "level": level, "inventory": inventory.duplicate(), "preferences": panel.preferences()}

func restore(state: Dictionary) -> bool:
	var result := Migration.migrate(state, 2, {1: migrate_v1}, valid_state)
	if not result.ok or not panel.apply_preferences(result.state.preferences): return false
	level = result.state.level
	inventory.assign(result.state.inventory)
	render()
	return true

func restart() -> void:
	level = "harbor"
	inventory.clear()
	render()
