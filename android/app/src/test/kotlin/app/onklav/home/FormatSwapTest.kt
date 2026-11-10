package app.onklav.home

import android.app.Application
import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormatSwapTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var root:File
 @Before fun setup(){root=File(app.cacheDir,"swap-test-${java.util.UUID.randomUUID()}").apply{mkdirs()}}
 @After fun cleanup(){root.deleteRecursively()}
 private fun file(name:String)=File(root,name)
 @Test fun imageConversionUsesRealEncodingResizesAndFlattensTransparency()=runBlocking {
  val source=file("transparent.png");val b=Bitmap.createBitmap(24,12,Bitmap.Config.ARGB_8888);b.eraseColor(Color.TRANSPARENT);source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  val input=FormatSwapEngine.prepare(source,source.name,SwapKind.IMAGE)
  val out=file("converted.jpg");FormatSwapEngine.convert(app,input,SwapFormat.JPG,SwapOptions(maxEdge=12),out){}
  val bytes=out.readBytes();assertEquals(0xff,bytes[0].toInt() and 255);assertEquals(0xd8,bytes[1].toInt() and 255)
  val image=BitmapFactory.decodeFile(out.path);assertEquals(12,image.width);assertEquals(6,image.height);assertTrue(Color.red(image.getPixel(0,0))>245);image.recycle()
  for(format in listOf(SwapFormat.PNG,SwapFormat.WEBP)){val dest=file("test.${format.ext}");FormatSwapEngine.convert(app,input,format,SwapOptions(),dest){};assertTrue(dest.length()>20);if(format!=SwapFormat.PDF)assertNotNull(BitmapFactory.decodeFile(dest.path))else assertTrue(dest.readBytes().take(4).toByteArray().toString(Charsets.US_ASCII)=="%PDF")}
 }
 @Test fun pcmConversionPreservesSamplesAndWritesCorrectWaveAndAiffHeaders(){
  val pcm=file("input.pcm");val samples=listOf(-1f,-.5f,0f,.5f,1f);AudioPcmWriter(pcm).use{w->samples.forEach{w.sample(it)}};val source=AudioSource(pcm,44100,1,5,"test")
  val wav=file("out.wav");FormatSwapEngine.pcm(source,wav,false);assertEquals(54L,wav.length());RandomAccessFile(wav,"r").use{r->r.seek(24);assertEquals(44100,Integer.reverseBytes(r.readInt()));r.seek(44);assertEquals(-32768,java.lang.Short.reverseBytes(r.readShort()).toInt())}
  val aiff=file("out.aiff");FormatSwapEngine.pcm(source,aiff,true);RandomAccessFile(aiff,"r").use{r->assertEquals(0x464f524d,r.readInt());assertEquals(aiff.length()-8,r.readInt().toLong());r.seek(28);assertEquals(0x400e,r.readUnsignedShort());assertEquals(44100L shl 48,r.readLong());r.seek(54);assertEquals(-32768,r.readShort().toInt())}
  val restored=AudioEditMedia.wave(wav,file("decoded.pcm"),"copy",{},{});assertNotNull(restored);assertEquals(5L,restored!!.frames);AudioPcmReader(restored.file,1).use{assertEquals(.5f,it.get(3,0),.0001f)}
 }
 @Test fun documentsProduceRealDocxHtmlTextAndEscapedRtf()=runBlocking{
  val doc=WriteDocument(title="Sample",text="Hello {reader} \\ world\nCafé 😀")
  for(format in listOf(SwapFormat.DOCX,SwapFormat.HTML,SwapFormat.TXT,SwapFormat.RTF)){FormatSwapEngine.document(doc,format,file("sample.${format.ext}"))}
  assertEquals(doc.text+"\n",WriteExport.importDocx(file("sample.docx").readBytes()).text)
  val rtf=file("sample.rtf").readText();assertTrue(rtf.startsWith("{\\rtf1"));assertTrue(rtf.contains("\\{reader\\}"));assertTrue(rtf.contains("\\u233?"))
  assertEquals(doc.text,file("sample.txt").readText());assertTrue(file("sample.html").readText().contains("Café"))
 }
 @Test fun textImportRejectsBinaryAndOdtPreservesParagraphsAndRejectsEntities(){
  assertEquals("Hello",FormatSwapEngine.decodeText(byteArrayOf(0xff.toByte(),0xfe.toByte())+"Hello".toByteArray(Charsets.UTF_16LE)))
  try{FormatSwapEngine.decodeText(byteArrayOf(0,1,2));fail("binary accepted")}catch(_:IllegalArgumentException){}
  fun odt(xml:String):File=file("test.odt").also{f->ZipOutputStream(f.outputStream()).use{z->z.putNextEntry(ZipEntry("content.xml"));z.write(xml.toByteArray());z.closeEntry()}}
  val input=FormatSwapEngine.prepare(odt("<office xmlns:text='urn:oasis:names:tc:opendocument:xmlns:text:1.0'><text:p>One</text:p><text:p>Two<text:tab/>three</text:p></office>"),"test.odt",SwapKind.DOCUMENT)
  assertEquals("One\nTwo\tthree",input.document!!.text);assertTrue(input.warning.contains("Not preserved"))
  try{FormatSwapEngine.prepare(odt("<!DOCTYPE x [<!ENTITY x 'bad'>]><x/>"),"test.odt",SwapKind.DOCUMENT);fail("entity accepted")}catch(_:IllegalArgumentException){}
 }
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File("/workspace/scratch/c739b0c1372b",name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun imageSaveFlowKeepsOriginalAndOffersDoneAndAnotherFile(){
  val source=file("original.png");val bitmap=Bitmap.createBitmap(16,8,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();val original=source.readBytes()
  val model=FormatSwapModel(app);var closed=false
  compose.activity.setContent{OnKlavTheme{FormatSwapWorkspace({closed=true},model)}}
  compose.runOnUiThread{model.choose(SwapKind.IMAGE);model.open(android.net.Uri.fromFile(source))}
  compose.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));!model.busy};assertFalse(model.status,model.error);assertNotNull(model.input)
  compose.onNodeWithText("JPG").performClick()
  capture("format-swap-options.png")
  compose.onNodeWithText("Convert to JPG").performScrollTo().performClick()
  compose.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));!model.busy};assertFalse(model.status,model.error);assertTrue(model.ready)
  compose.onNodeWithText("Save converted file").performScrollTo().assertIsDisplayed()
  val output=file("saved.jpg");compose.runOnUiThread{model.save(android.net.Uri.fromFile(output))}
  compose.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));!model.busy};assertFalse(model.status,model.error);assertNotNull(model.saved)
  assertArrayEquals(original,source.readBytes());assertNotNull(BitmapFactory.decodeFile(output.path))
  compose.onNodeWithText("Convert another file").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Done").performScrollTo().performClick();assertTrue(closed)
 }
 @Test @Config(qualifiers="w640dp-h360dp-mdpi") fun landscapeCanScrollToAllFourCategories(){
  compose.activity.setContent{OnKlavTheme{FormatSwapWorkspace({},FormatSwapModel(app))}}
  compose.onNodeWithText("Document").performScrollTo().assertIsDisplayed()
  capture("format-swap-landscape.png")
 }
 @Test @Config(qualifiers="w360dp-h720dp-mdpi",fontScale=1.5f) fun largeTextKeepsCardsReadable(){
  compose.activity.setContent{OnKlavTheme{FormatSwapWorkspace({},FormatSwapModel(app))}}
  compose.onNodeWithText("Document").performScrollTo().assertIsDisplayed()
  capture("format-swap-large-text.png")
 }
 @Test fun silentVideoDoesNotOfferAudioExtraction(){
  val input=SwapInput(file("silent.mp4"),"Silent",SwapKind.VIDEO,"No audio",hasAudio=false)
  assertEquals(listOf(SwapFormat.MP4),input.targets)
 }
 @Test fun homeAndFormatFlowHaveClearStepsAndHonestTargets(){
  val model=FormatSwapModel(app)
  compose.activity.setContent{OnKlavTheme{FormatSwapWorkspace({},model)}}
  compose.onNodeWithText("What kind of file do you want to convert").assertIsDisplayed()
  capture("format-swap-home.png")
  listOf("Audio","Video","Image","Document").forEach{compose.onNodeWithText(it).assertExists()}
  compose.runOnUiThread{model.choose(SwapKind.DOCUMENT)}
  compose.onNodeWithText("1  File").assertExists();compose.onNodeWithText("2  Format").assertExists();compose.onNodeWithText("3  Save").assertExists();compose.onNodeWithText("Choose file").assertIsDisplayed()
  compose.runOnUiThread{val v=compose.activity.window.decorView;val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File("/workspace/scratch/c739b0c1372b/format-swap-file-screen.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 }
}
