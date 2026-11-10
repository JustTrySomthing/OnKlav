extends Control

const Rules = preload("res://rules.gd")
var game := "four"
var generation := 0
var page: VBoxContainer
var status: Label
var board := []
var cells := []
var finished := false
var pending := false
var player_cards := []
var dealer_cards := []
var deck := []
var enemy_ships := []
var own_ships := []
var shots := []
var enemy_shots := []
var scores := [0,0]
var pause_panel: PanelContainer
var restored_pending := false

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_PAUSABLE
	var args := OS.get_cmdline_user_args()
	if OS.has_feature("web"):
		var selected: Variant = JavaScriptBridge.eval("new URL(window.location.href).searchParams.get('game') || 'four'")
		args = PackedStringArray([str(selected)])
	if not args.is_empty() and args[0] in ["panda","hockey","naval","four","blackjack","dominos"]:
		game = args[0]
	if game == "dominos":
		add_child(preload("res://dominos/main.gd").new())
		return
	if game == "panda":
		add_child(preload("res://panda/main.gd").new())
		return
	if game == "blackjack":
		add_child(preload("res://blackjack/main.gd").new())
		return
	if game == "four":
		add_child(preload("res://cat_dog/main.gd").new())
		return
	if game == "naval":
		add_child(preload("res://naval_battle/main.gd").new())
		return
	if game == "hockey":
		var port = preload("res://air_hockey/main.gd").new()
		port.requested_game = game
		add_child(port)
		return
	if OS.has_feature("web") and bool(JavaScriptBridge.eval("!!window.onklavFriend")):
		var table = preload("res://friend_table.gd").new();table.game=game;add_child(table);return
	setup()
	OnKlavGame.resumed.connect(resume_pending)
	var instructions := {"four":"Choose a column to drop a piece. Connect four to win.", "naval":"Choose squares on the enemy sea to find all ships.", "blackjack":"Hit to draw a card, or Stand. Get closer to21 than the dealer without going over."}
	OnKlavGame.attach_session(self, snapshot_session, restore_session, setup, instructions[game], "user://builtin-" + game + ".json")

func text_label(value: String, size_value: int = 24) -> Label:
	var label := Label.new()
	label.text = value
	label.add_theme_font_size_override("font_size",size_value)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	return label

func action(value: String, call: Callable) -> Button:
	var button := Button.new()
	button.text = value
	button.custom_minimum_size = Vector2(0,54)
	button.pressed.connect(func():
		if is_instance_valid(OnKlavGame.chrome): OnKlavGame.chrome.audio_feedback()
		call.call())
	return button

func setup() -> void:
	generation += 1
	get_tree().paused = false
	for child in get_children():
		remove_child(child)
		child.queue_free()
	finished = false
	pending = false
	cells.clear()
	page = VBoxContainer.new()
	page.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	page.offset_left = 18
	page.offset_right = -18
	page.offset_top = 16
	page.offset_bottom = -24
	page.add_theme_constant_override("separation",12)
	add_child(page)
	status = text_label("Your Turn",24)
	page.add_child(status)
	match game:
		"four": setup_four()
		"naval": setup_naval()
		"blackjack": setup_blackjack()
	if OnKlavGame.session_ready and OnKlavGame.game_scene == self:
		OnKlavGame.wrap_content()

func announce(value: String) -> void:
	finished = true
	pending = false
	status.text = value
	OnKlavGame.checkpoint()
	for button in cells:
		button.disabled = true

func show_pause() -> void:
	OnKlavGame.pause_game()

func _notification(what: int) -> void:
	if game != "hockey" and what in [NOTIFICATION_APPLICATION_PAUSED, NOTIFICATION_APPLICATION_FOCUS_OUT] and is_inside_tree():
		OnKlavGame.pause_game()

func snapshot_session() -> Dictionary:
	return {"game":game, "board":board, "finished":finished, "pending":pending, "status":status.text, "player_cards":player_cards, "dealer_cards":dealer_cards, "deck":deck, "enemy_ships":enemy_ships, "own_ships":own_ships, "shots":shots, "enemy_shots":enemy_shots, "scores":scores}

func valid_array(value: Variant, maximum: int, high: int, unique: bool = false) -> bool:
	if not value is Array or value.size() > maximum: return false
	var seen := {}
	for n in value:
		if not (n is int or n is float) or not is_finite(float(n)) or n != floor(n) or n < 0 or n > high: return false
		if unique and seen.has(n): return false
		seen[n] = true
	return true

func restore_session(data: Dictionary) -> bool:
	if data.get("game") != game or not data.get("finished") is bool or not data.get("pending") is bool or not data.get("status") is String or data.status.length() > 200: return false
	for name in ["board", "player_cards", "dealer_cards", "deck", "enemy_ships", "own_ships", "shots", "enemy_shots", "scores"]:
		if not data.has(name): return false
	if not valid_array(data.board, 42, 2) or not valid_array(data.scores, 2, 17) or data.scores.size() != 2: return false
	for name in ["player_cards", "dealer_cards", "deck"]:
		if not valid_array(data[name], 52, 51, true): return false
	for name in ["enemy_ships", "own_ships", "shots", "enemy_shots"]:
		if not valid_array(data[name], 100, 99, true): return false
	if game == "four" and data.board.size() != 42: return false
	if game == "naval" and (data.enemy_ships.size() != 17 or data.own_ships.size() != 17): return false
	if game == "blackjack" and (data.player_cards.size() < 2 or data.dealer_cards.size() < 2 or not valid_array(data.player_cards + data.dealer_cards + data.deck, 52, 51, true) or data.player_cards.size() + data.dealer_cards.size() + data.deck.size() != 52): return false
	generation += 1
	board = data.board.duplicate(); scores = data.scores.duplicate()
	player_cards = data.player_cards.duplicate(); dealer_cards = data.dealer_cards.duplicate(); deck = data.deck.duplicate()
	enemy_ships = data.enemy_ships.duplicate(); own_ships = data.own_ships.duplicate(); shots = data.shots.duplicate(); enemy_shots = data.enemy_shots.duplicate()
	finished = data.finished; pending = data.pending; restored_pending = pending
	status.text = data.status
	if game == "four":
		for i in range(42):
			cells[i].text = "·" if board[i] == 0 else "C" if board[i] == 1 else "D"
			cells[i].modulate = Color.WHITE if board[i] == 0 else Color("c9aaff") if board[i] == 1 else Color("70d3ed")
			cells[i].disabled = finished
	elif game == "naval":
		for i in range(100):
			cells[i].text = "·" if i not in shots else "×" if i in enemy_ships else "○"
			cells[i].disabled = finished or i in shots
	else:
		render_cards(finished)
		for item in cells: item.disabled = finished
	return true

func resume_pending() -> void:
	if not restored_pending: return
	restored_pending = false
	if pending and not finished:
		if game == "four": finish_four_turn()
		elif game == "naval": finish_naval_turn()

func setup_four() -> void:
	board.resize(42)
	board.fill(0)
	var grid := GridContainer.new()
	grid.columns = 7
	for i in range(42):
		var button := action("·",func(): play_four(i%7))
		button.custom_minimum_size = Vector2(75,88)
		button.add_theme_font_size_override("font_size",40)
		grid.add_child(button)
		cells.append(button)
	page.add_child(grid)
	page.add_child(text_label("Cats: C   •   Dogs: D",18))

func drop_four(column: int, player: int) -> bool:
	for row in range(5,-1,-1):
		var at: int = row*7+column
		if board[at] == 0:
			board[at] = player
			cells[at].text = "C" if player == 1 else "D"
			cells[at].modulate = Color("c9aaff") if player == 1 else Color("70d3ed")
			if Rules.four_winner(board,column,row) != 0:
				announce("Cats Win!" if player==1 else "Dogs Win!")
			elif not board.has(0):
				announce("Draw")
			return true
	return false

func play_four(column: int) -> void:
	if finished or pending or not drop_four(column,1):
		return
	if finished:
		return
	pending = true
	status.text = "Computer Is Choosing…"
	OnKlavGame.checkpoint()
	finish_four_turn()

func finish_four_turn() -> void:
	var stamp := generation
	await get_tree().create_timer(0.7,false).timeout
	if stamp != generation or not is_inside_tree() or finished:
		return
	var choices := []
	for c in range(7):
		if board[c] == 0:
			choices.append(c)
	if not choices.is_empty():
		drop_four(choices.pick_random(),2)
	pending = false
	if not finished:
		status.text = "Your Turn"
	OnKlavGame.checkpoint()

func make_fleet() -> Array:
	var fleet := []
	for length in [5,4,3,3,2]:
		var placed := false
		for attempt in range(1000):
			var ship: Array = Rules.place_ship(fleet,randi_range(0,99),length,randi_range(0,1)==1)
			if not ship.is_empty():
				fleet.append_array(ship)
				placed = true
				break
		if not placed:
			return make_fleet()
	return fleet

func setup_naval() -> void:
	enemy_ships = make_fleet()
	own_ships = make_fleet()
	shots.clear()
	enemy_shots.clear()
	scores = [0,0]
	page.add_child(text_label("Choose a Square on the Enemy Sea",18))
	var grid := GridContainer.new()
	grid.columns = 10
	for i in range(100):
		var button := action("·",func(): fire_naval(i))
		button.custom_minimum_size = Vector2(51,51)
		grid.add_child(button)
		cells.append(button)
	page.add_child(grid)

func fire_naval(at: int) -> void:
	if finished or pending or at in shots:
		return
	shots.append(at)
	var hit: bool = at in enemy_ships
	cells[at].text = "×" if hit else "○"
	cells[at].disabled = true
	if hit:
		scores[0] += 1
	if scores[0] == 17:
		announce("You Win!")
		return
	pending = true
	status.text = "Computer Is Choosing…"
	OnKlavGame.checkpoint()
	finish_naval_turn()

func finish_naval_turn() -> void:
	var stamp := generation
	await get_tree().create_timer(0.7,false).timeout
	if stamp != generation or finished or not is_inside_tree():
		return
	var available := []
	for index in range(100):
		if index not in enemy_shots:
			available.append(index)
	var shot: int = available.pick_random()
	enemy_shots.append(shot)
	if shot in own_ships:
		scores[1] += 1
	if scores[1] == 17:
		announce("Computer Wins")
	else:
		status.text = "Your Turn • Hits %d–%d" % [scores[0],scores[1]]
	pending = false
	OnKlavGame.checkpoint()

func setup_blackjack() -> void:
	deck = range(52)
	deck.shuffle()
	player_cards = [deck.pop_back(),deck.pop_back()]
	dealer_cards = [deck.pop_back(),deck.pop_back()]
	render_cards(false)
	var hit := action("Hit",hit_card)
	var stand := action("Stand",stand_cards)
	cells = [hit,stand]
	page.add_child(hit)
	page.add_child(stand)
	if Rules.blackjack_total(player_cards) == 21:
		stand_cards()

func card_names(cards: Array) -> String:
	var names := []
	var ranks := ["A","2","3","4","5","6","7","8","9","10","J","Q","K"]
	for card in cards:
		names.append(ranks[int(card)%13])
	return "   ".join(names)

func render_cards(reveal: bool) -> void:
	var old := page.get_node_or_null("Cards")
	if old:
		page.remove_child(old)
		old.queue_free()
	var box := VBoxContainer.new()
	box.name = "Cards"
	box.add_theme_constant_override("separation",32)
	box.custom_minimum_size.y = 300
	box.add_child(text_label("Computer",20))
	box.add_child(text_label(card_names(dealer_cards) if reveal else card_names([dealer_cards[0]])+"   ▰",36))
	box.add_child(text_label("Your Cards • %d" % Rules.blackjack_total(player_cards),20))
	box.add_child(text_label(card_names(player_cards),36))
	page.add_child(box)
	page.move_child(box,mini(1,page.get_child_count()-1))

func hit_card() -> void:
	if finished:
		return
	player_cards.append(deck.pop_back())
	render_cards(false)
	OnKlavGame.checkpoint()
	if Rules.blackjack_total(player_cards)>21:
		render_cards(true)
		announce("You Busted • Computer Wins")

func stand_cards() -> void:
	if finished:
		return
	while Rules.blackjack_total(dealer_cards)<17:
		dealer_cards.append(deck.pop_back())
	render_cards(true)
	var player: int = Rules.blackjack_total(player_cards)
	var dealer: int = Rules.blackjack_total(dealer_cards)
	if dealer>21 or player>dealer:
		announce("You Win!")
	elif dealer>player:
		announce("Computer Wins")
	else:
		announce("Push • Draw")
