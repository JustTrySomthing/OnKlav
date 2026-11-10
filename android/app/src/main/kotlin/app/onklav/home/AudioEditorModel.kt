package app.onklav.home

import android.app.Application
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.media.*
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import java.util.UUID
import kotlin.math.*

internal fun audioEditorAccess(c:Context,cap:String="storage.private"):Boolean=runCatching{
    val rows=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray
    (0 until rows.length()).any{val row=rows.getJSONObject(it);row.optBoolean("installed")&&row.optJSONObject("manifest")?.optString("id")=="app.onklav.audioeditor"&&row.optJSONArray("grants")?.let{a->(0 until a.length()).any{a.optString(it)==cap}}==true}
}.getOrDefault(false)
internal class AudioEditorModel(app:Application):AndroidViewModel(app){
    private val root=File(app.filesDir,"audio-editor");private val state=AtomicFile(File(root,"project.json"))
    var source by mutableStateOf<AudioSource?>(null);private set
    var edit by mutableStateOf(AudioEdit(emptyList()));private set
    var peaks by mutableStateOf(FloatArray(0));private set
    var selection by mutableStateOf(0f..1f);private set
    var cursor by mutableFloatStateOf(0f);private set
    var busy by mutableStateOf(false);private set
    var progress by mutableFloatStateOf(0f);private set
    var status by mutableStateOf("");private set
    var error by mutableStateOf(false);private set
    var playing by mutableStateOf(false);private set
    var noiseReady by mutableStateOf(false);private set
    private var profile:FloatArray?=null
    private var task:Job?=null;private var waveTask:Job?=null;private var tick:Job?=null
    private var previewPlan:AudioPreviewPlan?=null;private var prepareWait:kotlinx.coroutines.CancellableContinuation<Unit>?=null
    private val previewIdentities=mutableMapOf<Boolean,String>();private var playStart=0f;private var playEnd=1f
    private var player:MediaPlayer?=null;private var focus:AudioFocusRequest?=null
    private var noisy:BroadcastReceiver?=null
    private val undo=mutableStateListOf<AudioEdit>();private val redo=mutableStateListOf<AudioEdit>();private var committed=edit
    private val worker=Dispatchers.IO.limitedParallelism(1)
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    val duration get()=source?.let{edit.frames.toFloat()/it.rate/edit.tempo}?:0f
    init{restore()}
    private fun authorize(){check(audioEditorAccess(getApplication())){"Audio storage access is off. Restore it in Settings > Apps > Audio Edit."}}
    private fun persist(){authorize();root.mkdirs();val s=source?:return;val j=JSONObject().put("file",s.file.name).put("rate",s.rate).put("channels",s.channels).put("frames",s.frames).put("name",s.name).put("edit",edit.json()).put("noise",profile?.let{JSONArray(it.toList())})
        val out=state.startWrite();try{out.write(j.toString().toByteArray());state.finishWrite(out)}catch(e:Exception){state.failWrite(out);throw e}}
    private fun restore(){if(!root.exists())return
        launch("Restoring audio"){withContext(worker){authorize();if(!state.baseFile.exists()&&!File(root,"project.json.bak").exists())return@withContext
            val j=state.openRead().use{JSONObject(it.readBytes().toString(Charsets.UTF_8))};val name=j.getString("file");require(name.matches(Regex("[a-f0-9-]+\\.pcm")));val f=File(root,name);val rate=j.getInt("rate");val c=j.getInt("channels");val frames=j.getLong("frames");require(rate in 8000..192000&&c in 1..2&&frames in 1..rate*7200L&&f.length()==frames*c*4)
            val s=AudioSource(f,rate,c,frames,j.optString("name","Audio"));val e=audioBoundFades(AudioEdit.parse(j.getJSONObject("edit"),frames),rate);require(e.frames<=rate*7200L){"Saved edit exceeds the two-hour source timeline limit"};val n=j.optJSONArray("noise")?.takeIf{it.length()==AudioEditEngine.FFT/2+1}?.let{a->FloatArray(a.length()){a.getDouble(it).toFloat().also{v->require(v.isFinite()&&v>=0)}}}
            withContext(Dispatchers.Main){source=s;edit=e;committed=e;profile=n;noiseReady=n!=null;refreshWave()};root.listFiles()?.filter{it.name!=s.file.name&&it.name!="project.json"&&it.name!="project.json.bak"}?.forEach{it.deleteRecursively()}
        };status=""}
    }
    private fun launch(label:String,block:suspend CoroutineScope.()->Unit){if(busy)return;stop();busy=true;error=false;status=label;progress=-1f
        task=viewModelScope.launch{try{block()}catch(e:CancellationException){stop();status="Cancelled";throw e}catch(e:Exception){stop();status=e.message?:"Unable to complete. Try again.";error=true}finally{busy=false;progress=0f}}
    }
    
    fun cancel(){if(!busy)commit();task?.cancel();stop()}
    fun select(range:ClosedFloatingPointRange<Float>){val minimum=1f/edit.frames.coerceAtLeast(1);val start=range.start.coerceIn(0f,(1-minimum).coerceAtLeast(0f));selection=start..range.endInclusive.coerceIn(start+minimum,1f)}
    fun seek(value:Float){val next=value.coerceIn(0f,1f);val plan=previewPlan
        if(plan!=null&&next in plan.startFraction..plan.endFraction){player?.let{runCatching{it.seekTo((it.duration*(next-plan.startFraction)/(plan.endFraction-plan.startFraction)).toInt())}}}else if(player!=null)stop()
        cursor=next
    }
    fun open(uri:Uri){launch("Opening audio"){
        var imported:AudioSource?=null;var adopted=false
        try{
            val next=withContext(worker){authorize();root.mkdirs();val id=UUID.randomUUID().toString();val input=File(root,"$id.input");val pcm=File(root,"$id.pcm")
                try{
                    val resolver=getApplication<Application>().contentResolver
                    val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:"Audio"
                    resolver.openInputStream(uri).use{ins->requireNotNull(ins);input.outputStream().buffered().use{out->val b=ByteArray(65536);var count=0L;while(true){currentCoroutineContext().ensureActive();authorizeThrottled();val n=ins.read(b);if(n<0)break;count+=n;require(count<=1_000_000_000){"Choose a file smaller than 1 GB"};require(root.usableSpace>32_000_000){"Not enough free space"};out.write(b,0,n)}}}
                    val job=currentCoroutineContext();AudioEditMedia.decode(input,pcm,name,{job.ensureActive()},{v->if((v*100).toInt()!=(progress*100).toInt()){progress=v;status="Opening audio · ${(v*100).toInt()}%"}}).also{imported=it}
                }catch(e:Exception){pcm.delete();throw e}finally{input.delete()}
            }
            currentCoroutineContext().ensureActive()
            // Cancellation must never roll back memory/delete PCM after its durable JSON commit.
            // Only this small metadata promotion is non-cancellable; decode/render remain cancellable.
            withContext(NonCancellable){
                val old=source;val oldEdit=edit;val oldCommitted=committed;val oldProfile=profile
                source=next;edit=AudioEdit(listOf(AudioRegion(0,next.frames)));profile=null;noiseReady=false
                try{withContext(worker){persist()}}catch(e:Exception){source=old;edit=oldEdit;committed=oldCommitted;profile=oldProfile;noiseReady=oldProfile!=null;throw e}
                adopted=true;committed=edit
                old?.file?.takeIf{it!=next.file}?.delete();undo.clear();redo.clear();previewIdentities.clear();selection=0f..1f;cursor=0f;refreshWave();status=""
            }
        }catch(e:CancellationException){if(adopted)status="Audio opened"else throw e}
        finally{if(!adopted)imported?.file?.delete()}
    }}
    private fun refreshWave(){waveTask?.cancel();val s=source?:return;val e=edit;waveTask=viewModelScope.launch{try{val p=withContext(worker){val job=currentCoroutineContext();AudioEditEngine.peaks(s,e){job.ensureActive()}};peaks=p}catch(_:CancellationException){}catch(e:Exception){status=e.message?:"Unable to show waveform";error=true}}}
    fun change(next:AudioEdit){if(busy)return;stop();val value=source?.let{audioBoundFades(next,it.rate)}?:next
        val mapped=edit.regions!=value.regions||edit.reverse!=value.reverse
        edit=value;status="";error=false;if(mapped)refreshWave()
    }
    fun commit(){if(edit==committed)return;val previous=committed
        runCatching{persist()}.onSuccess{undo.add(previous);if(undo.size>40)undo.removeAt(0);redo.clear();committed=edit}.onFailure{status="Draft not saved: ${it.message}";error=true}
    }
    fun apply(next:AudioEdit){if(busy||source==null)return;change(next);commit();selection=0f..1f;cursor=0f}
    fun operation(op:String){if(busy||source==null)return;val start=(selection.start*edit.frames).toLong();val end=(selection.endInclusive*edit.frames).toLong().coerceAtMost(edit.frames);val a=if(edit.reverse)edit.frames-end else start;val b=if(edit.reverse)edit.frames-start else end
        if(b-a<1)return
        val next=when(op){"Trim"->edit.trim(a,b);"Cut"->edit.cut(a,b);"Silence"->edit.silence(a,b);else->edit.duplicate(a,b)}
        if(next.frames<1){status="Keep at least a small part of the audio";error=true;return};if(next.regions.size>512||next.frames>source!!.rate*7200L){status="This edit exceeds the project limit";error=true;return};apply(next)
    }
    fun history(back:Boolean){if(busy)return;stop();val a=if(back)undo else redo;val b=if(back)redo else undo;if(a.isEmpty())return
        val previous=edit;val value=a.last();edit=value
        runCatching{persist()}.onSuccess{
            a.removeAt(a.lastIndex);b.add(previous);committed=value;selection=0f..1f;cursor=0f;status="";error=false
            if(previous.regions!=value.regions||previous.reverse!=value.reverse)refreshWave()
        }.onFailure{edit=previous;status="Draft not saved: ${it.message}";error=true}
    }
    fun learnNoise(){val s=source?:return;val e=edit;val a=(selection.start*e.frames).toLong();val b=(selection.endInclusive*e.frames).toLong();launch("Learning noise"){
        val p=withContext(worker){authorize();val job=currentCoroutineContext();AudioEditEngine.profile(s,e,a,b){job.ensureActive()}};profile=p;noiseReady=true;persist();status="Noise sample learned"}}
    private suspend fun render(e:AudioEdit,file:File,context:AudioEditEngine.Context?=null):AudioEditEngine.Result{val s=requireNotNull(source);return withContext(worker){authorize();AudioEditEngine.requireWaveSize(s.channels,(e.frames/e.tempo).toLong().coerceAtLeast(1));val expected=(e.frames/e.tempo).toLong()*s.channels*4*4;require(root.usableSpace>expected+32_000_000){"Not enough free space. Trim the audio or free some space first."};val job=currentCoroutineContext();var last=0L
        AudioEditEngine.render(s,e,file,File(root,"work"),profile,{label,v->val now=System.nanoTime();if(now-last>100_000_000){last=now;status="$label · ${(v*100).toInt()}%";progress=v}},{job.ensureActive();authorizeThrottled()},context)}}
    private var accessAt=0L
    private fun authorizeThrottled(){val now=System.nanoTime();if(now-accessAt>500_000_000){authorize();accessAt=now}}
    fun preview(original:Boolean=false){if(busy)return;if(playing&&!original){stop();status="Paused";return};val s=source?:return
        checkPlay()?.let{status=it;error=true;return}
        commit();waveTask?.cancel()
        val plan=AudioPreviewPlan.make(s,edit,selection,cursor,original)
        val e=plan.edit
        launch(if(original)"Preparing original" else "Preparing preview"){
            val wav=File(root,if(original)"original.wav" else "preview.wav");val identity="${s.file.name}:${e.hashCode()}:${plan.start}:${plan.end}:${plan.context}:${profile?.contentHashCode()}"
            if(previewIdentities[original]!=identity||!wav.exists()){previewIdentities.remove(original);render(e,wav,plan.context);previewIdentities[original]=identity};authorize();checkPlay()?.let{kotlin.error(it)}
            previewPlan=plan;playStart=plan.startFraction;playEnd=plan.endFraction;cursor=playStart
            // Request only after rendering so another player is not held silent while processing.
            val audio=getApplication<Application>().getSystemService(AudioManager::class.java)
            val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            val playbackToken=Any()
            val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener{if(it!=AudioManager.AUDIOFOCUS_GAIN&&playbackOwner===playbackToken)stop()}.build()
            playbackOwner=playbackToken;HostSpeechOwner.claim(this@AudioEditorModel){if(playbackOwner===playbackToken)stop()};check(audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Audio focus unavailable"};focus=request
            val p=MediaPlayer();player=p;p.setAudioAttributes(attributes);p.setDataSource(wav.path)
            p.setOnCompletionListener{if(player===it){stop();cursor=playEnd;status="Preview finished · tap Play to continue"}}
            try{withTimeout(15000){suspendCancellableCoroutine<Unit>{continuation->
                prepareWait=continuation;continuation.invokeOnCancellation{if(prepareWait===continuation)prepareWait=null};p.setOnPreparedListener{if(player===it&&continuation.isActive){prepareWait=null;continuation.resumeWith(Result.success(Unit))}}
                p.setOnErrorListener{_,what,extra->if(player===p&&continuation.isActive){prepareWait=null;continuation.resumeWith(Result.failure(IllegalStateException("Unable to prepare audio ($what / $extra)")))};true}
                p.prepareAsync()
            }}}catch(e:TimeoutCancellationException){throw IllegalStateException("Audio preview timed out. Try saving a WAV copy.",e)}
            p.setOnErrorListener{_,_,_->if(player===p){stop();status="Unable to preview. Try saving a WAV copy.";error=true};true}
            check(player===p&&playbackOwner===playbackToken){"Preview was interrupted"}
            val routeReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action==AudioManager.ACTION_AUDIO_BECOMING_NOISY&&playbackOwner===playbackToken){stop();status="Preview paused · headphones disconnected";error=false}}}
            noisy=routeReceiver
            if(Build.VERSION.SDK_INT>=33)getApplication<Application>().registerReceiver(routeReceiver,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED)
            else getApplication<Application>().registerReceiver(routeReceiver,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            p.seekTo(0);p.start();playing=true;status=if(original)"Original · up to 12 s" else "Preview · up to 12 s"
            tick=viewModelScope.launch{while(isActive&&playing){cursor=runCatching{plan.fraction(p.currentPosition.toLong(),p.duration.toLong())}.getOrDefault(0f);if(cursor>=playEnd){stop();cursor=playEnd;status="Preview finished · tap Play to continue";break};delay(40)}}
        }
    }
    private fun checkPlay():String?=if(audioEditorAccess(getApplication(),"audio.playback"))null else "Audio playback access is off. Restore it in Settings > Apps > Audio Edit."
    private var playbackOwner:Any?=null
    fun stop(){tick?.cancel();tick=null;playing=false;playbackOwner=null;prepareWait?.cancel();prepareWait=null
        val receiver=noisy;noisy=null;receiver?.let{runCatching{getApplication<Application>().unregisterReceiver(it)}}
        val old=player;player=null;previewPlan=null
        if(old!=null){runCatching{old.setOnPreparedListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.stop()};runCatching{old.release()}}
        val previous=focus;focus=null;HostSpeechOwner.release(this);runCatching{previous?.let{getApplication<Application>().getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)}}
    }

    fun export(uri:Uri,aac:Boolean,mp3:Boolean=false){if(busy)return;commit();val s=source?:return;val e=edit
        if(mp3&&duration>7200f){status="MP3 export is limited to two hours. Trim the track, increase tempo, or save as WAV.";error=true;return}
        launch("Preparing export"){
        val wav=File(root,"export.wav");val compressed=File(root,if(mp3)"export.mp3"else"export.m4a")
        try{val result=render(e,wav);withContext(worker){authorize();val job=currentCoroutineContext();val file=if(mp3){app.onklav.shared.Mp3Codec.encode(wav,compressed,192,{job.ensureActive();authorizeThrottled()}){v->status="Encoding MP3 · ${(v*100).toInt()}%";progress=v};compressed}else if(aac){AudioEditMedia.aac(wav,compressed,s.rate,s.channels,{job.ensureActive();authorizeThrottled()}){v->status="Encoding M4A · ${(v*100).toInt()}%";progress=v};compressed}else wav
            authorize();writeAudioExport(getApplication<Application>().contentResolver,uri,file,{job.ensureActive();authorizeThrottled()},{job.ensureActive();authorize()});awaitFiledExport(getApplication(),uri)}
            status=if(result.clipped>0)"Copy saved · clipping detected; enable Prevent clipping" else "Copy saved · peak ${"%.1f".format(result.peakDb)} dBFS"
        }catch(e:Exception){if(e is CancellationException)throw e
            throw IllegalStateException("Unable to save: ${e.message}. Try WAV or another folder.",e)}finally{wav.delete();compressed.delete()}
    }}
    override fun onCleared(){cancel();waveTask?.cancel();super.onCleared()}
}
