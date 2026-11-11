package app.onklav.home

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun MessengerSettings(section: String, close: () -> Unit) {
    val c = LocalContext.current
    val spec=LocalAppearanceSpec.current
    val pickerContext=remember(c,spec){appearanceDialogContext(c,spec)}
    val scope = rememberCoroutineScope()
    val store = remember { ChatStore(c) }
    var state by remember { mutableStateOf(JSONObject()) }
    val epoch by ChatStore.epoch.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    val prefs =
        remember(section) {
            c.getSharedPreferences(
                if (section == "Notifications") "notification_preferences" else "quiet_hours",
                0,
            )
        }
    var sound by rememberSaveable(section) { mutableStateOf(prefs.getBoolean("sound", true)) }
    var vibrate by rememberSaveable(section) { mutableStateOf(prefs.getBoolean("vibrate", true)) }
    var badges by rememberSaveable(section) { mutableStateOf(prefs.getBoolean("badges", true)) }
    var enabled by rememberSaveable(section) { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var start by rememberSaveable(section) { mutableIntStateOf(prefs.getInt("start", 1320)) }
    var end by rememberSaveable(section) { mutableIntStateOf(prefs.getInt("end", 480)) }
    var days by rememberSaveable(section) { mutableIntStateOf(prefs.getInt("days", 127)) }
    var reminderKey by remember { mutableStateOf<String?>(null) }
    var reschedule by remember { mutableStateOf(false) }
    var number by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable {mutableStateOf(false)}
    var repeatDays by rememberSaveable {mutableIntStateOf(days)}
    val dayNames=listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    LaunchedEffect(epoch) {
        try {
            state = store.snapshot()
        } catch (e: Exception) {
            error = e.message
        }
    }
    fun job(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    ReferenceDialog(
        section,
        close,
        covered=repeat,
        footer = {
            DialogAction(if(section in setOf("Notifications","Quiet Hours")) "Cancel" else "Close", onClick = close)
            if (section in setOf("Notifications", "Quiet Hours"))
                DialogAction("Save") {
                    try {
                        if(section=="Quiet Hours" && enabled) {
                            require(days!=0){"Choose at least one day."}
                            require(start!=end){"Choose different start and end times."}
                        }
                        val edit=prefs.edit()
                        if(section=="Notifications")edit.putBoolean("sound",sound).putBoolean("vibrate",vibrate).putBoolean("badges",badges)
                        else edit.putBoolean("enabled",enabled).putInt("start",start).putInt("end",end).putInt("days",days)
                        check(edit.commit()){ "Could not save. Please try again." };close()
                    }catch(e:Exception){error=e.message}
                }
        },
    ) {
        when (section) {
            "Notifications" -> {
                PreferenceToggle("Sound", sound) { sound = it }
                PreferenceToggle("Vibrate", vibrate) { vibrate = it }
                PreferenceToggle("App Icon Badges", badges) { badges = it }
                Text(
                    "Android channel settings and your launcher control notification sounds, vibration and badge display. OnKlav can silence alerts but cannot override a disabled channel.",
                    fontSize = 13.sp,
                )
                SettingsChoice("Android Notification Settings") {
                    runCatching {
                            c.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
                            )
                        }
                        .onFailure { error = it.message }
                }
            }
            "Quiet Hours" -> {
                Column(Modifier.padding(start=20.dp,end=20.dp,top=10.dp,bottom=20.dp)) {
                    PreferenceToggle("Enable Schedule", enabled) { enabled = it }
                    Text(if(!enabled)"Off" else if(MessageNotifications.quiet(c)&&start==prefs.getInt("start",1320)&&end==prefs.getInt("end",480)&&days==prefs.getInt("days",127))"Quiet Now" else "Schedule Ready",Modifier.padding(top=4.dp,bottom=16.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.primary)
                    fun time(minute:Int):String {
                        val cal=java.util.Calendar.getInstance().apply{set(java.util.Calendar.HOUR_OF_DAY,minute/60);set(java.util.Calendar.MINUTE,minute%60)}
                        return android.text.format.DateFormat.getTimeFormat(c).format(cal.time)
                    }
                    fun pick(title:String,minute:Int,change:(Int)->Unit) {
                        TimePickerDialog(pickerContext,{_,h,m->change(h*60+m)},minute/60,minute%60,android.text.format.DateFormat.is24HourFormat(c)).apply{setTitle(title);show()}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ChoiceButton("From\n"+time(start),Modifier.weight(1f),enabled=enabled,outlined=false,minimumHeight=80.dp){pick("Start Time",start){start=it}}
                        ChoiceButton("Until\n"+time(end),Modifier.weight(1f),enabled=enabled,outlined=false,minimumHeight=80.dp){pick("End Time",end){end=it}}
                    }
                    val repeats=if(days==127)"Every Day" else if(days==0)"Choose Days" else dayNames.filterIndexed{i,_->days and (1 shl i)!=0}.joinToString(" · "){it.take(3)}
                    ChoiceButton("Repeat · "+repeats,Modifier.padding(top=12.dp),enabled=enabled,outlined=false,minimumHeight=54.dp){repeatDays=days;repeat=true}
                    Text("Messages and reply reminders arrive silently. Walkie alerts are quiet too. Other apps use their own settings.",Modifier.padding(top=16.dp,bottom=8.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "Reminders" -> {
                val all = state.child("messages")
                val keys =
                    all.keysList()
                        .filter { all.child(it).optLong("remind_at") > 0 }
                        .sortedBy { all.child(it).optLong("remind_at") }
                if (keys.isEmpty())
                    Text(
                        "Long-press a message and choose Remind Me to Reply to schedule a reminder."
                    )
                keys.forEach { key ->
                    val f = all.child(key)
                    SettingsChoice(
                        f.optString("reminder_title"),
                        DateFormat.getDateTimeInstance().format(Date(f.optLong("remind_at"))) +
                            " · Tap to manage",
                    ) {
                        reminderKey = key
                        reschedule = false
                    }
                }
                Text("Android may delay reminders while the phone is idle.", fontSize = 13.sp)
            }
            "Blocked Senders" -> {
                OutlinedTextField(
                    number,
                    { number = it },
                    singleLine = true,
                    label = { Text("Phone Number") },
                )
                SettingsChoice("Block Number") {
                    job {
                        store.setBlocked(listOf(number), true)
                        number = ""
                    }
                }
                val blocked = state.optJSONArray("blocked")
                if (blocked == null || blocked.length() == 0) Text("No blocked senders.")
                else
                    repeat(blocked.length()) { i ->
                        val peer = blocked.getString(i)
                        SettingsChoice(peer, "Tap to unblock") {
                            job {
                                store.setBlocked(listOf(peer), false)
                            }
                        }
                    }
                Text(
                    "OnKlav moves blocked conversations to Trash and silences their alerts.",
                    fontSize = 13.sp,
                )
            }
        }
    }
    if(repeat)ReferenceDialog("Repeat on These Days",{repeat=false},footer={
        DialogAction("Cancel"){repeat=false}
        DialogAction("Done"){days=repeatDays;repeat=false}
    }){Column(Modifier.padding(horizontal=20.dp)){dayNames.forEachIndexed{i,name->PreferenceToggle(name,repeatDays and (1 shl i)!=0){repeatDays=repeatDays xor (1 shl i)}}}}
    ErrorDialog(error) { error = null }
    reminderKey?.let { key ->
        val flags = state.child("messages").child(key)
        fun update(due: Long) {
            job {
                ReplyReminders.validate(c, due)
                store.request(JSONObject().put("action", "message").put("key", key).put("remind_at", due))
                withContext(ChatStore.dispatcher) { ReplyReminders.schedule(c, key, due) }
                reminderKey = null
            }
        }
        if (reschedule) ReplyReminderChooser(flags.optLong("remind_at"), { reminderKey = null }, ::update)
        else ReferenceDialog(flags.optString("reminder_title", "Reminder"), { reminderKey = null },
            footer = { DialogAction("Cancel") { reminderKey = null } }) {
            SettingsChoice("Open Message") {
                c.startActivity(Intent(c, MainActivity::class.java)
                    .putExtra("open_thread", flags.optLong("thread"))
                    .putExtra("open_message_key", key)
                    .putExtra("open_message_date", flags.optLong("date"))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                reminderKey = null
                close()
            }
            SettingsChoice("Reschedule") { reschedule = true }
            SettingsChoice("Remove Reminder") { update(0) }
        }
    }

}
