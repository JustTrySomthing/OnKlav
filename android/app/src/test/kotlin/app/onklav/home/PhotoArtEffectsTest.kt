package app.onklav.home
import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import kotlin.math.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoArtEffectsTest {
 private fun pixels(b:Bitmap)=IntArray(b.width*b.height).also{b.getPixels(it,0,b.width,0,0,b.width,b.height)}
 @Test fun zeroStrengthIsNeutralAndArtPreservesDimensionsAlphaAndHistory(){
  val b=Bitmap.createBitmap(96,72,Bitmap.Config.ARGB_8888)
  for(y in 0 until b.height)for(x in 0 until b.width)b.setPixel(x,y,Color.argb(if(x<5)0 else 170,x*2,y*3,110))
  val before=pixels(b);PhotoArtEffects.comic(b,0f);PhotoArtEffects.pencil(b,0f);PhotoArtEffects.reduceNoise(b,0f);assertArrayEquals(before,pixels(b))
  for(key in listOf("comic","pencil","denoise")){val edit=PhotoEdit().set(key,1f);assertEquals(edit,PhotoEdit.parse(edit.json()));val out=PhotoRender.render(b,edit);assertEquals(b.width,out.width);assertEquals(b.height,out.height);for(y in 1 until 70)for(x in 1 until 94)assertEquals(Color.alpha(b.getPixel(x,y)),Color.alpha(out.getPixel(x,y)));out.recycle()}
  assertArrayEquals(before,pixels(b))
 }
 @Test fun noiseReductionImprovesFlatRegionsAndKeepsStrongEdges(){
  val b=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888);val random=java.util.Random(42)
  for(y in 0 until 80)for(x in 0 until 160){val level=if(x<80)75 else 180
   fun noisy()=(level+random.nextGaussian()*13).roundToInt().coerceIn(0,255)
   b.setPixel(x,y,Color.rgb(noisy(),noisy(),noisy()))}
  fun error(image:Bitmap):Double{var e=0.0;var n=0;for(y in 8..71)for(x in 8..151){if(x in 72..87)continue;val p=image.getPixel(x,y);val ideal=if(x<80)75 else 180;for(v in listOf(Color.red(p),Color.green(p),Color.blue(p))){e+=(v-ideal).toDouble().pow(2);n++}};return e/n}
  val before=error(b);PhotoArtEffects.reduceNoise(b,1f);val after=error(b);assertTrue("Noise error $before -> $after",after<before*.65)
  val left=(12..65).map{Color.red(b.getPixel(79,it))}.average();val right=(12..65).map{Color.red(b.getPixel(80,it))}.average();assertTrue("No bleeding across the edge",right-left>95)
 }
 @Test fun pencilHasGraphiteContoursAndTonalShadingWithoutColorCast(){
  val b=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(Color.rgb(210,200,180));val p=Paint();p.color=Color.rgb(45,70,60);c.drawCircle(80f,50f,30f,p)
  PhotoArtEffects.pencil(b,1f);val values=pixels(b);assertTrue(values.maxOf{Color.red(it)}-values.minOf{Color.red(it)}>100)
  assertTrue(values.all{Color.red(it)==Color.green(it)&&Color.green(it)==Color.blue(it)})
 }
 @Test(timeout=60000) fun largeImageProcessingUsesBoundedRowsAndPreservesSource(){
  val b=Bitmap.createBitmap(1800,1200,Bitmap.Config.ARGB_8888);val c=Canvas(b);c.drawColor(Color.rgb(160,180,210));val paint=Paint(3).apply{color=Color.rgb(75,100,70)};c.drawCircle(900f,780f,570f,paint)
  val before=b.getPixel(900,780);val times=org.json.JSONObject()
  for(key in listOf("denoise","comic","pencil")){val start=System.nanoTime();val out=PhotoRender.render(b,PhotoEdit().set(key,.8f));times.put(key,(System.nanoTime()-start)/1000000);assertEquals(1800,out.width);assertEquals(1200,out.height);assertEquals(255,Color.alpha(out.getPixel(900,780)));assertEquals(before,b.getPixel(900,780));out.recycle()}
  File(System.getProperty("onklav.project.root"),"docs/evidence/v0.100.14/host-processing-ms.json").writeText(org.json.JSONObject().put("scope","Host native processing on 1800x1200 image; not phone performance qualification").put("milliseconds",times).toString(2))
 }
 @Test fun scenicFixtureShowsSmoothTonesAndDistinctArtStyles(){
  val root=File(System.getProperty("onklav.project.root"));val raw=BitmapFactory.decodeFile(File(root,"android/shared/src/main/assets/backgrounds/alpine.webp").path)!!
  val b=Bitmap.createScaledBitmap(raw,320,(raw.height*320f/raw.width).roundToInt(),true);raw.recycle()
  val sheet=Bitmap.createBitmap(b.width*4,b.height+32,Bitmap.Config.ARGB_8888);val c=Canvas(sheet);c.drawColor(Color.rgb(20,23,29));val p=Paint(3).apply{color=Color.WHITE;textSize=18f}
  for((i,item) in listOf("Original" to "","HDR" to "hdr","Comic book" to "comic","Pencil drawing" to "pencil").withIndex()){
   val out=PhotoRender.render(b,if(item.second.isEmpty())PhotoEdit()else PhotoEdit().set(item.second,1f));c.drawBitmap(out,(i*b.width).toFloat(),0f,null);c.drawText(item.first,i*b.width+10f,b.height+23f,p);out.recycle()
  }
  File(root,"docs/evidence/v0.100.14/scenic-quality-sheet.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
 @Test fun realPhotoContactSheetAndDeterministicResults(){
  val root=File(System.getProperty("onklav.project.root"));val raw=BitmapFactory.decodeFile(File(root,"docs/evidence/v0.100.14/quality-source.png").path)!!
  val b=Bitmap.createScaledBitmap(raw,420,(raw.height*420f/raw.width).roundToInt(),true);raw.recycle()
  val noisy=b.copy(Bitmap.Config.ARGB_8888,true);val random=java.util.Random(21)
  for(y in 0 until noisy.height)for(x in 0 until noisy.width){val p=noisy.getPixel(x,y)
   fun n(v:Int)=(v+random.nextGaussian()*14).roundToInt().coerceIn(0,255)
   noisy.setPixel(x,y,Color.rgb(n(Color.red(p)),n(Color.green(p)),n(Color.blue(p))))}
  val cases=listOf(Triple("Original",b,PhotoEdit()),Triple("Comic book",b,PhotoEdit().set("comic",1f)),Triple("Pencil drawing",b,PhotoEdit().set("pencil",1f)),Triple("HDR",b,PhotoEdit().set("hdr",.8f)),Triple("Noisy source",noisy,PhotoEdit()),Triple("Reduce noise",noisy,PhotoEdit().set("denoise",1f)))
  val sheet=Bitmap.createBitmap(b.width*3,(b.height+32)*2,Bitmap.Config.ARGB_8888);val canvas=Canvas(sheet);canvas.drawColor(Color.rgb(20,23,29));val paint=Paint(3).apply{textSize=19f;color=Color.WHITE}
  cases.forEachIndexed{i,(name,source,edit)->val out=PhotoRender.render(source,edit);val x=(i%3)*b.width;val y=(i/3)*(b.height+32);canvas.drawBitmap(out,x.toFloat(),y.toFloat(),null);canvas.drawText(name,x+12f,y+b.height+23f,paint);out.recycle()}
  val f=File(root,"docs/evidence/v0.100.14/photo-quality-sheet.png");f.outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
  for(key in listOf("comic","pencil","denoise")){val a=PhotoRender.render(b,PhotoEdit().set(key,.75f));val c=PhotoRender.render(b,PhotoEdit().set(key,.75f));assertArrayEquals(pixels(a),pixels(c));assertTrue(pixels(a).toSet().size>100);a.recycle();c.recycle()}
 }
}

/** UI automation scrolls directly; unbounded ScrollToNode loops on paused native roots. */
internal fun photoTestOptionIndex(label:String):Int=when(label){
 "Square · 1:1"->1;"Rotate 90 degrees"->6;"Scale"->1
 else->(PhotoLooks.controls+listOf("sharpness" to "Sharpen","denoise" to "Reduce noise","vignette" to "Vignette")).indexOfFirst{it.second==label}.also{require(it>=0)}
}
