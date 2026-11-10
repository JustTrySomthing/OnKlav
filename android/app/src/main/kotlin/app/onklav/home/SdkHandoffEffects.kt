package app.onklav.home

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.onklav.shared.ReferenceDialog
import app.onklav.shared.DialogAction
import kotlinx.coroutines.*
import org.json.JSONObject

internal fun handoffService(service:String)=service in setOf("handoff.handlers","handoff.file","handoff.text")
@Composable internal fun SdkHandoffEffects(pending:JSONObject?,model:SdkAppModel){
 val context=LocalContext.current;var review by remember{mutableStateOf<JSONObject?>(null)};val decision=remember(pending?.optString("token")){CompletableDeferred<Boolean>()}
 LaunchedEffect(pending?.optString("token")){
  val p=pending?.takeIf{handoffService(it.optString("service"))}?:return@LaunchedEffect
  if(!model.launchPending(p.getString("token")))return@LaunchedEffect
  var staged:android.net.Uri?=null;var launched=false
  try{
   val operation=model.authorizeNow(p);val a=operation.getJSONObject("args");val service=operation.getString("service")
   fun authorize(){model.authorizeNow(p)}
   val result=if(service=="handoff.handlers")SdkHandoffs.discover(context,a,::authorize)else{
    val file=if(service=="handoff.file")withContext(Dispatchers.IO){model.handoffFileNow(p)}else null
    review=JSONObject().put("operation",a.optString("operation","share")).put("name",file?.getString("name")?:"Text").put("mime",file?.getString("mime")?:"text/plain").put("size",file?.getLong("size")?:0).put("text",if(service=="handoff.text")a.getString("text").take(2000)else "")
    check(decision.await()){"Handoff cancelled"};review=null;authorize()
    if(file!=null){
     val uri=withContext(Dispatchers.IO){SdkHandoffProvider.stage(context,operation.getJSONObject("owner"),file,::authorize).also{staged=it}}
     SdkHandoffs.launch(context,a.getString("operation"),file.getString("mime"),uri,authorize=::authorize)
    }else SdkHandoffs.launch(context,"share","text/plain",text=a.getString("text"),authorize=::authorize)
   }
   launched=true;model.reply(p,result)
  }catch(e:CancellationException){withContext(NonCancellable){runCatching{model.reply(p,failure="Handoff cancelled")}};throw e}
  catch(e:Exception){model.reply(p,failure=if(e is java.io.IOException)"The temporary copy could not be prepared. Check available storage and try again."else e.message?:"Handoff unavailable")}
  finally{review=null;if(!launched)staged?.let{SdkHandoffProvider.discard(context,it)}}
 }
 review?.let{r->ReferenceDialog(if(r.getString("operation")=="view")"Open in another app?"else"Share with another app?",{decision.complete(false)},footer={DialogAction("Cancel"){decision.complete(false)};DialogAction("Choose app",primary=true){decision.complete(true)}}){Text("${r.getString("name")} · ${r.getString("mime")}\nThe app you choose receives a read-only copy. You can cancel in Android's chooser.\n${r.optString("text")}",Modifier.padding(20.dp))}}
}
