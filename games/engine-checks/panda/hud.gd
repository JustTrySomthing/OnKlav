extends Control
const Art=preload("res://panda/arena.gd")
var model
var me:=0
var friend:=false
var seconds:=20.0
var shown:=[0.0,0.0]
var pulse:=[0.0,0.0]
var time:=0.0
func _ready():mouse_filter=Control.MOUSE_FILTER_IGNORE
func _process(dt):
	time+=dt
	if model!=null:
		for i in range(2):
			if absf(shown[i]-model.score(i))>.1:pulse[i]=1
			shown[i]=move_toward(shown[i],model.score(i),maxf(2,absf(shown[i]-model.score(i))*8)*dt);pulse[i]=maxf(0,pulse[i]-dt*2)
	queue_redraw()
func text(text:String,p:Vector2,s:int,color:Color):draw_string(ThemeDB.fallback_font,p,text,HORIZONTAL_ALIGNMENT_LEFT,-1,s,color)
func _draw():
	if model==null:return
	var w:float=(size.x-90)*.5
	for side in range(2):
		var actor:=me if side==0 else 1-me;var x:=0.0 if side==0 else size.x-w;var active:bool=model.turn==actor and model.winner==-2
		var style:=StyleBoxFlat.new();style.bg_color=Color("123b46e8");style.border_color=Color("ffe299") if active else Color("8ce1d480");style.set_border_width_all(2 if active else 1);style.set_corner_radius_all(16);draw_style_box(style,Rect2(x,0,w,size.y-4))
		var character:int=model.character if actor==0 else 1-model.character
		draw_texture_rect(Art.PORTRAITS[character],Rect2(x+7,8,38,38),false)
		text("YOU" if side==0 else "FRIEND" if friend else "RIVAL",Vector2(x+49,20),10,Color("b5eade"))
		text(str(roundi(shown[actor])),Vector2(x+48,52),29+roundi(pulse[actor]*2),Color("fff0c2") if pulse[actor]>0 else Color("ffffff"))
		text("POINTS" if model.revision>=2 else "BLOCKS",Vector2(x+8,size.y-13),9,Color("b5eade"));text(str(model.round_wins[actor])+" WINS",Vector2(x+w-53,size.y-13),9,Color("b5eade"))
	var cx:=size.x*.5
	var timed:bool=model.phase in ["build","shoot","flight"]
	var label:="BUILD" if model.phase=="build" else "AIM" if model.phase in ["shoot","flight"] else "LEVEL"
	var number:=str(ceili(seconds)).pad_zeros(2) if timed else str(model.level+1)+"/3"
	var font=ThemeDB.fallback_font
	var ink:=Color("ff796b") if timed and seconds<8 else Color("fff1b8")
	var font_size:=32 if timed else 22
	var width:float=font.get_string_size(number,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
	text(label,Vector2(cx-font.get_string_size(label,HORIZONTAL_ALIGNMENT_LEFT,-1,11).x/2,17),11,Color("fff1b8"))
	text(number,Vector2(cx-width/2,53),font_size,ink)
	draw_arc(Vector2(cx,39),37,-PI/2,-PI/2+TAU*(seconds/20 if timed else (model.level+1)/3.0),60,Color(ink,.7),2,true)
	for i in range(model.shot_limit() if timed and model.phase!="build" else 3):draw_circle(Vector2(cx-12+i*12,size.y-12),3,Color("ffe299") if i<model.shot_limit()-model.shots else Color("315c64"))
