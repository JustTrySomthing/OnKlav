package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.onklav.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Separate package and source set: no diagnostic launcher or grants in normal builds. */
class DeviceChecksActivity:ComponentActivity(){
 private lateinit var checks:DeviceChecks
 private val camera=registerForActivityResult(SdkCameraContract()){ok->checks.cameraResult(ok)}
 private val microphone=registerForActivityResult(ActivityResultContracts.RequestPermission()){ok->if(ok)checks.record()else checks.mark("Audio","Permission denied")}
 private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){ok->if(ok)checks.notice()else checks.mark("Notification","Permission denied")}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  checks=ViewModelProvider(this)[DeviceChecks::class.java]
  androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
  setContent{OnKlavTheme{
   var preview by remember{mutableStateOf(false)}
   BackHandler(preview){preview=false}
   if(preview)SdkAppScreen("org.example.nativebridge.checks2",onFeedback={checks.feedback(it)},exit={preview=false})
   else Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("OnKlav Diagnostics",style=MaterialTheme.typography.headlineSmall)
    Text("This separate test app keeps your regular OnKlav install intact. Checks run only when you tap them. No computer or USB debugging is needed.")
    Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    Text("Start checks installs one signed sample app here and grants it camera, notification and secure-storage access. Android asks separately for microphone and notification permission. No SMS or contacts permission is needed.")
    Button(enabled=!checks.busy,onClick={checks.start()}){Text(if(checks.ready)"Restart checks"else "Start checks")}
    for((name,result)in checks.results)Text("$name: $result")
    if(checks.busy)CircularProgressIndicator(Modifier.size(24.dp))
    val enabled=checks.ready&&!checks.busy
    Button(enabled=enabled,onClick={checks.beginCamera{uri->camera.launch(uri)}}){Text("1. Take test photo")}
    Text("Take an ordinary test photo and accept it. The report includes only the result, not the photo.")
    Button(enabled=enabled,onClick={if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this@DeviceChecksActivity,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notifications.launch(Manifest.permission.POST_NOTIFICATIONS)else checks.notice()}){Text("2. Send test notification")}
    Text("Pull down the notification shade. Confirm only if you see “Native bridge”.")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     OutlinedButton(enabled=checks.noticeSent,onClick={checks.mark("Notification visibility","Confirmed by user")}){Text("I saw it")}
     OutlinedButton(enabled=checks.noticeSent,onClick={checks.mark("Notification visibility","Not seen by user")}){Text("Not seen")}
    }
    Button(enabled=enabled&&!checks.recording,onClick={if(ContextCompat.checkSelfPermission(this@DeviceChecksActivity,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)microphone.launch(Manifest.permission.RECORD_AUDIO)else checks.record()}){Text("3. Record test audio")}
    Text("Say a few words, then stop and play. Recording stops at 12 seconds. Audio stays private and is discarded when you leave.")
    Button(enabled=checks.recording,onClick={checks.finishRecording()}){Text("Stop recording")}
    Button(enabled=enabled&&checks.hasAudio,onClick={checks.play()}){Text("Play recording")}
    OutlinedButton(enabled=checks.playCompleted,onClick={checks.mark("Audio audibility","Confirmed by user")}){Text("I heard my recording")}
    Button(enabled=enabled,onClick={checks.secureStorage()}){Text("4. Test secure storage")}
    Text("Writes, reads and removes a test value using the real Android Keystore. The report never includes the value.")
    Button(enabled=enabled,onClick={preview=true}){Text("5. Open interactive app preview")}
    Text("The sample uses OnKlav’s app UI and native bridge. Try Camera and Notify, then Store secret followed by Read secret. Tap Store secret, then Read secret. Look for “Test secret saved. Tap Read secret next.”, followed by “Secure storage preview passed.” below the buttons. Confirm only after the storage result says it passed. Return here with Back. Only service names and generic status are reported.")
    OutlinedButton(enabled=enabled,onClick={checks.mark("Interactive preview","User confirmed sample interaction")}){Text("Preview worked")}
    Button(enabled=!checks.busy,onClick={runCatching{
     val uri=ExportProvider.stage(this@DeviceChecksActivity,"OnKlav-device-checks.txt",checks.report().toByteArray())
     startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri("Diagnostic report",uri)},"Share diagnostic report"))
    }.onFailure{checks.mark("Report sharing","Could not open sharing; try again")}}){Text("Share report")}
    Text("Save or share the text report, then attach it in this chat. Two-phone carrier delivery, receipts, reconnection and removal still need a separate test on two phones. Accessibility and performance are not automatically certified by this check.")
   }}
  }}
 }
 override fun onStop(){if(::checks.isInitialized)checks.stopAudio();super.onStop()}
}

class DeviceChecks(app:Application):AndroidViewModel(app){
 private val c get()=getApplication<Application>()
 var results by mutableStateOf(mapOf("Native bridge" to "Not tested","Camera" to "Not tested","Notification" to "Not tested","Audio" to "Not tested","Secure storage" to "Not tested","Interactive preview" to "Not tested"));private set
 var ready by mutableStateOf(false);private set
 var busy by mutableStateOf(false);private set
 var recording by mutableStateOf(false);private set
 var hasAudio by mutableStateOf(false);private set
 var playCompleted by mutableStateOf(false);private set
 var noticeSent by mutableStateOf(false);private set
 private var session:String?=null
 private var cameraPending:JSONObject?=null
 private var audio:VoiceAudio?=null
 private val backend=AndroidVoiceBackend(app)
 fun mark(name:String,value:String){results=results+(name to value)}
 private fun job(name:String,work:suspend()->Unit){if(busy)return;busy=true;viewModelScope.launch{try{work()}catch(e:CancellationException){throw e}catch(e:Exception){mark(name,"Failed (${e.javaClass.simpleName}); retry or report this result")}finally{busy=false}}}
 private suspend fun call(request:JSONObject)=AppSdk.call(c,request)as JSONObject
 private suspend fun pending(action:String)=call(JSONObject().put("action","event").put("session",requireNotNull(session)).put("event",JSONObject().put("action",action))).getJSONObject("pending")
 private fun authorize(p:JSONObject)=AppSdk.sync(c,JSONObject().put("action","authorize").put("session",requireNotNull(session)).put("token",p.getString("token")))as JSONObject
 private suspend fun reply(p:JSONObject,value:JSONObject)=call(JSONObject().put("action","reply").put("session",requireNotNull(session)).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",value)))
 private suspend fun failed(p:JSONObject){runCatching{call(JSONObject().put("action","reply").put("session",requireNotNull(session)).put("token",p.getString("token")).put("reply",JSONObject().put("ok",false).put("error","Device check did not complete")))}}
 fun start()=job("Native bridge"){
  ready=false;stopAudio();cameraPending?.let{SdkCamera.cleanup(c,it.getString("token"))};cameraPending=null
  session?.let{AppSdk.releaseSession(c,it)};session=null
  AppSdk.staging.withLock{
   val bytes=c.assets.open("diagnostics/NativeServices.onklav-a").use{it.readBytes()}
   val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
   val installed=AppSdk.list(c).any{it.optBoolean("installed")&&it.optString("hash")==hash}
   val grants=setOf("camera.capture","notifications.post","storage.secrets")
   if(!installed){bytes.inputStream().use{AppSdk.stage(c,it)};val review=AppSdk.inspect(c);AppSdk.install(c,review,grants)}
   else AppSdk.call(c,JSONObject().put("action","permissions").put("id","org.example.nativebridge.checks2").put("grants",JSONArray(grants.toList())))
  }
  File(c.filesDir,"app-install.zip").delete()
  session=call(JSONObject().put("action","launch").put("id","org.example.nativebridge.checks2")).getString("session")
  ready=true;mark("Native bridge","Passed: signed app installed and launched through ARM64 JNI")
 }
 fun beginCamera(launch:(android.net.Uri)->Unit)=job("Camera"){
  val p=pending("camera");cameraPending=p
  try{val uri=SdkCamera.prepare(c,p.getString("token")){authorize(p)};mark("Camera","Waiting for photo");launch(uri)}catch(e:Exception){SdkCamera.cleanup(c,p.getString("token"));cameraPending=null;failed(p);throw e}
 }
 fun cameraResult(ok:Boolean)=job("Camera"){
  val p=cameraPending?:return@job;cameraPending=null
  try{
   if(!ok){failed(p);mark("Camera","Cancelled");return@job}
   withContext(Dispatchers.IO){val bytes=SdkCamera.read(c,p.getString("token")){authorize(p)};val value=SdkCamera.commit(c,bytes,{authorize(p)}){AppSdk.sync(c,JSONObject().put("action","camera_host").put("session",requireNotNull(session)).put("token",p.getString("token")))as JSONObject};reply(p,value)}
   mark("Camera","Passed: captured, normalized and committed through native bridge")
  }catch(e:Exception){failed(p);throw e}finally{SdkCamera.cleanup(c,p.getString("token"))}
 }
 fun notice()=job("Notification"){
  noticeSent=false;mark("Notification visibility","Not confirmed")
  val p=pending("notice")
  try{val value=withContext(Dispatchers.IO){SdkNotifications.execute(c,authorize(p)){authorize(p)}};reply(p,value);noticeSent=true;mark("Notification","Submitted to Android; confirm visibility below")}
  catch(e:Exception){failed(p);throw e}
 }
 fun secureStorage()=job("Secure storage"){
  withContext(Dispatchers.IO){
   var p:JSONObject?=null
   try{
    p=pending("store");val stored=SdkSecrets.execute(c){authorize(p!!)};check(stored.getBoolean("stored"));reply(p,stored)
    p=pending("secret");val read=SdkSecrets.execute(c){authorize(p!!)};check(read.getString("value")=="private fixture");reply(p,read)
    SdkSecrets.deleteApp(c,"org.example.nativebridge.checks2")
    p=pending("secret");val missing=SdkSecrets.execute(c){authorize(p!!)};check(missing.isNull("value"));reply(p,missing)
   }catch(e:Exception){p?.let{failed(it)};throw e}finally{SdkSecrets.deleteApp(c,"org.example.nativebridge.checks2")}
  };mark("Secure storage","Passed: Android Keystore write/read/cleanup; deleted value absent")
 }
 fun record(){try{stopAudio();audio=null;hasAudio=false;playCompleted=false;mark("Audio audibility","Not confirmed");backend.record({ContextCompat.checkSelfPermission(c,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED},{finishRecording()},{mark("Audio","Recording failed");stopAudio()});recording=true;mark("Audio","Recording; say a few words") }catch(e:Exception){mark("Audio","Microphone could not start")}}
 fun finishRecording(){if(!recording)return;recording=false;try{audio=backend.finish();hasAudio=true;mark("Audio","Recorded; tap Play recording")}catch(e:Exception){backend.cancelRecord();mark("Audio","Recording did not finish; try a longer sample")}}
 fun play(){val sample=audio?:return;playCompleted=false;try{backend.play(sample,{true}){error->if(error==null){playCompleted=true;mark("Audio","Playback completed; confirm whether heard")}else mark("Audio","Playback failed")}}catch(e:Exception){mark("Audio","Playback could not start")}}
 fun stopAudio(){if(recording)mark("Audio","Recording interrupted; try again");recording=false;backend.close();audio?.bytes?.fill(0);audio=null;hasAudio=false}
 fun feedback(row:JSONObject){val phase=row.optString("phase").takeIf{it in setOf("opened","action_error","waiting_for_device")}?:"active";mark("Preview service feedback",phase+row.optString("service").takeIf{it!="null"&&it.matches(Regex("[a-z.]{1,64}"))}.let{if(it==null)""else " · $it"})}
 fun report():String=JSONObject().put("report","OnKlav Android device checks 2").put("created",java.time.Instant.now().toString()).put("device",Build.MANUFACTURER+" "+Build.MODEL).put("android",Build.VERSION.RELEASE).put("api",Build.VERSION.SDK_INT).put("package",c.packageName).put("version",c.packageManager.getPackageInfo(c.packageName,0).versionName).put("results",JSONObject(results)).put("not_verified",JSONArray(listOf("Two-phone carrier delivery and receipt ownership","Carrier reconnection and app removal","TalkBack accessibility and large-screen validation","Sustained performance and security audit"))).toString(2)
 override fun onCleared(){backend.close();audio?.bytes?.fill(0);session?.let{AppSdk.releaseSession(c,it)};cameraPending?.let{SdkCamera.cleanup(c,it.getString("token"))};super.onCleared()}
}
