package app.onklav.home

/** Provider IDs can be reused; a reminder must match the original date as well. */
internal fun focusedMessageIndex(messages: List<Message>, key: String, date: Long): Int =
    messages.indexOfFirst { it.key == key && (date == 0L || it.date == date) }
