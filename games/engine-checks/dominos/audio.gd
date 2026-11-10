extends Node
const TRACK=preload("res://dominos/audio/afterglow.ogg")
const CUES={"select":preload("res://dominos/audio/select.wav"),"deny":preload("res://dominos/audio/deny.wav"),"slide":preload("res://dominos/audio/slide.wav"),"land":preload("res://dominos/audio/land.wav"),"draw":preload("res://dominos/audio/draw.wav"),"pass":preload("res://dominos/audio/pass.wav"),"round":preload("res://dominos/audio/round.wav"),"win":preload("res://dominos/audio/win.wav")}
var voices:Array=[]
var cursor:=0
var played:Array=[]
func _ready():
	if AudioServer.get_bus_index("Effects")==-1:AudioServer.add_bus();AudioServer.set_bus_name(AudioServer.bus_count-1,"Effects")
	for i in range(3):
		var player:=AudioStreamPlayer.new();player.bus="Effects";player.playback_type=AudioServer.PLAYBACK_TYPE_STREAM;player.volume_db=-7;add_child(player);voices.append(player)
	OnKlavGame.suspended.connect(func():for v in voices:v.stream_paused=true)
	OnKlavGame.resumed.connect(func():for v in voices:v.stream_paused=false)
func music():
	if not is_instance_valid(OnKlavGame.chrome):return
	var player=OnKlavGame.chrome.soundtrack
	if player.stream!=TRACK:
		var stream=TRACK;stream.loop=true;player.stream=stream;player.volume_db=-14;player.play();player.stream_paused=OnKlavGame.paused
func play(cue:String):
	if OnKlavGame.paused or not CUES.has(cue):return
	var v=voices[cursor];cursor=(cursor+1)%voices.size();v.stream=CUES[cue];v.play();played.append(cue)
	if played.size()>64:played.pop_front()
func stop():
	for v in voices:v.stop()
