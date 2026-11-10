package app.onklav.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocuViewUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File("/workspace/scratch/c739b0c1372b/current/OnKlav/docs/evidence/docu-view/$name.png").apply{parentFile.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test @Config(qualifiers="w320dp-h640dp-mdpi",fontScale=1.4f) fun welcomeIsReadableAndUsesOnKlavFiles(){
  val app=ApplicationProvider.getApplicationContext<Application>();val model=DocuViewModel(app);compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=false)){DocuViewWorkspace({},model)}}
  compose.onNodeWithText("Choose file").performScrollTo().assertIsDisplayed();capture("welcome-large");compose.onNodeWithText("Choose file").performClick();val intent=org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult.intent;assertEquals(FileBrowserActivity::class.java.name,intent.component!!.className)
 }
 @Test fun tableSearchAndSheetNavigationRemainSimple(){
  compose.activity.setContent{OnKlavTheme{Surface{Column(Modifier.fillMaxSize()){ReadingContent(ReadingDocument(sheets=listOf(ReadingSheet("Budget",listOf(listOf("Name","Amount"),listOf("Travel","125"))),ReadingSheet("Archive",emptyList())),notice="Cached values"),18f,"Travel",0,{},Modifier.weight(1f))}}}}
  compose.onNodeWithText("1 / 1").assertExists();compose.onNodeWithText("Travel").assertExists();compose.onNodeWithText("Budget").assertExists();compose.onNodeWithText("Archive").performClick();capture("spreadsheet")
 }
 @Test fun screenLightOffersContinuousColorFade(){
  kotlinx.coroutines.runBlocking{AppSdk.bootstrap(ApplicationProvider.getApplicationContext())}
  compose.activity.setContent{OnKlavTheme{FlashlightContent()}}
  compose.onNodeWithText("Screen light").performScrollTo().performClick();compose.onNodeWithText("Color fade").assertExists().performClick();compose.onNodeWithContentDescription("Close screen light").assertExists()
 }
 @Test fun chosenCsvOpensThroughSignedFilesAndLosingSourceAccessClearsTheSnapshot(){
  val app=ApplicationProvider.getApplicationContext<Application>();kotlinx.coroutines.runBlocking{AppSdk.bootstrap(app)}
  val source=File(app.cacheDir,"viewer-source.csv").apply{writeText("Name,Amount\nTravel,125\nFood,48")};val expected=source.readBytes()
  val descriptor=FileSelectionSession(app).use{s->s.start();s.importSaved(listOf("documents"),source,"Budget.csv","text/csv")}
  val selected=FileSelectionSession(app).use{s->s.start();s.navigate(listOf("documents"));s.chooseAt(listOf("documents"),s.state.nodes("items").first{it.getString("id")==descriptor.getString("id")})}
  val provider=FileSelectionProvider().apply{attachInfo(app,android.content.pm.ProviderInfo().apply{authority=app.packageName+".file-selection";exported=false})}
  org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(app.packageName+".file-selection",provider)
  val uri=FileSelectionStore.selected(app,selected,listOf("documents"));val model=DocuViewModel(app)
  compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=false)){DocuViewWorkspace({},model)}}
  compose.runOnUiThread{model.open(uri)}
  compose.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));!model.busy}
  compose.onNodeWithText("Budget.csv").assertExists();compose.onNodeWithText("Travel").assertExists();compose.onNodeWithText("Share").assertExists();assertNotNull(model.document);capture("docu-workspace")
  val original=File(selected.getString("path"));assertArrayEquals(expected,original.readBytes());val moved=File(app.cacheDir,"viewer-source-away");assertTrue(original.renameTo(moved))
  try{compose.runOnUiThread{model.checkAccess()};compose.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));model.document==null};compose.onNodeWithText("Choose file").assertExists();assertTrue(model.name.isBlank())}finally{moved.renameTo(original);source.delete()}
 }

}
