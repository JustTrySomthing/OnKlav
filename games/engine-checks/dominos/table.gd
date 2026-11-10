extends Control
signal end_chosen(end:int)
const Art=preload("res://dominos/art.gd")
const Layout=preload("res://dominos/layout.gd")
var chain:Array=[]
var _plan_key:=Vector3(-1,-1,-1)
var _plan:Dictionary={}
var stock_count:=14
var left_ok:=false
var right_ok:=false
var reduced:=false
var clock:=0.0
var particles:Array=[]
var rings:Array=[]
var hidden_id:=-1
var old_rects:Dictionary={}
var old_angles:Dictionary={}
var captured_angles:Dictionary={}
var reflow_age:=1.0
var final_ink:=Art.GOLD
var final_text:=""
var final_detail:=""
var hover_end:=-1
func _ready():mouse_filter=Control.MOUSE_FILTER_STOP;gui_input.connect(input_event)
func input_event(event:InputEvent):
	if event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT and event.pressed:
		var end:=end_at(event.position)
		if end>=0:end_chosen.emit(end)
func space()->Rect2:return Rect2(14,24,size.x-28,maxf(36,size.y-48))
func plan()->Dictionary:
	var key:=Vector3(size.x,size.y,chain.size())
	if key!=_plan_key:_plan=Layout.table_plan(space(),chain.size());_plan_key=key
	return _plan
func tile_rect(index:int,total:int=-1)->Rect2:return Layout.table_rect_with_plan(space(),index,plan()) if total<0 or total==chain.size() else Layout.table_rect(space(),index,total)
func target_angle(index:int)->float:
	var arrangement:=plan();return PI if (index/int(arrangement.columns))%2==1 else 0.0
func display_angle(index:int)->float:
	var target:=target_angle(index);var id:int=chain[index][0]
	return target if reduced or not old_angles.has(id) else lerp_angle(old_angles[id],target,1-pow(1-minf(1,reflow_age/.4),3))
func end_box(end:int)->Rect2:
	var r:=Rect2(size*.5-Vector2(52,28),Vector2(104,56)) if chain.is_empty() else tile_rect(0 if end==0 else chain.size()-1)
	var extent:=Vector2(maxf(64,r.size.x+10),maxf(44,r.size.y+10))
	return Rect2(r.get_center()-extent*.5,extent)
func end_at(point:Vector2)->int:
	var left:bool=left_ok and end_box(0).has_point(point);var right:bool=right_ok and end_box(1).has_point(point)
	if left and right:return 0 if point.distance_to(end_box(0).get_center())<=point.distance_to(end_box(1).get_center()) else 1
	return 0 if left else 1 if right else -1
func current_rects()->Dictionary:
	var result:Dictionary={};captured_angles={}
	for i in range(chain.size()):result[int(chain[i][0])]=display_rect(i);captured_angles[int(chain[i][0])]=display_angle(i)
	return result
func reflow(previous:Dictionary):old_rects=previous;old_angles=captured_angles.duplicate();reflow_age=0
func display_rect(index:int)->Rect2:
	var target:=tile_rect(index);var id:int=chain[index][0]
	return target if reduced or not old_rects.has(id) else Rect2(old_rects[id].position.lerp(target.position,1-pow(1-minf(1,reflow_age/.4),3)),old_rects[id].size.lerp(target.size,minf(1,reflow_age/.4)))
func burst(point:Vector2,ink:Color,strong:bool=false):
	if reduced:return
	rings.append({"point":point,"age":0.0,"ink":ink})
	while rings.size()>5:rings.pop_front()
	for i in range(32 if strong else 14):
		var angle:float=TAU*i/(32 if strong else 14)+clock
		particles.append({"p":point,"v":Vector2.from_angle(angle)*(45+i%5*22)*(2 if strong else 1),"age":0.0,"life":.6+i%4*.15,"color":ink})
	while particles.size()>120:particles.pop_front()
func _process(dt:float):
	reflow_age=minf(1,reflow_age+dt)
	if not reduced:clock+=dt
	for p in particles:p.age+=dt;p.p+=p.v*dt;p.v*=.97
	particles=particles.filter(func(p):return p.age<p.life)
	for ring in rings:ring.age+=dt
	rings=rings.filter(func(ring):return ring.age<.7)
	queue_redraw()
func _draw():
	Art.panel(self,Rect2(Vector2.ZERO,size),Color("09182a"),Color("294b67"),22)
	Art.rail(self,Rect2(Vector2(5,5),size-Vector2(10,10)),clock,reduced)
	var area:=space()
	for i in range(10):
		var p:=Vector2(14+fmod(i*37.3+clock*2,maxf(1,size.x-28)),20+fmod(i*51.1,maxf(1,area.size.y)))
		draw_circle(p,1,Color(Art.CYAN,.1))
	if final_text!="":
		var center:=Vector2(size.x*.5,size.y*.48)
		for i in range(3):draw_arc(center-Vector2(0,24),30+i*7,clock*.2+i,clock*.2+i+PI*1.5,48,Color(final_ink,.35-i*.08),2,true)
		var crown:=PackedVector2Array([Vector2(-25,-41),Vector2(-13,-20),Vector2(0,-48),Vector2(13,-20),Vector2(25,-41),Vector2(21,-5),Vector2(-21,-5)])
		for i in range(crown.size()):crown[i]+=center
		draw_colored_polygon(crown,final_ink);draw_line(center+Vector2(-20,1),center+Vector2(20,1),final_ink,4,true)
		Art.text(self,final_text,center+Vector2(0,43),28,final_ink,true)
		Art.text(self,final_detail,center+Vector2(0,68),11,Color("b8cce3"),true)
	elif chain.is_empty():
		var center:=area.get_center()
		draw_arc(center,40,clock*.3,clock*.3+PI*1.5,48,Color(Art.CYAN,.25),2,true)
		Art.text(self,"MAKE YOUR CONNECTION",center+Vector2(0,5),14,Color("a8cadb"),true)
	else:
		for i in range(1,chain.size()):
			var a:=display_rect(i-1);var b:=display_rect(i)
			var a_side:float=-1 if absf(wrapf(display_angle(i-1),-PI,PI))>PI/2 else 1
			var b_side:float=1 if absf(wrapf(display_angle(i),-PI,PI))>PI/2 else -1
			var from:=a.get_center()+Vector2(a.size.x*.5*a_side,0);var to:=b.get_center()+Vector2(b.size.x*.5*b_side,0)
			if absf(from.y-to.y)<2:draw_line(from,to,Color(Art.CYAN,.5),3,true)
			else:
				var x:float=from.x+a_side*5;draw_polyline(PackedVector2Array([from,Vector2(x,from.y),Vector2(x,to.y),to]),Color(Art.CYAN,.5),3,true)
		for i in range(chain.size()):
			var tile:Array=chain[i]
			if int(tile[0])==hidden_id:continue
			var r:=display_rect(i)
			draw_set_transform(r.get_center(),display_angle(i),Vector2.ONE)
			Art.tile(self,Rect2(-r.size*.5,r.size),tile[1],tile[2],Art.CYAN if tile[3]==0 else Art.PINK,false,false,i==0 or i==chain.size()-1)
			draw_set_transform(Vector2.ZERO)
	if final_text=="":
		for end in range(2):
			var active:bool=left_ok if end==0 else right_ok
			if not active:continue
			var r:=end_box(end);var ink:Color=Art.CYAN if end==0 else Art.PINK
			var style:=StyleBoxFlat.new();style.bg_color=Color(ink,.08 if hover_end!=end else .25);style.border_color=Color(ink,.7+.3*sin(clock*4) if not reduced else 1);style.set_border_width_all(2);style.set_corner_radius_all(9);draw_style_box(style,r)
	for p in particles:draw_line(p.p,p.p-p.v*.035,Color(p.color,1-p.age/p.life),2,true)

	if not reduced:
		for ring in rings:
			var t:float=ring.age/.7
			for band in range(2):draw_arc(ring.point,8+t*36+band*5,0,TAU,40,Color(ring.ink,(1-t)*(.28 if band==0 else .09)),2 if band==0 else 5,true)
