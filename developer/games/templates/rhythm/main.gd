extends "res://starter_hud.gd"
const BEAT := 0.65
const COUNT := 20
const WINDOW := 0.16
var elapsed := 0.0
var judged := 0
var hits := 0
var sounded := 0
var correction := 0
var cue: AudioStreamPlayer

func _ready() -> void:
	make_layout()
	field.draw.connect(draw_beat)
	action("−10 ms", adjust.bind(-10))
	action("Tap beat", tap)
	action("+10 ms", adjust.bind(10))
	cue = AudioStreamPlayer.new()
	var samples := PackedByteArray()
	samples.resize(960)
	for index in range(480):
		var gain := sin(PI * index / 479.0)
		samples.encode_s16(index * 2, int(9000 * gain * sin(TAU * 880 * index / 16000.0)))
	var stream := AudioStreamWAV.new()
	stream.format = AudioStreamWAV.FORMAT_16_BITS
	stream.mix_rate = 16000
	stream.data = samples
	cue.stream = stream
	cue.bus = "Effects"
	add_child(cue)
	render()
	attach("Tap when the pulse reaches the ring or the cue plays. The timing window is ±160 ms. Positive offset compensates for taps arriving late.")

func _process(delta: float) -> void:
	if OnKlavGame.paused or elapsed >= COUNT * BEAT + WINDOW + 0.2:
		return
	elapsed = minf(14.0, elapsed + delta)
	var beat := mini(COUNT, int(elapsed / BEAT))
	if beat > sounded:
		sounded = beat
		cue.play()
	while judged < COUNT and elapsed - correction / 1000.0 > (judged + 1) * BEAT + WINDOW:
		judged += 1
	render()

func adjust(amount: int) -> void:
	correction = clampi(correction + amount, -150, 150)
	render()
	OnKlavGame.checkpoint()

func tap() -> void:
	if judged >= COUNT:
		return
	if absf(elapsed - correction / 1000.0 - (judged + 1) * BEAT) <= WINDOW:
		hits += 1
		judged += 1
		render()
		OnKlavGame.checkpoint()

func draw_beat() -> void:
	var center := field.size * 0.5
	var radius := minf(field.size.x, field.size.y) * 0.3
	field.draw_arc(center, radius, 0, TAU, 64, Color("#9ab5d3"), 4)
	if judged < COUNT:
		var phase := clampf(((judged + 1) * BEAT - elapsed) / BEAT, 0, 1)
		field.draw_circle(center, radius * (1 - phase), Color("#77dfbf"), false, 5)

func render() -> void:
	status.text = "%d / %d hits   ·   Offset %+d ms" % [hits, COUNT, correction]
	replay_button.visible = judged == COUNT
	field.queue_redraw()

func snapshot() -> Dictionary:
	return {"elapsed": elapsed, "judged": judged, "hits": hits, "correction": correction}

func restore(state: Dictionary) -> bool:
	if not finite_range(state.get("elapsed"), 0, 14) or not Rules.bounded_integer(state.get("judged"), 0, COUNT) or not Rules.bounded_integer(state.get("hits"), 0, COUNT) or not Rules.bounded_integer(state.get("correction"), -150, 150):
		return false
	if state.hits > state.judged: return false
	elapsed = float(state.elapsed)
	judged = int(state.judged)
	hits = int(state.hits)
	correction = int(state.correction)
	sounded = mini(COUNT, int(elapsed / BEAT))
	render()
	return true

func restart() -> void:
	elapsed = 0
	judged = 0
	hits = 0
	sounded = 0
	render()
