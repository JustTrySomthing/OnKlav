package app.onklav.home

import android.graphics.Bitmap
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PhotoGestureTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));compose.mainClock.advanceTimeByFrame()}}
 @Test fun croppedAnchorStaysUnderFingersAndCropColorAngleRemainUnchanged(){
  val e=PhotoEdit().set("left",.2f).set("right",.1f).set("top",.1f).set("angle",25f).set("exposure",.6f)
  val viewport=Size(432f,650f);val center=Offset(216f,325f)
  val next=photoGeometryGesture(e,800,600,viewport,1f,Offset.Zero,center,Offset(25f,-20f),1.4f)
  assertEquals(1.4f,next["scaleX"],.0001f);assertEquals(1.4f,next["scaleY"],.0001f)
  assertEquals(e.values.filterKeys{it !in photoGeometryKeys},next.values.filterKeys{it !in photoGeometryKeys})
  assertEquals(25f,next["angle"],0f)
  val old=photoCropBounds(800,600,e);assertEquals(old,photoCropBounds(800,600,next))
  val display=minOf(viewport.width/old.width(),viewport.height/old.height())*.94f
  val pivot=Offset(old.exactCenterX(),old.exactCenterY())
  val expected=pivot+(Offset(400f,300f)-pivot)*1.4f+Offset(25f,-20f)/display
  assertEquals(expected.x,800*(.5f+next["moveX"]),.001f);assertEquals(expected.y,600*(.5f+next["moveY"]),.001f)
 }
 @Test(timeout=60000) fun sustainedNativeDragAndPinchSurviveRecompositionAndCommitOnce(){
  val edit=mutableStateOf(PhotoEdit());val geometry=mutableStateOf(true);val viewZoom=mutableFloatStateOf(1f);val viewPan=mutableStateOf(Offset.Zero);var commits=0;var updates=0
  val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.RED)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{PhotoCanvasPreview(bitmap,edit.value,geometry.value,viewZoom.floatValue,viewPan.value,{z,p->viewZoom.floatValue=z;viewPan.value=p},onGeometry={edit.value=it;updates++},onFinish={commits++})}}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  val bounds=compose.onNodeWithContentDescription("Edited image preview").fetchSemanticsNode().boundsInRoot
  val cx=bounds.center.x;val cy=bounds.center.y;val start=android.os.SystemClock.uptimeMillis()
  fun event(action:Int,points:List<Offset>){compose.runOnUiThread{
   val properties=Array(points.size){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
   val coordinates=Array(points.size){i->MotionEvent.PointerCoords().apply{x=points[i].x;y=points[i].y;pressure=1f;size=1f}}
   val event=MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),action,points.size,properties,coordinates,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
   compose.activity.window.decorView.dispatchTouchEvent(event);event.recycle()
  };pump()}
  event(MotionEvent.ACTION_DOWN,listOf(Offset(cx,cy)))
  for(i in 1..6)event(MotionEvent.ACTION_MOVE,listOf(Offset(cx+i*10,cy+i*4)))
  event(MotionEvent.ACTION_UP,listOf(Offset(cx+60,cy+24)))
  assertTrue("Drag must continue across state updates",updates>=4);assertTrue(edit.value["moveX"]>.1f);assertEquals(1,commits)
  val before=updates
  event(MotionEvent.ACTION_DOWN,listOf(Offset(cx-40,cy)))
  event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(Offset(cx-40,cy),Offset(cx+40,cy)))
  for(i in 1..6)event(MotionEvent.ACTION_MOVE,listOf(Offset(cx-40-i*8,cy),Offset(cx+40+i*8,cy)))
  event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(Offset(cx-88,cy),Offset(cx+88,cy)))
  event(MotionEvent.ACTION_UP,listOf(Offset(cx-88,cy)))
  assertTrue(updates-before>=4);assertTrue("Pinch must enlarge image",edit.value["scaleX"]>1.6f);assertEquals(2,commits)
  assertTrue(edit.value.values.keys.none{it in photoCropKeys})
  val saved=edit.value;compose.runOnUiThread{geometry.value=false};pump()
  event(MotionEvent.ACTION_DOWN,listOf(Offset(cx,cy)))
  for(i in 1..6)event(MotionEvent.ACTION_MOVE,listOf(Offset(cx+i*10,cy+i*4)))
  event(MotionEvent.ACTION_UP,listOf(Offset(cx+60,cy+24)))
  assertTrue("Inspection view can pan even at default zoom",viewPan.value.x>40f);assertEquals(saved,edit.value);assertEquals(2,commits)
  event(MotionEvent.ACTION_DOWN,listOf(Offset(cx-40,cy)))
  event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(Offset(cx-40,cy),Offset(cx+40,cy)))
  for(i in 1..6)event(MotionEvent.ACTION_MOVE,listOf(Offset(cx-40-i*8,cy),Offset(cx+40+i*8,cy)))
  event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(Offset(cx-88,cy),Offset(cx+88,cy)))
  event(MotionEvent.ACTION_UP,listOf(Offset(cx-88,cy)))
  assertTrue(viewZoom.floatValue>1.6f);assertEquals(saved,edit.value);assertEquals(2,commits)
 }
}
