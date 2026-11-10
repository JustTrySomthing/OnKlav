package app.onklav.home

import android.content.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryBackupTest {
 @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
 private val c:Context get()=ApplicationProvider.getApplicationContext()
 private val uri=Uri.parse("content://library-backup-fixture/document/archive")
 private fun provider()=NotesBackupTest.Provider(c).also{it.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="library-backup-fixture";exported=true;applicationInfo=c.applicationInfo});ShadowContentResolver.registerProviderInternal("library-backup-fixture",it)}
 private fun fresh():Context{val root=java.nio.file.Files.createTempDirectory(c.cacheDir.toPath(),"library-target-").toFile();return object:ContextWrapper(c){override fun getFilesDir()=root}}
 private fun clean(context:Context){listOf("library-backup-import.zip","library-backup-export.zip","library-backup-export.tmp","library-backup.cancel").forEach{assertFalse(File(context.filesDir,it).exists())}}
 @Test fun filesProviderToJniRoundTripKeepsBytesAndRejectsChangedReview()=runBlocking{
  val p=provider();File(c.filesDir,"content-import.tmp").writeText("Portable file 🐈")
  AppSdk.sync(c,JSONObject().put("action","message_import").put("mime","image/png").put("name","photo.png"))
  assertEquals(2,LibraryBackup.export(c,"files",uri,LibraryBackup.Stop(c)).getInt("items"));clean(c)
  ZipFile(p.target).use{z->assertTrue(z.getInputStream(z.entries().toList().first{it.name.startsWith("files/")}).reader().readText().contains("Portable file"))}
  val target=fresh();val review=LibraryBackup.inspect(target,"files",uri,LibraryBackup.Stop(target));assertEquals(2,review.summary.getInt("new"));clean(target)
  try{assertEquals(2,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new"));assertEquals(2,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("kept"));clean(target)
   review.file.appendText("changed after review");assertTrue(runCatching{LibraryBackup.restore(target,review,LibraryBackup.Stop(target))}.isFailure);clean(target)
  }finally{review.close()}
 }
 @Test fun annotatedImageUsesRealSdkImportAndLiveSessionsBlockRestoration()=runBlocking{
  val p=provider();AppSdk.bootstrap(c);installLegacyAnnotation(c)
  val app=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.annotation")) as JSONObject;val token=app.getString("session")
  try{val pending=(AppSdk.sync(c,JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","image.open"))) as JSONObject).child("pending")
   val image=File(requireNotNull(System.getProperty("onklav.project.root")),"docs/fixtures/annotation-base.jpg").readBytes()
   AppSdk.sync(c,JSONObject().put("action","reply").put("session",token).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",true).put("value",JSONObject().put("image",android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP)))))
   LibraryBackup.export(c,"annotation",uri,LibraryBackup.Stop(c));val review=LibraryBackup.inspect(c,"annotation",uri,LibraryBackup.Stop(c))
   try{assertTrue(runCatching{LibraryBackup.restore(c,review,LibraryBackup.Stop(c))}.exceptionOrNull()!!.message!!.contains("Close open apps"))}finally{review.close()}
  }finally{AppSdk.sync(c,JSONObject().put("action","close").put("session",token))}
  AppSdk.epoch.value++;AppSdk.bootstrap(c);LibraryBackup.export(c,"annotation",uri,LibraryBackup.Stop(c))
  val target=fresh();val review=LibraryBackup.inspect(target,"annotation",uri,LibraryBackup.Stop(target))
  try{assertEquals(1,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new"));assertEquals(1,File(target.filesDir,"app-sdk/data/app.onklav.annotation").listFiles()!!.count{it.extension=="jpg"});clean(target)}finally{review.close()}
 }
 @Test fun cancellationAndProviderFailureCleanStagingAndIncompleteOutput()=runBlocking{
  val p=provider();val stop=LibraryBackup.Stop(c);stop.cancel()
  assertTrue(runCatching{LibraryBackup.export(c,"files",uri,stop)}.isFailure);clean(c)
  p.failWrite=true;assertTrue(runCatching{LibraryBackup.export(c,"files",uri,LibraryBackup.Stop(c))}.isFailure);assertTrue(p.target.exists());assertEquals(0,p.deletions);clean(c)
  p.failWrite=false;p.target.writeText("not a ZIP");assertTrue(runCatching{LibraryBackup.inspect(c,"files",uri,LibraryBackup.Stop(c))}.isFailure);clean(c)
 }
 private fun launch(context:Context,id:String)=AppSdk.sync(context,JSONObject().put("action","launch").put("id",id)) as JSONObject
 private fun event(context:Context,token:String,action:String,value:String="")=AppSdk.sync(context,JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action",action).put("value",value))) as JSONObject
 private fun close(context:Context,token:String){AppSdk.sync(context,JSONObject().put("action","close").put("session",token))}
 @Test fun rssProviderBackupReopensOfflineArticlesAndBookmarksInTheSignedApp()=runBlocking{
  provider();AppSdk.bootstrap(c)
  val own=AppSdk.list(c).first{it.child("manifest").getString("id")=="app.onklav.rss"}
  AppSdk.sync(c,JSONObject().put("action","adopt_own_feeds").put("publisher",own.child("manifest").getString("publisher")).put("feeds","[\"https://example.org/rss\"]"))
  val source=launch(c,"app.onklav.rss");val token=source.getString("session")
  try{val pending=source.child("pending")
   val reply=JSONObject("""{"ok":true,"value":{"items":[{"url":"https://example.org/story","title":"Story","body":"Offline restored article 🐈","published":12}]}}""")
   val loaded=AppSdk.sync(c,JSONObject().put("action","reply").put("session",token).put("token",pending.getString("token")).put("reply",reply)) as JSONObject
   val article=loaded.child("frame").child("state").child("listing").getJSONArray("items").getJSONObject(0).getString("id")
   event(c,token,"bookmark:$article")
  }finally{close(c,token)}
  LibraryBackup.export(c,"rss",uri,LibraryBackup.Stop(c));val target=fresh();val review=LibraryBackup.inspect(target,"rss",uri,LibraryBackup.Stop(target))
  try{LibraryBackup.restore(target,review,LibraryBackup.Stop(target));assertTrue(LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("kept")>0);clean(target)
   AppSdk.bootstrap(target);var reopened=launch(target,"app.onklav.rss");val t=reopened.getString("session")
   try{reopened.optJSONObject("pending")?.let{pending->reopened=AppSdk.sync(target,JSONObject().put("action","reply").put("session",t).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",false).put("error","Offline"))) as JSONObject};val state=reopened.child("frame").child("state");assertEquals(1,state.child("meta").getInt("bookmarks"));assertEquals("Story",state.child("listing").getJSONArray("items").getJSONObject(0).getString("title"));assertTrue(File(target.filesDir,"app-sdk/data/app.onklav.rss/feeds.json").readText().contains("Offline restored article"))}finally{close(target,t)}
  }finally{review.close()}
 }
 @Test fun readerBackupRestoresAnAccessibleDraftAndKeepsAnExistingClearedDraft()=runBlocking{
  provider();AppSdk.bootstrap(c);val source=launch(c,"app.onklav.reader");val t=source.getString("session")
  try{event(c,t,"text","Portable reader draft 🐈")}finally{close(c,t)}
  LibraryBackup.export(c,"reader",uri,LibraryBackup.Stop(c));val target=fresh();val review=LibraryBackup.inspect(target,"reader",uri,LibraryBackup.Stop(target))
  try{assertEquals(1,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new"));AppSdk.bootstrap(target)
   val reopened=launch(target,"app.onklav.reader");val token=reopened.getString("session")
   try{assertEquals("Portable reader draft 🐈",reopened.child("frame").child("state").child("note").getString("text"));event(target,token,"clear");event(target,token,"clear.accept")}finally{close(target,token)}
   assertEquals(1,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("kept"));val again=launch(target,"app.onklav.reader");try{assertEquals("",again.child("frame").child("state").child("note").getString("text"))}finally{close(target,again.getString("session"))};clean(target)
  }finally{review.close()}
 }

 @Test fun browserBackupReopensTabsAndBookmarksAndPreservesLocalChanges()=runBlocking{
  provider();AppSdk.bootstrap(c);val source=launch(c,"app.onklav.browser");val t=source.getString("session")
  try{event(c,t,"address","https://example.org/portable");event(c,t,"go");event(c,t,"bookmarks");event(c,t,"bookmark.add")}finally{close(c,t)}
  LibraryBackup.export(c,"browser",uri,LibraryBackup.Stop(c));val target=fresh();val review=LibraryBackup.inspect(target,"browser",uri,LibraryBackup.Stop(target))
  try{assertEquals(2,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new"));AppSdk.bootstrap(target);val opened=launch(target,"app.onklav.browser");val token=opened.getString("session")
   try{assertEquals("https://example.org/portable",opened.child("frame").child("state").getString("address"));val marks=event(target,token,"bookmarks");assertEquals(1,marks.child("frame").child("state").child("bookmarks").getInt("total"));event(target,token,"drawer.close");event(target,token,"address","https://example.org/keep");event(target,token,"go")}finally{close(target,token)}
   assertEquals(2,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("kept"));val again=launch(target,"app.onklav.browser");try{assertEquals("https://example.org/keep",again.child("frame").child("state").getString("address"))}finally{close(target,again.getString("session"))};clean(target)
  }finally{review.close()}
 }
 @Test fun youtubeBackupReopensBookmarksAndHistoryWithALocalViewerIdentity()=runBlocking{
  provider();AppSdk.bootstrap(c);val source=launch(c,"app.onklav.youtube");val t=source.getString("session")
  try{val node=(AppSdk.sync(c,JSONObject().put("action","host_web").put("session",t).put("widget","youtube.page")) as JSONObject).child("node")
   AppSdk.sync(c,JSONObject().put("action","web_event").put("session",t).put("widget","youtube.page").put("tab",node.getString("tab")).put("epoch",node.optLong("epoch")).put("value",JSONObject().put("url","https://www.youtube.com/watch?v=AbC_def-123").put("title","Portable video").put("kind","navigation")))
   event(c,t,"bookmarks");event(c,t,"bookmark.add")
  }finally{close(c,t)}
  LibraryBackup.export(c,"youtube",uri,LibraryBackup.Stop(c));val target=fresh();val review=LibraryBackup.inspect(target,"youtube",uri,LibraryBackup.Stop(target))
  try{assertEquals(2,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new"));AppSdk.bootstrap(target);val opened=launch(target,"app.onklav.youtube");val token=opened.getString("session")
   try{val marks=event(target,token,"bookmarks");assertEquals(1,marks.child("frame").child("state").child("listing").getInt("total"));event(target,token,"drawer.close");val history=event(target,token,"history");assertEquals("Portable video",history.child("frame").child("state").child("listing").getJSONArray("items").getJSONObject(0).getString("title"))}finally{close(target,token)}
   assertEquals(2,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("kept"));clean(target)
  }finally{review.close()}
 }

 @Test fun oneAppDocumentsBackupRestoresAcrossStoresAndRepeatsSafely()=runBlocking{
  provider();AppSdk.bootstrap(c);val source=launch(c,"app.onklav.reader");val t=source.getString("session");try{event(c,t,"text","One combined backup")}finally{close(c,t)}
  File(c.filesDir,"content-import.tmp").writeText("Combined file");AppSdk.sync(c,JSONObject().put("action","message_import").put("mime","image/png").put("name","combined.png"))
  LibraryBackup.export(c,"documents",uri,LibraryBackup.Stop(c));val target=fresh();val review=LibraryBackup.inspect(target,"documents",uri,LibraryBackup.Stop(target))
  try{assertTrue(LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new")>0);assertEquals(0,LibraryBackup.restore(target,review,LibraryBackup.Stop(target)).getInt("new"));AppSdk.bootstrap(target);val reopened=launch(target,"app.onklav.reader");try{assertEquals("One combined backup",reopened.child("frame").child("state").child("note").getString("text"))}finally{close(target,reopened.getString("session"))};clean(target)}finally{review.close()}
 }

}
