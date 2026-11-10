extends Button
signal chosen(id:int)
signal drag_started(id:int,point:Vector2)
signal drag_moved(point:Vector2)
signal drag_finished(point:Vector2,canceled:bool)
const Art=preload("res://dominos/art.gd")
const Model=preload("res://dominos/model.gd")
var tile_id:=0
var selected:=false
var playable:=false
var concealed:=false
var arrival:=1.0
var reduced:=false
var fresh:=false
var pulse:=0.0
var finger:=-1
var down:=Vector2.ZERO
var held:=false
var dragging:=false
var pointer_time:=-1000
var touched_at:=-1000
func _ready():
	custom_minimum_size=Vector2(44,38);mouse_default_cursor_shape=Control.CURSOR_POINTING_HAND
	for style in ["normal","hover","pressed","focus"]:add_theme_stylebox_override(style,StyleBoxEmpty.new())
	var p:=Model.faces(tile_id);tooltip_text="%d | %d"%[p.x,p.y];accessibility_name=tooltip_text
	pressed.connect(func():
		if not held and Time.get_ticks_msec()-pointer_time>500:chosen.emit(tile_id))
func cancel_pointer():
	var was_dragging:=dragging;held=false;dragging=false;finger=-1
	if was_dragging:drag_finished.emit(Vector2.ZERO,true)
func _gui_input(event:InputEvent):
	if disabled:return
	var press:=false;var release:=false;var moving:=false;var p:=Vector2.ZERO;var index:=-2
	var now:=Time.get_ticks_msec()
	if event is InputEventScreenTouch:
		touched_at=now
		# WebView may deliver the emulated mouse press before its real touch.
		if event.pressed and held and finger==-2:held=false;dragging=false;finger=-1
		if event.canceled:cancel_pointer();return
		press=event.pressed;release=not press;p=event.position;index=event.index
	elif event is InputEventScreenDrag:touched_at=now;moving=true;p=event.position;index=event.index
	elif event is InputEventMouseButton:
		if now-touched_at<500 or event.button_index!=MOUSE_BUTTON_LEFT:return
		press=event.pressed;release=not press;p=event.position
	elif event is InputEventMouseMotion:
		if now-touched_at<500:return
		moving=true;p=event.position
	else:return
	if press:
		if held:return
		held=true;finger=index;down=get_global_transform_with_canvas()*p;pointer_time=now
	elif held and index==finger:
		var point:Vector2=get_global_transform_with_canvas()*p;pointer_time=now
		if moving:
			if not dragging and point.distance_to(down)>10:dragging=true;drag_started.emit(tile_id,point)
			if dragging:drag_moved.emit(point)
		elif release:
			var was_dragging:=dragging;held=false;dragging=false;finger=-1
			if was_dragging:drag_finished.emit(point,false)
			else:chosen.emit(tile_id)
func _process(dt:float):
	arrival=minf(1,arrival+dt*4);pulse+=dt;queue_redraw()
func _draw():
	if concealed or dragging:return
	var p:=Model.faces(tile_id)
	var lift:float=2 if selected else 0
	if not reduced:lift+=sin(clampf(arrival,0,1)*PI)*3
	var extent:=Vector2(size.x-6,minf(size.y-7,(size.x-6)*.53))
	var r:=Rect2((size-extent)*.5-Vector2(0,lift),extent)
	modulate=Color.WHITE if playable or fresh else Color(.68,.74,.84)
	modulate.a=1 if reduced else clampf(arrival*3,0,1)
	Art.tile(self,r,p.x,p.y,Art.GOLD if fresh else Art.CYAN,false,false,selected or playable or fresh or has_focus())
	if not reduced and (playable or fresh or selected):
		var head:=fposmod(pulse*25+tile_id*7,r.size.x+20)-20
		var start:=maxf(r.position.x+6,r.position.x+head);var end:=minf(r.end.x-6,r.position.x+head+16)
		if end>start:draw_line(Vector2(start,r.position.y+2),Vector2(end,r.position.y+2),Color(1,1,1,.65),1.5,true)
	if fresh:
		var alpha:float=.8 if reduced else .7+.3*sin(pulse*5)
		draw_line(r.position+Vector2(7,1),r.position+Vector2(r.size.x-7,1),Color(Art.GOLD,alpha),3,true)
	if selected:draw_circle(Vector2(size.x*.5,size.y-2),2.5,Art.CYAN)
