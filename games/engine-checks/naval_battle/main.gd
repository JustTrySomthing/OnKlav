extends Control
const Model=preload("res://naval_battle/model.gd")
const Board=preload("res://naval_battle/board.gd")
const Dock=preload("res://naval_battle/dock.gd")
const Score=preload("res://naval_battle/scoreboard.gd")
const Audio=preload("res://naval_battle/audio.gd")
const Chrome=preload("res://game_chrome.gd")
const Channel=preload("res://turn_channel.gd")
var model=Model.new()
var network
var audio
var canvas:Control
var tabs:HBoxContainer
var actions:HBoxContainer
var place_title:Label
var target_prompt:Label
var message_space:Rect2
var view_delay:=0.0
var board
var scoreboard
var fleet_label:Label
var selected:=0
var horizontal:=true
var target:=-1
var own_view:=true
var feedback:="Place your fleet"
var queued_reply:=""
var ai_time:=0.0
var invalid:=false
var controls:HBoxContainer
var start_button:Button
var rotate_button:Button
var random_button:Button
var enemy_button:Button
var own_button:Button
var ships:Array=[]
var salvos:Array=[]
var active_event:Dictionary={}
var banner:VBoxContainer
var banner_title:Label
var banner_detail:Label
var toast_time:=0.0
var toast_title:=""
var toast_detail:=""
var result_age := 0.0
var banner_state: Array = []
var banner_plate: StyleBoxFlat
var banner_edge: Color
const HELP:="Drag each top-down ship from the dock onto your grid. Placed ships leave the dock. Drag a placed ship to move it, or tap it to rotate. Randomize offers a legal fleet.\n\nDeploy when ready. Choose Enemy Fleet, tap a target, then Fire. Shells arc toward the target: orange impacts and cross marks are hits; white plumes and ring markers are misses. Hits leave damage on visible ships. Sunk ships list and settle into dark wrecks. Enemy attacks automatically show your fleet. Sink all five ships (17 squares) to win. Every shot passes the turn.\n\nWith a friend, your fleet stays on your phone. Only readiness, shots and validated results are sent by text. Wait for delivery or use Retry update in Games."

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	network=Channel.new();add_child(network)
	audio=Audio.new();add_child(audio)
	model.friend=network.enabled;model.host=network.host if network.enabled else true
	canvas=Control.new();add_child(canvas)
	scoreboard=Score.new();scoreboard.model=model;canvas.add_child(scoreboard)
	tabs=HBoxContainer.new();tabs.add_theme_constant_override("separation",8);canvas.add_child(tabs)
	enemy_button=Chrome.action("Enemy Fleet",func():own_view=false;refresh());enemy_button.size_flags_horizontal=Control.SIZE_EXPAND_FILL;tabs.add_child(enemy_button)
	own_button=Chrome.action("Your Fleet",func():own_view=true;refresh());own_button.size_flags_horizontal=Control.SIZE_EXPAND_FILL;tabs.add_child(own_button)
	board=Board.new();board.model=model;canvas.add_child(board)
	board.selected.connect(func(cell):
		if cell!=target and not model.shots.has(str(cell)):audio.play("target")
		target=cell
		refresh())
	board.positioned.connect(place_ship);board.rotated.connect(rotate_ship)
	fleet_label=Chrome.caption("",12);fleet_label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;canvas.add_child(fleet_label)
	place_title=Chrome.caption("Place Your Ships",21);place_title.autowrap_mode=TextServer.AUTOWRAP_OFF;place_title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;canvas.add_child(place_title)
	target_prompt=Chrome.caption("Choose target",21);target_prompt.autowrap_mode=TextServer.AUTOWRAP_OFF;target_prompt.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;target_prompt.add_theme_color_override("font_color",Color("d4e8ef"));canvas.add_child(target_prompt)
	controls=HBoxContainer.new();controls.add_theme_constant_override("separation",3);canvas.add_child(controls)
	for i in range(5):
		var item=Dock.new();item.index=i;item.size_flags_horizontal=Control.SIZE_EXPAND_FILL;item.size_flags_stretch_ratio=Model.LENGTHS[i]+.4
		item.chosen.connect(func(index,event):
			board.begin_dock_drag(index,event)
			if board.dock_dragging:selected=board.drag_ship;refresh())
		controls.add_child(item);ships.append(item)
	actions=HBoxContainer.new();actions.add_theme_constant_override("separation",8);canvas.add_child(actions)
	rotate_button=Chrome.action("",func():
		if model.ships[selected].is_empty():horizontal=not horizontal;refresh()
		else:rotate_ship(selected))
	rotate_button.icon=rotation_icon();rotate_button.tooltip_text="Rotate selected ship";rotate_button.custom_minimum_size.x=44;actions.add_child(rotate_button)
	random_button=Chrome.action("Random",func():model.shuffle_fleet();feedback="Fleet ready";refresh();OnKlavGame.checkpoint());actions.add_child(random_button)
	start_button=Chrome.action("Deploy fleet",primary);start_button.size_flags_horizontal=Control.SIZE_EXPAND_FILL;start_button.add_theme_color_override("font_color",Color("2c180c"));start_button.add_theme_color_override("font_hover_color",Color("2c180c"));start_button.add_theme_color_override("font_pressed_color",Color("2c180c"))
	for state in ["normal","hover","pressed","focus","disabled"]:
		start_button.add_theme_stylebox_override(state,Chrome.box("ffb16b" if state in["normal","focus"] else "ffc68f" if state=="hover" else "e99350" if state=="pressed" else "6c5142","ffd2a4" if state!="disabled" else "826452",15))
	start_button.add_theme_color_override("font_disabled_color",Color("c9b6a9"));actions.add_child(start_button)
	for button in [rotate_button,random_button]:
		for state in ["normal","focus","hover","pressed"]:button.add_theme_stylebox_override(state,Chrome.box("1b2c3a" if state in["normal","focus"] else "2b4050","60717e",14))
	make_banner()
	network.received.connect(receive_turn);network.sent.connect(own_turn);network.changed.connect(refresh)
	resized.connect(layout)
	if OS.has_feature("web"): get_viewport().size_changed.connect(layout, CONNECT_DEFERRED)
	var path:String="user://naval-v2"+("-"+network.match_id if network.enabled else "")+".json"
	migrate_legacy(path)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,HELP,path,{}, {"is_resumable":func(_state:Dictionary):return network.enabled or model.phase!="finished"})
	OnKlavGame.chrome.preferences_changed.connect(func():
		board.reduced=OnKlavGame.chrome.reduce_motion
		board.queue_redraw())
	board.reduced=OnKlavGame.chrome.reduce_motion
	layout();refresh()

func _draw() -> void:
	draw_polygon(PackedVector2Array([Vector2.ZERO,Vector2(size.x,0),size,Vector2(0,size.y)]),
		PackedColorArray([Color("202c43"),Color("151f33"),Color("070f1b"),Color("091b2a")]))

func make_banner() -> void:
	banner=VBoxContainer.new();banner.mouse_filter=Control.MOUSE_FILTER_IGNORE;banner.add_theme_constant_override("separation",2);canvas.add_child(banner)
	banner_title=Chrome.caption("",25);banner_title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;banner.add_child(banner_title)
	banner_detail=Chrome.caption("",14);banner_detail.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;banner_detail.add_theme_color_override("font_color",Color("c1d9e4"));banner.add_child(banner_detail)
	for label in [banner_title,banner_detail]:
		label.mouse_filter=Control.MOUSE_FILTER_IGNORE;label.add_theme_color_override("font_shadow_color",Color("031320"));label.add_theme_constant_override("shadow_offset_y",3);label.add_theme_constant_override("outline_size",3);label.add_theme_color_override("font_outline_color",Color("071723"))
	banner_plate=Chrome.box("0a1729","375363",10)
	banner_plate.bg_color.a=.92
	banner.draw.connect(func():
		var plate := Rect2(Vector2(0,-5),banner.size+Vector2(0,10))
		banner.draw_style_box(banner_plate,plate)
		for x in [12.0,plate.size.x-30.0]:
			banner.draw_line(Vector2(x,plate.position.y+1),Vector2(x+18,plate.position.y+1),banner_edge,2,true))
	banner.hide()

func layout() -> void:
	Chrome.fit_board(canvas, size, 56, 520)
	fit_table();queue_redraw()

func fit_table() -> void:
	if not is_instance_valid(scoreboard):return
	var w:=canvas.size.x;var h:=canvas.size.y;var compact:=h<590
	var setup:bool=model.phase=="setup" and not model.own_ready
	var hud_h:=58.0 if compact else 76.0
	var tabs_h:=40.0 if compact else 44.0
	var gap:=8.0 if compact else 14.0
	var report_h:=52.0 if compact else 78.0
	scoreboard.position=Vector2(12,4);scoreboard.custom_minimum_size.y=hud_h;scoreboard.size=Vector2(w-24,hud_h)
	tabs.position=Vector2(12,4+hud_h+gap);tabs.size=Vector2(w-24,tabs_h)
	message_space=Rect2(12,tabs.position.y+tabs_h+4,w-24,report_h)
	var action_y:=h-(60 if compact else 76)
	var lower:float=action_y-(100 if setup and not model.fleet_ready() else 66 if setup else 70)
	var board_top:=message_space.end.y
	var side:=maxf(100,minf(w-24,lower-board_top))
	board.position=Vector2((w-side)*.5,board_top);board.size=Vector2.ONE*side
	controls.position=Vector2(18,action_y-58);controls.size=Vector2(w-36,40)
	place_title.position=Vector2(12,action_y-90);place_title.size=Vector2(w-24,28)
	actions.position=Vector2(12 if setup else (w-minf(210,w*.56))*.5,action_y);actions.size=Vector2(w-24 if setup else minf(210,w*.56),48)
	target_prompt.position=Vector2(12,board.position.y+side+(action_y-(board.position.y+side)-28)*.48);target_prompt.size=Vector2(w-24,28)
	fleet_label.position=Vector2(14,message_space.position.y);fleet_label.size=Vector2(w-28,report_h)
	banner_title.add_theme_font_size_override("font_size",22 if compact else 26)
	banner_detail.add_theme_font_size_override("font_size",12 if compact else 14)

func start_new() -> void:
	model=Model.new();model.friend=network.enabled;model.host=network.host if network.enabled else true
	board.model=model;selected=0;horizontal=true;target=-1;own_view=true;queued_reply="";invalid=false;ai_time=0;feedback="Place your fleet"
	salvos.clear();active_event={};board.salvo={};toast_time=0;view_delay=0;audio.stop();board.settled_ship=-1;result_age=0;refresh()

func snapshot() -> Dictionary:return {"model":model.snapshot(),"network":network.snapshot(),"reply":queued_reply,"own_view":own_view,"invalid":invalid}
func restore(value:Dictionary) -> bool:
	if not value.get("model") is Dictionary or not value.get("network") is Dictionary:return false
	if network.enabled and not network.restore(value.network):return false
	if not model.restore(value.model):return false
	invalid=bool(value.get("invalid",false));queued_reply=str(value.get("reply",""));own_view=bool(value.get("own_view",false));board.model=model
	salvos.clear();active_event={};board.salvo={};toast_time=0;board.settled_ship=-1;result_age=0
	board.cancel_pointer();target=-1;view_delay=0;ai_time=0;audio.stop()
	if not network.enabled and model.pending>=0:model.solo_result()
	if model.phase=="finished":own_view=model.winner!="you"
	if not network.enabled and model.phase=="battle" and not model.turn:ai_time=.5
	refresh();return true

func notify(title:String,detail:String) -> void:
	toast_title=title;toast_detail=detail;toast_time=3.8

func place_ship(index:int,cell:int,direction:bool) -> void:
	if busy() or deployment_pending():return
	selected=index
	if model.place(index,cell,direction):
		board.settle_ship(index);audio.play("deploy")
		horizontal=direction;feedback=Model.NAMES[index]+" positioned"
		for i in range(5):
			if model.ships[i].is_empty():selected=i;break
		OnKlavGame.checkpoint()
	else:notify("Cannot place here","Ships must fit inside the grid without overlap")
	refresh()

func rotate_ship(index:int) -> void:
	selected=index
	if model.ships[index].is_empty():return
	place_ship(index,int(model.ships[index][0]),int(model.ships[index][1])-int(model.ships[index][0])!=1)

func busy() -> bool:return not active_event.is_empty() or not salvos.is_empty() or view_delay>0
func deployment_pending() -> bool:
	return model.phase=="setup" and network.enabled and (network.pending or network.sending)

func primary() -> void:
	if busy() or invalid:return
	if model.phase=="setup":
		if not model.fleet_ready():notify("Fleet incomplete","Drag all five ships onto your waters");return
		if network.enabled:
			if not network.send("P"):notify("Waiting for delivery","The text channel is still busy")
		else:model.ready();own_view=false;feedback="Choose a target";OnKlavGame.checkpoint()
	elif model.phase=="battle" and model.turn and target>=0 and not own_view:
		if network.enabled:
			if not network.send("S"+str(target)):notify("Waiting for delivery","Your previous update must finish first")
		elif model.fire(target):
			var result:Dictionary=model.solo_result();queue_salvo(result,false);OnKlavGame.checkpoint()
	elif model.phase=="finished":
		if network.enabled:
			if network.host:network.send("N")
		else:OnKlavGame.replay()
	refresh()

# Game state commits before presentation. VFX never send or repeat a turn.
func queue_salvo(out:Dictionary,defending:bool,resolved:bool=true) -> void:
	if out.is_empty():return
	var event:=out.duplicate(true);event.own=defending;event.final=resolved and model.phase=="finished";event.resolved=resolved;event.age=0.0;event.impacted=false;event.launched=false;event.whistled=false
	salvos.append(event)
	if active_event.is_empty():begin_salvo()

func begin_salvo() -> void:
	if salvos.is_empty():return
	view_delay=0;active_event=salvos.pop_front();active_event.flight=.12 if board.reduced else Board.FLIGHT;own_view=bool(active_event.own);target=-1;toast_time=0;board.salvo=active_event
	if not OnKlavGame.paused:audio.play("fire");active_event.launched=true
	refresh()

func resolve_own(out:Dictionary) -> void:
	if not active_event.is_empty() and not active_event.own and int(active_event.cell)==int(out.cell):
		active_event.merge(out,true);active_event.resolved=true;active_event.final=model.phase=="finished";return
	for event in salvos:
		if not event.own and int(event.cell)==int(out.cell):event.merge(out,true);event.resolved=true;event.final=model.phase=="finished";return
	queue_salvo(out,false)

func visible_marks(yours:bool) -> Dictionary:
	var marks:Dictionary=(model.incoming if yours else model.shots).duplicate()
	var events:Array=salvos.duplicate()
	if not active_event.is_empty():events.append(active_event)
	for event in events:
		if bool(event.own)==yours and not event.impacted:marks.erase(str(int(event.cell)))
	return marks

func impact() -> void:
	audio.stop_flight();active_event.impacted=true
	var hit:bool=active_event.get("hit",false);var sunk:bool=not active_event.get("proof",[]).is_empty()
	audio.play("sink" if sunk else "hit" if hit else "miss")
	feedback="Your ship was destroyed" if sunk and active_event.own else "Rival ship destroyed!" if sunk else "Your ship was hit" if hit and active_event.own else "Direct hit!" if hit else "Your rival missed" if active_event.own else "You missed"
	refresh()

func hold_duration(event:Dictionary) -> float:
	return 4.8 if not event.get("proof",[]).is_empty() else 4.1 if event.get("hit",false) else 3.4

func update_banner() -> void:
	var state := [message_space, board.reduced, model.phase, model.winner, busy(), result_age, toast_time, active_event.get("age"), active_event.get("resolved")]
	if state==banner_state:return
	banner_state=state
	banner.size.x=message_space.size.x;banner_title.size.x=message_space.size.x;banner_detail.size.x=message_space.size.x
	var show:=false;var opacity:=1.0;var entrance:=1.0;var shake:=0.0
	banner_edge=Color("82cdd1")
	var flight:float=active_event.get("flight",Board.FLIGHT)
	var elapsed:float=float(active_event.get("age",0.0))-flight
	var final_impact:bool=active_event.get("final",false) and active_event.get("impacted",false)
	var result_delay := .22 if board.reduced else 1.55
	if (final_impact and elapsed>=result_delay) or (model.phase=="finished" and (not busy() or result_age>=1.0)):
		var won:bool=model.winner=="you"
		var age := elapsed-result_delay if final_impact else result_age
		show=true
		entrance=1.0 if board.reduced else clampf(age/.64,0,1)
		opacity=1.0 if board.reduced else smoothstep(0.0,.48,age)
		banner_edge=Color("f5c178") if won else Color("e68a88")
		banner_title.text="VICTORY" if won else "FLEET LOST"
		banner_detail.text="Enemy fleet destroyed" if won else "Your fleet fought to the last ship"
		banner_title.add_theme_color_override("font_color",banner_edge)
	elif not active_event.is_empty():
		if active_event.impacted and elapsed>.12 and not final_impact:
			var duration:=hold_duration(active_event)
			show=true;entrance=clampf((elapsed-.12)/.35,0,1);opacity=clampf((elapsed-.12)/.16,0,1)*clampf((duration-elapsed)/.35,0,1)
			banner_title.text=feedback
			var hit:bool=active_event.get("hit",false);var sunk:bool=not active_event.get("proof",[]).is_empty()
			banner_detail.text=("Your fleet lost a ship" if sunk else "Your rival hit your fleet" if hit else "Your fleet is safe") if active_event.own else ("Your rival lost a ship" if sunk else "Your shot damaged a rival ship" if hit else "Your shot landed in open water")
			banner_title.add_theme_color_override("font_color",Color("ffb59e") if active_event.own and hit else Color("ffcc87") if hit else Color("b6ebf2"))
			banner_edge=Color("ffc386") if hit else Color("b7e7ee")
			if hit and elapsed<.7:shake=sin(elapsed*55)*(1-elapsed/.7)*3
		elif not active_event.get("resolved",true) and float(active_event.age)>=flight:
			show=true;banner_title.text="Waiting for result…";banner_detail.text="Your shot has been sent";banner_title.add_theme_color_override("font_color",Color("c1d9e4"))
	elif toast_time>0:
		show=true;opacity=minf(1,toast_time/.3);banner_title.text=toast_title;banner_detail.text=toast_detail;banner_title.add_theme_color_override("font_color",Color("e8f7fa"))
	elif model.phase=="waiting":
		show=true;banner_title.text="Waiting for your friend";banner_detail.text="Your fleet is deployed";banner_title.add_theme_color_override("font_color",Color("c1d9e4"))
	banner.visible=show;banner.modulate.a=opacity
	if show:
		banner.size=Vector2(message_space.size.x,0)
		banner.position=Vector2(message_space.position.x+(0 if board.reduced else shake),message_space.position.y+(message_space.size.y-banner.size.y)*.5)
		banner.pivot_offset=banner.size*.5
		banner.queue_redraw()
		var pop:float=1+pow(2,-8*entrance)*sin((entrance-.15)*TAU*1.2)*.16 if entrance<1 else 1
		banner.scale=Vector2.ONE*(1 if board.reduced else pop)

func _process(delta:float) -> void:
	if not is_instance_valid(network) or not is_instance_valid(board):return
	toast_time=maxf(0,toast_time-delta)
	audio.music(model.phase=="finished")
	if model.phase=="finished" and not busy():result_age=minf(1.0,result_age+delta)
	if view_delay>0:
		view_delay=maxf(0,view_delay-delta)
		if view_delay==0 and salvos.is_empty():
			own_view=model.winner!="you" if model.phase=="finished" else not model.turn
			refresh()
	if network.enabled and not invalid and not queued_reply.is_empty() and not network.pending and not network.sending and network.status=="active":network.send(queued_reply)
	if not active_event.is_empty():
		if not active_event.launched:audio.play("fire");active_event.launched=true
		var flight:float=active_event.flight
		active_event.age=float(active_event.age)+delta
		if not active_event.get("whistled",false) and float(active_event.age)>=.1:
			active_event.whistled=true
			if active_event.resolved or float(active_event.age)<flight:audio.start_flight(flight-.1)
		if not active_event.resolved:active_event.age=minf(float(active_event.age),flight)
		elif float(active_event.age)>=flight and not active_event.impacted:impact()
		if active_event.impacted and float(active_event.age)>=flight+hold_duration(active_event):
			if active_event.get("final",false):result_age=1.0
			active_event={};board.salvo={}
			if not salvos.is_empty():begin_salvo()
			else:
				view_delay=.55
				if not network.enabled and model.phase=="battle" and not model.turn:ai_time=.75
				refresh()
	elif ai_time>0 and view_delay<=0:
		ai_time-=delta
		if ai_time<=0:
			var out:Dictionary=model.ai_fire()
			if not out.is_empty():queue_salvo(out,true);OnKlavGame.checkpoint();refresh()
	update_banner()

func own_turn(data:String) -> void:
	if data=="N" and network.host and model.phase=="finished":start_new();OnKlavGame.checkpoint();return
	if data==queued_reply:queued_reply=""
	if data=="P":model.ready();own_view=model.phase=="waiting" or not model.turn;feedback="Fleet deployed"
	elif data.begins_with("S") and data.substr(1).is_valid_int():
		var cell:=int(data.substr(1))
		if model.fire(cell):queue_salvo({"cell":cell},false,false)
	OnKlavGame.checkpoint();refresh()

func receive_turn(data:String) -> void:
	if invalid:return
	if data=="N" and not network.host and model.phase=="finished":start_new();OnKlavGame.checkpoint();return
	var accepted:=false
	if data=="P":
		accepted=true
		if not model.peer_ready:
			model.peer_positioned()
			own_view=not model.turn if model.phase=="battle" else true
	elif data.begins_with("S") and data.substr(1).is_valid_int():
		var cell:=int(data.substr(1));var out:Dictionary=model.defend(cell)
		if not out.is_empty():
			accepted=true
			var proof:=PackedStringArray()
			for n in out.proof:proof.append(str(n))
			queued_reply="R%d:%d"%[cell,1 if out.hit else 0]+(":"+",".join(proof) if not proof.is_empty() else "")
			queue_salvo(out,true)
	elif data.begins_with("R"):
		var parts:=data.substr(1).split(":");var proof:=[]
		if parts.size() in [2,3] and parts[0].is_valid_int() and parts[1] in ["0","1"]:
			if parts.size()==3:
				for n in parts[2].split(","):
					if not n.is_valid_int():invalid=true;OnKlavGame.checkpoint();refresh();return
					proof.append(int(n))
			var cell:=int(parts[0]);var hit:=parts[1]=="1"
			if model.result(cell,hit,proof):accepted=true;resolve_own({"cell":cell,"hit":hit,"proof":proof})
	if not accepted:invalid=true
	OnKlavGame.checkpoint();refresh()

func style_fleet_tab(button:Button,selected:bool):
	button.modulate=Color.WHITE
	var ink:=Color("7ee9c8") if selected else Color("85a3b7")
	for state in ["normal","hover","pressed","disabled"]:
		button.add_theme_stylebox_override(state,Chrome.box("183d43" if selected else "102638","7ee9c8" if selected else "294c60",14))
	button.add_theme_color_override("font_color",ink)
	button.add_theme_color_override("font_disabled_color",ink)

func refresh() -> void:
	if not is_instance_valid(board):return
	var setup:bool=model.phase=="setup" and not model.own_ready
	board.model=model;board.own=own_view;board.ship_index=selected;board.horizontal=horizontal;board.target=target;board.locked=busy() or invalid or deployment_pending() or (model.phase!="setup" and (not model.turn or own_view or model.phase!="battle"));board.masked_cells=[]
	if board.locked:board.cancel_pointer()
	var yours:=visible_marks(true)
	var theirs:=visible_marks(false)
	var displayed:=yours if own_view else theirs
	for key in (model.incoming if own_view else model.shots):
		if not displayed.has(key):board.masked_cells.append(int(key))
	rotate_button.disabled=board.locked
	random_button.disabled=board.locked
	board.queue_redraw()
	controls.visible=setup and not model.fleet_ready();rotate_button.visible=setup;random_button.visible=setup
	for i in range(ships.size()):ships[i].visible=model.ships[i].is_empty();ships[i].selected=i==selected;ships[i].queue_redraw()
	enemy_button.disabled=model.phase in ["setup","waiting"] or busy();own_button.disabled=busy()
	style_fleet_tab(enemy_button,not own_view);style_fleet_tab(own_button,own_view)
	fleet_label.text=""
	fleet_label.visible=false
	place_title.visible=setup and not model.fleet_ready()
	target_prompt.visible=not setup and model.phase=="battle" and model.turn and not busy() and not own_view
	target_prompt.text="Choose Target" if target<0 else "Target "+char(65+target%10)+str(target/10+1)

	scoreboard.model=model;scoreboard.your_marks=yours;scoreboard.enemy_marks=theirs;scoreboard.setup=setup;scoreboard.incoming=busy() and own_view
	scoreboard.title="FLEET DEPLOYMENT" if setup else "AWAITING ENEMY FLEET" if model.phase=="waiting" else "INCOMING FIRE" if busy() and own_view else "SHELL AWAY" if busy() else "VICTORY" if model.winner=="you" else "FLEET LOST" if model.winner=="them" else "YOUR TURN" if model.turn else "OPPONENT’S TURN"
	if network.enabled and network.status=="ended":scoreboard.title="YOUR FRIEND LEFT THE GAME"
	if network.enabled and not network.error.is_empty():fleet_label.text=network.error;fleet_label.show()
	scoreboard.queue_redraw()
	start_button.text="Deploy Fleet" if setup else "New game" if model.phase=="finished" else "Fire"
	start_button.disabled=not model.fleet_ready() if setup else false if model.phase=="finished" else model.phase!="battle" or not model.turn or model.pending>=0 or own_view or target<0 or model.shots.has(str(target))
	if busy():
		start_button.disabled=true
		start_button.text="Fire"
		if active_event.get("impacted",false):fleet_label.text=" "
	if invalid:scoreboard.title="TURN NEEDS ATTENTION";fleet_label.text="Saved progress is preserved · Return or quit to recover";fleet_label.show();start_button.disabled=true
	if network.enabled and model.phase=="finished" and not network.host:start_button.disabled=true;start_button.text="Waiting for host"
	if network.enabled and (network.pending or network.sending or network.status!="active"):start_button.disabled=true

	fit_table()

func rotation_icon() -> Texture2D:
	var image:=Image.new();image.load_svg_from_string("<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='#e4f6ff' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'><path d='M20 10A8 8 0 1 0 19 17M20 4V10H14'/></svg>");return ImageTexture.create_from_image(image)

func migrate_legacy(path:String) -> void:
	if network.enabled or FileAccess.file_exists("user://naval-v2-migrated"):return
	var fresh:=OS.has_feature("web") and bool(JavaScriptBridge.eval("window.onklavFreshStart === true"))
	if not fresh and not FileAccess.file_exists(path):
		var old:Dictionary=OnKlavGame.load_match("user://builtin-naval.json")
		if old.ok and old.get("exists",false):
			var converted=Model.from_legacy(old.state)
			if converted!=null:
				model=converted;board.model=model;own_view=false
				if OnKlavGame.save_match(snapshot(),path)!=OK:return
	if fresh or FileAccess.file_exists(path):
		var marker:=FileAccess.open("user://naval-v2-migrated",FileAccess.WRITE)
		if marker!=null:marker.store_string("1");marker.close()
