package app.onklav.home

import android.graphics.*
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PhotoHdrUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))}}
 private fun click(text:String){val node=if(compose.onAllNodesWithTag("photo-options-wheel").fetchSemanticsNodes().isNotEmpty())compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("photo-options-wheel")))else compose.onNodeWithText(text);node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
 @Test(timeout=90000)fun exclusiveLooksColorizeUndoAndRemovedHdrMatchTheVisibleControls(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)}
  val b=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(150,110,70))};val file=File(app.filesDir,"photo-editor/source");file.parentFile!!.mkdirs();file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{PhotoEditorWorkspace()}}};compose.waitForIdle();compose.mainClock.autoAdvance=false
  val model=ViewModelProvider(compose.activity)["photo-editor-${AppSdk.epoch.value}",PhotoEditorModel::class.java]
  compose.waitUntil(15000){pump();model.original!=null&&model.preview!=null}
  fun option(current:String,next:String){click(current);compose.onNodeWithTag("photo-options-wheel").performScrollToIndex(photoTestOptionIndex(next));pump();click(next)}
  click("Effects");compose.onNodeWithText("No look").assertIsDisplayed();option("No look","Black & white")
  assertEquals("blackWhite",PhotoLooks.key(model.edit));assertEquals(1f,PhotoLooks.amount(model.edit),0f)
  option("Black & white","Sepia");assertEquals("sepia",PhotoLooks.key(model.edit));assertEquals(0f,model.edit["noir"],0f)
  option("Sepia","Colorize");assertEquals("colorize",PhotoLooks.key(model.edit))
  compose.onNodeWithText("Choose color",substring=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()
  compose.onNodeWithContentDescription("Colorize Blue").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump();click("Use color")
  assertEquals(240f,model.edit["colorizeHue"],0f)
  compose.onNodeWithContentDescription("Look strength").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(.4f)};pump();compose.runOnUiThread{model.commit()}
  assertEquals(.4f,PhotoLooks.amount(model.edit),.001f)
  compose.onNodeWithContentDescription("Undo").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump();assertEquals(1f,PhotoLooks.amount(model.edit),0f)
  click("Colorize");compose.onNodeWithText("HDR Natural").assertDoesNotExist();compose.onNodeWithText("HDR B & W").assertDoesNotExist();compose.onNodeWithTag("photo-options-wheel").performScrollToIndex(0);pump();click("No look");assertEquals("none",PhotoLooks.key(model.edit))
  click("Effects");option("No look","Black & white");click("Color");click("Exposure");compose.onNodeWithTag("photo-options-wheel").performScrollToIndex(3);pump();click("Saturation")
  compose.onNodeWithContentDescription("Saturation").assertIsNotEnabled();compose.onNodeWithText("at 100% removes color",substring=true).assertExists()
  click("Remove look");compose.onNodeWithContentDescription("Saturation").assertIsEnabled()
 }
}
