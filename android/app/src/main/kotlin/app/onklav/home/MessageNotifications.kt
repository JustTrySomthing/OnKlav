package app.onklav.home

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import app.onklav.shared.R
import java.util.Calendar
import org.json.JSONObject

object MessageNotifications {
    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun quiet(c: Context): Boolean {
        val p = c.getSharedPreferences("quiet_hours", 0)
        if (!p.getBoolean("enabled", false)) return false
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = p.getInt("start", 1320)
        val end = p.getInt("end", 480)
        val day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val owner = if (start > end && minute < end) (day + 6) % 7 else day
        return p.getInt("days", 127) and (1 shl owner) != 0 &&
            if (start < end) minute in start until end else minute >= start || minute < end
    }

    private fun channels(c: Context) {
        manager(c)
            .createNotificationChannels(
                listOf(
                    NotificationChannel(
                        "messages",
                        "Messages",
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                    NotificationChannel(
                        "message-attention",
                        "Messaging Needs Attention",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                    NotificationChannel(
                        "reply-reminders",
                        "Reply Reminders",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            )
    }

    private fun open(c: Context, thread: Long, key: String? = null, date: Long = 0) =
        PendingIntent.getActivity(
            c,
            key?.hashCode() ?: thread.hashCode(),
            Intent(c, MainActivity::class.java)
                .setData(key?.let { android.net.Uri.parse("onklav-reminder://open/${android.net.Uri.encode(it)}") } ?: android.net.Uri.parse("onklav-message://thread/$thread"))
                .putExtra("open_thread", thread)
                .putExtra("open_message_key", key)
                .putExtra("open_message_date", date)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun incoming(c: Context, thread: Long, address: String, body: String, fileThread:Boolean=true) {
        // Filing must happen before notification preferences/permissions are checked.
        val state = if(fileThread)ChatStore(c).incomingThread(thread, address)else ChatStore(c).requestSync(JSONObject().put("action","snapshot"))
        channels(c)
        val normalized = phoneKey(address)
        if (
            state.optJSONArray("blocked")?.let { a ->
                (0 until a.length()).any { phoneKey(a.getString(it)) == normalized }
            } == true
        ) { cancel(c,thread);return }
        val prefs = c.getSharedPreferences("notification_preferences", 0)
        val sound = prefs.getBoolean("sound", true)
        val vibration = prefs.getBoolean("vibrate", true)
        val badge = prefs.getBoolean("badges", true)
        val defaultChannel =
            "messages-" + (if (sound) 1 else 0) + (if (vibration) 1 else 0) + (if (badge) 1 else 0)
        manager(c)
            .createNotificationChannel(
                NotificationChannel(defaultChannel, "Messages", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        if (!sound) setSound(null, null)
                        enableVibration(vibration)
                        setShowBadge(badge)
                    }
            )
        val channel=if(sound && vibration && badge && c.getSharedPreferences("conversation_sounds",0).getBoolean(thread.toString(),false)) conversationChannel(c,thread,runCatching{MessageRepository(c).contactName(address)}.getOrDefault(address)) else defaultChannel
        val notifyId = thread.hashCode()
        val read =
            PendingIntent.getBroadcast(
                c,
                notifyId,
                Intent(c, MessageNotificationReceiver::class.java)
                    .setAction("mark_read")
                    .setData(android.net.Uri.parse("onklav-message://thread/$thread"))
                    .putExtra("thread", thread),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val reply =
            PendingIntent.getBroadcast(
                c,
                notifyId,
                Intent(c, MessageNotificationReceiver::class.java)
                    .setAction("reply")
                    .setData(android.net.Uri.parse("onklav-message://thread/$thread"))
                    .putExtra("thread", thread)
                    .putExtra("address", address),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        val title = runCatching { MessageRepository(c).contactName(address) }.getOrDefault(address)
        val b =
            NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ref_nav_messages)
                .setContentTitle(title)
                .setContentText(body.take(200))
                .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(2000)))
                .setContentIntent(open(c, thread))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSilent(quiet(c) || state.thread(thread).optBoolean("muted"))
                .addAction(0, "Mark Read", read)
                .addAction(
                    NotificationCompat.Action.Builder(0, "Reply", reply)
                        .addRemoteInput(
                            RemoteInput.Builder("reply_text").setLabel("Message").build()
                        )
                        .setAllowGeneratedReplies(false)
                        .build()
                )
        if (!prefs.getBoolean("vibrate", true)) b.setVibrate(longArrayOf(0))
        if (prefs.getBoolean("badges", true)) b.setNumber(1) else b.setNumber(0)
        runCatching { manager(c).notify("thread:$thread",0,b.build()) }
    }

    fun picturePending(c:Context,id:String,sender:String,text:String){
        require(id.matches(Regex("[a-f0-9]{32}")))
        val state=ChatStore(c).requestSync(JSONObject().put("action","snapshot"))
        val senderKey=phoneKey(sender)
        if(senderKey.isNotEmpty()&&state.optJSONArray("blocked")?.let{a->(0 until a.length()).any{phoneKey(a.getString(it))==senderKey}}==true){pictureComplete(c,id);return}
        channels(c)
        val tap=PendingIntent.getActivity(c,id.hashCode(),Intent(c,MainActivity::class.java)
            .setData(android.net.Uri.parse("onklav-picture://pending/$id")).putExtra("open_pending_pictures",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching{manager(c).notify("picture:$id",0,NotificationCompat.Builder(c,"message-attention")
            .setSmallIcon(R.drawable.ref_nav_messages).setContentTitle("Picture message needs attention")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText((if(sender.isNotBlank())"$sender · "else "")+text))
            .setContentIntent(tap).setAutoCancel(false).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSilent(quiet(c)).build())}
    }
    fun pictureComplete(c:Context,id:String){manager(c).cancel("picture:$id",0)}

    fun attention(c: Context, title: String, text: String) {
        channels(c)
        runCatching {
            manager(c)
                .notify(
                    "messaging-attention",0,
                    NotificationCompat.Builder(c, "message-attention")
                        .setSmallIcon(R.drawable.ref_nav_messages)
                        .setContentTitle(title)
                        .setContentText(text.take(300))
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000)))
                        .setContentIntent(open(c, 0))
                        .setAutoCancel(true)
                        .setSilent(quiet(c))
                        .build(),
                )
        }
    }

    fun remind(c: Context, key: String, thread: Long, title: String, text: String, date: Long = 0) {
        channels(c)
        runCatching {
            manager(c)
                .notify(
                    "reminder:$key",0,
                    NotificationCompat.Builder(c, "reply-reminders")
                        .setSmallIcon(R.drawable.ref_setting_reminders)
                        .setContentTitle("Reply to $title")
                        .setContentText(text.take(300))
                        .setContentIntent(open(c, thread, key, date))
                        .setAutoCancel(true)
                        .setSilent(quiet(c))
                        .build(),
                )
        }
    }

    fun cancelReminder(c:Context,key:String){manager(c).cancel("reminder:$key",0);manager(c).cancel(key.hashCode())}

    fun cancel(c: Context, thread: Long) {
        manager(c).cancel("thread:$thread",0)
        manager(c).cancel(thread.hashCode())
    }
}

class MessageNotificationReceiver : BroadcastReceiver() {
    internal fun markRead(context:Context,thread:Long){
        fun allowed(){check(MessagingTransport.role(context)&&MessagingTransport.permission(context,android.Manifest.permission.READ_SMS)){"SMS access or the default texting role changed. Open OnKlav to mark this conversation read."}}
        require(thread>0);allowed()
        val values=android.content.ContentValues().apply{put("read",1);put("seen",1)}
        context.contentResolver.update(android.provider.Telephony.Sms.CONTENT_URI,values,"thread_id=?",arrayOf(thread.toString()))
        allowed()
        context.contentResolver.update(android.provider.Telephony.Mms.CONTENT_URI,values,"thread_id=?",arrayOf(thread.toString()))
        allowed()
        ChatStore(context).requestSync(JSONObject().put("action","thread").put("key",thread.toString()).put("unread",false).put("at",System.currentTimeMillis()))
    }
    internal fun finishRead(context:Context,thread:Long,replyQueued:Boolean){
        try{markRead(context,thread);MessageNotifications.cancel(context,thread)}catch(e:Exception){
            if(!replyQueued)throw e
            MessageNotifications.attention(context,"Reply Queued","Your reply is already queued. The conversation could not be marked read: ${e.message}")
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        val thread = intent.getLongExtra("thread", -1)
        if (thread <= 0 || intent.action !in setOf("mark_read","reply")) return
        val pending = goAsync()
        ChatStore.executor.execute {
            try {
                var replyQueued=false
                if (intent.action == "reply") {
                    val text =
                        RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence("reply_text")
                            ?.toString()
                    if (text.isNullOrBlank()) return@execute
                    MessagingTransport.sendSms(
                            context,
                            intent.getStringExtra("address") ?: error("Missing recipient"),
                            text,
                        )
                    replyQueued=true
                }
                finishRead(context,thread,replyQueued)
            } catch (e: Exception) {
                MessageNotifications.attention(
                    context,
                    if(intent.action=="reply")"Reply Needs Attention" else "Messaging Needs Attention",
                    e.message ?: "Open OnKlav",
                )
            } finally {
                pending.finish()
            }
        }
    }
}
