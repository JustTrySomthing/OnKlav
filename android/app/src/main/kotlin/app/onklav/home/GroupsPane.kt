package app.onklav.home

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject

/** A saved recipient set, not a second APK or a locally simulated chat service. */
data class SavedGroup(val id: String, val name: String, val members: List<String>, val thread: Long) {
    val key get() = if (thread > 0) thread.toString() else "group:$id"
    val address get() = members.joinToString(";")
}
fun JSONObject.savedGroups(): List<SavedGroup> = child("groups").let { groups ->
    groups.keysList().map { id ->
        val g = groups.getJSONObject(id)
        val people = g.getJSONArray("members")
        SavedGroup(id, g.getString("name"), List(people.length()) { people.getString(it) }, g.getLong("thread"))
    }
}
fun groupInitials(name: String): String = name.trim().split(Regex("\\s+")).mapNotNull { word ->
    word.codePoints().toArray().firstOrNull { Character.isLetterOrDigit(it) }?.let { String(Character.toChars(it)) }
}.take(2).joinToString("").uppercase(java.util.Locale.ROOT).let {
    if (it.isEmpty()) "•" else String(it.codePoints().limit(2).toArray(), 0, it.codePointCount(0, it.length).coerceAtMost(2))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupsPane(setup: () -> Unit, incomingThread: Long = 0, intentEpoch: Int = 0, incomingMessageKey: String? = null, incomingMessageDate: Long = 0, notificationGroup: SavedGroup? = null, playVideo: ((String)->Unit)? = null, openWalkie:((String)->Unit)?=null, openGames:((String,String)->Unit)?=null, openWeb:((String)->Unit)?=null, conversationVisible: Boolean = true) {
    val context = LocalContext.current
    val store = remember { ChatStore(context) }
    val repo = remember { MessageRepository(context) }
    val scope = rememberCoroutineScope()
    val epoch by ChatStore.epoch.collectAsState()
    val groupMemory:MessengerMemory=androidx.lifecycle.viewmodel.compose.viewModel(key="groups-list")
    val revision by groupMemory.providerEpoch
    var state by groupMemory.snapshot
    var conversations by groupMemory.rows
    var folder by rememberSaveable { mutableStateOf("Inbox") }
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<SavedGroup?>(null) }
    var membersSheet by remember { mutableStateOf<SavedGroup?>(null) }
    var deleting by remember { mutableStateOf<List<SavedGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    fun job(block: suspend () -> Unit) { scope.launch { try { block() } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Could not update group" } } }
    LaunchedEffect(intentEpoch, notificationGroup?.id) {
        if (notificationGroup != null && groupMemory.handledLaunch != intentEpoch) {
            groupMemory.handledLaunch = intentEpoch
            editor = null
            menu = null
            membersSheet = null
            deleting = null
            opened = notificationGroup.id
        }
    }
    LaunchedEffect(Unit) { groupMemory.watch(context) }
    LaunchedEffect(epoch, revision) {
        try { state = repo.reconcileGroups(); conversations = state.savedGroups().mapNotNull{g->if(g.thread>0)repo.conversation(g.thread)else null}; loaded = true }
        catch(e: Exception) { if (e is CancellationException) throw e; error = e.message; loaded = true }
    }
    val groups = state.savedGroups()
    var historical by remember{mutableStateOf(MessageRepository.HistorySearch(emptySet(),false))}
    var searching by remember{mutableStateOf(false)}
    LaunchedEffect(query,epoch,revision){
        historical=MessageRepository.HistorySearch(emptySet(),false)
        if(query.isNotBlank()){
            searching=true
            try{delay(250);historical=repo.searchThreads(query,groups.map{it.thread}.filter{it>0}.toSet())}
            catch(e:CancellationException){throw e}catch(e:Exception){ if (e is CancellationException) throw e;error="History search could not finish: ${e.message}"}finally{searching=false}
        }else searching=false
    }
    fun flags(g: SavedGroup) = state.child("threads").child(g.key)
    fun change(g: SavedGroup, key: String, value: Any) = job {
        if(key=="unread"&&g.thread>0)repo.markRead(g.thread,value as Boolean)
        else store.request(JSONObject().put("action", "thread").put("key", g.key).put(key, value).put("at", System.currentTimeMillis()))
    }
    var revealedGroup by remember { mutableStateOf<String?>(null) }
    val active = groups.firstOrNull { it.id == opened } ?: notificationGroup?.takeIf { it.id == opened }
    Box(Modifier.fillMaxSize()) {
    BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    Column(Modifier.fillMaxSize().testTag("groups-screen")) {
        HomeHeader("Groups") { OriginalAction(R.drawable.ref_action_add, "Create Group") { editor = "new" } }
        MailboxControls(folder, { folder = it; query = ""; selecting = false; selected = emptySet() }, query,
            { query = it }, selecting, { selecting = it; selected = emptySet() })
        if(searching)Text("Searching message history…",Modifier.padding(horizontal=12.dp),fontSize=13.sp)
        if(historical.truncated)Text("Search reached its limit. Refine the search to find older matches.",Modifier.padding(12.dp),fontSize=13.sp)
        val shown = groups.filter { g ->
            flags(g).optString("folder", "Inbox").ifEmpty { "Inbox" } == folder &&
                (g.name.contains(query, true) || g.address.contains(query, true) || g.thread in historical.threads ||
                    conversations.any { it.id == g.thread && it.snippet.contains(query, true) })
        }.sortedWith(compareByDescending<SavedGroup> { folder == "Inbox" && flags(it).optBoolean("pinned") }
            .thenByDescending{g->maxOf(conversations.firstOrNull{it.id==g.thread}?.date?:0L,flags(g).optLong("last_incoming_at"))}.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
        if (selecting) {
            val targets=groups.filter{it.id in selected}
            val peers=targets.flatMap{activityRecipients(it.members,context.getSharedPreferences("profile",0).getString("phone","").orEmpty())}.distinct()
            BulkConversationActions(selected.size,folder,peers.isNotEmpty()&&peers.all{conversationBlocked(state,it)},
                move={target->targets.forEach{change(it,"folder",target)};selected=emptySet();selecting=false},
                block={value->job{state=store.setBlocked(peers,value,targets.map{it.key});selected=emptySet();selecting=false}},
                done={selected=emptySet();selecting=false},
                unread={targets.forEach{change(it,"unread",true)}},
                delete=if(folder=="Trash")({deleting=targets})else null)
        }
        if(folder=="Trash") {
            val allTrash=groups.filter{flags(it).optString("folder")=="Trash"}
            TrashControls(allTrash.isNotEmpty(),restore={allTrash.forEach{change(it,"folder","Inbox")}},empty={deleting=allTrash})
        }
        if (shown.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(if (!loaded) "Loading groups…" else if (query.isBlank()) "No groups here. Use + to create a group." else "No matching groups",
                fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start=8.dp,end=8.dp,top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { g ->
                val f = flags(g)
                val unread = conversations.firstOrNull { it.id == g.thread }?.unread ?: f.optBoolean("unread")
                val peers=activityRecipients(g.members,context.getSharedPreferences("profile",0).getString("phone","").orEmpty())
                val blocked=peers.isNotEmpty() && peers.all{conversationBlocked(state,it)}
                SwipeConversation("group-${g.id}",!selecting,revealedGroup,{revealedGroup=it},folder=="Archive",folder=="Trash",blocked,
                    "${if(blocked)"Unblock"else"Block"} this group's other members in OnKlav? This also affects their direct messages.",
                    onArchive={change(g,"folder",if(folder=="Inbox")"Archive"else"Inbox")},
                    onDelete={if(folder=="Trash")deleting=listOf(g)else change(g,"folder","Trash")},
                    onBlock={job{state=store.setBlocked(peers,!blocked,listOf(g.key))}}) {
                Row(Modifier.fillMaxWidth().background(
                    groupSurface(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, unread, g.id in selected),
                    RoundedCornerShape(12.dp)).combinedClickable(onClick = {
                    if (selecting) selected = if (g.id in selected) selected - g.id else selected + g.id
                    else opened = g.id
                }, onLongClick = { menu = g }).padding(12.dp).testTag("group-${g.id}"), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) Checkbox(g.id in selected, { selected = if (it) selected + g.id else selected - g.id })
                    GroupAvatar(g.name, 48)
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                        Text(g.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
                        Text((if (unread) "●  " else "") + "${g.members.size} recipients", Modifier.padding(top = 5.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OriginalAction(R.drawable.ref_action_edit, "Edit ${g.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) { editor = g.id }
                    if (folder == "Inbox" && !selecting) OriginalAction(if (f.optBoolean("pinned")) R.drawable.ref_action_pin_filled else R.drawable.ref_action_pin, (if (f.optBoolean("pinned")) "Unpin " else "Pin ") + g.name, tint = if (f.optBoolean("pinned")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) { change(g, "pinned", !f.optBoolean("pinned")) }
                }
                }
            }
        }
    }
    RightSlideRoute(active) { group ->
        key(group.id) {
            val targeted = incomingThread > 0 && incomingThread == group.thread
            MessengerPane(setup,incomingAddress=group.address,incomingThread=group.thread,
                intentEpoch=if (targeted) intentEpoch else 0,
                incomingMessageKey=incomingMessageKey.takeIf { targeted },
                incomingMessageDate=if (targeted) incomingMessageDate else 0L,
                group=group,exitGroup={opened=null},playVideo=playVideo,openWeb=openWeb,openWalkie=openWalkie,openGames=openGames,
                conversationVisible=conversationVisible && opened == group.id)
        }
    }
    }
    editor?.let { id -> GroupEditor(groups.firstOrNull { it.id == id }, { editor = null }, { _ -> if(id=="new")folder="Inbox";editor = null; opened=null }) }
    menu?.let { g -> ReferenceDialog(g.name, { menu = null }, footer = { DialogAction("Close") { menu = null } }) {
        Column(Modifier.padding(16.dp)) {
            SettingsChoice("Group Settings") { editor = g.id; menu = null }
            SettingsChoice("Members") { membersSheet = g; menu = null }
            val isUnread=conversations.firstOrNull{it.id==g.thread}?.unread?:flags(g).optBoolean("unread")
            SettingsChoice(if (isUnread) "Mark read" else "Mark unread") { change(g, "unread", !isUnread); menu = null }
            listOf("Inbox", "Archive", "Trash").filter { it != folder }.forEach { target -> SettingsChoice(if (target == "Inbox") "Restore" else "Move to $target") { change(g, "folder", target); menu = null } }
            if (folder == "Trash") SettingsChoice("Delete Group") { deleting = listOf(g); menu = null }
        }
    } }
    deleting?.let { targets -> ReferenceDialog("Delete Group?", { deleting = null }, centerTitle = true, footer = {
        DialogAction("Cancel") { deleting = null }
        DialogAction("Delete Group", destructive = true) { job { targets.forEach { store.request(JSONObject().put("action", "group_remove").put("id", it.id)) }; deleting = null; selected = emptySet() } }
    }) { Text("Delete this group from this phone? Messages and other members are unchanged.", Modifier.padding(20.dp)) } }
    membersSheet?.let { g -> GroupMembers(g) { membersSheet = null } }
    ErrorDialog(error) { error = null }
}

@Composable
fun GroupAvatar(name: String, size: Int = 40) {
    Box(Modifier.size(size.dp).background(sentBrush(LocalAppearanceSpec.current), CircleShape), contentAlignment = Alignment.Center) {
        Text(groupInitials(name), fontSize = if(size == 48) 20.sp else 16.sp, color = sentInk(LocalAppearanceSpec.current), maxLines = 1)
    }
}

@Composable
fun GroupEditor(existing: SavedGroup?, close: () -> Unit, saved: (SavedGroup) -> Unit) {
    val context = LocalContext.current
    val store = remember { ChatStore(context) }
    val repo = remember { MessageRepository(context) }
    val scope = rememberCoroutineScope()
    var id by rememberSaveable { mutableStateOf(existing?.id ?: java.util.UUID.randomUUID().toString().replace("-", "")) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var memberJson by rememberSaveable { mutableStateOf(JSONArray(existing?.members ?: emptyList<String>()).toString()) }
    val members = JSONArray(memberJson).let { a -> List(a.length()) { a.getString(it) } }
    var choose by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var unread by rememberSaveable { mutableStateOf(false) }
    var unreadTouched by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existing?.id) { existing?.let { g ->
        try {
            val f = store.snapshot().child("threads").child(g.key)
            val conversation = repo.conversation(g.thread)
            notifications = !f.optBoolean("muted")
            unread = conversation?.unread ?: f.optBoolean("unread")
        } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message }
    } }
    ReferenceDialog(if(existing == null) "Create Group" else "Group Settings", { if (!busy) close() }, covered = choose || deleting, centerTitle = true, footer = {
        DialogAction("Cancel", enabled = !busy, onClick = close)
        DialogAction("Save", enabled = !busy) { scope.launch {
            busy = true
            try {
                require(name.trim().isNotEmpty()) { "Enter a group name" }
                val own = context.getSharedPreferences("profile", 0).getString("phone", "").orEmpty()
                require(own.isBlank() || members.none { phoneKey(it) == phoneKey(own) }) { "Choose other people. Your own number is included automatically." }
                require((members.size in 2..10||existing!=null&&sameRecipients(existing.members,members)&&members.size<=256) && members.map(::phoneKey).toSet().size == members.size) { "Choose 2–10 different people" }
                val thread = if (existing != null && sameRecipients(existing.members, members)) existing.thread else repo.threadFor(members.joinToString(";"))
                val result = store.request(JSONObject().put("action", "group_save").put("id", id).put("name", name.trim()).put("members", JSONArray(members)).put("thread", thread).put("discovered",existing!=null&&members.size>10))
                val g = result.savedGroups().first { it.id == id }
                if (existing != null) store.request(JSONObject().put("action", "thread").put("key", g.key).put("muted", !notifications))
                if(unreadTouched){if(g.thread>0)repo.markRead(g.thread,unread)else store.request(JSONObject().put("action","thread").put("key",g.key).put("unread",unread).put("at",System.currentTimeMillis()))}
                saved(g)
            } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message } finally { busy = false }
        } }
    }) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Group Name", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GroupField(name, { name = it.take(60).let { text -> if (text.lastOrNull()?.isHighSurrogate() == true) text.dropLast(1) else text }; error = null }, "Family, Friends, Book Club…", "group-name", centered = true)
            GroupDivider()
            Text("${members.size} People · Choose 2–10", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceButton("Choose Members", outlined = false, fontSize = 14.sp) { choose = true }
            Text("Adding or removing members starts a new conversation.", fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (existing != null) {
                GroupDivider()
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Notifications", Modifier.clickable { notifications = !notifications }.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), fontSize = 16.sp); Switch(notifications, { notifications = it }, Modifier.semantics { contentDescription = "Group Notifications" }) }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Read", Modifier.weight(1f).clickable { unread = false;unreadTouched=true }.padding(8.dp), textAlign = TextAlign.End, fontSize = 16.sp, fontWeight = if (!unread) FontWeight.Bold else FontWeight.Normal, color = if (!unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(unread, { unread = it;unreadTouched=true }, Modifier.semantics { contentDescription = "Unread conversation" })
                    Text("Unread", Modifier.weight(1f).clickable { unread = true;unreadTouched=true }.padding(8.dp), fontSize = 16.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GroupDivider()
                ChoiceButton("Delete Group", outlined = false) { deleting = true }
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        }
    }
    if (choose) GroupPeopleChooser(members, { choose = false }) { memberJson = JSONArray(it).toString(); error = null; choose = false }
    if (deleting) ReferenceDialog("Delete Group?", { deleting = false }, centerTitle = true, footer = {
        DialogAction("Cancel") { deleting = false }
        DialogAction("Delete Group", destructive = true) { scope.launch { try { store.request(JSONObject().put("action", "group_remove").put("id", id)); close() } catch(e: Exception) { if (e is CancellationException) throw e; error = e.message; deleting = false } } }
    }) { Text("Delete this group from this phone? Messages and other members are unchanged.", Modifier.padding(20.dp)) }
}
fun sameRecipients(a: List<String>, b: List<String>) = sameRecipients(a.joinToString(";"),b.joinToString(";"))
@Composable private fun GroupDivider() = HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .18f))

@Composable
fun GroupPeopleChooser(initial: List<String>, close: () -> Unit, done: (List<String>) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var chosenJson by rememberSaveable { mutableStateOf(JSONArray(initial).toString()) }
    val chosen = JSONArray(chosenJson).let { a -> List(a.length()) { a.getString(it) } }
    var contacts by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var epoch by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { epoch++ }
    val allowed = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { epoch++ }
    LaunchedEffect(epoch,allowed) {
        loading=allowed;error=null
        if (!allowed) { contacts=emptyList();return@LaunchedEffect }
        try { contacts = withContext(Dispatchers.IO) {
        val rows = mutableListOf<Pair<String, String>>()
        val cursor=context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf("display_name", "data1"), null, null, "display_name ASC") ?: throw IllegalStateException("Contacts are unavailable")
        cursor.use { c ->
            while(c.moveToNext() && rows.size < 5000) {
                val number = android.telephony.PhoneNumberUtils.normalizeNumber(c.getString(1).orEmpty())
                if (number.isNotBlank()) rows += (c.getString(0) ?: "Unnamed contact") to number
            }
        }
        rows.distinctBy { phoneKey(it.second) }
    } } catch(e: Exception) { if (e is CancellationException) throw e; error = "Could not load contacts. Check Contacts permission and try again." }
        finally { loading=false }
    }
    ReferenceDialog("Choose people", close, footer = { DialogAction("Cancel", onClick = close); DialogAction("Done") { done(chosen) } }) {
        Column(Modifier.padding(20.dp)) {
            GroupField(query, { query = it }, "Search people", "group-contact-search")
            Text("${chosen.size} selected", fontSize = 14.sp)
            if (!allowed) ChoiceButton("Allow Contacts", outlined = false) { permission.launch(Manifest.permission.READ_CONTACTS) }
            // Retain selected recipients even when a contact was removed or permission changes.
            val rows = (contacts + chosen.filter { n -> contacts.none { phoneKey(it.second) == phoneKey(n) } }.map { "Phone number" to it })
                .filter { (it.first + " " + it.second).contains(query, true) }
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                rows.forEach { (name, number) ->
                    val checked = chosen.any { phoneKey(it) == phoneKey(number) }
                    Row(Modifier.fillMaxWidth().clickable {
                        if (checked) chosenJson = JSONArray(chosen.filterNot { phoneKey(it) == phoneKey(number) }).toString()
                        else if (chosen.size < 10) chosenJson = JSONArray(chosen + number).toString()
                        else error = "Choose up to 10 people"
                    }.padding(vertical = 10.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GroupAvatar(name)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(name, fontSize = 16.sp); Text(number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Checkbox(checked, null)
                    }
                }
                if (loading) Text("Loading contacts…", Modifier.padding(16.dp), fontSize = 16.sp)
                else if (rows.isEmpty() && error==null && allowed) Text("No matching contacts", Modifier.padding(16.dp), fontSize = 16.sp)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (error!=null) TextButton(onClick={epoch++}) { Text("Try Again") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembers(group: SavedGroup, choose:((String,String)->Unit)?=null, close: () -> Unit) {
    val context=LocalContext.current
    var people by remember(group.address){mutableStateOf<List<ContactRow>?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    var refresh by remember{mutableIntStateOf(0)}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { refresh++ }
    val own=context.getSharedPreferences("profile",0).getString("phone","").orEmpty()
    LaunchedEffect(group.address,own,refresh){
        error=null
        try{people=withContext(Dispatchers.IO){
            val found=mutableMapOf<String,ContactRow>()
            if(context.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
                val cursor=context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf("display_name","data1","photo_thumb_uri"),null,null,null) ?: throw IllegalStateException("Contacts are unavailable")
                cursor.use{c->
                    var count=0;val photo=c.getColumnIndex("photo_thumb_uri")
                    while(c.moveToNext()&&count++<20000){val phone=c.getString(1).orEmpty();found[phoneKey(phone)]=ContactRow(0,c.getString(0).orEmpty().ifBlank{"Phone number"},if(photo>=0)c.getString(photo)else null,listOf(phone))}
                }
            }
            activityRecipients(group.members,own).map{number->found[phoneKey(number)]?.copy(numbers=listOf(number))?:ContactRow(0,"Phone number",null,listOf(number))}.sortedBy{it.name.lowercase(java.util.Locale.ROOT)}
        }}catch(e:Exception){ if (e is CancellationException) throw e;error="Contact names could not be loaded";people=activityRecipients(group.members,own).map{ContactRow(0,"Phone number",null,listOf(it))}}
    }
    val windowHeight=with(androidx.compose.ui.platform.LocalDensity.current){androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()}
    ModalBottomSheet(onDismissRequest=close,containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).heightIn(max=windowHeight*.65f).verticalScroll(rememberScrollState())){
            Row(verticalAlignment=Alignment.CenterVertically){Text(if(choose==null)"Members"else"Choose Who to Play or Talk With",Modifier.weight(1f),fontSize=if(choose==null)22.sp else 18.sp);OriginalAction(R.drawable.ref_action_close,if(choose==null)"Close members"else"Close member selection",action=close)}
            Text(group.name,fontSize=16.sp)
            val rows=people
            Text(if(rows==null)"Loading members…"else if(choose==null)"${rows.size+1} members · including you"else if(rows.isEmpty())"No Other Members Available"else if(rows.size==1)"1 Person"else"${rows.size} People",Modifier.padding(bottom=12.dp),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val shown=if(choose==null)listOf(ContactRow(-1,"You",null,listOf(own)))+rows.orEmpty()else rows.orEmpty()
            shown.forEach{person->val number=person.numbers.firstOrNull().orEmpty()
                Row(Modifier.fillMaxWidth().padding(top=6.dp).background(MaterialTheme.colorScheme.background,RoundedCornerShape(12.dp))
                    .then(if(choose!=null)Modifier.clickable{choose(number,person.name)}.testTag("activity-member-${phoneKey(number)}")else Modifier)
                    .padding(8.dp,10.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(40.dp)){ContactAvatar(person,40.dp)}
                    Column(Modifier.weight(1f).padding(start=12.dp)){Text(person.name,fontSize=16.sp);androidx.compose.foundation.text.selection.SelectionContainer{Text(if(number.isBlank())"This phone"else app.onklav.shared.displayPhone(number),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                }
            }
            error?.let{Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++}){Text("Try Again")}}
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One individual peer is selected; the host never broadcasts a game/call to the group. */
internal fun activityRecipients(members:List<String>,own:String):List<String> = members.map{app.onklav.shared.phoneInput(it)}.filter{it.isNotBlank()&&phoneKey(it).isNotBlank()&&(own.isBlank()||phoneKey(it)!=phoneKey(own))}.distinctBy(::phoneKey)

@Composable private fun GroupField(value: String, change: (String) -> Unit, hint: String, tag: String, centered: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, change, Modifier.fillMaxWidth().heightIn(min = 56.dp).onFocusChanged { focused = it.isFocused }
        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
        .border(if (focused) 2.dp else 1.dp, if(focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(10.dp)).padding(14.dp).testTag(tag),
        singleLine = true, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = if(centered) TextAlign.Center else TextAlign.Start),
        decorationBox = { field -> Box(contentAlignment = if(centered) Alignment.Center else Alignment.CenterStart) {
            if (value.isBlank()) Text(hint, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if(centered) TextAlign.Center else TextAlign.Start)
            field()
        } })
}

/** Same sRGB channel mix used by the reference's unread/selected list surfaces. */
private fun groupSurface(surface: Color, accent: Color, unread: Boolean, selected: Boolean): Color {
    val amount = if (selected) .22f else if (unread) .12f else 0f
    return Color(surface.red * (1-amount) + accent.red * amount,
        surface.green * (1-amount) + accent.green * amount,
        surface.blue * (1-amount) + accent.blue * amount)
}
