package app.onklav.home

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@LooperMode(LooperMode.Mode.PAUSED)
class SdkLifecycleTest {
    @Test fun keyDeletionFailureRetainsCleanupForAnExplicitRetry(){
        val c=ApplicationProvider.getApplicationContext<Context>();val id="test.example.keycleanup"
        val previous=SdkNativeData.profile(c,id)
        assertThrows(IllegalStateException::class.java){SdkNativeData.removed(c,id,true,clearAccounts={_,_->}){_,_->error("Keystore unavailable")}}
        val rotated=SdkNativeData.profile(c,id);assertNotEquals(previous,rotated)
        assertEquals("native",SdkNativeData.record(c,id)!!.getString("phase"))
        var cleared:String?=null;SdkNativeData.removed(c,id,true,clearAccounts={_,_->}){_,owner->cleared=owner}
        assertEquals(id,cleared);assertEquals(rotated,SdkNativeData.profile(c,id));assertNull(SdkNativeData.record(c,id))
    }
    @Test fun deletedNativeProfileIsImmediatelyIsolatedAndOnlyItsOldProfileIsPurged(){
        val c=ApplicationProvider.getApplicationContext<Context>();val id="test.example.cleanup"
        val previous=SdkNativeData.profile(c,id);val other=SdkNativeData.profile(c,"test.example.other")
        SdkNativeData.removed(c,id,true,clearAccounts={_,_->}){_,_->}
        assertNotEquals(previous,SdkNativeData.profile(c,id));assertEquals(other,SdkNativeData.profile(c,"test.example.other"))
        var attempts=0;SdkNativeData.flush(c){attempts++;false};assertEquals(1,attempts)
        val purged=mutableListOf<String>();SdkNativeData.flush(c){purged+=it;true};assertEquals(listOf(previous),purged)
        SdkNativeData.flush(c){error("Already purged")}
    }
    @Test fun partialNativeCleanupKeepsItsJournalAndRetriesWithoutRotatingAgain(){
        val c=ApplicationProvider.getApplicationContext<Context>();val id="test.example.retry"
        val previous=SdkNativeData.profile(c,id)
        val blocker=java.io.File(c.cacheDir,"sdk-thumbnails/"+AppSdk.desktopId(id)+"/unexpected-directory").apply{mkdirs()}
        java.io.File(blocker,"keep-until-reviewed").writeText("fixture")
        SdkNativeData.prepare(c,id,true)
        assertThrows(IllegalStateException::class.java){SdkNativeData.removed(c,id,true,clearAccounts={_,_->}){_,_->}}
        val rotated=SdkNativeData.profile(c,id);assertNotEquals(previous,rotated)
        assertEquals("native",SdkNativeData.record(c,id)!!.getString("phase"))
        blocker.deleteRecursively();SdkNativeData.removed(c,id,true,clearAccounts={_,_->}){_,_->}
        assertEquals(rotated,SdkNativeData.profile(c,id));assertNull(SdkNativeData.record(c,id))
    }
    @Test fun audioOwnerInterruptsOnlyThePreviousOwnerAndOldReleaseCannotStopNewAudio(){
        val a=Any();val b=Any();var stoppedA=0;var stoppedB=0
        HostSpeechOwner.claim(a){stoppedA++};HostSpeechOwner.claim(b){stoppedB++}
        assertEquals(1,stoppedA);HostSpeechOwner.release(a);HostSpeechOwner.claim(b){error("Same owner must not interrupt")};assertEquals(0,stoppedB);HostSpeechOwner.release(b)
    }
    private class Backend:SpeechBackend {
        var rate=0f
        override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit){done(true,listOf(SpeechVoice("local","Local")))}
        override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean{this.rate=rate;return true}
        override fun stop(){}
        override fun close(){}
    }
    @Test fun messagesAndReaderShareVoicePreferencesAndOneCurrentReader(){
        val c=ApplicationProvider.getApplicationContext<Context>();Shadows.shadowOf(c.getSystemService(Context.AUDIO_SERVICE)as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        c.getSharedPreferences("reader",0).edit().clear().commit()
        val a=Backend();val b=Backend();val reader=SdkSpeechController(c,"app.onklav.reader",{},a);val messages=SdkSpeechController(c,"host.messages",{},b)
        reader.prepare("reader");reader.settingsRate(1.25f);reader.play("Reader text","reader");assertTrue(reader.playing)
        messages.play("Message text","message");assertFalse(reader.playing);assertTrue(messages.playing);assertEquals(1.25f,b.rate)
        reader.close();messages.close()
    }
}
