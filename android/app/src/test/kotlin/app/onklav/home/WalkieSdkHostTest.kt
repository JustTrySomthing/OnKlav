package app.onklav.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WalkieSdkHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.readyClick(): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.waitUntil(15000) { pump();runCatching { assertIsEnabled();assertIsDisplayed() }.isSuccess }
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        pump()
        return this
    }
    private fun pump() { repeat(8) {
        // IO completions publish model state between virtual frames. Explicitly
        // flush snapshot notifications before advancing the paused host clock.
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
                run { // Android traverses newly created windows even without a pending Compose measure flag.
                    val decor = view.rootView
                    decor.forceLayout()
                    val display = compose.activity.resources.displayMetrics
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(display.widthPixels, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(display.heightPixels, android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    } }

    private fun waitFor(test:()->Boolean) {
        try { compose.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));pump();test() } }
        catch(e:Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { println(compose.onAllNodes(isRoot())[it].printToString()) }
            throw e
        }
    }

    private fun menu(label:String) {compose.onNodeWithContentDescription("More actions").readyClick();compose.onNodeWithText(label).readyClick()}
    @Test(timeout = 60000)
    @Config(qualifiers = "w320dp-h720dp-mdpi", fontScale = 1.3f)
    fun closeButtonAndQuickTogglesFitOnANarrowPanel() {
        var exits = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                OnKlavTheme(preview = app.onklav.shared.AppearanceSpec(dark = true, palette = 7)) {
                    SdkAppScreen("app.onklav.walkie", exit = { exits++ })
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val more = compose.onNodeWithContentDescription("More actions")
        val close = compose.onNodeWithContentDescription("Close Walkie-Talkie")
        val play = compose.onNodeWithTag("sdk-quick.auto_play")
        val send = compose.onNodeWithTag("sdk-quick.auto_send")
        waitFor { runCatching { play.assertExists(); send.assertExists() }.isSuccess }

        val playBounds = play.fetchSemanticsNode().boundsInRoot
        val sendBounds = send.fetchSemanticsNode().boundsInRoot
        assertEquals(playBounds.center.y, sendBounds.center.y, 1f)
        assertTrue(playBounds.right <= sendBounds.left)
        assertTrue(more.fetchSemanticsNode().boundsInRoot.right <= close.fetchSemanticsNode().boundsInRoot.left)

        val model = androidx.lifecycle.ViewModelProvider(compose.activity)["sdk-app.onklav.walkie", SdkAppModel::class.java]
        close.readyClick()
        waitFor { exits == 1 }
        assertEquals(0, model.frame.length())
    }

    @Test(timeout=60000) fun walkieSettingsOfflineDialAndRecipientStorageUseSignedSdk() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {AppSdk.bootstrap(context)}
        val installed=runBlocking {AppSdk.list(context)}.first {it.getJSONObject("manifest").getString("id")=="app.onklav.walkie"}
        assertFalse(installed.getJSONArray("grants").toString().contains("contacts.pick"))
        assertFalse(installed.getJSONArray("grants").toString().contains("communications.walkie"))
        AppSdk.sync(context,JSONObject().put("action","permissions").put("id","app.onklav.walkie").put("grants",org.json.JSONArray(listOf("storage.private","contacts.pick","communications.walkie"))))
        // Trusted host fixture only: valid structural AMR, not recorded/carrier audio.
        val bytes=org.json.JSONArray().apply { "#!AMR\n".toByteArray().forEach {put(it.toInt())};repeat(20) {put(60);repeat(31){put(0)}} }
        val binding=JSONObject().put("id","c".repeat(32)).put("session","a".repeat(32)).put("peer","+15550101234").put("subscription",2).put("outgoing",false)
        val ingestion=JSONObject().put("action","walkie_archive").put("args",JSONObject().put("operation","ingest").put("binding",binding).put("sequence",1).put("mime","audio/amr").put("bytes",bytes))
        AppSdk.sync(context,ingestion)

        compose.runOnUiThread {compose.activity.setContent {OnKlavTheme {OnKlavScreen()}}}
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        compose.onNodeWithTag("tab-Apps").readyClick()
        waitFor {compose.onAllNodesWithTag("app-icon-walkie").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("app-icon-walkie").readyClick()
        waitFor {compose.onAllNodesWithTag("sdk-dial").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Walkie Talkie Offline").assertIsNotEnabled()
        compose.onNodeWithTag("sdk-connect").assertDoesNotExist()
        menu("Voice Clips")
        waitFor {compose.onAllNodesWithTag("sdk-clip."+"c".repeat(32)).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Received · 0.4 seconds").assertExists()
        compose.onNodeWithContentDescription("Delete +15550101234").readyClick()
        waitFor {compose.onAllNodesWithText("Delete voice clips?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Delete +15550101234").readyClick()
        compose.onNodeWithText("Delete").readyClick()
        waitFor {compose.onAllNodesWithText("No saved voice clips").fetchSemanticsNodes().isNotEmpty()}
        assertTrue((AppSdk.sync(context,ingestion) as JSONObject).getBoolean("duplicate"))
        compose.onNodeWithContentDescription("Close Voice Clips").readyClick()

        menu("Walkie Talkie Settings")
        waitFor {compose.onAllNodesWithTag("sdk-settings.auto_send").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-settings.auto_send").readyClick()
        compose.onNodeWithTag("sdk-settings.save").readyClick()
        waitFor {compose.onAllNodesWithTag("sdk-settings.save").fetchSemanticsNodes().isEmpty()}
        // Real SDK effect -> Android picker launch -> selected provider row -> Rust persistence.
        val provider=SdkContactsTest.Provider()
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("sdk-contacts-fixture",provider)
        waitFor {compose.onAllNodesWithTag("sdk-status").fetchSemanticsNodes().any {it.config.toString().contains("Settings saved")}}
        compose.onNodeWithTag("sdk-contacts").readyClick()
        val activity=org.robolectric.Shadows.shadowOf(compose.activity)
        var picker: org.robolectric.shadows.ShadowActivity.IntentForResult?=null
        try {waitFor {if(picker==null) picker=activity.nextStartedActivityForResult;picker!=null}} catch(e:Throwable) {
            val m=androidx.lifecycle.ViewModelProvider(compose.activity)["sdk-app.onklav.walkie",SdkAppModel::class.java]
            println("Picker diagnostics: pending=${m.pending}, error=${m.error}, busy=${m.busy}, frame=${m.frame}")
            throw e
        }
        assertEquals(android.content.Intent.ACTION_PICK,picker!!.intent.action)
        assertEquals(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,picker!!.intent.data)
        compose.runOnUiThread {activity.receiveResult(picker!!.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.parse("content://sdk-contacts-fixture/data/7")))}
        waitFor {compose.onAllNodesWithTag("sdk-recipient.name").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-recipient.name").assertTextContains("Charles")
        compose.onNodeWithTag("sdk-recipient.phone").assertDoesNotExist()
        compose.onNodeWithTag("sdk-recipient.name").assertTextContains("Charles")
        compose.onNodeWithTag("sdk-connect").readyClick()
        waitFor {compose.onAllNodesWithTag("sdk-status").fetchSemanticsNodes().any { it.config.toString().contains("No invitation was sent") }}
        waitFor {compose.onAllNodesWithTag("sdk-connection.status").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-connection.status").assertTextContains("Connection unavailable")
        compose.onNodeWithTag("sdk-connection.peer").assertTextContains("Charles")
        compose.onNodeWithTag("sdk-connection.close").readyClick()
        waitFor {compose.onAllNodesWithTag("sdk-connection.close").fetchSemanticsNodes().isEmpty()}
        menu("Close")
        waitFor {compose.onAllNodesWithTag("app-icon-walkie").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("app-icon-walkie").readyClick()
        waitFor {compose.onAllNodesWithTag("sdk-dial").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-recipient.phone").assertDoesNotExist()
        compose.onNodeWithTag("sdk-recipient.name").assertTextContains("Charles")
        menu("Walkie Talkie Settings")
        waitFor {compose.onAllNodesWithTag("sdk-settings.auto_send").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-settings.auto_send").assertIsOff()
        compose.onNodeWithTag("sdk-settings.close").readyClick()
        menu("Recent Recipients")
        waitFor {compose.onAllNodesWithTag("sdk-history.clear").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-history.clear").readyClick()
        waitFor {compose.onAllNodesWithText("Clear recent recipients?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Clear").readyClick()
        waitFor {compose.onAllNodesWithText("Clear recent recipients?").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithContentDescription("Close Recent Recipients").readyClick()
    }
}
