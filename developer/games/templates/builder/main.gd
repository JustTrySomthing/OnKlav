extends "res://starter_hud.gd"
const SIDE := 4
const NAMES := ["Empty", "Farm", "Workshop", "Home"]
const COSTS := [0, 2, 3, 4]
var selected := 1
var coins := 8
var board: Array[int] = []
var plots: Array[Button] = []

func _ready() -> void:
	make_layout()
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	field.add_child(center)
	var grid := GridContainer.new()
	grid.columns = SIDE
	center.add_child(grid)
	for index in range(SIDE * SIDE):
		var plot := Button.new()
		plot.custom_minimum_size = Vector2(90, 62)
		plot.pressed.connect(build_on.bind(index))
		grid.add_child(plot)
		plots.append(plot)
	for type in range(1, NAMES.size()):
		action(NAMES[type], select_type.bind(type))
	action("Collect", collect)
	restart()
	attach("Choose a building, then tap an empty plot. Farms produce one coin and workshops two per Collect. Place four homes to finish.")

func select_type(type: int) -> void:
	selected = type
	render()

func build_on(index: int) -> void:
	if board[index] != 0 or coins < COSTS[selected] or board.count(3) >= 4:
		return
	coins -= COSTS[selected]
	board[index] = selected
	render()
	OnKlavGame.checkpoint()

func collect() -> void:
	if board.count(3) >= 4:
		return
	coins = mini(100000, coins + production())
	render()
	OnKlavGame.checkpoint()

func production() -> int:
	return board.count(1) + 2 * board.count(2)

func render() -> void:
	var homes := board.count(3)
	status.text = "%d coins   ·   %s (%d)   ·   %d/4 homes\nCollect yields %d coins" % [coins, NAMES[selected], COSTS[selected], homes, production()]
	var stuck := homes < 4 and (not board.has(0) or (coins < 2 and production() == 0))
	replay_button.visible = homes >= 4 or stuck
	if stuck:
		status.text += "\nNo viable plots or income. Start a new town."
	for index in range(plots.size()):
		plots[index].text = NAMES[board[index]]
		plots[index].disabled = board[index] != 0 or homes >= 4

func snapshot() -> Dictionary:
	return {"board": board.duplicate(), "coins": coins, "selected": selected}

func restore(state: Dictionary) -> bool:
	var saved: Variant = state.get("board")
	if not saved is Array or saved.size() != SIDE * SIDE or not Rules.bounded_integer(state.get("coins"), 0, 100000) or not Rules.bounded_integer(state.get("selected"), 1, 3):
		return false
	var next: Array[int] = []
	for plot in saved:
		if not Rules.bounded_integer(plot, 0, 3): return false
		next.append(int(plot))
	board = next
	coins = int(state.coins)
	selected = int(state.selected)
	render()
	return true

func restart() -> void:
	coins = 8
	selected = 1
	board.resize(SIDE * SIDE)
	board.fill(0)
	render()
