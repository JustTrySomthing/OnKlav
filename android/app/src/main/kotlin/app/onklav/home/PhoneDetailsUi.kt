package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date

@Composable internal fun phoneCallColors()=IconButtonDefaults.filledIconButtonColors(
 containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
 disabledContainerColor=MaterialTheme.colorScheme.primaryContainer,
 disabledContentColor=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.65f))

@Composable internal fun PhoneQuickCall(label:String,enabled:Boolean=true,call:()->Unit){
 FilledIconButton(onClick=call,enabled=enabled,shape=CircleShape,colors=phoneCallColors(),modifier=Modifier.padding(start=4.dp).size(48.dp)){Icon(PhoneIcons.Call,label,Modifier.size(24.dp))}
}

@Composable private fun PhoneDetailDialog(label:String,tag:String,close:()->Unit,content:@Composable ColumnScope.()->Unit){
 Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.padding(16.dp).widthIn(max=420.dp).fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp.dp-72.dp).coerceAtLeast(180.dp)).testTag(tag),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,tonalElevation=6.dp){
   Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);IconButton(onClick=close,modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.Close,"Close $label")}}
    content()
   }
  }
 }
}
@Composable private fun PhoneDetailIdentity(title:String,number:String?=null){
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(48.dp)){Box(contentAlignment=Alignment.Center){Icon(Icons.Outlined.Person,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)}}
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
   if(!number.isNullOrBlank())SelectionContainer{Text(number,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  }
 }
}
@Composable private fun PhoneDetailAction(label:String,icon:ImageVector,danger:Boolean=false,click:()->Unit){
 val ink=if(danger)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
 Surface(onClick=click,color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(12.dp)){
  Row(Modifier.fillMaxWidth().heightIn(min=52.dp).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Icon(icon,null,tint=if(danger)ink else MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,color=ink)
  }
 }
}
@Composable private fun PhonePrimaryActions(call:()->Unit,message:()->Unit,enabled:Boolean=true,callLabel:String?=null){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  FilledTonalButton(onClick=message,enabled=enabled,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)){Text("Message")}
  Button(onClick=call,enabled=enabled,modifier=Modifier.weight(1f).then(
   if(callLabel==null)Modifier else Modifier.semantics{contentDescription=callLabel}
  ),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)){
   Icon(PhoneIcons.Call,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Call")
  }
 }
}
@Composable internal fun PhoneContactDetails(person:ContactRow,close:()->Unit,call:(String)->Unit,message:(String)->Unit,edit:()->Unit,delete:()->Unit,block:(String)->Unit){
 PhoneDetailDialog("Contact","phone-contact-details",close){
  val single=person.numbers.singleOrNull()
  PhoneDetailIdentity(person.name,single?.let(PhoneRules::display))
  when {
   person.numbers.isEmpty()->Text("No phone number",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   single!=null->PhonePrimaryActions({call(single)},{message(single)},PhoneRules.callable(PhoneRules.input(single)))
   else->person.numbers.forEachIndexed{i,n->
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){
     Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
      Text("Phone ${i+1}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
      SelectionContainer{Text(PhoneRules.display(n),style=MaterialTheme.typography.titleMedium)}
      PhonePrimaryActions({call(n)},{message(n)},PhoneRules.callable(PhoneRules.input(n)),"Call ${PhoneRules.display(n)}")
     }
    }
   }
  }
  Column{
   PhoneDetailAction("Edit contact",Icons.Outlined.Edit,click=edit)
   person.numbers.firstOrNull{PhoneRules.callable(PhoneRules.input(it))}?.let{n->PhoneDetailAction(if(person.numbers.size>1)"Block ${PhoneRules.display(n)}"else"Block number",PhoneIcons.End){block(n)}}
   HorizontalDivider(Modifier.padding(vertical=8.dp))
   PhoneDetailAction("Delete contact",Icons.Outlined.Delete,true,delete)
  }
 }
}
@Composable internal fun PhoneRecentDetails(event:PhoneEvent,close:()->Unit,call:()->Unit,message:()->Unit,save:()->Unit,block:()->Unit,delete:()->Unit){
 PhoneDetailDialog("Call details","phone-recent-details",close){
  val title=if(event.name.isBlank()&&event.callable)PhoneRules.display(event.number)else event.title
  PhoneDetailIdentity(title,if(event.name.isNotBlank()&&event.callable)PhoneRules.display(event.number)else null)
  Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){
   Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(PhoneRules.type(event.type),style=MaterialTheme.typography.titleSmall);Text(PhoneRules.duration(event.duration),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(event.date))+" · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.date)),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
  if(event.callable)PhonePrimaryActions(call,message)
  Column{
   if(event.callable){PhoneDetailAction("Save contact",Icons.Outlined.Person,click=save);PhoneDetailAction("Block number",PhoneIcons.End,click=block)}
   HorizontalDivider(Modifier.padding(vertical=8.dp))
   PhoneDetailAction("Delete from history",Icons.Outlined.Delete,true,delete)
  }
 }
}
