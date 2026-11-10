package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class MessageComposeFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun emptyComposerEntireWidthAcceptsAnotherMessageAfterSend(){
  var draft by mutableStateOf("");var sending by mutableStateOf(false)
  compose.activity.setContent{OnKlavTheme{Box(Modifier.width(320.dp)){MessageComposer(draft,true,{draft=it},{_,_->},readOnly=sending)}}}
  val field=compose.onNodeWithTag("message-composer")
  val bounds=field.getUnclippedBoundsInRoot()
  assertTrue((bounds.right-bounds.left).value>=300)
  field.performTouchInput{click(androidx.compose.ui.geometry.Offset(width-12f,height/2f))}
  field.assertIsFocused();field.performTextInput("First")
  compose.runOnIdle{assertEquals("First",draft);sending=true}
  field.assertIsFocused()
  compose.runOnIdle{draft="";sending=false}
  compose.waitForIdle()
  field.performTouchInput{click(androidx.compose.ui.geometry.Offset(width-12f,height/2f))}
  field.assertIsFocused();field.performTextInput("Second")
  compose.runOnIdle{assertEquals("Second",draft)}
 }
 @Test fun sentTailStaysAboveComposerAfterResizeAndHistoryBrowsingIsRespected(){
  var rows by mutableStateOf((1..30).toList());var sent by mutableIntStateOf(0);var height by mutableStateOf(480.dp)
  lateinit var state:LazyListState
  compose.activity.setContent{OnKlavTheme{Column(Modifier.width(360.dp)){
   state=rememberLazyListState();MessageFeedAutoScroll(state,1L,sent,true)
   LazyColumn(Modifier.height(height).testTag("feed"),state=state){items(rows,key={it}){Text("Message $it",Modifier.fillMaxWidth().height(65.dp).testTag("row-$it"))}}
   MessageComposer("",true,{},{_,_->})
  }}}
  compose.waitForIdle();compose.runOnIdle{assertFalse(state.canScrollForward)}
  compose.runOnIdle{rows=rows+31;sent++};compose.waitForIdle()
  compose.onNodeWithTag("row-31").assertIsDisplayed();compose.runOnIdle{assertFalse(state.canScrollForward);height=240.dp}
  compose.waitForIdle();compose.runOnIdle{assertFalse(state.canScrollForward)}
  compose.onNodeWithTag("feed").performTouchInput{swipeDown()};compose.waitForIdle()
  var before=0;compose.runOnIdle{assertTrue(state.canScrollForward);before=state.firstVisibleItemIndex;rows=rows+32}
  compose.waitForIdle();compose.runOnIdle{assertEquals(before,state.firstVisibleItemIndex);sent++}
  compose.waitForIdle();compose.onNodeWithTag("row-32").assertIsDisplayed();compose.runOnIdle{assertFalse(state.canScrollForward)}
 }
}
