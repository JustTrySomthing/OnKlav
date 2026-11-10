extends SceneTree
const D=preload("res://panda/duel.gd")
const L=preload("res://panda/model.gd")
var checks:=0
var failures:=0
func check(ok:bool,message:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL "+message)
func clone(data):return JSON.parse_string(JSON.stringify(data))
func fly(m):
	var count:=0
	while m.phase=="flight" and count<1700:m.step();count+=1
	check(m.phase=="report" and count<=1680,"bounded shot resolves to readable report")
func _init():call_deferred("run")
func run():
	for stage in range(12):
		print("DUEL_COURSE ",stage+1)
		var m=D.new(9183);m.stage=stage;m.stars=[]
		for i in range(stage):m.stars.append(0)
		m.setup_stage()
		check(m.phase=="aim" and m.actor==0 and m.shapes.size()>=14 and m.shapes.size()<=40,"handmade playable course")
		check(m.boards[0]==m.boards[1],"both players start with identical towers")
		check(m.platforms.size()+1+m.shapes.size()<=45,"physics body budget")
		for s in m.shapes:check(s.y-s.h*.5>=35 and s.x-s.w*.5>=100 and s.x+s.w*.5<=900,"course visible inside arena")
		check(not m.launch(-43,50) and not m.launch(0,101),"invalid shot rejected")
		var shot_count:=0
		while m.phase not in["round","finished"] and shot_count<9:
			var previous_actor:int=m.actor;var other_board:Dictionary=m.boards[1-m.actor].duplicate(true)
			var pick:Vector2i=m.ai_shot()
			check(m.launch(pick.x,pick.y),"valid tactical launch")
			for i in range(150 if shot_count==0 or stage in[0,5,11] else 0):
				if m.phase=="flight":m.step()
			var snapshot:Dictionary=clone(m.snapshot());var copy=D.new();check(copy.restore(snapshot),"in-flight JSON restore")
			if shot_count>0 and stage not in[0,5,11]:m.world=null;m.phase="report";copy.world=null;copy.phase="report"
			fly(m);fly(copy)
			check(clone(m.snapshot())==clone(copy.snapshot()),"exact deterministic continuation course %d shot %d"%[stage+1,shot_count+1])
			check(m.boards[1-m.actor]==other_board,"opposing tower untouched by current shot")
			var points:int=m.totals[0]+m.totals[1];var off:int=m.off();m.step();check(points==m.totals[0]+m.totals[1] and off==m.off(),"no duplicate scoring after report")
			check(m.turn_over(),"automatic turn handoff")
			if m.phase=="aim":check(m.actor==(0 if m.eligible(0) else 1),"player completes volley before rival")
			shot_count+=1
		check(m.phase in["round","finished"] and m.stars.size()==stage+1,"round terminates and awards exactly one rating")
		check(not m.turn_over(),"round report cannot double-award wins or stars")
		var after=D.new();check(after.restore(clone(m.snapshot())),"round snapshot recovery")
		if stage<11:check(after.next_stage() and after.phase=="aim" and after.actor==0,"next course bypasses any building phase")
		else:check(not after.next_stage(),"campaign has finite twelve-stage ending")
	# Distinct layouts and a once-only fuse-to-fuse chain in the finale.
	var silhouettes:={}
	for stage in range(12):
		var course=D.new();course.stage=stage;course.setup_stage();silhouettes[str(course.shapes)]=true
	check(silhouettes.size()==12,"twelve distinct demolition puzzles")
	var finale=D.new();finale.stage=11
	for i in range(11):finale.stars.append(0)
	finale.setup_stage();finale.launch(0,70)
	finale.award(9,true);check(finale.boards[0].blocks[13].out,"neighboring fuse detonates in chain")
	var score:int=finale.totals[0];finale.award(9,true);check(finale.totals[0]==score,"no repeated explosive reward")
	var copy=D.new();check(copy.restore(clone(finale.snapshot())),"blast chain checkpoint restores")
	# Rare birds: persisted scheduling, one pass, cooldown and complete silhouette in upper quarter.
	var visits:=0
	for seed_id in range(1,201):
		var m=D.new(seed_id)
		for turn in range(36):
			m.actor=0;m.boards[0].shots=turn%3;m.stage=turn/3
			while m.stars.size()<m.stage:m.stars.append(0)
			m.prepare_bird()
			if not m.bird.is_empty():
				visits+=1;var origin=D.new();check(origin.restore(clone(m.snapshot())) and origin.bird==m.bird,"bird is model-owned")
				for tick in range(80):
					m.bird.age=m.bird.delay+tick*.07
					if m.bird_visible():check(m.bird_at().y-55*m.bird.size>=0 and m.bird_at().y+55*m.bird.size<300,"full bonus silhouette stays in top quarter")
				m.bird.age=20;check(not m.bird_visible(),"bird leaves and never wraps around")
	check(visits>160 and visits<800,"birds are rare random visits, never continuously present")
	# Current and malformed restore must never mutate a live model.
	var current=D.new(721);var reference:Dictionary=clone(current.snapshot())
	for field in ["actor","phase","boards","bird","stars"]:
		var corrupt:Dictionary=reference.duplicate(true);corrupt[field]=null
		check(not current.restore(corrupt) and clone(current.snapshot())==reference,"reject corrupt "+field+" without mutation")
	# Legacy solo checkpoints remain readable, including a shot already in motion.
	for version in [1,2,3]:
		var old=L.new();old.revision=version;old.accept(0,"C0,123" if version==3 else "C0");old.accept(1,"B"+old.complete(old.empty(),0));old.accept(0,"S8,70,0");old.step()
		var migrated=D.new();check(migrated.import_legacy(clone(old.snapshot())),"legacy campaign migration")
		check(migrated.phase=="flight" and clone(migrated.world.snapshot())==clone(old.world.snapshot()),"legacy in-flight bodies preserved")
		check(migrated.restore(clone(migrated.snapshot())),"migrated state can be saved and reopened")
	var exhausted=L.new()
	exhausted.accept(0,"C0,123")
	exhausted.accept(1,"B"+exhausted.complete(exhausted.empty(),0))
	exhausted.shots=exhausted.shot_limit()
	var migrated=D.new()
	check(migrated.import_legacy(clone(exhausted.snapshot())),"migrate old aim save with no player shots left")
	check(migrated.phase=="aim" and migrated.actor==1 and migrated.eligible(1),"old exhausted player aim advances to rival")
	check(D.new().restore(clone(migrated.snapshot())),"repaired legacy aim round-trips")
	exhausted.leg=1
	exhausted.results=[[1,3,150]]
	check(migrated.import_legacy(clone(exhausted.snapshot())) and migrated.phase=="round","old exhausted rival aim reaches round result")
	check(D.new().restore(clone(migrated.snapshot())),"repaired legacy result round-trips")
	print("PANDA_DUEL_RESULT checks=%d failures=%d birds=%d"%[checks,failures,visits])
	quit(1 if failures else 0)
