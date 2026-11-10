extends SceneTree
const Solo=preload("res://blackjack/solo.gd")
const Peer=preload("res://friend_table_model.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String) -> void:
	checks+=1
	if not ok:failures+=1;push_error("FAIL: "+label)
func state(a:Array,b:Array,phase:String="player",winner:int=-1,next:int=-1,legacy:bool=false) -> Dictionary:
	var deck:=[]
	for n in range(52):
		if n not in a and n not in b and n!=next:deck.append(n)
	if next>=0:deck.append(next)
	return {"schema":1,"hands":[a,b],"deck":deck,"phase":phase,"winner":winner,"legacy_rules":legacy}
func _initialize() -> void:call_deferred("run")
func run() -> void:
	for i in range(50):
		var m=Solo.new();m.start();var all:Array=m.deck.duplicate();all.append_array(m.hands[0]);all.append_array(m.hands[1]);all.sort()
		check(all==range(52) and m.hands[0].size()==2 and m.hands[1].size()==2,"one complete deck, four distinct dealt cards")
		var copy=Solo.new();check(copy.restore(JSON.parse_string(JSON.stringify(m.snapshot()))) and copy.hands==m.hands and copy.deck==m.deck,"JSON snapshot preserves normalized deck and hands")
	var m=Solo.new();check(m.restore(state([8,9],[3,5],"player",-1,4)),"load a controlled live hand")
	check(m.hit() and m.finished and m.winner==1 and Solo.Rules.blackjack_total(m.hands[0])==24,"bust ends player turn immediately")
	check(not m.hit() and not m.stand() and not m.dealer_step(),"no actions after settled hand")
	check(Solo.soft([0,5]) and Solo.Rules.blackjack_total([0,5])==17,"soft17")
	check(not Solo.soft([0,13,9]) and Solo.Rules.blackjack_total([0,13,9])==12,"multiple aces lower to avoid bust")
	m=Solo.new();check(m.restore(state([8,9],[13,5],"dealer")),"load a dealer turn")
	var count:int=m.deck.size();check(m.dealer_step() and m.deck.size()==count and m.finished and m.winner==0,"dealer stands on soft17")
	m=Solo.new();m.restore(state([8,9],[9+13,4],"dealer",-1,17));check(m.dealer_step() and m.finished and m.winner==1,"dealer takes one card to reach20 against19")
	# A player21 from three cards is not a natural; the dealer still finishes.
	m=Solo.new();m.restore(state([0,5],[2,7],"player",-1,3));check(m.hit() and m.phase=="dealer" and not m.finished and not Solo.natural(m.hands[0]),"three-card21 automatically stands without a natural bonus")
	m=Solo.new();check(m.restore(state([0,9],[14,8,22],"finished",0)),"natural21 beats a later21")
	check(Solo.natural(m.hands[0]) and m.comparison()==0,"natural detection is exact")
	m=Solo.new();check(m.restore(state([0,9],[14,8,22],"finished",-1,-1,true)),"previous comparison-only result remains a push")
	m=Solo.new();check(m.restore(state([0,9],[13,22],"finished",-1)),"two naturals push")
	var invalid:Dictionary=state([8,9],[3,5]);invalid.deck[0]=8;check(not Solo.new().restore(invalid),"duplicate card rejected")
	invalid=state([8,9],[3,5]);invalid.hands[0][0]=1.5;check(not Solo.new().restore(invalid),"fractional card rejected")
	invalid=state([8,9],[3,5]);invalid.deck.pop_back();check(not Solo.new().restore(invalid),"truncated deck rejected")
	invalid=state([8,9],[3,5]);invalid.hands[0][0]=NAN;check(not Solo.new().restore(invalid),"nonfinite card rejected")
	invalid=state([0,9],[13,22],"finished",0);check(not Solo.new().restore(invalid),"contradictory natural winner rejected")
	var old:Dictionary=state([8,9],[3,5]);var legacy:={"player_cards":old.hands[0],"dealer_cards":old.hands[1],"deck":old.deck,"finished":false}
	m=Solo.new();check(m.restore(JSON.parse_string(JSON.stringify(legacy))) and m.phase=="player" and m.legacy_rules,"old solo save is adopted without redealing")
	var peer=Peer.new("blackjack",456)
	while not peer.finished:
		while not peer.hand_over:check(peer.apply(peer.turn,"S"),"friend stand uses existing alternating protocol")
		if not peer.finished:check(peer.apply(0,"N"),"only host deals next hand")
	check(peer.round==10 and peer.wins[0]+peer.wins[1]<=10,"ten-hand friend series settles with real hand wins")
	var copy=Peer.new("blackjack",456)
	for move in JSON.parse_string(JSON.stringify(peer.tape)):check(copy.apply(int(move.actor),move.data),"JSON friend journal replays exact plays")
	check(copy.finished and copy.hands==peer.hands and copy.wins==peer.wins and copy.winner==peer.winner,"saved friend result is stable")
	print("BLACKJACK_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
