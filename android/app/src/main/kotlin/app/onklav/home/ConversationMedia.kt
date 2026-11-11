package app.onklav.home

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.media.*
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

internal data class ChatMedia(val message:Message,val part:MessagePart)
internal fun isConversationMedia(part:MessagePart) =
 !MessageMedia.isVCard(part.mime,part.name) && (part.mime.startsWith("image/")||part.mime.startsWith("video/")||part.mime.startsWith("audio/"))
internal fun conversationMedia(messages:List<Message>):List<ChatMedia> = messages.sortedByDescending{it.date}.flatMap{m->m.parts.filter(::isConversationMedia).map{ChatMedia(m,it)}}.distinctBy{it.part.uri}
internal fun mediaViewerSnapshot(items:List<ChatMedia>)=items.distinctBy{it.part.uri}.toList()
internal object MediaPreferences {
 val decoders=kotlinx.coroutines.sync.Semaphore(2)
 val epoch=MutableStateFlow(0L)
 fun gifs(c:Context)=c.getSharedPreferences("message-media",0).getBoolean("animate_gifs",true)
 fun fill(c:Context)=c.getSharedPreferences("message-media",0).getBoolean("fill_images",false)
 fun set(c:Context,key:String,value:Boolean){check(c.getSharedPreferences("message-media",0).edit().putBoolean(key,value).commit());epoch.value++}
}

/** Android's animated decoder is lifecycle-owned; only visible lazy rows allocate a drawable. */
@Composable internal fun AnimatedMessageImage(part:MessagePart,modifier:Modifier=Modifier,fit:Boolean=false,maxSize:Int=256,active:Boolean=true,naturalHeight:Boolean=false){
 val c=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle;val epoch by MediaPreferences.epoch.collectAsState()
 var failure by remember(part.uri){mutableStateOf(false)}
 val drawable by produceState<Drawable?>(null,part.uri,maxSize){
  value=withContext(Dispatchers.IO){MediaPreferences.decoders.withPermit{try{ImageDecoder.decodeDrawable(ImageDecoder.createSource(c.contentResolver,Uri.parse(part.uri))){decoder,info,_->
   require(info.size.width>0&&info.size.height>0&&info.size.width.toLong()*info.size.height<=100_000_000){"Image is too large"}
   decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM)
   val size=if(part.mime=="image/gif")minOf(maxSize,768)else maxSize;val scale=minOf(1f,size.toFloat()/maxOf(info.size.width,info.size.height));decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()))
  }}catch(e:CancellationException){throw e}catch(e:Exception){failure=true;null}catch(e:OutOfMemoryError){failure=true;null}}}
 }
 DisposableEffect(drawable,lifecycle,epoch,active){
  val animation=drawable as? Animatable
  fun update(){if(active&&MediaPreferences.gifs(c)&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))animation?.start()else animation?.stop()}
  val observer=LifecycleEventObserver{_,_->update()};lifecycle.addObserver(observer);update()
  onDispose{lifecycle.removeObserver(observer);animation?.stop()}
 }
 if(drawable!=null)AndroidView(factory={ImageView(it)},onReset=null,onRelease={it.setImageDrawable(null)},modifier=modifier.then(if(naturalHeight&&drawable!=null)Modifier.fillMaxWidth().heightIn(max=640.dp).aspectRatio(drawable!!.intrinsicWidth.toFloat().coerceAtLeast(1f)/drawable!!.intrinsicHeight.coerceAtLeast(1))else Modifier),update={it.scaleType=if(fit)ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP;it.contentDescription=part.name;it.setImageDrawable(drawable)})
 else Box(modifier,contentAlignment=Alignment.Center){Text(if(failure)"Image unavailable"else"Loading image…",fontSize=12.sp)}
}

@Composable internal fun ConversationGallery(items:List<ChatMedia>,state:JSONObject,loading:Boolean,error:String?,open:(ChatMedia)->Unit,retry:()->Unit,star:(Message,Boolean)->Unit,more:Boolean=false,loadOlder:()->Unit={}){
 val grid=rememberLazyGridState()
 LaunchedEffect(grid,items.size,more){snapshotFlow{grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:0}.collectLatest{if(items.isNotEmpty()&&more&&!loading&&it>=items.size-6)loadOlder()}}
 Column(Modifier.fillMaxSize()){
  if(loading&&items.isEmpty())LinearProgressIndicator(Modifier.fillMaxWidth())
  if(error!=null){Text(error,Modifier.padding(12.dp));TextButton(onClick=retry){Text("Try Again")}}
  LazyVerticalGrid(columns=GridCells.Fixed(3),state=grid,modifier=Modifier.fillMaxSize().testTag("conversation-media-grid"),contentPadding=PaddingValues(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
   items(items,key={it.part.uri}){entry->Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable{open(entry)}.testTag("gallery-${entry.message.key}")){
    MessageThumbnail(entry.part,Modifier.fillMaxSize()){open(entry)}
    if(entry.part.mime.startsWith("audio/")||entry.part.mime.startsWith("video/"))Text(if(entry.part.mime.startsWith("audio/"))"♫ Audio"else"▶ Video",Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha=.65f)).padding(5.dp),color=Color.White,fontSize=12.sp)
    ThemeStar(state.messageFlags(entry.message).optBoolean("starred"),{star(entry.message,it)},Modifier.align(Alignment.TopEnd).padding(5.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp)))
   }}
   if(more)item(span={GridItemSpan(3)}){TextButton(enabled=!loading,onClick=loadOlder,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Loading older media…"else"Load older media")}}
   if(items.isEmpty()&&!loading&&error==null&&!more)item(span={GridItemSpan(3)}){Text("No photos, videos or audio in this conversation.",Modifier.padding(20.dp))}
  }
 }
}

@Composable internal fun ConversationMediaViewer(items:List<ChatMedia>,initialUri:String,state:JSONObject,star:(Message,Boolean)->Unit,close:()->Unit){
 val pages=remember(initialUri){mediaViewerSnapshot(items)}
 val c=LocalContext.current;val scope=rememberCoroutineScope();val prefsEpoch by MediaPreferences.epoch.collectAsState()
 var selectedUri by remember(initialUri){mutableStateOf(initialUri)}
 val pager=rememberPagerState(initialPage=pages.indexOfFirst{it.part.uri==initialUri}.coerceAtLeast(0),pageCount={pages.size})
 val landscape=androidx.compose.ui.platform.LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
 val videoSelected=pages.getOrNull(pager.currentPage)?.part?.mime?.startsWith("video/")==true
 var manualFullScreen by remember{mutableStateOf(false)}
 MediaAutoRotation(videoSelected)
 val fullScreen=manualFullScreen||(landscape&&videoSelected)
 LaunchedEffect(landscape){manualFullScreen=false}
 var actions by remember{mutableStateOf(false)}
 var zoomed by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(pages.map{it.part.uri}){val at=pages.indexOfFirst{it.part.uri==selectedUri};if(at>=0&&at!=pager.currentPage)pager.scrollToPage(at)}
 LaunchedEffect(pager.settledPage){pages.getOrNull(pager.settledPage)?.let{selectedUri=it.part.uri};zoomed=false}
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  ImmersiveMediaMode(fullScreen)
  Surface(Modifier.fillMaxSize().testTag("conversation-media-viewer"),color=if(fullScreen)Color.Black else MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().then(if(fullScreen)Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))){
   if(!fullScreen)HomeHeader("${(pager.currentPage+1).coerceAtMost(pages.size)} / ${pages.size}",back=close){
    TextButton(onClick={manualFullScreen=true}){Text("Full screen")}
    Box{TextButton(onClick={actions=true}){Text("More")};DropdownMenu(actions,{actions=false}){
     DropdownMenuItem(text={Text("Settings")},onClick={actions=false;settings=true})
     if(!fullScreen)pages.getOrNull(pager.currentPage)?.let{entry->
      DropdownMenuItem(text={Text(if(busy)"Saving…"else"Save")},enabled=!busy,onClick={actions=false;busy=true;scope.launch{try{MessageMedia.save(c,entry.part);status="Saved in Files"}catch(e:CancellationException){throw e}catch(e:Exception){status=e.message}finally{busy=false}}})
      DropdownMenuItem(text={Text("Share")},onClick={actions=false;scope.launch{try{withContext(Dispatchers.IO){MessageMedia.share(c,entry.part)}}catch(e:Exception){status=e.message}}})
      DropdownMenuItem(text={Text("Open With")},onClick={actions=false;scope.launch{try{withContext(Dispatchers.IO){MessageMedia.open(c,entry.part)}}catch(e:Exception){status=e.message}}})
     }
    }}
   }
   if(fullScreen&&!videoSelected)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={manualFullScreen=false}){Text("Exit full screen")}}
   HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth(),userScrollEnabled=!zoomed,key={pages.getOrNull(it)?.part?.uri?:"page-$it"}){page->
    val part=pages.getOrNull(page)?.part?:return@HorizontalPager
    if(part.mime.startsWith("image/")){
     var scale by remember(part.uri){mutableFloatStateOf(1f)};var x by remember(part.uri){mutableFloatStateOf(0f)};var y by remember(part.uri){mutableFloatStateOf(0f)}
     val fill=MediaPreferences.fill(c)
     var viewport by remember{mutableStateOf(IntSize.Zero)}
     val transform=rememberTransformableState{zoom,pan,_->scale=(scale*zoom).coerceIn(1f,5f);x=if(scale==1f)0f else (x+pan.x).coerceIn(-viewport.width*(scale-1)/2,viewport.width*(scale-1)/2);y=if(scale==1f)0f else (y+pan.y).coerceIn(-viewport.height*(scale-1)/2,viewport.height*(scale-1)/2);if(page==pager.currentPage)zoomed=scale>1.01f}
     Box(Modifier.fillMaxSize().onSizeChanged{viewport=it}.clip(androidx.compose.ui.graphics.RectangleShape).transformable(state=transform,canPan={scale>1.01f}),contentAlignment=Alignment.Center){
      AnimatedMessageImage(part,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y},fit=!fill,maxSize=1280,active=page==pager.currentPage)
     }
    }else if(page==pager.currentPage)key(part.uri){InternalMessagePlayer(part,active=true,immersive=fullScreen,fullScreen={manualFullScreen=!manualFullScreen})}
   }
   if(!fullScreen)pages.getOrNull(pager.currentPage)?.let{entry->
    Text(entry.part.name,Modifier.padding(horizontal=16.dp,vertical=6.dp),maxLines=2,fontSize=15.sp)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
     TextButton(onClick={scope.launch{pager.animateScrollToPage((pager.currentPage-1).coerceAtLeast(0))}},enabled=pager.currentPage>0){Text("‹ Previous")}
     if(entry.message.id!=0L)ThemeStar(state.messageFlags(entry.message).optBoolean("starred"),{star(entry.message,it)})
     TextButton(onClick={scope.launch{pager.animateScrollToPage((pager.currentPage+1).coerceAtMost(pages.lastIndex))}},enabled=pager.currentPage<pages.lastIndex){Text("Next ›")}
    }

   }
   status?.let{Text(it,Modifier.padding(12.dp),fontSize=13.sp)}
  }}
 }
 if(settings)ReferenceDialog("Media Settings",{settings=false},footer={DialogAction("Done"){settings=false}}){Column(Modifier.padding(20.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Play animated GIFs",Modifier.weight(1f));Switch(MediaPreferences.gifs(c),{MediaPreferences.set(c,"animate_gifs",it)})}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Fill image viewer",Modifier.weight(1f));Switch(MediaPreferences.fill(c),{MediaPreferences.set(c,"fill_images",it)})}
  Text("Pinch to zoom and drag to pan. Swipe or use Previous / Next to browse. Playback stops when you leave the viewer.",fontSize=14.sp)
 }}
}

/** Optional speed support must never prevent ordinary playback. */
internal fun messagePlaybackSpeed(requested:Float,current:Float,apply:(Float)->Unit):Float {
 if(requested==current)return current
 return try{apply(requested);requested}catch(_:IllegalArgumentException){current}
}
@Composable internal fun InternalMessagePlayer(part:MessagePart,active:Boolean,clipStart:Int=0,clipEnd:Int=Int.MAX_VALUE,immersive:Boolean=false,fullScreen:()->Unit={}){
 var attempt by remember(part.uri){mutableIntStateOf(0)}
 key(part.uri,attempt){MessagePlayerAttempt(part,active,clipStart,clipEnd,immersive,fullScreen){attempt++}}
}
@Composable private fun MessagePlayerAttempt(part:MessagePart,active:Boolean,clipStart:Int,clipEnd:Int,immersive:Boolean,fullScreen:()->Unit,retry:()->Unit){
 val c=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val start by rememberUpdatedState(clipStart);val end by rememberUpdatedState(clipEnd)
 val audio=c.getSystemService(AudioManager::class.java);val player=remember(part.uri,lifecycle){MediaPlayer()}
 val scope=rememberCoroutineScope()
 var dragging by remember(player){mutableStateOf(false)};var ratio by remember(player){mutableFloatStateOf(16f/9f)};var prepared by remember(player){mutableStateOf(false)};var playing by remember(player){mutableStateOf(false)};var duration by remember(player){mutableIntStateOf(0)};var position by remember(player){mutableIntStateOf(0)};var error by remember(player){mutableStateOf<String?>(null)};var muted by remember(player){mutableStateOf(false)};var speed by remember(player){mutableFloatStateOf(1f)};var requestedSpeed by remember(player){mutableFloatStateOf(1f)}
 var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
 var autoStartPending by remember(player){mutableStateOf(true)}
 var appliedClip by remember(player){mutableStateOf(clipStart to clipEnd)}
 var surfaceReady by remember(player){mutableStateOf(!part.mime.startsWith("video/"))}
 var showControls by remember(immersive){mutableStateOf(false)}
 val activeNow by rememberUpdatedState(active)
 val focus=remember(player){AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener{if(it<=0){if(playing)runCatching{player.pause()};playing=false}}.build()}
 fun play(){
  if(!activeNow||!prepared||!resumed)return
  autoStartPending=false
  if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){error="Audio is in use by another app";return}
  runCatching{if(position>=minOf(duration,end))player.seekTo(start);speed=messagePlaybackSpeed(requestedSpeed,speed){player.playbackParams=PlaybackParams().allowDefaults().setSpeed(it)};requestedSpeed=speed;player.start()}
   .onSuccess{playing=true}.onFailure{playing=false;audio.abandonAudioFocusRequest(focus);error="Playback could not start. Try again."}
 }
 DisposableEffect(player,lifecycle){
  var mp3Attempted=false;var compatible:java.io.File?=null;var decodeJob:Job?=null
  player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
  var released=false
  player.setOnPreparedListener{if(released)return@setOnPreparedListener;prepared=true;duration=it.duration.coerceAtLeast(0);if(start>0){position=start.coerceAtMost(duration);it.seekTo(position)};if(it.videoWidth>0&&it.videoHeight>0)ratio=it.videoWidth.toFloat()/it.videoHeight;if(autoStartPending&&surfaceReady&&activeNow&&resumed)play()};player.setOnCompletionListener{playing=false;position=duration;audio.abandonAudioFocusRequest(focus)}
  player.setOnErrorListener{_,what,extra->
   prepared=false;playing=false;audio.abandonAudioFocusRequest(focus)
   if(!mp3Attempted&&Mp3Codec.mime(part.mime,part.name)=="audio/mpeg"){
    mp3Attempted=true;error=null;decodeJob=scope.launch{
     var pendingWave:java.io.File?=null
     try{val job=currentCoroutineContext();val wave=withContext(Dispatchers.IO){Mp3Playback.fromUri(c,Uri.parse(part.uri)){job.ensureActive()}.also{pendingWave=it}}
      if(released){wave.delete();return@launch};compatible=wave;player.reset();player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setDataSource(wave.path);player.prepareAsync()
     }catch(e:CancellationException){throw e}catch(e:Exception){if(!released)error="MP3 playback could not be prepared: ${e.message}"}finally{if(pendingWave!==compatible)pendingWave?.delete()}
    }
   }else error="Playback failed ($what / $extra). Try again or use Open With.";true
  }
  try{player.setDataSource(c,Uri.parse(part.uri));player.prepareAsync()}catch(e:Exception){error="Unable to open this audio or video. Try again or use Open With."}
  val observer=LifecycleEventObserver{_,event->resumed=event==Lifecycle.Event.ON_START||event==Lifecycle.Event.ON_RESUME;if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){if(playing)runCatching{player.pause()};playing=false;audio.abandonAudioFocusRequest(focus)}};lifecycle.addObserver(observer)
  onDispose{released=true;decodeJob?.cancel();compatible?.delete();lifecycle.removeObserver(observer);audio.abandonAudioFocusRequest(focus);player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null);runCatching{player.release()}}
 }
 LaunchedEffect(active){if(!active){if(playing)runCatching{player.pause()};playing=false;audio.abandonAudioFocusRequest(focus)}}
 LaunchedEffect(clipStart,clipEnd){if(appliedClip!=clipStart to clipEnd){appliedClip=clipStart to clipEnd;if(prepared){runCatching{if(playing)player.pause();if(position!=start.coerceIn(0,duration))player.seekTo(start.coerceIn(0,duration))};playing=false;position=start.coerceIn(0,duration);audio.abandonAudioFocusRequest(focus)}}}
 LaunchedEffect(prepared,active,resumed,surfaceReady){if(autoStartPending&&prepared&&active&&resumed&&surfaceReady&&error==null)play()}
 LaunchedEffect(immersive,showControls,playing){if(immersive&&showControls&&playing){delay(3000);showControls=false}}
 LaunchedEffect(playing){while(playing){if(!dragging)position=runCatching{player.currentPosition}.getOrDefault(0);if(position>=end){if(playing)runCatching{player.pause()};playing=false;position=minOf(duration,end);audio.abandonAudioFocusRequest(focus)};delay(100)}}
 fun time(ms:Int)="${ms/60000}:${((ms/1000)%60).toString().padStart(2,'0')}"
 Column(Modifier.fillMaxSize().then(if(immersive)Modifier else Modifier.padding(16.dp)).testTag("attachment-player"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
  if(part.mime.startsWith("video/"))BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color.Black).clickable(enabled=immersive){showControls=!showControls}.testTag("attachment-video"),contentAlignment=Alignment.Center){key(player){AndroidView(factory={ctx->SurfaceView(ctx).apply{holder.addCallback(object:SurfaceHolder.Callback{override fun surfaceCreated(holder:SurfaceHolder){runCatching{player.setDisplay(holder)}.onSuccess{surfaceReady=true}};override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int){};override fun surfaceDestroyed(holder:SurfaceHolder){surfaceReady=false;runCatching{player.setDisplay(null)}}})}},modifier=Modifier.width(minOf(maxWidth,maxHeight*ratio)).aspectRatio(ratio))}}
  else Text("♫",fontSize=96.sp,color=MaterialTheme.colorScheme.primary)
  error?.let{Text(it,Modifier.padding(12.dp));TextButton(onClick=retry){Text("Try again")}}
  if(!immersive||showControls||error!=null){
  Slider(value=position.toFloat().coerceIn(start.toFloat(),maxOf(start+1,minOf(duration,end)).toFloat()),onValueChange={dragging=true;position=it.toInt()},onValueChangeFinished={if(prepared)runCatching{player.seekTo(position)};dragging=false},valueRange=start.toFloat()..maxOf(start+1,minOf(duration,end)).toFloat(),enabled=prepared&&error==null)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(time(position));Text(time(minOf(duration,end)))}
  Row(verticalAlignment=Alignment.CenterVertically){
   TextButton(enabled=prepared,onClick={runCatching{player.seekTo((position-10000).coerceAtLeast(start))}}){Text("−10s")}
   FilledTonalButton(enabled=active&&prepared&&error==null,onClick={if(playing){if(playing)runCatching{player.pause()};playing=false;audio.abandonAudioFocusRequest(focus)}else play()}){Text(if(playing)"Pause"else if(error!=null)"Unavailable"else if(prepared)"Play"else"Loading…")}
   TextButton(enabled=prepared,onClick={runCatching{player.seekTo((position+10000).coerceAtMost(minOf(duration,end)))}}){Text("+10s")}
  }
  Row{TextButton(onClick={muted=!muted;runCatching{player.setVolume(if(muted)0f else 1f,if(muted)0f else 1f)}}){Text(if(muted)"Unmute"else"Mute")};TextButton(enabled=prepared,onClick={val requested=when(requestedSpeed){1f->1.5f;1.5f->2f;else->1f};if(playing){runCatching{messagePlaybackSpeed(requested,speed){player.playbackParams=PlaybackParams().allowDefaults().setSpeed(it)}}.onSuccess{speed=it;requestedSpeed=it}.onFailure{error="Playback speed could not change. Try again.";if(playing)runCatching{player.pause()};playing=false;audio.abandonAudioFocusRequest(focus)}}else requestedSpeed=requested}){Text("${requestedSpeed}×")};if(part.mime.startsWith("video/"))TextButton(onClick=fullScreen){Text("Full screen")}}
  }
 }
}
