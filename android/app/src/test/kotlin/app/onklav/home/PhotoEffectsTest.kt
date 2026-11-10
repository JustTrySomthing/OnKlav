package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoEffectsTest {
    private fun gray(v:Int,alpha:Int=255)=Color.argb(alpha,v,v,v)
    private fun light(p:Int)=.299f*Color.red(p)+.587f*Color.green(p)+.114f*Color.blue(p)
    private fun pixels(b:Bitmap)=IntArray(b.width*b.height).also{b.getPixels(it,0,b.width,0,0,b.width,b.height)}




    @Test fun sharpeningHasVisibleStrengthWithoutChangingFlatAreasOrAlpha(){
        val b=Bitmap.createBitmap(128,32,Bitmap.Config.ARGB_8888)
        for(y in 0 until 32)for(x in 0 until 128)b.setPixel(x,y,gray(if(x<64)100 else 140,128))
        val normal=PhotoRender.render(b,PhotoEdit());val sharp=PhotoRender.render(b,PhotoEdit().set("sharpness",1f))
        val before=light(normal.getPixel(64,16))-light(normal.getPixel(63,16));val after=light(sharp.getPixel(64,16))-light(sharp.getPixel(63,16))
        assertTrue("Full sharpening must be clearly stronger than the original edge",after>before*1.7f)
        assertEquals(normal.getPixel(20,16),sharp.getPixel(20,16));assertEquals(128,Color.alpha(sharp.getPixel(64,16)))
        assertTrue("Halo amplitude remains bounded",abs(light(sharp.getPixel(63,16))-light(normal.getPixel(63,16)))<=27)
    }
    @Test fun threeLooksHaveDistinctGradingAndRemovedLooksAreNeutral(){
        val b=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);b.eraseColor(Color.argb(128,150,100,60))
        val normal=PhotoRender.render(b,PhotoEdit());val zero=PhotoRender.render(b,PhotoEdit().set("hdr",0f).set("sharpness",0f).set("tealOrange",0f).set("moody",0f).set("noir",0f).set("technicolor",0f))
        assertArrayEquals(pixels(normal),pixels(zero))
        val outputs=listOf("tealOrange","moody","noir").map{key->val state=PhotoEdit().set(key,1f);assertEquals(state,PhotoEdit.parse(state.json()));PhotoRender.render(b,state)}
        assertEquals(3,outputs.map{it.getPixel(20,20)}.toSet().size)
        assertTrue(light(outputs[1].getPixel(20,20))<light(normal.getPixel(20,20)))
        val noir=outputs[2].getPixel(20,20);assertEquals(Color.red(noir),Color.green(noir));assertEquals(Color.green(noir),Color.blue(noir))
        assertTrue(light(outputs[2].getPixel(0,0))<light(noir))
        for(key in listOf("technicolor","comic","pencil"))assertArrayEquals(pixels(normal),pixels(PhotoRender.render(b,PhotoEdit().set(key,1f))))
        outputs.forEach{assertEquals(128,Color.alpha(it.getPixel(20,20)))}
        val shadow=PhotoEffects.grade(.15f,.15f,.15f,255,0f,PhotoEffects.Looks(1f,0f,0f,0f));assertTrue(Color.blue(shadow)>Color.red(shadow))
        val highlight=PhotoEffects.grade(.7f,.6f,.5f,255,0f,PhotoEffects.Looks(1f,0f,0f,0f));assertTrue(Color.red(highlight)-Color.blue(highlight)>51)
    }

    @Test fun renderedLookContactSheet(){
        val w=320;val h=240;val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        for(y in 0 until h)for(x in 0 until w){val texture=((sin(x*.23)*cos(y*.19))*9).toInt();val sky=y<125;val r=(if(sky)100+y/4 else 45+y/8)+texture;val g=(if(sky)160+y/5 else 75+y/7)+texture;val blue=(if(sky)205-y/5 else 45+y/12)+texture;b.setPixel(x,y,Color.rgb(r.coerceIn(0,255),g.coerceIn(0,255),blue.coerceIn(0,255)))}
        val c=Canvas(b);val paint=Paint(3);paint.color=Color.rgb(195,133,93);c.drawCircle(220f,118f,37f,paint);paint.color=Color.rgb(115,45,35);c.drawRect(185f,154f,255f,240f,paint);paint.color=Color.rgb(40,50,25);c.drawCircle(40f,155f,70f,paint)
        val cases=listOf("Original" to "","Sharpen" to "sharpness","Teal & Orange" to "tealOrange","Dark & Moody" to "moody","Film Noir" to "noir")
        val sheet=Bitmap.createBitmap(w*4,(h+32)*2,Bitmap.Config.ARGB_8888);val canvas=Canvas(sheet);canvas.drawColor(Color.rgb(25,25,28));paint.textSize=18f
        cases.forEachIndexed{i,(label,key)->val edit=if(key.isEmpty())PhotoEdit()else PhotoEdit().set(key,1f);val out=PhotoRender.render(b,edit);val x=(i%4)*w;val y=(i/4)*(h+32);canvas.drawBitmap(out,x.toFloat(),y.toFloat(),null);paint.color=Color.WHITE;canvas.drawText(label,x+10f,y+h+22f,paint);out.recycle()}
        val f=File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/pictures-hdr-fix/effects-contact-sheet.png");f.parentFile!!.mkdirs();f.outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
