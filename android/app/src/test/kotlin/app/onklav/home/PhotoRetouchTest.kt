package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoRetouchTest{
 private fun image(size:Int=200)=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(185,140,115))}
 private fun stroke(tool:String="redEye",x:Float=.5f,y:Float=.5f,width:Float=.06f)=PhotoRetouchStroke(tool,width,listOf(Offset(x,y)))
 @Test fun redEyeRemovesRedButPreservesCatchlightSkinAndOutsideBrush(){
  val b=image();b.setPixel(100,100,Color.rgb(230,25,30));b.setPixel(101,100,Color.WHITE);val skin=b.getPixel(98,98);val outside=b.getPixel(10,10)
  PhotoRetouch.apply(b,listOf(stroke()));val fixed=b.getPixel(100,100)
  assertTrue(Color.red(fixed)<60);assertEquals(25,Color.green(fixed));assertEquals(30,Color.blue(fixed));assertEquals(Color.WHITE,b.getPixel(101,100));assertEquals(skin,b.getPixel(98,98));assertEquals(outside,b.getPixel(10,10));b.recycle()
 }
 @Test fun healingReplacesDarkSpotWithNearbyTextureWithoutChangingOutsideOrAlpha(){
  val b=image();for(y in 0 until 200)for(x in 0 until 200){val noise=((x*13+y*7)%9)-4;b.setPixel(x,y,Color.rgb(185+noise,140+noise,115+noise))}
  for(y in 97..103)for(x in 97..103)b.setPixel(x,y,Color.rgb(55,35,30))
  b.setPixel(99,99,Color.argb(180,55,35,30));val outside=b.getPixel(110,110)
  PhotoRetouch.apply(b,listOf(stroke("heal")))
  assertTrue(Color.red(b.getPixel(100,100))>150);assertTrue(Color.green(b.getPixel(100,100))>110);assertEquals(180,Color.alpha(b.getPixel(99,99)));assertEquals(outside,b.getPixel(110,110));assertNotEquals(b.getPixel(100,100),b.getPixel(101,100));b.recycle()
 }
 @Test fun smallAndTransparentImagesAndEdgesAreSafe(){
  for(size in listOf(1,2,8,200)){val b=image(size);b.eraseColor(Color.TRANSPARENT);PhotoRetouch.apply(b,listOf(stroke("heal",0f,0f),stroke("redEye",1f,1f)));assertEquals(Color.TRANSPARENT,b.getPixel(0,0));b.recycle()}
 }
 @Test fun correctionsRoundTripWithLayersAndLegacyEditsStillLoad(){
  val e=PhotoEdit(retouch=listOf(stroke(),stroke("heal",.3f,.3f)));assertEquals(e,PhotoEdit.parse(e.json()));val state=PhotoLayers.State(e,listOf(PhotoEdit()),listOf(e));assertEquals(state,PhotoLayers.parse(state.json().toString()));assertTrue(PhotoEdit.parse("{\"exposure\":0.5}").retouch.isEmpty())
 }
 @Test fun invalidAndOversizedBrushWorkIsRejected(){
  for(s in listOf(stroke(width=Float.NaN),stroke(x=-1f),stroke("invalid"),stroke(width=1f))){assertTrue(runCatching{PhotoRetouch.validate(listOf(s))}.isFailure)}
  assertTrue(runCatching{PhotoRetouch.validate(List(81){stroke()})}.isFailure)
  assertTrue(runCatching{PhotoRetouch.validate(listOf(stroke().copy(points=List(256){Offset(.5f,.5f)})))}.isFailure)
 }
 @Test fun retouchCoordinatesFollowRotationCropZoomAndPan(){
  val e=PhotoEdit().set("angle",35f).set("left",.1f).set("top",.1f);val w=400;val h=300;val source=Offset(.55f,.45f);val xy=floatArrayOf(source.x*w,source.y*h);photoGeometry(w,h,e).matrix.mapPoints(xy)
  val viewport=Size(360f,420f);val pan=Offset(12f,-8f);val projection=photoMarkProjection(w,h,e,viewport,2f,pan);val screen=projection.map(Offset(xy[0]/w,xy[1]/h));val point=photoRetouchPoint(screen,w,h,e,viewport,2f,pan)!!
  assertEquals(source.x,point.x,.0001f);assertEquals(source.y,point.y,.0001f);assertNull(photoRetouchPoint(Offset(-900f,-900f),w,h,e,viewport,2f,pan))
 }
 @Test fun previewAndExportUseSameNormalizedCorrectionAndNeverChangeSource(){
  for(size in listOf(200,800)){val b=image(size);val cx=size/2;for(y in cx-2..cx+2)for(x in cx-2..cx+2)b.setPixel(x,y,Color.rgb(230,25,30));val out=PhotoRender.render(b,PhotoEdit(retouch=listOf(stroke())))
   assertTrue(Color.red(out.getPixel(cx,cx))<60);assertEquals(230,Color.red(b.getPixel(cx,cx)));out.recycle();b.recycle()}
 }
 @Test fun stampCopiesFrozenSampleWithFeatheredEdgesAndRoundTrips(){
  val b=image();for(y in 0 until 200)for(x in 0 until 200)b.setPixel(x,y,Color.rgb(x,80,100))
  val original=b.getPixel(100,100)
  val fix=PhotoRetouchStroke("clone",.08f,listOf(Offset(100f/199,100f/199),Offset(120f/199,100f/199)),Offset(80f/199,100f/199))
  assertTrue(PhotoRetouch.apply(b,listOf(fix))>0)
  assertEquals(80,Color.red(b.getPixel(100,100)));assertEquals(original,b.getPixel(120,100))
  assertTrue(Color.red(b.getPixel(100,107)) in 81..99);assertEquals(100,Color.red(b.getPixel(100,109)))
  val edit=PhotoEdit(retouch=listOf(fix));assertEquals(edit,PhotoEdit.parse(edit.json()));assertEquals(PhotoLayers.State(edit,emptyList(),emptyList()),PhotoLayers.parse(PhotoLayers.State(edit,emptyList(),emptyList()).json().toString()));b.recycle()
 }
 @Test fun stampRejectsInvalidSourceAndDoesNotWrapAtEdges(){
  assertTrue(runCatching{PhotoRetouch.validate(listOf(stroke("clone")))}.isFailure)
  assertTrue(runCatching{PhotoRetouch.validate(listOf(stroke("clone").copy(source=Offset(Float.NaN,.5f))))}.isFailure)
  val b=image();b.setPixel(0,100,Color.BLUE);val old=b.getPixel(0,100)
  PhotoRetouch.apply(b,listOf(PhotoRetouchStroke("clone",.06f,listOf(Offset(.5f,.5f),Offset(0f,.5f)),Offset(0f,.5f))))
  assertEquals(old,b.getPixel(0,100));b.recycle()
 }
 @Test fun hardStampPreservesTextureExactlyAndOnlyFeathersItsOuterEdge(){
  val b=image(400);for(y in 0 until 400)for(x in 0 until 180)b.setPixel(x,y,if((x+y)%2==0)Color.BLACK else Color.WHITE)
  val source=Offset(100f/399,200f/399);val target=Offset(300f/399,200f/399);val fix=PhotoRetouchStroke("clone",.08f,listOf(target),source,.9f)
  val original=b.getPixel(300,215);val donor=b.getPixel(100,215);PhotoRetouch.apply(b,listOf(fix))
  for(y in 189..211)for(x in 289..311)if(kotlin.math.hypot((x-300).toFloat(),(y-200).toFloat())<14f)assertEquals(b.getPixel(x-200,y),b.getPixel(x,y))
  assertNotEquals(original,b.getPixel(300,215));assertNotEquals(donor,b.getPixel(300,215));assertEquals(Color.rgb(185,140,115),b.getPixel(300,217))
  assertEquals(fix,PhotoRetouchStroke.parse(fix.json()));assertTrue(runCatching{PhotoRetouch.validate(listOf(fix.copy(hardness=Float.NaN)))}.isFailure)
  val old=fix.json().apply{remove("hardness")};assertEquals(.7f,PhotoRetouchStroke.parse(old).hardness);b.recycle()
 }
 @Test fun pinkFlashPupilsAreCorrectedAndNoOpIsReportedHonestly(){
  val b=image();assertEquals(0,PhotoRetouch.apply(b,listOf(stroke())))
  b.setPixel(100,100,Color.rgb(200,80,170));assertTrue(PhotoRetouch.apply(b,listOf(stroke()))>0)
  assertTrue(Color.red(b.getPixel(100,100))<130);assertEquals(80,Color.green(b.getPixel(100,100)));b.recycle()
 }

}
