extends RefCounted
## Bounded fixed-step rigid bodies, adapted from the original Panda solver.
const DT:=1.0/240
class Body:
	var p:=Vector2.ZERO
	var v:=Vector2.ZERO
	var h:=Vector2.ONE
	var angle:=0.0
	var spin:=0.0
	var inv:=0.0
	var inertia:=0.0
	var circle:=false
	var enabled:=true
	var platform:=false
	var passes:=false
	var bounce:=.08
	var friction:=.45
	var axis:=Vector2.RIGHT
	var extent:=Vector2.ONE
	func _init(pos:Vector2,half:Vector2,mass:float,round_body:bool=false):
		p=pos;h=half;circle=round_body;inv=0 if mass==0 else 1.0/mass
		inertia=0 if mass==0 else 1.0/(mass*h.x*h.x*.5 if circle else mass*h.length_squared()/3)
		bounds()
	func bounds():
		axis=Vector2(cos(angle),sin(angle));extent=h if circle else Vector2(absf(axis.x)*h.x+absf(axis.y)*h.y,absf(axis.y)*h.x+absf(axis.x)*h.y)
	func data()->Array:return [p.x,p.y,v.x,v.y,h.x,h.y,angle,spin,inv,inertia,circle,enabled,platform,passes]
var bodies:Array=[]
var impact:=0.0
var impact_point:=Vector2.ZERO
func add(pos:Vector2,half:Vector2,mass:float,circle:bool=false):
	var b:=Body.new(pos,half,mass,circle);bodies.append(b);return b
static func q(x:float)->float:return round(x*1000000)/1000000
func step()->void:
	impact=0
	for b in bodies:
		if not b.enabled:continue
		if b.inv>0:
			b.v.x=clampf(b.v.x*.99995,-3000,3000);b.v.y=clampf(b.v.y+1000*DT,-3000,3000);b.spin=clampf(b.spin*.9999,-40,40);b.p+=b.v*DT;b.angle+=b.spin*DT
		b.bounds()
	for pass_id in range(8):
		for i in range(bodies.size()):
			var a=bodies[i]
			if not a.enabled:continue
			for j in range(i+1,bodies.size()):
				var b=bodies[j]
				if not b.enabled or a.inv+b.inv==0:continue
				var d:Vector2=b.p-a.p
				if absf(d.x)>a.extent.x+b.extent.x+.05 or absf(d.y)>a.extent.y+b.extent.y+.05:continue
				if (a.platform and b.passes and (b.v.y<0 or b.p.y>a.p.y-a.h.y+1)) or (b.platform and a.passes and (a.v.y<0 or a.p.y>b.p.y-b.h.y+1)):continue
				contact(a,b)
	for b in bodies:
		if not b.enabled:continue
		b.p=Vector2(q(b.p.x),q(b.p.y));b.v=Vector2(q(b.v.x),q(b.v.y));b.angle=q(wrapf(b.angle,-PI,PI));b.spin=q(b.spin)
static func extent(b,n:Vector2)->float:return absf(n.dot(b.axis))*b.h.x+absf(n.dot(Vector2(-b.axis.y,b.axis.x)))*b.h.y
func contact(a,b):
	if a.circle or b.circle:
		circle_contact(a,b);return
	var d:Vector2=b.p-a.p;var depth:=INF;var normal:=Vector2.ZERO;var chosen:=0
	for i in range(4):
		var ref=a if i<2 else b;var n:Vector2=ref.axis if i%2==0 else Vector2(-ref.axis.y,ref.axis.x);var dot:=d.dot(n);var overlap:=extent(a,n)+extent(b,n)-absf(dot)
		if overlap<=0:return
		if overlap<depth:depth=overlap;normal=n if dot>=0 else -n;chosen=i
	var ref=a if chosen<2 else b;var inc=b if chosen<2 else a
	var rn:Vector2=normal if chosen<2 else -normal;var tangent:=Vector2(-rn.y,rn.x)
	var front:float=ref.h.x if chosen%2==0 else ref.h.y;var side:float=ref.h.y if chosen%2==0 else ref.h.x
	var face:Vector2=ref.p+rn*front;var dotx:=rn.dot(inc.axis);var doty:=rn.dot(Vector2(-inc.axis.y,inc.axis.x));var center:Vector2;var u:Vector2;var half:float
	if absf(dotx)>=absf(doty):center=inc.p+inc.axis*inc.h.x*(-1 if dotx>0 else 1);u=Vector2(-inc.axis.y,inc.axis.x);half=inc.h.y
	else:center=inc.p+Vector2(-inc.axis.y,inc.axis.x)*inc.h.y*(-1 if doty>0 else 1);u=inc.axis;half=inc.h.x
	var p0:=center-u*half;var p1:=center+u*half;var s0:float=(p0-ref.p).dot(tangent);var s1:float=(p1-ref.p).dot(tangent);var delta:=s1-s0;var low:=0.0;var high:=1.0
	if absf(delta)<.000000001:
		if s0< -side or s0>side:return
	else:
		var v0:=(-side-s0)/delta;var v1:float=(side-s0)/delta;low=maxf(0,minf(v0,v1));high=minf(1,maxf(v0,v1))
		if low>high:return
	var x0:=p0.lerp(p1,low);var x1:=p0.lerp(p1,high);var d0:float=(x0-face).dot(rn);var d1:float=(x1-face).dot(rn)
	correct(a,b,normal,depth)
	var count:=int(d0<=.05)+int(d1<=.05)
	if count==0:return
	if d0<=.05:impulse(a,b,normal,x0-rn*d0*.5,count)
	if d1<=.05:impulse(a,b,normal,x1-rn*d1*.5,count)
func circle_contact(a,b):
	var n:Vector2;var depth:float;var point:Vector2
	if a.circle and b.circle:
		var d:Vector2=b.p-a.p;var length:=d.length();depth=a.h.x+b.h.x-length
		if depth<=0:return
		n=Vector2.RIGHT if length<.0000001 else d/length;point=a.p+n*(a.h.x-depth*.5)
	else:
		var c=a if a.circle else b;var box=b if a.circle else a;var d:Vector2=c.p-box.p;var local:=Vector2(d.dot(box.axis),d.dot(Vector2(-box.axis.y,box.axis.x)));var nearest:=local.clamp(-box.h,box.h);var offset:=nearest-local;var length:=offset.length()
		if length>=c.h.x:return
		if length<.0000001:
			var edge:Vector2=box.h-local.abs()
			if edge.x<edge.y:offset=Vector2(1 if local.x<0 else -1,0);depth=c.h.x+edge.x;nearest=Vector2(-box.h.x if local.x<0 else box.h.x,local.y)
			else:offset=Vector2(0,1 if local.y<0 else -1);depth=c.h.x+edge.y;nearest=Vector2(local.x,-box.h.y if local.y<0 else box.h.y)
		else:offset/=length;depth=c.h.x-length
		n=box.axis*offset.x+Vector2(-box.axis.y,box.axis.x)*offset.y;point=box.p+box.axis*nearest.x+Vector2(-box.axis.y,box.axis.x)*nearest.y
		if not a.circle:n=-n
	correct(a,b,n,depth);impulse(a,b,n,point,1)
func correct(a,b,n:Vector2,depth:float):
	var amount:float=maxf(0,depth-.03)*.65/(a.inv+b.inv);a.p-=n*amount*a.inv;b.p+=n*amount*b.inv
func impulse(a,b,n:Vector2,p:Vector2,count:int)->float:
	var ar:Vector2=p-a.p;var br:Vector2=p-b.p;var relative:Vector2=b.v+Vector2(-br.y,br.x)*b.spin-a.v-Vector2(-ar.y,ar.x)*a.spin;var speed:=relative.dot(n)
	if speed>=0:return 0.0
	var ca:=ar.cross(n);var cb:=br.cross(n);var den:float=a.inv+b.inv+ca*ca*a.inertia+cb*cb*b.inertia
	var restitution:float=minf(a.bounce,b.bounce) if speed< -80 else 0
	var force:=-(1+restitution)*speed/den/count
	apply(a,b,ar,br,n*force)
	var t:=Vector2(-n.y,n.x);relative=b.v+Vector2(-br.y,br.x)*b.spin-a.v-Vector2(-ar.y,ar.x)*a.spin
	var ta:=ar.cross(t);var tb:=br.cross(t);var fd:float=a.inv+b.inv+ta*ta*a.inertia+tb*tb*b.inertia
	var friction_limit:=force*sqrt(a.friction*b.friction)
	var friction:=clampf(-relative.dot(t)/fd/count,-friction_limit,friction_limit)
	apply(a,b,ar,br,t*friction)
	if (a.circle or b.circle) and force>impact:impact=force;impact_point=p
	return force
func apply(a,b,ar:Vector2,br:Vector2,force:Vector2):
	a.v-=force*a.inv;b.v+=force*b.inv;a.spin-=ar.cross(force)*a.inertia;b.spin+=br.cross(force)*b.inertia
func snapshot()->Array:
	var result:=[]
	for b in bodies:result.append(b.data())
	return result
func restore(data:Array)->bool:
	if data.size()>24:return false
	var next:=[]
	for row in data:
		if not row is Array or row.size()!=14:return false
		for i in range(10):
			if not(row[i]is int or row[i]is float) or not is_finite(float(row[i])) or absf(float(row[i]))>100000:return false
		for i in range(10,14):
			if not row[i]is bool:return false
		if row[4]<=0 or row[5]<=0 or row[4]>600 or row[5]>600 or row[8]<0 or row[9]<0:return false
		var b:=Body.new(Vector2(row[0],row[1]),Vector2(row[4],row[5]),0,row[10]);b.v=Vector2(row[2],row[3]);b.angle=row[6];b.spin=row[7];b.inv=row[8];b.inertia=row[9];b.enabled=row[11];b.platform=row[12];b.passes=row[13];b.bounds();next.append(b)
	bodies=next;return true
