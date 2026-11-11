package app.onklav.home

import android.content.Context
import android.content.Intent
import android.app.Application
import android.app.job.JobScheduler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class OnKlavUpdatesTest {
    private val old=UpdateVersion.parse("0.101.52-rc1")!!
    private val next=UpdateVersion.parse("0.101.53-rc1")!!
    private val payload="verified apk fixture".toByteArray()
    private fun release(version:UpdateVersion=next)=OnKlavRelease(version,
        "https://github.com/JustTrySomthing/OnKlav/releases/download/v${version.label}/OnKlav-${version.base}"+(if(version.rc!=null)"-RC"else"")+".apk",
        payload.size.toLong(),UpdateFeed.hex(MessageDigest.getInstance("SHA-256").digest(payload)))
    private fun row(r:OnKlavRelease=release(),draft:Boolean=false,preview:Boolean=r.version.rc!=null)=JSONObject()
        .put("tag_name",r.tag).put("draft",draft).put("prerelease",preview)
        .put("assets",JSONArray().put(JSONObject().put("name",r.filename).put("state","uploaded")
            .put("browser_download_url",r.url).put("size",r.bytes).put("digest","sha256:"+r.digest)))
    private fun target()=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"update-test-${java.util.UUID.randomUUID()}.apk")
    @Test fun versionsOrderNumericallyIncludingReleaseCandidatesAndStable() {
        assertTrue(UpdateVersion.parse("0.101.100")!!>UpdateVersion.parse("0.101.99")!!)
        assertTrue(UpdateVersion.parse("0.101.53-rc10")!!>next)
        assertTrue(UpdateVersion.parse("0.101.53")!!>UpdateVersion.parse("0.101.53-rc99")!!)
        assertNull(UpdateVersion.parse("0.101.53-extra"));assertNull(UpdateVersion.parse("../../bad"))
    }
    @Test fun newestPublicReleaseIsSelectedRatherThanFirstListEntry() {
        val rows=JSONArray().put(row(release(old))).put(row()).put(row(release(UpdateVersion.parse("0.101.54-rc1")!!),draft=true))
        assertEquals(next,UpdateFeed.select(rows,old,true)?.version)
        assertNull(UpdateFeed.select(rows,next,true))
    }
    @Test fun stableChannelSkipsPreviewsAndSdkOnlyReleases() {
        val stable=release(UpdateVersion.parse("0.101.53")!!)
        val sdk=row(release(UpdateVersion.parse("0.101.99")!!)).put("assets",JSONArray())
        assertEquals(stable,UpdateFeed.select(JSONArray().put(sdk).put(row()).put(row(stable)),old,false))
        assertNull(UpdateFeed.select(JSONArray().put(row()),old,false))
    }
    @Test fun pendingUpdateSurvivesRestartDuringDailyCooldownUntilDismissed() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val prefs=app.getSharedPreferences("onklav-updates",0)
        val current=UpdateVersion.parse(BuildConfig.VERSION_NAME)!!
        val pending=release(current.copy(patch=current.patch+1,rc=1))
        prefs.edit().putBoolean("previews",true).putString("available",pending.json().toString())
            .putLong("checked",System.currentTimeMillis()).commit()
        val first=OnKlavUpdateModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(pending,first.ui.release)
        assertTrue(first.ui.prompt)

        val reopened=OnKlavUpdateModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(reopened.ui.prompt)
        reopened.dismissPrompt()

        val dismissed=OnKlavUpdateModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(pending,dismissed.ui.release)
        assertFalse(dismissed.ui.prompt)
        assertTrue(UpdateChecks.shouldPrompt(prefs,pending,System.currentTimeMillis()+UpdateChecks.DAY))
    }
    @Test fun backgroundCheckIsPersistentNetworkConstrainedAndCanBeDisabled() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=UpdateChecks.prefs(context)
        val scheduler=context.getSystemService(JobScheduler::class.java)
        UpdateChecks.schedule(context)
        UpdateChecks.schedule(context)
        val job=scheduler.allPendingJobs.single {it.service.className==OnKlavUpdateJob::class.java.name}
        assertTrue(job.isPersisted)
        assertTrue(job.isPeriodic)
        assertNotNull(job.requiredNetwork)
        assertTrue(job.intervalMillis<UpdateChecks.DAY)
        prefs.edit().putBoolean("automatic",false).commit()
        UpdateChecks.schedule(context)
        assertNull(scheduler.getPendingJob(job.id))
    }
    @Test fun cleanupKeepsUninstalledApkAndDeletesItAfterSuccessfulUpgrade() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=UpdateChecks.prefs(context)
        val apk=File(context.cacheDir,"onklav-updates/update.apk").apply {parentFile!!.mkdirs();writeBytes(payload)}
        val part=File(apk.parentFile,"update.part").apply {writeBytes(payload)}
        prefs.edit().putString("ready",release().json().toString()).putString("available",release().json().toString()).commit()
        UpdateChecks.cleanInstalledDownload(context,old)
        assertTrue(apk.isFile)
        assertTrue(prefs.contains("ready"))
        UpdateChecks.cleanInstalledDownload(context,next)
        assertFalse(apk.exists())
        assertFalse(part.exists())
        assertFalse(prefs.contains("ready"))
        assertFalse(prefs.contains("available"))
    }
    @Test fun unsafeMetadataCannotBecomeAnInstallCandidate() {
        val badUrl=row(release().copy(url="https://github.com/other/OnKlav/releases/evil.apk"))
        assertThrows(IllegalArgumentException::class.java){UpdateFeed.select(JSONArray().put(badUrl),old,true)}
        val noDigest=row();noDigest.getJSONArray("assets").getJSONObject(0).remove("digest")
        assertThrows(IllegalArgumentException::class.java){UpdateFeed.select(JSONArray().put(noDigest),old,true)}
        assertThrows(IllegalArgumentException::class.java){release().copy(bytes=UpdateFeed.MAX_APK+1).validate()}
        assertThrows(IllegalArgumentException::class.java){OnKlavRelease.cached(release().json().put("url","http://github.com/file.apk"))}
    }
    @Test fun redirectsRequireExactGithubHttpsHosts() {
        assertTrue(UpdateFeed.allowed("https://release-assets.githubusercontent.com/path?signature=abc"))
        for(url in listOf("http://github.com/file","https://github.com.evil.test/file","https://github.com@evil.test/file","https://user:pass@github.com/file","https://github.com:8080/file","file:///tmp/a.apk"))assertFalse(url,UpdateFeed.allowed(url))
    }
    @Test fun streamedDownloadChecksBytesAndDigestAndReportsProgress()=runBlocking {
        val target=target();var progress=0L
        try {UpdateFeed.copy(ByteArrayInputStream(payload),target,release()){progress=it};assertArrayEquals(payload,target.readBytes());assertEquals(payload.size.toLong(),progress)}finally{target.delete()}
    }
    @Test fun truncatedOversizedOrAlteredDownloadsCannotPass()=runBlocking {
        for(bytes in listOf(payload.dropLast(1).toByteArray(),payload+byteArrayOf(1),ByteArray(payload.size){1})) {
            val target=target()
            try {
                var failed=false
                try{UpdateFeed.copy(ByteArrayInputStream(bytes),target,release()) {}}catch(e:IllegalStateException){failed=true}
                assertTrue("Invalid update bytes must fail",failed)
            }finally{target.delete()}
        }
    }
    @Test fun cancellingDownloadStopsBeforeTheRestOfThePayloadIsWritten()=runBlocking {
        val bytes=ByteArray(200_000){42};val r=release().copy(bytes=bytes.size.toLong(),digest=UpdateFeed.hex(MessageDigest.getInstance("SHA-256").digest(bytes)))
        val target=target()
        try {
            val job=launch {
                UpdateFeed.copy(ByteArrayInputStream(bytes),target,r){currentCoroutineContext().cancel()}
            }
            job.join();assertTrue(job.isCancelled);assertTrue(target.length()<bytes.size)
        }finally{target.delete()}
    }
    private fun installed()=UpdateApkIdentity("app.onklav.home.prototype.rewrite",old.label,231,setOf("original-cert"),29)
    private fun candidate()=installed().copy(version=next.label,code=232)
    @Test fun apkRequiresSamePackageSignerNewerBuildAndCompatibleAndroid() {
        UpdateApk.validate(installed(),candidate(),release(),29)
        for(bad in listOf(candidate().copy(packageName="other.app"),candidate().copy(certificates=setOf("different-cert")),
            candidate().copy(certificates=emptySet()),candidate().copy(code=231),candidate().copy(version=old.label),candidate().copy(minSdk=30))) {
            assertThrows(IllegalStateException::class.java){UpdateApk.validate(installed(),bad,release(),29)}
        }
    }
    @Test fun alteredCachedApkIsRejectedBeforeArchiveParsing() {
        val target=target();target.writeBytes(ByteArray(payload.size){0})
        try{assertThrows(IllegalStateException::class.java){UpdateApk.verify(ApplicationProvider.getApplicationContext(),target,release())}}finally{target.delete()}
    }
    @Test fun installUsesReadOnlyContentUriAndProviderRejectsOtherPrivateFiles() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val file=File(c.cacheDir,"onklav-updates/update.apk").apply{parentFile!!.mkdirs();writeBytes(payload)}
        val intent=UpdateApk.installer(c,file)
        assertEquals(Intent.ACTION_INSTALL_PACKAGE,intent.action)
        assertEquals("content",intent.data!!.scheme)
        assertEquals(c.packageName+".updates",intent.data!!.authority)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.flags)
        assertEquals("application/vnd.android.package-archive",intent.type)
        assertThrows(IllegalArgumentException::class.java){FileProvider.getUriForFile(c,c.packageName+".updates",File(c.filesDir,"private.json"))}
    }
}
