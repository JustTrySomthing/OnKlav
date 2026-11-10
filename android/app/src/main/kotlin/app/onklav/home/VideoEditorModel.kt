@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
package app.onklav.home

import android.app.Application
import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.transformer.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.*
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.Composition
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.*

internal fun videoEditorAccess(c:Context,cap:String="storage.private"):Boolean=videoEditorHasAccess(c,cap)

private fun videoEditorHasAccess(c:Context,vararg capabilities:String):Boolean=runCatching{val rows=AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray;(0 until rows.length()).any{val row=rows.getJSONObject(it);row.optBoolean("installed")&&row.optJSONObject("manifest")?.optString("id")=="app.onklav.videoeditor"&&row.optJSONArray("grants")?.let{a->capabilities.all{cap->(0 until a.length()).any{a.optString(it)==cap}}}==true}}.getOrDefault(false)
internal class VideoEditorModel(app:Application):AndroidViewModel(app){
    val store=VideoStore(app)
    var project by mutableStateOf(VideoProject());private set
    var projects by mutableStateOf(listOf<VideoProject>());private set
    var selected by mutableIntStateOf(0);private set
    var selectedAudioId by mutableStateOf("main");private set
    var snapping by mutableStateOf(true);private set
    fun updateSnapping(value:Boolean){snapping=value}
    fun selectAudio(id:String){if(!readyToSwitch())return;selectedAudioId=id.takeIf{key->key=="main"||project.music.any{it.id==key}}?:"main"}
    fun stepFrame(direction:Int){seek(videoStepFrame(cursor,direction,project.fps,project.duration))}
    fun scrub(ms:Long,tolerance:Long){seek(if(snapping)videoTimelineSnap(project,ms,tolerance)else ms)}
    var cursor by mutableLongStateOf(0);private set
    var busy by mutableStateOf(false);private set
    var progress by mutableFloatStateOf(-1f);private set
    var status by mutableStateOf("");private set
    var error by mutableStateOf(false);private set
    var player by mutableStateOf<Player?>(null);private set
    var playing by mutableStateOf(false);private set
    var thumbnails by mutableStateOf(mapOf<String,Bitmap>());private set
    var revision by mutableIntStateOf(0);private set
    private val undo=mutableListOf<VideoProject>();private val redo=mutableListOf<VideoProject>()
    private var committed=project;private var job:Job?=null;private var tick:Job?=null;private var thumbs:Job?=null
    private var failedPreviewRevision:Int?=null
    private var compatibilityRevision:Int?=null
    private var compatibleFile:Pair<Int,File>?=null
    private var soundtrackFile:Pair<Int,File>?=null
    var transforming by mutableStateOf(false);private set
    fun beginTransform(){transforming=true}
    fun endTransform(){transforming=false;commit()}
    private var transformer:Transformer?=null;private var destination:Uri?=null
    private var focus:android.media.AudioFocusRequest?=null
    val hasUnsavedChanges get()=project!=committed
    val canUndo get()=undo.isNotEmpty()||hasUnsavedChanges;val canRedo get()=redo.isNotEmpty()&&!hasUnsavedChanges;val clip get()=project.clips.getOrNull(selected)
    val clipOffset get()=project.clipStart(selected)
    init{launch("Restoring projects"){val restored=withContext(Dispatchers.IO){authorize();runCatching{store.current()}};restored.getOrNull()?.let{project=it;committed=project};refresh();loadThumbnails();if(restored.isFailure){status="Couldn’t restore the last project. Open Projects or start a new one. ${restored.exceptionOrNull()?.message.orEmpty()}";error=true}else reportMedia()}}
    private fun reportMedia(){val missing=store.missing(project);if(missing.isEmpty()){status="";error=false}else{status="${missing.size} project media file(s) missing. Open Projects and restore a portable backup; the saved edits are kept.";error=true}}
    private fun requireMedia(){val missing=store.missing(project);check(missing.isEmpty()){ "${missing.size} project media file(s) missing. Restore a portable backup from Projects before playback or export." }}
    private fun readyToSwitch():Boolean{commit();return project==committed}
    private fun authorize(){check(videoEditorAccess(getApplication())){"Video Edit storage access is off. Restore it in Settings > Apps > Video Edit."}}
    fun dismiss(){if(hasUnsavedChanges){status="Unsaved edits · tap Retry save before leaving this project";error=true}else{status="";error=false}}
    private fun launch(label:String,action:suspend()->Unit){if(busy||recording)return;releasePreview();busy=true;progress=-1f;status=label;error=false;job=viewModelScope.launch{try{action()}catch(e:CancellationException){status="Cancelled";throw e}catch(e:Exception){android.util.Log.e("OnKlavVideoEditor",label,e);status=e.message?:"Unable to complete. Try again.";error=true}finally{busy=false;progress=-1f}}}
    private fun save(){authorize();store.save(project)}
    private fun refresh(){projects=store.list()}
    private fun refreshSaved(){projects=listOf(project)+projects.filter{it.id!=project.id}}
    fun change(p:VideoProject){if(busy||recording)return;releasePreview();soundtrackFile?.second?.delete();soundtrackFile=null;compatibleFile?.second?.delete();compatibleFile=null;project=videoClampProject(p);transforming=true;if(project.music.none{it.id==selectedAudioId})selectedAudioId="main";revision++;cursor=cursor.coerceIn(0,project.duration);selected=selected.coerceIn(0,(project.clips.size-1).coerceAtLeast(0))}
    fun commit(){transforming=false;if(project==committed)return;val next=project;runCatching{save()}.onSuccess{undo.add(committed);if(undo.size>40)undo.removeAt(0);redo.clear();committed=next;revision++;refreshSaved();status="";error=false}.onFailure{status="Project not saved: ${it.message}";error=true}}
    fun apply(p:VideoProject){change(p);commit()}
    fun edit(c:VideoClip){val clips=project.clips.toMutableList();if(selected !in clips.indices)return;clips[selected]=videoClampClip(c);change(project.copy(clips=clips))}
    fun history(back:Boolean){if(busy||recording)return;if(hasUnsavedChanges){commit();if(hasUnsavedChanges)return};val from=if(back)undo else redo;val to=if(back)redo else undo;if(from.isEmpty())return;val next=from.last();val old=project;change(next);runCatching{save()}.onSuccess{from.removeAt(from.lastIndex);to.add(old);committed=next;refreshSaved()}.onFailure{change(old);status="Couldn’t save changes: ${it.message}";error=true};transforming=false}
    fun choose(index:Int){if(busy||!readyToSwitch())return;selected=index.coerceIn(0,(project.clips.size-1).coerceAtLeast(0));selectedAudioId="main";seek(clipOffset)}
    fun seek(ms:Long){if(busy||recording)return;cursor=ms.coerceIn(0,project.duration);player?.seekTo(cursor)}
    fun operation(op:String){if(busy)return;val c=clip?:return;runCatching{when(op){"Split"->apply(project.split(selected,cursor-clipOffset));"Duplicate"->{require(project.clips.size<100){"A project can contain up to 100 clips"};apply(project.copy(clips=project.clips.take(selected+1)+c.copy(id=videoId())+project.clips.drop(selected+1)))};"Delete"->apply(project.copy(clips=project.clips.filterIndexed{i,_->i!=selected}));"Earlier","Later"->{val next=selected+if(op=="Earlier")-1 else 1;if(next in project.clips.indices){val list=project.clips.toMutableList();java.util.Collections.swap(list,selected,next);apply(project.copy(clips=list));selected=next;seek(clipOffset)}};"Reset"->{edit(c.copy(speed=1f,volume=1f,rotate=0,flipX=false,flipY=false,zoom=1f,panX=0f,panY=0f,endZoom=1f,motion=false,brightness=0f,contrast=0f,saturation=0f,hue=0f,warmth=0f,fadeIn=0,fadeOut=0,keyframes=emptyList(),rates=emptyList(),transition="None",lut="",lutName="",lutAmount=1f));commit()}}}.onFailure{status=it.message?:"Unable to edit";error=true}}
    fun newProject(){if(busy||recording||!readyToSwitch())return;val next=VideoProject();runCatching{authorize();store.save(next)}.onSuccess{change(next);transforming=false;committed=project;undo.clear();redo.clear();refreshSaved();status="";error=false;loadThumbnails()}.onFailure{status="Couldn’t create a project: ${it.message}";error=true}}
    fun openProject(id:String){if(busy||!readyToSwitch())return;launch("Opening project"){val p=withContext(Dispatchers.IO){authorize();store.load(id).also{store.save(it)}};project=p;committed=p;transforming=false;selected=0;cursor=0;revision++;undo.clear();redo.clear();selectedAudioId="main";loadThumbnails();reportMedia()}}
    fun rename(name:String){if(name.trim().isNotEmpty())apply(project.copy(name=name.trim().take(120)))}
    fun deleteProject(id:String){if(busy||recording||!readyToSwitch())return;launch("Deleting project"){withContext(Dispatchers.IO){authorize();store.delete(id)};if(project.id==id){project=VideoProject();committed=project;transforming=false;selected=0;cursor=0;revision++;undo.clear();redo.clear()};refresh();loadThumbnails();status="Project deleted"}}
    fun import(uris:List<Uri>,audio:Boolean=false,overlay:Boolean=false){if(uris.isEmpty()||busy||recording||!readyToSwitch())return;launch(if(audio)"Adding sound" else "Adding clips"){
        require(if(overlay)project.clips.isNotEmpty()&&project.layers.size+uris.size<=4 else if(audio)project.music.size+uris.size<=4 else project.clips.size+uris.size<=100){if(overlay)"Add a main clip first; use up to four overlays" else if(audio)"Use up to four sound tracks" else "Use up to 100 clips"}
        require(!audio||project.clips.isNotEmpty()){ "Add a video or photo before adding audio" }
        val folder=store.folder(project.id);val added=mutableListOf<File>();var importedSaved=false
        try{val imported=withContext(Dispatchers.IO){uris.mapIndexed{index,uri->authorize();val resolver=getApplication<Application>().contentResolver;val name=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:"Clip";val expected=resolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use{if(it.moveToFirst()&&!it.isNull(0))it.getLong(0).takeIf{n->n>0}else null};val f=File(folder,"${videoId()}.media");added.add(f)
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to read $name" }
                    f.outputStream().buffered().use { out ->
                        copyVideoMedia(input, out, 4_000_000_000L, checkAccess = {
                            authorize()
                            check(folder.usableSpace > 64_000_000) { "Not enough storage. Free space and try again." }
                        }) { bytes ->
                            progress = expected?.let { (index + (bytes.toDouble() / it).coerceIn(0.0, 1.0)).toFloat() / uris.size } ?: -1f
                            status = "Adding ${index + 1}/${uris.size} · ${bytes / 1_000_000} MB" +
                                (expected?.let { " / ${it / 1_000_000} MB" } ?: "")
                        }
                    }
                }
                if(audio&&app.onklav.shared.Mp3Codec.isMp3(f)){
                    val normalized=File(folder,"${videoId()}.wav");added.add(normalized);val job=currentCoroutineContext()
                    var permissionCheck=0L;try{app.onklav.shared.Mp3Codec.decode(f,normalized,{job.ensureActive();val now=System.nanoTime();if(now-permissionCheck>200_000_000){authorize();permissionCheck=now}}){v->progress=v;status="Opening MP3 · ${(v*100).toInt()}%"};check(f.delete()&&normalized.renameTo(f)){"Unable to prepare MP3 audio"}}finally{normalized.delete()}
                }
                val isImage=!audio&&resolver.getType(uri)?.startsWith("image/")==true
                if(audio){val duration=videoAudioDuration(f);require(duration in 100..43_200_000){"Unable to read audio duration: $name"};VideoClip(file=f.name,name=name,sourceMs=duration,hasAudio=true)}else if(isImage){val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(f.path,opts);require(opts.outWidth>0&&opts.outHeight>0){"Unsupported image: $name"};VideoClip(file=f.name,name=name,sourceMs=5000,image=true,hasAudio=false)}else{val meta=MediaMetadataRetriever();try{meta.setDataSource(f.path);val d=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0;require(d in 100..43_200_000){"Unable to read duration: $name"};val video=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes";val hasAudio=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes";require(if(audio)hasAudio else video){if(audio)"This file has no audio" else "Choose a video or photo"};VideoClip(file=f.name,name=name,sourceMs=d,hasAudio=hasAudio)}finally{meta.release()}}
            }}
            val next=if(overlay)project.copy(layers=project.layers+imported.map{c->val offset=cursor.coerceAtMost((project.duration-100).coerceAtLeast(0));val clip=if(c.image)c.copy(sourceMs=min(c.sourceMs,project.duration-offset),end=min(c.end,project.duration-offset))else c.copy(end=min(c.end,project.duration-offset));VideoLayer(clip=videoClampClip(clip),offset=offset)})else if(audio)project.copy(music=project.music+imported.map{VideoMusic(file=it.file,name=it.name,sourceMs=it.sourceMs,offset=cursor.coerceAtMost((project.duration-100).coerceAtLeast(0)))})else project.copy(clips=project.clips+imported,ratio=if(project.clips.isEmpty())withContext(Dispatchers.IO){videoInitialCanvasRatio(File(folder,imported.first().file),imported.first().image,project.ratio)}else project.ratio)
            VideoProject.parse(next.json());val old=project
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable){withContext(Dispatchers.IO){authorize();store.save(next)};importedSaved=true;project=next;undo.add(old);if(undo.size>40)undo.removeAt(0);redo.clear();committed=project;transforming=false;selected=if(audio||overlay)selected else (project.clips.size-imported.size).coerceAtLeast(0);cursor=if(audio||overlay)cursor.coerceIn(0,project.duration)else clipOffset;if(audio)selectedAudioId=project.music.last().id;revision++;refresh();loadThumbnails();status=if(audio)"Sound added" else ""}
        }catch(e:Exception){if(!importedSaved)added.forEach{it.delete()};throw e}
    }}
    fun addTitle(){val c=clip?:return;if(c.titles.size>=20){status="Use up to 20 text layers per clip";error=true;return};val start=(cursor-clipOffset).coerceIn(0,(c.duration-100).coerceAtLeast(0));edit(c.copy(titles=c.titles+VideoTitle(start=start,end=min(c.duration,start+3000))));commit()}
    fun music(m:VideoMusic){val next=m.copy(fadeIn=m.fadeIn.coerceAtMost(m.duration),fadeOut=m.fadeOut.coerceAtMost(m.duration));change(project.copy(music=project.music.map{if(it.id==m.id)next else it}))}
    private fun loadThumbnails(){thumbs?.cancel();val p=project;thumbs=viewModelScope.launch{val images=withContext(Dispatchers.IO){(p.clips+p.layers.map{it.clip}).distinctBy{it.file}.mapNotNull{c->currentCoroutineContext().ensureActive();runCatching{val file=File(store.folder(p.id),c.file);val b=if(c.image){videoPhotoThumbnail(file)}else{val m=MediaMetadataRetriever();try{m.setDataSource(file.path);m.getScaledFrameAtTime(c.start*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,240,160)}finally{m.release()}};c.file to requireNotNull(b)}.getOrNull()}.toMap()};thumbnails=images}}

    fun overlay(layer:VideoLayer){change(project.copy(layers=project.layers.map{if(it.id==layer.id)layer else it}))}
    fun addKeyframe(layerId:String?=null){val l=project.layers.firstOrNull{it.id==layerId};if(l!=null)updateLayerKey(l.id,videoPose(l.clip,(cursor-l.offset).coerceIn(0,l.clip.duration)))else{val c=clip?:return;updateKey(videoPose(c,(cursor-clipOffset).coerceIn(0,c.duration)))};commit()}
    fun updateLayerKey(id:String,pose:VideoKeyframe){val l=project.layers.firstOrNull{it.id==id}?:return;runCatching{val c=l.clip;val base=if(c.keyframes.isEmpty())c.copy(keyframes=listOf(videoPose(c,0),videoPose(c,c.duration)),motion=false)else c;overlay(l.copy(clip=videoSetKey(base,pose)))}.onFailure{status=it.message.orEmpty();error=true}}
    fun layerOperation(id:String,op:String){if(busy)return;val l=project.layers.firstOrNull{it.id==id}?:return;runCatching{val index=project.layers.indexOf(l);val list=when(op){"Delete"->project.layers.filter{it.id!=id};"Duplicate"->{require(project.layers.size<4){"Use up to four overlays"};project.layers+l.copy(id=videoId())};else->{require(project.layers.size<4){"Use up to four overlays"};val split=VideoProject(clips=listOf(l.clip)).split(0,cursor-l.offset);project.layers.take(index)+l.copy(clip=split.clips[0])+l.copy(id=videoId(),clip=split.clips[1],offset=l.offset+split.clips[0].duration)+project.layers.drop(index+1)}};apply(project.copy(layers=list))}.onFailure{status=it.message.orEmpty();error=true}}
    fun updateKey(pose:VideoKeyframe){val c=clip?:return;runCatching{val base=if(c.keyframes.isEmpty())c.copy(keyframes=listOf(videoPose(c,0),videoPose(c,c.duration)),motion=false)else c;edit(videoSetKey(base,pose))}.onFailure{status=it.message.orEmpty();error=true}}
    fun transform(pose:VideoKeyframe){val c=clip?:return;if(c.keyframes.isNotEmpty())updateKey(pose)else edit(c.copy(zoom=pose.zoom,panX=pose.x,panY=pose.y))}
    fun extractAudio(){val c=clip?:return;if(!c.hasAudio||c.image)return;runCatching{require(project.music.size<4){"Use up to four sound tracks"};apply(project.copy(clips=project.clips.mapIndexed{i,v->if(i==selected)v.copy(volume=0f)else v},music=project.music+VideoMusic(file=c.file,name="Audio · ${c.name}",sourceMs=c.sourceMs,start=c.start,end=c.end,offset=clipOffset,volume=c.volume,fadeIn=c.fadeIn,fadeOut=c.fadeOut,speed=c.speed,rates=c.rates)))}.onFailure{status=it.message.orEmpty();error=true}}
    fun importLut(uri:Uri){val c=clip?:return;val snapshot=project;launch("Importing color LUT"){
        val file=File(store.folder(snapshot.id),"${videoId()}.media");var saved=false
        try{val name=withContext(Dispatchers.IO){val ctx=currentCoroutineContext();authorize();val resolver=getApplication<Application>().contentResolver;val title=resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:"Color LUT"
            resolver.openInputStream(uri).use{input->requireNotNull(input);file.outputStream().use{out->val buffer=ByteArray(8192);var count=0;while(true){ctx.ensureActive();authorize();val n=input.read(buffer);if(n<0)break;count+=n;require(count<=4_000_000){"Choose a LUT smaller than 4 MB"};out.write(buffer,0,n)}}};VideoLut.parse(file.readText());title.take(120)}
            publish(snapshot.copy(clips=snapshot.clips.map{if(it.id==c.id)it.copy(lut=file.name,lutName=name,lutAmount=1f)else it}),selected,cursor);saved=true;status="Color LUT imported"
        }finally{if(!saved)file.delete()}
    }}
    fun detectBeats(id:String){val track=project.music.firstOrNull{it.id==id}?:return;val snapshot=project;launch("Finding beat markers · first 10 minutes"){
        val beats=withContext(Dispatchers.IO){val ctx=currentCoroutineContext();videoDetectBeats(File(store.folder(snapshot.id),track.file),track.start,track.end){ctx.ensureActive();authorize()}}.map{source->track.offset+if(source<=track.start)0 else videoOutputDuration(track.start,source,track.speed,track.rates)}.filter{it<snapshot.duration}.distinct().sorted()
        publish(snapshot.copy(beats=beats),selected,cursor);status="${beats.size} approximate beat markers · Undo restores the previous markers"
    }}
    fun clearProxies(){launch("Clearing editing proxies"){withContext(Dispatchers.IO){authorize();val folder=File(store.folder(project.id),".proxies");if(folder.exists())check(folder.deleteRecursively()){ "Unable to clear proxies" }};status="Editing proxies cleared; originals kept"}}
    fun makeProxy(){val c=clip?:return;if(c.image||busy)return;val snapshot=project;val cached=videoMediaFile(store.folder(snapshot.id),c,true);if(cached!=File(store.folder(snapshot.id),c.file)){status="Editing proxy already ready · exports use original quality";error=false;return};launch("Preparing 360p editing proxy"){
        require(c.sourceMs<=1_800_000){"Proxies support sources up to 30 minutes; use a shorter source"};val folder=store.folder(snapshot.id);val final=videoProxyPath(folder,c);final.parentFile!!.mkdirs();val temp=File(final.path+".part.mp4");var ready=false
        try{authorize();val existing=final.parentFile!!.listFiles().orEmpty().sumOf{it.length()};require(existing<1_000_000_000&&folder.usableSpace>max(128_000_000L,c.sourceMs*160)){"Clear editing proxies or free storage before creating another"}
            val media=MediaItem.fromUri(File(folder,c.file).toURI().toString());val item=EditedMediaItem.Builder(media).setDurationUs(c.sourceMs*1000).setFrameRate(30).setEffects(Effects(emptyList(),listOf(androidx.media3.effect.Presentation.createForHeight(360),androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(30f)))).build()
            suspendCancellableCoroutine<Unit>{cont->val t=Transformer.Builder(getApplication()).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(DefaultEncoderFactory.Builder(getApplication()).setEnableFallback(true).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(1_000_000).build()).build()).addListener(object:Transformer.Listener{override fun onCompleted(composition:Composition,result:ExportResult){if(cont.isActive)cont.resume(Unit){_,_,_->}};override fun onError(composition:Composition,result:ExportResult,e:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Couldn’t make a proxy; the original is still available",e)))}}).build();transformer=t;cont.invokeOnCancellation{viewModelScope.launch(Dispatchers.Main.immediate){t.cancel()}};t.start(Composition.Builder(EditedMediaItemSequence.Builder(listOf(item)).build()).setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC).build(),temp.path)
                viewModelScope.launch{val h=ProgressHolder();while(cont.isActive){if(!videoEditorAccess(getApplication())||folder.usableSpace<64_000_000||temp.length()+existing>1_000_000_000){t.cancel();cont.resumeWith(Result.failure(IllegalStateException("Proxy stopped: storage or permission unavailable")));break};progress=if(t.getProgress(h)==Transformer.PROGRESS_STATE_AVAILABLE)h.progress/100f else -1f;delay(250)}}
            };transformer=null
            withContext(Dispatchers.IO){authorize();val meta=MediaMetadataRetriever();try{meta.setDataSource(temp.path);val duration=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0;require(abs(duration-c.sourceMs)<=250){"Proxy timing differs from the source; original retained"}}finally{meta.release()};check(temp.renameTo(final)){"Unable to save editing proxy"};File(final.path+".ready").writeText(c.sourceMs.toString())}
            ready=true;revision++;status="Editing proxy ready · exports use original quality"
        }finally{transformer?.cancel();transformer=null;temp.delete();if(!ready)File(final.path+".ready").delete()}
    }}
    fun importCaptions(uri:Uri){launch("Importing captions"){
        val cues=withContext(Dispatchers.IO){authorize();getApplication<Application>().contentResolver.openInputStream(uri).use{input->requireNotNull(input);val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2_000_000){"Choose a subtitle file smaller than 2 MB"};out.write(buffer,0,n)};videoParseCaptions(out.toString("UTF-8"))}}
        val next=videoAddCaptions(project,cues);VideoProject.parse(next.json());publish(next,selected,cursor);status="Captions imported"
    }}
    fun exportCaptions(uri:Uri){launch("Saving captions"){val text=videoCaptionsSrt(project);withContext(Dispatchers.IO){authorize();writeFiledExport(getApplication(),uri){out->out.write(text.toByteArray())}};status="Captions saved"}}
    fun backupProject(uri:Uri){launch("Saving portable project"){
        val snapshot=project;var success=false
        try{withContext(Dispatchers.IO){val ctx=currentCoroutineContext();authorize();requireMedia();videoCheckBackupDestination(snapshot,store.folder(snapshot.id),uri,getApplication<Application>().packageName);writeFiledExport(getApplication(),uri){out->VideoProjectArchive.write(snapshot,store.folder(snapshot.id),out){ctx.ensureActive();authorize()}}};success=true;status="Project and media saved"}
        finally{ /* Interrupted destinations are retained by Files for recovery. */ }
    }}
    fun restoreProject(uri:Uri){launch("Restoring portable project"){
        var staged:VideoProject?=null;var saved=false
        try{withContext(Dispatchers.IO){val ctx=currentCoroutineContext();authorize();getApplication<Application>().contentResolver.openInputStream(uri).use{input->staged=VideoProjectArchive.read(requireNotNull(input),store.root){ctx.ensureActive();authorize()}}}
            withContext(NonCancellable){publish(requireNotNull(staged),0,0);saved=true};status="Project restored · original kept"
        }finally{if(!saved)staged?.let{p->withContext(Dispatchers.IO+NonCancellable){store.folder(p.id).deleteRecursively()}}}
    }}
    private suspend fun publish(next:VideoProject,index:Int,at:Long){val old=project;withContext(NonCancellable){withContext(Dispatchers.IO){authorize();store.save(next)};project=next;undo.add(old);if(undo.size>40)undo.removeAt(0);redo.clear();committed=project;transforming=false;selected=index.coerceIn(0,(next.clips.size-1).coerceAtLeast(0));cursor=at.coerceIn(0,next.duration);revision++;refresh();loadThumbnails()}}
    fun freeze(){val c=clip?:return;if(c.image)return;val snapshot=project;val index=selected;val local=(cursor-clipOffset).coerceIn(0,c.duration);launch("Creating freeze frame"){
        val file=File(store.folder(snapshot.id),"${videoId()}.media");var saved=false
        try{withContext(Dispatchers.IO){authorize();val retriever=MediaMetadataRetriever();try{retriever.setDataSource(File(store.folder(snapshot.id),c.file).path);val bitmap=requireNotNull(retriever.getFrameAtTime(videoSourceAt(c,local)*1000,MediaMetadataRetriever.OPTION_CLOSEST)){"Unable to capture this frame"};try{file.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()}}finally{retriever.release()}}
            val pose=videoPose(c,local);val still=VideoClip(file=file.name,name="Freeze · ${c.name}",sourceMs=3000,image=true,hasAudio=false,rotate=c.rotate,flipX=c.flipX,flipY=c.flipY,zoom=pose.zoom,panX=pose.x,panY=pose.y,brightness=c.brightness,contrast=c.contrast,saturation=c.saturation,hue=c.hue,warmth=c.warmth,lut=c.lut,lutName=c.lutName,lutAmount=c.lutAmount,keyframes=listOf(pose.copy(at=0)))
            val split=runCatching{snapshot.split(index,local)}.getOrNull();val next=if(split!=null)split.copy(clips=split.clips.take(index+1)+still+split.clips.drop(index+1))else snapshot.copy(clips=snapshot.clips.take(index+1)+still+snapshot.clips.drop(index+1))
            VideoProject.parse(next.json());publish(next,index+1,if(split!=null)cursor else snapshot.clipStart(index)+c.duration);saved=true;status="Freeze frame added"
        }finally{if(!saved)file.delete()}
    }}
    var recording by mutableStateOf(false);private set
    var recordingMs by mutableLongStateOf(0);private set
    var recordingLevel by mutableFloatStateOf(0f);private set
    private var recorder:android.media.MediaRecorder?=null;private var recordingFile:File?=null;private var recordingOffset=0L;private var recordingTick:Job?=null
    fun startVoiceover(){if(busy||recording||project.clips.isEmpty())return;runCatching{
        authorize();check(videoEditorAccess(getApplication(),"audio.record")){"Microphone access is off for Video Edit"};check(androidx.core.content.ContextCompat.checkSelfPermission(getApplication(),android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){"Microphone permission is required"};require(project.music.size<4){"Use up to four sound tracks"};require(store.root.usableSpace>64_000_000){"Free storage before recording"}
        releasePreview();claimPlaybackFocus();val file=File(store.folder(project.id),"${videoId()}.media");recordingFile=file;recordingOffset=cursor.coerceAtMost((project.duration-100).coerceAtLeast(0));val r=android.media.MediaRecorder();recorder=r;r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);r.setAudioSamplingRate(44100);r.setAudioEncodingBitRate(128000);r.setOutputFile(file.path);r.prepare();r.start();recording=true;recordingMs=0;val start=android.os.SystemClock.elapsedRealtime();val limit=min(1_800_000L,project.duration-recordingOffset)
        recordingTick=viewModelScope.launch{while(isActive&&recording){delay(100);recordingMs=android.os.SystemClock.elapsedRealtime()-start;recordingLevel=runCatching{r.maxAmplitude/32768f}.getOrDefault(0f);if(!videoEditorAccess(getApplication(),"audio.record")||!videoEditorAccess(getApplication())||store.root.usableSpace<32_000_000){stopVoiceover(true);status="Recording stopped: access or storage changed";error=true;break};if(recordingMs>=limit){stopVoiceover();break}}}
    }.onFailure{recorder?.release();recorder=null;recordingFile?.delete();recordingFile=null;recording=false;dropPlaybackFocus();status=it.message?:"Unable to record";error=true}}
    fun stopVoiceover(discard:Boolean=false){if(!recording)return;recording=false;recordingTick?.cancel();recordingTick=null;val file=recordingFile;recordingFile=null;var saved=false
        try{recorder?.stop();recorder?.release();recorder=null;if(!discard&&file!=null){authorize();val meta=MediaMetadataRetriever();val duration=try{meta.setDataSource(file.path);meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0}finally{meta.release()};require(duration>=100){"Record a little longer before saving"};val end=min(duration,project.duration-recordingOffset);require(end>=100);val before=project;apply(project.copy(music=project.music+VideoMusic(file=file.name,name="Voiceover",sourceMs=duration,end=end,offset=recordingOffset,volume=1f,narration=true)));saved=committed==project&&project.music.any{it.file==file.name};if(!saved){project=before;revision++};status=if(saved)"Voiceover saved"else"Unable to save voiceover"}}
        catch(e:Exception){status=e.message?:"Unable to save recording";error=true}finally{recorder?.release();recorder=null;if(!saved)file?.delete();recordingLevel=0f;dropPlaybackFocus()}
    }
    private fun claimPlaybackFocus(){
        authorize();check(videoEditorAccess(getApplication(),"audio.playback")){"Playback access is off"}
        HostSpeechOwner.claim(this){if(recording)stopVoiceover()else releasePreview()};if(focus!=null)return
        val manager=getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val req=android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener{if(it<=0){if(recording)stopVoiceover()else releasePreview()}}.build()
        check(manager.requestAudioFocus(req)==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Audio focus unavailable"};focus=req
    }
    private fun dropPlaybackFocus(){val previous=focus;focus=null;runCatching{previous?.let{(getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).abandonAudioFocusRequest(it)}};HostSpeechOwner.release(this)}
    fun preview(autoPlay:Boolean=true){
        if(busy||recording||transforming||project.clips.isEmpty()||!autoPlay&&(hasUnsavedChanges||failedPreviewRevision==revision))return
        if (player == null || (autoPlay && !playing)) {
            runCatching {
                if (autoPlay) claimPlaybackFocus()
                else {
                    authorize()
                    check(videoEditorAccess(getApplication(), "audio.playback")) { "Playback access is off" }
                }
            }.onFailure {
                releasePreview()
                status = it.message ?: "Playback unavailable"
                error = true
                return
            }
        }
        runCatching{
            player?.let { active ->
                if (!autoPlay) return
                if (playing) {
                    active.pause()
                    playing = false
                    cursor = active.currentPosition.coerceIn(0, project.duration)
                    dropPlaybackFocus()
                } else {
                    if (cursor >= project.duration || active.playbackState == Player.STATE_ENDED) {
                        active.seekTo(0)
                        cursor = 0
                    }
                    playing = true
                    active.play()
                }
                return
            }
            requireMedia()
            if(VideoComposition.directPreview(project)){val c=project.clips.single();val item=MediaItem.Builder().setUri(videoMediaFile(store.folder(project.id),c,true).toURI().toString()).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build();standardPreview(item,autoPlay,c.volume);return}
            if(VideoComposition.directVisualPreview(project)&&project.music.isNotEmpty()){soundtrackPreview(autoPlay);return}
            if(compatibilityRevision==revision){compatiblePreview(autoPlay);return}
            val p=CompositionPlayer.Builder(getApplication()).setVideoGraphFactory(MultipleInputVideoGraph.Factory()).build();player=p
            observePreview(p) { e, resumePlayback ->
                if (videoNeedsCompatiblePreview(e.errorCode)) {
                    compatibilityRevision = revision
                    compatiblePreview(resumePlayback)
                } else {
                    status = "Couldn’t preview this clip. ${e.errorCodeName}"
                    error = true
                }
            }
            p.setComposition(VideoComposition.build(project,store.folder(project.id),true));p.prepare();p.seekTo(videoPreviewPosition(cursor, project.duration, autoPlay));if(autoPlay&&player===p){playing=true;p.play()}
        }.onFailure{releasePreview();if(store.missing(project).isNotEmpty()){failedPreviewRevision=revision;status=it.message?:"Project media is missing";error=true}else if(it is IllegalStateException||it is IllegalArgumentException){failedPreviewRevision=revision;compatibilityRevision=revision;compatiblePreview(autoPlay)}else{status=it.message?:"Unable to preview";error=true}}
    }
    private fun standardPreview(item:MediaItem,autoPlay:Boolean,volume:Float=1f,source:MediaSource?=null){
        val p=ExoPlayer.Builder(getApplication(),DefaultRenderersFactory(getApplication()).setEnableDecoderFallback(true)).build();p.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT);player=p;p.volume=volume
        observePreview(p) { _, _ ->
            status = "Couldn’t play the preview. Try another clip or retry."
            error = true
        }
        if(source==null)p.setMediaItem(item)else p.setMediaSource(source);p.seekTo(videoPreviewPosition(cursor, project.duration, autoPlay));p.prepare();if(autoPlay&&player===p){if(focus==null)claimPlaybackFocus();playing=true;p.play()}
    }

    private fun observePreview(p: Player, onError: (PlaybackException, Boolean) -> Unit) {
        p.addListener(object : Player.Listener {
            override fun onEvents(source: Player, events: Player.Events) {
                if (player !== p || p.playerError != null) return
                playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && p.playbackState == Player.STATE_ENDED) {
                    cursor = project.duration
                    p.pause()
                    dropPlaybackFocus()
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (player !== p) return
                val resumePlayback = playing
                failedPreviewRevision = revision
                releasePreview()
                onError(e, resumePlayback)
            }
        })
        tick = viewModelScope.launch {
            var lastAccessCheck = android.os.SystemClock.elapsedRealtime()
            while (isActive && player === p) {
                delay(if (playing) 100 else 750)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastAccessCheck >= 750) {
                    val allowed = withContext(Dispatchers.IO) {
                        videoEditorHasAccess(getApplication(), "storage.private", "audio.playback")
                    }
                    if (player !== p) break
                    if (!allowed) {
                        releasePreview()
                        status = "Playback permission removed"
                        error = true
                        break
                    }
                    lastAccessCheck = now
                }
                if (p.isPlaying) cursor = p.currentPosition.coerceIn(0, project.duration)
            }
        }
    }

    /** Mix only sound; keep ordinary video decoding for a plain clip with added music.
     * This avoids routing a simple soundtrack edit through the experimental GPU compositor.
     */
    private fun soundtrackPreview(autoPlay:Boolean){
        fun play(file:File){
            val c=project.clips.single()
            val media=MediaItem.Builder().setUri(videoMediaFile(store.folder(project.id),c,true).toURI().toString())
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()).build()
            val factory=DefaultMediaSourceFactory(getApplication<Application>())
            val source=MergingMediaSource(true,true,FilteringMediaSource(factory.createMediaSource(media),C.TRACK_TYPE_VIDEO),factory.createMediaSource(MediaItem.fromUri(file.toURI().toString())))
            standardPreview(media,autoPlay,source=source);status="";error=false
        }
        soundtrackFile?.takeIf{it.first==revision&&it.second.isFile&&it.second.length()>0}?.let{play(it.second);return}
        launch("Preparing soundtrack preview"){
            val temp=File(getApplication<Application>().cacheDir,"video-soundtrack-${videoId()}.m4a");var complete=false
            try{
                authorize();check(temp.parentFile!!.usableSpace>64_000_000){"Free at least 64 MB to prepare this soundtrack"}
                val comp=VideoComposition.build(project,store.folder(project.id),true,true,audioOnly=true)
                suspendCancellableCoroutine<Unit>{cont->
                    val t=Transformer.Builder(getApplication()).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(DefaultEncoderFactory.Builder(getApplication()).setEnableFallback(true).build())
                        .addListener(object:Transformer.Listener{
                            override fun onCompleted(c:Composition,r:ExportResult){if(cont.isActive)cont.resume(Unit){_,_,_->}}
                            override fun onError(c:Composition,r:ExportResult,e:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Soundtrack preview failed (${e.errorCodeName}). Your project and audio are kept.",e)))}
                        }).build()
                    transformer=t;cont.invokeOnCancellation{viewModelScope.launch(Dispatchers.Main.immediate){t.cancel()}}
                    t.start(comp,temp.path)
                    viewModelScope.launch{val h=ProgressHolder();while(cont.isActive){
                        if(!videoEditorAccess(getApplication())||!videoEditorAccess(getApplication(),"audio.playback")||temp.parentFile!!.usableSpace<32_000_000){t.cancel();cont.resumeWith(Result.failure(IllegalStateException("Soundtrack preview stopped: access or free storage changed")));break}
                        progress=if(t.getProgress(h)==Transformer.PROGRESS_STATE_AVAILABLE)h.progress/100f else -1f;delay(250)
                    }}
                }
                transformer=null;soundtrackFile?.second?.delete();soundtrackFile=revision to temp;play(temp);complete=true
            }finally{transformer?.cancel();transformer=null;if(!complete)temp.delete()}
        }
    }
    private fun compatiblePreview(autoPlay:Boolean){
        compatibleFile?.takeIf{it.first==revision&&it.second.isFile&&it.second.length()>0}?.let{standardPreview(MediaItem.fromUri(it.second.toURI().toString()),autoPlay);status="";error=false;return}
        launch("Preparing compatible preview"){
        val snapshot=project;val temp=File(getApplication<Application>().cacheDir,"video-preview-${videoId()}.mp4");var complete=false
        try{authorize();check(temp.parentFile!!.usableSpace>128_000_000){"Free at least 128 MB to prepare a compatible preview"};val comp=VideoComposition.build(snapshot,store.folder(snapshot.id),true,true)
            suspendCancellableCoroutine<Unit>{cont->val t=Transformer.Builder(getApplication()).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(DefaultEncoderFactory.Builder(getApplication()).setEnableFallback(true).build()).addListener(object:Transformer.Listener{override fun onCompleted(c:Composition,r:ExportResult){if(cont.isActive)cont.resume(Unit){_,_,_->}};override fun onError(c:Composition,r:ExportResult,e:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Preview preparation failed (${e.errorCodeName}). Your project and audio are kept.",e)))}}).build();transformer=t;cont.invokeOnCancellation{viewModelScope.launch(Dispatchers.Main.immediate){t.cancel()}};t.start(comp,temp.path);viewModelScope.launch{val h=ProgressHolder();while(cont.isActive){if(!videoEditorAccess(getApplication())||!videoEditorAccess(getApplication(),"audio.playback")){t.cancel();cont.resumeWith(Result.failure(IllegalStateException("Playback access is off")));break};progress=if(t.getProgress(h)==Transformer.PROGRESS_STATE_AVAILABLE)h.progress/100f else -1f;delay(250)}}}
            transformer=null;compatibleFile=revision to temp;standardPreview(MediaItem.fromUri(temp.toURI().toString()),autoPlay);complete=true;status="";error=false
        }finally{transformer?.cancel();transformer=null;if(!complete)temp.delete();getApplication<Application>().cacheDir.listFiles()?.filter{it.name.startsWith("video-preview-")&&it!=temp}?.forEach{it.delete()}}
    }}
    fun releasePreview(){tick?.cancel();tick=null;val previous=player;player=null;playing=false;runCatching{previous?.release()};dropPlaybackFocus()}
    fun export(uri:Uri,settings:VideoOutputSettings?=null){if(busy||recording||project.clips.isEmpty()||!readyToSwitch())return;destination=uri;launch("Preparing export"){
        val temp=File(getApplication<Application>().cacheDir,"video-${videoId()}.mp4");var success=false
        try{authorize();requireMedia();val snapshot=settings?.project(project)?:project;require(store.root.usableSpace>128_000_000){"Free at least 128 MB before exporting"};val comp=VideoComposition.build(snapshot,store.folder(snapshot.id));suspendCancellableCoroutine<Unit>{cont->
                val t=Transformer.Builder(getApplication()).setEncoderFactory(DefaultEncoderFactory.Builder(getApplication()).setEnableFallback(true).apply{if(snapshot.bitrate>0)setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(snapshot.bitrate).build())}.build()).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(object:Transformer.Listener{override fun onCompleted(composition:Composition,result:ExportResult){if(cont.isActive)cont.resume(Unit){_,_,_->}};override fun onError(composition:Composition,result:ExportResult,e:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Export failed (${e.errorCodeName}). Try 720p / 30 fps or a different clip.",e)))}}).build();transformer=t;cont.invokeOnCancellation{viewModelScope.launch(Dispatchers.Main.immediate){t.cancel()}};t.start(comp,temp.path)
                viewModelScope.launch{val holder=ProgressHolder();while(cont.isActive){if(!videoEditorAccess(getApplication())){t.cancel();cont.resumeWith(Result.failure(IllegalStateException("Storage permission removed")));break};val state=t.getProgress(holder);progress=if(state==Transformer.PROGRESS_STATE_AVAILABLE)holder.progress/100f else -1f;status=if(progress>=0)"Exporting · ${(progress*100).toInt()}%" else "Exporting";if(requireNotNull(temp.parentFile).usableSpace<32_000_000){t.cancel();cont.resumeWith(Result.failure(IllegalStateException("Not enough storage to finish export")));break};delay(250)}}
            }
            transformer=null;progress=-1f;status="Saving video"
            withContext(Dispatchers.IO) {
                authorize()
                writeFiledExport(getApplication(), uri) { out ->
                    temp.inputStream().use { input -> copyVideoMedia(input, out, checkAccess = ::authorize) }
                }
            }
            success=true;status="Video saved · ${android.text.format.Formatter.formatShortFileSize(getApplication(),temp.length())}";progress=1f
        }finally{transformer?.cancel();transformer=null;temp.delete();destination=null}
    }}
    fun cancel(){if(recording)stopVoiceover();if(!busy)commit();job?.cancel();transformer?.cancel();releasePreview()}
    override fun onCleared(){soundtrackFile?.second?.delete();compatibleFile?.second?.delete();if(recording)stopVoiceover();job?.cancel();thumbs?.cancel();transformer?.cancel();releasePreview()}
}

internal fun videoNeedsCompatiblePreview(code:Int):Boolean=code in setOf(PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,PlaybackException.ERROR_CODE_DECODING_FAILED)

internal fun videoPreviewPosition(cursor: Long, duration: Long, autoPlay: Boolean): Long {
    if (cursor < duration) return cursor.coerceAtLeast(0)
    return if (autoPlay) 0 else (duration - 1).coerceAtLeast(0)
}

internal suspend fun copyVideoMedia(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long = Long.MAX_VALUE,
    checkAccess: () -> Unit,
    reportProgress: (Long) -> Unit = {},
) {
    val job = currentCoroutineContext()
    val buffer = ByteArray(131072)
    var copied = 0L
    var lastCheck = System.nanoTime()
    job.ensureActive()
    checkAccess()
    while (true) {
        job.ensureActive()
        val count = input.read(buffer)
        job.ensureActive()
        if (count < 0) break
        val now = System.nanoTime()
        if (now - lastCheck >= 200_000_000L) {
            checkAccess()
            reportProgress(copied)
            lastCheck = now
        }
        require(count <= maxBytes - copied) { "Choose a file smaller than 4 GB" }
        output.write(buffer, 0, count)
        copied += count
    }
    checkAccess()
    reportProgress(copied)
}
