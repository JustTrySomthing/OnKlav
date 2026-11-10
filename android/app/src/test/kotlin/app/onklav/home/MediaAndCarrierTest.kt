package app.onklav.home

import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class MediaAndCarrierTest {
 private val c:Context get()=ApplicationProvider.getApplicationContext()
 @Test fun galleryIncludesAllMediaKindsAndPreservesIdentityWithoutDuplicates(){
  fun part(id:String,mime:String)=MessagePart("content://fixture/$id",mime,id)
  val old=Message(1,"",1,false,parts=listOf(part("photo","image/gif"),part("voice","audio/amr"),part("smil","application/smil")))
  val new=Message(2,"",1000,true,parts=listOf(part("movie","video/mp4"),part("photo","image/gif")))
  val rows=conversationMedia(listOf(old,new,old));assertEquals(listOf("movie","photo","voice"),rows.map{it.part.name});assertEquals(old.key,rows.last().message.key)
 }
 @Test fun animatedImagesDefaultOnAndPreferencePersists(){
  assertTrue(MediaPreferences.gifs(c));MediaPreferences.set(c,"animate_gifs",false);assertFalse(MediaPreferences.gifs(c));MediaPreferences.set(c,"fill_images",true);assertTrue(MediaPreferences.fill(c))
 }
 @Test fun defaultCarrierTrialStillRequiresAndroidSmsRole(){
  assertFalse(MessagingTransport.MMS_READY)
  assertTrue(MessagingTransport.carrierEnabled(c))
  assertFalse(MessagingTransport.role(c))
  assertThrows(IllegalStateException::class.java){SdkWalkieTransport.checkSetup(c)}
  assertThrows(IllegalStateException::class.java){
   MessagingTransport.sendMms(c,listOf("+15550100001"),"Hi",org.json.JSONArray())
  }
  c.getSharedPreferences("messaging_setup",0).edit().putBoolean("carrier_trial",false).commit()
  assertFalse(MessagingTransport.carrierEnabled(c))
  Shadows.shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY,true)
  MessagingTransport.enableCarrierTrial(c)
  assertTrue(MessagingTransport.carrierEnabled(c))
  assertFalse(MessagingTransport.role(c))
 }
 @Test fun oversizedComposedMmsKeepsDraftBlobAndDoesNotReachCarrier(){
  val roles=c.getSystemService(android.app.role.RoleManager::class.java)
  Shadows.shadowOf(roles).addHeldRole(android.app.role.RoleManager.ROLE_SMS)
  org.robolectric.shadows.ShadowSubscriptionManager.setDefaultSmsSubscriptionId(1)
  val sms=Shadows.shadowOf(MessagingTransport.manager(c))
  sms.clearLastSentMultimediaMessageParams()
  val id=MessageTransferProvider.id()
  val blob=MessageTransferProvider.file(c,id)
  blob.writeBytes(ByteArray(1_000_000))
  val parts=org.json.JSONArray().put(JSONObject().put("file",id).put("mime","audio/mp4").put("bytes",blob.length()))
  val failure=assertThrows(IllegalStateException::class.java){
   MessagingTransport.sendMms(c,listOf("+15550100001","+15550100002"),"Two recipients",parts)
  }
  assertTrue(failure.message.orEmpty().contains("exceeds the MMS size limit"))
  assertNull(sms.lastSentMultimediaMessageParams)
  assertEquals(1_000_000L,blob.length())
  assertFalse(blob.parentFile!!.listFiles().orEmpty().any{it.extension=="pdu"})
 }
 class Storage:ContentProvider(){
  override fun onCreate()=true
  override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor?=null
  override fun getType(uri:Uri)="application/octet-stream"
  override fun insert(uri:Uri,values:ContentValues?):Uri?=null
  override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
  override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
  override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{
   val name=uri.lastPathSegment!!;require(name in setOf("source","destination"));val f=File(context!!.cacheDir,"drive-test-$name")
   return ParcelFileDescriptor.open(f,if(mode=="r")ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
  }
 }
 @Test fun driveProviderCopyStreamsSelectedFileAndRejectsSameDestination()=runBlocking{
  val p=Storage();p.attachInfo(c,ProviderInfo().apply{authority="test.drive"});ShadowContentResolver.registerProviderInternal("test.drive",p)
  val bytes=ByteArray(1024*1024){(it%251).toByte()};File(c.cacheDir,"drive-test-source").writeBytes(bytes)
  BackupDocuments.copy(c,Uri.parse("content://test.drive/source"),Uri.parse("content://test.drive/destination"));assertArrayEquals(bytes,File(c.cacheDir,"drive-test-destination").readBytes())
  var rejected=false;try{BackupDocuments.copy(c,Uri.parse("content://test.drive/source"),Uri.parse("content://test.drive/source"))}catch(e:IllegalArgumentException){rejected=true};assertTrue(rejected)
 }
}
