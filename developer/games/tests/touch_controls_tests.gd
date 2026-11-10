extends SceneTree
const Touch=preload("res://touch_controls.gd")
var checks:=0
var failed:=0
var pressed:Array=[]
var released:Array=[]
var look:=Vector2.ZERO
func check(ok:bool,label:String):
	checks+=1
	if not ok:failed+=1;printerr("FAIL ",label)
func _init():call_deferred("run")
func run():
	var c=Touch.new();root.add_child(c);c.size=Vector2(1000,540);c.configure("firstperson")
	c.action_pressed.connect(func(a):pressed.append(a));c.action_released.connect(func(a):released.append(a));c.look_changed.connect(func(d):look+=d)
	var anchor:=c.home();check(c.feed(1,anchor,true),"movement finger captured")
	c.feed(1,anchor+Vector2(100,0));check(c.input_vector.x>.9 and c.input_vector.length()<=1,"normalized movement clamps")
	check(c.feed(2,Vector2(600,180),true),"independent looking finger")
	c.feed(2,Vector2(650,200));check(look.x>0 and look.y>0,"look delta emitted")
	check(c.feed(3,c.button_rect(0).get_center(),true) and c.is_held("fire"),"third finger can fire while moving and looking")
	check(not c.feed(4,c.button_rect(0).get_center(),true),"second finger cannot steal held action")
	c.feed(3,Vector2(-100,-100),false,true);check(not c.is_held("fire") and released==["fire"] and c.input_vector.x>.9,"release outside only clears its action")
	c.reset_input();check(c.input_vector==Vector2.ZERO and c.pointers.is_empty() and c.look_owner==-1,"pause/reset clears all ownership")
	c.configure("platformer");check(c.horizontal_only and not c.look_enabled,"platformer preset")
	c.movement_style="dpad";c.feed(10,c.home()+Vector2(c.radius()*.7,0),true)
	check(c.input_vector==Vector2.RIGHT,"D-pad press moves immediately")
	c.feed(10,Vector2.ZERO,false,false,true);check(c.input_vector==Vector2.ZERO,"cancel stops movement")
	c.feed(1,c.button_rect(0).get_center(),true);c._notification(NOTIFICATION_APPLICATION_FOCUS_OUT)
	check(not c.is_held("jump") and c.pointers.is_empty(),"focus loss releases jump")
	check(not c.feed(99,Vector2(-1,-1),true),"outside press ignored")
	c.enabled=false;check(not c.feed(1,c.home(),true) and c.input_vector==Vector2.ZERO,"disabled controls cannot capture input")
	c.free();print("TOUCH_CONTROLS_RESULT checks=%d failures=%d"%[checks,failed]);quit(1 if failed else 0)
