package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class FilesRoutingTest {
 private fun await(condition:()->Boolean){val end=System.nanoTime()+5_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10)};assertTrue("SDK operation did not finish",condition())}
 @Test(timeout=20000) fun tappingDocumentResolvesBytesAndClosedFilesModelReopensWithoutBusyOverlay()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
  val id=FileSelectionSession(c).use{s->s.start();s.importSaved(listOf("documents"),java.io.File(c.cacheDir,"handoff.txt").apply{writeText("File handoff fixture")},"Handoff.txt","text/plain").getString("id")}
  val model=SdkAppModel(c);model.open("app.onklav.files");assertNull(model.error)
  model.action("entry:documents");await{model.frame.child("state").optString("folder")=="documents"&&!model.busy}
  model.action("entry:$id");await{model.pending?.optString("service")=="library.open"&&!model.busy}
  val p=model.pending!!;val descriptor=model.files(p,"resolve");assertEquals("text/plain",descriptor.getString("mime"));assertEquals("File handoff fixture",java.io.File(descriptor.getString("path")).readText());assertEquals(id,descriptor.getString("id"));model.reply(p)
  var closed=false;model.close{closed=true};await{closed&&!model.busy};model.open("app.onklav.files");assertFalse(model.busy);assertNull(model.error)
 }
}
