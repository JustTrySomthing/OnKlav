extends RefCounted
## Load local authored data and validate each payload before returning any levels.
static func load_levels(path: String, validate: Callable) -> Dictionary:
	if not path.begins_with("res://") or not validate.is_valid(): return {}
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null or file.get_length() > 131072: return {}
	var pack: Variant = JSON.parse_string(file.get_as_text())
	if not pack is Dictionary or pack.get("schema") != 1: return {}
	var rows: Variant = pack.get("levels")
	if not rows is Array or rows.is_empty() or rows.size() > 128: return {}
	var levels := {}
	for row in rows:
		if not row is Dictionary or not row.get("id") is String or not row.get("data") is Dictionary: return {}
		var id: String = row.id
		if id.is_empty() or id.length() > 32 or levels.has(id): return {}
		for letter in id:
			if letter not in "abcdefghijklmnopqrstuvwxyz0123456789_": return {}
		if not validate.call(row.data): return {}
		levels[id] = row.data.duplicate(true)
	return levels
