package app.onklav.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.ui.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Saved-document browser: search and previews stay visible while browsing. */
@Composable internal fun WriteDocumentsDrawer(documents:List<WriteDocument>,current:String,busy:Boolean,query:String,onQuery:(String)->Unit,onClose:()->Unit,onNew:()->Unit,onImport:()->Unit,onOpen:(String)->Unit,onDelete:(WriteDocument)->Unit,onDuplicate:()->Unit,visible:Boolean=true){
 val transition=remember{MutableTransitionState(false)};transition.targetState=visible
 if(transition.currentState||transition.targetState){
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Box(Modifier.fillMaxSize().clickable(onClick=onClose),contentAlignment=Alignment.CenterEnd){
   AnimatedVisibility(visibleState=transition,enter=slideInHorizontally(tween(220)){it}+fadeIn(tween(180)),exit=slideOutHorizontally(tween(180)){it}+fadeOut(tween(160))){
   Surface(Modifier.fillMaxHeight().widthIn(max=400.dp).fillMaxWidth().clickable{},tonalElevation=8.dp){
    Column(Modifier.fillMaxSize().padding(16.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Text("Documents",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);IconButton(onClick=onClose){Icon(Icons.Outlined.Close,"Close documents")}}
     OutlinedTextField(query,onQuery,label={Text("Search saved documents")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())
     Row(Modifier.padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      FilledTonalButton(enabled=!busy,onClick=onNew){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("New")}
      OutlinedButton(enabled=!busy,onClick=onImport){Text("Import")}
      TextButton(enabled=!busy,onClick=onDuplicate){Text("Duplicate")}
     }
     val matches=documents.filter{it.title.contains(query,true)||it.text.contains(query,true)}
     Text("${matches.size} documents",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
     LazyColumn(Modifier.weight(1f)){
      if(matches.isEmpty())item{Text("No matching documents",Modifier.padding(vertical=24.dp))}
      items(matches,key={it.id}){entry->
       Surface(color=if(entry.id==current)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium,modifier=Modifier.padding(vertical=4.dp)){
        Row(Modifier.fillMaxWidth().clickable(enabled=!busy){onOpen(entry.id)}.padding(start=12.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){
         Column(Modifier.weight(1f)){
          Text(entry.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
          Text(entry.text.replace('\n',' ').ifBlank{"Empty document"},style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
          if(entry.id==current)Text("Current document",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
         }
         IconButton(enabled=!busy,onClick={onDelete(entry)}){Icon(Icons.Outlined.Delete,"Delete ${entry.title}")}
        }
       }
      }
     }
    }
   }
  }
 }
}
 }
}
