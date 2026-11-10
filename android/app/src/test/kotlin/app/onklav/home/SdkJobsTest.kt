package app.onklav.home

import android.app.Application
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkJobsTest {
 @get:Rule val emptyKeys=EmptyKeyStoreRule()
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private suspend fun operation(action:String="download"):JSONObject{
  File(System.getProperty("onklav.project.root"),"docs/fixtures/tasks-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)};val review=AppSdk.inspect(c);AppSdk.install(c,review,setOf("background.tasks","network.http","storage.private","audio.playback"))
  val opened=AppSdk.call(c,JSONObject().put("action","launch").put("id","org.example.tasksfixture"))as JSONObject
  val event=AppSdk.call(c,JSONObject().put("action","event").put("session",opened.getString("session")).put("event",JSONObject().put("action",action)))as JSONObject
  return AppSdk.call(c,JSONObject().put("action","authorize").put("session",opened.getString("session")).put("token",event.getJSONObject("pending").getString("token")))as JSONObject
 }
 private fun enqueue(r:JSONObject)=SdkJobs.execute(c,r){}as JSONObject
 private fun pending()=c.getSystemService(JobScheduler::class.java).allPendingJobs.single{it.id in SdkJobs.BASE until SdkJobs.BASE+32}
 private fun status(owner:JSONObject,id:String)=SdkJobs.execute(c,JSONObject().put("service","jobs.status").put("owner",owner).put("args",JSONObject().put("id",id))){}as JSONObject
 private fun bytes(owner:JSONObject,file:String):ByteArray{val resolved=AppSdk.nativeTask(c,JSONObject().put("action","media_blob").put("owner",owner).put("service","resolve").put("args",JSONObject().put("id",file)));return File(resolved.getString("path")).readBytes()}
 @Test fun rangeParserRejectsMismatchedOffsetLengthAndOverlargeTotals(){assertEquals(10L,SdkJobs.contentRange("bytes 4-9/10",4,6,1024));for(v in listOf("bytes 3-9/10","bytes 4-9/99999","bytes */10","bytes 4-10/10"))assertThrows(Exception::class.java){SdkJobs.contentRange(v,4,6,1024)}}
 @Test fun signedDownloadResumesOnlyMatchingStrongEtagAndPublishesOneVerifiedFile()=runBlocking<Unit>{
  val op=operation();op.getJSONObject("args").put("sha256",op.getJSONObject("args").getString("sha256").uppercase());val job=enqueue(op);val p=pending();val id=job.getString("id");val token=p.extras.getString("token")!!
  assertTrue(p.isPersisted);assertTrue(p.isRequireBatteryNotLow)
  val interrupted=SdkJobs.run(c,id,token,{false},connect={request->SdkServiceCall(request){val body=object:ResponseBody(){override fun contentType():MediaType?=null;override fun contentLength()=10L;override fun source():BufferedSource=object:Source{var first=true;override fun read(sink:Buffer,byteCount:Long):Long{if(first){first=false;sink.writeUtf8("0123");return 4};throw IOException("Interrupted fixture")};override fun timeout()=Timeout();override fun close(){}}.buffer()};sdkResponse(request,byteArrayOf()).newBuilder().header("ETag","\"v1\"").body(body).build()}})
  assertTrue(interrupted);assertEquals(4L,status(op.getJSONObject("owner"),id).getLong("bytes"))
  c.getSystemService(JobScheduler::class.java).cancelAll();SdkJobs.recover(c);assertEquals(token,pending().extras.getString("token"))
  assertFalse(SdkJobs.run(c,id,token,{false},connect={request->assertEquals("bytes=4-",request.header("Range"));assertEquals("\"v1\"",request.header("If-Range"));SdkServiceCall(request){sdkResponse(request,"456789".toByteArray(),206).newBuilder().header("ETag","\"v1\"").header("Content-Range","bytes 4-9/10").build()}}))
  val final=status(op.getJSONObject("owner"),id);assertEquals("completed",final.getString("state"));assertEquals(2,final.getInt("attempts"));val file=final.getJSONObject("file");assertTrue(file.getBoolean("ready"));assertArrayEquals("0123456789".toByteArray(),bytes(op.getJSONObject("owner"),file.getString("id")));assertFalse(final.toString().contains("path"))
 }
 @Test fun ownerIsolationCancellationAndRevocationDoNotRunAStaleTask()=runBlocking<Unit>{
  val op=operation();val job=enqueue(op);val p=pending();val id=job.getString("id");val other=JSONObject(op.getJSONObject("owner").toString()).put("publisher","b".repeat(64))
  assertThrows(IllegalStateException::class.java){status(other,id)}
  val cancel=JSONObject().put("service","jobs.cancel").put("owner",op.getJSONObject("owner")).put("args",JSONObject().put("id",id));assertTrue((SdkJobs.execute(c,cancel){}as JSONObject).getBoolean("cancelled"));assertThrows(IllegalStateException::class.java){status(op.getJSONObject("owner"),id)}
  val replacement=enqueue(op);val replacementJob=pending();assertNotEquals(id,replacement.getString("id"))
  AppSdk.call(c,JSONObject().put("action","permissions").put("id","org.example.tasksfixture").put("grants",org.json.JSONArray().put("storage.private")))
  assertFalse(SdkJobs.run(c,replacement.getString("id"),replacementJob.extras.getString("token")!!,{false},connect={error("Revoked task reached network")}));assertTrue(c.getSystemService(JobScheduler::class.java).allPendingJobs.none{it.id in SdkJobs.BASE until SdkJobs.BASE+32})
 }
 @Test fun httpGetJobStoresBoundedResponseForLaterGuestRead()=runBlocking<Unit>{
  val op=operation("job");val job=enqueue(op);val p=pending()
  assertFalse(SdkJobs.run(c,job.getString("id"),p.extras.getString("token")!!,{false},fetch={policy,verify->verify();assertEquals("GET",policy.getJSONObject("args").getString("method"));JSONObject().put("status",200).put("text","synced").put("headers",JSONObject())}))
  val final=status(op.getJSONObject("owner"),job.getString("id"));assertEquals("completed",final.getString("state"));val output=JSONObject(String(bytes(op.getJSONObject("owner"),final.getJSONObject("file").getString("id"))));assertEquals("synced",output.getString("text"))
 }
 @Test fun constraintsAndRetryLimitRemainBounded()=runBlocking<Unit>{
  val op=operation();op.getJSONObject("args").put("unmetered",true).put("charging",true);val job=enqueue(op);val p=pending();assertTrue(p.isRequireCharging);assertEquals(android.app.job.JobInfo.NETWORK_TYPE_UNMETERED,p.networkType)
  val id=job.getString("id");val token=p.extras.getString("token")!!
  repeat(3){attempt->val retry=SdkJobs.run(c,id,token,{false},connect={request->SdkServiceCall(request){throw IOException("Network offline")}});assertEquals(attempt<2,retry)}
  assertEquals("failed",status(op.getJSONObject("owner"),id).getString("state"));assertFalse(SdkJobs.run(c,id,token,{false},connect={error("Fourth attempt must not run")}))
 }
 @Test fun completedFileWithoutHostReceiptRecoversWithoutAnotherRequest()=runBlocking<Unit>{
  val op=operation();val job=enqueue(op);val p=pending();val id=job.getString("id");val token=p.extras.getString("token")!!;val owner=op.getJSONObject("owner")
  fun native(service:String,args:JSONObject)=AppSdk.nativeTask(c,JSONObject().put("action","background_blob").put("owner",owner).put("job_token",token).put("service",service).put("args",args))
  val stage=native("blobs.begin",JSONObject().put("name","Sample.mp3").put("mime","audio/mpeg"));val file=stage.getString("id")
  native("blobs.append",JSONObject().put("id",file).put("offset",0).put("hex","30313233343536373839"));native("blobs.finish",JSONObject().put("id",file).put("sha256",op.getJSONObject("args").getString("sha256")))
  val saved=File(c.noBackupFilesDir,"sdk-jobs.json");val rows=JSONObject(saved.readText());rows.getJSONObject(id).put("attempts",3);saved.writeText(rows.toString())
  assertFalse(SdkJobs.run(c,id,token,{false},connect={error("Receipt recovery must not make another request")}));val final=status(owner,id);assertEquals("completed",final.getString("state"));assertEquals(file,final.getJSONObject("file").getString("id"));assertEquals(3,final.getInt("attempts"));assertEquals(10L,final.getLong("bytes"))
 }

}
