package app.onklav.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
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

@Composable
fun WalkieSettingsDialog(close: () -> Unit) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var play by rememberSaveable { mutableStateOf(true) }
    var send by rememberSaveable { mutableStateOf(true) }
    var beeps by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh) {
        loading=true;loaded=false;loadError=null
        try {
            val state=withContext(ChatStore.dispatcher) {
                AppSdk.sync(c,JSONObject().put("action","walkie_preferences")) as JSONObject
            }
            val prefs=state.getJSONObject("preferences")
            play=prefs.getBoolean("auto_play");send=prefs.getBoolean("auto_send");beeps=prefs.getBoolean("beeps");loaded=true
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) { loadError=e.message ?: "Walkie preferences could not be loaded" }
        finally{loading=false}
    }
    ReferenceDialog("Walkie Talkie",{if(!busy)close()},footer={
        DialogAction("Close",enabled=!busy,onClick=close)
        DialogAction("Save",enabled=loaded&&!busy) {
            busy=true
            scope.launch { try {
                withContext(ChatStore.dispatcher) {
                    AppSdk.sync(c,JSONObject().put("action","walkie_preferences").put("args",JSONObject()
                        .put("auto_play",play).put("auto_send",send).put("beeps",beeps)))
                };close()
            } catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;error=e.message} finally {busy=false} }
        }
    }) {
        Column(Modifier.padding(20.dp)) {
            if(loading) Text("Loading Walkie preferences…",fontSize=14.sp)
            loadError?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++}){Text("Try Again")}}
            PreferenceToggle("Auto Play",play,loaded&&!busy){play=it}
            PreferenceToggle("Auto Send",send,loaded&&!busy){send=it}
            PreferenceToggle("Walkie Talkie Beeps",beeps,loaded&&!busy){beeps=it}
            Text("Wait for Speak Now before talking. Recording stops after 12 seconds. Auto Send sends the clip when you finish; turn it off to review Send or Discard. Auto Play plays received clips while the connected voice chat is open. Carrier charges and delays apply; MMS may require mobile data.\n\nPlayed clips expire after 24 hours, unheard clips after 7 days. Voice history keeps up to 100 clips. Android and OnKlav app permissions still apply.",Modifier.padding(vertical=16.dp),fontSize=15.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceButton("Recent Voice History",outlined=false,enabled=loaded&&!busy){history=true}
            if(!MessagingTransport.carrierEnabled(c)) Text("Carrier voice messaging is awaiting verification. Saving preferences does not enable it.",Modifier.padding(top=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(history) ReferenceDialog("Voice History",{history=false},footer={DialogAction("Close"){history=false}}) {
        Text("Open Walkie Talkie to listen to and manage its private voice archive.",Modifier.padding(20.dp),fontSize=15.sp)
        SettingsChoice("Open Walkie Talkie") {
            c.startActivity(Intent(c,MainActivity::class.java).putExtra("open_walkie",true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            history=false;close()
        }
    }
    ErrorDialog(error){error=null}
}

@Composable
fun PrivacySettingsDialog(close: () -> Unit) {
    val c=LocalContext.current
    val revision by MessageVideoPreviews.epoch.collectAsState()
    val allowed=revision.let{MessageVideoPreviews.allowed(c)}
    var error by remember {mutableStateOf<String?>(null)}
    ReferenceDialog("Privacy Policy",close,footer={DialogAction("Close",onClick=close)}) {
        Column(Modifier.padding(20.dp)) {
            PreferenceToggle("Automatic YouTube Previews",allowed){value->runCatching{MessageVideoPreviews.setAllowed(c,value)}.onFailure{error=it.message}}
            Text("When enabled, YouTube thumbnail requests reveal the video ID and your IP address to Google. Turning this off cancels pending requests and clears cached message thumbnails. Videos play only when you choose Play.",Modifier.padding(vertical=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text("OnKlav stores its documents, messages metadata, preferences and app packages privately on this phone. Android messages remain in the phone’s messaging provider.\n\nAndroid grants permissions to OnKlav. Installed apps receive only the separate permissions you allow in Settings › Apps. You can revoke them or remove an app there. Browser pages, RSS feeds and videos contact their providers when used; their privacy policies apply. Speech uses OnKlav’s included offline voices. When automatic update checks are enabled, OnKlav contacts GitHub once a day when opened. Update downloads start only when you choose. You can turn automatic checks off in Settings › Check for updates.\n\nExported files and backups may contain personal information. Anyone you share a file with can keep a copy. Quiet Hours controls OnKlav alerts, not other apps.\n\nCarrier messaging and some platform services are still being verified. OnKlav does not provide end-to-end encryption for SMS/MMS.",fontSize=15.sp,lineHeight=22.sp) }
        }
    }
    ErrorDialog(error){error=null}
}

@Composable
fun SpamSettingsDialog(close: () -> Unit) {
    val c=LocalContext.current
    val p=remember { c.getSharedPreferences("spam_screen",0) }
    var enabled by rememberSaveable {mutableStateOf(p.getBoolean("enabled",true))}
    var reset by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    ReferenceDialog("Spam Screening",close,covered=reset,footer={
        DialogAction("Cancel",onClick=close)
        DialogAction("Save"){runCatching {check(p.edit().putBoolean("enabled",enabled).commit());ChatStore.epoch.value++;close()}.onFailure{error=it.message}}
    }) { Column(Modifier.padding(20.dp)) {
        PreferenceToggle("Warn About Suspicious Messages",enabled){enabled=it}
        Text("Checks suspicious link wording from people outside your contacts, on this phone only. It can miss scams or flag legitimate messages. Nothing is hidden or deleted. Contacts access is needed.",fontSize=14.sp,lineHeight=20.sp)
        ChoiceButton("Reset Trusted Senders",Modifier.padding(top=12.dp),outlined=false){reset=true}
    } }
    if(reset) ReferenceDialog("Reset Trusted Senders?",{reset=false},footer={
        DialogAction("Cancel"){reset=false}
        DialogAction("Reset",destructive=true){runCatching{check(p.edit().remove("trusted").commit());ChatStore.epoch.value++;reset=false}.onFailure{error=it.message}}
    }){Text("Messages from these senders will be checked again. Your contacts and blocked senders stay unchanged.",Modifier.padding(20.dp),fontSize=15.sp)}
    ErrorDialog(error){error=null}
}
