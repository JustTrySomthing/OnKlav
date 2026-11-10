extends Control

const NIGHT := Color("#071322")
const TEAL := Color("#5ce3ce")
const GOLD := Color("#ffcb78")
const RED := Color("#ff796b")
var font := ThemeDB.fallback_font
const SLEEPER_ART := preload("res://art/sleeper.svg")
const FREIGHT_ART := preload("res://art/freight.svg")
const ARMOR_ART := preload("res://art/armor.svg")
const COURIER_COAT := preload("res://art/courier-coat.svg")
const GUARD_ART := preload("res://art/guard.svg")
const TRAIN_SPEED := 144.0
const WHEEL_RADIUS := 12.0
const AUTO_TETHER_RANGE := 132.0
const CROSSING_TETHER_RANGE := 208.0
const TETHER_RANGE := 266.0
const SAVE_VERSION := 1
const WORLD_ZOOM := 1.298
const ACTOR_SCALE := 1.08
const LAST_LEVEL := 10
const SENTRY_CAR := 3
const SWITCHGEAR_CAR := 4
const FREIGHT_STACK_CARS := [2, 7]
const STORM_WINDBREAK_CARS := [3, 5, 7]
const SURGE_BANKS := [2, 3, 4]
const NIGHT_SPLIT_CARS := [2, 5]
const NIGHT_SPLIT_TRAVEL := [43.0, 45.0]
const DELIVERY_SECONDS := 4.6
const INTRO_SECONDS := 11.6
const INTRO_HANDOFF := 4.8
const DELIVERY_PEDESTAL := 125.0
const WARDEN_SWEEP_SPEED := 430.0
const WARDEN_SWEEP_LIFE := 1.2
const WARDEN_SWEEP_HEIGHT := 22.0
const WARDEN_CHARGE_SPEED := 520.0
const WARDEN_CHARGE_TIME := 0.62
const LEVEL_NAMES := ["First Light", "Night Run", "The Sentry Line", "Live Wires", "High Freight",
	"Storm Crossing", "Blackout Run", "Signal Siege", "The Last Crossing", "The Iron Warden"]
# Authored car widths, roof heights, and gaps from the preceding car.
const ROUTES := {
	1: [[300, 301, 0], [240, 278, 42], [320, 266, 122], [315, 264, 55], [385, 265, 56]],
	2: [[290, 298, 0], [216, 266, 40], [190, 249, 115], [226, 270, 49],
		[175, 243, 55], [220, 248, 141], [205, 231, 56], [345, 246, 52]],
	4: [[310, 299, 0], [305, 262, 52], [290, 249, 58], [370, 268, 114],
		[340, 261, 55], [315, 252, 70], [360, 230, 219], [350, 257, 62]],
	3: [[300, 272, 0], [350, 260, 54], [290, 276, 68], [365, 262, 53],
		[310, 228, 217], [340, 270, 54], [280, 256, 70], [375, 263, 50]],
	5: [[285, 301, 0], [295, 260, 48], [305, 211, 55], [240, 254, 128],
		[300, 299, 75], [310, 253, 64], [285, 204, 62], [360, 271, 137]],
	7: [[315, 277, 0], [252, 270, 62], [350, 280, 85], [340, 258, 56],
		[305, 268, 66], [365, 250, 70], [290, 264, 56], [350, 235, 217]],
	6: [[310, 295, 0], [290, 272, 52], [322, 259, 70], [328, 268, 62],
		[280, 257, 116], [325, 268, 61], [262, 254, 125], [385, 273, 129]],
	8: [[320, 296, 0], [270, 270, 52], [310, 254, 113], [345, 267, 58],
		[310, 273, 59], [285, 268, 112], [320, 261, 61], [415, 269, 52]],
	9: [[320, 299, 0], [255, 264, 55], [235, 273, 69], [345, 256, 61],
		[245, 264, 112], [265, 254, 69], [245, 257, 122], [465, 273, 158]],
	10: [[340, 299, 0], [320, 268, 58], [320, 251, 58], [335, 269, 65],
		[315, 245, 58], [275, 269, 108], [310, 264, 64], [740, 250, 215]],
}
# One additional car per route, inserted before the terminal car.
const APPROACH_CARS := {
	1: [244, 274, 138], 2: [239, 258, 152], 3: [273, 266, 148],
	4: [279, 259, 140], 5: [272, 249, 143], 6: [266, 271, 64],
	7: [271, 259, 147], 8: [290, 274, 134], 9: [273, 264, 141], 10: [297, 269, 67],
}
const APPROACH_NAMES := ["PARCEL VAN", "VENT CAR", "GUARD VAN", "INSULATOR", "HIGH LOAD",
	"STORM SHELTER", "SERVICE CAR", "SIGNAL VAN", "BRAKE VAN", "ARMORED TENDER"]
const FLOODLINE_TAIL := [[260, 248, 102], [295, 257, 117], [280, 237, 81], [452, 250, 103]]
const FEATURE_HINTS := {
	6: "Wait behind the windbreaks, then jump for the ring when the flag turns green.",
	8: "Reach the rooftop signal. Choose Depot repairs or Floodline rescue.",
	9: "Keep moving. Cracked cars fall after their warning.",
	10: "Drop under the sweep. Tether over the charge. Strike the furnace.",
	1: "The last station is dark. Bring the lantern home.",
	2: "Watch for splitting cars. Hold toward the lit ring and jump.",
	4: "Watch the amber lamps. Cross between surges or drop under the wires.",
	3: "Cross the searchlights or drop into the signal car to cut their power.",
	5: "Use the gold cargo steps to reach the high gantry rings.",
	7: "Follow the reflectors. Strike the power relay to light the tunnel.",
}
const FEATURE_RESULTS := {6: "Across the storm.", 9: "You cleared the viaduct.",1: "First delivery complete.", 2: "You made the rooftop run.", 4: "Breaker off. The line is safe.", 3: "Searchlights behind you.", 5: "The canyon is clear.", 7: "Power restored. The train is through."}
const DEPOT_CARRIAGE := 5
const DEPOT_FLOOR := 365.0
const SEARCHLIGHT_HEIGHT := 130.0
const SEARCHLIGHT_HALF_WIDTH := 37.0
const EXIT_REACH := 27.0
const ARROW_SIZE := Vector2(86, 86)
const ACTION_SIZE := Vector2(96, 58)

var pos := Vector2(135, 365)
var velocity := Vector2.ZERO
var face := 1.0
var grounded := false
var coyote := 0.0
var jump_buffer := 0.0
var tether := -1
var tether_left := 0.0
var strike := 0.0
var hurt := 0.0
var health := 4
var time := 0.0
var camera_x := 0.0
var cabin_camera_lift := 0.0
var bridge_x := 3100.0
var started := false
var water_hit := false
var finished := false
var defeated := false
var victory_time := 0.0
var intro_time := INTRO_SECONDS
var intro_waiting := false
var takeoff_pose := 0.0
var landing_pose := 0.0
var roofs: Array = []
var floors: Array = []
var anchors: Array = []
var crossing_rings: Dictionary = {}
var night_split := PackedFloat32Array([0.0, 0.0])
var platforms: Array = []
var pickups: Array[Dictionary] = []
var hazards: Array[Vector2] = []
var shots: Array[Dictionary] = []
var shield := false
var charged := false
var goal_x := 1805.0
var final_roof_y := 237.0
var level := 1
var next_button: Button
var retry_button: Button
var rescue := false
var branch := ""
var practice_swing_done := false
var checkpoint := false
var checkpoint_state: Dictionary = {}
var cleared_levels := 0
var campaign_return: Dictionary = {}
var profile_loaded := false
var profile_busy := false
var profile_saved_levels := 0
var enemies: Array[Dictionary] = []
var particles: Array[Dictionary] = []
var touches := {}
var drop_requested := false
var ignore_roof := 0.0
var jump_requested := false
var strike_requested := false
var tether_requested := false
var tether_buffer := 0.0
var jump_tether := 0.0
var jump_heading := 0.0
var tether_landing := 0.0
var message := ""
var message_left := 0.0
var pickup_feedback := ""
var pickup_feedback_left := 0.0

var route_checkpoint := Vector2(1225, 258)
var relay := Vector2.ZERO
var powered := false
var junction := Vector2.ZERO
var collapse_times: Dictionary = {}
var collapsed_until := 0.0
var collapse_warned: Dictionary = {}

var sound_players: Dictionary = {}
@onready var session := get_node("/root/OnKlavGame")
var music: AudioStreamPlayer
var train_bed: AudioStreamPlayer
var sound_environment := ""
var blackout_power_time := -INF
var storm_zone := 0
var storm_transition := 0.0
var storm_swing := 0
var storm_reveal := 0.0
var delivery_x := -134.0
var delivered := false
var web_audio: JavaScriptObject
var web_audio_active := false
var score_track := "rail"
var action_styles: Array[StyleBoxFlat] = []
var control_styles: Dictionary = {}

func _ready() -> void:
	resized.connect(_clear_input)
	if OS.has_feature("web"):
		# Reduce rendering work while leaving the route's 60 Hz physics intact.
		Engine.max_fps = 30
	for pressed in [false, true]:
		for attack in [false, true]:
			var style := StyleBoxFlat.new()
			style.set_corner_radius_all(16)
			style.corner_detail = 10
			style.anti_aliasing = true
			style.set_border_width_all(2)
			style.bg_color = Color("#3a6270ed") if pressed else Color("#102634f0")
			style.border_color = (GOLD if attack else TEAL) if pressed else Color("#accbd2")
			action_styles.append(style)
	for action in ["drop", "jump", "strike"]:
		var tint: Color = {"drop": GOLD, "jump": Color("#72dd91"), "strike": Color("#ff845f")}[action]
		var normal := action_styles[0].duplicate() as StyleBoxFlat
		normal.bg_color = tint.darkened(0.72)
		normal.border_color = tint
		var pressed := normal.duplicate() as StyleBoxFlat
		pressed.bg_color = tint.darkened(0.47)
		control_styles[action] = [normal, pressed]
	if OS.has_feature("web"):
		web_audio = JavaScriptBridge.get_interface("lastLightAudio")
	else:
		for cue in ["jump", "tether", "strike", "hit", "block", "impact", "switch", "warn", "pickup", "win", "gust", "groan", "sweep", "charge", "furnace"]:
			var voice := AudioStreamPlayer.new()
			voice.stream = load("res://audio/" + cue + ".wav")
			voice.volume_db = -4
			voice.max_polyphony = 1
			add_child(voice)
			sound_players[cue] = voice
		music = _loop_player()
		train_bed = AudioStreamPlayer.new()
		train_bed.stream = load("res://audio/train-bed.wav")
		train_bed.bus = "Effects"
		train_bed.volume_db = -25.0
		add_child(train_bed)
	_build_stage()
	session.attach_session(self, _snapshot, _restore, _start_campaign,
		"Carry the lantern through ten levels and place it at the final beacon. Rotate sideways to play. Use the arrows to move, JUMP to cross gaps or catch a nearby tether ring, DROP to reach the lower deck, and STRIKE to fight or operate a marked switch. At the junction, JUMP selects the Depot and STRIKE selects the Floodline.\n\nKeyboard: A/D, Space to jump, S to drop, J to strike, K to tether.",
		"user://last_light.json", {}, {"format_version": SAVE_VERSION, "is_resumable": _can_resume})
	for voice in sound_players.values():
		voice.bus = "Effects"
	if is_instance_valid(music):
		music.bus = "Music"
	session.suspended.connect(func():
		_clear_input()
		_sync_web_audio(false)
		if is_instance_valid(train_bed):
			train_bed.stream_paused = true
	)
	session.chrome.preferences_changed.connect(func():
		_sync_web_audio(web_audio_active, true)
		queue_redraw())
	session.chrome.replay_menu = _show_level_select
	session.quit_checkpoint = _campaign_quit_checkpoint
	if DisplayServer.get_name() != "headless":
		session.resumed.connect(_save_level_profile)
		_load_level_profile.call_deferred()
	next_button = Button.new()
	next_button.add_theme_stylebox_override("normal", action_styles[1])
	next_button.add_theme_stylebox_override("hover", action_styles[3])
	next_button.add_theme_stylebox_override("pressed", action_styles[3])
	next_button.pressed.connect(_advance)
	add_child(next_button)
	retry_button = Button.new()
	retry_button.text = "Retry Checkpoint"
	retry_button.add_theme_stylebox_override("normal", action_styles[0])
	retry_button.add_theme_stylebox_override("hover", action_styles[2])
	retry_button.add_theme_stylebox_override("pressed", action_styles[2])
	retry_button.pressed.connect(_retry_checkpoint)
	add_child(retry_button)
	resized.connect(_layout_next_button)
	_layout_next_button()

func _loop_player() -> AudioStreamPlayer:
	var voice := AudioStreamPlayer.new()
	voice.stream = preload("res://audio/rail.wav")
	voice.volume_db = -4.0
	add_child(voice)
	return voice

func _sync_web_audio(active: bool, force := false) -> void:
	if web_audio == null or (not force and web_audio_active == active):
		return
	web_audio_active = active
	var chrome = session.chrome
	web_audio.configure(active, chrome.music, 0.0 if chrome.muted else chrome.volume, score_track)

func _update_score() -> void:
	var track := "rail"
	if level == LAST_LEVEL:
		track = "warden"
	elif level == 7 and not powered:
		track = "blackout"
	elif level == 6 and storm_zone == 1:
		track = "storm"
	if track == score_track:
		return
	score_track = track
	if web_audio != null:
		_sync_web_audio(web_audio_active, true)
	elif is_instance_valid(music):
		var playing := music.playing
		var paused := music.stream_paused
		var offset := music.get_playback_position()
		music.stream = load("res://audio/" + track + ".wav")
		if playing:
			music.play(offset)
			music.stream_paused = paused

func _play(cue: String) -> void:
	if web_audio != null:
		web_audio.play(cue)
	elif DisplayServer.get_name() != "headless" and sound_players.has(cue):
		sound_players[cue].play()

func _start_campaign() -> void:
	_restart()
	intro_time = 0.0
	intro_waiting = true

func _intro_active() -> bool:
	return intro_waiting

func _play_intro() -> void:
	intro_waiting = false
	_clear_input()
	session.checkpoint()
	queue_redraw()

func _intro_play_bounds(width: float) -> Rect2:
	return Rect2(width * 0.5 - 101, 472, 202, 55)

func _restart(route_level: int = 1) -> void:
	intro_time = INTRO_SECONDS
	intro_waiting = false
	level = route_level
	touches.clear()
	drop_requested = false
	jump_requested = false
	strike_requested = false
	tether_requested = false
	tether_buffer = 0.0
	jump_tether = 0.0
	tether_landing = 0.0
	ignore_roof = 0.0
	coyote = 0.0
	jump_buffer = 0.0
	grounded = false
	face = 1.0
	water_hit = false
	bridge_x = 3100.0
	pos = Vector2(135, 365)
	velocity = Vector2.ZERO
	health = 4
	time = 0.0
	camera_x = 0.0
	cabin_camera_lift = 0.0
	tether = -1
	hurt = 0.0
	strike = 0.0
	branch = ""
	rescue = false
	checkpoint = false
	practice_swing_done = false
	checkpoint_state.clear()
	campaign_return.clear()
	finished = false
	started = true
	particles.clear()
	pickup_feedback_left = 0.0
	defeated = false
	victory_time = 0.0
	takeoff_pose = 0.0
	landing_pose = 0.0
	shield = false
	charged = false
	_build_stage()
	storm_zone = 0
	storm_transition = 0.0
	storm_swing = 0
	storm_reveal = 0.0
	delivery_x = -134.0
	delivered = false
	_update_score()
	_notice(FEATURE_HINTS.get(level, LEVEL_NAMES[level - 1]), 5.0)
	_layout_next_button()

func _advance() -> void:
	if (not finished and not defeated) or not session.is_landscape():
		return
	if not campaign_return.is_empty() and finished and not _show_delivery():
		_return_from_replay()
		return
	if defeated:
		var restart_stage := level
		var return_state := campaign_return.duplicate(true)
		_restart(restart_stage)
		campaign_return = return_state
		session.checkpoint()
		return
	if level == LAST_LEVEL:
		if not _show_delivery():
			session.replay()
		return
	_restart(level + 1)
	session.checkpoint()

func _can_retry_checkpoint() -> bool:
	return defeated and checkpoint and level in [9, 10]

func _retry_checkpoint() -> void:
	if not _can_retry_checkpoint() or not session.is_landscape():
		return
	var state := _snapshot()
	state.defeated = false
	if _restore(state):
		health = 4
		if not checkpoint_state.is_empty():
			checkpoint_state.health = health
		_notice("CHECKPOINT  /  Back on track.", 2.4)
		session.checkpoint()

func _layout_next_button() -> void:
	queue_redraw()
	if not is_instance_valid(next_button):
		return
	next_button.visible = (finished or defeated) and session.is_landscape() and not _show_delivery()
	retry_button.visible = next_button.visible and _can_retry_checkpoint()
	if next_button.visible:
		var area := _play_area()
		var layout := _layout_in(area)
		next_button.text = "Return to Campaign" if finished and not campaign_return.is_empty() else (
			"Play Again" if defeated or level == LAST_LEVEL else "Next Level")
		next_button.add_theme_font_size_override("font_size", maxi(15, roundi(25 * layout.x.x)))
		next_button.position = layout * Vector2(_view_width(area) * 0.5 - 130, 264)
		next_button.size = Vector2(260, 60) * layout.x.x
		if retry_button.visible:
			var center := _view_width(area) * 0.5
			next_button.position = layout * Vector2(center + 9, 264)
			next_button.size = Vector2(205, 60) * layout.x.x
			retry_button.position = layout * Vector2(center - 226, 264)
			retry_button.size = Vector2(226, 60) * layout.x.x
			retry_button.add_theme_font_size_override("font_size", maxi(15, roundi(22 * layout.x.x)))

func _build_stage() -> void:
	hazards.clear()
	pickups.clear()
	shots.clear()
	collapse_times.clear()
	collapse_warned.clear()
	collapsed_until = 0.0
	powered = false
	blackout_power_time = -INF
	roofs.clear()
	floors.clear()
	anchors.clear()
	crossing_rings.clear()
	night_split.fill(0.0)
	enemies.clear()
	var x := 60.0
	var cars: Array = ROUTES[level]
	if level == 8 and branch == "Floodline":
		cars = cars.slice(0, 4) + FLOODLINE_TAIL
	var approach: Array = [280, 267, 67] if level == 8 and branch == "Floodline" else APPROACH_CARS[level]
	cars = cars.slice(0, -1) + [approach, cars.back()]
	for index in cars.size():
		var car: Array = cars[index]
		var gap: float = car[2]
		x += gap
		if index > 0:
			var previous: Rect2 = roofs.back()
			var climb := index == 1 and level in [1, 3, 7]
			var wide := gap > 100.0
			if climb or wide:
				var anchor_x := lerpf(previous.end.x, x, 0.74 if wide else 0.5)
				if wide:
					crossing_rings[anchors.size()] = true
				var lift := 55.0 if level == 5 and index - 1 in FREIGHT_STACK_CARS else 0.0
				anchors.append(Vector2(anchor_x, minf(car[1], previous.position.y) - 66.0 - lift))
		roofs.append(Rect2(x, car[1], car[0], 12))
		floors.append(Rect2(x, 365, car[0], 16))
		x += float(car[0])
	var last: Rect2 = roofs.back()
	final_roof_y = last.position.y
	goal_x = last.end.x - 45.0
	platforms = roofs + floors
	var midpoint: Rect2 = roofs[3]
	route_checkpoint = Vector2(midpoint.position.x + 82, 365)
	match level:
		1:
			bridge_x = last.position.x + 170
			pickups.append({"pos": Vector2(midpoint.position.x + 65, 365), "kind": 0, "taken": false})
			pickups.append({"pos": Vector2(midpoint.position.x + 170, midpoint.position.y), "kind": 1, "taken": false})
			return
		2:
			var sleeper: Rect2 = roofs[3]
			pickups.append({"pos": Vector2(sleeper.get_center().x, 365), "kind": 0, "taken": false})
			for split in NIGHT_SPLIT_CARS.size():
				var vent_car: Rect2 = roofs[NIGHT_SPLIT_CARS[split]]
				hazards.append(vent_car.position + Vector2([130.0, 88.0][split], 0))
			var tender: Rect2 = roofs[7]
			hazards.append(Vector2(tender.get_center().x, tender.position.y))
			_feature_guard(4, 3, 1)
			_feature_guard(6, 3, 1)
			_feature_guard(7, 0, 1, true)
		3:
			for index in [1, 5, 7]:
				_feature_guard(index, 5, 1)
			relay = Vector2(midpoint.get_center().x, 365)
			for offset in [-75.0, 75.0]:
				var x_light: float = relay.x + offset
				enemies.append({"x": x_light, "home": x_light, "y": midpoint.position.y, "hp": 1,
					"wind": 0.0, "cool": 0.0, "kind": 5, "signal_light": true})
			_feature_guard(4, 0, 1, true)
			_feature_guard(6, 2, 1)
			_feature_guard(7, 1, 1, true)
		4:
			for index in [1, 2, SWITCHGEAR_CAR, 6, 7]:
				var car: Rect2 = roofs[index]
				if index == SWITCHGEAR_CAR:
					for offset in [65.0, 153.0, 241.0]:
						hazards.append(car.position + Vector2(offset, 0))
				else:
					hazards.append(Vector2(car.get_center().x, car.position.y))
			relay = Vector2(last.position.x + 145, 365)
			_feature_guard(6, 0, 1)
		5:
			for index in FREIGHT_STACK_CARS:
				var car: Rect2 = roofs[index]
				platforms.append_array(_freight_steps(car))
			_feature_guard(5, 0, 1)
			_feature_guard(7, 3, 1)
		6:
			_feature_guard(3, 0, 1)
			_feature_guard(7, 1, 1, true)
		7:
			relay = Vector2(midpoint.get_center().x, 365)
			route_checkpoint = Vector2(relay.x + 50, 365)
			_feature_guard(5, 1, 1)
			_feature_guard(6, 2, 1)
			_feature_guard(7, 0, 1, true)
		8:
			junction = Vector2(midpoint.get_center().x, midpoint.position.y)
			route_checkpoint = junction
			if branch == "Depot":
				relay = Vector2(last.position.x + 125, 365)
				_feature_guard(DEPOT_CARRIAGE, 6, 2, true)
				_feature_guard(DEPOT_CARRIAGE, 5, 1)
				_feature_guard(7, 1, 1, true)
				var armored: Rect2 = roofs[DEPOT_CARRIAGE]
				pickups.append({"pos": Vector2(armored.position.x + 75.0, DEPOT_FLOOR), "kind": 1, "taken": false})
			elif branch == "Floodline":
				_feature_guard(5, 2, 1)
				_feature_guard(7, 5, 1)
		9:
			route_checkpoint = Vector2(midpoint.position.x + 82, midpoint.position.y)
			var brake_van: Rect2 = roofs[7]
			pickups.append({"pos": Vector2(brake_van.get_center().x, brake_van.position.y), "kind": 1, "taken": false})
		10:
			_feature_guard(4, 1, 1)
			_feature_guard(7, 2, 1)
			route_checkpoint = Vector2(last.position.x + 90, last.position.y)
			var boss_x := last.position.x + 415.0
			enemies.append({"x": boss_x, "home": boss_x, "y": final_roof_y, "hp": 12,
				"wind": 0.0, "cool": 0.0, "kind": 4, "phase": "approach", "cycle": 0, "direction": -1.0, "retreated": false})
			anchors.append(Vector2(last.position.x + 270, last.position.y - 107))
			anchors.append(Vector2(last.position.x + 505, last.position.y - 110))
			pickups.append({"pos": Vector2(last.position.x + 75, 365), "kind": 0, "taken": false})
	for index in ([4, 6] if level == 2 else [2, 4, 6]):
		var car: Rect2 = roofs[index]
		var deck := car.position.y if level == 5 else 365.0
		pickups.append({"pos": Vector2(car.position.x + 105, deck), "kind": index / 2 - 1, "taken": false})
	if level == 5:
		for index in FREIGHT_STACK_CARS:
			var top := _freight_steps(roofs[index])[1]
			pickups.append({"pos": top.get_center() - Vector2(0, 5), "kind": 1 if index == 2 else 2, "taken": false})

func _feature_guard(car_index: int, kind: int, hp: int, lower := false) -> void:
	var car: Rect2 = roofs[car_index]
	var x := car.end.x - 72.0
	enemies.append({"x": x, "home": x, "y": 365.0 if lower else car.position.y, "hp": hp,
		"wind": 0.0, "cool": 0.0, "kind": kind, "facing": -1.0})

func _boss_alive() -> bool:
	return level == LAST_LEVEL and int(enemies.back()["hp"]) > 0

func _has_depot_carriage() -> bool:
	return level == 8 and branch == "Depot"

func _inside_depot_carriage() -> bool:
	if not _has_depot_carriage():
		return false
	var car: Rect2 = roofs[DEPOT_CARRIAGE]
	return pos.x >= car.position.x and pos.x <= car.end.x and pos.y >= car.position.y + 30.0

func _depot_ladder_near() -> bool:
	return _inside_depot_carriage() and absf(pos.x - (roofs[DEPOT_CARRIAGE].end.x - 35.0)) <= 24.0

func _blackout_ladder_near() -> bool:
	return level == 7 and powered and pos.y >= 363.0 and absf(pos.x - (roofs[-2].end.x - 48.0)) <= 25.0

func _blackout_shutter_x() -> float:
	return relay.x + 108.0

func _stop_at_blackout_shutter(before: Vector2) -> void:
	var bottom := lerpf(321.0, 151.0, _blackout_light(_blackout_shutter_x()))
	if pos.y >= bottom or bottom <= 151.0:
		return
	var gate := _blackout_shutter_x()
	var edge := gate - 14.0 if before.x <= gate else gate + 14.0
	if (before.x <= gate and pos.x >= edge) or (before.x > gate and pos.x <= edge):
		pos.x = edge
		velocity.x = 0.0

func _blackout_light(at_x: float) -> float:
	if not powered:
		return 0.0
	var delay := absf(at_x - relay.x) / 1250.0
	return smoothstep(0.0, 0.26, time - blackout_power_time - delay)

func _switchgear_ladder_near() -> bool:
	return level == 4 and pos.y >= 363.0 and absf(pos.x - (roofs[SWITCHGEAR_CAR].end.x - 30.0)) <= 24.0

func _signal_ladder_near() -> bool:
	return level == 3 and pos.y >= 363.0 and absf(pos.x - (roofs[SENTRY_CAR].end.x - 37.0)) <= 25.0

func _warden_ladders() -> PackedFloat32Array:
	if level != LAST_LEVEL or enemies.back().hp > 8:
		return PackedFloat32Array()
	var home: float = enemies.back().home
	return PackedFloat32Array([home - 215.0, home + 146.0])

func _warden_ladder_near() -> bool:
	if pos.y < 363.0:
		return false
	for x in _warden_ladders():
		if absf(pos.x - x) <= 26.0:
			return true
	return false

func _depot_cover() -> bool:
	return _inside_depot_carriage() and absf(pos.x - (roofs[DEPOT_CARRIAGE].position.x + 75.0)) <= 29.0

func _goal_deck() -> float:
	return final_roof_y if level in [2, 5] or branch == "Floodline" or level == LAST_LEVEL else 365.0

func _can_resume(state: Dictionary) -> bool:
	var saved_level := _saved_level(state)
	return started and saved_level > 0 and (state.has("campaign_return") or not state.get("finished", false)
		or saved_level < LAST_LEVEL or state.get("delivery_pending", false))

func _campaign_quit_checkpoint() -> Dictionary:
	return campaign_return.duplicate(true)

func _load_level_profile() -> void:
	if profile_loaded or profile_busy:
		return
	profile_busy = true
	var profile: Dictionary = await session.load_data("last_light_levels")
	profile_busy = false
	if not profile.get("ok", false):
		return
	if profile.get("exists", false):
		var state: Variant = profile.get("state")
		if not state is Dictionary:
			return
		var saved := _saved_integer(state.get("cleared"), 0, LAST_LEVEL, -1)
		if saved < 0:
			return
		profile_saved_levels = saved
		cleared_levels = maxi(cleared_levels, saved)
	profile_loaded = true
	_save_level_profile()

func _save_level_profile() -> void:
	if profile_busy:
		return
	if not profile_loaded:
		if DisplayServer.get_name() != "headless":
			_load_level_profile()
		return
	if cleared_levels <= profile_saved_levels:
		return
	profile_busy = true
	var requested := cleared_levels
	var saved: bool = await session.save_data("last_light_levels", {"cleared": requested})
	profile_busy = false
	if saved:
		profile_saved_levels = requested
		if cleared_levels > requested:
			_save_level_profile.call_deferred()
	else:
		_notice("Level unlocks will save with your campaign.")

func _show_level_select() -> void:
	session.pause_game()
	session.chrome.show_pause_notice(false)
	var actions: Array = []
	for route_level in range(1, cleared_levels + 1):
		actions.append(["%02d  %s" % [route_level, LEVEL_NAMES[route_level - 1]], _start_replay.bind(route_level)])
	if not campaign_return.is_empty():
		actions.append(["Return to Campaign", _return_from_replay])
	actions.append(["Back to Game", session.resume_game])
	session.sheet("Level Select", "Clear a level to unlock it for replay. Your campaign checkpoint is kept.", actions)

func _start_replay(route_level: int) -> void:
	if route_level < 1 or route_level > cleared_levels:
		return
	var return_state := campaign_return.duplicate(true) if not campaign_return.is_empty() else _snapshot()
	_restart(route_level)
	campaign_return = return_state
	session.checkpoint()
	session.resume_game()

func _return_from_replay() -> void:
	if campaign_return.is_empty():
		return
	var state := campaign_return.duplicate(true)
	if _restore(state):
		session.checkpoint()
		session.resume_game()

func _capture_checkpoint() -> void:
	checkpoint = true
	var collected: Array[int] = []
	for index in pickups.size():
		if pickups[index].taken:
			collected.append(index)
	checkpoint_state = {"health": health, "shield": shield, "charged": charged, "taken": collected}

func _apply_checkpoint() -> void:
	if checkpoint_state.is_empty():
		return
	health = _saved_integer(checkpoint_state.get("health"), 1, 4, 4)
	shield = checkpoint_state.get("shield", false) == true
	charged = checkpoint_state.get("charged", false) == true
	var collected: Variant = checkpoint_state.get("taken", [])
	if collected is Array:
		for index in collected:
			var pickup_index := _saved_integer(index, 0, pickups.size() - 1, -1)
			if pickup_index >= 0:
				pickups[pickup_index].taken = true

func _snapshot() -> Dictionary:
	var state := {"version": SAVE_VERSION, "signal": checkpoint, "branch": branch, "rescue": rescue,
		"level": level, "finished": finished, "defeated": defeated, "powered": powered,
		"delivery_pending": _show_delivery(), "cleared": cleared_levels,
		"checkpoint_state": checkpoint_state.duplicate(true)}
	if _intro_active():
		state["intro_pending"] = true
	if finished and level == LAST_LEVEL and delivered:
		state["lantern_placed"] = true
	if not campaign_return.is_empty():
		state["campaign_return"] = campaign_return.duplicate(true)
	return state

static func _saved_integer(value: Variant, low: int, high: int, fallback: int) -> int:
	if (value is int or value is float) and value >= low and value <= high and value == int(value):
		return int(value)
	return fallback

static func _saved_level(state: Dictionary) -> int:
	if state.get("version") != SAVE_VERSION or state.get("branch", "") not in ["", "Depot", "Floodline"]:
		return 0
	return _saved_integer(state.get("level", 1), 1, LAST_LEVEL, 0)

func _restore(state: Dictionary) -> bool:
	var saved_level := _saved_level(state)
	if saved_level == 0:
		return false
	var route: String = state.get("branch", "")
	_restart(saved_level)
	if saved_level == 1 and state.get("intro_pending", false) == true:
		intro_waiting = true
		intro_time = 0.0
	var saved_return: Variant = state.get("campaign_return")
	if saved_return is Dictionary and _saved_level(saved_return) > 0 and not saved_return.has("campaign_return"):
		campaign_return = saved_return.duplicate(true)
	var inferred := saved_level - 1 + int(state.get("finished", false) == true)
	cleared_levels = maxi(cleared_levels, _saved_integer(state.get("cleared"), 0, LAST_LEVEL, inferred))
	var saved_finished: bool = state.get("finished", false) == true
	checkpoint = state.get("signal", false) == true or (level == 7 and state.get("powered", false) == true)
	# Older builds could finish an airborne exit before capturing the checkpoint.
	if saved_finished and level != 8:
		checkpoint = true
	rescue = state.get("rescue", false) == true
	if level == 8 and checkpoint:
		branch = route
		_build_stage()
	if checkpoint:
		var saved_checkpoint: Variant = state.get("checkpoint_state")
		if saved_checkpoint is Dictionary:
			checkpoint_state = saved_checkpoint.duplicate(true)
			_apply_checkpoint()
		pos = route_checkpoint
		if level == 9:
			collapsed_until = roofs[2].end.x
		camera_x = pos.x - 385.0
	powered = checkpoint and (level == 7
		or (level == 3 and state.get("powered", false) == true)
		or ((level == 4 or (level == 8 and branch == "Depot"))
			and (state.get("powered", false) == true or state.get("finished", false) == true)))
	defeated = state.get("defeated", false) == true
	if defeated:
		health = 0
	finished = not defeated and checkpoint and (level != 8 or not branch.is_empty()) and saved_finished
	if finished:
		if level == LAST_LEVEL:
			enemies.back().hp = 0
			if state.get("delivery_pending", false) != true:
				delivered = true
				victory_time = DELIVERY_SECONDS
			elif state.get("lantern_placed", false) == true:
				delivered = true
				delivery_x = DELIVERY_PEDESTAL - 24.0
		pos = Vector2(goal_x, _goal_deck())
		camera_x = goal_x - 805.0
	if level == 2 and checkpoint:
		_tick_night_split(0.84)
	if level == 6:
		storm_zone = _storm_section()
	_update_score()
	_layout_next_button()
	return true

func _notice(copy: String, seconds: float = 2.4) -> void:
	message = copy
	message_left = seconds

func _layout() -> Transform2D:
	return _layout_in(_play_area())

func _play_area() -> Rect2:
	if is_instance_valid(session.content):
		return session.content.get_rect()
	return Rect2(Vector2.ZERO, size)

func _layout_in(area: Rect2) -> Transform2D:
	var design := Vector2(1000, 540)
	var factor := maxf(0.001, minf(area.size.x / design.x, area.size.y / design.y))
	var origin := area.position + (area.size - design * factor) * 0.5
	if area.size.x > area.size.y:
		origin.x = area.position.x
	return Transform2D(Vector2(factor, 0), Vector2(0, factor), origin)

func _view_width(area: Rect2) -> float:
	return area.size.x / _layout_in(area).x.x if area.size.x > area.size.y else 1000.0

func _pad(point: Vector2) -> String:
	for button in _buttons():
		var bounds := _button_bounds(button)
		if button[3] != ARROW_SIZE:
			# The horizontal margin stops midway through the gap between actions.
			if bounds.grow_individual(8, 12, 8, 12).has_point(point):
				return button[1]
		elif point.distance_squared_to(button[0]) <= (ARROW_SIZE.x * 0.5) ** 2:
			return button[1]
	return ""

func _button_bounds(button: Array) -> Rect2:
	var extent: Vector2 = button[3]
	return Rect2(button[0] - extent * 0.5, extent)

func _buttons() -> Array:
	if not session.is_landscape():
		return []
	var area := _play_area()
	var transform := _layout_in(area)
	var width := _view_width(area)
	var control_y := (area.end.y - transform.origin.y) / transform.x.x - 60.0
	const EDGE := 74.0
	const SPACING := 112.0
	if _show_delivery():
		if delivered:
			return []
		return [[Vector2(EDGE, control_y), "left", "", ARROW_SIZE], [Vector2(EDGE + SPACING, control_y), "right", "", ARROW_SIZE],
			[Vector2(width - EDGE - 42, control_y), "strike", "PLACE", Vector2(166, 58)]]
	return [[Vector2(EDGE, control_y), "left", "", ARROW_SIZE], [Vector2(EDGE + SPACING, control_y), "right", "", ARROW_SIZE],
		[Vector2(width - EDGE - 2 * SPACING, control_y), "drop", "DROP", ACTION_SIZE],
		[Vector2(width - EDGE - SPACING, control_y), "strike", "STRIKE", ACTION_SIZE],
		[Vector2(width - EDGE, control_y), "jump", "JUMP", ACTION_SIZE]]

func _set_touch(index: int, point: Vector2) -> void:
	var action := _pad(_layout().affine_inverse() * point)
	if action == touches.get(index, ""):
		return
	touches[index] = action
	match action:
		"drop": drop_requested = true
		"jump": jump_requested = true
		"strike": strike_requested = true

func _input(event: InputEvent) -> void:
	if event is InputEventMouse and event.device == InputEvent.DEVICE_ID_EMULATION:
		return
	if not started or session.paused or not session.is_landscape():
		return
	if _intro_active():
		if intro_time < INTRO_SECONDS:
			return
		if event is InputEventKey and event.pressed and not event.echo and event.physical_keycode in [KEY_SPACE, KEY_ENTER]:
			_play_intro()
			get_viewport().set_input_as_handled()
		elif (event is InputEventScreenTouch and event.pressed) or (event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT):
			var point: Vector2 = _layout().affine_inverse() * event.position
			if _intro_play_bounds(_view_width(_play_area())).has_point(point):
				_play_intro()
				get_viewport().set_input_as_handled()
		return
	if event is InputEventScreenTouch:
		if event.pressed and not event.canceled:
			_set_touch(event.index, event.position)
		else:
			touches.erase(event.index)
	elif event is InputEventScreenDrag and touches.has(event.index):
		_set_touch(event.index, event.position)
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed:
			_set_touch(-1, event.position)
		else:
			touches.erase(-1)
	elif event is InputEventMouseMotion and touches.has(-1):
		_set_touch(-1, event.position)
	elif event is InputEventKey and event.pressed and not event.echo:
		match event.physical_keycode:
			KEY_S, KEY_DOWN: drop_requested = true
			KEY_SPACE: jump_requested = true
			KEY_J: strike_requested = true
			KEY_K: tether_requested = true
			KEY_ENTER:
				if finished or defeated: _advance()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT or what == NOTIFICATION_PAUSED:
		_clear_input()

func _clear_input() -> void:
	touches.clear()
	drop_requested = false
	jump_requested = false
	jump_buffer = 0.0
	strike_requested = false
	tether_requested = false
	tether_buffer = 0.0
	jump_tether = 0.0

func _direction() -> float:
	var right := Input.is_physical_key_pressed(KEY_D) or Input.is_physical_key_pressed(KEY_RIGHT) or "right" in touches.values()
	var left := Input.is_physical_key_pressed(KEY_A) or Input.is_physical_key_pressed(KEY_LEFT) or "left" in touches.values()
	return float(int(right) - int(left))

func _anchor_target() -> int:
	var best := -1
	var distance := INF
	var range_limit := TETHER_RANGE + (18.0 if level in [5, 6, 9] else 0.0)
	for i in anchors.size():
		if anchors[i].x <= collapsed_until:
			continue
		var offset: Vector2 = anchors[i] - (pos - Vector2(0, 25))
		var squared := offset.length_squared()
		if offset.y >= 26.0 or squared > range_limit * range_limit:
			continue
		var score := sqrt(squared) + (56.0 if offset.x * face < -12.0 else 0.0)
		if score < distance:
			distance = score
			best = i
	return best

func _jump_anchor(origin: Vector2, heading: float, crossing_only := false) -> int:
	var best := -1
	var distance := INF
	for i in anchors.size():
		if anchors[i].x <= collapsed_until or (crossing_only and not crossing_rings.has(i)):
			continue
		var offset: Vector2 = anchors[i] - (origin - Vector2(0, 25))
		if offset.x * heading < 22.0 or offset.y > -12.0:
			continue
		var limit := CROSSING_TETHER_RANGE if crossing_rings.has(i) else AUTO_TETHER_RANGE
		var squared := offset.length_squared()
		if squared <= limit * limit and squared < distance:
			best = i
			distance = squared
	return best

func _jump_ready_ring() -> int:
	if tether >= 0 or _near_signal() or finished or defeated or not started:
		return -1
	if jump_tether > 0.0 and velocity.y < 0.0:
		return _jump_anchor(pos, jump_heading, jump_tether <= 0.10)
	if not grounded:
		return -1
	var target := _jump_anchor(pos, face)
	if target >= 0:
		return target
	# Preview the latter part of a held directional jump at long crossings.
	return _jump_anchor(pos + Vector2(face * 40.0, -65.0), face, true)

func _near_signal() -> bool:
	return level == 8 and branch.is_empty() and pos.distance_to(junction) < 63.0

func _set_branch(route: String) -> void:
	branch = route
	_build_stage()
	_capture_checkpoint()
	tether = -1
	tether_buffer = 0.0
	_play("switch")
	_notice("DEPOT  /  Drop inside to repair the relay." if branch == "Depot"
		else "FLOODLINE  /  Stay high and reach the crew's beacon.", 4.0)
	session.checkpoint()

func _physics_process(delta: float) -> void:
	# Opening the portrait menu holds the current frame without replacing a
	# manual pause, a recovery dialog, or the running save session.
	var portrait_menu: bool = not session.is_landscape()
	if is_instance_valid(music) and music.stream_paused != portrait_menu:
		music.stream_paused = portrait_menu
	_sync_web_audio(not portrait_menu and started)
	if is_instance_valid(train_bed):
		train_bed.stream_paused = portrait_menu or not started
	if portrait_menu or not started:
		return
	if DisplayServer.get_name() != "headless":
		if is_instance_valid(music) and not music.playing:
			music.play()
		if is_instance_valid(train_bed) and not train_bed.playing:
			train_bed.play()
	_update_environment_audio(delta)
	if _intro_active():
		if intro_time < INTRO_SECONDS:
			intro_time = minf(intro_time + delta, INTRO_SECONDS)
			queue_redraw()
		return
	if defeated or (finished and level != LAST_LEVEL):
		return
	if finished:
		if _show_delivery() and not delivered:
			_step_delivery(delta)
		else:
			var delivering := _show_delivery()
			var previous_time := victory_time
			victory_time += delta
			if delivering and previous_time < 0.75 and victory_time >= 0.75:
				_play("win")
			if delivering and not _show_delivery():
				_layout_next_button()
				session.checkpoint()
				if campaign_return.is_empty():
					_save_level_profile()
		jump_requested = false
		queue_redraw()
		return
	_step(delta, _direction())
	queue_redraw()

func _step_delivery(delta: float) -> void:
	var direction := _direction()
	if direction != 0.0:
		face = direction
	delivery_x = clampf(delivery_x + direction * 162.0 * delta, -174.0, DELIVERY_PEDESTAL - 24.0)
	if strike_requested and delivery_x >= DELIVERY_PEDESTAL - 50.0:
		delivered = true
		face = 1.0
		victory_time = 0.0
		_clear_input()
		_play("switch")
		session.checkpoint()
	strike_requested = false

func _update_environment_audio(delta: float) -> void:
	var environment := "cabin" if pos.y >= 320 else ("storm" if level == 6 and storm_zone == 1 else "roof")
	if _show_delivery() or (level == 6 and storm_zone == 2):
		environment = "station"
	if web_audio != null and environment != sound_environment:
		web_audio.setEnvironment(environment)
	sound_environment = environment
	if is_instance_valid(train_bed):
		var target: float = {"cabin": -25.0, "storm": -19.0, "roof": -21.0, "station": -32.0}[environment]
		train_bed.volume_db = move_toward(train_bed.volume_db, target, delta * 32.0)

func _storm_section() -> int:
	if pos.x < roofs[1].position.x + 38.0:
		return 0
	if pos.x >= roofs.back().position.x + 86.0 or (storm_swing == 3 and pos.x >= roofs.back().position.x):
		return 2
	return 1

func _update_storm_sequence(delta: float) -> void:
	storm_transition = maxf(0.0, storm_transition - delta)
	storm_reveal = maxf(0.0, storm_reveal - delta)
	var edge: float = roofs[-2].end.x
	if storm_swing == 0 and pos.x >= edge - 145.0 and pos.x < roofs[-1].position.x:
		storm_swing = 1
		storm_reveal = 0.62
		_play("furnace")
		_notice("THE LANTERN FOUND A RING  /  Hold right and jump.", 2.4)
	var zone := _storm_section()
	if zone == storm_zone:
		return
	storm_zone = zone
	_update_score()
	if zone == 1:
		storm_transition = 0.48
		_play("gust")
		_notice("OUT ON THE SPAN  /  Watch the wind flags.", 2.6)
	elif zone == 2:
		_notice("LEE OF THE BRIDGE  /  The wind has dropped.", 2.6)

func _tick_night_split(delta: float) -> void:
	for split in NIGHT_SPLIT_CARS.size():
		var index: int = NIGHT_SPLIT_CARS[split]
		var departure: Rect2 = roofs[index - 1]
		var roof: Rect2 = roofs[index]
		var progress := night_split[split]
		if progress >= 1.0 or pos.x < departure.end.x - 155.0:
			continue
		if progress == 0.0 and pos.x < roof.end.x:
			_play("switch")
			_notice("MAIL CAR PULLING AWAY  /  Hold right and jump to the ring." if split == 0
				else "FREIGHT CAR BREAKING AWAY  /  Tether across and watch the steam.", 3.2)
		var next := minf(1.0, progress + delta / 0.84)
		night_split[split] = next
		var shift: float = NIGHT_SPLIT_TRAVEL[split] * (smoothstep(0.0, 1.0, next) - smoothstep(0.0, 1.0, progress))
		if grounded and pos.x >= roof.position.x and pos.x <= roof.end.x and pos.y in [roof.position.y, 365.0]:
			pos.x += shift
		roofs[index].position.x += shift
		floors[index].position.x += shift
		platforms[index] = roofs[index]
		platforms[roofs.size() + index] = floors[index]
		anchors[split].x += shift
		hazards[split].x += shift

func _step(delta: float, direction: float) -> void:
	var wind_before := _wind_phase() if level == 6 else 0.0
	time += delta
	if level == 2:
		_tick_night_split(delta)
	if level == 6:
		_update_storm_sequence(delta)
	takeoff_pose = maxf(0.0, takeoff_pose - delta)
	landing_pose = maxf(0.0, landing_pose - delta)
	var was_grounded := grounded
	if level == 6 and storm_zone == 1 and wind_before < 1.95 and _wind_phase() >= 1.95:
		_play("gust")
	jump_tether = maxf(0.0, jump_tether - delta)
	tether_landing = maxf(0.0, tether_landing - delta)
	strike = maxf(strike - delta, 0.0)
	hurt = maxf(hurt - delta, 0.0)
	message_left = maxf(message_left - delta, 0.0)
	pickup_feedback_left = maxf(pickup_feedback_left - delta, 0.0)
	tether_left -= delta
	ignore_roof = maxf(ignore_roof - delta, 0.0)
	if drop_requested and grounded and pos.y < 340.0:
		ignore_roof = 0.4
		grounded = false
		coyote = 0.0
		velocity.y = 118.0
	drop_requested = false
	coyote = 0.14 if grounded else maxf(0.0, coyote - delta)
	jump_buffer = maxf(0.0, jump_buffer - delta)
	if direction != 0.0:
		face = direction
	if _near_signal() and (jump_requested or strike_requested):
		_set_branch("Depot" if jump_requested else "Floodline")
		jump_requested = false
		strike_requested = false
		tether_requested = false
	if jump_requested:
		jump_buffer = 0.16
	if jump_buffer > 0.0 and coyote > 0.0:
		velocity.y = -520.0 if _warden_ladder_near() else (-495.0 if _depot_ladder_near() or _signal_ladder_near() or _switchgear_ladder_near() or _blackout_ladder_near() else -425.0)
		takeoff_pose = 0.14
		landing_pose = 0.0
		coyote = 0.0
		grounded = false
		jump_buffer = 0.0
		jump_heading = direction
		jump_tether = 0.38 if direction != 0.0 else 0.0
		_play("jump")
	tether_buffer = 0.16 if tether_requested and tether < 0 else maxf(0.0, tether_buffer - delta)
	if tether < 0:
		if tether_buffer > 0.0:
			tether = _anchor_target()
		elif jump_tether > 0.0 and direction == jump_heading and velocity.y < 0.0 and not _near_signal():
			tether = _jump_anchor(pos, jump_heading, jump_tether <= 0.10)
		if tether >= 0:
			if level == 6 and storm_swing == 1 and tether == anchors.size() - 1:
				storm_swing = 2
				storm_reveal = 0.34
				_burst(anchors[tether], GOLD, 12)
			tether_buffer = 0.0
			jump_tether = 0.0
			tether_left = 0.49
			_play("tether")
	if strike_requested and strike <= 0.0:
		strike = 0.26
		_play("strike")
		_attack()
	jump_requested = false
	tether_requested = false
	strike_requested = false
	velocity.x = move_toward(velocity.x, direction * 237.0, delta * (2300.0 if grounded else 1050.0))
	velocity.y = minf(velocity.y + 1050.0 * delta, 730.0)
	if tether >= 0:
		var pull: Vector2 = anchors[tether] - Vector2(0, -3) - pos
		if tether_left <= 0.0 or pull.length() < 27.0:
			tether = -1
			tether_landing = 0.44
			velocity = Vector2(face * 243.0, -93.0)
		else:
			velocity = pull.normalized() * 510.0
	var before := pos
	pos += velocity * delta
	if level == 7:
		_stop_at_blackout_shutter(before)
	if level == 6:
		pos.x -= delta * _storm_wind_speed()
	pos.x = clampf(pos.x, 67.0, goal_x + 8.0)
	grounded = false
	var edge_grace := 8.0 if tether_landing > 0.0 else 0.0
	for platform in floors if ignore_roof > 0.0 else platforms:
		if platform.end.x <= collapsed_until:
			continue
		if velocity.y >= 0.0 and before.y <= platform.position.y and pos.y >= platform.position.y and pos.x >= platform.position.x - edge_grace and pos.x <= platform.end.x + edge_grace:
			pos.x = clampf(pos.x, platform.position.x, platform.end.x)
			pos.y = platform.position.y
			tether_landing = 0.0
			if not was_grounded and velocity.y > 150.0:
				landing_pose = 0.16
				_burst(pos, Color("#b5b6ad"), 5)
			velocity.y = 0.0
			grounded = true
	if level == 6 and storm_swing == 2 and grounded and pos.x >= roofs[-1].position.x:
		storm_swing = 3
		_update_storm_sequence(0.0)
		_play("switch")
	if level == 1 and not practice_swing_done and grounded and pos.x >= roofs[1].position.x:
		practice_swing_done = true
		if before.y < roofs[1].position.y and pos.y == roofs[1].position.y:
			_notice("ROOF REACHED  /  Look for rings over the wider gaps.", 2.5)
	if pos.y > 440.0:
		_reset_to_landing()
		return
	if level == 1:
		if absf(pos.x - bridge_x) < 47.0 and pos.y < 294.0:
			_damage(-1.0)
		elif before.x < bridge_x + 47.0 and pos.x >= bridge_x + 47.0 and pos.y >= 340.0:
			_play("switch")
			_notice("BRIDGE CLEAR  /  Follow the lower deck to the exit signal.", 2.8)
	elif level == 9:
		_tick_collapse()
	_tick_enemies(delta)
	_tick_course(delta)
	if defeated:
		return
	if branch == "Floodline" and pos.x >= _floodline_start() and pos.y >= _floodline_surface():
		if not water_hit and hurt <= 0.0:
			water_hit = true
			_damage(-1.0)
			_notice("SURGE ON THE LOWER DECK  /  Get to the roof.")
	else:
		water_hit = false
	if not checkpoint and level != 8 and pos.x >= route_checkpoint.x and grounded and (level != 7 or powered):
		_capture_checkpoint()
		session.checkpoint()
	if branch == "Floodline" and not rescue and pos.x > goal_x - 62.0 and absf(pos.y - final_roof_y + 12.0) <= 20.0:
		rescue = true
		_play("switch")
		_burst(pos - Vector2(0, 42), GOLD, 24)
		_notice("Crew rescued. Reach the end of the roof.")
	var near_exit := absf(pos.x - goal_x) <= EXIT_REACH
	var exit_deck := _goal_deck()
	var touches_exit := near_exit and pos.y >= exit_deck - 52.0 and pos.y <= exit_deck + 10.0
	if not finished and not defeated and near_exit and (grounded or touches_exit):
		if _needs_relay() and not powered:
			_notice("Strike the breaker on the lower deck." if level == 4 else "Strike the power relay on the lower deck.")
		elif level == 8 and branch.is_empty():
			_notice("Choose a line at the rooftop signal.")
		elif _boss_alive():
			_notice("Defeat the Iron Warden on the roof.")
		elif not touches_exit:
			_notice("Reach the signal on the roof." if exit_deck < 300.0 else "Drop to the lower deck to reach the signal.")
		elif branch != "Floodline" or rescue:
			if not checkpoint:
				_capture_checkpoint()
			finished = true
			if campaign_return.is_empty() and level > cleared_levels:
				cleared_levels = level
				if level != LAST_LEVEL:
					_save_level_profile()
			_clear_input()
			_layout_next_button()
			session.checkpoint()
			_play("switch")
	cabin_camera_lift = move_toward(cabin_camera_lift, 18.0 if _inside_depot_carriage() else 0.0, delta * 85.0)
	camera_x = lerpf(camera_x, clampf(pos.x - 385.0, 0.0, goal_x - 805.0), minf(delta * 5.0, 1.0))
	for i in range(particles.size() - 1, -1, -1):
		var p := particles[i]
		p.life -= delta
		p.pos += p.vel * delta
		p.vel.y += 180.0 * delta
		if p.life <= 0.0:
			particles.remove_at(i)

func _reset_to_landing() -> void:
	if level == 1 and not practice_swing_done and pos.x <= roofs[1].end.x:
		var coach: Rect2 = roofs[0]
		_land_after_fall(Vector2(coach.end.x - 62.0, coach.position.y))
		_notice("TRY THE PRACTICE SWING AGAIN  /  Hold right and jump.", 2.8)
		return
	if level == 2 and not checkpoint and pos.x >= roofs[1].end.x - 15.0 and pos.x < roofs[2].position.x:
		var departure: Rect2 = roofs[1]
		_land_after_fall(Vector2(departure.end.x - 44.0, departure.position.y))
		_notice("TRY THE MAIL CAR AGAIN  /  Hold right and jump.", 2.8)
		return
	health -= 1
	if health <= 0:
		_lose()
		return
	_land_after_fall(route_checkpoint if checkpoint else Vector2(135, 365))
	if level == 6:
		storm_swing = 0
		storm_reveal = 0.0
	if level == 9:
		collapse_times.clear()
		collapse_warned.clear()
		collapsed_until = roofs[2].end.x if checkpoint else 0.0
	hurt = 1.2
	_play("hit")

func _land_after_fall(landing: Vector2) -> void:
	_clear_input()
	pos = landing
	camera_x = clampf(pos.x - 385.0, 0.0, goal_x - 805.0)
	velocity = Vector2.ZERO
	tether = -1
	tether_left = 0.0
	tether_landing = 0.0
	ignore_roof = 0.0
	coyote = 0.0
	takeoff_pose = 0.0
	landing_pose = 0.0
	grounded = true

func _storm_windbreak(car: Rect2) -> Rect2:
	return Rect2(car.end.x - 138.0, car.position.y - 42.0, 110.0, 44.0)

func _in_storm_windbreak() -> bool:
	for index in STORM_WINDBREAK_CARS:
		if _storm_windbreak(roofs[index]).has_point(pos):
			return true
	return false

func _storm_wind_speed() -> float:
	if storm_zone != 1 or pos.y >= 320.0 or tether >= 0 or _in_storm_windbreak():
		return 0.0
	var phase := _wind_phase()
	return 147.0 * smoothstep(2.65, 2.95, phase) * (1.0 - smoothstep(4.10, 4.50, phase))

func _floodline_start() -> float:
	return goal_x - 290.0

func _floodline_phase() -> float:
	return fposmod(time, 5.0)

func _floodline_surface() -> float:
	var phase := _floodline_phase()
	var rise := smoothstep(1.7, 2.35, phase)
	var ebb := smoothstep(3.35, 4.2, phase)
	return lerpf(382.0, 319.0, rise * (1.0 - ebb))

func _wind_phase() -> float:
	return fposmod(time, 4.5)

func _collapse_duration(index: int) -> float:
	return 1.65 if index == roofs.size() - 2 else 2.15

func _tick_collapse() -> void:
	for index in roofs.size() - 1:
		var car: Rect2 = roofs[index]
		if car.end.x <= collapsed_until:
			continue
		if not collapse_times.has(index):
			if pos.x < car.position.x + 52.0:
				continue
			collapse_times[index] = time
		var age: float = time - collapse_times[index]
		var duration := _collapse_duration(index)
		if age >= duration - 1.0 and not collapse_warned.has(index):
			collapse_warned[index] = true
			if pos.x < car.end.x + 100:
				_play("groan")
				_notice("BRAKE VAN FAILING  /  Jump toward the last ring." if index == roofs.size() - 2
					else "CAR BREAKING AWAY  /  Keep moving.", 1.2)
		if age >= duration:
			collapsed_until = car.end.x
			if tether >= 0 and anchors[tether].x <= collapsed_until:
				tether = -1

func _needs_relay() -> bool:
	return level in [4, 7] or (level == 8 and branch == "Depot")

func _attack() -> void:
	var origin := pos - Vector2(0, 29)
	_burst(origin + Vector2(face * 33, 0), GOLD, 9)
	if _needs_relay() and not powered and pos.distance_to(relay) < 86.0:
		powered = true
		_play("switch")
		_update_score()
		_notice({4: "BREAKER OFF  /  Exit is clear.", 7: "POWER RESTORED  /  Tunnel lights online.",
			8: "RELAY REPAIRED  /  Depot exit open."}[level], 3.0)
		if level == 7:
			blackout_power_time = time
			_capture_checkpoint()
		if checkpoint:
			session.checkpoint()
		_burst(relay - Vector2(0, 36), TEAL, 18)
	if level == 3 and not powered and pos.distance_to(relay) < 73.0:
		powered = true
		for enemy in enemies:
			if enemy.get("signal_light", false):
				enemy.wind = 0.0
		for i in range(shots.size() - 1, -1, -1):
			if shots[i].get("signal_light", false):
				shots.remove_at(i)
		_capture_checkpoint()
		session.checkpoint()
		_play("switch")
		_burst(relay - Vector2(0, 39), TEAL, 16)
		_notice("SIGNAL LIGHTS OFF  /  Follow the green ladder back up.", 3.2)
	for enemy in enemies:
		var offset := Vector2(enemy.x, enemy.y - 20) - origin
		if enemy.hp > 0 and absf(offset.y) < 42.0 and absf(offset.x) < (110.0 if enemy.kind == 4 else 82.0) and face * offset.x > -22.0:
			var guard_block: bool = enemy.kind == 6 and enemy.cool <= 0.0 and (pos.x - enemy.x) * enemy.facing >= 0.0
			if (enemy.kind == 4 and enemy.phase != "recovery") or guard_block:
				enemy.block_until = time + 0.15
				_play("block")
				_burst(Vector2(enemy.x, enemy.y - 28), TEAL, 6)
				_notice("GUARD BRACED  /  Dodge, then strike." if guard_block else "Warden shield up. Strike after its volley.", 1.2)
				continue
			var previous_hp: int = enemy.hp
			enemy.hp -= 2 if charged else 1
			enemy.hit_until = time + 0.12
			_play("impact")
			_burst(Vector2(enemy.x, enemy.y - 28), GOLD, 8)
			charged = false
			if enemy.kind == 4:
				if previous_hp > 8 and enemy.hp <= 8:
					_notice("SERVICE HATCHES OPEN  /  Drop for cover, jump at a ladder.", 2.8)
					_play("switch")
				_burst(Vector2(enemy.x, enemy.y - 47), GOLD, 12)
				continue
			enemy.wind = 0.0
			enemy.cool = 0.56
			enemy.x = clampf(enemy.x + face * 36.0, enemy.home - 94.0, enemy.home + 94.0)
			_burst(Vector2(enemy.x, enemy.y - 23), TEAL, 16 if enemy.hp <= 0 else 7)

func _tick_enemies(delta: float) -> void:
	for enemy in enemies:
		if enemy.hp <= 0:
			continue
		if enemy.kind == 5:
			_tick_sentry(enemy, delta)
			continue
		if enemy.kind == 4:
			_tick_warden(enemy, delta)
			continue
		if enemy.kind == 2:
			_tick_gunner(enemy, delta)
			continue
		enemy.cool -= delta
		var offset: float = pos.x - enemy.x
		var same_deck := absf(pos.y - enemy.y) < 47.0
		if enemy.kind == 6 and _depot_cover():
			same_deck = false
		if enemy.wind > 0.0:
			enemy.wind -= delta
			if enemy.wind <= 0.0:
				enemy.cool = 0.95
				if same_deck and absf(offset) < 65.0:
					_damage(signf(offset))
		elif same_deck and absf(offset) < 195.0 and enemy.cool <= 0.0:
			enemy.facing = signf(offset)
			if absf(offset) < (70.0 if enemy.kind == 6 else 54.0):
				enemy.wind = 0.48 if enemy.kind == 0 else (0.88 if enemy.kind == 6 else 0.70)
			else:
				enemy.x = clampf(enemy.x + signf(offset) * delta * (147.0 if enemy.kind == 3 else (98.0 if enemy.kind == 0 else 64.0)), enemy.home - 94.0, enemy.home + 94.0)

func _beam_x(enemy: Dictionary) -> float:
	if enemy.get("signal_light", false):
		var direction := -1.0 if enemy.home < relay.x else 1.0
		return enemy.home + direction * sin(time * 1.65) * 103.0
	return enemy.home + sin(time * 1.65 + enemy.home * 0.012) * 106.0

func _inside_searchlight(enemy: Dictionary) -> bool:
	var depth := (pos.y - (float(enemy.y) - SEARCHLIGHT_HEIGHT)) / SEARCHLIGHT_HEIGHT
	if depth < 0.0 or depth > 1.0:
		return false
	var center := lerpf(enemy.x, _beam_x(enemy), depth)
	return absf(pos.x - center) < SEARCHLIGHT_HALF_WIDTH * depth

func _tick_sentry(enemy: Dictionary, delta: float) -> void:
	if powered and enemy.get("signal_light", false):
		return
	enemy.cool = maxf(0.0, enemy.cool - delta)
	if enemy.wind > 0.0:
		enemy.wind -= delta
		if enemy.wind <= 0.0:
			var direction := 1.0 if pos.x >= enemy.x else -1.0
			shots.append({"pos": Vector2(enemy.x, enemy.y - 27), "vx": direction * 255.0, "life": 1.1,
				"signal_light": enemy.get("signal_light", false)})
	elif enemy.cool <= 0.0 and _inside_searchlight(enemy):
		enemy.wind = 0.58
		enemy.cool = 2.8
		_play("warn")
		_notice("SPOTTED  /  Drop below the roof or jump the shot.", 2.0)

func _damage(direction: float) -> void:
	if hurt > 0.0 or defeated:
		return
	if shield:
		shield = false
		hurt = 0.5
		_play("block")
		_burst(pos - Vector2(0, 24), TEAL, 8)
		return
	health -= 1
	hurt = 0.85
	jump_tether = 0.0
	velocity = Vector2(direction * 210, -165)
	tether = -1
	_play("hit")
	_burst(pos - Vector2(0, 24), RED, 10)
	if health <= 0:
		_lose()

func _lose() -> void:
	defeated = true
	_clear_input()
	_layout_next_button()
	session.checkpoint()

func _tick_gunner(enemy: Dictionary, delta: float) -> void:
	if enemy.wind > 0.0:
		enemy.wind -= delta
		if enemy.wind <= 0.0:
			enemy.cool = 1.6
			var direction := 1.0 if pos.x >= float(enemy.x) else -1.0
			shots.append({"pos": Vector2(enemy.x, enemy.y - 26), "vx": direction * 235.0, "life": 1.6})
			_play("warn")
	elif absf(pos.x - float(enemy.x)) < 280.0 and absf(pos.y - float(enemy.y)) < 95.0:
		enemy.cool -= delta
		if enemy.cool <= 0.0:
			enemy.wind = 0.48

func _warden_brake_stop(enemy: Dictionary, direction: float) -> float:
	return enemy.home + (-235.0 if direction < 0.0 else 190.0)

func _warden_charge_end(enemy: Dictionary, duration: float) -> float:
	return clampf(enemy.x + enemy.direction * duration * WARDEN_CHARGE_SPEED,
		_warden_brake_stop(enemy, -1.0), _warden_brake_stop(enemy, 1.0))

func _tick_warden(enemy: Dictionary, delta: float) -> void:
	if enemy.phase == "approach":
		if absf(pos.x - float(enemy.x)) < 395.0:
			enemy.phase = "arrival"
			enemy.cool = 1.2
			_play("charge")
			_notice("IRON WARDEN  /  Guarding the last station", 1.6)
		return
	if enemy.phase == "idle":
		if absf(pos.x - float(enemy.x)) > 360.0:
			return
		if enemy.hp <= 4 and not enemy.retreated:
			enemy.retreated = true
			enemy.phase = "retreat"
			enemy.cool = 0.7
			_play("charge")
			_notice("FURNACE OVERDRIVE  /  The Warden is falling back.", 2.1)
			return
		enemy.cycle += 1
		enemy.brake_crash = false
		enemy.direction = -1.0 if pos.x < enemy.x else 1.0
		enemy.phase = "charge_warning" if enemy.hp <= 8 and enemy.cycle % 2 == 0 else "sweep_warning"
		enemy.cool = 0.72 if enemy.retreated else 0.85
		_play("charge" if enemy.phase == "charge_warning" else "sweep")
		if enemy.phase == "charge_warning":
			var stop := _warden_brake_stop(enemy, enemy.direction)
			var can_crash := absf(enemy.x - stop) >= 55.0 and is_equal_approx(_warden_charge_end(enemy, WARDEN_CHARGE_TIME), stop)
			enemy.brake_target = can_crash
			_notice("BRAKE IN ITS PATH  /  Jump or drop out of the charge!" if can_crash
				else "WARDEN CHARGING  /  Tether up!", 1.4)
		else:
			_notice("WARDEN SWEEP  /  Drop below or jump!", 1.4)
		return
	enemy.cool -= delta
	match enemy.phase:
		"arrival":
			if enemy.cool <= 0.0:
				enemy.phase = "idle"
		"retreat":
			enemy.x = move_toward(enemy.x, enemy.home + 145.0, delta * 480.0)
			if enemy.cool <= 0.0:
				enemy.phase = "idle"
		"sweep_warning":
			if enemy.cool <= 0.0:
				for direction in [-1.0, 1.0]:
					shots.append({"pos": Vector2(enemy.x, enemy.y - WARDEN_SWEEP_HEIGHT), "vx": direction * WARDEN_SWEEP_SPEED, "life": WARDEN_SWEEP_LIFE})
				enemy.phase = "recovery"
				enemy.cool = 2.1
				_play("furnace")
				_notice("FURNACE OPEN  /  Strike now!", 1.6)
		"charge_warning":
			if enemy.cool <= 0.0:
				enemy.phase = "charge"
				enemy.cool = WARDEN_CHARGE_TIME
		"charge":
			enemy.x = _warden_charge_end(enemy, delta)
			if absf(pos.x - enemy.x) < 57.0 and absf(pos.y - enemy.y) < 43.0:
				_damage(enemy.direction)
			var stop := _warden_brake_stop(enemy, enemy.direction)
			var crashed: bool = enemy.get("brake_target", false) and is_equal_approx(enemy.x, stop)
			if crashed or enemy.cool <= 0.0:
				enemy.phase = "recovery"
				enemy.brake_crash = crashed
				enemy.cool = 3.15 if crashed else 2.1
				if crashed:
					var impact := Vector2(stop + enemy.direction * 31.0, enemy.y - 19.0)
					_burst(impact, GOLD, 22)
					_play("impact")
					_notice("BRAKE CRASH  /  The furnace is exposed. Strike!", 2.4)
				else:
					_play("furnace")
					_notice("FURNACE OPEN  /  Strike now!", 1.6)
		"recovery":
			if enemy.cool <= 0.0:
				enemy.phase = "idle"

func _hazard_phase(index: int) -> float:
	if level == 4 and index in SURGE_BANKS:
		return fposmod(time - (index - SURGE_BANKS[0]) * 1.2, 3.6)
	return fposmod(time + index * 0.6, 2.7)

func _hazard_live(index: int) -> bool:
	if level == 4:
		if powered:
			return false
		var phase := _hazard_phase(index)
		return (phase >= 0.65 and phase < 1.55) if index in SURGE_BANKS else phase > 1.65
	return _hazard_phase(index) > 1.55

func _hazard_radius(index: int) -> float:
	return 31.0 if level == 4 and index in SURGE_BANKS else 30.0

func _tick_course(delta: float) -> void:
	if defeated:
		return
	for i in range(shots.size() - 1, -1, -1):
		var shot := shots[i]
		shot.pos.x += shot.vx * delta
		shot.life -= delta
		var hit := Vector2(shot.pos).distance_to(pos - Vector2(0, 24)) < 25.0
		if hit:
			_damage(signf(shot.vx))
		if hit or shot.life <= 0:
			shots.remove_at(i)
		if defeated:
			return
	for i in hazards.size():
		var height: float = hazards[i].y - pos.y
		var in_hazard := (height >= -3.0 and height < 32.0) if level == 2 else (grounded and absf(height) < 8.0)
		if level == 4:
			in_hazard = height >= -3.0 and height < 22.0
		if in_hazard and _hazard_live(i) and absf(pos.x - hazards[i].x) < _hazard_radius(i):
			_damage(-face)
			if defeated:
				return
	for pickup in pickups:
		if pickup.taken or pos.distance_to(pickup.pos) > 36.0 or (pickup.kind == 0 and health == 4):
			continue
		pickup.taken = true
		match pickup.kind:
			0: health = mini(4, health + 2)
			1: shield = true
			2: charged = true
		pickup_feedback = ["Health restored", "Shield equipped", "Next strike charged"][pickup.kind]
		pickup_feedback_left = 1.6
		_play("pickup")
		_burst(pickup.pos - Vector2(0, 20), GOLD, 12)

func _burst(at: Vector2, tint: Color, count: int) -> void:
	if session.chrome.reduce_motion:
		count = mini(count, 4)
	for i in count:
		var angle := TAU * float(i) / count + time
		particles.append({"pos": at, "vel": Vector2.from_angle(angle) * (45 + 12 * (i % 7)), "life": 0.22 + i % 5 * 0.045, "tint": tint})

func _polygon(points: Array, color: Color) -> void:
	draw_colored_polygon(PackedVector2Array(points), color)

func _label(at: Vector2, copy: String, height: int, color: Color = Color.WHITE) -> void:
	draw_string(font, at, copy, HORIZONTAL_ALIGNMENT_LEFT, -1, height, color)

func _center(x: float, y: float, copy: String, height: int, color: Color = Color.WHITE) -> void:
	_label(Vector2(x - font.get_string_size(copy, HORIZONTAL_ALIGNMENT_LEFT, -1, height).x * 0.5, y), copy, height, color)

func _draw() -> void:
	var area := _play_area()
	var layout := _layout_in(area)
	var width := _view_width(area)
	draw_rect(Rect2(Vector2.ZERO, size), NIGHT)
	if not session.is_landscape():
		_draw_rotate_prompt(area)
		return
	draw_set_transform_matrix(layout)
	if _intro_active():
		_draw_intro(width, layout)
		return
	if _show_delivery():
		_draw_delivery(width, layout)
		return
	_draw_city(width)
	_draw_track(width)
	var focus := Vector2(385, 280)
	var zoom := Transform2D(Vector2(WORLD_ZOOM, 0), Vector2(0, WORLD_ZOOM), focus * (1.0 - WORLD_ZOOM))
	zoom.origin.y -= cabin_camera_lift
	var world := (layout * zoom).translated_local(Vector2(-camera_x, 0))
	draw_set_transform_matrix(world)
	_draw_train(width, world)
	if level == 1 and pos.x < 550:
		_draw_waiting_keeper()
	elif level == 6 and storm_swing == 1 and storm_reveal > 0.0:
		var lantern := pos + Vector2(face * 18, -22)
		var ring: Vector2 = anchors.back()
		_polygon([lantern, ring - Vector2(5, 14), ring + Vector2(8, 14)], Color(GOLD, 0.12))
		_halo(ring, 39, GOLD)
	if level == 8:
		_draw_signal()
	_draw_goal()
	_draw_course(width)
	if level == 1:
		_draw_bridge(width)
	for enemy in enemies:
		if enemy.hp > 0 and enemy.x >= camera_x - 50 and enemy.x <= camera_x + width + 50:
			_draw_enemy(enemy, world)
	if level == 7:
		var darkness := 1.0 - _blackout_light(pos.x)
		if darkness > 0.0:
			draw_set_transform_matrix(layout)
			_draw_blackout_shade(width, (zoom * (pos - Vector2(camera_x, 0))).x, darkness)
			draw_set_transform_matrix(world)
		if not powered:
			_draw_tunnel_markers(width)
	_draw_courier(world)
	draw_set_transform_matrix(world)
	for p in particles:
		draw_circle(p.pos, 1.4 + p.life * 4.5, Color(p.tint, minf(p.life * 4, 1)))
	draw_set_transform_matrix(layout)
	_draw_pickup_feedback(zoom * (pos - Vector2(camera_x, 0)), width)
	_draw_hud(width)

func _draw_rotate_prompt(area: Rect2) -> void:
	var top := area.position
	var bottom := area.end
	draw_polygon(PackedVector2Array([top, Vector2(bottom.x, top.y), bottom, Vector2(top.x, bottom.y)]),
		PackedColorArray([Color("#142c42"), Color("#142c42"), NIGHT, NIGHT]))
	var center := area.get_center()
	var font_size := clampi(roundi(area.size.x * .082), 18, 64)
	var baseline := center.y + (font.get_ascent(font_size) - font.get_descent(font_size)) * .5
	_center(center.x, baseline, "Rotate to play", font_size, GOLD)

func _scenery_travel(depth: float) -> float:
	return (time * TRAIN_SPEED + camera_x) * depth

func _draw_track(width: float) -> void:
	var offset := fposmod(_scenery_travel(1.0), 58.0)
	draw_line(Vector2(0, 437), Vector2(width, 437), Color("#34434d"), 4)
	for index in range(ceili(width / 58.0) + 2):
		var x := index * 58.0 - offset
		draw_line(Vector2(x, 436), Vector2(x - 17, 451), Color("#46515a"), 4)

func _draw_city(width: float) -> void:
	if level in [1, 2, 4, 6, 8, 9, 10]:
		_draw_district(width)
		return
	if level == 5 or level == 7:
		_draw_feature_landscape(width)
		return
	if level == 3:
		draw_rect(Rect2(0, 0, width, 540), Color("#101b35"))
		var offset := fposmod(_scenery_travel(0.46), 310.0)
		for i in range(1 + ceili((width + offset) / 310.0)):
			var x := i * 310.0 - offset
			draw_line(Vector2(x, 109), Vector2(x, 422), Color("#253a59"), 12)
			draw_line(Vector2(x, 109), Vector2(x + 310, 109), Color("#253a59"), 9)
			draw_line(Vector2(x, 110), Vector2(x + 132, 237), Color("#253a59"), 8)
			draw_circle(Vector2(x + 8, 124), 8, Color("#829ad4"))
		draw_rect(Rect2(0, 414, width, 126), Color("#131b31"))
		return
func _draw_district(width: float) -> void:
	var sky: Color = {1: Color("#302136"), 2: Color("#171c42"), 4: Color("#271a37"),
		6: Color("#24384c"), 8: Color("#142c35"), 9: Color("#a45f69"), 10: Color("#2e1921")}[level]
	draw_rect(Rect2(0, 0, width, 540), sky)
	if level == 9:
		draw_circle(Vector2(width * 0.73, 176), 69, Color("#fac392"), true, -1, true)
		draw_rect(Rect2(0, 263, width, 95), Color("#87536b"))
	elif level == 6 and storm_zone == 1 and not session.chrome.reduce_motion:
		var light := maxf(maxf(storm_transition * 0.42, storm_reveal * 0.46),
			0.16 if _wind_phase() > 2.52 and _wind_phase() < 2.62 else 0.0)
		draw_rect(Rect2(0, 0, width, 414), Color(Color("#a1c8e2"), light))
	if level == 2:
		draw_circle(Vector2(width - 185, 115), 52, Color("#e0d8d5"), true, -1, true)
		draw_circle(Vector2(width - 162, 100), 47, sky, true, -1, true)
	var travel := _scenery_travel(0.48)
	var spacing := 255.0
	for i in range(-1, ceili(width / spacing) + 2):
		var x := i * spacing - fposmod(travel, spacing)
		var district := i + floori(travel / spacing)
		match level:
			1:
				draw_rect(Rect2(x, 132, 255, 21), Color("#6a4144"))
				draw_rect(Rect2(x + 16, 152, 13, 264), Color("#5a434d"))
				draw_rect(Rect2(x + 81, 154, 83, 7), GOLD)
				_halo(Vector2(x + 121, 163), 66, GOLD)
				draw_rect(Rect2(x + 158, 212, 86, 31), Color("#38253c"))
				_center(x + 201, 233, "CENTRAL", 14, GOLD)
			2:
				var top := 177 + posmod(district, 4) * 32
				draw_rect(Rect2(x + 8, top, 234, 416 - top), Color("#2c3053"))
				for window in 4:
					draw_rect(Rect2(x + 30 + window * 51, top + 36, 22, 30), Color("#c98f65"))
				draw_line(Vector2(x + 69, top - 8), Vector2(x + 202, top + 5), Color("#9b8dba"), 2)
				for cloth in 3:
					draw_rect(Rect2(x + 85 + cloth * 32, top - 4 + cloth * 3, 18, 15), Color("#8b619a"))
			4:
				draw_rect(Rect2(x + 37, 194, 120, 224), Color("#3d2b46"))
				draw_line(Vector2(x + 71, 96), Vector2(x + 71, 416), Color("#69506a"), 8)
				draw_line(Vector2(x + 34, 133), Vector2(x + 111, 133), Color("#a76d8d"), 6)
				draw_line(Vector2(x + 71, 136), Vector2(x + spacing + 71, 136), Color("#5d3f6a"), 3)
				draw_rect(Rect2(x + 90, 213, 42, 8), Color("#b080da"))
			6:
				if storm_zone != 1:
					var sheltered := Color("#24313b") if storm_zone == 0 else Color("#333c42")
					draw_rect(Rect2(x, 118, spacing, 298), sheltered)
					draw_line(Vector2(x + 4, 128), Vector2(x + 4, 414), Color("#51616b"), 14)
					draw_rect(Rect2(x + 96, 164, 64, 6), GOLD)
					_halo(Vector2(x + 128, 168), 47, GOLD)
					continue
				draw_line(Vector2(x + 10, 111), Vector2(x + 10, 416), Color("#385569"), 13)
				draw_line(Vector2(x, 111), Vector2(x + spacing, 111), Color("#4e697a"), 9)
				draw_line(Vector2(x + 10, 121), Vector2(x + spacing, 399), Color("#385569"), 10)
				draw_line(Vector2(x + 10, 392), Vector2(x + spacing, 392), Color("#4e697a"), 6)
				var flag := RED if _wind_phase() >= 2.65 else (GOLD if _wind_phase() >= 1.95 else TEAL)
				var gusting := _wind_phase() >= 2.65
				_polygon([Vector2(x + 16, 138), Vector2(x - (64 if gusting else 21), 151 if gusting else 179),
					Vector2(x + 16, 160)], flag)
			8:
				draw_rect(Rect2(x, 190, 230, 165), Color("#274652"))
				draw_line(Vector2(x - 12, 331), Vector2(x + spacing, 343), Color("#5f7d7b"), 5)
				draw_line(Vector2(x - 12, 355), Vector2(x + spacing, 344), Color("#48676f"), 5)
				draw_rect(Rect2(x + 125, 140, 9, 109), Color("#64797e"))
				draw_circle(Vector2(x + 130, 146), 11, RED if posmod(district, 2) == 0 else TEAL)
			9:
				draw_rect(Rect2(x + 18, 311, 31, 105), Color("#633f50"))
				draw_line(Vector2(x + 32, 306), Vector2(x + spacing - 38, 305), Color("#694c57"), 12)
				draw_line(Vector2(x + 40, 332), Vector2(x + 134, 406), Color("#633f50"), 6)
			10:
				draw_rect(Rect2(x + 15, 143, 191, 278), Color("#432a32"))
				draw_line(Vector2(x + 37, 120), Vector2(x + 37, 405), Color("#613840"), 12)
				draw_rect(Rect2(x + 98, 171, 79, 22), Color("#8c4d41"))
				_halo(Vector2(x + 137, 181), 58, RED)
	draw_rect(Rect2(0, 414, width, 126), sky.darkened(0.38))
	if level == 6 and storm_zone == 1:
		for i in 24:
			var x := fposmod(i * 131.0 - time * 330.0, width + 80) - 40
			var y := fposmod(i * 93.0 + time * 210.0, 435)
			draw_line(Vector2(x, y), Vector2(x - 26, y + 19), Color("#a8cada70"), 2)


func _draw_feature_landscape(width: float) -> void:
	if level == 7:
		draw_rect(Rect2(0, 0, width, 540), Color("#182f3b") if powered else Color("#12101e"))
		for y in [82, 151, 219, 315, 416]:
			draw_line(Vector2(0, y), Vector2(width, y), Color("#375260") if powered else Color("#282035"), 3)
		var offset := fposmod(_scenery_travel(0.76), 270.0)
		for i in range(1 + ceili((width + offset) / 270.0)):
			var x := i * 270.0 - offset
			draw_line(Vector2(x, 44), Vector2(x, 416), Color("#41616c") if powered else Color("#282438"), 11)
			var lamp := Color("#e05755").lerp(TEAL, _blackout_light(camera_x + x + 37.0))
			draw_rect(Rect2(x + 20, 116, 34, 8), lamp)
			_halo(Vector2(x + 37, 120), 35, lamp)
		return
	draw_rect(Rect2(0, 0, width, 540), Color("#293451"))
	draw_circle(Vector2(width * 0.76, 123), 61, Color("#e3a478"), true, -1, true)
	for layer in 3:
		var travel := _scenery_travel(0.16 + layer * 0.16)
		var tint: Color = [Color("#685873"), Color("#51475f"), Color("#34334a")][layer]
		var points := PackedVector2Array([Vector2(0, 540)])
		for i in range(-1, ceili(width / 154.0) + 2):
			var x := i * 154.0 - fposmod(travel, 154.0)
			var peak := posmod(i + floori(travel / 154.0) * -1, 5)
			points.append(Vector2(x, 209 + layer * 62 + peak * 13))
			points.append(Vector2(x + 51, 209 + layer * 62 + peak * 13))
		points.append(Vector2(width + 308, 540))
		draw_colored_polygon(points, tint)

func _draw_blackout_shade(width: float, courier_x: float, darkness: float) -> void:
	var light := courier_x + face * 44.0
	var edges := PackedFloat32Array([0.0, light - 244, light - 128, light - 35,
		light + 35, light + 128, light + 244, width])
	var opacity := PackedFloat32Array([0.77, 0.77, 0.50, 0.04, 0.04, 0.50, 0.77, 0.77])
	for index in range(edges.size() - 1):
		var left := edges[index]
		var right := edges[index + 1]
		if right <= 0.0 or left >= width or right <= left:
			continue
		var visible_left := maxf(left, 0.0)
		var visible_right := minf(right, width)
		var tint := Color("#030410")
		var start := Color(tint, darkness * lerpf(opacity[index], opacity[index + 1], inverse_lerp(left, right, visible_left)))
		var end := Color(tint, darkness * lerpf(opacity[index], opacity[index + 1], inverse_lerp(left, right, visible_right)))
		draw_polygon(PackedVector2Array([Vector2(visible_left, 0), Vector2(visible_right, 0),
			Vector2(visible_right, 447), Vector2(visible_left, 447)]), PackedColorArray([start, end, end, start]))

func _lantern_reveal(at: Vector2) -> float:
	var center := pos + Vector2(face * 85.0, -29.0)
	return 1.0 - smoothstep(35.0, 183.0, center.distance_to(at))

func _draw_tunnel_markers(width: float) -> void:
	for car in roofs:
		for x in [car.position.x + 9.0, car.end.x - 9.0]:
			if x < camera_x - 50 or x > camera_x + width:
				continue
			for y in [car.position.y + 4, 364.0]:
				var marker := Vector2(x, y)
				var glow := _lantern_reveal(marker)
				draw_rect(Rect2(marker - Vector2(6, 3), Vector2(12, 6)), Color(GOLD, lerpf(0.45, 1.0, glow)))
				if glow > 0.0:
					draw_line(marker, marker + Vector2(0, -13), Color(GOLD, glow * 0.72), 2)
	if relay.x < camera_x - 180 or relay.x > camera_x + width + 180:
		return
	var roof_y: float = roofs[3].position.y
	var roof_port := Vector2(relay.x, roof_y + 4)
	var console := relay - Vector2(0, 42)
	var glow := _lantern_reveal(roof_port)
	draw_line(roof_port, console, Color(TEAL, 0.16 + glow * 0.62), 3)
	draw_circle(roof_port, 5, Color(TEAL, 0.45 + glow * 0.55), true, -1.0, true)
	if glow > 0.38:
		_center(roof_port.x, roof_port.y - 18, "RELAY BELOW", 16, GOLD)
	for offset in [-115, -78, 78, 115]:
		var marker := relay + Vector2(offset, -9)
		var heading := -signf(offset)
		draw_polyline(PackedVector2Array([marker + Vector2(-heading * 8, -7), marker,
			marker + Vector2(-heading * 8, 7)]), Color(TEAL, 0.32 + _lantern_reveal(marker) * 0.68), 3, true)

func _draw_night_coupler(departure: Rect2, arrival: Rect2, split: int) -> void:
	var end := Vector2(departure.end.x, departure.position.y + 4)
	var start := arrival.position + Vector2(0, 4)
	var tint := GOLD if split == 0 else RED
	for stripe in range(4):
		var offset := 7.0 + stripe * 9.0
		draw_line(end + Vector2(-offset, 2), end + Vector2(-offset - 5, 9), tint, 3, true)
	draw_line(start + Vector2(4, 0), start + Vector2(65, 0), TEAL, 4, true)
	draw_line(Vector2(start.x - 15, 391), Vector2(start.x + 2, 391), Color("#5c6c70"), 7)
	draw_circle(Vector2(end.x + 12, 382), 4, tint, true, -1.0, true)
	if absf(pos.x - end.x) < 170.0:
		_center(end.x - 57.0, end.y - 42.0, "HOLD RIGHT + JUMP" if split == 0 else "STEAM AHEAD", 15, GOLD)

func _freight_steps(car: Rect2) -> Array[Rect2]:
	var edge := car.end.x
	var y := car.position.y
	return [Rect2(edge - 142.0, y - 33.0, 142.0, 10.0),
		Rect2(edge - 78.0, y - 68.0, 78.0, 10.0)]

func _draw_freight_stack(car: Rect2) -> void:
	for step in _freight_steps(car):
		var height := 35.0
		var body := Rect2(step.position, Vector2(step.size.x, height))
		draw_rect(body, Color("#533b3b"))
		draw_rect(body.grow(-3), Color("#745347"))
		for offset in range(10, int(step.size.x) - 3, 13):
			var rib := step.position + Vector2(offset, 8)
			draw_line(rib, rib + Vector2(0, height - 13), Color("#473637"), 2)
		draw_line(step.position, step.position + Vector2(step.size.x, 0), GOLD, 2, true)
		for offset in [-8.0, -19.0, -30.0]:
			var mark := Vector2(step.end.x + offset, step.position.y + 5.0)
			draw_line(mark, mark + Vector2(-5, 7), GOLD, 2, true)
	if absf(pos.x - car.end.x + 78.0) < 165.0:
		_center(car.end.x - 53.0, car.position.y - 83.0, "HIGH LAUNCH", 15, GOLD)

func _draw_storm_windbreak(car: Rect2) -> void:
	var shelter := _storm_windbreak(car)
	var y := car.position.y
	var x := shelter.position.x
	var phase := _wind_phase()
	var flag := TEAL if phase < 1.95 else (GOLD if phase < 2.65 else RED)
	draw_rect(shelter, Color("#263a46b8"))
	draw_line(Vector2(x, y), Vector2(x, shelter.position.y), Color("#7b929b"), 5)
	draw_line(Vector2(x, shelter.position.y), Vector2(shelter.end.x, shelter.position.y), Color("#557681"), 3)
	draw_line(Vector2(x + 2, y + 3), Vector2(shelter.end.x, y + 3), TEAL, 3)
	for offset in [22.0, 52.0, 82.0]:
		draw_line(Vector2(x + offset, y), Vector2(x + offset - 16.0, y - 32.0), Color("#42616e"), 3)
	var mast := Vector2(shelter.end.x, shelter.position.y)
	draw_line(mast, mast + Vector2(0, -21), Color("#819ca5"), 3)
	var flag_end := mast + Vector2(-26 if phase >= 1.95 else -12, -12 if phase >= 1.95 else -5)
	_polygon([mast + Vector2(0, -20), flag_end, mast + Vector2(0, -8)], flag)
	if absf(pos.x - shelter.get_center().x) < 135.0:
		_center(shelter.get_center().x, y - 12, "WIND SHELTER", 14, TEAL)

func _draw_train(width: float, world: Transform2D) -> void:
	for i in range(roofs.size()):
		var roof: Rect2 = roofs[i]
		var collapse_age: float = time - collapse_times.get(i, INF)
		var collapse_duration := _collapse_duration(i)
		if roof.end.x <= collapsed_until and (collapse_age < collapse_duration or collapse_age >= collapse_duration + 0.5):
			continue
		var x := roof.position.x
		var y := roof.position.y
		var w := roof.size.x
		if x + w < camera_x - 50 or x > camera_x + width + 50:
			continue
		if level == 9 and collapse_age >= collapse_duration - 1.0:
			var fall := maxf(0.0, collapse_age - collapse_duration)
			var pivot := roof.get_center()
			var tilt := fall * 0.65
			if not session.chrome.reduce_motion:
				tilt += sin(time * 21.0) * 0.013
			var transform := Transform2D(tilt, Vector2.ZERO)
			transform.origin = pivot - transform * pivot + Vector2(0, fall * fall * 780.0)
			draw_set_transform_matrix(world * transform)
		else:
			draw_set_transform_matrix(world)
		var body := SLEEPER_ART
		if level == 10 or (level == 8 and branch == "Depot" and i >= 4):
			body = ARMOR_ART
		elif level == 5 or i == roofs.size() - 2:
			body = FREIGHT_ART
		draw_texture_rect(body, Rect2(x - 3, y + 1, w + 6, 389 - y), false)
		if _has_depot_carriage() and i == DEPOT_CARRIAGE:
			_draw_depot_carriage(roof)
		if level == 7 and powered and body == SLEEPER_ART:
			for window_index in 5:
				var wx := x + (26.0 + window_index * 53.0) * w / 320.0
				draw_rect(Rect2(wx, y + (389 - y) * 0.43, w * 0.09, (389 - y) * 0.10), Color(TEAL, 0.33 * _blackout_light(wx)))
		draw_line(Vector2(x, y + 4), Vector2(x + w, y + 4), Color("#80a0a3"), 2)
		if level == 5 and i in FREIGHT_STACK_CARS:
			_draw_freight_stack(roof)
		draw_line(Vector2(x, 365), Vector2(x + w, 365), Color("#5c7a83"), 4)
		if level == 2 and i == NIGHT_SPLIT_CARS[0]:
			_label(Vector2(x + 23, 360), "ROYAL MAIL", 11, GOLD)
		else:
			_label(Vector2(x + 23, 360), APPROACH_NAMES[level - 1] if i == roofs.size() - 2 else (["MAIL", "SLEEPER", "BOILER", "SIGNAL", "POWER"][i] if i < 5 else ("ENGINE" if i == roofs.size() - 1 else "FREIGHT %02d" % (i - 4))), 11, GOLD)
		for dx in [42, 73, w - 73, w - 42]:
			var wheel := Vector2(x + dx, 389)
			draw_circle(wheel, 17, Color("#09121e"))
			draw_arc(wheel, 12, 0, TAU, 16, Color("#4b626b"), 2, true)
			draw_line(wheel, wheel + Vector2.from_angle(time * TRAIN_SPEED / WHEEL_RADIUS) * 11, Color("#65838c"), 3)
		if i == roofs.size() - 2:
			var accent: Color = [GOLD, Color("#a9c9d9"), Color("#83b5ee"), Color("#c696ed"), Color("#e5ac73"),
				Color("#81c8d9"), RED, TEAL, Color("#efb18c"), Color("#f08362")][level - 1]
			for band in [0.13, 0.86]:
				var stripe: float = x + w * band
				draw_line(Vector2(stripe, y + 12), Vector2(stripe, 342), Color(accent, 0.58), 3)
			draw_rect(Rect2(x + w * 0.5 - 26, 335, 52, 6), Color(accent, 0.65))
		if level == 6 and i in STORM_WINDBREAK_CARS:
			_draw_storm_windbreak(roof)
		if level == 6 and i == roofs.size() - 1 and storm_swing in [1, 2]:
			draw_line(Vector2(x + 2, y + 2), Vector2(x + 95, y + 2), GOLD, 4, true)
			_halo(Vector2(x + 38, y + 3), 51, GOLD)
		if (level == 5 or (level == 6 and i == roofs.size() - 1)) and i > 0 and x - roofs[i - 1].end.x > 100.0:
			for stripe in range(4):
				var edge := Vector2(x + 10 + 10 * stripe, y + 4)
				draw_line(edge, edge + Vector2(8, 8), GOLD, 3, true)
		if level == 9 and i < roofs.size() - 1:
			var warning := collapse_warned.has(i)
			var crack := Vector2(x + w * 0.58, y + 4)
			draw_polyline(PackedVector2Array([crack, crack + Vector2(-13, 11),
				crack + Vector2(7, 25), crack + Vector2(-10, 50)]), GOLD if warning else Color("#7b4646"), 4, true)
			if warning and collapse_age < collapse_duration:
				var remaining := clampf(collapse_duration - collapse_age, 0.0, 1.0)
				draw_rect(Rect2(crack.x - 23, crack.y - 19, 46, 7), NIGHT)
				draw_rect(Rect2(crack.x - 21, crack.y - 17, remaining * 42, 3), GOLD)
		if level == 9 and i == roofs.size() - 2:
			draw_line(Vector2(roof.end.x - 56.0, y + 3), Vector2(roof.end.x, y + 3), GOLD, 4, true)
			if absf(pos.x - roof.end.x) < 170.0:
				_center(roof.end.x - 58.0, y - 18, "LAST SWING", 15, GOLD)
		elif level == 9 and i == roofs.size() - 1:
			draw_line(Vector2(x + 3, y + 3), Vector2(x + 113, y + 3), TEAL, 4, true)
			for post in [14.0, 104.0]:
				var lamp := Vector2(x + post, y - 10)
				draw_line(lamp, lamp + Vector2(0, 12), Color("#76959d"), 3)
				draw_circle(lamp, 4, TEAL, true, -1.0, true)
		if level == 10 and i == roofs.size() - 1:
			for vent in 7:
				var vx := x + 22 + vent * 96
				draw_rect(Rect2(vx, y + 5, 70, 10), Color("#9c5242"))
		var hatch := x + w * 0.52
		draw_line(Vector2(hatch - 21, y + 5), Vector2(hatch + 21, y + 5), Color("#c18b5d"), 3)
		for offset in [-12, 0, 12]:
			draw_line(Vector2(hatch + offset, y + 7), Vector2(hatch + offset + 4, y + 12), Color("#c18b5d"), 2)
		if i + 1 < roofs.size():
			var reach := 56.0
			if level == 2:
				var split := NIGHT_SPLIT_CARS.find(i + 1)
				if split >= 0:
					reach = lerpf(56.0, 19.0, night_split[split])
					_draw_night_coupler(roof, roofs[i + 1], split)
				else:
					reach = minf(reach, roofs[i + 1].position.x - roof.end.x)
			draw_line(Vector2(x + w, 391), Vector2(x + w + reach, 391), Color("#5c6c70"), 7)
			draw_line(Vector2(x + w + 5, 397), Vector2(x + w + reach - 4, 397), Color("#263c49"), 3)
	draw_set_transform_matrix(world)
	if branch == "Floodline" and _floodline_start() < camera_x + width and goal_x + 25.0 > camera_x:
		_draw_floodline_water()
	if level == 1:
		_draw_tutorial_signs(width)
		if not practice_swing_done:
			var landing: Rect2 = roofs[1]
			draw_line(landing.position + Vector2(5, 3), landing.position + Vector2(81, 3), GOLD, 4, true)
			_halo(landing.position + Vector2(36, 5), 43, GOLD)
	var jump_target := _jump_ready_ring()
	for i in anchors.size():
		var at: Vector2 = anchors[i]
		if at.x <= collapsed_until or at.x < camera_x - 50 or at.x > camera_x + width + 50:
			continue
		var signature := level == 6 and i == anchors.size() - 1
		if signature and storm_swing == 0:
			draw_arc(at, 8, 0, TAU, 18, Color("#637883"), 2, true)
			continue
		if signature and storm_swing in [1, 2]:
			_halo(at, 44, GOLD)
			draw_arc(at, 17, 0, TAU, 28, GOLD, 3, true)
		draw_line(at + Vector2(0, 11), at + Vector2(16, 54), Color("#385666"), 3, true)
		_halo(at, 22 if i == jump_target else 13, TEAL)
		draw_arc(at, 8, 0, TAU, 18, Color("#92f1a9") if i == jump_target else TEAL, 2.5, true)
		if i == jump_target:
			var tip := at - Vector2(0, 23)
			draw_polyline(PackedVector2Array([tip + Vector2(-7, 7), tip, tip + Vector2(7, 7)]), TEAL, 3, true)

func _draw_depot_carriage(car: Rect2) -> void:
	var x := car.position.x
	var y := car.position.y
	var hatch := Vector2(x + 48, y + 4)
	draw_rect(Rect2(hatch - Vector2(22, 5), Vector2(44, 10)), Color("#5e6c6a"))
	for stripe in range(4):
		var marker := hatch + Vector2(-18 + stripe * 10, 0)
		draw_line(marker, marker + Vector2(5, -5), GOLD, 2, true)
	if not _inside_depot_carriage():
		return
	var height := DEPOT_FLOOR - y
	var cabin := Rect2(x + 3, y + 10, car.size.x - 6, height - 11)
	draw_rect(cabin, Color("#202632"))
	draw_rect(Rect2(x + 6, y + 18, car.size.x - 12, height - 33), Color("#3f3b38"))
	for bay in 3:
		var left := x + 12 + 84.0 * bay
		draw_rect(Rect2(left, y + 20, 68, height - 42), Color("#262a32"))
		draw_line(Vector2(left + 3, y + 20), Vector2(left + 3, 354), Color("#57554a"), 3)
	draw_rect(Rect2(x + 48, 314, 54, 42), Color("#303d40"))
	draw_line(Vector2(x + 50, 318), Vector2(x + 99, 318), Color("#b79061"), 3)
	_center(x + 75, 310, "COVER", 12, GOLD)
	var ladder_x := car.end.x - 35.0
	for rail_offset in [-9, 9]:
		draw_line(Vector2(ladder_x + rail_offset, y + 5), Vector2(ladder_x + rail_offset, 359), Color("#a58a62"), 3)
	for rung in range(7):
		var rung_y := y + 9.0 + rung * (height - 20.0) / 6.0
		draw_line(Vector2(ladder_x - 9, rung_y), Vector2(ladder_x + 9, rung_y), Color("#85745d"), 2)
	var light_x := clampf(pos.x + face * 18.0, cabin.position.x + 16, cabin.end.x - 16)
	var lantern := Vector2(light_x, pos.y - 30.0)
	draw_rect(Rect2(cabin.position.x, cabin.position.y, maxf(0.0, light_x - 63 - cabin.position.x), cabin.size.y), Color("#1217268a"))
	var shadow_x := minf(cabin.end.x, light_x + 63.0)
	draw_rect(Rect2(shadow_x, cabin.position.y, cabin.end.x - shadow_x, cabin.size.y), Color("#1217268a"))
	_halo(lantern, 57, GOLD)
	draw_line(Vector2(x + 6, 358), Vector2(car.end.x - 6, 358), Color("#856e55"), 2)

func _draw_tutorial_signs(width: float) -> void:
	var signs := [[180.0, 365.0, "JUMP ONTO THE ROOF"],
		[535.0, 278.0, "HOLD RIGHT + JUMP"], [float(bridge_x) - 111.0, final_roof_y, "DROP FOR LOW BRIDGE"]]
	for sign in signs:
		var x: float = sign[0]
		var y: float = sign[1]
		if x < camera_x - 100 or x > camera_x + width:
			continue
		draw_rect(Rect2(x - 95, y - 86, 190, 25), Color("#201b28"))
		_center(x, y - 68, sign[2], 15, GOLD)

func _halo(at: Vector2, radius: float, tint: Color) -> void:
	draw_circle(at, radius, Color(tint, 0.065))
	draw_circle(at, radius * 0.56, Color(tint, 0.095))

func _draw_floodline_water() -> void:
	var start := _floodline_start()
	var surface := _floodline_surface()
	var phase := _floodline_phase()
	var tint := RED if surface <= 365.0 else (GOLD if phase >= 0.95 and phase < 2.35 else TEAL)
	draw_rect(Rect2(start, surface, goal_x + 25.0 - start, 429.0 - surface), Color("#367d8096"))
	draw_line(Vector2(start, surface), Vector2(goal_x + 25.0, surface), TEAL, 2)
	for offset in [40.0, 124.0, 215.0]:
		var wake := Vector2(start + offset, surface + 13.0)
		draw_line(wake, wake + Vector2(37, 0), Color(TEAL, 0.48), 2)
	var marker := Vector2(start - 12.0, final_roof_y + 14.0)
	draw_line(marker, Vector2(marker.x, 365), Color("#738e94"), 4)
	_halo(marker, 28, tint)
	draw_circle(marker, 7, tint, true, -1.0, true)
	if absf(pos.x - marker.x) < 140.0:
		_center(marker.x, final_roof_y - 14.0, "SURGE ZONE", 15, tint)

func _draw_signal() -> void:
	var lamp := junction - Vector2(0, 58)
	var tint := TEAL if branch == "Floodline" else GOLD
	if branch.is_empty():
		tint = GOLD.lerp(RED, 0.5 + 0.5 * sin(time * 5))
	draw_line(lamp, lamp + Vector2(0, 57), Color("#758e95"), 6)
	draw_rect(Rect2(lamp - Vector2(15, 20), Vector2(30, 38)), Color("#253b46"))
	_halo(lamp, 43, tint)
	draw_circle(lamp, 8, tint, true, -1, true)
	_center(lamp.x, lamp.y - 29, "JUNCTION 08", 15, Color("#e5ece9"))
	if branch == "Floodline":
		var beacon := Vector2(goal_x - 42.0, final_roof_y - 43.0)
		draw_line(beacon, Vector2(beacon.x, final_roof_y), Color("#779da4"), 4)
		_halo(beacon, 41, GOLD if rescue else TEAL)
		draw_circle(beacon, 7, GOLD if rescue else TEAL)
		for offset in [-63, -36]:
			var crew := Vector2(beacon.x + offset, final_roof_y)
			draw_circle(crew - Vector2(0, 31), 7, Color("#e2c49c"))
			draw_rect(Rect2(crew.x - 8, crew.y - 24, 16, 23), Color("#9c5950") if rescue else Color("#4b7582"))


func _draw_goal() -> void:
	if level == 8 and branch.is_empty():
		return
	var feet := Vector2(goal_x, _goal_deck())
	var ready := (not _needs_relay() or powered) and not _boss_alive() and (branch != "Floodline" or rescue)
	var tint := TEAL if ready else RED
	var lamp := feet - Vector2(0, 53)
	draw_rect(Rect2(feet - Vector2(20, 6), Vector2(40, 6)), Color("#786551"))
	draw_rect(Rect2(feet - Vector2(5, 47), Vector2(10, 43)), Color("#4c6670"))
	draw_rect(Rect2(lamp - Vector2(15, 16), Vector2(30, 32)), Color("#233444"))
	draw_line(lamp - Vector2(16, 16), lamp + Vector2(16, -16), Color("#aa875b"), 3)
	_halo(lamp, 35, tint)
	draw_circle(lamp, 10, Color("#101721"), true, -1, true)
	draw_circle(lamp, 7, tint, true, -1, true)
	draw_circle(lamp - Vector2(2, 2), 2, Color("#f7efe0"))
	_center(lamp.x, lamp.y - 25, "EXIT SIGNAL", 16, GOLD)

func _draw_warden_hatches() -> void:
	for x in _warden_ladders():
		var roof := Vector2(x, final_roof_y)
		draw_rect(Rect2(x - 21, final_roof_y - 3, 42, 9), Color("#513e34"))
		for offset in [-9, 9]:
			draw_line(roof + Vector2(offset, 5), Vector2(x + offset, 359), Color("#b1976c"), 3)
		for y in range(int(final_roof_y) + 12, 360, 12):
			draw_line(Vector2(x - 9, y), Vector2(x + 9, y), Color("#968363"), 2)
		draw_circle(roof + Vector2(0, 4), 4, TEAL, true, -1.0, true)
		if absf(x - pos.x) < 100.0:
			_center(x, final_roof_y - 14 if pos.y < 335.0 else 353.0,
				"DROP" if pos.y < 335.0 else "JUMP", 15, GOLD)

func _draw_warden_brakes(enemy: Dictionary) -> void:
	for direction in [-1.0, 1.0]:
		var x: float = _warden_brake_stop(enemy, direction) + direction * 33.0
		var y: float = enemy.y
		var targeted: bool = enemy.direction == direction and enemy.phase in ["charge_warning", "charge"] and enemy.get("brake_target", false)
		var jammed: bool = enemy.direction == direction and enemy.phase == "recovery" and enemy.get("brake_crash", false)
		var lamp := GOLD if targeted else (RED if jammed else Color("#ad866e"))
		draw_rect(Rect2(x - 16, y - 28, 32, 28), Color("#41353a"))
		draw_rect(Rect2(x - 19, y - 5, 38, 5), Color("#9b7567"))
		draw_circle(Vector2(x, y - 21), 5, lamp, true, -1.0, true)
		for stripe in [-10.0, 0.0, 10.0]:
			draw_line(Vector2(x + stripe - 3, y - 7), Vector2(x + stripe + 3, y - 14), lamp, 2, true)
		if targeted:
			_center(x, y - 34, "BRAKE", 14, GOLD)

func _draw_warden_warning() -> void:
	if not _boss_alive():
		return
	var enemy: Dictionary = enemies.back()
	_draw_warden_brakes(enemy)
	var car: Rect2 = roofs.back()
	var x: float = enemy.x
	var y: float = enemy.y
	if enemy.phase == "sweep_warning":
		var extent := WARDEN_SWEEP_SPEED * WARDEN_SWEEP_LIFE
		var left := maxf(car.position.x, x - extent)
		var right := minf(car.end.x, x + extent)
		var lane := y - WARDEN_SWEEP_HEIGHT
		draw_rect(Rect2(left, lane - 13, right - left, 26), Color(RED, 0.16))
		draw_dashed_line(Vector2(left, lane), Vector2(right, lane), RED, 2, 12)
	elif enemy.phase == "charge_warning":
		var destination := _warden_charge_end(enemy, WARDEN_CHARGE_TIME)
		var direction: float = enemy.direction
		var start := minf(x, destination)
		var length := absf(destination - x)
		draw_rect(Rect2(start, y - 8, length, 8), Color(GOLD, 0.48))
		for distance in range(28, int(length), 44):
			var tip := Vector2(x + distance * direction, y - 13)
			draw_polyline(PackedVector2Array([tip + Vector2(-10 * direction, -7),
				tip, tip + Vector2(-10 * direction, 7)]), GOLD, 3, true)
	elif enemy.phase == "recovery":
		_center(x, y - 77, "STUNNED  /  STRIKE" if enemy.get("brake_crash", false) else "STRIKE NOW", 17, GOLD)

func _draw_signal_car(width: float) -> void:
	var car: Rect2 = roofs[SENTRY_CAR]
	if car.end.x < camera_x - 50 or car.position.x > camera_x + width + 50:
		return
	var ladder := car.end.x - 37.0
	var y := car.position.y
	var console := relay - Vector2(0, 38)
	var tint := TEAL if powered else GOLD
	draw_line(Vector2(car.position.x + 8, y + 8), Vector2(car.end.x - 8, y + 8), Color(tint, 0.65), 3)
	draw_rect(Rect2(console - Vector2(27, 18), Vector2(54, 52)), Color("#202b3b"))
	draw_rect(Rect2(console - Vector2(20, 11), Vector2(40, 14)), tint)
	for offset in [-12, 0, 12]:
		draw_circle(console + Vector2(offset, 12), 3, tint)
	for offset in [-9, 9]:
		draw_line(Vector2(ladder + offset, y + 5), Vector2(ladder + offset, 360), Color("#77a49f"), 3)
	for rung in range(int(y) + 14, 358, 12):
		draw_line(Vector2(ladder - 9, rung), Vector2(ladder + 9, rung), TEAL, 2)
	if pos.y >= y + 48.0:
		_center(console.x, console.y - 26, "LIGHTS OFF" if powered else "STRIKE SWITCH", 16, tint)
		_center(ladder, 348.0, "JUMP", 15, TEAL)
	elif not powered:
		_center(car.position.x + 64.0, y - 20.0, "DROP FOR COVER", 16, GOLD)

func _draw_switchgear(width: float) -> void:
	var car: Rect2 = roofs[SWITCHGEAR_CAR]
	if car.end.x < camera_x - 50 or car.position.x > camera_x + width + 50:
		return
	var y := car.position.y
	var ladder := car.end.x - 30.0
	draw_rect(Rect2(car.position.x + 9, y + 10, car.size.x - 18, 8), Color("#362b45"))
	for bank in SURGE_BANKS:
		var x: float = hazards[bank].x
		draw_line(Vector2(x, y + 15), Vector2(x, y + 38), Color("#9874a1"), 3)
		draw_rect(Rect2(x - 13, y + 32, 26, 20), Color("#312338"))
		var tint := TEAL if powered else (RED if _hazard_live(bank) else (GOLD if _hazard_phase(bank) < 0.65 else Color("#6c537b")))
		draw_circle(Vector2(x, y + 42), 5, tint, true, -1.0, true)
	for offset in [-8, 8]:
		draw_line(Vector2(ladder + offset, y + 4), Vector2(ladder + offset, 358), Color("#7fa4a0"), 3)
	for rung in range(int(y) + 14, 358, 12):
		draw_line(Vector2(ladder - 8, rung), Vector2(ladder + 8, rung), TEAL, 2)
	if pos.y > y + 55:
		_center(ladder, 347, "JUMP", 15, TEAL)
	elif not powered:
		_center(car.position.x + 78, y - 30, "DROP FOR COVER", 15, GOLD)

func _draw_blackout_hardware(width: float) -> void:
	var gate := _blackout_shutter_x()
	if gate > camera_x - 45 and gate < camera_x + width + 45:
		var raised := _blackout_light(gate)
		var bottom := lerpf(321.0, 151.0, raised)
		draw_rect(Rect2(gate - 12, 73, 24, bottom - 73), Color("#3e4255"))
		for y in range(83, int(bottom), 12):
			draw_line(Vector2(gate - 10, y), Vector2(gate + 10, y), Color("#777582"), 2)
		draw_rect(Rect2(gate - 15, bottom - 6, 30, 6), GOLD.lerp(TEAL, raised))
		if not powered and _lantern_reveal(Vector2(gate, 255)) > 0.34:
			_center(gate, 217.0, "SHUTTER LOCKED", 15, GOLD)
	var ladder: float = roofs[-2].end.x - 48.0
	if powered and ladder > camera_x - 45 and ladder < camera_x + width + 45:
		var top: float = roofs[-2].position.y
		for offset in [-9, 9]:
			draw_line(Vector2(ladder + offset, top + 6), Vector2(ladder + offset, 360), Color("#7da5a0"), 3)
		for y in range(int(top) + 14, 359, 12):
			draw_line(Vector2(ladder - 9, y), Vector2(ladder + 9, y), TEAL, 2)
		if absf(pos.x - ladder) < 90.0 and pos.y > 320.0:
			_center(ladder, 346, "JUMP", 15, TEAL)

func _draw_course(width: float) -> void:
	_draw_warden_hatches()
	_draw_warden_warning()
	if level == 3:
		_draw_signal_car(width)
	elif level == 4:
		_draw_switchgear(width)
	elif level == 7:
		_draw_blackout_hardware(width)
	if level == 3 or (level == 8 and not branch.is_empty()):
		for enemy in enemies:
			if enemy.kind != 5 or enemy.hp <= 0 or enemy.x < camera_x - 140 or enemy.x > camera_x + width + 140:
				continue
			if powered and enemy.get("signal_light", false):
				continue
			var beam := _beam_x(enemy)
			var apex := Vector2(enemy.x, enemy.y - SEARCHLIGHT_HEIGHT)
			var tint := RED if enemy.wind > 0 else Color("#82ccff")
			_polygon([apex, Vector2(beam - SEARCHLIGHT_HALF_WIDTH, enemy.y), Vector2(beam + SEARCHLIGHT_HALF_WIDTH, enemy.y)], Color(tint, 0.27))
			draw_line(Vector2(beam - SEARCHLIGHT_HALF_WIDTH, enemy.y), Vector2(beam + SEARCHLIGHT_HALF_WIDTH, enemy.y), tint, 3, true)
			draw_circle(apex, 6, tint)
	if _needs_relay() and absf(relay.x - camera_x - width * 0.5) < width * 0.6:
		var at := relay - Vector2(0, 32)
		var tint := TEAL if powered else GOLD
		var title := ("OFF" if powered else "STRIKE BREAKER") if level == 4 else ("ONLINE" if powered else "STRIKE RELAY")
		draw_rect(Rect2(at - Vector2(19, 21), Vector2(38, 48)), Color("#263648"))
		draw_rect(Rect2(at - Vector2(10, 11), Vector2(20, 14)), tint)
		_center(at.x, at.y - 28, title, 16, tint)
	for pickup in pickups:
		var feet: Vector2 = pickup.pos
		if pickup.taken or feet.x < camera_x - 50 or feet.x > camera_x + width:
			continue
		var at := feet - Vector2(0, 24 + sin(time * 4.5 + feet.x) * 4.0)
		var tint: Color = [RED, TEAL, GOLD][pickup.kind]
		_halo(at, 25, tint)
		_polygon([at + Vector2(0, -14), at + Vector2(13, 0), at + Vector2(0, 14), at + Vector2(-13, 0)], tint)
		_center(at.x, at.y + 5, ["+", "S", "P"][pickup.kind], 15, NIGHT)
	for i in hazards.size():
		var at := hazards[i]
		var radius := _hazard_radius(i)
		if at.x + radius < camera_x - 50 or at.x - radius > camera_x + width:
			continue
		var live := _hazard_live(i)
		if level == 2:
			draw_rect(Rect2(at.x - 26, at.y - 8, 52, 8), Color("#526675"))
			if not live and _hazard_phase(i) > 1.10:
				draw_line(at + Vector2(-20, -13), at + Vector2(20, -13), Color(GOLD, 0.82), 2, true)
			for vent in [-16, 0, 16]:
				draw_circle(at + Vector2(vent, -5), 4, GOLD if _hazard_phase(i) > 1.1 else Color("#1b293b"))
			if live:
				for puff in range(3):
					var lift := 13.0 + 13.0 * puff
					draw_circle(at + Vector2(sin(time * 7 + puff) * 8, -lift), 9.0 + puff * 3.0, Color("#bad8df88"), true, -1, true)
			continue
		var phase := _hazard_phase(i)
		var warning := level == 4 and not powered and (phase < 0.65 if i in SURGE_BANKS else phase > 1.15)
		var tint := RED if live else (GOLD if warning or level != 4 else (TEAL.darkened(0.45) if powered else Color("#58416a")))
		draw_rect(Rect2(at.x - radius, at.y - 5, radius * 2, 5), tint)
		if level == 4:
			for offset in [-radius, radius]:
				var insulator := at + Vector2(offset, -10)
				draw_rect(Rect2(insulator - Vector2(5, 3), Vector2(10, 13)), Color("#53425a"))
				draw_circle(insulator, 4, tint, true, -1, true)
		if live:
			for arc in range(int(radius / 30.0)):
				var x := at.x - radius + 30.0 + arc * 60.0
				draw_polyline(PackedVector2Array([Vector2(x - 27, at.y - 4), Vector2(x - 14, at.y - 21),
					Vector2(x, at.y - 7), Vector2(x + 15, at.y - 25), Vector2(x + 27, at.y - 4)]), Color("#ca8bff") if level == 4 else RED, 4, true)
	for shot in shots:
		if shot.pos.x < camera_x - 16 or shot.pos.x > camera_x + width + 16:
			continue
		draw_circle(shot.pos, 7, RED)
		draw_circle(shot.pos, 3, GOLD)

func _draw_bridge(width: float) -> void:
	if bridge_x < camera_x - 100 or bridge_x > camera_x + width + 120:
		return
	draw_rect(Rect2(bridge_x - 47, 104, 94, 155), Color("#3f3543"))
	draw_rect(Rect2(bridge_x - 47, 248, 94, 15), Color("#825e47"))
	for stripe in 6:
		var x := bridge_x - 43 + stripe * 15
		draw_line(Vector2(x, 260), Vector2(x + 8, 250), GOLD, 3)
	for offset in [-83.0, -54.0, -25.0, 4.0, 33.0, 62.0]:
		var lamp := Vector2(bridge_x + offset, 361.0)
		draw_rect(Rect2(lamp - Vector2(6, 2), Vector2(12, 4)), TEAL)
		if pos.y >= 340.0:
			draw_polyline(PackedVector2Array([lamp + Vector2(-6, -17), lamp + Vector2(0, -12),
				lamp + Vector2(-6, -7)]), TEAL, 2, true)

func _draw_enemy(enemy: Dictionary, world: Transform2D) -> void:
	var base := Vector2(enemy.x, enemy.y)
	var scale := ACTOR_SCALE * (2.2 if enemy.kind == 4 else 1.0)
	var scale_about_feet := Transform2D(0.0, Vector2.ONE * scale, 0.0, base * (1.0 - scale))
	draw_set_transform_matrix(world * scale_about_feet)
	var lean := signf(pos.x - enemy.x) * (11.0 if enemy.wind > 0.0 else 4.0 * sin(time * 10 + enemy.home))
	var armor: Color = [Color("#518191"), Color("#4f8b83"), Color("#a67759"), Color("#93649e"), Color("#ce8156"), Color("#6b9cd8"), Color("#947753")][enemy.kind]
	if enemy.get("hit_until", 0.0) > time:
		armor = armor.lightened(0.65)
	if enemy.kind == 4:
		if enemy.phase == "arrival":
			_halo(base - Vector2(0, 32), 54, RED)
		draw_rect(Rect2(base.x - 27, base.y - 53, 54, 5), NIGHT)
		draw_rect(Rect2(base.x - 27, base.y - 53, 54.0 * enemy.hp / 12.0, 5), RED)
		_center(base.x, base.y - 57, "IRON WARDEN", 9, GOLD)
		if enemy.phase == "recovery":
			_halo(base + Vector2(0, -27), 34, GOLD)
		else:
			var shield_tint := RED if enemy.phase.ends_with("warning") or enemy.phase == "charge" else TEAL
			if enemy.get("block_until", 0.0) > time:
				shield_tint = Color("#d5fff8")
			draw_arc(base + Vector2(0, -22), 30, 0, TAU, 24, shield_tint, 2, true)
	elif enemy.kind == 2:
		draw_rect(Rect2(base.x - 25, base.y - 26, 50, 7), GOLD)
	elif enemy.kind == 3:
		_polygon([base + Vector2(-27, -14), base + Vector2(0, -49), base + Vector2(27, -14)], armor)
	draw_texture_rect(GUARD_ART, Rect2(base + Vector2(-27, -42), Vector2(54, 42)), false, armor.lightened(0.25))
	if enemy.kind == 4:
		draw_circle(base + Vector2(0, -21), 9, GOLD if enemy.phase == "recovery" else Color("#673639"), true, -1, true)
	draw_line(base + Vector2(lean - 10, -35), base + Vector2(lean + 11, -36), armor.lightened(0.3), 2, true)
	draw_rect(Rect2(base + Vector2(-13, -15), Vector2(26, 7)), armor.darkened(0.42))
	for rivet in [-9, 9]:
		draw_circle(base + Vector2(rivet, -12), 1.8, armor.lightened(0.4), true, -1, true)
	if enemy.kind == 6:
		var plate := base + Vector2(enemy.facing * 18.0, -22)
		var rim := GOLD if enemy.wind > 0.0 else (TEAL if enemy.cool > 0.0 else Color("#a4bdc2"))
		draw_rect(Rect2(plate - Vector2(7, 15), Vector2(14, 30)), Color("#2f4c57"))
		draw_line(plate - Vector2(7, 14), plate + Vector2(-7, 14), rim, 3)
	if enemy.kind == 5:
		var lamp := Color("#344756") if powered and enemy.get("signal_light", false) else Color("#82ccff")
		draw_circle(base + Vector2(lean + 9, -40), 5, lamp, true, -1, true)
	elif enemy.kind == 3:
		draw_line(base + Vector2(-17, -16), base + Vector2(-30, -10), armor.lightened(0.25), 3, true)
	draw_line(base + Vector2(lean - 9, -28), base + Vector2(lean + 10, -29), RED, 2)
	if enemy.hp > 1:
		draw_arc(base + Vector2(0, -23), 24, PI * 0.94, PI * 2.06, 16, TEAL, 2, true)
	if enemy.wind > 0.0:
		_halo(base + Vector2(lean, -35), 38, RED)
		draw_circle(base + Vector2(lean, -47), 3.5, RED)

func _draw_courier(world: Transform2D) -> void:
	if not started or (hurt > 0 and fmod(hurt * 15, 2) > 1):
		return
	var swing := tether >= 0
	var lantern := pos + Vector2(face * (12 if swing else 18), -39 if swing else -22)
	var squash := 1.0 - landing_pose * 1.18 + takeoff_pose * 0.72
	var tilt := face * 0.17 if swing else 0.0
	var pose := Transform2D(tilt, Vector2(ACTOR_SCALE / squash, ACTOR_SCALE * squash), 0.0, Vector2.ZERO)
	pose.origin = pos - pose * pos
	if swing:
		draw_set_transform_matrix(world)
		var grip := pose * lantern
		draw_line(grip, anchors[tether], Color(TEAL, 0.3), 7, true)
		draw_line(grip, anchors[tether], Color("#d2fff1"), 2, true)
	draw_set_transform_matrix(world * pose)
	_halo(lantern, 63, GOLD)
	var step := sin(time * 27) * minf(absf(velocity.x) / 20, 9) if grounded else 0.0
	var rear_boot := Vector2(step - 4, 0) if grounded else Vector2(-12 * face, -5)
	var front_boot := Vector2(4 - step, -1) if grounded else Vector2(10 * face, -8 if velocity.y < 0 else -3)
	_draw_courier_art(world * pose, pos, face, lantern, swing, step, rear_boot, front_boot, true)
	if strike > 0.0:
		var angle := 0.0 if face > 0 else PI
		draw_arc(pos - Vector2(0, 25), 52, angle - 0.9, angle + 0.65, 22, Color(GOLD, strike * 3.3), 4, true)

func _draw_courier_art(transform: Transform2D, feet: Vector2, heading: float, lantern: Vector2,
		swing: bool, step: float, rear_boot: Vector2, front_boot: Vector2, carrying: bool) -> void:
	draw_set_transform_matrix(transform)
	draw_line(feet + Vector2(0, -13), feet + rear_boot, Color("#3a8392"), 5, true)
	draw_line(feet + Vector2(0, -13), feet + front_boot, Color("#6f9b9f"), 4, true)
	var coat_bounds := Rect2(feet + Vector2(-26, -34), Vector2(52, 25))
	if heading < 0:
		var mirror := Transform2D(Vector2(-1, 0), Vector2(0, 1), Vector2(feet.x * 2, 0))
		draw_set_transform_matrix(transform * mirror)
	draw_texture_rect(COURIER_COAT, coat_bounds, false)
	draw_set_transform_matrix(transform)
	var scarf_end := feet + Vector2(-(37 if swing else 26) * heading, -28 + step * 0.36 - (12 if swing else 0))
	draw_polyline(PackedVector2Array([feet + Vector2(-5 * heading, -31),
		feet + Vector2(-16 * heading, -29), scarf_end]), RED, 4, true)
	draw_circle(feet + Vector2(1, -36), 8, Color("#e6c098"), true, -1, true)
	draw_line(feet + Vector2(-10, -40), feet + Vector2(9, -39), Color("#245b69"), 5, true)
	draw_line(feet + Vector2(3 * heading, -29), lantern, Color("#e6c098"), 4, true)
	draw_line(feet + Vector2(-4 * heading, -30), feet + Vector2(6 * heading, -16), Color("#733b35"), 2, true)
	draw_circle(feet + Vector2(4 * heading, -24), 1.6, GOLD, true, -1, true)
	draw_circle(feet + Vector2(6 * heading, -19), 1.4, GOLD, true, -1, true)
	draw_rect(Rect2(feet + Vector2(-6, -47), Vector2(12, 8)), Color("#315c66"))
	draw_line(feet + Vector2(-5, -43), feet + Vector2(6, -43), Color("#619092"), 1.6, true)
	draw_circle(feet + Vector2(4 * heading, -35), 1.25, Color("#272232"), true, -1, true)
	if carrying:
		_draw_lantern(lantern)

func _draw_lantern(lantern: Vector2) -> void:
	draw_rect(Rect2(lantern - Vector2(4, 5), Vector2(8, 11)), GOLD)
	draw_line(lantern + Vector2(-3, -4), lantern + Vector2(3, -4), Color("#aa6c31"), 2, true)
	draw_line(lantern + Vector2(-3, 5), lantern + Vector2(3, 5), Color("#aa6c31"), 2, true)
	draw_circle(lantern, 2, Color("#fff7c9"))

func _draw_pickup_feedback(courier: Vector2, width: float) -> void:
	if pickup_feedback_left <= 0.0 or finished or defeated:
		return
	const SIZE := 21
	var half_width := font.get_string_size(pickup_feedback, HORIZONTAL_ALIGNMENT_LEFT, -1, SIZE).x * 0.5 + 11.0
	var x := clampf(courier.x, half_width + 12, width - half_width - 12)
	var y := clampf(courier.y - 69, 151, 416)
	var opacity := minf(1.0, pickup_feedback_left * 5.0)
	draw_rect(Rect2(x - half_width, y - 22, half_width * 2, 29), Color(NIGHT, opacity * 0.94))
	_center(x, y, pickup_feedback, SIZE, Color(GOLD, opacity))

func _draw_waiting_keeper() -> void:
	var keeper := Vector2(90, 365)
	draw_rect(Rect2(keeper + Vector2(-13, -30), Vector2(26, 29)), Color("#4e6977"))
	draw_circle(keeper - Vector2(0, 35), 9, Color("#b8a18b"))
	draw_line(keeper - Vector2(10, 43), keeper + Vector2(10, -43), Color("#354855"), 4)
	var empty_lamp := keeper + Vector2(-22, -24)
	draw_rect(Rect2(empty_lamp - Vector2(5, 7), Vector2(10, 14)), Color("#48483e"))
	draw_line(empty_lamp - Vector2(7, 8), empty_lamp + Vector2(7, -8), Color("#8a745b"), 2)

func _show_delivery() -> bool:
	return finished and level == LAST_LEVEL and (not delivered or victory_time < DELIVERY_SECONDS)

func _draw_intro(width: float, layout: Transform2D) -> void:
	var center := width * 0.5
	var handoff := intro_time >= INTRO_HANDOFF
	var reduced: bool = session.chrome.reduce_motion
	var signal_lamp := Vector2(center + 132.0, 302.0)
	_draw_station(width, center, signal_lamp.x)
	_draw_station_train(center)
	var train_light := Vector2(center - 91.0, 320.0)
	var train_glow := 0.35 if reduced else smoothstep(1.0, 3.5, intro_time)
	draw_circle(train_light, 33.0, Color("#ec8668", train_glow * 0.10))
	draw_circle(train_light, 5.0, Color("#ff9c75", train_glow * 0.8), true, -1.0, true)
	draw_rect(Rect2(signal_lamp.x - 6, 313, 12, 50), Color("#465e63"))
	draw_rect(Rect2(signal_lamp.x - 15, 309, 30, 6), Color("#8a705a"))
	draw_rect(Rect2(signal_lamp - Vector2(7, 13), Vector2(14, 20)), Color("#252c34"))
	draw_arc(signal_lamp - Vector2(0, 10), 6, PI, TAU, 12, Color("#788587"), 2, true)
	if handoff:
		var feet := Vector2(center + 6.0, 363.0)
		var keeper := feet + Vector2(61, 0)
		draw_line(keeper + Vector2(-5, -18), keeper + Vector2(-7, -1), Color("#53636c"), 6, true)
		draw_line(keeper + Vector2(6, -18), keeper + Vector2(8, -1), Color("#53636c"), 6, true)
		_polygon([keeper + Vector2(-12, -37), keeper + Vector2(10, -37), keeper + Vector2(15, -13),
			keeper + Vector2(-16, -13)], Color("#627c7e"))
		draw_circle(keeper + Vector2(0, -42), 8, Color("#b8a18b"), true, -1.0, true)
		draw_line(keeper + Vector2(-10, -48), keeper + Vector2(10, -48), Color("#354855"), 4, true)
		var hand := keeper + Vector2(-22, -27)
		draw_line(keeper + Vector2(-7, -32), hand, Color("#b8a18b"), 4, true)
		var progress := 1.0 if reduced else smoothstep(0.65, 3.2, intro_time - INTRO_HANDOFF)
		var lantern := hand.lerp(feet + Vector2(19, -25), progress)
		_halo(lantern, 66, GOLD)
		draw_circle(lantern, 29, Color(GOLD, 0.15))
		if not reduced:
			for spark in 9:
				var rise := fposmod((intro_time - INTRO_HANDOFF) * (19.0 + spark * 1.8) + spark * 11.0, 66.0)
				var point := lantern + Vector2(sin(rise * 0.09 + spark) * (7.0 + rise * 0.24), -rise)
				draw_circle(point, 1.0 + float(spark % 3) * 0.45, Color(GOLD, (1.0 - rise / 66.0) * 0.72))
		_draw_courier_art(layout, feet, 1.0, feet + Vector2(19, -25), false, 0.0,
			Vector2(-5, 0), Vector2(5, -1), false)
		_draw_lantern(lantern)
	_center(center, 88, "LAST LIGHT EXPRESS", 37, GOLD)
	if handoff:
		_center(center, 423, "Take this lantern aboard the last train.", 27, GOLD)
		_center(center, 453, "Reach the last station and restore its light.", 22, Color("#e7e0d4"))
	else:
		_center(center, 423, "The last station has gone dark.", 28, GOLD)
		_center(center, 453, "The night train is its last chance.", 22, Color("#e7e0d4"))
	if not reduced:
		var fade := 1.0 - smoothstep(0.0, 1.25, intro_time)
		var transition := 1.0 - smoothstep(0.0, 0.65, absf(intro_time - INTRO_HANDOFF))
		draw_rect(Rect2(0, 0, width, 540), Color(NIGHT, maxf(fade, transition * 0.92)))
	if intro_time >= INTRO_SECONDS:
		var play := _intro_play_bounds(width)
		draw_style_box(control_styles["jump"][1], play)
		_center(center, play.position.y + 36.0, "PLAY", 25, Color.WHITE)

func _draw_delivery(width: float, layout: Transform2D) -> void:
	var center := width * 0.5
	var lamp := Vector2(center + DELIVERY_PEDESTAL + 7.0, 302.0)
	_draw_station(width, center, lamp.x)
	_draw_station_train(center)
	var feet := Vector2(center + delivery_x, 363.0)
	var hand := feet + Vector2(face * 19.0, -25.0)
	var scale := 1.31
	var pose := Transform2D(0.0, Vector2.ONE * scale, 0.0, feet * (1.0 - scale))
	_halo(lamp if delivered else pose * hand, 68, GOLD)
	if delivered:
		var glow := _station_light(lamp.x, lamp.x)
		draw_circle(lamp, 87, Color(GOLD, glow * 0.09))
		draw_circle(lamp, 48, Color(GOLD, glow * 0.15))
	draw_rect(Rect2(lamp.x - 6, 318, 12, 43), Color("#465e63"))
	draw_line(Vector2(lamp.x - 4, 322), Vector2(lamp.x - 4, 354), Color("#90a19a"), 2)
	draw_rect(Rect2(lamp.x - 17, 312, 34, 7), Color("#a5805f"))
	draw_rect(Rect2(lamp.x - 13, 357, 26, 6), Color("#405058"))
	var walk := sin(delivery_x * 0.17) * 6.0 if _direction() != 0.0 and not delivered else 0.0
	draw_line(feet + Vector2(-21, 2), feet + Vector2(21, 2), Color("#101822a8"), 4, true)
	_draw_courier_art(layout * pose, feet, face, hand if not delivered else feet + Vector2(11 * face, -18),
		false, walk, Vector2(walk - 4, 0), Vector2(4 - walk, -1), not delivered)
	draw_set_transform_matrix(layout)
	_center(center, 88, "THE LAST STATION", 36, GOLD)
	if delivered:
		_draw_lantern(lamp)
		_draw_station_restoration(center, lamp)
	else:
		var instruction := "Place the lantern." if delivery_x >= DELIVERY_PEDESTAL - 50.0 else "Walk to the station lamp."
		_center(center, 435, instruction, 26, Color("#e7e0d4"))
		_draw_controls()

func _station_light(x: float, origin: float) -> float:
	if not delivered:
		return 0.0
	if session.chrome.reduce_motion:
		return 1.0
	var delay := absf(x - origin) / 490.0
	return smoothstep(0.08, 0.58, victory_time - delay)

func _draw_station_restoration(center: float, lamp: Vector2) -> void:
	var reduced: bool = session.chrome.reduce_motion
	if not reduced:
		var age := victory_time
		var wave := clampf(age / 1.4, 0.0, 1.0)
		if wave < 1.0:
			var radius := lerpf(11.0, 92.0, wave)
			draw_arc(lamp, radius, PI * 1.08, PI * 1.92, 28, Color(GOLD, 0.55 * (1.0 - wave)), 2, true)
		for spark in 22:
			var life := age - float(spark % 7) * 0.075
			if life < 0.0 or life > 1.8:
				continue
			var side := -1.0 if spark % 2 == 0 else 1.0
			var point := lamp + Vector2(side * (18.0 + float(spark % 9) * 6.0) * life,
				-(34.0 + float(spark % 11) * 7.0) * life + 13.0 * life * life)
			draw_circle(point, 1.2 + float(spark % 3) * 0.6, Color(GOLD, (1.0 - life / 1.8) * 0.82), true, -1.0, true)
	var reveal := 1.0 if reduced else smoothstep(0.75, 1.65, victory_time)
	var subtitle := 1.0 if reduced else smoothstep(1.35, 2.2, victory_time)
	if reveal > 0.0:
		_center(center, 443 + (1.0 - reveal) * 17.0, "LIGHT RESTORED", 41, Color(GOLD, reveal))
	if subtitle > 0.0:
		_center(center, 480, "The last station shines again.", 23, Color("#eee2cb", subtitle))

func _draw_station(width: float, center: float, origin: float) -> void:
	draw_rect(Rect2(0, 0, width, 540), Color("#101923"))
	draw_rect(Rect2(0, 114, width, 256), Color("#25333f"))
	for bay in range(-ceili(width / 360.0), ceili(width / 360.0) + 1):
		var x := center + 180.0 * bay
		var window := Vector2(x + 58, 203)
		var light := _station_light(window.x, origin)
		var glass := Color("#162c40").lerp(Color("#97765e"), light * 0.7)
		draw_circle(window, 42, Color("#56616a"))
		draw_rect(Rect2(window.x - 42, window.y, 84, 79), Color("#56616a"))
		draw_circle(window, 37, glass)
		draw_rect(Rect2(window.x - 37, window.y, 74, 73), glass)
		draw_line(window - Vector2(0, 36), window + Vector2(0, 73), Color("#607480"), 3)
		draw_line(window - Vector2(37, -18), window + Vector2(37, 18), Color("#607480"), 3)
		draw_line(Vector2(x - 13, 122), Vector2(x - 13, 363), Color("#101c29"), 13)
		draw_line(Vector2(x - 16, 122), Vector2(x - 16, 363), Color("#53626b"), 2)
		var fixture := Vector2(x + 58, 142)
		draw_rect(Rect2(fixture - Vector2(19, 4), Vector2(38, 8)), Color("#786454").lerp(GOLD, light))
		if light > 0.0:
			draw_circle(fixture, 49, Color(GOLD, light * 0.10))
			draw_polygon(PackedVector2Array([fixture + Vector2(-19, 4), fixture + Vector2(19, 4),
				fixture + Vector2(65, 209), fixture + Vector2(-65, 209)]),
				PackedColorArray([Color(GOLD, light * 0.14), Color(GOLD, light * 0.14), Color(GOLD, 0), Color(GOLD, 0)]))
	draw_line(Vector2(0, 119), Vector2(width, 119), Color("#8d6450"), 6)
	draw_line(Vector2(0, 129), Vector2(width, 129), Color("#52616a"), 2)
	draw_rect(Rect2(0, 362, width, 32), Color("#48535b"))
	draw_rect(Rect2(0, 369, width, 25), Color("#283742"))
	draw_line(Vector2(0, 364), Vector2(width, 364), Color("#b19d80"), 2)
	for tile in range(ceili(width / 48.0)):
		var x := tile * 48.0
		draw_line(Vector2(x, 375), Vector2(x - 17, 393), Color("#46555e"), 2)
	draw_line(Vector2(0, 394), Vector2(width, 394), Color("#111b25"), 4)

func _draw_station_train(center: float) -> void:
	var x := center - 449.0
	var body := Rect2(x, 235, 370, 106)
	draw_texture_rect(SLEEPER_ART, body, false)
	draw_line(Vector2(x + 13, 243), Vector2(x + 358, 243), Color("#739499"), 2)
	for offset in [38.0, 82.0, 286.0, 330.0]:
		var wheel := Vector2(x + offset, 343)
		draw_circle(wheel, 16, NIGHT, true, -1.0, true)
		draw_arc(wheel, 11, 0, TAU, 20, Color("#566f77"), 2, true)
		draw_line(wheel - Vector2(0, 8), wheel + Vector2(0, 8), Color("#799096"), 2)
		draw_circle(wheel, 3, Color("#88949a"))
	draw_circle(Vector2(x + 358, 320), 4, Color("#c65f51"), true, -1.0, true)

func _draw_controls() -> void:
	var held := touches.values()
	var jump_ready := _jump_ready_ring() >= 0
	for button in _buttons():
		var center: Vector2 = button[0]
		var active: bool = button[1] in held
		if button[3] == ARROW_SIZE:
			var radius := ARROW_SIZE.x * 0.5 - 2.0
			draw_circle(center, radius, Color("#3a6270ed") if active else Color("#102634f0"), true, -1.0, true)
			draw_arc(center, radius, 0, TAU, 64, TEAL if active else Color("#accbd2"), 2.0, true)
			var direction := -1.0 if button[1] == "left" else 1.0
			var tip := center + Vector2(direction * 10, 0)
			var upper := tip + Vector2(-direction * 20, -15)
			var lower := tip + Vector2(-direction * 20, 15)
			draw_line(upper, tip, Color.WHITE, 5, true)
			draw_line(tip, lower, Color.WHITE, 5, true)
			for point in [upper, tip, lower]:
				draw_circle(point, 2.5, Color.WHITE, true, -1.0, true)
		else:
			var action: String = button[1]
			var style: StyleBoxFlat = control_styles[action][int(active)] if control_styles.has(action) else action_styles[2 if active else 0]
			draw_style_box(style, _button_bounds(button))
			var ink := Color("#b4ffc8") if jump_ready and action == "jump" else Color.WHITE
			_center(center.x, center.y + 7, button[2], 25, ink)

func _draw_hud(width: float) -> void:
	draw_rect(Rect2(15, 10, 256, 57), Color("#071322f2"))
	draw_rect(Rect2(width - 146, 10, 130, 46), Color("#071322f2"))
	_label(Vector2(27, 38), "LEVEL %d / %d" % [level, LAST_LEVEL], 29, GOLD)
	_label(Vector2(29, 60), LEVEL_NAMES[level - 1], 20, Color("#d2e7eb"))
	if shield or charged:
		_label(Vector2(width - 145, 61), "SHIELD" if shield else "CHARGED", 16, TEAL)
	for i in 4:
		draw_circle(Vector2(width - 35 - (3 - i) * 22, 33), 6, GOLD if i < health else Color("#334b5b"))
	draw_rect(Rect2(25, 68, width - 50, 2), Color("#254252"))
	draw_rect(Rect2(25, 68, (width - 50) * clampf((pos.x - 135) / (goal_x - 135.0), 0, 1), 2), TEAL)
	draw_rect(Rect2(width * 0.5 - 143, 10, 286, 38), Color("#071322f2"))
	_center(width * 0.5, 36, "Turn upright for menu", 23, Color("#e7f2f4"))
	_draw_controls()
	if finished or defeated:
		draw_rect(Rect2(width * 0.5 - 248, 151, 496, 201), Color("#091827ef"))
		if finished and level == LAST_LEVEL:
			var pulse := 0.0 if session.chrome.reduce_motion else sin(victory_time * 5.0)
			for i in 12:
				var angle := i * TAU / 12.0 + victory_time * (0.0 if session.chrome.reduce_motion else 0.65)
				var spark := Vector2(width * 0.5, 198) + Vector2(cos(angle) * 200, sin(angle) * 32)
				draw_circle(spark, 3.0 + pulse, GOLD if i % 2 == 0 else TEAL)
			_center(width * 0.5, 218, "YOU WIN!", roundi(65.0 + pulse * 4.0), GOLD)
			_center(width * 0.5, 248, "All ten levels complete.", 21, TEAL)
		elif defeated:
			_center(width * 0.5, 199, "OUT OF LIGHT", 36, RED)
			_center(width * 0.5, 239, "Resume at the checkpoint or restart the level." if _can_retry_checkpoint() else "Try this level again.", 22, TEAL)
		else:
			_center(width * 0.5, 195, "LEVEL COMPLETE", 35, GOLD)
			_center(width * 0.5, 238, FEATURE_RESULTS.get(level, "Crew rescued. Floodline clear." if rescue else "Relay repaired. Depot clear."), 21, TEAL)
	elif _near_signal():
		draw_rect(Rect2(width * 0.5 - 325, 89, 650, 112), Color("#091827ed"))
		_center(width * 0.5, 121, "THROW THE SWITCH", 28, GOLD)
		_center(width * 0.5, 153, "JUMP  •  DEPOT     /     STRIKE  •  FLOODLINE", 22)
		_center(width * 0.5, 179, "Repair the relay below deck     /     Rescue the crew on the roofs", 17, Color("#bfdfdf"))
	else:
		var hint := _route_hint()
		if not hint.is_empty():
			var font_size := 27
			var text_width := font.get_string_size(hint, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
			if text_width > width - 64:
				font_size = floori(font_size * (width - 64) / text_width)
				text_width = font.get_string_size(hint, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size).x
			draw_rect(Rect2((width - text_width) * 0.5 - 15, 78, text_width + 30, 39), Color("#091827f2"))
			_center(width * 0.5, 106, hint, font_size, GOLD)

func _route_hint() -> String:
	if not started:
		return ""
	if level == 1 and not practice_swing_done and grounded and pos.x > roofs[0].end.x - 148.0 and message_left <= 0.0:
		return "PRACTICE SWING  /  HOLD RIGHT AND JUMP TOWARD THE RING"
	if level == 1 and pos.x >= bridge_x - 270 and pos.x < bridge_x + 47:
		return "LOW BRIDGE  /  DROP BELOW THE ROOF" if pos.y < 340 else "LOW BRIDGE  /  STAY ON THE LOWER DECK"
	if level == 6 and storm_swing == 1 and pos.x < roofs[-1].position.x and _wind_phase() < 1.95:
		return "WIND CLEAR  /  HOLD RIGHT AND JUMP TO THE LANTERN RING"
	if level == 6 and storm_zone == 1 and _wind_phase() >= 1.95:
		if _in_storm_windbreak():
			return "WIND RISING  /  WAIT IN SHELTER" if _wind_phase() < 2.65 else "WIND GUST  /  WAIT FOR THE GREEN FLAG"
		if _wind_phase() < 2.65:
			return "WIND RISING  /  FIND SHELTER OR DROP"
		return "WIND GUST  /  SHELTERED BELOW DECK" if pos.y >= 320 else "WIND GUST  /  DROP FOR SHELTER"
	if level == 6 and storm_zone == 1 and pos.x > roofs[-2].end.x - 165 and pos.x < roofs[-1].position.x:
		return "LAST SPAN  /  WAIT FOR CALM, THEN JUMP TOWARD THE RING"
	if level == 2 and pos.y < 340 and not hazards.is_empty() and pos.x > hazards[0].x - 370 and pos.x < hazards[0].x - 48:
		return "AMBER VENTS  /  WAIT FOR THE STEAM TO CLEAR"
	if _depot_ladder_near():
		return "LADDER  /  JUMP TO THE ROOF"
	if _has_depot_carriage() and not _inside_depot_carriage():
		var hatch_x: float = roofs[DEPOT_CARRIAGE].position.x + 48.0
		if absf(pos.x - hatch_x) < 92.0 and pos.y < 320:
			return "DEPOT HATCH  /  DROP FOR COVER OR STAY ON THE ROOF"
	if branch == "Floodline" and not rescue and pos.x >= _floodline_start() - 110.0:
		if _floodline_surface() <= 365.0:
			return "FLOOD SURGE  /  STAY ABOVE THE WATER"
		if _floodline_phase() >= 0.95 and _floodline_phase() < 2.35:
			return "WATER RISING  /  HEAD FOR THE ROOF"
		return "RESCUE BEACON  /  REACH THE CREW ON THE ROOF"
	if level == 7 and not powered and absf(relay.x - pos.x) < 145.0:
		return "RELAY BELOW  /  DROP, THEN STRIKE" if pos.y < 320 else "STRIKE THE RELAY TO RESTORE POWER"
	if message_left > 0:
		return message
	if grounded and _jump_ready_ring() >= 0:
		return "HOLD TOWARD THE RING + JUMP"
	return str(FEATURE_HINTS[level]) if pos.x < 375 else ""
