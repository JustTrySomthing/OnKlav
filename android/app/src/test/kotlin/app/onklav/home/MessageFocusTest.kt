package app.onklav.home

import org.junit.Assert.*
import org.junit.Test

class MessageFocusTest {
    @Test fun exactMessageAndDateAreRequired() {
        val rows = listOf(Message(1, "Old", 100, false), Message(2, "Reply", 200, false))
        assertEquals(0, focusedMessageIndex(rows, "sms:1", 100))
        assertEquals(-1, focusedMessageIndex(rows, "sms:1", 300))
        assertEquals(-1, focusedMessageIndex(rows, "mms:1", 100))
    }
}
