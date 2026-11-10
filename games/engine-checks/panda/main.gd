extends Control
const Duel=preload("res://panda/duel.gd")
const Arena=preload("res://panda/battle_arena.gd")
const FlightClock=preload("res://panda/flight_clock.gd")
const Hud=preload("res://panda/battle_hud.gd")
const Audio=preload("res://panda/audio.gd")
const Chrome=preload("res://game_chrome.gd")
const BACKGROUNDS:=[preload("res://panda/art/background.webp"),preload("res://panda/art/background2.webp"),preload("res://panda/art/background3.webp")]
const HELP:="CRASH TEST PANDA · SOLO CANNON DUEL\n\nPull down from the white circle, aim sideways, then release. More pull means more power. A short tap or a release above the starting point cancels. There is no aiming timer.\n\nTake all three of your shots first, then Red Panda takes all three rival shots. Each side has an identical, prebuilt tower and keeps its own damage between shots. Earned bonus pandas belong to the same volley. After a shot, watch the collapse and the score report; your next shot follows automatically. No building phase and no power buttons.\n\nWood is sturdy, bamboo is springy, ice-blue glass shatters easily, and purple stone is heavy. Break the lower supports to start a chain reaction. Toppled or shattered pieces score 10 points, with bigger chain bonuses as the same shot topples more pieces. Orange fuse crates can blast neighboring blocks; the final courses can award one extra panda.\n\nA small golden duck is a rare, random bonus visitor in the top quarter of the arena. Hit it in flight for 35 points. It makes one brief pass, then leaves; you never need to wait for one.\n\nTwelve courses cross three environments, with new platform and block variations each campaign. The higher round score wins that round. Round stars measure how much of your tower you demolished; three stars also require a round win. Highest total score wins the campaign. You keep going even after a lost round. Your best score and course stars are retained separately from the resumable game.\n\nThe shared top bar provides Pause, Help, Sound/Music, Settings, save/return and confirmed Quit. Pause preserves this campaign; Quit discards it. Reduced motion removes shake, extra particles and large transitions. No ads, purchases or fake currency."
var model
var arena
var hud
var audio
var canvas:Control
var backdrop:TextureRect
var stage_label:Label
var turn_label:Label
var report:VBoxContainer
var report_title:Label
var report_detail:Label
var result:VBoxContainer
var result_title:Label
var result_detail:Label
var rating:Control
var controls:Control
var next_button:Button
var flight_clock=FlightClock.new()
var frame_usec:=0
var hold:=0.0
var announce_age:=0.0
var announce_total:=0.0
var bot_age:=0.0
var ai_target:=Vector2i(0,65)
var ai_ready:=false
var last_phase:=""
var last_stage:=-1
var last_actor:=-1
var combo_level:=0
var profile:Dictionary={"schema":1,"best_score":0,"stars":[0,0,0,0,0,0,0,0,0,0,0,0]}
var profile_ready:=false
var profile_dirty:=false
var profile_busy:=false
var profile_loading:=false
func _ready():
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	audio=Audio.new();add_child(audio)
	canvas=Control.new();add_child(canvas)
	backdrop=TextureRect.new();backdrop.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);backdrop.expand_mode=TextureRect.EXPAND_IGNORE_SIZE;backdrop.stretch_mode=TextureRect.STRETCH_KEEP_ASPECT_COVERED;backdrop.mouse_filter=Control.MOUSE_FILTER_IGNORE;canvas.add_child(backdrop)
	var tint:=ColorRect.new();tint.color=Color("12384220");tint.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);tint.mouse_filter=Control.MOUSE_FILTER_IGNORE;canvas.add_child(tint)
	var col:=VBoxContainer.new();col.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT);col.offset_left=10;col.offset_right=-10;col.offset_top=7;col.offset_bottom=-10;col.add_theme_constant_override("separation",4);canvas.add_child(col)
	hud=Hud.new();hud.custom_minimum_size.y=64;col.add_child(hud)
	stage_label=label("",13,Color("fff1cc"));stage_label.custom_minimum_size.y=0;stage_label.hide();col.add_child(stage_label)
	arena=Arena.new();arena.clip_contents=true;arena.size_flags_vertical=Control.SIZE_EXPAND_FILL;col.add_child(arena)
	arena.fired.connect(shoot);arena.pulled.connect(func():audio.play("pull"));arena.resized.connect(arena.cancel)
	turn_label=label("",21,Color.WHITE);turn_label.custom_minimum_size.y=32;col.add_child(turn_label)
	turn_label.add_theme_color_override("font_shadow_color",Color("081725"))
	turn_label.add_theme_constant_override("shadow_offset_x",2)
	turn_label.add_theme_constant_override("shadow_offset_y",3)
	controls=Control.new();controls.custom_minimum_size.y=50;col.add_child(controls)
	next_button=Chrome.action("Continue",advance);next_button.set_anchors_and_offsets_preset(Control.PRESET_CENTER);next_button.offset_left=-88;next_button.offset_right=88;next_button.offset_top=-23;next_button.offset_bottom=23;next_button.add_theme_stylebox_override("normal",Chrome.box("23845c","9af1b6",15));next_button.add_theme_stylebox_override("hover",Chrome.box("379c6c","dcffe5",15));next_button.add_theme_color_override("font_color",Color.WHITE);controls.add_child(next_button)
	report=VBoxContainer.new();report.mouse_filter=Control.MOUSE_FILTER_IGNORE;report.add_theme_constant_override("separation",4);canvas.add_child(report)
	report_title=label("",29,Color("ffe199"));report.add_child(report_title)
	report_detail=label("",23,Color("fff9e7"));report.add_child(report_detail);report.hide()
	result=VBoxContainer.new();result.mouse_filter=Control.MOUSE_FILTER_IGNORE;result.add_theme_constant_override("separation",12);canvas.add_child(result)
	result_title=label("",31,Color("ffe094"));result.add_child(result_title)
	rating=Control.new();rating.custom_minimum_size.y=68;rating.draw.connect(draw_rating);result.add_child(rating)
	result_detail=label("",17,Color("fff9e7"));result.add_child(result_detail);result.hide()
	resized.connect(layout)
	if OS.has_feature("web"): get_viewport().size_changed.connect(layout, CONNECT_DEFERRED)
	OnKlavGame.attach_session(self,snapshot,restore,start_new,HELP,"user://builtin-panda.json",{}, {"format_version":4,"is_resumable":func(data:Dictionary):return data.model.phase!="finished"})
	OnKlavGame.resumed.connect(reset_frame_clock)
	OnKlavGame.suspended.connect(func():
		reset_frame_clock()
		arena.cancel()
		if profile_ready:profile_update()
		elif not profile_loading:load_profile()
		OnKlavGame.checkpoint())
	OnKlavGame.chrome.preferences_changed.connect(preferences);preferences();layout();refresh()
	load_profile()
func label(value:String,font_size:int,ink:Color)->Label:
	var l:=Chrome.caption(value,font_size);l.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;l.mouse_filter=Control.MOUSE_FILTER_IGNORE;l.add_theme_color_override("font_color",ink);l.add_theme_color_override("font_outline_color",Color("153b4b"));l.add_theme_constant_override("outline_size",4);l.add_theme_color_override("font_shadow_color",Color("153b4b"));l.add_theme_constant_override("shadow_offset_y",2);return l
func preferences():
	if is_instance_valid(arena):
		arena.reduced=OnKlavGame.chrome.reduce_motion
		hud.reduced=arena.reduced
		if arena.reduced:arena.clear_effects()
func layout():
	Chrome.fit_board(canvas, size, 56, 500)
	var width := canvas.size.x
	report.position=Vector2(14,canvas.size.y*.56);report.size=Vector2(width-28,0)
	var compact:=canvas.size.y<450
	result_title.add_theme_font_size_override("font_size",22 if compact else 31)
	result_detail.add_theme_font_size_override("font_size",13 if compact else 17)
	rating.custom_minimum_size.y=44 if compact else 68
	result.add_theme_constant_override("separation",5 if compact else 12)
	result.position=Vector2(20,112 if compact else canvas.size.y*.27);result.size=Vector2(width-40,0)
func reset_frame_clock():
	frame_usec=Time.get_ticks_usec();flight_clock.reset()
	if is_instance_valid(arena):arena.reset_frame_clock()
func start_new():
	model=Duel.new(randi_range(1,1000000));hold=1.35;reset_frame_clock();bot_age=0;ai_ready=false;last_phase="";last_stage=-1;last_actor=-1;combo_level=0;arena.clear_effects();arena.load_age=0;audio.stop();hud.shown=[0.0,0.0]
	announce("READY TO RUMBLE","Pick a weak spot. Let it rip!",1.35);refresh()
func can_shoot()->bool:return model!=null and model.phase=="aim" and model.actor==0 and hold<=0 and not OnKlavGame.paused and (arena.load_age>=1 or arena.reduced)
func shoot(angle:int,power:int):
	if not can_shoot():return
	fire(angle,power)
func fire(angle:int,power:int):
	if not model.launch(angle,power):return
	arena.aim=angle;arena.power=power;arena.launch_effect();audio.play("launch");audio.play("whizz",1.0 if model.actor==0 else .92);reset_frame_clock();combo_level=0;ai_ready=false;report.hide();announce_age=0;OnKlavGame.checkpoint();refresh()
func announce(title:String,detail:String,duration:float=1.5):
	report_title.text=title;report_detail.text=detail;announce_age=duration;announce_total=duration;report.show()
func finish_shot():
	var off:int=model.shot_toppled;hold=2.1 if off>0 else 1.6
	announce(shot_title(),shot_detail(),hold)
	audio.play("combo" if off>=3 else "tumble" if off>0 else "miss")
	arena.expressions[model.actor]=hold
	OnKlavGame.checkpoint();refresh()
func shot_detail()->String:
	if model.shot_bridge != 0 or model.shot_junction or model.shot_double==2 or model.stone_press_dropped() or model.balance_tipped() or model.lantern_vault_fell() or model.last_straw_fell():
		return "%d pieces down  ·  +%d points"%[model.shot_toppled,Duel.points(model.shot_points)]
	return "+%d points"%Duel.points(model.shot_points)
func shot_title()->String:
	if model.last_straw_fell():return "LAST STRAW SNAPPED"
	if model.lantern_vault_fell():return "LANTERN VAULT FALL"
	if model.balance_tipped():return "BALANCE BROKEN"
	if model.stone_press_dropped():return "STONE PRESS DROP"
	if model.shot_double==2:
		match model.stage:
			9: return "TEMPLE CASCADE"
			11: return "CROWN COLLAPSE"
		return "TWIN FUSE CHAIN"
	if model.shot_junction:return "COUNTERWEIGHT RELEASED"
	match model.shot_bridge:
		1: return "SUPPORT CUT"
		2: return "FUSE PIER BLAST"
		3: return "DOUBLE COLLAPSE"
	if model.off()==model.shapes.size():return "NO REFUNDS!"
	if model.shot_toppled>=7:return "STRUCTURAL OOPS!"
	if model.shot_toppled>=3:return "TIMBER!"
	return "BONK!" if model.shot_toppled>0 else "NICE SHOT!" if model.shot_points>0 else "WHOOPS!"
func advance():
	if OnKlavGame.paused or model==null:return
	if model.phase=="finished":OnKlavGame.replay();return
	if model.next_stage():
		hold=1.35;arena.clear_effects();arena.load_age=0;ai_ready=false;last_actor=-1;last_stage=-1;announce("ROUND %02d"%[model.stage+1],model.title(),1.35);audio.play("ready");OnKlavGame.checkpoint();refresh()
func _process(dt):
	var now:=Time.get_ticks_usec();dt=FlightClock.elapsed(frame_usec,now,dt);frame_usec=now
	if model==null or OnKlavGame.paused:return
	hold=maxf(0,hold-dt);announce_age=maxf(0,announce_age-dt)
	if announce_age<=0:report.hide()
	else:
		var age:=announce_total-announce_age;report.pivot_offset=report.size*.5
		report.modulate.a=minf(1,minf(age/.10,announce_age/.18));report.scale=Vector2.ONE if arena.reduced else Vector2.ONE*(1+.13*exp(-age*7)*sin(age*18))
	if model.phase=="flight":
		var impact_power:=0.0;var impact_point:=Vector2.ZERO
		var glass:=false;var wood:=false;var combo_cue:=false
		for tick in range(flight_clock.take(dt,model.tick_span())):
			if model.phase!="flight":break
			model.step()
			if model.hit_power>impact_power:impact_power=model.hit_power;impact_point=model.hit_point
			for item in model.popped:
				arena.fragment(item.p,item.kind,item.shattered);arena.points(item.p,"+"+str(Duel.points(item.points)))
				if item.kind==2:glass=true
				else:wood=true
			for event in model.events:
				if event.kind=="counterweight":
					arena.points(event.p,"WEIGHT AWAY!",Color("a6e5ff"))
					audio.play("block",.78)
				else:
					arena.burst(event.p);audio.play("bird" if event.kind=="bird" else "power")
					arena.points(event.p,"GOLDEN DUCK! +35" if event.kind=="bird" else "+1 PANDA!" if event.kind=="extra" else "BOOM! +20",Color("ffe093"))
			var combo:int=model.shot_toppled/3
			if combo>combo_level:
				combo_level=combo;combo_cue=true;arena.points(Vector2(500,635),["","DOUBLE TROUBLE","CHAIN REACTION","TOTAL CHAOS","UNBELIEVABLE!"][mini(4,combo)],Color("ffeba9"))
		# Cosmetic impacts and sound never stop the simulation or restart on every substep.
		if impact_power>130:arena.impact(impact_point,impact_power);audio.play("hit",.94+randf()*.12)
		if glass:audio.play("glass",.95+randf()*.15)
		if wood:audio.play("block",.95+randf()*.15)
		if combo_cue:audio.play("combo",1+combo_level*.06)
		if model.phase=="report":flight_clock.reset();arena.trail=[];finish_shot()
	elif model.phase=="report" and hold<=0:
		model.turn_over();hold=.9;ai_ready=false;bot_age=0;arena.cancel();arena.visual=[];arena.load_age=0;arena.aim=0;arena.power=0;OnKlavGame.checkpoint();refresh()
	elif model.phase=="aim" and hold<=0:
		model.advance_clock(minf(dt,.1))
		if model.actor==1:
			if not ai_ready:ai_target=model.ai_shot();ai_ready=true;bot_age=0
			bot_age+=dt;arena.aim=lerpf(arena.aim,ai_target.x,1-exp(-dt*4));arena.power=roundi(lerpf(arena.power,ai_target.y,1-exp(-dt*5)))
			if bot_age>=1.5:fire(ai_target.x,ai_target.y)
	arena.local_turn=can_shoot()
func refresh():
	if model==null:return
	arena.model=model;arena.local_turn=can_shoot();hud.model=model
	if last_stage!=model.stage:last_stage=model.stage;backdrop.texture=BACKGROUNDS[model.level()]
	stage_label.text=Duel.SCENES[model.level()].to_upper()+"  ·  "+model.title()
	var board:Dictionary=model.boards[model.actor]
	var shot:int=board.shots+int(model.phase=="aim")
	turn_label.text=("YOUR SHOT %d / %d" if model.actor==0 else "RIVAL SHOT %d / %d")%[mini(board.limit,shot),board.limit]
	var is_result:bool=model.phase in["round","finished"]
	controls.visible=is_result;result.visible=is_result;arena.visible=true;turn_label.visible=not is_result;report.visible=announce_age>0 and not is_result
	if is_result:
		var winner:int=model.result_winner()
		var won:=winner==0
		var tied:=winner<0
		result_title.text="ROUND WON!" if won else "NECK AND NECK!" if tied else "RIVAL WINS"
		var score:int=int(model.stars[-1]) if not model.stars.is_empty() else 0
		rating.set_meta("stars",score);rating.queue_redraw()
		result_detail.text=("You won round %d" if won else "Round %d was a draw" if tied else "Rival won round %d")%[model.stage+1]
		if model.phase=="finished":
			result_title.text="CAMPAIGN COMPLETE"
			result_detail.text="You won the campaign" if won else "The campaign was a draw" if tied else "Rival won the campaign"
			next_button.text="Play Again"
		else:next_button.text="Continue"
		if last_phase!=model.phase:
			if not tied:
				audio.play("win");arena.celebrate(won)
			profile_update()
	last_phase=model.phase;audio.music(model.level(),false)
func draw_rating():
	var amount:int=rating.get_meta("stars",0)
	for i in range(3):
		var compact:=canvas.size.y<450
		var center:=Vector2(rating.size.x*.5+(i-1)*(52 if compact else 65),22 if compact else 32);var points:=PackedVector2Array()
		for n in range(10):points.append(center+Vector2.from_angle(-PI/2+n*PI/5)*((20 if compact else 27) if n%2==0 else (9 if compact else 12)))
		var shadow:=points.duplicate()
		for n in range(shadow.size()):shadow[n]+=Vector2(2,4)
		rating.draw_colored_polygon(shadow,Color("081725cc"))
		rating.draw_colored_polygon(points,Color("ffd26d") if i<amount else Color("28526499"))
		var outline:=points.duplicate();outline.append(outline[0]);rating.draw_polyline(outline,Color("fff1b5") if i<amount else Color("96c3c8"),2.5,true)
		if i<amount:rating.draw_line(center+Vector2(-9,-2),center+Vector2(-2,-12),Color("fff7cf"),3,true)
func snapshot()->Dictionary:return {"game":"panda","schema":4,"model":model.snapshot(),"hold":hold}
func restore(data:Dictionary)->bool:
	if data.get("game")!="panda" or not data.get("model")is Dictionary:return false
	var candidate=Duel.new()
	var saved_hold:Variant=data.get("hold")
	if data.get("schema")==4:
		if not Duel.number(saved_hold,0,3.5) or not candidate.restore(data.model):return false
	elif data.get("schema")==1 and data.get("mode")=="solo":
		if not candidate.import_legacy(data.model):return false
	else:return false
	model=candidate;combo_level=int(model.shot_toppled)/3;hold=float(saved_hold) if Duel.number(saved_hold,0,3.5) else 1.0;reset_frame_clock();bot_age=0;ai_ready=false;announce_age=0;last_phase=model.phase;last_stage=-1;last_actor=-1;arena.clear_effects();arena.load_age=1;arena.aim=model.last_aim;arena.power=model.last_power;hud.shown=[float(Duel.points(model.totals[0])),float(Duel.points(model.totals[1]))]
	if model.phase=="report":announce(shot_title(),shot_detail(),maxf(.4,hold))
	refresh();return true
func load_profile():
	if profile_loading:return
	profile_loading=true
	var response:Dictionary=await OnKlavGame.load_data("panda_profile")
	profile_loading=false
	if not response.get("ok",false):return
	if response.get("exists",false):
		var value:Variant=response.get("state")
		if not value is Dictionary or value.get("schema")!=1 or not Duel.integer(value.get("best_score"),0,2000000) or not value.get("stars")is Array or value.stars.size()!=12:return
		for star in value.stars:
			if not Duel.integer(star,0,3):return
		profile=value.duplicate(true)
	profile_ready=true
	if model!=null and model.completed:profile_update()
func profile_update():
	if not profile_ready or model==null:return
	if model.completed:
		var previous:Dictionary=profile.duplicate(true)
		profile.best_score=maxi(profile.best_score,model.totals[0])
		for i in range(model.stars.size()):profile.stars[i]=maxi(int(profile.stars[i]),int(model.stars[i]))
		if previous!=profile:profile_dirty=true
	if not profile_dirty or profile_busy:return
	profile_busy=true
	var payload:Dictionary=profile.duplicate(true)
	var saved:bool=await OnKlavGame.save_data("panda_profile",payload)
	profile_busy=false
	if saved:
		profile_dirty=payload!=profile
		if profile_dirty:profile_update.call_deferred()
	# A failed write remains pending. The shared Retry save/suspend path retries it,
	# including while paused, instead of permanently marking this course as written.
