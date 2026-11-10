extends Control
const Art=preload("res://naval_battle/ship_art.gd")
signal chosen(index:int,event:InputEvent)
var index:=0
var selected:=false
func _ready() -> void:
	custom_minimum_size=Vector2(28,44)
	mouse_default_cursor_shape=Control.CURSOR_DRAG
	resized.connect(queue_redraw)
func _gui_input(event:InputEvent) -> void:
	if (event is InputEventScreenTouch and event.pressed) or (event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT and event.pressed):
		var screen_event=event.duplicate();screen_event.position=get_global_transform_with_canvas()*event.position
		chosen.emit(index,screen_event);accept_event()
func _draw() -> void:
	var unit:=minf(size.x/(Art.LENGTHS[index]+.4),22)
	var origin:=Vector2((size.x-Art.LENGTHS[index]*unit)*.5,12)
	Art.vessel(self,origin,unit,index,false,false,selected)
	if selected:draw_line(Vector2(size.x*.28,39),Vector2(size.x*.72,39),Color("79efcf"),2,true)
