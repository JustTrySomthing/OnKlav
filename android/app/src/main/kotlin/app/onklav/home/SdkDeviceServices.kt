package app.onklav.home

import app.onklav.shared.ReferenceDialog
import app.onklav.shared.DialogAction
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal object SdkDeviceLeases {
 private val owners=ConcurrentHashMap<String,String>()
 fun register(r:JSONObject):String{val o=r.getJSONObject("owner");val session=o.getString("session");owners[session]=o.getString("id");return session}
 fun clear(session:String){runCatching{SdkTorch.release(session)};SdkBle.clear(session);SdkNearby.clear(session);owners.remove(session)}
 fun clearApp(id:String){owners.filterValues{it==id}.keys.forEach{clear(it)}}
}

internal fun deviceService(s:String)=s in setOf("calendar.localize","calendar.resolve","calendar.insert","connectivity.read","device.settings","location.current","location.manual","torch.set","ble.select","ble.connect","ble.read","ble.write","ble.notify","ble.disconnect","wifi.join","wifi.disconnect","lan.select","lan.exchange","lan.disconnect")

/** Runs only while the app is foreground. All choices use the shared centered dialog. */
@Composable internal fun SdkDeviceServices(p:JSONObject?,model:SdkAppModel){
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
 var foreground by remember{mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_STOP)foreground=false else if(e==Lifecycle.Event.ON_START)foreground=true};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer)}}
 val pending=p?.takeIf{deviceService(it.optString("service"))}
 key(pending?.optString("token")){
  val permission=remember{CompletableDeferred<Boolean>()}
  val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->permission.complete(result.values.all{it})}
  val choice=remember{CompletableDeferred<Int>()};var labels by remember{mutableStateOf<List<String>?>(null)}
  var locationDialog by remember{mutableStateOf(false)};var latitude by remember{mutableStateOf("")};var longitude by remember{mutableStateOf("")}
  val locationDecision=remember{CompletableDeferred<Boolean>()}
  DisposableEffect(pending){onDispose{labels=null;locationDialog=false}}
  LaunchedEffect(pending?.optString("token"),foreground){
   val request=pending?:return@LaunchedEffect
   if(!foreground||!model.launchPending(request.getString("token")))return@LaunchedEffect
   var owner:String?=null
   try{
    val r=model.authorizeNow(request);val service=r.getString("service");val a=r.getJSONObject("args");val leaseOwner=SdkDeviceLeases.register(r);owner=leaseOwner
    fun authorize(){check(foreground){"This service needs the app open"};model.authorizeNow(request)}
    suspend fun permissions(vararg names:String):Boolean{
     val missing=names.filter{ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED};if(missing.isEmpty())return true
     launcher.launch(missing.toTypedArray());return permission.await()
    }
    val result=when(service){
     "calendar.localize"->SdkCalendarHandoff.localize(a)
     "calendar.resolve"->SdkCalendarHandoff.resolve(a)
     "calendar.insert"->SdkCalendarHandoff.insert(context,a,::authorize)
     "connectivity.read"->{authorize();SdkConnectivity.snapshot(context)}
     "device.settings"->SdkConnectivity.settings(context,a,::authorize)
     "location.manual"->{authorize();SdkLocation.manual(a)}
     "location.current"->{locationDialog=true;val device=locationDecision.await();locationDialog=false
      if(device){permissions(Manifest.permission.ACCESS_COARSE_LOCATION);SdkLocation.current(context,::authorize)}else{authorize();SdkLocation.manual(JSONObject().put("latitude",latitude.toDouble()).put("longitude",longitude.toDouble()))}}
     "torch.set"->{check(!a.getBoolean("enabled")||permissions(Manifest.permission.CAMERA)){"Camera permission is needed for the flashlight"};SdkTorch.execute(context,r,::authorize)}
     "ble.select"->{check(if(Build.VERSION.SDK_INT>=31)permissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else permissions(Manifest.permission.ACCESS_FINE_LOCATION)){"Bluetooth permission was declined"};val choices=SdkBle.scan(context,a.getString("service"),::authorize);check(choices.isNotEmpty()){ "No matching Bluetooth devices found" };labels=choices.map{it.label};val selected=choice.await();labels=null;authorize();SdkBle.select(leaseOwner,a.getString("service"),choices[selected]);JSONObject().put("selected",true).put("timeout_seconds",120)}
     "ble.connect","ble.read","ble.write","ble.notify","ble.disconnect"->SdkBle.execute(context,r,::authorize)
     "wifi.join"->{check(if(Build.VERSION.SDK_INT>=33)permissions(Manifest.permission.NEARBY_WIFI_DEVICES)else permissions(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)){"Nearby Wi-Fi permission was declined"};SdkNearby.join(context,leaseOwner,a,::authorize)}
     "wifi.disconnect","lan.disconnect"->{authorize();SdkNearby.clear(leaseOwner);JSONObject().put("disconnected",true)}
     "lan.select"->{val choices=SdkNearby.discover(context,a.getString("type"),::authorize);check(choices.isNotEmpty()){ "No matching local services found" };labels=choices.map{it.label};val selected=choice.await();labels=null;authorize();SdkNearby.select(context,leaseOwner,choices[selected],::authorize);JSONObject().put("selected",true).put("timeout_seconds",120)}
     "lan.exchange"->SdkNearby.exchange(leaseOwner,a,::authorize)
     else->error("Unsupported foreground service")
    }
    authorize();model.reply(request,result)
   }catch(e:CancellationException){owner?.let{SdkDeviceLeases.clear(it)};withContext(NonCancellable){model.reply(request,failure="Device action cancelled when the app closed or left the foreground")};throw e}
   catch(e:Exception){owner?.let{SdkDeviceLeases.clear(it)};model.reply(request,failure=e.message?:"Device action unavailable")}
   finally{labels=null;locationDialog=false}
  }
  labels?.let{items->ReferenceDialog("Choose a device",{choice.completeExceptionally(IllegalStateException("Selection cancelled"))},footer={DialogAction("Cancel"){choice.completeExceptionally(IllegalStateException("Selection cancelled"))}}){Column(Modifier.padding(16.dp)){items.forEachIndexed{i,label->TextButton(onClick={choice.complete(i)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("${i+1}. $label")}}}}}
  if(locationDialog)ReferenceDialog("Choose a location",{locationDecision.completeExceptionally(IllegalStateException("Location cancelled"))},footer={DialogAction("Cancel"){locationDecision.completeExceptionally(IllegalStateException("Location cancelled"))};DialogAction("Use device"){locationDecision.complete(true)};DialogAction("Use entered location"){locationDecision.complete(false)}}){Column(Modifier.padding(16.dp)){Text("Use your approximate location, or enter coordinates manually.");OutlinedTextField(latitude,{latitude=it.take(24)},label={Text("Latitude")});OutlinedTextField(longitude,{longitude=it.take(24)},label={Text("Longitude")})}}
 }
}
