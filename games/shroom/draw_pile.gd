extends RefCounted
## Draw pile for solitaire, card puzzles and slow turn-based games.
## Save the actual order so restoring does not depend on an engine RNG version.
var _remaining: Array[int] = []
var _discarded: Array[int] = []

func reset(cards: Array[int], seed_value: int) -> bool:
	if not _valid(cards):
		return false
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	_remaining = cards.duplicate()
	_discarded.clear()
	for index in range(_remaining.size() - 1, 0, -1):
		var other := rng.randi_range(0, index)
		var card := _remaining[index]
		_remaining[index] = _remaining[other]
		_remaining[other] = card
	return true

## Returns -1 if exhausted. Drawn cards stay recorded until the next reset.
func draw() -> int:
	if _remaining.is_empty():
		return -1
	var card: int = _remaining.pop_back()
	_discarded.append(card)
	return card

func snapshot() -> Dictionary:
	return {"remaining": _remaining.duplicate(), "drawn": _discarded.duplicate()}

func restore(state: Dictionary, expected_cards: Array[int]) -> bool:
	var remaining: Variant = state.get("remaining")
	var drawn: Variant = state.get("drawn")
	if not _valid(expected_cards) or not remaining is Array or not drawn is Array:
		return false
	if remaining.size() + drawn.size() != expected_cards.size():
		return false
	var combined: Array = remaining + drawn
	if not _valid(combined):
		return false
	for card in combined:
		if int(card) not in expected_cards:
			return false
	var next_remaining: Array[int] = []
	var next_drawn: Array[int] = []
	for card in remaining:
		next_remaining.append(int(card))
	for card in drawn:
		next_drawn.append(int(card))
	_remaining = next_remaining
	_discarded = next_drawn
	return true

func _valid(cards: Array) -> bool:
	if cards.size() > 256:
		return false
	var seen := {}
	for value in cards:
		if not (value is int or value is float) or not is_finite(float(value)) or value < 0 or value > 2147483647 or floor(value) != value:
			return false
		var card := int(value)
		if seen.has(card):
			return false
		seen[card] = true
	return true
