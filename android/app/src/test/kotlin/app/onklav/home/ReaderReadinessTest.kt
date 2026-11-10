package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class ReaderReadinessTest {
 @Test fun upgradeRepairsOldReaderGrantsOnceAndHonorsLaterRevocation()=runBlocking<Unit>{
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
  val defaults=c.getSharedPreferences("bundled-app-default-grants-v1",0)
  for(id in listOf("app.onklav.audiobooks","app.onklav.ebooks")){
   AppSdk.call(c,JSONObject().put("action","permissions").put("id",id).put("grants",JSONArray().put("storage.private")))
   defaults.edit().putBoolean(id,true).remove("reader-ready-v2:$id").commit()
  }
  AppSdk.bootstrap(c)
  for(id in listOf("app.onklav.audiobooks","app.onklav.ebooks")){
   val row=AppSdk.list(c).first{it.getJSONObject("manifest").getString("id")==id}
   val caps=row.getJSONObject("manifest").getJSONArray("capabilities");val grants=row.getJSONArray("grants")
   assertEquals((0 until caps.length()).map{caps.getString(it)}.toSet(),(0 until grants.length()).map{grants.getString(it)}.toSet())
   assertTrue(defaults.getBoolean("reader-ready-v2:$id",false))
  }
  AppSdk.call(c,JSONObject().put("action","permissions").put("id","app.onklav.audiobooks").put("grants",JSONArray().put("storage.private")))
  AppSdk.bootstrap(c)
  val row=AppSdk.list(c).first{it.getJSONObject("manifest").getString("id")=="app.onklav.audiobooks"}
  assertEquals(listOf("storage.private"),(0 until row.getJSONArray("grants").length()).map{row.getJSONArray("grants").getString(it)})
 }
}
