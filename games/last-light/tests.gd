extends SceneTree

const Scene := preload("res://main.tscn")
var checks := 0
var failures := 0

func check(condition: bool, what: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		push_error(what)

func step(game: Control, frames: int, direction: float = 0.0) -> void:
	for i in frames:
		game._step(1.0 / 60.0, direction)

func traverse_feature(game: Control, level: int, line := "Depot", use_hatch := true) -> bool:
	game._restart(level)
	for tick in 2600:
		if level == 10 and game.checkpoint:
			return true
		if game._near_signal():
			game.jump_requested = line == "Depot"
			game.strike_requested = line == "Floodline"
		elif game.grounded:
			for index in game.roofs.size():
				var car: Rect2 = game.roofs[index]
				if not car.has_point(Vector2(game.pos.x, car.position.y)):
					continue
				if level == 1 and game.pos.x > game.bridge_x - 142 and game.pos.y < 340:
					game.drop_requested = true
				elif level == 4 and game.pos.y < 340 and (index == 4 or index >= game.roofs.size() - 2):
					game.drop_requested = true
				elif level == 8 and game.branch == "Depot" and ((use_hatch and index == 5) or index == game.roofs.size() - 1) and game.pos.y < 340:
					game.drop_requested = true
				elif level == 7 and not game.powered and index == 3 and game.pos.y < 340:
					game.drop_requested = true
				elif game.pos.x >= game.goal_x - 65 and game.pos.y < 340 and game._goal_deck() == 365.0:
					game.drop_requested = true
				elif game.pos.y == 365 and car.position.y >= 289 and game.pos.x < car.end.x - 100:
					game.jump_requested = true
				elif car.end.x - game.pos.x < 24.0 and index < game.roofs.size() - 1:
					game.jump_requested = true
				break
		game.strike_requested = game.strike_requested or (not game._near_signal() and tick % 16 == 0)
		game._step(1.0 / 60.0, 1.0)
		if game.finished or game.defeated:
			break
	if not game.finished:
		print("Feature route ", level, " stopped at ", game.pos, ", health ", game.health, ", relay ", game.powered)
	return game.finished

func touch_action(game: Control, action: String, finger: int, pressed: bool) -> void:
	for button in game._buttons():
		if button[1] != action:
			continue
		var event := InputEventScreenTouch.new()
		event.index = finger
		event.position = game._layout() * button[0]
		event.pressed = pressed
		game._input(event)
		return

func win_arena_with_touch(game: Control) -> bool:
	game._restart(10)
	game.pos = game.route_checkpoint
	var warden: Dictionary = game.enemies.back()
	var move := ""
	for tick in 3600:
		var next := ""
		var horizontal: float = warden.x - game.pos.x
		var warning: bool = warden.phase.ends_with("warning")
		if warden.hp <= 0 or (not warning and absf(horizontal) > 73 and warden.phase != "charge"):
			next = "right" if warden.hp <= 0 or horizontal > 0 else "left"
		if warden.hp > 0 and not warning and warden.phase == "recovery" and absf(horizontal) < 95:
			if game.face * horizontal < -12:
				next = "right" if horizontal > 0 else "left"
		if next != move:
			if not move.is_empty():
				touch_action(game, move, 1, false)
			if not next.is_empty():
				touch_action(game, next, 1, true)
			move = next
		if warning and warden.cool < 0.18 and game.grounded:
			touch_action(game, "jump", 2, true)
			touch_action(game, "jump", 2, false)
		if warden.phase == "recovery" and game.strike <= 0.0 and absf(horizontal) < 95 and absf(warden.y - game.pos.y) < 31:
			touch_action(game, "strike", 3, true)
			touch_action(game, "strike", 3, false)
		game._step(1.0 / 60.0, game._direction())
		if game.finished or game.defeated:
			break
	if not game.finished:
		print("Arena touch run stopped: hp=", game.health, ", warden=", warden.hp, ", phase=", warden.phase, ", at=", game.pos)
	return game.finished

func _initialize() -> void:
	run.call_deferred()

func run() -> void:
	var game := Scene.instantiate()
	root.add_child(game)
	game.set_anchors_and_offsets_preset(Control.PRESET_TOP_LEFT)
	game.size = Vector2(1000, 540)
	game.set_physics_process(false)
	var session := root.get_node("OnKlavGame")
	session.session_started = true
	session.resume_game()
	game._start_campaign()
	game._physics_process(1.0)
	check(game._intro_active() and game.time == 0.0 and game.pos == Vector2(135, 365),
		"The opening runs before any route movement or time")
	var play := InputEventScreenTouch.new()
	play.pressed = true
	play.position = game._layout() * game._intro_play_bounds(1000.0).get_center()
	game._input(play)
	check(game._intro_active(), "The opening cannot be skipped")
	game._physics_process(10.0)
	check(game.intro_time < 11.6 and game._intro_active(), "Play waits for the full opening")
	game._physics_process(10.0)
	check(is_equal_approx(game.intro_time, 11.6) and game._intro_active() and game.time == 0.0,
		"The final shot holds without starting gameplay")
	game._input(play)
	check(not game._intro_active() and game.touches.is_empty(), "Touching Play starts the route without leaking input")
	check(game._restore(game._snapshot()) and not game._intro_active(), "Resuming after Play opens directly into gameplay")
	game._start_campaign()
	check(game._restore(game._snapshot()) and game._intro_active(), "Reopening before Play preserves the opening gate")
	game._restart(1)
	check(game.health == 4 and game.pos == Vector2(135, 365), "New route state")
	step(game, 36, 1)
	check(game.pos.x > 260 and game.grounded, "Walking along the mail car")
	check(game._anchor_target() == 0, "First tether is reachable from the interior")
	game.tether_requested = true
	step(game, 56, 1)
	check(game.pos.x > 402 and game.pos.y <= 278, "Tether carries the player onto the next car roof")
	game.drop_requested = true
	step(game, 26)
	check(game.pos.y == 365, "Drop passes through a roof and lands inside")
	game._restart(8)
	game.pos = game.junction
	game.jump_requested = true
	step(game, 1)
	check(game.branch == "Depot", "Jump selects Depot at the junction")
	var depot_goal: float = game.goal_x
	check(game._restore(game._snapshot()) and game.branch == "Depot" and game.pos == game.junction,
		"Checkpoint restores the selected route")
	game.pos = Vector2(game.goal_x, 365)
	step(game, 1)
	check(not game.finished, "Depot exit requires its relay")
	game.pos = game.relay
	game._attack()
	check(game._restore(game._snapshot()) and game.powered, "Depot repairs survive resuming")
	game._restart(8)
	game.pos = game.junction
	game.strike_requested = true
	step(game, 1)
	check(game.branch == "Floodline" and game.goal_x != depot_goal, "Floodline changes the route geometry")
	game.pos = Vector2(game.goal_x + 3, 365)
	game.time = 2.6
	step(game, 1)
	check(not game.finished and not game.rescue and game.health == 3, "Floodline requires the beacon and has live water")
	check(not game._restore({"version": -1}), "Reject invalid save version")
	var before_bad_save: Dictionary = game._snapshot()
	for malformed in [null, [], {}, 1.5, "5"]:
		check(not game._restore({"version": 1, "level": malformed}) and game._snapshot() == before_bad_save,
			"Invalid level data is rejected without changing the route")
	game._restart(1)
	game.enemies.clear()
	game.pos = Vector2(game.goal_x, game._goal_deck() - 20.0)
	game._step(0.0, 0.0)
	check(game.finished and game.checkpoint and not game.grounded, "An airborne exit records the completion checkpoint")
	var airborne_save: Dictionary = game._snapshot()
	check(game._restore(airborne_save) and game.finished, "An airborne completion survives reopening")
	airborne_save.signal = false
	check(game._restore(airborne_save) and game.finished and game.checkpoint,
		"Older airborne completions recover their missing checkpoint")
	game._restart(1)
	game.health = 1
	game.pickups.append({"kind": 0, "taken": false, "pos": game.pos})
	game.shots.append({"pos": game.pos - Vector2(0, 24), "vx": 235.0, "life": 1.0})
	game._tick_course(0.0)
	check(game.defeated and game.health == 0 and not game.pickups.back().taken,
		"A fatal shot prevents health pickups from reviving the player in the same frame")
	game._restart()
	var layout: Transform2D = game._layout()
	var move_touch := InputEventScreenTouch.new()
	move_touch.index = 2
	move_touch.pressed = true
	move_touch.position = layout * game._buttons()[1][0]
	game._input(move_touch)
	var jump_touch := InputEventScreenTouch.new()
	jump_touch.index = 3
	jump_touch.pressed = true
	jump_touch.position = layout * game._buttons()[4][0]
	game._input(jump_touch)
	check(game._direction() == 1.0 and game.jump_requested, "Movement and jump can be held on different fingers")
	move_touch.pressed = false
	game._input(move_touch)
	check(game._direction() == 0.0 and game.touches.has(3), "Releasing movement keeps the other touch intact")
	var orphan_drag := InputEventScreenDrag.new()
	orphan_drag.index = 2
	orphan_drag.position = move_touch.position
	game._input(orphan_drag)
	check(game._direction() == 0.0 and not game.touches.has(2), "A drag cannot acquire a control without a fresh press")
	var live_area: Rect2 = game._play_area()
	check(live_area.position.y == 0 and not session.chrome.visible, "Landscape hides the toolbar and reclaims its space")
	var saved_position: Vector2 = game.pos
	game.size = Vector2(540, 1000)
	check(session.chrome.visible and game._play_area().position.y > 0, "Portrait restores the toolbar")
	check(game.touches.is_empty() and game._buttons().is_empty(), "Rotation releases held fingers and disables portrait gameplay controls")
	game._physics_process(0.25)
	check(game.pos == saved_position, "Portrait holds play")
	var portrait_jump := InputEventKey.new()
	portrait_jump.physical_keycode = KEY_SPACE
	portrait_jump.pressed = true
	game._input(portrait_jump)
	check(not game.jump_requested, "Portrait input cannot queue a jump for landscape")
	game.size = Vector2(1000, 540)
	game._physics_process(0.0)
	check(not session.chrome.visible and game.pos == saved_position and game._buttons().size() == 5, "Landscape returns to the same game frame")
	for viewport in [Vector2(1536, 691), Vector2(915, 412), Vector2(640, 360), Vector2(800, 600)]:
		game.size = viewport
		var play_area: Rect2 = game._play_area()
		var screen: Transform2D = game._layout()
		var width: float = game._view_width(play_area)
		check(is_equal_approx(width * screen.x.x, play_area.size.x), "Landscape scene fills the available width")
		var last_x := -1000.0
		for button in game._buttons():
			var point: Vector2 = button[0]
			var bounds: Rect2 = game._button_bounds(button)
			check(play_area.grow(0.01).encloses(screen * bounds), "Entire touch target fits in the landscape play area")
			check(bounds.position.y > 389 and bounds.position.x > last_x, "Buttons clear the trains and each other")
			check(game._pad(screen.affine_inverse() * (screen * point)) == button[1], "Visible control matches its landscape touch target")
			check((button[2].is_empty() and bounds.size.x == bounds.size.y) if button[1] in ["left", "right"] else bounds.size.x > bounds.size.y, "Arrows have no label and action buttons are shorter than they are wide")
			if button[1] not in ["left", "right"]:
				check(game._pad(bounds.end - Vector2(2, 2)) == button[1], "Action works at the button corner")
				check(game._pad(point + Vector2(0, bounds.size.y * 0.5 + 6)) == button[1], "Action accepts a near miss")
			check(game._pad(point + Vector2(0, bounds.size.y * 0.5 + 13)) == "", "Touch beyond the hit margin is ignored")
			last_x = bounds.end.x
	for player in game.sound_players.values():
		check(player.stream.get_length() > 0 and player.playback_type == AudioServer.PLAYBACK_TYPE_DEFAULT, "Each cue loads and follows the platform audio backend")
	var score := game.music.stream as AudioStreamWAV
	check(score.format == AudioStreamWAV.FORMAT_16_BITS and score.mix_rate == 44100 and is_equal_approx(score.get_length(), 32.0), "Music ships as uncompressed 44.1 kHz PCM")
	check(score.loop_mode == AudioStreamWAV.LOOP_FORWARD and score.loop_end > 0, "Music uses one gapless forward-loop sample")
	game._restart(1)
	game.pos = Vector2(game.roofs[0].end.x + 12.0, 441)
	game._reset_to_landing()
	check(game.health == 4 and game.pos.y == game.roofs[0].position.y, "Missing the practice swing retries without damage")
	game.pos = Vector2(game.roofs[1].position.x + 30, game.roofs[1].position.y)
	step(game, 1)
	check(game.practice_swing_done, "Landing on the second car ends practice recovery")
	game._restart(9)
	game.checkpoint = true
	game.pos = Vector2(game.goal_x - 50.0, 450.0)
	game.camera_x = game.goal_x - 805.0
	game.jump_buffer = 0.12
	game.tether_buffer = 0.12
	game.tether_landing = 0.25
	game.ignore_roof = 0.3
	game._reset_to_landing()
	check(game.pos == game.route_checkpoint and game.grounded and game.health == 3,
		"A fall returns the player to solid ground at their checkpoint")
	check(game.jump_buffer == 0.0 and game.tether_buffer == 0.0 and game.tether_landing == 0.0 and game.ignore_roof == 0.0
		and game.camera_x == clampf(game.pos.x - 385.0, 0.0, game.goal_x - 805.0),
		"Fall recovery drops stale controls and centers the camera on the player")
	game._restart(4)
	game.health = 2
	game.shield = true
	game.pickups[0].taken = true
	game.pos = game.route_checkpoint
	step(game, 1)
	var recorded: Dictionary = game._snapshot()
	check(game._restore(recorded) and game.health == 2 and game.shield and game.pickups[0].taken,
		"Checkpoint restores recorded health, shield and collected pickups")
	game.cleared_levels = 3
	game._start_replay(2)
	check(game.level == 2 and not game.campaign_return.is_empty(), "Replay starts without replacing the campaign checkpoint")
	var replay: Dictionary = game._snapshot()
	check(game._restore(replay) and game.level == 2 and not game.campaign_return.is_empty(),
		"Reopening a replay retains its campaign return")
	var quit_session := root.get_node("OnKlavGame")
	var original_path: String = quit_session.path
	quit_session.path = "user://last-light-test-quit.json"
	check(quit_session._save_quit_state() == OK, "Quitting a replay saves the campaign instead of clearing the slot")
	var quit_save: Dictionary = quit_session.load_local(quit_session.path)
	check(quit_save.get("exists", false) and quit_save.state.level == 4 and not quit_save.state.has("campaign_return"),
		"Replay quit writes the original campaign checkpoint")
	quit_session.clear_match(quit_session.path)
	quit_session.path = original_path
	game._return_from_replay()
	check(game.level == 4 and game.health == 2 and game.pickups[0].taken and game.campaign_return.is_empty(),
		"Returning from replay restores the original campaign")
	check(traverse_feature(game, 1), "Complete First Light from spawn using movement and actions")
	var session_bridge := root.get_node("OnKlavGame")
	check(session_bridge.resume_check.call(game._snapshot()) == true, "Route one completion can resume at the Next Level dialog")
	var result_save: Dictionary = game._snapshot()
	check(game._restore(result_save) and game.finished and game.next_button.visible, "Completion dialog survives resume")
	game.next_button.pressed.emit()
	check(game.level == 2 and not game.finished and not game.next_button.visible, "Next Level starts the second route")
	check(game._restore(game._snapshot()) and game.level == 2, "Second route survives save and resume")
	check(traverse_feature(game, 2), "Finish the rooftop run from spawn")
	check(game.night_split[0] == 1.0 and game.night_split[1] == 1.0,
		"Both Night Run couplers separate during a full playthrough")
	game._restart(2)
	var mail_edge: float = game.roofs[1].end.x
	var mail_start: float = game.roofs[2].position.x
	var mail_ring: float = game.anchors[0].x
	var mail_vent: float = game.hazards[0].x
	game.pos = Vector2(mail_edge - 100.0, game.roofs[1].position.y)
	game._tick_night_split(0.42)
	check(is_equal_approx(game.roofs[2].position.x - mail_start, 21.5)
		and game.platforms[2] == game.roofs[2] and game.platforms[game.roofs.size() + 2] == game.floors[2],
		"Mail car collision follows the visible car during separation")
	check(is_equal_approx(game.anchors[0].x - mail_ring, 21.5) and is_equal_approx(game.hazards[0].x - mail_vent, 21.5),
		"Mail car carries its tether ring and steam vent")
	game.pos = Vector2(mail_edge + 22.0, 441.0)
	game._reset_to_landing()
	check(game.health == 4 and game.grounded and is_equal_approx(game.pos.x, mail_edge - 44.0),
		"A missed first crossing returns the player to the launch mark with full health")
	var freight_edge: float = game.roofs[4].end.x
	game.pos = Vector2(freight_edge - 21.0, game.roofs[4].position.y)
	game._tick_night_split(1.0)
	game.grounded = true
	check(game._jump_ready_ring() == 1, "The second tether ring is previewed from the freight takeoff mark")
	game.jump_requested = true
	var caught_freight := false
	for frame in 14:
		step(game, 1, 1)
		caught_freight = caught_freight or game.tether == 1
	check(caught_freight, "A held directional jump catches the second ring after full separation")
	for level in range(1, 11):
		game._restart(level)
		check(game.anchors.size() in [3, 4], "Every level has three or four authored tether rings")
	game._restart(8)
	game._set_branch("Floodline")
	check(game.anchors.size() in [3, 4], "The Floodline branch also has enough tether opportunities")
	game._restart(1)
	game.pos = Vector2(game.goal_x - 27, 365)
	step(game, 1)
	check(game.finished, "Touching the signal's leading edge completes the level")
	game._restart(1)
	game.pos = Vector2(game.goal_x - 28, 365)
	step(game, 1)
	check(not game.finished, "The signal requires contact")
	game._restart(2)
	check(game.hazards.size() == 3 and game.enemies.size() >= 3, "Night Run has steam vents and multiple guards")
	game.pos = game.hazards[0] - Vector2(0, 15)
	game.time = 2.0
	game._tick_course(0.0)
	check(game.health == 3, "Live steam hits an exposed courier above the vent")
	for level in range(3, 11):
		game._restart(level)
		check(game.roofs.size() >= 8 and game.goal_x > 2700 and game.pickups.size() >= 3, "Level %d has its longer course and pickups" % level)
	for stage in [3, 4, 5, 6, 7, 9]:
		check(traverse_feature(game, stage), "Finish authored level %d using regular controls" % stage)
	check(traverse_feature(game, 6) and game.storm_swing == 3 and game.storm_zone == 2,
		"The last swing reaches the lee of the bridge and drops the storm mix")
	check(traverse_feature(game, 8, "Depot"), "Reach and repair the Depot relay from spawn")
	check(traverse_feature(game, 8, "Depot", false), "Depot can be cleared by crossing the showcase car on its roof")
	game._restart(8)
	game._set_branch("Depot")
	var depot: Rect2 = game.roofs[5]
	game.pos = Vector2(depot.position.x + 48, depot.position.y)
	game.grounded = true
	game.drop_requested = true
	step(game, 26)
	check(game._inside_depot_carriage() and game._depot_cover() and game.pos.y == 365.0,
		"Drop at the hatch reaches the lower-deck cover bay")
	var guard: Dictionary = game.enemies[0]
	game.pos = Vector2(guard.x - 52, guard.y)
	game.face = 1.0
	game._attack()
	check(guard.hp == 2 and guard.has("block_until"), "Armored guard blocks a head-on strike while braced")
	guard.cool = 0.9
	game._attack()
	check(guard.hp == 1, "Armored guard is vulnerable while recovering")
	game.pos = Vector2(depot.end.x - 35.0, 365.0)
	game.grounded = true
	game.jump_requested = true
	step(game, 55)
	check(game.pos.y == depot.position.y and game.grounded, "Jump at the rear ladder returns to the roof")
	check(traverse_feature(game, 8, "Floodline"), "Reach the rooftop crew from spawn")
	game._restart(8)
	game._set_branch("Floodline")
	var flood_x: float = game._floodline_start() + 45.0
	game.pos = Vector2(flood_x, 365.0)
	game.grounded = true
	game.time = 1.0
	step(game, 1)
	check(game.health == 4 and not game.water_hit and game._route_hint().begins_with("WATER RISING"),
		"An amber warning precedes the surge while the deck is still dry")
	game.time = 2.6
	step(game, 1)
	check(game.health == 3 and game.water_hit, "The rising water reaches the lower deck")
	game.pos = Vector2(flood_x, game.final_roof_y)
	game.velocity = Vector2.ZERO
	game.grounded = true
	game.hurt = 0.0
	step(game, 1)
	check(game.health == 3 and not game.water_hit, "The rooftop stays above the same flood surge")
	game.pos = Vector2(flood_x, 365.0)
	game.velocity = Vector2.ZERO
	game.time = 2.6
	game.hurt = 0.12
	step(game, 1)
	check(not game.water_hit and game.health == 3, "Entering water while invulnerable leaves the surge hit pending")
	step(game, 10)
	check(game.water_hit and game.health == 2, "Water hits when invulnerability expires during the same surge")
	game.pos = Vector2(game.goal_x - 25.0, game.final_roof_y - 95.0)
	game.velocity = Vector2.ZERO
	step(game, 1)
	check(not game.rescue and not game.finished, "Swinging high over the rooftop crew does not rescue them")
	game.pos = Vector2(game.goal_x - 25.0, game.final_roof_y)
	game.velocity = Vector2.ZERO
	step(game, 1)
	check(game.rescue and game.finished, "Reaching the rooftop crew still completes Floodline")
	game._restart(6)
	game.time = 3.0
	game.pos = Vector2(180, game.roofs[0].position.y)
	step(game, 1)
	check(game.pos.x == 180 and game.storm_zone == 0 and game.score_track == "rail",
		"The approach is sheltered even on the roof")
	var span: Rect2 = game.roofs[1]
	var exposed_x := span.position.x + 55.0
	game.pos = Vector2(exposed_x, 365)
	step(game, 1)
	check(game.pos.x == exposed_x and game.storm_zone == 1 and game.score_track == "storm",
		"The bridge brings in the storm score, with shelter below deck")
	game.pos = Vector2(exposed_x, span.position.y)
	step(game, 1)
	check(game.pos.x < exposed_x, "A gust pushes an exposed courier")
	var windbreak: Rect2 = game._storm_windbreak(game.roofs[7])
	var sheltered_x := windbreak.get_center().x
	game.pos = Vector2(sheltered_x, windbreak.end.y - 2.0)
	game.velocity = Vector2.ZERO
	step(game, 1)
	check(is_equal_approx(game.pos.x, sheltered_x) and game._storm_wind_speed() == 0.0,
		"The roof windbreak shelters a grounded courier during a gust")
	game.pos.y = windbreak.position.y - 14.0
	check(game._storm_wind_speed() > 0.0, "Jumping above the windbreak re-exposes the courier")
	game.time = 1.0
	check(game._storm_wind_speed() == 0.0, "The green-flag launch window has no wind push")
	game.pos = Vector2(game.roofs.back().position.x + 100.0, game.final_roof_y)
	step(game, 1)
	check(game.storm_zone == 2 and game.score_track == "rail", "The far side returns to calm and the main score")
	game._restart(9)
	game.pos.x = 150
	game._tick_collapse()
	game.pos.x = 110
	game.time = 1.3
	game._tick_collapse()
	check(game.collapse_warned.has(0) and game.collapsed_until == 0.0, "A cracked car warns before falling")
	game.time = 2.2
	game._tick_collapse()
	check(game.collapsed_until == game.roofs[0].end.x, "A failed car is removed from the route")
	game._restart(9)
	var brake_index: int = game.roofs.size() - 2
	var brake: Rect2 = game.roofs[brake_index]
	var stable_car: Rect2 = game.roofs.back()
	game.pos = Vector2(brake.end.x - 25.0, brake.position.y)
	game.grounded = true
	game.collapse_times[brake_index] = 0.0
	game.time = 0.9
	game._tick_collapse()
	check(game.collapse_warned.has(brake_index), "The last car warns earlier than the preceding cars")
	game.jump_requested = true
	var escaped := false
	var used_final_ring := false
	for frame in 75:
		step(game, 1, 1)
		used_final_ring = used_final_ring or game.tether == game.anchors.size() - 1
		escaped = escaped or (game.grounded and game.pos.x >= stable_car.position.x)
	check(used_final_ring and escaped, "The last tether carries the courier to the stable train")
	check(game.collapsed_until == brake.end.x, "The brake van falls behind the final crossing")
	game._restart(1)
	game.pos = Vector2(game.bridge_x, game.final_roof_y)
	step(game, 1)
	check(game.health == 3, "First Light bridge hits an exposed player")
	game._restart(1)
	game.pos = Vector2(game.bridge_x, 365)
	step(game, 1)
	check(game.health == 4, "First Light bridge leaves the lower deck safe")
	check(game._route_hint().contains("STAY ON THE LOWER DECK"), "The bridge confirms the safe route after dropping")
	game.pos.x = game.bridge_x + 45.0
	game.velocity.x = 237.0
	step(game, 1, 1)
	check(game.message.begins_with("BRIDGE CLEAR"), "Passing under the bridge points the player toward the exit")
	game._restart(2)
	game.pos = game.pickups[0].pos
	game.health = 2
	game._tick_course(0.0)
	check(game.pos.y == 365 and game.health == 4, "Night Run rewards the lower-deck detour")
	game._restart(4)
	game.time = 1.3
	check(not game._hazard_live(0) and game._hazard_live(2) and not game._hazard_live(3),
		"The warning precedes the first bank's live pulse")
	game.pos = Vector2(game.hazards[2].x, 365.0)
	game._tick_course(0.0)
	check(game.health == 4, "The lower deck shelters the courier from the live bank")
	game.pos = game.hazards[2] - Vector2(0, 14.0)
	game._tick_course(0.0)
	check(game.health == 3, "A low jump through the active arc takes damage")
	game.time = 2.0
	check(game._hazard_live(0) and not game._hazard_live(2) and game._hazard_live(3),
		"The surge moves to the next bank and leaves the first bank safe")
	game.time = 3.15
	check(not game._hazard_live(3) and game._hazard_live(4), "The last bank completes the wave")
	game.pos = Vector2(game.roofs[4].end.x - 30.0, 365.0)
	game.velocity = Vector2.ZERO
	game.grounded = true
	game.jump_requested = true
	step(game, 56)
	check(game.grounded and game.pos.y == game.roofs[4].position.y, "The safe-deck ladder rejoins the roof past the surge banks")
	game.pos = Vector2(game.goal_x, 365)
	step(game, 1)
	check(not game.finished, "Live Wires exit requires its breaker")
	game.pos = game.relay
	game._attack()
	game.time = 3.15
	check(game.powered and not game._hazard_live(2) and not game._hazard_live(3) and not game._hazard_live(4),
		"Striking the breaker disarms every surge bank")
	check(game._restore(game._snapshot()) and game.powered, "Live Wires checkpoint retains breaker state")
	game._restart(3)
	var sentry: Dictionary = game.enemies.front()
	game.pos = Vector2(game._beam_x(sentry), 365)
	game._tick_sentry(sentry, 0.01)
	check(sentry.wind == 0.0, "The lower deck protects the courier from the searchlight")
	game.pos = Vector2(sentry.x, sentry.y - 140)
	check(not game._inside_searchlight(sentry), "Courier above the visible cone is not detected")
	game.pos = Vector2(lerpf(sentry.x, game._beam_x(sentry), 0.5), sentry.y - 65)
	check(game._inside_searchlight(sentry), "Searchlight detection follows the slanted cone")
	game.pos = Vector2(game._beam_x(sentry), sentry.y)
	game._tick_sentry(sentry, 0.01)
	check(sentry.wind > 0.0, "An exposed courier triggers the sentry")
	game._tick_sentry(sentry, 0.60)
	check(game.shots.size() == 1, "The sentry fires a telegraphed shot")
	var pair: Array = game.enemies.filter(func(enemy): return enemy.get("signal_light", false))
	check(pair.size() == 2, "The signal car has a paired roof patrol")
	var center: float = game.relay.x
	game.time = 0.0
	var initial_gap: float = game._beam_x(pair[1]) - game._beam_x(pair[0])
	game.time = PI / (2.0 * 1.65)
	check(game._beam_x(pair[1]) - game._beam_x(pair[0]) > initial_gap
		and is_equal_approx((game._beam_x(pair[1]) + game._beam_x(pair[0])) * 0.5, center),
		"The two lamps sweep in opposite directions around the signal car")
	game.pos = Vector2(center, game.roofs[3].position.y)
	game._attack()
	check(not game.powered, "The rooftop cannot reach the lower switch")
	game.grounded = true
	game.drop_requested = true
	step(game, 34)
	check(game.pos.y == 365.0 and game.health == 4, "Dropping through the signal roof reaches cover safely")
	pair[0].wind = 0.01
	game._tick_sentry(pair[0], 0.02)
	game._attack()
	check(game.powered and game.checkpoint and game.shots.all(func(shot): return not shot.get("signal_light", false)),
		"Striking the switch disables the pair and clears their fire")
	check(game._restore(game._snapshot()) and game.powered, "The disabled signal car survives resume")
	game.pos = Vector2(game.roofs[3].end.x - 37.0, 365.0)
	game.grounded = true
	game.jump_requested = true
	step(game, 56)
	check(game.grounded and game.pos.y == game.roofs[3].position.y, "The marked ladder returns the courier to the roof")
	game._restart(7)
	game.pos = game.relay - Vector2(85, 0)
	game.face = 1
	var relay_panel: Vector2 = game.relay - Vector2(0, 42)
	check(game._lantern_reveal(relay_panel) > 0.9, "Blackout lantern reveals a relay in front of the courier")
	game.face = -1
	check(game._lantern_reveal(relay_panel) < 0.1, "Facing away loses the relay spotlight")
	var shutter: float = game._blackout_shutter_x()
	game.pos = Vector2(shutter - 25.0, game.roofs[3].position.y)
	game.grounded = true
	step(game, 12, 1)
	check(is_equal_approx(game.pos.x, shutter - 14.0), "The closed shutter stops a rooftop approach")
	game.drop_requested = true
	step(game, 35, 1)
	check(game.pos.x > shutter and game.pos.y == 365.0, "Dropping to the lower deck passes under the closed shutter")
	game.pos = Vector2(game.goal_x, 365)
	step(game, 1)
	check(not game.finished, "Blackout exit requires power")
	game.pos = game.relay
	game._attack()
	check(game.powered, "Striking the relay powers the tunnel")
	game.time += 0.3
	check(game._blackout_light(game.relay.x) > 0.99 and game._blackout_light(game.goal_x) == 0.0,
		"Restored light reaches the relay before the distant exit")
	game.time += 0.2
	game.pos = Vector2(shutter - 25.0, game.roofs[3].position.y)
	game.velocity = Vector2.ZERO
	game.grounded = true
	step(game, 18, 1)
	check(game.pos.x > shutter + 14.0, "The raised shutter clears the rooftop route")
	check(game._restore(game._snapshot()) and game.powered and game.pos == game.route_checkpoint
		and game._blackout_light(game.goal_x) == 1.0, "A restored checkpoint starts with the entire tunnel already lit")
	check(game._restore({"version": 1, "level": 7, "signal": false, "powered": true})
		and game.checkpoint and game.powered, "Older saves with restored power resume at the Blackout checkpoint")
	check(game._restore({"version": 1, "level": 9, "signal": true, "branch": "Depot"}) and game.level == 9, "Campaign progress resumes at the correct stage")
	game._restart(10)
	var arrival: Dictionary = game.enemies.back()
	game.pos = Vector2(arrival.x - 330, arrival.y)
	game._tick_warden(arrival, 0.01)
	check(arrival.phase == "arrival" and game.shots.is_empty(), "Warden entrance warns before combat starts")
	game._tick_warden(arrival, 1.21)
	check(arrival.phase == "idle" and game.shots.is_empty(), "Warden entrance does not fire an attack")
	check(game._warden_ladders().is_empty(), "Warden service hatches start locked")
	arrival.hp = 8
	arrival.phase = "recovery"
	arrival.cool = 10.0
	var ladder_x: float = game._warden_ladders()[0]
	game.pos = Vector2(ladder_x, arrival.y)
	game.grounded = true
	game.drop_requested = true
	step(game, 34)
	check(game.grounded and game.pos.y == 365.0 and game._warden_ladder_near(),
		"Drop reaches the Warden service deck at an unlocked ladder")
	game.jump_requested = true
	step(game, 58)
	check(game.grounded and game.pos.y == arrival.y, "A ladder jump returns the courier to the Warden roof")
	arrival.hp = 4
	arrival.phase = "idle"
	game._tick_warden(arrival, 0.01)
	var retreat_start: float = arrival.x
	game._tick_warden(arrival, 0.7)
	check(arrival.retreated and arrival.phase == "idle" and arrival.x > retreat_start,
		"Warden falls back to the furnace once after reaching critical health")
	game._restart(1)
	game.grounded = true
	game.jump_requested = true
	step(game, 1)
	check(game.takeoff_pose > 0.0 and not game.grounded, "Jump triggers a distinct takeoff pose")
	var saw_landing := false
	for frame in 55:
		step(game, 1)
		saw_landing = saw_landing or (game.grounded and game.landing_pose > 0.0)
	check(saw_landing, "Landing triggers a separate recovery pose")
	check(traverse_feature(game, 10), "Reach the Iron Warden arena checkpoint from spawn")
	game._restart(10)
	game.pos = Vector2(game.goal_x + 3, game.final_roof_y)
	step(game, 1)
	check(not game.finished and game._boss_alive(), "Final exit is locked while the boss is alive")
	var warden: Dictionary = game.enemies.back()
	game.pos = Vector2(warden.x - 84, warden.y)
	warden.cool = 0.0
	warden.phase = "sweep_warning"
	game._attack()
	check(warden.hp == 12, "Warden shield blocks hits while charging")
	warden.phase = "recovery"
	warden.cool = 2.0
	game.charged = true
	game._attack()
	check(warden.hp == 10 and not game.charged, "Charged strike damages the vulnerable Warden")
	warden.hp = 6
	warden.phase = "sweep_warning"
	warden.cool = 0.01
	game._tick_warden(warden, 0.02)
	check(game.shots.size() == 2 and warden.phase == "recovery", "Warden sweeps both sides before exposing its furnace")
	warden.phase = "idle"
	warden.cycle = 1
	game._tick_warden(warden, 0.01)
	check(warden.phase == "charge_warning", "Warden telegraphs its later charge")
	game._tick_warden(warden, 0.86)
	var original_x: float = warden.x
	game.pos = Vector2(warden.x, warden.y - 88)
	game._tick_warden(warden, 0.22)
	check(warden.x < original_x and game.health == 4, "A tether-height courier avoids the moving charge")
	game._tick_warden(warden, 0.5)
	check(warden.phase == "recovery" and warden.brake_crash and warden.cool > 3.0,
		"Baiting a charge into the brake housing opens a longer furnace window")
	check(is_equal_approx(warden.x, game._warden_brake_stop(warden, -1.0)),
		"The housing stops the charge at the marked end of the roof")
	game.pos = Vector2(warden.x - 32, warden.y)
	game._attack()
	check(warden.hp == 5, "A strike damages the furnace during the brake stun")
	for hit in 5:
		game._attack()
	game.pos = Vector2(game.goal_x + 3, game.final_roof_y)
	step(game, 1)
	check(game.finished and game._show_delivery() and not game.next_button.visible,
		"Final goal starts the lantern delivery before offering replay")
	game._physics_process(2.5)
	check(game._show_delivery() and not game.delivered, "The station waits for the player")
	var pending: Dictionary = game._snapshot()
	check(session_bridge.resume_check.call(pending) and game._restore(pending) and game._show_delivery(),
		"An interrupted delivery remains resumable")
	touch_action(game, "strike", 3, true)
	touch_action(game, "strike", 3, false)
	game._physics_process(0.02)
	check(not game.delivered, "The lantern must be placed beside the lamp")
	touch_action(game, "right", 1, true)
	for frame in 90:
		game._physics_process(1.0 / 60.0)
	touch_action(game, "right", 1, false)
	touch_action(game, "strike", 3, true)
	touch_action(game, "strike", 3, false)
	game._physics_process(0.02)
	check(game.delivered and game._show_delivery(), "Touch controls can place the lantern and light the station")
	game._physics_process(0.5)
	check(game._station_light(600.0, 600.0) > 0.8 and game._station_light(150.0, 600.0) == 0.0,
		"The light reaches the pedestal before the far end of the station")
	var placed: Dictionary = game._snapshot()
	check(game._restore(placed) and game.delivered and game._show_delivery(),
		"Resuming after placement plays the restoration without asking for another delivery")
	game._physics_process(1.5)
	check(game._show_delivery() and not game.next_button.visible, "The restoration holds before showing replay")
	game._physics_process(3.2)
	check(not game._show_delivery() and game.next_button.visible and game.next_button.text == "Play Again",
		"Delivery ends at the victory screen with replay")
	check(session_bridge.resume_check.call(game._snapshot()) == false, "Final route completion is not resumable")
	check(win_arena_with_touch(game), "Actual touch actions can win the arena from its checkpoint")
	check(game._restore(game._snapshot()) and game._show_delivery() and not game._boss_alive(),
		"Reopening the pending delivery keeps the defeated Warden down")
	var completed_finale: Dictionary = game._snapshot()
	completed_finale.delivery_pending = false
	check(game._restore(completed_finale) and game.finished and not game._boss_alive() and not game._show_delivery(),
		"Reopening a completed finale keeps the Warden down")
	for retry_level in [9, 10]:
		game._restart(retry_level)
		game.health = 1
		game._damage(1.0)
		check(not game.retry_button.visible, "Checkpoint retry is hidden before reaching one")
		game._restart(retry_level)
		game.health = 1
		game.pos = game.route_checkpoint
		step(game, 1)
		game._damage(1.0)
		check(game.retry_button.visible and game.next_button.visible, "Defeat after a checkpoint offers both retry options")
		check(game._restore(game._snapshot()) and game.retry_button.visible, "Checkpoint retry is available after reopening a defeat")
		game.retry_button.pressed.emit()
		check(not game.defeated and game.health == 4 and game.pos == game.route_checkpoint and game.checkpoint,
			"Retry restores the checkpoint with full health")
		game.health = 1
		game._damage(1.0)
		game.next_button.pressed.emit()
		check(not game.checkpoint and game.pos == Vector2(135, 365), "Play Again restarts from the opening car")
	game._restart(7)
	check(game.score_track == "blackout" and game.music.stream.loop_mode == AudioStreamWAV.LOOP_FORWARD,
		"Blackout starts with its own looping arrangement")
	game.pos = game.relay
	game._attack()
	check(game.score_track == "rail", "Restoring tunnel power returns the full arrangement")
	game._restart(10)
	check(game.score_track == "warden" and game.music.stream.loop_mode == AudioStreamWAV.LOOP_FORWARD,
		"The Warden has its own looping arrangement")
	game._restart(3)
	check(game.anchors.size() == 3, "Sentry Line reserves rings for the roof lift and long crossing")
	var approach: Rect2 = game.roofs[3]
	var landing: Rect2 = game.roofs[4]
	game.pos = Vector2(approach.end.x - 2, approach.position.y)
	game.velocity.x = 237.0
	game.grounded = true
	game.jump_requested = true
	var landed := false
	game.anchors.clear()
	for frame in 55:
		game._step(1.0 / 60.0, 1.0)
		landed = landed or (game.grounded and game.pos.x >= landing.position.x)
	check(not landed, "A full-speed normal jump falls short of the long crossing")
	game._restart(3)
	game.pos = Vector2(approach.end.x - 2, approach.position.y)
	game.velocity.x = 237.0
	game.grounded = true
	game.jump_requested = true
	var attached := false
	landed = false
	for frame in 55:
		game._step(1.0 / 60.0, 1.0)
		attached = attached or game.tether >= 0
		landed = landed or (game.grounded and game.pos.x >= landing.position.x)
	check(attached and landed, "Jumping toward the higher ring auto-tethers and clears the long crossing")
	for lower_deck in [false, true]:
		game._restart(4)
		var departure: Rect2 = game.roofs[5]
		var destination: Rect2 = game.roofs[6]
		game.pos = Vector2(departure.end.x - 36, 365.0 if lower_deck else departure.position.y)
		game.grounded = true
		game.velocity.x = 237.0
		check(game._jump_ready_ring() >= 0, "Long crossing previews the jump ring from either deck")
		game.jump_requested = true
		attached = false
		landed = false
		for frame in 65:
			game._step(1.0 / 60.0, 1.0)
			attached = attached or game.tether >= 0
			landed = landed or (game.grounded and game.pos.x >= destination.position.x)
		check(attached and landed, "Early jump auto-tethers across Live Wires from either deck")
	game._restart(6)
	game.time = 3.0
	game.health = 2
	game.pos = game.pickups[0].pos
	game._update_storm_sequence(0.0)
	game._tick_course(0.0)
	check(game.pickup_feedback == "Health restored" and game.pickup_feedback_left > 0.0
		and game._route_hint().begins_with("WIND GUST"), "Wind warning and pickup confirmation coexist")
	for index in [2, 7]:
		game._restart(5)
		var car: Rect2 = game.roofs[index]
		var next_car: Rect2 = game.roofs[index + 1]
		var upper: Rect2 = game._freight_steps(car)[1]
		game.pos = Vector2(car.end.x - 38, car.position.y)
		game.grounded = true
		game.jump_requested = true
		step(game, 52)
		check(game.grounded and game.pos.y == upper.position.y, "A jump from the freight roof lands on the marked stack")
		check(game.shield if index == 2 else game.charged, "The upper cargo route grants its pickup")
		game.jump_requested = true
		var caught := false
		var across := false
		for frame in 68:
			step(game, 1, 1)
			caught = caught or game.tether >= 0
			across = across or (game.grounded and game.pos.x >= next_car.position.x)
		check(caught and across, "Launching from the high cargo catches the gantry ring and lands across the gap")
	game._restart(5)
	game.pos = Vector2(135, 365)
	game.tether_requested = true
	step(game, 1)
	check(game.tether < 0 and game.tether_buffer > 0, "An early tether tap is buffered")
	game.pos = game.anchors[0] + Vector2(-90, 72)
	step(game, 1, 1)
	check(game.tether >= 0 and game.tether_buffer == 0.0, "Buffered tether attaches when a ring becomes reachable")
	for direction in [-1.0, 0.0, 1.0]:
		game._restart(1)
		game.pos = Vector2(331, game.roofs[0].position.y)
		game.grounded = true
		game.jump_requested = true
		step(game, 1, direction)
		check((game.tether == 0) if direction > 0 else (game.tether < 0),
			"Only a jump toward a nearby forward ring auto-tethers")
	game._restart(1)
	game.pos = Vector2(331, game.roofs[0].position.y)
	step(game, 3, 1)
	check(game.tether < 0, "Walking under a ring does not auto-tether")
	game._restart(1)
	var scenery_start: float = game._scenery_travel(0.48)
	step(game, 60)
	check(game.camera_x == 0.0 and game._scenery_travel(0.48) > scenery_start + 60.0,
		"Scenery scrolls at train speed even while the courier stands still")
	game._restart(5)
	game.health = 1
	game._damage(1.0)
	check(game.defeated and game.next_button.text == "Play Again", "Defeat opens retry")
	check(session_bridge.resume_check.call(game._snapshot()) == true, "Defeat retains campaign progress")
	check(game._restore(game._snapshot()) and game.defeated and game.level == 5, "Reopening a defeated game restores the retry screen")
	game.next_button.pressed.emit()
	check(game.level == 5 and game.health == 4 and not game.defeated, "Retry restarts the failed level")
	game._restart()
	check(session_bridge.resume_check.call(game._snapshot()) == true, "Hosted save callback accepts its state argument and marks a running route resumable")
	game._restart(6)
	game.health = 2
	game.pos = game.pickups[0].pos
	game._tick_course(0.0)
	check(game.health == 4 and game.pickups[0].taken, "Health pickup restores two lights")
	game.pos = game.pickups[1].pos
	game._tick_course(0.0)
	game._damage(1.0)
	check(game.health == 4 and not game.shield, "Shield pickup absorbs one hit")
	game.pos = game.pickups[2].pos
	game._tick_course(0.0)
	check(game.charged, "Power pickup charges the next strike")
	session.session_started = true
	session.resume_game()
	game.touches[3] = "right"
	game.jump_requested = true
	game.jump_buffer = 0.14
	game.tether_buffer = 0.12
	session.toggle_pause()
	check(game.touches.is_empty() and not game.jump_requested and game.jump_buffer == 0.0
		and game.tether_buffer == 0.0, "Pausing clears held movement and queued actions")
	var resume_button := session.chrome.pause_notice.find_children("*", "Button", true, false)[0] as Button
	check(paused and resume_button.text == "Resume", "Paused notice has a Resume button")
	resume_button.pressed.emit()
	check(not paused and not session.paused, "Resume button unpauses the game")
	var rng := RandomNumberGenerator.new()
	rng.seed = 10981
	var healthy := true
	for tick in 36000:
		if tick % 1800 == 0:
			game.size = Vector2(500, 900)
			var before: Vector2 = game.pos
			game._physics_process(1.0 / 60.0)
			healthy = healthy and game.pos == before and game.touches.is_empty()
			game.size = Vector2(1100, 540)
		if game.finished or game.defeated:
			game._restart()
		game.jump_requested = rng.randf() < 0.02
		game.tether_requested = rng.randf() < 0.03
		game.strike_requested = rng.randf() < 0.06
		game.drop_requested = rng.randf() < 0.01
		game._step(1.0 / 60.0, 1.0 if tick % 400 < 350 else -1.0)
		healthy = healthy and game.pos.is_finite() and game.health >= 0 and game.health <= 4 and game.particles.size() < 100
	check(healthy, "Ten minutes of randomized input and rotation keep state and effects bounded")
	print("LAST_LIGHT_RESULT checks=%d failures=%d" % [checks, failures])
	quit(1 if failures > 0 else 0)
