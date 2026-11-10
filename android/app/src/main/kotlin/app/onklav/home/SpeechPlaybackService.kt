package app.onklav.home

import android.app.*
import android.content.*
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicLong

/** User-started built-in reading only. A service lease never grants an SDK capability. */
class SpeechPlaybackService:Service() {
    companion object {
        private val sequence=AtomicLong()
        @Volatile private var owner:SdkSpeechController?=null
        @Volatile private var epoch=0L
        private var active:SpeechPlaybackService?=null
        private val main=Handler(Looper.getMainLooper())
        internal fun attach(context:Context,controller:SdkSpeechController){
            if(owner!==controller){owner=controller;epoch=sequence.incrementAndGet()}
            ContextCompat.startForegroundService(context,Intent(context,SpeechPlaybackService::class.java).putExtra("epoch",epoch))
        }
        internal fun owns(controller:SdkSpeechController)=owner===controller
        internal fun refresh(controller:SdkSpeechController){if(owner===controller)active?.refresh()}
        internal fun release(context:Context,controller:SdkSpeechController){if(owner!==controller)return;owner=null;val previous=epoch;main.post{if(owner==null&&epoch==previous)context.stopService(Intent(context,SpeechPlaybackService::class.java))}}
    }
    private var media:MediaSession?=null
    private var wake:PowerManager.WakeLock?=null
    private val handler=Handler(Looper.getMainLooper())
    private var pausedAt=0L
    private val tick=object:Runnable{override fun run(){val p=owner;if(p==null){stopSelf();return};p.checkPermission();if(p.playing||p.isPreparing){pausedAt=0;wake?.acquire(120000)}else{if(wake?.isHeld==true)wake?.release();if(pausedAt==0L)pausedAt=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-pausedAt>300000){p.stop();return}};refresh();handler.postDelayed(this,1000)}}
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate();active=this
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("speech-reading","Read aloud",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null)})
        media=MediaSession(this,"OnKlav read aloud").apply{setCallback(object:MediaSession.Callback(){override fun onPlay(){owner?.resumeFromSystem()};override fun onPause(){owner?.pauseFromSystem()};override fun onStop(){owner?.stop()}});isActive=true}
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"OnKlav:read-aloud").apply{setReferenceCounted(false)}
        val filter=IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,RECEIVER_NOT_EXPORTED)else registerReceiver(noisy,filter)
        handler.post(tick)
    }
    private val noisy=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){owner?.pauseFromSystem()}}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(owner==null||intent==null){stopSelfResult(startId);return START_NOT_STICKY}
        if(intent.getLongExtra("epoch",-1)!=epoch)return START_NOT_STICKY
        try{refresh();when(intent.getStringExtra("control")){"play"->owner?.resumeFromSystem();"pause"->owner?.pauseFromSystem();"stop"->owner?.stop()}}
        catch(e:Exception){owner?.stop();stopSelfResult(startId)}
        return START_NOT_STICKY
    }
    private fun action(name:String)=PendingIntent.getService(this,name.hashCode(),Intent(this,SpeechPlaybackService::class.java).putExtra("control",name).putExtra("epoch",epoch),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun refresh(){val p=owner?:return;val playing=p.playing;val preparing=p.isPreparing;val activeReading=playing||preparing
        media?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Reading aloud").putString(MediaMetadata.METADATA_KEY_ARTIST,"OnKlav").build())
        media?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP).setState(if(preparing)PlaybackState.STATE_BUFFERING else if(playing)PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,if(playing)1f else 0f).build())
        val open=PendingIntent.getActivity(this,827,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=Notification.Builder(this,"speech-reading").setSmallIcon(app.onklav.shared.R.drawable.ref_message_speaker).setContentTitle("Read aloud").setContentText(if(preparing)"Preparing included voices…"else if(playing)"Reading offline"else"Paused").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(activeReading).setVisibility(Notification.VISIBILITY_PRIVATE).setStyle(Notification.MediaStyle().setMediaSession(media?.sessionToken).setShowActionsInCompactView(0,1)).addAction(Notification.Action.Builder(null,if(activeReading)"Pause"else"Play",action(if(activeReading)"pause"else"play")).build()).addAction(Notification.Action.Builder(null,"Stop",action("stop")).build()).build()
        startForeground(0x53504b,notification)
    }
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);runCatching{unregisterReceiver(noisy)};runCatching{if(wake?.isHeld==true)wake?.release()};media?.release();media=null;if(active===this){active=null;val previous=owner;owner=null;previous?.stop()};super.onDestroy()}
}
