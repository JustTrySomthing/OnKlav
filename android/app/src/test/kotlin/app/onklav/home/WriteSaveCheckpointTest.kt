package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @LooperMode(LooperMode.Mode.PAUSED)
class WriteSaveCheckpointTest {
 private fun waitFor(test:()->Boolean){val limit=System.nanoTime()+8_000_000_000;while(!test()&&System.nanoTime()<limit){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));Thread.sleep(10)};assertTrue("Operation completed",test())}
 @Test fun discardRestoresExplicitSaveDespiteAutosaveAndDoesNotDeleteTheDocument(){
  val app=ApplicationProvider.getApplicationContext<Application>();runBlocking{AppSdk.bootstrap(app)}
  val model=WriteModel(app);waitFor{model.initialized};model.open();waitFor{!model.busy&&model.status=="Saved"}
  val id=model.doc.id;model.change(model.doc.copy(title="Saved checkpoint",text="Keep this version"),false)
  var saved=false;model.saveNow(onSaved={saved=true});waitFor{saved}
  model.change(model.doc.copy(text="Autosaved draft to discard"),false);waitFor{model.status=="Saved"}
  assertEquals("Autosaved draft to discard",WriteFiles.read(app,id).text)
  var closed=false;model.discard{closed=true};waitFor{closed}
  assertEquals(id,model.doc.id);assertEquals("Keep this version",model.doc.text);assertEquals("Keep this version",WriteFiles.read(app,id).text)
  model.change(model.doc.copy(text="Recovered draft on reentry"),false);waitFor{model.status=="Saved"};model.checkpointOnOpen()
  model.change(model.doc.copy(text="Later edits"),false);waitFor{model.status=="Saved"};closed=false;model.discard{closed=true};waitFor{closed}
  assertEquals("Recovered draft on reentry",WriteFiles.read(app,id).text)
  model.flush();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));assertEquals("Recovered draft on reentry",WriteFiles.read(app,id).text)
 }
}
