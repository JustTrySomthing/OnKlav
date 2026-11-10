extends SceneTree
const Chrome=preload("res://game_chrome.gd")
const Cats=preload("res://cat_dog/series.gd")
const BJ=preload("res://blackjack/tournament.gd")
const Dom=preload("res://dominos/tournament.gd")
var checks:=0
var failures:=0
func check(ok:bool,what:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL ",what)
func clone(d):return JSON.parse_string(JSON.stringify(d))
func force_hand(t,who:int):
	t.hands=[[9,8],[7,6]] if who==0 else [[7,6],[9,8]] if who==1 else [[9,8],[22,21]]
	t.deck=[]
	for c in range(52):
		if c not in t.hands[0] and c not in t.hands[1]:t.deck.append(c)
	t.finished=false;t.hand_over=false;t.phase="player";t.settle()
func _init():call_deferred("run")
func check_board_layout() -> void:
	var canvas := Control.new()
	for extent: Vector2 in [Vector2(600, 1233), Vector2(600, 1224), Vector2(600, 1000),
			Vector2(1778, 1000), Vector2(1538, 1000), Vector2(824, 1000)]:
		for header: float in [0.0, 56.0]:
			var minimum := 540.0 if header == 0.0 else 520.0
			Chrome.fit_board(canvas, extent, header, minimum)
			var usable_height := extent.y - header * extent.x / Chrome.window_width()
			var bounds := Rect2(canvas.position, canvas.size * canvas.scale)
			check(Rect2(0, 0, extent.x, usable_height).grow(.01).encloses(bounds),
				"Board and controls fit %s with header %s" % [extent, header])
			check(is_equal_approx(bounds.get_center().x, extent.x * .5)
				and canvas.size.y >= minimum - .01 and canvas.scale.x == canvas.scale.y,
				"Board stays centered with room for controls and uniform scaling")
	canvas.free()

func run():
	check_board_layout()
	var m=Cats.new()
	for win in range(3):
		for col in [0,1,0,1,0,1,0]:check(m.apply(m.turn,"F"+str(col)),"series drop")
		check(m.match_wins==[win+1,0] and m.series_over()==(win==2),"one win per board; first to three")
		if win<2:check(m.apply(0,"N") and not m.finished,"next board without new entry")
	var c=Cats.new();check(c.load_base(clone(m.base)),"series base restores")
	for move in clone(m.tape):check(c.apply(int(move.actor),move.data),"multi-round journal replays")
	check(c.match_wins==[3,0] and c.series_over(),"series wins survive journal restore")
	check(c.apply(0,"N") and c.match_wins==[0,0] and not c.finished,"friend rematch resets series through journal")
	for first in range(7):
		m=Cats.new()
		for i in range(4):
			if m.finished:break
			m.apply(0,"F"+str(first))
			if m.finished:break
			var before:Array=m.board.duplicate();var col:int=m.choose_ai()
			check(m.board==before and m.row_for(col)>=0,"AI preserves input and chooses legal reply")
			m.apply(1,"F"+str(col))
		check(not m.finished or m.winner!=0,"AI stops four identical opening drops")
	for pattern in [[3,4,5,6],[5,4,6,3],[0,1,2,3],[6,5,4,3],[2,3,4,5]]:
		m=Cats.new()
		for col in pattern:
			if m.finished:break
			m.apply(0,"F"+str(col))
			if m.finished:break
			var block:int=m.immediate(0);var reply:int=m.choose_ai()
			if block>=0:check(reply==block or m.immediate(1)==reply,"AI blocks an imminent loss or wins immediately")
			m.apply(1,"F"+str(reply))
		check(not m.finished or m.winner!=0,"AI stops reported first-four horizontal openings")
	var b=BJ.new();b.round=1;b.results=[];b.wins=[0,0]
	for i in range(10):
		b.round=i+1;force_hand(b,i%2)
	check(b.wins==[5,5] and not b.finished and b.hand_over,"ten-hand tie requires tie-break")
	var copy=BJ.new();check(copy.restore(clone(b.snapshot())) and copy.wins==[5,5] and not copy.finished,"tied tournament restores")
	check(b.next_hand() and b.round==11,"tie-break deals immediately")
	# A push in a tie-break still requires another hand.
	b.results=copy.results.duplicate();b.wins=[5,5];b.round=11;force_hand(b,-1)
	check(not b.finished and b.wins==[5,5],"push does not decide tie-break")
	b.round=12;force_hand(b,0)
	check(b.finished and b.winner==0 and b.wins==[6,5],"first decisive tie-break finishes tournament")
	copy=BJ.new();check(copy.restore(clone(b.snapshot())) and copy.finished and copy.wins==[6,5],"finished tie-break save restores")
	var bad:Dictionary=clone(b.snapshot());bad.results.append(0)
	var before:Dictionary=copy.snapshot();check(not copy.restore(bad) and copy.snapshot()==before,"bad tournament save does not mutate state")
	var d=Dom.new();d.begin(5920);var actions:=0
	var expected_pips:=[0,0]
	while d.phase!="match_over" and actions<1000:
		if d.phase=="round_over":
			check(d.next_round() and d.pip_scores==[0,0],"Dominos next round resets only the pip score")
			expected_pips=[0,0]
			continue
		var actor:int=d.turn
		var moves:Array=d.moves(actor)
		if moves.is_empty():d.apply(actor,"draw" if not d.stock.is_empty() else "pass")
		else:
			var faces:=Dom.faces(moves[0][0])
			expected_pips[actor]+=faces.x+faces.y
			d.apply(actor,"play",moves[0][0],moves[0][1])
		actions+=1
		if d.phase!="playing":
			var net := [expected_pips[0]-Dom.pips(d.hands[0]),expected_pips[1]-Dom.pips(d.hands[1])]
			check(d.pip_scores==net and d.round_winner==Dom.pip_winner(net),
				"remaining pips are deducted once and the net score decides the round")
		else:
			check(d.pip_scores==expected_pips,"each played tile contributes exactly its pips")
		var restored=Dom.new()
		check(restored.restore(clone(d.snapshot())) and restored.match_totals()==d.match_totals()
			and restored.pip_scores==d.pip_scores and restored.round_winner==d.round_winner,
			"Dominos round and match scores restore after each action")
	check(d.phase=="match_over" and d.round_id==3 and d.round_results.size()==3,
		"Dominos plays three rounds regardless of round winners")
	print("GAME_REPAIR_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
