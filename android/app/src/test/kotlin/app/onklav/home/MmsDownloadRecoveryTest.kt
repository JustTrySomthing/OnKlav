package app.onklav.home

import android.app.*
import android.app.job.JobScheduler
import android.app.role.RoleManager
import android.content.Context
import android.telephony.SmsManager
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MmsDownloadRecoveryTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private val id="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
 private val other="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
 private fun stage(key:String=id){c.getSharedPreferences("mms_transfers",0).edit().putString("$key.location","http://carrier.example/picture").putString("$key.sender","+15550000000").putInt("$key.sub",1).commit();shadowOf(c.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS);c.getSharedPreferences("messaging_setup",0).edit().putBoolean("carrier_trial",true).commit()}
 @Test fun temporaryDownloadFailureStaysVisibleAndSchedulesBoundedIncomingRetries(){
  stage();val p=c.getSharedPreferences("mms_transfers",0)
  repeat(4){MessagingTransport.downloaded(c,id,false,SmsManager.MMS_ERROR_NO_DATA_NETWORK)}
  assertEquals(3,p.getInt("$id.retries",0));assertEquals(1,MessagingTransport.pendingDownloads(c).size)
  assertTrue(MessagingTransport.pendingDownloads(c).single().getString("error").contains("mobile data"))
  assertEquals("http://carrier.example/picture",p.getString("$id.location",null));assertNotNull(c.getSystemService(JobScheduler::class.java).allPendingJobs.singleOrNull())
  val n=shadowOf(c.getSystemService(NotificationManager::class.java)).getNotification("picture:$id",0);assertNotNull(n);assertEquals(0,n.flags and Notification.FLAG_AUTO_CANCEL)
 }
 @Test fun independentPictureNotificationsCannotOverwriteEachOtherAndCompletionClearsOnlyItsOwn(){
  stage();stage(other);MessagingTransport.downloaded(c,id,false,SmsManager.MMS_ERROR_INVALID_APN);MessagingTransport.downloaded(c,other,false,SmsManager.MMS_ERROR_DATA_DISABLED)
  val n=shadowOf(c.getSystemService(NotificationManager::class.java));assertNotNull(n.getNotification("picture:$id",0));assertNotNull(n.getNotification("picture:$other",0));assertEquals(2,MessagingTransport.pendingDownloads(c).size)
  MessageNotifications.pictureComplete(c,id);assertNull(n.getNotification("picture:$id",0));assertNotNull(n.getNotification("picture:$other",0))
  assertTrue(c.getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty())
 }
 @Test fun inFlightDownloadCannotBeTruncatedByDuplicateRetryAndRevokedRolePreventsDownload(){
  stage();val p=c.getSharedPreferences("mms_transfers",0);p.edit().putLong("$id.attempt",System.currentTimeMillis()).commit();val f=MessageTransferProvider.file(c,id,"pdu");val bytes=byteArrayOf(1,2,3);f.writeBytes(bytes)
  MessagingTransport.download(c,id);assertArrayEquals(bytes,f.readBytes());assertTrue(MessagingTransport.pendingDownloads(c).single().getBoolean("in_progress"))
  shadowOf(c.getSystemService(RoleManager::class.java)).removeHeldRole(RoleManager.ROLE_SMS);assertThrows(IllegalStateException::class.java){MessagingTransport.download(c,id)};assertArrayEquals(bytes,f.readBytes())
 }
 @Test fun scheduledDownloadNeverRunsWhenRoleWasRevokedAndMessagesRemainRecoverable(){
  stage();val p=c.getSharedPreferences("mms_transfers",0);p.edit().putLong("$id.retry_due",System.currentTimeMillis()-1).putInt("$id.retries",1).commit();shadowOf(c.getSystemService(RoleManager::class.java)).removeHeldRole(RoleManager.ROLE_SMS)
  MmsDownloadRecovery.recover(c);assertEquals(1,MessagingTransport.pendingDownloads(c).size);assertFalse(MessageTransferProvider.file(c,id,"pdu").exists());assertFalse(MessagingTransport.pendingDownloads(c).single().getBoolean("retry_scheduled"))
 }
 @Test fun acknowledgementFailureCannotReportAnAlreadyFiledPictureAsDownloadFailure(){
  stage();kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)}
  val provider=TransportRecoveryTest.Provider();org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mms",provider);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mms-sms",TransportRecoveryTest.Provider())
  val p=c.getSharedPreferences("mms_transfers",0);p.edit().putString("$id.transaction","x".repeat(201)).commit()
  val decoded=org.json.JSONObject().put("kind",132).put("status",128).put("from","+15550000000").put("parts",org.json.JSONArray().put(org.json.JSONObject().put("mime","text/plain").put("text","Picture caption")))
  MessageNotifications.picturePending(c,id,"Sender","Downloading")
  MessagingTransport.downloaded(c,id,true,Activity.RESULT_OK,decoded)
  assertTrue(p.all.toString(),p.getBoolean("$id.complete",false));assertFalse(p.contains("$id.error"));assertTrue(MessagingTransport.pendingDownloads(c).isEmpty())
  assertEquals(1,provider.inserted.count{it=="content://mms"});assertNull(shadowOf(c.getSystemService(NotificationManager::class.java)).getNotification("picture:$id",0))
 }
 @Test fun cancelledCodeAndLegacyStatusesRemainRetryableAndTruthful(){
  stage();assertTrue(incomingMmsTransient(Activity.RESULT_CANCELED))
  MessagingTransport.downloaded(c,id,false,Activity.RESULT_CANCELED)
  val p=c.getSharedPreferences("mms_transfers",0);assertEquals(1,p.getInt("$id.retries",0));assertTrue(p.getLong("$id.retry_due",0)>0)
  assertTrue(incomingMmsStatus("Carrier download failed (0). Check mobile data and try again.",0,100).contains("request is kept"))
  assertEquals("Picture waiting to download.",incomingMmsStatus("Downloading…",0,100))
  assertFalse(incomingMmsInProgress(500,100));assertTrue(incomingMmsStatus(null,500,100).contains("timed out"))
 }
 @Test fun stalledDownloadSchedulesBoundedRecoveryWithoutResendingAnything(){
  stage();val p=c.getSharedPreferences("mms_transfers",0);val now=System.currentTimeMillis()
  p.edit().putLong("$id.attempt",now-INCOMING_MMS_TIMEOUT_MS-1).putLong("$id.retry_due",now-1).commit()
  MmsDownloadRecovery.recover(c)
  assertEquals(0,p.getLong("$id.attempt",-1));assertEquals(1,p.getInt("$id.retries",0));assertTrue(p.getLong("$id.retry_due",0)>now)
  assertTrue(MessagingTransport.pendingDownloads(c).single().getBoolean("retry_scheduled"))
  assertFalse(MessageTransferProvider.file(c,id,"pdu").exists())
 }
 @Test fun oldAttemptCallbackCannotClearOrFailCurrentDownload(){
  stage();val p=c.getSharedPreferences("mms_transfers",0);val old="cccccccccccccccccccccccccccccccc";val current="dddddddddddddddddddddddddddddddd"
  val now=System.currentTimeMillis();p.edit().putString("$old.download_job",id).putString("$current.download_job",id).putString("$id.transfer",current).putLong("$id.attempt",now).putLong("$id.retry_due",now+INCOMING_MMS_TIMEOUT_MS).commit()
  MessageTransferProvider.file(c,old,"pdu").writeBytes(byteArrayOf(9));val file=MessageTransferProvider.file(c,current,"pdu");file.writeBytes(byteArrayOf(1,2,3))
  fun callback(token:String)=android.content.Intent(c,MessageResultReceiver::class.java).setAction("app.onklav.mms_download").setData(android.net.Uri.parse("onklav-result://mms_download/$token/0")).putExtra("kind","mms_download").putExtra("token",token).putExtra("part",0)
  MessagingTransport.receiveResult(c,callback(old),Activity.RESULT_CANCELED)
  assertEquals(now,p.getLong("$id.attempt",0));assertFalse(p.contains("$id.error"));assertArrayEquals(byteArrayOf(1,2,3),file.readBytes());assertFalse(p.contains("$old.download_job"))
  MessagingTransport.receiveResult(c,callback(current),Activity.RESULT_CANCELED)
  assertEquals(0,p.getLong("$id.attempt",-1));assertEquals(1,p.getInt("$id.retries",0));assertTrue(p.contains("$id.error"));assertTrue(CarrierResults(c).ids().isEmpty())
 }
 @Test fun realPlatformRequestUsesOriginalSimUniqueOutputAndMatchingCallback(){
  stage();shadowOf(c as Application).denyPermissions(android.Manifest.permission.READ_PHONE_STATE)
  val p=c.getSharedPreferences("mms_transfers",0)
  MessagingTransport.download(c,id)
  val first=p.getString("$id.transfer",null)!!;assertNotEquals(id,first)
  val manager=shadowOf(SmsManager.getSmsManagerForSubscriptionId(1));val request=manager.lastDownloadedMultimediaMessageParams!!
  assertEquals("http://carrier.example/picture",request.locationUrl);assertEquals("$first.pdu",request.contentUri.lastPathSegment)
  val callback=shadowOf(request.downloadedIntent).savedIntent;assertTrue(CarrierResults.valid(callback));assertEquals(first,callback.getStringExtra("token"))
  assertTrue(p.getLong("$id.retry_due",0)>System.currentTimeMillis());assertEquals(id,p.getString("$first.download_job",null))
  MessagingTransport.receiveResult(c,callback,Activity.RESULT_CANCELED)
  MessagingTransport.download(c,id)
  val second=p.getString("$id.transfer",null)!!;assertNotEquals(first,second)
  val after=manager.lastDownloadedMultimediaMessageParams!!;assertEquals("$second.pdu",after.contentUri.lastPathSegment)
  assertNotEquals(request.downloadedIntent,after.downloadedIntent);assertEquals(1,p.getInt("$id.sub",-1))
 }
 @Test fun unrecognizedCallbackCannotCreateAnIncomingMessageOrError(){
  val token="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
  val intent=android.content.Intent(c,MessageResultReceiver::class.java).setAction("app.onklav.mms_download").setData(android.net.Uri.parse("onklav-result://mms_download/$token/0")).putExtra("kind","mms_download").putExtra("token",token).putExtra("part",0)
  MessagingTransport.receiveResult(c,intent,Activity.RESULT_CANCELED)
  assertTrue(c.getSharedPreferences("mms_transfers",0).all.isEmpty());assertTrue(CarrierResults(c).ids().isEmpty())
 }
 @Test fun carrierErrorsExplainConfigurationAndDoNotRetryPermanentFailures(){
  assertTrue(incomingMmsFailure(SmsManager.MMS_ERROR_INVALID_APN).contains("APN"));assertTrue(incomingMmsFailure(SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION).contains("SIM"));assertFalse(incomingMmsTransient(SmsManager.MMS_ERROR_INVALID_APN));assertFalse(incomingMmsTransient(SmsManager.MMS_ERROR_DATA_DISABLED));assertTrue(incomingMmsTransient(SmsManager.MMS_ERROR_RETRY))
 }
 @Test fun oneRefusedJournalCannotLoseItsDeadlineOrBlockAnotherPicturesRecovery(){
  stage();stage(other);val p=c.getSharedPreferences("mms_transfers",0);val now=System.currentTimeMillis()
  p.edit().putLong("$id.attempt",now).putLong("$id.retry_due",now-1).putLong("$other.attempt",now).putLong("$other.retry_due",now-1).commit()
  val isolated=object:android.content.ContextWrapper(c){
   override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences{
    if(name!="mms_transfers")return super.getSharedPreferences(name,mode)
    return java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences::class.java.classLoader,arrayOf(android.content.SharedPreferences::class.java)){_,method,args->
     when(method.name){
      "getAll"->java.util.TreeMap(p.all)
      "edit"->{val editor=p.edit();val keys=mutableSetOf<String>()
       java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences.Editor::class.java.classLoader,arrayOf(android.content.SharedPreferences.Editor::class.java)){proxy,m,a->
        if(m.name=="commit"&&keys.any{it.startsWith(id)})false
        else {if((m.name.startsWith("put")||m.name=="remove")&&a?.firstOrNull() is String)keys+=a!![0] as String
         val value=m.invoke(editor,*(a?:emptyArray()));if(value is android.content.SharedPreferences.Editor)proxy else value}
       } as android.content.SharedPreferences.Editor
      }
      else->method.invoke(p,*(args?:emptyArray()))
     }
    } as android.content.SharedPreferences
   }
  }
  MmsDownloadRecovery.recover(isolated)
  assertEquals(now-1,p.getLong("$id.retry_due",0));assertEquals(now+INCOMING_MMS_TIMEOUT_MS,p.getLong("$other.retry_due",0))
  val job=c.getSystemService(JobScheduler::class.java).allPendingJobs.single{it.id==0x4D4D53}
  assertTrue(job.minLatencyMillis>=30000);assertTrue(ChatStore.lastError.value.orEmpty().contains("retained for retry"))
 }

}
