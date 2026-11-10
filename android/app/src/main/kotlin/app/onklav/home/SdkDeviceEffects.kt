package app.onklav.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/** Native operations stay bound to the live pending token through every callback. */
@Composable internal fun SdkDeviceEffects(model:SdkAppModel){
 val context=LocalContext.current;val scope=rememberCoroutineScope();val pending=model.pending
 var cameraToken by rememberSaveable{mutableStateOf<String?>(null)}
 var notificationToken by rememberSaveable{mutableStateOf<String?>(null)}
 suspend fun notify(token:String){
  val p=model.pending?.takeIf{it.optString("token")==token}?:return
  try{val result=withContext(Dispatchers.IO){val request=model.authorizeNow(p);SdkNotifications.execute(context,request){model.authorizeNow(p);Unit}};model.reply(p,result)}
  catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure=e.message?:"Notification could not be completed")}
 }
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->val token=notificationToken;notificationToken=null;if(token!=null)scope.launch{
  if(granted)notify(token)else model.pending?.takeIf{it.optString("token")==token}?.let{model.reply(it,failure="Notifications are off for OnKlav. Allow them in Android Settings before retrying.")}
 }}
 val camera=rememberLauncherForActivityResult(SdkCameraContract()){captured->val token=cameraToken;cameraToken=null;if(token!=null)scope.launch{
  try{
   val p=model.pending?.takeIf{it.optString("token")==token&&it.optString("service")=="camera.capture"}?:return@launch
   if(!captured)model.reply(p,failure="Camera capture cancelled. No image was imported.")
   else{
    val bytes=withContext(Dispatchers.IO){SdkCamera.read(context,token){model.authorizeNow(p);Unit}}
    val result=withContext(Dispatchers.IO){AppSdk.staging.withLock{SdkCamera.commit(context,bytes,{model.authorizeNow(p);Unit}){model.cameraCommitNow(p)}}}
    model.reply(p,result)
   }
  }catch(e:CancellationException){throw e}catch(e:Exception){model.pending?.takeIf{it.optString("token")==token}?.let{model.reply(it,failure=e.message?:"Camera image could not be imported")}}
  finally{SdkCamera.cleanup(context,token)}
 }}
 LaunchedEffect(pending?.optString("token")){
  val p=pending?:return@LaunchedEffect;val service=p.optString("service")
  if(service!="camera.capture"&&!service.startsWith("notifications."))return@LaunchedEffect
  if(!model.launchPending(p.getString("token")))return@LaunchedEffect
  try{
   model.authorize(p)
   when(service){
    "camera.capture"->{val token=p.getString("token");cameraToken=token;try{camera.launch(SdkCamera.prepare(context,token){model.authorizeNow(p);Unit})}catch(e:Exception){cameraToken=null;SdkCamera.cleanup(context,token);throw e}}
    "notifications.show","notifications.schedule","notifications.schedule_at"->if(Build.VERSION.SDK_INT>=33&&!SdkNotifications.allowed(context)){notificationToken=p.getString("token");permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else notify(p.getString("token"))
    "notifications.cancel","notifications.status","notifications.pending"->notify(p.getString("token"))
    else->error("Unsupported device operation")
   }
  }catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure=e.message?:"Device operation could not start")}
 }
}
