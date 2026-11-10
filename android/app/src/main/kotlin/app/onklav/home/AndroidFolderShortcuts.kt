package app.onklav.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable internal fun AndroidFilesPermissionPanel(refresh:()->Unit){
 val c=LocalContext.current;var allowed by remember{mutableStateOf(AndroidFilesAccess.allowed(c))}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){allowed=AndroidFilesAccess.allowed(c);if(allowed)refresh()}
 if(!allowed)Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)){
  Column(Modifier.padding(16.dp)){Text("Android files access",style=MaterialTheme.typography.titleSmall);Text("Enable access once to browse your phone’s photos, media, documents and downloads here.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={runCatching{launcher.launch(AndroidFilesAccess.request(c))}}){Text("Allow Android files")}}
 }
}
