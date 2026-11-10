package app.onklav.home

import org.junit.Assert.*
import org.junit.Test

class MessageIdentityTest {
    @Test
    fun contactFormattingAndUsPrefixShareDraft() {
        assertEquals(messageDraftKey("+1 (555) 555-0123"), messageDraftKey("555-555-0123"))
    }

    @Test
    fun groupOrderingAndDuplicateRecipientsDoNotForkDraft() {
        assertEquals(messageDraftKey("5550102;5550103"), messageDraftKey("5550103,5550102;5550102"))
    }

    @Test
    fun differentInternationalRecipientsKeepSeparateDrafts() {
        assertNotEquals(messageDraftKey("+44 20 7123 4567"), messageDraftKey("+33 20 7123 4567"))
        assertEquals(messageDraftKey("+442071234567"), messageDraftKey("+44 (20) 7123-4567"))
    }
}
