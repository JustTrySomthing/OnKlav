extends Node
const CUES={"deal":preload("res://blackjack/audio/deal.wav"),"land":preload("res://blackjack/audio/land.wav"),"flip":preload("res://blackjack/audio/stand.wav"),"win":preload("res://blackjack/audio/win.wav"),"lose":preload("res://blackjack/audio/lose.wav"),"draw":preload("res://blackjack/audio/draw.wav"),"shuffle":preload("res://blackjack/audio/round.wav")}
var voices:Array[AudioStreamPlayer]=[]
var played:Array=[]
var next_voice:=0
func _ready() -> void:
	if AudioServer.get_bus_index("Effects")==-1:AudioServer.add_bus();AudioServer.set_bus_name(AudioServer.bus_count-1,"Effects")
	for i in range(3):
		var voice:=AudioStreamPlayer.new();voice.bus="Effects";voice.volume_db=-9;voice.playback_type=AudioServer.PLAYBACK_TYPE_STREAM;add_child(voice);voices.append(voice)
	OnKlavGame.suspended.connect(func():for voice in voices:voice.stream_paused=true)
	OnKlavGame.resumed.connect(func():for voice in voices:voice.stream_paused=false)
func play(kind:String) -> void:
	if not CUES.has(kind):return
	played.append(kind)
	if played.size()>32:played.pop_front()
	var voice:=voices[next_voice];next_voice=(next_voice+1)%voices.size();voice.stream=CUES[kind];voice.pitch_scale=1.1 if kind=="flip" else .94 if kind=="land" else 1.0;voice.volume_db=-15 if kind=="land" else -9;voice.stream_paused=false;voice.play()
func stop() -> void:
	for voice in voices:voice.stop()


func music() -> void:
	if not is_instance_valid(OnKlavGame.chrome): return
	var player: AudioStreamPlayer = OnKlavGame.chrome.soundtrack
	var track = preload("res://blackjack/audio/velvet-table.ogg")
	if player.stream == track: return
	track.loop = true
	player.stream = track
	player.volume_db = -14
	player.play()
	player.stream_paused = OnKlavGame.paused
