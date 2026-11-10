extends SceneTree
const D=preload("res://panda/duel.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL ",label)
func clone(d):return JSON.parse_string(JSON.stringify(d))
func course(stage:int,seed:int=5920):
	var m=D.new(seed);m.stage=stage;m.stars=[]
	for i in range(stage):m.stars.append(0)
	m.setup_stage();return m
func _init():call_deferred("run")
func run():
	var counts:=[15,18,20,22,24,26,28,30,32,34,36,40]
	for stage in range(12):
		var m=course(stage)
		check(m.shapes.size()==counts[stage],"piece count grows course %d"%(stage+1))
		check(m.shapes.size()+m.platforms.size()+1<=45,"bounded expanded body count")
		for s in m.shapes:check(s.y-s.h*.5>=35 and s.x-s.w*.5>=100 and s.x+s.w*.5<=900,"structure fits arena")
		check(m.boards[0]==m.boards[1],"fair independent targets")
		m.launch(42,10);m.world.bodies[m.platforms.size()].enabled=false
		while m.phase=="flight":m.step()
		check(m.off()==0,"unhit targets stay put")
	# Campaign seeds vary each course without rerolling either player's target.
	for stage in range(12):
		var layouts:Dictionary={}
		for seed in range(1,33):
			var candidate=course(stage,seed)
			layouts[JSON.stringify(candidate.platforms)]=true
			for deck in candidate.platforms:
				check(deck[2]>=250 and deck[2]<=495,"raised decks stay inside arena")
			for piece in candidate.shapes:
				check(piece.y-piece.h*.5>=35 and piece.x-piece.w*.5>=100 and piece.x+piece.w*.5<=900,"varied blocks fit arena")
			var copy=D.new()
			check(copy.restore(clone(candidate.snapshot())),"varied course survives save and load")
			check(copy.platforms==candidate.platforms and copy.shapes==candidate.shapes,"save retains exact course geometry")
		check(layouts.size()>=28,"campaign seeds produce varied platform layouts")
		var same=course(stage)
		var repeat=course(stage)
		check(same.platforms==repeat.platforms and same.shapes==repeat.shapes,"course seed is reproducible")
	# The first three rounds teach a straight hit, cutting a support, then steering.
	for spec in [[0,0,50,4],[1,-8,48,6],[2,-8,48,4],[2,8,48,4]]:
		var opening=course(spec[0])
		check(opening.launch(spec[1],spec[2]),"Opening course shot launches")
		while opening.phase=="flight":opening.step()
		check(opening.shot_toppled>=spec[3] and opening.off(1)==0,"Opening course rewards its intended shot")
		if spec[0]<2:
			check(opening.boards[0].blocks[0].out,"The marked route breaks the lower support")
	# Both routes are real cannon shots, including a seed that mirrors the piers.
	for spec in [[5920,-10,58,2],[5920,10,76,1],[17,-6,58,2],[17,12,58,1]]:
		var bridge=course(3,spec[0])
		check(bridge.launch(spec[1],spec[2]),"Bridge shot launches")
		while bridge.phase=="flight":bridge.step()
		check(bridge.shot_bridge==spec[3] and bridge.boards[0].blocks[2].out and bridge.shot_toppled>=7,
			"The chosen pier drops the bridge and its load")
		check(bridge.off(1)==0,"The rival's bridge stays untouched")
		var resumed=D.new()
		check(resumed.restore(clone(bridge.snapshot())) and resumed.shot_bridge==spec[3],
			"Bridge collapse report survives resume")
		var old=clone(bridge.snapshot())
		old.erase("shot_bridge")
		for piece in old.shapes:piece.erase("bridge_role")
		check(resumed.restore(old) and resumed.shot_bridge==0,"Earlier bridge snapshots still load")
	# The glass stop releases a real dynamic body into a separate island.
	for seed in [5920,17]:
		var junction=course(4,seed)
		check(junction.launch(8,58),"Junction shot launches")
		while junction.phase=="flight" and not junction.shot_junction:junction.step()
		check(junction.phase=="flight" and junction.shot_junction,"A cannon shot releases the marked counterweight")
		var restored=D.new()
		check(restored.restore(clone(junction.snapshot())) and restored.shot_junction,"A moving counterweight survives resume")
		while junction.phase=="flight":junction.step()
		while restored.phase=="flight":restored.step()
		var down:=0
		for i in range(8,16):
			if junction.boards[0].blocks[i].out:down+=1
		check(down>=4 and junction.off(1)==0,"The counterweight knocks down the receiving tower on the other island")
		check(junction.boards==restored.boards and junction.totals==restored.totals,"The resumed collapse scores identically")
	# Either end of the join can trigger both fuses and damage both towers.
	for angle in [-5,5]:
		var twin=course(5)
		check(twin.launch(angle,65),"Twin fuse shot launches")
		while twin.phase=="flight" and twin.shot_double==0:twin.step()
		check(twin.shot_double==2 and twin.boards[0].blocks[23].out and twin.boards[0].blocks[24].out,
			"Both fuses fire once in the same shot")
		var resumed=D.new()
		check(resumed.restore(clone(twin.snapshot())) and resumed.shot_double==2,"Twin fuse chain survives mid-collapse resume")
		while twin.phase=="flight":twin.step()
		while resumed.phase=="flight":resumed.step()
		var tower_hits := [0,0]
		for i in range(16):
			if twin.boards[0].blocks[i].out:tower_hits[i/8]+=1
		check(tower_hits[0]>=4 and tower_hits[1]>=4 and twin.off(1)==0,"The chain damages both towers on only the active side")
		check(twin.boards==resumed.boards and twin.totals==resumed.totals,"Resuming preserves twin fuse scoring")
	# Actual cannon shots remove the glass bearing points and drop the stone press.
	for seed in [5920,17]:
		var stone=course(6,seed)
		check(stone.launch(-4,58),"Stone press shot launches")
		while stone.phase=="flight":stone.step()
		var stones_down:=0
		for i in range(11,20):
			if stone.boards[0].blocks[i].out:stones_down+=1
		check(stone.stone_press_dropped() and stones_down>=6 and stone.off(1)==0,
			"Breaking glass drops the press and scores a heavy collapse on one side")
		var reopened=D.new()
		check(reopened.restore(clone(stone.snapshot())) and reopened.stone_press_dropped(),
			"The stone press report survives save and resume")
	# Each end brace gives a distinct shot into the same loaded beam.
	for spec in [[-6,7,9],[6,9,7]]:
		var balance=course(7)
		check(balance.launch(spec[0],58),"Balance shot launches")
		while balance.phase=="flight":balance.step()
		check(balance.balance_tipped() and balance.boards[0].blocks[spec[1]].out
			and not balance.boards[0].blocks[spec[2]].out and balance.off(1)==0,
			"Each shot breaks a different end brace and tips only the active tower")
		var copy=D.new()
		check(copy.restore(clone(balance.snapshot())) and copy.balance_tipped(),"Tipped-beam report survives resume")
	# The high vault falls into the lower courtyard through the real solver.
	for seed in [5920,17]:
		var lantern=course(8,seed)
		check(lantern.launch(-12,65),"Lantern vault shot launches")
		while lantern.phase=="flight":lantern.step()
		var court:=0
		for i in range(24,32):
			if lantern.boards[0].blocks[i].out:court+=1
		check(lantern.lantern_vault_fell() and court>=5 and lantern.off(1)==0,
			"The vault falls and clears lower courtyard blocks on the active side")
		var copy=D.new()
		check(copy.restore(clone(lantern.snapshot())) and copy.lantern_vault_fell(),
			"Lantern vault report survives resume")
	# Two terrace fuses can cascade into the lowest pavilion with one bonus panda.
	for seed in [5920,17]:
		var temple=course(9,seed)
		check(temple.launch(-5,58),"Temple cascade shot launches")
		while temple.phase=="flight" and temple.shot_double<2:temple.step()
		check(temple.shot_double==2 and temple.boards[0].limit==4 and temple.boards[1].limit==3,
			"The terrace chain grants exactly one bonus panda to the active side")
		var resumed=D.new()
		check(resumed.restore(clone(temple.snapshot())) and resumed.shot_double==2,"Temple cascade survives resume")
		while temple.phase=="flight":temple.step()
		while resumed.phase=="flight":resumed.step()
		var low:=0
		for i in [0,1,2,3,4,5,6,28,29,30]:
			if temple.boards[0].blocks[i].out:low+=1
		check(low>=6 and temple.off(1)==0,"The cascade reaches the lowest terrace without changing the rival board")
		check(temple.boards==resumed.boards and temple.totals==resumed.totals,"Resumed cascade preserves scoring")
	var straw=course(10)
	check(straw.launch(4,58),"Glass spindle shot launches")
	while straw.phase=="flight":straw.step()
	check(straw.last_straw_fell() and straw.shot_toppled>=15 and straw.off(1)==0,
		"The marked glass spindle breaks and the loaded gatehouse drops")
	var straw_copy=D.new()
	check(straw_copy.restore(clone(straw.snapshot())) and straw_copy.last_straw_fell(),
		"The gatehouse collapse report survives resume")
	var finale=course(11)
	check(finale.launch(0,58),"Finale shot launches into the temple core")
	while finale.phase=="flight":finale.step()
	check(finale.shot_double==2 and finale.boards[0].blocks[20].out and finale.shot_toppled>=20,
		"The central charges bring down the crown in a real shot")
	check(finale.boards[0].limit==4 and finale.off(1)==0,"The finale awards one bonus panda on the active side")
	var finale_copy=D.new()
	check(finale_copy.restore(clone(finale.snapshot())) and finale_copy.shot_double==2,
		"The finale collapse report survives resume")
	# Independent islands do not wake when another island is struck.
	var m=course(4);m.launch(0,60);var first:int=m.platforms.size()+1
	m.world.wake(first)
	check(m.world.awake_mask[first] and not m.world.awake_mask[first+8],"separate island stays asleep")
	var untouched:Dictionary=m.boards[0].blocks[8].duplicate(true)
	m.world.bodies[m.platforms.size()].enabled=false
	for tick in range(100):
		if m.phase=="flight":m.step()
	check(m.boards[0].blocks[8]==untouched,"another platform does not collapse from unrelated hit")
	# Clearing targets must not end a shot while scored debris is still visible.
	for stage in [0,5,11]:
		m=course(stage);m.launch(0,60);m.world.bodies[m.platforms.size()].enabled=false
		for i in range(m.shapes.size()):m.award(i)
		m.step();check(m.phase=="flight","all-target clearance still drains debris")
		var copy=D.new();check(copy.restore(clone(m.snapshot())),"mid-fall scored debris restores")
		var steps:=0
		while m.phase=="flight" and steps<1700:m.step();steps+=1
		while copy.phase=="flight" and steps<3400:copy.step();steps+=1
		check(m.phase=="report" and copy.phase=="report","debris drain terminates")
		check(m.boards==copy.boards and m.totals==copy.totals,"debris continuation is deterministic and scores once")
		for b in m.boards[0].blocks:check(not b.out or b.y>=1240,"no scored block freezes above screen exit")
	# Even the safety deadline cannot strand a visibly falling awarded block.
	m=course(0);m.launch(0,60);m.world.bodies[m.platforms.size()].enabled=false;m.award(0)
	var debris=m.world.bodies[m.platforms.size()+1];debris.p.y=350;debris.v=Vector2(0,-100);m.ticks=2398
	m.step();check(m.phase=="flight","deadline waits for visible debris")
	var n:=0
	while m.phase=="flight" and n<1500:m.step();n+=1
	check(m.phase=="report" and m.boards[0].blocks[0].y>=1240,"late debris leaves screen before next aim")
	print("PANDA_COURSE_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
