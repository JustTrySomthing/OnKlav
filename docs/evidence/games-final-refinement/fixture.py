from pathlib import Path
import subprocess
w=Path('/workspace/scratch/f6c9793330b3');f=w/'hockey-fixture';p=f/'main.gd';s=p.read_text()
marker='\t\tvar ratio:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x'
a=s.index(marker)
s=s[:a]+'''		if kind=="panda":
			if str(args[0])=="character":port.act("C0")
			elif str(args[0])=="random":port.random_draft()
			elif str(args[0])=="ready":port.commit_build()
			elif str(args[0])=="fire":port.shoot(0,70)
			elif str(args[0])=="build":
				port.start_new();port.model.accept(0,"C0");port.model.leg=1;port.model.turn=0;port.model.phase="build";port.draft=port.model.empty();port.refresh()
			elif str(args[0])=="timeout":port.seconds=.05
			elif str(args[0])=="shoot":
				port.start_new();port.model.accept(0,"C0");port.model.accept(1,"B"+port.model.complete(port.model.empty(),24));port.draft=port.model.empty();port.seconds=30;port.load_delay=.9;port.arena.load_age=0;port.report_age=0;port.refresh()
			elif str(args[0])=="duck":
				port.start_new();port.model.round_id=1;port.model.accept(0,"C0");port.model.accept(1,"B"+port.model.complete(port.model.empty(),24));port.model.accept(0,"S0,70,0");port.model.ticks=550;port.draft=port.model.empty();port.refresh()
			elif str(args[0])=="notice":port.announce("BONUS PANDA!","One more chance. Make it count.",3)
		elif kind=="cat_dog" and str(args[0])=="notice":port.notify("Three connected!","Cats are building a line",3)
''' +s[a:]
s=s.replace('var progress:float=port.active_event', '''var extra:Dictionary={}
		if kind=="panda":
			var a=port.arena;var muzzle:Vector2=(a.get_global_transform_with_canvas()*(a.origin()+port.model.muzzle()*a.factor()))*ratio
			extra={"phase":port.model.phase,"seconds":port.seconds,"load":a.load_age,"local":port.can_play(),"cannon":[muzzle.x,muzzle.y],"reserve":port.model.shot_limit()-port.model.shots,"blocks":port.model.count(),"build_buttons":[],"report":port.report.visible,"report_box":port.report.get_theme_stylebox("panel").get_class()}
			for b in port.build_row.get_children():
				var point:Vector2=b.get_global_rect().get_center()*ratio;extra.build_buttons.append({"text":b.text,"visible":b.is_visible_in_tree(),"point":[point.x,point.y]})
		elif kind=="cat_dog":
			var grid:Rect2=port.board.grid();var top:Vector2=port.canvas.get_global_transform_with_canvas().affine_inverse()*(port.board.get_global_transform_with_canvas()*grid.position)
			var bottom:Vector2=port.canvas.get_global_transform_with_canvas().affine_inverse()*(port.scoreboard.get_global_transform_with_canvas()*Vector2(0,port.scoreboard.size.y))
			extra={"gap_top":bottom.y,"gap_bottom":top.y,"feedback_y":port.feedback_y(),"title_size":port.report_title.get_theme_font_size("font_size"),"report_box":port.report.get_theme_stylebox("panel").get_class()}
		var progress:float=port.active_event''')
s=s.replace('"snapshot":port.snapshot(),"progress":progress','"snapshot":port.snapshot(),"progress":progress,"extra":extra')
p.write_text(s)
exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64';x=subprocess.run([str(exe),'--headless','--path',str(f),'--export-release','OnKlav Web',str(w/'hockey-web/index.html')],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(w/'final-game-fixture-export.log').write_text(x.stdout);assert x.returncode==0 and 'ERROR:'not in x.stdout,x.stdout[-2000:]
