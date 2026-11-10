package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shift in output time through every speed segment, including beyond the current trim. */
internal fun videoTrimSource(c:VideoClip,startEdge:Boolean,delta:Long):Long {
    val anchor=if(startEdge)c.start else c.end
    val at=if(anchor==0L)0L else videoOutputDuration(0,anchor,c.speed,c.rates)
    val full=c.copy(start=0,end=c.sourceMs)
    val target=(at+delta).coerceIn(0,full.duration)
    val source=videoSourceAt(full,target)
    return if(startEdge)source.coerceIn(0,c.end-100)else source.coerceIn(c.start+100,c.sourceMs)
}
internal data class VideoOutputSettings(val height:Int=1080,val fps:Int=30,val bitrate:Int=0) {
    fun project(p:VideoProject)=p.copy(height=height,fps=fps,bitrate=bitrate).also{VideoProject.parse(it.json())}
    fun estimatedBytes(duration:Long):Long?=if(bitrate==0)null else (duration/1000.0*(bitrate+192000)/8*1.04).toLong()
}
internal fun videoCanvasAspect(p:VideoProject):Float=p.ratio.split(':').let{it[0].toFloat()/it[1].toFloat()}

/** A real canvas boundary keeps preview gestures and letterboxing consistent with export. */
@Composable internal fun VideoCanvasFrame(p:VideoProject,modifier:Modifier=Modifier,content:@Composable BoxScope.()->Unit){
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center){
        val aspect=videoCanvasAspect(p)
        val width=minOf(maxWidth,maxHeight*aspect)
        Box(Modifier.width(width).height(width/aspect).clipToBounds().semantics{contentDescription="Project canvas ${p.ratio}"},content=content)
    }
}

@Composable internal fun VideoOutputDialog(p:VideoProject,export:Boolean,confirm:(VideoProject)->Unit,close:()->Unit){
    var draft by remember(p.id,export){mutableStateOf(p)}
    val output=VideoOutputSettings(draft.height,draft.fps,draft.bitrate)
    val c=androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest=close,title={Text(if(export)"Export video"else"Canvas")},text={
        Column(Modifier.imePadding().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            if(!export){
                Text("Aspect ratio",style=MaterialTheme.typography.titleSmall)
                VideoChoice("Canvas aspect ratio",draft.ratio,VideoProject.ratios.map{it to it}){draft=draft.copy(ratio=it)}
                VideoToggle("Fill frame (crop edges)",draft.fill,true){draft=draft.copy(fill=it)}
                Text(if(draft.fill)"Edges outside the canvas are cropped."else"The whole image fits, with black bars where needed.",style=MaterialTheme.typography.bodySmall)
            }else Text("${p.ratio} canvas · ${videoTime(p.duration)} · MP4",style=MaterialTheme.typography.titleSmall)
            Text("Resolution",style=MaterialTheme.typography.titleSmall)
            VideoChoice("Output resolution",if(draft.height==2160)"4K"else"${draft.height}p",listOf(480,720,1080,2160).map{it.toString() to if(it==2160)"4K · largest"else"${it}p"},selectedId=draft.height.toString()){draft=draft.copy(height=it.toInt())}
            Text("Frame rate limit",style=MaterialTheme.typography.titleSmall)
            VideoChoice("Output frame rate","Up to ${draft.fps} fps",listOf(24,25,30,50,60).map{it.toString() to "$it fps"},selectedId=draft.fps.toString()){draft=draft.copy(fps=it.toInt())}
            Text("Quality",style=MaterialTheme.typography.titleSmall)
            val qualities=listOf(0 to "Automatic",4000000 to "Smaller · 4 Mbps",8000000 to "Balanced · 8 Mbps",16000000 to "High · 16 Mbps",32000000 to "Maximum · 32 Mbps")
            VideoChoice("Output quality",qualities.single{it.first==draft.bitrate}.second,qualities.map{it.first.toString() to it.second},selectedId=draft.bitrate.toString()){draft=draft.copy(bitrate=it.toInt())}
            val dimensions=VideoComposition.dimensions(draft)
            Text("${dimensions.first} × ${dimensions.second} · H.264 / AAC",style=MaterialTheme.typography.bodySmall)
            output.estimatedBytes(p.duration)?.let{Text("About ${android.text.format.Formatter.formatShortFileSize(c,it)} · estimate only",style=MaterialTheme.typography.bodySmall)}
            Text(if(export)"A separate copy is saved. For an easier export, choose 720p / 30 fps."else"Changes apply when you tap Done.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={confirm(draft)}){Text(if(export)"Choose save location"else"Done")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}

/** ZIP level0 overhead is bounded conservatively before opening a host Files save. */
internal fun videoBackupEstimate(p:VideoProject,folder:java.io.File):Long {
    val names=VideoProjectArchive.files(p)
    val payload=names.sumOf{java.io.File(folder,it).length()}+p.json().toString().toByteArray(Charsets.UTF_8).size
    return (payload*1.001).toLong()+(names.size+1)*1024L+1024
}
internal fun videoCheckBackupDestination(p:VideoProject,folder:java.io.File,uri:android.net.Uri,packageName:String){
    require(uri.authority!=packageName+".file-selection"||videoBackupEstimate(p,folder)<=FilesBroker.MAX_FILE){"This project backup is over 256 MB. In Files, choose a connected phone folder for the backup."}
}
