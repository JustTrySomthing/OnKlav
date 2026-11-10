package app.onklav.home

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import app.onklav.shared.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.onklav.shared.ChoiceButton
import org.json.JSONObject

/** Public themed controls; the app supplies a queue, not player/focus/service plumbing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SdkPlayerComponent(node:JSONObject,model:SdkAppModel,modifier:Modifier){
 val id=node.getString("id");val epoch by SdkMedia.changes.collectAsState();var row by remember(id){mutableStateOf(JSONObject())};var owner by remember(id){mutableStateOf<JSONObject?>(null)}
 var seek by remember(id){mutableFloatStateOf(0f)};var dragging by remember(id){mutableStateOf(false)}
 LaunchedEffect(id,epoch){runCatching{model.mediaOwner(id)}.onSuccess{owner=it;row=SdkMedia.status(it)}.onFailure{owner=null;row=JSONObject()}}
 fun command(op:String,args:JSONObject=JSONObject()){runCatching{val live=model.mediaOwner(id);SdkMedia.controlFor(live,op,args)}.onFailure{model.error="Playback control unavailable. Check this app’s permissions and media source."}}
 val count=row.optInt("count");val active=owner!=null&&count>0&&node.optBoolean("enabled",true)&&!model.busy;val position=row.optLong("position_ms");val duration=row.optLong("duration_ms")
 if(node.optBoolean("podcast")){
  val ready=owner!=null&&count>0&&node.optBoolean("enabled",true)&&!model.busy
  val seekable=ready&&duration>0&&row.optString("state")!="preparing"&&row.optString("state")!="error"
  val playing=row.optString("state")=="playing"
  var timer by remember(id){mutableStateOf(false)}
  var speeds by remember(id){mutableStateOf(false)}
  val left=row.optLong("sleep_remaining_ms")
  fun play(){if(ready&&row.optString("state")!="error")command(if(playing)"pause"else"play")else if(node.has("retry"))model.action(node.getString("retry"))}
  Surface(modifier.fillMaxWidth(),tonalElevation=3.dp,shape=MaterialTheme.shapes.large){Column(Modifier.padding(horizontal=10.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
   Text(row.optString("title").ifEmpty{node.optString("title","Choose an episode")},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
   Text(when(row.optString("state")){"preparing"->"Loading episode…";"error"->"Couldn't play · tap Play to retry";else->"${position/60000}:${(position/1000%60).toString().padStart(2,'0')} / ${duration/60000}:${(duration/1000%60).toString().padStart(2,'0')}"}+if(left>0)" · Sleep ${(left+59999)/60000}m"else"",style=MaterialTheme.typography.labelMedium)
   Slider(value=if(dragging)seek else position.toFloat().coerceIn(0f,duration.coerceAtLeast(1).toFloat()),onValueChange={seek=it;dragging=true},onValueChangeFinished={command("seek",JSONObject().put("position_ms",seek.toLong()));dragging=false},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=seekable,modifier=Modifier.fillMaxWidth().height(28.dp).semantics{contentDescription="Episode position"})
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
    IconButton(enabled=seekable,onClick={command("seek",JSONObject().put("position_ms",(position-15000).coerceAtLeast(0)))},modifier=Modifier.size(48.dp).semantics{contentDescription="Back 15 seconds"}){Text("−15",style=MaterialTheme.typography.labelLarge)}
    IconButton(enabled=(ready||node.has("retry"))&&!model.busy,onClick={play()},modifier=Modifier.size(48.dp)){Icon(painterResource(if(playing)R.drawable.ref_radio_pause else R.drawable.ref_radio_play),if(playing)"Pause episode"else"Play episode",Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)}
    IconButton(enabled=seekable,onClick={command("seek",JSONObject().put("position_ms",(position+30000).coerceAtMost(duration)))},modifier=Modifier.size(48.dp).semantics{contentDescription="Forward 30 seconds"}){Text("+30",style=MaterialTheme.typography.labelLarge)}
    Box{TextButton(enabled=ready,onClick={speeds=true},modifier=Modifier.width(48.dp),contentPadding=PaddingValues(0.dp)){Text("${row.optDouble("speed",1.0)}×")};DropdownMenu(speeds,{speeds=false}){for(value in listOf(0.75,1.0,1.25,1.5,2.0)){DropdownMenuItem(text={Text("$value×")},onClick={speeds=false;command("speed",JSONObject().put("speed",value))})}}}
    Box{IconButton(enabled=ready,onClick={timer=true},modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.ref_radio_sleep),"Sleep timer",Modifier.size(24.dp),tint=if(left>0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)};DropdownMenu(timer,{timer=false}){for(minutes in listOf(0,15,30,60)){DropdownMenuItem(text={Text(if(minutes==0)"Sleep off"else"Stop in $minutes minutes")},onClick={timer=false;command("sleep",JSONObject().put("minutes",minutes))})}}}
    IconButton(enabled=ready,onClick={command("stop")},modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.ref_radio_stop),"Stop episode",Modifier.size(24.dp))}
   }
  }}
  return
 }
 if(node.optBoolean("radio")){
  SdkRadioPlayer(node,model,row,owner,modifier){op,args->command(op,args)}
  return
 }

 Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(row.optString("title").ifEmpty{"No media selected"},color=MaterialTheme.colorScheme.onSurface)
  if(active&&row.optBoolean("video"))AndroidView(factory={c->SurfaceView(c).also{view->view.holder.addCallback(object:SurfaceHolder.Callback{override fun surfaceCreated(h:SurfaceHolder){owner?.let{SdkMedia.surface(it,h)}};override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,height:Int){owner?.let{SdkMedia.surface(it,h)}};override fun surfaceDestroyed(h:SurfaceHolder){owner?.let{SdkMedia.surface(it,null)}}})}},modifier=Modifier.fillMaxWidth().aspectRatio(row.optDouble("aspect",16.0/9.0).coerceIn(0.1,10.0).toFloat()))
  Text("${position/60000}:${(position/1000%60).toString().padStart(2,'0')} / ${duration/60000}:${(duration/1000%60).toString().padStart(2,'0')}",color=MaterialTheme.colorScheme.onSurfaceVariant)
  Slider(value=if(dragging)seek else position.toFloat().coerceIn(0f,duration.coerceAtLeast(1).toFloat()),onValueChange={seek=it;dragging=true},onValueChangeFinished={command("seek",JSONObject().put("position_ms",seek.toLong()));dragging=false},valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=active&&duration>0,modifier=Modifier.fillMaxWidth().semantics{contentDescription="Playback position"})
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   ChoiceButton("Previous",modifier=Modifier.widthIn(min=88.dp,max=144.dp),enabled=active&&row.optInt("index")>0){command("previous")}
   ChoiceButton(if(row.optString("state")=="playing")"Pause"else"Play",modifier=Modifier.widthIn(min=88.dp,max=144.dp),enabled=active){command(if(row.optString("state")=="playing")"pause"else"play")}
   ChoiceButton("Next",modifier=Modifier.widthIn(min=88.dp,max=144.dp),enabled=active&&row.optInt("index")+1<count){command("next")}
   ChoiceButton("Stop",modifier=Modifier.widthIn(min=88.dp,max=144.dp),enabled=active){command("stop")}
   ChoiceButton("Speed ${row.optDouble("speed",1.0)}×",modifier=Modifier.widthIn(min=88.dp,max=144.dp),enabled=active){val options=listOf(0.5,1.0,1.5,2.0,3.0);command("speed",JSONObject().put("speed",options[(options.indexOf(row.optDouble("speed",1.0))+1).mod(options.size)]))}
  }
  if(row.optString("error").isNotEmpty())Text(row.getString("error"),color=MaterialTheme.colorScheme.error)
 }
 DisposableEffect(id){onDispose{owner?.let{SdkMedia.surface(it,null)}}}
}
