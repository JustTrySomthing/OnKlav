from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/b5e698131b7f');r=w/'source/OnKlav';f=w/'naval-fixture-final';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(preload("res://naval_battle/main.gd").new())','var port=preload("res://naval_battle/main.gd").new();add_child(port);install_probe(port)')
s+='''
var callback
func install_probe(port: Control) -> void:
	callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op=="random":port.model.shuffle_fleet();port.refresh()
		elif op=="primary":port.primary()
		elif op=="done":OnKlavGame.chrome.finish_temporary()
		elif op=="entry_left" and is_instance_valid(OnKlavGame.chrome.dialog):
			var buttons=OnKlavGame.chrome.dialog.find_children("*","Button",true,false);buttons[0].pressed.emit()
		var scale_value:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var r:Rect2=port.board.grid();var origin:Vector2=port.board.get_global_transform_with_canvas()*r.position;var extent:Vector2=port.board.get_global_transform_with_canvas().basis_xform(r.size)
		var b:Rect2=port.start_button.get_global_rect()
		var labels:=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for item in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):labels.append(item.text)
		JavaScriptBridge.eval("window.navalState="+JSON.stringify({"phase":port.model.phase,"turn":port.model.turn,"paused":OnKlavGame.paused,"ships":port.model.ships,"ready":port.model.fleet_ready(),"grid":[origin.x*scale_value,origin.y*scale_value,extent.x*scale_value,extent.y*scale_value],"primary":[b.get_center().x*scale_value,b.get_center().y*scale_value],"buttons":labels,"shots":port.model.shots,"hits":port.Model.hit_count(port.model.shots),"feedback":port.feedback,"own":port.own_view,"target":port.target,"pending":port.model.pending,"rx":port.network.last_rx,"tx":port.network.last_tx})))
	JavaScriptBridge.get_interface("window").navalProbe=callback
''';p.write_text(s)
out=w/'naval-web-final';out.mkdir();exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64'
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 z=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(z.stdout);assert z.returncode==0 and 'SCRIPT ERROR:'not in z.stdout
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit:function(){console.log("ONKLAV_GAME_EXIT")},'))
