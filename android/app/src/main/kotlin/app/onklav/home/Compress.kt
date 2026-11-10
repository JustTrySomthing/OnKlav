package app.onklav.home

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavFileContracts
import app.onklav.shared.R
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CompressWorkspace(exit:()->Unit,model:CompressModel=viewModel()) {
    val c=LocalContext.current
    fun size(bytes:Long)=Formatter.formatShortFileSize(c,bytes)
    var confirmCancel by remember{mutableStateOf(false)}
    var shareError by remember{mutableStateOf<String?>(null)}
    val back:()->Unit={if(model.busy)confirmCancel=true else if(model.kind!=null)model.reset()else exit()}
    BackHandler(onBack=back)
    val open=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null)model.open(listOf(uri))}
    val files=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){uris->model.open(uris)}
    val folder=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocumentTree()){uri->if(uri!=null)model.open(listOf(uri),folder=true)}
    val save=rememberLauncherForActivityResult(FileExportContract()){uri->if(uri!=null)model.save(uri)}
    fun pick(){when(model.kind){CompressKind.IMAGE->open.launch(arrayOf("image/*"));CompressKind.VIDEO->open.launch(arrayOf("video/*"));CompressKind.AUDIO->open.launch(arrayOf("audio/*"));else->files.launch(arrayOf("*/*"))}}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground){
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
            HomeHeader("Compress",back=back)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                val kind=model.kind;val source=model.source;val result=model.result
                if(kind==null){
                    Text("What do you want to compress?",style=MaterialTheme.typography.headlineMedium)
                    Text("Smaller copies. Easier to send.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    CompressKind.entries.chunked(if(LocalDensity.current.fontScale>1.2f)1 else 2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        row.forEach{category->FilledTonalButton(onClick={model.choose(category);if(category!=CompressKind.FILES)pick()},modifier=Modifier.weight(1f).heightIn(min=160.dp),shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(16.dp)){
                            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                                Icon(painterResource(when(category){CompressKind.IMAGE->R.drawable.ref_app_photo;CompressKind.VIDEO->R.drawable.ref_app_video_editor;CompressKind.AUDIO->R.drawable.ref_app_audio_editor;CompressKind.FILES->R.drawable.ref_app_files}),null,Modifier.size(28.dp))
                                Text(category.title,style=MaterialTheme.typography.titleLarge)
                                Text(category.hint,style=MaterialTheme.typography.bodySmall)
                            }
                        }}
                    }}
                    Text("On your device · originals stay unchanged",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text("Send the saved copy through Messages, email or another app. Each service has its own size limit.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }else{
                    Text(if(kind==CompressKind.FILES)"Make a ZIP" else "Compress ${kind.title.lowercase()}",style=MaterialTheme.typography.headlineSmall)
                    val step=if(result!=null)3 else if(source!=null)2 else 1
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        listOf("Choose","Size","Save").forEachIndexed{i,label->Surface(Modifier.weight(1f),shape=RoundedCornerShape(14.dp),color=if(i+1<=step)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer){Text("${if(i+1<step||model.saved!=null)"✓"else"${i+1}"}  $label",Modifier.padding(12.dp),style=MaterialTheme.typography.labelLarge)}}
                    }
                    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(source?.name?:if(kind==CompressKind.FILES)"Choose files or a folder"else"Choose a ${kind.title.lowercase()} file",style=MaterialTheme.typography.titleMedium)
                        source?.let{Text("Original · ${size(it.bytes)}"+(if(kind==CompressKind.FILES)" · ${it.entries.count{e->e.file!=null}} files"else if(it.width>0)" · ${it.width} × ${it.height}"else""),style=MaterialTheme.typography.bodySmall)}
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedButton(onClick={pick()},enabled=!model.busy){Text(if(kind==CompressKind.FILES)"Choose files"else if(source==null)"Choose file"else"Change file")}
                            if(kind==CompressKind.FILES)OutlinedButton(onClick={folder.launch(null)},enabled=!model.busy){Text("Choose folder")}
                        }
                    }}
                    if(source!=null){
                        if(result==null){
                            if(kind==CompressKind.IMAGE){
                                Text("Image size",style=MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(800 to "Small",1600 to "Medium",2560 to "Large").forEach{(edge,label)->FilterChip(selected=model.options.imageEdge==edge,onClick={model.update(model.options.copy(imageEdge=edge))},enabled=!model.busy,label={Text(label)})}}
                                Text("Up to ${model.options.imageEdge} px · longest edge · never enlarged",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(false to "JPG · most compatible",true to "WebP · keeps transparency").forEach{(webp,label)->FilterChip(selected=model.options.webp==webp,onClick={model.update(model.options.copy(webp=webp))},enabled=!model.busy,label={Text(label)})}}
                                if(!model.options.webp)Text("Transparent areas become white.",style=MaterialTheme.typography.bodySmall)
                            }
                            if(kind==CompressKind.VIDEO){
                                Text("Video size",style=MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(480,720,1080).forEach{edge->FilterChip(selected=model.options.videoEdge==edge,onClick={model.update(model.options.copy(videoEdge=edge))},enabled=!model.busy,label={Text("${edge}p")})}}
                                val dims=CompressEngine.dimensions(source,model.options.videoEdge)
                                Text("${dims.first} × ${dims.second} · MP4 · up to 30 fps",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(kind!=CompressKind.FILES){
                                Text("Quality · ${listOf("Smaller","Balanced","Clearer")[model.options.quality]}",style=MaterialTheme.typography.titleMedium)
                                Slider(value=model.options.quality.toFloat(),onValueChange={model.update(model.options.copy(quality=it.roundToInt()))},valueRange=0f..2f,steps=1,enabled=!model.busy,modifier=Modifier.semantics{contentDescription="Compression quality"})
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Smaller file",style=MaterialTheme.typography.bodySmall);Text("More detail",style=MaterialTheme.typography.bodySmall)}
                                if(kind==CompressKind.AUDIO)Text("MP3 · ${model.options.audioKbps} kbps",style=MaterialTheme.typography.bodySmall)
                            }
                            if(source.note.isNotBlank())Text(source.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(kind!=CompressKind.FILES)Text("Lower quality loses detail. Check the copy before sending.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }else if(model.saved==null){
                            val difference=source.bytes-result.length()
                            Text(if(difference>0)"${(difference*100/source.bytes.coerceAtLeast(1))}% smaller"else if(difference==0L)"Same size as the original"else"This copy is larger",style=MaterialTheme.typography.headlineSmall,color=if(difference>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text("${size(source.bytes)} → ${size(result.length())}",style=MaterialTheme.typography.titleLarge)
                            if(difference<0)Text(if(kind==CompressKind.FILES)"These files are already compressed. The ZIP still bundles them together."else"The original is already compact. Try lower quality or a smaller size.",style=MaterialTheme.typography.bodyMedium)
                            Text(model.outputName,style=MaterialTheme.typography.bodySmall)
                            Button(onClick={save.launch(FileExportRequest(model.outputName,model.mime))},enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(difference<0)"Save copy anyway"else"Save compressed copy")}
                            TextButton(onClick={model.adjust()},enabled=!model.busy){Text("Adjust settings")}
                        }else{
                            Text("✓  Copy saved",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                            Text(model.saved!!)
                            Text("${size(result.length())} · ready to use in Messages, email or another app",style=MaterialTheme.typography.bodyMedium)
                            model.savedUri?.let{uri->FilledTonalButton(onClick={try{c.startActivity(android.content.Intent.createChooser(SdkHandoffs.intent("share",model.mime,uri),"Share compressed copy"));shareError=null}catch(e:Exception){shareError="No compatible app could open sharing. The copy is saved in Files."}},modifier=Modifier.fillMaxWidth()){Text("Share saved copy")}}
                            shareError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                            Button(onClick={shareError=null;model.reset()},modifier=Modifier.fillMaxWidth()){Text("Compress something else")}
                            OutlinedButton(onClick={model.reset();exit()},modifier=Modifier.fillMaxWidth()){Text("Done")}
                        }
                    }
                    if(model.busy&&!(source!=null&&result==null)){if(model.progress<0)LinearProgressIndicator(Modifier.fillMaxWidth())else LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth());Text(model.status+(if(model.progress>=0)" · ${(model.progress*100).toInt()}%"else""));TextButton(onClick={model.cancel()}){Text("Cancel")}}
                    else if(model.error&&!(source!=null&&result==null))Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(16.dp)){Text(model.status,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}
                    else if(model.status.isNotBlank()&&source==null)Text(model.status,style=MaterialTheme.typography.bodySmall)
                }
            }
            val input=model.source
            if(input!=null&&model.result==null){
                Surface(tonalElevation=3.dp,color=MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    if(model.busy){
                        Text(model.status+(if(model.progress>=0)" · ${(model.progress*100).toInt()}%"else""),style=MaterialTheme.typography.titleMedium)
                        if(model.progress<0)LinearProgressIndicator(Modifier.fillMaxWidth())else LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth())
                        OutlinedButton(onClick={model.cancel()},modifier=Modifier.fillMaxWidth()){Text("Cancel")}
                    }else{
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text(if(input.kind==CompressKind.IMAGE)"Size preview"else"Projected size",style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                            Text(when{model.estimating->"Calculating…";model.estimate!=null->(if(input.kind==CompressKind.IMAGE)""else"About ")+size(model.estimate!!);input.kind==CompressKind.FILES->"Varies by contents";else->"Not available"},style=MaterialTheme.typography.titleLarge)
                        }
                        Text(when{
                            model.estimateError!=null->model.estimateError!!
                            model.estimate!=null&&model.estimate!!>=input.bytes->"May be larger · try a smaller size or lower quality"
                            model.estimate!=null->"About ${((input.bytes-model.estimate!!)*100/input.bytes.coerceAtLeast(1))}% smaller"+(if(input.kind==CompressKind.IMAGE)""else" · estimate only")
                            input.kind==CompressKind.FILES->"Lossless ZIP · media may not get smaller"
                            else->"Finished size is shown before saving"
                        },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(model.error)Text(model.status,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        Button(onClick={model.compress()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(input.kind==CompressKind.FILES)"Create ZIP"else"Compress ${input.kind.title.lowercase()}")}
                    }
                }}
            }
        }
    }
    if(confirmCancel)AlertDialog(onDismissRequest={confirmCancel=false},title={Text("Cancel this operation?")},text={Text("Your originals stay unchanged.")},confirmButton={TextButton(onClick={confirmCancel=false;model.cancel()}){Text("Cancel operation")}},dismissButton={TextButton(onClick={confirmCancel=false}){Text("Keep going")}})
}
