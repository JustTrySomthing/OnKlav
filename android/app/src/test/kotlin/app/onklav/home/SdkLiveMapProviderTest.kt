package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Explicit opt-in live check: one OSM world tile, no geocoding/routing or prefetch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkLiveMapProviderTest {
 @Test fun signedMapFetchesOneRealTileCachesDecodesAndBlocksRevokedAccess()=runBlocking<Unit> {
  val path=System.getenv("ONKLAV_LIVE_MAP_PACKAGE")
  assumeTrue("Needs a disposable signed reference-map package; no live request by default",!path.isNullOrBlank())
  val c=ApplicationProvider.getApplicationContext<Application>()
  fun call(value:JSONObject)=AppSdk.sync(c,value) as JSONObject
  File(path!!).inputStream().use{AppSdk.stage(c,it)}
  AppSdk.install(c,AppSdk.inspect(c),setOf("storage.private","network.http"))
  val launched=call(JSONObject().put("action","launch").put("id","org.example.referencemap"));val session=launched.getString("session")
  fun event(action:String,value:String="")=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action).put("value",value)))
  fun component()=call(JSONObject().put("action","map_component").put("session",session).put("node","map.preview"))
  try {
   event("map.latitude","0");event("map.longitude","0");event("map.zoom","0");event("map.open")
   call(JSONObject().put("action","map_event").put("session",session).put("node","map.preview").put("value",JSONObject().put("width",256).put("height",256)))
   val pending=event("map.load").getJSONObject("pending")
   assertEquals("http.request",pending.getString("service"))
   fun authorize()=call(JSONObject().put("action","authorize").put("session",session).put("token",pending.getString("token")))
   assertEquals("https://tile.openstreetmap.org/0/0/0.png",authorize().getJSONObject("args").getString("url"))
   val response=SdkHttpService.fetch("live-osm-world",{authorize()})
   assertEquals(200,response.getInt("status"));assertTrue(response.getString("hex").startsWith("89504e470d0a1a0a"))
   val result=call(JSONObject().put("action","reply").put("session",session).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",true).put("value",response)))
   assertTrue(result.isNull("pending"));assertEquals(1,result.getJSONObject("frame").getJSONObject("state").getJSONObject("map").getJSONArray("tiles").length())
   val resolved=component();val tile=resolved.getJSONArray("tiles").getJSONObject(0).getJSONObject("file")
   val bitmap=SdkMapTiles.decode(c,resolved.getJSONObject("owner"),tile){component()}
   assertEquals(256,bitmap.width);assertEquals(256,bitmap.height);bitmap.recycle()
   val cached=event("map.load");assertTrue(cached.isNull("pending"));assertEquals(1,cached.getJSONObject("frame").getJSONObject("state").getJSONObject("map").getJSONArray("tiles").length())
   AppSdk.sync(c,JSONObject().put("action","permissions").put("id","org.example.referencemap").put("grants",JSONArray()))
   assertThrows(IllegalStateException::class.java){component()}
  } finally {
   SdkHttpService.cancel("live-osm-world")
   AppSdk.sync(c,JSONObject().put("action","close").put("session",session))
  }
 }
}
