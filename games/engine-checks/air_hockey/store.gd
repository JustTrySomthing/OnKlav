extends RefCounted
## One bounded private checkpoint, never a match history or an executable package.
const MatchState = preload("res://air_hockey/match.gd")
const Rink = preload("res://air_hockey/rink.gd")
const CURRENT := "user://onklav-current-game.json"
const LIMIT := 4096

static func helper(): return Engine.get_main_loop().root.get_node("OnKlavGame")

static func read_current(path: String = CURRENT) -> Dictionary:
	var storage=helper();storage.init_storage()
	if storage.host_storage and storage.host_boot.get("known",storage.host_boot.get("exists",false)):
		var saved:Dictionary=storage.load_match(path)
		if not saved.get("ok",false):return saved
		if not saved.get("exists",false):return {"ok":true,"active":false}
		return validate(saved.state)
	if storage.host_storage and not storage.host_boot.get("ok",false):return {"ok":false,"error":"Saved progress needs attention; it has been retained."}
	if not FileAccess.file_exists(path):
		return {"ok": true, "active": false}
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null or file.get_length() > LIMIT:
		return {"ok": false, "error": "The saved match could not be read."}
	var parser := JSON.new()
	var parsed := parser.parse(file.get_as_text())
	file.close()
	var raw: Variant = parser.data if parsed == OK else null
	if raw is Dictionary and raw.get("schema")=="onklav.game.save.v2":
		var saved:Dictionary=storage.load_local(path)
		if not saved.ok:return saved
		raw=saved.state
	return validate(raw)

static func validate(raw:Variant)->Dictionary:
	if not raw is Dictionary or raw.get("schema") != "onklav.current_game.v1" or raw.get("game") != "ah2" or raw.get("mode") != "solo":
		return {"ok": false, "error": "The saved match is damaged or unsupported."}
	if not raw.get("match") is Dictionary or not raw.get("rink") is Dictionary:
		return {"ok": false, "error": "The saved match is incomplete."}
	var state = MatchState.new()
	if not state.restore(raw.match) or not valid_rink(raw.rink):
		return {"ok": false, "error": "The saved match contains invalid state."}
	return {"ok": true, "active": state.phase != MatchState.Phase.FINISHED, "record": raw}

static func valid_rink(rink: Dictionary) -> bool:
	for key in ["x", "y", "vx", "vy", "player", "opponent", "ai_velocity", "ai_target", "tick", "ai_hits"]:
		var n: Variant = rink.get(key)
		if not (n is int or n is float) or not is_finite(float(n)):
			return false
	var difficulty: Variant = rink.get("difficulty",Rink.Difficulty.NORMAL)
	if not (difficulty is int or difficulty is float) or not is_finite(float(difficulty)) or difficulty != floor(float(difficulty)) or difficulty < Rink.Difficulty.EASY or difficulty > Rink.Difficulty.HARD:
		return false
	return float(rink.x) >= -0.1 and float(rink.x) <= 1.1 and float(rink.y) >= -0.1 and float(rink.y) <= 1.1 and absf(rink.vx) <= 2 and absf(rink.vy) <= 2 and rink.player >= 0.1 and rink.player <= 0.9 and rink.opponent >= 0.1 and rink.opponent <= 0.9 and absf(rink.ai_velocity) <= [.24,.38,.52][int(difficulty)] and rink.ai_target >= 0.1 and rink.ai_target <= 0.9 and rink.tick == floor(rink.tick) and rink.ai_hits == floor(rink.ai_hits) and rink.tick >= 0 and rink.tick <= 1000000000 and rink.ai_hits >= 0 and rink.ai_hits <= 10000000

static func save_current(state: RefCounted, rink: Dictionary, path: String = CURRENT) -> Error:
	if state.phase==MatchState.Phase.FINISHED:return clear(path)
	if not MatchState.new().restore(state.snapshot()) or not valid_rink(rink):return ERR_INVALID_DATA
	return helper().save_match({"schema":"onklav.current_game.v1","game":"ah2","mode":"solo","match":state.snapshot(),"rink":rink},path)

static func clear(path: String = CURRENT) -> Error:
	if helper().host_storage:return helper().clear_match(path)
	if FileAccess.file_exists(path):
		var error := DirAccess.remove_absolute(path)
		if error != OK:
			return error
	if FileAccess.file_exists(path + ".tmp"):
		return DirAccess.remove_absolute(path + ".tmp")
	return OK
