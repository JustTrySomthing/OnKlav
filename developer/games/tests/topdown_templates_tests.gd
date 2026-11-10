extends SceneTree
var checks:=0
var failed:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failed+=1;printerr("FAIL ",label)
func frames(n:int):
	for i in range(n):await physics_frame
func _init():call_deferred("run")
func run():
	var game=load("res://main.gd").new();root.add_child(game);var session=root.get_node("OnKlavGame");session.new_game();await frames(5)
	var initial:Dictionary=JSON.parse_string(JSON.stringify(game.snapshot()));check(game.enemies.size()>0,"live enemies spawned")
	var x:float=game.player.position.x;game.controls.floating_stick=false;game.controls.feed(1,game.controls.home(),true);game.controls.feed(1,game.controls.home()+Vector2(70,0));await frames(10);game.controls.reset_input();check(game.player.position.x>x+10,"touch moves actual body")
	game.player.position=game.pickup;await frames(2);check(game.state.inventory.get("potion",0)==1,"pickup enters inventory")
	game.state.damage(40);game.use_potion();check(game.state.health==95 and game.state.inventory.get("potion",0)==0,"potion consumed and heals")
	game.player.position=game.npc;game.interact();check(game.state.flags.get("quest",false),"NPC dialogue records quest")
	var e=game.enemies[0];e.position=game.player.position+Vector2(40,0);game.facing=Vector2.RIGHT;game.attack();game.state.tick(1);game.attack();check(not game.enemies.has(e),"combat removes enemy and awards loot")
	var saved:Dictionary=JSON.parse_string(JSON.stringify(game.snapshot()));check(game.restore(saved) and JSON.parse_string(JSON.stringify(game.snapshot()))==saved,"full save restores")
	var bad:Dictionary=saved.duplicate(true);bad.enemies=[{"position":[NAN,0],"hp":20}];var before:Dictionary=game.snapshot();check(not game.restore(bad) and game.snapshot()==before,"malformed save atomic rejection")
	session.pause_game();check(game.controls.input_vector==Vector2.ZERO,"pause clears input");session.resume_game()
	game.spawn_enemies(0)
	if game.ARENA:game.state.wave=3;await frames(2);check(game.state.flags.get("complete",false),"final wave completes dungeon")
	else:game.player.position=game.exit_point;game.interact();check(game.state.level==2 and not game.enemies.is_empty(),"exit advances room")
	game.restore(initial);game.queue_free();await process_frame;print("TOPDOWN_TEMPLATE_RESULT checks=%d failures=%d"%[checks,failed]);quit(1 if failed else 0)
