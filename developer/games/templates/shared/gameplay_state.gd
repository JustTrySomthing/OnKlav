extends RefCounted
## Serializable bounded RPG/action model. No file access: save through OnKlavGame.
signal changed
signal defeated
const LIMIT:=9999
var health:=100
var max_health:=100
var inventory:Dictionary={}
var flags:Dictionary={}
var cooldowns:Dictionary={}
var weapons:Array[String]=["blaster","heavy"]
var weapon:=0
var level:=1
var wave:=1
var score:=0
var checkpoint:=Vector3.ZERO
func tick(dt:float):
	if not is_finite(dt) or dt<0:return
	for key in cooldowns.keys():
		cooldowns[key]=maxf(0,float(cooldowns[key])-dt)
		if cooldowns[key]<=0:cooldowns.erase(key)
func ready_for(key:String,seconds:float)->bool:
	if key.is_empty() or key.length()>64 or not is_finite(seconds) or seconds<0 or seconds>3600 or cooldowns.get(key,0)>0:return false
	if not cooldowns.has(key) and cooldowns.size()>=64:return false
	cooldowns[key]=seconds;return true
func damage(amount:int)->int:
	if amount<=0 or health<=0:return 0
	var actual:=mini(amount,health);health-=actual;changed.emit()
	if health==0:defeated.emit()
	return actual
func heal(amount:int):
	if amount>0:health=mini(max_health,health+mini(amount,LIMIT));changed.emit()
func add_item(id:String,count:int=1)->bool:
	if id.is_empty() or id.length()>64 or count<=0 or (not inventory.has(id) and inventory.size()>=128):return false
	inventory[id]=mini(LIMIT,int(inventory.get(id,0))+mini(count,LIMIT));changed.emit();return true
func take_item(id:String,count:int=1)->bool:
	if count<=0 or inventory.get(id,0)<count:return false
	inventory[id]-=count
	if inventory[id]==0:inventory.erase(id)
	changed.emit();return true
func set_flag(id:String,value:bool=true)->bool:
	if id.is_empty() or id.length()>64 or (not flags.has(id) and flags.size()>=128):return false
	flags[id]=value;changed.emit();return true
func switch_weapon():weapon=(weapon+1)%weapons.size();changed.emit()
func respawn():health=max_health;cooldowns.clear();changed.emit()
func snapshot()->Dictionary:
	return {"schema":1,"health":health,"max_health":max_health,"inventory":inventory.duplicate(true),"flags":flags.duplicate(true),"cooldowns":cooldowns.duplicate(true),"weapon":weapon,"level":level,"wave":wave,"score":score,"checkpoint":[checkpoint.x,checkpoint.y,checkpoint.z]}
static func number(v:Variant,lo:float,hi:float,integer:bool=false)->bool:
	return (v is int or v is float) and is_finite(v) and v>=lo and v<=hi and (not integer or float(v)==floorf(v))
static func valid(d:Dictionary)->bool:
	if d.get("schema")!=1:return false
	for k in ["health","max_health","weapon","level","wave","score"]:
		if not number(d.get(k),0,LIMIT,true):return false
	if d.max_health<1 or d.health>d.max_health or d.weapon>1 or d.level<1 or d.wave<1:return false
	for spec in [["inventory",128],["flags",128],["cooldowns",64]]:
		if not d.get(spec[0]) is Dictionary or d[spec[0]].size()>spec[1]:return false
		for k in d[spec[0]]:
			if not k is String or k.is_empty() or k.length()>64:return false
			var v:Variant=d[spec[0]][k]
			if spec[0]=="flags":
				if not v is bool:return false
			elif not number(v,1 if spec[0]=="inventory" else 0,LIMIT if spec[0]=="inventory" else 3600,spec[0]=="inventory"):return false
	if not d.get("checkpoint") is Array or d.checkpoint.size()!=3:return false
	for v in d.checkpoint:
		if not number(v,-100000,100000):return false
	return true
func restore(d:Dictionary)->bool:
	if not valid(d):return false
	health=int(d.health);max_health=int(d.max_health);weapon=int(d.weapon);level=int(d.level);wave=int(d.wave);score=int(d.score)
	inventory=d.inventory.duplicate(true);flags=d.flags.duplicate(true);cooldowns=d.cooldowns.duplicate(true);checkpoint=Vector3(d.checkpoint[0],d.checkpoint[1],d.checkpoint[2]);changed.emit();return true
