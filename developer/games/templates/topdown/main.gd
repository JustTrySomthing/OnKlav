extends Control
const Touch=preload("res://touch_controls.gd")
const State=preload("res://gameplay_state.gd")
const Steering=preload("res://enemy_steering.gd")
const ARENA=false
var state=State.new()
var controls
var world:SubViewport
var player:CharacterBody2D
var camera:Camera2D
var enemies:Array[CharacterBody2D]=[]
var status:Label
var message:Label
var facing:=Vector2.RIGHT
var pending_attack:=false
var dash:=0.0
var elapsed:=0.0
var pickup:=Vector2(360,300)
var npc:=Vector2(170,240)
var exit_point:=Vector2(880,300)
var loot:Polygon2D
var again:Button
func body(at:Vector2,color:Color)->CharacterBody2D:
	var b:=CharacterBody2D.new();b.position=at;var shape:=CollisionShape2D.new();var circle:=CircleShape2D.new();circle.radius=15;shape.shape=circle;b.add_child(shape)
	var art:=Polygon2D.new();art.polygon=PackedVector2Array([Vector2(-14,-14),Vector2(14,-14),Vector2(14,14),Vector2(-14,14)]);art.color=color;b.add_child(art);world.add_child(b);return b
func wall(at:Vector2,extent:Vector2):
	var b:=StaticBody2D.new();b.position=at;var c:=CollisionShape2D.new();var r:=RectangleShape2D.new();r.size=extent;c.shape=r;b.add_child(c)
	var a:=Polygon2D.new();a.polygon=PackedVector2Array([-extent*.5,Vector2(extent.x*.5,-extent.y*.5),extent*.5,Vector2(-extent.x*.5,extent.y*.5)]);a.color=Color("466c7b");b.add_child(a);world.add_child(b)
func _ready():
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var view:=SubViewportContainer.new();view.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);view.stretch=true;view.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(view)
	world=SubViewport.new();world.world_2d=World2D.new();world.size=Vector2i(1000,600);view.add_child(world)
	wall(Vector2(500,15),Vector2(1000,30));wall(Vector2(500,585),Vector2(1000,30));wall(Vector2(15,300),Vector2(30,600));wall(Vector2(985,300),Vector2(30,600))
	player=body(Vector2(100,300),Color("ffd487"));camera=Camera2D.new();world.add_child(camera)
	var guide:=Label.new();guide.position=npc;guide.add_theme_font_size_override("font_size",22);guide.text="Guide\nUse nearby";world.add_child(guide)
	var door:=Label.new();door.position=exit_point;door.add_theme_font_size_override("font_size",22);door.text="Exit\nUse nearby";world.add_child(door)
	loot=Polygon2D.new();loot.position=pickup;loot.polygon=PackedVector2Array([Vector2(0,-14),Vector2(14,0),Vector2(0,14),Vector2(-14,0)]);loot.color=Color("88e4b2");world.add_child(loot)
	controls=Touch.new();controls.configure("arena" if ARENA else "topdown");controls.action_pressed.connect(action);add_child(controls)
	status=Label.new();status.add_theme_font_size_override("font_size",24);status.position=Vector2(16,8);status.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(status)
	message=Label.new();message.add_theme_font_size_override("font_size",22);message.position=Vector2(16,40);message.mouse_filter=Control.MOUSE_FILTER_IGNORE;add_child(message)
	again=Button.new();again.position=Vector2(16,60);again.text="Play again";again.pressed.connect(func():OnKlavGame.replay());again.hide();add_child(again)
	var settings:=Button.new();settings.position=Vector2(16,100);settings.text="Controls";settings.add_theme_font_size_override("font_size",24);settings.pressed.connect(show_controls);add_child(settings)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,"Single-player action starter. Move with the left stick or WASD. Attack with the button or Space; Use/E talks to the guide and uses the exit. Pick up the green potion, Bag/I shows inventory. Arena: right stick aims and fires; Dash/Shift evades; clear three waves. Controls opens touch preferences. Saves retain room, health, inventory, enemies and preferences.")
	OnKlavGame.suspended.connect(func():controls.reset_input();pending_attack=false;dash=0)
func show_controls():controls.show_settings(self)
func spawn_enemies(count:int):
	for e in enemies:world.remove_child(e);e.queue_free()
	enemies.clear()
	for i in range(clampi(count,0,12)):
		var e:=body(Vector2(650+(i%3)*65,150+(i/3)*65),Color("e57783"));e.set_meta("hp",30);enemies.append(e)
func action(name:String):
	if OnKlavGame.paused:return
	if name=="attack":pending_attack=true
	elif name=="dash" and state.ready_for("dash",.8):dash=.15
	elif name=="use":interact()
	elif name=="bag":message.text="Bag: %s · press H to use potion"%str(state.inventory)
func _input(event:InputEvent):
	if OnKlavGame.paused:return
	if event is InputEventKey and event.pressed and not event.echo:
		if event.keycode==KEY_SPACE:action("attack")
		elif event.keycode==KEY_E:action("use")
		elif event.keycode==KEY_I:action("bag")
		elif event.keycode==KEY_SHIFT:action("dash")
		elif event.keycode==KEY_H:use_potion()
func use_potion():
	if state.health<state.max_health and state.take_item("potion"):state.heal(35);OnKlavGame.checkpoint()
func interact():
	if player.position.distance_to(npc)<85:state.set_flag("quest");message.text="Guide: clear the room, then use the exit.";OnKlavGame.checkpoint()
	elif player.position.distance_to(exit_point)<85:
		if not enemies.is_empty():message.text="Defeat the remaining enemies first.";return
		state.level=mini(9999,state.level+1);state.checkpoint=Vector3(100,300,0);player.position=Vector2(100,300);state.set_flag("pickup",false);loot.show();spawn_enemies(mini(10,state.level+1));OnKlavGame.checkpoint()
	else:use_potion()
func attack():
	if not state.ready_for("attack",.28):return
	for e in enemies.duplicate():
		var delta:Vector2=e.position-player.position;var reach:=300.0 if ARENA else 85.0
		if delta.length()<=reach and delta.normalized().dot(facing)>.6:
			e.set_meta("hp",int(e.get_meta("hp"))-15)
			if e.get_meta("hp")<=0:enemies.erase(e);world.remove_child(e);e.queue_free();state.score=mini(9999,state.score+10);state.add_item("gold",2)
			break
	OnKlavGame.checkpoint()
func _physics_process(dt):
	if OnKlavGame.paused or state.health<=0 or (ARENA and state.flags.get("complete",false)):return
	state.tick(dt);elapsed+=dt;dash=maxf(0,dash-dt)
	var axis:Vector2=(controls.input_vector+Vector2(float(Input.is_key_pressed(KEY_D))-float(Input.is_key_pressed(KEY_A)),float(Input.is_key_pressed(KEY_S))-float(Input.is_key_pressed(KEY_W)))).limit_length()
	if axis.length()>.1:facing=axis.normalized()
	if controls.aim_vector.length()>.2:facing=controls.aim_vector.normalized()
	player.velocity=axis*(620 if dash>0 else 200);player.move_and_slide();camera.position=player.position.lerp(Vector2(500,300),.7)
	if pending_attack or controls.is_held("attack") or controls.aim_vector.length()>.3:attack()
	pending_attack=false
	for e in enemies:
		Steering.chase_2d(e,player.position,50+mini(state.wave,6)*5)
		if e.position.distance_to(player.position)<38 and dash<=0 and state.ready_for("hurt",.7):state.damage(10)
	if not state.flags.get("pickup",false) and player.position.distance_to(pickup)<32:state.add_item("potion");state.set_flag("pickup");loot.hide();OnKlavGame.checkpoint()
	if ARENA and enemies.is_empty():
		if state.wave>=3:state.set_flag("complete");again.show();controls.reset_input();message.text="Dungeon cleared!";OnKlavGame.checkpoint()
		else:state.wave+=1;spawn_enemies(state.wave+2);OnKlavGame.checkpoint()
	if state.health<=0:message.text="Defeated · Play again";again.show();controls.reset_input();OnKlavGame.checkpoint()
	status.text="Room %d · Wave %d · Health %d · Gold %d"%[state.level,state.wave,state.health,state.inventory.get("gold",0)]
func snapshot()->Dictionary:
	var list:Array=[]
	for e in enemies:list.append({"position":[e.position.x,e.position.y],"hp":e.get_meta("hp")})
	return {"model":state.snapshot(),"position":[player.position.x,player.position.y],"enemies":list,"controls":controls.settings()}
func restore(d:Dictionary)->bool:
	if not d.get("model") is Dictionary or not State.valid(d.model) or not d.get("position") is Array or d.position.size()!=2 or not d.get("enemies") is Array or d.enemies.size()>12 or not d.get("controls") is Dictionary:return false
	for v in d.position:
		if not State.number(v,30,970):return false
	if d.position[1]>570:return false
	for e in d.enemies:
		if not e is Dictionary or not e.get("position") is Array or e.position.size()!=2 or not State.number(e.get("hp"),1,30,true):return false
		for v in e.position:
			if not State.number(v,0,1000):return false
	var test:=Touch.new();var valid:bool=test.apply_settings(d.controls);test.free()
	if not valid:return false
	state.restore(d.model);controls.apply_settings(d.controls);player.position=Vector2(d.position[0],d.position[1]);player.velocity=Vector2.ZERO;spawn_enemies(d.enemies.size())
	for i in range(enemies.size()):enemies[i].position=Vector2(d.enemies[i].position[0],d.enemies[i].position[1]);enemies[i].set_meta("hp",d.enemies[i].hp)
	loot.visible=not state.flags.get("pickup",false);again.visible=state.health<=0 or state.flags.get("complete",false);pending_attack=false;dash=0;return true
func start_new():
	state=State.new();state.checkpoint=Vector3(100,300,0);player.position=Vector2(100,300);player.velocity=Vector2.ZERO;spawn_enemies(3 if ARENA else 2);loot.show();again.hide();controls.reset_input();pending_attack=false;dash=0;message.text="Aim with the right stick" if ARENA else "Green gem: potion · Guide: Use · Exit: clear room first"
