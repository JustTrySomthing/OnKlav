package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** SDR effects. Spatial work uses bounded analysis buffers and original-row rings. */
internal object PhotoEffects {
    data class Looks(val teal:Float,val moody:Float,val noir:Float,val technicolor:Float) {
        constructor(e:PhotoEdit):this(e["tealOrange"].coerceIn(0f,1f),e["moody"].coerceIn(0f,1f),e["noir"].coerceIn(0f,1f),0f)
    }
    fun grade(red:Float,green:Float,blue:Float,alpha:Int,distance:Float,a:Looks):Int {
        var r=red;var g=green;var b=blue
        if(a.teal>0){
            val y=(.299f*r+.587f*g+.114f*b).coerceIn(0f,1f);val shadows=(1-y).pow(2);val highlights=y.pow(1.3f)
            val skin=if(r>g&&g>b)((r-b)*2).coerceIn(0f,.6f)else 0f
            r+=a.teal*(-.15f*shadows+.12f*highlights+.035f*skin)
            g+=a.teal*(.045f*shadows+.025f*highlights)
            b+=a.teal*(.11f*shadows-.12f*highlights-.04f*skin)
        }
        if(a.moody>0){
            val y=.299f*r+.587f*g+.114f*b;val rr=y+(r-y)*.62f;val gg=y+(g-y)*.62f;val bb=y+(b-y)*.62f
            fun tone(v:Float)=((v.coerceAtLeast(0f).pow(1.18f)-.5f)*1.24f+.46f).coerceIn(0f,1f)
            r+=(tone(rr)-r)*a.moody;g+=(tone(gg)*.96f-g)*a.moody;b+=(tone(bb)*.88f-b)*a.moody
        }
        if(a.noir>0){
            val gray=(((.299f*r+.587f*g+.114f*b)-.5f)*1.95f+.47f).coerceIn(0f,1f).pow(1.12f)
            val vignette=1-.65f*(distance/2).coerceIn(0f,1f).pow(.8f)
            val target=gray*vignette
            r+=(target-r)*a.noir;g+=(target-g)*a.noir;b+=(target-b)*a.noir
        }
        if(a.technicolor>0){
            val rr=1.14f*r-.08f*g-.025f*b;val gg=-.035f*r+1.12f*g-.045f*b;val bb=-.035f*r-.055f*g+1.17f*b
            val y=.299f*rr+.587f*gg+.114f*bb
            fun vivid(v:Float)=((y+(v-y)*1.42f-.5f)*1.12f+.5f).coerceIn(0f,1f)
            r+=(vivid(rr)-r)*a.technicolor;g+=(vivid(gg)-g)*a.technicolor;b+=(vivid(bb)-b)*a.technicolor
        }
        return Color.argb(alpha,(r*255).roundToInt().coerceIn(0,255),(g*255).roundToInt().coerceIn(0,255),(b*255).roundToInt().coerceIn(0,255))
    }
    private fun luma(p:Int)=(.299f*Color.red(p)+.587f*Color.green(p)+.114f*Color.blue(p))/255f
    fun sharpen(bitmap:Bitmap,strength:Float){
        val amount=strength.coerceIn(0f,1f);if(amount==0f||bitmap.width<3||bitmap.height<3)return
        val w=bitmap.width;val h=bitmap.height;val radius=(max(w,h)/1000).coerceIn(1,4);val count=radius*2+1
        val pixels=Array(count){IntArray(w)};val horizontal=Array(count){FloatArray(w)};val opacity=Array(count){FloatArray(w)}
        val sum=FloatArray(w);val weight=FloatArray(w);val output=IntArray(w)
        fun load(slot:Int,y:Int){bitmap.getPixels(pixels[slot],0,w,0,y.coerceIn(0,h-1),w,1);var light=0f;var alpha=0f
            fun add(x:Int,sign:Float){val p=pixels[slot][x.coerceIn(0,w-1)];val a=Color.alpha(p)/255f;light+=luma(p)*a*sign;alpha+=a*sign}
            for(k in -radius..radius)add(k,1f)
            for(x in 0 until w){horizontal[slot][x]=light/count;opacity[slot][x]=alpha/count;add(x-radius,-1f);add(x+radius+1,1f)}}
        for(slot in 0 until count){load(slot,slot-radius);for(x in 0 until w){sum[x]+=horizontal[slot][x];weight[x]+=opacity[slot][x]}}
        var first=0
        for(y in 0 until h){val original=pixels[(first+radius)%count]
            for(x in 0 until w){val p=original[x];val mean=if(weight[x]>.001f)sum[x]/weight[x]else luma(p);val edge=luma(p)-mean
                val detail=sign(edge)*(abs(edge)-.004f).coerceAtLeast(0f)
                val delta=(detail*2.8f*amount).coerceIn(-.10f,.10f)*255
                output[x]=if(Color.alpha(p)==0)p else Color.argb(Color.alpha(p),(Color.red(p)+delta).roundToInt().coerceIn(0,255),(Color.green(p)+delta).roundToInt().coerceIn(0,255),(Color.blue(p)+delta).roundToInt().coerceIn(0,255))}
            bitmap.setPixels(output,0,w,0,y,w,1)
            if(y<h-1){for(x in 0 until w){sum[x]-=horizontal[first][x];weight[x]-=opacity[first][x]};load(first,y+radius+1);for(x in 0 until w){sum[x]+=horizontal[first][x];weight[x]+=opacity[first][x]};first=(first+1)%count}
        }
    }
}

/** One selected look; adjustments and detail controls are independent and reversible. */
internal object PhotoLooks {
 val controls=listOf("none" to "No look","blackWhite" to "Black & white","sepia" to "Sepia","colorize" to "Colorize","tealOrange" to "Teal & Orange","moody" to "Dark & Moody","noir" to "Film Noir")
 private val oldKeys=setOf("colorMode","tealOrange","moody","noir")
 fun removed(key:String)=key.startsWith("hdr",true)
 fun key(e:PhotoEdit)=controls[(e.values["look"]?:0f).toInt().coerceIn(0,6)].first
 fun label(e:PhotoEdit)=controls.first{it.first==key(e)}.second
 fun amount(e:PhotoEdit)=(e.values["lookStrength"]?:0f).coerceIn(0f,1f)
 fun monochrome(e:PhotoEdit)=key(e) in setOf("blackWhite","noir")&&amount(e)>=.999f
 fun choose(e:PhotoEdit,selectedKey:String,strength:Float=if(key(e)==selectedKey&&amount(e)>0f)amount(e)else 1f):PhotoEdit {
  val index=controls.indexOfFirst{it.first==selectedKey};require(index>=0)
  return e.copy(values=e.values.filterKeys{it !in oldKeys&&!removed(it)}+mapOf("look" to index.toFloat(),"lookStrength" to if(index==0)0f else strength.coerceIn(0f,1f)))
 }
 fun migrate(values:Map<String,Float>):Map<String,Float>{
  var clean=values.filterKeys{!removed(it)}
  if("look" !in clean){
   val legacy=listOf("noir","moody","tealOrange").firstOrNull{(clean[it]?:0f)>0f}
   val selected=legacy?:when((clean["colorMode"]?:0f).toInt()){1->"blackWhite";2->"sepia";else->"none"}
   if(selected!="none")clean=choose(PhotoEdit(clean),selected,legacy?.let{clean[it]}?:1f).values
  }
  clean=clean.filterKeys{it !in oldKeys}
  return clean.mapValues{(k,v)->when(k){
   "look"->v.toInt().coerceIn(0,6).toFloat()
   "lookStrength","colorizeSaturation","colorizeValue","vignette","sharpness","denoise"->v.coerceIn(0f,1f)
   "colorizeHue"->v.coerceIn(0f,360f)
   "exposure"->v.coerceIn(-3f,3f)
   "brightness","warmth","tint"->v.coerceIn(-1f,1f)
   "contrast","saturation"->v.coerceIn(0f,2f)
   "gamma"->v.coerceIn(.2f,3f)
   "hue"->v.coerceIn(-180f,180f)
   "black"->v.coerceIn(0f,.45f)
   "white"->v.coerceIn(.55f,1f)
   else->v
  }}
 }
 data class Plan(val key:String,val strength:Float,val tint:Int,val grade:PhotoEffects.Looks)
 fun plan(e:PhotoEdit):Plan {
  val key=key(e);val strength=amount(e)
  return Plan(key,strength,Color.HSVToColor(floatArrayOf(e["colorizeHue"],e["colorizeSaturation"],e["colorizeValue"])),PhotoEffects.Looks(if(key=="tealOrange")strength else 0f,if(key=="moody")strength else 0f,if(key=="noir")strength else 0f,0f))
 }
 private val neutral=PhotoEffects.Looks(0f,0f,0f,0f)
 fun apply(r:Float,g:Float,b:Float,alpha:Int,distance:Float,plan:Plan):Int {
  val key=plan.key;val strength=plan.strength
  if(key=="none"||strength==0f)return PhotoEffects.grade(r,g,b,alpha,distance,neutral)
  var rr=r;var gg=g;var bb=b
  val gray=(.299f*r+.587f*g+.114f*b).coerceIn(0f,1f)
  when(key){
   "blackWhite"->{rr=gray;gg=gray;bb=gray}
   "sepia"->{rr=.393f*r+.769f*g+.189f*b;gg=.349f*r+.686f*g+.168f*b;bb=.272f*r+.534f*g+.131f*b}
   "colorize"->{
    val color=plan.tint
    // Multiply a neutral luminance image by the selected tint, retaining texture and alpha.
    rr=gray*Color.red(color)/255f;gg=gray*Color.green(color)/255f;bb=gray*Color.blue(color)/255f
   }
   else->return PhotoEffects.grade(r,g,b,alpha,distance,plan.grade)
  }
  return PhotoEffects.grade(r+(rr.coerceIn(0f,1f)-r)*strength,g+(gg.coerceIn(0f,1f)-g)*strength,b+(bb.coerceIn(0f,1f)-b)*strength,alpha,distance,neutral)
 }
}
