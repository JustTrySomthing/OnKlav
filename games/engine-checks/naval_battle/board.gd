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
var mouse_owned := false
var redraw_age := 0.0
var clock := 0.0
var settled_ship := -1
var settle_age := 1.0
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
	resized.connect(cancel_pointer)
	visibility_changed.connect(cancel_pointer)

func grid() -> Rect2:
	var side := maxf(10,minf(size.x-24,size.y-24))
	return Rect2((size-Vector2.ONE*side)/2+Vector2(6,6),Vector2.ONE*side)

func cell_at(point: Vector2) -> int:
	var r := grid()
	if not r.has_point(point): return -1
	var n := (point-r.position)/r.size.x*10
	return clampi(int(n.y),0,9)*10+clampi(int(n.x),0,9)

func begin_dock_drag(index:int,event:InputEvent) -> void:
	if locked or finger>=0 or model.phase!="setup" or model.own_ready or not model.ships[index].is_empty():return
	if event is InputEventScreenTouch and event.pressed:
		cancel_pointer()
		finger=event.index
	elif event is InputEventMouseButton and event.device!=InputEvent.DEVICE_ID_EMULATION and event.button_index==MOUSE_BUTTON_LEFT and event.pressed and not dragging:
		mouse_owned=true
	else:return
	dock_dragging=true;dragging=true;drag_ship=index;ship_index=index;drag_anchor=0;drag_horizontal=horizontal
	drag_cursor=get_global_transform_with_canvas().affine_inverse()*event.position

func _input(event:InputEvent) -> void:
	if not dock_dragging:return
	if locked:cancel_pointer();return
	if (event is InputEventScreenDrag or event is InputEventScreenTouch) and event.index!=finger:return
	if event is InputEventMouse and (not mouse_owned or event.device==InputEvent.DEVICE_ID_EMULATION):return
	if event is InputEventScreenDrag or event is InputEventMouseMotion:
		drag_cursor=get_global_transform_with_canvas().affine_inverse()*event.position;hover=cell_at(drag_cursor);queue_redraw()
	elif (event is InputEventScreenTouch and not event.pressed) or (event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT and not event.pressed):
		var cell:=cell_at(get_global_transform_with_canvas().affine_inverse()*event.position)
		var index:=drag_ship;var direction:=drag_horizontal
		cancel_pointer()
		if cell>=0 and not(event is InputEventScreenTouch and event.canceled):positioned.emit(index,cell,direction)
		get_viewport().set_input_as_handled()

func cancel_pointer() -> void:
	dock_dragging=false;dragging=false;finger=-1;mouse_owned=false;hover=-1;pressed_cell=-1
	queue_redraw()

func _notification(what:int) -> void:
	if what in [NOTIFICATION_APPLICATION_FOCUS_OUT,NOTIFICATION_PAUSED]:cancel_pointer()

func _gui_input(event: InputEvent) -> void:
	if locked:return
	var point := Vector2.ZERO
	var down := false; var up := false; var moving := false
	if event is InputEventScreenTouch:
		if event.canceled:
			if event.index==finger:cancel_pointer()
			return
		if event.pressed and finger != -1: return
		if not event.pressed and event.index != finger: return
		if event.pressed:cancel_pointer()
		point = event.position
		down = event.pressed; up = not event.pressed
		finger = event.index if down else -1
	elif event is InputEventScreenDrag:
		if event.index != finger: return
		point = event.position; moving = true
	elif event is InputEventMouseButton:
		if finger>=0 or event.device==InputEvent.DEVICE_ID_EMULATION or event.button_index!=MOUSE_BUTTON_LEFT:return
		if not event.pressed and not mouse_owned:return
		mouse_owned=event.pressed
		point = event.position; down = event.pressed; up = not event.pressed
	elif event is InputEventMouseMotion:
		if not mouse_owned or event.device==InputEvent.DEVICE_ID_EMULATION:return
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

func settle_ship(kind: int) -> void:
	settled_ship = kind
	settle_age = 0.0
	queue_redraw()

func _process(delta: float) -> void:
	settle_age = minf(1, settle_age + delta)
	if reduced:return
	clock += delta
	redraw_age += delta
	# The sea and smoke can update less often than targeting and shell flight.
	var animating := (not salvo.is_empty() and float(salvo.age)<salvo_flight()+2.1) or settle_age<1.0
	if animating or redraw_age>=1.0/24.0:
		redraw_age=0.0
		queue_redraw()

func mark_values() -> Dictionary:
	var marks:Dictionary=(model.incoming if own else model.shots).duplicate()
	for cell in masked_cells:marks.erase(str(int(cell)))
	return marks

func kind_for_wreck(ship:Array,used:Dictionary) -> int:
	for i in range(5):
		if not used.has(i) and Art.LENGTHS[i]==ship.size():used[i]=true;return i
	return 2

func ship_art(cells:Array,kind:int,red:bool=false,ghost:bool=false,marks:Dictionary={}) -> void:
	if cells.is_empty():return
	var r:=grid();var step:=r.size.x/10
	var first:int=int(cells[0]);var vertical:bool=cells.size()>1 and int(cells[1])-first==10
	var sink := 1.0 if red and not ghost else 0.0
	if red and not reduced and salvo.get("impacted",false) and bool(salvo.own)==own and int(salvo.cell) in cells:
		sink = smoothstep(.16, 2.1 if salvo.get("final",false) else 1.48, maxf(0.0, float(salvo.age)-salvo_flight()))
	var point := r.position + Vector2(first%10,first/10)*step
	var settling := 1.0-settle_age if not reduced and not ghost and kind==settled_ship and model.phase=="setup" else 0.0
	if settling>0:
		var center := point + (Vector2(.5,cells.size()*.5) if vertical else Vector2(cells.size()*.5,.5))*step
		draw_arc(center,step*(.38+(1.0-settling)*1.2),0,TAU,28,Color(AQUA,settling*.7),1.4,true)
		point.y -= settling*settling*step*.12
	var damage: Array[int] = []
	for i in range(cells.size()):
		if marks.get(str(int(cells[i])))=="hit":damage.append(i)
	var smoke := clock+float(first)*.19 if not reduced and sink<.94 else -1.0
	Art.vessel(self,point,step,kind,vertical,red,ghost or (model.phase=="setup" and kind==ship_index),.62 if ghost else 1.0,sink,damage,smoke)

func flight_point(part:float) -> Vector2:
	var r:=grid();var n:int=int(salvo.cell)
	var destination:=r.position+(Vector2(n%10,n/10)+Vector2.ONE*.5)*r.size.x/10
	var start:=r.position+Vector2(r.size.x*.08,r.size.y*.98) if not bool(salvo.own) else r.position+Vector2(r.size.x*.94,r.size.y*.04)
	# Own=true in an event means the board defending the attack, not the shooter.
	return start.lerp(destination,part)-Vector2(0,sin(PI*part)*clampf(start.distance_to(destination)*.14,8,36))

func salvo_flight() -> float:
	return float(salvo.get("flight",FLIGHT))

func combat_art() -> void:
	if salvo.is_empty():return
	var age:float=salvo.age;var flight:=salvo_flight()
	var r:=grid();var unit:=r.size.x/10
	var n:int=int(salvo.cell);var point:=r.position+(Vector2(n%10,n/10)+Vector2.ONE*.5)*unit
	if age<flight:
		if reduced:return
		var part:=clampf(age/flight,0,1)
		var muzzle := flight_point(0)
		if part<.25:
			var burst := 1.0-part/.25
			draw_circle(muzzle,unit*(.22+part*2),Color("ffba6330"))
			draw_circle(muzzle,unit*.17*burst,Color("ffe5af",burst))
		for i in range(18,0,-1):
			var a := flight_point(maxf(0,part-i*.014))
			var b := flight_point(maxf(0,part-(i-1)*.014))
			if a.distance_squared_to(b)<.04:continue
			var intensity := 1.0-i/19.0
			draw_line(a,b,Color("ff9343",intensity*.38),1+unit*.11*intensity,true)
			draw_line(a,b,Color("ffe6b4",intensity*.68),maxf(1,unit*.05*intensity),true)
		var p := flight_point(part)
		var tangent := (flight_point(minf(1,part+.012))-flight_point(maxf(0,part-.012))).normalized()
		draw_circle(p,unit*.29,Color("ffb86430"))
		draw_line(p-tangent*unit*.14,p+tangent*unit*.12,Color("fff0c1"),maxf(2,unit*.13),true)

		return
	if not salvo.get("impacted",false):
		draw_arc(point,unit*.32,0 if reduced else clock*2,TAU*.72 if reduced else clock*2+TAU*.72,28,AQUA,2,true);return
	var t:=age-flight
	var hit:bool=salvo.get("hit",false)
	if t<1.35:
		var fade:=1-clampf(t/1.35,0,1)
		if reduced:
			return
		if hit:
			draw_circle(point,unit*(.24+t*.66),Color("ff8046",fade*.26))
			if not salvo.get("proof",[]).is_empty():
				draw_arc(point,unit*(.6+t*2.2),0,TAU,38,Color("ffc184",fade*.55),2,true)
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
			var lift := sin(minf(1.0,t*2.1)*PI)
			if lift>0:
				var plume := PackedVector2Array([point+Vector2(-.34,.1)*unit,
					point+Vector2(-.22,-lift*.48)*unit,point+Vector2(-.04,-lift*1.13)*unit,
					point+Vector2(.12,-lift*.73)*unit,point+Vector2(.34,.1)*unit])
				draw_polygon(plume,PackedColorArray([Color("9dd3e4",fade*.30),Color("c7e9ef",fade*.58),
					Color("f4ffff",fade*.92),Color("d6eff1",fade*.68),Color("9dd3e4",fade*.30)]))
			for i in range(3):draw_arc(point,unit*(.18+t*(1+i*.35)),0,TAU,40,Color("a5eff5",fade*.7/(i+1)),1.8,true)
			for i in range(12):
				var a:=i*TAU/12;var p:=point+Vector2(cos(a),sin(a))*unit*(.2+t*1.5)-Vector2(0,sin(minf(1,t)*PI)*unit*.8)
				draw_line(p,p+Vector2(0,unit*.12),Color("b8f6fc",fade),2,true)

func ocean(r: Rect2, step: float) -> void:
	var pale := Color("236373") if own else Color("353c59")
	var deep := Color("071e30") if own else Color("11182d")
	var glint := Color("a7ead7") if own else Color("e4a990")
	var corners := PackedVector2Array([r.position,Vector2(r.end.x,r.position.y),r.end,Vector2(r.position.x,r.end.y)])
	draw_rect(r.grow(7),Color("030c17"),true)
	draw_rect(r.grow(5),Color("416979") if own else Color("795f66"),false,1.6)
	draw_polygon(corners,PackedColorArray([pale,pale.darkened(.42),deep.darkened(.25),deep]))
	var strength := grid_strength()
	for i in range(1,10):
		var ink := Color(glint,(.36 if i==5 else .24)*strength)
		draw_line(r.position+Vector2(i*step,0),r.position+Vector2(i*step,r.size.y),ink,1.0,true)
		draw_line(r.position+Vector2(0,i*step),r.position+Vector2(r.size.x,i*step),ink,1.0,true)
	draw_rect(r,Color(glint,.52),false,1.3,true)
	for i in range(4):
		var edge := corners[i]
		draw_line(edge,edge.lerp(corners[(i+1)%4],.065),Color(glint,.85),2,true)
		draw_line(edge,edge.lerp(corners[(i+3)%4],.065),Color(glint,.85),2,true)

func grid_strength() -> float:
	if reduced:return 1.0
	if salvo.get("final",false) and salvo.get("impacted",false):
		return lerpf(1.0,.55,smoothstep(salvo_flight(),salvo_flight()+1.25,float(salvo.age)))
	return .55 if model.phase=="finished" and salvo.is_empty() else 1.0

func _draw() -> void:
	if model == null: return
	var r := grid()
	var step := r.size.x/10
	ocean(r,step)
	var font := ThemeDB.fallback_font
	var coordinate_ink := Color(Color("afcfd7") if own else Color("c8b8b3"),lerpf(.52,1.0,grid_strength()))
	for i in range(10):
		draw_string(font,r.position+Vector2((i+.5)*step-4,-6),char(65+i),HORIZONTAL_ALIGNMENT_LEFT,-1,10,coordinate_ink)
		draw_string(font,r.position+Vector2(-17,(i+.65)*step),str(i+1),HORIZONTAL_ALIGNMENT_LEFT,-1,10,coordinate_ink)
	var marks:Dictionary=mark_values()
	var wreck_cells:=[]
	var visible_hulls:=[]
	if own:
		for i in range(5):
			var ship:Array=model.ships[i]
			var red:bool=not ship.is_empty() and ship.all(func(n):return marks.get(str(int(n)))=="hit")
			ship_art(ship,i,red,false,marks)
			visible_hulls.append_array(ship)
			if red:wreck_cells.append_array(ship)
	else:
		var used:Dictionary={}
		for ship in model.wrecks:
			var kind:=kind_for_wreck(ship,used)
			if ship.all(func(n):return marks.get(str(int(n)))=="hit"):
				ship_art(ship,kind,true,false,marks);wreck_cells.append_array(ship)
		if model.phase=="finished" and not model.friend and masked_cells.is_empty() and salvo.is_empty():
			for i in range(model.opponent.size()):
				if not model.opponent[i].all(func(n):return n in wreck_cells):
					ship_art(model.opponent[i],i,false,false,marks)
					visible_hulls.append_array(model.opponent[i])

	for key in marks:
		var index := int(key)
		if index in wreck_cells:continue
		var p := r.position+(Vector2(index%10,index/10)+Vector2.ONE*.5)*step
		match marks[key]:
			"hit":
				if index in visible_hulls:continue
				draw_circle(p,step*.25,Color("10161cd9"))
				var arm := step*.15
				var ember := Color("ffbf84")
				draw_line(p-Vector2.ONE*arm,p+Vector2.ONE*arm,ember,2,true)
				draw_line(p+Vector2(-1,1)*arm,p+Vector2(1,-1)*arm,ember,2,true)
			"miss":
				draw_arc(p,step*.17,0,TAU,24,Color("b5e0ee"),1.5,true)
				draw_circle(p,maxf(1,step*.041),Color("b5e0ee"))
			"pending":
				var angle:=0.0 if reduced else clock
				draw_arc(p,step*.25,angle,angle+TAU*.7,16,AQUA,2)
	if not own and target>=0 and not model.shots.has(str(target)):
		var center := r.position+(Vector2(target%10,target/10)+Vector2.ONE*.5)*step
		var cell := Rect2(center-Vector2.ONE*step*.47,Vector2.ONE*step*.94)
		draw_rect(cell,Color(AQUA,.14))
		draw_rect(cell,Color(AQUA,.66),false,1.4)
		draw_arc(center,step*.3,0,TAU,32,AQUA,1.5,true)
		for d in [Vector2.LEFT,Vector2.RIGHT,Vector2.UP,Vector2.DOWN]: draw_line(center+d*step*.22,center+d*step*.52,AQUA,1.5)
	if dragging and hover>=0 and own and model.phase=="setup":
		var anchor := hover-drag_anchor
		var cells:Array=model.Rules.place_ship([],anchor,model.LENGTHS[drag_ship],drag_horizontal)
		var used:=[]
		for i in range(5):
			if i!=drag_ship:used.append_array(model.ships[i])
		var legal:bool=not cells.is_empty() and cells.all(func(n):return n not in used)
		var ink := AQUA if legal else PINK
		var start := Vector2i(anchor%10,anchor/10)
		for i in range(model.LENGTHS[drag_ship]):
			var coord := start+Vector2i(i,0) if drag_horizontal else start+Vector2i(0,i)
			if anchor<0 or coord.x<0 or coord.x>=10 or coord.y<0 or coord.y>=10:continue
			var footprint := Rect2(r.position+Vector2(coord)*step,Vector2.ONE*step).grow(-1)
			draw_rect(footprint,Color(ink,.21))
			draw_rect(footprint,Color(ink,.88),false,1.5)
		if cells.is_empty():
			var invalid := Rect2(r.position+Vector2(hover%10,hover/10)*step,Vector2.ONE*step).grow(-1)
			draw_rect(invalid,Color(PINK,.32));draw_rect(invalid,PINK,false,1.5)
		else:ship_art(cells,drag_ship,not legal,true)
	elif dock_dragging:
		var span := Vector2(step,model.LENGTHS[drag_ship]*step) if not drag_horizontal else Vector2(model.LENGTHS[drag_ship]*step,step)
		Art.vessel(self,drag_cursor-span*.5,step,drag_ship,not drag_horizontal,false,true,.7)
	combat_art()
