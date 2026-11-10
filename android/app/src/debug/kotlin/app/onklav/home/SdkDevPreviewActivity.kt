package app.onklav.home

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Debug source set only; shell permission, private staging and a one-use expiring ticket. */
class SdkDevPreviewActivity:ComponentActivity(){
 private fun feedback(phase:String,error:String?=null){val directory=File(filesDir,"sdk-dev").apply{mkdirs()};File(directory,"feedback.json").writeText(JSONObject().put("phase",phase).put("error",error?:JSONObject.NULL).toString())}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE==0){finish();return}
  val ticket=intent.getStringExtra("ticket").orEmpty()
  setContent{OnKlavTheme{
   var review by remember{mutableStateOf<JSONObject?>(null)};var grants by remember{mutableStateOf(setOf<String>())};var opened by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
   LaunchedEffect(ticket){try{
    require(ticket.matches(Regex("[a-f0-9]{32}"))){"Invalid preview ticket"}
    val directory=File(filesDir,"sdk-dev");val request=File(directory,"request.json");val pkg=File(directory,"package.onklav-a")
    val row=withContext(Dispatchers.IO){require(request.isFile&&request.length()<=4096);JSONObject(request.readText()).also{require(it.getString("ticket")==ticket&&System.currentTimeMillis()-it.getLong("created")in 0..300000);check(request.delete())}}
    review=AppSdk.staging.withLock{withContext(Dispatchers.IO){require(pkg.isFile&&pkg.length()in 1..700000);try{pkg.inputStream().use{AppSdk.stage(this@SdkDevPreviewActivity,it)}}finally{pkg.delete()}};AppSdk.inspect(this@SdkDevPreviewActivity).also{check(it.getJSONObject("manifest").getString("id")==row.getString("id"))}}
    feedback("review")
   }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Preview package could not be opened";feedback("error","Preview setup failed")}}
   if(opened!=null)SdkAppScreen(opened!!,onFeedback={row->File(File(filesDir,"sdk-dev"),"feedback.json").writeText(row.toString())},exit={feedback("closed");finish()})
   else Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
    PageHeader("Device preview",leading={CompactAction(androidx.compose.material.icons.Icons.Outlined.Close,"Close"){finish()}})
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)){
    Text("Review permissions, then open your app using the real Android services. Android may request its own permissions when needed.")
    review?.let{r->Text(r.getJSONObject("manifest").getString("name"),Modifier.padding(vertical=12.dp));val caps=r.getJSONObject("manifest").getJSONArray("capabilities");for(i in 0 until caps.length()){val cap=caps.getString(i);SdkPermissionChoice(appPermissionNames[cap]?:cap,cap in grants,enabled=!busy){allowed->grants=if(allowed)grants+cap else grants-cap}}
     Button(enabled=!busy,onClick={busy=true;scope.launch{try{AppSdk.staging.withLock{AppSdk.install(this@SdkDevPreviewActivity,r,grants)};opened=r.getJSONObject("manifest").getString("id");feedback("opened")}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Preview install failed";feedback("error","Preview install failed")}finally{busy=false}}}){Text("Install and open")}
    };error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
   }}
  }}
 }
}
