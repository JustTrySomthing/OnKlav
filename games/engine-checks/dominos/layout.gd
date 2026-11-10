extends RefCounted
## Pure geometry shared by presentation and bounded phone-size checks.
static func rack(width:float,count:int,finished:bool=false)->Dictionary:
	var available:=width-24
	var maximum:=maxi(1,floori((available+6)/(58 if count>14 else 70)))
	var rows:=maxi(1,ceili(float(maxi(1,count))/maximum))
	var columns:=maxi(1,ceili(float(maxi(1,count))/rows))
	var cell_width:=minf(96,(available-(columns-1)*6)/columns)
	var cell_height:=38.0 if finished else 44.0 if count>10 else 52.0
	return {"rows":rows,"columns":columns,"width":cell_width,"height":cell_height,"extent":rows*cell_height+(rows-1)*4}
static func rack_rect(index:int,count:int,width:float,finished:bool=false)->Rect2:
	var spec:=rack(width,count,finished);var row:int=index/int(spec.columns);var col:int=index%int(spec.columns)
	var in_row:=mini(int(spec.columns),count-row*int(spec.columns))
	var used:float=in_row*spec.width+(in_row-1)*6
	return Rect2((width-24-used)*.5+col*(spec.width+6),row*(spec.height+4),spec.width,spec.height)
static func table_plan(area:Rect2,count:int)->Dictionary:
	var best:=-1.0;var columns:=1;var tile_width:=80.0
	for cols in range(1,mini(7,maxi(1,count))+1):
		var rows:=ceili(float(maxi(1,count))/cols)
		var w:=minf(104,minf((area.size.x-(cols-1)*8)/cols,(area.size.y-(rows-1)*8)/rows/.53))
		var score:=w-rows*.04
		if score>best:best=score;columns=cols;tile_width=w
	return {"columns":columns,"rows":ceili(float(maxi(1,count))/columns),"width":maxf(12,tile_width),"height":maxf(12,tile_width)*.53}
static func table_rect(area:Rect2,index:int,count:int)->Rect2:
	return table_rect_with_plan(area,index,table_plan(area,count))
static func table_rect_with_plan(area:Rect2,index:int,plan:Dictionary)->Rect2:
	var column:int=index%int(plan.columns);var row:int=index/int(plan.columns)
	if row%2==1:column=int(plan.columns)-1-column
	var used:=Vector2(plan.columns*plan.width+(plan.columns-1)*8,plan.rows*plan.height+(plan.rows-1)*8)
	return Rect2(area.position+(area.size-used)*.5+Vector2(column*(plan.width+8),row*(plan.height+8)),Vector2(plan.width,plan.height))

static func screen(width:float,height:float,count:int,finished:bool=false)->Dictionary:
	var spec:=rack(width,count,finished)
	var header:=28.0 if finished else 44.0
	var footer:=48.0 if finished else 0.0
	var rack_y:float=height-spec.extent-header-footer-8
	var top:=136.0
	return {"table":Rect2(12,top,width-24,maxf(72,rack_y-top-8)),"hand":Rect2(12,rack_y+header,width-24,spec.extent),"rack_label":Rect2(16,rack_y+8,width-32,22),"draw":Rect2(width-154,rack_y,142,44),"next":Rect2(12,height-48,width-24,44)}
