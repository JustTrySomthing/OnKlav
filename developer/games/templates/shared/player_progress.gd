extends RefCounted
## Permanent progress, stored separately from the active run.
const Rules = preload("res://genre_rules.gd")
const MAX_SCORE := 1000000000
var records: Dictionary = {}

func valid_id(id: String) -> bool:
	if id.is_empty() or id.length() > 32: return false
	for letter in id:
		if letter not in "abcdefghijklmnopqrstuvwxyz0123456789_": return false
	return true

func complete(id: String, score: int, medal: int) -> bool:
	if not valid_id(id) or score < 0 or score > MAX_SCORE or medal < 1 or medal > 3:
		return false
	if not records.has(id) and records.size() >= 128: return false
	var prior: Dictionary = records.get(id, {})
	records[id] = {"score": maxi(prior.get("score", 0), score), "medal": maxi(prior.get("medal", 0), medal)}
	return true

func unlocked(id: String, prerequisites: Dictionary) -> bool:
	if not prerequisites.has(id): return false
	for needed in prerequisites[id]:
		if not records.has(needed): return false
	return true

## Derived from completed IDs. Replaying or loading cannot grant a second reward.
func earned_reward(reward_by_level: Dictionary) -> int:
	var total := 0
	for id in records:
		var reward: Variant = reward_by_level.get(id, 0)
		if Rules.bounded_integer(reward, 0, 1000000): total += int(reward)
	return total

func snapshot() -> Dictionary:
	return {"schema": 1, "levels": records.duplicate(true)}

func restore(state: Dictionary) -> bool:
	var levels: Variant = state.get("levels")
	if state.get("schema") != 1 or not levels is Dictionary or levels.size() > 128: return false
	var next := {}
	for id in levels:
		var row: Variant = levels[id]
		if not id is String or not valid_id(id) or not row is Dictionary: return false
		if not Rules.bounded_integer(row.get("score"), 0, MAX_SCORE) or not Rules.bounded_integer(row.get("medal"), 1, 3): return false
		next[id] = {"score": int(row.score), "medal": int(row.medal)}
	records = next
	return true
