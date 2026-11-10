extends "res://panda/physics.gd"
## One sweep broad phase per 240 Hz tick; bounded iterative contact resolution.
## Original solver remains available for imported legacy in-flight checkpoints.
const GRAVITY:=1000.0
const HORIZONTAL_DRAG:=.99995
var tick_span:=1
var tower_awake:=true
var awake_mask:Array=[]
var groups:Array=[]
func awake(i:int)->bool:return (tower_awake or bodies[i].circle) if awake_mask.is_empty() else bool(awake_mask[i])
func build_groups():
	groups=[];groups.resize(bodies.size())
	for i in range(bodies.size()):groups[i]=[]
	if awake_mask.is_empty():return
	var seen:=[]
	for i in range(bodies.size()):
		if i in seen or awake(i) or bodies[i].platform or not bodies[i].enabled:continue
		var group:=[i];seen.append(i);var at:=0
		while at<group.size():
			var a:Body=bodies[group[at]];at+=1
			for j in range(bodies.size()):
				if j in seen or awake(j) or bodies[j].platform or not bodies[j].enabled:continue
				var b:Body=bodies[j];var gap:Vector2=(b.p-a.p).abs()-a.extent-b.extent
				if gap.x<=8 and gap.y<=8:group.append(j);seen.append(j)
		for id in group:groups[id]=group
func wake(i:int):
	if awake_mask.is_empty():tower_awake=true;return
	awake_mask[i]=true
	if i<groups.size():
		for id in groups[i]:awake_mask[id]=true
func restore(data:Array)->bool:
	if data.size()>45:return false
	var next:=[]
	for start in range(0,data.size(),24):
		var part=preload("res://panda/physics.gd").new()
		if not part.restore(data.slice(start,mini(start+24,data.size()))):return false
		next.append_array(part.bodies)
	bodies=next;return true
func step()->void:
	var dt:=DT*tick_span
	impact=0
	var active:Array=[]
	for i in range(bodies.size()):
		var b:Body=bodies[i]
		if not b.enabled:continue
		if b.inv>0 and awake(i):
			b.v.x=clampf(b.v.x*HORIZONTAL_DRAG,-3000,3000);b.v.y=clampf(b.v.y+GRAVITY*dt,-3000,3000);b.spin=clampf(b.spin*.9999,-40,40);b.p+=b.v*dt;b.angle+=b.spin*dt
		b.bounds();active.append(i)
	active.sort_custom(func(i,j):
		var x:float=bodies[i].p.x-bodies[i].extent.x;var y:float=bodies[j].p.x-bodies[j].extent.x
		return i<j if x==y else x<y)
	var pairs:Array=[]
	for ii in range(active.size()):
		var left:Body=bodies[active[ii]];var right_edge:float=left.p.x+left.extent.x+2
		for jj in range(ii+1,active.size()):
			var right:Body=bodies[active[jj]]
			if right.p.x-right.extent.x>right_edge:break
			if (awake(active[ii]) or awake(active[jj])) and left.inv+right.inv>0 and absf(right.p.y-left.p.y)<=left.extent.y+right.extent.y+2:pairs.append(Vector2i(mini(active[ii],active[jj]),maxi(active[ii],active[jj])))
	for pass_id in range(6):
		for pair in pairs:
			var a:Body=bodies[pair.x];var b:Body=bodies[pair.y];var d:Vector2=b.p-a.p
			if absf(d.x)>a.extent.x+b.extent.x+.05 or absf(d.y)>a.extent.y+b.extent.y+.05:continue
			if (a.platform and b.passes and (b.v.y<0 or b.p.y>a.p.y-a.h.y+1)) or (b.platform and a.passes and (a.v.y<0 or a.p.y>b.p.y-b.h.y+1)):continue
			if not awake_mask.is_empty() and awake(pair.x)!=awake(pair.y) and (a.v-b.v).length()>18:
				wake(pair.x);wake(pair.y)
			contact(a,b)
			if not awake_mask.is_empty():
				if not awake(pair.x) and a.v.length()>2:wake(pair.x)
				if not awake(pair.y) and b.v.length()>2:wake(pair.y)
			if (a.circle or b.circle) and impact>130:
				wake(pair.x);wake(pair.y)
	for b in bodies:
		if not b.enabled:continue
		b.p=Vector2(q(b.p.x),q(b.p.y));b.v=Vector2(q(b.v.x),q(b.v.y));b.angle=q(wrapf(b.angle,-PI,PI));b.spin=q(b.spin)

func impulse(a,b,n:Vector2,p:Vector2,count:int)->float:
	var force:=super.impulse(a,b,n,p,count)
	if force>impact:impact=force;impact_point=p
	return force
