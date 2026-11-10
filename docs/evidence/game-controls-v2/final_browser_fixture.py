from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/b5e698131b7f');r=w/'source/OnKlav';f=w/'fixture-final';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'))
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
		elif op in ["left", "right"] and is_instance_valid(port.chrome.dialog):
			var buttons: Array = port.chrome.dialog.find_children("*", "Button", true, false)
			if op == "right":
				JavaScriptBridge.get_interface("window").gameProbe = null
				buttons[buttons.size() - 1].pressed.emit()
				JavaScriptBridge.get_interface("window").quit_cleared = not FileAccess.file_exists(port.checkpoint_path)
				return
			buttons[0].pressed.emit()
		var names := []
		var texts := []
		var colors := []
		if is_instance_valid(port.chrome.dialog):
			for item in port.chrome.dialog.find_children("*", "Button", true, false):
				names.append(item.text)
				colors.append(item.get_theme_stylebox("normal").bg_color.to_html())
			for item in port.chrome.dialog.find_children("*", "Label", true, false): texts.append(item.text)
		var saved: Dictionary = port.Store.read_current(port.checkpoint_path)
		JavaScriptBridge.eval("window.gameState=" + JSON.stringify({"phase":port.state.phase,"scores":port.state.scores,"paused":port.rink.paused,"play_icon":port.chrome.paused_state,"modal":is_instance_valid(port.overlay),"chrome_dialog":is_instance_valid(port.chrome.dialog),"buttons":names,"texts":texts,"colors":colors,"saved_scores":saved.get("record",{}).get("match",{}).get("scores",[])})))
	JavaScriptBridge.get_interface("window").gameProbe = probe_callback
''';p.write_text(s)
exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64';out=w/'web-final';out.mkdir()
with (w/'fixture-export.log').open('w')as log:
 for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
  z=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=log,stderr=subprocess.STDOUT);assert z.returncode==0
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit: function() { console.log("ONKLAV_GAME_EXIT"); },'))
