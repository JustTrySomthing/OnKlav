extends RefCounted
## Single current ten-goal practice match. Rendering and collisions belong to Godot.
signal changed
const GOALS := 10
enum Phase { WAITING, PLAYING, FINISHED }
var scores: Array[int] = [0, 0]
var goal_order: Array[int] = []
var phase: int = Phase.WAITING
var session: String = Crypto.new().generate_random_bytes(16).hex_encode()
var rally: int = 0

func serve() -> bool:
	if phase != Phase.WAITING:
		return false
	phase = Phase.PLAYING
	rally += 1
	changed.emit()
	return true

func goal(side: int) -> bool:
	if phase != Phase.PLAYING or side not in [0, 1]:
		return false
	scores[side] += 1
	goal_order.append(side)
	phase = Phase.FINISHED if scores[0] + scores[1] == GOALS else Phase.WAITING
	changed.emit()
	return true

func round_number() -> int:
	return mini(scores[0] + scores[1] + 1, GOALS)

func outcome() -> String:
	if phase != Phase.FINISHED:
		return ""
	if scores[0] == scores[1]:
		return "Draw"
	return "You win the match!" if scores[0] > scores[1] else "They win the match"

func snapshot() -> Dictionary:
	return {"scores": scores.duplicate(), "goal_order": goal_order.duplicate(), "phase": phase, "session": session, "rally": rally}

func restore(value: Dictionary) -> bool:
	# Validate before mutating; a damaged save must never create extra points.
	var raw: Variant = value.get("scores")
	if not raw is Array or raw.size() != 2:
		return false
	for n in raw:
		if not (n is int or n is float) or not is_finite(float(n)) or float(n) != floor(float(n)) or n < 0 or n > GOALS:
			return false
	var total: int = int(raw[0]) + int(raw[1])
	for key in ["phase", "rally"]:
		var n: Variant = value.get(key)
		if not (n is int or n is float) or not is_finite(float(n)) or float(n) != floor(float(n)) or n < 0 or n > GOALS:
			return false
	var p: int = int(value.get("phase", -1))
	var count: int = int(value.get("rally", -1))
	var id: String = str(value.get("session", ""))
	if p not in [Phase.WAITING, Phase.PLAYING, Phase.FINISHED] or total > GOALS or id.length() != 32 or not id.is_valid_hex_number():
		return false
	if (total == GOALS) != (p == Phase.FINISHED) or count != total + (1 if p == Phase.PLAYING else 0):
		return false
	var order: Array[int] = []
	if value.has("goal_order"):
		var history: Variant = value.goal_order
		if not history is Array or history.size() != total:
			return false
		for side in history:
			if not (side is int or side is float) or not is_finite(float(side)) or side!=floor(float(side)) or side< -1 or side>1:
				return false
			order.append(int(side))
		if order.count(0)>int(raw[0]) or order.count(1)>int(raw[1]):
			return false
	else:
		# Older saves have totals without the order of their goals.
		order.resize(total)
		order.fill(-1)
	goal_order.assign(order)
	scores.assign([int(raw[0]), int(raw[1])])
	phase = p
	rally = count
	session = id
	changed.emit()
	return true
