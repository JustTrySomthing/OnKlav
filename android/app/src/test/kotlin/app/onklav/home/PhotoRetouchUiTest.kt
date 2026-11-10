package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoRetouchUiTest{
 @get:Rule val keys=EmptyKeyStoreRule()
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var model:PhotoEditorModel
 @Before fun setup(){
  val c=compose.activity;runBlocking{AppSdk.bootstrap(c)}
  val f=File(c.cacheDir,"retouch.png");val b=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(185,140,115));for(y in 145..155)for(x in 195..205)setPixel(x,y,Color.rgb(230,25,30))};f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  c.setContent{OnKlavTheme{PhotoEditorWorkspace()}};compose.waitForIdle();compose.mainClock.autoAdvance=false;model=ViewModelProvider(c)["photo-editor-${AppSdk.epoch.value}",PhotoEditorModel::class.java]
  waitFor{!model.busy};compose.runOnUiThread{model.open(android.net.Uri.fromFile(f))};waitFor{!model.busy&&model.original!=null&&model.preview!=null};compose.onNodeWithText("Tools",substring=false).performClick();pump();compose.onNodeWithContentDescription("Retouch").performClick();pump()
 }
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25));compose.mainClock.advanceTimeBy(100)}}
 private fun waitFor(test:()->Boolean){try{compose.waitUntil(15000){pump();test()}}catch(e:Exception){throw AssertionError("Image fixture: busy=${model.busy}, source=${model.original!=null}, preview=${model.preview!=null}, feedback=${model.feedback}",e)}}
 private fun capture(){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/photo-retouch/tools.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun retouchTapUndoRedoAndPersistentToolRowWork(){
  compose.onNodeWithContentDescription("Red eye brush").assertIsSelected();val node=compose.onNodeWithContentDescription("Retouch canvas");val bounds=node.fetchSemanticsNode().boundsInRoot;assertTrue(bounds.height>300)
  val point=photoMarkProjection(400,300,model.edit,Size(bounds.width,bounds.height),1f,Offset.Zero).map(Offset(.5f,.5f));node.performTouchInput{click(point)}
  waitFor{model.edit.retouch.size==1&&model.preview?.getPixel(200,150)?.let{Color.red(it)<100}==true}
  assertEquals(230,Color.red(model.original!!.getPixel(200,150)))
  compose.onNodeWithContentDescription("Undo").performClick();pump();assertTrue(model.edit.retouch.isEmpty());compose.onNodeWithContentDescription("Redo").performClick();pump();assertEquals(1,model.edit.retouch.size)
  assertEquals(model.edit,PhotoEdit.parse(File(model.source.parentFile,"edits.json").readText()))
  compose.onNodeWithContentDescription("Heal brush").performClick();pump();compose.onNodeWithContentDescription("Heal brush").assertIsSelected();capture();compose.onNodeWithText("Drawing").assertDoesNotExist();compose.onNodeWithContentDescription("Pen").assertIsDisplayed();compose.onNodeWithContentDescription("Pen").performClick();waitFor{compose.onAllNodesWithContentDescription("Image tools canvas").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithContentDescription("Image tools canvas").assertExists()
 }
 @Test fun pinchDoesNotApplyCorrectionAndCompareDisablesPainting(){
  val before=model.edit;compose.onNodeWithContentDescription("Retouch canvas").performTouchInput{pinch(Offset(100f,150f),Offset(200f,150f),Offset(70f,150f),Offset(230f,150f),400)}
  assertEquals(before,model.edit);compose.onNodeWithContentDescription("Show original image").performClick();pump();compose.onNodeWithContentDescription("Retouch canvas").assertDoesNotExist();assertEquals(before,model.edit)
 }
 @Test fun stampSamplesWithoutEditingThenPaintsAndCanResample(){
  compose.onNodeWithContentDescription("Clone brush").performClick();pump();compose.onNodeWithContentDescription("Pen").assertIsDisplayed()
  val canvas=compose.onNodeWithContentDescription("Retouch canvas");val bounds=canvas.fetchSemanticsNode().boundsInRoot
  fun point(x:Float,y:Float)=photoMarkProjection(400,300,model.edit,Size(bounds.width,bounds.height),1f,Offset.Zero).map(Offset(x,y))
  canvas.performTouchInput{click(point(.25f,.5f))};pump();assertTrue(model.edit.retouch.isEmpty())
  canvas.performTouchInput{click(point(.5f,.5f))};waitFor{model.edit.retouch.size==1&&model.preview?.getPixel(200,150)?.let{Color.red(it)<200}==true}
  assertEquals("clone",model.edit.retouch.single().tool);assertNotNull(model.edit.retouch.single().source);assertEquals(.9f,model.edit.retouch.single().hardness)
  compose.onNodeWithContentDescription("Undo").performClick();pump();assertTrue(model.edit.retouch.isEmpty())
  compose.onNodeWithContentDescription("Sample stamp source").performClick();pump();canvas.performTouchInput{click(point(.3f,.3f))};pump();assertTrue(model.edit.retouch.isEmpty());capture()
 }
 @Test fun redEyeNoMatchGivesFeedbackAndKeepsOtherToolsAvailable(){
  val canvas=compose.onNodeWithContentDescription("Retouch canvas");val bounds=canvas.fetchSemanticsNode().boundsInRoot
  val point=photoMarkProjection(400,300,model.edit,Size(bounds.width,bounds.height),1f,Offset.Zero).map(Offset(.2f,.2f))
  canvas.performTouchInput{click(point)};waitFor{model.feedback.startsWith("No red pupils found")}
  compose.onNodeWithContentDescription("Highlighter").assertIsDisplayed();compose.onNodeWithContentDescription("Heal brush").performClick();pump();compose.onNodeWithContentDescription("Move marks").assertIsDisplayed()
 }

}
