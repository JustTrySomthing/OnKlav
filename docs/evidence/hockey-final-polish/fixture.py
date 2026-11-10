from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'hockey-fixture';shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(port)\n\t\treturn','add_child(port)\n\t\tinstall_probe(port)\n\t\treturn',1)
for name in ['panda','blackjack','cat_dog','naval_battle']:
 old='add_child(preload("res://'+name+'/main.gd").new())'
 new='var port=preload("res://'+name+'/main.gd").new();add_child(port);install_controls_probe(port,"'+name+'")'
 s=s.replace(old,new)
s+='''
var controls_callback
func install_controls_probe(port:Control,kind:String):
	controls_callback=JavaScriptBridge.create_callback(func(args):
		if str(args[0])=="prepare":
			if kind=="naval_battle":port.model.shuffle_fleet();port.primary();port.target=0;port.own_view=false;port.primary()
			elif kind=="cat_dog":port.choose(3)
			elif kind=="panda":
				port.model=port.Model.new();port.model.accept(0,"C0");port.model.accept(1,"B"+port.model.complete(port.model.empty()));port.draft=port.model.empty();port.report_age=0;port.load_delay=0;port.apply_move(0,"S0,70,1")
		var ratio:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var buttons:=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for b in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):
				var p:Vector2=b.get_global_rect().get_center()*ratio;buttons.append({"text":b.text,"point":[p.x,p.y]})
		var progress:float=port.active_event.get("age",0) if kind=="naval_battle" else port.active.get("age",0) if kind in["cat_dog","blackjack"] else port.model.ticks
		JavaScriptBridge.eval("window.controlsState="+JSON.stringify({"paused":get_tree().paused,"temporary":OnKlavGame.chrome.temporary_open,"buttons":buttons,"snapshot":port.snapshot(),"progress":progress})))
	JavaScriptBridge.get_interface("window").controlsProbe=controls_callback
var hk_callback
func install_probe(port:Control):
	hk_callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op=="serve":port.serve()
		elif op=="goal":port.on_point(0)
		elif op=="midfield":port.rink.puck.place(Vector2(300,420),Vector2(140,400))
		elif op=="finish":
			while port.state.phase!=port.MatchState.Phase.FINISHED:
				if port.state.phase==port.MatchState.Phase.WAITING:port.serve()
				port.on_point(0)
		elif op=="enemy_goal":port.on_point(1)
		elif op=="collision":port.rink.puck.place(Vector2(320,port.rink.player.position.y-80),Vector2(0,550))
		var ratio:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var buttons:=[]
		if is_instance_valid(port.chrome.dialog):
			for b in port.chrome.dialog.find_children("*","Button",true,false):
				var p:Vector2=b.get_global_rect().get_center()*ratio;buttons.append({"text":b.text,"point":[p.x,p.y]})
		var chrome_buttons:={}
		for b in port.chrome.get_children():
			if b is Button:
				var p:Vector2=b.get_global_rect().get_center()*ratio;chrome_buttons[b.name]=[p.x,p.y]
		var r:Rect2=port.rink.get_global_rect();var serve:Vector2=port.serve_button.get_global_rect().get_center()*ratio
		JavaScriptBridge.eval("window.hockeyState="+JSON.stringify({"phase":port.state.phase,"paused":port.rink.paused,"stopped":port.rink.stopped,"freeze":port.rink.puck.freeze,"point":[port.rink.puck.position.x,port.rink.puck.position.y],"velocity":[port.rink.puck.linear_velocity.x,port.rink.puck.linear_velocity.y],"paddle":port.rink.player.position.x,"target":port.rink.target,"tick":port.rink.tick,"scores":port.state.scores,"goal_time":port.rink.goal_time,"result":port.result.visible,"temporary":port.chrome.temporary_open,"chrome_paused":port.chrome.paused_state,"buttons":buttons,"toolbar":chrome_buttons,"serve":[serve.x,serve.y],"rink":[r.position.x*ratio,r.position.y*ratio,r.size.x*ratio,r.size.y*ratio],"saved_velocity":[port.rink.snapshot().vx,port.rink.snapshot().vy],"render_point":[port.rink.render_puck.x,port.rink.render_puck.y],"music_paused":port.chrome.soundtrack.stream_paused,"fps":Engine.get_frames_per_second()})))
	JavaScriptBridge.get_interface("window").hockeyProbe=hk_callback
''';p.write_text(s)
exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64';out=w/'hockey-web';out.mkdir(exist_ok=True)
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 x=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(x.stdout);assert x.returncode==0 and 'SCRIPT ERROR:'not in x.stdout and 'ERROR:'not in x.stdout
