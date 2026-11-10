package app.onklav.home

import android.graphics.*
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkDrawingTest {
 private fun fixture()=File(requireNotNull(System.getProperty("onklav.project.root")),"docs/fixtures/annotation-base.jpg").readBytes()
 private fun doc():JSONObject=JSONObject().put("id","a".repeat(32)).put("revision",0).put("width",120).put("height",100).put("image",android.util.Base64.encodeToString(SdkDrawingImages.normalize(fixture()),android.util.Base64.NO_WRAP)).put("marks",JSONArray())
 @Test fun importsAreBoundedAndPreserveOrientedDimensions() {
  val bytes=fixture();var checked=0;assertArrayEquals(bytes,SdkDrawingImages.read(ByteArrayInputStream(bytes)) {checked++});assertTrue(checked>1)
  assertTrue(runCatching {SdkDrawingImages.read(ByteArrayInputStream(ByteArray(20*1024*1024+1))) {}}.isFailure)
  assertTrue(runCatching {SdkDrawingImages.read(ByteArrayInputStream(bytes)) {error("Revoked")}}.isFailure)
  assertTrue(runCatching {SdkDrawingImages.normalize(byteArrayOf(1,2,3))}.isFailure)
  val b=SdkDrawingImages.image(doc());assertEquals(120,b.width);assertEquals(100,b.height);b.recycle()
 }
 @Test fun exportRendersImageCoordinatesAndTextBounds() {
  val d=doc();val mark=JSONObject("""{"tool":"pen","color":4294901760,"width":8,"x":0,"y":0,"points":[[10,20],[55,20]]}""");d.getJSONArray("marks").put(mark)
  val bytes=SdkDrawingImages.render(d);assertTrue(bytes.size<=900*1024);val b=BitmapFactory.decodeByteArray(bytes,0,bytes.size);assertEquals(120,b.width);assertEquals(100,b.height)
  assertTrue(Color.red(b.getPixel(25,20))>180);assertTrue(Color.green(b.getPixel(25,20))<80);assertTrue(Color.green(b.getPixel(25,50))>180);b.recycle()
  val note=JSONObject().put("text","Hello");val size=SdkDrawingImages.textSize(note,d);assertTrue(size.getInt("box_width")<=120);assertTrue(size.getInt("box_height")<=100)
  note.put("text","x\n".repeat(200));assertTrue(runCatching {SdkDrawingImages.textSize(note,d)}.isFailure)
 }
 @Test fun viewportSurvivesResizeAndARecreatedCanvasWithoutSavingAnUnfinishedGesture(){
  val c=ApplicationProvider.getApplicationContext<android.content.Context>();val d=doc();val v=SdkDrawingView(c);v.layout(0,0,360,420);v.update(d,SdkDrawingImages.image(d));
  val state=JSONObject().put("id",d.getString("id")).put("scale",3).put("x",.6).put("y",.55).toString();v.restoreViewport(state)
  val before=JSONObject(v.viewport());v.layout(0,0,500,360);val after=JSONObject(v.viewport());assertEquals(before.getDouble("scale"),after.getDouble("scale"),.01)
  val next=SdkDrawingView(c);next.layout(0,0,500,360);next.update(d,SdkDrawingImages.image(d));next.restoreViewport(v.viewport());assertEquals(after.getDouble("x"),JSONObject(next.viewport()).getDouble("x"),.01);v.clear();next.clear()
 }
 @Test fun drawingGestureUsesImageSpaceAndCancellationDoesNotCommit() {
  val v=SdkDrawingView(ApplicationProvider.getApplicationContext());val d=doc();v.layout(0,0,360,360);v.update(d,SdkDrawingImages.image(d));v.tool="pen"
  val output=mutableListOf<JSONObject>();v.send={value,done->output.add(value);done()}
  fun touch(action:Int,x:Float,y:Float) {val e=MotionEvent.obtain(1,2,action,x,y,0);v.onTouchEvent(e);e.recycle()}
  touch(MotionEvent.ACTION_DOWN,60f,60f);touch(MotionEvent.ACTION_MOVE,120f,100f);touch(MotionEvent.ACTION_UP,120f,100f)
  assertEquals(1,output.size);val points=output[0].getJSONObject("mark").getJSONArray("points");assertEquals(2,points.length());assertTrue(points.getJSONArray(0).getDouble(0)<60);assertTrue(points.getJSONArray(1).getDouble(0)<=120)
  touch(MotionEvent.ACTION_DOWN,60f,60f);touch(MotionEvent.ACTION_CANCEL,80f,80f);assertEquals(1,output.size);v.clear()
 }
 @Test fun interruptedGesturesRequireANewDownAndOldCompletionsCannotCancelNewStrokes() {
  val v=SdkDrawingView(ApplicationProvider.getApplicationContext());val d=doc()
  v.layout(0,0,360,360);v.update(d,SdkDrawingImages.image(d));v.tool="pen"
  val output=mutableListOf<JSONObject>();val completions=mutableListOf<()->Unit>()
  v.send={value,done->output.add(value);completions.add(done)}
  fun touch(action:Int) {val e=MotionEvent.obtain(1,2,action,100f,100f,0);v.onTouchEvent(e);e.recycle()}
  touch(MotionEvent.ACTION_DOWN);v.isEnabled=false;v.isEnabled=true;touch(MotionEvent.ACTION_UP)
  touch(MotionEvent.ACTION_DOWN);v.tool="text";touch(MotionEvent.ACTION_UP)
  touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_CANCEL);touch(MotionEvent.ACTION_UP)
  assertEquals(0,output.size)
  v.tool="pen";touch(MotionEvent.ACTION_DOWN);touch(MotionEvent.ACTION_UP);assertEquals(1,output.size)
  v.update(JSONObject(d.toString()).put("revision",1),null)
  touch(MotionEvent.ACTION_DOWN);completions[0]();touch(MotionEvent.ACTION_UP)
  assertEquals(2,output.size);assertEquals("stroke",output[1].getString("op"));v.clear()
 }

 @Test fun switchingDocumentsRestoresTheNewViewportAndDiscardsTheOldGesture() {
  val v=SdkDrawingView(ApplicationProvider.getApplicationContext());val first=doc()
  v.layout(0,0,360,360);v.update(first,SdkDrawingImages.image(first))
  fun saved(d:JSONObject,scale:Int)=JSONObject().put("id",d.getString("id")).put("scale",scale).put("x",.5).put("y",.5).toString()
  v.restoreViewport(saved(first,2));assertEquals(2.0,JSONObject(v.viewport()).getDouble("scale"),.01)
  val next=JSONObject(first.toString()).put("id","b".repeat(32));v.update(next,SdkDrawingImages.image(next));v.restoreViewport(saved(next,3))
  assertEquals(3.0,JSONObject(v.viewport()).getDouble("scale"),.01);v.clear()
 }

 @Test fun keyboardAndAccessibilityViewportActionsNeverCommitDrawingEdits(){
  val view=SdkDrawingView(ApplicationProvider.getApplicationContext());val data=doc();view.layout(0,0,360,420);view.update(data,SdkDrawingImages.image(data));var commits=0;view.send={_,_->commits++}
  val info=android.view.accessibility.AccessibilityNodeInfo.obtain();view.onInitializeAccessibilityNodeInfo(info)
  assertTrue(info.actionList.any{it.label=="Zoom in"});assertTrue(info.actionList.any{it.label=="Fit image"})
  assertFalse(SdkDrawingView.ZOOM_IN_ACTION in setOf(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_FOCUS,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
  assertTrue(view.performAccessibilityAction(SdkDrawingView.ZOOM_IN_ACTION,null));assertTrue(JSONObject(view.viewport()).getDouble("scale")>1)
  val before=JSONObject(view.viewport());assertTrue(view.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_RIGHT,android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_RIGHT)));assertTrue(JSONObject(view.viewport()).getDouble("x")>before.getDouble("x"))
  assertTrue(view.onKeyDown(android.view.KeyEvent.KEYCODE_0,android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_0)));assertEquals(1.0,JSONObject(view.viewport()).getDouble("scale"),.001)
  view.isEnabled=false;assertFalse(view.performAccessibilityAction(SdkDrawingView.ZOOM_IN_ACTION,null));view.clear();assertFalse(view.performAccessibilityAction(SdkDrawingView.FIT_ACTION,null));assertEquals(0,commits);info.recycle()
 }

}
