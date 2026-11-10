package app.onklav.home
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PhotoMarkupUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var model:PhotoEditorModel
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25));compose.mainClock.advanceTimeBy(100)}}
 private fun waitFor(test:()->Boolean){compose.waitUntil(15000){pump();test()}}
 private fun click(label:String){compose.onNodeWithContentDescription(label).performClick();pump()}
 private fun start(onClose:()->Unit={}){
  val c=compose.activity;runBlocking{AppSdk.bootstrap(c)}
  val file=File(c.cacheDir,"photo-markup-fixture.png");val b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(70,110,160))};file.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  compose.runOnUiThread{c.setContent{OnKlavTheme{PhotoEditorWorkspace(onClose=onClose)}}};compose.waitForIdle();compose.mainClock.autoAdvance=false
  model=ViewModelProvider(c)["photo-editor-${AppSdk.epoch.value}",PhotoEditorModel::class.java];waitFor{!model.busy};compose.runOnUiThread{model.open(android.net.Uri.fromFile(file))};waitFor{!model.busy&&model.original!=null&&model.preview!=null&&model.edit.marks.isEmpty()&&model.edit.values.isEmpty()};assertTrue("Fresh image fixture must have no marks",model.edit.marks.isEmpty())
  compose.onNodeWithText("Tools",substring=false).performClick();waitFor{compose.onAllNodesWithContentDescription("Image tools canvas").fetchSemanticsNodes().isNotEmpty()}
 }
 private fun draw(from:Offset,to:Offset,cancel:Boolean=false){
  val b=compose.onNodeWithContentDescription("Image tools canvas").fetchSemanticsNode().boundsInRoot
  val mapping=photoMarkProjection(640,480,model.edit,Size(b.width,b.height),1f,Offset.Zero)
  val a=mapping.map(from)+b.topLeft;val z=mapping.map(to)+b.topLeft;val time=android.os.SystemClock.uptimeMillis()
  fun event(action:Int,p:Offset){compose.runOnUiThread{val e=MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),action,p.x,p.y,0);compose.activity.window.decorView.dispatchTouchEvent(e);e.recycle()};pump()}
  event(MotionEvent.ACTION_DOWN,a);for(i in 1..6)event(MotionEvent.ACTION_MOVE,a+(z-a)*(i/6f));event(if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,z)
 }
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));val f=File(System.getProperty("onklav.project.root"),"docs/evidence/photo-markup/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=120000) fun penHighlightMoveEraseTextUndoDraftAndExportWorkInOneEditor(){
  start();compose.onNodeWithContentDescription("Pen").assertIsOn();val original=model.original!!.getPixel(100,100)
  val canvas=compose.onNodeWithContentDescription("Image tools canvas").fetchSemanticsNode().boundsInRoot
  assertTrue("Image keeps substantial room",canvas.height>450)
  draw(Offset(.2f,.3f),Offset(.6f,.3f));assertEquals(1,model.edit.marks.size);assertEquals("pen",model.edit.marks[0].tool)
  val initial=model.edit;click("Move marks");draw(Offset(.4f,.3f),Offset(.5f,.45f));assertTrue(model.edit.marks[0].x>.08f);assertTrue(model.edit.marks[0].y>.1f)
  click("Undo");assertEquals(initial,model.edit);click("Redo")
  click("Highlighter");draw(Offset(.15f,.65f),Offset(.7f,.65f));assertEquals(2,model.edit.marks.size);assertEquals("highlight",model.edit.marks.last().tool)
  val marks=model.edit.marks;click("Erase marks");draw(Offset(.5f,.45f),Offset(.5f,.45f));assertEquals(1,model.edit.marks.size);click("Undo");assertEquals(marks,model.edit.marks)
  click("Text");compose.onNode(hasSetTextAction()).performTextInput("Hello 🔆");pump();draw(Offset(.2f,.55f),Offset(.2f,.55f));click("Add text");assertEquals(3,model.edit.marks.size)
  val text=model.edit.marks.last();assertEquals("Hello 🔆",text.text);compose.onNodeWithContentDescription("Move marks").assertIsOn()
  capture("tools-move")
  click("Edit selected text");compose.onNode(hasSetTextAction()).performTextReplacement("Revised label");pump();capture("tools-text");click("Apply text");assertEquals(3,model.edit.marks.size);assertEquals(text.id,model.edit.marks.last().id)
  click("Delete selected mark");assertEquals(2,model.edit.marks.size);click("Undo");assertEquals("Revised label",model.edit.marks.last().text)
  click("Text");compose.onNode(hasSetTextAction()).performTextInput("Saved with image");pump();click("Save image")
  compose.onNodeWithText("Save image",substring=false).assertDoesNotExist();compose.onNodeWithText("Save (Overwrite)",substring=false).assertIsDisplayed();assertEquals(4,model.edit.marks.size);assertEquals("Saved with image",model.edit.marks.last().text)
  compose.onNodeWithText("Cancel",substring=false).performClick();pump()
  assertEquals(original,model.original!!.getPixel(100,100));assertEquals(model.edit,PhotoEdit.parse(File(compose.activity.filesDir,"photo-editor/edits.json").readText()))
  compose.runOnUiThread{model.apply(model.edit.set("angle",20f).set("left",.1f).set("exposure",.4f))};waitFor{compose.onAllNodesWithContentDescription("Image tools canvas").fetchSemanticsNodes().isNotEmpty()}
  val target=File(compose.activity.cacheDir,"marked-image.png");compose.runOnUiThread{model.export(android.net.Uri.fromFile(target),true,100)};waitFor{model.feedback=="Copy saved"}
  val actual=android.graphics.BitmapFactory.decodeFile(target.path)!!;val expected=PhotoRender.render(model.original!!,model.edit)
  assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
  for(y in 0 until actual.height step 9)for(x in 0 until actual.width step 9)assertEquals(expected.getPixel(x,y),actual.getPixel(x,y))
  compose.onNodeWithText("Crop",substring=false).performClick();pump();compose.onNodeWithContentDescription("Crop frame: drag corners or edges to resize; drag inside to move").assertIsDisplayed()
  compose.onNodeWithText("Tools",substring=false).performClick();pump();click("Text");compose.onNode(hasSetTextAction()).performTextInput("Discard me");pump()
  click("Restore original image");compose.onNodeWithText("Continue",substring=false).performClick();pump();assertTrue(model.edit.marks.isEmpty());compose.onNodeWithText("Discard me",substring=false).assertDoesNotExist()
  click("Undo");assertEquals(4,model.edit.marks.size);assertEquals("Saved with image",model.edit.marks.last().text)
 }
 @Test(timeout=90000) fun switchingToolsAndTabsCommitsTextAndLayersReopensEditableHistory(){
  start();click("Text");compose.onNode(hasSetTextAction()).performTextInput("Automatic tool commit");pump();click("Pen");assertEquals("Automatic tool commit",model.edit.marks.single().text)
  click("Text");compose.onNode(hasSetTextAction()).performTextInput("Automatic tab commit");pump();compose.onNodeWithText("Effects",substring=false).performClick();pump();assertEquals(2,model.edit.marks.size);assertEquals("Automatic tab commit",model.edit.marks.last().text)
  val expected=model.edit;val target=File(compose.activity.cacheDir,"editable.layers");compose.runOnUiThread{model.exportLayers(android.net.Uri.fromFile(target))};waitFor{!model.busy&&target.exists()&&model.feedback.startsWith("Layers saved")}
  compose.runOnUiThread{model.apply(PhotoEdit());model.open(android.net.Uri.fromFile(target))};waitFor{!model.busy&&model.edit==expected};assertFalse(model.canReplace);assertTrue(model.canUndo)
  compose.runOnUiThread{model.history(true)};pump();assertEquals(1,model.edit.marks.size);compose.runOnUiThread{model.history(false)};pump();assertEquals(expected,model.edit)
  compose.onNodeWithText("Tools",substring=false).performClick();waitFor{compose.onAllNodesWithContentDescription("Image tools canvas").fetchSemanticsNodes().any{it.boundsInRoot.height>50}};click("Move marks");capture("tools-reopened-layers");compose.onNodeWithContentDescription("Image tools canvas").assertIsDisplayed()
 }
 @Test(timeout=60000) fun saveMenuHasStackedActionsAndDiscardClosesWithoutChangingOriginal(){
  var closed=false;start{closed=true};draw(Offset(.2f,.3f),Offset(.6f,.3f));assertTrue(model.edit.marks.isNotEmpty())
  val file=File(compose.activity.cacheDir,"photo-markup-fixture.png");val original=file.readBytes();click("Save image")
  compose.onNodeWithText("Save image",substring=false).assertDoesNotExist()
  val save=compose.onNodeWithText("Save (Overwrite)",substring=false).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  val close=compose.onNodeWithText("Close (Discard Changes)",substring=false).assertIsDisplayed();assertTrue(close.fetchSemanticsNode().boundsInRoot.top>save.bottom)
  compose.onNodeWithText("Layers",substring=false).assertExists();close.performClick();waitFor{closed&&!model.busy};assertNull(model.original);assertFalse(model.source.exists());assertFalse(File(compose.activity.filesDir,"photo-editor/edits.json").exists());assertArrayEquals(original,file.readBytes())
 }
 @Test(timeout=60000) fun cancelledStrokeDoesNotCommitAndEraseNeverChangesThePhoto(){
  start();draw(Offset(.2f,.3f),Offset(.6f,.3f),cancel=true);assertTrue(model.edit.marks.isEmpty());assertFalse(model.canUndo)
  click("Erase marks");draw(Offset(.2f,.3f),Offset(.6f,.3f));assertTrue(model.edit.marks.isEmpty());assertFalse(model.canUndo)
  click("Pen")
  val box=compose.onNodeWithContentDescription("Image tools canvas").fetchSemanticsNode().boundsInRoot;val cx=box.center.x;val cy=box.center.y;val now=android.os.SystemClock.uptimeMillis()
  fun event(action:Int,spread:Float,count:Int){compose.runOnUiThread{
   val properties=Array(count){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
   val coords=Array(count){i->MotionEvent.PointerCoords().apply{x=cx+(if(i==0)-spread else spread);y=cy;pressure=1f;size=1f}}
   val e=MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);compose.activity.window.decorView.dispatchTouchEvent(e);e.recycle()
  };pump()}
  event(MotionEvent.ACTION_DOWN,40f,1);event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),40f,2)
  for(i in 1..6)event(MotionEvent.ACTION_MOVE,40f+i*8,2)
  event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),88f,2);event(MotionEvent.ACTION_UP,88f,1)
  assertTrue(model.edit.marks.isEmpty());assertFalse(model.canUndo)
  val description=compose.onNodeWithContentDescription("Image tools canvas").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
  assertTrue("Pinch zoom should work without leaving marks: $description",description.substringBefore(" percent zoom").substringAfterLast("; ").toInt()>150)
  compose.onNodeWithContentDescription("Restore original image").assertIsDisplayed()
 }
 @Test(timeout=60000) @Config(qualifiers="w936dp-h432dp-land-mdpi") fun landscapeTextToolsStayInlineAndLeaveAVisibleCanvas(){
  start();click("Text");compose.onNode(hasSetTextAction()).assertIsDisplayed().performTextInput("Landscape label");pump()
  val bounds=compose.onNodeWithContentDescription("Image tools canvas").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue("Visible editing canvas $bounds",bounds.height>40)
  compose.onNodeWithContentDescription("Add text").assertIsDisplayed().assertIsEnabled();capture("tools-landscape")
  compose.onNodeWithText("Tools",substring=false).assertIsDisplayed()
 }
}
