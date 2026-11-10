from pathlib import Path
import shutil,subprocess
w=Path('/workspace/scratch/f6c9793330b3');r=w/'project/OnKlav';f=w/'blackjack-fixture';shutil.copytree(r/'games/engine-checks',f,ignore=shutil.ignore_patterns('.godot'),dirs_exist_ok=True)
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('add_child(preload("res://blackjack/main.gd").new())','var port=preload("res://blackjack/main.gd").new();add_child(port);install_probe(port)')
s+='''
var bj_callback
func fixture_state(a:Array,b:Array,phase:String="player",winner:int=-1,next:int=-1) -> Dictionary:
	var deck:=[]
	for n in range(52):
		if n not in a and n not in b and n!=next:deck.append(n)
	if next>=0:deck.append(next)
	return {"schema":1,"hands":[a,b],"deck":deck,"phase":phase,"winner":winner,"legacy_rules":false}
func install_probe(port:Control) -> void:
	bj_callback=JavaScriptBridge.create_callback(func(args):
		var op:=str(args[0])
		if op in ["player_setup","bust_setup","natural_setup"]:
			port.events.clear();port.active={};port.table.active={};port.audio.stop();port.history=[];port.dealer_time=0
			port.model=port.Solo.new()
			var state:=fixture_state([8,4],[9,3],"player",-1,5)
			if op=="bust_setup":state=fixture_state([8,9],[3,5],"player",-1,4)
			if op=="natural_setup":state=fixture_state([0,9],[22,7],"finished",0)
			port.model.restore(state);port.queue_transition({},port.capture(),true);OnKlavGame.checkpoint()
		elif op=="legacy":
			OnKlavGame.pause_game();var state:=fixture_state([8,4],[9,3],"player",-1,5)
			var old:Variant=JSON.parse_string(JSON.stringify({"game":"blackjack","player_cards":state.hands[0],"dealer_cards":state.hands[1],"deck":state.deck,"finished":false}))
			JavaScriptBridge.eval("window.blackjackLegacyOk="+str(port.restore(old)).to_lower())
		elif op=="legacy_friend":
			var old:Variant=JSON.parse_string(JSON.stringify({"game":"blackjack","tape":port.model.tape,"network":port.network.snapshot()}));JavaScriptBridge.eval("window.blackjackLegacyFriendOk="+str(port.restore(old)).to_lower())
		elif op=="quiet_music":AudioServer.set_bus_mute(AudioServer.get_bus_index("Music"),true)
		elif op=="mute":AudioServer.set_bus_mute(0,true)
		elif op=="unmute":AudioServer.set_bus_mute(0,false)
		elif op=="reduced":port.table.reduced=true
		elif op=="resume":OnKlavGame.resume_game()
		var scale_value:=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var actions:={}
		for item in [port.hit_button,port.stand_button,port.next_button]:
			var point:Vector2=item.get_global_rect().get_center()*scale_value;actions[item.text]={"point":[point.x,point.y],"enabled":not item.disabled,"visible":item.visible}
		var buttons:=[];var texts:=[]
		for container in [OnKlavGame.chrome.dialog,OnKlavGame.overlay]:
			if not is_instance_valid(container):continue
			for item in container.find_children("*","Button",true,false):
				var point:Vector2=item.get_global_rect().get_center()*scale_value;buttons.append({"text":item.text,"point":[point.x,point.y]})
			for item in container.find_children("*","Label",true,false):texts.append(item.text)
		var voices:=[];var hidden:=[[],[]]
		for voice in port.audio.voices:voices.append({"playing":voice.playing,"paused":voice.stream_paused,"bus":voice.bus})
		for actor in range(2):
			for index in range(port.table.hands[actor].size()):hidden[actor].append(port.table.face_down(actor,index))
		var rect:Rect2=port.table.get_global_rect()
		JavaScriptBridge.eval("window.blackjackState="+JSON.stringify({"hands":port.model.hands,"shown":port.table.hands,"hidden":hidden,"revealed":port.table.revealed,"view":port.view,"turn":port.model.turn,"winner":port.model.winner,"finished":port.model.finished,"hand_over":port.model.hand_over,"round":port.model.round,"wins":port.model.wins,"history":port.history,"me":port.me,"friend":port.network.enabled,"invalid":port.invalid,"ready":port.can_play(),"busy":port.busy(),"paused":OnKlavGame.paused,"event":port.active,"queue":port.events.size(),"actions":actions,"buttons":buttons,"dialog_texts":texts,"report":port.report.visible,"report_title":port.report_title.text,"report_detail":port.report_detail.text,"status":port.status.text,"audio":port.audio.played,"voices":voices,"tx":port.network.last_tx,"rx":port.network.last_rx,"rect":[rect.position.x*scale_value,rect.position.y*scale_value,rect.size.x*scale_value,rect.size.y*scale_value],"tape":port.model.tape if port.network.enabled else [],"phase":"friend" if port.network.enabled else port.model.phase})))
	JavaScriptBridge.get_interface("window").blackjackProbe=bj_callback
''';p.write_text(s)
out=w/'blackjack-web';out.mkdir(exist_ok=True);exe='/workspace/scratch/b5e698131b7f/toolchain/Godot_v4.7.2-stable_linux.x86_64'
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 z=subprocess.run([exe,'--headless','--path',str(f),*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);print(z.stdout);assert z.returncode==0 and 'SCRIPT ERROR:'not in z.stdout and 'ERROR:'not in z.stdout
p=out/'index.html';p.write_text(p.read_text().replace('engine.startGame({','engine.startGame({onExit:function(){console.log("ONKLAV_GAME_EXIT")},'))
