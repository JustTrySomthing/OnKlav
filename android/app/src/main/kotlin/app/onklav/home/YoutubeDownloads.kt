@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import androidx.lifecycle.viewModelScope
import android.content.Context
import android.webkit.WebView
import android.media.MediaCodecList
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.*
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import app.onklav.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

internal fun youtubeDownloadHeader(frame:JSONObject):JSONObject{val result=JSONObject(frame.toString());result.optJSONArray("actions")?.let{a->for(i in 0 until a.length()){val b=a.getJSONObject(i);if(b.optString("id")=="home"){b.put("id","download").put("text","Download").put("icon","download");b.remove("long_action");b.remove("long_label")}}};return result}
internal data class YoutubeStream(val itag:Int,val url:String,val mime:String,val height:Int,val audio:Boolean,val identity:String="",val captured:Boolean=false)
/** Only URLs that the current signed-out page supplies or actually requests; no signature or DRM bypass. */
internal class YoutubeStreams{
 @Volatile var video="";private set;private var ad=true;private var live=false
 private val declared=linkedMapOf<Int,YoutubeStream>();private val observed=linkedMapOf<Int,String>()
 companion object{
  fun url(raw:String):HttpUrl{val u=raw.toHttpUrlOrNull()?:error("Invalid media address");require(u.scheme=="https"&&u.port==443&&u.username.isEmpty()&&u.password.isEmpty()&&u.host.endsWith(".googlevideo.com")&&u.encodedPath=="/videoplayback"){"This video does not provide a downloadable media address"};require(u.queryParameter("source")=="youtube"||u.queryParameter("source").isNullOrBlank());return u}
  fun complete(raw:String):String{val u=url(raw);val signed=(u.queryParameter("sparams").orEmpty()+","+u.queryParameter("lsparams").orEmpty()).split(',');require("range" !in signed){"This stream is restricted to the player"};return u.newBuilder().removeAllQueryParameters("range").build().toString()}
 }
 @Synchronized fun bind(id:String){if(video!=id){video=id;declared.clear();observed.clear();ad=true;live=false}}
 @Synchronized fun manifest(j:JSONObject){if(j.optString("video")!=video)return;ad=j.optBoolean("ad",true);live=j.optBoolean("live");val a=j.optJSONArray("streams")?:return;for(i in 0 until minOf(a.length(),160)){val f=a.getJSONObject(i);val type=f.optString("mime").substringBefore(';');if(type !in listOf("video/mp4","video/webm","audio/mp4","audio/webm"))continue;val itag=f.optInt("itag");if(itag<=0)continue;val raw=f.optString("url");val valid=runCatching{url(raw)}.getOrNull();declared[itag]=YoutubeStream(itag,valid?.toString().orEmpty(),type,f.optInt("height"),f.optBoolean("audio"),f.optString("identity",valid?.queryParameter("id").orEmpty()))}}
 @Synchronized fun observe(raw:String){if(ad||video.isBlank())return;val u=runCatching{url(raw)}.getOrNull()?:return;val itag=u.queryParameter("itag")?.toIntOrNull()?:return;observed[itag]=u.toString();if(observed.size>160)observed.remove(observed.keys.first())}
 @Synchronized fun candidates():List<YoutubeStream> =declared.values.mapNotNull{f->val captured=observed[f.itag]?.takeIf{raw->f.identity.isBlank()||runCatching{url(raw).queryParameter("id")==f.identity}.getOrDefault(false)};val raw=captured?:f.url.takeIf{it.isNotBlank()}?:return@mapNotNull null;runCatching{f.copy(url=complete(raw),captured=captured!=null)}.getOrNull()}
 @Synchronized fun hasHeight(h:Int)=declared.values.any{it.height>=h}
 @Synchronized fun select(audioOnly:Boolean,height:Int):Pair<YoutubeStream,YoutubeStream?>{require(!live){"Live videos can be downloaded after the broadcast ends"};val rows=candidates();if(audioOnly)return (rows.filter{it.audio}.sortedWith(compareBy<YoutubeStream>{if(it.captured)0 else 1}.thenBy{if(it.height==0)0 else 1}.thenBy{if(it.mime=="audio/mp4")0 else 1}).firstOrNull()?:error("Audio is not available yet. Play the video briefly, then retry.")) to null;val video=rows.filter{it.height>=height}.sortedWith(compareBy<YoutubeStream>{if(it.captured)0 else 1}.thenBy{it.height}.thenBy{if(it.mime=="video/mp4")0 else 1}).firstOrNull()?:error("${height}p is not available from this video. Play it at that quality, then retry.");return video to if(video.audio)null else rows.filter{it.audio&&it.height==0}.sortedBy{if(it.mime=="audio/mp4")0 else 1}.firstOrNull().also{require(it!=null){"The audio track is not available yet. Play the video briefly, then retry."}}}
}
internal object YoutubeDownloadScripts{
 val manifest="""(()=>{let p=document.querySelector('#movie_player')||document.querySelector('.html5-video-player'),r=window.ytInitialPlayerResponse;try{if(p&&p.getPlayerResponse)r=p.getPlayerResponse()||r;}catch(_){}if(typeof r==='string')try{r=JSON.parse(r);}catch(_){r=null;}const id=new URL(location.href).searchParams.get('v')||((location.pathname.match(/^\/(?:shorts|live|embed)\/([^/]+)/)||[])[1])||'';if(!r||!r.videoDetails||r.videoDetails.videoId!==id)return JSON.stringify({video:id,ad:true,streams:[]});const data=r.streamingData||{};return JSON.stringify({video:id,ad:!!document.querySelector('.ad-showing,.ad-interrupting'),live:!!r.videoDetails.isLive,streams:[...(data.formats||[]),...(data.adaptiveFormats||[])].slice(0,160).map(f=>{let base=f.url||'';if(!base&&f.signatureCipher)base=new URLSearchParams(f.signatureCipher).get('url')||'';let identity='';try{identity=new URL(base).searchParams.get('id')||'';}catch(_){}return {itag:f.itag,url:f.url||'',identity:identity,mime:f.mimeType,height:parseInt(f.qualityLabel||'')||Math.min(f.height||0,f.width||f.height||0),audio:!!f.audioQuality||!!f.audioChannels};})});})()"""
 fun quality(h:Int)="""(()=>{const p=document.querySelector('#movie_player')||document.querySelector('.html5-video-player');try{if(p&&p.setPlaybackQualityRange)p.setPlaybackQualityRange('${if(h==1080)"hd1080" else"large"}');if(p&&p.setPlaybackQuality)p.setPlaybackQuality('${if(h==1080)"hd1080" else"large"}');}catch(_){}})()"""
}
internal fun youtubeDimensions(width:Int,height:Int,quality:Int):Pair<Int,Int>{require(width>0&&height>0&&minOf(width,height)>=quality){"The source is below ${quality}p. Choose a lower quality."};return if(width>=height)((width.toDouble()*quality/height/2).roundToInt()*2) to quality else quality to ((height.toDouble()*quality/width/2).roundToInt()*2)}
internal class YoutubeLinkRejected(val status:Int):java.io.IOException("YouTube declined this download link ($status). Try again or choose another format.")
/** A rejected signed link gets one fresh extraction, never an unbounded retry loop. */
internal suspend fun <T> freshYoutubeTransfer(resolve:suspend ()->YoutubeResolver.Result,refresh:()->Unit={},consume:suspend (YoutubeResolver.Result)->T):T{
 for(attempt in 0..1){val result=resolve();try{return consume(result)}catch(e:YoutubeLinkRejected){if(attempt==1)throw e;refresh()}}
 error("Download could not start")
}
internal object YoutubeDownload{
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(12,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 private const val LIMIT=2L*1024*1024*1024

 internal suspend fun transfer(url:String,target:File,agent:String,verify:()->Unit,sourceClient:OkHttpClient=client,progress:(Long,Long)->Unit)=withContext(Dispatchers.IO){var address=YoutubeStreams.complete(url);var done=0L;var length=-1L;var redirects=0;var etag:String?=null;var modified:String?=null
  target.outputStream().buffered().use{out->while(true){currentCoroutineContext().ensureActive();verify();var redirected=false;val builder=Request.Builder().url(address).header("User-Agent",agent.take(1000)).header("Accept-Encoding","identity").header("Range","bytes=$done-");if(done>0)(etag?:modified)?.let{builder.header("If-Range",it)};val call=sourceClient.newCall(builder.build());coroutineScope{val watcher=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}};try{call.execute().use{r->
   if(r.code in listOf(301,302,303,307,308)){require(++redirects<=5){"The download redirected too many times"};redirected=true;address=YoutubeStreams.complete(r.request.url.resolve(r.header("Location")?:error("Missing download address"))?.toString()?:error("Invalid download address"));return@use}
   if(r.code==403||r.code==410)throw YoutubeLinkRejected(r.code);require(r.code==200||r.code==206){"Download failed (${r.code}). Retry or choose another video."};if(done>0)require(r.code==206){"The stream changed while downloading. Retry from the beginning."}
   val body=requireNotNull(r.body);val type=body.contentType()?.toString().orEmpty();require(type.startsWith("audio/")||type.startsWith("video/")||type.startsWith("application/octet-stream")){"The server did not return a media file"}
   if(r.code==206){val match=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(r.header("Content-Range").orEmpty())?:error("The server returned an incomplete range");require(match.groupValues[1].toLong()==done);val total=match.groupValues[3].toLong();require(total in 1..LIMIT);if(length>0)require(total==length){"The stream changed while downloading"};length=total}else{length=body.contentLength();require(length<=LIMIT)}
   if(done==0L){etag=r.header("ETag");modified=r.header("Last-Modified")}else{if(etag!=null)require(r.header("ETag")==etag){"The stream changed while downloading"};if(modified!=null)require(r.header("Last-Modified")==modified){"The stream changed while downloading"}}
   val before=done;body.byteStream().use{input->val b=ByteArray(131072);while(true){currentCoroutineContext().ensureActive();verify();val n=input.read(b);if(n<0)break;done+=n;require(done<=LIMIT&&(length<0||done<=length)){"This download is too large"};check(target.parentFile!!.usableSpace>32_000_000){"Not enough space. Free storage and retry."};out.write(b,0,n);progress(done,length)}};require(done>before){"The download stopped before completing"}
  }}finally{watcher.cancel()}};if(redirected)continue;if(length<0||done==length)break}
  };require(done>0&&(length<0||done==length)){"Download is incomplete"}
 }
 suspend fun render(c:Context,first:File,second:File?,audio:Boolean,height:Int,output:File,verify:()->Unit,progress:(Float)->Unit){val size=if(audio)null else withContext(Dispatchers.IO){val m=android.media.MediaMetadataRetriever();try{m.setDataSource(first.path);val w=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0;val h=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0;val rotated=(m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?:0)%180!=0;youtubeDimensions(if(rotated)h else w,if(rotated)w else h,height)}finally{m.release()}};withContext(Dispatchers.Main.immediate){verify();val primary=EditedMediaItem.Builder(MediaItem.fromUri(first.toURI().toString())).setRemoveVideo(audio).apply{if(!audio&&second!=null)setRemoveAudio(true);if(!audio)setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(requireNotNull(size).first,size.second,Presentation.LAYOUT_SCALE_TO_FIT))))}.build();val sequences=mutableListOf(EditedMediaItemSequence.Builder(primary).build());if(second!=null)sequences.add(EditedMediaItemSequence.Builder(EditedMediaItem.Builder(MediaItem.fromUri(second.toURI().toString())).setRemoveVideo(true).build()).build());val comp=Composition.Builder(sequences).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC).build()
  suspendCancellableCoroutine<Unit>{cont->val t=Transformer.Builder(c).setAudioMimeType(MimeTypes.AUDIO_AAC).apply{if(!audio)setVideoMimeType(MimeTypes.VIDEO_H264)}.setEncoderFactory(DefaultEncoderFactory.Builder(c).setEnableFallback(true).build()).addListener(object:Transformer.Listener{override fun onCompleted(composition:Composition,result:ExportResult){if(cont.isActive)cont.resume(Unit){_,_,_->}};override fun onError(composition:Composition,result:ExportResult,e:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Couldn’t prepare this format. Retry or choose another quality.",e)))}}).build();cont.invokeOnCancellation{android.os.Handler(android.os.Looper.getMainLooper()).post{t.cancel()}};t.start(comp,output.path);CoroutineScope(cont.context).launch{val h=ProgressHolder();while(cont.isActive){try{verify();check(output.parentFile!!.usableSpace>32_000_000){"Not enough storage"};progress(if(t.getProgress(h)==Transformer.PROGRESS_STATE_AVAILABLE)h.progress/100f else-1f)}catch(e:Exception){t.cancel();if(cont.isActive)cont.resumeWith(Result.failure(e));break};delay(250)}}}
 }}
}

@Composable internal fun YoutubeDownloadDialog(model:SdkAppModel,widget:String,pool:SdkWebPool,view:WebView?,visible:Boolean,resolveVideo:suspend (String,()->Unit)->YoutubeResolver.Result=YoutubeResolver::resolve,close:()->Unit){if(!visible)return;val c=LocalContext.current;val scope=rememberCoroutineScope();var audio by remember{mutableStateOf(false)};var mp3 by remember{mutableStateOf(false)};var downloadFraction by remember{mutableFloatStateOf(0f)};var conversionFraction by remember{mutableFloatStateOf(0f)};var converting by remember{mutableStateOf(false)};var height by remember{mutableIntStateOf(1080)};var formats by remember{mutableStateOf<YoutubeResolver.Result?>(null)};var resolving by remember{mutableStateOf(true)};var working by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")};var fraction by remember{mutableFloatStateOf(-1f)};var job by remember{mutableStateOf<Job?>(null)};var saved by remember{mutableStateOf(false)};var accessOff by remember{mutableStateOf(false)};val expected=remember{pool.videoStreams.video};val currentView by rememberUpdatedState(view)
 fun verify(){model.webComponent(widget);check(pool.videoStreams.video==expected&&expected.isNotBlank()){ "The video changed. Open Download again." };val apps=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray;val row=(0 until apps.length()).map{apps.getJSONObject(it)}.first{it.optJSONObject("manifest")?.optString("id")=="app.onklav.youtube"};val grants=row.getJSONArray("grants");check(row.optBoolean("installed")&&listOf("storage.private","files.manage").all{cap->(0 until grants.length()).any{grants.optString(it)==cap}}){"Downloads are disabled."}}
 suspend fun resolve(){resolving=true;status="Finding available formats";try{verify();val result=resolveVideo(expected,::verify);verify();formats=result;if(height !in result.heights)height=result.heights.lastOrNull{it<=1080}?:result.heights.firstOrNull()?:0;status=""}catch(e:TimeoutCancellationException){status="The video service took too long. Tap Retry formats."}catch(e:CancellationException){throw e}catch(e:Exception){status="Could not read this video’s formats. "+(e.message?:"Try again.").take(160)}finally{resolving=false}}
 LaunchedEffect(Unit){accessOff=!essentialAccess(c,"app.onklav.youtube",*YOUTUBE_DOWNLOAD_CAPS.toTypedArray());if(!accessOff&&expected.isNotBlank())resolve()else resolving=false};DisposableEffect(Unit){onDispose{job?.cancel()}}
 ReferenceDialog(if(saved)"Download complete" else"Download",{job?.cancel();close()},footer={if(working)DialogAction("Cancel"){job?.cancel()}else{DialogAction(if(saved)"Close" else"Cancel"){close()};if(!saved)DialogAction("Download",enabled=expected.isNotBlank()&&!accessOff&&!resolving&&formats!=null&&(audio||height>0)){
  working=true;saved=false;downloadFraction=-1f;conversionFraction=0f;converting=false;status="Preparing download";fraction=-1f;job=scope.launch{val folder=File(c.cacheDir,"youtube-download-${java.util.UUID.randomUUID()}").apply{mkdirs()};try{verify();val processingContext=currentCoroutineContext();var verifiedAt=0L
   fun verifyWork(){processingContext.ensureActive();check(pool.videoStreams.video==expected){"The video changed. Open Download again."};val now=System.nanoTime();if(now-verifiedAt>=200_000_000){verify();verifiedAt=now}}
   val agent=currentView?.settings?.userAgentString.orEmpty();val first=File(folder,"source");var second:File?=null;var selectedStream:YoutubeStream?=null
   freshYoutubeTransfer(resolve={verify();resolveVideo(expected,::verify).also{verify();formats=it}},refresh={status="Refreshing download link";fraction=-1f;downloadFraction=-1f}){fresh->
    val choice=fresh.select(audio,height);selectedStream=choice.first;first.delete();File(folder,"sound").delete();second=choice.second?.let{File(folder,"sound")};status=if(audio)"Downloading audio"else"Downloading video"
    YoutubeDownload.transfer(choice.first.url,first,agent,::verifyWork){done,total->fraction=if(total>0)done.toFloat()/total else-1f;downloadFraction=fraction;status="Downloading ${if(audio)"audio"else"video"} · ${done/1_000_000} MB"+(if(total>0)" / ${total/1_000_000} MB"else"")}
    second?.let{sound->status="Downloading audio track";YoutubeDownload.transfer(choice.second!!.url,sound,agent,::verifyWork){done,total->fraction=if(total>0)done.toFloat()/total else-1f;status="Downloading audio · ${done/1_000_000} MB"}}
   };downloadFraction=1f
   val audioFormat=selectedStream?.let{youtubeAudioFormat(it,mp3)}
   val extension=if(audio)requireNotNull(audioFormat).extension else "mp4"
   val output=File(folder,"download.$extension");status="Preparing ${if(audio)"audio"else"${height}p video"}";fraction=-1f
   if(audio)withContext(Dispatchers.IO){
       if(mp3){converting=true;status="Converting to MP3";conversionFraction=-1f
           val wave=File(folder,"audio.wav");AudioImportWave.convert(first,wave,"YouTube",::verifyWork){conversionFraction=it*.65f}
           app.onklav.shared.Mp3Codec.encode(wave,output,192,::verifyWork){conversionFraction=.65f+it*.35f};conversionFraction=1f
       }else{status="Preparing original audio";youtubeOriginalAudio(first,output,requireNotNull(selectedStream),::verifyWork)}
   }else YoutubeDownload.render(c,first,second,false,height,output,::verify){fraction=it}
   status="Saving";fraction=-1f
   val title=FilesBroker.cleanName((formats?.title?:currentView?.title?.removeSuffix(" - YouTube"))?.take(90)?:expected);withContext(Dispatchers.IO){AppSdk.staging.withLock{verify();val staged=File(c.filesDir,"content-import.tmp");try{output.copyTo(staged,true);model.saveVideoFile(widget,"$title.$extension",if(audio)requireNotNull(audioFormat).mime else"video/mp4",if(audio)"audio"else"video")}finally{staged.delete()}}};saved=true;status="Saved in Files › ${if(audio)"Audio"else"Video"} › YouTube";fraction=1f
  }catch(e:CancellationException){status="Download cancelled";throw e}catch(e:Exception){accessOff=!essentialAccess(c,"app.onklav.youtube",*YOUTUBE_DOWNLOAD_CAPS.toTypedArray());status=if(accessOff)"Downloads are disabled."else e.message?:"Couldn’t download this video. Reload it and retry."}finally{folder.deleteRecursively();working=false}}
 }}}){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  if(!saved){if(expected.isBlank())Text("Open a video, then choose Download.")else{Text(formats?.title?:view?.title?.removeSuffix(" - YouTube").orEmpty(),style=MaterialTheme.typography.titleSmall,maxLines=2);MailChoices(listOf("Video","Audio"),if(audio)"Audio"else"Video"){if(!working)audio=it!="Video"}
      if(audio){val native= formats?.let{runCatching{youtubeAudioFormat(it.select(true,height).first,false).extension.uppercase()}.getOrNull()}?:"Original"
          YoutubeAudioChoices(native,mp3,!working){mp3=it}
      };if(!audio&&formats!=null){val heights=formats!!.heights;if(heights.isNotEmpty())MailChoices(heights.map{"${it}p"},"${height}p"){if(!working)height=it.removeSuffix("p").toInt()}else Text("No downloadable video formats are available.")}}}
  if(accessOff&&!working)FilledTonalButton(onClick={close();model.viewModelScope.launch{try{check(model.appIdentity=="app.onklav.youtube"){"Open YouTube again"};AppSdk.enableYoutubeDownloads(c);model.reopenDiscardingDraft()}catch(e:CancellationException){throw e}catch(e:Exception){model.error=e.message?:"Could not enable downloads. Close and reopen YouTube."}}},modifier=Modifier.fillMaxWidth()){Text("Enable downloads")}
  if(!working&&!resolving&&formats==null&&!accessOff&&expected.isNotBlank())TextButton(onClick={scope.launch{resolve()}}){Text("Retry formats")}
  if(resolving)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(working){if(audio&&mp3)YoutubeAudioProgress(downloadFraction,conversionFraction,converting)else{Text(if(audio)"Download audio"else"Download & prepare video",style=MaterialTheme.typography.labelMedium);val value=if(audio)downloadFraction else fraction;if(value>=0)LinearProgressIndicator(progress={value.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())}};if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodyMedium)
 }}
}
