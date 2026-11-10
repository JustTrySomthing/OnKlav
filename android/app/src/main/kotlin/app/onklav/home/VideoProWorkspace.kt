@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.onklav.shared.R
import kotlin.math.*

@Composable internal fun VideoPreviewCanvas(m:VideoEditorModel,automatic:Boolean,fullscreen:Boolean,openFullscreen:()->Unit,layerId:String?,modifier:Modifier=Modifier,allowTransform:Boolean=false){
    val c=m.clip;val p=m.project;val pose=c?.let{videoPose(it,(m.cursor-m.clipOffset).coerceIn(0,it.duration))}
    val current by rememberUpdatedState(c);val layer by rememberUpdatedState(p.layers.firstOrNull{it.id==layerId});val local by rememberUpdatedState((m.cursor-m.clipOffset).coerceAtLeast(0))
    VideoCanvasFrame(p,modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).semantics{contentDescription="Video preview"}){
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(c?.id,layerId,m.busy,allowTransform){if(!m.busy&&allowTransform)awaitEachGesture{awaitFirstDown(requireUnconsumed=false);var changed=false;var totalPan=Offset.Zero;var totalZoom=1f;try{do{val event=awaitPointerEvent();var pan=event.calculatePan();var zoom=event.calculateZoom();totalPan+=pan;totalZoom*=zoom
        if(!changed&&!videoPreviewPastSlop(totalPan,totalZoom,event.calculateCentroidSize(useCurrent=false),viewConfiguration.touchSlop)){if(event.changes.none{it.pressed})break;continue}
        if(!changed){m.beginTransform();pan=totalPan;zoom=totalZoom;changed=true}
        if(pan!=Offset.Zero||zoom!=1f){val l=layer;if(l!=null){if(l.clip.keyframes.isNotEmpty()){val q=videoPose(l.clip,(m.cursor-l.offset).coerceIn(0,l.clip.duration));m.updateLayerKey(l.id,q.copy(zoom=(q.zoom*zoom).coerceIn(.1f,4f),x=(q.x+pan.x/size.width*2).coerceIn(-1f,1f),y=(q.y-pan.y/size.height*2).coerceIn(-1f,1f)))}else m.overlay(l.copy(scale=(l.scale*zoom).coerceIn(.1f,1f),x=(l.x+pan.x/size.width*2).coerceIn(-1f,1f),y=(l.y-pan.y/size.height*2).coerceIn(-1f,1f)))}else current?.let{clip->val q=videoPose(clip,local.coerceAtMost(clip.duration));m.transform(q.copy(zoom=(q.zoom*zoom).coerceIn(.1f,4f),x=(q.x+pan.x/size.width*2).coerceIn(-1f,1f),y=(q.y-pan.y/size.height*2).coerceIn(-1f,1f)))};changed=true;event.changes.forEach{it.consume()}}}while(event.changes.any{it.pressed})}finally{if(changed)m.endTransform()}}},contentAlignment=Alignment.Center){
        if(m.player!=null&&!fullscreen)VideoPlayerView(m,Modifier.fillMaxSize())else{
            if(c!=null)m.thumbnails[c.file]?.let{b->Image(b.asImageBitmap(),"Source frame",Modifier.fillMaxSize().graphicsLayer{scaleX=(pose?.zoom?:1f)*(if(c.flipX)-1 else 1);scaleY=(pose?.zoom?:1f)*(if(c.flipY)-1 else 1);translationX=(pose?.x?:0f)*size.width/2;translationY=-(pose?.y?:0f)*size.height/2;rotationZ=-c.rotate.toFloat()-(pose?.rotation?:0f)})}
            p.layers.filter{m.cursor in it.offset until it.offset+it.clip.duration}.forEach{l->m.thumbnails[l.clip.file]?.let{bitmap->Image(bitmap.asImageBitmap(),"Overlay source ${l.clip.name}",Modifier.fillMaxSize(l.scale).graphicsLayer{translationX=l.x*size.width/(2*l.scale);translationY=-l.y*size.height/(2*l.scale);rotationZ=-l.rotation;alpha=l.opacity})}}
            Text(if(m.busy)"Preparing preview…"else if(m.error)"Source · Retry preview"else if(automatic&&!m.transforming)"Preparing preview…"else"Source · Play to preview",Modifier.align(Alignment.BottomCenter).padding(end=48.dp).background(Color.Black.copy(alpha=.6f)).padding(5.dp),color=Color.White,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick=openFullscreen,enabled=!m.busy,modifier=Modifier.align(Alignment.BottomEnd).size(48.dp).background(Color.Black.copy(alpha=.4f),RoundedCornerShape(12.dp))){Icon(painterResource(R.drawable.video_edit_fullscreen),"Fullscreen preview",tint=Color.White)}
        if (allowTransform) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            for (fraction in listOf(1f / 3f, 2f / 3f)) {
                val x = size.width * fraction
                val y = size.height * fraction
                drawLine(Color.White.copy(alpha = .45f), Offset(x, 0f), Offset(x, size.height))
                drawLine(Color.White.copy(alpha = .45f), Offset(0f, y), Offset(size.width, y))
            }
        }
        if(allowTransform)Text(if(layerId!=null)"Move & resize overlay"else"Move & resize clip",Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha=.6f)).padding(5.dp),color=Color.White,style=MaterialTheme.typography.labelSmall)
    }
    }
}

/** Only the visible timeline is drawn, so long projects never allocate a giant layout. */
@Composable internal fun VideoProTimeline(m:VideoEditorModel,zoom:Float,open:(String)->Unit,layer:(String)->Unit,compact:Boolean=false,clearLayer:()->Unit={},selectedLayerId:String?=null,modifier:Modifier=Modifier,trimEnabled:Boolean=false,interactive:Boolean=true){
    val p=m.project;val color=MaterialTheme.colorScheme;val current by rememberUpdatedState(p);val cursor by rememberUpdatedState(m.cursor)
    val bands=1+(if(p.clips.any{it.titles.isNotEmpty()})1 else 0)+p.layers.size+p.music.size
    val height=(30+64+(bands-1)*44).dp
    Column(modifier.fillMaxWidth().heightIn(max=if(compact)160.dp else 230.dp).verticalScroll(rememberScrollState())){
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(height).background(color.surfaceContainerLow,RoundedCornerShape(10.dp)).semantics{
            contentDescription="Timeline";stateDescription="${videoSelectionLabel(p,m.selected,m.selectedAudioId,selectedLayerId)}. ${p.clips.size} clips, ${p.layers.size} overlays, ${p.music.size} audio tracks. ${videoTime(m.cursor)}"
            progressBarRangeInfo=ProgressBarRangeInfo(m.cursor.toFloat(),0f..p.duration.toFloat().coerceAtLeast(1f))
            setProgress{if(interactive&&!m.busy){m.seek(it.toLong());true}else false}
            customActions=if(!interactive)emptyList()else listOf(CustomAccessibilityAction("Previous frame"){m.stepFrame(-1);true},CustomAccessibilityAction("Next frame"){m.stepFrame(1);true},CustomAccessibilityAction("Seek back one second"){m.seek(m.cursor-1000);true},CustomAccessibilityAction("Seek forward one second"){m.seek(m.cursor+1000);true},CustomAccessibilityAction("Previous clip"){m.choose((m.selected-1).coerceAtLeast(0));true},CustomAccessibilityAction("Next clip"){m.choose((m.selected+1).coerceAtMost(p.clips.lastIndex));true})
        }.pointerInput(zoom,m.busy,interactive){
            if(!m.busy&&interactive)detectTapGestures(onTap={point->val unit=40.dp.toPx()*zoom;val at=(cursor+(point.x-size.width/2)/unit*1000).toLong().coerceIn(0,current.duration);if(point.y<94.dp.toPx()){val i=current.clips.indices.lastOrNull{i->at in current.clipStart(i) until current.clipStart(i)+current.clips[i].duration}?:-1;if(i>=0){clearLayer();m.choose(i)};m.scrub(at,(10.dp.toPx()/unit*1000).toLong())}else{var y=94.dp.toPx();if(current.clips.any{it.titles.isNotEmpty()}){if(point.y<y+44.dp.toPx()){clearLayer();val index=current.clips.indices.lastOrNull{i->at in current.clipStart(i) until current.clipStart(i)+current.clips[i].duration};if(index!=null)m.choose(index);m.seek(at);open("Text");return@detectTapGestures};y+=44.dp.toPx()};for(l in current.layers){if(point.y<y+44.dp.toPx()){m.seek(at);layer(l.id);return@detectTapGestures};y+=44.dp.toPx()};val audio=current.music.getOrNull(((point.y-y)/44.dp.toPx()).toInt());if(audio!=null){clearLayer();m.selectAudio(audio.id);m.seek(at)};open("Audio")}},onLongPress={open("Edit")})
        }.pointerInput(zoom,m.busy,m.selected,trimEnabled,interactive){
            if(m.busy||!interactive)return@pointerInput
            var mode="scrub";var start=cursor;var delta=0f;var clip=m.clip;var dragOffset=0L
            detectHorizontalDragGestures(onDragStart={point->start=cursor;delta=0f;clip=m.clip;dragOffset=m.clipOffset;mode="scrub";val c=clip;if(trimEnabled&&c!=null&&point.y in 30.dp.toPx()..94.dp.toPx()){val unit=40.dp.toPx()*zoom;val left=size.width/2+(m.clipOffset-cursor)/1000f*unit;val right=left+c.duration/1000f*unit;if(abs(point.x-left)<18.dp.toPx()){mode="start"}else if(abs(point.x-right)<18.dp.toPx()){mode="end"}}},onDragEnd={if(mode!="scrub")m.commit()},onDragCancel={if(mode!="scrub")m.commit()}){event,movement->event.consume();delta+=movement;val ms=(delta/(40.dp.toPx()*zoom)*1000).toLong();val c=clip;if(mode=="scrub")m.scrub(start-ms,(10.dp.toPx()/(40.dp.toPx()*zoom)*1000).toLong())else if(c!=null){val edge=dragOffset+if(mode=="start")0 else c.duration;val adjusted=if(m.snapping&&edge+ms in 0..current.duration)videoTimelineSnap(current,edge+ms,(10.dp.toPx()/(40.dp.toPx()*zoom)*1000).toLong(),c.id)-edge else ms;val source=videoTrimSource(c,mode=="start",adjusted);m.edit(videoClampClip(if(mode=="start")c.copy(start=source)else c.copy(end=source)))}}
        }){
            val unit=40.dp.toPx()*zoom;val center=size.width/2;fun x(ms:Long)=center+(ms-m.cursor)/1000f*unit
            val textPaint=android.graphics.Paint(3).apply{this.color=android.graphics.Color.WHITE;textSize=10.dp.toPx()}
            val rulerPaint=android.graphics.Paint(textPaint).apply{this.color=color.onSurfaceVariant.toArgb()}
            val step=if(zoom>=1)1000L else if(zoom>=.5)2000L else 5000L
            val first=((m.cursor-center/unit*1000).toLong().coerceAtLeast(0)/step)*step;val end=(m.cursor+center/unit*1000).toLong().coerceAtMost(p.duration)
            var t=first;while(t<=end){val xx=x(t);drawLine(color.onSurfaceVariant.copy(alpha=.4f),Offset(xx,20.dp.toPx()),Offset(xx,27.dp.toPx()));drawIntoCanvas{it.nativeCanvas.drawText(videoTime(t).substringBefore('.'),xx+3.dp.toPx(),14.dp.toPx(),rulerPaint)};t+=step}
            var offset=0L
            p.clips.forEachIndexed{i,c->offset=p.clipStart(i);val l=x(offset);val r=x(offset+c.duration);val active=i==m.selected&&selectedLayerId==null&&m.selectedAudioId=="main"
                if(r>=0&&l<=size.width){drawRect(if(active)color.primaryContainer else color.surfaceContainerHigh,Offset(max(0f,l),30.dp.toPx()),Size((min(size.width,r)-max(0f,l)).coerceAtLeast(0f),60.dp.toPx()))
                    m.thumbnails[c.file]?.let{bitmap->drawIntoCanvas{canvas->val nc=canvas.nativeCanvas;nc.save();nc.clipRect(max(0f,l),31.dp.toPx(),min(size.width,r),89.dp.toPx());val tile=60.dp.toPx();var xx=l+floor((max(0f,l)-l)/tile)*tile;while(xx<min(r,size.width)){nc.drawBitmap(bitmap,null,android.graphics.RectF(xx,31.dp.toPx(),xx+tile,89.dp.toPx()),null);xx+=tile};nc.restore()}}
                    drawRect(Color.Black.copy(alpha=.45f),Offset(max(0f,l),72.dp.toPx()),Size((min(size.width,r)-max(0f,l)).coerceAtLeast(0f),18.dp.toPx()))
                    drawIntoCanvas{it.nativeCanvas.drawText("${i+1} · ${c.name}".take(35),max(0f,l)+5.dp.toPx(),85.dp.toPx(),textPaint)}
                    if(videoOverlap(p.clips,i)>0){val end=x(offset+videoOverlap(p.clips,i));drawRect(color.tertiary.copy(alpha=.35f),Offset(max(0f,l),31.dp.toPx()),Size((min(size.width,end)-max(0f,l)).coerceAtLeast(0f),58.dp.toPx()))}
                    if(active){drawRect(color.primary,Offset(max(1.dp.toPx(),l),30.dp.toPx()),Size((min(size.width-1.dp.toPx(),r)-max(1.dp.toPx(),l)).coerceAtLeast(1f),60.dp.toPx()),style=Stroke(1.5.dp.toPx()));if(trimEnabled)for(xx in listOf(l,r))if(xx in 0f..size.width)drawRect(color.primary,Offset(xx-4.dp.toPx(),30.dp.toPx()),Size(8.dp.toPx(),60.dp.toPx()))}
                    c.keyframes.forEach{k->val xx=x(offset+k.at);if(xx in 0f..size.width){val d=4.dp.toPx();drawPath(Path().apply{moveTo(xx,34.dp.toPx()-d);lineTo(xx+d,34.dp.toPx());lineTo(xx,34.dp.toPx()+d);lineTo(xx-d,34.dp.toPx());close()},color.tertiary)}}
                };offset+=c.duration
            }
            var top=96.dp.toPx()
            fun block(start:Long,duration:Long,label:String,tint:Color,active:Boolean=false){val l=max(0f,x(start));val r=min(size.width,x(start+duration));if(r>l){drawRoundRect(tint.copy(alpha=.65f),Offset(l,top),Size(r-l,40.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()));if(active)drawRoundRect(color.onSurface,Offset(l,top),Size(r-l,40.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),style=Stroke(2.dp.toPx()));drawIntoCanvas{it.nativeCanvas.drawText(label.take(36),l+5.dp.toPx(),top+25.dp.toPx(),textPaint)}}}
            if(p.clips.any{it.titles.isNotEmpty()}){for((i,c) in p.clips.withIndex()){offset=p.clipStart(i);for(title in c.titles)block(offset+title.start,title.end-title.start,title.text,color.tertiary);offset+=c.duration};top+=44.dp.toPx()}
            for(l in p.layers){block(l.offset,l.clip.duration,"Overlay · ${l.clip.name}",color.secondary,l.id==selectedLayerId);top+=44.dp.toPx()}
            for(a in p.music){block(a.offset,min(a.duration,(p.duration-a.offset).coerceAtLeast(0)),"Audio · ${a.name}",color.primary,a.id==m.selectedAudioId);top+=44.dp.toPx()}
            for(beat in p.beats){val xx=x(beat);if(xx in 0f..size.width)drawCircle(color.tertiary,3.dp.toPx(),Offset(xx,25.dp.toPx()))}
            drawLine(color.onSurface,Offset(center,22.dp.toPx()),Offset(center,size.height),4.dp.toPx());drawLine(color.primary,Offset(center,22.dp.toPx()),Offset(center,size.height),2.dp.toPx());drawCircle(color.primary,3.dp.toPx(),Offset(center,24.dp.toPx()))
        }
    }
}

@Composable internal fun VideoProInspector(m:VideoEditorModel,tool:String,layerId:String?,selectLayer:(String?)->Unit,addMedia:()->Unit,addAudio:()->Unit,record:()->Unit,importCaptions:()->Unit,saveCaptions:()->Unit,precise:()->Unit,editTitle:(VideoTitle)->Unit,help:()->Unit,importLut:()->Unit){
    val p=m.project;val activeLayer=p.layers.firstOrNull{it.id==layerId}.takeIf{tool=="Keyframes"};val c=activeLayer?.clip?:m.clip;val enabled=!m.busy
    if(c==null)return
    val targetOffset=activeLayer?.offset?:m.clipOffset;val at=(m.cursor-targetOffset).coerceIn(0,c.duration)
    val pages=when(tool){"Edit"->listOf("Trim","Arrange","More");"Speed"->listOf("Speed","Curve");"Transform"->listOf("Position","Rotate");"Keyframes"->listOf("Points","Zoom","Position","Rotation","Opacity","Easing");"Audio"->listOf("Tracks","Volume","Timing","Fades","Sound");"Overlay"->listOf("Layers","Position","Timing","Key","Mask");"Filters"->listOf("Looks","Adjust","LUT");else->emptyList()}
    var page by remember(tool,c.id,layerId){mutableStateOf(pages.firstOrNull().orEmpty())}
    Column(Modifier.fillMaxSize()){
    if(pages.isNotEmpty())VideoInspectorTabs(pages,page){page=it}
    key(page){VideoScrollableSettings(Modifier.fillMaxWidth().weight(1f)){
    when(tool){
        "Edit"->when(page){
            "Trim"->{
                if(!c.image){
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(videoTime(c.start), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = precise, enabled = enabled) { Text("Exact trim") }
                        Text(videoTime(c.end), style = MaterialTheme.typography.labelLarge)
                    }
                    VideoTrimFilmstrip(m.thumbnails[c.file], c.start, c.end, c.sourceMs)
                    RangeSlider(value=c.start.toFloat()..c.end.toFloat(),onValueChange={r->val a=r.start.toLong().coerceAtMost(c.end-100);m.edit(videoClampClip(c.copy(start=a,end=r.endInclusive.toLong().coerceAtLeast(a+100))))},onValueChangeFinished=m::commit,valueRange=0f..c.sourceMs.toFloat(),enabled=enabled,modifier=Modifier.semantics{contentDescription="Clip trim handles"})
                    Text("Drag the clip edges to trim · ${videoTime(c.duration)} kept",style=MaterialTheme.typography.bodySmall)
                }else VideoControl("Photo duration",c.duration/1000f,.1f..60f,"${c.duration/1000f} s",enabled,{val d=(it*1000).roundToLong();m.edit(videoClampClip(c.copy(sourceMs=d,start=0,end=d)))},m::commit)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){VideoTool("Split",R.drawable.audio_edit_contentcut,enabled&&at>=100&&at<=c.duration-100){m.operation("Split")};VideoTool("Duplicate",R.drawable.video_edit_duplicate,enabled){m.operation("Duplicate")}}
                if(at<100||at>c.duration-100)Text("Move the playhead inside this clip to split it.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                VideoDangerButton("Delete clip",enabled){m.operation("Delete")}
            }
            "Arrange"->{
                Text("Clip ${m.selected+1} of ${p.clips.size}",style=MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){VideoTool("Move earlier",R.drawable.video_edit_left,enabled&&m.selected>0){m.operation("Earlier")};VideoTool("Move later",R.drawable.video_edit_right,enabled&&m.selected<p.clips.lastIndex){m.operation("Later")};VideoTool("Freeze frame",R.drawable.video_pro_freeze,enabled&&!c.image){m.freeze()}}
            }
            else->{
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){VideoTool("Previous frame",R.drawable.video_edit_left,enabled&&m.cursor>0){m.stepFrame(-1)};VideoTool("Next frame",R.drawable.video_edit_right,enabled&&m.cursor<p.duration){m.stepFrame(1)}}
                VideoToggle("Snap to edits and beats",m.snapping,enabled,m::updateSnapping)
                VideoDangerButton("Reset clip effects",enabled){m.operation("Reset")}
                TextButton(onClick=help){Text("Editing guide")}
            }
        }
        "Speed"->{if(c.image)Text("Photos use a duration. Open Trim & split to change it.")else if(page=="Speed"){
            VideoControl("Playback speed",c.speed,.25f..4f,"%.2f×".format(c.speed),enabled,{m.edit(videoClampClip(c.copy(speed=it)))},m::commit)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Normal","Speed up","Slow down","Hero moment").forEach{name->AssistChip(onClick={m.edit(videoSpeedCurve(c,name));m.commit()},label={Text(name)})}}
        }else{VideoSpeedGraph(c,{m.edit(it)},m::commit);Text("Drag a point up to speed up or down to slow down.",style=MaterialTheme.typography.bodySmall)}}
        "Transform"->{if(page=="Position"){
            VideoControl("Zoom",c.zoom,.1f..4f,"%.0f%%".format(c.zoom*100),enabled,{m.edit(c.copy(zoom=it))},m::commit)
            VideoControl("Horizontal position",c.panX,-1f..1f,"%.0f%%".format(c.panX*100),enabled,{m.edit(c.copy(panX=it))},m::commit)
            VideoControl("Vertical position",c.panY,-1f..1f,"%.0f%%".format(c.panY*100),enabled,{m.edit(c.copy(panY=it))},m::commit)
        }else{VideoTool("Rotate 90°",R.drawable.video_edit_rotate,enabled){m.edit(c.copy(rotate=(c.rotate+90)%360));m.commit()};VideoToggle("Flip horizontal",c.flipX,enabled){m.edit(c.copy(flipX=it));m.commit()};VideoToggle("Flip vertical",c.flipY,enabled){m.edit(c.copy(flipY=it));m.commit()}}}
        "Keyframes"->{val pose=videoPose(c,at)
            val segment=c.keyframes.lastOrNull{it.at<=at}?:c.keyframes.firstOrNull()
            fun update(q:VideoKeyframe){if(activeLayer!=null)m.updateLayerKey(activeLayer.id,q)else m.updateKey(q)}
            when(page){
                "Points"->{
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){VideoTool("Add keyframe",R.drawable.video_pro_key,enabled){m.addKeyframe(activeLayer?.id)};VideoTool("Remove point",R.drawable.video_edit_delete,enabled&&c.keyframes.any{abs(it.at-at)<50}){val next=c.copy(keyframes=c.keyframes.filter{abs(it.at-at)>=50});if(activeLayer!=null)m.overlay(activeLayer.copy(clip=next))else m.edit(next);m.commit()}}
                    VideoControl("Playhead",at/1000f,0f..(c.duration/1000f).coerceAtLeast(.1f),videoTime(at),enabled,{m.seek(targetOffset+(it*1000).toLong())},{})
                    c.keyframes.forEach{k->TextButton(onClick={m.seek(targetOffset+k.at)}){Text("◇ ${videoTime(k.at)} · ${"%.0f".format(k.zoom*100)}%")}}
                }
                "Zoom"->VideoControl("Keyframe zoom",pose.zoom,.1f..4f,"%.0f%%".format(pose.zoom*100),enabled,{update(pose.copy(zoom=it))},m::commit)
                "Position"->{VideoControl("Keyframe X",pose.x,-1f..1f,"%.0f%%".format(pose.x*100),enabled,{update(pose.copy(x=it))},m::commit);VideoControl("Keyframe Y",pose.y,-1f..1f,"%.0f%%".format(pose.y*100),enabled,{update(pose.copy(y=it))},m::commit)}
                "Rotation"->VideoControl("Rotation",pose.rotation,-180f..180f,"%.0f°".format(pose.rotation),enabled,{update(pose.copy(rotation=it))},m::commit)
                "Opacity"->VideoControl("Opacity",pose.opacity,0f..1f,"%.0f%%".format(pose.opacity*100),enabled,{update(pose.copy(opacity=it))},m::commit)
                else->{
                    VideoChoice("Motion easing",segment?.easing?:"Linear",videoEasings.map{it to it},enabled){name->var next=c;if(next.keyframes.isEmpty())next=next.copy(motion=false,keyframes=listOf(videoPose(c,0),videoPose(c,c.duration)));val key=segment?:next.keyframes.first();next=next.copy(keyframes=next.keyframes.map{if(it.at==key.at)it.copy(easing=name,curveStart=0f,curveEnd=1f)else it});if(activeLayer!=null)m.overlay(activeLayer.copy(clip=next))else m.edit(next);m.commit()}
                    if(segment?.easing=="Custom")VideoBezierGraph(segment,{key->val next=c.copy(keyframes=c.keyframes.map{if(it.at==segment.at)key.copy(curveStart=0f,curveEnd=1f)else it});if(activeLayer!=null)m.overlay(activeLayer.copy(clip=next))else m.edit(next)},m::commit)
                    Text("Motion presets replace this clip's motion points.",style=MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Push in","Pull out","Pan left","Pan right").forEach{name->AssistChip(onClick={val first=when(name){"Pull out"->VideoKeyframe(0,1.25f,easing="Smooth");"Pan right"->VideoKeyframe(0,1.15f,x=-.15f,easing="Smooth");"Pan left"->VideoKeyframe(0,1.15f,x=.15f,easing="Smooth");else->VideoKeyframe(0,easing="Smooth")};val last=when(name){"Push in"->VideoKeyframe(c.duration,1.25f);"Pan right"->VideoKeyframe(c.duration,1.15f,x=.15f);"Pan left"->VideoKeyframe(c.duration,1.15f,x=-.15f);else->VideoKeyframe(c.duration)};val next=c.copy(motion=false,keyframes=listOf(first,last));if(activeLayer!=null)m.overlay(activeLayer.copy(clip=next))else m.edit(next);m.commit()},label={Text(name)})}}
                }
            }
        }
        "Text","Captions"->{
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                VideoTool(if(tool=="Captions")"Add caption"else"Add text",R.drawable.video_pro_text,enabled&&c.titles.size<200){editTitle(VideoTitle(start=at.coerceAtMost((c.duration-1000).coerceAtLeast(0)),end=min(c.duration,at.coerceAtMost((c.duration-1000).coerceAtLeast(0))+3000),caption=tool=="Captions"))}
                if(tool=="Text")VideoTool("Sticker / emoji",R.drawable.video_pro_sticker,enabled&&c.titles.size<200){editTitle(VideoTitle(text="✨",start=at.coerceAtMost((c.duration-1000).coerceAtLeast(0)),end=min(c.duration,at.coerceAtMost((c.duration-1000).coerceAtLeast(0))+3000),y=0f,size=2f,background=false))}
            }
            if(tool=="Captions"){OutlinedButton(onClick=importCaptions){Text("Import SRT / WebVTT")};OutlinedButton(onClick=saveCaptions,enabled=p.clips.any{clip->clip.titles.any{it.caption}}){Text("Export captions · SRT")};Text("Import timed subtitles or write captions yourself. Automatic speech transcription is not available.",style=MaterialTheme.typography.bodySmall)}
            c.titles.filter{tool!="Captions"||it.caption}.forEach{title->Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh,onClick={editTitle(title)}){Column(Modifier.fillMaxWidth().padding(12.dp)){Text(title.text,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${videoTime(title.start)} – ${videoTime(title.end)}",style=MaterialTheme.typography.labelSmall)}}}
            if(c.titles.isEmpty())Text("Add a title, caption, or emoji to this clip.",style=MaterialTheme.typography.bodySmall)
        }
        "Audio"->{
            val track=p.music.firstOrNull{it.id==m.selectedAudioId}
            when(page){
                "Tracks"->{
                    VideoChoice("Choose audio track",track?.name?:"Clip audio",listOf("main" to "Clip audio")+p.music.map{it.id to it.name},enabled,selectedId=track?.id?:"main"){m.selectAudio(it)}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        VideoAudioAction("Add audio",R.drawable.audio_edit_graphiceq,enabled&&p.music.size<4,Modifier.weight(1f),addAudio)
                        VideoAudioAction("Voiceover",R.drawable.video_pro_mic,enabled&&p.music.size<4,Modifier.weight(1f),record)
                        VideoAudioAction("Extract",R.drawable.video_pro_extract,enabled&&!c.image&&c.hasAudio&&p.music.size<4,Modifier.weight(1f)){m.extractAudio()}
                    }
                    if(p.music.size>=4)Text("Four audio tracks added. Remove one to add another.",style=MaterialTheme.typography.bodySmall)
                    if(c.image||!c.hasAudio)Text("Extract needs a video clip with recorded sound.",style=MaterialTheme.typography.bodySmall)
                    if(track!=null){TextButton(onClick={m.detectBeats(track.id)},enabled=enabled){Text("Find beats · first 10 min")};VideoDangerButton("Remove audio track",enabled){m.apply(p.copy(music=p.music.filter{it.id!=track.id}))}}
                    if(p.beats.isNotEmpty()){Text("${p.beats.size} approximate beats",style=MaterialTheme.typography.labelLarge);Row{VideoIcon(R.drawable.video_edit_left,"Previous beat"){m.seek(p.beats.lastOrNull{it<m.cursor-30}?:0)};VideoIcon(R.drawable.video_edit_right,"Next beat"){m.seek(p.beats.firstOrNull{it>m.cursor+30}?:p.duration)};VideoDangerButton("Clear beats",enabled){m.apply(p.copy(beats=emptyList()))}}}
                }
                "Volume"->{if(track==null){if(c.hasAudio&&!c.image){VideoToggle("Mute clip",c.volume==0f,enabled){m.edit(c.copy(volume=if(it)0f else 1f));m.commit()};VideoControl("Clip volume",c.volume,0f..2f,"${(c.volume*100).roundToInt()}%",enabled,{m.edit(c.copy(volume=it))},m::commit)}else VideoSettingsPrompt("This clip has no recorded sound.","Add or choose audio"){page="Tracks"}}
                    else{VideoControl("Volume",track.volume,0f..2f,"${(track.volume*100).roundToInt()}%",enabled,{m.music(track.copy(volume=it))},m::commit);VideoToggle("Narration track",track.narration,enabled){m.music(track.copy(narration=it,duck=if(it)false else track.duck));m.commit()};if(!track.narration)VideoToggle("Lower under narration",track.duck,enabled){m.music(track.copy(duck=it));m.commit()}}
                }
                "Timing"->{if(track==null)VideoSettingsPrompt("Timing applies to added music or voice tracks.","Choose audio track"){page="Tracks"}else{
                    var exact by remember(track.id){mutableStateOf(false)}
                    TextButton(onClick={exact=true},enabled=enabled){Text("Exact position · ${videoPreciseTime(track.offset)}")}
                    if(exact)VideoPositionDialog("Audio position",track.offset,(p.duration-100).coerceAtLeast(0),{m.music(track.copy(offset=it));m.commit();exact=false},{exact=false})
                    VideoControl("Starts in video",track.offset/1000f,0f..max(.1f,(p.duration-100)/1000f),videoTime(track.offset),enabled,{m.music(track.copy(offset=(it*1000).toLong()))},m::commit)
                    Text("Trim audio",style=MaterialTheme.typography.labelLarge)
                    RangeSlider(value=track.start.toFloat()..track.end.toFloat(),onValueChange={r->val a=r.start.toLong().coerceAtMost(track.end-100);m.music(track.copy(start=a,end=r.endInclusive.toLong().coerceAtLeast(a+100)))},onValueChangeFinished=m::commit,valueRange=0f..track.sourceMs.toFloat(),enabled=enabled,modifier=Modifier.semantics{contentDescription="Trim ${track.name}"})
                }}
                "Fades"->{if(track==null)VideoSettingsPrompt("Fades apply to added music or voice tracks.","Choose audio track"){page="Tracks"}else{VideoControl("Sound fade in",track.fadeIn/1000f,0f..min(5f,track.duration/1000f),"%.1f s".format(track.fadeIn/1000f),enabled,{m.music(track.copy(fadeIn=(it*1000).toLong()))},m::commit);VideoControl("Sound fade out",track.fadeOut/1000f,0f..min(5f,track.duration/1000f),"%.1f s".format(track.fadeOut/1000f),enabled,{m.music(track.copy(fadeOut=(it*1000).toLong()))},m::commit)}}
                else->{if(track==null)VideoSettingsPrompt("Sound adjustments apply to added music or voice tracks.","Choose audio track"){page="Tracks"}else{
                    VideoChoice("Audio tone",track.tone,videoAudioTones.map{it to it},enabled){m.music(track.copy(tone=it));m.commit()}
                    var property by remember(track.id){mutableStateOf("Noise cleanup")};VideoChoice("Sound adjustment",property,listOf("Noise cleanup","Pitch","Stereo balance").map{it to it}){property=it}
                    when(property){"Pitch"->VideoControl("Pitch",track.pitch,-12f..12f,"%+.1f semitones".format(track.pitch),enabled,{m.music(track.copy(pitch=it))},m::commit);"Stereo balance"->VideoControl("Stereo balance",track.pan,-1f..1f,"${(track.pan*100).roundToInt()}%",enabled,{m.music(track.copy(pan=it))},m::commit);else->VideoControl("Noise cleanup",track.noise,0f..1f,"${(track.noise*100).roundToInt()}%",enabled,{m.music(track.copy(noise=it))},m::commit)}
                    VideoToggle("Remove low rumble",track.rumble,enabled){m.music(track.copy(rumble=it));m.commit()}
                }}
            }
        }
        "Overlay"->{val l=p.layers.firstOrNull{it.id==layerId}
            when(page){
                "Layers"->{OutlinedButton(onClick=addMedia,enabled=enabled&&p.layers.size<4){Text("Add video or photo overlay")}
                    if(p.layers.isNotEmpty())VideoChoice("Choose overlay",l?.clip?.name?:"Select an overlay",p.layers.map{it.id to it.clip.name},enabled,selectedId=layerId){selectLayer(it)}
                    if(l!=null){FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){VideoTool("Split overlay",R.drawable.audio_edit_contentcut,enabled&&m.cursor-l.offset>=100&&m.cursor-l.offset<=l.clip.duration-100){m.layerOperation(l.id,"Split")};VideoTool("Duplicate overlay",R.drawable.video_edit_duplicate,enabled&&p.layers.size<4){m.layerOperation(l.id,"Duplicate")}}
                        VideoDangerButton("Delete overlay",enabled){m.apply(p.copy(layers=p.layers.filter{it.id!=l.id}));selectLayer(null)}
                        TextButton(onClick={selectLayer(null)}){Text("Deselect overlay")}}
                    if(p.layers.isEmpty())Text("Place a video or photo over your main clip.",style=MaterialTheme.typography.bodySmall)
                    if(p.layers.size>=4)Text("Four overlays added. Remove one to add another.",style=MaterialTheme.typography.bodySmall)
                }
                else->{if(l==null)VideoSettingsPrompt("Select an overlay to adjust it.","Choose overlay"){page="Layers"}else when(page){
                    "Position"->{var property by remember(l.id){mutableStateOf("Scale")};VideoChoice("Overlay adjustment",property,listOf("Scale","Horizontal","Vertical","Rotation","Opacity").map{it to it}){property=it}
                        when(property){"Scale"->VideoControl("Overlay scale",l.scale,.1f..1f,"%.0f%%".format(l.scale*100),enabled,{m.overlay(l.copy(scale=it))},m::commit);"Horizontal"->VideoControl("Overlay X",l.x,-1f..1f,"%.0f%%".format(l.x*100),enabled,{m.overlay(l.copy(x=it))},m::commit);"Vertical"->VideoControl("Overlay Y",l.y,-1f..1f,"%.0f%%".format(l.y*100),enabled,{m.overlay(l.copy(y=it))},m::commit);"Rotation"->VideoControl("Overlay rotation",l.rotation,-180f..180f,"%.0f°".format(l.rotation),enabled,{m.overlay(l.copy(rotation=it))},m::commit);else->VideoControl("Overlay opacity",l.opacity,0f..1f,"%.0f%%".format(l.opacity*100),enabled,{m.overlay(l.copy(opacity=it))},m::commit)}}
                    "Timing"->{VideoControl("Overlay starts",l.offset/1000f,0f..max(.1f,(p.duration-100)/1000f),videoTime(l.offset),enabled,{m.overlay(l.copy(offset=(it*1000).toLong()))},m::commit);Text("Trim overlay",style=MaterialTheme.typography.labelLarge);RangeSlider(value=l.clip.start.toFloat()..l.clip.end.toFloat(),onValueChange={r->val a=r.start.toLong().coerceAtMost(l.clip.end-100);m.overlay(l.copy(clip=videoClampClip(l.clip.copy(start=a,end=r.endInclusive.toLong().coerceAtLeast(a+100)))))},onValueChangeFinished=m::commit,valueRange=0f..l.clip.sourceMs.toFloat(),enabled=enabled)}
                    "Key"->{VideoToggle("Chroma key",l.chroma,enabled){m.overlay(l.copy(chroma=it));m.commit()};if(l.chroma){VideoChoice("Key color",if(l.keyColor==0xFF0000FF.toInt())"Blue"else"Green",listOf("Green" to "Green","Blue" to "Blue"),enabled){m.overlay(l.copy(keyColor=if(it=="Blue")0xFF0000FF.toInt()else 0xFF00FF00.toInt()));m.commit()};VideoControl("Key tolerance",l.keyTolerance,.05f.. .8f,"%.0f%%".format(l.keyTolerance*100),enabled,{m.overlay(l.copy(keyTolerance=it))},m::commit)}}
                    else->{VideoChoice("Overlay mask",listOf("None","Circle","Letterbox")[l.mask],listOf("0" to "None","1" to "Circle","2" to "Letterbox"),enabled,selectedId=l.mask.toString()){m.overlay(l.copy(mask=it.toInt()));m.commit()};if(l.mask>0)VideoControl("Mask feather",l.feather,0f.. .2f,"%.0f%%".format(l.feather*100),enabled,{m.overlay(l.copy(feather=it))},m::commit)}
                }}
            }
        }
        "Filters"->{
            when(page){
                "Looks"->{FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Original","Vivid","Warm","Cool","Mono","Soft").forEach{name->AssistChip(onClick={m.edit(when(name){"Vivid"->c.copy(brightness=.03f,contrast=.15f,saturation=25f,warmth=0f,hue=0f);"Warm"->c.copy(brightness=.02f,contrast=.05f,saturation=10f,warmth=.5f,hue=0f);"Cool"->c.copy(brightness=0f,contrast=.1f,saturation=0f,warmth=-.5f,hue=0f);"Mono"->c.copy(brightness=0f,contrast=.1f,saturation=-100f,warmth=0f,hue=0f);"Soft"->c.copy(brightness=.04f,contrast=-.1f,saturation=-15f,warmth=.1f,hue=0f);else->c.copy(brightness=0f,contrast=0f,saturation=0f,hue=0f,warmth=0f,lut="",lutName="",lutAmount=1f)});m.commit()},label={Text(name)})}}}
                "Adjust"->{var property by remember(c.id){mutableStateOf("Brightness")};VideoChoice("Color adjustment",property,listOf("Brightness","Contrast","Saturation","Warmth","Hue").map{it to it}){property=it}
                    when(property){"Brightness"->VideoControl("Brightness",c.brightness,-1f..1f,"%.0f".format(c.brightness*100),enabled,{m.edit(c.copy(brightness=it))},m::commit);"Contrast"->VideoControl("Contrast",c.contrast,-1f..1f,"%.0f".format(c.contrast*100),enabled,{m.edit(c.copy(contrast=it))},m::commit);"Saturation"->VideoControl("Saturation",c.saturation,-100f..100f,"%.0f".format(c.saturation),enabled,{m.edit(c.copy(saturation=it))},m::commit);"Warmth"->VideoControl("Warmth",c.warmth,-1f..1f,"%.0f".format(c.warmth*100),enabled,{m.edit(c.copy(warmth=it))},m::commit);else->VideoControl("Hue",c.hue,-180f..180f,"%.0f°".format(c.hue),enabled,{m.edit(c.copy(hue=it))},m::commit)}}
                else->{OutlinedButton(onClick=importLut,enabled=enabled){Text("Import .cube color look")};if(c.lut.isNotEmpty()){Text(c.lutName,style=MaterialTheme.typography.labelLarge);VideoControl("LUT strength",c.lutAmount,0f..1f,"${(c.lutAmount*100).roundToInt()}%",enabled,{m.edit(c.copy(lutAmount=it))},m::commit);TextButton(onClick={m.edit(c.copy(lut="",lutName="",lutAmount=1f));m.commit()}){Text("Remove imported look")}}else Text("SDR Rec.709 · 2–33 points per channel",style=MaterialTheme.typography.bodySmall)}
            }
            if(p.clips.size>1)VideoDangerButton("Apply color to all clips",enabled,"Replace color settings on all ${p.clips.size} main clips? Other edits are kept."){m.apply(p.copy(clips=p.clips.map{it.copy(brightness=c.brightness,contrast=c.contrast,saturation=c.saturation,hue=c.hue,warmth=c.warmth,lut=c.lut,lutName=c.lutName,lutAmount=c.lutAmount)}))}
        }
        "Transitions"->{
            if(m.selected==0)Text("Select the second or a later clip to blend it with the previous clip.")else{
                Text("Into this clip",style=MaterialTheme.typography.titleSmall)
                VideoChoice("Transition style",c.transition,videoTransitions.map{it to it},enabled){m.edit(c.copy(transition=it));m.commit()}
                VideoControl("Transition length",c.transitionMs/1000f,0f..2f,"%.2f s".format(videoOverlap(p.clips,m.selected)/1000f),enabled,{m.edit(c.copy(transitionMs=(it*1000).toLong()))},m::commit)
                Text("Overlaps both video and sound. Length is limited to half of each neighboring clip; project duration becomes shorter.",style=MaterialTheme.typography.bodySmall)
            }
        }
        "Performance"->{
            Text("Editing preview",style=MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(360,540,720).forEach{height->FilterChip(selected=p.previewHeight==height,onClick={m.apply(p.copy(previewHeight=height))},label={Text("${height}p")})}}
            OutlinedButton(onClick={m.makeProxy()},enabled=enabled&&!c.image){Text("Create 360p proxy for this source")}
            TextButton(onClick={m.clearProxies()},enabled=enabled){Text("Clear this project's proxy cache")}
            Text("Proxies reduce decoding load while editing. Original media is used for every export and project backup. Up to 30-minute sources; 1 GB cache per project. Missing proxies fall back to originals.",style=MaterialTheme.typography.bodySmall)
        }
        "Fades"->{
            Text("Fade through black",style=MaterialTheme.typography.titleSmall);Text("Use a soft opening or ending. These fades go through black; they do not overlap adjacent clips.",style=MaterialTheme.typography.bodySmall)
            VideoControl("Fade in",c.fadeIn/1000f,0f..min(5f,c.duration/1000f),"%.1f s".format(c.fadeIn/1000f),enabled,{m.edit(c.copy(fadeIn=(it*1000).toLong()))},m::commit)
            VideoControl("Fade out",c.fadeOut/1000f,0f..min(5f,c.duration/1000f),"%.1f s".format(c.fadeOut/1000f),enabled,{m.edit(c.copy(fadeOut=(it*1000).toLong()))},m::commit)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(0L,300L,600L,1000L).forEach{time->AssistChip(onClick={m.edit(c.copy(fadeIn=min(time,c.duration),fadeOut=min(time,c.duration)));m.commit()},label={Text(if(time==0L)"No fade"else"${time/1000f} s")})}}
        }
    }
}
    }}}

@Composable private fun VideoSpeedGraph(c:VideoClip,change:(VideoClip)->Unit,commit:()->Unit){
    val latest by rememberUpdatedState(c);val accent=MaterialTheme.colorScheme.primary;val line=MaterialTheme.colorScheme.outlineVariant
    fun anchors(clip:VideoClip)=FloatArray(5){i->videoRateAt(clip.start+(clip.end-clip.start)*i/4,clip.speed,clip.rates)}
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh,RoundedCornerShape(12.dp)).semantics{contentDescription="Speed curve"}.pointerInput(c.id,c.start,c.end,c.speed){var index=0;var speeds=anchors(latest);detectDragGestures(onDragStart={p->index=(p.x/size.width*4).roundToInt().coerceIn(0,4);speeds=anchors(latest)},onDragEnd=commit,onDragCancel=commit){event,_->event.consume();speeds[index]=(.25f*16f.pow(1-event.position.y/size.height)).coerceIn(.25f,4f);val clip=latest;val rates=(0 until 32).map{i->val x=i/31f*4;val a=x.toInt().coerceAtMost(3);val f=x-a;val rate=speeds[a]+(speeds[a+1]-speeds[a])*f;VideoRate(clip.start+(clip.end-clip.start)*i/32,(rate/clip.speed).coerceIn(.0625f,16f))};change(videoClampClip(clip.copy(rates=rates)))}}){
        val values=anchors(c);fun y(speed:Float)=size.height*(1-ln(speed/.25f)/ln(16f))
        drawLine(line,Offset(0f,y(1f)),Offset(size.width,y(1f)),1.dp.toPx());for(i in 0..3)drawLine(accent,Offset(size.width*i/4,y(values[i])),Offset(size.width*(i+1)/4,y(values[i+1])),2.dp.toPx());for(i in 0..4)drawCircle(accent,5.dp.toPx(),Offset((size.width*i/4).coerceIn(6.dp.toPx(),size.width-6.dp.toPx()),y(values[i]).coerceIn(6.dp.toPx(),size.height-6.dp.toPx())))
    }
}

internal class VideoToolbarState {
    var enabled by mutableStateOf(false);var canExport by mutableStateOf(false);var resolution by mutableStateOf("16:9")
    var projects:()->Unit={};var canvas:()->Unit={};var export:()->Unit={}
}
@Composable internal fun VideoHeaderButtons(state:VideoToolbarState){
    TextButton(enabled=state.enabled,onClick={state.canvas()},contentPadding=PaddingValues(4.dp),modifier=Modifier.semantics{contentDescription="Canvas settings"}){Text(state.resolution,style=MaterialTheme.typography.labelMedium)}
    VideoIcon(R.drawable.maps_folder,"Projects",state.enabled){state.projects()}
    AudioHint("Export video"){FilledTonalButton(enabled=state.canExport,onClick={state.export()},contentPadding=PaddingValues(horizontal=12.dp),shape=RoundedCornerShape(12.dp),modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="Export video"}){Text("Export",style=MaterialTheme.typography.labelMedium)}}
}

@Composable private fun VideoBezierGraph(key:VideoKeyframe,change:(VideoKeyframe)->Unit,commit:()->Unit){
    val current by rememberUpdatedState(key);val accent=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant
    Text("Drag either handle to shape the motion curve. The endpoints remain fixed.",style=MaterialTheme.typography.bodySmall)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh,RoundedCornerShape(12.dp)).semantics{contentDescription="Custom easing curve"}.pointerInput(key.at){var handle=0;detectDragGestures(onDragStart={p->val k=current;val a=Offset(k.cx1*size.width,(1-k.cy1)*size.height);val b=Offset(k.cx2*size.width,(1-k.cy2)*size.height);handle=if((p-a).getDistance()<(p-b).getDistance())0 else 1},onDragEnd=commit,onDragCancel=commit){event,_->event.consume();val x=(event.position.x/size.width).coerceIn(0f,1f);val y=(1-event.position.y/size.height).coerceIn(0f,1f);change(if(handle==0)current.copy(cx1=x,cy1=y)else current.copy(cx2=x,cy2=y))}}){
        val a=Offset(key.cx1*size.width,(1-key.cy1)*size.height);val b=Offset(key.cx2*size.width,(1-key.cy2)*size.height)
        drawLine(grid,Offset(0f,size.height),Offset(size.width,0f));drawLine(grid,Offset(0f,size.height),a);drawLine(grid,Offset(size.width,0f),b)
        drawPath(Path().apply{moveTo(0f,size.height);cubicTo(a.x,a.y,b.x,b.y,size.width,0f)},accent,style=Stroke(2.dp.toPx()));drawCircle(accent,7.dp.toPx(),a);drawCircle(accent,7.dp.toPx(),b)
    }
    VideoControl("Curve handle 1 X",key.cx1,0f..1f,"%.2f".format(key.cx1),true,{change(key.copy(cx1=it))},commit)
    VideoControl("Curve handle 1 Y",key.cy1,0f..1f,"%.2f".format(key.cy1),true,{change(key.copy(cy1=it))},commit)
    VideoControl("Curve handle 2 X",key.cx2,0f..1f,"%.2f".format(key.cx2),true,{change(key.copy(cx2=it))},commit)
    VideoControl("Curve handle 2 Y",key.cy2,0f..1f,"%.2f".format(key.cy2),true,{change(key.copy(cy2=it))},commit)
}

internal fun videoPreviewPastSlop(pan:Offset,zoom:Float,span:Float,slop:Float)=pan.getDistance()>slop||abs(zoom-1f)*span>slop

/** Source overview. The range slider immediately below owns the accessible trim handles. */
@Composable
private fun VideoTrimFilmstrip(bitmap: android.graphics.Bitmap?, start: Long, end: Long, duration: Long) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHigh)
    ) {
        bitmap?.let {
            drawIntoCanvas { canvas ->
                val tileWidth = size.width / 6f
                repeat(6) { tile ->
                    val left = tile * tileWidth
                    canvas.nativeCanvas.drawBitmap(it, null, android.graphics.RectF(left, 0f, left + tileWidth, size.height), null)
                }
            }
        }
        val source = duration.coerceAtLeast(1).toFloat()
        val left = size.width * (start / source).coerceIn(0f, 1f)
        val right = size.width * (end / source).coerceIn(0f, 1f)
        drawRect(Color.Black.copy(alpha = .58f), size = Size(left, size.height))
        drawRect(Color.Black.copy(alpha = .58f), Offset(right, 0f), Size(size.width - right, size.height))
        if (right > left) drawRect(colors.primary, Offset(left, 0f), Size(right - left, size.height), style = Stroke(2.dp.toPx()))
    }
}
