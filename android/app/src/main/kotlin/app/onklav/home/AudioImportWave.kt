package app.onklav.home

import java.io.File
import kotlin.math.roundToInt

/** Shared bounded local decode used for portable soundtrack and MP3 downloads. */
internal object AudioImportWave {
 fun convert(input:File,output:File,name:String,check:()->Unit,progress:(Float)->Unit){
  val pcm=File(output.parentFile,"decode-${java.util.UUID.randomUUID()}.pcm")
  try{val source=AudioEditMedia.decode(input,pcm,name,check){progress(it*.8f)}
   output.outputStream().buffered().use{out->AudioEditEngine.writeWaveHeader(out,source.rate,source.channels,source.frames)
    AudioPcmReader(pcm,source.channels).use{reader->for(frame in 0 until source.frames){if(frame%4096==0L){check();check(output.parentFile!!.usableSpace>32_000_000){"Not enough free space"};progress(.8f+.2f*frame/source.frames)};repeat(source.channels){ch->val n=(reader.get(frame,ch)*32768).roundToInt().coerceIn(-32768,32767);out.write(n and 255);out.write((n shr 8)and 255)}}}
   }
  }catch(t:Throwable){output.delete();throw t}finally{pcm.delete()}
 }
}
