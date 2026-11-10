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
class BrowserSdkHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
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
            capture("sdk-failure")
            throw e
        }
    }

    private fun capture(name:String) { compose.runOnIdle {
        val view=compose.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(b))
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/v0.91.0/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    @Test(timeout=120000) fun browserPackageBookmarksTabsSettingsAndReopen() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        OnKlavPermissions.finish(context)
        runBlocking { AppSdk.bootstrap(context) }
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { OnKlavScreen() } } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        compose.onNodeWithTag("tab-Apps").readyClick()
        waitFor { compose.onAllNodesWithTag("app-icon-browser").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-browser").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-address").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-web-surface").assertExists()
        compose.onNodeWithContentDescription("App controls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Downloads").readyClick()
        waitFor { compose.onAllNodesWithTag("dialog-Downloads").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No browser downloads yet").assertExists()
        compose.onNodeWithText("Saved to Files › Downloads").assertExists()
        compose.onNodeWithText("Done").readyClick()
        waitFor { compose.onAllNodesWithTag("dialog-Downloads").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("History").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-history.clear").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("History").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Bookmarks").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-bookmark.add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-bookmark.add").readyClick()
        waitFor { compose.onAllNodesWithText("No Bookmarks Yet").fetchSemanticsNodes().isEmpty() }
        capture("browser-bookmarks")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        pump()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("sdk-tabs").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-tab.new").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-tab.new").readyClick()
        waitFor { compose.onAllNodesWithTag("dialog-Tabs").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("sdk-tabs").assertTextContains("2")
        compose.onNodeWithContentDescription("Browser Settings").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-settings.home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-settings.home").performTextReplacement("https://example.org/home")
        compose.onNodeWithTag("sdk-settings.save").performScrollTo().readyClick()
        waitFor { compose.onAllNodesWithTag("dialog-Browser Settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Home").readyClick()
        waitFor { runCatching { compose.onNodeWithTag("sdk-address").assertTextContains("https://example.org/home") }.isSuccess }
        capture("browser-sdk-shell")
        compose.onNodeWithContentDescription("Close").readyClick()
        waitFor { compose.onAllNodesWithTag("app-icon-browser").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-browser").readyClick()
        waitFor { runCatching { compose.onNodeWithTag("sdk-address").assertTextContains("https://example.org/home") }.isSuccess }
        compose.onNodeWithTag("sdk-tabs").assertTextContains("2")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor { compose.onAllNodesWithTag("app-icon-browser").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(MessagingTransport.MMS_READY)
    }
}
