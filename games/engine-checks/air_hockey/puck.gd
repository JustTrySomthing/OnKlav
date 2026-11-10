extends RigidBody2D
## Godot handles contact detection, continuous collision and rail response.
signal impact(kind: int, strength: float)
var pending_side := ""
var rail_velocity := Vector2.ZERO
var placement: Dictionary = {}
var player: StaticBody2D
var opponent: StaticBody2D
var ai_seen := 0.5
var ai_hits := 0
var player_motion := 0.0
const RADIUS := 15.0
const DIM := Vector2(600, 600.0 / 0.62)

func _ready() -> void:
	gravity_scale = 0
	linear_damp_mode = RigidBody2D.DAMP_MODE_REPLACE
	angular_damp_mode = RigidBody2D.DAMP_MODE_REPLACE
	linear_damp = 0
	angular_damp = 0
	lock_rotation = true
	continuous_cd = RigidBody2D.CCD_MODE_CAST_SHAPE
	contact_monitor = true
	max_contacts_reported = 8
	can_sleep = false
	var material := PhysicsMaterial.new()
	material.friction = 0
	material.bounce = 1
	physics_material_override = material
	var collider := CollisionShape2D.new()
	var shape := CircleShape2D.new()
	shape.radius = RADIUS
	collider.shape = shape
	add_child(collider)
	body_entered.connect(func(body: Node):
		pending_side = str(body.get_meta("side", "rail")))

func place(at: Vector2, velocity: Vector2, contact: String = "") -> void:
	# Apply teleports at the physics synchronization boundary, including re-serves.
	placement = {"at": at, "velocity": velocity,"contact":contact}
	position = at
	linear_velocity = velocity
	pending_side = ""

func _integrate_forces(state: PhysicsDirectBodyState2D) -> void:
	if not placement.is_empty():
		state.transform = Transform2D(0, placement.at)
		state.linear_velocity = placement.velocity
		rail_velocity=placement.velocity
		pending_side = str(placement.get("contact",""))
		placement.clear()
	if pending_side.is_empty():
		var x:=state.transform.origin.x
		var margin:=maxf(30.0,16.0+absf(rail_velocity.x)*state.step)
		if x>margin and x<DIM.x-margin:rail_velocity=state.linear_velocity
		return
	var kind := pending_side
	pending_side = ""
	var velocity := state.linear_velocity / DIM
	var speed: float = velocity.length()
	if kind == "side_rail" and not rail_velocity.is_zero_approx():
		# Shape casting can report a slightly skewed normal even on a straight side.
		var direction := 1.0 if state.transform.origin.x<DIM.x*.5 else -1.0
		state.linear_velocity=Vector2(absf(rail_velocity.x)*direction,rail_velocity.y)
	elif kind == "player" and state.transform.origin.y <= player.position.y:
		var contact := clampf((state.transform.origin.x - player.position.x) / 60.0, -1, 1)
		var flick := clampf(player_motion / 1800.0, -1, 1)
		var angle := clampf(contact * .94 + flick * .48, -1.08, 1.08)
		var launch_speed := clampf(state.linear_velocity.length() + 38.0 + absf(flick) * 235.0, 390.0, 1210.0)
		state.linear_velocity = Vector2(sin(angle), -cos(angle)) * launch_speed
	elif kind == "opponent" and state.transform.origin.y >= opponent.position.y:
		speed = minf(1.12, speed * 1.015 + 0.015)
		var target: float = 0.625 if ai_seen < 0.46 else 0.375 if ai_seen > 0.54 else 0.375 if state.transform.origin.x < 300 else 0.625
		target += sin(ai_hits * 1.7) * 0.035
		# A bank trades a longer path for a different return angle.
		if ai_hits % 3 == 2:
			target = 0.05-target if state.transform.origin.x<DIM.x*.5 else 1.95-target
		var direction := Vector2(target - state.transform.origin.x / 600.0, 1.025 - state.transform.origin.y / DIM.y).normalized()
		velocity = direction * speed
		if absf(velocity.x) < 0.12:
			velocity.x = 0.12 if state.transform.origin.x < 300 else -0.12
		velocity.x = clampf(velocity.x, -speed * 0.78, speed * 0.78)
		velocity.y = sqrt(maxf(0.0, speed * speed - velocity.x * velocity.x))
		state.linear_velocity = velocity * DIM
		ai_hits += 1
	rail_velocity=state.linear_velocity
	var strength:=clampf((state.linear_velocity.length()-320.0)/850.0,0.0,1.0)
	impact.emit(1 if kind in ["rail","side_rail"] else 2,strength)
