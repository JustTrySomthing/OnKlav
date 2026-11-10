from pathlib import Path
import os
import shutil,subprocess
w=Path(os.environ['ONKLAV_PANDA_CHECK_DIR']);r=Path(__file__).resolve().parents[1];f=w/'panda-reboot-fixture';out=w/'panda-reboot-web'
shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('func _ready() -> void:','var reboot_probe\nfunc _ready() -> void:\n\tinstall_reboot_probe()')
s+='''
func install_reboot_probe():
	reboot_probe=JavaScriptBridge.create_callback(func(args):
		var port=get_children()[0]
		if not port.get("model"):return
		var command=str(args[0])
		if command=="course":
			port.model.stage=int(args[1]);port.model.stars=[]
			for i in range(port.model.stage):port.model.stars.append(0)
			port.model.setup_stage();port.hold=0;port.arena.load_age=1;port.refresh()
		elif command=="checkpoint":OnKlavGame.checkpoint()
		elif command=="launch":port.fire(int(args[1]),int(args[2]))
		elif command=="round_view":
			port.model.phase="round";port.model.completed=true;port.model.stars.resize(port.model.stage+1);port.model.stars[port.model.stage]=2;port.model.world=null;port.refresh()
		elif command=="advance":port.advance()
		elif command=="retry_save":OnKlavGame.return_to_onklav()
		elif command=="resume":OnKlavGame.resume_game()
		var factor=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var a=port.arena;var base=a.get_global_transform_with_canvas()
		var anchor:Vector2=(base*a.point_to_screen(a.aim_anchor()))*factor
		var low:Vector2=(base*a.point_to_screen(Vector2(500,1085)))*factor
		var dialogs=[]
		if is_instance_valid(OnKlavGame.chrome.dialog):
			for button in OnKlavGame.chrome.dialog.find_children("*","Button",true,false):
				var p=button.get_global_rect().get_center()*factor;dialogs.append({"text":button.text,"point":[p.x,p.y]})
		JavaScriptBridge.eval("window.pandaState="+JSON.stringify({"model":port.model.snapshot(),"can_shoot":port.can_shoot(),"anchor":[anchor.x,anchor.y],"low":[low.x,low.y],"held":a.aiming,"power":a.power,"paused":OnKlavGame.paused,"dialogs":dialogs,"save_error":OnKlavGame.last_save_error,"report":port.report_title.text,"report_visible":port.report.visible,"next_visible":port.controls.visible,"music_playing":OnKlavGame.chrome.soundtrack.playing,"particle_count":a.particles.size(),"profile_dirty":port.profile_dirty,"profile_busy":port.profile_busy,"profile_ready":port.profile_ready,"cannon_color":a.cannon_color().to_html(false),"style_cache_size":a.style_cache.size(),"sim_pending":port.flight_clock.pending,"stage_caption_visible":port.stage_label.visible,"result_detail":port.result_detail.text,"continue_width":port.next_button.size.x,"footer_size":port.turn_label.get_theme_font_size("font_size")})))
	JavaScriptBridge.get_interface("window").pandaProbe=reboot_probe
'''
p.write_text(s);out.mkdir(exist_ok=True)
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 x=subprocess.run([str(Path(os.environ['GODOT_BIN'])),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=120);print(x.stdout);assert x.returncode==0 and 'SCRIPT ERROR:'not in x.stdout and 'ERROR:'not in x.stdout
