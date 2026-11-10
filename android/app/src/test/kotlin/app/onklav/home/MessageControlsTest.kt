package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.net.Uri
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class MessageControlsTest {
 @Test fun inboxTimestampsMatchTodayMonthDayAndOlderYear(){
  val zone=TimeZone.getTimeZone("America/Chicago");fun at(y:Int,m:Int,d:Int,h:Int=10,n:Int=37)=Calendar.getInstance(zone).apply{clear();set(y,m-1,d,h,n)}.timeInMillis
  val now=at(2026,10,23,12)
  assertEquals("10:37 AM",conversationTimestamp(at(2026,10,23),now,Locale.US,zone))
  assertEquals("10:37",conversationTimestamp(at(2026,10,23),now,Locale.US,zone,true))
  assertEquals("Oct 22",conversationTimestamp(at(2026,10,22),now,Locale.US,zone))
  assertEquals("Oct 22, 2025",conversationTimestamp(at(2025,10,22),now,Locale.US,zone))
 }
 @Test fun pinAndMutePersistAndTransferWhenANewGroupGetsItsPhoneThread()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Context>();val store=ChatStore(c);val id="b".repeat(32)
  store.request(JSONObject().put("action","group_save").put("id",id).put("name","Family").put("members",org.json.JSONArray(listOf("15550100101","15550100102"))).put("thread",0))
  store.request(JSONObject().put("action","thread").put("key","group:$id").put("muted",true).put("pinned",true))
  store.request(JSONObject().put("action","group_save").put("id",id).put("name","Family").put("members",org.json.JSONArray(listOf("15550100101","15550100102"))).put("thread",51))
  val s=ChatStore(c).snapshot();assertTrue(s.thread(51).optBoolean("muted"));assertTrue(s.thread(51).optBoolean("pinned"))
  store.request(JSONObject().put("action","thread").put("key","51").put("muted",false).put("pinned",false));assertFalse(store.snapshot().thread(51).optBoolean("muted"));assertFalse(store.snapshot().thread(51).optBoolean("pinned"))
 }
 private class Voice:SpeechBackend {
  lateinit var initialized:(Boolean,List<SpeechVoice>)->Unit
  var spoken="";var voice="";var rate=0f;var starts=0
  override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit){initialized=done}
  override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean{spoken=text;this.voice=voice;this.rate=rate;starts++;return true}
  override fun stop(){};override fun close(){}
 }
 @Test fun firstTapWaitsForVoiceAndEachMessageStartsAtTheBeginningUsingReaderPreferences(){
  val c=ApplicationProvider.getApplicationContext<Context>();Shadows.shadowOf(c.getSystemService(Context.AUDIO_SERVICE)as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
  c.getSharedPreferences("reader",0).edit().putString("voice","chosen").putFloat("rate",1.05f).commit()
  val voice=Voice();val player=SdkSpeechController(c,"host.messages",{},voice)
  player.playMessage("Same message","sms:1");assertEquals(0,voice.starts)
  voice.initialized(true,listOf(SpeechVoice("chosen","Voice")));assertTrue(player.playing);assertEquals("Same message",voice.spoken);assertEquals("chosen",voice.voice);assertEquals(1.05f,voice.rate)
  player.playMessage("Same message","sms:2");assertTrue(player.playing);assertEquals(2,voice.starts);assertEquals(0,player.position)
  player.playMessage("Same message","sms:2");assertFalse(player.playing);player.close()
 }
 @Test fun pendingMessageSpeechCannotStartAfterStop(){
  val c=ApplicationProvider.getApplicationContext<Context>();val voice=Voice();val player=SdkSpeechController(c,"host.messages",{},voice)
  player.playMessage("Private message","sms:1");player.stop();voice.initialized(true,listOf(SpeechVoice("chosen","Voice")));assertEquals(0,voice.starts);assertFalse(player.playing);player.close()
 }
 class Mail:ContentProvider(){
  var smsSelection="";var smsArgs=listOf<String>();var windowArgs=listOf<String>()
  override fun onCreate()=true
  override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {
   if(uri.authority=="sms"&&projection?.size==3){smsSelection=selection.orEmpty();smsArgs=args?.toList().orEmpty();return MatrixCursor(arrayOf("_id","body","date")).apply{addRow(arrayOf(1,"Older needle message",1000L))}}
   if(uri.authority=="sms"){windowArgs=args?.toList().orEmpty();return MatrixCursor(arrayOf("_id","body","date","type","status","address"))}
   return MatrixCursor(projection?:arrayOf("_id","date"))
  }
  override fun getType(uri:Uri):String?=null
  override fun insert(uri:Uri,values:ContentValues?):Uri?=null
  override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
  override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
 }
 @Test fun historySearchIsThreadScopedAndOpeningAResultLoadsTheRequestedDateWindow()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_SMS)
  val provider=Mail();provider.attachInfo(c,ProviderInfo().apply{authority="sms"});ShadowContentResolver.registerProviderInternal("sms",provider);ShadowContentResolver.registerProviderInternal("mms",provider)
  val repo=MessageRepository(c);val hits=repo.findConversationMessages(51,"needle%");assertEquals("sms:1",hits.hits.single().key);assertTrue(provider.smsSelection.contains("thread_id=?"));assertEquals(listOf("51","%needle\\%%"),provider.smsArgs)
  repo.messages(51,100,JSONObject(),beforeDate=1000);assertEquals(listOf("51","1000"),provider.windowArgs)
 }
}
