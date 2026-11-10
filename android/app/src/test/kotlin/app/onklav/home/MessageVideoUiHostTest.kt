package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MessageVideoUiHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun pump(){repeat(4){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.mainClock.advanceTimeByFrame()}}
    private fun click(text:String){compose.onNodeWithText(text).performClick();pump()}
    @Test(timeout=90000) fun privacySwitchDrivesMessageCardsAndPlayStaysExplicit() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        MessageVideoPreviews.setAllowed(c,false)
        val fetched=AtomicInteger(0);var played:MessageVideo?=null
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{
            var privacy by remember{mutableStateOf(false)}
            Column {
                TextButton(onClick={privacy=true}){Text("Privacy")}
                MessageVideoCards("See https://youtu.be/AbC_def-123?t=1m23s",loader={_,_,_->fetched.incrementAndGet();null}){played=it}
            }
            if(privacy)PrivacySettingsDialog{privacy=false}
        }}}
        pump();assertEquals(0,fetched.get())
        click("Play Video");assertEquals(MessageVideo("AbC_def-123",83),played)
        click("Privacy");click("Automatic YouTube Previews");click("Close")
        compose.waitUntil(15000){pump();fetched.get()>0}
        assertTrue(MessageVideoPreviews.allowed(c))
        val before=fetched.get()
        click("Privacy");click("Automatic YouTube Previews");click("Close")
        pump();assertFalse(MessageVideoPreviews.allowed(c));assertEquals(before,fetched.get())
        compose.onAllNodesWithText("Play Video").onLast().assertIsDisplayed()
    }
}
