extends SceneTree
const D=preload("res://panda/duel.gd")
const P=preload("res://panda/duel_physics.gd")
const L=preload("res://panda/physics.gd")
const A=preload("res://panda/battle_arena.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL "+label)
func clone(d):return JSON.parse_string(JSON.stringify(d))
func collision(p):
	p.add(Vector2(0,90),Vector2(60,10),0)
	var block=p.add(Vector2(0,65),Vector2(20,20),2);block.v=Vector2(0,900)
	p.step();return p.impact
func _init():call_deferred("run")
func run():
	check(collision(P.new())>130,"block-to-block fracture impact is recorded")
	check(collision(L.new())==0,"legacy solver behavior preserved")
	var m=D.new();var before:Dictionary=clone(m.snapshot());var bad:Dictionary=clone(before);bad.boards[0].shots=3
	check(not m.restore(bad) and clone(m.snapshot())==before,"unshootable aim save rejected without mutation")
	bad=clone(before)
	for b in bad.boards[0].blocks:b.out=true
	check(not m.restore(bad),"cleared aim board rejected")
	m.launch(0,80)
	var snapshot:Dictionary=clone(m.snapshot())
	bad=snapshot.duplicate(true);bad.erase("bodies")
	check(not m.restore(bad) and clone(m.snapshot())==snapshot,"Missing bodies reject the save without mutating the active shot")
	for invalid_bodies in [null,42,{},[]]:
		bad=snapshot.duplicate(true);bad.bodies=invalid_bodies
		check(not m.restore(bad) and clone(m.snapshot())==snapshot,"Malformed bodies reject the save without mutating the active shot")
	for field in [10,12,13]:
		bad=snapshot.duplicate(true);bad.bodies[0][field]=not bad.bodies[0][field]
		check(not D.new().restore(bad),"bad platform body role rejected")
	bad=snapshot.duplicate(true);bad.bodies[m.platforms.size()][10]=false
	check(not D.new().restore(bad),"non-circle projectile rejected")
	bad=snapshot.duplicate(true);bad.bodies[m.platforms.size()+1][4]+=10
	check(not D.new().restore(bad),"mismatched block geometry rejected")
	var copy=D.new();check(copy.restore(snapshot),"valid flight still restores")
	for i in range(600):
		m.step();copy.step()
	check(clone(m.snapshot())==clone(copy.snapshot()),"structural impacts exact after JSON resume")
	var arena=A.new();arena.model=D.new();arena.local_turn=true;arena.size=Vector2(360,600)
	var press:=InputEventScreenTouch.new();press.pressed=true;press.index=0;press.position=arena.point_to_screen(arena.aim_anchor());arena.input_event(press)
	check(arena.aiming and arena.pointer_id==0,"primary touch owns gesture")
	var cancel:=InputEventScreenTouch.new();cancel.index=1;cancel.canceled=true;arena.input_event(cancel)
	check(arena.aiming and arena.pointer_id==0,"canceled second finger does not cancel primary")
	cancel.index=0;arena.input_event(cancel);check(not arena.aiming and arena.pointer_id==-1,"canceled owner releases gesture")
	var aim_model=D.new()
	var guide:Dictionary=aim_model.aim_preview(0,50)
	check(guide.contact.is_finite() and not guide.points.is_empty(),"The guide finds the opening target")
	aim_model.launch(0,50)
	for i in 7:aim_model.step()
	var ball=aim_model.world.bodies[aim_model.platforms.size()]
	check(ball.p.distance_to(guide.points[0])<.03,"The first aim dot matches the real shot's integration")
	var released:Array=[]
	arena.fired.connect(func(angle,power):released.append(Vector2i(angle,power)))
	arena.input_event(press)
	var drag:=InputEventScreenDrag.new()
	drag.index=0
	drag.position=press.position+Vector2(-17,56)
	arena.input_event(drag)
	drag.position+=Vector2(-5,12)
	arena.input_event(drag)
	var preview:Vector2i=arena.preview_input
	check(arena.preview_path.is_empty() and preview.y>=10,"Drag events defer trajectory computation")
	arena.refresh_aim_preview()
	check(arena.rendered_preview_input==preview and not arena.preview_path.is_empty(),"A frame uses the newest drag position")
	arena.shake=.6;arena.time=.15
	var release:=InputEventScreenTouch.new()
	release.index=0;release.position=drag.position
	arena.input_event(release)
	check(released==[preview] and arena.preview_path.is_empty(),"Release uses the previewed angle and power despite shake")
	arena.reduced=true
	arena.burst(Vector2.ZERO)
	check(arena.rings.is_empty(),"Reduced motion suppresses expanding blast rings")
	arena.free()
	var result_model=D.new()
	result_model.totals=[1000,900]
	result_model.boards[0].score=100
	result_model.boards[1].score=200
	result_model.phase="round"
	check(result_model.result_winner()==1,"Round result uses round scores even when the player leads overall")
	result_model.phase="finished"
	check(result_model.result_winner()==0,"Campaign result uses cumulative scores")
	result_model.totals[1]=1000
	check(result_model.result_winner()==-1,"Tied campaign has no winning panda")
	var sound=load("res://panda/audio.gd").new();root.add_child(sound);root.get_node("OnKlavGame").paused=false
	sound.play("hit");sound.play("glass");sound.play("block");sound.play("glass")
	check(sound.played==["hit","glass","block"],"impact no longer suppresses material sounds and repeats stay bounded")
	sound.stop();sound.free()
	await create_timer(.25).timeout
	print("PANDA_AUDIT_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
