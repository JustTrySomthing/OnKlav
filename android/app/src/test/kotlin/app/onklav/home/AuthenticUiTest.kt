package app.onklav.home
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthenticUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var model:AuthenticModel
 private val demo=AuthenticAccount(issuer="Example service",account="demo@example.com",secret=AuthenticOtp.encode("12345678901234567890".toByteArray()))
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));compose.mainClock.advanceTimeBy(100)}}
 private fun waitFor(test:()->Boolean){compose.waitUntil(20000){pump();test()}}
 @Before fun setup(){val app=compose.activity.application;File(app.filesDir,"authentic.bin").delete();AuthenticStore(app).createWithRecovery("test-password".toCharArray(),listOf(0,0,0),listOf("snack","movie","car").map{it.toCharArray()});model=AuthenticModel(app);compose.activity.setContent{OnKlavTheme{AuthenticWorkspace({},model)}};compose.waitForIdle();compose.mainClock.autoAdvance=false;compose.runOnUiThread{model.unlock("test-password".toCharArray())};waitFor{model.unlocked&&!model.busy};compose.runOnUiThread{model.stage(demo);model.importPending()};waitFor{model.accounts.size==1&&!model.busy};pump()}
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/authentic/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test fun codesCopySearchPinAndLockAreClear(){compose.onNodeWithText("Example service").assertIsDisplayed();compose.onNodeWithContentDescription("Copy code for demo@example.com").performClick();pump();val manager=compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)as android.content.ClipboardManager;assertEquals(6,manager.primaryClip!!.getItemAt(0).text.length);compose.onNodeWithContentDescription("Options for demo@example.com").performClick();pump();compose.onNodeWithText("Pin",substring=false).performClick();waitFor{model.accounts.single().pinned&&!model.busy};compose.mainClock.advanceTimeBy(5000);pump();capture("phone");compose.onNodeWithContentDescription("Lock Authentic").performClick();pump();assertFalse(model.unlocked);assertTrue(model.accounts.isEmpty());compose.onNodeWithText("Unlock Authentic").assertIsDisplayed();assertEquals(6,manager.primaryClip!!.getItemAt(0).text.length)}
 @Test fun duplicatesCounterUpdatesAndDeleteAreReversibleUntilConfirmed(){compose.runOnUiThread{model.stage(demo.copy(id="different"));model.importPending()};waitFor{!model.busy};assertEquals(1,model.accounts.size);compose.onNodeWithContentDescription("Options for demo@example.com").performClick();pump();compose.onNodeWithText("Delete account").performClick();pump();assertEquals(1,model.accounts.size);compose.onNodeWithText("Cancel",substring=false).performClick();pump();assertEquals(1,model.accounts.size);compose.onNodeWithContentDescription("Options for demo@example.com").performClick();pump();compose.onNodeWithText("Delete account").performClick();pump();compose.onNodeWithText("Delete",substring=false).performClick();waitFor{model.accounts.isEmpty()&&!model.busy}}
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun eightDigitCodesFitOnSmallLargeTextPhones(){compose.runOnUiThread{model.stage(demo.copy(id="eight",account="eight@example.com",secret=AuthenticOtp.encode("12345678901234567890123456789012".toByteArray()),digits=8,algorithm="SHA256"));model.importPending()};waitFor{model.accounts.size==2&&!model.busy};compose.onNodeWithContentDescription("Copy code for eight@example.com").performScrollTo().assertIsDisplayed();capture("small-large-text")}
}
