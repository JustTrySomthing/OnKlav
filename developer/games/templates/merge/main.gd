extends "res://starter_hud.gd"
const SIDE := 4
const GOAL := 128
var board: Array[int] = []
var tiles: Array[Label] = []
var cursor := 0

func _ready() -> void:
	make_layout()
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	field.add_child(center)
	var grid := GridContainer.new()
	grid.columns = SIDE
	center.add_child(grid)
	for index in range(SIDE * SIDE):
		var tile := Label.new()
		tile.custom_minimum_size = Vector2(72, 64)
		tile.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		tile.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		grid.add_child(tile)
		tiles.append(tile)
	for direction in range(4):
		action(["Left", "Up", "Down", "Right"][direction], slide.bind(direction))
	restart()
	attach("Slide tiles in one direction. Equal tiles merge once per move. Reach 128 to win.")

func cell(line: int, offset: int, direction: int) -> int:
	match direction:
		0: return line * SIDE + offset
		1: return offset * SIDE + line
		2: return (SIDE - 1 - offset) * SIDE + line
		_: return line * SIDE + SIDE - 1 - offset

func slide(direction: int) -> void:
	if terminal(): return
	var before := board.duplicate()
	for line in range(SIDE):
		var values: Array[int] = []
		for offset in range(SIDE):
			var value := board[cell(line, offset, direction)]
			if value > 0: values.append(value)
		var merged: Array[int] = []
		var index := 0
		while index < values.size():
			var value := values[index]
			if index + 1 < values.size() and values[index + 1] == value:
				value *= 2
				index += 1
			merged.append(value)
			index += 1
		for offset in range(SIDE):
			board[cell(line, offset, direction)] = merged[offset] if offset < merged.size() else 0
	if board != before:
		spawn()
		render()
		OnKlavGame.checkpoint()

func spawn() -> void:
	for offset in range(board.size()):
		var index := (cursor + offset) % board.size()
		if board[index] == 0:
			board[index] = 2
			cursor = (index + 5) % board.size()
			return

func terminal() -> bool:
	if board.has(GOAL): return true
	if board.has(0): return false
	for index in range(board.size()):
		if index % SIDE < SIDE - 1 and board[index] == board[index + 1]: return false
		if index + SIDE < board.size() and board[index] == board[index + SIDE]: return false
	return true

func render() -> void:
	status.text = "Reached 128!" if board.has(GOAL) else ("No moves left" if terminal() else "Merge tiles to reach 128")
	replay_button.visible = terminal()
	for index in range(tiles.size()):
		tiles[index].text = "·" if board[index] == 0 else str(board[index])

func snapshot() -> Dictionary:
	return {"board": board.duplicate(), "cursor": cursor}

func restore(state: Dictionary) -> bool:
	var saved: Variant = state.get("board")
	if not saved is Array or saved.size() != SIDE * SIDE or not Rules.bounded_integer(state.get("cursor"), 0, SIDE * SIDE - 1):
		return false
	var next: Array[int] = []
	for value in saved:
		if not Rules.bounded_integer(value, 0, GOAL): return false
		var tile := int(value)
		if tile == 1 or (tile & (tile - 1)) != 0: return false
		next.append(tile)
	board = next
	cursor = int(state.cursor)
	render()
	return true

func restart() -> void:
	board.resize(SIDE * SIDE)
	board.fill(0)
	cursor = 0
	spawn()
	spawn()
	render()
