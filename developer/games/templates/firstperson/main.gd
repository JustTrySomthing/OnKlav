extends Control
const State=preload("res://gameplay_state.gd")
const Steering=preload("res://enemy_steering.gd")
var state=State.new()
var enemies:Array[CharacterBody3D]=[]
var pickup_used:=false
var again:Button
var medkit:MeshInstance3D
const Touch=preload("res://touch_controls.gd")
var controls
var player:CharacterBody3D
var camera:Camera3D
var world:SubViewport
var targets:Array=[]
var door:StaticBody3D
var status:Label
var mask:=0
var door_open:=false
var pitch:=0.0
var cooldown:=0.0
var jump_pending:=false
var fire_pending:=false
func block(at:Vector3,extent:Vector3,color:Color)->StaticBody3D:
	var body:=StaticBody3D.new();body.position=at
	var collider:=CollisionShape3D.new();var shape:=BoxShape3D.new();shape.size=extent;collider.shape=shape;body.add_child(collider)
	var mesh:=MeshInstance3D.new();var box:=BoxMesh.new();box.size=extent;mesh.mesh=box;var material:=StandardMaterial3D.new();material.albedo_color=color;mesh.material_override=material;body.add_child(mesh);world.add_child(body);return body
func _ready():
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var view:=SubViewportContainer.new();view.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);view.stretch=true;view.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(view)
	world=SubViewport.new();world.size=Vector2i(1000,600);world.own_world_3d=true;view.add_child(world)
	block(Vector3(0,-.5,0),Vector3(24,1,24),Color("344655"))
	for spec in [[Vector3(0,2,-12),Vector3(24,4,1)],[Vector3(-12,2,0),Vector3(1,4,24)],[Vector3(12,2,0),Vector3(1,4,24)],[Vector3(0,2,12),Vector3(24,4,1)]]:block(spec[0],spec[1],Color("687c89"))
	for i in range(3):
		var target=block(Vector3((i-1)*4,1,-5-abs(i-1)*2),Vector3(1,2,1),Color("eab05d"));target.set_meta("target",i);targets.append(target)
	door=block(Vector3(5,1,2),Vector3(2,2,.4),Color("68b6ba"));door.set_meta("door",true)
	medkit=MeshInstance3D.new();medkit.position=Vector3(-4,.4,4);var kit:=BoxMesh.new();kit.size=Vector3(.6,.6,.6);medkit.mesh=kit;var green:=StandardMaterial3D.new();green.albedo_color=Color("7de2a9");medkit.material_override=green;world.add_child(medkit)
	var light:=DirectionalLight3D.new();light.rotation_degrees=Vector3(-45,-25,0);world.add_child(light)
	player=CharacterBody3D.new();var collider:=CollisionShape3D.new();var shape:=CapsuleShape3D.new();shape.radius=.3;shape.height=1.8;collider.shape=shape;collider.position.y=.9;player.add_child(collider);world.add_child(player)
	camera=Camera3D.new();camera.position.y=1.55;camera.far=60;player.add_child(camera)
	controls=Touch.new();controls.configure("firstperson");controls.actions.append("weapon");controls.look_changed.connect(look);controls.action_pressed.connect(action);add_child(controls)
	var cross:=Label.new();cross.text="+";cross.set_anchors_and_offsets_preset(Control.PRESET_CENTER);cross.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(cross)
	status=Label.new();status.add_theme_font_size_override("font_size",24);status.position=Vector2(16,12);status.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(status)
	var prefs:=Button.new();prefs.text="Controls";prefs.add_theme_font_size_override("font_size",24);prefs.position=Vector2(16,70);prefs.pressed.connect(func():controls.show_settings(self));add_child(prefs)
	again=Button.new();again.text="Play again";again.position=Vector2(16,110);again.hide();again.pressed.connect(func():OnKlavGame.replay());add_child(again)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,"Landscape first-person starter. Left stick moves, drag the right area to look. Fire hits targets at the crosshair; Use opens the cyan door within three metres. Jump and hold Sprint. Keyboard: WASD, Space, Shift, Ctrl to fire, E to use; drag to look. Lightweight single-player target arena, not a complete shooter.")
	OnKlavGame.suspended.connect(func():controls.reset_input();jump_pending=false;fire_pending=false)
func look(delta:Vector2):
	if OnKlavGame.paused:return
	player.rotation.y=wrapf(player.rotation.y-delta.x,-PI,PI);pitch=clampf(pitch-delta.y,-1.3,1.3);camera.rotation.x=pitch
func action(name:String):
	if name=="fire":fire_pending=true
	elif name=="jump":jump_pending=true
	elif name=="use":interact()
	elif name=="weapon":state.switch_weapon()
func _input(event:InputEvent):
	if OnKlavGame.paused:return
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode==KEY_SPACE:jump_pending=true
		elif event.keycode==KEY_CTRL:fire_pending=true
		elif event.keycode==KEY_E:interact()
		elif event.keycode==KEY_Q:state.switch_weapon()
func ray(length:float)->Dictionary:
	var from:=camera.global_position;var query:=PhysicsRayQueryParameters3D.create(from,from-camera.global_basis.z*length,1,[player.get_rid()])
	return player.get_world_3d().direct_space_state.intersect_ray(query)
func interact():
	if mask==7 and enemies.is_empty() and player.position.distance_to(Vector3(5,0,2))<3:
		state.level=mini(9999,state.level+1);mask=0;door_open=false;spawn_enemies();player.position=Vector3(0,.1,6);apply_progress();OnKlavGame.checkpoint();return
	var hit:=ray(3)
	if not hit.is_empty() and hit.collider.has_meta("door"):door_open=not door_open;apply_progress();OnKlavGame.checkpoint()
func fire():
	if cooldown>0:return
	cooldown=.22 if state.weapon==0 else .55;var hit:=ray(40)
	if not hit.is_empty() and hit.collider.has_meta("target"):
		state.score=mini(9999,state.score+10)
		mask|=1<<int(hit.collider.get_meta("target"));apply_progress();OnKlavGame.checkpoint()
	elif not hit.is_empty() and hit.collider.has_meta("enemy"):
		var e:CharacterBody3D=hit.collider;e.set_meta("hp",int(e.get_meta("hp"))-(15 if state.weapon==0 else 30))
		if e.get_meta("hp")<=0:enemies.erase(e);world.remove_child(e);e.queue_free();state.add_item("gold",2)
		OnKlavGame.checkpoint()
func spawn_enemies(count:int=-1):
	for e in enemies:world.remove_child(e);e.queue_free()
	enemies.clear()
	for i in range(mini(6,state.level+1) if count<0 else clampi(count,0,6)):
		var e:=CharacterBody3D.new();e.position=Vector3(-5+i*2,.1,-8);e.set_meta("enemy",true);e.set_meta("hp",30)
		var c:=CollisionShape3D.new();var shape:=CapsuleShape3D.new();shape.height=1.6;shape.radius=.35;c.shape=shape;c.position.y=.8;e.add_child(c)
		var art:=MeshInstance3D.new();var mesh:=CapsuleMesh.new();mesh.height=1.6;mesh.radius=.35;art.mesh=mesh;art.position.y=.8;var mat:=StandardMaterial3D.new();mat.albedo_color=Color("df7185");art.material_override=mat;e.add_child(art);world.add_child(e);enemies.append(e)
func apply_progress():
	for i in range(targets.size()):targets[i].visible=not bool(mask&(1<<i));targets[i].get_child(0).set_deferred("disabled",bool(mask&(1<<i)))
	door.visible=not door_open;door.get_child(0).set_deferred("disabled",door_open)
	status.text="Targets %d / 3 · move + look · Fire · Use cyan door"%[int(bool(mask&1))+int(bool(mask&2))+int(bool(mask&4))]
func _physics_process(dt):
	if OnKlavGame.paused or state.health<=0:return
	state.tick(dt)
	for e in enemies:
		Steering.chase_3d(e,player.position,1.1,dt)
		if e.position.distance_to(player.position)<1.2 and state.ready_for("hurt",.7):state.damage(10)
	if not pickup_used and player.position.distance_to(Vector3(-4,0,4))<1.5:pickup_used=true;medkit.hide();state.heal(35);state.add_item("medkit")
	if state.health<=0:again.show();controls.reset_input();OnKlavGame.checkpoint();return
	status.text="HP %d · %s · Level %d · Targets %d/3 · Q/Weapon"%[state.health,state.weapons[state.weapon],state.level,int(bool(mask&1))+int(bool(mask&2))+int(bool(mask&4))]
	cooldown=maxf(0,cooldown-dt)
	var axis:Vector2=(controls.input_vector+Vector2(float(Input.is_key_pressed(KEY_D))-float(Input.is_key_pressed(KEY_A)),float(Input.is_key_pressed(KEY_S))-float(Input.is_key_pressed(KEY_W)))).limit_length()
	var direction:Vector3=player.basis*Vector3(axis.x,0,axis.y);var speed:=8.0 if controls.is_held("sprint") or Input.is_key_pressed(KEY_SHIFT) else 4.5
	player.velocity.x=direction.x*speed;player.velocity.z=direction.z*speed;player.velocity.y=maxf(-20,player.velocity.y-18*dt)
	if jump_pending and player.is_on_floor():player.velocity.y=6
	jump_pending=false;player.move_and_slide()
	if fire_pending or controls.is_held("fire") or Input.is_key_pressed(KEY_CTRL):fire()
	fire_pending=false
func snapshot()->Dictionary:
	var list:Array=[]
	for e in enemies:list.append({"position":[e.position.x,e.position.y,e.position.z],"hp":e.get_meta("hp")})
	return {"model":state.snapshot(),"controls":controls.settings(),"enemies":list,"pickup":pickup_used,"cooldown":cooldown,"position":[player.position.x,player.position.y,player.position.z],"velocity":[player.velocity.x,player.velocity.y,player.velocity.z],"yaw":player.rotation.y,"pitch":pitch,"targets":mask,"door":door_open}
func restore(d:Dictionary)->bool:
	if d.has("model") and (not d.model is Dictionary or not State.valid(d.model)):return false
	if d.has("controls"):
		if not d.controls is Dictionary:return false
		var test:=Touch.new();var valid:bool=test.apply_settings(d.controls);test.free()
		if not valid:return false
	if d.has("enemies"):
		if not d.enemies is Array or d.enemies.size()>6:return false
		for e in d.enemies:
			if not e is Dictionary or not e.get("position") is Array or e.position.size()!=3 or not State.number(e.get("hp"),1,30,true):return false
			for v in e.position:
				if not State.number(v,-30,30):return false
	if d.has("cooldown") and not State.number(d.cooldown,0,1):return false
	if d.has("pickup") and not d.pickup is bool:return false
	for key in ["position","velocity"]:
		if not d.get(key)is Array or d[key].size()!=3:return false
		for value in d[key]:
			if not(value is int or value is float) or not is_finite(value) or absf(value)>30:return false
	for key in ["yaw","pitch"]:
		if not(d.get(key)is int or d.get(key)is float) or not is_finite(d[key]) or absf(d[key])>PI:return false
	if not(d.get("targets")is int or d.get("targets")is float) or float(d.targets)!=int(d.targets) or d.targets<0 or d.targets>7 or not d.get("door")is bool:return false
	if absf(d.pitch)>1.3:return false
	if d.has("model"):state.restore(d.model)
	if d.has("controls"):controls.apply_settings(d.controls)
	pickup_used=d.get("pickup",false);spawn_enemies(d.enemies.size() if d.has("enemies") else -1)
	if d.has("enemies"):
		while enemies.size()>d.enemies.size():var e:CharacterBody3D=enemies.pop_back();world.remove_child(e);e.queue_free()
		for i in range(enemies.size()):enemies[i].position=Vector3(d.enemies[i].position[0],d.enemies[i].position[1],d.enemies[i].position[2]);enemies[i].set_meta("hp",d.enemies[i].hp)
	medkit.visible=not pickup_used;again.visible=state.health<=0;cooldown=d.get("cooldown",0);jump_pending=false;fire_pending=false
	player.position=Vector3(d.position[0],d.position[1],d.position[2]);player.velocity=Vector3(d.velocity[0],d.velocity[1],d.velocity[2]);player.rotation.y=d.yaw;pitch=d.pitch;camera.rotation.x=pitch;mask=int(d.targets);door_open=d.door;controls.reset_input();apply_progress();return true
func start_new():
	state=State.new();spawn_enemies();pickup_used=false;medkit.show();again.hide()
	player.position=Vector3(0,.1,6);player.velocity=Vector3.ZERO;player.rotation=Vector3.ZERO;camera.rotation=Vector3.ZERO;pitch=0;mask=0;door_open=false;cooldown=0;jump_pending=false;fire_pending=false;controls.reset_input();apply_progress()
