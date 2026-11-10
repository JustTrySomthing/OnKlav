package app.onklav.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CatalogArtwork(game: PlatformGame, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap =
        remember(game.artwork) {
            context.assets.open("catalog/${game.artwork}.webp").use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        }
    Image(bitmap, null, modifier, contentScale = ContentScale.Fit)
}

/** Feature new included games once, then preserve the owner’s ordering. */
internal fun includedGameOrder(prefs:android.content.SharedPreferences,allIds:List<String>):List<String> {
    var saved=prefs.getString("order","").orEmpty().split(',').filter{it.isNotBlank()}.distinct()
    if("panda" in allIds && !prefs.getBoolean("panda_premiere_v1",false)) {
        saved=listOf("panda")+saved.filter{it!="panda"}
        // Keep installed-game IDs even before their asynchronous catalog load completes.
        prefs.edit().putString("order",saved.joinToString(",")).putBoolean("panda_premiere_v1",true).apply()
    }
    if (GamePackages.SHROOM_ID in allIds && !prefs.getBoolean("shroom_premiere_v1", false)) {
        val featured = listOf("panda", GamePackages.SHROOM_ID)
        saved = featured + saved.filterNot { it in featured }
        prefs.edit().putString("order", saved.joinToString(",")).putBoolean("shroom_premiere_v1", true).apply()
    }
    if (GamePackages.LAST_LIGHT_ID in allIds && !prefs.getBoolean("last_light_premiere_v1", false)) {
        saved = listOf(GamePackages.LAST_LIGHT_ID) + saved.filterNot { it == GamePackages.LAST_LIGHT_ID }
        prefs.edit().putString("order", saved.joinToString(",")).putBoolean("last_light_premiere_v1", true).apply()
    }
    return (saved.filter{it in allIds}+allIds).distinct()
}

@Composable
fun GamesCatalog(friendPeer:String?=null,launch: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("game_catalog", 0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<List<GamePackages.Game>>(emptyList()) }
    val removedIds = removedCatalogIds(games = true)
    val catalogEpoch by GamePackages.catalogEpoch.collectAsState()
    LaunchedEffect(catalogEpoch) {
        try {
            installed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                GamePackages.open(context).installed()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            notice = e.message ?: "Could not read installed games"
        }
    }
    val installedFriendIds = installed.filter { game ->
        val modes = game.manifest.optJSONArray("play_modes")
        modes != null && (0 until modes.length()).any { modes.optString(it) == "turn_sms" }
    }.map { it.id }.toSet()
    val friendGameIds = (platformGames.map { it.id }.toSet() + installedFriendIds) - singlePlayerOnlyGames
    val available = (platformGames + installed.filterNot { it.id == GamePackages.SHROOM_ID }.map {
        PlatformGame(it.id, it.name, it.manifest.getString("description"), "pets")
    }).filter { friendPeer == null || it.id in friendGameIds }
    val colors = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(18.dp)
    val allIds = available.map { it.id }
    var ids by remember {
        mutableStateOf(
            includedGameOrder(prefs,allIds)
        )
    }
    LaunchedEffect(allIds) { ids=(ids+allIds).filter{it in allIds}.distinct() }
    val games =
        (ids + allIds).distinct().mapNotNull { id -> available.find { it.id == id && id !in removedIds } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dragged by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf(ids) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var friendGame by remember { mutableStateOf<PlatformGame?>(null) }
    var blocking by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var checkingStart by remember { mutableStateOf(false) }
    fun enter(game:PlatformGame,friend:Boolean=false,solo:Boolean=false) {
        if(checkingStart)return
        checkingStart=true
        scope.launch {try {
            val match=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){ FriendGames.active(context,game.id) }
            when {
                match!=null && friendPeer!=null && phoneKey(match.getString("peer"))!=phoneKey(friendPeer) -> {
                    notice="This game already has a match or invitation with another contact. Open Games to manage it."
                }
                match?.optString("status")=="active" -> {
                    if(friendPeer==null)launch(game.id)
                    else WebGameActivity.launch(context,game.id,friend=match.getString("id"))
                }
                match!=null->{blocking=match}
                friend->{friendGame=game}
                solo->{WebGameActivity.launch(context,game.id,fresh=true)}
                else->{launch(game.id)}
            }
        } catch(e:Exception){notice=e.message?:"Could not open the game"}finally{checkingStart=false} }
    }
    fun openGame(game:PlatformGame)=enter(game)
    fun openFriend(game:PlatformGame)=enter(game,friend=true)
    fun persist() {
        prefs.edit().putString("order", ids.joinToString(",")).apply()
    }
    fun move(id: String, target: String) {
        val copy = ids.toMutableList()
        val from = copy.indexOf(id)
        val to = copy.indexOf(target)
        if (from >= 0 && to >= 0 && from != to) {
            copy.add(to, copy.removeAt(from))
            ids = copy
        }
    }
    val start by
        rememberUpdatedState<(Offset) -> Unit>({ at ->
            val item =
                list.layoutInfo.visibleItemsInfo.find {
                    at.y >= it.offset && at.y < it.offset + it.size
                }
            dragged = item?.key as? String
            snapshot = ids
            finger = at
        })
    val drag by
        rememberUpdatedState<(Offset) -> Unit>({ delta ->
            finger += delta
            list.layoutInfo.visibleItemsInfo
                .find { finger.y >= it.offset && finger.y < it.offset + it.size }
                ?.let { target -> dragged?.let { move(it, target.key as String) } }
        })
    LaunchedEffect(dragged, finger.y) {
        while (dragged != null) {
            val delta =
                if (finger.y < 40) -12
                else if (finger.y > list.layoutInfo.viewportEndOffset - 40) 12 else 0
            if (delta == 0) break
            var moved=0f
            list.scroll { moved=scrollBy(delta.toFloat()) }
            if(moved==0f)break
            delay(32)
        }
    }
    Box(
        Modifier.fillMaxSize().testTag("games-catalog").pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { start(it) },
                onDragEnd = {
                    persist()
                    dragged = null
                },
                onDragCancel = {
                    ids = snapshot
                    dragged = null
                },
            ) { change, delta ->
                if (dragged != null) {
                    change.consume()
                    drag(delta)
                }
            }
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(end = 14.dp),
            state = list,
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
            userScrollEnabled = dragged == null,
        ) {
            item("friend-sessions") { FriendSessionsPanel(friendPeer, allIds.toSet() - removedIds) }
            items(games, key = { it.id }) { game ->
                Row(
                    Modifier.fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp)
                        .heightIn(min = 108.dp)
                        .alpha(if (dragged == game.id) .35f else 1f)
                        .clip(cardShape)
                        .background(colors.surfaceContainerLow)
                        .border(1.dp, colors.outlineVariant, cardShape)
                        .clickable(role = Role.Button, onClickLabel = "Open ${game.title}") { if(friendPeer==null)openGame(game) else openFriend(game) }
                        .semantics {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction("Move Earlier") {
                                        val at = ids.indexOf(game.id)
                                        if (at <= 0) false
                                        else {
                                            move(game.id, ids[at - 1])
                                            persist()
                                            true
                                        }
                                    },
                                    CustomAccessibilityAction("Move Later") {
                                        val at = ids.indexOf(game.id)
                                        if (at >= ids.lastIndex) false
                                        else {
                                            move(game.id, ids[at + 1])
                                            persist()
                                            true
                                        }
                                    },
                                )
                        }
                        .padding(12.dp)
                        .testTag("game-card-${game.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val supportsFriend = game.id in friendGameIds
                    CatalogArtwork(game, Modifier.size(84.dp))
                    Column(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            game.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Row(
                            Modifier.border(1.dp, colors.primary, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.primary,
                            )
                            Text(
                                if (supportsFriend) "1 player & 2 player" else "1 player",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface,
                            )
                        }
                    }
                    Box(
                        Modifier.padding(end = 6.dp).size(34.dp).border(1.5.dp, colors.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(14.dp)) {
                            val triangle = Path().apply {
                                moveTo(size.width * .23f, size.height * .10f)
                                lineTo(size.width * .87f, size.height * .50f)
                                lineTo(size.width * .23f, size.height * .90f)
                                close()
                            }
                            drawPath(triangle, colors.primary, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
        GameCatalogScrollBar(list,Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(14.dp).padding(vertical=10.dp))
    }
    friendGame?.let{game->FriendInviteDialog(game,friendPeer){friendGame=null}}
    blocking?.let{match->
        val game=available.firstOrNull{it.id==match.getString("game")}
        if(game!=null)FriendInviteDialog(game,match.getString("peer"),existing=match.getString("id")){blocking=null}
        else AlertDialog(onDismissRequest={blocking=null},title={Text("Game in progress")},text={Text("Reinstall the current game to resume it, or cancel it in Games.")},confirmButton={TextButton(onClick={blocking=null}){Text("Close")}})
    }
    ErrorDialog(notice) { notice = null }
}

/** Always visible; thumb is derived from the real lazy list, never a second scroll state. */
@Composable
private fun GameCatalogScrollBar(list:LazyListState,modifier:Modifier) {
    val scope=rememberCoroutineScope()
    val track=MaterialTheme.colorScheme.onSurface.copy(alpha=.16f)
    val thumb=MaterialTheme.colorScheme.primary.copy(alpha=.9f)
    androidx.compose.foundation.Canvas(modifier.semantics {
        contentDescription="Games scrollbar"
        customActions=listOf(CustomAccessibilityAction("Scroll down"){scope.launch{list.animateScrollToItem((list.firstVisibleItemIndex+2).coerceAtMost((list.layoutInfo.totalItemsCount-1).coerceAtLeast(0)))};true},CustomAccessibilityAction("Scroll up"){scope.launch{list.animateScrollToItem((list.firstVisibleItemIndex-2).coerceAtLeast(0))};true})
    }.pointerInput(list){
        detectVerticalDragGestures { change,_ ->
            change.consume()
            val total=list.layoutInfo.totalItemsCount
            if(total>0) {val index=((change.position.y/size.height).coerceIn(0f,.999f)*total).toInt();scope.launch{list.scrollToItem(index)}}
        }
    }) {
        val info=list.layoutInfo
        val total=info.totalItemsCount.coerceAtLeast(1)
        val visible=info.visibleItemsInfo
        val rowHeight=(visible.firstOrNull{it.key!="friend-sessions"}?.size?:1).coerceAtLeast(1).toFloat()
        val headerHeight=(visible.firstOrNull{it.key=="friend-sessions"}?.size?:0).toFloat()
        val viewport=(info.viewportEndOffset-info.viewportStartOffset).coerceAtLeast(1).toFloat()
        val content=(rowHeight*(total-1)+headerHeight).coerceAtLeast(viewport)
        val fraction=(viewport/content).coerceIn(.12f,1f)
        val height=(size.height*fraction).coerceAtLeast(32.dp.toPx()).coerceAtMost(size.height)
        val offset=(list.firstVisibleItemIndex-1).coerceAtLeast(0)*rowHeight+list.firstVisibleItemScrollOffset+(if(list.firstVisibleItemIndex>0)headerHeight else 0f)
        val position=offset/(content-viewport).coerceAtLeast(1f)
        val top=(size.height-height)*position.coerceIn(0f,1f)
        drawRoundRect(track,Offset(size.width/2-2.dp.toPx(),0f),androidx.compose.ui.geometry.Size(4.dp.toPx(),size.height),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        drawRoundRect(thumb,Offset(size.width/2-2.dp.toPx(),top),androidx.compose.ui.geometry.Size(4.dp.toPx(),height),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
    }
}
