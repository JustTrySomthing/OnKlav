from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/b5e698131b7f');r=w/'source/OnKlav';f=w/'fixture-controls4-checked';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(port)\n\t\treturn','add_child(port)\n\t\tinstall_probe(port)\n\t\treturn');s+='''
var probe_callback
func install_probe(port: Control) -> void:
	probe_callback = JavaScriptBridge.create_callback(func(args):
		var op := str(args[0])
		if op == "start": port.start_single_player()
		elif op == "done": port.chrome.finish_temporary()
		elif op == "point": port.on_point(0)
		elif op == "serve": port.serve()
		elif op in ["mute", "unmute", "musicoff", "musicon"]:
			if op in ["mute", "unmute"]: port.chrome.muted = op == "mute"
			else: port.chrome.music = op == "musicon"
			port.chrome.save_preferences()
		elif op == "effect": port.chrome.audio_feedback()
		elif op in ["left", "right"] and is_instance_valid(port.chrome.dialog):
			var buttons: Array = port.chrome.dialog.find_children("*", "Button", true, false)
			if op == "right" and buttons[buttons.size() - 1].text == "Quit":
				JavaScriptBridge.get_interface("window").gameProbe = null
				buttons[buttons.size() - 1].pressed.emit()
				JavaScriptBridge.get_interface("window").quit_cleared = not FileAccess.file_exists(port.checkpoint_path)
				return
			(buttons[buttons.size() - 1] if op == "right" else buttons[0]).pressed.emit()
		var names := []
		var texts := []
		var colors := []
		if is_instance_valid(port.chrome.dialog):
			for item in port.chrome.dialog.find_children("*", "Button", true, false):
				names.append(item.text)
				colors.append(item.get_theme_stylebox("normal").bg_color.to_html())
			for item in port.chrome.dialog.find_children("*", "Label", true, false): texts.append(item.text)
		var saved: Dictionary = port.Store.read_current(port.checkpoint_path)
		JavaScriptBridge.eval("window.gameState=" + JSON.stringify({"notice":port.chrome.pause_attention,"blink_alpha":port.chrome.get_node("Pause").modulate.a,"music_playing":port.chrome.soundtrack.playing,"music_paused":port.chrome.soundtrack.stream_paused,"music_bytes":port.chrome.music_stream.data.size(),"master_peak":AudioServer.get_bus_peak_volume_left_db(0,0),"music_peak":AudioServer.get_bus_peak_volume_left_db(AudioServer.get_bus_index("Music"),0),"muted":port.chrome.muted,"volume":port.chrome.volume,"sample":port.chrome.music_stream.data.decode_s16(2048),"play_position":port.chrome.soundtrack.get_playback_position(),"fresh_pending":port.Chrome.hosted(),"phase":port.state.phase,"scores":port.state.scores,"paused":port.rink.paused,"play_icon":port.chrome.paused_state,"modal":is_instance_valid(port.overlay),"chrome_dialog":is_instance_valid(port.chrome.dialog),"buttons":names,"texts":texts,"colors":colors,"saved_scores":saved.get("record",{}).get("match",{}).get("scores",[])})))
	JavaScriptBridge.get_interface("window").gameProbe = probe_callback
''';p.write_text(s)
exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64';out=w/'web-controls4-checked';out.mkdir()
with (w/'flow-browser-export.log').open('w')as log:
 for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
  z=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=log,stderr=subprocess.STDOUT);assert z.returncode==0
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit: function() { console.log("ONKLAV_GAME_EXIT"); },'))
