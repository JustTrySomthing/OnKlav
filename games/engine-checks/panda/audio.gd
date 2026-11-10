extends Node
const CUES:={"pull":preload("res://panda/audio/panda_pull.wav"),"launch":preload("res://panda/audio/panda_launch.wav"),"hit":preload("res://panda/audio/panda_hit.wav"),"block":preload("res://panda/audio/panda_block.wav"),"whizz":preload("res://panda/audio/duel_whizz.wav"),"tumble":preload("res://panda/audio/duel_tumble.wav"),"combo":preload("res://panda/audio/duel_combo.wav"),"bird":preload("res://panda/audio/duel_bird.wav"),"miss":preload("res://panda/audio/duel_miss.wav"),"ready":preload("res://panda/audio/duel_ready.wav"),"glass":preload("res://panda/audio/duel_glass.wav"),"power":preload("res://panda/audio/duel_power.wav")}
const TRACK:=preload("res://panda/audio/duel_music0.ogg")
var generated:Dictionary={}
var voices:Array=[]
var next_voice:=0
var played:Array=[]
var last_cue:Dictionary={}
func _ready():
	for cue in ["tick","win","power"]:generated[cue]=synth(cue)
	for i in range(5):
		var player:=AudioStreamPlayer.new();player.playback_type=AudioServer.PLAYBACK_TYPE_STREAM;player.bus="Effects";player.volume_db=-12;add_child(player);voices.append(player)
	OnKlavGame.suspended.connect(pause_voices)
	OnKlavGame.resumed.connect(resume_voices)
func pause_voices():
	for voice in voices:voice.stream_paused=true
func resume_voices():
	for voice in voices:voice.stream_paused=false
func music(_level:int,finished:bool=false):
	if not is_instance_valid(OnKlavGame.chrome):return
	var player=OnKlavGame.chrome.soundtrack
	if finished:player.stop();return
	var stream=TRACK
	if player.stream!=stream:
		stream.loop=true;player.stream=stream;player.volume_db=-11;player.play();player.stream_paused=OnKlavGame.paused
const COOLDOWNS:={"hit":.12,"block":.12,"glass":.12,"combo":.25,"power":.2,"bird":.25,"pull":.1}
func play(cue:String,pitch:float=1.0):
	if OnKlavGame.paused or voices.is_empty():return
	var now:=Time.get_ticks_msec()/1000.0
	if now-float(last_cue.get(cue,-1.0))<float(COOLDOWNS.get(cue,0.0)):return
	# Do not abruptly replace a sound which is still playing: drop excess collision cues.
	var chosen:=-1
	for offset in range(voices.size()):
		var index:=(next_voice+offset)%voices.size()
		if not voices[index].playing:chosen=index;break
	if chosen<0:return
	last_cue[cue]=now;next_voice=(chosen+1)%voices.size()
	var voice=voices[chosen];voice.stream=CUES[cue] if CUES.has(cue) else generated.get(cue,generated["tick"]);voice.pitch_scale=clampf(pitch,.75,1.4);voice.stream_paused=false;voice.play();played.append(cue)
	if played.size()>100:played.pop_front()
func synth(cue:String)->AudioStreamWAV:
	var rate:=22050;var duration:=.5 if cue in["win","power"] else .16;var count:=int(duration*rate);var data:=PackedByteArray();data.resize(count*2)
	var phase:=0.0
	for i in range(count):
		var t:=float(i)/rate;var progress:=float(i)/count;var frequency:float=[523.25,659.25,783.99,1046.5][mini(3,int(progress*4))] if cue=="win" else 180+900*progress if cue=="power" else 800+500*progress
		phase+=TAU*frequency/rate
		var note_age:=fmod(t,duration/4)
		var envelope:=minf(note_age/.008,minf((duration/4-note_age)/.012,1)) if cue=="win" else minf(t/.008,1)
		var sample:=sin(phase)*pow(1-progress,1.5)*envelope
		data.encode_s16(i*2,int(sample*12000))
	var wav:=AudioStreamWAV.new();wav.format=AudioStreamWAV.FORMAT_16_BITS;wav.mix_rate=rate;wav.data=data;return wav
func stop():
	for voice in voices:voice.stop()
