package app.onklav.home
import android.app.*
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File
import java.time.*

@Implements(AlarmManager::class)
class RepairAlarmManager {
 companion object{var refuse=false;var scheduled:Long?=null}
 @Implementation fun cancel(operation:PendingIntent){scheduled=null}
 @Implementation fun setExactAndAllowWhileIdle(type:Int,at:Long,operation:PendingIntent){if(refuse)throw SecurityException("Injected schedule failure");scheduled=at}
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],shadows=[RepairAlarmManager::class])
class CalendarScheduleRepairTest{
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private fun idle(m:CalendarModel){val end=System.nanoTime()+10_000_000_000L;do{shadowOf(android.os.Looper.getMainLooper()).idle();if(!m.busy)return;Thread.sleep(10)}while(System.nanoTime()<end);fail("Calendar operation timed out")}
 @Test fun schedulingFailurePreservesEventAndPreviousScheduleAndRetryClearsTheWarning(){
  File(app.filesDir,"calendar").deleteRecursively();app.getSharedPreferences("calendar-reminders",0).edit().clear().commit();RepairAlarmManager.refuse=false;RepairAlarmManager.scheduled=null
  val start=LocalDate.now().plusDays(1).atTime(10,0);val e=CalendarEvent(title="Before",start=start,end=start.plusHours(1));CalendarDisk(app).write(CalendarState(events=listOf(e)))
  val m=CalendarModel(app);idle(m);val before=RepairAlarmManager.scheduled;assertNotNull(before)
  val o=CalendarOccurrence(e,e.start.toLocalDate(),e.start.atZone(ZoneId.of(e.zone)),e.end.atZone(ZoneId.of(e.zone)))
  m.edit(o,false);m.change(e.copy(title="After",start=start.plusHours(1),end=start.plusHours(2)));RepairAlarmManager.refuse=true;m.save();idle(m)
  assertEquals("After",CalendarDisk(app).read().events.single().title);assertEquals(before,RepairAlarmManager.scheduled);assertTrue(m.reminderError.isNotBlank());assertTrue(m.message.contains("Reminders not scheduled"));assertNull(m.draft)
  RepairAlarmManager.refuse=false;m.retryReminders();idle(m);assertEquals("",m.reminderError);assertEquals("Reminders updated",m.message);assertTrue(RepairAlarmManager.scheduled!!>before!!)
 }
}
