extends SceneTree
const Model = preload("res://naval_battle/model.gd")
const Table = preload("res://friend_table_model.gd")
var checks := 0
var failures := 0
func check(value:bool,message:String) -> void:
	checks+=1
	if not value:failures+=1;push_error("FAIL: "+message)
func _initialize() -> void:call_deferred("run")
func run() -> void:
	for i in range(100):
		var m=Model.new();m.shuffle_fleet();check(m.fleet_ready() and Model.legal_fleet(m.ships),"legal complete random fleet")
	var m=Model.new()
	check(not m.place(0,9,true),"no row wrapping")
	check(m.place(0,0,true) and not m.place(1,2,false),"no overlap")
	m.shuffle_fleet();check(m.ready() and m.phase=="battle","ready starts solo")
	var restored=Model.new();check(restored.restore(m.snapshot()),"lossless ready snapshot")
	var bad=m.snapshot().duplicate(true);bad.ships[1]=bad.ships[0].duplicate();check(not restored.restore(bad),"corrupt fleet rejected")
	var targets:=[]
	for ship in m.opponent:targets.append_array(ship)
	var safe:=[]
	var mine:=[]
	for ship in m.ships:mine.append_array(ship)
	for cell in range(100):
		if cell not in mine:safe.append(cell)
	for cell in targets:
		check(m.fire(cell),"new target accepted")
		check(not m.fire(cell),"pending shot blocks duplicates")
		var out:Dictionary=m.solo_result();check(out.get("hit",false),"correct hit result")
		if m.phase!="finished":check(not m.defend(int(safe.pop_back())).is_empty(),"defender reply passes turn")
	check(m.phase=="finished" and m.winner=="you" and Model.hit_count(m.shots)==17 and m.sunk.size()==17,"all five ships sunk wins")

	var old={"own_ships":[],"enemy_ships":[],"shots":[],"enemy_shots":[],"pending":true}
	for ship in m.ships:old.own_ships.append_array(ship)
	for ship in m.opponent:old.enemy_ships.append_array(ship)
	var migrated=Model.from_legacy(old);check(migrated!=null and migrated.phase=="battle" and not migrated.turn,"legacy pending AI turn retained")
	old.own_ships[0]=999;check(Model.from_legacy(old)==null,"bad legacy save left untouched")
	var host=Model.new();host.friend=true;host.host=true;host.shuffle_fleet();host.ready()
	var guest=Model.new();guest.friend=true;guest.host=false;guest.shuffle_fleet();guest.ready()
	host.peer_positioned();guest.peer_positioned()
	check(host.turn and not guest.turn,"inviter starts after both positioned")
	check(host.opponent.is_empty() and guest.opponent.is_empty(),"friend fleet stays private")
	var target:int=int(guest.ships[0][0]);check(host.fire(target),"friend shot queued")
	var answer:Dictionary=guest.defend(target);check(host.result(target,answer.hit,answer.proof),"friend result validates")
	check(not host.turn and guest.turn,"SMS result alternates turns")
	host.peer_positioned();check(not host.turn,"duplicate ready cannot reset turns")
	check(not host.result(target,true),"duplicate result rejected")
	var committed:Dictionary=host.shots.duplicate()
	check(not host.result(-1,true) and host.shots==committed,"no result without an outstanding shot")
	var damaged:Dictionary=host.snapshot().duplicate(true)
	damaged.shots["00"]="hit"
	var recovered=Model.new();recovered.friend=true
	check(not recovered.restore(damaged),"noncanonical shot key rejected")
	damaged=host.snapshot().duplicate(true)
	damaged.ships[0][0]="bad cell"
	check(not recovered.restore(damaged),"malformed fleet rejected without conversion")
	damaged=host.snapshot().duplicate(true)
	damaged.phase="finished";damaged.winner="you"
	check(not recovered.restore(damaged),"finished save requires a winning fleet count")
	damaged=host.snapshot().duplicate(true)
	var expected_ship:Array=damaged.ships[0].duplicate()
	damaged.ships[0].reverse()
	check(recovered.restore(damaged) and recovered.ships[0]==expected_ship,"older valid unsorted hulls restored in draw order")

	# Adjacent sunk ships retain separate, validated silhouettes through save/restore.
	var wreck=Model.new();wreck.shuffle_fleet();wreck.ready()
	wreck.opponent=[[0,1,2,3,4],[10,11,12,13],[20,21,22],[30,31,32],[40,41]]
	for n in [0,1,2,3,4,10,11,12,13]:
		wreck.turn=true;check(wreck.fire(n),"wreck target accepted");var outcome:Dictionary=wreck.solo_result()
		check(outcome.cell==n and outcome.has("proof"),"presentation receives exact cell and proof")
	check(wreck.wrecks==[[0,1,2,3,4],[10,11,12,13]],"touching ships do not merge into one wreck")
	var saved=Model.new();check(saved.restore(wreck.snapshot()) and saved.wrecks==wreck.wrecks,"red wreck outlines survive save/restore")
	var older:Dictionary=wreck.snapshot();older.erase("wrecks");check(saved.restore(older) and saved.wrecks==wreck.wrecks,"older solo save reconstructs only confirmed wrecks")
	var malformed:Dictionary=wreck.snapshot();malformed.wrecks=[[0,1,2,3,99]];check(not saved.restore(malformed),"unconfirmed wreck geometry rejected")
	malformed=wreck.snapshot();malformed.wrecks=[[0,1,2,3,4],[0,1]];check(not saved.restore(malformed),"overlapping wreck groups rejected")
	malformed=wreck.snapshot();malformed.wrecks="invalid";check(not saved.restore(malformed),"wrong wreck type is rejected without throwing")
	var loss=Model.new();loss.shuffle_fleet();loss.ready();loss.turn=false
	var strike:Dictionary=loss.defend(int(loss.ships[0][0]));check(strike.cell==loss.ships[0][0] and strike.hit and strike.proof.is_empty(),"incoming attack supplies its target before sinking")

	var four=Table.new("four",123)
	for move in [[0,"F0"],[1,"F1"],[0,"F0"],[1,"F1"],[0,"F0"],[1,"F1"],[0,"F0"]]:check(four.apply(move[0],move[1]),"legal four move")
	check(four.finished and four.winner==0,"four-in-row wins")
	check(not four.apply(1,"F3"),"no move after result")
	var a=Table.new("blackjack",456);var b=Table.new("blackjack",456)
	check(a.hands==b.hands and a.deck==b.deck,"identical blackjack deal")
	for hand in range(10):
		while not a.hand_over:
			var actor:int=a.turn
			check(a.apply(actor,"S") and b.apply(actor,"S"),"blackjack stand passes turn")
		if hand<9:check(a.apply(0,"N") and b.apply(0,"N"),"host starts next hand")
	check(a.finished and a.round==10 and a.wins==b.wins,"ten-hand friend tournament")
	print("NAVAL_TURN_RESULT checks=%d failures=%d"%[checks,failures]);quit(1 if failures else 0)
