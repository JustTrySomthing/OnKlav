package app.onklav.home

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

/** Replaces the retired HDR acceptance checks with migration and exclusive-look regressions. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoHdrTest {
 private fun source()=Bitmap.createBitmap(24,16,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(155,94,48))}
 private fun pixel(e:PhotoEdit):Int {val b=source();val out=PhotoRender.render(b,e);return out.getPixel(12,8).also{out.recycle();b.recycle()}}
 @Test fun hdrKeysAreRemovedFromOldProjectsAndNeverAffectRendering(){
  val old=PhotoEdit.parse("""{"hdr":0.8,"hdrStyle":6,"hdrNatural":1,"exposure":0.4}""")
  assertEquals(setOf("exposure"),old.values.keys);assertEquals(pixel(PhotoEdit().set("exposure",.4f)),pixel(old));assertEquals(PhotoEdit(),PhotoEdit().set("hdrNatural",1f));assertTrue(PhotoHdr.controls.isEmpty())
 }
 @Test fun selectingAnotherLookReplacesThePreviousLookAndPreservesAdjustments(){
  val initial=PhotoEdit().set("exposure",.3f).set("saturation",1.8f).set("denoise",.2f)
  val noir=PhotoLooks.choose(initial,"noir");val sepia=PhotoLooks.choose(noir,"sepia")
  assertEquals("sepia",PhotoLooks.key(sepia));assertEquals(0f,sepia["noir"],0f);assertEquals(.3f,sepia["exposure"],0f);assertEquals(.2f,sepia["denoise"],0f)
  assertEquals(pixel(PhotoLooks.choose(initial,"sepia")),pixel(sepia));assertEquals(sepia,PhotoEdit.parse(sepia.json()))
 }
 @Test fun legacyStacksMigrateToOneExplicitLookWithoutDiscardingSourceAdjustments(){
  val e=PhotoEdit.parse("""{"colorMode":2,"tealOrange":0.6,"moody":0.7,"noir":0.8,"saturation":1.4}""")
  assertEquals("noir",PhotoLooks.key(e));assertEquals(.8f,PhotoLooks.amount(e),0f);assertEquals(1.4f,e["saturation"],0f);assertFalse(e.values.containsKey("moody"));assertFalse(e.values.containsKey("colorMode"));assertEquals(e,PhotoEdit.parse(e.json()))
 }
 @Test fun colorizeHonorsTintStrengthAndDoesNotLeaveNoirActive(){
  val base=PhotoEdit();val red=PhotoLooks.choose(PhotoLooks.choose(base,"noir"),"colorize").set("colorizeHue",0f).set("colorizeSaturation",1f)
  val original=pixel(base);val full=pixel(red);val half=pixel(red.set("lookStrength",.5f))
  assertTrue(Color.red(full)>50);assertEquals(0,Color.green(full));assertEquals(0,Color.blue(full));assertEquals(original,pixel(red.set("lookStrength",0f)))
  assertTrue(Color.green(half)>0&&Color.green(half)<Color.green(original));assertEquals(0f,red["noir"],0f)
  val blue=pixel(red.set("colorizeHue",240f));assertTrue(Color.blue(blue)>Color.red(blue));assertEquals(red,PhotoEdit.parse(red.json()))
 }
 @Test fun fullMonochromeExplainsColorSuppressionAndLowerStrengthRestoresColor(){
  val bw=PhotoLooks.choose(PhotoEdit(),"blackWhite");val p=pixel(bw);assertTrue(PhotoLooks.monochrome(bw));assertEquals(Color.red(p),Color.green(p));assertEquals(Color.red(p),Color.blue(p))
  val half=bw.set("lookStrength",.5f);assertFalse(PhotoLooks.monochrome(half));assertNotEquals(Color.red(pixel(half)),Color.blue(pixel(half)))
 }
 @Test fun neutralRenderDoesNotRotateHueOrModifyOpaquePixels(){val b=source();val out=PhotoRender.render(b,PhotoEdit());assertEquals(b.getPixel(12,8),out.getPixel(12,8));out.recycle();b.recycle()}
}
