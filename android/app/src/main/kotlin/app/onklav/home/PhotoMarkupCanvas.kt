package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** The graded bitmap has no marks; the editable overlay and export share fixed canvas coordinates. */
@Composable internal fun PhotoMarkupCanvas(bitmap:Bitmap,edit:PhotoEdit,w:Int,h:Int,state:PhotoMarkupState,enabled:Boolean,zoom:Float,pan:Offset,onView:(Float,Offset)->Unit,onMarks:(List<PhotoMark>)->Unit){
 val ink=MaterialTheme.colorScheme.primary
 var provisional by remember{mutableStateOf<List<PhotoMark>?>(null)}
 val currentEdit by rememberUpdatedState(edit);val currentZoom by rememberUpdatedState(zoom);val currentPan by rememberUpdatedState(pan)
 val viewCallback by rememberUpdatedState(onView);val marksCallback by rememberUpdatedState(onMarks)
 Canvas(Modifier.fillMaxSize().semantics{contentDescription="Image tools canvas";stateDescription="${edit.marks.size} marks; ${state.tool} tool; ${(zoom*100).toInt()} percent zoom"}.pointerInput(w,h,edit,state.tool,enabled){
  if(enabled)awaitEachGesture{
   val down=awaitFirstDown(requireUnconsumed=false);val start=currentEdit;var z=currentZoom;var p=currentPan
   var projection=photoMarkProjection(w,h,start,Size(size.width.toFloat(),size.height.toFloat()),z,p)
   val origin=projection.unmap(down.position);val inside=projection.canvas.contains(down.position.x,down.position.y)&&origin!=null&&origin.x in 0f..1f&&origin.y in 0f..1f
   var marks=start.marks;var stroke:PhotoMark?=null;var selected:PhotoMark?=null;var multi=false;var changed=false;var moving=false
   val tool=state.tool;var totalPan=Offset.Zero
   if(inside){when(tool){
    "pen","highlight"->{stroke=PhotoMark(tool=tool,color=state.color,width=state.width/360*(if(tool=="highlight")4 else 1),points=listOf(origin!!));provisional=marks+stroke;state.selected=null}
    "move"->{selected=projection.hit(marks,down.position,20.dp.toPx());state.selected=selected?.id}
    "erase"->{val hit=projection.hit(marks,down.position,18.dp.toPx());if(hit!=null){marks=marks.filter{it.id!=hit.id};changed=true;provisional=marks;state.selected=null}}
   }}
   try{do{
    val event=awaitPointerEvent();if(event.changes.any{it.isConsumed})break
    if(event.changes.count{it.pressed}>1){multi=true;stroke=null;selected=null;provisional=null;changed=false;marks=start.marks}
    val delta=event.calculatePan();val factor=event.calculateZoom();val center=event.calculateCentroid(useCurrent=false)
    if(multi){
     if(event.changes.count{it.pressed}>1){val next=(z*factor).coerceIn(.5f,6f);val middle=Offset(size.width/2f,size.height/2f);p=center-middle+(p-(center-middle))*(next/z)+delta;z=next;viewCallback(z,p)}
    }else{
     val change=event.changes.firstOrNull{it.id==down.id};val at=change?.position?:down.position
     totalPan+=delta;if(totalPan.getDistance()>viewConfiguration.touchSlop)moving=true
     val source=projection.unmap(at)
     if(change?.pressed==true){
      if(stroke!=null&&source!=null&&stroke!!.points.size<2000){val point=Offset(source.x.coerceIn(0f,1f),source.y.coerceIn(0f,1f));if((point-stroke!!.points.last()).getDistance()>.0005f){stroke=stroke!!.copy(points=stroke!!.points+point);provisional=marks+stroke!!}}
      else if(tool=="move"&&selected!=null&&source!=null&&origin!=null&&moving){val moved=PhotoMarkup.move(selected!!,source-origin,w,h);marks=start.marks.map{if(it.id==moved.id)moved else it};changed=marks!=start.marks;provisional=marks}
      else if(tool=="move"&&selected==null&&moving){p+=delta;viewCallback(z,p)}
      else if(tool=="erase"&&inside){projection.hit(marks,at,18.dp.toPx())?.let{hit->marks=marks.filter{it.id!=hit.id};changed=true;provisional=marks}}
     }
     if(change!=null&&!change.pressed&&change.previousPressed){
      if(stroke!=null){PhotoMarkup.validate(marks+stroke!!);marksCallback(marks+stroke!!);state.selected=stroke!!.id}
      else if(changed)marksCallback(marks)
      else if(tool=="text"&&inside&&!moving&&source!=null){val hit=projection.hit(marks,at,20.dp.toPx());if(hit?.tool=="text")state.edit(hit)else{state.editing=null;state.selected=null;state.anchor=Offset(source.x.coerceIn(0f,1f),source.y.coerceIn(0f,1f))}}
     }
    }
    if(inside||multi||tool=="move")event.changes.forEach{if(it.position!=it.previousPosition)it.consume()}
   }while(event.changes.any{it.pressed})}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){state.error=e.message}finally{provisional=null}
  }
 }){
  val scale=min(size.width/bitmap.width,size.height/bitmap.height)*.94f*zoom
  val left=(size.width-bitmap.width*scale)/2+pan.x;val top=(size.height-bitmap.height*scale)/2+pan.y
  val rect=RectF(left,top,left+bitmap.width*scale,top+bitmap.height*scale)
  val projection=photoMarkProjection(w,h,edit,size,zoom,pan)
  drawIntoCanvas{ctx->val c=ctx.nativeCanvas;val save=c.save();c.clipRect(rect);c.drawColor(android.graphics.Color.rgb(36,39,44));c.drawBitmap(bitmap,null,rect,Paint(3))
   c.translate(left,top);c.scale(scale,scale)
   val marks=provisional?:edit.marks
   val textPreview=if(state.tool=="text"&&state.text.isNotBlank())runCatching{state.note(w,h)}.getOrNull()else null
   PhotoMarkup.drawOnPhoto(c,edit,w,h,if(textPreview==null)marks else marks.filter{it.id!=state.editing}+textPreview)
   c.restoreToCount(save)
  }
  if(state.tool=="move"){
   (provisional?:edit.marks).firstOrNull{it.id==state.selected}?.let{mark->val b=PhotoMarkup.bounds(mark,w,h).apply{inset(-.008f,-.008f)};val points=listOf(Offset(b.left,b.top),Offset(b.right,b.top),Offset(b.right,b.bottom),Offset(b.left,b.bottom)).map(projection::map)
    val path=androidx.compose.ui.graphics.Path().apply{moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)};close()}
    drawPath(path,ink,style=Stroke(1.3.dp.toPx(),pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx()))))
   }
  }
  drawRect(Color.White.copy(alpha=.18f),Offset(left,top),Size(rect.width(),rect.height()),style=Stroke(1.dp.toPx()))
 }
}
