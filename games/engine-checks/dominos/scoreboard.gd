extends Control
const Art=preload("res://dominos/art.gd")
var scores:Array=[0,0]
var totals:Array=[0,0]
var gain_side:=-1
var gain_points:=0
var gain_age:=2.0
var shown:Array=[0.0,0.0]
var turn:=0
var round_id:=1
var phase:="playing"
var clock:=0.0
var reduced:=false
func _ready():mouse_filter=Control.MOUSE_FILTER_IGNORE
func reset(values:Array):
	shown=[float(values[0]),float(values[1])]
	gain_side=-1

func show_gain(side:int,points:int):
	gain_side=side
	gain_points=points
	gain_age=0

func _process(dt:float):
	gain_age+=dt
	if not reduced:clock+=dt
	for i in range(2):shown[i]=scores[i] if reduced else move_toward(shown[i],scores[i],maxf(10,absf(shown[i]-scores[i])*6)*dt)
	queue_redraw()
func right_text(value: String, at: Vector2, font_size: int, ink: Color) -> void:
	var width := ThemeDB.fallback_font.get_string_size(value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
	Art.text(self,value,at-Vector2(width,0),font_size,ink)

func _draw():
	Art.text(self,"ROUND SCORE",Vector2(2,15),14,Art.CYAN)
	right_text("ROUND %d / 3"%round_id,Vector2(size.x,15),13,Color("c7d6eb"))
	var w := (size.x-10)*.5
	for i in range(2):
		var ink := Art.CYAN if i==0 else Art.PINK
		var r := Rect2(i*(w+10),23,w,52)
		var active := turn==i and phase=="playing"
		Art.panel(self,r,Color("112438") if i==0 else Color("251b38"),Color(ink,.9 if active else .45),13)
		if active: Art.rail(self,r.grow(-2),clock*.65,reduced)
		Art.text(self,"YOU" if i==0 else "RIVAL",r.position+Vector2(10,17),12,ink)
		right_text("MATCH TOTAL",r.position+Vector2(w-10,17),12,Color("c7d6eb"))
		Art.text(self,str(roundi(shown[i])),r.position+Vector2(10,43),25,Color("f4f7ff"))
		right_text(str(totals[i]),r.position+Vector2(w-10,43),22,Color("f4f7ff"))
		if gain_side==i and gain_age<1.1:
			var lift := 0.0 if reduced else minf(4,gain_age*12)
			var opacity := 1.0 if reduced else minf(1.0,(1.1-gain_age)*4)
			Art.text(self,"+%d"%gain_points,r.position+Vector2(57,39-lift),12,Color(Art.GOLD,opacity))
