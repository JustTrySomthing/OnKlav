package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.app.AlarmManager
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import android.app.PendingIntent

// These pass when a defect is OBSERVED. They are not desired-behavior regressions.
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29], manifest=Config.NONE)
class AuditObservations {
 @Test fun timestampUntilIsRoundedUpToTheWholeDate(){
  val text="BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:until-probe\nSUMMARY:Until probe\nDTSTART:20261101T100000Z\nDTEND:20261101T110000Z\nRRULE:FREQ=DAILY;UNTIL=20261103T090000Z\nEND:VEVENT\nEND:VCALENDAR\n"
  val parsed=CalendarIcs.parse(text);assertEquals(0,parsed.skipped)
  val rows=CalendarRules.occurrences(parsed.events.single(),Instant.parse("2026-11-01T00:00:00Z"),Instant.parse("2026-11-05T00:00:00Z"))
  assertEquals(3,rows.size);assertTrue(rows.last().start.toInstant()>Instant.parse("2026-11-03T09:00:00Z"))
  println("OBSERVED B04: timestamp UNTIL admits an extra occurrence: ${rows.last().start}")
 }
 @Test fun cloudStoreCanWriteAnEnvelopeItsOwnReaderRejects(){
  val c=ApplicationProvider.getApplicationContext<Application>();val dir=File(c.noBackupFilesDir,"cloud-files");dir.deleteRecursively()
  val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
  val keys=object:SecretKeys{override fun load(alias:String,create:Boolean):SecretKey=key;override fun deletePrefix(prefix:String){}}
  val store=CloudStore(c,keys)
  val accounts=List(20){CloudAccount(name="Account $it",endpoint="https://example.test/dav/",user="fixture",password="\\".repeat(4096))}
  accounts.forEach{it.validate()};store.save(accounts)
  val file=File(dir,"accounts.bin");assertTrue(file.length()>150000)
  assertThrows(Exception::class.java){store.load()};assertThrows(Exception::class.java){store.save(emptyList())}
  println("OBSERVED B05: valid accounts written (${file.length()} bytes), subsequent load and save rejected")
  dir.deleteRecursively()
 }
}

@Implements(AlarmManager::class)
class AuditFailingAlarmManager {
 @Implementation fun cancel(operation:PendingIntent){}
 @Implementation fun setExactAndAllowWhileIdle(type:Int,at:Long,operation:PendingIntent){throw SecurityException("Injected scheduling refusal")}
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29], manifest=Config.NONE, shadows=[AuditFailingAlarmManager::class])
class AuditScheduleObservation {
 private fun idle(m:CalendarModel){val until=System.nanoTime()+10_000_000_000L;do{shadowOf(android.os.Looper.getMainLooper()).idle();if(!m.busy)return;Thread.sleep(10)}while(System.nanoTime()<until);fail("Model timed out")}
 @Test fun reminderSchedulingFailureIsReportedAsEventSaved(){
  val app=ApplicationProvider.getApplicationContext<Application>()
  File(app.filesDir,"calendar").deleteRecursively();app.getSharedPreferences("calendar-reminders",0).edit().clear().commit()
  val m=CalendarModel(app);idle(m);assertTrue(m.ready)
  m.new(LocalDate.now().plusDays(1));m.change(m.draft!!.copy(title="Scheduling fault probe"));m.save();idle(m)
  assertEquals(1,CalendarDisk(app).read().events.size);assertFalse(m.failed);assertEquals("Event saved",m.message)
  assertThrows(SecurityException::class.java){CalendarReminders.recover(app)}
  println("OBSERVED B06: scheduling throws, durable event exists, UI reports Event saved without failure")
 }
}
