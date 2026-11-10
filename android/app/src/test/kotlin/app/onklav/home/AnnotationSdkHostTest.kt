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
import java.io.File

/** Archive fixture only. Production has no Annotation package asset or install route. */
internal fun installLegacyAnnotation(c:Context){
 val source=File(requireNotNull(System.getProperty("onklav.project.root")),"sdk/packages/Annotation.onklav-a")
 File(c.filesDir,"app-install.zip").writeBytes(source.readBytes())
 val review=AppSdk.sync(c,JSONObject().put("action","inspect")) as JSONObject
 AppSdk.sync(c,JSONObject().put("action","install").put("hash",review.getString("hash")).put("grants",review.child("manifest").getJSONArray("capabilities")))
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AnnotationSdkHostTest{
 @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 @Test fun freshHostIncludesImageEditorAndNoStandaloneAnnotation()=runBlocking{
  AppSdk.bootstrap(c)
  assertFalse(AppSdk.bundledApps.containsKey("annotation"));assertFalse(platformApps.any{it.id=="annotation"})
  assertFalse(c.assets.list("sdk")!!.contains("Annotation.onklav-a"));assertTrue(c.assets.list("sdk")!!.contains("Photo.onklav-a"))
  val apps=AppSdk.list(c);assertFalse(apps.any{it.child("manifest").optString("id")=="app.onklav.annotation"&&it.optBoolean("installed")})
  assertTrue(apps.any{it.child("manifest").optString("id")=="app.onklav.photo"&&it.optBoolean("installed")});assertTrue(photoAccess(c))
 }
 @Test fun upgradeRetiresOldAppRevokesItsSessionAndKeepsAllSavedImageData()=runBlocking{
  AppSdk.bootstrap(c);installLegacyAnnotation(c)
  val opened=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.annotation")) as JSONObject
  val session=opened.getString("session")
  val scope=File(c.filesDir,"app-sdk/data/app.onklav.annotation").apply{mkdirs()}
  val saved=File(scope,"retained-work.txt").apply{writeText("Retain existing editable image data")}
  val before=saved.readBytes()
  AppSdk.epoch.value++;AppSdk.bootstrap(c)
  val retired=AppSdk.list(c).single{it.child("manifest").optString("id")=="app.onklav.annotation"}
  assertFalse(retired.getBoolean("installed"));assertEquals(0,retired.getJSONArray("grants").length());assertArrayEquals(before,saved.readBytes())
  assertThrows(IllegalStateException::class.java){AppSdk.sync(c,JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action","fit")))}
  AppSdk.bootstrap(c);assertArrayEquals(before,saved.readBytes());assertTrue(photoAccess(c))
 }
}
