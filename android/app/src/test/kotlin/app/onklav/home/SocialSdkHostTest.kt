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
class SocialSdkHostTest {
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
            throw e
        }
    }

    @Test(timeout=60000) fun socialOpensThemedComposerFriendsAndFeed() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {AppSdk.bootstrap(context)}
        val shown=androidx.compose.runtime.mutableStateOf(true)
        compose.runOnUiThread {compose.activity.setContent {OnKlavTheme {if(shown.value)SdkAppScreen("app.onklav.social",exit={shown.value=false})else androidx.compose.material3.Text("Closed Social")}}}
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        waitFor {compose.onAllNodesWithText("What would you like to share?").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("My Posts").assertExists()
        compose.onNodeWithContentDescription("Attach image").assertExists()
        compose.onNodeWithText("Friends").readyClick()
        waitFor {compose.onAllNodesWithContentDescription("Invite a friend").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Feed").readyClick()
        waitFor {compose.onAllNodesWithText("No recent posts yet.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Close").readyClick()
        waitFor {compose.onAllNodesWithText("Closed Social").fetchSemanticsNodes().isNotEmpty()}
    }
}
