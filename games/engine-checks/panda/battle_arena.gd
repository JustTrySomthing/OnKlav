extends Control
signal fired(angle:int,power:int)
signal pulled
const Clock=preload("res://panda/flight_clock.gd")
const PORTRAITS:=[preload("res://panda/art/piece1.webp"),preload("res://panda/art/piece2.webp")]
const HELMET_COLORS:=[Color("f4a719"),Color("1479dd")]
const INKS:=[Color("e8a254"),Color("68cf9b"),Color("8ddff1"),Color("a299cb")]
var model
var style_cache:Dictionary={}
var text_width_cache:Dictionary={}
var local_turn:=false
var reduced:=false
var pointer_id:=-1
var last_touch:=-10000
var down:=Vector2.ZERO
var pointer:=Vector2.ZERO
var aiming:=false
var aim:=0.0
var power:=0
var preview_input:=Vector2i(-99,-99)
var rendered_preview_input:=Vector2i(-99,-99)
var preview_path:Dictionary={}
var time:=0.0
var frame_usec:=0
var recoil:=0.0
var shake:=0.0
var flash:=0.0
var load_age:=1.0
var particles:Array=[]
var floating:Array=[]
var rings:Array=[]
var trail:Array=[]
var ghosts:Array=[]
var expressions:=[0.0,0.0]
var last_ball:=Vector2.ZERO
var visual:Array=[]
func _ready():mouse_filter=Control.MOUSE_FILTER_STOP;gui_input.connect(input_event)
func factor()->float:return minf(size.x/940,size.y/1200)
func rest_origin()->Vector2:return Vector2(size.x*.5-500*factor(),size.y*.5-600*factor())
func origin()->Vector2:return rest_origin()+Vector2(sin(time*81),cos(time*67))*shake*factor()*7 if not reduced else rest_origin()
func point_to_screen(p:Vector2)->Vector2:return origin()+p*factor()
func world(p:Vector2)->Vector2:return (p-rest_origin())/maxf(.001,factor())
func base():draw_set_transform(origin(),0,Vector2.ONE*factor())
func cancel():
	pointer_id=-1;aiming=false;power=0
	preview_input=Vector2i(-99,-99);rendered_preview_input=preview_input;preview_path.clear()
func reset_frame_clock():frame_usec=Time.get_ticks_usec()
func clear_effects():
	reset_frame_clock()
	particles=[];floating=[];rings=[];trail=[];visual=[];ghosts=[];shake=0;flash=0;recoil=0;cancel()
func input_event(event:InputEvent):
	if not local_turn or model==null or model.phase!="aim":return
	var press:=false;var release:=false;var moving:=false;var position_value:=Vector2.ZERO;var id:=-2
	if event is InputEventScreenTouch:
		last_touch=Time.get_ticks_msec()
		if event.pressed and pointer_id==-2:cancel()
		press=event.pressed;release=not press;position_value=event.position;id=event.index
		if event.canceled:
			if pointer_id==id:cancel()
			return
	elif event is InputEventScreenDrag:last_touch=Time.get_ticks_msec();moving=true;position_value=event.position;id=event.index
	elif event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT:
		if Time.get_ticks_msec()-last_touch<600:return
		press=event.pressed;release=not press;position_value=event.position
	elif event is InputEventMouseMotion:
		if Time.get_ticks_msec()-last_touch<600:return
		moving=true;position_value=event.position
	else:return
	var p:=world(position_value)
	if press:
		if pointer_id!=-1 or p.distance_to(aim_anchor())>125 and p.distance_to(model.muzzle(aim))>130:return
		pointer_id=id;down=p;pointer=p;aiming=true;pulled.emit();update_aim()
	elif id==pointer_id and moving:pointer=p;update_aim()
	elif id==pointer_id and release:
		pointer=p;update_aim()
		if aiming and pointer.y-down.y>22:fired.emit(roundi(aim),maxi(10,power))
		cancel()
func update_aim():
	if not aiming:return
	var d:=pointer-down
	aim=roundf(clampf(-d.x*.34,-42,42))
	power=clampi(roundi(maxf(0,d.y)*.42),0,100)
	preview_input=Vector2i(int(aim),maxi(10,power)) if d.y>22.0 else Vector2i(-99,-99)
func refresh_aim_preview():
	# A touch controller can deliver several drag events before a single draw.
	if preview_input==rendered_preview_input:return
	rendered_preview_input=preview_input
	preview_path=model.aim_preview(preview_input.x,preview_input.y) if preview_input.y>=10 else {}
func aim_anchor()->Vector2:return Vector2(500,835)
func _process(dt):
	var now:=Time.get_ticks_usec();dt=Clock.elapsed(frame_usec,now,dt);frame_usec=now
	refresh_aim_preview()
	time+=dt;recoil=maxf(0,recoil-dt*3.8);shake=maxf(0,shake-dt*3);flash=maxf(0,flash-dt*3.5);load_age=minf(1,load_age+dt/ .72)
	for p in particles:p.age+=dt;p.p+=p.v*dt;p.v.y+=460*dt;p.rotation+=dt*p.spin
	if not particles.is_empty():particles=particles.filter(func(p):return p.age<p.life)
	for p in floating:p.age+=dt;p.p.y-=dt*40
	if not floating.is_empty():floating=floating.filter(func(p):return p.age<1.35)
	for p in rings:p.age+=dt
	if not rings.is_empty():rings=rings.filter(func(p):return p.age<.7)
	for t in trail:t.age+=dt
	if not trail.is_empty():trail=trail.filter(func(p):return p.age<.32)
	for i in range(2):expressions[i]=maxf(0,expressions[i]-dt)
	if model!=null:
		var blocks:Array=model.boards[model.actor].blocks
		if visual.size()!=blocks.size():visual=[]
		var blend:=1-exp(-dt*35)
		for i in range(blocks.size()):
			var target:=Vector3(blocks[i].x,blocks[i].y,blocks[i].angle)
			if i>=visual.size():visual.append(target)
			elif reduced or model.phase!="flight":visual[i]=target
			else:
				visual[i].x=lerpf(visual[i].x,target.x,blend);visual[i].y=lerpf(visual[i].y,target.y,blend);visual[i].z=lerp_angle(visual[i].z,target.z,blend)
		if not reduced and model.phase=="flight" and model.world!=null:
			var ball=model.world.bodies[model.platforms.size()]
			if ball.enabled and ball.p.distance_to(last_ball)>14:
				last_ball=ball.p;trail.append({"p":ball.p,"age":0.0})
				if trail.size()>28:trail.pop_front()
	queue_redraw()
func impact(p:Vector2,strength:float):
	shake=maxf(shake,.13 if strength<800 else .42);flash=.04 if strength<800 else .09
	for i in range(7):spawn(p,Vector2.from_angle(time+i)*float(70+i*30),Color("fff5d3"),5,.35)
func spawn(p:Vector2,velocity:Vector2,ink:Color,radius:float,life:float):
	if reduced:return
	particles.append({"p":p,"v":velocity,"color":ink,"radius":radius,"age":0.0,"life":life,"spin":randf_range(-7,7),"rotation":randf()*TAU})
	if particles.size()>180:particles.pop_front()
func fragment(p:Vector2,kind:int,strong:bool=false):
	if reduced:return
	var count := 18 if strong else 10
	for i in range(count):
		var angle := TAU*i/count+time
		var speed := 100+i%4*64 if kind==3 else 130+i%4*85
		spawn(p,Vector2.from_angle(angle)*speed+Vector2(0,-100),INKS[kind].lightened((i%3)*.12),5+i%4*2,.65+i%3*.16)
		particles.back()["material"] = kind
func burst(p:Vector2):
	if reduced:return
	shake=.6;flash=.2;rings.append({"p":p,"age":0.0})
	if rings.size()>5:rings.pop_front()
	for i in range(34):spawn(p,Vector2.from_angle(i*TAU/34)*(220+i%5*80),Color("ffdb69") if i%2 else Color("fff8db"),6+i%4, .8+i%3*.1)
func points(p:Vector2,text:String,ink:Color=Color("fff4c6")):
	floating.append({"p":p,"text":text,"age":0.0,"ink":ink})
	if floating.size()>12:floating.pop_front()
func launch_effect():
	recoil=1;trail=[];expressions[model.actor]=2
	var p:Vector2=model.muzzle(aim)
	for i in range(18):spawn(p,Vector2.from_angle(-PI/2+randf_range(-.7,.7))*randf_range(80,350),Color("fff3c1") if i%2 else Color("c5eadc"),8+i%5, .45+i%3*.07)
func celebrate(won:bool):
	if reduced:return
	for i in range(65):spawn(Vector2(100+i%9*100,-40-i*4),Vector2(randf_range(-70,70),randf_range(40,130)),[Color("ffcf6c"),Color("73e0b2"),Color("ff8e9c"),Color("8ddff1")][i%4],6+i%4,2+i%3*.3)
	expressions[0 if won else 1]=4
func rounded(rect:Rect2,ink:Color,edge:Color=Color.TRANSPARENT,radius:int=8):
	var key:="%08x:%08x:%d"%[ink.to_rgba32(),edge.to_rgba32(),radius]
	if not style_cache.has(key):
		if style_cache.size()>=256:style_cache.clear()
		var style:=StyleBoxFlat.new();style.bg_color=ink;style.border_color=edge;style.set_border_width_all(2 if edge.a>0 else 0);style.set_corner_radius_all(radius);style_cache[key]=style
	draw_style_box(style_cache[key],rect)
func words(value:String,p:Vector2,font_size:int,ink:Color=Color.WHITE,shadow:bool=false):
	var f=ThemeDB.fallback_font;var key:=str(font_size)+"|"+value
	if not text_width_cache.has(key):
		if text_width_cache.size()>=256:text_width_cache.clear()
		text_width_cache[key]=f.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x*.5
	var x:float=text_width_cache[key]
	if shadow:draw_string_outline(f,p-Vector2(x-4,-6),value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,8,Color("071f2bca"))
	draw_string_outline(f,p-Vector2(x,0),value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,5,Color("183a46"))
	draw_string(f,p-Vector2(x,0),value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,ink)
func panda(p:Vector2,radius:float,side:int,angle:float=0,squash:float=1):
	draw_set_transform(point_to_screen(p),angle,Vector2(squash,1/squash)*factor())
	draw_texture_rect(PORTRAITS[side],Rect2(-Vector2.ONE*radius,Vector2.ONE*radius*2),false)
	# Expressive motion is drawn with the game, so the same sharp art reads at every size.
	if expressions[side]>0 and model.phase!="flight":
		for i in range(3):
			var p2:=Vector2.from_angle(-PI+i*.8-time*1.8)*radius*1.25;draw_circle(p2,4,Color("ffe6a2"))
	base()
func draw_block(i:int,p:Vector2,angle:float,b:Dictionary):
	var s:Dictionary=model.shapes[i];var dim:=Vector2(s.w,s.h);var kind:int=s.kind;var ink:Color=INKS[kind]
	draw_set_transform(point_to_screen(p),angle,Vector2.ONE*factor())
	var rect:=Rect2(-dim*.5,dim)
	rounded(Rect2(rect.position+Vector2(4,7),dim),Color(0,.13,.2,.23),Color.TRANSPARENT,8)
	rounded(rect,ink.darkened(.34),ink.darkened(.46),7)
	rounded(Rect2(rect.position+Vector2(3,3),dim-Vector2(6,8)),Color(ink,.58) if kind==2 else ink,ink.lightened(.34),6)
	draw_line(rect.position+Vector2(6,5),rect.position+Vector2(dim.x-6,5),ink.lightened(.55),3,true)
	match kind:
		0:
			for y in range(10,int(dim.y-7),16):
				draw_line(Vector2(-dim.x*.5+8,-dim.y*.5+y),Vector2(dim.x*.5-8,-dim.y*.5+y+sin(i+y)*3),ink.darkened(.15),2,true)
			for x in [-1,1]:draw_circle(Vector2(x*(dim.x*.5-10),0),3,Color("9e6339"))
		1:
			for x in range(12,int(dim.x-5),20):draw_line(Vector2(-dim.x*.5+x,-dim.y*.5+5),Vector2(-dim.x*.5+x,dim.y*.5-6),Color("bbf1b1"),3,true)
			draw_line(Vector2(-dim.x*.5+3,dim.y*.18),Vector2(dim.x*.5-3,dim.y*.18),Color("3c9476"),4,true)
		2:
			draw_line(Vector2(-dim.x*.3,dim.y*.3),Vector2(dim.x*.15,-dim.y*.32),Color("efffff"),6,true)
			draw_line(Vector2(dim.x*.12,dim.y*.3),Vector2(dim.x*.32,-dim.y*.15),Color("d9fbff"),3,true)
		3:
			draw_colored_polygon(PackedVector2Array([Vector2(-dim.x*.4,-dim.y*.15),Vector2(-dim.x*.1,-dim.y*.4),Vector2(dim.x*.37,-dim.y*.18),Vector2(dim.x*.2,dim.y*.3),Vector2(-dim.x*.3,dim.y*.35)]),ink.lightened(.08))
	if s.bomb and not b.out:
		rounded(Rect2(-dim*.5+Vector2(4,4),dim-Vector2(8,8)),Color("ed9852"),Color("ffe7a7"),6)
		var r:=minf(dim.x,dim.y)*.32
		draw_circle(Vector2.ZERO,r,Color("fff2bf"));draw_line(Vector2(-r*.55,-r*.55),Vector2(r*.55,r*.55),Color("c85e3e"),4,true);draw_line(Vector2(r*.55,-r*.55),Vector2(-r*.55,r*.55),Color("c85e3e"),4,true)
		var tip:=Vector2(dim.x*.2,-dim.y*.5-7);draw_line(tip+Vector2(-10,10),tip,Color("fff6d1"),3,true)
		if not reduced:draw_circle(tip,4+sin(time*11)*2,Color("ffe184"))
	elif minf(dim.x,dim.y)>40:
		# Tiny worried eyes turn every structure into an expressive toy, not a flat pile.
		var glance:=sin(time*.6+i)*2 if not reduced else 0.0
		for x in [-1,1]:
			draw_circle(Vector2(x*9,0),7,Color("fff9e6"));draw_circle(Vector2(x*9+glance,1),3,Color("29434c"))
		draw_arc(Vector2(0,11),5,PI,TAU,10,Color("29434c"),2,true)
	var bridge_role := int(s.get("bridge_role",0))
	if bridge_role != 0 and not b.out:
		var marker := Color("fff2b2") if bridge_role==1 else Color("fff7db")
		var y := dim.y*.27
		for side in [-1,1]:
			var x:float = side*dim.x*.34
			draw_line(Vector2(x-6,y-6),Vector2(x+6,y+6),marker,3,true)
		draw_line(Vector2(-dim.x*.27,y+10),Vector2(dim.x*.27,y+10),marker,3,true)
	var junction_role := int(s.get("junction_role",0))
	if junction_role == 1 and not b.out:
		draw_rect(Rect2(-dim*.5+Vector2(3,3),dim-Vector2(6,6)),Color("a6e5ff"),false,4)
		draw_line(Vector2(-dim.x*.24,dim.y*.30),Vector2(dim.x*.24,-dim.y*.30),Color.WHITE,4,true)
	elif junction_role == 2 and not b.out:
		draw_circle(Vector2.ZERO,minf(dim.x,dim.y)*.35,Color("f7ce73"))
		draw_line(Vector2(-9,-5),Vector2(0,5),Color("534554"),4,true)
		draw_line(Vector2(0,5),Vector2(9,-5),Color("534554"),4,true)
	if (model.stone_press_column(i) or model.balance_brace(i) or model.lantern_support(i) or model.last_straw_spindle(i)) and not b.out:
		draw_rect(Rect2(-dim*.5+Vector2(3,3),dim-Vector2(6,6)),Color("d9faff"),false,4)
	if model.lantern_support(4) and i in [17,18] and not b.out:
		var glow := Color("ffd477")
		draw_circle(Vector2.ZERO,11,glow)
		draw_line(Vector2(-8,-13),Vector2(8,-13),Color("a8724f"),3,true)
		draw_line(Vector2(-8,13),Vector2(8,13),Color("a8724f"),3,true)
	if model.finale_charge(9) and i==20 and not b.out:
		draw_polyline(PackedVector2Array([Vector2(-40,9),Vector2(-45,-9),Vector2(-22,0),
			Vector2(0,-13),Vector2(22,0),Vector2(45,-9),Vector2(40,9),Vector2(-40,9)]),Color("ffe49b"),4,true)
	if b.damage>.2 and not b.out:
		var crack:=PackedVector2Array([Vector2(-dim.x*.14,-dim.y*.47),Vector2(dim.x*.05,-dim.y*.1),Vector2(-dim.x*.08,dim.y*.08),Vector2(dim.x*.15,dim.y*.43)])
		draw_polyline(crack,ink.darkened(.55),2.5,true)
	base()
func _draw():
	if model==null or factor()<=0:return
	base()
	# Low contrast pollen, falling petals and lanterns frame the field rather than obscure aim.
	if not reduced and model.phase not in["round","finished"]:
		for i in range(9):
			var p:=Vector2(90+fposmod(i*173+sin(time*.23+i)*24,820),100+fposmod(i*127-time*10,990))
			if model.level()==1:
				draw_line(p,p+Vector2(4,7),Color(1,.83,.73,.35),3,true)
			else:draw_circle(p,2+sin(time+i)*.6,Color(1,.97,.73,.3))
	if model.level()==2 and model.phase not in["round","finished"]:
		for x in [98,902]:
			var p:=Vector2(x,290+sin(time*1.3+x)*7 if not reduced else 290)
			draw_line(p-Vector2(0,60),p,Color("a3997770"),2,true)
			rounded(Rect2(p-Vector2(17,24),Vector2(34,48)),Color("d88b6180"),Color("ffde9a90"),10)
			draw_line(p+Vector2(0,24),p+Vector2(0,42),Color("efb96a80"),2,true)
	# Layered island shelves frame the action and leave the upper sky open.
	for d in ([] if model.phase in["round","finished"] else model.platforms):
		var width:float=d[1]-d[0];var center:float=(d[0]+d[1])*.5
		ellipse(Vector2(center,d[2]+47),Vector2(width*.46,22),Color(0,.18,.24,.15))
		draw_colored_polygon(PackedVector2Array([Vector2(d[0]-12,d[2]+8),Vector2(d[1]+12,d[2]+8),Vector2(d[1]-35,d[2]+45),Vector2(center,d[2]+67),Vector2(d[0]+35,d[2]+45)]),Color("967048"))
		rounded(Rect2(d[0]-9,d[2]-2,width+18,24),Color("efd5a1"),Color("886746"),9)
		rounded(Rect2(d[0]-14,d[2]-10,width+28,14),Color("8ed693"),Color("daf5ad"),6)
		for x in range(int(d[0])+12,int(d[1]),40):draw_line(Vector2(x,d[2]+4),Vector2(x-6,d[2]+15),Color("cfaa75"),2,true)
	var blocks:Array=[] if model.phase in["round","finished"] else model.boards[model.actor].blocks
	for i in range(blocks.size()):
		var b:Dictionary=blocks[i]
		if b.out and b.y>1240:continue
		var p:Vector3=Vector3(b.x,b.y,b.angle) if i>=visual.size() else visual[i]
		if b.out:draw_set_transform(origin(),0,Vector2.ONE*factor())
		draw_block(i,Vector2(p.x,p.y),p.z,b)
	var accent:Color=HELMET_COLORS[model.actor].lightened(.4)
	for t in trail:
		if not reduced:draw_circle(t.p,18*(1-t.age/.32),Color(accent,(1-t.age/.32)*.52))
	var remaining:int=maxi(0,model.boards[model.actor].limit-model.boards[model.actor].shots)
	var reserve:int=maxi(0,remaining-int(model.phase=="aim"))
	if model.phase in["aim","flight","report"]:
		for i in range(reserve):
			var p:=Vector2(861,1040-i*102);ellipse(p+Vector2(0,37),Vector2(42,9),Color(0,.12,.2,.16));panda(p,45,model.actor,0,1 if reduced else 1+.025*sin(time*2+i))
		if model.phase=="aim":
			var loaded:Vector2=model.muzzle(aim)
			if load_age<1 and not reduced:
				loaded=Vector2(861,1040-reserve*102).lerp(loaded,smoothstep(0,1,load_age));loaded.y-=sin(load_age*PI)*140
			panda(loaded,57,model.actor,deg_to_rad(aim),1 if reduced else 1+.025*sin(time*3))
		# A real launch island grounds the cannon instead of floating it over the water.
		ellipse(Vector2(500,1190),Vector2(180,25),Color(0,.15,.2,.22))
		draw_colored_polygon(PackedVector2Array([Vector2(345,1177),Vector2(655,1177),Vector2(620,1235),Vector2(410,1238)]),Color("96734d"))
		rounded(Rect2(338,1170,324,17),Color("7ac899"),Color("c9efb3"),8)
		cannon()
		# Left-hand charge gauge, separated from the clear pull target.
		rounded(Rect2(114,800,38,310),Color("164755cc"),Color("e9e9bc"),18)
		var charge:=power/100.0;var ink:=Color("7be6ae").lerp(Color("ffd06d"),charge)
		if charge>0:rounded(Rect2(120,1104-298*charge,26,298*charge),ink,Color.TRANSPARENT,13)
		for i in range(1,5):draw_line(Vector2(118,1104-i*59),Vector2(149,1104-i*59),Color(1,1,1,.45),2,true)
		words(str(power)+"%",Vector2(133,775),44,Color("fff0bc"))
		if model.phase=="aim" and model.actor==0:
			var anchor:=aim_anchor();var radius:float=65 if reduced else 65+sin(time*3)*3
			draw_circle(anchor+Vector2(4,7),radius+3,Color("071f2b30"),true,-1,true)
			draw_circle(anchor,radius,Color("14394536"),true,-1,true)
			draw_arc(anchor,radius,0,TAU,64,Color(1,1,1,.60),3,true)
			if not aiming:
				words("PULL & LET GO",anchor-Vector2(0,95),40,Color.WHITE,true)
				if model.stage<3:
					words(["AIM STRAIGHT UP","AIM AT A LOWER SUPPORT","PULL SIDEWAYS TO AIM"][model.stage],
						Vector2(500,662),31,Color("ffe7a0"),true)
				elif model.stage==3 and model.shapes[0].has("bridge_role"):
					words("CUT A PIER OR HIT THE FUSE",Vector2(500,662),31,Color("ffe7a0"),true)
				elif model.stage==4 and model.shapes[0].has("junction_role"):
					words("BREAK THE GLASS STOP",Vector2(500,662),31,Color("a6e5ff"),true)
				elif model.stage==5 and model.shapes.size()==26 and model.shapes[23].bomb and (not blocks[23].out or not blocks[24].out):
					words("HIT THE TWIN FUSES",Vector2(500,662),31,Color("ffe7a0"),true)
				elif model.stone_press_column(8) and (not blocks[8].out or not blocks[9].out or not blocks[10].out):
					words("SHATTER THE GLASS COLUMNS",Vector2(500,662),31,Color("c7f2ff"),true)
				elif model.balance_brace(7) and (not blocks[7].out or not blocks[9].out):
					words("CUT AN END BRACE",Vector2(500,662),31,Color("c7f2ff"),true)
				elif model.lantern_support(4) and not blocks[16].out:
					words("DROP THE LANTERN VAULT",Vector2(500,662),31,Color("ffe7a0"),true)
				elif model.stage==9 and model.shapes.size()==34 and model.shapes[18].bomb and not blocks[18].out:
					words("HIT THE UPPER FUSE",Vector2(500,662),31,Color("ffe7a0"),true)
				elif model.last_straw_spindle(16) and not blocks[16].out:
					words("SNAP THE GLASS SPINDLE",Vector2(500,662),31,Color("c7f2ff"),true)
				elif model.finale_charge(9) and (not blocks[9].out or not blocks[13].out):
					words("FIRE INTO THE TEMPLE CORE",Vector2(500,662),31,Color("ffe7a0"),true)
				var tail:=anchor-Vector2(0,21)
				var tip:=anchor+Vector2(0,23)
				var left:=anchor+Vector2(-12,8)
				var right:=anchor+Vector2(12,8)
				var shadow:=Vector2(3,5)
				draw_polyline(PackedVector2Array([tail+shadow,tip+shadow,left+shadow,tip+shadow,right+shadow]),Color("071f2b"),7,true)
				draw_polyline(PackedVector2Array([tail,tip,left,tip,right]),Color.WHITE,5,true)
			else:
				draw_line(down,pointer,Color(1,1,1,.55),3,true)
				var cue := "PULL FARTHER TO FIRE" if preview_input.y<10 else ("LET IT RIP!" if power>93 else "RELEASE TO FIRE")
				words(cue,Vector2(500,760),30,Color("ffeab1"))
				var dots:PackedVector2Array=preview_path.get("points",PackedVector2Array())
				for i in dots.size():
					draw_circle(dots[i],7.5,Color("103745bb"))
					draw_circle(dots[i],4.8,Color("fff1ad"))
				var target:Vector2=preview_path.get("contact",Vector2.INF)
				if target.is_finite() and Rect2(50,28,900,1190).has_point(target):
					draw_arc(target,18,0,TAU,28,Color("103745"),7,true)
					draw_arc(target,18,0,TAU,28,Color("ffe7a0"),3,true)
					draw_circle(target,4,Color("fff1ad"))
	if model.phase=="flight" and model.world!=null:
		var ball=model.world.bodies[model.platforms.size()]
		if ball.enabled:
			if ball.p.y<35:
				words("PANDA OVERHEAD",Vector2(clampf(ball.p.x,190,810),35),24,accent);draw_circle(Vector2(clampf(ball.p.x,50,950),55),9,accent)
			else:panda(ball.p,49,model.actor,ball.angle,1+.08*minf(1,ball.v.length()/1400))
	if model.bird_visible():duck(model.bird_at())
	if model.phase in["round","finished"] and size.y>250:
		var winner:int=model.result_winner()
		var tilt:=sin(time*2)*.07 if not reduced else 0.0
		var squash:=1+.045*sin(time*4) if not reduced else 1.0
		if winner<0:
			panda(Vector2(400,925),85,0,tilt,squash)
			panda(Vector2(600,925),85,1,-tilt,squash)
		else:
			panda(Vector2(500,925),105,winner,tilt,squash)
	for ring in rings:draw_arc(ring.p,25+ring.age*310,0,TAU,60,Color(1,.92,.58,(1-ring.age/.7)*.8),4,true)
	for p in particles:
		var alpha:float=1-p.age/p.life
		draw_set_transform(point_to_screen(p.p),p.rotation,Vector2.ONE*factor())
		var r:float=p.radius
		var ink:=Color(p.color,alpha)
		match int(p.get("material",-1)):
			2: draw_colored_polygon(PackedVector2Array([Vector2(-r,r),Vector2(.6*r,r*.35),Vector2(r,-1.6*r)]),ink)
			0,1: draw_rect(Rect2(-r*1.5,-r*.32,r*3,r*.64),ink)
			_: draw_rect(Rect2(-r,-r,r*2,r*2),ink)
		base()
	for f in floating:words(f.text,f.p,34+int(5*exp(-f.age*6)),Color(f.ink,minf(1,(1.35-f.age)*3)))
	if flash>0 and not reduced:draw_rect(Rect2(0,0,1000,1200),Color(1,.97,.8,flash*.28))
	draw_set_transform(Vector2.ZERO)
func ellipse(p:Vector2,radius:Vector2,ink:Color):
	var polygon:=PackedVector2Array()
	for i in range(32):polygon.append(p+Vector2(cos(i*TAU/32)*radius.x,sin(i*TAU/32)*radius.y))
	draw_colored_polygon(polygon,ink)
func cannon_color()->Color:return HELMET_COLORS[model.actor]
func cannon():
	var ink:=cannon_color()
	var pivot:=Vector2(500,1090)+Vector2(0,recoil*20)
	ellipse(pivot+Vector2(0,87),Vector2(110,16),Color(0,.13,.21,.22))
	for x in [-66,66]:
		draw_circle(pivot+Vector2(x,62),26,Color("2b4858"));draw_circle(pivot+Vector2(x,62),18,Color("6c929b"));draw_circle(pivot+Vector2(x,62),7,ink.lightened(.65))
	draw_set_transform(point_to_screen(pivot),deg_to_rad(aim),Vector2(.86,.94)*factor()*(1-recoil*.08))
	var shape:=PackedVector2Array([Vector2(-68,-103),Vector2(68,-103),Vector2(88,0),Vector2(70,42),Vector2(0,61),Vector2(-70,42),Vector2(-88,0)])
	draw_colored_polygon(shape,ink.darkened(.15))
	for i in range(9):
		var x:float=-61+i*15;draw_line(Vector2(x,-99),Vector2(x*.9,18+24*(1-absf(x)/80)),ink.lightened(.4).lerp(ink.darkened(.28),i/9.0),14,true)
	for x in [-35,35]:
		var star:=PackedVector2Array()
		for i in range(10):star.append(Vector2(x,-47)+Vector2.from_angle(-PI/2+i*PI/5)*(13 if i%2==0 else 6))
		draw_colored_polygon(star,Color("fff4d3"))
	rounded(Rect2(-78,-119,156,25),ink.lightened(.25),ink.lightened(.7),8)
	draw_line(Vector2(-70,-113),Vector2(70,-113),Color("fff6d4"),4,true)
	base()
func duck(p:Vector2):
	var bird:Dictionary=model.bird
	draw_set_transform(point_to_screen(p),0,Vector2(bird.direction,1)*factor()*float(bird.size))
	var color:=Color("ffc668");draw_circle(Vector2.ZERO,39,color);draw_circle(Vector2(27,-20),25,color.lightened(.12))
	draw_colored_polygon(PackedVector2Array([Vector2(46,-15),Vector2(70,-9),Vector2(47,-3)]),Color("e8884b"))
	draw_circle(Vector2(35,-24),4,Color("284751"));draw_arc(Vector2(-8,sin(time*18)*6 if not reduced else 0),21,.2,PI,20,Color("dca54b"),6,true)
	base()
