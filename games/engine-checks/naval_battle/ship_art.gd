extends RefCounted
## Cell-aligned top-down ships shared by board, deployment dock and fleet HUD.
const LENGTHS := [5,4,3,3,2]
static func vessel(c:CanvasItem, origin:Vector2, unit:float, kind:int, vertical:bool=false, red:bool=false, selected:bool=false, alpha:float=1.0, sink:float=0.0, damage:Array[int]=[], smoke_time:float=-1.0) -> void:
	var length:float=LENGTHS[kind]
	var rotation := PI/2 if vertical else 0.0
	var center := Vector2(length*.5,.5)*unit
	var list_angle := .15*sink
	var scale := Vector2(1.0-.035*sink,1.0-.29*sink)
	var shift := center.rotated(rotation)-(center*scale).rotated(rotation+list_angle)
	var position := origin+(Vector2(unit,0) if vertical else Vector2.ZERO)+shift+Vector2(0,unit*.1*sink)
	c.draw_set_transform(position,rotation+list_angle,scale*unit)
	var metal:=Color(["a9ccd5","92b5c8","8ebeb2","668eaa","b5bfca"][kind]) if not red else Color("98575a").lerp(Color("633b45"),sink)
	metal.a=alpha
	var dark:=Color("294b61") if not red else Color("4b303c").darkened(sink*.24);dark.a=alpha
	var bright:=Color("e1f5ed") if not red else Color("e39475").darkened(sink*.3);bright.a=alpha
	var hull:=PackedVector2Array([Vector2(.14,.23),Vector2(length-.65,.16),Vector2(length-.27,.3),Vector2(length-.06,.5),Vector2(length-.27,.7),Vector2(length-.65,.84),Vector2(.14,.77),Vector2(.06,.63),Vector2(.06,.37)])
	if kind==4:
		hull=PackedVector2Array([Vector2(.09,.31),Vector2(1.16,.24),Vector2(1.64,.35),
			Vector2(1.97,.5),Vector2(1.64,.65),Vector2(1.16,.76),Vector2(.09,.69)])
	elif kind==3:
		hull=PackedVector2Array()
		for i in range(28):
			var a:=TAU*i/28.0
			hull.append(Vector2(length*.5+cos(a)*(length*.5-.08),.5+sin(a)*.28))
	if not red and not selected:
		for side in [-1.0,1.0]:
			var edge := .5+float(side)*(.32 if kind==4 else .39)
			var wake := PackedVector2Array([Vector2(.08,edge+side*.055),Vector2(.4,edge),
				Vector2(length*.6,edge),Vector2(length-.38,edge-side*.08)])
			c.draw_polyline(wake,Color("529eb4",alpha*.22),.10,true)
			c.draw_polyline(wake,Color("b5eee5",alpha*.35),.023,true)
			for i in range(2,int(length*4)-2):
				var x := i*.23
				var y := edge+float(side)*.04
				c.draw_line(Vector2(x,y),Vector2(x+.085,y-side*.014),Color("d3fcf1",alpha*.28),.02,true)
	var shadow:=PackedVector2Array()
	for p in hull:shadow.append(p+Vector2(.035,.09))
	c.draw_colored_polygon(shadow,Color(0,0,0,.36*alpha))
	var colors:=PackedColorArray()
	for p in hull:colors.append(bright.lerp(dark,clampf(p.y*1.18-.08,0,1)))
	c.draw_polygon(hull,colors)
	var outline:=hull.duplicate();outline.append(hull[0]);c.draw_polyline(outline,Color("76f2dc",alpha) if selected else metal,.035,true)
	var deck_edge := .34 if kind==4 else .30
	c.draw_line(Vector2(.25,deck_edge),Vector2(length-.62,deck_edge),bright,.025,true)
	c.draw_line(Vector2(.25,1.0-deck_edge),Vector2(length-.62,1.0-deck_edge),dark,.048,true)
	if unit>=16:
		for i in range(1,int(length*4)-2):
			var x := i*.23+.12
			c.draw_line(Vector2(x,deck_edge),Vector2(x+.055,deck_edge),Color(bright,alpha*.68),.024,true)
	if not red:
		c.draw_circle(Vector2(.18,.42),.032,Color("ffe4a0",alpha),true)
		c.draw_circle(Vector2(length-.23,.5),.034,Color("e3ffef",alpha),true)
	match kind:
		0:
			# Carrier runway, arresting wires and the offset island.
			c.draw_rect(Rect2(.23,.34,length-.92,.32),dark)
			for x in [.43,.98,1.53,2.08,2.63,3.18,3.73]:
				c.draw_line(Vector2(x,.49),Vector2(x+.30,.49),bright,.025,true)
			for x in [.40,.49,.58,length-.94,length-1.03]:
				c.draw_line(Vector2(x,.38),Vector2(x,.62),bright,.025,true)
			bridge(c,Vector2(2.82,.67),.74,.16,metal,dark,bright)
			if unit>=16:
				for x in [1.15,1.75,2.35]:
					c.draw_line(Vector2(x-.16,.37),Vector2(x+.18,.37),metal,.035,true)
		1:
			# Twin forward batteries and an aft battery face opposite directions.
			bridge(c,Vector2(1.65,.5),.86,.42,metal,dark,bright)
			turret(c,.62,-1.0,.18,3,metal,dark,bright)
			turret(c,2.65,1.0,.17,3,metal,dark,bright)
			turret(c,3.22,1.0,.18,3,metal,dark,bright)
			for x in [1.42,1.89]:
				c.draw_circle(Vector2(x,.5),.105,dark,true)
				c.draw_arc(Vector2(x,.48),.075,PI,TAU,10,bright,.024,true)
		2:
			bridge(c,Vector2(1.24,.5),.60,.37,metal,dark,bright)
			turret(c,2.31,1.0,.15,2,metal,dark,bright)
			c.draw_rect(Rect2(1.62,.36,.40,.28),dark)
			for x in [1.69,1.82,1.95]:
				c.draw_line(Vector2(x,.40),Vector2(x,.59),bright,.026,true)
			c.draw_arc(Vector2(.52,.5),.12,0,TAU,14,metal,.027,true)
		3:
			# Rounded pressure hull and a narrow conning tower.
			c.draw_line(Vector2(.37,.5),Vector2(length-.35,.5),metal,.032,true)
			bridge(c,Vector2(1.57,.5),.47,.32,metal,dark,bright)
			c.draw_line(Vector2(1.58,.53),Vector2(1.77,.42),bright,.043,true)
			for x in [.40,2.60]:
				c.draw_line(Vector2(x,.41),Vector2(x,.59),dark,.055,true)
		4:
			bridge(c,Vector2(.91,.5),.51,.31,metal,dark,bright)
			turret(c,1.46,1.0,.112,1,metal,dark,bright)
			c.draw_rect(Rect2(.25,.38,.30,.24),dark)
			c.draw_line(Vector2(.3,.5),Vector2(.49,.5),bright,.027,true)
	for segment in damage:
		var point := Vector2(segment+.50,.51)
		c.draw_colored_polygon(PackedVector2Array([point+Vector2(-.34,-.10),point+Vector2(-.12,-.28),
			point+Vector2(.20,-.19),point+Vector2(.31,.12),point+Vector2(.06,.23),
			point+Vector2(-.22,.15)]),Color("191c25",alpha*.94))
		c.draw_line(point+Vector2(-.17,.07),point+Vector2(.18,-.12),Color("cf7953",alpha*(1.0-sink*.62)),.043,true)
	c.draw_set_transform(Vector2.ZERO)
	if damage.is_empty() or smoke_time<0.0:return
	# Draw drifting smoke in board space so vertical ships share the same wind.
	var source := position+(Vector2(damage.back()+.50,.49)*scale*unit).rotated(rotation+list_angle)
	for i in range(5,0,-1):
		var phase := fmod(smoke_time*.36+i/5.0,1.0)
		var drift := Vector2(.52*phase+sin(phase*5.0)*.065,-.82*phase)*unit
		c.draw_circle(source+drift,unit*(.09+phase*.23),Color("82808b",(1.0-phase)*alpha*.24),true)

static func bridge(c:CanvasItem, point:Vector2, width:float, depth:float, metal:Color, dark:Color, bright:Color) -> void:
	c.draw_rect(Rect2(point-Vector2(width,depth)*.5,Vector2(width,depth)),dark)
	c.draw_rect(Rect2(point-Vector2(width*.36,depth*.40),Vector2(width*.63,depth*.60)),metal)
	var glass := point+Vector2(width*.22,-depth*.29)
	c.draw_line(glass,glass+Vector2(0,depth*.46),bright,.036,true)

static func turret(c:CanvasItem, x:float, direction:float, radius:float, barrels:int, metal:Color, dark:Color, bright:Color) -> void:
	var point := Vector2(x,.5)
	c.draw_circle(point,radius,dark,true)
	c.draw_circle(point-Vector2(0,.028),radius*.75,metal,true)
	for i in range(barrels):
		var start := point+Vector2(direction*radius*.30,(i-(barrels-1)*.5)*.071-.014)
		c.draw_line(start,start+Vector2(direction*.30,0),bright,.033,true)
