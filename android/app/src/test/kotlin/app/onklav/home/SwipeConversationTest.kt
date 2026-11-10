package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeConversationTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Test(timeout=25000) fun swipingEitherWayRevealsActionsWithoutArchivingDeletingOrBlocking() {
        var changes=0
        var revealed:String?=null
        ui.setContent {OnKlavTheme {
            var opened by remember{mutableStateOf<String?>(null)}
            SwipeConversation("sample",true,opened,{opened=it;revealed=it},false,false,false,"Block this sender?",{changes++},{changes++},{changes++}) {
                Box(Modifier.fillMaxWidth().height(90.dp)){Text("Conversation")}
            }
        }}
        ui.onNodeWithTag("swipe-sample").performTouchInput{swipeLeft()}
        ui.waitForIdle();ui.onNodeWithText("Archive").assertIsDisplayed();ui.onNodeWithText("Block").assertIsDisplayed();ui.onNodeWithText("Delete").assertIsDisplayed()
        assertEquals("sample",revealed);assertEquals(0,changes)
        ui.onNodeWithTag("swipe-sample").performTouchInput{click(androidx.compose.ui.geometry.Offset(width*.15f,height/2f))}
        ui.waitForIdle();ui.onNodeWithText("Archive").assertDoesNotExist()
        ui.onNodeWithTag("swipe-sample").performTouchInput{swipeRight()}
        ui.waitForIdle();ui.onNodeWithText("Archive").assertIsDisplayed()
        ui.onNodeWithTag("swipe-sample").performTouchInput{click(androidx.compose.ui.geometry.Offset(width*.85f,height/2f))}
        ui.waitForIdle();ui.onNodeWithText("Archive").assertDoesNotExist();assertEquals(0,changes)
    }
    @Test fun smallMovementClosesAndLargeMovementRevealsOnEitherSide(){
        assertEquals(0f,swipeRevealTarget(10f,200f));assertEquals(0f,swipeRevealTarget(-10f,200f))
        assertEquals(200f,swipeRevealTarget(80f,200f));assertEquals(-200f,swipeRevealTarget(-80f,200f))
    }
}
