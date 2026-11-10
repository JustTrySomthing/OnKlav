extends Node
const Rules = preload("res://genre_rules.gd")
## Host-scoped, durable SMS turns. No recipient, arbitrary SMS body or Android object.
signal received(data: String)
signal sent(data: String)
signal changed
var enabled := false
var host := false
var host_dog := false
var match_id := ""
var status := ""
var pending := false
var sending := false
var error := ""
var last_rx := 0
var last_tx := 0
var callback

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	if not OS.has_feature("web"): return
	var raw: Variant = JavaScriptBridge.eval("JSON.stringify(window.onklavFriend || null)")
	var metadata: Variant = JSON.parse_string(str(raw))
	if not metadata is Dictionary: return
	enabled = true
	host = bool(metadata.get("host", false))
	host_dog = bool(metadata.get("host_dog", false))
	match_id = str(metadata.get("id", ""))
	callback = JavaScriptBridge.create_callback(func(args):
		if args.size() == 1:
			var text: Variant = JavaScriptBridge.eval("JSON.stringify(window.onklavTurnSnapshot)")
			var state: Variant = JSON.parse_string(str(text))
			if state is Dictionary: apply_state(state))
	JavaScriptBridge.get_interface("window").onklavTurnState = callback

func apply_state(state: Dictionary) -> void:
	if str(state.get("id", "")) != match_id: return
	status = str(state.get("status", ""))
	pending = bool(state.get("pending", false))
	error = str(state.get("error", ""))
	if OS.has_feature("web"):
		var remote_error: Variant = JavaScriptBridge.eval("window.onklavTurnError || ''")
		if str(remote_error) != "":
			error = str(remote_error)
			sending = false
			JavaScriptBridge.eval("window.onklavTurnError=''")
	# Native journal order spans both directions. Stop at gaps instead of
	# applying a later event against the wrong board.
	var timeline: Variant = state.get("timeline", [])
	if not timeline is Array:
		error = "Invalid turn journal"
		changed.emit()
		return
	for entry in timeline:
		if not entry is Dictionary or not Rules.bounded_integer(entry.get("sequence"), 1, 9999) or not entry.get("own") is bool or not entry.get("data") is String:
			error = "Invalid turn journal"
			break
		var sequence := int(entry.sequence)
		var own: bool = entry.own
		var cursor := last_tx if own else last_rx
		if sequence <= cursor: continue
		if sequence != cursor + 1:
			error = "Waiting for an earlier text turn"
			break
		# Advance before emitting so the game checkpoints the board and cursor together.
		if own:
			last_tx = sequence
			sending = false
			sent.emit(entry.data)
		else:
			last_rx = sequence
			received.emit(entry.data)
	changed.emit()

func send(data: String) -> bool:
	if not enabled or status != "active" or pending or sending: return false
	if data.length() < 1 or data.length() > 64: return false
	var regex := RegEx.new(); regex.compile("^[A-Za-z0-9_.,:-]+$")
	if regex.search(data) == null: return false
	sending = true
	JavaScriptBridge.eval('console.log("ONKLAV_TURN|' + data + '")')
	return true

func retry() -> void:
	if enabled and OS.has_feature("web"): JavaScriptBridge.eval('console.log("ONKLAV_TURN|retry")')

func snapshot() -> Dictionary: return {"rx":last_rx,"tx":last_tx,"match":match_id}
func restore(value: Dictionary) -> bool:
	if str(value.get("match", "")) != match_id: return false
	var rx: Variant = value.get("rx", 0)
	var tx: Variant = value.get("tx", 0)
	if not Rules.bounded_integer(rx, 0, 9999) or not Rules.bounded_integer(tx, 0, 9999): return false
	last_rx = int(rx)
	last_tx = int(tx)
	return true

func _exit_tree() -> void:
	if enabled and JavaScriptBridge.get_interface("window").onklavTurnState == callback:
		JavaScriptBridge.get_interface("window").onklavTurnState = null
