extends Control
signal placed(id:int,pos:Vector2,rot:bool)
signal fired(aim:int,power:int)
signal pulled
const PALETTES:=[Color("ffbe54"),Color("52d7cc"),Color("b898ef"),Color("fa829b")]
const PORTRAITS:=[preload("res://panda/art/piece1.webp"),preload("res://panda/art/piece2.webp")]
var model
var draft:=""
var local_turn:=false
var reduced:=false
var drag_id:=-1
var pointer_id:=-1
var pointer:=Vector2.ZERO
var down:=Vector2.ZERO
var rotate_piece:=false
var aiming:=false
var aim:=0
var power:=65
var particles:Array=[]
var floating:Array=[]
var trail:Array=[]
var rings:Array=[]
var time:=0.0
var flash:=0.0
var shake:=0.0
var special:=0
var load_age:=1.0
var last_ball:=Vector2.ZERO
func factor()->float:return minf(size.x/760,size.y/1400)
func origin()->Vector2:return Vector2(size.x*.5-500*factor(),size.y-1380*factor())+(Vector2(sin(time*90),cos(time*74))*shake*3 if not reduced else Vector2.ZERO)
func world(p:Vector2)->Vector2:return (p-origin())/factor()
func _ready():mouse_filter=Control.MOUSE_FILTER_STOP;gui_input.connect(input_event)
func _process(dt):
	time+=dt;flash=maxf(0,flash-dt*2);shake=maxf(0,shake-dt*3);load_age=minf(1,load_age+dt/.9)
	for p in particles:p.age+=dt;p.p+=p.v*dt;p.v.y+=220*dt
	particles=particles.filter(func(p):return p.age<p.life)
	for ring in rings:ring.age+=dt
	rings=rings.filter(func(ring):return ring.age<ring.life)
	for p in floating:p.age+=dt;p.p.y-=dt*55
	floating=floating.filter(func(p):return p.age<1.2)
	for p in trail:p.age+=dt
	trail=trail.filter(func(p):return p.age<.5)
	if model!=null and model.phase=="flight" and model.world!=null:
		var ball=model.world.bodies[model.decks().size()]
		if ball.enabled and (ball.p-last_ball).length()>10:
			last_ball=ball.p;trail.append({"p":ball.p,"age":0.0})
			if trail.size()>35:trail.pop_front()
	queue_redraw()
func burst(p:Vector2,color:Color,strong:bool=false):
	flash=.23 if strong else .07;shake=.8 if strong else .22
	if reduced:return
	if strong:
		rings.append({"p":p,"color":color,"age":0.0,"life":1.0})
		if rings.size()>8:rings.pop_front()
	var count:=32 if strong else 13
	for i in range(count):
		var angle:=TAU*i/count+time;var speed:float=(350+(i%5)*120) if strong else (150+(i%5)*70)
		particles.append({"p":p,"v":Vector2.from_angle(angle)*speed,"age":0.0,"life":(1.1 if strong else .5)+(i%4)*.1,"color":color,"size":5+i%5})
	while particles.size()>160:particles.pop_front()
func points(p:Vector2,text:String):
	floating.append({"p":p,"age":0.0,"text":text})
	if floating.size()>20:floating.pop_front()
func cancel():drag_id=-1;pointer_id=-1;aiming=false
func input_event(event:InputEvent):
	if model==null or not local_turn:return
	var press:=false;var release:=false;var moving:=false;var pos:=Vector2.ZERO;var id:=0
	if event is InputEventScreenTouch:press=event.pressed;release=not press;pos=event.position;id=event.index
	elif event is InputEventScreenDrag:moving=true;pos=event.position;id=event.index
	elif event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT:press=event.pressed;release=not press;pos=event.position
	elif event is InputEventMouseMotion:moving=true;pos=event.position
	else:return
	if press:
		if pointer_id!=-1:return
		down=world(pos);pointer=down;pointer_id=id
		if model.phase=="build":
			var best:=70.0/factor()*size.x/760
			for i in range(model.count()):
				var center:Vector2=model.point(draft,i) if model.present(draft,i) else tray(i)
				var dist:=center.distance_to(down)
				if dist<best:best=dist;drag_id=i;rotate_piece=model.rotated(draft,i) if model.present(draft,i) else false
		elif model.phase=="shoot" and (down.distance_to(aim_anchor())<110 or down.distance_to(model.muzzle(aim))<120):
			aiming=true;pulled.emit();update_aim()
	elif id==pointer_id and moving:pointer=world(pos);update_aim()
	elif id==pointer_id and release:
		pointer=world(pos)
		if drag_id>=0:
			if pointer.distance_to(down)<12 and model.present(draft,drag_id):rotate_piece=not rotate_piece
			placed.emit(drag_id,pointer,rotate_piece)
		elif aiming:
			update_aim()
			if pointer.distance_to(down)>18:fired.emit(aim,power)
		cancel()
func update_aim():
	if not aiming:return
	var d:=pointer-down;aim=clampi(roundi(-d.x*.3),-45,45);power=clampi(roundi(15+maxf(0,d.y)*.30),10,100)
func aim_anchor()->Vector2:return Vector2(500,920)
func tray(i:int)->Vector2:
	var columns:=mini(6,model.count());var rows:=ceili(model.count()/float(columns))
	return Vector2(168+(i%columns)*(664.0/maxi(1,columns-1)),1080-(rows-2)*65+(i/columns)*130)
func rect_round(rect:Rect2,color:Color,edge:Color=Color.TRANSPARENT,radius:int=12):
	var style:=StyleBoxFlat.new();style.bg_color=color;style.border_color=edge;style.set_border_width_all(2 if edge.a>0 else 0);style.set_corner_radius_all(radius);draw_style_box(style,rect)
func text_at(text:String,p:Vector2,font_size:int,color:Color,center:bool=true):
	var font=ThemeDB.fallback_font;var width:float=font.get_string_size(text,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
	draw_string(font,p-Vector2(width*.5 if center else 0,0),text,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,color)
func block(p:Vector2,dimensions:Vector2,angle:float,id:int,alpha:float=1.0):
	draw_set_transform(origin()+p*factor(),angle,Vector2.ONE*factor())
	var color:Color=PALETTES[model.kind(id)];color.a=alpha
	rect_round(Rect2(-dimensions*.5+Vector2(4,8),dimensions),Color(0,.1,.16,.28*alpha),Color.TRANSPARENT,9)
	rect_round(Rect2(-dimensions*.5,dimensions),color.darkened(.2),color.lightened(.2),9)
	rect_round(Rect2(-dimensions*.5+Vector2(4,3),dimensions-Vector2(8,10)),color,Color.TRANSPARENT,7)
	draw_line(Vector2(-dimensions.x*.5+10,-dimensions.y*.5+8),Vector2(dimensions.x*.5-10,-dimensions.y*.5+8),Color(1,1,1,.45*alpha),3,true)
	var h:=minf(dimensions.x,dimensions.y)*.2
	draw_circle(Vector2.ZERO,h,Color(0,.15,.2,.1*alpha))
	if model.revision>=3:
		match model.kind(id):
			0:draw_line(Vector2(-h,0),Vector2(h,0),Color(1,1,1,.65*alpha),3,true)
			1:draw_arc(Vector2.ZERO,h*.7,0,TAU,24,Color(1,1,1,.8*alpha),3,true)
			2:
				for x in [-1,1]:draw_line(Vector2(x*h*.4,-h*.6),Vector2(x*h*.4,h*.6),Color(1,1,1,.8*alpha),4,true)
			3:draw_colored_polygon(PackedVector2Array([Vector2(0,-h),Vector2(h,0),Vector2(0,h),Vector2(-h,0)]),Color(1,1,1,.55*alpha))
	else:draw_arc(Vector2.ZERO,h*.5,0,TAU,24,Color(1,1,1,.7*alpha),2,true)
	base()
func panda(p:Vector2,radius:float,character:int,angle:float=0.0,stretch:float=1.0):
	draw_set_transform(origin()+p*factor(),angle,Vector2(stretch,1.0/stretch)*factor());draw_texture_rect(PORTRAITS[character],Rect2(Vector2.ONE*-radius,Vector2.ONE*radius*2),false);base()
func _draw():
	if model==null or factor()<=0:return
	var offset:=origin()
	if not reduced:offset+=Vector2(sin(time*90),cos(time*74))*shake*4
	draw_set_transform(offset,0,Vector2.ONE*factor())
	# World drawings use a child canvas transform, restored after helper calls.
	draw_world()
	draw_set_transform(Vector2.ZERO)
func base():draw_set_transform(origin(),0,Vector2.ONE*factor())
func draw_world():
	base()
	# Gently drifting motes keep the environment alive without obscuring aim.
	if not reduced:
		for i in range(11):
			var p:=Vector2(150+fmod(i*77+sin(time*.4+i)*30,700),80+fmod(i*139-time*9,1080))
			draw_circle(p,2+sin(time+i),Color(1,.96,.6,.5))
	for d in model.decks():
		rect_round(Rect2(d[0]-8,d[2]+8,d[1]-d[0]+16,32),Color(0,.12,.15,.3),Color.TRANSPARENT,12)
		rect_round(Rect2(d[0]-6,d[2],d[1]-d[0]+12,30),Color("86603a"),Color("e4bf77"),10)
		rect_round(Rect2(d[0]-6,d[2]-5,d[1]-d[0]+12,13),Color("d8ec92"),Color("f3ffba"),6)
		for x in range(int(d[0])+12,int(d[1]),36):draw_line(Vector2(x,d[2]+4),Vector2(x-8,d[2]+22),Color("b68a4b"),3,true)
	if model.phase=="build":
		text_at("BUILD THE DEFENSE",Vector2(500,775),38,Color("fff3cf"))
		text_at("Drag to stack · tap a block to rotate",Vector2(500,827),29,Color("fff3cf"))
		var used:=0
		for i in range(model.count()):
			if model.present(draft,i):used+=1
		text_at("%d / %d BLOCKS PLACED"%[used,model.count()],Vector2(500,890),28,Color("fff3cf"))
		for i in range(model.count()):
			if i==drag_id:continue
			if model.present(draft,i):block(model.point(draft,i),model.dims(i),PI/2 if model.rotated(draft,i) else 0,i)
			else:
				var p:=tray(i);draw_circle(p+Vector2(0,24),28,Color(0,.18,.22,.18));block(p,model.dims(i)*.7,0,i)
		if drag_id>=0:
			var candidate:String=model.place(draft,drag_id,pointer,rotate_piece)
			if candidate!="":block(model.point(candidate,drag_id),model.dims(drag_id),PI/2 if rotate_piece else 0,drag_id,.4)
			block(pointer,model.dims(drag_id),PI/2 if rotate_piece else 0,drag_id)

	else:
		for i in range(model.blocks.size()):
			var b=model.blocks[i]
			if b.y<1450:
				block(Vector2(b.x,b.y),model.dims(i),b.angle,i,.45 if b.out else 1.0)
				if i==model.special_id() and not b.out and not model.special_claimed:
					var glow:float=.65+.35*sin(time*8) if model.special_pulsing() else .15
					draw_arc(Vector2(b.x,b.y),model.dims(i).length()*.55,0,TAU,48,Color(1,.95,.4,glow),5,true)
					text_at("★",Vector2(b.x,b.y+12),40,Color("fff4b3"))
	if model.phase in ["shoot","flight"]:
		var active_character:int=model.character if model.leg==0 else 1-model.character
		var colors:=[Color("ffd872"),Color("69e9ff"),Color("c1a5ff"),Color("ffbd63")]
		var accent:Color=colors[special if model.phase=="shoot" else model.powerup]
		for t in trail:
			if not reduced:draw_circle(t.p,24*(1-t.age/.5),Color(accent, .5*(1-t.age/.5)))
		var remaining:int=maxi(0,model.shot_limit()-model.shots)
		var reserve:int=maxi(0,remaining-(1 if model.phase=="shoot" else 0))
		for i in range(reserve):panda(Vector2(810,1140-i*114),46,active_character,0,1 if reduced else 1+.035*sin(time*3+i))
		var tilt:=deg_to_rad(aim)
		var loaded:Vector2=model.muzzle(aim)
		if model.phase=="shoot" and not reduced and load_age<1:
			var source:=Vector2(810,1140-reserve*114)
			loaded=source.lerp(loaded,load_age*load_age*(3-2*load_age));loaded.y-=sin(load_age*PI)*170
		if model.phase=="shoot":panda(loaded,58,active_character,tilt,1 if reduced else 1+.025*sin(time*3))
		cannon(tilt)
		# Tall left meter and bottom-anchored reserve remain visible throughout aiming.
		rect_round(Rect2(180,780,42,430),Color("173b4ed9"),Color("ffe3a0"),20)
		var charge:=power/100.0
		var charge_ink:=Color("80ecb1").lerp(Color("ffe08b"),charge)
		rect_round(Rect2(187,1203-416*charge,28,maxf(10,416*charge)),charge_ink,Color.TRANSPARENT,12)
		for i in range(1,5):draw_line(Vector2(184,1203-i*83),Vector2(218,1203-i*83),Color(1,1,1,.45),2,true)
		text_at(str(power)+"%",Vector2(201,757),48,Color("fff1c7"))
		if model.phase=="shoot":
			var anchor:=aim_anchor()
			draw_circle(anchor,72,Color(1,1,1,.1 if aiming else .17))
			draw_arc(anchor,72,0,TAU,64,Color(1,1,1,.55 if aiming else .85),4,true)
			if not aiming:
				text_at("PULL FROM HERE",anchor-Vector2(0,105),29,Color("ffffff"))
				draw_line(anchor+Vector2(0,-28),anchor+Vector2(0,27),Color(1,1,1,.8),4,true)
				draw_line(anchor+Vector2(0,27),anchor+Vector2(-13,12),Color(1,1,1,.8),4,true)
				draw_line(anchor+Vector2(0,27),anchor+Vector2(13,12),Color(1,1,1,.8),4,true)
			else:
				draw_line(down,pointer,Color(1,1,1,.4),3,true)
				text_at("FULL POWER!" if power>=98 else "RELEASE TO LAUNCH",Vector2(500,690),34,Color("fff3b6"))
		if model.phase=="shoot" and aiming:
			var speed:float=(1400+power*13)*(1.22 if special==1 else 1)
			var velocity:=Vector2(sin(tilt),-cos(tilt))*speed
			for i in range(1,16):
				var t:=i*.025;var point:Vector2=model.muzzle(aim)+velocity*t+Vector2(0,500*t*t)
				draw_circle(point,5*(1-i/20.0),Color(1,1,.85,1-i/20.0))
		elif model.phase=="flight" and model.world!=null:
			var ball=model.world.bodies[model.decks().size()]
			if ball.enabled:draw_circle(ball.p,57,Color(accent,.22));panda(ball.p,51,active_character,ball.angle,1)
		if model.duck_active():duck(model.duck_at())
		if model.powerup==3 and model.burst and flash>0:draw_arc(model.hit_point,120+(1-flash/.23)*180,0,TAU,64,Color("fff4b3"),8,true)
	for ring in rings:
		var progress:float=ring.age/ring.life;draw_arc(ring.p,28+progress*230,0,TAU,64,Color(ring.color,(1-progress)*.7),3,true)
	for p in particles:
		var alpha:float=1-p.age/p.life;draw_line(p.p-p.v*.025,p.p,Color(p.color,alpha*.65),maxf(1,p.size*alpha*.6),true);draw_circle(p.p,p.size*alpha,Color(p.color,alpha))
	for p in floating:
		var alpha:float=minf(1,(1.2-p.age)*3);text_at(p.text,p.p+Vector2(2,3),40,Color(0,.1,.2,alpha*.5));text_at(p.text,p.p,40,Color(1,.98,.75,alpha))
	if flash>0:draw_rect(Rect2(100,0,800,1400),Color(1,.95,.7,flash*.35))

func cannon(tilt:float):
	# Adapted from the original CannonArt: gold curved barrel, cream stars and fixed cool stand.
	var pivot:=Vector2(500,1232 if model.revision>=3 else 1200 if model.revision>=2 else 1140)
	draw_set_transform(origin()+pivot*factor(),tilt,Vector2.ONE*factor()*.8)
	var shape:=PackedVector2Array([Vector2(-83,-119),Vector2(83,-119),Vector2(102,-27),Vector2(104,12),Vector2(80,48),Vector2(0,65),Vector2(-80,48),Vector2(-104,12),Vector2(-102,-27)])
	draw_colored_polygon(shape,Color("df9f32"))
	for i in range(10):
		var x:float=-78+i*16;draw_line(Vector2(x,-110),Vector2(x*.92,15+35*(1-absf(x)/90)),Color("fff0a4").lerp(Color("bd7626"),float(i)/10),16,true)
	for cx in [-44,44]:
		var star:=PackedVector2Array()
		for i in range(10):star.append(Vector2(cx,-64)+Vector2.from_angle(-PI/2+i*PI/5)*(15 if i%2==0 else 6))
		draw_colored_polygon(star,Color("fff4d9"))
	rect_round(Rect2(-94,-134,188,27),Color("ffd574"),Color("fff1b6"),9)
	draw_line(Vector2(-85,-129),Vector2(85,-129),Color("fff9ec"),5,true)
	rect_round(Rect2(-87,-109,174,7),Color("925c2b"),Color.TRANSPARENT,3)
	base()
	draw_colored_polygon(PackedVector2Array([pivot+Vector2(-58,66),pivot+Vector2(-30,4),pivot+Vector2(0,-5),pivot+Vector2(30,4),pivot+Vector2(58,66)]),Color("5c758b"))
	draw_circle(pivot+Vector2(0,32),18,Color("7894a9"));draw_circle(pivot+Vector2(0,32),7,Color("fff4d9"))
	rect_round(Rect2(pivot+Vector2(-116,65),Vector2(232,16)),Color("5c758b"),Color("b2c6ce"),7)
	rect_round(Rect2(pivot+Vector2(-130,80),Vector2(260,10)),Color("344b5e"),Color.TRANSPARENT,5)
func duck(p:Vector2):
	var profile:Dictionary=model.bird_profile() if model.revision>=3 else {"size":1.0,"direction":1,"hue":.13}
	var scale_value:float=profile.size;var direction:float=profile.direction
	draw_set_transform(origin()+p*factor(),0,Vector2(direction,1)*factor()*scale_value)
	var flap:float=sin(time*17)*12 if not reduced else 0
	var color:=Color.from_hsv(profile.hue,.58,.98)
	draw_circle(Vector2.ZERO,47,color);draw_circle(Vector2(31,-29),30,color.lightened(.22))
	draw_circle(Vector2(42,-33),5,Color("23384b"));draw_circle(Vector2(43,-35),1.5,Color.WHITE)
	draw_colored_polygon(PackedVector2Array([Vector2(53,-21),Vector2(80,-13),Vector2(53,-7)]),Color("ffac44"))
	draw_arc(Vector2(-10,flap),26,.2,PI,20,color.darkened(.25),7,true)
	draw_colored_polygon(PackedVector2Array([Vector2(-37,0),Vector2(-71,-17),Vector2(-60,14)]),color.darkened(.12))
	base();text_at("+250",p+Vector2(0,-80*scale_value),27,Color("fff4bf"))
