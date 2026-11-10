@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.graphics.Matrix
import android.text.SpannableString
import android.text.Spanned
import android.text.style.*
import androidx.media3.common.*
import androidx.media3.common.audio.*
import androidx.media3.effect.*
import androidx.media3.transformer.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*

internal fun videoEnvelope(ms:Long,duration:Long,fadeIn:Long,fadeOut:Long):Float = min(if(fadeIn>0)ms.toFloat()/fadeIn else 1f,if(fadeOut>0)(duration-ms).toFloat()/fadeOut else 1f).coerceIn(0f,1f)
internal class VideoGain(private val gain:Float,private val duration:Long,private val fadeIn:Long=0,private val fadeOut:Long=0):BaseAudioProcessor(){
    private var frames=0L
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat{if(inputAudioFormat.encoding !in listOf(C.ENCODING_PCM_16BIT,C.ENCODING_PCM_FLOAT))throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat);return inputAudioFormat}
    override fun onFlush(){frames=0}
    override fun queueInput(inputBuffer:ByteBuffer){val out=replaceOutputBuffer(inputBuffer.remaining());val channels=inputAudioFormat.channelCount;val floating=inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT
        while(inputBuffer.remaining()>=(if(floating)4 else 2)*channels){val g=gain*videoEnvelope(frames*1000/inputAudioFormat.sampleRate,duration,fadeIn,fadeOut);repeat(channels){if(floating)out.putFloat((inputBuffer.float*g).coerceIn(-1f,1f))else out.putShort((inputBuffer.short*g).roundToInt().coerceIn(-32768,32767).toShort())};frames++};out.flip()}
}
internal object VideoComposition{
    fun directPreview(p:VideoProject):Boolean=p.music.isEmpty()&&directVisualPreview(p)
    fun directVisualPreview(p:VideoProject):Boolean{val c=p.clips.singleOrNull()?:return false;return p.layers.isEmpty()&&c.keyframes.isEmpty()&&c.rates.isEmpty()&&!c.image&&!p.fill&&p.ratio=="16:9"&&c.speed==1f&&c.volume<=1f&&c.rotate==0&&!c.flipX&&!c.flipY&&c.zoom==1f&&c.panX==0f&&c.panY==0f&&!c.motion&&c.brightness==0f&&c.contrast==0f&&c.saturation==0f&&c.hue==0f&&c.warmth==0f&&c.fadeIn==0L&&c.fadeOut==0L&&c.titles.isEmpty()&&c.lut.isEmpty()}
    fun dimensions(p:VideoProject,preview:Boolean=false):Pair<Int,Int>{val r=p.ratio.split(':').map{it.toFloat()};val base=if(preview)min(p.height,p.previewHeight)else p.height;val aspect=r[0]/r[1];return if(aspect>=1f)((base*aspect/2).roundToInt()*2) to base else base to ((base/aspect/2).roundToInt()*2)}
    fun build(p:VideoProject,folder:File,preview:Boolean=false,compatibility:Boolean=false,audioOnly:Boolean=false):Composition{
        require(p.clips.isNotEmpty());val (w,h)=dimensions(p,preview)
        fun item(c:VideoClip,overlay:Boolean=false,layer:VideoLayer?=null,index:Int=-1):EditedMediaItem{
            val media=MediaItem.Builder().setUri(videoMediaFile(folder,c,preview).toURI().toString()).apply{if(c.image)setImageDurationMs(c.duration)else setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build())}.build()
            val a=mutableListOf<AudioProcessor>();val v=mutableListOf<Effect>();if(!audioOnly&&layer!=null&&(layer.chroma||layer.mask!=0))v.add(VideoCutoutEffect(layer))
            if(!c.image&&(c.speed!=1f||c.rates.isNotEmpty())){val effects=Effects.createExperimentalSpeedChangingEffect(videoSpeedProvider(c));a.add(effects.first);if(!audioOnly)v.add(effects.second)}
            a.add(VideoGain(c.volume,c.duration,max(c.fadeIn,if(index>=0)videoOverlap(p.clips,index)else 0),max(c.fadeOut,if(index>=0)videoOverlap(p.clips,index+1)else 0)))
            if(!audioOnly&&(c.rotate!=0||c.flipX||c.flipY))v.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(c.rotate.toFloat()).setScale(if(c.flipX)-1f else 1f,if(c.flipY)-1f else 1f).build())
            if(!audioOnly&&!overlay)v.add(Presentation.createForWidthAndHeight(w,h,if(p.fill)Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT))
            if(!audioOnly&&!overlay&&(c.keyframes.isNotEmpty()||c.motion||c.zoom!=1f||c.panX!=0f||c.panY!=0f))v.add(MatrixTransformation{time->val pose=videoPose(c,time/1000);Matrix().apply{setScale(pose.zoom,pose.zoom);postRotate(pose.rotation);postTranslate(pose.x,pose.y)}})
            if(!audioOnly&&c.brightness!=0f)v.add(Brightness(c.brightness));if(!audioOnly&&c.contrast!=0f)v.add(Contrast(c.contrast))
            if(!audioOnly&&(c.saturation!=0f||c.hue!=0f))v.add(HslAdjustment.Builder().adjustSaturation(c.saturation).adjustHue(c.hue).build())
            if(!audioOnly&&c.warmth!=0f)v.add(RgbAdjustment.Builder().setRedScale(1+c.warmth*.25f).setBlueScale(1-c.warmth*.25f).build())
            if(!audioOnly&&c.lut.isNotEmpty()&&c.lutAmount>0)v.add(File(folder,c.lut).let{file->require(file.length() in 1..4_000_000){"Choose a LUT smaller than 4 MB"};VideoLut.parse(file.readText()).effect(c.lutAmount)})
            if(!audioOnly&&(c.fadeIn>0||c.fadeOut>0))v.add(RgbMatrix{time,_->val x=videoEnvelope(time/1000,c.duration,c.fadeIn,c.fadeOut);floatArrayOf(x,0f,0f,0f,0f,x,0f,0f,0f,0f,x,0f,0f,0f,0f,1f)})
            if(!audioOnly&&index>=0&&index%2==0&&p.clips.indices.any{videoOverlap(p.clips,it)>0}){val next=p.clips.getOrNull(index+1);v.add(VideoTransitionEffect(c.transition,videoOverlap(p.clips,index),next?.transition?:"None",videoOverlap(p.clips,index+1),c.duration))}
            return EditedMediaItem.Builder(media).setDurationUs((if(c.image)c.duration else c.sourceMs)*1000).setFrameRate(p.fps).setRemoveAudio(overlay).setRemoveVideo(audioOnly).setEffects(Effects(a,if(audioOnly)emptyList()else v)).build()
        }
        val overlapping=p.clips.indices.any{videoOverlap(p.clips,it)>0}
        val activeLayers=if(audioOnly)emptyList()else p.layers.filter{it.offset<p.duration}.asReversed()
        val sequences=activeLayers.map{layer->val seq=EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO));if(layer.offset>0)seq.addGap(layer.offset*1000);seq.addItem(item(layer.clip,true,layer));val tail=p.duration-layer.offset-layer.clip.duration;if(tail>0)seq.addGap(tail*1000);seq.build()}.toMutableList()
        val mainIndex=sequences.size
        fun mainItem(seq:EditedMediaItemSequence.Builder,c:VideoClip,index:Int){if(audioOnly&&(c.image||!c.hasAudio))seq.addGap(c.duration*1000)else seq.addItem(item(c,index=index))}
        val mainTypes=if(audioOnly)setOf(C.TRACK_TYPE_AUDIO)else setOf(C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_VIDEO)
        if(!overlapping){val seq=EditedMediaItemSequence.Builder(mainTypes);p.clips.forEachIndexed{i,c->mainItem(seq,c,i)};sequences.add(seq.build())}
        else for(track in 0..1){val seq=EditedMediaItemSequence.Builder(mainTypes);var end=0L
            p.clips.forEachIndexed{i,c->if(i%2==track){val start=p.clipStart(i);if(start>end)seq.addGap((start-end)*1000);mainItem(seq,c,i);end=start+c.duration}}
            if(end<p.duration)seq.addGap((p.duration-end)*1000);sequences.add(seq.build())
        }
        p.music.filter{it.offset<p.duration}.forEach{m->
            val audioClip=VideoClip(file=m.file,name=m.name,sourceMs=m.sourceMs,start=m.start,end=m.end,speed=m.speed,rates=m.rates)
            val end=min(m.end,videoSourceAt(audioClip,p.duration-m.offset));if(end<=m.start)return@forEach
            val media=MediaItem.Builder().setUri(File(folder,m.file).toURI().toString()).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.start).setEndPositionMs(end).build()).build()
            val processors=mutableListOf<AudioProcessor>();if(m.speed!=1f||m.rates.isNotEmpty())processors.add(Effects.createExperimentalSpeedChangingEffect(videoSpeedProvider(audioClip)).first)
            processors.add(VideoGain(m.volume,videoOutputDuration(m.start,end,m.speed,m.rates),m.fadeIn,m.fadeOut))
            if(m.pan!=0f||m.tone!="Original"||m.duck&&!m.narration){val ranges=if(m.duck&&!m.narration)p.music.filter{it.narration&&it.volume>0}.map{it.offset to min(p.duration,it.offset+it.duration)}else emptyList();processors.add(VideoSound(m.pan,m.tone,m.offset,ranges))}
            if(m.noise>0||m.rumble)processors.add(VideoNoise(m.noise,m.rumble))
            if(m.pitch!=0f)processors.add(SonicAudioProcessor().apply{setPitch(2f.pow(m.pitch/12f))})
            val audioItem=EditedMediaItem.Builder(media).setDurationUs(m.sourceMs*1000).setRemoveVideo(true).setEffects(Effects(processors,emptyList())).build()
            val seq=EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO));if(m.offset>0)seq.addGap(m.offset*1000);seq.addItem(audioItem);val tail=p.duration-m.offset-videoOutputDuration(m.start,end,m.speed,m.rates);if(tail>0)seq.addGap(tail*1000);sequences.add(seq.build())
        }
        val compositor=object:VideoCompositorSettings{
            override fun getOutputSize(inputSizes:List<androidx.media3.common.util.Size>)=androidx.media3.common.util.Size(w,h)
            override fun getOverlaySettings(inputId:Int,presentationTimeUs:Long):OverlaySettings{
                val ms=presentationTimeUs/1000
                if(inputId<mainIndex){val layer=activeLayers[inputId];val pose=videoPose(layer.clip,(ms-layer.offset).coerceAtLeast(0));return StaticOverlaySettings.Builder().setBackgroundFrameAnchor((layer.x+pose.x).coerceIn(-1f,1f),(layer.y+pose.y).coerceIn(-1f,1f)).setScale(layer.scale*pose.zoom,layer.scale*pose.zoom).setRotationDegrees(layer.rotation+pose.rotation).setAlphaScale(if(ms in layer.offset until layer.offset+layer.clip.duration)layer.opacity*pose.opacity else 0f).build()}
                if(inputId in mainIndex..(mainIndex+if(overlapping)1 else 0)){val track=inputId-mainIndex;val i=p.clips.indices.firstOrNull{i->(!overlapping||i%2==track)&&ms in p.clipStart(i) until p.clipStart(i)+p.clips[i].duration};return StaticOverlaySettings.Builder().setAlphaScale(if(i==null)0f else videoPose(p.clips[i],ms-p.clipStart(i)).opacity).build()}
                return StaticOverlaySettings.Builder().build()
            }
        }
        val projectTitles=p.clips.flatMapIndexed{i,c->val start=p.clipStart(i);c.titles.map{it.copy(start=it.start+start,end=it.end+start)}}
        val projectEffects=mutableListOf<Effect>();if(!audioOnly&&!compatibility)projectEffects.add(FrameDropEffect.createDefaultFrameDropEffect(p.fps.toFloat()))
        if(!audioOnly&&projectTitles.isNotEmpty())projectEffects.add(OverlayEffect(listOf(VideoTitleOverlay(projectTitles,w,h))))
        return Composition.Builder(sequences).apply{if(!audioOnly&&(activeLayers.isNotEmpty()||overlapping))setVideoCompositorSettings(compositor)}.setEffects(Effects(emptyList(),projectEffects)).setHdrMode(if(compatibility||!preview)Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC else Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
    }
}

/** Bounded wrapping prevents long captions from allocating an oversized GPU texture. */
internal fun videoTitleBitmap(title:VideoTitle,width:Int,height:Int):android.graphics.Bitmap{
    val size=(height*.045f*title.size).coerceAtLeast(8f)
    val text=SpannableString(title.text).apply{
        setSpan(ForegroundColorSpan(title.color),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if(title.bold)setSpan(StyleSpan(android.graphics.Typeface.BOLD),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if(title.background)setSpan(BackgroundColorSpan(0xB3000000.toInt()),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val paint=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textSize=size}
    val layout=android.text.StaticLayout.Builder.obtain(text,0,text.length,paint,(width*.86f).roundToInt().coerceAtLeast(16))
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(true)
        .setMaxLines((height*.72f/(size*1.3f)).toInt().coerceAtLeast(1)).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    return android.graphics.Bitmap.createBitmap(layout.width,layout.height.coerceIn(1,(height*.8f).roundToInt().coerceAtLeast(1)),android.graphics.Bitmap.Config.ARGB_8888).also{layout.draw(android.graphics.Canvas(it))}
}

internal fun videoSpeedProvider(c:VideoClip)=object:SpeedProvider {
    override fun getSpeed(timeUs:Long)=videoRateAt(c.start+timeUs/1000,c.speed,c.rates)
    override fun getNextSpeedChangeTimeUs(timeUs:Long)=c.rates.firstOrNull{it.at>c.start+timeUs/1000&&it.at<c.end}?.let{(it.at-c.start)*1000}?:C.TIME_UNSET
}
