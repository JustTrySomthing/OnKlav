package app.onklav.home

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

/** One bounded raster page, with gestures contained in its viewport. No live renderer on the UI thread. */
internal class PdfReadingView(c:Context):android.view.View(c){
 private var bitmap:Bitmap?=null;private var bounds:List<RectF> = emptyList();private var page=1 to 1
 private var zoom=1f;private var requestedZoom=1f;private var dx=0f;private var dy=0f;private var lastX=0f;private var lastY=0f
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG);private val mark=Paint().apply{color=0x66FFCC00}
 private val scale=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoom=(zoom*d.scaleFactor).coerceIn(1f,4f);clamp();invalidate();return true}})
 private val taps=GestureDetector(c,object:GestureDetector.SimpleOnGestureListener(){override fun onDown(e:MotionEvent)=true;override fun onDoubleTap(e:MotionEvent):Boolean{zoom=if(zoom>1f)1f else 2f;dx=0f;dy=0f;clamp();invalidate();return true}})
 init{contentDescription="PDF page. Pinch to zoom, drag to pan, double tap to fit.";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES}
 fun show(value:Bitmap,rects:List<RectF>,size:Pair<Int,Int>,level:Float){if(value!==bitmap){bitmap=value;zoom=1f;dx=0f;dy=0f};if(level!=requestedZoom){zoom=level;requestedZoom=level};bounds=rects;page=size;clamp();invalidate()}
 private fun fit():Float {val b=bitmap?:return 1f;return min(width.toFloat()/b.width,height.toFloat()/b.height)}
 private fun clamp(){val b=bitmap?:return;val s=fit()*zoom;val mx=max(0f,(b.width*s-width)/2);val my=max(0f,(b.height*s-height)/2);dx=dx.coerceIn(-mx,mx);dy=dy.coerceIn(-my,my)}
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){clamp()}
 override fun onDraw(canvas:Canvas){super.onDraw(canvas);val b=bitmap?:return;val s=fit()*zoom;val x=(width-b.width*s)/2+dx;val y=(height-b.height*s)/2+dy;canvas.save();canvas.translate(x,y);canvas.scale(s,s);canvas.drawBitmap(b,0f,0f,paint);canvas.scale(b.width.toFloat()/page.first,b.height.toFloat()/page.second);bounds.forEach{canvas.drawRect(it,mark)};canvas.restore()}
 override fun performClick():Boolean{super.performClick();return true}
 override fun onTouchEvent(e:MotionEvent):Boolean{scale.onTouchEvent(e);taps.onTouchEvent(e);when(e.actionMasked){MotionEvent.ACTION_DOWN->{lastX=e.x;lastY=e.y;parent?.requestDisallowInterceptTouchEvent(true)};MotionEvent.ACTION_MOVE->{if(!scale.isInProgress&&e.pointerCount==1){dx+=e.x-lastX;dy+=e.y-lastY;clamp();invalidate()};lastX=e.x;lastY=e.y};MotionEvent.ACTION_UP->{performClick();parent?.requestDisallowInterceptTouchEvent(false)};MotionEvent.ACTION_CANCEL->parent?.requestDisallowInterceptTouchEvent(false)};return true}
}
