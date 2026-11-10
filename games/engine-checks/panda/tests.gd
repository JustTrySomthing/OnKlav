extends SceneTree
const M=preload("res://panda/model.gd")
const P=preload("res://panda/physics.gd")
var checks:=0
var failures:=0
func check(ok:bool,message:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL "+message)
func json_clone(d):return JSON.parse_string(JSON.stringify(d))
func run_flight(m):
	var steps:=0
	while m.phase=="flight" and steps<1700:m.step();steps+=1
	check(steps<=1680 and m.phase!="flight","bounded flight")
func old_model():
	var m=M.new();m.revision=2;return m
func _init():call_deferred("run")
func run():
	var started:=Time.get_ticks_msec()
	for l in range(3):
		for r in range(5):
			var m=old_model();m.level=l;m.round_id=r
			for seed_id in range(4):
				var d:String=m.complete(m.empty(),seed_id)
				check(m.valid(d,true),"complete geometry %d %d %d"%[l,r,seed_id]);check(d.length()<=24,"compact SMS layout")
				for i in range(m.count()):
					var changed:String=m.place(d,i,Vector2(490,300),not m.rotated(d,i));check(changed=="" or m.valid(changed,true),"supported relocation")
			check(not m.valid("?".repeat(m.count()*2)),"bad geometry")
	var m=old_model();check(not m.accept(1,"C0"),"host chooses");check(m.accept(0,"C1"),"red panda choice");check(m.turn==1 and m.builder()==1,"first defender")
	check(not m.accept(0,"B"+m.complete(m.empty())),"wrong builder refused")
	check(m.accept(1,"B"+m.complete(m.empty())),"build accepted")
	check(m.turn==0 and m.phase=="shoot","shooter turn")
	for s in ["S90,80,0","S0,101,1","S0,NaN,1","S0,80,4","S0,80,1,2","Bbad"]:check(not m.accept(0,s),"bad action "+s)
	check(m.accept(0,"S0,75,3"),"shockwave launch")
	for i in range(150):m.step()
	var restored=old_model();check(restored.restore(json_clone(m.snapshot())),"midflight JSON restore")
	while m.phase=="flight":
		m.step();restored.step()
	
	check(m.snapshot()==restored.snapshot(),"exact restored physics continuation");check(m.burst,"shockwave hits block");check(m.total[0]==m.off(),"score every fallen block once")
	check(not m.charges[2],"one charge used")
	if m.phase=="shoot":check(not m.accept(0,"S0,75,3"),"spent charge refused")
	for special in [1,2]:
		var n=old_model();n.accept(0,"C0");n.accept(1,"B"+n.complete(n.empty(),33));check(n.accept(0,"S-9,70,%d"%special),"power accepted");run_flight(n);check(n.total[0]==n.off(),"power score")
	var stable=old_model();stable.accept(0,"C0");stable.accept(1,"B"+stable.complete(stable.empty()))
	check(stable.accept(0,"F"),"timeout ends attack");check(stable.phase=="review" and stable.turn==0,"host review")
	check(not stable.accept(1,"N"),"guest cannot advance");check(stable.accept(0,"N") and stable.leg==1 and stable.turn==0,"paired opposite defender")
	stable.accept(0,"B"+stable.complete(stable.empty()));stable.accept(1,"F");check(stable.summary=="Round drawn","paired tie")
	stable.accept(0,"N");check(stable.round_id==1 and stable.leg==0,"round advance")
	var campaign=old_model();campaign.phase="review";campaign.leg=1
	for l in range(3):
		for r in range(4):
			campaign.round_id=r;campaign.phase="review";campaign.leg=1;campaign.level_rounds=[2,0];campaign.turn=0;campaign.total=[10+l,4];check(campaign.accept(0,"N"),"campaign advance")
	check(campaign.winner==0 and campaign.level_wins==[3,0],"campaign champion")
	var tie=old_model();tie.round_id=3;tie.leg=1;tie.phase="review";tie.advance_round();check(tie.round_id==4 and tie.level==0,"level tiebreak")
	tie.advance_round();check(tie.round_id==4 and tie.level==0,"unnumbered repeated tiebreak")
	tie.level_rounds=[1,0];tie.advance_round();check(tie.level==1 and tie.round_id==0,"tiebreak settles level")
	var source=stable.snapshot()
	for key in ["shots","level","ticks","turn"]:
		var bad=source.duplicate(true);bad[key]=99999;check(not old_model().restore(bad),"bad save "+key)
	var bad=source.duplicate(true);bad.charges=[1,true,true];check(not old_model().restore(bad),"boolean charges")
	bad=source.duplicate(true);bad.blocks=[{"x":INF,"y":0,"angle":0,"out":false}];check(not old_model().restore(bad),"nonfinite body")
	var physics=P.new();var floor=physics.add(Vector2(0,100),Vector2(100,10),0);var body=physics.add(Vector2(0,60),Vector2(20,20),1)
	for i in range(1000):physics.step()
	check(absf(body.p.y-70)<.5 and absf(body.angle)<.03,"resting stack stable");check(body.v.length()<5,"resting velocity bounded")
	var replay=old_model();check(replay.restore(json_clone(stable.snapshot())),"between-phase restore")
	var angled=old_model();angled.accept(0,"C0");angled.accept(1,"B"+angled.complete(angled.empty()));angled.accept(0,"S30,70,0")
	check(angled.world.bodies[angled.decks().size()].p==angled.muzzle(30),"shot starts at the visible angled muzzle")
	check(absf(angled.muzzle(30).distance_to(Vector2(500,1200))-140)<.01,"loaded head follows barrel arm")
	var modern=old_model();check(modern.count()==8 and modern.dims(0)==Vector2(80,80),"larger eight-piece opening")
	modern.level=1;check(modern.count()==10,"ten-piece middle level");modern.level=2;check(modern.count()==12,"twelve-piece final level")
	var legacy=old_model();legacy.revision=1;legacy.accept(0,"C0");legacy.accept(1,"B"+legacy.complete(legacy.empty()));legacy.accept(0,"S0,75,0")
	for i in range(180):legacy.step()
	var old_save:Dictionary=json_clone(legacy.snapshot())
	for field in ["bonus","duck_hit","special_claimed","extra_shot"]:old_save.erase(field)
	var migrated=old_model();check(migrated.restore(old_save) and migrated.revision==1 and migrated.count()==6,"legacy in-flight rules retained")
	while legacy.phase=="flight":legacy.step();migrated.step()
	check(legacy.snapshot()==migrated.snapshot(),"legacy physics continuation unchanged")
	var duck=old_model();duck.round_id=1;duck.accept(0,"C0");duck.accept(1,"B"+duck.complete(duck.empty(),13));duck.accept(0,"S0,70,0");duck.ticks=239
	var ball=duck.world.bodies[duck.decks().size()];ball.p=duck.duck_at();ball.v=Vector2.ZERO;duck.step()
	check(duck.duck_hit and duck.bonus[0]==250 and duck.reward=="DUCK BONUS +250","duck collision grants bonus")
	duck.step();check(duck.bonus[0]==250,"duck reward is once per attack")
	var duck_copy=old_model();check(duck_copy.restore(json_clone(duck.snapshot())) and duck_copy.duck_hit and duck_copy.bonus[0]==250,"duck reward survives save without duplication")
	var star=old_model();star.level=2;star.round_id=1;star.accept(0,"C0");star.accept(1,"B"+star.complete(star.empty(),5));star.accept(0,"S0,70,0")
	var starred=star.world.bodies[star.decks().size()+1+star.special_id()];starred.p=Vector2(800,900);star.step()
	check(star.special_claimed and star.extra_shot and star.shot_limit()==4 and star.bonus[0]==150,"pulsing special grants blast and hardest-level bonus panda")
	var star_copy=old_model();check(star_copy.restore(json_clone(star.snapshot())) and star_copy.extra_shot,"bonus shot checkpoint")
	star.step();check(star.bonus[0]==150,"special block reward is once only")
	var dark=old_model();dark.level=1;dark.round_id=1;dark.accept(0,"C0");dark.accept(1,"B"+dark.complete(dark.empty(),5));dark.accept(0,"S0,70,0");dark.ticks=180
	var dimmed=dark.world.bodies[dark.decks().size()+1+dark.special_id()];dimmed.p=Vector2(800,900);dark.step();check(not dark.special_claimed and dark.bonus[0]==0,"unlit special has no reward")
	check(not old_model().restore({"version":2.5}),"fractional revision rejected")
	# Revision3: all courses fit more blocks, with stable per-campaign randomness.
	for l in range(3):
		for r in range(5):
			for seed_id in range(1,3):
				var n=M.new();n.level=l;n.round_id=r;n.course_seed=seed_id*7919
				var d:String=n.complete(n.empty(),seed_id)
				check(n.valid(d,true) and n.count()==12+l*3,"modern course complete %d/%d/%d"%[l,r,seed_id])
				check(d.length()+1<=64,"modern layout fits friend intent")
				for deck in n.decks():check(deck[2]<=540 and deck[2]>=400,"raised platforms")
	var current=M.new();check(current.accept(0,"C1,93218"),"seeded character intent")
	check(current.accept(1,"B"+current.complete(current.empty(),18)),"modern build accepted")
	check(current.duck_active() and current.bird_ticks==0,"bird visible before first launch")
	var styles:={}
	for tick in range(0,12000,11):
		current.bird_ticks=tick;var at:Vector2=current.duck_at();var style:Dictionary=current.bird_profile()
		check(at.y-50*float(style.size)>0 and at.y+50*float(style.size)<500,"bird stays in upper half")
		styles[str(style)]=true
	check(styles.size()>3,"bird routes vary across passes")
	current.bird_ticks=171
	check(not current.accept(0,"S0,75,0,170"),"stale bird clock refused")
	check(current.accept(0,"S0,75,0,171"),"launch commits visible bird clock")
	for i in range(100):current.step()
	var copy=M.new();check(copy.restore(json_clone(current.snapshot())),"modern material flight restored")
	while current.phase=="flight":current.step();copy.step()
	check(current.snapshot()==copy.snapshot(),"modern weighted flight exact after restore")
	var target=M.new();target.accept(0,"C0,1234");target.accept(1,"B"+target.complete(target.empty()));target.accept(0,"S0,75,0,400")
	var projectile=target.world.bodies[target.decks().size()];projectile.p=target.duck_at();projectile.v=Vector2.ZERO;target.step()
	check(target.duck_hit and target.bonus[0]==250,"upper sky bird collision awards once")
	target.step();check(target.bonus[0]==250,"modern bird bonus not duplicated")
	check(target.density(0)!=target.density(1),"different material density")
	var arena=load("res://panda/arena.gd").new();arena.model=M.new();arena.model.level=2
	for screen in [Vector2(300,320),Vector2(340,460),Vector2(410,660),Vector2(700,340)]:
		arena.size=screen
		for i in range(arena.model.count()):
			var center:Vector2=arena.origin()+arena.tray(i)*arena.factor();var half:Vector2=arena.model.dims(i)*.35*arena.factor()
			check(center.y+half.y<screen.y-6 and center.y-half.y>0,"reserve inside arena above controls")
		var start:Vector2=arena.origin()+arena.aim_anchor()*arena.factor()
		check(start.y+284*arena.factor()<screen.y,"full pull has room")
	arena.free()
	print("PANDA_RESULT checks=%d failures=%d elapsed_ms=%d"%[checks,failures,Time.get_ticks_msec()-started]);quit(0 if failures==0 else 1)
