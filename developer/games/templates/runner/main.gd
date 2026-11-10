extends Control
const Rules = preload("res://genre_rules.gd")
var lane := 1
var obstacle_lane := 0
var distance := 0.0
var score := 0
var finished := false
var status: Label
var replay: Button
var track: Control

func _ready() -> void:
	var content := VBoxContainer.new()
	content.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	content.add_theme_constant_override("separation", 12)
	add_child(content)
	status = Label.new()
	status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	content.add_child(status)
	track = Control.new()
	track.size_flags_vertical = Control.SIZE_EXPAND_FILL
	track.draw.connect(draw_track)
	content.add_child(track)
	var choices := HBoxContainer.new()
	content.add_child(choices)
	for index in range(3):
		var button := Button.new()
		button.text = ["Left", "Center", "Right"][index]
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.custom_minimum_size.y = 64
		button.pressed.connect(select_lane.bind(index))
		choices.add_child(button)
	replay = Button.new()
	replay.text = "Play again"
	replay.pressed.connect(OnKlavGame.replay)
	content.add_child(replay)
	render()
	OnKlavGame.attach_session(self, snapshot, restore, new_game,
		"Change lanes to avoid incoming barriers. Score a point for each barrier you pass.")

func select_lane(index: int) -> void:
	if not finished and not OnKlavGame.paused:
		lane = index
		track.queue_redraw()

func _physics_process(delta: float) -> void:
	if finished or OnKlavGame.paused:
		return
	distance += delta * minf(1.4, 0.44 + score * 0.025)
	if distance >= 1.0:
		distance = 0.0
		if lane == obstacle_lane:
			finished = true
		else:
			score = mini(score + 1, 1000000)
			obstacle_lane = (obstacle_lane + 1 + score % 2) % 3
		render()
		OnKlavGame.checkpoint()
	track.queue_redraw()

func draw_track() -> void:
	var width := track.size.x / 3.0
	var height := track.size.y
	track.draw_rect(Rect2(Vector2.ZERO, track.size), Color("#182331"))
	for index in range(1, 3):
		track.draw_line(Vector2(index * width, 0), Vector2(index * width, height), Color("#69839c"), 2)
	track.draw_rect(Rect2(Vector2((obstacle_lane + 0.2) * width, distance * height * 0.8), Vector2(width * 0.6, height * 0.09)), Color("#fa937d"))
	track.draw_circle(Vector2((lane + 0.5) * width, height * 0.85), minf(width, height) * 0.09, Color("#82e6b3"))

func render() -> void:
	status.text = ("Run finished   ·   %d passed" if finished else "%d passed") % score
	replay.visible = finished
	track.queue_redraw()

func snapshot() -> Dictionary:
	return {"lane": lane, "obstacle": obstacle_lane, "distance": distance, "score": score, "finished": finished}

func restore(state: Dictionary) -> bool:
	if not Rules.bounded_integer(state.get("lane"), 0, 2) or not Rules.bounded_integer(state.get("obstacle"), 0, 2):
		return false
	if not Rules.bounded_integer(state.get("score"), 0, 1000000) or not state.get("finished") is bool:
		return false
	var progress: Variant = state.get("distance")
	if not (progress is float or progress is int) or not is_finite(float(progress)) or progress < 0 or progress >= 1:
		return false
	lane = int(state.lane)
	obstacle_lane = int(state.obstacle)
	distance = float(progress)
	score = int(state.score)
	finished = state.finished
	render()
	return true

func new_game() -> void:
	lane = 1
	obstacle_lane = 0
	distance = 0.0
	score = 0
	finished = false
	render()
