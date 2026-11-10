extends RefCounted
## Collision-aware pursuit via CharacterBody move_and_slide. For obstacle mazes use grid_path.
static func chase_2d(body:CharacterBody2D,target:Vector2,speed:float,stop:float=20):
	var offset:=target-body.global_position
	body.velocity=offset.normalized()*maxf(0,speed) if offset.length()>stop else Vector2.ZERO
	body.move_and_slide()
static func chase_3d(body:CharacterBody3D,target:Vector3,speed:float,dt:float,stop:float=1):
	var offset:=target-body.global_position;offset.y=0
	var v:=offset.normalized()*maxf(0,speed) if offset.length()>stop else Vector3.ZERO
	body.velocity=Vector3(v.x,maxf(-20,body.velocity.y-18*dt),v.z);body.move_and_slide()
## Bounded four-neighbour BFS for tile maps. blocked contains Vector2i keys; [] means no route.
static func grid_path(start:Vector2i,goal:Vector2i,bounds:Rect2i,blocked:Dictionary,max_visits:int=1024)->Array[Vector2i]:
	var route:Array[Vector2i]=[]
	if not bounds.has_point(start) or not bounds.has_point(goal) or blocked.has(goal):return route
	var queue:Array[Vector2i]=[start];var previous:Dictionary={start:start};var head:=0
	while head<queue.size() and head<clampi(max_visits,1,4096):
		var current:=queue[head];head+=1
		if current==goal:
			while current!=start:route.push_front(current);current=previous[current]
			return route
		for direction in [Vector2i.LEFT,Vector2i.RIGHT,Vector2i.UP,Vector2i.DOWN]:
			var next:Vector2i=current+direction
			if bounds.has_point(next) and not blocked.has(next) and not previous.has(next):previous[next]=current;queue.append(next)
	return route
