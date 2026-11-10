package app.onklav.home
import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SpeechServiceTest {
    private class Fake:SpeechBackend {
        override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit)=done(true,SpeechVoices.choices)
        override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit)=true
        override fun stop(){};override fun close(){}
    }
    @Test fun notificationPauseResumeNoisyRouteAndDestroyReleaseReading(){
        val app=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(app.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val player=SdkSpeechController(app,"app.onklav.reader",{},Fake());player.play("An offline reading.","speech")
        val start=Shadows.shadowOf(app).nextStartedService;assertNotNull(start)
        val controller=Robolectric.buildService(SpeechPlaybackService::class.java,start).create();val service=controller.get();service.onStartCommand(start,0,1);Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(Shadows.shadowOf(service).lastForegroundNotification)
        val wake=SpeechPlaybackService::class.java.getDeclaredField("wake").apply{isAccessible=true}.get(service)as PowerManager.WakeLock;assertTrue(wake.isHeld)
        service.onStartCommand(Intent(start).putExtra("control","pause"),0,2);assertFalse(player.playing);player.onBackground();assertTrue(SpeechPlaybackService.owns(player))
        service.onStartCommand(Intent(start).putExtra("control","play").putExtra("epoch",-100L),0,3);assertFalse(player.playing)
        service.onStartCommand(Intent(start).putExtra("control","play"),0,4);assertTrue(player.playing)
        app.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(player.playing)
        player.resumeFromSystem();assertTrue(player.playing);controller.destroy();assertFalse(player.playing);assertFalse(wake.isHeld);player.close()
    }
    @Test fun lockingDuringColdVoicePreparationKeepsTheUserStartedSession(){
        val app=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(app.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        var ready:((Boolean,List<SpeechVoice>)->Unit)?=null
        val backend=object:SpeechBackend{override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit){ready=done};override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit)=true;override fun stop(){};override fun close(){}}
        val player=SdkSpeechController(app,"app.onklav.reader",{},backend);player.play("A cold-start reading.","speech");assertTrue(player.isPreparing);assertTrue(SpeechPlaybackService.owns(player));player.onBackground();assertTrue(player.isPreparing)
        ready!!(true,SpeechVoices.choices);assertTrue(player.playing);player.onBackground();assertTrue(player.playing);player.close();assertFalse(SpeechPlaybackService.owns(player))
    }

}
