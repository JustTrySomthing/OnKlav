package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal class PhotoRetouchState{
 var tool by mutableStateOf("redEye")
 var width by mutableFloatStateOf(.025f)
 var hardness by mutableFloatStateOf(.9f)
 var error by mutableStateOf<String?>(null)
 var source by mutableStateOf<Offset?>(null)
 var alignedOffset by mutableStateOf<Offset?>(null)
 var sampling by mutableStateOf(true)
}
internal fun photoRetouchPoint(point:Offset,w:Int,h:Int,edit:PhotoEdit,viewport:Size,zoom:Float,pan:Offset):Offset?{
 val projection=photoMarkProjection(w,h,edit,viewport,zoom,pan)
 if(!projection.canvas.contains(point.x,point.y))return null
 val fixed=projection.unmap(point)?:return null
 val inverse=Matrix();if(!photoGeometry(w,h,edit).matrix.invert(inverse))return null
 val xy=floatArrayOf(fixed.x*w,fixed.y*h);inverse.mapPoints(xy)
 val source=Offset(xy[0]/w,xy[1]/h)
 return source.takeIf{it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..1f&&it.y in 0f..1f}
}
@Composable internal fun PhotoRetouchCanvas(bitmap:Bitmap,edit:PhotoEdit,w:Int,h:Int,state:PhotoRetouchState,enabled:Boolean,zoom:Float,pan:Offset,onView:(Float,Offset)->Unit,apply:(PhotoRetouchStroke)->Unit){
 val currentEdit by rememberUpdatedState(edit);val currentZoom by rememberUpdatedState(zoom);val currentPan by rememberUpdatedState(pan)
 val accent=MaterialTheme.colorScheme.primary
 val callback by rememberUpdatedState(apply);val view by rememberUpdatedState(onView)
 var cursor by remember{mutableStateOf<Offset?>(null)}
 var donorCursor by remember{mutableStateOf<Offset?>(null)}
 var cursorRadius by remember{mutableFloatStateOf(10f)}
 Box(Modifier.fillMaxSize()){
  PhotoCanvasPreview(bitmap,edit,false,zoom,pan,{_,_->},editable=false)
  Canvas(Modifier.fillMaxSize().semantics{contentDescription="Retouch canvas";stateDescription="${when(state.tool){"redEye"->"Red eye";"clone"->if(state.sampling)"Clone sample"else"Clone";else->"Healing"}} brush; ${edit.retouch.size} corrections; ${(zoom*100).toInt()} percent zoom"}.pointerInput(w,h,state.tool,state.width,state.hardness,state.sampling,enabled){
   if(enabled)awaitEachGesture{
    val down=awaitFirstDown(requireUnconsumed=false);val start=currentEdit;val sampling=state.tool=="clone"&&state.sampling;var z=currentZoom;var p=currentPan;val viewport=Size(size.width.toFloat(),size.height.toFloat())
    val projection=photoMarkProjection(w,h,start,viewport,z,p)
    val diameter=state.width*min(w,h);val a=projection.map(Offset.Zero);val b=projection.map(Offset(diameter/w,0f));cursorRadius=(b-a).getDistance()/2
    // Brush size is in source coordinates; geometry transforms the cursor radius locally.
    val geom=photoGeometry(w,h,start).matrix
    fun screenRadius(source:Offset):Float{val xy=floatArrayOf(source.x*w,source.y*h,(source.x*w+diameter/2),source.y*h);geom.mapPoints(xy);return (projection.map(Offset(xy[2]/w,xy[3]/h))-projection.map(Offset(xy[0]/w,xy[1]/h))).getDistance()}
    var points=listOfNotNull(photoRetouchPoint(down.position,w,h,start,viewport,z,p));var multi=false;var cancelled=false
    if(points.isNotEmpty()){donorCursor=if(state.tool=="clone"&&!sampling)points.first()+(state.alignedOffset?:((state.source?:points.first())-points.first()))else null;cursor=down.position;cursorRadius=screenRadius(points.first());state.error=null}
    try{do{
     val event=awaitPointerEvent();if(event.changes.any{it.isConsumed}){cancelled=true;break}
     if(event.changes.count{it.pressed}>1){multi=true;points=emptyList();cursor=null;donorCursor=null}
     if(multi){if(event.changes.count{it.pressed}>1){val factor=event.calculateZoom();val delta=event.calculatePan();val anchor=event.calculateCentroid(useCurrent=false);val next=(z*factor).coerceIn(.5f,8f);val middle=Offset(size.width/2f,size.height/2f);p=anchor-middle+(p-(anchor-middle))*(next/z)+delta;z=next;view(z,p)}}
     else if(points.isNotEmpty()){
      val change=event.changes.firstOrNull{it.id==down.id};val point=change?.position
      if(point!=null){cursor=point;val source=photoRetouchPoint(point,w,h,start,viewport,z,p)
       if(source!=null){if(state.tool=="clone"&&!sampling)donorCursor=source+(state.alignedOffset?:((state.source?:points.first())-points.first()));cursorRadius=screenRadius(source);val last=points.last();val distance=hypot((source.x-last.x)*w,(source.y-last.y)*h);val spacing=max(1f,diameter*.2f)
        val steps=floor(distance/spacing).toInt()
        if(steps>0){check(points.size+steps<=256){"Use shorter strokes for precise retouching."};points=points+(1..steps).map{i->last+(source-last)*(i.toFloat()/steps)}}
       }
      }
     }
     if(points.isNotEmpty()||multi)event.changes.forEach{if(it.position!=it.previousPosition)it.consume()}
    }while(event.changes.any{it.pressed})
    if(!multi&&!cancelled&&points.isNotEmpty()){
     if(sampling){state.source=points.first();state.alignedOffset=null;state.sampling=false;state.error=null}
     else{val source=if(state.tool=="clone")state.alignedOffset?.let{points.first()+it}?:state.source else null
      if(state.tool=="clone")check(source!=null&&source.x in 0f..1f&&source.y in 0f..1f){"Source is outside the image · tap Sample to choose again"}
      val stroke=PhotoRetouchStroke(state.tool,state.width,points,source,state.hardness);PhotoRetouch.validate(start.retouch+stroke);callback(stroke)
      if(state.tool=="clone"&&state.alignedOffset==null)state.alignedOffset=requireNotNull(source)-points.first()
     }
    }
    }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){state.error=e.message?:"Unable to apply correction"}finally{cursor=null;donorCursor=null}
   }
  }){
   if(state.tool=="clone") (donorCursor?:state.source)?.let{source->
    val xy=floatArrayOf(source.x*w,source.y*h);photoGeometry(w,h,edit).matrix.mapPoints(xy)
    val target=photoMarkProjection(w,h,edit,Size(size.width,size.height),zoom,pan).map(Offset(xy[0]/w,xy[1]/h));val arm=7.dp.toPx()
    drawCircle(Color.Black.copy(alpha=.7f),10.dp.toPx(),target,style=Stroke(3.dp.toPx()));drawCircle(accent,10.dp.toPx(),target,style=Stroke(1.5.dp.toPx()))
    drawLine(accent,target-Offset(arm,0f),target+Offset(arm,0f),2.dp.toPx());drawLine(accent,target-Offset(0f,arm),target+Offset(0f,arm),2.dp.toPx())
   }
   cursor?.let{drawCircle(Color.Black.copy(alpha=.65f),cursorRadius+1.dp.toPx(),it,style=Stroke(3.dp.toPx()));drawCircle(Color.White,cursorRadius,it,style=Stroke(1.5.dp.toPx()))}}
 }
}
@Composable internal fun PhotoRetouchPanel(state:PhotoRetouchState,enabled:Boolean){
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("redEye" to "Red eye","clone" to "Clone","heal" to "Heal").forEach{(key,label)->FilterChip(selected=state.tool==key,onClick={state.tool=key;state.error=null;if(key=="clone"&&state.source==null)state.sampling=true},enabled=enabled,label={Text(label)},modifier=Modifier.heightIn(min=32.dp).semantics{contentDescription="$label brush"})}
  }
  PhotoDefaultSlider("Brush size",state.width,.025f,.006f.. .06f,enabled,{state.width=it},{},format={"${(it*1000).roundToInt()}"})
  if(state.tool=="clone")PhotoDefaultSlider("Hardness",state.hardness,.9f,0f..1f,enabled,{state.hardness=it},{},format={"${(it*100).roundToInt()}%"})
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Text(when(state.tool){"redEye"->"Zoom in, then brush over the red pupil";"clone"->if(state.sampling)"Tap the image to sample a source"else"Paint to copy · crosshair marks the source";else->"Tap spots or use short strokes · pinch to zoom"},Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(state.tool=="clone")TextButton(onClick={state.sampling=true;state.source=null;state.alignedOffset=null;state.error=null},enabled=enabled,modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="Sample stamp source"}){Text("Sample")}
  }
  state.error?.let{Text(it,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)}
 }
}
