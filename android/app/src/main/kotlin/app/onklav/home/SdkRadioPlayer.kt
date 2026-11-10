package app.onklav.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.onklav.shared.*
import app.onklav.shared.R
import org.json.JSONObject

@Composable internal fun SdkRadioVisualizationSettings(){
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("radio-player",0)}
 var enabled by remember{mutableStateOf(prefs.getBoolean("waveform",true)&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
 var denied by remember{mutableStateOf(false)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->enabled=granted;denied=!granted;prefs.edit().putBoolean("waveform",granted).apply()}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Audio waveform",Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={value->if(value&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.RECORD_AUDIO)else{enabled=value;prefs.edit().putBoolean("waveform",value).apply()}})}
 Text(if(denied)"Audio permission was declined. Playback still works."else"Shows the station’s sound. Android requires audio permission; microphone audio is never recorded.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun SdkRadioPlayer(node:JSONObject,model:SdkAppModel,row:JSONObject,owner:JSONObject?,modifier:Modifier,command:(String,JSONObject)->Unit){
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val id=node.getString("id");val ready=owner!=null&&row.optInt("count")>0&&node.optBoolean("enabled",true)
 val interactive=!model.busy&&model.pending==null
 var timer by remember(id){mutableStateOf(false)}
 var samples by remember(id){mutableStateOf(byteArrayOf())}
 var bands by remember(id){mutableStateOf(FloatArray(32))}
 var visualizerOpen by rememberSaveable(id){mutableStateOf(false)}
 var captureError by remember(id){mutableStateOf(false)}
 var session by remember(id){mutableIntStateOf(0)}
 var foreground by remember{mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
 val prefs=remember{context.getSharedPreferences("radio-player",0)}
 var waveform by remember{mutableStateOf(prefs.getBoolean("waveform",true))}
 var waveformEpoch by remember{mutableIntStateOf(0)}
 DisposableEffect(prefs,lifecycle){
  val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="waveform"){waveform=prefs.getBoolean("waveform",true);waveformEpoch++}}
  val observer=LifecycleEventObserver{_,_->foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
  prefs.registerOnSharedPreferenceChangeListener(listener);lifecycle.addObserver(observer)
  onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener);lifecycle.removeObserver(observer)}
 }
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->prefs.edit().putBoolean("waveform",granted).apply();waveform=granted;waveformEpoch++}
 val playing=row.optString("state")=="playing"
 val playRequested=playing||row.optString("state")=="preparing"
 val playPause:()->Unit={
  if(!playRequested&&node.has("retry"))model.action(node.getString("retry"))
  else if(ready)command(if(playRequested)"pause"else"play",JSONObject())
 }

 LaunchedEffect(playing,foreground){if(playing&&foreground&&waveform&&!prefs.getBoolean("waveform_asked",false)&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){prefs.edit().putBoolean("waveform_asked",true).apply();permission.launch(Manifest.permission.RECORD_AUDIO)}}
 // Recheck the authorized session: changing stations may replace MediaPlayer while state stays playing.
 LaunchedEffect(owner?.toString(),playing,foreground,waveform,waveformEpoch,visualizerOpen){
  session=0
  if(playing&&foreground&&waveform&&owner!=null&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
   while(true){session=runCatching{SdkMedia.radioAudioSession(owner)}.getOrDefault(0);delay(500)}
  }
 }
 DisposableEffect(session,playing,foreground,waveform,waveformEpoch){
  captureError=false
  val capture=RadioWaveform(emit={samples=it},emitFft={data,rate->bands=radioSpectrum(data,rate)})
  if(session>0&&playing&&foreground&&waveform&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){captureError=runCatching{capture.start(session)}.isFailure}
  onDispose{capture.close();bands=FloatArray(32)}
 }
 val enableVisualization:()->Unit={
  prefs.edit().putBoolean("waveform",true).putBoolean("waveform_asked",true).apply();waveform=true;waveformEpoch++
  if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.RECORD_AUDIO)
 }
 if(visualizerOpen)RadioVisualizerDialog(
  title=row.optString("title").ifEmpty{"Radio"},playing=playRequested,foreground=foreground,
  samples=samples,bands=bands,
  status=when{ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED->"Audio permission is required. The microphone is never recorded."
   !waveform->"Audio visuals are off"
   !playing->if(row.optString("state")=="preparing")radioConnectionLabel(row)else"Play a station to see its sound"
   captureError->"Audio visuals unavailable on this output"
   session==0||samples.isEmpty()->"Waiting for station audio…"
   else->null},
  canEnable=!waveform||ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED,
  onEnable=enableVisualization,onRetry={waveformEpoch++},onDismiss={visualizerOpen=false},
  transportEnabled=(ready||node.has("retry"))&&interactive,onPlayPause=playPause
 )
 val ink=MaterialTheme.colorScheme.primary
 Surface(modifier.fillMaxWidth(),tonalElevation=3.dp,shape=MaterialTheme.shapes.large){Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
  Canvas(Modifier.fillMaxWidth().height(30.dp).testTag("radio-waveform").semantics{contentDescription="Station audio waveform"}){
   val mid=size.height/2
   if(samples.isEmpty())drawLine(ink.copy(alpha=.35f),Offset(0f,mid),Offset(size.width,mid),1.dp.toPx())
   else {val path=Path();samples.forEachIndexed{i,b->val x=i.toFloat()/samples.lastIndex.coerceAtLeast(1)*size.width;val y=mid-((b.toInt()and 255)-128)/128f*(mid-2.dp.toPx());if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,ink,style=Stroke(1.5.dp.toPx()))}
  }
  Text(row.optString("title").ifEmpty{node.optString("title").ifEmpty{"Choose a station"}},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
  val left=row.optLong("sleep_remaining_ms")
  Text(when(row.optString("state")){"playing"->"Live";"preparing"->radioConnectionLabel(row);"paused"->"Paused";"error"->row.optString("error").ifEmpty{"Tap Play to reconnect"};"ended"->"Stopped";else->if(node.has("retry"))"Tap Play to reconnect"else"Browse stations to listen"}+if(left>0)" · Sleep ${(left+59999)/60000} min"else"",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
   RadioTransportIcon(if(playRequested)R.drawable.ref_radio_pause else R.drawable.ref_radio_play,if(playRequested)"Pause station"else"Play station",(ready||node.has("retry"))&&interactive){playPause()}
   RadioTransportIcon(R.drawable.ref_radio_stop,"Stop station",ready&&interactive){command("stop",JSONObject())}
   RadioTransportIcon(R.drawable.ref_browser_refresh,"Retry station",node.has("retry")&&interactive){model.action(node.getString("retry"))}
   RadioTransportIcon(if(node.optBoolean("favorite"))R.drawable.ref_bookmark_filled else R.drawable.ref_browser_bookmark,node.optString("favorite_label","Add favorite"),node.has("long_action")&&interactive){model.action(node.getString("long_action"))}
   RadioTransportIcon(R.drawable.audio_edit_graphiceq,"Open music visualizer",true){visualizerOpen=true}
   Box{RadioTransportIcon(R.drawable.ref_radio_sleep,"Sleep timer",ready&&interactive,active=left>0){timer=true};DropdownMenu(timer,{timer=false}){for(minutes in listOf(0,15,30,60)){DropdownMenuItem(text={Text(if(minutes==0)"Sleep off"else"Stop in $minutes minutes")},onClick={timer=false;command("sleep",JSONObject().put("minutes",minutes))})}}}
  }
 }}
}
@Composable private fun RadioTransportIcon(drawable:Int,label:String,enabled:Boolean,active:Boolean=false,onClick:()->Unit){
 IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp).semantics{if(active)stateDescription="On"}){Icon(painterResource(drawable),label,Modifier.size(24.dp),tint=if(enabled)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.38f))}
}

internal fun radioConnectionLabel(row:JSONObject)=when(row.optString("connection")){
 "offline"->"Waiting for internet…"
 "reconnecting"->"Reconnecting to station…"
 "buffering"->"Buffering station audio…"
 else->"Connecting to station…"
}
