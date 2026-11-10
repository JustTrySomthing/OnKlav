package app.onklav.home

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.*

/** Immutable fixed-canvas-normalized marks share the photo's history/draft, never the source bitmap. */
internal data class PhotoMark(
 val id:String=UUID.randomUUID().toString(),val tool:String="pen",val color:Int=0xFFFF5252.toInt(),val width:Float=.009f,
 val x:Float=0f,val y:Float=0f,val points:List<Offset> = emptyList(),val text:String="",val textSize:Float=.055f
){
 fun json()=JSONObject().put("id",id).put("tool",tool).put("color",color.toLong()and 0xffffffffL).put("width",width.toDouble())
  .put("x",x.toDouble()).put("y",y.toDouble()).put("text",text).put("text_size",textSize.toDouble())
  .put("points",JSONArray().apply{points.forEach{put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble()))}})
 companion object{
  fun parse(o:JSONObject):PhotoMark{
   val points=o.getJSONArray("points");require(points.length()<=2000)
   return PhotoMark(o.getString("id"),o.getString("tool"),o.getLong("color").also{require(it in 0..0xffffffffL)}.toInt(),o.getDouble("width").toFloat(),o.getDouble("x").toFloat(),o.getDouble("y").toFloat(),List(points.length()){i->val a=points.getJSONArray(i);require(a.length()==2);Offset(a.getDouble(0).toFloat(),a.getDouble(1).toFloat())},o.getString("text"),o.getDouble("text_size").toFloat()).also{it.validate()}
  }
 }
 fun validate(){
  require(id.length in 1..64&&tool in setOf("pen","highlight","text"))
  require(width.isFinite()&&width in .001f.. .15f&&textSize.isFinite()&&textSize in .015f.. .18f)
  require(x.isFinite()&&y.isFinite()&&x in -1f..1f&&y in -1f..1f)
  require(text.length<=400&&text.none{it.isISOControl()&&it!='\n'&&it!='\t'})
  require(points.size<=2000&&points.all{it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..1f&&it.y in 0f..1f})
  if(tool=="text")require(text.isNotBlank()&&points.isEmpty()&&x>=0f&&y>=0f)else{require(points.isNotEmpty()&&text.isEmpty());val b=PhotoMarkup.bounds(this);require(b.left>=-.0001f&&b.top>=-.0001f&&b.right<=1.0001f&&b.bottom<=1.0001f)}
 }
}
internal object PhotoMarkup{
 fun validate(marks:List<PhotoMark>){require(marks.size<=300&&marks.sumOf{it.points.size}<=30000){"This image has reached its mark limit. Erase a mark to make room."};require(marks.map{it.id}.toSet().size==marks.size);marks.forEach{it.validate()}}
 fun textLayout(mark:PhotoMark,w:Int,h:Int):StaticLayout{
  val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=mark.color;textSize=w*mark.textSize;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)}
  return StaticLayout.Builder.obtain(mark.text,0,mark.text.length,paint,maxOf(1,(w*.85f).toInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY).build()
 }
 fun bounds(m:PhotoMark,w:Int=1000,h:Int=1000):RectF{
  if(m.tool=="text"){val layout=textLayout(m,w,h);val width=(0 until layout.lineCount).maxOfOrNull{layout.getLineWidth(it)}?:1f;return RectF(m.x,m.y,m.x+width/w,m.y+layout.height.toFloat()/h)}
  return RectF(m.points.minOf{it.x}+m.x,m.points.minOf{it.y}+m.y,m.points.maxOf{it.x}+m.x,m.points.maxOf{it.y}+m.y)
 }
 fun move(m:PhotoMark,delta:Offset,w:Int,h:Int):PhotoMark{
  val b=bounds(m,w,h);return m.copy(x=m.x+delta.x.coerceIn(-b.left,maxOf(-b.left,1-b.right)),y=m.y+delta.y.coerceIn(-b.top,maxOf(-b.top,1-b.bottom)))
 }
 fun placeText(m:PhotoMark,w:Int,h:Int):PhotoMark{
  val b=bounds(m,w,h);require(b.width()<=1f&&b.height()<=1f){"Shorten the text or reduce its size so it fits the image."}
  return m.copy(x=m.x.coerceIn(0f,1-b.width()),y=m.y.coerceIn(0f,1-b.height()))
 }
 /** Shared Annotation stroke renderer; text is a directly editable label rather than a note box. */
 fun draw(canvas:Canvas,marks:List<PhotoMark>,w:Int,h:Int){
  for(m in marks){if(m.tool=="text"){
   if(m.text.isBlank())continue
   val layout=textLayout(m,w,h);layout.paint.setShadowLayer(maxOf(1f,w*.0015f),0f,w*.0006f,0x99000000.toInt())
   val save=canvas.save();canvas.translate(m.x*w,m.y*h);layout.draw(canvas);canvas.restoreToCount(save)
  }else{
   val o=JSONObject().put("tool",m.tool).put("color",m.color.toLong()and 0xffffffffL).put("width",(m.width*w).toDouble()).put("x",(m.x*w).toDouble()).put("y",(m.y*h).toDouble())
   o.put("points",JSONArray().apply{m.points.forEach{put(JSONArray().put((it.x*w).toDouble()).put((it.y*h).toDouble()))}})
   SdkDrawingImages.drawMark(canvas,o,w)
  }}
 }
 fun drawOnPhoto(canvas:Canvas,e:PhotoEdit,w:Int,h:Int,marks:List<PhotoMark> = e.marks){
  if(marks.isEmpty())return
  val crop=photoCropBounds(w,h,e);val transform=Matrix().apply{postTranslate(-crop.left.toFloat(),-crop.top.toFloat())}
  val save=canvas.save();canvas.clipRect(0,0,crop.width(),crop.height());canvas.concat(transform);draw(canvas,marks,w,h);canvas.restoreToCount(save)
 }
}
internal data class PhotoMarkProjection(val matrix:Matrix,val inverse:Matrix,val canvas:RectF,val w:Int,val h:Int){
 fun map(p:Offset):Offset{val a=floatArrayOf(p.x*w,p.y*h);matrix.mapPoints(a);return Offset(a[0],a[1])}
 fun unmap(p:Offset):Offset?{val a=floatArrayOf(p.x,p.y);inverse.mapPoints(a);return if(a.all{it.isFinite()})Offset(a[0]/w,a[1]/h)else null}
 fun hit(marks:List<PhotoMark>,p:Offset,radius:Float):PhotoMark?{
  if(!canvas.contains(p.x,p.y))return null
  val source=unmap(p)?:return null
  fun segment(a:Offset,b:Offset):Float{val v=b-a;val length=v.x*v.x+v.y*v.y;val t=if(length<.0001f)0f else (((p-a).x*v.x+(p-a).y*v.y)/length).coerceIn(0f,1f);return (p-(a+v*t)).getDistance()}
  return marks.asReversed().firstOrNull{m->if(m.tool=="text"){
   val dx=unmap(p+Offset(radius,0f))?.minus(source)?:Offset.Zero;val dy=unmap(p+Offset(0f,radius))?.minus(source)?:Offset.Zero
   PhotoMarkup.bounds(m,w,h).apply{inset(-maxOf(abs(dx.x),abs(dy.x)),-maxOf(abs(dx.y),abs(dy.y)))}.contains(source.x,source.y)
  }else{
   val points=m.points.map{map(it+Offset(m.x,m.y))};val stroke=(map(source+Offset(m.width/2,0f))-map(source)).getDistance();val reach=maxOf(radius,stroke)
   if(points.size==1)(points[0]-p).getDistance()<=reach else (0 until points.lastIndex).any{segment(points[it],points[it+1])<=reach}
  }}
 }
}
internal fun photoMarkProjection(w:Int,h:Int,e:PhotoEdit,viewport:Size,zoom:Float,pan:Offset):PhotoMarkProjection{
 val crop=photoCropBounds(w,h,e);val scale=(min(viewport.width/crop.width(),viewport.height/crop.height())*.94f*zoom).coerceAtLeast(.0001f)
 val left=(viewport.width-crop.width()*scale)/2+pan.x;val top=(viewport.height-crop.height()*scale)/2+pan.y
 val m=Matrix().apply{postTranslate(-crop.left.toFloat(),-crop.top.toFloat());postScale(scale,scale);postTranslate(left,top)}
 val inverse=Matrix();check(m.invert(inverse));return PhotoMarkProjection(m,inverse,RectF(left,top,left+crop.width()*scale,top+crop.height()*scale),w,h)
}
