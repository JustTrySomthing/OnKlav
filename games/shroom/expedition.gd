extends RefCounted

const ENTRY = 7
const CELL = 10.0
const NEIGHBORS = [[1,3],[0,2,4],[1,5],[0,4,6],[1,3,5,7],[2,4,8],[3,7],[4,6,8],[5,7]]
const OBJECTIVES = ["Recover spore samples", "Reset ventilation relays", "Recover culture canisters", "Shut down root pumps", "Defeat the Cap King"]
const COUNTS = [3, 3, 4, 4, 0]

var paths := AStar2D.new()
var next_rooms := PackedInt32Array()
var targets: Array[int] = []
var exit_room := 0

func center(id: int) -> Vector3:
	return Vector3((id%3-1)*CELL, 0, (floori(id/3.0)-1)*CELL)

func room_at(p: Vector3) -> int:
	return clampi(roundi(p.x/CELL)+1,0,2)+3*clampi(roundi(p.z/CELL)+1,0,2)

func generate(seed_code: int, stage: int) -> void:
	var random := RandomNumberGenerator.new()
	random.seed = seed_code + stage*11731
	paths.clear()
	targets.clear()
	for i in range(9):
		var p := center(i)
		paths.add_point(i,Vector2(p.x,p.z))
	# The airlock has one forward door. Grow a connected layout beyond it.
	paths.connect_points(ENTRY,4)
	var visited := [ENTRY,4]
	while visited.size()<9:
		var frontier: Array[Vector2i] = []
		for a in visited:
			if a == ENTRY: continue
			for b in NEIGHBORS[a]:
				if b not in visited: frontier.append(Vector2i(a,b))
		var edge := frontier[random.randi_range(0,frontier.size()-1)]
		paths.connect_points(edge.x,edge.y)
		visited.append(edge.y)
	var rooms: Array[int] = []
	for i in range(9):
		if i not in [ENTRY,4]: rooms.append(i)
	for i in range(rooms.size()-1,0,-1):
		var j := random.randi_range(0,i)
		var swap := rooms[i]
		rooms[i]=rooms[j]
		rooms[j]=swap
	exit_room = rooms[0]
	for id in rooms:
		if paths.get_id_path(ENTRY,id).size()>paths.get_id_path(ENTRY,exit_room).size(): exit_room=id
	rooms.erase(exit_room)
	for i in range(COUNTS[stage-1]): targets.append(rooms[i])
	# The room graph stays fixed for the run. Resolve its routes once, before
	# combat, so the compass and every pursuing enemy can reuse the first hop.
	next_rooms.resize(81)
	for start in range(9):
		for destination in range(9):
			var route := paths.get_id_path(start,destination)
			next_rooms[start*9+destination] = route[1] if route.size()>1 else start

func waypoint(from: Vector3, destination: Vector3) -> Vector3:
	var start := room_at(from)
	var finish := room_at(destination)
	if start==finish: return destination
	var origin := center(start)
	var forward := (center(next_rooms[start*9+finish])-origin).normalized()
	var door := origin+forward*CELL*.5
	var side := (from-door)-forward*(from-door).dot(forward)
	return door-forward*1.2 if side.length()>1.0 and (from-door).dot(forward)<-0.85 else door+forward*1.05
