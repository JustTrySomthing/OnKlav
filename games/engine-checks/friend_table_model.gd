extends RefCounted
const Rules = preload("res://rules.gd")
var game := "four"
var seed_value := 0
var board: Array = []
var turn := 0
var winner := -1
var finished := false
var hand_over := false
var round := 1
var wins := [0,0]
var hands: Array = [[],[]]
var deck: Array = []
var stood := [false,false]
var tape: Array = []

func _init(kind: String = "four", seed_number: int = 1) -> void:
	game = kind; seed_value = seed_number
	board.resize(42); board.fill(0)
	if game == "blackjack": deal()

func deal() -> void:
	var rng := RandomNumberGenerator.new(); rng.seed = seed_value + round * 7919
	deck = range(52)
	for i in range(51,0,-1):
		var j := rng.randi_range(0,i); var old: int = int(deck[i]);deck[i]=deck[j];deck[j]=old
	hands=[[deck.pop_back(),deck.pop_back()],[deck.pop_back(),deck.pop_back()]]
	stood=[false,false];turn=(round-1)%2;hand_over=false;winner=-1

func apply(actor: int, data: String) -> bool:
	if (finished and not(game=="blackjack" and data=="N" and actor==0)) or actor not in [0,1] or tape.size()>=4096:return false
	if game=="four":
		if actor!=turn or not data.begins_with("F") or not data.substr(1).is_valid_int():return false
		var col:=int(data.substr(1))
		if col<0 or col>6:return false
		var row:=5
		while row>=0 and board[row*7+col]!=0:row-=1
		if row<0:return false
		board[row*7+col]=actor+1
		if Rules.four_winner(board,col,row)!=0:winner=actor;finished=true
		elif not board.has(0):finished=true;winner=-1
		turn=1-turn
	elif game=="blackjack":
		if data=="N":
			if actor!=0 or not hand_over:return false
			if finished:round=1;wins=[0,0];finished=false
			else:round+=1
			deal()
		else:
			if actor!=turn or hand_over or data not in ["H","S"] or stood[actor]:return false
			if data=="H":
				if deck.is_empty():return false
				hands[actor].append(deck.pop_back())
			var total:=Rules.blackjack_total(hands[actor])
			if total>21:settle(1-actor)
			elif data=="S" or total>=21:
				stood[actor]=true;turn=1-actor
				if stood[0] and stood[1]:
					var a:=Rules.blackjack_total(hands[0]);var b:=Rules.blackjack_total(hands[1])
					settle(0 if a>b else 1 if b>a else -1)
	else:return false
	tape.append({"actor":actor,"data":data})
	return true

func settle(actor:int) -> void:
	hand_over=true;winner=actor
	if actor>=0:wins[actor]+=1
	if round>=10 and wins[0]!=wins[1]:
		finished=true;winner=0 if wins[0]>wins[1] else 1 if wins[1]>wins[0] else -1
