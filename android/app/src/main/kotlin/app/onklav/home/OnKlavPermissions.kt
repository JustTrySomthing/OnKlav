package app.onklav.home

import android.Manifest
import android.app.AlarmManager
import android.app.role.RoleManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Android approvals belong to host setup; included apps already receive their declared SDK grants. */
internal object OnKlavPermissions {
 const val SETUP_VERSION=6
 val messageRuntime=listOf(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS,Manifest.permission.RECEIVE_MMS,Manifest.permission.RECEIVE_WAP_PUSH,Manifest.permission.READ_PHONE_STATE)
 fun seen(c:Context)=c.getSharedPreferences("onklav-setup",0).getInt("permissions_seen",0)>=SETUP_VERSION
 fun finish(c:Context){c.getSharedPreferences("onklav-setup",0).edit().putInt("permissions_seen",SETUP_VERSION).apply()}
 fun granted(c:Context,p:String)=ContextCompat.checkSelfPermission(c,p)==PackageManager.PERMISSION_GRANTED
 fun runtime(c:Context):List<String> = buildList {
  if(c.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))add(Manifest.permission.CAMERA)
  if(c.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))add(Manifest.permission.RECORD_AUDIO)
  if(Build.VERSION.SDK_INT<30){add(Manifest.permission.READ_EXTERNAL_STORAGE);add(Manifest.permission.WRITE_EXTERNAL_STORAGE)}
  add(Manifest.permission.READ_CONTACTS)
  add(Manifest.permission.WRITE_CONTACTS)
  if(PhoneAccess.role(c))addAll(PhoneAccess.permissions)
  if(MessagingTransport.role(c))addAll(messageRuntime)
  if(!granted(c,Manifest.permission.ACCESS_COARSE_LOCATION)){add(Manifest.permission.ACCESS_COARSE_LOCATION);add(Manifest.permission.ACCESS_FINE_LOCATION)}
  if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)
  if(Build.VERSION.SDK_INT>=31&&c.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){add(Manifest.permission.BLUETOOTH_CONNECT);add(Manifest.permission.BLUETOOTH_SCAN)}
  if(Build.VERSION.SDK_INT>=33&&c.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI))add(Manifest.permission.NEARBY_WIFI_DEVICES)
 }.distinct().filterNot{granted(c,it)}
 fun alarms(c:Context)=Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
 fun notifications(c:Context)=c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
 fun lockScreen(c:Context)=Build.VERSION.SDK_INT<34||c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
 fun lockScreenIntent(c:Context)=Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${c.packageName}"))
 fun exactIntent(c:Context)=Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${c.packageName}"))
}

internal data class SetupStep(val id:String,val title:String,val detail:String,val complete:Boolean,val action:String)
internal fun platformSetupSteps(c:Context):List<SetupStep> = buildList {
 val telephony=c.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
 if(telephony)add(SetupStep("messaging","Text & picture messages","Use OnKlav as your SMS app",MessagingTransport.role(c),"Choose OnKlav"))
 if(PhoneAccess.available(c))add(SetupStep("phone","Phone calls","Choose OnKlav for incoming calls, keypad & call history",PhoneAccess.role(c),"Choose OnKlav Phone"))
 val missing=OnKlavPermissions.runtime(c)
 add(SetupStep("access","App access",if(missing.isEmpty())"Calls, camera, audio, contacts & nearby devices" else "${missing.size} approvals remaining",missing.isEmpty(),"Allow access"))
 add(SetupStep("android-files","Android files","Browse photos, media, documents and downloads directly",AndroidFilesAccess.allowed(c),"Allow Android files"))
 add(SetupStep("notifications","Notifications","Calls, messages, reminders & alarms",OnKlavPermissions.notifications(c),"Allow notifications"))
 add(SetupStep("alarms","Alarms & timers","Ring at the time you choose",OnKlavPermissions.alarms(c),"Allow alarms"))
 if(Build.VERSION.SDK_INT>=34)add(SetupStep("lock","Lock-screen calls & alarms","Show calls & alarms over the lock screen",OnKlavPermissions.lockScreen(c),"Allow full-screen alerts"))
 add(SetupStep("power","Power button","Restart or shut down without physical buttons",PowerAccessibilityService.enabled(c),"Enable Power"))
}

@Composable internal fun SetupStepCard(number:Int,step:SetupStep,next:Boolean,directions:(()->Unit)?=null,skip:(()->Unit)?=null,skipped:Boolean=false,onClick:()->Unit) {
 val colors=MaterialTheme.colorScheme
 Surface(modifier=Modifier.fillMaxWidth().testTag("setup-step-${step.id}"),shape=RoundedCornerShape(20.dp),color=if(next)colors.primaryContainer else colors.surfaceVariant.copy(alpha=.45f)) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    Surface(shape=CircleShape,color=if(step.complete)colors.primary else colors.surface,modifier=Modifier.size(36.dp)) {
     Box(contentAlignment=Alignment.Center) { Text(number.toString(),fontWeight=FontWeight.Bold) }
    }
    Column(Modifier.weight(1f)) {
     Text(step.title,style=MaterialTheme.typography.titleMedium)
     Text(if(step.complete)"✓ Complete"else if(skipped)"Skipped for now"else if(next)"Next step"else"To do",style=MaterialTheme.typography.labelMedium,color=if(step.complete)colors.primary else colors.onSurfaceVariant)
    }
   }
   if(directions!=null)TextButton(onClick=directions,contentPadding=PaddingValues(0.dp)){Text("Directions",style=MaterialTheme.typography.labelLarge)}
   if(!step.complete) {
    Text(step.detail,style=MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(step.action) }
    if(skip!=null&&!skipped)TextButton(onClick=skip,modifier=Modifier.align(Alignment.Start),contentPadding=PaddingValues(horizontal=0.dp)){Text("Skip this step for now",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)}
   }
  }
 }
}

@Composable internal fun PlatformSetupScreen(done:()->Unit,messaging:()->Unit,showMessagingOptions:Boolean=true){
 val c=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
 // Back closes setup without claiming it is complete.
 BackHandler{done()}
 var epoch by remember{mutableIntStateOf(0)};var message by remember{mutableStateOf("")}
 var requestedAccess by remember{mutableStateOf(false)}
 val runtime=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){epoch++;requestedAccess=true;message=""}
 val special=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){epoch++;message=""}
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)epoch++};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 val steps=remember(epoch){platformSetupSteps(c)}
 var skipped by rememberSaveable{mutableStateOf(listOf<String>())}
 var powerDirections by remember{mutableStateOf(false)}
 val next=steps.firstOrNull{!it.complete&&it.id !in skipped};val completed=steps.count{it.complete};val ready=completed==steps.size
 fun request(step:SetupStep){runCatching{
  message=""
  when(step.id){
   "android-files"->special.launch(AndroidFilesAccess.request(c))
   "phone"->special.launch(PhoneAccess.request(c))
   "messaging"->special.launch(c.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS))
   "access"->{val missing=OnKlavPermissions.runtime(c);if(missing.isNotEmpty())runtime.launch(missing.toTypedArray())else epoch++}
   "notifications"->special.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName))
   "alarms"->special.launch(OnKlavPermissions.exactIntent(c))
   "lock"->special.launch(OnKlavPermissions.lockScreenIntent(c))
   "power"->special.launch(PowerAccessibilityService.settings())
  }
 }.onFailure{message="Android could not open this step. Try again."}}
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
  Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=16.dp).testTag("onklav-permission-setup"),horizontalAlignment=Alignment.CenterHorizontally){
   HomeHeader("Set up OnKlav",back=done){}
   Column(Modifier.widthIn(max=640.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text(if(ready)"You’re all set"else"Let’s get you ready",style=MaterialTheme.typography.headlineMedium)
    Text(if(ready)"All ${steps.size} steps complete"else"$completed of ${steps.size} complete · ${steps.size-completed} remaining",style=MaterialTheme.typography.bodyLarge,modifier=Modifier.testTag("setup-progress"))
    LinearProgressIndicator(progress={completed.toFloat()/steps.size},modifier=Modifier.fillMaxWidth().height(6.dp))
    if(next!=null)Text("Next: ${next.title}",style=MaterialTheme.typography.titleSmall)
    steps.forEachIndexed{index,step->SetupStepCard(index+1,step,step==next,onClick={skipped=skipped-step.id;request(step)},directions=if(step.id=="power")({powerDirections=true})else null,skip={skipped=skipped+step.id},skipped=step.id in skipped)}
    if(requestedAccess&&steps.any{it.id=="access"&&!it.complete}){
     Text("Access still off? Review the remaining permissions in Android.",style=MaterialTheme.typography.bodySmall)
     TextButton(onClick={runCatching{special.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}.onFailure{message="Android settings are unavailable."}}){Text("Review app permissions")}
    }
    if(showMessagingOptions)TextButton(onClick=messaging){Text("Messaging options & recovery")}
    if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
   }
   Surface(Modifier.widthIn(max=640.dp).fillMaxWidth(),color=MaterialTheme.colorScheme.background){Column(Modifier.padding(vertical=10.dp)){
    Button(onClick={if(ready||next==null){OnKlavPermissions.finish(c);done()}else request(next)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)){
     Text(if(ready)"Finish setup"else if(next==null)"Continue to Apps"else"Step ${steps.indexOf(next)+1} · ${next.action}")
    }
    if(!ready)TextButton(onClick={OnKlavPermissions.finish(c);done()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Finish later · ${steps.size-completed} remaining",fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
   }}
  }
 }
 if(powerDirections)PowerDirectionsDialog{powerDirections=false}

}
