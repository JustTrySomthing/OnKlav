extends HBoxContainer
## Shared OnKlav game controls. One row: close, return, pause / help, audio, settings.
signal close_requested
signal return_requested
signal pause_requested
signal resume_requested
signal silent_pause_requested
signal entry_requested
signal help_requested
signal preferences_changed
var muted := false
var volume := .8
var music := true
var reduce_motion := false
var screen_awake := true
var dialog: CanvasLayer
var bridge
var paused_state := false
var temporary_open := false
var temporary_resume := false
var pause_notice: CanvasLayer
var pause_attention := false
var blink_time := 0.0
var soundtrack: AudioStreamPlayer
var _custom_music: AudioStream
var _music_transition: Tween
var feedback: AudioStreamPlayer
static var music_stream: AudioStreamWAV

const PREFS := "user://onklav-game-controls.cfg"

static func box(fill: String = "102638", edge: String = "294c60", radius: int = 14) -> StyleBoxFlat:
	var item := StyleBoxFlat.new()
	item.bg_color = Color(fill)
	item.border_color = Color(edge)
	item.set_border_width_all(1)
	item.set_corner_radius_all(radius)
	item.content_margin_left = 12
	item.content_margin_right = 12
	item.content_margin_top = 8
	item.content_margin_bottom = 8
	return item

static func caption(text: String, font_size: int = 18) -> Label:
	var item := Label.new()
	item.text = text
	item.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	item.add_theme_font_size_override("font_size", font_size)
	item.add_theme_color_override("font_color", Color("e4f6ff"))
	return item

static func action(text: String, callback: Callable) -> Button:
	var item := Button.new()
	item.text = text
	item.custom_minimum_size = Vector2(44, 44)
	item.add_theme_font_size_override("font_size", 18)
	item.add_theme_color_override("font_color", Color("e4f6ff"))
	for state in ["normal", "hover", "pressed", "focus"]:
		item.add_theme_stylebox_override(state, box("193e53" if state in ["hover", "pressed"] else "102638", "65c9df" if state == "focus" else "294c60"))
	item.pressed.connect(callback)
	return item

func icon(kind: String) -> Texture2D:
	var paths := {"close":"<path d='M6 6L18 18M18 6L6 18'/>", "return":"<path d='M15 5L8 12L15 19'/>", "play":"<path d='M8 4L20 12L8 20Z'/>", "pause":"<path d='M8 5V19M16 5V19'/>", "help":"<circle cx='12' cy='12' r='9'/><path d='M9.5 8.5C10 5.5 16 6 15.5 9C15.2 11 12 11 12 14M12 17V17.2'/>", "audio":"<path d='M3 9H7L12 5V19L7 15H3ZM16 8Q21 12 16 16M19 5Q27 12 19 19'/>", "settings":"<path d='M12 2L14 2L14.6 4.6L17 6L19.6 5.2L21.6 8.6L19.6 10.5V13.5L21.6 15.4L19.6 18.8L17 18L14.6 19.4L14 22H10L9.4 19.4L7 18L4.4 18.8L2.4 15.4L4.4 13.5V10.5L2.4 8.6L4.4 5.2L7 6L9.4 4.6L10 2Z'/><circle cx='12' cy='12' r='3.3'/>"}
	var image := Image.new()
	image.load_svg_from_string("<svg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='#e4f6ff' stroke-width='1.7' stroke-linecap='round' stroke-linejoin='round'>" + paths[kind] + "</svg>")
	return ImageTexture.create_from_image(image)

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	add_theme_constant_override("separation", 4)
	custom_minimum_size.y = 48
	for kind in ["close", "return", "pause", "help", "audio", "settings"]:
		if kind == "help":
			var gap := Control.new()
			gap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			add_child(gap)
		var item := action("", func(): dispatch(kind))
		if kind=="close":
			for state in ["normal","hover","pressed","disabled"]:item.add_theme_stylebox_override(state,StyleBoxEmpty.new())
			item.add_theme_stylebox_override("focus",box("00000000","65c9df",10))
			item.icon_alignment=HORIZONTAL_ALIGNMENT_CENTER
		item.name = kind.capitalize()
		item.icon = icon(kind)
		item.expand_icon = true
		item.add_theme_constant_override("icon_max_width", 24)
		item.custom_minimum_size = Vector2(48, 48)
		item.tooltip_text = {"close":"Quit game", "return":"Save and return to OnKlav", "pause":"Pause", "help":"Help", "audio":"Sound and music", "settings":"Settings"}[kind]
		add_child(item)
	load_preferences()
	setup_audio()
	if OS.has_feature("web"):
		bridge = JavaScriptBridge.create_callback(func(args):
			if args.size() == 1:
				dispatch(str(args[0])))
		JavaScriptBridge.get_interface("window").onklavGameAction = bridge
		if JavaScriptBridge.eval("document.hidden || window.onklavHostSuspended === true"):
			silent_pause_requested.emit.call_deferred()

func set_paused(value: bool) -> void:
	var changed := paused_state != value
	paused_state = value
	show_pause_notice(false)
	if is_instance_valid(soundtrack): soundtrack.stream_paused = value
	if has_node("Pause"):
		$Pause.icon = icon("play" if value else "pause")
		$Pause.tooltip_text = "Play" if value else "Pause"
	if changed and not value and OS.has_feature("web"):
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_RESUME")')

func dispatch(kind: String) -> void:
	# Host state probes and unknown messages must not cancel an open settings flow.
	if kind not in ["close","return","pause","resume","host_pause","entry","host_resume","help","audio","settings"]:return
	audio_feedback()
	if kind not in ["audio", "settings"]:
		temporary_open = false;temporary_resume=false
	match kind:
		"close": close_dialog(); close_requested.emit()
		"return": close_dialog(); return_requested.emit()
		"pause": close_dialog(); pause_requested.emit()
		"resume": close_dialog(); resume_requested.emit()
		"host_pause": close_dialog(); silent_pause_requested.emit()
		"entry": close_dialog(); entry_requested.emit()
		"host_resume": silent_pause_requested.emit(); show_pause_notice(true)
		"help": close_dialog(); help_requested.emit()
		"audio", "settings":
			var restore_playing:=temporary_resume if temporary_open else not paused_state
			silent_pause_requested.emit()
			temporary_open = true;temporary_resume=restore_playing
			if kind == "audio": show_audio()
			else: show_settings()

func finish_temporary() -> void:
	if not temporary_open: return
	var restore_playing:=temporary_resume
	temporary_open = false;temporary_resume=false
	close_dialog()
	if restore_playing:resume_requested.emit()
	else:show_pause_notice(true)

static func colored_action(text: String, callback: Callable, fill: String, ink: String) -> Button:
	var item := action(text, callback)
	item.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	item.add_theme_color_override("font_color", Color(ink))
	for state in ["normal", "hover", "pressed", "focus"]:
		item.add_theme_stylebox_override(state, box(fill, "ffffff" if state == "focus" else fill))
	return item

func choice(message: String, left: String, left_action: Callable, right: String, right_action: Callable, quit: bool = false) -> void:
	var content := dialog_box("")
	content.add_child(caption(message, 20))
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 12)
	content.add_child(row)
	row.add_child(colored_action(left, left_action, "7ee9c8" if quit else "183b50", "062b26" if quit else "e4f6ff"))
	row.add_child(colored_action(right, right_action, "ff6c8d" if quit else "7ee9c8", "340b18" if quit else "062b26"))

func load_preferences() -> void:
	var prefs := ConfigFile.new()
	if prefs.load(PREFS) == OK:
		muted = prefs.get_value("audio", "muted", false) == true
		music = prefs.get_value("audio", "music", true) == true
		var raw: Variant = prefs.get_value("audio", "volume", .8)
		if (raw is float or raw is int) and is_finite(float(raw)):
			volume = clampf(float(raw), 0, 1)
		reduce_motion = prefs.get_value("display", "reduce_motion", false) == true
		screen_awake = prefs.get_value("display", "screen_awake", true) == true
	for bus in ["Effects", "Music"]:
		if AudioServer.get_bus_index(bus) == -1:
			AudioServer.add_bus()
			var index := AudioServer.bus_count - 1
			AudioServer.set_bus_name(index, bus)
			AudioServer.set_bus_send(index, "Master")
	# Keep simultaneous game cues below full scale instead of clipping into static.
	if not AudioServer.get_bus_effect_count(0):
		var limiter:=AudioEffectLimiter.new();limiter.ceiling_db=-1;limiter.threshold_db=-3;limiter.soft_clip_db=2;AudioServer.add_bus_effect(0,limiter)
	apply_preferences()

func apply_preferences() -> void:
	AudioServer.set_bus_mute(0, muted or volume == 0)
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(.001, volume)))
	AudioServer.set_bus_mute(AudioServer.get_bus_index("Music"), not music)
	preferences_changed.emit()
	if OS.has_feature("web"):
		var loader_prefs := JSON.stringify({"muted":muted,"volume":volume,"reduce_motion":reduce_motion})
		JavaScriptBridge.eval("try { localStorage.setItem('onklav-loader-preferences',"+JSON.stringify(loader_prefs)+"); } catch (_) {}")
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_SCREEN_' + ("ON" if screen_awake else "OFF") + '")')

func save_preferences() -> void:
	apply_preferences()
	var prefs := ConfigFile.new()
	prefs.set_value("audio", "muted", muted)
	prefs.set_value("audio", "volume", volume)
	prefs.set_value("audio", "music", music)
	prefs.set_value("display", "reduce_motion", reduce_motion)
	prefs.set_value("display", "screen_awake", screen_awake)
	if prefs.save(PREFS) == OK and OS.has_feature("web"):
		JavaScriptBridge.force_fs_sync()

static func hosted() -> bool:
	if not OS.has_feature("web"): return false
	return bool(JavaScriptBridge.eval("window.onklavHosted === true"))

static func consume_fresh_start() -> bool:
	if not hosted(): return false
	return bool(JavaScriptBridge.eval("(()=>{const fresh=window.onklavFreshStart===true;window.onklavFreshStart=false;return fresh})()"))

static func consume_resume_start() -> bool:
	if not hosted(): return false
	return bool(JavaScriptBridge.eval("(()=>{const resume=window.onklavLaunchAction==='resume';window.onklavLaunchAction='';return resume})()"))

static func request_start_menu() -> bool:
	if hosted() and bool(JavaScriptBridge.eval("window.onklavEntryProtocol === 1")): return false
	if hosted():
		JavaScriptBridge.force_fs_sync()
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_START_MENU")')
		return true
	return false

## Keep the existing phone units while fitting short windows and wide tablets.
static func window_width() -> float:
	if OS.has_feature("web"):
		return maxf(320, float(JavaScriptBridge.eval("window.innerWidth")))
	return 432.0

static func fit_board(canvas: Control, available: Vector2, header: float, min_height: float) -> void:
	var width := window_width()
	var phone_scale := maxf(0.001, available.x / width)
	var height := maxf(1.0, available.y - header * phone_scale)
	var scale_factor := maxf(0.001, minf(phone_scale, height / min_height))
	var board_width := minf(width, 540.0)
	canvas.scale = Vector2.ONE * scale_factor
	canvas.position = Vector2((available.x - board_width * scale_factor) * .5, 0)
	canvas.size = Vector2(board_width, height / scale_factor)

## Fit modal layers to the current viewport, including while gameplay is paused.
static func fit_layer(screen: Control, after_fit: Callable = Callable()) -> void:
	var viewport := screen.get_viewport()
	var update := func():
		if not is_instance_valid(screen) or not screen.is_inside_tree(): return
		var extent := viewport.get_visible_rect().size
		var scale_factor := maxf(0.001, minf(extent.x / window_width(), extent.y / 240.0))
		screen.scale = Vector2.ONE * scale_factor
		screen.size = extent / scale_factor
		if after_fit.is_valid(): after_fit.call()
	viewport.size_changed.connect(update)
	screen.tree_exiting.connect(func(): viewport.size_changed.disconnect(update), CONNECT_ONE_SHOT)
	update.call()

static func fit_dialog(screen: Control, panel: PanelContainer, content: VBoxContainer) -> void:
	var fit_panel := func():
		panel.custom_minimum_size = Vector2(minf(400.0, screen.size.x - 32.0),
			minf(content.get_combined_minimum_size().y + 16.0, screen.size.y - 32.0))
	content.minimum_size_changed.connect(fit_panel)
	fit_layer(screen, fit_panel)

func show_pause_notice(value: bool) -> void:
	pause_attention = value
	blink_time = 0
	if has_node("Pause"): $Pause.modulate.a = 1
	if is_instance_valid(pause_notice): pause_notice.queue_free()
	pause_notice = null
	if not value: return
	pause_notice = CanvasLayer.new()
	pause_notice.layer = 90
	add_child(pause_notice)
	var screen := Control.new()
	screen.mouse_filter = Control.MOUSE_FILTER_IGNORE
	pause_notice.add_child(screen)
	fit_layer(screen)
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	screen.add_child(center)
	var panel := PanelContainer.new()
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.add_theme_stylebox_override("panel", box("102638ed", "294c60", 16))
	center.add_child(panel)
	var label := caption("Game paused", 26)
	label.autowrap_mode = TextServer.AUTOWRAP_OFF
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	panel.add_child(label)

func _process(delta: float) -> void:
	if pause_attention and has_node("Pause"):
		blink_time += delta
		$Pause.modulate.a = 1.0 if reduce_motion else .72 + .28 * cos(blink_time * TAU / 2.4)

## Pass a validated MusicBundle stream before adding chrome to the scene,
## or replace the current loop with a short fade during play.
func set_music_track(track: AudioStream, fade_seconds: float = 0.0) -> void:
	if track == null:
		return
	_custom_music = track
	if not is_instance_valid(soundtrack):
		return
	if _music_transition != null:
		_music_transition.kill()
	var fade := clampf(fade_seconds, 0.0, 3.0)
	if fade == 0.0 or paused_state:
		_activate_music(track)
		soundtrack.volume_db = 0.0
		return
	_music_transition = create_tween()
	_music_transition.tween_property(soundtrack, "volume_db", -45.0, fade * 0.5)
	_music_transition.tween_callback(_activate_music.bind(track))
	_music_transition.tween_property(soundtrack, "volume_db", 0.0, fade * 0.5)

func _activate_music(track: AudioStream) -> void:
	soundtrack.stream = track
	if DisplayServer.get_name() != "headless": soundtrack.play()
	soundtrack.stream_paused = paused_state

func setup_audio() -> void:
	if _custom_music == null and music_stream == null:
		music_stream = AudioStreamWAV.new()
		music_stream.format = AudioStreamWAV.FORMAT_16_BITS
		music_stream.mix_rate = 22050
		var count := 22050 * 8
		var data := PackedByteArray(); data.resize(count * 2)
		var notes := [60, 64, 67, 71, 57, 60, 64, 67, 53, 57, 60, 64, 55, 59, 62, 67]
		for i in range(count):
			var t := float(i) / 22050
			var step := mini(15, int(t * 2))
			var age := fmod(t, .5)
			var hz := 440.0 * pow(2, float(notes[step] - 69) / 12)
			var fade := minf(t / .04, minf((8 - t) / .04, 1))
			var wave := (sin(TAU * hz * t) + .24 * sin(TAU * hz * 2 * t)) * exp(-age * 5) * minf(age / .012, minf((.5-age)/.018,1))
			data.encode_s16(i * 2, int(wave * 3600 * fade))
		music_stream.data = data
		music_stream.loop_mode = AudioStreamWAV.LOOP_FORWARD
		music_stream.loop_end = count
	soundtrack = AudioStreamPlayer.new(); soundtrack.playback_type = AudioServer.PLAYBACK_TYPE_STREAM; soundtrack.bus = "Music"; soundtrack.stream = _custom_music if _custom_music != null else music_stream
	add_child(soundtrack)
	if _custom_music == null or DisplayServer.get_name() != "headless": soundtrack.play()
	soundtrack.stream_paused = paused_state
	feedback = AudioStreamPlayer.new(); feedback.playback_type = AudioServer.PLAYBACK_TYPE_STREAM; feedback.bus = "Effects"; feedback.volume_db = -14
	var wave := AudioStreamWAV.new(); wave.format = AudioStreamWAV.FORMAT_16_BITS; wave.mix_rate = 22050
	var data := PackedByteArray(); data.resize(2205)
	# A short, soft interaction cue, independent from the music bus.
	data.resize(2204)
	for i in range(1102): data.encode_s16(i * 2, int(sin(TAU * 660 * i / 22050.0) * (1 - i / 1102.0) * minf(i/110.0,1) * 7000))
	wave.data = data; feedback.stream = wave; add_child(feedback)

func audio_feedback() -> void:
	if is_instance_valid(feedback): feedback.play()

func close_dialog() -> void:
	if is_instance_valid(dialog):
		dialog.queue_free()
	dialog = null

func dialog_box(title: String) -> VBoxContainer:
	close_dialog()
	dialog = CanvasLayer.new()
	dialog.layer = 200
	add_child(dialog)
	var screen := Control.new()
	dialog.add_child(screen)
	var shade := ColorRect.new()
	shade.color = Color(0, 0, 0, .72)
	shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	screen.add_child(shade)
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	shade.add_child(center)
	var panel := PanelContainer.new()
	panel.add_theme_stylebox_override("panel", box("102638", "38d4ea", 20))
	center.add_child(panel)
	var content := VBoxContainer.new()
	content.add_theme_constant_override("separation", 16)
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	panel.add_child(scroll)
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(content)
	fit_dialog(screen, panel, content)
	if not title.is_empty(): content.add_child(caption(title, 24))
	return content

func toggle(content: VBoxContainer, title: String, value: bool, callback: Callable) -> void:
	var item := CheckButton.new()
	item.text = title
	item.button_pressed = value
	item.custom_minimum_size.y = 44
	item.add_theme_font_size_override("font_size", 18)
	item.toggled.connect(callback)
	content.add_child(item)

func show_audio() -> void:
	var content := dialog_box("Sound & music")
	toggle(content, "Mute", muted, func(value): muted = value; save_preferences())
	var value_label := caption("Game volume · %d%%" % roundi(volume * 100))
	content.add_child(value_label)
	var slider := HSlider.new()
	slider.min_value = 0
	slider.max_value = 1
	slider.step = .01
	slider.value = volume
	slider.custom_minimum_size.y = 44
	slider.value_changed.connect(func(value): volume = value; value_label.text = "Game volume · %d%%" % roundi(volume * 100); save_preferences())
	content.add_child(slider)
	toggle(content, "Music", music, func(value): music = value; save_preferences())
	content.add_child(action("Done", finish_temporary))

func show_settings() -> void:
	var content := dialog_box("Settings")
	toggle(content, "Reduce motion", reduce_motion, func(value): reduce_motion = value; save_preferences())
	toggle(content, "Keep screen awake", screen_awake, func(value): screen_awake = value; save_preferences())
	content.add_child(action("Done", finish_temporary))

static func return_to_onklav() -> void:
	if OS.has_feature("web"):
		JavaScriptBridge.force_fs_sync()
		JavaScriptBridge.eval('console.log("ONKLAV_GAME_RETURN")')

func _exit_tree() -> void:
	if _music_transition != null: _music_transition.kill()
	if is_instance_valid(soundtrack): soundtrack.stop(); soundtrack.stream = null
	if is_instance_valid(feedback): feedback.stop(); feedback.stream = null
	music_stream = null
	if OS.has_feature("web") and JavaScriptBridge.get_interface("window").onklavGameAction == bridge:
		JavaScriptBridge.get_interface("window").onklavGameAction = null
