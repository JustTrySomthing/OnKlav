extends SceneTree
var checks := 0
func verify(value: bool, label: String) -> void:
	checks += 1
	if not value:
		printerr("FAIL: " + label)
		quit(1)
func _initialize() -> void:
	call_deferred("run")
func run() -> void:
	var helper = root.get_node("OnKlavGame")
	var path := "user://test-match.json"
	verify(helper.save_match({"turn":2,"score":4},path)==OK,"save")
	var loaded: Dictionary = helper.load_match(path)
	verify(loaded.ok and loaded.exists and loaded.state.turn==2,"load")
	verify(helper.save_match({"large":"x".repeat(3*1024*1024)},path)!=OK,"bounded save")
	verify(helper.load_match(path).state.score==4,"oversize preserves prior save")
	var file := FileAccess.open(path,FileAccess.WRITE)
	file.store_string("broken");file.close()
	verify(not helper.load_match(path).ok,"corrupt retained")
	verify(FileAccess.file_exists(path),"no silent discard")
	verify(helper.clear_match(path)==OK,"explicit clear")
	helper.pause_game()
	verify(paused and helper.paused,"pause")
	helper.resume_game()
	verify(not paused and not helper.paused,"resume")
	await process_frame
	# Standard tree pause keeps rigid-body velocity; dialogs preserve prior pause intent.
	var controls=load("res://game_chrome.gd").new();root.add_child(controls)
	controls.silent_pause_requested.connect(func():helper.pause_game();controls.set_paused(true))
	controls.resume_requested.connect(func():helper.resume_game();controls.set_paused(false))
	verify(AudioServer.get_bus_effect_count(0)>0 and AudioServer.get_bus_effect(0,0)is AudioEffectLimiter,"shared master clipping safeguard")
	var music_data:PackedByteArray=controls.music_stream.data
	verify(absi(music_data.decode_s16(11024*2))<100 and absi(music_data.decode_s16(11025*2))<100,"music notes fade through boundaries")
	verify(controls.feedback.stream.data.decode_s16(0)==0,"control cue begins at zero")
	var body:=RigidBody2D.new();body.gravity_scale=0;body.linear_damp=0;body.can_sleep=false
	var shape:=CollisionShape2D.new();shape.shape=CircleShape2D.new();body.add_child(shape);root.add_child(body);body.linear_velocity=Vector2(100,0)
	for i in range(4):await physics_frame
	controls.dispatch("audio");await process_frame
	verify(paused and controls.temporary_open,"audio pauses gameplay")
	var at:Vector2=body.position
	for i in range(3):await physics_frame
	verify(body.position.distance_to(at)<.01 and body.linear_velocity.length()>90,"tree pause freezes pose without losing velocity")
	controls.dispatch("host_state");verify(controls.temporary_open,"host probe retains dialog intent")
	controls.finish_temporary()
	for i in range(4):await physics_frame
	verify(not paused and body.position.distance_to(at)>1 and body.linear_velocity.length()>90,"Done resumes physics motion")
	helper.pause_game();controls.set_paused(true);controls.dispatch("settings");controls.finish_temporary()
	verify(paused and controls.paused_state,"settings respects an existing pause")
	helper.resume_game();controls.queue_free();body.queue_free();await process_frame
	if ResourceLoader.exists("res://model.gd"):
		var model = load("res://model.gd").new()
		verify(model.play(0,0,1),"first turn")
		verify(not model.play(1,0,2),"stale revision")
		verify(not model.play(0,1,2),"occupied cell")
		verify(not model.play(1,1,1),"wrong player")
		verify(not model.play(9,1,2),"outside board")
		for cell in [3,1,4,2]:
			verify(model.play(cell,model.revision,model.turn),"legal next turn")
		verify(model.winner==1,"deterministic result")
		verify(not model.play(8,model.revision,model.turn),"terminal turn rejected")
	if ResourceLoader.exists("res://genre_rules.gd"):
		var rules = load("res://genre_rules.gd")
		verify(rules.toggle_cross(0, 0, 4) == 19, "puzzle corner flips only three cells")
		verify(rules.toggle_cross(19, 0, 4) == 0, "puzzle move is reversible")
		verify(rules.toggle_cross(19, -1, 4) == 19, "invalid puzzle cell has no effect")
		verify(rules.offline_reward(100, 90, 2, 3600) == 0, "idle clock rollback gives no reward")
		verify(rules.offline_reward(100, 10100, 2, 3600) == 7200, "idle payout is capped")
		verify(not rules.bounded_integer(0.5, 0, 5), "fractional saved counters rejected")
	if ResourceLoader.exists("res://draw_pile.gd"):
		var pile_type = load("res://draw_pile.gd")
		var pile = pile_type.new()
		var restored = pile_type.new()
		var cards: Array[int] = [0, 1, 2, 3]
		verify(pile.reset(cards, 82), "initialize draw pile")
		verify(pile.draw() in cards, "draw a known card")
		var saved: Dictionary = JSON.parse_string(JSON.stringify(pile.snapshot()))
		verify(restored.restore(saved, cards) and restored.draw() == pile.draw(), "serialized pile resumes exact draw order")
		var before: Dictionary = restored.snapshot()
		verify(not restored.restore({"remaining": [0, 0], "drawn": [1, 2]}, cards), "reject duplicate cards")
		verify(restored.snapshot() == before, "bad deck save preserves current state")
	print("OnKlav template: ",checks," checks")
	quit()
