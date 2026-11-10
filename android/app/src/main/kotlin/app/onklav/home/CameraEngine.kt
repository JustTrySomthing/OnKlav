package app.onklav.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal object CameraDisk{
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 fun folder(c:Context):File{
  // Android may expose filesDir through /data/user/0 -> /data/data. Resolve the trusted root first.
  val dir=File(c.filesDir.canonicalFile,"camera-pending")
  check(!java.nio.file.Files.isSymbolicLink(dir.toPath())){"Camera storage is unavailable"}
  check(dir.mkdirs()||dir.isDirectory){"Camera storage could not be opened"}
  check(dir.canonicalFile==dir.absoluteFile){"Camera storage is unavailable"};return dir
 }
 fun pending(c:Context)=folder(c).listFiles().orEmpty().filter{it.isFile&&it.name.matches(Regex("[a-f0-9]{32}\\.(jpg|mp4)"))&&it.length()>0}.sortedByDescending{it.lastModified()}
 fun create(c:Context,video:Boolean):File{check(pending(c).size<12){"Save or discard pending captures first"};val dir=folder(c);check(dir.usableSpace>if(video)32*1024*1024 else 8*1024*1024){"Free some storage before capturing"};return File(dir,UUID.randomUUID().toString().replace("-","")+if(video)".mp4"else".jpg")}
 fun validate(file:File,video:Boolean){require(file.isFile&&file.length() in 1..2*1024*1024*1024L){"Capture is empty or exceeds2GiB"};if(video){val m=android.media.MediaMetadataRetriever();try{m.setDataSource(file.absolutePath);check((m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)>0){"No playable video was recorded"}}finally{m.release()}}else{val o=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(file.absolutePath,o);check(o.outWidth>0&&o.outHeight>0){"Photo could not be decoded"}}}
 suspend fun save(c:Context,file:File,model:SdkAppModel):JSONObject=AppSdk.staging.withLock{
  withContext(Dispatchers.IO){check(essentialAccess(c,CAMERA_APP,"storage.private","camera.capture","files.manage")){"Camera access is off"};require(file.canonicalFile.parentFile==folder(c).canonicalFile){"Invalid capture"};validate(file,file.extension=="mp4");val stage=File(c.filesDir,"content-import.tmp");try{file.inputStream().use{input->stage.outputStream().use{out->val buffer=ByteArray(65536);var total=0L;while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;total+=n;check(total<=2*1024*1024*1024L);if(total%1048576<65536)check(essentialAccess(c,CAMERA_APP,"files.manage","camera.capture")){"Camera access was turned off"};out.write(buffer,0,n)}}};val date=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));model.cameraAction("save",JSONObject().put("name","${if(file.extension=="mp4")"VID"else"IMG"}_$date.${file.extension}").put("mime",if(file.extension=="mp4")"video/mp4"else"image/jpeg")).also{file.delete()}}finally{stage.delete()}}
 }
}
/** One lifecycle-bound CameraX session, with explicit rebinds and no automatic error retry loop. */
internal class NativeCamera(private val c:Context,private val providerFuture:()->com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> = {ProcessCameraProvider.getInstance(c)}){
 var camera by mutableStateOf<Camera?>(null);private set
 var ready by mutableStateOf(false);private set
 var message by mutableStateOf("");private set
 var recording by mutableStateOf(false);private set
 var paused by mutableStateOf(false);private set
 var duration by mutableLongStateOf(0);private set
 var bytes by mutableLongStateOf(0);private set
 var working by mutableStateOf(false);private set
 var qualities by mutableStateOf(listOf(Quality.SD,Quality.HD,Quality.FHD));private set
 var actualQuality by mutableStateOf(Quality.FHD);private set
 var hasFront by mutableStateOf(false);private set
 var hasBack by mutableStateOf(false);private set
 private var provider:ProcessCameraProvider?=null;private var image:ImageCapture?=null;private var video:VideoCapture<Recorder>?=null;private var active:Recording?=null;private var generation=0
 private val executor=ContextCompat.getMainExecutor(c)
 private fun access()=essentialAccess(c,CAMERA_APP,"storage.private","camera.capture","files.manage")&&ContextCompat.checkSelfPermission(c,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
 fun bind(owner:LifecycleOwner,view:PreviewView,front:Boolean,videoMode:Boolean,quality:Quality,ratio:String,high:Boolean,flash:Int,rotation:Int=Surface.ROTATION_0){
  if(recording||working)return;val serial=++generation;ready=false;message=""
  val f=try{TorchControl.stop(c);SdkTorch.clear();providerFuture()}catch(e:Exception){message=e.message?:"Camera could not start. Tap Retry.";return}
  f.addListener({if(serial!=generation)return@addListener;runCatching{check(access()){ "Camera access is off" };val p=f.get();provider=p;p.unbindAll();hasFront=p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);hasBack=p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);val selector=if(front)CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA;val info=p.availableCameraInfos.firstOrNull{CameraSelector.Builder().requireLensFacing(if(front)CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK).build().filter(listOf(it)).isNotEmpty()}?:error("This camera is unavailable");qualities=Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR);val chosen=quality.takeIf{it in qualities}?:qualities.firstOrNull()?:Quality.SD;actualQuality=chosen
   val preview=Preview.Builder().setTargetRotation(rotation).build().also{it.surfaceProvider=view.surfaceProvider};val group=UseCaseGroup.Builder().addUseCase(preview)
   image=null;video=null
   if(videoMode){val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(chosen,FallbackStrategy.lowerQualityOrHigherThan(chosen))).build();video=VideoCapture.withOutput(recorder).also{it.targetRotation=rotation;group.addUseCase(it)}}else{image=ImageCapture.Builder().setCaptureMode(if(high)ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(if(high)95 else 85).setTargetRotation(rotation).setFlashMode(flash).build().also{group.addUseCase(it)}}
   val aspect=cameraFrameAspect(ratio,view.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
   val viewport=view.viewPort?:ViewPort.Builder(android.util.Rational((aspect*10000).toInt(),10000),view.display?.rotation?:Surface.ROTATION_0).setScaleType(ViewPort.FILL_CENTER).build()
   group.setViewPort(viewport);camera=p.bindToLifecycle(owner,selector,group.build());ready=true
  }.onFailure{message=it.cause?.message?:it.message?:"Camera unavailable. Close other camera apps and retry.";ready=false}},executor)
 }
 fun rotate(rotation:Int){image?.targetRotation=rotation;video?.targetRotation=rotation}
 fun flash(value:Int){image?.flashMode=value}
 fun focus(view:PreviewView,x:Float,y:Float){runCatching{val point=view.meteringPointFactory.createPoint(x,y);camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(5,java.util.concurrent.TimeUnit.SECONDS).build())}}
 fun zoom(value:Float){camera?.cameraInfo?.zoomState?.value?.let{camera?.cameraControl?.setZoomRatio(value.coerceIn(it.minZoomRatio,it.maxZoomRatio))}}
 fun exposure(value:Int){camera?.cameraControl?.setExposureCompensationIndex(value)}
 fun light(on:Boolean){camera?.cameraControl?.enableTorch(on)}
 fun photo(mirror:Boolean,done:(File?,String)->Unit,onShutter:()->Unit={}){if(!ready||working||recording)return;val capture=image?:return;working=true;runCatching{check(access());val file=CameraDisk.create(c,false);val opts=ImageCapture.OutputFileOptions.Builder(file).setMetadata(ImageCapture.Metadata().apply{isReversedHorizontal=mirror}).build();capture.takePicture(opts,executor,object:ImageCapture.OnImageSavedCallback{override fun onCaptureStarted(){runCatching{onShutter()}};override fun onImageSaved(result:ImageCapture.OutputFileResults){working=false;done(file,"")};override fun onError(e:ImageCaptureException){working=false;file.delete();done(null,e.message?:"Photo failed")}})}.onFailure{working=false;done(null,it.message?:"Photo failed")}}
 @SuppressLint("MissingPermission") fun record(audio:Boolean,done:(File?,String)->Unit){if(!ready||working||recording)return;runCatching{check(access());if(audio)check(essentialAccess(c,CAMERA_APP,"audio.record")&&ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){"Microphone access is off"};val capture=video?:error("Video is not ready");val file=CameraDisk.create(c,true);val opts=FileOutputOptions.Builder(file).setFileSizeLimit((CameraDisk.folder(c).usableSpace-16*1024*1024L).coerceAtMost(2*1024*1024*1024L)).build();var pending=capture.output.prepareRecording(c,opts);if(audio)pending=pending.withAudioEnabled();duration=0;bytes=0;working=true
   active=pending.start(executor){event->when(event){is VideoRecordEvent.Start->{working=false;recording=true};is VideoRecordEvent.Status->{duration=event.recordingStats.recordedDurationNanos/1000000;bytes=event.recordingStats.numBytesRecorded};is VideoRecordEvent.Pause->{paused=true};is VideoRecordEvent.Resume->{paused=false};is VideoRecordEvent.Finalize->{working=false;recording=false;paused=false;active=null;val acceptable=!event.hasError()||event.error in setOf(VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED);if(acceptable&&file.length()>0)done(file,if(event.hasError())"Recording ended; captured video saved"else"")else{file.delete();done(null,"Recording failed: ${event.cause?.message?:event.error}")}}}}
  }.onFailure{working=false;done(null,it.message?:"Could not record video")}}
 fun pause(){if(paused)active?.resume()else active?.pause()}
 fun stop(){active?.stop();working=active!=null}
 fun close(){generation++;runCatching{active?.stop()};runCatching{camera?.cameraControl?.enableTorch(false)};runCatching{provider?.unbindAll()};ready=false;camera=null}
}

internal fun cameraFrameAspect(ratio:String,landscape:Boolean):Float {val value=when(ratio){"16:9"->16f/9;"1:1"->1f;else->4f/3};return if(landscape)value else 1f/value}
internal fun cameraFrameSize(width:Float,height:Float,aspect:Float):Pair<Float,Float>{val w=minOf(width,height*aspect).coerceAtLeast(0f);return w to w/aspect}
internal class CameraShutterSound:AutoCloseable{private val sound=runCatching{android.media.MediaActionSound().also{it.load(android.media.MediaActionSound.SHUTTER_CLICK)}}.getOrNull();fun play(){runCatching{sound?.play(android.media.MediaActionSound.SHUTTER_CLICK)}};override fun close(){runCatching{sound?.release()}}}

internal fun cameraRequiresShutterSound():Boolean=android.os.Build.VERSION.SDK_INT>=33&&runCatching{android.media.MediaActionSound.mustPlayShutterSound()}.getOrDefault(false)
