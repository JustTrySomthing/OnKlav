extends "res://starter_hud.gd"
const START := Vector2(0.5, 0.8)
const GOAL := Vector2(0.5, 0.18)
var ball := START
var velocity := Vector2.ZERO
var attempts := 0
var goals := 0
var dragging := false
var drag_point := START

func _ready() -> void:
	make_layout()
	field.mouse_filter = Control.MOUSE_FILTER_STOP
	field.gui_input.connect(aim_input)
	field.draw.connect(draw_green)
	action("Reset ball", reset_ball)
	OnKlavGame.suspended.connect(func(): dragging = false; field.queue_redraw())
	render()
	attach("Pull back from the ball and release to putt. Direction and distance set aim and power. Sink three putts to win.")

func aim_input(event: InputEvent) -> void:
	if OnKlavGame.paused or goals >= 3 or velocity.length() > 0.01:
		return
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		var at: Vector2 = event.position / field.size.max(Vector2.ONE)
		if event.pressed:
			dragging = at.distance_to(ball) <= 0.14
			drag_point = at
		elif dragging:
			dragging = false
			var pull := (ball - at).limit_length(0.38)
			if pull.length() > 0.015:
				velocity = pull * 4.0
				attempts = mini(1000000, attempts + 1)
				OnKlavGame.checkpoint()
			render()
	elif event is InputEventMouseMotion and dragging:
		drag_point = event.position / field.size.max(Vector2.ONE)
	field.queue_redraw()

func _physics_process(delta: float) -> void:
	if OnKlavGame.paused or velocity == Vector2.ZERO:
		return
	ball += velocity * delta
	if ball.x < 0.04 or ball.x > 0.96:
		velocity.x *= -0.75
	if ball.y < 0.04 or ball.y > 0.96:
		velocity.y *= -0.75
	ball = ball.clamp(Vector2(0.04, 0.04), Vector2(0.96, 0.96))
	velocity = velocity.move_toward(Vector2.ZERO, delta * 0.54)
	if ball.distance_to(GOAL) < 0.055:
		goals += 1
		reset_ball()
	elif velocity == Vector2.ZERO:
		OnKlavGame.checkpoint()
	field.queue_redraw()

func draw_green() -> void:
	field.draw_rect(Rect2(Vector2.ZERO, field.size), Color("#244a3e"))
	field.draw_circle(GOAL * field.size, 18, Color("#101c24"))
	field.draw_circle(ball * field.size, 12, Color.WHITE)
	if dragging:
		field.draw_line(ball * field.size, (ball + (ball - drag_point).limit_length(0.38)) * field.size, Color("#f5df92"), 4)

func render() -> void:
	status.text = "%d / 3 putts   ·   %d strokes" % [goals, attempts]
	replay_button.visible = goals == 3
	field.queue_redraw()

func reset_ball() -> void:
	ball = START
	velocity = Vector2.ZERO
	dragging = false
	render()
	OnKlavGame.checkpoint()

func snapshot() -> Dictionary:
	return {"x": ball.x, "y": ball.y, "vx": velocity.x, "vy": velocity.y, "goals": goals, "attempts": attempts}

func restore(state: Dictionary) -> bool:
	for key in ["x", "y"]:
		if not finite_range(state.get(key), 0, 1): return false
	for key in ["vx", "vy"]:
		if not finite_range(state.get(key), -1.6, 1.6): return false
	if not Rules.bounded_integer(state.get("goals"), 0, 3) or not Rules.bounded_integer(state.get("attempts"), 0, 1000000) or state.goals > state.attempts:
		return false
	ball = Vector2(state.x, state.y)
	velocity = Vector2(state.vx, state.vy)
	goals = int(state.goals)
	attempts = int(state.attempts)
	dragging = false
	render()
	return true

func restart() -> void:
	ball = START
	velocity = Vector2.ZERO
	dragging = false
	goals = 0
	attempts = 0
	render()
