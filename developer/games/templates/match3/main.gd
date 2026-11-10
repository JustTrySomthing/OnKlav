extends "res://starter_hud.gd"
const SIDE := 6
const SYMBOLS := ["●", "▲", "■", "★", "◆"]
var board: Array[int] = []
var tiles: Array[Button] = []
var picked := -1
var score := 0
var refill := 0

func _ready() -> void:
	make_layout()
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	field.add_child(center)
	var grid := GridContainer.new()
	grid.columns = SIDE
	center.add_child(grid)
	for index in range(SIDE * SIDE):
		var tile := Button.new()
		tile.custom_minimum_size = Vector2(54, 54)
		tile.pressed.connect(select_tile.bind(index))
		grid.add_child(tile)
		tiles.append(tile)
	action("Reset board", func(): seed_board(); render(); OnKlavGame.checkpoint())
	restart()
	attach("Swap neighboring tiles to line up three or more matching shapes. Matches clear, tiles fall, and new tiles refill the board.")

func seed_board() -> void:
	board.clear()
	for y in range(SIDE):
		for x in range(SIDE):
			board.append((x + 2 * y) % SYMBOLS.size())
	board[2] = 0
	board[SIDE + 1] = 0
	picked = -1

func matches() -> Dictionary:
	var hits := {}
	for y in range(SIDE):
		for x in range(SIDE):
			var i := y * SIDE + x
			if x + 2 < SIDE and board[i] == board[i + 1] and board[i] == board[i + 2]:
				for j in [i, i + 1, i + 2]: hits[j] = true
			if y + 2 < SIDE and board[i] == board[i + SIDE] and board[i] == board[i + SIDE * 2]:
				for j in [i, i + SIDE, i + SIDE * 2]: hits[j] = true
	return hits

func select_tile(index: int) -> void:
	if picked < 0:
		picked = index
		render()
		return
	var other := picked
	picked = -1
	var adjacent := (absi(index - other) == SIDE) or (absi(index - other) == 1 and index / SIDE == other / SIDE)
	if adjacent:
		var original := board[other]
		board[other] = board[index]
		board[index] = original
		var hits := matches()
		if hits.is_empty():
			board[index] = board[other]
			board[other] = original
		else:
			for cascade in range(12):
				score = mini(1000000, score + hits.size())
				for x in range(SIDE):
					var bottom := SIDE - 1
					for y in range(SIDE - 1, -1, -1):
						var cell := y * SIDE + x
						if not hits.has(cell):
							board[bottom * SIDE + x] = board[cell]
							bottom -= 1
					while bottom >= 0:
						refill = (refill * 73 + 41) % 997
						board[bottom * SIDE + x] = refill % SYMBOLS.size()
						bottom -= 1
				hits = matches()
				if hits.is_empty(): break
			if not hits.is_empty(): seed_board()
			OnKlavGame.checkpoint()
	render()

func render() -> void:
	status.text = "Match three   ·   Score %d" % score
	for index in range(tiles.size()):
		tiles[index].text = ("[%s]" if index == picked else "%s") % SYMBOLS[board[index]]

func snapshot() -> Dictionary:
	return {"board": board.duplicate(), "score": score, "refill": refill}

func restore(state: Dictionary) -> bool:
	var saved: Variant = state.get("board")
	if not saved is Array or saved.size() != SIDE * SIDE or not Rules.bounded_integer(state.get("score"), 0, 1000000) or not Rules.bounded_integer(state.get("refill"), 0, 996):
		return false
	var next: Array[int] = []
	for cell in saved:
		if not Rules.bounded_integer(cell, 0, SYMBOLS.size() - 1): return false
		next.append(int(cell))
	board = next
	score = int(state.score)
	refill = int(state.refill)
	picked = -1
	render()
	return true

func restart() -> void:
	score = 0
	refill = 0
	seed_board()
	render()
