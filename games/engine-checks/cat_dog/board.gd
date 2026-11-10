extends Control
const Art=preload("res://cat_dog/art.gd")
const Chrome=preload("res://game_chrome.gd")
signal chosen(column:int)
signal preview(column:int)
var values:Array=[]
var event:Dictionary={}
var win_line:Array=[]
var host_dog:=false
var actor:=0
var locked:=false:
	set(value):
		locked=value
		if value:cancel_pointer()
var reduced:=false
var hover:=-1
var finger:=-1
var pressed:=false
var touched_at:=0
var recent_cell:=-1
var match_win:=false
var loss_side:=-1
var frame:=Chrome.box("222c3d","61728b",18)
var inset:=Chrome.box("141c2b","a4acc0",12)
var foot_style:=Chrome.box("141b29","4f6176",4)
var celebration:=0.0
var denied:=-1
var deny_time:=0.0
func _ready() -> void:
	mouse_default_cursor_shape=Control.CURSOR_POINTING_HAND
	resized.connect(cancel_pointer)
	frame.shadow_color=Color(0,0,0,.55)
	frame.shadow_size=9
	frame.shadow_offset=Vector2(0,5)
	frame.border_width_top=2
	frame.border_width_bottom=4
func grid() -> Rect2:
	var width:=maxf(35,minf(size.x-14,(size.y-40)*7/6))
	var extent:=Vector2(width,width*6/7)
	return Rect2((size-extent)*.5+Vector2(0,10),extent)
func is_cat(who:int) -> bool:return (who==0)!=host_dog
func column_at(point:Vector2) -> int:
	var r:=grid()
	if not r.has_point(point):return -1
	return clampi(int((point.x-r.position.x)/r.size.x*7),0,6)
func cancel_pointer()->void:
	var changed:=hover!=-1
	pressed=false;finger=-1;hover=-1
	if changed:preview.emit(-1)
	queue_redraw()
func _notification(what:int)->void:
	if what==NOTIFICATION_APPLICATION_FOCUS_OUT:cancel_pointer()
func _input(input:InputEvent)->void:
	if not is_visible_in_tree() or locked:return
	if not(input is InputEventScreenTouch or input is InputEventScreenDrag or input is InputEventMouseButton or input is InputEventMouseMotion):return
	var local:InputEvent=input.duplicate()
	local.position=get_global_transform_with_canvas().affine_inverse()*input.position
	if finger==-1 and not Rect2(Vector2.ZERO,size).has_point(local.position):return
	_handle_pointer(local)
func _gui_input(input:InputEvent)->void:_handle_pointer(input)
func _handle_pointer(input:InputEvent)->void:
	if locked:cancel_pointer();return
	var point:=Vector2.ZERO;var down:=false;var up:=false
	if input is InputEventScreenTouch:
		touched_at=Time.get_ticks_msec()
		if input.canceled:
			if input.index==finger:cancel_pointer()
			return
		if input.pressed and finger>=0:return
		if not input.pressed and input.index!=finger:return
		point=input.position;down=input.pressed;up=not down
		if down:finger=input.index # Real touch supersedes an emulated mouse press.
	elif input is InputEventScreenDrag:
		if input.index!=finger:return
		touched_at=Time.get_ticks_msec();point=input.position
	elif input is InputEventMouseButton:
		if finger>=0 or Time.get_ticks_msec()-touched_at<700 or input.button_index!=MOUSE_BUTTON_LEFT:return
		point=input.position;down=input.pressed;up=not down
		if down:finger=-2
		elif finger!=-2:return
	elif input is InputEventMouseMotion:
		if finger!=-2 or not pressed:return
		if Time.get_ticks_msec()-touched_at<700:return
		point=input.position
	else:return
	if down:
		if column_at(point)<0:cancel_pointer();return
		pressed=true
	if not pressed:return
	hover=column_at(point);preview.emit(hover)
	if up:
		var chosen_column:=hover
		cancel_pointer()
		if chosen_column>=0:chosen.emit(chosen_column)
	get_viewport().set_input_as_handled();queue_redraw()
func _process(delta:float) -> void:
	if not win_line.is_empty():celebration+=delta
	var was_denied:=deny_time>0
	deny_time=maxf(0,deny_time-delta)
	var animating_win:=not reduced and not win_line.is_empty() and celebration-delta<2.5
	if not event.is_empty() or animating_win or was_denied:queue_redraw()
func center(cell:int) -> Vector2:
	var r:=grid();var unit:=r.size.x/7
	return r.position+(Vector2(cell%7,cell/7)+Vector2.ONE*.5)*unit
func flight_point(age:float) -> Vector2:
	var p:=center(int(event.cell));var unit:=grid().size.x/7
	if reduced:return p
	var duration:float=event.flight
	if age<duration:
		var t:=clampf(age/duration,0,1)
		return Vector2(p.x,lerpf(grid().position.y-unit*.65,p.y,t*t))
	var settle:=age-duration
	if settle<.13:
		var t:=settle/.13
		return p-Vector2(0,unit*.16*4*t*(1-t))
	var t:=clampf((settle-.13)/.08,0,1)
	return p-Vector2(0,unit*.04*4*t*(1-t))
func draw_frame(r:Rect2) -> void:
	draw_style_box(frame,r.grow(7))
	draw_style_box(inset,r.grow(3))
	for i in range(18):
		var shade:=float(i)/17
		var y:=r.position.y+r.size.y*i/18
		var h:=r.size.y/18+1
		draw_rect(Rect2(r.position.x,y,r.size.x*.5,h),Color("654658").lerp(Color("282735"),shade))
		draw_rect(Rect2(r.get_center().x,y,r.size.x*.5,h),Color("426783").lerp(Color("1d2c3e"),shade))
	var top:=r.position.y-4
	var middle:=r.get_center().x
	draw_line(Vector2(r.position.x+5,top),Vector2(middle,top),Color("d69cab"),2,true)
	draw_line(Vector2(middle,top),Vector2(r.end.x-5,top),Color("a3cce1"),2,true)
	draw_line(r.position+Vector2(-4,10),Vector2(r.position.x-4,r.end.y-8),Color("a6879955"),1.5,true)
	draw_line(Vector2(r.end.x+4,r.position.y+10),r.end+Vector2(4,-8),Color("7caac77a"),1.5,true)
	draw_line(Vector2(r.position.x+4,r.end.y+3),r.end+Vector2(-4,3),Color("080e19"),2,true)
	var foot:=Rect2(r.position+Vector2(-8,r.size.y+7),Vector2(r.size.x+16,9))
	draw_style_box(foot_style,foot)
	draw_line(foot.position+Vector2(8,2),Vector2(foot.end.x-8,foot.position.y+2),Color("a3b8ce77"),1,true)

func draw_finale_rail(r:Rect2) -> void:
	var rail:=r.grow(4)
	var corners:=[rail.position+Vector2(4,0),Vector2(rail.end.x-4,rail.position.y),Vector2(rail.end.x,rail.position.y+4),rail.end-Vector2(0,4),rail.end-Vector2(4,0),Vector2(rail.position.x+4,rail.end.y),Vector2(rail.position.x,rail.end.y-4),rail.position+Vector2(0,4),rail.position+Vector2(4,0)]
	var remaining:=clampf(celebration/.72,0,1)*(2*(rail.size.x+rail.size.y)-32+16*sqrt(2.0))
	var alpha:=.9 if reduced else clampf((1.8-celebration)/.8,0,1)
	for i in range(corners.size()-1):
		var start:Vector2=corners[i]
		var end:Vector2=corners[i+1]
		var length:=start.distance_to(end)
		var amount:=1.0 if reduced else clampf(remaining/length,0,1)
		if amount<=0:break
		var tip:=start.lerp(end,amount)
		draw_line(start,tip,Color(1,.64,.24,alpha*.24),7,true)
		draw_line(start,tip,Color(1,.88,.57,alpha),2,true)
		remaining-=length

func _draw() -> void:
	if values.size()!=42:return
	var r:=grid();var unit:=r.size.x/7;var radius:=unit*.43
	draw_frame(r)
	if loss_side>=0:
		var fade:=1.0 if reduced else smoothstep(0.0,.65,celebration)
		var half:=r.size.x*.5
		draw_rect(Rect2(r.position+Vector2(half*loss_side,0),Vector2(half,r.size.y)),Color(.04,.06,.12,fade*.36))
	if pressed and hover>=0 and not locked and values[hover]==0:
		var lane := Rect2(r.position+Vector2(hover*unit,0),Vector2(unit,r.size.y))
		var lane_ink := Art.color(is_cat(actor))
		draw_rect(lane,Color(lane_ink,.10))
		draw_line(lane.position+Vector2(4,2),Vector2(lane.end.x-4,lane.position.y+2),Color(lane_ink,.68),2,true)
	var animated_cell:int=int(event.get("cell",-1))
	if not reduced and animated_cell>=0:
		var landed:float=event.age-event.flight
		if landed>=0 and landed<.46:
			var origin:=center(animated_cell)
			var reach:=unit*(.4+landed*10)
			var ink:=Color(Art.color(is_cat(int(event.actor))),.46*(1-landed/.46))
			for offset in [-1,1]:
				var y:float=origin.y+offset*unit*.49
				draw_line(Vector2(maxf(r.position.x,origin.x-reach),y),Vector2(minf(r.end.x,origin.x+reach),y),ink,2.5,true)
	for cell in range(42):
		var point:=center(cell)
		var value:int=int(values[cell])
		var inactive:=not win_line.is_empty() and cell not in win_line
		var dim:=.5 if inactive else 1.0
		var rim:=Color("d8a4b0") if point.x<r.get_center().x else Color("9bc7df")
		draw_circle(point+Vector2(0,1),radius+1,Color("0a101a"),true,-1,true)
		draw_circle(point,radius,Color("253044").darkened(1-dim),true,-1,true)
		draw_circle(point+Vector2(0,1),radius*.84,Color("111a29"),true,-1,true)
		draw_arc(point,radius-.7,PI*1.05,PI*1.95,20,Color("060a12"),2.3,true)
		draw_arc(point,radius+.2,PI*.12,PI*.88,20,Color(rim,.52*dim),1.5,true)
		if value!=0 and cell!=animated_cell:
			var cat:=is_cat(value-1)
			var ink:=Art.color(cat)
			draw_circle(point+Vector2(0,2),radius*.95,Color(0,0,0,.46),true,-1,true)
			Art.face(self,point,radius*.94,cat,.58 if inactive else 1)
			draw_arc(point,radius*.86,PI*1.2,PI*1.66,14,Color(ink.lightened(.55),.58*dim),1.4,true)
			if win_line.is_empty() and cell==recent_cell:
				draw_arc(point,radius+1.4,-PI*.85,-PI*.15,24,Color(ink,.88),2.1,true)
				draw_circle(point+Vector2(0,-radius-1.2),2.4,ink)
	if pressed and hover>=0 and not locked and values[hover]==0:
		var row:=5
		while row>=0 and int(values[row*7+hover])!=0:row-=1
		if row>=0:
			var point:=center(row*7+hover)
			Art.face(self,point,radius*.96,is_cat(actor),.22)
			draw_arc(point,radius*.96,0,TAU,36,Color(Art.color(is_cat(actor)),.6),1.6,true)
		var point:=r.position+Vector2((hover+.5)*unit,-unit*.54)
		Art.face(self,point,radius*.65,is_cat(actor),.95)
	if not event.is_empty():
		var age:float=event.age;var point:=flight_point(age)
		if not reduced and age<float(event.flight):
			var trailing:=flight_point(maxf(0,age-.055))
			draw_line(trailing,point,Color(Art.color(is_cat(int(event.actor))),.25),radius*.72,true)
		var cat:=is_cat(int(event.actor))
		if reduced:
			Art.face(self,point,radius*.96,cat,minf(1,age/float(event.flight)))
		else:
			var tumble:=sin(clampf(age/float(event.flight),0,1)*PI)
			var direction:=1.0 if cat else -1.0
			Art.tilted_face(self,point,radius*.96,cat,tumble*.48*direction,1.0-.36*tumble)
		var landed:float=age-float(event.flight)
		if landed>=0 and landed<.32:
			var ink:=Art.color(is_cat(int(event.actor)));ink.a=(1-landed/.32)*.7
			draw_arc(center(int(event.cell)),radius*(.9+landed*1.6),0,TAU,40,ink,2,true)
			if not reduced:
				var shine:=landed*TAU*2-PI
				draw_arc(point,radius*.84,shine,shine+.85,16,Color(1,.98,.88,ink.a),2.4,true)
				for i in range(6):
					var a:=i*TAU/6
					Art.paw(self,point+Vector2(cos(a),sin(a))*unit*(.5+landed*1.2),3,ink)
	if not win_line.is_empty():
		var a:=center(int(win_line[0]));var b:=center(int(win_line[-1]));var t:=1.0 if reduced else clampf(celebration/.55,0,1)
		var end:=a.lerp(b,t)
		var result_ink:=Color("b6d4ee") if loss_side>=0 else Color("ffe3a4")
		draw_line(a,end,Color(result_ink,.16),13,true)
		draw_line(a,end,result_ink,3 if loss_side>=0 else 4,true)
		for i in range(win_line.size()):
			var point:=center(int(win_line[i]))
			var sweep:=celebration-i*.13
			var reveal:=1.0 if reduced else clampf(sweep/.17,0,1)
			if reveal>0:
				draw_arc(point,radius+1,0,TAU,40,Color(result_ink,reveal),2.2,true)
			if not reduced and sweep>0 and sweep<.68:
				var angle:float=-PI+sweep*8
				draw_arc(point,radius+3,angle,angle+1.1,20,Color(1,1,.86,sin(sweep/.68*PI)),3,true)
		if match_win and loss_side<0:draw_finale_rail(r)
		if match_win and loss_side<0 and not reduced and celebration>=.74 and celebration<2.4:
			var confetti_age:=celebration-.74
			for i in range(42):
				var x:=fposmod(i*81.17+sin(i*2.1+celebration*3)*19,size.x)
				var y:float=-20+fposmod(i*39.1,100)+confetti_age*(80+i%5*16)
				var ink:=Art.ROSE if i%2==0 else Art.BLUE;ink.a=clampf(2.4-celebration,0,1)
				draw_line(Vector2(x,y),Vector2(x+3,y+5),ink,3,true)
	if deny_time>0 and denied>=0:
		var point:=r.position+Vector2((denied+.5)*unit,-12)
		draw_arc(point,9,0,TAU,24,Color("ffa78d"),2,true)
		draw_line(point+Vector2(-5,5),point+Vector2(5,-5),Color("ffa78d"),2,true)
