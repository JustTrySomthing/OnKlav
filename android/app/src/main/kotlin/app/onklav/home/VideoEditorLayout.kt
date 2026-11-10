package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.onklav.shared.R

internal data class VideoToolGroup(val name:String,val icon:Int,val tools:List<String>)
internal val videoToolGroups = listOf(
    VideoToolGroup("Edit",R.drawable.audio_edit_contentcut,listOf("Edit","Transform","Speed","Keyframes")),
    VideoToolGroup("Audio",R.drawable.audio_edit_graphiceq,listOf("Audio")),
    VideoToolGroup("Layers",R.drawable.video_pro_overlay,listOf("Text","Captions","Overlay")),
    VideoToolGroup("Finish",R.drawable.photo_edit_color,listOf("Filters","Transitions","Fades","Canvas")),
)

/** Wide, labelled selection instead of an off-screen strip of chips. */
@Composable internal fun VideoChoice(label:String,current:String,options:List<Pair<String,String>>,enabled:Boolean=true,selectedId:String?=null,select:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={open=true},enabled=enabled,shape=RoundedCornerShape(12.dp),
            colors=ButtonDefaults.textButtonColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh),
            contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription=label;stateDescription=current}){
            Text(current,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
            Icon(painterResource(R.drawable.photo_edit_chevron),null,Modifier.size(20.dp))
        }
    }
    if (open && enabled) VideoEditorSheet(label, dismiss = { open = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (id, name) ->
                val active = id == (selectedId ?: current)
                Surface(
                    onClick = { open = false; select(id) },
                    modifier = Modifier.fillMaxWidth().semantics { selected = active },
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (active) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    LaunchedEffect(enabled) { if (!enabled) open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VideoEditorSheet(title: String, dismiss: () -> Unit, content: @Composable () -> Unit) {
    val bodyHeight = (LocalConfiguration.current.screenHeightDp * .60f).dp
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Close $title") }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().heightIn(max = bodyHeight)) { content() }
        }
    }
}

@Composable internal fun VideoEditorOptions(
    enabled: Boolean,
    dismiss: () -> Unit,
    options: List<Pair<String, () -> Unit>>,
) {
    VideoEditorSheet("Editor options", dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AlignedCardGrid(options, gap = 8.dp) { (label, action), modifier ->
                Surface(
                    onClick = { dismiss(); action() },
                    modifier = modifier,
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(Modifier.heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable private fun VideoGroupPalette(group: VideoToolGroup, enabled: Boolean, back: () -> Unit, choose: (String) -> Unit) {
    val tools = if (group.name == "Audio") group.tools + "Add audio" else group.tools
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = back) { Icon(Icons.Outlined.ChevronLeft, "All tool categories") }
        val entries = tools + "Add media"
        BoxWithConstraints(Modifier.weight(1f)) {
            val spacing = 4.dp
            val minTile = (60 * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp
            val tileWidth = maxOf(minTile, (maxWidth - spacing * (entries.size - 1)) / entries.size)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                entries.forEach { tool ->
                    val label = if (tool == "Add media") "Add clips" else videoToolLabel(tool)
                    Column(
                        Modifier.width(tileWidth).heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = enabled, role = Role.Button) { choose(tool) }
                            .semantics { contentDescription = "Open $label" }
                            .padding(horizontal = 3.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(painterResource(videoToolIcon(tool)), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable private fun VideoToolDock(enabled: Boolean, select: (VideoToolGroup) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            videoToolGroups.forEach { item ->
                Column(
                    Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled, role = Role.Button) { select(item) }
                        .semantics { contentDescription = "${item.name} tools" }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(item.icon), null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One shared footer: timeline + categories, or the focused inspector. No idle palette. */
@Composable internal fun VideoWorkspaceLayout(tool:String?,group:String?,enabled:Boolean,
    chooseGroup:(VideoToolGroup)->Unit,closeGroup:()->Unit,chooseTool:(String)->Unit,
    preview:@Composable (Modifier)->Unit,transport:@Composable ()->Unit,
    timeline:@Composable (Modifier,Boolean)->Unit,inspector:@Composable (Modifier)->Unit,previewAspect:Float=16f/9f,timelineRequest:Int=0){
    BoxWithConstraints(Modifier.fillMaxSize()){
        val fontScale=LocalDensity.current.fontScale.coerceIn(1f,1.6f)
        val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0&&tool!=null
        val wide=maxWidth>=480.dp&&maxWidth>maxHeight*1.2f&&!keyboard
        val short=!wide&&!keyboard&&maxHeight<420.dp
        var timelineOpen by remember{mutableStateOf(false)}
        LaunchedEffect(timelineRequest){if(timelineRequest>0)timelineOpen=true}
        LaunchedEffect(tool){timelineOpen=false}
        val dock: @Composable () -> Unit = {
            val activeGroup = videoToolGroups.firstOrNull { it.name == group }
            if (activeGroup == null) VideoToolDock(enabled, chooseGroup)
            else VideoGroupPalette(activeGroup, enabled, closeGroup, chooseTool)
        }
        val controls:@Composable (Modifier)->Unit={modifier->
            Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainerLow){
                if(tool!=null)inspector(Modifier.fillMaxSize())else Column(Modifier.fillMaxSize()){
                    timeline(Modifier.fillMaxWidth().weight(1f),true)
                    dock()
                }
            }
        }
        if(wide){
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Column(Modifier.weight(1.2f).fillMaxHeight()){
                    preview(Modifier.weight(1f).fillMaxWidth());transport()
                    timeline(Modifier.fillMaxWidth().height(100.dp),true)
                }
                Surface(Modifier.weight(1f).fillMaxHeight(),color=MaterialTheme.colorScheme.surfaceContainerLow){
                    if(tool!=null)inspector(Modifier.fillMaxSize())else Box(contentAlignment=Alignment.BottomCenter){dock()}
                }
            }
        }else{
            val footerHeight = if (short) minOf(160.dp, maxHeight * .52f) else (180f * fontScale).dp
            val previewHeight=if(keyboard)minOf(maxWidth/previewAspect.coerceIn(.2f,5f),maxHeight*.40f)else
                (maxHeight - 48.dp - footerHeight).coerceAtLeast(48.dp)
            Column(Modifier.fillMaxSize()){
                Box(Modifier.fillMaxWidth().height(previewHeight)){
                    preview(Modifier.fillMaxSize())
                    if(short)FilledTonalButton(onClick={timelineOpen=true},modifier=Modifier.align(Alignment.TopStart).semantics{contentDescription="Open timeline"},contentPadding=PaddingValues(horizontal=10.dp)){
                        Text("Timeline",style=MaterialTheme.typography.labelMedium)
                    }
                }
                if(!keyboard)transport()
                controls(Modifier.fillMaxWidth().weight(1f))
            }
        }
        if (timelineOpen) VideoEditorSheet("Timeline", dismiss = { timelineOpen = false }) {
            timeline(Modifier.fillMaxWidth().height(180.dp), true)
        }
    }
}

internal fun videoToolLabel(tool:String)=when(tool){"Edit"->"Trim & split";"Keyframes"->"Motion";"Filters"->"Color";"Performance"->"Preview quality";"Audio"->"Sound controls";else->tool}
internal fun videoToolIcon(tool:String)=when(tool){
 "Add media","Add audio"->R.drawable.ref_action_add;"Edit"->R.drawable.audio_edit_contentcut;"Transform"->R.drawable.video_edit_rotate
 "Transitions"->R.drawable.maps_swap;"Speed"->R.drawable.video_pro_speed;"Keyframes"->R.drawable.video_pro_key;"Audio"->R.drawable.ref_message_speaker
 "Text"->R.drawable.video_pro_text;"Captions"->R.drawable.video_pro_captions;"Overlay"->R.drawable.video_pro_overlay;"Canvas"->R.drawable.video_edit_fullscreen;"Fades"->R.drawable.video_pro_fade
 else->R.drawable.photo_edit_color
}
/** One settings page at a time; the preview and transport stay in the workspace. */
@Composable internal fun VideoInspectorTabs(options:List<String>,current:String,select:(String)->Unit){
 Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom=2.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
  options.forEach{label->FilterChip(selected=current==label,onClick={select(label)},label={Text(label)},modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="$label settings"})}
 }
}
@Composable internal fun VideoDangerButton(label:String,enabled:Boolean=true,detail:String="This changes only this project. Original files are kept. You can Undo afterward.",action:()->Unit){
 var confirm by remember(label){mutableStateOf(false)}
 TextButton(onClick={confirm=true},enabled=enabled,modifier=Modifier.heightIn(min=48.dp)){Text(label,color=MaterialTheme.colorScheme.error)}
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("$label?")},text={Text(detail)},confirmButton={TextButton(onClick={confirm=false;action()},enabled=enabled){Text("Confirm")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}

@Composable internal fun VideoAudioAction(label:String,icon:Int,enabled:Boolean,modifier:Modifier=Modifier,action:()->Unit){
    OutlinedButton(onClick=action,enabled=enabled,modifier=modifier.heightIn(min=72.dp),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=4.dp,vertical=8.dp)){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
            Icon(painterResource(icon),null,Modifier.size(23.dp))
            Text(label,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun VideoScrollableSettings(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    val scroll=rememberScrollState()
    val ink=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f)
    Box(modifier){
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end=8.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        Canvas(Modifier.align(Alignment.CenterEnd).width(3.dp).fillMaxHeight()){
            if(scroll.maxValue>0){
                val total=size.height+scroll.maxValue
                val height=(size.height*size.height/total).coerceAtLeast(24.dp.toPx()).coerceAtMost(size.height)
                val top=(size.height-height)*scroll.value/scroll.maxValue
                drawRoundRect(ink,androidx.compose.ui.geometry.Offset(0f,top),androidx.compose.ui.geometry.Size(size.width,height),androidx.compose.ui.geometry.CornerRadius(size.width/2))
            }
        }
    }
}

/** A compact route out of an unavailable settings page. */
@Composable internal fun VideoSettingsPrompt(message:String,actionLabel:String,action:()->Unit){
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  OutlinedButton(onClick=action,modifier=Modifier.heightIn(min=48.dp)){Text(actionLabel)}
 }
}
internal fun videoSelectionLabel(project:VideoProject,index:Int,audioId:String,layerId:String?):String {
 project.layers.firstOrNull{it.id==layerId}?.let{return "Overlay · ${it.clip.name}"}
 project.music.firstOrNull{it.id==audioId}?.let{return "Audio · ${it.name}"}
 return "Clip ${index+1} of ${project.clips.size} · ${project.clips.getOrNull(index)?.name.orEmpty()}"
}
