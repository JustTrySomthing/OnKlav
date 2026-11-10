extends "res://cat_dog/model.gd"
var match_wins:=[0,0]
var round_id:=1
func series_over()->bool:return maxi(match_wins[0],match_wins[1])>=3
func load_base(value:Variant)->bool:
	if not super.load_base(value):return false
	match_wins=[0,0];round_id=1
	if finished and winner>=0:match_wins[winner]=1
	return true
func apply(actor:int,data:String)->bool:
	if data=="N":
		if actor!=0 or not finished or tape.size()>=4096:return false
		if series_over():match_wins=[0,0];round_id=1
		else:round_id+=1
		board.fill(0);turn=0;winner=-1;finished=false
		tape.append({"actor":actor,"data":data});return true
	if not super.apply(actor,data):return false
	if finished and winner>=0:match_wins[winner]+=1
	return true
