extends "res://starter_hud.gd"
const COURSE := 6.0
var progress := 0.0
var lateral := 0.0
var elapsed := 0.0
var steering := 0

func _ready() -> void:
	make_layout()
	field.draw.connect(draw_course)
	for direction in [-1, 1]:
		var button := action("Left" if direction < 0 else "Right", func(): pass)
		button.button_down.connect(func(): steering = direction)
		button.button_up.connect(func(): if steering == direction: steering = 0)
	OnKlavGame.suspended.connect(func(): steering = 0)
	render()
	attach("Hold Left or Right to steer on a circular track. Stay near the middle of the road for speed and beat the rival over two laps.")

func _physics_process(delta: float) -> void:
	if OnKlavGame.paused or progress >= COURSE:
		return
	elapsed += delta
	var checkpoint := int(progress)
	lateral = clampf(lateral + (steering * 1.8 - 0.23) * delta, -1, 1)
	var speed := 0.32 if absf(lateral) < 0.65 else 0.14
	progress = minf(COURSE, progress + delta * speed)
	render()
	if int(progress) != checkpoint or progress >= COURSE:
		OnKlavGame.checkpoint()

func draw_course() -> void:
	var size := field.size
	var center := size * 0.5
	var radius := minf(size.x, size.y) * 0.34
	for i in range(3):
		var ring := radius + (i - 1) * radius * 0.27
		field.draw_arc(center, ring, 0, TAU, 96, Color("#486987"), 4)
	for i in range(3):
		var angle := TAU * i / 3.0
		var normal := Vector2.from_angle(angle)
		field.draw_line(center + normal * radius * 0.73, center + normal * radius * 1.27, Color("#dbdfeb"), 3)
	var player := Vector2.from_angle(progress * TAU / 3) * radius * (1.0 + lateral * 0.27)
	var rival := Vector2.from_angle(elapsed * 0.275 * TAU / 3) * radius * 1.15
	field.draw_circle(center + rival, 10, Color("#fa9073"))
	field.draw_circle(center + player, 12, Color("#78e8bf"))

func render() -> void:
	status.text = "Lap %d / 2   ·   Checkpoint %d / 3   ·   %.1f s" % [mini(2, int(progress / 3) + 1), int(progress) % 3 + 1, elapsed]
	replay_button.visible = progress >= COURSE
	if replay_button.visible:
		status.text = ("You won" if elapsed * 0.275 < COURSE else "Rival won") + "   ·   %.1f s" % elapsed
	field.queue_redraw()

func snapshot() -> Dictionary:
	return {"progress": progress, "lateral": lateral, "elapsed": elapsed}

func restore(state: Dictionary) -> bool:
	if not finite_range(state.get("progress"), 0, COURSE) or not finite_range(state.get("lateral"), -1, 1) or not finite_range(state.get("elapsed"), 0, 3600):
		return false
	progress = float(state.progress)
	lateral = float(state.lateral)
	elapsed = float(state.elapsed)
	steering = 0
	render()
	return true

func restart() -> void:
	progress = 0
	lateral = 0
	elapsed = 0
	steering = 0
	render()
