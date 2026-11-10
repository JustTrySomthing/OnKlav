extends Control
const Model = preload("res://model.gd")
const Channel = preload("res://turn_channel.gd")
var model = Model.new()
var network
var label: Label
var buttons: Array[Button] = []
var me := 1
var invalid := false
var retry_button: Button
func _ready() -> void:
	network=Channel.new();add_child(network);me=1 if network.host or not network.enabled else 2
	var box := VBoxContainer.new(); box.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); box.offset_left = 24; box.offset_right = -24; box.offset_top = 16; add_child(box)
	label = Label.new(); label.add_theme_font_size_override("font_size", 26); box.add_child(label)
	var grid := GridContainer.new(); grid.columns = 3; box.add_child(grid)
	for cell in range(9):
		var item := Button.new(); item.custom_minimum_size = Vector2(0, 100); item.size_flags_horizontal = Control.SIZE_EXPAND_FILL; grid.add_child(item); buttons.append(item)
		item.pressed.connect(func():
			if model.turn!=me or invalid:return
			if network.enabled:network.send("T"+str(cell));render()
			elif model.play(cell,model.revision,me):
				if model.winner==0 and model.revision<9:
					var free:=[]
					for i in range(9):
						if model.board[i]==0:free.append(i)
					if not free.is_empty():model.play(int(free.pick_random()),model.revision,2)
				render();OnKlavGame.checkpoint())
	retry_button = Button.new()
	retry_button.text = "Retry text delivery"
	retry_button.custom_minimum_size.y = 52
	retry_button.pressed.connect(network.retry)
	box.add_child(retry_button)
	network.sent.connect(func(data):apply_turn(me,data));network.received.connect(func(data):apply_turn(3-me,data));network.changed.connect(render)
	OnKlavGame.attach_session(self, snapshot, restore, new_game, "Choose a free square; three in a row wins. Solo plays against the computer. With a friend, wait for each text update before the next turn.","user://match"+("-"+network.match_id if network.enabled else "")+".json")
	render()
func apply_turn(actor:int,data:String) -> void:
	if data.length()!=2 or not data.begins_with("T") or not data.substr(1).is_valid_int() or not model.play(int(data.substr(1)),model.revision,actor):invalid=true
	OnKlavGame.checkpoint();render()
func render() -> void:
	label.text = "Player %d wins" % model.winner if model.winner else "Draw" if model.revision == 9 else "Your turn" if model.turn==me else "Your friend’s turn" if network.enabled else "Computer’s turn"
	if invalid:label.text="Invalid update received"
	if network.enabled and not invalid:
		if not network.error.is_empty(): label.text = network.error
		elif network.pending or network.sending: label.text = "Waiting for text delivery…"
	retry_button.visible = network.enabled and network.pending and not invalid
	for i in range(9):
		buttons[i].text = "·" if model.board[i] == 0 else "X" if model.board[i] == 1 else "O"
		buttons[i].disabled = invalid or model.turn!=me or model.board[i] != 0 or model.winner != 0 or model.revision == 9 or (network.enabled and (network.pending or network.sending or network.status!="active" or not network.error.is_empty()))
func snapshot() -> Dictionary: return {"board": model.board, "turn": model.turn, "revision": model.revision, "winner": model.winner,"network":network.snapshot(),"invalid":invalid}
func restore(state: Dictionary) -> bool:
	var board: Variant = state.get("board")
	if not board is Array or board.size() != 9: return false
	for value in board:
		if not (value is int or value is float) or value != floor(value) or value < 0 or value > 2: return false
	var ones: int = board.count(1); var twos: int = board.count(2)
	if ones < twos or ones > twos + 1 or state.get("revision") != ones + twos or state.get("turn") != (1 if ones == twos else 2): return false
	var winner := 0
	for line in [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]]:
		if board[line[0]] != 0 and board[line[0]] == board[line[1]] and board[line[1]] == board[line[2]]:
			if winner != 0 and winner != board[line[0]]: return false
			winner = int(board[line[0]])
	if state.get("winner") != winner or (winner == 1 and ones != twos + 1) or (winner == 2 and ones != twos): return false
	if not state.get("invalid", false) is bool:return false
	if network.enabled and (not state.get("network")is Dictionary or not network.restore(state.network)):return false
	model.board = board.duplicate(); model.turn = int(state.turn); model.revision = int(state.revision); model.winner = winner; invalid = state.get("invalid", false); render(); return true
func new_game() -> void: model = Model.new(); invalid=false;render()
