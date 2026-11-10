extends Control
const Rules = preload("res://genre_rules.gd")
const SIDE := 4
var board := 0
var moves := 0
var cells: Array[Button] = []
var status: Label
var replay: Button

func _ready() -> void:
	var content := VBoxContainer.new()
	content.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_theme_constant_override("separation", 16)
	add_child(content)
	status = Label.new()
	status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	content.add_child(status)
	var grid := GridContainer.new()
	grid.columns = SIDE
	grid.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	content.add_child(grid)
	for index in range(SIDE * SIDE):
		var button := Button.new()
		button.custom_minimum_size = Vector2(72, 72)
		button.pressed.connect(press_cell.bind(index))
		grid.add_child(button)
		cells.append(button)
	replay = Button.new()
	replay.text = "Play again"
	replay.pressed.connect(OnKlavGame.replay)
	content.add_child(replay)
	new_game()
	OnKlavGame.attach_session(self, snapshot, restore, new_game,
		"Turn every tile off. Each tap flips that tile and its horizontal and vertical neighbors.")

func press_cell(index: int) -> void:
	if board == 0 or OnKlavGame.paused:
		return
	board = Rules.toggle_cross(board, index, SIDE)
	moves = mini(moves + 1, 1000000)
	render()
	OnKlavGame.checkpoint()

func render() -> void:
	status.text = ("Solved in %d moves" if board == 0 else "Turn every tile off   ·   %d moves") % moves
	replay.visible = board == 0
	for index in range(cells.size()):
		var lit := (board & (1 << index)) != 0
		cells[index].text = "On" if lit else "Off"
		cells[index].disabled = board == 0

func snapshot() -> Dictionary:
	return {"board": board, "moves": moves}

func restore(state: Dictionary) -> bool:
	if not Rules.bounded_integer(state.get("board"), 0, 65535) or not Rules.bounded_integer(state.get("moves"), 0, 1000000):
		return false
	board = int(state.board)
	moves = int(state.moves)
	render()
	return true

func new_game() -> void:
	board = 0
	for index in [0, 3, 5, 10, 12]:
		board = Rules.toggle_cross(board, index, SIDE)
	moves = 0
	render()
