package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.*

internal val photoCropKeys=setOf("left","right","top","bottom")

/** Identical pixel rounding for export, context painting, handles and inverse pointer mapping. */
internal fun photoCropBounds(w:Int,h:Int,e:PhotoEdit):android.graphics.Rect {
 val l=(e["left"]*w).toInt().coerceIn(0,w-1);val t=(e["top"]*h).toInt().coerceIn(0,h-1)
 val cw=(w*(1-e["left"]-e["right"])).toInt().coerceIn(1,w-l)
 val ch=(h*(1-e["top"]-e["bottom"])).toInt().coerceIn(1,h-t)
 return android.graphics.Rect(l,t,l+cw,t+ch)
}

/** Surroundings stay available for expansion; the selected patch uses the exact export grading,
 * including crop-relative vignette/looks/sharpen. Geometry is applied once by the shared matrix. */
internal fun photoCropContext(src:Bitmap,e:PhotoEdit):Bitmap {
 val out=PhotoRender.render(src,e.copy(values=e.values.filterKeys{it !in photoCropKeys}))
 val crop=photoCropBounds(src.width,src.height,e)
 if(crop.left==0&&crop.top==0&&crop.width()==src.width&&crop.height()==src.height)return out
 try {
  val selected=PhotoRender.render(src,e)
  try{BitmapCanvas(out).drawBitmap(selected,crop.left.toFloat(),crop.top.toFloat(),Paint().apply{xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC)})}
  finally{selected.recycle()}
  return out
 }catch(t:Throwable){out.recycle();throw t}
}

internal data class PhotoCropProjection(val matrix:Matrix,val inverse:Matrix,val sourceWidth:Int,val sourceHeight:Int){
 fun map(p:Offset):Offset {val a=floatArrayOf(p.x,p.y);matrix.mapPoints(a);return Offset(a[0],a[1])}
 fun unmap(p:Offset):Offset? {val a=floatArrayOf(p.x,p.y);inverse.mapPoints(a);return if(a.all{it.isFinite()})Offset(a[0],a[1])else null}
 fun corners(e:PhotoEdit):List<Offset>{val c=photoCropBounds(sourceWidth,sourceHeight,e);return listOf(Offset(c.left.toFloat(),c.top.toFloat()),Offset(c.right.toFloat(),c.top.toFloat()),Offset(c.right.toFloat(),c.bottom.toFloat()),Offset(c.left.toFloat(),c.bottom.toFloat())).map(::map)}
 fun handles(e:PhotoEdit):List<Pair<String,Offset>>{
  val c=photoCropBounds(sourceWidth,sourceHeight,e);val l=c.left.toFloat();val t=c.top.toFloat();val r=c.right.toFloat();val b=c.bottom.toFloat()
  return listOf("lt" to Offset(l,t),"rt" to Offset(r,t),"rb" to Offset(r,b),"lb" to Offset(l,b),"t" to Offset((l+r)/2,t),"r" to Offset(r,(t+b)/2),"b" to Offset((l+r)/2,b),"l" to Offset(l,(t+b)/2)).map{it.first to map(it.second)}
 }
}

/** Extend the cropped export's geometry into its surroundings, without changing saved parameters.
 * An extreme perspective may have a horizon outside the selection: keep that undefined area out
 * of the fit calculation rather than letting it collapse the selection to a dot. */
internal fun photoCropProjection(w:Int,h:Int,e:PhotoEdit,viewport:IntSize):PhotoCropProjection? {
 if(w<=0||h<=0||viewport.width<=0||viewport.height<=0)return null
 val scale=min(viewport.width.toFloat()/w,viewport.height.toFloat()/h)*.94f
 val matrix=Matrix().apply{setScale(scale,scale);postTranslate((viewport.width-w*scale)/2,(viewport.height-h*scale)/2)}
 val inverse=Matrix();check(matrix.invert(inverse))
 return PhotoCropProjection(matrix,inverse,w,h)
}

private fun cropSegmentDistance(p:Offset,a:Offset,b:Offset):Float {
 val v=b-a;val length=v.x*v.x+v.y*v.y
 val t=if(length<=.0001f)0f else (((p-a).x*v.x+(p-a).y*v.y)/length).coerceIn(0f,1f)
 return (p-(a+v*t)).getDistance()
}
internal fun photoCropHit(p:Offset,e:PhotoEdit,projection:PhotoCropProjection,radius:Float):String {
 val handles=projection.handles(e)
 handles.minByOrNull{(it.second-p).getDistance()}?.takeIf{(it.second-p).getDistance()<=radius}?.let{return it.first}
 val q=projection.corners(e);val edge=(0..3).minBy{cropSegmentDistance(p,q[it],q[(it+1)%4])}
 if(cropSegmentDistance(p,q[edge],q[(edge+1)%4])<=radius)return listOf("t","r","b","l")[edge]
 val source=projection.unmap(p)?:return ""
 val c=photoCropBounds(projection.sourceWidth,projection.sourceHeight,e)
 return if(source.x>=c.left&&source.x<=c.right&&source.y>=c.top&&source.y<=c.bottom)"move"else ""
}
internal fun photoCropDrag(start:PhotoEdit,handle:String,origin:Offset,point:Offset,projection:PhotoCropProjection):PhotoEdit {
 val a=projection.unmap(origin)?:return start;val b=projection.unmap(point)?:return start
 return movePhotoCrop(start,handle,(b.x-a.x)/projection.sourceWidth,(b.y-a.y)/projection.sourceHeight)
}

@Composable internal fun PhotoCropFrame(bitmap:Bitmap,edit:PhotoEdit,enabled:Boolean,onChange:(PhotoEdit)->Unit,onFinish:()->Unit){
 val worker=remember(bitmap){Dispatchers.Default.limitedParallelism(1)}
 val context by produceState<Bitmap?>(null,bitmap,edit){delay(70);value=withContext(worker){photoCropContext(bitmap,edit)}}
 var viewport by remember{mutableStateOf(IntSize.Zero)}
 var dragProjection by remember(bitmap){mutableStateOf<PhotoCropProjection?>(null)}
 val current by rememberUpdatedState(edit);val change by rememberUpdatedState(onChange);val finish by rememberUpdatedState(onFinish)
 val projection=dragProjection?:photoCropProjection(bitmap.width,bitmap.height,edit,viewport)
 Box(Modifier.fillMaxSize().onSizeChanged{viewport=it},contentAlignment=Alignment.Center){
  Canvas(Modifier.fillMaxSize().semantics{contentDescription="Edited image preview"}){
   val image=context;val mapping=projection
   if(image!=null&&mapping!=null)drawIntoCanvas{it.nativeCanvas.drawBitmap(image,mapping.matrix,Paint(3))}
  }
  if(context==null)CircularProgressIndicator()
  Canvas(Modifier.fillMaxSize().semantics{
   contentDescription="Crop frame: drag corners or edges to resize; drag inside to move"
   stateDescription="Fixed canvas; crop %.0f by %.0f percent".format((1-edit["left"]-edit["right"])*100,(1-edit["top"]-edit["bottom"])*100)
  }.pointerInput(bitmap,enabled){
   if(!enabled)return@pointerInput
   var handle="";var start=current;var origin=Offset.Zero;var mapping:PhotoCropProjection?=null
   detectDragGestures(onDragStart={point->
    mapping=photoCropProjection(bitmap.width,bitmap.height,current,viewport)
    handle=mapping?.let{photoCropHit(point,current,it,28.dp.toPx())}.orEmpty()
    if(handle.isNotEmpty()){start=current;origin=point;dragProjection=mapping}
   },onDragEnd={if(handle.isNotEmpty())finish();handle="";dragProjection=null},onDragCancel={if(handle.isNotEmpty())finish();handle="";dragProjection=null}){event,_->
    val frame=mapping
    if(handle.isNotEmpty()&&frame!=null){event.consume();change(photoCropDrag(start,handle,origin,event.position,frame))}
   }
  }){
   val frame=projection?:return@Canvas
   val q=frame.corners(edit);val white=Color.White
   val mask=Path().apply{fillType=PathFillType.EvenOdd;addRect(androidx.compose.ui.geometry.Rect(Offset.Zero,size));moveTo(q[0].x,q[0].y);q.drop(1).forEach{lineTo(it.x,it.y)};close()}
   drawPath(mask,Color.Black.copy(alpha=.62f))
   for(i in 0..3)drawLine(white,q[i],q[(i+1)%4],1.dp.toPx())
   val c=photoCropBounds(bitmap.width,bitmap.height,edit)
   for(i in 1..2){
    val x=c.left+c.width()*i/3f;val y=c.top+c.height()*i/3f
    drawLine(white.copy(alpha=.35f),frame.map(Offset(x,c.top.toFloat())),frame.map(Offset(x,c.bottom.toFloat())))
    drawLine(white.copy(alpha=.35f),frame.map(Offset(c.left.toFloat(),y)),frame.map(Offset(c.right.toFloat(),y)))
   }
   frame.handles(edit).forEach{(_,p)->drawCircle(Color.Black.copy(alpha=.6f),6.dp.toPx(),p);drawCircle(white,4.dp.toPx(),p)}
  }
 }
}
