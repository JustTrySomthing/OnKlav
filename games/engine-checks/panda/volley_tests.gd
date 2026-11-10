extends SceneTree
const D=preload("res://panda/duel.gd")
const Clock=preload("res://panda/flight_clock.gd")
const Arena=preload("res://panda/battle_arena.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL "+label)
func clone(d):return JSON.parse_string(JSON.stringify(d))
func report(m):
	check(m.launch(0,60),"volley shot is launchable")
	m.world=null;m.phase="report"
func _init():call_deferred("run")
func run():
	var m=D.new()
	for side in range(2):
		for shot in range(3):
			check(m.actor==side,"three player shots before three rival shots")
			report(m)
			var copy=D.new();check(copy.restore(clone(m.snapshot())),"volley report restores exactly")
			check(copy.snapshot()==m.snapshot(),"current volley save does not change actor")
			m.turn_over()
	check(m.phase=="round" and m.boards[0].shots==3 and m.boards[1].shots==3,"round finishes after both volleys")
	m=D.new();m.boards[0].limit=4
	for i in range(4):check(m.actor==0,"earned extra shot stays in player volley");report(m);m.turn_over()
	check(m.actor==1,"rival starts after player bonus shot")
	m=D.new();report(m)
	for b in m.boards[0].blocks:b.out=true
	m.turn_over();check(m.actor==1,"cleared player board hands over without deadlock")
	m=D.new();var old:Dictionary=clone(m.snapshot());old.erase("turn_rule");old.actor=1;old.boards[0].shots=1
	var resumed=D.new();check(resumed.restore(old) and resumed.actor==0,"old queued rival aim gives unused player shots back")
	m.actor=1;m.launch(0,60);old=clone(m.snapshot());old.erase("turn_rule")
	check(resumed.restore(old) and resumed.actor==1 and resumed.phase=="flight","old committed rival flight is not discarded")
	for fps in [15,20,24,30,60,120]:
		var clock=Clock.new();var ticks:=0
		for i in range(fps*3):ticks+=clock.take(1.0/fps)
		check(ticks==720,"no slow motion at %d FPS"%fps)
		clock.reset();check(clock.take(.001)==0,"reset removes pending simulation debt")
	check(is_equal_approx(Clock.elapsed(1000000,1100000,.05),.1),"wall clock bypasses clamped engine deltas")
	check(is_equal_approx(Clock.elapsed(1000000,1010000,.1),.01),"reset time excludes pause duration")
	check(is_equal_approx(Clock.elapsed(1000000,2000000,.05),.2),"elapsed wall time remains bounded")
	var clock=Clock.new();check(clock.take(10)==Clock.MAX_STEPS,"long stalls have bounded catch-up")
	check(clock.take(-1)==0 and clock.take(NAN)==0,"invalid frame deltas cannot corrupt clock")
	for fps in [10,15,20,30,60,120]:
		var c=Clock.new();var count:=0
		for i in range(fps*3):count+=c.take(1.0/fps,2)
		check(count==360,"120 Hz pacing at %d FPS"%fps)
	check(D.points(100)==10 and D.points(200)==20 and D.points(350)==35 and D.points(2500)==250,"all score values use ten percent units")
	m=D.new();m.launch(0,60);m.step();check(m.ticks==2 and m.world.tick_span==2,"new flight performs 120 physics steps per second")
	var saved:Dictionary=clone(m.snapshot());saved.erase("physics_revision");saved.ticks=0
	check(resumed.restore(saved) and resumed.tick_span()==1,"unmarked old flights keep original step rate")
	var before:Dictionary=resumed.snapshot();saved.physics_revision=4
	check(not resumed.restore(saved) and resumed.snapshot()==before,"invalid physics revisions are rejected without mutation")
	var a=Arena.new();a.model=D.new();check(a.cannon_color()==Arena.HELMET_COLORS[0],"player cannon matches gold helmet")
	a.model.actor=1;check(a.cannon_color()==Arena.HELMET_COLORS[1] and a.cannon_color().b>a.cannon_color().r,"rival cannon is blue like helmet")
	a.power=80;a.cancel();check(a.power==0,"idle and cancelled charge is zero")
	a.free()
	print("PANDA_VOLLEY_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
