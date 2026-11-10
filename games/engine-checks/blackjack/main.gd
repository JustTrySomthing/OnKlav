extends Control
const Solo=preload("res://blackjack/solo.gd")
const Tournament=preload("res://blackjack/tournament.gd")
const Peer=preload("res://friend_table_model.gd")
const Table=preload("res://blackjack/table.gd")
const Score=preload("res://blackjack/scoreboard.gd")
const Audio=preload("res://blackjack/audio.gd")
const Chrome=preload("res://game_chrome.gd")
const Channel=preload("res://turn_channel.gd")
const HELP:="SOLO TOURNAMENT: Ten hands; most hand wins takes the tournament. If tied after ten, play tie-break hands until there is a winner. Pushes award no win. Get closer to 21 than the dealer without going over. Hit draws a card; Stand lets the dealer finish. Aces count as 1 or 11. Face cards count 10. The dealer stands on every 17, including soft 17. A two-card 21 is Blackjack and beats a later 21; two naturals push. No wagers or cash are involved.\n\nFRIEND SHOWDOWN: Play ten hands against your friend. If the wins are tied, keep playing until one player wins a tie-break hand. Take hits on your turn, then Stand. Busting loses the hand immediately. The first player alternates each hand; the inviter deals the next hand. Your friend's first card stays face down until the hand ends. The scoreboard counts hands won; tied hands give neither player a win. Friend games are for casual play with people you trust.\n\nThe shared toolbar provides Pause/Play, Help, Sound, Settings, save/return and confirmed Quit. Reduced motion removes the card travel and sparkles."
var model
var network
var audio
var canvas:Control
var table
var scoreboard
var status:Label
var hint:Label
var hit_button:Button
var stand_button:Button
var next_button:Button
var actions:HBoxContainer
var report:PanelContainer
var report_title:Label
var report_detail:Label
var report_kicker:Label
var me:=0
var history:Array=[]
var view:Dictionary={}
var events:Array=[]
var active:Dictionary={}
var invalid:=false
var dealer_time:=0.0
func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	network=Channel.new();add_child(network);me=0 if not network.enabled or network.host else 1
	audio=Audio.new();add_child(audio)
	canvas=Control.new();add_child(canvas)
	var column:=VBoxContainer.new();column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);column.offset_left=10;column.offset_right=-10;column.offset_top=5;column.offset_bottom=-10;column.add_theme_constant_override("separation",8);canvas.add_child(column)
	scoreboard=Score.new();column.add_child(scoreboard)
	status=Chrome.caption("Welcome to the table",19);status.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
	status.add_theme_color_override("font_shadow_color",Color("030e14"));status.add_theme_constant_override("shadow_offset_y",2)
	column.add_child(status)
	table=Table.new();table.me=me;table.friend=network.enabled;table.size_flags_vertical=Control.SIZE_EXPAND_FILL;column.add_child(table)
	hint=Chrome.caption("",14);hint.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;hint.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART;hint.hide();column.add_child(hint)
	var dock:=Control.new();dock.custom_minimum_size.y=54;column.add_child(dock)
	actions=HBoxContainer.new();actions.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);actions.add_theme_constant_override("separation",10);dock.add_child(actions)
	hit_button=Chrome.action("Hit",func():choose("H"));actions.add_child(hit_button)
	dress_action(hit_button,"17604d","61b89a","f2fff6")
	stand_button=Chrome.action("Stand",func():choose("S"));actions.add_child(stand_button)
	dress_action(stand_button,"3b3226","b89459","fff0cb")
	next_button=Chrome.action("New game",next_hand);next_button.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);dock.add_child(next_button)
	dress_action(next_button,"3e3425","d7b674","ffebbd")
	make_report()
	network.sent.connect(func(data):apply_peer(me,data));network.received.connect(func(data):apply_peer(1-me,data));network.changed.connect(refresh)
	resized.connect(layout)
	if OS.has_feature("web"): get_viewport().size_changed.connect(layout, CONNECT_DEFERRED)
	var path:String="user://friend-"+network.match_id+".json" if network.enabled else "user://builtin-blackjack.json"
	OnKlavGame.attach_session(self,snapshot,restore,start_new,HELP,path,{}, {"is_resumable":func(_state:Dictionary):return network.enabled or not model.finished})
	audio.music()
	OnKlavGame.chrome.preferences_changed.connect(func():table.reduced=OnKlavGame.chrome.reduce_motion)
	table.reduced=OnKlavGame.chrome.reduce_motion;layout();refresh()
func dress_action(button:Button,fill:String,border:String,ink:String) -> void:
	button.size_flags_horizontal=Control.SIZE_EXPAND_FILL
	button.add_theme_font_size_override("font_size",20)
	var base:=Color(fill)
	var edge:=Color(border)
	for state in ["normal","hover","pressed"]:
		var tint:=base.lightened(.10) if state=="hover" else base.darkened(.12) if state=="pressed" else base
		var style:=Chrome.box(tint.to_html(false),border,13)
		style.border_width_bottom=3
		style.shadow_size=3
		style.shadow_color=Color(edge,.18)
		button.add_theme_stylebox_override(state,style)
		button.add_theme_color_override("font_color" if state=="normal" else "font_"+state+"_color",Color(ink))

func _draw() -> void:
	for i in range(20):
		draw_rect(Rect2(0,size.y*i/20,size.x,size.y/20+1),Color("162b2a").lerp(Color("080e16"),i/19.0))
	for side in [0,1]:
		var x:=8.0 if side==0 else size.x-8.0
		draw_line(Vector2(x,0),Vector2(x,size.y),Color("bd9d6230"),1,true)
func layout() -> void:
	Chrome.fit_board(canvas, size, 56, 500)
	scoreboard.custom_minimum_size.y=104 if canvas.size.y<600 else 116
	queue_redraw()
func make_report() -> void:
	report=PanelContainer.new();report.mouse_filter=Control.MOUSE_FILTER_IGNORE
	var style:=Chrome.box("101d25fa","c9ad70",16);style.shadow_color=Color(0,0,0,.5);style.shadow_size=9;style.content_margin_top=10;style.content_margin_bottom=10;report.add_theme_stylebox_override("panel",style);canvas.add_child(report)
	var box:=VBoxContainer.new();box.add_theme_constant_override("separation",3);box.mouse_filter=Control.MOUSE_FILTER_IGNORE;report.add_child(box)
	report_kicker=Chrome.caption("HAND COMPLETE",12);report_kicker.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;report_kicker.add_theme_color_override("font_color",Color("d6bc86"));box.add_child(report_kicker)
	report_title=Chrome.caption("",27);report_title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;box.add_child(report_title)
	report_detail=Chrome.caption("",13);report_detail.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;report_detail.add_theme_color_override("font_color",Color("c6d9d7"));box.add_child(report_detail);report.hide()
func busy() -> bool:return not active.is_empty() or not events.is_empty()
func transport_ready() -> bool:return not network.enabled or (network.status=="active" and not network.pending and not network.sending)
func can_play() -> bool:return model!=null and not invalid and not busy() and not model.hand_over and model.turn==me and transport_ready()
func should_reveal() -> bool:return model.hand_over if network.enabled else model.phase!="player"
func hand_winner() -> int:
	var a:=Solo.Rules.blackjack_total(model.hands[0]);var b:=Solo.Rules.blackjack_total(model.hands[1])
	return 1 if a>21 else 0 if b>21 else 0 if a>b else 1 if b>a else -1
func capture() -> Dictionary:
	return {"hands":model.hands.duplicate(true),"stood":model.stood.duplicate(),"turn":model.turn,"hand_over":model.hand_over,"finished":model.finished,"winner":model.winner,"round":model.round,"wins":model.wins.duplicate(),"history":history.duplicate() if network.enabled else model.results.duplicate(),"revealed":should_reveal(),"phase":"friend" if network.enabled else model.phase}
func start_new() -> void:
	model=Peer.new("blackjack",network.match_id.substr(0,15).hex_to_int()) if network.enabled else Tournament.new()
	if not network.enabled:model.start()
	history=[];invalid=false;events.clear();active={};audio.stop();dealer_time=0;table.active={};table.result_age=10;table.landing_age=1;report.hide()
	queue_transition({},capture(),true)
func snapshot() -> Dictionary:
	if network.enabled:return {"game":"blackjack","mode":"friend","tape":model.tape,"network":network.snapshot(),"invalid":invalid}
	return {"game":"blackjack","mode":"solo","model":model.snapshot(),"invalid":invalid}
func restore(value:Dictionary) -> bool:
	if value.get("game")!="blackjack":return false
	var candidate
	var restored_history:=[]
	if network.enabled:
		if value.get("mode","friend")!="friend" or not value.get("tape")is Array or value.tape.size()>4096 or not value.get("network")is Dictionary:return false
		candidate=Peer.new("blackjack",network.match_id.substr(0,15).hex_to_int())
		var own_count:=0
		for move in value.tape:
			if not move is Dictionary or not move.get("data")is String:return false
			var actor:Variant=move.get("actor")
			if not(actor is int or actor is float) or not is_finite(float(actor)) or float(actor)!=int(actor) or int(actor)not in[0,1]:return false
			var previous:bool=candidate.hand_over
			if move.data=="N" and candidate.finished:restored_history=[]
			if not candidate.apply(int(actor),move.data):return false
			if int(actor)==me:own_count+=1
			if candidate.hand_over and not previous:restored_history.append(winner_for(candidate.hands))
		if int(value.network.get("tx",-1))!=own_count or int(value.network.get("rx",-1))!=candidate.tape.size()-own_count or not network.restore(value.network):return false
	else:
		if value.get("mode","solo")!="solo":return false
		candidate=Tournament.new()
		var data:Variant=value.get("model",value)
		if not data is Dictionary or not candidate.restore(data):return false
	model=candidate;history=restored_history;invalid=value.get("invalid",false)==true;events.clear();active={};view=capture();table.hands=view.hands.duplicate(true);table.revealed=view.revealed;table.counts=[float(model.hands[0].size()),float(model.hands[1].size())];table.targets=table.counts.duplicate();table.active={};table.result_age=10;table.landing_age=1;report.hide();dealer_time=.4 if not network.enabled and model.phase=="dealer" else 0
	refresh();return true
static func winner_for(hands:Array) -> int:
	var a:=Solo.Rules.blackjack_total(hands[0]);var b:=Solo.Rules.blackjack_total(hands[1])
	return 1 if a>21 else 0 if b>21 else 0 if a>b else 1 if b>a else -1
func choose(data:String) -> void:
	if not can_play() or OnKlavGame.paused:return
	if data=="H" and Solo.Rules.blackjack_total(model.hands[me])>=21:return
	if network.enabled:network.send(data);refresh();return
	var before:=capture()
	var accepted:bool=model.hit() if data=="H" else model.stand() if data=="S" else false
	if accepted:dealer_time=0;OnKlavGame.checkpoint();queue_transition(before,capture())
func next_hand() -> void:
	if model==null or invalid or busy() or OnKlavGame.paused:return
	if network.enabled:
		if me==0 and model.hand_over and transport_ready():network.send("N");refresh()
		return
	if model.finished:OnKlavGame.replay();return
	if model.next_hand():OnKlavGame.checkpoint();queue_transition({},capture(),true)
	return
func apply_peer(actor:int,data:String) -> void:
	if invalid:return
	var before:=capture()
	if not model.apply(actor,data):invalid=true;OnKlavGame.checkpoint();refresh();return
	if data=="N" and before.finished:history=[]
	if model.hand_over and not bool(before.hand_over):history.append(hand_winner())
	OnKlavGame.checkpoint();queue_transition(before,capture(),data=="N")
func queue_transition(before:Dictionary,after:Dictionary,fresh:bool=false) -> void:
	if fresh:
		events.append({"kind":"reset","state":after,"duration":.08})
		for index in range(2):
			for actor in [me,1-me]:events.append({"kind":"deal","actor":actor,"index":index,"card":after.hands[actor][index],"counts":[after.hands[0].size(),after.hands[1].size()],"duration":.1 if table.reduced else .34})
	else:
		var added:=false
		for actor in range(2):
			for index in range(before.hands[actor].size(),after.hands[actor].size()):
				events.append({"kind":"deal","actor":actor,"index":index,"card":after.hands[actor][index],"counts":[after.hands[0].size(),after.hands[1].size()],"duration":.1 if table.reduced else .36});added=true
		if not added:events.append({"kind":"stand","actor":int(before.turn),"duration":.18})
	if bool(after.revealed) and (fresh or not bool(before.get("revealed",false))):
		var actor:int=1-me;var index:=0 if network.enabled else 1
		events.append({"kind":"flip","actor":actor,"index":index,"card":after.hands[actor][index],"duration":.12 if table.reduced else .42})
	events.append({"kind":"commit","state":after,"duration":.01})
	if after.hand_over and (fresh or not bool(before.get("hand_over",false))):events.append({"kind":"result","state":after,"duration":2.55 if after.finished else 1.9})
	if active.is_empty():begin_event()
	refresh()
func begin_event() -> void:
	if events.is_empty():return
	active=events.pop_front();active.age=0.0;active.launched=false;active.landed=false
	if active.kind=="reset":
		view=active.state.duplicate(true);view.hand_over=false;view.finished=false;view.winner=-1;view.stood=[false,false];table.hands=[[],[]];table.revealed=false;table.counts=[2.0,2.0];table.targets=table.counts.duplicate();table.result_age=10;table.landing_age=1;report.hide()
	elif active.kind=="deal":table.targets=[float(active.counts[0]),float(active.counts[1])]
	elif active.kind=="commit":
		view=active.state.duplicate(true);table.hands=view.hands.duplicate(true);table.revealed=view.revealed;table.targets=[float(view.hands[0].size()),float(view.hands[1].size())]
	table.active=active
	if not OnKlavGame.paused:launch_event()
	refresh()
func launch_event() -> void:
	active.launched=true
	match active.kind:
		"reset":audio.play("shuffle")
		"deal":audio.play("deal")
		"flip","stand":audio.play("flip")
		"result":
			var result:int=active.state.winner
			var kind:="draw" if result<0 else "win" if result==me else "lose"
			audio.play(kind);table.result_kind=kind;table.result_age=0;show_result(active.state)
func show_result(state:Dictionary) -> void:
	var own:=Solo.Rules.blackjack_total(state.hands[me]);var other:=Solo.Rules.blackjack_total(state.hands[1-me]);var winner:int=state.winner
	var rival:="Friend" if network.enabled else "Dealer"
	var ink:=Color("ffdc96") if winner==me else Color("ffc0ae") if winner>=0 else Color("e0d4bc")
	report_kicker.text="TOURNAMENT COMPLETE" if state.finished else "HAND %02d RESULT"%state.round
	report_title.text=outcome_title(state)
	report_title.add_theme_font_size_override("font_size",31 if state.finished else 26)
	report_title.add_theme_color_override("font_color",ink)
	(report.get_theme_stylebox("panel") as StyleBoxFlat).border_color=Color(ink,.85)
	if state.finished:report_detail.text="You %d  ·  %s %d"%[state.wins[me],rival,state.wins[1-me]]
	elif not network.enabled and (Solo.natural(state.hands[0]) or Solo.natural(state.hands[1])):report_detail.text="Two natural 21s" if Solo.natural(state.hands[0]) and Solo.natural(state.hands[1]) else "Your natural 21 takes the hand" if Solo.natural(state.hands[0]) else "Dealer has a natural 21"
	else:report_detail.text="You bust · %d points"%own if own>21 else ("Friend" if network.enabled else "Dealer")+" busts · "+str(other) if other>21 else "You %d · %s %d"%[own,"Friend" if network.enabled else "Dealer",other]
	report.show()
func outcome_title(state:Dictionary) -> String:
	var rival:="Friend" if network.enabled else "Dealer"
	if state.finished:
		if state.winner<0:return "Tournament tied"
		return "Tournament won!" if state.winner==me else rival+" takes the tournament"
	if state.winner<0:return "Push"
	if not network.enabled and Solo.natural(state.hands[state.winner]):
		return "Blackjack!" if state.winner==me else "Dealer blackjack"
	return "Hand won" if state.winner==me else rival+" wins the hand"

func refresh() -> void:
	if not is_instance_valid(table) or view.is_empty():return
	table.stood=view.stood;table.hand_over=view.hand_over
	table.winning_actor=int(view.history.back()) if view.hand_over and not view.history.is_empty() else -1
	table.queue_redraw()
	scoreboard.hands=table.hands;scoreboard.me=me;scoreboard.friend=network.enabled;scoreboard.revealed=table.revealed;scoreboard.turn=view.turn;scoreboard.hand_over=view.hand_over;scoreboard.winner=view.winner;scoreboard.round=view.round;scoreboard.wins=view.wins;scoreboard.history=view.history;scoreboard.queue_redraw()
	var hand_over:bool=view.hand_over
	actions.visible=not hand_over;next_button.visible=hand_over
	hit_button.disabled=not can_play() or Solo.Rules.blackjack_total(model.hands[me])>=21;stand_button.disabled=not can_play()
	next_button.text="Play again" if view.finished else "Deal next hand" if me==0 else "Waiting for host to deal"
	next_button.disabled=busy() or invalid or not transport_ready() or (network.enabled and me!=0)
	status.text="Dealing the cards…" if busy() and active.get("kind") in ["reset","deal"] else "Revealing the hand…" if busy() and active.get("kind")=="flip" else "Standing…" if busy() and active.get("kind")=="stand" else outcome_title(view) if hand_over else "Hit or stand" if view.turn==me else "Your friend’s move" if network.enabled else "Dealer is drawing…"
	status.add_theme_color_override("font_color",Color("edcd91") if hand_over else Color("94ecd1") if view.turn==me else Color("d4dbc9"))
	hint.text=""
	if network.enabled:
		if network.status=="ended":status.text="Your friend left the table";hint.text="Return to Games to start another match"
		elif not network.error.is_empty():hint.text="Delivery delayed · return to Games to retry"
		elif network.pending or network.sending:hint.text="Sending your play · waiting for delivery"
	if invalid:status.text="Game update needs attention";hint.text="Your progress is saved · return to Games";hit_button.disabled=true;stand_button.disabled=true;next_button.disabled=true
	hint.visible=not hint.text.is_empty()
func _process(delta:float) -> void:
	if not is_instance_valid(table):return
	if not active.is_empty():
		if not active.launched:launch_event()
		active.age=float(active.age)+delta
		if active.kind=="deal" and not active.landed and float(active.age)>=float(active.duration):
			active.landed=true;table.hands[int(active.actor)].append(int(active.card))
			table.land(int(active.actor),int(active.index));audio.play("land");refresh()
		if active.kind=="flip" and not active.landed and float(active.age)>=float(active.duration)*.5:
			active.landed=true;table.revealed=true;refresh()
		if active.kind=="result":position_report()
		if float(active.age)>=float(active.duration)+(.05 if active.kind=="deal" else 0):
			if active.kind=="result":report.hide()
			active={};table.active={}
			if not events.is_empty():begin_event()
			elif not network.enabled and model.phase=="dealer":dealer_time=.35
			refresh()
	elif dealer_time>0:
		dealer_time-=delta
		if dealer_time<=0 and not network.enabled and model.phase=="dealer" and not invalid:
			var before:=capture()
			if model.dealer_step():OnKlavGame.checkpoint();queue_transition(before,capture())
func position_report() -> void:
	var width:=minf(340,canvas.size.x-32);report.custom_minimum_size.x=width;report.size=Vector2(width,report.get_combined_minimum_size().y)
	var center:Vector2=canvas.get_global_transform_with_canvas().affine_inverse()*(table.get_global_transform_with_canvas()*(table.size*.5))
	report.position=Vector2((canvas.size.x-width)*.5,center.y-report.size.y*.5)
	var fade:=minf(1,minf(float(active.age)/.15,(float(active.duration)-float(active.age))/.22));report.modulate.a=maxf(0,fade);report.pivot_offset=report.size*.5;report.scale=Vector2.ONE*(1 if table.reduced else 1-.07*exp(-float(active.age)*8)*cos(float(active.age)*19))
