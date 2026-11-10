package app.onklav.home
import android.app.*
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CalendarRemindersTest{
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Before fun reset(){File(app.filesDir,"calendar").deleteRecursively();app.getSharedPreferences("calendar-reminders",0).edit().clear().commit()}
 private fun event():CalendarEvent{val start=LocalDateTime.ofInstant(Instant.now().plusSeconds(300),ZoneOffset.UTC);return CalendarEvent(title="Reminder fixture",start=start,end=start.plusHours(1),zone="UTC",reminders=listOf(CalendarReminder(5,true),CalendarReminder(0,false)))}
 @Test fun oneScheduledAlarmRecoversAndEmptyCalendarCancelsIt(){val e=event();CalendarDisk(app).write(CalendarState(events=listOf(e)));CalendarReminders.recover(app);assertNotNull(shadowOf(app.getSystemService(AlarmManager::class.java)).nextScheduledAlarm);CalendarDisk(app).write(CalendarState());CalendarReminders.recover(app);assertNull(shadowOf(app.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)}
 @Test fun deliveredReminderDoesNotRepeatAndChannelsKeepSilentSeparateFromAlarmSound(){val e=event();CalendarDisk(app).write(CalendarState(events=listOf(e)));CalendarReminders.fire(app);val nm=app.getSystemService(NotificationManager::class.java);assertEquals(1,nm.activeNotifications.size);assertNull(nm.getNotificationChannel("calendar-silent").sound);assertNotNull(nm.getNotificationChannel("calendar-sound").sound);assertEquals(android.media.AudioAttributes.USAGE_ALARM,nm.getNotificationChannel("calendar-sound").audioAttributes.usage);CalendarReminders.fire(app);assertEquals(1,nm.activeNotifications.size);assertEquals(1,app.getSharedPreferences("calendar-reminders",0).getStringSet("delivered",emptySet())!!.size)}
 @Test fun snoozePersistsAndDeletingEventCancelsItsNotificationsAndAlarm(){val e=event();CalendarDisk(app).write(CalendarState(events=listOf(e)));CalendarReminders.fire(app);val key=app.getSystemService(NotificationManager::class.java).activeNotifications.single().tag;CalendarReminders.snooze(app,Intent().setAction("calendar.snooze").putExtra("key",key).putExtra("id",e.id).putExtra("updated",e.updated).putExtra("sound",true));assertEquals(1,org.json.JSONArray(app.getSharedPreferences("calendar-reminders",0).getString("snoozes","[]")).length());CalendarDisk(app).write(CalendarState());CalendarReminders.cleanup(app,listOf(e),emptyList());assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty());assertNull(shadowOf(app.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)}
 private fun snoozeIntent(e:CalendarEvent,key:String)=Intent().setAction("calendar.snooze").putExtra("key",key).putExtra("id",e.id).putExtra("updated",e.updated).putExtra("sound",true)
 @Test fun duplicateSnoozeActionDoesNotCreateOrPostponeAnotherReminder(){
  val e=event();CalendarDisk(app).write(CalendarState(events=listOf(e)));CalendarReminders.fire(app)
  val key=app.getSystemService(NotificationManager::class.java).activeNotifications.single().tag!!;val action=snoozeIntent(e,key)
  CalendarReminders.snooze(app,action);val p=app.getSharedPreferences("calendar-reminders",0);val first=org.json.JSONArray(p.getString("snoozes","[]")).getJSONObject(0)
  org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(3));CalendarReminders.snooze(app,action)
  val next=org.json.JSONArray(p.getString("snoozes","[]"));assertEquals(1,next.length());assertEquals(first.getLong("at"),next.getJSONObject(0).getLong("at"));assertEquals(first.getString("key"),next.getJSONObject(0).getString("key"))
 }
 @Test fun replayAfterTheSnoozedReminderFiredCannotResurrectItButFreshSnoozeWorks(){
  val e=event();CalendarDisk(app).write(CalendarState(events=listOf(e)));CalendarReminders.fire(app)
  val key=app.getSystemService(NotificationManager::class.java).activeNotifications.single().tag!!;val action=snoozeIntent(e,key)
  CalendarReminders.snooze(app,action);val p=app.getSharedPreferences("calendar-reminders",0);val child=org.json.JSONArray(p.getString("snoozes","[]")).getJSONObject(0).getString("key")
  // Robolectric's uptime advance does not advance System.currentTimeMillis.
  // Make the persisted RTC deadline due, then exercise the real fire path.
  val due=org.json.JSONArray(p.getString("snoozes","[]"));due.getJSONObject(0).put("at",System.currentTimeMillis()-1000);p.edit().putString("snoozes",due.toString()).commit()
  CalendarReminders.fire(app);assertEquals(0,org.json.JSONArray(p.getString("snoozes","[]")).length());assertTrue(p.getStringSet("delivered",emptySet())!!.contains(child))
  CalendarReminders.snooze(app,action);assertEquals(0,org.json.JSONArray(p.getString("snoozes","[]")).length())
  CalendarReminders.snooze(app,snoozeIntent(e,child));assertEquals(1,org.json.JSONArray(p.getString("snoozes","[]")).length())
 }
 @Test fun legacyTimestampSnoozeIsRecognizedBeforeAddingAStableSuccessor(){
  val e=event();CalendarDisk(app).write(CalendarState(events=listOf(e)));CalendarReminders.fire(app)
  val key=app.getSystemService(NotificationManager::class.java).activeNotifications.single().tag!!;val p=app.getSharedPreferences("calendar-reminders",0)
  val legacy=org.json.JSONObject().put("key","$key:snooze:123").put("id",e.id).put("updated",e.updated).put("at",System.currentTimeMillis()+600000).put("sound",true)
  p.edit().putString("snoozes",org.json.JSONArray().put(legacy).toString()).commit();CalendarReminders.snooze(app,snoozeIntent(e,key))
  val pending=org.json.JSONArray(p.getString("snoozes","[]"));assertEquals(1,pending.length());assertEquals(legacy.getString("key"),pending.getJSONObject(0).getString("key"))
 }

}
