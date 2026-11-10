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
class PhotoEditorUiTest {
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

    private fun option(label:String){
        val wheel=compose.onNodeWithTag("photo-options-wheel")
        wheel.performScrollToIndex(photoTestOptionIndex(label));pump()
        if(label=="Comic book")capture("photo-effect-wheel",true)
        compose.onNodeWithText(label,substring=false).readyClick()
    }
    private fun waitFor(test:()->Boolean) {
        try { compose.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));pump();test() } }
        catch(e:Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { println(compose.onAllNodes(isRoot())[it].printToString()) }
            capture("sdk-failure")
            throw e
        }
    }

    private fun capture(name:String,dialog:Boolean=false) { compose.runOnIdle {
        val view=if(dialog)org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView else compose.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(b))
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/pictures-hdr-fix/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    @Test(timeout=120000) fun editingToolsAndExportAreReachable(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking{AppSdk.bootstrap(c)}
        val launch=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.photo")) as JSONObject
        assertEquals("Image Editor",launch.child("frame").getString("title"));assertTrue(photoAccess(c))
        val file=java.io.File(c.filesDir,"photo-editor/source");file.parentFile!!.mkdirs()
        val bitmap=android.graphics.Bitmap.createBitmap(640,480,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(100,160,195));val paint=android.graphics.Paint();paint.color=android.graphics.Color.rgb(55,100,70);canvas.drawCircle(320f,390f,250f,paint)
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{PhotoEditorWorkspace()}}}
        compose.mainClock.autoAdvance=false
        waitFor{compose.onAllNodesWithContentDescription("Edited image preview").fetchSemanticsNodes().isNotEmpty()}
        val frame=compose.onNodeWithContentDescription("Crop frame: drag corners or edges to resize; drag inside to move")
        frame.assertIsDisplayed()
        assertTrue("Image canvas should occupy most of the workspace",frame.fetchSemanticsNode().boundsInRoot.height>600f)
        frame.performTouchInput{swipe(androidx.compose.ui.geometry.Offset(width.toFloat()-2,height/2f),androidx.compose.ui.geometry.Offset(width*.75f,height/2f),250)}
        pump();compose.onNodeWithContentDescription("Undo").assertIsEnabled()
        compose.onNodeWithText("Ratio").readyClick();option("Square · 1:1");capture("photo-crop")
        compose.onNodeWithText("Geometry").readyClick()
        waitFor{compose.onAllNodesWithTag("photo-adjustment-slider").fetchSemanticsNodes().isNotEmpty()}
        val slider=compose.onNodeWithTag("photo-adjustment-slider")
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(25f)};pump()
        compose.onNodeWithText("25.0°").assertExists()
        compose.onNodeWithContentDescription("Increase Rotate").readyClick();pump();compose.onNodeWithText("25.1°").assertExists()
        compose.onNodeWithContentDescription("Reset Rotate").readyClick();pump()
        assertEquals(0f,slider.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current,0f)
        compose.onNodeWithText("Rotate").readyClick();option("Rotate 90 degrees")
        compose.onNodeWithContentDescription("Undo").assertIsEnabled().readyClick();compose.onNodeWithContentDescription("Redo").assertIsEnabled()
        compose.onNodeWithText("Rotate").readyClick();option("Scale")
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(1.5f)};pump()
        compose.onNodeWithText("150%").assertExists();compose.onNodeWithText("Fixed canvas").assertDoesNotExist();compose.onNodeWithText("Fit image").assertDoesNotExist();compose.onNodeWithText("Fill canvas").assertDoesNotExist();compose.onNodeWithContentDescription("Reset geometry").assertDoesNotExist();capture("photo-geometry")
        compose.onNodeWithContentDescription("Show original image").readyClick();compose.onNodeWithContentDescription("Show edited image").assertExists().readyClick()
        compose.onNodeWithText("Color").readyClick();compose.onNodeWithText("Exposure").readyClick()
        compose.onNodeWithTag("photo-adjustment-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(1.5f)};pump()
        compose.onNodeWithContentDescription("Reset Exposure").readyClick();pump()
        assertEquals(0f,compose.onNodeWithTag("photo-adjustment-slider").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current,0f)
        compose.onNodeWithTag("photo-adjustment-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(1f)};pump()
        compose.onNodeWithContentDescription("Restore original image").readyClick();compose.onNodeWithText("Continue").readyClick();pump()
        assertEquals(0f,compose.onNodeWithTag("photo-adjustment-slider").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current,0f)
        capture("photo-color")
        compose.onNodeWithText("Effects").readyClick();compose.onNodeWithText("No look").assertIsDisplayed()
        var effect="No look"
        for(name in listOf("Teal & Orange","Dark & Moody","Film Noir","Reduce noise")){
            compose.onNodeWithText(effect).readyClick();option(name)
            compose.onNodeWithTag("photo-adjustment-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(.7f)};pump()
            compose.onNodeWithContentDescription(if(name in listOf("Teal & Orange","Dark & Moody","Film Noir"))"Reset Look strength"else"Reset $name").readyClick()
            assertEquals(0f,compose.onNodeWithTag("photo-adjustment-slider").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current,0f)
            effect=name
        }

        compose.onNodeWithText(effect).readyClick();compose.onNodeWithTag("photo-options-wheel").performScrollToIndex(9);pump()
        for(name in listOf("Retro Technicolor","Comic book","Pencil drawing"))compose.onNodeWithText(name).assertDoesNotExist()
        option("Vignette")
        compose.onNodeWithText("Warm film").assertDoesNotExist();compose.onNodeWithContentDescription("Effect presets").assertDoesNotExist()
        val saveBounds=compose.onNodeWithContentDescription("Save image").fetchSemanticsNode().boundsInRoot
        val openBounds=compose.onNodeWithContentDescription("Open image").fetchSemanticsNode().boundsInRoot
        assertTrue(saveBounds.left>openBounds.left)
        val fitBounds=compose.onNodeWithContentDescription("Reset zoom").fetchSemanticsNode().boundsInRoot
        val resetBounds=compose.onNodeWithContentDescription("Restore original image").fetchSemanticsNode().boundsInRoot
        assertTrue(resetBounds.top<fitBounds.top);assertTrue(resetBounds.left<openBounds.left)
        val eye=compose.onNodeWithContentDescription("Show original image").fetchSemanticsNode().boundsInRoot
        assertTrue(eye.left<fitBounds.left);assertEquals(eye.top,fitBounds.top,1f)
        compose.onNodeWithContentDescription("Save image").readyClick();waitFor{compose.onAllNodesWithText("Save (Overwrite)").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("PNG").readyClick();compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Restore original image").readyClick();compose.onNodeWithText("Cancel").readyClick()
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.photo").put("grants",org.json.JSONArray()))
        assertFalse(photoAccess(c));pump()
        compose.onNodeWithContentDescription("Edited image preview").assertDoesNotExist()
    }
}
