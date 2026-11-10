package app.onklav.home

import android.app.Application
import android.graphics.*
import android.net.Uri
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
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompressTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var root:File
 @Before fun setup(){root=File(app.cacheDir,"compress-test-${java.util.UUID.randomUUID()}").apply{mkdirs()}}
 @After fun cleanup(){root.deleteRecursively()}
 private fun file(name:String)=File(root,name)
 private fun image():File {val out=file("noise.png");val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);val random=java.util.Random(5);val pixels=IntArray(640*480){0xff000000.toInt() or random.nextInt(0xffffff)};bitmap.setPixels(pixels,0,640,0,0,640,480);out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();return out}
 private fun pump(){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))}
 private fun waitFor(model:CompressModel){compose.waitUntil(15000){pump();!model.busy};assertFalse(model.status,model.error)}
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val view=compose.activity.window.decorView;val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap));File("/workspace/scratch/c739b0c1372b",name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Test fun imageReencodingProducesSmallerPlayableCopiesAndKeepsOriginal()=runBlocking {
  val original=image();val bytes=original.readBytes();val source=CompressEngine.inspect(original,"noise.png",CompressKind.IMAGE)
  for(webp in listOf(false,true)){val out=file(if(webp)"small.webp"else"small.jpg");CompressEngine.compress(app,source,CompressOptions(quality=0,imageEdge=256,webp=webp),out){};assertTrue(out.length()<source.bytes);val bitmap=BitmapFactory.decodeFile(out.path);assertEquals(256,bitmap.width);assertEquals(192,bitmap.height);bitmap.recycle()}
  assertArrayEquals(bytes,original.readBytes())
 }
 @Test fun zipPreservesBytesPathsEmptyFoldersAndDetectsUnsafeOrDuplicatePaths(){
  val text=file("text.txt").apply{writeText("Content worth keeping\n".repeat(3000))};val binary=file("data.bin").apply{writeBytes(ByteArray(1024){it.toByte()})}
  val entries=listOf(CompressEntry("Folder"),CompressEntry("Folder/Empty"),CompressEntry("Folder/text.txt",text),CompressEntry("Folder/data.bin",binary));val out=file("bundle.zip");CompressEngine.zip(entries,out)
  ZipFile(out).use{zip->assertTrue(zip.getEntry("Folder/Empty/").isDirectory);assertArrayEquals(text.readBytes(),zip.getInputStream(zip.getEntry("Folder/text.txt")).readBytes());assertArrayEquals(binary.readBytes(),zip.getInputStream(zip.getEntry("Folder/data.bin")).readBytes())};assertTrue(out.length()<text.length()+binary.length())
  for(bad in listOf(listOf(CompressEntry("../escape",text)),listOf(CompressEntry("same",text),CompressEntry("same",binary)))){try{CompressEngine.zip(bad,file("bad.zip"));fail("Unsafe archive accepted")}catch(_:IllegalArgumentException){}}
  val used=mutableSetOf<String>();assertEquals("same.txt",CompressEngine.uniqueName("same.txt",used));assertEquals("same (2).txt",CompressEngine.uniqueName("SAME.txt",used).lowercase())
 }
 @Test fun selectedFolderUsesSignedFilesTraversalPreservesHierarchyAndHonorsRevocation()=runBlocking {
  AppSdk.bootstrap(app)
  val provider=FileSelectionProvider().apply{attachInfo(app,android.content.pm.ProviderInfo().apply{authority=app.packageName+".file-selection";exported=false})}
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(app.packageName+".file-selection",provider)
  val path=FileSelectionSession(app).use{session->session.start();session.navigate(listOf("documents"));session.event("new.folder");session.event("name","Bundle");session.event("name.save");val id=session.state.nodes("items").single{it.optString("name")=="Bundle"}.getString("id");val at=listOf("documents",id)
   session.navigate(at);session.event("new.folder");session.event("name","Empty");session.event("name.save")
   session.importSaved(at,file("note.txt").apply{writeText("Folder content")},"note.txt","text/plain");at}
  val uri=FileSelectionStore.folder(app,path,"Bundle");val stage=file("stage").apply{mkdirs()};val source=compressFolder(app,uri,stage)
  assertEquals(14L,source.bytes);val out=file("folder.zip");CompressEngine.zip(source.entries,out)
  ZipFile(out).use{zip->assertTrue(zip.getEntry("Bundle/Empty/").isDirectory);assertEquals("Folder content",zip.getInputStream(zip.getEntry("Bundle/note.txt")).reader().readText())}
  AppSdk.sync(app,org.json.JSONObject().put("action","permissions").put("id","app.onklav.files").put("grants",org.json.JSONArray()))
  try{compressFolder(app,uri,file("revoked").apply{mkdirs()});fail("Revoked Files access accepted")}catch(_:Exception){}
 }
 @Test fun mediaEstimatesFollowQualityAndResolutionWithoutUpscaling(){
  val video=CompressSource("video",CompressKind.VIDEO,listOf(CompressEntry("video",file("video"))),1_000_000,60.0,1920,1080)
  val small=CompressEngine.estimate(video,CompressOptions(quality=0,videoEdge=480))!!;val big=CompressEngine.estimate(video,CompressOptions(quality=2,videoEdge=1080))!!;assertTrue(big>small);assertEquals(854 to 480,CompressEngine.dimensions(video,480))
  assertEquals(640 to 360,CompressEngine.dimensions(video.copy(width=640,height=360),1080))
  val portrait=video.copy(width=1080,height=1920);assertEquals(480 to 854,CompressEngine.dimensions(portrait,480))
  val audio=video.copy(kind=CompressKind.AUDIO);assertTrue(CompressEngine.estimate(audio,CompressOptions(quality=2))!!>CompressEngine.estimate(audio,CompressOptions(quality=0))!!)
  assertNull(CompressEngine.estimate(audio.copy(seconds=0.0),CompressOptions()))
 }
 @Test fun completeImageFlowShowsMeasuredSizeSavesOriginalUnchangedAndHasDone(){
  val original=image();val bytes=original.readBytes();val model=CompressModel(app);var closed=false
  compose.activity.setContent{OnKlavTheme{CompressWorkspace({closed=true},model)}}
  capture("compress-home.png")
  compose.runOnUiThread{model.choose(CompressKind.IMAGE);model.open(listOf(Uri.fromFile(original)))};waitFor(model)
  compose.waitUntil(15000){pump();!model.estimating};assertNotNull(model.estimate)
  capture("compress-options.png")
  val estimate=model.estimate!!
  compose.onNodeWithText("Size preview").assertIsDisplayed();compose.onNode(hasText("Compress images") and hasClickAction()).assertIsDisplayed().performClick();waitFor(model);assertNotNull(model.result);assertEquals(estimate,model.result!!.length())
  compose.onNodeWithText("Save compressed copy").performScrollTo().assertIsDisplayed();capture("compress-result.png")
  val saved=file("saved.jpg");compose.runOnUiThread{model.save(Uri.fromFile(saved))};waitFor(model);assertNotNull(model.saved);assertArrayEquals(bytes,original.readBytes());assertNotNull(BitmapFactory.decodeFile(saved.path))
  compose.onNodeWithText("Share saved copy").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Done").performScrollTo().performClick();assertTrue(closed)
 }
 @Test @Config(qualifiers="w640dp-h360dp-mdpi") fun landscapeCanReachFilesAndFolderChoice(){
  val model=CompressModel(app);compose.activity.setContent{OnKlavTheme{CompressWorkspace({},model)}}
  compose.onNodeWithText("Files & folders").performScrollTo().performClick();compose.onNodeWithText("Choose folder").performScrollTo().assertIsDisplayed();capture("compress-landscape.png")
 }
 @Test @Config(fontScale=1.5f) fun largeTextKeepsCategoriesAndControlsReachable(){
  val model=CompressModel(app);compose.activity.setContent{OnKlavTheme{CompressWorkspace({},model)}}
  compose.onNodeWithText("Audio").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Files & folders").performScrollTo().assertIsDisplayed();capture("compress-large-text.png")
 }
 @Test fun cancelledCompressionAndOpenFailureKeepThePreviousSourceUsable(){
  val original=image();val model=CompressModel(app);compose.runOnUiThread{model.choose(CompressKind.IMAGE);model.open(listOf(Uri.fromFile(original)))};waitFor(model);val previous=model.source
  compose.runOnUiThread{model.open(listOf(Uri.fromFile(file("missing.png"))))};compose.waitUntil(15000){pump();!model.busy};assertTrue(model.error);assertSame(previous,model.source)
  compose.runOnUiThread{model.compress();model.cancel()};compose.waitUntil(15000){pump();!model.busy};assertNull(model.result);assertTrue(original.exists())
 }
}
