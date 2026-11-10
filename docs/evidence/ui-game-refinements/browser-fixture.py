from pathlib import Path
import shutil,subprocess
w=Path.cwd();r=w/'project/OnKlav';f=w/'refinements-fixture'
shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('func _ready() -> void:','var probe_callback\nfunc _ready() -> void:\n\tinstall_probe()')
s+='''
func install_probe():
	probe_callback=JavaScriptBridge.create_callback(func(args):
		var port=get_children()[0];var op=str(args[0]);var dt=float(args[1]) if args.size()>1 else 0.0
		if op=="freeze":port.set_process(false)
		elif op=="step":port._process(dt)
		elif op=="start":port.start_new()
		elif op=="cat_drop":port.choose(0)
		elif op=="naval_attack":
			port.model.shuffle_fleet();port.model.ready();port.model.turn=false
			port.queue_salvo(port.model.ai_fire(),true);port.refresh()
		elif op=="dominos_play":
			port.busy=0
			for id in port.model.hands[port.model.turn]:
				if port.model.legal(id,0):
					port.perform("play",id,0,Vector2(180,600));break
		elif op=="burst":port.table.burst(port.table.size*.5,Color.CYAN,true)
		elif op=="reduce":OnKlavGame.chrome.reduce_motion=true;port.preferences()
		var state={"game":game}
		if game=="four":state.merge({"active":port.active,"think":port.think_time,"can_play":port.can_play(),"status":port.status.text})
		elif game=="naval":
			var color:Color=port.own_button.get_theme_stylebox("disabled").border_color
			state.merge({"own":port.own_view,"board_own":port.board.own,"disabled":port.own_button.disabled,"color":color.to_html(),"active":port.active_event,"delay":port.view_delay,"turn":port.model.turn})
		elif game=="dominos":state.merge({"flying":not port.flying.is_empty(),"rings":port.table.rings.size(),"particles":port.table.particles.size(),"model":port.model.snapshot(),"reduced":port.reduced})
		JavaScriptBridge.eval("window.refinementState="+JSON.stringify(state)))
	JavaScriptBridge.get_interface("window").refinementProbe=probe_callback
'''
p.write_text(s);out=w/'refinements-web';out.mkdir(exist_ok=True)
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 x=subprocess.run([str(w/'toolchain/Godot_v4.7.2-stable_linux.x86_64'),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=120);print(x.stdout);assert x.returncode==0 and 'SCRIPT ERROR:'not in x.stdout and 'ERROR:'not in x.stdout
