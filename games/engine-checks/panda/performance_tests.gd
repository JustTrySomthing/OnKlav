extends SceneTree
const D=preload("res://panda/duel.gd")
var failures:=0
func _init():call_deferred("run")
func run():
	for course in range(12):
		var m=D.new(5920);m.stage=course;m.setup_stage();m.launch(42,10)
		var ball=m.world.bodies[m.platforms.size()];ball.enabled=false;ball.v=Vector2.ZERO
		while m.phase=="flight":m.step()
		if m.off()!=0:failures+=1;printerr("FAIL unattended tower course ",course+1," toppled ",m.off())
	var rows:=[]
	for span in [1,2]:
		var started:=Time.get_ticks_usec();var steps:=0
		for repetition in range(2):
			var m=D.new(5920);m.stage=11;m.setup_stage();m.launch(0,100);m.physics_revision=span;m.world.tick_span=span;m.world.tower_awake=true;m.world.awake_mask=[]
			while m.phase=="flight":m.step();steps+=1
		rows.append({"span":span,"solver_steps":steps,"cpu_ms":(Time.get_ticks_usec()-started)/1000.0})
	print("PANDA_PERFORMANCE_RESULT ",JSON.stringify({"unattended_courses":12,"failures":failures,"results":rows}))
	quit(1 if failures else 0)
