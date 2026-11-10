package app.onklav.home

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Keep model values, slider bounds and exported fades in agreement after a shorter edit. */
internal fun audioBoundFades(edit:AudioEdit,rate:Int):AudioEdit {
    val maximum=(edit.frames.toDouble()/rate/edit.tempo).coerceIn(0.0,30.0).toFloat()
    return edit.copy(fadeIn=edit.fadeIn.coerceAtMost(maximum),fadeOut=edit.fadeOut.coerceAtMost(maximum))
}

/** A failed copy must not look like a successful close to OnKlav Files' commit listener. */
internal fun writeAudioExport(resolver:ContentResolver,uri:Uri,file:File,check:()->Unit,beforeClose:()->Unit){
    check()
    val descriptor=requireNotNull(resolver.openFileDescriptor(uri,"wt")){"This folder cannot write audio"}
    val output=ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
    try{
        file.inputStream().use{input->val bytes=ByteArray(65536)
            while(true){check();val n=input.read(bytes);if(n<0)break;output.write(bytes,0,n)}
        }
        output.flush();beforeClose();output.close()
    }catch(error:Exception){
        // Providers supporting reliable descriptors retain an interrupted/recoverable save.
        // Never delete an arbitrary selected URI, particularly before it was even opened.
        runCatching{descriptor.closeWithError("Audio export interrupted")}
        throw error
    }finally{runCatching{output.close()}}
}
