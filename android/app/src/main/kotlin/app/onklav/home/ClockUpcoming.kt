package app.onklav.home

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Date

internal object ClockUpcoming {
    const val SHOW = "clock.upcoming"
    const val DISMISS = "clock.dismiss-upcoming"
    private const val CHANNEL = "clock-upcoming"
    private const val NOTIFICATION_ID = 832
    private const val LEAD_TIME = 2 * 60 * 60 * 1000L
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun reminder(context: Context) = PendingIntent.getBroadcast(
        context, 832, Intent(context, ClockReceiver::class.java).setAction(SHOW), FLAGS,
    )

    fun refresh(context: Context) {
        runCatching { update(context) }.onFailure { Log.w("ClockUpcoming", "Could not refresh upcoming alarms", it) }
    }

    @SuppressLint("MissingPermission")
    private fun update(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val callback = reminder(context)
        manager.cancel(callback)
        val now = System.currentTimeMillis()
        val next = if (ClockDisk.allowed(context) && ClockDisk.exact(context) && notifications.areNotificationsEnabled()) {
            ClockDisk.alarms(context).filter { it.enabled && it.next > now }.minByOrNull { it.next }
        } else null
        if (next == null || next.next - now > LEAD_TIME) {
            notifications.cancel(NOTIFICATION_ID)
            if (next != null) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.next - LEAD_TIME, callback)
            return
        }

        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Upcoming alarms", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            context, 832, Intent(context, MainActivity::class.java).putExtra("open_native_tool", "clock"), FLAGS,
        )
        // A notification for an older occurrence must never dismiss a rescheduled alarm.
        val dismiss = PendingIntent.getBroadcast(
            context, 0, Intent(context, ClockReceiver::class.java).setAction(DISMISS)
                .setData(Uri.parse("onklav-clock:///upcoming/${next.id}/${next.next}"))
                .putExtra("id", next.id).putExtra("at", next.next), FLAGS,
        )
        val time = DateFormat.getTimeFormat(context).format(Date(next.next))
        notifications.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Upcoming alarm · $time")
                .setContentText(next.label.ifBlank { "Alarm" })
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(next.next - now)
                .addAction(0, "Dismiss", dismiss)
                .build(),
        )
    }
}
