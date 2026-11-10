package app.onklav.home

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/** Audit probes of observed defects, NOT assertions of desired production behavior.
 * Kept outside the normal regression suite. Remove/replace each probe when its finding is fixed. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AppAuditObservationTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private fun record(id:String,value:JSONObject){println("AUDIT_OBSERVATION "+value.put("finding",id));val root=File(System.getProperty("onklav.project.root"),"docs/evidence/app-audit").apply{mkdirs()};File(root,"$id.json").writeText(value.toString(2)+"\n")}
 private class FaultContext(base:Context):ContextWrapper(base){var failWrites=false
  override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
   val original=super.getSharedPreferences(name,mode)
   if(name!="native-clock")return original
   return object:SharedPreferences by original {override fun edit():SharedPreferences.Editor {
    val editor=original.edit();return object:SharedPreferences.Editor by editor {
     override fun putString(key:String?,value:String?):SharedPreferences.Editor {editor.putString(key,value);return this}
     override fun commit():Boolean=if(failWrites)false else editor.commit()
    }
   }}
  }
 }
 @Test fun unrelatedAlphanumericSendersCollapseToTheSameRecipientIdentity(){
  val match=sameRecipients("BANK","DELIVERY");assertTrue(match);assertEquals("",phoneKey("BANK"));record("A05",JSONObject().put("BANK_key",phoneKey("BANK")).put("DELIVERY_key",phoneKey("DELIVERY")).put("sameRecipients",match))
 }
 @Test fun failedAlarmEditCancelsTheOldScheduleButLeavesItEnabledOnDisk(){
  runBlocking{AppSdk.bootstrap(c)};val context=FaultContext(c);val alarm=ClockAlarm(hour=7,minute=30);ClockDisk.saveAlarm(context,alarm)
  val manager=Shadows.shadowOf(c.getSystemService(AlarmManager::class.java));assertEquals(1,manager.scheduledAlarms.size)
  context.failWrites=true;assertThrows(IllegalStateException::class.java){ClockDisk.saveAlarm(context,alarm.copy(hour=8))}
  val stored=ClockDisk.alarms(context).single();assertTrue(stored.enabled);assertEquals(7,stored.hour);assertTrue(manager.scheduledAlarms.isEmpty());record("A03-alarm",JSONObject().put("stored_enabled",stored.enabled).put("stored_hour",stored.hour).put("scheduled_alarms",manager.scheduledAlarms.size))
 }
 @Test fun failedNewTimerSaveLeavesAnOrphanedScheduledCallback(){
  runBlocking{AppSdk.bootstrap(c)};val context=FaultContext(c);context.failWrites=true;val timer=ClockTimer(duration=60000)
  assertThrows(IllegalStateException::class.java){ClockDisk.timer(context,timer,true)}
  val manager=Shadows.shadowOf(c.getSystemService(AlarmManager::class.java));assertTrue(ClockDisk.timers(context).isEmpty());assertEquals(1,manager.scheduledAlarms.size);record("A03-timer",JSONObject().put("stored_timers",0).put("scheduled_callbacks",1))
 }
 @Test fun normalCloseProtocolCanPublishAnIncompleteDocumentAsASavedFile(){
  runBlocking{AppSdk.bootstrap(c)};val uri=FileSelectionStore.save(c,listOf("documents"),"incomplete-copy.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  val file=FileSelectionStore.writable(c,uri);val fragment="PK-partial-copy".toByteArray();file.writeBytes(fragment)
  // openOutputStream(...).use closes normally even when its copy block throws.
  // Audio exports use closeWithError; the other writer paths under review do not.
  FileSelectionStore.completed(c,uri,null)
  val row=FileSelectionStore.read(c,uri);assertEquals("saved",row.getString("state"));assertArrayEquals(fragment,FileSelectionStore.file(c,row).readBytes());record("A01",JSONObject().put("file_state",row.getString("state")).put("bytes",fragment.size).put("is_complete_docx",false))
 }
}
