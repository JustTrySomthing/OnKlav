package app.onklav.home

import android.content.Context
import android.net.Uri
import app.onklav.shared.Mp3Codec
import java.io.File

/** Compatibility only: ordinary playback keeps Android's streaming decoder.
 * The bundled engine recovers local MP3s on outputs whose decoder rejects them. */
internal object Mp3Playback {
 fun fromUri(c:Context,uri:Uri,check:()->Unit):File {
  require(uri.scheme=="content");val original=File.createTempFile("mp3-input-",".tmp",c.cacheDir);val wave=File.createTempFile("mp3-compatible-",".wav",c.cacheDir)
  try{c.contentResolver.openInputStream(uri)?.use{input->original.outputStream().use{FilesBroker.copyBoundedLimit(input,it,2L*1024*1024*1024,check)}}?:error("MP3 could not be opened");Mp3Codec.decode(original,wave,check);return wave}
  catch(t:Throwable){wave.delete();throw t}finally{original.delete()}
 }
}
