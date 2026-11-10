package app.onklav.shared

import de.sciss.jump3r.mp3.*
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** App-wide, offline software MPEG Layer III. No JavaSound/NDK/external player.
 * File transforms stream bounded buffers; callers supply cancellation/live authorization. */
object Mp3Codec {
 data class Info(val rate:Int,val channels:Int,val frames:Long)
 fun mime(reported:String,name:String):String = if(name.substringAfterLast('.').equals("mp3",true)||reported.lowercase() in setOf("audio/mp3","audio/x-mp3","audio/mpeg3","audio/x-mpeg","audio/x-mpeg-3"))"audio/mpeg"else reported
 private fun start(r:RandomAccessFile):Long{
  if(r.length()<4)return 0
  if(r.length()>=10){val b=ByteArray(10);r.seek(0);r.readFully(b)
   if(String(b,0,3,Charsets.US_ASCII)=="ID3"){require((6..9).all{b[it].toInt()and 128==0}){"Invalid MP3 tag"};var size=0L;for(i in 6..9)size=(size shl 7)+(b[i].toInt()and 127);require(size<=16*1024*1024&&10+size<=r.length()){"MP3 tag is incomplete or too large"};return 10+size+if(b[5].toInt()and 16!=0)10 else 0}
  };return 0
 }
 fun isMp3(file:File):Boolean=runCatching{RandomAccessFile(file,"r").use{r->val offset=start(r);if(offset+4>r.length())false else{r.seek(offset);val h=r.readInt();h ushr 21==0x7ff&&(h ushr 19 and 3)!=1&&(h ushr 17 and 3)==1&&(h ushr 12 and 15)!=15&&(h ushr 10 and 3)!=3}}}.getOrDefault(false)
 /** The upstream Xing size calculation uses the MPEG-2 rate for MPEG-2.5.
  * Consume only that metadata frame here, retaining its standard gapless fields. */
 private fun audioStart(r:RandomAccessFile,enc:Enc):Long {
  val offset=start(r);r.seek(offset);val h=r.readInt();if((h ushr 19 and 3)!=0)return offset
  val bitrate=intArrayOf(0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0)[h ushr 12 and 15]
  if(bitrate==0)return offset
  val rate=intArrayOf(11025,12000,8000)[h ushr 10 and 3];val length=72000*bitrate/rate+(h ushr 9 and 1)
  val side=if((h ushr 6 and 3)==3)9 else 17;val tag=offset+4+side
  if(tag+8>offset+length||offset+length>r.length())return offset
  r.seek(tag);val magic=ByteArray(4);r.readFully(magic);if(String(magic,Charsets.US_ASCII) !in setOf("Xing","Info"))return offset
  val flags=r.readInt();var position=tag+8
  if(flags and 1!=0)position+=4;if(flags and 2!=0)position+=4;if(flags and 4!=0)position+=100;if(flags and 8!=0)position+=4
  if(position+24<=offset+length){r.seek(position);val encoder=ByteArray(9);r.readFully(encoder);val name=String(encoder,Charsets.US_ASCII)
   if(name.startsWith("LAME")||name.startsWith("Lavc")||name.startsWith("Lavf")){r.seek(position+21);val a=r.readUnsignedByte();val b=r.readUnsignedByte();val c=r.readUnsignedByte();val delay=(a shl 4)+(b ushr 4);val padding=((b and 15)shl 8)+c;if(delay<=3000)enc.enc_delay=delay;if(padding<=3000)enc.enc_padding=padding}
  };return offset+length
 }
 private class Engine {
  val lame=Lame();val gain=GainAnalysis();val bits=BitStream();val presets=Presets();val qp=QuantizePVT();val quant=Quantize();val vbr=VBRTag();val version=Version();val id3=ID3Tag();val reservoir=Reservoir();val take=Takehiro()
  val mpg=MPGLib();val inter=Interface();val common=Common()
  init{lame.setModules(gain,bits,presets,qp,quant,vbr,version,id3,mpg);bits.setModules(gain,mpg,version,vbr);id3.setModules(bits,version);presets.setModules(lame);quant.setModules(bits,reservoir,qp,take);qp.setModules(take,reservoir,lame.enc.psy);reservoir.setModules(bits);take.setModules(qp);vbr.setModules(lame,bits,version);mpg.setModules(inter,common);inter.setModules(vbr,common)}
 }
 private fun header(out:RandomAccessFile,rate:Int,channels:Int,frames:Long){
  val bytes=frames*channels*2;require(bytes<=0xfffffff0L){"PCM compatibility file exceeds WAV limits"}
  val b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".toByteArray());b.putInt((36+bytes).toInt());b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(channels.toShort());b.putInt(rate);b.putInt(rate*channels*2);b.putShort((channels*2).toShort());b.putShort(16);b.put("data".toByteArray());b.putInt(bytes.toInt());out.seek(0);out.write(b.array())
 }
 fun decode(input:File,wave:File,check:()->Unit={},progress:(Float)->Unit={}):Info {
  require(input.canonicalFile!=wave.canonicalFile);require(isMp3(input)){"Choose a valid, unprotected MP3 file"};check()
  val e=Engine();val hip=e.mpg.hip_decode_init();val info=MP3Data();val enc=Enc();var rate=0;var channels=0;var frames=0L;var skip=-1;var last=0L
  val left=ShortArray(1152);val right=ShortArray(1152);val data=ByteArray(4096);val pcm=ByteBuffer.allocate(4608).order(ByteOrder.LITTLE_ENDIAN)
  try{RandomAccessFile(input,"r").use{r->
   var end=r.length();if(end>=128){r.seek(end-128);val tag=ByteArray(3);r.readFully(tag);if(String(tag,Charsets.US_ASCII)=="TAG")end-=128}
   r.seek(audioStart(r,enc));RandomAccessFile(wave,"rw").use{out->out.setLength(44);out.seek(44)
    while(true){check();val available=minOf(data.size.toLong(),end-r.filePointer).coerceAtLeast(0).toInt();val n=if(available>0)r.read(data,0,available)else -1;var amount=if(n>0)n else 0
     var drained=0;var idle=0
     while(true){check();val count=e.mpg.hip_decode1_headersB(hip,data,amount,left,right,info,enc);amount=0;require(count>=0){"This MP3 contains an invalid audio frame"};if(count==0){if(++idle<=3)continue else break};idle=0;require(++drained<=128){"MP3 decoder did not advance"}
      if(rate==0){rate=info.samplerate;channels=info.stereo;require(rate in 8000..48000&&channels in 1..2){"Unsupported MP3 format: $rate Hz, $channels channels"};skip=529+maxOf(0,enc.enc_delay)}else require(rate==info.samplerate&&channels==info.stereo){"Changing MP3 sample formats cannot be converted"}
      pcm.clear();for(i in 0 until count){if(skip>0){skip--;continue};pcm.putShort(left[i]);if(channels==2)pcm.putShort(right[i]);frames++}
      require(frames<=rate*7200L){"Choose an MP3 up to two hours for software conversion"};require(wave.absoluteFile.parentFile!!.usableSpace>32_000_000){"Not enough storage for MP3 compatibility audio"};out.write(pcm.array(),0,pcm.position())
     }
     val now=System.nanoTime();if(now-last>100_000_000){progress((r.filePointer.toFloat()/r.length()).coerceIn(0f,1f));last=now};if(n<0)break
    }
    val tail=maxOf(0,enc.enc_padding-529).toLong();frames=(frames-tail).coerceAtLeast(0);require(frames>0&&rate>0){"MP3 contains no decodable audio"};out.setLength(44+frames*channels*2);header(out,rate,channels,frames);out.fd.sync()
   }
  };check();progress(1f);return Info(rate,channels,frames)
  }catch(t:Throwable){wave.delete();throw t}finally{e.mpg.hip_decode_exit(hip)}
 }
 private data class Wave(val rate:Int,val channels:Int,val offset:Long,val bytes:Long)
 private fun readWave(r:RandomAccessFile):Wave{
  require(r.length()>=44);val h=ByteArray(12);r.readFully(h);require(String(h,0,4)=="RIFF"&&String(h,8,4)=="WAVE"){"MP3 export needs a PCM WAV source"}
  var rate=0;var channels=0;var offset=0L;var bytes=0L
  while(r.filePointer+8<=r.length()){
   val tag=ByteArray(4);r.readFully(tag);val size=Integer.reverseBytes(r.readInt()).toLong()and 0xffffffffL;val pos=r.filePointer;require(pos+size<=r.length())
   if(String(tag)=="fmt "){require(size>=16);val format=java.lang.Short.reverseBytes(r.readShort()).toInt()and 65535;channels=java.lang.Short.reverseBytes(r.readShort()).toInt()and 65535;rate=Integer.reverseBytes(r.readInt());r.skipBytes(6);val bits=java.lang.Short.reverseBytes(r.readShort()).toInt()and 65535;require(format==1&&bits==16){"MP3 export needs 16-bit PCM WAV"}}
   if(String(tag)=="data"){offset=pos;bytes=size};r.seek((pos+size+(size and 1)).coerceAtMost(r.length()))
  };require(rate in 8000..192000&&channels in 1..2&&offset>0&&bytes>0&&bytes%(channels*2)==0L);return Wave(rate,channels,offset,bytes)
 }
 fun encode(wave:File,output:File,kbps:Int=192,check:()->Unit={},progress:(Float)->Unit={}) {
  require(wave.canonicalFile!=output.canonicalFile);require(kbps in setOf(64,96,128,160,192,224,256,320));check();val e=Engine();val g=e.lame.lame_init()
  try{RandomAccessFile(wave,"r").use{r->val source=readWave(r);val total=source.bytes/(source.channels*2);require(total<=source.rate*7200L)
   g.num_channels=source.channels;g.in_samplerate=source.rate;g.out_samplerate=when{source.rate>=48000->48000;source.rate>=44100->44100;source.rate>=32000->32000;source.rate>=24000->24000;source.rate>=22050->22050;source.rate>=16000->16000;source.rate>=12000->12000;source.rate>=11025->11025;else->8000};g.num_samples=total.toInt();g.mode=if(source.channels==1)MPEGMode.MONO else MPEGMode.JOINT_STEREO;g.brate=when{g.out_samplerate<16000->minOf(kbps,48);g.out_samplerate<32000->minOf(kbps,96);else->kbps};g.quality=3;g.write_id3tag_automatic=false;g.bWriteVbrTag=true;e.id3.id3tag_init(g);require(e.lame.lame_init_params(g)==0){"MP3 encoder could not start"}
   val left=IntArray(1152);val right=IntArray(1152);val pcmBytes=ByteArray(1152*source.channels*2);val pcm=ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);val encoded=ByteArray(16384);var sent=0L;var last=0L;r.seek(source.offset)
   RandomAccessFile(output,"rw").use{out->out.setLength(0)
    while(sent<total){check();val count=minOf(1152L,total-sent).toInt();r.readFully(pcmBytes,0,count*source.channels*2);pcm.position(0);repeat(count){i->left[i]=pcm.short.toInt()shl 16;right[i]=if(source.channels==2)pcm.short.toInt()shl 16 else left[i]};val bytes=e.lame.lame_encode_buffer_int(g,left,right,count,encoded,0,encoded.size);require(bytes>=0){"MP3 encoding failed ($bytes)"};out.write(encoded,0,bytes);sent+=count;require(output.absoluteFile.parentFile!!.usableSpace>8_000_000){"Not enough storage to encode MP3"};val now=System.nanoTime();if(now-last>100_000_000){progress(sent.toFloat()/total);last=now}}
    check();val count=e.lame.lame_encode_flush(g,encoded,0,encoded.size);require(count>=0);out.write(encoded,0,count);val tag=e.vbr.getLameTagFrame(g,encoded);if(tag>0){require(tag<=encoded.size);out.seek(0);out.write(encoded,0,tag)};out.fd.sync()
   }
  };check();progress(1f)
  }catch(t:Throwable){output.delete();throw t}finally{e.lame.lame_close(g)}
 }
}
