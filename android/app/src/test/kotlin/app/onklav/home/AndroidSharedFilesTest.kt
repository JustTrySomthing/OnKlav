package app.onklav.home
import android.Manifest
import android.app.Application
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AndroidSharedFilesTest {
 @Test fun androidTrashRestoresWithoutOverwritingAndImagesIncludeCameraPhotos(){
  val c=ApplicationProvider.getApplicationContext<Application>()
  Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
  // Robolectric starts with an empty MIME registry; Android supplies this mapping.
  Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg")
  val root=Environment.getExternalStorageDirectory();val image=File(root,"DCIM/Camera/test-photo.jpg").apply{parentFile!!.mkdirs();writeText("original")}
  val entries=JSONObject();val files=AndroidSharedFiles(c)
  fun run(op:String,folder:String="",id:String="",query:String="")=requireNotNull(files.run(JSONObject().put("operation",op).put("folder",folder).put("id",id).put("query",query),entries){})
  try{
   val roots=run("list","external").getJSONArray("items");assertEquals(9,roots.length())
   val imageRows=run("list","android-images").getJSONArray("items");assertTrue("root=$root canonical=${AndroidFilesAccess.root()} file=${image.exists()} mime=${AndroidFilesAccess.mime(image)} rows=$imageRows",imageRows.length()>0);val row=(0 until imageRows.length()).map{imageRows.getJSONObject(it)}.single{it.getString("name")==image.name}
   run("trash",id=row.getString("id"));assertFalse(image.exists())
   val bin=run("list","android-trash");assertTrue(bin.getBoolean("trash"));val trashed=bin.getJSONArray("items").getJSONObject(0);assertTrue(trashed.getBoolean("trash"));assertEquals("test-photo.jpg",trashed.getString("name"))
   assertEquals(1,run("list","android-trash",query="test-photo").getJSONArray("items").length())
   val internal=run("list","android-other").getJSONArray("items");assertFalse((0 until internal.length()).any{internal.getJSONObject(it).optString("name")=="OnKlav Trash"})
   image.writeText("new file")
   assertTrue(runCatching{run("restore",id=trashed.getString("id"))}.isFailure);assertEquals("new file",image.readText())
   image.delete();run("restore",id=trashed.getString("id"));assertEquals("original",image.readText());assertEquals(0,run("list","android-trash").getJSONArray("items").length())
  }finally{image.delete();File(root,"OnKlav Trash").deleteRecursively()}
 }
 @Test fun failedCopyKeepsAnUnrelatedFileCreatedAtTheDestination(){
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val root=Environment.getExternalStorageDirectory();val source=File(root,"Download/copy-race.txt").apply{parentFile!!.mkdirs();writeText("source")};val target=File(root,"copy-race.txt")
  val entries=JSONObject();val files=AndroidSharedFiles(c)
  try{
   val list=requireNotNull(files.run(JSONObject().put("operation","list").put("folder","android-downloads"),entries){})
   val rows=list.getJSONArray("items");val row=(0 until rows.length()).map{rows.getJSONObject(it)}.single{it.optString("name")==source.name}
   var checks=0
   val result=runCatching{files.run(JSONObject().put("operation","copy").put("id",row.getString("id")).put("parent","android-other"),entries){if(++checks==2)target.writeText("unrelated")}}
   assertTrue(result.isFailure);assertEquals("unrelated",target.readText());assertEquals("source",source.readText())
  }finally{source.delete();target.delete()}
 }
 @Test fun purgeRejectsLinksBeforeDeletingAnyFolderContents(){
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val root=Environment.getExternalStorageDirectory();val source=File(root,"Download/linked-folder").apply{mkdirs()};File(source,"keep.txt").writeText("keep")
  val outside=File(c.cacheDir,"outside-delete-test").apply{mkdirs()};val protected=File(outside,"protected.txt").apply{writeText("protected")}
  java.nio.file.Files.createSymbolicLink(File(source,"link").toPath(),outside.toPath())
  val entries=JSONObject();val files=AndroidSharedFiles(c);var moved:File?=null
  fun op(name:String,folder:String="",id:String="")=requireNotNull(files.run(JSONObject().put("operation",name).put("folder",folder).put("id",id),entries){})
  try{
   val rows=op("list","android-downloads").getJSONArray("items");val row=(0 until rows.length()).map{rows.getJSONObject(it)}.single{it.optString("name")==source.name}
   op("trash",id=row.getString("id"));val trash=op("list","android-trash").getJSONArray("items").getJSONObject(0);moved=File(root,entries.getJSONObject(trash.getString("id")).getString("android_path"))
   assertTrue(runCatching{op("purge",id=trash.getString("id"))}.isFailure)
   assertEquals("protected",protected.readText());assertEquals("keep",File(moved,"keep.txt").readText())
  }finally{File(moved?:source,"link").delete();(moved?:source).deleteRecursively();outside.deleteRecursively();File(root,"OnKlav Trash").delete()}
 }

 @Test fun androidWorkflowPreservesBytesAcrossCopyMoveRenameZipAndTrashRecovery(){
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val root=AndroidFilesAccess.root();val download=File(root,"Download").apply{mkdirs()};val source=File(download,"Workflow").apply{mkdirs()};File(source,"nested").mkdir();File(source,"nested/hello.txt").writeText("original bytes")
  val entries=JSONObject();val files=AndroidSharedFiles(c)
  fun op(action:String,folder:String="",id:String="",parent:String="",name:String="")=requireNotNull(files.run(JSONObject().put("operation",action).put("folder",folder).put("id",id).put("parent",parent).put("name",name),entries){})
  fun find(folder:String,name:String):JSONObject{val rows=op("list",folder).getJSONArray("items");return (0 until rows.length()).map{rows.getJSONObject(it)}.single{it.optString("name")==name}}
  try{
   val item=find("android-downloads","Workflow")
   op("folder",parent="android-downloads",name="Copies");val copies=find("android-downloads","Copies")
   op("copy",id=item.getString("id"),parent=copies.getString("id"));assertEquals("original bytes",File(download,"Copies/Workflow/nested/hello.txt").readText())
   val copied=find(copies.getString("id"),"Workflow");op("move",id=copied.getString("id"),parent="android-other");assertEquals("original bytes",File(root,"Workflow/nested/hello.txt").readText());assertFalse(File(download,"Copies/Workflow").exists())
   op("rename",id=item.getString("id"),name="Renamed");val renamed=find("android-downloads","Renamed")
   op("compress",id=renamed.getString("id"));java.util.zip.ZipFile(File(download,"Renamed.zip")).use{zip->assertEquals("original bytes",zip.getInputStream(zip.getEntry("Renamed/nested/hello.txt")).bufferedReader().readText())}
   val shared=op("share",id=renamed.getString("id"));assertTrue(File(shared.getString("path")).isFile);File(shared.getString("path")).delete()
   File(download,"Renamed/recover.txt").writeText("recover")
   val recover=find(renamed.getString("id"),"recover.txt");op("trash",id=recover.getString("id"))
   File(download,"Renamed").renameTo(File(download,"Removed-parent"))
   val trash=find("android-trash","recover.txt");assertTrue(runCatching{op("open",id=trash.getString("id"))}.isFailure)
   op("restore",id=trash.getString("id"));assertEquals("recover",File(download,"recover.txt").readText())
   for(name in listOf("recover.txt","Renamed.zip"))op("trash",id=find("android-downloads",name).getString("id"))
   c.getSharedPreferences("android-trash",0).edit().clear().commit()
   val restored=op("restore_all","android-trash");assertEquals(2,restored.getInt("completed"));assertEquals("recover",File(download,"recover.txt").readText());assertTrue(File(download,"Renamed.zip").isFile)
   op("trash",id=find("android-downloads","recover.txt").getString("id"));assertEquals(1,op("empty","android-trash").getInt("completed"));assertEquals(0,op("list","android-trash").getJSONArray("items").length())
  }finally{for(f in listOf("Workflow","OnKlav Trash"))File(root,f).deleteRecursively();for(f in listOf("Workflow","Copies","Removed-parent","Renamed","Renamed.zip","recover.txt"))File(download,f).deleteRecursively()}
 }

 @Test fun standardAndAppManagedFoldersAreProtectedWhilePersonalFilesRemainEditable(){
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val root=AndroidFilesAccess.root();val music=File(root,"Music").apply{mkdirs()};File(music,"personal.mp3").writeText("song");val custom=File(root,"My folder").apply{mkdirs()}
  val entries=JSONObject();val files=AndroidSharedFiles(c)
  fun list(folder:String)=requireNotNull(files.run(JSONObject().put("operation","list").put("folder",folder),entries){}).getJSONArray("items")
  try{
   val rows=list("android-other");val standard=(0 until rows.length()).map{rows.getJSONObject(it)}.single{it.getString("name")=="Music"};assertTrue(standard.getBoolean("protected"));assertFalse(standard.getBoolean("editable"))
   for(op in listOf("rename","trash","move","compress"))assertTrue(op,runCatching{files.run(JSONObject().put("operation",op).put("id",standard.getString("id")).put("name","Changed").put("parent","android-downloads"),entries){}}.isFailure)
   val children=list(standard.getString("id"));assertFalse(children.getJSONObject(0).getBoolean("protected"))
   assertTrue(AndroidFilesAccess.protected("Android/media/com.amazon.test",true));assertTrue(AndroidFilesAccess.protected("com.amazon.test/cache",true));assertTrue(AndroidFilesAccess.protected("DCIM/Camera",true));assertFalse(AndroidFilesAccess.protected("Pictures/my-photo.jpg",false));assertFalse(AndroidFilesAccess.protected("My folder",true))
   assertTrue(music.isDirectory);assertEquals("song",File(music,"personal.mp3").readText())
  }finally{music.deleteRecursively();custom.deleteRecursively()}
 }

 @Test fun protectedAndroidMetadataCanBeReadButCannotBeWrittenOrDeletedThroughTheProvider(){
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
  val file=File(AndroidFilesAccess.root(),"Android/media/com.amazon.test/settings.json").apply{parentFile!!.mkdirs();writeText("metadata")}
  val provider=AndroidFilesProvider().apply{attachInfo(c,android.content.pm.ProviderInfo().apply{authority=c.packageName+".android-files";applicationInfo=c.applicationInfo})}
  val uri=AndroidFilesAccess.uri(c,"Android/media/com.amazon.test/settings.json")
  try{provider.openFile(uri,"r").use{assertEquals(8,it.statSize)};assertThrows(IllegalStateException::class.java){provider.openFile(uri,"wt")};assertThrows(IllegalStateException::class.java){provider.delete(uri,null,null)};assertEquals("metadata",file.readText())}finally{file.delete();file.parentFile!!.delete()}
 }

}
