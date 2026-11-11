package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
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
 @Test fun reopeningAutoScrollAfterNotificationFocusKeepsTheTargetOnScreen(){
  var enabled by mutableStateOf(false)
  var sent by mutableIntStateOf(0)
  lateinit var state: LazyListState
  compose.activity.setContent { OnKlavTheme {
   state = rememberLazyListState(initialFirstVisibleItemIndex = 6)
   MessageFeedAutoScroll(state, 1L, sent, enabled, preservePosition = sent == 0)
   LazyColumn(Modifier.height(240.dp), state = state) {
    items((1..30).toList(), key = { it }) { Text("Message $it", Modifier.height(60.dp)) }
   }
  } }
  compose.onNodeWithText("Message 7").assertIsDisplayed()
  compose.runOnIdle { enabled = true }
  compose.onNodeWithText("Message 7").assertIsDisplayed()
  compose.runOnIdle { sent++ }
  compose.onNodeWithText("Message 30").assertIsDisplayed()
 }

 @Test fun tappingAnExistingInboxContactLoadsHistoryOnTheFirstOpen(){
  val provider = openInboxFixture()
  compose.onNodeWithText("First Tap Contact").performClick()
  assertHistoryDisplayed(provider)
  compose.onNodeWithText("No messages to display.").assertDoesNotExist()

  provider.newMessage = true
  val app = ApplicationProvider.getApplicationContext<Application>()
  app.contentResolver.notifyChange(Uri.parse("content://sms/75"), null)
  compose.waitUntil(6000) {
   compose.onAllNodesWithText("New message while open").fetchSemanticsNodes().isNotEmpty()
  }
  compose.onNodeWithText("New message while open").assertIsDisplayed()
 }

 private fun assertHistoryDisplayed(provider: ColdHistoryProvider) {
  try {
   compose.waitUntil(6000) {
    compose.onAllNodesWithText("Previously saved message").fetchSemanticsNodes().isNotEmpty()
   }
   compose.onNodeWithText("Previously saved message").assertIsDisplayed()
  } catch (e: Throwable) {
   throw AssertionError("Queries: ${provider.queries} " + compose.onRoot().printToString(), e)
  }
 }

 private fun openInboxFixture(): ColdHistoryProvider {
  val app = ApplicationProvider.getApplicationContext<Application>()
  shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)
  val provider = ColdHistoryProvider()
  for (authority in listOf("sms", "mms", "mms-sms", "com.android.contacts")) {
   ShadowContentResolver.registerProviderInternal(authority, provider)
  }
  compose.runOnUiThread { compose.activity.setContent { OnKlavTheme { MessengerPane({}) } } }
  compose.waitUntil(10000) { compose.onAllNodesWithText("First Tap Contact").fetchSemanticsNodes().isNotEmpty() }
  return provider
 }

 private class ColdHistoryProvider : ContentProvider() {
  @Volatile var newMessage = false
  val queries = java.util.concurrent.ConcurrentLinkedQueue<String>()
  override fun onCreate() = true
  override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
   args: Array<out String>?, sort: String?): MatrixCursor {
   queries += "$uri ${args?.toList()}"
   val columns = requireNotNull(projection)
   return MatrixCursor(columns).apply {
    fun row(values: Map<String, Any>) = addRow(columns.map(values::get))
    when {
     uri.authority == "com.android.contacts" -> row(mapOf(
      "data1" to "5550101", "display_name" to "First Tap Contact", "data2" to "First", "contact_id" to 1L))
     uri.path.orEmpty().startsWith("/canonical-address") -> row(mapOf("_id" to 921L, "address" to "5550101"))
     uri.path == "/conversations" && selection?.contains("LIKE") != true -> row(mapOf(
      "_id" to 42L, "date" to 1000L, "snippet" to "Saved history", "read" to 1,
      "recipient_ids" to "921", "message_count" to 1))
     uri.authority == "sms" && args?.firstOrNull() == "42" -> {
      val sms = mapOf("type" to 1, "status" to -1, "address" to "5550101")
      if (newMessage) row(sms + mapOf("_id" to 75L, "date" to 2000L, "body" to "New message while open"))
      row(sms + mapOf("_id" to 74L, "date" to 1000L, "body" to "Previously saved message"))
     }
    }
   }
  }
  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
 }

}
