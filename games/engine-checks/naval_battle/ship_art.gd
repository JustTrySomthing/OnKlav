extends RefCounted
## Cell-aligned top-down ships shared by board, deployment dock and fleet HUD.
const LENGTHS := [5,4,3,3,2]
static func vessel(c:CanvasItem, origin:Vector2, unit:float, kind:int, vertical:bool=false, red:bool=false, selected:bool=false, alpha:float=1.0) -> void:
	var length:float=LENGTHS[kind]
	c.draw_set_transform(origin+(Vector2(unit,0) if vertical else Vector2.ZERO),PI/2 if vertical else 0,Vector2.ONE*unit)
	var metal:=Color(["a9ccd5","92b5c8","8ebeb2","668eaa","b5bfca"][kind]) if not red else Color("e45c64")
	metal.a=alpha
	var dark:=Color("294b61") if not red else Color("702b3b");dark.a=alpha
	var bright:=Color("e1f5ed") if not red else Color("ffc0a3");bright.a=alpha
	var hull:=PackedVector2Array([Vector2(.14,.23),Vector2(length-.65,.16),Vector2(length-.27,.3),Vector2(length-.06,.5),Vector2(length-.27,.7),Vector2(length-.65,.84),Vector2(.14,.77),Vector2(.06,.63),Vector2(.06,.37)])
	if kind==3:
		hull=PackedVector2Array()
		for i in range(28):
			var a:=TAU*i/28.0
			hull.append(Vector2(length*.5+cos(a)*(length*.5-.08),.5+sin(a)*.28))
	var shadow:=PackedVector2Array()
	for p in hull:shadow.append(p+Vector2(.035,.09))
	c.draw_colored_polygon(shadow,Color(0,0,0,.36*alpha))
	var colors:=PackedColorArray()
	for p in hull:colors.append(bright.lerp(dark,clampf(p.y,0,1)))
	c.draw_polygon(hull,colors)
	var outline:=hull.duplicate();outline.append(hull[0]);c.draw_polyline(outline,Color("76f2dc",alpha) if selected else metal,.035,true)
	c.draw_line(Vector2(.26,.3),Vector2(length-.65,.3),bright,.025,true)
	if kind==0:
		c.draw_rect(Rect2(.25,.33,length-.87,.34),dark)
		c.draw_line(Vector2(.38,.49),Vector2(length-.8,.49),bright,.025,true)
		for x in [.4,length-1.0]:c.draw_line(Vector2(x,.36),Vector2(x,.62),bright,.025,true)
		c.draw_rect(Rect2(length*.48,.66,.85,.12),dark)
		c.draw_rect(Rect2(length*.54,.64,.46,.1),metal)
		for i in range(3):
			var p:=Vector2(.8+i*.95,.49)
			c.draw_colored_polygon(PackedVector2Array([p+Vector2(.24,0),p+Vector2(-.07,-.14),p+Vector2(-.03,-.035),p+Vector2(-.17,-.07),p+Vector2(-.14,0),p+Vector2(-.17,.07),p+Vector2(-.03,.035),p+Vector2(-.07,.14)]),bright)
	elif kind==3:
		c.draw_line(Vector2(.45,.5),Vector2(length-.4,.5),metal,.025,true)
		c.draw_rect(Rect2(length*.44,.33,.45,.34),dark)
		c.draw_rect(Rect2(length*.48,.37,.27,.25),metal)
		c.draw_line(Vector2(length*.5,.48),Vector2(length*.5+.13,.48),bright,.045,true)
		for x in [.35,length-.4]:c.draw_circle(Vector2(x,.5),.045,dark)
	else:
		var mid:=length*.49
		c.draw_rect(Rect2(mid-.28,.31,.57,.38),dark)
		c.draw_rect(Rect2(mid-.19,.34,.33,.32),metal)
		c.draw_line(Vector2(mid-.05,.29),Vector2(mid+.23,.29),bright,.04,true)
		var turrets:=[.65,length-.7] if kind!=4 else [length-.48]
		if kind==1:turrets.append(length-1.25)
		for x in turrets:
			c.draw_circle(Vector2(x,.5),.145,dark)
			c.draw_circle(Vector2(x,.47),.108,metal)
			for y in ([.445,.515] if kind==1 else [.48]):c.draw_line(Vector2(x,y),Vector2(x+.35,y),bright,.035,true)
		if kind==2:
			for x in [mid+.4,mid+.58]:
				c.draw_rect(Rect2(x,.37,.12,.24),dark)
				c.draw_line(Vector2(x,.42),Vector2(x+.12,.42),bright,.025)
		if kind==4:c.draw_rect(Rect2(.3,.39,.24,.22),dark)
	if red:
		for i in range(1,int(length)):
			c.draw_line(Vector2(i-.12,.3),Vector2(i+.12,.65),Color("3e2537",alpha),.06,true)
	c.draw_set_transform(Vector2.ZERO)
