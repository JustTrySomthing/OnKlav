extends "res://dominos/model.gd"
const ROUND_TARGET := 3
const SET_PIPS := 168
var pip_scores := [0,0]
var deductions := [0,0]
var round_results: Array = []
var round_winner := -2

func deal(seed_id: int):
	super.deal(seed_id)
	pip_scores = [0,0]
	deductions = [0,0]
	round_winner = -2

static func played_pips(tiles: Array) -> Array:
	var totals := [0,0]
	for tile in tiles:
		totals[tile[3]] += tile[1]+tile[2]
	return totals

static func pip_winner(totals: Array) -> int:
	return -1 if totals[0]==totals[1] else 0 if totals[0]>totals[1] else 1

func apply(actor: int, kind: String, id: int=-1, end: int=0) -> bool:
	if not super.apply(actor,kind,id,end): return false
	if kind=="play" and phase=="playing":
		var pair := faces(id)
		pip_scores[actor] += pair.x+pair.y
	return true

func begin(seed_id: int):
	round_results.clear()
	super.begin(seed_id)

func settle_round() -> void:
	deductions = [pips(hands[0]),pips(hands[1])]
	pip_scores = played_pips(chain)
	for side in range(2):
		pip_scores[side] -= deductions[side]
	round_winner = pip_winner(pip_scores)
	round_results.append(pip_scores.duplicate())
	phase = "match_over" if round_id==ROUND_TARGET else "round_over"

func match_totals() -> Array:
	var totals := [0,0]
	for result in round_results:
		for side in range(2):
			totals[side] += result[side]
	return totals

func finish(actor: int, points: int, locked: bool):
	super.finish(actor,points,locked)
	settle_round()

func next_round() -> bool:
	if phase!="round_over": return false
	scores = [0,0]
	return super.next_round()

func snapshot() -> Dictionary:
	var hand := super.snapshot()
	# The base model still validates the deal and its original ending condition.
	hand.phase = "playing" if phase=="playing" else "match_over" if maxi(scores[0],scores[1])>=TARGET else "round_over"
	return {"tournament_schema":3,"completed":round_results.slice(0,round_id-1).duplicate(true),"hand":hand}

func restore(value: Dictionary) -> bool:
	var wrapped := value.has("tournament_schema")
	if wrapped and not integer(value.tournament_schema,1,3): return false
	var hand: Variant = value.get("hand") if wrapped else value
	if not hand is Dictionary: return false
	var probe := preload("res://dominos/model.gd").new()
	if not probe.restore(hand): return false
	var completed: Array = []
	if value.get("tournament_schema")==3:
		var saved: Variant = value.get("completed")
		if probe.round_id>ROUND_TARGET or not saved is Array or saved.size()!=probe.round_id-1: return false
		for result in saved:
			if not result is Array or result.size()!=2: return false
			if not integer(result[0],-SET_PIPS,SET_PIPS) or not integer(result[1],-SET_PIPS,SET_PIPS): return false
			if absi(int(result[0]))+absi(int(result[1]))>SET_PIPS: return false
			completed.append([int(result[0]),int(result[1])])
	if not super.restore(hand): return false
	# Older saves recorded round wins only. Keep the current deal as round one.
	if value.get("tournament_schema")!=3:
		round_id = 1
		scores = [0,0]
		if winner>=0: scores[winner] = award
	round_results = completed
	round_winner = -2
	deductions = [0,0]
	pip_scores = played_pips(chain)
	if phase!="playing": settle_round()
	return true
