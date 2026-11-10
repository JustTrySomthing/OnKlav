@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.*

internal val videoTitleAnimations=listOf("None","Fade","Slide","Pop")
internal data class VideoTitlePose(val alpha:Float=1f,val y:Float=0f,val scale:Float=1f,val moving:Boolean=false)
internal fun videoTitlePose(t:VideoTitle,ms:Long):VideoTitlePose {
    val duration=if(t.animationDuration>0)t.animationDuration else t.end-t.start
    val local=ms-t.start+t.animationOffset;val ramp=min(400L,duration/2).coerceAtLeast(1)
    val enter=(local.toFloat()/ramp).coerceIn(0f,1f);val exit=((duration-local).toFloat()/ramp).coerceIn(0f,1f)
    fun smooth(x:Float)=x*x*(3-2*x)
    fun alpha(name:String,f:Float)=if(name=="None")1f else smooth(f)
    val scale=(if(t.entrance=="Pop") .72f+.28f*smooth(enter)else 1f)*(if(t.exit=="Pop").72f+.28f*smooth(exit)else 1f)
    val y=(if(t.entrance=="Slide")(1-smooth(enter))*.14f else 0f)-(if(t.exit=="Slide")(1-smooth(exit))*.14f else 0f)
    return VideoTitlePose(alpha(t.entrance,enter)*alpha(t.exit,exit),y,scale,t.entrance!="None"&&enter<1f||t.exit!="None"&&exit<1f)
}

/** One texture above every video track. Glyph bitmaps are reused throughout animations. */
internal class VideoTitleOverlay(private val titles:List<VideoTitle>,private val width:Int,private val height:Int):BitmapOverlay(){
    private var active:List<VideoTitle>?=null;private var bitmap:Bitmap?=null;private var stamp=Long.MIN_VALUE
    private val glyphs=mutableMapOf<VideoTitle,Bitmap>();private var anchorX=0f;private var anchorY=0f;private var visible=false
    private fun glyph(title:VideoTitle):Bitmap {
        glyphs.remove(title)?.let{glyphs[title]=it;return it}
        val image=videoTitleBitmap(title,width,height)
        val budget=16L*1024*1024
        var bytes=glyphs.values.sumOf{it.byteCount.toLong()}+image.byteCount
        while(glyphs.isNotEmpty()&&bytes>budget){val oldest=glyphs.keys.first();val removed=requireNotNull(glyphs.remove(oldest));bytes-=removed.byteCount;removed.recycle()}
        glyphs[title]=image;return image
    }
    private fun update(us:Long){val ms=us/1000;val next=titles.filter{ms in it.start until it.end};val poses=next.map{videoTitlePose(it,ms)};val frame=if(poses.any{it.moving})ms else -1L;if(next==active&&frame==stamp)return
        glyphs.keys.filter{it !in next}.toList().forEach{glyphs.remove(it)?.recycle()}
        val pieces=next.mapIndexed{index,title->val pose=poses[index];val image=glyph(title);val w=image.width*pose.scale;val h=image.height*pose.scale;val x=(title.x+1)*width/2-w/2;val y=(1-title.y+pose.y)*height/2-h/2;Triple(title,RectF(x,y,x+w,y+h),pose.alpha)}
        val bounds=RectF();pieces.forEach{(_,rect,alpha)->if(alpha>0)bounds.union(rect)};val intersects=pieces.isNotEmpty()&&bounds.intersect(0f,0f,width.toFloat(),height.toFloat())
        val rendered=if(!intersects)Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)else{
            val left=floor(bounds.left);val top=floor(bounds.top);val w=(ceil(bounds.right)-left).toInt().coerceAtLeast(1);val h=(ceil(bounds.bottom)-top).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also{out->val canvas=Canvas(out);val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                pieces.forEach{(title,rect,alpha)->val image=glyph(title);paint.alpha=(alpha*255).roundToInt().coerceIn(0,255);canvas.drawBitmap(image,null,RectF(rect).apply{offset(-left,-top)},paint)}
                anchorX=((left+w/2f)/width*2-1).coerceIn(-1f,1f);anchorY=(1-(top+h/2f)/height*2).coerceIn(-1f,1f)
            }
        }
        val old=bitmap;bitmap=rendered;active=next;stamp=frame;visible=intersects;old?.recycle()
    }
    override fun getBitmap(presentationTimeUs:Long):Bitmap{update(presentationTimeUs);return requireNotNull(bitmap)}
    override fun getOverlaySettings(presentationTimeUs:Long):StaticOverlaySettings{update(presentationTimeUs);return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(anchorX,anchorY).setAlphaScale(if(visible)1f else 0f).build()}
    override fun release(){super.release();bitmap?.recycle();bitmap=null;active=null;glyphs.values.forEach{it.recycle()};glyphs.clear();stamp=Long.MIN_VALUE}
}
