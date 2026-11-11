package app.onklav.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.onklav.shared.DialogAction
import app.onklav.shared.ReferenceDialog
import kotlinx.coroutines.*

internal data class VCardPreview(val name:String,val details:List<Pair<String,String>>)

internal fun vCardPreview(text:String):List<VCardPreview> {
    val cards=mutableListOf<VCardPreview>()
    var fields:MutableList<Pair<String,String>>?=null
    for(line in text.trimStart('\uFEFF').replace(Regex("\\r?\\n[ \\t]"),"").lineSequence()) {
        val property=line.substringBefore(':').substringBefore(';').substringAfterLast('.').uppercase()
        val value=line.substringAfter(':',"").trim()
        when {
            property=="BEGIN" && value.equals("VCARD",true) -> fields=mutableListOf()
            property=="END" && value.equals("VCARD",true) -> {
                val entries=fields.orEmpty()
                val name=entries.firstOrNull{it.first=="FN"}?.second
                    ?: entries.firstOrNull{it.first=="N"}?.second?.split(';')?.let {
                        listOf(3,1,2,0,4).mapNotNull(it::getOrNull).filter(String::isNotBlank).joinToString(" ")
                    }
                val details=entries.mapNotNull { (key,entry) ->
                    val label=when(key) {
                        "TEL" -> "Phone"
                        "EMAIL" -> "Email"
                        "ORG" -> "Organization"
                        "ADR" -> "Address"
                        else -> return@mapNotNull null
                    }
                    label to if(key=="ADR"||key=="ORG")entry.split(';').filter(String::isNotBlank).joinToString(", ")else entry
                }
                if(fields!=null)cards+=VCardPreview(name.orEmpty().ifBlank{"Contact"},details.distinct())
                fields=null
                if(cards.size>=30)break
            }
            property in setOf("FN","N","TEL","EMAIL","ORG","ADR") && value.isNotBlank() ->
                fields?.takeIf{it.size<40}?.add(property to value.take(500))
        }
    }
    return cards
}

@Composable
internal fun VCardAttachmentDialog(part:MessagePart,close:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val name=MessageMedia.vCardName(part.name)
    var feedback by remember(part) {mutableStateOf<String?>(null)}
    var saving by remember(part) {mutableStateOf(false)}
    val preview by produceState<Result<List<VCardPreview>>?>(null,part) {
        value=withContext(Dispatchers.IO) {
            runCatching {
                val bytes=MessageMedia.input(context,part)?.use{MessageRepository.readBounded(it,1024*1024)}
                    ?: error("Could not read the contact card.")
                val text=if(bytes.size>=2 && ((bytes[0].toInt()==-1 && bytes[1].toInt()==-2)||(bytes[0].toInt()==-2 && bytes[1].toInt()==-1)))
                    bytes.toString(Charsets.UTF_16) else bytes.toString(Charsets.UTF_8)
                vCardPreview(text)
            }
        }
    }
    val save=rememberLauncherForActivityResult(FileExportContract()) { destination ->
        if(destination!=null)scope.launch {
            saving=true
            feedback=null
            try {
                writeFiledExport(context,destination) { output ->
                    val job=currentCoroutineContext()
                    MessageMedia.input(context,part)?.use { input ->
                        FilesBroker.copyBoundedLimit(input,output,1024*1024L) {job.ensureActive()}
                    } ?: error("Could not read the contact card.")
                }
                feedback="Saved to Files"
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {feedback=e.message?:"Could not save the contact card."}
            finally {saving=false}
        }
    }
    ReferenceDialog("Contact card",close,footer={
        DialogAction("Close",onClick=close)
        Button(onClick={save.launch(FileExportRequest(name,MessageMedia.VCARD_MIME))},enabled=!saving) {
            Text("Save to Files")
        }
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(name,style=MaterialTheme.typography.bodySmall)
            val cards=preview?.getOrNull().orEmpty()
            when {
                preview==null -> Text("Opening contact card…")
                cards.isEmpty() -> Text("Preview unavailable. You can still save the file.")
                else -> cards.forEachIndexed { index,card ->
                    if(index>0)HorizontalDivider()
                    Text(card.name,style=MaterialTheme.typography.titleMedium)
                    card.details.forEach { (label,value) -> Text("$label · $value") }
                }
            }
            TextButton(onClick={
                scope.launch {
                    try {withContext(Dispatchers.IO){MessageMedia.open(context,part.copy(mime=MessageMedia.VCARD_MIME,name=name))}}
                    catch(e:CancellationException) {throw e}
                    catch(_:Exception) {feedback="Contacts couldn’t open this file. You can save it to Files."}
                }
            }) {Text("Open in Contacts")}
            feedback?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
