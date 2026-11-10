package app.onklav.home

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkClockTest {
    @Test fun snapshotKeepsDstAndLocalDateBoundariesConsistent() {
        val zone=ZoneId.of("America/Chicago")
        fun at(s:String)=SdkClock.snapshot(Instant.parse(s),123,zone,Locale.US,false)
        val before=at("2026-03-08T07:59:59Z");val after=at("2026-03-08T08:00:00Z")
        assertEquals("01:59:59",before.getString("local_time"))
        assertEquals("03:00",after.getString("local_time"))
        assertEquals(-21600,before.getInt("offset_seconds"))
        assertEquals(-18000,after.getInt("offset_seconds"))
        assertFalse(before.getBoolean("daylight_saving"));assertTrue(after.getBoolean("daylight_saving"))
        assertEquals("2026-10-24",at("2026-10-25T00:30:00Z").getString("local_date"))
        val fall1=at("2026-11-01T06:30:00Z");val fall2=at("2026-11-01T07:30:00Z")
        assertEquals(fall1.getString("local_time"),fall2.getString("local_time"))
        assertNotEquals(fall1.getInt("offset_seconds"),fall2.getInt("offset_seconds"))
    }

    @Test fun snapshotFollowsLocaleWeekStartAndUserHourCycle() {
        val instant=Instant.parse("2026-10-25T00:30:00Z")
        val us=SdkClock.snapshot(instant,555,ZoneId.of("UTC"),Locale.US,false)
        val gb=SdkClock.snapshot(instant,555,ZoneId.of("UTC"),Locale.UK,true)
        assertEquals(7,us.getInt("first_weekday"));assertEquals(1,gb.getInt("first_weekday"))
        assertEquals("h12",us.getString("hour_cycle"));assertEquals("h23",gb.getString("hour_cycle"))
        assertEquals("en-GB",gb.getString("locale"));assertEquals(555,gb.getLong("monotonic_ms"))
        val c=ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(c.contentResolver,Settings.System.TIME_12_24,"24")
        assertEquals("h23",SdkClock.snapshot(c).getString("hour_cycle"))
        Settings.System.putString(c.contentResolver,Settings.System.TIME_12_24,"12")
        assertEquals("h12",SdkClock.snapshot(c).getString("hour_cycle"))
    }

    @Test fun permissionFreeSignedGuestReadsClockAndRemovalInvalidatesPendingRequest() {
        val c=ApplicationProvider.getApplicationContext<Application>()
        fun call(j:JSONObject)=AppSdk.sync(c,j) as JSONObject
        File(c.filesDir,"app-install.zip").writeBytes(File(System.getProperty("onklav.project.root"),"docs/fixtures/clock-wip.onklav-a").readBytes())
        val review=call(JSONObject().put("action","inspect"))
        assertEquals(0,review.getJSONObject("manifest").getJSONArray("capabilities").length())
        call(JSONObject().put("action","install").put("hash",review.getString("hash")).put("grants",JSONArray()))
        val app=call(JSONObject().put("action","launch").put("id","org.example.clockfixture"))
        val session=app.getString("session");val pending=app.getJSONObject("pending")
        assertEquals("clock.read",pending.getString("service"))
        fun request(action:String)=JSONObject().put("action",action).put("session",session).put("token",pending.getString("token"))
        assertEquals("clock.read",call(request("authorize")).getString("service"))
        val snapshot=SdkClock.snapshot(c)
        val frame=call(request("reply").put("reply",JSONObject().put("ok",true).put("value",snapshot))).getJSONObject("frame")
        assertTrue(frame.getJSONObject("state").getJSONObject("result").getBoolean("ok"))
        val refresh=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action","refresh"))).getJSONObject("pending")
        AppSdk.sync(c,JSONObject().put("action","remove").put("id","org.example.clockfixture").put("delete_data",true))
        assertThrows(IllegalStateException::class.java){call(JSONObject().put("action","authorize").put("session",session).put("token",refresh.getString("token")))}
    }
}
