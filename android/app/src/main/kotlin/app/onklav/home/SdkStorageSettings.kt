package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Host-owned controls; changing limits never grants an app a capability. */
@Composable internal fun SdkStorageSettings(app:JSONObject,close:()->Unit){
    val c=LocalContext.current;val scope=rememberCoroutineScope();val id=app.child("manifest").getString("id")
    var state by remember(id){mutableStateOf<JSONObject?>(null)}
    var error by remember(id){mutableStateOf<String?>(null)}
    var busy by remember(id){mutableStateOf(false)}
    var confirmClear by remember(id){mutableStateOf(false)}
    suspend fun run(args:JSONObject){busy=true;try{state=AppSdk.call(c,JSONObject().put("action","storage_admin").put("id",id).put("args",args))as JSONObject;error=null}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Storage settings could not be changed"}finally{busy=false}}
    LaunchedEffect(id){run(JSONObject().put("operation","info"))}
    fun next(key:String,values:List<Int>){val current=state?:return;val policy=current.getJSONObject("limits");val old=(policy.getLong(key)/1048576).toInt();val value=values[(values.indexOf(old)+1).coerceAtLeast(0)%values.size];val args=JSONObject(policy.toString()).put("operation","limits").put(key,value.toLong()*1048576);scope.launch{run(args)}}
    ReferenceDialog("App Storage",{if(!busy)close()},large=true,footer={DialogAction("Close",enabled=!busy){close()}}){
        Column(Modifier.padding(20.dp).testTag("sdk-storage-settings")){
            Text(includedAppDisplayName(app.child("manifest")),fontSize=19.sp)
            Text("Limits apply only to this app. Lower limits never delete saved files or documents. Cache files may be removed.",Modifier.padding(vertical=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val current=state;val policy=current?.getJSONObject("limits")
            if(current!=null&&policy!=null){
                fun label(key:String,used:String,title:String):String{
                    val count=current.getLong(used);return "$title: ${count/1048576} MiB used · ${policy.getLong(key)/1048576} MiB limit"
                }
                ChoiceButton(label("documents","documents_used","Documents"),enabled=!busy,outlined=false){next("documents",listOf(16,64,128))}
                ChoiceButton(label("files","files_used","Saved files"),enabled=!busy,outlined=false){next("files",listOf(64,256,1024))}
                ChoiceButton(label("cache","cache_used","Cache"),enabled=!busy,outlined=false){next("cache",listOf(0,32,128,256))}
                Text("Tap a limit to cycle through supported sizes.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceButton("Clear Cache",enabled=!busy,outlined=false){confirmClear=true}
            }else if(!busy)ChoiceButton("Try Again",outlined=false){scope.launch{run(JSONObject().put("operation","info"))}}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp))
            error?.let{Text(it,Modifier.padding(top=12.dp),color=MaterialTheme.colorScheme.error)}
        }
    }
    if(confirmClear)ReferenceDialog("Clear App Cache?",{confirmClear=false},footer={
        DialogAction("Cancel"){confirmClear=false}
        DialogAction("Clear Cache"){
            confirmClear=false
            scope.launch{run(JSONObject().put("operation","clear_cache"))}
        }
    }){
        Text("Only this app’s disposable cache files will be removed. Saved files and documents are kept.",Modifier.padding(20.dp))
    }
}
