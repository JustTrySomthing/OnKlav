package app.onklav.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

/** One process-wide native model; every app still uses SdkSpeechController's authorization/focus lease. */
internal object NeuralSpeechNative {
    private val loaded by lazy {
        val root=System.getProperty("onklav.project.root")
        if(root!=null){val libs=File(root,"core/speech/host-libs/linux-x86_64");listOf("libonnxruntime.so","libsherpa-onnx-c-api.so","libonklav_speech.so").forEach{System.load(File(libs,it).absolutePath)}}
        else listOf("onnxruntime","sherpa-onnx-c-api","onklav_speech").forEach{System.loadLibrary(it)}
        true
    }
    fun load(){check(loaded)}
    external fun initialize(directory:String)
    external fun synthesize(text:String,speaker:Int,speed:Float):FloatArray
    external fun unload()
    external fun open():Long
    external fun enqueue(handle:Long,pcm:FloatArray)
    external fun played(handle:Long):Long
    external fun buffered(handle:Long):Long
    external fun cancel(handle:Long)
    external fun close(handle:Long)
}
internal object SpeechVoices {
    const val MALE="english-nano-fp32-male"
    const val FEMALE="english-nano-fp32-female"
    val choices=listOf(SpeechVoice(MALE,"Male"),SpeechVoice(FEMALE,"Female"))
    fun speaker(id:String)=when(id){MALE->2;FEMALE->0;else->error("Choose an included English voice")}
}
internal object SpeechModelFiles {
    private fun digest(file:File)=file.inputStream().use{input->val hash=MessageDigest.getInstance("SHA-256");val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;hash.update(bytes,0,n)};hash.digest().joinToString(""){"%02x".format(it)}}
    @Synchronized fun prepare(context:Context):File {
        val manifest=JSONObject(context.assets.open("speech/english.json").bufferedReader().use{it.readText()})
        val files=manifest.getJSONObject("files");val version=manifest.getString("archive_sha256")
        val root=File(context.noBackupFilesDir,"speech/nano-fp32-v08")
        fun valid():Boolean=File(root,".verified").takeIf{it.isFile}?.readText()==version&&files.keys().asSequence().all{n->val f=File(root,n);f.isFile&&f.length()==files.getJSONObject(n).getLong("bytes")&&digest(f)==files.getJSONObject(n).getString("sha256")}
        if(valid())return root
        val staging=File(root.parentFile,"installing");if(staging.exists())staging.deleteRecursively();check(staging.mkdirs()){"Could not prepare voice storage"}
        try {
            val seen=mutableSetOf<String>();var total=0L
            ZipInputStream(context.assets.open("speech/english.voicepack")).use{zip->while(true){val entry=zip.nextEntry?:break;val name=entry.name
                require(!entry.isDirectory&&files.has(name)&&seen.add(name)&&!name.startsWith("/")&&name.split('/').none{it in setOf("",".","..")}&&!name.contains('\\')){"Invalid included voice pack"}
                val expected=files.getJSONObject(name);val out=File(staging,name);check(out.canonicalPath.startsWith(staging.canonicalPath+File.separator));out.parentFile?.mkdirs()
                var count=0L;val hash=MessageDigest.getInstance("SHA-256");out.outputStream().use{stream->val bytes=ByteArray(65536);while(true){val n=zip.read(bytes);if(n<0)break;count+=n;total+=n;require(count<=expected.getLong("bytes")&&total<=160L*1024*1024);hash.update(bytes,0,n);stream.write(bytes,0,n)}}
                require(count==expected.getLong("bytes")&&hash.digest().joinToString(""){"%02x".format(it)}==expected.getString("sha256")){"Included voice verification failed"}
            }}
            require(seen.size==files.length());File(staging,".verified").writeText(version)
            if(root.exists())check(root.deleteRecursively());check(staging.renameTo(root)){"Could not install included voices"};return root
        }catch(e:Exception){staging.deleteRecursively();throw e}
    }
}
internal data class SpeechPiece(val start:Int,val end:Int)
internal object SpeechPieces {
    /** Keep short requests intact (including the approved reference); split long reading only at natural boundaries. */
    fun plan(text:String):List<SpeechPiece>{val result=mutableListOf<SpeechPiece>();var at=0
        while(at<text.length){var end=SpeechPlan.boundary(text,(at+220).coerceAtMost(text.length))
            if(end<text.length){val punctuation=(end downTo at+60).firstOrNull{text[it-1] in ".!?。！？;:\n"};val clause=(end downTo at+60).firstOrNull{text[it-1] in ",，"};val space=(end downTo at+1).firstOrNull{text[it-1].isWhitespace()};end=punctuation?:clause?:space?:end}
            if(end<=at)end=SpeechPlan.boundary(text,(at+2).coerceAtMost(text.length));result+=SpeechPiece(at,end);at=end
        };return result}
}
/** UI_HIDDEN is a visibility event, not memory pressure; keep the shared model warm. */
internal fun shouldTrimSpeech(level:Int)=level in setOf(
    android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
    android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
    android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
    android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE,
    android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
)
internal object NeuralSpeechRuntime {
    val worker=Executors.newSingleThreadExecutor{r->Thread(r,"OnKlav speech synthesis").apply{isDaemon=true}}
    val main=Handler(Looper.getMainLooper())
    private var directory:String?=null
    /** Worker only: native pronunciation/model initialization can never block the UI or audio callback. */
    fun prepare(context:Context){NeuralSpeechNative.load();if(directory==null){val path=SpeechModelFiles.prepare(context).absolutePath;NeuralSpeechNative.initialize(path);directory=path}}
    fun trim(){worker.execute{runCatching{NeuralSpeechNative.unload()};directory=null}}
}
internal class NeuralSpeechBackend(context:Context):SpeechBackend {
    private val app=context.applicationContext
    private val generation=AtomicLong();private val lock=Any();private var handle=0L
    private var pending:Future<*>?=null
    @Volatile private var closed=false
    override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit){closed=false;NeuralSpeechRuntime.worker.execute{val ok=runCatching{NeuralSpeechRuntime.prepare(app)}.onFailure{android.util.Log.w("OnKlavSpeech","Voice setup failed",it)}.isSuccess;NeuralSpeechRuntime.main.post{if(!closed)done(ok,if(ok)SpeechVoices.choices else emptyList())}}}
    override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean {
        if(closed||text.isBlank()||text.length>SpeechReading.MAX_SPOKEN_LENGTH||!rate.isFinite()||rate !in .75f..1.5f)return false
        val speaker=runCatching{SpeechVoices.speaker(voice)}.getOrNull()?:return false
        stop();val run=generation.get();val marks=java.util.concurrent.ConcurrentLinkedQueue<Pair<Long,Int>>();var wrote=0L
        fun current()=!closed&&generation.get()==run
        val progress=object:Runnable{override fun run(){if(!current())return;try{val played=synchronized(lock){if(handle==0L)0L else NeuralSpeechNative.played(handle)};var latest:Int?=null;while(true){val mark=marks.peek()?:break;if(mark.first>played)break;latest=marks.poll()?.second};latest?.let{range(it)}}catch(e:Exception){stop();done(e.message?:"Speech playback interrupted");return};NeuralSpeechRuntime.main.postDelayed(this,100)}}
        pending=NeuralSpeechRuntime.worker.submit {
            var failure:String?=null
            try {
                if(!current())return@submit;NeuralSpeechRuntime.prepare(app);if(!current())return@submit
                val opened=NeuralSpeechNative.open();val accepted=synchronized(lock){if(current()){handle=opened;true}else false};if(!accepted){NeuralSpeechNative.cancel(opened);NeuralSpeechNative.close(opened);return@submit}
                NeuralSpeechRuntime.main.post(progress)
                for(piece in SpeechPieces.plan(text)){
                    if(!current())break
                    val chunk=text.substring(piece.start,piece.end);if(chunk.isBlank())continue
                    val pcm=NeuralSpeechNative.synthesize(chunk,speaker,rate);if(!current())break
                    // Position feedback is estimated from played PCM, not claimed native word timestamps.
                    val words=Regex("\\S+").findAll(chunk).toList();val weight=words.sumOf{it.value.length+1}.coerceAtLeast(1);var used=0
                    for(word in words){marks.add(wrote+pcm.size.toLong()*used/weight to piece.start+word.range.first);used+=word.value.length+1}
                    val audio=synchronized(lock){handle};if(audio==0L||!current())break
                    NeuralSpeechNative.enqueue(audio,pcm);wrote+=pcm.size
                }
                var drainStarted=0L
                while(current()){val remaining=synchronized(lock){if(handle==0L)0L else NeuralSpeechNative.buffered(handle)};if(remaining==0L)break;if(drainStarted==0L)drainStarted=android.os.SystemClock.elapsedRealtime();check(android.os.SystemClock.elapsedRealtime()-drainStarted<15000){"Speech output stalled. Tap Play to resume."};Thread.sleep(10)}
            }catch(e:InterruptedException){Thread.currentThread().interrupt()}catch(e:Exception){failure=e.message?:"Speech playback interrupted";android.util.Log.w("OnKlavSpeech","Reading failed",e)}catch(e:LinkageError){failure="Included speech engine could not load";android.util.Log.e("OnKlavSpeech","Native speech failed",e)}
            finally{NeuralSpeechRuntime.main.removeCallbacks(progress);val closing=synchronized(lock){val old=handle;handle=0L;old};if(closing!=0L)runCatching{NeuralSpeechNative.close(closing)}}
            if(current())NeuralSpeechRuntime.main.post{if(current())done(failure)}
        };return true
    }
    override fun stop(){generation.incrementAndGet();pending?.cancel(true);pending=null;synchronized(lock){if(handle!=0L)NeuralSpeechNative.cancel(handle)}}
    override fun close(){closed=true;stop()}
}
