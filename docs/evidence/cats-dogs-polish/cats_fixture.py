from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'cats-fixture';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'),dirs_exist_ok=True)
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(preload("res://cat_dog/main.gd").new())','var port=preload("res://cat_dog/main.gd").new();add_child(port);install_probe(port)')
s+='''
var probe_callback
func install_probe(port:Control) -> void:
	probe_callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op=="new":port.start_new()
		elif op=="win_setup":
			port.start_new()
			for col in [0,6,1,6,2,5]:port.model.apply(port.model.turn,"F"+str(col))
			port.refresh()
		elif op=="full_setup":
			port.start_new()
			for col in [0,0,0,0,0,0]:port.model.apply(port.model.turn,"F"+str(col))
			port.refresh()
		elif op=="legacy":
			OnKlavGame.pause_game()
			var m=port.Model.new()
			for col in [0,6,0,6,0]:m.apply(m.turn,"F"+str(col))
			JavaScriptBridge.eval("window.catsLegacyOk="+str(port.restore({"game":"four","board":m.board,"pending":true,"finished":false})).to_lower())
		elif op=="legacy_friend":
			var old:Variant=JSON.parse_string(JSON.stringify({"game":"four","tape":port.model.tape,"network":port.network.snapshot()}))
			JavaScriptBridge.eval("window.catsLegacyFriendOk="+str(port.restore(old)).to_lower())
		elif op=="quiet_music":AudioServer.set_bus_mute(AudioServer.get_bus_index("Music"),true)
		elif op=="mute":AudioServer.set_bus_mute(0,true)
		elif op=="unmute":AudioServer.set_bus_mute(0,false)
		elif op=="reduced":port.board.reduced=true
		elif op=="resume":OnKlavGame.resume_game()
		var scale_value:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var r:Rect2=port.board.grid();var origin:Vector2=port.board.get_global_transform_with_canvas()*r.position;var extent:Vector2=port.board.get_global_transform_with_canvas().basis_xform(r.size)
		var buttons:=[];var dialog_texts:=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for item in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):
				var p:Vector2=item.get_global_rect().get_center()*scale_value;buttons.append({"text":item.text,"point":[p.x,p.y]})
			for item in OnKlavGame.chrome.dialog.find_children("*","Label",true,false):dialog_texts.append(item.text)
		if is_instance_valid(OnKlavGame.overlay):
			for item in OnKlavGame.overlay.find_children("*","Button",true,false):
				var p:Vector2=item.get_global_rect().get_center()*scale_value;buttons.append({"text":item.text,"point":[p.x,p.y]})
			for item in OnKlavGame.overlay.find_children("*","Label",true,false):dialog_texts.append(item.text)
		var voices:=[]
		for voice in port.audio.voices:voices.append({"bus":voice.bus,"playing":voice.playing,"paused":voice.stream_paused})
		JavaScriptBridge.eval("window.catsState="+JSON.stringify({"session_started":OnKlavGame.session_started,"can_save":OnKlavGame.can_save,"save_path":OnKlavGame.path,"saved":OnKlavGame.load_match(OnKlavGame.path),"board":port.model.board,"shown":port.board.values,"base":port.model.base,"tape":port.model.tape,"turn":port.model.turn,"winner":port.model.winner,"finished":port.model.finished,"me":port.me,"host_dog":port.host_dog,"friend":port.network.enabled,"ready":port.can_play(),"paused":OnKlavGame.paused,"grid":[origin.x*scale_value,origin.y*scale_value,extent.x*scale_value,extent.y*scale_value],"event":port.active,"queue":port.events.size(),"think":port.think_time,"status":port.status.text,"report":port.report.visible,"report_title":port.report_title.text,"line":port.board.win_line,"hover":port.board.hover,"audio":port.audio.played,"voices":voices,"buttons":buttons,"dialog_texts":dialog_texts,"invalid":port.invalid,"rx":port.network.last_rx,"tx":port.network.last_tx,"new_visible":port.new_button.visible})))
	JavaScriptBridge.get_interface("window").catsProbe=probe_callback
''';p.write_text(s)
out=w/'cats-web';out.mkdir(exist_ok=True);exe='/workspace/scratch/b5e698131b7f/toolchain/Godot_v4.7.2-stable_linux.x86_64'
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 z=subprocess.run([exe,'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(z.stdout);assert z.returncode==0 and 'SCRIPT ERROR:'not in z.stdout and 'ERROR:'not in z.stdout
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit:function(){console.log("ONKLAV_GAME_EXIT")},'))
