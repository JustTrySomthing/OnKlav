package app.onklav.home

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessengerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val c
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun shot(name: String) {
        compose.onNodeWithTag("messenger-screen").captureToImage().asAndroidBitmap().let { b ->
            File(c.cacheDir, "qa-$name.png").outputStream().use {
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun draftsFoldersFlagsAndSetupUseRust() {
        assertFalse(
            "The development build must not take over default SMS",
            MessagingTransport.role(c),
        )
        val codecId = MessageTransferProvider.id()
        MessagingTransport.codec(
            c,
            JSONObject()
                .put("action", "compose")
                .put("id", codecId)
                .put("recipients", org.json.JSONArray(listOf("5550102")))
                .put("text", "Hello Rust MMS 🐈")
                .put("parts", org.json.JSONArray())
                .put("date", 1000),
        )
        val parsed =
            MessagingTransport.codec(c, JSONObject().put("action", "parse").put("id", codecId))
        assertEquals(128, parsed.getInt("kind"))
        assertTrue(parsed.getJSONArray("parts").toString().contains("Hello Rust MMS 🐈"))
        val uri = MessageTransferProvider.uri(c, codecId, "pdu", "application/vnd.wap.mms-message")
        assertTrue(
            c.contentResolver
                .openInputStream(uri)!!
                .use { it.readBytes() }
                .contentEquals(MessageTransferProvider.file(c, codecId, "pdu").readBytes())
        )
        c.getSharedPreferences("conversation_folders", 0)
            .edit()
            .putString("90002", "Trash")
            .commit()
        c.getSharedPreferences("messenger_migration", 0)
            .edit()
            .putBoolean("folders", false)
            .commit()
        val store = ChatStore(c)
        assertEquals(
            "Trash",
            store
                .requestSync(JSONObject().put("action", "snapshot"))
                .thread(90002)
                .optString("folder"),
        )
        store.requestSync(
            JSONObject().put("action", "draft").put("key", "qa").put("text", "Hello Rust 🐈")
        )
        assertEquals(
            "Hello Rust 🐈",
            store
                .requestSync(JSONObject().put("action", "snapshot"))
                .child("drafts")
                .getString("qa"),
        )
        store.requestSync(
            JSONObject()
                .put("action", "thread")
                .put("key", "90001")
                .put("folder", "Archive")
                .put("pinned", true)
        )
        assertTrue(
            store
                .requestSync(JSONObject().put("action", "snapshot"))
                .thread(90001)
                .getBoolean("pinned")
        )
        val providerRows=kotlinx.coroutines.runBlocking{MessageRepository(c).conversations()}
        assertTrue("Canonical thread lookup must use the real combined SMS/MMS endpoint",providerRows.any{sameRecipients(it.address,"5550102")&&it.count>0})
        compose.onNodeWithContentDescription("Enter Phone Number").performClick()
        compose.onNode(hasSetTextAction() and hasText("Phone Number")).performTextInput("5550102")
        compose.onNodeWithText("Open", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Feed").assertIsDisplayed()
        compose.onNodeWithText("Gallery").performClick()
        compose.onNodeWithText("Photos and attachments appear here.").assertIsDisplayed()
        compose.onNodeWithText("Starred", substring = false).performClick()
        compose.onNodeWithText("Star a message to keep it here.").assertIsDisplayed()
        compose.onNodeWithText("Feed").performClick()
        compose.waitUntil(15000) {
            compose
                .onAllNodesWithText("OnKlav QA inbound message")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Star Message").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithContentDescription("Unstar Message")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Starred").performClick()
        compose.onNodeWithText("OnKlav QA inbound message").assertIsDisplayed()
        compose.onNodeWithText("Feed").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Unsent durable draft 🐈")
        compose.waitForIdle()
        back()
        compose.onNodeWithContentDescription("Enter Phone Number").performClick()
        compose.onNode(hasSetTextAction() and hasText("Phone Number")).performTextInput("5550102")
        compose.onNodeWithText("Open", useUnmergedTree = true).performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Unsent durable draft 🐈").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Emoji").performClick()
        compose.onNodeWithText("Search Emoji").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        shot("messenger-draft")
        compose.onNodeWithContentDescription("Mute Conversation").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithContentDescription("Unmute Conversation")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Pin Conversation").performClick()
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithContentDescription("Unpin Conversation")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        back()
        compose.onNodeWithText("5550102", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Move to Archive").performClick()
        compose.onNodeWithTag("folder-Archive").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("5550102").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("5550102", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("Move to Inbox").performClick()
        compose.onNodeWithTag("folder-Inbox").performClick()
        compose.onNodeWithTag("tab-Settings").performClick()
        compose
            .onNode(hasText("Setup") and hasAnyAncestor(hasTestTag("settings-pane")))
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("messenger-setup").assertIsDisplayed()
        compose.onNodeWithText("Sending SIM").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("My Number", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        back()
        compose
            .onNode(hasText("Help") and hasAnyAncestor(hasTestTag("settings-pane")))
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("OnKlav 0.9.0-dev", substring = true).assertIsDisplayed()
    }
}
