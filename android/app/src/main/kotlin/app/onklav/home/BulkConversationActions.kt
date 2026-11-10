package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.onklav.shared.*

/** Wrap actions instead of allowing a full-width SDK button to consume a whole Row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BulkConversationActions(count:Int,folder:String,blocked:Boolean=false,
    move:(String)->Unit,block:(Boolean)->Unit,done:()->Unit,unread:(()->Unit)?=null,delete:(()->Unit)?=null) {
    var confirm by remember { mutableStateOf(false) }
    Surface(color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth().testTag("bulk-actions")) {
        Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
            Row { Text(if(count==0)"Select conversations"else"$count selected",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                TextButton(onClick=done,contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text("Done")} }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Inbox","Archive","Trash").filter{it!=folder}.forEach { target ->
                    FilledTonalButton(onClick={move(target)},enabled=count>0,contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)){Text(target)}
                }
                OutlinedButton(onClick={confirm=true},enabled=count>0){Text(if(blocked)"Unblock"else"Block")}
                unread?.let{TextButton(onClick=it,enabled=count>0){Text("Mark unread")}}
                delete?.let{TextButton(onClick=it,enabled=count>0){Text("Delete permanently",color=MaterialTheme.colorScheme.error)}}
            }
        }
    }
    if(confirm)ReferenceDialog(if(blocked)"Unblock selected senders?"else"Block selected senders?",{confirm=false},footer={
        DialogAction("Cancel"){confirm=false}
        DialogAction(if(blocked)"Unblock"else"Block",destructive=!blocked){confirm=false;block(!blocked)}
    }) { Text("Applies to the other participants in $count selected conversations, including their direct messages in OnKlav.",Modifier.padding(20.dp)) }
}
