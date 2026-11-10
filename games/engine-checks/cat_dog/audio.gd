extends Node
## Small original project cues; shared Master/Effects preferences and paused lifecycle.
const CUES={"drop":preload("res://cat_dog/audio/pet_swish.wav"),"land":preload("res://cat_dog/audio/land.wav"),"win":preload("res://cat_dog/audio/win.wav"),"lose":preload("res://cat_dog/audio/lose.wav"),"draw":preload("res://cat_dog/audio/draw.wav")}
var voices:Array[AudioStreamPlayer]=[]
var next_voice:=0
var played:Array=[]
func _ready() -> void:
	if AudioServer.get_bus_index("Effects")==-1:AudioServer.add_bus();AudioServer.set_bus_name(AudioServer.bus_count-1,"Effects")
	for i in range(3):
		var voice:=AudioStreamPlayer.new();voice.bus="Effects";voice.volume_db=-7;voice.playback_type=AudioServer.PLAYBACK_TYPE_STREAM;add_child(voice);voices.append(voice)
	OnKlavGame.suspended.connect(func():for voice in voices:voice.stream_paused=true)
	OnKlavGame.resumed.connect(func():for voice in voices:voice.stream_paused=false)
func play(kind:String,cat:bool=true,stacked:bool=false,finale:bool=false) -> void:
	if not CUES.has(kind) or voices.is_empty():return
	played.append(kind)
	if played.size()>24:played.pop_front()
	var voice:=voices[next_voice]
	next_voice=(next_voice+1)%voices.size()
	voice.stream=CUES[kind]
	voice.pitch_scale=1.0
	voice.volume_db=-7
	match kind:
		"drop":voice.pitch_scale=1.07 if cat else .9
		"land":
			voice.pitch_scale=1.28 if stacked else .78
			voice.volume_db=-7 if stacked else -5
		"win","lose":
			voice.volume_db=-5 if finale else -11
	voice.stream_paused=false
	voice.play()
func stop() -> void:
	for voice in voices:voice.stop()


func music() -> void:
	if not is_instance_valid(OnKlavGame.chrome): return
	var player: AudioStreamPlayer = OnKlavGame.chrome.soundtrack
	var track = preload("res://cat_dog/audio/playroom.ogg")
	if player.stream == track: return
	track.loop = true
	player.stream = track
	player.volume_db = -13
	player.play()
	player.stream_paused = OnKlavGame.paused
