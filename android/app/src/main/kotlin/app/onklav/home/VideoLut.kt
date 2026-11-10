@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import androidx.media3.effect.SingleColorLut
import kotlin.math.*

/** Bounded normalized SDR .cube; file order is red-fastest, then green, then blue. */
internal data class VideoLut(val size:Int,val values:List<FloatArray>){
    fun sample(r:Float,g:Float,b:Float):FloatArray{
        val xyz=floatArrayOf(r,g,b).map{it.coerceIn(0f,1f)*(size-1)};val lo=xyz.map{floor(it).toInt().coerceAtMost(size-2)};val f=xyz.mapIndexed{i,x->x-lo[i]};val out=FloatArray(3)
        for(z in 0..1)for(y in 0..1)for(x in 0..1){val weight=(if(x==0)1-f[0]else f[0])*(if(y==0)1-f[1]else f[1])*(if(z==0)1-f[2]else f[2]);val v=values[(lo[2]+z)*size*size+(lo[1]+y)*size+lo[0]+x];for(k in 0..2)out[k]+=v[k]*weight};return out
    }
    fun cube(amount:Float):Array<Array<IntArray>>{
        // Media3 effects operate on linear BT.709; conventional SDR LUTs expect encoded RGB.
        fun encode(x:Float)=if(x<.018f)4.5f*x else 1.099f*x.pow(.45f)-.099f
        fun decode(x:Float)=if(x<.081f)x/4.5f else ((x+.099f)/1.099f).pow(1/.45f)
        return Array(size){r->Array(size){g->IntArray(size){b->val original=floatArrayOf(r.toFloat()/(size-1),g.toFloat()/(size-1),b.toFloat()/(size-1));val looked=sample(encode(original[0]),encode(original[1]),encode(original[2]));val rgb=IntArray(3){i->((original[i]+(decode(looked[i])-original[i])*amount)*255).roundToInt().coerceIn(0,255)};android.graphics.Color.rgb(rgb[0],rgb[1],rgb[2])}}}
    }
    fun effect(amount:Float)=SingleColorLut.createFromCube(cube(amount))
    companion object{
        fun parse(text:String):VideoLut{
            require(text.length<=4_000_000){"Choose a .cube file smaller than 4 MB"};var size=0;val values=ArrayList<FloatArray>()
            text.removePrefix("\uFEFF").lineSequence().forEach{line->val clean=line.substringBefore('#').trim();if(clean.isEmpty())return@forEach;val p=clean.split(Regex("\\s+"))
                when(p[0]){"TITLE"->Unit;"LUT_3D_SIZE"->{require(size==0&&p.size==2);size=p[1].toInt();require(size in 2..33){"Use a 3D LUT with 2–33 points per channel"}};"DOMAIN_MIN","DOMAIN_MAX"->{val target=if(p[0]=="DOMAIN_MIN")0f else 1f;require(p.size==4&&p.drop(1).all{it.toFloatOrNull()==target}){"Only normalized SDR LUT domains (0 to 1) are supported"}};else->{require(size>0&&p.size==3){"Choose a normalized 3D .cube LUT"};val v=p.map{it.toFloat()};require(v.all{it.isFinite()&&it in 0f..1f}){"LUT samples must be finite SDR values from 0 to 1"};values+=v.toFloatArray();require(values.size<=size*size*size){"Too many LUT samples"}}}
            };require(size>0&&values.size==size*size*size){"The LUT sample count does not match its size"};return VideoLut(size,values)
        }
    }
}
