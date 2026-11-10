@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.onklav.home

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.R
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable internal fun RecorderWorkspace(kind:RecorderKind,exit:()->Unit,model:RecorderLibraryModel=viewModel(key="recorder-library-${kind.name}")){
 val c=LocalContext.current;val scope=rememberCoroutineScope();val state by RecordingRuntime.state.collectAsState()
 val prefs=remember(kind){c.getSharedPreferences("recorder-options-${kind.name}",0)}
 var tab by rememberSaveable{mutableIntStateOf(0)}
 var sound by rememberSaveable{mutableStateOf(runCatching{RecorderSound.valueOf(prefs.getString("sound","DEVICE")!!)}.getOrDefault(RecorderSound.DEVICE))}
 var format by rememberSaveable{mutableStateOf(runCatching{RecorderFormat.valueOf(prefs.getString("format","AAC")!!)}.getOrDefault(RecorderFormat.AAC))}
 var high by rememberSaveable{mutableStateOf(prefs.getBoolean("high",false))}
 var countdown by rememberSaveable{mutableIntStateOf(prefs.getInt("countdown",3).takeIf{it in listOf(0,3,5,10)}?:3)}
 var pendingOptions by remember{mutableStateOf<RecorderOptions?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 var query by rememberSaveable{mutableStateOf("")}
 var menu by remember{mutableStateOf<RecorderItem?>(null)}
 var rename by remember{mutableStateOf<RecorderItem?>(null)};var name by remember{mutableStateOf("")}
 var delete by remember{mutableStateOf<RecorderItem?>(null)}
 var exporting by remember{mutableStateOf<RecorderItem?>(null)}
 var video by remember{mutableStateOf<RecorderItem?>(null)}
 var keepRecording by remember{mutableStateOf(false)}
 val own=state.active&&state.options?.kind==kind
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 fun close(){model.closePlayer();video=null;if(own&&(state.phase==RecorderPhase.RECORDING||state.phase==RecorderPhase.PAUSED||state.phase==RecorderPhase.COUNTDOWN))keepRecording=true else exit()}
 BackHandler{close()}
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){model.pausePlayer();video=null}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);model.closePlayer()}}
 LaunchedEffect(kind,state.revision){model.refresh(kind)}
 val projection=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
  val options=pendingOptions;pendingOptions=null
  if(result.resultCode==Activity.RESULT_OK&&result.data!=null&&options!=null)try{RecordingRuntime.start(c,options,result.data);tab=0;model.closePlayer()}catch(e:Exception){error=e.message}
  else if(options!=null)error="Screen capture cancelled. Nothing was recorded."
 }
 fun begin(o:RecorderOptions){
  if(RecordingRuntime.state.value.active){error="Stop the current recording first.";return}
  prefs.edit().putString("sound",sound.name).putString("format",format.name).putBoolean("high",high).putInt("countdown",countdown).apply()
  if(o.kind==RecorderKind.SCREEN){pendingOptions=o;projection.launch(c.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())}
  else try{model.closePlayer();RecordingRuntime.start(c,o);tab=0}catch(e:Exception){error=e.message}
 }
 val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->val o=pendingOptions;pendingOptions=null;if(o!=null){if(!o.audio||grants[Manifest.permission.RECORD_AUDIO]==true||ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)begin(o)else error="Audio permission is needed for this sound choice. Tap Record to try again."}}
 fun start(){error=null;val o=RecorderOptions(kind,if(kind==RecorderKind.VOICE)RecorderSound.MIC else sound,format,high,countdown)
  val needed=mutableListOf<String>();if(o.audio&&ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)needed+=Manifest.permission.RECORD_AUDIO
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)needed+=Manifest.permission.POST_NOTIFICATIONS
  if(needed.isNotEmpty()){pendingOptions=o;permissions.launch(needed.toTypedArray())}else begin(o)
 }
 val save=rememberLauncherForActivityResult(FileExportContract()){uri->val item=exporting;exporting=null;if(uri!=null&&item!=null)model.export(item,uri)}
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
  HomeHeader(kind.title,back={close()})
  TabRow(tab){Tab(tab==0,{tab=0},text={Text("Record")});Tab(tab==1,{tab=1;model.refresh(kind)},text={Text("Recordings")})}
  if(tab==0){Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   if(state.active&&!own){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("${state.options?.kind?.title} is running",style=MaterialTheme.typography.titleLarge);Text("Finish that recording before starting another.");OutlinedButton(onClick={RecordingRuntime.control(c,"stop")}){Text("Stop & save current recording")}}}}
   RecorderDashboard(kind,if(own)state else RecorderState(),{start()},{command->RecordingRuntime.control(c,command)},!state.active&&pendingOptions==null,showControls=false)
   if(!state.active){
    if(kind==RecorderKind.VOICE){Text("Recording format",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){RecorderFormat.entries.forEach{choice->FilterChip(format==choice,{format=choice},label={Text(choice.label)})}};Text(if(format==RecorderFormat.AAC)"Clear voice audio in a smaller file."else"Original microphone samples · larger files for editing.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    else{
     Text("Sound",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){RecorderSound.entries.forEach{choice->FilterChip(sound==choice,{sound=choice},label={Text(choice.label)})}}
     if(sound==RecorderSound.BOTH)Text("Headphones keep device sound out of the microphone.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Text("Video quality",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!high,{high=false},label={Text("Standard · 720p")});FilterChip(high,{high=true},label={Text("High · 1080p")})}
     Text("Start delay",style=MaterialTheme.typography.titleMedium);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0,3,5,10).forEach{seconds->FilterChip(countdown==seconds,{countdown=seconds},label={Text(if(seconds==0)"Instant"else"$seconds seconds")})}}
     Text("Android asks before each capture. Protected video and some app audio cannot be recorded. Rotating keeps the starting video shape; the picture may have borders.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   if(own&&kind==RecorderKind.SCREEN)Text(if(state.phase==RecorderPhase.COUNTDOWN)"You can switch to the app you want to capture now."else"Open any app. Pause or stop from the recording notification. Android can stop capture when the phone locks.",style=MaterialTheme.typography.bodyMedium)
   if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)Text("Notifications are off. Return here to pause or stop and save.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(!state.active){Text("Automatically saved in Files › ${kind.root.replaceFirstChar{it.uppercase()}} › ${kind.folder}.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("A storage or file-size limit stops and saves the recording. Interrupted drafts stay here for recovery.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   if(state.options?.kind==kind&&!state.active&&state.message.isNotBlank()){Text(state.message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary);TextButton(onClick={tab=1;model.refresh(kind)}){Text("View recordings")}}
   (error?:state.error.takeIf{state.options?.kind==kind})?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  }
   HorizontalDivider()
   Box(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)){RecorderActions(kind,if(own)state else RecorderState(),{start()},{RecordingRuntime.control(c,it)},!state.active&&pendingOptions==null)}
  }else{
   OutlinedTextField(query,{query=it},placeholder={Text("Search recordings")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),leadingIcon={Icon(Icons.Outlined.Search,null)})
   if(model.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   (model.error?:error)?.let{Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error)}
   if(model.message.isNotBlank())Text(model.message,Modifier.padding(horizontal=20.dp,vertical=4.dp),color=MaterialTheme.colorScheme.primary)
   val visible=model.items.filter{it.name.contains(query,true)}
   if(visible.isEmpty()&&!model.busy)Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(painterResource(if(kind==RecorderKind.VOICE)R.drawable.recorder_mic else R.drawable.recorder_screen),null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary);Text(if(query.isBlank())"Your recordings will appear here"else"No matching recordings",style=MaterialTheme.typography.titleMedium);if(query.isBlank())TextButton(onClick={tab=0}){Text("Make a recording")};TextButton(onClick={model.refresh(kind)}){Text("Refresh")}}
   else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    items(visible,key={"${it.pending}:${it.id}"}){item->ElevatedCard(Modifier.fillMaxWidth()){
     Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
      IconButton(onClick={if(item.interrupted)model.recover(item)else if(kind==RecorderKind.VOICE)model.openPlayer(item)else video=item},enabled=!model.busy&&!state.active){Icon(if(item.interrupted)Icons.Outlined.Refresh else Icons.Outlined.PlayArrow,if(item.interrupted)"Recover ${item.title}"else"Play ${item.title}")}
      Column(Modifier.weight(1f).padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
       Text(item.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
       Text((if(item.interrupted)"Interrupted draft"else if(item.pending)"On device · not filed yet"else item.name.substringAfterLast('.').uppercase())+" · "+Formatter.formatShortFileSize(c,item.bytes)+(if(item.duration>0)" · ${recorderTime(item.duration)}"else""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
       if(item.created>0)Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(item.created)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
       if(item.warning.isNotBlank())Text(item.warning,style=MaterialTheme.typography.bodySmall)
      }
      IconButton(onClick={menu=item},enabled=!model.busy){Icon(Icons.Outlined.MoreVert,"Options for ${item.title}")}
     }
    }}
    item{TextButton(onClick={model.refresh(kind)},modifier=Modifier.fillMaxWidth(),enabled=!model.busy){Text("Refresh recordings")}}
   }
  }
 }}
 menu?.let{item->AlertDialog(onDismissRequest={menu=null},title={Text(item.title)},text={Column{
  TextButton(onClick={menu=null;rename=item;name=item.title}){Text("Rename")}
  if(item.pending)TextButton(onClick={menu=null;model.recover(item)}){Text(if(item.interrupted)"Recover & save to Files"else"Save to Files")}
  if(!item.interrupted){
   TextButton(onClick={menu=null;exporting=item;save.launch(FileExportRequest(item.name,item.mime))}){Text("Save a copy…")}
   TextButton(onClick={menu=null;scope.launch{try{val uri=model.uri(item);val share=Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri(item.title,uri)};c.startActivity(Intent.createChooser(share,"Share recording"))}catch(e:Exception){error=e.message}}}){Text("Share")}
  }
  TextButton(onClick={menu=null;delete=item}){Text(if(item.pending)"Delete draft"else"Move to Trash",color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(onClick={menu=null}){Text("Done")}})}
 rename?.let{item->AlertDialog(onDismissRequest={rename=null},title={Text("Rename recording")},text={OutlinedTextField(name,{name=it.take(80)},label={Text("Name")},singleLine=true)},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={model.rename(item,name);rename=null}){Text("Save")}},dismissButton={TextButton(onClick={rename=null}){Text("Cancel")}})}
 delete?.let{item->AlertDialog(onDismissRequest={delete=null},title={Text(if(item.pending)"Delete this draft?"else"Move recording to Trash?")},text={Text(if(item.pending)"This unfiled draft will be permanently removed."else"You can restore it from Files › Trash.")},confirmButton={TextButton(onClick={model.delete(item);delete=null}){Text(if(item.pending)"Delete"else"Move to Trash")}},dismissButton={TextButton(onClick={delete=null}){Text("Cancel")}})}
 if(keepRecording)AlertDialog(onDismissRequest={keepRecording=false},title={Text("Keep recording?")},text={Text("You can leave this app and use the notification to pause or stop.")},confirmButton={TextButton(onClick={keepRecording=false;exit()}){Text("Keep recording & leave")}},dismissButton={TextButton(onClick={keepRecording=false;RecordingRuntime.control(c,"stop")}){Text("Stop & save")}})
 model.playing?.let{item->RecorderAudioPreview(model,item)}
 video?.let{item->RecorderVideoPreview(item,model){video=null}}
}

@Composable internal fun RecorderDashboard(kind:RecorderKind,state:RecorderState,start:()->Unit,control:(String)->Unit,canStart:Boolean,showControls:Boolean=true){
 val active=state.active;val paused=state.phase==RecorderPhase.PAUSED
 val color=MaterialTheme.colorScheme.primary
 ElevatedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp)){
  Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    if(active)Box(Modifier.size(9.dp).background(if(paused)MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,CircleShape))
    Text(when(state.phase){RecorderPhase.COUNTDOWN->"Starts in ${state.countdown}";RecorderPhase.RECORDING->state.message.ifBlank{"Recording"};RecorderPhase.PAUSED->state.message.ifBlank{"Paused"};RecorderPhase.FINISHING->state.message.ifBlank{"Saving…"};RecorderPhase.STARTING->"Preparing…";else->"Ready to record"},style=MaterialTheme.typography.labelLarge)
   }
   BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){val label=if(state.phase==RecorderPhase.COUNTDOWN)state.countdown.toString()else recorderTime(state.elapsed);val font=minOf(52f,maxWidth.value/label.length/.66f/LocalDensity.current.fontScale).sp;Text(label,style=MaterialTheme.typography.displayLarge.copy(fontFamily=FontFamily.Monospace,fontSize=font),maxLines=1,modifier=Modifier.semantics{contentDescription="Recording time ${recorderTime(state.elapsed)}"})}
   Canvas(Modifier.fillMaxWidth().height(74.dp).semantics{contentDescription=if(kind==RecorderKind.VOICE)"Live microphone level"else"Recording activity"}){
    val levels=state.levels.takeLast(60);val step=size.width/60;for(i in 0 until 60){val level=levels.getOrNull(i-(60-levels.size))?:0f;val height=maxOf(2.dp.toPx(),level*size.height*.9f);val x=(i+.5f)*step;drawLine(color.copy(alpha=if(paused).35f else .85f),Offset(x,(size.height-height)/2),Offset(x,(size.height+height)/2),strokeWidth=maxOf(2.dp.toPx(),step*.5f),cap=StrokeCap.Round)}
   }
   if(showControls)RecorderActions(kind,state,start,control,canStart)
   if(state.bytes>0)Text(Formatter.formatShortFileSize(LocalContext.current,state.bytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)

  }
 }
}

@Composable private fun RecorderActions(kind:RecorderKind,state:RecorderState,start:()->Unit,control:(String)->Unit,canStart:Boolean){
 when(state.phase){
  RecorderPhase.IDLE->Button(start,enabled=canStart,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).semantics{contentDescription=if(kind==RecorderKind.VOICE)"Start voice recording"else"Start screen recording"},shape=RoundedCornerShape(18.dp)){
   Icon(painterResource(if(kind==RecorderKind.VOICE)R.drawable.recorder_mic else R.drawable.recorder_screen),null,Modifier.size(24.dp));Spacer(Modifier.width(10.dp));Text("Start recording",style=MaterialTheme.typography.titleMedium)
  }
  RecorderPhase.RECORDING,RecorderPhase.PAUSED->FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   FilledTonalButton(onClick={control(if(state.phase==RecorderPhase.PAUSED)"resume"else"pause")},enabled=!state.controlling,modifier=Modifier.heightIn(min=52.dp)){Icon(painterResource(if(state.phase==RecorderPhase.PAUSED)R.drawable.recorder_play else R.drawable.recorder_pause),null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(state.phase==RecorderPhase.PAUSED)"Resume"else"Pause")}
   Button(onClick={control("stop")},modifier=Modifier.heightIn(min=52.dp)){Icon(painterResource(R.drawable.recorder_stop),null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Stop & save")}
  }
  RecorderPhase.STARTING,RecorderPhase.COUNTDOWN->OutlinedButton(onClick={control("stop")},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Cancel recording")}
  RecorderPhase.FINISHING->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.message.ifBlank{"Saving recording…"},style=MaterialTheme.typography.labelLarge)}
 }
}

@Composable private fun RecorderAudioPreview(model:RecorderLibraryModel,item:RecorderItem){
 var seek by remember(item.id){mutableStateOf<Float?>(null)}
 AlertDialog(onDismissRequest={model.closePlayer()},title={Text(item.title)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  if(!model.playbackReady)LinearProgressIndicator(Modifier.fillMaxWidth())
  Text("${recorderTime(model.position)} / ${recorderTime(model.duration)}",style=MaterialTheme.typography.titleMedium)
  Slider(seek?:model.position.toFloat(),{seek=it},onValueChangeFinished={seek?.let{model.seek(it.toLong())};seek=null},valueRange=0f..model.duration.coerceAtLeast(1).toFloat(),enabled=model.playbackReady,modifier=Modifier.semantics{contentDescription="Recording playback position"})
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={model.seek(model.position-10000)}){Text("−10s")};FilledIconButton(onClick={model.playPause()},enabled=model.playbackReady){Icon(painterResource(if(model.isPlaying)R.drawable.recorder_pause else R.drawable.recorder_play),if(model.isPlaying)"Pause playback"else"Play recording")};TextButton(onClick={model.seek(model.position+10000)}){Text("+10s")}}
  FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(1f,1.25f,1.5f,2f).forEach{speed->FilterChip(model.speed==speed,{model.changeSpeed(speed)},label={Text("${speed}×")})}}
  model.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(onClick={model.closePlayer()}){Text("Done")}})
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun RecorderVideoPreview(item:RecorderItem,model:RecorderLibraryModel,close:()->Unit){
 val c=LocalContext.current;var player by remember(item.id){mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null)};var error by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(item.id){try{val uri=model.uri(item);player=androidx.media3.exoplayer.ExoPlayer.Builder(c).build().apply{setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT,true);setHandleAudioBecomingNoisy(true);addListener(object:androidx.media3.common.Player.Listener{override fun onPlayerError(e:androidx.media3.common.PlaybackException){error="This video could not be played"}});setMediaItem(androidx.media3.common.MediaItem.fromUri(uri));prepare();playWhenReady=true}}catch(e:Exception){error=e.message}}
 DisposableEffect(item.id){onDispose{player?.release();player=null}}
 app.onklav.shared.ReferenceDialog(item.title,close,large=true,footer={app.onklav.shared.DialogAction("Done",onClick=close)}){
  Box(Modifier.fillMaxWidth().heightIn(min=180.dp,max=420.dp).aspectRatio(9f/16f).background(androidx.compose.ui.graphics.Color.Black)){
   AndroidView(factory={context->androidx.media3.ui.PlayerView(context).apply{useController=true;setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}},update={it.player=player},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
   if(player==null&&error==null)CircularProgressIndicator(Modifier.align(Alignment.Center))
  }
  error?.let{Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)}
 }
}
