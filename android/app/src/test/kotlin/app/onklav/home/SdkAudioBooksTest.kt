package app.onklav.home

import android.app.Application
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkAudioBooksTest {
 class BooksProvider:ContentProvider(){
  val files=mutableMapOf<String,File>()
  override fun onCreate()=true
  override fun query(uri:Uri,p:Array<out String>?,s:String?,args:Array<out String>?,sort:String?):Cursor=MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).also{it.addRow(arrayOf(uri.lastPathSegment))}
  override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(files[uri.lastPathSegment]?:throw FileNotFoundException(),ParcelFileDescriptor.MODE_READ_ONLY)
  override fun getType(uri:Uri)="audio/mpeg"
  override fun insert(uri:Uri,values:ContentValues?):Uri?=null
  override fun delete(uri:Uri,s:String?,args:Array<out String>?)=0
  override fun update(uri:Uri,v:ContentValues?,s:String?,args:Array<out String>?)=0
 }
 private fun setup():Triple<Application,JSONObject,BooksProvider>{val c=ApplicationProvider.getApplicationContext<Application>();runBlocking{AppSdk.bootstrap(c)};val opened=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.audiobooks"))as JSONObject;val o=AppSdk.sync(c,JSONObject().put("action","media_component").put("session",opened.getString("session")).put("node","audiobooks.library"))as JSONObject
  val provider=BooksProvider();provider.attachInfo(c,android.content.pm.ProviderInfo().also{it.authority="audiobooks.test"});ShadowContentResolver.registerProviderInternal("audiobooks.test",provider);return Triple(c,o,provider)}
 private fun audio(c:Context,p:BooksProvider,name:String,bytes:ByteArray):Uri{val f=File(c.cacheDir,name);f.writeBytes(bytes);p.files[name]=f;return Uri.parse("content://audiobooks.test/$name")}
 @Test(timeout=60000)fun signedLibraryImportsNaturallyResumesAndRevokesLocalSourceReads(){
  val(c,o,provider)=setup();val bytes="DRM-free test source bytes".toByteArray();val two=audio(c,provider,"chapter2.mp3",bytes);val ten=audio(c,provider,"chapter10.mp3",bytes)
  val b=SdkAudioBooks.import(c,o,listOf(ten,two),"import-ok");val key=b.getString("id");val tracks=b.getJSONArray("tracks");assertEquals("chapter2",tracks.getJSONObject(0).getString("title"));assertEquals(2,SdkAudioBooks.offline(c,o,b));assertArrayEquals(bytes,provider.files["chapter2.mp3"]!!.readBytes())
  val item=SdkAudioBooks.sourceQueue(c,o,key).getJSONObject(1);SdkAudioBooks.recorded(c,o,item,123456,600000,1.5f);assertEquals(123456,SdkAudioBooks.progress(c,o,key).getLong("position_ms"));assertEquals(1.5,SdkAudioBooks.progress(c,o,key).getDouble("speed"),0.001)
  SdkAudioBooks.bookmark(c,o,key,1,123456,"A moment");SdkAudioBooks.reorder(c,o,key,1,0);assertEquals(0,SdkAudioBooks.progress(c,o,key).getInt("index"));assertEquals(0,SdkAudioBooks.get(c,o,key).getJSONArray("bookmarks").getJSONObject(0).getInt("index"))
  val source=SdkAudioBooks.open(c,o,SdkAudioBooks.sourceQueue(c,o,key).getJSONObject(0));val buffer=ByteArray(5);assertEquals(5,source.readAt(0,buffer,0,5));assertArrayEquals(bytes.take(5).toByteArray(),buffer)
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id",o.getString("id")).put("grants",JSONArray(listOf("storage.private","audio.playback"))))
  assertThrows(Exception::class.java){source.readAt(5,buffer,0,5)};source.close()
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id",o.getString("id")).put("grants",JSONArray(listOf("storage.private","audio.playback","audiobooks.library"))))
  assertEquals(1,SdkAudioBooks.library(c,o).size);SdkAudioBooks.finished(c,o,key,true);assertTrue(SdkAudioBooks.progress(c,o,key).getBoolean("finished"));SdkAudioBooks.remove(c,o,key);SdkAudioBooks.recorded(c,o,item,999,600000,1f);assertTrue(SdkAudioBooks.library(c,o).isEmpty());assertFalse(SdkAudioBooks.progress(c,o,key).has("position_ms"));assertArrayEquals(bytes,provider.files["chapter10.mp3"]!!.readBytes())
 }
 @Test(timeout=60000)fun failedMultiFileImportRollsBackWithoutLosingExistingBooks(){val(c,o,p)=setup();val uri=audio(c,p,"chapter1.mp3",ByteArray(128){1});val saved=SdkAudioBooks.import(c,o,listOf(uri),"first");assertThrows(Exception::class.java){SdkAudioBooks.import(c,o,listOf(uri,Uri.parse("content://audiobooks.test/missing.mp3")),"broken")};assertEquals(listOf(saved.getString("id")),SdkAudioBooks.library(c,o).map{it.getString("id")});val d=File(c.filesDir,"sdk-audiobooks/${o.getString("id")}");assertTrue(d.listFiles().orEmpty().none{it.name.startsWith(".import-")});assertEquals(128,p.files["chapter1.mp3"]!!.length());SdkAudioBooks.clearApp(c,o.getString("id"));assertTrue(p.files["chapter1.mp3"]!!.exists())}
 @Test fun publicCatalogProjectionRejectsCredentialsAndPrivateSources(){for(u in listOf("http://archive.org/a.mp3","https://user:pass@archive.org/a.mp3","https://127.0.0.1/a.mp3","https://archive.org.evil.test/a.mp3","https://example.org/a.mp3"))assertThrows(Exception::class.java){SdkAudioBooks.publicUrl(u)};assertEquals("https://ia800001.us.archive.org/a.mp3",SdkAudioBooks.publicUrl("https://ia800001.us.archive.org/a.mp3"));val b=SdkAudioBooks.catalogBook(JSONObject("""{"id":"52","title":"<b>Classic</b>","authors":[{"first_name":"A","last_name":"Writer"}],"language":"English","totaltime":"1:02:03"}"""));assertEquals("lv-52",b.getString("id"));assertEquals("Classic",b.getString("title"));assertEquals("A Writer",b.getString("author"));assertFalse(b.toString().contains("url"));assertEquals(3723000,SdkAudioBooks.playtime("1:02:03"));assertEquals(123000,SdkAudioBooks.playtime("123"));assertTrue(SdkAudioBooks.naturalCompare("part2.mp3","part10.mp3")<0)}
 private fun atom(type:String,bytes:ByteArray):ByteArray=ByteArrayOutputStream().also{out->DataOutputStream(out).use{it.writeInt(bytes.size+8);it.writeBytes(type);it.write(bytes)}}.toByteArray()
 @Test fun m4bNeroChaptersAndMalformedMarkersAreBounded(){val file=File.createTempFile("chapters",".m4b");val payload=ByteArrayOutputStream();DataOutputStream(payload).use{it.writeInt(0x01000000);it.writeInt(0);it.writeByte(2);for((pos,title) in listOf(0L to "Beginning",60000L to "Next")){val bytes=title.toByteArray();it.writeLong(pos*10000);it.writeByte(bytes.size);it.write(bytes)}};file.writeBytes(atom("moov",atom("udta",atom("chpl",payload.toByteArray()))));val marks=SdkAudioBookChapters.read(file);assertEquals(2,marks.length());assertEquals(60000,marks.getJSONObject(1).getLong("position_ms"));assertEquals("Next",marks.getJSONObject(1).getString("title"));file.writeBytes(atom("moov",atom("udta",atom("chpl",byteArrayOf(1,0,0,0,0,0,0,0,120)))));assertEquals(0,SdkAudioBookChapters.read(file).length());file.delete()}
 @Test fun progressUsesWholeBookAndFinishedState(){val b=JSONObject("""{"tracks":[{"duration_ms":100000},{"duration_ms":200000}]}""");assertEquals(50,progressPercent(b,JSONObject().put("index",1).put("position_ms",50000)));assertEquals(100,progressPercent(b,JSONObject().put("finished",true)));assertEquals("1:02:03",audioTime(3723000))}
 @Test fun cachedCatalogChaptersOpenWithoutAnotherNetworkRequest(){
  val(c,o,_)=setup();val row=JSONObject("""{"id":"987654","title":"Cached book","authors":[],"sections":[{"section_number":"1","title":"Chapter one","listen_url":"https://archive.org/download/book/chapter.mp3","playtime":"60"}]}""")
  val b=SdkAudioBooks.addCatalog(c,o,"lv-987654","${o.getString("id")}/cached",connect={error("Chapter preview must use the existing catalog record")},inLibrary=false,record=row)
  assertEquals("Chapter one",b.getJSONArray("tracks").getJSONObject(0).getString("title"));assertFalse(b.getBoolean("in_library"))
 }
 @Test fun archiveCatalogFallbackOpensOnlyOneCopyOfEachChapter(){
  val(c,o,_)=setup();val rows=SdkAudioBooks.archiveResults(JSONObject("""{"response":{"docs":[{"identifier":"mystery_fixture","title":"Mystery","creator":["Author"],"language":"English"}]}}"""))
  val meta="""{"metadata":{"title":"Mystery","creator":"Author"},"files":[{"name":"01.mp3","title":"One","format":"VBR MP3","length":"60"},{"name":"01_64kb.mp3","format":"64Kbps MP3","length":"60"},{"name":"02.mp3","title":"Two","format":"VBR MP3","length":"60"}]}""".toByteArray()
  val b=SdkAudioBooks.addCatalog(c,o,rows[0].getString("id"),"${o.getString("id")}/archive",connect={req->CallStub(req){response(req,200,meta)}},inLibrary=false,record=rows[0].getJSONObject("catalog_record"))
  assertEquals(2,b.getJSONArray("tracks").length());assertEquals("One",b.getJSONArray("tracks").getJSONObject(0).getString("title"));assertTrue(b.getJSONArray("tracks").getJSONObject(0).getString("url").startsWith("https://archive.org/download/"))
 }
 private class CallStub(val req:Request,val reply:()->Response):Call {var cancelled=false;override fun request()=req;override fun execute()=reply();override fun enqueue(responseCallback:Callback)=error("unused");override fun cancel(){cancelled=true};override fun isExecuted()=true;override fun isCanceled()=cancelled;override fun clone():Call=CallStub(req,reply);override fun timeout()=Timeout()}
 private fun response(req:Request,code:Int,bytes:ByteArray,location:String?=null)=Response.Builder().request(req).protocol(Protocol.HTTP_1_1).message("Fixture").code(code).body(bytes.toResponseBody()).header("Content-Type","audio/mpeg").apply{if(location!=null)header("Location",location)}.build()
 @Test(timeout=60000)fun interruptedPublicDownloadKeepsCompletedChaptersAndResumesOnlyMissingOnes(){
  val(c,o,_)=setup();val catalog="""{"books":[{"id":"52","title":"Classic","authors":[],"sections":[{"title":"One","listen_url":"https://archive.org/download/classic/one.mp3","playtime":"60"},{"title":"Two","listen_url":"https://archive.org/download/classic/two.mp3","playtime":"60"}]}]}""".toByteArray()
  val b=SdkAudioBooks.addCatalog(c,o,"lv-52","${o.getString("id")}/fixture",connect={r->CallStub(r){response(r,200,catalog)}});val key=b.getString("id");val bytes=ByteArray(128){it.toByte()};val requests=mutableListOf<String>()
  assertThrows(Exception::class.java){SdkAudioBooks.download(c,o,key,connect={r->requests.add(r.url.encodedPath+"/"+r.method);CallStub(r){if(r.url.encodedPath.endsWith("two.mp3")&&r.method=="GET")throw IOException("Interrupted fixture transport");response(r,200,bytes)}}){}}
  assertEquals(1,SdkAudioBooks.offline(c,o,SdkAudioBooks.get(c,o,key)));requests.clear()
  SdkAudioBooks.download(c,o,key,connect={r->requests.add(r.url.encodedPath);CallStub(r){response(r,200,bytes)}}){}
  assertTrue(requests.isNotEmpty());assertTrue(requests.all{it.endsWith("two.mp3")});assertEquals(2,SdkAudioBooks.offline(c,o,SdkAudioBooks.get(c,o,key)))
  val source=SdkAudioBooks.open(c,o,SdkAudioBooks.sourceQueue(c,o,key).getJSONObject(1));val buffer=ByteArray(8);assertEquals(8,source.readAt(32,buffer,0,8));assertArrayEquals(bytes.sliceArray(32..39),buffer);source.close()
  val d=File(c.filesDir,"sdk-audiobooks/${o.getString("id")}/$key");assertTrue(d.listFiles().orEmpty().none{it.name.endsWith(".part")})
 }
 @Test fun publicAudioRedirectsCannotEscapeTrustedHostsOrDowngradeTls(){
  for(target in listOf("http://archive.org/audio.mp3","https://evil.test/audio.mp3","https://127.0.0.1/audio.mp3"))assertThrows(Exception::class.java){SdkAudioBooks.resolve("https://archive.org/a.mp3","bad",connect={r->CallStub(r){response(r,302,byteArrayOf(),target)}}){}}
  val request=SdkAudioBooks.resolve("https://archive.org/a.mp3","good",connect={r->assertNull(r.header("Authorization"));assertNull(r.header("Cookie"));CallStub(r){if(r.url.host=="archive.org")response(r,302,byteArrayOf(),"https://ia800001.us.archive.org/a.mp3")else response(r,200,byteArrayOf(1))}}){};assertEquals("ia800001.us.archive.org",request.url.host)
 }

 private fun ints(vararg values:Int)=ByteArrayOutputStream().also{out->DataOutputStream(out).use{d->values.forEach{d.writeInt(it)}}}.toByteArray()
 @Test fun quickTimeTextChapterTrackResolvesSampleOffsetsAndTimes(){
  val names=listOf("Opening","Second chapter");val samples=names.map{n->ByteArrayOutputStream().also{out->DataOutputStream(out).use{d->d.writeShort(n.toByteArray().size);d.write(n.toByteArray())}}.toByteArray()}
  fun movie(offset:Int):ByteArray {val stbl=atom("stbl",atom("stsz",ints(0,0,2,*samples.map{it.size}.toIntArray()))+atom("stts",ints(0,1,2,60000))+atom("stsc",ints(0,1,1,2,1))+atom("stco",ints(0,1,offset)));val chapter=atom("trak",atom("tkhd",ints(0,0,0,2))+atom("mdia",atom("mdhd",ints(0,0,0,1000))+atom("minf",stbl)));val audio=atom("trak",atom("tref",atom("chap",ints(2))));return atom("moov",audio+chapter)}
  val initial=movie(0);val file=File.createTempFile("qt-chapters",".m4b");file.writeBytes(movie(initial.size+8)+atom("mdat",samples.reduce{a,b->a+b}));val rows=SdkAudioBookChapters.read(file);assertEquals(2,rows.length());assertEquals("Second chapter",rows.getJSONObject(1).getString("title"));assertEquals(60000L,rows.getJSONObject(1).getLong("position_ms"));file.delete()
 }

 @Test(timeout=60000)fun previewIsNotInMyLibraryUntilSavedAndImportedBookStartsWithDefaultGrants(){
  val(c,o,p)=setup();val catalog="""{"books":[{"id":"52","title":"Preview","authors":[],"sections":[{"section_number":"1","title":"First","listen_url":"https://archive.org/download/preview/first.mp3","playtime":"60"}]}]}""".toByteArray()
  val b=SdkAudioBooks.addCatalog(c,o,"lv-52","preview-test",connect={r->CallStub(r){response(r,200,catalog)}},inLibrary=false)
  assertFalse(b.getBoolean("in_library"));assertTrue(SdkAudioBooks.library(c,o).none{it.optBoolean("in_library",true)})
  val item=SdkAudioBooks.sourceQueue(c,o,b.getString("id")).getJSONObject(0);SdkAudioBooks.recorded(c,o,item,0,0,1f);assertFalse(SdkAudioBooks.progress(c,o,b.getString("id")).has("updated"));SdkAudioBooks.recorded(c,o,item,25000,60000,1f)
  assertTrue(SdkAudioBooks.progress(c,o,b.getString("id")).getBoolean("listened"));SdkAudioBooks.keep(c,o,b.getString("id"));assertTrue(SdkAudioBooks.get(c,o,b.getString("id")).getBoolean("in_library"))
  val uri=audio(c,p,"play.mp3",ByteArray(128){1});val local=SdkAudioBooks.import(c,o,listOf(uri),"play-default")
  org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider{org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(60000,0)}
  try{val state=SdkAudioBooks.start(c,o,local.getString("id"));assertEquals("preparing",state.getString("state"))}finally{SdkMedia.stop()}
 }

 @Test(timeout=60000)fun actualLibriVoxCatalogBuildsAllItsChapterSources(){
  val file=System.getenv("ONKLAV_AUDIOBOOK_COMPAT_PATH")?.let(::File);org.junit.Assume.assumeTrue("Live API record is opt-in",file?.isFile==true)
  val(c,o,_)=setup();val raw=file!!.readBytes();val record=JSONObject(String(raw)).getJSONArray("books").getJSONObject(0)
  val b=SdkAudioBooks.addCatalog(c,o,"lv-"+record.getString("id"),"actual-catalog",connect={r->CallStub(r){response(r,200,raw)}},inLibrary=false)
  assertEquals(record.getJSONArray("sections").length(),b.getJSONArray("tracks").length());assertTrue(b.getJSONArray("tracks").length()>0);assertFalse(b.getBoolean("in_library"))
  for(i in 0 until b.getJSONArray("tracks").length())assertTrue(b.getJSONArray("tracks").getJSONObject(i).getString("url").startsWith("https://"))
 }

}
