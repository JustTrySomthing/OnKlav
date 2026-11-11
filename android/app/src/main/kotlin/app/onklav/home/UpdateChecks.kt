package app.onklav.home

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

internal object UpdateChecks {
    const val DAY = 24 * 60 * 60 * 1000L
    const val OPEN = "app.onklav.home.OPEN_UPDATES"
    private const val HOUR = DAY / 24
    private const val JOB = 4182
    private const val CHANNEL = "onklav-updates"
    private val lock = Mutex()
    var foreground = false

    fun prefs(context: Context) = context.getSharedPreferences("onklav-updates", 0)
    fun current() = requireNotNull(UpdateVersion.parse(BuildConfig.VERSION_NAME))
    fun includesPreviews(prefs: SharedPreferences) = prefs.getBoolean("previews", current().rc != null)
    fun cached(prefs: SharedPreferences, key: String) = prefs.getString(key, null)?.let { json ->
        runCatching { OnKlavRelease.cached(JSONObject(json)) }.getOrNull()
    }

    fun due(prefs: SharedPreferences, interval: Long, now: Long = System.currentTimeMillis()) =
        prefs.getBoolean("automatic", true) &&
            elapsed(prefs.getLong("checked", 0), interval, now) &&
            elapsed(prefs.getLong("attempted", 0), HOUR, now)

    fun shouldPrompt(prefs: SharedPreferences, release: OnKlavRelease, now: Long = System.currentTimeMillis()) =
        prefs.getBoolean("automatic", true) &&
            (prefs.getString("notified", null) != release.tag || elapsed(prefs.getLong("notifiedAt", 0), DAY, now))

    private fun elapsed(last: Long, interval: Long, now: Long) = last == 0L || now < last || now - last >= interval

    data class Result(val release: OnKlavRelease?)

    suspend fun check(prefs: SharedPreferences, interval: Long? = null): Result? = lock.withLock {
        if (interval != null && !due(prefs, interval)) return@withLock null
        prefs.edit().putLong("attempted", System.currentTimeMillis()).apply()
        val release = withContext(Dispatchers.IO) {
            UpdateFeed.select(UpdateFeed.releases(), current(), includesPreviews(prefs))
        }
        prefs.edit().putLong("checked", System.currentTimeMillis()).apply {
            if (release == null) remove("available") else putString("available", release.json().toString())
        }.apply()
        Result(release)
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!prefs(context).getBoolean("automatic", true)) {
            scheduler.cancel(JOB)
            cancelNotice(context)
            return
        }
        if (scheduler.getPendingJob(JOB) == null) {
            val job = JobInfo.Builder(JOB, ComponentName(context, OnKlavUpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(12 * HOUR).setPersisted(true)
                .setBackoffCriteria(HOUR, JobInfo.BACKOFF_POLICY_LINEAR).build()
            scheduler.schedule(job)
        }
    }

    fun cancelNotice(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(CHANNEL, JOB)
    }

    @android.annotation.SuppressLint("MissingPermission") // NotificationManagerCompat checks app-level permission.
    fun notifyAvailable(context: Context, release: OnKlavRelease?) {
        if (release == null) {
            cancelNotice(context)
            return
        }
        if (foreground || !shouldPrompt(prefs(context), release)) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "OnKlav updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, JOB,
            Intent(context, MainActivity::class.java).setAction(OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("OnKlav update available")
            .setContentText("Version ${release.version.label} is ready. Tap to open updates.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
        manager.notify(CHANNEL, JOB, notice)
    }

    fun cleanInstalledDownload(context: Context, installed: UpdateVersion = current()) {
        val prefs = prefs(context)
        val ready = cached(prefs, "ready")
        if (ready != null && ready.version <= installed) {
            File(context.cacheDir, "onklav-updates/update.apk").delete()
            File(context.cacheDir, "onklav-updates/update.part").delete()
            prefs.edit().remove("ready").apply()
        }
        if (cached(prefs, "available")?.version?.let { it <= installed } == true) {
            prefs.edit().remove("available").apply()
            cancelNotice(context)
        }
    }

    suspend fun backgroundCheck(context: Context) {
        // Give the twice-daily job slack without fetching again right after a foreground check.
        check(prefs(context), interval = 10 * HOUR)?.let { notifyAvailable(context, it.release) }
    }
}

class OnKlavUpdateJob : JobService() {
    private var check: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        check = CoroutineScope(Dispatchers.Main).launch {
            var retry = false
            try {
                UpdateChecks.backgroundCheck(applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retry = true
                android.util.Log.w("OnKlavUpdates", "Background update check failed", e)
            } finally {
                if (isActive) jobFinished(params, retry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        check?.cancel()
        return false
    }

    override fun onDestroy() {
        check?.cancel()
        super.onDestroy()
    }
}

class OnKlavUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateChecks.cleanInstalledDownload(context)
            UpdateChecks.schedule(context)
        }
    }
}
