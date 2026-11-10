package app.onklav.home
import android.app.Application
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
class CalendarModelTest{
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var e:CalendarEvent
 private lateinit var m:CalendarModel
 private fun waitIdle(){val until=System.nanoTime()+20_000_000_000L;do{shadowOf(android.os.Looper.getMainLooper()).idle();if(!m.busy)return;Thread.sleep(10)}while(System.nanoTime()<until);fail("Calendar operation timed out")}
 private fun occurrence(event:CalendarEvent=e)=CalendarOccurrence(event,event.start.toLocalDate(),event.start.atZone(ZoneId.of(event.zone)),event.end.atZone(ZoneId.of(event.zone)))
 @Before fun setup(){File(app.filesDir,"calendar").deleteRecursively();app.getSharedPreferences("calendar-reminders",0).edit().clear().commit();e=CalendarEvent(title="Original",reminders=emptyList());CalendarDisk(app).write(CalendarState(events=listOf(e)));m=CalendarModel(app);waitIdle();assertTrue(m.ready)}
 @Test fun staleEditorCannotResurrectAnEventDeletedInAnotherWindow(){m.edit(occurrence(),false);m.change(e.copy(title="Stale edit"));CalendarDisk(app).write(CalendarState(events=emptyList(),revision=2));m.save();waitIdle();assertTrue(m.failed);assertTrue(CalendarDisk(app).read().events.isEmpty());assertNotNull(m.draft)}
 @Test fun undoRestoresTheFreshTransactionWithoutLosingAnotherWindowsEvent(){val extra=CalendarEvent(title="Added elsewhere",reminders=emptyList());CalendarDisk(app).write(CalendarState(events=listOf(e,extra),revision=2));m.delete(occurrence(),false);waitIdle();assertFalse(m.failed);assertEquals(listOf(extra.id),m.state.events.map{it.id});m.undo();waitIdle();assertFalse(m.failed);assertEquals(setOf(e.id,extra.id),m.state.events.map{it.id}.toSet())}
 @Test fun occurrenceRevisionStaysMonotonicWhenPhoneClockMovesBack(){val future=e.copy(repeat="daily",updated=System.currentTimeMillis()+60000);CalendarDisk(app).write(CalendarState(events=listOf(future)));m.load();waitIdle();m.delete(occurrence(future),true);waitIdle();assertFalse(m.failed);assertTrue(m.state.events.single().updated>future.updated)}
 @Test fun importsChooseACalendarKeepExistingUidsAndCanBeUndone(){val added=e.copy(id=java.util.UUID.randomUUID().toString(),uid="new-import",title="Imported appointment");val file=File(app.cacheDir,"calendar-import.ics");file.writeText(CalendarIcs.export(listOf(e,added)));m.import(android.net.Uri.fromFile(file));waitIdle();assertFalse(m.failed);assertNotNull(m.imported);m.acceptImport("work");waitIdle();assertFalse(m.failed);assertEquals(2,m.state.events.size);assertEquals("work",m.state.events.first{it.uid=="new-import"}.book);assertEquals("personal",m.state.events.first{it.id==e.id}.book);m.undo();waitIdle();assertEquals(listOf(e.id),m.state.events.map{it.id});file.delete()}
 @Test fun notificationOpensTheRequestedRecurringDate(){val repeated=e.copy(repeat="daily");CalendarDisk(app).write(CalendarState(events=listOf(repeated)));m.load();waitIdle();val date=e.start.toLocalDate().plusDays(30);m.notification(e.id,date.toString());assertEquals(date,m.selected);assertEquals(date,m.detail!!.date)}
 @Test fun exportIncludesEventsCommittedAfterThisWindowLoaded(){
  val extra=CalendarEvent(title="Committed elsewhere",reminders=emptyList());CalendarDisk(app).write(CalendarState(events=listOf(e,extra),revision=2))
  val provider=AuditRepairTest.Provider(app).also{it.attachInfo(app,android.content.pm.ProviderInfo().apply{authority="calendar-fresh-export";applicationInfo=app.applicationInfo});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("calendar-fresh-export",it)}
  m.export(android.net.Uri.parse("content://calendar-fresh-export/file"));waitIdle();assertFalse(m.message,m.failed)
  assertEquals(setOf(e.uid,extra.uid),CalendarIcs.parse(provider.file.readText()).events.map{it.uid}.toSet())
 }
 @Test fun timedReminderSelectsTheDisplayDayRatherThanTheEventsForeignDate(){
  val old=java.util.TimeZone.getDefault()
  try{java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
   val start=LocalDateTime.of(2026,11,3,0,30);val foreign=e.copy(start=start,end=start.plusHours(1),zone="Pacific/Kiritimati")
   CalendarDisk(app).write(CalendarState(events=listOf(foreign)));m.load();waitIdle();m.notification(foreign.id,"2026-11-03")
   assertEquals(LocalDate.of(2026,11,2),m.selected);assertEquals(LocalDate.of(2026,11,3),m.detail!!.date)
  }finally{java.util.TimeZone.setDefault(old)}
 }

}
