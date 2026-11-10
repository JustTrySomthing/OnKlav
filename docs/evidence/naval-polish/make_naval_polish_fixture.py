from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'naval-polish-fixture';shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(preload("res://naval_battle/main.gd").new())','var port=preload("res://naval_battle/main.gd").new();add_child(port);install_probe(port)')
s+='''
var probe_callback
func install_probe(port:Control) -> void:
	probe_callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op=="random":port.model.shuffle_fleet();port.refresh()
		elif op=="primary":port.primary()
		elif op=="battle":
			port.model.ships=[[0,1,2,3,4],[20,21,22,23],[40,41,42],[60,61,62],[80,81]]
			port.model.ready();port.model.opponent=[[10,11,12,13,14],[30,31,32,33],[50,51,52],[70,71,72],[90,91]];port.model.hunt=[0];port.own_view=false;port.refresh()
		elif op=="sink":
			port.ai_time=0;port.salvos.clear();port.active_event={};port.board.salvo={}
			for n in [10,11,12,13]:port.model.shots[str(n)]="hit"
			port.model.turn=true;port.target=14;port.own_view=false;port.refresh();port.primary()
		elif op=="incoming_sink":
			port.ai_time=0;port.salvos.clear();port.active_event={};port.board.salvo={}
			for n in [0,1,2,3]:port.model.incoming[str(n)]="hit"
			port.model.turn=false;port.queue_salvo(port.model.defend(4),true);port.refresh()
		elif op=="new":port.start_new()
		elif op=="reduced":port.board.reduced=true
		elif op=="quiet_music":AudioServer.set_bus_mute(AudioServer.get_bus_index("Music"),true)
		elif op=="mute":AudioServer.set_bus_mute(0,true)
		elif op=="unmute":AudioServer.set_bus_mute(0,false)
		elif op=="resume":OnKlavGame.resume_game()
		var scale_value:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var r:Rect2=port.board.grid();var origin:Vector2=port.board.get_global_transform_with_canvas()*r.position;var extent:Vector2=port.board.get_global_transform_with_canvas().basis_xform(r.size)
		var b:Rect2=port.start_button.get_global_rect()
		var docks:=[]
		for item in port.ships:
			var p:Vector2=item.get_global_rect().get_center()*scale_value;docks.append({"visible":item.visible,"point":[p.x,p.y]})
		var labels:=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for item in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):labels.append(item.text)
		var sounds:=[]
		for voice in port.audio.voices:sounds.append({"bus":voice.bus,"playing":voice.playing,"paused":voice.stream_paused})
		JavaScriptBridge.eval("window.navalState="+JSON.stringify({"phase":port.model.phase,"turn":port.model.turn,"paused":OnKlavGame.paused,"ships":port.model.ships,"wrecks":port.model.wrecks,"dock":docks,"ready":port.model.fleet_ready(),"grid":[origin.x*scale_value,origin.y*scale_value,extent.x*scale_value,extent.y*scale_value],"primary":[b.get_center().x*scale_value,b.get_center().y*scale_value],"primary_enabled":not port.start_button.disabled,"buttons":labels,"shots":port.model.shots,"incoming":port.model.incoming,"feedback":port.feedback,"own":port.own_view,"target":port.target,"pending":port.model.pending,"rx":port.network.last_rx,"tx":port.network.last_tx,"event":port.active_event,"queue":port.salvos.size(),"banner":port.banner.visible,"banner_title":port.banner_title.text,"audio":port.audio.played,"voices":sounds,"masked":port.board.masked_cells,"score_title":port.scoreboard.title})))
	JavaScriptBridge.get_interface("window").navalProbe=probe_callback
''';p.write_text(s)
out=w/'naval-polish-web';out.mkdir(exist_ok=True);exe=Path('/workspace/scratch/b5e698131b7f/toolchain/Godot_v4.7.2-stable_linux.x86_64')
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 z=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(z.stdout);assert z.returncode==0 and 'SCRIPT ERROR:'not in z.stdout and 'ERROR:' not in z.stdout
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit:function(){console.log("ONKLAV_GAME_EXIT")},'))
