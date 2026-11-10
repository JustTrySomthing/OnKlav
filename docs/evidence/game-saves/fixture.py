from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'save-fixture';shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('func _ready() -> void:','var save_probe\nfunc _ready() -> void:\n\tinstall_save_probe()')
s+='''
func install_save_probe():
	save_probe=JavaScriptBridge.create_callback(func(args):
		var port=get_children()[0];var h=OnKlavGame;var c=port.chrome if game=="hockey" else h.chrome
		var op=str(args[0])
		if op=="checkpoint":
			if game=="hockey":port.persist()
			else:h.checkpoint()
		elif op=="choose_new":port.choose_new() if game=="hockey" else h.choose_new()
		elif op=="new":port.restart() if game=="hockey" else h.new_game()
		elif op=="profile":h.save_data("profile",{"inventory":["sword","key"],"quest":4})
		elif op=="load_profile":h.load_data("profile").then() if false else profile_read(h)
		var dialogs=[]
		if is_instance_valid(c.dialog):
			for b in c.dialog.find_children("*","Button",true,false):
				var p=b.get_global_rect().get_center()*float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
				dialogs.append({"text":b.text,"point":[p.x,p.y]})
		if is_instance_valid(h.overlay):
			for b in h.overlay.find_children("*","Button",true,false):
				var p=b.get_global_rect().get_center()*float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
				dialogs.append({"text":b.text,"point":[p.x,p.y]})
		var state:Dictionary={"match":port.state.snapshot(),"rink":port.rink.snapshot()} if game=="hockey" else port.snapshot()
		JavaScriptBridge.eval("window.saveProbeState="+JSON.stringify({"game":game,"state":state,"dialogs":dialogs,"tree_paused":get_tree().paused,"pending":h.save_requests.size(),"error":h.last_save_error,"paused":port.rink.paused if game=="hockey" else h.paused})))
	JavaScriptBridge.get_interface("window").saveProbe=save_probe
func profile_read(h):
	var result:Dictionary=await h.load_data("profile")
	JavaScriptBridge.eval("window.profileRead="+JSON.stringify(result))
'''
s=s.replace('h.load_data("profile").then() if false else profile_read(h)','profile_read(h)');p.write_text(s)
out=w/'save-web';out.mkdir(exist_ok=True);exe=w/'toolchain/Godot_v4.7.2-stable_linux.x86_64'
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 x=subprocess.run([str(exe),'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=120);print(x.stdout);assert x.returncode==0 and 'SCRIPT ERROR:' not in x.stdout and 'ERROR:' not in x.stdout
