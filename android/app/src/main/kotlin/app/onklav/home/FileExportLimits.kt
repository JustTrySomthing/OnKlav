package app.onklav.home

import android.content.Context
import android.net.Uri
import java.io.FilterOutputStream
import java.io.OutputStream

/** Same limits as the native Files importer; connected providers own their limits. */
internal fun fileImportLimit(mime:String):Long =
 if(mime.startsWith("audio/")||mime.startsWith("video/"))2L*1024*1024*1024 else 256L*1024*1024

internal fun destinationExportLimit(c:Context,uri:Uri):Long =
 if(uri.authority==c.packageName+".file-selection")fileImportLimit(FileSelectionStore.read(c,uri).getString("mime"))else Long.MAX_VALUE

internal fun preflightFileExport(c:Context,uri:Uri,mime:String,size:Long){
 val limit=if(uri.authority==c.packageName+".file-selection")minOf(fileImportLimit(mime),destinationExportLimit(c,uri))else Long.MAX_VALUE
 require(size<0||size<=limit){"This file exceeds this folder’s ${limit/(1024*1024)} MiB limit. Choose Android Files → Downloads or Other instead."}
}

internal class LimitedExportStream(out:OutputStream,private val limit:Long):FilterOutputStream(out){
 private var bytes=0L
 private fun reserve(n:Int){require(n>=0&&bytes<=limit-n){"This file exceeds the selected folder’s size limit. Choose Android Files → Downloads or Other instead."};bytes+=n}
 override fun write(b:Int){reserve(1);out.write(b)}
 override fun write(b:ByteArray,off:Int,len:Int){require(off>=0&&len>=0&&off<=b.size-len);reserve(len);out.write(b,off,len)}
}
