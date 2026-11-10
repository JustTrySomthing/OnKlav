package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.NativeCore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class WriteMigrationHostTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun legacy():JSONObject{
  val id=UUID.randomUUID().toString().replace("-","")
  val response=JSONObject(NativeCore.dispatch(c.filesDir.path,JSONObject().put("op","save").put("note",JSONObject().put("id",id).put("title","My old note").put("text","Every word 🐈\nAnother line").put("updated",1)).toString()))
  assertTrue(response.toString(),response.getBoolean("ok"));return response.getJSONObject("value")
 }
 @Test fun startupConvertsLegacyNotesRetiresThePackageAndKeepsLinkedSaves(){
  val original=legacy();val id=original.getString("id");val raw=File(c.filesDir,"notes/$id.json").readBytes()
  runBlocking{AppSdk.bootstrap(c)}
  val rows=AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray
  assertFalse((0 until rows.length()).any{rows.getJSONObject(it).let{r->r.child("manifest").optString("id")=="app.onklav.notes"&&r.optBoolean("installed")}})
  assertFalse(AppSdk.bundledApps.containsKey("notes"));assertFalse(platformApps.any{it.id=="notes"})
  val uuid=AppSdk.writeMigration(c,"linked",id).getString("id");val document=WriteFiles.read(c,uuid)
  assertEquals(original.getString("text"),document.text);assertEquals("My old note",document.title)
  WriteFiles.save(c,document.copy(text="Edited in Write"))
  val shared=JSONObject(File(c.filesDir,"onklav-content/objects/$id").readText());assertEquals("Edited in Write",shared.getString("text"))
  assertArrayEquals(raw,File(c.filesDir,"notes/$id.json").readBytes())
  assertTrue(AppSdk.writeMigration(c,"status").getBoolean("complete"));runBlocking{AppSdk.bootstrap(c)}
  assertEquals(1,WriteFiles.list(c).count{it.id==uuid})
 }
 @Test fun conflictingWriteDocumentDoesNotRemoveNotesOrOverwriteEitherOriginal(){
  val original=legacy();val id=UUID.fromString(original.getString("id").let{"${it.take(8)}-${it.substring(8,12)}-${it.substring(12,16)}-${it.substring(16,20)}-${it.substring(20)}"}).toString()
  val file=File(c.filesDir,"write/documents/$id.json");file.parentFile!!.mkdirs();file.writeText(WriteDocument(id=id,title="Existing",text="Do not overwrite").json().toString())
  var failed=false;try{runBlocking{AppSdk.bootstrap(c)}}catch(e:IllegalStateException){failed=true}
  assertTrue(failed);assertEquals("Do not overwrite",JSONObject(file.readText()).getString("text"));assertFalse(AppSdk.writeMigration(c,"status").getBoolean("complete"))
  val rows=AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray
  assertEquals(original.getString("text"),JSONObject(File(c.filesDir,"notes/${original.getString("id")}.json").readText()).getString("text"))
 }
}
