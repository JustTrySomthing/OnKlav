package app.onklav.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.*
import org.json.JSONObject

/** Activity-owned screen data survives tab composition; provider refreshes never blank it. */
internal class MessengerMemory:ViewModel(){
 private var watching=false
 var recovered=false
 val raw=mutableStateOf<List<Message>>(emptyList())
 val rawWindows=linkedMapOf<Long,List<Message>>()
 val inboxCursor=mutableStateOf<MediaCursor?>(null)
 val inboxMore=mutableStateOf(true)
 val providerEpoch=mutableIntStateOf(0)
 fun watch(context:android.content.Context){if(watching)return;watching=true;val app=context.applicationContext;viewModelScope.launch{MessageRepository(app).changes().collect{providerEpoch.intValue++}}}
 val snapshot=mutableStateOf(JSONObject())
 val rows=mutableStateOf<List<Conversation>>(emptyList())
 val messages=mutableStateOf<List<Message>>(emptyList())
 val windows=linkedMapOf<Long,List<Message>>()
 val galleries=linkedMapOf<Long,MediaPage>()
 val initializedThreads=mutableSetOf<Long>()
 val seenReactions=mutableMapOf<String,Map<String,ReactionMark>>()
 fun keep(thread:Long,value:List<Message>,source:List<Message> = value){if(thread<=0)return;if(windows.size>=12&&!windows.containsKey(thread)){val first=windows.keys.first();windows.remove(first);rawWindows.remove(first)};windows[thread]=value.takeLast(200);rawWindows[thread]=source.takeLast(200)}
}
internal fun mergeLatestMessages(previous:List<Message>,fresh:List<Message>):List<Message>{
 if(fresh.isEmpty())return emptyList()
 val from=fresh.minOf{it.date}
 return (previous.filter{it.date<from}+fresh).distinctBy{it.key}.sortedBy{it.date}
}
internal data class MediaCursor(val date:Long,val id:Long)
internal data class MediaPage(val messages:List<Message>,val next:MediaCursor?,val more:Boolean)
