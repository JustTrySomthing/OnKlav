package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ChromeLayoutHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun layout(){
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread{
            val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
            val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
            val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach{root->
                val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val decor=view.rootView;decor.forceLayout()
                decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(936,android.view.View.MeasureSpec.AT_MOST))
                decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
            }
        }
    }
    private fun pump(){repeat(4){compose.mainClock.advanceTimeBy(100);layout()}}
    @Test(timeout=90000) fun headersAndBottomBarReachBothEdgesAcrossEveryHomeTab(){
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)}
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{OnKlavScreen()}}};pump()
        for(tab in listOf("Apps","Messages","Groups")){
            compose.onNodeWithTag("tab-$tab").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()
            for(tag in listOf("home-header","home-navigation")){
                val bounds=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertEquals("$tab $tag left",0f,bounds.left.value,1f);assertEquals("$tab $tag right",432f,bounds.right.value,1f)
            }
            compose.onNodeWithTag("status-bar-backplate").assertExists();compose.onNodeWithTag("navigation-bar-backplate").assertExists()
        }
    }
    @Test(timeout=90000) fun sharedPopupGrowsSubtlyAtCenterWithoutSidewaysTravel(){
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)}
        compose.mainClock.autoAdvance=false;var show by mutableStateOf(false)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{if(show)ReferenceDialog("Motion check",{},footer={DialogAction("Close") {}}){androidx.compose.material3.Text("Popup content")}}}};pump()
        compose.runOnUiThread{show=true;androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()}
        compose.waitUntil(5000){compose.mainClock.advanceTimeByFrame();layout();compose.onAllNodesWithTag("dialog-Motion check").fetchSemanticsNodes().isNotEmpty()}
        compose.mainClock.advanceTimeBy(48);layout()
        val early=compose.onNodeWithTag("dialog-Motion check").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(400);layout()
        val settled=compose.onNodeWithTag("dialog-Motion check").fetchSemanticsNode().boundsInRoot
        assertEquals("Popup stays horizontally centered",settled.center.x,early.center.x,1f)
        assertEquals("Popup stays vertically centered",settled.center.y,early.center.y,1f)
        assertTrue("Popup grows only a little: $early -> $settled",early.width>=settled.width*.93f && early.width<settled.width)
        compose.runOnUiThread {
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertEquals(android.view.Gravity.CENTER,dialog.window!!.attributes.gravity)
            assertEquals(0,dialog.window!!.attributes.windowAnimations)
        }
        assertFalse(MessagingTransport.MMS_READY)
    }
}
