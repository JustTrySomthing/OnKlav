package app.onklav.home

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.io.File
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class VaultTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun reset(){listOf("vault.bin","vault.bin.new","vault.bin.bak").forEach{File(c.filesDir,it).delete()}}
 @Test fun passwordsEncryptionFormattingReopenAndChangePassword(){
  reset();val store=VaultStore(c);val original=store.create("unique long password".toCharArray())
  val text=SpannableStringBuilder("Secret password 9527");text.setSpan(StyleSpan(Typeface.BOLD),0,6,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
  val doc=WriteDocument.capture(original.id,"Vault",text);store.save(doc);store.close()
  val bytes=File(c.filesDir,"vault.bin").readBytes();assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Secret password"));assertFalse(File(c.filesDir,"write/documents/${doc.id}.json").exists())
  assertTrue(runCatching{store.unlock("wrong password".toCharArray())}.isFailure)
  assertEquals(doc.text,store.unlock("unique long password".toCharArray()).text)
  assertEquals(doc.marks,store.unlock("unique long password".toCharArray()).marks)
  store.changePassword("unique long password".toCharArray(),"another long password".toCharArray(),doc);store.close()
  assertTrue(runCatching{store.unlock("unique long password".toCharArray())}.isFailure)
  assertEquals(doc.text,store.unlock("another long password".toCharArray()).text);store.close()
 }
 @Test fun tamperAndTruncationDoNotPublishAnyPlaintext(){
  reset();val store=VaultStore(c);store.create("unique long password".toCharArray());store.close();val f=File(c.filesDir,"vault.bin");val intact=f.readBytes()
  val corrupt=intact.clone();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte();f.writeBytes(corrupt)
  assertTrue(runCatching{store.unlock("unique long password".toCharArray())}.isFailure)
  f.writeBytes(intact.copyOf(intact.size-10));assertTrue(runCatching{store.unlock("unique long password".toCharArray())}.isFailure)
  f.writeBytes(intact);assertEquals("",store.unlock("unique long password".toCharArray()).text);store.close()
 }
 @Test fun lockedStoreRejectsSavesAndFailedOversizeSaveKeepsLastGoodDocument(){
  reset();val store=VaultStore(c);val original=store.create("unique long password".toCharArray());store.save(original.copy(text="Keep this"))
  assertTrue(runCatching{store.save(original.copy(text="x".repeat(VaultStore.MAX_DOCUMENT)))}.isFailure)
  store.close();assertTrue(runCatching{store.save(original)}.isFailure)
  assertEquals("Keep this",store.unlock("unique long password".toCharArray()).text);store.close()
 }
 @Test fun encryptedDocumentIsPortableWithoutDeviceKeysAndKeepsImageAndLinkMarks(){
  reset();val store=VaultStore(c);val initial=store.create("unique long password".toCharArray())
  val image="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jq1sAAAAASUVORK5CYII="
  val doc=initial.copy(text="Private link \uFFFC",marks=listOf(WriteMark(0,7,"link","https://example.com"),WriteMark(13,14,"image",image)))
  store.save(doc);store.close()
  val destination=File(c.cacheDir,"portable-vault").apply{mkdirs()};File(c.filesDir,"vault.bin").copyTo(File(destination,"vault.bin"),overwrite=true)
  val other=object:android.content.ContextWrapper(c){override fun getFilesDir()=destination}
  val restored=VaultStore(other);val read=restored.unlock("unique long password".toCharArray());assertEquals(doc.text,read.text);assertEquals(doc.marks,read.marks);restored.close()
  assertEquals(listOf("vault.bin"),destination.listFiles()!!.map{it.name});destination.deleteRecursively()
 }
 @Test fun realEncryptedVaultRoundTripsThroughNativeAppDocumentsBackup(){
  fun fresh(name:String):android.content.Context{val root=java.nio.file.Files.createTempDirectory(c.cacheDir.toPath(),name).toFile();return object:android.content.ContextWrapper(c){override fun getFilesDir()=root}}
  val source=fresh("vault-source-");val target=fresh("vault-target-")
  val store=VaultStore(source);val initial=store.create("unique long password".toCharArray());store.save(initial.copy(text="Backup private content"));store.close()
  fun call(context:android.content.Context,operation:String,sha:String="")=AppSdk.sync(context,org.json.JSONObject().put("action","library_backup").put("kind","documents").put("operation",operation).put("sha256",sha)) as org.json.JSONObject
  call(source,"export")
  java.util.zip.ZipFile(File(source.filesDir,"library-backup-export.zip")).use{zip->val encrypted=zip.getInputStream(zip.getEntry("vault/document.bin")).readBytes();assertArrayEquals(File(source.filesDir,"vault.bin").readBytes(),encrypted);assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("Backup private content"))}
  File(source.filesDir,"library-backup-export.zip").copyTo(File(target.filesDir,"library-backup-import.zip"))
  val review=call(target,"inspect");call(target,"restore",review.getString("sha256"))
  val restored=VaultStore(target);assertEquals("Backup private content",restored.unlock("unique long password".toCharArray()).text);restored.close()
  source.filesDir.deleteRecursively();target.filesDir.deleteRecursively()
 }

}
