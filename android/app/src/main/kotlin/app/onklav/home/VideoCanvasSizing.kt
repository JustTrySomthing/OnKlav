package app.onklav.home

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

/** Match the first imported source; later clips never change an established canvas. */
internal fun videoSourceCanvasRatio(width:Int,height:Int,rotation:Int=0,fallback:String="16:9"):String {
    if(width<=0||height<=0)return fallback
    val swapped=((rotation%180)+180)%180==90
    val aspect=if(swapped)height.toDouble()/width else width.toDouble()/height
    return VideoProject.ratios.minBy{ratio->val parts=ratio.split(':');abs(ln(aspect/(parts[0].toDouble()/parts[1].toDouble())))}
}

internal fun videoInitialCanvasRatio(file:File,image:Boolean,fallback:String):String=runCatching{
    if(image){
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.path,bounds)
        val rotation=runCatching{val exif=android.media.ExifInterface(file.path)
            when(exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)){5,6,7,8->90;else->0}
        }.getOrDefault(0)
        videoSourceCanvasRatio(bounds.outWidth,bounds.outHeight,rotation,fallback)
    }else{
        val meta=MediaMetadataRetriever()
        try{
            meta.setDataSource(file.path)
            videoSourceCanvasRatio(
                meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?:0,
                meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0,
                meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?:0,fallback)
        }finally{meta.release()}
    }
}.getOrDefault(fallback)

/** The source thumbnail must use the same display orientation as its canvas. */
internal fun videoPhotoThumbnail(file:File,maxEdge:Int=240):Bitmap? {
    require(maxEdge>0)
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    BitmapFactory.decodeFile(file.path,bounds)
    if(bounds.outWidth<=0||bounds.outHeight<=0)return null
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge)sample*=2
    val source=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?:return null
    val orientation=runCatching{android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
    val matrix=Matrix().apply{when(orientation){
        2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
        5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
        7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
    }}
    if(matrix.isIdentity)return source
    return try{Bitmap.createBitmap(source,0,0,source.width,source.height,matrix,true).also{if(it!==source)source.recycle()}}
        catch(error:Throwable){source.recycle();throw error}
}
