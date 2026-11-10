extends RefCounted
const Physics=preload("res://panda/physics.gd")
const CELLS:="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_:"
const WIDTH:=[64,96,32,96]
const HEIGHT:=[64,32,96,96]
const SCENES:=["Forest Clearing","Bamboo Forest","Temple Courtyard"]
var revision:=3
var course_seed:=17
var bird_ticks:=0
var bonus:=[0,0]
var duck_hit:=false
var special_claimed:=false
var extra_shot:=false
var reward:=""
var level:=0
var round_id:=0
var leg:=0
var phase:="choose"
var turn:=0
var character:=0
var total:=[0,0]
var round_wins:=[0,0]
var level_rounds:=[0,0]
var level_wins:=[0,0]
var results:Array=[]
var shots:=0
var energy:=0
var charges:=[true,true,true]
var winner:=-2
var summary:=""
var last_round_winner:=-2
var blocks:Array=[]
var world
var ticks:=0
var quiet:=0
var burst:=false
var powerup:=0
var popped:Array=[]
var hit_power:=0.0
var hit_point:=Vector2.ZERO
var attack_off:=0
var rng_seed:=17
func count()->int:return 12+level*3 if revision>=3 else 8+level*2 if revision>=2 else 6+level*2+mini(3,round_id)/2
func muzzle(angle:float=0)->Vector2:
	return Vector2(500,1232 if revision>=3 else 1200)+Vector2(0,-140).rotated(deg_to_rad(angle)) if revision>=2 else Vector2(500,1040)
func shot_limit()->int:return 4 if extra_shot else 3
func score(actor:int)->int:return total[actor]*100+bonus[actor] if revision>=2 else total[actor]
func bird_profile()->Dictionary:
	var rng:=RandomNumberGenerator.new();rng.seed=course_seed+level*104729+round_id*7919+int(bird_ticks/1920)*3571
	return {"direction":1 if rng.randi_range(0,1)==0 else -1,"speed":rng.randf_range(105,185),"height":rng.randf_range(170,320),"wave":rng.randf_range(25,65),"size":rng.randf_range(.6,.95),"hue":rng.randf(),"offset":rng.randf_range(0,800),"frequency":rng.randf_range(1.4,2.8)}
func duck_at()->Vector2:
	if revision<3:return Vector2(lerpf(-80,1080,float(ticks-24)/480),820+sin(float(ticks)/90)*45)
	var profile:=bird_profile();var age:=float(bird_ticks%1920)/240
	var x:=fposmod(profile.offset+age*profile.speed,1160)-80
	return Vector2(x if profile.direction==1 else 1000-x,profile.height+sin(age*profile.frequency)*profile.wave+sin(age*4.1)*9)
func duck_active()->bool:
	if revision>=3:return phase in ["shoot","flight"] and not duck_hit
	return phase=="flight" and revision>=2 and (level*4+round_id)%3==1 and not duck_hit and ticks>=24 and ticks<=504
func density(id:int)->float:return [1.0,.65,1.65,1.2][kind(id)] if revision>=3 else 1.0
func material(body,id:int):
	if revision<3:return
	body.friction=[.55,.32,.68,.46][kind(id)];body.bounce=[.09,.24,.035,.14][kind(id)]
func special_id()->int:return count()-2 if revision>=2 and level>=1 and round_id%2==1 else -1
func special_pulsing()->bool:return special_id()>=0 and not special_claimed and ticks%240<150
func builder()->int:return 1-leg
func kind(id:int)->int:return (id+level+mini(round_id,3))%4
func dims(id:int,rot:bool=false)->Vector2:
	var widths:=[80,112,48,96] if revision>=2 else WIDTH
	var heights:=[64,32,96,64] if revision>=3 else [80,48,112,96] if revision>=2 else HEIGHT
	return Vector2(heights[kind(id)],widths[kind(id)]) if rot else Vector2(widths[kind(id)],heights[kind(id)])
func decks()->Array:
	if revision>=3:return modern_decks(level,round_id,course_seed)
	match (level+round_id)%3:
		0:return [[292+level*16,708-level*16,620-level*16]]
		1:return [[292,484,620],[516,708,588-level*16]]
	return [[292,420,620],[436,564,588-level*16],[580,708,620-level*16]]
static func modern_decks(l:int,r:int,seed_value:int)->Array:
	var rng:=RandomNumberGenerator.new();rng.seed=seed_value+l*104729+r*7919
	var profile:=rng.randi_range(0,4);var lift:=rng.randi_range(0,3)*8+l*16
	match profile:
		0:return [[292,708,532-lift]]
		1:return [[292,484,516-lift],[516,708,484-lift]]
		2:return [[292,420,532-lift],[436,564,484-lift],[580,708,516-lift]]
		3:return [[292,452,476-lift],[468,708,540-lift]]
	return [[292,548,524-lift],[564,708,460-lift]]
func empty()->String:return "--".repeat(count())
func present(d:String,i:int)->bool:return d.substr(i*2,2)!="--"
func point(d:String,i:int)->Vector2:return Vector2(252+16*(CELLS.find(d[i*2])%32),108+8*CELLS.find(d[i*2+1]))
func rotated(d:String,i:int)->bool:return CELLS.find(d[i*2])>=32
func put(d:String,i:int,p:Vector2,rot:bool)->String:
	var x:=roundi((p.x-252)/16);var y:=roundi((p.y-108)/8)
	if x<0 or x>31 or y<0 or y>63:return ""
	return d.substr(0,i*2)+CELLS[x+(32 if rot else 0)]+CELLS[y]+d.substr(i*2+2)
func valid(d:String,complete:bool=false)->bool:
	if d.length()!=count()*2:return false
	for i in range(count()):
		if not present(d,i):
			if complete:return false
			continue
		if CELLS.find(d[i*2])<0 or CELLS.find(d[i*2+1])<0:return false
		var p:=point(d,i);var h:=dims(i,rotated(d,i))*.5
		if p.x-h.x<292 or p.x+h.x>708 or p.y-h.y<(44 if revision>=3 else 108) or p.y+h.y>620:return false
		var supported:=false
		for deck in decks():
			if p.x-h.x>=deck[0]-.1 and p.x+h.x<=deck[1]+.1 and absf(p.y+h.y-deck[2])<.1:supported=true
		for j in range(count()):
			if j==i or not present(d,j):continue
			var q:=point(d,j);var hh:=dims(j,rotated(d,j))*.5
			if absf(p.x-q.x)<h.x+hh.x-.1 and absf(p.y-q.y)<h.y+hh.y-.1:return false
			if absf(p.y+h.y-q.y+hh.y)<.1 and p.x>=q.x-hh.x+8 and p.x<=q.x+hh.x-8:supported=true
		if not supported:return false
	return true
func drop(d:String,id:int,x:float,rot:bool)->String:
	if id<0 or id>=count() or not valid(d):return ""
	var h:=dims(id,rot)*.5;var px:=252+16*roundf((x-252)/16);var bottom:=INF
	if px-h.x<292 or px+h.x>708:return ""
	for deck in decks():
		if px-h.x>=deck[0]-.1 and px+h.x<=deck[1]+.1:bottom=minf(bottom,deck[2])
	if bottom==INF:return ""
	for j in range(count()):
		if j==id or not present(d,j):continue
		var p:=point(d,j);var hh:=dims(j,rotated(d,j))*.5
		if absf(px-p.x)<h.x+hh.x-.1:bottom=minf(bottom,p.y-hh.y)
	var next:=put(d,id,Vector2(px,bottom-h.y),rot)
	return next if valid(next) else ""
func place(d:String,id:int,pos:Vector2,rot:bool)->String:
	if not valid(d) or id<0 or id>=count():return ""
	# Rebuild bottom-first after removing a support. No floating pieces survive a move.
	var order:=[]
	for j in range(count()):
		if j!=id and present(d,j):order.append(j)
	order.sort_custom(func(a,b):return point(d,a).y>point(d,b).y)
	var base:=empty()
	for j in order:
		var next:=drop(base,j,point(d,j).x,rotated(d,j))
		if next=="":return ""
		base=next
	var best:="";var distance:=INF
	for col in range(32):
		var next:=drop(base,id,252+col*16,rot)
		if next!="":
			var delta:=point(next,id)-pos;var score:=delta.x*delta.x+delta.y*delta.y*.45
			if score<distance:distance=score;best=next
	return best
func complete(d:String,seed_value:int=0,fallback:bool=true)->String:
	if not valid(d):d=empty()
	var rng:=RandomNumberGenerator.new();rng.seed=seed_value
	for id in range(count()):
		if present(d,id):continue
		var options:=[];var low:="";var y:=-INF
		for col in range(32):
			for rot in [false,true]:
				var next:=drop(d,id,252+col*16,rot)
				if next!="":
					options.append(next)
					if point(next,id).y>y:y=point(next,id).y;low=next
		if options.is_empty():return complete(empty(),0,false) if fallback and d!=empty() else ""
		d=options[rng.randi_range(0,options.size()-1)] if seed_value!=0 else low
	return d
func off()->int:
	var n:=0
	for b in blocks:
		if b.out:n+=1
	return n
func accept(actor:int,data:String)->bool:
	if actor!=turn or winner!=-2 or data.length()>64:return false
	if phase=="choose" and data.begins_with("C"):
		var choice:=data.substr(1).split(",")
		if choice.size()!=(2 if revision>=3 else 1) or choice[0] not in ["0","1"]:return false
		if revision>=3:
			if not choice[1].is_valid_int() or int(choice[1])<1 or int(choice[1])>1000000:return false
			course_seed=int(choice[1])
		character=int(choice[0]);phase="build";turn=builder();return true
	if phase=="build" and data.begins_with("B") and valid(data.substr(1),true):
		blocks=[]
		for i in range(count()):
			var p:=point(data.substr(1),i);blocks.append({"x":p.x,"y":p.y,"angle":PI/2 if rotated(data.substr(1),i) else 0.0,"out":false})
		phase="shoot";turn=leg;shots=0;energy=0;charges=[true,true,true];attack_off=0;bird_ticks=0;duck_hit=false;special_claimed=false;extra_shot=false;return true
	if phase=="shoot" and data=="F":finish_attack();return true
	if phase=="shoot" and data.begins_with("S"):
		var parts:=data.substr(1).split(",")
		if parts.size() not in ([3,4] if revision>=3 else [3]):return false
		for p in parts:
			if not p.is_valid_int():return false
		var launch_clock:=bird_ticks
		if parts.size()==4:
			launch_clock=int(parts[3])
			if launch_clock<bird_ticks or launch_clock>24000:return false
		var aim:=int(parts[0]);var strength:=int(parts[1]);var special:=int(parts[2])
		if aim< -45 or aim>45 or strength<10 or strength>100 or special<0 or special>3 or (special>0 and not charges[special-1]):return false
		if special>0:charges[special-1]=false
		bird_ticks=launch_clock;shots+=1;energy+=strength;powerup=special;burst=false;ticks=0;quiet=0;phase="flight";world=Physics.new()
		for d in decks():
			var deck=world.add(Vector2((d[0]+d[1])*.5,d[2]+20),Vector2((d[1]-d[0])*.5,20),0);deck.platform=true
		var ball=world.add(muzzle(aim),Vector2(48,48),5.4 if special==2 else 3.0,true);ball.passes=true
		var speed:float=(1400+strength*13)*(1.22 if special==1 else 1)
		ball.v=Vector2(sin(deg_to_rad(aim)),-cos(deg_to_rad(aim)))*speed;ball.spin=ball.v.x/48
		for i in range(blocks.size()):
			var b=blocks[i];var half:=dims(i)*.5;var body=world.add(Vector2(b.x,b.y),half,half.x*half.y/1024*density(i));material(body,i);body.angle=b.angle;body.enabled=not b.out;body.bounds()
		if revision>=3:
			# Compact JSON uses fourteen significant digits; keep mass terms canonical.
			for body in world.bodies:
				body.inv=round(body.inv*1e10)/1e10;body.inertia=round(body.inertia*1e12)/1e12
		return true
	if phase=="review" and data=="N":
		if leg==0:leg=1
		else:advance_round()
		if winner==-2:phase="build";turn=builder();blocks=[]
		return true
	return false
func step()->void:
	popped=[];hit_power=0;reward=""
	if phase!="flight":return
	world.step();ticks+=1
	if revision>=3:bird_ticks+=1
	var ball=world.bodies[decks().size()]
	if ticks>=912:ball.enabled=false
	if duck_active() and ball.enabled and ball.p.distance_to(duck_at())<(48+47*float(bird_profile().size) if revision>=3 else 85):
		duck_hit=true;bonus[leg]+=250;reward="DUCK BONUS +250"
	if world.impact>90:
		hit_power=world.impact;hit_point=world.impact_point
		if powerup==3 and not burst:
			burst=true
			for i in range(blocks.size()):
				var body=world.bodies[decks().size()+1+i]
				var diff:Vector2=body.p-hit_point
				if body.enabled and diff.length()<260:body.v+=diff.normalized()*(1100*(1-diff.length()/350))+Vector2(0,-250);body.spin+=(-1 if diff.x<0 else 1)*3
	var settled:=ticks>1008
	for i in range(blocks.size()):
		var body=world.bodies[decks().size()+1+i];var b=blocks[i]
		if not b.out and (body.p.y-body.extent.y>684 or body.p.x< -100 or body.p.x>1100):
			b.out=true;body.enabled=false;popped.append(i);total[leg]+=1
			if i==special_id() and special_pulsing():
				special_claimed=true;bonus[leg]+=150;reward="STAR BURST +150"
				if level==2 and not extra_shot:extra_shot=true;reward="BONUS PANDA!"
				for j in range(blocks.size()):
					var neighbor=world.bodies[decks().size()+1+j];var diff:Vector2=neighbor.p-body.p
					if neighbor.enabled and diff.length()<230:neighbor.v+=diff.normalized()*650+Vector2(0,-220)
		if b.out:body.v.y=minf(2400,body.v.y+1000*Physics.DT);body.p+=body.v*Physics.DT;body.p.y=minf(1600,body.p.y);body.angle+=body.spin*Physics.DT
		elif body.v.length()>3 or absf(body.spin)>.06:settled=false
		b.x=body.p.x;b.y=body.p.y;b.angle=body.angle
	quiet=quiet+1 if settled else 0
	if quiet>=72 or ticks>=1680:
		world=null
		if shots>=shot_limit() or off()==count():finish_attack()
		else:phase="shoot"
func finish_attack()->void:
	results.append([off(),shots,energy]);
	if results.size()>24:results.pop_front()
	world=null;phase="review";turn=0;summary="%d blocks toppled · %d shots"%[off(),shots]
	if leg==1:
		var a:Array=results[-2];var b:Array=results[-1];var won:=-1
		if a[0]!=b[0]:won=0 if a[0]>b[0] else 1
		elif a[1]!=b[1]:won=0 if a[1]<b[1] else 1
		elif a[0]==count() and a[2]!=b[2]:won=0 if a[2]<b[2] else 1
		last_round_winner=won
		if won>=0:round_wins[won]+=1;level_rounds[won]+=1
		summary="Round drawn" if won<0 else ("Panda" if (character if won==0 else 1-character)==0 else "Red Panda")+" wins the round"
func advance_round()->void:
	leg=0
	if round_id<3:round_id+=1;return
	if level_rounds[0]==level_rounds[1]:round_id=4;return
	level_wins[0 if level_rounds[0]>level_rounds[1] else 1]+=1
	if level<2:level+=1;round_id=0;level_rounds=[0,0];return
	winner=-1
	for pair in [[score(0),score(1)],round_wins,level_wins]:
		if pair[0]!=pair[1]:winner=0 if pair[0]>pair[1] else 1;break
	phase="finished";summary="A perfect draw" if winner<0 else "Champion!"
func snapshot()->Dictionary:
	return {"version":revision,"course_seed":course_seed,"bird_ticks":bird_ticks,"bonus":bonus,"duck_hit":duck_hit,"special_claimed":special_claimed,"extra_shot":extra_shot,"level":level,"round_id":round_id,"leg":leg,"phase":phase,"turn":turn,"character":character,"total":total,"round_wins":round_wins,"level_rounds":level_rounds,"level_wins":level_wins,"results":results,"shots":shots,"energy":energy,"charges":charges,"winner":winner,"summary":summary,"last_round_winner":last_round_winner,"blocks":blocks,"ticks":ticks,"quiet":quiet,"burst":burst,"powerup":powerup,"bodies":world.snapshot() if world!=null else []}
func restore(data:Dictionary)->bool:
	data=data.duplicate(true)
	if not(data.get("version")is int or data.get("version")is float) or not is_finite(float(data.version)) or float(data.version)!=int(data.version) or int(data.version)not in [1,2,3]:return false
	var candidate_revision:=int(data.version)
	for field in ["course_seed","bird_ticks"]:
		var value:Variant=data.get(field,17 if field=="course_seed" else 0)
		if not(value is int or value is float) or not is_finite(float(value)) or float(value)!=int(value) or value<0 or value>1000000:return false
		data[field]=int(value)
	if candidate_revision==1:data.bonus=[0,0];data.duck_hit=false;data.special_claimed=false;data.extra_shot=false
	if not data.get("bonus")is Array or data.bonus.size()!=2:return false
	for amount in data.bonus:
		if not(amount is int or amount is float) or not is_finite(float(amount)) or float(amount)!=int(amount) or amount<0 or amount>99999:return false
	for field in ["duck_hit","special_claimed","extra_shot"]:
		if not data.get(field)is bool:return false
	for field in ["level","round_id","leg","turn","character","shots","energy","winner","ticks","quiet","powerup","last_round_winner"]:
		var v:Variant=data.get(field)
		if not(v is int or v is float) or not is_finite(float(v)) or int(v)!=float(v):return false
		data[field]=int(v)
	data.version=int(data.version)
	if data.level not in [0,1,2] or data.round_id<0 or data.round_id>4 or data.leg not in [0,1] or data.turn not in [0,1] or data.character not in [0,1] or data.shots<0 or data.shots>(4 if data.extra_shot else 3) or data.energy<0 or data.energy>400 or data.winner not in [-2,-1,0,1] or data.ticks<0 or data.ticks>1680 or data.quiet<0 or data.quiet>72 or data.powerup not in [0,1,2,3] or data.last_round_winner not in [-2,-1,0,1]:return false
	if data.get("phase")not in ["choose","build","shoot","flight","review","finished"] or (data.phase=="finished")!=(data.winner!=-2):return false
	for field in ["total","round_wins","level_rounds","level_wins"]:
		if not data.get(field)is Array or data[field].size()!=2:return false
		for v in data[field]:
			if not(v is int or v is float) or not is_finite(float(v)) or int(v)!=float(v) or v<0 or v>9999:return false
	if not data.get("charges")is Array or data.charges.size()!=3:return false
	for v in data.charges:
		if not v is bool:return false
	if not data.get("blocks")is Array or data.blocks.size()>18 or not data.get("results")is Array or data.results.size()>24 or not data.get("burst")is bool or not data.get("summary")is String or data.summary.length()>150:return false
	for b in data.blocks:
		if not b is Dictionary or not b.get("out")is bool:return false
		for field in ["x","y","angle"]:
			if not(b.get(field)is int or b.get(field)is float) or not is_finite(float(b[field])) or absf(float(b[field]))>10000:return false
	for row in data.results:
		if not row is Array or row.size()!=3:return false
		for v in row:
			if not(v is int or v is float) or not is_finite(float(v)) or int(v)!=float(v) or v<0 or v>400:return false
	for field in ["total","round_wins","level_rounds","level_wins"]:
		data[field]=[int(data[field][0]),int(data[field][1])]
	for i in range(data.results.size()):
		data.results[i]=[int(data.results[i][0]),int(data.results[i][1]),int(data.results[i][2])]
	var candidate=Physics.new()
	if not data.get("bodies")is Array or not candidate.restore(data.bodies):return false
	var expected_count:int=12+int(data.level)*3 if candidate_revision>=3 else 8+int(data.level)*2 if candidate_revision>=2 else 6+int(data.level)*2+mini(3,int(data.round_id))/2
	if data.phase in ["shoot","flight","review","finished"] and data.blocks.size()!=expected_count:return false
	if data.phase=="flight" and (data.bodies.size()!=decks_for(int(data.level),int(data.round_id),candidate_revision,int(data.course_seed))) :return false
	if data.phase!="flight" and not data.bodies.is_empty():return false
	for field in ["level","round_id","leg","turn","character","shots","energy","winner","ticks","quiet","powerup","last_round_winner"]:set(field,int(data[field]))
	for field in ["phase","summary","burst"]:set(field,data[field])
	for field in ["total","round_wins","level_rounds","level_wins","charges","blocks","results"]:set(field,data[field].duplicate(true))
	revision=candidate_revision;course_seed=data.course_seed;bird_ticks=data.bird_ticks;bonus=[int(data.bonus[0]),int(data.bonus[1])];duck_hit=data.duck_hit;special_claimed=data.special_claimed;extra_shot=data.extra_shot
	world=candidate if phase=="flight" else null
	if world!=null and revision>=3:
		for i in range(blocks.size()):material(world.bodies[decks().size()+1+i],i)
	return true
static func decks_for(l:int,r:int,v:int=2,seed_value:int=17)->int:
	if v>=3:return modern_decks(l,r,seed_value).size()+1+12+l*3
	return (1 if (l+r)%3==0 else 2 if (l+r)%3==1 else 3)+1+(8+l*2 if v>=2 else 6+l*2+mini(3,r)/2)
