extends Control
const Rules = preload("res://genre_rules.gd")
const MAX_COINS := 1000000000
const OFFLINE_CAP := 8 * 60 * 60
var coins := 0
var rate := 1
var carry := 0.0
var status: Label
var upgrade: Button

func _ready() -> void:
	var content := VBoxContainer.new()
	content.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_theme_constant_override("separation", 20)
	add_child(content)
	status = Label.new()
	status.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	content.add_child(status)
	var collect := Button.new()
	collect.text = "Collect a coin"
	collect.custom_minimum_size.y = 64
	collect.pressed.connect(func(): credit(1); OnKlavGame.checkpoint())
	content.add_child(collect)
	upgrade = Button.new()
	upgrade.custom_minimum_size.y = 64
	upgrade.pressed.connect(buy_upgrade)
	content.add_child(upgrade)
	render()
	OnKlavGame.attach_session(self, snapshot, restore, new_game,
		"Collect coins and upgrade production. Up to eight hours of production are credited when you resume a save.")

func _process(delta: float) -> void:
	if OnKlavGame.paused:
		return
	carry += delta
	if carry >= 1.0:
		var seconds := floori(carry)
		carry -= seconds
		credit(seconds * rate)

func buy_upgrade() -> void:
	if coins < rate * 10 or rate >= 100:
		return
	coins -= rate * 10
	rate += 1
	render()
	OnKlavGame.checkpoint()

func credit(amount: int) -> void:
	coins = mini(MAX_COINS, coins + amount)
	render()

func render() -> void:
	status.text = "%d coins   ·   %d per second" % [coins, rate]
	upgrade.text = "Increase production (%d coins)" % (rate * 10)
	upgrade.disabled = coins < rate * 10 or rate >= 100

func snapshot() -> Dictionary:
	return {"coins": coins, "rate": rate, "saved_at": int(Time.get_unix_time_from_system())}

func restore(state: Dictionary) -> bool:
	if not Rules.bounded_integer(state.get("coins"), 0, MAX_COINS) or not Rules.bounded_integer(state.get("rate"), 1, 100):
		return false
	if not Rules.bounded_integer(state.get("saved_at"), 0, 4102444800):
		return false
	coins = int(state.coins)
	rate = int(state.rate)
	carry = 0.0
	credit(Rules.offline_reward(int(state.saved_at), int(Time.get_unix_time_from_system()), rate, OFFLINE_CAP))
	return true

func new_game() -> void:
	coins = 0
	rate = 1
	carry = 0.0
	render()
