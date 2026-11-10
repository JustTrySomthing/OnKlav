package app.onklav.home

import android.content.ClipDescription
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.json.JSONObject
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class KeyboardContentTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(32);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))}}
 private fun owner(v:View):View?=if(v.javaClass.simpleName=="AndroidComposeView")v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull{owner(v.getChildAt(it))}else null
 @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
 @Test(timeout=90000)fun realInputConnectionAcceptsUnicodeAndAnimatedGifIntoDraftMedia(){
  var value by mutableStateOf("");var part:JSONObject?=null
  val c=compose.activity;val bytes=android.util.Base64.decode("R0lGODlhCAAIAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAACAAIAAAIDwABCBxIsKDBgwgTKkwYEAAh+QQBCgABACwAAAAACAAIAIEAAP8AAAAAAAAAAAAIDwABCBxIsKDBgwgTKkwYEAA7",0)
  val uri=MessageTransferProvider.create(c,"image/gif");c.contentResolver.openOutputStream(uri)!!.use{it.write(bytes)}
  compose.runOnUiThread{c.setContent{OnKlavTheme{MessageComposer(value,true,{value=it},{_,u->part=MessageMedia.import(c,u,300000)})}}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump();compose.onNodeWithTag("message-composer").performClick();pump()
  var connection:InputConnection?=null;val info=EditorInfo()
  compose.waitUntil(10000){pump();compose.runOnUiThread{connection=owner(c.window.decorView)?.onCreateInputConnection(info)};connection!=null}
  assertTrue(info.contentMimeTypes?.any{ClipDescription.compareMimeTypes("image/gif",it)}==true)
  val text="Hi 👩🏽‍💻 ❤️ 🫶"
  compose.runOnUiThread{assertTrue(connection!!.commitText(text,1))};pump();assertEquals(text,value)
  compose.runOnUiThread{assertTrue(connection!!.commitContent(InputContentInfo(uri,ClipDescription("Keyboard GIF",arrayOf("image/gif")),null),0,null))};pump()
  assertNotNull(part);assertEquals("image/gif",part!!.getString("mime"));assertArrayEquals(bytes,MessageTransferProvider.file(c,part!!.getString("file")).readBytes())
  assertEquals(text,value)
  val png=MessageTransferProvider.create(c,"image/png");val bitmap=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GREEN);c.contentResolver.openOutputStream(png)!!.use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  compose.runOnUiThread{assertTrue(connection!!.commitContent(InputContentInfo(png,ClipDescription("Emoji sticker",arrayOf("image/png")),null),0,null))};pump()
  assertEquals("image/jpeg",part!!.getString("mime"));assertTrue(part!!.getInt("bytes")>0);assertEquals(text,value)
  compose.runOnUiThread{value="Draft restored 😀"};pump();compose.onNodeWithTag("message-composer").assertTextContains("Draft restored 😀")
  assertThrows(IllegalArgumentException::class.java){MessageMedia.import(c,uri,1)}
 }
}
