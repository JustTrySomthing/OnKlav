extends "res://starter_hud.gd"
const Classic = preload("res://classic_rules.gd")
const SIZE := 5
const BOUNDS := Rect2i(0, 0, SIZE, SIZE)
const WALLS: Array[Vector2i] = [Vector2i(2, 1), Vector2i(2, 3)]
var hero := Vector2i(0, 2)
var enemy := Vector2i(4, 2)
var hp := 4
var enemy_hp := 3
var turns := 0
var tiles: Array[Button] = []

func _ready() -> void:
	make_layout()
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	field.add_child(center)
	var grid := GridContainer.new()
	grid.columns = SIZE
	center.add_child(grid)
	for index in range(SIZE * SIZE):
		var tile := Button.new()
		tile.custom_minimum_size = Vector2(66, 66)
		tile.pressed.connect(play.bind(Vector2i(index % SIZE, floori(float(index) / SIZE))))
		grid.add_child(tile)
		tiles.append(tile)
	render()
	attach("Tap an adjacent square to move, or tap an adjacent foe to attack. The foe responds after each legal turn.")

func play(target: Vector2i) -> void:
	if hp == 0 or enemy_hp == 0 or not Classic.grid_step(hero, target, BOUNDS, WALLS):
		return
	if target == enemy:
		enemy_hp -= 1
	else:
		hero = target
	turns = mini(100000, turns + 1)
	if enemy_hp > 0: respond()
	render()
	OnKlavGame.checkpoint()

func respond() -> void:
	if Classic.grid_step(enemy, hero, BOUNDS, WALLS):
		hp -= 1
		return
	var best := enemy
	for offset in [Vector2i.LEFT, Vector2i.RIGHT, Vector2i.UP, Vector2i.DOWN]:
		var cell: Vector2i = enemy + offset
		if Classic.grid_step(enemy, cell, BOUNDS, WALLS) and cell.distance_squared_to(hero) < best.distance_squared_to(hero):
			best = cell
	enemy = best

func render() -> void:
	status.text = "You %d HP   ·   Foe %d HP   ·   Turn %d" % [hp, enemy_hp, turns + 1]
	replay_button.visible = hp == 0 or enemy_hp == 0
	if replay_button.visible: status.text = "Victory" if enemy_hp == 0 else "Defeated"
	for index in range(tiles.size()):
		var cell := Vector2i(index % SIZE, floori(float(index) / SIZE))
		tiles[index].text = "You" if cell == hero else ("Foe" if cell == enemy else ("Wall" if cell in WALLS else "·"))
		tiles[index].disabled = replay_button.visible or not Classic.grid_step(hero, cell, BOUNDS, WALLS)

func snapshot() -> Dictionary:
	return {"hero": [hero.x, hero.y], "enemy": [enemy.x, enemy.y], "hp": hp, "enemy_hp": enemy_hp, "turns": turns}

func restore(state: Dictionary) -> bool:
	for key in ["hero", "enemy"]:
		if not state.get(key) is Array or state[key].size() != 2: return false
		for coordinate in state[key]:
			if not Rules.bounded_integer(coordinate, 0, SIZE - 1): return false
	for bound in [["hp", 4], ["enemy_hp", 3], ["turns", 100000]]:
		if not Rules.bounded_integer(state.get(bound[0]), 0, bound[1]): return false
	var next_hero := Vector2i(state.hero[0], state.hero[1])
	var next_enemy := Vector2i(state.enemy[0], state.enemy[1])
	if next_hero == next_enemy or next_hero in WALLS or next_enemy in WALLS: return false
	hero = next_hero
	enemy = next_enemy
	hp = int(state.hp)
	enemy_hp = int(state.enemy_hp)
	turns = int(state.turns)
	render()
	return true

func restart() -> void:
	hero = Vector2i(0, 2)
	enemy = Vector2i(4, 2)
	hp = 4
	enemy_hp = 3
	turns = 0
	render()
