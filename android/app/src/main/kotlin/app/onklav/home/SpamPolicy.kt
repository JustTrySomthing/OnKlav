package app.onklav.home

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.onklav.shared.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Warning only; contact lookup stays off the UI thread and fails closed. */
internal object SpamPolicy {
    fun suspicious(text:String):Boolean {
        val lower=text.lowercase(java.util.Locale.ROOT)
        val link=Regex("(?i)(https?://|www\\.)\\S+").containsMatchIn(text)
        return link && listOf("verify your","verify account","account suspended","urgent","claim your","gift card","unpaid toll","delivery failed","confirm payment","password","bank details").any {it in lower}
    }
    fun assess(c:Context,number:String,text:String):Boolean {
        val p=c.getSharedPreferences("spam_screen",0)
        val key=phoneKey(number)
        if(!p.getBoolean("enabled",true)||key.isEmpty()||!suspicious(text)||key in p.getStringSet("trusted",emptySet()).orEmpty())return false
        if(!MessagingTransport.permission(c,Manifest.permission.READ_CONTACTS))return false
        return runCatching {
            c.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),arrayOf(ContactsContract.PhoneLookup._ID),null,null,null)
                ?.use { !it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
    fun trust(c:Context,number:String) {
        val p=c.getSharedPreferences("spam_screen",0)
        val all=p.getStringSet("trusted",emptySet()).orEmpty().toMutableSet()
        require(all.size<5000){"Trusted sender limit reached. Reset trusted senders in Settings."}
        all.add(phoneKey(number));check(p.edit().putStringSet("trusted",all).commit())
        ChatStore.epoch.value++
    }
}

@Composable
internal fun SpamWarning(peer: String, thread: Long) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var open by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    TextButton(onClick={open=true}){Text("Possible Spam · Review")}
    if(open) ReferenceDialog("Possible Spam",{open=false},footer={DialogAction("Close"){open=false}}){
        SettingsChoice("Trust Sender","Stop these warnings for this number") {runCatching{SpamPolicy.trust(c,peer);open=false}.onFailure{error=it.message}}
        SettingsChoice("Block Sender", "Silence this sender and move their direct conversation to Trash") {
            scope.launch {
                try {
                    ChatStore(c).setBlocked(listOf(peer), true, listOfNotNull(thread.takeIf { it > 0 }?.toString()))
                    open = false
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Could not block this sender." }
            }
        }
        Text("Verify the sender before opening links or sharing information.")
    }
    ErrorDialog(error){error=null}
}
