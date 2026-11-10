package app.onklav.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.onklav.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.zip.CRC32

/** Signed, bounded icon data only. Stock artwork and desktop layout remain host-owned. */
internal object SdkDesktopIcons {
    private val cache=object:LruCache<String,Bitmap>(1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private val decoders=Semaphore(2)
    fun clear(){cache.evictAll()} // Displayed bitmaps remain valid until Compose releases them.
    fun symbol(name:String):Int=when(name){
        "calc"->R.drawable.ref_app_calc;"weather"->R.drawable.ref_app_weather;"convert"->R.drawable.ref_app_convert;"ebooks"->R.drawable.ref_app_ebooks;"audiobooks"->R.drawable.ref_app_audiobooks;"podcasts"->R.drawable.ref_app_podcasts;"radio"->R.drawable.ref_app_radio;"apps"->R.drawable.ref_nav_apps;"notes"->R.drawable.ref_app_notes;"files"->R.drawable.ref_app_files;"walkie"->R.drawable.ref_app_walkie
        "social"->R.drawable.ref_app_social;"browser"->R.drawable.ref_app_browser;"youtube"->R.drawable.ref_app_youtube
        "reader"->R.drawable.ref_app_reader;"annotation"->R.drawable.ref_app_annotation;"rss"->R.drawable.ref_app_rss
        else->sdkIconResource(name)
    }
    fun app(manifest:JSONObject):PlatformApp{
        val icon=manifest.optJSONObject("icon")
        return PlatformApp(AppSdk.desktopId(manifest.getString("id")),includedAppDisplayName(manifest),if(manifest.optString("id")=="app.onklav.clock")R.drawable.ref_app_clock else if(manifest.optString("id")=="app.onklav.flashlight")R.drawable.ref_app_flashlight else if(manifest.optString("id")=="app.onklav.camera")R.drawable.ref_app_camera else if(manifest.optString("id")=="app.onklav.email")R.drawable.ref_app_email else if(manifest.optString("id")=="app.onklav.videoeditor")R.drawable.ref_app_video_editor else if(manifest.optString("id")=="app.onklav.maps")R.drawable.ref_app_maps else if(manifest.optString("id")=="app.onklav.audioeditor")R.drawable.ref_app_audio_editor else if(manifest.optString("id")=="app.onklav.write")R.drawable.ref_app_write else if(manifest.optString("id")=="app.onklav.photo")R.drawable.ref_app_photo else if(icon?.has("symbol")==true)symbol(icon.getString("symbol"))else R.drawable.ref_nav_apps,icon?.optString("png_hex")?.takeIf{it.isNotEmpty()})
    }
    private fun u32(b:ByteArray,at:Int):Long=(0..3).fold(0L){v,i->(v shl 8)or(b[at+i].toLong()and 255)}
    suspend fun bitmap(data:String):Bitmap?=withContext(Dispatchers.IO){decoders.withPermit{
        if(data.length !in 66..12288||data.length%2!=0||data.any{it !in '0'..'9'&&it !in 'a'..'f'})return@withPermit null
        val key=MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.US_ASCII)).joinToString(""){"%02x".format(it)}
        cache.get(key)?.let{return@withPermit it}
        try{
            val bytes=ByteArray(data.length/2){data.substring(it*2,it*2+2).toInt(16).toByte()}
            if(!bytes.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))||u32(bytes,8)!=13L||String(bytes,12,4,Charsets.US_ASCII)!="IHDR"||u32(bytes,16) !in 1L..128L||u32(bytes,20) !in 1L..128L||bytes[24].toInt()!=8||bytes[25].toInt() !in setOf(2,6)||bytes.sliceArray(26..28).any{it.toInt()!=0})return@withPermit null
            // No compressed profiles/text, animation or unknown ancillary decoders.
            var offset=8;var idat=false;var ended=false;var chunks=0
            while(offset+12<=bytes.size){
                if(++chunks>64)return@withPermit null
                val size=u32(bytes,offset);if(size>bytes.size-offset-12)return@withPermit null
                val n=size.toInt();val kind=String(bytes,offset+4,4,Charsets.US_ASCII)
                when(kind){"IHDR"->if(offset!=8||n!=13)return@withPermit null;"IDAT"->idat=true;"IEND"->{if(n!=0||offset+12!=bytes.size)return@withPermit null;ended=true};"sRGB"->if(n!=1||idat)return@withPermit null;"gAMA"->if(n!=4||idat)return@withPermit null;"cHRM"->if(n!=32||idat)return@withPermit null;"pHYs"->if(n!=9||idat)return@withPermit null;else->return@withPermit null}
                val crc=CRC32();crc.update(bytes,offset+4,n+4);if(crc.value!=u32(bytes,offset+8+n))return@withPermit null
                offset+=n+12
            }
            if(!ended||!idat||offset!=bytes.size)return@withPermit null
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth !in 1..128||bounds.outHeight !in 1..128||bounds.outMimeType!="image/png")return@withPermit null
            BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.also{cache.put(key,it)}
        }catch(e:IllegalArgumentException){null}catch(e:OutOfMemoryError){null}
    }}
}
