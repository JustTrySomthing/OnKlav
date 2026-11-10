extends Node
## Eight original synthesized cues, cached once; bounded concurrent voices.
const MUSIC=preload("res://air_hockey/audio/neon-rally.ogg")
var cues: Array[AudioStreamWAV] = []
var voices: Array[AudioStreamPlayer] = []
var next_voice := 0
var last_impact := 0

func _ready() -> void:
	for kind in range(8):
		cues.append(make_cue(kind))
	for i in range(3):
		var voice := AudioStreamPlayer.new()
		voice.volume_db = -8
		voice.playback_type = AudioServer.PLAYBACK_TYPE_STREAM
		voice.bus = "Effects"
		add_child(voice)
		voices.append(voice)

func make_cue(kind: int) -> AudioStreamWAV:
	var rate := 22050
	var duration := .044 if kind==1 else .073 if kind==2 else .36 if kind in [3, 6, 7] else .21
	var count := int(rate * duration)
	var data := PackedByteArray()
	data.resize(count * 2)
	for i in range(count):
		var t := float(i) / rate
		var part := float(i) / count
		var base := 220.0 if kind == 1 else 390.0 if kind == 2 else 590.0
		if kind in [3, 4]:
			base = [523.25, 659.25, 783.99][mini(2, int(part * 3))]
		elif kind in [5, 6]:
			base = [493.88, 392.0, 329.63][mini(2, int(part * 3))]
		elif kind == 7:
			base = [523.25, 659.25, 523.25][mini(2, int(part * 3))]
		var envelope := minf(t / .003, 1) * pow(1 - part, 1.6)
		var sample := sin(TAU * base * t) + .2 * sin(TAU * base * 2 * t)
		if kind==1:
			sample=.64*sin(TAU*740*t)*exp(-t*70)+.38*sin(TAU*1280*t)*exp(-t*115)
		elif kind==2:
			sample=.82*sin(TAU*(245-790*t)*t)+.32*sin(TAU*1460*t)*exp(-t*110)
		elif kind in [3,4,5,6,7]:
			sample+=.15*sin(TAU*base*.5*t)
		data.encode_s16(i * 2, int(sample * envelope * 11000))
	var wav := AudioStreamWAV.new()
	wav.format = AudioStreamWAV.FORMAT_16_BITS
	wav.mix_rate = rate
	wav.data = data
	return wav

func play(kind: int, strength: float = .55) -> void:
	if kind < 0 or kind >= cues.size():
		return
	if kind in [1, 2]:
		if Time.get_ticks_msec() - last_impact < 45:
			return
		last_impact = Time.get_ticks_msec()
	var voice := voices[next_voice]
	next_voice = (next_voice + 1) % voices.size()
	voice.stream = cues[kind]
	var contact:=kind in [1,2]
	var power:=clampf(strength,0,1)
	voice.volume_db=lerpf(-20.0,-6.0,power) if contact else lerpf(-11.0,-7.0,power) if kind>=3 else -8
	voice.pitch_scale=lerpf(.90,1.12,power) if contact else 1.0
	voice.play()

func stop() -> void:
	for voice in voices:
		voice.stop()
		voice.stream = null

func _exit_tree() -> void:
	stop()

func music(chrome,paused:bool) -> void:
	if not is_instance_valid(chrome) or not is_instance_valid(chrome.soundtrack):return
	var player=chrome.soundtrack
	if player.stream!=MUSIC:
		var stream=MUSIC;stream.loop=true;player.stream=stream;player.volume_db=-11;player.play()
	player.stream_paused=paused
