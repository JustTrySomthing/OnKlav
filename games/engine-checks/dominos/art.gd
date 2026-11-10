extends RefCounted
const CYAN:=Color("62f2ed")
const PINK:=Color("ed75ff")
const GOLD:=Color("ffdc89")
static func panel(c:CanvasItem,r:Rect2,fill:Color,edge:Color,radius:int=16):
	var s:=StyleBoxFlat.new();s.bg_color=fill;s.border_color=edge;s.set_border_width_all(1);s.set_corner_radius_all(radius);c.draw_style_box(s,r)
static func text(c:CanvasItem,value:String,p:Vector2,points:int,ink:Color,center:bool=false):
	var font=ThemeDB.fallback_font;var x:float=font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,points).x/2 if center else 0
	c.draw_string(font,p+Vector2(1,2)-Vector2(x,0),value,HORIZONTAL_ALIGNMENT_LEFT,-1,points,Color(0,0,0,ink.a*.65))
	c.draw_string(font,p-Vector2(x,0),value,HORIZONTAL_ALIGNMENT_LEFT,-1,points,ink)
static func pips(c:CanvasItem,value:int,center:Vector2,unit:float,ink:Color):
	var dots:Array=[]
	if value%2==1:dots.append(Vector2.ZERO)
	if value>=2:dots.append(Vector2(-1,-1));dots.append(Vector2(1,1))
	if value>=4:dots.append(Vector2(1,-1));dots.append(Vector2(-1,1))
	if value==6:dots.append(Vector2(-1,0));dots.append(Vector2(1,0))
	for p in dots:
		c.draw_circle(center+p*unit,unit*.55,Color(ink,.09));c.draw_circle(center+p*unit,unit*.39,Color(ink,.18));c.draw_circle(center+p*unit,unit*.28,ink);c.draw_circle(center+p*unit-Vector2.ONE*unit*.07,unit*.07,Color(1,1,1,ink.a*.6))
static func tile(c:CanvasItem,r:Rect2,a:int,b:int,ink:Color,vertical:bool=false,back:bool=false,lit:bool=false):
	var radius:=clampi(roundi(minf(r.size.x,r.size.y)*.16),2,7)
	if lit:
		for i in range(3,0,-1):panel(c,r.grow(i*2),Color(ink,.025),Color(ink,.07),9+i*2)
	panel(c,Rect2(r.position+Vector2(0,3),r.size),Color("020610"),Color("020610"),radius)
	panel(c,r,Color("173044") if back else Color("101c35"),Color(ink,.8) if lit or back else Color(ink,.45),radius)
	if back:
		var q:=r.grow(-5);panel(c,q,Color("132033"),Color(ink,.5),4)
		c.draw_line(q.position+q.size*.2,q.end-q.size*.2,Color(ink,.4),1,true)
		c.draw_line(q.position+Vector2(q.size.x*.8,q.size.y*.2),q.position+Vector2(q.size.x*.2,q.size.y*.8),Color(ink,.4),1,true);return
	var mid:=r.get_center();var u:float=minf(r.size.x/(3 if vertical else 6),r.size.y/(6 if vertical else 3))*.64
	var offset:=Vector2(0,r.size.y*.25) if vertical else Vector2(r.size.x*.25,0)
	c.draw_line(Vector2(r.position.x+5,mid.y) if vertical else Vector2(mid.x,r.position.y+4),Vector2(r.end.x-5,mid.y) if vertical else Vector2(mid.x,r.end.y-4),Color(ink,.45),1,true)
	pips(c,a,mid-offset,u,ink if lit else Color("bfdaed"));pips(c,b,mid+offset,u,ink if lit else Color("bfdaed"))
	c.draw_line(r.position+Vector2(6,2),r.position+Vector2(r.size.x-6,2),Color(ink,.65),1,true)

## Bounded perimeter runners. Pure decoration, outside tile geometry and hit targets.
static func rail(c:CanvasItem,r:Rect2,clock:float,reduced:bool):
	var x:=r.position.x;var y:=r.position.y;var w:=r.size.x;var h:=r.size.y;var cut:=14.0
	var path:=PackedVector2Array([Vector2(x+cut,y),Vector2(x+w-cut,y),Vector2(x+w,y+cut),Vector2(x+w,y+h-cut),Vector2(x+w-cut,y+h),Vector2(x+cut,y+h),Vector2(x,y+h-cut),Vector2(x,y+cut),Vector2(x+cut,y)])
	var total:=0.0
	for i in range(1,path.size()):total+=path[i-1].distance_to(path[i])
	c.draw_polyline(path,Color(CYAN,.13),1,true)
	if reduced:return
	for runner in range(2):
		var ink:Color=CYAN if runner==0 else PINK
		var at:float=fposmod(clock*55+runner*total*.5,total)
		for i in range(14):
			var from:=rail_point(path,fposmod(at-i*4,total));var to:=rail_point(path,fposmod(at-(i+1)*4,total))
			if from.distance_to(to)<12:
				c.draw_line(from,to,Color(ink,(1-float(i)/14)*.09),7,true)
				c.draw_line(from,to,Color(ink,(1-float(i)/14)*.75),1.5,true)
static func rail_point(path:PackedVector2Array,distance:float)->Vector2:
	for i in range(1,path.size()):
		var length:=path[i-1].distance_to(path[i])
		if distance<=length:return path[i-1].lerp(path[i],distance/maxf(length,.01))
		distance-=length
	return path[0]
