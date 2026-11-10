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
class SdkAppsHostTest {
    @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
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
    @Test fun shortcutPackagesAreProtectedIndependentlyOfDesktopVisibility() {
        val context = compose.activity
        for (id in listOf("browser", "youtube", "walkie", "reader")) {
            val packageId = AppSdk.bundledApps.getValue(id).first
            assertTrue(AppSdk.isRemovalProtected(packageId))
            val failure = assertThrows(IllegalStateException::class.java) {
                AppSdk.sync(context, JSONObject().put("action", "remove").put("id", packageId))
            }
            assertEquals("This included app cannot be uninstalled.", failure.message)
            setHidden(context, false, id, true)
            assertTrue(hidden(context, false, id))
            setHidden(context, false, id, false)
        }
        assertFalse(AppSdk.isRemovalProtected("app.onklav.audiobooks"))
        assertFalse(AppSdk.isRemovalProtected("app.onklav.podcasts"))
    }

    @Test(timeout=90000) fun bundledAudiobooksCanBeHiddenWithoutUninstalling() {
        val context=compose.activity
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread { context.setContent { OnKlavTheme { SdkAppsCenter {} } } }
        val row=hasAnyAncestor(hasTestTag("managed-app-app.onklav.audiobooks"))
        val hide=compose.onNode(hasText("Hide") and row)
        waitFor { runCatching { hide.assertIsEnabled() }.isSuccess }
        hide.performScrollTo().readyClick()
        assertTrue(hidden(context,false,"audiobooks"))
        compose.onNode(hasText("Installed · Hidden") and row,useUnmergedTree=true).assertExists()
        val record=runBlocking { AppSdk.list(context) }.single {
            it.child("manifest").optString("id")=="app.onklav.audiobooks"
        }
        assertTrue(record.optBoolean("installed"))
        compose.onNode(hasText("Show") and row).performScrollTo().readyClick()
        assertFalse(hidden(context,false,"audiobooks"))
        compose.onNode(hasText("Hide") and row).assertExists()
    }

    @Test(timeout=120000) fun sdkNotesSaveReopenClipboardRemoveReinstallAndDeveloperApp() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { AppSdk.bootstrap(context) }
        val pkg=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"sdk/packages/Counter.onklav-a")
        runBlocking {
            pkg.inputStream().use { AppSdk.stage(context,it) }
            AppSdk.install(context,AppSdk.inspect(context),emptySet())
        }
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { OnKlavScreen() } } }
        compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
        compose.onNodeWithTag("tab-Apps").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("app-icon-notes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-notes").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-note.title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-note.title").performTextReplacement("SDK QA");pump()
        compose.onNodeWithTag("sdk-note.text").performTextReplacement("first line 🐈");pump()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));pump()
        val docs=java.io.File(context.filesDir,"onklav-content/notes")
        waitFor { docs.listFiles()?.filter { it.extension=="json" }?.any { JSONObject(it.readText()).optString("text")=="first line 🐈" } == true }
        val note=docs.listFiles()!!.filter { it.extension=="json" }.map { JSONObject(it.readText()) }.first { it.optString("title")=="SDK QA" }
        waitFor { compose.onAllNodesWithText("13 / 100,000 · Saved").fetchSemanticsNodes().isNotEmpty() }
        capture("sdk-notes-editor")
        compose.onNodeWithContentDescription("App controls").assertDoesNotExist()
        compose.onNodeWithContentDescription("Saved Notes").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-open:${note.getString("id")}").fetchSemanticsNodes().isNotEmpty() }
        capture("sdk-notes-left-drawer")
        compose.onNodeWithTag("sdk-library.query").performTextInput("not present");pump()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));pump()
        waitFor { compose.onAllNodesWithText("No Saved Notes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-library.query").performTextClearance();pump()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600));pump()
        waitFor { compose.onAllNodesWithTag("sdk-open:${note.getString("id")}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-open:${note.getString("id")}").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty() && runCatching { compose.onNodeWithContentDescription("Notes Settings").assertIsEnabled() }.isSuccess }
        waitFor { runCatching { compose.onNodeWithContentDescription("Notes Settings").assertIsEnabled() }.isSuccess }
        compose.onNodeWithContentDescription("Notes Settings").readyClick();pump()
        waitFor { compose.onAllNodesWithText("Copy Text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Copy Text").readyClick();pump()
        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        waitFor { clipboard.primaryClip?.getItemAt(0)?.text?.toString()=="first line 🐈" }
        compose.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("QA"," pasted")) }
        waitFor { runCatching { compose.onNodeWithContentDescription("Notes Settings").assertIsEnabled() }.isSuccess }
        compose.onNodeWithContentDescription("Notes Settings").readyClick();pump()
        waitFor { compose.onAllNodesWithText("Paste").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Paste").readyClick();pump()
        waitFor { docs.listFiles()?.filter { it.extension=="json" }?.any { JSONObject(it.readText()).optString("text").contains("pasted") }==true }
        waitFor { runCatching { compose.onNodeWithTag("sdk-note.text").assertIsEnabled() }.isSuccess && compose.onAllNodesWithText("Paste").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Close").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("app-icon-notes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-notes").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-note.text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-note.text").assertTextContains("pasted",substring=true)
        waitFor { runCatching { compose.onNodeWithTag("sdk-note.text").assertIsEnabled() }.isSuccess && compose.onAllNodesWithText("Paste").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Close").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("tab-Apps").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-Apps").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("app-icon-settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app-icon-settings").readyClick();pump()
        compose.onNode(hasText("Apps") and hasAnyAncestor(hasTestTag("settings-pane"))).readyClick();pump()
        waitFor { compose.onAllNodesWithTag("managed-app-app.onklav.notes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("managed-app-app.onklav.notes").readyClick();pump()
        compose.onNodeWithText("Remove App").readyClick();pump()
        compose.onNodeWithText("Remove").readyClick();pump()
        waitFor { !(AppSdk.sync(context,JSONObject().put("action","list")) as org.json.JSONArray).let { a -> (0 until a.length()).any { a.getJSONObject(it).child("manifest").optString("id")=="app.onklav.notes" && a.getJSONObject(it).optBoolean("installed") } } }
        assertTrue("Removing without Delete Data retains notes",docs.listFiles()!!.isNotEmpty())
        waitFor { compose.onAllNodesWithText("Install").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Install").readyClick();pump()
        waitFor { compose.onAllNodesWithTag("dialog-Install App").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Private saved documents, collections and files").readyClick();pump()
        compose.onNode(hasText("Install") and hasAnyAncestor(hasTestTag("dialog-Install App"))).readyClick();pump()
        waitFor { (AppSdk.sync(context,JSONObject().put("action","list")) as org.json.JSONArray).let { a -> (0 until a.length()).any { a.getJSONObject(it).child("manifest").optString("id")=="app.onklav.notes" && a.getJSONObject(it).optBoolean("installed") } } }
        waitFor { compose.onAllNodesWithTag("dialog-Install App").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Close").readyClick();pump()
        compose.onNodeWithTag("tab-Apps").readyClick();pump()
        val icon="app-icon-"+AppSdk.desktopId("developer.example.counter")
        compose.onNodeWithTag("apps-next-page").readyClick();pump()
        waitFor { compose.onAllNodesWithTag(icon).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(icon).readyClick();pump()
        waitFor { compose.onAllNodesWithTag("sdk-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add One").readyClick();pump()
        waitFor { compose.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sdk-count").assertTextEquals("1")
        assertFalse(MessagingTransport.MMS_READY)
    }
}
