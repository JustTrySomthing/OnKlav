extends "res://starter_hud.gd"
const Classic = preload("res://classic_rules.gd")
const ROWS := 3
const COLS := 6
const START := Vector2(0.5, 0.82)
var paddle := 0.5
var ball := START
var velocity := Vector2.ZERO
var bricks := (1 << (ROWS * COLS)) - 1
var lives := 3

func _ready() -> void:
	make_layout()
	field.mouse_filter = Control.MOUSE_FILTER_STOP
	field.gui_input.connect(aim)
	field.draw.connect(draw_board)
	action("Launch", launch)
	render()
	attach("Touch or slide on the board to move the paddle. Launch and clear the bricks. You have three balls.")

func aim(event: InputEvent) -> void:
	if event is InputEventScreenTouch or event is InputEventScreenDrag or (event is InputEventMouseMotion and (event.button_mask & MOUSE_BUTTON_MASK_LEFT) != 0):
		paddle = clampf(event.position.x / maxf(field.size.x, 1), 0.14, 0.86)
		field.queue_redraw()

func launch() -> void:
	if velocity != Vector2.ZERO or lives == 0 or bricks == 0: return
	ball = Vector2(paddle, START.y)
	velocity = Classic.pull_velocity(Vector2.ZERO, Vector2(-0.1, 0.36), 1.0, 1.0)

func brick(index: int) -> Rect2:
	return Rect2(0.06 + (index % COLS) * 0.15, 0.12 + floori(float(index) / COLS) * 0.055, 0.13, 0.038)

func _physics_process(delta: float) -> void:
	if OnKlavGame.paused or velocity == Vector2.ZERO or bricks == 0 or lives == 0: return
	var old := ball
	ball += velocity * delta
	if ball.x < 0.02: velocity = Classic.rebound(velocity, Vector2.RIGHT)
	if ball.x > 0.98: velocity = Classic.rebound(velocity, Vector2.LEFT)
	if ball.y < 0.02: velocity = Classic.rebound(velocity, Vector2.DOWN)
	ball = ball.clamp(Vector2(0.02, 0.02), Vector2(0.98, 1.1))
	if old.y <= 0.89 and ball.y >= 0.89 and absf(ball.x - paddle) <= 0.15:
		ball.y = 0.89
		velocity = Classic.rebound(velocity, Vector2.UP)
		velocity.x = clampf((ball.x - paddle) * 2.1, -0.3, 0.3)
	for index in range(ROWS * COLS):
		if (bricks & (1 << index)) == 0 or not brick(index).grow(0.012).has_point(ball): continue
		var normal := Vector2.UP if velocity.y > 0 else Vector2.DOWN
		var area := brick(index).grow(0.012)
		if old.x < area.position.x: normal = Vector2.LEFT
		elif old.x > area.end.x: normal = Vector2.RIGHT
		ball = old
		velocity = Classic.rebound(velocity, normal)
		bricks &= ~(1 << index)
		if bricks == 0: velocity = Vector2.ZERO
		OnKlavGame.checkpoint()
		break
	if ball.y > 1.0:
		lives -= 1
		ball = START
		velocity = Vector2.ZERO
		OnKlavGame.checkpoint()
	render()

func draw_board() -> void:
	field.draw_rect(Rect2(Vector2.ZERO, field.size), Color("#203346"))
	for index in range(ROWS * COLS):
		if (bricks & (1 << index)) != 0:
			var area := brick(index)
			field.draw_rect(Rect2(area.position * field.size, area.size * field.size), Color("#82e3bd"))
	field.draw_rect(Rect2(Vector2(paddle - 0.14, 0.9) * field.size, Vector2(0.28, 0.025) * field.size), Color("#faad82"))
	field.draw_circle(ball * field.size, 7, Color.WHITE)

func render() -> void:
	status.text = "Board cleared" if bricks == 0 else ("Out of balls" if lives == 0 else "%d balls left" % lives)
	replay_button.visible = bricks == 0 or lives == 0
	field.queue_redraw()

func snapshot() -> Dictionary:
	return {"bricks": bricks, "lives": lives, "paddle": paddle, "ball": [ball.x, ball.y], "velocity": [velocity.x, velocity.y]}

func restore(state: Dictionary) -> bool:
	if not Rules.bounded_integer(state.get("bricks"), 0, (1 << (ROWS * COLS)) - 1) or not Rules.bounded_integer(state.get("lives"), 0, 3) or not finite_range(state.get("paddle"), 0.14, 0.86): return false
	for key in ["ball", "velocity"]:
		if not state.get(key) is Array or state[key].size() != 2: return false
		var lower := 0.0 if key == "ball" else -0.4
		var upper := 1.0 if key == "ball" else 0.4
		for component in state[key]:
			if not finite_range(component, lower, upper): return false
	bricks = int(state.bricks)
	lives = int(state.lives)
	paddle = float(state.paddle)
	ball = Vector2(state.ball[0], state.ball[1])
	velocity = Vector2(state.velocity[0], state.velocity[1])
	render()
	return true

func restart() -> void:
	bricks = (1 << (ROWS * COLS)) - 1
	lives = 3
	paddle = 0.5
	ball = START
	velocity = Vector2.ZERO
	render()
