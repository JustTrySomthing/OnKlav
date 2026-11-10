package app.onklav.home
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class FileExportRepairTest {
 @get:Rule val keys=EmptyKeyStoreRule()
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 @Before fun setup()=runBlocking{
  AppSdk.bootstrap(c)
  val authority=c.packageName+".file-selection";val provider=FileSelectionProvider();provider.attachInfo(c,android.content.pm.ProviderInfo().apply{this.authority=authority;applicationInfo=c.applicationInfo});ShadowContentResolver.registerProviderInternal(authority,provider)
 }
 private suspend fun pump(job:Deferred<Unit>){val end=System.nanoTime()+15_000_000_000L;while(!job.isCompleted&&System.nanoTime()<end){shadowOf(FileSelectionStore.handler.looper).idle();shadowOf(android.os.Looper.getMainLooper()).idle();delay(10)};check(job.isCompleted){"Export did not finish"};job.await()}
 @Test fun actualFilesSaveWaitsForFilingAndKeepsFailedCopiesInRecovery()=runBlocking{
  val good=FileSelectionStore.save(c,listOf("documents"),"audit-complete.txt","text/plain")
  pump(async(Dispatchers.IO){writeFiledExport(c,good){it.write("complete".toByteArray())}})
  assertEquals("saved",FileSelectionStore.read(c,good).getString("state"))
  val bad=FileSelectionStore.save(c,listOf("documents"),"audit-partial.txt","text/plain")
  val failure=async(Dispatchers.IO){runCatching{writeFiledExport(c,bad){it.write("fragment".toByteArray());throw java.io.IOException("Injected copy failure")}}}
  while(!failure.isCompleted){shadowOf(FileSelectionStore.handler.looper).idle();delay(10)};assertTrue(failure.await().isFailure)
  repeat(10){shadowOf(FileSelectionStore.handler.looper).idle();delay(10)}
  assertEquals("recovery",FileSelectionStore.read(c,bad).getString("state"));assertFalse(FileSelectionStore.read(c,bad).has("path"))
 }
 @Test fun failedWriterCannotPublishThroughANormalCloseButOwnerRecoveryRemainsAvailable()=runBlocking{
  val uri=FileSelectionStore.save(c,listOf("documents"),"audit-recover-fragment.txt","text/plain")
  FileSelectionStore.writable(c,uri).writeText("retained fragment")
  FileSelectionStore.interrupted(c,uri);FileSelectionStore.completed(c,uri,null)
  assertEquals("recovery",FileSelectionStore.read(c,uri).getString("state"))
  FileSelectionStore.completed(c,uri,null,allowInterrupted=true)
  assertEquals("saved",FileSelectionStore.read(c,uri).getString("state"))
 }
 @Test fun backupAndCloudSizeChecksRejectBeforeOpeningAnInternalDestination(){
  val uri=FileSelectionStore.save(c,listOf("documents"),"large.zip","application/zip")
  preflightFileExport(c,uri,"application/zip",256L*1024*1024)
  assertThrows(IllegalArgumentException::class.java){preflightFileExport(c,uri,"application/zip",256L*1024*1024+1)}
  assertEquals("new",FileSelectionStore.read(c,uri).getString("state"))
  preflightFileExport(c,android.net.Uri.parse("content://connected-phone/document/large.zip"),"application/zip",512L*1024*1024)
 }
 @Test fun backupFilingRunsAfterTheSharedStagingMutexIsReleased()=runBlocking{
  val uri=FileSelectionStore.save(c,listOf("documents"),"audit-backup.json","application/json")
  pump(async(Dispatchers.IO){NotesBackup.export(c,uri);Unit})
  assertEquals("saved",FileSelectionStore.read(c,uri).getString("state"))
 }
}
