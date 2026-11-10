package app.onklav.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class VoiceAudio(val bytes:ByteArray,val mime:String,val millis:Int=0) {
    fun json()=JSONObject().put("mime",mime).put("bytes",JSONArray().apply {bytes.forEach {put(it.toInt() and 255)}})
    companion object { fun from(v:JSONObject):VoiceAudio {val raw=v.getJSONArray("bytes");require(raw.length() in 1..65536);return VoiceAudio(ByteArray(raw.length()){val n=raw.getInt(it);require(n in 0..255);n.toByte()},v.getString("mime"),v.optInt("millis"))} }
}
internal interface VoiceBackend {
    fun record(allowed:()->Boolean,limit:()->Unit,error:(String)->Unit)
    fun finish():VoiceAudio
    fun cancelRecord()
    fun play(audio:VoiceAudio,ready:()->Boolean,done:(String?)->Unit)
    fun stopPlay()
    fun close()
    fun beep() {}
}
internal interface VoiceFocus { fun acquire():Boolean;fun release() }
private class AndroidVoiceFocus(context:Context,loss:()->Unit):VoiceFocus {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var held=false
    private val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener {if(it<=0) loss()}.build()
    override fun acquire():Boolean {if(manager.mode!=AudioManager.MODE_NORMAL)return false;held=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return held}
    override fun release() {if(held){held=false;manager.abandonAudioFocusRequest(request)}}
}
/** Only private cache files; no URI/path from a guest reaches MediaRecorder/MediaPlayer. */
@Suppress("DEPRECATION")
internal class AndroidVoiceBackend(private val context:Context):VoiceBackend {
    private val main=Handler(Looper.getMainLooper())
    private var recorder:MediaRecorder?=null
    private var recording:File?=null
    private var format="audio/amr"
    private var player:MediaPlayer?=null
    private var playing:File?=null
    init {context.cacheDir.listFiles()?.filter {it.isFile&&it.name.startsWith("sdk-voice-")&&System.currentTimeMillis()-it.lastModified()>86400000}?.forEach {it.delete()} }
    override fun record(allowed:()->Boolean,limit:()->Unit,error:(String)->Unit) {
        cancelRecord();var failure:Exception?=null
        for(wide in listOf(true,false)) {
            val file=File.createTempFile("sdk-voice-",".amr",context.cacheDir);val r=MediaRecorder()
            try {
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(if(wide) MediaRecorder.OutputFormat.AMR_WB else MediaRecorder.OutputFormat.AMR_NB)
                r.setAudioEncoder(if(wide) MediaRecorder.AudioEncoder.AMR_WB else MediaRecorder.AudioEncoder.AMR_NB)
                r.setAudioChannels(1);r.setAudioSamplingRate(if(wide)16000 else 8000);r.setAudioEncodingBitRate(if(wide)12650 else 12200)
                r.setMaxDuration(12000);r.setMaxFileSize(65536);r.setOutputFile(file.absolutePath)
                r.setOnInfoListener {_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) main.post {if(recorder===r) limit()} }
                r.setOnErrorListener {_,_,_->main.post {if(recorder===r) error("Microphone recording failed")} }
                r.prepare();check(allowed()) {"Recording authorization changed"};r.start();recorder=r;recording=file;format=if(wide)"audio/amr-wb" else "audio/amr";return
            } catch(e:Exception){failure=e;runCatching {r.release()};file.delete()}
        }
        throw IllegalStateException("Microphone could not start",failure)
    }
    override fun finish():VoiceAudio {
        val r=recorder ?: error("Recording is not active");val file=requireNotNull(recording);recorder=null;recording=null
        try {r.setOnInfoListener(null);r.setOnErrorListener(null);r.stop();require(file.length() in 1..65536);return VoiceAudio(file.readBytes(),format)}
        finally {runCatching {r.release()};file.delete()}
    }
    override fun cancelRecord(){val r=recorder;recorder=null;runCatching {r?.reset()};runCatching {r?.release()};recording?.delete();recording=null}
    override fun play(audio:VoiceAudio,ready:()->Boolean,done:(String?)->Unit) {
        stopPlay();require(audio.bytes.size in 1..65536)
        val file=File.createTempFile("sdk-voice-",".amr",context.cacheDir);val p=MediaPlayer()
        try {
            file.writeBytes(audio.bytes);player=p;playing=file
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setDataSource(file.absolutePath)
            p.setOnPreparedListener {if(player===p)try {if(ready())p.start()else stopPlay()}catch(e:Exception){stopPlay();done("Voice playback could not start")} }
            p.setOnCompletionListener {if(player===p){stopPlay();done(null)}}
            p.setOnErrorListener {_,_,_->if(player===p){stopPlay();done("Voice clip could not be played")};true}
            p.prepareAsync()
        } catch(e:Exception){if(player===p)stopPlay()else{runCatching {p.release()};file.delete()};throw e}
    }
    override fun stopPlay(){val p=player;player=null;runCatching {p?.release()};playing?.delete();playing=null}
    override fun close(){cancelRecord();stopPlay()}
    override fun beep(){val tone=ToneGenerator(AudioManager.STREAM_MUSIC,65);tone.startTone(ToneGenerator.TONE_PROP_ACK,80);main.postDelayed({tone.release()},150)}
}
private object VoiceOwner {
    private var owner by mutableStateOf<SdkVoiceController?>(null)
    fun idle()=owner==null
    fun claim(next:SdkVoiceController){HostSpeechOwner.claim(next){next.cancel("Another audio control was opened")};if(owner!==next){owner?.cancel("Another voice control was opened");owner=next}}
    fun release(current:SdkVoiceController){if(owner===current)owner=null;HostSpeechOwner.release(current)}
}
/** Resources/drafts remain host owned. Stale callbacks never persist audio or heard proof. */
internal class SdkVoiceController(
    private val context:Context,private val widget:String,private val capture:Boolean,
    private val host:(String,JSONObject)->JSONObject,
    private val backend:VoiceBackend=AndroidVoiceBackend(context),focus:VoiceFocus?=null,
) {
    private val focus=focus ?: AndroidVoiceFocus(context){cancel("Audio interrupted; unsent recording discarded")}
    private val main=Handler(Looper.getMainLooper())
    private var generation=0L;private var closed=false;private var foreground=true;private var key="";private var captureId="";private var started=0L;private var playStarted=0L
    var waiting by mutableStateOf(false);private set
    var warming by mutableStateOf(false);private set
    private var live=false;private var automatic=false;private var floorLease=""
    var recording by mutableStateOf(false);private set
    var playing by mutableStateOf(false);private set
    var draft by mutableStateOf<VoiceAudio?>(null);private set
    var status by mutableStateOf("");private set
    var remaining by mutableIntStateOf(12);private set
    private val monitor=object:Runnable {override fun run(){if(!recording&&!playing&&draft==null)return;if(!checkPermission())return
        if(playing&&SystemClock.elapsedRealtime()-playStarted>=20000){cancel("Voice playback timed out");return}
        if(recording){remaining=(12-((SystemClock.elapsedRealtime()-started)/1000).toInt()).coerceAtLeast(0);if(SystemClock.elapsedRealtime()-started>=12000){finish();return}}
        main.postDelayed(this,250)
    }}
    fun microphoneAllowed()=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    fun permit():Boolean=try {check(!closed&&foreground){"Open the app in the foreground"};val p=host("host_voice",JSONObject().put("permission_only",true));live=p.optBoolean("live");true}catch(e:Exception){status=e.message ?: "Enable voice access in Settings > Apps";false}
    private fun authorized(read:Boolean=false,preview:Boolean=false):JSONObject {
        check(!closed&&foreground){"Voice is only available in the foreground"}
        if(capture)check(microphoneAllowed()){ "Microphone permission is off" }
        val v=host("host_voice",JSONObject().put("read",read).put("preview",preview))
        if(key.isNotEmpty())check(v.getString("key")==key){"Recipient changed; recording discarded"}
        return v
    }
    fun checkPermission():Boolean {if(key.isEmpty())return true;return try {authorized(preview=capture&&playing);true}catch(e:Exception){cancel(e.message ?: "Voice permission changed");false}}
    fun permissionResult(allowed:Boolean){status=if(allowed)"Microphone allowed. Tap Record when ready." else "Microphone permission was not granted"}
    fun record() {
        cancel();if(!permit())return
        try {
            check(microphoneAllowed()){ "Allow Android microphone access first" }
            if(live) {
                floorLease=host("voice_floor_request",JSONObject()).getString("lease");waiting=true;status="Waiting for microphone…"
                val run=++generation;val since=SystemClock.elapsedRealtime()
                val await=object:Runnable {override fun run(){
                    if(run!=generation||!waiting||!foreground||closed)return
                    val permission=runCatching {authorized()}.getOrNull()
                    if(permission!=null){waiting=false;startCapture(permission);return}
                    if(SystemClock.elapsedRealtime()-since>=10000){cancel("Microphone request timed out");return}
                    main.postDelayed(this,200)
                }};main.post(await)
            }else startCapture(authorized())
        }catch(e:Exception){cancel(e.message ?: "Recording could not start")}
    }
    private fun startCapture(permission:JSONObject) {
        try {
            key=permission.getString("key");automatic=permission.optBoolean("auto_send");captureId=UUID.randomUUID().toString().replace("-","")
            VoiceOwner.claim(this);check(focus.acquire()){ "Audio is busy. Try again." }
            val run=++generation
            fun start() {
                if(run!=generation||!foreground||closed)return
                if(!checkPermission())return
                warming=false;recording=true;remaining=12;started=SystemClock.elapsedRealtime()
                try {backend.record({run==generation&&recording&&checkPermission()},{if(run==generation&&recording)finish()},{if(run==generation)cancel(it)})
                    if(run==generation&&recording){status="Speak now";main.postDelayed(monitor,250)}
                }catch(e:Exception){cancel(e.message ?: "Microphone could not start")}
            }
            if(live){warming=true;status="Getting microphone ready…";if(permission.optBoolean("beeps"))backend.beep();main.postDelayed({start()},180)}else start()
        }catch(e:Exception){cancel(e.message ?: "Recording could not start")}
    }
    fun finish() {
        if(!recording)return
        if(!checkPermission())return
        generation++;main.removeCallbacks(monitor);recording=false
        try {
            val raw=backend.finish();focus.release()
            val clean=host("voice_prepare",raw.json().put("key",key));draft=VoiceAudio.from(clean)
            status="Review recording · ${draft!!.millis/1000.0} seconds";main.postDelayed(monitor,250);if(live&&automatic)queue()
        }catch(e:Exception){cancel(e.message ?: "Recording could not be saved")}
    }
    fun queue(send:Boolean=false) {
        val audio=draft ?: return
        if(!checkPermission())return
        try {backend.stopPlay();playing=false;focus.release();host("voice_commit",audio.json().put("key",key).put("capture",captureId).put("send",send));val sent=live||send;cancel();status=if(sent)"Queued for carrier sending"else"Queued locally. Carrier sending is not available yet."}
        catch(e:Exception){status=e.message ?: "Could not queue recording"}
    }
    fun autoPlay() {
        if(!foreground||closed||playing||recording||draft!=null||!VoiceOwner.idle())return
        if(runCatching {host("host_voice",JSONObject()).optBoolean("auto_play")}.getOrDefault(false))play()
    }
    fun play() {
        if(playing){stopPlayback();return}
        if(recording)return
        try {
            val permission=authorized(read=!capture,preview=capture);key=permission.getString("key")
            val audio=if(capture)requireNotNull(draft){"Record something first"}else VoiceAudio.from(permission.getJSONObject("audio"))
            VoiceOwner.claim(this);check(focus.acquire()){ "Audio is busy. Try again." }
            val run=++generation;playing=true;playStarted=SystemClock.elapsedRealtime();status="Preparing voice clip…"
            backend.play(audio,{val allowed=run==generation&&playing&&checkPermission();if(allowed)status="Playing";allowed},{error->
                if(run==generation&&playing&&checkPermission()) {
                    playing=false;main.removeCallbacks(monitor);focus.release()
                    if(error!=null)status=error else try {if(!capture)host("voice_played",JSONObject().put("key",key));status=if(capture)"Review recording"else"Playback complete"}catch(e:Exception){status=e.message ?: "Could not update clip"}
                    if(draft!=null)main.postDelayed(monitor,250)else{VoiceOwner.release(this);key=""}
                }
            });main.postDelayed(monitor,250)
        }catch(e:Exception){if(capture){stopPlayback();status=e.message ?: "Preview unavailable"}else cancel(e.message ?: "Playback unavailable")}
    }
    fun stopPlayback(){generation++;backend.stopPlay();playing=false;focus.release();main.removeCallbacks(monitor);status=if(draft!=null)"Review recording"else"Playback stopped";if(draft!=null)main.postDelayed(monitor,250)else{VoiceOwner.release(this);key=""}}
    fun setForeground(value:Boolean){foreground=value;if(!value)cancel("Unsent recording discarded when leaving")}
    fun cancel(message:String="") {val release=live&&(waiting||warming||recording||draft!=null);generation++;waiting=false;warming=false;main.removeCallbacks(monitor);backend.cancelRecord();backend.stopPlay();focus.release();VoiceOwner.release(this);recording=false;playing=false;draft=null;key="";captureId="";status=message;if(release&&floorLease.isNotEmpty())runCatching {host("voice_floor_release",JSONObject().put("lease",floorLease))};floorLease=""}
    fun close(){if(!closed){cancel();closed=true;backend.close()}}
}

@Composable internal fun SdkVoiceComponent(node:JSONObject,model:SdkAppModel,modifier:Modifier=Modifier) {
    val context=LocalContext.current;val id=node.getString("id");val capture=node.getString("kind")=="voice_capture";val lifecycle=LocalLifecycleOwner.current
    val player=remember(id,model){SdkVoiceController(context,id,capture,{action,args->model.voiceRequest(action,id,args)})}
    val carrierAvailable=model.carrierVoiceEligible&&(model.isBuiltinVoice()||node.optBoolean("live"))&&MessagingTransport.carrierEnabled(context)
    var sendReview by remember {mutableStateOf(false)}
    val epoch by AppSdk.epoch.collectAsState()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){player.permissionResult(it)}
    DisposableEffect(player,lifecycle){player.setForeground(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED));val observer=LifecycleEventObserver {_,event->when(event){Lifecycle.Event.ON_RESUME->player.setForeground(true);Lifecycle.Event.ON_PAUSE,Lifecycle.Event.ON_STOP->player.setForeground(false);else->{}}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);player.close()}}
    LaunchedEffect(epoch,model.frame){player.checkPermission()}
    var autoAttempted by remember(id,node.optString("clip")){mutableStateOf(false)}
    val idle=VoiceOwner.idle()
    LaunchedEffect(node.optString("clip"),idle){if(!capture&&node.optBoolean("autoplay")&&idle&&!autoAttempted){autoAttempted=true;player.autoPlay()}}
    Column(modifier.fillMaxWidth().testTag("sdk-$id").padding(vertical=8.dp)) {
        if(!capture){Text(node.optString("text"),fontSize=16.sp);Text(node.optString("detail"),fontSize=13.sp)}
        if(capture) {
            if(!node.optBoolean("live"))Text("Voice message to ${displayPhone(node.optString("peer"))}",fontSize=14.sp)
            fun press() {
                if(player.recording)player.finish()else if(player.waiting||player.warming)player.cancel("Microphone request cancelled")
                else if(player.permit()){if(player.microphoneAllowed())player.record()else permission.launch(Manifest.permission.RECORD_AUDIO)}
            }
            if(node.optBoolean("live")) {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Box(Modifier.widthIn(max=220.dp).fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.background,CircleShape).border(7.dp,MaterialTheme.colorScheme.primary.copy(alpha=.6f),CircleShape)
                        .testTag("sdk-$id-record").semantics {contentDescription="Walkie Talkie microphone";role=Role.Button;onClick {press();true}}
                        .pointerInput(player){detectTapGestures(onPress={if(player.draft==null){press();tryAwaitRelease();if(player.recording)player.finish()else if(player.waiting||player.warming)player.cancel("Microphone request cancelled")}})},contentAlignment=Alignment.Center) {
                        if(player.recording)CircularProgressIndicator(progress={player.remaining/12f},modifier=Modifier.fillMaxSize(),strokeWidth=7.dp,color=MaterialTheme.colorScheme.primary)
                        Column(horizontalAlignment=Alignment.CenterHorizontally){
                            Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_app_walkie),null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(if(player.recording)"SPEAK NOW"else if(player.waiting)"WAITING"else if(player.warming)"GETTING READY"else if(player.draft!=null)"REVIEW"else"HOLD TO TALK",color=MaterialTheme.colorScheme.primary,fontSize=16.sp)
                        }
                    }
                }
            }else ChoiceButton(if(player.recording)"Stop · ${player.remaining}s"else if(player.waiting||player.warming)"Cancel microphone request"else"Record voice message",Modifier.testTag("sdk-$id-record"),enabled=!model.busy&&player.draft==null,outlined=false) {press()}
            if(player.draft!=null){
                ChoiceButton(if(player.playing)"Stop preview"else"Preview",Modifier.testTag("sdk-$id-preview"),outlined=false){player.play()}
                ChoiceButton(if(node.optBoolean("live"))"Send voice clip"else if(carrierAvailable)"Send voice message"else"Queue voice clip",Modifier.testTag("sdk-$id-queue"),enabled=!model.busy,outlined=false){if(!node.optBoolean("live")&&carrierAvailable)sendReview=true else player.queue()}
                if(!node.optBoolean("live")&&!carrierAvailable)Text(if(model.carrierVoiceEligible)"Saved locally. Enable carrier phone testing in Setup to send."else"Saved locally. Carrier sending is not available for this app.",fontSize=13.sp)
            }
            if(player.recording||player.draft!=null)ChoiceButton("Discard",Modifier.testTag("sdk-$id-discard"),outlined=false){player.cancel("Recording discarded")}
        }else Row(Modifier.fillMaxWidth()) {
            ChoiceButton(if(player.playing)"Stop"else"Listen",Modifier.weight(1f).testTag("sdk-$id-listen"),outlined=false){player.play()}
            node.optString("delete").takeIf{it.isNotBlank()}?.let{action->CompactAction(Icons.Outlined.Delete,"Delete "+node.optString("text"),enabled=!model.busy&&model.pending==null){model.action(action)}}
            if(node.has("retry"))ChoiceButton("Retry",Modifier.weight(1f),outlined=false){model.action(node.getString("retry"))}
        }
        if(player.status.isNotBlank())Text(player.status,Modifier.testTag("sdk-$id-status"),fontSize=14.sp)
    }
    if(sendReview)ReferenceDialog("Send voice message?",{sendReview=false},footer={
        DialogAction("Cancel"){sendReview=false}
        DialogAction("Send",primary=true){sendReview=false;player.queue(true)}
    }){Text("Send this recording to ${node.optString("peer")}?",Modifier.padding(20.dp))}

}
