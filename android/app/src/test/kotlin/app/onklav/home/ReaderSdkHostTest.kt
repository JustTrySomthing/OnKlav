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
class ReaderSdkHostTest {
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
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/v0.90.0/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    @Test(timeout=120000) fun readerSdkDraftControlsSettingsClearAndReopen() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppSdk.bootstrap(context) }
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { OnKlavScreen() } } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        compose.onNodeWithTag("tab-Apps").readyClick()
        waitFor { compose.onAllNodesWithTag("app-icon-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-reader").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("App controls").assertDoesNotExist()
        compose.onNodeWithText("Ready",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("sdk-text").performTextInput("Read this private draft. 🐈")
        compose.onNodeWithTag("sdk-clear").readyClick()
        waitFor { compose.onAllNodesWithText("Clear Text?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithTag("sdk-text").assertTextContains("Read this private draft. 🐈")
        compose.onNodeWithContentDescription("Next").readyClick()
        waitFor { runCatching { compose.onNodeWithTag("sdk-text").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange,androidx.compose.ui.text.TextRange("Read this private draft. 🐈".length))) }.isSuccess }
        compose.onNodeWithContentDescription("Previous").readyClick()
        waitFor { runCatching { compose.onNodeWithTag("sdk-text").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange,androidx.compose.ui.text.TextRange(0))) }.isSuccess }
        compose.onNodeWithTag("sdk-speech-position").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(10f) }
        pump()
        compose.onNodeWithTag("sdk-text").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange,androidx.compose.ui.text.TextRange(SpeechPlan.start("Read this private draft. 🐈",10))))
        compose.onNodeWithTag("sdk-text").performTextInput("x".repeat(50001))
        waitFor { compose.onAllNodesWithText("Text limit: 50000 characters. Your text was kept.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("OK").readyClick()
        compose.onNodeWithTag("sdk-text").assertTextContains("Read this private draft. 🐈")
        capture("reader-editor")
        compose.onNodeWithContentDescription("Voice and Speed Settings").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-speech-speed").fetchSemanticsNodes().isNotEmpty() }
        capture("reader-voice-settings")
        compose.onNodeWithTag("sdk-settings.close").readyClick()
        waitFor { compose.onAllNodesWithTag("dialog-Voice and Speed Settings").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Close").readyClick()
        waitFor { compose.onAllNodesWithTag("app-icon-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-reader").readyClick()
        waitFor { compose.onAllNodesWithTag("sdk-text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-text").assertTextContains("Read this private draft. 🐈")
        compose.onNodeWithTag("sdk-clear").readyClick()
        waitFor { compose.onAllNodesWithText("Clear Text?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Clear") and hasAnyAncestor(hasTestTag("dialog-Clear Text?"))).readyClick()
        waitFor { runCatching { compose.onNodeWithTag("sdk-text").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString(""))) }.isSuccess }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("test","Pasted words"))
        compose.onNodeWithTag("sdk-paste").readyClick()
        waitFor {runCatching {compose.onNodeWithTag("sdk-text").assertTextContains("Pasted words")}.isSuccess}
        assertFalse(MessagingTransport.MMS_READY)
    }
}
