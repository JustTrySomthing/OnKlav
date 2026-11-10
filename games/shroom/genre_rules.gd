extends RefCounted
## Pure rules used by the puzzle and idle starters. Save values, not engine nodes.

static func bounded_integer(value: Variant, minimum: int, maximum: int) -> bool:
	if not (value is int or value is float):
		return false
	return is_finite(float(value)) and value >= minimum and value <= maximum and floor(value) == value

static func toggle_cross(board: int, index: int, side: int) -> int:
	if side < 2 or side > 7 or index < 0 or index >= side * side:
		return board
	var cell := Vector2i(index % side, floori(float(index) / side))
	for offset in [Vector2i.ZERO, Vector2i.LEFT, Vector2i.RIGHT, Vector2i.UP, Vector2i.DOWN]:
		var neighbor: Vector2i = cell + offset
		if Rect2i(0, 0, side, side).has_point(neighbor):
			board ^= 1 << (neighbor.y * side + neighbor.x)
	return board

## Credit only whole seconds and bound elapsed time and rate before multiplication.
static func offline_reward(saved_at: int, now: int, rate: int, cap_seconds: int) -> int:
	var elapsed := clampi(now - saved_at, 0, clampi(cap_seconds, 0, 86400))
	return elapsed * clampi(rate, 0, 100000)
