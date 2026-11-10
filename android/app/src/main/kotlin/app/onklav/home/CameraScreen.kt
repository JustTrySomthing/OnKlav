package app.onklav.home

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.*
import android.net.Uri
import android.view.*
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import app.onklav.shared.R
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

internal class CameraToolbarState {
 var active by mutableStateOf(false);var gallery by mutableStateOf(false);var locked by mutableStateOf(false);var flashEnabled by mutableStateOf(false)
 var flash by mutableIntStateOf(ImageCapture.FLASH_MODE_OFF);var video by mutableStateOf(false);var torch by mutableStateOf(false);var timer by mutableIntStateOf(0)
 var onFlash:()->Unit={};var onTimer:()->Unit={};var onSettings:()->Unit={}
}
@Composable internal fun CameraHeaderButtons(state:CameraToolbarState){
 if(!state.gallery){
  IconButton(onClick={state.onFlash()},enabled=state.flashEnabled){Icon(if(state.video){if(state.torch)Icons.Outlined.FlashOn else Icons.Outlined.FlashOff}else when(state.flash){ImageCapture.FLASH_MODE_AUTO->Icons.Outlined.FlashAuto;ImageCapture.FLASH_MODE_ON->Icons.Outlined.FlashOn;else->Icons.Outlined.FlashOff},if(state.video)"Video light"else "Flash: ${when(state.flash){ImageCapture.FLASH_MODE_AUTO->"Auto";ImageCapture.FLASH_MODE_ON->"On";else->"Off"}}")}
  IconButton(onClick={state.onTimer()},enabled=!state.locked,modifier=Modifier.semantics{stateDescription=if(state.timer==0)"Off"else "${state.timer} seconds"}){
   Box(contentAlignment=Alignment.Center){Icon(painterResource(if(state.timer==0)R.drawable.camera_timer_off else R.drawable.camera_timer),"Capture timer");if(state.timer>0)Text("${state.timer}",Modifier.offset(x=12.dp,y=10.dp).background(Color.Black,CircleShape).padding(horizontal=3.dp),color=Color.White,fontSize=10.sp)}
  }
 }
 IconButton(onClick={state.onSettings()},enabled=!state.locked){Icon(Icons.Outlined.Settings,"Camera settings")}
}
@Composable internal fun CameraWorkspace(model:SdkAppModel,toolbar:CameraToolbarState?=null,onClose:()->Unit={}){val c=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();if(!remember(epoch){essentialAccess(c,CAMERA_APP,"storage.private","camera.capture","files.manage")}){Text("Camera access is off. Restore it in Settings > Apps > Camera.",Modifier.padding(24.dp));return};CameraContent(model,toolbar=toolbar,onClose=onClose)}
@Composable internal fun CameraContent(model:SdkAppModel,enableHardware:Boolean=true,toolbar:CameraToolbarState?=null,onClose:()->Unit={}){
 MediaAutoRotation(true)
 val c=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope();val p=remember{c.getSharedPreferences("camera-settings",0)}
 var startupAttempt by remember{mutableIntStateOf(0)}
 val startup=remember(startupAttempt){runCatching{CameraDisk.folder(c);NativeCamera(c) to PreviewView(c).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FILL_CENTER}}}
 if(startup.isFailure){Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("Camera could not start",style=MaterialTheme.typography.titleLarge);Text(startup.exceptionOrNull()?.message?:"Camera storage or hardware is unavailable",Modifier.padding(vertical=16.dp));Button(onClick={startupAttempt++}){Text("Retry camera")}};return}
 val (native,preview)=startup.getOrThrow()
 var front by rememberSaveable{mutableStateOf(false)};var videoMode by rememberSaveable{mutableStateOf(false)};var ratio by rememberSaveable{mutableStateOf(p.getString("ratio","4:3")?:"4:3")};var flash by rememberSaveable{mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)}
 var qualityName by rememberSaveable{mutableStateOf(p.getString("video","1080p")?:"1080p")};var high by remember{mutableStateOf(p.getBoolean("high",true))};var mirror by remember{mutableStateOf(p.getBoolean("mirror",true))};var grid by remember{mutableStateOf(p.getBoolean("grid",false))};var audio by remember{mutableStateOf(p.getBoolean("audio",true))};var countdown by remember{mutableIntStateOf(p.getInt("countdown",0))}
 var shutterSound by remember{mutableStateOf(p.getBoolean("shutter_sound",true))}
 val sound=remember{CameraShutterSound()};DisposableEffect(sound){onDispose{sound.close()}}
 val landscape=LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
 val displayRotation=LocalView.current.display?.rotation?:Surface.ROTATION_0
 var previewSize by remember{mutableStateOf(IntSize.Zero)}
 var permission by remember{mutableStateOf(ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)};var resumed by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))};var restart by remember{mutableIntStateOf(0)};var settings by remember{mutableStateOf(false)};var gallery by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<JSONObject?>(null)};var media by remember{mutableStateOf<MessagePart?>(null)};var rows by remember{mutableStateOf(listOf<JSONObject>())};var total by remember{mutableIntStateOf(0)};var pending by remember{mutableStateOf(CameraDisk.pending(c))};var status by remember{mutableStateOf("")};var saving by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(false)};var delete by remember{mutableStateOf<JSONObject?>(null)};var discard by remember{mutableStateOf<File?>(null)};var count by remember{mutableIntStateOf(0)};var timerJob by remember{mutableStateOf<Job?>(null)};var videoTorch by remember{mutableStateOf(false)};var rotation by remember{mutableIntStateOf(Surface.ROTATION_0)};var zoom by remember{mutableFloatStateOf(1f)};var exposure by remember{mutableIntStateOf(0)};var focusPoint by remember{mutableStateOf<Offset?>(null)}
 fun message(error:Throwable){status=error.message?:"Camera action failed. Try again."}
 fun refresh(){if(loading)return;loading=true;scope.launch{try{val result=model.cameraAction("list");rows=result.getJSONArray("items").let{a->(0 until a.length()).map{a.getJSONObject(it)}};total=result.optInt("total");pending=runCatching{CameraDisk.pending(c)}.getOrElse{status="Camera storage is unavailable";pending}}catch(e:Exception){message(e)}finally{loading=false}}}
 fun save(file:File){if(saving)return;saving=true;status="Saving capture…";CameraDisk.scope.launch{try{model.cameraAction("list");val saved=CameraDisk.save(c,file,model);status=if(file.extension=="mp4")"Saved to Video / Camera"else"Saved to Images / Camera";if(gallery)refresh();selected=null;@Suppress("UNUSED_VARIABLE") val id=saved.optString("id")}catch(e:Exception){status="Capture kept on phone. Tap Captures to retry saving. ${e.message.orEmpty()}"}finally{saving=false;pending=runCatching{CameraDisk.pending(c)}.getOrElse{status="Camera storage is unavailable";pending}}}}
 val complete:(File?,String)->Unit={file,note->if(file!=null)save(file)else status=note;if(note.isNotBlank()&&file!=null)status=note}
 fun shutter(){if(native.recording){native.stop();return};if(!native.ready||saving||count>0)return
  timerJob=scope.launch{for(i in countdown downTo 1){count=i;delay(1000)};count=0;if(videoMode)native.record(audio,complete)else native.photo(front&&mirror,complete){if(shutterSound||cameraRequiresShutterSound())sound.play()}}
 }
 val camPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permission=it;if(!it)status="Allow Camera in Android settings to take photos and videos"}
 var recordAfterPermission by remember{mutableStateOf(false)}
 val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){audio=true;p.edit().putBoolean("audio",true).apply();if(recordAfterPermission)shutter()}else{audio=false;status="Microphone is off. You can record silent video."};recordAfterPermission=false}
 DisposableEffect(owner,native){val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->{permission=ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;resumed=true;pending=runCatching{CameraDisk.pending(c)}.getOrElse{status="Camera storage is unavailable";pending}};Lifecycle.Event.ON_STOP->{resumed=false;timerJob?.cancel();count=0;native.close();videoTorch=false};else->Unit}};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer);timerJob?.cancel();native.close()}}
 DisposableEffect(resumed){val orientation=object:OrientationEventListener(c){override fun onOrientationChanged(degrees:Int){if(degrees==ORIENTATION_UNKNOWN)return;rotation=when(degrees){in 45..134->Surface.ROTATION_270;in 135..224->Surface.ROTATION_180;in 225..314->Surface.ROTATION_90;else->Surface.ROTATION_0};native.rotate(rotation)}};if(resumed&&orientation.canDetectOrientation())orientation.enable();onDispose{orientation.disable()}}
 LaunchedEffect(permission,resumed,front,videoMode,ratio,qualityName,high,restart,gallery,previewSize,displayRotation){if(enableHardware&&permission&&resumed&&!gallery&&previewSize.width>0&&previewSize.height>0){val q=when(qualityName){"480p"->Quality.SD;"720p"->Quality.HD;"4K"->Quality.UHD;else->Quality.FHD};native.bind(owner,preview,front,videoMode,q,if(videoMode)"16:9"else ratio,high,flash,rotation);zoom=1f;exposure=0;videoTorch=false}else native.close()}
 LaunchedEffect(native.camera){val cam=native.camera?:return@LaunchedEffect;val zoomObserver=androidx.lifecycle.Observer<androidx.camera.core.ZoomState>{zoom=it.zoomRatio};cam.cameraInfo.zoomState.observe(owner,zoomObserver);try{awaitCancellation()}finally{cam.cameraInfo.zoomState.removeObserver(zoomObserver)}}
 LaunchedEffect(gallery){if(gallery)refresh()}
 LaunchedEffect(focusPoint){if(focusPoint!=null){delay(1500);focusPoint=null}}
 LaunchedEffect(native.ready,videoMode){if(native.ready&&videoMode){qualityName=when(native.actualQuality){Quality.SD->"480p";Quality.HD->"720p";Quality.UHD->"4K";else->"1080p"}}}
 val locked=native.recording||native.working||saving||count>0
 val actions=toolbar?:remember{CameraToolbarState()}
 val controlTimer=countdown;val controlFlash=flash;val controlTorch=videoTorch
 SideEffect{actions.active=true;actions.gallery=gallery;actions.locked=locked;actions.flashEnabled=native.camera?.cameraInfo?.hasFlashUnit()==true&&count==0;actions.flash=controlFlash;actions.video=videoMode;actions.torch=controlTorch;actions.timer=controlTimer
  actions.onFlash={if(videoMode){videoTorch=!videoTorch;native.light(videoTorch)}else{flash=when(flash){ImageCapture.FLASH_MODE_OFF->ImageCapture.FLASH_MODE_AUTO;ImageCapture.FLASH_MODE_AUTO->ImageCapture.FLASH_MODE_ON;else->ImageCapture.FLASH_MODE_OFF};native.flash(flash)}}
  actions.onTimer={countdown=when(countdown){0->3;3->10;else->0};p.edit().putInt("countdown",countdown).apply()};actions.onSettings={settings=true}
 }
 DisposableEffect(toolbar){onDispose{toolbar?.let{it.active=false;it.gallery=false;it.locked=true;it.flashEnabled=false;it.onFlash={};it.onTimer={};it.onSettings={}}}}
 @Composable fun modes(){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!videoMode,onClick={videoMode=false},enabled=!locked,label={Text("Photo")});FilterChip(selected=videoMode,onClick={videoMode=true},enabled=!locked,label={Text("Video")})}}
 @Composable fun zoomControl(modifier:Modifier){val z=native.camera?.cameraInfo?.zoomState?.value;val low=z?.minZoomRatio?:1f;val highZoom=(z?.maxZoomRatio?:4f).coerceAtLeast(low+.01f)
  Row(modifier,verticalAlignment=Alignment.CenterVertically){Text("${"%.1f".format(zoom)}×",Modifier.width(42.dp),style=MaterialTheme.typography.labelMedium);Slider(zoom.coerceIn(low,highZoom),{native.zoom(it)},valueRange=low..highZoom,modifier=Modifier.weight(1f).semantics{contentDescription="Camera zoom"},enabled=z!=null&&z.maxZoomRatio>z.minZoomRatio&&count==0,colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color.White,inactiveTrackColor=Color.White.copy(alpha=.35f)))}
 }
 @Composable fun captures(){IconButton(onClick={gallery=true},enabled=!locked){Icon(Icons.Outlined.PhotoLibrary,"Open captures",Modifier.size(28.dp))}}
 @Composable fun capture(){FilledIconButton(onClick={if(videoMode&&audio&&!native.recording&&(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)){recordAfterPermission=true;micPermission.launch(Manifest.permission.RECORD_AUDIO)}else shutter()},enabled=native.recording||native.ready&&!native.working&&!saving&&count==0,modifier=Modifier.size(if(landscape)64.dp else 76.dp),shape=CircleShape,colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black,disabledContainerColor=Color.White.copy(alpha=.3f),disabledContentColor=Color.White.copy(alpha=.65f))){Icon(if(native.recording)Icons.Outlined.Stop else if(videoMode)Icons.Outlined.Videocam else Icons.Outlined.CameraAlt,if(native.recording)"Stop recording"else if(videoMode)"Record video"else"Take photo",Modifier.size(32.dp))}}
 @Composable fun switchCamera(){IconButton(onClick={if(native.recording)native.pause()else front=!front},enabled=native.recording||!locked&&native.hasFront&&native.hasBack){if(native.recording)Icon(if(native.paused)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,if(native.paused)"Resume recording"else"Pause recording",Modifier.size(28.dp))else Icon(painterResource(R.drawable.camera_switch_arrows),"Switch camera",Modifier.size(28.dp))}}
 Column(Modifier.fillMaxSize().background(if(gallery)MaterialTheme.colorScheme.background else Color.Black)){
  if(gallery)TextButton(onClick={gallery=false;selected=null}){Icon(Icons.Outlined.ArrowBack,null);Text("Camera")}
  if(gallery&&status.isNotBlank())Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically){Text(status,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);if(!saving)IconButton(onClick={status=""}){Icon(Icons.Outlined.Close,"Dismiss message")}}
  if(gallery&&(saving||loading))LinearProgressIndicator(Modifier.fillMaxWidth())
  if(gallery){
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    if(pending.isNotEmpty())item{Text("Pending captures",style=MaterialTheme.typography.titleMedium)}
    items(pending,key={it.name}){file->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(if(file.extension=="mp4")Icons.Outlined.Videocam else Icons.Outlined.CameraAlt,null);Column(Modifier.weight(1f).padding(12.dp)){Text(if(file.extension=="mp4")"Video awaiting save"else"Photo awaiting save");Text("${file.length()/1024} KB",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={save(file)},enabled=!saving){Text("Save")};IconButton(onClick={discard=file},enabled=!saving){Icon(Icons.Outlined.DeleteOutline,"Discard pending capture")}}}
    item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your captures",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={refresh()},enabled=!loading){Icon(Icons.Outlined.Refresh,"Refresh captures")}}}
    if(rows.isEmpty()&&pending.isEmpty()&&!loading)item{Text("No photos or videos yet",Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
    items(rows,key={it.optString("id")}){row->Row(Modifier.fillMaxWidth().clickable{selected=row}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){CameraThumbnail(row,Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)));Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(row.optString("name"),style=MaterialTheme.typography.bodyMedium,maxLines=2);Text(if(row.optString("mime").startsWith("video/"))"Video"else"Photo",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Outlined.ChevronRight,null)};HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))}
    if(rows.size<total)item{TextButton(onClick={loading=true;scope.launch{try{val result=model.cameraAction("list",JSONObject().put("offset",rows.size));rows=rows+result.getJSONArray("items").let{a->(0 until a.length()).map{a.getJSONObject(it)}};total=result.optInt("total")}catch(e:Exception){message(e)}finally{loading=false}}},enabled=!loading){Text("More captures")}}
   }
  }else{
   BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().semantics{contentDescription="Camera preview stage"},contentAlignment=Alignment.Center){
   val frame=cameraFrameSize(maxWidth.value,maxHeight.value,cameraFrameAspect(if(videoMode)"16:9"else ratio,landscape))
   Box(Modifier.size(frame.first.dp,frame.second.dp).onSizeChanged{previewSize=it}.clip(RoundedCornerShape(4.dp)).background(Color.Black).semantics{contentDescription="Camera capture preview";stateDescription="${if(videoMode)"16:9"else ratio} · ${if(landscape)"Landscape"else"Portrait"}"},contentAlignment=Alignment.Center){
    if(permission&&enableHardware)AndroidView(factory={preview.apply{val scale=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(detector:ScaleGestureDetector):Boolean{native.zoom(zoom*detector.scaleFactor);return true}});var pinched=false;setOnTouchListener{v,event->if(event.actionMasked==MotionEvent.ACTION_DOWN)pinched=false;if(event.pointerCount>1)pinched=true;scale.onTouchEvent(event);if(event.actionMasked==MotionEvent.ACTION_UP&&!pinched){native.focus(this,event.x,event.y);focusPoint=Offset(event.x,event.y);v.performClick()};true}}},modifier=Modifier.fillMaxSize())
    focusPoint?.let{point->Canvas(Modifier.fillMaxSize()){drawCircle(Color.White,26.dp.toPx(),point,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))}}
    if(grid&&permission)Canvas(Modifier.fillMaxSize()){val color=Color.White.copy(alpha=.4f);for(i in 1..2){drawLine(color,Offset(size.width*i/3,0f),Offset(size.width*i/3,size.height),1.dp.toPx());drawLine(color,Offset(0f,size.height*i/3),Offset(size.width,size.height*i/3),1.dp.toPx())}}
    if(!permission)Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){Icon(Icons.Outlined.CameraAlt,null,Modifier.size(56.dp),tint=Color.White);Button(onClick={camPermission.launch(Manifest.permission.CAMERA)}){Text("Allow camera")};TextButton(onClick={c.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}){Text("Android app settings")}}
    if(native.message.isNotBlank())Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(native.message,color=Color.White);Button(onClick={restart++}){Text("Retry camera")}}
    if(count>0){Text("$count",style=MaterialTheme.typography.displayLarge,color=Color.White);TextButton(onClick={timerJob?.cancel();count=0},modifier=Modifier.align(Alignment.Center).offset(y=64.dp)){Text("Cancel countdown",color=Color.White)}}
    if(native.recording)Surface(Modifier.align(Alignment.TopCenter).padding(12.dp),color=Color.Black.copy(alpha=.7f),shape=RoundedCornerShape(12.dp)){Text("${if(native.paused)"Paused"else"●"} ${elapsedLabel(native.duration)} · ${native.bytes/1048576} MB",Modifier.padding(10.dp),color=Color.White)}
   }
   CompositionLocalProvider(LocalContentColor provides Color.White){
    if(landscape||toolbar==null)Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha=.6f),Color.Transparent))).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
     if(landscape)app.onklav.shared.ClosePageButton(description="Close camera",onClick=onClose);Spacer(Modifier.weight(1f));CameraHeaderButtons(actions)
    }
    if(status.isNotBlank()||saving)Surface(Modifier.align(Alignment.TopCenter).padding(top=if(landscape||toolbar==null)60.dp else 8.dp,start=12.dp,end=12.dp),color=Color.Black.copy(alpha=.65f),contentColor=Color.White,shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){Text(status,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);if(!saving)IconButton(onClick={status=""}){Icon(Icons.Outlined.Close,"Dismiss message")}}}
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.78f)))).then(if(landscape)Modifier.safeDrawingPadding()else Modifier).padding(horizontal=16.dp,vertical=8.dp).semantics{contentDescription=if(landscape)"Landscape camera controls"else"Portrait camera controls"}){
     if(landscape)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){zoomControl(Modifier.weight(1f));captures();modes();capture();switchCamera()}
     else Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){zoomControl(Modifier.fillMaxWidth());modes();Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){captures();capture();switchCamera()}}
    }
   }
   }
  }
 }
 if(settings)EssentialDialog("Camera settings",{settings=false}){
  Text("Photo size",style=MaterialTheme.typography.titleSmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("4:3","16:9","1:1").forEach{r->FilterChip(selected=ratio==r,onClick={ratio=r;p.edit().putString("ratio",r).apply()},label={Text(r)})}}
  Row(verticalAlignment=Alignment.CenterVertically){Text("High-quality photos",Modifier.weight(1f));Switch(high,{high=it;p.edit().putBoolean("high",it).apply()})};Row(verticalAlignment=Alignment.CenterVertically){Text("Mirror selfies",Modifier.weight(1f));Switch(mirror,{mirror=it;p.edit().putBoolean("mirror",it).apply()})};Row(verticalAlignment=Alignment.CenterVertically){Text("Grid",Modifier.weight(1f));Switch(grid,{grid=it;p.edit().putBoolean("grid",it).apply()})}
  Row(verticalAlignment=Alignment.CenterVertically){Text("Shutter sound",Modifier.weight(1f));Switch(shutterSound||cameraRequiresShutterSound(),{shutterSound=it;p.edit().putBoolean("shutter_sound",it).apply()},enabled=!cameraRequiresShutterSound(),modifier=Modifier.semantics{contentDescription="Shutter sound"})}
  Text("Video quality",style=MaterialTheme.typography.titleSmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("480p" to Quality.SD,"720p" to Quality.HD,"1080p" to Quality.FHD,"4K" to Quality.UHD).filter{it.second in native.qualities}.forEach{(name,_)->FilterChip(selected=qualityName==name,onClick={qualityName=name;p.edit().putString("video",name).apply()},label={Text(name)})}}
  Row(verticalAlignment=Alignment.CenterVertically){Text("Record microphone",Modifier.weight(1f));Switch(audio,{enable->if(enable){if(!essentialAccess(c,CAMERA_APP,"audio.record"))status="Microphone access is off in Settings > Apps > Camera"else if(ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)micPermission.launch(Manifest.permission.RECORD_AUDIO)else{audio=true;p.edit().putBoolean("audio",true).apply()}}else{audio=false;p.edit().putBoolean("audio",false).apply()}})}
  TextButton(onClick={c.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}){Text("Android app settings")}
  val e=native.camera?.cameraInfo?.exposureState;if(e!=null&&e.isExposureCompensationSupported&&e.exposureCompensationRange.upper>e.exposureCompensationRange.lower){Text("Exposure ${"%+.1f".format(exposure*e.exposureCompensationStep.toFloat())} EV",style=MaterialTheme.typography.titleSmall);Slider(exposure.toFloat(),{exposure=it.toInt();native.exposure(exposure)},valueRange=e.exposureCompensationRange.lower.toFloat()..e.exposureCompensationRange.upper.toFloat(),steps=(e.exposureCompensationRange.upper-e.exposureCompensationRange.lower-1).coerceAtLeast(0));TextButton(onClick={exposure=0;native.exposure(0)}){Text("Reset exposure")}}
 }
 selected?.let{row->EssentialDialog(row.optString("name"),{selected=null}){
  CameraThumbnail(row,Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)))
  FilledTonalButton(onClick={scope.launch{try{val item=model.cameraAction("resolve",JSONObject().put("id",row.getString("id")));if(item.optString("mime").startsWith("video/"))check(essentialAccess(c,CAMERA_APP,"audio.playback")){"Playback access is off"};media=MessagePart(Uri.fromFile(File(item.getString("path"))).toString(),item.getString("mime"),item.getString("name"));selected=null}catch(e:Exception){message(e)}}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.PlayArrow,null);Text(if(row.optString("mime").startsWith("video/"))"Play video"else"View photo")}
  TextButton(onClick={loading=true;scope.launch{try{check(essentialAccess(c,CAMERA_APP,"files.export")){"Sharing access is off"};val item=model.cameraAction("resolve",JSONObject().put("id",row.getString("id")));val uri=withContext(Dispatchers.IO){FilesOpenProvider.stage(c,item){check(essentialAccess(c,CAMERA_APP,"files.export","files.manage","camera.capture"))}};c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(item.getString("mime")).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri("Camera capture",uri)},"Share capture"))}catch(e:Exception){message(e)}finally{loading=false}}},enabled=!loading){Icon(Icons.Outlined.Share,null);Text("Share")};TextButton(onClick={delete=row}){Icon(Icons.Outlined.DeleteOutline,null);Text("Move to Trash")}
 }}
 if(delete!=null)AlertDialog(onDismissRequest={delete=null},title={Text("Move capture to Trash?")},text={Text("You can restore it in Files.")},confirmButton={TextButton(onClick={val row=delete!!;delete=null;scope.launch{try{model.cameraAction("trash",JSONObject().put("id",row.getString("id")));selected=null;refresh()}catch(e:Exception){message(e)}}}){Text("Move to Trash")}},dismissButton={TextButton(onClick={delete=null}){Text("Cancel")}})
 if(discard!=null)AlertDialog(onDismissRequest={discard=null},title={Text("Discard unsaved capture?")},confirmButton={TextButton(onClick={discard?.delete();discard=null;pending=runCatching{CameraDisk.pending(c)}.getOrElse{status="Camera storage is unavailable";pending}}){Text("Discard")}},dismissButton={TextButton(onClick={discard=null}){Text("Keep")}})
 media?.let{part->ConversationMediaViewer(listOf(ChatMedia(Message(0,"",0,false,parts=listOf(part)),part)),part.uri,JSONObject(),{_,_->},{media=null})}
}
@Composable private fun CameraThumbnail(row:JSONObject,modifier:Modifier){
 var bitmap by remember(row.optString("id")){mutableStateOf<android.graphics.Bitmap?>(null)}
 LaunchedEffect(row.optString("id")){bitmap=withContext(Dispatchers.IO){runCatching{val file=File(row.getString("path"));if(row.optString("mime").startsWith("video/"))android.media.ThumbnailUtils.createVideoThumbnail(file,android.util.Size(400,300),null)else android.media.ThumbnailUtils.createImageThumbnail(file,android.util.Size(400,300),null)}.getOrNull()}}
 Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),contentAlignment=Alignment.Center){bitmap?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}?:Icon(if(row.optString("mime").startsWith("video/"))Icons.Outlined.Videocam else Icons.Outlined.CameraAlt,null)}
}
