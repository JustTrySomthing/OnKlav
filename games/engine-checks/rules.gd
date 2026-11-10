extends RefCounted

static func four_winner(board: Array, column: int, row: int) -> int:
	var player: int = board[row * 7 + column]
	if player == 0:
		return 0
	for direction in [Vector2i(1,0),Vector2i(0,1),Vector2i(1,1),Vector2i(1,-1)]:
		var count := 1
		for sign_value in [-1,1]:
			var point: Vector2i = Vector2i(column,row) + direction * sign_value
			while point.x >= 0 and point.x < 7 and point.y >= 0 and point.y < 6 and board[point.y * 7 + point.x] == player:
				count += 1
				point += direction * sign_value
		if count >= 4:
			return player
	return 0

static func blackjack_total(cards: Array) -> int:
	var score := 0
	var aces := 0
	for card in cards:
		var rank: int = int(card) % 13 + 1
		if rank == 1:
			score += 11
			aces += 1
		else:
			score += mini(rank,10)
	while score > 21 and aces > 0:
		score -= 10
		aces -= 1
	return score

static func place_ship(occupied: Array, start: int, length: int, horizontal: bool) -> Array:
	var cells := []
	for step in range(length):
		var cell: int = start + step * (1 if horizontal else 10)
		if cell < 0 or cell >= 100 or (horizontal and cell / 10 != start / 10) or cell in occupied:
			return []
		cells.append(cell)
	return cells
