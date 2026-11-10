extends RefCounted
const Rules = preload("res://rules.gd")
const LENGTHS := [5, 4, 3, 3, 2]
const NAMES := ["Carrier", "Battleship", "Cruiser", "Submarine", "Destroyer"]
var ships: Array = [[], [], [], [], []]
var opponent: Array = []
var shots: Dictionary = {}
var incoming: Dictionary = {}
var sunk: Array = []
var wrecks: Array = []
var pending := -1
var phase := "setup"
var turn := true
var own_ready := false
var peer_ready := false
var friend := false
var host := true
var winner := ""
var hunt: Array = []

func place(index: int, start: int, horizontal: bool) -> bool:
	if phase != "setup" or own_ready or index < 0 or index >= 5: return false
	var occupied := []
	for i in range(5):
		if i != index: occupied.append_array(ships[i])
	var cells := Rules.place_ship(occupied, start, LENGTHS[index], horizontal)
	if cells.is_empty(): return false
	ships[index] = cells
	return true

func fleet_ready() -> bool:
	var all := []
	for i in range(5):
		if ships[i].size() != LENGTHS[i]: return false
		for cell in ships[i]:
			if cell in all: return false
			all.append(cell)
	return true

func random_fleet() -> Array:
	for attempt in range(100):
		var fleet := []; var used := []
		for length in LENGTHS:
			var ship := []
			for trial in range(100):
				ship = Rules.place_ship(used, randi_range(0,99), length, randi_range(0,1) == 0)
				if not ship.is_empty(): break
			if ship.is_empty(): break
			fleet.append(ship); used.append_array(ship)
		if fleet.size() == 5: return fleet
	return [[0,1,2,3,4],[20,21,22,23],[40,41,42],[60,61,62],[80,81]]

func shuffle_fleet() -> void:
	if phase == "setup" and not own_ready: ships = random_fleet()

func ready() -> bool:
	if own_ready or not fleet_ready(): return false
	own_ready = true
	if not friend:
		peer_ready = true; opponent = random_fleet()
	begin_battle()
	return true

func peer_positioned() -> void:
	if peer_ready: return
	peer_ready = true
	begin_battle()

func begin_battle() -> void:
	if own_ready and peer_ready and phase in ["setup","waiting"]:
		phase = "battle"; turn = host if friend else true
	elif own_ready: phase = "waiting"

func fire(cell: int) -> bool:
	if phase != "battle" or not turn or pending >= 0 or cell < 0 or cell > 99 or shots.has(str(cell)): return false
	pending = cell; shots[str(cell)] = "pending"
	return true

func result(cell: int, hit: bool, proof: Array = []) -> bool:
	if pending != cell or phase != "battle": return false
	if not proof.is_empty():
		if not hit or proof.size() < 2 or proof.size() > 5 or cell not in proof: return false
		var ordered := proof.duplicate(); ordered.sort()
		var step: int = int(ordered[1]) - int(ordered[0])
		if step not in [1,10]: return false
		for i in range(ordered.size()):
			var n: int = int(ordered[i])
			if n < 0 or n > 99 or n in sunk or n != int(ordered[0]) + i * step or (step == 1 and n / 10 != int(ordered[0]) / 10): return false
			if n != cell and shots.get(str(n)) != "hit": return false
		if wrecks.filter(func(ship):return ship.size()==proof.size()).size()>=LENGTHS.count(proof.size()):return false
		sunk.append_array(ordered)
		wrecks.append(ordered.duplicate())
	shots[str(cell)] = "hit" if hit else "miss"
	pending = -1; turn = false
	if hit_count(shots) == 17: phase = "finished"; winner = "you"
	return true

func defend(cell: int) -> Dictionary:
	if phase != "battle" or turn or pending >= 0 or cell < 0 or cell > 99 or incoming.has(str(cell)): return {}
	var ship := []
	for candidate in ships:
		if cell in candidate: ship = candidate; break
	var hit := not ship.is_empty()
	incoming[str(cell)] = "hit" if hit else "miss"
	var proof := []
	if hit and ship.all(func(n): return incoming.get(str(n)) == "hit"): proof = ship.duplicate()
	turn = true
	if hit_count(incoming) == 17: phase = "finished"; winner = "them"
	return {"cell":cell,"hit":hit,"proof":proof,"sunk":not proof.is_empty()}

func solo_result() -> Dictionary:
	if friend or pending < 0: return {}
	var ship := []
	for candidate in opponent:
		if pending in candidate: ship = candidate; break
	var proof := []
	if not ship.is_empty() and ship.all(func(n): return n == pending or shots.get(str(n)) == "hit"): proof = ship.duplicate()
	var cell := pending; var hit := not ship.is_empty()
	if not result(cell, hit, proof): return {}
	return {"cell":cell,"hit":hit,"sunk":not proof.is_empty(),"proof":proof}

func ai_cell() -> int:
	while not hunt.is_empty():
		var n: int = int(hunt.pop_front())
		if n >= 0 and n < 100 and not incoming.has(str(n)): return n
	var choices := []
	for n in range(100):
		if not incoming.has(str(n)) and (n / 10 + n % 10) % 2 == 0: choices.append(n)
	if choices.is_empty():
		for n in range(100):
			if not incoming.has(str(n)): choices.append(n)
	return int(choices.pick_random()) if not choices.is_empty() else -1

func ai_fire() -> Dictionary:
	if friend or phase != "battle" or turn: return {}
	var cell := ai_cell(); var out := defend(cell)
	if out.get("hit",false) and out.get("proof",[]).is_empty():
		for n in [cell-10,cell+10]:
			if n >= 0 and n < 100: hunt.append(n)
		for n in [cell-1,cell+1]:
			if n >= 0 and n < 100 and n / 10 == cell / 10: hunt.append(n)
	elif not out.get("proof",[]).is_empty(): hunt.clear()
	out["cell"] = cell
	return out

static func hit_count(values: Dictionary) -> int:
	var hits := 0
	for value in values.values():
		if value == "hit": hits += 1
	return hits

func snapshot() -> Dictionary:
	return {"schema":1,"ships":ships,"opponent":opponent,"shots":shots,"incoming":incoming,"sunk":sunk,"wrecks":wrecks,"pending":pending,"phase":phase,"turn":turn,"own_ready":own_ready,"peer_ready":peer_ready,"friend":friend,"host":host,"winner":winner,"hunt":hunt}

static func legal_fleet(fleet: Variant, empty: bool = false) -> bool:
	if not fleet is Array or fleet.size() != 5: return false
	var used := []
	for i in range(5):
		if not fleet[i] is Array or (fleet[i].is_empty() and empty):
			if empty and fleet[i] is Array and fleet[i].is_empty(): continue
			return false
		if fleet[i].size() != LENGTHS[i]: return false
		var sorted: Array = fleet[i].duplicate(); sorted.sort()
		var step := int(sorted[1]) - int(sorted[0])
		if step not in [1,10]: return false
		for j in range(sorted.size()):
			var value: Variant = sorted[j]
			if not (value is int or value is float) or not is_finite(float(value)) or float(value) != int(value) or int(value) < 0 or int(value) > 99: return false
			var n := int(value)
			if n in used or n != int(sorted[0]) + j * step or (step == 1 and n / 10 != int(sorted[0]) / 10): return false
			used.append(n)
	return true

func restore(data: Dictionary) -> bool:
	if data.get("schema") != 1 or not legal_fleet(data.get("ships"),data.get("phase") == "setup"): return false
	if bool(data.get("friend")) != friend or bool(data.get("host")) != host: return false
	if not friend and bool(data.get("own_ready")) and not legal_fleet(data.get("opponent")): return false
	if data.get("phase") not in ["setup","waiting","battle","finished"]: return false
	for key in ["shots","incoming"]:
		if not data.get(key) is Dictionary or data[key].size() > 100: return false
		for n in data[key]:
			if not str(n).is_valid_int() or int(n) < 0 or int(n) > 99 or data[key][n] not in ["hit","miss","pending"]: return false
	var next_pending := int(data.get("pending",-1))
	if next_pending < -1 or next_pending > 99 or (next_pending >= 0 and data.shots.get(str(next_pending)) != "pending"): return false
	if not data.get("opponent") is Array or data.opponent.size()>5:return false
	for key in ["turn","own_ready","peer_ready","friend","host"]:
		if not data.get(key)is bool:return false
	if data.get("phase")in["battle","finished"] and (not data.own_ready or not data.peer_ready):return false
	if data.get("phase")=="waiting" and (not data.own_ready or data.peer_ready):return false
	if data.get("winner")not in["","you","them"]:return false
	if data.get("phase")=="finished" and data.winner=="":return false
	if not data.get("sunk") is Array or data.sunk.size() > 17 or not data.get("hunt") is Array or data.hunt.size() > 400: return false
	var seen:=[]
	for n in data.sunk:
		if not(n is int or n is float) or n!=floor(n) or n<0 or n>99 or n in seen or data.shots.get(str(int(n)))!="hit":return false
		seen.append(n)
	for n in data.hunt:
		if not(n is int or n is float) or n!=floor(n) or n<0 or n>99:return false
	if data.incoming.values().has("pending") or data.shots.values().count("pending")!=(1 if next_pending>=0 else 0):return false
	var next_wrecks:Variant=data.get("wrecks",[])
	if not next_wrecks is Array or next_wrecks.size()>5:return false
	var wreck_cells:=[];var wreck_lengths:=[]
	for wreck in next_wrecks:
		if not wreck is Array or wreck.size() not in LENGTHS:return false
		var ordered:Array=wreck.duplicate();ordered.sort()
		var stride:int=int(ordered[1])-int(ordered[0])
		if stride not in [1,10]:return false
		wreck_lengths.append(ordered.size())
		if wreck_lengths.count(ordered.size())>LENGTHS.count(ordered.size()):return false
		for i in range(ordered.size()):
			var n:Variant=ordered[i]
			if not(n is int or n is float) or n!=floor(n) or n not in data.sunk or n in wreck_cells or int(n)!=int(ordered[0])+i*stride or (stride==1 and int(n)/10!=int(ordered[0])/10):return false
			wreck_cells.append(n)
	if not data.has("wrecks") and not friend:
		for ship in data.opponent:
			if ship.all(func(n):return n in data.sunk):next_wrecks.append(ship.duplicate())
	wrecks=next_wrecks.duplicate(true)
	ships = data.ships.duplicate(true); opponent = data.opponent.duplicate(true); shots = data.shots.duplicate(); incoming = data.incoming.duplicate(); sunk = data.sunk.duplicate()
	pending = next_pending; phase = data.phase; turn = bool(data.turn); own_ready = bool(data.own_ready); peer_ready = bool(data.peer_ready); winner = str(data.winner); hunt = data.hunt.duplicate()
	return true

static func from_legacy(data:Dictionary):
	for key in ["own_ships","enemy_ships","shots","enemy_shots"]:
		if not data.get(key)is Array:return null
	if data.own_ships.size()!=17 or data.enemy_ships.size()!=17:return null
	var value=new();var index:=0;value.ships=[];value.opponent=[]
	for length in LENGTHS:
		value.ships.append(data.own_ships.slice(index,index+length));value.opponent.append(data.enemy_ships.slice(index,index+length));index+=length
	if not legal_fleet(value.ships) or not legal_fleet(value.opponent):return null
	for key in ["shots","enemy_shots"]:
		if data[key].size()>100:return null
		for n in data[key]:
			if not(n is int or n is float) or n!=floor(n) or n<0 or n>99:return null
	for n in data.shots:value.shots[str(int(n))]="hit" if n in data.enemy_ships else "miss"
	for n in data.enemy_shots:value.incoming[str(int(n))]="hit" if n in data.own_ships else "miss"
	for ship in value.opponent:
		if ship.all(func(n):return value.shots.get(str(int(n)))=="hit"):value.sunk.append_array(ship);value.wrecks.append(ship.duplicate())
	value.own_ready=true;value.peer_ready=true;value.phase="battle";value.turn=not bool(data.get("pending",false))
	if hit_count(value.shots)==17:value.phase="finished";value.winner="you"
	elif hit_count(value.incoming)==17:value.phase="finished";value.winner="them"
	return value
