package app.onklav.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal data class UpdateVersion(val major:Int,val minor:Int,val patch:Int,val rc:Int?):Comparable<UpdateVersion> {
    val base get()="$major.$minor.$patch"
    val label get()=base+(rc?.let{"-rc$it"}?:"")
    override fun compareTo(other:UpdateVersion):Int {
        for((a,b) in listOf(major to other.major,minor to other.minor,patch to other.patch))if(a!=b)return a.compareTo(b)
        return (rc?:Int.MAX_VALUE).compareTo(other.rc?:Int.MAX_VALUE)
    }
    companion object {
        fun parse(value:String):UpdateVersion? {
            val m=Regex("v?([0-9]{1,6})\\.([0-9]{1,6})\\.([0-9]{1,6})(?:-rc([0-9]{1,6}))?").matchEntire(value.lowercase())?:return null
            return UpdateVersion(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt(),m.groupValues[4].toIntOrNull())
        }
    }
}
internal data class OnKlavRelease(val version:UpdateVersion,val url:String,val bytes:Long,val digest:String) {
    val tag get()="v${version.label}"
    val filename get()="OnKlav-${version.base}"+(if(version.rc!=null)"-RC"else"")+".apk"
    fun json()=JSONObject().put("version",version.label).put("url",url).put("bytes",bytes).put("digest",digest)
    fun validate():OnKlavRelease {
        require(bytes in 1..UpdateFeed.MAX_APK && digest.matches(Regex("[a-f0-9]{64}"))) {"This release does not have valid download verification details."}
        require(url=="https://github.com/JustTrySomthing/OnKlav/releases/download/$tag/$filename") {"This update is not an official OnKlav download."}
        return this
    }
    companion object {
        fun cached(j:JSONObject)=OnKlavRelease(requireNotNull(UpdateVersion.parse(j.getString("version"))),j.getString("url"),j.getLong("bytes"),j.getString("digest")).validate()
    }
}
internal object UpdateFeed {
    const val ENDPOINT="https://api.github.com/repos/JustTrySomthing/OnKlav/releases?per_page=50"
    const val MAX_APK=256L*1024*1024
    fun select(rows:JSONArray,current:UpdateVersion,previews:Boolean):OnKlavRelease? {
        require(rows.length()<=50)
        return (0 until rows.length()).mapNotNull { n ->
            val row=rows.getJSONObject(n)
            if(row.optBoolean("draft"))return@mapNotNull null
            val version=UpdateVersion.parse(row.optString("tag_name"))?:return@mapNotNull null
            if(version<=current||(!previews&&(row.optBoolean("prerelease")||version.rc!=null)))return@mapNotNull null
            val name="OnKlav-${version.base}"+(if(version.rc!=null)"-RC"else"")+".apk"
            val assets=row.optJSONArray("assets")?:return@mapNotNull null
            val matches=(0 until assets.length()).map{assets.getJSONObject(it)}.filter{it.optString("name")==name&&it.optString("state")=="uploaded"}
            if(matches.size!=1)return@mapNotNull null
            val asset=matches.single()
            OnKlavRelease(version,asset.getString("browser_download_url"),asset.getLong("size"),asset.optString("digest").removePrefix("sha256:")).validate()
        }.maxByOrNull{it.version}
    }
    internal fun allowed(url:String):Boolean=runCatching {
        val u=URI(url)
        u.scheme=="https" && u.rawUserInfo==null && u.port in setOf(-1,443) && u.fragment==null &&
            u.host in setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    }.getOrDefault(false)
    /** Public HTTPS only. No cookies, GitHub credentials or authorization headers. */
    private suspend fun <T> request(url:String,accept:String,read:suspend(HttpURLConnection)->T):T {
        var next=url
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(allowed(next)){"GitHub returned an unsupported download address."}
            val c=URI(next).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=20000;c.useCaches=false
            c.setRequestProperty("Accept",accept);c.setRequestProperty("User-Agent","OnKlav/${BuildConfig.VERSION_NAME}")
            c.setRequestProperty("Accept-Encoding","identity")
            try {
                val code=c.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    next=URI(next).resolve(c.getHeaderField("Location")?:error("GitHub returned an incomplete redirect.")).toString()
                } else {
                    check(code==200) {if(code in setOf(403,429))"GitHub update checks are temporarily limited. Try again later."else"GitHub could not complete this request (HTTP $code). Try again."}
                    return read(c)
                }
            } finally {c.disconnect()}
        }
        error("GitHub returned too many redirects.")
    }
    suspend fun releases():JSONArray=request(ENDPOINT,"application/vnd.github+json") { c ->
        c.inputStream.use { input ->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;check(out.size()+n<=4*1024*1024){"The update listing is too large."};out.write(buffer,0,n)}
            JSONArray(out.toString("UTF-8"))
        }
    }
    internal suspend fun copy(input:InputStream,target:File,release:OnKlavRelease,progress:suspend(Long)->Unit) {
        release.validate()
        val digest=MessageDigest.getInstance("SHA-256");var total=0L
        target.outputStream().use { out ->
            val buffer=ByteArray(64*1024)
            while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;total+=n
                check(total<=release.bytes){"The update download is larger than expected."}
                digest.update(buffer,0,n);out.write(buffer,0,n);progress(total)
            }
            out.fd.sync()
        }
        check(total==release.bytes&&hex(digest.digest())==release.digest){"The update download is incomplete or failed verification. Please download it again."}
    }
    suspend fun download(release:OnKlavRelease,target:File,progress:suspend(Long)->Unit)=withTimeout(10*60*1000L) {
        request(release.url,"application/octet-stream") { c ->
            check(c.contentLengthLong<0||c.contentLengthLong==release.bytes){"The update download size has changed. Check for updates again."}
            c.inputStream.use {copy(it,target,release,progress)}
        }
    }
    fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 255)}
}
internal data class UpdateApkIdentity(val packageName:String,val version:String,val code:Long,val certificates:Set<String>,val minSdk:Int)
internal object UpdateApk {
    fun identity(info:PackageInfo)=UpdateApkIdentity(info.packageName,info.versionName.orEmpty(),info.longVersionCode,
        info.signingInfo?.apkContentsSigners?.map{UpdateFeed.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))}?.toSet().orEmpty(),info.applicationInfo?.minSdkVersion?:Int.MAX_VALUE)
    fun validate(installed:UpdateApkIdentity,candidate:UpdateApkIdentity,release:OnKlavRelease,sdk:Int) {
        check(candidate.packageName==installed.packageName){"This APK is not an update for this OnKlav installation."}
        check(candidate.certificates.isNotEmpty()&&candidate.certificates==installed.certificates){"This update does not use OnKlav’s existing signing certificate."}
        check(candidate.code>installed.code&&UpdateVersion.parse(candidate.version)==release.version&&
            UpdateVersion.parse(installed.version)?.let{release.version>it}==true){"This APK is not a newer matching OnKlav version."}
        check(candidate.minSdk<=sdk){"This update needs a newer version of Android."}
    }
    fun verify(c:Context,file:File,release:OnKlavRelease) {
        release.validate();check(file.isFile&&file.length()==release.bytes){"Download the update again before installing."}
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val buffer=ByteArray(64*1024);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        check(UpdateFeed.hex(digest.digest())==release.digest){"The downloaded update failed verification. Please download it again."}
        val pm=c.packageManager
        val apk=pm.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)?:error("Android could not read the downloaded update.")
        val installed=pm.getPackageInfo(c.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        validate(identity(installed),identity(apk),release,android.os.Build.VERSION.SDK_INT)
    }
    fun installer(c:Context,file:File)=Intent(Intent.ACTION_INSTALL_PACKAGE)
        .setDataAndType(FileProvider.getUriForFile(c,c.packageName+".updates",file),"application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
class OnKlavUpdateProvider:FileProvider()
internal data class UpdateUi(val busy:Boolean=false,val downloading:Boolean=false,val downloaded:Long=0,val release:OnKlavRelease?=null,
    val ready:Boolean=false,val message:String="Check GitHub for the latest OnKlav version.",val error:String?=null,
    val prompt:Boolean=false,val installRequested:Boolean=false)
class OnKlavUpdateModel(app:Application):AndroidViewModel(app) {
    private val c=app.applicationContext
    private val prefs=UpdateChecks.prefs(c)
    private val dir=File(c.cacheDir,"onklav-updates").apply{mkdirs()}
    private val file=File(dir,"update.apk")
    private var job:Job?=null
    internal var ui by mutableStateOf(UpdateUi());private set
    var automatic by mutableStateOf(prefs.getBoolean("automatic",true));private set
    var previews by mutableStateOf(UpdateChecks.includesPreviews(prefs));private set
    init {
        task {
            UpdateChecks.cleanInstalledDownload(c)
            File(dir,"update.part").delete()
            val available=cachedRelease("available")
            if(available!=null)ui=ui.copy(release=available,message=availableMessage(available),prompt=shouldPrompt(available))
            else prefs.edit().remove("available").apply()
            val saved=cachedRelease("ready")
            if(saved!=null&&(available==null||available==saved)) {
                val valid=withContext(Dispatchers.IO){runCatching{UpdateApk.verify(c,file,saved)}.isSuccess}
                if(valid) {
                    if(available==null) prefs.edit().putString("available",saved.json().toString()).apply()
                    ui=ui.copy(release=saved,ready=true,message="Update downloaded. Ready to install.",prompt=shouldPrompt(saved))
                }
                else clearDownload()
            } else clearDownload()
            if(due())runCheck(true)
        }
    }
    private fun cachedRelease(key:String)=UpdateChecks.cached(prefs,key)
        ?.takeIf {it.version>UpdateChecks.current()&&(previews||it.version.rc==null)}
    private fun shouldPrompt(release:OnKlavRelease)=UpdateChecks.shouldPrompt(prefs,release)
    private fun availableMessage(release:OnKlavRelease)="OnKlav ${release.version.label} is available."
    private fun due():Boolean {
        val online=c.getSystemService(ConnectivityManager::class.java)?.activeNetwork!=null
        return online&&UpdateChecks.due(prefs,UpdateChecks.DAY)
    }
    private fun task(action:suspend()->Unit) {
        if(job?.isActive==true)return
        job=viewModelScope.launch {
            ui=ui.copy(busy=true,error=null)
            try { action() }
            catch(e:TimeoutCancellationException){ui=ui.copy(error="The update download timed out. Please try again.")}
            catch(e:CancellationException){ui=ui.copy(message="Download cancelled. You can try again.");throw e}
            catch(e:java.io.IOException){ui=ui.copy(error="Could not reach GitHub. Check your connection and try again.")}
            catch(e:Exception){ui=ui.copy(error=e.message?:"Could not connect to GitHub. Check your connection and try again.")}
            finally {ui=ui.copy(busy=false,downloading=false)}
        }
    }
    fun automaticCheck(){
        if(job?.isActive==true)return
        val available=cachedRelease("available")
        if(available!=ui.release)showResult(available,true)
        else if(available!=null&&!ui.installRequested)ui=ui.copy(prompt=shouldPrompt(available))
        if(due())task{runCheck(true)}
    }
    fun checkNow()=task{runCheck(false)}
    fun changeAutomatic(value:Boolean){
        automatic=value;prefs.edit().putBoolean("automatic",value).apply()
        UpdateChecks.schedule(c)
        ui=ui.copy(prompt=value&&ui.release?.let(::shouldPrompt)==true)
        if(value)automaticCheck()
    }
    fun changePreviews(value:Boolean){previews=value;prefs.edit().putBoolean("previews",value).apply();if(!ui.busy)checkNow()}
    fun dismissPrompt(){
        ui.release?.let {
            prefs.edit().putString("notified",it.tag).putLong("notifiedAt",System.currentTimeMillis()).apply()
        }
        UpdateChecks.cancelNotice(c)
        ui=ui.copy(prompt=false)
    }
    private fun clearDownload(){
        file.delete();prefs.edit().remove("ready").apply()
        ui=ui.copy(ready=false,installRequested=false)
    }
    private suspend fun runCheck(silent:Boolean) {
        if(!silent)ui=ui.copy(message="Checking GitHub…")
        val result=UpdateChecks.check(prefs,UpdateChecks.DAY.takeIf {silent})?:return
        if(result.release==null)UpdateChecks.cancelNotice(c)
        showResult(result.release,silent)
    }
    private fun showResult(release:OnKlavRelease?,silent:Boolean) {
        if(release!=ui.release){clearDownload()}
        ui=ui.copy(release=release,message=when {
            release==null->"You’re up to date."
            ui.ready->"Update downloaded. Ready to install."
            else->availableMessage(release)
        },prompt=silent&&release?.let(::shouldPrompt)==true)
    }
    fun cancelDownload(){if(ui.downloading)job?.cancel()}
    fun updateNow() {
        if(ui.busy||ui.release==null)return
        dismissPrompt()
        if(ui.ready)ui=ui.copy(installRequested=true,error=null) else download()
    }
    fun consumeInstallRequest(){ui=ui.copy(installRequested=false)}
    private fun download() {
        val release=ui.release?:return
        task {
            clearDownload();ui=ui.copy(downloading=true,downloaded=0,message="Downloading update…",prompt=false)
            val part=File(dir,"update.part")
            try {
                withContext(Dispatchers.IO) {
                    check(dir.usableSpace>release.bytes+16*1024*1024){"Free some space on your phone, then try again."}
                    var at=0L
                    UpdateFeed.download(release,part){bytes->val now=SystemClock.elapsedRealtime();if(now-at>=250||bytes==release.bytes){at=now;withContext(Dispatchers.Main){ui=ui.copy(downloaded=bytes)}}}
                    UpdateApk.verify(c,part,release)
                    check(part.renameTo(file)){"Could not save the verified update. Please try again."}
                }
                prefs.edit().putString("ready",release.json().toString()).apply()
                ui=ui.copy(ready=true,installRequested=true,message="Update verified. Ready to install.")
            } finally {withContext(Dispatchers.IO+NonCancellable){part.delete()}}
        }
    }
    fun install(context:Context) {
        val release=ui.release?:return
        if(!ui.ready)return
        task {
            check(context.packageManager.canRequestPackageInstalls()){ "Allow OnKlav to install updates, then tap Install update." }
            withContext(Dispatchers.IO){
                try { UpdateApk.verify(c,file,release) }
                catch(e:Exception){
                    if(e is CancellationException)throw e
                    withContext(Dispatchers.Main){clearDownload()}
                    throw e
                }
            }
            context.startActivity(UpdateApk.installer(context,file))
            ui=ui.copy(message="Confirm the update in Android’s installer. If you cancel, tap Install update to try again.")
        }
    }
    fun installPermissionDenied(){ui=ui.copy(error="Update installation was not enabled. Tap Install update when you’re ready to allow it.")}
}
