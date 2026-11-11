package app.onklav.home

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.onklav.shared.OnKlavFileContracts
import app.onklav.shared.ClosePageButton
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

private fun JSONObject.socialRows(table: String): List<JSONObject> =
    optJSONObject(table)?.let { rows -> rows.keys().asSequence().map(rows::getJSONObject).toList() }.orEmpty()

@Composable
internal fun RealSocialScreen(exit: () -> Unit, openBrowser: ((String) -> Unit)?, requestLaunch:Long?=null) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val epoch by RealSocial.epoch.collectAsState()
    val profile = remember { c.getSharedPreferences("profile", 0) }
    val name = profile.getString("name", "").orEmpty().ifBlank { "My Page" }
    val photo = remember { runCatching { android.util.Base64.decode(profile.getString("photo",""),0) }.getOrNull() }
    var snapshot by remember { mutableStateOf(JSONObject()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRequest by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(requestLaunch) {
        if(requestLaunch!=null&&handledRequest!=requestLaunch){handledRequest=requestLaunch;page=null;tab=1}
    }
    var text by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var linkDialog by remember { mutableStateOf(false) }
    var invite by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<JSONObject?>(null) }
    var remove by remember { mutableStateOf<JSONObject?>(null) }
    var attachment by rememberSaveable { mutableStateOf("") }
    var mime by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<JSONObject?>(null) }
    var reply by remember { mutableStateOf<Pair<String,String>?>(null) }
    var reaction by remember { mutableStateOf<Pair<String,String>?>(null) }
    val friends = snapshot.socialRows("friends")
    val posts = snapshot.socialRows("posts").sortedByDescending { it.optLong("at") }
    val friend = page?.let { snapshot.optJSONObject("friends")?.optJSONObject(it) }
    fun back() { if (page != null) page = null else exit() }
    BackHandler { back() }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = ""
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Real Social could not complete this action." }
            finally { busy = false }
        }
    }
    LaunchedEffect(epoch) {
        try { snapshot = withContext(Dispatchers.IO) { RealSocial.snapshot(c) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Could not read Real Social." }
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { RealSocial.scheduleCleanup(c); TransportRecovery.schedule(c) } }
    val picker = rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            val prepared = withContext(Dispatchers.IO) {
                val budget = CarrierMedia.available(c,"Real Social",org.json.JSONArray()).coerceAtMost(260_000)
                val type = c.contentResolver.getType(uri).orEmpty()
                val bytes = c.contentResolver.openAssetFileDescriptor(uri,"r")?.use { it.length } ?: -1L
                if ((type.startsWith("audio/") || type.startsWith("video/")) && bytes !in 1..budget.toLong()) {
                    val info = CarrierMedia.inspect(c,uri)
                    CarrierMedia.prepare(c,CarrierMedia.Selection(uri,info,budget,"Social attachment"),0,info.duration) { _,_ -> }
                } else MessageMedia.import(c,uri,budget)
            }
            val next = prepared.getString("file")
            val nextMime = prepared.getString("mime")
            if (!RealSocial.validMime(nextMime)) {
                MessageTransferProvider.file(c,next).delete()
                error("Choose a supported image, video, or audio file.")
            }
            if (attachment.isNotEmpty()) MessageTransferProvider.file(c,attachment).delete()
            attachment = next
            mime = nextMime
        }
    }
    fun share() = work {
        withContext(Dispatchers.IO) {
            RealSocial.post(c,name,text.trim(),link,attachment.takeIf(String::isNotEmpty)?.let { MessageTransferProvider.file(c,it) },mime)
            if (attachment.isNotEmpty()) MessageTransferProvider.file(c,attachment).delete()
        }
        text = ""; link = ""; attachment = ""; mime = ""
    }
    fun save(post: JSONObject) = work {
        withContext(Dispatchers.IO) {
            val type = post.getString("mime")
            val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "bin"
            FileSelectionSession(c).use { files ->
                files.start()
                files.importSaved(listOf("downloads"),RealSocial.media(c,post.getString("id")),"Real Social.$ext",type)
            }
        }
        error = "Saved a copy in Files › Downloads."
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically) {
            if (page != null) IconButton(onClick = { page = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back to Friends") }
            else ClosePageButton(onClick = ::back)
            Text(friend?.optString("name")?.ifBlank { friend.optString("peer") } ?: "Real Social",
                Modifier.weight(1f),style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { work { withContext(Dispatchers.IO) { RealSocial.mute(c) } } }, enabled = !busy) {
                val muted = snapshot.optBoolean("muted")
                Icon(Icons.Outlined.Notifications, if (muted) "Unmute Real Social" else "Mute Real Social",
                    tint=if(muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            }
        }
        if (page == null) Box(Modifier.padding(horizontal = 8.dp)) {
            val tabs = listOf("My Page","Friends","Feed")
            ThemedButtonTabs(tabs,tabs[tab],"social-tab") { tab = tabs.indexOf(it) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error.ifBlank { snapshot.optString("error") }).takeIf(String::isNotBlank)?.let {
            Text(it,Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
        }
        val shown = when {
            page != null -> posts.filter { it.optString("peer") == page }
            tab == 0 -> posts.filter { it.optString("peer").isEmpty() }
            tab == 2 -> posts.filter { it.optString("peer").isNotEmpty() }
            else -> emptyList()
        }
        androidx.compose.runtime.key(tab,page) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f),contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page == null && tab == 0) item {
                    SocialIdentity(name,photo)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { linkDialog = true },enabled = !busy) { Icon(Icons.Outlined.Link,"Attach link") }
                        listOf(Triple("image",R.drawable.files_image,"Attach image"),
                            Triple("video",R.drawable.files_video,"Attach video"),
                            Triple("audio",R.drawable.files_audio,"Attach audio")).forEach { (kind, icon, label) ->
                            IconButton(onClick = { picker.launch(arrayOf("$kind/*")) },enabled = !busy) { Icon(painterResource(icon),label,Modifier.size(24.dp)) }
                        }
                    }
                    OutlinedTextField(text,{ text=it.take(650) },Modifier.fillMaxWidth(),
                        placeholder = { Text("What would you like to share?") },minLines = 2,maxLines = 6,
                        trailingIcon = {
                            IconButton(onClick = ::share,enabled = !busy && (text.isNotBlank() || link.isNotBlank() || attachment.isNotBlank())) {
                                Icon(Icons.AutoMirrored.Outlined.Send,"Share post")
                            }
                        })
                    if (link.isNotBlank()) AssistChip(onClick = { linkDialog=true },label = { Text(link,maxLines=1) })
                    if (attachment.isNotBlank()) AssistChip(onClick = {
                        MessageTransferProvider.file(c,attachment).delete();attachment="";mime=""
                    },label = { Text("Remove ${mime.substringBefore('/')} attachment") },enabled = !busy)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("My Posts",style = MaterialTheme.typography.titleMedium)
                }
                if (page == null && tab == 1) {
                    item {
                        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                            Text("Friends",Modifier.weight(1f),style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { invite = true },enabled = !busy) { Icon(Icons.Outlined.Add,"Invite a friend") }
                        }
                        if (friends.isEmpty()) Text("Invite a contact to share posts privately.",style = MaterialTheme.typography.bodyMedium)
                    }
                    items(friends,key = { phoneKey(it.getString("peer")) }) { f ->
                        val number = phoneKey(f.getString("peer"))
                        val status = f.optString("status")
                        Card {
                            Row(Modifier.fillMaxWidth().clickable(enabled = status=="friend") { page=number }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.optString("name").ifBlank { f.getString("peer") },style=MaterialTheme.typography.titleSmall)
                                    val label = when (status) {
                                        "friend" -> if (f.optBoolean("muted")) "Muted" else "Friend"
                                        "request" -> "Would like to connect with you on Real Social"
                                        "fast","checking" -> "Checking OnKlav"
                                        "verified_waiting" -> "Invitation queued"
                                        "unconfirmed" -> "No response · invitation timed out"
                                        else -> "Invitation sent"
                                    }
                                    Text(label,style=MaterialTheme.typography.labelMedium)
                                }
                                if (status == "request") {
                                    TextButton(onClick = { work { withContext(Dispatchers.IO) { RealSocial.remove(c,number,false) } } }, enabled = !busy) { Text("Decline") }
                                    TextButton(onClick = { work { withContext(Dispatchers.IO) { RealSocial.accept(c,number) } } }, enabled = !busy) { Text("Accept") }
                                } else IconButton(onClick = { chosen = f },enabled = !busy) { Icon(Icons.Outlined.MoreVert,"Friend options") }
                            }
                        }
                    }
                }
                if ((page != null || tab != 1) && shown.isEmpty()) item {
                    Text(if (tab == 0 && page == null) "Your posts will appear here." else "No recent posts yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(shown,key = { it.getString("id") }) { post ->
                    val id = post.getString("id")
                    val author = post.optString("name").ifBlank { post.optString("peer") }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SocialIdentity(author,photo.takeIf { post.optString("peer").isEmpty() },post.optLong("at"))
                            if (post.optString("text").isNotBlank()) Text(post.getString("text"),style=MaterialTheme.typography.bodyLarge)
                            post.optString("link").takeIf(RealSocial::safeLink)?.let { url ->
                                TextButton(onClick = { openBrowser?.invoke(url) },enabled = openBrowser!=null) { Text(url) }
                            }
                            if (post.optString("mime").startsWith("image/")) SocialPhoto(RealSocial.media(c,id))
                            if (post.optString("mime").isNotEmpty()) Row {
                                if (!post.optString("mime").startsWith("image/"))
                                    TextButton(onClick = { player=post }) { Text("Play ${post.optString("mime").substringBefore('/')}") }
                                TextButton(onClick = { save(post) },enabled = !busy) { Icon(Icons.Outlined.FileDownload,null);Text("Save copy") }
                            }
                            Row {
                                TextButton(onClick = { reaction=id to "" },enabled = !busy) { Text("React") }
                                TextButton(onClick = { reply=id to "" },enabled = !busy) { Text("Reply") }
                            }
                            val replies = post.socialRows("replies").sortedBy { it.optLong("at") }
                            val emojis = replies.filter { it.optString("kind")=="reaction" && it.optString("target").isEmpty() }
                            if (emojis.isNotEmpty()) Text(emojis.map { it.optString("text") }.distinct().joinToString(" "))
                            replies.filter { it.optString("kind")=="comment" }.forEach { comment ->
                                HorizontalDivider()
                                val peer = comment.optString("peer")
                                Text(if (peer.isEmpty()) name else friends.firstOrNull { phoneKey(it.optString("peer"))==peer }
                                    ?.optString("name")?.ifBlank { peer } ?: peer,style = MaterialTheme.typography.labelLarge)
                                Text(comment.optString("text"))
                                val target = comment.getString("id")
                                val marks = replies.filter { it.optString("kind")=="reaction" && it.optString("target")==target }
                                if (marks.isNotEmpty()) Text(marks.map { it.optString("text") }.distinct().joinToString(" "))
                                Row {
                                    TextButton(onClick = { reaction=id to target },enabled = !busy) { Text("React") }
                                    TextButton(onClick = { reply=id to target },enabled = !busy) { Text("Reply") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (linkDialog) SocialTextDialog("Attach link","HTTPS link",link,{linkDialog=false}) { value ->
        if (value.isEmpty() || RealSocial.safeLink(value)) { link=value;linkDialog=false }
        else error="Enter a complete HTTPS link."
    }
    if (invite) {
        var numberDialog by remember { mutableStateOf(false) }
        if (numberDialog) SocialTextDialog("Invite by number","Mobile number","",{numberDialog=false}) { number ->
            invite=false;work { withContext(Dispatchers.IO) { RealSocial.invite(c,number,number) } }
        } else androidx.compose.ui.window.Dialog(onDismissRequest = { invite=false }, properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                Column {
                    TextButton(onClick={numberDialog=true}) { Text("Invite by number") }
                    ContactsScreen(exit = { invite=false }, selectionLabel = "Invite") { number ->
                        invite=false;work {
                            withContext(Dispatchers.IO) {
                                val label=loadPhoneContacts(c).firstOrNull { row -> row.numbers.any { phoneKey(it)==phoneKey(number) } }?.name ?: number
                                RealSocial.invite(c,number,label)
                            }
                        }
                    }
                }
            }
        }
    }
    chosen?.let { f ->
        val number = phoneKey(f.getString("peer"))
        AlertDialog(onDismissRequest={chosen=null},title={Text(f.optString("name").ifBlank{f.getString("peer")})},text={
            Column {
                TextButton(onClick={chosen=null;work { withContext(Dispatchers.IO){ RealSocial.mute(c,number) } }}) {
                    Text(if(f.optBoolean("muted"))"Unmute friend" else "Mute friend")
                }
                TextButton(onClick={chosen=null;remove=f}) { Text("Remove or block") }
            }
        },confirmButton={TextButton(onClick={chosen=null}){Text("Done")}})
    }
    remove?.let { f ->
        val number=phoneKey(f.getString("peer"))
        AlertDialog(onDismissRequest={remove=null},title={Text("Remove from Real Social?")},
            text={Text("This removes their posts and quietly updates their friend list. Block requests to prevent future invitations.")},
            confirmButton={TextButton(onClick={remove=null;work { withContext(Dispatchers.IO){ RealSocial.remove(c,number,true) } }}) {Text("Remove & block")}},
            dismissButton={TextButton(onClick={remove=null;work { withContext(Dispatchers.IO){ RealSocial.remove(c,number,false) } }}) {Text("Remove only")}})
    }
    reply?.let { (id,target) ->
        SocialTextDialog("Reply","Write a reply","",{reply=null}) { value ->
            if(value.isNotBlank()) {reply=null;work { withContext(Dispatchers.IO){ RealSocial.respond(c,id,target,value,false) } }}
        }
    }
    reaction?.let { (id,target) ->
        AlertDialog(onDismissRequest={reaction=null},title={Text("Choose a reaction")},text={
            Column { RealSocial.reactions.chunked(3).forEach { row -> Row {
                row.forEach { emoji -> TextButton(onClick={reaction=null;work {
                    withContext(Dispatchers.IO){ RealSocial.respond(c,id,target,emoji,true) }
                }}) { Text(emoji) } }
            } } }
        },confirmButton={TextButton(onClick={reaction=null}){Text("Cancel")}})
    }
    player?.let { post ->
        val file=RealSocial.media(c,post.getString("id"))
        AlertDialog(onDismissRequest={player=null},title={Text("Attachment")},text={
            AndroidView(factory={ context -> android.widget.VideoView(context).apply {
                val controls=android.widget.MediaController(context);controls.setAnchorView(this)
                setMediaController(controls);setVideoPath(file.absolutePath)
                setOnPreparedListener { start();controls.show(0) }
            } },modifier=Modifier.fillMaxWidth().height(200.dp),onRelease={it.stopPlayback()})
        },confirmButton={TextButton(onClick={player=null}){Text("Close")}})
    }
}

@Composable private fun SocialTextDialog(title:String,label:String,initial:String,close:()->Unit,done:(String)->Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=close,title={Text(title)},text={
        OutlinedTextField(value,{value=it.take(650)},label={Text(label)})
    },confirmButton={TextButton(onClick={done(value.trim())}) { Text("Done") }},
        dismissButton={TextButton(onClick=close){Text("Cancel")}})
}

@Composable private fun SocialIdentity(name: String, photo: ByteArray?, at: Long = 0) {
    val image=remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap() } }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment=Alignment.Center) {
            if(image!=null) Image(image,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            else Icon(Icons.Outlined.Person,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(name,style=MaterialTheme.typography.titleMedium)
            if(at>0) Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT)
                .format(java.util.Date(at)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SocialPhoto(file: File) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,file.path) {
        value=withContext(Dispatchers.IO) {
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0)return@withContext null
            val options=BitmapFactory.Options()
            while(maxOf(bounds.outWidth,bounds.outHeight)/options.inSampleSize.coerceAtLeast(1)>1200)
                options.inSampleSize=options.inSampleSize.coerceAtLeast(1)*2
            BitmapFactory.decodeFile(file.path,options)?.asImageBitmap()
        }
    }
    bitmap?.let { Image(it,"Shared image",Modifier.fillMaxWidth().heightIn(max=320.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Fit) }
}
