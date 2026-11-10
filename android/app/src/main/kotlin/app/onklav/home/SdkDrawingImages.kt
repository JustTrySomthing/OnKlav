package app.onklav.home

import android.graphics.*
import android.media.ExifInterface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Host-owned image codec and image-space renderer, shared by SDK packages. */
internal object SdkDrawingImages {
 fun read(input:InputStream,authorize:()->Unit):ByteArray {
  val out=ByteArrayOutputStream();val block=ByteArray(8192)
  while(true) { authorize();val n=input.read(block);if(n<0) break;require(out.size()+n<=20*1024*1024) { "Choose an Image Under 20 MB" };out.write(block,0,n) };return out.toByteArray()
 }
 fun normalize(bytes:ByteArray):ByteArray {
  val size=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,size)
  require(size.outWidth>0 && size.outHeight>0 && size.outWidth.toLong()*size.outHeight<=100_000_000) { "Choose a Still Image up to 100 Megapixels" }
  var sample=1;while(size.outWidth/sample>1800 || size.outHeight/sample>1800) sample*=2
  val source=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})) { "Could Not Open This Image" }
  val owned=mutableSetOf(source)
  try {
   val orientation=runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
   val transform=Matrix().apply { when(orientation) { 2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f) } }
   val oriented=Bitmap.createBitmap(source,0,0,source.width,source.height,transform,true).also { owned.add(it) }
   val paper=Bitmap.createBitmap(oriented.width,oriented.height,Bitmap.Config.ARGB_8888).also { owned.add(it) }
   Canvas(paper).apply { drawColor(Color.WHITE);drawBitmap(oriented,0f,0f,null) };return jpeg(paper,600*1024)
  } finally { owned.forEach { it.recycle() } }
 }
 fun jpeg(bitmap:Bitmap,limit:Int):ByteArray {
  for(quality in listOf(90,82,74,65,55,45,35)) { val out=ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out));if(out.size()<=limit) return out.toByteArray() };error("This Image Is Too Detailed. Choose a Smaller Picture.")
 }
 fun image(doc:JSONObject):Bitmap {
  val raw=Base64.decode(doc.getString("image"),Base64.NO_WRAP);require(raw.size<=600*1024)
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(raw,0,raw.size,bounds)
  require(bounds.outWidth in 1..1800 && bounds.outHeight in 1..1800 && bounds.outWidth==doc.getInt("width") && bounds.outHeight==doc.getInt("height")) { "Invalid annotated image dimensions" }
  return requireNotNull(BitmapFactory.decodeByteArray(raw,0,raw.size))
 }
 fun thumbnail(doc:JSONObject):Bitmap? {
  val raw=Base64.decode(doc.getString("image"),Base64.NO_WRAP);require(raw.size<=600*1024)
  var sample=1;while(doc.getInt("width")/sample>96 || doc.getInt("height")/sample>96) sample*=2
  return BitmapFactory.decodeByteArray(raw,0,raw.size,BitmapFactory.Options().apply {inSampleSize=sample})
 }
 fun layout(text:String,imageWidth:Int):StaticLayout {
  val font=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=maxOf(18f,imageWidth/24f) }
  val width=maxOf(80,minOf(imageWidth-16,(imageWidth*.55f).toInt()))
  return StaticLayout.Builder.obtain(text,0,text.length,font,maxOf(1,width-20)).setIncludePad(false).build()
 }
 fun textSize(mark:JSONObject,doc:JSONObject):JSONObject {
  val text=mark.getString("text");require(text.length<=400 && text.isNotBlank()) { "Enter a Text Note" }
  val layout=layout(text,doc.getInt("width"));val w=layout.width+20;val h=layout.height+20
  require(w<=doc.getInt("width") && h<=doc.getInt("height")) { "Shorten the Note So It Fits Inside This Picture" }
  return JSONObject().put("box_width",w).put("box_height",h)
 }
 fun bounds(mark:JSONObject):RectF {
  val x=mark.optDouble("x").toFloat();val y=mark.optDouble("y").toFloat()
  if(mark.getString("tool")=="text") return RectF(x,y,x+mark.getDouble("box_width").toFloat(),y+mark.getDouble("box_height").toFloat())
  val points=mark.getJSONArray("points");val b=RectF(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE)
  for(i in 0 until points.length()) { val p=points.getJSONArray(i);val px=p.getDouble(0).toFloat()+x;val py=p.getDouble(1).toFloat()+y;b.left=minOf(b.left,px);b.top=minOf(b.top,py);b.right=maxOf(b.right,px);b.bottom=maxOf(b.bottom,py) };return b
 }
 fun drawMark(canvas:Canvas,m:JSONObject,width:Int) {
  val color=m.getLong("color").toInt();val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color }
  if(m.getString("tool")=="text") {
   val b=bounds(m);paint.alpha=166;canvas.drawRoundRect(b,8f,8f,paint)
   val luminance=Color.red(color)*.2126+Color.green(color)*.7152+Color.blue(color)*.0722
   val text=layout(m.getString("text"),width);text.paint.color=if(luminance>140) Color.BLACK else Color.WHITE
   val saved=canvas.save();canvas.translate(b.left+10,b.top+10);text.paint.setShadowLayer(maxOf(1f,width/900f),0f,0f,if(luminance>140) Color.WHITE else Color.BLACK);text.draw(canvas);canvas.restoreToCount(saved)
  } else {
   paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.strokeWidth=m.getDouble("width").toFloat();if(m.getString("tool")=="highlight") paint.alpha=95
   val points=m.getJSONArray("points");val path=Path();val dx=m.optDouble("x").toFloat();val dy=m.optDouble("y").toFloat()
   for(i in 0 until points.length()) { val p=points.getJSONArray(i);val x=p.getDouble(0).toFloat()+dx;val y=p.getDouble(1).toFloat()+dy;if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
   if(points.length()==1) {val p=points.getJSONArray(0);canvas.drawPoint(p.getDouble(0).toFloat()+dx,p.getDouble(1).toFloat()+dy,paint)} else canvas.drawPath(path,paint)
  }
 }
 fun render(doc:JSONObject):ByteArray {
  val base=image(doc);val output=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
  try { val c=Canvas(output);c.drawColor(Color.WHITE);c.drawBitmap(base,0f,0f,null);c.clipRect(0,0,base.width,base.height);val marks=doc.getJSONArray("marks");for(i in 0 until marks.length()) drawMark(c,marks.getJSONObject(i),base.width);return jpeg(output,900*1024) } finally { base.recycle();output.recycle() }
 }
}
