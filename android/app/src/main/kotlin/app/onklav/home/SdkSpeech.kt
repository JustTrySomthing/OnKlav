package app.onklav.home

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import kotlinx.coroutines.flow.collect

/** UTF-16 positions match Android selection/range callbacks; no split surrogate pairs. */
internal object SpeechPlan {
    fun boundary(text: String, at: Int): Int = at.coerceIn(0,text.length).let { if(it>0 && it<text.length && text[it].isLowSurrogate() && text[it-1].isHighSurrogate()) it-1 else it }
    fun start(text: String, at: Int): Int {
        var p=boundary(text,at)
        while(p>0 && p<text.length && !text[p-1].isWhitespace() && text[p-1] !in ".!?。！？") p--
        while(p<text.length && text[p].isWhitespace()) p++
        return boundary(text,p)
    }

}
internal fun speechText(text:String):String = text.lineSequence().joinToString("\n") { line ->
    if(line.count{it.isLetter()}>=4 && line.none{it.isLowerCase()}) line.map{if(it in 'A'..'Z')it.lowercaseChar()else it}.joinToString("") else line
}
internal fun speechRate(value:Float)=if(value.isFinite())value.coerceIn(.75f,1.5f)else 1.0f
internal data class SpeechVoice(val id:String,val label:String)
internal interface SpeechBackend {
    fun initialize(done:(Boolean,List<SpeechVoice>)->Unit)
    fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean
    fun stop()
    fun close()
}
/** Host-owned audio resource. Every start/chunk/callback rechecks the live Rust session. */
internal class SdkSpeechController(
    private val context:Context, private val appId:String, private val authorize:(String)->Unit,
    private val backend:SpeechBackend=NeuralSpeechBackend(context),
) {
    private val prefs=context.getSharedPreferences(if(appId in setOf("app.onklav.reader","app.onklav.ebooks","host.messages"))"reader"else"sdk-speech-$appId",0).also{shared->
        if(appId=="app.onklav.reader"){
            val previous=context.getSharedPreferences("sdk-speech-$appId",0)
            val edit=shared.edit();if(!shared.contains("voice")&&previous.contains("voice"))edit.putString("voice",previous.getString("voice",""));if(!shared.contains("rate")&&previous.contains("rate"))edit.putFloat("rate",previous.getFloat("rate",1f));edit.apply()
        }
    }
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { if(it<=0) {bookContinuous=false;stop()} }.build()
    private var generation=0L
    private var closed=false
    private var initialized=false
    private var setupGeneration=0L
    private var document=""
    private var widget=""
    private var queued by mutableStateOf<Pair<String,String>?>(null)
    val isPreparing get()=queued!=null
    var messageKey by mutableStateOf<String?>(null);private set
    private var settingsSnapshot:Pair<String,Int>?=null
    var onDocumentComplete:(()->Unit)?=null
    val backgroundEligible get()=appId in setOf("app.onklav.reader","app.onklav.ebooks")
    var bookContinuous=false
    var bookWidget=""
    var bookKey=""
    var completed by mutableLongStateOf(0L);private set
    var position by mutableIntStateOf(0);private set
    var playing by mutableStateOf(false);private set
    var ready by mutableStateOf(false);private set
    var voices by mutableStateOf(emptyList<SpeechVoice>());private set
    var voice by mutableStateOf(prefs.getString("voice","").orEmpty());private set
    var rate by mutableFloatStateOf(speechRate(prefs.getFloat("rate",1.0f)));private set
    var error by mutableStateOf<String?>(null);private set
    fun prepare(id:String) {
        if(closed || initialized || !allowed(id)) return
        initialized=true
        val setup=++setupGeneration
        backend.initialize { ok,list ->
            if(!closed && setup==setupGeneration) {
                voices=list;ready=ok
                if(list.none { it.id==voice }) voice=list.firstOrNull()?.id.orEmpty()
                val next=queued;queued=null
                if(ok) { error=null;if(next!=null) play(next.first,next.second) }
                else { stop();error="Could not prepare the included English voices. Tap Retry Voice Setup." }
            }
        }
    }
    fun retry(id:String) { stop();setupGeneration++;backend.close();initialized=false;ready=false;error=null;prepare(id) }
    private fun allowed(id:String):Boolean=try { authorize(id);true } catch(e:Exception) { bookContinuous=false;stop();error=e.message ?: "Speech Access Is Off";false }
    fun setDocument(text:String) { if(text!=document) { stop(keepNotification=bookContinuous&&backgroundEligible);document=text.take(SpeechPlan.boundary(text,text.length.coerceAtMost(50000)));position=0 } }
    fun stop(keepNotification:Boolean=false) { queued=null;generation++;backend.stop();playing=false;runCatching{audio.abandonAudioFocusRequest(focus)};HostSpeechOwner.release(this);if(keepNotification&&backgroundEligible){HostSpeechOwner.claim(this){stop()};SpeechPlaybackService.refresh(this)}else SpeechPlaybackService.release(context,this) }
    fun pauseFromSystem(){bookContinuous=false;stop(keepNotification=true)}
    fun resumeFromSystem(){if(!closed&&document.isNotBlank()&&!playing){bookContinuous=bookWidget.isNotEmpty();play(document,widget)}}
    fun onBackground(){if(!backgroundEligible||!allowed(widget)||(!playing&&!isPreparing&&!SpeechPlaybackService.owns(this))){bookContinuous=false;stop()}}
    fun checkPermission() { if(widget.isNotBlank()) allowed(widget) }
    fun move(text:String,at:Int,id:String) { val resume=playing;setDocument(text);stop();position=SpeechPlan.start(document,at);if(resume && position<document.length) play(document,id) }
    fun play(text:String,id:String) {
        if(closed) return
        setDocument(text);prepare(id)
        if(!allowed(id)) return
        if(playing) { stop();return }
        if(document.isBlank()) { error="Add Some Words to Read";return }
        widget=id
        if(!ready) {
            if(error==null) { HostSpeechOwner.claim(this){stop()};queued=text to id
                if(backgroundEligible)try{SpeechPlaybackService.attach(context,this)}catch(e:Exception){stop();error="Could not prepare background reading. Try again."}
            }
            return
        }
        if(position>=document.length) position=0
        HostSpeechOwner.claim(this){stop()}
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { HostSpeechOwner.release(this);error="Audio Is Busy. Try Again.";return }
        voice=prefs.getString("voice",voice).orEmpty().takeIf{chosen->voices.any{it.id==chosen}}?:voice
        rate=speechRate(prefs.getFloat("rate",rate))
        widget=id;error=null;playing=true
        if(backgroundEligible)try{SpeechPlaybackService.attach(context,this)}catch(e:Exception){stop();error="Could not start background reading. Try again.";return}
        val run=++generation;speakChunk(run)
    }
    /** Each message has its own identity, even when two messages have identical text. */
    fun isMessageActive(key:String)=messageKey==key&&(playing||queued!=null)
    fun playMessage(text:String,key:String) {
        if(messageKey==key&&(playing||queued!=null)){stop();return}
        stop();position=0;play(text,"message");messageKey=key
    }
    private fun speakChunk(run:Long) {
        if(closed || run!=generation || !playing || !allowed(widget)) return
        if(position>=document.length) { stop(keepNotification=bookContinuous&&onDocumentComplete!=null);position=document.length;completed++;if(bookContinuous)onDocumentComplete?.invoke();return }
        val offset=SpeechPlan.boundary(document,position);val end=document.length
        val reading=SpeechReading.prepare(document.substring(offset,end))
        if(reading.text.isBlank()){position=end;speakChunk(run);return}
        val accepted=backend.speak(reading.text,voice,rate,"$run-$offset",{ relative ->
            if(!closed && run==generation && playing && allowed(widget)) position=SpeechPlan.boundary(document,(offset+reading.original(relative)).coerceIn(offset,end))
        },{ failure ->
            if(!closed && run==generation && playing && allowed(widget)) {
                if(failure!=null) { stop();error=failure } else { position=end;speakChunk(run) }
            }
        })
        if(!accepted) { stop();error="Offline Speech Could Not Start" }
    }
    fun settingsVoice(id:String) { stop();if(voices.any { it.id==id }) { voice=id;prefs.edit().putString("voice",id).apply() } }
    fun settingsRate(value:Float) { stop();rate=speechRate(value);prefs.edit().putFloat("rate",rate).apply() }
    fun beginSettings() { stop();settingsSnapshot=document to position }
    fun endSettings() { stop();settingsSnapshot?.let { document=it.first;position=it.second };settingsSnapshot=null }
    fun close() { if(!closed) { stop();closed=true;setupGeneration++;backend.close() } }
}

@Composable internal fun SdkSpeechTransport(node:org.json.JSONObject, model:SdkAppModel, player:SdkSpeechController) {
    val text=node.optString("value");val id=node.getString("id")
    if(node.optBoolean("compact")){
        LaunchedEffect(model,player,id){var previous:String?=null;var previousBook:String?=null;snapshotFlow{model.frame}.collect{frame->
            val current=frame.optJSONArray("body")?.let{body->(0 until body.length()).mapNotNull{body.optJSONObject(it)}.firstOrNull{it.optString("id")==id}}?:return@collect
            val next=current.optString("value");val book=current.optString("book");if(next==previous&&book==previousBook)return@collect;previous=next;previousBook=book;if(player.bookKey.isNotEmpty()&&player.bookKey!=book)player.bookContinuous=false
            player.bookKey=book;player.setDocument(next);if(next.isNotBlank()){player.prepare(id);if(player.bookContinuous&&player.bookWidget==id&&!player.playing)player.play(next,id)}
        }}
        DisposableEffect(id,node.has("on_done")){player.onDocumentComplete=if(node.has("on_done"))({model.speechDone(id)})else null;onDispose{player.onDocumentComplete=null;player.bookContinuous=false;player.stop()}}
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Row(Modifier.weight(1f),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_message_speaker),null,Modifier.size(24.dp))
                Text("Read aloud",Modifier.padding(start=8.dp),fontSize=15.sp)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(0.dp)) {
                IconButton(enabled=text.isNotBlank()&&!model.busy,onClick={if(!player.playing){player.bookContinuous=true;player.bookWidget=id;player.play(text,id)}},modifier=Modifier.testTag("sdk-ebook-speaker")){Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_radio_play),"Play read aloud")}
                IconButton(enabled=player.playing,onClick={player.bookContinuous=false;player.stop()}){Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_radio_pause),"Pause read aloud")}
                IconButton(enabled=text.isNotBlank(),onClick={player.bookContinuous=false;player.stop();player.move(text,0,id)}){Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_radio_stop),"Stop read aloud")}
            }
        }
        player.error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        return
    }

    LaunchedEffect(text) { player.setDocument(text);player.prepare(id) }
    DisposableEffect(id) { onDispose { player.stop() } }
    fun move(at:Int) { player.move(text,at,id);model.followSpeechPosition(id,text,player.position) }
    val at=player.position.coerceIn(0,text.length)
    Column(Modifier.fillMaxWidth().testTag("sdk-$id").padding(vertical=6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("${if(text.isEmpty()) 0 else 100L*at/text.length}%",Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(model.appIdentity=="app.onklav.reader") TextButton(modifier=Modifier.testTag("sdk-clear"),onClick={player.stop();model.action("clear")},enabled=text.isNotEmpty()&&!model.busy){Text("Clear")}
            if(model.appIdentity=="app.onklav.reader") TextButton(modifier=Modifier.testTag("sdk-paste"),onClick={player.stop();model.action("paste")},enabled=!model.busy){Text("Paste")}
        }
        player.error?.let{Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
        var tracking by remember { mutableStateOf(false) };var seek by remember { mutableFloatStateOf(0f) };var resume by remember { mutableStateOf(false) }
        Slider(value=if(tracking) seek else at.toFloat(),onValueChange={if(!tracking) resume=player.playing;seek=it;tracking=true;player.stop()},onValueChangeFinished={move(seek.toInt());tracking=false;if(resume && player.position<text.length) player.play(text,id)},valueRange=0f..text.length.coerceAtLeast(1).toFloat(),enabled=text.isNotEmpty() && !model.busy,modifier=Modifier.testTag("sdk-speech-position").semantics {contentDescription="Reading position"})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            IconButton(onClick={move(at-180)},enabled=at>0&&!model.busy,modifier=Modifier.weight(1f)){Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_speech_previous),"Previous")}
            IconButton(onClick={player.play(text,id)},enabled=text.isNotBlank()&&!model.busy,modifier=Modifier.weight(1f).testTag("sdk-speech-play")){Icon(androidx.compose.ui.res.painterResource(if(player.playing)app.onklav.shared.R.drawable.ref_radio_pause else app.onklav.shared.R.drawable.ref_radio_play),if(player.playing)"Pause" else "Play")}
            IconButton(onClick={move(at+180)},enabled=at<text.length&&!model.busy,modifier=Modifier.weight(1f)){Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_speech_next),"Next")}
        }
    }
}
@Composable internal fun SdkSpeechSettings(node:org.json.JSONObject,player:SdkSpeechController) {
    val id=node.getString("id")
    LaunchedEffect(id) { player.prepare(id) }
    // Settings preview uses the same engine; restore the document/position when it closes.
    DisposableEffect(id) { player.beginSettings();onDispose { player.endSettings() } }
    Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        Text("English voices included. Reading works offline.",fontSize=14.sp)
        player.error?.let { Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error) }
        if(!player.ready) ChoiceButton("Retry Voice Setup",outlined=false) { player.retry(id) }
        Text("Speech Speed · ${"%.2f".format(player.rate)}×",fontSize=16.sp)
        Slider(player.rate,{player.settingsRate(it)},valueRange=.75f..1.5f,steps=14,modifier=Modifier.testTag("sdk-speech-speed"))
        ChoiceButton(if(player.playing) "Stop Preview" else "Preview Voice",outlined=false,enabled=player.ready) { player.play("Hello! This is how your messages will sound.",id) }
        player.voices.forEach { v -> TextButton(onClick={player.settingsVoice(v.id)}) { Text((if(player.voice==v.id) "✓ " else "")+v.label,fontSize=13.sp) } }
        Text("Voice: Kitten Nano FP32. Reading-position highlighting is approximate.",fontSize=12.sp)
    }
}
