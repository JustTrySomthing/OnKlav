package app.onklav.home

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun conversationChannel(c:android.content.Context,thread:Long,name:String):String {
    require(thread>0)
    val id="conversation-$thread"
    c.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(id,name.take(100),NotificationManager.IMPORTANCE_HIGH))
    return id
}

@Composable
fun ChatSoundsDialog(close:()->Unit) {
    val c=LocalContext.current
    var rows by remember {mutableStateOf<List<Conversation>>(emptyList())}
    var query by rememberSaveable {mutableStateOf("")}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){try{rows=MessageRepository(c).conversations(500)}catch(e:Exception){error=e.message}finally{loading=false}}
    ReferenceDialog("Chat Sounds",close,footer={DialogAction("Close",onClick=close)}) {
        Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)) {
            Text("Choose a conversation, then change its sound in Android notification settings.",fontSize=14.sp)
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(vertical=12.dp),singleLine=true,placeholder={Text("Find a person or Group")})
            if(loading) Text("Loading Conversations…",fontSize=14.sp)
            val shown=rows.filter{(it.name+it.address).contains(query,true)}
            if(!loading&&shown.isEmpty()) Text(if(MessageRepository(c).allowed()) "No matching conversations." else "Allow message access in Setup to choose a conversation.",fontSize=14.sp)
            shown.forEach{row->SettingsChoice(row.name,row.address){runCatching {
                val id=conversationChannel(c,row.id,row.name)
                c.getSharedPreferences("conversation_sounds",0).edit().putBoolean(row.id.toString(),true).apply()
                c.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,id))
            }.onFailure{error=it.message}}}
        }
    }
    ErrorDialog(error){error=null}
}

@Composable
fun BlockedMessagesDialog(close:()->Unit) {
    val c=LocalContext.current
    var rows by remember {mutableStateOf<List<Conversation>>(emptyList())}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){try{
        val state=ChatStore(c).snapshot()
        val a=state.optJSONArray("blocked")
        val numbers=if(a==null) emptySet() else (0 until a.length()).map{phoneKey(a.getString(it))}.toSet()
        rows=MessageRepository(c).conversations(500,state).filter{row->row.address.split(';',',').any{phoneKey(it) in numbers}}
    }catch(e:Exception){error=e.message}finally{loading=false}}
    ReferenceDialog("Blocked Messages",close,footer={DialogAction("Close",onClick=close)}) {
        Text("Messages stay on this phone. Tap a conversation to review it. Unblock a number in Blocked Senders.",Modifier.padding(20.dp),fontSize=14.sp)
        if(loading)Text("Loading Messages…",Modifier.padding(horizontal=20.dp))
        if(!loading&&rows.isEmpty())Text(if(MessageRepository(c).allowed()) "No blocked conversations." else "Allow message access in Setup to review blocked conversations.",Modifier.padding(20.dp))
        rows.forEach{row->SettingsChoice(row.name,row.snippet){
            c.startActivity(Intent(c,MainActivity::class.java).putExtra("open_thread",row.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));close()
        }}
    }
    ErrorDialog(error){error=null}
}

@Composable
fun SendingSimDialog(close:()->Unit) {
    val c=LocalContext.current
    var epoch by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf<String?>(null)}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){epoch++}
    val p=remember{c.getSharedPreferences("messaging_setup",0)}
    val selected=p.getInt("sim",-1)
    ReferenceDialog("Sending SIM",close,footer={DialogAction("Close",onClick=close)}){
        SettingsChoice((if(selected==-1)"✓ " else "")+"System Default") {p.edit().remove("sim").apply();close()}
        if(epoch.let{MessagingTransport.permission(c,Manifest.permission.READ_PHONE_STATE)}) {
            val sims=runCatching{c.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()}
            sims.getOrDefault(emptyList()).forEach{sim->SettingsChoice((if(selected==sim.subscriptionId)"✓ " else "")+sim.displayName+" · SIM "+(sim.simSlotIndex+1)){
                p.edit().putInt("sim",sim.subscriptionId).apply();close()
            }}
            if(sims.isFailure)Text("This phone could not list its SIMs. System Default is still available.",Modifier.padding(20.dp))
            else if(sims.getOrNull().isNullOrEmpty())Text("No active SIM is available.",Modifier.padding(20.dp))
        } else SettingsChoice("Allow SIM Access","Show the SIMs available on this phone"){
            runCatching{request.launch(Manifest.permission.READ_PHONE_STATE)}.onFailure{error=it.message}
        }
    }
    ErrorDialog(error){error=null}
}

@Composable
fun PendingPicturesDialog(close:()->Unit) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var rows by remember {mutableStateOf(emptyList<JSONObject>())}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var epoch by remember {mutableIntStateOf(0)}
    var downloadsEnabled by remember {mutableStateOf(MessagingTransport.carrierEnabled(c))}
    var defaultApp by remember {mutableStateOf(MessagingTransport.role(c))}
    val roleRequest=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){epoch++}
    val changes by ChatStore.epoch.collectAsState()
    LaunchedEffect(epoch,changes){try{while(true){
        rows=withContext(ChatStore.dispatcher){MessagingTransport.pendingDownloads(c)}
        downloadsEnabled=MessagingTransport.carrierEnabled(c);defaultApp=MessagingTransport.role(c)
        kotlinx.coroutines.delay(2000)
    }}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message}}
    ReferenceDialog("Retry Pending Pictures",{if(!busy)close()},footer={DialogAction("Close",enabled=!busy,onClick=close)}){
        if(rows.isEmpty())Text("No pending picture messages.",Modifier.padding(20.dp),fontSize=15.sp)
        if(rows.isNotEmpty()&&!defaultApp)Column(Modifier.padding(20.dp)){
            Text("Choose OnKlav as your default messaging app to download these pictures. Pending messages are kept.",fontSize=14.sp)
            ChoiceButton("Choose default messaging app",outlined=false,enabled=!busy){try{
                roleRequest.launch(c.getSystemService(android.app.role.RoleManager::class.java).createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS))
            }catch(e:Exception){error=e.message}}
        }
        if(rows.isNotEmpty()&&!downloadsEnabled)Column(Modifier.padding(20.dp)){
            Text("Picture downloads are off in OnKlav. Enable them to retrieve these pending messages using your receiving SIM. Mobile data charges may apply.",fontSize=14.sp)
            ChoiceButton("Enable picture downloads",outlined=false,enabled=!busy&&defaultApp){try{MessagingTransport.enableCarrierTrial(c);downloadsEnabled=true;epoch++}catch(e:Exception){error=e.message}}
        }
        rows.forEach{job->Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)){
            Text(job.optString("sender"),fontSize=16.sp)
            Text(job.optString("error")+if(job.optBoolean("retry_scheduled"))" · Automatic retry scheduled"else "",fontSize=13.sp)
            ChoiceButton(if(job.optBoolean("in_progress"))"Downloading…" else "Retry Picture Message",outlined=false,enabled=!busy&&!job.optBoolean("in_progress")&&downloadsEnabled&&defaultApp){
                busy=true;scope.launch{try{withContext(ChatStore.dispatcher){MessagingTransport.download(c,job.getString("id"))};epoch++}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}}
            }
        }}
    }
    ErrorDialog(error){error=null}
}
