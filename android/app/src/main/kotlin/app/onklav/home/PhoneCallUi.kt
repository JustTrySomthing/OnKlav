package app.onklav.home

import android.Manifest
import android.content.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.*
import android.telecom.*
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.delay

class PhoneDialActivity:ComponentActivity(){
 private var tab by mutableStateOf<String?>(null);private var number by mutableStateOf("");private var epoch by mutableLongStateOf(0)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);FolderVaultLifecycle.install(application);number=PhoneRules.dialIntent(intent);tab=intent?.getStringExtra("phone_tab");setContent{OnKlavTheme{PhoneWorkspace({finish()},initialNumber=number,launchKey=epoch,initialTab=tab)}}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);number=PhoneRules.dialIntent(intent);tab=intent.getStringExtra("phone_tab");epoch++}
}
class PhoneCallActivity:ComponentActivity(){
 override fun onResume(){super.onResume();PhoneCalls.screen(this,true)}
 override fun onPause(){PhoneCalls.screen(this,false);super.onPause()}
 override fun onDestroy(){PhoneCalls.screen(this,false);super.onDestroy()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);FolderVaultLifecycle.install(application);setShowWhenLocked(true);setTurnScreenOn(true);volumeControlStream=AudioManager.STREAM_VOICE_CALL;answer(intent);setContent{OnKlavTheme{PhoneInCallScreen{finish()}}}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);answer(intent)}
 private fun answer(intent:Intent?){val id=intent?.getStringExtra("call");val action=intent?.getStringExtra("action");intent?.removeExtra("action");if(id!=null&&action in setOf("answer","audio-answer"))PhoneCalls.action(id,action!!)}
}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun PhoneInCallScreen(close:()->Unit){
 val c=LocalContext.current;val state by PhoneCalls.state.collectAsState();val error by PhoneCalls.error.collectAsState();val last by PhoneCalls.ended.collectAsState();val waiting by PhoneCalls.waiting.collectAsState();val requestedPad by PhoneCalls.keypad.collectAsState();var selected by rememberSaveable{mutableStateOf("")};var pad by rememberSaveable{mutableStateOf(false)};var digits by rememberSaveable{mutableStateOf("")};var participants by remember{mutableStateOf(false)};var tick by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
 LaunchedEffect(requestedPad){if(requestedPad){pad=true;PhoneCalls.keypad.value=false}}
 LaunchedEffect(Unit){while(true){delay(1000);tick=SystemClock.elapsedRealtime()}}
 LaunchedEffect(state.calls.isEmpty()){if(state.calls.isEmpty()){delay(1800);close()}}
 val roots=state.calls.filter{it.parent==null};val call=roots.firstOrNull{it.state==Call.STATE_RINGING}?:roots.firstOrNull{it.id==selected}?:roots.firstOrNull{it.state==Call.STATE_ACTIVE}?:roots.firstOrNull()
 val proximity=call?.state==Call.STATE_ACTIVE&&state.audio.any{it.selected&&it.earpiece}
 DisposableEffect(proximity){val power=c.getSystemService(android.os.PowerManager::class.java);val lock=if(proximity&&power.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))power.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,"onklav:phone-proximity")else null;runCatching{lock?.acquire(4*60*60*1000L)};onDispose{if(lock?.isHeld==true)runCatching{lock.release(android.os.PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)}}}
 Scaffold(topBar={TopAppBar(title={Text("OnKlav call",fontWeight=FontWeight.SemiBold)},navigationIcon={app.onklav.shared.ClosePageButton(description="Minimize call",onClick=close)})},bottomBar={if(call!=null)Surface{Column(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){if(call.state==Call.STATE_RINGING)PhoneIncomingControls(call)else FilledIconButton(onClick={PhoneCalls.action(call.id,"end")},modifier=Modifier.size(72.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.errorContainer,contentColor=MaterialTheme.colorScheme.onErrorContainer)){Icon(PhoneIcons.End,"End call",Modifier.size(34.dp))}}}}){padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
  if(call==null){PhoneEmpty("Call ended",last?.status?.takeIf{it.isNotBlank()}?:"Returning to Phone…");return@Column}
  if(roots.size>1)Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){roots.forEach{other->FilterChip(selected=call.id==other.id,onClick={selected=other.id},label={Text("${other.name.ifBlank{other.number}.take(24)} · ${PhoneCalls.status(other.state)}")})}}
  Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(if(pad)64.dp else 104.dp)){Box(contentAlignment=Alignment.Center){Text(call.name.ifBlank{call.number}.firstOrNull()?.uppercaseChar()?.toString()?:"?",style=MaterialTheme.typography.displayMedium)}}
  Text(call.name.ifBlank{call.number.ifBlank{"Caller"}},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  if(call.name.isNotBlank()&&call.number.isNotBlank())Text(PhoneRules.display(call.number),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.primaryContainer){Text(if(call.connected>0&&call.state==Call.STATE_ACTIVE)PhoneRules.duration((tick-call.connected)/1000)else PhoneCalls.status(call.state),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.padding(horizontal=18.dp,vertical=8.dp))}
  if(error.isNotBlank()){Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);TextButton(onClick={PhoneCalls.error.value=""}){Text("Dismiss")}}
  waiting[call.id]?.let{remaining->Text("Continue with additional digits: $remaining");Row{TextButton(onClick={PhoneCalls.action(call.id,"continue")}){Text("Continue")};TextButton(onClick={PhoneCalls.action(call.id,"cancel-digits")}){Text("Cancel digits")}}}

  if(call.state==Call.STATE_SELECT_PHONE_ACCOUNT){Text("Choose a calling account");var lines by remember{mutableStateOf<List<PhoneLine>>(emptyList())};LaunchedEffect(call.id){lines=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){runCatching{PhoneRepository(c).lines()}.getOrDefault(emptyList())}};lines.forEach{line->OutlinedButton(onClick={PhoneCalls.service?.selectAccount(call.id,line.handle)},modifier=Modifier.fillMaxWidth()){Text(line.name)}}}
  if(call.state!=Call.STATE_RINGING&&call.state!=Call.STATE_DISCONNECTED){
   PhoneVideoSession(call)
   if(pad){Text(digits,style=MaterialTheme.typography.headlineSmall);PhonePad(press={key->digits=(digits+key).takeLast(40);PhoneCalls.tone(call.id,key.single())},compact=true);TextButton(onClick={pad=false}){Text("Hide keypad")}}
   else FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalArrangement=Arrangement.spacedBy(12.dp),maxItemsInEachRow=3){
    PhoneCallControl("Mute",Icons.Outlined.MicOff,call.state==Call.STATE_ACTIVE,{PhoneCalls.action(call.id,"mute")},selected=state.muted)
    PhoneCallControl("Keypad",PhoneIcons.Pad,call.state==Call.STATE_ACTIVE,{pad=true})
    PhoneCallControl("Speaker",Icons.Outlined.VolumeUp,PhoneCalls.speakerTarget(state.audio,null)!=null,{PhoneCalls.toggleSpeaker()},selected=state.audio.any{it.selected&&it.speaker})
    PhoneCallControl(if(call.state==Call.STATE_HOLDING)"Resume"else"Hold",PhoneIcons.Hold,call.hold,{PhoneCalls.action(call.id,"hold")},selected=call.state==Call.STATE_HOLDING)
    PhoneCallControl("Add call",Icons.Outlined.Add,state.add,{runCatching{c.startActivity(Intent(c,PhoneDialActivity::class.java).setAction(Intent.ACTION_DIAL))}.onFailure{PhoneCalls.error.value="Could not open keypad."}})
    if(call.merge)PhoneCallControl("Merge",PhoneIcons.Merge,true,{PhoneCalls.action(call.id,"merge")})else if(roots.any{it.state==Call.STATE_HOLDING}&&roots.any{it.state==Call.STATE_ACTIVE})PhoneCallControl("Swap",PhoneIcons.Swap,true,{PhoneCalls.action(call.id,"swap")})
   }
   if(call.children.isNotEmpty())TextButton(onClick={participants=true}){Text("${call.children.size} participants")}
  }
 }}

 if(participants&&call!=null)AlertDialog(onDismissRequest={participants=false},title={Text("Participants")},text={Column{state.calls.filter{it.id in call.children}.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Text(p.name.ifBlank{p.number},Modifier.weight(1f));if(p.separate)TextButton(onClick={PhoneCalls.action(p.id,"split")}){Text("Separate")};IconButton(onClick={PhoneCalls.action(p.id,"end")}){Icon(PhoneIcons.End,"End call with ${p.name}")}}}}},confirmButton={TextButton(onClick={participants=false}){Text("Done")}})
}
@Composable private fun PhoneIncomingControls(call:PhoneLiveCall){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){PhoneCallControl("Decline",PhoneIcons.End,true,{PhoneCalls.action(call.id,"decline")},danger=true);PhoneCallControl(if(VideoProfile.isVideo(call.video))"Answer video"else"Answer",PhoneIcons.Call,true,{PhoneCalls.action(call.id,"answer")},selected=true)};if(VideoProfile.isVideo(call.video))TextButton(onClick={PhoneCalls.action(call.id,"audio-answer")}){Text("Answer with audio only")}}
@Composable private fun PhoneCallControl(label:String,icon:ImageVector,enabled:Boolean,click:()->Unit,selected:Boolean=false,danger:Boolean=false){Column(Modifier.width(88.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){FilledTonalIconButton(onClick=click,enabled=enabled,modifier=Modifier.size(62.dp).then(if(label=="Speaker")Modifier.semantics{stateDescription=if(selected)"On"else"Off"}else Modifier),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=when{danger->MaterialTheme.colorScheme.errorContainer;selected&&label=="Speaker"->MaterialTheme.colorScheme.primary;selected->MaterialTheme.colorScheme.primaryContainer;else->MaterialTheme.colorScheme.surfaceContainerHigh},contentColor=when{danger->MaterialTheme.colorScheme.onErrorContainer;selected&&label=="Speaker"->MaterialTheme.colorScheme.onPrimary;else->MaterialTheme.colorScheme.onSurface})){Icon(icon,label,Modifier.size(28.dp))};Text(label,style=MaterialTheme.typography.labelMedium,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
/** Surfaces belong to Telecom's VideoCall provider; no additional capture/stream engine. */
@Composable private fun PhoneVideoSession(call:PhoneLiveCall){
 val c=LocalContext.current;val video=PhoneCalls.service?.call(call.id)?.videoCall;var request by remember(call.id){mutableStateOf<VideoProfile?>(null)};val camera=PhoneAccess.permission(c,Manifest.permission.CAMERA)
 DisposableEffect(video){val cb=object:InCallService.VideoCall.Callback(){override fun onSessionModifyRequestReceived(profile:VideoProfile){request=profile};override fun onSessionModifyResponseReceived(status:Int,asked:VideoProfile?,response:VideoProfile?){if(status!=android.telecom.Connection.VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS)PhoneCalls.error.value="Video is not available for this call."};override fun onCallSessionEvent(event:Int){};override fun onPeerDimensionsChanged(width:Int,height:Int){};override fun onVideoQualityChanged(quality:Int){};override fun onCallDataUsageChanged(usage:Long){};override fun onCameraCapabilitiesChanged(capabilities:VideoProfile.CameraCapabilities){}}
  runCatching{video?.registerCallback(cb)};onDispose{runCatching{video?.unregisterCallback(cb)};runCatching{video?.setPreviewSurface(null)};runCatching{video?.setDisplaySurface(null)};runCatching{video?.setCamera(null)}}
 }
 if(video!=null&&call.state==Call.STATE_ACTIVE){
  if(VideoProfile.isVideo(call.video))Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color.Black)){
   AndroidView(factory={ctx->SurfaceView(ctx).apply{holder.addCallback(object:SurfaceHolder.Callback{override fun surfaceCreated(h:SurfaceHolder){runCatching{video.setDisplaySurface(h.surface)}};override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,hg:Int){};override fun surfaceDestroyed(h:SurfaceHolder){runCatching{video.setDisplaySurface(null)}}})}},modifier=Modifier.fillMaxSize())
   if(camera&&VideoProfile.isTransmissionEnabled(call.video))AndroidView(factory={ctx->SurfaceView(ctx).apply{setZOrderMediaOverlay(true);holder.addCallback(object:SurfaceHolder.Callback{override fun surfaceCreated(h:SurfaceHolder){val manager=ctx.getSystemService(CameraManager::class.java);runCatching{val front=manager.cameraIdList.firstOrNull{manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT};video.setCamera(front);video.setPreviewSurface(h.surface)}.onFailure{PhoneCalls.error.value="Camera could not be opened."}};override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,hg:Int){};override fun surfaceDestroyed(h:SurfaceHolder){runCatching{video.setPreviewSurface(null)};runCatching{video.setCamera(null)}}})}},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp).width(92.dp).aspectRatio(3f/4f))
  }
  if(VideoProfile.isVideo(call.video)||call.supportsVideo)TextButton(onClick={if(VideoProfile.isVideo(call.video))video.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_AUDIO_ONLY))else if(camera)video.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL))else PhoneCalls.error.value="Camera access is off. Review Phone setup after this call."}){Text(if(VideoProfile.isVideo(call.video))"Turn off video"else"Video call")}
 }
 request?.let{profile->AlertDialog(onDismissRequest={video?.sendSessionModifyResponse(VideoProfile(call.video));request=null},title={Text("Switch to video?")},text={Text(if(camera)"The other caller requested video. Allow your camera for this call?"else"Camera permission is off. You can continue with audio.")},confirmButton={if(camera)TextButton(onClick={video?.sendSessionModifyResponse(profile);request=null}){Text("Allow video")}},dismissButton={TextButton(onClick={video?.sendSessionModifyResponse(VideoProfile(call.video));request=null}){Text("Keep current call")}})}
}
