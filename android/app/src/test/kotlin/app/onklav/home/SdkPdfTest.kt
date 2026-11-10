package app.onklav.home

import android.app.Application
import android.graphics.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*

/** Protocol/backend-seam checks, not a claim that Android PDFium ran on the host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkPdfTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private class Fake:PdfEngine{
  override var pages=2;override var textSupported=true;var closed=false;var failRender=false;var points:Pair<Point,Point>?=null
  override fun size(page:Int)=420 to 600
  override fun render(page:Int,width:Int,out:OutputStream){if(failRender)error("Broken renderer");out.write(byteArrayOf(1,2,3))}
  override fun text(page:Int)="x".repeat(60000)
  override fun search(page:Int,query:String,after:Int)=List(if(page==0)60 else 3){listOf(RectF(it.toFloat(),10f,it+5f,20f))}.drop(after).take(41)
  override fun select(page:Int,start:Point,end:Point):Pair<String,List<RectF>>{points=start to end;return "Selected text" to listOf(RectF(0f,0f,10f,20f))}
  override fun close(){closed=true}
 }
 private fun execute(a:Bundle,engine:Fake=Fake(),out:OutputStream?=null):Bundle{val f=File(c.cacheDir,"pdf-test-source");f.writeText("%PDF-1.4\nfixture");return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY).use{SdkPdfProtocol.execute(it,a,out){engine}}}
 private fun request(op:String,page:Int=0)=Bundle().apply{putString("op",op);putInt("page",page)}
 @Test fun renderingDimensionsBoundPixelCountAndRejectInvalidGeometry(){for((w,h)in listOf(420 to 600,20000 to 20000,1 to 20000)){val(x,y)=SdkPdfProtocol.dimensions(w,h,1600);assertTrue(x*y<=SdkPdfProtocol.MAX_PIXELS);assertTrue(x in 1..1600);assertTrue(y in 1..4096)};assertThrows(Exception::class.java){SdkPdfProtocol.dimensions(0,600,1000)};assertThrows(Exception::class.java){SdkPdfProtocol.dimensions(420,600,2000)}}
 @Test fun engineAndPageRequestFailuresDoNotLeaveAnOpenRenderer(){val engine=Fake().apply{failRender=true};assertThrows(Exception::class.java){execute(request("render"),engine,ByteArrayOutputStream())};assertTrue(engine.closed);val invalid=Fake();assertThrows(Exception::class.java){execute(request("render",2),invalid,ByteArrayOutputStream())};assertTrue(invalid.closed)}
 @Test fun infoAndRasterRemainAvailableWhenAdvancedTextIsUnsupported(){val old=Fake().apply{textSupported=false};val info=execute(request("info"),old);assertEquals(2,info.getInt("pages"));assertFalse(info.getBoolean("text_supported"));assertTrue(old.closed);val render=Fake().apply{textSupported=false};val bytes=ByteArrayOutputStream();execute(request("render"),render,bytes);assertEquals(3,bytes.size());val search=Fake().apply{textSupported=false};assertThrows(Exception::class.java){execute(request("search").apply{putString("query","needle")},search)};assertTrue(search.closed)}
 @Suppress("DEPRECATION") @Test fun boundedSearchContinuationDoesNotSkipMatchesOnTheSamePage(){val first=execute(request("search").apply{putString("query","needle")});assertEquals(40,first.getParcelableArrayList<Bundle>("matches")!!.size);assertEquals(0,first.getInt("next_page"));assertEquals(40,first.getInt("next_match"));val second=execute(request("search").apply{putString("query","needle");putInt("after",40)});assertEquals(23,second.getParcelableArrayList<Bundle>("matches")!!.size);assertEquals(-1,second.getInt("next_page"));assertThrows(Exception::class.java){execute(request("search").apply{putString("query","")})}}
 @Suppress("DEPRECATION") @Test fun selectionClampsCoordinatesAndTextHasExplicitLimits(){val fake=Fake();val result=execute(request("select").apply{putFloatArray("points",floatArrayOf(-50f,-10f,10000f,10000f))},fake);assertEquals(Point(0,0),fake.points!!.first);assertEquals(Point(420,600),fake.points!!.second);assertEquals("Selected text",result.getString("text"));assertTrue(result.getParcelableArrayList<Bundle>("bounds")!!.isNotEmpty());assertEquals(50000,execute(request("text")).getString("text")!!.length);assertEquals("A",SdkPdfProtocol.boundedText("A😀",2));assertThrows(Exception::class.java){execute(request("select").apply{putFloatArray("points",floatArrayOf(Float.NaN,0f,0f,1f))})}}
 private fun description(id:String="org.example.pdf",sha:String="a".repeat(64)):JSONObject{val handle="b".repeat(32);val f=File(c.filesDir,"app-sdk/data/$id/private-blobs-v1/$handle.data");f.parentFile!!.mkdirs();f.writeText("%PDF-1.4\nsource");return JSONObject().put("owner",JSONObject().put("id",id).put("publisher","c".repeat(64)).put("hash","d".repeat(64))).put("file",JSONObject().put("id",handle).put("sha256",sha).put("path",f.absolutePath).put("size",f.length()))}
 @Test fun nativeSourceMustBeAnOwnedCanonicalPrivatePdf(){val own=description();assertTrue(SdkPdfClient.source(c,own).isFile);val foreign=JSONObject(own.toString());foreign.getJSONObject("owner").put("id","org.example.other");assertThrows(Exception::class.java){SdkPdfClient.source(c,foreign)};val nonPdf=description();File(nonPdf.getJSONObject("file").getString("path")).writeText("not a pdf here");assertThrows(Exception::class.java){SdkPdfClient.source(c,nonPdf)}}
 @Test fun savedPagesArePinnedToPublisherAndContentAndRemovalIsOwnerOnly(){val one=description();val other=description("org.example.other");SdkPdfPositions.save(c,one,1,3){};SdkPdfPositions.save(c,other,2,3){};assertEquals(1,SdkPdfPositions.read(c,one,3));val changed=JSONObject(one.toString());changed.getJSONObject("file").put("sha256","f".repeat(64));assertEquals(0,SdkPdfPositions.read(c,changed,3));val publisher=JSONObject(one.toString());publisher.getJSONObject("owner").put("publisher","e".repeat(64));assertEquals(0,SdkPdfPositions.read(c,publisher,3));SdkPdfPositions.clearApp(c,"org.example.pdf");assertEquals(0,SdkPdfPositions.read(c,one,3));assertEquals(2,SdkPdfPositions.read(c,other,3));assertThrows(Exception::class.java){SdkPdfPositions.save(c,other,1,3){error("Revoked")}}}
}
