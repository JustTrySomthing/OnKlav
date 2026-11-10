package app.onklav.home

import java.io.*
import java.nio.*
import kotlin.math.*
import org.json.*

internal data class AudioRegion(val start:Long,val end:Long,val silent:Boolean=false){val size get()=end-start}
internal data class AudioEdit(
    val regions:List<AudioRegion>,val gain:Float=0f,val bass:Float=0f,val mid:Float=0f,val treble:Float=0f,
    val pitch:Float=0f,val tempo:Float=1f,val fadeIn:Float=0f,val fadeOut:Float=0f,
    val noise:Float=0f,val gate:Float=-80f,val highPass:Boolean=false,val dc:Boolean=false,
    val compressor:Boolean=false,val normalize:Boolean=false,val limiter:Boolean=true,val reverse:Boolean=false
){
    val frames:Long=regions.sumOf{it.size}
    fun slice(from:Long,to:Long):List<AudioRegion>{
        var offset=0L
        return regions.mapNotNull{r->val a=max(0,from-offset);val b=min(r.size,to-offset);offset+=r.size;if(b>a)AudioRegion(r.start+a,r.start+b,r.silent)else null}
    }
    fun trim(a:Long,b:Long)=copy(regions=slice(a,b))
    fun cut(a:Long,b:Long)=copy(regions=slice(0,a)+slice(b,frames))
    fun silence(a:Long,b:Long)=copy(regions=slice(0,a)+slice(a,b).map{it.copy(silent=true)}+slice(b,frames))
    fun duplicate(a:Long,b:Long)=copy(regions=slice(0,b)+slice(a,b)+slice(b,frames))
    fun json()=JSONObject().apply{
        put("regions",JSONArray().apply{regions.forEach{put(JSONArray(listOf(it.start,it.end,it.silent)))}})
        listOf("gain" to gain,"bass" to bass,"mid" to mid,"treble" to treble,"pitch" to pitch,"tempo" to tempo,"fadeIn" to fadeIn,"fadeOut" to fadeOut,"noise" to noise,"gate" to gate).forEach{put(it.first,it.second.toDouble())}
        listOf("highPass" to highPass,"dc" to dc,"compressor" to compressor,"normalize" to normalize,"limiter" to limiter,"reverse" to reverse).forEach{put(it.first,it.second)}
    }
    companion object{fun parse(j:JSONObject,sourceFrames:Long):AudioEdit{
        val a=j.getJSONArray("regions");require(a.length() in 1..512)
        val r=(0 until a.length()).map{val v=a.getJSONArray(it);AudioRegion(v.getLong(0),v.getLong(1),v.optBoolean(2)).also{require(it.start>=0&&it.end<=sourceFrames&&it.size>0)}}
        fun f(k:String,default:Float,lo:Float,hi:Float)=j.optDouble(k,default.toDouble()).toFloat().also{require(it.isFinite()&&it in lo..hi)}
        return AudioEdit(r,f("gain",0f,-24f,24f),f("bass",0f,-12f,12f),f("mid",0f,-12f,12f),f("treble",0f,-12f,12f),f("pitch",0f,-12f,12f),f("tempo",1f,.5f,2f),f("fadeIn",0f,0f,30f),f("fadeOut",0f,0f,30f),f("noise",0f,0f,1f),f("gate",-80f,-80f,-20f),j.optBoolean("highPass"),j.optBoolean("dc"),j.optBoolean("compressor"),j.optBoolean("normalize"),j.optBoolean("limiter",true),j.optBoolean("reverse"))
    }}
}
internal data class AudioSource(val file:File,val rate:Int,val channels:Int,val frames:Long,val name:String){val seconds get()=frames.toDouble()/rate}
internal class AudioPcmReader(val file:File,val channels:Int):Closeable{
    private val input=RandomAccessFile(file,"r");private val length=input.length()
    private data class Page(val samples:FloatArray,var base:Long=-1,var count:Int=0)
    private val pages=Array(4){Page(FloatArray(16384*channels))};private var recent=0
    private val bytes=ByteArray(16384*channels*4)
    internal var blockLoads=0;private set
    fun get(frame:Long,ch:Int):Float{
        if(frame<0||frame>=length/(4*channels))return 0f
        val base=frame/16384*16384
        var page=pages[recent]
        if(page.base!=base){val cached=pages.indexOfFirst{it.base==base};recent=if(cached>=0)cached else (recent+1)%pages.size;page=pages[recent]
            if(cached<0){input.seek(base*4*channels);val n=min(bytes.size.toLong(),length-input.filePointer).toInt();input.readFully(bytes,0,n);page.count=n/4;ByteBuffer.wrap(bytes,0,n).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(page.samples,0,page.count);page.base=base;blockLoads++}
        }
        val i=((frame-base)*channels+ch).toInt();return if(i<page.count)page.samples[i]else 0f
    }
    override fun close()=input.close()
}

internal class AudioPcmWriter(file:File):Closeable{
    private val out=BufferedOutputStream(file.outputStream(),65536);private val buf=ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)
    fun sample(v:Float){if(buf.remaining()<4)flush();buf.putFloat(if(v.isFinite())v else 0f)}
    private fun flush(){out.write(buf.array(),0,buf.position());buf.clear()}
    override fun close(){flush();out.close()}
}
/** All processing is file backed; audio duration does not increase heap use. */
internal object AudioEditEngine{
    const val FFT=2048
    fun mapped(source:AudioSource,e:AudioEdit,read:AudioPcmReader): (Long,Int)->Float{
        val frames=e.frames;val ends=LongArray(e.regions.size);var n=0L;e.regions.forEachIndexed{i,r->n+=r.size;ends[i]=n}
        return {frame,ch->
            val f=if(e.reverse)frames-1-frame else frame
            if(f<0||f>=frames)0f else{
                var lo=0;var hi=ends.lastIndex;while(lo<hi){val m=(lo+hi)/2;if(f<ends[m])hi=m else lo=m+1}
                val r=e.regions[lo];if(r.silent)0f else read.get(r.start+f-(if(lo==0)0 else ends[lo-1]),ch)
            }
        }
    }
    fun peaks(source:AudioSource,e:AudioEdit,bins:Int=1600,check:()->Unit={}):FloatArray{
        val out=FloatArray(bins)
        AudioPcmReader(source.file,source.channels).use{r->val sample=mapped(source,e,r)
            for(i in out.indices){check();val a=i*e.frames/bins;val b=max(a+1,(i+1)*e.frames/bins);val step=max(1,(b-a)/64)
                var p=0f;var f=a;while(f<b){for(c in 0 until source.channels)p=max(p,abs(sample(f,c)));f+=step};out[i]=p.coerceAtMost(1f)}
        };return out
    }
    // Waveform similarity overlap-add: linked stereo alignment; pitch resampling and
    // independent analysis hop keep pitch and duration controls independent.
    private fun timing(source:AudioSource,e:AudioEdit,out:File,progress:(Float)->Unit,check:()->Unit){
        AudioPcmReader(source.file,source.channels).use{r->AudioPcmWriter(out).use{w->val input=mapped(source,e,r);val c=source.channels
            val ratio=2.0.pow(e.pitch/12.0);val frames=(e.frames/e.tempo).toLong().coerceAtLeast(1)
            if(abs(e.pitch)<.0001f&&abs(e.tempo-1)<.0001f){for(f in 0 until e.frames){if(f%4096==0L){check();progress(f.toFloat()/e.frames)};for(ch in 0 until c)w.sample(input(f,ch))};return}
            val hop=(source.rate*.02).toInt().coerceAtLeast(128);val search=hop/2;var tail=FloatArray(hop*c);var position=0L
            val block=FloatArray((ceil((2*search+2*hop+8)*ratio).toInt()+4)*c)
            while(position<frames){check();val expected=position*e.tempo/ratio;var best=expected;var score=Double.NEGATIVE_INFINITY
                val base=floor(max(0.0,expected-search)*ratio).toLong();val end=ceil((expected+search+2*hop+2)*ratio).toLong()+2
                val count=((end-base)*c).toInt();for(i in 0 until count)block[i]=input(base+i/c,i%c)
                fun cached(pos:Double,ch:Int):Float{val f=floor(pos).toLong();val t=(pos-f).toFloat();val i=((f-base)*c+ch).toInt();return (if(i in 0 until count)block[i]else 0f)*(1-t)+(if(i+c in 0 until count)block[i+c]else 0f)*t}

                if(position>0){var offset=-search;while(offset<=search){val candidate=max(0.0,expected+offset);var dot=0.0;var aa=1e-12;var bb=1e-12
                    for(i in 0 until hop step 16){var a=0.0;var b=0.0;for(ch in 0 until c){a+=tail[i*c+ch];b+=cached((candidate+i)*ratio,ch)};dot+=a*b;aa+=a*a;bb+=b*b}
                    val s=dot/sqrt(aa*bb)-.001*abs(offset).toDouble()/search;if(s>score){score=s;best=candidate};offset+=8}
                }
                for(i in 0 until min(hop.toLong(),frames-position).toInt()){val t=i.toFloat()/hop;for(ch in 0 until c){val v=cached((best+i)*ratio,ch);w.sample(if(position==0L)v else tail[i*c+ch]*(1-t)+v*t)}}
                for(i in 0 until hop)for(ch in 0 until c)tail[i*c+ch]=cached((best+hop+i)*ratio,ch)
                position+=hop;progress(position.toFloat()/frames)
            }
        }}
    }
    fun fft(re:DoubleArray,im:DoubleArray,inverse:Boolean=false){
        val n=re.size;var j=0
        for(i in 1 until n){var bit=n shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val a=re[i];re[i]=re[j];re[j]=a;val b=im[i];im[i]=im[j];im[j]=b}}
        var len=2;while(len<=n){val angle=(if(inverse)2 else -2)*PI/len;val wr=cos(angle);val wi=sin(angle)
            for(base in 0 until n step len){var cr=1.0;var ci=0.0;for(k in 0 until len/2){val a=base+k;val b=a+len/2;val tr=re[b]*cr-im[b]*ci;val ti=re[b]*ci+im[b]*cr;re[b]=re[a]-tr;im[b]=im[a]-ti;re[a]+=tr;im[a]+=ti;val next=cr*wr-ci*wi;ci=cr*wi+ci*wr;cr=next}}
            len*=2
        };if(inverse)for(i in re.indices){re[i]/=n;im[i]/=n}
    }
    private val window=DoubleArray(FFT){sqrt(.5-.5*cos(2*PI*it/FFT))}
    fun profile(source:AudioSource,e:AudioEdit,a:Long,b:Long,check:()->Unit={}):FloatArray{
        require(b-a>=FFT){"Select at least ${"%.2f".format(FFT.toDouble()/source.rate)} seconds of background noise"}
        val power=FloatArray(FFT/2+1);var count=0
        AudioPcmReader(source.file,source.channels).use{r->val input=mapped(source,e,r)
            var pos=a;while(pos+FFT<=b&&count<128){check();for(ch in 0 until source.channels){val re=DoubleArray(FFT){input(pos+it,ch)*window[it]};val im=DoubleArray(FFT);fft(re,im);for(i in power.indices)power[i]+=(re[i]*re[i]+im[i]*im[i]).toFloat()};count++;pos+=max((b-a)/128,FFT/2L)}
        };require(count>0);for(i in power.indices)power[i]/=(count*source.channels);return power
    }
    private fun denoise(source:AudioSource,file:File,out:File,power:FloatArray,amount:Float,check:()->Unit,progress:(Float)->Unit){
        val c=source.channels;val frames=file.length()/(4*c);val hop=FFT/2;val overlap=Array(c){DoubleArray(hop)}
        AudioPcmReader(file,c).use{r->AudioPcmWriter(out).use{w->var pos=-hop.toLong()
            while(pos<frames){check();val block=Array(c){DoubleArray(hop)}
                for(ch in 0 until c){val re=DoubleArray(FFT){r.get(pos+it,ch)*window[it]};val im=DoubleArray(FFT);fft(re,im)
                    val gains=DoubleArray(FFT/2+1){i->val p=re[i]*re[i]+im[i]*im[i];sqrt(max(.01,1-amount*2.5*power[i]/max(p,1e-16)))}
                    for(i in 0 until FFT){val k=if(i<=FFT/2)i else FFT-i;val g=(gains[max(0,k-1)]+2*gains[k]+gains[min(FFT/2,k+1)])/4;re[i]*=g;im[i]*=g};fft(re,im,true)
                    for(i in 0 until hop){block[ch][i]=re[i]*window[i]+overlap[ch][i];overlap[ch][i]=re[i+hop]*window[i+hop]}
                }
                if(pos>=0)for(i in 0 until min(hop.toLong(),frames-pos).toInt())for(ch in 0 until c)w.sample(block[ch][i].toFloat())
                pos+=hop;progress(pos.toFloat()/frames)
            }
        }}
    }
    private class Filter(val b0:Double,val b1:Double,val b2:Double,val a1:Double,val a2:Double){var z1=0.0;var z2=0.0;fun sample(v:Double):Double{val y=b0*v+z1;z1=b1*v-a1*y+z2;z2=b2*v-a2*y;return y}}
    private fun eq(rate:Int,hz:Double,db:Float):Filter{val a=10.0.pow(db/40.0);val w=2*PI*min(hz,rate*.4)/rate;val alpha=sin(w)/2;val d=1+alpha/a;return Filter((1+alpha*a)/d,-2*cos(w)/d,(1-alpha*a)/d,-2*cos(w)/d,(1-alpha/a)/d)}
    data class Result(val frames:Long,val peakDb:Double,val clipped:Long)
    data class Context(val offset:Long,val total:Long)
    fun render(source:AudioSource,e:AudioEdit,output:File,scratch:File,profile:FloatArray?,progress:(String,Float)->Unit={_,_->},check:()->Unit={},context:Context?=null):Result{
        require(e.frames>0&&e.frames<=source.rate*60L*120){"Keep edits within two hours"};requireWaveSize(source.channels,(e.frames/e.tempo).toLong().coerceAtLeast(1));scratch.mkdirs()
        val time=File(scratch,"timing.pcm");val clean=File(scratch,"clean.pcm");val fx=File(scratch,"effects.pcm")
        try{
            timing(source,e,time,{progress("Pitch & tempo",it*.35f)},check)
            val input=if(e.noise>0){require(profile?.size==FFT/2+1){"Learn a noise sample in Clean first"};denoise(source,time,clean,profile,e.noise,check){progress("Reducing noise",.35f+it*.25f)};clean}else time
            val c=source.channels;val frames=input.length()/(4*c);var peak=0.0
            val filters=Array(c){listOfNotNull(if(e.bass!=0f)eq(source.rate,120.0,e.bass)else null,if(e.mid!=0f)eq(source.rate,1000.0,e.mid)else null,if(e.treble!=0f)eq(source.rate,8000.0,e.treble)else null)}
            val previous=DoubleArray(c);val high=DoubleArray(c);val dcx=DoubleArray(c);val dcy=DoubleArray(c);var envelope=0.0;var gateGain=1.0
            val dcAlpha=exp(-2*PI*3/source.rate);val highAlpha=exp(-2*PI*80/source.rate);val attack=exp(-1.0/(.005*source.rate));val release=exp(-1.0/(.12*source.rate));val gain=10.0.pow(e.gain/20.0)
            val values=DoubleArray(c)
            AudioPcmReader(input,c).use{r->AudioPcmWriter(fx).use{w->for(f in 0 until frames){if(f%4096==0L){check();progress("Adjusting sound",.6f+.2f*f/frames)}
                for(ch in 0 until c){var v=r.get(f,ch).toDouble();if(e.dc){val y=v-dcx[ch]+dcAlpha*dcy[ch];dcx[ch]=v;dcy[ch]=y;v=y};if(e.highPass){val y=highAlpha*(high[ch]+v-previous[ch]);previous[ch]=v;high[ch]=y;v=y};for(filter in filters[ch])v=filter.sample(v);values[ch]=v*gain}
                val dynamics=e.compressor||e.gate> -80
                val level=if(dynamics)values.maxOf{abs(it)}else 0.0;val coeff=if(level>envelope)attack else release;envelope=coeff*envelope+(1-coeff)*level
                val db=if(dynamics)20*log10(max(envelope,1e-12))else -120.0;val compression=if(e.compressor&&db> -18)10.0.pow(((-18+(db+18)/3)-db)/20)else 1.0
                val gateTarget=if(e.gate> -80&&db<e.gate)0.0 else 1.0;gateGain+=(gateTarget-gateGain)*(if(gateTarget>gateGain).02 else .001)
                val timeline=f+(context?.offset?:0);val total=context?.total?:frames
                val fade=min(if(e.fadeIn>0)(timeline.toDouble()/(source.rate*e.fadeIn)).coerceIn(0.0,1.0)else 1.0,if(e.fadeOut>0)((total-1-timeline).toDouble()/(source.rate*e.fadeOut)).coerceIn(0.0,1.0)else 1.0)
                for(v in values){val y=v*compression*gateGain*fade;peak=max(peak,abs(y));w.sample(y.toFloat())}
            }}}
            val norm=if(e.normalize&&peak>1e-12)10.0.pow(-1.0/20)/peak else 1.0
            // Constant peak limiting preserves shape and prevents hard clipping; peak
            // normalization is optional while the default overload guard only reduces.
            val ceiling=10.0.pow(-.1/20);val scale=if(e.limiter&&peak*norm>ceiling)ceiling/peak else norm;var clipped=0L
            output.outputStream().buffered(65536).use{out->writeWaveHeader(out,source.rate,c,frames)
                val bytes=ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)
                AudioPcmReader(fx,c).use{r->for(f in 0 until frames){if(f%4096==0L){check();progress("Finishing audio",.8f+.2f*f/frames)};for(ch in 0 until c){val v=r.get(f,ch)*scale;if(abs(v)>1)clipped++;if(bytes.remaining()<2){out.write(bytes.array(),0,bytes.position());bytes.clear()};bytes.putShort((v.coerceIn(-1.0,1.0)*32767).roundToInt().toShort())}}};out.write(bytes.array(),0,bytes.position())
            };return Result(frames,if(peak*scale>0)20*log10(peak*scale)else -120.0,clipped)
        }finally{time.delete();clean.delete();fx.delete()}
    }
    fun requireWaveSize(channels:Int,frames:Long):Long{require(channels in 1..2&&frames in 1..((0xffffffffL-36)/(channels*2))){"This export exceeds the WAV size limit. Trim the track or increase its tempo."};return frames*channels*2}
    fun writeWaveHeader(out:OutputStream,rate:Int,channels:Int,frames:Long){val size=requireWaveSize(channels,frames);val b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".toByteArray());b.putInt((size+36).toInt());b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(channels.toShort());b.putInt(rate);b.putInt(rate*channels*2);b.putShort((channels*2).toShort());b.putShort(16);b.put("data".toByteArray());b.putInt(size.toInt());out.write(b.array())}
}
