package app.onklav.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.text.style.ImageSpan
import android.util.Base64
import java.io.ByteArrayOutputStream

internal class WriteImageSpan(val encoded:String):ImageSpan(drawable(encoded),ALIGN_BASELINE){
 companion object{
  fun valid(value:String):Boolean=runCatching{bytes(value);true}.getOrDefault(false)
  private fun bytes(value:String):ByteArray{
   require(value.length<=2_800_000)
   val bytes=Base64.decode(value,Base64.NO_WRAP)
   require(bytes.size<=2_000_000&&bytes.take(8)==listOf(137,80,78,71,13,10,26,10).map{it.toByte()})
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   require(bounds.outWidth in 1..1200&&bounds.outHeight in 1..1200)
   return bytes
  }
  private fun drawable(value:String):BitmapDrawable{
   val bytes=bytes(value);val bitmap=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
   val scale=minOf(1f,360f/bitmap.width,300f/bitmap.height)
   @Suppress("DEPRECATION") val result=BitmapDrawable(bitmap)
   result.setBounds(0,0,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1));return result
  }
  fun read(c:Context,uri:android.net.Uri):String{
   val input=c.contentResolver.openInputStream(uri).use{stream->requireNotNull(stream);val out=ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=16_000_000){"Choose an image smaller than 16 MB"};out.write(buffer,0,n)};out.toByteArray()}
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(input,0,input.size,bounds)
   require(bounds.outWidth>0&&bounds.outHeight>0){"This image could not be opened"}
   var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1200)sample*=2
   var bitmap=requireNotNull(BitmapFactory.decodeByteArray(input,0,input.size,BitmapFactory.Options().apply{inSampleSize=sample}))
   try{
    if(maxOf(bitmap.width,bitmap.height)>1200){val scale=1200f/maxOf(bitmap.width,bitmap.height);val smaller=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true);bitmap.recycle();bitmap=smaller}
    val orientation=runCatching{android.media.ExifInterface(input.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
    val matrix=android.graphics.Matrix();when(orientation){2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(270f)}
    if(!matrix.isIdentity){val oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);if(oriented!==bitmap){bitmap.recycle();bitmap=oriented}}
    while(true){val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out)
     if(out.size()<=2_000_000)return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
     require(bitmap.width>64&&bitmap.height>64){"This image is too complex to embed"}
     val smaller=Bitmap.createScaledBitmap(bitmap,(bitmap.width*.75f).toInt(),(bitmap.height*.75f).toInt(),true);bitmap.recycle();bitmap=smaller
    }
   }finally{bitmap.recycle()}
  }
 }
}
