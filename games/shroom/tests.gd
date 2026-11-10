extends SceneTree
const Expedition = preload("res://expedition.gd")
var checks := 0
var failures := 0
func verify(value: bool, label: String) -> void:
	checks += 1
	if not value:
		printerr("FAIL: " + label)
		failures += 1
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
	helper.resume_game()
	controls.soundtrack.stop()
	controls.feedback.stop()
	controls.queue_free()
	body.queue_free()
	await process_frame
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
	var game = load("res://main.tscn").instantiate()
	root.add_child(game)
	await process_frame
	await helper.start_mode("Single player")
	verify(helper.session_started and not paused and game.music.playing, "Shroom single-player start")
	verify(not is_instance_valid(helper.chrome.soundtrack), "Only Shroom owns the session soundtrack")
	var layout = load("res://expedition.gd").new()
	var signatures := {}
	for seed_code in range(1,65):
		for sector in range(1,6):
			layout.generate(seed_code,sector)
			var route: PackedInt64Array = layout.paths.get_id_path(7,layout.exit_room)
			verify(route.size()>=3 and route[1]==4, "Airlock and exit connected")
			for room_id in layout.targets:
				verify(layout.paths.get_id_path(7,room_id).size()>=3, "Reachable objective")
			signatures[str(layout.paths.get_point_connections(4))+str(layout.targets)]=true
	verify(signatures.size()>8,"Different runs change routes and objectives")
	verify(game.phase=="briefing" and game.enemies.is_empty(), "Safe opening briefing")
	game.tick_opening_intro(game.INTRO_TIME)
	verify(game.title_panel.visible and game.result_box.get_child(3).text=="Play",
		"The incident sequence ends at a briefing with a Play button")
	verify(game.result_objective.text == "Recover 3 spore samples." and not game.result_info.visible
		and game.result_objective.get_theme_font_size("font_size") > game.result_info.get_theme_font_size("font_size"),
		"The opening sector dialog emphasizes the immediate objective")
	verify(game.INTRO_TIME / 3.0 >= 4.0 and game.INCIDENT_BRIEF.contains("Sergeant Reyes"),
		"Each opening caption has at least four seconds and the full story uses the updated name")

	# Phase 7: all archetype outcomes remain in the supported actor roles.
	for room_id in range(9):
		for target in [false,true]:
			for st in range(1,6):
				game.stage = st
				for repeat in range(12):
					var role: int = game.encounter_kind(room_id,target)
					verify(role >= 0 and role < game.TYPE_HP.size() and role != 3,"Encounter roles exclude boss and unknown types")
	game.stage = 1
	game.begin_expedition()
	var touch = game.controls
	var original_size: Vector2 = touch.size
	for extent in [Vector2(1000,600),Vector2(1450,600),Vector2(1000,765),Vector2(1000,1300)]:
		touch.size = extent
		for i in range(touch.actions.size()):
			var target: Rect2 = touch.button_hit_rect(i)
			verify(Rect2(Vector2.ZERO,extent).grow(.01).encloses(target),
				"Touch targets stay inside the screen at different aspect ratios")
			for j in range(i):
				verify(not target.grow(-.01).intersects(touch.button_hit_rect(j)),
					"Adjacent touch targets keep separate hit areas")
	touch.size = original_size
	var fire_position: Vector2 = touch.button_rect(0).end - Vector2(1, 1)
	var dash_edge: Vector2 = touch.button_rect(1).position + Vector2(-2, 4)
	verify(touch.feed(70,dash_edge,true) and touch.is_held("dash"), "The margin beside Dash accepts a touch")
	touch.feed(70,dash_edge,false,true)
	game.dash_clock = 0.0
	var swap_position: Vector2 = touch.button_rect(2).get_center()
	verify(touch.feed(71,fire_position,true) and touch.is_held("fire"), "Fire is held only after a touch down")
	verify(touch.feed(72,swap_position,true) and game.weapon == 1 and not game.fire_tap and not touch.is_held("fire"),
		"One Swap press changes the weapon and drops queued Fire")
	touch.feed(71,fire_position,false,true)
	touch.feed(72,swap_position,false,true)
	verify(not touch.is_held("swap"), "Swap releases its visual state")
	game.weapon = 0
	var safe_position: Vector3 = game.player.position
	verify(game.field_prompt().contains("gold target"), "The opening teaches aiming at a visible target")
	game.player.position.x = 1.4
	await physics_frame
	await physics_frame
	game.fire()
	verify(game.practice_complete and game.field_prompt() == "Move to the shutter", "A shot at the target hands off to the shutter prompt")
	var practice_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(practice_save) and game.practice_complete, "Practice stays completed after resuming")
	game.player.position = safe_position
	game.fire_clock = 0.0
	game.reset_ammo()
	for frame in range(120): game.tick_enemies(.016)
	verify(game.health==100 and game.player.position==safe_position and game.enemies.all(func(e):return not e.get_meta("alert")), "Airlock is safe while learning")
	var before_balance: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	var close_cap = game.spawn_enemy(0, safe_position+Vector3.FORWARD*.7)
	await physics_frame
	await physics_frame
	game.fire()
	verify(int(close_cap.get_meta("hp")) < game.TYPE_HP[0], "Point-blank fire lands at eye level")
	game.tick_enemies(.016)
	verify(close_cap.velocity.dot(game.player.position-close_cap.position)<=.001, "Close cap backs off instead of crowding")
	close_cap.position=game.expedition.center(4)
	close_cap.set_meta("pursuit_left",game.PURSUIT_MEMORY)
	close_cap.set_meta("alert",true)
	game.tick_enemies(.016)
	verify(not close_cap.get_meta("alert") and close_cap.velocity==Vector3.ZERO, "Retreat to airlock ends pursuit")
	var blob = game.spawn_enemy(1, safe_position+Vector3.FORWARD*3)
	await physics_frame
	await physics_frame
	game.tick_enemies(.016)
	verify(blob.has_meta("warning_lamp") and blob.velocity.dot(game.player.position-blob.position)<=.001, "Spore blob keeps ranged spacing")
	var cover_cap = game.spawn_enemy(0, safe_position+Vector3.FORWARD*1.55)
	game.block(safe_position+Vector3(0,1,-.8),Vector3(2,2,.2),Color.WHITE)
	await physics_frame
	await physics_frame
	game.fire_clock=0.0
	game.fire()
	verify(int(cover_cap.get_meta("hp"))==game.TYPE_HP[0], "Close aim assist respects solid cover")
	cover_cap.set_meta("alert",true)
	cover_cap.set_meta("pursuit_left",game.PURSUIT_MEMORY)
	game.tick_enemies(game.PURSUIT_MEMORY+.01)
	verify(not cover_cap.get_meta("alert") and cover_cap.velocity==Vector3.ZERO, "Occluded enemy forgets pursuit")
	verify(game.restore(before_balance), "Combat balance fixture restores the run")
	var enemy = game.spawn_enemy(0, safe_position+Vector3.FORWARD*3)
	await physics_frame
	await physics_frame
	game.action("swap")
	game.fire()
	verify(game.enemies.has(enemy) and int(enemy.get_meta("hp")) < game.TYPE_HP[0], "Fresh cap survives the first hit")
	game.fire_clock=0.0
	game.fire()
	verify(not game.enemies.has(enemy) and game.kills == 1 and game.score > 0, "Shroom aim and kill")
	game.player.position=game.objective_nodes[0].position
	game.tick_objectives(.016)
	var saved_run: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(saved_run) and game.kills==1 and game.weapon==1 and game.objective_mask==1 and not game.objective_nodes[0].visible, "Objective progress and combat resume")
	verify(game.snapshot().rng == saved_run.rng, "Random sequence resumes")
	game.player.position = safe_position
	var left_cap = game.spawn_enemy(0,safe_position+Vector3(-.46,0,-3))
	var right_cap = game.spawn_enemy(0,safe_position+Vector3(.46,0,-3))
	await physics_frame
	await physics_frame
	game.fire_clock = 0.0
	game.fire()
	verify(int(left_cap.get_meta("hp")) < game.TYPE_HP[0] and int(right_cap.get_meta("hp")) < game.TYPE_HP[0],
		"One reagent burst hits separate targets")
	left_cap.position.x = safe_position.x
	game.weapon = 3
	game.weapon_unlocks |= 1 << 3
	game.fire_clock = 0.0
	game.fire()
	verify(game.mortar_rounds.size() == 1, "Grav Mortar launches a traveling projectile")
	game.mortar_rounds[0].node.position = left_cap.position+Vector3(0,.82,.8)
	game.mortar_rounds[0].velocity = Vector3.FORWARD*game.MORTAR_SPEED
	var in_flight: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(in_flight) and game.mortar_rounds.size() == 1, "An in-flight mortar round survives resume")
	game.weapon = 0
	await physics_frame
	await physics_frame
	game.tick_mortar(1.0)
	verify(game.mortar_rounds.is_empty() and game.kills > int(saved_run.kills),
		"A restored projectile still hits and explodes after swapping weapons")
	verify(game.restore(saved_run), "Weapon test restores the original run")
	var arc_origin: Vector3 = safe_position+Vector3(0,0,-3)
	var arc_primary = game.spawn_enemy(0,arc_origin)
	var arc_secondary = game.spawn_enemy(0,arc_origin+Vector3.RIGHT*.8)
	await physics_frame
	await physics_frame
	game.fire_arc(safe_position+Vector3.UP,arc_origin+Vector3.UP*.82,arc_primary)
	verify(int(arc_primary.get_meta("hp")) < game.TYPE_HP[0] and int(arc_secondary.get_meta("hp")) < game.TYPE_HP[0],
		"Arc Lance damages its target and chains to a nearby enemy")
	verify(float(arc_primary.get_meta("arc_time")) == 1.0 and float(arc_secondary.get_meta("arc_time")) == 1.0,
		"Arc charges both victims for a one-second stun")
	var frozen: Vector3 = arc_primary.position
	game.tick_enemies(.5)
	verify(arc_primary.position == frozen and is_equal_approx(float(arc_primary.get_meta("arc_time")),.5),
		"An electrified enemy cannot move before discharge")
	game.shock_enemy(arc_primary,200)
	var charged_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	var old_kills: int = game.kills
	verify(game.restore(charged_save), "A pending lethal Arc charge survives resume")
	game.tick_shocks(.51)
	verify(game.kills > old_kills and not game.blast_fx.is_empty(),
		"The charge discharges and kills after the remaining stun time")
	verify(game.restore(saved_run), "Arc fixture restores the original run")
	var pickup_sectors: Array[int] = game.nail_pickup_sectors()
	verify(pickup_sectors.size() == 3 and pickup_sectors[0] not in pickup_sectors.slice(1)
		and pickup_sectors[1] != pickup_sectors[2], "Exactly three distinct seeded sectors have Triple Nails")
	var pickup_run := saved_run.duplicate(true)
	pickup_run.stage = pickup_sectors[0]
	pickup_run.objective_mask = 0
	pickup_run.access_state = 0
	pickup_run.enemies = []
	pickup_run.breach = 0
	pickup_run.breach_guard = false
	pickup_run.breach_clock = 0.0
	pickup_run.sporeworks = 0
	pickup_run.culture_sealed = 0
	verify(game.restore(pickup_run) and is_instance_valid(game.nail_pickup), "The seeded sector contains a Triple Nails pickup")
	if is_instance_valid(game.nail_pickup):
		game.player.position = game.nail_pickup.position
		game.tick_nail_pickup(.016)
		verify(game.nail_charges == 10, "Triple Nails grants ten shots")
		game.weapon = 0
		for i in range(10):
			game.rounds[0] = 1
			game.fire_clock = 0.0
			game.reload_left = 0.0
			game.fire()
		verify(game.nail_charges == 0, "Ten Nailgun shots spend all ten charges")
		var after_pickup: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
		verify(game.restore(after_pickup) and not is_instance_valid(game.nail_pickup), "Collected pickups do not respawn after resume")
	verify(game.restore(saved_run), "Triple Nails fixture restores the original run")


	var legacy := saved_run.duplicate(true)
	for key in ["breach","breach_clock","layout","objective_mask","rng","spawn_delay","streak","streak_timer","dash_cooldown","fire_cooldown"]: legacy.erase(key)
	legacy.enemies=[]
	legacy.position=[0,0,9]
	verify(game.restore(legacy) and not game.expedition_mode, "First-release arena checkpoints resume")
	verify(game.restore(saved_run), "Current expedition reloads")
	var invalid := saved_run.duplicate(true)
	invalid.objective_mask=256
	verify(not game.restore(invalid) and game.objective_mask==1, "Invalid objectives preserve live run")
	var run_seed: int = game.seed_code
	for sector in range(1,6):
		verify(game.stage == sector, "Sector progression")
		if sector == 2 and game.phase=="briefing":
			await process_frame
			await process_frame
			var dialog: Rect2 = game.title_panel.get_global_rect()
			var viewport: Rect2 = game.get_viewport_rect()
			verify(dialog.size.x > 0.0 and dialog.position.x >= viewport.position.x
				and dialog.end.x <= viewport.end.x and game.result_info.autowrap_mode != TextServer.AUTOWRAP_OFF,
				"Sporeworks briefing wraps within the visible screen")
			var briefing: Dictionary = game.snapshot()
			verify(game.encounter_prepared and not game.enemies.is_empty() and briefing.enemies.is_empty(),
				"Starting enemies are ready before the briefing button and excluded from briefing saves")
			var prepared: Array = game.enemies.duplicate()
			game.request_expedition_entry()
			verify(game.phase == "combat" and game.enemies == prepared and game.sector_steps.is_empty(),
				"Entering a prepared sector reuses its starting enemies")
			var seeded_combat: Dictionary = game.snapshot()
			verify(game.restore(briefing) and game.enemies.is_empty(), "A prepared briefing checkpoint restores cleanly")
			game.request_expedition_entry()
			game._process(0.0)
			var pending_entry_steps: int = game.sector_steps.size()
			helper.pause_game()
			game._process(0.0)
			verify(game.phase == "briefing" and game.sector_steps.size() == pending_entry_steps
				and game.snapshot() == briefing, "Paused encounter setup keeps the complete briefing checkpoint")
			helper.resume_game()
			for frame in 16:
				game._process(0.0)
			var restored_combat: Dictionary = game.snapshot()
			verify(game.phase == "combat" and restored_combat.enemies == seeded_combat.enemies
				and restored_combat.rng == seeded_combat.rng, "Staged entry regenerates the exact seeded encounter")
		if game.phase=="briefing": game.begin_expedition()
		if sector == 2:
			var transfer: Vector3 = game.expedition.center(game.SPOREWORKS_TRANSFER_ROOM)
			var pressure: Vector3 = game.expedition.center(game.SPOREWORKS_PRESSURE_ROOM)
			var quarantine: Vector3 = game.expedition.center(game.SPOREWORKS_POD_ROOM)
			verify(game.expedition.room_at(game.sporeworks_belt.global_position) == game.SPOREWORKS_TRANSFER_ROOM
				and game.expedition.room_at(game.sporeworks_roots.position) == game.SPOREWORKS_PRESSURE_ROOM,
				"The conveyor and pressure roots are in separate named side rooms")
			verify(game.enemies.any(func(actor): return actor.get_meta("type") == 1 and actor.position.distance_to(transfer+Vector3(1.46,0,-2.66)) < .15), "A spitter holds the transfer bay")
			verify(game.enemies.any(func(actor):
				return actor.get_meta("type") == game.RAZOR_MITE and game.expedition.room_at(actor.position) == game.SPOREWORKS_TRANSFER_ROOM),
				"A Razor Mite is already positioned to pressure cover in the transfer bay")
			verify(game.room_lights.any(func(entry):
				return entry.emergency_red and game.expedition.room_at(entry.node.position) == game.SPOREWORKS_POD_ROOM),
				"The light budget reserves red emergency lighting for Specimen Hold")
			game.player.position = game.expedition.center(4)
			verify(game.field_prompt().is_empty(), "The arrival junction gives no hazard prompt")
			for fixture_enemy in game.enemies.duplicate():
				game.discard(fixture_enemy)
			game.enemies.clear()
			var original_health: int = game.health
			verify(game.expedition.room_at(game.weapon_pickup.position) == 5 and not game.sporeworks_route.visible,
				"Motor Service holds the Arc Lance while extraction guidance remains off")
			var pickup_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
			var pickup_at: Vector3 = game.weapon_pickup.position
			var display: Node3D = game.weapon_pickup.get_node("WeaponDisplay")
			var angle := display.rotation.y
			game.player.position = pickup_at+Vector3.RIGHT*1.5
			game.active_time = .4
			game.tick_weapon_pickup(.2)
			verify(display.position.y > 1.38 and display.rotation.y != angle and display.get_child(0).get_child_count() > 0,
				"The pickup displays a rotating and hovering weapon model")
			verify(game.field_prompt().contains("Arc Lance"), "The discovery prompt names the weapon")
			game.player.position = pickup_at
			game.tick_weapon_pickup(.016)
			verify(not is_instance_valid(game.weapon_pickup) and game.weapon == 2 and (game.weapon_unlocks & 4) != 0,
				"Walking into the display collects and equips the weapon")
			var acquired: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
			verify(game.restore(acquired) and not is_instance_valid(game.weapon_pickup),
				"An acquired weapon stays collected on resume")
			verify(game.restore(pickup_save) and is_instance_valid(game.weapon_pickup), "The pickup fixture restores the run")
			var belt_position: Vector3 = game.sporeworks_belt.position
			game.player.position = game.SPOREWORKS_STEAM
			game.active_time = 1.5
			game.tick_sporeworks(.1)
			verify(game.sporeworks_steam_signal.visible and not game.sporeworks_steam.visible and game.health == original_health
				and game.sporeworks_belt.position == belt_position, "Steam warns harmlessly while the transfer line is stalled")
			game.active_time = 2.5
			game.player.position.x += 1.8
			game.tick_sporeworks(.1)
			verify(game.sporeworks_steam.visible and game.health == original_health, "The side aisle bypasses live steam")
			game.player.position = game.SPOREWORKS_STEAM
			game.damage_time = 0.0
			game.tick_sporeworks(.1)
			verify(game.health == original_health-10, "Crossing the live steam lane damages the player")
			game.health = original_health
			game.damage_time = 0.0
			game.player.position = pressure+Vector3(-2.47, 0, .58)
			game.active_time = 2.4
			game.tick_sporeworks()
			game.update_sporeworks_motion()
			var needle: Node3D = game.room.get_meta("pressure_needle")
			verify(needle.rotation.z < 1.0, "The pressure needle rises before the surge")
			verify(game.sporeworks_warning.visible and game.field_prompt().contains("dash") and game.health == original_health,
				"The amber warning appears before the roots can hurt the player")
			game.player.position = quarantine+Vector3(2.52, 0, 2.75)
			game.weapon = 0
			verify(game.field_prompt().contains("Swap"), "Approaching the pod with the Nailgun teaches Swap")
			game.player.position = pressure+Vector3(-2.47, 0, .58)
			game.active_time = 3.4
			game.damage_time = 0.0
			game.dash_left = .2
			game.tick_sporeworks()
			verify(game.health == original_health, "Dash carries the player through the active root lane")
			game.dash_left = 0.0
			game.tick_sporeworks()
			verify(game.health == original_health - 12, "Extended roots punish standing in the lane")
			game.health = original_health
			game.player.rotation.y = 0.0
			game.camera.rotation.x = 0.0
			game.player.position = pressure+Vector3(-2.47, 0, 3.25)
			game.weapon = 0
			game.fire_clock = 0.0
			game.reload_left = 0.0
			await physics_frame
			await physics_frame
			game.fire()
			verify(game.sporeworks_cleared == game.SPOREWORKS_TENDRIL and not is_instance_valid(game.sporeworks_roots),
				"A real Nailgun shot pins the root core")
			verify(is_equal_approx(needle.rotation.z,1.82), "The needle drops as soon as the valve is shut")
			game.player.position = quarantine+Vector3(2.52, 0, -4.15)
			game.weapon = 1
			var pod: StaticBody3D = game.room.get_children().filter(func(node):
				return node.get_meta("sporeworks_target", 0) == game.SPOREWORKS_POD).front()
			verify(game.expedition.room_at(pod.position) == game.SPOREWORKS_POD_ROOM,
				"The rupture pod is in Specimen Hold")
			game.hit_sporeworks_target(pod)
			verify(game.sporeworks_cleared == game.SPOREWORKS_TENDRIL, "A distant Sporebreaker shot leaves the pod intact")
			game.player.position = quarantine+Vector3(2.52, 0, 2.75)
			game.fire_clock = 0.0
			await physics_frame
			await physics_frame
			game.fire()
			verify(game.sporeworks_cleared == 3, "A close Sporebreaker shot clears Specimen Hold")
			verify(game.restore(JSON.parse_string(JSON.stringify(game.snapshot()))) and game.sporeworks_cleared == 3
				and not is_instance_valid(game.sporeworks_roots), "Both routes remain open after resume")

		if sector == 3:
			var before_crossing: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
			verify(game.waste_surface != null and game.waste_safe(game.player.position), "The waste crossing starts on a safe landing")
			for point in [Vector3(2.17,0,10.9),Vector3(2.17,0,9.7),Vector3(2.17,0,7.6),Vector3(0,0,7.6),Vector3(0,0,5.3),Vector3(0,0,4.5)]:
				game.player.position = point
				game.tick_access_route(.016)
				verify(game.phase=="combat", "The turning catwalk is safe from landing to exit")
			game.player.position = Vector3(-2.7,0,8.9)
			game.dash_left = .2
			game.tick_access_route(.016)
			verify(game.phase=="dead" and game.health==0 and game.player.position.y<0,
				"Falling into green waste is lethal even during a dash")
			verify(game.restore(before_crossing) and game.waste_surface != null, "The crossing persists on resume")
			var legacy_crossing := before_crossing.duplicate(true)
			legacy_crossing.erase("access_route")
			legacy_crossing.erase("access_state")
			verify(game.restore(legacy_crossing) and game.waste_surface == null, "Older saves keep a safe floor in their active sector")
			verify(game.restore(before_crossing), "Restore the crossing fixture")
			var vent: StaticBody3D = game.room.get_children().filter(func(node): return node.get_meta("culture_vent", -1) == 0).front()
			game.active_time = 3.1
			verify(game.chamber_lane_state(0) == 2, "The culture vent can contaminate its lane")
			game.seal_culture_vent(vent)
			verify(game.chamber_lane_state(0) == 0, "Sealing a vent clears the contamination")
			verify(game.restore(JSON.parse_string(JSON.stringify(game.snapshot()))) and game.chamber_lane_state(0) == 0,
				"The culture lane stays clear after resume")
		if sector == 4:
			game.active_time = 2.0
			game.tick_hub_chamber()
			verify(game.chamber_lanes[0].warning.visible and not game.chamber_lanes[0].danger.visible,
				"The furnace gives an amber warning before the heat")
			game.active_time = 3.1
			game.player.position = Vector3(0, 0, 0)
			var safe_health: int = game.health
			game.damage_time = 0
			game.dash_left = 0
			game.tick_hub_chamber()
			verify(game.health == safe_health and game.chamber_lane_state(0) == 2 and game.chamber_lane_state(1) == 0,
				"Heat fills one lane while the middle stays safe")
			game.player.position.x = -2.3
			game.tick_hub_chamber()
			verify(game.health == safe_health - 14, "Crossing the live lane deals heat damage")
			game.health = safe_health
			game.active_time = .4
			verify(game.chamber_lane_state(0) == 0 and game.chamber_lane_state(1) == 2, "Furnace heat alternates between lanes")
		if sector==5:
			var boss: CharacterBody3D = game.enemies.filter(func(e):return e.get_meta("type")==3).front()
			game.active_time = 3.15
			verify(game.chamber_lane_state(0) == 0, "Root lanes are quiet at the start of the boss fight")
			boss.set_meta("hp", 300)
			boss.set_meta("boss_windup", .1)
			game.tick_boss(boss, .12, 6.0, true)
			var phase_since: float = boss.get_meta("boss_phase_since")
			verify(boss.get_meta("boss_phase") == 1 and game.chamber_lane_state(0, 1, phase_since) == 0,
				"The phase change starts with a safe beat")
			game.active_time += 2.0
			verify(game.chamber_lane_state(0, 1, phase_since) == 1, "Roots warn before they can damage the player")
			verify(boss.get_meta("boss_exposed") > 0.0, "A completed volley exposes the boss")
			var finale: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
			verify(game.restore(finale), "Boss exposure checkpoint reloads")
			boss = game.enemies.filter(func(e):return e.get_meta("type")==3).front()
			verify(boss.get_meta("boss_exposed") > 0.0, "Boss exposure survives a checkpoint")
			game.hurt_enemy(boss, 40)
			verify(boss.get_meta("hp") == 246, "A shot during exposure deals extra damage")
			boss.set_meta("hp", 125)
			game.tick_boss(boss, .05, 6.0, false)
			verify(boss.get_meta("boss_phase") == 2, "Critical health starts the last stand")
			game.active_time = 2.2
			verify(game.chamber_lane_state(0, 2) == 2 and game.chamber_lane_state(0, 1) == 1,
				"The last stand runs its roots on a faster beat")
			game.hurt_enemy(boss,10000)
			verify(game.exit_marker.visible and game.portal_ready() and game.phase=="combat", "Cap King unlocks the fixed final gate")
			game.player.position=game.exit_marker.position
			game.tick_objectives(.016)
			verify(game.phase=="portal", "Final sector uses the portal before victory")
			verify(game.restore(JSON.parse_string(JSON.stringify(game.snapshot()))), "Final portal checkpoint resumes")
			game.tick_portal(game.PORTAL_TIME)
		else:
			if sector == 1:
				game.player.position = Vector3(0, 0, 7)
				game.tick_breach(.1)
				verify(game.breach_state == game.Breach.WARNING and is_instance_valid(game.breach_gate), "Approaching the airlock triggers a warning before opening")
				verify(game.restore(JSON.parse_string(JSON.stringify(game.snapshot()))) and is_instance_valid(game.breach_gate), "The closed shutter survives a saved warning")
				game.tick_breach(game.BREACH_WARNING_TIME)
				verify(game.breach_state == game.Breach.OPEN and not is_instance_valid(game.breach_gate), "Containment opens the route after warning")
			for objective in game.objective_nodes:
				game.player.position=objective.position
				game.tick_objectives(.016)
			verify(game.objectives_complete(),"All sector objectives secured")
			if sector == 2:
				verify(game.sporeworks_route.visible and not game.sporeworks_steam.visible and not game.sporeworks_steam_signal.visible,
					"Final relay shuts down the steam and lights the extraction route")
				var stopped_at: Vector3 = game.sporeworks_belt.position
				game.tick_sporeworks(.2)
				var prior_position: Vector3 = game.player.position
				var scanner: Node3D = game.room.get_meta("transfer_scanner")
				game.player.position = scanner.global_position
				game.update_sporeworks_motion()
				game.player.position = prior_position
				verify(game.sporeworks_belt.position != stopped_at, "Ventilation restart sets the transfer line moving")
				verify(game.restore(JSON.parse_string(JSON.stringify(game.snapshot()))) and game.sporeworks_route.visible
					and not game.sporeworks_steam.visible, "Restoring after the restart keeps extraction lit and the floor safe")
			if sector in [2,4]:
				var door: StaticBody3D = game.access_gate
				verify(is_instance_valid(door) and is_instance_valid(game.access_card) and not game.portal_ready(),
					"A physical keycard door blocks extraction after all objectives are complete")
				var card_room: int = game.expedition.room_at(game.access_card.position)
				verify(card_room not in [Expedition.ENTRY,4,game.expedition.exit_room]
					and game.expedition.exit_room not in game.expedition.paths.get_id_path(Expedition.ENTRY,card_room),
					"The keycard is in a side room reachable before the locked door")
				game.player.position = door.position
				game.tick_access_route(.016)
				verify(is_instance_valid(game.access_gate), "Approaching without the keycard leaves the door closed")
				game.player.position = game.access_card.position
				game.tick_access_route(.016)
				var card_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
				verify(game.access_state==1 and game.restore(card_save) and not is_instance_valid(game.access_card)
					and is_instance_valid(game.access_gate), "A collected keycard survives resume before unlocking")
				game.player.position = game.access_gate.position
				game.tick_access_route(.016)
				var door_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
				verify(game.access_state==2 and game.restore(door_save) and not is_instance_valid(game.access_gate),
					"The unlocked door stays open after resuming")
			var fixed_gate: Vector3 = game.exit_marker.position
			var exit_center: Vector3 = game.expedition.center(game.expedition.exit_room)
			game.player.position = exit_center
			var enemies_at_exit: int = game.enemies.size()
			game.tick_objectives(.016)
			verify(game.exit_marker.visible and not game.portal_field.visible and fixed_gate.distance_to(exit_center) > 4.0,
				"Gate remains at the far wall and stays dark at the room center")
			if sector == 1:
				verify(game.breach_state == game.Breach.EXTRACTION and not game.portal_ready() and game.enemies.size() == enemies_at_exit,
					"Extraction waits for pre-positioned guards without spawning replacements")
				verify(game.restore(JSON.parse_string(JSON.stringify(game.snapshot()))), "Extraction encounter resumes")
				var guards: Array = game.enemies.filter(func(e): return e.get_meta("breach_guard", false))
				verify(guards.size() == 2, "Both extraction attackers retain their role after restoring")
				for guard in guards:
					game.hurt_enemy(guard, 10000)
					if game.enemies.has(guard):
						game.hurt_enemy(guard, 10000)
					game.tick_objectives(.016)
					if guard == guards.front():
						verify(game.phase == "combat" and not game.portal_ready(), "One attacker still blocks extraction")
			game.player.position = fixed_gate.lerp(exit_center,.53)
			game.tick_objectives(.016)
			verify(game.portal_field.visible and game.phase=="combat", "Eligible gate powers up on approach")
			game.player.position = fixed_gate
			game.tick_objectives(.016)
			verify(game.phase=="portal","Crossing the fixed gate starts extraction before the perk")
			if sector==1:
				var portal_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
				verify(game.restore(portal_save) and game.phase=="portal","Portal checkpoint resumes")
			game.tick_portal(game.PORTAL_TIME*.5)
			verify(game.phase=="portal" and game.exit_marker.visible, "Portal stays visible during the animation")
			game.tick_portal(game.PORTAL_TIME*.5)
			verify(game.phase=="upgrade","Extraction unlocks perk")
			if sector == 1:
				var saved_upgrade: Dictionary = game.snapshot()
				game.request_sector_advance(1)
				game.request_sector_advance(2)
				game._process(0.0)
				verify(game.stage == 1 and game.result_title.text == "PREPARING SECTOR",
					"Transition status gets a frame before setup begins")
				game._process(0.0)
				verify(game.stage == 2 and game.snapshot() == saved_upgrade
					and game.result_title.text == "PREPARING SECTOR", "Partial setup keeps the old checkpoint and blocks entry")
				var pending_steps: int = game.sector_steps.size()
				helper.pause_game()
				game._process(0.0)
				verify(game.sector_steps.size() == pending_steps, "Pausing suspends staged construction")
				helper.resume_game()
				for frame in 24:
					game._process(0.0)
				verify(game.sector_steps.is_empty() and game.snapshot().stage == 2 and game.phase == "briefing"
					and game.power == 1 and game.speed_bonus == 0, "Completed setup commits one upgrade and opens the briefing")
			else:
				game.advance(1)
	verify(game.phase=="won" and game.power==4,"Cap King defeat completes the run")
	game.start_new()
	verify(game.seed_code!=run_seed and game.stage==1 and game.score==0 and game.phase=="briefing", "Replay resets the expedition")
	# Phase 7: all archetype outcomes remain in the supported actor roles.
	for room_id in range(9):
		for target in [false,true]:
			for st in range(1,6):
				game.stage = st
				for repeat in range(12):
					var role: int = game.encounter_kind(room_id,target)
					verify(role >= 0 and role < game.TYPE_HP.size() and role != 3,"Encounter roles exclude boss and unknown types")
	game.stage = 1
	game.begin_expedition()
	verify(game.room_lights.size()<=6 and game.hazards.size()<=4, "Atmospheric lights and traps are bounded per sector")
	# Phase 16: only static dressing is instanced. Animated hazard puddles
	# remain separate MeshInstance3D nodes so they can still pulse visibly.
	var batches := 0
	var batched_details := 0
	for child in game.room.get_children():
		if child is MultiMeshInstance3D:
			batches += 1
			batched_details += child.multimesh.instance_count
	verify(batches > 0 and batched_details > batches, "Decorative geometry uses shared MultiMesh draws")
	verify(game.decor_batches.is_empty(), "Static batches flushed after room construction")
	verify(game.web_render_shrink(Vector2(1000,600)) == 2 and game.web_render_shrink(Vector2(2400,1080)) == 4,
		"High-resolution WebView reduces 3D pixel workload without changing the HUD")
	verify(game.web_render_shrink(Vector2(1331,600),true) == 3
		and game.web_render_shrink(Vector2(1331,600)) == 2,
		"The Android host uses a smaller 3D buffer than standalone Web")
	for visual in game.room.get_children():
		if not visual is MultiMeshInstance3D: continue
		var batch: MultiMesh = visual.multimesh
		var first_room: int = game.expedition.room_at(batch.get_instance_transform(0).origin)
		for i in range(1,batch.instance_count):
			verify(game.expedition.room_at(batch.get_instance_transform(i).origin) == first_room,
				"Room dressing batches stay within their own culling region")
	for origin in range(9):
		for destination in range(9):
			if origin == destination: continue
			var route: PackedInt64Array = game.expedition.paths.get_id_path(origin,destination)
			verify(game.expedition.next_rooms[origin*9+destination] == route[1],
				"Cached room routing retains the original path")
	verify(game.hazards.all(func(h): return h.node is MeshInstance3D), "Pulsing hazards retain separate visual nodes")
	var repulsion_total := Vector3.ZERO
	for force in game.crowd_forces(): repulsion_total += force
	verify(repulsion_total.length_squared() < .0001, "Shared enemy pair separation is balanced")
	verify(game.world_environment != null and game.world_environment.fog_density > .0, "Mobile sector fog uses compatibility renderer")
	verify(game.world.msaa_3d == Viewport.MSAA_2X and game.world.get_parent().stretch_shrink == 2, "Mobile internal render resolution and AA stay bounded")
	verify(game.hazard_next_hit == 0.0, "Hazard cooldown resets on a new sector")

	# Phase 11: landmarks are deterministic static dressing only; the central hub
	# and each objective retain their original traversable grid positions.
	var room_positions := []
	for id in range(9): room_positions.append(game.expedition.center(id))
	var stage11_paths = game.expedition.paths.get_point_connections(Expedition.ENTRY)
	game.build_room()
	verify(game.expedition.paths.get_point_connections(Expedition.ENTRY) == stage11_paths, "Sector dressing preserves airlock links")
	verify(game.expedition.targets.size() == Expedition.COUNTS[game.stage-1], "Objective-room cues preserve goal count")
	verify(game.expedition.center(4) == room_positions[4], "Sector centerpiece preserves hub center")
	# Phase 12: one-time exploration bonuses are affordable, deterministic,
	# bounded to nine rooms, and cannot repeat after a save/restore.
	verify(game.explored_rooms_mask == (1 << Expedition.ENTRY), "New sector begins with visited entry only")
	var before_exploration = game.score
	for id in [4,1,2]:
		game.player.position = game.expedition.center(id)
		game.tick_exploration()
	verify(game.score == before_exploration + 100, "Four unique rooms grant one exploration bonus")
	game.tick_exploration()
	verify(game.score == before_exploration + 100, "Revisiting the same room cannot farm points")
	for id in [3,5,6]:
		game.player.position = game.expedition.center(id)
		game.tick_exploration()
	verify(game.score == before_exploration + 300, "Seven unique rooms grant second exploration bonus")
	var explored_snapshot: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(explored_snapshot) and game.explored_rooms_mask == int(explored_snapshot.explored_rooms), "Exploration progress survives restore")
	game.tick_exploration()
	verify(game.score == before_exploration + 300, "Restored exploration cannot award points twice")
	var invalid_exploration := explored_snapshot.duplicate(true)
	invalid_exploration.explored_rooms = 1024
	verify(not game.restore(invalid_exploration), "Oversized visited-room mask is rejected")
	invalid_exploration.explored_rooms = 0
	verify(not game.restore(invalid_exploration), "Missing entry room in visited mask is rejected")
	game.player.position = game.expedition.center(7) + Vector3(0,0,2.3)
	game.previous_player_room = Expedition.ENTRY
	# Phase 10: optional ambushes are deterministic, never occur in the entry
	# airlock, provide a real warning and are one-shot across checkpoints.
	verify(game.ambush_rooms.size() <= 2 and not game.ambush_rooms.has(7) and not game.ambush_rooms.has(4), "Ambush budget and safe entry")
	if not game.ambush_rooms.is_empty():
		var ambush_room: int = game.ambush_rooms[0]
		game.player.position = game.expedition.center(ambush_room)
		game.spawn_enemy(4,game.player.position+Vector3(1.9,0,-1.6))
		game.tick_ambush(.01)
		verify(game.ambush_pending_room == ambush_room and game.ambush_timer > 1.0, "The alert telegraphs before the patrol responds")
		var warned_state: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
		verify(game.restore(warned_state) and game.ambush_pending_room == ambush_room, "Ambush warning resumes")
		var before_ambush = game.enemies.size()
		game.tick_ambush(1.5)
		verify(game.enemies.size() == before_ambush and game.ambush_pending_room == -1, "The warning alerts a pre-positioned patrol without spawning enemies")
		var used_ambush_state: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
		verify(game.restore(used_ambush_state) and (game.ambush_used_mask & (1 << ambush_room)) != 0, "Triggered ambush never repeats on restore")
		game.player.position = game.expedition.center(7)
		game.tick_ambush(.01)
		var count_used = game.enemies.size()
		game.player.position = game.expedition.center(ambush_room)
		game.tick_ambush(2.0)
		verify(game.enemies.size() == count_used, "Cleared ambush does not respawn")
		var bad_ambush := used_ambush_state.duplicate(true)
		bad_ambush.ambush_delay = 999.0
		verify(not game.restore(bad_ambush), "Forged ambush timer rejected")
		game.player.position = game.expedition.center(7)+Vector3(0,0,2.3)
		game.previous_player_room = 7
	# Cap King phases and shot cap must survive the same mobile checkpoint path.
	var king = game.spawn_enemy(3,game.player.position+Vector3.FORWARD*6)
	king.set_meta("hp",145)
	var initial_orbs = game.orbs.size()
	game.boss_volley(king,2)
	verify(game.orbs.size()==initial_orbs+3 and game.orbs.all(func(o): return o.damage==9), "Cap King bounded triple volley")
	king.set_meta("boss_cycle",2.25)
	king.set_meta("boss_windup",.4)
	king.set_meta("boss_phase",2)
	var boss_state: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(boss_state), "Cap King checkpoint loads")
	var restored_king = game.enemies.filter(func(e):return int(e.get_meta("type"))==3).front()
	verify(is_equal_approx(float(restored_king.get_meta("boss_windup")),.4) and int(restored_king.get_meta("boss_phase"))==2, "Cap King windup and phase persist")
	var shots_before_cover = game.orbs.size()
	game.tick_boss(restored_king,.1,5.0,false)
	verify(float(restored_king.get_meta("boss_windup"))==0.0 and game.orbs.size()==shots_before_cover, "Cover cancels a charged boss volley")
	verify(float(restored_king.get_meta("boss_cycle"))>=1.0, "Interrupted boss volley cannot restart instantly")
	var invalid_king := boss_state.duplicate(true)
	for e in invalid_king.enemies:
		if int(e.kind)==3: e.boss_windup=99.0
	verify(not game.restore(invalid_king), "Invalid boss charge is rejected")
	game.clear_orbs()
	game.enemies.erase(restored_king)
	game.discard(restored_king)
	var skitter = game.spawn_enemy(4,game.player.position+Vector3.FORWARD*3)
	var bloater = game.spawn_enemy(5,game.player.position+Vector3.FORWARD*4)
	verify(int(skitter.get_meta("type"))==4 and int(bloater.get_meta("type"))==5, "Skitter and bloater are valid enemies")
	# Hits briefly interrupt ordinary enemies, and checkpoint resume must not
	# erase the recovery interval or allow crafted oversized stagger values.
	game.hurt_enemy(bloater,8)
	verify(float(bloater.get_meta("stagger")) > 0.0, "Enemy hit reaction interrupts attack")
	var stagger_saved: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(stagger_saved), "Hit reaction checkpoint reloads")
	verify(game.enemies.any(func(e): return int(e.get_meta("type"))==5 and float(e.get_meta("stagger")) > 0.0), "Hit-stagger persists through save")
	var malformed_stagger = stagger_saved.duplicate(true)
	for e in malformed_stagger.enemies:
		if int(e.kind)==5: e.stagger=500.0
	verify(not game.restore(malformed_stagger), "Invalid enemy recovery timers rejected")
	# Phase 8: spitter warning must be serialized, bounded and canceled by hits.
	var warning_spitter = game.spawn_enemy(1,game.player.position+Vector3.FORWARD*5)
	warning_spitter.set_meta("attack_warning",.31)
	game.damage_time=.37
	var warning_saved: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(warning_saved),"Spitter charge checkpoint loads")
	var resumed_health: int = game.health
	game.damage_player(10)
	verify(game.health==resumed_health and is_equal_approx(game.damage_time,.37),"Resume preserves the short post-hit protection")
	var invalid_damage = warning_saved.duplicate(true)
	invalid_damage.damage_cooldown=1.0
	verify(not game.restore(invalid_damage),"Oversized damage cooldown is rejected")
	var resumed_spitter = game.enemies.filter(func(e):return int(e.get_meta("type"))==1 and is_equal_approx(float(e.get_meta("attack_warning")),.31))
	verify(not resumed_spitter.is_empty(),"Spitter charge duration resumes")
	var invalid_warning = warning_saved.duplicate(true)
	for e in invalid_warning.enemies:
		if int(e.kind)==1: e.attack_warning=5.0
	verify(not game.restore(invalid_warning),"Oversized Spitter charge rejected")
	if not resumed_spitter.is_empty():
		game.hurt_enemy(resumed_spitter.front(),8)
		verify(float(resumed_spitter.front().get_meta("attack_warning"))==0.0,"Hit interrupts Spitter windup")
	var new_snapshot: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(new_snapshot) and game.enemies.any(func(e):return e.get_meta("type")==5), "New enemy types survive save and resume")
	for e in game.enemies.duplicate():
		if e.get_meta("type") in [4,5]:
			game.enemies.erase(e)
			game.discard(e)
	# A large frame can carry a fast orb completely across the player's hitbox.
	# Segment sweeping still detects the hit without adding physics bodies.
	game.damage_time=0.0
	game.health=100
	var spitter=game.spawn_enemy(1,game.player.position+Vector3.FORWARD*3)
	game.launch_orb(spitter,1)
	var crossing = game.orbs.back()
	crossing.node.position = game.player.position+Vector3(-1.5,.9,0)
	crossing.velocity = Vector3(8,0,0)
	game.tick_orbs(.40)
	verify(game.health==90 and game.orbs.is_empty(), "Large-frame projectile sweep still hits player")
	game.health=100
	game.damage_time=0.0
	game.launch_orb(spitter,1)
	var expiring = game.orbs.back()
	expiring.node.position = game.player.position+Vector3(-1.5,.9,0)
	expiring.velocity = Vector3(8,0,0)
	expiring.life = .13
	game.tick_orbs(.40)
	verify(game.health==90 and game.orbs.is_empty(), "An expiring orb can hit before its lifetime ends")
	game.health=100
	game.damage_time=0.0
	game.launch_orb(spitter,1)
	var short_lived = game.orbs.back()
	short_lived.node.position = game.player.position+Vector3(-1.5,.9,0)
	short_lived.velocity = Vector3(8,0,0)
	short_lived.life = .05
	game.tick_orbs(.40)
	verify(game.health==100 and game.orbs.is_empty(), "An orb cannot hit after its lifetime ends")
	var invalid_phase: Dictionary = game.snapshot()
	invalid_phase.stage=2
	invalid_phase.access_route=true
	invalid_phase.access_state=1
	invalid_phase.erase("phase")
	verify(not game.restore(invalid_phase), "Missing phase rejects a route checkpoint cleanly")
	game.health=10
	game.damage_time=0.0
	game.launch_orb(spitter,1)
	game.launch_orb(spitter,1)
	game.orbs[1].node.position=game.player.position+Vector3.UP*.9
	game.tick_orbs(.016)
	verify(game.phase=="dead" and game.orbs.is_empty(), "Lethal spore safely drains projectiles")
	verify(await helper.save_data("profile",{"best":"damaged"}),"Test profile can be seeded")
	game.best_loaded=false
	game.retry_best_at=0
	game.highest=500
	await game.persist_best()
	var retained_profile: Dictionary = await helper.load_data("profile")
	verify(not game.best_loaded and retained_profile.state.best=="damaged","Unreadable best-score data is not overwritten")
	verify(await helper.save_data("profile",{"best":7000}),"Test profile can be recovered")
	game.retry_best_at=0
	await game.load_best()
	verify(game.best_loaded and game.highest==7000 and game.stored_best==7000,"Best-score retry retains the higher saved score")
	var pool_children: int = game.effect_root.get_child_count()
	for i in game.BLAST_LIMIT + 2:
		game.explosion(Vector3(i,0,0),4.0,Color.ORANGE,Color.YELLOW)
	verify(game.blast_fx.size() == game.BLAST_LIMIT and game.motes.size() == game.FX_LIMIT
		and game.effect_root.get_child_count() == pool_children, "Explosion bursts reuse the bounded visual pool")
	game.tick_motes(1.0)
	game.tick_blasts(1.0)
	verify(game.free_motes.size() == game.FX_LIMIT and game.free_blasts.size() == game.BLAST_LIMIT,
		"Expired visuals return to the pool")
	game.explosion(Vector3.ZERO,4.0,Color.ORANGE,Color.YELLOW)
	game.build_room()
	verify(game.free_motes.size() == game.FX_LIMIT and game.free_blasts.size() == game.BLAST_LIMIT
		and game.motes.is_empty() and game.blast_fx.is_empty(), "Sector rebuild recycles active visuals")
	game.player.rotation = Vector3.ZERO
	game.camera.rotation = Vector3.ZERO
	var camera_at: Vector3 = game.camera.global_position
	var behind := camera_at + Vector3.BACK*8.0
	var frustum: Array[Plane] = game.camera.get_frustum()
	verify(game.effect_in_view(frustum,camera_at+Vector3.FORWARD*8.0,.5)
		and not game.effect_in_view(frustum,behind,.5), "Cosmetic culling distinguishes front from rear")
	game.explosion(behind,1.5,Color.ORANGE,Color.YELLOW)
	game.death_mark(behind,0)
	var mote: Dictionary = game.motes.front()
	var mote_node: MeshInstance3D = mote.node
	var blast_core: Node3D = game.blast_fx.front().node.get_child(0)
	var mark_cap: Node3D = game.death_marks.front().cap
	var old_blast_scale := blast_core.scale
	var old_mark_scale := mark_cap.scale
	var old_lifetime: float = mote.life
	game.tick_cosmetic_effects(.10)
	verify(not mote_node.visible and mote_node.position == behind and mote.life < old_lifetime
		and blast_core.scale == old_blast_scale and mark_cap.scale == old_mark_scale,
		"Off-screen cosmetics age without updating their meshes")
	game.player.rotation.y = PI
	game.tick_cosmetic_effects(.05)
	verify(mote_node.visible and mote_node.position.distance_to(behind) > .01
		and blast_core.scale != old_blast_scale and mark_cap.scale != old_mark_scale,
		"Effects reenter view at their current animation state")
	game.complete_sector()
	verify(game.free_motes.size() == game.FX_LIMIT and game.free_blasts.size() == game.BLAST_LIMIT
		and game.death_marks.is_empty(), "Extraction results immediately release cosmetic effects")
	game.hazards.clear()
	game.phase = "combat"
	game.health = 100
	game.damage_time = 0.0
	game.dash_left = 0.0
	game.add_floor_hazard(game.player.position,true,-2.4)
	var hazard: Dictionary = game.hazards.back()
	game.tick_atmosphere(1.0)
	verify(game.health == 93 and game.hazard_next_hit > 1.0 and not hazard.has("last_armed"),
		"Hazard damage advances in physics before its visuals update")
	game.update_atmosphere_visuals()
	verify(hazard.last_armed and hazard.has("last_clock"), "Nearby hazard visuals use the current physics phase")
	game.player.position += Vector3(20,0,0)
	var prior_visual_clock: float = hazard.last_clock
	game.tick_atmosphere(.1)
	game.update_atmosphere_visuals()
	verify(hazard.last_clock == prior_visual_clock, "Distant hazard visuals keep their last rendered state")
	var label_width: float = game.hud.text_width("VITALS  93",21)
	verify(is_equal_approx(label_width,game.hud.text_width("VITALS  93",21))
		and game.hud.text_widths.has(Vector2i("VITALS  93".hash(),21)), "Repeated HUD labels reuse their measured widths")
	game.start_new()
	game.begin_expedition()
	game.hazard_clock = 6.0
	game.hazard_next_hit = 6.4
	var hazard_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(hazard_save) and is_equal_approx(game.hazard_clock,6.0)
		and is_equal_approx(game.hazard_next_hit,6.4), "Resume preserves floor hazard phase and damage cooldown")
	hazard_save.erase("hazard_clock")
	hazard_save.erase("hazard_cooldown")
	verify(game.restore(hazard_save) and game.hazard_clock == 0.0, "Old checkpoints can still resume")

	game.dash_left = .12
	game.dash_clock = 1.9
	game.dash_direction = Vector3.FORWARD
	game.player.velocity = Vector3.FORWARD*10.0
	var dash_save: Dictionary = JSON.parse_string(JSON.stringify(game.snapshot()))
	verify(game.restore(dash_save) and is_equal_approx(game.dash_left,.12)
		and is_equal_approx(game.dash_clock,1.9) and game.dash_direction == Vector3.FORWARD
		and game.player.velocity == Vector3.FORWARD*10.0, "A checkpoint restores active dash movement")
	var dash_health: int = game.health
	game.damage_player(10)
	verify(game.health == dash_health, "Restored dash keeps its remaining damage protection")
	for key in ["dash_left","dash_direction","dash_velocity"]:
		dash_save.erase(key)
	verify(game.restore(dash_save) and game.dash_left == 0.0 and is_equal_approx(game.dash_clock,1.9),
		"Legacy checkpoints retain the dash cooldown")
	game.set_physics_process(false)
	game.expedition_mode = false
	game.build_room()
	game.phase = "combat"
	game.player.position = Vector3(0,6,0)
	game.player.rotation = Vector3.ZERO
	game.camera.rotation = Vector3.ZERO
	var covered_spitter: CharacterBody3D = game.spawn_enemy(1,Vector3(0,6,5))
	game.block(Vector3(0,6.9,2.5),Vector3(3,3,.4),Color.WHITE)
	var nail_target: CharacterBody3D = game.spawn_enemy(3,Vector3(0,6,-5))
	await physics_frame
	await physics_frame
	covered_spitter.set_meta("alert",true)
	covered_spitter.set_meta("pursuit_left",1.0)
	covered_spitter.set_meta("attack",-.1)
	covered_spitter.set_meta("attack_warning",.2)
	game.tick_enemies(.1)
	verify(float(covered_spitter.get_meta("attack_warning")) == 0.0 and game.orbs.is_empty(),
		"A solid wall cancels an active Spitter windup")
	game.weapon = 0
	game.nail_charges = 2
	game.fire_clock = 0.0
	game.reload_left = 0.0
	game.rounds[0] = 2
	var nail_damage: int = 24+game.power*8
	var target_hp: int = nail_target.get_meta("hp")
	game.fire()
	verify(int(nail_target.get_meta("hp")) == target_hp-nail_damage*2, "All three landed nails deal double damage")
	for side in [-1.0,1.0]:
		game.block(Vector3(side*.132,7.48,-3),Vector3(.08,.5,.08),Color.WHITE)
	await physics_frame
	await physics_frame
	game.fire_clock = 0.0
	target_hp = nail_target.get_meta("hp")
	game.fire()
	verify(int(nail_target.get_meta("hp")) == target_hp-nail_damage,
		"Side nails blocked by cover do not damage the center target")
	game.block(Vector3(0,6.9,.46),Vector3(3,3,.2),Color.WHITE)
	var close_shooter: CharacterBody3D = game.spawn_enemy(1,Vector3(0,6,2))
	var volatile_enemy: CharacterBody3D = game.spawn_enemy(5,Vector3(.2,6,1.4),1)
	var sheltered_enemy: CharacterBody3D = game.spawn_enemy(2,Vector3(.2,6,-.4))
	var exposed_enemy: CharacterBody3D = game.spawn_enemy(2,Vector3(1.0,6,1.6))
	await physics_frame
	await physics_frame
	game.health = 100
	game.damage_time = 0.0
	game.hurt_enemy(volatile_enemy,80,0)
	verify(int(sheltered_enemy.get_meta("hp")) == game.TYPE_HP[2]
		and int(exposed_enemy.get_meta("hp")) == game.TYPE_HP[2]-22 and game.health == 100,
		"Cover protects players and enemies from a volatile enemy blast")
	game.launch_orb(close_shooter,1)
	game.tick_orbs(.5)
	verify(game.health == 100 and game.orbs.is_empty(), "A spore cannot hit through thin cover")
	game.player.position.z = .88
	game.launch_orb(close_shooter,1)
	game.tick_orbs(.5)
	verify(game.health == 90 and game.orbs.is_empty(), "A spore still hits a player in front of cover")
	var first_box: MeshInstance3D = game.cube(game.room,Vector3.ZERO,Vector3(2,3,4),game.material(Color.RED))
	var second_box: MeshInstance3D = game.cube(game.room,Vector3.ONE,Vector3(2,3,4),game.material(Color.BLUE))
	verify(first_box.mesh == second_box.mesh and first_box.position != second_box.position
		and first_box.material_override != second_box.material_override, "Identical box dimensions share geometry only")
	verify(game.music.stream is AudioStreamOggVorbis and game.music.stream.loop
		and game.music.stream.get_length() > 34.0 and game.music.stream.get_length() < 35.0,
		"The Godot soundtrack fallback plays the packed raw loop")
	game.music.stop()
	for voice in game.voices: voice.stop()
	if is_instance_valid(helper.chrome):
		if is_instance_valid(helper.chrome.soundtrack):
			helper.chrome.soundtrack.stop()
		helper.chrome.feedback.stop()
	helper.clear_match("user://shroom-session.json")
	await helper.delete_data("profile")
	game.queue_free()
	await create_timer(.25).timeout
	print("OnKlav template: ",checks," checks")
	quit(1 if failures else 0)
