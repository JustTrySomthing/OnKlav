package app.onklav.home
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class) @org.robolectric.annotation.Config(sdk=[29])
class WeatherDisplayTest {
 @Test fun forecastTimesRespectCityTimezoneAndDst(){val n=JSONObject().put("zone","America/Chicago").put("forecast",JSONObject().put("points",JSONArray().put(JSONArray().put("2026-11-01T06:00:00Z").put(10).put(2).put(80).put(0).put("clearsky_night")).put(JSONArray().put("2026-11-01T08:00:00Z").put(12).put(2).put(70).put(0).put("cloudy"))));val p=weatherPoints(n);assertEquals(1,p[0].at.hour);assertEquals(2,p[1].at.hour);assertNotEquals(p[0].at.offset,p[1].at.offset);assertEquals("Light rain",weatherCondition("lightrainshowers_day"));}
 @Test fun malformedForecastTimesAndTemperaturesAreSkipped(){val n=JSONObject().put("zone","invalid").put("forecast",JSONObject().put("points",JSONArray().put(JSONArray().put("invalid").put(10)).put(JSONArray().put("2026-10-26T12:00:00Z").put(500))));assertTrue(weatherPoints(n).isEmpty())}
}
