package app.onklav.home

import android.app.*
import android.content.*
import android.net.Uri
import org.json.JSONObject

object ReplyReminders {
    private fun pending(c: Context, key: String) =
        PendingIntent.getBroadcast(
            c,
            0,
            Intent(c, ReplyReminderReceiver::class.java)
                .setAction("remind")
                .setData(Uri.parse("onklav-reminder://message/${Uri.encode(key)}"))
                .putExtra("key", key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(c: Context, key: String, time: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (time == 0L) {
            am.cancel(pending(c, key))
            MessageNotifications.cancelReminder(c,key)
        }
        else
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                maxOf(time, System.currentTimeMillis() + 1000),
                pending(c, key),
            )
    }

    fun restore(c: Context) {
        val messages =
            ChatStore(c).requestSync(JSONObject().put("action", "snapshot")).child("messages")
        messages.keysList().forEach { key ->
            val due = messages.child(key).optLong("remind_at")
            if (due > 0) schedule(c, key, due)
        }
    }

    fun validate(c: Context, time: Long) {
        require(time == 0L || time > System.currentTimeMillis()) { "Choose a time in the future" }
        check(time == 0L || c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            "Allow notifications in Setup so your reminder can reach you"
        }
        check(time == 0L || c.getSystemService(NotificationManager::class.java)
            .getNotificationChannel("reply-reminders")?.importance != NotificationManager.IMPORTANCE_NONE) {
            "Enable the Reply Reminders notification channel in Android Notification Settings"
        }
    }

    fun set(c: Context, m: Message, thread: Long, title: String, time: Long) {
        validate(c, time)
        ChatStore(c)
            .requestSync(
                JSONObject()
                    .put("action", "message")
                    .put("key", m.key)
                    .put("date", m.date)
                    .put("thread", thread)
                    .put("reminder_title", title)
                    .put("reminder_text", m.text)
                    .put("remind_at", time)
            )
        schedule(c, m.key, time)
    }
}

class ReplyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ChatStore.executor.execute {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReplyReminders.restore(context)
                else {
                    val key = intent.getStringExtra("key") ?: return@execute
                    val f =
                        ChatStore(context)
                            .requestSync(JSONObject().put("action", "snapshot"))
                            .flags(key)
                    val due = f.optLong("remind_at")
                    // A broadcast already queued before rescheduling must not fire the new reminder early.
                    if (due > System.currentTimeMillis()) {
                        ReplyReminders.schedule(context, key, due)
                        return@execute
                    }
                    if (
                        due > 0 &&
                            context
                                .getSystemService(NotificationManager::class.java)
                                .areNotificationsEnabled()
                    ) {
                        MessageNotifications.remind(
                            context,
                            key,
                            f.optLong("thread"),
                            f.optString("reminder_title"),
                            f.optString("reminder_text"),
                            f.optLong("date"),
                        )
                        ChatStore(context)
                            .requestSync(
                                JSONObject()
                                    .put("action", "message")
                                    .put("key", key)
                                    .put("remind_at", 0)
                            )
                    }
                }
            } catch (e: Exception) {
                ChatStore.lastError.value = e.message
            } finally {
                pending.finish()
            }
        }
    }
}

fun JSONObject.messageFlags(message: Message): JSONObject {
    val f = flags(message.key)
    return if (message.key.startsWith("out:") || f.optLong("date", message.date) == message.date) f
    else JSONObject()
}
