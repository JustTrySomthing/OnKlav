extends "res://friend_table_model.gd"
## Same F0..F6 actor journal in solo and friend modes. Rendering never makes moves.
const ORDER:=[3,2,4,1,5,0,6]
var base:Array=[]
var search_nodes:=0
var search_depth:=0
const Solver=preload("res://cat_dog/ai.gd")
func _init() -> void:
	super("four",1)
	base=board.duplicate()
func row_for(col:int) -> int:
	if col<0 or col>6:return -1
	for row in range(5,-1,-1):
		if board[row*7+col]==0:return row
	return -1
static func line_for(values:Array) -> Array:
	for row in range(6):
		for col in range(7):
			var piece:int=int(values[row*7+col])
			if piece==0:continue
			for direction in [Vector2i(1,0),Vector2i(0,1),Vector2i(1,1),Vector2i(1,-1)]:
				var end:Vector2i=Vector2i(col,row)+direction*3
				if end.x<0 or end.x>6 or end.y<0 or end.y>5:continue
				var result:=[]
				for i in range(4):
					var at:int=(row+direction.y*i)*7+col+direction.x*i
					if int(values[at])==piece:result.append(at)
				if result.size()==4:return result
	return []
func best_chain(actor:int) -> int:
	var longest:=0
	for row in range(6):
		for col in range(7):
			if int(board[row*7+col])!=actor+1:continue
			for d in [Vector2i(1,0),Vector2i(0,1),Vector2i(1,1),Vector2i(1,-1)]:
				var count:=1
				for i in range(1,4):
					var x:int=col+d.x*i;var y:int=row+d.y*i
					if x<0 or x>6 or y<0 or y>5 or int(board[y*7+x])!=actor+1:break
					count+=1
				longest=maxi(longest,count)
	return longest
# Used only to adopt the previous solo save; friend boards always replay their tape.
func load_base(value:Variant) -> bool:
	if not value is Array or value.size()!=42:return false
	var counts:=[0,0]
	for col in range(7):
		var empty:=false
		for row in range(5,-1,-1):
			var n:Variant=value[row*7+col]
			if not(n is int or n is float) or not is_finite(float(n)) or float(n)!=int(n) or int(n) not in [0,1,2]:return false
			if int(n)==0:empty=true
			elif empty:return false
			else:counts[int(n)-1]+=1
	if counts[0]<counts[1] or counts[0]>counts[1]+1:return false
	var wins:=[false,false]
	for row in range(6):
		for col in range(7):
			var who:int=Rules.four_winner(value,col,row)
			if who>0:wins[who-1]=true
	if wins[0] and wins[1]:return false
	if (wins[0] and counts[0]!=counts[1]+1) or (wins[1] and counts[0]!=counts[1]):return false
	if wins.has(true):
		var last_actor:int=0 if wins[0] else 1
		var reachable:=false
		for col in range(7):
			for row in range(6):
				var at:int=row*7+col
				if int(value[at])==0:continue
				if int(value[at])==last_actor+1:
					var prior:Array=value.duplicate();prior[at]=0
					if line_for(prior).is_empty():reachable=true
				break
		if not reachable:return false
	board=[]
	for n in value:board.append(int(n))
	base=board.duplicate();tape=[];turn=0 if counts[0]==counts[1] else 1
	finished=wins.has(true) or counts[0]+counts[1]==42
	winner=0 if wins[0] else 1 if wins[1] else -1
	return true
func immediate(actor:int)->int:return Solver.immediate(board,actor)
func choose_ai(depth_limit:int=7)->int:
	if finished:return -1
	var solver=Solver.new();var result:=solver.choose(board.duplicate(),turn,depth_limit)
	search_nodes=solver.nodes;search_depth=solver.completed_depth
	return result
