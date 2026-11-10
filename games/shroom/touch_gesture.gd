extends Node
## Attach to a Control and feed its gui_input signal. One pointer per instance.
signal tapped(at: Vector2)
signal held(at: Vector2)
signal swiped(delta: Vector2)
signal dragged(at: Vector2, delta: Vector2)
signal cancelled
@export var drag_threshold := 18.0
@export var swipe_threshold := 48.0
@export var hold_seconds := 0.45
var pointer := -2
var start := Vector2.ZERO
var last := Vector2.ZERO
var elapsed := 0.0
var moved := false
var hold_sent := false

func _ready() -> void:
	var session := get_node_or_null("/root/OnKlavGame")
	if session != null: session.suspended.connect(cancel)

func feed(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.canceled:
			if pointer == event.index: cancel()
		elif event.pressed:
			begin(event.index, event.position)
		else:
			finish(event.index, event.position)
	elif event is InputEventScreenDrag:
		move(event.index, event.position)
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.device != InputEvent.DEVICE_ID_EMULATION:
		if event.pressed: begin(-1, event.position)
		else: finish(-1, event.position)
	elif event is InputEventMouseMotion and event.device != InputEvent.DEVICE_ID_EMULATION:
		move(-1, event.position)

func begin(id: int, at: Vector2) -> void:
	if pointer != -2: return
	pointer = id
	start = at
	last = at
	elapsed = 0
	moved = false
	hold_sent = false

func move(id: int, at: Vector2) -> void:
	if pointer != id: return
	moved = moved or start.distance_to(at) >= maxf(drag_threshold, 1.0)
	var delta := at - last
	last = at
	if moved: dragged.emit(at, delta)

func finish(id: int, at: Vector2) -> void:
	if pointer != id: return
	var delta := at - start
	var was_drag := moved or delta.length() >= maxf(drag_threshold, 1.0)
	pointer = -2
	if not hold_sent and delta.length() >= maxf(swipe_threshold, maxf(drag_threshold, 1.0)):
		swiped.emit(delta)
	elif not was_drag and not hold_sent:
		tapped.emit(at)

func _process(delta: float) -> void:
	if pointer == -2 or moved or hold_sent: return
	elapsed += delta
	if elapsed >= maxf(hold_seconds, 0.05):
		hold_sent = true
		held.emit(start)

func cancel() -> void:
	if pointer == -2: return
	pointer = -2
	cancelled.emit()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_OUT or what == NOTIFICATION_PAUSED:
		cancel()
