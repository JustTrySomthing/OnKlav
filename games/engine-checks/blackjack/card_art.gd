extends RefCounted
const Chrome=preload("res://game_chrome.gd")
const RED=Color("a52e44")
const BLACK=Color("193242")
const GOLD=Color("e2bd79")
static func suit(c:CanvasItem,point:Vector2,radius:float,kind:int,ink:Color) -> void:
	if kind==2:
		c.draw_colored_polygon(PackedVector2Array([point+Vector2(0,-radius),point+Vector2(radius*.7,0),point+Vector2(0,radius),point+Vector2(-radius*.7,0)]),ink)
	elif kind==3:
		for d in [Vector2(0,-.45),Vector2(-.43,.15),Vector2(.43,.15)]:c.draw_circle(point+d*radius,radius*.47,ink)
		c.draw_colored_polygon(PackedVector2Array([point+Vector2(-.38,.9)*radius,point+Vector2(0,.12)*radius,point+Vector2(.38,.9)*radius]),ink)
	else:
		var points:=PackedVector2Array()
		for i in range(48):
			var a:=TAU*i/48.0
			var x:float=pow(sin(a),3)
			var y:float=-(13*cos(a)-5*cos(2*a)-2*cos(3*a)-cos(4*a))/17
			points.append(point+Vector2(x,y)*radius*(1 if kind==1 else -1))
		c.draw_colored_polygon(points,ink)
		if kind==0:c.draw_colored_polygon(PackedVector2Array([point+Vector2(-.37,.98)*radius,point+Vector2(0,.15)*radius,point+Vector2(.37,.98)*radius]),ink)
static func text(c:CanvasItem,value:String,point:Vector2,size_value:int,ink:Color,centered:bool=false) -> void:
	var font:=ThemeDB.fallback_font
	if centered:point.x-=font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,size_value).x*.5
	c.draw_string(font,point,value,HORIZONTAL_ALIGNMENT_LEFT,-1,size_value,ink)
static func court(c:CanvasItem,extent:Vector2,rank:int,ink:Color) -> void:
	var width:=extent.x
	var height:=extent.y
	var frame:=Rect2(-width*.29,-height*.29,width*.58,height*.58)
	c.draw_style_box(Chrome.box("eadbb6",ink.to_html(false),4),frame)
	for direction in [-1,1]:
		var face:=Vector2(width*.04*direction,height*.15*direction)
		var shoulders:=face+Vector2(0,-direction*height*.074)
		var mantle:=PackedVector2Array([shoulders+Vector2(-width*.18,0),shoulders+Vector2(0,-direction*height*.054),shoulders+Vector2(width*.18,0),shoulders+Vector2(0,direction*height*.054)])
		c.draw_colored_polygon(mantle,ink)
		c.draw_circle(face,width*.087,Color("a8784f"),true,-1,true)
		c.draw_circle(face-Vector2(width*.012,0),width*.068,Color("fff1c8"),true,-1,true)
		var top:=face+Vector2(0,direction*width*.09)
		for spike in range(3):
			var offset:=(spike-1)*width*.05
			var start:=top+Vector2(offset,0)
			c.draw_line(start,start+Vector2(0,direction*width*(.04 if rank==10 else .078)),GOLD,1.4,true)
		c.draw_line(top-Vector2(width*.084,0),top+Vector2(width*.084,0),GOLD,1.8,true)

static func card(c:CanvasItem,center:Vector2,extent:Vector2,value:int,back:bool,angle:float=0.0,squeeze:float=1.0,alpha:float=1.0) -> void:
	c.draw_set_transform(center,angle,Vector2(squeeze,1))
	var rect:=Rect2(-extent*.5,extent)
	var style:=Chrome.box("103f43" if back else "fff9eb","cfae76" if back else "d4d3c9",7)
	style.shadow_color=Color(0,0,0,.4*alpha);style.shadow_size=4;style.shadow_offset=Vector2(1,3)
	c.draw_style_box(style,rect)
	var inset:=rect.grow(-4)
	c.draw_style_box(Chrome.box("12383c" if back else "fffdf5","b8a478" if back else "e8dfc9",4),inset)
	if back:
		for row in range(8):
			for col in range(5):
				var p:=inset.position+Vector2((col+.5)*inset.size.x/5,(row+.5)*inset.size.y/8)
				var rr:=minf(extent.x*.055,3.5)
				c.draw_polyline(PackedVector2Array([p+Vector2(0,-rr),p+Vector2(rr,0),p+Vector2(0,rr),p+Vector2(-rr,0),p+Vector2(0,-rr)]),Color(.8,.7,.42,.3),.65,true)
		c.draw_circle(Vector2.ZERO,extent.x*.28,Color("12383c"));c.draw_arc(Vector2.ZERO,extent.x*.29,0,TAU,40,GOLD,.9,true)
		for i in range(8):
			var a:=i*TAU/8;var p:=Vector2(cos(a),sin(a))*extent.x*.35
			c.draw_line(p*.9,p*1.12,GOLD,.7,true)
		text(c,"OK",Vector2(0,extent.y*.05),int(extent.x*.22),GOLD,true)
	else:
		var rank:int=value%13;var kind:int=value/13;var ink:=RED if kind in [1,2] else BLACK
		var label:String=["A","2","3","4","5","6","7","8","9","10","J","Q","K"][rank]
		var font_size:=maxi(10,int(extent.y*.14));var corner:=rect.position+Vector2(extent.x*.14,extent.y*.19)
		text(c,label,corner,font_size,ink,true);suit(c,corner+Vector2(0,extent.y*.105),extent.y*.046,kind,ink)
		c.draw_set_transform(center,angle+PI,Vector2(squeeze,1));text(c,label,corner,font_size,ink,true);suit(c,corner+Vector2(0,extent.y*.105),extent.y*.046,kind,ink);c.draw_set_transform(center,angle,Vector2(squeeze,1))
		if rank==0:suit(c,Vector2.ZERO,extent.y*.18,kind,ink)
		elif rank>=10:
			court(c,extent,rank,ink)
			c.draw_circle(Vector2.ZERO,extent.x*.125,Color("fff4d6"),true,-1,true)
			text(c,label,Vector2(0,extent.y*.04),int(extent.y*.12),ink,true)
		else:
			var count:int=rank+1;var points:Array=[]
			if count<=3:
				for i in range(count):points.append(Vector2(0,-.23+i*.46/maxi(1,count-1)))
			else:
				var rows:int=2 if count<=5 else 3 if count<=8 else 4
				for row in range(rows):
					for x in [-.2,.2]:points.append(Vector2(x,-.25+row*.5/(rows-1)))
				if count in [5,7,9]:points.append(Vector2(0,0 if count!=7 else -.12))
				if count in [8,10]:points.append_array([Vector2(0,-.12),Vector2(0,.12)])
			for point in points:suit(c,Vector2(point.x*extent.x,point.y*extent.y),extent.y*.06,kind,ink)
	c.draw_set_transform(Vector2.ZERO)
