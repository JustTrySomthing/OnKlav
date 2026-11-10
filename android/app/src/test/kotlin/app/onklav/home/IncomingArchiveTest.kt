package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class IncomingArchiveTest {
    private val c: Context get() = ApplicationProvider.getApplicationContext()
    private fun ChatStore.setThread(id: Long, folder: String) = requestSync(
        JSONObject().put("action", "thread").put("key", id.toString()).put("folder", folder)
            .put("pinned", true).put("muted", true))

    @Test fun incomingTextReturnsArchivedThreadToInboxAndPreservesFlagsAcrossReload() {
        val store = ChatStore(c)
        store.setThread(71, "Archive")
        MessageNotifications.incoming(c, 71, "+15550100001", "New text")
        val row = ChatStore(c).requestSync(JSONObject().put("action", "snapshot")).thread(71)
        assertEquals("Inbox", row.getString("folder"))
        assertTrue(row.getBoolean("pinned"))
        assertTrue(row.getBoolean("muted"))
    }

    @Test fun incomingPictureUsesTheSameResurfacingPathForGroupThreads() {
        val store = ChatStore(c)
        store.requestSync(JSONObject().put("action", "group_save").put("id", "a".repeat(32))
            .put("name", "Family").put("members", org.json.JSONArray(listOf("15550100001", "15550100002"))).put("thread", 72))
        store.setThread(72, "Archive")
        MessageNotifications.incoming(c, 72, "+15550100001", "Picture message")
        assertEquals("Inbox", store.requestSync(JSONObject().put("action", "snapshot")).thread(72).getString("folder"))
    }

    @Test fun blockedSendersStayInTheirExistingFolders() {
        val store = ChatStore(c)
        store.setThread(73, "Archive")
        store.setThread(74, "Trash")
        store.requestSync(JSONObject().put("action", "blocked").put("address", "+15550100003").put("blocked", true))
        assertEquals("Archive", store.incomingThread(73, "+1 (555) 010-0003").thread(73).getString("folder"))
        assertEquals("Trash", store.incomingThread(74, "+1 (555) 010-0003").thread(74).getString("folder"))
    }

    @Test fun incomingTextReturnsDeletedConversationToInboxWithoutLosingFlags() {
        val store = ChatStore(c)
        store.setThread(76, "Trash")
        MessageNotifications.incoming(c, 76, "+15550100006", "New text after deletion")
        val row = ChatStore(c).requestSync(JSONObject().put("action", "snapshot")).thread(76)
        assertEquals("Inbox", row.getString("folder"))
        assertTrue(row.getBoolean("pinned"))
        assertTrue(row.getBoolean("muted"))
    }

    @Test fun incomingPictureReturnsDeletedGroupConversationToInbox() {
        val store = ChatStore(c)
        store.requestSync(JSONObject().put("action", "group_save").put("id", "b".repeat(32))
            .put("name", "Family").put("members", org.json.JSONArray(listOf("15550100007", "15550100008"))).put("thread", 77))
        store.setThread(77, "Trash")
        MessageNotifications.incoming(c, 77, "+15550100007", "Picture message after deletion")
        assertEquals("Inbox", ChatStore(c).requestSync(JSONObject().put("action", "snapshot")).thread(77).getString("folder"))
    }

    @Test fun unblockingAllowsTheNextMessageToResurfaceTrash() {
        val store = ChatStore(c)
        store.setThread(78, "Trash")
        store.requestSync(JSONObject().put("action", "blocked").put("address", "+15550100008").put("blocked", true))
        assertEquals("Trash", store.incomingThread(78, "+1 (555) 010-0008").thread(78).getString("folder"))
        store.requestSync(JSONObject().put("action", "blocked").put("address", "5550100008").put("blocked", false))
        assertEquals("Inbox", store.incomingThread(78, "+15550100008").thread(78).getString("folder"))
    }

    @Test fun blockedArrivalsLeaveInboxAndNeverPostAnAlert() {
        val store = ChatStore(c)
        store.setThread(79, "Inbox")
        store.requestSync(JSONObject().put("action", "blocked").put("address", "+15550100009").put("blocked", true))
        MessageNotifications.incoming(c, 79, "+15550100009", "Blocked text")
        val row = store.requestSync(JSONObject().put("action", "snapshot")).thread(79)
        assertEquals("Archive", row.getString("folder"))
        assertTrue(row.getBoolean("pinned"))
        assertTrue(row.getBoolean("muted"))
        assertTrue(c.getSystemService(android.app.NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun blockedArrivalWithoutOldMetadataIsRetainedOutsideInbox() {
        val store = ChatStore(c)
        store.requestSync(JSONObject().put("action", "blocked").put("address", "+15550100010").put("blocked", true))
        assertEquals("Archive", store.incomingThread(80, "+15550100010").thread(80).getString("folder"))
        // A missing/invalid provider thread must never create metadata, even for a blocked sender.
        assertFalse(store.incomingThread(0, "+15550100010").child("threads").has("0"))
    }

    @Test fun inboxAndInvalidThreadsDoNotCreateOrRewriteMetadata() {
        val store = ChatStore(c)
        store.setThread(75, "Inbox")
        val before = store.requestSync(JSONObject().put("action", "snapshot")).toString()
        val epoch = ChatStore.epoch.value
        repeat(2) { store.incomingThread(75, "+15550100005") }
        store.incomingThread(0, "+15550100005")
        assertEquals(before, store.requestSync(JSONObject().put("action", "snapshot")).toString())
        assertEquals(epoch, ChatStore.epoch.value)
    }
}
