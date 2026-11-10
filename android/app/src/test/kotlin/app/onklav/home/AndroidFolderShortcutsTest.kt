package app.onklav.home
import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.json.JSONObject
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AndroidFilesTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Before fun access(){shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)}
 @Test fun categoryHomeIsDirectAndContainsNoConnectionChoice(){val rows=AndroidFilesAccess.roots();assertEquals(listOf("Photos","Images","Audio","Video","Documents","Downloads","APK","Other"),rows.map{it.getString("name")});assertTrue(rows.all{it.getBoolean("folder")})}
 @Test fun attachmentAndSaveTypesOpenTheirMatchingFolders(){assertEquals("images",initialFilesFolder(listOf("image/*")));assertEquals("audio",initialFilesFolder(listOf("audio/mpeg")));assertEquals("video",initialFilesFolder(listOf("video/*")));assertEquals("",initialFilesFolder(listOf("*/*")));assertEquals("documents",initialFilesFolder(listOf("application/pdf"),true))}
 @Test fun androidFilesCannotEscapeSharedStorageOrEnterPrivateAppDirectories(){val root=File(app.cacheDir,"shared-fixture").apply{mkdirs()};for(path in listOf("../secret","/etc/passwd","Android/data/app/secret","Android/obb/app/file",".hidden/file"))assertThrows(Exception::class.java){AndroidFilesAccess.checked(app,path,root)};assertEquals(File(root,"DCIM/photo.jpg"),AndroidFilesAccess.checked(app,"DCIM/photo.jpg",root));assertEquals(File(root,"Android/media/app/photo.jpg"),AndroidFilesAccess.checked(app,"Android/media/app/photo.jpg",root));val outside=File(app.cacheDir,"other-private").apply{mkdirs()};java.nio.file.Files.createSymbolicLink(File(root,"link").toPath(),outside.toPath());assertThrows(Exception::class.java){AndroidFilesAccess.checked(app,"link/secret",root)}}
 @Test fun revokingAndroidAccessBlocksEvenKnownFiles(){shadowOf(app).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);assertFalse(AndroidFilesAccess.allowed(app));assertThrows(IllegalStateException::class.java){AndroidFilesAccess.checked(app,"DCIM/photo.jpg",app.cacheDir)}}
 @Test fun supportedEmailProvidersHaveNoRetiredPlatformPresets(){for(provider in listOf("Yahoo","Gmail","Other")){val a=MailAccount.preset(provider,"owner@example.org");assertEquals(provider,a.provider)};for(provider in listOf("GMX","Outlook","iCloud"))assertThrows(IllegalArgumentException::class.java){MailAccount.preset(provider)}}

 @Test fun directAndroidFilesCanBeRenamedCopiedCompressedAndNeverOverwrite(){
  val root=AndroidFilesAccess.root().apply{mkdirs()};val source=File(root,"Shared fixture").apply{mkdirs()};File(source,"sound.mp3").writeText("audio fixture")
  val entries=JSONObject();val browser=AndroidSharedFiles(app)
  fun call(operation:String,folder:String="",id:String="",parent:String="",name:String="")=requireNotNull(browser.run(JSONObject().put("operation",operation).put("folder",folder).put("id",id).put("parent",parent).put("name",name),entries){})
  try{
   val home=call("list","android-other").getJSONArray("items");val folder=(0 until home.length()).map{home.getJSONObject(it)}.first{it.getString("name")==source.name}.getString("id")
   val row=call("list",folder).getJSONArray("items").getJSONObject(0);assertFalse(row.has("uri"));assertFalse(row.has("android_path"));val id=row.getString("id")
   call("rename",id=id,name="recording.mp3");assertTrue(File(source,"recording.mp3").isFile)
   call("folder",parent=folder,name="Copies");val rows=call("list",folder).getJSONArray("items");val destination=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("name")=="Copies"}.getString("id")
   call("copy",id=id,parent=destination);assertEquals("audio fixture",File(source,"Copies/recording.mp3").readText());assertThrows(Exception::class.java){call("copy",id=id,parent=destination)}
   call("compress",id=id);java.util.zip.ZipFile(File(source,"recording.mp3.zip")).use{assertEquals("recording.mp3",it.entries().nextElement().name)}
   shadowOf(app).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);assertThrows(Exception::class.java){call("open",id=id)}
  }finally{source.deleteRecursively()}
 }

 @Test fun thumbnailReadsDoNotOverwriteAConcurrentDirectoryListing(){
  val root=AndroidFilesAccess.root().apply{mkdirs()};val file=File(root,"preview-fixture.jpg").apply{writeText("fixture")};val appId="test.android.previews";val repo=ExternalFiles(app,appId)
  try{
   val rows=repo.run(JSONObject().put("operation","list").put("folder","android-other")){}.getJSONArray("items");val id=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("name")==file.name}.getString("id")
   val prefs=app.getSharedPreferences("external-files-"+AppSdk.desktopId(appId),0);var calls=0
   repo.run(JSONObject().put("operation","open").put("id",id)){if(++calls==2){val latest=JSONObject(prefs.getString("entries","{}")!!).put("new-directory-entry",JSONObject().put("name","Arrived later"));prefs.edit().putString("entries",latest.toString()).commit()}}
   assertTrue(JSONObject(prefs.getString("entries","{}")!!).has("new-directory-entry"))
  }finally{file.delete()}
 }
}
