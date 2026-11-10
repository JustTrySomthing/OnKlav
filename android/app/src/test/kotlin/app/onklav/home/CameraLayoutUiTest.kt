package app.onklav.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class CameraLayoutUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));compose.mainClock.advanceTimeByFrame()}}
 private fun start(wide:Boolean=false,onClose:()->Unit={}):CameraToolbarState{
  val c=compose.activity;runBlocking{AppSdk.bootstrap(c)};c.getSharedPreferences("camera-settings",0).edit().putInt("countdown",0).putString("ratio","16:9").commit()
  val model=SdkAppModel(c.application);runBlocking{model.open(CAMERA_APP)};val bar=CameraToolbarState()
  compose.runOnUiThread{c.setContent{OnKlavTheme{Column(Modifier.fillMaxSize()){
   if(!wide)Row(Modifier.fillMaxWidth().height(56.dp).testTag("camera-test-header")){Text("Camera",Modifier.weight(1f));CameraHeaderButtons(bar)}
   Box(Modifier.weight(1f)){CameraContent(model,false,bar,onClose)}
  }}}};compose.waitForIdle();compose.mainClock.autoAdvance=false;pump();return bar
 }
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));val f=File(System.getProperty("onklav.project.root"),"docs/evidence/camera-layout/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=60000)fun portraitHeaderTimerAndOverlayUseWholeStage(){
  val bar=start();val header=compose.onNodeWithTag("camera-test-header").fetchSemanticsNode().boundsInRoot
  val stage=compose.onNodeWithContentDescription("Camera preview stage").fetchSemanticsNode().boundsInRoot
  val dock=compose.onNodeWithContentDescription("Portrait camera controls").fetchSemanticsNode().boundsInRoot
  val timer=compose.onNodeWithContentDescription("Capture timer").assertIsDisplayed();assertTrue(timer.fetchSemanticsNode().boundsInRoot.top<header.bottom);compose.onNodeWithText("Off",substring=false).assertDoesNotExist()
  timer.performClick();pump();assertEquals(3,bar.timer);timer.performClick();pump();assertEquals(10,bar.timer);timer.performClick();pump();assertEquals(0,bar.timer)
  assertTrue(stage.top>=header.bottom);assertTrue(stage.height>800);assertTrue(dock.top>stage.top&&dock.bottom<=stage.bottom);compose.onNodeWithContentDescription("Camera zoom").assertIsDisplayed()
  val preview=compose.onNodeWithContentDescription("Camera capture preview").fetchSemanticsNode().boundsInRoot;assertEquals(9f/16,preview.width/preview.height,.001f);assertTrue(preview.bottom>dock.top)
  compose.onNodeWithContentDescription("Camera settings").performClick();pump();compose.onNodeWithText("1:1",substring=false).performClick();compose.onNodeWithText("Done",substring=false).performClick();pump();capture("portrait")
  compose.onNodeWithContentDescription("Open captures").performClick();pump();assertTrue(bar.gallery);compose.onNode(hasText("Camera",substring=false) and hasClickAction()).performClick();pump();assertFalse(bar.gallery)
 }
 @Test(timeout=60000) @Config(qualifiers="w936dp-h432dp-land-mdpi")fun landscapeZoomAndCaptureShareBottomRow(){
  var closed=false;start(true){closed=true}
  val stage=compose.onNodeWithContentDescription("Camera preview stage").fetchSemanticsNode().boundsInRoot
  val dock=compose.onNodeWithContentDescription("Landscape camera controls").fetchSemanticsNode().boundsInRoot
  assertEquals(936f,stage.width,2f);assertEquals(432f,stage.height,2f)
  val zoom=compose.onNodeWithContentDescription("Camera zoom").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  val shutter=compose.onNodeWithContentDescription("Take photo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue(zoom.left<shutter.left);assertTrue(kotlin.math.abs(zoom.center.y-shutter.center.y)<20);assertTrue(dock.bottom<=stage.bottom&&dock.top>stage.center.y)
  compose.onNodeWithContentDescription("Capture timer").assertIsDisplayed();compose.onNodeWithContentDescription("Camera settings").assertIsDisplayed();capture("landscape")
  compose.onNodeWithContentDescription("Close camera").performClick();pump();assertTrue(closed)
 }
}
