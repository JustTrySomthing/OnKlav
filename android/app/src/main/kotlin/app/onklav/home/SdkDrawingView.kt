package app.onklav.home

import android.content.Context
import android.graphics.*
import android.view.*
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Local viewport and gesture preview only. Rust commits every completed edit. */
internal class SdkDrawingView(context:Context):View(context) {
 companion object {
  val ZOOM_IN_ACTION=R.id.sdk_canvas_zoom_in
  val ZOOM_OUT_ACTION=R.id.sdk_canvas_zoom_out
  val FIT_ACTION=R.id.sdk_canvas_fit
 }
 var send:(JSONObject,()->Unit)->Unit={_,done->done()}
 var viewportChanged:(String)->Unit={}
 private var viewportRestored=false
 var tool="move"
  set(value) { if(field!=value) cancelGesture();field=value }
 var ink=0xffe94242.toInt();var thickness=4f
 private var bitmap:Bitmap?=null
 private var document=JSONObject()
 private val emptyMarks=JSONArray()
 private var zoom=1f;private var ox=0f;private var oy=0f;private var ready=false
 private var stroke:JSONObject?=null;private var selected=-1;private var dx=0f;private var dy=0f
 private var downX=0f;private var downY=0f;private var lastX=0f;private var lastY=0f
 private var gesture=false;private var completion=0L
 private var moved=false;private var multi=false;private var waiting=false
 private val density=resources.displayMetrics.density
 private val zoomer=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener() {
  override fun onScale(detector:ScaleGestureDetector):Boolean {
   val base=fitScale();val next=(zoom*detector.scaleFactor).coerceIn(base,base*8)
   val px=(detector.focusX-ox)/zoom;val py=(detector.focusY-oy)/zoom
   ox=detector.focusX-px*next;oy=detector.focusY-py*next;zoom=next;clamp();invalidate();return true
  }
 })
 init { contentDescription="Image Annotation. Two Fingers to Zoom and Pan. Choose Move to Reposition Marks.";isFocusable=true }
 override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){
  super.onInitializeAccessibilityNodeInfo(info)
  if(bitmap!=null && isEnabled && !waiting){
   info.contentDescription="$contentDescription Zoom ${((zoom/fitScale())*100).roundToInt()} percent."
   info.addAction(AccessibilityNodeInfo.AccessibilityAction(ZOOM_IN_ACTION,"Zoom in"))
   info.addAction(AccessibilityNodeInfo.AccessibilityAction(ZOOM_OUT_ACTION,"Zoom out"))
   info.addAction(AccessibilityNodeInfo.AccessibilityAction(FIT_ACTION,"Fit image"))
  }
 }
 private fun zoomBy(factor:Float):Boolean {
  if(bitmap==null)return false;val base=fitScale();val next=(zoom*factor).coerceIn(base,base*8)
  if(next==zoom)return false
  cancelGesture();val x=(width/2f-ox)/zoom;val y=(areaHeight()/2f-oy)/zoom
  zoom=next;ox=width/2f-x*zoom;oy=areaHeight()/2f-y*zoom;clamp();invalidate()
  viewportChanged(viewport());sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);return true
 }
 override fun performAccessibilityAction(action:Int,args:Bundle?):Boolean {
  if(action in setOf(ZOOM_IN_ACTION,ZOOM_OUT_ACTION,FIT_ACTION)){
   if(!isEnabled||bitmap==null||waiting)return false
   return when(action){ZOOM_IN_ACTION->zoomBy(1.25f);ZOOM_OUT_ACTION->zoomBy(.8f);else->{cancelGesture();fit();true}}
  }
  return super.performAccessibilityAction(action,args)
 }
 override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
  if(isEnabled&&bitmap!=null&&!waiting&&!event.isCtrlPressed&&!event.isAltPressed&&!event.isMetaPressed){
   when(keyCode){
    KeyEvent.KEYCODE_PLUS,KeyEvent.KEYCODE_EQUALS,KeyEvent.KEYCODE_NUMPAD_ADD->return zoomBy(1.25f)
    KeyEvent.KEYCODE_MINUS,KeyEvent.KEYCODE_NUMPAD_SUBTRACT->return zoomBy(.8f)
    KeyEvent.KEYCODE_0,KeyEvent.KEYCODE_NUMPAD_0->{cancelGesture();fit();return true}
   }
   val step=32*density;val previousX=ox;val previousY=oy
   when(keyCode){KeyEvent.KEYCODE_DPAD_LEFT->ox+=step;KeyEvent.KEYCODE_DPAD_RIGHT->ox-=step;KeyEvent.KEYCODE_DPAD_UP->oy+=step;KeyEvent.KEYCODE_DPAD_DOWN->oy-=step;else->return super.onKeyDown(keyCode,event)}
   clamp();if(ox!=previousX||oy!=previousY){cancelGesture();invalidate();viewportChanged(viewport());return true}
  }
  return super.onKeyDown(keyCode,event)
 }
 private fun areaHeight()=maxOf(1f,height-72*density)
 private fun fitScale():Float=bitmap?.let {minOf(width.toFloat()/it.width,areaHeight()/it.height).coerceAtLeast(.001f)} ?: 1f
 fun fit() { zoom=fitScale();ox=((width-(bitmap?.width ?: 0)*zoom)/2);oy=((areaHeight()-(bitmap?.height ?: 0)*zoom)/2);ready=width>0;invalidate();if(bitmap!=null)viewportChanged(viewport()) }
 fun viewport(w:Int=width,h:Int=height):String {
  val b=bitmap?:return "";if(!ready||w<=0||h<=0)return ""
  val area=maxOf(1f,h-72*density);val base=minOf(w.toFloat()/b.width,area/b.height).coerceAtLeast(.001f)
  return JSONObject().put("id",document.optString("id")).put("scale",(zoom/base).coerceIn(1f,8f).toDouble())
   .put("x",((w/2f-ox)/zoom/b.width).coerceIn(0f,1f).toDouble()).put("y",((area/2f-oy)/zoom/b.height).coerceIn(0f,1f).toDouble()).toString()
 }
 fun restoreViewport(raw:String,force:Boolean=false) {
  if(!force&&viewportRestored||!ready||bitmap==null||raw.isEmpty())return
  runCatching{val saved=JSONObject(raw);require(saved.optString("id")==document.optString("id"));val scale=saved.getDouble("scale");val x=saved.getDouble("x");val y=saved.getDouble("y");require(scale.isFinite()&&scale in 1.0..8.0&&x.isFinite()&&x in 0.0..1.0&&y.isFinite()&&y in 0.0..1.0)
   zoom=fitScale()*scale.toFloat();ox=width/2f-x.toFloat()*bitmap!!.width*zoom;oy=areaHeight()/2f-y.toFloat()*bitmap!!.height*zoom;clamp();invalidate();viewportRestored=true;viewportChanged(viewport())}
 }
 private fun clamp() { val b=bitmap ?: return;val w=b.width*zoom;val h=b.height*zoom;ox=if(w<width) (width-w)/2 else ox.coerceIn(width-w,0f);oy=if(h<areaHeight()) (areaHeight()-h)/2 else oy.coerceIn(areaHeight()-h,0f) }
 fun update(doc:JSONObject,image:Bitmap?) {
  val changed=document.optString("id")!=doc.optString("id");val edited=document.optLong("revision",-1)!=doc.optLong("revision",-1)
  document=doc
  if(image!=null && image!==bitmap) { bitmap?.recycle();bitmap=image;ready=false }
  if(changed) { ready=false;viewportRestored=false }
  if(changed || edited) { completion++;waiting=false;cancelGesture() }
  if(!ready) fit();invalidate()
 }
 private fun cancelGesture() {
  gesture=false;stroke=null;selected=-1;multi=false;dx=0f;dy=0f
  parent?.requestDisallowInterceptTouchEvent(false);invalidate()
 }
 override fun setEnabled(enabled:Boolean) { if(!enabled) cancelGesture();super.setEnabled(enabled) }
 fun clear() { completion++;cancelGesture();viewportRestored=false; bitmap?.recycle();bitmap=null;document=JSONObject();stroke=null;selected=-1;waiting=false;invalidate() }
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
  val previous=viewport(oldw,oldh);super.onSizeChanged(w,h,oldw,oldh);cancelGesture()
  fit();if(previous.isNotEmpty())restoreViewport(previous,true)
 }
 override fun onDraw(c:Canvas) {
  val b=bitmap ?: return;if(!ready) fit()
  val save=c.save();c.translate(ox,oy);c.scale(zoom,zoom);c.clipRect(0,0,b.width,b.height);c.drawBitmap(b,0f,0f,null)
  val marks=document.optJSONArray("marks") ?: emptyMarks
  for(i in 0 until marks.length()) { val m=marks.getJSONObject(i);val layer=c.save();if(i==selected) c.translate(dx,dy);SdkDrawingImages.drawMark(c,m,b.width);c.restoreToCount(layer) }
  stroke?.let {SdkDrawingImages.drawMark(c,it,b.width)};c.restoreToCount(save)
 }
 private fun hit(x:Float,y:Float):Int {
  val marks=document.optJSONArray("marks") ?: return -1
  for(i in marks.length()-1 downTo 0) {
   val m=marks.getJSONObject(i);val bounds=SdkDrawingImages.bounds(m)
   if(m.getString("tool")=="text") { if(bounds.contains(x,y)) return i }
   else { val pts=m.getJSONArray("points");val tolerance=maxOf(m.getDouble("width").toFloat(),20*density/zoom);val mx=m.optDouble("x").toFloat();val my=m.optDouble("y").toFloat()
    for(j in 0 until pts.length()) { val p=pts.getJSONArray(j);if(hypot(x-p.getDouble(0).toFloat()-mx,y-p.getDouble(1).toFloat()-my)<tolerance) return i }
   }
  };return -1
 }
 private fun submit(value:JSONObject) { val ticket=++completion;waiting=true;send(value) { if(ticket==completion) {waiting=false;cancelGesture()} } }
 override fun onTouchEvent(e:MotionEvent):Boolean {
  val b=bitmap ?: return true;if(!isEnabled || waiting) return true
  if(e.actionMasked!=MotionEvent.ACTION_DOWN && !gesture) return true
  zoomer.onTouchEvent(e)
  if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN) { multi=true;stroke=null;selected=-1;dx=0f;dy=0f;lastX=(e.getX(0)+e.getX(1))/2;lastY=(e.getY(0)+e.getY(1))/2;invalidate();return true }
  if(e.pointerCount>1) {if(e.actionMasked==MotionEvent.ACTION_MOVE) {val x=(e.getX(0)+e.getX(1))/2;val y=(e.getY(0)+e.getY(1))/2;if(!zoomer.isInProgress) {ox+=x-lastX;oy+=y-lastY;clamp()};lastX=x;lastY=y;invalidate()};return true}
  val x=(e.x-ox)/zoom;val y=(e.y-oy)/zoom
  when(e.actionMasked) {
   MotionEvent.ACTION_DOWN->{gesture=true;parent?.requestDisallowInterceptTouchEvent(true);multi=false;moved=false;downX=e.x;downY=e.y;lastX=e.x;lastY=e.y;selected=-1;dx=0f;dy=0f
    if(tool=="move") selected=hit(x,y)
    else if(tool=="pen"||tool=="highlight") { if(x in 0f..b.width.toFloat() && y in 0f..b.height.toFloat()) stroke=JSONObject().put("tool",tool).put("color",ink.toLong() and 0xffffffffL).put("width",maxOf(1f,thickness*b.width/360f)*(if(tool=="highlight") 4 else 1)).put("x",0).put("y",0).put("points",JSONArray().put(JSONArray().put(x.toDouble()).put(y.toDouble()))) }
   }
   MotionEvent.ACTION_MOVE->{if(hypot(e.x-downX,e.y-downY)>4*density) moved=true
    if(!multi) { if(stroke!=null) {val points=stroke!!.getJSONArray("points");if(points.length()<2000 && x in 0f..b.width.toFloat() && y in 0f..b.height.toFloat()) points.put(JSONArray().put(x.toDouble()).put(y.toDouble()))}
     else if(selected>=0) {val bounds=SdkDrawingImages.bounds(document.getJSONArray("marks").getJSONObject(selected));dx=((e.x-downX)/zoom).coerceIn(-bounds.left,b.width-bounds.right);dy=((e.y-downY)/zoom).coerceIn(-bounds.top,b.height-bounds.bottom)}
     else if(tool=="move") {ox+=e.x-lastX;oy+=e.y-lastY;clamp()}
    };lastX=e.x;lastY=e.y
   }
   MotionEvent.ACTION_UP->{if(!multi) {
    val pending=stroke
    if(pending!=null) submit(JSONObject().put("op","stroke").put("mark",pending))
    else if(selected>=0 && moved) submit(JSONObject().put("op","move").put("index",selected).put("dx",dx.toDouble()).put("dy",dy.toDouble()))
    else if(!moved && tool=="erase") {val i=hit(x,y);if(i>=0) submit(JSONObject().put("op","erase").put("index",i))}
    else if(!moved && tool=="text" && x in 0f..b.width.toFloat() && y in 0f..b.height.toFloat()) submit(JSONObject().put("op","text").put("x",x.toDouble()).put("y",y.toDouble()))
    else if(!moved && selected>=0 && document.getJSONArray("marks").getJSONObject(selected).getString("tool")=="text") submit(JSONObject().put("op","text").put("index",selected))
   };gesture=false;if(!waiting) {stroke=null;selected=-1};viewportChanged(viewport());performClick();parent?.requestDisallowInterceptTouchEvent(false)}
   MotionEvent.ACTION_CANCEL->{cancelGesture();viewportChanged(viewport())}
  };invalidate();return true
 }
 override fun performClick():Boolean {super.performClick();return true}
}
