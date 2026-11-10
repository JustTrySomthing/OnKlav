package app.onklav.home
import android.content.Intent
import android.provider.CalendarContract
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CalendarIntentTest{
 @Test fun allDayEventIntentRetainsUtcDateRangeEvenWithAnOppositeTimeZone(){val base=CalendarEvent();val i=Intent(Intent.ACTION_INSERT).putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,true).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,Instant.parse("2026-06-01T00:00:00Z").toEpochMilli()).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,Instant.parse("2026-06-04T00:00:00Z").toEpochMilli()).putExtra(CalendarContract.Events.EVENT_TIMEZONE,"Pacific/Honolulu").putExtra(CalendarContract.Events.TITLE,"Holiday");val e=calendarIntentEvent(i,base);assertEquals(LocalDate.parse("2026-06-01"),e.start.toLocalDate());assertEquals(LocalDate.parse("2026-06-04"),e.end.toLocalDate());assertTrue(e.allDay);assertEquals(base.id,e.id)}
 @Test fun OutOfRangeExternalDatesBecomeANewUnsavedForm(){val base=CalendarEvent();val i=Intent(Intent.ACTION_INSERT).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,Long.MAX_VALUE-3600001).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,Long.MAX_VALUE-1).putExtra(CalendarContract.Events.TITLE,"Untrusted date");val e=calendarIntentEvent(i,base);assertEquals(base.start,e.start);assertTrue(e.title.isBlank());assertTrue(e.notes.contains("could not be used"))}
}
