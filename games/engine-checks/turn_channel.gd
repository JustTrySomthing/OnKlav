extends Node
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
	var remote_error: Variant = JavaScriptBridge.eval("window.onklavTurnError || ''")
	if str(remote_error) != "":
		error = str(remote_error); sending = false
		JavaScriptBridge.eval("window.onklavTurnError=''")
	# One native journal orders both directions for restart replay.
	for entry in state.get("timeline", []):
		var sequence := int(entry.get("sequence", 0))
		if bool(entry.get("own", false)):
			if sequence == last_tx + 1:
				last_tx = sequence; sending = false
				sent.emit(str(entry.get("data", "")))
		elif sequence == last_rx + 1:
			last_rx = sequence
			received.emit(str(entry.get("data", "")))
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
	if enabled: JavaScriptBridge.eval('console.log("ONKLAV_TURN|retry")')

func snapshot() -> Dictionary: return {"rx":last_rx,"tx":last_tx,"match":match_id}
func restore(value: Dictionary) -> bool:
	if str(value.get("match", "")) != match_id: return false
	last_rx = int(value.get("rx", 0)); last_tx = int(value.get("tx", 0))
	return last_rx >= 0 and last_rx <= 9999 and last_tx >= 0 and last_tx <= 9999

func _exit_tree() -> void:
	if enabled and JavaScriptBridge.get_interface("window").onklavTurnState == callback:
		JavaScriptBridge.get_interface("window").onklavTurnState = null
