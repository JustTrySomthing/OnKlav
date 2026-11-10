extends Button
## Reuse with typed card IDs. The receiving pile must validate the move.
signal card_dropped(card_id: int)
var card_id := -1
var accepts: Callable

func _get_drag_data(_position: Vector2) -> Variant:
	if card_id < 0 or disabled:
		return null
	var preview := Label.new()
	preview.text = text
	set_drag_preview(preview)
	return {"onklav_card": card_id}

func _can_drop_data(_position: Vector2, data: Variant) -> bool:
	return not disabled and data is Dictionary and data.get("onklav_card") is int and accepts.is_valid() and accepts.call(data.onklav_card)

func _drop_data(position: Vector2, data: Variant) -> void:
	if _can_drop_data(position, data):
		card_dropped.emit(data.onklav_card)
