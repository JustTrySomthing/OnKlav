package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkRadioCatalogTest {
 private fun station(name:String,url:String,country:String="US",source:String="Radio Browser")=SdkRadioCatalog.Station(UUID.nameUUIDFromBytes((name+url+country+source).toByteArray()).toString(),name,url,countrycode=country,language=if(country=="US")"english"else"",tags="jazz, music",source=source)
 @Test fun sameStreamsAndAlternateQualitiesMergeAcrossBothSourcesWithoutLosingAliases(){
  val a=station("Great Jazz (MP3 128 kbps)","https://stream.example/live")
  val b=station("Great Jazz [AAC 64 kbps]","https://another.example/hd")
  val c=station("Great Jazz","https://stream.example:443/live?utm_source=icecast","","Icecast")
  val rows=SdkRadioCatalog.merge(listOf(a,b,c));assertEquals(1,rows.size);assertEquals(a.id,rows[0].id);assertEquals(setOf(a.id,b.id,c.id),rows[0].aliases.toSet());assertEquals("US",rows[0].countrycode)
 }
 @Test fun duplicateBridgesMergeTransitivelyButDifferentCountriesAndGenericNamesStaySeparate(){
  val a=station("Northern Waves","https://first.example/live")
  val b=station("Other Name","https://second.example/live")
  val bridge=station("Northern Waves","https://second.example/live")
  assertEquals(1,SdkRadioCatalog.merge(listOf(a,b,bridge)).size)
  assertEquals(2,SdkRadioCatalog.merge(listOf(station("Regional Jazz","https://first.example/us"),station("Regional Jazz","https://second.example/gb","GB"))).size)
  assertEquals(2,SdkRadioCatalog.merge(listOf(station("Radio 1","https://first.example/one"),station("Radio 1","https://first.example/two","","Icecast"))).size)
 }
 @Test fun icecastExportRejectsMissingUnsafeAndUnsupportedStreamsAndDtds(){
  val xml="""<directory><entry><server_name>Great Jazz</server_name><server_type>audio/mpeg</server_type><listen_url>https://stream.example/live</listen_url><genre>Jazz</genre><bitrate>128</bitrate></entry><entry><server_name>Missing</server_name><server_type>audio/mpeg</server_type><listen_url/></entry><entry><server_name>Private</server_name><server_type>audio/mpeg</server_type><listen_url>https://127.0.0.1/live</listen_url></entry><entry><server_name>Insecure</server_name><server_type>audio/mpeg</server_type><listen_url>http://stream.example/live</listen_url></entry><entry><server_name>Ogg</server_name><server_type>audio/ogg</server_type><listen_url>https://stream.example/ogg</listen_url></entry></directory>"""
  val rows=SdkRadioCatalog.readIcecast(xml.byteInputStream()){};assertEquals(3,rows.size);assertFalse(rows.any{it.name=="Private"||it.name=="Missing"});assertEquals("Icecast",rows[0].source);assertEquals("Jazz",rows[0].tags);assertEquals(rows[0].id,SdkRadioCatalog.readIcecast(xml.byteInputStream()){}[0].id)
  assertThrows(Exception::class.java){SdkRadioCatalog.readIcecast("<!DOCTYPE directory [<!ENTITY x SYSTEM 'file:///secret'>]><directory>&x;</directory>".byteInputStream()){}}
 }
 @Test fun searchAndPagingCoverTheEntireUnifiedCatalogNotOnlyFirstThousand(){
  val rows=(0..1239).map{station("Station $it","https://stream.example/$it")}
  val later=SdkRadioCatalog.page(rows,JSONObject().put("offset",1200));assertEquals(1240,later.getInt("total"));assertEquals(20,later.getJSONArray("items").length());assertEquals(1220,later.getInt("next"));assertEquals(rows[1200].id,later.getJSONArray("items").getJSONObject(0).getString("id"));assertFalse(later.toString().contains("stream.example"))
  val found=SdkRadioCatalog.page(rows,JSONObject().put("query","Station 1239").put("country","US").put("language","english").put("tag","jazz"));assertEquals(1,found.getJSONArray("items").length())
 }
 @Test fun streamedBrowserParserSkipsExtraFieldsAndHonorsSafetyProjection(){
  val json="""[{"stationuuid":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","name":"Great Jazz","url_resolved":"https://stream.example/live","codec":"MP3","hls":0,"lastcheckok":1,"bitrate":128,"tracking":{"private":"ignored"}},{"stationuuid":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","name":"Unsafe","url_resolved":"http://stream.example/live","codec":"MP3","hls":0,"lastcheckok":1}]"""
  val rows=SdkRadioCatalog.readBrowser(json.byteInputStream()){};assertEquals(2,rows.size);assertEquals("Great Jazz",rows[0].name)
  assertThrows(Exception::class.java){SdkRadioCatalog.Limited(ByteArray(200).inputStream(),100).use{it.readBytes()}}
 }
 @Test fun bundledLiveCatalogAddsUniqueIcecastStationsAndSurvivesRefreshFailure()=runBlocking{
  val c=ApplicationProvider.getApplicationContext<Context>();var fetches=0
  val first=SdkRadioCatalog.get(c,false,{}){fetches++;error("Offline")};assertEquals(0,fetches);assertTrue(first.raw.size>37000);val browser=SdkRadioCatalog.merge(first.raw.filter{it.source=="Radio Browser"});val icecast=first.rows.count{it.source=="Icecast"};assertTrue(icecast>0);assertTrue(first.rows.size>browser.size)
  val keys=first.rows.map{SdkRadioCatalog.streamKey(it.url)};assertEquals(keys.size,keys.toSet().size)
  println("Merged catalog: raw=${first.raw.size}, browserUnique=${browser.size}, mergedUnique=${first.rows.size}, additionalIcecast=$icecast")
  val saved=first.rows.first{it.source=="Icecast"};SdkRadioCatalog.remember(c,listOf(saved)){}
  val snapshot=SdkRadioCatalog::class.java.getDeclaredField("snapshot").apply{isAccessible=true};snapshot.set(SdkRadioCatalog,null)
  val history=SdkRadioCatalog::class.java.getDeclaredField("history").apply{isAccessible=true};history.set(SdkRadioCatalog,null)
  assertEquals(saved.url,SdkRadioCatalog.find(c,saved.id){}?.url)
  val reloaded=SdkRadioCatalog.get(c,false,{}){error("Offline")};val fallback=SdkRadioCatalog.get(c,true,{}){fetches++;error("Offline")};assertTrue(fallback.partial);assertEquals(2,fetches);assertEquals(reloaded.rows.size,fallback.rows.size)
 }
}
