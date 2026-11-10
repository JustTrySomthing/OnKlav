package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import app.onklav.shared.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.*

internal fun audioTime(seconds:Float):String{val n=(seconds*100).roundToLong().coerceAtLeast(0);return "%d:%02d.%02d".format(n/6000,n/100%60,n%100)}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AudioEditorWorkspace(){
    val context=LocalContext.current;val epoch by AppSdk.epoch.collectAsState();val allowed=remember(epoch){audioEditorAccess(context)}
    if(!allowed){Text("Audio Edit storage access is off. Restore it in Settings > Apps > Audio Edit.",Modifier.padding(24.dp));return}
    val model:AudioEditorModel=viewModel(key="audio-editor-$epoch")
    AudioEditorContent(model)
}
/** Each group exposes three visible choices; changing one resets only its panel scroll. */
internal enum class AudioTool(val group:String,val label:String) {
    Selection("Trim","Edit"), Fades("Trim","Fades"), Reverse("Trim","Reverse"),
    Volume("Sound","Volume"), Tone("Sound","Tone"), Pitch("Sound","Pitch"),
    Noise("Clean","Noise"), Gate("Clean","Gate"), Filters("Clean","Filters")
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun AudioEditorContent(model:AudioEditorModel){
    var tab by rememberSaveable{mutableStateOf("Trim")}
    var trimTool by rememberSaveable{mutableStateOf(AudioTool.Selection)}
    var soundTool by rememberSaveable{mutableStateOf(AudioTool.Volume)}
    var cleanTool by rememberSaveable{mutableStateOf(AudioTool.Noise)}
    val tool=when(tab){"Sound"->soundTool;"Clean"->cleanTool;else->trimTool}
    var zoom by rememberSaveable{mutableFloatStateOf(1f)}
    var offset by rememberSaveable{mutableFloatStateOf(0f)}
    var waveformExpanded by rememberSaveable{mutableStateOf(false)}
    BackHandler(enabled=waveformExpanded){waveformExpanded=false}
    var zoomSource by rememberSaveable{mutableStateOf<String?>(null)}
    var export by remember{mutableStateOf(false)};var format by rememberSaveable{mutableStateOf("MP3")}
    var replace by remember{mutableStateOf(false)};var reset by remember{mutableStateOf(false)}
    var help by remember{mutableStateOf(false)};var precise by remember{mutableStateOf(false)}
    var more by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){it?.let(model::open)}
    val mp3Save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("audio/mpeg")){it?.let{model.export(it,false,true)}}
    val waveSave=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("audio/wav")){it?.let{model.export(it,false)}}
    val aacSave=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("audio/mp4")){it?.let{model.export(it,true)}}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle,model){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)model.cancel()}
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer);model.cancel()}
    }
    val s=model.source;val duration=model.duration;val colors=MaterialTheme.colorScheme
    LaunchedEffect(s?.file?.name){if(s!=null&&zoomSource!=s.file.name){zoomSource=s.file.name;zoom=1f;offset=0f}}
    Surface(Modifier.fillMaxSize(),color=colors.surface){
        BoxWithConstraints(Modifier.fillMaxSize()){
        val wide=maxWidth>=568.dp && maxWidth>maxHeight
        Column(Modifier.fillMaxSize().testTag("audio-workspace")){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                AudioHint("Open audio"){IconButton(modifier=Modifier.size(48.dp),enabled=!model.busy,onClick={if(s==null)picker.launch(arrayOf("audio/*","video/mp4"))else replace=true}){Icon(Icons.Outlined.FolderOpen,"Open audio")}}
                Column(Modifier.weight(1f).padding(horizontal=4.dp)){
                    Text(s?.name?:"Open an audio file",style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(s!=null)Text("${s.rate/1000f} kHz · ${if(s.channels==1)"Mono" else "Stereo"}",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                AudioHint(if(waveformExpanded)"Back to tools"else"Expand waveform"){
                    IconButton(modifier=Modifier.size(48.dp),enabled=s!=null,onClick={waveformExpanded=!waveformExpanded}){if(waveformExpanded)Icon(Icons.Outlined.Close,"Back to audio tools")else Icon(painterResource(R.drawable.video_edit_fullscreen),"Expand waveform")}
                }
                AudioHint("Save a copy"){FilledTonalIconButton(modifier=Modifier.size(48.dp),enabled=s!=null&&!model.busy,onClick={export=true}){Icon(painterResource(R.drawable.audio_edit_savealt),"Save copy")}}
                Box{
                    AudioHint("More audio options"){IconButton(modifier=Modifier.size(48.dp),onClick={more=true}){Icon(Icons.Outlined.MoreVert,"More audio options")}}
                    DropdownMenu(more,{more=false}){
                        DropdownMenuItem(text={Text("Reset edits")},leadingIcon={Icon(painterResource(R.drawable.audio_edit_restartalt),null)},enabled=s!=null&&!model.busy,onClick={more=false;reset=true})
                        DropdownMenuItem(text={Text("Audio Edit help")},leadingIcon={Icon(painterResource(R.drawable.audio_edit_helpoutline),null)},onClick={more=false;help=true})
                    }
                }
            }
            if(s==null){
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Icon(painterResource(R.drawable.audio_edit_graphiceq),null,Modifier.size(56.dp),tint=colors.primary)
                    Spacer(Modifier.height(16.dp));Text("Make it sound right",style=MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp));Text("Trim, tune, and clean up your audio.",style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp));Button(enabled=!model.busy,onClick={picker.launch(arrayOf("audio/*","video/mp4"))}){Text("Open audio")}
                }
            }else{
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
                    val timeline:@Composable (Modifier)->Unit={modifier->
                        AudioTimeline(model,zoom,offset,modifier,onZoom={next->zoom=next;offset=offset.coerceAtMost(1-1/next)},onOffset={offset=it},onPrecise={precise=true})
                    }
                    val tools:@Composable (Modifier)->Unit={modifier->
                        AudioToolPanel(model,tab,tool,modifier,onTab={model.commit();tab=it},onTool={next->
                            model.commit()
                            tab=next.group
                            when(next.group){"Sound"->soundTool=next;"Clean"->cleanTool=next;else->trimTool=next}
                        })
                    }
                    if(waveformExpanded){
                        Column(Modifier.fillMaxSize().testTag("audio-expanded-waveform")){
                            timeline(Modifier.weight(1f).fillMaxWidth())
                            TextButton(onClick={waveformExpanded=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Back to tools")}
                        }
                    }else if(wide){
                        Row(Modifier.fillMaxSize().testTag("audio-side-by-side")){
                            Column(Modifier.weight(.46f).fillMaxHeight()){
                                timeline(Modifier.weight(1f).fillMaxWidth())
                                AudioTransport(model)
                            }
                            VerticalDivider()
                            tools(Modifier.weight(.54f).fillMaxHeight())
                        }
                    }else{
                        val minimumTools=184.dp*sqrt(LocalDensity.current.fontScale.coerceIn(1f,1.6f))
                        val timelineHeight=(maxHeight-minimumTools).coerceIn(0.dp,maxHeight*.42f)
                        Column(Modifier.fillMaxSize()){
                            if(timelineHeight>=96.dp)timeline(Modifier.height(timelineHeight).fillMaxWidth())
                            else FilledTonalButton(onClick={waveformExpanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp)){
                                Icon(painterResource(R.drawable.audio_edit_graphiceq),null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Waveform & selection")
                            }
                            HorizontalDivider(color=colors.outlineVariant)
                            tools(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
            if(!wide||s==null||waveformExpanded)AudioTransport(model)
        }
        }
    }
    if(precise&&s!=null){var start by remember{mutableStateOf("%.3f".format(java.util.Locale.US,model.selection.start*duration))};var end by remember{mutableStateOf("%.3f".format(java.util.Locale.US,model.selection.endInclusive*duration))};val a=start.toFloatOrNull();val b=end.toFloatOrNull();val valid=a!=null&&b!=null&&a>=0&&b<=duration+.001&&b>a
        AlertDialog(onDismissRequest={precise=false},title={Text("Selection")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(start,{start=it},label={Text("Start · seconds")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);OutlinedTextField(end,{end=it},label={Text("End · seconds")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)}},confirmButton={TextButton(enabled=valid,onClick={model.select((a!!/duration)..(b!!/duration).coerceAtMost(1f));precise=false}){Text("Apply")}},dismissButton={TextButton(onClick={precise=false}){Text("Cancel")}})
    }
    if(export){AlertDialog(onDismissRequest={export=false},title={Text("Save a copy")},text={Column(Modifier.verticalScroll(rememberScrollState())){FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(choice in listOf("MP3","M4A","WAV"))FilterChip(selected=format==choice,onClick={format=choice},label={Text(choice)})};Text(when(format){"MP3"->"Built-in MP3 encoder · up to 192 kbps";"M4A"->"Smaller file · AAC audio";else->"Uncompressed · 16-bit PCM"},style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(8.dp));Text("${audioTime(duration)} · All edits are included. Your original stays unchanged.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={export=false;val name=(s?.name?.substringBeforeLast('.')?:"Audio")+"-edited";when(format){"MP3"->mp3Save.launch("$name.mp3");"M4A"->aacSave.launch("$name.m4a");else->waveSave.launch("$name.wav")}}){Text("Save")}},dismissButton={TextButton(onClick={export=false}){Text("Cancel")}})}
    if(reset||replace)AlertDialog(onDismissRequest={reset=false;replace=false},title={Text(if(reset)"Reset all edits?" else "Open another file?")},text={Text(if(reset)"You can undo this reset." else "Save a copy first if you want to keep your current edits.")},confirmButton={TextButton(onClick={if(reset&&s!=null)model.apply(AudioEdit(listOf(AudioRegion(0,s.frames))))else picker.launch(arrayOf("audio/*","video/mp4"));reset=false;replace=false}){Text(if(reset)"Reset edits"else"Open audio")}},dismissButton={TextButton(onClick={reset=false;replace=false}){Text("Cancel")}})
    if(help)AlertDialog(onDismissRequest={help=false},title={Text("Audio Edit")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Open a recording or an audio file. Use the selection handles, or tap the selection times for precise start and end points. Zoom in for detail; the view position slider moves through the waveform. Expand waveform opens a focused selection view; Back to tools returns to editing.");Text("Trim: Edit contains Keep, Cut, Silence and Duplicate. Fades and Reverse have their own controls. Sound and Clean adjustments apply to the whole track. The tool panel scrolls independently; playback stays within reach.");Text("Sound: Adjust loudness, tone, pitch, and tempo. Normalize balances the overall level; Prevent clipping lowers overloads.");Text("Clean: Learn a noise-only sample, then raise Reduce noise gently. Rumble and DC controls remove low-frequency problems.");Text("Play auditions up to 12 seconds from the playhead inside your selection. Tap the waveform to hear another part; Play continues after a completed preview. Original compares sound without effects. Saving processes the entire track. Preview normalization uses the excerpt; final levels use the full track. Undo and Redo step through changes. Tap a value to enter an exact number. Hold an icon to see its label. An information button opens the full status or error message.");Text("Save exports the whole edited track as MP3, WAV or M4A. Your draft is kept privately. Opens mono/stereo files up to two hours. MP3 export is limited to two hours; slower tempo can make a longer WAV, within its file-size limit. MP3 uses the bundled software codec; other compressed imports depend on your phone. This is a single-track editor.")}},confirmButton={TextButton(onClick={help=false}){Text("Done")}})
}

/** Bounded independently from the tools, using the actual available window, not device height. */
@Composable private fun AudioTimeline(model:AudioEditorModel,zoom:Float,offset:Float,modifier:Modifier,onZoom:(Float)->Unit,onOffset:(Float)->Unit,onPrecise:()->Unit){
    val duration=model.duration;val colors=MaterialTheme.colorScheme;val window=1/zoom
    BoxWithConstraints(modifier.testTag("audio-timeline")){
        // The range slider and actions retain 48 dp touch targets; only the drawing shrinks.
        val waveHeight=(maxHeight-96.dp-(if(zoom>1)48.dp else 0.dp)).coerceIn(48.dp,160.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                TextButton(enabled=!model.busy,onClick=onPrecise,modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics{contentDescription="Edit selection times"},shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=8.dp)){
                    Column(Modifier.fillMaxWidth()){
                        Text("Selection",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                        if(LocalDensity.current.fontScale>1.3f){
                            Text("${audioTime(model.selection.start*duration)} →",style=MaterialTheme.typography.labelMedium,maxLines=1)
                            Text(audioTime(model.selection.endInclusive*duration),style=MaterialTheme.typography.labelMedium,maxLines=1)
                        }else Text("${audioTime(model.selection.start*duration)} – ${audioTime(model.selection.endInclusive*duration)}",style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
                AudioHint("Select all"){IconButton(modifier=Modifier.size(48.dp),enabled=!model.busy&&model.selection!=(0f..1f),onClick={model.select(0f..1f)}){Icon(painterResource(R.drawable.audio_edit_selectall),"Select all")}}
                AudioHint("Zoom out"){IconButton(modifier=Modifier.size(48.dp),enabled=!model.busy&&zoom>1,onClick={onZoom(max(1f,zoom/2))}){Icon(painterResource(R.drawable.audio_edit_zoomout),"Zoom out")}}
                AudioHint("Zoom in"){IconButton(modifier=Modifier.size(48.dp),enabled=!model.busy&&zoom<16,onClick={onZoom(min(16f,zoom*2))}){Icon(painterResource(R.drawable.audio_edit_zoomin),"Zoom in")}}
            }
            Canvas(Modifier.fillMaxWidth().height(waveHeight).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLow)
                .semantics{contentDescription="Audio waveform. Selection from ${audioTime(model.selection.start*duration)} to ${audioTime(model.selection.endInclusive*duration)}";setProgress{if(!model.busy){model.seek(it);true}else false};progressBarRangeInfo=ProgressBarRangeInfo(model.cursor,0f..1f)}
                .pointerInput(model.busy,zoom,offset){detectTapGestures{if(!model.busy)model.seek(offset+it.x/size.width/zoom)}}){
                val from=offset;val to=offset+window
                val a=((model.selection.start-from)/window*size.width).coerceIn(0f,size.width);val b=((model.selection.endInclusive-from)/window*size.width).coerceIn(0f,size.width)
                drawRect(colors.primary.copy(alpha=.14f),Offset(a,0f),androidx.compose.ui.geometry.Size(max(0f,b-a),size.height))
                drawLine(colors.outlineVariant,Offset(0f,size.height/2),Offset(size.width,size.height/2))
                val peaks=model.peaks;val bars=(size.width/3).toInt().coerceAtLeast(1)
                repeat(bars){i->val index=((from+i.toFloat()/bars*window)*peaks.size).toInt();val peak=peaks.getOrElse(index){0f};val x=i*size.width/bars;val h=max(1f,peak*(size.height*.43f));drawLine(colors.primary,Offset(x,size.height/2-h),Offset(x,size.height/2+h),2f)}
                if(model.selection.start in from..to)drawLine(colors.primary,Offset(a,0f),Offset(a,size.height),2f)
                if(model.selection.endInclusive in from..to)drawLine(colors.primary,Offset(b,0f),Offset(b,size.height),2f)
                val x=(model.cursor-from)/window*size.width;if(x in 0f..size.width)drawLine(colors.tertiary,Offset(x,0f),Offset(x,size.height),3f)
            }
            RangeSlider(value=model.selection,onValueChange=model::select,enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription="Selection start and end"})
            if(zoom>1)Slider(value=offset,onValueChange=onOffset,valueRange=0f..(1-1/zoom),enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription="Waveform view position"})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AudioToolPanel(model:AudioEditorModel,tab:String,tool:AudioTool,modifier:Modifier,onTab:(String)->Unit,onTool:(AudioTool)->Unit){
    val colors=MaterialTheme.colorScheme
    BoxWithConstraints(modifier.testTag("audio-tool-panel")){
    val condensed=maxHeight<180.dp
    Column(Modifier.fillMaxSize()){
        if(condensed){
            var choosing by remember{mutableStateOf(false)}
            Box(Modifier.fillMaxWidth().padding(horizontal=12.dp)){
                OutlinedButton(onClick={choosing=true},enabled=!model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription="Choose audio tool"}){
                    Text("$tab · ${tool.label}",Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(choosing,{choosing=false}){
                    AudioTool.entries.forEach{choice->DropdownMenuItem(text={Text("${choice.group} · ${choice.label}")},onClick={choosing=false;onTool(choice)})}
                }
            }
        }else{
        PrimaryTabRow(selectedTabIndex=listOf("Trim","Sound","Clean").indexOf(tab)){
            listOf("Trim","Sound","Clean").forEach{label->Tab(selected=tab==label,onClick={onTab(label)},enabled=!model.busy,text={Text(label)})}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=2.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            AudioTool.entries.filter{it.group==tab}.forEach{choice->
                Surface(
                    modifier=Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).selectable(selected=tool==choice,enabled=!model.busy,role=Role.Tab,onClick={onTool(choice)}),
                    shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,if(tool==choice)colors.secondaryContainer else colors.outlineVariant),
                    color=if(tool==choice)colors.secondaryContainer else colors.surface,
                    contentColor=if(tool==choice)colors.onSecondaryContainer else colors.onSurfaceVariant
                ){
                    Box(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=10.dp),contentAlignment=Alignment.Center){Text(choice.label,style=MaterialTheme.typography.labelLarge,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
                }
            }
        }
        }
        // A fresh scroll state for each selected tool always reveals its first control.
        key(tool){
            val scroll=rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth().testTag("audio-tool-viewport")){
                Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("audio-tool-scroll").padding(horizontal=12.dp).padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    AudioToolSettings(model,tool)
                }
                // A subtle overflow cue does not intercept touches or cover a control.
                if(scroll.canScrollForward)Surface(Modifier.align(Alignment.BottomCenter).size(28.dp,3.dp),shape=RoundedCornerShape(2.dp),color=colors.outlineVariant){}
            }
        }
    }
    }
}

@Composable private fun AudioToolSettings(model:AudioEditorModel,tool:AudioTool){
    val e=model.edit;val duration=model.duration;val enabled=!model.busy
    val note:@Composable (String)->Unit={Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    when(tool){
        AudioTool.Selection->{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                AudioEditAction("Keep",R.drawable.audio_edit_crop,enabled,Modifier.weight(1f)){model.operation("Trim")}
                AudioEditAction("Cut",R.drawable.audio_edit_contentcut,enabled,Modifier.weight(1f)){model.operation("Cut")}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                AudioEditAction("Silence",R.drawable.audio_edit_silence,enabled,Modifier.weight(1f)){model.operation("Silence")}
                AudioEditAction("Duplicate",R.drawable.video_edit_duplicate,enabled,Modifier.weight(1f)){model.operation("Duplicate")}
            }
            note("Keep retains the selection. Cut removes it. Silence mutes it; Duplicate repeats it.")
        }
        AudioTool.Fades->{
            AudioControl("Fade in",e.fadeIn,0f..min(30f,duration.coerceAtLeast(.000001f)),"${"%.1f".format(e.fadeIn)} s",enabled,{model.change(e.copy(fadeIn=it))},model::commit)
            AudioControl("Fade out",e.fadeOut,0f..min(30f,duration.coerceAtLeast(.000001f)),"${"%.1f".format(e.fadeOut)} s",enabled,{model.change(e.copy(fadeOut=it))},model::commit)
            note("Fades soften the beginning and end of the whole track.")
        }
        AudioTool.Reverse->{AudioToggle("Reverse playback",e.reverse,enabled){model.change(e.copy(reverse=it));model.commit()};note("Play the whole track backwards. Toggle off to restore the direction.")}
        AudioTool.Volume->{
            AudioControl("Volume",e.gain,-24f..24f,"${"%+.1f".format(e.gain)} dB",enabled,{model.change(e.copy(gain=it))},model::commit)
            AudioToggle("Normalize to −1 dB",e.normalize,enabled){model.change(e.copy(normalize=it));model.commit()}
            AudioToggle("Even out loud and quiet parts",e.compressor,enabled){model.change(e.copy(compressor=it));model.commit()}
            AudioToggle("Prevent clipping",e.limiter,enabled){model.change(e.copy(limiter=it));model.commit()}
            note(if(e.normalize)"Preview levels use this excerpt. Saving measures the entire track." else "Loudness adjustments apply to the whole track.")
        }
        AudioTool.Tone->{
            listOf("Bass" to e.bass,"Mid" to e.mid,"Treble" to e.treble).forEach{(name,value)->AudioControl(name,value,-12f..12f,"${"%+.1f".format(value)} dB",enabled,{v->model.change(when(name){"Bass"->e.copy(bass=v);"Mid"->e.copy(mid=v);else->e.copy(treble=v)})},model::commit)}
            note("Shape the low, middle and high frequencies of the whole track.")
        }
        AudioTool.Pitch->{
            AudioControl("Pitch",e.pitch,-12f..12f,"${"%+.1f".format(e.pitch)} semitones",enabled,{model.change(e.copy(pitch=it))},model::commit)
            AudioControl("Tempo",e.tempo,.5f..2f,"${(e.tempo*100).roundToInt()}%",enabled,{model.change(e.copy(tempo=it))},model::commit)
            note("Pitch changes the note; Tempo changes the speed. Output length · ${audioTime(duration)}")
        }
        AudioTool.Noise->{
            OutlinedButton(onClick=model::learnNoise,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Icon(if(model.noiseReady)painterResource(R.drawable.audio_edit_checkcircle)else painterResource(R.drawable.audio_edit_hearing),null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(model.noiseReady)"Relearn noise sample"else"Learn noise sample")
            }
            AudioControl("Reduce noise",e.noise,0f..1f,"${(e.noise*100).roundToInt()}%",enabled&&model.noiseReady,{model.change(e.copy(noise=it))},model::commit)
            note(if(model.noiseReady)"Sample ready. Increase gently; reduction applies to the whole track."else"Select a short section containing only background noise, then learn it to enable reduction.")
        }
        AudioTool.Gate->{AudioControl("Quiet background gate",e.gate,-80f..-20f,if(e.gate<=-80)"Off"else"${e.gate.roundToInt()} dB",enabled,{model.change(e.copy(gate=it))},model::commit);note("Lower the background during quiet passages. Start gently to keep soft words and notes.")}
        AudioTool.Filters->{
            AudioToggle("Remove low rumble",e.highPass,enabled){model.change(e.copy(highPass=it));model.commit()}
            AudioToggle("Remove DC offset",e.dc,enabled){model.change(e.copy(dc=it));model.commit()}
            note("Clean up low-frequency rumble and an off-center signal across the whole track.")
        }
    }
}

@Composable private fun AudioEditAction(label:String,icon:Int,enabled:Boolean,modifier:Modifier,onClick:()->Unit){
    FilledTonalButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)){
        Icon(painterResource(icon),null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(label)
    }
}

@Composable private fun AudioTransport(model:AudioEditorModel){
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.surfaceContainer,tonalElevation=2.dp){
        Column(Modifier.fillMaxWidth().padding(horizontal=8.dp).padding(top=4.dp,bottom=8.dp).testTag("audio-transport")){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(0.dp)){
                AudioHint("Compare original"){FilledTonalIconButton(modifier=Modifier.size(48.dp),enabled=model.source!=null&&!model.busy,onClick={model.preview(true)}){Icon(painterResource(R.drawable.audio_edit_comparearrows),"Compare original")}}
                AudioHint(if(model.busy)"Cancel processing"else if(model.playing)"Pause preview"else"Play preview"){
                FilledIconButton(enabled=model.source!=null||model.busy,onClick={if(model.busy)model.cancel()else model.preview()},modifier=Modifier.size(52.dp)){
                    Icon(if(model.busy)Icons.Outlined.Close else if(model.playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(model.busy)"Cancel processing"else if(model.playing)"Pause preview"else"Play preview")
                }
                }
                AudioHint("Stop and return to start"){IconButton(modifier=Modifier.size(48.dp),enabled=!model.busy&&(model.playing||model.cursor>0),onClick={model.stop();model.seek(0f)}){Icon(painterResource(R.drawable.audio_edit_stop),"Stop")}}
                Spacer(Modifier.weight(1f))
                AudioHint("Undo"){IconButton(modifier=Modifier.size(48.dp),enabled=model.canUndo&&!model.busy,onClick={model.history(true)}){Icon(painterResource(R.drawable.audio_edit_undo),"Undo")}}
                AudioHint("Redo"){IconButton(modifier=Modifier.size(48.dp),enabled=model.canRedo&&!model.busy,onClick={model.history(false)}){Icon(painterResource(R.drawable.audio_edit_redo),"Redo")}}
            }
            if(model.busy){
                if(model.progress<0)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(progress={model.progress},modifier=Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(top=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                AudioStatusMessage(model.status,model.error,Modifier.weight(1f))
                if(model.source!=null)Text("${audioTime(model.cursor*model.duration)} / ${audioTime(model.duration)}",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun AudioControl(title:String,value:Float,range:ClosedFloatingPointRange<Float>,display:String,enabled:Boolean,onValue:(Float)->Unit,onDone:()->Unit){
    val default=when(title){"Tempo"->1f;"Quiet background gate"->-80f;else->0f}
    var exact by rememberSaveable(title){mutableStateOf(false)}
    val neutral=abs(value-default)<.001f;val colors=MaterialTheme.colorScheme;val tint=if(neutral)colors.onSurfaceVariant else colors.primary
    Surface(shape=RoundedCornerShape(16.dp),color=colors.surfaceContainerLow){
        Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={exact=true},enabled=enabled,contentPadding=PaddingValues(horizontal=4.dp),modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="Set exact $title"}){
                    Text(display,style=MaterialTheme.typography.labelMedium,color=tint)
                    Spacer(Modifier.width(4.dp));Icon(Icons.Outlined.Edit,null,Modifier.size(16.dp),tint=tint)
                }
                AudioHint("Reset $title"){IconButton(onClick={onValue(default);onDone()},enabled=enabled&&!neutral,modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.audio_edit_restartalt),"Reset $title",Modifier.size(20.dp))}}
            }
            Box(Modifier.fillMaxWidth().height(48.dp)){
                Slider(value.coerceIn(range),onValueChange={onValue(if(abs(it-default)<(range.endInclusive-range.start)*.001f)default else it)},onValueChangeFinished=onDone,valueRange=range,enabled=enabled,modifier=Modifier.fillMaxWidth().semantics{contentDescription=title},colors=SliderDefaults.colors(thumbColor=tint,activeTrackColor=tint))
                Canvas(Modifier.fillMaxWidth().height(48.dp)){
                    val inset=12.dp.toPx();val x=inset+(size.width-inset*2)*(default-range.start)/(range.endInclusive-range.start)
                    drawLine(colors.onSurfaceVariant,Offset(x,size.height/2+9.dp.toPx()),Offset(x,size.height/2+14.dp.toPx()),2.dp.toPx())
                }
            }
        }
    }
    if(exact)AudioExactValueDialog(title,value,range,enabled,onDismiss={exact=false}){next->onValue(next);onDone();exact=false}
}

@Composable private fun AudioToggle(title:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(12.dp)).toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange).padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f))
        Switch(checked,onCheckedChange=null,enabled=enabled)
    }
}
