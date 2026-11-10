extends Control
const Art=preload("res://blackjack/card_art.gd")
const Chrome=preload("res://game_chrome.gd")
var hands:Array=[[],[]]
var me:=0
var friend:=false
var revealed:=false
var stood:Array=[false,false]
var hand_over:=false
var winning_actor:=-1
var active:Dictionary={}
var counts:Array=[2.0,2.0]
var targets:Array=[2.0,2.0]
var reduced:=false
var result_age:=10.0
var result_kind:=""
var landing_age:=1.0
var landing_actor:=0
var landing_index:=0

func land(actor:int,index:int) -> void:
	landing_actor=actor
	landing_index=index
	landing_age=0

func _ready() -> void:
	mouse_filter=Control.MOUSE_FILTER_IGNORE;resized.connect(queue_redraw)
func card_size() -> Vector2:
	var height:=clampf(size.y*.29,68,124)
	return Vector2(height*.69,height)
func deck_point() -> Vector2:return Vector2(size.x*.86,size.y*.105)
func place(actor:int,index:int,count_override:float=-1) -> Vector2:
	var count_value:float=counts[actor] if count_override<0 else count_override
	var extent:=card_size();var step:=minf(extent.x*.8,(size.x-extent.x-36)/maxf(1,count_value-1))
	return Vector2(size.x*.5+(index-(count_value-1)*.5)*step,size.y*(.745 if actor==me else .27)+absf(index-(count_value-1)*.5)*2)
func angle_for(actor:int,index:int) -> float:return clampf((index-(counts[actor]-1)*.5)*.035,-.12,.12)
func face_down(actor:int,index:int) -> bool:return actor!=me and not revealed and index==(0 if friend else 1)
func _process(delta:float) -> void:
	var moved:=false
	for i in range(2):
		if counts[i]!=targets[i]:counts[i]=targets[i] if reduced else move_toward(float(counts[i]),float(targets[i]),delta*8);moved=true
	if result_age<2.4:result_age+=delta;moved=true
	if landing_age<.36:landing_age+=delta;moved=true
	if moved or not active.is_empty():queue_redraw()
func centered(value:String,at:Vector2,points:int,ink:Color) -> void:
	var font:=ThemeDB.fallback_font
	var width:=font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,points).x
	draw_string(font,at-Vector2(width*.5,0),value,HORIZONTAL_ALIGNMENT_LEFT,-1,points,ink)

func diamond(at:Vector2,extent:Vector2,ink:Color) -> void:
	draw_colored_polygon(PackedVector2Array([at-Vector2(0,extent.y),at+Vector2(extent.x,0),at+Vector2(0,extent.y),at-Vector2(extent.x,0)]),ink)

func felt(rect:Rect2) -> void:
	var outer:=Chrome.box("171d22","c4a478",24)
	outer.shadow_color=Color(0,0,0,.55);outer.shadow_size=10;outer.shadow_offset=Vector2(0,5)
	outer.border_width_bottom=3
	draw_style_box(outer,rect)
	draw_style_box(Chrome.box("48372b","816749",21),rect.grow(-4))
	var inner:=rect.grow(-9)
	draw_style_box(Chrome.box("0e2c2a","b99c63",17),inner)
	var cloth:=inner.grow(-4)
	var center:=cloth.get_center()
	var vertices:=PackedVector2Array([center])
	var colors:=PackedColorArray([Color("245847")])
	# A softly lit felt center with a rounded, dark edge.
	for corner in range(4):
		var offset:=Vector2(1 if corner in [0,3] else -1,1 if corner in [0,1] else -1)
		var arc_center:=center+(cloth.size*.5-Vector2.ONE*14)*offset
		for i in range(5):
			var angle:=corner*PI*.5+i*PI/8
			vertices.append(arc_center+Vector2.from_angle(angle)*14)
			colors.append(Color("092425"))
	for i in range(1,vertices.size()):
		var next:=1 if i==vertices.size()-1 else i+1
		draw_polygon(PackedVector2Array([center,vertices[i],vertices[next]]),PackedColorArray([colors[0],colors[i],colors[next]]))
	for i in range(84):
		var u:=fposmod(i*.618034,.98)
		var v:=fposmod(i*.414214,.98)
		var p:=cloth.position+Vector2(.04+u*.92,.04+v*.92)*cloth.size
		draw_line(p,p+Vector2(1.1,.25),Color(.74,.86,.68,.065),1)
	var oval:=PackedVector2Array()
	for i in range(65):
		var angle:=TAU*i/64
		oval.append(center+Vector2(cos(angle)*cloth.size.x*.47,sin(angle)*cloth.size.y*.45))
	draw_polyline(oval,Color("d8b77838"),1.2,true)
	for side in [-1,1]:
		var x:float=center.x+side*cloth.size.x*.31
		draw_line(Vector2(x-side*19,center.y),Vector2(x+side*12,center.y),Color("c6a3694d"),1,true)
		diamond(Vector2(x+side*16,center.y),Vector2(3,5),Color("e0bc7e63"))
	var radius:=minf(24,size.y*.07)
	draw_circle(center,radius,Color("0e292746"),true,-1,true)
	draw_arc(center,radius,0,TAU,48,Color("caa36764"),1.2,true)
	draw_arc(center,radius-3,0,TAU,48,Color("caa36730"),1,true)
	centered("21",center+Vector2(0,radius*.30),maxi(11,roundi(radius*1.03)),Color("ffe2a27a"))

func _draw() -> void:
	if size.x<70 or size.y<80:return
	var rect:=Rect2(4,7,size.x-8,size.y-14)
	felt(rect)
	var extent:=card_size()
	for offset in [Vector2(4,-4),Vector2(2,-2),Vector2.ZERO]:Art.card(self,deck_point()+offset,extent*.39,0,true,.12)
	if not reduced and landing_age<.36:
		var t:=landing_age/.36
		var at:=place(landing_actor,landing_index)
		var ink:=Color("9ef5d7") if landing_actor==me else Color("ffdb94")
		draw_arc(at,extent.x*(.50+t*.30),PI*.12,PI*.88,28,Color(ink,(1-t)*.76),2.2,true)
		for side in [-1,1]:
			var p:=at+Vector2(side*extent.x*(.50+t*.35),extent.y*.31+t*6)
			diamond(p,Vector2(2.5,4)*(1-t),Color(ink,(1-t)*.9))
	for actor in [1-me,me]:
		var label:="YOU" if actor==me else "FRIEND" if friend else "DEALER"
		if stood[actor] and not hand_over:label+=" · STANDING"
		var ink:=Color("c4f4e3" if actor==me else "f3d9a0")
		var point:=Vector2(26,clampf(size.y*.105,31,50) if actor!=me else size.y-clampf(size.y*.09,29,45))
		var width:=ThemeDB.fallback_font.get_string_size(label,HORIZONTAL_ALIGNMENT_LEFT,-1,11).x
		draw_style_box(Chrome.box("112b2b","6b785d",6),Rect2(point-Vector2(5,13),Vector2(width+18,19)))
		diamond(point+Vector2(2,-3),Vector2(2,3),ink)
		draw_string(ThemeDB.fallback_font,point+Vector2(9,0),label,HORIZONTAL_ALIGNMENT_LEFT,-1,11,ink)
		if hand_over and actor==winning_actor and not hands[actor].is_empty():
			var gold:=Color("c8a56c")
			var left:=place(actor,0)
			var right:=place(actor,hands[actor].size()-1)
			var rail_y:=maxf(left.y,right.y)+extent.y*.53+5
			draw_line(Vector2(left.x-extent.x*.35,rail_y),Vector2(right.x+extent.x*.35,rail_y),Color(gold,.8),1.5,true)
			diamond(Vector2((left.x+right.x)*.5,rail_y),Vector2(3,4),gold)
		for index in range(hands[actor].size()):
			if not active.is_empty() and active.get("kind")in["deal","flip"] and int(active.actor)==actor and int(active.index)==index:continue
			Art.card(self,place(actor,index),extent,int(hands[actor][index]),face_down(actor,index),angle_for(actor,index))
	if not active.is_empty() and active.get("kind")in["deal","flip"]:
		var actor:int=active.actor;var index:int=active.index;var duration:float=active.duration;var t:=clampf(float(active.age)/duration,0,1)
		var dest:=place(actor,index,float(targets[actor]));var point:=dest;var angle:=angle_for(actor,index);var squeeze:=1.0
		var back:=face_down(actor,index)
		if active.kind=="deal" and not reduced:
			var ease:=1-pow(1-t,3);point=deck_point().lerp(dest,ease)-Vector2(0,sin(PI*ease)*24);angle=lerpf(.26,angle,ease)
			squeeze=lerpf(.72,1.0,ease)
			for i in range(1,4):
				var tail:=maxf(0,ease-i*.045)
				var at:=deck_point().lerp(dest,tail)-Vector2(0,sin(PI*tail)*24)
				diamond(at,Vector2(3,6),Color(.98,.81,.48,sin(PI*t)*(.27-i*.055)))
		elif active.kind=="flip":
			squeeze=1.0 if reduced else maxf(.025,absf(cos(PI*t)))
			back=t<.5
		Art.card(self,point,extent,int(active.card),back,angle,squeeze)
	if not reduced and result_age<2.4:
		var glow:=maxf(0,1-result_age/.7)
		var color:=Color("ffdb88") if result_kind=="win" else Color("e3bd93") if result_kind=="draw" else Color("edab8c")
		var rim:=Chrome.box("00000000",Color(color,glow*.85).to_html(),24)
		rim.set_border_width_all(2)
		draw_style_box(rim,rect)
	if not reduced and result_kind=="win" and result_age<2.4:
		for i in range(18):
			var a:float=i*2.39996;var p:=rect.get_center()+Vector2(cos(a),sin(a))*Vector2(rect.size.x*.42,rect.size.y*.4)*(1-result_age*.1)
			var alpha:=clampf(1-result_age/2.4,0,1)*(.5+.5*sin(i+result_age*7))
			diamond(p,Vector2(2+i%3,4+i%3),Color(1,.88,.57,alpha))
