extends SceneTree
const Model=preload("res://cat_dog/model.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String) -> void:
	checks+=1
	if not ok:failures+=1;push_error("FAIL: "+label)
func _initialize() -> void:call_deferred("run")
func play(columns:Array):
	var m=Model.new()
	for col in columns:check(m.apply(m.turn,"F"+str(col)),"legal alternating drop")
	return m
func run() -> void:
	var vertical=play([0,1,0,1,0,1,0]);check(vertical.finished and vertical.winner==0,"vertical win ends match")
	check(Model.line_for(vertical.board)==[14,21,28,35],"highlight follows the four winning cells")
	check(not vertical.apply(1,"F2"),"no move after winning drop")
	var horizontal=play([0,6,1,6,2,5,3]);check(horizontal.finished and horizontal.winner==0,"horizontal win")
	for line in [[14,22,30,38],[17,23,29,35]]:
		var b:=[];b.resize(42);b.fill(0)
		for n in line:b[n]=2
		check(Model.line_for(b).size()==4,"diagonal winning geometry")
	var full=play([0,0,0,0,0,0]);check(full.row_for(0)==-1 and not full.apply(0,"F0"),"full column cannot consume a turn")
	var previous:Array=full.board.duplicate();var choice:int=full.choose_ai();check(choice in [1,2,3,4,5,6] and full.board==previous,"AI chooses a legal column without mutating public state")
	check(full.search_nodes<=4096,"AI search has a fixed node ceiling")
	var win=play([0,3,1,3,0,3,1]);check(win.turn==1 and win.choose_ai()==3,"AI takes its immediate winning move")
	var block=play([0,3,0,3,0]);check(block.turn==1 and block.choose_ai()==0,"AI blocks an immediate loss")
	var loaded=Model.new();check(loaded.load_base(block.board) and loaded.turn==1 and loaded.best_chain(0)==3,"previous solo save adopts a pending AI turn")
	check(loaded.tape.is_empty() and loaded.base==block.board,"legacy prefix is retained separately from new journal moves")
	var decoded:Variant=JSON.parse_string(JSON.stringify(block.board))
	var json_copy=Model.new();check(json_copy.load_base(decoded) and json_copy.board.count(0)==37 and json_copy.apply(1,"F0") and not json_copy.finished,"JSON float numbers normalize before journal replay")
	var before:Array=loaded.board.duplicate();check(loaded.apply(1,"F0"),"one AI move after legacy adoption")
	var copy=Model.new();check(copy.load_base(before) and copy.apply(1,"F0") and copy.board==loaded.board,"legacy prefix and move replay do not duplicate a piece")
	var invalid:Array=block.board.duplicate();invalid[0]=1;check(not Model.new().load_base(invalid),"floating piece is rejected")
	invalid=block.board.duplicate();invalid[35]=1.5;check(not Model.new().load_base(invalid),"fractional piece is rejected")
	invalid=block.board.duplicate();invalid[35]=NAN;check(not Model.new().load_base(invalid),"nonfinite piece is rejected")
	invalid=block.board.duplicate();invalid[0]=2;check(not Model.new().load_base(invalid),"bad gravity cannot be loaded")
	var draw:Array=[]
	for row in range(6):draw.append_array([1,1,2,2,1,1,2] if row%2==0 else [2,2,1,1,2,2,1])
	var drawn=Model.new();check(drawn.load_base(draw) and drawn.finished and drawn.winner==-1 and Model.line_for(draw).is_empty(),"full board without four is a draw")
	check(drawn.choose_ai()==-1,"no AI move after draw")
	var end=Model.new();check(end.load_base(vertical.board) and end.finished and end.winner==0,"finished legacy save remains finished")
	for game in range(4):
		var m=Model.new();var turns:=0
		while not m.finished and turns<42:
			var original:Array=m.board.duplicate();var col:int=m.choose_ai()
			check(m.board==original and col>=0 and m.row_for(col)>=0,"bounded search preserves its input")
			check(m.apply(m.turn,"F"+str(col)),"AI self-play uses legal turn journal")
			turns+=1
		check(m.finished,"AI self-play terminates within42 drops")
	print("CATS_DOGS_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
