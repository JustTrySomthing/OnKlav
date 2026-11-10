package app.onklav.home
import android.graphics.*
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCanvasTest {
 @Test fun rotationScalePerspectiveNeverResizeOrRotateTheCropCanvas(){
  val src=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
  val crop=PhotoEdit().set("left",.1f).set("right",.2f).set("top",.1f).set("bottom",.15f)
  val frame=photoCropProjection(80,60,crop,IntSize(432,650))!!.corners(crop)
  for(edit in listOf(crop.set("angle",42f),crop.set("turn",90f),crop.set("scaleX",2.5f).set("scaleY",.3f),crop.set("perspectiveX",.9f).set("perspectiveY",-.8f),crop.set("moveX",.8f))){
   val out=PhotoRender.render(src,edit);val bounds=photoCropBounds(src.width,src.height,crop)
   assertEquals(bounds.width(),out.width);assertEquals(bounds.height(),out.height);assertEquals(frame,photoCropProjection(80,60,edit,IntSize(432,650))!!.corners(edit));out.recycle()
  };src.recycle()
 }
 @Test fun transformedImageIsClippedInsideCanvasWithRealTransparentEdges(){
  val src=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
  val out=PhotoRender.render(src,PhotoEdit().set("angle",90f))
  assertEquals(40,out.width);assertEquals(20,out.height);assertEquals(0,Color.alpha(out.getPixel(0,10)));assertEquals(Color.RED,out.getPixel(20,10))
  val zoom=PhotoRender.render(src,PhotoEdit().set("scaleX",3f).set("scaleY",3f));assertEquals(40,zoom.width);assertEquals(20,zoom.height);assertEquals(Color.RED,zoom.getPixel(0,0));assertEquals(Color.RED,src.getPixel(0,0))
  out.recycle();zoom.recycle();src.recycle()
 }
 @Test fun fitCentersPerspectiveWithinTheSelectedCanvas(){
  val e=PhotoEdit().set("left",.2f).set("right",.1f).set("top",.15f).set("bottom",.1f).set("angle",37f).set("perspectiveX",.8f).set("perspectiveY",-.6f)
  val fit=fitPhotoImage(800,600,e);val crop=photoCropBounds(800,600,e)
  for(p in photoGeometry(800,600,fit).corners){assertTrue(p.x>=crop.left-.01f&&p.x<=crop.right+.01f);assertTrue(p.y>=crop.top-.01f&&p.y<=crop.bottom+.01f)}
 }
 @Test fun fitAndResetChangeImageOnlyAndPrecisionCombinesLegacyQuarterTurns(){
  val crop=PhotoEdit().set("left",.25f).set("right",.25f).set("turn",90f).set("exposure",.6f)
  val fit=fitPhotoImage(800,600,crop);assertEquals(crop.values.filterKeys{it in photoCropKeys},fit.values.filterKeys{it in photoCropKeys});assertEquals(.6f,fit["exposure"],0f)
  assertEquals(90f,photoAdjustmentValue(crop,"angle"),0f);val fine=photoAdjustment(crop,"angle",.1f);assertEquals(.1f,photoAdjustmentValue(fine,"angle"),.0001f);assertEquals(0f,fine["turn"],0f)
  val linked=photoAdjustment(crop.set("scaleX",2f).set("scaleY",.5f),"scale",1.2f);assertEquals(4f,linked["scaleX"]/linked["scaleY"],.001f)
 }
}
