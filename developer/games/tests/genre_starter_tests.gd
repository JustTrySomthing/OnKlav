extends SceneTree
## Run against a generated project: godot --headless --path PROJECT --script THIS_FILE -- TEMPLATE
var failed := false

func _initialize() -> void:
	call_deferred("run")

func expect(ok: bool, label: String) -> void:
	if not ok:
		failed = true
		printerr("FAIL: " + label)

func run() -> void:
	var game = load("res://main.tscn").instantiate()
	root.add_child(game)
	var helper = root.get_node("OnKlavGame")
	helper.paused = false # Drive deterministic steps without writing a player save.
	game.restart()
	var genre := OS.get_cmdline_user_args()[0]
	match genre:
		"racing":
			game.progress = 5.99
			game._physics_process(0.1)
			expect(game.progress == 6.0 and game.replay_button.visible, "finish line")
		"defense":
			game.place(0)
			game.start_wave()
			game._physics_process(0.64)
			expect(game.towers == 1 and game.remaining == 2 and game.coins == 3, "tower defeats first enemy")
		"rhythm":
			game.adjust(50)
			game.elapsed = 0.7
			game.tap()
			game.tap()
			expect(game.hits == 1 and game.judged == 1, "offset and duplicate tap")
		"match3":
			expect(game.matches().is_empty(), "starter board has no pre-existing matches")
			game.select_tile(1)
			game.select_tile(7)
			expect(game.score >= 3 and game.matches().is_empty(), "valid swap clears and refills")
		"merge":
			game.board.fill(0)
			for i in range(4): game.board[i] = 2
			game.slide(0)
			expect(game.board[0] == 4 and game.board[1] == 4, "each tile merges once")
		"sports":
			game.attempts = 1
			game.ball = Vector2(0.5, 0.19)
			game.velocity = Vector2(0, -0.1)
			game._physics_process(0.016)
			expect(game.goals == 1 and game.velocity == Vector2.ZERO, "goal scores once and resets ball")
		"builder":
			game.build_on(0)
			game.build_on(0)
			game.collect()
			expect(game.coins == 7 and game.production() == 1, "occupied plot and production")
		"breakout":
			game.launch()
			expect(game.velocity.y < 0, "launch aims up")
			game.ball = Vector2(0.125, 0.17)
			game.velocity = Vector2(0, -0.36)
			var before: int = game.bricks
			game._physics_process(0.016)
			expect(game.bricks < before and game.velocity.y > 0, "brick hit scores and rebounds")
		"wordquiz":
			expect(game.questions.size() == 3, "local question pack loads")
			game.typed = "LIBRARY"
			game.submit()
			expect(game.score == 1 and game.index == 1, "word answer uses normalized comparison")
		"cards":
			game.move_card(0, 1)
			expect(game.tops == [0, 0, 0], "wrong suit rejected")
			for cards in [[0, 3, 6], [1, 4, 7], [2, 5, 8]]:
				for card in cards: game.move_card(card, floori(float(card) / 3))
			expect(game.replay_button.visible and game.tops == [3, 3, 3], "all foundations can finish")
		"adventure":
			game.transition("hall")
			game.transition("garden", "brass_key")
			expect(game.story.scene == "hall", "dialogue gate requires inventory")
			game.transition("room")
			game.take_key()
			game.transition("hall")
			game.transition("garden", "brass_key")
			expect(game.replay_button.visible, "hotspot and choice reach ending")
		"tactics":
			game.play(Vector2i(4, 0))
			expect(game.turns == 0, "illegal grid move preserves turn")
			game.play(Vector2i(1, 2))
			game.play(Vector2i(2, 2))
			for attempt in range(3): game.play(Vector2i(3, 2))
			expect(game.enemy_hp == 0 and game.hp > 0, "legal turn order and targeting")
		_:
			expect(false, "unknown template")
	var saved: Dictionary = game.snapshot()
	expect(not game.restore({}), "invalid save rejected")
	expect(game.snapshot() == saved, "invalid save leaves current state intact")
	game.restart()
	expect(game.restore(saved) and game.snapshot() == saved, "save round trip")
	game.queue_free()
	await process_frame
	if not failed:
		print(genre + ": gameplay and save checks passed")
	quit(1 if failed else 0)
