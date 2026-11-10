extends RefCounted
## Neon Draw: double-six, seven tiles per player, first to100.
const TARGET:=100
var hands:Array=[[],[]]
var stock:Array=[]
var chain:Array=[]
var scores:Array=[0,0]
var round_id:=1
var turn:=0
var required:=-1
var passes:=0
var phase:="playing"
var winner:=-2
var award:=0
var blocked:=false
var seed_value:=1
var event:Dictionary={}
static func faces(id:int)->Vector2i:
	var index:=0
	for a in range(7):
		for b in range(a,7):
			if index==id:return Vector2i(a,b)
			index+=1
	return Vector2i(-1,-1)
static func pips(hand:Array)->int:
	var total:=0
	for id in hand:
		var pair:=faces(int(id));total+=pair.x+pair.y
	return total
func begin(seed_id:int):
	scores=[0,0];round_id=1;deal(seed_id)
func deal(seed_id:int):
	seed_value=clampi(seed_id,1,2147483646)
	var rng:=RandomNumberGenerator.new();rng.seed=seed_value
	stock=range(28)
	for i in range(27,0,-1):
		var j:=rng.randi_range(0,i);var temp:int=stock[i];stock[i]=stock[j];stock[j]=temp
	hands=[[],[]]
	for actor in range(2):
		for i in range(7):hands[actor].append(stock.pop_back())
	chain=[];passes=0;phase="playing";winner=-2;award=0;blocked=false;event={}
	var best:=-1
	for actor in range(2):
		for id in hands[actor]:
			var pair:=faces(id);var rank:int=100+pair.x if pair.x==pair.y else pair.x+pair.y
			if rank>best:best=rank;turn=actor;required=id
func legal(id:int,end:int)->bool:
	if id<0 or id>27 or end not in [0,1]:return false
	if chain.is_empty():return id==required
	var pair:=faces(id);var value:int=chain[0][1] if end==0 else chain[-1][2]
	return pair.x==value or pair.y==value
func moves(actor:int)->Array:
	var result:Array=[]
	for id in hands[actor]:
		for end in range(1 if chain.is_empty() else 2):
			if legal(id,end):result.append([id,end])
	return result
func apply(actor:int,kind:String,id:int=-1,end:int=0)->bool:
	event={}
	if phase!="playing" or actor!=turn:return false
	if kind=="play":
		if id not in hands[actor] or not legal(id,end):return false
		var pair:=faces(id);var a:=pair.x;var b:=pair.y
		if not chain.is_empty():
			var value:int=chain[0][1] if end==0 else chain[-1][2]
			if (end==0 and a==value) or (end==1 and b==value):a=pair.y;b=pair.x
		var tile:Array=[id,a,b,actor]
		if end==0:chain.push_front(tile)
		else:chain.append(tile)
		hands[actor].erase(id);required=-1;passes=0;turn=1-actor
		event={"kind":"play","actor":actor,"id":id,"end":end}
		if hands[actor].is_empty():finish(actor,pips(hands[1-actor]),false)
		return true
	if not moves(actor).is_empty():return false
	if kind=="draw" and not stock.is_empty():
		var drawn:int=stock.pop_back();hands[actor].append(drawn);event={"kind":"draw","actor":actor,"id":drawn};return true
	if kind=="pass" and stock.is_empty():
		passes+=1;turn=1-actor;event={"kind":"pass","actor":actor}
		if passes==2:
			var a:=pips(hands[0]);var b:=pips(hands[1]);finish(-1 if a==b else 0 if a<b else 1,absi(a-b),true)
		return true
	return false
func finish(actor:int,points:int,locked:bool):
	winner=actor;award=points;blocked=locked
	if actor>=0:scores[actor]+=points
	phase="match_over" if actor>=0 and scores[actor]>=TARGET else "round_over"
func next_round()->bool:
	if phase!="round_over":return false
	round_id+=1;deal(int((seed_value*1664525+1013904223)%2147483646)+1);return true
func choose_ai()->Array:
	# The agent sees only its own rack and the public chain, never the player's tiles.
	return pick(hands[1],chain,required)
static func pick(hand:Array,table:Array,opening:int)->Array:
	var best:Array=[];var best_score:=-10000
	for id in hand:
		var pair:=faces(id)
		for end in range(1 if table.is_empty() else 2):
			if table.is_empty() and id!=opening:continue
			var value:int=-1 if table.is_empty() else table[0][1] if end==0 else table[-1][2]
			if value>=0 and value!=pair.x and value!=pair.y:continue
			var exposed:int=pair.y if value==pair.x else pair.x
			var connections:=0
			for other in hand:
				if other==id:continue
				var q:=faces(other)
				if q.x==exposed or q.y==exposed:connections+=1
			var score:int=(pair.x+pair.y)*3+connections*5+(6 if pair.x==pair.y else 0)
			if score>best_score:best_score=score;best=[id,end]
	return best
func snapshot()->Dictionary:
	return {"schema":1,"hands":hands.duplicate(true),"stock":stock.duplicate(),"chain":chain.duplicate(true),"scores":scores.duplicate(),"round":round_id,"turn":turn,"required":required,"passes":passes,"phase":phase,"winner":winner,"award":award,"blocked":blocked,"seed":seed_value}
static func integer(v:Variant,low:int,high:int)->bool:
	return (v is int or v is float) and is_finite(float(v)) and float(v)==int(v) and int(v)>=low and int(v)<=high
func restore(d:Dictionary)->bool:
	if not integer(d.get("schema"),1,1):return false
	for field in ["hands","stock","chain","scores"]:
		if not d.get(field)is Array:return false
	if d.hands.size()!=2 or d.scores.size()!=2 or d.stock.size()>14 or d.chain.size()>28:return false
	for field in [["round",1,9999],["turn",0,1],["required",-1,27],["passes",0,2],["winner",-2,1],["award",0,168],["seed",1,2147483646]]:
		if not integer(d.get(field[0]),field[1],field[2]):return false
	if d.get("phase")not in ["playing","round_over","match_over"] or not d.get("blocked")is bool:return false
	for n in d.scores:
		if not integer(n,0,300):return false
	var found:Array=[];var candidate_hands:Array=[[],[]];var candidate_stock:Array=[];var candidate_chain:Array=[]
	for actor in range(2):
		if not d.hands[actor]is Array or d.hands[actor].size()>21:return false
		for v in d.hands[actor]:
			if not integer(v,0,27) or int(v) in found:return false
			found.append(int(v));candidate_hands[actor].append(int(v))
	for v in d.stock:
		if not integer(v,0,27) or int(v)in found:return false
		found.append(int(v));candidate_stock.append(int(v))
	for row in d.chain:
		if not row is Array or row.size()!=4:return false
		if not integer(row[0],0,27) or int(row[0])in found or not integer(row[1],0,6) or not integer(row[2],0,6) or not integer(row[3],0,1):return false
		var id:=int(row[0]);var pair:=faces(id);var a:=int(row[1]);var b:=int(row[2])
		if not ((a==pair.x and b==pair.y) or (a==pair.y and b==pair.x)):return false
		if not candidate_chain.is_empty() and candidate_chain[-1][2]!=a:return false
		found.append(id);candidate_chain.append([id,a,b,int(row[3])])
	if found.size()!=28:return false
	if candidate_chain.is_empty():
		if int(d.required)not in candidate_hands[int(d.turn)] or d.phase!="playing":return false
	elif int(d.required)!=-1:return false
	if d.phase=="playing" and (int(d.winner)!=-2 or candidate_hands[0].is_empty() or candidate_hands[1].is_empty() or int(d.passes)>1):return false
	if d.phase!="playing" and (int(d.winner)==-2 or (not d.blocked and int(d.winner)<0)):return false
	if (d.phase=="match_over")!=(maxf(d.scores[0],d.scores[1])>=TARGET):return false
	if d.phase!="playing":
		var won:=int(d.winner)
		if d.blocked:
			if int(d.passes)!=2 or not candidate_stock.is_empty():return false
			if not pick(candidate_hands[0],candidate_chain,-1).is_empty() or not pick(candidate_hands[1],candidate_chain,-1).is_empty():return false
			var a:=pips(candidate_hands[0]);var b:=pips(candidate_hands[1])
			if won!=(-1 if a==b else 0 if a<b else 1) or int(d.award)!=absi(a-b):return false
		elif won<0 or not candidate_hands[won].is_empty() or int(d.award)!=pips(candidate_hands[1-won]):return false
		if won>=0 and d.scores[won]<int(d.award):return false
		if d.phase=="match_over" and (won<0 or d.scores[won]<TARGET or d.scores[1-won]>=TARGET):return false
	hands=candidate_hands;stock=candidate_stock;chain=candidate_chain;scores=[int(d.scores[0]),int(d.scores[1])];round_id=int(d.round);turn=int(d.turn);required=int(d.required);passes=int(d.passes);phase=d.phase;winner=int(d.winner);award=int(d.award);blocked=d.blocked;seed_value=int(d.seed);event={};return true
