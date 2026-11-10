from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'dominos-fixture';shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(preload("res://dominos/main.gd").new())','var port=preload("res://dominos/main.gd").new();add_child(port);install_probe(port)')
s+='''
var probe_callback
func install_probe(port:Control):
	probe_callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op=="opening":
			for seed_id in range(1,100):
				port.model.begin(seed_id)
				if port.model.turn==0:break
			port.reset_rack();port.busy=0;port.bot_wait=10;port.deal_age=10;port.feedback_time=0;port.refresh()
		elif op=="auto":
			if port.model.turn==0 and port.busy<=0 and port.model.phase=="playing":
				var move:Array=port.Model.pick(port.model.hands[0],port.model.chain,port.model.required)
				if move.is_empty():port.draw_tile()
				else:port.selected=move[0];port.play_end(move[1])
		elif op in ["round","finish","long","draw"]:
			port.model.begin(91);var steps:=0
			while steps<4000:
				if op=="draw" and port.model.phase=="playing" and port.model.turn==0 and port.model.moves(0).is_empty() and not port.model.stock.is_empty():break
				if op=="long" and port.model.chain.size()>=19:break
				if port.model.phase=="round_over":
					if op=="round":break
					port.model.next_round()
				elif port.model.phase=="match_over":break
				else:
					var move:Array=port.Model.pick(port.model.hands[port.model.turn],port.model.chain,port.model.required)
					if move.is_empty():port.model.apply(port.model.turn,"draw" if not port.model.stock.is_empty() else "pass")
					else:port.model.apply(port.model.turn,"play",move[0],move[1])
				steps+=1
			port.flying={};port.table.hidden_id=-1;port.busy=0;port.bot_wait=10;port.deal_age=10;port.reset_rack();port.refresh()
			if op in ["round","finish"]:port.show_outcome()
		var ratio:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var controls:=[]
		for b in [port.left_button,port.right_button,port.draw_button,port.next_button,port.zoom_button]:
			var point:Vector2=b.get_global_rect().get_center()*ratio;controls.append({"text":b.text,"point":[point.x,point.y],"enabled":not b.disabled,"visible":b.is_visible_in_tree()})
		var tiles:=[]
		for b in port.hand.get_children():
			var point:Vector2=b.get_global_rect().get_center()*ratio;tiles.append({"id":b.tile_id,"point":[point.x,point.y],"enabled":not b.disabled,"playable":b.playable,"visible":b.is_visible_in_tree(),"concealed":b.concealed})
		var dialogs:=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for b in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):
				var point:Vector2=b.get_global_rect().get_center()*ratio;dialogs.append({"text":b.text,"point":[point.x,point.y]})
		var endpoints:=[]
		for end in range(2):
			var point:Vector2=(port.table.get_global_transform_with_canvas()*port.table.end_box(end).get_center())*ratio;endpoints.append([point.x,point.y])
		JavaScriptBridge.eval("window.dominosState="+JSON.stringify({"model":port.model.snapshot(),"snapshot":port.snapshot(),"tiles":tiles,"controls":controls,"dialogs":dialogs,"endpoints":endpoints,"busy":port.busy,"flying":port.flying,"selected":port.selected,"reduced":port.reduced,"feedback_scale":port.status.scale.x,"status":port.status.text,"detail":port.detail.text,"paused":OnKlavGame.paused,"feedback":port.feedback_time,"music_playing":OnKlavGame.chrome.soundtrack.playing,"music_paused":OnKlavGame.chrome.soundtrack.stream_paused,"audio":port.audio.played,"zoomed":port.zoomed,"scroll":port.tray.scroll_horizontal,"particle_count":port.table.particles.size()})))
	JavaScriptBridge.get_interface("window").dominosProbe=probe_callback
''';p.write_text(s)
exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64';out=w/'dominos-web';out.mkdir(exist_ok=True)
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 x=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(x.stdout);assert x.returncode==0 and 'SCRIPT ERROR:'not in x.stdout and 'ERROR:'not in x.stdout
