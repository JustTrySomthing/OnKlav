package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

val appPermissionNames = mapOf("ebooks.library" to "Browse Project Gutenberg and save the free ebooks you select for offline reading", "audiobooks.library" to "Import books you choose and browse, stream or download free public LibriVox audiobooks", "podcasts.library" to "Browse the offline podcast directory and public RSS feeds; no account, API key or listening quota", "radio.stations" to "Browse free public radio and play stations you select; no account or API key", "apps.handoff" to "Review typed file or text handoffs to an Android app you choose", "calendar.handoff" to "Review an event in the Android calendar app you choose", "device.connectivity" to "Read connection status without network names or identifiers", "device.settings" to "Open selected Android settings pages", "location.foreground" to "Use approximate location while this app is open, or enter it manually", "device.torch" to "Use the flashlight while this app is open, up to two minutes", "nearby.bluetooth" to "Choose a matching Bluetooth device for bounded data transactions", "nearby.wifi" to "Ask Android to join a Wi-Fi device temporarily", "network.lan" to "Choose a local network service and exchange bounded data", "background.tasks" to "Run bounded app jobs and downloads while OnKlav is closed", "accounts.connect" to "Connect accounts in your browser and keep credentials protected inside OnKlav", "network.http" to "Send and receive data from declared HTTPS services", "camera.capture" to "Take a photo using the camera app you choose", "notifications.post" to "Show app notifications on this device", "storage.secrets" to "Read and change this app’s encrypted secrets on this device", "files.manage" to "Browse and change all OnKlav documents and media, including Trash", "files.external" to "Read and change Android files in folders you choose","communications.walkie" to "Send and receive Walkie messages and invitations", "contacts.pick" to "Choose a contact (selected name and number only)", "audio.record" to "Record voice with your microphone", "audio.playback" to "Play audio and video, including background playback", "images.edit" to "Edit private images", "files.images" to "Open pictures you choose", "storage.private" to "Private saved documents, collections and files", "clipboard.read" to "Paste from clipboard", "clipboard.write" to "Copy to clipboard", "files.export" to "Export files", "share.text" to "Share text and images", "network.feeds" to "Download public RSS and Atom feeds", "network.web" to "Browse websites", "network.thumbnails" to "Download video thumbnails", "speech.output" to "Read text aloud with offline voices", "links.external" to "Open links in another browser")

@Composable
fun SdkAppsCenter(exit: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val removedIds = removedCatalogIds(games = false)
    val epoch by AppSdk.epoch.collectAsState()
    var installed by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var review by remember { mutableStateOf<JSONObject?>(null) }
    var grants by remember { mutableStateOf(setOf<String>()) }
    var permissions by remember { mutableStateOf<JSONObject?>(null) }
    var removing by remember { mutableStateOf<JSONObject?>(null) }
    var deleteData by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf<JSONObject?>(null) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var storageApp by remember{mutableStateOf<JSONObject?>(null)}
    var guide by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var setupComplete by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    fun job(block:suspend ()->Unit) { scope.launch { busy=true; try { block() } catch(e:Exception) { if(e is CancellationException)throw e;error=e.message } finally { busy=false } } }
    // Setup changes epoch itself; catalogue updates must never cancel setup.
    LaunchedEffect(refresh) {
        setupComplete=false;loading=true;loadError=null
        try { AppSdk.bootstrap(context);setupComplete=true }
        catch(e:CancellationException){throw e}
        catch(e:Exception){loadError=e.message ?: "Apps could not be loaded";loading=false}
    }
    LaunchedEffect(epoch,setupComplete) {
        if(!setupComplete)return@LaunchedEffect
        loading=true;loadError=null
        try { installed=AppSdk.list(context).filterNot{it.child("manifest").optString("id")in setOf("app.onklav.annotation","app.onklav.notes")&&!it.optBoolean("installed")} }
        catch(e:CancellationException){throw e}
        catch(e:Exception){loadError=e.message ?: "Apps could not be loaded"}
        finally{loading=false}
    }
    fun toggleDesktop(id:String) {
        runCatching { setHidden(context, false, id, id !in removedIds) }
            .onFailure { error = it.message ?: "Could not change desktop visibility" }
    }
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()) { uri -> if(uri!=null) job {
        AppSdk.staging.withLock { context.contentResolver.openInputStream(uri)?.use { AppSdk.stage(context,it) } ?: error("Could not open package"); review=AppSdk.inspect(context); grants=emptySet() }
    } }
    val saver=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/octet-stream")) { uri -> val bytes=exportBytes; exportBytes=null; if(uri!=null&&bytes!=null) job { withContext(Dispatchers.IO) { writeFiledExport(context,uri){it.write(bytes)} } } }
    suspend fun packageBytes(app:JSONObject):ByteArray { AppSdk.call(context,JSONObject().put("action","export").put("id",app.child("manifest").getString("id"))); return withContext(Dispatchers.IO) { File(context.filesDir,"app-export.onklav-a").readBytes() } }
    BackHandler { if(!busy) exit() }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            PackageManagementHeader("Apps", busy, exit)
            Text("Hide keeps the app and its data. Tap an app for uninstall and other options.",Modifier.padding(16.dp,8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("package-center-apps")) {
                if(loading)Text("Loading Apps…",Modifier.padding(bottom=12.dp))
                loadError?.let { message ->
                    Text(message,color=MaterialTheme.colorScheme.error)
                    TextButton(onClick={refresh++}){Text("Try Again")}
                }
                installed.forEach { app ->
                    val m=app.child("manifest")
                    val active=app.optBoolean("installed")
                    val packageId=m.getString("id")
                    val desktopId=AppSdk.bundledApps.entries.firstOrNull { it.value.first==packageId }?.key
                        ?: AppSdk.desktopId(packageId)
                    val hidden=desktopId in removedIds
                    Row(Modifier.fillMaxWidth().testTag("managed-app-${m.optString("id")}").padding(bottom=8.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp)).combinedClickable(onClick={selected=app},onLongClick={selected=app}).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(includedAppDisplayName(m),fontSize=17.sp); Text(if(!active) "Not installed" else if(hidden) "Installed · Hidden" else "Installed · Version ${m.optLong("version")}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        if(active && desktopId !in corePlatformApps) {
                            TextButton(enabled=!busy&&!loading&&loadError==null,onClick={toggleDesktop(desktopId)}) {
                                Text(if(hidden) "Show" else "Hide")
                            }
                        }
                        if(active) OriginalAction(R.drawable.ref_action_download,"Download ${includedAppDisplayName(m)}",enabled=!busy&&!loading&&loadError==null) { export=app }
                        else TextButton(enabled=!busy&&!loading&&loadError==null,onClick={ if(AppSdk.bundledApps.values.any { it.first==m.optString("id") }) job { AppSdk.staging.withLock { context.assets.open("sdk/${AppSdk.bundledApps.values.first { it.first==m.optString("id") }.second}.onklav-a").use { AppSdk.stage(context,it) }; review=AppSdk.inspect(context); grants=emptySet() } } else picker.launch(arrayOf("*/*")) }) { Text("Install") }
                    }
                }
                platformApps.filter { it.id !in AppSdk.bundledApps }.forEach { app ->
                    val removed = app.id in removedIds
                    Row(Modifier.fillMaxWidth().padding(bottom=8.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(app.title,fontSize=17.sp); Text(if(app.id in setOf("voicerecorder","screenrecorder")) "Included · Recording" else if(app.id in setOf("settings","games"))"Included · OnKlav" else if(app.id=="cloudfiles")"Included · Cloud storage" else if(app.id=="phone")"Included · Phone" else if(app.id=="calendar") "Included · Offline calendar" else if(app.id=="authentic") "Included · Offline authenticator" else if(app.id=="codeedit") "Included · Code editor" else if(app.id=="docuview") "Included · Document viewer" else if(app.id=="vault") "Included · Private encrypted document" else if(app.id=="compress") "Included · Media & ZIP compression" else if(app.id=="formatswap") "Included · File conversion" else if(app.id=="power") "Included · System control" else "Included · SDK port pending",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        when {
                            app.id in corePlatformApps -> Text("Core app", style = MaterialTheme.typography.labelMedium)
                            else -> TextButton(enabled=!busy,onClick={toggleDesktop(app.id)}) {
                                Text(if(removed) "Show" else "Hide")
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                ChoiceButton(if(busy) "Please Wait…" else "Install an App",enabled=!busy&&!loading&&loadError==null,outlined=false,fontSize=14.sp,minimumHeight=48.dp) { picker.launch(arrayOf("*/*")) }
                ChoiceButton("Developer Guide",outlined=false,fontSize=14.sp,minimumHeight=48.dp) { guide=true }
            }
        }
    }
    selected?.let { app -> val m=app.child("manifest"); ReferenceDialog(includedAppDisplayName(m),{selected=null},footer={DialogAction("Close"){selected=null}}) {
        Column(Modifier.padding(16.dp)) {
            Text("Version ${m.optLong("version")} · Publisher ${m.optString("publisher").take(16)}",fontSize=13.sp)
            var includedVersion by remember(m.optString("id")){mutableLongStateOf(0L)}
            LaunchedEffect(m.optString("id")){runCatching{includedVersion=AppSdk.includedVersion(context,m.getString("id"))}}
            if(app.optBoolean("installed")) {
                if(includedVersion>m.optLong("version"))SettingsChoice("Update Included App","Version $includedVersion · Review permissions before updating") {
                    job { AppSdk.staging.withLock {
                        val name=AppSdk.bundledApps.values.first{it.first==m.getString("id")}.second
                        context.assets.open("sdk/$name.onklav-a").use{AppSdk.stage(context,it)}
                        review=AppSdk.inspect(context)
                        grants=bundledUpgradeGrants(app,review!!.child("manifest").optJSONArray("capabilities").strings().toSet())
                        selected=null
                    } }
                }
                SettingsChoice("Storage") { storageApp=app;selected=null }
                SettingsChoice("Permissions") { permissions=app; grants=app.optJSONArray("grants").strings().toSet(); selected=null }
                SettingsChoice("Save or Share Package") { export=app; selected=null }
                if (!AppSdk.isRemovalProtected(m.getString("id"))) {
                    SettingsChoice("Remove App") { removing=app; deleteData=false; selected=null }
                }
            } else Text("Use Install to add this app again.",fontSize=14.sp)
        }
    } }
    review?.let { r -> val m=r.child("manifest"); ReferenceDialog(if(r.optBoolean("update")) "Update App" else "Install App",{review=null},footer={
        DialogAction("Cancel",enabled=!busy) { review=null }
        DialogAction("Install",primary=true,enabled=!busy&&!loading&&loadError==null) { job { AppSdk.staging.withLock { AppSdk.install(context,r,grants) }; review=null } }
    }) { Column(Modifier.padding(20.dp)) {
        Text("${includedAppDisplayName(m)} · Version ${m.optLong("version")}",fontSize=18.sp)
        Text("Publisher fingerprint\n${r.optString("publisher_fingerprint")}",Modifier.padding(vertical=12.dp),fontSize=12.sp)
        SdkNetworkPolicyReview(m)
        Text("Allow this app to:",fontSize=15.sp)
        m.optJSONArray("capabilities").strings().forEach { cap -> PermissionRow(cap,cap in grants,enabled=!busy&&!loading&&loadError==null) { grants=if(it) grants+cap else grants-cap } }
        if(m.optJSONArray("capabilities").strings().isEmpty()) Text("No privileged access requested.",fontSize=14.sp)
        Text("Runs inside OnKlav. No separate Android app is installed.",Modifier.padding(top=12.dp),fontSize=13.sp)
    } } }
    permissions?.let { app -> ReferenceDialog("App Permissions",{permissions=null},footer={DialogAction("Cancel"){permissions=null};DialogAction("Save",enabled=!busy&&!loading&&loadError==null){job { AppSdk.call(context,JSONObject().put("action","permissions").put("id",app.child("manifest").getString("id")).put("grants",JSONArray(grants.toList()))); permissions=null }}}) {
        Column(Modifier.padding(20.dp)) { SdkNetworkPolicyReview(app.child("manifest"));app.child("manifest").optJSONArray("capabilities").strings().forEach { cap -> PermissionRow(cap,cap in grants,enabled=!busy&&!loading&&loadError==null) { grants=if(it) grants+cap else grants-cap } } }
    } }
    removing?.let { app -> ReferenceDialog("Remove App?",{removing=null},footer={DialogAction("Cancel"){removing=null};DialogAction("Remove",destructive=true,enabled=!busy&&!loading&&loadError==null){job { AppSdk.call(context,JSONObject().put("action","remove").put("id",app.child("manifest").getString("id")).put("delete_data",deleteData)); removing=null }}}) {
        Column(Modifier.padding(20.dp)) { Text("Remove ${includedAppDisplayName(app.child("manifest"))} from OnKlav?"); SdkPermissionChoice("Also delete this app's saved data",deleteData,enabled=!busy&&!loading&&loadError==null){deleteData=it}; Text("Exported copies in Files are kept.",fontSize=13.sp) }
    } }
    export?.let { app -> val name=includedAppDisplayName(app.child("manifest")).replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(60)+".onklav-a"; ReferenceDialog(includedAppDisplayName(app.child("manifest")),{export=null},footer={DialogAction("Close"){export=null}}) {
        Column(Modifier.padding(16.dp)) {
            SettingsChoice("Save Package") { job { exportBytes=packageBytes(app); saver.launch(name); export=null } }
            SettingsChoice("Share Package") { job { val bytes=packageBytes(app); val uri=withContext(Dispatchers.IO) { ExportProvider.stage(context,name,bytes,"onklav-a") }; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share App")); export=null } }
        }
    } }
    if(guide) ReferenceDialog("App Developer Guide",{guide=false},large=true,footer={DialogAction("Close"){guide=false}}) { Text("OnKlav App SDK 0.4\n\nStart from a template and write only your app logic. The shared onklav-ui library supplies themed screens, forms, lists, dialogs, drawers, file selection, media and speech. Included apps use the same public controllers.\n\nOn a development computer, install Python 3.11+ and Rust with the Wasm target, then use developer/onklav.py new and verify from the SDK starter. Verify builds and tests locally with a disposable signer; you do not need Android Studio or a publisher key for local checks. Portable dependencies move with your project. The catalog command finds available components and services.\n\nFor distribution, create your own publisher key once and build a signed .onklav-a. Reuse that key and app ID and increase the version for updates. Users install, review grants, update and remove apps here. Generated packages include a signed minimum host build and a themed stock icon or bounded PNG.\n\nThe host owns Back, title/actions, safe areas, fonts, theme and protected Close. Forms and list/detail templates handle persistence, validation and discard prompts. Apps receive only declared, user-approved permissions; clipboard, files, web, feeds, recording and speech use the live broker. No raw Android APIs or filesystem paths are exposed.\n\nThe SDK includes quickstart, API and compatibility references, cookbook projects and scenario tests. Desktop verification does not replace real phone tests for providers, media, speech, accessibility or lifecycle. Working source includes native date/time, camera, secure storage, bounded notifications and private collection/file services. External carrier acceptance and final SDK packaging remain pending. Use the matching host and consult the SDK capability inventory. Independent security and store qualification remain open.",Modifier.padding(20.dp),fontSize=15.sp) }
    storageApp?.let{app->SdkStorageSettings(app){storageApp=null}}
    ErrorDialog(error){error=null}
}
private fun JSONArray?.strings():List<String> = this?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
@Composable private fun PermissionRow(cap:String,checked:Boolean,enabled:Boolean,change:(Boolean)->Unit) { SdkPermissionChoice(appPermissionNames[cap] ?: cap,checked,enabled,change) }

@Composable internal fun SdkNetworkPolicyReview(manifest:JSONObject){
 val origins=manifest.optJSONObject("network")?.optJSONArray("origins")?.strings().orEmpty()
 if(origins.isNotEmpty())Text("HTTPS services:\n"+origins.joinToString("\n"),Modifier.padding(vertical=8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
 val accounts=manifest.optJSONObject("network")?.optJSONArray("accounts")
 if(accounts!=null&&accounts.length()>0){
  Text("Declared account providers",Modifier.padding(top=8.dp),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurface)
  for(index in 0 until minOf(accounts.length(),4)){
   val provider=accounts.getJSONObject(index)
   Text(provider.getString("label")+"\nRequested scopes: "+provider.getJSONArray("scopes").strings().joinToString(", ")+"\nAPI services: "+provider.getJSONArray("api_origins").strings().joinToString(", "),Modifier.padding(vertical=6.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Text("Connect accounts in your system browser only when you choose to sign in. Installing does not connect an account.",Modifier.padding(bottom=8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
