package app.onklav.home
import android.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoMarkupTest{
 private fun stroke()=PhotoMark(points=listOf(Offset(.2f,.4f),Offset(.8f,.4f)),width=.02f,color=Color.RED)
 @Test fun legacyDraftsAndMarksRoundTripWithValidatedFiniteBounds(){
  assertEquals(PhotoEdit().set("angle",12f),PhotoEdit.parse("{\"angle\":12}"))
  val mark=stroke();val text=PhotoMark(tool="text",x=.1f,y=.6f,text="Hello 🔆\nImage Editor")
  val edit=PhotoEdit(mapOf("exposure" to .4f),listOf(mark,text));assertEquals(edit,PhotoEdit.parse(edit.json()))
  assertThrows(IllegalArgumentException::class.java){PhotoMarkup.validate(listOf(mark.copy(points=listOf(Offset(Float.NaN,.2f)))))}
  assertThrows(IllegalArgumentException::class.java){PhotoMarkup.validate(List(301){stroke()})}
  assertThrows(IllegalArgumentException::class.java){PhotoEdit.parse("{\"_marks\":[${mark.copy(x=2f).json()}]}")}
 }
 @Test fun marksStayOnFixedCanvasAfterImageGeometryAndNeverChangeSourcePixels(){
  val src=Bitmap.createBitmap(200,150,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.BLUE)};val mark=stroke()
  val e=PhotoEdit().set("colorMode",1f).set("angle",23f).set("left",.1f).copy(marks=listOf(mark))
  val out=PhotoRender.render(src,e);val base=PhotoRender.render(src,e.copy(marks=emptyList()))
  assertEquals(base.width,out.width);assertEquals(base.height,out.height)
  val at=floatArrayOf(100f,60f);val crop=photoCropBounds(src.width,src.height,e)
  val p=out.getPixel((at[0]-crop.left).toInt(),(at[1]-crop.top).toInt());assertTrue(Color.red(p)>220&&Color.blue(p)<40)
  assertEquals(Color.BLUE,src.getPixel(100,60));assertEquals(0,Color.red(base.getPixel((at[0]-crop.left).toInt(),(at[1]-crop.top).toInt()))-Color.blue(base.getPixel((at[0]-crop.left).toInt(),(at[1]-crop.top).toInt())))
 }
 @Test fun highlightIsTranslucentAndSourceNormalizedMarksScaleWithExport(){
  val mark=stroke().copy(tool="highlight",color=Color.YELLOW,width=.08f)
  for(size in listOf(100,400)){
   val src=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)};val out=PhotoRender.render(src,PhotoEdit(marks=listOf(mark)))
   val color=out.getPixel(size/2,(size*.4f).toInt());assertEquals(255,Color.red(color));assertTrue(Color.blue(color) in 120..200);assertEquals(Color.WHITE,src.getPixel(size/2,size/2))
  }
 }
 @Test fun fixedCanvasProjectionAndSegmentHitIgnoreImageGeometry(){
  val e=PhotoEdit().set("angle",33f).set("perspectiveX",.4f).set("left",.1f);val mark=stroke()
  val projection=photoMarkProjection(800,600,e,Size(432f,620f),1.7f,Offset(10f,-8f))
  val point=Offset(.5f,.4f);val screen=projection.map(point);val fixed=photoMarkProjection(800,600,e.copy(values=e.values.filterKeys{it !in photoGeometryKeys}),Size(432f,620f),1.7f,Offset(10f,-8f));assertEquals(fixed.map(point),screen);val inverse=projection.unmap(screen)!!;assertEquals(point.x,inverse.x,.0001f);assertEquals(point.y,inverse.y,.0001f)
  assertEquals(mark.id,projection.hit(listOf(mark),screen,15f)?.id) // Middle of a long segment, not just sampled vertices.
  val moved=PhotoMarkup.move(mark,Offset(2f,-2f),800,600);val b=PhotoMarkup.bounds(moved,800,600);assertTrue(abs(b.right-1)<.0001f);assertTrue(abs(b.top)<.0001f);moved.validate()
 }
 @Test fun temporaryCollapsedViewportDuringKeyboardResizeRemainsFinite(){
  val p=photoMarkProjection(640,480,PhotoEdit(),Size.Zero,1f,Offset.Zero)
  val point=p.map(Offset(.5f,.5f));assertTrue(point.x.isFinite()&&point.y.isFinite())
  val inverse=p.unmap(point)!!;assertEquals(.5f,inverse.x,.001f);assertEquals(.5f,inverse.y,.001f)
 }
 @Test fun croppedContextIncludesMarksWithExactSelectedExportPixels(){
  val src=Bitmap.createBitmap(120,90,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)}
  val e=PhotoEdit().set("angle",15f).set("left",.1f).set("right",.2f).set("exposure",.7f).copy(marks=listOf(stroke(),PhotoMark(tool="text",x=.2f,y=.5f,text="Label",color=Color.BLACK)))
  val context=photoCropContext(src,e);val out=PhotoRender.render(src,e);val crop=photoCropBounds(src.width,src.height,e)
  for(y in 0 until out.height)for(x in 0 until out.width)assertEquals(out.getPixel(x,y),context.getPixel(crop.left+x,crop.top+y))
 }
}
