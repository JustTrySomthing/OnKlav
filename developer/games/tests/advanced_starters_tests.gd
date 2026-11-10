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
	if game.player is CharacterBody2D:
		var x:float=game.moving.position.x;await frames(15);check(game.moving.position.x!=x,"platform actually moves")
		game.player.position=Vector2(250,430);await frames(2);check(game.state.checkpoint.x==250,"checkpoint activates")
		game.player.position=Vector2(850,520);var hp:int=game.state.health;await frames(2);check(game.state.health<hp and game.player.position.x<300,"hazard damages and respawns at checkpoint")
	else:
		check(game.enemies.size()==2,"FPS enemy bodies spawned")
		var first=game.enemies[0];var at:Vector3=first.position;await frames(10);check(first.position.distance_to(at)>.01,"FPS enemy pursues")
		game.state.damage(50);game.player.position=Vector3(-4,.1,4);await frames(2);check(game.state.health==85 and game.pickup_used,"medkit heals once")
		game.action("weapon");check(game.state.weapon==1,"weapon switches")
		for e in game.enemies:game.world.remove_child(e);e.queue_free()
		game.enemies.clear();game.mask=7;game.player.position=Vector3(5,.1,2);game.interact();check(game.state.level==2 and game.mask==0 and game.enemies.size()==3,"cleared exit starts next level")
	var prefs:Dictionary=game.controls.settings();prefs.left_handed=true;prefs.scale=1.15;game.controls.apply_settings(prefs)
	var saved:Dictionary=JSON.parse_string(JSON.stringify(game.snapshot()));check(game.restore(saved) and game.controls.left_handed,"preferences and full progress restore")
	game.controls.show_settings(game);check(session.paused and game.has_node("TouchPreferences"),"touch preferences pause gameplay")
	game.get_node("TouchPreferences").queue_free();session.resume_game();game.queue_free();await process_frame
	print("ADVANCED_STARTERS_RESULT checks=%d failures=%d"%[checks,failed]);quit(1 if failed else 0)
