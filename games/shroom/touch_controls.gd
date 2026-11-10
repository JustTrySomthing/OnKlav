extends Control
## Opt-in gameplay input. Place inside the game's content area, below canonical chrome.
signal movement_changed(value:Vector2)
signal look_changed(delta:Vector2)
signal aim_changed(value:Vector2)
signal action_pressed(action:String)
signal action_released(action:String)
@export_enum("stick","dpad") var movement_style:="stick"
@export var dead_zone:=.15
@export var look_sensitivity:=2.5
@export var floating_stick:=true
@export var horizontal_only:=false
@export var enabled:=true:
	set(value):
		enabled=value
		if not value:reset_input()
@export_range(.7,1.5) var control_scale:=1.0
@export_range(.2,1.0) var opacity:=.8
@export var left_handed:=false
var dash_fill := 1.0:
	set(value):
		if is_equal_approx(value,dash_fill): return
		dash_fill=value
		queue_redraw()
var twin_stick:=false
var aim_vector:=Vector2.ZERO
var input_vector:=Vector2.ZERO
var actions:Array[String]=["jump","dash"]
var look_enabled:=false
var pointers:Dictionary={}
var held:Dictionary={}
var stick_owner:=-1
var look_owner:=-1
var stick_center:=Vector2.ZERO
var knob:=Vector2.ZERO
var last_touch:=-1000
const ACTION_COLORS := {"fire":Color("#ff6e68"),"dash":Color("#72dfb5"),"swap":Color("#8dbdff")}
var action_styles: Dictionary = {}

func _ready():
	for action in ACTION_COLORS:
		var accent: Color = ACTION_COLORS[action]
		var idle := StyleBoxFlat.new()
		idle.bg_color = accent.darkened(.76)
		idle.border_color = accent
		idle.set_border_width_all(2)
		idle.set_corner_radius_all(14)
		var active := idle.duplicate() as StyleBoxFlat
		active.bg_color = accent.darkened(.40)
		active.border_color = accent.lightened(.37)
		active.shadow_color = Color(accent,.44)
		active.shadow_size = 5
		action_styles[action] = [idle,active]
	mouse_filter=Control.MOUSE_FILTER_IGNORE;set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	resized.connect(reset_input);visibility_changed.connect(reset_input)
	if OS.has_feature("web"):
		get_viewport().size_changed.connect(update_action_unit)
		update_action_unit()
	var session=get_node_or_null("/root/OnKlavGame")
	if session!=null and session.has_signal("suspended"):session.suspended.connect(reset_input)
func configure(preset:String):
	twin_stick=false
	reset_input()
	if preset=="platformer":actions=["jump","dash"];look_enabled=false;horizontal_only=true
	elif preset=="firstperson":actions=["fire","jump","use","sprint"];look_enabled=true;horizontal_only=false
	elif preset=="topdown":actions=["attack","use","bag"];look_enabled=false;horizontal_only=false
	elif preset=="arena":actions=["attack","dash"];look_enabled=true;horizontal_only=false;twin_stick=true
	else:push_error("Unknown touch preset: "+preset)
	queue_redraw()
func is_held(action:String)->bool:return held.get(action,false)
func radius()->float:return clampf(minf(size.x,size.y)*.17*clampf(control_scale,.7,1.5),32,100)
func mirror(p:Vector2)->Vector2:return Vector2(size.x-p.x,p.y) if left_handed else p
func aim_home()->Vector2:return Vector2(size.x-radius()-22,size.y-radius()-22)
func home()->Vector2:return Vector2(radius()+22,size.y-radius()-22)
var css_unit := 1.0

func update_action_unit() -> void:
	var css_width := float(JavaScriptBridge.eval("window.innerWidth"))
	var unit := maxf(.1, css_width / maxf(1.0, get_viewport_rect().size.x))
	if is_equal_approx(unit, css_unit): return
	css_unit = unit
	reset_input()

func action_unit() -> float:
	if OS.has_feature("web"): return css_unit
	return maxf(.1, get_viewport().get_stretch_transform().x.length())

func button_rect(index:int)->Rect2:
	var unit := action_unit()
	var width := minf(132.0/unit*control_scale,size.x*.25)
	var height := minf(68.0/unit*control_scale,size.y*.30)
	var margin := minf(12.0/unit,minf(size.x,size.y)*.05)
	var column := index % 2
	var row := 1 if index >= 2 else 0
	return Rect2(size.x-margin-width-column*(width+margin*.65),
		size.y-margin-height-row*(height+margin*.65),width,height)

func button_hit_rect(index: int) -> Rect2:
	# Half the gutter keeps adjacent actions separate even at custom sizes.
	var margin := minf(12.0/action_unit(),minf(size.x,size.y)*.05)
	var gutter := margin*.325
	var left_column := index % 2 == 1
	var top_row := index >= 2
	return button_rect(index).grow_individual(
		margin if left_column else gutter,
		margin if top_row else gutter,
		gutter if left_column else margin,
		gutter if top_row else margin
	)
func reset_input():
	aim_vector=Vector2.ZERO;aim_changed.emit(aim_vector)
	pointers.clear();stick_owner=-1;look_owner=-1;knob=Vector2.ZERO
	if input_vector!=Vector2.ZERO:input_vector=Vector2.ZERO;movement_changed.emit(input_vector)
	for action in held.keys():
		if held[action]:action_released.emit(action)
	held.clear();queue_redraw()
func _notification(what:int):
	if what==NOTIFICATION_APPLICATION_FOCUS_OUT:reset_input()
func _input(event:InputEvent):
	# Godot also emits synthetic mouse input for touch to drive ordinary GUI buttons.
	# The combat overlay handles the original touch only.
	if event is InputEventMouse and event.device == InputEvent.DEVICE_ID_EMULATION: return
	if not enabled or not is_visible_in_tree():return
	var id:=-1000;var point:=Vector2.ZERO;var down:=false;var up:=false;var cancel:=false
	if event is InputEventScreenTouch:
		last_touch=Time.get_ticks_msec();id=event.index;point=event.position;down=event.pressed;up=not down;cancel=event.canceled
		if down and pointers.has(-1000):release(-1000)
	elif event is InputEventScreenDrag:last_touch=Time.get_ticks_msec();id=event.index;point=event.position
	elif event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT:
		if Time.get_ticks_msec()-last_touch<700 or pointers.keys().any(func(n):return n>=0):return
		point=event.position;down=event.pressed;up=not down
	elif event is InputEventMouseMotion:
		if not pointers.has(id) or Time.get_ticks_msec()-last_touch<700:return
		point=event.position
	else:return
	point=get_global_transform_with_canvas().affine_inverse()*point
	if feed(id,point,down,up,cancel):get_viewport().set_input_as_handled()
func feed(id:int,point:Vector2,down:bool=false,up:bool=false,cancel:bool=false)->bool:
	if not enabled:return false
	point=mirror(point)
	if cancel or up:
		if not pointers.has(id):return false
		release(id);return true
	if down:
		if pointers.has(id) or not Rect2(Vector2.ZERO,size).has_point(point):return false
		for i in range(actions.size()):
			if button_hit_rect(i).has_point(point):
				var action:String=actions[i]
				if is_held(action):return false
				pointers[id]={"role":action,"point":point};held[action]=true;action_pressed.emit(action);queue_redraw();return true
		if stick_owner==-1 and ((point-home()).length()<radius()*1.5 or (floating_stick and point.x<size.x*.45 and point.y>size.y*.45)):
			stick_owner=id;stick_center=point if floating_stick and movement_style!="dpad" else home();pointers[id]={"role":"move","point":point}
		elif look_enabled and look_owner==-1 and point.x>=size.x*.45:
			look_owner=id;pointers[id]={"role":"look","point":point}
		else:return false
	if not pointers.has(id):return false
	var role:String=pointers[id].role
	if role=="move":
		var raw:Vector2=((point-stick_center)/radius()).limit_length()
		if horizontal_only:raw.y=0
		if movement_style=="dpad":raw=Vector2(signf(raw.x) if absf(raw.x)>.25 else 0,signf(raw.y) if absf(raw.y)>.25 else 0).limit_length()
		elif raw.length()<dead_zone:raw=Vector2.ZERO
		else:raw=raw.normalized()*clampf((raw.length()-dead_zone)/maxf(.01,1-dead_zone),0,1)
		knob=raw*radius();input_vector=raw;movement_changed.emit(raw)
	elif role=="look":
		if twin_stick:
			aim_vector=((point-aim_home())/radius()).limit_length()
			if aim_vector.length()<dead_zone:aim_vector=Vector2.ZERO
			aim_changed.emit(aim_vector)
		else:look_changed.emit((point-Vector2(pointers[id].point))/maxf(1,minf(size.x,size.y))*look_sensitivity)
	pointers[id].point=point;queue_redraw();return true
func cancel_action(action: String) -> void:
	for id in pointers.keys():
		if pointers[id].role == action:
			release(id)
			return

func release(id:int):
	if not pointers.has(id):return
	var role:String=pointers[id].role;pointers.erase(id)
	if role=="move":stick_owner=-1;input_vector=Vector2.ZERO;knob=Vector2.ZERO;movement_changed.emit(input_vector)
	elif role=="look":look_owner=-1;aim_vector=Vector2.ZERO;aim_changed.emit(aim_vector)
	else:held.erase(role);action_released.emit(role)
	queue_redraw()
func settings()->Dictionary:return {"scale":control_scale,"opacity":opacity,"left_handed":left_handed,"sensitivity":look_sensitivity,"dpad":movement_style=="dpad"}
func apply_settings(d:Dictionary)->bool:
	for k in ["scale","opacity","sensitivity"]:
		if not (d.get(k) is int or d.get(k) is float) or not is_finite(d[k]):return false
	if d.scale<.7 or d.scale>1.5 or d.opacity<.2 or d.opacity>1 or d.sensitivity<.2 or d.sensitivity>8 or not d.get("left_handed") is bool or not d.get("dpad") is bool:return false
	reset_input();control_scale=d.scale;opacity=d.opacity;left_handed=d.left_handed;look_sensitivity=d.sensitivity;movement_style="dpad" if d.dpad else "stick";queue_redraw();return true
func _draw():
	modulate.a=opacity
	if left_handed:draw_set_transform(Vector2(size.x,0),0,Vector2(-1,1))
	if twin_stick:
		draw_circle(aim_home(),radius(),Color(.05,.1,.16,.45));draw_arc(aim_home(),radius(),0,TAU,48,Color(.8,.9,1,.65),2,true);draw_circle(aim_home()+aim_vector*radius()*.62,radius()*.35,Color(.7,.85,1,.65))
	var center:=home() if stick_owner==-1 else stick_center;var r:=radius()
	draw_circle(center,r,Color(.05,.1,.16,.45));draw_arc(center,r,0,TAU,48,Color(.8,.9,1,.65),2,true)
	if movement_style=="dpad":
		for d in [Vector2.LEFT,Vector2.RIGHT] if horizontal_only else [Vector2.LEFT,Vector2.RIGHT,Vector2.UP,Vector2.DOWN]:
			var p:Vector2=center+d*r*.6;var side:Vector2=d.orthogonal()*r*.15
			draw_colored_polygon(PackedVector2Array([p+d*r*.18,p-d*r*.12+side,p-d*r*.12-side]),Color(.85,.93,1,.8))
	else:draw_circle(center+knob*.62,r*.35,Color(.7,.85,1,.65))
	draw_set_transform(Vector2.ZERO,0,Vector2.ONE)
	for i in range(actions.size()):
		var box := button_rect(i)
		box.position.x=mirror(box.get_center()).x-box.size.x*.5
		var action := actions[i]
		var held_now := is_held(action)
		var accent: Color = ACTION_COLORS.get(action,Color("#a9eee2"))
		var styles: Array = action_styles.get(action,action_styles["dash"])
		draw_style_box(styles[int(held_now)],box)
		if action=="dash" and dash_fill<1:
			var meter := Rect2(box.position+Vector2(9,box.size.y-10),Vector2(box.size.x-18,4))
			draw_rect(meter,Color("#476065"))
			meter.size.x*=dash_fill
			draw_rect(meter,accent)
		var font := ThemeDB.fallback_font
		var label := action.to_upper()
		var unit := maxf(.1,get_viewport().get_stretch_transform().x.length())
		var font_size := mini(roundi(48/unit),mini(roundi(box.size.x*.29),roundi(box.size.y*.49)))
		var width := font.get_string_size(label,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
		var baseline := (font.get_ascent(font_size)-font.get_descent(font_size))*.5
		draw_string(font,box.get_center()+Vector2(-width*.5,baseline),label,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,Color.WHITE)

## Optional in-game preferences sheet. Caller snapshots settings with its game state.
func show_settings(parent: Control) -> void:
	if parent.has_node("TouchPreferences"):
		return
	var session = parent.get_node("/root/OnKlavGame")
	var was_paused: bool = session.paused
	session.pause_game()

	# Use the same logical coordinates as Shroom's HUD. The viewport already scales them.
	var shade := ColorRect.new()
	shade.name = "TouchPreferences"
	shade.color = Color(0, 0, 0, 0.68)
	shade.process_mode = Node.PROCESS_MODE_ALWAYS
	parent.add_child(shade)
	shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

	var panel := PanelContainer.new()
	shade.add_child(panel)
	panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
	var fit_panel := func():
		panel.size = Vector2(minf(560.0, shade.size.x - 28.0), minf(450.0, shade.size.y - 28.0))
		panel.position = (shade.size - panel.size) * 0.5
	shade.resized.connect(fit_panel)
	fit_panel.call()

	var surface := StyleBoxFlat.new()
	surface.bg_color = Color("#102638")
	surface.border_color = Color("#4e9ba9")
	surface.set_border_width_all(2)
	surface.set_corner_radius_all(14)
	for side in [SIDE_LEFT, SIDE_RIGHT, SIDE_TOP, SIDE_BOTTOM]:
		surface.set_content_margin(side, 18)
	panel.add_theme_stylebox_override("panel", surface)

	var column := VBoxContainer.new()
	column.add_theme_constant_override("separation", 12)
	panel.add_child(column)
	var title := Label.new()
	title.text = "Touch controls"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 32)
	column.add_child(title)

	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(scroll)
	var options := VBoxContainer.new()
	options.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	options.add_theme_constant_override("separation", 8)
	scroll.add_child(options)
	for spec in [["Button size", "scale", .7, 1.5], ["Opacity", "opacity", .2, 1.0], ["Look sensitivity", "sensitivity", .2, 8.0]]:
		var row := HBoxContainer.new()
		row.custom_minimum_size.y = 48
		options.add_child(row)
		var label := Label.new()
		label.text = spec[0]
		label.custom_minimum_size.x = 184
		label.add_theme_font_size_override("font_size", 24)
		row.add_child(label)
		var slider := HSlider.new()
		slider.custom_minimum_size.y = 48
		slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		slider.min_value = spec[2]
		slider.max_value = spec[3]
		slider.step = .05
		slider.value = settings()[spec[1]]
		row.add_child(slider)
		slider.value_changed.connect(func(value): _set_touch_option(spec[1], value))

	for spec in [["Left handed", "left_handed"], ["D-pad", "dpad"]]:
		var toggle := CheckButton.new()
		toggle.text = spec[0]
		toggle.custom_minimum_size.y = 48
		toggle.add_theme_font_size_override("font_size", 24)
		toggle.button_pressed = settings()[spec[1]]
		options.add_child(toggle)
		toggle.toggled.connect(func(value): _set_touch_option(spec[1], value))

	var done := Button.new()
	done.text = "Done"
	done.custom_minimum_size.y = 56
	done.add_theme_font_size_override("font_size", 25)
	column.add_child(done)
	done.pressed.connect(func():
		shade.queue_free()
		if was_paused:
			session.pause_game()
		else:
			session.checkpoint()
			session.resume_game()
	)

func _set_touch_option(key: String, value: Variant) -> void:
	var updated := settings()
	updated[key] = value
	apply_settings(updated)
