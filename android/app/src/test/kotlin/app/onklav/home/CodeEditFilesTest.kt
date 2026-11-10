package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CodeEditFilesTest{
 @get:Rule val keys=EmptyKeyStoreRule()
 @Test fun selectedFileReplacementPreservesOldVersionAndRefusesExternalChange(){
  val c=ApplicationProvider.getApplicationContext<Application>();runBlocking{AppSdk.bootstrap(c)}
  val file=File(c.cacheDir,"code-fixture.txt");file.writeText("let value = 1;\n")
  val row=FileSelectionSession(c).use{s->s.start();s.importSaved(listOf("documents"),file,"main.rs","text/plain")};val uri=FileSelectionSession(c).use{s->s.start();FileSelectionStore.selected(c,s.chooseAt(listOf("documents"),row),listOf("documents"))}
  val original=CodeEditFiles.read(c,uri);val saved=CodeEditFiles.replace(c,"test",uri,CodeEditFiles.digest(original),"let value = 2;\n".toByteArray());assertEquals("let value = 2;\n",CodeEditFiles.read(c,saved).toString(Charsets.UTF_8))
  assertTrue(runCatching{CodeEditFiles.replace(c,"test",saved,"incorrect","oops".toByteArray())}.isFailure);assertEquals("let value = 2;\n",CodeEditFiles.read(c,saved).toString(Charsets.UTF_8))
  FileSelectionSession(c).use{s->s.start();s.navigate(listOf("trash"));assertTrue(s.state.nodes("items").any{it.optString("name")=="main.rs"})}
 }
}
