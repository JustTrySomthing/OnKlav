package app.onklav.home

import android.app.Application
import android.content.Intent
import android.content.pm.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkHandoffTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun owner(id:String="org.example.one")=JSONObject().put("id",id).put("publisher","a".repeat(64)).put("hash","b".repeat(64)).put("session","session-one")
 private fun source(o:JSONObject=owner()):JSONObject{val id="c".repeat(32);val f=File(c.filesDir,"app-sdk/data/${o.getString("id")}/private-blobs-v1/$id.data");f.parentFile!!.mkdirs();f.writeText("PDF content");return JSONObject().put("id",id).put("name","Book.pdf").put("mime","application/pdf").put("size",f.length()).put("path",f.absolutePath)}
 @Test fun typedIntentsGrantOneReadOnlyFileAndCannotInjectAPackageOrWriteGrant(){
  val uri=Uri.parse("content://${c.packageName}.sdk-handoff/"+"a".repeat(32));val i=SdkHandoffs.intent("share","application/pdf",uri)
  assertEquals(Intent.ACTION_SEND,i.action);assertEquals("application/pdf",i.type);assertEquals(uri,i.clipData!!.getItemAt(0).uri);assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,i.flags);assertNull(i.`package`);assertNull(i.component)
  assertThrows(Exception::class.java){SdkHandoffs.intent("view","*/*",uri)};assertThrows(Exception::class.java){SdkHandoffs.intent("view","text/plain",Uri.parse("file:///private"))}
 }
 @Test fun handlerDiscoveryIsScopedAndReturnsAvailabilityWithoutAnInventory(){
  assertFalse(SdkHandoffs.available(c,"view","application/pdf"))
  val uri=Uri.parse("content://${c.packageName}.sdk-handoff/probe");val intent=SdkHandoffs.intent("view","application/pdf",uri)
  val info=ResolveInfo().apply{activityInfo=ActivityInfo().apply{packageName="org.example.viewer";name="Viewer";exported=true;enabled=true;applicationInfo=ApplicationInfo().apply{enabled=true}}}
  shadowOf(c.packageManager).addResolveInfoForIntent(intent,info)
  val result=SdkHandoffs.discover(c,JSONObject().put("operation","view").put("mime","application/pdf")){}
  assertEquals(setOf("available"),result.keys().asSequence().toSet());assertTrue(result.getBoolean("available"))
 }
 @Test fun copiedFilesAreIsolatedReadOnlyAndExpireWithoutDeletingThePrivateSource(){
  val input=source();val uri=SdkHandoffProvider.stage(c,owner(),input,{},1000)
  val(row,copy)=SdkHandoffProvider().inspect(c,uri,1001){_,_->true};assertEquals("Book.pdf",row.getString("name"));assertEquals("PDF content",copy.readText());assertNotEquals(input.getString("path"),copy.absolutePath)
  val provider=SdkHandoffProvider();assertThrows(Exception::class.java){provider.openFile(uri,"rw")}
  assertThrows(Exception::class.java){provider.inspect(c,uri.buildUpon().appendQueryParameter("name","evil").build(),1001){_,_->true}}
  assertThrows(Exception::class.java){provider.inspect(c,uri,601000){_,_->true}};assertFalse(copy.exists());assertTrue(File(input.getString("path")).exists())
 }
 @Test fun removalAndLateRevocationDeleteOnlyTheOwningAppsCopies(){
  val first=SdkHandoffProvider.stage(c,owner(),source(),{},1000);val other=owner("org.example.two");val second=SdkHandoffProvider.stage(c,other,source(other),{},1000)
  SdkHandoffProvider.clearApp(c,"org.example.one");val p=SdkHandoffProvider();assertThrows(Exception::class.java){p.inspect(c,first,1001){_,_->true}};assertEquals("PDF content",p.inspect(c,second,1001){_,_->true}.second.readText())
  val path=p.inspect(c,second,1001){_,_->true}.second;assertThrows(Exception::class.java){p.inspect(c,second,1001){_,_->false}};assertFalse(path.exists())
 }
 @Test fun ownerPublisherVersionAndAllGrantsAreRechecked(){
  val o=owner();val row=JSONObject().put("installed",true).put("manifest",JSONObject().put("id",o.getString("id")).put("publisher",o.getString("publisher"))).put("hash",o.getString("hash")).put("grants",JSONArray(listOf("apps.handoff","storage.private","files.export")))
  assertTrue(SdkHandoffProvider.eligibleRows(JSONArray().put(row),o));for(key in listOf("id","publisher","hash")){val changed=JSONObject(o.toString()).put(key,"different");assertFalse(SdkHandoffProvider.eligibleRows(JSONArray().put(row),changed))};row.put("grants",JSONArray(listOf("apps.handoff","storage.private")));assertFalse(SdkHandoffProvider.eligibleRows(JSONArray().put(row),o));row.put("installed",false);assertFalse(SdkHandoffProvider.eligibleRows(JSONArray().put(row),o))
 }
 @Test fun foreignPathsAndFailedAuthorizationNeverCreateCopies(){
  val o=owner();val input=source(o);val foreign=File(c.filesDir,"foreign.pdf");foreign.writeText("foreign");input.put("path",foreign.absolutePath).put("size",foreign.length())
  assertThrows(Exception::class.java){SdkHandoffProvider.stage(c,o,input,{},1000)};assertThrows(Exception::class.java){SdkHandoffProvider.stage(c,o,source(o),{error("Revoked")},1000)};assertTrue(c.getSharedPreferences("sdk-handoffs",0).all.isEmpty())
 }
 @Test fun interruptedAndRevokedStagingLeavesNoLiveCopyOrGrant(){
  val dir=File(c.cacheDir,"sdk-handoffs");dir.mkdirs();val orphan=File(dir,"d".repeat(32));orphan.writeText("Interrupted copy")
  val o=owner();val input=source(o);var calls=0
  assertThrows(Exception::class.java){SdkHandoffProvider.stage(c,o,input,{if(++calls==3)error("Revoked during copy")},1000)}
  assertFalse(orphan.exists());assertTrue(dir.listFiles().orEmpty().isEmpty());assertTrue(c.getSharedPreferences("sdk-handoffs",0).all.isEmpty());assertTrue(File(input.getString("path")).exists())
 }
 @Test fun failedRemovalCleanupCannotResurrectAnOldUriWhenAccessIsRegranted(){
  val o=owner();val uri=SdkHandoffProvider.stage(c,o,source(o),{},1000);val provider=SdkHandoffProvider();val copy=provider.inspect(c,uri,1001){_,_->true}.second
  assertTrue(copy.delete());assertTrue(copy.mkdir());val obstruction=File(copy,"busy");obstruction.writeText("busy")
  assertThrows(Exception::class.java){SdkHandoffProvider.clearApp(c,o.getString("id"))}
  assertTrue(obstruction.delete());assertTrue(copy.delete());copy.writeText("PDF content")
  assertThrows(Exception::class.java){provider.inspect(c,uri,1001){_,_->true}};assertFalse(copy.exists())
 }
}
