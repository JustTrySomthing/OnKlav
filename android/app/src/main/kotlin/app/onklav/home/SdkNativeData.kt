package app.onklav.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Durable host cleanup intent, written before Rust removal. All mutations use AppSdk's carrier lock. */
internal object SdkNativeData {
    private fun prefs(c:Context)=c.getSharedPreferences("sdk-native-data",0)
    private fun valid(id:String){require(id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&id.length<=128)}
    fun profile(c:Context,id:String):String {valid(id);val generation=prefs(c).getLong("generation.$id",0);return SdkWebPolicy.profile(id)+(if(generation>0)"_$generation"else "")}
    fun pending(c:Context):List<String> = prefs(c).all.keys.filter{it.startsWith("removal.")}.map{it.removePrefix("removal.")}.sorted()
    fun record(c:Context,id:String):JSONObject? {valid(id);return prefs(c).getString("removal.$id",null)?.let(::JSONObject)}
    fun prepare(c:Context,id:String,delete:Boolean){
        valid(id);check(record(c,id)==null){"Finish this app's pending removal first"}
        val generation=prefs(c).getLong("generation.$id",0);check(generation<Long.MAX_VALUE)
        val row=JSONObject().put("delete",delete).put("phase","prepared").put("profile",profile(c,id)).put("generation",generation+1)
        check(prefs(c).edit().putString("removal.$id",row.toString()).commit()){"Could not save app removal; nothing was removed"}
    }
    fun cancel(c:Context,id:String){valid(id);check(prefs(c).edit().remove("removal.$id").commit())}
    fun removed(c:Context,id:String,delete:Boolean,clearAccounts:(Context,String)->Unit=SdkAccountVault::clear,clearSecrets:(Context,String)->Unit=SdkSecrets::deleteApp){
        valid(id);if(id==CLOCK_APP)ClockDisk.stopAll(c);if(id==TORCH_APP)TorchControl.stop(c);if(id==MAIL_APP)c.getSystemService(android.app.job.JobScheduler::class.java).cancel(MailSync.JOB);SdkDeviceLeases.clearApp(id);SdkPdfClient.cancelApp(id);SdkHandoffProvider.clearApp(c,id);SdkJobs.clearApp(c,id);SdkMedia.changed(c,id);clearAccounts(c,id);SdkNotifications.clearApp(c,id);if(record(c,id)==null)prepare(c,id,delete)
        val row=record(c,id)!!;val p=prefs(c)
        check(row.getBoolean("delete")==delete)
        if(row.getString("phase")=="prepared"){
            val edit=p.edit()
            if(delete){if(id==CLOCK_APP)check(c.getSharedPreferences("native-clock",0).edit().clear().commit());if(id==TORCH_APP)check(c.getSharedPreferences("flashlight",0).edit().clear().commit());if(id==CAMERA_APP){check(c.getSharedPreferences("camera-settings",0).edit().clear().commit());val folder=java.io.File(c.filesDir,"camera-pending");check(!folder.exists()||folder.deleteRecursively())};if(id=="app.onklav.email")MailDisk.clear(c);if(id=="app.onklav.videoeditor")check(!java.io.File(c.filesDir,"video-editor").exists()||java.io.File(c.filesDir,"video-editor").deleteRecursively());if(id=="app.onklav.maps"){check(!java.io.File(c.filesDir,"maps").exists()||java.io.File(c.filesDir,"maps").deleteRecursively());MapsNet.clear(c);check(c.getSharedPreferences("maps",0).edit().clear().commit())};if(id=="app.onklav.audioeditor")check(!java.io.File(c.filesDir,"audio-editor").exists()||java.io.File(c.filesDir,"audio-editor").deleteRecursively());edit.putStringSet("pending",p.getStringSet("pending",emptySet()).orEmpty()+row.getString("profile")).putLong("generation.$id",row.getLong("generation"))}
            if(id=="app.onklav.walkie")edit.putBoolean("walkie_cleanup",true).putLong("walkie_cleanup_epoch",p.getLong("walkie_cleanup_epoch",0)+1)
            edit.putStringSet("transport_cleanup",p.getStringSet("transport_cleanup",emptySet()).orEmpty()+id)
            row.put("phase","native");check(edit.putString("removal.$id",row.toString()).commit())
        }
        if(delete&&id==RealSocial.APP)RealSocial.clear(c)
        if(delete){if(id=="app.onklav.write"){check(!java.io.File(c.filesDir,"write").exists()||java.io.File(c.filesDir,"write").deleteRecursively());check(c.getSharedPreferences("write",0).edit().clear().commit())};if(id=="app.onklav.photo")check(!java.io.File(c.filesDir,"photo-editor").exists()||java.io.File(c.filesDir,"photo-editor").deleteRecursively());SdkAudioBooks.clearApp(c,id);SdkPdfPositions.clearApp(c,id);SdkMedia.clearPositions(c,id);clearSecrets(c,id);check(c.getSharedPreferences("sdk-speech-$id",0).edit().clear().commit());check(c.getSharedPreferences("external-files-${AppSdk.desktopId(id)}",0).edit().clear().commit());SdkThumbnails.clear(c,id)}
        // Profile rotation + phase are one commit. Retrying never rotates twice.
        cancel(c,id)
        Handler(Looper.getMainLooper()).post { runCatching { flush(c) }.onFailure{ChatStore.lastError.value="App removed; cleanup is pending: ${it.message}"} }
    }
    internal fun deleteProfile(store:ProfileStore,name:String)=store.deleteProfile(name)||name !in store.allProfileNames
    fun flush(c:Context,deleteProfile:(String)->Boolean={name->
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))false
        else deleteProfile(ProfileStore.getInstance(),name)
    }){
        check(Looper.myLooper()==Looper.getMainLooper())
        val p=prefs(c)
        val walkie=AppSdk.carrierEgress { if(p.getBoolean("walkie_cleanup",false))p.getLong("walkie_cleanup_epoch",0)else null }
        if(walkie!=null){
            SdkWalkieTransport.packageRemoved(c)
            AppSdk.carrierEgress {if(p.getLong("walkie_cleanup_epoch",0)==walkie)check(p.edit().putBoolean("walkie_cleanup",false).commit())}
        }
        val transports=AppSdk.carrierEgress {p.getStringSet("transport_cleanup",emptySet()).orEmpty().toSet()}
        transports.forEach{id->SdkWalkieTransport.packageRemoved(c,id)}
        if(transports.isNotEmpty())AppSdk.carrierEgress{check(p.edit().putStringSet("transport_cleanup",p.getStringSet("transport_cleanup",emptySet()).orEmpty()-transports).commit())}
        val pending=AppSdk.carrierEgress {p.getStringSet("pending",emptySet()).orEmpty().toSet()}
        val deleted=pending.filter{runCatching{deleteProfile(it)}.getOrDefault(false)}.toSet()
        if(deleted.isNotEmpty())AppSdk.carrierEgress {
            val latest=p.getStringSet("pending",emptySet()).orEmpty().toSet()
            check(p.edit().putStringSet("pending",latest-deleted).commit())
        }
    }
}
