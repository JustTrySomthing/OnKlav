extends RefCounted

static func pull_velocity(origin: Vector2, release: Vector2, max_pull: float, gain: float) -> Vector2:
	return (origin - release).limit_length(maxf(0.0, max_pull)) * maxf(0.0, gain)

static func rebound(velocity: Vector2, normal: Vector2) -> Vector2:
	var unit := normal.normalized()
	return velocity.bounce(unit) if velocity.dot(unit) < 0 else velocity

static func grid_step(from: Vector2i, to: Vector2i, bounds: Rect2i, blocked: Array[Vector2i]) -> bool:
	var offset := to - from
	return bounds.has_point(from) and bounds.has_point(to) and absi(offset.x) + absi(offset.y) == 1 and not blocked.has(to)

static func text_matches(input: String, answer: String) -> bool:
	return input.strip_edges().to_lower() == answer.strip_edges().to_lower()

## Local authored content. Reject malformed or oversized packs before building controls.
static func load_questions(path: String) -> Array:
	if not path.begins_with("res://"):
		return []
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null or file.get_length() > 65536:
		return []
	var rows: Variant = JSON.parse_string(file.get_as_text())
	if not rows is Array or rows.is_empty() or rows.size() > 100:
		return []
	for row in rows:
		if not row is Dictionary or not row.get("prompt") is String or not row.get("answer") is String:
			return []
		if row.prompt.strip_edges().is_empty() or row.prompt.length() > 200 or row.answer.strip_edges().is_empty() or row.answer.length() > 24:
			return []
	return rows
