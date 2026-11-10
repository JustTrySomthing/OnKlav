extends SceneTree
const MatchState = preload("res://air_hockey/match.gd")
const Store = preload("res://air_hockey/store.gd")
const Rink = preload("res://air_hockey/rink.gd")
const Game = preload("res://air_hockey/main.gd")
const SAVE := "user://air-hockey-test-current.json"
var checks := 0
var failures := 0
var points: Array[int] = []

func check(value: bool, description: String) -> void:
	checks += 1
	if not value:
		failures += 1
		push_error("FAIL: " + description)

func _initialize() -> void:
	call_deferred("run")

func frames(count: int) -> void:
	for i in range(count):
		await physics_frame
	await process_frame

func run() -> void:
	var match_state = MatchState.new()
	check(not match_state.goal(0), "no goal before Serve")
	for i in range(10):
		check(match_state.round_number() == i + 1, "ten rounds in order")
		check(match_state.serve() and not match_state.serve(), "one serve per round")
		check(match_state.goal(i % 2) and not match_state.goal(i % 2), "one point per serve")
	check(match_state.outcome() == "Draw" and match_state.scores == [5, 5], "5–5 draw at ten goals")
	check(not match_state.serve(), "no eleventh round")
	for winner in [0, 1]:
		var finished = MatchState.new()
		for i in range(10):
			finished.serve()
			finished.goal(winner)
		check(finished.phase == MatchState.Phase.FINISHED and finished.outcome() == ("You win the match!" if winner == 0 else "They win the match"), "win and loss outcomes")
	var restored = MatchState.new()
	check(restored.restore(match_state.snapshot()) and restored.scores == [5, 5], "finished model round trip")
	var valid := match_state.snapshot()
	var legacy_history := valid.duplicate(true)
	legacy_history.erase("goal_order")
	check(restored.restore(legacy_history) and restored.goal_order.count(-1)==10, "old checkpoints keep neutral round dots")
	check(restored.restore(JSON.parse_string(JSON.stringify(valid))) and restored.goal_order==match_state.goal_order,"goal colors survive JSON save and restore")
	var bad_history:=valid.duplicate(true)
	bad_history.goal_order[0]=1
	check(not restored.restore(bad_history),"round history cannot exceed scorer totals")
	for field in ["scores", "phase", "rally", "session"]:
		var bad := valid.duplicate(true)
		bad[field] = "corrupt"
		var before := restored.snapshot()
		check(not restored.restore(bad) and restored.snapshot() == before, "invalid " + field + " rejected without mutation")
	var broken := valid.duplicate(true)
	broken.scores = [5.5, 4.5]
	check(not restored.restore(broken), "fractional scores rejected")
	broken = valid.duplicate(true)
	broken.scores = [11, 0]
	check(not restored.restore(broken), "over-complete scores rejected")
	var waiting = MatchState.new()
	broken = waiting.snapshot()
	broken.rally = 4
	check(not restored.restore(broken), "phantom rounds rejected")
	var rink = Rink.new()
	root.add_child(rink)
	rink.size = Vector2(400, 620)
	rink.point.connect(func(side: int): points.append(side))
	await frames(3)
	var initial: Dictionary = rink.snapshot()
	check(Store.valid_rink(initial), "initial rink is saveable")
	var legacy_rink := initial.duplicate()
	legacy_rink.erase("difficulty")
	rink.difficulty = Rink.Difficulty.HARD
	rink.restore(legacy_rink, false)
	check(rink.difficulty == Rink.Difficulty.NORMAL, "old saves default to Normal")
	rink.difficulty = Rink.Difficulty.HARD
	var hard_rink: Dictionary = JSON.parse_string(JSON.stringify(rink.snapshot()))
	check(Store.valid_rink(hard_rink), "Hard difficulty survives JSON save encoding")
	rink.restore(hard_rink, false)
	check(rink.difficulty == Rink.Difficulty.HARD, "Hard difficulty is restored")
	hard_rink.difficulty = 1.5
	check(not Store.valid_rink(hard_rink), "fractional difficulty rejected")
	rink.restore(initial, false)
	check(Store.clear(SAVE) == OK and not Store.read_current(SAVE).active, "no current game initially")
	check(Store.save_current(waiting, initial, SAVE) == OK, "write current game")
	var saved := Store.read_current(SAVE)
	check(saved.ok and saved.active and saved.record.match.session == waiting.session, "read one current game")
	var invalid := initial.duplicate(true)
	invalid.tick = 1.5
	check(not Store.valid_rink(invalid), "fractional simulation tick rejected")
	invalid = initial.duplicate(true)
	invalid.vx = INF
	check(Store.save_current(waiting, invalid, SAVE) == ERR_INVALID_DATA and Store.read_current(SAVE).record == saved.record, "invalid save leaves previous checkpoint intact")
	check(Store.save_current(match_state, initial, SAVE) == OK and not FileAccess.file_exists(SAVE) and not FileAccess.file_exists(SAVE + ".tmp"), "finished match removes current state; no history")
	var file := FileAccess.open(SAVE, FileAccess.WRITE)
	file.store_string("{")
	file.close()
	check(not Store.read_current(SAVE).ok and FileAccess.file_exists(SAVE), "corrupt save reported and retained")
	file = FileAccess.open(SAVE, FileAccess.WRITE)
	file.store_string("x".repeat(Store.LIMIT + 1))
	file.close()
	check(not Store.read_current(SAVE).ok, "oversized checkpoint rejected")
	Store.clear(SAVE)
	# Actual Godot physics, not a replacement collision algorithm.
	rink.serve(3)
	await frames(4)
	check(rink.puck.position.y < Rink.DIM.y * 0.70, "serve travels toward opponent")
	rink.set_paused(true)
	await frames(2) # Allow the in-flight physics tick to reach its freeze boundary.
	var at: Vector2 = rink.puck.position
	await frames(30)
	check(rink.puck.position.distance_to(at) < 0.01, "pause freezes real physics")
	rink.set_paused(false)
	await frames(2)
	check(rink.puck.position.distance_to(at) < 30, "resume has no background catch-up")
	rink.puck.place(Vector2(40, 400), Vector2(-500, -230))
	await frames(20)
	check(rink.puck.linear_velocity.distance_to(Vector2(500, -230)) < 12 and points.is_empty(), "oblique rail bank preserves tangential speed and reflects the normal: %s" % rink.puck.linear_velocity)
	rink.puck.place(Vector2(100, 30), Vector2(0, -500))
	await frames(20)
	check(rink.puck.linear_velocity.y > 0 and points.is_empty(), "end rail outside mouth rebounds puck")
	check(rink.pocket_scorer(Vector2(230,30),Vector2(205,10))==0,
		"fast diagonal puck scores at its swept pocket crossing")
	check(rink.pocket_scorer(Vector2(195,30),Vector2(200,10))==-1,
		"puck overlapping the post does not score at the stripe")
	rink.puck.place(Vector2(216, 22), Vector2(0, -950))
	await frames(20)
	check(points == [0] and rink.stopped, "top goal scores player once")
	await frames(30)
	check(points == [0], "frozen goal cannot score twice")
	rink.serve(3)
	rink.puck.place(Vector2(300, Rink.DIM.y - 20), Vector2(0, 500))
	await frames(20)
	check(points == [0, 1], "bottom goal scores opponent")
	rink.serve(3)
	rink.puck.place(Vector2(322, rink.player.position.y - 75), Vector2(0, 500))
	await frames(12)
	check(rink.puck.linear_velocity.y < 0 and rink.puck.linear_velocity.x > 0, "player collision steers upward by contact offset")
	check(absf(rink.ai_velocity) <= 0.38 and rink.opponent.position.x >= 60 and rink.opponent.position.x <= 540, "AI respects speed and defense line bounds")
	rink.puck.place(Vector2(465,430),Vector2(20,-620))
	rink.update_opponent_plan()
	check(rink.ai_mode=="guard" and rink.ai_target>.5 and rink.ai_target<.8,"rival shades toward an incoming puck without abandoning center")
	rink.puck.place(Vector2(465,rink.opponent.position.y+64),Vector2(0,-120))
	rink.update_opponent_plan()
	check(rink.ai_mode=="chase" and rink.ai_target>.72,"rival commits to a reachable loose puck")
	rink.puck.place(Vector2(465,440),Vector2(0,490))
	rink.update_opponent_plan()
	check(rink.ai_mode=="recover" and absf(rink.ai_target-.5)<.05,"rival recovers toward center when puck leaves")
	var touch := InputEventScreenTouch.new()
	touch.position = Vector2(100, 500)
	touch.index = 4
	touch.pressed = true
	rink.touches=-2 # Browser may dispatch emulated mouse before real finger.
	var grab_start:float=rink.player.position.x / Rink.DIM.x
	rink._gui_input(touch)
	check(rink.touches == 4 and is_equal_approx(rink.target, grab_start), "first lower-half finger grabs without moving paddle")
	var drag:=InputEventScreenDrag.new();drag.index=4;drag.position=Vector2(180,500);rink._gui_input(drag)
	check(is_equal_approx(rink.target,clampf(grab_start+.2,.1,.9)),"real finger takes over emulated mouse with relative drag")
	rink.target=.25
	touch.index = 5
	touch.position.x = 350
	rink._gui_input(touch)
	check(rink.touches == 4 and is_equal_approx(rink.target, 0.25), "second finger cannot steal paddle")
	touch.index = 4
	touch.pressed = false
	rink._gui_input(touch)
	check(rink.touches == -1, "finger release releases paddle")
	rink.set_paused(true)
	touch.pressed = true
	rink._gui_input(touch)
	check(rink.touches == -1, "paused rink rejects touch")
	rink.free()
	# Production screen owns the same rules and checkpoint path.
	var ui = Game.new()
	ui.checkpoint_path = SAVE
	root.add_child(ui)
	await frames(3)
	check(ui.loaded and ui.rink.paused and is_instance_valid(ui.chrome.dialog), "fresh game offers its supported start mode")
	await ui.start_single_player()
	check(not ui.serve_button.disabled and ui.state.scores == [0, 0], "single player selection starts ready to serve")
	ui.serve()
	await frames(3)
	check(ui.state.phase == MatchState.Phase.PLAYING and ui.serve_button.disabled, "production Serve gates while playing")
	check(ui.tone.cues.size() == 8 and ui.tone.voices.size() == 3, "cached audio and concurrent voice budget")
	check(ui.tone.cues.all(func(cue): return cue.data.size() <= 16000), "small bounded sound buffers")
	ui.tone.last_impact=-1000
	ui.tone.play(1,0.0)
	var soft:AudioStreamPlayer=ui.tone.voices[posmod(ui.tone.next_voice-1,3)]
	ui.tone.last_impact=-1000
	ui.tone.play(2,1.0)
	var hard:AudioStreamPlayer=ui.tone.voices[posmod(ui.tone.next_voice-1,3)]
	check(hard.volume_db>soft.volume_db and hard.pitch_scale>soft.pitch_scale and hard.stream!=soft.stream,"hard paddle hits are louder and use a distinct cue from soft rail ticks")
	# Regression: the real Sound/Settings flows must preserve a live rigid body's motion.
	for menu in ["audio","settings","audio"]:
		ui.rink.puck.place(Vector2(300,450),Vector2(180,460))
		await frames(3)
		ui.chrome.dispatch(menu)
		await frames(3)
		var held:Dictionary=ui.rink.snapshot()
		check(ui.rink.paused and Vector2(held.vx,held.vy).length()>.1,"settings snapshot keeps pre-freeze velocity")
		ui.chrome.dispatch("host_state")
		check(ui.chrome.temporary_open,"host state probe preserves temporary dialog")
		ui.chrome.finish_temporary()
		await frames(6)
		check(not ui.rink.paused and ui.rink.puck.linear_velocity.length()>100,"Done restores actual puck velocity")
		var from:Vector2=ui.rink.puck.position
		await frames(6)
		check(ui.rink.puck.position.distance_to(from)>5,"puck travels after closing "+menu)
	ui.show_pause()
	var held_save:Dictionary=Store.read_current(SAVE).record.rink
	check(Vector2(held_save.vx,held_save.vy).length()>.1,"paused disk save retains rally motion")
	ui.chrome.dispatch("audio");ui.chrome.finish_temporary()
	check(ui.rink.paused,"Sound opened while manually paused stays paused")
	ui.resume()
	var legacy:Dictionary=ui.rink.snapshot();legacy.vx=0;legacy.vy=0
	ui.rink.restore(legacy,true);ui.resume();await frames(6)
	check(ui.rink.puck.linear_velocity.length()>100,"old zero-velocity Playing saves recover without rescore")
	ui.on_point(0)
	check(ui.rink.stopped and ui.rink.puck.position.is_equal_approx(Rink.DIM / 2) and ui.rink.trace.is_empty(), "scoring resets visible puck and trail")
	check(ui.rink.goal_time > 0 and ui.rink.goal_side == 0 and ui.state.scores == [1, 0], "goal feedback identifies actual scorer")
	ui.serve()
	check(ui.rink.goal_time == 0 and not ui.rink.stopped, "Serve clears celebration and starts next rally")
	ui.show_pause()
	check(ui.rink.paused and Store.read_current(SAVE).active, "pause persists active match")
	ui.resume()
	check(not ui.rink.paused and not ui.chrome.paused_state, "silent pause resumes directly with a pause icon")
	await create_timer(.4).timeout
	check(not ui.rink.paused and ui.overlay == null, "resume enables physics after sheet closes")
	ui.show_pause()
	ui.free()
	var resume_ui = Game.new()
	resume_ui.checkpoint_path = SAVE
	resume_ui.requested_game = "four"
	root.add_child(resume_ui)
	await frames(3)
	check(resume_ui.rink.paused and resume_ui.state.phase == MatchState.Phase.PLAYING and is_instance_valid(resume_ui.chrome.dialog), "another game request retains and gates on current match")
	await resume_ui.restart()
	check(resume_ui.state.scores == [0, 0] and resume_ui.state.phase == MatchState.Phase.WAITING and not resume_ui.rink.paused, "restart replaces score and resets physics")
	for i in range(10):
		resume_ui.serve()
		resume_ui.on_point(i % 2)
	check(resume_ui.result.visible and not resume_ui.serve_button.visible and not FileAccess.file_exists(SAVE), "production result clears current game")
	resume_ui.free()
	Store.clear(SAVE)
	# Let the real audio thread release stopped WAV playbacks before engine teardown.
	OS.delay_msec(100)
	print("AIR_HOCKEY_RESULT checks=%d failures=%d" % [checks, failures])
	quit(1 if failures else 0)
