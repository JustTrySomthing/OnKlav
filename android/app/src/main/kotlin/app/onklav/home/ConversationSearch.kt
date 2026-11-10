package app.onklav.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import kotlinx.coroutines.*

internal data class ConversationSearchHit(val key:String,val date:Long,val text:String)
internal data class ConversationSearchResult(val hits:List<ConversationSearchHit>,val limited:Boolean=false)

@Composable internal fun ConversationSearchDialog(repo:MessageRepository,thread:Long,close:()->Unit,choose:(ConversationSearchHit)->Unit){
 var query by remember{mutableStateOf("")};var result by remember{mutableStateOf(ConversationSearchResult(emptyList()))};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 val focus=remember{FocusRequester()}
 LaunchedEffect(Unit){focus.requestFocus()}
 LaunchedEffect(query,thread){
  result=ConversationSearchResult(emptyList());error=null
  if(query.isBlank()){busy=false;return@LaunchedEffect}
  busy=true
  try{delay(250);result=repo.findConversationMessages(thread,query).let{found->found.copy(hits=found.hits.mapNotNull{hit->
   if(hit.text.startsWith("/\"0\"1:")){val body=hit.text.substringAfter('\n',"");hit.copy(text=body).takeIf{body.isNotBlank()&&body.contains(query.trim(),true)}}else hit
  })}}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Search could not finish"}finally{busy=false}
 }
 ReferenceDialog("Search Conversation",close,footer={DialogAction("Close"){close()}}){
  OutlinedTextField(query,{query=it.take(200)},Modifier.fillMaxWidth().focusRequester(focus).testTag("conversation-search-query"),singleLine=true,placeholder={Text("Search messages")},trailingIcon={if(query.isNotEmpty())TextButton(onClick={query=""}){Text("Clear")}})
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
  error?.let{Text(it,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)}
  if(query.isNotBlank()&&!busy&&error==null&&result.hits.isEmpty())Text("No matching messages",Modifier.padding(16.dp))
  if(result.limited)Text("Search limit reached. Narrow your search to find more.",Modifier.padding(8.dp),fontSize=12.sp)
  LazyColumn(Modifier.fillMaxWidth().heightIn(max=340.dp)){
   items(result.hits,key={it.key}){hit->Column(Modifier.fillMaxWidth().clickable{choose(hit)}.padding(vertical=12.dp).testTag("search-result-${hit.key}")){
    Text(conversationTimestamp(hit.date),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
    Text(hit.text,maxLines=3,overflow=TextOverflow.Ellipsis)
   }}
  }
 }
}
