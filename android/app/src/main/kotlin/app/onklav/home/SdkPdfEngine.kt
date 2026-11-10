package app.onklav.home

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.*
import java.io.OutputStream
import kotlin.math.*

/** Small test seam; production always uses Android's renderer in the isolated process. */
internal interface PdfEngine:AutoCloseable {
 val pages:Int
 val textSupported:Boolean
 fun size(page:Int):Pair<Int,Int>
 fun render(page:Int,width:Int,out:OutputStream)
 fun text(page:Int):String
 fun search(page:Int,query:String,after:Int):List<List<RectF>>
 fun select(page:Int,start:Point,end:Point):Pair<String,List<RectF>>
}
internal object SdkPdfProtocol {
 const val MAX_FILE=50L*1024*1024
 const val MAX_PIXELS=2_000_000
 const val MAX_PNG=8*1024*1024
 fun dimensions(w:Int,h:Int,target:Int):Pair<Int,Int>{
  require(w in 1..20000&&h in 1..20000&&target in 256..1600)
  val scale=minOf(target.toDouble()/w,4096.0/h,sqrt(MAX_PIXELS.toDouble()/(w.toDouble()*h)))
  return max(1,floor(w*scale).toInt()) to max(1,floor(h*scale).toInt())
 }
 fun boundedText(text:String,limit:Int):String=text.take(limit).let{if(it.lastOrNull()?.isHighSurrogate()==true)it.dropLast(1)else it}
 fun rects(rects:List<RectF>,w:Int,h:Int):ArrayList<Bundle>{return ArrayList(rects.take(128).filter{listOf(it.left,it.top,it.right,it.bottom).all(Float::isFinite)}.map{r->Bundle().apply{putFloatArray("rect",floatArrayOf(r.left.coerceIn(0f,w.toFloat()),r.top.coerceIn(0f,h.toFloat()),r.right.coerceIn(0f,w.toFloat()),r.bottom.coerceIn(0f,h.toFloat())))}})}
 fun execute(source:ParcelFileDescriptor,a:Bundle,out:OutputStream?,factory:(ParcelFileDescriptor)->PdfEngine={AndroidPdfEngine(it)}):Bundle{
  require(source.statSize in 1..MAX_FILE)
  val op=a.getString("op")?:error("Missing PDF operation")
  require(op in setOf("info","render","text","search","select"))
  factory(source).use{engine->
   require(engine.pages in 1..2000){"PDF page limit reached"}
   val result=Bundle().apply{putInt("pages",engine.pages);putBoolean("text_supported",engine.textSupported)}
   if(op=="info")return result
   val page=a.getInt("page",-1);require(page in 0 until engine.pages)
   val(w,h)=engine.size(page);require(w in 1..20000&&h in 1..20000)
   result.putInt("page",page);result.putInt("width",w);result.putInt("height",h)
   when(op){
    "render"->{val width=a.getInt("render_width",1000);dimensions(w,h,width);require(out!=null);engine.render(page,width,out)}
    "text"->{check(engine.textSupported){"Text tools need Android15 or later"};result.putString("text",boundedText(engine.text(page),50000))}
    "search"->{check(engine.textSupported){"Text tools need Android15 or later"};val query=a.getString("query")?:"";require(query.isNotBlank()&&query.length<=128)
     val matches=ArrayList<Bundle>();var nextPage=-1;var nextMatch=0;val after=a.getInt("after",0);require(after in 0..1_000_000)
     for(index in page until minOf(engine.pages,page+8)){
      val skip=if(index==page)after else 0;val(pw,ph)=engine.size(index);val found=engine.search(index,query,skip);val accepted=minOf(40-matches.size,found.size)
      for(bounds in found.take(accepted)){matches.add(Bundle().apply{putInt("page",index);putParcelableArrayList("bounds",rects(bounds.take(16),pw,ph))})}
      if(found.size>accepted){nextPage=index;nextMatch=skip+accepted;break}
      nextPage=if(index+1<engine.pages)index+1 else -1;nextMatch=0
      if(matches.size==40)break
     }
     result.putParcelableArrayList("matches",matches);result.putInt("next_page",nextPage);result.putInt("next_match",nextMatch)
    }
    "select"->{check(engine.textSupported){"Text tools need Android15 or later"};val points=a.getFloatArray("points")?:error("Missing selection");require(points.size==4&&points.all(Float::isFinite));val start=Point(points[0].roundToInt().coerceIn(0,w),points[1].roundToInt().coerceIn(0,h));val end=Point(points[2].roundToInt().coerceIn(0,w),points[3].roundToInt().coerceIn(0,h));val(text,bounds)=engine.select(page,start,end);result.putString("text",boundedText(text,20000));result.putParcelableArrayList("bounds",rects(bounds,w,h))}
   };return result
  }
 }
}
internal class AndroidPdfEngine(source:ParcelFileDescriptor):PdfEngine {
 private val renderer=PdfRenderer(source)
 override val pages get()=renderer.pageCount
 override val textSupported get()=Build.VERSION.SDK_INT>=35
 override fun size(page:Int)=renderer.openPage(page).use{it.width to it.height}
 override fun render(page:Int,width:Int,out:OutputStream){renderer.openPage(page).use{p->val(w,h)=SdkPdfProtocol.dimensions(p.width,p.height,width);val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);try{bitmap.eraseColor(Color.WHITE);val matrix=Matrix().apply{setScale(w.toFloat()/p.width,h.toFloat()/p.height)};p.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out))}finally{bitmap.recycle()}}}
 @android.annotation.TargetApi(35) override fun text(page:Int):String{check(textSupported);return renderer.openPage(page).use{p->val out=StringBuilder();for(c in p.textContents){out.append(c.text).append('\n');if(out.length>=50000)break};SdkPdfProtocol.boundedText(out.toString(),50000)}}
 @android.annotation.TargetApi(35) override fun search(page:Int,query:String,after:Int):List<List<RectF>>{check(textSupported);return renderer.openPage(page).use{p->p.searchText(query).drop(after).take(41).map{it.bounds.take(16)}}}
 @android.annotation.TargetApi(35) override fun select(page:Int,start:Point,end:Point):Pair<String,List<RectF>>{check(textSupported);return renderer.openPage(page).use{p->val selection=p.selectContent(SelectionBoundary(start),SelectionBoundary(end));val chunks=selection?.selectedTextContents.orEmpty();val text=StringBuilder();val bounds=ArrayList<RectF>();for(c in chunks){text.append(c.text).append('\n');bounds.addAll(c.bounds.take((128-bounds.size).coerceAtLeast(0)));if(text.length>=20000)break};SdkPdfProtocol.boundedText(text.toString(),20000) to bounds}}
 override fun close(){renderer.close()}
}
