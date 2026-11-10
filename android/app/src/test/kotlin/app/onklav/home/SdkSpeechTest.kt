package app.onklav.home

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkSpeechTest {
    @Test fun uppercaseTitlesAreWordsAndKeepRangeOffsets(){
        val text="CHAPTER IV\nTHE RIVER\nI met NASA yesterday."
        val spoken=speechText(text)
        assertEquals("chapter iv\nthe river\nI met NASA yesterday.",spoken)
        assertEquals(text.length,spoken.length)
    }

    @Test fun defaultRateAndInvalidStoredRateStayUsable() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("sdk-speech-test.rates",0)
        prefs.edit().clear().commit()
        var player=SdkSpeechController(context,"test.rates",{},Fake())
        assertEquals(1.0f,player.rate);player.settingsRate(Float.NaN)
        assertEquals(1.0f,player.rate);player.close()
        prefs.edit().putFloat("rate",Float.POSITIVE_INFINITY).commit()
        player=SdkSpeechController(context,"test.rates",{},Fake())
        assertEquals(1.0f,player.rate);player.close();prefs.edit().clear().commit()
    }
    private class Fake:SpeechBackend {
        var spoken="";var stops=0;var closed=false
        var range:((Int)->Unit)?=null;var done:((String?)->Unit)?=null
        override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit) { done(true,listOf(SpeechVoice("local","Local"))) }
        override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean { spoken=text;this.range=range;this.done=done;return true }
        override fun stop() { stops++ }
        override fun close() { closed=true }
    }
    @Test fun chunkedPlaybackPauseSeekRevocationAndLateCallbacks() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val fake=Fake();var allowed=true
        val player=SdkSpeechController(context,"test.example.reader",{check(allowed) {"Revoked"}},fake)
        val text="A long sentence. ".repeat(200)+"🐈 End."
        player.play(text,"player");assertTrue(player.playing);assertFalse(fake.spoken.contains("🐈"))
        fake.range!!(10);assertEquals(10,player.position)
        val old=fake.done!!;player.stop();old(null);assertEquals(10,player.position);assertFalse(player.playing)
        player.play(text,"player");fake.range!!(15);assertTrue(player.position>10)
        allowed=false;fake.range!!(20);assertFalse(player.playing);assertTrue(player.error!!.contains("Revoked"))
        val paused=player.position;fake.done!!(null);assertEquals(paused,player.position)
        allowed=true;player.move(text,text.length,"player");player.play(text,"player");assertEquals(0,player.position)
        player.settingsRate(4f);assertEquals(1.5f,player.rate);assertFalse(player.playing)
        player.close();assertTrue(fake.closed)
    }
    @Test fun utf16BoundariesAndPreviewRestoresPosition() {
        val context=ApplicationProvider.getApplicationContext<Context>();val fake=Fake()
        Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        assertEquals(1,SpeechPlan.boundary("a🐈z",2))
        val player=SdkSpeechController(context,"test.example.preview",{},fake)
        player.setDocument("One. Two. Three.");player.move("One. Two. Three.",5,"p");val at=player.position
        player.beginSettings();player.play("Preview","voices");player.endSettings();assertEquals(at,player.position);assertFalse(player.playing)
        player.play("One. Two. Three.","p");fake.done!!(null);assertEquals(16,player.position);assertFalse(player.playing)
        player.close()
    }
    private class Delayed:SpeechBackend {
        val callbacks=mutableListOf<(Boolean,List<SpeechVoice>)->Unit>()
        var spoken=0
        override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit) { callbacks+=done }
        override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean { spoken++;return true }
        override fun stop() {}
        override fun close() {}
        fun ready(index:Int=callbacks.lastIndex) { callbacks[index](true,listOf(SpeechVoice("local","Local"))) }
    }
    @Test fun queuedSpeechCannotInterruptNewerReaderOrSurviveRevocation() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val slow=Delayed();val fast=Fake();var allowed=true
        val first=SdkSpeechController(context,"test.queue",{check(allowed){"Revoked"}},slow)
        val second=SdkSpeechController(context,"test.newer",{},fast)
        first.play("Old queued words","p");second.play("Newer words","p")
        slow.ready();assertEquals(0,slow.spoken);assertTrue(second.playing)
        first.close();second.close()
        val delayed=Delayed();val revoked=SdkSpeechController(context,"test.revoked",{check(allowed){"Revoked"}},delayed)
        revoked.play("Waiting","p");allowed=false;revoked.checkPermission();delayed.ready()
        assertEquals(0,delayed.spoken);assertFalse(revoked.playing);revoked.close()
    }
    @Test fun retryIgnoresOldInitializationAndCloseCancelsQueue() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val delayed=Delayed();val player=SdkSpeechController(context,"test.retry",{},delayed)
        player.play("Waiting","p");player.retry("p");player.play("Replacement","p")
        delayed.ready(0);assertFalse(player.ready);assertEquals(0,delayed.spoken)
        delayed.ready(1);assertTrue(player.playing);assertEquals(1,delayed.spoken)
        player.close();player.play("Closed","p");assertEquals(1,delayed.spoken)
        val late=Delayed();val closed=SdkSpeechController(context,"test.closed",{},late)
        closed.play("Waiting","p");closed.close();late.ready();assertFalse(closed.ready);assertEquals(0,late.spoken)
    }

    @Test fun ebooksShareMessageVoiceSettingsAndCompletionIsRevocable(){
        val context=ApplicationProvider.getApplicationContext<Context>();context.getSharedPreferences("reader",0).edit().putString("voice","local").putFloat("rate",1.2f).commit()
        Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val fake=Fake();var allowed=true;val player=SdkSpeechController(context,"app.onklav.ebooks",{check(allowed){"Revoked"}},fake)
        assertEquals(1.2f,player.rate);assertEquals("local",player.voice);player.bookContinuous=true;player.bookWidget="ebook.read.aloud";player.play("One page.","ebook.read.aloud");fake.done!!(null);assertEquals(1L,player.completed);assertFalse(player.playing)
        player.play("Next page.","ebook.read.aloud");allowed=false;fake.done!!(null);assertFalse(player.playing);assertFalse(player.bookContinuous);assertEquals(1L,player.completed);player.close()
    }

}
