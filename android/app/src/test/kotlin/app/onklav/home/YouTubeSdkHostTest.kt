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
class YouTubeSdkHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.readyClick(): androidx.compose.ui.test.SemanticsNodeInteraction {
        try { compose.waitUntil(15000) { pump();runCatching { assertIsEnabled();assertIsDisplayed() }.isSuccess } }
        catch(e:Throwable){
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)["sdk-app.onklav.youtube",SdkAppModel::class.java]
            val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true)
            throw AssertionError("YouTube action unavailable: "+runCatching{printToString()}.getOrDefault("missing")+"\nmodel busy="+model.busy+" pending="+model.pending+" error="+model.error+" frame="+model.frame+"\n"+roots.fetchSemanticsNodes().indices.joinToString("\n"){roots[it].printToString()},e)
        }
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
                run { // Android traverses newly created windows even without a pending Compose measure flag.
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

    private fun waitFor(test:()->Boolean) {
        try { compose.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));pump();test() } }
        catch(e:Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { println(compose.onAllNodes(isRoot())[it].printToString()) }
            throw e
        }
    }

    @Test(timeout=120000) fun homeStartsOnAppsAndKeepsSelectedTabAfterBackground() {
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        waitFor { compose.onAllNodesWithTag("app-icon-youtube").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-Groups").readyClick()
        compose.onNodeWithTag("tab-Groups").assertIsSelected()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        pump()
        compose.onNodeWithTag("tab-Groups").assertIsSelected()
        compose.activityRule.scenario.recreate()
        pump()
        compose.onNodeWithTag("tab-Groups").assertIsSelected()
    }

    @Test(timeout=90000) fun previewToggleIsOffByDefaultAndSitsBetweenDownloadAndHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppSdk.bootstrap(context) }
        compose.runOnUiThread {
            compose.activity.setContent { OnKlavTheme { SdkAppScreen("app.onklav.youtube", exit = {}) } }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val preview = compose.onNodeWithContentDescription("Video previews")
        waitFor { runCatching { preview.assertIsDisplayed() }.isSuccess }
        preview.assertIsOff()
        compose.onNodeWithContentDescription("YouTube").assertExists()
        val download = compose.onNodeWithContentDescription("Download").fetchSemanticsNode().boundsInRoot
        val toggle = preview.fetchSemanticsNode().boundsInRoot
        val history = compose.onNodeWithContentDescription("History").fetchSemanticsNode().boundsInRoot
        assertTrue(download.center.x < toggle.center.x && toggle.center.x < history.center.x)
        preview.readyClick().assertIsOn()
        assertTrue(context.getSharedPreferences("onklav-youtube-viewer", 0).getBoolean("feed_previews", false))
        preview.readyClick().assertIsOff()
    }

    @Test(timeout=120000) fun youtubePackageOpensOwnViewerLibraryAndResetDialog() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppSdk.bootstrap(context) }
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { OnKlavScreen() } } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        compose.onNodeWithTag("app-icon-youtube").assertExists() // Cold start defaults to Apps.
        waitFor { compose.onAllNodesWithTag("app-icon-youtube").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-youtube").readyClick()
        waitFor { compose.onAllNodesWithContentDescription("YouTube Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-web-surface").assertExists()
        compose.onNodeWithContentDescription("App controls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Channels").readyClick()
        waitFor { compose.onAllNodesWithText("No Channels Yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-channel.add").readyClick()
        waitFor { compose.onAllNodesWithText("Open a video or channel, then try again.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Channels").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Channels").readyClick()
        compose.onNodeWithContentDescription("Bookmarks").readyClick()
        waitFor { compose.onAllNodesWithContentDescription("Close Bookmarks").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("YouTube Home").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("sdk-address").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bookmarks").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-bookmark.add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-channel.add").assertDoesNotExist()
        compose.onNodeWithTag("sdk-bookmark.add").assertIsNotEnabled()
        val viewer=androidx.lifecycle.ViewModelProvider(compose.activity)["sdk-app.onklav.youtube",SdkAppModel::class.java]
        compose.runOnUiThread{val web=viewer.webComponent("youtube.page").getJSONObject("node");viewer.webUpdate("youtube.page",web.getString("tab"),JSONObject().put("url","https://www.youtube.com/watch?v=AbC_def-123").put("title","Saved Video"),web.optLong("epoch"))}
        waitFor{!viewer.busy&&viewer.frame.optJSONObject("state")?.optString("video")=="AbC_def-123"}
        compose.onNodeWithTag("sdk-bookmark.add").readyClick()
        waitFor { compose.onAllNodesWithText("Saved Video").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved Video").assertExists()
        compose.onNodeWithContentDescription("Close Bookmarks").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("History").readyClick()
        waitFor { compose.onAllNodesWithText("Saved Video").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-history.clear").readyClick()
        waitFor { compose.onAllNodesWithText("Clear All History?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Close History").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("YouTube Home").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
        waitFor { compose.onAllNodesWithText("Reset YouTube Viewer?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Close").readyClick()
        waitFor { compose.onAllNodesWithTag("app-icon-youtube").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-youtube").readyClick()
        waitFor { compose.onAllNodesWithContentDescription("Bookmarks").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Bookmarks").readyClick()
        waitFor { compose.onAllNodesWithText("Saved Video").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(MessagingTransport.MMS_READY)
    }
}
