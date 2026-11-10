package app.onklav.home
import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CloudExportRepairTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun response(r:Request,body:String)=Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(if(r.method=="PROPFIND")207 else 200).message("Fixture").body(body.toResponseBody()).build()
 private fun idle(m:CloudModel){val end=System.nanoTime()+15_000_000_000L;do{shadowOf(android.os.Looper.getMainLooper()).idle();if(!m.busy)return;Thread.sleep(10)}while(System.nanoTime()<end);fail("Cloud operation timed out")}
 private fun model(onGet:()->Unit={}):CloudModel{
  File(c.noBackupFilesDir,"cloud-files").deleteRecursively();val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey();val keys=object:SecretKeys{override fun load(alias:String,create:Boolean):SecretKey=key;override fun deletePrefix(prefix:String){}}
  val store=CloudStore(c,keys);store.save(listOf(cloudAccount()))
  return CloudModel(c,store){a->CloudDav(a){r->if(r.method=="PROPFIND")response(r,cloudXml())else{onGet();response(r,"complete cloud file")}}}.also{idle(it);assertNotNull(it.account)}
 }
 @Test fun choosingDownloadDoesNotStartTheTransferBeforeDestinationSelection(){var gets=0;val m=model{gets++};var ready=false;m.download(CloudEntry("https://cloud.example.test/dav/file.zip".toHttpUrl(),"file.zip",false,size=300L*1024*1024)){ready=true};assertTrue(ready);assertEquals(0,gets);assertNull(m.staged);assertNotNull(m.stagedEntry)}
 @Test fun failedSaveRetainsFullDownloadAndRetrySavesItWithoutDownloadingAgain(){
  var gets=0;val m=model{gets++};val p=AuditRepairTest.Provider(c).also{it.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="cloud-repair-export";applicationInfo=c.applicationInfo});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("cloud-repair-export",it)}
  val e=CloudEntry("https://cloud.example.test/dav/file.txt".toHttpUrl(),"file.txt",false);m.download(e){};p.reject=true;m.export(Uri.parse("content://cloud-repair-export/file"));idle(m)
  assertEquals(1,gets);assertTrue(m.error.isNotBlank());assertEquals("complete cloud file",m.staged!!.readText());assertNotNull(m.stagedEntry)
  p.reject=false;m.export(Uri.parse("content://cloud-repair-export/file"));idle(m);assertEquals(1,gets);assertEquals("complete cloud file",p.file.readText());assertTrue(m.status.startsWith("Saved"));assertNull(m.staged)
 }
 @Test fun streamLimitsRejectKnownAndUnknownLengthBodiesWithoutLeavingPartialFiles()=runBlocking{
  val account=cloudAccount();val entry=CloudEntry("https://cloud.example.test/dav/file".toHttpUrl(),"file",false);val target=File(c.cacheDir,"bounded-cloud.part")
  val known=CloudDav(account){r->response(r,"1234")};assertTrue(runCatching{known.download(entry,target,3){_,_->}}.isFailure);assertFalse(target.exists())
  val unknown=CloudDav(account){r->response(r,"1234").newBuilder().body(object:ResponseBody(){override fun contentType():MediaType?=null;override fun contentLength()=-1L;override fun source()=okio.Buffer().writeUtf8("1234")}).build()}
  assertTrue(runCatching{unknown.download(entry,target,3){_,_->}}.isFailure);assertFalse(target.exists());Unit
 }
}
