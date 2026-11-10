package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class WalkiePresentationTest {
 @Test(timeout=20000) fun selectedRecipientShowsCompactDialAndOfflineRecordingIsStillAvailable()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c);val model=SdkAppModel(c);model.open("app.onklav.walkie");assertNull(model.error)
  fun body()=model.frame.getJSONArray("body").let{a->(0 until a.length()).map{a.getJSONObject(it)}}
  assertTrue(body().any{it.optString("id")=="peer"})
  model.chooseWalkieRecipient("15550100101");assertFalse(body().any{it.optString("id")=="peer"});assertTrue(body().any{it.optString("kind")=="voice_dial"});assertTrue(body().single{it.optString("id")=="connect"}.optBoolean("primary"));assertFalse(body().any{it.optString("kind")=="voice_capture"})
  model.action("record.toggle");val deadline=System.nanoTime()+5_000_000_000
  while(body().none{it.optString("kind")=="voice_capture"}&&System.nanoTime()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)}
  assertEquals("panel",model.frame.optString("presentation"));assertTrue(model.frame.getJSONArray("actions").toString().contains("record.toggle"));assertFalse(body().any{it.optString("id")=="record.toggle"});assertNull(model.error);assertTrue(body().any{it.optString("kind")=="voice_capture"});assertTrue(body().any{it.optString("id")=="quick.auto_play"});assertTrue(body().any{it.optString("id")=="quick.auto_send"})
 }
}
