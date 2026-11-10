extends Control
const Art=preload("res://naval_battle/ship_art.gd")
const FLIGHT:=.95
signal selected(cell: int)
signal positioned(index: int, cell: int, horizontal: bool)
signal rotated(index: int)
var model
var own := true
var ship_index := 0
var horizontal := true
var target := -1
var hover := -1
var dragging := false
var dock_dragging := false
var pressed_cell := -1
var drag_ship := -1
var finger := -1
var touched_at := 0
var flashes: Array = []
var clock := 0.0
var reduced := false
var locked:=false
var salvo:Dictionary={}
var masked_cells:Array=[]
var drag_anchor:=0
var drag_horizontal:=true
var drag_cursor:=Vector2.ZERO
const AQUA := Color("69e5e5")
const PINK := Color("ff6c8d")

func _ready() -> void:
	mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	resized.connect(queue_redraw)

func grid() -> Rect2:
	var side := maxf(10,minf(size.x-24,size.y-24))
	return Rect2((size-Vector2.ONE*side)/2+Vector2(6,6),Vector2.ONE*side)

func cell_at(point: Vector2) -> int:
	var r := grid()
	if not r.has_point(point): return -1
	var n := (point-r.position)/r.size.x*10
	return clampi(int(n.y),0,9)*10+clampi(int(n.x),0,9)

func begin_dock_drag(index:int,event:InputEvent) -> void:
	if model.phase!="setup" or model.own_ready or not model.ships[index].is_empty():return
	if event is InputEventScreenTouch and event.pressed:
		dock_dragging=true;dragging=true;drag_ship=index;ship_index=index;drag_anchor=0;drag_horizontal=horizontal;drag_cursor=get_global_transform_with_canvas().affine_inverse()*event.position;finger=event.index;pressed_cell=-1
	elif event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT and event.pressed:
		dock_dragging=true;dragging=true;drag_ship=index;ship_index=index;drag_anchor=0;drag_horizontal=horizontal;drag_cursor=get_global_transform_with_canvas().affine_inverse()*event.position;pressed_cell=-1

func _input(event:InputEvent) -> void:
	if not dock_dragging:return
	if (event is InputEventScreenDrag or event is InputEventScreenTouch) and event.index!=finger:return
	if event is InputEventScreenDrag or event is InputEventMouseMotion:
		drag_cursor=get_global_transform_with_canvas().affine_inverse()*event.position;hover=cell_at(drag_cursor);queue_redraw()
	elif (event is InputEventScreenTouch and not event.pressed) or (event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT and not event.pressed):
		var cell:=cell_at(get_global_transform_with_canvas().affine_inverse()*event.position)
		if cell>=0 and not(event is InputEventScreenTouch and event.canceled):positioned.emit(drag_ship,cell-drag_anchor,drag_horizontal)
		dock_dragging=false;dragging=false;finger=-1;hover=-1;queue_redraw()

func _notification(what:int) -> void:
	if what==NOTIFICATION_APPLICATION_FOCUS_OUT:
		dock_dragging=false;dragging=false;finger=-1;hover=-1

func _gui_input(event: InputEvent) -> void:
	if locked:return
	var point := Vector2.ZERO
	var down := false; var up := false; var moving := false
	if event is InputEventScreenTouch:
		if event.canceled:
			dragging=false;finger=-1;hover=-1;queue_redraw();return
		if event.pressed and finger != -1: return
		if not event.pressed and event.index != finger: return
		touched_at = Time.get_ticks_msec()
		point = event.position
		down = event.pressed; up = not event.pressed
		finger = event.index if down else -1
	elif event is InputEventScreenDrag:
		if event.index != finger: return
		point = event.position; moving = true
	elif event is InputEventMouseButton:
		if Time.get_ticks_msec()-touched_at < 500 or event.button_index != MOUSE_BUTTON_LEFT: return
		point = event.position; down = event.pressed; up = not event.pressed
	elif event is InputEventMouseMotion:
		if Time.get_ticks_msec()-touched_at < 500: return
		point = event.position; moving = true
	else: return
	var cell := cell_at(point)
	if down:
		pressed_cell = cell; dragging = true; drag_ship = ship_index;drag_anchor=0;drag_horizontal=horizontal
		if own and model.phase == "setup":
			for i in range(5):
				if cell in model.ships[i]:
					drag_ship=i;ship_index=i;drag_anchor=cell-int(model.ships[i][0]);drag_horizontal=int(model.ships[i][1])-int(model.ships[i][0])==1;break
	if moving and dragging: hover = cell;drag_cursor=point
	if up and dragging:
		if cell >= 0:
			if own and model.phase == "setup" and not model.own_ready:
				if cell == pressed_cell and cell in model.ships[drag_ship]: rotated.emit(drag_ship)
				else: positioned.emit(drag_ship,cell-drag_anchor,drag_horizontal)
			elif not own: target = cell; selected.emit(cell)
		dragging = false; hover = -1
	accept_event(); queue_redraw()

func flash(cell: int, hit: bool) -> void:
	flashes.append({"cell":cell,"hit":hit,"age":0.0})
	if flashes.size()>8: flashes.pop_front()

func _process(delta: float) -> void:
	if reduced: flashes.clear();queue_redraw();return
	clock += delta
	for item in flashes: item.age += delta
	flashes = flashes.filter(func(item): return item.age < 1.4)
	queue_redraw()

func mark_values() -> Dictionary:
	var marks:Dictionary=(model.incoming if own else model.shots).duplicate()
	for cell in masked_cells:marks.erase(str(int(cell)))
	return marks

func kind_for_wreck(ship:Array,used:Dictionary) -> int:
	for i in range(5):
		if not used.has(i) and Art.LENGTHS[i]==ship.size():used[i]=true;return i
	return 2

func ship_art(cells:Array,kind:int,red:bool=false,ghost:bool=false) -> void:
	if cells.is_empty():return
	var r:=grid();var step:=r.size.x/10
	var first:int=int(cells[0]);var vertical:bool=cells.size()>1 and int(cells[1])-first==10
	var tint_red:=red
	if red and not reduced and not salvo.is_empty() and salvo.get("impacted",false) and bool(salvo.own)==own and int(salvo.cell) in cells:
		var age:float=float(salvo.age)-FLIGHT
		if age<1.0:tint_red=int(age/.17)%2==0
	var point:=r.position+Vector2(first%10,first/10)*step
	Art.vessel(self,point,step,kind,vertical,tint_red,ghost or (model.phase=="setup" and kind==ship_index),.45 if ghost else 1.0)

func flight_point(part:float) -> Vector2:
	var r:=grid();var n:int=int(salvo.cell)
	var destination:=r.position+(Vector2(n%10,n/10)+Vector2.ONE*.5)*r.size.x/10
	var start:=r.position+Vector2(r.size.x*.08,r.size.y*.98) if not bool(salvo.own) else r.position+Vector2(r.size.x*.94,r.size.y*.04)
	# Own=true in an event means the board defending the attack, not the shooter.
	return start.lerp(destination,part)-Vector2(0,sin(PI*part)*clampf(start.distance_to(destination)*.14,8,36))

func combat_art() -> void:
	if salvo.is_empty():return
	var age:float=salvo.age;var flight:float=.12 if reduced else FLIGHT
	var r:=grid();var unit:=r.size.x/10
	var n:int=int(salvo.cell);var point:=r.position+(Vector2(n%10,n/10)+Vector2.ONE*.5)*unit
	if age<flight:
		if reduced:return
		var part:=clampf(age/flight,0,1)
		for i in range(12,0,-1):
			var a:=flight_point(maxf(0,part-i*.014));var b:=flight_point(maxf(0,part-(i-1)*.014))
			draw_line(a,b,Color(1,.64,.28,.6*(1-i/13.0)),maxf(1,4-i*.25),true)
		var p:=flight_point(part);var tangent:Vector2=(flight_point(minf(1,part+.012))-flight_point(maxf(0,part-.012))).normalized()
		draw_circle(p,10,Color(1,.64,.2,.1));draw_circle(p,6,Color(1,.76,.33,.28))
		draw_line(p-tangent*5,p+tangent*4,Color("fff0a9"),5,true)
		draw_circle(p-tangent*6,2,Color("ff875b"))
		return
	if not salvo.get("impacted",false):
		draw_arc(point,unit*.32,clock*2,clock*2+TAU*.72,28,AQUA,2,true);return
	var t:=age-flight
	var hit:bool=salvo.get("hit",false)
	if t<1.1:
		var fade:=1-clampf(t/1.1,0,1)
		if reduced:
			draw_arc(point,unit*.6,0,TAU,24,Color(PINK if hit else AQUA,fade),2,true);return
		if hit:
			for i in range(20):
				var a:=i*2.39996;var radius:=unit*(.25+t*(1.2+fmod(i*.71,1.8)))
				var p:=point+Vector2(cos(a),sin(a))*radius+Vector2(0,-t*t*unit*.5)
				draw_circle(p,maxf(.7,unit*(.15-t*.1)),Color(Color("ffd487") if i%3==0 else Color("ff7549"),fade))
			for i in range(7):
				var a:=i*TAU/7;var p:=point+Vector2(cos(a),sin(a))*unit*t*.7-Vector2(0,t*unit*.4)
				draw_circle(p,unit*(.16+t*.38),Color(.1,.13,.19,fade*.58))
			draw_circle(point,unit*maxf(0,.56-t*1.4),Color("fff2bb",fade))
			draw_arc(point,unit*(.4+t*1.7),0,TAU,40,Color("ffb273",fade*.8),2,true)
		else:
			for i in range(3):draw_arc(point,unit*(.18+t*(1+i*.35)),0,TAU,40,Color("a5eff5",fade*.7/(i+1)),1.8,true)
			for i in range(12):
				var a:=i*TAU/12;var p:=point+Vector2(cos(a),sin(a))*unit*(.2+t*1.5)-Vector2(0,sin(minf(1,t)*PI)*unit*.8)
				draw_line(p,p+Vector2(0,unit*.12),Color("b8f6fc",fade),2,true)

func _draw() -> void:
	if model == null: return
	var r := grid(); var step := r.size.x/10
	var frame := StyleBoxFlat.new(); frame.bg_color = Color("081c2b"); frame.border_color = Color("267588"); frame.set_border_width_all(2); frame.set_corner_radius_all(12)
	draw_style_box(frame,r.grow(3))
	for stripe in range(64):
		var band:=Rect2(r.position+Vector2(0,r.size.y*stripe/64),Vector2(r.size.x,r.size.y/64+1))
		draw_rect(band,Color("123b50").lerp(Color("071723"),float(stripe)/64))
	for row in range(10):
		for col in range(10):
			var cell := Rect2(r.position+Vector2(col,row)*step,Vector2.ONE*step)
			var light := .045 + .014*sin(clock*.5+row*.5+col*.2)
			draw_rect(cell,Color(light,.12+light,.17+light,.22),true)

	for i in range(11):
		draw_line(r.position+Vector2(i*step,0),r.position+Vector2(i*step,r.size.y),Color("2b5165"),.85,true)
		draw_line(r.position+Vector2(0,i*step),r.position+Vector2(r.size.x,i*step),Color("2b5165"),.85,true)
	if model.phase=="battle" and not reduced:
		var scan:=fmod(clock*.09,1.0)*r.size.y
		draw_line(r.position+Vector2(0,scan),r.position+Vector2(r.size.x,scan),Color(.3,.9,.9,.08),2)
	var font := ThemeDB.fallback_font
	for i in range(10):
		var ink := Color("7bafc3")
		draw_string(font,r.position+Vector2((i+.5)*step-4,-6),char(65+i),HORIZONTAL_ALIGNMENT_LEFT,-1,10,ink)
		draw_string(font,r.position+Vector2(-17,(i+.65)*step),str(i+1),HORIZONTAL_ALIGNMENT_LEFT,-1,10,ink)
	var marks:Dictionary=mark_values()
	var wreck_cells:=[]
	if own:
		for i in range(5):
			var ship:Array=model.ships[i]
			var red:bool=not ship.is_empty() and ship.all(func(n):return marks.get(str(int(n)))=="hit")
			ship_art(ship,i,red)
			if red:wreck_cells.append_array(ship)
	else:
		var used:Dictionary={}
		for ship in model.wrecks:
			var kind:=kind_for_wreck(ship,used)
			if ship.all(func(n):return marks.get(str(int(n)))=="hit"):
				ship_art(ship,kind,true);wreck_cells.append_array(ship)
		if model.phase=="finished" and not model.friend and masked_cells.is_empty():
			for i in range(model.opponent.size()):
				if not model.opponent[i].all(func(n):return n in wreck_cells):ship_art(model.opponent[i],i)

	for key in marks:
		var index := int(key)
		if index in wreck_cells:continue
		var p := r.position+(Vector2(index%10,index/10)+Vector2.ONE*.5)*step
		match marks[key]:
			"hit":
				draw_circle(p,step*.24,Color("ff6c8d33"));draw_line(p-Vector2.ONE*step*.16,p+Vector2.ONE*step*.16,PINK,2)
				draw_line(p+Vector2(-1,1)*step*.16,p+Vector2(1,-1)*step*.16,PINK,2)
			"miss": draw_arc(p,step*.13,0,TAU,16,Color("749aa7"),1.5)
			"pending": draw_arc(p,step*.25,clock,clock+TAU*.7,16,AQUA,2)
	if not own and target>=0 and not model.shots.has(str(target)):
		var center := r.position+(Vector2(target%10,target/10)+Vector2.ONE*.5)*step
		draw_arc(center,step*.38,0,TAU,24,AQUA,1.5)
		for d in [Vector2.LEFT,Vector2.RIGHT,Vector2.UP,Vector2.DOWN]: draw_line(center+d*step*.22,center+d*step*.52,AQUA,1.5)
	if dragging and hover>=0 and own and model.phase=="setup":
		var cells:Array=model.Rules.place_ship([],hover-drag_anchor,model.LENGTHS[drag_ship],drag_horizontal)
		var used:=[]
		for i in range(5):
			if i!=drag_ship:used.append_array(model.ships[i])
		var legal:bool=not cells.is_empty() and cells.all(func(n):return n not in used)
		ship_art(cells,drag_ship,not legal,true)
	elif dock_dragging:
		Art.vessel(self,drag_cursor-Vector2(model.LENGTHS[drag_ship]*step*.5,step*.5),step,drag_ship,false,false,true,.7)
	for item in flashes:
		var p: Vector2 = r.position+(Vector2(int(item.cell)%10,int(item.cell)/10)+Vector2.ONE*.5)*step
		var color := PINK if item.hit else AQUA; color.a = 1-item.age/1.4
		draw_arc(p,step*(.25+item.age),0,TAU,32,color,2)

	combat_art()
