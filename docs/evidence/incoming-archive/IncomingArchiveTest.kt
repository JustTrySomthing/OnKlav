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
@Config(sdk=[29], manifest=Config.NONE)
class IncomingArchiveTest {
    private val c: Context get() = ApplicationProvider.getApplicationContext()
    private fun ChatStore.setThread(id: Long, folder: String) = requestSync(
        JSONObject().put("action", "thread").put("key", id.toString()).put("folder", folder)
            .put("pinned", true).put("muted", true))

    @Test fun incomingTextReturnsArchivedThreadToInboxAndPreservesFlagsAcrossReload() {
        val store = ChatStore(c)
        store.setThread(71, "Archive")
        store.incomingThread(71, "+15550100001")
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
        store.incomingThread(72, "+15550100001")
        assertEquals("Inbox", store.requestSync(JSONObject().put("action", "snapshot")).thread(72).getString("folder"))
    }

    @Test fun blockedSendersAndTrashStayInTheirExistingFolders() {
        val store = ChatStore(c)
        store.setThread(73, "Archive")
        store.setThread(74, "Trash")
        store.requestSync(JSONObject().put("action", "blocked").put("address", "+15550100003").put("blocked", true))
        assertEquals("Archive", store.incomingThread(73, "+1 (555) 010-0003").thread(73).getString("folder"))
        assertEquals("Trash", store.incomingThread(74, "+15550100004").thread(74).getString("folder"))
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
