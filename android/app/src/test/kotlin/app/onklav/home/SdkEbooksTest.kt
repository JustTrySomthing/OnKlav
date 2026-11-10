package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkEbooksTest {
 @Test fun catalogSummaryIsShortAndDoesNotIncludeSourceFluff(){
  val xml="""<feed><content type="text">Title: Example
Summary:
Alice follows a rabbit into an imaginary world. She meets strange creatures. This is a third sentence.
Reading Level: Easy
Author: Carroll</content></feed>"""
  assertEquals("Alice follows a rabbit into an imaginary world. She meets strange creatures.",SdkEbooks.summary(xml.toByteArray()))
 }

 @Test fun providerXhtmlSummaryIncludesOnlyShortDescription(){val bytes=File(System.getProperty("onklav.project.root"),"docs/fixtures/gutenberg-11-summary.xml").readBytes();val text=SdkEbooks.summary(bytes);assertTrue(text.contains("Alice"));assertTrue(text.length in 40..360);assertFalse(text.contains("Reading Level:"));assertFalse(text.contains("<p>"))}
 @Test fun libraryOwnershipIncludesLegacyBooksBeyondFirstPageAndStaysScoped()=runBlocking<Unit>{
  val c=c();val owner=JSONObject().put("id","app.onklav.ebooks");val root=File(c.filesDir,"app-sdk/data/app.onklav.ebooks").apply{mkdirs()};val rows=JSONObject();repeat(60){i->val id=i.toString().padStart(32,'0');rows.put(id,JSONObject().put("id",id).put("title","Book $i"))};val id="f".repeat(32);rows.put(id,JSONObject().put("id",id).put("title","Alice"));File(root,"collection-ebooks_library_v1.json").writeText(JSONObject().put("rows",rows).toString());File(root,"private-blobs-v1.json").writeText(JSONObject().put("entries",JSONObject().put(id,JSONObject().put("name","Gutenberg-11.epub").put("ready",true))).toString());assertEquals(setOf(11L),SdkEbooks.owned(c,owner));assertTrue(SdkEbooks.owned(c,JSONObject().put("id","org.example.other")).isEmpty())
  var touched=false;try{SdkEbooks.acquire(c,"duplicate",{JSONObject().put("args",JSONObject().put("id",11)).put("owner",owner)},{_,_->touched=true;JSONObject()},connect={touched=true;error("Network must not run")});fail("Expected duplicate guard")}catch(e:IllegalStateException){assertEquals("Already in your library",e.message)};assertFalse(touched)
  rows.remove(id);File(root,"collection-ebooks_library_v1.json").writeText(JSONObject().put("rows",rows).toString());assertTrue(SdkEbooks.owned(c,owner).isEmpty())
 }
 private class Stub(val req:Request,val f:()->Response):Call{private var cancelled=false;override fun request()=req;override fun execute()=f();override fun enqueue(c:Callback)=error("unused");override fun cancel(){cancelled=true};override fun isExecuted()=true;override fun isCanceled()=cancelled;override fun clone():Call=Stub(req,f);override fun timeout()=Timeout()}
 private fun response(r:Request,b:ByteArray)=Response.Builder().request(r).protocol(Protocol.HTTP_1_1).message("Fixture").code(200).body(b.toResponseBody()).build()
 private fun meta()="""<feed xmlns="http://www.w3.org/2005/Atom"><link rel="next" href="/ebooks/search.opds/?query=alice&amp;start_index=26"/><entry><id>https://www.gutenberg.org/ebooks/authors/search.opds/?query=alice</id><title>Authors</title></entry><entry><id>https://www.gutenberg.org/ebooks/11.opds</id><title>Alice &amp; friends</title><content>Lewis Carroll</content><link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="https://www.gutenberg.org/ebooks/11.epub3.images"/></entry></feed>""".toByteArray()
 private fun c()=ApplicationProvider.getApplicationContext<Application>()
 private fun call(c:Application,j:JSONObject)=AppSdk.sync(c,j)as JSONObject
 private fun launch(c:Application):JSONObject {runBlocking{AppSdk.bootstrap(c)};return call(c,JSONObject().put("action","launch").put("id","app.onklav.ebooks"))}
 private fun event(c:Application,session:String,a:String,value:String="")=call(c,JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",a).put("value",value)))
 private fun pending(c:Application,s:String):JSONObject {event(c,s,"book.discover");val p=event(c,s,"book.search").getJSONObject("pending");return call(c,JSONObject().put("action","reply").put("session",s).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",JSONObject().put("items",JSONArray().put(JSONObject().put("id",11).put("title","Alice").put("author","Carroll"))).put("next",JSONObject.NULL)))).let{val detail=event(c,s,"book.preview.11").getJSONObject("pending");call(c,JSONObject().put("action","reply").put("session",s).put("token",detail.getString("token")).put("reply",JSONObject().put("ok",false).put("error","No summary in fixture")));event(c,s,"book.acquire.11").getJSONObject("pending")}}
 @Test fun opdsProjectsOnlyBooksAndKeepsOpaquePaging(){val p=SdkEbooks.parse(meta());assertEquals(1,p.items.length());assertEquals(11L,p.items.getJSONObject(0).getLong("id"));assertEquals("Alice & friends",p.items.getJSONObject(0).getString("title"));assertEquals(26,p.next);assertEquals("https://www.gutenberg.org/ebooks/11.epub3.images",p.epub);assertFalse(p.items.toString().contains("https://"));assertThrows(Exception::class.java){SdkEbooks.parse("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///secret'>]><feed/>".toByteArray())};for(u in listOf("http://www.gutenberg.org/a","https://user:pass@www.gutenberg.org/a","https://127.0.0.1/a","https://gutenberg.org.evil.test/a"))assertThrows(Exception::class.java){SdkEbooks.url(u)}}
 @Test(timeout=60000)fun signedAcquisitionUsesScopedBlobThenResumesChapterAndBookmarks()=runBlocking<Unit>{
  val c=c();val first=launch(c);val s=first.getString("session");val p=pending(c,s);val token=p.getString("token")
  fun auth()=call(c,JSONObject().put("action","authorize").put("session",s).put("token",token))
  fun transfer(op:String,a:JSONObject)=call(c,JSONObject().put("action","blob_host").put("session",s).put("token",token).put("operation",op).put("args",a))
  val root=File(System.getProperty("onklav.project.root"));val bytes=File(root,"docs/fixtures/text-profile.epub").readBytes();val saved=SdkEbooks.acquire(c,token,::auth,::transfer,connect={r->assertNull(r.header("Authorization"));assertTrue(r.header("User-Agent")!!.contains("onklav.app"));Stub(r){response(r,if(r.url.toString().endsWith(".opds"))meta()else bytes)}})
  var f=call(c,JSONObject().put("action","reply").put("session",s).put("token",token).put("reply",JSONObject().put("ok",true).put("value",saved)));assertEquals("ready",f.getJSONObject("frame").getJSONObject("state").getString("view"));f=event(c,s,"book.read");assertEquals("read",f.getJSONObject("frame").getJSONObject("state").getString("view"));val body=f.getJSONObject("frame").getJSONArray("body");assertTrue((0 until body.length()).any{body.getJSONObject(it).optString("id")=="ebook.read.aloud"});assertEquals("OnKlav sample book",f.getJSONObject("frame").getJSONObject("state").getJSONObject("active").getString("title"))
  event(c,s,"book.bookmark");f=event(c,s,"book.marks");assertTrue(f.toString().contains("book.mark.goto.0"));event(c,s,"book.marks.close");f=call(c,JSONObject().put("action","speech_event").put("session",s).put("node","ebook.read.aloud"));assertEquals(1,f.getJSONObject("frame").getJSONObject("state").getJSONObject("reader").getJSONObject("chapter").getInt("chapter"));AppSdk.sync(c,JSONObject().put("action","close").put("session",s))
  val reopened=launch(c);val id=saved.getString("id");event(c,reopened.getString("session"),"book.recent");f=event(c,reopened.getString("session"),"book.open.$id");assertEquals(1,f.getJSONObject("frame").getJSONObject("state").getJSONObject("reader").getJSONObject("chapter").getInt("chapter"));assertEquals(1,f.getJSONObject("frame").getJSONObject("state").getJSONObject("active").getJSONArray("marks").length())
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.ebooks").put("grants",JSONArray().put("storage.private")));assertThrows(Exception::class.java){call(c,JSONObject().put("action","host_speech").put("session",reopened.getString("session")).put("widget","ebook.read.aloud"))}
 }
 @Test(timeout=60000)fun failedDownloadAbortsOnlyItsOwnNewStagingFile()=runBlocking<Unit>{val c=c();val opened=launch(c);val s=opened.getString("session");val p=pending(c,s);val token=p.getString("token");var aborted=false
  try{SdkEbooks.acquire(c,token,{call(c,JSONObject().put("action","authorize").put("session",s).put("token",token))},{op,a->if(op=="abort")aborted=true;call(c,JSONObject().put("action","blob_host").put("session",s).put("token",token).put("operation",op).put("args",a))},connect={r->Stub(r){if(r.url.toString().endsWith(".opds"))response(r,meta())else throw IOException("Interrupted fixture")}});fail("Expected interruption")}catch(expected:IOException){assertTrue(aborted)}
  call(c,JSONObject().put("action","reply").put("session",s).put("token",token).put("reply",JSONObject().put("ok",false).put("error","Interrupted")));assertEquals(0,event(c,s,"book.library").getJSONObject("frame").getJSONObject("state").getJSONArray("library").length())
 }
 @Test(timeout=60000)fun closingSessionCleansUnfinishedAcquisition(){val c=c();val s=launch(c).getString("session");val p=pending(c,s);val token=p.getString("token");val made=call(c,JSONObject().put("action","blob_host").put("session",s).put("token",token).put("operation","begin").put("args",JSONObject().put("name","Book.epub").put("mime","application/epub+zip")));val id=made.getString("id");AppSdk.sync(c,JSONObject().put("action","close").put("session",s));val index=JSONObject(File(c.filesDir,"app-sdk/data/app.onklav.ebooks/private-blobs-v1.json").readText());assertFalse(index.getJSONObject("entries").has(id))}

 @Test(timeout=60000)fun downloadedGutenbergEpubPassesSignedRustReader()=runBlocking<Unit>{
  val file=System.getenv("ONKLAV_EBOOK_COMPAT_PATH")?.let(::File);org.junit.Assume.assumeTrue("Actual-source compatibility is opt-in",file?.isFile==true)
  val c=c();val s=launch(c).getString("session");val p=pending(c,s);val token=p.getString("token")
  fun auth()=call(c,JSONObject().put("action","authorize").put("session",s).put("token",token))
  fun transfer(op:String,a:JSONObject)=call(c,JSONObject().put("action","blob_host").put("session",s).put("token",token).put("operation",op).put("args",a))
  val actual=file!!.readBytes();val saved=SdkEbooks.acquire(c,token,::auth,::transfer,connect={r->Stub(r){response(r,if(r.url.toString().endsWith(".opds"))meta()else actual)}})
  call(c,JSONObject().put("action","reply").put("session",s).put("token",token).put("reply",JSONObject().put("ok",true).put("value",saved)));val f=event(c,s,"book.read");val state=f.getJSONObject("frame").getJSONObject("state");assertTrue(state.getJSONObject("active").getString("title").contains("Alice",true));val reader=state.getJSONObject("reader");assertTrue(reader.getJSONObject("book").getJSONArray("chapters").length()>1)
  val report=JSONObject().put("status","passed").put("scope","Actual Gutenberg EPUB bytes through signed acquire/blob/Rust EPUB/reader path; HTTP transport supplied from actual downloaded file").put("title",state.getJSONObject("active").getString("title")).put("chapters",reader.getJSONObject("book").getJSONArray("chapters").length()).put("bytes",saved.getLong("size")).put("sha256",saved.getString("sha256")).put("default_android_network_qualified",false)
  File(System.getProperty("onklav.project.root"),"docs/evidence/v0.74.0/gutenberg-compatibility.json").writeText(report.toString(2));AppSdk.sync(c,JSONObject().put("action","close").put("session",s))
 }

 @Test(timeout=90000)fun largeIllustratedDownloadCommitsAndOpensWithoutTheOld20MbLimit()=runBlocking<Unit>{
  val c=c();val session=launch(c).getString("session");val token=pending(c,session).getString("token")
  fun auth()=call(c,JSONObject().put("action","authorize").put("session",session).put("token",token))
  fun transfer(op:String,a:JSONObject)=call(c,JSONObject().put("action","blob_host").put("session",session).put("token",token).put("operation",op).put("args",a))
  val output=java.io.ByteArrayOutputStream();java.util.zip.ZipOutputStream(output).use{zip->
   java.util.zip.ZipInputStream(File(System.getProperty("onklav.project.root"),"docs/fixtures/text-profile.epub").inputStream()).use{source->while(true){val part=source.nextEntry?:break;val data=source.readBytes();val entry=java.util.zip.ZipEntry(part.name);if(part.name=="mimetype"){entry.method=java.util.zip.ZipEntry.STORED;entry.size=data.size.toLong();entry.crc=java.util.zip.CRC32().apply{update(data)}.value};zip.putNextEntry(entry);zip.write(data);zip.closeEntry()}}
   zip.setLevel(0);zip.putNextEntry(java.util.zip.ZipEntry("Images/large.jpg"));val block=ByteArray(8192){37};repeat(21*128){zip.write(block)};zip.closeEntry()
  };val bytes=output.toByteArray();assertTrue(bytes.size>20*1024*1024)
  val saved=SdkEbooks.acquire(c,token,::auth,::transfer,connect={r->Stub(r){response(r,if(r.url.toString().endsWith(".opds"))meta()else bytes)}});assertEquals(bytes.size.toLong(),saved.getLong("size"))
  call(c,JSONObject().put("action","reply").put("session",session).put("token",token).put("reply",JSONObject().put("ok",true).put("value",saved)))
  val frame=event(c,session,"book.read").getJSONObject("frame");assertEquals("read",frame.getJSONObject("state").getString("view"));assertTrue(frame.toString().contains("Alpha"))
 }

}
