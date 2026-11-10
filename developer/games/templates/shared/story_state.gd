extends RefCounted
## Store stable scene and item IDs. Validate the complete snapshot before committing it.
var scene := ""
var items: Array[String] = []

func collect(id: String, allowed: Array[String]) -> bool:
	if id not in allowed or id in items:
		return false
	items.append(id)
	return true

func choose(target: String, required: String, scenes: Array[String]) -> bool:
	if target not in scenes or (not required.is_empty() and required not in items):
		return false
	scene = target
	return true

func snapshot() -> Dictionary:
	return {"scene": scene, "items": items.duplicate()}

func restore(state: Dictionary, scenes: Array[String], allowed: Array[String]) -> bool:
	var next_scene: Variant = state.get("scene")
	var next_items: Variant = state.get("items")
	if not next_scene is String or next_scene not in scenes or not next_items is Array or next_items.size() > allowed.size():
		return false
	var checked: Array[String] = []
	for item in next_items:
		if not item is String or item not in allowed or item in checked:
			return false
		checked.append(item)
	scene = next_scene
	items = checked
	return true
