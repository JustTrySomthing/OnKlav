package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun FileRecoveryPanel(){
 val c=LocalContext.current;val scope=rememberCoroutineScope()
 var rows by remember{mutableStateOf(emptyList<JSONObject>())};var show by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
 LaunchedEffect(Unit){rows=withContext(Dispatchers.IO){FileSelectionStore.recovered(c)}}
 if(rows.isNotEmpty())OutlinedButton(onClick={show=true}){Text("Recovered saves · ${rows.size}")}
 if(show)AlertDialog(onDismissRequest={if(!busy)show=false},title={Text("Recovered saves")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("These files were kept after an interrupted save or filing error. Retry adds a new copy in the originally chosen folder.")
  rows.take(10).forEach{row->Text(row.getString("name"),style=MaterialTheme.typography.titleSmall);Text(row.optString("error","Interrupted save · the file may be incomplete"),style=MaterialTheme.typography.bodySmall);TextButton(enabled=!busy,onClick={scope.launch{busy=true;try{withContext(Dispatchers.IO){FileSelectionStore.completed(c,FileSelectionStore.uri(c,row),null,allowInterrupted=true)};rows=withContext(Dispatchers.IO){FileSelectionStore.recovered(c)}}finally{busy=false}}}){Text("Retry filing this copy")}}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 }},confirmButton={TextButton(enabled=!busy,onClick={show=false}){Text("Close")}})
}
