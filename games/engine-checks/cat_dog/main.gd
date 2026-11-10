extends Control
const FACEOFF=preload("res://cat_dog/art/faceoff.webp")
const INTRO_TIME:=2.05
const INTRO_REVEAL:=.46
const Model=preload("res://cat_dog/series.gd")
const Board=preload("res://cat_dog/board.gd")
const Score=preload("res://cat_dog/scoreboard.gd")
const Audio=preload("res://cat_dog/audio.gd")
const Art=preload("res://cat_dog/art.gd")
const Chrome=preload("res://game_chrome.gd")
const Channel=preload("res://turn_channel.gd")
const HELP:="Tap inside a board column to drop your pet, or press and slide across the board to preview your landing spot. Release inside the board to drop. Connect four of your pets horizontally, vertically or diagonally to win. Full columns cannot accept another piece. First to win three rounds wins the tournament; draws award no win.\n\nChoose Cat or Dog in single player. Your rival takes the other side. In friend play, the inviter chooses Cats or Dogs and the inviter starts. Turns travel by text message; wait for delivery before the next move. Return to Games to retry a delayed update.\n\nThe shared top bar provides Pause/Play, save and return, Help, Sound and Settings. Reduce motion replaces the falling/bouncing effects with short fades and disables confetti."
var model=Model.new()
var network
var audio
var canvas:Control
var board
var scoreboard
var status:Label
var status_age:=1.0
var intro_left:=0.0
var fight_font:=FontVariation.new()
var status_ink:=Color.WHITE
var new_button:Button
var footer_hint:Label
var report:PanelContainer
var report_title:Label
var report_detail:Label
var report_time:=0.0
var report_age:=0.0
var report_kind:=""
var loss_delay:=0.0
var me:=0
var host_dog:=false
var side_picker: ColorRect
var dog_choice: Button
var invalid:=false
var events:Array=[]
var active:Dictionary={}
var think_time:=0.0
var outcome_shown:=false
func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	fight_font.base_font=ThemeDB.fallback_font
	fight_font.variation_embolden=1.1
	network=Channel.new();add_child(network)
	me=0 if not network.enabled or network.host else 1;host_dog=network.host_dog if network.enabled else false
	audio=Audio.new();add_child(audio)
	canvas=Control.new();canvas.draw.connect(draw_sparks);add_child(canvas)
	var column:=VBoxContainer.new();column.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);column.offset_left=10;column.offset_right=-10;column.offset_top=12;column.offset_bottom=-20;column.add_theme_constant_override("separation",8);canvas.add_child(column)
	scoreboard=Score.new();column.add_child(scoreboard)
	status=Chrome.caption("Cat’s turn",27);status.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
	status.vertical_alignment=VERTICAL_ALIGNMENT_CENTER;status.mouse_filter=Control.MOUSE_FILTER_IGNORE
	status.add_theme_color_override("font_shadow_color",Color("02060e"))
	status.add_theme_constant_override("shadow_offset_y",3)
	status.add_theme_color_override("font_outline_color",Color("07101e"))
	status.add_theme_constant_override("outline_size",5)
	canvas.add_child(status)
	var breathing_room:=Control.new();breathing_room.custom_minimum_size.y=78;column.add_child(breathing_room)
	board=Board.new();board.host_dog=host_dog;board.size_flags_vertical=Control.SIZE_EXPAND_FILL;column.add_child(board)
	board.chosen.connect(choose)
	var footer:=HBoxContainer.new()
	footer.custom_minimum_size.y=44
	footer.alignment=BoxContainer.ALIGNMENT_CENTER
	column.add_child(footer)
	footer_hint=Chrome.caption("Get 4 Coins in a Row",16)
	footer_hint.autowrap_mode=TextServer.AUTOWRAP_OFF
	footer_hint.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
	footer_hint.vertical_alignment=VERTICAL_ALIGNMENT_CENTER
	footer_hint.size_flags_vertical=Control.SIZE_SHRINK_CENTER
	footer_hint.mouse_filter=Control.MOUSE_FILTER_IGNORE
	footer_hint.add_theme_color_override("font_color",Color("a5aab3"))
	footer.add_child(footer_hint)
	new_button=Chrome.action("Play again",next_round)
	new_button.custom_minimum_size.x=148
	footer.add_child(new_button)
	make_report()
	make_side_picker()
	network.sent.connect(func(data):apply_move(me,data));network.received.connect(func(data):apply_move(1-me,data));network.changed.connect(refresh)
	resized.connect(layout)
	if OS.has_feature("web"): get_viewport().size_changed.connect(layout, CONNECT_DEFERRED)
	var path:String="user://friend-"+network.match_id+".json" if network.enabled else "user://builtin-four.json"
	OnKlavGame.attach_session(self,snapshot,restore,start_new,HELP,path,{}, {"is_resumable":func(_state:Dictionary):return network.enabled or not model.series_over()})
	audio.music()
	OnKlavGame.suspended.connect(board.cancel_pointer)
	OnKlavGame.chrome.preferences_changed.connect(preferences)
	preferences();layout();refresh()
func _draw() -> void:
	var middle:=size.x*.5
	for i in range(24):
		var y:=size.y*i/24
		var h:=size.y/24+1
		draw_rect(Rect2(0,y,middle,h),Color("352438").lerp(Color("101522"),i/23.0))
		draw_rect(Rect2(middle,y,middle,h),Color("183449").lerp(Color("101522"),i/23.0))
	var floor_y:=size.y*.76
	draw_rect(Rect2(0,floor_y,size.x,size.y-floor_y),Color("0b1320"))
	for i in range(-4,5):
		draw_line(Vector2(middle+i*size.x*.09,floor_y),Vector2(middle+i*size.x*.22,size.y),Color("ffffff0a"),1,true)
	draw_line(Vector2(0,floor_y),Vector2(size.x,floor_y),Color("ffffff14"),1,true)
	for side in [-1,1]:
		var x:float=middle+side*size.x*.36
		var ink:=Art.ROSE if side<0 else Art.BLUE
		var banner:=Rect2(x-size.x*.09,0,size.x*.18,floor_y*.46)
		draw_rect(banner,Color(ink,.05))
		draw_rect(banner,Color(ink,.12),false,1)
		Art.paw(self,Vector2(x,banner.end.y-24),12,Color(ink,.25))
func layout() -> void:
	Chrome.fit_board(canvas, size, 56, 500)
	scoreboard.custom_minimum_size.y=102 if canvas.size.y<600 else 122
	queue_redraw()
func preferences() -> void:
	board.reduced=OnKlavGame.chrome.reduce_motion;scoreboard.reduced=board.reduced
func is_cat(actor:int) -> bool:return (actor==0)!=host_dog
func team(actor:int) -> String:return "Cat" if is_cat(actor) else "Dog"
func busy() -> bool:return not active.is_empty() or not events.is_empty()
func can_play() -> bool:return not side_picker.visible and intro_left<=0 and not invalid and not model.finished and not busy() and model.turn==me and (not network.enabled or (network.status=="active" and not network.sending and not network.pending))
func make_report() -> void:
	report=PanelContainer.new();report.mouse_filter=Control.MOUSE_FILTER_IGNORE
	report.add_theme_stylebox_override("panel",StyleBoxEmpty.new());canvas.add_child(report)
	var column:=VBoxContainer.new();column.add_theme_constant_override("separation",5);column.mouse_filter=Control.MOUSE_FILTER_IGNORE;report.add_child(column)
	report_title=Chrome.caption("",30);report_title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;report_title.add_theme_color_override("font_shadow_color",Color("020811"));report_title.add_theme_constant_override("shadow_offset_y",3);report_title.add_theme_color_override("font_outline_color",Color("07101e"));report_title.add_theme_constant_override("outline_size",5);column.add_child(report_title)
	report_detail=Chrome.caption("",15);report_detail.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;report_detail.add_theme_color_override("font_color",Color("b8cde0"));column.add_child(report_detail);report.hide()
func notify(title:String,detail:String,duration:float=1.5,kind:String="") -> void:
	report_title.text=title;report_detail.text=detail;report_time=duration;report_age=0
	report_kind=kind
	report_title.add_theme_color_override("font_outline_color",Color("fff5ec") if kind=="round" else Color("07101e"))
	report_title.add_theme_font_size_override("font_size",29 if kind=="round" else 30)
	report_title.add_theme_color_override("font_color",Art.color(is_cat(model.winner if model.winner>=0 else me)))
func make_side_picker() -> void:
	side_picker = ColorRect.new()
	side_picker.color = Color("090e20e8")
	side_picker.hide()
	canvas.add_child(side_picker)
	side_picker.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var center := CenterContainer.new()
	side_picker.add_child(center)
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var panel := PanelContainer.new()
	var frame := Chrome.box("132239", "719fc5", 18)
	for side in [SIDE_LEFT, SIDE_TOP, SIDE_RIGHT, SIDE_BOTTOM]:
		frame.set_content_margin(side, 18)
	panel.add_theme_stylebox_override("panel", frame)
	center.add_child(panel)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation",16)
	panel.add_child(column)
	var title := Chrome.caption("Choose your side",25)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	column.add_child(title)
	var choices := HBoxContainer.new()
	choices.add_theme_constant_override("separation",16)
	column.add_child(choices)
	var group := ButtonGroup.new()
	for dog in [false,true]:
		var name_label := "Dog" if dog else "Cat"
		var choice := Chrome.action(name_label,func(): pass)
		choice.icon = preload("res://cat_dog/art/dog.webp") if dog else preload("res://cat_dog/art/cat.webp")
		choice.expand_icon = true
		choice.icon_alignment = HORIZONTAL_ALIGNMENT_CENTER
		choice.vertical_icon_alignment = VERTICAL_ALIGNMENT_TOP
		choice.custom_minimum_size = Vector2(130,142)
		choice.toggle_mode = true
		choice.button_group = group
		choice.button_pressed = not dog
		var ink := Art.BLUE if dog else Art.ROSE
		choice.add_theme_stylebox_override("pressed",Chrome.box("24374b",ink.to_html(false),14))
		choice.add_theme_stylebox_override("hover_pressed",Chrome.box("24374b",ink.to_html(false),14))
		choices.add_child(choice)
		if dog: dog_choice = choice
	var play := Chrome.action("Play",confirm_side)
	play.custom_minimum_size.y = 48
	play.add_theme_stylebox_override("normal",Chrome.box("17604d","61b89a",14))
	column.add_child(play)

func confirm_side() -> void:
	if not side_picker.visible or network.enabled or OnKlavGame.paused: return
	host_dog = dog_choice.button_pressed
	side_picker.hide()
	intro_left = INTRO_TIME
	OnKlavGame.checkpoint()
	refresh()

func start_new() -> void:
	side_picker.visible=not network.enabled
	intro_left=INTRO_TIME if network.enabled else 0.0
	model=Model.new();invalid=false;events.clear();active={};think_time=0;outcome_shown=false;report_time=0;audio.stop();board.event={};board.win_line=[];board.celebration=0;board.recent_cell=-1;board.match_win=false;board.loss_side=-1;loss_delay=0;scoreboard.reaction_time=0;refresh()
func next_round()->void:
	if not model.finished or busy() or loss_delay>0 or OnKlavGame.paused:return
	if network.enabled:
		if me==0:network.send("N")
	elif model.series_over():OnKlavGame.replay()
	else:apply_move(0,"N")
func snapshot() -> Dictionary:
	return {"game":"four","schema":1,"base":model.base,"tape":model.tape,"network":network.snapshot(),"friend":network.enabled,"host_dog":host_dog,"side_pending":side_picker.visible,"invalid":invalid}
func restore(value:Dictionary) -> bool:
	if value.get("game")!="four":return false
	var candidate=Model.new()
	if value.has("friend") and value.friend!=network.enabled:return false
	var saved_dog: Variant = value.get("host_dog",network.enabled and network.host_dog)
	var pending: Variant = value.get("side_pending",false)
	if not saved_dog is bool or not pending is bool: return false
	if network.enabled and (saved_dog!=host_dog or pending): return false
	if value.has("tape"):
		if not value.tape is Array or value.tape.size()>4096:return false
		if value.has("base"):
			if network.enabled and (not value.base is Array or value.base.any(func(n):return n!=0)):return false
			if not candidate.load_base(value.base):return false
		for move in value.tape:
			if not move is Dictionary or not move.get("data") is String:return false
			var who:Variant=move.get("actor")
			if not(who is int or who is float) or not is_finite(float(who)) or float(who)!=int(who) or int(who) not in [0,1] or not candidate.apply(int(who),move.data):return false
	else:
		if network.enabled or not candidate.load_base(value.get("board")):return false
		if not value.get("finished") is bool or not value.get("pending") is bool:return false
		if value.finished!=candidate.finished or value.pending!=(not candidate.finished and candidate.turn==1):return false
	if network.enabled:
		if not value.get("network") is Dictionary:return false
		var own_count:=0
		for move in candidate.tape:
			if int(move.actor)==me:own_count+=1
		if int(value.network.get("tx",-1))!=own_count or int(value.network.get("rx",-1))!=candidate.tape.size()-own_count:return false
		if not network.restore(value.network):return false
	if pending and candidate.board.any(func(cell): return cell!=0): return false
	host_dog=saved_dog
	side_picker.visible=pending
	intro_left=0.0
	model=candidate;invalid=value.get("invalid",false)==true;events.clear();active={};board.event={};think_time=.5 if not network.enabled and not model.finished and model.turn==1 else 0;outcome_shown=model.finished
	board.win_line=Model.line_for(model.board) if model.finished else [];board.celebration=10;report_time=0
	board.match_win=model.series_over()
	board.loss_side=(0 if is_cat(me) else 1) if model.finished and model.winner>=0 and model.winner!=me else -1
	loss_delay=0
	scoreboard.reaction_time=0
	board.recent_cell=-1
	if not model.tape.is_empty():
		var last:String=model.tape.back().data
		if last.begins_with("F"):
			var col:=int(last.substr(1))
			board.recent_cell=(model.row_for(col)+1)*7+col
	refresh();return true
func choose(col:int) -> void:
	if not can_play() or OnKlavGame.paused:return
	if col<0 or col>6:return
	if model.row_for(col)<0:
		board.denied=col;board.deny_time=.7;audio.play("land",is_cat(me));notify("This column is full","Choose another column",1.1);return
	board.hover=-1
	if network.enabled:network.send("F"+str(col));refresh()
	else:apply_move(me,"F"+str(col))
func apply_move(actor:int,data:String) -> void:
	if invalid:return
	if data=="N":
		if not model.apply(actor,data):invalid=true;refresh();return
		events.clear();active={};think_time=0;outcome_shown=false;report_time=0;board.event={};board.win_line=[];board.celebration=0;board.recent_cell=-1;board.match_win=false;board.loss_side=-1;loss_delay=0;scoreboard.reaction_time=0
		OnKlavGame.checkpoint();refresh();return
	var col:int=int(data.substr(1)) if data.begins_with("F") and data.substr(1).is_valid_int() else -1
	var row:=model.row_for(col)
	var blocked:bool=actor in [0,1] and col>=0 and model.immediate(1-actor)==col
	var prior_chain:int=model.best_chain(actor) if actor in [0,1] else 0
	if row<0 or not model.apply(actor,data):
		invalid=true;notify("Game update needs attention","Progress is saved · return to Games to retry",4);OnKlavGame.checkpoint();refresh();return
	var event:={"cell":row*7+col,"actor":actor,"age":0.0,"flight":.12 if board.reduced else .32+row*.051,"landed":false,"launched":false,"terminal":model.finished,"winner":model.winner,"chain":model.best_chain(actor),"prior_chain":prior_chain,"blocked":blocked}
	events.append(event);OnKlavGame.checkpoint()
	if active.is_empty():begin_move()
	refresh()
func begin_move() -> void:
	if events.is_empty():return
	active=events.pop_front();board.event=active;board.hover=-1
	if not OnKlavGame.paused:audio.play("drop",is_cat(int(active.actor)));active.launched=true
	refresh()
func shown_values() -> Array:
	var result:Array=model.board.duplicate()
	for event in events:result[int(event.cell)]=0
	if not active.is_empty() and not active.landed:result[int(active.cell)]=0
	return result
func finish_move() -> void:
	var terminal:bool=active.terminal;var winner:int=active.winner
	active={};board.event={}
	if not events.is_empty():begin_move()
	elif terminal and not outcome_shown:
		outcome_shown=true;board.win_line=Model.line_for(model.board);board.celebration=0;board.match_win=model.series_over()
		audio.play("draw" if winner<0 else "win" if winner==me else "lose",true,false,board.match_win)
		if winner>=0:
			if winner!=me:
				board.loss_side=0 if is_cat(me) else 1
				loss_delay=.65 if board.reduced else 1.08
				scoreboard.react(winner,"loss")
			else:scoreboard.react(winner,"result")
		if board.match_win:
			notify(match_result(),"Three rounds won",4.0,"match")
		elif winner>=0:
			notify(round_result(),"Four in a row",3.1,"round")
		else:notify("A perfect draw!","Good game · the board is full",2.5)
	elif not network.enabled and not model.finished and model.turn==1:think_time=maxf(.75,report_time+.08)
	refresh()
func round_result() -> String:
	return "%s wins round %d!"%[team(model.winner),model.round_id]
func match_result() -> String:
	return team(model.winner)+" wins the match!"
func refresh_status() -> void:
	if not is_instance_valid(status):return
	var ink:=Art.color(is_cat(int(active.actor) if not active.is_empty() else model.winner if model.finished and model.winner>=0 else model.turn))
	if model.finished and model.winner<0:ink=Color("d3e2f0")
	var line:=team(model.turn)+"’s turn"
	if not active.is_empty():line=team(int(active.actor))+"’s turn"
	elif model.finished and not busy():
		line="Draw · well played" if model.winner<0 else match_result() if model.series_over() else round_result()
	if network.enabled:
		if network.status=="ended":line="Match ended · return to Games"
		elif not network.error.is_empty():line="Delivery delayed · return to Games"
		elif network.pending or network.sending:line="Waiting for delivery…"
	if invalid:line="Game update needs attention"
	if status.text!=line or status_ink!=ink:
		status_age=0.0
		status_ink=ink
		status.text=line
		status.add_theme_color_override("font_color",ink.lightened(.20))
		status.add_theme_font_size_override("font_size",22 if line.length()>25 else 27)
func refresh() -> void:
	if not is_instance_valid(board):return
	board.values=shown_values();board.actor=model.turn;board.locked=not can_play();board.host_dog=host_dog;board.queue_redraw()
	scoreboard.match_wins=model.match_wins;scoreboard.round_id=model.round_id;scoreboard.values=board.values;scoreboard.me=me;scoreboard.host_dog=host_dog;scoreboard.turn=int(active.actor) if not active.is_empty() else model.turn;scoreboard.finished=model.finished and not busy();scoreboard.winner=model.winner;scoreboard.queue_redraw()
	new_button.text="Play again" if model.series_over() else "Next round"
	new_button.disabled=network.enabled and (me!=0 or network.pending or network.sending or network.status!="active")
	new_button.visible=model.finished and not busy() and loss_delay<=0
	footer_hint.visible=not new_button.visible
	refresh_status()
func draw_faceoff() -> void:
	var age:=INTRO_TIME-intro_left
	var center:=Vector2(canvas.size.x*.5,feedback_y())
	var grid_top:Vector2=canvas.get_global_transform_with_canvas().affine_inverse()*(board.get_global_transform_with_canvas()*board.grid().position)
	var art_height:=clampf((grid_top.y-center.y)*1.75,44,102)
	var image_size:=FACEOFF.get_size()
	var half:=image_size.x*.5
	var source_height:=image_size.y*.72
	var top:=image_size.y*.14
	var art_width:=art_height*half/source_height
	var reduced:bool=board.reduced
	var fade:=minf(1.0,intro_left/.30) if not reduced else 1.0
	var slide:=pow(1.0-clampf(age/INTRO_REVEAL,0,1),3.0)*canvas.size.x*.27 if not reduced else 0.0
	var spacing:=maxf(47,canvas.size.x*.25)
	var title_age:=age-INTRO_REVEAL
	if not reduced and title_age>=0 and title_age<.44:
		var sweep:=title_age/.44
		var x:=lerpf(center.x-68,center.x+68,sweep)
		var alpha:=sin(sweep*PI)*fade
		canvas.draw_line(Vector2(x-72,center.y+9),Vector2(x+48,center.y-10),Color(1,.46,.13,alpha*.30),13,true)
		canvas.draw_line(Vector2(x-64,center.y+8),Vector2(x+42,center.y-9),Color(1,.83,.44,alpha*.78),2,true)
	for side in [-1,1]:
		var x:float=center.x+side*(spacing+slide)
		var portrait:=Rect2(x-art_width*.5,center.y-art_height*.5,art_width,art_height)
		var source:=Rect2(0 if side<0 else half,top,half,source_height)
		var rim:=Art.ROSE if side<0 else Art.BLUE
		canvas.draw_texture_rect_region(FACEOFF,portrait.grow(1.4),source,Color(rim,.67*fade))
		canvas.draw_texture_rect_region(FACEOFF,portrait,source,Color(1,1,1,fade))
	if title_age<0 and not reduced:return
	var kick:=maxf(0,1-title_age/.22) if not reduced else 0.0
	var gold:=Color(1.0,.59,.13,fade)
	if not reduced and title_age<.4:
		var spark_alpha:=fade*(1-title_age/.4)
		for i in range(8):
			var direction:=Vector2.from_angle(TAU*(i+.5)/8)
			var tip:=center+direction*Vector2(54+title_age*110,12+title_age*36)
			canvas.draw_line(tip-direction*12,tip,Color(1,.6,.18,spark_alpha),2,true)
	var font:=fight_font
	var font_size:=roundi(clampf(art_height*.46,25,39)*(1+kick*.18))
	var width:=font.get_string_size("FIGHT!",HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
	var baseline:=Vector2(-width*.5,font.get_ascent(font_size)*.5)
	canvas.draw_set_transform_matrix(Transform2D(Vector2(1,-.13),Vector2(-.25,1),center))
	canvas.draw_string_outline(font,baseline+Vector2(3,5),"FIGHT!",HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,10,Color(.43,.10,.08,fade))
	canvas.draw_string_outline(font,baseline,"FIGHT!",HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,10,Color(.10,.04,.07,fade))
	canvas.draw_string_outline(font,baseline,"FIGHT!",HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,5,Color(1.0,.92,.64,fade))
	canvas.draw_string(font,baseline,"FIGHT!",HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,gold)
	canvas.draw_set_transform(Vector2.ZERO)

func draw_match_title() -> void:
	var center:=Vector2(canvas.size.x*.5,feedback_y())
	var fade:=clampf(minf(report_age/.26,report_time/.35),0,1)
	var motion:bool=not board.reduced
	var settle:=smoothstep(0.0,.42,report_age) if motion else 1.0
	var ink:=Color("ffc25d") if board.loss_side<0 else Color("9de7ff")
	var burst:=maxf(0,1-report_age/.95) if motion else 0.0
	if burst>0:
		for i in range(16):
			var direction:=Vector2.from_angle(i*TAU/16)
			var tip:=center+direction*Vector2(96+report_age*86,22+report_age*35)
			canvas.draw_line(tip-direction*18,tip,Color(ink,burst*.72),2.5,true)
	var scale_factor:=1.0+(1-settle)*.14
	var headline:=team(model.winner).to_upper()+" WINS"
	var font_size:=roundi(clampf(canvas.size.x*.126,34,49))
	var width:=fight_font.get_string_size(headline,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
	var baseline:=Vector2(-width*.5,-1)
	canvas.draw_set_transform_matrix(Transform2D(Vector2(scale_factor,-.08),Vector2(-.19,scale_factor),center+Vector2(0,(1-settle)*9)))
	canvas.draw_string_outline(fight_font,baseline+Vector2(3,5),headline,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,10,Color(.16,.09,.26,fade))
	canvas.draw_string_outline(fight_font,baseline,headline,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,9,Color(.035,.04,.08,fade))
	canvas.draw_string_outline(fight_font,baseline,headline,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,4,Color(1,1,.95,fade))
	canvas.draw_string(fight_font,baseline,headline,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,Color(ink,fade))
	canvas.draw_set_transform(Vector2.ZERO)
	var subtitle:="THE MATCH"
	var subtitle_width:=fight_font.get_string_size(subtitle,HORIZONTAL_ALIGNMENT_LEFT,-1,18).x
	var subtitle_at:=center+Vector2(-subtitle_width*.5,28)
	canvas.draw_string_outline(fight_font,subtitle_at,subtitle,HORIZONTAL_ALIGNMENT_LEFT,-1,18,5,Color(.04,.05,.11,fade))
	canvas.draw_string(fight_font,subtitle_at,subtitle,HORIZONTAL_ALIGNMENT_LEFT,-1,18,Color(.94,.97,1,fade))

func draw_sparks():
	if not is_instance_valid(status):return
	if intro_left>0:
		draw_faceoff()
		return
	if report_time>0 and report_kind=="match":
		draw_match_title()
		return
	var center:=Vector2(canvas.size.x*.5,feedback_y())
	var tint:=status_ink
	var span:=minf(136,canvas.size.x*.36)
	var y:=center.y+23
	for side in [-1.0,1.0]:
		canvas.draw_line(Vector2(center.x+side*12,y),Vector2(center.x+side*span,y),Color(tint,.25),1,true)
		canvas.draw_circle(Vector2(center.x+side*(span+6),y),2.4,Color(tint,.74))
	if board.reduced:return
	var age:=report_age if report_time>0 else status_age
	var fade:=maxf(0,1-age*1.8)
	if fade<=0:return
	for i in range(10):
		var direction:=Vector2.from_angle(TAU*i/10)
		var point:=center+direction*Vector2(88+age*65,22+age*32)
		canvas.draw_line(point-direction*3,point+direction*3,Color(tint,fade*.8),2,true)
func feedback_y()->float:
	var board_top:Vector2=canvas.get_global_transform_with_canvas().affine_inverse()*(board.get_global_transform_with_canvas()*board.grid().position)
	var score_bottom:Vector2=canvas.get_global_transform_with_canvas().affine_inverse()*(scoreboard.get_global_transform_with_canvas()*Vector2(0,scoreboard.size.y))
	return (score_bottom.y+board_top.y)*.5
func _process(delta:float) -> void:
	if side_picker.visible: return
	canvas.queue_redraw()
	if intro_left>0:
		intro_left=maxf(0,intro_left-delta)
		if intro_left==0:
			status_age=0.0
			refresh()
	status_age+=delta
	status.position=Vector2(10,feedback_y()-21);status.size=Vector2(canvas.size.x-20,42);status.visible=intro_left<=0 and report_time<=0
	status.pivot_offset=status.size*.5
	var reveal:=clampf(status_age/.32,0,1)
	status.scale=Vector2.ONE if board.reduced else Vector2.ONE*(.91+.09*sin(reveal*PI*.5))
	status.modulate.a=1.0 if board.reduced else .58+.42*reveal

	if not is_instance_valid(board):return
	if not active.is_empty():
		if not active.launched:audio.play("drop",is_cat(int(active.actor)));active.launched=true
		active.age=float(active.age)+delta
		if not active.landed and float(active.age)>=float(active.flight):
			var actor:int=active.actor
			active.landed=true
			board.recent_cell=int(active.cell)
			audio.play("land",is_cat(actor),int(active.cell)<35)
			refresh()
			if not active.terminal:
				if active.blocked:
					scoreboard.react(actor,"block")
					notify("Threat blocked",team(actor)+" stopped a winning drop",1.65)
				elif active.chain>=3 and active.prior_chain<3:
					scoreboard.react(actor,"chain")
					notify("Three connected!",team(actor)+" is building a line",1.65)
		if float(active.age)>=float(active.flight)+(.22 if board.reduced else .50):finish_move()
	elif think_time>0:
		think_time-=delta
		if think_time<=0 and not model.finished and model.turn==1 and not invalid:
			var col:=model.choose_ai()
			if col>=0:apply_move(1,"F"+str(col))
	if loss_delay>0:
		loss_delay=maxf(0,loss_delay-delta)
		if loss_delay==0:refresh()
	if report_time>0:
		report_time=maxf(0,report_time-delta);report_age+=delta
		report.visible=report_time>0 and report_kind!="match"
		var width:=minf(340,canvas.size.x-32);report.custom_minimum_size.x=width;report.size=Vector2(width,report.get_combined_minimum_size().y)
		report.position=Vector2((canvas.size.x-width)/2,feedback_y()-report.size.y/2-(8 if report_kind=="round" else 0));report.modulate.a=minf(1,minf(report_age/.13,report_time/.22))
		report.pivot_offset=report.size*.5
		if board.reduced:report.scale=Vector2.ONE
		elif board.loss_side>=0:report.scale=Vector2.ONE*(.96+.04*clampf(report_age/.28,0,1))
		else:report.scale=Vector2.ONE*(1+.16*exp(-report_age*8)*sin(report_age*20))
	else:report.hide()
