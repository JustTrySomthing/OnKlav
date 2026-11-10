package app.onklav.home

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.onklav.shared.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(exit: () -> Unit, allow: () -> Unit, startMessaging: () -> Unit = exit, completedLabel:String = "Start messaging") {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var epoch by remember { mutableIntStateOf(0) }
    var child by rememberSaveable { mutableStateOf<String?>(null) }
    var phone by rememberSaveable {
        mutableStateOf(c.getSharedPreferences("profile", 0).getString("phone", "").orEmpty())
    }
    var phoneDialog by rememberSaveable { mutableStateOf(false) }
    var simDialog by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var options by rememberSaveable { mutableStateOf(false) }
    
    var transfers by remember { mutableStateOf(emptyList<org.json.JSONObject>()) }
    val owner = LocalLifecycleOwner.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            epoch++
        }
    val role =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            epoch++
        }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) epoch++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(epoch) {
        transfers = withContext(ChatStore.dispatcher) { MessagingTransport.pendingDownloads(c) }
    }
    BackHandler(onBack = exit)
    val read = epoch.let { OnKlavPermissions.messageRuntime.all { MessagingTransport.permission(c,it) } }
    val send = MessagingTransport.permission(c, Manifest.permission.SEND_SMS)
    val contacts = MessagingTransport.permission(c, Manifest.permission.READ_CONTACTS)
    val default = MessagingTransport.role(c)
    val notifications =
        c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun launch(intent: Intent) {
        runCatching { c.startActivity(intent) }.onFailure { error = it.message }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp)
                .testTag("messenger-setup")
        ) {
            HomeHeader("Setup", back = exit) {
                TextButton(onClick = { child = "Help" }) { Text("Help") }
            }
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max=720.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ready = default && read && send
                Text(if(ready) "Messaging is ready" else "Set up messages",style=MaterialTheme.typography.headlineSmall)
                Text("${listOf(default,read&&send).count{it}} of 2 complete · ${listOf(default,read&&send).count{!it}} remaining",style=MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress={listOf(default,read&&send).count{it}/2f},modifier=Modifier.fillMaxWidth().height(6.dp))
                SetupStepCard(1,SetupStep("sms-role","Default SMS app","Choose OnKlav for text and picture messages",default,"Choose OnKlav"),!default){
                    runCatching{role.launch(c.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS))}.onFailure{error=it.message}
                }
                SetupStepCard(2,SetupStep("sms-access","Message access","Allow text and picture permissions",read&&send,"Allow message access"),default&&!(read&&send)){
                    runCatching{if(!default)role.launch(c.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS))else permission.launch(OnKlavPermissions.messageRuntime.toTypedArray())}.onFailure{error=it.message}
                }
                TextButton(onClick={options=!options}){Text(if(options) "Hide options & recovery" else "Options & recovery")}
                if(options){
                ChoiceButton("Privacy policy",outlined=false){child="Privacy"}
                Text("Optional — you can do these later",fontSize=16.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(contacts) "✓ Contacts allowed" else "Contacts not allowed yet",fontSize=17.sp,color=if(contacts)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if(!contacts)ChoiceButton("Allow contacts",outlined=false){permission.launch(arrayOf(Manifest.permission.READ_CONTACTS))}
                Text(if(notifications) "✓ Notifications on" else "Notifications off",fontSize=17.sp,color=if(notifications)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if(!notifications)ChoiceButton("Turn on notifications",outlined=false){
                    if(Build.VERSION.SDK_INT>=33&&!MessagingTransport.permission(c,Manifest.permission.POST_NOTIFICATIONS))permission.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    else launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName))
                }
                ChoiceButton("Sending SIM",outlined=false){
                    if(!MessagingTransport.permission(c,Manifest.permission.READ_PHONE_STATE))permission.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))else simDialog=true
                }
                ChoiceButton("My number"+if(phone.isNotBlank()) " · "+displayPhone(phone) else "",outlined=false){phoneDialog=true}
                ChoiceButton("Android App Permissions",outlined=false){launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.packageName)))}
                if (
                    java.io.File(c.filesDir, "incoming-sms").listFiles()?.any {
                        it.extension == "json"
                    } == true
                ) {
                    ChoiceButton("Recover Pending Text Messages", outlined = false) {
                        scope.launch {
                            try {
                                withContext(ChatStore.dispatcher) { MessagingTransport.retrySms(c) }
                                epoch++
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                }
                transfers.forEach { job ->
                    Text(job.optString("sender") + " · " + job.optString("error"))
                    ChoiceButton("Retry Picture Message", outlined = false) {
                        scope.launch {
                            try {
                                withContext(ChatStore.dispatcher) {
                                    MessagingTransport.download(c, job.getString("id"))
                                }
                                epoch++
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                }
            }
                }
            Button(onClick={
                when {
                    default && read && send -> startMessaging()
                    !default -> role.launch(c.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS))
                    else -> permission.launch(OnKlavPermissions.messageRuntime.toTypedArray())
                }
            },modifier=Modifier.fillMaxWidth().padding(vertical=12.dp).heightIn(min=52.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)){
                Text(when {default&&read&&send->completedLabel;!default->"Set as default SMS app";else->"Allow message permissions"},fontSize=17.sp)
            }
        }
    }
    if (phoneDialog)
        OnKlavAlertDialog(
            onDismissRequest = { phoneDialog = false },
            title = { Text("My Number") },
            text = {
                OutlinedTextField(
                    phone,
                    { phone = phoneInput(it) },
                    visualTransformation = PhoneVisualTransformation,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Phone),
                    singleLine = true,
                    label = { Text("Your Phone Number") },
                )
            },
            confirmButton = {
                DialogAction("Save") {
                    if (phone.isBlank() || phone.count { it.isDigit() } in 3..15) {
                        c.getSharedPreferences("profile", 0)
                            .edit()
                            .putString("phone", phone.trim())
                            .apply()
                        phoneDialog = false
                    } else error = "Enter a valid phone number"
                }
            },
            dismissButton = { DialogAction("Cancel") { phoneDialog = false } },
        )
    if (simDialog) SendingSimDialog { simDialog = false }
    child?.let { SettingsDialogs(it) { child = null } }
    ErrorDialog(error) { error = null }
}
