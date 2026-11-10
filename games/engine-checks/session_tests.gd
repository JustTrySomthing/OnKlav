extends SceneTree
var checks := 0
var failures := 0
var value := 7
const PATH := "user://onklav-session-contract-test.json"
func check(ok: bool, message: String) -> void:
	checks += 1
	if not ok: failures += 1; push_error(message)
func snapshot() -> Dictionary: return {"value":value}
func restore(data: Dictionary) -> bool:
	if not data.get("value") is float and not data.get("value") is int: return false
	value = int(data.value); return true
func reset() -> void: value = 0
func _initialize() -> void: call_deferred("run")
func run() -> void:
	var helper = root.get_node("OnKlavGame")
	helper.clear_match(PATH)
	var scene := Control.new(); root.add_child(scene)
	helper.attach_session(scene, snapshot, restore, reset, "Test game", PATH)
	check(helper.session_ready and helper.paused and not helper.session_started, "fresh game offers start menu without checkpoint")
	check(not FileAccess.file_exists(PATH), "opening a start menu does not create saved gameplay")
	helper.start_mode("Single player")
	check(not helper.paused and helper.session_started, "choosing supported mode starts play")
	check(helper.chrome.get_children().filter(func(child): return child is Button).size() == 6, "six controls and one expanding space")
	var names: Array[String] = []
	for child in helper.chrome.get_children():
		if child is Button: names.append(child.name)
	check(names == ["Close", "Return", "Pause", "Help", "Audio", "Settings"], "standard control order without title")
	helper.chrome.dispatch("pause")
	check(helper.paused and helper.chrome.dialog == null, "Pause toggles without dialogue")
	helper.chrome.dispatch("pause")
	check(not helper.paused and helper.chrome.dialog == null, "Play toggles back without dialogue")
	helper.confirm_quit()
	var choices: Array = helper.chrome.dialog.find_children("*", "Button", true, false)
	check(choices.size() == 2 and choices[0].text == "Resume" and choices[1].text == "Quit", "quit has exactly Resume left and Quit right")
	check(choices[0].get_parent() is HBoxContainer and choices[0].get_parent() == choices[1].get_parent(), "quit actions share a horizontal row")
	check(choices[0].get_theme_stylebox("normal").bg_color.is_equal_approx(Color("7ee9c8")) and choices[1].get_theme_stylebox("normal").bg_color.is_equal_approx(Color("ff6c8d")), "quit actions are green and red")
	choices[0].pressed.emit()
	check(not helper.paused, "quit Resume resumes without another menu")
	value = 19
	check(helper.checkpoint(), "checkpoint commits")
	check(helper.pause_game() and paused and helper.overlay == null, "pause checkpoints silently without a menu")
	check(helper.chrome.paused_state, "paused state selects the play icon")
	helper.resume_game()
	helper.chrome.dispatch("audio")
	check(paused and helper.overlay == null and helper.chrome.temporary_open, "audio pauses silently with no underlying pause menu")
	helper.chrome.finish_temporary()
	check(not paused and not helper.chrome.paused_state, "audio Done returns directly to gameplay")
	helper.chrome.dispatch("settings")
	check(paused and helper.overlay == null, "settings has no underlying pause menu")
	helper.chrome.finish_temporary()
	check(not paused, "settings Done resumes gameplay")
	helper.pause_game()
	helper.resume_game(); check(not paused and not helper.paused, "resume remains available")
	check(helper.load_match(PATH).state.value == 19, "exact progress retained")
	scene.free(); value = 0
	scene = Control.new(); root.add_child(scene)
	helper.attach_session(scene, snapshot, restore, reset, "Test game", PATH)
	check(value == 19 and helper.paused and paused, "fresh scene restores into explicit resume menu")
	helper.choose_new()
	check(helper.load_match(PATH).state.value == 19, "New keeps previous save until a mode is chosen")
	helper.new_game()
	check(value == 0 and not paused and helper.load_match(PATH).state.value == 0, "new game clears old progress and becomes playable")
	value=99;helper.replay()
	check(value==0 and not paused and helper.chrome.dialog==null and helper.load_match(PATH).state.value==0,"result replay starts and saves directly with no entry/help dialog")
	var file := FileAccess.open(PATH, FileAccess.WRITE); file.store_string("broken"); file.close()
	scene.free(); scene = Control.new(); root.add_child(scene)
	helper.attach_session(scene, snapshot, restore, reset, "Test game", PATH)
	check(not helper.can_save and helper.paused and paused, "corrupt checkpoint offers recovery")
	helper.resume_game(); check(paused, "invalid checkpoint cannot silently resume a fresh game")
	check(FileAccess.get_file_as_string(PATH) == "broken", "corrupt original retained until explicit choice")
	helper.new_game(); check(helper.can_save and not paused and helper.load_match(PATH).ok, "explicit new game recovers from corruption")
	helper.chrome.muted = true; helper.chrome.apply_preferences()
	check(AudioServer.is_bus_mute(0), "mute controls actual master bus")
	helper.chrome.muted = false; helper.chrome.volume = .35; helper.chrome.music = false; helper.chrome.apply_preferences()
	check(is_equal_approx(db_to_linear(AudioServer.get_bus_volume_db(0)), .35), "volume controls actual master bus")
	check(AudioServer.is_bus_mute(AudioServer.get_bus_index("Music")), "music toggle controls music bus")
	helper.session_ready = false; helper.clear_match(PATH); paused = false; scene.free()
	if is_instance_valid(helper.overlay): helper.overlay.queue_free()
	if is_instance_valid(helper.chrome_layer): helper.chrome_layer.queue_free()
	await process_frame
	print("GAME_SESSION_RESULT checks=%d failures=%d" % [checks, failures])
	quit(1 if failures else 0)
