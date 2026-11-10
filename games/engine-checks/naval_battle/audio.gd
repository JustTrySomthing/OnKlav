extends Node
## Original Inner Circle naval cues, routed through the shared Effects bus.
const CUES={"fire":preload("res://naval_battle/audio/fire.wav"),"hit":preload("res://naval_battle/audio/hit.wav"),"miss":preload("res://naval_battle/audio/miss.wav"),"sink":preload("res://naval_battle/audio/sink.wav")}
const MUSIC=preload("res://naval_battle/audio/fleet-command.ogg")
const FLIGHT=preload("res://naval_battle/audio/flight.wav")
var flight_voice:AudioStreamPlayer
var voices:Array[AudioStreamPlayer]=[]
var next_voice:=0
var played:Array[String]=[]
var interface_cues: Dictionary
func interface_cue(pitch: float, duration: float) -> AudioStreamWAV:
	var samples := PackedByteArray()
	var count := roundi(22050*duration)
	samples.resize(count*2)
	for i in range(count):
		var t := i/22050.0
		var envelope := minf(1,t/.006)*pow(1.0-t/duration,2)
		var signal_value := sin(TAU*pitch*t)+.22*sin(TAU*pitch*1.5*t)
		samples.encode_s16(i*2,roundi(signal_value*envelope*2200))
	var stream := AudioStreamWAV.new()
	stream.format=AudioStreamWAV.FORMAT_16_BITS
	stream.mix_rate=22050
	stream.data=samples
	return stream

func _ready() -> void:
	interface_cues={"target":interface_cue(520,.085),"deploy":interface_cue(305,.15)}
	if AudioServer.get_bus_index("Effects")==-1:
		AudioServer.add_bus();AudioServer.set_bus_name(AudioServer.bus_count-1,"Effects")
	for i in range(3):
		var voice:=AudioStreamPlayer.new();voice.bus="Effects";voice.volume_db=-5;voice.playback_type=AudioServer.PLAYBACK_TYPE_STREAM;add_child(voice);voices.append(voice)
	flight_voice=AudioStreamPlayer.new();flight_voice.bus="Effects";flight_voice.volume_db=-4;flight_voice.playback_type=AudioServer.PLAYBACK_TYPE_STREAM;add_child(flight_voice)
	OnKlavGame.suspended.connect(func():
		for voice in voices:voice.stream_paused=true
		flight_voice.stream_paused=true)
	OnKlavGame.resumed.connect(func():
		for voice in voices:voice.stream_paused=false
		flight_voice.stream_paused=false)
func play(kind:String) -> void:
	var stream: AudioStream = CUES.get(kind,interface_cues.get(kind))
	if stream==null or voices.is_empty():return
	played.append(kind)
	if played.size()>16:played.pop_front()
	var voice:=voices[next_voice];next_voice=(next_voice+1)%voices.size();voice.stream=stream;voice.stream_paused=false;voice.play()
func music(finished:bool=false) -> void:
	if not is_instance_valid(OnKlavGame.chrome):return
	var player=OnKlavGame.chrome.soundtrack
	if player.stream!=MUSIC:
		var stream=MUSIC;stream.loop=true;player.stream=stream;player.volume_db=-9;player.play();player.stream_paused=OnKlavGame.paused
	if finished and player.playing:player.stop()
	elif not finished and not player.playing:player.play();player.stream_paused=OnKlavGame.paused
func start_flight(duration:float) -> void:
	if OnKlavGame.paused:return
	played.append("flight")
	if played.size()>16:played.pop_front()
	flight_voice.stream=FLIGHT;flight_voice.pitch_scale=.85/maxf(.1,duration);flight_voice.stream_paused=false;flight_voice.play()
func stop_flight() -> void:flight_voice.stop()
func stop() -> void:
	for voice in voices:voice.stop()
	stop_flight()
