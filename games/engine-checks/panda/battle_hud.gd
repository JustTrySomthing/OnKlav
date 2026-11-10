extends Control
const Duel=preload("res://panda/duel.gd")
const Art=preload("res://panda/battle_arena.gd")
var model
var time:=0.0
var reduced:=false
var card_styles:Dictionary={}
var shown:=[0.0,0.0]
var pulse:=[0.0,0.0]
func _ready():mouse_filter=Control.MOUSE_FILTER_IGNORE
func _process(dt):
	time+=dt
	if model!=null:
		for i in range(2):
			var target:float=Duel.points(model.totals[i])
			if absf(shown[i]-target)>1:pulse[i]=1
			shown[i]=move_toward(shown[i],target,maxf(15,absf(shown[i]-target)*9)*dt);pulse[i]=maxf(0,pulse[i]-dt*2)
	queue_redraw()
func text(s:String,p:Vector2,sz:int,color:Color):draw_string(ThemeDB.fallback_font,p,s,HORIZONTAL_ALIGNMENT_LEFT,-1,sz,color)
func _draw():
	if model==null:return
	var width:float=(size.x-70)*.5
	for side in range(2):
		var x:float=0 if side==0 else size.x-width;var active:bool=model.actor==side and model.phase not in["round","finished"]
		var edge:=Color("ffe2a1") if side==0 else Color("1479dd")
		var key:=side*2+int(active)
		if not card_styles.has(key):
			var style:=StyleBoxFlat.new()
			style.bg_color=Color("214e60f5") if active else Color("173f50f2")
			style.border_color=edge if active else Color(edge,.62)
			style.set_border_width_all(4 if active else 2)
			style.set_corner_radius_all(15)
			style.shadow_color=Color(0,.1,.2,.28)
			style.shadow_size=3
			card_styles[key]=style
		draw_style_box(card_styles[key],Rect2(x,2,width,size.y-5))
		if active:draw_reflection(x,width,edge)
		var portrait_size:=35.0
		draw_texture_rect(Art.PORTRAITS[side],Rect2(x+7,10,portrait_size,portrait_size),false)
		text("YOU" if side==0 else "RIVAL",Vector2(x+48,24),13,Color("b8dce1"))
		text(str(roundi(shown[side])),Vector2(x+47,51),25+int(pulse[side]*2),edge if pulse[side]>0 else Color.WHITE)
	var cx:=size.x*.5;var font=ThemeDB.fallback_font
	for pair in [["ROUND",13,23],[str(model.stage+1).pad_zeros(2),34,55]]:
		var width_value:float=font.get_string_size(pair[0],HORIZONTAL_ALIGNMENT_LEFT,-1,pair[1]).x
		text(pair[0],Vector2(cx-width_value*.5,pair[2]),pair[1],Color("101820"))

func draw_reflection(x:float,width:float,edge:Color):
	var travel:=.5 if reduced else fposmod(time,3.0)/1.25
	if travel>=1.0:return
	var center:=lerpf(x+45,x+width-45,travel)
	var bright:=Color(edge.lightened(.65),.25*sin(PI*travel))
	var clear:=Color(bright,0)
	for i in range(2):
		var left:=center-20+i*20
		var right:=left+20
		var colors:=PackedColorArray([clear,bright,bright,clear] if i==0 else [bright,clear,clear,bright])
		draw_polygon(PackedVector2Array([Vector2(left+9,8),Vector2(right+9,8),Vector2(right-9,size.y-9),Vector2(left-9,size.y-9)]),colors)
