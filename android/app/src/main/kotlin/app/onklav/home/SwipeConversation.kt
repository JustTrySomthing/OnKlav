package app.onklav.home

import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import kotlin.math.roundToInt

/** Reveal only; neither a fast fling nor an interrupted drag can change stored data. */
internal fun swipeRevealTarget(offset:Float,limit:Float):Float = when {
    offset < -limit * .25f -> -limit
    offset > limit * .25f -> limit
    else -> 0f
}
@Composable internal fun SwipeConversation(
    identity:String, enabled:Boolean, opened:String?, reveal:(String?)->Unit,
    archived:Boolean, trashed:Boolean, blocked:Boolean,
    blockDetail:String, onArchive:()->Unit, onDelete:()->Unit, onBlock:()->Unit,
    content:@Composable ()->Unit
) {
    val currentOpened by rememberUpdatedState(opened)
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds().testTag("swipe-$identity")) {
        val limit=with(density){(maxWidth * .59f).toPx()}
        var direction by remember(identity){mutableFloatStateOf(-1f)}
        var dragging by remember(identity){mutableStateOf(false)}
        var drag by remember(identity){mutableFloatStateOf(0f)}
        val position=remember(identity){Animatable(0f)}
        val scope=rememberCoroutineScope()
        var settling by remember(identity){mutableStateOf<Job?>(null)}
        var start by remember(identity){mutableFloatStateOf(0f)}
        val offset=if(dragging)drag else position.value
        fun settle(value:Float){settling?.cancel();settling=scope.launch{position.snapTo(drag);dragging=false;position.animateTo(value,tween(190))}}
        fun gestures()=Modifier.pointerInput(identity,enabled,limit) {
            if(enabled)detectHorizontalDragGestures(
                onDragStart={settling?.cancel();drag=position.value;start=drag;dragging=true},
                onHorizontalDrag={change,amount->change.consume();drag=(drag+amount).coerceIn(-limit,limit)},
                onDragCancel={settle(if(currentOpened==identity)direction*limit else 0f)},
                onDragEnd={val next=if(abs(start)>limit*.8f&&abs(drag)<abs(start)-24*density.density)0f else swipeRevealTarget(drag,limit);direction=if(next>0)1f else -1f;reveal(if(next==0f)null else identity);settle(next)}
            )
        }
        var confirm by remember(identity){mutableStateOf<String?>(null)}
        LaunchedEffect(enabled,opened){if(!enabled || opened!=identity){settling?.cancel();if(!dragging){position.animateTo(0f,tween(190));drag=0f}}}
        if(offset!=0f) Row(Modifier.align(if(offset<0)Alignment.CenterEnd else Alignment.CenterStart).fillMaxWidth(.59f).heightIn(min=72.dp).padding(horizontal=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            fun choose(action:String){if(action=="Delete" && trashed){reveal(null);onDelete()}else confirm=action}
            @Composable fun RowScope.action(text:String,accent:Boolean=false) {
                Surface(Modifier.weight(1f).heightIn(min=64.dp).clickable{choose(text)},shape=RoundedCornerShape(12.dp),color=if(accent)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(horizontal=3.dp,vertical=22.dp),contentAlignment=Alignment.Center){Text(text,fontSize=13.sp,maxLines=1,color=if(accent)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
            action(if(blocked)"Unblock"else"Block")
            action(if(archived || trashed)"Restore"else"Archive")
            action("Delete",true)
        }
        Box(Modifier.offset{IntOffset(offset.roundToInt(),0)}.then(gestures()).semantics {
            if(enabled)customActions=listOf(CustomAccessibilityAction("Show conversation actions"){direction=-1f;drag=position.value;reveal(identity);settle(-limit);true},CustomAccessibilityAction("Close conversation actions"){reveal(null);true})
        }) {content();if(opened==identity&&!dragging)Box(Modifier.matchParentSize().clickable{drag=position.value;reveal(null);settle(0f)})}
        confirm?.let { action ->
            val deleting=action=="Delete"
            ReferenceDialog("$action conversation?",{confirm=null},footer={
                DialogAction("Cancel"){confirm=null}
                DialogAction(action,destructive=deleting){confirm=null;reveal(null);when(action){"Delete"->onDelete();"Archive","Restore"->onArchive();else->onBlock()}}
            }) {Text(when(action){"Delete"->if(trashed)"Permanently delete this conversation?"else"Move this conversation to Trash? You can restore it later.";"Archive"->"Move this conversation to Archive?";"Restore"->"Return this conversation to Inbox?";else->blockDetail},Modifier.padding(20.dp))}
        }
    }
}
internal fun conversationBlocked(state:org.json.JSONObject,address:String):Boolean {
    val peers=address.split(';').filter{it.isNotBlank()}
    val blocked=state.optJSONArray("blocked")?:return false
    return peers.isNotEmpty() && peers.all{peer->(0 until blocked.length()).any{phoneKey(blocked.getString(it))==phoneKey(peer)}}
}
