@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import app.onklav.shared.R
import kotlin.math.*

@Composable internal fun VideoEditorWorkspace(automaticPreview:Boolean=true,toolbar:VideoToolbarState?=null){val c=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();if(!remember(epoch){videoEditorAccess(c)}){Text("Video Edit storage access is off. Restore it in Settings > Apps > Video Edit.",Modifier.padding(24.dp));return};val model:VideoEditorModel=viewModel(key="video-editor-$epoch");VideoEditorContent(model,automaticPreview,toolbar)}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun VideoEditorContent(m:VideoEditorModel,automaticPreview:Boolean=true,toolbar:VideoToolbarState?=null){
    var pendingOutput by remember{mutableStateOf<VideoOutputSettings?>(null)}
    var positionDialog by remember{mutableStateOf(false)}
    var zoom by remember{mutableFloatStateOf(1f)};var library by remember{mutableStateOf(false)};var export by remember{mutableStateOf(false)};var help by remember{mutableStateOf(false)};var rename by remember{mutableStateOf(false)};var deleteProject by remember{mutableStateOf<String?>(null)};var titleId by remember{mutableStateOf<String?>(null)};var pendingTitle by remember{mutableStateOf<VideoTitle?>(null)};var precise by remember{mutableStateOf(false)};var fullscreen by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){m.import(it)}
    val sound=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let{uri->m.import(listOf(uri),true)}}
    val save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("video/mp4")){uri->val settings=pendingOutput;pendingOutput=null;if(uri!=null)m.export(uri,settings)}
    val lifecycle=LocalLifecycleOwner.current;DisposableEffect(lifecycle,m,automaticPreview){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)m.cancel();if(event==Lifecycle.Event.ON_START&&automaticPreview)m.preview(false)};lifecycle.lifecycle.addObserver(observer);onDispose{lifecycle.lifecycle.removeObserver(observer);m.cancel()}}
    val p=m.project;val c=m.clip;val colors=MaterialTheme.colorScheme
    val largeLabels=LocalDensity.current.fontScale>1.3f
    LaunchedEffect(m.revision,m.busy,m.transforming,automaticPreview){if(automaticPreview&&!m.busy&&!m.transforming&&!m.hasUnsavedChanges&&m.project.clips.isNotEmpty()){kotlinx.coroutines.delay(350);if(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))m.preview(false)}}
    val context=LocalContext.current

    var sheet by remember(p.id){mutableStateOf<String?>(null)}
    var selectedLayer by remember(p.id){mutableStateOf<String?>(null)}
    var group by remember(p.id){mutableStateOf<String?>(null)}
    var more by remember{mutableStateOf(false)}
    var timelineRequest by remember{mutableIntStateOf(0)}
    LaunchedEffect(p.id,p.layers.map{it.id}){if(selectedLayer!=null&&p.layers.none{it.id==selectedLayer})selectedLayer=null}
    fun openTool(name:String){
        m.commit()
        if(name!="Overlay"&&name!="Keyframes")selectedLayer=null
        if(name!="Audio")m.selectAudio("main")
        if(m.hasUnsavedChanges)return
        if(m.playing)m.preview()
        if(name!="Canvas"&&name!="Add media"){
            val overlay=p.layers.firstOrNull{it.id==selectedLayer}.takeIf{name=="Overlay"||name=="Keyframes"}
            val track=p.music.firstOrNull{it.id==m.selectedAudioId}.takeIf{name=="Audio"}
            val start=overlay?.offset?:track?.offset?:m.clipOffset
            val duration=overlay?.clip?.duration?:track?.duration?:c?.duration?:0L
            if(m.cursor<start||m.cursor>=start+duration)m.seek(start)
        }
        if(name=="Add audio"){sound.launch(arrayOf("audio/*"));return}
        if(name=="Add media"){picker.launch(arrayOf("video/*","image/*"));return}
        sheet=name
    }
    fun closeInspector() {
        m.commit()
        if (!m.hasUnsavedChanges) {
            sheet = null
            group = null
        }
    }
    val overlayPicker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){m.import(it,overlay=true)}
    val captionPicker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(m::importCaptions)}
    val captionSave=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/x-subrip")){it?.let(m::exportCaptions)}
    val lutPicker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(m::importLut)}
    val projectImport=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(m::restoreProject)}
    val projectBackup=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/zip")){it?.let(m::backupProject)}
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)m.startVoiceover()}
    fun record(){if(androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED)m.startVoiceover()else microphone.launch(android.Manifest.permission.RECORD_AUDIO)}
    androidx.activity.compose.BackHandler(enabled=sheet!=null||group!=null||titleId!=null){if(titleId!=null){titleId=null;pendingTitle=null}else if(sheet!=null)closeInspector() else group=null}
    Surface(Modifier.fillMaxSize(),color=colors.surface){
        Column(Modifier.fillMaxSize()){
            if(toolbar!=null){SideEffect{toolbar.enabled=!m.busy&&titleId==null;toolbar.canExport=p.clips.isNotEmpty()&&!m.busy&&!m.hasUnsavedChanges&&titleId==null;toolbar.resolution=p.ratio;toolbar.projects={library=true};toolbar.canvas={openTool("Canvas")};toolbar.export={export=true}};DisposableEffect(toolbar){onDispose{toolbar.enabled=false;toolbar.canExport=false;toolbar.projects={};toolbar.canvas={};toolbar.export={}}}}
            else Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){
                VideoIcon(R.drawable.maps_folder,"Projects",!m.busy){library=true}
                TextButton(onClick={rename=true},enabled=!m.busy,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text(p.name,maxLines=1,overflow=TextOverflow.Ellipsis)}
                TextButton(enabled=!m.busy,onClick={openTool("Canvas")},contentPadding=PaddingValues(6.dp)){Text(p.ratio,style=MaterialTheme.typography.labelMedium)}
                FilledTonalButton(modifier=Modifier.semantics{contentDescription="Export video"},enabled=p.clips.isNotEmpty()&&!m.busy&&!m.hasUnsavedChanges,onClick={export=true},contentPadding=PaddingValues(horizontal=12.dp),shape=RoundedCornerShape(12.dp)){Text("Export")}
            }
            if(m.busy&&m.status=="Restoring projects")LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!m.busy&&m.error)Row(Modifier.fillMaxWidth().background(if(m.error)colors.errorContainer else colors.surfaceContainerHigh,RoundedCornerShape(10.dp)).padding(start=10.dp),verticalAlignment=Alignment.CenterVertically){AudioStatusMessage(if(m.hasUnsavedChanges)"Unsaved edits · ${m.status.ifBlank{"Tap Retry save"}}"else m.status,m.error||m.hasUnsavedChanges,Modifier.weight(1f),"Video status","Show video status details");if(m.hasUnsavedChanges)TextButton(onClick=m::commit){Text("Retry save")}else IconButton(onClick=m::dismiss){Icon(Icons.Outlined.Close,"Dismiss status")}}
            if(p.clips.isEmpty()){
                Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Icon(painterResource(R.drawable.ref_app_video_editor),null,Modifier.size(64.dp),tint=colors.primary);Spacer(Modifier.height(16.dp));Text("Start a video project",style=MaterialTheme.typography.headlineSmall);Text("Add videos or photos to begin",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant);Spacer(Modifier.height(24.dp))
                    Button(onClick={picker.launch(arrayOf("video/*","image/*"))}){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Add media")}
                    TextButton(onClick={library=true}){Text("Open a project")}
                }
            }else{
                VideoWorkspaceLayout(sheet,group,!m.busy,
                    chooseGroup={item->m.commit();if(!m.hasUnsavedChanges)group=item.name},closeGroup={group=null},chooseTool={openTool(it)},
                    preview={modifier->VideoPreviewCanvas(m,automaticPreview,fullscreen,{fullscreen=true},selectedLayer,modifier,allowTransform=sheet in setOf("Transform","Keyframes")||sheet=="Overlay"&&selectedLayer!=null)},
                    transport={
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.weight(1f)) { AudioHint("Go to exact time"){
                                TextButton(onClick={positionDialog=true},enabled=!m.busy,contentPadding=PaddingValues(2.dp),modifier=Modifier.fillMaxWidth()){
                                    Column(Modifier.fillMaxWidth()){
                                        Text(if (sheet != null) videoToolLabel(sheet.orEmpty()) else p.name, maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium,color=colors.onSurface)
                                        Text(if(largeLabels)videoPreciseTime(m.cursor)else"${videoPreciseTime(m.cursor)} / ${videoTime(p.duration)}",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                                    }
                                }
                            }
                            }
                            VideoIcon(R.drawable.audio_edit_undo,"Undo",!m.busy&&titleId==null&&m.canUndo){m.history(true)}
                            VideoIcon(R.drawable.audio_edit_redo,"Redo",!m.busy&&titleId==null&&m.canRedo){m.history(false)}
                            AudioHint(if(m.playing)"Pause preview"else"Play preview"){
                                FilledTonalIconButton(onClick={m.preview()},enabled=!m.busy,modifier=Modifier.size(44.dp)){
                                    Icon(painterResource(if(m.playing)R.drawable.audio_edit_pause else R.drawable.audio_edit_playarrow),if(m.playing)"Pause preview"else"Play preview",Modifier.size(26.dp))
                                }
                            }
                            VideoIcon(R.drawable.ref_browser_more,"More video options",!m.busy&&titleId==null){more=true}
                        }
                    },timeline={modifier,compact->
                        Column(modifier.background(colors.surfaceContainerLowest,RoundedCornerShape(12.dp)).padding(horizontal=6.dp,vertical=4.dp)){
                            Text(videoSelectionLabel(p,m.selected,m.selectedAudioId,selectedLayer),Modifier.fillMaxWidth().padding(horizontal=6.dp).semantics{contentDescription="Selected item";stateDescription=videoSelectionLabel(p,m.selected,m.selectedAudioId,selectedLayer)},maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                            VideoProTimeline(m,zoom,{openTool(it)},{selectedLayer=it;sheet="Overlay"},compact=true,clearLayer={selectedLayer=null},selectedLayerId=selectedLayer,modifier=Modifier.weight(1f),trimEnabled=sheet=="Edit",interactive=titleId==null)
                        }
                    },inspector={modifier->
                        Surface(modifier,color=colors.surfaceContainer,shape=RoundedCornerShape(topStart=16.dp,topEnd=16.dp)){
                            if(sheet=="Canvas")VideoCanvasPanel(p,{m.apply(it);if(!m.hasUnsavedChanges){sheet=null;group=null}},{sheet=null;group=null})
                            else if(titleId!=null&&pendingTitle!=null)VideoTitlePanel(pendingTitle!!,c?.duration?:1,
                                save={next->c?.let{clip->m.edit(clip.copy(titles=if(clip.titles.any{it.id==next.id})clip.titles.map{if(it.id==next.id)next else it}else clip.titles+next));m.commit()};titleId=null;pendingTitle=null},
                                delete={c?.let{clip->m.edit(clip.copy(titles=clip.titles.filter{it.id!=titleId}));m.commit()};titleId=null;pendingTitle=null},close={titleId=null;pendingTitle=null},existing=c?.titles?.any{it.id==titleId}==true)
                            else Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
                                Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically){
                                    IconButton(onClick=::closeInspector,modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.ChevronLeft,"Back to tools")}
                                    Column(Modifier.weight(1f)){
                                        Text(videoToolLabel(sheet.orEmpty()),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                        Text(if(m.hasUnsavedChanges)"Changes pending" else videoSelectionLabel(p,m.selected,m.selectedAudioId,selectedLayer),Modifier.semantics{contentDescription=if(m.hasUnsavedChanges)"Edit save state" else "Tool target";liveRegion=LiveRegionMode.Polite},maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                                    TextButton(onClick=::closeInspector,enabled=!m.busy,modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="Done editing"}){Text("Done")}
                                    }
                                }
                                key(sheet,m.selected,selectedLayer){
                                    VideoProInspector(m,sheet.orEmpty(),selectedLayer,{selectedLayer=it},
                                        addMedia={overlayPicker.launch(arrayOf("video/*","image/*"))},addAudio={sound.launch(arrayOf("audio/*","video/*"))},
                                        record={record()},importCaptions={captionPicker.launch(arrayOf("application/x-subrip","text/vtt","text/plain","application/octet-stream"))},saveCaptions={captionSave.launch(p.name+".srt")},
                                        precise={precise=true},editTitle={title->pendingTitle=title;titleId=title.id},help={help=true},importLut={lutPicker.launch(arrayOf("application/octet-stream","text/plain","*/*"))})
                                }
                            }
                        }
                    },previewAspect=videoCanvasAspect(p),timelineRequest=timelineRequest)
            }
        }
    }
    if (more && p.clips.isNotEmpty()) VideoEditorOptions(
        enabled = !m.busy,
        dismiss = { more = false },
        options = listOf(
            "Add clips" to { picker.launch(arrayOf("video/*", "image/*")) },
            "Timeline" to { timelineRequest++ },
            "Zoom timeline in" to { zoom = min(8f, zoom * 1.5f) },
            "Zoom timeline out" to { zoom = max(.25f, zoom / 1.5f) },
            "Go to exact time" to { positionDialog = true },
            "Rename project" to { rename = true },
            "Canvas" to { openTool("Canvas") },
            "Preview & proxies" to { openTool("Performance") },
            "Editing guide" to { help = true },
        ),
    )
    if(positionDialog)VideoPositionDialog("Go to time",m.cursor,p.duration,{m.seek(it);positionDialog=false},{positionDialog=false})
    if(m.recording)AlertDialog(onDismissRequest={},title={Text("Recording voiceover")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(videoTime(m.recordingMs));LinearProgressIndicator(progress={m.recordingLevel},modifier=Modifier.fillMaxWidth());Text("Record your narration. Playback stays quiet to avoid microphone feedback.",style=MaterialTheme.typography.bodyMedium)}},confirmButton={TextButton(onClick={m.stopVoiceover()}){Text("Stop and save")}},dismissButton={TextButton(onClick={m.stopVoiceover(true)}){Text("Discard")}})
    if(m.busy&&m.status!="Restoring projects")AlertDialog(onDismissRequest={},title={Text("Video Edit")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(m.status,Modifier.semantics{liveRegion=LiveRegionMode.Polite});if(m.progress<0)LinearProgressIndicator(Modifier.fillMaxWidth())else LinearProgressIndicator(progress={m.progress},modifier=Modifier.fillMaxWidth())}},confirmButton={TextButton(onClick=m::cancel){Text("Cancel")}})
    if(library)AlertDialog(onDismissRequest={library=false},title={Text("Projects")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={library=false;m.newProject()}){Icon(Icons.Outlined.Add,null);Text("New project")};OutlinedButton(enabled=!m.busy,onClick={library=false;projectImport.launch(arrayOf("application/zip","application/octet-stream"))}){Text("Import portable project")};OutlinedButton(enabled=!m.busy&&p.clips.isNotEmpty(),onClick={library=false;projectBackup.launch(p.name.replace(Regex("[^a-zA-Z0-9 ._-]"),"_")+"-project.zip")}){Text("Back up project with media")};Text("Portable backups include every imported video, photo and audio file. Over 256 MB? Save to a connected phone folder in Files. Restore creates a new project.",style=MaterialTheme.typography.bodySmall);m.projects.forEach{row->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={library=false;m.openProject(row.id)},modifier=Modifier.weight(1f)){Column(Modifier.fillMaxWidth()){Text(row.name,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${row.clips.size} clips · ${videoTime(row.duration)}",style=MaterialTheme.typography.labelSmall)}};VideoIcon(R.drawable.video_edit_delete,"Delete ${row.name}",!m.busy){deleteProject=row.id}}}}},confirmButton={TextButton(onClick={library=false}){Text("Done")}})
    deleteProject?.let{id->AlertDialog(onDismissRequest={deleteProject=null},title={Text("Delete project?")},text={Text("This removes the project and its imported copies. Your original files and exported videos are kept.")},confirmButton={TextButton(onClick={m.deleteProject(id);deleteProject=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleteProject=null}){Text("Cancel")}})}
    if(rename){var name by remember{mutableStateOf(p.name)};AlertDialog(onDismissRequest={rename=false},title={Text("Project name")},text={OutlinedTextField(value=name,onValueChange={name=it.take(120)},singleLine=true,label={Text("Name")})},confirmButton={TextButton(onClick={m.rename(name);rename=false},enabled=name.trim().isNotEmpty()){Text("Save")}},dismissButton={TextButton(onClick={rename=false}){Text("Cancel")}})}
    if(export)VideoOutputDialog(p,true,{draft->pendingOutput=VideoOutputSettings(draft.height,draft.fps,draft.bitrate);export=false;save.launch(p.name.replace(Regex("[^\\p{L}\\p{N} ._-]"),"_")+".mp4")},{export=false})
    if(precise&&c!=null){var start by remember{mutableStateOf((c.start/1000.0).toString())};var end by remember{mutableStateOf((c.end/1000.0).toString())};val a=videoPositionMs(start,c.sourceMs);val b=videoPositionMs(end,c.sourceMs);val valid=a!=null&&b!=null&&a>=0&&b<=c.sourceMs&&b-a>=100
        AlertDialog(onDismissRequest={precise=false},title={Text("Trim clip")},text={Column(Modifier.imePadding().heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it},label={Text("Start (seconds)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));OutlinedTextField(end,{end=it},label={Text("End (seconds)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal));Text("Source length: ${videoTime(c.sourceMs)}",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={m.edit(videoClampClip(c.copy(start=a!!,end=b!!)));m.commit();precise=false},enabled=valid){Text("Apply")}},dismissButton={TextButton(onClick={precise=false}){Text("Cancel")}})}
    if(fullscreen)Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize(),color=Color.Black){Box{
        if(m.player!=null)VideoCanvasFrame(p,Modifier.fillMaxSize()){VideoPlayerView(m,Modifier.fillMaxSize())}else{
            c?.let{clip->m.thumbnails[clip.file]?.let{image->Image(image.asImageBitmap(),"Fullscreen source frame",Modifier.fillMaxSize())}}
            Text(if(m.error)"Preview unavailable · Tap Play to retry"else"Source frame · Tap Play to preview edits",Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.7f)).padding(12.dp),color=Color.White,style=MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick={fullscreen=false},modifier=Modifier.align(Alignment.TopEnd).padding(12.dp)){Icon(Icons.Outlined.Close,"Close fullscreen",tint=Color.White)};FilledTonalIconButton(onClick={m.preview()},modifier=Modifier.align(Alignment.BottomCenter).padding(20.dp)){Icon(painterResource(if(m.playing)R.drawable.audio_edit_pause else R.drawable.audio_edit_playarrow),if(m.playing)"Pause preview" else "Play preview")}}}}
    if(help)AlertDialog(onDismissRequest={help=false},title={Text("Video Edit")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("1. Add videos or photos. Tap a clip in the timeline to select it.\n\n2. Scrub to a point and Split, drag trim handles, or enter exact trim times. Duplicate, reorder, or delete clips.\n\n3. Add timed text, music, fades, motion, and color. Preview shows the same composition used for export.\n\n4. Choose canvas, resolution, and frame rate, then export a new MP4.");Text("Projects save automatically with private copies of imported media. Open Projects to switch, create, or delete. Undo and Redo track the current editing session.");Text("Transitions overlap neighboring clips; Fades create an opening or ending through black. This editor supports a main video/photo lane, up to four video/photo overlays, timed text/caption layers, keyframes, speed curves and up to four audio tracks including recorded voiceovers. Import SRT or WebVTT for timed captions. Automatic speech captions, AI background removal, cloud templates, stabilization and optical-flow slow motion are not included.",style=MaterialTheme.typography.bodySmall);Text("Keep Video Edit open during import or export. Codec support and encoding speed depend on your phone. If export fails, try 720p / 30 fps. Originals are preserved.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={help=false}){Text("Done")}})
}
internal fun videoClampClip(c:VideoClip):VideoClip{val d=c.duration;return c.copy(fadeIn=c.fadeIn.coerceAtMost(d),fadeOut=c.fadeOut.coerceAtMost(d),keyframes=if(c.keyframes.any{it.at>d})videoSplitKeys(c,d,true)else c.keyframes,titles=c.titles.filter{it.start<d}.map{it.copy(end=it.end.coerceAtMost(d))})}
@Composable internal fun VideoPlayerView(m:VideoEditorModel,modifier:Modifier){AndroidView(factory={c->PlayerView(c).apply{useController=false;setShutterBackgroundColor(android.graphics.Color.BLACK)}},update={it.player=m.player},onRelease={it.player=null},modifier=modifier)}
@Composable internal fun VideoIcon(icon:Int,label:String,enabled:Boolean=true,action:()->Unit){AudioHint(label){IconButton(onClick=action,enabled=enabled,modifier=Modifier.size(48.dp)){Icon(painterResource(icon),label,Modifier.size(24.dp))}}}
@Composable internal fun VideoTool(label:String,icon:Int,enabled:Boolean,action:()->Unit){OutlinedButton(onClick=action,enabled=enabled,contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)){Icon(painterResource(icon),null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(label)}}
@Composable internal fun VideoToggle(label:String,value:Boolean,enabled:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(value=value,enabled=enabled,role=Role.Switch,onValueChange=change),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Switch(checked=value,onCheckedChange=null,enabled=enabled)
    }
}
/** Defaults are explicit for adjustable controls; time/trim controls have no reset marker. */
private fun videoControlDefault(label:String):Float?=when(label){"Curve handle 1 X","Curve handle 2 X"->.25f;"Curve handle 1 Y"->.1f;"Curve handle 2 Y"->1f;"Playback speed","Zoom","Keyframe zoom","Opacity","Overlay opacity","Text size","LUT strength"->1f;"Clip volume"->1f;"Volume"->.5f;"Overlay scale"->.35f;"Overlay X","Overlay Y"->.5f;"Key tolerance"->.35f;"Mask feather"->.03f;"Text vertical position"->-.65f;"Brightness","Contrast","Saturation","Warmth","Hue","Horizontal position","Vertical position","Keyframe X","Keyframe Y","Rotation","Overlay rotation","Text horizontal position","Stereo balance","Pitch","Noise cleanup","Fade in","Fade out","Sound fade in","Sound fade out"->0f;else->null}
@Composable internal fun VideoControl(label:String,value:Float,range:ClosedFloatingPointRange<Float>,formatted:String,enabled:Boolean,change:(Float)->Unit,commit:()->Unit){
    val default=videoControlDefault(label)?.takeIf{it in range};val original=default!=null&&abs(value-default)<.0001f
    val tint=if(original)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val marker=MaterialTheme.colorScheme.onSurfaceVariant
    Column{
        Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(formatted,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1f)){
                Slider(value=value.coerceIn(range),onValueChange={v->change(if(default!=null&&abs(v-default)<(range.endInclusive-range.start)*.012f)default else v)},onValueChangeFinished=commit,valueRange=range,enabled=enabled,colors=SliderDefaults.colors(thumbColor=tint,activeTrackColor=tint),modifier=Modifier.fillMaxWidth().height(48.dp).semantics{contentDescription=label})
                if(default!=null)androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(48.dp).semantics{contentDescription="Original $label: $default"}){val inset=12.dp.toPx();val x=inset+(size.width-2*inset)*(default-range.start)/(range.endInclusive-range.start);drawLine(marker,androidx.compose.ui.geometry.Offset(x,size.height/2+10.dp.toPx()),androidx.compose.ui.geometry.Offset(x,size.height/2+15.dp.toPx()),2.dp.toPx())}
            }
            if(default!=null)IconButton(onClick={change(default);commit()},enabled=enabled&&!original,modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.audio_edit_restartalt),"Reset $label",Modifier.size(18.dp))}
        }
    }
}

@Composable internal fun VideoPositionDialog(title:String,initial:Long,duration:Long,apply:(Long)->Unit,dismiss:()->Unit){
    var text by remember(initial){mutableStateOf((initial/1000.0).toString())}
    val value=videoPositionMs(text,duration)
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Column(Modifier.imePadding().heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(value=text,onValueChange={text=it.take(24)},singleLine=true,label={Text("Seconds")},isError=value==null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        Text("Enter 0–${duration/1000.0} seconds (up to millisecond precision).",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={value?.let(apply)},enabled=value!=null){Text("Apply")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
