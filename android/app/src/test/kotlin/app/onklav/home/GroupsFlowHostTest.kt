package app.onklav.home

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.OnKlavTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver

/** Real Compose -> ChatStore -> production Rust JNI, with fixture contact rows only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class GroupsFlowHostTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    // Robolectric's offscreen dialog window needs its traversal driven explicitly.
    // The phone WindowManager performs this measure/layout; no production UI is mocked.
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.readyClick(): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.waitUntil(15000) { pump();runCatching { assertIsEnabled();assertIsDisplayed() }.isSuccess }
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        pump()
        return this
    }
    private fun pump() { repeat(8) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread {
            val env = compose.javaClass.getDeclaredField("environment").apply { isAccessible = true }.get(compose)
            val base = requireNotNull(env.javaClass.superclass)
            val registry = base.getDeclaredField("composeRootRegistry").apply { isAccessible = true }.get(env)
            val roots = registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach { root ->
                val view = root.javaClass.getMethod("getView").invoke(root) as android.view.View
                run { // Newly opened offscreen windows also need a traversal when no Compose pass is pending.
                    val decor = view.rootView
                    decor.forceLayout()
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(936, android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    } }
    private fun waitForContact(name:String){
        try{compose.waitUntil(10000){pump();compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}}
        catch(e:Throwable){
            val tree=runCatching{val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true);roots.fetchSemanticsNodes().indices.joinToString("\n"){roots[it].printToString()}}.getOrElse{it.toString()}
            throw AssertionError("Contact '$name' absent after fixture query; screen state:\n$tree",e)
        }
    }
    class ContactsFixture : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            return MatrixCursor(arrayOf("display_name", "data1")).apply {
                addRow(arrayOf("Alice", "+15555550101")); addRow(arrayOf("Bob", "+15555550102")); addRow(arrayOf("Carol", "+15555550103"))
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
    @Test fun failedContactLoadOffersRetryWithoutPretendingTheAddressBookIsEmpty() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        kotlinx.coroutines.runBlocking { AppSdk.bootstrap(context) }
        Shadows.shadowOf(context.applicationContext as Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider=RetryContactsFixture()
        ShadowContentResolver.registerProviderInternal("com.android.contacts",provider)
        var picked:List<String>?=null
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { GroupPeopleChooser(emptyList(),{}, {picked=it}) } } }
        compose.mainClock.autoAdvance=false
        compose.waitUntil(10000) { pump();compose.onAllNodesWithText("Try Again").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No matching contacts").assertDoesNotExist()
        provider.reject=false
        compose.onNodeWithText("Try Again").readyClick()
        waitForContact("Alice")
        compose.onNodeWithText("Alice").readyClick()
        compose.onNodeWithText("Done").readyClick()
        compose.waitUntil(10000) {pump();picked!=null}
        assertEquals(listOf("+15555550101"),picked)
    }
    class RetryContactsFixture:ContentProvider() {
        @Volatile var reject=true
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {
            check(!reject){"Fixture contacts temporarily unavailable"}
            return MatrixCursor(arrayOf("display_name","data1")).apply{addRow(arrayOf("Alice","+15555550101"))}
        }
        override fun getType(uri:Uri):String?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
    }
    @Test(timeout = 120000) fun createEditMembersFoldersSearchAndDelete() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Finish SDK initialization before replacing the activity composition.
        kotlinx.coroutines.runBlocking { AppSdk.bootstrap(context) }
        Shadows.shadowOf(context.applicationContext as Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        ShadowContentResolver.registerProviderInternal("com.android.contacts", ContactsFixture())
        val store = ChatStore(context)
        fun groups() = store.requestSync(JSONObject().put("action", "snapshot")).savedGroups()
        groups().forEach { store.requestSync(JSONObject().put("action", "group_remove").put("id", it.id)) }
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { OnKlavScreen() } } }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-Groups").readyClick()
        compose.mainClock.autoAdvance = false; pump()
        compose.waitUntil(15000) { pump();compose.onAllNodesWithContentDescription("Create Group").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Create Group").readyClick(); pump()
        compose.onNodeWithTag("group-name").performTextInput("Family"); pump()
        compose.onNodeWithText("Save").readyClick(); pump()
        compose.onNodeWithText("Choose 2–10 different people").assertExists()
        compose.onNodeWithText("Choose Members").readyClick(); pump()
        waitForContact("Alice")
        compose.onNodeWithText("Alice").readyClick(); pump()
        compose.onNodeWithText("Bob").readyClick(); pump()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Done").readyClick(); pump()
        compose.onNodeWithText("2 People · Choose 2–10").assertExists()
        compose.onNodeWithText("Save").readyClick(); pump()
        compose.waitUntil(10000) { pump(); groups().size == 1 }
        val id = groups().single().id
        compose.onNodeWithTag("group-$id").assertExists()
        compose.onNodeWithTag("group-$id").readyClick();pump()
        compose.waitUntil(10000) { pump(); compose.onAllNodesWithContentDescription("Family. View group members").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Family. View group members").readyClick(); pump()
        waitForContact("Alice")
        compose.onNodeWithText("3 members · including you").assertExists()
        compose.onNodeWithText("You").assertExists()
        compose.onNodeWithText("Alice").assertExists()
        compose.onNodeWithContentDescription("Close members").readyClick(); pump()
        compose.onNodeWithTag("chat-activities").readyClick();pump()
        waitForContact("Alice")
        compose.onNodeWithText("You").assertDoesNotExist()
        compose.onNodeWithText("Alice").readyClick();pump()
        compose.onNodeWithText("With Alice").assertExists()
        compose.onNodeWithTag("activity-game").readyClick();pump()
        compose.onNodeWithText("Play a Game with Alice").assertExists()
        compose.onNodeWithText("Cancel").readyClick();pump()
        compose.onNodeWithTag("chat-activities").assertExists()

        compose.onNodeWithContentDescription("Close").readyClick(); pump()
        compose.waitUntil(10000) { pump(); compose.onAllNodesWithContentDescription("Edit Family").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Edit Family").readyClick(); pump()
        compose.onNodeWithTag("group-name").performTextReplacement("Friends"); pump()
        compose.onNodeWithText("Choose Members").readyClick(); pump()
        waitForContact("Carol")
        compose.onNodeWithText("Bob").readyClick(); pump(); compose.onNodeWithText("Carol").readyClick(); pump()
        compose.onNodeWithText("Done").readyClick(); pump()
        compose.onNodeWithContentDescription("Group Notifications").readyClick(); pump()
        compose.onNodeWithContentDescription("Unread conversation").readyClick(); pump()
        compose.onNodeWithText("Save").readyClick(); pump()
        compose.waitUntil(10000) { pump(); groups().singleOrNull()?.name == "Friends" }
        assertEquals(listOf("+15555550101", "+15555550103"), groups().single().members)
        compose.waitUntil(10000) { pump(); compose.onAllNodesWithContentDescription("Pin Friends").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Pin Friends").readyClick(); pump()
        compose.waitUntil(10000) { pump(); store.requestSync(JSONObject().put("action", "snapshot")).child("threads").child(groups().single().key).optBoolean("pinned") }
        assertTrue(store.requestSync(JSONObject().put("action", "snapshot")).child("threads").child(groups().single().key).optBoolean("muted"))
        compose.onNodeWithTag("group-$id").performTouchInput { longClick() }; pump()
        compose.onNodeWithText("Move to Archive").readyClick(); pump()
        compose.onNodeWithText("Archive", useUnmergedTree = false).readyClick(); pump()
        compose.waitUntil(10000) { pump(); compose.onAllNodesWithContentDescription("Edit Friends").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mailbox-search").performTextInput("Missing group"); pump()
        compose.onNodeWithText("No matching groups").assertExists()
        compose.onNodeWithTag("mailbox-search").performTextClearance(); pump()
        compose.onNodeWithContentDescription("Select multiple conversations").readyClick(); pump()
        compose.onNodeWithTag("group-$id").readyClick(); pump()
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Restore").readyClick(); pump()
        compose.onNodeWithText("Inbox").readyClick(); pump()
        compose.waitUntil(10000) { pump(); compose.onAllNodesWithContentDescription("Edit Friends").fetchSemanticsNodes().isNotEmpty() }
        // Capture the actual framework-rendered list for layout review.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val root = java.io.File(requireNotNull(System.getProperty("onklav.project.root")))
            val out = java.io.File(root, "docs/evidence/v0.9.3/groups-inbox.png"); out.parentFile!!.mkdirs()
            out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        compose.onNodeWithTag("group-$id").performTouchInput { longClick() }; pump()
        compose.onNodeWithText("Move to Trash").readyClick(); pump()
        compose.onNodeWithText("Trash").readyClick(); pump()
        compose.waitUntil(10000) { pump(); compose.onAllNodesWithText("Empty trash").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Empty trash").readyClick(); pump()
        compose.onNodeWithText("Delete Group").readyClick(); pump()
        compose.waitUntil(10000) { pump(); groups().isEmpty() }
        assertFalse("MMS acceptance gate stays closed", MessagingTransport.MMS_READY)
        assertFalse(MessagingTransport.role(context))
    }
}
