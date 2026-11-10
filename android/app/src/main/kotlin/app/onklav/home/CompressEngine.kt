@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.*
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.util.zip.*
import kotlin.math.roundToInt

internal enum class CompressKind(val title:String,val hint:String) {
    IMAGE("Images","Smaller photos · JPG / WebP"), VIDEO("Videos","Smaller playable MP4"),
    AUDIO("Audio","Smaller playable MP3"), FILES("Files & folders","Lossless ZIP")
}
internal data class CompressEntry(val name:String,val file:File?=null)
internal data class CompressSource(val name:String,val kind:CompressKind,val entries:List<CompressEntry>,val bytes:Long,val seconds:Double=0.0,val width:Int=0,val height:Int=0,val note:String="") {
    val file get()=requireNotNull(entries.single().file)
}
internal data class CompressOptions(val quality:Int=1,val imageEdge:Int=1600,val videoEdge:Int=720,val webp:Boolean=false) {
    val imageQuality get()=listOf(55,75,90)[quality]
    val audioKbps get()=listOf(64,128,192)[quality]
    val videoBitrate get()=when(videoEdge){480->listOf(600000,1000000,1800000);1080->listOf(2500000,5000000,8000000);else->listOf(1200000,2500000,4000000)}[quality]
    fun extension(kind:CompressKind)=when(kind){CompressKind.IMAGE->if(webp)"webp" else "jpg";CompressKind.VIDEO->"mp4";CompressKind.AUDIO->"mp3";CompressKind.FILES->"zip"}
    fun mime(kind:CompressKind)=when(kind){CompressKind.IMAGE->if(webp)"image/webp" else "image/jpeg";CompressKind.VIDEO->"video/mp4";CompressKind.AUDIO->"audio/mpeg";CompressKind.FILES->"application/zip"}
}
internal object CompressEngine {
    const val MAX_BYTES=1_000_000_000L
    const val MAX_ENTRIES=10000
    fun cleanName(name:String)=name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}]")," ").trim().take(100).ifBlank{"File"}.let{if(it=="."||it=="..")"File" else it}
    fun uniqueName(name:String,used:MutableSet<String>):String {
        val clean=cleanName(name);var candidate=clean;var index=2
        while(!used.add(candidate.lowercase(java.util.Locale.ROOT))){val stem=clean.substringBeforeLast('.',clean);val ext=clean.substringAfterLast('.',"");candidate="$stem (${index++})"+if(ext.isEmpty())"" else ".$ext"}
        return candidate
    }
    suspend fun copy(c:Context,uri:Uri,out:File,remaining:Long=MAX_BYTES):Pair<String,Long> = withContext(Dispatchers.IO) {
        var name=uri.lastPathSegment?:"File"
        c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{q->if(q.moveToFirst()){val ni=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ni>=0)name=q.getString(ni)?:name;val si=q.getColumnIndex(OpenableColumns.SIZE);if(si>=0&&!q.isNull(si))require(q.getLong(si)<=remaining){"Choose up to 1 GB in total."}}}
        var total=0L;requireNotNull(c.contentResolver.openInputStream(uri)){"File is unavailable."}.use{input->out.outputStream().use{output->val buf=ByteArray(131072);while(true){currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break;total+=n;require(total<=remaining){"Choose up to 1 GB in total."};require(out.parentFile!!.usableSpace>n+32_000_000L){"Not enough free storage."};output.write(buf,0,n)}}};cleanName(name) to total
    }
    fun inspect(file:File,name:String,kind:CompressKind):CompressSource {
        require(file.length()>0){"This media file is empty."}
        var seconds=0.0;var width=0;var height=0;var note=""
        when(kind){
            CompressKind.IMAGE->{val prepared=FormatSwapEngine.prepare(file,name,SwapKind.IMAGE);note=prepared.warning
                android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(file)){decoder,info,_->width=info.size.width;height=info.size.height;decoder.setTargetSize(1,1)}
            }
            CompressKind.AUDIO,CompressKind.VIDEO->{
                FormatSwapEngine.prepare(file,name,if(kind==CompressKind.VIDEO)SwapKind.VIDEO else SwapKind.AUDIO)
                val retriever=MediaMetadataRetriever();try{retriever.setDataSource(file.path);seconds=(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?:0.0)/1000
                    width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0;height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
                    if(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() in setOf(90,270)){val swap=width;width=height;height=swap}
                }finally{retriever.release()}
                if(kind==CompressKind.VIDEO)require(width>0&&height>0){"Video dimensions could not be read."}
                note="Metadata${if(kind==CompressKind.VIDEO)" and subtitles" else ""} are not copied. Device codecs determine input support."
            }
            CompressKind.FILES->Unit
        }
        return CompressSource(name,kind,listOf(CompressEntry(name,file)),file.length(),seconds,width,height,note)
    }
    fun dimensions(source:CompressSource,edge:Int):Pair<Int,Int>{
        require(source.width>0&&source.height>0)
        val scale=minOf(1.0,edge.toDouble()/minOf(source.width,source.height))
        fun even(n:Int)=((n*scale/2).roundToInt()*2).coerceAtLeast(2)
        return even(source.width) to even(source.height)
    }
    fun estimate(source:CompressSource,o:CompressOptions):Long?=when(source.kind){
        CompressKind.AUDIO->if(source.seconds>0)(source.seconds*o.audioKbps*1000/8*1.03+4096).toLong()else null
        CompressKind.VIDEO->if(source.seconds>0)(source.seconds*(o.videoBitrate+96000)/8*1.04+8192).toLong()else null
        else->null
    }
    suspend fun compress(c:Context,source:CompressSource,o:CompressOptions,out:File,progress:(Float)->Unit) {
        require(o.quality in 0..2)
        when(source.kind){
            CompressKind.IMAGE->FormatSwapEngine.convert(c,SwapInput(source.file,source.name,SwapKind.IMAGE,""),if(o.webp)SwapFormat.WEBP else SwapFormat.JPG,SwapOptions(quality=o.imageQuality,maxEdge=o.imageEdge),out,progress)
            CompressKind.AUDIO->FormatSwapEngine.convert(c,SwapInput(source.file,source.name,SwapKind.AUDIO,""),SwapFormat.MP3,SwapOptions(kbps=o.audioKbps),out,progress)
            CompressKind.VIDEO->video(c,source,o,out,progress)
            CompressKind.FILES->withContext(Dispatchers.IO){val job=currentCoroutineContext();zip(source.entries,out,{job.ensureActive();require(out.parentFile!!.usableSpace>32_000_000){"Not enough free storage."}},progress)}
        }
    }
    internal fun zip(entries:List<CompressEntry>,out:File,check:()->Unit={},progress:(Float)->Unit={}) {
        require(entries.size<=MAX_ENTRIES);val total=entries.sumOf{it.file?.length()?:0L};require(total<=MAX_BYTES){"Choose up to 1 GB in total."}
        val names=mutableSetOf<String>();var sent=0L
        ZipOutputStream(BufferedOutputStream(out.outputStream())).use{zip->zip.setLevel(9)
            entries.forEach{entry->check();require(entry.name.isNotBlank()&&!entry.name.startsWith('/')&&!entry.name.contains('\\')&&entry.name.split('/').filter{it.isNotEmpty()}.none{it=="."||it==".."}){"Invalid archive path."};require(names.add(entry.name.lowercase(java.util.Locale.ROOT))){"Duplicate archive path."}
                zip.putNextEntry(ZipEntry(entry.name+(if(entry.file==null&&!entry.name.endsWith('/'))"/"else"")))
                entry.file?.inputStream()?.use{input->val buf=ByteArray(131072);while(true){check();val n=input.read(buf);if(n<0)break;sent+=n;require(sent<=MAX_BYTES){"Choose up to 1 GB in total."};zip.write(buf,0,n);progress(sent.toFloat()/total.coerceAtLeast(1))}}
                zip.closeEntry()
            }
        }
    }
    private suspend fun video(c:Context,source:CompressSource,o:CompressOptions,out:File,progress:(Float)->Unit)=withContext(Dispatchers.Main.immediate){
        val (w,h)=dimensions(source,o.videoEdge)
        val item=EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source.file))).setFrameRate(30).setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(w,h,Presentation.LAYOUT_SCALE_TO_FIT),androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(30f)))).build()
        val composition=Composition.Builder(EditedMediaItemSequence.Builder(item).build()).setTransmuxVideo(false).setTransmuxAudio(false).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC).build()
        coroutineScope{val scope=this;suspendCancellableCoroutine<Unit>{cont->
            val encoders=DefaultEncoderFactory.Builder(c).setEnableFallback(true).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(o.videoBitrate).build()).setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(96000).build()).build()
            val transformer=Transformer.Builder(c).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoders)
                .addListener(object:Transformer.Listener{override fun onCompleted(composition:Composition,result:ExportResult){if(cont.isActive)cont.resumeWith(Result.success(Unit))};override fun onError(composition:Composition,result:ExportResult,error:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Video compression failed (${error.errorCodeName}). Try 480p or another file.",error)))}}).build()
            cont.invokeOnCancellation{scope.launch(NonCancellable+Dispatchers.Main.immediate){transformer.cancel()}}
            try{transformer.start(composition,out.path)}catch(e:Exception){transformer.cancel();if(cont.isActive)cont.resumeWith(Result.failure(e))}
            scope.launch{val holder=ProgressHolder();while(cont.isActive){if(out.parentFile!!.usableSpace<32_000_000){transformer.cancel();if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Not enough free storage.")));break};if(transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE)progress(holder.progress/100f);delay(250)}}
        }}
    }
}
