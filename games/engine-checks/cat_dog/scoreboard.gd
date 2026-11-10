extends Control
const Art=preload("res://cat_dog/art.gd")
const Chrome=preload("res://game_chrome.gd")
var match_wins:=[0,0]
var round_id:=1
var values:Array=[]
var me:=0
var host_dog:=false
var turn:=0
var winner:=-1
var finished:=false
var reaction_actor:=-1
var reaction_kind:=""
var reaction_time:=0.0
var reduced:=false
func _ready() -> void:
	mouse_filter=Control.MOUSE_FILTER_IGNORE;custom_minimum_size=Vector2(0,112);resized.connect(queue_redraw)
func react(actor:int,kind:String) -> void:
	reaction_actor=actor
	reaction_kind=kind
	reaction_time=1.08 if kind=="loss" else .8
	queue_redraw()

func _process(delta:float) -> void:
	if reaction_time<=0:return
	reaction_time=maxf(0,reaction_time-delta)
	queue_redraw()

func text(value:String,point:Vector2,font_size:int,color:Color) -> void:draw_string(ThemeDB.fallback_font,point,value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,color)
func chain(actor:int) -> int:
	var best:=0
	for row in range(6):
		for col in range(7):
			if values[row*7+col]!=actor+1:continue
			for d in [Vector2i(1,0),Vector2i(0,1),Vector2i(1,1),Vector2i(1,-1)]:
				var count:=1
				for i in range(1,4):
					var x:int=col+d.x*i;var y:int=row+d.y*i
					if x<0 or x>6 or y<0 or y>5 or values[y*7+x]!=actor+1:break
					count+=1
				best=maxi(best,count)
	return best
func card(rect:Rect2,actor:int) -> void:
	var cat:bool=(actor==0)!=host_dog;var ink:=Art.color(cat)
	var active:bool=(turn==actor and not finished) or (finished and winner==actor)
	var style:=Chrome.box("253647" if cat else "14354e",ink.to_html(false) if active else "3a536a",14)
	var defeated:=finished and winner>=0 and winner!=me and actor==me
	style.border_color=Color(ink,.9 if active else .15 if defeated else .28)
	if defeated:style.bg_color=style.bg_color.darkened(.34)
	draw_style_box(style,rect)
	if active:draw_line(rect.position+Vector2(13,2),rect.position+Vector2(rect.size.x-13,2),ink,2,true)
	var compact:bool=size.y<112
	var radius:=25.0 if compact else 29.0
	var portrait:=Vector2(rect.position.x+radius+10,rect.get_center().y)
	if reaction_time>0 and not reduced:
		var t:=1.0-reaction_time/(1.08 if reaction_kind=="loss" else .8)
		var arc:=sin(t*PI)
		var lean:=0.0
		if defeated and reaction_kind=="loss":
			var bow:=smoothstep(0.0,.28,t)*(1.0-smoothstep(.60,1.0,t))
			portrait.y+=bow*7
			lean=-bow*.22
		elif actor==reaction_actor and reaction_kind in ["chain","result"]:
			portrait.y-=arc*4
			lean=sin(t*TAU)*.12
			draw_arc(portrait,radius+1,-PI,0,20,Color(ink,arc*.8),1.5,true)
		elif actor!=reaction_actor and reaction_kind in ["block","result"]:
			portrait.y+=arc*3
			lean=sin(t*TAU*2)*reaction_time*.26 if reaction_kind=="block" else arc*.14
		Art.tilted_face(self,portrait,radius,cat,lean)
	else:
		Art.face(self,portrait,radius,cat)
	var score:=str(match_wins[actor])
	var font:=ThemeDB.fallback_font
	var score_size:=40 if compact else 46
	var score_center:=Vector2(lerpf(portrait.x+radius,rect.end.x,.56),rect.get_center().y)
	var width:=font.get_string_size(score,HORIZONTAL_ALIGNMENT_LEFT,-1,score_size).x
	text(score,score_center+Vector2(-width*.5,8),score_size,ink.darkened(.32) if defeated else ink)
	var longest:=chain(actor)
	for i in range(4):
		var point:=score_center+Vector2((i-1.5)*13,rect.size.y*.31)
		draw_circle(point,3.8,Color(ink,.9 if i<longest else .19))
		if i<longest:draw_circle(point-Vector2(1,1),1.0,Color("ffffff99"))
func _draw() -> void:
	if values.size()!=42:return
	var width:float=(size.x-12)/2
	var title:="ROUND %d  ·  FIRST TO 3"%round_id
	var font:=ThemeDB.fallback_font
	var font_size:=22
	text(title,Vector2((size.x-font.get_string_size(title,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x)/2,23),font_size,Color("e0ecfa"))
	card(Rect2(0,34,width,size.y-34),me)
	card(Rect2(width+12,34,width,size.y-34),1-me)
