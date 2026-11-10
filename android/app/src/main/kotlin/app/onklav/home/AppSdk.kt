package app.onklav.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.onklav.shared.NativeCore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** Included app upgrades enable newly shipped capabilities while retaining existing user choices. */
internal fun bundledUpgradeGrants(previous:JSONObject,caps:Set<String>):Set<String>{
    fun values(a:JSONArray?)=a?.let{(0 until it.length()).map{index->it.getString(index)}.toSet()}?:emptySet()
    val before=values(previous.child("manifest").optJSONArray("capabilities"))
    return (values(previous.optJSONArray("grants"))+(caps-before)).intersect(caps)
}
internal const val YOUTUBE_READY_KEY="youtube-download-ready-v1:app.onklav.youtube"
internal val YOUTUBE_DOWNLOAD_CAPS=setOf("storage.private","network.web","clipboard.read","clipboard.write","files.manage")
/** Host-only access to the Rust SDK. A Wasm package never receives this object or Context. */
object AppSdk {
    val bundledApps = mapOf("clock" to ("app.onklav.clock" to "Clock"),"flashlight" to ("app.onklav.flashlight" to "Flashlight"),"camera" to ("app.onklav.camera" to "Camera"),"email" to ("app.onklav.email" to "Email"),"videoeditor" to ("app.onklav.videoeditor" to "VideoEditor"),"maps" to ("app.onklav.maps" to "Maps"),"audioeditor" to ("app.onklav.audioeditor" to "Audio"),"write" to ("app.onklav.write" to "Write"),"photo" to ("app.onklav.photo" to "Photo"),"calc" to ("app.onklav.calc" to "Calc"),"convert" to ("app.onklav.convert" to "Convert"),"weather" to ("app.onklav.weather" to "Weather"),"ebooks" to ("app.onklav.ebooks" to "eBooks"),"audiobooks" to ("app.onklav.audiobooks" to "AudioBooks"),"files" to ("app.onklav.files" to "Files"),"rss" to ("app.onklav.rss" to "RSS"),"reader" to ("app.onklav.reader" to "ReadAloud"),"browser" to ("app.onklav.browser" to "Browser"),"youtube" to ("app.onklav.youtube" to "YouTube"),"social" to ("app.onklav.social" to "Social"),"walkie" to ("app.onklav.walkie" to "WalkieTalkie"),"podcasts" to ("app.onklav.podcasts" to "Podcasts"),"radio" to ("app.onklav.radio" to "Radio"))
    val epoch = MutableStateFlow(0L)
    val notesRecoveryRequired = MutableStateFlow(false)
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cleanupScope=CoroutineScope(SupervisorJob()+dispatcher)
    internal fun releaseSession(context:Context,token:String){
        val owner=context.applicationContext
        cleanupScope.launch{runCatching{sync(owner,JSONObject().put("action","close").put("session",token))}}
    }
    val staging = Mutex()
    // Cache only completion of bundled-package setup, never grants or runtime
    // authorization. Each launch/event still reaches the live Rust checks.
    private val vaultRecovered = java.util.WeakHashMap<Context,Boolean>()
    private val bootstrapped = java.util.WeakHashMap<Context,Long>()
    private val carrierLock=Any()
    internal fun <T> carrierEgress(action:()->T):T=synchronized(carrierLock){action()}
    private fun raw(context:Context,request:JSONObject):JSONObject = JSONObject(NativeCore.dispatch(context.filesDir.absolutePath,request.put("op","sdk").toString()))
    internal fun nativeTask(context:Context,request:JSONObject):JSONObject=synchronized(carrierLock){
        check(request.optString("action") in setOf("audiobook_authorize","background_authorize","background_blob","media_authorize","media_blob","task_cleanup"))
        val response=raw(context,request);check(response.getBoolean("ok")){response.optString("error","Native operation unavailable")};response.getJSONObject("value")
    }
    internal fun isSocialInstalled(context:Context)=installed(context,RealSocial.APP)
    private fun installed(context:Context,id:String):Boolean {
        val response=raw(context,JSONObject().put("action","list"));check(response.getBoolean("ok")){response.optString("error")}
        val rows=response.getJSONArray("value")
        return (0 until rows.length()).any{rows.getJSONObject(it).let{a->a.child("manifest").optString("id")==id&&a.optBoolean("installed")}}
    }
    private fun recoverRemoval(context:Context,id:String){
        val row=SdkNativeData.record(context,id)?:return
        if(row.getString("phase")=="prepared"){
            if(installed(context,id)){SdkNativeData.cancel(context,id);return}
            val retry=raw(context,JSONObject().put("action","remove").put("id",id).put("delete_data",row.getBoolean("delete")))
            check(retry.getBoolean("ok")){"App removed; private-data cleanup remains pending: "+retry.optString("error")}
        }
        SdkNativeData.removed(context,id,row.getBoolean("delete"))
    }
    private val protectedShortcutApps = setOf("browser", "youtube", "walkie", "reader")

    internal fun isRemovalProtected(packageId: String): Boolean = bundledApps.any { (id, pkg) ->
        pkg.first == packageId && (id in corePlatformApps || id in protectedShortcutApps)
    }

    fun sync(context: Context, request: JSONObject): Any = synchronized(carrierLock) {
        val action=request.optString("action");val id=request.optString("id")
        check(action != "remove" || !isRemovalProtected(id)) { "This included app cannot be uninstalled." }
        SdkNativeData.pending(context).forEach{pending->
            try{recoverRemoval(context,pending)}catch(e:Exception){
                ChatStore.lastError.value=e.message
                if(action in setOf("install","launch","remove")&&(id==pending||action=="install"))throw e
            }
        }
        if(action=="remove"){
            check(installed(context,id)){"App is not installed"}
            SdkNativeData.prepare(context,id,request.optBoolean("delete_data"))
        }
        val response=raw(context,request)
        if(!response.getBoolean("ok")){
            if(action=="remove"){
                if(installed(context,id))SdkNativeData.cancel(context,id)
                else {SdkFeedService.cancelAll();SdkHttpService.cancelAll();SdkRadio.cancelAll();SdkPodcasts.cancelAll();SdkEbooks.cancelAll();SdkAccounts.cancelAll();SdkJobs.cancelNetworkCalls();SdkMedia.changed(context,id);if(id.isNotBlank()){SdkDeviceLeases.clearApp(id);SdkAudioBooks.invalidate(id);SdkRadio.clearApp(id);SdkPodcasts.clearApp(id);SdkPdfClient.cancelApp(id);SdkHandoffProvider.clearApp(context,id)};SdkThumbnails.cancelAll();epoch.value++;ChatStore.lastError.value="App removed; cleanup is pending: "+response.optString("error");return@synchronized true}
            }
            error(response.optString("error","App operation failed"))
        }
        if(action in setOf("install","remove","permissions")){SdkFeedService.cancelAll();SdkHttpService.cancelAll();SdkRadio.cancelAll();SdkPodcasts.cancelAll();SdkEbooks.cancelAll();SdkAccounts.cancelAll();SdkJobs.cancelNetworkCalls();SdkMedia.changed(context,id);if(id.isNotBlank()){SdkDeviceLeases.clearApp(id);SdkAudioBooks.invalidate(id);SdkRadio.clearApp(id);SdkPodcasts.clearApp(id);SdkPdfClient.cancelApp(id);SdkHandoffProvider.clearApp(context,id)};SdkThumbnails.cancelAll();epoch.value++}
        if(action=="install")response.optJSONObject("value")?.optString("id")?.takeIf{it.isNotBlank()}?.let{SdkDeviceLeases.clearApp(it);SdkRadio.clearApp(it);SdkPodcasts.clearApp(it);SdkPdfClient.cancelApp(it);SdkHandoffProvider.clearApp(context,it)}
        if(action=="permissions")runCatching{SdkDeviceLeases.clearApp(id);SdkAudioBooks.invalidate(id);SdkRadio.clearApp(id);SdkPodcasts.clearApp(id);SdkJobs.clearApp(context,id);if(request.optJSONArray("grants").let{a->a==null||!setOf("accounts.connect","network.http").all{cap->(0 until a.length()).any{a.optString(it)==cap}}})SdkAccountVault.clear(context,id);SdkNotifications.clearApp(context,id)}.onFailure{ChatStore.lastError.value="Permissions saved; native data cleanup is pending"}
        if(action=="permissions"||action=="remove"){
            if(id==TORCH_APP)TorchControl.stop(context)
            if(id==CLOCK_APP)ClockDisk.stopAll(context)
        }
        if(action=="remove")try{SdkNativeData.removed(context,id,request.optBoolean("delete_data"))}catch(e:Exception){ChatStore.lastError.value="App removed; cleanup is pending: ${e.message}"}
        response.get("value")
    }
    suspend fun call(context: Context, request: JSONObject): Any = withContext(dispatcher) {
        // Trace only operation names; never message text, contacts or guest state.
        if(!android.os.Trace.isEnabled()) return@withContext sync(context,request)
        val action=request.optString("action").take(40).replace(Regex("[^a-z_]"),"_")
        android.os.Trace.beginSection("OnKlav.sdk.$action")
        try { sync(context, request) } finally { android.os.Trace.endSection() }
    }
    internal suspend fun includedVersion(context:Context,id:String):Long=withContext(Dispatchers.IO) {
        val name=bundledApps.values.firstOrNull{it.first==id}?.second ?: return@withContext 0L
        ZipInputStream(context.assets.open("sdk/$name.onklav-a")).use { zip ->
            while(true){val entry=zip.nextEntry ?: break;if(entry.name=="manifest.json")return@withContext JSONObject(app.onklav.shared.ThemePackage.bounded(zip,65536).toString(Charsets.UTF_8)).getLong("version")}
        };0L
    }
    suspend fun list(context: Context): List<JSONObject> = (call(context, JSONObject().put("action", "list")) as JSONArray).let { a -> List(a.length()) { a.getJSONObject(it) } }
    suspend fun stage(context: Context, input: java.io.InputStream) = withContext(dispatcher) {
        val destination = File(context.filesDir, "app-install.zip")
        try { destination.outputStream().use { out ->
            val buffer = ByteArray(8192); var total = 0
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 700000) { "App package exceeds 700 KB" }; out.write(buffer, 0, n) }
        } } catch(e: Exception) { destination.delete(); throw e }
    }
    private suspend fun platformIdentity(context:Context,review:JSONObject){
        val manifest=review.getJSONObject("manifest");val id=manifest.getString("id")
        if(!id.startsWith("app.onklav."))return
        val name=bundledApps.values.firstOrNull{it.first==id}?.second?:error("The app.onklav namespace is reserved for OnKlav")
        val publisher=withContext(Dispatchers.IO){ZipInputStream(context.assets.open("sdk/$name.onklav-a")).use{zip->
            var result:String?=null
            while(true){val entry=zip.nextEntry?:break;if(entry.name=="manifest.json"){result=JSONObject(app.onklav.shared.ThemePackage.bounded(zip,16384).toString(Charsets.UTF_8)).getString("publisher");break}}
            requireNotNull(result)
        }}
        require(manifest.getString("publisher")==publisher){"This platform identity belongs to OnKlav"}
    }
    suspend fun inspect(context: Context):JSONObject{val review=call(context,JSONObject().put("action","inspect")) as JSONObject;platformIdentity(context,review);return review}
    suspend fun install(context: Context, review: JSONObject, grants: Set<String>): JSONObject {platformIdentity(context,review);return call(context, JSONObject().put("action", "install").put("hash", review.getString("hash")).put("grants", JSONArray(grants.toList()))) as JSONObject}
    internal suspend fun enableYoutubeDownloads(context:Context){
        val row=list(context).firstOrNull{it.child("manifest").optString("id")=="app.onklav.youtube"&&it.optBoolean("installed")}?:error("Open YouTube again before downloading")
        platformIdentity(context,row)
        val declared=row.child("manifest").getJSONArray("capabilities").let{a->(0 until a.length()).map{a.getString(it)}.toSet()}
        check(declared.containsAll(YOUTUBE_DOWNLOAD_CAPS)){"Update the included YouTube app to enable downloads"}
        val current=row.optJSONArray("grants").let{a->if(a==null)emptySet()else (0 until a.length()).map{a.getString(it)}.toSet()}
        if(!current.containsAll(YOUTUBE_DOWNLOAD_CAPS))call(context,JSONObject().put("action","permissions").put("id","app.onklav.youtube").put("grants",JSONArray((current+YOUTUBE_DOWNLOAD_CAPS).intersect(declared).toList())))
    }
    internal fun folderVault(context:Context,action:String,id:String="",extra:JSONObject=JSONObject()):JSONObject=synchronized(carrierLock){
        val response=JSONObject(NativeCore.dispatch(context.filesDir.absolutePath,extra.put("op","folder_vault").put("action",action).put("id",id).toString()))
        check(response.getBoolean("ok")){response.optString("error","Protected folder action failed")};response.getJSONObject("value")
    }
    internal fun writeMigration(context:Context,action:String="migrate",id:String=""):JSONObject=synchronized(carrierLock){
        val response=JSONObject(NativeCore.dispatch(context.filesDir.absolutePath,JSONObject().put("op","notes_to_write").put("action",action).put("id",id).toString()))
        check(response.getBoolean("ok")){response.optString("error","Write migration failed; originals kept")};response.getJSONObject("value")
    }
    suspend fun bootstrap(context: Context) = staging.withLock {
        val owner=context.applicationContext
        if(bootstrapped[owner]==epoch.value && File(context.filesDir,"app-sdk/registry.json").isFile) return@withLock
        if(vaultRecovered[owner]!=true){
            val failures=FolderVaults.engine(context).recoverAndLock();vaultRecovered[owner]=true
            if(failures.isNotEmpty())ChatStore.lastError.value="An encrypted folder needs its password to finish recovery. Its contents remain locked."
        }
        val records = list(context)
        // Annotation's tools now live in Image Edit. Unregister the old package and
        // revoke its sessions/grants through the normal broker; never delete saved work.
        if(records.any{it.child("manifest").optString("id")=="app.onklav.annotation"&&it.optBoolean("installed")})
            call(context,JSONObject().put("action","remove").put("id","app.onklav.annotation").put("delete_data",false))
        for ((desktopId, pkg) in bundledApps) {
            val (packageId,name) = pkg
            val previous=records.firstOrNull { it.child("manifest").optString("id")==packageId }
            val restoreProtected = previous != null && !previous.optBoolean("installed") && isRemovalProtected(packageId) &&
                previous.child("manifest").optLong("version") <= includedVersion(context,packageId)
            if (previous==null || restoreProtected || (previous.optBoolean("installed") && previous.child("manifest").optLong("version") < includedVersion(context,packageId))) {
                context.assets.open("sdk/$name.onklav-a").use { stage(context,it) }
                val review=inspect(context)
                val caps=review.child("manifest").getJSONArray("capabilities").let { a -> List(a.length()) { a.getString(it) }.toSet() }
                if(previous!=null)check(previous.child("manifest").getString("publisher")==review.child("manifest").getString("publisher")){"Built-in app publisher changed"}
                val granted=if(previous==null || restoreProtected)caps else bundledUpgradeGrants(previous,caps)
                if (restoreProtected && desktopId in protectedShortcutApps) setHidden(context, false, desktopId, true)
                install(context,review,granted)
            }
        }
        // User-authorized defaults for shipped apps; later revocations remain effective.
        val defaults=context.getSharedPreferences("bundled-app-default-grants-v1",0)
        for(row in list(context)){
            val appId=row.child("manifest").optString("id")
            if(row.optBoolean("installed")&&bundledApps.values.any{it.first==appId}&&!defaults.getBoolean(if(appId in setOf("app.onklav.audiobooks","app.onklav.ebooks"))"reader-ready-v2:$appId"else appId,false)){
                val caps=row.child("manifest").getJSONArray("capabilities")
                call(context,JSONObject().put("action","permissions").put("id",appId).put("grants",caps))
                check(defaults.edit().putBoolean(appId,true).putBoolean(if(appId in setOf("app.onklav.audiobooks","app.onklav.ebooks"))"reader-ready-v2:$appId"else appId,true).commit()){"App defaults could not be saved"}
            }
        }
        // One user-authorized repair of all shipped defaults, including Clock. Later revocations stay effective.
        for(row in list(context)){
            val appId=row.child("manifest").optString("id");val key="included-ready-v2:$appId"
            if(row.optBoolean("installed")&&bundledApps.values.any{it.first==appId}&&!defaults.getBoolean(key,false)){
                platformIdentity(context,row)
                val caps=row.child("manifest").getJSONArray("capabilities")
                val granted=row.optJSONArray("grants")?:JSONArray()
                if((0 until caps.length()).any{i->(0 until granted.length()).none{granted.optString(it)==caps.getString(i)}})
                    call(context,JSONObject().put("action","permissions").put("id",appId).put("grants",caps))
                check(defaults.edit().putBoolean(key,true).commit()){"Included app defaults could not be saved"}
            }
        }
        if(!defaults.getBoolean(YOUTUBE_READY_KEY,false)){
            val youtube=list(context).firstOrNull{it.child("manifest").optString("id")=="app.onklav.youtube"&&it.optBoolean("installed")}
            if(youtube!=null){
                enableYoutubeDownloads(context)
                check(defaults.edit().putBoolean(YOUTUBE_READY_KEY,true).commit()){"YouTube defaults could not be saved"}
            }
        }
        val own=list(context).firstOrNull { it.child("manifest").optString("id")=="app.onklav.notes" }
        if(WriteFiles.allowed(context)){
            try{withContext(dispatcher){writeMigration(context)};notesRecoveryRequired.value=false}catch(e:Exception){if(e is CancellationException)throw e;notesRecoveryRequired.value=true;throw e}
            if(own?.optBoolean("installed")==true)call(context,JSONObject().put("action","remove").put("id","app.onklav.notes").put("delete_data",false))
        }
        File(context.filesDir, "app-install.zip").delete()
        withContext(Dispatchers.IO){runCatching{SdkScheduledNotifications.recover(context,rescheduleAbsolute=true);SdkJobs.recover(context)}.onFailure{ChatStore.lastError.value="Some app reminders need recovery. They will be checked when OnKlav opens again."}}
        bootstrapped[owner]=epoch.value
    }
    fun desktopId(id: String) = "pkg_" + java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
}

/** Serialized lifecycle/field updates keep autosaves ahead of navigation and host actions. */
internal class SdkScrollMemory {
    private val offsets=linkedMapOf<String,Int>()
    fun position(key:String)=offsets[key] ?: 0
    fun save(key:String,position:Int) {
        offsets.remove(key);offsets[key]=position.coerceAtLeast(0)
        while(offsets.size>32) offsets.remove(offsets.keys.first())
    }
    internal val size get()=offsets.size
}

internal data class EditorCaretRequest(val id:String,val text:String,val at:Int,val serial:Long)

class SdkAppModel(application: Application) : AndroidViewModel(application) {
    private val context:Application = application
    private val operations = Mutex()
    internal val webPool=SdkWebPool()
    private val youtubePrefs by lazy { context.getSharedPreferences("onklav-youtube-viewer", Context.MODE_PRIVATE) }
    internal var youtubePreviews by mutableStateOf(false)
        private set
    internal val managesYoutubePreviews get() = appId == "app.onklav.youtube"
    internal fun toggleYoutubePreviews() {
        youtubePreviews = !youtubePreviews
        youtubePrefs.edit().putBoolean("feed_previews", youtubePreviews).apply()
    }
    internal val scrollMemory=SdkScrollMemory()
    private val dirty = linkedMapOf<String,String>()
    private var debounce: Job? = null
    private var launchedToken:String?=null
    var frame by mutableStateOf(JSONObject()); private set
    var pending by mutableStateOf<JSONObject?>(null); private set
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    internal var componentBack:(()->Boolean)?=null
    internal var vaultRequest by mutableStateOf<JSONObject?>(null)
    var recoveryNotice by mutableStateOf(false); private set
    fun dismissRecoveryNotice(){recoveryNotice=false}
    var sessionUnavailable by mutableStateOf(false); private set
    var accessNotice by mutableStateOf(false); private set
    fun keepVisibleDraft(){accessNotice=false;error=null}
    private fun retainUnavailableSession(){
        releaseDeviceLeases();debounce?.cancel();pending?.optString("token")?.let{SdkFeedService.cancel(it);SdkHttpService.cancel(it);SdkRadio.cancel(it);SdkPodcasts.cancel(it);SdkEbooks.cancel(it);SdkAccounts.cancel(it)}
        pending=null;sessionUnavailable=true;accessNotice=true
    }
    suspend fun reopenDiscardingDraft(){
        val id=operations.withLock{val previous=appId;session=null;frame=JSONObject();pending=null;dirty.clear();sessionUnavailable=false;accessNotice=false;error=null;previous}
        if(id.isNotEmpty())open(id)
    }
    internal fun releaseDeviceLeases(){if(appId.isNotBlank())SdkPdfClient.cancelApp(appId);session?.let{SdkDeviceLeases.clear(it)}}
    private var session: String? = null
    private var appId = ""
    internal val appIdentity:String get()=appId
    private var carrierOwner by mutableStateOf<JSONObject?>(null)
    internal var fileConnectLocation:android.net.Uri?=null
    internal var fileStatus by mutableStateOf<String?>(null)
    internal var filesClipboard by mutableStateOf<FilesClipboard?>(null); private set
    internal var speechCaret by mutableStateOf<EditorCaretRequest?>(null);private set
    private var caretSerial=0L
    internal fun followSpeechPosition(widget:String,text:String,at:Int) {
        if(session==null) return
        val body=frame.optJSONArray("body") ?: return
        val nodes=(0 until body.length()).mapNotNull { body.optJSONObject(it) }
        if(nodes.none { it.optString("id")==widget && it.optString("kind")=="speech" && it.optString("value")==text }) return
        val editor=nodes.filter { it.optString("kind")=="editor" && it.optString("value")==text }.singleOrNull() ?: return
        val id=editor.optString("id")
        if(hasUnsaved(id)) return
        speechCaret=EditorCaretRequest(id,text,SpeechPlan.boundary(text,at.coerceIn(0,text.length)),++caretSerial)
    }
    var selectionEnd = 0
    var selection = 0
    suspend fun open(id: String,recovering:Boolean=false) = operations.withLock {
        if(appId.isNotEmpty()&&appId!=id){error="Open this app in its own app session";return@withLock}
        if (session != null && appId == id) {
            try {AppSdk.call(context, JSONObject().put("action", "check").put("session", session))}
            catch(e:CancellationException){throw e}catch(e:Exception){retainUnavailableSession()}
            return@withLock
        }
        speechCaret=null
        busy = true
        try {
            AppSdk.bootstrap(context)
            if(id=="app.onklav.rss") {
                val legacy=context.getSharedPreferences("rss",0)
                if(legacy.contains("feeds") && !legacy.getBoolean("sdk_feeds_adopted",false)) {
                    val publisher=withContext(Dispatchers.IO) {
                        ZipInputStream(context.assets.open("sdk/RSS.onklav-a")).use { zip ->
                            var publisher:String?=null
                            while(true) { val entry=zip.nextEntry ?: break
                                if(entry.name=="manifest.json") { publisher=JSONObject(app.onklav.shared.ThemePackage.bounded(zip,65536).toString(Charsets.UTF_8)).getString("publisher");break }
                            }
                            requireNotNull(publisher)
                        }
                    }
                    AppSdk.call(context,JSONObject().put("action","adopt_own_feeds").put("publisher",publisher).put("feeds",legacy.getString("feeds","[]")))
                    check(legacy.edit().putBoolean("sdk_feeds_adopted",true).commit()) { "Previous feeds were retained; import acknowledgement could not be saved" }
                }
            }
            val result = AppSdk.call(context, JSONObject().put("action", "launch").put("id", id)) as JSONObject
            radioOpeningEpoch=userActionEpoch;filesClipboard=null;session = result.getString("session"); appId = id;if(managesYoutubePreviews)youtubePreviews=youtubePrefs.getBoolean("feed_previews",false);carrierOwner=result.optJSONObject("owner"); apply(result);sessionUnavailable=false;accessNotice=false;error=null;recoveryNotice=recovering
            if(id=="app.onklav.reader"){
                val legacy=context.getSharedPreferences("reader",0)
                if(!legacy.getBoolean("sdk_draft_adopted",false)){
                    val note=frame.child("state").child("note");val text=legacy.getString("text","").orEmpty()
                    if(text.isNotEmpty()&&note.optLong("updated")==0L&&note.optString("text").isEmpty()){
                        require(text.length<=50000){"The previous Reader draft is retained; shorten it in the old editor before importing"}
                        step("text",text)
                        check(frame.child("state").child("note").optString("text")==text && frame.child("state").child("note").optLong("updated")>0){"Reader draft could not be adopted. The previous draft was retained."}
                    }
                    check(legacy.edit().putBoolean("sdk_draft_adopted",true).commit())
                }
            }
        } catch(e: CancellationException) { throw e } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message } finally { busy = false }
    }
    private var radioRestored=false
    private var radioOpeningEpoch=0L
    private fun radioViewPrefs()=context.getSharedPreferences("radio-view-"+(carrierOwner?.optString("publisher")?:"bundled"),0)
    internal suspend fun restoreRadioView():Boolean=operations.withLock {
        if(appId!="app.onklav.radio"||sessionUnavailable||busy||pending!=null)return@withLock false
        if(radioRestored)return@withLock true
        if(userActionEpoch!=radioOpeningEpoch){radioRestored=true;saveRadioView();return@withLock true}
        val prefs=radioViewPrefs();val tab=prefs.getString("tab","explore")
        val values=listOf("query","country","language","genre").associateWith{prefs.getString(it,"").orEmpty()}
        busy=true
        try{
            if(tab=="listen"&&frame.child("state").optJSONArray("recent")?.length()?.let{it>0}==true)step("radio.listen")
            else if(tab=="favorites")step("radio.library")
            else{for(key in listOf("genre","country","language","query")){val value=values[key].orEmpty();if(key=="genre")continue;step("radio.$key",value)};step("radio.search")}
            radioRestored=true;saveRadioView();true
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message;radioRestored=true;true}finally{busy=false}
    }
    private fun saveRadioView(){if(!radioRestored||appId!="app.onklav.radio")return;val state=frame.child("state");val edit=radioViewPrefs().edit();for(key in listOf("tab","query","country","language","genre"))edit.putString(key,state.optString(key).take(128));edit.apply()}
    private fun apply(result: JSONObject) { frame = result.getJSONObject("frame"); pending = result.optJSONObject("pending")
        if(appId=="app.onklav.files"&&frame.child("state").optBoolean("external_mode")&&frame.child("state").optString("confirm")=="trash"){
            frame.optJSONObject("confirm")?.put("title","Move to Trash?")?.put("text","You can restore this item from Android Trash. Folders include their contents.")
        }
        saveRadioView() }
    private suspend fun step(id: String, value: String = "") {
        val token = session ?: error("App is not open")
        if(sessionUnavailable){accessNotice=true;error("This app session is no longer available")}
        try{apply(AppSdk.call(context, JSONObject().put("action", "event").put("session", token).put("event", JSONObject().put("action", id).put("value", value).put("selection", selection).put("selection_end", selectionEnd))) as JSONObject)}
        catch(e:CancellationException){throw e}catch(e:Exception){
            try{AppSdk.call(context,JSONObject().put("action","check").put("session",token))}
            catch(cancel:CancellationException){throw cancel}catch(invalid:Exception){retainUnavailableSession()}
            throw e
        }
    }
    fun clipboardAllowed(cap: String): Boolean = session?.let { token -> runCatching {
        AppSdk.sync(context, JSONObject().put("action", "host_clipboard").put("session", token).put("cap", cap)) == true
    }.getOrDefault(false) } ?: false
    fun webComponent(widget: String): JSONObject = if(session==null) JSONObject() else AppSdk.sync(context, JSONObject().put("action","host_web").put("session",session).put("widget",widget)) as JSONObject
    fun webUpdate(widget:String,tab:String,value:JSONObject,epoch:Long=0) { viewModelScope.launch { operations.withLock {
        if(session==null || pending!=null) return@withLock
        runCatching { apply(AppSdk.call(context,JSONObject().put("action","web_event").put("session",session).put("widget",widget).put("tab",tab).put("value",value).put("epoch",epoch)) as JSONObject) }
    } } }
    fun canvasComponent(widget:String,image:Boolean=false):JSONObject = AppSdk.sync(context,JSONObject().put("action","host_canvas").put("session",session).put("widget",widget).put("image",image)) as JSONObject
    fun drawingImage(widget:String):JSONObject = AppSdk.sync(context,JSONObject().put("action","host_drawing_image").put("session",session).put("widget",widget)) as JSONObject
    fun canvasEvent(widget:String,document:String,revision:Long,value:JSONObject,done:()->Unit) {viewModelScope.launch { operations.withLock {
        busy=true
        try { apply(AppSdk.call(context,JSONObject().put("action","canvas_event").put("session",session).put("widget",widget).put("document",document).put("revision",revision).put("value",value)) as JSONObject) }
        catch(e:Exception) { if (e is CancellationException) throw e;error=e.message} finally {busy=false;done()}
    } } }
    fun imageComponent(widget:String):JSONObject = AppSdk.sync(context,JSONObject().put("action","host_image").put("session",session).put("widget",widget)) as JSONObject
    fun mapViewport(widget:String,width:Int,height:Int,operation:String="viewport",dx:Double=0.0,dy:Double=0.0){if(busy||pending!=null||sessionUnavailable)return;viewModelScope.launch{operations.withLock{if(pending!=null||sessionUnavailable)return@withLock;busy=true;try{apply(AppSdk.call(context,JSONObject().put("action","map_event").put("session",session).put("node",widget).put("value",JSONObject().put("width",width).put("height",height).put("operation",operation).put("dx",dx).put("dy",dy)))as JSONObject)}catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{busy=false}}}}
    fun mapDocument(widget:String):JSONObject=AppSdk.sync(context,JSONObject().put("action","map_component").put("session",session).put("node",widget))as JSONObject
    fun pdfDocument(widget:String):JSONObject=AppSdk.sync(context,JSONObject().put("action","pdf_component").put("session",session).put("node",widget))as JSONObject
    fun mediaOwner(widget:String):JSONObject=AppSdk.sync(context,JSONObject().put("action","media_component").put("session",session).put("node",widget))as JSONObject
    internal fun speechDone(widget:String){viewModelScope.launch{var waiting=0;while(pending!=null&&waiting++<100)delay(50);operations.withLock{try{if(pending==null)apply(AppSdk.call(context,JSONObject().put("action","speech_event").put("session",session).put("node",widget)) as JSONObject)}catch(e:Exception){if(e is CancellationException)throw e;error=e.message}}}}
    fun speechComponent(widget: String): JSONObject = AppSdk.sync(context, JSONObject().put("action","host_speech").put("session",session).put("widget",widget)) as JSONObject
    internal val carrierVoiceEligible get()=carrierOwner?.optBoolean("carrier")==true
    internal fun isBuiltinVoice()=appId=="app.onklav.walkie"
    fun voiceRequest(action:String,widget:String,extra:JSONObject=JSONObject()):JSONObject {
        if(action in setOf("voice_floor_request","voice_floor_release")||(action=="voice_commit"&&extra.optBoolean("send")))check(carrierVoiceEligible){"Carrier voice routing is not available to this app"}
        extra.put("action",action).put("session",session).put("widget",widget)
        if(frame.child("state").child("meta").child("session").optString("phase")=="connected")SdkWalkieTransport.checkSetup(context)
        if(action=="voice_commit"&&extra.optBoolean("send")){
            val authorization=AppSdk.sync(context,JSONObject().put("action","host_voice").put("session",session).put("widget",widget)) as JSONObject
            extra.put("facts",SdkWalkieTransport.facts(context,authorization.getString("peer"),authorization.optInt("subscription",-1),carrierOwner))
        }
        if(action=="voice_floor_request"||action=="voice_floor_release") {
            val state=SdkWalkieTransport.state(context,carrierOwner);extra.put("facts",SdkWalkieTransport.facts(context,state.getString("peer"),state.getInt("subscription"),carrierOwner))
        }
        val response=AppSdk.sync(context,extra) as JSONObject
        if(action=="voice_floor_request"||action=="voice_floor_release")SdkWalkieTransport.kick(context)
        if(action=="voice_commit" || (action=="voice_played" && response.has("frame"))) {apply(response);if(action=="voice_commit"&&extra.optBoolean("send"))SdkWalkieTransport.sendQueued(context,extra.getString("capture"),carrierOwner)else SdkWalkieTransport.kick(context)}
        return response
    }
    suspend fun refreshWalkie() = operations.withLock {
        if(!carrierVoiceEligible || session==null || pending!=null || dirty.isNotEmpty())return@withLock
        runCatching {apply(AppSdk.call(context,JSONObject().put("action","walkie_refresh").put("session",session)) as JSONObject)}
    }
    fun hasUnsaved(id: String) = dirty.containsKey(id)
    internal fun draftValue(id:String,canonical:String)=dirty[id]?:canonical
    fun edit(id: String, value: String) {
        if(speechCaret?.id==id) speechCaret=null
        dirty[id] = value; debounce?.cancel()
        debounce = viewModelScope.launch { delay(500); flush() }
    }
    private suspend fun flushLocked() { val changes = dirty.toList(); for ((key,value) in changes) { if(pending!=null)return;step(key,value); if(dirty[key] == value) dirty.remove(key) } }
    fun flush() { if(dirty.isNotEmpty()) viewModelScope.launch { operations.withLock { try { flushLocked() } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message } } } }
    var userActionEpoch by mutableLongStateOf(0L);private set
    fun action(id: String, value: String = "", fromHostDismiss:Boolean=false) { if(!fromHostDismiss)userActionEpoch++;debounce?.cancel(); viewModelScope.launch { operations.withLock {
        if(pending!=null||sessionUnavailable)return@withLock
        busy = true; try {
            if(appId=="app.onklav.files"&&id in setOf("copy","move","paste","paste.here","clipboard.cancel")){
                flushLocked()
                if(pending==null) handleFilesClipboard(id)
                return@withLock
            }
            if(appId=="app.onklav.files"&&id.startsWith("entry:")){
                val target=id.removePrefix("entry:");val status=withContext(Dispatchers.IO){AppSdk.folderVault(context,"status",target)}
                if(status.optBoolean("locked")){vaultRequest=status.put("navigate",target);return@withLock}
            }
            flushLocked(); if(pending==null)step(id,value) } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message } finally { busy = false }
    } } }
    private suspend fun handleFilesClipboard(action:String){
        val state=frame.child("state")
        val selected=state.child("selected")
        val android=state.optBoolean("external_mode")
        if(action=="clipboard.cancel"){filesClipboard=null;return}
        if(action=="copy"||action=="move"){
            check(selected.optString("id").isNotEmpty()&&!selected.optBoolean("protected")&&
                !selected.optBoolean("trash")&&!selected.optBoolean("connected_root")&&
                (action=="copy"||selected.optBoolean("editable",true))){"This item is protected."}
            filesClipboard=FilesClipboard(JSONObject(selected.toString()),android,action=="move")
            step("menu.close")
            return
        }
        val clipboard=filesClipboard?:return
        val intoFolder=action=="paste"&&selected.optBoolean("folder")
        val folder=if(intoFolder) selected.optString("id") else state.optString("folder")
        check(filesWritable(folder,state.optBoolean("in_trash"))){"Choose a writable destination folder."}
        val token=session?:return
        if(frame.optBoolean("menu_open"))step("menu.close")
        val borrowed=FileSelectionSession(context).also{it.borrow(token,frame)}
        try{
            fileStatus=if(clipboard.move)"Moving…" else "Copying…"
            withContext(Dispatchers.IO){
                val job=currentCoroutineContext()
                val authorize:()->Unit={
                    job.ensureActive()
                    AppSdk.sync(context,JSONObject().put("action","check").put("session",token))
                    if(android||clipboard.android)filesPermission(context,true)
                    if(android&&!clipboard.android)filesCapability(context,"files.export")
                }
                if(intoFolder&&!android)borrowed.event("entry:$folder")
                val path=if(!android&&!clipboard.android)borrowed.breadcrumbs() else null
                FilesTransfer(context,borrowed,authorize).paste(clipboard,folder,android,path)
            }
            if(clipboard.move)filesClipboard=null
        }catch(e:FilesMoveIncomplete){
            filesClipboard=null
            throw e
        }finally{
            fileStatus=null
            apply(borrowed.result)
            if(pending==null&&currentCoroutineContext().isActive)step("refresh")
        }
    }
    internal fun androidTrash(empty:Boolean){
        if(appId!="app.onklav.files"||busy||pending!=null)return
        viewModelScope.launch{operations.withLock{
            if(!frame.child("state").optBoolean("external_mode")||frame.child("state").optString("folder")!="android-trash")return@withLock
            val token=session?:return@withLock;busy=true
            try{withContext(Dispatchers.IO){
                val authorize={AppSdk.sync(context,JSONObject().put("action","check").put("session",token));filesPermission(context);filesPermission(context,true)}
                authorize();ExternalFiles(context,"app.onklav.files").run(JSONObject().put("operation",if(empty)"empty"else"restore_all").put("folder","android-trash"),authorize)
            };step("refresh")}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message;runCatching{step("refresh")}}finally{busy=false}
        }}
    }
    internal fun filesLocation(android:Boolean){
        if(appId!="app.onklav.files"||busy||pending!=null)return
        viewModelScope.launch{operations.withLock{
            busy=true
            try{flushLocked();if(pending==null&&frame.child("state").optJSONObject("destination")==null){step("home");if(android&&pending==null)step("entry:external")}}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}
        }}
    }
    internal suspend fun refreshFiles()=operations.withLock{
        if((appId!="app.onklav.files"&&!frame.optBoolean("file_browser"))||session==null)return@withLock
        try{AppSdk.call(context,JSONObject().put("action","check").put("session",session));if(pending==null&&!frame.optBoolean("menu_open"))step("refresh")}
        catch(e:CancellationException){throw e}catch(e:Exception){retainUnavailableSession();error=e.message?:"Files access is off"}
    }
    internal fun webDownloadSession(): String = session ?: error("Reopen the Browser to download")
    internal fun checkWebDownload(token: String, widget: String) {
        AppSdk.sync(context, JSONObject().put("action", "host_web").put("session", token).put("widget", widget))
    }
    internal suspend fun saveWebFile(token: String, widget: String, name: String, mime: String): JSONObject =
        AppSdk.call(context, JSONObject().put("action", "web_file_save").put("session", token)
            .put("widget", widget).put("name", name).put("mime", mime)) as JSONObject
    internal val browserDownloads = BrowserDownloads(context, this, viewModelScope)
    internal suspend fun cameraAction(operation:String,args:JSONObject=JSONObject()):JSONObject {val result=AppSdk.call(context,args.put("action","native_camera").put("session",session).put("operation",operation));return if(result is JSONObject)result else JSONObject().put("done",result)}
    internal suspend fun saveVideoFile(widget:String,name:String,mime:String,folder:String):JSONObject=AppSdk.call(context,JSONObject().put("action","web_file_save").put("session",session).put("widget",widget).put("name",name).put("mime",mime).put("folder",folder)) as JSONObject
    internal suspend fun openNoteRecord(id:String)=operations.withLock {
        check(appId=="app.onklav.notes");flushLocked()
        apply(AppSdk.call(context,JSONObject().put("action","note_open").put("session",session).put("note",id)) as JSONObject)
    }
    internal suspend fun openWebLink(url:String)=operations.withLock {
        check(appId=="app.onklav.browser")
        flushLocked();step("address",SdkWebPolicy.external(url));step("go")
    }
    internal suspend fun chooseWalkieRecipient(phone:String)=operations.withLock {
        check(appId=="app.onklav.walkie")
        flushLocked()
        val number=app.onklav.shared.phoneInput(phone)
        val label=withContext(Dispatchers.IO){MessageRepository(context).contactName(number)}
        val token=session ?: error("Walkie-Talkie is not open")
        check(pending==null) { "Finish the current Walkie-Talkie action first" }
        val selection=AppSdk.call(context,JSONObject().put("action","event").put("session",token)
            .put("event",JSONObject().put("action","contacts").put("value",""))) as JSONObject
        val request=selection.getJSONObject("pending")
        check(request.getString("service")=="contacts.pick") { "Contact selection is unavailable" }
        try {
            val result=AppSdk.call(context,JSONObject().put("action","reply").put("session",token)
                .put("token",request.getString("token"))
                .put("reply",JSONObject().put("ok",true).put("value",
                    JSONObject().put("phone",number).put("name",label.take(80))))) as JSONObject
            apply(result)
        } catch (e:Exception) {
            apply(selection)
            throw e
        }
    }
    private var videoLaunchKey:String?=null
    internal suspend fun openMessageVideo(video:MessageVideo,key:Long)=operations.withLock {
        val requestKey="${video.id}:${video.start}:$key"
        if(videoLaunchKey==requestKey)return@withLock
        busy=true
        try {
            check(appId=="app.onklav.youtube"){"Open videos in YouTube"}
            flushLocked()
            val opened=AppSdk.call(context,JSONObject().put("action","video_open").put("session",session)
                .put("video",video.id).put("start",video.start)) as JSONObject
            webPool.autoplayVideo=video.id
            apply(opened)
            videoLaunchKey=requestKey
        }catch(e:Exception){ if (e is CancellationException) throw e;error=e.message}finally{busy=false}
    }
    fun closeFromHost(done:()->Unit){browserDownloads.cancelTransfers();debounce?.cancel();viewModelScope.launch{operations.withLock{
        try{pending?.optString("token")?.let{SdkFeedService.cancel(it);SdkHttpService.cancel(it);SdkRadio.cancel(it);SdkPodcasts.cancel(it);SdkEbooks.cancel(it);SdkAccounts.cancel(it)};session?.let{AppSdk.call(context,JSONObject().put("action","close").put("session",it))}}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        finally{session=null;frame=JSONObject();webPool.entries.clear();launchedToken=null;pending=null;dirty.clear();speechCaret=null;webPool.autoplayVideo=null;busy=false;done()}
    }}}
    fun close(done: () -> Unit) { browserDownloads.cancelTransfers();debounce?.cancel(); viewModelScope.launch { operations.withLock {
        busy = true
        try { pending?.optString("token")?.let { SdkFeedService.cancel(it);SdkHttpService.cancel(it);SdkRadio.cancel(it);SdkPodcasts.cancel(it);SdkEbooks.cancel(it);SdkAccounts.cancel(it) };flushLocked();releaseDeviceLeases(); session?.let { AppSdk.call(context, JSONObject().put("action", "close").put("session", it)) }; session = null;frame=JSONObject();dirty.clear();launchedToken=null;webPool.entries.clear();error=null;speechCaret=null;pending = null;videoLaunchKey=null;webPool.autoplayVideo=null;if(appId=="app.onklav.walkie")SdkWalkieTransport.kick(context);done() }
        catch(e: Exception) { if (e is CancellationException) throw e; error = e.message } finally { busy = false }
    } } }
    fun launchPending(token:String):Boolean {
        if(token.isBlank()||pending?.optString("token")!=token||session==null||sessionUnavailable||launchedToken==token)return false
        launchedToken=token;return true
    }
    override fun onCleared(){
        releaseDeviceLeases();debounce?.cancel();pending?.optString("token")?.let{SdkFeedService.cancel(it);SdkHttpService.cancel(it);SdkRadio.cancel(it);SdkPodcasts.cancel(it);SdkEbooks.cancel(it);SdkAccounts.cancel(it)}
        val previous=session;session=null;pending=null;launchedToken=null
        webPool.entries.clear();webPool.onBack=null;webPool.autoplayVideo=null
        previous?.let{AppSdk.releaseSession(context,it)}
        super.onCleared()
    }
    fun authorizeNow(p:JSONObject):JSONObject = AppSdk.sync(context,JSONObject().put("action","authorize").put("session",session).put("token",p.getString("token"))) as JSONObject
    fun confirmWalkieNow(p:JSONObject):JSONObject {
        check(carrierVoiceEligible){"Carrier routing is not available to this app"}
        return SdkWalkieTransport.confirm(context){authorizeNow(p)}
    }
    fun cameraCommitNow(p:JSONObject):JSONObject=AppSdk.sync(context,JSONObject().put("action","camera_host").put("session",session).put("token",p.getString("token"))) as JSONObject
    internal fun handoffFileNow(p:JSONObject):JSONObject=AppSdk.sync(context,JSONObject().put("action","handoff_host").put("session",session).put("token",p.getString("token")).put("operation","resolve")) as JSONObject
    internal fun privateFileNow(p:JSONObject,operation:String,args:JSONObject=JSONObject()):JSONObject=AppSdk.sync(context,JSONObject().put("action","blob_host").put("session",session).put("token",p.getString("token")).put("operation",operation).put("args",args)) as JSONObject
    suspend fun files(p:JSONObject,operation:String,extra:JSONObject=JSONObject()):JSONObject = AppSdk.call(context,extra.put("action","library_host").put("session",session).put("token",p.getString("token")).put("operation",operation)) as JSONObject
    suspend fun authorize(p: JSONObject): JSONObject = AppSdk.call(context, JSONObject().put("action", "authorize").put("session", session).put("token", p.getString("token"))) as JSONObject
    suspend fun reply(p: JSONObject, value: Any? = true, failure: String? = null) = withContext(NonCancellable){operations.withLock {
        if(pending?.optString("token")!=p.optString("token")||session==null||sessionUnavailable)return@withLock
        fun request(ok:Boolean,payload:Any?)=JSONObject().put("action","reply").put("session",session).put("token",p.getString("token"))
            .put("reply",JSONObject().put("ok",ok).put(if(ok)"value"else"error",payload))
        try{
            apply(AppSdk.call(context,request(failure==null,failure?:value)) as JSONObject);error=null
        }catch(e:Exception){
            if(p.optString("service")=="radio.play"&&value is JSONObject)runCatching{SdkMedia.pauseUncommittedRadio(authorizeNow(p).getJSONObject("owner"),value.getJSONObject("station").getString("id"))}
            // A rejected result has not consumed the SDK token. Acknowledge failure
            // before allowing another event; never just erase the host's pending copy.
            try{
                apply(AppSdk.call(context,request(false,"The action could not finish. Please try again.")) as JSONObject)
                error="The action could not finish. You can try again."
            }catch(second:Exception){retainUnavailableSession();error="This app action could not be recovered. Reopen this app to continue."}
        }
    }}

}
