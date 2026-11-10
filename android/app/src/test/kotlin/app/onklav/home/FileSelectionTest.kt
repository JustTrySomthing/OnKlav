package app.onklav.home

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavFileContracts
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class FileSelectionTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun bootstrap()=runBlocking{AppSdk.bootstrap(c)}
 private fun seed(name:String="Example.txt",text:String="Selected content"):JSONObject{
  val source=File(c.cacheDir,"picker-fixture").apply{writeText(text)}
  return FileSelectionSession(c).use{s->s.start();s.importSaved(listOf("documents"),source,name,"text/plain")}
 }
 @Test fun defaultContractsLaunchOnlyOurNonExportedHostPickerAndPreserveResults(){
  val contracts=listOf(OnKlavFileContracts.OpenDocument().createIntent(c,arrayOf("image/*")),OnKlavFileContracts.OpenMultipleDocuments().createIntent(c,arrayOf("*/*")),OnKlavFileContracts.CreateDocument("application/zip").createIntent(c,"Project.zip"),OnKlavFileContracts.OpenDocumentTree().createIntent(c,null),FileExportContract().createIntent(c,FileExportRequest("File.txt","text/plain")))
  for(i in contracts){assertEquals("app.onklav.home.FileBrowserActivity",i.component!!.className);assertEquals(c.packageName,i.component!!.packageName);assertNull(i.action)}
  assertEquals("save",contracts[2].getStringExtra("mode"));assertEquals("Project.zip",contracts[2].getStringExtra("name"))
  val first=Uri.parse("content://selection/one");val second=Uri.parse("content://selection/two");val data=Intent().setData(first).apply{clipData=ClipData.newRawUri("files",first).also{it.addItem(ClipData.Item(second))}}
  assertEquals(listOf(first,second),OnKlavFileContracts.OpenMultipleDocuments().parseResult(-1,data));assertTrue(OnKlavFileContracts.OpenMultipleDocuments().parseResult(0,data).isEmpty())
 }
 @Test fun actualSignedFilesControllerSelectsOneReadableFileWithoutCopyingOrExposingPaths(){
  bootstrap();val saved=seed();FileSelectionSession(c).use{s->s.start();s.navigate(listOf("documents"));val row=s.state.nodes("items").first{it.optString("id")==saved.getString("id")};val uri=FileSelectionStore.selected(c,s.choose(row));assertEquals(c.packageName+".file-selection",uri.authority)
   val provider=FileSelectionProvider().apply{attachInfo(c,ProviderInfo().apply{authority=c.packageName+".file-selection";exported=false})}
   provider.openFile(uri,"r").use{assertEquals("Selected content",FileInputStream(it.fileDescriptor).reader().readText())}
   assertThrows(IllegalStateException::class.java){provider.openFile(uri,"wt")}
  }
  assertThrows(IllegalStateException::class.java){FileSelectionStore.selected(c,JSONObject().put("path",File(c.filesDir,"private-secret").apply{writeText("private")}.path).put("name","Secret").put("mime","text/plain"))}
  assertThrows(IllegalArgumentException::class.java){FileSelectionStore.read(c,Uri.parse("content://${c.packageName}.file-selection/../private"))}
 }
 @Test fun saveCommitUsesRustImportAndUpdatesRealListingSizeWhileKeepingNamesUnique(){
  bootstrap();seed("Report.txt","Original kept")
  val uri=FileSelectionStore.save(c,listOf("documents"),"Report.txt","text/plain");val raw=FileSelectionStore.writable(c,uri);raw.writeText("New saved content");FileSelectionStore.completed(c,uri,null)
  val receipt=FileSelectionStore.read(c,uri);assertEquals("saved",receipt.getString("state"));assertEquals("Report (2).txt",receipt.getString("name"));assertFalse(raw.exists());assertEquals("New saved content",FileSelectionStore.file(c,receipt).readText())
  FileSelectionSession(c).use{s->s.start();s.navigate(listOf("documents"));val rows=s.state.nodes("items");assertEquals(2,rows.count{it.optString("name").startsWith("Report")});assertEquals(17,rows.first{it.getString("name")=="Report (2).txt"}.getInt("bytes"))}
  assertThrows(IllegalStateException::class.java){FileSelectionStore.writable(c,uri)}
 }
 @Test fun revokedAccessKeepsUnfiledSaveAndFolderSelectionUsesTheSharedBrowser(){
  bootstrap();val saved=seed();val dir=FileSelectionStore.folder(c,listOf("documents"),"Documents");val entries=FileSelectionStore.folderEntries(c,dir);assertEquals("Documents",entries.first);assertEquals(1,entries.second.size)
  val uri=FileSelectionStore.save(c,listOf("documents"),"Keep.txt","text/plain");val raw=FileSelectionStore.writable(c,uri);raw.writeText("Recover this")
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.files").put("grants",JSONArray()))
  FileSelectionStore.completed(c,uri,null);assertTrue(raw.exists());assertEquals("recovery",FileSelectionStore.read(c,uri).getString("state"));assertTrue(FileSelectionStore.recovered(c).any{it.optString("token")==uri.lastPathSegment})
 }
 @Test fun directAndroidDownloadsUseTheSharedBrowserAndRespectRemovedAndroidAccess(){
  bootstrap();org.robolectric.Shadows.shadowOf(c).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val file=File(AndroidFilesAccess.root(),"Download/Picker fixture.txt").apply{parentFile!!.mkdirs();writeText("Selected Android content")}
  try{FileSelectionSession(c).use{s->s.start();s.navigate(listOf("external","android-downloads"));val row=s.state.nodes("items").single{it.getString("name")==file.name};val uri=FileSelectionStore.selected(c,s.choose(row));assertEquals(c.packageName+".android-files",uri.authority);assertEquals("Selected Android content",c.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()})
   org.robolectric.Shadows.shadowOf(c).denyPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE)
   assertThrows(Exception::class.java){s.navigate(listOf("external","android-downloads"))}
  }}finally{file.delete()}
 }
 @Test fun filtersAndNamesRejectUnsupportedTypesWithoutHidingFolders(){
  assertTrue(filePickerAccepts(JSONObject().put("folder",true),listOf("image/*")))
  assertTrue(filePickerAccepts(JSONObject().put("name","Photo.png").put("mime","image/png"),listOf("image/*")))
  assertFalse(filePickerAccepts(JSONObject().put("name","Notes.txt").put("mime","text/plain"),listOf("image/*")))
  assertThrows(IllegalArgumentException::class.java){FileSelectionStore.save(c,listOf("documents"),"../file","text/plain")}
 }
 @Test fun oldAndroidLocationsAndFolderSelectionsRemainUsableAndTrashCannotBeAttached(){
  bootstrap();org.robolectric.Shadows.shadowOf(c).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  org.robolectric.Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg")
  val root=AndroidFilesAccess.root();val photo=File(root,"DCIM/Camera/old-location.jpg").apply{parentFile!!.mkdirs();writeText("photo")};val apk=File(root,"Download/old-location.apk").apply{parentFile!!.mkdirs();writeText("apk")}
  try{FileSelectionSession(c).use{s->
   s.start();s.navigate(listOf("external","android-photos"));assertTrue(s.state.nodes("items").any{it.optString("name")==photo.name})
   s.navigate(listOf("external","android-apk"));assertTrue(s.state.nodes("items").any{it.optString("name")==apk.name})
   val folder=FileSelectionStore.folder(c,listOf("external","android-photos"),"Photos");assertEquals(1,FileSelectionStore.folderEntries(c,folder).second.size)
   s.navigate(listOf("external","android-downloads"));val item=s.state.nodes("items").single{it.optString("name")==apk.name}
   ExternalFiles(c,"app.onklav.files").run(JSONObject().put("operation","trash").put("id",item.getString("id"))){}
   s.navigate(listOf("external","android-trash"));assertThrows(IllegalStateException::class.java){s.choose(s.state.nodes("items").first())}
   assertTrue(filePickerAccepts(JSONObject().put("name","fallback.jpg").put("mime","application/octet-stream"),listOf("image/*")))
  }}finally{photo.delete();apk.delete();File(root,"OnKlav Trash").deleteRecursively()}
 }

}
