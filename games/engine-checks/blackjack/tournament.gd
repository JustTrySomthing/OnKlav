extends "res://blackjack/solo.gd"
var results:Array=[]
var last_winner:=-1
func start()->void:
	round=1;wins=[0,0];results=[];super.start()
func settle()->void:
	super.settle();last_winner=winner;results.append(winner)
	if winner>=0:wins[winner]+=1
	finished=round>=10 and wins[0]!=wins[1]
	if finished:winner=0 if wins[0]>wins[1] else 1
func next_hand()->bool:
	if not hand_over or finished or round>=1000:return false
	round+=1;super.start();return true
func snapshot()->Dictionary:
	var hand:=super.snapshot();hand.winner=last_winner if hand_over else -1
	return {"tournament_schema":1,"round":round,"results":results.duplicate(),"hand":hand}
func restore(value:Dictionary)->bool:
	var wrapped:=value.has("tournament_schema")
	if wrapped and value.tournament_schema!=1:return false
	var h:Variant=value.get("hand") if wrapped else value
	if not h is Dictionary:return false
	var probe=preload("res://blackjack/solo.gd").new()
	if not probe.restore(h):return false
	var history:Variant=value.get("results") if wrapped else [probe.winner] if probe.hand_over else []
	var number:Variant=value.get("round") if wrapped else 1
	if not history is Array or history.size()>1000 or not(number is int or number is float) or float(number)!=int(number) or number<1 or number>1000:return false
	if history.size()!=int(number)-(0 if probe.hand_over else 1):return false
	var counts:=[0,0]
	for i in range(history.size()):
		var n:Variant=history[i]
		if not(n is int or n is float) or float(n)!=int(n) or int(n)not in[-1,0,1]:return false
		if i>=10 and counts[0]!=counts[1]:return false
		if n>=0:counts[int(n)]+=1
	if probe.hand_over and int(history[-1])!=probe.winner:return false
	if not super.restore(h):return false
	results=[]
	for n in history:results.append(int(n))
	round=int(number);wins=counts;last_winner=probe.winner
	finished=hand_over and round>=10 and wins[0]!=wins[1]
	if finished:winner=0 if wins[0]>wins[1] else 1
	return true
