extends SceneTree
const Model=preload("res://cat_dog/model.gd")
const Board=preload("res://cat_dog/board.gd")
const AI=preload("res://cat_dog/ai.gd")
var checks:=0
var failures:=0
var dropped:Array=[]
func check(ok:bool,label:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL ",label)
func _init():call_deferred("run")
func touch(b,index:int,p:Vector2,down:bool,cancelled:bool=false):
	var e:=InputEventScreenTouch.new();e.index=index;e.position=p;e.pressed=down;e.canceled=cancelled;b._handle_pointer(e)
func run():
	for last in [5,6]:
		for remove in [31,32,37]:
			var values:=[];values.resize(42);values.fill(0)
			for col in [3,4,5,6]:
				if col!=last:values[35+col]=1
			for at in [37,31,32]:
				if at!=remove:values[at]=2
			var m=Model.new();check(m.load_base(values) and m.turn==1,"reported board before the last rival move is legal")
			var before:Array=m.board.duplicate()
			for i in range(30):
				var result:int=m.choose_ai()
				check((result==last or (last==6 and remove==37 and result==2)) and m.board==before,"forced loss is blocked where possible without mutation")
	var fork=Model.new()
	for col in [3,3,4]:fork.apply(fork.turn,"F"+str(col))
	check(fork.choose_ai() in [2,5],"prevent the two-ended fork shown in the report before it exists")
	var rng:=RandomNumberGenerator.new();rng.seed=5920
	for game in range(30):
		var m=Model.new()
		for turn in range(25):
			if m.finished:break
			for actor in [0,1]:
				var bits:=AI.bits_for(m.board,actor);var occupied:=AI.bits_for(m.board,0)|AI.bits_for(m.board,1)
				for col in range(7):
					var row:=m.row_for(col)
					if row<0:continue
					var copy:Array=m.board.duplicate();copy[row*7+col]=actor+1
					var expected:bool=Model.Rules.four_winner(copy,col,row)==actor+1
					var bit:=1<<(col*7+5-row)
					check(bool(AI.threats(bits)&AI.available(bits,occupied^bits)&bit)==expected,"bitboard tactics match independent board rules")
			var legal:=[]
			for col in range(7):
				if m.row_for(col)>=0:legal.append(col)
			m.apply(m.turn,"F"+str(legal[rng.randi_range(0,legal.size()-1)]))
	for opening in [[3,4,5,6],[5,4,6,3],[6,5,4,3],[0,1,2,3],[4,6,3,5]]:
		var m=Model.new()
		for col in opening:
			if m.finished:break
			m.apply(0,"F"+str(col))
			if m.finished:break
			var before:Array=m.board.duplicate();var reply:int=m.choose_ai()
			check(m.board==before and m.search_nodes<=AI.MAX_NODES,"bounded search cannot change live board")
			m.apply(1,"F"+str(reply))
		check(not m.finished or m.winner!=0,"first-four opening is defended")
	var b=Board.new();root.add_child(b);b.size=Vector2(400,500);b.values=[];b.values.resize(42);b.values.fill(0);b.chosen.connect(func(col):dropped.append(col))
	var p:Vector2=b.grid().get_center();b.touched_at=-1000
	var mouse:=InputEventMouseButton.new();mouse.position=p;mouse.button_index=MOUSE_BUTTON_LEFT;mouse.pressed=true;b._handle_pointer(mouse)
	check(b.finger==-2 and b.pressed,"mouse-first preview begins")
	touch(b,4,p,true);check(b.finger==4,"real finger takes over synthetic mouse")
	mouse.pressed=false;b.touched_at-=1000;b._handle_pointer(mouse)
	check(b.pressed and b.finger==4,"late synthetic release cannot cancel finger")
	var drag:=InputEventScreenDrag.new();drag.index=4;drag.position=p+Vector2(b.grid().size.x/7,0);b._handle_pointer(drag)
	touch(b,4,drag.position,false)
	check(dropped==[4] and not b.pressed and b.finger==-1 and b.hover==-1,"one release commits exactly once and clears preview")
	var motion:=InputEventMouseMotion.new();motion.position=p;b.touched_at-=1000;b._handle_pointer(motion)
	check(b.hover==-1 and not b.pressed,"idle mouse motion cannot resurrect floating preview")
	touch(b,4,p,false);check(dropped==[4],"duplicate release does not drop twice")
	for col in [0,6,2,5,1,4,3]:
		var point:Vector2=b.grid().position+Vector2((col+.5)*b.grid().size.x/7,b.grid().size.y*.5)
		touch(b,2,point,true);touch(b,2,point,false)
		check(dropped[-1]==col and b.hover==-1,"single taps work repeatedly across columns")
	var n:=dropped.size();touch(b,2,p,true);touch(b,3,p,true);touch(b,3,p,false)
	check(b.pressed and b.finger==2,"second finger cannot steal or release gesture")
	touch(b,2,p,false,true);check(not b.pressed and b.hover==-1 and dropped.size()==n,"cancel clears preview without drop")
	touch(b,2,p,true);b.locked=true;check(not b.pressed and b.hover==-1,"turn change clears held preview")
	b.locked=false;touch(b,2,p,true);touch(b,2,Vector2(-30,100),false)
	check(dropped.size()==n and b.hover==-1,"release outside board cancels cleanly")
	var grid:Rect2=b.grid()
	var above:=Vector2(p.x,grid.position.y-1)
	var below:=Vector2(p.x,grid.end.y+1)
	var beside:=Vector2(grid.position.x-1,p.y)
	for outside in [above,below,beside]:
		touch(b,2,outside,true);touch(b,2,outside,false)
		check(dropped.size()==n and b.hover==-1 and not b.pressed,"off-grid tap never selects a column")
	touch(b,2,above,true);touch(b,2,p,false)
	check(dropped.size()==n,"gesture started above the board cannot drop")
	touch(b,2,p,true);touch(b,2,above,false)
	check(dropped.size()==n and b.hover==-1,"release over center text cancels the drop")
	mouse.position=above;mouse.pressed=true;b.touched_at=-1000;b._handle_pointer(mouse)
	mouse.pressed=false;b._handle_pointer(mouse)
	check(dropped.size()==n and not b.pressed,"off-grid mouse click cannot drop")
	b.free();print("CATS_INPUT_AI_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
