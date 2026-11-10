extends RefCounted
## Bounded catch-up keeps 240 Hz saves/physics intact without slowing 15–20 FPS frames.
const STEP:=1.0/240.0
const MAX_STEPS:=48
const MAX_BACKLOG:=.2
var pending:=0.0
func reset():pending=0.0
func take(dt:float,span:int=1)->int:
	if not is_finite(dt) or dt<=0:return 0
	pending=minf(MAX_BACKLOG,pending+dt)
	var interval:=STEP*clampi(span,1,2)
	var count:=mini(MAX_STEPS,int((pending+.000000001)/interval))
	pending=maxf(0,pending-count*interval)
	return count

static func elapsed(previous_usec:int,now_usec:int,fallback:float)->float:
	if previous_usec>0 and now_usec>=previous_usec:return minf(MAX_BACKLOG,(now_usec-previous_usec)/1000000.0)
	return clampf(fallback,0,MAX_BACKLOG) if is_finite(fallback) else 0.0
