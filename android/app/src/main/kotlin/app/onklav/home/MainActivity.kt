package app.onklav.home

import kotlinx.coroutines.withContext

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
import app.onklav.tools.NotesScreen

class MainActivity : ComponentActivity() {
    private var launchIntent by mutableStateOf<Intent?>(null)
    private var intentEpoch by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FolderVaultLifecycle.install(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        // Android owns the initial splash. Animate its glyph only; the padded
        // vector keeps the speech tail and K inside Android's splash mask.
        if(android.os.Build.VERSION.SDK_INT>=31) splashScreen.setOnExitAnimationListener { splash ->
            splash.iconView?.let { icon -> icon.animate().scaleX(1.22f).scaleY(1.22f).alpha(0f).setDuration(700).setInterpolator(android.view.animation.DecelerateInterpolator()).start() }
            splash.animate().alpha(0f).setStartDelay(0).setDuration(300).withEndAction {splash.remove()}.start()
        }
        launchIntent = intent
        runCatching{SdkNativeData.flush(this)}.onFailure{ChatStore.lastError.value="App cleanup remains pending: ${it.message}"}
        TransportRecovery.schedule(this)
        // Draw the shell immediately. Package setup already runs asynchronously;
        // holding every pre-draw hid useful UI for 350ms to two seconds.
        setContent { OnKlavTheme { OnKlavScreen(launchIntent, intentEpoch) } }
    }

    override fun onResume(){super.onResume();PhoneCalls.reconcile(this);androidx.lifecycle.ViewModelProvider(this)[OnKlavUpdateModel::class.java].automaticCheck()}

    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        if(shouldTrimSpeech(level))NeuralSpeechRuntime.trim()
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) { SdkDesktopIcons.clear();AppearanceStore.trimMemory();SdkThumbnails.trimMemory();MessageVideoPreviews.trimMemory() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntent = intent
        intentEpoch++
    }
}

@Composable
fun OnKlavScreen(launchIntent: Intent? = null, intentEpoch: Int = 0,onReady:()->Unit={}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var browserUrl by rememberSaveable {mutableStateOf<String?>(null)}
    var browserLaunch by rememberSaveable {mutableLongStateOf(0L)}
    fun browse(url:String){browserUrl=SdkWebPolicy.external(url);browserLaunch++}
    var initialNote by rememberSaveable { mutableStateOf<String?>(null) }
    var fileNote by rememberSaveable { mutableStateOf<String?>(null) }
    var noteReturn by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTool by rememberSaveable { mutableStateOf<String?>(null) }
    var initialPeer by rememberSaveable {mutableStateOf<String?>(null)}
    var initialVideo by rememberSaveable {mutableStateOf<String?>(null)}
    var videoLaunchKey by rememberSaveable {mutableLongStateOf(0L)}
    var toolReturn by rememberSaveable { mutableStateOf<String?>(null) }
    fun playVideo(url:String){toolReturn=activeTool?.takeUnless{it=="youtube"};initialVideo=url;videoLaunchKey++;activeTool="youtube"}
    var gamePeer by rememberSaveable{mutableStateOf<String?>(null)}
    var gameMatch by rememberSaveable{mutableStateOf<String?>(null)}
    var socialRequestLaunch by rememberSaveable{mutableStateOf<Long?>(null)}
    var gameName by rememberSaveable{mutableStateOf("")}
    var tab by rememberSaveable { mutableIntStateOf(2) }
    var cameraImmersive by remember { mutableStateOf(false) }
    val onCameraImmersiveChange = remember { { enabled:Boolean -> cameraImmersive=enabled } }
    var appsResetKey by rememberSaveable { mutableIntStateOf(0) }
    fun playWith(peer:String,name:String){gamePeer=peer;gameName=name}
    fun talkWith(peer:String){initialPeer=peer;initialVideo=null;videoLaunchKey++;activeTool="walkie"}
    var error by remember { mutableStateOf<String?>(null) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var setup by rememberSaveable { mutableStateOf(false) }
    var platformSetup by rememberSaveable { mutableStateOf(!OnKlavPermissions.seen(context)) }
    var powerUnavailable by remember { mutableStateOf(false) }
    fun power(){activeTool=null;toolReturn=null;tab=2;powerUnavailable=!PowerAccessibilityService.openMenu(context)}
    LaunchedEffect(activeTool){when(activeTool){"power"->power();"phone"->{tab=5;activeTool=null};"camera"->{tab=6;activeTool=null}}}
    var catalogManagement by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    var sdkCatalog by remember { mutableStateOf(emptyList<org.json.JSONObject>()) }
    val sdkEpoch by AppSdk.epoch.collectAsState()
    val notesRecoveryRequired by AppSdk.notesRecoveryRequired.collectAsState()
    // Installing bundled apps updates sdkEpoch. Do not let that cancel and
    // restart the very bootstrap that produced the update.
    LaunchedEffect(Unit) {
        try { AppSdk.bootstrap(context); sdkCatalog = AppSdk.list(context);if(activeTool=="notes"&&!notesRecoveryRequired)activeTool="write" }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { error = e.message }
        finally { onReady() }
    }
    LaunchedEffect(sdkEpoch) {
        try { sdkCatalog = AppSdk.list(context) }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { error = e.message }
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionEpoch++
        }
    val incomingAddress =
        launchIntent
            ?.takeIf { it.action == Intent.ACTION_SENDTO }
            ?.data
            ?.takeIf { it.scheme in setOf("sms", "smsto", "mms", "mmsto") }
            ?.schemeSpecificPart
            ?.substringBefore('?')
    val incomingThread = launchIntent?.getLongExtra("open_thread", 0) ?: 0
    val incomingMessageKey = launchIntent?.getStringExtra("open_message_key")
    val incomingMessageDate = launchIntent?.getLongExtra("open_message_date", 0) ?: 0
    LaunchedEffect(intentEpoch) {
        if(launchIntent?.getBooleanExtra("open_friend_games",false)==true){setup=false;tab=2;gameMatch=launchIntent.getStringExtra("friend_game_match");activeTool="games";return@LaunchedEffect}
        try{launchIntent?.getStringExtra("sdk_notice")?.let{token->
            val app=withContext(kotlinx.coroutines.Dispatchers.IO){SdkNotifications.resolveTap(context,token)}
            if(app!=null){sdkCatalog=AppSdk.list(context);browserUrl=null;fileNote=null;noteReturn=null;setup=false;settingsRoute=null;tab=2;initialPeer=null;initialNote=null;initialVideo=null;videoLaunchKey++;activeTool=AppSdk.bundledApps.entries.firstOrNull{it.value.first==app}?.key?:AppSdk.desktopId(app);return@LaunchedEffect}
        }}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message?:"Could not open this app notification"}
        val recorder=launchIntent?.getStringExtra("open_native_recorder")?.takeIf{it in setOf("voicerecorder","screenrecorder")}
        if(recorder!=null){browserUrl=null;fileNote=null;noteReturn=null;setup=false;settingsRoute=null;tab=2;videoLaunchKey++;if(activeTool!=recorder){toolReturn=activeTool;activeTool=recorder};return@LaunchedEffect}
        if(launchIntent?.getBooleanExtra("open_real_social",false)==true && RealSocial.installed(context)){
            sdkCatalog=AppSdk.list(context);browserUrl=null;fileNote=null;noteReturn=null;setup=false;settingsRoute=null;tab=2;initialPeer=null;initialNote=null;initialVideo=null;videoLaunchKey++;socialRequestLaunch=videoLaunchKey.takeIf{launchIntent.getBooleanExtra("open_real_social_requests",false)};activeTool="social";return@LaunchedEffect
        }
        val essential=launchIntent?.getStringExtra("open_native_tool")?.takeIf{it in setOf("clock","flashlight","camera","calendar")}
        if(essential!=null&&(essential=="calendar"||essentialAccess(context,"app.onklav.$essential","storage.private"))){sdkCatalog=AppSdk.list(context);browserUrl=null;fileNote=null;noteReturn=null;setup=false;settingsRoute=null;tab=2;initialPeer=null;initialNote=null;initialVideo=null;videoLaunchKey++;activeTool=essential;return@LaunchedEffect}
        if(launchIntent?.getBooleanExtra("open_email",false)==true && mailAccess(context)){sdkCatalog=AppSdk.list(context);browserUrl=null;fileNote=null;noteReturn=null;setup=false;settingsRoute=null;tab=2;initialPeer=null;initialNote=null;initialVideo=null;videoLaunchKey++;activeTool="email";return@LaunchedEffect}
        launchIntent?.getStringExtra("open_sdk_carrier")?.let{route->
            val owner=withContext(kotlinx.coroutines.Dispatchers.IO){SdkCarrierRoutes.owners(context).firstOrNull{SdkCarrierRoutes.route(it)==route}}
            if(owner!=null){val app=owner.getString("id");sdkCatalog=AppSdk.list(context);browserUrl=null;fileNote=null;noteReturn=null;setup=false;settingsRoute=null;tab=2;initialPeer=null;initialNote=null;initialVideo=null;videoLaunchKey++;activeTool=AppSdk.bundledApps.entries.firstOrNull{it.value.first==app}?.key?:AppSdk.desktopId(app);return@LaunchedEffect}
        }
        launchIntent?.getStringExtra("open_note")?.takeIf{it.matches(Regex("[a-f0-9]{32}"))}?.let{
            if (notesRecoveryRequired) {
                noteReturn=activeTool?.takeIf{tool->tool=="files"};initialNote=it;initialPeer=null;initialVideo=null;videoLaunchKey++;activeTool="notes"
            } else {
                browserUrl=null
                fileNote="write:$it"
            }
            return@LaunchedEffect
        }
        if(launchIntent?.getBooleanExtra("open_walkie",false)==true){initialPeer=null;toolReturn=null;videoLaunchKey++;activeTool="walkie";return@LaunchedEffect}
        if(launchIntent?.getBooleanExtra("open_pending_pictures",false)==true){activeTool=null;browserUrl=null;setup=false;settingsRoute="Retry Pending Pictures";tab=0}
        if (incomingAddress != null || incomingThread > 0) {
            val savedGroup = if (incomingThread > 0) runCatching {
                MessageRepository(context).reconcileGroups().savedGroups().firstOrNull { it.thread == incomingThread }
            }.getOrNull() else null
            tab = if (savedGroup != null) 1 else 0
            activeTool = null
            browserUrl = null
            setup = false
            settingsRoute = null
        }
    }
    /* Screens stay composed through their exit, over the retained home. */
    val pageRoute = if(platformSetup && !setup) "platform-setup" else if(setup) "setup" else settingsRoute?.takeIf { it in setOf("Apps","Games","Profile","Backups") }?.let { "settings:$it" } ?: activeTool?.takeUnless{it in setOf("power","phone","camera")}?.let { "tool:$it" }
    val fullScreenCamera = tab==6 && cameraImmersive && pageRoute==null
    Box(Modifier.fillMaxSize()) {
    BackHandler(enabled = tab != 0 && pageRoute == null && catalogManagement == null) { tab = 0 }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            OnKlavBackground(Modifier.matchParentSize())
            // Android 15 ignores statusBarColor: paint behind its transparent bars.
            if(!fullScreenCamera) Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background).testTag("status-bar-backplate"))
            if(!fullScreenCamera) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(MaterialTheme.colorScheme.background).testTag("navigation-bar-backplate"))
            Column(
                Modifier.fillMaxSize()
                    .then(if(fullScreenCamera)Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)).imePadding())
            ) {
                if (tab == 3 || tab == 4)
                    HomeHeader(homePageTitles[tab], gamesHint = tab == 3) {
                        if (tab == 3) ManageGamesAction { catalogManagement = "Games" }
                    }
                Box(Modifier.weight(1f).fillMaxWidth().then(if(tab==6)Modifier.background(androidx.compose.ui.graphics.Color.Black)else Modifier).then(if(fullScreenCamera)Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))).testTag("home-workspace").then(if(tab==3||tab==4)Modifier.padding(horizontal=8.dp)else Modifier)) {
                    when (tab) {
                        0 ->
                            holder.SaveableStateProvider("messages") {
                                MessengerPane(
                                    { setup = true },
                                    incomingAddress,
                                    incomingThread,
                                    intentEpoch + permissionEpoch,
                                    incomingMessageKey,
                                    incomingMessageDate,
                                    playVideo=::playVideo,openWeb=::browse,
                                    openWalkie=::talkWith,openGames=::playWith,
                                )
                            }
                        1 -> holder.SaveableStateProvider("groups") { GroupsPane({ setup = true }, incomingThread, intentEpoch, incomingMessageKey, incomingMessageDate,playVideo=::playVideo,openWeb=::browse,openWalkie=::talkWith,openGames=::playWith) }
                        2 ->
                            AppsDesktop(resetKey=appsResetKey, manage={catalogManagement="Apps"}) { id ->
                                toolReturn=null;videoLaunchKey++;initialPeer=null;initialVideo=null
                                if(id=="power")power()
                                else if (id in setOf("cloudfiles", "settings", "games", "phone", "calendar", "authentic", "codeedit", "docuview", "formatswap", "compress", "vault", "voicerecorder", "screenrecorder") || id in AppSdk.bundledApps)
                                    activeTool = id
                                else if (sdkCatalog.any { it.optBoolean("installed") && AppSdk.desktopId(it.child("manifest").optString("id")) == id }) activeTool = id
                                else error = "$id has not been rewritten yet."
                            }
                        5 -> holder.SaveableStateProvider("phone") { PhoneWorkspace({tab=2},embeddedInHome=true) }
                        6 -> holder.SaveableStateProvider("camera") { SdkAppScreen("app.onklav.camera",embeddedInHome=true,onImmersiveChange=onCameraImmersiveChange,exit={tab=2}) }
                        3 ->
                            GamesCatalog { id ->
                                runCatching {
                                        WebGameActivity.launch(context,id)
                                    }
                                    .onFailure {
                                        error = "Could not start the game runtime: " + it.message
                                    }
                            }
                        else ->
                            SettingsPane { name ->
                                when (name) {
                                    "Appearance" -> appearance = true
                                    "Setup" -> platformSetup = true
                                    else -> settingsRoute = name
                                }
                            }
                    }
                }
                if(!fullScreenCamera) HomeNavigation(tab) { destination ->
                    activeTool=null;toolReturn=null;tab=destination
                    if(destination==5||destination==6){initialPeer=null;initialVideo=null;videoLaunchKey++}
                    if(destination==2)appsResetKey++
                }
            }
        }
    }
    RightSlideRoute(pageRoute,opaque=pageRoute!="tool:walkie",popup=pageRoute=="tool:walkie") { route ->
        when {
            route.startsWith("tool:") -> {
                val id=route.removePrefix("tool:")
                holder.SaveableStateProvider("tool:$id") {
                val packageId=AppSdk.bundledApps[id]?.first ?: sdkCatalog.firstOrNull { AppSdk.desktopId(it.child("manifest").optString("id"))==id }?.child("manifest")?.optString("id")
                val launchVideo=remember(id,videoLaunchKey){initialVideo.takeIf{id=="youtube"}}
                val launchPeer=remember(id,videoLaunchKey){initialPeer.takeIf{id=="walkie"}}
                val closingLaunch=videoLaunchKey
                val close:()->Unit={if(videoLaunchKey==closingLaunch){activeTool=if(id=="notes")noteReturn else toolReturn;toolReturn=null;noteReturn=null;initialNote=null;initialVideo=null;initialPeer=null}}
                if(id=="voicerecorder") RecorderWorkspace(RecorderKind.VOICE,close)
                else if(id=="screenrecorder") RecorderWorkspace(RecorderKind.SCREEN,close)
                else if(id=="settings") Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){BackHandler(onBack=close);HomeHeader("Settings",back=close);SettingsPane{name->when(name){"Appearance"->appearance=true;"Setup"->platformSetup=true;else->settingsRoute=name}}}
                else if(id=="games") Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){BackHandler(onBack=close);HomeHeader("Games",back=close){ManageGamesAction{catalogManagement="Games"}};GamesCatalog(inviteMatch=gameMatch,inviteOpened={gameMatch=null}){id->runCatching{WebGameActivity.launch(context,id)}.onFailure{error="Could not start the game runtime: "+it.message}}}
                else if(id=="social"&&packageId==RealSocial.APP) RealSocialScreen(close,::browse,socialRequestLaunch)
                else if(id=="cloudfiles") CloudWorkspace(close,::browse)
                else if(id=="phone") PhoneWorkspace(close)
                else if(id=="calendar") CalendarWorkspace(close,initialEvent=launchIntent?.getStringExtra("calendar_event"),initialDate=launchIntent?.getStringExtra("calendar_date"),launchKey=videoLaunchKey)
                else if(id=="authentic") AuthenticWorkspace(close)
                else if(id=="codeedit") CodeEditWorkspace(close)
                else if(id=="docuview") DocuViewWorkspace(close)
                else if(id=="vault") VaultWorkspace(close)
                else if(id=="compress") CompressWorkspace(close)
                else if(id=="formatswap") FormatSwapWorkspace(close)
                else if(id=="notes"&&notesRecoveryRequired) NotesScreen(close)
                else if(packageId!=null) SdkAppScreen(packageId,initialVideoUrl=launchVideo,launchKey=videoLaunchKey,initialPeer=launchPeer,initialNoteId=initialNote.takeIf{id=="notes"},openBrowser=if(id!="browser")::browse else null,openNote={note->fileNote=note},exit=close)
                else Column(Modifier.fillMaxSize()) { HomeHeader("App unavailable",back=close);Text("Install or update this app in Settings › Apps.",Modifier.padding(20.dp)) }
                }
            }
            route=="platform-setup" -> PlatformSetupScreen(done={platformSetup=false},messaging={setup=true})
            route=="setup" -> SetupScreen(exit={setup=false}, allow={permissions.launch(arrayOf(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.READ_CONTACTS))}, startMessaging={setup=false;if(!platformSetup)tab=0},completedLabel=if(platformSetup)"Back to setup"else"Start messaging")
            route=="settings:Apps" -> PackageCenter(false){settingsRoute=null}
            route=="settings:Games" -> PackageCenter(true){settingsRoute=null}
            route=="settings:Profile" -> ProfileScreen{settingsRoute=null}
            route=="settings:Backups" -> BackupsScreen{settingsRoute=null}
        }
    }
    }
    RightSlideRoute(catalogManagement) { section ->
        PackageCenter(games = section == "Games") { catalogManagement = null }
    }
    RightSlideRoute(fileNote){note->
        key("files-note",note) {
            if (!note.startsWith("write:") && notesRecoveryRequired) NotesScreen { fileNote=null }
            else SdkAppScreen("app.onklav.write",initialWriteId=note.removePrefix("write:"),exit={fileNote=null})
        }
    }
    RightSlideRoute(browserUrl){url->
        val generation=browserLaunch
        holder.SaveableStateProvider("browser-handoff"){SdkAppScreen("app.onklav.browser",initialWebUrl=url,launchKey=browserLaunch){if(browserLaunch==generation)browserUrl=null}}
    }
    gamePeer?.let{peer->ReferenceDialog("Play a Game with "+gameName.ifBlank{app.onklav.shared.displayPhone(peer)}.substringBefore(' '),{gamePeer=null},large=true,footer={DialogAction("Cancel"){gamePeer=null}}){
        Box(Modifier.fillMaxWidth().height(420.dp)){GamesCatalog(friendPeer=peer){}}
    }}
    settingsRoute?.takeUnless { it in setOf("Apps","Games","Profile","Backups") }?.let { SettingsDialogs(it) { settingsRoute = null } }
    OnKlavUpdateNotice(tab==2 && pageRoute==null && browserUrl==null && fileNote==null && gamePeer==null && settingsRoute==null && !appearance){settingsRoute="Check for updates"}
    if (appearance) AppearanceDialog { appearance = false }
    if(powerUnavailable)AlertDialog(onDismissRequest={powerUnavailable=false},title={Text("Power access")},text={Text(if(PowerAccessibilityService.enabled(context))"Android has not connected Power yet. Wait a moment and tap Power again."else"Enable OnKlav Power in setup to use Android’s power menu.")},confirmButton={TextButton(onClick={powerUnavailable=false;platformSetup=true}){Text("Open setup")}},dismissButton={TextButton(onClick={powerUnavailable=false}){Text("Cancel")}})
    ErrorDialog(error) { error = null }
}
