from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'dominos-refine-fixture';shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
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
			port.cancel_drag();port.selected=-1;port.fresh_id=-1;port.reset_rack();port.busy=0;port.bot_wait=10;port.deal_age=10;port.feedback_time=0;port.refresh()
		elif op=="large":
			port.model.begin(19);port.model.chain=[];port.model.hands=[[],[]];port.model.stock=[]
			for id in range(28):
				var pair:Vector2i=port.Model.faces(id)
				if pair.x==1 and pair.y==1:port.model.chain=[[id,1,1,1]]
				elif pair.x==1 or pair.y==1:port.model.hands[1].append(id)
				else:port.model.hands[0].append(id)
			port.model.required=-1;port.model.turn=0;port.model.phase="playing";port.model.winner=-2;port.model.passes=0
			port.cancel_drag();port.selected=-1;port.fresh_id=-1;port.reset_rack();port.busy=0;port.bot_wait=10;port.deal_age=10;port.feedback_time=0;port.refresh()
		elif op in ["round","finish","long","draw","choice"]:
			port.model.begin(91);var steps:=0
			while steps<4000:
				if op=="draw" and port.model.phase=="playing" and port.model.turn==0 and port.model.moves(0).is_empty() and not port.model.stock.is_empty():break
				if op=="long" and port.model.chain.size()>=19:break
				if op=="choice" and port.model.phase=="playing" and port.model.turn==0 and not port.model.chain.is_empty() and port.model.chain[0][1]!=port.model.chain[-1][2]:
					var found:=false
					for id in port.model.hands[0]:
						if port.model.legal(id,0) and port.model.legal(id,1):found=true
					if found:break
				if port.model.phase=="round_over":
					if op=="round":break
					port.model.next_round()
				elif port.model.phase=="match_over":
					if op in ["draw","long","choice"]:port.model.begin(port.model.seed_value%999+1)
					else:break
				else:
					var move:Array=port.Model.pick(port.model.hands[port.model.turn],port.model.chain,port.model.required)
					if move.is_empty():port.model.apply(port.model.turn,"draw" if not port.model.stock.is_empty() else "pass")
					else:port.model.apply(port.model.turn,"play",move[0],move[1])
				steps+=1
			port.cancel_drag();port.selected=-1;port.flying={};port.table.hidden_id=-1;port.busy=0;port.bot_wait=10;port.deal_age=10;port.reset_rack();port.refresh()
			if op in ["round","finish"]:port.show_outcome()
		var ratio:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var controls:=[]
		for entry in [["left",port.left_button],["right",port.right_button],["draw",port.draw_button],["next",port.next_button]]:
			var b=entry[1];var rect:Rect2=b.get_global_rect();var point:Vector2=rect.get_center()*ratio;controls.append({"id":entry[0],"text":b.text,"point":[point.x,point.y],"rect":[rect.position.x*ratio,rect.position.y*ratio,rect.size.x*ratio,rect.size.y*ratio],"enabled":not b.disabled,"visible":b.is_visible_in_tree()})
		var tiles:=[]
		for b in port.hand.get_children():
			var rect:Rect2=b.get_global_rect();var point:Vector2=rect.get_center()*ratio;var pair:Vector2i=port.Model.faces(b.tile_id)
			tiles.append({"id":b.tile_id,"faces":[pair.x,pair.y],"point":[point.x,point.y],"rect":[rect.position.x*ratio,rect.position.y*ratio,rect.size.x*ratio,rect.size.y*ratio],"enabled":not b.disabled,"ends":port.valid_ends(b.tile_id),"fresh":b.fresh,"concealed":b.concealed,"dragging":b.dragging})
		var dialogs:=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for b in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):
				var point:Vector2=b.get_global_rect().get_center()*ratio;dialogs.append({"text":b.text,"point":[point.x,point.y]})
		var table:Rect2=port.table.get_global_rect();var chain:=[]
		for i in range(port.model.chain.size()):
			var rect:Rect2=port.table.tile_rect(i);var point:Vector2=(port.table.get_global_transform_with_canvas()*rect.position)*ratio;var extent:Vector2=(port.table.get_global_transform_with_canvas()*rect.end)*ratio-point;chain.append({"rect":[point.x,point.y,extent.x,extent.y],"angle":port.table.target_angle(i)})
		JavaScriptBridge.eval("window.dominosState="+JSON.stringify({"model":port.model.snapshot(),"snapshot":port.snapshot(),"tiles":tiles,"controls":controls,"dialogs":dialogs,"table":[table.position.x*ratio,table.position.y*ratio,table.size.x*ratio,table.size.y*ratio],"chain":chain,"busy":port.busy,"flying":port.flying,"drag":port.drag,"selected":port.selected,"status":port.status.text,"detail":port.detail.text,"paused":OnKlavGame.paused,"reduced":port.reduced,"feedback_scale":port.status.scale.x,"music_paused":OnKlavGame.chrome.soundtrack.stream_paused,"fresh_id":port.fresh_id,"particle_count":port.table.particles.size()})))
	JavaScriptBridge.get_interface("window").dominosProbe=probe_callback
''';p.write_text(s)
exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64';out=w/'dominos-refine-web';out.mkdir(exist_ok=True)
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 x=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(x.stdout);assert x.returncode==0 and 'SCRIPT ERROR:'not in x.stdout and 'ERROR:'not in x.stdout
