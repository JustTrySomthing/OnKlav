extends Control
const Layout=preload("res://dominos/layout.gd")
const Model=preload("res://dominos/tournament.gd")
const Art=preload("res://dominos/art.gd")
const Tile=preload("res://dominos/tile.gd")
const Table=preload("res://dominos/table.gd")
const Score=preload("res://dominos/scoreboard.gd")
const Audio=preload("res://dominos/audio.gd")
const Chrome=preload("res://game_chrome.gd")
const SCORE_TITLE := "How to score"
const SCORE_COPY := "Get a point for every dot on the domino's you play. Any dominos you still have at the end of a round are deducted from your score."
const WIN_TITLE := "How to win the game"
const WIN_COPY := "The highest total score at the end of 3 rounds wins."
const HELP := SCORE_TITLE+"\n"+SCORE_COPY+"\n\n"+WIN_TITLE+"\n"+WIN_COPY+"\n\nYou and your rival receive seven tiles from a double-six set. The highest double in either hand opens each round. If neither hand has a double, the highest total opens. The opening tile glows in its owner's hand.\n\nTap a glowing tile: an opening move or a tile with one matching end plays immediately. If both different ends match, choose the cyan or pink end. You can also drag a tile onto its glowing end; release outside the table to cancel. Tiles orient automatically. Every tile in your hand stays visible in a wrapping rack, and newly drawn tiles glow gold. The two-ended line has no spinners; blank counts as zero.\n\nIf you have no match, draw one tile at a time until you can play. You may pass only when the draw pile is empty and you cannot play. If both players pass, the round is blocked.\n\nA round ends when either hand is empty or both players pass. The higher score after deductions wins that round. Equal scores draw, including an equal match total. The scoreboard shows this round’s score and the total from completed rounds.\n\nThe shared toolbar handles Pause, save/return, Help, Sound/Music, Settings and confirmed Quit. Reduced motion removes tile travel, pulses and confetti. No bets, currency, advertisements or purchases."
var model=Model.new()
var intro: ColorRect
var audio
var canvas:Control
var table
var fx:Control
var score
var hand:Control
var rack_label:Label
var status:Label
var detail:Label
var left_button:Button
var right_button:Button
var draw_button:Button
var next_button:Button
var selected:=-1
var busy:=0.0
var bot_wait:=.9
var feedback_age:=0.0
var feedback_time:=0.0
var feedback_total:=0.0
var clock:=0.0
var deal_age:=0.0
var reduced:=false
var flying:Dictionary={}
var rack_key:=""
var fresh_id:=-1
var fresh_time:=0.0
var drag:Dictionary={}
var rack_layout_key:=""
var rack_motion:Dictionary={}
var rack_motion_age:=1.0
func _ready():
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	canvas=Control.new();canvas.draw.connect(paint);add_child(canvas)
	score=Score.new();canvas.add_child(score)
	table=Table.new();canvas.add_child(table);table.end_chosen.connect(play_end)
	status=Chrome.caption("",23);status.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;status.mouse_filter=Control.MOUSE_FILTER_IGNORE;canvas.add_child(status)
	detail=Chrome.caption("",13);detail.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;detail.mouse_filter=Control.MOUSE_FILTER_IGNORE;detail.add_theme_color_override("font_color",Color("a6bbd4"));canvas.add_child(detail)
	status.add_theme_color_override("font_outline_color",Color("070c20"));status.add_theme_constant_override("outline_size",4);status.add_theme_color_override("font_shadow_color",Color("020510"));status.add_theme_constant_override("shadow_offset_y",2)
	rack_label=Chrome.caption("YOUR HAND",12);rack_label.add_theme_color_override("font_color",Art.CYAN);canvas.add_child(rack_label)
	hand=Control.new();hand.mouse_filter=Control.MOUSE_FILTER_IGNORE;canvas.add_child(hand)
	left_button=target_button(func():play_end(0));canvas.add_child(left_button)
	right_button=target_button(func():play_end(1));canvas.add_child(right_button)
	draw_button=button("Draw",draw_tile,"173d49",Art.CYAN);canvas.add_child(draw_button)
	next_button=button("Next round",advance,"3c3464",Art.GOLD);canvas.add_child(next_button)
	fx=Control.new();fx.mouse_filter=Control.MOUSE_FILTER_IGNORE;fx.draw.connect(paint_flight);canvas.add_child(fx)
	audio=Audio.new();add_child(audio)
	make_intro()
	resized.connect(layout)
	if OS.has_feature("web"): get_viewport().size_changed.connect(layout, CONNECT_DEFERRED)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,HELP,"user://builtin-dominos.json",{}, {"is_resumable":func(_state:Dictionary):return model.phase!="match_over"})
	OnKlavGame.suspended.connect(cancel_drag)
	OnKlavGame.chrome.preferences_changed.connect(preferences);preferences();layout();refresh()
func make_intro() -> void:
	intro = ColorRect.new()
	intro.color = Color("070c20da")
	intro.hide()
	canvas.add_child(intro)
	intro.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var center := CenterContainer.new()
	intro.add_child(center)
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel",Chrome.box("132239","59c8cb",16))
	center.add_child(panel)
	var margin := MarginContainer.new()
	for side in ["left","right","top","bottom"]:
		margin.add_theme_constant_override("margin_"+side,18)
	panel.add_child(margin)
	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation",12)
	margin.add_child(column)
	var title := Chrome.caption("How to play", 24)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_color_override("font_color", Art.CYAN)
	column.add_child(title)

	for rule in [
		["Match an end", "Tap a glowing tile. If nothing matches, draw."],
		[SCORE_TITLE, SCORE_COPY],
		[WIN_TITLE, WIN_COPY]
	]:
		var heading := Chrome.caption(rule[0], 17)
		heading.add_theme_color_override("font_color", Art.GOLD)
		var copy := Chrome.caption(rule[1], 15)
		copy.add_theme_color_override("font_color", Color("d2dfef"))
		var group := VBoxContainer.new()
		group.add_theme_constant_override("separation", 2)
		group.add_child(heading)
		group.add_child(copy)
		column.add_child(group)

	var fit := func(): column.custom_minimum_size.x = minf(340.0, maxf(180.0, intro.size.x - 84.0))
	intro.resized.connect(fit)
	fit.call()
	column.add_child(button("Play",func():
		intro.hide()
		OnKlavGame.checkpoint()
		refresh(),"17604d",Color("edfff4")))

func button(value:String,callback:Callable,fill:String,ink:Color)->Button:
	var b:=Chrome.action(value,callback);b.size_flags_horizontal=Control.SIZE_EXPAND_FILL;b.add_theme_font_size_override("font_size",15)
	b.add_theme_stylebox_override("normal",Chrome.box(fill,Color(ink,.65).to_html(),14));b.add_theme_stylebox_override("hover",Chrome.box(Color(fill).lightened(.15).to_html(),ink.to_html(),14));b.add_theme_color_override("font_color",ink);return b
func target_button(callback:Callable)->Button:
	var b:=Button.new();b.mouse_default_cursor_shape=Control.CURSOR_POINTING_HAND
	for style in ["normal","hover","pressed","disabled"]:b.add_theme_stylebox_override(style,StyleBoxEmpty.new())
	var focus:=StyleBoxFlat.new();focus.bg_color=Color(Art.CYAN,.12);focus.border_color=Art.CYAN;focus.set_border_width_all(2);focus.set_corner_radius_all(8);b.add_theme_stylebox_override("focus",focus);b.pressed.connect(callback);return b
func preferences():
	reduced=OnKlavGame.chrome.reduce_motion;table.reduced=reduced;score.reduced=reduced
	for tile in hand.get_children():tile.reduced=reduced
func layout():
	if not drag.is_empty(): cancel_drag()
	Chrome.fit_board(canvas, size, 56, 520)
	var width := canvas.size.x
	score.position=Vector2(12,3);score.size=Vector2(width-24,75)
	status.position=Vector2(8,82);status.size=Vector2(width-16,28);status.pivot_offset=status.size*.5
	detail.position=Vector2(8,112);detail.size=Vector2(width-16,18)
	var arrangement:=Layout.screen(width,canvas.size.y,model.hands[0].size(),model.phase!="playing")
	table.position=arrangement.table.position;table.size=arrangement.table.size
	hand.position=arrangement.hand.position;hand.size=arrangement.hand.size
	rack_label.position=arrangement.rack_label.position;rack_label.size=arrangement.rack_label.size
	draw_button.position=arrangement.draw.position;draw_button.size=arrangement.draw.size
	next_button.position=arrangement.next.position;next_button.size=arrangement.next.size
	place_rack(false);fit_end_buttons();canvas.queue_redraw()
func fit_end_buttons():
	left_button.position=table.position+table.end_box(0).position;left_button.size=table.end_box(0).size
	right_button.position=table.position+table.end_box(1).position;right_button.size=table.end_box(1).size
func place_rack(animate:bool):
	var previous:Dictionary={}
	for tile in hand.get_children():previous[tile.tile_id]=Rect2(tile.position,tile.size)
	var key:="%d:%d:%s"%[roundi(canvas.size.x),model.hands[0].size(),model.phase]
	if animate and key!=rack_layout_key:rack_motion=previous;rack_motion_age=0
	rack_layout_key=key
	for i in range(hand.get_child_count()):
		var tile=hand.get_child(i);var target:=Layout.rack_rect(i,model.hands[0].size(),canvas.size.x,model.phase!="playing")
		if reduced or not animate or not rack_motion.has(tile.tile_id):tile.position=target.position;tile.size=target.size
		else:
			var t:=1-pow(1-minf(1,rack_motion_age/.25),3);tile.position=rack_motion[tile.tile_id].position.lerp(target.position,t);tile.size=rack_motion[tile.tile_id].size.lerp(target.size,t)
func reset_rack():
	for child in hand.get_children():hand.remove_child(child);child.queue_free()
	rack_key=""
func start_new():
	reset_rack()
	model=Model.new();model.begin(int(Time.get_unix_time_from_system()*1000)%2147483646+1)
	intro.show()
	selected=-1;fresh_id=-1;fresh_time=0;drag={};busy=1.0;bot_wait=1.1;deal_age=0;flying={};rack_key="";score.reset([0,0]);audio.stop();layout();refresh()
func can_play()->bool:return not intro.visible and model.phase=="playing" and model.turn==0 and busy<=0 and not OnKlavGame.paused
func announce(title:String,subtitle:String,duration:float,ink:Color):
	status.text=title;detail.text=subtitle;status.add_theme_color_override("font_color",ink);feedback_time=duration;feedback_total=duration;feedback_age=0
	# A tap needs no second control when the intended end is unambiguous.
func select_tile(id:int):
	if not can_play() or not drag.is_empty():return
	var ends:=valid_ends(id)
	if ends.is_empty():audio.play("deny");announce("NO MATCH","Match "+ends_text(),1.1,Art.PINK);return
	selected=id;audio.play("select");feedback_time=0
	if ends.size()==1 or model.chain.is_empty() or model.chain[0][1]==model.chain[-1][2]:play_end(ends[0])
	else:refresh()
func valid_ends(id:int)->Array:
	var result:Array=[]
	if id not in model.hands[0]:return result
	for end in range(2):
		if model.legal(id,end):result.append(end)
	return result
func begin_drag(id:int,point:Vector2):
	if not can_play() or not drag.is_empty() or valid_ends(id).is_empty():return
	selected=id;feedback_time=0;drag={"id":id,"point":canvas.get_global_transform_with_canvas().affine_inverse()*point};refresh();move_drag(point)
func move_drag(point:Vector2):
	if drag.is_empty():return
	drag.point=canvas.get_global_transform_with_canvas().affine_inverse()*point;table.hover_end=table.end_at(drag.point-table.position);fx.queue_redraw()
func finish_drag(point:Vector2,canceled:bool):
	if drag.is_empty():return
	var id:int=drag.id;var local:Vector2=canvas.get_global_transform_with_canvas().affine_inverse()*point;var target:int=table.end_at(local-table.position)
	var ends:=valid_ends(id)
	if not canceled and target<0 and Rect2(table.position,table.size).has_point(local) and (ends.size()==1 or model.chain.is_empty() or model.chain[0][1]==model.chain[-1][2]):target=ends[0]
	drag={};table.hover_end=-1
	if canceled or target<0:selected=-1;refresh();return
	selected=id;play_end(target,local-Vector2(0,25))
func cancel_drag():
	drag={};table.hover_end=-1
	for tile in hand.get_children():tile.cancel_pointer()
	refresh()
func ends_text()->String:
	if model.chain.is_empty():
		var pair:=Model.faces(model.required);return "%d | %d to open"%[pair.x,pair.y]
	return "%d or %d"%[model.chain[0][1],model.chain[-1][2]]
func play_end(end:int,from_point:Vector2=Vector2.INF):
	if not can_play() or not drag.is_empty() or selected<0 or not model.legal(selected,end):return
	var from:=Vector2(canvas.size.x*.5,hand.position.y+hand.size.y*.5)
	for node in hand.get_children():
		if node.tile_id==selected:from=canvas.get_global_transform_with_canvas().affine_inverse()*(node.get_global_transform_with_canvas()*(node.size*.5))
	if from_point.is_finite():from=from_point
	perform("play",selected,end,from)
	if from_point.is_finite() and not flying.is_empty():flying.duration=.1 if reduced else .24
func draw_tile():
	if not can_play():return
	perform("draw" if not model.stock.is_empty() else "pass")
func perform(kind:String,id:int=-1,end:int=0,from:Vector2=Vector2.ZERO):
	var actor:int=model.turn
	var previous_positions:Dictionary=table.current_rects()
	var previous_rack:Dictionary={}
	for node in hand.get_children():previous_rack[node.tile_id]=Rect2(hand.position+node.position,node.size)
	if not model.apply(actor,kind,id,end):return
	if kind=="draw" and actor==0:fresh_id=int(model.event.id);fresh_time=4.0
	elif kind=="play" and id==fresh_id:fresh_id=-1;fresh_time=0
	selected=-1;bot_wait=.7;busy=.22 if reduced else .52;flying={}
	# Model state is durable before effects. Restoring adopts the committed turn without replay.
	OnKlavGame.checkpoint();refresh();table.reflow(previous_positions)
	rack_motion={}
	for id_key in previous_rack:rack_motion[id_key]=Rect2(previous_rack[id_key].position-hand.position,previous_rack[id_key].size)
	rack_motion_age=0;place_rack(true)
	if kind=="play":
		var index:int=0 if end==0 else model.chain.size()-1;var tile:Array=model.chain[index]
		table.hidden_id=id
		flying={"kind":"play","id":id,"a":tile[1],"b":tile[2],"from":from if actor==0 else Vector2(canvas.size.x-74,48),"index":index,"actor":actor,"age":0.0,"duration":.10 if reduced else .44,"landed":false}
		refresh();audio.play("slide");announce("NICE CONNECTION" if actor==0 else "YOUR RIVAL PLAYS","%d | %d · "%[tile[1],tile[2]]+tiles_left(model.hands[actor].size()),1.3,Art.CYAN if actor==0 else Art.PINK)
	elif kind=="draw":
		audio.play("draw");announce("FRESH POSSIBILITIES" if actor==0 else "YOUR RIVAL DRAWS","You drew %d | %d"%[Model.faces(int(model.event.id)).x,Model.faces(int(model.event.id)).y] if actor==0 else "The draw pile has "+tiles_left(model.stock.size()),1.0,Art.CYAN if actor==0 else Art.PINK)
		if actor==0:
			fresh_id=int(model.event.id);fresh_time=4.0
			for node in hand.get_children():
				if node.tile_id==int(model.event.id):node.concealed=true
		var pair:=Model.faces(int(model.event.id))
		flying={"kind":"draw","id":int(model.event.id),"a":pair.x,"b":pair.y,"from":draw_button.get_rect().get_center(),"actor":actor,"age":0.0,"duration":.10 if reduced else .36,"landed":false}
	else:audio.play("pass");announce("YOU PASS" if actor==0 else "YOUR RIVAL PASSES","No match · the draw pile is empty",1.5,Art.GOLD);busy=1.0
	if model.phase!="playing":busy=1.3 if reduced else 1.8
func tiles_left(count:int)->String:return "%d %s left"%[count,"tile" if count==1 else "tiles"]
func outcome_side() -> int:
	return Model.pip_winner(model.match_totals()) if model.phase=="match_over" else model.round_winner

func deduction_text() -> String:
	return "Pips deducted · You %d · Rival %d"%[model.deductions[0],model.deductions[1]]

func show_outcome():
	var side := outcome_side()
	var title := "MATCH DRAWN" if side<0 else "YOU WIN THE MATCH" if side==0 else "RIVAL WINS THE MATCH"
	if model.phase!="match_over":
		title = "ROUND DRAWN" if side<0 else "YOU WIN THE ROUND" if side==0 else "RIVAL WINS THE ROUND"
	var ink := Art.GOLD if side==0 else Art.PINK if side==1 else Art.CYAN
	announce(title,deduction_text(),3.8,ink)
	audio.play("win" if side==0 else "round")
	table.burst(table.space().get_center(),ink,true)
func advance():
	if OnKlavGame.paused or busy>0:return
	if model.phase=="match_over":OnKlavGame.replay();return
	if not model.next_round():return
	reset_rack();busy=1.1;bot_wait=1.0;deal_age=0;selected=-1;fresh_id=-1;fresh_time=0;flying={};table.hidden_id=-1;rack_key="";feedback_time=0;score.reset([0,0]);OnKlavGame.checkpoint();refresh();announce("ROUND %02d"%model.round_id,"A fresh deal · highest total after 3 rounds",1.6,Art.CYAN)
func refresh():
	audio.music()
	score.scores=model.pip_scores.duplicate();score.totals=model.match_totals();score.round_id=model.round_id;score.turn=model.turn;score.phase=model.phase
	if flying.get("kind")=="play" and not flying.landed:
		score.scores[flying.actor]-=int(flying.a)+int(flying.b)
	var side := outcome_side()
	table.final_ink=Art.GOLD if side==0 else Art.PINK if side==1 else Art.CYAN;table.chain=model.chain;table.stock_count=model.stock.size();table.left_ok=can_play() and selected>=0 and model.legal(selected,0);table.right_ok=can_play() and selected>=0 and model.legal(selected,1)
	table.final_text=("YOU WIN" if side==0 else "RIVAL WINS" if side==1 else "MATCH DRAWN") if model.phase=="match_over" else ""
	table.final_detail="FINAL SCORE  ·  %d — %d"%score.totals
	var key:=str(model.hands[0])
	if key!=rack_key:
		rack_key=key
		var existing:Dictionary={}
		for child in hand.get_children():
			if child.tile_id not in model.hands[0]:hand.remove_child(child);child.queue_free()
			else:existing[child.tile_id]=child
		for i in range(model.hands[0].size()):
			var id:int=model.hands[0][i]
			var tile=existing.get(id)
			if tile==null:
				tile=Tile.new();tile.tile_id=id;tile.reduced=reduced;tile.arrival=1 if deal_age>5 else -i*.09 if deal_age<.5 else 0;tile.chosen.connect(select_tile);tile.drag_started.connect(begin_drag);tile.drag_moved.connect(move_drag);tile.drag_finished.connect(finish_drag);hand.add_child(tile)
			hand.move_child(tile,i)
		layout()
	for node in hand.get_children():
		node.fresh=node.tile_id==fresh_id and fresh_time>0;node.selected=node.tile_id==selected;node.playable=model.phase=="playing" and (model.legal(node.tile_id,0) or model.legal(node.tile_id,1));node.disabled=not can_play() or (not drag.is_empty() and node.tile_id!=int(drag.id))
	rack_label.text="YOUR HAND  ·  %d %s"%[model.hands[0].size(),"TILE" if model.hands[0].size()==1 else "TILES"] if model.phase=="playing" else "TABLE BLOCKED" if model.blocked else "HAND COMPLETE"
	fit_end_buttons()
	left_button.accessibility_name="Play at left end";right_button.accessibility_name="Play at right end"
	left_button.visible=table.left_ok and drag.is_empty();right_button.visible=table.right_ok and drag.is_empty()
	left_button.disabled=not table.left_ok;right_button.disabled=not table.right_ok
	draw_button.text="Draw · %d"%model.stock.size() if not model.stock.is_empty() else "Pass"
	draw_button.visible=model.phase=="playing" and model.turn==0 and model.moves(0).is_empty()
	draw_button.disabled=not can_play() or not drag.is_empty()
	next_button.visible=model.phase!="playing";next_button.text="Play again" if model.phase=="match_over" else "Next round";next_button.disabled=busy>0 or OnKlavGame.paused
	if feedback_time<=0:
		status.scale=Vector2.ONE;status.modulate.a=1
		status.text="MATCH COMPLETE" if model.phase=="match_over" else ("ROUND DRAWN" if model.round_winner<0 else "YOU WIN THE ROUND" if model.round_winner==0 else "RIVAL WINS THE ROUND") if model.phase=="round_over" else "RIVAL’S TURN" if model.turn==1 else "CHOOSE AN END" if selected>=0 else "YOUR MOVE"
		detail.text=deduction_text() if model.phase!="playing" else "Your rival is planning a connection…" if model.turn==1 else "Release on a glowing end" if not drag.is_empty() else "Choose the cyan or pink end" if selected>=0 else "Match "+ends_text() if not model.moves(0).is_empty() else "No match · draw a tile" if not model.stock.is_empty() else "No match · pass to your rival"
		status.add_theme_color_override("font_color",(Art.GOLD if model.round_winner==0 else Art.PINK if model.round_winner==1 else Art.CYAN) if model.phase!="playing" else Art.PINK if model.turn==1 else Art.CYAN)
	place_rack(true);canvas.queue_redraw();fx.queue_redraw()
func _process(dt:float):
	if intro.visible: return
	clock+=dt;deal_age+=dt;rack_motion_age=minf(1,rack_motion_age+dt);fresh_time=maxf(0,fresh_time-dt)
	var previous_busy:=busy;busy=maxf(0,busy-dt)
	if not flying.is_empty():
		flying.age+=dt
		if not flying.landed and flying.age>=flying.duration:
			flying.landed=true;table.hidden_id=-1;audio.play("land")
			if flying.kind=="play":
				var ink:Color=Art.CYAN if flying.actor==0 else Art.PINK
				table.burst(table.tile_rect(flying.index).get_center(),ink)
				score.show_gain(flying.actor,int(flying.a)+int(flying.b))
			else:
				for node in hand.get_children():
					if node.tile_id==int(flying.id):node.concealed=false;node.arrival=.35
		if flying.age>flying.duration+.12:flying={}
	if previous_busy>0 and busy<=0 and model.phase!="playing":show_outcome()
	if feedback_time>0:
		feedback_time=maxf(0,feedback_time-dt);feedback_age+=dt
		status.scale=Vector2.ONE*(1 if reduced else 1+.1*exp(-feedback_age*7)*sin(feedback_age*19));status.modulate.a=minf(1,feedback_age/.12)
	if model.phase=="playing" and model.turn==1 and busy<=0:
		bot_wait-=dt
		if bot_wait<=0:
			var move:Array=model.choose_ai()
			if not move.is_empty():perform("play",move[0],move[1])
			else:perform("draw" if not model.stock.is_empty() else "pass")
	refresh()
func paint():
	for i in range(32):canvas.draw_rect(Rect2(0,canvas.size.y*i/32,canvas.size.x,canvas.size.y/32+1),Color("090d20").lerp(Color("08182a"),float(i)/31))
	for i in range(15):
		var x:float=fmod(i*67.7+sin(clock*.15+i)*10,canvas.size.x);var y:float=fmod(i*91.3+clock*2,canvas.size.y)
		if not reduced:canvas.draw_circle(Vector2(x,y),1.1,Color(Art.PINK,.12))
func paint_flight():
	if not drag.is_empty():
		var pair:=Model.faces(int(drag.id));var extent:=Vector2(84,45);var point:Vector2=drag.point-Vector2(0,25)
		Art.tile(fx,Rect2(point-extent*.5,extent),pair.x,pair.y,Art.CYAN,false,false,true)

	if not flying.is_empty() and not flying.landed:
		var t:float=clampf(flying.age/flying.duration,0,1);var target:=Rect2(Vector2(canvas.size.x-97,35),Vector2(46,26));var dest:Vector2=target.get_center()
		if flying.kind=="play":target=table.tile_rect(flying.index);dest=table.position+target.get_center()
		elif flying.actor==0:
			for node in hand.get_children():
				if node.tile_id==int(flying.id):dest=canvas.get_global_transform_with_canvas().affine_inverse()*(node.get_global_transform_with_canvas()*(node.size*.5));target.size=node.size-Vector2(6,8)
		var point:Vector2=dest if reduced else flying.from.lerp(dest,1-pow(1-t,3))
		if not reduced:point.y-=sin(t*PI)*38
		if not reduced:
			var ink:Color=Art.CYAN if flying.actor==0 else Art.PINK
			var previous:=point
			for i in range(1,9):
				var age:float=maxf(0,t-i*.018)
				var tail:Vector2=flying.from.lerp(dest,1-pow(1-age,3));tail.y-=sin(age*PI)*38
				fx.draw_line(previous,tail,Color(ink,(1-float(i)/9)*.18),7,true)
				fx.draw_line(previous,tail,Color(ink,(1-float(i)/9)*.6),1.5,true);previous=tail
		var extent:Vector2=target.size if reduced else Vector2(68,36).lerp(target.size,t)
		var angle:float=table.target_angle(flying.index) if flying.kind=="play" else 0
		fx.draw_set_transform(point,angle if reduced else lerp_angle(0,angle,t),Vector2.ONE)
		Art.tile(fx,Rect2(-extent*.5,extent),flying.a,flying.b,Art.CYAN if flying.actor==0 else Art.PINK,false,flying.kind=="draw" and flying.actor==1,true)
		fx.draw_set_transform(Vector2.ZERO)
func snapshot()->Dictionary:return {"game":"dominos","schema":1,"model":model.snapshot(),"intro_pending":intro.visible,"recent_draw":fresh_id if fresh_time>0 and fresh_id in model.hands[0] else -1}
func restore(d:Dictionary)->bool:
	if d.get("game")!="dominos" or not Model.integer(d.get("schema"),1,1) or not d.get("model")is Dictionary:return false
	var candidate=Model.new()
	if not candidate.restore(d.model) or not d.get("intro_pending",false) is bool:return false
	intro.visible=d.get("intro_pending",false)
	reset_rack();model=candidate;selected=-1;busy=0;bot_wait=.8;flying={};table.hidden_id=-1;feedback_time=0;deal_age=10;fresh_id=int(d.get("recent_draw",-1)) if Model.integer(d.get("recent_draw",-1),0,27) and int(d.get("recent_draw",-1))in model.hands[0] else -1;fresh_time=4 if fresh_id>=0 else 0;drag={};rack_key="";score.reset(model.pip_scores);layout();refresh();return true
