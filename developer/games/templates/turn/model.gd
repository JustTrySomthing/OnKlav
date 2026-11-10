extends RefCounted
## Deterministic local turns. No network/carrier/session security is implied.
var board := [0,0,0,0,0,0,0,0,0]
var turn := 1
var revision := 0
var winner := 0
func play(cell: int, expected_revision: int, player: int) -> bool:
	if winner!=0 or revision>=9 or expected_revision!=revision or player!=turn or cell<0 or cell>=9 or board[cell]!=0:
		return false
	board[cell]=player;revision+=1
	for line in [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]]:
		if board[line[0]]==player and board[line[1]]==player and board[line[2]]==player:
			winner=player
	turn=3-turn
	return true
