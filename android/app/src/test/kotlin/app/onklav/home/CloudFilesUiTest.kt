package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudFilesUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var m:CloudModel
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;roots.filterNotNull().forEach{root->val v=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val d=v.rootView;d.forceLayout();d.measure(android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.heightPixels,android.view.View.MeasureSpec.AT_MOST));d.layout(0,0,d.measuredWidth,d.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}}}
 private fun waitReady(){compose.waitUntil(10000){pump();!m.busy&&m.loaded}}
 private fun click(text:String){compose.onAllNodesWithText(text,substring=false).onLast().performSemanticsAction(SemanticsActions.OnClick){it()};pump()}
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/cloud-files/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun prepare(){File(compose.activity.noBackupFilesDir,"cloud-files").deleteRecursively();compose.mainClock.autoAdvance=false;androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)}
 @After fun cleanup(){if(::m.isInitialized)m.stop();File(compose.activity.noBackupFilesDir,"cloud-files").deleteRecursively()}
 private fun empty(){m=CloudModel(compose.activity.application,CloudStore(compose.activity,MailTestKeys()));compose.activity.setContent{OnKlavTheme{Surface{CloudWorkspace({}, {},m)}}};waitReady()}
 @Test(timeout=45000) fun firstLaunchMakesSignInAndSignupClearAndHasProviderInstructions(){empty();compose.onNodeWithText("Sign in",substring=false).assertIsDisplayed();compose.onNodeWithText("Create a free account",substring=false).assertIsDisplayed();capture("welcome");click("Sign in");click("pCloud · US");click("Koofr");compose.onNodeWithText("App password",substring=false).assertExists();click("Sign-in instructions");compose.onNodeWithText("Koofr sign-in",substring=false).assertExists();compose.onNodeWithText("Open Koofr settings",substring=false).assertExists();click("Got it");capture("koofr-sign-in")}
 @Test(timeout=45000) @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallLargeTextSignInKeepsConnectAndHelpReachable(){empty();click("Sign in");compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy){it(0f,1200f)};pump();compose.onNodeWithText("Connect",substring=false).assertIsDisplayed();compose.onNodeWithText("Sign-in instructions",substring=false).assertIsDisplayed();capture("small-large-text")}
 @Test(timeout=45000) fun connectedBrowserSearchFolderUpAndCreationWork(){val keys=MailTestKeys();val store=CloudStore(compose.activity,keys);store.save(listOf(cloudAccount()));var created=false
  m=CloudModel(compose.activity.application,store){a->CloudDav(a){r->when(r.method){"MKCOL"->{created=true;cloudResponse(r,201)};else->{val rows=if(r.url.encodedPath=="/dav/")mutableListOf(cloudRow("/dav/","Root",true),cloudRow("/dav/Documents/","Documents",true),cloudRow("/dav/photo.jpg","Photo.jpg",size=8192)).apply{if(created)add(cloudRow("/dav/Projects/","Projects",true))}else mutableListOf(cloudRow("/dav/Documents/","Documents",true),cloudRow("/dav/Documents/readme.txt","Readme.txt"));cloudResponse(r,207,cloudXml(*rows.toTypedArray()))}}}}
  compose.activity.setContent{OnKlavTheme{Surface{CloudWorkspace({}, {},m)}}};waitReady();compose.onNodeWithText("Upload",substring=false).assertIsDisplayed();click("Documents");waitReady();compose.onNodeWithText("Readme.txt",substring=false).assertIsDisplayed();compose.onNodeWithContentDescription("Parent folder").performClick();pump();waitReady();compose.onNodeWithText("Search this folder").performTextInput("photo");pump();compose.onNodeWithText("Photo.jpg",substring=false).assertIsDisplayed();compose.onNodeWithText("Documents",substring=false).assertDoesNotExist();compose.onNodeWithText("photo",substring=false).performTextClearance();pump();compose.onAllNodesWithText("Folder",substring=false).onFirst().performSemanticsAction(SemanticsActions.OnClick){it()};pump();compose.onNodeWithText("Name",substring=false).performTextInput("Projects");pump();click("Create");waitReady();compose.onNodeWithText("Projects",substring=false).assertIsDisplayed();capture("browser")
 }
 @Test(timeout=45000) fun disconnectRemovesLocalCredentialsWithoutDeletingCloudFiles(){val store=CloudStore(compose.activity,MailTestKeys());store.save(listOf(cloudAccount()));val methods=mutableListOf<String>();m=CloudModel(compose.activity.application,store){a->CloudDav(a){r->methods+=r.method;cloudResponse(r,207,cloudXml(cloudRow("/dav/","Root",true)))}};compose.activity.setContent{OnKlavTheme{Surface{CloudWorkspace({}, {},m)}}};waitReady();compose.onNodeWithContentDescription("Cloud accounts & help").performClick();pump();click("Disconnect");assertEquals(1,store.load().size);click("Disconnect");waitReady();assertTrue(store.load().isEmpty());assertFalse("DELETE" in methods);compose.onNodeWithText("Sign in",substring=false).assertIsDisplayed()}
 @Test(timeout=45000) fun successfulUploadIsNotRetriedWhenFolderRefreshFails(){
  val store=CloudStore(compose.activity,MailTestKeys());store.save(listOf(cloudAccount()));var lists=0;var puts=0
  m=CloudModel(compose.activity.application,store){a->CloudDav(a){r->if(r.method=="PUT"){puts++;assertEquals("/dav/Final.bin",r.url.encodedPath);cloudResponse(r,201)}else{lists++;if(lists==1)cloudResponse(r,207,cloudXml(cloudRow("/dav/","Root",true)))else cloudResponse(r,503)}}}
  compose.activity.setContent{OnKlavTheme{Surface{CloudWorkspace({}, {},m)}}};waitReady();compose.runOnUiThread{m.prepare(android.net.Uri.parse("content://cloud-fixture/file"))};waitReady();assertNotNull(m.upload)
  compose.runOnUiThread{m.send("Final.bin")};waitReady();assertEquals(1,puts);assertNull(m.upload);assertTrue(m.status.startsWith("Uploaded Final.bin"));assertTrue(m.error.isNotBlank());compose.onNodeWithText("Upload file",substring=false).assertDoesNotExist()
 }

}
