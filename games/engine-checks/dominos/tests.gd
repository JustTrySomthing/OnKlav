extends SceneTree
const Tournament=preload("res://dominos/tournament.gd")
const M=preload("res://dominos/model.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL "+label)
func clone(d):return JSON.parse_string(JSON.stringify(d))
func _init():call_deferred("run")
func run():
	var sum:=0;var unique:Array=[]
	for id in range(28):
		var p:=M.faces(id);check(p.x>=0 and p.x<=p.y and p.y<=6,"double-six face");unique.append(str(p));sum+=p.x+p.y
	check(sum==168,"full set pip total");check(unique.size()==28,"full set size")
	for id in range(28):
		for end in range(2):
			for value in range(7):
				var m=M.new();m.chain=[[0,value,value,1]];m.hands=[[id],[27]];m.required=-1;m.turn=0
				var pair:=M.faces(id);var possible:bool=pair.x==value or pair.y==value
				check(m.legal(id,end)==possible,"end legality across every face")
				if possible:
					check(m.apply(0,"play",id,end),"matching end accepts tile")
					check(m.chain[0][2]==m.chain[1][1],"automatic orientation connects equal pips")
	for seed_id in range(1,65):
		var m=M.new();m.begin(seed_id);check(m.hands[0].size()==7 and m.hands[1].size()==7 and m.stock.size()==14,"seven-tile deal")
		var copy=M.new();check(copy.restore(clone(m.snapshot())),"JSON opening restore")
		check(m.snapshot()==copy.snapshot(),"normalized opening exact")
		check(not m.apply(1-m.turn,"play",m.required,0),"opponent cannot move")
		check(not m.apply(m.turn,"draw"),"cannot draw past a legal opening")
		check(not m.apply(m.turn,"pass"),"cannot pass past a legal opening")
		var opening:int=m.required;check(m.apply(m.turn,"play",opening,0),"highest opening accepted")
		var steps:=0
		while m.phase=="playing" and steps<90:
			var actor:int=m.turn;var moves:Array=m.moves(actor)
			if not moves.is_empty():
				var chosen:Array=M.pick(m.hands[actor],m.chain,m.required);check(chosen in moves,"AI chooses a legal public-information move");check(m.apply(actor,"play",chosen[0],chosen[1]),"play progresses")
			else:check(m.apply(actor,"draw" if not m.stock.is_empty() else "pass"),"draw or pass progresses")
			if steps%7==0:
				copy=M.new();check(copy.restore(clone(m.snapshot())),"midround or result restore");check(copy.snapshot()==m.snapshot(),"exact model adoption")
			steps+=1
		check(steps<90 and m.phase!="playing","round terminates within a finite tile budget")
		check(m.winner in [-1,0,1] and m.award>=0,"real round result")
		var saved:Dictionary=clone(m.snapshot());var bad:Dictionary=saved.duplicate(true);bad.seed=1.5;check(not M.new().restore(bad),"fractional seed rejected")
		bad=saved.duplicate(true);bad.chain[0][1]=9;check(not M.new().restore(bad),"invalid pip rejected")
		bad=saved.duplicate(true);bad.stock.append(bad.chain[0][0]);check(not M.new().restore(bad),"duplicate tile rejected")
		check(not m.apply(m.turn,"draw"),"finished round rejects new action")
		check(m.next_round() and m.round_id==2,"next round retains match progress")
	for seed_id in range(1,13):
		var m=M.new();m.begin(seed_id*39);var moves:=0
		while m.phase!="match_over" and moves<4000:
			if m.phase=="round_over":m.next_round()
			else:
				var chosen:Array=M.pick(m.hands[m.turn],m.chain,m.required)
				if chosen.is_empty():m.apply(m.turn,"draw" if not m.stock.is_empty() else "pass")
				else:m.apply(m.turn,"play",chosen[0],chosen[1])
			moves+=1
		check(m.phase=="match_over" and m.scores[m.winner]>=100,"complete race to100")
		check(not m.next_round(),"match cannot advance beyond the winner")
		var copy=M.new();check(copy.restore(clone(m.snapshot())),"match winner survives reload")
	test_tournament()
	print("NEON_DOMINOS_RESULT checks=%d failures=%d"%[checks,failures]);quit(0 if failures==0 else 1)

func test_tournament():
	var m := Tournament.new()
	m.begin(120)
	var totals := [0,0]
	for round_number in range(1,4):
		var legacy := {"tournament_schema":2,"wins":[0,0],"hand":m.snapshot().hand}
		var adopted := Tournament.new()
		check(adopted.restore(clone(legacy)) and adopted.round_id==1 and adopted.hands==m.hands,
			"old win-based checkpoints retain the current deal")
		while m.phase=="playing":
			var actor := m.turn
			var moves := m.moves(actor)
			if moves.is_empty(): m.apply(actor,"draw" if not m.stock.is_empty() else "pass")
			else: m.apply(actor,"play",moves[0][0],moves[0][1])
		var gross := Tournament.played_pips(m.chain)
		for side in range(2):
			var net := int(gross[side])-M.pips(m.hands[side])
			check(m.pip_scores[side]==net,"remaining pips reduce the round score")
			totals[side] += net
		check(m.round_winner==Tournament.pip_winner(m.pip_scores),"round winner uses net scores")
		check(m.match_totals()==totals,"match total sums settled rounds once")
		var copy := Tournament.new()
		check(copy.restore(clone(m.snapshot())) and copy.match_totals()==totals and copy.phase==m.phase,
			"completed round restores without losing or doubling its points")
		check(m.next_round()==(round_number<3),"exactly three rounds per match")
	check(Tournament.pip_winner([40,40])==-1,"equal match totals draw")
