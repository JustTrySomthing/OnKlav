package app.onklav.home
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var m:CalendarModel
 private val date=LocalDate.of(2026,11,12)
 private fun waitFor(test:()->Boolean){compose.waitUntil(20000){shadowOf(android.os.Looper.getMainLooper()).idle();test()};compose.waitForIdle()}
 @Before fun setup(){val app=ApplicationProvider.getApplicationContext<Application>();File(app.filesDir,"calendar").deleteRecursively();app.getSharedPreferences("calendar-reminders",0).edit().clear().commit();val first=CalendarEvent(title="Design review",start=date.atTime(9,30),end=date.atTime(10,30),book="work",location="Studio",reminders=emptyList());val second=first.copy(id=java.util.UUID.randomUUID().toString(),title="A little time outside",start=date.atTime(14,0),end=date.atTime(15,0),book="personal",location="Riverside park");CalendarDisk(app).write(CalendarState(events=listOf(first,second)));m=CalendarModel(app);compose.activity.setContent{OnKlavTheme{CalendarWorkspace({},m)}};waitFor{m.ready&&!m.busy};compose.runOnUiThread{m.selected=date};compose.waitForIdle()}
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/calendar/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun monthDayWeekAgendaAndSearchAreReachable(){compose.onNodeWithText("Month",substring=false).assertIsDisplayed();capture("month");compose.onNodeWithText("Day",substring=false).performClick();waitFor{m.mode=="Day"};capture("day");compose.onNodeWithText("Week",substring=false).performClick();waitFor{m.mode=="Week"};capture("week");compose.onNodeWithText("Agenda",substring=false).performClick();waitFor{m.mode=="Agenda"};compose.onNodeWithText("Design review").assertExists();compose.onNodeWithContentDescription("Search events").performClick();compose.onNodeWithText("Search title, place or notes").performTextInput("Studio");waitFor{compose.onAllNodesWithText("Design review").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Design review").assertIsDisplayed();capture("search")}
 @Test fun editorLimitsToFiveRemindersAndSavesSoundChoice(){compose.onNodeWithContentDescription("Add event").performClick();compose.onNodeWithText("Event title").performTextInput("Dentist appointment");repeat(4){compose.onNodeWithText("Add reminder").performScrollTo().performClick()};compose.onNodeWithText("5 / 5").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Add reminder").assertDoesNotExist();compose.onNodeWithContentDescription("Reminder 1 sound").performScrollTo().performClick();capture("reminders");compose.onNodeWithText("Notes").performScrollTo().performTextInput("Bring insurance card");compose.onNodeWithText("Save",substring=false).assertIsDisplayed().performClick();waitFor{!m.busy&&m.draft==null};val saved=m.state.events.first{it.title=="Dentist appointment"};assertEquals(5,saved.reminders.size);assertTrue(saved.reminders[0].sound);assertEquals("Bring insurance card",saved.notes)}
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallPhoneLargeTextKeepsSaveAndReminderControlsReachable(){compose.onNodeWithContentDescription("Add event").performClick();compose.onNodeWithText("Event title").performTextInput("Small phone event");compose.onNodeWithText("Ends").performScrollTo().assertIsDisplayed();capture("small-editor-dates");compose.onNodeWithText("Add reminder").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Save",substring=false).assertIsDisplayed();capture("small-editor")}
 @Test fun lightThemeRetainsTheSameCalendarControls(){compose.activity.setContent{OnKlavTheme(preview=app.onklav.shared.AppearanceSpec(dark=false)){CalendarWorkspace({},m)}};waitFor{!m.busy};compose.onNodeWithText("Month",substring=false).assertIsDisplayed();compose.onNodeWithContentDescription("Add event").assertIsDisplayed();capture("light-month")}
 @Test fun deletingOneRecurringOccurrenceDoesNotDeleteSeriesAndUndoRestores(){val e=m.state.events.first().copy(repeat="daily");compose.runOnUiThread{m.edit(CalendarOccurrence(e,e.start.toLocalDate(),e.start.atZone(ZoneId.of(e.zone)),e.end.atZone(ZoneId.of(e.zone))),false);m.change(e)};compose.onNodeWithText("Save",substring=false).performClick();waitFor{!m.busy&&m.draft==null};val current=m.state.events.first{it.id==e.id};val o=CalendarRules.occurrences(current,date.atStartOfDay(ZoneId.systemDefault()).toInstant(),date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()).first();compose.runOnUiThread{m.delete(o,true)};waitFor{!m.busy};assertEquals(2,m.state.events.size);assertTrue(date in m.state.events.first{it.id==e.id}.excluded);compose.onNodeWithText("Undo").performClick();waitFor{!m.busy};assertTrue(m.state.events.first{it.id==e.id}.excluded.isEmpty())}
 @Test fun weekAllDayShortcutOpensTheEventAndSearchTakesFocus(){
  compose.runOnUiThread{val e=m.state.events.first().copy(title="Conference",allDay=true,start=date.atStartOfDay(),end=date.plusDays(3).atStartOfDay());m.edit(CalendarOccurrence(e,date,e.start.atZone(ZoneId.of(e.zone)),e.end.atZone(ZoneId.of(e.zone))),false);m.change(e)}
  compose.onNodeWithText("Save",substring=false).performClick();waitFor{!m.busy&&m.draft==null}
  compose.onNodeWithText("Week",substring=false).performClick();waitFor{m.mode=="Week"}
  compose.onNodeWithContentDescription("View 1 all-day events on ${calendarDate(date)}").performClick();waitFor{m.mode=="Day"}
  waitFor{compose.onAllNodesWithText("Conference").fetchSemanticsNodes().any{it.boundsInRoot.top>=0f&&it.boundsInRoot.bottom<=780f}};capture("day-all-day");compose.onNodeWithText("Conference").assertIsDisplayed();compose.onNodeWithText("Nov 12 – Nov 14 · All day").assertIsDisplayed()
  compose.onNodeWithContentDescription("Search events").performClick();compose.onNodeWithText("Search title, place or notes").assertIsFocused()
  assertEquals("Dec 28, 2026 – Jan 3, 2027",calendarWeekRange(LocalDate.of(2026,12,28)))
 }
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun calendarColorsWrapAndHaveAccessibleNames(){
  compose.onNodeWithContentDescription("Calendar options").performClick();compose.onNodeWithText("Calendars",substring=false).performClick()
  compose.onNodeWithContentDescription("Teal color for Personal").performScrollTo().assertIsDisplayed().performClick()
  compose.onNodeWithContentDescription("Teal color for Personal").assertIsSelected();capture("calendar-colors-small")
  compose.onNodeWithText("Save",substring=false).performClick();waitFor{!m.busy};assertEquals(5,m.state.books.first{it.name=="Personal"}.color)
 }

}
