package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable internal fun SdkPodcastTransfer(model:SdkAppModel,episode:String,downloaded:Boolean){
 val transfers by SdkPodcasts.transfers.collectAsState();val transfer=transfers[SdkPodcasts.transferKey(model.appIdentity,episode)]
 val active=transfer?.state in setOf("connecting","downloading");val scope=rememberCoroutineScope()
 if(transfer==null&&!downloaded)return
 val text=when{downloaded&&transfer?.state !in setOf("connecting","downloading","error","cancelled")->"Download complete";transfer?.state=="downloading"->{val bytes=transfer.bytes;val total=transfer.total;val size=String.format(Locale.getDefault(),"%.1f MB",bytes/1048576.0);if(total>0)"Downloading ${(bytes*100/total).coerceIn(0,99)}% · $size / "+String.format(Locale.getDefault(),"%.1f MB",total/1048576.0)else "Downloading · $size"};else->transfer?.message.orEmpty()}
 Column(Modifier.fillMaxWidth().padding(start=4.dp,end=4.dp,bottom=10.dp).testTag("podcast-transfer-$episode")){
  if(active){if(transfer!!.total>0)LinearProgressIndicator(progress={(transfer.bytes.toDouble()/transfer.total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().semantics{contentDescription="Episode download progress"})else LinearProgressIndicator(Modifier.fillMaxWidth().semantics{contentDescription="Connecting or downloading; total size unavailable"})}
  Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(text,Modifier.weight(1f).padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=if(transfer?.state=="error")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
   if(active)TextButton(onClick={val p=model.pending;if(p?.optString("service")=="podcasts.download"&&p.optJSONObject("args")?.optString("episode")==episode){SdkPodcasts.cancelDownload(model.appIdentity,episode,p.getString("token"));scope.launch{model.reply(p,failure="Download cancelled")}}}){Text("Cancel")}
  }
 }
}
