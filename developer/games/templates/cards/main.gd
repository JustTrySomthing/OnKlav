extends "res://starter_hud.gd"
const CardButton = preload("res://card_drag.gd")
const SUITS := ["♠", "♥", "♦"]
const STACKS := [[0, 4, 8], [3, 7, 2], [6, 1, 5]]
var tops: Array[int] = [0, 0, 0]
var placed: Array[int] = [0, 0, 0]
var sources: Array[Button] = []
var targets: Array[Button] = []
var selected := -1

func _ready() -> void:
	make_layout()
	var content := VBoxContainer.new()
	content.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_theme_constant_override("separation", 24)
	field.add_child(content)
	for row in range(2):
		var line := HBoxContainer.new()
		content.add_child(line)
		for index in range(3):
			var button := CardButton.new()
			button.custom_minimum_size.y = 96
			button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			line.add_child(button)
			if row == 0:
				button.pressed.connect(pick.bind(index))
				sources.append(button)
			else:
				button.accepts = accepts.bind(index)
				button.card_dropped.connect(move_card.bind(index))
				button.pressed.connect(place_selected.bind(index))
				targets.append(button)
	render()
	attach("Drag an exposed card onto its suit foundation, or tap the card and then its foundation. Place ranks 1–3 in order.")

func top(index: int) -> int:
	return STACKS[index][tops[index]] if tops[index] < 3 else -1

func pick(index: int) -> void:
	selected = top(index)
	render()

func place_selected(index: int) -> void:
	move_card(selected, index)

func accepts(card: int, suit: int) -> bool:
	if card < 0 or card >= 9 or floori(float(card) / 3) != suit or card % 3 != placed[suit]:
		return false
	for index in range(3):
		if top(index) == card: return true
	return false

func move_card(card: int, suit: int) -> void:
	if not accepts(card, suit): return
	for index in range(3):
		if top(index) == card:
			tops[index] += 1
			break
	placed[suit] += 1
	selected = -1
	render()
	OnKlavGame.checkpoint()

func render() -> void:
	var total := placed[0] + placed[1] + placed[2]
	status.text = "Foundations complete" if total == 9 else "%d / 9 cards placed" % total
	replay_button.visible = total == 9
	for index in range(3):
		var card := top(index)
		sources[index].set("card_id", card)
		sources[index].text = "Empty" if card < 0 else "%s%d%s" % [SUITS[floori(float(card) / 3)], card % 3 + 1, " ✓" if card == selected else ""]
		targets[index].text = "%s foundation\n%d / 3" % [SUITS[index], placed[index]]

func snapshot() -> Dictionary:
	return {"tops": tops.duplicate()}

func restore(state: Dictionary) -> bool:
	var saved: Variant = state.get("tops")
	if not saved is Array or saved.size() != 3: return false
	var next: Array[int] = []
	var masks := [0, 0, 0]
	for index in range(3):
		if not Rules.bounded_integer(saved[index], 0, 3): return false
		next.append(int(saved[index]))
		for offset in range(next[index]):
			var card: int = STACKS[index][offset]
			masks[floori(float(card) / 3)] |= 1 << (card % 3)
	var counts: Array[int] = []
	for mask in masks:
		var count: int = [0, 1, 3, 7].find(mask)
		if count < 0: return false
		counts.append(count)
	tops = next
	placed = counts
	selected = -1
	render()
	return true

func restart() -> void:
	tops = [0, 0, 0]
	placed = [0, 0, 0]
	selected = -1
	render()
