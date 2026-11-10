package app.onklav.home

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.*
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.animation.core.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

internal data class TorchHardware(val id:String,val max:Int,val normal:Int)
internal data class TorchState(val on:Boolean=false,val sos:Boolean=false,val level:Int=1,val until:Long=0,val message:String="")
internal object TorchControl{
 val state=MutableStateFlow(TorchState())
 fun hardware(c:Context):TorchHardware?{val m=c.getSystemService(CameraManager::class.java);val id=m.cameraIdList.firstOrNull{m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true&&m.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK}?:return null;val info=m.getCameraCharacteristics(id);return TorchHardware(id,if(Build.VERSION.SDK_INT>=33)info.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)?:1 else 1,if(Build.VERSION.SDK_INT>=33)info.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)?:1 else 1)}
 fun allowed(c:Context)=essentialAccess(c,TORCH_APP,"storage.private","device.torch","background.tasks","notifications.post")&&ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
 fun start(c:Context,sos:Boolean,level:Int,minutes:Int){check(allowed(c)){"Allow Camera and Flashlight access first"};SdkTorch.clear();ContextCompat.startForegroundService(c,Intent(c,FlashlightService::class.java).putExtra("sos",sos).putExtra("level",level).putExtra("minutes",minutes))}
 fun stop(c:Context){c.stopService(Intent(c,FlashlightService::class.java))}
}
class FlashlightService:Service(){
 private val h=Handler(Looper.getMainLooper());private var hardware:TorchHardware?=null;private var wake:PowerManager.WakeLock?=null;private var actualEnabled=false;private var writing=false;private var started=0L;private var level=1;private var sos=false;private var until=0L
 private val manager by lazy{getSystemService(CameraManager::class.java)}
 private val callback=object:CameraManager.TorchCallback(){override fun onTorchModeUnavailable(cameraId:String){if(cameraId==hardware?.id)fail("Camera is using the light. Try again when it closes.")};override fun onTorchModeChanged(cameraId:String,enabled:Boolean){if(cameraId==hardware?.id){actualEnabled=enabled;if(!enabled)h.postDelayed({if(!actualEnabled&&!sos&&TorchControl.state.value.on)fail("Flashlight was turned off by another app")},250)}}}
 override fun onBind(i:Intent?)=null
 override fun onCreate(){super.onCreate();hardware=runCatching{TorchControl.hardware(this)}.getOrNull();manager.registerTorchCallback(callback,h)}
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("flashlight","Flashlight",NotificationManager.IMPORTANCE_LOW))
  val off=PendingIntent.getBroadcast(this,0,Intent(this,FlashlightOffReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val open=PendingIntent.getActivity(this,832,Intent(this,MainActivity::class.java).putExtra("open_native_tool","flashlight"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  startForeground(832,NotificationCompat.Builder(this,"flashlight").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("Flashlight on").setContentText("Tap to open controls").setOngoing(true).setContentIntent(open).addAction(0,"Turn off",off).build())
  if(!TorchControl.allowed(this)||hardware==null){fail(if(hardware==null)"This device has no rear flashlight"else"Flashlight access is off");return START_NOT_STICKY}
  sos=i?.getBooleanExtra("sos",false)?:false;level=(i?.getIntExtra("level",hardware!!.normal)?:1).coerceIn(1,hardware!!.max);val minutes=i?.getIntExtra("minutes",0)?.coerceIn(0,30)?:0
  if(started==0L){started=SystemClock.elapsedRealtime();wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"onklav:flashlight").apply{acquire(31*60000L)}}
  until=if(minutes>0)SystemClock.elapsedRealtime()+minutes*60000L else 0;h.removeCallbacksAndMessages(null);TorchControl.state.value=TorchState(true,sos,level,until)
  if(sos)pulse(0)else light(true)
  h.postDelayed(object:Runnable{override fun run(){if(!TorchControl.allowed(this@FlashlightService))fail("Flashlight access was turned off")else if(until>0&&SystemClock.elapsedRealtime()>=until)stopSelf()else{if(wake?.isHeld!=true)wake?.acquire(31*60000L);h.postDelayed(this,1000)}}},1000)
  return START_NOT_STICKY
 }
 private fun light(on:Boolean){runCatching{writing=true;val id=hardware?.id?:return;if(Build.VERSION.SDK_INT>=33&&on&&hardware!!.max>1)manager.turnOnTorchWithStrengthLevel(id,level)else manager.setTorchMode(id,on)}.onFailure{fail(if(it is CameraAccessException)"Light unavailable. Close the camera and try again."else it.message?:"Could not control the light")};writing=false}
 private val morse=longArrayOf(300,300,300,300,300,900,900,300,900,300,900,900,300,300,300,300,300,2100)
 private fun pulse(index:Int){if(!TorchControl.state.value.on)return;light(index%2==0);h.postDelayed({pulse((index+1)%morse.size)},morse[index])}
 private fun fail(message:String){TorchControl.state.value=TorchControl.state.value.copy(on=false,message=message);stopSelf()}
 override fun onDestroy(){h.removeCallbacksAndMessages(null);manager.unregisterTorchCallback(callback);runCatching{hardware?.let{manager.setTorchMode(it.id,false)}};wake?.let{if(it.isHeld)it.release()};TorchControl.state.value=TorchState(message=TorchControl.state.value.message);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
class FlashlightOffReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){TorchControl.stop(c)}}
@Composable internal fun FlashlightWorkspace(){val c=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();if(!remember(epoch){essentialAccess(c,TORCH_APP,"storage.private","device.torch")}){Text("Flashlight access is off. Restore it in Settings > Apps > Flashlight.",Modifier.padding(24.dp));return};FlashlightContent()}
@Composable internal fun FlashlightContent(){
 val c=LocalContext.current;val p=remember{c.getSharedPreferences("flashlight",0)};val hardware=remember{runCatching{TorchControl.hardware(c)}.getOrNull()};val state by TorchControl.state.collectAsState();var screen by remember{mutableStateOf(false)};var sos by remember{mutableStateOf(p.getBoolean("sos",false))};var minutes by remember{mutableIntStateOf(p.getInt("minutes",5))};var level by remember{mutableFloatStateOf(p.getInt("level",hardware?.normal?:1).coerceIn(1,hardware?.max?:1).toFloat())};var message by remember{mutableStateOf("")};var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
 LaunchedEffect(state.on){while(state.on){now=SystemClock.elapsedRealtime();delay(1000)}}
 fun start(){runCatching{TorchControl.start(c,sos,level.toInt(),minutes)}.onFailure{message=it.message?:"Could not start flashlight"}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)start()else message="Camera permission is needed for the phone’s flashlight. You can still use screen light."}
 val notification=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
  Spacer(Modifier.height(16.dp));Text(if(state.on)if(state.sos)"SOS is on"else"Flashlight is on"else"Flashlight is off",style=MaterialTheme.typography.headlineSmall)
  FilledTonalIconButton(onClick={if(state.on)TorchControl.stop(c)else if(ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.CAMERA)else start()},enabled=hardware!=null,modifier=Modifier.size(144.dp),shape=CircleShape,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=if(state.on)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,contentColor=if(state.on)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)){Icon(Icons.Outlined.PowerSettingsNew,if(state.on)"Turn flashlight off"else"Turn flashlight on",Modifier.size(64.dp))}
  if(hardware==null)Text("No rear flashlight. Use screen light below.",color=MaterialTheme.colorScheme.onSurfaceVariant)
  if(state.until>0&&state.on)Text("Turns off in ${elapsedLabel(state.until-now)}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
  (message.ifBlank{state.message}).takeIf{it.isNotBlank()}?.let{Text(it,color=MaterialTheme.colorScheme.error);if(ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)TextButton(onClick={c.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${c.packageName}")))}){Text("Android app settings")};TextButton(onClick={message="";TorchControl.state.value=state.copy(message="")}){Text("Dismiss")}}
  if(hardware!=null&&hardware.max>1){Row(Modifier.fillMaxWidth()){Icon(Icons.Outlined.BrightnessLow,null);Text("Brightness",Modifier.weight(1f).padding(start=10.dp));Text("${level.toInt()} / ${hardware.max}",style=MaterialTheme.typography.labelMedium)};Slider(level,{level=it},valueRange=1f..hardware.max.toFloat(),steps=(hardware.max-2).coerceAtLeast(0),onValueChangeFinished={p.edit().putInt("level",level.toInt()).apply();if(state.on)start()})}
  HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("SOS signal",style=MaterialTheme.typography.titleMedium);Text("Slow Morse light signal",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(sos,{sos=it;p.edit().putBoolean("sos",it).apply();if(state.on)start()})}
  Text("Auto-off",Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleSmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0,1,5,10,30).forEach{m->FilterChip(selected=minutes==m,onClick={minutes=m;p.edit().putInt("minutes",m).apply();if(state.on)start()},label={Text(if(m==0)"Never"else"$m min")})}}
  OutlinedButton(onClick={screen=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.LightMode,null);Text("Screen light",Modifier.padding(start=8.dp))}
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)TextButton(onClick={notification.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Allow off-switch notification")}
 }
 if(screen)ScreenLight{screen=false}
}
@Composable private fun ScreenLight(close:()->Unit){
 var warmth by remember{mutableIntStateOf(0)};var brightness by remember{mutableFloatStateOf(.9f)}
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  val view=androidx.compose.ui.platform.LocalView.current;val window=(view.parent as? DialogWindowProvider)?.window
  DisposableEffect(window){val old=window?.attributes?.screenBrightness;val flags=window?.attributes?.flags?:0;window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{window?.attributes=window?.attributes?.apply{screenBrightness=old?:WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE};if(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON==0)window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
  SideEffect{window?.attributes=window?.attributes?.apply{screenBrightness=brightness}}
  val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
  val hue=if(warmth==3&&lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){
   val transition=rememberInfiniteTransition(label="Screen color fade")
   val value by transition.animateFloat(0f,360f,infiniteRepeatable(animation=tween(30000,easing=LinearEasing)),label="Hue")
   value
  }else 0f
  val color=if(warmth==3)Color.hsv(hue,.7f,1f) else listOf(Color.White,Color(0xFFFFDD9A),Color(0xFFDC2929))[warmth]
  Column(Modifier.fillMaxSize().background(color).padding(WindowInsets.safeDrawing.asPaddingValues()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){IconButton(onClick=close){Icon(Icons.Outlined.Close,"Close screen light",tint=Color.Black)}};Spacer(Modifier.weight(1f));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("White","Warm","Red","Color fade").forEachIndexed{i,s->FilledTonalButton(onClick={warmth=i}){Text(s)}}};Slider(brightness,{brightness=it},valueRange=.05f..1f,modifier=Modifier.fillMaxWidth());Text("Screen brightness",color=Color.Black,style=MaterialTheme.typography.labelLarge)}
 }
}
