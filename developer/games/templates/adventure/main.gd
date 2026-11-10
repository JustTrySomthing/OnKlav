extends "res://starter_hud.gd"
const Story = preload("res://story_state.gd")
const SCENES: Array[String] = ["room", "hall", "garden"]
const ITEMS: Array[String] = ["brass_key"]
var story := Story.new()
var scene_view: Label
var inventory: Label
var hotspots: VBoxContainer

func _ready() -> void:
	make_layout()
	var box := VBoxContainer.new()
	box.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	box.alignment = BoxContainer.ALIGNMENT_CENTER
	box.add_theme_constant_override("separation", 20)
	field.add_child(box)
	scene_view = Label.new()
	scene_view.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	scene_view.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	box.add_child(scene_view)
	inventory = Label.new()
	inventory.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	box.add_child(inventory)
	hotspots = VBoxContainer.new()
	box.add_child(hotspots)
	restart()
	attach("Tap hotspots to investigate. Find an item and use the dialogue choices to reach the garden.")

func hotspot(title: String, callback: Callable) -> void:
	var button := Button.new()
	button.text = title
	button.custom_minimum_size.y = 58
	button.pressed.connect(callback)
	hotspots.add_child(button)

func transition(target: String, required: String = "") -> void:
	if story.choose(target, required, SCENES):
		render()
		OnKlavGame.checkpoint()
	else:
		status.text = "The caretaker asks for a brass key."

func take_key() -> void:
	if story.collect("brass_key", ITEMS):
		render()
		OnKlavGame.checkpoint()

func render() -> void:
	for old in hotspots.get_children():
		hotspots.remove_child(old)
		old.queue_free()
	status.text = story.scene.capitalize()
	inventory.text = "Inventory: brass key" if "brass_key" in story.items else "Inventory: empty"
	replay_button.visible = story.scene == "garden"
	match story.scene:
		"room":
			scene_view.text = "A desk stands beside a door."
			if "brass_key" not in story.items: hotspot("Search the desk", take_key)
			hotspot("Go to the hall", transition.bind("hall"))
		"hall":
			scene_view.text = "Caretaker: Have you found the garden key?"
			hotspot("Yes, show the key", transition.bind("garden", "brass_key"))
			hotspot("Go back and look", transition.bind("room"))
		"garden":
			scene_view.text = "The caretaker opens the door. You found the garden."

func snapshot() -> Dictionary:
	return story.snapshot()

func restore(state: Dictionary) -> bool:
	if not story.restore(state, SCENES, ITEMS): return false
	render()
	return true

func restart() -> void:
	story.scene = "room"
	story.items.clear()
	render()
