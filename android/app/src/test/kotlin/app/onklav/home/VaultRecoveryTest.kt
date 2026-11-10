package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class VaultRecoveryTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private val selected=listOf(2,1,0)
 private fun answers()=listOf("Brussels sprouts","The Long Book","1979 Blue Ford").map{it.toCharArray()}
 @Before fun clear(){listOf("vault.bin","vault.bin.new","vault.bin.bak").forEach{File(app.filesDir,it).delete()}}
 private fun waitFor(model:VaultModel){val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(model.busy&&System.nanoTime()<until){shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10)};assertFalse(model.busy);assertNull(model.error)}
 @Test fun allThreeAnswersResetPasswordWithoutLosingDataAndOldPasswordStopsWorking(){
  val s=VaultStore(app);s.createWithRecovery("original password".toCharArray(),selected,answers());val doc=s.unlock("original password".toCharArray()).copy(text="Private information");s.save(doc);s.close()
  assertEquals(selected,s.recoveryQuestions());val before=File(app.filesDir,"vault.bin").readBytes()
  val wrong=answers().toMutableList();wrong[1]="wrong book".toCharArray();assertThrows(Exception::class.java){s.resetPassword(wrong,"replacement password".toCharArray())};assertArrayEquals(before,File(app.filesDir,"vault.bin").readBytes())
  s.resetPassword(listOf("  BRUSSELS   SPROUTS ","the long book","1979 blue ford").map{it.toCharArray()},"replacement password".toCharArray())
  assertThrows(Exception::class.java){s.unlock("original password".toCharArray())};assertEquals(doc,s.unlock("replacement password".toCharArray()));s.close()
  val raw=File(app.filesDir,"vault.bin").readBytes().toString(Charsets.ISO_8859_1);for(secret in listOf("Private information","original password","replacement password","Brussels sprouts","The Long Book","1979 Blue Ford"))assertFalse(raw.contains(secret))
  assertEquals(listOf("vault.bin"),app.filesDir.listFiles()!!.filter{it.name.startsWith("vault")}.map{it.name})
 }
 @Test fun legacyVaultRequiresPasswordBeforeRecoveryCanBeAddedAndPasswordChangesKeepRecovery(){
  val s=VaultStore(app);val doc=s.create("legacy password".toCharArray()).copy(text="Keep existing content");s.save(doc);s.close();assertNull(s.recoveryQuestions())
  assertThrows(Exception::class.java){s.completeRecovery(selected,answers(),doc)}
  assertEquals(doc,s.unlock("legacy password".toCharArray()));s.completeRecovery(selected,answers(),doc)
  assertEquals(doc,s.unlock("legacy password".toCharArray()));s.changePassword("legacy password".toCharArray(),"changed password".toCharArray(),doc);s.close()
  s.resetPassword(answers(),"recovered password".toCharArray());assertEquals(doc,s.unlock("recovered password".toCharArray()));s.close()
 }
 @Test fun incompleteOrInvalidSetupNeverCreatesAVaultAndCompleteRelocks(){
  val model=VaultModel(app);model.open("setup password".toCharArray(),true);assertTrue(model.showQuestions);assertNull(model.document);assertFalse(model.exists);assertFalse(VaultStore(app).exists())
  model.lock();val resumed=VaultModel(app);assertFalse(resumed.exists);resumed.open("different password".toCharArray(),true)
  resumed.completeSetup(selected,answers());waitFor(resumed);assertTrue(resumed.exists);assertTrue(resumed.locked);assertFalse(resumed.showQuestions);assertNull(resumed.document)
  resumed.resetPassword(answers(),"reset after setup".toCharArray());waitFor(resumed);assertTrue(resumed.locked);assertNull(resumed.document)
  resumed.open("reset after setup".toCharArray(),false);waitFor(resumed);assertFalse(resumed.locked);resumed.lock()
 }
 @Test fun invalidAnswersAndCorruptionCannotReplaceTheEncryptedVault(){
  val s=VaultStore(app);assertThrows(Exception::class.java){s.createWithRecovery("setup password".toCharArray(),selected,listOf("a".toCharArray()," ".toCharArray(),"b".toCharArray()))};assertFalse(s.exists())
  s.createWithRecovery("setup password".toCharArray(),selected,answers());val f=File(app.filesDir,"vault.bin");val raw=f.readBytes();raw[raw.lastIndex]=(raw.last().toInt() xor 1).toByte();f.writeBytes(raw)
  assertThrows(Exception::class.java){s.resetPassword(answers(),"replacement password".toCharArray())};assertArrayEquals(raw,f.readBytes());assertThrows(Exception::class.java){s.unlock("setup password".toCharArray())}
 }
 @Test fun passwordOnlySetupRestartsWithoutOldPasswordAndCompletedVaultCannotBeReplaced(){
  val store=VaultStore(app);store.create("forgotten old password".toCharArray());store.close();val before=File(app.filesDir,"vault.bin").readBytes()
  val model=VaultModel(app);assertFalse(model.exists);assertNull(model.recoveryChoices);assertTrue(model.locked)
  model.open("brand new password".toCharArray(),true);assertTrue(model.showQuestions);assertArrayEquals(before,File(app.filesDir,"vault.bin").readBytes())
  model.lock();val retry=VaultModel(app);assertFalse(retry.exists);retry.open("replacement password".toCharArray(),true);retry.completeSetup(selected,answers());waitFor(retry)
  assertTrue(retry.exists);assertTrue(retry.locked);assertNull(retry.document);assertEquals(selected,store.recoveryQuestions())
  assertThrows(Exception::class.java){store.unlock("forgotten old password".toCharArray())};assertEquals("",store.unlock("replacement password".toCharArray()).text);store.close()
  val complete=File(app.filesDir,"vault.bin").readBytes();assertThrows(Exception::class.java){store.createWithRecovery("overwrite attempt".toCharArray(),selected,answers())};assertArrayEquals(complete,File(app.filesDir,"vault.bin").readBytes());assertTrue(VaultModel(app).exists)
 }
 @Test fun recoveryTravelsInTheExistingOpaqueBackupPayload(){
  val s=VaultStore(app);s.createWithRecovery("portable password".toCharArray(),selected,answers());s.save(s.unlock("portable password".toCharArray()).copy(text="Portable secret"));s.close()
  fun request(context:android.content.Context,action:String,sha:String="")=AppSdk.sync(context,org.json.JSONObject().put("action","library_backup").put("kind","documents").put("operation",action).put("sha256",sha)) as org.json.JSONObject
  request(app,"export")
  val folder=File(app.cacheDir,"vault-recovery-restore").apply{mkdirs()};val other=object:android.content.ContextWrapper(app){override fun getFilesDir()=folder}
  File(folder,"vault.bin").delete();File(app.filesDir,"library-backup-export.zip").copyTo(File(folder,"library-backup-import.zip"),overwrite=true)
  val review=request(other,"inspect");request(other,"restore",review.getString("sha256"));val restored=VaultStore(other);assertEquals(selected,restored.recoveryQuestions());restored.resetPassword(answers(),"restored password".toCharArray());assertEquals("Portable secret",restored.unlock("restored password".toCharArray()).text);restored.close();folder.deleteRecursively()
 }
}
