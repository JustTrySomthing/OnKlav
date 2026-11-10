package app.onklav.home

import android.Manifest
import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.os.*
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.OnKlavTheme
import app.onklav.shared.LocalAppearanceSpec
import app.onklav.shared.sentBrush
import app.onklav.shared.sentInk
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun ClockWorkspace(){val c=LocalContext.current;val e by AppSdk.epoch.collectAsState();if(!remember(e){essentialAccess(c,CLOCK_APP,"storage.private")}){Text("Clock access is off. Restore it in Settings > Apps > Clock.",Modifier.padding(24.dp));return};ClockContent()}
@Composable private fun clockTick(period:Long=200):Long{var tick by remember{mutableLongStateOf(System.currentTimeMillis())};val owner=LocalLifecycleOwner.current;LaunchedEffect(owner,period){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){tick=System.currentTimeMillis();delay(period)}}};return tick}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ClockContent(){
 val c=LocalContext.current;val epoch by ClockDisk.epoch.collectAsState();val now=clockTick();var tab by rememberSaveable{mutableIntStateOf(0)};var alarm by remember{mutableStateOf<ClockAlarm?>(null)};var addingTimer by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var permissionTick by remember{mutableIntStateOf(0)}
 val lifecycle=LocalLifecycleOwner.current;DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){permissionTick++;ClockDisk.epoch.value++}};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 val alarms=remember(epoch){ClockDisk.alarms(c)};val timers=remember(epoch){ClockDisk.timers(c)};val watch=remember(epoch){ClockDisk.watch(c)};val rings=remember(epoch){ClockDisk.rings(c)}
 fun attempt(block:()->Unit){runCatching(block).onFailure{message=it.message?:"Could not save. Try again."}}
 var pendingSchedule by remember{mutableStateOf<(() -> Unit)?>(null)}
 val approval=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){
  permissionTick++;val action=pendingSchedule;pendingSchedule=null
  if(ClockDisk.exact(c)&&action!=null)attempt(action)else message="Alarm access was not approved. Your changes are kept."
 }
 fun schedule(action:()->Unit){if(ClockDisk.exact(c))attempt(action)else{pendingSchedule=action;runCatching{approval.launch(OnKlavPermissions.exactIntent(c))}.onFailure{pendingSchedule=null;message="Android could not open alarm approval. Your changes are kept."}}}
 val notify=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permissionTick++}
 val tone=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r->if(r.resultCode==Activity.RESULT_OK){@Suppress("DEPRECATION") val uri=r.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);alarm=alarm?.copy(tone=uri?.toString().orEmpty())}}
 val exact=remember(permissionTick){ClockDisk.exact(c)}
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground){
 Column(Modifier.fillMaxSize()){
  ClockHeader(now,tab,alarms.filter{it.enabled&&it.next>now}.minByOrNull{it.next}){settings=true}
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   listOf("Alarms" to Icons.Outlined.Alarm,"Timer" to Icons.Outlined.Timer,"Stopwatch" to Icons.Outlined.Timer).forEachIndexed{i,(label,icon)->
    Surface(onClick={tab=i},modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),color=Color.Transparent){
     Column(Modifier.fillMaxWidth().then(if(tab==i)Modifier.background(sentBrush(LocalAppearanceSpec.current))else Modifier).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,Modifier.size(22.dp),tint=if(tab==i)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(4.dp));Text(label,style=MaterialTheme.typography.labelMedium,color=if(tab==i)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)}
    }
   }
  }
  if(!exact)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text("Allow precise alarms",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={runCatching{c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:${c.packageName}")))}.onFailure{message="Open Android Settings > OnKlav > Alarms & reminders"}}){Text("Allow")}}
  if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text("Show alarm notifications",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);TextButton(onClick={notify.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow")}}
  if(message.isNotBlank())Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically){Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall);IconButton(onClick={message=""}){Icon(Icons.Outlined.Close,"Dismiss message")}}
  if(rings.isNotEmpty())ClockRinging()
  when(tab){
   0->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your alarms",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);FilledTonalButton(onClick={alarm=ClockAlarm(enabled=false)},shape=RoundedCornerShape(16.dp)){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("Add alarm")}}}
    if(alarms.isEmpty())item{ClockEmpty("No alarms",Icons.Outlined.Alarm)}
    items(alarms.sortedWith(compareBy<ClockAlarm>{it.hour}.thenBy{it.minute}),key={it.id}){a->Card(onClick={alarm=a},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=if(a.enabled)Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,contentColor=if(a.enabled)sentInk(LocalAppearanceSpec.current)else MaterialTheme.colorScheme.onSurface)){Row(Modifier.fillMaxWidth().then(if(a.enabled)Modifier.background(sentBrush(LocalAppearanceSpec.current))else Modifier).padding(start=20.dp,top=18.dp,bottom=18.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(alarmTime(c,a),style=MaterialTheme.typography.displaySmall.copy(fontFeatureSettings="tnum"),fontWeight=FontWeight.Medium,color=if(a.enabled)sentInk(LocalAppearanceSpec.current) else MaterialTheme.colorScheme.onSurfaceVariant);Text(a.label,style=MaterialTheme.typography.titleSmall);Text(alarmDays(a.days),style=MaterialTheme.typography.bodySmall,color=if(a.enabled)sentInk(LocalAppearanceSpec.current).copy(alpha=.8f)else MaterialTheme.colorScheme.onSurfaceVariant);if(a.enabled&&a.next>now)Text("In ${alarmDistance(a.next-now)}",style=MaterialTheme.typography.labelSmall,color=sentInk(LocalAppearanceSpec.current))};Switch(checked=a.enabled,onCheckedChange={enable->schedule{ClockDisk.saveAlarm(c,a.copy(enabled=enable,next=0,snoozed=false))}});IconButton(onClick={attempt{ClockDisk.deleteAlarm(c,a.id)}}){Icon(Icons.Outlined.DeleteOutline,"Delete ${a.label}")}}}}
   }
   1->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Make time for it",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);FilledTonalButton(onClick={addingTimer=true},shape=RoundedCornerShape(16.dp)){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("Add timer")}}}
    item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(1,5,10).forEach{min->OutlinedButton(onClick={schedule{ClockDisk.timer(c,ClockTimer(label="$min minute${if(min==1)""else"s"}",duration=min*60000L),true)}},modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("$min min")}}}}
    if(timers.isEmpty())item{ClockEmpty("No timers",Icons.Outlined.Timer)}
    items(timers,key={it.id}){t->val left=t.left(c,wallNow=now);Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(t.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={attempt{ClockDisk.deleteTimer(c,t.id)}}){Icon(Icons.Outlined.Close,"Remove ${t.label}")}};ClockProgressDial((left.toFloat()/t.duration).coerceIn(0f,1f),elapsedLabel((left+999)/1000*1000),if(t.end>0)"TIME REMAINING"else"READY WHEN YOU ARE",Modifier.fillMaxWidth().height(228.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){IconButton(onClick={schedule{ClockDisk.timer(c,t.copy(remaining=t.duration,end=0),false)}}){Icon(Icons.Outlined.RestartAlt,"Reset timer")};FilledTonalButton(onClick={schedule{ClockDisk.timer(c,if(t.left(c)==0L)t.copy(remaining=t.duration,end=0)else t,t.end==0L)}}){Icon(if(t.end>0)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,null);Text(if(t.end>0)"Pause"else if(t.left(c)==0L)"Restart"else"Start")}}}}}
   }
   else->{val value=watch.value(c);Column(Modifier.weight(1f).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){ClockProgressDial((value%60000)/60000f,elapsedLabel(value,true),if(watch.start>0)"EVERY MOMENT COUNTS"else if(value>0)"TAKE YOUR TIME"else"READY, SET…",Modifier.fillMaxWidth().weight(1f));Spacer(Modifier.height(16.dp));Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically){OutlinedIconButton(onClick={attempt{if(watch.start!=0L){check(watch.laps.size<1000){"Lap limit reached"};ClockDisk.setWatch(c,watch.copy(laps=watch.laps+value))}else ClockDisk.setWatch(c,ClockWatch())}},enabled=watch.start!=0L||value>0){Icon(if(watch.start>0)Icons.Outlined.Flag else Icons.Outlined.RestartAlt,if(watch.start>0)"Lap"else"Reset stopwatch")};Button(modifier=Modifier.height(56.dp).widthIn(min=140.dp),shape=RoundedCornerShape(20.dp),onClick={attempt{ClockDisk.setWatch(c,if(watch.start>0)watch.copy(accumulated=value,start=0,wall=0)else watch.copy(start=SystemClock.elapsedRealtime(),wall=now,boot=ClockDisk.boot(c)))}}){Icon(if(watch.start>0)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,null);Text(if(watch.start>0)"Pause"else if(value==0L)"Start"else"Resume")};OutlinedIconButton(onClick={attempt{check(essentialAccess(c,CLOCK_APP,"share.text")){"Sharing access is off"};c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Stopwatch: ${elapsedLabel(value,true)}\n"+watch.laps.mapIndexed{i,l->"Lap ${i+1}: ${elapsedLabel(l-(watch.laps.getOrNull(i-1)?:0),true)} · ${elapsedLabel(l,true)}"}.joinToString("\n")),"Share laps"))}},enabled=watch.laps.isNotEmpty()){Icon(Icons.Outlined.Share,"Share laps")}};Spacer(Modifier.height(16.dp));LazyColumn(Modifier.weight(1f)){if(watch.laps.isNotEmpty())item{Row(Modifier.fillMaxWidth().padding(vertical=8.dp)){Text("Lap",Modifier.weight(1f));Text("Split / Total",color=MaterialTheme.colorScheme.onSurfaceVariant)}};items(watch.laps.indices.reversed().toList(),key={it}){i->HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f));Row(Modifier.fillMaxWidth().padding(vertical=12.dp)){Text("${i+1}",Modifier.weight(1f));Text("${elapsedLabel(watch.laps[i]-(watch.laps.getOrNull(i-1)?:0),true)}  /  ${elapsedLabel(watch.laps[i],true)}",style=MaterialTheme.typography.bodyMedium)}}}}}
  }
 }}
 if(alarm!=null)key(alarm!!.id){val a=alarm!!;val time=rememberTimePickerState(a.hour,a.minute,DateFormat.is24HourFormat(c));var label by remember{mutableStateOf(a.label)};var days by remember{mutableIntStateOf(a.days)};var vibrate by remember{mutableStateOf(a.vibrate)};var snooze by remember{mutableIntStateOf(a.snooze)}
  AlertDialog(onDismissRequest={alarm=null},title={Text("Alarm")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){TimeInput(time);OutlinedTextField(label,{label=it.take(80)},label={Text("Name")},singleLine=true);Text("Repeat",style=MaterialTheme.typography.labelLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("M","T","W","T","F","S","S").forEachIndexed{i,s->FilterChip(selected=days and(1 shl i)!=0,onClick={days=days xor(1 shl i)},label={Text(s)},modifier=Modifier.weight(1f),border=null)}};Row(verticalAlignment=Alignment.CenterVertically){Text("Vibrate",Modifier.weight(1f));Switch(vibrate,{vibrate=it})};Row(verticalAlignment=Alignment.CenterVertically){Text("Snooze",Modifier.weight(1f));listOf(5,10,15).forEach{FilterChip(selected=snooze==it,onClick={snooze=it},label={Text("${it}m")})}};TextButton(onClick={alarm=a.copy(hour=time.hour,minute=time.minute,label=label,days=days,vibrate=vibrate,snooze=snooze);tone.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,if(a.tone.isEmpty())RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)else android.net.Uri.parse(a.tone)))}){Icon(Icons.Outlined.MusicNote,null);Text(if(a.tone.isBlank())"Alarm sound"else"Change sound")}}},confirmButton={TextButton(onClick={schedule{ClockDisk.saveAlarm(c,a.copy(hour=time.hour,minute=time.minute,label=label.ifBlank{"Alarm"},days=days,vibrate=vibrate,snooze=snooze,enabled=true,next=0,snoozed=false));alarm=null}}){Text("Save")}},dismissButton={TextButton(onClick={alarm=null}){Text("Cancel")}})
 }
 if(addingTimer){var h by remember{mutableStateOf("")};var m by remember{mutableStateOf("5")};var s by remember{mutableStateOf("")};var label by remember{mutableStateOf("")};AlertDialog(onDismissRequest={addingTimer=false},title={Text("New timer")},text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)){OutlinedTextField(label,{label=it.take(80)},label={Text("Name")},singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(Triple(h,"Hours",{v:String->h=v}),Triple(m,"Minutes",{v:String->m=v}),Triple(s,"Seconds",{v:String->s=v})).forEach{(value,name,set)->OutlinedTextField(value,{if(it.length<=3&&it.all(Char::isDigit))set(it)},label={Text(name)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f))}}}},confirmButton={TextButton(onClick={schedule{val duration=((h.toLongOrNull()?:0)*3600+(m.toLongOrNull()?:0)*60+(s.toLongOrNull()?:0))*1000;check(duration in 1000..7*86400000L){"Choose1 second to7 days"};ClockDisk.timer(c,ClockTimer(label=label.ifBlank{"Timer"},duration=duration),true);addingTimer=false}}){Text("Start")}},dismissButton={TextButton(onClick={addingTimer=false}){Text("Cancel")}})}
 if(settings)EssentialDialog("Clock settings",{settings=false}){TextButton(onClick={if(Build.VERSION.SDK_INT>=33)notify.launch(Manifest.permission.POST_NOTIFICATIONS)else message="Notifications are managed in Android settings"}){Text("Allow alarm notifications")};TextButton(onClick={c.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName))}){Text("Notification settings")};if(Build.VERSION.SDK_INT>=34)TextButton(onClick={c.startActivity(OnKlavPermissions.lockScreenIntent(c))}){Text("Full-screen calls & alarms")};TextButton(onClick={c.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))}){Text("Alarm volume & sounds")};Text("Silence after",style=MaterialTheme.typography.titleSmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(5,10,20).forEach{minutes->FilterChip(selected=ClockDisk.read(c).optInt("silence",10)==minutes,onClick={attempt{ClockDisk.update(c){it.put("silence",minutes)}}},label={Text("$minutes min")})}};ClockDisk.read(c).optString("error").takeIf{it.isNotBlank()}?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={ClockDisk.update(c){it.remove("error")}}){Text("Dismiss")}}}
}
@Composable private fun ClockEmpty(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector){
 val colors=MaterialTheme.colorScheme
 Column(Modifier.fillMaxWidth().padding(vertical=40.dp),horizontalAlignment=Alignment.CenterHorizontally){
  Box(Modifier.size(104.dp).clip(CircleShape).background(Brush.radialGradient(listOf(colors.primaryContainer,colors.surfaceContainerLow))),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(42.dp),tint=colors.primary)}
  Spacer(Modifier.height(18.dp));Text(text,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp));Text(if(text=="No alarms")"Set your next wake-up or reminder."else"Choose a quick timer or add your own.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
 }
}
@Composable private fun ClockHeader(now:Long,tab:Int,next:ClockAlarm?,settings:()->Unit){
 val c=LocalContext.current;val colors=MaterialTheme.colorScheme;val date=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
 if(tab==0)Box(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).clip(RoundedCornerShape(28.dp)).background(sentBrush(LocalAppearanceSpec.current))){
  Row(Modifier.fillMaxWidth().padding(start=20.dp,top=20.dp,bottom=20.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),style=MaterialTheme.typography.labelLarge,color=colors.onPrimaryContainer.copy(alpha=.75f))
    Text(date.format(DateTimeFormatter.ofPattern(if(DateFormat.is24HourFormat(c))"HH:mm"else"h:mm")),style=MaterialTheme.typography.displayLarge.copy(fontFeatureSettings="tnum"),fontWeight=FontWeight.Light,color=colors.onPrimaryContainer)
    if(!DateFormat.is24HourFormat(c))Text(date.format(DateTimeFormatter.ofPattern("a")),style=MaterialTheme.typography.labelMedium,color=colors.onPrimaryContainer)
    Spacer(Modifier.height(6.dp));Text(if(next==null)"A fresh start, on your time"else"Next alarm in ${alarmDistance(next.next-now)}",style=MaterialTheme.typography.labelMedium,color=colors.onPrimaryContainer)
   }
   if(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=380&&androidx.compose.ui.platform.LocalDensity.current.fontScale<=1.3f)ClockFace(date,Modifier.size(90.dp));IconButton(onClick=settings,modifier=Modifier.align(Alignment.Top)){Icon(Icons.Outlined.Settings,"Clock settings",tint=colors.onPrimaryContainer)}
  }
 }else Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp,top=10.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(tab==1)"Timers"else"Stopwatch",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Medium);Text(if(tab==1)"A moment for what matters"else"Find your rhythm",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)};IconButton(onClick=settings){Icon(Icons.Outlined.Settings,"Clock settings")}}
}
@Composable private fun ClockFace(time:ZonedDateTime,modifier:Modifier){
 val ink=MaterialTheme.colorScheme.onPrimaryContainer;val accent=MaterialTheme.colorScheme.primary
 Canvas(modifier){val radius=size.minDimension/2;drawCircle(ink.copy(alpha=.05f));for(i in 0 until 12){val a=i*PI/6-PI/2;val u=Offset(cos(a).toFloat(),sin(a).toFloat());drawLine(ink.copy(alpha=.4f),center+u*radius*.82f,center+u*radius*.9f,2.dp.toPx(),StrokeCap.Round)}
  fun hand(fraction:Double,length:Float,width:Float,color:Color){val a=fraction*2*PI-PI/2;drawLine(color,center,center+Offset(cos(a).toFloat(),sin(a).toFloat())*radius*length,width,StrokeCap.Round)}
  hand((time.hour%12+time.minute/60.0)/12,.5f,3.dp.toPx(),ink);hand((time.minute+time.second/60.0)/60,.7f,2.dp.toPx(),ink);hand(time.second/60.0,.77f,1.dp.toPx(),accent);drawCircle(ink,3.dp.toPx())
 }
}
@Composable private fun ClockProgressDial(progress:Float,time:String,label:String,modifier:Modifier){
 val colors=MaterialTheme.colorScheme
 Box(modifier,contentAlignment=Alignment.Center){
  Canvas(Modifier.fillMaxHeight().aspectRatio(1f).padding(12.dp)){
   val stroke=7.dp.toPx();val pad=stroke/2;val dim=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke)
   drawArc(colors.outlineVariant.copy(alpha=.5f),-90f,360f,false,Offset(pad,pad),dim,style=Stroke(stroke,cap=StrokeCap.Round))
   if(progress>0)drawArc(Brush.sweepGradient(listOf(colors.primary,colors.tertiary,colors.primary)), -90f,360f*progress.coerceIn(0f,1f),false,Offset(pad,pad),dim,style=Stroke(stroke,cap=StrokeCap.Round))
   for(i in 0 until 60){val a=i*PI/30;val u=Offset(cos(a).toFloat(),sin(a).toFloat());val radius=size.minDimension/2;drawLine(colors.onSurfaceVariant.copy(alpha=if(i%5==0).4f else .15f),center+u*radius*.83f,center+u*radius*(if(i%5==0).89f else .86f),1.dp.toPx())}
  }
  Column(horizontalAlignment=Alignment.CenterHorizontally){Text(time,style=MaterialTheme.typography.displayMedium.copy(fontFeatureSettings="tnum",fontSize=if(time.length>8)30.sp else if(time.length>6)36.sp else 44.sp),fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Light);Spacer(Modifier.height(8.dp));Text(label,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)}
 }
}
private fun alarmTime(c:Context,a:ClockAlarm)=java.text.SimpleDateFormat(if(DateFormat.is24HourFormat(c))"HH:mm"else"h:mm a",java.util.Locale.getDefault()).format(java.util.Date(ClockDisk.next(a)))
private fun alarmDays(days:Int)=if(days==0)"Once"else if(days==127)"Every day"else listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").filterIndexed{i,_->days and(1 shl i)!=0}.joinToString(" · ")
private fun alarmDistance(ms:Long):String{val m=(ms/60000).coerceAtLeast(1);return if(m<60)"$m min"else if(m<1440)"${m/60}h ${m%60}m"else"${m/1440}d ${m/60%24}h"}
@Composable internal fun ClockRinging() {
 val c = LocalContext.current
 val epoch by ClockDisk.epoch.collectAsState()
 val rows = remember(epoch) { ClockDisk.rings(c) }
 var error by remember { mutableStateOf("") }
 fun handle(key: String, snooze: Boolean) {
  runCatching { ClockDisk.dismiss(c, key, snooze) }
   .onSuccess { error = "" }
   .onFailure { error = it.message ?: "Could not update this alarm." }
 }
 Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
  if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
  rows.forEach { (key, label) ->
   val alarm = key.startsWith("a-")
   Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     Icon(Icons.Outlined.Alarm, null, Modifier.size(32.dp))
     Text(label.ifBlank { if (alarm) "Alarm" else "Timer" }, style = MaterialTheme.typography.headlineMedium)
     Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (alarm) FilledTonalButton(onClick = { handle(key, true) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Snooze") }
      Button(onClick = { handle(key, false) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Dismiss") }
     }
    }
   }
  }
 }
}

class ClockAlarmActivity : ComponentActivity() {
 override fun onCreate(state: Bundle?) {
  super.onCreate(state)
  setShowWhenLocked(true)
  setTurnScreenOn(true)
  setContent {
   OnKlavTheme {
    Surface(Modifier.fillMaxSize()) {
     val epoch by ClockDisk.epoch.collectAsState()
     val rows = remember(epoch) { ClockDisk.rings(this) }
     var hadRings by rememberSaveable { mutableStateOf(rows.isNotEmpty()) }
     LaunchedEffect(rows.isNotEmpty()) {
      if (rows.isNotEmpty()) hadRings = true
      else if (hadRings) finish()
     }
     Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
      verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
      Text(if (rows.isEmpty()) "Clock" else if (rows.first().first.startsWith("a-")) "Alarm ringing" else "Timer finished",
       Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineLarge)
      if (rows.isNotEmpty()) ClockRinging()
      else if (!hadRings) Button(onClick = {
       startActivity(Intent(this@ClockAlarmActivity, MainActivity::class.java).putExtra("open_native_tool", "clock"))
       finish()
      }) { Text("Open Clock") }
      TextButton(onClick = { finish() }) { Text("Close") }
     }
    }
   }
  }
 }
}
