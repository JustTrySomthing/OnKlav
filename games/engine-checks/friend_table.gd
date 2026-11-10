extends Control
const Model = preload("res://friend_table_model.gd")
const Chrome = preload("res://game_chrome.gd")
const Channel = preload("res://turn_channel.gd")
var game := "four"
var model
var network
var page: VBoxContainer
var status: Label
var subtitle: Label
var grid: GridContainer
var cards: VBoxContainer
var actions: HBoxContainer
var invalid := false
var me := 0

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	network=Channel.new();add_child(network);me=0 if network.host else 1
	model=Model.new(game,network.match_id.substr(0,15).hex_to_int())
	page=VBoxContainer.new();page.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);page.offset_left=18;page.offset_right=-18;page.offset_top=18;page.add_theme_constant_override("separation",16);add_child(page)
	status=Chrome.caption("Friend game",28);page.add_child(status)
	subtitle=Chrome.caption("",20);page.add_child(subtitle)
	if game=="four":
		grid=GridContainer.new();grid.columns=7;page.add_child(grid)
		for cell in range(42):
			var item:=Chrome.action("·",func():send("F"+str(cell%7)));item.custom_minimum_size=Vector2(74,76);item.add_theme_font_size_override("font_size",32);grid.add_child(item)
	else:
		cards=VBoxContainer.new();cards.add_theme_constant_override("separation",18);page.add_child(cards)
		actions=HBoxContainer.new();actions.add_theme_constant_override("separation",12);page.add_child(actions)
		actions.add_child(Chrome.action("Hit",func():send("H")));actions.add_child(Chrome.action("Stand",func():send("S")));actions.add_child(Chrome.action("Next hand",func():send("N")))
	var retry:=Chrome.action("Retry last update",network.retry);page.add_child(retry)
	network.sent.connect(func(data):apply_turn(me,data));network.received.connect(func(data):apply_turn(1-me,data));network.changed.connect(render)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,"Take turns with your friend. Cats vs Dogs: connect four across, down or diagonally. Blackjack: ten hands between two equal players, no betting. Hit or Stand; bust loses immediately. First turn alternates each hand. Your opponent’s first card is hidden until the hand ends. SMS can take time; Retry resends the same update safely.","user://friend-"+network.match_id+".json")
	render()

func start_new() -> void:model=Model.new(game,network.match_id.substr(0,15).hex_to_int());invalid=false;render()
func snapshot() -> Dictionary:return {"game":game,"tape":model.tape,"network":network.snapshot()}
func restore(state:Dictionary) -> bool:
	if state.get("game")!=game or not state.get("tape")is Array or state.tape.size()>400 or not state.get("network")is Dictionary:return false
	var candidate=Model.new(game,network.match_id.substr(0,15).hex_to_int())
	for move in state.tape:
		if not move is Dictionary or not move.get("actor")in[0,1] or not move.get("data")is String or not candidate.apply(int(move.actor),move.data):return false
	if not network.restore(state.network):return false
	model=candidate;render();return true
func send(data:String) -> void:
	if invalid or model.finished:return
	if data!="N" and model.turn!=me:return
	if network.send(data):render()
func apply_turn(actor:int,data:String) -> void:
	if not model.apply(actor,data):invalid=true
	else:OnKlavGame.chrome.audio_feedback()
	OnKlavGame.checkpoint();render()
func render() -> void:
	if not is_instance_valid(status):return
	var ready:bool=not invalid and not model.finished and not network.pending and not network.sending and network.status=="active"
	status.text="You won!" if model.finished and model.winner==me else "Your friend won" if model.finished and model.winner>=0 else "Draw" if model.finished else "Your turn" if model.turn==me else "Your friend’s turn"
	if invalid:status.text="Invalid game update · return to Games"
	elif network.status=="ended":status.text="Your friend left the game"
	elif network.pending or network.sending:status.text="Sending · waiting for delivery"
	subtitle.text=network.error if not network.error.is_empty() else "You: "+("Dogs" if symbol(me)=="D" else "Cats")+"  •  Friend: "+("Dogs" if symbol(1-me)=="D" else "Cats") if game=="four" else "Hand %d of 10   •   You %d — %d Friend"%[model.round,model.wins[me],model.wins[1-me]]
	if game=="four":
		for i in range(42):
			var item:Button=grid.get_child(i);item.text="·" if model.board[i]==0 else symbol(int(model.board[i])-1);item.modulate=Color.WHITE if model.board[i]==0 else Color("c9aaff") if model.board[i]==1 else Color("70d3ed");item.disabled=not ready or model.turn!=me or model.board[i%7]!=0
	else:
		for child in cards.get_children():cards.remove_child(child);child.queue_free()
		cards.add_child(Chrome.caption("Friend",20));cards.add_child(Chrome.caption(card_text(model.hands[1-me],not model.hand_over),32));cards.add_child(Chrome.caption("You · %d"%Model.Rules.blackjack_total(model.hands[me]),20));cards.add_child(Chrome.caption(card_text(model.hands[me],false),32))
		if model.hand_over and not model.finished:cards.add_child(Chrome.caption("You won the hand" if model.winner==me else "Friend won the hand" if model.winner>=0 else "Hand drawn",24))
		actions.get_child(0).disabled=not ready or model.turn!=me or model.hand_over;actions.get_child(1).disabled=actions.get_child(0).disabled;actions.get_child(2).visible=model.hand_over and not model.finished;actions.get_child(2).disabled=not ready or me!=0
func card_text(values:Array,hidden:bool) -> String:
	var ranks:=["A","2","3","4","5","6","7","8","9","10","J","Q","K"];var result:=PackedStringArray()
	for i in range(values.size()):result.append("▰" if hidden and i==0 else ranks[int(values[i])%13])
	return "   ".join(result)

func symbol(actor:int) -> String:return "D" if (actor==0)==network.host_dog else "C"
