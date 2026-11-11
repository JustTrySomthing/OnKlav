package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** One preparation path for camera, recorder and picked files in Messages and Groups. */
@Composable internal fun MessageAttachments(open:Boolean,close:()->Unit,budget:()->Int,ready:(JSONObject)->Unit,linkReady:(String)->Boolean={false}){
 val c=LocalContext.current;val scope=rememberCoroutineScope()
 var cloudShare by remember{mutableStateOf(false)}
 val currentBudget by rememberUpdatedState(budget);val onReady by rememberUpdatedState(ready)
 var selection by remember{mutableStateOf<CarrierMedia.Selection?>(null)}
 var range by remember{mutableStateOf(0f..1f)}
 var job by remember{mutableStateOf<Job?>(null)};var busy by remember{mutableStateOf(false)}
 var progress by remember{mutableFloatStateOf(0f)};var status by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
 var captured by rememberSaveable{mutableStateOf<String?>(null)};var captureType by rememberSaveable{mutableStateOf("image/jpeg")}
 val owned=remember{mutableSetOf<File>()}
 var recorder by remember{mutableStateOf<MediaRecorder?>(null)};var recorded by remember{mutableStateOf<Uri?>(null)};var seconds by remember{mutableIntStateOf(0)}
 val remaining=remember{ArrayDeque<Uri>()}
 fun cancel(){job?.cancel();remaining.clear();selection=null}
 suspend fun addPrepared(work:suspend ()->JSONObject){
  var part:JSONObject?=null
  var attached=false
  try{
   withContext(Dispatchers.IO){
    withTimeoutOrNull(180000){part=work();true}
     ?: error("Preparing this attachment took too long. Try a smaller file or shorter clip.")
   }
   val readyPart=requireNotNull(part)
   require(readyPart.getInt("bytes")<=currentBudget()){"The draft changed. Remove an attachment or choose a shorter clip"}
   onReady(readyPart);attached=true;selection=null
  }finally{
   if(!attached)part?.let{MessageTransferProvider.file(c,it.getString("file")).delete()}
  }
 }
 suspend fun encode(s:CarrierMedia.Selection,start:Long,end:Long) {
  val limit=minOf(s.budget,currentBudget())
  addPrepared{
   CarrierMedia.prepare(c,s.copy(budget=limit),start,end){value,label->progress=value;status=label}
  }
 }
 fun trim(s:CarrierMedia.Selection) {
  val limit=CarrierMedia.recommended(minOf(s.budget,currentBudget()),s.info.video,s.info.audio)
  require(limit>=500){"This message is full. Remove an attachment or shorten its text"}
  selection=s;range=0f..minOf(s.info.duration,limit).toFloat()
 }
 suspend fun process(uri:Uri,limit:Int){
  status="Checking attachment…";progress=0f
  var candidate:CarrierMedia.Selection?=null
  try{
   require(limit>0){"This message is full. Remove an attachment or shorten its text"}
   val (name,mime,bytes)=withContext(Dispatchers.IO){
    val name=MessageMedia.name(c,uri)
    val reported=Mp3Codec.mime(c.contentResolver.getType(uri).orEmpty().substringBefore(';').lowercase(),name)
    val mime=if(MessageMedia.isVCard(reported,name))MessageMedia.VCARD_MIME else reported
    val bytes=if(mime.startsWith("audio/")||mime.startsWith("video/"))
     runCatching{c.contentResolver.openAssetFileDescriptor(uri,"r")?.use{it.length}}.getOrNull()?:-1L
    else -1L
    Triple(name,mime,bytes)
   }
   if(mime.startsWith("audio/")||mime.startsWith("video/")){
    val s=withContext(Dispatchers.IO){CarrierMedia.Selection(uri,CarrierMedia.inspect(c,uri),limit,name)}
    candidate=s
    if(bytes in 1..limit.toLong() && mime in setOf("video/mp4","video/3gpp","audio/mp4","audio/mpeg","audio/amr","audio/3gpp","audio/ogg")){
     addPrepared{MessageMedia.import(c,uri,limit)}
    }else if(s.info.duration<=CarrierMedia.recommended(limit,s.info.video,s.info.audio)){
     encode(s,0,s.info.duration)
    }else trim(s)
   }else addPrepared{MessageMedia.import(c,uri,limit)}
  }catch(e:CancellationException){throw e}
  catch(e:Exception){
   error=e.message?:"Could not open this attachment"
   candidate?.let{runCatching{trim(it)}}
   if(selection==null)remaining.clear()
  }
 }
 suspend fun drainAttachments(){
  while(remaining.isNotEmpty() && selection==null && error==null){
   val limit=CarrierMedia.batchBudget(currentBudget(),remaining.size)
   process(remaining.removeFirst(),limit)
  }
 }
 fun startPreparation(work:suspend ()->Unit){
  if(job?.isActive==true)return
  job=scope.launch{
   busy=true;error=null;progress=0f;status="Preparing…"
   try{work()}catch(e:CancellationException){throw e}
   catch(e:Exception){error=e.message?:"Could not prepare this clip"}
   finally{busy=false;status=""}
  }
 }
 fun prepare(s:CarrierMedia.Selection,start:Long,end:Long)=startPreparation{
  encode(s,start,end)
  drainAttachments()
 }
 fun import(uri:Uri)=startPreparation{remaining.addLast(uri);drainAttachments()}
 fun finishRecording(keep:Boolean){val r=recorder?:return;recorder=null;val uri=recorded;recorded=null;val stopped=runCatching{r.stop()}.isSuccess;r.release();if(keep&&stopped&&uri!=null)import(uri)else if(keep)error="The recording was too short. Try again"}
 fun record(){try{
  val uri=MessageTransferProvider.create(c,"audio/mp4");val file=MessageTransferProvider.file(c,uri.lastPathSegment!!.substringBefore('.'));owned+=file
  val r=MediaRecorder();try{r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioSamplingRate(44100);r.setAudioChannels(1);r.setAudioEncodingBitRate(CarrierMedia.AUDIO_RATE);r.setOutputFile(file.path);r.setMaxDuration(CarrierMedia.recommended(currentBudget(),false,true).coerceIn(500,180000).toInt());r.prepare();r.start();recorded=uri;recorder=r;seconds=0;r.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)finishRecording(true)}}catch(e:Exception){r.release();throw e}
 }catch(e:Exception){error=e.message?:"Microphone could not be opened"}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)record()else error="Allow microphone access to record audio"}
 val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){uris->
  if(uris.size>3)error="Choose up to three files at a time"else if(uris.isNotEmpty()){
   // Import serially so each file receives the actual remaining message budget.
   startPreparation {remaining.addAll(uris);drainAttachments()}
  }
 }
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->val output=captured;captured=null;if(result.resultCode==android.app.Activity.RESULT_OK){val uri=result.data?.data?:output?.let(Uri::parse);if(uri!=null)import(uri)}}
 fun capture(video:Boolean){try{captureType=if(video)"video/mp4"else"image/jpeg";val uri=MessageTransferProvider.create(c,captureType);owned+=MessageTransferProvider.file(c,uri.lastPathSegment!!.substringBefore('.'));captured=uri.toString();val intent=Intent(if(video)MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION).apply{clipData=ClipData.newRawUri("Capture",uri);if(video){putExtra(MediaStore.EXTRA_VIDEO_QUALITY,0);putExtra(MediaStore.EXTRA_DURATION_LIMIT,CarrierMedia.recommended(currentBudget(),true,true).coerceAtLeast(1000).toInt()/1000)}};camera.launch(intent)}catch(e:Exception){error=e.message?:"No camera app is available"}}
 val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP&&recorder!=null)finishRecording(false)};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 DisposableEffect(Unit){onDispose{job?.cancel();runCatching{recorder?.stop()};recorder?.release();owned.forEach{it.delete()}}}
 LaunchedEffect(recorder){if(recorder!=null)while(isActive){delay(1000);seconds++}}
 if(open)ReferenceDialog("Attach",close,footer={DialogAction("Cancel",onClick=close)}){
  Text("MMS · ${android.text.format.Formatter.formatShortFileSize(c,currentBudget().toLong())} available",Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=13.sp)
  data class Choice(val label:String,val icon:Int,val click:()->Unit)
  listOf(Choice("Take photo",R.drawable.attach_camera){close();capture(false)},Choice("Record Video",R.drawable.attach_video){close();capture(true)},Choice("Record Audio",R.drawable.attach_mic){close();if(c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)record()else permission.launch(Manifest.permission.RECORD_AUDIO)},Choice("Image",R.drawable.attach_image){close();picker.launch(arrayOf("image/*"))},Choice("Video",R.drawable.attach_video){close();picker.launch(arrayOf("video/*"))},Choice("Audio",R.drawable.attach_audio){close();picker.launch(arrayOf("audio/*"))},Choice("Contact card (.vcf)",R.drawable.files_document){close();picker.launch(MessageMedia.vCardTypes)},Choice("Share via Cloud Files",app.onklav.shared.R.drawable.ref_app_cloud_files){close();cloudShare=true}).forEachIndexed{i,item->
   if(i==3||item.label=="Share via Cloud Files")HorizontalDivider(Modifier.padding(horizontal=20.dp,vertical=6.dp))
   TextButton(onClick=item.click,enabled=!busy&&recorder==null,modifier=Modifier.fillMaxWidth().testTag("attach-${item.label}")){Icon(painterResource(item.icon),null,Modifier.size(24.dp));Spacer(Modifier.width(14.dp));Text(item.label,Modifier.weight(1f),fontSize=17.sp)}
  }
 }
 if(busy)ReferenceDialog("Preparing attachment",{cancel()},footer={DialogAction("Cancel"){cancel()}}){Column(Modifier.padding(20.dp)){Text(status);LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().padding(top=16.dp))}}
 selection?.takeIf{!busy}?.let{s->
  val max=CarrierMedia.recommended(minOf(s.budget,currentBudget()),s.info.video,s.info.audio);val length=range.endInclusive-range.start;val fits=length>=500&&length<=max
  ReferenceDialog(if(s.info.video)"Trim video"else"Trim audio",{cancel()},footer={DialogAction("Cancel"){cancel()};DialogAction("Use clip",primary=true,enabled=fits){prepare(s,range.start.toLong(),range.endInclusive.toLong())}}){Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)){
   Text("Choose up to ${"%.1f".format(max/1000.0)} seconds for this message. Your original stays unchanged.",fontSize=15.sp)
   Box(Modifier.fillMaxWidth().height(if(s.info.video)230.dp else 220.dp)){InternalMessagePlayer(MessagePart(s.uri.toString(),if(s.info.video)"video/mp4"else"audio/mp4",s.name),true,clipStart=range.start.toInt(),clipEnd=range.endInclusive.toInt())}
   fun time(value:Float)="${value.toInt()/60000}:${(value.toInt()/1000%60).toString().padStart(2,'0')}"
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Start ${time(range.start)}");Text("End ${time(range.endInclusive)}")}
   RangeSlider(value=range,onValueChange={range=it},valueRange=0f..s.info.duration.toFloat())
   Text(if(fits)"${"%.1f".format(length/1000)} seconds · Ready to prepare"else"Shorten by ${"%.1f".format((length-max).coerceAtLeast(0f)/1000)} seconds",color=if(fits)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,fontSize=14.sp)
  }}
 }
 if(recorder!=null)ReferenceDialog("Record Audio",{finishRecording(false)},footer={DialogAction("Cancel"){finishRecording(false)};DialogAction("Use recording",primary=true){finishRecording(true)}}){Column(Modifier.padding(24.dp)){Text("●  Recording · ${seconds}s",fontSize=22.sp,color=MaterialTheme.colorScheme.primary);Text("Stops automatically when the message is full.",Modifier.padding(top=12.dp),fontSize=14.sp)}}
 if(cloudShare)androidx.compose.ui.window.Dialog(onDismissRequest={cloudShare=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  CloudWorkspace(exit={cloudShare=false},website={url->try{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){error="Could not open the provider website."}},m=androidx.lifecycle.viewmodel.compose.viewModel(key="message-cloud-share"),share=linkReady)
 }
 ErrorDialog(error){error=null}
}
