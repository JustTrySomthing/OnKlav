extends "res://starter_hud.gd"
const Progress = preload("res://player_progress.gd")
const Packs = preload("res://level_pack.gd")
const Gesture = preload("res://touch_gesture.gd")
var profile := Progress.new()
var levels: Dictionary
var level_index := 0
var ready_profile := false
var saving := false
var message := ""

func _ready() -> void:
	make_layout()
	levels = Packs.load_levels("res://levels.json", validate_level)
	var tap := Gesture.new()
	field.add_child(tap)
	field.mouse_filter = Control.MOUSE_FILTER_STOP
	field.gui_input.connect(tap.feed)
	tap.tapped.connect(func(at: Vector2):
		if field.size.x > 0: submit(clampi(int(at.x / field.size.x * 3) + 1, 1, 3)))
	field.draw.connect(draw_choices)
	for choice in [1, 2, 3]: action(str(choice), submit.bind(choice))
	action("Reload progress", load_profile)
	render()
	attach("Pick the correct number. Permanent rewards are saved separately from the current run.")
	load_profile()

func validate_level(data: Dictionary) -> bool:
	return data.get("prompt") is String and not data.prompt.strip_edges().is_empty() and data.prompt.length() <= 80 and Rules.bounded_integer(data.get("answer"), 1, 3) and Rules.bounded_integer(data.get("reward"), 0, 1000)

func load_profile() -> void:
	if saving: return
	saving = true
	var reply: Dictionary = await OnKlavGame.load_data("campaign_profile")
	ready_profile = reply.get("ok", false) and (not reply.get("exists", false) or (reply.get("state") is Dictionary and profile.restore(reply.state)))
	if ready_profile and not reply.get("exists", false): profile = Progress.new()
	message = "" if ready_profile else "Progress unavailable. Retry before playing."
	saving = false
	render()

func submit(choice: int) -> void:
	if not ready_profile or saving or levels.is_empty() or OnKlavGame.paused: return
	var id: String = levels.keys()[level_index]
	var keys := levels.keys()
	if level_index > 0 and not profile.unlocked(id, {id: [keys[level_index - 1]]}):
		message = "Complete the previous level first."
		render()
		return
	if choice != levels[id].answer:
		message = "Try again."
		render()
		return
	var proposed := Progress.new()
	if not proposed.restore(profile.snapshot()) or not proposed.complete(id, 100, 3): return
	saving = true
	message = "Saving reward…"
	render()
	var ok: bool = await OnKlavGame.save_data("campaign_profile", proposed.snapshot())
	if ok:
		profile = proposed
		level_index = mini(level_index + 1, levels.size() - 1)
		message = "Level complete."
		OnKlavGame.checkpoint()
	else:
		message = "Save unconfirmed. Reload progress before trying again."
		ready_profile = false
	saving = false
	render()

func draw_choices() -> void:
	for index in range(3):
		var width := field.size.x / 3.0
		field.draw_rect(Rect2(index * width + 4, 0, maxf(0, width - 8), field.size.y), Color("#30465c"))
		field.draw_string(ThemeDB.fallback_font, Vector2((index + 0.45) * width, field.size.y * 0.5), str(index + 1), HORIZONTAL_ALIGNMENT_LEFT, -1, 36)

func render() -> void:
	if levels.is_empty():
		status.text = "Level pack unavailable."
		return
	var rewards := {}
	for id in levels: rewards[id] = levels[id].reward
	status.text = "Level %d · %s · %d reward coins\n%s" % [level_index + 1, levels[levels.keys()[level_index]].prompt, profile.earned_reward(rewards), message]
	replay_button.visible = profile.records.size() == levels.size()
	field.queue_redraw()

func snapshot() -> Dictionary:
	return {"level": level_index}

func restore(state: Dictionary) -> bool:
	if not Rules.bounded_integer(state.get("level"), 0, levels.size() - 1): return false
	level_index = int(state.level)
	render()
	return true

func restart() -> void:
	level_index = 0
	message = ""
	render()
