package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AuthenticStoreTest{
 private val account=AuthenticAccount(issuer="RFC Demo",account="demo@example.com",secret=AuthenticOtp.encode("12345678901234567890".toByteArray()))
 private fun store():AuthenticStore{val c=ApplicationProvider.getApplicationContext<Application>();File(c.filesDir,"authentic.bin").delete();return AuthenticStore(c)}
 @Test fun encryptedStoreDoesNotExposeSeedsAndWrongPasswordNeverReplacesData(){val s=store();s.create("correct-password".toCharArray());s.save(listOf(account));val bytes=s.bytes();assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(account.secret));assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(account.account));s.close();assertTrue(runCatching{s.unlock("wrong-password".toCharArray())}.isFailure);assertArrayEquals(bytes,s.bytes());assertEquals(account,s.unlock("correct-password".toCharArray()).single());s.close();assertTrue(runCatching{s.save(emptyList())}.isFailure)}
 @Test fun portableBackupsAuthenticateCompletelyAndMergeIsExplicit(){val s=store();s.create("backup-password".toCharArray());s.save(listOf(account));val backup=s.bytes();assertEquals(account,s.inspectBackup(backup,"backup-password".toCharArray()).single());for(corrupt in listOf(backup.copyOf(backup.size-8),backup.copyOf().also{it[it.lastIndex]=(it.last().toInt()xor 1).toByte()})){assertTrue(runCatching{s.inspectBackup(corrupt,"backup-password".toCharArray())}.isFailure)};assertArrayEquals(backup,s.bytes());s.erase();assertEquals(account,s.restoreNew(backup,"backup-password".toCharArray()).single());assertTrue(runCatching{s.restoreNew(backup,"backup-password".toCharArray())}.isFailure)}
 @Test fun passwordChangeKeepsOldBackupsReadableOnlyWithTheirOriginalPassword(){val s=store();s.create("old-password".toCharArray());s.save(listOf(account));val old=s.bytes();s.changePassword("old-password".toCharArray(),"new-password".toCharArray(),listOf(account));s.close();assertTrue(runCatching{s.unlock("old-password".toCharArray())}.isFailure);assertEquals(account,s.unlock("new-password".toCharArray()).single());assertEquals(account,s.inspectBackup(old,"old-password".toCharArray()).single());assertTrue(runCatching{s.inspectBackup(old,"new-password".toCharArray())}.isFailure)}
 @Test fun countersPersistAndDifferentAppEnvelopesAreRejected(){val s=store();s.create("counter-password".toCharArray());s.save(listOf(account.copy(type="hotp",counter=42)));s.close();assertEquals(42L,s.unlock("counter-password".toCharArray()).single().counter);val(h,k)=FolderVaultCrypto.create("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","other-password".toCharArray());val out=java.io.ByteArrayOutputStream();try{FolderVaultCrypto.seal(out,h,k){it.write("{}".toByteArray())}}finally{k.fill(0)};assertTrue(runCatching{s.inspectBackup(out.toByteArray(),"other-password".toCharArray())}.isFailure)}
 @Test fun recoveryEnrollmentResetAndPasswordChangeKeepAccountsAndAuthenticateAllAnswers(){
  val s=store();val choices=listOf(1,2,0);fun answers()=listOf("pizza","music","first car").map{it.toCharArray()}
  s.createWithRecovery("first-password".toCharArray(),choices,answers());assertEquals(choices,s.recoveryQuestions());s.unlock("first-password".toCharArray());s.save(listOf(account));s.close()
  val bytes=s.bytes();assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("first car"))
  assertTrue(runCatching{s.resetPassword(listOf("pizza","wrong","first car").map{it.toCharArray()},"replacement".toCharArray())}.isFailure);assertArrayEquals(bytes,s.bytes())
  s.resetPassword(answers(),"replacement".toCharArray());assertTrue(runCatching{s.unlock("first-password".toCharArray())}.isFailure);assertEquals(account,s.unlock("replacement".toCharArray()).single())
  s.changePassword("replacement".toCharArray(),"third-password".toCharArray(),listOf(account));s.close();s.resetPassword(answers(),"fourth-password".toCharArray());assertEquals(account,s.unlock("fourth-password".toCharArray()).single())
 }
 @Test fun legacyEnrollmentPreservesAccountsAndOldBackups(){
  val s=store();s.create("legacy-password".toCharArray());s.save(listOf(account));val old=s.bytes();assertNull(s.recoveryQuestions());val rows=s.unlock("legacy-password".toCharArray())
  s.enrollRecovery(listOf(0,0,0),listOf("snack","movie","car").map{it.toCharArray()},rows);assertEquals(account,s.unlock("legacy-password".toCharArray()).single());assertEquals(account,s.inspectBackup(old,"legacy-password".toCharArray()).single())
 }

}
