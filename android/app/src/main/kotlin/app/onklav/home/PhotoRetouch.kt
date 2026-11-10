package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Source-normalized edits, replayed before geometry/color so crop and rotation retain the target. */
internal data class PhotoRetouchStroke(val tool:String,val width:Float,val points:List<Offset>,val source:Offset?=null,val hardness:Float=.7f){
 fun json()=JSONObject().put("tool",tool).put("width",width.toDouble()).put("hardness",hardness.toDouble()).put("points",JSONArray().apply{points.forEach{put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble()))}}).apply{source?.let{put("source",JSONArray().put(it.x.toDouble()).put(it.y.toDouble()))}}
 companion object{fun parse(j:JSONObject):PhotoRetouchStroke{val a=j.getJSONArray("points");require(a.length() in 1..256);return PhotoRetouchStroke(j.getString("tool"),j.getDouble("width").toFloat(),List(a.length()){i->val p=a.getJSONArray(i);require(p.length()==2);Offset(p.getDouble(0).toFloat(),p.getDouble(1).toFloat())},j.optJSONArray("source")?.let{require(it.length()==2);Offset(it.getDouble(0).toFloat(),it.getDouble(1).toFloat())},j.optDouble("hardness",.7).toFloat()).also{PhotoRetouch.validate(listOf(it))}}}
}
internal object PhotoRetouch{
 fun validate(strokes:List<PhotoRetouchStroke>){
  require(strokes.size<=80&&strokes.sumOf{it.points.size}<=1000){"Retouch limit reached. Undo a correction or save a copy and reopen it."}
  require(strokes.sumOf{it.points.size*it.width.toDouble()*it.width}<=.16){"Use smaller brushes or fewer strokes. Save a copy and reopen it to continue."}
  strokes.forEach{s->require(s.hardness.isFinite()&&s.hardness in 0f..1f);require((s.tool=="clone")== (s.source!=null));s.source?.let{require(it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..1f&&it.y in 0f..1f)};require(s.tool in setOf("redEye","clone","heal")&&s.width.isFinite()&&s.width in .002f.. .08f&&s.points.size in 1..256);require(s.points.all{it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..1f&&it.y in 0f..1f})}
 }
 /** Returns changed pixel visits for the last stroke, for truthful result feedback. */
 fun apply(bitmap:Bitmap,strokes:List<PhotoRetouchStroke>):Int{
  validate(strokes);require(bitmap.isMutable&&bitmap.width.toLong()*bitmap.height<=16_000_000)
  var changed=0
  for(s in strokes){
   changed=0
   // Freeze the donor for the whole stroke: overlapping paint must not feed back into its source.
   val donor=if(s.tool=="clone")bitmap.copy(Bitmap.Config.ARGB_8888,false) else null
   try{for(point in s.points){
    val radius=max(1f,s.width*min(bitmap.width,bitmap.height)/2)
    val cx=point.x*(bitmap.width-1);val cy=point.y*(bitmap.height-1)
    changed+=when(s.tool){"redEye"->redEye(bitmap,cx,cy,radius);"clone"->clone(bitmap,requireNotNull(donor),cx,cy,radius,requireNotNull(s.source)-s.points.first(),s.hardness);else->heal(bitmap,cx,cy,radius)}
   }}finally{donor?.recycle()}
  }
  return changed
 }
 private fun clone(b:Bitmap,donor:Bitmap,cx:Float,cy:Float,radius:Float,offset:Offset,hardness:Float):Int{
  val l=max(0,floor(cx-radius).toInt());val t=max(0,floor(cy-radius).toInt());val right=min(b.width,ceil(cx+radius+1).toInt());val bottom=min(b.height,ceil(cy+radius+1).toInt())
  val w=right-l;val h=bottom-t;val pixels=IntArray(w*h);b.getPixels(pixels,0,w,l,t,w,h);var changed=0
  val dx=offset.x*(b.width-1);val dy=offset.y*(b.height-1)
  for(y in 0 until h)for(x in 0 until w){val i=y*w+x;val old=pixels[i];val distance=hypot(x+l-cx,y+t-cy)/radius;val sx=(x+l+dx).roundToInt();val sy=(y+t+dy).roundToInt()
   if(distance<1&&sx in 0 until b.width&&sy in 0 until b.height&&Color.alpha(old)>0){val src=donor.getPixel(sx,sy);if(Color.alpha(src)>0){pixels[i]=blend(old,Color.red(src).toFloat(),Color.green(src).toFloat(),Color.blue(src).toFloat(),cloneWeight(distance,hardness)*Color.alpha(src)/255f);if(pixels[i]!=old)changed++}}
  }
  b.setPixels(pixels,0,w,l,t,w,h);return changed
 }
 private fun cloneWeight(distance:Float,hardness:Float):Float {if(distance<=hardness)return 1f;val t=((1-distance)/(1-hardness).coerceAtLeast(.0001f)).coerceIn(0f,1f);return t*t*(3-2*t)}
 private fun weight(distance:Float):Float {val t=((1-distance)/.3f).coerceIn(0f,1f);return t*t*(3-2*t)}
 private fun blend(old:Int,r:Float,g:Float,b:Float,a:Float)=Color.argb(Color.alpha(old),(Color.red(old)+(r-Color.red(old))*a).roundToInt().coerceIn(0,255),(Color.green(old)+(g-Color.green(old))*a).roundToInt().coerceIn(0,255),(Color.blue(old)+(b-Color.blue(old))*a).roundToInt().coerceIn(0,255))
 private fun redEye(b:Bitmap,cx:Float,cy:Float,radius:Float):Int{
  val l=max(0,floor(cx-radius).toInt());val t=max(0,floor(cy-radius).toInt());val w=min(b.width,ceil(cx+radius+1).toInt())-l;val h=min(b.height,ceil(cy+radius+1).toInt())-t
  val p=IntArray(w*h);b.getPixels(p,0,w,l,t,w,h);var changed=0
  for(y in 0 until h)for(x in 0 until w){val i=y*w+x;val old=p[i];val r=Color.red(old);val g=Color.green(old);val blue=Color.blue(old);val distance=hypot(x+l-cx,y+t-cy)/radius
   if(distance<1&&Color.alpha(old)>0&&r>55&&r>g*1.35f&&r>blue*1.05f&&r-g>25){val amount=weight(distance)*((r-g-25)/35f).coerceIn(0f,1f);val neutralBlue=min(blue.toFloat(),g*1.5f);p[i]=blend(old,(g+neutralBlue)/2f,g.toFloat(),neutralBlue,amount);if(p[i]!=old)changed++}
  };b.setPixels(p,0,w,l,t,w,h);return changed
 }
 /** Small content-aware patch: compare surrounding rings, then color-match and feather the donor. */
 private fun heal(b:Bitmap,cx:Float,cy:Float,radius:Float):Int{
  val x=cx.roundToInt();val y=cy.roundToInt();val r=max(1,radius.roundToInt())
  // Bounded local scratch, never a second full-image pixel array.
  val l=max(0,x-r*6);val t=max(0,y-r*6);val right=min(b.width,x+r*6+1);val bottom=min(b.height,y+r*6+1)
  val w=right-l;val h=bottom-t;val pixels=IntArray(w*h);b.getPixels(pixels,0,w,l,t,w,h)
  fun pixel(px:Int,py:Int):Int?=if(px in l until right&&py in t until bottom)pixels[(py-t)*w+px-l].takeIf{Color.alpha(it)>240}else null
  val ring=(0 until 16).map{a->val angle=a*PI/8;Pair((cos(angle)*r*1.15).roundToInt(),(sin(angle)*r*1.15).roundToInt())}
  var best=Float.MAX_VALUE;var dx=0;var dy=0;var dr=0f;var dg=0f;var db=0f
  for(distance in listOf(2.8f,4.2f))for(a in 0 until 16){val angle=a*PI/8;val ox=(cos(angle)*r*distance).roundToInt();val oy=(sin(angle)*r*distance).roundToInt()
   if(x+ox-r<0||x+ox+r>=b.width||y+oy-r<0||y+oy+r>=b.height)continue
   var count=0;var rr=0f;var gg=0f;var bb=0f;var error=0f
   for((rx,ry) in ring){val dst=pixel(x+rx,y+ry);val src=pixel(x+ox+rx,y+oy+ry);if(dst!=null&&src!=null){val ar=Color.red(dst)-Color.red(src);val ag=Color.green(dst)-Color.green(src);val ab=Color.blue(dst)-Color.blue(src);rr+=ar;gg+=ag;bb+=ab;error+=ar*ar+ag*ag+ab*ab;count++}}
   if(count<8)continue
   val score=error/count
   if(score<best){best=score;dx=ox;dy=oy;dr=(rr/count).coerceIn(-35f,35f);dg=(gg/count).coerceIn(-35f,35f);db=(bb/count).coerceIn(-35f,35f)}
  }
  // No trustworthy nearby donor: preserve the image rather than smear its edge.
  if(best==Float.MAX_VALUE)return 0
  val outLeft=max(0,x-r);val outTop=max(0,y-r);val outRight=min(b.width,x+r+1);val outBottom=min(b.height,y+r+1);val ow=outRight-outLeft;val oh=outBottom-outTop
  val out=IntArray(ow*oh);var changed=0
  for(yy in outTop until outBottom)for(xx in outLeft until outRight){val old=pixels[(yy-t)*w+xx-l];val distance=hypot(xx-cx,yy-cy)/radius;val src=pixel(xx+dx,yy+dy)
   out[(yy-outTop)*ow+xx-outLeft]=if(distance<1&&src!=null&&Color.alpha(old)>0)blend(old,Color.red(src)+dr,Color.green(src)+dg,Color.blue(src)+db,weight(distance))else old
   if(out[(yy-outTop)*ow+xx-outLeft]!=old)changed++
  };b.setPixels(out,0,ow,outLeft,outTop,ow,oh);return changed
 }
}
