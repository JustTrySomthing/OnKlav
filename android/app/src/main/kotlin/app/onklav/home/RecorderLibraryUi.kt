package app.onklav.home

import android.app.Application
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.media.*
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File

/** Read-only, one-recording grants. Every open rechecks the live signed Files
 * broker; unfinalized drafts, other folders and arbitrary paths are rejected. */
class RecorderProvider:ContentProvider(){
 override fun onCreate()=true
 private fun resolve(uri:Uri):Pair<File,RecorderItem>{val c=requireNotNull(context);require(uri.authority==c.packageName+".recordings"&&uri.pathSegments.size==3);val(kind,source,id)=uri.pathSegments;require(source in setOf("draft","file"));return RecorderLibrary.resolve(c,RecorderKind.valueOf(kind),id,source=="draft")}
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{require(mode=="r"){"Recordings are read-only"};return ParcelFileDescriptor.open(resolve(uri).first,ParcelFileDescriptor.MODE_READ_ONLY)}
 override fun getType(uri:Uri)=resolve(uri).second.mime
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor{val(f,item)=resolve(uri);val cols=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE);return MatrixCursor(cols).apply{addRow(cols.map{when(it){OpenableColumns.DISPLAY_NAME->item.name;OpenableColumns.SIZE->f.length();else->null}})}}
 override fun insert(uri:Uri,values:ContentValues?):Uri=throw UnsupportedOperationException()
 override fun update(uri:Uri,values:ContentValues?,s:String?,a:Array<out String>?)=throw UnsupportedOperationException()
 override fun delete(uri:Uri,s:String?,a:Array<out String>?)=throw UnsupportedOperationException()
}
internal class RecorderLibraryModel(app:Application):AndroidViewModel(app){
 var items by mutableStateOf<List<RecorderItem>>(emptyList());private set
 var busy by mutableStateOf(false);private set
 var error by mutableStateOf<String?>(null);private set
 var message by mutableStateOf("");private set
 var playing by mutableStateOf<RecorderItem?>(null);private set
 var isPlaying by mutableStateOf(false);private set
 var duration by mutableLongStateOf(0);private set
 var position by mutableLongStateOf(0);private set
 var speed by mutableFloatStateOf(1f);private set
 var playbackReady by mutableStateOf(false);private set
 private var player:MediaPlayer?=null;private var ticker:Job?=null;private var focus:AudioFocusRequest?=null
 private var refreshTask:Job?=null;private var work:Job?=null;private var generation=0;private var queuedRefresh:RecorderKind?=null
 fun refresh(kind:RecorderKind){if(work?.isActive==true){queuedRefresh=kind;return};refreshTask?.cancel();val version=++generation;busy=true
  refreshTask=viewModelScope.launch{try{val list=withContext(Dispatchers.IO){RecorderLibrary.list(getApplication(),kind)};if(version==generation){items=list;error=null}}
   catch(e:Exception){if(e is CancellationException)throw e;if(version==generation)error=e.message?:"Could not load recordings"}finally{if(version==generation)busy=false}}
 }
 private fun run(kind:RecorderKind,block:suspend ()->Unit){if(busy)return;busy=true;error=null;message=""
  work=viewModelScope.launch{try{block();items=withContext(Dispatchers.IO){RecorderLibrary.list(getApplication(),kind)}}catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:"Could not update recording"}finally{busy=false;work=null;queuedRefresh?.let{queuedRefresh=null;refresh(it)}}}
 }
 fun rename(item:RecorderItem,title:String){run(item.kind){withContext(Dispatchers.IO){RecorderLibrary.rename(getApplication(),item,title)};message="Renamed"}}
 fun delete(item:RecorderItem){closePlayer();run(item.kind){withContext(Dispatchers.IO){RecorderLibrary.delete(getApplication(),item)};message=if(item.pending)"Draft deleted"else"Moved to Files Trash"}}
 fun recover(item:RecorderItem){run(item.kind){withContext(Dispatchers.IO){
  if(item.interrupted){val c=getApplication<Application>();val j=RecorderLibrary.read(c,item.id);val ext=j.getString("extension");val format=if(ext=="wav")RecorderFormat.WAV else RecorderFormat.AAC
   val candidate=RecorderLibrary.file(c,item.id,if(item.kind==RecorderKind.SCREEN)"video.part.mp4"else"audio.part.$ext");val ready=RecorderLibrary.draft(c,item.id);var warning="Recovered after an interruption"
   if(!runCatching{recorderValidate(ready,item.kind,format)}.isSuccess){
    recorderValidate(candidate,item.kind,format)
    val captured=RecorderLibrary.file(c,item.id,"capture.part.m4a")
    if(item.kind==RecorderKind.SCREEN&&j.optString("sound") in setOf("DEVICE","BOTH")){
     if(runCatching{recorderValidate(captured,RecorderKind.VOICE)}.isSuccess){val temp=RecorderLibrary.file(c,item.id,"recovered.mp4");try{recorderMux(candidate,captured,temp,0);recorderValidate(temp,RecorderKind.SCREEN);check(temp.renameTo(ready)){"Recovered video could not be retained"}}finally{temp.delete()}}
     else{check(candidate.renameTo(ready)){"Draft could not be recovered"};warning="Recovered video without audio; the interrupted soundtrack was not playable."}
    }else check(candidate.renameTo(ready)){"Draft could not be recovered"}
   }
   RecorderLibrary.ready(c,item.id,item.duration,warning)
  };RecorderLibrary.publish(getApplication(),item.id)
 };message="Saved in Files"}}
 suspend fun uri(item:RecorderItem):Uri=withContext(Dispatchers.IO){RecorderLibrary.resolve(getApplication(),item.kind,item.id,item.pending);RecorderLibrary.uri(getApplication(),item)}
 fun export(item:RecorderItem,uri:Uri){run(item.kind){val source=withContext(Dispatchers.IO){RecorderLibrary.resolve(getApplication(),item.kind,item.id,item.pending).first};writeFiledExport(getApplication(),uri){output->source.inputStream().use{input->val bytes=ByteArray(131072);while(true){currentCoroutineContext().ensureActive();val n=input.read(bytes);if(n<0)break;output.write(bytes,0,n)}}};message="Copy saved in your chosen folder"}}
 fun openPlayer(item:RecorderItem){closePlayer();playing=item;playbackReady=false;error=null
  ticker=viewModelScope.launch{try{val source=withContext(Dispatchers.IO){RecorderLibrary.resolve(getApplication(),item.kind,item.id,item.pending).first};if(playing?.id!=item.id)return@launch
   val p=MediaPlayer();player=p;p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());p.setDataSource(source.path)
   p.setOnPreparedListener{if(player===p){duration=p.duration.toLong();playbackReady=true;position=0}}
   p.setOnCompletionListener{if(player===p){isPlaying=false;position=duration;dropFocus()}}
   p.setOnErrorListener{_,_,_->if(player===p){error="This recording cannot be played";closePlayer()};true};p.prepareAsync()
   while(isActive&&playing?.id==item.id){if(playbackReady&&player===p)runCatching{position=p.currentPosition.toLong()};delay(200)}
  }catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:"Could not open recording";closePlayer()}}
 }
 fun playPause(){val p=player?:return;if(!playbackReady)return
  try{if(isPlaying){p.pause();isPlaying=false;dropFocus()}else{
   if(RecordingRuntime.state.value.active){error="Pause or stop recording before playback";return}
   val audio=getApplication<Application>().getSystemService(AudioManager::class.java);val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).setOnAudioFocusChangeListener{change->if(change<0)viewModelScope.launch{pausePlayer()}}.build();check(audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"Audio is busy"};focus=request
   if(position>=duration)p.seekTo(0);p.playbackParams=PlaybackParams().setSpeed(speed);p.start();isPlaying=true
  }}catch(e:Exception){error=e.message?:"Could not play recording";dropFocus()}
 }
 fun seek(to:Long){if(playbackReady){position=to.coerceIn(0,duration);player?.seekTo(position.toInt())}}
 fun changeSpeed(value:Float){require(value in listOf(1f,1.25f,1.5f,2f));speed=value;if(playbackReady&&isPlaying)player?.playbackParams=PlaybackParams().setSpeed(value)}
 private fun dropFocus(){focus?.let{runCatching{getApplication<Application>().getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)}};focus=null}
 fun pausePlayer(){if(isPlaying)runCatching{player?.pause()};isPlaying=false;dropFocus()}
 fun closePlayer(){ticker?.cancel();ticker=null;runCatching{player?.release()};player=null;playing=null;isPlaying=false;playbackReady=false;dropFocus()}
 override fun onCleared(){generation++;refreshTask?.cancel();work?.cancel();closePlayer();super.onCleared()}
}
