package app.onklav.home
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MapsAppHostTest{
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private fun fixture(n:String)=JSONObject(File(requireNotNull(System.getProperty("onklav.project.root")),"docs/fixtures/maps-app/$n.json").readText())
 private fun route(mode:String="Drive")=MapsParser.route(fixture(when(mode){"Walk"->"route-foot";"Cycle"->"route-bike";else->"route"}),MapsPlace("start","Start",36.16,-86.78),MapsPlace("end","Destination",36.17,-86.79),mode)
 @Test fun projectionGesturesAndDatelineFitStayFinite(){
  for(z in listOf(2,8,19))for(p in listOf(36.16 to -86.78,0.0 to 179.9,-70.0 to -179.9)){val xy=SdkMapTiles.project(p.first,p.second,z);val back=MapsMath.unproject(xy.first,xy.second,z);assertEquals(p.first,back.first,.00001);assertEquals(p.second,back.second,.00001)}
  val east=MapsMath.move(MapsCamera(0.0,179.9,3.0),-100.0,0.0);assertTrue(east.lon< -150);val zoom=MapsMath.move(east,0.0,0.0,2.0);assertEquals(4.0,zoom.zoom,0.0)
  val fit=MapsMath.fit(listOf(10.0 to 179.8,10.1 to -179.8));assertTrue(kotlin.math.abs(fit.lon)>179);assertTrue(fit.zoom>5)
  assertEquals("1.0 mi",MapsMath.distanceText(1609.344,false));assertEquals("1.6 km",MapsMath.distanceText(1609.344,true))
 }
 @Test fun offlineZipCityAndCoordinatesAreUsefulWithoutNetwork(){
  val zip=MapsOffline.search(c,"37201").single();assertTrue(zip.name.contains("Nashville"));assertEquals(36.17,zip.lat,.04)
  assertTrue(MapsOffline.search(c,"00501-1234").single().name.contains("00501"));assertTrue(MapsOffline.search(c,"Nashville TN").isNotEmpty());assertTrue(MapsOffline.search(c,"Paris France").isNotEmpty());assertEquals(36.16,MapsOffline.search(c,"36.16,-86.78").single().lat,0.0);assertTrue(MapsOffline.search(c,"91,999").isEmpty())
 }
 @Test fun realProviderPlacesAndAllTravelModesParse(){
  val places=MapsParser.photon(fixture("nearby"));assertTrue(places.size>=10);assertEquals(places.size,places.map{it.id}.distinct().size);val at=MapsPlace("center","Area",36.16,-86.78);assertTrue(places.any{MapsMath.distance(it,at)<5000})
  for(mode in listOf("Drive","Walk","Cycle")){val r=route(mode);assertTrue(r.distance>500);assertTrue(r.seconds>60);assertTrue(r.points.size>2);assertTrue(r.steps.size>2);assertEquals(mode,r.mode);assertTrue(r.steps.last().text.contains("Arrive"));assertEquals(r,MapsRoute.parse(r.json()))}
  assertThrows(IllegalArgumentException::class.java){MapsParser.route(JSONObject("{\"code\":\"NoRoute\"}"),at,at,"Drive")}
  val special=fixture("route");val maneuver=special.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps").getJSONObject(0).getJSONObject("maneuver");maneuver.put("type","roundabout");maneuver.remove("exit")
  assertTrue(MapsParser.route(special,at,at,"Drive").steps.first().text.startsWith("Enter the roundabout"));maneuver.put("exit",3);assertTrue(MapsParser.route(special,at,at,"Drive").steps.first().text.contains("exit 3"));maneuver.put("type","exit roundabout");assertTrue(MapsParser.route(special,at,at,"Drive").steps.first().text.startsWith("Exit the roundabout"))
 }
 @Test fun savedNamesRoutesRecoveryAndGrantsUseTheSignedApp(){
  runBlocking{AppSdk.bootstrap(c)};val launch=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.maps")) as JSONObject;assertEquals("Maps",launch.getJSONObject("frame").getString("title"));assertTrue(mapsAccess(c));assertTrue(mapsAccess(c,"network.http"));assertTrue(mapsAccess(c,"location.foreground"))
  val home=MapsPlace("home","Home",36.16,-86.78,"Nashville, Tennessee");val r=route().copy(name="Weekend trip");MapsStore.save(c,MapsSaved(listOf(home),listOf(r)));assertEquals("Weekend trip",MapsStore.read(c).routes.single().name)
  MapsStore.save(c,MapsSaved(listOf(home.copy(name="Workshop")),listOf(r.copy(name="To town"))));val saved=MapsStore.read(c);assertEquals("Workshop",saved.places.single().name);assertEquals(r.points,saved.routes.single().points);assertEquals("To town",saved.routes.single().name)
  val file=File(c.filesDir,"maps/saved.json");assertTrue(file.renameTo(File(file.path+".bak")));assertEquals("Workshop",MapsStore.read(c).places.single().name)
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.maps").put("grants",JSONArray()));assertFalse(mapsAccess(c));assertThrows(IllegalStateException::class.java){MapsStore.save(c,MapsSaved())}
 }
 @Test fun requestsStayOnDeclaredProvidersAndUseExplicitQueries(){
  val url=MapsNet.searchUrl("New York, NY & café");assertTrue(url.startsWith("https://photon.komoot.io/api/"));assertTrue(url.contains("q="));val at=MapsPlace("center","Area",36.16,-86.78);assertTrue(MapsNet.nearbyUrl("Gas",at,10).contains("include=osm.amenity.fuel"));assertTrue(MapsNet.routeUrl(at,at,"Walk").contains("routed-foot"));assertTrue(MapsNet.routeUrl(at,at,"Cycle").contains("routed-bike"))
  val op=JSONObject().put("owner",JSONObject().put("id","app.onklav.maps")).put("origins",JSONArray(MapsNet.origins)).put("args",JSONObject().put("url",url));assertNotNull(SdkHttpPolicy.request(op));op.getJSONObject("args").put("url","https://example.com/search");assertThrows(IllegalArgumentException::class.java){SdkHttpPolicy.request(op)}
 }
}
