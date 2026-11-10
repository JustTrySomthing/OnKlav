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
	var game=load("res://main.gd").new();root.add_child(game)
	var session=root.get_node("OnKlavGame");session.new_game();await frames(30)
	var saved:Dictionary=JSON.parse_string(JSON.stringify(game.snapshot()))
	if game.player is CharacterBody2D:
		check(game.player.is_on_floor(),"platformer stands on real collision floor")
		var y:float=game.player.position.y;game.action("jump");await frames(10)
		check(game.player.position.y<y-20,"jump moves actual character upward")
		var x:float=game.player.position.x;game.controls.floating_stick=false
		game.controls.feed(1,game.controls.home(),true);game.controls.feed(1,game.controls.home()+Vector2(100,0));await frames(10)
		check(game.player.position.x>x+20,"touch stick moves actual character")
	else:
		check(game.player.is_on_floor(),"first person stands on real collision floor")
		game.action("fire");await frames(3);check(game.mask==2,"short fire tap ray hits centre target")
		game.player.position=Vector3(5,.05,4.3);await frames(3);game.interact();await frames(2)
		check(game.door_open,"use ray opens nearby door")
		game.action("jump");await frames(2);check(game.player.velocity.y>0,"first-person jump")
		game.look(Vector2(.3,.2));check(absf(game.player.rotation.y)>.2 and game.pitch<0,"look rotates camera")
	check(game.restore(saved),"JSON save restores")
	var before:Dictionary=game.snapshot();var bad:Dictionary=saved.duplicate(true);bad.position=[NAN,0]
	check(not game.restore(bad) and game.snapshot()==before,"bad save is rejected before mutation")
	game.controls.feed(1,game.controls.home(),true);game.controls.feed(1,game.controls.home()+Vector2(100,0));session.pause_game()
	check(game.controls.input_vector==Vector2.ZERO,"pause releases held movement")
	session.resume_game();game.queue_free();await process_frame
	print("ACTION_TEMPLATE_RESULT checks=%d failures=%d"%[checks,failed]);quit(1 if failed else 0)
