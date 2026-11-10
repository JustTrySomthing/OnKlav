extends SceneTree
const State=preload("res://gameplay_state.gd")
const Steering=preload("res://enemy_steering.gd")
const Touch=preload("res://touch_controls.gd")
var checks:=0
var failed:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failed+=1;printerr("FAIL ",label)
func _init():call_deferred("run")
func run():
	var s=State.new();check(s.damage(200)==100 and s.health==0,"damage clamps at zero")
	s.respawn();s.damage(40);s.heal(100);check(s.health==100,"healing clamps")
	check(s.add_item("key",2) and s.take_item("key") and not s.take_item("key",2),"inventory cannot overspend")
	check(not s.add_item("",1) and not s.add_item("gold",-1),"invalid inventory rejected")
	check(s.ready_for("fire",.2) and not s.ready_for("fire",.2),"cooldown gates repeats");s.tick(.3);check(s.ready_for("fire",.2),"cooldown advances")
	s.switch_weapon();s.checkpoint=Vector3(20,30,0);s.set_flag("door");var saved:Dictionary=JSON.parse_string(JSON.stringify(s.snapshot()));var restored=State.new();check(restored.restore(saved) and JSON.parse_string(JSON.stringify(restored.snapshot()))==saved,"model JSON roundtrip")
	var before:Dictionary=restored.snapshot();saved.health=-1;check(not restored.restore(saved) and restored.snapshot()==before,"bad model atomic rejection")
	var route=Steering.grid_path(Vector2i(0,0),Vector2i(2,0),Rect2i(0,0,3,3),{Vector2i(1,0):true});check(route.size()==4 and route.back()==Vector2i(2,0),"path goes around blocked tile")
	check(Steering.grid_path(Vector2i(0,0),Vector2i(2,0),Rect2i(0,0,3,3),{Vector2i(1,0):true},1).is_empty(),"path bounded budget")
	var c=Touch.new();root.add_child(c);c.set_anchors_preset(Control.PRESET_TOP_LEFT);c.size=Vector2(1000,600);c.configure("arena");c.floating_stick=false
	c.feed(1,c.home(),true);c.feed(1,c.home()+Vector2(60,0));c.feed(2,c.aim_home(),true);c.feed(2,c.aim_home()+Vector2(0,-60));check(c.input_vector.x>0 and c.aim_vector.y<0,"independent move and aim")
	c.feed(2,Vector2.ZERO,false,true);check(c.aim_vector==Vector2.ZERO and c.input_vector.x>0,"aim release preserves movement")
	var prefs:Dictionary=c.settings();prefs.left_handed=true;prefs.scale=1.2;check(c.apply_settings(prefs) and c.input_vector==Vector2.ZERO,"settings clear ownership")
	c.feed(3,c.mirror(c.home()),true);c.feed(3,c.mirror(c.home()+Vector2(60,0)));check(c.input_vector.x>0,"left handed hit mapping")
	prefs.scale=NAN;var old:Dictionary=c.settings();check(not c.apply_settings(prefs) and c.settings()==old,"invalid settings atomic rejection")
	c.queue_free();await process_frame;print("GAMEPLAY_COMPONENTS_RESULT checks=%d failures=%d"%[checks,failed]);quit(1 if failed else 0)
