package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.NativeCore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class FolderVaultHostTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private class Recovery:FolderVaultRecovery{
  val keys=mutableMapOf<String,ByteArray>()
  override fun wrap(folder:String,bytes:ByteArray)=JSONObject().put("token",UUID.randomUUID().toString().also{keys[it]=bytes.copyOf()})
  override fun unwrap(folder:String,value:JSONObject)=keys[value.getString("token")]?.copyOf()?:error("Recovery unavailable")
  override fun erase(folder:String,value:JSONObject){keys.remove(value.getString("token"))?.fill(0)}
 }
 private fun createFolder():String=FileSelectionSession(c).use{s->s.start();s.event("entry:documents");s.event("new.folder");s.event("name","Private");s.event("name.save");s.state.nodes("items").first{it.getString("name")=="Private"}.getString("id")}
 private fun import(folder:String):String{val f=File(c.cacheDir,"vault-test.txt").apply{writeText("This text must be encrypted")};return FileSelectionSession(c).use{s->s.start();s.importSaved(listOf("documents",folder),f,"secret.txt","text/plain").getString("id")}}
 private fun status(id:String)=AppSdk.folderVault(c,"status",id)
 @Test fun protectUnlockChangePasswordAndRemoveRetainFilesAndRevokeSelectedLinks()=runBlocking{
  AppSdk.bootstrap(c);val folder=createFolder();val item=import(folder);val raw=File(c.filesDir,"onklav-content/objects/$item");val text=raw.readText();val recovery=Recovery();val engine=FolderVaultEngine(c,recovery,{})
  val uri=FileSelectionStore.selected(c,JSONObject().put("path",raw.path).put("id",item).put("name","secret.txt").put("mime","text/plain"),listOf("documents",folder))
  val pending=FileSelectionStore.save(c,listOf("documents",folder),"later.txt","text/plain")
  engine.protect(folder,"folder password".toCharArray());assertTrue(status(folder).getBoolean("locked"));assertFalse(raw.exists());assertTrue(recovery.keys.isEmpty())
  val archive=File(c.filesDir,"onklav-content/vaults/$folder.bin");assertFalse(archive.readBytes().toString(Charsets.ISO_8859_1).contains(text));assertFalse(File(c.filesDir,"onklav-content/index.json").readText().contains("secret.txt"))
  assertThrows(Exception::class.java){FileSelectionStore.read(c,uri)}
  val stale=JSONObject().put("state",JSONObject().put("folder","").put("items",org.json.JSONArray().put(JSONObject().put("id",item).put("name","secret.txt"))).put("selected",JSONObject().put("id",item))).put("body",org.json.JSONArray().put(JSONObject().put("id","entry:$item").put("text","secret.txt"))).put("menu_open",true)
  val masked=FolderVaultFrames.sanitize(c,stale);assertEquals(0,masked.getJSONArray("body").length());assertEquals(0,masked.child("state").getJSONArray("items").length());assertFalse(masked.getBoolean("menu_open"))
  var failed=false;try{engine.unlock(folder,"wrong password".toCharArray())}catch(e:Exception){failed=true};assertTrue(failed);assertFalse(raw.exists())
  val originalArchive=archive.readBytes();archive.writeBytes(originalArchive.copyOf().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()})
  failed=false;try{engine.unlock(folder,"folder password".toCharArray())}catch(e:Exception){failed=true};assertTrue(failed);assertFalse(raw.exists());assertTrue(status(folder).getBoolean("locked"));archive.writeBytes(originalArchive)
  val h=FolderVaultCrypto.readHeader(originalArchive.inputStream());val k=FolderVaultCrypto.unlock(h,"folder password".toCharArray());val bomb=java.io.ByteArrayOutputStream()
  FolderVaultCrypto.seal(bomb,h,k){out->java.util.zip.ZipOutputStream(out).use{zip->zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"));zip.write((" ".repeat(900001)+"{}").toByteArray());zip.closeEntry()}};k.fill(0);archive.writeBytes(bomb.toByteArray())
  failed=false;try{engine.unlock(folder,"folder password".toCharArray())}catch(e:Exception){failed=true};assertTrue(failed);assertFalse(raw.exists());archive.writeBytes(originalArchive)
  engine.unlock(folder,"folder password".toCharArray());assertEquals(text,raw.readText());assertFalse(status(folder).getBoolean("locked"));assertEquals(item,FileSelectionStore.read(c,uri).getString("entry"))
  assertThrows(Exception::class.java){FileSelectionStore.save(c,listOf("documents",folder),"pending.txt","text/plain")}
  assertThrows(Exception::class.java){FileSelectionStore.writable(c,pending)}
  engine.changePassword(folder,"folder password".toCharArray(),"changed password".toCharArray());assertTrue(status(folder).getBoolean("locked"))
  failed=false;try{engine.unlock(folder,"folder password".toCharArray())}catch(e:Exception){failed=true};assertTrue(failed)
  engine.unlock(folder,"changed password".toCharArray());engine.removeProtection(folder,"changed password".toCharArray());assertFalse(status(folder).getBoolean("encrypted"));assertFalse(archive.exists());assertEquals(text,raw.readText());assertTrue(recovery.keys.isEmpty())
 }
 @Test fun crashRecoverySealsLatestWorkingEditsAndSurvivesMissingRecoveryKey()=runBlocking{
  AppSdk.bootstrap(c);val folder=createFolder();val item=import(folder);val raw=File(c.filesDir,"onklav-content/objects/$item");val recovery=Recovery();val engine=FolderVaultEngine(c,recovery,{})
  engine.protect(folder,"folder password".toCharArray());engine.unlock(folder,"folder password".toCharArray())
  // Same-length in-place changes model a successfully committed latest editor revision.
  val edited="X".repeat(raw.length().toInt());raw.writeText(edited)
  AppSdk.folderVault(c,"close_reads",folder)
  assertTrue(FolderVaultEngine(c,recovery,{}).recoverAndLock().isEmpty());assertFalse(raw.exists())
  val reopened=FolderVaultEngine(c,recovery,{});reopened.unlock(folder,"folder password".toCharArray());assertEquals(edited,raw.readText())
  recovery.keys.clear();AppSdk.folderVault(c,"close_reads",folder);val afterCrash=FolderVaultEngine(c,recovery,{})
  assertEquals(listOf(folder),afterCrash.recoverAndLock());assertTrue(status(folder).getBoolean("locked"));assertEquals(edited,raw.readText())
  afterCrash.unlock(folder,"folder password".toCharArray());assertEquals(edited,raw.readText());afterCrash.lock(folder);assertFalse(raw.exists())
 }
 @Test fun migratedWriteAndLegacyCopiesAreEncryptedAndRestoredTogether()=runBlocking{
  val id=UUID.randomUUID().toString().replace("-","");val note=JSONObject().put("id",id).put("title","Personal note").put("text","Secret original").put("updated",1)
  assertTrue(JSONObject(NativeCore.dispatch(c.filesDir.path,JSONObject().put("op","save").put("note",note).toString())).getBoolean("ok"))
  AppSdk.bootstrap(c);val folder=createFolder();val uuid=AppSdk.writeMigration(c,"linked",id).getString("id")
  val shared=File(c.filesDir,"onklav-content/objects/$id");val private=File(c.filesDir,"write/documents/$uuid.json");val legacy=File(c.filesDir,"notes/$id.json")
  FileSelectionSession(c).use{s->s.start();s.event("entry:documents");val write=s.state.nodes("items").first{it.optString("name")=="Write"}.getString("id");s.event("entry:$write");s.event("options:$id");s.event("move");s.event("entry:documents");s.event("entry:$folder");s.event("destination.accept")}
  val engine=FolderVaultEngine(c,Recovery(),{});engine.protect(folder,"folder password".toCharArray());assertFalse(private.exists());assertFalse(legacy.exists());assertFalse(shared.exists());assertFalse(WriteFiles.list(c).any{it.id==uuid});assertThrows(Exception::class.java){WriteFiles.read(c,uuid)}
  engine.unlock(folder,"folder password".toCharArray());assertEquals("Secret original",WriteFiles.read(c,uuid).text);assertTrue(legacy.exists())
  WriteFiles.save(c,WriteFiles.read(c,uuid).copy(text="Latest edits"));engine.lock(folder);engine.unlock(folder,"folder password".toCharArray());assertEquals("Latest edits",WriteFiles.read(c,uuid).text)
 }
}
