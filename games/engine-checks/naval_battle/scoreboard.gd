extends Control
const Art=preload("res://naval_battle/ship_art.gd")
const Chrome=preload("res://game_chrome.gd")
var model
var your_marks:Dictionary={}
var enemy_marks:Dictionary={}
var title:="FLEET DEPLOYMENT"
var setup:=true
var incoming:=false
func _ready() -> void:
	custom_minimum_size=Vector2(0,76)
	mouse_filter=Control.MOUSE_FILTER_IGNORE
	resized.connect(queue_redraw)
func text(value:String,point:Vector2,font_size:int,color:Color) -> void:
	draw_string(ThemeDB.fallback_font,point,value,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,color)
func side_card(rect:Rect2,yours:bool) -> void:
	var compact:bool=rect.size.y<80
	var marks:Dictionary=your_marks if yours else enemy_marks
	var tint:=Color("79efcf") if yours else Color("ffbe83")
	var values:Array=model.ships if yours else model.wrecks
	var destroyed:=0
	if yours:
		for ship in values:
			if not ship.is_empty() and ship.all(func(n):return marks.get(str(int(n)))=="hit"):destroyed+=1
	else:
		for ship in values:
			if ship.all(func(n):return marks.get(str(int(n)))=="hit"):destroyed+=1
	var hits:int=model.hit_count(marks)
	var ready_count:=0
	for ship in model.ships:
		if not ship.is_empty():ready_count+=1
	draw_style_box(Chrome.box("102d40" if yours else "192b3d","3d6975" if yours else "514b50",14),rect)
	draw_line(rect.position+Vector2(13,3),rect.position+Vector2(rect.size.x-13,3),Color(tint,.6),2,true)
	text("YOUR FLEET" if yours else "ENEMY FLEET",rect.position+Vector2(12,17 if compact else 20),10 if compact else 11,Color(tint,.9))
	var value:=str(ready_count)+" / 5" if setup and yours else "5 / 5" if setup else str(5-destroyed)+" / 5"
	text(value,rect.position+Vector2(12,41 if compact else 47),22 if compact else 25,Color("e8f7fa"))
	text("DEPLOYED" if setup and yours else "AWAITING" if setup else "AFLOAT",rect.position+Vector2(76 if compact else 84,39 if compact else 45),8 if compact else 9,Color("8faab8"))
	var bar:=Rect2(rect.position+Vector2(12,rect.size.y-13 if compact else 58),Vector2(rect.size.x-24,4))
	draw_rect(bar,Color("081a29"));draw_rect(Rect2(bar.position,Vector2(bar.size.x*(float(ready_count)/5 if setup and yours else 1 if setup else float(17-hits)/17),4)),tint)
	
func _draw() -> void:
	if model==null:return
	var width:float=(size.x-12)/2
	side_card(Rect2(0,0,width,size.y-2),true);side_card(Rect2(width+12,0,width,size.y-2),false)
