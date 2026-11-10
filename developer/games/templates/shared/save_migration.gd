extends RefCounted
## Transform an isolated copy. The caller only commits a successfully validated result.
const Rules = preload("res://genre_rules.gd")

static func migrate(original: Dictionary, target: int, steps: Dictionary, validate: Callable) -> Dictionary:
	var version: Variant = original.get("schema")
	if target < 1 or target > 10000 or not Rules.bounded_integer(version, 1, target) or not validate.is_valid():
		return {"ok": false, "error": "Unsupported save schema"}
	var draft := original.duplicate(true)
	for current in range(int(version), target):
		var step: Variant = steps.get(current)
		if not step is Callable or not step.is_valid():
			return {"ok": false, "error": "Missing migration from schema %d" % current}
		var next: Variant = step.call(draft)
		if not next is Dictionary or next.get("schema") != current + 1:
			return {"ok": false, "error": "Migration failed at schema %d" % current}
		draft = next
	if not validate.call(draft):
		return {"ok": false, "error": "Migrated save is invalid"}
	return {"ok": true, "state": draft}
