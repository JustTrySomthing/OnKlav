extends RefCounted
## Loads a Music Maker v1 export from a trusted, bundled res:// directory.
## Add music.json to the Godot export preset's non-resource include filter.

static func load_loop(directory: String) -> AudioStream:
	if not directory.begins_with("res://") or ".." in directory.split("/"):
		return null
	var file := FileAccess.open(directory.path_join("music.json"), FileAccess.READ)
	if file == null or file.get_length() > 4096:
		return null
	var parsed: Variant = JSON.parse_string(file.get_as_text())
	if not parsed is Dictionary:
		return null
	var data: Dictionary = parsed
	if data.get("version") != 1 or data.get("sampleRate") != 44100 or data.get("beatsPerBar") != 4:
		return null
	var bpm: Variant = data.get("bpm")
	var end_frame: Variant = data.get("loopEndFrame")
	if not (bpm is float or bpm is int) or not (end_frame is float or end_frame is int):
		return null
	if bpm < 60 or bpm > 180 or bpm != floorf(bpm) or end_frame < 1 or end_frame > 5644800 or end_frame != floorf(end_frame):
		return null
	var bars := roundi(float(end_frame) * float(bpm) / 10584000.0)
	if bars < 4 or bars > 32 or int(floor(float(bars) * 10584000.0 / float(bpm))) != int(end_frame):
		return null
	if data.get("loopStartFrame") != 0:
		return null
	var name: Variant = data.get("audio")
	if name != "music.wav" and name != "music.ogg" and name != "music.mp3":
		return null
	var path: String = directory.path_join(name)
	if not ResourceLoader.exists(path, "AudioStream"):
		return null
	var stream := ResourceLoader.load(path, "AudioStream") as AudioStream
	if stream is AudioStreamWAV and name == "music.wav":
		if stream.mix_rate != 44100 or absf(stream.get_length() * stream.mix_rate - end_frame) > 2.0:
			return null
		var wav: AudioStreamWAV = stream.duplicate()
		wav.loop_mode = AudioStreamWAV.LOOP_FORWARD
		wav.loop_begin = 0
		wav.loop_end = int(end_frame)
		return wav
	if stream is AudioStreamOggVorbis and name == "music.ogg":
		if absf(stream.get_length() - end_frame / 44100.0) > 0.1:
			return null
		var ogg: AudioStreamOggVorbis = stream.duplicate()
		ogg.loop = true
		ogg.loop_offset = 0.0
		ogg.bpm = bpm
		ogg.bar_beats = 4
		return ogg
	if stream is AudioStreamMP3 and name == "music.mp3":
		if absf(stream.get_length() - end_frame / 44100.0) > 0.15:
			return null
		var mp3: AudioStreamMP3 = stream.duplicate()
		mp3.loop = true
		mp3.loop_offset = 0.0
		return mp3
	return null
