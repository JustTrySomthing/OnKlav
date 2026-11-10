extends Control
const State=preload("res://gameplay_state.gd")
var state=State.new()
var moving:AnimatableBody2D
var phase:=0.0
var checkpoint_art:Polygon2D
var enemy:CharacterBody2D
const Touch=preload("res://touch_controls.gd")
var controls
var player:CharacterBody2D
var camera:Camera2D
var world:SubViewport
var status:Label
var again:Button
var won:=false
var coyote:=0.0
var jump_buffer:=0.0
var dash_time:=0.0
var dash_cooldown:=0.0
var facing:=1.0
func platform(at:Vector2,extent:Vector2):
	var body:=StaticBody2D.new();body.position=at
	var shape:=CollisionShape2D.new();var rect:=RectangleShape2D.new();rect.size=extent;shape.shape=rect;body.add_child(shape)
	var art:=Polygon2D.new();art.polygon=PackedVector2Array([-extent*.5,Vector2(extent.x*.5,-extent.y*.5),extent*.5,Vector2(-extent.x*.5,extent.y*.5)]);art.color=Color("4f977e");body.add_child(art);world.add_child(body)
func _ready():
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var view:=SubViewportContainer.new();view.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);view.stretch=true;view.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(view)
	world=SubViewport.new();world.size=Vector2i(1000,600);world.world_2d=World2D.new();world.canvas_item_default_texture_filter=Viewport.DEFAULT_CANVAS_ITEM_TEXTURE_FILTER_NEAREST;view.add_child(world)
	platform(Vector2(500,580),Vector2(2200,40));platform(Vector2(250,460),Vector2(180,24));platform(Vector2(480,360),Vector2(180,24));platform(Vector2(720,260),Vector2(220,24))
	moving=AnimatableBody2D.new();moving.sync_to_physics=false;moving.position=Vector2(450,480)
	var mc:=CollisionShape2D.new();var ms:=RectangleShape2D.new();ms.size=Vector2(90,16);mc.shape=ms;moving.add_child(mc)
	var ma:=Polygon2D.new();ma.polygon=PackedVector2Array([Vector2(-45,-8),Vector2(45,-8),Vector2(45,8),Vector2(-45,8)]);ma.color=Color("72b5d6");moving.add_child(ma);world.add_child(moving)
	checkpoint_art=Polygon2D.new();checkpoint_art.position=Vector2(250,430);checkpoint_art.polygon=PackedVector2Array([Vector2(-6,-20),Vector2(6,-20),Vector2(6,15),Vector2(-6,15)]);checkpoint_art.color=Color("f4dc77");world.add_child(checkpoint_art)
	platform(Vector2(850,548),Vector2(80,24)) # raised hazard; collision keeps player above floor
	enemy=CharacterBody2D.new();enemy.position=Vector2(580,540);var ec:=CollisionShape2D.new();var er:=RectangleShape2D.new();er.size=Vector2(24,30);ec.shape=er;enemy.add_child(ec);var ea:=Polygon2D.new();ea.polygon=PackedVector2Array([Vector2(-12,-15),Vector2(12,-15),Vector2(12,15),Vector2(-12,15)]);ea.color=Color("e57783");enemy.add_child(ea);world.add_child(enemy)
	player=CharacterBody2D.new();var shape:=CollisionShape2D.new();var rect:=RectangleShape2D.new();rect.size=Vector2(28,44);shape.shape=rect;player.add_child(shape)
	var art:=Polygon2D.new();art.polygon=PackedVector2Array([Vector2(-14,-22),Vector2(14,-22),Vector2(14,22),Vector2(-14,22)]);art.color=Color("ffc875");player.add_child(art);world.add_child(player)
	camera=Camera2D.new();camera.position=Vector2(500,300);world.add_child(camera)
	view.resized.connect(func():camera.zoom=Vector2.ONE*maxf(.01,minf(view.size.x/1000,view.size.y/600)))
	controls=Touch.new();controls.configure("platformer");add_child(controls);controls.action_pressed.connect(action)
	status=Label.new();status.add_theme_font_size_override("font_size",24);status.text="Reach the top platform · move + jump · dash across gaps";status.position=Vector2(16,12);status.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(status)
	again=Button.new();again.text="Play again";again.position=Vector2(16,44);again.hide();again.pressed.connect(func():OnKlavGame.replay());add_child(again)
	var prefs:=Button.new();prefs.text="Controls";prefs.add_theme_font_size_override("font_size",24);prefs.position=Vector2(16,80);prefs.pressed.connect(func():controls.show_settings(self));add_child(prefs)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,"Landscape platformer starter. Left stick moves; Jump supports short input buffering and coyote time; Dash crosses gaps. Reach the top platform. Keyboard: A/D or arrows, Space to jump, Shift to dash. Shared controls pause, save/return and quit.")
	OnKlavGame.suspended.connect(func():controls.reset_input();jump_buffer=0;coyote=0;dash_time=0)
func action(name:String):
	if name=="jump":jump_buffer=.13
	elif name=="dash" and dash_cooldown<=0:dash_time=.13;dash_cooldown=.65
func _input(event:InputEvent):
	if OnKlavGame.paused:return
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode==KEY_SPACE:action("jump")
		elif event.keycode==KEY_SHIFT:action("dash")
func _physics_process(dt):
	if won or OnKlavGame.paused:return
	state.tick(dt);phase+=dt;moving.position.x=450+sin(phase)*90
	enemy.velocity=Vector2(cos(phase)*60,100);enemy.move_and_slide()
	if player.position.distance_to(enemy.position)<35 and state.ready_for("hazard",1):state.damage(25);player.position=Vector2(state.checkpoint.x,state.checkpoint.y);player.velocity=Vector2.ZERO
	if state.health<=0:state.respawn()
	status.text="Health %d · Checkpoint %s · Reach the top"%[state.health,"active" if state.checkpoint.x>100 else "start"]
	if player.position.distance_to(Vector2(250,430))<55:state.checkpoint=Vector3(250,430,0)
	if player.position.x>800 and player.position.y>500 and state.ready_for("hazard",1):
		state.damage(25);player.position=Vector2(state.checkpoint.x,state.checkpoint.y);player.velocity=Vector2.ZERO
		if state.health<=0:state.respawn()
	coyote=.12 if player.is_on_floor() else maxf(0,coyote-dt);jump_buffer=maxf(0,jump_buffer-dt);dash_cooldown=maxf(0,dash_cooldown-dt);dash_time=maxf(0,dash_time-dt)
	var axis:float=clampf(controls.input_vector.x+float(Input.is_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT))-float(Input.is_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT)),-1,1)
	if absf(axis)>.1:facing=signf(axis)
	player.velocity.x=facing*650 if dash_time>0 else axis*270
	player.velocity.y=minf(1000,player.velocity.y+1200*dt)
	if jump_buffer>0 and coyote>0:player.velocity.y=-580;jump_buffer=0;coyote=0
	player.move_and_slide();player.position.x=clampf(player.position.x,20,980)
	if player.position.y>700:player.position=Vector2(state.checkpoint.x,state.checkpoint.y);player.velocity=Vector2.ZERO
	if player.position.x>650 and player.position.y<250 and player.is_on_floor():won=true;status.text="Goal reached!";again.show();controls.reset_input();OnKlavGame.checkpoint()
func snapshot()->Dictionary:return {"model":state.snapshot(),"controls":controls.settings(),"phase":phase,"enemy":[enemy.position.x,enemy.position.y],"position":[player.position.x,player.position.y],"velocity":[player.velocity.x,player.velocity.y],"won":won}
func restore(d:Dictionary)->bool:
	if d.has("model") and (not d.model is Dictionary or not State.valid(d.model)):return false
	if d.has("enemy"):
		if not d.enemy is Array or d.enemy.size()!=2:return false
		for v in d.enemy:
			if not State.number(v,-1000,3000):return false
	if d.has("phase") and not State.number(d.phase,0,100000):return false
	if d.has("controls"):
		if not d.controls is Dictionary:return false
		var test:=Touch.new();var valid:bool=test.apply_settings(d.controls);test.free()
		if not valid:return false
	for key in ["position","velocity"]:
		if not d.get(key)is Array or d[key].size()!=2:return false
		for value in d[key]:
			if not(value is int or value is float) or not is_finite(value) or absf(value)>3000:return false
	if not d.get("won")is bool:return false
	if d.has("model"):state.restore(d.model)
	if d.has("controls"):controls.apply_settings(d.controls)
	if d.has("enemy"):enemy.position=Vector2(d.enemy[0],d.enemy[1])
	phase=d.get("phase",0)
	player.position=Vector2(d.position[0],d.position[1]);player.velocity=Vector2(d.velocity[0],d.velocity[1]);won=d.won;controls.reset_input();again.visible=won;status.text="Goal reached!" if won else "Reach the top platform · move + jump · dash across gaps";return true
func start_new():
	state=State.new();state.checkpoint=Vector3(100,520,0);phase=0;enemy.position=Vector2(580,540)
	player.position=Vector2(100,520);player.velocity=Vector2.ZERO;won=false;coyote=0;jump_buffer=0;dash_time=0;dash_cooldown=0;controls.reset_input();again.hide();status.text="Reach the top platform · move + jump · dash across gaps"
