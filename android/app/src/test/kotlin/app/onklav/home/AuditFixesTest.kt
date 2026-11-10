package app.onklav.home
import android.app.AlarmManager
import android.content.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AuditFixesTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private class FaultContext(base:Context):ContextWrapper(base){var failWrites=false
  override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
   val original=super.getSharedPreferences(name,mode);if(name!="native-clock")return original
   return object:SharedPreferences by original {override fun edit():SharedPreferences.Editor {
    val editor=original.edit();return object:SharedPreferences.Editor by editor {
     override fun putString(key:String?,value:String?):SharedPreferences.Editor {editor.putString(key,value);return this}
     override fun commit():Boolean=if(failWrites)false else editor.commit()
    }
   }}
  }
 }
 @Test fun failedAlarmEditRestoresTheOriginalPendingIntentAndDeleteFailureKeepsIt(){
  runBlocking{AppSdk.bootstrap(c)};val context=FaultContext(c);val a=ClockAlarm(hour=7,minute=30);ClockDisk.saveAlarm(context,a)
  val manager=Shadows.shadowOf(c.getSystemService(AlarmManager::class.java));val before=manager.scheduledAlarms.single().triggerAtTime
  context.failWrites=true;assertThrows(IllegalStateException::class.java){ClockDisk.saveAlarm(context,a.copy(hour=8,next=0))}
  assertEquals(7,ClockDisk.alarms(context).single().hour);assertEquals(before,manager.scheduledAlarms.single().triggerAtTime)
  assertThrows(IllegalStateException::class.java){ClockDisk.deleteAlarm(context,a.id)};assertEquals(before,manager.scheduledAlarms.single().triggerAtTime)
 }
 @Test fun failedTimerCreationLeavesNoCallbackAndFailedPauseRestoresTheRunningTimer(){
  runBlocking{AppSdk.bootstrap(c)};val context=FaultContext(c);val t=ClockTimer(duration=60000);val manager=Shadows.shadowOf(c.getSystemService(AlarmManager::class.java));context.failWrites=true
  assertThrows(IllegalStateException::class.java){ClockDisk.timer(context,t,true)};assertTrue(ClockDisk.timers(context).isEmpty());assertTrue(manager.scheduledAlarms.isEmpty())
  context.failWrites=false;ClockDisk.timer(context,t,true);val saved=ClockDisk.timers(context).single();context.failWrites=true
  assertThrows(IllegalStateException::class.java){ClockDisk.timer(context,saved,false)};assertEquals(saved.wall,manager.scheduledAlarms.single().triggerAtTime);assertEquals(saved,ClockDisk.timers(context).single())
 }
 @Test fun senderMatchingKeepsOpaqueIdentitiesDistinctAndNormalizesPhones(){
  assertFalse(sameRecipients("BANK","DELIVERY"));assertFalse(sameRecipients("",""));assertTrue(sameRecipients("BANK","bank"));assertTrue(sameRecipients("+1 (555) 123-4567","5551234567"));assertEquals(phoneKey("BANK"),phoneKey("sender:bank"));assertFalse(sameRecipients("BANK123","123"))
 }
 @Test fun replyAllKeepsCcDeduplicatesCaseAndExcludesOwnAndReplyTarget(){
  val h=MailHeader("account","INBOX",1,2,"sender@example.test","Me <me@example.test>, TO@example.test","Subject",0,false,false,replyTo="reply@example.test",cc="cc@example.test, to@example.test, REPLY@example.test")
  assertEquals(h,MailHeader.parse(h.json()));assertEquals("",MailHeader.parse(h.json().apply{remove("cc")}).cc)
  assertEquals(listOf("TO@example.test","cc@example.test"),MailMime.addresses(mailReplyAllCc(h,"ME@example.test",h.replyTo)).map{it.address})
 }
 @Test fun errorCloseKeepsPartialExportsOutOfTheSavedFolder(){
  runBlocking{AppSdk.bootstrap(c)};val uri=FileSelectionStore.save(c,listOf("documents"),"interrupted.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  FileSelectionStore.writable(c,uri).writeText("partial");FileSelectionStore.completed(c,uri,java.io.IOException("Interrupted"))
  val row=FileSelectionStore.read(c,uri);assertEquals("recovery",row.getString("state"));assertFalse(row.has("path"));assertEquals("partial",FileSelectionStore.file(c,row).readText())
 }
}
