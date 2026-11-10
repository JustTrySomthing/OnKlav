package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.time.Duration
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PhotoCropUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))}}
 private fun click(text:String){if(compose.onAllNodesWithTag("photo-options-wheel").fetchSemanticsNodes().isNotEmpty()){compose.onNodeWithTag("photo-options-wheel").performScrollToIndex(photoTestOptionIndex(text));pump()};compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
 @Test(timeout=90000)fun returningToCropShowsGeometryColorAndEffectsAndKeepsHandlesUsable(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  val src=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888)
  for(y in 0 until src.height)for(x in 0 until src.width)src.setPixel(x,y,Color.rgb(20+x,30+y,210-x))
  val file=File(app.filesDir,"photo-editor/source");file.parentFile!!.mkdirs();file.outputStream().use{src.compress(Bitmap.CompressFormat.PNG,100,it)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{PhotoEditorWorkspace()}}};compose.waitForIdle();compose.mainClock.autoAdvance=false
  val key="photo-editor-${AppSdk.epoch.value}";val model=ViewModelProvider(compose.activity)[key,PhotoEditorModel::class.java]
  compose.waitUntil(15000){pump();model.original!=null&&model.preview!=null}
  click("Geometry");click("Rotate");click("Rotate 90 degrees")
  click("Effects");click("No look");click("Sepia")
  compose.onNodeWithTag("photo-adjustment-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(.7f)};pump();compose.runOnUiThread{model.commit()} // Semantics SetProgress has no pointer-up callback.
  click("Crop")
  val frame=compose.onNodeWithContentDescription("Crop frame: drag corners or edges to resize; drag inside to move").assertIsDisplayed()
  compose.waitUntil(15000){pump();runCatching{compose.onNode(isRoot()).assertExists()}.isSuccess}
  assertEquals(90f,photoAdjustmentValue(model.edit,"angle"),0f);assertEquals(2f,model.edit["colorMode"],0f);assertEquals(.7f,model.edit["lookStrength"],.001f)
  // Wait for the real CPU bitmap render, not just the state description.
  val expected=PhotoRender.render(src,model.edit);val expectedColor=expected.getPixel(expected.width/2,expected.height/2)
  var screenshot:Bitmap?=null
  compose.waitUntil(15000){pump();compose.runOnUiThread{
   val view=compose.activity.window.decorView
   screenshot?.recycle();screenshot=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also{view.draw(android.graphics.Canvas(it))}
  };val bounds=frame.fetchSemanticsNode().boundsInRoot;val p=photoCropProjection(src.width,src.height,model.edit,IntSize(bounds.width.toInt(),bounds.height.toInt()))!!
   val center=p.map(Offset(src.width/2f,src.height/2f));val actual=screenshot!!.getPixel((bounds.left+center.x).roundToInt(),(bounds.top+center.y).roundToInt())
   abs(Color.red(actual)-Color.red(expectedColor))<12&&abs(Color.blue(actual)-Color.blue(expectedColor))<12
  }
  File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/radio-geometry-polish/crop-edited.png").also{it.parentFile!!.mkdirs()}.outputStream().use{screenshot!!.compress(Bitmap.CompressFormat.PNG,100,it)}
  val before=model.edit;val bounds=frame.fetchSemanticsNode().boundsInRoot
  val p=photoCropProjection(src.width,src.height,before,IntSize(bounds.width.toInt(),bounds.height.toInt()))!!
  val start=p.handles(before).first{it.first=="r"}.second
  val end=p.map(Offset(src.width*.8f,src.height*.5f))
  frame.performTouchInput{swipe(start,end,300)};pump()
  assertTrue(model.edit["right"]>.05f)
  assertEquals(before.values.filterKeys{it !in photoCropKeys},model.edit.values.filterKeys{it !in photoCropKeys})
  compose.onNodeWithContentDescription("Undo").performClick();pump();assertEquals(before,model.edit)
  screenshot?.recycle();expected.recycle();src.recycle()
 }
}
