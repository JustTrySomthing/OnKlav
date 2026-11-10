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
class RssAppsHostTest {
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
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/v0.57.0/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    @Test(timeout=120000) fun rssPackageRunsOfflineArticlesBookmarksDrawersAndSettings() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppSdk.bootstrap(context) }
        var result=AppSdk.sync(context,JSONObject().put("action","launch").put("id","app.onklav.rss")) as JSONObject
        val token=result.getString("session")
        for(n in 0..3) {
            val xml="<rss><channel><title>Fixture</title><item><title>RSS Story $n</title><link>https://example.org/story$n</link><description>Offline story body $n</description><pubDate>Wed, 21 Oct 2026 12:00:0$n +0000</pubDate></item></channel></rss>"
            val earlier=if(n==0)(0..14).joinToString("") { "<item><title>Earlier Story $it</title><link>https://example.org/earlier$it</link><description>Earlier offline body $it</description><pubDate>Tue, 20 Oct 2026 12:00:00 +0000</pubDate></item>" }else ""
            val fixture=xml.replace("</channel>",earlier+"</channel>")
            val reply=SdkFeedService.parse(fixture.toByteArray(),"https://example.org/feed")
            result=AppSdk.sync(context,JSONObject().put("action","reply").put("session",token).put("token",result.child("pending").getString("token")).put("reply",JSONObject().put("ok",true).put("value",reply))) as JSONObject
        }
        AppSdk.sync(context,JSONObject().put("action","close").put("session",token))
        AppSdk.sync(context,JSONObject().put("action","permissions").put("id","app.onklav.rss").put("grants",org.json.JSONArray(listOf("storage.private"))))
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { OnKlavScreen() } } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        compose.onNodeWithTag("tab-Apps").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("app-icon-rss").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-rss").readyClick();pump()
        waitFor { compose.onAllNodesWithText("RSS Story 3").fetchSemanticsNodes().isNotEmpty() }
        capture("rss-articles")
        compose.onNodeWithTag("sdk-app-body").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f,650f) };pump()
        val listOffset=compose.onNodeWithTag("sdk-app-body").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(listOffset>0f)
        compose.onNodeWithText("Earlier Story 10").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-article.title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-article.title").assertTextContains("Earlier Story 10")
        compose.onNodeWithTag("sdk-app-body").assert(SemanticsMatcher("Article starts at the top") { it.config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()==0f })
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() };pump()
        waitFor { compose.onAllNodesWithTag("sdk-query").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-app-body").assert(SemanticsMatcher("Article list position is retained") { kotlin.math.abs(it.config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()-listOffset)<1f })
        compose.onNodeWithText("Earlier Story 10").assertIsDisplayed()
        compose.onNodeWithTag("sdk-app-body").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f,-10000f) };pump()
        compose.onNodeWithText("RSS Story 3").readyClick();pump()
        waitFor { compose.onAllNodesWithText("Offline story body 3").fetchSemanticsNodes().isNotEmpty() }
        capture("rss-offline-article")
        waitFor { runCatching { compose.onNodeWithTag("sdk-article.bookmark").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("sdk-article.bookmark").readyClick();pump()
        waitFor { compose.onAllNodesWithText("Bookmarked").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-article.back").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-query").fetchSemanticsNodes().isNotEmpty() }
        waitFor { runCatching { compose.onNodeWithContentDescription("Bookmarks").assertIsEnabled() }.isSuccess }
        compose.onNodeWithContentDescription("Bookmarks").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isNotEmpty() }
        capture("rss-bookmarks-drawer")
        compose.onNodeWithContentDescription("Close Bookmarks").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() }
        waitFor { runCatching { compose.onNodeWithContentDescription("RSS Settings").assertIsEnabled() }.isSuccess }
        compose.onNodeWithContentDescription("RSS Settings").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-settings.interval").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-settings.interval").readyClick();pump()
        compose.onNodeWithText("Manually").readyClick();pump()
        waitFor { runCatching { compose.onNodeWithTag("sdk-settings.unread").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("sdk-settings.unread").readyClick();pump()
        waitFor { runCatching { compose.onNodeWithTag("sdk-settings.done").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("sdk-settings.done").performScrollTo().readyClick();pump()
        waitFor { compose.onAllNodesWithTag("dialog-RSS Settings").fetchSemanticsNodes().isEmpty() }
        waitFor { compose.onAllNodesWithText("RSS Story 3").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Close").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("app-icon-rss").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-rss").readyClick();pump()
        waitFor { compose.onAllNodesWithText("RSS Story 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("RSS Story 3").assertDoesNotExist()
        assertFalse(MessagingTransport.MMS_READY)
    }
}
