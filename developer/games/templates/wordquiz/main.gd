extends "res://starter_hud.gd"
const Classic = preload("res://classic_rules.gd")
var questions: Array
var index := 0
var score := 0
var typed := ""
var answer_view: Label

func _ready() -> void:
	make_layout()
	questions = Classic.load_questions("res://questions.json")
	var box := VBoxContainer.new()
	box.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	box.alignment = BoxContainer.ALIGNMENT_CENTER
	field.add_child(box)
	answer_view = Label.new()
	answer_view.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	box.add_child(answer_view)
	var keys := GridContainer.new()
	keys.columns = 7
	keys.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	box.add_child(keys)
	for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
		var button := Button.new()
		button.text = letter
		button.custom_minimum_size = Vector2(52, 52)
		button.pressed.connect(type_letter.bind(letter))
		keys.add_child(button)
	action("Space", type_letter.bind(" "))
	action("Erase", erase)
	action("Submit", submit)
	render()
	attach("Answer the local word and trivia questions using the letter grid. The starter uses English letters and spaces.")

func type_letter(letter: String) -> void:
	if index < questions.size() and typed.length() < 24:
		typed += letter
		render()

func erase() -> void:
	typed = typed.left(maxi(0, typed.length() - 1))
	render()

func submit() -> void:
	if index >= questions.size() or typed.strip_edges().is_empty(): return
	if Classic.text_matches(typed, questions[index].answer):
		score += 1
	index += 1
	typed = ""
	render()
	OnKlavGame.checkpoint()

func render() -> void:
	replay_button.visible = not questions.is_empty() and index == questions.size()
	status.text = "Question pack unavailable" if questions.is_empty() else ("%d / %d correct" % [score, questions.size()] if replay_button.visible else str(questions[index].prompt))
	answer_view.text = typed if not typed.is_empty() else "Tap letters to answer"

func snapshot() -> Dictionary:
	return {"index": index, "score": score, "typed": typed}

func restore(state: Dictionary) -> bool:
	if not Rules.bounded_integer(state.get("index"), 0, questions.size()) or not Rules.bounded_integer(state.get("score"), 0, int(state.index)): return false
	var text: Variant = state.get("typed")
	if not text is String or text.length() > 24: return false
	for letter in text:
		if letter not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ ": return false
	index = int(state.index)
	score = int(state.score)
	typed = text
	render()
	return true

func restart() -> void:
	index = 0
	score = 0
	typed = ""
	render()
