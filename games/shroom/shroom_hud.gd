extends Control

var game
const LIGHT = Color("#f6e5cd")
const MINT = Color("#71f9d0")
const DARK = Color("#140f20")
const GOLD = Color("#ffca76")

const TEXT_CACHE_LIMIT = 256
var panel_skin := StyleBoxFlat.new()
var text_widths: Dictionary = {}
var mission_panel: Rect2
var health_panel: Rect2
var weapon_panel: Rect2

func _ready() -> void:
	panel_skin.bg_color = Color("#0a1422df")
	panel_skin.border_color = Color("#55707c9c")
	panel_skin.set_border_width_all(1)
	panel_skin.set_corner_radius_all(10)
	resized.connect(update_panel_layout)
	update_panel_layout()

func update_panel_layout() -> void:
	mission_panel = Rect2(14,12,minf(280.0,size.x*.32),71)
	var health_width := minf(170.0,size.x*.25)
	health_panel = Rect2(size.x-health_width-14,12,health_width,60)
	weapon_panel = Rect2(health_panel.position.x,health_panel.end.y+7,health_width,40)
	queue_redraw()

func text_width(line: String, px: int) -> float:
	var key := Vector2i(line.hash(),px)
	var cached: Variant = text_widths.get(key)
	if cached != null and cached[0] == line:
		return cached[1]
	var measured := ThemeDB.fallback_font.get_string_size(line,HORIZONTAL_ALIGNMENT_LEFT,-1,px).x
	if text_widths.size() >= TEXT_CACHE_LIMIT:
		text_widths.clear()
	text_widths[key] = [line,measured]
	return measured

func panel(rect: Rect2, accent: Color) -> void:
	draw_style_box(panel_skin,rect)
	draw_line(rect.position+Vector2(8,1),rect.position+Vector2(minf(rect.size.x-8,55.0),1),accent,2.0,true)

func fit_text(line: String, width: float, preferred: int) -> int:
	var measured := text_width(line,preferred)
	return maxi(1,mini(preferred,floori(width*preferred/maxf(measured,1.0))))

func meter(rect: Rect2, value: float, tint: Color) -> void:
	draw_rect(rect,Color("#344553"))
	rect.size.x *= clampf(value,0.0,1.0)
	draw_rect(rect,tint)

func text_at(at: Vector2, line: String, px: int, tint: Color) -> void:
	var font := ThemeDB.fallback_font
	draw_string(font,at+Vector2(1,2),line,HORIZONTAL_ALIGNMENT_LEFT,-1,px,Color("#110b1b"))
	draw_string(font,at,line,HORIZONTAL_ALIGNMENT_LEFT,-1,px,tint)

func centered(at: Vector2, line: String, px: int, tint: Color) -> void:
	var w := text_width(line,px)
	text_at(at-Vector2(w*.5,0),line,px,tint)

func poly(points: Array, tint: Color, anchor: Vector2, scale_factor: float) -> void:
	var path := PackedVector2Array()
	for p in points: path.append(anchor+Vector2(p[0],p[1])*scale_factor)
	draw_colored_polygon(path,tint)

func _draw() -> void:
	if game==null or not is_instance_valid(game) or not OnKlavGame.session_started: return
	var mid := size*.5
	if game.phase == "briefing" and game.stage == 1 and game.intro_elapsed < game.INTRO_TIME:
		draw_opening()
		return
	if game.phase == "won":
		draw_victory()
		return
	if game.damage_time>0:
		var intensity: float = pow(game.damage_time/.48,1.7)
		var flash := .16 if game.reduced_motion() else .34
		draw_rect(Rect2(Vector2.ZERO,size),Color(.78,.015,.065,flash*intensity))
		for i in range(5):
			var inset := 4.0+i*8.0
			draw_rect(Rect2(Vector2.ZERO,size).grow(-inset),Color(.91,.025,.10,(.28-i*.045)*intensity),false,8)
	if game.phase=="portal":
		var progress: float = game.portal_elapsed/game.PORTAL_TIME
		var tint := Color(.31,.94,.80,sin(progress*PI)*.16)
		draw_rect(Rect2(Vector2.ZERO,size),tint)
		var final_sector: bool = game.stage == game.MAX_STAGE
		var panel_width := minf(size.x-24.0,346.0 if final_sector else 284.0)
		var panel := Rect2(mid.x-panel_width*.5,minf(size.y*.79,size.y-75),panel_width,63)
		var caption := "EXTRACTION LINKED"
		if final_sector:
			caption = "FINAL EXTRACTION" if progress < .53 else "CONTAINMENT RESTORED"
			if not game.reduced_motion():
				var light: float = smoothstep(.55,1.0,progress)
				draw_rect(Rect2(Vector2.ZERO,size),Color(.95,.79,.48,light*.26))
		draw_rect(panel,Color(.05,.09,.12,.88))
		centered(Vector2(mid.x,panel.position.y+28),caption,fit_text(caption,panel_width-22,21 if final_sector else 23),MINT)
		var track_width := panel_width-38.0
		draw_rect(Rect2(panel.position+Vector2(19,43),Vector2(track_width,5)),Color("#25434a"))
		draw_rect(Rect2(panel.position+Vector2(19,43),Vector2(track_width*progress,5)),MINT)
		return
	if game.phase!="combat": return
	var target: Vector3 = game.objective_target() if game.expedition_mode else Vector3.ZERO
	var objective := "Clear the wave"
	var count: int = game.objective_nodes.size()
	var secured := 0
	if game.expedition_mode:
		if count==0:
			objective = "Reach extraction" if game.portal_ready() else "Find the Cap King"
		elif game.objectives_complete():
			if is_instance_valid(game.access_card):
				objective = "Find extraction keycard"
			elif is_instance_valid(game.access_gate):
				objective = "Unlock extraction"
			else:
				objective = "Reach extraction" if game.portal_ready() else "Clear the gate guards"
		else:
			for node in game.objective_nodes:
				if not node.visible: secured += 1
			objective = ["Recover samples","Reset relays","Recover cultures","Shut down pumps"][game.stage-1]
	var mission := mission_panel
	var mission_width := mission.size.x
	panel(mission,MINT)
	var sector: String = "%02d  /  %s" % [game.stage,game.STAGES[game.stage-1]]
	text_at(mission.position+Vector2(11,20),sector,fit_text(sector,mission_width-22,16),MINT)
	text_at(mission.position+Vector2(11,43),objective,fit_text(objective,mission_width-22,23),LIGHT)
	if count > 0:
		var segment := (mission_width-22-(count-1)*4.0)/count
		for index in count:
			var start := mission.position+Vector2(11+index*(segment+4.0),56)
			draw_rect(Rect2(start,Vector2(segment,4)),MINT if index < secured or game.objectives_complete() else Color("#344553"))
	if game.expedition_mode:
		var offset: Vector3 = game.player.basis.inverse()*(target-game.player.position)
		var angle := atan2(offset.x,-offset.z)
		var arrow := Vector2(mid.x,35)
		draw_circle(arrow,20,Color("#0a1422df"))
		draw_arc(arrow,19,0,TAU,36,Color("#55707c"),1.4,true)
		draw_colored_polygon(PackedVector2Array([
			arrow+Vector2(0,-13).rotated(angle),arrow+Vector2(-8,8).rotated(angle),
			arrow+Vector2(0,4).rotated(angle),arrow+Vector2(8,8).rotated(angle)]),GOLD)
		centered(arrow+Vector2(0,37),"%d m" % roundi(Vector2(offset.x,offset.z).length()),15,LIGHT)
	var health := health_panel
	var health_width := health.size.x
	var health_tint := MINT if game.health>28 else Color("#ff7986")
	panel(health,health_tint)
	var health_label := "VITALS  %d" % game.health
	text_at(health.position+Vector2(11,25),health_label,fit_text(health_label,health_width-22,21),LIGHT)
	meter(Rect2(health.position+Vector2(11,39),Vector2(health_width-22,6)),game.health/100.0,health_tint)
	for enemy in game.enemies:
		if enemy.get_meta("type") != 3 or not enemy.get_meta("alert"): continue
		var width := minf(size.x*.34,300)
		var exposed: bool = enemy.get_meta("boss_exposed", 0.0) > 0.0
		centered(Vector2(mid.x,97),"CAP KING  ·  EXPOSED" if exposed else "CAP KING",23,GOLD)
		draw_rect(Rect2(mid.x-width*.5,106,width,9),Color("#452836"))
		draw_rect(Rect2(mid.x-width*.5,106,width*float(enemy.get_meta("hp"))/game.TYPE_HP[3],9),Color("#ff6684"))
		break
	if game.banner_time>0:
		var banner_text: String = game.banner
		var px := fit_text(banner_text,size.x*.72,22)
		var span := text_width(banner_text,px)+28.0
		panel(Rect2(mid.x-span*.5,121,span,35),GOLD)
		centered(Vector2(mid.x,145),banner_text,px,GOLD)
	var prompt: String = game.field_prompt()
	if not prompt.is_empty():
		var px := fit_text(prompt,size.x-24.0,23)
		var label_width := text_width(prompt,px)
		var baseline := mid.y + 53.0
		draw_rect(Rect2(mid.x - label_width * .5 - 8.0, baseline - px - 3.0, label_width + 16.0, px + 12.0), Color("#0b1422e8"))
		centered(Vector2(mid.x, baseline), prompt, px, GOLD)
	var hit := Color("#ffdf8d") if game.hit_mark>0 else Color(.92,1,.98,.84)
	var gap: float = 5.0+game.recoil*6
	for side in [Vector2.LEFT,Vector2.RIGHT,Vector2.UP,Vector2.DOWN]:
		draw_line(mid+side*(gap+1),mid+side*(gap+10),Color("#181120"),4,true)
		draw_line(mid+side*gap,mid+side*(gap+8),hit,2,true)
	draw_circle(mid,1.7,hit)
	if game.hit_mark > 0.0:
		var mark := Color("#ffcf7b") if game.hit_defeated else Color("#f8ffff")
		mark.a = clampf(game.hit_mark/.10,0.0,1.0)
		for axis in [Vector2(-1,-1),Vector2(1,-1),Vector2(-1,1),Vector2(1,1)]:
			var reach := 13.0 if game.hit_defeated else 10.0
			draw_line(mid+axis*5.0,mid+axis*reach,mark,2.4,true)
	var reload_progress := 0.0
	if game.reload_left>0:
		reload_progress=1.0-game.reload_left/game.RELOAD_TIME[game.weapon]
	# Directional hurt indicator: flat 2D wedge, no particle/geometry cost.
	# The bearing remains readable in reduced-motion mode (no pulsing).
	if game.damage_cue_left > 0.0:
		var opacity: float = clampf(game.damage_cue_left/game.DAMAGE_CUE_TIME,0.0,1.0)
		var bearing: float = game.damage_cue_angle
		var outward := Vector2(sin(bearing),-cos(bearing))
		var right := Vector2(-outward.y,outward.x)
		var radius := minf(size.x,size.y)*.31
		var pos := mid+outward*radius
		var tip := pos+outward*17.0
		var tint := Color(1.0,.20,.25,.30+.60*opacity)
		draw_colored_polygon(PackedVector2Array([tip,pos-outward*7.0+right*12.0,pos-outward*7.0-right*12.0]),tint)
	var label: String = game.WEAPON_NAMES[game.weapon].to_upper()
	var tint: Color = [MINT,GOLD,Color("#8deeff"),Color("#e8afff")][game.weapon]
	var weapon_width := health_width
	var readout := weapon_panel
	panel(readout,tint)
	var weapon_text: String = "RELOADING" if game.reload_left > 0.0 else "%s  %02d / %02d" % [label,game.rounds[game.weapon],game.CAPACITY[game.weapon]]
	if game.weapon == 0 and game.nail_charges > 0 and game.reload_left <= 0.0:
		weapon_text += "  ·  ×3: %d" % game.nail_charges
	centered(readout.position+Vector2(weapon_width*.5,25),weapon_text,fit_text(weapon_text,weapon_width-16,17),LIGHT)
	if game.reload_left > 0.0:
		meter(Rect2(readout.position+Vector2(9,32),Vector2(weapon_width-18,3)),reload_progress,tint)



func draw_victory() -> void:
	draw_rect(Rect2(Vector2.ZERO,size),Color("#090d18dc"))
	var progress: float = 1.0 if game.reduced_motion() else game.victory_elapsed/game.VICTORY_REVEAL
	var center := size*.5
	var spread := Vector2(size.x*.41,size.y*.39)
	for i in 3:
		var radius := (i+1)*.082*minf(size.x,size.y)
		draw_arc(center,radius+spread.y,0.0,TAU,48,Color(.82,.56,.26,.13-i*.035),1.6,true)
	for i in 14:
		var angle := i*TAU/14.0 + .14
		var delay := (i%4)*.09
		var reveal := smoothstep(delay,delay+.43,progress)
		var point := center + Vector2(cos(angle),sin(angle))*spread*(.78+.22*reveal)
		var length := (4.0+i%3*2.0)*reveal
		var tint := Color(.99,.77,.44,.67*reveal)
		draw_line(point-Vector2(length,0),point+Vector2(length,0),tint,1.4,true)
		draw_line(point-Vector2(0,length),point+Vector2(0,length),tint,1.4,true)

func draw_opening() -> void:
	var t: float = game.intro_elapsed
	var p: float = t/game.INTRO_TIME
	var fade := 1.0-smoothstep(0.0,.48,t)
	draw_rect(Rect2(Vector2.ZERO,size),Color(.03,.04,.07,.34+fade*.61))
	var bar := size.y*.13
	draw_rect(Rect2(0,0,size.x,bar),Color("#090d16"))
	draw_rect(Rect2(0,size.y-bar,size.x,bar),Color("#090d16"))
	var center := size*.5
	var beat := mini(2,int(p*3.0))
	var title: String = ["DEEP SPORE STATION","CREW EVACUATED","SERGEANT REYES"][beat]
	var story: Array = [
		["We grew fungi to break down toxic waste.","Then the strain escaped into the root pumps."],
		["The crew made it to Surface Shelter One.","You are the last containment officer underground."],
		["Shut down the pumps and defeat the Cap King.","Stop the bloom before it reaches the surface."]
	][beat]
	var reveal := smoothstep(0.0,.075,p*3.0-float(beat))
	var title_size := fit_text(title,size.x*.88,clampi(int(size.x*.061),26,56))
	centered(center+Vector2(0,-48),"ONKLAV  /  INCIDENT 07",17,Color(.68,.90,.83,.88*reveal))
	centered(center+Vector2(0,8),title,title_size,Color(1.0,.77,.47,reveal))
	var caption_width := minf(size.x*.94,690.0)
	draw_rect(Rect2(center.x-caption_width*.5,center.y+20,caption_width,63),
		Color(.025,.035,.055,.75*reveal))
	var font := ThemeDB.fallback_font
	for index in story.size():
		var line: String = story[index]
		var px := fit_text(line,caption_width-22.0,26)
		var width := text_width(line,px)
		var at := center+Vector2(-width*.5,44+index*27)
		draw_string_outline(font,at,line,HORIZONTAL_ALIGNMENT_LEFT,-1,px,4,Color(.02,.03,.05,reveal))
		draw_string(font,at,line,HORIZONTAL_ALIGNMENT_LEFT,-1,px,Color(.98,.99,1.0,reveal))
	var track := minf(size.x*.31,290.0)
	draw_rect(Rect2(center.x-track*.5,size.y-bar-9,track,3),Color("#364651"))
	draw_rect(Rect2(center.x-track*.5,size.y-bar-9,track*p,3),MINT)
