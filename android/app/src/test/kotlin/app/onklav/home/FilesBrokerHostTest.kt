package app.onklav.home

import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract as Docs
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class FilesBrokerHostTest {
 private val c:Context get()=ApplicationProvider.getApplicationContext()
 class Documents:android.provider.DocumentsProvider(){
  var fileName="Example.txt";var deleted=false;var queries=0
  private val columns=arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_MIME_TYPE,Docs.Document.COLUMN_FLAGS,Docs.Document.COLUMN_SIZE)
  override fun onCreate()=true
  override fun queryRoots(projection:Array<out String>?)=MatrixCursor(arrayOf("root_id","document_id","title")).apply{addRow(arrayOf("root","root","Android Test Folder"))}
  override fun queryDocument(id:String,projection:Array<out String>?):Cursor{queries++;return MatrixCursor(columns).apply{if(id=="root")addRow(arrayOf(id,"Android Test Folder",Docs.Document.MIME_TYPE_DIR,Docs.Document.FLAG_DIR_SUPPORTS_CREATE,0))else if(!deleted&&id=="root:file")addRow(arrayOf(id,fileName,"text/plain",Docs.Document.FLAG_SUPPORTS_DELETE or Docs.Document.FLAG_SUPPORTS_RENAME,13))}}
  override fun queryChildDocuments(parent:String,projection:Array<out String>?,sort:String?):Cursor{queries++;return MatrixCursor(columns).apply{if(parent=="root"&&!deleted)addRow(arrayOf("root:file",fileName,"text/plain",Docs.Document.FLAG_SUPPORTS_DELETE or Docs.Document.FLAG_SUPPORTS_RENAME,13))}}
  override fun isChildDocument(parent:String,id:String)=parent=="root"&&id.startsWith("root:")
  override fun openDocument(id:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor{check(id=="root:file"&&!deleted);val file=File(context!!.cacheDir,"external-fixture").apply{writeText("External text")};return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)}
  override fun renameDocument(id:String,name:String):String{check(id=="root:file");fileName=name;return id}
  override fun deleteDocument(id:String){check(id=="root:file");deleted=true}
 }
 private fun provider():Documents{val p=Documents();p.attachInfo(c,ProviderInfo().apply{authority="test.files";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"});ShadowContentResolver.registerProviderInternal("test.files",p);return p}
 @Test fun androidFoldersExposeOpaqueTokensAndSupportRenameAndExplicitDelete(){
  val p=provider();val repo=ExternalFiles(c,"test.example.files");val tree=Docs.buildTreeDocumentUri("test.files","root");val connected=repo.connect(tree){};val root=connected.getString("id");assertTrue(root.matches(Regex("[a-f0-9]{32}")))
  val listed=repo.run(JSONObject().put("operation","list").put("folder",root)){};val row=listed.getJSONArray("items").getJSONObject(0);assertFalse(row.has("uri"));assertFalse(row.has("tree"));val id=row.getString("id")
  repo.run(JSONObject().put("operation","rename").put("id",id).put("name","Renamed.txt")){};assertEquals("Renamed.txt",p.fileName)
  assertEquals("External text",repo.run(JSONObject().put("operation","info").put("id",id)){}.getString("preview"))
  repo.run(JSONObject().put("operation","trash").put("id",id)){};assertTrue(p.deleted)
  repo.run(JSONObject().put("operation","disconnect").put("id",root)){};assertEquals(9,repo.run(JSONObject().put("operation","list").put("folder","external")){}.getJSONArray("items").length())
 }
 @Test fun revokedSdkAuthorizationAndUnknownTokensCannotQueryAndroidFiles(){
  val p=provider();val repo=ExternalFiles(c,"test.example.files");val tree=Docs.buildTreeDocumentUri("test.files","root");val root=repo.connect(tree){}.getString("id");val before=p.queries
  assertThrows(SecurityException::class.java){repo.run(JSONObject().put("operation","list").put("folder",root)){throw SecurityException("Revoked")}}
  assertEquals(before,p.queries)
  assertThrows(IllegalStateException::class.java){repo.run(JSONObject().put("operation","open").put("id","../escape")){} }
  assertThrows(IllegalStateException::class.java){ExternalFiles(c,"different.example.app").run(JSONObject().put("operation","list").put("folder",root)){} }
 }
 @Test fun fileExportsAreReadOnlyAndCannotExposeArbitraryPrivatePaths(){
  val dir=File(c.filesDir,"onklav-content/objects").apply{mkdirs()};val file=File(dir,"a".repeat(32)).apply{writeText("one selected file")}
  val uri=FilesOpenProvider.stage(c,JSONObject().put("path",file.path).put("name","Sample.txt").put("mime","text/plain")){}
  val provider=FilesOpenProvider().apply{attachInfo(c,ProviderInfo().apply{authority=c.packageName+".files-open";exported=false;grantUriPermissions=true})}
  provider.openFile(uri,"r").use{assertEquals("one selected file",FileInputStream(it.fileDescriptor).bufferedReader().readText())}
  assertThrows(FileNotFoundException::class.java){provider.openFile(uri,"rw")}
  val private=File(c.filesDir,"unrelated-secret").apply{writeText("private")}
  assertThrows(IllegalArgumentException::class.java){FilesOpenProvider.stage(c,JSONObject().put("path",private.path).put("name","Secret").put("mime","text/plain")){} }
  assertThrows(FileNotFoundException::class.java){provider.openFile(Uri.parse("content://${c.packageName}.files-open/../secret"),"r")}
 }
 @Test fun deletingPrivateDataClearsOnlyThatAppsFolderConnections(){
  val id="test.example.files";val own=c.getSharedPreferences("external-files-${AppSdk.desktopId(id)}",0);val other=c.getSharedPreferences("external-files-${AppSdk.desktopId("test.other.app")}",0)
  own.edit().putString("fixture","token").commit();other.edit().putString("fixture","other token").commit()
  val content=File(c.filesDir,"onklav-content/keep.txt").apply{parentFile!!.mkdirs();writeText("shared content")}
  var cleared:String?=null
  SdkNativeData.prepare(c,id,true);SdkNativeData.removed(c,id,true,clearAccounts={_,_->}){_,owner->cleared=owner};assertEquals(id,cleared)
  assertTrue(own.all.isEmpty());assertEquals("other token",other.getString("fixture",null));assertEquals("shared content",content.readText())
 }
 @Test fun selectedImportsStreamAndStopWhenAuthorizationIsRevoked()=runBlocking{
  provider();val uri=Docs.buildDocumentUri("test.files","root:file");var checks=0
  FilesBroker.stage(c,uri){checks++};assertTrue(checks>=2);assertEquals("External text",File(c.filesDir,"content-import.tmp").readText())
  assertThrows(SecurityException::class.java){FilesBroker.copyBounded(ByteArrayInputStream(ByteArray(2*1024*1024)),ByteArrayOutputStream()){throw SecurityException("Revoked")}}
  assertEquals("_safe_name",FilesBroker.cleanName("../safe/name"))
 }
 @Test fun sharingAnAndroidFolderCreatesAReadableZipAndPreservesOriginals(){
  val docs=provider();val repo=ExternalFiles(c,"test.example.files");val tree=Docs.buildTreeDocumentUri("test.files","root");val root=repo.connect(tree){}.getString("id")
  val archive=repo.run(JSONObject().put("operation","share").put("id",root)){};val file=File(archive.getString("path"));assertTrue(file.isFile)
  java.util.zip.ZipFile(file).use{zip->val item=zip.getEntry("Android Test Folder/Example.txt");assertNotNull(item);assertEquals("External text",zip.getInputStream(item).bufferedReader().readText())}
  val uri=FilesOpenProvider.stage(c,archive){};assertFalse(file.exists());assertFalse(docs.deleted)
  val provider=FilesOpenProvider().apply{attachInfo(c,ProviderInfo().apply{authority=c.packageName+".files-open";exported=false;grantUriPermissions=true})}
  provider.openFile(uri,"r").use{assertTrue(it.statSize>0)}
 }

}
