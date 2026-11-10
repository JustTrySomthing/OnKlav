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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Geometry gestures transform the image inside the fixed crop canvas. Other tabs pan/zoom
 * the inspection view. Pointer identity never depends on state modified by the gesture. */
@Composable internal fun PhotoCanvasPreview(bitmap:Bitmap,edit:PhotoEdit,geometry:Boolean,zoom:Float,pan:Offset,onView:(Float,Offset)->Unit,
 editable:Boolean=true,sourceWidth:Int=bitmap.width,sourceHeight:Int=bitmap.height,onGeometry:(PhotoEdit)->Unit={},onFinish:()->Unit={}){
 val border=MaterialTheme.colorScheme.outline
 val currentEdit by rememberUpdatedState(edit);val currentZoom by rememberUpdatedState(zoom);val currentPan by rememberUpdatedState(pan)
 val viewCallback by rememberUpdatedState(onView);val geometryCallback by rememberUpdatedState(onGeometry);val finishCallback by rememberUpdatedState(onFinish)
 Canvas(Modifier.fillMaxSize().semantics{contentDescription="Edited image preview";stateDescription="Canvas ${bitmap.width} by ${bitmap.height}; image rotation %.1f degrees".format(edit["angle"]+edit["turn"])}.pointerInput(sourceWidth,sourceHeight,geometry,editable){
  if(editable)awaitEachGesture{
   awaitFirstDown(requireUnconsumed=false)
   var state=currentEdit;var z=currentZoom;var p=currentPan;var changed=false;var passedSlop=false
   var totalPan=Offset.Zero;var totalZoom=1f
   try{do{
    val event=awaitPointerEvent()
    if(event.changes.any{it.isConsumed})break
    val delta=event.calculatePan();val factor=event.calculateZoom();val anchor=event.calculateCentroid(useCurrent=false)
    totalPan+=delta;totalZoom*=factor
    if(!passedSlop)passedSlop=totalPan.getDistance()>viewConfiguration.touchSlop||kotlin.math.abs(1-totalZoom)*event.calculateCentroidSize(useCurrent=false)>viewConfiguration.touchSlop
    if(passedSlop&&(delta!=Offset.Zero||factor!=1f)){
     if(geometry){state=photoGeometryGesture(state,sourceWidth,sourceHeight,Size(size.width.toFloat(),size.height.toFloat()),z,p,anchor,delta,factor);geometryCallback(state)}
     else{val next=(z*factor).coerceIn(.5f,6f);val origin=Offset(size.width/2f,size.height/2f);p=anchor-origin+(p-(anchor-origin))*(next/z)+delta;z=next;viewCallback(z,p)}
     changed=true;event.changes.forEach{if(it.position!=it.previousPosition)it.consume()}
    }
   }while(event.changes.any{it.pressed})}finally{if(changed&&geometry)finishCallback()}
  }
 }){
  val scale=min(size.width/bitmap.width,size.height/bitmap.height)*.94f*zoom
  val left=(size.width-bitmap.width*scale)/2+pan.x;val top=(size.height-bitmap.height*scale)/2+pan.y
  val right=left+bitmap.width*scale;val bottom=top+bitmap.height*scale
  val canvas=RectF(left,top,right,bottom)
  drawIntoCanvas{context->val c=context.nativeCanvas;val count=c.save();c.clipRect(canvas)
   val tile=14.dp.toPx();val paint=Paint();val rows=((bottom-top)/tile).toInt()+1;val columns=((right-left)/tile).toInt()+1
   for(y in maxOf(0,((-top)/tile).toInt())..minOf(rows,((size.height-top)/tile).toInt()+1))for(x in maxOf(0,((-left)/tile).toInt())..minOf(columns,((size.width-left)/tile).toInt()+1)){paint.color=if((x+y)%2==0)android.graphics.Color.rgb(38,40,44)else android.graphics.Color.rgb(49,51,55);c.drawRect(left+x*tile,top+y*tile,left+(x+1)*tile,top+(y+1)*tile,paint)}
   c.drawBitmap(bitmap,null,canvas,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG));c.restoreToCount(count)
  }
  drawRect(border,Offset(left,top),androidx.compose.ui.geometry.Size(right-left,bottom-top),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
  if(geometry)for(i in 1..2){val f=i/3f;drawLine(Color.White.copy(alpha=.22f),Offset(left+(right-left)*f,top),Offset(left+(right-left)*f,bottom));drawLine(Color.White.copy(alpha=.22f),Offset(left,top+(bottom-top)*f),Offset(right,top+(bottom-top)*f))}
 }
}

internal fun photoAdjustmentValue(e:PhotoEdit,key:String):Float=when(key){"scale"->kotlin.math.sqrt(e["scaleX"]*e["scaleY"]);"angle"->{var a=(e["angle"]+e["turn"])%360;while(a>180)a-=360;while(a< -180)a+=360;a};else->e[key]}
internal fun photoAdjustment(e:PhotoEdit,key:String,value:Float):PhotoEdit=when(key){
 "angle"->e.set("angle",value).set("turn",0f)
 "scale"->{val factor=value/photoAdjustmentValue(e,"scale").coerceAtLeast(.01f);e.set("scaleX",(e["scaleX"]*factor).coerceIn(.1f,3f)).set("scaleY",(e["scaleY"]*factor).coerceIn(.1f,3f))}
 else->e.set(key,value)
}

/** Gesture anchor is in preview pixels; translation is stored in full-source fractions. */
internal fun photoGeometryGesture(e:PhotoEdit,w:Int,h:Int,viewport:Size,viewZoom:Float,viewPan:Offset,anchor:Offset,pan:Offset,zoom:Float):PhotoEdit{
 val crop=photoCropBounds(w,h,e)
 val display=min(viewport.width/crop.width(),viewport.height/crop.height())*.94f*viewZoom
 if(!display.isFinite()||display<=0f||!zoom.isFinite()||zoom<=0f)return e
 val left=(viewport.width-crop.width()*display)/2+viewPan.x;val top=(viewport.height-crop.height()*display)/2+viewPan.y
 val pivot=Offset(crop.left+(anchor.x-left)/display,crop.top+(anchor.y-top)/display)
 val scale=photoAdjustment(e,"scale",(photoAdjustmentValue(e,"scale")*zoom).coerceIn(.1f,3f))
 val actualX=scale["scaleX"]/e["scaleX"];val actualY=scale["scaleY"]/e["scaleY"]
 val center=Offset(w*(.5f+e["moveX"]),h*(.5f+e["moveY"]))
 return scale.set("moveX",((pivot.x+(center.x-pivot.x)*actualX+pan.x/display)/w-.5f).coerceIn(-1f,1f))
  .set("moveY",((pivot.y+(center.y-pivot.y)*actualY+pan.y/display)/h-.5f).coerceIn(-1f,1f))
}
