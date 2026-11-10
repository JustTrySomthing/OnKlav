package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.onklav.shared.contrastInk
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class MessageCorrectionsTest {
 @Test fun completePickerIsAcceptedByWireAndRepresentativeChoicesSurviveNativeReload()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Context>();val wire=GroupProtocol(c)
  val rows=JSONArray(c.assets.open("messenger/emoji.json").bufferedReader().use{it.readText()})
  for(i in 0 until rows.length()) { val emoji=rows.getJSONObject(i).getString("emoji");assertTrue(emoji,wire.reaction("a".repeat(32),emoji).isNotBlank()) }
  val store=ChatStore(c)
  for(emoji in listOf("😊","🤞","🥰","👩🏽‍💻","🏳️‍🌈","")) {
   store.request(JSONObject().put("action","message").put("key","sms:77").put("reaction",emoji))
   assertEquals(emoji,ChatStore(c).snapshot().child("messages").child("sms:77").getString("reaction"))
  }
 }
 @Test fun mediaOnlyMessagesHaveNoSpeechButCaptionsAndTextDo() {
  for(mime in listOf("image/jpeg","video/mp4","audio/mp4")) {
   val part=MessagePart("content://media/test",mime,"test")
   assertEquals("",messageSpeechText(Message(1,"",1,false,parts=listOf(part))))
   assertEquals("",messageSpeechText(Message(1,"[Audio]",1,false,parts=listOf(part))))
   assertEquals("Family picnic",messageSpeechText(Message(1,"Family picnic",1,false,parts=listOf(part))))
  }
  assertEquals("Audio",messageSpeechText(Message(1,"Audio",1,false)))
 }
 @Test fun selectedThemeFillAlwaysGetsReadableButtonText() {
  for(rgb in listOf(0xffc9aaff,0xff7addb3,0xffd9be41,0xff326c48,0xff191a1e,0xffffffff)) {
   val fill=Color(rgb);val ink=contrastInk(fill)
   val ratio=(maxOf(fill.luminance(),ink.luminance())+.05f)/(minOf(fill.luminance(),ink.luminance())+.05f)
   assertTrue("Contrast $ratio for $rgb",ratio>=4.5f)
  }
 }
}
