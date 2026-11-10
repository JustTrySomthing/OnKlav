extends SceneTree
const Rules = preload("res://rules.gd")
var checks := 0
var failed := false
func verify(value: bool, label: String) -> void:
	checks += 1
	if not value:
		failed = true
		printerr("FAIL: "+label)
func _initialize() -> void:
	verify(Rules.blackjack_total([0,13,26])==13,"Three aces")
	verify(Rules.blackjack_total([0,9])==21,"Blackjack")
	verify(Rules.blackjack_total([9,10,1])==22,"Bust")
	verify(Rules.place_ship([],9,2,true).is_empty(),"No ship wraps rows")
	verify(Rules.place_ship([0],0,2,false).is_empty(),"No overlap")
	verify(Rules.place_ship([],80,3,false).is_empty(),"No ship outside board")
	verify(Rules.place_ship([],9,5,false).size()==5,"Vertical ship")
	for direction in [Vector2i(1,0),Vector2i(0,1),Vector2i(1,1),Vector2i(1,-1)]:
		var board := []
		board.resize(42)
		board.fill(0)
		var start := Vector2i(1,4) if direction.y<0 else Vector2i(1,1)
		for step in range(4):
			var at: Vector2i = start + direction*step
			board[at.y*7+at.x] = 2
		verify(Rules.four_winner(board,start.x,start.y)==2,"Connect four direction "+str(direction))
	var empty := []
	empty.resize(42)
	empty.fill(0)
	verify(Rules.four_winner(empty,0,0)==0,"Empty board")
	print("Godot rules: ",checks," checks; failed=",failed)
	quit(1 if failed else 0)
