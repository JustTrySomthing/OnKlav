package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.onklav.home.R as HostR
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.*
import app.onklav.shared.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

internal fun JSONObject.nodes(key: String) = optJSONArray(key)?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
@Composable private fun rememberSdkScroll(slot:String,model:SdkAppModel):ScrollState {
    val scroll=key(slot) { rememberScrollState(model.scrollMemory.position(slot)) }
    // A departing layout may clamp its old ScrollState before disposal. Save while active.
    LaunchedEffect(slot,scroll) { snapshotFlow { scroll.value }.collect { model.scrollMemory.save(slot,it) } }
    return scroll
}
@Composable
fun SdkAppScreen(id: String, initialVideoUrl:String?=null, launchKey:Long=0, initialPeer:String?=null, initialWebUrl:String?=null,initialNoteId:String?=null,initialWriteId:String?=null,openBrowser:((String)->Unit)?=null,openNote:((String)->Unit)?=null,onFeedback:((JSONObject)->Unit)?=null,embeddedInHome:Boolean=false,onImmersiveChange:((Boolean)->Unit)?=null,exit: () -> Unit) {
    if (id == RealSocial.APP) {
        RealSocialScreen(exit, openBrowser)
        return
    }
    val application = LocalContext.current.applicationContext as android.app.Application
    val factory = remember(application) { object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == SdkAppModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return SdkAppModel(application) as T
        }
    } }
    val model: SdkAppModel = viewModel(key = "sdk-$id", factory = factory)
    LaunchedEffect(model.error,model.pending,model.busy,model.sessionUnavailable){onFeedback?.invoke(JSONObject().put("phase",if(model.error!=null)"action_error"else if(model.pending!=null)"waiting_for_device"else"opened").put("service",model.pending?.optString("service")?:JSONObject.NULL).put("busy",model.busy).put("session_unavailable",model.sessionUnavailable).put("error",if(model.error!=null)"App action failed; see the device screen"else JSONObject.NULL))}
    val context = LocalContext.current
    val toolbar = LocalTextToolbar.current
    val clipboard = remember(model) { GatedSdkClipboard(context,model) }
    val gatedToolbar = remember(model,toolbar) { object : TextToolbar {
        override val status get() = toolbar.status
        override fun hide() = toolbar.hide()
        override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
            val copy = model.clipboardAllowed("clipboard.write")
            val paste = model.clipboardAllowed("clipboard.read")
            toolbar.showMenu(rect, if(copy) onCopyRequested else null, if(paste) onPasteRequested else null, if(copy) onCutRequested else null, onSelectAllRequested)
        }
    } }
    @Suppress("DEPRECATION")
    CompositionLocalProvider(LocalClipboardManager provides clipboard, LocalClipboard provides clipboard.asClipboard, LocalTextToolbar provides gatedToolbar) {
        val configuration=LocalConfiguration.current
        val tags=List(configuration.locales.size()){configuration.locales[it].toLanguageTag()}
        CompositionLocalProvider(LocalLayoutDirection provides if(SdkLocalization.rtl(tags))LayoutDirection.Rtl else LayoutDirection.Ltr) { key(id) { SdkAppContent(id,model,exit,initialVideoUrl,launchKey,initialPeer,initialWebUrl,initialNoteId,initialWriteId,openBrowser,openNote,embeddedInHome,onImmersiveChange) } }
    }
}

/** Native text menus use the same live permission gate as explicit SDK services. */
@Suppress("DEPRECATION")
private class GatedSdkClipboard(context: Context, val model: SdkAppModel) : androidx.compose.ui.platform.ClipboardManager {
    private val native = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    override fun getText(): AnnotatedString? = if(model.clipboardAllowed("clipboard.read")) native.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.take(100000)?.let { AnnotatedString(it.toString()) } else null
    override fun hasText() = model.clipboardAllowed("clipboard.read") && native.hasPrimaryClip()
    override fun setText(annotatedString: AnnotatedString) { if(model.clipboardAllowed("clipboard.write")) native.setPrimaryClip(ClipData.newPlainText("OnKlav",annotatedString.text.take(100000))) }
    override fun getClip(): ClipEntry? = getText()?.let { ClipEntry(ClipData.newPlainText("OnKlav",it.text)) }
    override fun setClip(clipEntry: ClipEntry?) { clipEntry?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { setText(AnnotatedString(it.toString())) } }
    val asClipboard = object : androidx.compose.ui.platform.Clipboard {
        override suspend fun getClipEntry() = getClip()
        override suspend fun setClipEntry(clipEntry: ClipEntry?) = setClip(clipEntry)
        override val nativeClipboard: ClipboardManager get() = throw UnsupportedOperationException("Use OnKlav's clipboard service")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SdkAppContent(id: String, model: SdkAppModel, exit: () -> Unit, initialVideoUrl:String?, launchKey:Long,initialPeer:String?,initialWebUrl:String?,initialNoteId:String?,initialWriteId:String?,openBrowser:((String)->Unit)?,openNote:((String)->Unit)?,embeddedInHome:Boolean,onImmersiveChange:((Boolean)->Unit)?) {
    val context = LocalContext.current
    val focus=LocalFocusManager.current
    val textToolbar=LocalTextToolbar.current
    SdkFilesEffects(model,id,openNote)
    SdkDeviceEffects(model)
    SdkPrivateFilesEffects(model)
    var openingVideo by remember(id,initialVideoUrl,launchKey){mutableStateOf(initialVideoUrl!=null)}
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    val webPool = remember(model,id) { model.webPool }
    val selectedTab=model.frame.nodes("body").firstOrNull{it.optBoolean("tab")&&it.optBoolean("selected")}?.optString("id").orEmpty()
    val bodyScroll=rememberSdkScroll("body:"+model.frame.optString("scroll_key","body")+":"+selectedTab,model)
    val speech = remember(model,id) { SdkSpeechController(context,id,authorize={ widget -> model.speechComponent(widget);Unit }) }
    if(id=="app.onklav.radio"){
        LaunchedEffect(model){snapshotFlow{!model.busy&&model.pending==null&&model.frame.has("state")}.collect{ready->if(ready)model.restoreRadioView()}}
    }
    val epoch by AppSdk.epoch.collectAsState()
    LaunchedEffect(epoch) { speech.checkPermission() }
    DisposableEffect(speech) { onDispose { speech.close() } }
    var walkieRequest by remember { mutableStateOf<JSONObject?>(null) }
    var webRequest by remember { mutableStateOf<JSONObject?>(null) }
    var contactOpening by rememberSaveable { mutableStateOf<String?>(null) }
    val contactPicker=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p=model.pending?.takeIf {it.optString("token")==contactOpening && it.optString("service")=="contacts.pick"};contactOpening=null
        if(p!=null) scope.launch {
            try {
                if(result.resultCode!=android.app.Activity.RESULT_OK) model.reply(p,failure="Contact selection cancelled")
                else {
                    val uri=result.data?.data ?: error("Choose a contact with a phone number")
                    val selected=withContext(Dispatchers.IO) { SdkContacts.selected(context,uri) {model.authorizeNow(p);Unit} }
                    model.authorize(p);model.reply(p,selected)
                }
            } catch(e:Exception) { if (e is CancellationException) throw e;model.reply(p,failure=e.message ?: "Contact could not be opened")}
        }
    }
    var imageOpening by rememberSaveable { mutableStateOf<String?>(null) }
    val imagePicker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()) { uri ->
        val p=model.pending?.takeIf {it.optString("token")==imageOpening};imageOpening=null
        if(p!=null) scope.launch {
            try {
                if(uri==null) model.reply(p,failure="Image Selection Cancelled")
                else {
                    require(uri.scheme=="content") {"Choose a Picture from Files"}
                    val bytes=withContext(Dispatchers.IO) { model.authorizeNow(p);context.contentResolver.openInputStream(uri)?.use { SdkDrawingImages.normalize(SdkDrawingImages.read(it) {model.authorizeNow(p);Unit}) } ?: error("Could Not Open Image") }
                    model.authorize(p);model.reply(p,JSONObject().put("image",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)))
                }
            } catch(e:Exception) { if (e is CancellationException) throw e;model.reply(p,failure=e.message ?: "Image Could Not Be Opened")}
        }
    }
    var previouslyOpened by rememberSaveable(id){mutableStateOf(false)}
    var handledLaunchKey by rememberSaveable(id){mutableStateOf<Long?>(null)}
    LaunchedEffect(id,initialVideoUrl,launchKey,initialPeer,initialWebUrl,initialNoteId) {
        try {
            model.open(id,recovering=previouslyOpened)
            if(model.error==null){
                previouslyOpened=true
                // Persist only the consumed launch marker, never an action token or app data.
                if(handledLaunchKey!=launchKey){
                    handledLaunchKey=launchKey
                    if(id=="app.onklav.notes"&&initialNoteId!=null)model.openNoteRecord(initialNoteId)
                    if(id=="app.onklav.browser"&&initialWebUrl!=null)model.openWebLink(initialWebUrl)
                    if(id=="app.onklav.walkie"&&initialPeer!=null)model.chooseWalkieRecipient(initialPeer)
                    if(id=="app.onklav.youtube"&&initialVideoUrl!=null)MessageVideoLinks.parse(initialVideoUrl)?.let{model.openMessageVideo(it,launchKey)}
                }
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){model.error=e.message?:"Could not open this file"}finally{openingVideo=false}
    }
    LaunchedEffect(id,lifecycle,model.carrierVoiceEligible) {
        if(model.carrierVoiceEligible&&MessagingTransport.carrierEnabled(context)) {
            sdkForegroundPolling(lifecycle.lifecycle,watch={SdkWalkieTransport.watch(context)},refresh={model.refreshWalkie()})
        }
    }
    DisposableEffect(lifecycle, model) { val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_STOP) { speech.onBackground();model.releaseDeviceLeases();model.flush() } }; lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer); model.releaseDeviceLeases();model.flush() } }
    val configuration=LocalConfiguration.current
    val languageTags=List(configuration.locales.size()){configuration.locales[it].toLanguageTag()}
    val frame = remember(model.frame,languageTags,epoch){SdkLocalization.frame(if(id=="app.onklav.files")FolderVaultFrames.sanitize(context,model.frame)else model.frame,languageTags).also{display->for(field in listOf("title","menu_title","drawer_title"))if(display.has(field))display.put(field,includedAppFrameTitle(id,display.optString(field)))}}
    val external=AppSdk.bundledApps.values.none { it.first==id }
    var hiddenMenuEpoch by remember(id){mutableLongStateOf(-1L)}
    var hiddenDrawerEpoch by remember(id){mutableLongStateOf(-1L)}
    var hostClose by remember(id){mutableStateOf(false)}
    var hostActions by remember(id){mutableStateOf(false)}
    fun dismissMenu(action:String){val confirmation=model.frame.optJSONObject("confirm");if(confirmation!=null&&action!=confirmation.optString("cancel"))return;if(confirmation==null&&action!=model.frame.optString("menu_close","close.menu"))return;if(!model.frame.optBoolean("menu_open")&&model.frame.optJSONObject("confirm")==null)return;if(external)hiddenMenuEpoch=model.userActionEpoch;model.action(action,fromHostDismiss=true)}
    fun dismissDrawer(action:String){if(!model.frame.optBoolean("drawer_open"))return;if(external)hiddenDrawerEpoch=model.userActionEpoch;model.action(action,fromHostDismiss=true)}
    val menuOpen=frame.optBoolean("menu_open")&&frame.optJSONObject("confirm")==null&&(!external||hiddenMenuEpoch!=model.userActionEpoch)
    val confirmOpen=frame.optJSONObject("confirm")!=null&&(!external||hiddenMenuEpoch!=model.userActionEpoch)
    val drawerOpen=frame.optBoolean("drawer_open")&&(!external||hiddenDrawerEpoch!=model.userActionEpoch)
    var retainedDrawer by remember(id){mutableStateOf("{}")}
    if(drawerOpen){
        val snapshot=JSONObject().apply{listOf("drawer","drawer_title","drawer_max_width","drawer_width","drawer_close","drawer_scroll_key").forEach{key->if(frame.has(key))put(key,frame.get(key))}}.toString()
        if(snapshot!=retainedDrawer)SideEffect{retainedDrawer=snapshot}
    }
    val drawerFrame=if(drawerOpen)frame else JSONObject(retainedDrawer)
    val pending = model.pending
    SdkDeviceServices(pending,model)
    SdkHandoffEffects(pending,model)
    LaunchedEffect(pending?.optString("token")) {
        if (pending != null && !handoffService(pending.optString("service")) && !deviceService(pending.optString("service")) && !pending.optString("service").startsWith("library.") && !pending.optString("service").startsWith("blobs.") && pending.optString("service")!="camera.capture" && !pending.optString("service").startsWith("notifications.") && model.launchPending(pending.getString("token"))) {
            try {
                val operation = model.authorize(pending)
                val args = operation.child("args")
                when (operation.getString("service")) {
                    "calendar.expand" -> { val result=withContext(Dispatchers.Default){SdkRecurrence.expand(args)};model.authorizeNow(pending);model.reply(pending,result) }
                    "clock.read" -> { model.authorizeNow(pending);model.reply(pending,SdkClock.snapshot(context)) }
                    "jobs.schedule", "jobs.status", "jobs.cancel", "jobs.list", "downloads.start" -> {val result=withContext(Dispatchers.IO){SdkJobs.execute(context,model.authorizeNow(pending)){model.authorizeNow(pending);Unit}};model.reply(pending,result)}
                    "ebooks.search" -> {val result=SdkEbooks.search(context,pending.getString("token")){model.authorizeNow(pending)};model.reply(pending,result)}
                    "ebooks.acquire" -> {val result=SdkEbooks.acquire(context,pending.getString("token"),{model.authorizeNow(pending)},{op,args->model.privateFileNow(pending,op,args)});model.reply(pending,result)}
                    "podcasts.search" -> {val result=SdkPodcasts.search(context,pending.getString("token")){model.authorizeNow(pending)};model.reply(pending,result)}
                    "podcasts.downloads" -> {model.reply(pending,SdkPodcasts.saved(context,pending.getString("token")){model.authorizeNow(pending)})}
                    "podcasts.download" -> {model.reply(pending,SdkPodcasts.download(context,pending.getString("token")){model.authorizeNow(pending)})}
                    "podcasts.delete" -> {val result=SdkPodcasts.delete(context,pending.getString("token")){model.authorizeNow(pending)};SdkMedia.podcastDownloadRemoved(operation.getJSONObject("owner").getString("id"),args.getString("episode"));model.reply(pending,result)}
                    "podcasts.feed" -> {val result=SdkPodcasts.feed(context,pending.getString("token"),model.frame.optJSONObject("state")?.optJSONObject("show")?.optString("title").orEmpty()){model.authorizeNow(pending)};model.reply(pending,result)}
                    "podcasts.play" -> {val prepared=SdkPodcasts.prepare(context,pending.getString("token")){model.authorizeNow(pending)};val result=SdkPodcasts.start(context,prepared){model.authorizeNow(pending);Unit};model.reply(pending,result)}
                    "radio.search" -> {val result=SdkRadio.search(context,pending.getString("token")){model.authorizeNow(pending)};model.reply(pending,result)}
                    "radio.play" -> {val prepared=SdkRadio.prepare(context,pending.getString("token")){model.authorizeNow(pending)};val result=SdkRadio.start(context,prepared){model.authorizeNow(pending);Unit};model.reply(pending,result)}
                    "media.play", "media.status", "media.control" -> {val result=SdkMedia.execute(context,operation){model.authorizeNow(pending);Unit};model.reply(pending,result)}
                    "accounts.connect", "accounts.list", "accounts.refresh", "accounts.disconnect" -> {val value=SdkAccounts.execute(context,pending.getString("token"),{model.authorizeNow(pending)});model.reply(pending,value)}
                    "communications.walkie" -> {SdkWalkieTransport.checkSetup(context);walkieRequest=JSONObject(pending.toString()).put("args",args)}
                    "contacts.pick" -> {contactOpening=pending.getString("token");contactPicker.launch(SdkContacts.intent())}
                    "images.import" -> {imageOpening=pending.getString("token");imagePicker.launch(arrayOf("image/*"))}
                    "images.text" -> {val size=withContext(Dispatchers.IO) {SdkDrawingImages.textSize(args.child("mark"),operation.child("document"))};model.reply(pending,size)}
                    "images.export" -> {
                        val bytes=withContext(Dispatchers.IO) {SdkDrawingImages.render(operation.child("document"))};model.authorize(pending)
                        val title=operation.child("document").optString("title","Annotated Image").replace(Regex("[^\\p{L}\\p{N} ._-]"),"_").take(60).ifBlank {"Annotated Image"}+".jpg"
                        if(args.getString("mode")=="files") {
                            AppSdk.staging.withLock {withContext(Dispatchers.IO){java.io.File(context.filesDir,"content-import.tmp").writeBytes(bytes)};try{val saved=model.files(pending,"save_image",JSONObject().put("name",title));model.reply(pending,saved)}finally{java.io.File(context.filesDir,"content-import.tmp").delete()}}
                        } else {
                            val uri=withContext(Dispatchers.IO) {ExportProvider.stage(context,title,bytes,"jpg")};model.authorize(pending)
                            if(args.getString("mode")=="copy") (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newUri(context.contentResolver,title,uri))
                            else context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=ClipData.newRawUri(title,uri) },"Share Image"))
                            model.reply(pending)
                        }
                    }
                    "http.request" -> {val result=SdkHttpService.fetchWithAccount(pending.getString("token"),authorize={model.authorize(pending)},context=context,accountAuthorize={model.authorizeNow(pending)});model.reply(pending,result)}
                    "feeds.fetch" -> { val result=SdkFeedService.fetch(context,pending.getString("token"),args) { model.authorize(pending);Unit }; model.reply(pending,result) }
                    "images.clear" -> { withContext(Dispatchers.IO) { SdkThumbnails.clear(context,id) }; model.reply(pending) }
                    "links.external" -> { val url=SdkWebPolicy.external(args.getString("url"));if(openBrowser!=null&&args.optString("target")!="youtube"){model.reply(pending);openBrowser(url)}else{val intent=Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url));if(args.optString("target")=="youtube")intent.setPackage("com.google.android.youtube");try{context.startActivity(intent)}catch(e:android.content.ActivityNotFoundException){error(if(args.optString("target")=="youtube")"YouTube App Is Not Installed"else"No Browser Is Installed")};model.reply(pending)} }
                    "web.open" -> { val url=SdkWebPolicy.external(args.getString("url"));if(openBrowser!=null){model.reply(pending);openBrowser(url)}else{SdkFeedService.address(url);webRequest=pending} }
                    "secrets.get","secrets.set","secrets.delete" -> { val result=withContext(Dispatchers.IO){SdkSecrets.execute(context){model.authorizeNow(pending)}};model.reply(pending,result) }
                    "clipboard.read" -> { val text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.take(100000)?.toString().orEmpty(); model.reply(pending,text) }
                    "clipboard.write" -> { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(frame.optString("title"),args.optString("text"))); model.reply(pending) }
                    "files.export" -> { val saved=model.files(pending,"save_text");model.reply(pending,saved) }
                    "share.text" -> { if(args.optBoolean("inline")) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,args.optString("text")),"Share Page"));model.reply(pending) } else { val uri = withContext(Dispatchers.IO) { ExportProvider.stage(context, args.optString("name","Document") + ".txt", args.optString("text").toByteArray(Charsets.UTF_8)) }; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share Text")); model.reply(pending) } }
                }
            } catch(e: Exception) { if (e is CancellationException){if(pending.optString("service").startsWith("radio."))withContext(NonCancellable){model.reply(pending,failure="Radio request cancelled")};throw e}; model.reply(pending,failure=e.message ?: "Host action failed") }
        }
    }
    val finishExit:()->Unit={previouslyOpened=false;handledLaunchKey=null;exit()}
    fun back(system:Boolean=false) { focus.clearFocus(force=true);textToolbar.hide();when { drawerOpen -> dismissDrawer(frame.optString("drawer_close","close.drawer")); menuOpen -> dismissMenu(frame.optString("menu_close","close.menu")); confirmOpen -> dismissMenu(frame.getJSONObject("confirm").getString("cancel")); !system -> model.close(finishExit); model.componentBack?.invoke()==true -> Unit; id=="app.onklav.notes" && initialNoteId!=null -> model.close(finishExit); system && webPool.back() -> Unit; frame.has("back_action") -> model.action(frame.getString("back_action")); else -> model.close(finishExit) } }
    BackHandler { back(system=true) }
    LaunchedEffect(frame.optBoolean("close_requested")) { if(frame.optBoolean("close_requested")) model.close(finishExit) }
    val vaultFolder=if(id=="app.onklav.files")frame.child("state").optString("folder")else""
    // Synchronous policy lookup masks a stale guest frame in the same recomposition.
    val vaultStatus=remember(vaultFolder,epoch,frame){if(vaultFolder.isNotEmpty())runCatching{AppSdk.folderVault(context,"status",vaultFolder)}.getOrNull()else null}
    model.vaultRequest?.let{request->FolderVaultDialog(request,{model.vaultRequest=null}){
        model.vaultRequest=null;val target=request.optString("navigate");if(target.isNotEmpty())model.action("entry:$target")else scope.launch{model.refreshFiles()}
    }}
    val landscape=androidx.compose.ui.platform.LocalConfiguration.current.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val providerVideo=frame.nodes("body").any{it.optString("provider")=="youtube"&&SdkVideoPolicy.video(it.optString("url")).isNotEmpty()}
    MediaAutoRotation(providerVideo)
    val cameraToolbar=remember(id){CameraToolbarState()}
    val immersiveCamera=id=="app.onklav.camera"&&landscape&&cameraToolbar.active&&!cameraToolbar.gallery
    val immersiveVideo=landscape&&frame.optBoolean("hide_header_landscape")&&providerVideo
    SideEffect { onImmersiveChange?.invoke(immersiveVideo||immersiveCamera) }
    DisposableEffect(onImmersiveChange) { onDispose { onImmersiveChange?.invoke(false) } }
    ImmersiveMediaMode(immersiveVideo||immersiveCamera)
    val writeToolbar=remember(id){WriteToolbarState()}
    val photoToolbar=remember(id){PhotoToolbarState()}
    val videoToolbar=remember(id){VideoToolbarState()}
    var mapsSavedOpen by rememberSaveable { mutableStateOf(false) }
    var browserDownloadsOpen by rememberSaveable(id) { mutableStateOf(false) }
    if(id=="app.onklav.browser"&&browserDownloadsOpen) {
        BrowserDownloadsDialog(model.browserDownloads){browserDownloadsOpen=false}
    }
    val panel=!external&&frame.optString("presentation")=="panel"
    Surface(Modifier.fillMaxSize(), color = if(panel)Color.Black.copy(alpha=.55f)else MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().then(if(immersiveVideo||immersiveCamera||embeddedInHome)Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()).testTag("sdk-app-screen")) {
            if(!panel&&!immersiveVideo&&!immersiveCamera) SdkFrameHeader(if(id=="app.onklav.youtube")youtubeDownloadHeader(frame)else if(id=="app.onklav.files")JSONObject(frame.toString()).put("header_actions",0).put("title",if(frame.child("state").optBoolean("external_mode"))"Android Files"else frame.child("state").optString("title").replace("OnKlav System Files","OnKlave Files").ifBlank{"Files"})else frame,model.busy||pending!=null,webPool.progress,showBack=!embeddedInHome,
                titleContent=if(id=="app.onklav.youtube") {{ modifier -> YoutubeHeaderMark(modifier) }} else null,
                afterAction={action->
                if(id=="app.onklav.youtube"&&action=="download") YoutubePreviewAction(model.youtubePreviews,model::toggleYoutubePreviews)
                if(id=="app.onklav.browser"&&action=="bookmarks") BrowserDownloadsAction(model.browserDownloads.active){browserDownloadsOpen=true}
            },onBack={back()},onAction={action->speech.stop();if(id=="app.onklav.youtube"&&action=="download")webPool.downloadRequest++ else model.action(action)}) {
                if(id=="app.onklav.files") FilesHeaderControls(model)
                if(id=="app.onklav.camera") CameraHeaderButtons(cameraToolbar)
                if(id=="app.onklav.write") WriteHeaderButtons(writeToolbar)
                if(id=="app.onklav.files"&&vaultStatus?.let{it.optBoolean("encrypted")}==true)IconButton(enabled=!model.busy,onClick={model.vaultRequest=vaultStatus}){Icon(Icons.Outlined.Lock,if(vaultStatus.optBoolean("encrypted"))"Folder protection"else"Protect folder")}
                if(id=="app.onklav.photo") PhotoHeaderButtons(photoToolbar)
                if(id=="app.onklav.videoeditor") VideoHeaderButtons(videoToolbar)
                if(id=="app.onklav.maps") MapsFolderButton{mapsSavedOpen=!mapsSavedOpen}
                if(id !in setOf("app.onklav.clock","app.onklav.flashlight","app.onklav.camera","app.onklav.email","app.onklav.videoeditor","app.onklav.maps","app.onklav.audioeditor","app.onklav.write","app.onklav.photo","app.onklav.browser","app.onklav.files","app.onklav.reader","app.onklav.notes","app.onklav.annotation","app.onklav.radio","app.onklav.podcasts","app.onklav.audiobooks","app.onklav.ebooks","app.onklav.youtube","app.onklav.calc","app.onklav.convert","app.onklav.weather")) Box {
                    CompactAction(Icons.Outlined.MoreVert,stringResource(HostR.string.sdk_app_controls)){hostActions=true}
                    DropdownMenu(hostActions,{hostActions=false}){
                        DropdownMenuItem(text={Text(stringResource(HostR.string.sdk_close_app))},onClick={hostActions=false;hostClose=true})
                        val actions=frame.nodes("actions")
                        if(actions.size>2||actions.take(2).any{it.has("long_action")})HorizontalDivider()
                        SdkAppActionItems(actions,model.busy||pending!=null,toolbarOverflow=true){action->hostActions=false;model.action(action)}
                    }
                }
            }
            if(model.fileStatus!=null&&id!="app.onklav.files")Column(Modifier.fillMaxWidth().padding(horizontal=12.dp)){
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(model.fileStatus.orEmpty(),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            }
            if(model.recoveryNotice&&!immersiveVideo)Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                Text(stringResource(HostR.string.sdk_restarted),Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite},fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={model.dismissRecoveryNotice()}){Text(stringResource(HostR.string.sdk_dismiss))}
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val compactEditor=maxHeight<360.dp && frame.nodes("body").any{it.optString("kind")=="editor"} && frame.nodes("body").none{it.optString("kind") in setOf("web","canvas","pdf_viewer","map_viewer")}
                val pinKeypad=frame.nodes("body").any{it.optBoolean("keypad")}&&maxHeight>=600.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
                val scrollBody=!pinKeypad&&frame.optBoolean("scroll_body")&&frame.nodes("body").none{it.optString("kind") in setOf("web","canvas","pdf_viewer","map_viewer")}
                val pinnedTabs=frame.nodes("body").filter{it.optBoolean("tab")};val nativeLibrary=frame.nodes("body").any{it.optBoolean("audiobook")}
                val dockedPlayer=frame.nodes("body").firstOrNull{it.optString("kind")=="media_player"&&it.optBoolean("dock")}
                val rowColumns=(maxWidth.value/(160f*LocalDensity.current.fontScale.coerceIn(1f,1.5f))).toInt().coerceIn(1,3)
                if(vaultStatus?.optBoolean("locked")==true)FolderLockedPane(vaultStatus){model.vaultRequest=vaultStatus}
                else if(id=="app.onklav.files" && !model.sessionUnavailable) FilesWorkspaceBody(model)
                else if(id=="app.onklav.clock" && !model.busy && model.error==null && !model.sessionUnavailable) ClockWorkspace()
                else if(id=="app.onklav.flashlight" && !model.busy && model.error==null && !model.sessionUnavailable) FlashlightWorkspace()
                else if(id=="app.onklav.camera" && !model.busy && model.error==null && !model.sessionUnavailable) CameraWorkspace(model,cameraToolbar,onClose={back()})
                else if(id=="app.onklav.email" && !model.busy && model.error==null && !model.sessionUnavailable) EmailWorkspace()
                else if(id=="app.onklav.videoeditor" && !model.busy && model.error==null && !model.sessionUnavailable) VideoEditorWorkspace(toolbar=videoToolbar)
                else if(id=="app.onklav.maps" && !model.busy && model.error==null && !model.sessionUnavailable) MapsWorkspace(mapsSavedOpen){mapsSavedOpen=it}
                else if(id=="app.onklav.audioeditor" && !model.busy && model.error==null && !model.sessionUnavailable) AudioEditorWorkspace()
                else if(id=="app.onklav.write" && !model.busy && model.error==null && !model.sessionUnavailable) WriteWorkspace(writeToolbar,initialWriteId,onClose={back()})
                else if(id=="app.onklav.photo" && !model.busy && model.error==null && !model.sessionUnavailable) PhotoEditorWorkspace(photoToolbar,onClose={back()})
                else if(id=="app.onklav.ebooks" && frame.nodes("body").any{it.optString("id")=="reading.page"} && !model.sessionUnavailable) EbookReaderWorkspace(model){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                else if(id=="app.onklav.calc" && !model.sessionUnavailable) CalcWorkspace(model)
                else if(id=="app.onklav.convert" && !model.sessionUnavailable) ConvertWorkspace(model){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                else if(openingVideo) Text("Opening Video…",Modifier.padding(20.dp))
                else Column((if(panel)Modifier.align(Alignment.Center).widthIn(max=380.dp).fillMaxWidth(.94f).heightIn(max=maxHeight*.84f).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(24.dp)).padding(horizontal=24.dp,vertical=20.dp)else Modifier.fillMaxSize().then(if(frame.optBoolean("full_bleed")) Modifier else Modifier.padding(horizontal=12.dp,vertical=8.dp))).then(if(dockedPlayer==null&&pinnedTabs.isEmpty()&&!nativeLibrary&&!pinKeypad&&(scrollBody || compactEditor || frame.nodes("body").none { it.optString("kind") in setOf("editor","web","canvas","pdf_viewer","map_viewer") })) Modifier.verticalScroll(bodyScroll) else Modifier).testTag("sdk-app-body")) {
                    if(panel) {
                        var more by remember{mutableStateOf(false)}
                        Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Spacer(Modifier.width(40.dp))
                            Text(frame.optString("title"),Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontSize=23.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
                            Box{CompactAction(Icons.Outlined.MoreVert,"More actions"){more=true};DropdownMenu(more,{more=false}){SdkAppActionItems(frame.nodes("actions"),model.busy||pending!=null){action->more=false;model.action(action)};DropdownMenuItem(text={Text("Close")},onClick={more=false;back()})}}
                        }
                    }
                    if(id=="app.onklav.files"&&frame.child("state").optString("folder").isEmpty())FileRecoveryPanel()
                    if (dockedPlayer != null) {
                        val fixedTabs=frame.nodes("body").filter{it.optBoolean("tab")}
                        if (fixedTabs.isNotEmpty()) {
                            SdkBodyNodes(fixedTabs,3,false,false){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                        }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(bodyScroll)) {
                            SdkBodyNodes(frame.nodes("body").filter{it!==dockedPlayer&&!it.optBoolean("tab")},rowColumns,false,true){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                        }
                        HorizontalDivider()
                        SdkWidget(dockedPlayer,frame.optString("revision_key"),model,speech,webPool,Modifier.padding(vertical=8.dp))
                    } else if (pinnedTabs.isNotEmpty()) {
                        SdkBodyNodes(pinnedTabs,3,false,false){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(bodyScroll)) {
                            SdkBodyNodes(frame.nodes("body").filter{!it.optBoolean("tab")},rowColumns,false,true){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                        }
                    } else {
                        SdkBodyNodes(frame.nodes("body"),rowColumns,compactEditor,scrollBody,pinKeypad){node,modifier->SdkWidget(node,frame.optString("revision_key"),model,speech,webPool,modifier)}
                    }
                    if(frame.length()==0) { Text("Opening…",fontSize=16.sp) }
                    if(id=="app.onklav.files"&&(model.busy||pending!=null)){LinearProgressIndicator(Modifier.fillMaxWidth());Text(model.fileStatus?:"Working with Files…",fontSize=13.sp)}
                    if(pending?.optString("service")=="accounts.connect") { LinearProgressIndicator(Modifier.fillMaxWidth());ChoiceButton("Cancel Sign-in",outlined=false) { SdkAccounts.cancel(pending.getString("token"));scope.launch { model.reply(pending,failure="Account connection cancelled") } } }
                    if(pending?.optString("service") in setOf("ebooks.search","ebooks.acquire","radio.search","radio.play","podcasts.search","podcasts.feed","podcasts.play","podcasts.download")) { LinearProgressIndicator(Modifier.fillMaxWidth());ChoiceButton("Cancel request",outlined=false){val p=pending!!;SdkRadio.cancel(p.getString("token"));SdkRadio.cancel("podcast-"+p.getString("token"));if(p.optString("service")=="podcasts.download")SdkPodcasts.cancelDownload(model.appIdentity,p.getJSONObject("args").getString("episode"),p.getString("token"))else SdkPodcasts.cancel(p.getString("token"));SdkEbooks.cancel(p.getString("token"));scope.launch{model.reply(p,failure="Request cancelled")}} }
                    if(pending?.optString("service")=="feeds.fetch") { LinearProgressIndicator(Modifier.fillMaxWidth());ChoiceButton("Stop Refresh",outlined=false) { SdkFeedService.cancel(pending.getString("token"));scope.launch { model.reply(pending,failure="Refresh stopped") } } }
                }
                androidx.compose.animation.AnimatedVisibility(drawerOpen, enter = fadeIn(tween(220)), exit = fadeOut(tween(360))) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopEnd) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.4f)).clickable { dismissDrawer(drawerFrame.optString("drawer_close","close.drawer")) })
                        SdkDrawerContent(drawerFrame,rememberSdkScroll("drawer:"+drawerFrame.optString("drawer_scroll_key",drawerFrame.optString("drawer_title")),model),Modifier.animateEnterExit(enter=slideInHorizontally(tween(420,easing=androidx.compose.animation.core.FastOutSlowInEasing)){it},exit=slideOutHorizontally(tween(360,easing=androidx.compose.animation.core.FastOutSlowInEasing)){it}),onClose={dismissDrawer(drawerFrame.optString("drawer_close"))}){node->SdkWidget(node,"drawer",model,speech,webPool)}
                    }
                }
            }
        }
    }
    val creatingFolder=menuOpen&&id=="app.onklav.files"&&frame.child("state").optString("dialog")=="new"
    if(creatingFolder)FilesCreateFolderDialog(model){dismissMenu("menu.close")}
    if(menuOpen&&!creatingFolder) ReferenceDialog(frame.optString("menu_title",frame.optString("title")), { dismissMenu(frame.optString("menu_close","close.menu")) }, footer = {if(external)DialogAction("Cancel"){dismissMenu(frame.optString("menu_close","close.menu"))}}) {
        BoxWithConstraints { val menuWidth=maxWidth;Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(id=="app.onklav.radio"&&frame.optString("menu_title")=="Radio settings")SdkRadioVisualizationSettings()
            val nodes=frame.nodes("menu");var index=0
            while(index<nodes.size) {
                val node=nodes[index];val row=node.optString("row")
                if(row.isNotEmpty()) {
                    val group=nodes.drop(index).takeWhile {it.optString("row")==row};index+=group.size
                    group.chunked(if(menuWidth<380.dp||LocalDensity.current.fontScale>1.2f)1 else 2).forEach{cells->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {cells.forEach{SdkWidget(it,"menu",model,speech,webPool,Modifier.weight(1f))}}}
                } else {SdkWidget(node,"menu",model,speech,webPool);index++}
            }
            webPool.findStatus?.let { Text(it,Modifier.padding(vertical=8.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }}
    }
    frame.optJSONObject("confirm")?.takeIf{confirmOpen}?.let { c -> ReferenceDialog(c.optString("title"), { dismissMenu(c.getString("cancel")) }, footer = {
        DialogAction("Cancel") { dismissMenu(c.getString("cancel")) }
        DialogAction(c.optString("accept_label","Delete"),destructive=true) { model.action(c.getString("accept")) }
    }) { Text(c.optString("text"),Modifier.padding(20.dp)) } }
    if(hostClose)ReferenceDialog("Close App?",{hostClose=false},footer={
        DialogAction("Cancel"){hostClose=false}
        DialogAction("Close App",destructive=true){hostClose=false;model.closeFromHost(finishExit)}
    }){Text("Unsaved changes may be lost.",Modifier.padding(20.dp))}
    webRequest?.let { p -> SdkPublisherPage(p.child("args").optString("url"),appId=id,clipboard=model::clipboardAllowed,authorize={ model.authorize(p);Unit },close={ webRequest=null;scope.launch { model.reply(p) } }) }
    walkieRequest?.let {p ->
        ReferenceDialog("Walkie Talkie", {walkieRequest=null;scope.launch {model.reply(p,failure="Connection cancelled. No invitation was sent.")}}, footer={
            DialogAction("Cancel"){walkieRequest=null;scope.launch {model.reply(p,failure="Connection cancelled. No invitation was sent.")}}
            DialogAction("Continue",primary=true){walkieRequest=null;scope.launch {try {val out=withContext(Dispatchers.IO){model.confirmWalkieNow(p)};model.reply(p,out)}catch(e:Exception){ if (e is CancellationException) throw e;model.reply(p,failure=e.message ?: "Connection failed")}}}
        }) {Text(if(p.child("args").optString("operation")=="retry")"Retry the voice clip to ${p.child("args").optString("peer")}? If it already arrived, another app may show a duplicate."else"Start a Walkie Talkie session with ${p.child("args").optString("peer")}?",Modifier.padding(20.dp))}
    }
    if(model.accessNotice)ReferenceDialog("App access changed",{model.keepVisibleDraft()},footer={
        DialogAction("Keep draft here"){model.keepVisibleDraft()}
        DialogAction("Reopen",destructive=true){scope.launch{model.reopenDiscardingDraft()}}
    }){Text("This session is no longer available. The app may have been updated, removed, or had its permissions changed. Your visible work has been kept here. Review access in Settings > Apps. Reopening loads saved data and discards unsaved changes.",Modifier.padding(20.dp))}
    ErrorDialog(if(model.sessionUnavailable)null else model.error) { model.error = null }
}

@Composable internal fun SdkActionRow(items:List<JSONObject>,render:@Composable (JSONObject,Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact=maxWidth<48.dp*items.size
        val gap=((maxWidth-48.dp*items.size)/(items.size-1).coerceAtLeast(1)).coerceIn(0.dp,8.dp)
        Row(Modifier.fillMaxWidth().then(if(compact)Modifier.horizontalScroll(rememberScrollState())else Modifier),horizontalArrangement=Arrangement.spacedBy(gap)) {items.forEach {render(it,if(compact)Modifier.width(48.dp)else Modifier.weight(1f))}}
    }
}

/** Shared responsive body layout, also used by the desktop native preview test host. */
@Composable internal fun ColumnScope.SdkBodyNodes(nodes:List<JSONObject>,rowColumns:Int,compactEditor:Boolean,scrollBody:Boolean,render:@Composable (JSONObject,Modifier)->Unit){
    SdkBodyNodes(nodes,rowColumns,compactEditor,scrollBody,false,render)
}
@Composable internal fun ColumnScope.SdkBodyNodes(nodes:List<JSONObject>,rowColumns:Int,compactEditor:Boolean,scrollBody:Boolean,pinKeypad:Boolean,render:@Composable (JSONObject,Modifier)->Unit){
    val groups=mutableListOf<List<JSONObject>>()
    var index=0
    while(index<nodes.size){
        val row=nodes[index].optString("row")
        val group=if(row.isNotEmpty())nodes.drop(index).takeWhile{it.optString("row")==row}else if(nodes[index].optString("kind")=="select"&&nodes[index].optBoolean("compact"))nodes.drop(index).takeWhile{it.optString("kind")=="select"&&it.optBoolean("compact")}.take(2)else listOf(nodes[index])
        groups+=group;index+=group.size
    }
    var keypadStarted=false
    groups.forEach{group->
        val node=group.first()
        if(pinKeypad&&!keypadStarted&&node.optBoolean("keypad")){Spacer(Modifier.weight(1f));keypadStarted=true}
        key(node.optString("id"),node.optString("kind")){
            if(node.optString("row").isNotEmpty()||group.size>1){
                if(group.all{it.optString("kind")=="icon"})SdkActionRow(group){cell,modifier->key(cell.optString("id")){render(cell,modifier)}}
                else group.chunked(if(group.all{it.optBoolean("keypad")})4 else if(group.all{it.optBoolean("tab")})3 else if(group.all{it.optString("kind")=="select"&&it.optBoolean("compact")})2 else rowColumns.coerceIn(1,3)).forEach{cells->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        cells.forEach{cell->key(cell.optString("id"),cell.optString("kind")){
                            render(cell,if(cell.optBoolean("compact")&&cell.optString("kind")!="select")Modifier.width(48.dp)else Modifier.weight(1f))
                        }}
                    }
                }
            }else render(node,if((compactEditor||scrollBody)&&node.optString("kind")=="editor")Modifier.heightIn(min=160.dp,max=280.dp)else if(node.optString("kind")in setOf("editor","web","canvas","pdf_viewer","map_viewer"))Modifier.weight(1f)else Modifier)
        }
    }
}

@Composable internal fun SdkWidget(node: JSONObject, revision: String, model: SdkAppModel, speech: SdkSpeechController, webPool:SdkWebPool, modifier: Modifier = Modifier) {
    val id=node.getString("id")
    val interactive=!model.busy&&model.pending==null&&node.optBoolean("enabled",true)
    if(!node.optBoolean("enabled",true)&&node.optString("kind") in setOf("web","canvas","speech","speech_settings","voice_capture","voice_clip","media_record","image_record","media_player","pdf_viewer","map_viewer")){
        Text(node.optString("text").ifBlank{"Unavailable"},modifier.fillMaxWidth().padding(12.dp).testTag("sdk-$id").semantics {disabled()},color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    when(node.getString("kind")) {
        "pdf_viewer" -> SdkPdfViewer(node,model,modifier)
        "map_viewer" -> SdkMapViewer(node,model,modifier)
        "media_player" -> if(node.optBoolean("audiobook")) SdkAudioBooksComponent(node,model,modifier) else SdkPlayerComponent(node,model,modifier)
        "voice_capture","voice_clip" -> SdkVoiceComponent(node,model,modifier)
        "canvas" -> SdkDrawingComponent(node,model,modifier)
        "image_record" -> SdkImageRecord(node,model,modifier)
        "slider" -> { var value by remember(id,node.optInt("value")) {mutableFloatStateOf(node.optInt("value").toFloat())};Column(modifier) {Text(node.optString("text"),fontSize=14.sp);Slider(value=value,onValueChange={value=it},onValueChangeFinished={model.action(id,value.toInt().toString())},valueRange=1f..12f,steps=10,enabled=interactive,modifier=Modifier.testTag("sdk-$id").semantics {contentDescription=node.optString("text")})} }
        "palette" -> Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(0xffffdd41L to "Yellow",0xff36bd7aL to "Green",0xffe94242L to "Red",0xff000000L to "Black",0xffffffffL to "White").forEach { (color,label)->Surface(onClick={model.action(id,color.toString())},modifier=Modifier.size(48.dp).testTag("sdk-color-$label").semantics {contentDescription=label;selected=node.optLong("value")==color;role=Role.Button},shape=RoundedCornerShape(12.dp),color=Color(color.toInt()),border=BorderStroke(2.dp,MaterialTheme.colorScheme.outline),enabled=interactive) {Box(Modifier,contentAlignment=Alignment.Center) {if(node.optLong("value")==color) Text("✓",color=if(color==0xff000000L) Color.White else Color.Black)}} } }
        "media_record" -> SdkMediaRecord(node,model,modifier)
        "web" -> SdkWebComponent(node,model,webPool,modifier)
        "icon" -> Box(modifier,contentAlignment=Alignment.Center) {
            val enabled=interactive
            if(node.has("badge")) TextButton(onClick={model.action(id)},enabled=enabled,modifier=Modifier.size(48.dp).testTag("sdk-$id")) { Text(node.optString("badge")) }
            else OriginalAction(sdkIconResource(node.optString("icon")),node.optString("text"),enabled=enabled){model.action(id)}

        }
        "speech" -> SdkSpeechTransport(node,model,speech)
        "speech_settings" -> SdkSpeechSettings(node,speech)
        "label" -> if(id=="status"&&model.appIdentity=="app.onklav.reader"&&node.optString("text").isEmpty()) Unit else if(node.optBoolean("weather_display")) SdkWeatherDisplay(node,modifier) else { val content:@Composable ()->Unit = { val readingColor=MaterialTheme.colorScheme.primary;Text(if(id=="reading.page"&&speech.playing) androidx.compose.ui.text.buildAnnotatedString {
                val value=node.optString("text");append(value);val start=speech.position.coerceIn(0,value.length);var end=start;while(end<value.length&&!value[end].isWhitespace())end++
                if(end>start)addStyle(androidx.compose.ui.text.SpanStyle(color=readingColor,fontWeight=FontWeight.Bold),start,end)
            }else androidx.compose.ui.text.AnnotatedString(node.optString("text")),modifier.fillMaxWidth().then(if(node.optBoolean("utility_display")&&node.optString("align")!="end")Modifier.background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(16.dp)).padding(horizontal=16.dp)else Modifier).then(if(node.has("long_action")) Modifier.combinedClickable(enabled=interactive,onClick={},onLongClick={model.action(node.getString("long_action"))},onLongClickLabel=node.optString("long_label","Copy"))else Modifier).padding(vertical=if(node.optBoolean("utility_display"))12.dp else 4.dp).testTag("sdk-$id").semantics {if(node.optBoolean("heading"))heading();if(node.optBoolean("status"))liveRegion=LiveRegionMode.Polite},textAlign=if(node.optString("align")=="end") androidx.compose.ui.text.style.TextAlign.End else if(node.optBoolean("center")) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,maxLines=if(node.optBoolean("utility_display"))1 else Int.MAX_VALUE,overflow=if(node.optBoolean("utility_display"))TextOverflow.Ellipsis else TextOverflow.Clip,lineHeight=if(id=="reading.page")(node.optInt("font",18)*1.55f).sp else androidx.compose.ui.unit.TextUnit.Unspecified,fontSize=(if(node.optBoolean("utility_display")&&node.optString("text").length>22)20 else if(node.optBoolean("utility_display")&&node.optString("text").length>16)26 else node.optInt("font",13)).coerceIn(12,if(node.optBoolean("utility_display"))40 else 28).sp,fontWeight=if(node.optBoolean("strong")) FontWeight.Bold else FontWeight.Normal,color=if(node.optBoolean("utility_display")&&node.optString("align")!="end")MaterialTheme.colorScheme.onPrimaryContainer else if(node.optInt("font",13)>=16) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) };if(node.optBoolean("selectable")) SelectionContainer { content() } else content() }
        "toggle" -> Row(modifier.fillMaxWidth().padding(top=12.dp).heightIn(min=48.dp).testTag("sdk-$id").semantics {node.optString("error").takeIf{it.isNotBlank()}?.let{error(it)}}.toggleable(value=node.optBoolean("checked"),enabled=interactive,role=Role.Checkbox,onValueChange={model.action(id,it.toString())}),verticalAlignment=Alignment.CenterVertically) {
            @Composable fun check(){Checkbox(node.optBoolean("checked"),onCheckedChange=null,enabled=interactive,colors=if(node.optBoolean("neutral"))CheckboxDefaults.colors(checkedColor=MaterialTheme.colorScheme.onSurfaceVariant,checkmarkColor=MaterialTheme.colorScheme.surface)else CheckboxDefaults.colors())}
            if(node.optString("align")=="end"){Text(node.optString("text"),Modifier.weight(1f),fontSize=16.sp);check()}else{check();Text(node.optString("text"),Modifier.weight(1f),fontSize=16.sp)}
        }
        "select" -> { var expanded by remember(id) { mutableStateOf(false) };val options=node.nodes("options");Column(modifier.fillMaxWidth()) { if(!node.optBoolean("compact"))Text(node.optString("text"),fontSize=14.sp);Box(Modifier.align(if(id=="radio.language")Alignment.End else Alignment.Start)) { ChoiceButton((options.firstOrNull { it.optString("value")==node.optString("value") }?.optString("text") ?: "Choose…")+" ▾",Modifier.testTag("sdk-$id").semantics {contentDescription=node.optString("text");stateDescription=options.firstOrNull{it.optString("value")==node.optString("value")}?.optString("text")?:"Choose…";node.optString("error").takeIf{it.isNotBlank()}?.let{error(it)}},enabled=interactive,outlined=false) { expanded=true };DropdownMenu(expanded&&interactive,{expanded=false}) { options.forEach { o -> DropdownMenuItem(text={Text(o.optString("text"))},enabled=interactive,onClick={expanded=false;model.action(id,o.getString("value"))}) } } } }
        }
        "chips" -> Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("sdk-$id"),horizontalArrangement=Arrangement.spacedBy(4.dp)) { node.nodes("options").forEach { o -> FilterChip(selected=node.optString("value")==o.optString("value"),onClick={model.action(id,o.optString("value"))},label={Text(o.optString("text"),fontSize=13.sp)},enabled=interactive) } }
        "voice_dial" -> SdkOfflineVoiceDial(modifier.testTag("sdk-$id"))
        "button" -> if(node.optBoolean("keypad")) {
            val colors=MaterialTheme.colorScheme
            val background=if(node.optBoolean("accent"))colors.primary else if(node.optBoolean("muted_key"))colors.secondaryContainer else colors.surfaceVariant
            val ink=if(node.optBoolean("accent"))colors.onPrimary else if(node.optBoolean("muted_key"))colors.onSecondaryContainer else colors.onSurfaceVariant
            Button(onClick={model.action(id)},enabled=node.optBoolean("enabled",true)&&model.pending==null&&!model.sessionUnavailable,modifier=modifier.fillMaxWidth().padding(vertical=4.dp).heightIn(min=58.dp).testTag("sdk-$id").semantics{contentDescription=node.optString("accessibility_label",node.optString("text"))},shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(2.dp),colors=ButtonDefaults.buttonColors(containerColor=background,contentColor=ink)){Text(node.optString("text"),fontSize=25.sp,maxLines=1)}
        } else if(node.optBoolean("tab")) {
            val selected=node.optBoolean("selected");val spec=LocalAppearanceSpec.current
            Box(modifier.height(48.dp).clickable(enabled=interactive,role=Role.Tab){model.action(id)}.semantics{this.selected=selected}.testTag("sdk-$id").padding(horizontal=4.dp,vertical=6.dp).then(if(selected)Modifier.background(sentBrush(spec),RoundedCornerShape(8.dp)).border(1.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(8.dp))else Modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp))),contentAlignment=Alignment.Center){Text(node.optString("text"),fontSize=if(node.optString("text").length>10)14.sp else 17.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(selected&&spec.dark&&spec.palette in listOf(2,4,6))sentInk(spec)else MaterialTheme.colorScheme.primary)}
        }else if(node.optBoolean("subtle"))TextButton(onClick={model.action(id)},enabled=interactive,modifier=modifier.testTag("sdk-$id")){Text(node.optString("text"),style=MaterialTheme.typography.labelLarge)}else if(node.optBoolean("center"))Surface(onClick={model.action(id)},modifier=modifier.fillMaxWidth().then(if(node.optBoolean("utility_display")&&node.optString("align")!="end")Modifier.background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(16.dp)).padding(horizontal=16.dp)else Modifier).then(if(node.has("long_action")) Modifier.combinedClickable(enabled=interactive,onClick={},onLongClick={model.action(node.getString("long_action"))},onLongClickLabel=node.optString("long_label","Copy"))else Modifier).padding(vertical=if(node.optBoolean("utility_display"))12.dp else 4.dp).testTag("sdk-$id"),enabled=interactive,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.background){Box(Modifier.heightIn(min=52.dp),contentAlignment=Alignment.Center){Text(node.optString("text"),fontSize=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}else if(node.optString("tone")=="positive")Button(onClick={model.action(id)},modifier=modifier.fillMaxWidth().padding(top=4.dp,bottom=10.dp).heightIn(min=58.dp).testTag("sdk-$id"),enabled=interactive,shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=contrastInk(MaterialTheme.colorScheme.primaryContainer),disabledContainerColor=MaterialTheme.colorScheme.surface,disabledContentColor=MaterialTheme.colorScheme.onSurfaceVariant)){Text(node.optString("text"),fontSize=19.sp)}else if(node.optBoolean("primary"))Button(onClick={model.action(id)},modifier=modifier.fillMaxWidth().heightIn(min=56.dp).testTag("sdk-$id"),enabled=interactive,shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(disabledContainerColor=MaterialTheme.colorScheme.surface,disabledContentColor=MaterialTheme.colorScheme.onSurfaceVariant)){Text(node.optString("text"),fontSize=17.sp)}else ChoiceButton(node.optString("text"),modifier.testTag("sdk-$id"),enabled = interactive, outlined = false) { model.action(id) }
        "record" -> {
            if(model.appIdentity=="app.onklav.radio"&&node.optBoolean("hide_unavailable")){val hidden by SdkRadio.unavailable.collectAsState();val radioOptions by SdkRadioHealth.options.collectAsState();val radioNotices by SdkRadioHealth.notices.collectAsState();val station=node.optString("radio_station");if(!radioOptions.showHidden&&(radioOptions.autoHide||radioNotices[station]?.manual==true)&&hidden[station]?.let{it>System.currentTimeMillis()}==true)return}

            if(id.startsWith("book.preview.")||id.startsWith("book.open.")) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
            val enabled=interactive
            val dimmed=if(node.optBoolean("enabled",true))1f else .45f
            val recordDetail=if(id.startsWith("book.preview.")&&!node.optBoolean("enabled",true))node.optString("detail").substringAfterLast(" · ")else node.optString("detail")
            val trailing:@Composable ()->Unit={SdkTrailingActions(node,model,enabled)}
            val open=if(node.optBoolean("openable",true))Modifier.clickable(enabled=enabled){model.action(id)}else Modifier
            if(node.optBoolean("card"))Column(modifier.fillMaxWidth().padding(bottom=8.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(14.dp)).then(open).padding(start=14.dp,top=10.dp,end=10.dp,bottom=14.dp).testTag("sdk-$id")){
                val detail=node.optString("detail")
                Row(verticalAlignment=Alignment.CenterVertically){Text(detail.substringBefore('\n'),Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant);trailing()}
                Text(node.optString("text"),fontSize=LocalAppearanceSpec.current.size.coerceIn(17,22).sp,maxLines=3,overflow=TextOverflow.Ellipsis,fontWeight=if(node.optBoolean("strong"))FontWeight.Bold else FontWeight.Normal)
                detail.substringAfter('\n',"").takeIf{it.isNotBlank()}?.let{Text(it,Modifier.padding(top=6.dp),fontSize=14.sp,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }else Column(modifier.fillMaxWidth().graphicsLayer{alpha=dimmed}){Row(Modifier.testTag("sdk-$id").fillMaxWidth().then(open).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                if(node.has("leading_icon"))Icon(painterResource(sdkIconResource(node.getString("leading_icon"))),null,Modifier.size(38.dp).padding(end=6.dp),tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){Text(node.optString("text"),maxLines=2,fontSize=16.sp,fontWeight=if(node.optBoolean("strong"))FontWeight.Bold else FontWeight.Normal);Text(recordDetail,maxLines=2,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(model.appIdentity=="app.onklav.files"&&id.startsWith("entry:")&&model.frame.child("state").nodes("items").any{it.optString("id")==id.removePrefix("entry:")&&it.optBoolean("encrypted")})Icon(Icons.Outlined.Lock,"Encrypted folder",Modifier.size(20.dp))
                trailing();if(node.has("delete"))CompactAction(Icons.Outlined.Delete,"Delete "+node.optString("text")){model.action(node.getString("delete"))}
            }
            if(node.has("radio_station")&&model.appIdentity=="app.onklav.radio")SdkRadioStatus(node.getString("radio_station"))
            if(node.has("podcast_episode"))SdkPodcastTransfer(model,node.getString("podcast_episode"),node.optBoolean("downloaded"))
            }
        }
        "field","editor" -> {
            val editor=node.getString("kind")=="editor"
            val inputKind=node.optString("input_kind","text")
            val inputContext=LocalContext.current;val appearance=LocalAppearanceSpec.current
            var picker by remember(id,revision){mutableStateOf<android.app.Dialog?>(null)}
            DisposableEffect(id,revision){onDispose{picker?.setOnDismissListener(null);picker?.dismiss();picker=null}}
            LaunchedEffect(interactive){if(!interactive){picker?.dismiss();picker=null}}
            var value by remember(revision,id) { mutableStateOf(TextFieldValue(model.draftValue(id,node.optString("value")))) }
            val canonical=node.optString("value")
            LaunchedEffect(canonical) { if(value.text != canonical && !model.hasUnsaved(id)) value=TextFieldValue(canonical,TextRange(value.selection.start.coerceAtMost(canonical.length))) }
            val editorScroll=rememberScrollState()
            var textLayout by remember {mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)}
            val highlight=node.optBoolean("speech_highlight")&&speech.playing
            val highlightColor=MaterialTheme.colorScheme.primary
            LaunchedEffect(speech.position,highlight,textLayout) {
                if(highlight) textLayout?.let { layout ->
                    val line=layout.getLineForOffset(speech.position.coerceIn(0,value.text.length))
                    val top=layout.getLineTop(line).toInt();val bottom=layout.getLineBottom(line).toInt()
                    val visible=editorScroll.viewportSize
                    if(top<editorScroll.value||bottom>editorScroll.value+visible)editorScroll.animateScrollTo((top-visible/3).coerceIn(0,editorScroll.maxValue))
                }
            }
            val caret=model.speechCaret
            LaunchedEffect(caret) {
                if(editor && caret?.id==id && caret.text==value.text && !model.hasUnsaved(id)) {
                    value=value.copy(selection=TextRange(caret.at))
                    model.selection=caret.at;model.selectionEnd=caret.at
                }
            }
            BasicTextField(value, { incoming ->
                val clean=(if(id=="peer") phoneInput(incoming.text) else incoming.text).filter { !it.isISOControl() || it=='\n' || it=='\t' }.take(node.optInt("limit",80)).let { if(it.lastOrNull()?.isHighSurrogate()==true) it.dropLast(1) else it }
                if(node.optBoolean("reject_overflow") && incoming.text.length>node.optInt("limit",80)) {
                    model.error="Text limit: ${node.optInt("limit",80)} characters. Your text was kept."
                    return@BasicTextField
                }
                val previous=value.text
                value=incoming.copy(text=clean,selection=TextRange(incoming.selection.start.coerceAtMost(clean.length),incoming.selection.end.coerceAtMost(clean.length)))
                if(editor) { model.selection=value.selection.start;model.selectionEnd=value.selection.end }
                if(previous != clean) { speech.stop();model.edit(id,clean) }
            }, modifier.then(if(editor&&node.optBoolean("speech_highlight"))Modifier.clip(RoundedCornerShape(14.dp)).drawWithContent {
                drawContent()
                if(editorScroll.maxValue>0){val h=size.height;val thumb=(h*editorScroll.viewportSize/(editorScroll.maxValue+editorScroll.viewportSize).coerceAtLeast(1)).coerceIn(24f,h);val y=(h-thumb)*editorScroll.value/editorScroll.maxValue
                    drawRoundRect(highlightColor.copy(alpha=.4f),androidx.compose.ui.geometry.Offset(size.width-5.dp.toPx(),y),androidx.compose.ui.geometry.Size(2.dp.toPx(),thumb),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))}
            }.verticalScroll(editorScroll)else Modifier).then(if(editor && revision=="menu") Modifier.heightIn(min=120.dp,max=220.dp) else Modifier).onPreviewKeyEvent { e ->
                if(e.isCtrlPressed || e.isMetaPressed) when(e.key) {
                    Key.C,Key.X -> !model.clipboardAllowed("clipboard.write")
                    Key.V -> !model.clipboardAllowed("clipboard.read")
                    else -> false
                } else false
            }.semantics {
                contentDescription=node.optString("label").ifBlank{node.optString("hint").ifBlank{"Text input"}}
                node.optString("error").takeIf{it.isNotBlank()}?.let{error(it)}
            }.fillMaxWidth().heightIn(min=48.dp).then(if(editor) Modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(14.dp)).padding(horizontal=16.dp,vertical=14.dp) else Modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp)).padding(horizontal=12.dp,vertical=8.dp)).testTag("sdk-$id"),
                onTextLayout={textLayout=it},
                visualTransformation=if(id=="peer") PhoneVisualTransformation else if(highlight) androidx.compose.ui.text.input.VisualTransformation { original ->
                    val start=SpeechPlan.boundary(original.text,speech.position);var end=start
                    while(end<original.length&&!original[end].isWhitespace())end++
                    val colored=androidx.compose.ui.text.buildAnnotatedString {append(original);if(end>start)addStyle(androidx.compose.ui.text.SpanStyle(color=highlightColor,fontWeight=FontWeight.Bold),start,end)}
                    androidx.compose.ui.text.input.TransformedText(colored,androidx.compose.ui.text.input.OffsetMapping.Identity)
                } else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions=KeyboardOptions(keyboardType=if(id=="peer") androidx.compose.ui.text.input.KeyboardType.Phone else SdkFieldInputs.keyboard(inputKind),imeAction=if(node.has("submit")) ImeAction.Go else ImeAction.Default),keyboardActions=KeyboardActions(onGo={if(node.has("submit")) model.action(node.getString("submit"))}),
                enabled=interactive,singleLine=!editor,cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                textStyle=LocalTextStyle.current.copy(color=MaterialTheme.colorScheme.onSurface,fontSize=if(editor) LocalAppearanceSpec.current.size.sp else node.optInt("font",16).coerceIn(12,26).sp),
                decorationBox={ field -> Row(verticalAlignment=if(editor)Alignment.Top else Alignment.CenterVertically){
                    Box(Modifier.weight(1f)){if(value.text.isEmpty())Text(node.optString("hint"),fontSize=if(editor) LocalAppearanceSpec.current.size.sp else node.optInt("font",16).coerceIn(12,26).sp,color=MaterialTheme.colorScheme.onSurfaceVariant);field()}
                    if(!editor&&model.appIdentity=="app.onklav.files"&&id=="query")IconButton(enabled=interactive,onClick={model.action("query",value.text)}){Icon(Icons.Outlined.ArrowForward,"Search files")}
                    else if(!editor)SdkTrailingActions(node,model,interactive)
                    if(!editor&&inputKind in setOf("date","time"))IconButton(modifier=Modifier.size(48.dp),enabled=interactive,onClick={
                        if(picker?.isShowing!=true){
                            val selected:(String)->Unit={chosen->value=TextFieldValue(chosen,TextRange(chosen.length));model.action(id,chosen)}
                            picker=if(inputKind=="date")SdkFieldInputs.date(inputContext,appearance,value.text,selected)else SdkFieldInputs.time(inputContext,appearance,value.text,selected)
                            picker?.setOnDismissListener{picker=null};picker?.show()
                        }
                    }){if(inputKind=="date")Icon(Icons.Outlined.DateRange,"Choose date for "+node.optString("hint").ifBlank{"this field"},tint=MaterialTheme.colorScheme.primary)else Icon(painterResource(R.drawable.sdk_time),"Choose time for "+node.optString("hint").ifBlank{"this field"},tint=MaterialTheme.colorScheme.primary)}
                } })
        }
    }
}


/** Shared declarative voice status surface; no microphone or transport access. */
@Composable private fun SdkOfflineVoiceDial(modifier: Modifier) {
    val colors=MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().padding(top=16.dp,bottom=20.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.widthIn(max=196.dp).fillMaxWidth().aspectRatio(1f).semantics { contentDescription="Walkie Talkie Offline"; disabled() },contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius=size.minDimension*.46f
                drawCircle(colors.surface, radius)
                drawCircle(colors.background, radius*.94f)
                drawCircle(colors.primary.copy(alpha=.48f),radius,style=androidx.compose.ui.graphics.drawscope.Stroke(size.minDimension*.035f))
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(androidx.compose.ui.res.painterResource(app.onklav.shared.R.drawable.ref_app_walkie),null,Modifier.size(76.dp),tint=colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("OFFLINE",fontSize=16.sp,color=colors.onSurfaceVariant)
            }
        }
    }
}

/** Static geometry from the read-only 2.2.85 artwork; shared by first- and third-party SDK apps. */
@Composable private fun SdkTrailingActions(node:JSONObject,model:SdkAppModel,enabled:Boolean){
 for(key in listOf("secondary_trailing","trailing"))node.optJSONObject(key)?.let{t->
  if(t.optBoolean("spinning"))Box(Modifier.size(48.dp).semantics{contentDescription=t.optString("text")},contentAlignment=Alignment.Center){SpeechActivityIcon(MaterialTheme.colorScheme.primary,t.optString("text"))}
  else OriginalAction(sdkIconResource(t.optString("icon")),t.optString("text"),enabled=enabled){model.action(t.getString("id"))}
 }
}

internal fun sdkIconResource(kind:String):Int=when(kind){
 "folder.add"->R.drawable.ref_folder_add
 "calc"->R.drawable.ref_app_calc;"weather"->R.drawable.ref_app_weather;"convert"->R.drawable.ref_app_convert;"ebooks"->R.drawable.ref_app_ebooks;"audiobooks"->R.drawable.ref_app_audiobooks;"podcasts"->R.drawable.ref_app_podcasts
 "play"->R.drawable.ref_radio_play;"delete"->R.drawable.ref_action_close
 "radio"->R.drawable.ref_app_radio
 "search"->R.drawable.ref_action_search
 "tabs"->R.drawable.ref_browser_tabs;"contacts"->R.drawable.ref_nav_contacts;"folder"->R.drawable.ref_app_files;"file"->R.drawable.ref_file_document;"home"->R.drawable.ref_browser_home;"forward"->R.drawable.ref_browser_forward;"back"->R.drawable.ref_action_back;"go"->R.drawable.ref_browser_go
 "bookmark.filled"->R.drawable.ref_bookmark_filled;"bookmark"->R.drawable.ref_browser_bookmark;"menu"->R.drawable.ref_browser_menu;"refresh"->R.drawable.ref_browser_refresh;"history"->R.drawable.ref_browser_history
 "channels"->R.drawable.ref_youtube_channels;"settings"->R.drawable.ref_nav_settings;"rss"->R.drawable.ref_app_rss;"add"->R.drawable.ref_action_add;"download"->R.drawable.ref_action_download;"edit"->R.drawable.ref_action_edit
 else->R.drawable.ref_browser_more
}
