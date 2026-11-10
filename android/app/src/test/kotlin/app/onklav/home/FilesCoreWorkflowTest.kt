package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class FilesCoreWorkflowTest {
 @Test(timeout=90000) fun nativeFoldersCopyMoveZipSearchAndTrashKeepDataAndRejectCycles(){
  val c=ApplicationProvider.getApplicationContext<Application>();runBlocking{AppSdk.bootstrap(c)}
  val temp=File(c.cacheDir,"native-workflow.txt").apply{writeText("real native bytes")}
  FileSelectionSession(c).use{s->s.start()
   val seed=s.importSaved(listOf("documents"),temp,"Original.txt","text/plain")
   s.cancelPending("");s.navigate(listOf("documents"))
   fun folder(name:String):String{s.event("new.folder");s.event("name",name);s.event("name.save");return s.state.nodes("items").single{it.optString("name")==name}.getString("id")}
   val work=folder("Workflow");val destination=folder("Destination")
   s.event("options:"+seed.getString("id"));s.event("copy");s.event("entry:$work");s.event("paste.here")
   val copy=s.state.nodes("items").single{it.optString("name")=="Original.txt"}.getString("id")
   s.event("options:$copy");s.event("rename");s.event("name","Copy.txt");s.event("name.save")
   s.event("options:$copy");s.event("compress");val zip=s.state.nodes("items").single{it.optString("mime")=="application/zip"}
   val descriptor=s.choose(zip);java.util.zip.ZipFile(descriptor.getString("path")).use{z->assertEquals("real native bytes",z.getInputStream(z.getEntry("Copy.txt")).bufferedReader().readText())}
   s.event("options:$copy");s.event("move");s.event("entry:documents");s.event("entry:$destination");s.event("destination.accept")
   val moved=s.state.nodes("items").single{it.optString("id")==copy};assertEquals("real native bytes",File(s.choose(moved).getString("path")).readText())
   s.navigate(listOf("documents"));s.event("options:$work");s.event("move");s.event("entry:documents");s.event("entry:$work")
   assertTrue(runCatching{s.event("destination.accept")}.isFailure);s.event("menu.close");s.event("destination.cancel")
   s.navigate(listOf("documents"));s.event("query","Destination");assertEquals(1,s.state.optInt("total"));s.event("query","")
   s.event("options:$destination");s.event("trash");s.event("confirm.accept");s.navigate(listOf("trash"));assertTrue(s.state.nodes("items").any{it.optString("id")==destination})
   s.event("restore.all");s.navigate(listOf("documents",destination));assertEquals("real native bytes",File(s.choose(s.state.nodes("items").single{it.optString("id")==copy}).getString("path")).readText())
   s.navigate(listOf("documents"));s.event("options:$work");s.event("trash");s.event("confirm.accept");s.navigate(listOf("trash"));s.event("empty.trash");s.event("confirm.accept");assertEquals(0,s.state.optInt("total"))
   s.navigate(listOf("documents"));assertTrue(s.state.nodes("items").any{it.optString("id")==seed.getString("id")});assertTrue(s.state.nodes("items").any{it.optString("id")==destination})
  }
 }
}
