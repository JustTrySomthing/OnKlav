extends "res://starter_hud.gd"
const PADS := 5
const WAVES := 5
var towers := 0
var coins := 3
var life := 5
var wave := 0
var remaining := 0
var progress := 0.0
var health := 0
var pads: Array[Button] = []
var send: Button

func _ready() -> void:
	make_layout()
	field.draw.connect(draw_route)
	for index in range(PADS):
		pads.append(action(str(index + 1), place.bind(index)))
	send = action("Wave", start_wave)
	render()
	attach("Place towers on numbered pads for one coin each. Start waves, earn coins by defeating invaders, and defend the route through five waves.")

func place(index: int) -> void:
	if coins < 1 or life == 0 or (towers & (1 << index)) != 0 or (wave == WAVES and remaining == 0):
		return
	towers |= 1 << index
	coins -= 1
	render()
	OnKlavGame.checkpoint()

func start_wave() -> void:
	if remaining > 0 or wave == WAVES or life == 0:
		return
	wave += 1
	remaining = wave + 2
	health = wave + 1
	progress = 0
	render()
	OnKlavGame.checkpoint()

func _physics_process(delta: float) -> void:
	if OnKlavGame.paused or remaining == 0 or life == 0:
		return
	var old := int(progress)
	progress = minf(float(PADS), progress + delta * 1.6)
	var next := int(progress)
	if next != old and (towers & (1 << old)) != 0:
		health -= 2
	if health <= 0 or progress >= PADS:
		if health <= 0:
			coins += 1
		else:
			life -= 1
		remaining -= 1
		progress = 0
		health = wave + 1 if remaining > 0 else 0
		OnKlavGame.checkpoint()
	render()

func draw_route() -> void:
	var size := field.size
	var y := size.y * 0.55
	field.draw_line(Vector2(0, y), Vector2(size.x, y), Color("#6684a3"), 8)
	for index in range(PADS):
		var point := Vector2((index + 0.5) * size.x / PADS, y - 50)
		field.draw_circle(point, 20, Color("#7ee5bb") if (towers & (1 << index)) != 0 else Color("#485d75"))
	if remaining > 0 and life > 0:
		field.draw_circle(Vector2(progress / PADS * size.x, y), 16, Color("#ff987e"))

func render() -> void:
	replay_button.visible = life == 0 or (wave == WAVES and remaining == 0)
	status.text = "%d coins   ·   %d life   ·   Wave %d / %d" % [coins, life, wave, WAVES]
	if replay_button.visible:
		status.text = ("Base lost" if life == 0 else "Base defended") + "   ·   " + status.text
	for index in range(PADS):
		pads[index].disabled = coins == 0 or replay_button.visible or (towers & (1 << index)) != 0
	send.disabled = remaining > 0 or replay_button.visible
	field.queue_redraw()

func snapshot() -> Dictionary:
	return {"towers": towers, "coins": coins, "life": life, "wave": wave, "remaining": remaining, "progress": progress, "health": health}

func restore(state: Dictionary) -> bool:
	for bound in [["towers", 31], ["coins", 30], ["life", 5], ["wave", WAVES], ["remaining", 7]]:
		if not Rules.bounded_integer(state.get(bound[0]), 0, bound[1]):
			return false
	if not finite_range(state.get("progress"), 0, PADS - 0.000001) or not Rules.bounded_integer(state.get("health"), 0, 6):
		return false
	if state.remaining > 0 and (state.wave == 0 or state.remaining > state.wave + 2 or state.health == 0 or state.health > state.wave + 1):
		return false
	towers = int(state.towers)
	coins = int(state.coins)
	life = int(state.life)
	wave = int(state.wave)
	remaining = int(state.remaining)
	progress = float(state.progress)
	health = int(state.health)
	render()
	return true

func restart() -> void:
	towers = 0
	coins = 3
	life = 5
	wave = 0
	remaining = 0
	progress = 0
	health = 0
	render()
