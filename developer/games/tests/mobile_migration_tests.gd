extends SceneTree
const Migration = preload("res://save_migration.gd")
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
	var old := {"schema": 1, "level": "old_docks", "found_key": true}
	expect(game.restore(old), "legacy save loads")
	expect(game.snapshot().level == "harbor" and game.snapshot().inventory == ["key"], "level rename and inventory migration")
	expect(old == {"schema": 1, "level": "old_docks", "found_key": true}, "original snapshot remains unchanged")
	var previous: Dictionary = game.snapshot()
	expect(not game.restore({"schema": 1, "level": "removed", "found_key": true}), "invalid migration rejected")
	expect(game.snapshot() == previous, "failed migration preserves current state")
	var nested := {"schema": 1, "items": ["coin"]}
	var bad := func(draft: Dictionary):
		draft.items.clear()
		return null
	expect(not Migration.migrate(nested, 2, {1: bad}, func(_s): return true).ok and nested.items == ["coin"], "failed callback cannot mutate original")
	game.set_anchors_preset(Control.PRESET_TOP_LEFT)
	game.size = Vector2(360, 720)
	game.toggle_pref("text_scale")
	game.toggle_pref("swapped")
	game.toggle_pref("reduce_motion")
	expect(game.columns.columns == 1 and game.buttons[0].text == "Back", "portrait and remapped action")
	expect(game.buttons[0].custom_minimum_size.y == 78 and game.panel.motion_duration(0.2) == 0, "large touch targets and reduced motion")
	game.size = Vector2(900, 420)
	game.render()
	expect(game.columns.columns == 2, "landscape reflows with large text")
	game.panel.set_safe_insets(Vector4(30, 10, 0, 0))
	expect(game.panel.get_theme_constant("margin_left") == 46, "safe inset applied")
	print("Mobile layout and migration checks finished")
	quit(1 if failed else 0)
