from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'panda-fixture';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'),dirs_exist_ok=True)
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(preload("res://panda/main.gd").new())','var port=preload("res://panda/main.gd").new();add_child(port);install_panda_probe(port)')
s+='''
var panda_callback
func install_panda_probe(port:Control):
	panda_callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op=="resume":OnKlavGame.resume_game()
		elif op=="reduced":port.arena.reduced=true
		elif op=="mute":AudioServer.set_bus_mute(0,true)
		elif op=="unmute":AudioServer.set_bus_mute(0,false)
		elif op=="quiet_music":AudioServer.set_bus_mute(AudioServer.get_bus_index("Music"),true)
		elif op=="shoot_setup":
			port.model=port.Model.new();port.model.accept(0,"C0");port.model.accept(1,"B"+port.model.complete(port.model.empty()));port.draft=port.model.empty();port.seconds=30;port.report_age=0;port.load_delay=0;port.refresh()
		elif op=="build_setup":
			port.model=port.Model.new();port.model.accept(0,"C0");port.model.leg=1;port.model.turn=0;port.draft=port.model.empty();port.seconds=30;port.report_age=0;port.load_delay=0;port.refresh()
		elif op=="json_restore":
			var data:Variant=JSON.parse_string(JSON.stringify(port.snapshot()));JavaScriptBridge.eval("window.pandaRestored="+str(port.restore(data)).to_lower())
		elif op=="timeout":port.seconds=.05
		elif op=="final_setup":
			port.model=port.Model.new();port.model.accept(0,"C0");port.model.level=2;port.model.round_id=3;port.model.leg=1;port.model.turn=0;port.model.accept(0,"B"+port.model.complete(port.model.empty()));port.model.results=[[6,2,170]];port.model.accept(1,"F");port.model.level_rounds=[3,0];port.model.total=[38,26];port.draft=port.model.empty();port.report_age=0;port.load_delay=0;port.refresh()
		var ratio:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var actions:={}
		for node in [port.build_row,port.power_row,port.choice,port.controls]:scan_panda_buttons(node,actions,ratio)
		var dialogs:=[]
		for layer in [OnKlavGame.overlay,OnKlavGame.chrome.dialog]:
			if is_instance_valid(layer):scan_panda_dialog(layer,dialogs,ratio)
		var m=port.model
		var result:={"phase":m.phase,"turn":m.turn,"leg":m.leg,"level":m.level,"round":m.round_id,"shots":m.shots,"total":m.total,"round_wins":m.round_wins,"level_wins":m.level_wins,"blocks":m.blocks,"draft":port.draft,"placed":0,"ready":port.can_play(),"invalid":port.invalid,"paused":OnKlavGame.paused,"seconds":port.seconds,"ticks":m.ticks,"charges":m.charges,"winner":m.winner,"incoming":port.incoming.size(),"report":port.report.visible,"report_title":port.report_title.text,"actions":actions,"dialogs":dialogs,"audio":port.audio.played,"arena":[port.arena.get_global_rect().position.x*ratio,port.arena.get_global_rect().position.y*ratio,port.arena.get_global_rect().size.x*ratio,port.arena.get_global_rect().size.y*ratio],"music_playing":OnKlavGame.chrome.soundtrack.playing,"music_paused":OnKlavGame.chrome.soundtrack.stream_paused,"tx":port.network.last_tx,"rx":port.network.last_rx,"dragging":port.arena.drag_id,"aiming":port.arena.aiming,"aim":port.arena.aim,"power":port.arena.power,"particles":port.arena.particles.size(),"saved":OnKlavGame.load_match(OnKlavGame.path),"can_save":OnKlavGame.can_save,"started":OnKlavGame.session_started,"fps":Engine.get_frames_per_second()}
		var world_points:=[]
		for i in range(m.count()):
			var p:Vector2=port.arena.tray(i)
			if m.present(port.draft,i):result.placed+=1;p=m.point(port.draft,i)
			world_points.append((port.arena.get_global_transform()*(port.arena.origin()+p*port.arena.factor()))*ratio)
		result["pieces"]=world_points
		result["deck"]=(port.arena.get_global_transform()*(port.arena.origin()+Vector2(500,560)*port.arena.factor()))*ratio
		result["cannon"]=(port.arena.get_global_transform()*(port.arena.origin()+Vector2(500,1040)*port.arena.factor()))*ratio
		JavaScriptBridge.eval("window.pandaState="+JSON.stringify(result));)
	JavaScriptBridge.get_interface("window").pandaProbe=panda_callback
func scan_panda_buttons(node:Node,result:Dictionary,ratio:float):
	if node is Button and node.is_visible_in_tree():result[node.text]={"point":(node.get_global_rect().get_center()*ratio),"enabled":not node.disabled}
	for child in node.get_children():scan_panda_buttons(child,result,ratio)
func scan_panda_dialog(node:Node,result:Array,ratio:float):
	if node is Button:result.append({"text":node.text,"point":node.get_global_rect().get_center()*ratio})
	for child in node.get_children():scan_panda_dialog(child,result,ratio)
'''
# Vector2 JSON encoding is a string; explicitly convert probe point values.
s=s.replace('node.get_global_rect().get_center()*ratio','point_array(node.get_global_rect().get_center()*ratio)').replace('world_points.append((port.arena.get_global_transform()*(port.arena.origin()+p*port.arena.factor()))*ratio)','world_points.append(point_array((port.arena.get_global_transform()*(port.arena.origin()+p*port.arena.factor()))*ratio))')
s=s.replace('result["deck"]=(port.arena.get_global_transform()*(port.arena.origin()+Vector2(500,560)*port.arena.factor()))*ratio','result["deck"]=point_array((port.arena.get_global_transform()*(port.arena.origin()+Vector2(500,560)*port.arena.factor()))*ratio)').replace('result["cannon"]=(port.arena.get_global_transform()*(port.arena.origin()+Vector2(500,1040)*port.arena.factor()))*ratio','result["cannon"]=point_array((port.arena.get_global_transform()*(port.arena.origin()+Vector2(500,1040)*port.arena.factor()))*ratio)')
s+='\nfunc point_array(v:Vector2)->Array:return [v.x,v.y]\n';p.write_text(s)
godot='/workspace/scratch/b5e698131b7f/toolchain/Godot_v4.7.2-stable_linux.x86_64'
subprocess.run([godot,'--headless','--path',str(f),'--editor','--import'],check=True)
web=w/'panda-web';web.mkdir(exist_ok=True);subprocess.run([godot,'--headless','--path',str(f),'--export-release','OnKlav Web',str(web/'index.html')],check=True)
