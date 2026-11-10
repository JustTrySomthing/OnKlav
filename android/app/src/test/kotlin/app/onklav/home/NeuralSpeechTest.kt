package app.onklav.home
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class NeuralSpeechTest {
    @Test fun compactInstallationReplacesOldDataAndRepairsChangedFiles(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val root=File(context.noBackupFilesDir,"speech/nano-fp32-v08").apply{mkdirs()}
        File(root,".verified").writeText("previous-full-pack")
        File(root,"espeak-ng-data").mkdirs()
        File(root,"espeak-ng-data/ru_dict").writeText("obsolete")
        val installed=SpeechModelFiles.prepare(context)
        assertFalse(File(installed,"espeak-ng-data/ru_dict").exists())
        assertEquals(819200L,File(installed,"voices.bin").length())
        val dictionary=File(installed,"espeak-ng-data/en_dict");val expected=dictionary.readBytes()
        val changed=expected.copyOf();changed[0]=(changed[0].toInt() xor 1).toByte();dictionary.writeBytes(changed)
        SpeechModelFiles.prepare(context)
        assertArrayEquals(expected,dictionary.readBytes())
    }
    @Test fun screenOffKeepsSharedSpeechWarmButRealPressureUnloadsIt(){
        assertFalse(shouldTrimSpeech(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertFalse(shouldTrimSpeech(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        for(level in listOf(10,15,40,60,80))assertTrue(shouldTrimSpeech(level))
    }
    @Test fun approvedReferenceIsUnsplitAndLongTextHasSafeNaturalBoundaries(){
        val approved="Friends fell out often because life was changing so fast. The easiest thing in the world was to lose touch with someone."
        assertEquals(listOf(SpeechPiece(0,approved.length)),SpeechPieces.plan(approved))
        val text=("A complete sentence, with a cat 🐈 beside the river. ".repeat(700)) + "x".repeat(501)+"🐈"
        val plan=SpeechPieces.plan(text);assertEquals(text,plan.joinToString(""){text.substring(it.start,it.end)})
        assertTrue(plan.all{it.end-it.start<=220&&it.end>it.start&&SpeechPlan.boundary(text,it.start)==it.start&&SpeechPlan.boundary(text,it.end)==it.end})
    }
    @Test fun bundledNativeJniPreservesApprovedVoiceAndRejectsInvalidRequests(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=SpeechModelFiles.prepare(context);assertTrue(File(directory,"espeak-ng-data/en_dict").exists())
        assertEquals(listOf("Male","Female"),SpeechVoices.choices.map{it.label})
        assertEquals(2,SpeechVoices.speaker(SpeechVoices.MALE))
        assertEquals(0,SpeechVoices.speaker(SpeechVoices.FEMALE))
        NeuralSpeechNative.load();NeuralSpeechNative.initialize(directory.absolutePath)
        val second=NeuralSpeechNative.synthesize("Hello Charles. This is the second included voice.",0,1f)
        assertTrue(second.size>24000&&second.all{it.isFinite()})
        assertTrue(second.maxOf{abs(it)} in .1f..1f)
        assertThrows(IllegalStateException::class.java){NeuralSpeechNative.synthesize("Retired voice",5,1f)}
        val text="Friends fell out often because life was changing so fast. The easiest thing in the world was to lose touch with someone."
        val pcm=NeuralSpeechNative.synthesize(text,2,1f);assertTrue(pcm.all{it.isFinite()});assertTrue(pcm.maxOf{abs(it)} in .1f..1f)
        val fixture=JSONObject(File(System.getProperty("onklav.project.root"),"docs/evidence/speech-integration/approved-envelope.json").readText());assertTrue(abs(pcm.size-fixture.getInt("frames"))<1200)
        val expected=fixture.getJSONArray("envelope");val actual=(0 until pcm.size-240 step 120).map{at->sqrt((at until at+240).sumOf{pcm[it].toDouble().pow(2)}/240)};fun smooth(values:List<Double>)=values.windowed(9).map{it.average()}
        val a=smooth(actual);val b=smooth((0 until expected.length()).map{expected.getDouble(it)})
        val corr=(-4..4).maxOf{lag->val aa=a.drop(max(0,lag));val bb=b.drop(max(0,-lag));val n=min(aa.size,bb.size);val x=aa.take(n);val y=bb.take(n);val mx=x.average();val my=y.average();(0 until n).sumOf{(x[it]-mx)*(y[it]-my)}/sqrt(x.sumOf{(it-mx).pow(2)}*y.sumOf{(it-my).pow(2)})}
        // The model contains RandomNormalLike/RandomUniformLike operations. Smooth
        // sub-phoneme variation and allow20ms alignment, without time-warping speech.
        assertTrue("JNI must retain approved native pronunciation/prosody: $corr",corr>.99)

        assertThrows(IllegalStateException::class.java){NeuralSpeechNative.synthesize("Hello",999,1f)}
        assertThrows(IllegalStateException::class.java){NeuralSpeechNative.synthesize("Hello",2,Float.NaN)}
        NeuralSpeechNative.unload()
    }
    private class Fake:SpeechBackend {
        var done:((String?)->Unit)?=null
        override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit)=done(true,SpeechVoices.choices)
        override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean{this.done=done;return true}
        override fun stop(){};override fun close(){}
    }
    @Test fun backgroundReadingRetainsAuthorizationAndNewAudioOwnerStopsIt(){
        val context=ApplicationProvider.getApplicationContext<Context>();Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE)as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        var authorized=true;val old=SdkSpeechController(context,"app.onklav.reader",{check(authorized){"Revoked"}},Fake());old.play("A long reading.","speech");old.onBackground();assertTrue(old.playing)
        val next=SdkSpeechController(context,"test.new-audio",{},Fake());next.play("New reading.","speech");assertFalse(old.playing);assertTrue(next.playing);next.close()
        old.play("Continue reading.","speech");authorized=false;old.onBackground();assertFalse(old.playing);old.close()
        val external=SdkSpeechController(context,"external.reader",{},Fake());external.play("Foreground only.","speech");external.onBackground();assertFalse(external.playing);external.close()
    }
    @Test fun onePageCompletionCallbackAndPausedReaderCannotStealNewAudio(){
        val context=ApplicationProvider.getApplicationContext<Context>();Shadows.shadowOf(context.getSystemService(Context.AUDIO_SERVICE)as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val fake=Fake();val p=SdkSpeechController(context,"app.onklav.ebooks",{},fake);var completed=0;p.onDocumentComplete={completed++};p.bookContinuous=true;p.bookWidget="speech";p.play("Page one.","speech");val stale=fake.done!!;stale(null);assertEquals(1,completed);stale(null);assertEquals(1,completed)
        p.resumeFromSystem();p.pauseFromSystem();assertFalse(p.playing)
        val newer=SdkSpeechController(context,"test.next",{},Fake());newer.play("New owner.","speech");assertTrue(newer.playing);p.close();assertTrue(newer.playing);newer.close()
    }
}
