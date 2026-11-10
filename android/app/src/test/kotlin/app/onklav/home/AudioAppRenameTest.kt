package app.onklav.home

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AudioAppRenameTest {
 @Test fun signedAppNameAndFrameChangeWithoutChangingIdentityOrDefaultGrants(){
  val app=ApplicationProvider.getApplicationContext<android.app.Application>();runBlocking{AppSdk.bootstrap(app)}
  val rows=AppSdk.sync(app,JSONObject().put("action","list")) as JSONArray
  val row=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.optJSONObject("manifest")?.optString("id")=="app.onklav.audioeditor"}
  assertEquals("Audio Edit",platformApps.first{it.id=="audioeditor"}.title)
  val manifest=row.getJSONObject("manifest");assertEquals("Audio Editor",manifest.getString("name"));assertEquals(4,manifest.getInt("version"))
  val grants=row.getJSONArray("grants");val names=(0 until grants.length()).map{grants.getString(it)}
  assertTrue("storage.private" in names);assertTrue("audio.playback" in names)
  val opened=AppSdk.sync(app,JSONObject().put("action","launch").put("id","app.onklav.audioeditor")) as JSONObject
  assertEquals("Audio Editor",opened.getJSONObject("frame").getString("title"))
 }
}
