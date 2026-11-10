extends RefCounted
## Private 49-bit Connect Four search; never changes the live board or journal.
const ORDER:=[3,2,4,1,5,0,6]
const BOTTOM:=0x40810204081
const BOARD:=BOTTOM*63
const CENTER:=63<<21
const MAX_NODES:=4096
const WIN:=100000
var nodes:=0
var completed_depth:=0
var aborted:=false
static func bits_for(values:Array,actor:int)->int:
	var bits:=0
	for row in range(6):
		for col in range(7):
			if int(values[row*7+col])==actor+1:bits|=1<<(col*7+5-row)
	return bits
static func threats(p:int)->int:
	var result:=(p<<1)&(p<<2)&(p<<3)
	for d in [7,6,8]:
		var pair:int=(p<<d)&(p<<(2*d))
		result|=pair&(p<<(3*d));result|=pair&(p>>d)
		pair=(p>>d)&(p>>(2*d))
		result|=pair&(p<<d);result|=pair&(p>>(3*d))
	return result&BOARD
static func available(p:int,o:int)->int:return ((p|o)+BOTTOM)&BOARD
static func count(p:int)->int:
	var n:=0
	while p:p&=p-1;n+=1
	return n
static func column(move:int)->int:
	for col in ORDER:
		if move&(63<<(col*7)):return col
	return -1
static func immediate(values:Array,actor:int)->int:
	var p:=bits_for(values,actor);var o:=bits_for(values,1-actor)
	return column(threats(p)&available(p,o))
static func evaluate(p:int,o:int,moves:int)->int:
	var own:=threats(p)&~(p|o);var other:=threats(o)&~(p|o)
	return 8*(count(p&CENTER)-count(o&CENTER))+5*(count(own)-count(other))+70*(count(own&moves)-count(other&moves))
func search(p:int,o:int,depth:int,alpha_value:int,beta:int)->int:
	if nodes>=MAX_NODES:aborted=true;return 0
	nodes+=1
	var moves:=available(p,o)
	if moves==0:return 0
	if threats(p)&moves:return WIN+depth
	var forced:=threats(o)&moves
	if count(forced)>1:return -WIN-depth
	if depth==0:return evaluate(p,o,moves)
	if forced:moves=forced
	var best:=-WIN*2;var alpha:=alpha_value
	for col in ORDER:
		var bit:int=moves&(63<<(col*7))
		if bit==0:continue
		var score:int=-search(o,p|bit,depth-1,-beta,-alpha)
		if aborted:return 0
		best=maxi(best,score);alpha=maxi(alpha,score)
		if alpha>=beta:break
	return best
func choose(values:Array,actor:int,depth_limit:int=7)->int:
	nodes=0;completed_depth=0;aborted=false
	if values.size()!=42 or actor not in [0,1]:return -1
	var p:=bits_for(values,actor);var o:=bits_for(values,1-actor);var moves:=available(p,o)
	var win:=threats(p)&moves
	if win:return column(win)
	var block:=threats(o)&moves
	if block:return column(block)
	var safe:=0
	for col in ORDER:
		var bit:int=moves&(63<<(col*7))
		if bit and not(threats(o)&available(p|bit,o)):safe|=bit
	if safe:moves=safe
	var best:=column(moves)
	for depth in range(1,depth_limit+1):
		var candidate:=best;var value:=-WIN*2;var alpha:=-WIN*2
		for col in ORDER:
			var bit:int=moves&(63<<(col*7))
			if bit==0:continue
			var score:int=-search(o,p|bit,depth-1,-WIN*2,-alpha)
			if aborted:break
			if score>value:value=score;candidate=col
			alpha=maxi(alpha,score)
		if aborted:break
		best=candidate;completed_depth=depth
	# Mandatory tactical moves never depend on search depth, wall-clock speed or budget.
	win=threats(p)&available(p,o);block=threats(o)&available(p,o)
	return column(win) if win else column(block) if block else best
