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
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class BundledDefaultsTest{
 private suspend fun grants(c:Application):Set<String>{val row=AppSdk.list(c).first{it.child("manifest").optString("id")=="app.onklav.youtube"};val a=row.getJSONArray("grants");return (0 until a.length()).map{a.getString(it)}.toSet()}
 @Test fun bundledUpgradeAddsNewCapabilitiesAndKeepsOlderUserRevocations(){
  val previous=JSONObject().put("manifest",JSONObject().put("capabilities",JSONArray(listOf("storage.private","network.web","links.external")))).put("grants",JSONArray(listOf("network.web","links.external")))
  assertEquals(setOf("network.web","files.manage"),bundledUpgradeGrants(previous,setOf("storage.private","network.web","files.manage")))
 }
 @Test fun existingYoutubeDownloadsAreRepairedOnceAndCanSaveWithoutSettings()=runBlocking<Unit>{
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c);val defaults=c.getSharedPreferences("bundled-app-default-grants-v1",0)
  AppSdk.call(c,JSONObject().put("action","permissions").put("id","app.onklav.youtube").put("grants",JSONArray(listOf("storage.private","network.web"))))
  check(defaults.edit().putBoolean("app.onklav.youtube",true).remove(YOUTUBE_READY_KEY).commit());AppSdk.bootstrap(c)
  assertTrue(grants(c).containsAll(YOUTUBE_DOWNLOAD_CAPS));assertFalse(grants(c).contains("links.external"));assertTrue(defaults.getBoolean(YOUTUBE_READY_KEY,false))
  val model=SdkAppModel(c);model.open("app.onklav.youtube");assertNull(model.error);model.webComponent("youtube.page")
  File(c.filesDir,"content-import.tmp").writeBytes(byteArrayOf(1,2,3));val saved=model.saveVideoFile("youtube.page","Clip.mp4","video/mp4","video");assertTrue(saved.has("id"))
  AppSdk.call(c,JSONObject().put("action","permissions").put("id","app.onklav.youtube").put("grants",JSONArray(listOf("storage.private","network.web"))));AppSdk.bootstrap(c)
  assertFalse(grants(c).contains("files.manage"));AppSdk.enableYoutubeDownloads(c);model.reopenDiscardingDraft();assertNull(model.error);model.webComponent("youtube.page");assertTrue(grants(c).containsAll(YOUTUBE_DOWNLOAD_CAPS))
 }
 @Test fun freshBuiltinHasDownloadAccessAndRemovedAppStaysRemoved()=runBlocking<Unit>{
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c);assertTrue(grants(c).containsAll(YOUTUBE_DOWNLOAD_CAPS));AppSdk.call(c,JSONObject().put("action","remove").put("id","app.onklav.youtube").put("delete_data",false));val defaults=c.getSharedPreferences("bundled-app-default-grants-v1",0);defaults.edit().remove(YOUTUBE_READY_KEY).commit();AppSdk.bootstrap(c);assertFalse(AppSdk.list(c).first{it.child("manifest").optString("id")=="app.onklav.youtube"}.optBoolean("installed"));assertTrue(runCatching{AppSdk.enableYoutubeDownloads(c)}.isFailure)
 }
}
