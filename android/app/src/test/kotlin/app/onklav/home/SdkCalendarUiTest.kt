package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkCalendarUiTest {
    private val c get()=ApplicationProvider.getApplicationContext<Application>()
    private val id="org.example.calendarfixture"
    private fun call(j:JSONObject)=AppSdk.sync(c,j) as JSONObject
    private fun install(granted:Boolean=true):String {
        File(c.filesDir,"app-install.zip").writeBytes(File(System.getProperty("onklav.project.root"),"docs/fixtures/calendar-wip.onklav-a").readBytes())
        val review=call(JSONObject().put("action","inspect"))
        call(JSONObject().put("action","install").put("hash",review.getString("hash")).put("grants",JSONArray(if(granted)listOf("storage.private")else emptyList<String>())))
        return launch().getString("session")
    }
    private fun launch()=call(JSONObject().put("action","launch").put("id",id))
    private fun event(session:String,action:String,value:String="")=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action).put("value",value))).getJSONObject("frame")
    private fun store()=File(c.filesDir,"app-sdk/data/$id/collection-calendar_fixture.json")

    @Test fun signedAllDayEditorSavesThroughJniAndReloadsTheSameExclusiveDateInterval() {
        val s=install();val opened=event(s,"calendar.open.0")
        assertEquals("edit",opened.getJSONObject("state").getString("route"))
        event(s,"calendar.summary","Trip 🐈")
        event(s,"calendar.end_date","2026-10-28")
        val saved=event(s,"calendar.save")
        assertEquals("agenda",saved.getJSONObject("state").getString("route"))
        val row=JSONObject(store().readText()).getJSONObject("rows").getJSONObject("event")
        assertEquals("Trip 🐈",row.getString("summary"));assertEquals("20261028",row.getString("end"));assertTrue(row.getBoolean("all_day"))
        val reopened=launch().getJSONObject("frame")
        assertEquals("Trip 🐈",reopened.getJSONObject("state").getJSONArray("events").getJSONObject(0).getString("summary"))
        assertTrue(reopened.getJSONArray("body").toString().contains("through 2026-10-27"))
    }

    @Test fun invalidDatesAndCancelledEditsDoNotWriteAndUseTheStandardCenteredConfirmation() {
        val s=install();event(s,"calendar.open.0")
        event(s,"calendar.end_date","2026-10-25")
        val failed=event(s,"calendar.save")
        assertEquals("edit",failed.getJSONObject("state").getString("route"));assertFalse(store().exists())
        val draft=failed.getJSONObject("state").getJSONObject("edit")
        assertEquals("2026-10-25",draft.getString("end_date"));assertFalse(draft.getString("error").isBlank())
        val confirm=event(s,"calendar.cancel").getJSONObject("confirm")
        assertEquals("Discard changes?",confirm.getString("title"))
        event(s,"calendar.keep")
        val discard=event(s,"calendar.cancel");assertTrue(discard.has("confirm"))
        val agenda=event(s,"calendar.discard")
        assertEquals("agenda",agenda.getJSONObject("state").getString("route"));assertFalse(store().exists())
    }

    @Test fun concurrentStorageChangeKeepsTheEditorDraftAndDoesNotOverwriteSavedData() {
        val s=install();event(s,"calendar.open.0");event(s,"calendar.save")
        event(s,"calendar.open.0");event(s,"calendar.summary","Keep this draft")
        val disk=JSONObject(store().readText());disk.put("revision",disk.getLong("revision")+1);store().writeText(disk.toString())
        val before=store().readBytes();val failed=event(s,"calendar.save")
        assertEquals("Keep this draft",failed.getJSONObject("state").getJSONObject("edit").getString("summary"))
        assertTrue(failed.getJSONObject("state").getString("status").contains("changed"));assertArrayEquals(before,store().readBytes())
    }

    @Test fun ungrantedStorageCreatesNoCollectionAndRevocationPreservesTheSavedEvent() {
        install(false);assertFalse(store().exists())
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id",id).put("grants",JSONArray(listOf("storage.private"))))
        val s=launch().getString("session");event(s,"calendar.open.0");event(s,"calendar.save")
        event(s,"calendar.open.0");event(s,"calendar.summary","Must not save")
        val before=store().readBytes()
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id",id).put("grants",JSONArray()))
        runCatching{event(s,"calendar.save")}
        assertArrayEquals(before,store().readBytes())
    }
}
