extends SceneTree
const Progress = preload("res://player_progress.gd")
const Packs = preload("res://level_pack.gd")
const Gesture = preload("res://touch_gesture.gd")
const Channel = preload("res://turn_channel.gd")
var failed := false
var events: Array[String] = []

func _initialize() -> void:
	call_deferred("run")

func expect(ok: bool, label: String) -> void:
	if not ok:
		failed = true
		printerr("FAIL: " + label)

func run() -> void:
	var progress := Progress.new()
	expect(not progress.unlocked("two", {"two": ["one"]}), "level starts locked")
	expect(progress.complete("one", 80, 2), "valid score")
	expect(progress.complete("one", 60, 3), "replay can improve medal")
	expect(progress.snapshot().levels.one == {"score": 80, "medal": 3}, "keep best score and medal")
	expect(progress.unlocked("two", {"two": ["one"]}), "completion unlocks next level")
	var helper = root.get_node("OnKlavGame")
	expect(await helper.save_data("systems_test", progress.snapshot()), "write named profile")
	var reply: Dictionary = await helper.load_data("systems_test")
	var reopened := Progress.new()
	expect(reply.ok and reopened.restore(reply.state), "restore named profile")
	reopened.complete("one", 90, 3)
	expect(reopened.earned_reward({"one": 10}) == 10, "reward stays single after replay and reload")
	await helper.delete_data("systems_test")

	var pack := Packs.load_levels("res://levels.json", func(row: Dictionary): return row.get("answer") is float)
	expect(pack.size() == 3 and pack.one.answer == 2, "load authored levels")
	expect(Packs.load_levels("res://levels.json", func(_row: Dictionary): return false).is_empty(), "reject whole invalid pack")
	expect(Packs.load_levels("user://levels.json", func(_row: Dictionary): return true).is_empty(), "only local project resources")

	var gesture := Gesture.new()
	root.add_child(gesture)
	gesture.tapped.connect(func(_at): events.append("tap"))
	gesture.held.connect(func(_at): events.append("hold"))
	gesture.dragged.connect(func(_at, _delta): events.append("drag"))
	gesture.swiped.connect(func(_delta): events.append("swipe"))
	gesture.cancelled.connect(func(): events.append("cancel"))
	gesture.begin(0, Vector2.ZERO)
	gesture.finish(1, Vector2.ZERO)
	gesture.finish(0, Vector2.ZERO)
	gesture.begin(0, Vector2.ZERO)
	gesture._process(0.5)
	gesture.finish(0, Vector2.ZERO)
	gesture.begin(0, Vector2.ZERO)
	gesture.move(0, Vector2(60, 0))
	gesture.finish(0, Vector2(60, 0))
	gesture.begin(0, Vector2.ZERO)
	helper.suspended.emit()
	gesture.finish(0, Vector2.ZERO)
	expect(events == ["tap", "hold", "drag", "swipe", "cancel"], "gestures and pause cancellation")
	events.clear()

	var channel := Channel.new()
	root.add_child(channel)
	channel.match_id = "fixture"
	channel.received.connect(func(data): events.append(data + ":" + str(channel.last_rx)))
	var first := {"sequence": 1, "own": false, "data": "T1"}
	var second := {"sequence": 2, "own": false, "data": "T2"}
	channel.apply_state({"id": "fixture", "timeline": [second, first]})
	expect(events.is_empty() and not channel.error.is_empty(), "stop at an out-of-order gap")
	channel.apply_state({"id": "fixture", "timeline": [first, second, second]})
	expect(events == ["T1:1", "T2:2"], "apply ordered turns once with checkpoint cursor")
	var saved := channel.snapshot()
	expect(not channel.restore({"match": "fixture", "rx": 2.5, "tx": 0}) and channel.snapshot() == saved, "invalid cursor leaves state intact")
	var resumed := Channel.new()
	resumed.match_id = "fixture"
	expect(resumed.restore(saved), "reopen match")
	resumed.received.connect(func(data): events.append(data))
	resumed.apply_state({"id": "fixture", "timeline": [first, second, {"sequence": 3, "own": false, "data": "T3"}]})
	expect(events == ["T1:1", "T2:2", "T3"], "resume skips already applied turns")
	resumed.free()
	print("Shared system checks finished")
	quit(1 if failed else 0)
