extends RefCounted
const Rules=preload("res://rules.gd")
var hands:Array=[[],[]]
var deck:Array=[]
var turn:=0
var phase:="player"
var finished:=false
var hand_over:=false
var winner:=-1
var stood:Array=[false,false]
var round:=1
var wins:Array=[0,0]
var legacy_rules:=false
func start() -> void:
	deck=range(52);deck.shuffle();hands=[[],[]];stood=[false,false];phase="player";turn=0;finished=false;hand_over=false;winner=-1;legacy_rules=false
	for i in range(2):
		hands[0].append(deck.pop_back());hands[1].append(deck.pop_back())
	if natural(hands[0]) or natural(hands[1]):settle()
static func natural(cards:Array) -> bool:return cards.size()==2 and Rules.blackjack_total(cards)==21
static func soft(cards:Array) -> bool:
	var raw:=0
	for card in cards:raw+=1 if int(card)%13==0 else mini(int(card)%13+1,10)
	return Rules.blackjack_total(cards)>raw
func comparison() -> int:
	var a:=Rules.blackjack_total(hands[0]);var b:=Rules.blackjack_total(hands[1])
	if a>21:return 1
	if b>21:return 0
	if not legacy_rules and natural(hands[0])!=natural(hands[1]):return 0 if natural(hands[0]) else 1
	return 0 if a>b else 1 if b>a else -1
func settle() -> void:
	winner=comparison();phase="finished";finished=true;hand_over=true;turn=0;stood=[true,true]
func hit() -> bool:
	if phase!="player" or finished or deck.is_empty() or Rules.blackjack_total(hands[0])>=21:return false
	hands[0].append(deck.pop_back())
	var total:=Rules.blackjack_total(hands[0])
	if total>21:settle()
	elif total==21:stand()
	return true
func stand() -> bool:
	if phase!="player" or finished:return false
	phase="dealer";turn=1;stood[0]=true;return true
func dealer_step() -> bool:
	if phase!="dealer" or finished:return false
	if Rules.blackjack_total(hands[1])<17:
		if deck.is_empty():return false
		hands[1].append(deck.pop_back())
	if Rules.blackjack_total(hands[1])>=17:settle()
	return true
func snapshot() -> Dictionary:return {"schema":1,"hands":hands,"deck":deck,"phase":phase,"winner":winner,"legacy_rules":legacy_rules}
func restore(value:Dictionary) -> bool:
	var old:=value.has("player_cards")
	var next_hands:Variant=[value.get("player_cards"),value.get("dealer_cards")] if old else value.get("hands")
	var next_deck:Variant=value.get("deck")
	if not next_hands is Array or next_hands.size()!=2 or not next_deck is Array:return false
	var used:=[];var normalized:Array=[[],[]];var clean_deck:=[]
	for i in range(3):
		var cards:Variant=next_deck if i==2 else next_hands[i]
		if not cards is Array or cards.size()>52 or (i<2 and cards.size()<2):return false
		for card in cards:
			if not(card is int or card is float) or not is_finite(float(card)) or float(card)!=int(card) or int(card)<0 or int(card)>51 or int(card) in used:return false
			used.append(int(card))
			if i==2:clean_deck.append(int(card))
			else:normalized[i].append(int(card))
	if used.size()!=52:return false
	var next_phase:String="finished" if old and value.get("finished")==true else "player" if old else str(value.get("phase"))
	if next_phase not in ["player","dealer","finished"]:return false
	if old and not value.get("finished") is bool:return false
	if not old and (value.get("schema")!=1 or not value.get("legacy_rules") is bool):return false
	var a:=Rules.blackjack_total(normalized[0]);var b:=Rules.blackjack_total(normalized[1])
	if next_phase=="player" and (a>=21 or b>21):return false
	if next_phase=="dealer" and (a>21 or b>21):return false
	var candidate=new();candidate.hands=normalized;candidate.deck=clean_deck;candidate.legacy_rules=old or value.legacy_rules==true
	if next_phase=="finished":
		candidate.settle()
		if not old and value.get("winner")!=candidate.winner:return false
	else:
		if not old and value.get("winner")!=-1:return false
		candidate.phase=next_phase;candidate.turn=1 if next_phase=="dealer" else 0;candidate.stood=[next_phase=="dealer",false]
	hands=candidate.hands;deck=candidate.deck;phase=candidate.phase;turn=candidate.turn;stood=candidate.stood;finished=candidate.finished;hand_over=candidate.hand_over;winner=candidate.winner;legacy_rules=candidate.legacy_rules
	return true
