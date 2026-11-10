package app.onklav.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class ClockPolishUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(64);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))}}
 private fun click(s:String){compose.onNodeWithText(s).performClick();pump()}
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));val f=File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/device-polish/clock-$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=90000)fun polishedTabsKeepAlarmTimerAndStopwatchActionsFunctional(){
  val c=compose.activity.application;runBlocking{AppSdk.bootstrap(c)}
  ClockDisk.saveAlarm(c,ClockAlarm(hour=7,minute=30,label="Morning light"));ClockDisk.timer(c,ClockTimer(label="Tea break",duration=180000),true)
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme(preview=app.onklav.shared.AppearanceSpec(palette=6,gradient=true)){ClockContent()}}};compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  compose.onNodeWithText("Morning light").assertIsDisplayed();compose.onNodeWithText("Add alarm").assertIsDisplayed();capture("alarms")
  click("Timer");compose.onNodeWithText("Tea break").assertIsDisplayed();compose.onNodeWithText("Pause").assertIsDisplayed();capture("timer")
  click("Pause");assertEquals(0L,ClockDisk.timers(c).single().end);click("Start");assertTrue(ClockDisk.timers(c).single().end>0)
  click("Stopwatch");click("Start");pump();compose.onNodeWithContentDescription("Lap").performClick();pump();assertEquals(1,ClockDisk.watch(c).laps.size);click("Pause");capture("stopwatch");compose.onNodeWithText("Resume").assertIsDisplayed()
 }
}
