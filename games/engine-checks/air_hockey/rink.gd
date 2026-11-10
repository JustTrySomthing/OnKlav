extends TextureRect
## A fixed normalized rink is simulated in a Godot World2D, then fitted to the UI.
signal point(side: int)
signal sound(kind: int, strength: float)
const Puck = preload("res://air_hockey/puck.gd")
const GOAL_LEFT := 198.0
const GOAL_RIGHT := 402.0
const GOAL_INSET := 17.0
const DIM := Vector2(600, 600.0 / 0.62)
const LINE := 0.89
var puck
var player: StaticBody2D
var opponent: StaticBody2D
var view: SubViewport
var world: Node2D
var artist
var target := 0.5
enum Difficulty { EASY, NORMAL, HARD }
var difficulty := Difficulty.NORMAL
var ai_velocity := 0.0
var ai_target := 0.5
var ai_mode := "recover"
var tick := 0
var stopped := true
var paused := false
var paused_velocity:=Vector2.ZERO
var paused_position:=DIM/2
var paused_contact:=""
var previous_puck:=DIM/2
var current_puck:=DIM/2
var previous_player:=Vector2(300,DIM.y*LINE)
var current_player:=previous_player
var previous_opponent:=Vector2(300,DIM.y*(1-LINE))
var current_opponent:=previous_opponent
var render_puck:=DIM/2
var render_player:=previous_player
var render_opponent:=previous_opponent
var final_message:=false
var goal_duration:=2.4
var celebration_age:=0.0
var touches := -1
var grip_offset := 0.0
var touched_at:=-1000
var trace: Array[Vector2] = []
var reduced_effects := false
var effects_clock := 0.0
var idle_redraw_time := 0.0

var impacts: Array[Dictionary] = []
var goal_time := 0.0
var goal_side := 0

class Painter extends Node2D:
	var rink
	const TABLE = preload("res://air_hockey/art/table.svg")
	const BLUE = preload("res://air_hockey/art/paddle-blue.svg")
	const CORAL = preload("res://air_hockey/art/paddle-coral.svg")
	const PUCK = preload("res://air_hockey/art/puck.svg")
	const TOP_RAIL := Color("#ff86ad")
	const BOTTOM_RAIL := Color("#6fd9ff")
	const RAIL_HALF_PERIOD := 3.3
	var rails: Array[Curve2D] = []
	var rail_lines: Array[PackedVector2Array] = []

	func _ready() -> void:
		for end in range(2):
			for side in range(2):
				var curve := Curve2D.new()
				curve.bake_interval = 4
				var points := PackedVector2Array([Vector2(192,9)])
				for step in range(9):
					var angle := lerpf(-PI*.5,-PI,float(step)/8)
					points.append(Vector2(27,27)+Vector2.from_angle(angle)*18)
				points.append(Vector2(9,DIM.y*.5))
				for point in points:
					if side==1: point.x=DIM.x-point.x
					if end==1: point.y=DIM.y-point.y
					curve.add_point(point)
				rails.append(curve)
				rail_lines.append(curve.get_baked_points())

	func disc(image: Texture2D, at: Vector2, radius: float) -> void:
		var extent := Vector2(radius, radius * rink.round_scale())
		draw_texture_rect(image, Rect2(at - extent, extent * 2), false)
	func shadow(at: Vector2, radius: float) -> void:
		draw_set_transform(at + Vector2(4,7),0,Vector2(1,rink.round_scale()*.83))
		draw_circle(Vector2.ZERO,radius+5,Color(0,0,0,.19),true,-1,true)
		draw_circle(Vector2.ZERO,radius,Color(0,0,0,.36),true,-1,true)
		draw_set_transform(Vector2.ZERO)

	func ring(at: Vector2, radius: float, color: Color, width: float = 2) -> void:
		draw_set_transform(at, 0, Vector2(1, rink.round_scale()))
		draw_arc(Vector2.ZERO, radius, 0, TAU, 64, color, width, true)
		draw_set_transform(Vector2.ZERO)
	func goal_arc(top: bool) -> void:
		var scale_y:float=rink.round_scale()
		var edge:=asin(clampf(19.0/maxf(1.0,170.0*scale_y),0,1))
		var start:=edge if top else PI+edge
		var end:=PI-edge if top else TAU-edge
		draw_set_transform(Vector2(300,0 if top else DIM.y),0,Vector2(1,scale_y))
		draw_arc(Vector2.ZERO,170,start,end,44,Color("67879a"),2,true)
		draw_set_transform(Vector2.ZERO)

	func _draw() -> void:
		draw_texture_rect(TABLE, Rect2(Vector2.ZERO, DIM), false)
		ring(DIM / 2, 85, Color("6090a2"))
		goal_arc(true)
		goal_arc(false)
		if not rink.reduced_effects:
			draw_rail_lights()
		if rink.goal_time>0:
			draw_goal_lights()
		if not rink.reduced_effects:
			var segments:int=rink.trace.size()-1
			for i in range(segments):
				var weight:=float(i+1)/segments
				var tip:Vector2=rink.render_puck if i==segments-1 else rink.trace[i+1]
				draw_line(rink.trace[i],tip,Color(.78,.90,.98,.18*weight),lerpf(2.0,7.0,weight),true)
		for impact in ([] if rink.reduced_effects else rink.impacts):
			var age:float=1-impact.life/.22
			var ink:Color=impact.ink
			var power:float=impact.strength
			ring(impact.at,16+age*33,Color(ink,(1-age)*power*.75))
			for i in range(4 if impact.kind==1 else 8):
				var direction:=Vector2.from_angle(i*2.4+impact.at.x*.07)
				var tip:Vector2=impact.at+direction*(14+age*(38+i%3*10))
				draw_line(tip-direction*(10*(1-age)),tip,Color(ink,(1-age)*power*.9),2,true)
		shadow(rink.render_opponent,49)
		shadow(rink.render_player,49)
		disc(CORAL, rink.render_opponent, 64)
		disc(BLUE, rink.render_player, 64)
		disc(PUCK, rink.render_puck, 32)
		if rink.goal_time > 0:
			var age:float=rink.celebration_age;var ink:=Color("70c9ff") if rink.goal_side==0 else Color("ff94b2")
			var center:=Vector2(300,DIM.y*.5)
			if not rink.reduced_effects:
				for i in range(26):
					var angle:=TAU*i/26+age*.2;var travel:=minf(1,age/.8);var radius:=65+travel*(100+i%4*24)
					var p:=center+Vector2(cos(angle)*radius,sin(angle)*radius*.7)
					var fade:=clampf(1-age/2.2,0,1);draw_line(p-Vector2.from_angle(angle)*12,p,Color(ink,fade*.8),3,true)
				ring(center,80+minf(1,age)*160,Color(ink,maxf(0,.4-age*.3)),3)
			if rink.final_message:return
			var title:="GOAL!" if rink.goal_side==0 else "RIVAL SCORES"
			var font:=ThemeDB.fallback_font;var entrance:=minf(1,age/.45)
			var scale_value:float=1 if rink.reduced_effects else 1+pow(2,-7*entrance)*sin((entrance-.18)*TAU*1.1)*.22 if entrance<1 else 1
			var opacity:=minf(1,age/.12)*minf(1,rink.goal_time/.3)
			draw_set_transform(center,0,Vector2.ONE*scale_value)
			var width:=font.get_string_size(title,HORIZONTAL_ALIGNMENT_LEFT,-1,58).x
			draw_string(font,Vector2(-width*.5+2,19),title,HORIZONTAL_ALIGNMENT_LEFT,-1,58,Color(0,.03,.08,opacity*.8))
			draw_string(font,Vector2(-width*.5,16),title,HORIZONTAL_ALIGNMENT_LEFT,-1,58,Color(ink,opacity))
			var sub:="CLEAN FINISH" if rink.goal_side==0 else "GET THE NEXT ONE"
			width=font.get_string_size(sub,HORIZONTAL_ALIGNMENT_LEFT,-1,17).x
			draw_string(font,Vector2(-width*.5,49),sub,HORIZONTAL_ALIGNMENT_LEFT,-1,17,Color(.82,.94,1,opacity))
			draw_set_transform(Vector2.ZERO)

	func draw_rail_lights() -> void:
		# All four heads leave their own pocket together, turn at center ice, and return.
		for index in range(rails.size()):
			var rail := rails[index]
			var ink := TOP_RAIL if index<2 else BOTTOM_RAIL
			var length := rail.get_baked_length()
			var clock: float = rink.effects_clock
			for segment in range(9,0,-1):
				var older := maxf(0,clock-segment*.035)
				var newer := maxf(0,clock-(segment-1)*.035)
				var a := rail.sample_baked(length*(1.0-absf(fposmod(older/RAIL_HALF_PERIOD,2.0)-1.0)))
				var b := rail.sample_baked(length*(1.0-absf(fposmod(newer/RAIL_HALF_PERIOD,2.0)-1.0)))
				var strength := 1.0-float(segment-1)/9.0
				draw_line(a,b,Color(ink,strength*.10),12,true)
				draw_line(a,b,Color(ink.lightened(.24),strength*.60),4,true)

	func draw_goal_lights() -> void:
		var top: bool = rink.goal_side==0
		var ink := TOP_RAIL if top else BOTTOM_RAIL
		var mouth := Vector2(300,10 if top else DIM.y-10)
		var a := mouth-Vector2(102,0)
		var b := mouth+Vector2(102,0)
		if rink.reduced_effects:
			draw_line(a,b,ink,8,true)
			return
		# One bright strike, then a slower pocket afterglow. Keep the opposite mouth dark.
		var age: float = rink.celebration_age
		var flash := pow(maxf(0,1-age/.56),2)
		var pocket := maxf(0,1-age/1.28)
		if flash>0:
			for rail in rail_lines:
				draw_polyline(rail,Color(ink,flash*.34),19,true)
				draw_polyline(rail,Color(ink.lightened(.72),flash*.96),6,true)
		if pocket>0:
			draw_line(a,b,Color(ink,pocket*.19),38,true)
			draw_line(a,b,Color(ink,pocket*.52),21,true)
			draw_line(a,b,Color(ink.lightened(.82),pocket*.97),8,true)


func round_scale() -> float:
	return size.x / maxf(size.y, 1) * DIM.y / DIM.x

func _process(delta: float) -> void:
	if paused:
		return
	var animated := goal_time > 0 or not impacts.is_empty() or not stopped
	if not reduced_effects:
		effects_clock+=delta
		idle_redraw_time+=delta
		if not animated and idle_redraw_time>=1.0/30.0:
			idle_redraw_time=0
			redraw()
	if goal_time>0:celebration_age+=delta
	var fraction:=clampf(Engine.get_physics_interpolation_fraction(),0,1)
	if not stopped:
		render_puck=previous_puck.lerp(current_puck,fraction);render_player=previous_player.lerp(current_player,fraction);render_opponent=previous_opponent.lerp(current_opponent,fraction)
	goal_time = maxf(0, goal_time - delta)
	for impact in impacts:
		impact.life -= delta
	impacts = impacts.filter(func(impact): return impact.life > 0)
	if animated:
		redraw()

func celebrate(side: int) -> void:
	stopped = true
	touches = -1
	puck.set_deferred("freeze", true)
	puck.place(DIM / 2, Vector2.ZERO)
	trace.clear()
	goal_side = side
	goal_time = goal_duration;celebration_age=0;reset_render()
	redraw()

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	focus_mode = Control.FOCUS_ALL
	stretch_mode = TextureRect.STRETCH_SCALE
	expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	custom_minimum_size = Vector2(0, 200)
	view = SubViewport.new()
	view.size = Vector2i(DIM)
	view.world_2d = World2D.new()
	view.render_target_update_mode = SubViewport.UPDATE_ONCE
	add_child(view)
	world = Node2D.new()
	view.add_child(world)
	texture = view.get_texture()
	# Thin end rails leave the original centered goal mouth open.
	for segment in [[Vector2(0, -40), Vector2(0, DIM.y + 40)], [Vector2(600, -40), Vector2(600, DIM.y + 40)], [Vector2(0, 0), Vector2(GOAL_LEFT, 0)], [Vector2(GOAL_RIGHT, 0), Vector2(600, 0)], [Vector2(0, DIM.y), Vector2(GOAL_LEFT, DIM.y)], [Vector2(GOAL_RIGHT, DIM.y), DIM]]:
		var wall := StaticBody2D.new()
		if segment[0].x == segment[1].x:wall.set_meta("side","side_rail")
		var collision := CollisionShape2D.new()
		var line := SegmentShape2D.new()
		line.a = segment[0]
		line.b = segment[1]
		collision.shape = line
		wall.add_child(collision)
		world.add_child(wall)
	player = paddle_body("player", Vector2(300, DIM.y * LINE))
	opponent = paddle_body("opponent", Vector2(300, DIM.y * (1 - LINE)))
	puck = Puck.new()
	puck.player = player
	puck.opponent = opponent
	puck.position = DIM / 2
	puck.freeze = true
	puck.impact.connect(func(kind: int, strength: float):
		sound.emit(kind,strength)
		if reduced_effects or strength<.46:return
		if impacts.size() == 8:
			impacts.pop_front()
		var ink:=Color("a9ecff") if kind==1 else Color("76edff") if puck.position.y>DIM.y*.5 else Color("ff9dbc")
		impacts.append({"at":puck.position,"life":.22,"kind":kind,"ink":ink,"strength":strength}))
	world.add_child(puck)
	resized.connect(func():
		touches = -1
		redraw())
	artist = Painter.new()
	artist.rink = self
	world.add_child(artist)

func paddle_body(side: String, at: Vector2) -> StaticBody2D:
	var body := StaticBody2D.new()
	body.set_meta("side", side)
	body.position = at
	var collision := CollisionShape2D.new()
	var shape := CircleShape2D.new()
	shape.radius = 45
	collision.shape = shape
	body.add_child(collision)
	world.add_child(body)
	return body

func serve(seed: int) -> void:
	stopped = false
	paused = false
	trace.clear()
	goal_time = 0;final_message=false
	impacts.clear()
	ai_mode="recover"
	ai_target=.5
	puck.place(Vector2(300, DIM.y * 0.70), Vector2((posmod(seed, 7) - 3) * 0.075, -0.63) * DIM)
	puck.pending_side = ""
	puck.freeze = false
	reset_render();redraw()

func set_paused(value: bool) -> void:
	if value and not paused and not stopped:
		paused_position=puck.placement.get("at",puck.position)
		paused_velocity=puck.placement.get("velocity",puck.linear_velocity)
		paused_contact=puck.pending_side
	var was_paused:=paused
	paused=value
	puck.freeze=stopped or paused
	if was_paused and not paused and not stopped:
		# Godot zeroes a frozen rigid body's velocity; restore it at its sync boundary.
		puck.place(paused_position,paused_velocity,paused_contact)
	touches=-1;reset_render();redraw()

func reset_render() -> void:
	previous_puck=puck.position;current_puck=puck.position;render_puck=puck.position
	previous_player=player.position;current_player=player.position;render_player=player.position
	previous_opponent=opponent.position;current_opponent=opponent.position;render_opponent=opponent.position

func pocket_scorer(from: Vector2, to: Vector2) -> int:
	# Count entry at the visible pocket stripe, before the puck reaches an end rail.
	for side in range(2):
		var line := GOAL_INSET if side == 0 else DIM.y - GOAL_INSET
		var entered := from.y >= line and to.y < line if side == 0 else from.y <= line and to.y > line
		if entered:
			var x := lerpf(from.x,to.x,(line-from.y)/(to.y-from.y))
			if x >= GOAL_LEFT+Puck.RADIUS and x <= GOAL_RIGHT-Puck.RADIUS:
				return side
	# A puck that grazes a post may clear the mouth after crossing the stripe.
	if to.y < -Puck.RADIUS: return 0
	if to.y > DIM.y + Puck.RADIUS: return 1
	return -1

func _physics_process(delta: float) -> void:
	if not is_instance_valid(puck) or stopped or paused:
		return
	previous_puck=current_puck;previous_player=current_player;previous_opponent=current_opponent
	tick += 1
	var previous: float = player.position.x
	player.position.x = move_toward(previous, target * 600, 3600 * delta)
	puck.player_motion = (player.position.x - previous) / maxf(delta, .001)
	var plan_interval: int = [20,12,7][difficulty]
	if tick % plan_interval == 0:
		update_opponent_plan()
	var speed_limit: float = [.24,.38,.52][difficulty] if ai_mode=="chase" else [.21,.33,.46][difficulty]
	var desired:=clampf((ai_target-opponent.position.x/DIM.x)*4,-speed_limit,speed_limit)
	ai_velocity=move_toward(ai_velocity,desired,[1.12,1.65,2.4][difficulty]*delta)
	opponent.position.x=clampf(opponent.position.x+ai_velocity*DIM.x*delta,60,540)
	var scorer := pocket_scorer(previous_puck,puck.position)
	if scorer >= 0:
		stopped = true
		puck.set_deferred("freeze", true)
		point.emit(scorer)
		trace.clear()
	elif not reduced_effects and puck.linear_velocity.length()>380:
		trace.append(puck.position)
		if trace.size()>4:trace.pop_front()
	else:
		trace.clear()
	current_puck=puck.position;current_player=player.position;current_opponent=opponent.position

func update_opponent_plan() -> void:
	puck.ai_seen=player.position.x/DIM.x
	var velocity:Vector2=puck.linear_velocity
	var position_x:float=puck.position.x/DIM.x
	var reach:float=puck.position.y-opponent.position.y
	if absf(reach)<=76 and velocity.length()<445:
		ai_mode="chase"
		var offset:=.025 if puck.ai_seen<.5 else -.025
		ai_target=clampf(position_x+offset,.1,.9)
	elif reach>0 and velocity.y< -65:
		ai_mode="guard"
		var seconds:=clampf(reach/-velocity.y,0,.48)
		var reflected:=fposmod(position_x+velocity.x/DIM.x*seconds-.025,1.9)
		var intercept:=.025+minf(reflected,1.9-reflected)
		var uncertainty: float = sin(tick*.017+puck.ai_hits*2.13)*[.085,.052,.024][difficulty]
		ai_target=clampf(lerpf(.5,intercept,[.61,.78,.93][difficulty])+uncertainty,.14,.86)
	else:
		ai_mode="recover"
		ai_target=lerpf(.5,position_x,.13)

func redraw() -> void:
	if is_instance_valid(artist):
		artist.queue_redraw()
		view.render_target_update_mode = SubViewport.UPDATE_ONCE

func _input(event: InputEvent) -> void:
	# Track touch at the rink boundary, independent of decorative GUI children.
	if event is InputEventScreenTouch or event is InputEventScreenDrag or event is InputEventMouseButton or event is InputEventMouseMotion:
		event=event.duplicate()
		event.position=get_global_transform_with_canvas().affine_inverse()*event.position
		if touches==-1 and not Rect2(Vector2.ZERO,size).has_point(event.position):return
	_rink_input(event)

func _gui_input(event:InputEvent)->void:
	_rink_input(event)

func _rink_input(event: InputEvent) -> void:
	if paused or stopped:
		return
	if event is InputEventScreenTouch:
		touched_at=Time.get_ticks_msec()
		if event.pressed and not event.canceled and touches < 0 and event.position.y >= size.y * 0.5:
			touches = event.index
			begin_grip(event.position.x)
		elif (not event.pressed or event.canceled) and touches == event.index:
			touches = -1
	elif event is InputEventScreenDrag and event.index == touches:
		touched_at=Time.get_ticks_msec()
		move_input(event.position.x)
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if touches>=0 or Time.get_ticks_msec()-touched_at<500:return
		touches = -2 if event.pressed and event.position.y >= size.y * 0.5 else -1
		if touches == -2:
			begin_grip(event.position.x)
	elif event is InputEventMouseMotion and touches == -2:
		if touches>=0 or Time.get_ticks_msec()-touched_at<500:return
		move_input(event.position.x)
	elif event is InputEventKey and event.pressed:
		if event.keycode in [KEY_LEFT, KEY_A]:
			target = clampf(target - 0.055, 0.1, 0.9)
		elif event.keycode in [KEY_RIGHT, KEY_D]:
			target = clampf(target + 0.055, 0.1, 0.9)
		get_viewport().set_input_as_handled()

func begin_grip(x: float) -> void:
	target = player.position.x / DIM.x
	grip_offset = target - x / maxf(size.x, 1)
	get_viewport().set_input_as_handled()

func move_input(x: float) -> void:
	target = clampf(x / maxf(size.x, 1) + grip_offset, 0.1, 0.9)
	get_viewport().set_input_as_handled()

func snapshot() -> Dictionary:
	var at:Vector2=paused_position if paused and not stopped else puck.position
	var velocity:Vector2=paused_velocity if paused and not stopped else puck.linear_velocity
	return {"difficulty":difficulty,"x": at.x / 600, "y": at.y / DIM.y, "vx": velocity.x / 600, "vy": velocity.y / DIM.y, "player": player.position.x / 600, "opponent": opponent.position.x / 600, "ai_velocity": ai_velocity, "ai_target": ai_target, "tick": tick, "ai_hits": puck.ai_hits}

func restore(data: Dictionary, playing: bool) -> void:
	difficulty = int(data.get("difficulty",Difficulty.NORMAL)) as Difficulty
	paused_position=Vector2(data.x*600,data.y*DIM.y);paused_velocity=Vector2(data.vx*600,data.vy*DIM.y);paused_contact=""
	# Repair old PLAYING saves written after the freeze bug without changing scores.
	if playing and paused_velocity.length()<1:paused_velocity=Vector2(0,-.63*DIM.y)
	puck.place(paused_position,paused_velocity)
	player.position.x = data.player * 600
	opponent.position.x = data.opponent * 600
	target = data.player
	difficulty = int(data.get("difficulty", Difficulty.NORMAL)) as Difficulty
	ai_velocity = data.ai_velocity
	ai_target = data.ai_target
	ai_mode="recover"
	tick = int(data.tick)
	puck.ai_hits = int(data.ai_hits)
	stopped = not playing
	paused=true
	puck.freeze=true
	reset_render();redraw()
