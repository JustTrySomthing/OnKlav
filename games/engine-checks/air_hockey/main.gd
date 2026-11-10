extends Control
## Trusted, bundled solo port. Friend transport and untrusted packages are separate.
const MatchState = preload("res://air_hockey/match.gd")
const Store = preload("res://air_hockey/store.gd")
const Sounds = preload("res://air_hockey/audio.gd")
const Chrome = preload("res://game_chrome.gd")
const Rink = preload("res://air_hockey/rink.gd")
var state = MatchState.new()
var session
var rink
var canvas: Control
var column: VBoxContainer
var score: Control
var serve_button: Button
var result: VBoxContainer
var chrome
var overlay: Control
var modal_tween: Tween
var muted := false
var checkpoint_time := 0.0
var tone
var exiting := false
var resuming := false
var resume_token := 0
var save_error: Label
var loaded := false
var requested_game := "hockey"
var checkpoint_path := Store.CURRENT
var load_result: Dictionary = {}
var session_started := false


class ScoreBar extends Control:
	var model
	var pulse := 0.0
	var side := 0
	var chips: Array[StyleBoxFlat] = []
	func _ready() -> void:
		for i in range(2):
			var chip:=StyleBoxFlat.new()
			chip.set_corner_radius_all(10)
			chip.set_border_width_all(1)
			chips.append(chip)
	func _process(delta: float) -> void:
		if pulse > 0:
			pulse = maxf(0, pulse - delta)
			queue_redraw()
	func _draw() -> void:
		if model == null:
			return
		var font := ThemeDB.fallback_font
		var top := (size.y - 56.0) * 0.5
		for i in range(2):
			var ink := Color("609aff") if i == 0 else Color("ff6c8d")
			var x := 40.0 if i == 0 else size.x - 40
			var chip := Rect2(x-32,top,64,56)
			chips[i].bg_color=Color(ink,.08 if side!=i else .12)
			chips[i].border_color=Color(ink,.38+pulse*.45 if side==i else .32)
			draw_style_box(chips[i],chip)
			if pulse > 0 and side == i:
				draw_circle(Vector2(x, top + 23), 25 + pulse * 5, Color(ink, pulse * .16))
			draw_string(font, Vector2(x-16, top + 32), str(model.scores[i]), HORIZONTAL_ALIGNMENT_CENTER, 32, 34, ink.lightened(.30))
			draw_string(font, Vector2(x - 29, top + 49), "YOU" if i == 0 else "RIVAL", HORIZONTAL_ALIGNMENT_CENTER, 58, 11, Color("e6f4ff"))
		var title := "FINAL SCORE" if model.phase == 2 else "ROUND %d OF 10" % model.round_number()
		var width := font.get_string_size(title, HORIZONTAL_ALIGNMENT_LEFT, -1, 13).x
		draw_string(font, Vector2((size.x - width) / 2, top + 21), title, HORIZONTAL_ALIGNMENT_LEFT, -1, 13, Color("c3dfeb"))
		for i in range(10):
			var ink:=Color("294859")
			if i<model.scores[0]+model.scores[1]:
				var scorer:int=model.goal_order[i] if i<model.goal_order.size() else -1
				ink=Color("82baff") if scorer==0 else Color("ff91aa") if scorer==1 else Color("91a7b9")
			draw_circle(Vector2(size.x / 2 - 49.5 + i * 11, top + 40), 3, ink, true, -1, true)

class ResultPanel extends VBoxContainer:
	var age:=0.0
	var reduced:=false
	func _process(dt:float):
		if not visible:return
		age+=dt;pivot_offset=size*.5
		var t:=minf(1,age/.5);scale=Vector2.ONE*(1 if reduced else 1+pow(2,-7*t)*sin((t-.2)*TAU)*.1 if t<1 else 1)
		modulate.a=minf(1,age/.2)

func style(color: String, edge: String = "294c60", radius: int = 14) -> StyleBoxFlat:
	var box := StyleBoxFlat.new()
	box.bg_color = Color(color)
	box.border_color = Color(edge)
	box.set_border_width_all(1)
	box.set_corner_radius_all(radius)
	box.content_margin_left = 12
	box.content_margin_right = 12
	box.content_margin_top = 6
	box.content_margin_bottom = 6
	return box

func label(text: String, font_size: int = 20) -> Label:
	var item := Label.new()
	item.text = text
	item.add_theme_font_size_override("font_size", font_size)
	item.add_theme_color_override("font_color", Color("e4f6ff"))
	item.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	item.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	return item

func button(text: String, callback: Callable, width: float = 48) -> Button:
	var item := Button.new()
	item.text = text
	item.custom_minimum_size = Vector2(width, 44)
	item.add_theme_font_size_override("font_size", 18)
	item.add_theme_stylebox_override("normal", style("102638"))
	item.add_theme_stylebox_override("hover", style("193e53", "38d4ea"))
	item.add_theme_stylebox_override("pressed", style("24556c", "38d4ea"))
	item.add_theme_stylebox_override("disabled", style("101d2b"))
	item.add_theme_color_override("font_color", Color("e4f6ff"))
	item.pressed.connect(callback)
	return item

func _ready() -> void:
	session=get_tree().root.get_node("OnKlavGame")
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	get_tree().auto_accept_quit = false
	get_tree().quit_on_go_back = false
	var backdrop := ColorRect.new()
	backdrop.color = Color("070e18")
	backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	backdrop.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(backdrop)
	canvas = Control.new()
	add_child(canvas)
	column = VBoxContainer.new()
	column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	column.offset_left = 4
	column.offset_right = -4
	column.offset_top = 4
	column.offset_bottom = -4
	column.add_theme_constant_override("separation", 4)
	canvas.add_child(column)
	chrome = Chrome.new()
	chrome.close_requested.connect(confirm_quit)
	chrome.return_requested.connect(return_to_onklav)
	chrome.pause_requested.connect(toggle_pause)
	chrome.silent_pause_requested.connect(show_pause)
	chrome.resume_requested.connect(resume)
	chrome.entry_requested.connect(show_entry)
	chrome.help_requested.connect(show_help)
	chrome.preferences_changed.connect(func():
		muted = chrome.muted
		if is_instance_valid(rink):
			rink.reduced_effects = chrome.reduce_motion
		if is_instance_valid(result):result.reduced=chrome.reduce_motion)
	column.add_child(chrome)
	var score_panel := PanelContainer.new()
	score_panel.custom_minimum_size.y = 68
	score_panel.add_theme_stylebox_override("panel", style("233c4d", "456474", 15))
	column.add_child(score_panel)
	score = ScoreBar.new()
	score.model = state
	score_panel.add_child(score)
	rink = Rink.new()
	rink.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(rink)
	rink.point.connect(on_point)
	rink.sound.connect(play_tone)
	rink.reduced_effects = chrome.reduce_motion
	var center := CenterContainer.new()
	center.custom_minimum_size.y = 52
	column.add_child(center)
	serve_button = button("Serve", serve, 172)
	serve_button.add_theme_stylebox_override("normal",style("11604b","4b9e82"))
	serve_button.add_theme_stylebox_override("hover",style("17735a","6abf9d"))
	serve_button.add_theme_stylebox_override("pressed",style("0b4c3c","4b9e82"))
	serve_button.add_theme_stylebox_override("focus",style("11604b","8ce2ba"))
	serve_button.add_theme_color_override("font_color",Color("f3fff8"))
	serve_button.add_theme_color_override("font_hover_color",Color("ffffff"))
	serve_button.add_theme_color_override("font_pressed_color",Color("ffffff"))
	center.add_child(serve_button)
	var result_center := CenterContainer.new()
	result_center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	result_center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	rink.add_child(result_center)
	result = ResultPanel.new()
	result.custom_minimum_size.x = 320
	result.add_theme_constant_override("separation", 14)
	result.visible = false
	result_center.add_child(result)
	save_error = label("", 13)
	save_error.visible = false
	column.add_child(save_error)
	tone = Sounds.new()
	add_child(tone)
	resized.connect(layout)
	if OS.has_feature("web"): get_viewport().size_changed.connect(layout, CONNECT_DEFERRED)
	layout()
	session.save_failed.connect(func(message:String):
		rink.set_paused(true);tone.stop();chrome.set_paused(true);save_error.text=message;save_error.show())
	session.init_storage()
	session.resume_check=func(data:Dictionary):return data.match.phase!=MatchState.Phase.FINISHED
	if Chrome.consume_fresh_start():
		loaded = true
		show_difficulty(); return
	load_result = Store.read_current(checkpoint_path)
	if not load_result.ok:
		show_modal("Saved match needs attention", str(load_result.error), [["Discard saved match", discard_save], ["Exit", exit_game]])
		return
	if load_result.active:
		state.restore(load_result.record.match)
		rink.restore(load_result.record.rink, state.phase == MatchState.Phase.PLAYING)
		loaded = true
		refresh()
		session_started = true
		if Chrome.consume_resume_start(): resume()
		else: show_entry()
	else:
		loaded = true
		refresh()
		show_start_menu()
func layout() -> void:
	if not is_instance_valid(canvas): return
	Chrome.fit_board(canvas, size, 0, 540)
	if is_instance_valid(result):
		result.custom_minimum_size.x = minf(320, canvas.size.x - 64)

func _process(delta: float) -> void:
	if not loaded:
		return
	tone.music(chrome, rink.paused or state.phase==MatchState.Phase.FINISHED)
	checkpoint_time += delta
	if checkpoint_time >= 4 and not rink.paused and not rink.stopped:
		checkpoint_time = 0
		persist()
	if state.phase == MatchState.Phase.WAITING and not rink.paused and not chrome.reduce_motion:
		serve_button.modulate = Color.WHITE * (0.94 + sin(Time.get_ticks_msec() / 1000.0 * TAU) * 0.06)
	else:
		serve_button.modulate = Color.WHITE

func refresh() -> void:
	score.model = state
	score.queue_redraw()
	serve_button.disabled = state.phase != MatchState.Phase.WAITING or rink.paused
	serve_button.text = "Paused" if rink.paused and state.phase == MatchState.Phase.PLAYING else "Rally in play" if state.phase == MatchState.Phase.PLAYING else "Serve"
	serve_button.visible = state.phase != MatchState.Phase.FINISHED
	result.visible = state.phase == MatchState.Phase.FINISHED
	result.reduced=chrome.reduce_motion
	if result.visible and result.get_child_count() == 0:
		result.age=0
		result.add_child(label("MATCH COMPLETE", 13))
		var headline:=label("VICTORY!" if state.scores[0]>state.scores[1] else "RIVAL WINS" if state.scores[0]<state.scores[1] else "MATCH DRAWN",30);headline.add_theme_color_override("font_color",Color("8fddff") if state.scores[0]>=state.scores[1] else Color("ffadc4"));headline.add_theme_color_override("font_shadow_color",Color("020b14"));headline.add_theme_constant_override("shadow_offset_y",3);result.add_child(headline)
		result.add_child(label("%d  —  %d" % [state.scores[0], state.scores[1]], 32))
		var again := button("Play Again", show_difficulty)
		again.add_theme_stylebox_override("normal", style("7ee9c8", "b2ffdf"))
		again.add_theme_color_override("font_color", Color("062b26"))
		result.add_child(again)
		result.add_child(button("Close", exit_game))

func serve() -> void:
	if rink.paused or not state.serve():
		return
	rink.serve(state.session.hash() + state.rally)
	rink.grab_focus()
	refresh()
	persist()
	play_tone(0)

func on_point(side: int) -> void:
	if state.goal(side):
		var goal_power:=clampf((rink.puck.linear_velocity.length()-320.0)/850.0,0.0,1.0)
		rink.celebrate(side)
		rink.final_message=state.phase==MatchState.Phase.FINISHED
		score.side = side
		score.pulse = .65
		refresh()
		persist()
		play_tone((3 if state.scores[0] > state.scores[1] else 6 if state.scores[0] < state.scores[1] else 7) if state.phase == MatchState.Phase.FINISHED else 4 if side == 0 else 5,goal_power)

func persist() -> bool:
	if exiting: return true
	if not loaded:
		return false
	if not session_started:
		return true
	var error := Store.save_current(state, rink.snapshot(), checkpoint_path)
	save_error.visible = error != OK
	save_error.text = "Could not save this match. Free storage and pause to retry." if error != OK else ""
	if error == OK and OS.has_feature("web"):
		JavaScriptBridge.force_fs_sync()
	return error == OK

func resume() -> void:
	if not session.last_save_error.is_empty():return
	if not session_started: show_start_menu(); return
	get_tree().paused=false
	session.paused=false
	chrome.close_dialog()
	dismiss_modal()
	rink.set_paused(false)
	chrome.set_paused(false)
	refresh()
	rink.grab_focus()

func dismiss_modal() -> void:
	resume_token += 1
	resuming = false
	if is_instance_valid(modal_tween): modal_tween.kill()
	if is_instance_valid(overlay): overlay.queue_free()
	overlay = null

func show_pause() -> void:
	dismiss_modal()
	rink.set_paused(true)
	tone.stop()
	chrome.set_paused(true)
	if loaded: persist()
	refresh()

func toggle_pause() -> void:
	if not session_started: show_start_menu(); return
	if rink.paused: resume()
	else:
		show_pause()
		chrome.show_pause_notice(true)

func show_entry() -> void:
	show_pause()
	if not loaded:
		show_modal("Saved match needs attention", str(load_result.get("error", "Choose New to replace the saved match.")), [["New", show_start_menu], ["Return to OnKlav", func(): Chrome.return_to_onklav()]])
		return
	if not session_started: show_start_menu(); return
	chrome.choice("Would you like to resume the current game or start a new game?", "New", choose_new, "Resume", resume)
	var boxes=chrome.dialog.find_children("*","VBoxContainer",true,false)
	if not boxes.is_empty():boxes[0].add_child(Chrome.action("Cancel",return_to_onklav))

func choose_new() -> void:
	show_pause()
	chrome.choice("Start a new game? Your saved session will be replaced after the new game saves successfully.","Cancel",show_entry,"New",prepare_new)

func prepare_new() -> void:
	session.replacing_save=true
	session_started=false
	loaded=true
	chrome.close_dialog()
	if not Chrome.request_start_menu():show_start_menu()

func show_start_menu() -> void:
	show_pause()
	if Chrome.request_start_menu(): return
	var content: VBoxContainer = chrome.dialog_box("Air Hockey")
	content.add_child(Chrome.caption("Slide your paddle to defend your pocket and score on your rival. The match lasts ten goals.", 17))
	content.add_child(Chrome.action("Single player", show_difficulty))
	content.add_child(Chrome.action("Cancel", return_to_onklav))

func show_difficulty() -> void:
	show_pause()
	var content: VBoxContainer = chrome.dialog_box("")
	content.add_theme_constant_override("separation", 12)
	var title := label("Choose difficulty", 25)
	title.add_theme_color_override("font_color", Color("8fddff"))
	content.add_child(title)

	var names := ["Easy", "Normal", "Hard"]
	var descriptions := ["Slower reactions", "Balanced pace", "Fast reactions, tighter defense"]
	var accents := ["7ee9c8", "82baff", "ff91aa"]
	var fills := ["173d39", "203d58", "492b40"]
	for choice in [Rink.Difficulty.EASY, Rink.Difficulty.NORMAL, Rink.Difficulty.HARD]:
		var option := VBoxContainer.new()
		option.add_theme_constant_override("separation", 3)
		content.add_child(option)
		var pick := button(names[choice], func(): start_single_player(choice))
		pick.custom_minimum_size.y = 46
		var accent: String = accents[choice]
		var fill: String = fills[choice]
		pick.add_theme_stylebox_override("normal", style(fill, accent))
		pick.add_theme_stylebox_override("hover", style(Color(fill).lightened(.10).to_html(), accent))
		pick.add_theme_stylebox_override("pressed", style(Color(fill).darkened(.14).to_html(), accent))
		var focus := style(fill, "e4f6ff")
		focus.draw_center = false
		focus.set_border_width_all(2)
		pick.add_theme_stylebox_override("focus", focus)
		option.add_child(pick)
		var description := label(descriptions[choice], 14)
		description.add_theme_color_override("font_color", Color("b6cddd"))
		option.add_child(description)

	content.add_child(Chrome.action("Cancel", resume if session_started else return_to_onklav))

func start_single_player(difficulty: Rink.Difficulty = Rink.Difficulty.NORMAL) -> void:
	rink.difficulty = difficulty
	await restart()

func return_to_onklav() -> void:
	show_pause()
	if not loaded or (persist() and await session.flush_saves()): Chrome.return_to_onklav()
	else: show_modal("Could not save",session.last_save_error,[["Retry save",return_to_onklav],["Stay here",show_pause]])

func show_help() -> void:
	show_modal("How to play", "Slide left or right in the lower half to move your paddle. Press Serve between goals. Ten goals complete the match; most goals wins. A 5–5 result is a draw. Choose Easy, Normal or Hard at the start of each match.\n\nKeyboard: left / right arrows or A / D. Touch controls stay with the first finger.", [["Done", resume]])

func confirm_restart() -> void: choose_new()

func restart() -> void:
	session.replacing_save=true
	loaded = true
	session_started = true
	chrome.close_dialog()
	resume_token += 1
	resuming = false
	state = MatchState.new()
	for child in result.get_children():
		result.remove_child(child)
		child.queue_free()
	rink.stopped = true
	rink.puck.freeze = true
	rink.puck.place(Rink.DIM / 2, Vector2.ZERO)
	rink.puck.ai_hits = 0
	rink.tick = 0
	rink.ai_velocity = 0
	rink.ai_target = 0.5
	rink.target = 0.5
	rink.player.position.x = 300
	rink.opponent.position.x = 300
	rink.trace.clear()
	rink.goal_time = 0
	rink.impacts.clear();rink.reset_render()
	rink.set_paused(true)
	refresh()
	if persist() and await session.flush_saves(): resume()
	else: show_modal("Could not save","Your previous save is safe.",[["Retry",return_to_onklav]])

func confirm_quit() -> void:
	show_pause()
	chrome.choice("Are you sure you want to quit? You will lose your progress.", "Resume", resume, "Quit", quit_now, true)

func quit_now() -> void:
	if exiting: return
	exiting = true
	if not await session.flush_saves():
		exiting = false
		show_modal("Could not quit", "Pending saves could not finish. Retry before discarding.", [["Back", confirm_quit]])
		return
	if Store.clear(checkpoint_path) == OK and await session.flush_saves():
		exiting = false
		exit_game()
	else:
		exiting = false
		show_modal("Could not quit", "The checkpoint could not be cleared.", [["Back", confirm_quit]])

func discard_save() -> void: choose_new()

func exit_game() -> void:
	if exiting:
		return
	exiting = true
	if is_instance_valid(modal_tween):
		modal_tween.kill()
	rink.set_paused(true)
	tone.stop()
	var tree := get_tree()
	if Chrome.hosted():
		tree.paused = true
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_EXIT")')
		return
	if OS.has_feature("web"):
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_EXIT")')
		# Let stopped sample voices drain before the shared engine shuts down.
		tree.create_timer(.12).timeout.connect(tree.quit)
		queue_free()
	else:
		tree.quit()

func show_modal(title: String, message: String, actions: Array) -> void:
	show_pause()
	var box: VBoxContainer = chrome.dialog_box(title)
	box.add_child(Chrome.caption(message, 17))
	for entry in actions: box.add_child(Chrome.action(entry[0], entry[1]))

func close_modal() -> void:
	dismiss_modal()

func _notification(what: int) -> void:
	if what in [NOTIFICATION_APPLICATION_PAUSED, NOTIFICATION_APPLICATION_FOCUS_OUT]:
		if loaded and not exiting and is_instance_valid(rink):
			show_pause()
	elif what in [NOTIFICATION_WM_GO_BACK_REQUEST, NOTIFICATION_WM_CLOSE_REQUEST]:
		if loaded:
			return_to_onklav()

func play_tone(kind: int, strength: float = .55) -> void:
	if not muted and not rink.paused:
		tone.play(kind,strength)

func _exit_tree() -> void:
	if is_instance_valid(tone):
		tone.stop()
