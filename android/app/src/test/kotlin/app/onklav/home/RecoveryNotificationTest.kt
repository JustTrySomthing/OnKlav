package app.onklav.home

import android.Manifest
import android.app.*
import android.app.role.RoleManager
import android.content.*
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class RecoveryNotificationTest {
 private val c:Context get()=ApplicationProvider.getApplicationContext()
 @Test fun collidingThreadsKeepIndependentNotificationsAndAllActionTargets(){
  val a=1L;val b=1L shl 32;assertEquals(a.hashCode(),b.hashCode())
  MessageNotifications.incoming(c,a,"+15550100001","First");MessageNotifications.incoming(c,b,"+15550100002","Second")
  val manager=c.getSystemService(NotificationManager::class.java);val rows=manager.activeNotifications.associateBy{it.tag}
  assertEquals(2,rows.size);val first=rows.getValue("thread:$a").notification;val second=rows.getValue("thread:$b").notification
  assertNotEquals(first.contentIntent,second.contentIntent)
  assertEquals(a,Shadows.shadowOf(first.contentIntent).savedIntent.getLongExtra("open_thread",0))
  assertEquals(b,Shadows.shadowOf(second.contentIntent).savedIntent.getLongExtra("open_thread",0))
  for(i in 0..1){assertNotEquals(first.actions[i].actionIntent,second.actions[i].actionIntent);assertEquals(a,Shadows.shadowOf(first.actions[i].actionIntent).savedIntent.getLongExtra("thread",0))}
  MessageNotifications.cancel(c,a);assertEquals(listOf("thread:$b"),manager.activeNotifications.map{it.tag})
 }
 @Test fun aNewNotificationOpensTheExactSmsOrMmsEvenWhenProviderIdsMatch() {
  val thread = 7L
  val manager = c.getSystemService(NotificationManager::class.java)
  MessageNotifications.incoming(c, thread, "+15550100001", "Text", fileThread = false,
   messageKey = "sms:21", messageDate = 1200L)
  val sms = manager.activeNotifications.single().notification.contentIntent
  val provider = object : ContentProvider() {
   override fun onCreate() = true
   override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
    args: Array<out String>?, sort: String?) = android.database.MatrixCursor(requireNotNull(projection)).apply {
    if (uri.lastPathSegment == "21") addRow(arrayOf(thread, 2L))
   }
   override fun getType(uri: Uri): String? = null
   override fun insert(uri: Uri, values: ContentValues?): Uri? = null
   override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
   override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
  }
  ShadowContentResolver.registerProviderInternal("mms", provider)
  val transfer = "b".repeat(32)
  c.getSharedPreferences("mms_transfers", 0).edit()
   .putBoolean("$transfer.file_pending", true).putBoolean("$transfer.folder_filed", true)
   .putString("$transfer.uri", "content://mms/21").putString("$transfer.sender", "+15550100001").commit()
  assertTrue(MessagingTransport.finishReceivedMms(c, transfer))
  val mms = manager.activeNotifications.single().notification.contentIntent
  val feed = listOf(Message(21, "Text", 1200L, false), Message(21, "", 2000L, false, key = "mms:21"))
  assertNotEquals(sms, mms)
  listOf(sms, mms).forEachIndexed { index, pending ->
   val tap = Shadows.shadowOf(pending).savedIntent
   assertEquals(thread, tap.getLongExtra("open_thread", 0))
   assertEquals(index, focusedMessageIndex(feed, tap.getStringExtra("open_message_key")!!, tap.getLongExtra("open_message_date", 0)))
  }
 }
 @Test fun remindersAttentionAndThreadsCannotOverwriteOrCancelEachOther(){
  assertEquals("Aa".hashCode(),"BB".hashCode());MessageNotifications.remind(c,"Aa",1,"A","a");MessageNotifications.remind(c,"BB",2,"B","b");MessageNotifications.attention(c,"Attention","x");MessageNotifications.incoming(c,170017,"+15550100003","Thread")
  val m=c.getSystemService(NotificationManager::class.java);assertEquals(4,m.activeNotifications.size)
  ReplyReminders.schedule(c,"Aa",0);val tags=m.activeNotifications.map{it.tag}.toSet();assertEquals(setOf("reminder:BB","messaging-attention","thread:170017"),tags)
 }
 @Test fun removalRecoveryKeepsWorkEnqueuedDuringProfileDeletionAndRetriesFailures(){
  val a="test.example.cleanupone";val b="test.example.cleanuptwo";val oldA=SdkNativeData.profile(c,a);val oldB=SdkNativeData.profile(c,b)
  SdkNativeData.removed(c,a,true,clearAccounts={_,_->}){_,_->};SdkNativeData.flush(c){name->assertEquals(oldA,name);SdkNativeData.removed(c,b,true,clearAccounts={_,_->}){_,_->};true}
  val tried=mutableListOf<String>();SdkNativeData.flush(c){tried+=it;false};assertEquals(listOf(oldB),tried)
  SdkNativeData.flush(c){assertEquals(oldB,it);true};SdkNativeData.flush(c){error("Already deleted")}
  assertNotEquals(oldA,SdkNativeData.profile(c,a));assertNotEquals(oldB,SdkNativeData.profile(c,b))
 }
 class Mail:ContentProvider(){var writes=0;var denyAfterFirst=false;override fun onCreate()=true
  override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor?=null
  override fun getType(u:Uri)="vnd.android.cursor.dir/sms"
  override fun insert(u:Uri,v:ContentValues?):Uri?=null
  override fun delete(u:Uri,s:String?,a:Array<out String>?)=0
  override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?):Int{writes++;if(denyAfterFirst)Shadows.shadowOf(context!!.applicationContext as Application).denyPermissions(Manifest.permission.READ_SMS);return 1}
 }
 @Test fun markReadRechecksRoleAndPermissionBeforeProviderWritesAndLocalState(){
  val app=c.applicationContext as Application;val roles=Shadows.shadowOf(c.getSystemService(RoleManager::class.java));roles.addHeldRole(RoleManager.ROLE_SMS)
  val p=Mail();p.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="sms;mms";applicationInfo=c.applicationInfo});ShadowContentResolver.registerProviderInternal("sms",p);ShadowContentResolver.registerProviderInternal("mms",p)
  val chat=ChatStore(c);chat.requestSync(JSONObject().put("action","thread").put("key","7").put("unread",true).put("at",1))
  Shadows.shadowOf(app).denyPermissions(Manifest.permission.READ_SMS);assertThrows(IllegalStateException::class.java){MessageNotificationReceiver().markRead(c,7)};assertEquals(0,p.writes)
  Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS);p.denyAfterFirst=true;assertThrows(IllegalStateException::class.java){MessageNotificationReceiver().markRead(c,7)};assertEquals(1,p.writes);assertTrue(chat.requestSync(JSONObject().put("action","snapshot")).thread(7).getBoolean("unread"))
  p.denyAfterFirst=false;Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS);MessageNotificationReceiver().markRead(c,7);assertEquals(3,p.writes);assertFalse(chat.requestSync(JSONObject().put("action","snapshot")).thread(7).getBoolean("unread"))
  roles.removeHeldRole(RoleManager.ROLE_SMS);assertThrows(IllegalStateException::class.java){MessageNotificationReceiver().markRead(c,7)};assertEquals(3,p.writes)
 }
 @Test fun conversationReadDoesNotCommitLocalSuccessAfterPermissionLoss()=kotlinx.coroutines.runBlocking {
  val app=c.applicationContext as Application;val roles=Shadows.shadowOf(c.getSystemService(RoleManager::class.java));roles.addHeldRole(RoleManager.ROLE_SMS)
  Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS)
  val p=Mail();p.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="sms;mms";applicationInfo=c.applicationInfo});ShadowContentResolver.registerProviderInternal("sms",p);ShadowContentResolver.registerProviderInternal("mms",p)
  val chat=ChatStore(c);chat.requestSync(JSONObject().put("action","thread").put("key","17").put("unread",true).put("at",1))
  p.denyAfterFirst=true
  try{MessageRepository(c).markRead(17);fail("Expected revoked access")}catch(_:IllegalStateException){}
  assertEquals(1,p.writes);assertTrue(chat.requestSync(JSONObject().put("action","snapshot")).thread(17).getBoolean("unread"))
  p.denyAfterFirst=false;Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS);MessageRepository(c).markRead(17)
  assertEquals(3,p.writes);assertFalse(chat.requestSync(JSONObject().put("action","snapshot")).thread(17).getBoolean("unread"))
 }
 @Test fun queuedReplyIsNotReportedAsFailedWhenReadAccessDisappears(){
  Shadows.shadowOf(c.applicationContext as Application).denyPermissions(Manifest.permission.READ_SMS)
  val chat=ChatStore(c);chat.requestSync(JSONObject().put("action","thread").put("key","9").put("unread",true).put("at",1))
  MessageNotificationReceiver().finishRead(c,9,true)
  val note=c.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
  assertEquals("Reply Queued",note.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
  assertTrue(chat.requestSync(JSONObject().put("action","snapshot")).thread(9).getBoolean("unread"))
 }

 @Test fun profileCleanupAcceptsAlreadyMissingProfilesButRetainsRejectedExistingOnes(){
  var present=true
  val store=java.lang.reflect.Proxy.newProxyInstance(androidx.webkit.ProfileStore::class.java.classLoader,arrayOf(androidx.webkit.ProfileStore::class.java)){_,method,_->when(method.name){"deleteProfile"->false;"getAllProfileNames"->if(present)listOf("old")else emptyList<String>();else->error("Unexpected profile call")}} as androidx.webkit.ProfileStore
  assertFalse(SdkNativeData.deleteProfile(store,"old"));present=false;assertTrue(SdkNativeData.deleteProfile(store,"old"))
 }

}
