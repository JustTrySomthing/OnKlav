"""Export actual game scenes with a test-only completion/state probe in a copied root."""
from pathlib import Path
import os,shutil,subprocess
r=Path(__file__).resolve().parents[1];w=Path(os.environ['ONKLAV_GAME_CHECK_DIR']);f=w/'fixture';out=w/'web'
shutil.copytree(r/'games/engine-checks',f,dirs_exist_ok=True,ignore=shutil.ignore_patterns('.godot'))
p=f/'export_presets.cfg';p.write_text(p.read_text().replace('../../developer/games/engine-template.zip',str(r/'developer/games/engine-template.zip')))
p=f/'main.gd';s=p.read_text().replace('func _ready() -> void:','var test_callback\nfunc _ready() -> void:\n\tinstall_game_probe()')
s+='''
func install_game_probe():
	test_callback=JavaScriptBridge.create_callback(func(args):
		var port=get_children()[0]
		var command=str(args[0])
		if command=="cats_prefix":
			port.start_new()
			for col in [3,3,4]:port.model.apply(port.model.turn,"F"+str(col))
			port.think_time=.1;port.refresh()
		if command=="complete":
			match game:
				"hockey":port.state.phase=2;port.state.scores=[6,4];port.rink.stopped=true
				"four":port.model.finished=true;port.model.winner=0;port.model.match_wins=[3,0];port.active={};port.events=[]
				"blackjack":port.model.finished=true;port.model.hand_over=true;port.model.winner=0;port.model.wins=[6,4];port.model.phase="finished";port.active={};port.events=[];port.view=port.capture()
				"dominos":port.model.phase="match_over";port.model.match_wins=[3,0];port.model.round_winner=0;port.busy=0;port.feedback_time=0
				"panda":port.model.phase="finished";port.model.completed=true;port.model.stars.resize(12);port.model.stars.fill(2);port.model.world=null
				"naval":port.model.phase="finished";port.model.winner="you";port.active_event={};port.salvos=[];port.view_delay=0
			port.refresh()
		var factor=float(JavaScriptBridge.eval("window.innerWidth"))/get_viewport_rect().size.x
		var buttons=[]
		for button in port.find_children("*","Button",true,false):
			if button.is_visible_in_tree():
				var point=button.get_global_rect().get_center()*factor;buttons.append({"text":button.text,"point":[point.x,point.y],"disabled":button.disabled})
		var chrome=port.chrome if game=="hockey" else OnKlavGame.chrome
		var state={"game":game,"buttons":buttons,"dialog":is_instance_valid(chrome.dialog),"paused":port.rink.paused if game=="hockey" else OnKlavGame.paused}
		if game=="hockey":
			var rect=port.rink.get_global_rect();state.rink=[rect.position.x*factor,rect.position.y*factor,rect.size.x*factor,rect.size.y*factor];state.touches=port.rink.touches;state.target=port.rink.target;state.player=port.rink.player.position.x;state.phase=port.state.phase
		elif game=="four":
			state.hover=port.board.hover;state.pressed=port.board.pressed;state.finger=port.board.finger;state.nodes=port.model.search_nodes;state.depth=port.model.search_depth;state.can_play=port.can_play();state.finished=port.model.finished;state.winner=port.model.winner;state.turn=port.model.turn;state.board=port.model.board;state.wins=port.model.match_wins
			var rect=port.board.grid();var transform=port.board.get_global_transform_with_canvas();var points=[]
			for col in range(7):
				var point=(transform*(rect.position+Vector2(rect.size.x*(col+.5)/7,rect.size.y*.5)))*factor;points.append([point.x,point.y])
			state.columns=points
		elif game=="blackjack":state.finished=port.model.finished;state.round=port.model.round;state.wins=port.model.wins
		elif game=="dominos":state.phase=port.model.phase;state.wins=port.model.match_wins
		elif game=="panda":state.phase=port.model.phase
		elif game=="naval":state.phase=port.model.phase
		JavaScriptBridge.eval("window.gameTestState="+JSON.stringify(state)))
	JavaScriptBridge.get_interface("window").gameTest=test_callback
'''
p.write_text(s)
p=f/'air_hockey/rink.gd';p.write_text(p.read_text().replace('func _input(event: InputEvent) -> void:', 'func _input(event: InputEvent) -> void:\n\tif event is InputEventScreenTouch or event is InputEventScreenDrag:JavaScriptBridge.eval(\"window.hockeyEvents=window.hockeyEvents||[];hockeyEvents.push(\"+JSON.stringify([event.get_class(),event.index,touches,paused,stopped])+\")\")'))
out.mkdir(parents=True,exist_ok=True)
bin=os.environ['GODOT_BIN']
for args in [['--editor','--import'],['--export-release','OnKlav Web',str(out/'index.html')]]:
 result=subprocess.run([bin,'--headless','--path',str(f),*args],capture_output=True,text=True);print(result.stdout,result.stderr)
 if result.returncode or 'SCRIPT ERROR:' in result.stdout+result.stderr:raise SystemExit(1)
