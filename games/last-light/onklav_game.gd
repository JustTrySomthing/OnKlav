extends Node
## Standard lifecycle: explicit resume/quit, bounded checkpoints, one chrome row.
signal suspended
signal resumed
signal save_failed(message: String)
const Chrome = preload("res://game_chrome.gd")
const SAVE := "user://match.json"
const LIMIT := 2 * 1024 * 1024
var paused := false
var overlay: CanvasLayer
var chrome
var chrome_layer: CanvasLayer
var chrome_root: Control
var content: Control
var game_scene: Control
var snapshotter: Callable
var restorer: Callable
var starter: Callable
var quit_checkpoint: Callable
var help_text := ""
var path := SAVE
var can_save := true
var session_ready := false
var elapsed := 0.0
var session_started := false
var modes: Dictionary = {}
var selected_mode := "Single player"

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	get_tree().auto_accept_quit = false
	get_tree().quit_on_go_back = false

# Host storage protocol v2. The host binds every command to this renderer's game/publisher.
# Existing user:// checkpoints are imported only when the host has never recorded this slot.
var host_storage := false
var host_boot: Dictionary = {}
var save_requests: Dictionary = {}
var save_serial := 0
var last_save_error := ""
var save_errors: Dictionary = {}
var replacing_save := false
var payload_version := 1
var resume_check: Callable

func init_storage() -> void:
	if not OS.has_feature("web") or host_storage: return
	var text: Variant = JavaScriptBridge.eval("JSON.stringify(window.onklavSaveBoot || null)")
	var value: Variant = JSON.parse_string(str(text))
	if value is Dictionary and value.get("protocol") == 2:
		host_storage = true
		host_boot = value

func storage_request(op: String, slot: String, state: Dictionary = {}, resumable: bool = true) -> Error:
	init_storage()
	if not host_storage: return ERR_UNAVAILABLE
	if save_requests.size() >= 16: return ERR_BUSY
	var command := {"request":save_serial+1,"op":op,"slot":slot,"state":state,"version":payload_version,"resumable":resumable,"replace":replacing_save}
	var text := JSON.stringify(command)
	if text.to_utf8_buffer().size() > LIMIT + 8192: return ERR_OUT_OF_MEMORY
	save_serial += 1
	save_requests[save_serial] = {"command":command,"sent":Time.get_ticks_msec()}
	JavaScriptBridge.eval("console.log(" + JSON.stringify("ONKLAV_SAVE|"+text) + ")")
	return OK

func poll_saves() -> void:
	if not host_storage: return
	for id in save_requests.keys():
		if save_requests[id].command.op == "load": continue
		var value: Variant = JavaScriptBridge.eval("JSON.stringify(window.onklavSaveReplies["+str(id)+"] || null)")
		var response: Variant = JSON.parse_string(str(value))
		if not response is Dictionary: continue
		JavaScriptBridge.eval("delete window.onklavSaveReplies["+str(id)+"]")
		var command: Dictionary = save_requests[id].command
		save_requests.erase(id)
		if not response.get("ok",false):
			last_save_error = str(response.get("error","Could not save progress."))
			save_errors[command.slot] = last_save_error
			save_failed.emit(last_save_error)
			paused = true; get_tree().paused = true
			if is_instance_valid(chrome): chrome.set_paused(true)
			suspended.emit()
			if is_instance_valid(chrome): show_save_problem()
		else:
			save_errors.erase(command.slot)
			last_save_error = "" if save_errors.is_empty() else str(save_errors.values()[0])
			if command.slot != "session": continue
			if command.op == "save":
				host_boot = {"ok":true,"known":true,"exists":true,"record":{"state":command.state,"resumable":command.resumable}}
				replacing_save = false
			elif command.op == "clear": host_boot = {"ok":true,"known":true,"exists":false}

func flush_saves() -> bool:
	var until := Time.get_ticks_msec()+10000
	while not save_requests.is_empty() and Time.get_ticks_msec()<until:
		poll_saves()
		await get_tree().process_frame
	if not save_requests.is_empty(): last_save_error = "Saving has not finished. Keep this game open and retry."
	return save_requests.is_empty() and last_save_error.is_empty()

# Named durable data slots are separate from the automatic resume slot; e.g. data.profile.
func valid_data_name(name: String) -> bool:
	if name.length()<1 or name.length()>64 or name[0] not in "abcdefghijklmnopqrstuvwxyz":return false
	for letter in name:
		if letter not in "abcdefghijklmnopqrstuvwxyz0123456789_":return false
	return true

func save_data(name: String, state: Dictionary) -> bool:
	init_storage()
	if not valid_data_name(name): return false
	if host_storage:
		if storage_request("save","data."+name,state,false)!=OK: return false
		return await flush_saves()
	return save_local(state,"user://onklav-data-"+name+".json") == OK

func delete_data(name: String) -> bool:
	init_storage()
	if not valid_data_name(name):return false
	if host_storage:
		if storage_request("clear","data."+name)!=OK:return false
		return await flush_saves()
	return clear_match("user://onklav-data-"+name+".json")==OK

func load_data(name: String) -> Dictionary:
	init_storage()
	if not valid_data_name(name): return {"ok":false,"error":"Invalid slot name"}
	if not host_storage: return load_local("user://onklav-data-"+name+".json")
	var id := save_serial+1
	if storage_request("load","data."+name)!=OK: return {"ok":false,"error":"Storage is busy"}
	var until := Time.get_ticks_msec()+10000
	while Time.get_ticks_msec()<until:
		var value: Variant = JSON.parse_string(str(JavaScriptBridge.eval("JSON.stringify(window.onklavSaveReplies["+str(id)+"] || null)")))
		if value is Dictionary:
			save_requests.erase(id)
			JavaScriptBridge.eval("delete window.onklavSaveReplies["+str(id)+"]")
			if value.get("ok",false) and value.get("exists",false): value["state"] = value.record.state
			return value
		await get_tree().process_frame
	save_requests.erase(id)
	return {"ok":false,"error":"Storage read timed out"}

func save_match(state: Dictionary, save_path: String = SAVE) -> Error:
	init_storage()
	if host_storage:
		var resumable: bool = resume_check.call(state) if resume_check.is_valid() else true
		return storage_request("save","session",state,resumable)
	return save_local(state,save_path)

func save_local(state: Dictionary, save_path: String) -> Error:
	var payload := JSON.stringify(state)
	var text := JSON.stringify({"schema":"onklav.game.save.v2","owner":save_path,"state":state,"payload":payload,"checksum":payload.sha256_text()})
	if text.to_utf8_buffer().size()>LIMIT: return ERR_OUT_OF_MEMORY
	var file := FileAccess.open(save_path+".tmp",FileAccess.WRITE)
	if file==null: return FileAccess.get_open_error()
	file.store_string(text);file.flush()
	var error := file.get_error();file.close()
	if error!=OK: return error
	var old := load_local(save_path)
	if old.get("ok",false) and old.get("exists",false):
		error = DirAccess.copy_absolute(save_path,save_path+".previous")
		if error!=OK: return error
	error = DirAccess.rename_absolute(save_path+".tmp",save_path)
	if error==OK and OS.has_feature("web"): JavaScriptBridge.force_fs_sync()
	return error

func load_match(save_path: String = SAVE) -> Dictionary:
	init_storage()
	if host_storage:
		if not host_boot.get("ok",false): return {"ok":false,"error":str(host_boot.get("error","Saved progress needs attention."))}
		if host_boot.get("known",host_boot.get("exists",false)):
			if not host_boot.get("exists",false) or not host_boot.record.get("resumable",true): return {"ok":true,"exists":false}
			return {"ok":true,"exists":true,"state":host_boot.record.state}
	return load_local(save_path)

func load_local(save_path: String) -> Dictionary:
	if not FileAccess.file_exists(save_path): return {"ok":true,"exists":false}
	var file := FileAccess.open(save_path,FileAccess.READ)
	if file==null or file.get_length()>LIMIT: return {"ok":false,"error":"Saved progress cannot be read."}
	var parser := JSON.new()
	var parsed := parser.parse(file.get_as_text());file.close()
	var value: Variant = parser.data if parsed==OK else null
	if not value is Dictionary or not value.get("state") is Dictionary: return {"ok":false,"error":"Saved progress is damaged and has been retained."}
	if value.get("schema")=="onklav.game.save.v2":
		if value.get("owner")!=save_path or not value.get("payload") is String or value.get("checksum")!=value.payload.sha256_text(): return {"ok":false,"error":"Saved progress failed validation."}
		var decoded: Variant = JSON.parse_string(value.payload)
		if not decoded is Dictionary:return {"ok":false,"error":"Saved progress is invalid."}
		value.state = decoded
	elif value.get("schema")!="onklav.game.save.v1": return {"ok":false,"error":"Unsupported save format; progress retained."}
	return {"ok":true,"exists":true,"state":value.state}

func clear_match(save_path: String = SAVE) -> Error:
	init_storage()
	if host_storage: return storage_request("clear","session")
	for suffix in ["",".tmp",".previous"]:
		if FileAccess.file_exists(save_path+suffix):
			var error := DirAccess.remove_absolute(save_path+suffix)
			if error!=OK: return error
	return OK


func attach_session(scene: Control, snapshot: Callable, restore: Callable, restart: Callable, instructions: String, save_path: String = SAVE, supported_modes: Dictionary = {}, save_options: Dictionary = {}) -> void:
	init_storage()
	payload_version = int(save_options.get("format_version",1))
	resume_check = save_options.get("is_resumable",Callable())
	game_scene = scene
	snapshotter = snapshot
	restorer = restore
	starter = restart
	help_text = instructions
	path = save_path
	modes = supported_modes.duplicate() if not supported_modes.is_empty() else {"Single player":restart}
	for mode in modes:
		assert(modes[mode] is Callable and modes[mode].is_valid(), "Register only implemented game modes")
	selected_mode = str(modes.keys()[0])
	session_started = false
	can_save = true
	session_ready = true
	paused = false
	get_tree().paused = false
	if is_instance_valid(chrome_layer): chrome_layer.queue_free()
	if is_instance_valid(overlay): overlay.queue_free()
	overlay = null
	chrome_layer = CanvasLayer.new()
	chrome_layer.layer = 100
	add_child(chrome_layer)
	chrome_root = Control.new()
	chrome_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	chrome_layer.add_child(chrome_root)
	chrome = Chrome.new()
	chrome.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
	chrome.close_requested.connect(confirm_quit)
	chrome.return_requested.connect(return_to_onklav)
	chrome.pause_requested.connect(toggle_pause)
	chrome.silent_pause_requested.connect(pause_game)
	chrome.resume_requested.connect(resume_game)
	chrome.entry_requested.connect(show_entry)
	chrome.help_requested.connect(show_help)
	chrome_root.add_child(chrome)
	if not game_scene.resized.is_connected(fit_content): game_scene.resized.connect(fit_content)
	wrap_content()
	print("ONKLAV_SESSION_READY")
	if Chrome.consume_fresh_start():
		start_mode(str(modes.keys()[0])); return
	var saved := load_match(path)
	if not saved.ok:
		can_save = false
		show_recovery(str(saved.error))
	elif saved.get("exists", false):
		if restorer.call(saved.state) == true:
			session_started = true
			selected_mode = str(saved.state.get("_onklav_mode", selected_mode))
			if selected_mode not in modes: selected_mode = str(modes.keys()[0])
			if Chrome.consume_resume_start(): resume_game()
			else: show_entry()
		else:
			can_save = false
			show_recovery("Saved progress could not be restored. It is retained until you choose New game.")
	else:
		show_start_menu()
func wrap_content() -> void:
	if not is_instance_valid(game_scene): return
	content = Control.new()
	var children := game_scene.get_children()
	game_scene.add_child(content)
	content.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	for child in children:
		child.reparent(content)
	fit_content()

func is_landscape() -> bool:
	return is_instance_valid(game_scene) and game_scene.size.x > game_scene.size.y

func fit_content() -> void:
	if not is_instance_valid(game_scene) or not is_instance_valid(chrome_root): return
	var width := 432.0
	if OS.has_feature("web"): width = maxf(320, float(JavaScriptBridge.eval("window.innerWidth")))
	var ratio := maxf(game_scene.size.x / width, .01)
	chrome_root.scale = Vector2.ONE * ratio
	chrome_root.size = Vector2(width, game_scene.size.y / ratio)
	var landscape := is_landscape()
	chrome.visible = not landscape
	if is_instance_valid(content): content.offset_top = 0.0 if landscape else 56 * ratio
	game_scene.queue_redraw()

func checkpoint() -> bool:
	if not session_ready or not can_save or not is_instance_valid(game_scene): return false
	if not session_started: return true
	var state: Variant = snapshotter.call()
	if not state is Dictionary: return false
	state = state.duplicate(true)
	state["_onklav_mode"] = selected_mode
	var error := save_match(state, path)
	if error != OK: save_failed.emit("Could not save this game. Your current session remains open.")
	return error == OK

func _process(delta: float) -> void:
	poll_saves()
	if session_ready and not paused and is_instance_valid(game_scene):
		elapsed += delta
		if elapsed >= 2:
			elapsed = 0
			checkpoint()

func sheet(title: String, description: String, actions: Array) -> void:
	if is_instance_valid(overlay): overlay.queue_free()
	overlay = CanvasLayer.new(); overlay.layer = 180; add_child(overlay)
	var screen := Control.new(); overlay.add_child(screen)
	var width := 432.0
	if OS.has_feature("web"): width = maxf(320, float(JavaScriptBridge.eval("window.innerWidth")))
	screen.scale = Vector2.ONE * get_viewport().get_visible_rect().size.x / width
	screen.size = get_viewport().get_visible_rect().size / screen.scale
	var shade := ColorRect.new(); shade.color = Color(0, 0, 0, .75)
	shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); screen.add_child(shade)
	var center := CenterContainer.new(); center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); shade.add_child(center)
	var panel := PanelContainer.new(); panel.custom_minimum_size.x = minf(400, width - 32)
	panel.add_theme_stylebox_override("panel", Chrome.box("102638", "38d4ea", 20)); center.add_child(panel)
	var box := VBoxContainer.new(); box.add_theme_constant_override("separation", 12)
	var scroll := ScrollContainer.new(); scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED; panel.add_child(scroll)
	box.size_flags_horizontal = Control.SIZE_EXPAND_FILL; scroll.add_child(box)
	box.minimum_size_changed.connect(func(): panel.custom_minimum_size.y = minf(box.get_combined_minimum_size().y + 16, maxf(80, screen.size.y - 32)))
	box.add_child(Chrome.caption(title, 24)); box.add_child(Chrome.caption(description, 17))
	for entry in actions: box.add_child(Chrome.action(entry[0], entry[1]))

func dismiss_sheet() -> void:
	if is_instance_valid(overlay): overlay.queue_free()
	overlay = null

func pause_game() -> bool:
	paused = true
	get_tree().paused = true
	dismiss_sheet()
	if is_instance_valid(chrome): chrome.set_paused(true)
	if not session_ready: return true
	suspended.emit()
	return checkpoint()

func toggle_pause() -> void:
	if not session_started: show_start_menu(); return
	if paused: resume_game()
	else:
		pause_game()
		chrome.show_pause_notice(true)

func resume_game() -> void:
	if not last_save_error.is_empty():show_save_problem();return
	if not can_save: show_recovery("Saved progress needs attention."); return
	if session_ready and not session_started: show_start_menu(); return
	dismiss_sheet()
	if is_instance_valid(chrome): chrome.close_dialog(); chrome.set_paused(false)
	get_tree().paused = false
	paused = false
	resumed.emit()

func show_entry() -> void:
	pause_game()
	if not can_save: show_recovery("Saved progress needs attention."); return
	if not session_started: show_start_menu(); return
	chrome.choice("Would you like to resume the current game or start a new game?", "New", choose_new, "Resume", resume_game)
	var box = chrome.dialog.find_children("*","VBoxContainer",true,false)
	if not box.is_empty(): box[0].add_child(Chrome.action("Cancel", return_to_onklav))

func choose_new() -> void:
	pause_game()
	chrome.choice("Start a new game? Your current saved session will be replaced after the new game saves successfully.", "Cancel", show_entry, "New", prepare_new)

func prepare_new() -> void:
	replacing_save = true
	session_started = false
	can_save = true
	chrome.close_dialog()
	if not Chrome.request_start_menu(): show_start_menu()

func show_start_menu() -> void:
	pause_game()
	if OS.has_feature("web") and bool(JavaScriptBridge.eval("!!window.onklavFriend")):
		start_mode(str(modes.keys()[0])); return
	# The catalog already presented the original game-specific start dialog.
	if Chrome.request_start_menu(): return
	var content: VBoxContainer = chrome.dialog_box(str(ProjectSettings.get_setting("application/config/name", "Game")))
	content.add_child(Chrome.caption(help_text, 17))
	var row := HBoxContainer.new(); row.add_theme_constant_override("separation", 12); content.add_child(row)
	for mode in modes:
		var item := Chrome.action(str(mode), func(): start_mode(str(mode)))
		item.size_flags_horizontal = Control.SIZE_EXPAND_FILL; row.add_child(item)
	content.add_child(Chrome.action("Cancel", return_to_onklav))

func start_mode(mode: String) -> void:
	if mode not in modes: return
	replacing_save = true
	can_save = true
	selected_mode = mode
	modes[mode].call()
	session_started = true
	paused = true; get_tree().paused = true
	if checkpoint() and await flush_saves(): resume_game()
	else: show_save_problem()

func show_help() -> void:
	pause_game()
	sheet("How to play", help_text, [["Done", resume_game]])

func recover_previous() -> void:
	var previous: Variant = host_boot.get("backup")
	if not previous is Dictionary or not previous.get("state") is Dictionary or restorer.call(previous.state)!=true: return
	host_boot = {"ok":true,"known":true,"exists":true,"record":previous}
	replacing_save=true;can_save=true;session_started=true
	show_entry()

func show_recovery(message: String) -> void:
	paused = true; get_tree().paused = true
	if is_instance_valid(chrome): chrome.set_paused(true)
	var actions: Array = [["New", choose_new], ["Return without changing save", func(): Chrome.return_to_onklav()]]
	if host_boot.get("backup") is Dictionary: actions.push_front(["Try previous checkpoint", recover_previous])
	sheet("Saved game needs attention", message, actions)

func replay() -> void:
	# The result-screen action already expresses intent; do not reopen entry/help.
	if not session_ready or not session_started:return
	start_mode(selected_mode)

func confirm_new() -> void: choose_new()
func new_game() -> void: start_mode(str(modes.keys()[0]))

func confirm_quit() -> void:
	pause_game()
	var replay: bool = quit_checkpoint.is_valid() and not quit_checkpoint.call().is_empty()
	var message := "Quit this replay? Your campaign checkpoint will be kept." if replay else "Are you sure you want to quit? You will lose your progress."
	chrome.choice(message, "Resume", resume_game, "Quit", quit_now, true)

func quit_now() -> void:
	if not session_ready: return
	session_ready = false
	if not await flush_saves():
		session_ready = true
		sheet("Could not quit", "Pending saves could not finish. Retry before discarding.", [["Back", confirm_quit]])
		return
	if _save_quit_state() == OK and await flush_saves():
		close_game()
	else:
		session_ready = true
		sheet("Could not quit", "The save could not be updated. You can retry or resume.", [["Back", confirm_quit]])

func _save_quit_state() -> Error:
	var preserved: Dictionary = quit_checkpoint.call() if quit_checkpoint.is_valid() else {}
	if preserved.is_empty():
		return clear_match(path)
	preserved["_onklav_mode"] = selected_mode
	return save_match(preserved, path)

func show_save_problem() -> void:
	sheet("Could not save", last_save_error if not last_save_error.is_empty() else "Your previous save is safe. Keep this game open and retry.", [["Retry save", return_to_onklav], ["Stay here", func(): dismiss_sheet()], ["Leave without saving", func(): Chrome.return_to_onklav()]])

func return_to_onklav() -> void:
	if not can_save:
		Chrome.return_to_onklav(); return
	if pause_game() and await flush_saves(): Chrome.return_to_onklav()
	else: show_save_problem()

func close_game() -> void:
	var tree := get_tree()
	if Chrome.hosted():
		tree.paused = true
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_EXIT")')
		return
	tree.paused = false
	if OS.has_feature("web"):
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_EXIT")')
		tree.create_timer(.12).timeout.connect(tree.quit)
		if is_instance_valid(tree.current_scene): tree.current_scene.queue_free()
	else:
		tree.quit()

func _notification(what: int) -> void:
	if session_ready and is_instance_valid(game_scene) and what in [NOTIFICATION_APPLICATION_PAUSED, NOTIFICATION_APPLICATION_FOCUS_OUT]:
		pause_game()
	elif session_ready and what in [NOTIFICATION_WM_GO_BACK_REQUEST, NOTIFICATION_WM_CLOSE_REQUEST]:
		return_to_onklav()
