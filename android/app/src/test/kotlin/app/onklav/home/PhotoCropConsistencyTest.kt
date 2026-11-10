package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCropConsistencyTest {
 private fun image():Bitmap=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888).also{b->for(y in 0 until b.height)for(x in 0 until b.width)b.setPixel(x,y,Color.argb(if(x<12)128 else 255,40+x*2,25+y*3,200-x))}
 @Test fun cropContextIncludesAllEditsAndSelectedPixelsMatchExport(){
  val src=image();val before=IntArray(src.width*src.height);src.getPixels(before,0,src.width,0,0,src.width,src.height)
  val base=PhotoEdit().set("left",.1f).set("right",.15f).set("top",.2f).set("bottom",.1f).set("exposure",.4f).set("colorMode",2f).set("vignette",.6f).set("hdr",.6f).set("sharpness",.8f).set("technicolor",.35f)
  for(e in listOf(base,base.set("turn",90f).set("flipX",1f).set("scaleX",1.4f),base.set("angle",31f).set("perspectiveX",.65f).set("perspectiveY",-.35f).set("scaleY",1.2f))){
   val context=photoCropContext(src,e);val actual=PhotoRender.geometry(context,PhotoEdit(e.values.filterKeys{it in photoCropKeys}));val expected=PhotoRender.render(src,e)
   assertEquals(expected.width,actual.width);assertEquals(expected.height,actual.height)
   val a=IntArray(actual.width*actual.height);val b=IntArray(a.size)
   actual.getPixels(a,0,actual.width,0,0,actual.width,actual.height);expected.getPixels(b,0,expected.width,0,0,expected.width,expected.height)
   assertArrayEquals("Selected crop must match actual export with geometry/colors/effects",b,a)
   assertNotEquals(src.getPixel(3,30),context.getPixel(3,30))
   context.recycle();actual.recycle();expected.recycle()
  }
  val after=IntArray(before.size);src.getPixels(after,0,src.width,0,0,src.width,src.height);assertArrayEquals(before,after);src.recycle()
 }
 @Test fun transformedHandlesUseInverseMappingAndPreserveEveryOtherEdit(){
  val e=PhotoEdit().set("left",.15f).set("right",.1f).set("top",.1f).set("bottom",.2f).set("turn",90f).set("angle",27f).set("flipX",1f).set("scaleX",1.5f).set("scaleY",.7f).set("perspectiveX",.6f).set("perspectiveY",-.3f).set("colorMode",2f).set("moody",.8f)
  val p=photoCropProjection(800,600,e,IntSize(432,690))!!
  val neutral=photoCropProjection(800,600,PhotoEdit(e.values.filterKeys{it in photoCropKeys}),IntSize(432,690))!!
  assertEquals(neutral.corners(e),p.corners(e))
  val c=photoCropBounds(800,600,e);val origin=p.map(Offset(c.left.toFloat(),c.top.toFloat()))
  assertEquals("lt",photoCropHit(origin,e,p,24f))
  val point=p.map(Offset(c.left+80f,c.top+30f));val moved=photoCropDrag(e,"lt",origin,point,p)
  assertEquals(e["left"]+.1f,moved["left"],.0001f);assertEquals(e["top"]+.05f,moved["top"],.0001f)
  assertEquals(e.values.filterKeys{it !in photoCropKeys},moved.values.filterKeys{it !in photoCropKeys})
  for(v in listOf(Offset(340f,270f),Offset(120f,60f),Offset(720f,480f))){val back=p.unmap(p.map(v))!!;assertEquals(v.x,back.x,.002f);assertEquals(v.y,back.y,.002f)}
  val right=p.handles(e).first{it.first=="r"}.second;assertEquals("r",photoCropHit(right,e,p,20f))
 }
 @Test fun extremePerspectiveDoesNotCollapseOrInvertTheVisibleSelection(){
  for(px in listOf(-1f,1f))for(py in listOf(-1f,1f)){
   val e=PhotoEdit().set("left",.45f).set("right",.45f).set("top",.45f).set("bottom",.45f).set("angle",70f).set("perspectiveX",px).set("perspectiveY",py).set("scaleX",2f).set("scaleY",.1f)
   val p=photoCropProjection(1200,900,e,IntSize(432,690))!!
   for(q in p.corners(e)){assertTrue(q.x.isFinite()&&q.y.isFinite());assertTrue(q.x in 0f..432f&&q.y in 0f..690f);assertNotNull(p.unmap(q))}
  }
 }
}
