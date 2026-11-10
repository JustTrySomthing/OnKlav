package app.onklav.home
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEditorHostTest {
 @Test fun cropHandlesClampMoveAndKeepOtherEdits(){
  val e=PhotoEdit().set("left",.1f).set("right",.2f).set("top",.15f).set("bottom",.1f).set("exposure",1f)
  val corner=movePhotoCrop(e,"lt",.2f,.1f)
  assertEquals(.3f,corner["left"],.0001f);assertEquals(.25f,corner["top"],.0001f);assertEquals(1f,corner["exposure"],0f)
  val crossed=movePhotoCrop(e,"rb",-2f,-2f)
  assertEquals(.05f,1-crossed["left"]-crossed["right"],.0001f)
  assertEquals(.05f,1-crossed["top"]-crossed["bottom"],.0001f)
  val moved=movePhotoCrop(e,"move",3f,-3f)
  assertEquals(0f,moved["right"],.0001f);assertEquals(0f,moved["top"],.0001f)
  assertEquals(.7f,1-moved["left"]-moved["right"],.0001f)
  val edge=movePhotoCrop(e,"l",-5f,1f);assertEquals(0f,edge["left"],0f);assertEquals(e["top"],edge["top"],0f)
 }
 @Test fun geometryHandlesAndColorEffectsHaveRealOutput(){
  val center=androidx.compose.ui.geometry.Offset.Zero;val origin=androidx.compose.ui.geometry.Offset(100f,0f)
  val scaled=photoGeometryDrag(PhotoEdit(),"scale",0,origin,origin*1.5f,center,200f)
  assertEquals(1.5f,scaled["scaleX"],.001f);assertEquals(1.5f,scaled["scaleY"],.001f)
  val rotated=photoGeometryDrag(PhotoEdit(),"angle",8,origin,androidx.compose.ui.geometry.Offset(0f,100f),center,200f)
  assertEquals(90f,rotated["angle"],.001f)
  val perspective=photoGeometryDrag(PhotoEdit(),"perspectiveX",0,origin,origin+androidx.compose.ui.geometry.Offset(0f,20f),center,200f)
  assertTrue(perspective["perspectiveX"]>0f)
  val wide=photoGeometryDrag(PhotoEdit(),"scale",5,origin,origin*2f,center,200f)
  assertEquals(2f,wide["scaleX"],0f);assertEquals(1f,wide["scaleY"],0f)
  val b=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);b.eraseColor(Color.argb(128,120,80,40))
  val sepia=PhotoRender.render(b,PhotoEdit().set("colorMode",2f));val p=sepia.getPixel(5,5)
  assertTrue(Color.red(p)>Color.green(p));assertTrue(Color.green(p)>Color.blue(p));assertEquals(128,Color.alpha(p))
  val gray=PhotoRender.render(b,PhotoEdit().set("colorMode",1f)).getPixel(5,5)
  assertTrue(kotlin.math.abs(Color.red(gray)-Color.blue(gray))<=1)
  val hdrSource=b.copy(Bitmap.Config.ARGB_8888,true);hdrSource.setPixel(5,5,Color.argb(128,145,95,55));val hdr=PhotoRender.render(hdrSource,PhotoEdit().set("hdr",1f));assertEquals(PhotoRender.render(hdrSource,PhotoEdit()).getPixel(5,5),hdr.getPixel(5,5));assertEquals(128,Color.alpha(hdr.getPixel(5,5)))
  val mapping=photoGeometry(20,10,scaled);assertEquals(20,mapping.width);assertEquals(10,mapping.height)
 }
 @Test fun geometryPreservesOriginalAndFlipsActualPixels(){
  val b=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);for(x in 10..19)for(y in 0..9)b.setPixel(x,y,Color.BLUE)
  val flip=PhotoRender.render(b,PhotoEdit().set("flipX",1f));assertEquals(Color.BLUE,flip.getPixel(2,5));assertEquals(Color.RED,b.getPixel(2,5))
  val turn=PhotoRender.render(b,PhotoEdit().set("turn",90f));assertEquals(20,turn.width);assertEquals(10,turn.height)
  val crop=PhotoRender.render(b,PhotoEdit().set("left",.25f).set("right",.25f).set("scaleX",2f));assertEquals(10,crop.width);assertEquals(10,crop.height)
 }
 @Test fun tonesTransparencyAndParameterRoundTrip(){
  val b=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);b.eraseColor(Color.argb(128,180,80,30))
  val e=PhotoEdit().set("saturation",0f);assertEquals(e,PhotoEdit.parse(e.json()));val out=PhotoRender.render(b,e);val p=out.getPixel(6,6);assertEquals(128,Color.alpha(p));assertTrue(kotlin.math.abs(Color.red(p)-Color.green(p))<=2);assertTrue(kotlin.math.abs(Color.blue(p)-Color.green(p))<=2)
  val brighter=PhotoRender.render(b,PhotoEdit().set("exposure",1f));assertTrue(Color.red(brighter.getPixel(6,6))>Color.red(b.getPixel(6,6)))
 }
}
