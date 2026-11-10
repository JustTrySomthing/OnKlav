package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable internal fun MailRecipientField(label:String,value:String,m:MailModel,enabled:Boolean,modifier:Modifier=Modifier,change:(String)->Unit){
 val input=value;val headers=m.headers;val drafts=m.drafts;val account=m.draft?.account.orEmpty()
 val suggestions by produceState<List<MailRecipient>>(emptyList(),input,headers,drafts,account){this.value=emptyList();delay(160);this.value=withContext(Dispatchers.Default){mailRecipientSuggestions(input,account,headers,drafts)}}
 Column(modifier){
  OutlinedTextField(value=value,onValueChange={change(it.take(16000))},label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
  if(enabled&&suggestions.isNotEmpty())Surface(Modifier.fillMaxWidth().padding(top=4.dp),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){Column{
   Text("Recent correspondents",Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   suggestions.take(3).forEach{person->Row(Modifier.fillMaxWidth().clickable{change(mailInsertRecipient(value,person))}.heightIn(min=48.dp).padding(horizontal=12.dp,vertical=6.dp).semantics{role=Role.Button;contentDescription="Use ${person.email}"},verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Person,null,Modifier.size(20.dp));Column(Modifier.weight(1f).padding(start=10.dp)){if(person.name.isNotBlank())Text(person.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge);Text(person.email,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}}
  }}
 }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun MailConversationRow(rows:List<MailHeader>,m:MailModel){
 val h=rows.first();val colors=MaterialTheme.colorScheme;val keys=rows.map{it.key}.toSet();val selected=keys.all{it in m.selected};val unread=rows.any{!it.seen}
 fun toggle(){m.selected=if(selected)m.selected-keys else m.selected+keys}
 Row(Modifier.fillMaxWidth().background(if(selected)colors.secondaryContainer else colors.surfaceContainerLow,RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).combinedClickable(enabled=!m.busy,onClick={if(m.selected.isNotEmpty())toggle()else m.open(h)},onLongClick={m.selected=m.selected+keys}).padding(start=12.dp,top=12.dp,bottom=12.dp,end=2.dp).semantics{stateDescription="${rows.size} message${if(rows.size==1)""else"s"}${if(unread)", unread"else""}"},verticalAlignment=Alignment.CenterVertically){
  if(m.selected.isNotEmpty())Checkbox(selected,{toggle()},enabled=!m.busy)else Box(Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),contentAlignment=Alignment.Center){Text(h.from.trim().firstOrNull()?.uppercase()?:"?",color=colors.onSecondaryContainer,style=MaterialTheme.typography.titleMedium)}
  Column(Modifier.weight(1f).padding(start=10.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text(h.from.substringBefore('<').trim().trim('"').ifBlank{h.from},Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall,fontWeight=if(unread)FontWeight.Bold else FontWeight.Normal);Text(mailShortDate(h.date),style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)}
   Text(h.subject.ifBlank{"(No subject)"},maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,fontWeight=if(unread)FontWeight.SemiBold else FontWeight.Normal)
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){if(unread)Box(Modifier.size(6.dp).background(colors.primary,CircleShape));if(rows.size>1)Text("${rows.size} messages",style=MaterialTheme.typography.labelSmall,color=colors.primary);if(rows.any{it.attachment})Icon(Icons.Outlined.AttachFile,"Has attachments",Modifier.size(14.dp));if(m.account.isEmpty())Text(m.accounts.firstOrNull{it.id==h.account}?.email.orEmpty(),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)}
  }
  IconButton(onClick={m.star(h)},enabled=!m.busy){MailStar(h.starred)}
 }
}
@Composable internal fun MailConversationMembers(m:MailModel,current:MailHeader){
 val members=remember(m.headers,m.onlineResults,current.key){mailConversations((m.headers+m.onlineResults.orEmpty()+listOf(current)).distinctBy{it.key}).firstOrNull{row->row.any{it.key==current.key}}.orEmpty().sortedBy{it.date}}
 var choose by remember{mutableStateOf(false)}
 if(m.threaded&&members.size>1){val index=members.indexOfFirst{it.key==current.key}
  Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text("Conversation · ${members.size} downloaded messages",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    IconButton(onClick={m.open(members[index-1])},enabled=!m.busy&&index>0){Icon(Icons.Outlined.ChevronLeft,"Previous message in conversation")}
    TextButton(onClick={choose=true},enabled=!m.busy,modifier=Modifier.weight(1f)){Text("${index+1} of ${members.size} · Messages",style=MaterialTheme.typography.labelLarge)}
    IconButton(onClick={m.open(members[index+1])},enabled=!m.busy&&index<members.lastIndex){Icon(Icons.Outlined.ChevronRight,"Next message in conversation")}
   }}
  }
  if(choose)AlertDialog(onDismissRequest={choose=false},title={Text("Conversation")},text={LazyColumn(Modifier.heightIn(max=360.dp)){items(members,key={it.key}){h->ListItem(headlineContent={Text(h.from.substringBefore('<').trim(),maxLines=1,overflow=TextOverflow.Ellipsis)},supportingContent={Text("${mailDate(h.date)} · ${h.folder}",style=MaterialTheme.typography.labelSmall)},trailingContent={if(h.key==current.key)Icon(Icons.Outlined.Check,"Current message")},modifier=Modifier.clickable(enabled=!m.busy){choose=false;if(h.key!=current.key)m.open(h)})}}},confirmButton={TextButton(onClick={choose=false}){Text("Done")}})
 }
}
