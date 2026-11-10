package app.onklav.home
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Looper
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PendingPicturesUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));compose.mainClock.advanceTimeByFrame()}}
 @Test(timeout=60000) fun disabledDownloadsOfferAnActionAndExpiredStatusDoesNotPretendToBeDownloading(){
  val c=compose.activity;val id="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  shadowOf(c.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_TELEPHONY,true)
  c.getSharedPreferences("messaging_setup",0).edit().putBoolean("carrier_trial",false).commit()
  c.getSharedPreferences("mms_transfers",0).edit().putString("$id.location","http://carrier.example/picture").putString("$id.sender","Test sender").putLong("$id.attempt",System.currentTimeMillis()-INCOMING_MMS_TIMEOUT_MS-1).commit()
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{PendingPicturesDialog{}}}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false
  compose.waitUntil(15000){pump();compose.onAllNodesWithText("Enable picture downloads").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Retry Picture Message").assertIsNotEnabled()
  compose.onNodeWithText("The picture download timed out.",substring=true).assertExists()
  compose.onNodeWithText("Downloading…",substring=false).assertDoesNotExist()
  compose.onNodeWithText("Enable picture downloads").performClick();pump()
  assertTrue(MessagingTransport.carrierEnabled(c));assertFalse(MessagingTransport.MMS_READY)
  compose.onNodeWithText("Retry Picture Message").assertIsEnabled()
  compose.onNodeWithText("Enable picture downloads").assertDoesNotExist()
 }
}
