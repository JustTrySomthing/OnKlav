extends RefCounted
## Solo-only demolition duel. Presentation never owns scores, turns, RNG or rewards.
const Physics=preload("res://panda/duel_physics.gd")
const LegacyPhysics=preload("res://panda/physics.gd")
const Legacy=preload("res://panda/model.gd")
const TITLES:=["Wobbly Welcome","The Leaning Lunch","Bamboo Sandwich","Bridge Too Far","Jellybean Junction","Double Trouble","Stone Cold Silly","The Great Balancing Act","Lantern Lunacy","Temple of Tumbles","The Last Straw","Crash Grand Finale"]
const SCENES:=["Sunshine Lagoon","Bamboo Bluffs","Golden Temple"]
const LIMIT:=12
const SHOT_TICK_SPAN:=2
const PANDA_RADIUS:=43.0
var stage:=0
var seed_value:=17
var phase:="aim"
var actor:=0
var totals:=[0,0]
var wins:=[0,0]
var stars:Array=[]
var shapes:Array=[]
var platforms:Array=[]
var boards:Array=[]
var world
var legacy_flight:=false
var legacy_revision:=0
var physics_revision:=3
var wake_state:Array=[]
var tower_awake:=false
var ticks:=0
var quiet:=0
var clock:=0.0
var bird:Dictionary={}
var last_bird_turn:=-10
var player_turns:=0
var shot_points:=0
var shot_toppled:=0
var shot_hits:=0
var shot_bridge:=0
var shot_junction:=false
var shot_double:=0
var shot_vault:=false
var popped:Array=[]
var events:Array=[]
var hit_power:=0.0
var hit_point:=Vector2.ZERO
var flight_limit:=1200
var completed:=false
var last_aim:=0
var last_power:=60
func _init(value:int=17):seed_value=clampi(value,1,1000000);setup_stage()
func level()->int:return mini(2,stage/4)
func title()->String:return TITLES[stage]
func muzzle(angle:float=0)->Vector2:return Vector2(500,1090)+Vector2(0,-112).rotated(deg_to_rad(angle))
static func points(raw:float)->int:return roundi(raw*.1)
func tick_span()->int:return 1 if legacy_flight or physics_revision<2 else 2
static func speed(power:int)->float:return 910.0+power*6.0
func add_piece(x:float,y:float,w:float,h:float,kind:int,bomb:bool=false):
	shapes.append({"x":x,"y":y,"w":w,"h":h,"kind":kind,"bomb":bomb,"base_angle":0.0})
func tower(x:float,y:float,tier:int,kind:int=0):
	for n in range(tier):
		var yy:=y-n*137
		add_piece(x-65,yy-54,44,108,(kind+n)%4)
		add_piece(x+65,yy-54,44,108,(kind+n+1)%4)
		add_piece(x,yy-122,204,28,kind)
	add_piece(x,y-tier*137-24,78,48,1)
func bay(rng:RandomNumberGenerator,x:float,y:float,span:float,tiers:int,kind:int=0):
	for row in range(tiers):
		var floor_y:=y-row*94
		var posts:=[-span*.5+22,0.0,span*.5-22]
		if stage>1 and span>=170:
			posts[0]+=rng.randi_range(0,10)
			posts[1]+=rng.randi_range(-7,7)
			posts[2]-=rng.randi_range(0,10)
		for dx in posts:add_piece(x+dx,floor_y-35,44,70,kind)
		add_piece(x,floor_y-82,span,24,0 if kind==3 else kind)
func cargo(x:float,y:float,count:int,columns:int=4,kind:int=1):
	for i in range(count):add_piece(x+((i%columns)-(columns-1)*.5)*42,y-15-(i/columns)*30,42,30,kind)
func geometry():
	shapes=[];platforms=[]
	var rng:=RandomNumberGenerator.new()
	rng.seed=seed_value*104729+stage*7919
	match stage:
		0: # A clear line up the middle introduces the pull and release.
			platforms=[[135.0,865.0,535.0]]
			add_piece(500,490,48,90,2)
			add_piece(500,433,156,24,0)
			cargo(500,421,5,3,1)
			bay(rng,250,535,152,1,1);bay(rng,750,535,152,1,1)
		1: # Cut the lower glass brace under an offset stone load.
			platforms=[[330.0,670.0,500.0]]
			add_piece(419,466,44,68,2);add_piece(500,466,44,68,1);add_piece(581,466,44,68,3)
			add_piece(500,420,224,24,0)
			bay(rng,471,408,156,1,1)
			cargo(461,314,6,3,3)
			cargo(577,408,4,1,2)
		2: # Choose a side and learn to steer between two elevations.
			platforms=[[160.0,420.0,500.0],[590.0,850.0,410.0]]
			bay(rng,290,500,208,1,1);cargo(290,406,6,3,0)
			for x in [648,720,792]:add_piece(x,376,42,68,2)
			add_piece(720,330,206,24,3)
			cargo(720,318,6,3,3)
		3: # One span, two load paths. The fuse pier trades precision for blast radius.
			platforms=[[150.0,430.0,500.0],[570.0,850.0,500.0]]
			add_piece(402,456,42,88,1);shapes[-1].bridge_role=1
			add_piece(598,456,46,88,2,true);shapes[-1].bridge_role=2
			add_piece(500,400,244,24,0)
			cargo(500,388,3,3,3)
			bay(rng,273,500,156,1,3);bay(rng,727,500,156,1,3)
			cargo(273,406,4,3,1);cargo(727,406,4,3,2)
		4: # Cut a glass stop to send a stone weight into the lower receiving tower.
			platforms=[[130.0,380.0,510.0],[405.0,595.0,380.0],[620.0,870.0,475.0]]
			add_piece(430,351,32,58,2);shapes[-1].junction_role=1
			add_piece(476,348,58,64,3);shapes[-1].junction_role=2
			add_piece(520,345,36,70,1);add_piece(565,345,36,70,1)
			add_piece(545,298,108,24,0);cargo(545,286,3,3,2)
			bay(rng,276,510,170,1,0);cargo(276,416,4,4,2)
			bay(rng,740,475,170,1,3);cargo(740,381,4,4,1)
		5: # Two fuse crates straddle the join between the towers.
			platforms=[[180.0,820.0,460.0]]
			bay(rng,340,460,224,2,3);bay(rng,660,460,224,2,0)
			add_piece(500,260,128,24,1)
			cargo(340,272,3,3);cargo(660,272,3,3)
			add_piece(460,224,40,48,2,true)
			add_piece(540,224,40,48,2,true)
			add_piece(500,233,38,30,3)
		6: # A stone press rests on three fragile glass columns.
			platforms=[[130.0,350.0,520.0],[390.0,610.0,420.0],[670.0,890.0,320.0]]
			bay(rng,240,520,188,1,3);cargo(240,426,4,4,1)
			for x in [444,500,556]:add_piece(x,386,34,68,2)
			add_piece(500,331,188,42,3)
			cargo(500,310,4,4,3)
			cargo(500,280,4,4,3)
			bay(rng,780,320,188,1,0);cargo(780,226,4,4,2)
		7: # Break an end brace to tip a loaded beam over its stone fulcrum.
			platforms=[[100.0,260.0,520.0],[350.0,650.0,450.0],[740.0,900.0,360.0]]
			bay(rng,180,520,150,1,3);cargo(180,426,3,3)
			for x in [404,500,596]:
				add_piece(x,418,36,64,3 if x==500 else 2)
			add_piece(500,373,252,26,0)
			for side in [-1,1]:
				cargo(500+side*78,360,6,2,3 if side<0 else 1)
			bay(rng,820,360,150,1,0);cargo(820,266,3,3)
		8: # The lantern vault drops into a courtyard between its two towers.
			platforms=[[150.0,430.0,500.0],[444.0,556.0,500.0],[570.0,850.0,500.0]]
			bay(rng,290,500,220,2,3);bay(rng,710,500,220,2,3)
			for id in [4,5,6,12,13,14]:shapes[id].kind=2
			add_piece(500,300,244,24,0)
			add_piece(464,253,36,70,1);add_piece(536,253,36,70,1)
			add_piece(500,206,132,24,0)
			cargo(500,194,4,4,3)
			cargo(500,500,8,2,1)
		9: # The upper charge reaches a second charge on the descending terrace.
			platforms=[[125.0,280.0,520.0],[325.0,480.0,450.0],[525.0,680.0,370.0],[735.0,890.0,445.0]]
			bay(rng,203,520,150,1,0);cargo(203,426,3,3,2)
			bay(rng,403,450,150,1,1);cargo(403,356,3,3,2)
			bay(rng,603,370,150,1,3);cargo(603,276,3,3,2)
			bay(rng,813,445,150,1,3);cargo(813,351,3,3,1)
			cargo(203,396,3,3,2);cargo(813,321,3,3,2)
			shapes[11].bomb=true;shapes[18].bomb=true
		10: # A tall glass spindle carries the loaded roof between two wings.
			platforms=[[100.0,230.0,380.0],[250.0,850.0,500.0]]
			bay(rng,337,500,144,2,3);bay(rng,663,500,144,2,1)
			add_piece(500,425,40,150,2)
			add_piece(500,337,178,26,3)
			cargo(500,324,7,4,3)
			cargo(337,312,2,2,2);cargo(663,312,3,3,1)
			bay(rng,165,380,120,1,3);cargo(165,286,2,2,2)
		11: # Twin charges sit under the crowned temple's three floors.
			platforms=[[105.0,275.0,520.0],[320.0,680.0,480.0],[720.0,895.0,430.0],[110.0,290.0,300.0]]
			bay(rng,190,520,160,1,3);cargo(190,426,4,3)
			bay(rng,500,480,264,3,3)
			for id in [9,13]:
				shapes[id].kind=2
				shapes[id].bomb=true
			add_piece(500,177,164,42,3)
			cargo(500,156,3,3,2)
			add_piece(500,111,44,30,3)
			bay(rng,810,430,160,1,3);cargo(810,336,4,3)
			bay(rng,200,300,160,1,0);cargo(200,206,3,3)
	vary_course(rng)

func vary_course(rng:RandomNumberGenerator):
	# Mirror or resize the whole layout so connected bridges retain their bearings.
	var stretch:=rng.randi_range(54,63)/64.0
	var direction:=1 if rng.randf()<.5 else -1
	var shift:=rng.randi_range(-1,1)*floori(400*(1-stretch)*.75)
	var lift:=rng.randi_range(40,50)
	var separate:=stage in [2,4,6,7,9]
	var deck_lifts:Array[int]=[]
	for deck in platforms:
		deck_lifts.append(lift if not separate else mini(int(deck[2])-250,lift+rng.randi_range(-16,16)))
	for piece in shapes:
		var piece_lift:=lift
		if separate:
			for i in range(platforms.size()):
				var deck:Array=platforms[i]
				if piece.x>=deck[0] and piece.x<=deck[1]:
					piece_lift=deck_lifts[i]
					break
		piece.x=500+shift+(piece.x-500)*stretch*direction
		piece.y-=piece_lift
		piece.w*=stretch
		# Reinforce some load-bearing posts on later rounds. Leave weaker
		# routes into every structure, including the wooden bridge spans.
		if stage>0 and piece.h>=70 and piece.kind!=3 and not piece.has("bridge_role") and not (stage in [8,10] and piece.kind==2) and not piece.bomb and rng.randf()<.40:
			piece.kind=3
	for i in range(platforms.size()):
		var deck:Array=platforms[i]
		var left:float=500+shift+(deck[0]-500)*stretch*direction
		var right:float=500+shift+(deck[1]-500)*stretch*direction
		platforms[i]=[minf(left,right),maxf(left,right),deck[2]-deck_lifts[i]]
func fresh_board()->Dictionary:
	var blocks:=[]
	for s in shapes:blocks.append({"x":s.x,"y":s.y,"angle":s.get("base_angle",0.0),"out":false,"damage":0.0,"last_hit":-100})
	return {"blocks":blocks,"shots":0,"limit":3,"score":0,"bonus_claimed":false}
func setup_stage():
	shot_vault=false;shot_double=0;shot_junction=false;shot_bridge=0;geometry();boards=[fresh_board(),fresh_board()];actor=0;phase="aim";world=null;legacy_flight=false;legacy_revision=0;ticks=0;quiet=0;clock=0;shot_points=0;shot_toppled=0;shot_hits=0;events=[];popped=[];flight_limit=1200;completed=false;wake_state=[]
	prepare_bird()
func prepare_bird():
	bird={}
	if actor!=0:return
	player_turns+=1
	var rng:=RandomNumberGenerator.new();rng.seed=seed_value*1009+stage*7919+boards[0].shots*173
	# At most one brief visit every four player turns, with only a 12% chance.
	if player_turns<3 or player_turns-last_bird_turn<4 or rng.randf()>=.12:return
	last_bird_turn=player_turns
	bird={"delay":rng.randf_range(1.2,3.6),"height":rng.randf_range(85,200),"direction":1 if rng.randf()<.5 else -1,"age":0.0,"hit":false,"size":rng.randf_range(.5,.66),"wave":rng.randf_range(8,16),"speed":rng.randf_range(175,225)}
	for key in ["delay","height","age","size","wave","speed"]:bird[key]=precise(bird[key])
func bird_at()->Vector2:
	if bird.is_empty():return Vector2(-500,-500)
	var age:float=bird.age-bird.delay
	var x:float=-80+age*bird.speed
	return Vector2(x if bird.direction==1 else 1000-x,bird.height+sin(age*2.6)*bird.wave)
func bird_visible()->bool:
	return not bird.is_empty() and not bird.hit and bird.age>=bird.delay and bird.age<=bird.delay+1160/bird.speed and phase in["aim","flight"]
func advance_clock(dt:float):
	if phase!="aim":return
	clock+=dt
	if not bird.is_empty():bird.age=precise(bird.age+dt)
func launch_velocity(angle:int,power:int)->Vector2:
	return Vector2(sin(deg_to_rad(angle)),-cos(deg_to_rad(angle)))*speed(power)

func aim_preview(angle:int,power:int)->Dictionary:
	var points := PackedVector2Array()
	var contact := Vector2.INF
	var blocks:Array = boards[actor].blocks
	var targets:Array = []
	var bottom := 0.0
	for i in blocks.size():
		var block:Dictionary = blocks[i]
		if block.out:continue
		var shape:Dictionary = shapes[i]
		var extent := Vector2(shape.w,shape.h)*.5
		targets.append([Vector2(block.x,block.y),extent,-float(block.angle),pow(extent.length()+PANDA_RADIUS,2.0)])
		bottom = maxf(bottom,float(block.y)+extent.length()+PANDA_RADIUS)
	var dt := Physics.DT*SHOT_TICK_SPAN
	var p := muzzle(angle)
	var velocity := launch_velocity(angle,power)
	for step in range(1,361):
		velocity.x *= Physics.HORIZONTAL_DRAG
		velocity.y += Physics.GRAVITY*dt
		p += velocity*dt
		if p.y <= bottom:
			for target in targets:
				var offset:Vector2 = p-target[0]
				if offset.length_squared()>=target[3]:continue
				var local := offset.rotated(target[2])
				var extent:Vector2 = target[1]
				if local.distance_squared_to(local.clamp(-extent,extent)) < PANDA_RADIUS*PANDA_RADIUS:
					contact = p
					break
		if velocity.y >= 0.0 and not contact.is_finite():
			for deck in platforms:
				# Cannon shots pass upward through shelves, and collide on descent.
				if p.y > deck[2]+1.0:continue
				var closest := Vector2(clampf(p.x,deck[0],deck[1]),float(deck[2]))
				if p.distance_squared_to(closest) < PANDA_RADIUS*PANDA_RADIUS:
					contact = p
					break
		if contact.is_finite():break
		if p.y>1270 or absf(p.x-500)>850:break
		if step%7==0 and Rect2(50,28,900,1190).has_point(p):points.append(p)
	return {"points":points,"contact":contact}

func launch(angle:int,power:int)->bool:
	if phase!="aim" or angle< -42 or angle>42 or power<10 or power>100:return false
	var board:Dictionary=boards[actor]
	if board.shots>=board.limit or off(actor)==shapes.size():return false
	shot_vault=false;shot_double=0;shot_junction=false;shot_bridge=0;last_aim=angle;last_power=power;board.shots+=1;phase="flight";ticks=0;quiet=0;world=Physics.new();world.tick_span=SHOT_TICK_SPAN;world.tower_awake=false;tower_awake=false;physics_revision=3;legacy_flight=false;shot_points=0;shot_toppled=0;shot_hits=0;flight_limit=1200
	for d in platforms:
		var p=world.add(Vector2((d[0]+d[1])*.5,d[2]+20),Vector2((d[1]-d[0])*.5,20),0);p.platform=true
	var ball=world.add(muzzle(angle),Vector2.ONE*PANDA_RADIUS,3.25,true);ball.passes=true;ball.bounce=.2
	ball.v=launch_velocity(angle,power);ball.spin=ball.v.x/38
	for i in range(shapes.size()):
		var b:Dictionary=board.blocks[i];b.last_hit=-100;var s:Dictionary=shapes[i]
		var body=world.add(Vector2(b.x,b.y),Vector2(s.w,s.h)*.5,s.w*s.h/4096*[.9,.58,.42,1.65][int(s.kind)])
		body.angle=b.angle;body.enabled=not b.out;material(body,int(s.kind));body.bounds()
	canonical_masses()
	world.awake_mask=[]
	for body in world.bodies:world.awake_mask.append(body.platform or body.circle)
	world.build_groups();wake_state=world.awake_mask.duplicate();flight_limit=2400
	return true
func material(body,kind:int):body.friction=[.55,.43,.25,.65][kind];body.bounce=[.08,.18,.1,.025][kind]
func canonical_masses():
	for b in world.bodies:b.inv=round(b.inv*1e10)/1e10;b.inertia=round(b.inertia*1e12)/1e12
func off(side:int=-1)->int:
	var result:=0
	for b in boards[actor if side<0 else side].blocks:
		if b.out:result+=1
	return result
func stone_press_column(id:int)->bool:
	return stage==6 and shapes.size()==28 and id>=8 and id<=10 and shapes[id].kind==2

func stone_press_dropped()->bool:
	if shot_toppled<3 or not stone_press_column(8):return false
	var blocks:Array=boards[actor].blocks
	var press:Dictionary=blocks[11]
	if not press.out and press.y<shapes[11].y+25.0 and absf(press.angle)<.20:return false
	for i in range(8,11):
		if blocks[i].out and blocks[i].last_hit>=0:return true
	return false

func balance_brace(id:int)->bool:
	return stage==7 and shapes.size()==30 and id in [7,9] and shapes[id].kind==2

func balance_tipped()->bool:
	if shot_toppled<3 or not balance_brace(7):return false
	var blocks:Array=boards[actor].blocks
	var beam:Dictionary=blocks[10]
	if not beam.out and absf(beam.angle)<.28:return false
	for i in [7,9]:
		if blocks[i].out and blocks[i].last_hit>=0:return true
	return false

func lantern_support(id:int)->bool:
	return stage==8 and shapes.size()==32 and (id in [4,5,6,12,13,14]) and shapes[id].kind==2

func lantern_vault_fell()->bool:
	return shot_vault and shot_toppled>=3

func last_straw_spindle(id:int)->bool:
	return stage==10 and shapes.size()==36 and id==16 and shapes[id].kind==2 and shapes[id].h==150

func last_straw_fell()->bool:
	if shot_toppled<3 or not last_straw_spindle(16):return false
	var blocks:Array=boards[actor].blocks
	return blocks[16].out and blocks[16].last_hit>=0 and (blocks[17].out or blocks[17].y>shapes[17].y+35.0)

func finale_charge(id:int)->bool:
	return stage==11 and shapes.size()==40 and id in [9,13] and shapes[id].bomb

func award(id:int,shattered:bool=false):
	var board:Dictionary=boards[actor];var b:Dictionary=board.blocks[id]
	if b.out:return
	var body=world.bodies[platforms.size()+1+id]
	b.out=true;body.enabled=false;shot_toppled+=1
	if id==16 and lantern_support(4):shot_vault=true
	if stage == 3:
		shot_bridge |= int(shapes[id].get("bridge_role",0))
	if stage == 4 and shapes[id].get("junction_role",0)==1:
		var weight_index := platforms.size()+2
		var weight = world.bodies[weight_index]
		if weight.enabled and not legacy_flight:
			var direction := signf(shapes[id].x-shapes[1].x)
			world.wake(weight_index)
			weight.v += Vector2(direction*500.0,-145.0)
			weight.spin += direction*3.4
			shot_junction = true
			events.append({"kind":"counterweight","p":body.p})

	var points:=100+mini(4,(shot_toppled-1)/3)*25
	board.score+=points;totals[actor]+=points;shot_points+=points
	popped.append({"id":id,"p":body.p,"kind":shapes[id].kind,"shattered":shattered,"points":points})
	if shapes[id].bomb:
		var twin_fuse := stage == 5 and shapes.size()==26 and id in [23,24]
		if twin_fuse or finale_charge(id) or (stage==9 and shapes.size()==34 and id in [11,18]): shot_double += 1
		var first_blast:bool=not board.bonus_claimed
		board.bonus_claimed=true;board.score+=200;totals[actor]+=200;shot_points+=200;events.append({"kind":"blast","p":body.p})
		if stage>=9 and first_blast:board.limit=4;events.append({"kind":"extra","p":body.p})
		for j in range(shapes.size()):
			var neighbor=world.bodies[platforms.size()+1+j];var diff:Vector2=neighbor.p-body.p
			if neighbor.enabled and diff.length() < (205.0 if twin_fuse else 280.0):
				if not legacy_flight:world.wake(platforms.size()+1+j)
				neighbor.v+=diff.normalized()*((680.0 if twin_fuse else 820.0)*(1-diff.length()/360))+Vector2(0,-260);neighbor.spin+=(-1 if diff.x<0 else 1)*3
				# Detonate a neighboring fuse crate once; out is committed before recursion.
				if shapes[j].bomb and diff.length()<240:award(j,true)
static func precise(v:float)->float:return round(v*1000000.0)/1000000.0
func step():
	popped=[];events=[];hit_power=0
	if phase!="flight" or world==null:return
	world.step();ticks+=tick_span()
	var dt:=Physics.DT*tick_span()
	if not bird.is_empty():bird.age=precise(bird.age+dt)
	var ball=world.bodies[platforms.size()]
	if bird_visible() and ball.enabled and ball.p.distance_to(bird_at())<43+47*float(bird.size):
		bird.hit=true;boards[actor].score+=350;totals[actor]+=350;shot_points+=350;events.append({"kind":"bird","p":bird_at()})
	if ball.p.y>1270 or absf(ball.p.x-500)>850 or ticks>=840:ball.enabled=false
	if world.impact>130:
		hit_power=world.impact;hit_point=world.impact_point;shot_hits+=1
		var nearest:=-1;var distance:=INF
		for i in range(shapes.size()):
			var b:Dictionary=boards[actor].blocks[i]
			if b.out or ticks-int(b.last_hit)<28:continue
			var body=world.bodies[platforms.size()+1+i];var d:float=body.p.distance_to(hit_point)
			if d<distance and d<maxf(shapes[i].w,shapes[i].h)*.65+45:nearest=i;distance=d
		if nearest>=0:
			var b:Dictionary=boards[actor].blocks[nearest];b.last_hit=ticks;b.damage=precise(b.damage+minf(1.25,world.impact/([2100.0,1600.0,650.0,7000.0] if physics_revision>=3 and stage>0 else [1450.0,1050.0,540.0,3900.0])[int(shapes[nearest].kind)]))
			if b.damage>=1.0:award(nearest,true)
	var busy:=false
	for i in range(shapes.size()):
		var body=world.bodies[platforms.size()+1+i];var b:Dictionary=boards[actor].blocks[i]
		if not b.out and (body.p.y-body.extent.y>640 or body.p.x<70 or body.p.x>930 or ticks>100 and absf(wrapf(body.angle-float(shapes[i].get("base_angle",0)),-PI,PI))>(1.2 if shapes[i].kind==3 else 1.0)):award(i)
		if not b.out and ticks>=flight_limit and (body.v.length()>18 or absf(body.spin)>.18):award(i)
		if b.out:
			body.v.y=precise(minf(2000,body.v.y+1000*dt));body.p+=body.v*dt;body.p.x=precise(clampf(body.p.x,-2000,3000));body.p.y=precise(minf(1500,body.p.y));body.angle=precise(body.angle+body.spin*dt)
			if body.p.y<1240:busy=true
		elif body.v.length()>18 or absf(body.spin)>.18:busy=true
		b.x=body.p.x;b.y=body.p.y;b.angle=body.angle
	if not legacy_flight:tower_awake=world.tower_awake;wake_state=world.awake_mask.duplicate()
	quiet=quiet+tick_span() if ticks>170 and not busy and (not ball.enabled or ball.v.length()<70) else 0
	if not busy and (quiet>=70 or ticks>=flight_limit):
		world=null;wake_state=[];phase="report"
func eligible(side:int)->bool:return boards[side].shots<boards[side].limit and off(side)<shapes.size()
func turn_over()->bool:
	if phase!="report":return false
	# Complete the player's entire volley, then the rival's, including earned bonus shots.
	if eligible(0):actor=0
	elif eligible(1):actor=1
	else:finish_round();return true
	phase="aim";clock=0;prepare_bird();return true
func result_winner()->int:
	var player_score:int=totals[0] if phase=="finished" else boards[0].score
	var rival_score:int=totals[1] if phase=="finished" else boards[1].score
	if player_score==rival_score:return -1
	return 0 if player_score>rival_score else 1
func finish_round():
	phase="round";completed=true
	var won:=result_winner()
	if won>=0:wins[won]+=1
	var ratio:=float(off(0))/shapes.size();var rating:=3 if ratio>=.90 and won==0 else 2 if ratio>=.62 else 1 if ratio>=.3 else 0
	stars.append(rating)
	if stage==LIMIT-1:phase="finished"
func next_stage()->bool:
	if phase!="round" or stage>=LIMIT-1:return false
	stage+=1;setup_stage();return true
func ai_shot()->Vector2i:
	var rng:=RandomNumberGenerator.new();rng.seed=seed_value+stage*3571+boards[actor].shots*811
	var targets:=[]
	for i in range(shapes.size()):
		if not boards[actor].blocks[i].out:targets.append(i)
	if targets.is_empty():return Vector2i(0,65)
	# Prefer low supports with occasional deliberate, deterministic imperfect choices.
	targets.sort_custom(func(a,b):return boards[actor].blocks[a].y>boards[actor].blocks[b].y)
	var target:Dictionary=boards[actor].blocks[targets[rng.randi_range(0,mini(3,targets.size()-1))]]
	var best:=Vector2i(0,65);var error:=INF
	for p in [46,58,70,82,94]:
		for a in range(-38,39,2):
			var start:=muzzle(a);var velocity:=Vector2(sin(deg_to_rad(a)),-cos(deg_to_rad(a)))*speed(p)
			for step_id in range(5,80):
				var t:=step_id*.025;var pos:=start+velocity*t+Vector2(0,500*t*t);var distance:=pos.distance_squared_to(Vector2(target.x,target.y))
				if distance<error:error=distance;best=Vector2i(a,p)
	best.x=clampi(best.x+rng.randi_range(-4,4),-42,42);best.y=clampi(best.y+rng.randi_range(-6,6),10,100);return best
func snapshot()->Dictionary:
	return {"schema":4,"physics_revision":physics_revision,"wake_state":wake_state,"tower_awake":tower_awake,"turn_rule":"volley","legacy_flight":legacy_flight,"legacy_revision":legacy_revision,"stage":stage,"seed":seed_value,"phase":phase,"actor":actor,"totals":totals,"wins":wins,"stars":stars,"shapes":shapes,"platforms":platforms,"boards":boards,"ticks":ticks,"quiet":quiet,"clock":clock,"bird":bird,"last_bird_turn":last_bird_turn,"player_turns":player_turns,"shot_points":shot_points,"shot_toppled":shot_toppled,"shot_hits":shot_hits,"shot_bridge":shot_bridge,"shot_junction":shot_junction,"shot_double":shot_double,"shot_vault":shot_vault,"flight_limit":flight_limit,"completed":completed,"last_aim":last_aim,"last_power":last_power,"bodies":world.snapshot() if world!=null else []}
static func integer(v,lo:int,hi:int)->bool:return (v is int or v is float) and is_finite(float(v)) and float(v)==int(v) and v>=lo and v<=hi
static func number(v,lo:float,hi:float)->bool:return (v is int or v is float) and is_finite(float(v)) and v>=lo and v<=hi
func restore(data:Dictionary)->bool:
	var d:Dictionary=data.duplicate(true)
	if not integer(d.get("shot_bridge",0),0,3) or not d.get("shot_junction",false) is bool:return false
	if not integer(d.get("shot_double",0),0,2) or not d.get("shot_vault",false) is bool:return false
	if not integer(d.get("physics_revision",1),1,3) or not d.get("tower_awake",true)is bool:return false
	if d.has("turn_rule") and d.turn_rule!="volley":return false
	if not d.get("legacy_flight")is bool or not integer(d.get("legacy_revision"),0,3):return false
	if d.get("schema")!=4 or d.get("phase")not in["aim","flight","report","round","finished"]:return false
	for spec in [["stage",0,11],["seed",1,1000000],["actor",0,1],["ticks",0,4800],["quiet",0,4800],["last_bird_turn",-10,1000],["player_turns",0,1000],["shot_points",0,20000],["shot_toppled",0,40],["shot_hits",0,20000],["flight_limit",1200,2400],["last_aim",-42,42],["last_power",10,100]]:
		if not integer(d.get(spec[0]),spec[1],spec[2]):return false
		d[spec[0]]=int(d[spec[0]])
	if not number(d.get("clock"),0,10000000) or not d.get("completed")is bool:return false
	if d.completed!=(d.phase in["round","finished"]) or (d.phase=="finished" and d.stage!=11):return false
	for name in ["totals","wins"]:
		if not d.get(name)is Array or d[name].size()!=2:return false
		for i in range(2):
			if not integer(d[name][i],0,1000000):return false
			d[name][i]=int(d[name][i])
	if not d.get("stars")is Array or d.stars.size()!=d.stage+int(d.completed):return false
	for i in range(d.stars.size()):
		if not integer(d.stars[i],0,3):return false
		d.stars[i]=int(d.stars[i])
	if not d.get("shapes")is Array or d.shapes.size()<1 or d.shapes.size()>40 or not d.get("platforms")is Array or d.platforms.size()<1 or d.platforms.size()>4:return false
	for s in d.shapes:
		if not s is Dictionary or not number(s.get("x"),-10000,10000) or not number(s.get("y"),-10000,10000) or not number(s.get("w"),24,280) or not number(s.get("h"),24,160) or not integer(s.get("kind"),0,3) or not s.get("bomb")is bool or not number(s.get("base_angle"),-1000,1000):return false
		if not integer(s.get("bridge_role",0),0,2):return false
		if s.has("bridge_role"):s.bridge_role=int(s.bridge_role)
		if not integer(s.get("junction_role",0),0,2):return false
		if s.has("junction_role"):s.junction_role=int(s.junction_role)
		s.kind=int(s.kind)
	# Legacy layouts have no roles. New metadata must identify the exact pair.
	for i in d.shapes.size():
		var role:int = d.shapes[i].get("junction_role",0)
		if role != 0 and (d.stage!=4 or i!=role-1 or d.shapes.size()!=24):return false
	if d.shapes[0].get("junction_role",0)==1 and d.shapes[1].get("junction_role",0)!=2:return false
	for p in d.platforms:
		if not p is Array or p.size()!=3 or not number(p[0],100,850) or not number(p[1],150,900) or p[1]<=p[0] or not number(p[2],250,620):return false
	if not d.get("boards")is Array or d.boards.size()!=2:return false
	for b in d.boards:
		if not b is Dictionary or not integer(b.get("shots"),0,4) or not integer(b.get("limit"),3,4) or b.shots>b.limit or not integer(b.get("score"),0,100000) or not b.get("bonus_claimed")is bool or not b.get("blocks")is Array or b.blocks.size()!=d.shapes.size():return false
		b.shots=int(b.shots);b.limit=int(b.limit);b.score=int(b.score)
		for item in b.blocks:
			if not item is Dictionary or not item.get("out")is bool or not number(item.get("x"),-10000,10000) or not number(item.get("y"),-10000,10000) or not number(item.get("angle"),-1000,1000) or not number(item.get("damage"),0,10) or not integer(item.get("last_hit"),-100,4800):return false
			item.last_hit=int(item.last_hit)
	if not d.get("bird")is Dictionary:return false
	if not d.bird.is_empty():
		for spec in [["delay",1.2,3.6],["height",85,200],["age",0,10000000],["size",.5,.66],["wave",8,16],["speed",175,225]]:
			if not number(d.bird.get(spec[0]),spec[1],spec[2]):return false
		if not integer(d.bird.get("direction"),-1,1) or int(d.bird.direction)==0 or not d.bird.get("hit")is bool:return false
		d.bird.direction=int(d.bird.direction)
	if not d.get("bodies")is Array:return false
	var mask:Variant=d.get("wake_state",[])
	if not mask is Array or mask.size()>45:return false
	for flag in mask:
		if not flag is bool:return false
	if int(d.get("physics_revision",1))>=3 and d.phase=="flight" and mask.size()!=d.bodies.size():return false
	var candidate=LegacyPhysics.new() if d.legacy_flight else Physics.new()
	if not candidate.restore(d.bodies):return false
	if d.phase=="flight" and d.bodies.size()!=d.platforms.size()+1+d.shapes.size():return false
	if d.phase!="flight" and not d.bodies.is_empty():return false
	var active_board:Dictionary=d.boards[d.actor]
	var cleared:=true
	for block in active_board.blocks:
		if not block.out:cleared=false
	if d.phase=="aim" and (active_board.shots>=active_board.limit or cleared):return false
	if d.phase in["flight","report"] and active_board.shots<1:return false
	if d.phase=="flight":
		for i in range(candidate.bodies.size()):
			var body=candidate.bodies[i]
			if i<d.platforms.size():
				if body.circle or not body.platform or body.inv!=0 or not body.enabled or body.passes:return false
			elif i==d.platforms.size():
				if not body.circle or body.platform or body.inv<=0 or not body.passes:return false
			else:
				var id:int=i-d.platforms.size()-1
				var shape:Dictionary=d.shapes[id]
				if body.circle or body.platform or body.passes or body.inv<=0 or body.enabled==bool(active_board.blocks[id].out):return false
				if absf(body.h.x-shape.w*.5)>.0001 or absf(body.h.y-shape.h*.5)>.0001:return false
	for field in ["stage","phase","actor","totals","wins","stars","shapes","platforms","boards","ticks","quiet","clock","bird","last_bird_turn","player_turns","shot_points","shot_toppled","shot_hits","flight_limit","completed","last_aim","last_power"]:set(field,d[field])
	shot_bridge=int(d.get("shot_bridge",0))
	shot_junction=d.get("shot_junction",false)
	shot_double=int(d.get("shot_double",0))
	shot_vault=d.get("shot_vault",false)
	physics_revision=int(d.get("physics_revision",1));wake_state=mask.duplicate();tower_awake=bool(d.get("tower_awake",true))
	seed_value=d.seed;legacy_flight=d.legacy_flight;legacy_revision=int(d.legacy_revision);world=candidate if phase=="flight" else null
	if world!=null:
		if not legacy_flight:world.tick_span=tick_span();world.tower_awake=tower_awake;world.awake_mask=wake_state.duplicate();world.build_groups();world.bodies[platforms.size()].bounce=.2
		for i in range(shapes.size()):
			var body=world.bodies[platforms.size()+1+i];var kind:int=shapes[i].kind
			if not legacy_flight:material(body,kind)
			elif legacy_revision>=3:body.friction=[.55,.32,.68,.46][kind];body.bounce=[.09,.24,.035,.14][kind]
	# Older alternating-turn saves may have an un-fired rival turn queued.
	# Never interrupt a committed flight; return unused player shots at the next report.
	if not d.has("turn_rule") and phase=="aim" and actor==1 and eligible(0):
		actor=0;clock=0;prepare_bird()
	return true
func import_legacy(data:Dictionary)->bool:
	var old=Legacy.new()
	if not old.restore(data):return false
	seed_value=maxi(1,old.course_seed);stage=mini(11,old.level*4+mini(3,old.round_id));totals=[old.score(0),old.score(1)];wins=old.round_wins.duplicate();stars=[]
	for i in range(stage):stars.append(0)
	setup_stage();physics_revision=1;wake_state=[]
	if old.phase in["shoot","flight","review"] and not old.blocks.is_empty():
		shapes=[];platforms=old.decks().duplicate(true)
		for i in range(old.blocks.size()):
			var size:Vector2=old.dims(i);var b:Dictionary=old.blocks[i];add_piece(b.x,b.y,size.x,size.y,old.kind(i));shapes[-1].base_angle=b.angle
		boards=[fresh_board(),fresh_board()];actor=old.leg
		for i in range(old.blocks.size()):
			var b:Dictionary=boards[actor].blocks[i];b.x=old.blocks[i].x;b.y=old.blocks[i].y;b.angle=old.blocks[i].angle;b.out=old.blocks[i].out
		boards[actor].shots=old.shots;boards[actor].limit=old.shot_limit();boards[actor].score=old.off()*100
		if old.leg==1 and old.results.size()>0:boards[0].shots=3;boards[0].score=int(old.results[old.results.size()-1][0])*100
		phase="flight" if old.phase=="flight" else "report" if old.phase=="review" else "aim"
		world=old.world;legacy_flight=old.phase=="flight";legacy_revision=old.revision;ticks=old.ticks;quiet=old.quiet;flight_limit=1680;bird={};completed=false
		if phase=="aim" and not eligible(actor):
			phase="report"
			turn_over()
	if old.phase=="finished":
		stage=11;stars=[]
		for i in range(12):stars.append(0)
		phase="finished";completed=true
	return true
