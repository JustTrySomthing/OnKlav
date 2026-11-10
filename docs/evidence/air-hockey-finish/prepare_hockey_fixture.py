from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/b5e698131b7f');r=w/'recovered/OnKlav';f=w/'hockey-finish-fixture';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';s=p.read_text();s=s.replace('../../developer/games/engine-template.zip',(r/'developer/games/engine-template.zip').as_posix());p.write_text(s)
p=f/'main.gd';s=p.read_text().replace('add_child(port)\n\t\treturn','add_child(port)\n\t\tinstall_probe(port)\n\t\treturn');s+='''
# Private browser acceptance harness; never included in the production export.
var probe_callback
var stress_count := 0
func install_probe(port: Control) -> void:
	probe_callback = JavaScriptBridge.create_callback(func(args):
		var op := str(args[0])
		if op == "pause": port.show_pause()
		elif op == "resume": port.resume()
		elif op == "restart": port.restart()
		elif op == "stress":
			for i in range(5000):
				var group := WorkerThreadPool.add_group_task(func(_n): pass, 0)
				WorkerThreadPool.wait_for_group_task_completion(group)
			for i in range(100):
				var group := WorkerThreadPool.add_group_task(func(_n): stress_count += 1, 23, 2)
				WorkerThreadPool.wait_for_group_task_completion(group)
		elif op == "result":
			for i in range(10):
				if port.state.phase == 0: port.serve()
				port.on_point(0 if i < 7 else 1)
		elif op == "quit":
			JavaScriptBridge.get_interface("window").hockeyProbe = null
			port.exit_game()
			return
		var status := {"phase":port.state.phase,"scores":port.state.scores,"paused":port.rink.paused,"target":port.rink.target,"puck":[port.rink.puck.position.x,port.rink.puck.position.y],"result":port.result.visible,"stress_count":stress_count,"zero_groups":5000 if stress_count > 0 else 0}
		JavaScriptBridge.eval("window.hockeyState = " + JSON.stringify(status)))
	JavaScriptBridge.get_interface("window").hockeyProbe = probe_callback
''';p.write_text(s)
bin=str(w/'toolchain/Godot_v4.7.2-stable_linux.x86_64');out=w/'hockey-finish-fixture-export';out.mkdir()
with (w/'hockey-finish-fixture.log').open('w')as log:
 for a in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
  z=subprocess.run([bin,'--headless','--path',str(f),*a],stdout=log,stderr=subprocess.STDOUT);assert z.returncode==0
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit: function() { console.log("ONKLAV_GAME_EXIT"); },'))
