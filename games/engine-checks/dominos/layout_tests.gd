extends SceneTree
const Layout=preload("res://dominos/layout.gd")
var checks:=0
var failures:=0
func check(ok:bool,label:String):
	checks+=1
	if not ok:failures+=1;printerr("FAIL "+label)
func _init():
	for viewport in [Vector2(320,584),Vector2(360,664),Vector2(432,744)]:
		for finished in [false,true]:
			for count in range(22):
				var screen:=Layout.screen(viewport.x,viewport.y,count,finished)
				check(screen.hand.end.y<=viewport.y-(48 if finished else 0),"all rack rows visible above actions")
				check(screen.table.end.y<=screen.rack_label.position.y,"table and rack do not collide")
				for i in range(count):
					var r:=Layout.rack_rect(i,count,viewport.x,finished)
					check(Rect2(Vector2.ZERO,screen.hand.size).encloses(r.grow(-.01)),"every tile stays in the visible rack")
					check(r.size.x>=44 and (finished or r.size.y>=44),"touch targets stay usable")
					for j in range(i):check(not r.grow(-.01).intersects(Layout.rack_rect(j,count,viewport.x,finished)),"rack tiles never overlap")
				for total in range(1,29-count):
					var area:=Rect2(14,24,screen.table.size.x-28,screen.table.size.y-48)
					for i in range(total):
						var tile:=Layout.table_rect(area,i,total)
						check(area.encloses(tile.grow(-.01)),"auto-fit keeps the full chain visible")
						check(tile.size.x>=50,"table dominoes retain readable width across valid hand/chain partitions")
	print("NEON_LAYOUT_RESULT checks=%d failures=%d"%[checks,failures]);quit(0 if failures==0 else 1)
