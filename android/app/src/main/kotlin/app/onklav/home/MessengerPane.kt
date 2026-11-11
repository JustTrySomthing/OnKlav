@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.onklav.home

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import org.json.JSONObject

/** New host UI. Provider messages stay in Android; private state is owned by Rust. */
@Composable
fun MessengerPane(
    setup: () -> Unit,
    incomingAddress: String? = null,
    incomingThread: Long = 0,
    intentEpoch: Int = 0,
    incomingMessageKey: String? = null,
    incomingMessageDate: Long = 0,
    group: SavedGroup? = null,
    exitGroup: (() -> Unit)? = null,
    playVideo: ((String)->Unit)? = null,
    openWalkie: ((String)->Unit)? = null,
    openGames: ((String,String)->Unit)? = null,
    openWeb: ((String)->Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ChatStore(context) }
    val repo = remember { MessageRepository(context) }
    val groupProtocol = remember { GroupProtocol(context) }
    var groupFeed by remember { mutableStateOf(JSONObject()) }
    val epoch by ChatStore.epoch.collectAsState()
    val failure by ChatStore.lastError.collectAsState()
    val memory:MessengerMemory=androidx.lifecycle.viewmodel.compose.viewModel(key="messenger-${group?.id?:"inbox"}")
    var state by memory.snapshot
    var rows by memory.rows
    var messages by memory.messages
    var loadingMessages by remember { mutableStateOf(false) }
    var positionedThread by remember { mutableLongStateOf(-1L) }
    var address by rememberSaveable { mutableStateOf(group?.address.orEmpty()) }
    var thread by rememberSaveable { mutableLongStateOf(group?.thread ?: 0L) }
    var title by rememberSaveable { mutableStateOf(group?.name.orEmpty()) }
    var givenName by remember(address) { mutableStateOf<String?>(null) }
    LaunchedEffect(address) {
        if (group == null && address.isNotBlank()) givenName = repo.contactGivenName(address)
    }
    var folder by rememberSaveable { mutableStateOf("Inbox") }
    var search by rememberSaveable { mutableStateOf("") }
    var chatSearch by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("Feed") }
    var limit by rememberSaveable { mutableIntStateOf(20) }
    var revision by memory.providerEpoch
    var error by remember { mutableStateOf<String?>(null) }
    var numberDialog by rememberSaveable { mutableStateOf(false) }
    var number by rememberSaveable { mutableStateOf("") }
    var contacts by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Conversation?>(null) }
    var permanentDelete by remember { mutableStateOf<Conversation?>(null) }
    var revealedConversation by remember { mutableStateOf<String?>(null) }
    var messageMenu by remember { mutableStateOf<Message?>(null) }
    var reactionMessage by remember { mutableStateOf<Message?>(null) }
    var historyBefore by rememberSaveable { mutableLongStateOf(Long.MAX_VALUE) }
    var mediaUri by rememberSaveable { mutableStateOf<String?>(null) }
    var galleryMessages by remember { mutableStateOf(emptyList<Message>()) }
    var galleryLoading by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    var galleryRetry by remember { mutableIntStateOf(0) }
    var galleryCursor by remember {mutableStateOf<MediaCursor?>(null)}
    var galleryMore by remember {mutableStateOf(false)}
    var loadingOlder by remember {mutableStateOf(false)}
    var olderFailure by remember(thread){mutableStateOf(false)}
    var hasOlder by remember {mutableStateOf(true)}
    var inboxMore by memory.inboxMore
    var inboxCursor by memory.inboxCursor
    var loadingInbox by remember {mutableStateOf(false)}
    val inboxList=rememberLazyListState()
    suspend fun loadGallery(older:Boolean=false){
        if(galleryLoading)return
        galleryLoading=true;galleryError=null
        try{
            val wasEmpty=galleryMessages.isEmpty()
            val page=repo.mediaPage(thread,store.snapshot(),if(older)galleryCursor else null)
            galleryMessages=if(older)(galleryMessages+page.messages).distinctBy{it.key}else mergeLatestMessages(galleryMessages,page.messages)
            if(older||wasEmpty){galleryCursor=page.next;galleryMore=page.more}
            memory.galleries[thread]=MediaPage(galleryMessages,galleryCursor,galleryMore)
        }catch(e:CancellationException){throw e}catch(e:Exception){galleryError=e.message}finally{galleryLoading=false}
    }
    LaunchedEffect(thread,address,panel=="Gallery",galleryRetry,revision){
        if(address.isNotBlank()&&panel=="Gallery"){
            memory.galleries[thread]?.let{galleryMessages=it.messages;galleryCursor=it.next;galleryMore=it.more}
            loadGallery()
        }
    }

    var reminderMessage by remember { mutableStateOf<Message?>(null) }
    var emptyTrash by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Message?>(null) }
    var retryMessage by remember { mutableStateOf<Message?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var sending by remember { mutableStateOf(false) }
    var sentScroll by remember { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var attachments by remember { mutableStateOf(JSONArray()) }
    var keyboardImports by remember{mutableIntStateOf(0)}
    val keyboardImportLock=remember{kotlinx.coroutines.sync.Mutex()}
    var draftLoaded by remember { mutableStateOf(false) }
    var emoji by rememberSaveable { mutableStateOf(false) }
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var focusDate by rememberSaveable { mutableLongStateOf(0) }
    var focusNotice by remember { mutableStateOf(false) }
    var activityMembers by rememberSaveable {mutableStateOf(false)}
    var activityPeer by rememberSaveable {mutableStateOf<String?>(null)}
    var activityName by rememberSaveable {mutableStateOf("")}
    var membersVisible by rememberSaveable { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    val clipboard = LocalClipboardManager.current
    val list = rememberLazyListState()
    if (membersVisible && group != null) GroupMembers(group) { membersVisible = false }
    if(activityMembers&&group!=null)GroupMembers(group,choose={peer,name->activityMembers=false;activityPeer=peer;activityName=name}){activityMembers=false}
    activityPeer?.let{peer->ChatActivities(peer,activityName,{activityPeer=null},voice={activityPeer=null;openWalkie?.invoke(peer)},game={activityPeer=null;openGames?.invoke(peer,activityName)})}
    val spec = LocalAppearanceSpec.current
    val draftKey = remember(address) { messageDraftKey(address) }
    val currentDraftKey by rememberUpdatedState(draftKey)
    val latestDraft by rememberUpdatedState(draft)
    val latestAttachments by rememberUpdatedState(attachments)
    val latestLoaded by rememberUpdatedState(draftLoaded)
    fun launchJob(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) { if (e is CancellationException) throw e;
                error = e.message ?: "Could not complete this action"
            }
        }
    }
    fun saveDraft(key: String, text: String, media: JSONArray) {
        store.saveLater(JSONObject().put("action", "draft").put("key", key).put("text", text))
        store.saveLater(
            JSONObject().put("action", "draft_media").put("key", key).put("parts", media)
        )
    }
    fun open(peer: String, id: Long = 0, name: String = peer, targetKey: String? = null, targetDate: Long = 0) {
        focusKey = targetKey
        focusDate = targetDate
        focusNotice = false
        if (address.isNotEmpty() && draftLoaded) saveDraft(draftKey, draft, attachments)
        historyBefore=Long.MAX_VALUE
        messages=memory.windows[id].orEmpty();memory.raw.value=memory.rawWindows[id].orEmpty()
        galleryMessages=memory.galleries[id]?.messages.orEmpty();galleryCursor=memory.galleries[id]?.next;galleryMore=memory.galleries[id]?.more?:false
        hasOlder=true
        if(address!=peer)draftLoaded=false
        address = peer
        thread = id
        title = name
        search = ""
        panel = "Feed"
        chatSearch = false
        limit = 20
    }
    fun close() {
        if (group != null) {
            if (draftLoaded) saveDraft(draftKey, draft, attachments)
            exitGroup?.invoke()
            return
        }
        if (draftLoaded) saveDraft(draftKey, draft, attachments)
        address = ""
        focusKey = null
        thread = 0
        search = ""
        chatSearch = false
    }
    fun threadChange(id: Long, key: String, value: Any) {
        if (id <= 0 && group == null) {
            error = "Allow message access in Setup before changing conversation controls"
            return
        }
        launchJob {
            store.request(
                JSONObject().put("action", "thread").put("key", if (group != null && id == 0L) group.key else id.toString()).put(key, value)
            )
            state=store.snapshot()
        }
    }
    fun flag(m: Message, key: String, value: Any) {
        launchJob {
            store.request(
                JSONObject()
                    .put("action", "message")
                    .put("key", m.key)
                    .put("date", m.date)
                    .put("thread", thread)
                    .put(key, value)
            )
        }
    }
    val reactionLock = remember { kotlinx.coroutines.sync.Mutex() }
    fun react(m: Message, reaction: String) {
        val peer = address
        val messageThread = thread
        val groupTarget = groupFeed.child("keys").optString(m.key)
        launchJob {
            reactionLock.withLock {
                val remove = store.snapshot().messageFlags(m).optString("reaction") == reaction
                if (group != null) {
                    check(groupTarget.isNotEmpty()) { "This post has no unique reaction target in the loaded history." }
                    val wire = groupProtocol.reaction(groupTarget, if (remove) "" else reaction)
                    withContext(ChatStore.dispatcher) { MessagingTransport.sendMms(context, group.members, wire, JSONArray()) }
                } else repo.send(peer, reactionSms(reaction, m.text, remove))
                state = store.request(JSONObject().put("action", "message").put("key", m.key)
                    .put("date", m.date).put("thread", messageThread).put("reaction", if (remove) "" else reaction))
            }
        }
    }
    LaunchedEffect(intentEpoch) {
        if (group != null) open(group.address, group.thread, group.name, incomingMessageKey, incomingMessageDate)
        else if (!incomingAddress.isNullOrBlank()) open(incomingAddress, targetKey = incomingMessageKey, targetDate = incomingMessageDate)
        else if (incomingThread > 0) {
            launchJob {
                repo.conversation(incomingThread)
                    ?.let { open(it.address, it.id, it.name, incomingMessageKey, incomingMessageDate) }
                    ?: run { error = "The conversation is no longer available or message access is disabled." }
            }
        }
    }
    LaunchedEffect(Unit) {
        if(!memory.recovered){store.request(JSONObject().put("action", "recover").put("at", System.currentTimeMillis()));memory.recovered=true}
        memory.watch(context)
    }
    LaunchedEffect(epoch, revision, address, limit,historyBefore,search,folder) {
        loadingMessages=true
        try {
            state = if(address.isEmpty())repo.reconcileGroups()else store.snapshot()
            val groupThreads = state.savedGroups().map { it.thread }.filter { it > 0 }.toSet()
            if (address.isEmpty()) {
                val page=if(search.isNotBlank()){delay(250);repo.searchConversations(state,search)}else if(folder!="Inbox")state.child("threads").keysList().mapNotNull{it.toLongOrNull()}.filter{state.thread(it).optString("folder")==folder}.mapNotNull{repo.conversation(it)}else repo.conversations(41,state,directOnly=true).sortedWith(compareByDescending<Conversation>{it.date}.thenByDescending{it.id})
                val pins=state.child("threads").keysList().mapNotNull{it.toLongOrNull()}.filter{it !in groupThreads&&state.thread(it).optBoolean("pinned")&&page.none{r->r.id==it}}.mapNotNull{repo.conversation(it)}
                val window=if(search.isBlank()&&folder=="Inbox")page.take(40)else page
                val fresh=(window+pins).distinctBy{it.id}.filterNot{it.id in groupThreads}
                if(inboxCursor==null)inboxCursor=window.lastOrNull()?.let{MediaCursor(it.date,it.id)}
                rows=if(rows.isEmpty()||search.isNotBlank()||folder!="Inbox")fresh else (fresh+rows.filter{old->fresh.none{it.id==old.id}&&old.date<(page.minOfOrNull{it.date}?:Long.MAX_VALUE)}).sortedByDescending{it.date}
                if(rows.size<=fresh.size)inboxMore=search.isBlank()&&folder=="Inbox"&&page.size>40
            }
            if (address.isNotEmpty()) {
                if (thread == 0L) {
                    thread = repo.threadFor(address)
                    if (group != null && group.thread == 0L && thread > 0) {
                        store.request(JSONObject().put("action", "group_save").put("id", group.id).put("name", group.name).put("members", JSONArray(group.members)).put("thread", thread))
                    }
                }
                val fresh = repo.messages(thread, 20, state, address,beforeDate=historyBefore)
                hasOlder=fresh.size>=20
                val loaded=if(historyBefore==Long.MAX_VALUE)mergeLatestMessages(memory.raw.value,fresh)else fresh
                memory.raw.value=loaded
                if (group != null) {
                    val context=repo.groupParents(thread,groupProtocol.targets(loaded))
                    groupFeed = groupProtocol.feed((loaded+context).distinctBy{it.key})
                    val ids = groupFeed.getJSONArray("visible").let { a -> List(a.length()) { a.getString(it) }.toSet() }
                    messages = loaded.filter { it.key in ids }.map { it.copy(text = groupFeed.child("texts").optString(it.key, it.text)) }
                } else messages = loaded
                if(memory.initializedThreads.add(thread))memory.seenReactions.putAll(projectReactions(messages,state,if(group!=null)groupFeed else null).marks)
                if(historyBefore==Long.MAX_VALUE)memory.keep(thread,messages,memory.raw.value)
            }
        } catch (e: Exception) { if (e is CancellationException) throw e;
            error = e.message
        } finally {loadingMessages=false}
    }
    fun olderMessages(){
        if(loadingOlder||olderFailure||!hasOlder||messages.isEmpty())return
        val before=messages.first().date;val boundary=messages.filter{it.date==before};val smsId=boundary.filter{it.uri.startsWith("content://sms/")}.minOfOrNull{it.id}?:Long.MAX_VALUE;val mmsId=boundary.filter{it.uri.startsWith("content://mms/")}.minOfOrNull{it.id}?:Long.MAX_VALUE;loadingOlder=true
        launchJob{try{
            val batch=repo.messages(thread,40,state,address,beforeDate=before,beforeSmsId=smsId,beforeMmsId=mmsId);hasOlder=batch.size>=40
            val combined=(batch+memory.raw.value).distinctBy{it.key}.sortedBy{it.date}
            memory.raw.value=combined
            if(group!=null){val parents=repo.groupParents(thread,groupProtocol.targets(combined));groupFeed=groupProtocol.feed((combined+parents).distinctBy{it.key});val ids=groupFeed.getJSONArray("visible").let{a->List(a.length()){a.getString(it)}.toSet()};messages=combined.filter{it.key in ids}.map{it.copy(text=groupFeed.child("texts").optString(it.key,it.text))}}else messages=combined
            if(historyBefore==Long.MAX_VALUE)memory.keep(thread,messages,memory.raw.value)
        }catch(e:CancellationException){throw e}catch(e:Exception){olderFailure=true}finally{loadingOlder=false}}
    }
    LaunchedEffect(list,thread){snapshotFlow{Triple(list.isScrollInProgress&&list.firstVisibleItemIndex<12,messages.firstOrNull()?.key,loadingOlder)}.collectLatest{(nearTop,_,loading)->if(nearTop&&!loading)olderMessages()}}
    LaunchedEffect(address) {
        if (address.isNotEmpty()) {
            try {
                val s = store.snapshot()
                draft = s.child("drafts").optString(draftKey)
                attachments = s.child("media_drafts").optJSONArray(draftKey) ?: JSONArray()
                draftLoaded = true
            } catch (e: Exception) { if (e is CancellationException) throw e;
                error = e.message
            }
        }
    }
    LaunchedEffect(draft, attachments.toString(), draftLoaded) {
        if (draftLoaded && address.isNotEmpty()) {
            delay(350)
            saveDraft(draftKey, draft, attachments)
        }
    }
    val reactionView = remember(messages, state, groupFeed) {
        projectReactions(messages, state, if (group != null) groupFeed else null)
    }
    DisposableEffect(draftKey) {
        onDispose {
            if (latestLoaded && draftKey != "peer:e3b0c44298fc1c149afbf4c8996fb924")
                saveDraft(draftKey, latestDraft, latestAttachments)
        }
    }
    LaunchedEffect(thread, messages.lastOrNull()?.key,messages.lastOrNull()?.date,state.thread(thread).optLong("last_incoming_at")) {
        if (thread > 0) {
            val newest=messages.lastOrNull()?.date ?: 0L
            val readState=state.thread(thread)
            if(newest>0 && (readState.optBoolean("unread") || readState.optLong("read_at")<newest))repo.markRead(thread)
            if (reactionView.messages.isNotEmpty() && search.isEmpty() && focusKey == null && panel == "Feed") {
                if(positionedThread!=thread) list.scrollToItem(reactionView.messages.size)
                positionedThread=thread
            }
        }
    }
    LaunchedEffect(messages, focusKey, focusDate,loadingMessages) {
        val key = focusKey ?: return@LaunchedEffect
        if (address.isBlank() || messages.isEmpty() || loadingMessages) return@LaunchedEffect
        val index = focusedMessageIndex(reactionView.messages, key, focusDate)
        if (index >= 0) {
            list.scrollToItem(index + 1) // The earlier-history control occupies the first item.
            focusKey = null
        } else if (!focusNotice) {
            focusNotice = true
            error = "This message is outside the loaded history or has been removed. Use Load Earlier Messages to look farther back."
        }
    }
    BackHandler(enabled = address.isNotEmpty() || selecting) {
        if (selecting) {
            selecting = false
            selected = emptySet()
        } else close()
    }
    val messageSpeech=remember{SdkSpeechController(context,"host.messages",authorize={})}
    LaunchedEffect(messageSpeech.error){if(messageSpeech.messageKey!=null)messageSpeech.error?.let{error=it}}
    val speechLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(messageSpeech){messageSpeech.prepare("message")}
    DisposableEffect(messageSpeech,speechLifecycle){
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)messageSpeech.stop()}
        speechLifecycle.lifecycle.addObserver(observer)
        onDispose{speechLifecycle.lifecycle.removeObserver(observer);messageSpeech.close()}
    }
    LaunchedEffect(address){messageSpeech.stop()}
    var attachmentMenu by remember{mutableStateOf(false)}
    key(address){MessageAttachments(attachmentMenu,{attachmentMenu=false},budget={CarrierMedia.available(context,draft,attachments)},ready={part->
        if(attachments.length()>=10){MessageTransferProvider.file(context,part.getString("file")).delete();error="Use up to 10 attachments"}
        else attachments=JSONArray(attachments.toString()).put(part)
    },linkReady={link->
        val updated=if(draft.isBlank())link else draft+"\n"+link
        if(updated.length<=10000){draft=updated;true}else{error="Shorten your message before adding the cloud link.";false}
    })}
    AnimatedContent(address.isNotEmpty(),modifier=Modifier.fillMaxSize(),transitionSpec={
        (if(targetState) slideInHorizontally(tween(420,easing=FastOutSlowInEasing)){it} togetherWith ExitTransition.None
         else EnterTransition.None togetherWith slideOutHorizontally(tween(360,easing=FastOutSlowInEasing)){it})
            .apply{targetContentZIndex=if(targetState)1f else 0f}.using(null)
    },label="message-page") { isChat ->
    Box(Modifier.fillMaxSize()) {
    OnKlavBackground(Modifier.matchParentSize())
    Column(Modifier.fillMaxSize().testTag("messenger-screen")) {
        HomeHeader(
            if (!isChat) (if(folder=="Inbox") "Messages" else folder)
            else if (group != null) title
            else givenName ?: firstNameFromDisplayName(title, address) ?: address,
            titleAction = if (group != null) ({ membersVisible = true }) else null,
            back = if (isChat) ({ close() }) else null,
        ) {
            if (!isChat) {
                OriginalAction(R.drawable.ref_action_add, "Add Contact") {
                    runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                            )
                        }
                        .onFailure { error = "No contact editor is available" }
                }
                OriginalAction(R.drawable.ref_action_keypad, "Enter Phone Number") {
                    number = ""
                    numberDialog = true
                }
                OriginalAction(R.drawable.ref_nav_contacts, "Open Contacts") { contacts = true }
            } else {
                val controls=state.child("threads").child(if(thread>0)thread.toString()else group?.key?:"0")
                val muted=controls.optBoolean("muted");val pinned=controls.optBoolean("pinned")
                OriginalAction(if(muted)R.drawable.ref_action_bell_muted else R.drawable.ref_action_bell,if(muted)"Unmute Conversation"else"Mute Conversation",active=muted){threadChange(thread,"muted",!muted)}
                OriginalAction(R.drawable.ref_action_search,"Search Conversation",active=chatSearch||search.isNotBlank()){chatSearch=true}
                OriginalAction(if(pinned)R.drawable.ref_action_pin_filled else R.drawable.ref_action_pin,if(pinned)"Unpin Conversation"else"Pin Conversation",active=pinned){threadChange(thread,"pinned",!pinned)}
            }
        }
        if (!isChat) {
            PendingPictureBanner()
            MailboxControls(
                folder,
                {
                    folder = it
                    search = ""
                    selected = emptySet()
                    selecting = false
                },
                search,
                { search = it },
                selecting,
                {
                    selecting = it
                    selected = emptySet()
                },
            )
            if (!repo.allowed())
                OnKlavTextButton(onClick = setup) { Text("Set Up Messaging Access") }
            if (selecting) {
                val peers=rows.filter{it.id in selected}.flatMap{it.address.split(';',',')}.filter{it.isNotBlank()}.distinct()
                BulkConversationActions(selected.size,folder,peers.isNotEmpty()&&peers.all{conversationBlocked(state,it)},
                    move={target->selected.forEach{threadChange(it,"folder",target)};selected=emptySet();selecting=false},
                    block={value->launchJob{peers.forEach{store.request(JSONObject().put("action","blocked").put("address",it).put("blocked",value))};selected=emptySet();selecting=false;revision++}},
                    done={selected=emptySet();selecting=false})
            }
            val visible =
                rows.sortedWith(compareByDescending<Conversation>{state.thread(it.id).optBoolean("pinned")}.thenByDescending{it.date}).filter {
                    state.thread(it.id).optString("folder", "Inbox").ifEmpty { "Inbox" } ==
                        folder &&
                        (it.name.contains(search, true) ||
                            it.address.contains(search, true) ||
                            it.snippet.contains(search, true))
                }
            val trashed=state.child("threads").keysList().mapNotNull{it.toLongOrNull()}.filter{state.thread(it).optString("folder")=="Trash"}
            if(folder=="Trash") TrashControls(trashed.isNotEmpty(),
                restore={launchJob{for(id in trashed){if(store.snapshot().thread(id).optString("folder")=="Trash")store.request(JSONObject().put("action","thread").put("key",id.toString()).put("folder","Inbox"))};revision++}},empty={emptyTrash=true})
            LaunchedEffect(inboxList,address,folder,search){
                if(address.isEmpty()&&folder=="Inbox"&&search.isBlank())snapshotFlow{
                    val info=inboxList.layoutInfo
                    Pair(Triple(info.visibleItemsInfo.lastOrNull()?.index?:-1,info.totalItemsCount,inboxMore),inboxCursor)
                }.collect{(signal,_)->val(last,total,more)=signal
                    if(more&&!loadingInbox&&last>=total-4&&total>0){
                        val cursor=inboxCursor
                        if(cursor==null)inboxMore=false else{
                            loadingInbox=true
                            try{val batch=repo.conversations(41,state,cursor.date,cursor.id,directOnly=true).sortedWith(compareByDescending<Conversation>{it.date}.thenByDescending{it.id});val page=batch.take(40)
                                inboxMore=batch.size>40;inboxCursor=page.lastOrNull()?.let{MediaCursor(it.date,it.id)}
                                rows=(rows+page.filterNot{it.id in state.savedGroups().map{g->g.thread}}).distinctBy{it.id}
                            }catch(e:CancellationException){throw e}catch(e:Exception){inboxMore=false;error="Could not load more conversations. Reopen Messages to retry."}finally{loadingInbox=false}
                        }
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f).padding(top = 8.dp),
                state=inboxList,
                contentPadding=PaddingValues(horizontal=8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if(visible.isEmpty()) item { Text(if(loadingMessages) "Loading conversations…" else if(!repo.allowed()) "Allow SMS access in Setup to display your conversations." else if(search.isNotBlank()) "No matching conversations" else "No conversations in ${folder.lowercase()}",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant) }
                items(visible, key = { it.id }) { row ->
                    val blocked=conversationBlocked(state,row.address)
                    SwipeConversation("message-${row.id}",!selecting,revealedConversation,{revealedConversation=it},folder=="Archive",folder=="Trash",blocked,
                        "${if(blocked)"Unblock"else"Block"} the sender(s) of this conversation in OnKlav?",
                        onArchive={threadChange(row.id,"folder",if(folder=="Inbox")"Archive"else"Inbox")},
                        onDelete={if(folder=="Trash")permanentDelete=row else threadChange(row.id,"folder","Trash")},
                        onBlock={launchJob{row.address.split(';').filter{it.isNotBlank()}.forEach{peer->store.request(JSONObject().put("action","blocked").put("address",peer).put("blocked",!blocked))};revision++}}) {
                    Surface(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selecting)
                                        selected =
                                            if (row.id in selected) selected - row.id
                                            else selected + row.id
                                    else open(row.address, row.id, row.name)
                                },
                                onLongClick = { menu = row },
                            ),
                        shape = RoundedCornerShape(14.dp),
                        color =
                            if (row.unread || row.id in selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selecting)
                                Checkbox(
                                    row.id in selected,
                                    { selected = if (it) selected + row.id else selected - row.id },
                                )
                            ContactAvatar(row.address,row.name,Modifier.size(48.dp),revision)
                            Column(Modifier.weight(1f).padding(start=12.dp)){
                                Text(row.name,fontWeight=if(row.unread)FontWeight.Bold else FontWeight.Normal,fontSize=19.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Row(Modifier.padding(top=6.dp),verticalAlignment=Alignment.CenterVertically){
                                    Text(row.snippet,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(conversationTimestamp(row.date,use24Hour=android.text.format.DateFormat.is24HourFormat(context)),Modifier.padding(start=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val pinned=state.thread(row.id).optBoolean("pinned")
                            OriginalAction(if(pinned)R.drawable.ref_action_pin_filled else R.drawable.ref_action_pin,if(pinned)"Unpin ${row.name}"else"Pin ${row.name}",tint=if(pinned)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant){threadChange(row.id,"pinned",!pinned)}
                        }
                    }
                    }
                }
                if(loadingInbox)item(key="inbox-loading"){LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))}

            }
        } else {
            if (group != null && !MessagingTransport.carrierEnabled(context))
                Text("Group sending needs verified MMS support. Your draft stays saved.", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ConversationTabs(panel){panel=it}

            val visible =
                reactionView.messages.filter {
                    (panel != "Starred" || state.messageFlags(it).optBoolean("starred")) &&
                        (panel != "Gallery" || it.parts.isNotEmpty()) &&
                        (search.isEmpty() || it.text.contains(search, true))
                }
            MessageFeedAutoScroll(list, thread, sentScroll, panel=="Feed" && search.isEmpty() && focusKey==null && historyBefore==Long.MAX_VALUE)
            Box(Modifier.weight(1f).fillMaxWidth()){
            if(loadingMessages && messages.isEmpty())LinearProgressIndicator(Modifier.fillMaxWidth())
            if(panel=="Gallery")ConversationGallery(conversationMedia(galleryMessages+messages),state,galleryLoading,galleryError,{mediaUri=it.part.uri},{galleryRetry++},{message,value->flag(message,"starred",value)},galleryMore,{scope.launch{loadGallery(true)}})
            else LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding=PaddingValues(horizontal=8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    if(olderFailure)TextButton(onClick={olderFailure=false;olderMessages()}){Text("Retry earlier messages")}else if(loadingOlder)Box(Modifier.fillMaxWidth().height(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)}else Spacer(Modifier.height(24.dp))
                }
                items(visible, key = { it.key }) { m ->
                    val flags = state.messageFlags(m)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (m.sent) Arrangement.Start else Arrangement.End,
                    ) {
                        Column(Modifier.widthIn(max = 320.dp)) {
                            Box(Modifier.padding(bottom=14.dp)){
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                modifier =
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { messageMenu = m },
                                    ),
                            ) {
                                Column(
                                    Modifier.background(
                                            if (!m.sent) sentBrush(spec)
                                            else SolidColor(MaterialTheme.colorScheme.surface)
                                        )
                                        .padding(12.dp)
                                ) {
                                    val ink=if(!m.sent)sentInk(spec)else MaterialTheme.colorScheme.onSurface
                                    val senderName by produceState(if(m.sent)"You"else title,m.sender,revision){
                                        value=if(m.sent)"You"else if(group==null)title else withContext(Dispatchers.IO){repo.contactName(m.sender.ifBlank{address})}
                                    }
                                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(bottom=10.dp)){
                                        ContactAvatar(if(m.sent)""else m.sender.ifBlank{address},senderName,Modifier.size(40.dp),revision)
                                        Column(Modifier.weight(1f).padding(start=8.dp).clickable{messageMenu=m}){
                                            Text(senderName,fontSize=17.sp,color=ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.date))+if(m.status.isNotEmpty())" · "+m.status else "",fontSize=11.sp,color=ink)
                                        }
                                        ThemeStar(flags.optBoolean("starred"),{flag(m,"starred",it)},ink=if(!m.sent)ink else MaterialTheme.colorScheme.primary)
                                        IconButton(onClick={reactionMessage=m},modifier=Modifier.size(36.dp).testTag("message-reactions-${m.key}")){Icon(painterResource(R.drawable.ref_message_emoji),"Message Reactions",Modifier.size(23.dp),tint=ink)}
                                        if(messageSpeechText(m).isNotBlank()) IconButton(onClick={messageSpeech.playMessage(messageSpeechText(m),m.key);messageSpeech.error?.let{error=it}},enabled=m.text.isNotBlank(),modifier=Modifier.size(36.dp).testTag("message-speech-${m.key}")){if(messageSpeech.isMessageActive(m.key))SpeechActivityIcon(ink)else Icon(painterResource(R.drawable.ref_message_speaker),"Read Message Aloud",Modifier.size(24.dp),tint=ink)}
                                    }
                                    if (m.error.isNotBlank()) Text(m.error, fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(bottom = 8.dp))
                                    m.parts.forEach { p ->
                                        if(p.mime.startsWith("audio/"))MessageMediaAction("Audio",if(!m.sent)sentColor(spec)else MaterialTheme.colorScheme.surface){mediaUri=p.uri}
                                        else MessageThumbnail(
                                            p,
                                            Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                            naturalHeight = true,
                                        ) {
                                            mediaUri=p.uri
                                        }
                                        if(p.mime.startsWith("video/"))MessageMediaAction("Video",if(!m.sent)sentColor(spec)else MaterialTheme.colorScheme.surface){mediaUri=p.uri}
                                    }
                                    if (m.suspicious) SpamWarning(m.sender.ifBlank { address })
                                    val messageDisplayText=if(playVideo!=null)MessageVideoLinks.displayText(m.text)else m.text
                                    if (messageDisplayText.isNotEmpty())
                                        Text(
                                            messageLinkText(messageDisplayText,MaterialTheme.colorScheme.primary,openWeb),
                                            fontSize = spec.size.sp,
                                            color =
                                                if (!m.sent) sentInk(spec)
                                                else MaterialTheme.colorScheme.onSurface,
                                        )
                                    if(playVideo!=null)MessageVideoCards(m.text,bubbleColor=if(!m.sent)sentColor(spec)else MaterialTheme.colorScheme.surface){video->playVideo("https://www.youtube.com/watch?v=${video.id}&t=${video.start}")}
                                    if (flags.optBoolean("locked")) Text("Locked", fontSize = 11.sp)

                                }
                            }
                            ReactionDecoration(m.key, reactionView.marks[m.key].orEmpty(), memory.seenReactions)
                            }
                        }
                    }
                }
                if (visible.isEmpty() && !loadingMessages)
                    item {
                        Text(
                            if (panel == "Starred") "Star a message to keep it here."
                            else if (panel == "Gallery") "Photos and attachments appear here."
                            else "No messages to display.",
                            Modifier.padding(16.dp),
                        )
                    }
            }
            val atLatest by remember {derivedStateOf{historyBefore==Long.MAX_VALUE&&!list.canScrollForward}}
            val hasPeer=group!=null||activityRecipients(address.split(';',','),"").size==1
            if(panel=="Feed"&&(!atLatest||hasPeer))ChatFeedAction(atLatest,Modifier.align(Alignment.BottomEnd).padding(12.dp)){
                if(!atLatest){if(historyBefore!=Long.MAX_VALUE){focusKey=null;historyBefore=Long.MAX_VALUE;limit=20;positionedThread=-1L}else scope.launch{list.scrollToItem((list.layoutInfo.totalItemsCount-1).coerceAtLeast(0))}}else if(group!=null)activityMembers=true else {activityPeer=activityRecipients(address.split(';',','),"").singleOrNull();activityName=title}
            }
            }
            if(keyboardImports>0)Text("Adding keyboard image…",Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.labelSmall)
            if (attachments.length() > 0)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    repeat(attachments.length()) { i ->
                        val p = attachments.getJSONObject(i)
                        TextButton(
                            onClick = {
                                attachments =
                                    JSONArray().apply {
                                        repeat(attachments.length()) { j ->
                                            if (i != j) put(attachments.getJSONObject(j))
                                        }
                                    }
                            }
                        ) {
                            Text(p.optString("name", "Attachment") + " ×")
                        }
                    }
                }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(
                    onClick = { attachmentMenu=true },
                    modifier = Modifier.size(40.dp),
                    enabled = !sending,
                ) {
                    Icon(painterResource(R.drawable.ref_message_attach), "Attach File")
                }
                IconButton(onClick = { emoji = true }, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ref_message_emoji), "Emoji")
                }
                Surface(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    key(draftKey){MessageComposer(draft,draftLoaded,{draft=it},{content,uri->
                        if(attachments.length()+keyboardImports>=10)error="Use up to 10 attachments"
                        else{
                            val target=draftKey;keyboardImports++
                            scope.launch{
                                var part:JSONObject?=null;var kept=false
                                try{
                                    keyboardImportLock.lock()
                                    try{
                                        val state=if(currentDraftKey!=target)store.snapshot()else null
                                        val text=state?.child("drafts")?.optString(target)?:draft
                                        val parts=state?.child("media_drafts")?.optJSONArray(target)?:attachments
                                        require(parts.length()<10){"Use up to 10 attachments"}
                                        val budget=CarrierMedia.available(context,text,parts)
                                        withContext(Dispatchers.IO){part=MessageMedia.import(context,uri,budget)}
                                        ensureActive()
                                        val ready=requireNotNull(part)
                                        if(currentDraftKey==target&&draftLoaded){
                                            require(attachments.length()<10&&ready.getInt("bytes")<=CarrierMedia.available(context,draft,attachments)){"The draft changed. Remove an attachment and try again"}
                                            attachments=JSONArray(attachments.toString()).put(ready)
                                        }else withContext(ChatStore.dispatcher){
                                            val saved=store.requestSync(JSONObject().put("action","snapshot"));val existing=saved.child("media_drafts").optJSONArray(target)?:JSONArray()
                                            require(existing.length()<10&&ready.getInt("bytes")<=CarrierMedia.available(context,saved.child("drafts").optString(target),existing)){"This message is full. Choose a smaller image"}
                                            store.requestSync(JSONObject().put("action","draft_media").put("key",target).put("parts",JSONArray(existing.toString()).put(ready)))
                                        }
                                        kept=true
                                    }finally{keyboardImportLock.unlock()}
                                }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Could not add the keyboard image"}
                                finally{if(!kept)part?.optString("file")?.takeIf{it.isNotEmpty()}?.let{MessageTransferProvider.file(context,it).delete()};java.lang.ref.Reference.reachabilityFence(content);keyboardImports--}
                            }
                        }
                    })}
                }
                IconButton(
                    onClick = {
                        launchJob {
                            val sentText=draft;val sentParts=JSONArray(attachments.toString());val sentKey=draftKey;val sentAddress=address;val sentGroup=group
                            sending = true
                            try {
                                if (sentParts.length() > 0 || sentAddress.contains(';'))
                                    withContext(ChatStore.dispatcher) {
                                        MessagingTransport.sendMms(
                                            context,
                                            sentAddress.split(';').filter { it.isNotBlank() },
                                            if (sentGroup != null) groupProtocol.post(sentText) else sentText,
                                            sentParts,
                                        )
                                    }
                                else repo.send(sentAddress, sentText)
                                if(draftKey==sentKey){if(draft.startsWith(sentText))draft=draft.removePrefix(sentText);val sentFiles=(0 until sentParts.length()).map{sentParts.getJSONObject(it).optString("file")}.toSet();attachments=JSONArray().apply{for(i in 0 until attachments.length()){val part=attachments.getJSONObject(i);if(part.optString("file") !in sentFiles)put(part)}};saveDraft(sentKey,draft,attachments)}
                                focusKey = null
                                search = ""
                                panel = "Feed"
                                historyBefore = Long.MAX_VALUE
                                sentScroll++
                                revision++
                            } finally {
                                sending = false
                            }
                        }
                    },
                    enabled = !sending && keyboardImports==0 && (draft.isNotBlank() || attachments.length() > 0),
                    modifier = Modifier.size(44.dp),
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp))
                    else Icon(painterResource(R.drawable.ref_message_send), "Send Message")
                }
            }
        }
    }
    }
    }
    RightSlideRoute(if(contacts) "contacts" else null) {
        ContactsScreen({contacts=false},walkie=openWalkie?.let { callback -> { peer -> contacts=false;callback(peer) } }) {peer->contacts=false;open(peer)}
    }
    if(emptyTrash) ReferenceDialog("Empty trash?",{emptyTrash=false},footer={
        DialogAction("Cancel"){emptyTrash=false}
        DialogAction("Empty trash",destructive=true){
            emptyTrash=false
            launchJob {
                val latest=store.snapshot();val ids=latest.child("threads").keysList().mapNotNull{it.toLongOrNull()}.filter{latest.thread(it).optString("folder")=="Trash"}
                repo.emptyTrash(ids)
                val survivors=ids.mapNotNull{repo.conversation(it)};rows=(rows.filterNot{it.id in ids}+survivors).distinctBy{it.id};ids.forEach{memory.windows.remove(it);memory.rawWindows.remove(it);memory.galleries.remove(it)}
                revision++
            }
        }
    }) { Text("Permanently delete conversations in Trash? Locked messages are kept. Phone deletion requires messaging Setup.",Modifier.padding(16.dp)) }
    permanentDelete?.let { row -> ReferenceDialog("Delete conversation permanently?",{permanentDelete=null},footer={
        DialogAction("Cancel"){permanentDelete=null}
        DialogAction("Delete",destructive=true){permanentDelete=null;launchJob{repo.emptyTrash(listOf(row.id));memory.windows.remove(row.id);memory.rawWindows.remove(row.id);memory.galleries.remove(row.id);revision++}}
    }){Text("Permanently delete this conversation from the phone? Locked messages are kept.",Modifier.padding(20.dp))} }
    menu?.let { row ->
        var unread by remember(row.id) { mutableStateOf(row.unread) }
        var saving by remember(row.id) { mutableStateOf(false) }
        ReferenceDialog("", {if(!saving)menu=null},footer={
            DialogAction("Cancel",enabled=!saving,modifier=Modifier.weight(1f)){menu=null}
            DialogAction("Save",enabled=!saving,modifier=Modifier.weight(1f)){
                saving=true
                val chosen=unread
                launchJob {try {
                    repo.markRead(row.id,chosen)
                    rows=rows.map{if(it.id==row.id)it.copy(unread=chosen)else it}
                    state=store.snapshot()
                    revision++
                    menu=null
                } finally {saving=false}}
            }
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=28.dp).testTag("conversation-read-status"),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                Text("Read",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.End,fontWeight=if(!unread)FontWeight.SemiBold else FontWeight.Normal,color=if(!unread)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=19.sp)
                Switch(unread,{unread=it},Modifier.padding(horizontal=12.dp).testTag("conversation-unread-switch"))
                Text("Unread",Modifier.weight(1f),fontWeight=if(unread)FontWeight.SemiBold else FontWeight.Normal,color=if(unread)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=19.sp)
            }
        }
    }
    reminderMessage?.let { m ->
        ReplyReminderChooser(
            existing = state.messageFlags(m).optLong("remind_at"),
            close = { reminderMessage = null },
            save = { due ->
                launchJob {
                    withContext(ChatStore.dispatcher) {
                        ReplyReminders.set(context, m, thread, title, due)
                    }
                }
                reminderMessage = null
            },
        )
    }
    mediaUri?.let{selected->
        val media=conversationMedia(galleryMessages+messages)
        if(media.isNotEmpty())ConversationMediaViewer(media,selected,state,{message,value->flag(message,"starred",value)},{mediaUri=null})
    }
    messageMenu?.let { m ->
        val flags = state.messageFlags(m)
        ReferenceDialog(
            "Message",
            { messageMenu = null },
            footer = { DialogAction("Close") { messageMenu = null } },
        ) {
            Text(
                DateFormat.getDateTimeInstance().format(Date(m.date)) + " · " + m.status,
                fontSize = 12.sp,
            )
            SettingsChoice("Copy Text") {
                clipboard.setText(AnnotatedString(m.text))
                messageMenu = null
            }
            SettingsChoice("Forward") {
                forwarding = m
                number = ""
                numberDialog = true
                messageMenu = null
            }
            SettingsChoice(if (flags.optBoolean("locked")) "Unlock" else "Lock") {
                flag(m, "locked", !flags.optBoolean("locked"))
                messageMenu = null
            }
            SettingsChoice("Remind Me to Reply") {
                reminderMessage = m
                messageMenu = null
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { reaction ->
                    TextButton(
                        onClick = {
                            react(m,reaction)
                            messageMenu = null
                        }
                    ) {
                        Text(reaction)
                    }
                }
            }
            if (m.parts.isNotEmpty())
                m.parts.forEach { p ->
                    SettingsChoice("Open " + p.name) {
                        if(p.mime.startsWith("image/")||p.mime.startsWith("video/")||p.mime.startsWith("audio/"))mediaUri=p.uri
                        else launchJob { withContext(Dispatchers.IO) { MessageMedia.open(context, p) } }
                        messageMenu = null
                    }
                    SettingsChoice("Share " + p.name) {
                        launchJob { withContext(Dispatchers.IO) { MessageMedia.share(context, p) } }
                        messageMenu = null
                    }
                }
            if (m.status in listOf("Failed", "Status unknown", "Delivery failed")) {
                val trackedText = m.key.startsWith("out:") &&
                    state.child("outgoing").child(m.key.removePrefix("out:")).optString("kind") == "sms"
                if (trackedText) SettingsChoice("Retry Text") {
                    retryMessage = m
                    messageMenu = null
                } else SettingsChoice("Copy to Draft") {
                    draft = m.text
                    error = "Check whether this message arrived before sending another copy."
                    messageMenu = null
                }
            }
            SettingsChoice("Delete") {
                confirmDelete = m
                messageMenu = null
            }
        }
    }
    retryMessage?.let { m ->
        OnKlavAlertDialog(
            onDismissRequest = { retryMessage = null },
            title = { Text("Retry Text?") },
            text = { Text("The original may still arrive. Check with the recipient before sending another copy.") },
            confirmButton = {
                DialogAction("Retry", enabled = !sending) {
                    retryMessage = null
                    launchJob {
                        sending = true
                        try {
                            withContext(ChatStore.dispatcher) { MessagingTransport.retryText(context, m.key) }
                            revision++
                        } finally { sending = false }
                    }
                }
            },
            dismissButton = { DialogAction("Cancel") { retryMessage = null } },
        )
    }
    confirmDelete?.let { m ->
        OnKlavAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete Message?") },
            text = {
                Text(
                    "This permanently removes this message from the phone. Locked messages must be unlocked first."
                )
            },
            confirmButton = {
                DialogAction("Delete") {
                    launchJob {
                        repo.delete(m, state)
                        messages=messages.filterNot{it.key==m.key};memory.raw.value=memory.raw.value.filterNot{it.key==m.key};memory.keep(thread,messages,memory.raw.value)
                        revision++
                    }
                    confirmDelete = null
                }
            },
            dismissButton = { DialogAction("Cancel") { confirmDelete = null } },
        )
    }
    if(numberDialog) NumberEntryDialog(number,{number=it},
        dismiss={numberDialog=false;forwarding=null},forwarding=forwarding!=null,
        choose={
                    if (number.isBlank()) error = "Enter a phone number"
                    else {
                        val captured = forwarding
                        val target = number.trim()
                        numberDialog = false
                        forwarding = null
                        launchJob {
                            if (captured != null) {
                                val parts = JSONArray()
                                withContext(Dispatchers.IO) {
                                    captured.parts.forEach {
                                        parts.put(
                                            MessageMedia.import(
                                                context,
                                                MessageMedia.grantable(context, it),
                                                280000,
                                            )
                                        )
                                    }
                                }
                                val key = messageDraftKey(target)
                                store.request(
                                    JSONObject()
                                        .put("action", "draft")
                                        .put("key", key)
                                        .put("text", captured.text)
                                )
                                store.request(
                                    JSONObject()
                                        .put("action", "draft_media")
                                        .put("key", key)
                                        .put("parts", parts)
                                )
                            }
                            open(target)
                        }
                    }
        })
    reactionMessage?.let{m->EmojiPicker({reactionMessage=null}){reaction->reactionMessage=null;react(m,reaction)}}
    if(chatSearch)ConversationSearchDialog(repo,thread,{chatSearch=false}){hit->
        chatSearch=false;search="";panel="Feed";focusNotice=false;focusKey=hit.key;focusDate=hit.date;historyBefore=hit.date;limit=20
    }
    if (emoji)
        EmojiPicker({ emoji = false }) {
            draft += it
            emoji = false
        }
    ErrorDialog(error ?: failure) {
        error = null
        ChatStore.lastError.value = null
    }
}

@Composable
private fun EmojiPicker(close: () -> Unit, choose: (String) -> Unit) {
    val context = LocalContext.current
    val rows = remember {
        runCatching {
                JSONArray(
                    context.assets.open("messenger/emoji.json").bufferedReader().use {
                        it.readText()
                    }
                )
            }
            .getOrDefault(JSONArray())
    }
    var query by rememberSaveable { mutableStateOf("") }
    ReferenceDialog("Emoji", close, footer = { DialogAction("Close", onClick = close) }) {
        OutlinedTextField(
            query,
            { query = it },
            singleLine = true,
            placeholder = { Text("Search Emoji") },
        )
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            val selected =
                (0 until rows.length())
                    .map { rows.getJSONObject(it) }
                    .filter {
                        query.isBlank() ||
                            it.optString("name").contains(query, true) ||
                            it.optString("category").contains(query, true)
                    }
            items(selected.chunked(6)) { chunk ->
                Row {
                    chunk.forEach { j ->
                        TextButton(
                            onClick = { choose(j.optString("emoji")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(j.optString("emoji"), fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
