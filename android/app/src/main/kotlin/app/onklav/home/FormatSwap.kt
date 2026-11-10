package app.onklav.home

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import app.onklav.shared.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavFileContracts
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

internal class FormatSwapModel(app:Application):AndroidViewModel(app) {
    var kind by mutableStateOf<SwapKind?>(null)
    var input by mutableStateOf<SwapInput?>(null);private set
    var target by mutableStateOf(SwapFormat.PNG)
    var options by mutableStateOf(SwapOptions())
    var busy by mutableStateOf(false);private set
    var progress by mutableFloatStateOf(-1f);private set
    var status by mutableStateOf("");private set
    var error by mutableStateOf(false);private set
    var saved by mutableStateOf<String?>(null);private set
    private val root=File(app.cacheDir,"format-swap-${UUID.randomUUID()}").apply{mkdirs()}
    private var task:Job?=null
    private var result:File?=null
    val ready get()=result!=null
    val outputName get()=input?.name?.substringBeforeLast('.')?.take(100).orEmpty().ifBlank{"Converted"}+(if((input?.pages?:0)>0)"-page-${options.page}" else "")+"-converted.${target.ext}"
    fun choose(value:SwapKind){if(busy)return;clear();kind=value}
    private fun clear(){result?.delete();result=null;input?.file?.delete();input=null;saved=null;status="";error=false;options=SwapOptions()}
    fun reset(){task?.cancel();task=null;clear();kind=null}
    fun cancel(){task?.cancel()}
    fun dismissResult(){if(!busy){result?.delete();result=null;status=""}}
    private fun run(label:String,block:suspend ()->Unit){if(busy)return;busy=true;error=false;status=label;progress=-1f;saved=null
        task=viewModelScope.launch{try{block()}catch(e:CancellationException){status="Cancelled · original file unchanged";throw e}catch(e:Exception){error=true;status=e.message?.take(240)?:"Conversion failed. Try another file."}finally{busy=false;progress=-1f;task=null}}
    }
    fun open(uri:Uri){val selected=kind?:return
        run("Opening file"){
            val file=File(root,"source-${UUID.randomUUID()}");var accepted=false
            try{val prepared=withContext(Dispatchers.IO){val c=getApplication<Application>();var name="File"
                c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{q->if(q.moveToFirst()){val ni=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(ni>=0)name=q.getString(ni)?:name;val si=q.getColumnIndex(OpenableColumns.SIZE);if(si>=0&&!q.isNull(si))require(q.getLong(si)<=if(selected==SwapKind.DOCUMENT)16_000_000 else FormatSwapEngine.MAX_SOURCE){"Choose a file under 1 GB."}}}
                requireNotNull(c.contentResolver.openInputStream(uri)){"File could not be opened."}.use{source->file.outputStream().use{out->val bytes=ByteArray(131072);var total=0L;while(true){currentCoroutineContext().ensureActive();val n=source.read(bytes);if(n<0)break;total+=n;require(total<=FormatSwapEngine.MAX_SOURCE){"Choose a file under 1 GB."};require(root.usableSpace>n+32_000_000L){"Not enough free storage."};out.write(bytes,0,n)}}}
                require(file.length()>0){"This file is empty."};FormatSwapEngine.prepare(file,name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}]")," ").take(180),selected)
            };clear();input=prepared;target=prepared.targets.first();accepted=true;status="Ready to convert"
            }finally{if(!accepted)file.delete()}
        }
    }
    fun convert(){val source=input?:return;val format=target;val settings=options
        run("Converting") {val out=File(root,"result-${UUID.randomUUID()}.${format.ext}");var complete=false
            try{require(root.usableSpace>64_000_000){"Free at least 64 MB before converting."};var lastProgress=0L;FormatSwapEngine.convert(getApplication(),source,format,settings,out){p->val now=System.nanoTime();if(now-lastProgress>100_000_000){lastProgress=now;viewModelScope.launch{progress=p.coerceIn(0f,1f)}}};result?.delete();result=out;complete=true;status="Conversion complete · choose where to save"}finally{if(!complete)out.delete()}
        }
    }
    fun save(uri:Uri){val file=result?:return;val name=outputName
        run("Saving copy") {writeFiledExport(getApplication(),uri){out->file.inputStream().use{source->val buf=ByteArray(131072);while(true){currentCoroutineContext().ensureActive();val n=source.read(buf);if(n<0)break;out.write(buf,0,n)}}};saved=name;status="Saved to your chosen folder"}
    }
    override fun onCleared(){task?.cancel();root.deleteRecursively();super.onCleared()}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun FormatSwapWorkspace(exit:()->Unit,model:FormatSwapModel=viewModel()) {
    var confirmLeave by remember{mutableStateOf(false)}
    val back:()->Unit={when{model.busy->confirmLeave=true;model.kind!=null->{model.reset()};else->exit()}}
    BackHandler(onBack=back)
    val open=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null)model.open(uri)}
    val save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument(model.target.mime)){uri->if(uri!=null)model.save(uri)}
    fun pick(){open.launch(when(model.kind){SwapKind.IMAGE->arrayOf("image/*");SwapKind.AUDIO->arrayOf("audio/*");SwapKind.VIDEO->arrayOf("video/*");else->arrayOf("application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.oasis.opendocument.text","text/*")})}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        HomeHeader("Format Swap",back=back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(model.kind==null){
                Text("What kind of file do you want to convert",style=MaterialTheme.typography.headlineMedium)
                Text("A new format. A separate copy.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                // Two generous rows that remain readable with larger accessibility text.
                SwapKind.entries.chunked(if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.2f)1 else 2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    row.forEach{kind->FilledTonalButton(onClick={model.choose(kind);pick()},modifier=Modifier.weight(1f).heightIn(min=160.dp),shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(16.dp)){
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Icon(painterResource(when(kind){SwapKind.AUDIO->R.drawable.ref_app_audio_editor;SwapKind.VIDEO->R.drawable.ref_app_video_editor;SwapKind.IMAGE->R.drawable.ref_app_photo;SwapKind.DOCUMENT->R.drawable.ref_app_write}),null,Modifier.size(28.dp))
                            Text(kind.title,style=MaterialTheme.typography.titleLarge)
                            Text(kind.inputs,style=MaterialTheme.typography.bodySmall)
                        }
                    }}
                }}
                Text("Works on your device · no upload",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text("Supported inputs depend on your device. Legacy DOC, AVI and WMV are not supported. HEIC and GIF can be opened as still images.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }else{
                val input=model.input
                val step=when{model.saved!=null->3;model.ready->3;input!=null->2;else->1}
                Text("${model.kind!!.title} conversion",style=MaterialTheme.typography.headlineSmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("File","Format","Save").forEachIndexed{i,label->Surface(modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),color=if(i+1<=step)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer){Text("${if(i+1<step||model.saved!=null)"✓" else "${i+1}"}  $label",Modifier.padding(12.dp),style=MaterialTheme.typography.labelLarge)}}
                }
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(input?.name?:"Choose a ${model.kind!!.title.lowercase()} file",style=MaterialTheme.typography.titleMedium)
                    input?.let{Text("${it.detail} · ${android.text.format.Formatter.formatShortFileSize(androidx.compose.ui.platform.LocalContext.current,it.file.length())}",style=MaterialTheme.typography.bodySmall)}
                    OutlinedButton(onClick={pick()},enabled=!model.busy){Text(if(input==null)"Choose file" else "Change file")}
                }}
                if(input!=null){
                    if(!model.ready&&model.saved==null){
                        Text("Convert to",style=MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){input.targets.forEach{format->FilterChip(selected=model.target==format,onClick={model.target=format},enabled=!model.busy,label={Text(format.label)})}}
                        if(input.kind==SwapKind.IMAGE){
                            Text("Image size",style=MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0 to "Original",2560 to "2560 px",1280 to "1280 px").forEach{(edge,label)->FilterChip(selected=model.options.maxEdge==edge,onClick={model.options=model.options.copy(maxEdge=edge)},enabled=!model.busy,label={Text(label)})}}
                        }
                        if((input.kind==SwapKind.IMAGE||input.pages>0)&&model.target in setOf(SwapFormat.JPG,SwapFormat.WEBP)){
                            Text("Quality · ${model.options.quality}%",style=MaterialTheme.typography.labelLarge)
                            Slider(value=model.options.quality.toFloat(),onValueChange={model.options=model.options.copy(quality=it.toInt())},valueRange=50f..100f,steps=9,enabled=!model.busy)
                            if(model.target==SwapFormat.JPG)Text("Transparent areas become white.",style=MaterialTheme.typography.bodySmall)
                        }
                        if(model.target==SwapFormat.MP3){
                            Text("Audio quality",style=MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(128,192,320).forEach{rate->FilterChip(selected=model.options.kbps==rate,onClick={model.options=model.options.copy(kbps=rate)},enabled=!model.busy,label={Text("$rate kbps")})}}
                        }
                        if(input.pages>0){
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                OutlinedButton(onClick={model.options=model.options.copy(page=model.options.page-1)},enabled=!model.busy&&model.options.page>1){Text("Previous")}
                                Text("Page ${model.options.page} of ${input.pages}",Modifier.weight(1f))
                                OutlinedButton(onClick={model.options=model.options.copy(page=model.options.page+1)},enabled=!model.busy&&model.options.page<input.pages){Text("Next")}
                            }
                        }
                        Text(input.warning,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(model.target in setOf(SwapFormat.TXT,SwapFormat.RTF))Text("Text only · formatting and images are removed.",style=MaterialTheme.typography.bodySmall)
                        Button(onClick={model.convert()},enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Convert to ${model.target.label}")}
                    }else if(model.saved==null){
                        Text("Your converted file is ready",style=MaterialTheme.typography.titleLarge)
                        Text(model.outputName)
                        Button(onClick={save.launch(model.outputName)},enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Save converted file")}
                        TextButton(onClick={model.dismissResult()},enabled=!model.busy){Text("Change format or options")}
                    }else{
                        Text("✓  File saved",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                        Text(model.saved!!)
                        Button(onClick={model.reset()},modifier=Modifier.fillMaxWidth()){Text("Convert another file")}
                        OutlinedButton(onClick={model.reset();exit()},modifier=Modifier.fillMaxWidth()){Text("Done")}
                    }
                }
                if(model.busy){if(model.progress<0)LinearProgressIndicator(Modifier.fillMaxWidth())else LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth());Text(model.status+(if(model.progress>=0)" · ${(model.progress*100).toInt()}%" else ""));TextButton(onClick={model.cancel()}){Text("Cancel")}}
                else if(model.error)Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)){Text(model.status,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}
                else if(model.status.isNotBlank()&&model.saved==null)Text(model.status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
    if(confirmLeave)AlertDialog(onDismissRequest={confirmLeave=false},title={Text("Cancel this operation?")},text={Text("Your original file is unchanged.")},confirmButton={TextButton(onClick={confirmLeave=false;model.cancel()}){Text("Cancel operation")}},dismissButton={TextButton(onClick={confirmLeave=false}){Text("Keep going")}})
}
