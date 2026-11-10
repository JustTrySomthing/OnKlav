extends Control

const Expedition = preload("res://expedition.gd")
const Touch = preload("res://touch_controls.gd")
const PropArt = preload("res://prop_art.gd")
const AtmosphereArt = preload("res://atmosphere_art.gd")
const MAX_STAGE = 5
const ARENA = 13.5
const TEAL = Color("#71f9d0")
const GOLD = Color("#ffca76")
const PINK = Color("#ff6488")
const TYPE_COLORS = [Color("#ff526a"), Color("#a280ff"), Color("#ffad43"), Color("#f252c6"),Color("#8cf0b0"),Color("#d3ba56"),Color("#dfa363"),Color("#c5a7f1")]
const TYPE_HP = [38, 54, 115, 510, 23, 68, 44, 76]
const TYPE_SPEED = [2.55, 1.25, 1.55, 1.95, 3.25, 1.85, 3.65, 1.43]
const PURSUIT_MEMORY = 1.6
const MELEE_STANDOFF = 1.45
const SPITTER_STANDOFF = 4.6
const STAGES = ["THE BREACH", "SPOREWORKS", "THE CULTURE", "ROOT FURNACE", "THE CAP KING"]
const START = Vector3(0,0,9)
const FX_LIMIT = 32
const BLAST_LIMIT = 6
# Death feedback is capped at four simple two-mesh remnants, without physics or lights.
const DEATH_MARK_LIMIT = 4
const DEATH_MARK_TIME = .42
const IMPACT_COLOR = Color("#d6dcf0")
const ORB_LIMIT = 16
const ROOM_LIGHT_LIMIT = 6
const HAZARD_LIMIT = 4
const HAZARD_HIT_INTERVAL = 1.35
const ROOM_DETAIL_LIMIT = 9 # sparse per-room mesh dressing for mobile
const SPORE_CANISTER_LIMIT = 3
const SPORE_CANISTER_RADIUS = 3.4
const BOSS_VOLLEY_MAX = 3
const BOSS_VOLLEY_WINDUP = .90
const BOSS_EXPOSE_TIME := 1.45
const BOSS_VOLLEY_COOLDOWN = [5.4,4.6,3.9]
const BOSS_VOLLEY_SPEED = [5.6,6.2,6.8]
# Hit-stagger is brief and capped. Crowd steering is O(13^2) at the existing
# actor limit and uses no physics bodies, raycasts, timers or extra visuals.
const HIT_STAGGER_TIME = .24
# Directional hit feedback is HUD-only: no new lights, meshes or world effects.
const DAMAGE_CUE_TIME = .68
# Spitter attacks must provide a full visual warning before the projectile exists.
const SPITTER_CHARGE_TIME = .52
# Deterministic, room-triggered reinforcements: two potential encounters per
# sector, one extra actor per encounter, with a warning and existing actor cap.
const AMBUSH_DELAY = 1.35
const AMBUSH_MAX_PER_SECTOR = 2
# Exploration awards are granted once on first visits to four and seven rooms.
# Nine-bit masks are cheap to update and persist within the existing save file.
const EXPLORATION_FIRST_BONUS = 100
const EXPLORATION_DEEP_BONUS = 200
enum Breach { INACTIVE, SEALED, WARNING, OPEN, EXTRACTION, CLEAR }
const BREACH_WARNING_TIME := 1.65
var breach_state := Breach.INACTIVE
var breach_clock := 0.0
var breach_gate: StaticBody3D
var breach_bars: Node3D

const SPOREWORKS_TRANSFER_ROOM := 1
const SPOREWORKS_PRESSURE_ROOM := 3
const SPOREWORKS_POD_ROOM := 8
const SPOREWORKS_CONVEYOR_OFFSET := Vector3(2.86,0,0)
const SPOREWORKS_TENDRIL := 1
const SPOREWORKS_POD := 2
const SPOREWORKS_CYCLE := 5.0
const SPOREWORKS_STEAM := Vector3(0,0,-2.1)
var sporeworks_cleared := 0
var sporeworks_roots: Node3D
var sporeworks_warning: MeshInstance3D
var practice_complete := false
var culture_sealed := 0
var chamber_lanes: Array[Dictionary] = []
var sporeworks_cargo: Array[Node3D] = []
var sporeworks_belt: Node3D
var sporeworks_drive: AudioStreamPlayer3D
var sporeworks_valve: AudioStreamPlayer3D
var sporeworks_vent: ShaderMaterial
var sporeworks_pressure_live := false
var sporeworks_steam: Node3D
var sporeworks_steam_material: ShaderMaterial
var sporeworks_steam_signal: MeshInstance3D
var sporeworks_steam_voice: AudioStreamPlayer3D
var sporeworks_steam_live := false
var sporeworks_route: Node3D
var sporeworks_status: Label3D
var sporeworks_restart := 0.0

const INCIDENT_BRIEF := "Sergeant Reyes, containment officer. The crew is safe at Surface Shelter One. Our fungi ate toxic waste until they escaped into the root pumps. Stop the pumps and defeat the Cap King before the bloom reaches the surface."
const SECTOR_BRIEFINGS = [
	"Approach the observation shutter and recover the missing samples.",
	"Cross the pressure line and reset the ventilation relays. Follow the green lights to extraction.",
	"Cross the waste catwalk and recover the cultures from the exposure lab.",
	"The root pumps are still feeding the bloom. Shut them down at the regulator cores.",
	"Find the Cap King at the heart of the bloom. Break his crown and reach extraction."
]
const ENEMY_LIMIT = 13
const CROWD_SEPARATION_RADIUS = 1.22
const CROWD_SEPARATION_WEIGHT = .52
# One non-colliding focal landmark per expedition room; all geometry is static.
# Avoid new physics bodies, dynamic lights, textures and per-frame effects.

var expedition := Expedition.new()
var expedition_mode := true
var objective_mask := 0
var objective_nodes: Array[Node3D] = []
var exit_marker: Node3D
var portal_field: MeshInstance3D
var portal_rim: MeshInstance3D
var portal_status: Label3D
const PORTAL_WAKE_DISTANCE := 3.55
var portal_orbit: Node3D
var portal_surface: ShaderMaterial
var portal_light: OmniLight3D
var arena: Node3D
var room: Node3D
var actors: Node3D
var player: CharacterBody3D
var camera: Camera3D
var view_weapon: Node3D
var view_guns: Array[Node3D] = []
var muzzle_light: OmniLight3D
var muzzle_flash: MeshInstance3D
var mortar_rounds: Array[Dictionary] = []
const MORTAR_SPEED := 24.0
const MORTAR_LIFE := .95
const SHOT_TINTS = [Color("#ffd079"),Color("#cb65ff"),Color("#94edff"),Color("#e9a3ff")]
const BUCKSHOT = [Vector2.ZERO,Vector2(-.088,0),Vector2(.088,0),Vector2(-.046,-.053),
	Vector2(.046,-.053),Vector2(-.046,.053),Vector2(.046,.053)]
var muzzle_time := 0.0
var weapon_action: Array[Node3D] = []
var weapon_readouts: Array[Label3D] = []
var controls
var hud: Control
var world: SubViewport
var world_environment: Environment
var title_panel: PanelContainer
var prefs: Button
var result_box: VBoxContainer
var result_title: Label
var result_objective: Label
var result_info: Label
var music: AudioStreamPlayer
var web_score
var web_score_state: Array = []
var web_score_supplied := false
var web_effects_supplied: Dictionary = {}
const SCORE_PATH = "res://assets/below_the_mycelium.ogg"
var sounds: Dictionary
var voices: Array[AudioStreamPlayer] = []
var voice_index := 0
var art_cache: Dictionary = {}
var prop_art := PropArt.new()
var atmosphere_art := AtmosphereArt.new()
# Static decorative instances are grouped by cached material + mesh shape.
var decor_batches: Dictionary = {}
var enemies: Array[CharacterBody3D] = []
var orbs: Array[Dictionary] = []
var motes: Array[Dictionary] = []
var effect_root: Node3D
var free_motes: Array[MeshInstance3D] = []
var free_blasts: Array[Node3D] = []
var block_shapes: Dictionary[Vector3,BoxShape3D] = {}
var box_meshes: Dictionary[Vector3,BoxMesh] = {}
var death_marks: Array[Dictionary] = []
var covers: Array[Vector3] = []
var pickups: Array[Node3D] = []
var hazards: Array[Dictionary] = []
var room_lights: Array[Dictionary] = []
var canisters: Array[StaticBody3D] = []
var secret_supply: Node3D
var hazard_clock := 0.0
var atmosphere_dirty := false
var sector_steps: Array[Callable] = []
var sector_focal_variant := 0
var sector_checkpoint: Dictionary = {}
var sector_view_mode: SubViewport.UpdateMode
var sector_frame_ready := false
var encounter_prepared := false
var encounter_rng_state := 0
var ambush_rooms: Array[int] = []
var ambush_used_mask := 0
var explored_rooms_mask := 1 << Expedition.ENTRY
var ambush_pending_room := -1
var ambush_timer := 0.0
var previous_player_room := Expedition.ENTRY
var hazard_next_hit := 0.0
var rng := RandomNumberGenerator.new()
var seed_code := 1
var stage := 1
var wave := 1
var pending := 0
var spawn_clock := 0.0
var health := 100
var weapon := 0
var score := 0
var streak := 0
var streak_timer := 0.0
var kills := 0
var highest := 0
var stored_best := 0
var best_loaded := false
var loading_best := false
var retry_best_read := false
var saving_best := false
var retry_best_at := 0
var active_time := 0.0
var damage_time := 0.0
var damage_cue_left := 0.0
var damage_cue_angle := 0.0
var dash_clock := 0.0
const DASH_TIME = .19
const DASH_SPEED = 16.0
var dash_left := 0.0
var dash_direction := Vector3.ZERO
var fire_clock := 0.0
var recoil := 0.0
var hit_mark := 0.0
var hit_defeated := false
var pitch := 0.0
var phase := "combat"
var banner := ""
var banner_time := 0.0
const RAZOR_MITE := 6
const LANTERN_WRAITH := 7
const CAPACITY = [18,5,7,3]
const WEAPON_NAMES = ["Nailgun","Sporebreaker","Arc Lance","Grav Mortar"]
const FIRE_DELAY = [.19,.66,.48,.95]
const INTRO_TIME := 12.75
var intro_elapsed := INTRO_TIME
var weapon_unlocks := 3
var weapon_pickup: Node3D
var nail_pickup: Node3D
var nail_charges := 0
var nail_pickups_collected := 0
var blast_fx: Array[Dictionary] = []
var access_route := false
var access_state := 0 # 0: card on floor, 1: card held, 2: door open
var access_card: Node3D
var access_gate: StaticBody3D
var waste_surface: ShaderMaterial
const WASTE_BASIN := Rect2(-4.72,5.0,9.44,5.5)
const WASTE_DECK = [
	Rect2(1.12,8.50,2.10,2.04),
	Rect2(-1.02,6.55,4.24,1.95),
	Rect2(-1.02,4.65,2.04,1.90),
]
const RELOAD_TIME = [1.12,1.38,1.54,1.86]
const PORTAL_TIME = 1.65
const VICTORY_REVEAL := 1.4
var victory_elapsed := VICTORY_REVEAL
const GOO = Color("#8dff31")
const STAIN_LIMIT = 20

var rounds := CAPACITY.duplicate()
var reload_left := 0.0
var portal_elapsed := 0.0
var stains: Array[Node3D] = []
var fire_tap := false
var move_bob := 0.0
var power := 0
var speed_bonus := 0

func web_render_shrink(view_size: Vector2, hosted := false) -> int:
	# Leave the HUD full size. Android WebView gets a smaller 3D pixel budget.
	var limit := Vector2(640,384) if hosted else Vector2(720,420)
	return maxi(2,ceili(maxf(view_size.x/limit.x,view_size.y/limit.y)))

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var view := SubViewportContainer.new()
	view.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	view.stretch = true
	view.stretch_shrink = 2
	view.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	view.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(view)
	world = SubViewport.new()
	world.own_world_3d = true
	world.size = Vector2i(600,340)
	# Smooth silhouettes inside the reduced-resolution 3D viewport.
	world.msaa_3d = Viewport.MSAA_2X
	view.add_child(world)
	if OS.has_feature("web"):
		var hosted := bool(JavaScriptBridge.eval("window.onklavHosted === true"))
		var resize_world := func():
			view.stretch_shrink = web_render_shrink(view.size,hosted)
		view.resized.connect(resize_world)
		resize_world.call()
	arena = Node3D.new()
	world.add_child(arena)
	prepare_effect_pool()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color("#0b0818")
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color("#91a8d4")
	env.ambient_light_energy = .23
	env.fog_enabled = true
	env.fog_light_color = Color("#201332")
	env.fog_density = .021
	world_environment = env
	var atmosphere := WorldEnvironment.new()
	atmosphere.environment = env
	arena.add_child(atmosphere)
	var light := DirectionalLight3D.new()
	light.rotation_degrees = Vector3(-52,-32,0)
	light.light_color = Color("#a7afdc")
	light.light_energy = .13
	arena.add_child(light)
	player = body(.38,1.65,2,1)
	arena.add_child(player)
	camera = Camera3D.new()
	camera.position.y = 1.48
	camera.fov = 79
	camera.far = 48
	player.add_child(camera)
	camera.make_current()
	build_view_weapon()
	hud = preload("res://shroom_hud.gd").new()
	hud.game = self
	hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	hud.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(hud)
	controls = Touch.new()
	controls.configure("firstperson")
	controls.actions.assign(["fire","dash","swap"])
	controls.look_changed.connect(look)
	controls.action_pressed.connect(action)
	add_child(controls)
	make_overlay()
	init_audio()
	if OS.has_feature("web"):
		# Leave audio mixing headroom on mobile WebView; physics remains at 60 Hz.
		Engine.max_fps = 30
	# New and Resume build the selected room. Constructing a preview here
	# duplicates geometry during launch and briefly holds two copies in memory.
	OnKlavGame.attach_session(self,snapshot,restore,start_new,
		"SHROOM • " + INCIDENT_BRIEF + "\n\nMove with the left stick and drag on the right to aim. Hold FIRE, tap DASH to dodge attacks, and use SWAP to change weapons. Glowing kits restore health.\n\nFollow the objective arrow through five sectors. Complete the sector objective, find the extraction keycard when prompted, and enter the portal once the route is clear. Choose an upgrade between sectors and defeat the Cap King in the final sector. Room connections and objective locations vary by run.\n\nKeyboard: WASD to move, arrow keys or drag to aim, Ctrl/Space to fire, Shift to dash, Q to swap, R to reload.",
		"user://shroom-session.json",{},{"format_version":1,"is_resumable":func(d):return str(d.get("phase")) not in ["dead","won"]})
	OnKlavGame.suspended.connect(func():
		controls.reset_input()
		fire_tap = false
		music.stream_paused = true
		sync_web_score())
	OnKlavGame.resumed.connect(func():
		music.stream_paused = false
		sync_web_score()
		load_best())
	OnKlavGame.chrome.preferences_changed.connect(sync_web_score)
	call_deferred("load_best")

func load_best() -> void:
	if best_loaded or loading_best or Time.get_ticks_msec() < retry_best_at: return
	loading_best = true
	retry_best_read = false
	var saved := await OnKlavGame.load_data("profile")
	loading_best = false
	if not saved.get("ok",false):
		retry_best_read = true
		retry_best_at = Time.get_ticks_msec()+5000
		return
	if saved.get("exists",false):
		var profile: Variant = saved.get("state")
		if not profile is Dictionary or not numeric(profile.get("best"),0,100000000,true):
			retry_best_at = Time.get_ticks_msec()+2000
			return
		stored_best = int(profile.best)
	best_loaded = true
	highest = maxi(highest,stored_best)
	if phase in ["dead","won"]: update_overlay()

func body(radius: float, height: float, layer: int, mask: int) -> CharacterBody3D:
	var actor := CharacterBody3D.new()
	actor.collision_layer = layer
	actor.collision_mask = mask
	var c := CollisionShape3D.new()
	var shape := CapsuleShape3D.new()
	shape.radius = radius
	shape.height = height
	c.shape = shape
	c.position.y = height*.5
	actor.add_child(c)
	return actor

func material(color: Color, emission := 0.0, tile := false, surface := "") -> StandardMaterial3D:
	var key := "%s/%s/%s/%s" % [color.to_html(), emission, tile, surface]
	if art_cache.has(key): return art_cache[key]
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.roughness = .82
	if emission > 0:
		m.emission_enabled = true
		m.emission = color
		m.emission_energy_multiplier = emission
	if tile:
		if surface in ["breach_wall","breach_floor"]:
			var deck := surface == "breach_floor"
			m.albedo_texture = preload("res://assets/breach-floor.png") if deck else preload("res://assets/breach-wall.png")
			m.normal_enabled = true
			m.normal_texture = preload("res://assets/breach-floor-normal.png") if deck else preload("res://assets/breach-wall-normal.png")
			m.normal_scale = .72
			m.uv1_triplanar = true
			m.uv1_scale = Vector3.ONE * (.62 if deck else .47)
			m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
			m.metallic = .42 if deck else .06
			m.roughness = .58 if deck else .84
			art_cache[key] = m
			return m
		m.albedo_texture = preload("res://assets/foundry-tread.png") if surface == "floor" else preload("res://assets/foundry-panels.png")
		m.uv1_triplanar = true
		m.uv1_scale = Vector3.ONE * .58
		m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
		m.metallic = .18
		m.roughness = .70
	elif surface == "fungus":
		m.albedo_texture = preload("res://assets/fungal-skin.png")
		m.roughness = .62
		m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
	art_cache[key] = m
	return m

func mesh(parent: Node3D, shape: Mesh, at: Vector3, mat: Material, stretch := Vector3.ONE) -> MeshInstance3D:
	var visual := MeshInstance3D.new()
	visual.mesh = shape
	visual.material_override = mat
	visual.position = at
	visual.scale = stretch
	visual.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(visual)
	return visual

func add_prop_art(parent: Node3D, model_name: String) -> void:
	for shape in prop_art.model(model_name):
		mesh(parent, shape, Vector3.ZERO, null)

# Batch only non-interactive room dressing; never collision, doors or pickups.
# Using a unit mesh and instance Basis avoids a unique BoxMesh per small detail.
func decor_box(at: Vector3, size: Vector3, mat: Material, tilt_x := 0.0) -> void:
	add_decor_instance("box",at,size,mat,tilt_x)

func decor_ball(at: Vector3, mat: Material, size: Vector3) -> void:
	add_decor_instance("sphere",at,size,mat,0.0)

func add_decor_instance(shape_name: String, at: Vector3, size: Vector3, mat: Material, tilt_x: float) -> void:
	# A sector-wide MultiMesh submits every instance whenever any room is in
	# view. Keep batches spatially compact so the renderer can cull whole rooms.
	var area := expedition.room_at(at) if expedition_mode else 0
	var key := "%s/%s/%s" % [area,shape_name,mat.get_instance_id()]
	if not decor_batches.has(key):
		decor_batches[key] = {"shape":shape_name,"material":mat,"transforms":[]}
	var basis := Basis(Vector3.RIGHT,tilt_x).scaled(size)
	decor_batches[key].transforms.append(Transform3D(basis,at))

func flush_decor_batches() -> void:
	for entry in decor_batches.values():
		var transforms: Array = entry.transforms
		if transforms.is_empty(): continue
		var multimesh := MultiMesh.new()
		multimesh.transform_format = MultiMesh.TRANSFORM_3D
		if entry.shape == "box":
			multimesh.mesh = art_cache["decor_unit_box"]
		else:
			multimesh.mesh = ball()
		multimesh.instance_count = transforms.size()
		for i in range(transforms.size()):
			multimesh.set_instance_transform(i,transforms[i])
		var visual := MultiMeshInstance3D.new()
		visual.multimesh = multimesh
		visual.material_override = entry.material
		visual.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		room.add_child(visual)
	decor_batches.clear()

func cube(parent: Node3D, at: Vector3, size: Vector3, mat: Material) -> MeshInstance3D:
	if not box_meshes.has(size):
		var box := BoxMesh.new()
		box.size = size
		box_meshes[size] = box
	return mesh(parent,box_meshes[size],at,mat)

func ball() -> SphereMesh:
	if art_cache.has("sphere"): return art_cache.sphere
	var shape := SphereMesh.new()
	shape.radius = .5
	shape.height = 1
	shape.radial_segments = 12
	shape.rings = 6
	art_cache.sphere = shape
	return shape

func prepare_effect_pool() -> void:
	effect_root = Node3D.new()
	arena.add_child(effect_root)
	for i in FX_LIMIT:
		var mote := mesh(effect_root, ball(), Vector3.ZERO, null)
		mote.hide()
		free_motes.append(mote)
	var ring := TorusMesh.new()
	ring.inner_radius = .93
	ring.outer_radius = 1.0
	ring.rings = 24
	ring.ring_segments = 8
	for i in BLAST_LIMIT:
		var blast := Node3D.new()
		effect_root.add_child(blast)
		blast.hide()
		for part in 6:
			mesh(blast, ring if part == 2 else ball(), Vector3.ZERO, null)
		free_blasts.append(blast)

func mote_mesh(shape: Mesh, at: Vector3, mat: Material, stretch := Vector3.ONE) -> MeshInstance3D:
	var visual: MeshInstance3D = free_motes.pop_back()
	visual.mesh = shape
	visual.material_override = mat
	visual.transform = Transform3D(Basis.from_scale(stretch), at)
	visual.show()
	return visual

func recycle_mote(node: MeshInstance3D) -> void:
	node.hide()
	free_motes.append(node)

func recycle_blast(node: Node3D) -> void:
	node.hide()
	free_blasts.append(node)

func block(at: Vector3, size: Vector3, color: Color, surface := "") -> void:
	var b := StaticBody3D.new()
	b.position = at
	var c := CollisionShape3D.new()
	if not block_shapes.has(size):
		var shape := BoxShape3D.new()
		shape.size = size
		block_shapes[size] = shape
	c.shape = block_shapes[size]
	b.add_child(c)
	b.set_meta("impact_surface", "steel" if surface in ["floor","breach_floor"] else "concrete")
	cube(b, Vector3.ZERO, size, material(color, 0, true, surface))
	room.add_child(b)

func discard(node: Node) -> void:
	if is_instance_valid(node) and node.get_parent() != null:
		node.get_parent().remove_child(node)
		node.queue_free()

# Ambush rooms are deterministic per run, never in the safe entry, central
# hub, exit room, or boss stage. These are optional target-room reinforcements.
func configure_ambushes() -> void:
	explored_rooms_mask = 1 << Expedition.ENTRY
	ambush_rooms.clear()
	ambush_used_mask = 0
	ambush_pending_room = -1
	ambush_timer = 0.0
	previous_player_room = Expedition.ENTRY
	if not expedition_mode or stage == MAX_STAGE: return
	var choices: Array[int] = expedition.targets.duplicate()
	choices.erase(Expedition.ENTRY)
	choices.erase(4)
	choices.erase(expedition.exit_room)
	var local := RandomNumberGenerator.new()
	local.seed = seed_code + stage*17713 + 79
	while not choices.is_empty() and ambush_rooms.size() < AMBUSH_MAX_PER_SECTOR:
		var index := local.randi_range(0,choices.size()-1)
		ambush_rooms.append(choices[index])
		choices.remove_at(index)

# Exploration is a reason to check side corridors, but never a resource grind:
# room rewards are a one-time pair of score bonuses per sector.
func tick_exploration() -> void:
	if not expedition_mode or phase != "combat": return
	var current_room := expedition.room_at(player.position)
	var flag := 1 << current_room
	if (explored_rooms_mask & flag) != 0: return
	explored_rooms_mask |= flag
	var explored := 0
	for id in range(9):
		if (explored_rooms_mask & (1 << id)) != 0: explored += 1
	if explored == 4:
		score += EXPLORATION_FIRST_BONUS
		show_banner("EXPLORER  /  4 ROOMS  /  +100")
		sfx("pop")
	elif explored == 7:
		score += EXPLORATION_DEEP_BONUS
		show_banner("DEEP EXPLORER  /  7 ROOMS  /  +200")
		sfx("pop")
	# No checkpoint per step; existing milestones, pickups and sector saves handle persistence.

func tick_ambush(dt: float) -> void:
	if not expedition_mode or phase != "combat" or ambush_rooms.is_empty(): return
	var current_room := expedition.room_at(player.position)
	if current_room != previous_player_room:
		previous_player_room = current_room
		if ambush_pending_room >= 0 and ambush_pending_room != current_room:
			ambush_pending_room = -1
			ambush_timer = 0.0
		if current_room in ambush_rooms and (ambush_used_mask & (1 << current_room)) == 0:
			if nearby_patrol(current_room) != null:
				ambush_pending_room = current_room
				ambush_timer = AMBUSH_DELAY
				show_banner("PATROL ALERTED  /  WATCH THE DOORWAYS")
				sfx("wave")
	if ambush_pending_room < 0 or current_room != ambush_pending_room: return
	ambush_timer = maxf(0.0,ambush_timer-dt)
	if ambush_timer > 0.0: return
	var room_id := ambush_pending_room
	ambush_pending_room = -1
	ambush_used_mask |= 1 << room_id
	var enemy := nearby_patrol(room_id)
	if enemy != null:
		enemy.set_meta("alert",true)
		enemy.set_meta("pursuit_left",PURSUIT_MEMORY)
		show_banner("PATROL RESPONDING  /  CHECK THE CORRIDOR")
	OnKlavGame.checkpoint()

func nearby_patrol(room_id: int) -> CharacterBody3D:
	var nearest: CharacterBody3D
	var distance := INF
	for enemy in enemies:
		if enemy.get_meta("type") == 3 or enemy.get_meta("breach_guard",false): continue
		var from_room := expedition.room_at(enemy.position)
		if from_room != room_id and not expedition.paths.are_points_connected(from_room,room_id): continue
		var gap := enemy.position.distance_squared_to(player.position)
		if gap < distance:
			nearest = enemy
			distance = gap
	return nearest

func build_room(staged := false) -> void:
	encounter_prepared = false
	if not staged and not sector_checkpoint.is_empty():
		clear_sector_transition()
	if phase == "briefing":
		breach_state = Breach.INACTIVE
		breach_clock = 0.0
	breach_gate = null
	breach_bars = null
	access_card = null
	nail_pickup = null
	clear_cosmetic_effects()
	block_shapes.clear()
	box_meshes.clear()
	access_gate = null
	waste_surface = null
	sporeworks_roots = null
	sporeworks_warning = null
	sporeworks_cargo.clear()
	sporeworks_belt = null
	sporeworks_drive = null
	sporeworks_valve = null
	sporeworks_vent = null
	sporeworks_pressure_live = false
	sporeworks_steam_live = false
	sporeworks_restart = 0.0
	chamber_lanes.clear()
	stains.clear()
	discard(room)
	discard(actors)
	enemies.clear()
	orbs.clear()
	mortar_rounds.clear()
	covers.clear()
	pickups.clear()
	hazards.clear()
	room_lights.clear()
	canisters.clear()
	secret_supply = null
	hazard_clock = 0.0
	atmosphere_dirty = false
	hazard_next_hit = 0.0
	objective_nodes.clear()
	room = Node3D.new()
	actors = Node3D.new()
	arena.add_child(room)
	arena.add_child(actors)
	decor_batches.clear()
	if not art_cache.has("decor_unit_box"):
		art_cache["decor_unit_box"] = BoxMesh.new()
	var local := RandomNumberGenerator.new()
	local.seed = seed_code + stage*1297
	var accent: Color = [TEAL,Color("#b287ff"),GOLD,Color("#ff765c"),PINK][stage-1]
	# Per-sector atmosphere changes only when building a level, never each frame.
	if world_environment != null:
		world_environment.ambient_light_energy = [.25,.23,.27,.21,.22][stage-1]
		world_environment.fog_density = [.030,.036,.026,.044,.040][stage-1]
		world_environment.fog_light_color = Color("#162a24") if stage in [2,4] else Color("#201332")
	if expedition_mode:
		if staged:
			sector_steps.append_array(expedition_build_steps(accent))
		else:
			for step in expedition_build_steps(accent):
				step.call()
		return
	block(Vector3(0,-.35,0),Vector3(28,.7,28),Color("#84848a"), "floor")
	for i in range(4):
		var wall := Node3D.new()
		wall.rotation.y = i*PI/2
		room.add_child(wall)
		var at := Vector3(0,2.2,-14).rotated(Vector3.UP,i*PI/2)
		var extent := Vector3(28,4.4,.9) if i%2==0 else Vector3(.9,4.4,28)
		block(at,extent,Color("#6b587d"))
		cube(wall,Vector3(0,.48,-13.51),Vector3(26,.08,.06),material(accent,1.1))
		cube(wall,Vector3(0,3.25,-13.50),Vector3(26,.18,.12),material(Color("#21142d")))
		for x in [-10,-5,0,5,10]:
			cube(wall,Vector3(x,2.5,-13.40),Vector3(.15,3.3,.18),material(Color("#332246")))
			cube(wall,Vector3(x,2.83,-13.28),Vector3(.34,.55,.12),material(accent,1.35))
		for x in [-9,9]:
			cube(wall,Vector3(x,.017,0),Vector3(.085,.02,26),material(accent,.32))
	var patterns := [
		[Vector2(-6.5,-6.2),Vector2(6.5,-6.2),Vector2(-6.5,3.2),Vector2(6.5,3.2)],
		[Vector2(-4,-6),Vector2(4,-6),Vector2(-7,1),Vector2(3,3),Vector2(7,-1)],
		[Vector2(0,-4),Vector2(-6,-3),Vector2(6,-3),Vector2(-4,4),Vector2(4,4)],
		[Vector2(-5,-7),Vector2(2,-7),Vector2(6,-2),Vector2(0,0),Vector2(-6,4),Vector2(6,4)],
		[Vector2(0,-7),Vector2(-7,-4),Vector2(7,-4),Vector2(-4,2),Vector2(4,2),Vector2(0,4)],
	]
	var mirror_x := 1.0 if local.randi_range(0,1)==0 else -1.0
	for slot in patterns[stage-1]:
		var p := Vector3(slot.x*mirror_x+local.randf_range(-.9,.9),0,slot.y+local.randf_range(-.7,.7))
		covers.append(p)
		block(p+Vector3.UP*1.14,Vector3(2.35,2.28,2.35),Color("#746085"))
		cube(room,p+Vector3.UP*2.31,Vector3(2.45,.12,2.45),material(accent,.48))
		for offset in [-.76,.76]:
			cube(room,p+Vector3(offset,1.15,1.19),Vector3(.1,1.8,.07),material(accent,.72))
	for i in range(14):
		var angle := float(i)*TAU/14
		var p := Vector3(sin(angle)*12.7,0,cos(angle)*12.7)
		mesh(room,ball(),p+Vector3.UP*.19,material(accent,.16),Vector3(.45,.38,.45))
		mesh(room,ball(),p+Vector3.UP*.4,material(Color("#382745")),Vector3(.72,.29,.62))
	cube(room,Vector3(0,.02,0),Vector3(3.8,.03,.12),material(accent,.85))
	cube(room,Vector3(0,.02,0),Vector3(.12,.03,3.8),material(accent,.85))
	add_kits([Vector3(-10,0,0),Vector3(10,0,0)])
	decorate_sector(local,accent,false)
	flush_decor_batches()
	build_exit_portal(Vector3(0,0,-11.6))

func add_kits(positions: Array) -> void:
	for pos in positions:
		var kit := Node3D.new()
		kit.position = pos
		actors.add_child(kit)
		add_prop_art(kit, "biogel")
		pickups.append(kit)

func build_weapon_pickup() -> void:
	weapon_pickup = null
	if stage not in [2,4]:
		return
	var index := 2 if stage == 2 else 3
	if weapon_unlocks & (1 << index):
		return
	var pickup := Node3D.new()
	pickup.position = expedition.center(5 if stage == 2 else 8)+Vector3(2.53,0,-2.55)
	pickup.set_meta("weapon",index)
	actors.add_child(pickup)
	add_prop_art(pickup,"dock")
	var tint: Color = SHOT_TINTS[index]
	var ring := TorusMesh.new()
	ring.inner_radius = .40
	ring.outer_radius = .47
	ring.rings = 20
	ring.ring_segments = 6
	mesh(pickup,ring,Vector3.UP*.115,material(tint,1.12))
	var display := Node3D.new()
	display.name = "WeaponDisplay"
	display.position.y = 1.38
	display.rotation.y = PI*.5
	display.scale = Vector3.ONE*1.92
	pickup.add_child(display)
	var model := Node3D.new()
	model.position.z = .24
	model.rotation.z = -.13
	display.add_child(model)
	add_prop_art(model,"arc_lance" if index == 2 else "grav_mortar")
	var label := Label3D.new()
	label.position.y = 2.04
	label.billboard = BaseMaterial3D.BILLBOARD_FIXED_Y
	label.text = WEAPON_NAMES[index].to_upper()
	label.font_size = 36
	label.outline_size = 7
	label.pixel_size = .005
	label.modulate = tint
	pickup.add_child(label)
	weapon_pickup = pickup

func tick_weapon_pickup(dt: float) -> void:
	if not is_instance_valid(weapon_pickup):
		return
	var distance := player.position.distance_squared_to(weapon_pickup.position)
	if distance < 144.0 and not reduced_motion():
		var display := weapon_pickup.get_node("WeaponDisplay") as Node3D
		display.rotation.y = fposmod(display.rotation.y+dt*1.2,TAU)
		display.position.y = 1.38+sin(active_time*2.5)*.13
	if distance < 1.0:
		var index := int(weapon_pickup.get_meta("weapon"))
		weapon_unlocks |= 1 << index
		weapon = index
		reload_left = 0.0
		rounds[index] = CAPACITY[index]
		discard(weapon_pickup)
		weapon_pickup = null
		show_banner(WEAPON_NAMES[index].to_upper()+"  /  ACQUIRED")
		sfx("heal")
		OnKlavGame.checkpoint()

func build_waste_crossing() -> void:
	var pool := QuadMesh.new()
	pool.size = WASTE_BASIN.size
	var center := WASTE_BASIN.get_center()
	waste_surface = atmosphere_art.waste_pool()
	mesh(room,pool,Vector3(center.x,-.31,center.y),waste_surface).rotation.x = -PI*.5
	var rim := material(Color("#d0ae55"),.85)
	for strip: Rect2 in WASTE_DECK:
		var point := strip.get_center()
		var deck := Vector3(point.x,-.095,point.y)
		block(deck,Vector3(strip.size.x,.19,strip.size.y),Color("#8e9b9a"),"breach_floor")
		for side in [-1,1]:
			var edge := strip.position.x+.085 if side<0 else strip.end.x-.085
			# Draw seam-free edge strips; the inner bend remains open.
			var start := strip.position.y+.05
			var end := strip.end.y-.05
			decor_box(Vector3(edge,.026,(start+end)*.5),Vector3(.10,.052,end-start),rim)
		for row in floori(strip.size.y/.44):
			decor_box(Vector3(point.x,.018,strip.position.y+.26+row*.44),
				Vector3(strip.size.x-.23,.032,.045),material(Color("#53646a")))
	breach_sign(Vector3(3.05,2.13,10.65),"WASTE CROSSING","STAY ON THE CATWALK",0.0)

func waste_safe(at: Vector3) -> bool:
	var point := Vector2(at.x,at.z)
	if not WASTE_BASIN.has_point(point): return true
	return WASTE_DECK.any(func(strip): return strip.has_point(point))

func exploration_pickup_position(salt: int, offset: Vector3) -> Vector3:
	var random := RandomNumberGenerator.new()
	random.seed = seed_code + stage*11731 + salt
	var candidates: Array[int] = []
	for id in range(9):
		if id in [Expedition.ENTRY, 4, expedition.exit_room]: continue
		if expedition.exit_room not in expedition.paths.get_id_path(Expedition.ENTRY,id):
			candidates.append(id)
	return expedition.center(candidates[random.randi_range(0,candidates.size()-1)]) + offset

func nail_pickup_sectors() -> Array[int]:
	var random := RandomNumberGenerator.new()
	random.seed = seed_code + 92413
	var pool: Array[int] = [1,2,3,4,5]
	var selected: Array[int] = []
	for i in range(3):
		selected.append(pool.pop_at(random.randi_range(0,pool.size()-1)))
	return selected

func build_nail_pickup() -> void:
	if stage not in nail_pickup_sectors() or nail_pickups_collected & (1 << (stage-1)): return
	nail_pickup = Node3D.new()
	nail_pickup.position = clear_saved_cover_position(exploration_pickup_position(40631, Vector3(1.75,0,2.28)))
	actors.add_child(nail_pickup)
	var bundle := Node3D.new()
	bundle.name = "Nails"
	bundle.position.y = 1.03
	nail_pickup.add_child(bundle)
	add_prop_art(bundle,"triple_nails")

func tick_nail_pickup(dt: float) -> void:
	if not is_instance_valid(nail_pickup): return
	var distance := player.position.distance_squared_to(nail_pickup.position)
	if distance < 64 and not reduced_motion():
		nail_pickup.get_node("Nails").rotation.y += dt*1.8
	if distance >= 1.1: return
	nail_pickups_collected |= 1 << (stage-1)
	nail_charges += 10
	discard(nail_pickup)
	nail_pickup = null
	show_banner("TRIPLE NAILS  /  %d CHARGED SHOTS" % nail_charges)
	sfx("heal")
	OnKlavGame.checkpoint()

func build_access_door(at: Vector3, outward: Vector3) -> void:
	var angle := atan2(-outward.x,-outward.z)
	var tint := GOLD if stage == 2 else Color("#f18fae")
	var card_name := "AMBER KEYCARD" if stage == 2 else "RED KEYCARD"
	breach_sign(at-outward*.35+Vector3.UP*3.17,"EXTRACTION ACCESS",card_name,angle)
	if access_state < 2:
		access_gate = StaticBody3D.new()
		access_gate.position = at
		access_gate.rotation.y = angle
		room.add_child(access_gate)
		var collision := CollisionShape3D.new()
		var slab := BoxShape3D.new()
		slab.size = Vector3(3.30,3.80,.37)
		collision.shape = slab
		collision.position.y = 1.90
		access_gate.add_child(collision)
		cube(access_gate,collision.position,slab.size,material(Color("#46545a"),0,true,"breach_wall"))
		cube(access_gate,Vector3(0,1.66,.21),Vector3(.19,2.98,.06),material(tint,.83))
		cube(access_gate,Vector3(.92,1.22,.23),Vector3(.31,.46,.13),material(Color("#25343d")))
		cube(access_gate,Vector3(.92,1.30,.31),Vector3(.19,.08,.032),material(tint,1.1))
	if access_state == 0:
		access_card = Node3D.new()
		access_card.position = exploration_pickup_position(17011, Vector3(-1.65,0,-2.1))
		actors.add_child(access_card)
		var card := Node3D.new()
		card.name = "Card"
		card.position.y = 1.08
		card.rotation.x = -.22
		access_card.add_child(card)
		cube(card,Vector3.ZERO,Vector3(.43,.27,.07),material(tint,.75))
		cube(card,Vector3(.11,0,.047),Vector3(.095,.112,.023),material(Color("#f6e1ac"),.45))
		cube(card,Vector3(-.075,-.053,.046),Vector3(.15,.035,.018),material(Color("#302b32")))
		breach_sign(access_card.position+Vector3.UP*1.58,card_name,"EXTRACTION ACCESS",0.0).reparent(access_card)

func tick_access_route(dt: float) -> void:
	if waste_surface != null and expedition.room_at(player.position) == Expedition.ENTRY:
		if not waste_safe(player.position):
			health = 0
			player.position.y = -.48
			finish("dead")
			return
		if not reduced_motion(): waste_surface.set_shader_parameter("clock",active_time)
	if is_instance_valid(access_card):
		if not reduced_motion() and player.position.distance_squared_to(access_card.position)<64.0:
			access_card.get_node("Card").rotation.y += dt*1.4
		if player.position.distance_squared_to(access_card.position)<1.1:
			discard(access_card)
			access_card = null
			access_state = 1
			show_banner("KEYCARD ACQUIRED  /  EXTRACTION DOOR")
			sfx("heal")
			OnKlavGame.checkpoint()
	if is_instance_valid(access_gate) and player.position.distance_squared_to(access_gate.position)<4.4 and access_state == 1:
		discard(access_gate)
		access_gate = null
		access_state = 2
		show_banner("ACCESS GRANTED  /  DOOR OPEN")
		sfx("break")
		OnKlavGame.checkpoint()

func expedition_build_steps(accent: Color) -> Array[Callable]:
	return [
		build_expedition_layout.bind(accent),
		build_expedition_objectives,
		build_expedition_dressing.bind(accent),
		build_expedition_landmarks.bind(accent),
		build_expedition_focal.bind(accent),
		flush_decor_batches,
		build_expedition_supplies,
	]

func build_expedition_layout(accent: Color) -> void:
	expedition.generate(seed_code,stage)
	configure_ambushes()
	var wall := Color("#9b9094").lerp(accent, .18)
	var wall_surface := "breach_wall" if stage == 1 else ""
	var floor_tint := Color("#84848a")
	if access_route and stage == 3:
		# Leave the airlock's basin open below the catwalk.
		block(Vector3(0,-.35,-5.25),Vector3(31,.7,20.5),floor_tint,"floor")
		block(Vector3(0,-.35,13),Vector3(31,.7,5),floor_tint,"floor")
		for side in [-1,1]:
			block(Vector3(side*10.11,-.35,7.75),Vector3(10.78,.7,5.5),floor_tint,"floor")
		build_waste_crossing()
	else:
		block(Vector3(0,-.35,0),Vector3(31,.7,31),floor_tint,"breach_floor" if stage==1 else "floor")
	for direction in [Vector3.LEFT,Vector3.RIGHT,Vector3.FORWARD,Vector3.BACK]:
		var extent := Vector3(.7,3.8,31) if direction.x!=0 else Vector3(31,3.8,.7)
		block(direction*15.3+Vector3.UP*1.9,extent,wall,wall_surface)
	cube(room,Vector3(0,3.85,0),Vector3(31,.2,31),material(Color("#433751"),0,true))
	for id in range(9):
		var point := expedition.center(id)
		decor_box(point + Vector3.UP * 3.66, Vector3(2.9, .18, .57), material(Color("#374650"), 0, true))
		decor_box(point + Vector3.UP * 3.55, Vector3(2.45, .06, .27), material(accent, .24))
		for other in Expedition.NEIGHBORS[id]:
			if other<=id: continue
			var next := expedition.center(other)
			var at := (point+next)*.5
			var side := Vector3.FORWARD if absf(next.x-point.x)>0 else Vector3.RIGHT
			var width := Vector3(.58,3.8,10) if side.z!=0 else Vector3(10,3.8,.58)
			if not expedition.paths.are_points_connected(id,other):
				block(at+Vector3.UP*1.9,width,wall,wall_surface)
				continue
			var segment := Vector3(.58,3.8,3.35) if side.z!=0 else Vector3(3.35,3.8,.58)
			for sign_value in [-1,1]:
				block(at+Vector3.UP*1.9+side*sign_value*3.325,segment,wall,wall_surface)
				cube(room,at+Vector3.UP*1.5+side*sign_value*1.62,Vector3(.60,2.9,.60),material(accent,.65))
			var floor_strip := Vector3(1.1,.035,2.4) if side.z!=0 else Vector3(2.4,.035,1.1)
			cube(room,at+Vector3.UP*.018,floor_strip,material(accent,.62))

func build_expedition_objectives() -> void:
	for i in range(expedition.targets.size()):
		var node := Node3D.new()
		node.position = expedition.center(expedition.targets[i])
		actors.add_child(node)
		if stage == 4:
			var pump := Node3D.new()
			pump.position = node.position
			room.add_child(pump)
			add_prop_art(pump,"furnace_pump")
		else:
			add_prop_art(node,"dock")
		var sample := Node3D.new()
		sample.name = "Specimen"
		sample.position.y = .95
		sample.rotation.z = .18
		node.add_child(sample)
		add_prop_art(sample,"pump_core" if stage == 4 else "sample")
		if stage == 2:
			var relay := Node3D.new()
			relay.position = node.position
			room.add_child(relay)
			add_prop_art(relay,"pressure_manifold")
			breach_sign(relay.position+Vector3.UP*1.7,"VENT RELAY %02d" % (i+1),"APPROACH TO RESET",0.0).reparent(node)
		node.visible=(objective_mask & (1<<i))==0
		objective_nodes.append(node)
	var exit_point := expedition.center(expedition.exit_room)
	var outward := exit_wall_direction()
	var inward := -outward
	build_exit_portal(exit_point+outward*4.16,atan2(inward.x,inward.z))
	if access_route and stage in [2,4]:
		build_access_door(exit_point-outward*5.0,outward)
	if breach_state in [Breach.SEALED, Breach.WARNING]:
		build_breach_gate()
	var first_kit := expedition.center(3)+Vector3(-2.05,0,2.63) if stage == 1 else expedition.center(4)+Vector3(3,0,1)
	add_kits([first_kit,exit_point+Vector3(3,0,1)])
	build_weapon_pickup()

func build_expedition_dressing(accent: Color) -> void:
	var decor_rng := RandomNumberGenerator.new()
	decor_rng.seed = seed_code + stage*1297 + 8191
	decorate_sector(decor_rng,accent,true)
	sector_focal_variant = decor_rng.randi_range(0,2)

func build_expedition_focal(accent: Color) -> void:
	var main_room := expedition.exit_room if stage == MAX_STAGE else 4
	add_sector_focal(expedition.center(main_room),accent,(main_room+stage+sector_focal_variant)%4)

func build_expedition_landmarks(accent: Color) -> void:
	# A distinct hub feature for each stage.
	# Decorative only: no changes to navigation, collision, or serialization.
	add_sector_identity(accent)
	if stage == 1:
		dress_breach_route()
		dress_breach_side_rooms()
		if not practice_complete and phase in ["briefing", "combat"]:
			build_practice_target()
	if stage == 2:
		build_sporeworks_line()
		dress_sporeworks_side_rooms()
		build_sporeworks_ventilation()
	elif stage in [3, 4, 5]:
		build_hub_chamber(expedition.center(expedition.exit_room) if stage == MAX_STAGE else Vector3.ZERO)
		match stage:
			3: dress_culture_side_rooms()
			4: dress_furnace_side_rooms()
			MAX_STAGE: dress_throne_side_rooms()

func build_expedition_supplies() -> void:
	# Fixed count and seeded locations keep geometry stable on save/resume.
	# Props are tucked into side corners, away from doors and objectives.
	var sites: Array[int] = [0,2,6,8]
	for i in range(mini(SPORE_CANISTER_LIMIT,sites.size())):
		var id: int = sites[(i+stage-1)%sites.size()]
		add_spore_canister(expedition.center(id)+Vector3(2.6,0,2.5))
	# One optional supply cache rewards exploring a side room each sector.
	# Use the fourth corner, which never hosts a canister in this sector.
	# Secrets are optional, never block objectives, and should not overlap hazards.
	var secret_id: int = sites[(stage+2)%sites.size()]
	secret_supply = Node3D.new()
	secret_supply.position = expedition.center(secret_id)+Vector3(-2.6,0,-2.6)
	actors.add_child(secret_supply)
	add_prop_art(secret_supply, "cache")

	if is_instance_valid(access_card): access_card.position = clear_saved_cover_position(access_card.position)
	build_nail_pickup()


# One deliberately recognizable landmark in each sector's central hub. All parts
# stay overhead or against the north wall; all materials are unlit and shared.
# The 10m grid, doors, collision and pathfinding are untouched.
# The opening uses a fixed art layout over the seeded navigation grid.

func dress_breach_route() -> void:
	var alloy := material(Color("#657984"),0.0,true)
	var enamel := material(Color("#849ca4"),0.0,true)
	var rubber := material(Color("#24272f"))
	var caution := material(Color("#d6a84e"),.26)
	var coolant := material(Color("#9dddf0"),.95)
	var emergency := material(Color("#ff9a57"),.95)
	for side in [-1,1]:
		var x: float = side*1.68
		decor_box(Vector3(x,1.82,5),Vector3(.24,3.64,.66),alloy)
		decor_box(Vector3(x,1.76,5.35),Vector3(.13,3.38,.12),rubber)
		for y in [.62,1.46,2.30]:
			decor_box(Vector3(x,y,5.44),Vector3(.11,.43,.047),caution,.28)
		decor_box(Vector3(side*2.97,.075,9.4),Vector3(.76,.15,7.2),alloy)
		decor_box(Vector3(side*2.57,.14,9.4),Vector3(.07,.13,7.24),rubber)
		for rung in 12:
			decor_box(Vector3(side*2.97,.158,6.16+rung*.55),Vector3(.68,.027,.11),rubber)
		for z in [6.15,8.72,11.29,13.86]:
			decor_box(Vector3(side*4.54,1.48,z),Vector3(.28,2.96,.20),enamel)
			decor_box(Vector3(side*4.39,.46,z),Vector3(.12,.51,.27),rubber)
		for z in [7.16,10.22,13.28]:
			decor_box(Vector3(side*4.67,2.78,z),Vector3(.14,.14,2.70),alloy)
			decor_box(Vector3(side*4.59,2.61,z),Vector3(.11,.10,2.70),rubber)
	decor_box(Vector3(0,3.53,5),Vector3(3.54,.28,.67),alloy)
	decor_box(Vector3(0,3.37,5.33),Vector3(2.88,.075,.12),coolant)
	decor_box(Vector3(0,3.36,4.69),Vector3(2.88,.075,.12),emergency)
	breach_sign(Vector3(0,2.57,14.89),"07 / DECONTAMINATION","CREW EVACUATION COMPLETE",PI)
	breach_sign(Vector3(0,3.12,5.38),"CONTAINMENT ACCESS","AUTHORIZED PERSONNEL",0.0)
	breach_sign(Vector3(3.03,1.98,4.62),"REMEDIATION LAB","BIOLOGICAL BREACH",PI)
	build_observation_window(alloy, rubber)
	dress_breach_surfaces(alloy,rubber,emergency)
	# The intact entry uses cool light; the chamber is keyed from behind the spitter.
	ceiling_pool(Vector3(0,3.03,8.35),Color("#a0dbff"),2.55,5.25)
	ceiling_pool(Vector3(2.55,2.88,-.55),Color("#ff9e62"),3.0,5.1)
	var observation_fill := OmniLight3D.new()
	observation_fill.position = Vector3(3.03,1.84,4.35)
	observation_fill.omni_range = 3.2
	observation_fill.omni_attenuation = 1.7
	observation_fill.light_color = Color("#9bccf1")
	observation_fill.light_energy = 1.46
	room.add_child(observation_fill)
	var vapor := atmosphere_art.vapor()
	vapor.set_shader_parameter("tint",Color("#deb18d"))
	var haze := Node3D.new()
	haze.position = Vector3(3.46,.58,.68)
	room.add_child(haze)
	add_vapor_cards(haze,Vector2(1.65,1.16),vapor)
	haze.set_meta("breach_vapor",vapor)
	room.set_meta("breach_haze",haze)
	var ambience := spatial_effect(room,Vector3(3.24,.85,2.3),"lab_air",-22,9.0)
	room.set_meta("breach_ambience",ambience)
	add_contact_shadow(Vector3(3.24,.025,3.75),Vector2(2.65,1.7),room)

func dress_breach_side_rooms() -> void:
	var labels := {0:"STERILE STORES",1:"OBSERVATION",2:"SERVICE TRANSFER",
		3:"TRIAGE",5:"POWER SERVICE",6:"OVERFLOW WARD",8:"QUARANTINE STORAGE"}
	var steel := material(Color("#667883"),0,true,"breach_wall")
	var rubber := material(Color("#303642"))
	var glass := material(Color("#7095a0"),.19)
	var linen := material(Color("#96aaa1"))
	var med := material(Color("#79e2bc"),.68)
	var amber := material(Color("#d8975d"),.56)
	var footprints: Array[Rect2] = []
	for id in labels:
		var center := expedition.center(id)
		var sign_at := center+Vector3(3.22,2.36,-4.57)
		breach_sign(sign_at,labels[id],"BREACH  /  %02d" % id,0.0)
		var anchor := center+Vector3(-3.39,0,2.91)
		var depth := 1.10 if id in [2,5] else .82
		footprints.append(Rect2(Vector2(anchor.x-.62,anchor.z-depth*.5),Vector2(1.24,depth)))
		match id:
			3,6: # Triage stretchers and privacy screens; one biogel kit in room 03.
				block(anchor+Vector3.UP*.30,Vector3(1.21,.60,.82),Color("#5b686b"),"breach_wall")
				decor_box(anchor+Vector3.UP*.64,Vector3(1.29,.14,.82),linen)
				decor_box(anchor+Vector3(-.39,.76,0),Vector3(.42,.14,.56),med)
				for z in [-.42,.42]:
					decor_box(anchor+Vector3(0,.63,z),Vector3(1.43,.085,.053),steel)
				for rib in 5:
					decor_box(anchor+Vector3(-.80,1.48,-.48+rib*.17),Vector3(.038,1.02,.149),linen)
				decor_box(anchor+Vector3(-.80,2.03,-.14),Vector3(.065,.049,.93),steel)
				decor_box(anchor+Vector3(.31,2.14,.05),Vector3(.66,.08,.35),med)
			0,8: # Lockers line the wall; the quarantine cache keeps its seeded location.
				block(anchor+Vector3.UP*.43,Vector3(1.17,.86,.80),Color("#505761"),"breach_wall")
				for z in [-3.59,-2.85,-2.11]:
					var locker := center+Vector3(-4.44,1.0,z)
					decor_box(locker,Vector3(.49,1.95,.62),steel)
					decor_box(locker+Vector3(.257,.25,0),Vector3(.039,.51,.43),rubber)
					decor_box(locker+Vector3(.289,.07,-.15),Vector3(.036,.07,.13),amber)
				if id == 8:
					var stain := material(Color("#548763"),.10,false,"fungus")
					for j in 5:
						decor_ball(center+Vector3(-3.16+j*.23,.022,-2.94+j*.09),
							stain,Vector3(.40,.018,.17))
					decor_box(center+Vector3(-3.08,1.92,-2.78),Vector3(.38,.065,.44),amber)
			2,5: # Offset machinery leaves a straight retreat through the door.
				block(anchor+Vector3.UP*.38,Vector3(1.24,.76,1.10),Color("#474b56"),"breach_wall")
				for y in [.23,.51,.82]:
					decor_box(anchor+Vector3(.66,y,0),Vector3(.088,.087,.82),steel)
				for z in [-3.43,-2.76,-2.09]:
					decor_box(center+Vector3(-4.46,1.60,z),Vector3(.21,.24,.70),steel)
					decor_box(center+Vector3(-4.30,1.62,z),Vector3(.10,.095,.18),amber)
				for rib in 7:
					decor_box(anchor+Vector3(-.44+rib*.16,.022,-.92),Vector3(.061,.030,.54),rubber)
			1: # Console and glass fragments frame the sightline across the room.
				block(anchor+Vector3.UP*.41,Vector3(1.18,.82,.79),Color("#455965"),"breach_wall")
				decor_box(anchor+Vector3(0,.89,0),Vector3(1.22,.15,.79),steel,.24)
				decor_box(anchor+Vector3(0,.985,.08),Vector3(.66,.028,.27),med,.24)
				for y in [1.30,2.23]:
					decor_box(center+Vector3(-4.61,y,-2.78),Vector3(.12,.07,1.76),steel)
				for z in [-3.32,-2.35]:
					decor_box(center+Vector3(-4.59,1.76,z),Vector3(.052,.87,.51),glass,.22)
				for j in 6:
					decor_ball(center+Vector3(-3.84+j*.11,.055,-3.23+j*.18),
						material(Color("#7a785b"),.05,false,"fungus"),Vector3(.24,.11,.21))
		add_contact_shadow(anchor+Vector3.UP*.019,Vector2(1.57,1.34),room)
	room.set_meta("side_room_cover",footprints)

func clear_saved_cover_position(at: Vector3) -> Vector3:
	# Move checkpoints out of props introduced or moved since they were saved.
	for key in ["side_room_cover","sporeworks_cover"]:
		for footprint: Rect2 in room.get_meta(key,[]):
			var clearance := footprint.grow(.52)
			if clearance.has_point(Vector2(at.x,at.z)):
				var center_x := expedition.center(expedition.room_at(at)).x
				at.x = clearance.position.x-.035 if footprint.get_center().x>center_x else clearance.end.x+.035
	return at

func dress_breach_surfaces(alloy: Material, rubber: Material, emergency: Material) -> void:
	var ceramic := material(Color("#8a9da7"),0,true,"breach_wall")
	var chipped := material(Color("#929286"))
	var warning := material(Color("#c7984c"),.26)
	for side in [-1,1]:
		for z in [7.43,9.98,12.55]:
			var at := Vector3(side*4.65,1.55,z)
			decor_box(at,Vector3(.16,2.52,2.17),rubber)
			decor_box(at+Vector3(-side*.09,.08,0),Vector3(.075,2.25,2.02),ceramic)
			for edge in [-.92,.92]:
				decor_box(at+Vector3(-side*.135,.075,edge),Vector3(.044,2.17,.048),alloy)
				for y in [-.88,.99]:
					decor_ball(at+Vector3(-side*.156,y,edge*.93),chipped,Vector3(.038,.067,.067))
			decor_box(at+Vector3(-side*.14,-.78,0),Vector3(.067,.19,1.86),alloy)
		for stripe in 5:
			decor_box(Vector3(side*(.77+stripe*.19),.024,5.57),Vector3(.11,.018,.36),warning)
	for x in [2.93,3.17,3.41]:
		decor_box(Vector3(x,.027,3.56),Vector3(.085,.030,.62),rubber)
	decor_box(Vector3(3.17,.021,3.56),Vector3(.78,.025,.78),alloy)
	for z in [1.34,1.83,2.32]:
		decor_box(Vector3(4.48,.42,z),Vector3(.16,.18,.51),rubber)
		decor_box(Vector3(4.37,.43,z),Vector3(.041,.079,.34),emergency)
	var fungus := material(Color("#818558"),.08,false,"fungus")
	for i in 8:
		var offset := i*.095
		decor_ball(Vector3(3.56+offset,.047+sin(i*.78)*.019,3.34-offset*.42),
			fungus,Vector3(.23,.083,.22))

func breach_sign(at: Vector3, label: String, line: String, angle: float) -> Node3D:
	var sign_root := Node3D.new()
	sign_root.position = at
	sign_root.rotation.y = angle
	room.add_child(sign_root)
	cube(sign_root,Vector3.ZERO,Vector3(1.76,.39,.05),material(Color("#24313b")))
	for data in [[label,.07,36,Color("#d6f7ff")],[line,-.087,23,Color("#ffbb75")]]:
		var text := Label3D.new()
		text.text = data[0]
		text.position = Vector3(0,data[1],.031)
		text.font_size = data[2]
		text.pixel_size = .0032
		text.modulate = data[3]
		text.outline_size = 0
		sign_root.add_child(text)
	return sign_root

func build_observation_window(alloy: Material, gasket: Material) -> void:
	# A damaged inspection window sits in the solid wall beside the doorway.
	var at := Vector3(3.03,1.14,4.62)
	decor_box(at,Vector3(2.22,1.21,.12),gasket)
	for x in [-1.08,1.08]:
		decor_box(at+Vector3(x,0,-.08),Vector3(.12,1.26,.15),alloy)
	for y in [-.60,.60]:
		decor_box(at+Vector3(0,y,-.08),Vector3(2.25,.13,.15),alloy)
	var glass := SurfaceTool.new()
	glass.begin(Mesh.PRIMITIVE_TRIANGLES)
	for point in [Vector2(-1,-.53),Vector2(-1,.52),Vector2(-.30,.52),
			Vector2(-1,-.53),Vector2(-.30,.52),Vector2(-.58,-.11),
			Vector2(.98,-.53),Vector2(.25,-.53),Vector2(.57,.03),
			Vector2(.98,-.53),Vector2(.57,.03),Vector2(.98,.52)]:
		glass.add_vertex(Vector3(point.x,point.y,0))
	glass.generate_normals()
	var pane := material(Color("#527e89"),.18).duplicate() as StandardMaterial3D
	pane.cull_mode = BaseMaterial3D.CULL_DISABLED
	pane.roughness = .19
	mesh(room,glass.commit(),at+Vector3.FORWARD*.09,pane)
	var fracture := material(Color("#95bec2"),.22)
	for edge in [[Vector2(-.93,.38),Vector2(-.58,-.11)],[Vector2(-.58,-.11),Vector2(-.94,-.35)],
			[Vector2(.57,.03),Vector2(.92,.23)],[Vector2(.57,.03),Vector2(.78,-.43)]]:
		var a: Vector2 = edge[0]
		var b: Vector2 = edge[1]
		var mid := (a+b)*.5
		var seam := cube(room,at+Vector3(mid.x,mid.y,-.099),Vector3(a.distance_to(b),.013,.010),fracture)
		seam.rotation.z = (b-a).angle()
	for x in [-.80,.80]:
		for y in [-.59,.59]:
			decor_ball(at+Vector3(x,y,-.165),alloy,Vector3(.070,.07,.045))
	var infection := material(Color("#6c7951"),.12,false,"fungus")
	for i in 9:
		var t := i/8.0
		var growth := Vector3(3.94-.94*t,.06+.76*t*t,4.42-.26*t)
		decor_ball(growth,infection,Vector3(.21-.08*t,.15+.14*t,.24-.10*t))
		if i%2==0:
			decor_ball(growth+Vector3(.13,.04,-.08),material(Color("#a78659"),.09),Vector3(.095,.13,.12))

func add_contact_shadow(at: Vector3, size: Vector2, parent: Node3D) -> void:
	if not art_cache.has("contact_shadow"):
		art_cache["contact_shadow"] = atmosphere_art.effect("contact_shadow","""
shader_type spatial;
render_mode unshaded, depth_draw_never, cull_disabled;
void fragment() {
	float radius = length(UV * 2.0 - 1.0);
	ALBEDO = vec3(0.013, 0.015, 0.021);
	ALPHA = (1.0 - smoothstep(0.16, 0.99, radius)) * 0.55;
}
""")
	var plane := QuadMesh.new()
	plane.size = size
	mesh(parent,plane,at,art_cache["contact_shadow"]).rotation.x = -PI*.5

func build_practice_target() -> void:
	var target := StaticBody3D.new()
	target.collision_layer = 4
	target.collision_mask = 0
	target.position = Vector3(1.4, 1.48, 8.1)
	target.set_meta("practice_target", true)
	room.add_child(target)
	var collision := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = .43
	collision.shape = shape
	target.add_child(collision)
	mesh(target, ball(), Vector3.ZERO, material(Color("#344e56"), .28), Vector3(.92, .92, .18))
	mesh(target, ball(), Vector3(0, 0, .11), material(GOLD, 1.10), Vector3(.48, .48, .17))
	cube(target, Vector3(0, -.72, 0), Vector3(.12, .66, .12), material(Color("#506474")))

func field_prompt() -> String:
	if not expedition_mode or phase != "combat":
		return ""
	if waste_surface != null and expedition.room_at(player.position) == Expedition.ENTRY:
		return "Follow the catwalk · Green waste is lethal"
	if is_instance_valid(access_gate) and player.position.distance_squared_to(access_gate.position)<9.0:
		return "Extraction door locked · Find the keycard" if access_state == 0 else "Approach to unlock the extraction door"
	if is_instance_valid(weapon_pickup) and player.position.distance_squared_to(weapon_pickup.position) < 9.0:
		return "Approach to pick up the "+WEAPON_NAMES[int(weapon_pickup.get_meta("weapon"))]
	if stage == 1 and expedition.room_at(player.position) == Expedition.ENTRY and breach_state == Breach.SEALED:
		return "Move to the shutter" if practice_complete else "Drag to aim · Fire at the gold target"
	if stage == 2:
		var room_id := expedition.room_at(player.position)
		var local := player.position-expedition.center(room_id)
		if room_id == 4 and player.position.distance_to(SPOREWORKS_STEAM) < 1.9 and not objectives_complete():
			return "Steam rising · Wait or take the side aisle" if sporeworks_steam_signal.visible else "Cross the grate or use the side aisle"
		if room_id == SPOREWORKS_POD_ROOM and local.x > .65 and local.z > -.2 and (sporeworks_cleared & SPOREWORKS_POD) == 0:
			return "Close in and break the pod" if weapon == 1 else "Swap to the Sporebreaker for the pod"
		if room_id == SPOREWORKS_PRESSURE_ROOM and local.x < -.65 and local.z > -.2 and (sporeworks_cleared & SPOREWORKS_TENDRIL) == 0:
			var cycle := fposmod(active_time, SPOREWORKS_CYCLE)
			if cycle >= 2.25 and cycle < 4.28:
				return "Roots rising · Wait or dash through"
			return "Pin the amber valve with the Nailgun"
	if stage in [3, 4] and expedition.room_at(player.position) == 4 and absf(player.position.z) < 3.6:
		if stage == 3:
			if culture_sealed == 3:
				return "Both ventilation lanes sealed"
			if culture_sealed != 0:
				return "Cyan lane safe · Shoot the other vent"
			return "Shoot a glowing vent to seal its lane"
		return "Amber warns of heat · Follow the cyan centerline or Dash"
	if is_instance_valid(nail_pickup) and player.position.distance_squared_to(nail_pickup.position)<8.0:
		return "Approach to collect Triple Nails"
	if is_instance_valid(access_card) and player.position.distance_squared_to(access_card.position)<8.0:
		return "Approach to collect the extraction keycard"
	return ""

func build_hub_chamber(origin: Vector3) -> void:
	if stage == 3:
		dress_culture_lab()
	elif stage == 4:
		dress_root_furnace()
	elif stage == MAX_STAGE:
		dress_throne(origin)
	for lane in 2:
		var x := -2.3 if lane == 0 else 2.3
		var center := origin + Vector3(x, .045, 0)
		var size := Vector3(2.45, .025, 2.4)
		var grate := material(Color("#69777c"))
		for edge in [-1.15, 1.15]:
			cube(room, center + Vector3(0, 0, edge), Vector3(size.x, .025, .07), grate)
		for slat in 5:
			cube(room, center + Vector3((slat - 2) * .45, 0, 0), Vector3(.10, .025, size.z), grate)
		for edge in [-1.21,1.21]:
			cube(room, center + Vector3(edge,0,0), Vector3(.12,.045,2.4), grate)
		var tile := QuadMesh.new()
		tile.size = Vector2(2.40,2.34)
		var warning := mesh(room,tile,center+Vector3.UP*.003,atmosphere_art.grate(GOLD))
		warning.rotation.x = -PI*.5
		var live_floor := atmosphere_art.grate(Color("#94e657") if stage == 3 else Color("#ff694a"))
		var danger := mesh(room,tile,center+Vector3.UP*.005,live_floor)
		danger.rotation.x = -PI*.5
		var plume_color: Color = {3: Color("#9bd375"), 4: Color("#ff8a52"), 5: PINK}[stage]
		var vapor := atmosphere_art.vapor()
		vapor.set_shader_parameter("tint", plume_color)
		var plume := Node3D.new()
		plume.position = center + Vector3.UP*.59
		room.add_child(plume)
		add_vapor_cards(plume, Vector2(2.2,1.12), vapor)
		warning.hide()
		danger.hide()
		plume.hide()
		var entry := {"at":center,"warning":warning,"danger":danger,"plume":plume,
			"vapor":vapor,"live_floor":live_floor}
		chamber_lanes.append(entry)
		if stage == 3:
			build_culture_vent(lane,Vector3(x,1.45,-1.5),entry)
		elif stage == 4:
			build_furnace_lane(lane,center,entry)
		elif stage == MAX_STAGE:
			var roots := Node3D.new()
			roots.position = center
			room.add_child(roots)
			add_prop_art(roots,"pressure_roots")
			roots.hide()
			entry.throne_roots = roots

func dress_throne(origin: Vector3) -> void:
	var stone := material(Color("#675363"),0,true,"breach_wall")
	var root_hide := material(Color("#784557"),.05,false,"fungus")
	var ember := material(Color("#ed8574"),.66)
	var safe := material(Color("#a1cbd3"),.45)
	var metal := material(Color("#554853"),0,true,"breach_floor")
	var inlay := material(Color("#be856a"),.36)
	for z in [-3.54,-2.36,-1.18,0.0,1.18,2.36,3.54]:
		decor_box(origin+Vector3(0,.014,z),Vector3(.91,.023,.96),metal)
		for side in [-1,1]:
			decor_box(origin+Vector3(side*.47,.025,z),Vector3(.051,.024,.73),safe)
	for side in [-1,1]:
		var arch := Node3D.new()
		arch.position = origin+Vector3(side*3.86,2.97,-2.54)
		arch.scale.x = side
		room.add_child(arch)
		add_prop_art(arch,"throne_root")
		add_contact_shadow(origin+Vector3(side*3.7,.019,-2.54),Vector2(1.8,1.7),room)
		for x in [1.45,2.20,2.95]:
			decor_box(origin+Vector3(side*x,.023,-2.68),Vector3(.44,.022,.06),inlay)
	for side in [-1,1]:
		for z in [-3.55,3.55]:
			decor_box(origin+Vector3(side*4.39,1.56,z),Vector3(.60,3.1,.55),stone)
			for i in 5:
				var height := .36+i*.48
				var bend := sin(i*.71)*.31
				decor_ball(origin+Vector3(side*(4.23-bend),height,z),
					root_hide,Vector3(.49,.69,.51))
			decor_ball(origin+Vector3(side*4.09,2.47,z-.11),ember,Vector3(.18,.31,.18))
		for z in [-2.48,-1.68,1.68,2.48]:
			decor_box(origin+Vector3(side*.53,.029,z),Vector3(.07,.031,.56),safe)
		for z in [-1.12,1.12]:
			for j in 3:
				decor_box(origin+Vector3(side*(1.28+j*.43),.035,z),Vector3(.22,.041,.077),ember)
	ceiling_pool(origin+Vector3(-2.83,2.96,-2.88),Color("#fa8a7d"),2.5,4.85)
	var phase_light := ceiling_pool(origin+Vector3(2.83,2.99,2.88),Color("#ac8fec"),1.92,4.65)
	room.set_meta("throne_phase_light",phase_light)
	room.set_meta("throne_tier",-1)
	var seal := QuadMesh.new()
	seal.size = Vector2(2.0,2.0)
	var phase_seal := atmosphere_art.effect("throne_seal","""
shader_type spatial;
render_mode unshaded, depth_draw_never, cull_disabled;
uniform vec3 tint : source_color;
void fragment() {
	vec2 p = UV * 2.0 - 1.0;
	float r = length(p);
	float a = atan(p.y, p.x + 0.0001);
	float ring = 1.0 - smoothstep(0.009, 0.031, abs(r - 0.75));
	float inlay = (1.0 - smoothstep(0.009, 0.026, abs(r - 0.57))) * smoothstep(0.04, 0.32, abs(sin(a * 4.0)));
	float spoke = (1.0 - smoothstep(0.05, 0.19, abs(sin(a * 4.0)))) * smoothstep(0.69, 0.74, r) * (1.0 - smoothstep(0.90, 0.95, r));
	ALBEDO = tint;
	ALPHA = max(max(ring, inlay * 0.75), spoke) * 0.72;
}
""")
	phase_seal.set_shader_parameter("tint",Color("#ac8fec"))
	mesh(room,seal,origin+Vector3(0,.042,-2.42),phase_seal).rotation.x = -PI*.5
	room.set_meta("throne_seal",phase_seal)

func dress_root_furnace() -> void:
	var casing := material(Color("#67565a"),0,true,"breach_wall")
	var deck := material(Color("#625357"),0,true,"breach_floor")
	var soot := material(Color("#342a31"))
	var hot := material(Color("#e28d63"),.48)
	var pipe := material(Color("#8b6859"))
	var safe := material(Color("#78d7da"),.78)
	for side in [-1,1]:
		for z in [-3.64,-2.50,2.50,3.64]:
			decor_box(Vector3(side*4.43,1.68,z),Vector3(.40,3.36,.24),casing)
		for z in [-3.72,3.72]:
			var at := Vector3(side*4.54,1.30,z)
			decor_box(at,Vector3(.25,1.73,1.24),soot)
			for rib in 7:
				decor_box(at+Vector3(-side*.145,-.62+rib*.20,0),Vector3(.10,.082,1.10),casing)
			decor_box(at+Vector3(-side*.196,.74,0),Vector3(.051,.045,.85),hot)
			add_contact_shadow(Vector3(side*4.23,.019,z),Vector2(1.36,1.42),room)
		for y in [2.38,2.86,3.22]:
			decor_box(Vector3(side*4.56,y,0),Vector3(.20,.20,8.18),pipe)
			for z in [-3.65,-1.85,1.85,3.65]:
				decor_box(Vector3(side*4.53,y,z),Vector3(.27,.28,.18),casing)
		for z in [-3.74,-2.52,-1.3,-.08,1.14,2.36,3.58]:
			decor_box(Vector3(side*.68,.014,z),Vector3(.32,.021,1.04),deck)
		for z in [-3.62,-2.35,-1.08,.19,1.46,2.73,4.0]:
			decor_box(Vector3(side*.51,.028,z),Vector3(.063,.033,.86),safe)
		var x: float = side*1.76
		decor_box(Vector3(x,1.82,5),Vector3(.25,3.64,.57),casing)
	decor_box(Vector3(0,3.56,5),Vector3(3.75,.25,.58),casing)
	breach_sign(Vector3(0,3.16,5.30),"04 / ROOT FURNACE","PRESSURE TRANSFER STATION",0.0)
	breach_sign(Vector3(-3.05,2.02,4.61),"THERMAL LANES","CYAN WALKWAY SAFE",PI)
	breach_sign(Vector3(3.04,2.02,4.61),"ROOT PUMPS","RECOVER THE REGULATOR CORES",PI)
	ceiling_pool(Vector3(0,3.05,8.0),Color("#bad5d9"),1.90,5.0)
	ceiling_pool(Vector3(0,3.00,-2.0),Color("#ff945d"),2.4,5.0)

func dress_furnace_side_rooms() -> void:
	var labels := {0:"PUMP SERVICE",1:"PRESSURE CONTROL",2:"COOLANT STORAGE",
		3:"HEAT EXCHANGE",5:"EXHAUST SERVICE",6:"REGULATOR STORE",8:"CONDENSATE RETURN"}
	var steel := material(Color("#716068"),0,true,"breach_wall")
	var iron := material(Color("#373137"))
	var copper := material(Color("#97735f"))
	var amber := material(Color("#eaa268"),.56)
	var coolant := material(Color("#86cbd9"),.55)
	var cover := Vector3(1.26,.69,.96)
	var footprints: Array[Rect2] = []
	for id in labels:
		var center := expedition.center(id)
		breach_sign(center+Vector3(3.21,2.37,-4.57),labels[id],"ROOT FURNACE  /  %02d" % id,0.0)
		var bench := center+Vector3(-3.39,0,2.91)
		block(bench+Vector3.UP*cover.y*.5,cover,Color("#564a51"),"breach_wall")
		footprints.append(Rect2(Vector2(bench.x-cover.x*.5,bench.z-cover.z*.5),Vector2(cover.x,cover.z)))
		decor_box(bench+Vector3.UP*.71,Vector3(1.30,.07,.99),steel)
		add_contact_shadow(bench+Vector3.UP*.019,Vector2(1.58,1.40),room)
		match id:
			0,1,6:
				var machine := Node3D.new()
				machine.position = bench+Vector3.UP*(.77 if id == 0 else .95)
				room.add_child(machine)
				var model_name := "furnace_pump" if id==0 else ("pressure_manifold" if id==1 else "pump_core")
				add_prop_art(machine,model_name)
				for z in [-3.44,-2.61,-1.77]:
					decor_box(center+Vector3(-4.52,1.63,z),Vector3(.27,1.35,.62),steel)
					decor_box(center+Vector3(-4.36,1.79,z),Vector3(.048,.09,.35),amber)
			2,8:
				for x in [-.29,.29]:
					decor_box(bench+Vector3(x,1.09,0),Vector3(.38,.64,.53),iron)
					decor_box(bench+Vector3(x,1.22,.276),Vector3(.25,.13,.033),coolant)
				for y in [1.22,1.59,1.98]:
					decor_box(center+Vector3(-4.52,y,-2.65),Vector3(.23,.24,2.30),copper)
					decor_box(center+Vector3(-4.39,y,-2.39),Vector3(.080,.16,.31),coolant)
				for i in 6:
					decor_box(bench+Vector3(-.43+i*.17,.022,-.87),Vector3(.07,.025,.53),iron)
			3,5:
				for z in [-3.36,-2.23]:
					var vent := center+Vector3(-4.50,1.36,z)
					decor_box(vent,Vector3(.28,1.66,.91),iron)
					for i in 6:
						decor_box(vent+Vector3(.16,-.55+i*.21,0),Vector3(.079,.072,.76),steel)
				decor_box(bench+Vector3(0,.85,-.27),Vector3(1.03,.16,.27),copper)
				decor_box(bench+Vector3(0,1.07,-.30),Vector3(.64,.077,.10),amber)
	room.set_meta("side_room_cover",footprints)

func dress_throne_side_rooms() -> void:
	var names := ["ABANDONED CHECKPOINT","MONITORING POST","FAILED QUARANTINE",
		"OVERRUN STORES","ROOT GALLERY","SPORE NURSERY","THRONE APPROACH"]
	var route: Array[int] = []
	for id in 9:
		if id != Expedition.ENTRY and id != expedition.exit_room:
			route.append(id)
	var distance := {}
	for id in route:
		distance[id] = expedition.paths.get_id_path(id,expedition.exit_room).size()
	route.sort_custom(func(a: int,b: int): return a < b if distance[a] == distance[b] else distance[a] > distance[b])
	var steel := material(Color("#615564"),0,true,"breach_wall")
	var dark := material(Color("#362d3d"))
	var bark := material(Color("#765461"),.05,false,"fungus")
	var ember := material(Color("#dd806e"),.55)
	var cold := material(Color("#8ba9c1"),.46)
	var cover := Vector3(1.24,.68,.92)
	var footprints: Array[Rect2] = []
	for rank in route.size():
		var id := route[rank]
		var center := expedition.center(id)
		breach_sign(center+Vector3(3.20,2.37,-4.57),names[rank],"THRONE SECTOR  /  %02d" % id,0.0)
		var bench := center+Vector3(-3.39,0,2.91)
		block(bench+Vector3.UP*cover.y*.5,cover,Color("#50444f"),"breach_wall")
		footprints.append(Rect2(Vector2(bench.x-cover.x*.5,bench.z-cover.z*.5),Vector2(cover.x,cover.z)))
		add_contact_shadow(bench+Vector3.UP*.019,Vector2(1.58,1.37),room)
		decor_box(bench+Vector3.UP*.70,Vector3(1.30,.064,.97),steel)
		if rank < 3:
			decor_box(bench+Vector3(-.21,1.08,-.23),Vector3(.72,.61,.14),dark,.14)
			decor_box(bench+Vector3(-.21,1.11,-.145),Vector3(.54,.34,.025),cold,.14)
			for z in [-3.37,-2.51]:
				decor_box(center+Vector3(-4.52,1.35,z),Vector3(.27,1.73,.62),steel)
				decor_box(center+Vector3(-4.37,1.62,z),Vector3(.049,.09,.31),ember)
		else:
			var root_feature := Node3D.new()
			root_feature.position = center+Vector3(-4.07,1.63,-2.76)
			root_feature.rotation.y = PI
			room.add_child(root_feature)
			add_prop_art(root_feature,"throne_root")
			for z in [-3.18,-2.76,-2.34]:
				decor_box(center+Vector3(-4.52,1.1,z),Vector3(.20,1.80,.13),steel)
		for i in range(2+rank):
			var t := float(i)/maxf(1.0,1.0+rank)
			decor_ball(bench+Vector3(-.54+t*1.09,.76+t*.32,-.36+sin(i*.91)*.15),
				bark,Vector3(.27,.11+.10*t,.22))
			if rank >= 4 and i%2==0:
				decor_ball(bench+Vector3(-.54+t*1.09,.85+t*.34,-.34),
					ember,Vector3(.09,.093,.078))
	room.set_meta("side_room_cover",footprints)

func build_furnace_lane(lane: int, center: Vector3, entry: Dictionary) -> void:
	var nozzle := Node3D.new()
	nozzle.position = center+Vector3(0,.49,-1.46)
	room.add_child(nozzle)
	add_prop_art(nozzle,"furnace_nozzle")
	add_contact_shadow(Vector3(center.x,.020,nozzle.position.z),Vector2(1.34,1.30),room)
	var badge := Label3D.new()
	badge.position = Vector3(0,.51,.37)
	badge.font_size = 26
	badge.pixel_size = .008
	badge.outline_size = 4
	nozzle.add_child(badge)
	entry.badge = badge
	entry.lane_name = "A" if lane == 0 else "B"
	var status := material(Color("#674b49"),.85).duplicate() as StandardMaterial3D
	entry.status = status
	for x in [-1.0,1.0]:
		for z in [-1.09,1.09]:
			cube(room,center+Vector3(x,.033,z),Vector3(.28,.060,.16),status)
	var iris := Node3D.new()
	iris.position = Vector3(0,0,.38)
	nozzle.add_child(iris)
	add_prop_art(iris,"furnace_iris")
	entry.iris = iris
	entry.hiss = spatial_effect(nozzle,Vector3(0,0,.32),"furnace_surge",-9,10.0)
	entry.previous = -1
	refresh_furnace_lane(entry,0,false)

func refresh_furnace_lane(entry: Dictionary, state: int, audible: bool) -> void:
	if entry.previous == state:
		return
	entry.previous = state
	var status: StandardMaterial3D = entry.status
	var tint: Color = [Color("#674b49"),GOLD,Color("#ff543b")][state]
	status.albedo_color = tint
	status.emission = tint
	var badge: Label3D = entry.badge
	badge.text = "%s  /  %s" % [entry.lane_name,["IDLE","PRESSURE","SURGE"][state]]
	badge.modulate = Color("#c5a5a0") if state == 0 else tint
	var iris: Node3D = entry.iris
	iris.visible = state != 2
	iris.rotation.z = PI*.25 if state == 1 and not reduced_motion() else 0.0
	var hiss: AudioStreamPlayer3D = entry.hiss
	if state == 0:
		hiss.stop()
	elif state == 1 and audible and DisplayServer.get_name() != "headless":
		hiss.play()

func dress_culture_lab() -> void:
	var steel := material(Color("#657b86"),0,true,"breach_wall")
	var porcelain := material(Color("#9eacaa"),0,true,"breach_wall")
	var deck := material(Color("#738a8b"),0,true,"breach_floor")
	var glass := material(Color("#b8ded8")).duplicate() as StandardMaterial3D
	glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	glass.albedo_color.a = .22
	glass.roughness = .22
	var sleeve := CylinderMesh.new()
	sleeve.top_radius = .245
	sleeve.bottom_radius = .245
	sleeve.height = .85
	sleeve.radial_segments = 16
	var dark := material(Color("#27313e"))
	var blue := material(Color("#88cae9"),.80)
	for side in [-1,1]:
		for z in [-3.62,-2.58,2.55,3.70]:
			decor_box(Vector3(side*4.40,1.73,z),Vector3(.41,3.43,.22),steel)
			decor_box(Vector3(side*4.17,2.57,z),Vector3(.073,.69,.15),blue)
		for y in [.38,2.95]:
			decor_box(Vector3(side*4.57,y,0),Vector3(.14,.18,8.25),dark)
		for z in [-3.42,3.40]:
			decor_box(Vector3(side*4.64,1.49,z),Vector3(.12,2.47,1.31),porcelain)
			decor_box(Vector3(side*4.50,.66,z),Vector3(.18,.22,1.11),steel)
		for z in [-3.38,-2.25,2.25,3.38]:
			decor_box(Vector3(side*.65,.023,z),Vector3(.12,.024,.80),blue)
		decor_box(Vector3(side*.88,.016,3.1),Vector3(.32,.020,2.23),deck)
		var x: float = side*1.75
		decor_box(Vector3(x,1.84,5),Vector3(.22,3.65,.49),steel)
		decor_box(Vector3(x,2.61,5.28),Vector3(.11,.57,.05),blue)
		for z in [-3.50,3.53]:
			var cradle := Node3D.new()
			cradle.position = Vector3(side*4.22,1.26,z)
			room.add_child(cradle)
			add_prop_art(cradle,"culture_cassette")
			mesh(cradle,sleeve,Vector3.ZERO,glass)
			decor_box(cradle.position+Vector3(0,-.61,0),Vector3(.76,.10,.75),steel)
			add_contact_shadow(Vector3(side*4.20,.024,z),Vector2(.98,1.06),room)
	decor_box(Vector3(0,3.57,5),Vector3(3.66,.22,.49),steel)
	breach_sign(Vector3(0,3.16,5.29),"03 / THE CULTURE","CONTROLLED EXPOSURE LAB",0.0)
	breach_sign(Vector3(-3.04,2.02,4.61),"A / VENTILATION","SHOOT VENT TO SEAL LANE",PI)
	breach_sign(Vector3(3.04,2.02,4.61),"B / VENTILATION","SHOOT VENT TO SEAL LANE",PI)
	ceiling_pool(Vector3(0,3.02,8.12),Color("#9adafb"),2.22,5.1)
	ceiling_pool(Vector3(0,3.03,-1.15),Color("#a9e5d5"),2.16,4.85)
	ceiling_pool(Vector3(-3.65,2.98,2.55),Color("#96dce9"),1.45,3.45)

func dress_culture_side_rooms() -> void:
	var labels := {0:"COLD ARCHIVE",1:"DIAGNOSTICS",2:"ISOLATION STORES",
		3:"CULTURE PREP",5:"MICROSCOPY",6:"DECON LOCKER",8:"OBSERVATION CELL"}
	var ceramic := material(Color("#9caeab"),0,true,"breach_wall")
	var steel := material(Color("#647a83"),0,true,"breach_wall")
	var dark := material(Color("#334652"))
	var cool := material(Color("#92e3dc"),.66)
	var amber := material(Color("#e4b87e"),.49)
	var glass := material(Color("#c8ede2")).duplicate() as StandardMaterial3D
	glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	glass.albedo_color.a = .24
	glass.roughness = .22
	var sleeve := CylinderMesh.new()
	sleeve.top_radius = .242
	sleeve.bottom_radius = .242
	sleeve.height = .81
	sleeve.radial_segments = 16
	var cover := Vector3(1.30,.74,.94)
	var footprints: Array[Rect2] = []
	for id in labels:
		var center := expedition.center(id)
		breach_sign(center+Vector3(3.21,2.37,-4.57),labels[id],"THE CULTURE  /  %02d" % id,0.0)
		var station := center+Vector3(-3.39,0,2.91)
		block(station+Vector3.UP*cover.y*.5,cover,Color("#576b72"),"breach_wall")
		footprints.append(Rect2(Vector2(station.x-cover.x*.5,station.z-cover.z*.5),Vector2(cover.x,cover.z)))
		decor_box(station+Vector3.UP*.756,Vector3(1.34,.068,.98),ceramic)
		add_contact_shadow(station+Vector3.UP*.019,Vector2(1.56,1.25),room)
		match id:
			0,3: # Sample archive and prep tables with sealed cultures.
				for x in [-.31,.31]:
					var cassette := Node3D.new()
					cassette.position = station+Vector3(x,1.28,0)
					room.add_child(cassette)
					add_prop_art(cassette,"culture_cassette")
					mesh(cassette,sleeve,Vector3.ZERO,glass)
				decor_box(station+Vector3(0,2.01,0),Vector3(1.20,.072,.31),cool)
				for z in [-3.55,-2.65,-1.86]:
					decor_box(center+Vector3(-4.44,1.20,z),Vector3(.47,1.92,.71),steel)
					decor_box(center+Vector3(-4.19,1.53,z),Vector3(.043,.08,.33),cool)
			1,5: # Instrument consoles and recording racks.
				var display := QuadMesh.new()
				display.size = Vector2(.68,.34)
				var panel := station+Vector3(-.22,1.24,-.16)
				decor_box(panel,Vector3(.74,.42,.13),dark,.12)
				mesh(room,display,panel+Vector3(0,0,.083),atmosphere_art.terminal_screen()).rotation.x = .12
				for x in [-.38,-.20,-.02,.16]:
					decor_box(station+Vector3(x,.817,.21),Vector3(.14,.044,.15),steel)
				var sample := Node3D.new()
				sample.position = station+Vector3(.46,1.10,-.12)
				room.add_child(sample)
				add_prop_art(sample,"sample")
			2,6: # Storage lockers and decon drains leave the existing cache reachable.
				for z in [-3.57,-2.82,-2.07]:
					var locker := center+Vector3(-4.50,1.09,z)
					decor_box(locker,Vector3(.42,2.08,.63),ceramic)
					decor_box(locker+Vector3(.23,.13,0),Vector3(.050,.73,.49),steel)
					decor_box(locker+Vector3(.26,.11,-.13),Vector3(.041,.09,.10),amber)
				for slot in 7:
					decor_box(station+Vector3(-.44+slot*.145,.023,-.91),Vector3(.058,.031,.56),dark)
				decor_box(station+Vector3(0,.83,-.27),Vector3(1.13,.087,.23),amber if id==2 else cool)
			8: # Reinforced observation cell faces inward, clear of the main aisle.
				for y in [1.14,2.08]:
					decor_box(center+Vector3(-4.58,y,-2.71),Vector3(.15,.09,1.87),steel)
				for z in [-3.43,-2.69,-1.95]:
					decor_box(center+Vector3(-4.55,1.60,z),Vector3(.14,.96,.087),steel)
					decor_box(center+Vector3(-4.58,1.61,z+.26),Vector3(.055,.83,.43),cool)
				for z in [-.30,.30]:
					decor_box(station+Vector3(0,.86,z),Vector3(1.19,.13,.10),dark)
	room.set_meta("side_room_cover",footprints)

func build_culture_vent(lane: int, at: Vector3, entry: Dictionary) -> void:
	var fixture := Node3D.new()
	fixture.position = at
	room.add_child(fixture)
	add_prop_art(fixture,"culture_vent")
	var duct := material(Color("#68818a"),0,true,"breach_wall")
	var sleeve := material(Color("#32404c"))
	decor_box(at+Vector3(0,.95,-.17),Vector3(.38,1.29,.31),duct)
	for y in [.48,.81,1.14,1.40]:
		decor_box(at+Vector3(0,y,-.13),Vector3(.45,.087,.37),sleeve)
	var badge := Label3D.new()
	badge.position = Vector3(0,.43,.19)
	badge.rotation.y = 0.0
	badge.font_size = 25
	badge.pixel_size = .008
	badge.outline_size = 4
	badge.no_depth_test = false
	fixture.add_child(badge)
	entry.badge = badge
	var shutter := Node3D.new()
	fixture.add_child(shutter)
	add_prop_art(shutter,"culture_shutter")
	entry.shutter = shutter
	var status := material(TEAL,.9).duplicate() as StandardMaterial3D
	entry.status = status
	for side in [-1,1]:
		cube(fixture,Vector3(side*.27,.25,.21),Vector3(.062,.085,.045),status)
	for side in [-1,1]:
		for z in [-.96,.96]:
			cube(room,entry.at+Vector3(side*1.11,.038,z),Vector3(.17,.043,.29),status)
	entry.hiss = spatial_effect(fixture,Vector3(0,0,.26),"pressure",-9,9.0)
	entry.previous = -1
	if (culture_sealed & (1 << lane)) == 0:
		var vent := StaticBody3D.new()
		vent.collision_layer = 4
		vent.collision_mask = 0
		vent.position = at
		vent.set_meta("culture_vent",lane)
		room.add_child(vent)
		var collision := CollisionShape3D.new()
		var shape := BoxShape3D.new()
		shape.size = Vector3(.62,.62,.36)
		collision.shape = shape
		vent.add_child(collision)
	refresh_culture_vent(entry,lane,0,false)

func refresh_culture_vent(entry: Dictionary, lane: int, state: int, audible: bool) -> void:
	var sealed := (culture_sealed & (1 << lane)) != 0
	var visual := 3 if sealed else state
	if entry.previous == visual:
		return
	entry.previous = visual
	var status: StandardMaterial3D = entry.status
	var tint: Color = [Color("#9de975"),GOLD,Color("#ff7c58"),TEAL][visual]
	status.albedo_color = tint
	status.emission = tint
	var badge: Label3D = entry.badge
	badge.text = ("%s  /  " % ("A" if lane == 0 else "B")) + ["READY","PRESSURE","PURGING","SEALED"][visual]
	badge.modulate = tint
	entry.shutter.visible = sealed
	var hiss: AudioStreamPlayer3D = entry.hiss
	if sealed:
		hiss.stop()
	elif state == 2 and audible and DisplayServer.get_name() != "headless":
		hiss.play()


func chamber_lane_state(lane: int, boss_tier := 0, boss_phase_since := 0.0) -> int:
	if stage == MAX_STAGE:
		if boss_tier == 0:
			return 0
		var tempo := 1.35 if boss_tier == 2 else 1.0
		var elapsed := (active_time - boss_phase_since) * tempo - lane * 2.6
		if elapsed < 0.0:
			return 0
		var beat := fposmod(elapsed, 5.2)
		if beat >= 1.65 and beat < 2.6:
			return 1
		return 2 if beat >= 2.6 and beat < 4.32 else 0
	if stage == 3 and (culture_sealed & (1 << lane)) != 0:
		return 0
	var cycle := fposmod(active_time + lane * 2.6, 5.2)
	if cycle >= 1.75 and cycle < 2.6:
		return 1
	return 2 if cycle >= 2.6 and cycle < 4.2 else 0

func tick_hub_chamber() -> void:
	var boss_tier := 0
	var phase_since := 0.0
	if stage == MAX_STAGE:
		for enemy in enemies:
			if enemy.get_meta("type") == 3:
				boss_tier = int(enemy.get_meta("boss_phase", 0))
				phase_since = float(enemy.get_meta("boss_phase_since", 0.0))
				break
		if room.has_meta("throne_phase_light") and room.get_meta("throne_tier") != boss_tier:
			room.set_meta("throne_tier",boss_tier)
			var light: OmniLight3D = room.get_meta("throne_phase_light")
			light.light_color = [Color("#ac8fec"),Color("#ef88b9"),Color("#ff795f")][boss_tier]
			var seal: ShaderMaterial = room.get_meta("throne_seal")
			seal.set_shader_parameter("tint",light.light_color)
	for lane in chamber_lanes.size():
		var entry: Dictionary = chamber_lanes[lane]
		var state := chamber_lane_state(lane, boss_tier, phase_since)
		if entry.get("render_state",-1) != state:
			entry.render_state = state
			entry.warning.visible = state == 1
			entry.danger.visible = state == 2
			entry.plume.visible = state == 2
			if stage == MAX_STAGE:
				entry.throne_roots.visible = state == 2
		if stage == 3:
			refresh_culture_vent(entry,lane,state,phase=="combat")
		elif stage == 4:
			refresh_furnace_lane(entry,state,phase=="combat")
		var offset: Vector3 = player.position - entry.at
		if state == 2 and absf(offset.x) < 1.23 and absf(offset.z) < 1.2:
			damage_player(10 if stage == 3 else 14, entry.at)

func update_chamber_motion() -> void:
	var motion := not reduced_motion()
	for lane in chamber_lanes.size():
		var entry: Dictionary = chamber_lanes[lane]
		if entry.get("render_state",0) != 2 or player.position.distance_squared_to(entry.at) >= 18.0*18.0:
			continue
		if stage == MAX_STAGE:
			entry.throne_roots.scale.y = .84+sin(active_time*5.0+lane)*.12 if motion else 1.0
		if motion:
			(entry.vapor as ShaderMaterial).set_shader_parameter("clock", active_time + lane * 3.1)
			(entry.live_floor as ShaderMaterial).set_shader_parameter("clock", active_time)

func seal_culture_vent(vent: StaticBody3D) -> void:
	var lane := int(vent.get_meta("culture_vent"))
	culture_sealed |= 1 << lane
	var entry: Dictionary = chamber_lanes[lane]
	entry.warning.hide()
	entry.danger.hide()
	entry.plume.hide()
	refresh_culture_vent(entry,lane,0,false)
	burst(vent.position, TEAL, 12)
	discard(vent)
	show_banner("VENT %s SEALED  /  LANE CLEAR" % ("A" if lane == 0 else "B"))
	sfx("break")
	OnKlavGame.checkpoint()

func dress_sporeworks_side_rooms() -> void:
	var labels := {0:"SPARES STORE",1:"TRANSFER INSPECTION",2:"WASHDOWN",
		3:"PRESSURE CONTROL",5:"MOTOR SERVICE",6:"EMPTY CARRIER RETURN",8:"SPECIMEN HOLD"}
	var metal := material(Color("#68757d"),0,true,"breach_wall")
	var rubber := material(Color("#2e363c"))
	var bronze := material(Color("#907363"))
	var amber := material(Color("#eab267"),.66)
	var cyan := material(Color("#87d9c9"),.53)
	var cover := Vector3(1.26,.70,.94)
	var footprints: Array[Rect2] = []
	for id in labels:
		var center := expedition.center(id)
		breach_sign(center+Vector3(3.18,2.37,-4.57),labels[id],"SPOREWORKS  /  %02d" % id,0.0)
		var bench := center+Vector3(-3.39,0,2.91)
		block(bench+Vector3.UP*cover.y*.5,cover,Color("#515b64"),"breach_wall")
		footprints.append(Rect2(Vector2(bench.x-cover.x*.5,bench.z-cover.z*.5),Vector2(cover.x,cover.z)))
		decor_box(bench+Vector3.UP*.715,Vector3(1.33,.065,1.0),metal)
		add_contact_shadow(bench+Vector3.UP*.019,Vector2(1.58,1.35),room)
		match id:
			0,6: # Spare parts and empty return pallets, away from the cache in room 00.
				for offset in [-.37,.37]:
					decor_box(bench+Vector3(offset,.78,0),Vector3(.49,.065,.72),rubber)
					for rib in [-.22,0.0,.22]:
						decor_box(bench+Vector3(offset,.825,rib),Vector3(.41,.056,.085),metal)
				for z in [-3.6,-2.7,-1.85]:
					decor_box(center+Vector3(-4.48,1.02,z),Vector3(.37,1.91,.71),metal)
					decor_box(center+Vector3(-4.275,1.23,z),Vector3(.047,.082,.26),amber)
			1,8: # Inspection and live specimen holding.
				var specimen := Node3D.new()
				specimen.position = bench+Vector3.UP*.75
				room.add_child(specimen)
				add_prop_art(specimen,"conveyor_specimen")
				decor_box(bench+Vector3(-.46,1.68,0),Vector3(.067,1.31,.095),metal)
				decor_box(bench+Vector3(0,2.31,0),Vector3(.99,.08,.21),cyan)
				for z in [-3.50,-2.57]:
					decor_box(center+Vector3(-4.54,1.45,z),Vector3(.20,1.10,.78),rubber)
					for strip in 3:
						decor_box(center+Vector3(-4.415,1.29+strip*.16,z),Vector3(.07,.085,.60),metal)
			2: # Washdown station with a recessed drain and colored pipe labels.
				for z in [-.32,.32]:
					decor_box(bench+Vector3(0,.82,z),Vector3(1.12,.14,.085),metal)
				decor_box(bench+Vector3(.52,.94,0),Vector3(.07,.36,.51),cyan)
				for rib in 7:
					decor_box(bench+Vector3(-.43+rib*.145,.023,-.91),Vector3(.069,.028,.57),rubber)
				for y in [1.3,1.64]:
					decor_box(center+Vector3(-4.48,y,-2.51),Vector3(.19,.19,2.55),bronze)
					decor_box(center+Vector3(-4.345,y,-2.72),Vector3(.12,.13,.40),cyan)
			3,5: # Manifold and drive assemblies reuse the same parts as the hub.
				var machinery := Node3D.new()
				machinery.position = bench+Vector3.UP*.96
				room.add_child(machinery)
				add_prop_art(machinery,"pressure_manifold" if id == 3 else "conveyor_drive")
				for y in [1.43,1.78,2.11]:
					decor_box(center+Vector3(-4.51,y,-2.47),Vector3(.18,.19,2.44),bronze)
					for z in [-3.44,-2.48,-1.53]:
						decor_box(center+Vector3(-4.41,y,z),Vector3(.22,.23,.12),metal)
				decor_box(bench+Vector3(0,1.78,-.37),Vector3(.61,.083,.17),amber)
	room.set_meta("side_room_cover",footprints)

func build_sporeworks_line() -> void:
	dress_sporeworks()
	var transfer := expedition.center(SPOREWORKS_TRANSFER_ROOM)+SPOREWORKS_CONVEYOR_OFFSET
	var pressure := expedition.center(SPOREWORKS_PRESSURE_ROOM)
	var quarantine := expedition.center(SPOREWORKS_POD_ROOM)
	var footprints: Array[Rect2] = []
	var conveyor := Node3D.new()
	conveyor.position = transfer
	room.add_child(conveyor)
	block(transfer+Vector3(0,.36,0),Vector3(1.12,.72,3.4),Color("#43505a"),"breach_wall")
	footprints.append(Rect2(Vector2(transfer.x-.56,transfer.z-1.7),Vector2(1.12,3.4)))
	add_contact_shadow(transfer+Vector3(0,.019,0),Vector2(1.75,4.18),room)
	for end in [-1,1]:
		var drive := Node3D.new()
		drive.position = Vector3(0,.38,end*1.73)
		drive.rotation.y = 0.0 if end == 1 else PI
		conveyor.add_child(drive)
		add_prop_art(drive,"conveyor_drive")
	var rail := material(Color("#829499"),0,false)
	var rubber := material(Color("#252e32"))
	var safety := material(Color("#efa754"),.46)
	for x in [-.51,.51]:
		decor_box(transfer+Vector3(x,.75,0),Vector3(.10,.11,3.55),rail)
		decor_box(transfer+Vector3(x,.54,0),Vector3(.13,.14,3.45),rubber)
		for z in [-1.36,-.68,0.0,.68,1.36]:
			decor_box(transfer+Vector3(x,.86,z),Vector3(.15,.12,.13),rail)
			decor_box(transfer+Vector3(x,.585,z+.18),Vector3(.14,.052,.19),safety,.23)
	for side in [-1,1]:
		decor_box(transfer+Vector3(0,.76,side*1.65),Vector3(1.15,.16,.32),rail)
		decor_box(transfer+Vector3(0,.84,side*1.65),Vector3(.91,.021,.22),safety)
		decor_box(transfer+Vector3(0,.35,side*1.68),Vector3(.72,.46,.15),rubber)
	sporeworks_belt = Node3D.new()
	sporeworks_belt.position.y = .747
	conveyor.add_child(sporeworks_belt)
	for i in 10:
		cube(sporeworks_belt,Vector3(0,0,-1.49+i*.32),Vector3(.89,.037,.09),rail)
	var sleeve := CylinderMesh.new()
	sleeve.top_radius = .218
	sleeve.bottom_radius = .218
	sleeve.height = .34
	sleeve.radial_segments = 12
	var glass := material(Color("#c5e4dc")).duplicate() as StandardMaterial3D
	glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	glass.albedo_color.a = .23
	glass.roughness = .18
	for i in 3:
		var cargo := Node3D.new()
		conveyor.add_child(cargo)
		cargo.position = Vector3(0,.79,-1.02+i*1.02)
		add_prop_art(cargo,"conveyor_specimen")
		mesh(cargo,sleeve,Vector3(0,.387,0),glass)
		sporeworks_cargo.append(cargo)
	sporeworks_drive = spatial_effect(room,transfer+Vector3(0,.62,-1.35),"drive",-18,8.5)
	sporeworks_valve = spatial_effect(room,pressure+Vector3(-2.47,.16,.58),"pressure",-12,10.0)
	build_sporeworks_lane_signals(pressure,quarantine)
	if (sporeworks_cleared & SPOREWORKS_TENDRIL) == 0:
		sporeworks_roots = Node3D.new()
		sporeworks_roots.position = pressure+Vector3(-2.47, 0, .58)
		room.add_child(sporeworks_roots)
		var slot := QuadMesh.new()
		slot.size = Vector2(2.4,1.4)
		sporeworks_vent = atmosphere_art.grate(GOLD)
		sporeworks_warning = mesh(room,slot,pressure+Vector3(-2.47,.034,.58),sporeworks_vent)
		sporeworks_warning.rotation.x = -PI*.5
		sporeworks_warning.hide()
		for z in [-.10,1.26]:
			decor_box(pressure+Vector3(-2.47,.041,z),Vector3(2.48,.08,.105),rail)
			for i in 6:
				decor_box(pressure+Vector3(-3.47+i*.40,.088,z),Vector3(.16,.027,.11),safety,.22)
		add_prop_art(sporeworks_roots,"pressure_roots")
		build_sporeworks_target(pressure+Vector3(-2.47,1.40,-.18),Vector3(.64,.66,.45),SPOREWORKS_TENDRIL)
		footprints.append(Rect2(Vector2(pressure.x-2.79,pressure.z-.405),Vector2(.64,.45)))
	if (sporeworks_cleared & SPOREWORKS_POD) == 0:
		build_sporeworks_target(quarantine+Vector3(2.52,.95,.58),Vector3(2.24,1.9,1.02),SPOREWORKS_POD)
		footprints.append(Rect2(Vector2(quarantine.x+1.40,quarantine.z+.07),Vector2(2.24,1.02)))
	room.set_meta("sporeworks_cover",footprints)
	build_sporeworks_room_character(conveyor,pressure,quarantine)
	add_contact_shadow(quarantine+Vector3(2.49,.024,.66),Vector2(2.35,1.8),room)

func build_sporeworks_room_character(conveyor: Node3D, pressure: Vector3, quarantine: Vector3) -> void:
	var steel := material(Color("#76818a"),0,true,"breach_wall")
	var black := material(Color("#2c323a"))
	var copper := material(Color("#936b55"))
	var glass := material(Color("#b2dbdf")).duplicate() as StandardMaterial3D
	glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	glass.albedo_color.a = .24
	var amber := material(Color("#ffd18a"),.76)
	var cyan := material(Color("#7dcad3"),.70)
	# The inspection booth sits on the existing side-room cover footprint.
	var booth := expedition.center(SPOREWORKS_TRANSFER_ROOM)+Vector3(-3.39,0,2.91)
	for z in [-.45,.45]:
		decor_box(booth+Vector3(.61,1.43,z),Vector3(.078,1.43,.076),steel)
	for y in [.86,2.12]:
		decor_box(booth+Vector3(.61,y,0),Vector3(.09,.084,.95),steel)
	cube(room,booth+Vector3(.609,1.49,0),Vector3(.025,1.18,.84),glass)
	decor_box(booth+Vector3(.50,.83,0),Vector3(.23,.074,.90),cyan)
	decor_box(booth+Vector3(0,2.16,0),Vector3(1.24,.10,.91),steel)
	var scanner := Node3D.new()
	scanner.position.y = 2.75
	conveyor.add_child(scanner)
	cube(scanner,Vector3.ZERO,Vector3(.93,.15,.24),black)
	cube(scanner,Vector3(0,-.082,0),Vector3(.74,.035,.083),amber)
	var lamp := SpotLight3D.new()
	lamp.rotation.x = -PI*.5
	lamp.light_color = Color("#ffd391")
	lamp.light_energy = .95
	lamp.spot_range = 3.5
	lamp.spot_angle = 24.0
	scanner.add_child(lamp)
	room.set_meta("transfer_scanner",scanner)
	room.set_meta("transfer_lamp",lamp)
	# Pipework feeds the root trench; the gauge tracks the same hazard clock.
	for y in [.81,1.12]:
		decor_box(pressure+Vector3(-3.77,y,-.22),Vector3(2.05,.14,.14),copper)
		for x in [-4.37,-3.55,-2.83]:
			decor_box(pressure+Vector3(x,y,-.22),Vector3(.08,.21,.21),steel)
	var gauge := Node3D.new()
	gauge.position = pressure+Vector3(-2.49,2.05,.05)
	room.add_child(gauge)
	var dial := CylinderMesh.new()
	dial.top_radius = .29
	dial.bottom_radius = .29
	dial.height = .087
	dial.radial_segments = 20
	mesh(gauge,dial,Vector3.ZERO,black).rotation.x = PI*.5
	for i in range(7):
		var angle := deg_to_rad(-105.0+i*35.0)
		var tick := cube(gauge,Vector3(-sin(angle)*.23,cos(angle)*.23,.051),
			Vector3(.021,.055,.013),amber if i >= 4 else steel)
		tick.rotation.z = angle
	var needle := Node3D.new()
	needle.position.z = .07
	gauge.add_child(needle)
	cube(needle,Vector3.UP*.095,Vector3(.018,.188,.016),amber)
	room.set_meta("pressure_needle",needle)
	update_sporeworks_pressure_gauge(0.0)
	# The cage is split open toward the aisle; its damaged frame stays after the pod bursts.
	var cage := quarantine+Vector3(2.53,0,.59)
	for x in [-1.15,1.15]:
		for z in [-.49,.50]:
			decor_box(cage+Vector3(x,1.00,z),Vector3(.078,2.0,.078),steel,.08 if x>0 else -.08)
	for y in [.20,1.94]:
		decor_box(cage+Vector3(0,y,-.51),Vector3(2.34,.08,.085),steel)
	for x in [-.78,-.40,0.0,.40,.78]:
		decor_box(cage+Vector3(x,1.03,-.51),Vector3(.057,1.79,.068),black)
	var spill := material(Color("#735371"),.13,false,"fungus")
	for i in range(6):
		decor_ball(cage+Vector3(-.82-i*.125,.028,.22+i*.20),spill,Vector3(.29,.027,.14))
	decor_box(cage+Vector3(-.81,.094,.69),Vector3(.75,.079,.091),steel,.24)

func update_sporeworks_pressure_gauge(cycle: float) -> void:
	var needle: Node3D = room.get_meta("pressure_needle")
	var stopped := (sporeworks_cleared & SPOREWORKS_TENDRIL) != 0
	var level := smoothstep(1.28,3.05,cycle)*(1.0-smoothstep(4.20,4.72,cycle))
	if stopped:
		level = 0.0
	elif reduced_motion():
		level = 1.0 if cycle >= 2.25 and cycle < 4.28 else 0.0
	needle.rotation.z = lerpf(1.82,-1.82,level)

func build_sporeworks_lane_signals(pressure: Vector3, quarantine: Vector3) -> void:
	var carrier := material(Color("#4c565d"))
	var relay := Node3D.new()
	relay.position = pressure+Vector3(-2.47,.32,-.25)
	room.add_child(relay)
	add_prop_art(relay,"pressure_manifold")
	for side in [-1,1]:
		var tint := material(GOLD,.86).duplicate() as StandardMaterial3D
		var key := "pressure_signal" if side == -1 else "pod_signal"
		room.set_meta(key,tint)
		for z in [-.14,1.33]:
			var at := (pressure if side == -1 else quarantine)+Vector3(side*2.48,.046,z)
			decor_box(at,Vector3(2.12,.084,.15),carrier)
			for slot in [-.83,-.63,.63,.83]:
				cube(room,at+Vector3(slot,.048,0),Vector3(.13,.023,.12),tint)
	update_sporeworks_lane_signals(false)

func update_sporeworks_lane_signals(warned: bool) -> void:
	var left: StandardMaterial3D = room.get_meta("pressure_signal")
	var right: StandardMaterial3D = room.get_meta("pod_signal")
	var left_color := TEAL if sporeworks_cleared & SPOREWORKS_TENDRIL else (GOLD if warned else Color("#9d6950"))
	var right_color := TEAL if sporeworks_cleared & SPOREWORKS_POD else Color("#f1a4cf")
	left.albedo_color = left_color
	left.emission = left_color
	right.albedo_color = right_color
	right.emission = right_color

func dress_sporeworks() -> void:
	var metal := material(Color("#68757d"),0,true,"breach_wall")
	var black := material(Color("#27323a"))
	var deck := material(Color("#746d60"),0,true,"breach_floor")
	var cyan := material(Color("#7adacc"),.7)
	var amber := material(Color("#efae60"),.78)
	for side in [-1,1]:
		for z in [-3.45,-2.35,2.35,3.45]:
			decor_box(Vector3(side*4.42,1.55,z),Vector3(.39,3.1,.25),metal)
			decor_box(Vector3(side*4.17,2.63,z),Vector3(.12,.38,.28),amber)
		for y in [2.65,2.94]:
			decor_box(Vector3(side*4.61,y,0),Vector3(.17,.17,8.1),black)
			for z in [-3.84,-2.55,2.55,3.84]:
				decor_box(Vector3(side*4.54,y,z),Vector3(.26,.27,.14),metal)
		for z in [-3.1,3.1]:
			decor_box(Vector3(side*4.56,1.24,z),Vector3(.19,1.62,1.32),black)
			for louver in 5:
				decor_box(Vector3(side*4.43,.69+louver*.23,z),Vector3(.09,.073,1.16),metal)
		for z in [-3.68,-2.81,2.83,3.70]:
			decor_box(Vector3(side*.98,.015,z),Vector3(.63,.024,.79),deck)
		for z in [2.8,4.36]:
			decor_box(Vector3(side*1.15,.049,z),Vector3(.12,.041,1.28),cyan)
		var entry_x: float = side*1.77
		decor_box(Vector3(entry_x,1.83,5),Vector3(.24,3.66,.57),metal)
		decor_box(Vector3(entry_x,2.67,5.3),Vector3(.11,.65,.077),cyan)
	decor_box(Vector3(0,3.54,5),Vector3(3.78,.28,.57),metal)
	breach_sign(Vector3(0,3.13,5.37),"02 / SPOREWORKS","LIVE SPECIMEN TRANSFER",0.0)
	breach_sign(Vector3(-3.13,2.12,4.64),"FACILITY DIRECTORY","01 TRANSFER  /  03 PRESSURE",PI)
	breach_sign(Vector3(3.11,2.12,4.64),"CONTAINMENT","08 SPECIMEN HOLD",PI)
	ceiling_pool(Vector3(0,3.02,8.2),Color("#91dfd3"),2.1,5.1)
	ceiling_pool(Vector3(1.98,2.96,-2.48),Color("#c592fd"),2.55,4.9)

func build_sporeworks_target(at: Vector3, extent: Vector3, kind: int) -> void:
	var target := StaticBody3D.new()
	target.position = at
	target.set_meta("sporeworks_target", kind)
	room.add_child(target)
	var collision := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = extent
	collision.shape = shape
	target.add_child(collision)
	var model_name := "rupture_pod" if kind == SPOREWORKS_POD else "pressure_valve"
	add_prop_art(target,model_name)

func hit_sporeworks_target(target: StaticBody3D) -> void:
	var kind: int = target.get_meta("sporeworks_target")
	if kind == SPOREWORKS_TENDRIL and weapon != 0:
		show_banner("AMBER ROOT  /  PIN IT WITH THE NAILGUN")
		return
	if kind == SPOREWORKS_POD and (weapon != 1 or player.position.distance_to(target.position) > 4.2):
		show_banner("SPORE POD  /  USE THE SPOREBREAKER UP CLOSE")
		return
	sporeworks_cleared |= kind
	burst(target.position, GOLD if kind == SPOREWORKS_TENDRIL else PINK, 14)
	discard(target)
	if kind == SPOREWORKS_TENDRIL:
		discard(sporeworks_roots)
		sporeworks_roots = null
		discard(sporeworks_warning)
		sporeworks_warning = null
		if is_instance_valid(sporeworks_valve):
			sporeworks_valve.stop()
		show_banner("ROOT PINNED  /  PRESSURE BAY CLEAR")
	elif enemies.size() < ENEMY_LIMIT:
		var hatch := target.position + Vector3(.28,-target.position.y,-1.08)
		var released := spawn_enemy(4,hatch)
		released.set_meta("alert",true)
		released.set_meta("pursuit_left",PURSUIT_MEMORY)
		burst(hatch+Vector3.UP*.5,PINK,5)
		show_banner("POD BURST  /  SKITTER LOOSE")
	else:
		show_banner("POD BURST  /  SPECIMEN HOLD CLEAR")
	update_sporeworks_lane_signals(sporeworks_pressure_live)
	update_sporeworks_pressure_gauge(fposmod(active_time,SPOREWORKS_CYCLE))
	sfx("break")
	OnKlavGame.checkpoint()

func build_sporeworks_ventilation() -> void:
	var deck := material(Color("#2c3b42"),0,true,"breach_floor")
	var rail := material(Color("#7a8079"))
	decor_box(SPOREWORKS_STEAM+Vector3.UP*.024,Vector3(2.9,.048,1.03),deck)
	for i in 13:
		decor_box(SPOREWORKS_STEAM+Vector3(-1.32+i*.22,.058,0),Vector3(.072,.04,.86),rail)
	for side in [-1.0,1.0]:
		decor_box(SPOREWORKS_STEAM+Vector3(0,.06,side*.51),Vector3(2.95,.07,.07),material(GOLD,.35))
	var warning := QuadMesh.new()
	warning.size = Vector2(2.96,1.12)
	sporeworks_steam_signal = mesh(room,warning,SPOREWORKS_STEAM+Vector3.UP*.088,atmosphere_art.grate(GOLD))
	sporeworks_steam_signal.rotation.x = -PI*.5
	sporeworks_steam_signal.hide()
	sporeworks_steam = Node3D.new()
	sporeworks_steam.position = SPOREWORKS_STEAM
	room.add_child(sporeworks_steam)
	sporeworks_steam_material = atmosphere_art.vapor()
	sporeworks_steam_material.set_shader_parameter("tint",Color("#e0f3f5"))
	var plume := QuadMesh.new()
	plume.size = Vector2(1.4,2.25)
	for x in [-.87,0.0,.87]:
		mesh(sporeworks_steam,plume,Vector3(x,1.16,0),sporeworks_steam_material)
	mesh(sporeworks_steam,plume,Vector3(0,1.16,0),sporeworks_steam_material).rotation.y = PI*.5
	sporeworks_steam.hide()
	sporeworks_steam_voice = spatial_effect(room,SPOREWORKS_STEAM,"pressure",-14,8.5)
	var panel := breach_sign(Vector3(0,2.79,3.77),"VENTILATION","RELAYS OFFLINE",0.0)
	sporeworks_status = panel.get_child(panel.get_child_count()-1) as Label3D
	sporeworks_route = Node3D.new()
	room.add_child(sporeworks_route)
	var route := expedition.paths.get_id_path(Expedition.ENTRY,expedition.exit_room)
	var guidance := material(TEAL,.72)
	for i in range(1,route.size()):
		var start := expedition.center(route[i-1])
		var direction := (expedition.center(route[i])-start).normalized()
		var across := direction.cross(Vector3.UP)
		for side in [-1.0,1.0]:
			var marker := cube(sporeworks_route,start+direction*4.0+across*side*.18+Vector3.UP*.085,
				Vector3(.075,.021,.38),guidance)
			marker.rotation.y = atan2(direction.x,direction.z)+side*.64
	update_sporeworks_ventilation()


func update_sporeworks_ventilation() -> void:
	var online := objectives_complete()
	sporeworks_route.visible = online
	sporeworks_status.text = "ONLINE / EXTRACTION OPEN" if online else "RELAYS OFFLINE"
	sporeworks_status.modulate = TEAL if online else GOLD
	var lamp: SpotLight3D = room.get_meta("transfer_lamp")
	lamp.light_color = Color("#9deee6") if online else Color("#ffd391")
	lamp.light_energy = 2.05 if online else .95
	if online:
		sporeworks_steam.hide()
		sporeworks_steam_signal.hide()
		sporeworks_steam_live = false
		sporeworks_steam_voice.stop()


func tick_sporeworks_steam() -> void:
	if objectives_complete():
		return
	var cycle := fposmod(active_time+1.2,5.8)
	var warning := cycle >= 2.6 and cycle < 4.85
	var live := cycle >= 3.55 and cycle < 4.85
	sporeworks_steam_signal.visible = warning
	sporeworks_steam.visible = live
	if live:
		if not sporeworks_steam_live and DisplayServer.get_name() != "headless":
			sporeworks_steam_voice.play()
		var offset := player.position-SPOREWORKS_STEAM
		if absf(offset.x) < 1.4 and absf(offset.z) < .53:
			damage_player(10,SPOREWORKS_STEAM)
	sporeworks_steam_live = live


func tick_sporeworks(dt := 0.0) -> void:
	if sporeworks_cargo.is_empty():
		return
	tick_sporeworks_steam()
	if phase != "combat":
		return
	var online := objectives_complete()
	if online and not reduced_motion():
		sporeworks_restart += dt
	if online and not sporeworks_drive.playing and DisplayServer.get_name() != "headless":
		sporeworks_drive.play()
	var pressure := fposmod(active_time,SPOREWORKS_CYCLE)
	var warned := is_instance_valid(sporeworks_roots) and pressure>=2.25 and pressure<4.28
	if warned and not sporeworks_pressure_live and DisplayServer.get_name() != "headless":
		sporeworks_valve.play()
	if sporeworks_pressure_live != warned:
		update_sporeworks_lane_signals(warned)
		if is_instance_valid(sporeworks_warning): sporeworks_warning.visible = warned
	sporeworks_pressure_live = warned
	if not is_instance_valid(sporeworks_roots):
		return
	var offset := player.position - sporeworks_roots.position
	if absf(offset.x) > 1.2 or absf(offset.z) > .7:
		return
	if pressure >= 3.05 and pressure < 4.28:
		damage_player(12, sporeworks_roots.position)

func update_sporeworks_motion() -> void:
	if sporeworks_cargo.is_empty(): return
	var motion := not reduced_motion()
	var at := player.position
	var cycle := fposmod(active_time, SPOREWORKS_CYCLE)
	var needle: Node3D = room.get_meta("pressure_needle")
	if at.distance_squared_to(needle.global_position) < 18.0*18.0:
		update_sporeworks_pressure_gauge(cycle)
	if sporeworks_steam_live and at.distance_squared_to(SPOREWORKS_STEAM) < 144.0:
		sporeworks_steam_material.set_shader_parameter("clock",fposmod(active_time+1.2,5.8) if motion else 0.0)
	if is_instance_valid(sporeworks_roots) and at.distance_squared_to(sporeworks_roots.position) < 18.0*18.0:
		sporeworks_vent.set_shader_parameter("clock",cycle if motion else 0.0)
		var height := smoothstep(2.25, 3.05, cycle) * (1.0 - smoothstep(4.28, 4.8, cycle))
		sporeworks_roots.scale.y = maxf(.12, height) if motion else (1.0 if cycle >= 3.05 and cycle < 4.28 else .25)
	var scanner: Node3D = room.get_meta("transfer_scanner")
	if motion and objectives_complete() and at.distance_squared_to(scanner.global_position) < 18.0*18.0:
		scanner.position.z = sin(sporeworks_restart*1.25)*1.04
		sporeworks_belt.position.z = fposmod(sporeworks_restart*.79,.32) - .16
		for i in sporeworks_cargo.size():
			var track := fposmod(sporeworks_restart*.79+i*1.02,3.06)
			var inset := minf(track,3.06-track)
			var carrier := sporeworks_cargo[i]
			carrier.position.z = track-1.53
			carrier.position.y = .79 - .32*(1.0-smoothstep(0.0,.35,inset))
			carrier.scale.y = lerpf(.58,1.0,smoothstep(0.0,.32,inset))

func add_sector_identity(accent: Color) -> void:
	var hub := expedition.center(4)
	var steel := material(Color("#333943"))
	var trim := material(Color("#84776c"))
	var signal_material := material(accent,.85)
	match stage:
		1: # Breach: broken containment shutter hanging from a ceiling rail.
			for x in [-1.25,1.25]:
				decor_box(hub+Vector3(x,3.28,-2.7),Vector3(.22,.56,1.15),steel)
				decor_box(hub+Vector3(x,2.99,-3.05),Vector3(.35,.14,.85),trim)
			decor_box(hub+Vector3(0,3.19,-2.9),Vector3(2.75,.15,.18),signal_material)
		2: # Sporeworks: twin extraction plenums and exposed intake grilles.
			for x in [-1.0,1.0]:
				decor_box(hub+Vector3(x,3.32,-1.75),Vector3(.72,.30,2.2),steel)
				for z in [-2.25,-1.75,-1.25]:
					decor_box(hub+Vector3(x,3.14,z),Vector3(.56,.07,.08),signal_material)
		3: # Culture: sealed specimen tubes mounted high on a laboratory wall.
			decor_box(hub+Vector3(0,2.85,-3.88),Vector3(3.2,.2,.14),steel)
			for x in [-1.0,0.0,1.0]:
				decor_box(hub+Vector3(x,2.96,-3.74),Vector3(.33,.62,.12),trim)
				decor_box(hub+Vector3(x,2.99,-3.66),Vector3(.18,.37,.06),signal_material)
		4: # Root Furnace: red-hot pressure manifold above the north-side wall.
			for x in [-1.2,0.0,1.2]:
				decor_box(hub+Vector3(x,3.30,-2.75),Vector3(.34,.30,2.1),steel)
				decor_box(hub+Vector3(x,3.13,-2.70),Vector3(.15,.08,1.45),signal_material)
		5: # Cap King: crown-like infected canopy above the central hub.
			for x in [-1.35,-.45,.45,1.35]:
				decor_box(hub+Vector3(x,3.19,-2.7),Vector3(.19,.32,.52),signal_material)
			decor_box(hub+Vector3(0,3.36,-2.7),Vector3(3.1,.14,.7),steel)

# Compact architectural motifs. Centerpieces fit within the middle 3m of
# the 10m room grid and sit at ceiling height / perimeter so they cannot block
# doors, objective markers, player sight lines or enemy paths.
func add_sector_focal(center: Vector3, accent: Color, motif: int) -> void:
	var steel := material(Color("#353c42"))
	var warning := material(Color("#b4a36a"),.3)
	var glow := material(accent,.55)
	match motif:
		0: # decontamination ring and overhead tubing
			for x in [-1.25,1.25]:
				decor_box(center+Vector3(x,3.20,0),Vector3(.13,.25,2.8),steel)
			for z in [-1.2,1.2]:
				decor_box(center+Vector3(0,3.14,z),Vector3(2.5,.12,.15),warning)
		2: # fungal ceiling colony with a restrained luminescent core
			for z in [-.6,0,.6]:
				decor_ball(center+Vector3(0,3.29,z),material(Color("#598d54"),.22),Vector3(.68,.18,.40))
			decor_box(center+Vector3(0,3.32,0),Vector3(.28,.06,.30),glow)
		3: # reinforcing braces and broken warning strip
			for x in [-1.2,1.2]:
				decor_box(center+Vector3(x,3.3,0),Vector3(.17,.23,2.7),steel)
			decor_box(center+Vector3(0,3.31,-1.18),Vector3(2.45,.08,.13),warning)

# Roles respond to location and sector, not just a single uniform random roll.
# All spawns still use the original 13-enemy budget, original types and AI.
func encounter_kind(room_id: int, target_room: bool) -> int:
	var roll := rng.randf()
	if stage >= 2 and room_id != 4:
		if (stage == 2 and room_id == 0) or roll < .18: return RAZOR_MITE
		if stage >= 3 and roll > .82: return LANTERN_WRAITH
	if stage == 1: return 1 if room_id==4 else (4 if roll < .16 else 0)
	if stage == 2:
		return 1 if room_id == 4 or target_room or roll < .38 else (4 if roll < .59 else 0)
	if stage == 3:
		return 2 if target_room and roll < .42 else (1 if roll < .46 else (4 if roll < .70 else 0))
	if stage == 4:
		return 5 if roll < .18 else (2 if roll < .46 else (1 if roll < .74 else 4))
	# Boss sector's satellite rooms mix ranged pressure with swift enemies.
	return 5 if roll < .19 else (1 if roll < .52 else (4 if roll < .76 else 2))

# Shootable spore canisters use only three simple colliders, with no rigid bodies,
# real-time lights, or persistent physics effects. Explosion is resolved once.
func add_spore_canister(at: Vector3) -> void:
	var target := StaticBody3D.new()
	target.collision_layer = 1
	target.collision_mask = 0
	target.position = at
	target.set_meta("spore_canister",true)
	var collision := CollisionShape3D.new()
	var shape := CylinderShape3D.new()
	shape.radius = .38
	shape.height = 1.2
	collision.shape = shape
	collision.position.y = .6
	target.add_child(collision)
	add_prop_art(target, "canister")
	var beacon := mesh(target,ball(),Vector3(0,.61,0),material(Color("#ff262b"),2.1),Vector3(.92,.37,.92))
	beacon.name = "BlastBeacon"
	room.add_child(target)
	canisters.append(target)

# Explosive damage respects solid cover. Unlike a simple radius check, this
# prevents unfair hits through sealed room walls or heavy machinery.
func blast_visible(origin: Vector3, destination: Vector3) -> bool:
	return ray(origin,destination,1).is_empty()

func rupture_canister(target: StaticBody3D) -> void:
	if not canisters.has(target): return
	canisters.erase(target)
	var center := target.position+Vector3.UP*.65
	# Remove the collider immediately so a second shot cannot detonate it twice.
	target.collision_layer = 0
	discard(target)
	explosion(center,SPORE_CANISTER_RADIUS,Color("#ff4c11"),Color("#ffe187"))
	sfx("pop")
	if player.position.distance_to(center) < SPORE_CANISTER_RADIUS and blast_visible(center,player.position+Vector3.UP*.9):
		damage_player(15,center)
	# Iterate over a copy: killed enemies erase themselves from the original list.
	for enemy in enemies.duplicate():
		if phase != "combat": break
		if not is_instance_valid(enemy): continue
		if enemy.position.distance_to(center) < SPORE_CANISTER_RADIUS and blast_visible(center,enemy.position+Vector3.UP*.95):
			hurt_enemy(enemy,62)

# Visual details never block navigation. Gameplay hazards sit off the central doorway lanes.
# Lightweight structural accents. Pure decoration; no colliders or new lights.
# Put fixtures above the player's head and away from the central doorways.
func add_room_landmark(center: Vector3, accent: Color, choice: int) -> void:
	var steel := material(Color("#373d4a"))
	var rust := material(Color("#807060"))
	match choice:
		0: # twin overhead conduit pipes and bracketed vent
			for x in [-1.05,1.05]:
				decor_box(center+Vector3(x,3.36,0),Vector3(.16,.16,3.1),steel)
				decor_box(center+Vector3(x,3.33,-1.3),Vector3(.25,.21,.12),rust)
			decor_box(center+Vector3(0,3.39,0),Vector3(1.25,.10,.9),steel)
		1: # sagging cable trunk and ceiling reinforcement
			decor_box(center+Vector3(0,3.43,0),Vector3(2.9,.15,.18),steel)
			decor_box(center+Vector3(-1.15,3.20,0),Vector3(.12,.30,.14),rust)
			decor_box(center+Vector3(1.15,3.20,0),Vector3(.12,.30,.14),rust)
		2: # infected overhead ducts; subtle glow, no actual lights
			decor_box(center+Vector3(0,3.43,.6),Vector3(1.7,.24,.40),steel)
			for x in [-.57,.0,.57]:
				decor_ball(center+Vector3(x,3.18,.6),material(Color("#4d9c63"),.3),Vector3(.22,.08,.19))
		3: # robust overhead structural braces
			for z in [-1.1,1.1]:
				decor_box(center+Vector3(0,3.42,z),Vector3(2.7,.18,.22),rust)
			decor_box(center+Vector3(0,3.40,0),Vector3(.12,.17,2.3),material(accent,.17))

func wall_terminal(at: Vector3, facing: float) -> void:
	var inward := Vector3(facing,0,0)
	var dark := material(Color("#1d2936"))
	var casing := material(Color("#6e7a82"))
	var bezel := material(Color("#344958"))
	decor_box(at, Vector3(.12,.86,1.10), casing)
	decor_box(at+inward*.087, Vector3(.065,.76,1.02), dark)
	decor_box(at+inward*.128+Vector3.UP*.105, Vector3(.048,.47,.92), bezel)
	var screen := QuadMesh.new()
	screen.size = Vector2(.82,.38)
	var monitor := mesh(room, screen, at+inward*.158+Vector3.UP*.105, atmosphere_art.terminal_screen())
	monitor.rotation.y = facing*PI*.5
	for y in [-.36,.36]:
		for z in [-.48,.48]:
			decor_ball(at+inward*.13+Vector3(0,y,z), casing, Vector3(.033,.051,.051))
	for key in 5:
		var key_color := Color("#81eed0") if key == 0 else Color("#849ca4")
		decor_box(at+inward*.145+Vector3(0,-.225,(key-2)*.127), Vector3(.055,.079,.09),
			material(key_color,.3 if key == 0 else 0.0))
	decor_ball(at+inward*.15+Vector3(0,-.22,.43),material(Color("#ff945a"),.8),Vector3(.058,.112,.112))
	decor_box(at+inward*.05+Vector3(0,-.64,-.34),Vector3(.076,.44,.09),bezel)

func ceiling_pool(at: Vector3, tint: Color, energy: float, reach: float) -> OmniLight3D:
	var fixture := OmniLight3D.new()
	fixture.position = at
	fixture.omni_range = reach
	fixture.omni_attenuation = 1.55
	fixture.light_energy = energy
	fixture.light_color = tint
	room.add_child(fixture)
	decor_box(at+Vector3.UP*.31,Vector3(.91,.20,.42),material(Color("#333644")))
	decor_box(at+Vector3.UP*.19,Vector3(.77,.046,.28),material(tint,.95))
	for x in [-.43,.43]:
		decor_box(at+Vector3(x,.17,0),Vector3(.075,.10,.43),material(Color("#6d6572")))
	return fixture

func add_vapor_cards(parent: Node3D, size: Vector2, vapor: ShaderMaterial) -> void:
	var card := QuadMesh.new()
	card.size = size
	for angle in [0.0, PI*.5]:
		mesh(parent,card,Vector3.ZERO,vapor).rotation.y = angle

func add_floor_hazard(at: Vector3, toxic: bool, offset: float) -> void:
	var floor_effect := atmosphere_art.hazard(toxic)
	var tile := QuadMesh.new()
	tile.size = Vector2(1.59,1.59)
	var surface := mesh(room,tile,at+Vector3.UP*.027,floor_effect)
	surface.rotation.x = -PI*.5
	var bubbles: Array[MeshInstance3D] = []
	var vapor: ShaderMaterial
	if toxic:
		vapor = atmosphere_art.vapor()
		var plume := Node3D.new()
		plume.position = at+Vector3(0,.43,0)
		room.add_child(plume)
		add_vapor_cards(plume,Vector2(1.06,.86),vapor)
		for i in 4:
			var angle := i*TAU/4.0 + offset
			var position := at + Vector3(cos(angle)*.39,.09,sin(angle)*.39)
			bubbles.append(mesh(room,ball(),position,material(Color("#a6ea68"),.44),Vector3.ONE*.12))
	else:
		# Flush bus bars and broken ceramic insulators, never an upright control.
		for side in [-1,1]:
			var edge := at+Vector3(side*.58,.045,0)
			decor_box(edge,Vector3(.26,.088,.91),material(Color("#343847")))
			for z in [-.27,0.0,.27]:
				decor_box(edge+Vector3(0,.064,z),Vector3(.25,.065,.12),material(Color("#b58f6e")))
			decor_box(edge+Vector3(-side*.045,.10,0),Vector3(.105,.045,.81),material(Color("#567fb1"),.7))
		for z in [-.73,.73]:
			decor_box(at+Vector3(0,.032,z),Vector3(1.30,.04,.09),material(Color("#b7864c"),.26))
	hazards.append({"p":at, "radius":.85, "damage":7 if toxic else 9, "offset":offset,
		"node":surface, "effect":floor_effect, "vapor":vapor, "bubbles":bubbles})

func decorate_sector(local: RandomNumberGenerator, accent: Color, grid: bool) -> void:
	var spots: Array[Vector3] = []
	if grid:
		for id in range(9):
			if id == Expedition.ENTRY: continue
			spots.append(expedition.center(id))
	else:
		spots = [Vector3(-8,0,-8),Vector3(8,0,-8),Vector3(-8,0,8),Vector3(8,0,8)]
	for index in range(spots.size()):
		var center := spots[index]
		# One sparse architectural feature per room. No collision, navigation changes,
		# runtime material duplication, shadow-casting, or extra draw calls per frame.
		if index < ROOM_DETAIL_LIMIT and local.randf() < .66:
			add_room_landmark(center,accent,local.randi_range(0,3))
		var style := local.randi_range(0,4)
		var facing := -1.0 if grid or center.x>0 else 1.0
		var wall_x := center.x + (4.91 if center.x>5.0 else 4.64) if grid else -facing*13.30
		var wall_z := center.z + (local.randf_range(3.35,3.9) * (1.0 if local.randi_range(0,1)==0 else -1.0) if grid else local.randf_range(-1.2,1.2))
		var dark := material(Color("#232535"))
		match style:
			0: # cracked reinforced concrete, three intersecting dark fractures
				decor_box(Vector3(wall_x,1.6,wall_z),Vector3(.06,1.55,.055),dark,.42)
				decor_box(Vector3(wall_x+facing*.04,1.27,wall_z+.28),Vector3(.065,.85,.052),dark,-.64)
			1: # irregular fluorescent green contamination stains
				for i in range(5):
					decor_ball(Vector3(wall_x,local.randf_range(.7,2.6),wall_z+local.randf_range(-.7,.7)),material(Color("#4ca75f"),.12),Vector3(.055,local.randf_range(.14,.42),local.randf_range(.15,.35)))
			2: # warning plate with contrasting diagonal hazard stripes
				decor_box(Vector3(wall_x,2.0,wall_z),Vector3(.08,.82,1.1),material(Color("#cba34a")))
				for stripe in [-.3,0.0,.3]:
					decor_box(Vector3(wall_x+facing*.055,2.0,wall_z+stripe),Vector3(.02,.72,.10),dark,.48)
			3:
				wall_terminal(Vector3(wall_x,1.55,wall_z),facing)
			4: # corroded panel, exposed brackets and pipe
				decor_box(Vector3(wall_x,1.75,wall_z),Vector3(.09,1.8,1.2),material(Color("#564b42")))
				for y in [1.1,2.3]:
					decor_box(Vector3(wall_x+facing*.07,y,wall_z),Vector3(.06,.08,1.1),material(Color("#99806b")))
				for rib in 6:
					decor_box(Vector3(wall_x+facing*.085, 1.48 + rib * .11, wall_z),
						Vector3(.075, .043, .72), dark)
		# Inexpensive, localized lamps. Some rooms deliberately remain dim.
		var side_id := expedition.room_at(center) if grid else -1
		var emergency_red := grid and ((stage in [1,3,5] and side_id == 0) or (stage == 2 and side_id == SPOREWORKS_POD_ROOM))
		var damaged := grid and (side_id == 2 or (stage >= 2 and side_id == 3))
		var light_limit := ROOM_LIGHT_LIMIT-1 if grid and stage == 2 and side_id != SPOREWORKS_POD_ROOM else ROOM_LIGHT_LIMIT
		if room_lights.size() < light_limit and (local.randf() < .68 or emergency_red or damaged):
			var at := center+Vector3(local.randf_range(-1.3,1.3),3.02,local.randf_range(-1.3,1.3))
			var energy := local.randf_range(1.65,2.60)
			var tint := Color("#a7e6c4") if style==1 else accent.lerp(Color("#ffd7ac"),.58)
			if emergency_red:
				tint = Color("#ed4449")
				energy = 1.12
			var fixture := ceiling_pool(at,tint,energy*.28 if grid and stage==1 and center==Vector3.ZERO else energy,4.8 if emergency_red else (5.5 if grid else 6.8))
			var offset := local.randf_range(0,60)
			var random_flicker := local.randf()<.20
			room_lights.append({"node":fixture,"energy":fixture.light_energy,"seed":offset,
				"flicker":damaged or (random_flicker and not emergency_red),"damaged":damaged,"emergency_red":emergency_red})
		# Rare pulsing hazards placed on a side shelf instead of on compulsory routes.
		if index>0 and hazards.size() < HAZARD_LIMIT and local.randf() < (.36 if grid else .25):
			var at := center+Vector3(local.randf_range(-2.6,-1.9),0,local.randf_range(-2.7,-1.9))
			var toxic := local.randf()<.64
			add_floor_hazard(at,toxic,local.randf_range(0,TAU))
	# Keep the airlock and hub navigable on sparse seeds while retaining unlit rooms.
	if grid:
		wall_terminal(expedition.center(Expedition.ENTRY)+Vector3(4.64,1.55,3.35),-1.0)
		for id in [Expedition.ENTRY,4]:
			if room_lights.size() >= ROOM_LIGHT_LIMIT:
				break
			var center := expedition.center(id)
			if not room_lights.any(func(entry): return (entry.node.position-center).length_squared() < 15.0):
				var lamp := ceiling_pool(center+Vector3(0,3.02,-.6),accent.lerp(Color("#ffd2a0"),.65),2.1,5.6)
				room_lights.append({"node":lamp,"energy":2.1,"seed":0.0,"flicker":false})

func tick_atmosphere(dt: float) -> void:
	hazard_clock += dt
	atmosphere_dirty = true
	if hazard_clock < 1.0 or phase != "combat" or hazard_clock < hazard_next_hit or dash_left > 0:
		return
	for entry in hazards:
		var radius := float(entry.radius)
		if player.position.distance_squared_to(entry.p) > radius*radius: continue
		if not hazard_is_active(entry): continue
		var health_before := health
		damage_player(int(entry.damage),entry.p)
		if health < health_before:
			hazard_next_hit = hazard_clock + HAZARD_HIT_INTERVAL
			show_banner("TOXIC EXPOSURE" if int(entry.damage)==7 else "ELECTRICAL DISCHARGE")
			break

func hazard_is_active(entry: Dictionary) -> bool:
	return sin(hazard_clock*2.4+float(entry.offset)) > -.72

func _process(_dt: float) -> void:
	if not OnKlavGame.session_started or OnKlavGame.paused:
		return
	if not sector_checkpoint.is_empty():
		if sector_frame_ready:
			var step: Callable = sector_steps.pop_front()
			step.call()
		else:
			sector_frame_ready = true
		return
	if not atmosphere_dirty or phase != "combat":
		return
	atmosphere_dirty = false
	update_atmosphere_visuals()
	update_sporeworks_motion()
	update_chamber_motion()

func update_atmosphere_visuals() -> void:
	var motion := not reduced_motion()
	var at := player.position
	if motion:
		if room.has_meta("breach_haze"):
			var haze: Node3D = room.get_meta("breach_haze")
			(haze.get_meta("breach_vapor") as ShaderMaterial).set_shader_parameter("clock",hazard_clock)
		for entry in room_lights:
			if not entry.flicker: continue
			var lamp: OmniLight3D = entry.node
			# Freeze distant flicker, then resume at the current phase on approach.
			if at.distance_squared_to(lamp.position) >= 24.0*24.0: continue
			var t := hazard_clock + float(entry.seed)
			var dip := .58 if entry.get("damaged",false) else .25
			lamp.light_energy = float(entry.energy)*(1.0-dip*pow(maxf(0.0,sin(t*5.4)),6.0))
	for entry in hazards:
		if at.distance_squared_to(entry.p) >= 144.0: continue
		var effect: ShaderMaterial = entry.effect
		var active := hazard_is_active(entry)
		if entry.get("last_armed") != active:
			effect.set_shader_parameter("armed",1.0 if active else .22)
			entry.last_armed = active
		var time := hazard_clock+float(entry.offset) if motion else 0.0
		if entry.get("last_clock") == time: continue
		entry.last_clock = time
		effect.set_shader_parameter("clock",time)
		if entry.vapor != null:
			(entry.vapor as ShaderMaterial).set_shader_parameter("clock",time)
		if motion:
			for i in entry.bubbles.size():
				var bubble: MeshInstance3D = entry.bubbles[i]
				var rise := fposmod(time*.84+i*.29,1.0)
				bubble.position.y = .07+rise*.31
				bubble.scale = Vector3.ONE*(.07+.09*sin(rise*PI))

func objectives_complete() -> bool:
	return objective_mask == (1<<objective_nodes.size())-1

func objective_target() -> Vector3:
	if waste_surface != null and expedition.room_at(player.position) == Expedition.ENTRY:
		var at := player.position
		if at.z >= 10.35 and absf(at.x-2.17) > .55: return Vector3(2.17,0,10.85)
		if at.z > 8.1: return Vector3(2.17,0,7.60)
		if absf(at.x) > .48: return Vector3(0,0,7.60)
		return Vector3(0,0,4.45)
	if objectives_complete() and is_instance_valid(access_card):
		return expedition.waypoint(player.position,access_card.position)
	var best := exit_marker.position
	var distance := INF
	for node in objective_nodes:
		if not node.visible: continue
		var candidate := player.position.distance_squared_to(node.position)
		if candidate<distance:
			distance=candidate
			best=node.position
	return expedition.waypoint(player.position,best)

func build_breach_gate() -> void:
	breach_gate = StaticBody3D.new()
	breach_gate.set_meta("impact_surface","steel")
	breach_gate.position = Vector3(0, 0, 5)
	room.add_child(breach_gate)
	var collision := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = Vector3(3.3, 3.8, .35)
	collision.shape = shape
	collision.position.y = 1.9
	breach_gate.add_child(collision)
	breach_bars = Node3D.new()
	breach_gate.add_child(breach_bars)
	var frame := material(Color("#344852"), 0.0, true)
	for x in [-1.48, -.74, 0.0, .74, 1.48]:
		cube(breach_bars, Vector3(x, 1.8, 0), Vector3(.13, 3.6, .24), frame)
	cube(breach_bars, Vector3(0, .32, 0), Vector3(3.26, .64, .28), frame)
	for x in [-1.32, 1.32]:
		cube(breach_bars, Vector3(x, 2.48, .18), Vector3(.2, .23, .08), material(GOLD, .8))

func tick_breach(dt: float) -> void:
	if breach_state == Breach.SEALED and player.position.distance_to(Vector3(0, 0, 5)) < 3.1 and player.position.z >= 5.0:
		breach_state = Breach.WARNING
		breach_clock = BREACH_WARNING_TIME
		show_banner("CONTAINMENT FAILING  /  SHUTTER RELEASE")
		sfx("hurt")
	if breach_state == Breach.WARNING:
		breach_clock = maxf(0.0, breach_clock - dt)
		if is_instance_valid(breach_bars) and not reduced_motion():
			breach_bars.position.y = 3.8 * (1.0 - smoothstep(0.0, .55, breach_clock))
		if breach_clock <= 0.0:
			discard(breach_gate)
			breach_gate = null
			breach_bars = null
			breach_state = Breach.OPEN
			burst(Vector3(0, 1.8, 5), GOLD, 9)
			sfx("break")
			show_banner("SHUTTER OPEN  /  CLEAR THE HUB")
			OnKlavGame.checkpoint()

func update_breach_extraction() -> void:
	if breach_state in [Breach.INACTIVE, Breach.SEALED, Breach.WARNING, Breach.CLEAR] or not objectives_complete():
		return
	var guards_remain := enemies.any(func(enemy): return enemy.get_meta("breach_guard",false))
	if not guards_remain:
		breach_state = Breach.CLEAR
		show_banner("EXTRACTION CLEAR  /  APPROACH THE GATE")
		OnKlavGame.checkpoint()
	elif breach_state == Breach.OPEN and expedition.room_at(player.position) == expedition.exit_room:
		breach_state = Breach.EXTRACTION
		show_banner("EXTRACTION BLOCKED  /  CLEAR THE GATE GUARDS")
		OnKlavGame.checkpoint()

func begin_expedition() -> void:
	if phase!="briefing" or not sector_checkpoint.is_empty(): return
	intro_elapsed = INTRO_TIME
	player.rotation.y = 0.0
	camera.rotation.x = 0.0
	phase="combat"
	if stage == 1:
		breach_state = Breach.SEALED
		build_breach_gate()
		show_banner("AIRLOCK SECURE")
	if not encounter_prepared:
		for id in 9:
			spawn_encounter_room(id)
		if stage == 1: spawn_breach_guards()
	encounter_prepared = false
	pending=0
	update_portal_approach()
	update_overlay()
	sfx("wave")
	OnKlavGame.checkpoint()

func request_expedition_entry() -> void:
	if phase != "briefing" or not sector_checkpoint.is_empty(): return
	if encounter_prepared:
		begin_expedition()
		return
	start_sector_transition()
	queue_encounter()
	sector_steps.append(func():
		clear_sector_transition()
		begin_expedition()
	)

func queue_encounter() -> void:
	encounter_rng_state = rng.state
	for id in 9:
		if id != Expedition.ENTRY:
			sector_steps.append(spawn_encounter_room.bind(id))
	if stage == 1:
		sector_steps.append(spawn_breach_guards)
	sector_steps.append(func(): encounter_prepared = true)

func spawn_encounter_room(id: int) -> void:
	if id==Expedition.ENTRY: return
	if id==expedition.exit_room and stage==MAX_STAGE:
		spawn_enemy(3,expedition.center(id))
	elif id==4 or id in expedition.targets or stage>=3 or (stage==1 and id in [0,8]) or (stage==2 and id in [0,SPOREWORKS_TRANSFER_ROOM,2,SPOREWORKS_PRESSURE_ROOM,SPOREWORKS_POD_ROOM]):
		# Target rooms get a deliberate guardian; outer rooms vary the approach.
		var kind := encounter_kind(id,id in expedition.targets)
		var side := -1.0 if id%2==0 else 1.0
		var approach := Vector3(side*2.6,0,-2.6 if id%3==0 else 2.6)
		if stage == 2 and id == SPOREWORKS_TRANSFER_ROOM:
			kind = 1
			approach = Vector3(1.46,0,-2.66)
		elif stage == 2 and id == SPOREWORKS_POD_ROOM:
			approach = Vector3(.96,0,-2.1)
		spawn_enemy(kind,expedition.center(id)+approach)
		if stage == 2 and id == SPOREWORKS_TRANSFER_ROOM:
			spawn_enemy(RAZOR_MITE,expedition.center(id)+Vector3(-2.42,0,-1.48))
		if stage in [3,4,5] and id in [1,5] and enemies.size() < ENEMY_LIMIT:
			spawn_enemy(RAZOR_MITE if id==1 else LANTERN_WRAITH,expedition.center(id)+Vector3(1.92,0,-1.52))

func spawn_breach_guards() -> void:
	# The extraction guards are stationed here from the beginning of the sector.
	var gate_room := expedition.center(expedition.exit_room)
	spawn_enemy(4,gate_room+Vector3(-2.05,0,-1.45)).set_meta("breach_guard",true)
	spawn_enemy(1,gate_room+Vector3(2.05,0,1.45)).set_meta("breach_guard",true)

func tick_objectives(dt: float) -> void:
	for i in range(objective_nodes.size()):
		var node := objective_nodes[i]
		if not node.visible: continue
		if not reduced_motion():
			var core := node.get_node("Specimen") as Node3D
			core.rotation.y+=dt*1.6
			core.position.y=.95+sin(active_time*3.0+i)*.15
		if player.position.distance_to(node.position)>1.1: continue
		node.hide()
		objective_mask |= 1<<i
		score+=175
		burst(node.position+Vector3.UP,GOLD,12)
		sfx("heal")
		match stage:
			2:
				update_sporeworks_ventilation()
				if objectives_complete():
					show_banner("VENTILATION ONLINE  /  FOLLOW GREEN TO EXTRACTION")
					sfx("clear")
				else:
					show_banner("VENT RELAY %02d  /  RESET" % (i+1))
			4: show_banner("PUMP OFFLINE  /  CORE RECOVERED")
			_: show_banner("Objective secured")
		OnKlavGame.checkpoint()
	update_breach_extraction()
	update_portal_approach()

func exit_wall_direction() -> Vector3:
	var route := expedition.paths.get_id_path(Expedition.ENTRY,expedition.exit_room)
	# The seeded exit is farthest from the airlock and has one approach door.
	return (expedition.center(expedition.exit_room)-expedition.center(route[route.size()-2])).normalized()

func portal_ready() -> bool:
	if phase == "portal":
		return true
	if phase != "combat":
		return false
	if not expedition_mode:
		return wave == 2 and pending == 0 and enemies.is_empty()
	if stage == MAX_STAGE:
		return not enemies.any(func(enemy): return enemy.get_meta("type")==3)
	return objectives_complete() and not is_instance_valid(access_gate) and breach_state in [Breach.INACTIVE,Breach.CLEAR]

func near_exit_gate() -> bool:
	return (not expedition_mode or expedition.room_at(player.position) == expedition.exit_room) \
		and player.position.distance_to(exit_marker.position) < PORTAL_WAKE_DISTANCE

func update_portal_approach() -> void:
	var ready := portal_ready()
	set_portal_display(ready,ready and near_exit_gate())
	if portal_field.visible:
		animate_exit_portal(0.0)
		if phase == "combat" and player.position.distance_to(exit_marker.position) < 1.25:
			enter_portal()

func set_portal_display(ready: bool, active: bool) -> void:
	portal_field.visible = active
	portal_light.visible = active
	portal_rim.visible = active
	var caption := "GATE ACTIVE" if active else ("APPROACH TO ACTIVATE" if ready else "GATE LOCKED")
	if portal_status.text != caption:
		portal_status.text = caption
		portal_status.modulate = TEAL if ready else GOLD
	if not active:
		portal_orbit.rotation.z = 0.0

func build_exit_portal(at: Vector3, heading := 0.0) -> void:
	exit_marker = Node3D.new()
	exit_marker.position = at
	exit_marker.rotation.y = heading
	room.add_child(exit_marker)
	var center := Vector3.UP*1.32
	var frame := TorusMesh.new()
	frame.inner_radius = 1.02
	frame.outer_radius = 1.27
	frame.rings = 32
	frame.ring_segments = 8
	var shell := material(Color("#465564"))
	mesh(exit_marker,frame,center,shell).rotation.x = PI*.5
	var rim := TorusMesh.new()
	rim.inner_radius = 1.005
	rim.outer_radius = 1.062
	rim.rings = 36
	rim.ring_segments = 6
	portal_rim = mesh(exit_marker,rim,center+Vector3.BACK*.13,material(TEAL,.92))
	portal_rim.rotation.x = PI*.5
	for side in [-1,1]:
		cube(exit_marker,Vector3(side*.89,.15,0),Vector3(.54,.30,.75),shell)
		var brace := cube(exit_marker,Vector3(side*.91,.44,0),Vector3(.23,.71,.40),shell)
		brace.rotation.z = -side*.41
	portal_orbit = Node3D.new()
	portal_orbit.position = center+Vector3.BACK*.12
	exit_marker.add_child(portal_orbit)
	for i in 8:
		var angle := i*TAU/8.0
		var point := Vector3(cos(angle),sin(angle),0)*1.17
		var clamp := cube(portal_orbit,point,Vector3(.16,.32,.16),shell)
		clamp.rotation.z = angle
		var strip := cube(portal_orbit,point+Vector3.BACK*.09,Vector3(.043,.22,.035),material(GOLD,.92))
		strip.rotation.z = angle
	var opening := QuadMesh.new()
	opening.size = Vector2(2.05,2.05)
	portal_surface = atmosphere_art.portal()
	portal_field = mesh(exit_marker,opening,center,portal_surface)
	portal_status = Label3D.new()
	portal_status.position = Vector3(0,2.76,.19)
	portal_status.font_size = 24
	portal_status.pixel_size = .009
	portal_status.outline_size = 4
	exit_marker.add_child(portal_status)
	portal_light = OmniLight3D.new()
	portal_light.position = center
	portal_light.omni_range = 4.2
	portal_light.light_color = Color("#73dfeb")
	portal_light.light_energy = 1.10
	exit_marker.add_child(portal_light)
	set_portal_display(phase=="portal",phase=="portal")

func animate_exit_portal(progress: float) -> void:
	if not reduced_motion():
		var time := active_time + portal_elapsed*2.0
		portal_orbit.rotation.z = time*.28
		portal_surface.set_shader_parameter("clock",time)
		portal_surface.set_shader_parameter("crossing",progress)
		portal_light.light_energy = 1.10 + sin(progress*PI)*.65

func enter_portal() -> void:
	phase = "portal"
	portal_elapsed = 0.0
	set_portal_display(true,true)
	clear_orbs()
	controls.reset_input()
	fire_tap = false
	sfx("portal")
	update_overlay()
	OnKlavGame.checkpoint()

func tick_portal(dt: float) -> void:
	portal_elapsed = minf(PORTAL_TIME,portal_elapsed+dt)
	animate_exit_portal(portal_elapsed/PORTAL_TIME)
	if not reduced_motion():
		var offset := exit_marker.position-player.position
		if offset.length_squared()>.09:
			player.rotation.y=lerp_angle(player.rotation.y,atan2(-offset.x,-offset.z),1.0-exp(-dt*7.0))
	if portal_elapsed>=PORTAL_TIME:
		if stage==MAX_STAGE: finish("won")
		else: complete_sector()

func complete_sector() -> void:
	phase="upgrade"
	clear_orbs()
	clear_cosmetic_effects()
	sfx("clear")
	update_overlay()
	OnKlavGame.checkpoint()

func spawn_enemy(kind: int, point: Vector3, hp: int = -1) -> CharacterBody3D:
	var big := kind == 3
	var scale_factor := 1.65 if big else (1.25 if kind == 2 else (.72 if kind==4 else (1.1 if kind==5 else 1.0)))
	var e := body(.53 if kind==1 else .43*scale_factor,1.16 if kind==1 else 1.62*scale_factor,4,1|4)
	e.position = point
	e.set_meta("type",kind)
	e.set_meta("hp",TYPE_HP[kind] if hp < 0 else hp)
	e.set_meta("attack",rng.randf_range(.2,1.1))
	e.set_meta("flash",0.0)
	e.set_meta("stagger",0.0)
	e.set_meta("alert",false)
	e.set_meta("pursuit_left",0.0)
	e.set_meta("backoff_left",0.0)
	e.set_meta("strafe", -1.0 if rng.randi_range(0,1)==0 else 1.0)
	e.set_meta("tactic",rng.randf_range(.35,1.15))
	e.set_meta("step_phase",rng.randf_range(0,TAU))
	e.set_meta("attack_warning",0.0)
	# Boss counters are per actor, not global timers, and serialize with the run.
	if kind == 3:
		e.set_meta("boss_cycle",3.8)
		e.set_meta("boss_windup",0.0)
		e.set_meta("boss_phase",0)
	var form := Node3D.new()
	form.scale = Vector3.ONE*scale_factor
	e.add_child(form)
	if kind in [RAZOR_MITE,LANTERN_WRAITH]:
		build_sprite_enemy(e,form,kind)
		actors.add_child(e)
		enemies.append(e)
		return e

	if kind==1:
		build_spitter(e,form)
		actors.add_child(e)
		enemies.append(e)
		return e
	var skin: Color = [Color("#da1828"),Color("#8956e9"),Color("#d78b31"),Color("#bf326e"),Color("#53ad86"),Color("#b0a548")][kind]
	var cream := material(Color("#ffe4b9"))
	var mouth := material(Color("#48151b"))
	var cap := Node3D.new()
	form.add_child(cap)
	cap.position.y = 1.32
	cap.rotation.z = .23 if kind == 0 else .10
	if kind == 0:
		e.set_meta("gait_cap",cap)
	var gait_feet: Array[MeshInstance3D] = []
	var gloss := material(skin, .06, false, "fungus")
	mesh(cap, fungal_cap_mesh(), Vector3.ZERO, thumbnail_cap_material(skin), Vector3(1, 1, .87))
	mesh(cap, fungal_gill_mesh(), Vector3.ZERO, material(Color("#ddb17b")), Vector3(1, 1, .87))
	mesh(form, ball(), Vector3(0,.67,0), cream, Vector3(.99,1.18,.73))
	mesh(form, ball(), Vector3(0,.38,0), cream, Vector3(1.02,.64,.74))
	var iris := material(Color("#74f993") if kind == 0 else TYPE_COLORS[kind], .58)
	for side in [-1,1]:
		var eye := mesh(form, ball(), Vector3(side*.218,.94,-.327), mouth, Vector3(.38,.30,.16))
		eye.rotation.z = side*.40
		var green := mesh(form, ball(), Vector3(side*.218,.934,-.393), iris, Vector3(.29,.22,.08))
		green.rotation.z = side*.40
		mesh(form, ball(), Vector3(side*.186,.935,-.431), mouth, Vector3(.084,.128,.036))
		mesh(form, ball(), Vector3(side*.186-.035,.971,-.451), material(Color.WHITE,.45), Vector3(.051,.052,.023))
		var brow := mesh(form, ball(), Vector3(side*.222,1.061,-.395), cream, Vector3(.41,.13,.14))
		brow.rotation.z = side*.56
		gait_feet.append(mesh(form, ball(), Vector3(side*.309,.155,-.13), cream, Vector3(.47,.31,.66)))
	if kind == 0:
		e.set_meta("gait_feet",gait_feet)
	mesh(form, ball(), Vector3(0,.63,-.355), mouth, Vector3(.51,.33,.14))
	mesh(form, ball(), Vector3(0,.558,-.414), material(Color("#c23d3b")), Vector3(.28,.12,.035))
	for side in [-1,1]:
		var upper := mesh(form, fungal_fang_mesh(), Vector3(side*.137,.723,-.416), cream)
		upper.rotation.z = PI - side*.24
		var lower := mesh(form, fungal_fang_mesh(), Vector3(side*.137,.544,-.421), cream, Vector3.ONE*.79)
		lower.rotation.z = side*.25
	if kind==4:
		for side in [-1,1]:
			mesh(form,ball(),Vector3(side*.39,.24,.1),material(Color("#427f5b")),Vector3(.20,.18,.66))
	elif kind==5:
		mesh(form,ball(),Vector3(0,.58,.24),material(Color("#9fce4b"),.25),Vector3(.82,.78,.68))
		for side in [-1,1]:
			mesh(form,ball(),Vector3(side*.43,.58,-.24),material(Color("#e9ec77"),.5),Vector3(.19,.19,.18))
	elif kind==2:
		for side in [-1,1]:
			mesh(form,ball(),Vector3(side*.43,.74,0),gloss,Vector3(.33,.37,.45))
	# Readable, different silhouettes so the player can identify threats at a
	# glance even at the 600x340 internal mobile render resolution.
	if kind==2: # brute: high armor plates
		for side in [-1,1]:
			cube(form,Vector3(side*.56,.98,-.02),Vector3(.27,.55,.55),gloss)
	elif kind==4: # skitter: low lateral feelers
		for side in [-1,1]:
			var feeler := cube(form,Vector3(side*.70,.32,-.03),Vector3(.55,.09,.13),gloss)
			feeler.rotation.z=side*.32
	elif kind==5: # bloater: glowing rear spore sacs
		for side in [-1,1]:
			mesh(form,ball(),Vector3(side*.55,.66,.48),material(Color("#b9ef58"),.55),Vector3(.32,.33,.25))
	if big:
		build_cap_king(e,form,cap)
	actors.add_child(e)
	enemies.append(e)
	return e

func build_cap_king(enemy: CharacterBody3D, form: Node3D, cap: Node3D) -> void:
	add_prop_art(form,"king_armor")
	var crown := Node3D.new()
	crown.position.y = .43
	cap.add_child(crown)
	add_prop_art(crown,"king_crown")
	var scars := Node3D.new()
	form.add_child(scars)
	add_prop_art(scars,"king_scars")
	var plates: Array[Node3D] = []
	for side in [-1,1]:
		var plate := Node3D.new()
		plate.position = Vector3(side*.25,.49,-.405)
		plate.scale.x = side
		form.add_child(plate)
		add_prop_art(plate,"king_plate")
		plates.append(plate)
	var core := Node3D.new()
	core.position = Vector3(0,.49,-.410)
	form.add_child(core)
	add_prop_art(core,"king_core")
	var charge := mesh(form,ball(),Vector3(0,1.86,-.58),material(Color("#ff865f"),1.1),Vector3.ONE*.21)
	enemy.set_meta("king_crown",crown)
	enemy.set_meta("king_scars",scars)
	enemy.set_meta("king_plates",plates)
	enemy.set_meta("king_charge",charge)
	enemy.set_meta("exposed_core",core)
	var audio := spatial_effect(form,Vector3(0,1.24,-.30),"furnace_surge",-6,15.0)
	enemy.set_meta("charge_sound",audio)
	update_cap_king_art(enemy)

func update_cap_king_art(enemy: CharacterBody3D) -> void:
	var tier := int(enemy.get_meta("boss_phase",0))
	var winding := float(enemy.get_meta("boss_windup",0.0))
	var exposed := float(enemy.get_meta("boss_exposed",0.0)) > 0.0
	var scars: Node3D = enemy.get_meta("king_scars")
	scars.visible = tier > 0
	var crown: Node3D = enemy.get_meta("king_crown")
	crown.rotation.z = -.24 if tier == 2 else 0.0
	crown.position.y = .38 if tier == 2 else .43
	var plates: Array[Node3D] = enemy.get_meta("king_plates")
	for i in plates.size():
		var side := -1.0 if i==0 else 1.0
		plates[i].position.x = side*(.25 if exposed else .15)
		plates[i].rotation.y = side*(.92 if exposed else .0)
	var core: Node3D = enemy.get_meta("exposed_core")
	core.visible = exposed
	var charge: MeshInstance3D = enemy.get_meta("king_charge")
	charge.visible = winding > 0.0
	var buildup := 1.0-clampf(winding/BOSS_VOLLEY_WINDUP,0.0,1.0)
	charge.scale = Vector3.ONE*(.22 if reduced_motion() else .16+buildup*.26)
	if winding <= 0.0:
		var voice: AudioStreamPlayer3D = enemy.get_meta("charge_sound")
		if voice.playing: voice.stop()

func thumbnail_cap_material(color: Color) -> StandardMaterial3D:
	var key := "thumbnail_cap_" + color.to_html(false)
	if art_cache.has(key):
		return art_cache[key]
	# The UV strip wraps once around the cap, from its rolled lip to the crown.
	# Cream patches are painted into one texture to keep a single cap draw call.
	var image := Image.new()
	image.load_svg_from_string("""<svg xmlns="http://www.w3.org/2000/svg" width="256" height="128" viewBox="0 0 512 256">
	<rect width="512" height="256" fill="#%s"/>
	<g fill="#ffe6af">
	<path d="M15 72c-1-17 17-31 39-30 21 0 26 9 25 20-3 15-14 20-26 23-15 4-36 0-38-13Z"/>
	<path d="M95 121c2-12 15-15 25-11 9 4 12 10 5 17-9 8-31 9-30-6Z"/>
	<path d="M143 68c4-13 18-13 29-21 16-11 28-1 38 8 9 8 12 22 3 31-10 11-20 3-32 6-19 5-44-4-38-24Z"/>
	<path d="M254 103c-5-17 16-25 37-21 19 4 27 17 17 28-9 11-50 13-54-7Z"/>
	<path d="M338 53c-2-10 10-18 21-15 13 4 16 14 9 21-8 8-28 5-30-6Z"/>
	<path d="M413 82c-9-15 6-27 20-26 9 0 15-8 25-5 16 5 27 16 23 27-5 15-21 13-33 15-16 2-29 1-35-11Z"/>
	<ellipse cx="71" cy="164" rx="16" ry="9" transform="rotate(-10 71 164)"/>
	<ellipse cx="194" cy="173" rx="23" ry="9"/>
	<ellipse cx="363" cy="157" rx="23" ry="12" transform="rotate(9 363 157)"/>
	<ellipse cx="473" cy="138" rx="17" ry="7"/>
	<ellipse cx="276" cy="205" rx="21" ry="7"/>
	</g></svg>""" % color.to_html(false))
	for y in range(image.get_height()):
		for x in range(image.get_width()):
			var ink := image.get_pixel(x,y)
			var variation := .96 + .046*sin(x*.47+y*.21)*cos(y*.55-x*.18)
			image.set_pixel(x,y,Color(ink.r*variation,ink.g*variation,ink.b*variation,ink.a))
	image.generate_mipmaps()
	var cap := StandardMaterial3D.new()
	cap.albedo_texture = ImageTexture.create_from_image(image)
	cap.roughness = .27
	art_cache[key] = cap
	return cap

func fungal_cap_mesh() -> Mesh:
	return fungal_profile("thumbnail_cap", [
		Vector2(.82,-.17), Vector2(.90,-.12), Vector2(.91,-.04), Vector2(.86,.06),
		Vector2(.73,.22), Vector2(.55,.41), Vector2(.37,.58), Vector2(.21,.68),
		Vector2(.09,.71), Vector2(.001,.72)], 32, false)

func fungal_gill_mesh() -> Mesh:
	return fungal_profile("thumbnail_gills", [
		Vector2(.001,-.28), Vector2(.32,-.29), Vector2(.52,-.27),
		Vector2(.71,-.23), Vector2(.82,-.17)], 96, true)

func fungal_profile(key: String, profile: Array[Vector2], slices: int, gills: bool) -> Mesh:
	if art_cache.has(key):
		return art_cache[key]
	var builder := SurfaceTool.new()
	builder.begin(Mesh.PRIMITIVE_TRIANGLES)
	for ring in range(profile.size() - 1):
		for slice in slices:
			for offset in [Vector2i(0, 0), Vector2i(0, 1), Vector2i(1, 0),
					Vector2i(0, 1), Vector2i(1, 1), Vector2i(1, 0)]:
				var row: int = ring + offset.x
				var u: float = (slice + offset.y) / float(slices)
				var angle := TAU * float((slice + offset.y) % slices) / float(slices)
				var here := profile[row]
				var ripple := 1.0 + .024 * sin(angle * 3.0) + .016 * cos(angle * 5.0)
				var pleat := .045 * sin(angle * 24.0) * sin(PI * here.x / .82) if gills else 0.0
				builder.set_uv(Vector2(u, float(row) / (profile.size() - 1)))
				builder.add_vertex(Vector3(here.x * cos(angle) * ripple, here.y + pleat, here.x * sin(angle) * ripple))
	builder.generate_normals()
	builder.index()
	var surface := builder.commit()
	art_cache[key] = surface
	return surface

func fungal_fang_mesh() -> Mesh:
	if not art_cache.has("thumbnail_fang"):
		var fang := CylinderMesh.new()
		fang.top_radius = 0
		fang.bottom_radius = .053
		fang.height = .13
		fang.radial_segments = 8
		art_cache["thumbnail_fang"] = fang
	return art_cache.thumbnail_fang

func build_sprite_enemy(enemy: CharacterBody3D, form: Node3D, kind: int) -> void:
	var wraith := kind == LANTERN_WRAITH
	var art := Sprite3D.new()
	art.texture = preload("res://assets/lantern-wraith.svg") if wraith else preload("res://assets/razor-mite.svg")
	art.billboard = BaseMaterial3D.BILLBOARD_FIXED_Y
	art.alpha_cut = SpriteBase3D.ALPHA_CUT_DISCARD
	art.shaded = true
	art.pixel_size = .008 if wraith else .0068
	art.position.y = .91 if wraith else .76
	form.add_child(art)
	if wraith:
		var lamp := mesh(form,ball(),Vector3(0,.98,-.24),material(Color("#ffba8a"),1.45),Vector3.ONE*.17)
		lamp.hide()
		enemy.set_meta("warning_lamp",lamp)
		enemy.set_meta("charge_sound",spatial_effect(form,Vector3.UP*.92,"charge",-10,12.0))

func build_spitter(enemy: CharacterBody3D, form: Node3D) -> void:
	add_prop_art(form, "spitter")
	if expedition_mode and stage == 1:
		add_contact_shadow(Vector3.UP*.019,Vector2(1.66,1.47),enemy)
	var legs: Array[Node3D] = []
	for side in [-1,1]:
		var leg := Node3D.new()
		leg.position = Vector3(side*.49,.52,-.17)
		leg.scale.x = side
		form.add_child(leg)
		add_prop_art(leg,"spitter_foreleg")
		legs.append(leg)
	enemy.set_meta("spitter_forelegs",legs)
	var sacs := Node3D.new()
	sacs.position = Vector3(0,.80,.28)
	form.add_child(sacs)
	add_prop_art(sacs,"spore_sacs")
	if not art_cache.has("spore_membrane"):
		var membrane := material(Color("#c5db82"),.12).duplicate() as StandardMaterial3D
		membrane.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		membrane.albedo_color.a = .31
		membrane.roughness = .27
		art_cache["spore_membrane"] = membrane
	for side in [-1,1]:
		var skin := mesh(sacs,ball(),Vector3(side*.559,.02,0),art_cache["spore_membrane"],Vector3(.306,.505,.37))
		skin.rotation.z = side*.44
	var jaw := Node3D.new()
	jaw.position = Vector3(0,.443,-.69)
	form.add_child(jaw)
	add_prop_art(jaw,"spitter_jaw")
	enemy.set_meta("spore_sacs",sacs)
	enemy.set_meta("spitter_jaw",jaw)
	enemy.set_meta("spitter_recoil",0.0)
	enemy.set_meta("charge_sound",spatial_effect(form,Vector3(0,.59,-.50),"charge",-9,12.0))
	var lamp := mesh(form, ball(), Vector3(0,.59,-.80), material(GOLD,1.2), Vector3.ONE*.15)
	lamp.hide()
	enemy.set_meta("warning_lamp", lamp)

func spawn_next() -> void:
	var a := rng.randf_range(0,TAU)
	var point := Vector3(sin(a)*11.8,0,cos(a)*11.8)
	if point.distance_to(player.position) < 5: point = -point
	var kind := 0
	if stage == MAX_STAGE and wave == 2 and pending == 1: kind = 3
	elif stage >= 3 and rng.randf() < .16: kind = 5
	elif stage >= 2 and rng.randf() < .22: kind = 4
	elif stage >= 3 and rng.randf() < .28: kind = 2
	elif stage >= 2 and rng.randf() < .45: kind = 1
	spawn_enemy(kind,point)
	burst(point+Vector3.UP*.8,TYPE_COLORS[kind],6)
	pending -= 1

func show_banner(line: String) -> void:
	banner = line
	banner_time = 2.8

func begin_wave() -> void:
	pending = 3+stage+wave*2
	spawn_clock = .27
	show_banner("SECTOR %02d   /   WAVE %d" % [stage,wave])
	sfx("wave")

func start_new() -> void:
	seed_code = randi_range(1,2000000000)
	access_route = true
	access_state = 0
	rng.seed = seed_code
	stage = 1
	sporeworks_cleared = 0
	culture_sealed = 0
	practice_complete = false
	wave = 1
	pending = 0
	spawn_clock = 0
	health = 100
	weapon = 0
	weapon_unlocks = 3
	nail_charges = 0
	nail_pickups_collected = 0
	intro_elapsed = 0.0
	score = 0
	kills = 0
	power = 0
	speed_bonus = 0
	streak = 0
	streak_timer = 0
	active_time = 0
	expedition_mode = true
	objective_mask = 0
	phase = "briefing"
	damage_time = 0
	dash_clock = 0
	fire_clock = 0
	dash_left = 0
	recoil = 0
	muzzle_time = 0.0
	update_view_weapon(0.0)
	hit_mark = 0
	hit_defeated = false
	damage_cue_left = 0.0
	damage_cue_angle = 0.0
	move_bob = 0
	fire_tap = false
	build_room()
	reset_player()
	if not expedition_mode: begin_wave()
	update_overlay()
	if web_score == null and not music.playing: music.play()

func reset_player() -> void:
	reset_ammo()
	portal_elapsed=0.0
	player.position = expedition.center(Expedition.ENTRY)+Vector3(0,0,2.3) if expedition_mode else START
	player.rotation = Vector3.ZERO
	player.velocity = Vector3.ZERO
	pitch = 0
	camera.rotation.x = 0
	controls.reset_input()

func look(delta: Vector2) -> void:
	if not OnKlavGame.session_started or OnKlavGame.paused or phase != "combat": return
	player.rotation.y = wrapf(player.rotation.y-delta.x,-PI,PI)
	pitch = clampf(pitch-delta.y,-.83,.83)
	camera.rotation.x = pitch

func action(key: String) -> void:
	if not OnKlavGame.session_started or OnKlavGame.paused or phase != "combat": return
	match key:
		"fire": fire_tap = true
		"reload": begin_reload()
		"swap":
			fire_tap = false
			controls.cancel_action("fire")
			reload_left=0.0
			for offset in range(1,WEAPON_NAMES.size()+1):
				var candidate := (weapon+offset)%WEAPON_NAMES.size()
				if weapon_unlocks & (1 << candidate):
					weapon = candidate
					break
			recoil = .42
			sfx("swap")
		"dash":
			if dash_clock > 0: return
			dash_clock = 2.1
			dash_left = DASH_TIME
			var axis := movement()
			if axis.length() < .1: axis = Vector2.UP
			dash_direction = player.basis * Vector3(axis.x,0,axis.y).normalized()
			sfx("dash")

func _input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode == KEY_Q: action("swap")
		elif event.keycode == KEY_R: action("reload")
		elif event.keycode == KEY_SHIFT: action("dash")

func movement() -> Vector2:
	return (controls.input_vector + Vector2(float(Input.is_key_pressed(KEY_D))-float(Input.is_key_pressed(KEY_A)),float(Input.is_key_pressed(KEY_S))-float(Input.is_key_pressed(KEY_W)))).limit_length()

func ray(from: Vector3, to: Vector3, mask: int, hit_inside := false) -> Dictionary:
	var query := PhysicsRayQueryParameters3D.create(from,to,mask)
	query.hit_from_inside=hit_inside
	return player.get_world_3d().direct_space_state.intersect_ray(query)

func build_view_weapon() -> void:
	view_weapon = Node3D.new()
	camera.add_child(view_weapon)
	for name in ["nailgun","sporebreaker","arc_lance","grav_mortar"]:
		var gun := Node3D.new()
		view_weapon.add_child(gun)
		add_prop_art(gun,name)
		if name == "sporebreaker":
			var casing := CylinderMesh.new()
			casing.top_radius = .081
			casing.bottom_radius = .081
			casing.height = .278
			casing.radial_segments = 12
			var glass := StandardMaterial3D.new()
			glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			glass.albedo_color = Color("#d9a5ff55")
			glass.roughness = .14
			var reservoir := mesh(gun,casing,Vector3(.005,.158,-.345),glass)
			reservoir.rotation.x = PI*.5
		var action := Node3D.new()
		action.position = Vector3(0,.12,-.24)
		gun.add_child(action)
		add_prop_art(action,"nail_slide" if name == "nailgun" else "breaker_action")
		for branch in [gun,action]:
			for child in branch.get_children():
				if child is MeshInstance3D:
					child.layers = 3
		var readout := Label3D.new()
		readout.position = Vector3(0,.173,-.075)
		readout.rotation.x = -.38
		readout.pixel_size = .00185
		readout.font_size = 27
		readout.outline_size = 3
		readout.modulate = TEAL if name == "nailgun" else GOLD
		gun.add_child(readout)
		weapon_readouts.append(readout)
		weapon_action.append(action)
		view_guns.append(gun)
	var view_fill := OmniLight3D.new()
	view_fill.position = Vector3(0,.22,-.31)
	view_fill.omni_range = .85
	view_fill.light_energy = .85
	view_fill.light_color = Color("#b4c7de")
	view_fill.light_cull_mask = 2
	view_weapon.add_child(view_fill)
	var flash := PrismMesh.new()
	flash.size = Vector3(.085,.045,.22)
	muzzle_flash = mesh(view_weapon,flash,Vector3(0,.02,-.66),material(Color("#ffd079"),1.45))
	muzzle_flash.hide()
	muzzle_light = OmniLight3D.new()
	muzzle_light.position = Vector3(0,.02,-.66)
	muzzle_light.light_color = Color("#ffbb74")
	muzzle_light.omni_range = 3.2
	muzzle_light.hide()
	view_weapon.add_child(muzzle_light)
	update_view_weapon(0.0)

func update_view_weapon(dt: float) -> void:
	if not is_instance_valid(view_weapon):
		return
	var motion := 0.25 if reduced_motion() else 1.0
	var dip := sin(clampf(reload_left/RELOAD_TIME[weapon],0.0,1.0)*PI)
	var heavy := weapon in [1,3]
	var kick := recoil*(1.62 if heavy else .82)*motion
	view_weapon.position = Vector3(.185+sin(move_bob)*.009*motion+dip*.12*motion,
		-.247-dip*.19*motion, -.50+kick*.075)
	view_weapon.rotation = Vector3(-kick*.115,-.055,dip*.32*motion)
	var readout := weapon_readouts[weapon]
	var ammo := "%02d" % rounds[weapon] if reload_left <= 0.0 else "—"
	if weapon == 0 and nail_charges > 0: ammo += " · ×3"
	if readout.text != ammo:
		readout.text = ammo
	var action := weapon_action[weapon]
	action.position.z = -.24 + recoil*(.032 if heavy else .095)*motion
	action.rotation.x = -dip*.62*motion if heavy else 0.0
	action.position.y = .12 + dip*.045*motion if heavy else .12
	view_weapon.visible = phase in ["combat","portal"]
	for index in view_guns.size():
		view_guns[index].visible = weapon==index
	muzzle_time = maxf(0.0,muzzle_time-dt)
	muzzle_light.visible = muzzle_time>0.0 and not reduced_motion()
	muzzle_light.light_energy = muzzle_time/.065*(1.8 if weapon==0 else 2.65)
	muzzle_flash.visible = muzzle_light.visible and weapon < 2
	if muzzle_time > 0.0:
		muzzle_light.light_color = SHOT_TINTS[weapon]
		if muzzle_flash.visible:
			muzzle_flash.material_override = material(SHOT_TINTS[weapon],1.45)
			var profile := Vector3(2.1,1.45,.65) if weapon == 1 else Vector3.ONE
			muzzle_flash.scale = profile*(.58+muzzle_time/.065*.42)

func reset_ammo() -> void:
	rounds=CAPACITY.duplicate()
	reload_left=0.0

func begin_reload() -> void:
	if reload_left>0 or fire_clock>0 or rounds[weapon]>=CAPACITY[weapon]: return
	reload_left=RELOAD_TIME[weapon]
	sfx("reload")

func fire() -> void:
	if fire_clock > 0 or reload_left > 0: return
	if rounds[weapon] == 0:
		begin_reload()
		return
	rounds[weapon]-=1
	var triple := weapon == 0 and nail_charges > 0
	if triple: nail_charges -= 1
	fire_clock = FIRE_DELAY[weapon]
	recoil = 1.0
	muzzle_time = .065
	update_view_weapon(0.0)
	if weapon >= 2:
		var shot_key := "arc_fire" if weapon == 2 else "grav_fire"
		if not sounds.has(shot_key):
			sounds[shot_key] = machine_sound(shot_key)
		sfx(shot_key)
	else:
		sfx("nail" if weapon == 0 else "break",1.04 if weapon==0 else .77)
	var origin := camera.global_position
	var direction := -camera.global_basis.z
	var distance: float = [32.0,19.0,29.0,23.0][weapon]
	if weapon == 3:
		launch_mortar(origin,direction*MORTAR_SPEED,MORTAR_LIFE)
		return
	var hit := ray(origin,origin+direction*distance,5,true)
	var impact: Vector3 = hit.get("position",origin+direction*distance)
	var muzzle := view_weapon.to_global(Vector3(0,.02,-.65))
	var wing_targets: Array[CharacterBody3D] = []
	if weapon == 0:
		arc_segment(muzzle,impact,SHOT_TINTS[0],.018 if triple else .012,.09 if triple else .05)
		if triple:
			for side in [-1.0,1.0]:
				var wing := direction.rotated(camera.global_basis.y,side*.044)
				var wing_hit := ray(origin,origin+wing*distance,5,true)
				arc_segment(muzzle,wing_hit.get("position",origin+wing*distance),GOLD,.019,.09)
				if wing_hit.get("collider") is CharacterBody3D: wing_targets.append(wing_hit.collider)
	if not hit.is_empty() and hit.collider.get_meta("practice_target", false):
		practice_complete = true
		hit_mark = .16
		hit_defeated = false
		burst(hit.position, GOLD, 8)
		discard(hit.collider)
		show_banner("TARGET HIT  /  APPROACH THE SHUTTER")
		sfx("hit")
		OnKlavGame.checkpoint()
		return
	if not hit.is_empty() and hit.collider is StaticBody3D and hit.collider.get_meta("spore_canister",false):
		rupture_canister(hit.collider)
		return
	if not hit.is_empty() and hit.collider is StaticBody3D and hit.collider.has_meta("sporeworks_target"):
		hit_sporeworks_target(hit.collider)
		return
	if not hit.is_empty() and hit.collider is StaticBody3D and hit.collider.has_meta("culture_vent"):
		seal_culture_vent(hit.collider)
		return
	if weapon == 1:
		fire_reagent(origin,muzzle,direction)
		return
	var victim: CharacterBody3D = null
	if not hit.is_empty() and hit.collider is CharacterBody3D: victim = hit.collider
	if victim == null:
		victim=close_shot_target(origin,direction)
	if victim == null:
		var best := .993 if weapon == 0 else .966
		for e in enemies:
			var center := e.position+Vector3.UP*(.58 if e.get_meta("type")==1 else (1.26 if e.get_meta("type")==3 else .96))
			var offset := center-origin
			if offset.length() > distance: continue
			var angle := direction.dot(offset.normalized())
			if angle > best and ray(origin,center,1).is_empty():
				best = angle
				victim = e
	if weapon == 2:
		var point := victim.position+Vector3.UP*.82 if victim != null else impact
		fire_arc(muzzle,point,victim)
	elif victim != null or not wing_targets.is_empty():
		var damage := 24+power*8
		var strikes := {}
		if victim != null: strikes[victim] = damage
		for target in wing_targets:
			strikes[target] = int(strikes.get(target,0)) + damage/2
		for target in strikes: hurt_enemy(target,int(strikes[target]),0)
	elif not hit.is_empty():
		strike_surface(hit.position,hit.normal,hit.collider.get_meta("impact_surface","steel"))

func fire_reagent(origin: Vector3, muzzle: Vector3, forward: Vector3) -> void:
	var pellets := {}
	for index in BUCKSHOT.size():
		var offset: Vector2 = BUCKSHOT[index]
		var direction := (forward+camera.global_basis.x*offset.x+camera.global_basis.y*offset.y).normalized()
		var hit := ray(origin,origin+direction*19.0,5,true)
		var contact: Vector3 = hit.get("position",origin+direction*19.0)
		if not reduced_motion() and motes.size() < FX_LIMIT:
			var stream := (contact-muzzle)
			var droplet := mote_mesh(ball(),muzzle,material(Color("#c94ef2"),1.18),Vector3(.075,.075,.27))
			droplet.look_at(contact,Vector3.FORWARD if absf(stream.normalized().y)>.98 else Vector3.UP)
			motes.append({"node":droplet,"v":stream/.14,"life":.14,"fluid":true})
		if hit.is_empty(): continue
		if hit.collider is CharacterBody3D:
			var target: CharacterBody3D = hit.collider
			var damage := 13 if origin.distance_to(contact) < 7.0 else 8
			pellets[target] = int(pellets.get(target,0))+damage
		elif index in [0,3,5]:
			strike_surface(contact,hit.normal,hit.collider.get_meta("impact_surface","steel"),2)
	if pellets.is_empty():
		var near := close_shot_target(origin,forward)
		if near != null: pellets[near] = 39
	for target in pellets:
		reagent_splash(target)
		hurt_enemy(target,int(pellets[target])+power*8,1)

func reagent_splash(target: CharacterBody3D) -> void:
	var at := target.position+Vector3.UP*.85
	if motes.size() < FX_LIMIT and not reduced_motion():
		var toward := target.global_basis.inverse()*(player.position-target.position).normalized()
		var coat := mesh(target,ball(),Vector3.UP*.85+toward*.35,material(Color("#aa29d3"),.82),Vector3(.46,.28,.18))
		coat.rotation.y = atan2(toward.x,toward.z)
		motes.append({"node":coat,"v":Vector3.ZERO,"life":.82,"beam":true})
	for i in range(mini(3 if reduced_motion() else 9,FX_LIMIT-motes.size())):
		var steam := i >= 4 or reduced_motion()
		var tint := Color("#dec5ed") if steam else Color("#a632cd")
		var mote := mote_mesh(ball(),at+Vector3(randf_range(-.28,.28),0,randf_range(-.28,.28)),material(tint,.48),Vector3.ONE*(.19 if steam else .12))
		motes.append({"node":mote,"v":Vector3(randf_range(-.6,.6),randf_range(.55,1.8),randf_range(-.6,.6)),"life":.65 if steam else .28,"steam":steam})

func lightning(from: Vector3, to: Vector3) -> void:
	var axis := (to-from).normalized()
	var side := axis.cross(Vector3.UP).normalized()
	if side.length_squared() < .01: side = Vector3.RIGHT
	var offset := minf(.46,from.distance_to(to)*.13)
	var previous := from
	for i in range(1,6):
		var t := i/5.0
		var point := from.lerp(to,t) + side*sin(i*2.4)*offset*sin(PI*t)
		arc_segment(previous,point,Color("#25baff"),.092,.21)
		arc_segment(previous,point,Color("#eaffff"),.032,.19)
		previous = point
	arc_segment(from,from.lerp(to,.45)-side*offset*.9,Color("#9a84ff"),.032,.15)

func fire_arc(muzzle: Vector3, point: Vector3, primary: CharacterBody3D) -> void:
	lightning(muzzle,point)
	burst(point,SHOT_TINTS[2],5)
	if primary == null: return
	var targets := enemies.duplicate()
	targets.erase(primary)
	targets.sort_custom(func(a,b): return a.position.distance_squared_to(point)<b.position.distance_squared_to(point))
	var budget := 2
	for target in targets:
		var center: Vector3 = target.position+Vector3.UP*.82
		if center.distance_to(point) > 4.0 or not ray(point,center,1).is_empty(): continue
		lightning(point,center)
		shock_enemy(target,35+power*5)
		budget -= 1
		if budget == 0: break
	shock_enemy(primary,58+power*8)

func shock_enemy(enemy: CharacterBody3D, damage: int) -> void:
	if float(enemy.get_meta("arc_time",0.0)) <= 0.0:
		enemy.set_meta("arc_time",1.0)
	hurt_enemy(enemy,damage,2)

func tick_shocks(dt: float) -> void:
	for enemy in enemies.duplicate():
		var left := float(enemy.get_meta("arc_time",0.0))
		if left <= 0.0: continue
		var remaining := maxf(0.0,left-dt)
		enemy.set_meta("arc_time",remaining)
		var form := enemy.get_child(1) as Node3D
		if form != null:
			form.rotation.z = sin(remaining*78.0)*.11 if remaining > 0.0 and not reduced_motion() else 0.0
		var center: Vector3 = enemy.position+Vector3.UP*.9
		if remaining > 0.0:
			if center.distance_squared_to(player.position) < 100.0 and int(left*5) != int(remaining*5):
				burst(center,Color("#73eaff"),2)
			continue
		explosion(center,1.55,Color("#37bdff"),Color("#ecffff"))
		if enemy.get_meta("arc_lethal",false):
			enemy.set_meta("arc_lethal",false)
			hurt_enemy(enemy,1,2)
		else:
			hurt_enemy(enemy,22,2)
			if enemies.has(enemy): sfx("arc_crack")
		if phase != "combat": return

func launch_mortar(origin: Vector3, velocity: Vector3, life: float) -> void:
	if mortar_rounds.size() >= 3: return
	var rocket := Node3D.new()
	rocket.position = origin
	actors.add_child(rocket)
	mesh(rocket,ball(),Vector3.ZERO,material(Color("#ffd0ff"),2.15),Vector3(.25,.25,.56))
	for z in [-.12,.12]:
		var shell := mesh(rocket,ball(),Vector3(0,0,z),material(SHOT_TINTS[3],1.65),Vector3(.34,.34,.12))
		shell.rotation.z = z*3.0
	if not reduced_motion():
		mesh(rocket,ball(),Vector3(0,0,.28),material(Color("#a675f7"),1.1),Vector3(.14,.14,.70))
	rocket.look_at(origin+velocity,Vector3.FORWARD if absf(velocity.normalized().y)>.98 else Vector3.UP)
	mortar_rounds.append({"node":rocket,"velocity":velocity,"life":life})

func tick_mortar(dt: float) -> void:
	for index in range(mortar_rounds.size()-1,-1,-1):
		var round_data := mortar_rounds[index]
		var node: Node3D = round_data.node
		var step := minf(dt,round_data.life)
		var destination: Vector3 = node.position+round_data.velocity*step
		var hit := ray(node.position,destination,5,true)
		round_data.life -= step
		if hit.is_empty() and round_data.life > 0.0:
			node.position = destination
			continue
		mortar_rounds.remove_at(index)
		discard(node)
		var point: Vector3 = destination if hit.is_empty() else (hit.position+hit.normal*.08)
		var direct: CharacterBody3D = hit.collider if not hit.is_empty() and hit.collider is CharacterBody3D else null
		if not hit.is_empty() and hit.collider.get_meta("spore_canister",false):
			rupture_canister(hit.collider)
			if phase != "combat": return
		explosion(point,3.3,Color("#8f42ff"),Color("#ffc5ff"))
		sfx("grav_blast")
		for target in enemies.duplicate():
			var center: Vector3 = target.position+Vector3.UP*.82
			if target == direct or (center.distance_to(point) < 3.3 and blast_visible(point,center)):
				hurt_enemy(target,(105 if target == direct else 61)+power*5,3)
				if phase != "combat": return

func arc_segment(from: Vector3, to: Vector3, tint: Color, width: float, life: float) -> void:
	if (reduced_motion() and width < .08) or motes.size() >= FX_LIMIT or from.distance_squared_to(to) < .01:
		return
	var bolt := mote_mesh(art_cache["decor_unit_box"],(from+to)*.5,material(tint,1.24))
	bolt.look_at(to,Vector3.FORWARD if absf((to-from).normalized().dot(Vector3.UP))>.98 else Vector3.UP)
	bolt.scale = Vector3(width,width,from.distance_to(to))
	motes.append({"node":bolt,"v":Vector3.ZERO,"life":life,"beam":true})

func strike_surface(at: Vector3, normal: Vector3, surface := "steel", limit := 8) -> void:
	if normal.is_zero_approx():
		return
	var count := 1 if reduced_motion() else mini(limit,5 if weapon==0 else 8)
	for i in range(mini(count,FX_LIMIT-motes.size())):
		var origin := at + normal*.035
		var concrete := surface == "concrete"
		var tint := Color("#c4b5a5") if concrete else (GOLD if i==0 else Color("#ff9d52"))
		var spark := mote_mesh(ball(),origin,material(tint,.15 if concrete else 1.3),
			Vector3.ONE*(.15 if i==0 else .09))
		var velocity := Vector3.ZERO
		if i>0:
			velocity = normal*randf_range(1.5,3.0)+Vector3(randf_range(-1.6,1.6),randf_range(.1,2.0),randf_range(-1.6,1.6))
		motes.append({"node":spark,"v":velocity,"life":.31 if concrete else (.12 if i==0 else .23)})

# The camera can sit above a close target's capsule. Only assist a shot
# directly in front of the player, with a clear line to the enemy.
func close_shot_target(origin: Vector3, direction: Vector3) -> CharacterBody3D:
	var target: CharacterBody3D = null
	var nearest := 1.85
	var forward := Vector3(direction.x,0,direction.z).normalized()
	for enemy in enemies:
		var offset := enemy.global_position-origin
		offset.y=0
		var gap := offset.length()
		if gap>=nearest or (gap>.18 and offset.normalized().dot(forward)<.64): continue
		var height := .58 if enemy.get_meta("type")==1 else .95
		if not ray(origin,enemy.global_position+Vector3.UP*height,1,true).is_empty(): continue
		nearest=gap
		target=enemy
	return target

# This lightweight effect is distinct from normal particle bursts: a flat green
# spore splash and a shrinking cap remnant. It cannot collide or block aiming.
func death_mark(at: Vector3, kind: int, heading := 0.0, style := 0) -> void:
	if reduced_motion() or death_marks.size() >= DEATH_MARK_LIMIT: return
	var root_mark := Node3D.new()
	root_mark.position = at
	actors.add_child(root_mark)
	var tint: Color = TYPE_COLORS[kind]
	var cap_mark: MeshInstance3D
	if kind == 1 and style == 0:
		cap_mark = mesh(root_mark,prop_art.model("spitter")[0],Vector3.ZERO,null)
		cap_mark.rotation.y = heading
	else:
		cap_mark = mesh(root_mark,ball(),Vector3(0,.38,0),material(tint if style < 2 else SHOT_TINTS[style],1.0 if style >= 2 else .55),Vector3(.70,.23,.70))
	var splash := mesh(root_mark,ball(),Vector3(0,.035,0),material(Color("#65c36a") if style == 0 else SHOT_TINTS[style],.42),Vector3(1.10,.05,.75))
	death_marks.append({"node":root_mark,"cap":cap_mark,"splash":splash,"kind":kind,"style":style,"life":DEATH_MARK_TIME})

func add_stain(at: Vector3) -> void:
	if stains.size()>=STAIN_LIMIT: discard(stains.pop_front())
	var stain := mesh(actors,ball(),Vector3(at.x,.028,at.z),material(Color("#417a39"),.15),Vector3(.76,.026,.58))
	stains.append(stain)

func tick_death_marks(dt: float, frustum: Array[Plane] = []) -> void:
	for i in range(death_marks.size()-1,-1,-1):
		var mark: Dictionary = death_marks[i]
		mark.life -= dt
		if mark.life <= 0.0:
			discard(mark.node)
			death_marks.remove_at(i)
		elif effect_in_view(frustum,mark.node.global_position,1.5):
			var progress: float = 1.0-mark.life/DEATH_MARK_TIME
			if mark.style == 3:
				mark.cap.position.y = .38+progress*.55
				mark.cap.scale = Vector3.ONE*(.46*(1.0-progress))
			elif mark.style == 2:
				mark.cap.scale = Vector3(.48,.78,.48)*(1.0-progress)
			elif mark.kind == 1 and mark.style == 0:
				mark.cap.rotation.z = progress*.58
				mark.cap.scale = Vector3(1.0-progress*.38,1.0-progress*.76,1.0-progress*.34)
			else:
				mark.cap.position.y = .38-progress*.24
				mark.cap.scale = Vector3(.70,.23,.70)*(1.0-progress*.70)
			mark.splash.scale = Vector3(1.10,.05,.75)*(1.0+progress if mark.style == 3 else 1.0-progress*.45)

func hurt_enemy(e: CharacterBody3D, damage: int, source_weapon := -1) -> void:
	var impact_weapon := weapon if source_weapon < 0 else source_weapon
	var kind := int(e.get_meta("type"))
	var exposed := kind == 3 and float(e.get_meta("boss_exposed", 0.0)) > 0.0
	var hp := int(e.get_meta("hp")) - (roundi(damage * 1.35) if exposed else damage)
	if kind<3 and int(e.get_meta("hp"))==TYPE_HP[kind]: hp=maxi(1,hp)
	if hp <= 0 and float(e.get_meta("arc_time",0.0)) > 0.0:
		e.set_meta("arc_lethal",true)
		hp = 1
	hit_mark = .16
	hit_defeated = hp <= 0
	# Brutes have a hard, pale shell; impact colors and particle amounts make
	# armored hits read differently without increasing the global FX budget.
	var impact_tint: Color = GOLD if exposed else (IMPACT_COLOR if kind == 2 and hp > 0 else (GOO if impact_weapon == 0 else SHOT_TINTS[impact_weapon]))
	burst(e.position+Vector3.UP,impact_tint,3 if kind == 2 and hp > 0 else (5 if hp > 0 else [10,16,7,17][impact_weapon]))
	if hp > 0:
		e.set_meta("hp",hp)
		e.set_meta("flash",.15)
		var impact_x := (e.global_basis.inverse()*(player.global_position-e.global_position)).x
		e.set_meta("hit_lean",1.0 if impact_x>=0.0 else -1.0)
		# Normal enemies briefly flinch and lose their immediate attack. Cap King
		# remains dangerous and cannot be stun-locked with the fast Nailgun.
		if kind != 3:
			e.set_meta("stagger",HIT_STAGGER_TIME)
			e.set_meta("backoff_left",.45)
			e.set_meta("attack",maxf(float(e.get_meta("attack")),.26))
			e.set_meta("attack_warning",0.0)
		e.set_meta("alert",true)
		e.set_meta("pursuit_left",PURSUIT_MEMORY)
		sfx("hit", [1.14,.73,1.38,.62][impact_weapon])
		return
	kills += 1
	streak = mini(9,streak+1)
	streak_timer = 3.25
	score += (170 if kind==3 else 95+kind*30)*streak
	if kind==5:
		var blast_center := e.position+Vector3.UP*.7
		for other in enemies:
			if other == e: continue
			if other.position.distance_to(e.position)<2.3 and blast_visible(blast_center,other.position+Vector3.UP*.9):
				other.set_meta("hp",maxi(1,int(other.get_meta("hp"))-22))
		if player.position.distance_to(e.position)<2.3 and blast_visible(blast_center,player.position+Vector3.UP*.9):
			damage_player(12,e.position)
		burst(e.position+Vector3.UP,TYPE_COLORS[5],12)
	death_mark(e.position,kind,e.rotation.y,impact_weapon)
	if impact_weapon < 2: add_stain(e.position)
	enemies.erase(e)
	discard(e)
	if impact_weapon != 3:
		sfx("arc_crack" if impact_weapon == 2 else "pop",.72 if impact_weapon == 1 else 1.0)
	if expedition_mode and kind==3 and phase=="combat":
		set_portal_display(true,false)
		show_banner("CAP KING DOWN  /  REACH THE PORTAL")
		OnKlavGame.checkpoint()

func damage_player(value: int, source: Vector3 = Vector3.ZERO) -> void:
	if damage_time > .13 or dash_left > 0: return
	damage_time = .48
	# World-space source projected into the player's horizontal facing plane.
	# Zero-length damage (legacy callers) gives a neutral front indicator.
	var toward := source-player.position
	toward.y = 0.0
	if toward.length_squared() > .001:
		var facing := player.global_basis.inverse()*toward.normalized()
		damage_cue_angle = atan2(facing.x,-facing.z)
	else:
		damage_cue_angle = 0.0
	damage_cue_left = DAMAGE_CUE_TIME
	health = maxi(0,health-value)
	streak = 0
	sfx("hurt")
	if health == 0: finish("dead")

func launch_orb(e: CharacterBody3D, kind: int) -> void:
	if orbs.size() >= ORB_LIMIT: return
	var point := e.position+Vector3.UP*(.59 if kind==1 else (1.3 if kind==3 else .95))
	var aim := (player.position+Vector3.UP*.95-point).normalized()
	var node := mesh(actors,ball(),point,material(TYPE_COLORS[kind],1.55),Vector3.ONE*.32)
	orbs.append({"node":node,"velocity":aim*(7.6 if kind==3 else 6.1),"life":3.6,"damage":14 if kind==3 else 10})
	sfx("spit")

# Three capped spore bolts fan toward the player's position at the end of a
# visible windup. All use the existing cheap orb renderer and collision loop.
func boss_volley(e: CharacterBody3D, tier: int) -> void:
	var slots := mini(BOSS_VOLLEY_MAX,ORB_LIMIT-orbs.size())
	if slots <= 0: return
	var point := e.position+Vector3.UP*2.05
	var aim := player.position+Vector3.UP*.95-point
	if aim.length_squared() < .01: return
	var direction := aim.normalized()
	for i in range(slots):
		var offset := (float(i)-float(slots-1)*.5)*.22
		var flight = direction.rotated(Vector3.UP,offset).normalized()*BOSS_VOLLEY_SPEED[tier]
		var node := mesh(actors,ball(),point,material(PINK,1.0),Vector3.ONE*.27)
		orbs.append({"node":node,"velocity":flight,"life":3.1,"damage":9})
	sfx("spit")

# The arena reacts at the same health thresholds as the King's volley.
func tick_boss(e: CharacterBody3D, dt: float, gap: float, sight: bool) -> float:
	e.set_meta("boss_exposed", maxf(0.0, float(e.get_meta("boss_exposed", 0.0)) - dt))
	var hp := int(e.get_meta("hp"))
	var tier := 2 if hp <= 153 else (1 if hp <= 331 else 0)
	var previous := int(e.get_meta("boss_phase",0))
	if tier > previous:
		e.set_meta("boss_phase",tier)
		e.set_meta("boss_phase_since", active_time)
		burst(e.position+Vector3.UP*1.7,GOLD if tier == 1 else PINK,12)
		show_banner("CROWN CRACKED  /  ROOT LANES ACTIVE" if tier == 1 else "LAST STAND  /  ROOTS SURGE FASTER")
	var windup := float(e.get_meta("boss_windup",0.0))
	var cycle := float(e.get_meta("boss_cycle",3.8))
	# Only wind up within the current room and clear line of sight.
	if windup > 0.0:
		# Breaking line of sight actually interrupts the charged volley; otherwise
		# a player on mobile can dodge behind cover yet be surprised on re-entry.
		if not sight or gap >= 14.0:
			e.set_meta("boss_windup",0.0)
			e.set_meta("boss_cycle",maxf(cycle,1.0))
			return 0.0
		windup = maxf(0.0,windup-dt)
		if windup <= 0.0:
			boss_volley(e,tier)
			if expedition_mode and tier > 0:
				e.set_meta("boss_exposed", BOSS_EXPOSE_TIME)
				show_banner("CORE EXPOSED  /  FIRE NOW")
		e.set_meta("boss_windup",windup)
		return windup
	if gap < 13.5 and sight:
		cycle -= dt
		if cycle <= 0.0:
			e.set_meta("boss_windup",BOSS_VOLLEY_WINDUP)
			if DisplayServer.get_name() != "headless":
				var voice: AudioStreamPlayer3D = e.get_meta("charge_sound")
				voice.pitch_scale = 1.3
				voice.play()
			cycle = BOSS_VOLLEY_COOLDOWN[tier]
		e.set_meta("boss_cycle",cycle)
	return float(e.get_meta("boss_windup",0.0))

# Only 13 enemies exist at once. A single symmetric pair pass is cheaper than
# doing the same separation test twice (and avoids hash-grid allocation on mobile).
func crowd_forces() -> Array[Vector3]:
	var forces: Array[Vector3] = []
	forces.resize(enemies.size())
	forces.fill(Vector3.ZERO)
	if enemies.size() < 2: return forces
	var radius_sq := CROWD_SEPARATION_RADIUS * CROWD_SEPARATION_RADIUS
	var positions := PackedVector2Array()
	positions.resize(enemies.size())
	for i in enemies.size():
		var at := enemies[i].position
		positions[i] = Vector2(at.x,at.z)
	for i in range(positions.size()-1):
		var at := positions[i]
		for j in range(i+1,positions.size()):
			var apart := at-positions[j]
			var d2 := apart.length_squared()
			if d2 <= .001 or d2 >= radius_sq: continue
			var distance := sqrt(d2)
			var push := Vector3(apart.x,0,apart.y) * ((1.0-distance/CROWD_SEPARATION_RADIUS)/distance)
			forces[i] += push
			forces[j] -= push
	return forces

func tick_enemies(dt: float) -> void:
	tick_shocks(dt)
	if phase != "combat": return
	var crowd := crowd_forces()
	var player_room := expedition.room_at(player.position) if expedition_mode else -1
	for index in range(enemies.size()):
		var e := enemies[index]
		if float(e.get_meta("arc_time",0.0)) > 0.0:
			e.velocity = Vector3.ZERO
			continue
		var kind := int(e.get_meta("type"))
		var offset := player.position-e.position
		offset.y = 0
		var gap := offset.length()
		var toward := offset/gap if gap > 0.0 else Vector3.ZERO
		var direction := toward
		var same_room := not expedition_mode or expedition.room_at(e.position)==player_room
		var safe_airlock := not same_room and player_room==Expedition.ENTRY
		var notice := 10.0 if kind==3 else (7.4 if stage>1 else 6.4)
		var was_alert := bool(e.get_meta("alert"))
		var can_spot := (same_room and gap<=notice) or (was_alert and gap<=notice+2.0)
		var pursuit := maxf(0.0,float(e.get_meta("pursuit_left",0.0))-dt)
		var has_sight := not safe_airlock and can_spot and ray(e.position+Vector3.UP*.85,player.position+Vector3.UP*.9,1).is_empty()
		if has_sight:
			pursuit=PURSUIT_MEMORY
		if safe_airlock or gap>notice+4.0: pursuit=0.0
		e.set_meta("pursuit_left",pursuit)
		e.set_meta("alert",pursuit>0.0)
		if pursuit<=0.0:
			e.velocity=Vector3.ZERO
			e.set_meta("attack_warning",0.0)
			if kind in [1,LANTERN_WRAITH]:
				(e.get_meta("warning_lamp") as MeshInstance3D).hide()
				var voice: AudioStreamPlayer3D = e.get_meta("charge_sound")
				if voice.playing: voice.stop()
				if kind == 1 and (was_alert or float(e.get_meta("spitter_recoil",0.0)) > 0.0):
					update_spitter_pose(e,0.0,dt)
			if kind == 3:
				e.set_meta("boss_windup", 0.0)
				e.set_meta("boss_exposed", 0.0)
				update_cap_king_art(e)
			continue
		if not same_room:
			direction=(expedition.waypoint(e.position,player.position)-e.position).normalized()
		if gap > .1: e.rotation.y = atan2(-direction.x,-direction.z)
		var stagger := maxf(0.0,float(e.get_meta("stagger",0.0))-dt)
		e.set_meta("stagger",stagger)
		var attack := float(e.get_meta("attack"))-dt
		var shooting := kind in [1,3,LANTERN_WRAITH]
		var warning := float(e.get_meta("attack_warning",0.0))
		var clear := has_sight and gap<12.0
		# Spitter volleys are earned only after a complete, visible windup.
		# Losing sight or being staggered cancels the charge; no surprise shot.
		if kind in [1,LANTERN_WRAITH]:
			if stagger > 0.0 or not clear or gap >= 12.0:
				warning = 0.0
			elif attack <= 0.0:
				if warning <= 0.0:
					warning = SPITTER_CHARGE_TIME
					if DisplayServer.get_name() != "headless":
						(e.get_meta("charge_sound") as AudioStreamPlayer3D).play()
				else:
					warning = maxf(0.0,warning-dt)
					if warning <= 0.0:
						launch_orb(e,kind)
						e.set_meta("spitter_recoil",1.0)
						attack = 2.05 if kind == LANTERN_WRAITH else 2.45
			var warning_lamp: MeshInstance3D = e.get_meta("warning_lamp")
			warning_lamp.visible = warning > 0.0
			if warning <= 0.0:
				var charge_sound: AudioStreamPlayer3D = e.get_meta("charge_sound")
				if charge_sound.playing: charge_sound.stop()
			if warning > 0.0 and not reduced_motion():
				warning_lamp.scale = Vector3.ONE*(.13+.09*(1.0-warning/SPITTER_CHARGE_TIME))
			if kind == 1: update_spitter_pose(e,1.0-warning/SPITTER_CHARGE_TIME if warning>0.0 else 0.0,dt)
		var boss_charging := 0.0
		if kind == 3:
			# The boss keeps its orientation while charging its clearly telegraphed attack.
			boss_charging = tick_boss(e,dt,gap,clear)
		var backoff := maxf(0.0,float(e.get_meta("backoff_left",0.0))-dt)
		e.set_meta("backoff_left",backoff)
		var standoff := SPITTER_STANDOFF if kind in [1,LANTERN_WRAITH] else (1.85 if kind==3 else MELEE_STANDOFF)
		var away := -toward if gap>.01 else -player.global_basis.z
		var holding := same_room and gap<=standoff+.20
		var drive := direction
		if same_room and (gap<standoff or backoff>0.0):
			drive=away*(.8 if kind!=3 else .4)
		elif holding or (kind in [1,LANTERN_WRAITH] and clear and gap<=7.4):
			drive=Vector3.ZERO
		elif stagger>0.0 or (kind==3 and boss_charging>0.0):
			drive *= .15
		var tactic := float(e.get_meta("tactic"))-dt
		if tactic<=0:
			e.set_meta("strafe",-float(e.get_meta("strafe")))
			tactic=rng.randf_range(.55,1.75)
		e.set_meta("tactic",tactic)
		if gap < 9 and same_room and not holding and backoff<=0.0 and stagger<=0.0 and drive.length_squared()>.4:
			var sideways := Vector3(-direction.z,0,direction.x)*float(e.get_meta("strafe"))
			drive = (drive + sideways*(.28 if shooting else .16)).limit_length()
		# Gentle separation makes groups approach from different angles rather
		# than forming a single overlapping blob. No new navigation queries.
		var separation := crowd[index]
		if separation.length_squared() > .0001:
			drive = (drive+separation.limit_length(1.0)*CROWD_SEPARATION_WEIGHT).limit_length()
		for p in covers:
			var relative := e.position-p
			if relative.length_squared() < 2.55*2.55 and absf(relative.x)<1.8 and absf(relative.z)<1.8 and relative.dot(drive)<0:
				var around := Vector3(-relative.z,0,relative.x).normalized()
				if around.dot(direction)<0: around = -around
				drive = (drive*.38+around*.90).normalized()
		if holding or (same_room and backoff>0.0):
			drive -= toward*maxf(0.0,drive.dot(toward))
		e.velocity = drive*float(TYPE_SPEED[kind])*(1.0+stage*.025)
		e.move_and_slide()
		if attack <= 0 and stagger <= 0.0:
			if kind not in [1,LANTERN_WRAITH] and same_room and has_sight and backoff<=0.0 and gap<standoff+.26:
				damage_player(10 if kind==RAZOR_MITE else (9 if kind==4 else (14 if kind==5 else 8+kind*3)),e.position)
				attack=1.6
				e.set_meta("backoff_left",.6)
			elif kind == 3 and clear and gap < 12.0 and boss_charging <= 0.0:
				# Standard ranged shots have the same visibility/range limits as other shooters.
				# Don't stack an instant ranged shot on a telegraphed boss volley.
				launch_orb(e,kind)
				attack = 1.32
		e.set_meta("attack",maxf(-.1,attack))
		e.set_meta("attack_warning",warning)
		var flash := maxf(0,float(e.get_meta("flash"))-dt)
		e.set_meta("flash",flash)
		var form := e.get_child(1) as Node3D
		var reduced := reduced_motion()
		form.position.y = 0.0 if reduced else sin(active_time*(8.2+kind)+float(e.get_meta("step_phase")))*.055
		form.rotation.z = 0.0 if reduced else sin(active_time*7.0+e.position.z)*.055 + flash*.55*float(e.get_meta("hit_lean",1.0)) + stagger*.38*float(e.get_meta("hit_lean",1.0))
		var scale_factor := 1.65 if kind==3 else (1.25 if kind==2 else (.72 if kind==4 else (1.1 if kind==5 else 1.0)))
		var windup := clampf(warning*.8,0,.42) if clear else 0.0
		if kind == 3:
			windup = maxf(windup,boss_charging*.50)
			update_cap_king_art(e)
		var flinch := 0.0 if reduced else stagger/HIT_STAGGER_TIME
		form.scale = Vector3(1+windup*.2+flinch*.10,1-windup*.28-flinch*.13,1+windup*.2+flinch*.10)*scale_factor
		if kind == 0:
			var stride := clampf(e.velocity.length()/2.55,0.0,1.0) if not reduced else 0.0
			var step := sin(active_time*12.5+float(e.get_meta("step_phase")))*stride
			var feet: Array[MeshInstance3D] = e.get_meta("gait_feet")
			for i in feet.size():
				var foot_step := step if i==0 else -step
				feet[i].position.y = .155 + maxf(0.0,foot_step)*.075
				feet[i].position.z = -.13 + foot_step*.15
			var crown: Node3D = e.get_meta("gait_cap")
			crown.rotation.x = lerpf(crown.rotation.x,-step*.12,minf(1.0,dt*9.0))
			crown.rotation.z = .23+sin(active_time*8.2+e.position.z)*.11*stride
		if kind in [RAZOR_MITE,LANTERN_WRAITH] and not reduced:
			var cadence := 13.0 if kind == RAZOR_MITE else 4.1
			form.position.y += sin(active_time*cadence+float(e.get_meta("step_phase")))*(.055 if kind == RAZOR_MITE else .13)
		if kind==1 and not reduced:
			var squash := sin(active_time*5.5+float(e.get_meta("step_phase")))*.07
			form.scale *= Vector3(1+squash,1-squash,1+squash)
		if phase != "combat": return

func update_spitter_pose(enemy: CharacterBody3D, charge: float, dt: float) -> void:
	var kick := move_toward(float(enemy.get_meta("spitter_recoil")),0.0,dt*4.6)
	enemy.set_meta("spitter_recoil",kick)
	var motion := 0.0 if reduced_motion() else 1.0
	var sacs: Node3D = enemy.get_meta("spore_sacs")
	var jaw: Node3D = enemy.get_meta("spitter_jaw")
	sacs.scale = Vector3(1.0+charge*.18*motion,1.0+charge*.44*motion,1.0+charge*.20*motion)
	jaw.rotation.x = (charge*.27+kick*.36)*motion
	jaw.position.z = -.69+kick*.082*motion
	var legs: Array[Node3D] = enemy.get_meta("spitter_forelegs")
	var stride := clampf(enemy.velocity.length()/1.25,0.0,1.0)*(1.0-charge)
	var beat := sin(active_time*9.0+float(enemy.get_meta("step_phase")))
	for i in legs.size():
		var step := beat*stride*(1.0 if i==0 else -1.0)*motion
		legs[i].rotation.x = step*.32 - charge*.11*motion + kick*.14*motion
		legs[i].position.y = .52+maxf(0.0,step)*.062

func tick_orbs(dt: float) -> void:
	for i in range(orbs.size()-1,-1,-1):
		var o := orbs[i]
		var node: Node3D = o.node
		var step := minf(dt,o.life)
		var next: Vector3 = node.position+o.velocity*step
		o.life -= step
		# Sweep each projectile against the player, but first clip its travel to
		# world collision. A long mobile frame must neither tunnel through the
		# player nor ignore a player hit BEFORE a nearby wall.
		var wall_hit: Dictionary = ray(node.position,next,1)
		var blocked := not wall_hit.is_empty()
		var segment_end: Vector3 = wall_hit.position if blocked else next
		var travel: Vector3 = segment_end-node.position
		var player_center := player.position+Vector3.UP*.9
		var progress := clampf((player_center-node.position).dot(travel)/maxf(travel.length_squared(),.000001),0.0,1.0)
		var closest: Vector3 = node.position+travel*progress
		var contact_distance := closest.distance_squared_to(player_center)
		var contact := step>0.0 and contact_distance<.69*.69
		if contact and contact_distance > .000001:
			# Check the width of the sweep as well as its travel line. When the
			# sweep ends at a wall, start just on the projectile side of it.
			var sight_start := closest
			if blocked and progress >= 1.0:
				sight_start = closest.move_toward(node.position,.002)
			contact = ray(sight_start,player_center,1,true).is_empty()
		if o.life<=0 or contact or blocked:
			burst(closest if contact else segment_end,PINK,3)
			discard(node)
			orbs.remove_at(i)
			if contact: damage_player(o.damage,node.position)
			if phase!="combat": return
		else: node.position = next

func burst(at: Vector3, tint: Color, count: int) -> void:
	if reduced_motion(): return
	for i in range(mini(count,FX_LIMIT-motes.size())):
		var mote := mote_mesh(ball(),at,material(tint,.9),Vector3.ONE*.12)
		var a := randf_range(0,TAU)
		motes.append({"node":mote,"v":Vector3(cos(a),randf_range(.15,1),sin(a))*randf_range(1.5,5),"life":randf_range(.14,.38)})

func explosion(at: Vector3, radius: float, edge: Color, core: Color) -> void:
	if blast_fx.size() >= BLAST_LIMIT:
		recycle_blast(blast_fx.pop_front().node)
	var effect: Node3D = free_blasts.pop_back()
	effect.position = at
	var colors := [material(core,2.0),material(edge,1.35),material(edge,1.6),material(edge.darkened(.64),.32)]
	var low_motion := reduced_motion()
	for part in 6:
		var visual := effect.get_child(part) as MeshInstance3D
		visual.material_override = colors[mini(part,3)]
		visual.visible = part < 3 or not low_motion
		match part:
			0: visual.transform = Transform3D(Basis.from_scale(Vector3.ONE*.52),Vector3.ZERO)
			1: visual.transform = Transform3D(Basis.from_scale(Vector3.ONE*.72),Vector3.ZERO)
			2: visual.transform = Transform3D(Basis.from_scale(Vector3(1,.18,1)),Vector3.ZERO)
			_:
				var angle := (part-3)*TAU/3.0
				visual.transform = Transform3D(Basis.from_scale(Vector3.ONE*.24),Vector3(cos(angle),.32,sin(angle))*.32)
	effect.show()
	blast_fx.append({"node":effect,"age":0.0,"radius":radius})
	if reduced_motion(): return
	for i in range(mini(18,FX_LIMIT-motes.size())):
		var angle := i*2.39996
		var velocity := Vector3(cos(angle),randf_range(.18,1.1),sin(angle))*radius*randf_range(2.6,4.6)
		var ember := mote_mesh(ball(),at,material(core if i%3==0 else edge,1.6),Vector3.ONE*.22)
		motes.append({"node":ember,"v":velocity,"life":randf_range(.24,.58)})

func tick_cosmetic_effects(dt: float) -> void:
	var frustum: Array[Plane] = []
	if not (motes.is_empty() and blast_fx.is_empty() and death_marks.is_empty()):
		frustum = camera.get_frustum()
	tick_motes(dt,frustum)
	tick_blasts(dt,frustum)
	tick_death_marks(dt,frustum)

func effect_in_view(frustum: Array[Plane], at: Vector3, radius: float) -> bool:
	for plane in frustum:
		if plane.distance_to(at) > radius:
			return false
	return true

func tick_blasts(dt: float, frustum: Array[Plane] = []) -> void:
	for i in range(blast_fx.size()-1,-1,-1):
		var fx := blast_fx[i]
		fx.age += dt
		var effect: Node3D = fx.node
		if fx.age >= .48:
			recycle_blast(effect)
			blast_fx.remove_at(i)
			continue
		if not effect_in_view(frustum,effect.global_position,float(fx.radius)+1.0): continue
		var progress := clampf(fx.age/.48,0,1)
		var spread := .25 + float(fx.radius)*(1.0-pow(1.0-progress,2.0))
		if reduced_motion(): spread = float(fx.radius)*.55
		effect.get_child(0).position.y = spread*.14
		effect.get_child(0).scale = Vector3.ONE*spread*.72*(1.0-progress)
		effect.get_child(1).scale = Vector3.ONE*spread*(1.0-progress)*.82
		effect.get_child(2).scale = Vector3(spread,.13*(1.0-progress),spread)
		for j in range(3,effect.get_child_count()):
			var plume := effect.get_child(j) as MeshInstance3D
			if not plume.visible: continue
			var angle := (j-3)*TAU/3.0
			plume.position = Vector3(cos(angle)*spread*.25,spread*(.23+progress*.29),sin(angle)*spread*.25)
			plume.scale = Vector3(1,.82,1)*spread*.47*sin(progress*PI)
	for canister in canisters:
		var beacon := canister.get_node("BlastBeacon") as MeshInstance3D
		if player.position.distance_squared_to(canister.position) < 100 and not reduced_motion():
			var pulse := .92 + .08*sin(active_time*5.2)
			beacon.scale = Vector3(.92,.37,.92)*pulse

func release_mote(node: MeshInstance3D) -> void:
	if not is_instance_valid(node): return
	if node.get_parent() == effect_root:
		recycle_mote(node)
	else:
		discard(node)

func clear_cosmetic_effects() -> void:
	for fx in motes:
		release_mote(fx.node)
	motes.clear()
	for fx in blast_fx:
		recycle_blast(fx.node)
	blast_fx.clear()
	for mark in death_marks:
		discard(mark.node)
	death_marks.clear()

func tick_motes(dt: float, frustum: Array[Plane] = []) -> void:
	for i in range(motes.size()-1,-1,-1):
		var fx := motes[i]
		fx.life -= dt
		var node: MeshInstance3D = fx.node if is_instance_valid(fx.node) else null
		if fx.life <= 0 or node == null:
			release_mote(node)
			motes.remove_at(i)
		elif not fx.get("beam",false):
			# Moving motes live under the untransformed effect root.
			var at: Vector3 = fx.get("position",node.position) + fx.v*dt
			var steam: bool = fx.get("steam",false)
			var fluid: bool = fx.get("fluid",false)
			if not (steam or fluid):
				fx.v.y -= dt*8
			var elapsed := dt+float(fx.get("hidden_time",0.0))
			if effect_in_view(frustum,at,.5):
				node.position = at
				if not fluid:
					node.scale *= pow(2.6 if steam else .11,elapsed)
				if not node.visible:
					node.show()
					fx.erase("position")
					fx.erase("hidden_time")
			else:
				if node.visible: node.hide()
				fx.position = at
				fx.hidden_time = elapsed

func _physics_process(dt: float) -> void:
	if not sector_checkpoint.is_empty(): return
	var audio_paused := not OnKlavGame.session_started or OnKlavGame.paused or phase != "combat"
	if is_instance_valid(sporeworks_drive):
		sporeworks_drive.stream_paused = audio_paused
		sporeworks_valve.stream_paused = audio_paused
		sporeworks_steam_voice.stream_paused = audio_paused
	for entry in chamber_lanes:
		if entry.has("hiss"):
			(entry.hiss as AudioStreamPlayer3D).stream_paused = audio_paused
	var score_level := -9.0
	if is_instance_valid(room) and room.has_meta("breach_ambience"):
		var ambience: AudioStreamPlayer3D = room.get_meta("breach_ambience")
		ambience.stream_paused = audio_paused
		if not audio_paused:
			if not ambience.playing and DisplayServer.get_name() != "headless":
				ambience.play()
			var engaged := enemies.any(func(e): return bool(e.get_meta("alert",false)))
			score_level = -7.0 if engaged else -11.0
	music.volume_db = move_toward(music.volume_db,score_level,dt*2.6)
	sync_web_score()
	if audio_paused:
		for enemy in enemies:
			if enemy.has_meta("charge_sound"):
				var voice: AudioStreamPlayer3D = enemy.get_meta("charge_sound")
				if voice.playing: voice.stop()
	if not OnKlavGame.session_started or OnKlavGame.paused: return
	if phase == "won" and victory_elapsed < VICTORY_REVEAL:
		victory_elapsed = minf(VICTORY_REVEAL,victory_elapsed+dt)
		update_victory_reveal()
		hud.queue_redraw()
	if phase == "briefing" and stage == 1 and intro_elapsed < INTRO_TIME:
		tick_opening_intro(dt)
		hud.queue_redraw()
	if phase in ["combat","portal"]:
		hud.queue_redraw()
		update_view_weapon(dt)
	if Time.get_ticks_msec()>=retry_best_at:
		if retry_best_read:
			load_best()
		elif highest>stored_best and not saving_best:
			persist_best()
	if phase=="portal":
		tick_cosmetic_effects(dt)
		tick_portal(dt)
		return
	if phase != "combat":
		# A shot already resolving when the player died can leave a final burst.
		if not (motes.is_empty() and blast_fx.is_empty() and death_marks.is_empty()):
			clear_cosmetic_effects()
		return
	active_time += dt
	banner_time = maxf(0,banner_time-dt)
	streak_timer = maxf(0,streak_timer-dt)
	if streak_timer == 0: streak=0
	damage_time = maxf(0,damage_time-dt)
	hit_mark = maxf(0,hit_mark-dt)
	damage_cue_left = maxf(0.0,damage_cue_left-dt)
	fire_clock = maxf(0,fire_clock-dt)
	if reload_left > 0:
		reload_left=maxf(0,reload_left-dt)
		if reload_left==0:
			rounds[weapon]=CAPACITY[weapon]
			sfx("swap")
	elif rounds[weapon]==0:
		begin_reload()
	recoil = move_toward(recoil,0,dt*(3.45 if weapon==1 else 7.4))
	dash_clock = maxf(0,dash_clock-dt)
	controls.dash_fill = 1.0-dash_clock/2.1
	dash_left = maxf(0,dash_left-dt)
	var turn := Vector2(float(Input.is_key_pressed(KEY_RIGHT))-float(Input.is_key_pressed(KEY_LEFT)),float(Input.is_key_pressed(KEY_DOWN))-float(Input.is_key_pressed(KEY_UP)))
	look(turn*dt*1.65)
	var axis := movement()
	var wanted := (player.basis*Vector3(axis.x,0,axis.y))*(5.45+speed_bonus*.48)
	if dash_left>0: wanted=dash_direction*DASH_SPEED
	player.velocity = player.velocity.lerp(wanted,1.0-exp(-dt*18))
	player.move_and_slide()
	tick_access_route(dt)
	if phase != "combat": return
	move_bob += dt*axis.length()*11.0
	if fire_tap or controls.is_held("fire") or Input.is_key_pressed(KEY_CTRL) or Input.is_key_pressed(KEY_SPACE): fire()
	fire_tap = false
	if phase!="combat": return
	tick_exploration()
	tick_weapon_pickup(dt)
	tick_nail_pickup(dt)
	tick_breach(dt)
	tick_sporeworks(dt)
	tick_hub_chamber()
	if phase != "combat": return
	tick_ambush(dt)
	spawn_clock -= dt
	if pending>0 and spawn_clock<=0 and enemies.size()<13:
		spawn_next()
		spawn_clock = .28 if stage<3 else .21
	tick_enemies(dt)
	if phase!="combat": return
	tick_orbs(dt)
	if phase != "combat": return
	tick_mortar(dt)
	if phase != "combat": return
	tick_atmosphere(dt)
	if phase != "combat": return
	tick_cosmetic_effects(dt)
	if phase!="combat": return
	for kit in pickups:
		if not kit.visible: continue
		kit.rotation.y += dt
		if health<100 and kit.position.distance_to(player.position)<.9:
			health = mini(100,health+28)
			kit.hide()
			show_banner("BIOGEL  +28")
			sfx("heal")
	if expedition_mode:
		if secret_supply != null and secret_supply.visible:
			secret_supply.rotation.y += dt*1.3
			if secret_supply.position.distance_to(player.position)<1.0:
				secret_supply.hide()
				health = mini(100,health+15)
				score += 250
				show_banner("HIDDEN SUPPLY   +250")
				sfx("heal")
		tick_objectives(dt)
		return
	if pending==0 and enemies.is_empty():
		if wave<2:
			wave += 1
			begin_wave()
		else:
			update_portal_approach()

func clear_orbs() -> void:
	for o in orbs: discard(o.node)
	orbs.clear()
	for projectile in mortar_rounds: discard(projectile.node)
	mortar_rounds.clear()

func request_sector_advance(perk: int) -> void:
	if phase != "upgrade" or not sector_checkpoint.is_empty(): return
	if not expedition_mode:
		advance(perk)
		return
	start_sector_transition()
	sector_steps.append(func():
		apply_sector_upgrade(perk)
		build_room(true)
		sector_steps.append(func():
			queue_encounter()
			sector_steps.append(finish_sector_advance)
		)
	)

func start_sector_transition() -> void:
	# A pause during setup saves the last fully built state.
	sector_checkpoint = snapshot()
	sector_view_mode = world.render_target_update_mode
	world.render_target_update_mode = SubViewport.UPDATE_DISABLED
	sector_frame_ready = false
	update_overlay()

func clear_sector_transition() -> void:
	world.render_target_update_mode = sector_view_mode
	sector_steps.clear()
	sector_checkpoint.clear()
	sector_frame_ready = false

func advance(perk: int) -> void:
	if phase != "upgrade" or not sector_checkpoint.is_empty(): return
	apply_sector_upgrade(perk)
	build_room()
	finish_sector_advance()

func apply_sector_upgrade(perk: int) -> void:
	match perk:
		0: health=mini(100,health+45)
		1: power+=1
		2: speed_bonus+=1
	stage+=1
	access_route = expedition_mode
	access_state = 0
	culture_sealed = 0
	sporeworks_cleared = 0
	breach_state = Breach.INACTIVE
	breach_clock = 0.0
	wave=1
	objective_mask=0
	phase="briefing" if expedition_mode else "combat"
	dash_left=0
	damage_time=0
	damage_cue_left=0.0
	damage_cue_angle=0.0
	fire_tap=false

func finish_sector_advance() -> void:
	reset_player()
	if not sector_checkpoint.is_empty():
		clear_sector_transition()
	if not expedition_mode: begin_wave()
	update_overlay()
	OnKlavGame.checkpoint()

func finish(next: String) -> void:
	phase = next
	clear_orbs()
	clear_cosmetic_effects()
	controls.reset_input()
	if next == "won":
		victory_elapsed = 0.0 if not reduced_motion() else VICTORY_REVEAL
		damage_time = 0.0
		if not sounds.has("victory"):
			sounds["victory"] = machine_sound("victory")
		sfx("victory")
	else:
		sfx("death")
	highest=maxi(highest,score)
	update_overlay()
	OnKlavGame.checkpoint()

func persist_best() -> void:
	if not best_loaded:
		load_best()
		return
	saving_best=true
	var target := highest
	if await OnKlavGame.save_data("profile",{"best":target}):
		stored_best=maxi(stored_best,target)
	else:
		retry_best_at=Time.get_ticks_msec()+2000
	saving_best=false

func reduced_motion() -> bool:
	return is_instance_valid(OnKlavGame.chrome) and OnKlavGame.chrome.reduce_motion

func sync_web_score() -> void:
	if web_score == null or not is_instance_valid(OnKlavGame.chrome): return
	var chrome = OnKlavGame.chrome
	var effects_enabled: bool = OnKlavGame.session_started and not OnKlavGame.paused and not chrome.muted
	var enabled: bool = effects_enabled and chrome.music
	var level: float = float(chrome.volume)*db_to_linear(snappedf(music.volume_db,.5))
	var state := [enabled,level,effects_enabled,chrome.volume]
	if state == web_score_state: return
	web_score_state = state
	if enabled and level > 0.0 and not web_score_supplied:
		var track := FileAccess.get_file_as_bytes(SCORE_PATH)
		if not track.is_empty():
			web_score.provideTrack(Marshalls.raw_to_base64(track))
			web_score_supplied = true
	web_score.configure(enabled,level,float(chrome.volume) if effects_enabled else 0.0)

func _exit_tree() -> void:
	if web_score != null: web_score.configure(false,0,0)

func init_audio() -> void:
	if OS.has_feature("web"):
		web_score = JavaScriptBridge.get_interface("shroomScore")
	music=AudioStreamPlayer.new()
	music.bus="Music"
	music.playback_type=AudioServer.PLAYBACK_TYPE_STREAM
	music.volume_db=-9
	if web_score == null:
		var song := AudioStreamOggVorbis.load_from_buffer(FileAccess.get_file_as_bytes(SCORE_PATH))
		if song != null:
			song.loop = true
			music.stream = song
	add_child(music)
	for key in ["nail","break","hit","pop","dash","swap","heal","hurt","spit","wave","clear","death","reload","portal"]:
		sounds[key]=load("res://assets/"+key+".wav")
	for key in ["drive","lab_air","pressure","furnace_surge","charge","arc_fire","grav_fire","grav_blast","arc_crack","victory"]:
		sounds[key] = machine_sound(key)
	for i in range(8):
		var voice:=AudioStreamPlayer.new()
		voice.bus="Effects"
		voice.volume_db=-11
		add_child(voice)
		voices.append(voice)

func spatial_effect(parent: Node3D, at: Vector3, key: String, volume: float, reach: float) -> AudioStreamPlayer3D:
	if not sounds.has(key):
		sounds[key] = machine_sound(key)
	var voice := AudioStreamPlayer3D.new()
	voice.bus = "Effects"
	voice.stream = sounds[key]
	voice.position = at
	voice.volume_db = volume
	voice.max_distance = reach
	voice.unit_size = 2.0
	parent.add_child(voice)
	return voice

func machine_sound(key: String) -> AudioStreamWAV:
	var rate := 16000
	var duration: float = {"drive":1.0,"charge":.52,"furnace_surge":2.15,"victory":1.65,"lab_air":2.0,"arc_fire":.21,"grav_fire":.47,"grav_blast":.30,"arc_crack":.16}.get(key,.96)
	var victory_notes := [261.63,329.63,392.0,523.25]
	var count := roundi(duration*rate)
	var data := PackedByteArray()
	data.resize(count*2)
	var noise := RandomNumberGenerator.new()
	noise.seed = 821
	for i in count:
		var t := float(i)/rate
		var p := t/duration
		var value := 0.0
		match key:
			"drive":
				value = sin(TAU*52.0*t)*.18 + sin(TAU*104.0*t)*(.06+.055*sin(TAU*7.0*t))
			"arc_fire":
				var envelope := smoothstep(0.0,.03,p)*(1.0-smoothstep(.22,1.0,p))
				value = envelope*(sin(TAU*(765.0*t-1400.0*t*t))*.31+noise.randf_range(-.21,.21))
			"arc_crack":
				value = noise.randf_range(-.48,.48)*pow(1.0-p,3.0)*sin(TAU*42.0*t)
			"grav_blast":
				value = (sin(TAU*(75.0*t-40.0*t*t))*.52+noise.randf_range(-.29,.29))*pow(1.0-p,2.3)
			"grav_fire":
				var envelope := smoothstep(0.0,.025,p)*(1.0-smoothstep(.16,1.0,p))
				value = envelope*(sin(TAU*(116.0*t-77.0*t*t))*.43+noise.randf_range(-.24,.24)*(1.0-p))
			"lab_air":
				var pulse := .62+.38*sin(TAU*1.5*t)
				value = sin(TAU*61.0*t)*.12 + sin(TAU*122.0*t)*.035 + sin(TAU*187.5*t)*.038*pulse
			"pressure":
				var envelope := smoothstep(0.0,.07,p)*(1.0-smoothstep(.26,1.0,p))
				value = envelope*(noise.randf_range(-.42,.42)+sin(TAU*68.0*t)*.09)
			"furnace_surge":
				var rise := smoothstep(0.0,.22,t)
				var blast := smoothstep(.74,.88,t)
				var decay := 1.0-smoothstep(1.45,duration,t)
				value = rise*decay*(sin(TAU*(47.0*t+17.0*t*t))*.26
					+ noise.randf_range(-.40,.40)*(.18+.82*blast))
			"victory":
				for note in victory_notes.size():
					var age := t-note*.17
					if age < 0.0: continue
					var frequency: float = victory_notes[note]
					var envelope := smoothstep(0.0,.045,age)*exp(-age*2.6)*(1.0-smoothstep(1.27,duration,t))
					value += envelope*(sin(TAU*frequency*age)*.18+sin(TAU*frequency*2.0*age)*.045)
			"charge":
				var envelope := smoothstep(0.0,.25,p)*(1.0-smoothstep(.88,1.0,p))
				value = envelope*(sin(TAU*(91.0*t+130.0*t*t)+sin(TAU*39.0*t)*.78)*.28
					+ noise.randf_range(-.11,.11))
		data.encode_s16(i*2,roundi(value*32767.0))
	var stream := AudioStreamWAV.new()
	stream.format = AudioStreamWAV.FORMAT_16_BITS
	stream.mix_rate = rate
	stream.data = data
	if key in ["drive","lab_air"]:
		stream.loop_mode = AudioStreamWAV.LOOP_FORWARD
		stream.loop_end = count
	return stream

func sfx(key: String, pitch_multiplier := 1.0) -> void:
	if DisplayServer.get_name()=="headless" or voices.is_empty() or sounds.get(key)==null: return
	if is_instance_valid(OnKlavGame.chrome) and OnKlavGame.chrome.muted: return
	var pitch_scale := pitch_multiplier * (randf_range(.94,1.06) if key in ["nail","pop","hit"] else 1.0)
	var sound: AudioStreamWAV = sounds[key]
	if web_score != null and sound.format == AudioStreamWAV.FORMAT_16_BITS:
		if not web_effects_supplied.has(key):
			web_score.provideEffect(key,Marshalls.raw_to_base64(sound.data),sound.mix_rate,2 if sound.stereo else 1)
			web_effects_supplied[key] = true
		web_score.playEffect(key,pitch_scale)
		return
	var voice := voices[voice_index]
	voice_index=(voice_index+1)%voices.size()
	voice.stream=sound
	voice.pitch_scale = pitch_scale
	voice.play()

func make_overlay() -> void:
	var scroll := ScrollContainer.new()
	scroll.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.mouse_filter = Control.MOUSE_FILTER_PASS
	add_child(scroll)
	var center := CenterContainer.new()
	center.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	center.size_flags_vertical = Control.SIZE_EXPAND_FILL
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	scroll.add_child(center)
	title_panel=PanelContainer.new()
	scroll.resized.connect(func(): title_panel.custom_minimum_size.x = minf(680.0,maxf(0.0,scroll.size.x-36.0)))
	title_panel.visibility_changed.connect(func(): scroll.visible = title_panel.visible)

	var skin := StyleBoxFlat.new()
	skin.bg_color=Color("#141f28")
	skin.border_color=Color("#507a73")
	skin.set_border_width_all(2)
	skin.set_corner_radius_all(16)
	skin.content_margin_left=32
	skin.content_margin_right=32
	skin.content_margin_top=22
	skin.content_margin_bottom=22
	title_panel.add_theme_stylebox_override("panel",skin)
	center.add_child(title_panel)
	result_box=VBoxContainer.new()
	result_box.add_theme_constant_override("separation",10)
	title_panel.add_child(result_box)
	result_title=Label.new()
	result_title.add_theme_font_size_override("font_size",38)
	result_title.add_theme_color_override("font_color",TEAL)
	result_title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
	result_title.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART
	result_box.add_child(result_title)
	result_objective = Label.new()
	result_objective.add_theme_font_size_override("font_size",34)
	result_objective.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	result_objective.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	result_box.add_child(result_objective)
	result_info=Label.new()
	result_info.add_theme_font_size_override("font_size",28)
	result_info.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
	result_info.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART
	result_box.add_child(result_info)
	title_panel.hide()
	prefs = Button.new()
	prefs.text="Touch controls"
	prefs.add_theme_font_size_override("font_size",23)
	add_child(prefs)
	prefs.hide()
	prefs.offset_left=14
	prefs.offset_right=213
	prefs.offset_top=92
	prefs.offset_bottom=143
	prefs.pressed.connect(func(): controls.show_settings(self))

func overlay_button(label: String, callback: Callable) -> void:
	var button := Button.new()
	button.text=label
	button.text_overrun_behavior=TextServer.OVERRUN_TRIM_ELLIPSIS
	button.custom_minimum_size.y=66
	button.add_theme_font_size_override("font_size",28)
	var skin := StyleBoxFlat.new()
	skin.bg_color=Color("#276251")
	skin.border_color=Color("#77c5ab")
	skin.set_border_width_all(2)
	skin.set_corner_radius_all(13)
	button.add_theme_stylebox_override("normal",skin)
	var hover := skin.duplicate() as StyleBoxFlat
	hover.bg_color=Color("#347e68")
	button.add_theme_stylebox_override("hover",hover)
	button.add_theme_stylebox_override("pressed",hover)
	result_box.add_child(button)
	button.pressed.connect(callback)

func tick_opening_intro(dt: float) -> void:
	intro_elapsed = minf(INTRO_TIME,intro_elapsed+dt)
	var ease := smoothstep(0.0,INTRO_TIME,intro_elapsed)
	if not reduced_motion():
		player.rotation.y = lerpf(-.24,0.0,ease)
		camera.rotation.x = lerpf(.12,0.0,ease)
	if intro_elapsed >= INTRO_TIME:
		player.rotation.y = 0.0
		camera.rotation.x = 0.0
		update_overlay()

func update_victory_reveal() -> void:
	var progress := 1.0 if reduced_motion() else clampf(victory_elapsed/VICTORY_REVEAL,0.0,1.0)
	result_title.pivot_offset = result_title.size*.5
	var settle := smoothstep(0.0,.45,progress)
	result_title.scale = Vector2.ONE*(.86+.14*settle+.055*sin(settle*PI))
	result_title.modulate.a = lerpf(.55,1.0,settle)

func update_overlay() -> void:
	if is_instance_valid(view_weapon):
		view_weapon.visible = phase in ["combat","portal"]
	var opening := phase == "briefing" and stage == 1 and intro_elapsed < INTRO_TIME
	title_panel.visible=phase not in ["combat","portal"] and not opening
	var won := phase == "won"
	var skin := title_panel.get_theme_stylebox("panel") as StyleBoxFlat
	skin.bg_color = Color("#161d2b") if won else Color("#141f28")
	skin.border_color = Color("#dcad69") if won else Color("#507a73")
	result_title.add_theme_font_size_override("font_size",64 if won else 38)
	result_objective.visible = phase == "briefing"
	result_info.visible = phase != "briefing" or stage != 1
	result_title.add_theme_color_override("font_color",GOLD if won else TEAL)
	result_title.scale = Vector2.ONE
	result_title.modulate.a = 1.0
	controls.dash_fill=1.0-dash_clock/2.1
	controls.enabled=phase=="combat"
	controls.visible=phase=="combat"
	prefs.visible=phase=="combat"
	for i in range(result_box.get_child_count()-1,2,-1):
		discard(result_box.get_child(i))
	if not sector_checkpoint.is_empty():
		result_objective.hide()
		result_info.show()
		result_title.text="PREPARING SECTOR"
		result_info.text="Setting up the next area…"
	elif phase=="briefing":
		result_title.text="SECTOR %d  ·  %s" % [stage,STAGES[stage-1]]
		result_objective.text = [
			"Recover %d spore samples." % Expedition.COUNTS[0],
			"Reset %d ventilation relays." % Expedition.COUNTS[1],
			"Recover %d culture canisters." % Expedition.COUNTS[2],
			"Shut down %d root pumps." % Expedition.COUNTS[3],
			Expedition.OBJECTIVES[4]
		][stage-1]
		result_info.text = SECTOR_BRIEFINGS[stage-1]
		if not opening:
			overlay_button("Play" if stage==1 else "Enter sector",request_expedition_entry)
	elif phase=="upgrade":
		result_title.text="SECTOR CLEAR"
		result_info.text="Pick one upgrade for the next sector"
		overlay_button("BIOGEL  /  Restore 45 health",request_sector_advance.bind(0))
		overlay_button("HOT LOAD  /  More weapon damage",request_sector_advance.bind(1))
		overlay_button("QUICKROOT  /  Faster movement",request_sector_advance.bind(2))
	elif phase=="won" or phase=="dead":
		result_title.text="YOU WIN" if won else "YOU GOT MULCHED"
		if not won and waste_surface != null and player.position.y < -.3:
			result_title.text = "FATAL WASTE EXPOSURE"
		result_info.text="%s points   ·   Best %s\n%d caps down   ·   %d:%02d" % [score,highest,kills,int(active_time)/60,int(active_time)%60]
		if won:
			result_info.text = "CAP KING DEFEATED\nSURFACE SHELTER CONFIRMS CONTAINMENT\n" + result_info.text
			update_victory_reveal()
		overlay_button("Play again",func():OnKlavGame.replay())
	hud.queue_redraw()

func v3(p: Vector3) -> Array:
	return [p.x,p.y,p.z]

func vec(value: Array) -> Vector3:
	return Vector3(float(value[0]),float(value[1]),float(value[2]))

func canister_survival() -> Array:
	var result := []
	for i in range(SPORE_CANISTER_LIMIT):
		var id = [0,2,6,8][(i+stage-1)%4]
		var original := expedition.center(id)+Vector3(2.6,0,2.5)
		result.append(canisters.any(func(prop): return prop.position.distance_to(original)<.01))
	return result

func snapshot() -> Dictionary:
	if not sector_checkpoint.is_empty(): return sector_checkpoint.duplicate(true)
	# Briefing saves regenerate the dormant encounter from its original RNG state.
	var live: Array = []
	for e in ([] if encounter_prepared else enemies):
		var entry := {"p":v3(e.position),"kind":e.get_meta("type"),"hp":e.get_meta("hp"),"attack":e.get_meta("attack"),"alert":e.get_meta("alert"),"stagger":e.get_meta("stagger",0.0),"attack_warning":e.get_meta("attack_warning",0.0)}
		if e.get_meta("breach_guard", false):
			entry["breach_guard"] = true
		entry["arc_time"] = e.get_meta("arc_time",0.0)
		entry["arc_lethal"] = e.get_meta("arc_lethal",false)
		if int(e.get_meta("type")) == 3:
			entry["boss_cycle"] = e.get_meta("boss_cycle",3.8)
			entry["boss_windup"] = e.get_meta("boss_windup",0.0)
			entry["boss_phase"] = e.get_meta("boss_phase",0)
			entry["boss_exposed"] = e.get_meta("boss_exposed", 0.0)
			entry["boss_phase_since"] = e.get_meta("boss_phase_since", 0.0)
		live.append(entry)
	var active_dash := dash_left if phase == "combat" else 0.0
	var shots: Array = []
	for o in orbs:
		shots.append({"p":v3(o.node.position),"v":v3(o.velocity),"life":o.life,"damage":o.damage})
	return {"layout":int(expedition_mode),"objective_mask":objective_mask,"seed":seed_code,"stage":stage,"wave":wave,"pending":pending,"phase":phase,
		"health":health,"weapon":weapon,"weapon_unlocks":weapon_unlocks,"nail_charges":nail_charges,"nail_pickups":nail_pickups_collected,"rounds":rounds.duplicate(),"reload":reload_left,"portal":portal_elapsed,
		"stains":stains.map(func(stain):return v3(stain.position)),"score":score,"kills":kills,"power":power,"speed":speed_bonus,
		"rng":str(encounter_rng_state if encounter_prepared else rng.state),"spawn_delay":maxf(0,spawn_clock),"streak":streak,"streak_timer":streak_timer,
		"dash_cooldown":dash_clock,"dash_left":active_dash,
		"dash_direction":v3(dash_direction if active_dash > 0.0 else Vector3.ZERO),
		"dash_velocity":v3(player.velocity if active_dash > 0.0 else Vector3.ZERO),
		"damage_cooldown":damage_time,"fire_cooldown":fire_clock,"time":active_time,"position":v3(player.position),"yaw":player.rotation.y,"pitch":pitch,
		"enemies":live,"orbs":shots,"mortar":mortar_rounds.map(func(o):return {"p":v3(o.node.position),"v":v3(o.velocity),"life":o.life}),"kits":pickups.map(func(kit):return kit.visible),"canisters":canister_survival(),
		"secret_found":secret_supply != null and not secret_supply.visible,"access_route":access_route,"access_state":access_state,
		"explored_rooms": explored_rooms_mask,
		"hazard_clock": hazard_clock,
		"hazard_cooldown": clampf(hazard_next_hit-hazard_clock,0.0,HAZARD_HIT_INTERVAL),
		"ambush_used":ambush_used_mask,"ambush_room":ambush_pending_room,"ambush_delay":ambush_timer,"last_room":previous_player_room,
		"breach": int(breach_state), "breach_clock": breach_clock, "sporeworks": sporeworks_cleared, "practice_complete": practice_complete, "culture_sealed": culture_sealed, "controls":controls.settings()}

func numeric(value: Variant, low: float, high: float, whole := false) -> bool:
	return (value is int or value is float) and is_finite(value) and value>=low and value<=high and (not whole or float(value)==floorf(value))

func vector_ok(value: Variant, bound: float) -> bool:
	if not value is Array or value.size()!=3: return false
	return value.all(func(v): return numeric(v,-bound,bound))

func restore(d: Dictionary) -> bool:
	if d.get("phase") not in ["briefing","combat","portal","upgrade","won","dead"]: return false
	for item in [["seed",1,2000000000],["stage",1,MAX_STAGE],["wave",1,2],["pending",0,16],
		["health",0,100],["weapon",0,WEAPON_NAMES.size()-1],["score",0,100000000],["kills",0,200],
		["power",0,4],["speed",0,4]]:
		if not numeric(d.get(item[0]),item[1],item[2],true): return false
	var route_enabled: Variant = d.get("access_route",false)
	var saved_access: Variant = d.get("access_state",0)
	if not route_enabled is bool or not numeric(saved_access,0,2,true): return false
	if int(saved_access) != 0 and (not route_enabled or int(d.stage) not in [2,4] or d.phase == "briefing"): return false
	if route_enabled and int(d.stage) in [2,4] and d.phase in ["portal","upgrade"] and int(saved_access) != 2: return false
	var layout: Variant = d.get("layout",0)
	if not numeric(layout,0,1,true): return false
	var room_bound := 15.1 if layout==1 else ARENA
	var count: int = Expedition.COUNTS[int(d.stage)-1] if layout==1 else 0
	if not numeric(d.get("objective_mask",0),0,(1<<count)-1,true): return false
	if d.get("phase")=="briefing" and (layout!=1 or d.get("objective_mask",0)!=0): return false
	if not numeric(d.get("time"),0,100000) or not vector_ok(d.get("position"),room_bound) or not numeric(d.get("yaw"),-PI,PI) or not numeric(d.get("pitch"),-.83,.83): return false
	var saved_rounds: Variant = d.get("rounds",CAPACITY)
	var unlocked: Variant = d.get("weapon_unlocks",3)
	if not numeric(d.get("nail_charges",0),0,30,true) or not numeric(d.get("nail_pickups",0),0,31,true): return false
	if not numeric(unlocked,3,15,true) or (int(unlocked) & 3)!=3 or (int(unlocked) & (1 << int(d.weapon)))==0: return false
	if not saved_rounds is Array or saved_rounds.size() not in [2,CAPACITY.size()]: return false
	if saved_rounds.size()==2 and int(unlocked)!=3: return false
	for i in saved_rounds.size():
		if not numeric(saved_rounds[i],0,CAPACITY[i],true): return false
	if not numeric(d.get("reload",0),0,RELOAD_TIME[int(d.weapon)]): return false
	if not numeric(d.get("portal",0),0,PORTAL_TIME): return false
	if not d.get("enemies") is Array or d.enemies.size()>13 or not d.get("orbs") is Array or d.orbs.size()>ORB_LIMIT: return false
	if d.phase=="portal":
		if layout==0 and (d.get("wave")!=2 or d.get("pending")!=0 or d.get("enemies")!=[]): return false
		if layout==1 and int(d.get("objective_mask",0))!=(1<<count)-1: return false
	var saved_stains: Variant = d.get("stains",[])
	if not saved_stains is Array or saved_stains.size()>STAIN_LIMIT: return false
	for stain in saved_stains:
		if not vector_ok(stain,room_bound): return false
	if (d.phase=="dead") != (int(d.health)==0): return false
	if (d.phase=="upgrade" and int(d.stage)>=MAX_STAGE) or (d.phase=="won" and int(d.stage)!=MAX_STAGE): return false
	if d.get("phase")=="briefing" and (d.get("pending")!=0 or d.get("enemies")!=[] or d.get("orbs")!=[]): return false
	for e in d.enemies:
		if not e is Dictionary or not vector_ok(e.get("p"),room_bound) or not numeric(e.get("kind"),0,TYPE_HP.size()-1,true): return false
		if not numeric(e.get("hp"),1,TYPE_HP[int(e.kind)],true) or not numeric(e.get("attack"),-.101,2.45): return false
		if not numeric(e.get("stagger",0.0),0,HIT_STAGGER_TIME): return false
		if not numeric(e.get("arc_time",0.0),0,1.0) or not e.get("arc_lethal",false) is bool: return false
		if e.get("arc_lethal",false) and (e.get("arc_time",0.0)<=0.0 or int(e.hp)!=1): return false
		if not numeric(e.get("attack_warning",0.0),0,SPITTER_CHARGE_TIME): return false
	for e in d.enemies:
		if int(e.kind)==3:
			if d.phase=="portal" or not numeric(e.get("boss_exposed", 0.0), 0, BOSS_EXPOSE_TIME): return false
			if not numeric(e.get("boss_phase_since", 0.0), 0, float(d.time)): return false
			if not numeric(e.get("boss_cycle",3.8),0,5.4) or not numeric(e.get("boss_windup",0.0),0,BOSS_VOLLEY_WINDUP) or not numeric(e.get("boss_phase",0),0,2,true): return false
	var saved_mortar: Variant = d.get("mortar",[])
	if not saved_mortar is Array or saved_mortar.size()>3 or (d.phase!="combat" and not saved_mortar.is_empty()): return false
	for projectile in saved_mortar:
		if not projectile is Dictionary or not vector_ok(projectile.get("p"),40) or not vector_ok(projectile.get("v"),MORTAR_SPEED+.01): return false
		if not numeric(projectile.get("life"),.000001,MORTAR_LIFE) or absf(vec(projectile.v).length()-MORTAR_SPEED)>.01: return false
	for o in d.orbs:
		if not o is Dictionary or not vector_ok(o.get("p"),28) or not vector_ok(o.get("v"),8) or not numeric(o.get("life"),0,3.6) or not numeric(o.get("damage"),9,14,true) or int(o.damage) not in [9,10,14]: return false
	if not d.get("kits") is Array or d.kits.size()!=2 or not d.kits.all(func(k):return k is bool): return false
	var prop_state = d.get("canisters",[true,true,true])
	if not prop_state is Array or prop_state.size()!=SPORE_CANISTER_LIMIT or not prop_state.all(func(flag):return flag is bool): return false
	if not d.get("secret_found",false) is bool: return false
	if not numeric(d.get("explored_rooms",1 << Expedition.ENTRY),0,511,true): return false
	if layout==1 and (int(d.get("explored_rooms",1 << Expedition.ENTRY)) & (1 << Expedition.ENTRY)) == 0: return false
	if not numeric(d.get("ambush_used",0),0,511,true): return false
	if not numeric(d.get("ambush_room",-1),-1,8,true): return false
	if not numeric(d.get("ambush_delay",0.0),0,AMBUSH_DELAY): return false
	if not numeric(d.get("last_room",Expedition.ENTRY),0,8,true): return false
	if int(d.get("ambush_room",-1)) >= 0 and (int(d.get("ambush_used",0)) & (1 << int(d.ambush_room))) != 0: return false
	var rng_state = d.get("rng")
	if rng_state != null and (not rng_state is String or rng_state.length()>20 or not rng_state.is_valid_int()): return false
	for item in [["spawn_delay",1.0],["streak",9],["streak_timer",3.25],["dash_cooldown",2.1],["damage_cooldown",.48],["fire_cooldown",FIRE_DELAY.max()]]:
		if not numeric(d.get(item[0],0),0,item[1],item[0]=="streak"): return false
	if not numeric(d.get("breach", 0), 0, Breach.CLEAR, true) or not numeric(d.get("breach_clock", 0), 0, BREACH_WARNING_TIME):
		return false
	if d.get("breach", 0) != Breach.INACTIVE and (layout != 1 or int(d.stage) != 1):
		return false
	var cleared_lanes: Variant = d.get("sporeworks", 0)
	if not numeric(cleared_lanes, 0, SPOREWORKS_TENDRIL | SPOREWORKS_POD, true):
		return false
	if cleared_lanes != 0 and (layout != 1 or int(d.stage) != 2):
		return false
	var saved_dash: Variant = d.get("dash_left",0.0)
	var saved_heading: Variant = d.get("dash_direction",[0.0,0.0,0.0])
	var saved_velocity: Variant = d.get("dash_velocity",[0.0,0.0,0.0])
	if not numeric(saved_dash,0,DASH_TIME) or not vector_ok(saved_heading,1.01) or not vector_ok(saved_velocity,DASH_SPEED+.01):
		return false
	var heading := vec(saved_heading)
	var dash_velocity := vec(saved_velocity)
	if float(saved_dash) > 0.0 and (d.phase != "combat" or not is_zero_approx(heading.y)
		or not heading.is_normalized() or dash_velocity.length() > DASH_SPEED+.01):
		return false
	var saved_hazard_clock: Variant = d.get("hazard_clock",0.0)
	var saved_hazard_cooldown: Variant = d.get("hazard_cooldown",0.0)
	if not numeric(saved_hazard_clock,0,100000) or not numeric(saved_hazard_cooldown,0,HAZARD_HIT_INTERVAL):
		return false
	var sealed: Variant = d.get("culture_sealed", 0)
	if not numeric(sealed, 0, 3, true) or (sealed != 0 and (layout != 1 or int(d.stage) != 3)):
		return false
	if not d.get("controls") is Dictionary or not controls.apply_settings(d.controls): return false
	culture_sealed = int(sealed)
	sporeworks_cleared = int(cleared_lanes)
	practice_complete = d.get("practice_complete", false) == true
	breach_state = int(d.get("breach", 0)) as Breach
	breach_clock = float(d.get("breach_clock", 0))
	expedition_mode=layout==1
	objective_mask=int(d.get("objective_mask",0))
	seed_code=int(d.seed)
	stage=int(d.stage)
	wave=int(d.wave)
	pending=int(d.pending)
	health=int(d.health)
	weapon=int(d.weapon)
	weapon_unlocks=int(unlocked)
	nail_charges=int(d.get("nail_charges",0))
	nail_pickups_collected=int(d.get("nail_pickups",0))
	intro_elapsed=INTRO_TIME
	hit_mark = 0.0
	hit_defeated = false
	score=int(d.score)
	kills=int(d.kills)
	power=int(d.power)
	speed_bonus=int(d.speed)
	active_time=float(d.time)
	phase=str(d.phase)
	access_route = route_enabled and layout == 1
	access_state = int(saved_access) if access_route else 0
	victory_elapsed = VICTORY_REVEAL
	rng.seed=seed_code+stage*991+kills*499+pending
	build_room()
	hazard_clock = float(saved_hazard_clock)
	hazard_next_hit = hazard_clock+float(saved_hazard_cooldown)
	reset_player()
	for i in saved_rounds.size():
		rounds[i]=int(saved_rounds[i])
	reload_left=float(d.get("reload",0))
	portal_elapsed=float(d.get("portal",0))
	for stain in saved_stains: add_stain(vec(stain))
	player.position=clear_saved_cover_position(vec(d.position))

	player.rotation.y=float(d.yaw)
	pitch=float(d.pitch)
	camera.rotation.x=pitch
	for e in d.enemies:
		var actor:=spawn_enemy(int(e.kind),clear_saved_cover_position(vec(e.p)),int(e.hp))
		actor.set_meta("attack",float(e.attack))
		actor.set_meta("arc_time",float(e.get("arc_time",0.0)))
		actor.set_meta("arc_lethal",e.get("arc_lethal",false))
		actor.set_meta("stagger",float(e.get("stagger",0.0)))
		actor.set_meta("attack_warning",float(e.get("attack_warning",0.0)))
		actor.set_meta("alert",e.get("alert",false)==true)
		actor.set_meta("pursuit_left",PURSUIT_MEMORY if actor.get_meta("alert") else 0.0)
		if e.get("breach_guard", false) == true:
			actor.set_meta("breach_guard", true)
		if int(e.kind)==3:
			actor.set_meta("boss_cycle",float(e.get("boss_cycle",3.8)))
			actor.set_meta("boss_windup",float(e.get("boss_windup",0.0)))
			actor.set_meta("boss_phase",int(e.get("boss_phase",0)))
			actor.set_meta("boss_exposed",float(e.get("boss_exposed",0.0)))
			actor.set_meta("boss_phase_since",float(e.get("boss_phase_since",0.0)))
			update_cap_king_art(actor)
	var gate_ready := portal_ready()
	set_portal_display(gate_ready,phase=="portal" or (gate_ready and near_exit_gate()))
	if rng_state != null: rng.state=int(rng_state)
	for o in d.orbs:
		var node:=mesh(actors,ball(),vec(o.p),material(PINK,1.55),Vector3.ONE*.32)
		orbs.append({"node":node,"velocity":vec(o.v),"life":float(o.life),"damage":int(o.damage)})
	for projectile in saved_mortar:
		launch_mortar(vec(projectile.p),vec(projectile.v),float(projectile.life))
	for i in range(2): pickups[i].visible=d.kits[i]
	explored_rooms_mask = int(d.get("explored_rooms",1 << Expedition.ENTRY))
	ambush_used_mask = int(d.get("ambush_used",0))
	ambush_pending_room = int(d.get("ambush_room",-1))
	ambush_timer = float(d.get("ambush_delay",0.0))
	previous_player_room = int(d.get("last_room",Expedition.ENTRY))
	if not expedition_mode:
		explored_rooms_mask = 1 << Expedition.ENTRY
		ambush_used_mask = 0
		ambush_pending_room = -1
		ambush_timer = 0.0
	if expedition_mode:
		for i in range(SPORE_CANISTER_LIMIT-1,-1,-1):
			if not prop_state[i]:
				var gone := canisters[i]
				canisters.remove_at(i)
				discard(gone)
		if secret_supply != null: secret_supply.visible = not d.get("secret_found",false)
	streak=int(d.get("streak",0))
	streak_timer=float(d.get("streak_timer",0))
	dash_clock=float(d.get("dash_cooldown",0))
	fire_clock=float(d.get("fire_cooldown",0))
	dash_left=float(saved_dash)
	dash_direction=heading if dash_left > 0.0 else Vector3.ZERO
	if dash_left > 0.0:
		player.velocity=dash_velocity
	damage_time=float(d.get("damage_cooldown",0.0))
	damage_cue_left=0.0
	damage_cue_angle=0.0
	fire_tap=false
	spawn_clock=float(d.get("spawn_delay",.2))
	show_banner("SECTOR %02d   /   %s" % [stage,STAGES[stage-1]])
	update_overlay()
	if web_score == null and not music.playing: music.play()
	return true
