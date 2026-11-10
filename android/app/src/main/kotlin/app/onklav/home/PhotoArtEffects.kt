package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Deterministic local image processing. Original row rings bound memory even for exports. */
internal object PhotoArtEffects {
 private fun light(p:Int)=(Color.red(p)*.299f+Color.green(p)*.587f+Color.blue(p)*.114f)/255f
 private fun mix(a:Int,b:Int,t:Float)=Color.argb(Color.alpha(a),(Color.red(a)+(Color.red(b)-Color.red(a))*t).roundToInt().coerceIn(0,255),(Color.green(a)+(Color.green(b)-Color.green(a))*t).roundToInt().coerceIn(0,255),(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).roundToInt().coerceIn(0,255))
 private class RowSamples(val ring:Array<IntArray>,val radius:Int,val width:Int){
  var first=0
  operator fun invoke(x:Int,dy:Int):Int=ring[(first+radius+dy)%ring.size][x.coerceIn(0,width-1)]
 }
 private fun interface PixelOperation {fun apply(x:Int,y:Int,pixel:Int,samples:RowSamples):Int}
 private fun rows(bitmap:Bitmap,radius:Int,operation:PixelOperation){
  val w=bitmap.width;val h=bitmap.height;val count=radius*2+1;val ring=Array(count){IntArray(w)};val output=IntArray(w)
  fun load(slot:Int,y:Int){bitmap.getPixels(ring[slot],0,w,0,y.coerceIn(0,h-1),w,1)}
  repeat(count){load(it,it-radius)};var first=0;val sample=RowSamples(ring,radius,w)
  for(y in 0 until h){
   sample.first=first
   for(x in 0 until w){val p=sample(x,0);output[x]=if(Color.alpha(p)==0)p else operation.apply(x,y,p,sample)}
   bitmap.setPixels(output,0,w,0,y,w,1)
   if(y<h-1){load(first,y+radius+1);first=(first+1)%count}
  }
 }
 /** A modest bilateral filter, not a blur preset: color distance protects edges and texture. */
 fun reduceNoise(bitmap:Bitmap,strength:Float){
  val a=strength.coerceIn(0f,1f);if(a==0f)return
  val sigma=10f+a*24f;val inverse=1f/(2*sigma*sigma)
  val range=FloatArray(195076){exp(-it*inverse)}
  val offsets=arrayOf(intArrayOf(0,0,4),intArrayOf(-1,0,3),intArrayOf(1,0,3),intArrayOf(0,-1,3),intArrayOf(0,1,3),intArrayOf(-1,-1,2),intArrayOf(1,-1,2),intArrayOf(-1,1,2),intArrayOf(1,1,2),intArrayOf(-2,0,1),intArrayOf(2,0,1),intArrayOf(0,-2,1),intArrayOf(0,2,1))
  rows(bitmap,2){x,_,p,sample->
   val r=Color.red(p);val g=Color.green(p);val b=Color.blue(p);var rr=0f;var gg=0f;var bb=0f;var total=0f
   for(o in offsets){val q=sample(x+o[0],o[1]);val dr=Color.red(q)-r;val dg=Color.green(q)-g;val db=Color.blue(q)-b
    val weight=range[dr*dr+dg*dg+db*db]*o[2]*(Color.alpha(q)/255f);rr+=Color.red(q)*weight;gg+=Color.green(q)*weight;bb+=Color.blue(q)*weight;total+=weight}
   if(total<=.001f)p else mix(p,Color.rgb((rr/total).roundToInt(),(gg/total).roundToInt(),(bb/total).roundToInt()),a)
  }
 }
 fun comic(bitmap:Bitmap,strength:Float){
  val a=strength.coerceIn(0f,1f);if(a==0f)return
  // Denoise the analysis/source gently before inking: avoid turning skin grain into outlines.
  reduceNoise(bitmap,.8f*a)
  val unit=max(bitmap.width,bitmap.height)/650f
  val edgeRadius=(max(bitmap.width,bitmap.height)/900).coerceIn(1,6)
  rows(bitmap,edgeRadius){x,y,p,sample->
   val l=light(p);val gx=light(sample(x+edgeRadius,-edgeRadius))+2*light(sample(x+edgeRadius,0))+light(sample(x+edgeRadius,edgeRadius))-light(sample(x-edgeRadius,-edgeRadius))-2*light(sample(x-edgeRadius,0))-light(sample(x-edgeRadius,edgeRadius))
   val gy=light(sample(x-edgeRadius,edgeRadius))+2*light(sample(x,edgeRadius))+light(sample(x+edgeRadius,edgeRadius))-light(sample(x-edgeRadius,-edgeRadius))-2*light(sample(x,-edgeRadius))-light(sample(x+edgeRadius,-edgeRadius))
   val edge=((hypot(gx,gy)-.10f)/.65f).coerceIn(0f,1f).pow(.8f)
   val quant=(floor(l*6)+.5f)/6;val target=(l*.2f+quant*.8f).coerceIn(0f,1f)
   fun tone(v:Int)=((target+(v/255f-l)*1.18f)*(1-edge*.88f)+.035f*edge).coerceIn(0f,1f)
   val dotUnit=unit.coerceAtLeast(1f)*4f;val dx=(x+y*.25f)%dotUnit/dotUnit-.5f;val dy=(y-x*.25f).let{((it%dotUnit)+dotUnit)%dotUnit}/dotUnit-.5f
   val dot=if(l<.5f&&dx*dx+dy*dy<(.5f-l)*.07f).055f else 0f
   mix(p,Color.rgb(((tone(Color.red(p))-dot)*255).roundToInt().coerceIn(0,255),((tone(Color.green(p))-dot)*255).roundToInt().coerceIn(0,255),((tone(Color.blue(p))-dot)*255).roundToInt().coerceIn(0,255)),a)
  }
 }
 fun pencil(bitmap:Bitmap,strength:Float){
  val a=strength.coerceIn(0f,1f);if(a==0f)return
  val w=bitmap.width;val h=bitmap.height;val radius=(max(w,h)/100).coerceIn(2,18);val count=2*radius+1
  val pixels=Array(count){IntArray(w)};val horizontal=Array(count){FloatArray(w)};val weights=Array(count){FloatArray(w)};val sum=FloatArray(w);val alpha=FloatArray(w);val output=IntArray(w)
  fun load(slot:Int,y:Int){bitmap.getPixels(pixels[slot],0,w,0,y.coerceIn(0,h-1),w,1);var s=0f;var weight=0f
   fun add(x:Int,sign:Float){val p=pixels[slot][x.coerceIn(0,w-1)];val a=Color.alpha(p)/255f;s+=light(p)*a*sign;weight+=a*sign}
   for(k in -radius..radius)add(k,1f)
   for(x in 0 until w){horizontal[slot][x]=s/count;weights[slot][x]=weight/count;add(x-radius,-1f);add(x+radius+1,1f)}
  }
  repeat(count){load(it,it-radius);for(x in 0 until w){sum[x]+=horizontal[it][x];alpha[x]+=weights[it][x]}};var first=0
  val spacing=(max(w,h)/360f).coerceAtLeast(1f)*4f
  for(y in 0 until h){val row=pixels[(first+radius)%count]
   for(x in 0 until w){val p=row[x];val l=light(p);val base=if(alpha[x]>.001f)sum[x]/alpha[x]else l
    val contour=((base-l)/(.10f+base*.18f)).coerceIn(0f,1f)
    val tone=(1-l).pow(1.35f);val stroke=abs(sin((x*.75f+y)/spacing*PI.toFloat())).pow(14f)
    val cross=if(l<.38f)abs(sin((x-y*.8f)/spacing*PI.toFloat())).pow(14f)else 0f
    val grain=(((x*73+y*151)xor(x*y*13))and 255)/255f
    val value=(.975f+grain*.02f-tone*.32f-contour*.66f-stroke*tone*.14f-cross*tone*.10f).coerceIn(0f,1f)
    val v=(value*255).roundToInt();output[x]=if(Color.alpha(p)==0)p else mix(p,Color.rgb(v,v,v),a)
   }
   bitmap.setPixels(output,0,w,0,y,w,1)
   if(y<h-1){for(x in 0 until w){sum[x]-=horizontal[first][x];alpha[x]-=weights[first][x]};load(first,y+radius+1);for(x in 0 until w){sum[x]+=horizontal[first][x];alpha[x]+=weights[first][x]};first=(first+1)%count}
  }
 }
}
