package app.onklav.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Bounded Nero/QuickTime chapter parsing. Never load an audiobook into memory. */
internal object SdkAudioBookChapters {
 data class Atom(val type:String,val start:Long,val data:Long,val end:Long)
 private fun atoms(f:RandomAccessFile,start:Long,end:Long):List<Atom>{val out=mutableListOf<Atom>();var at=start;while(at+8<=end&&out.size<20000){f.seek(at);var n=f.readInt().toLong()and 0xffffffffL;val type=ByteArray(4).also{f.readFully(it)}.toString(Charsets.ISO_8859_1);var header=8;if(n==1L){if(at+16>end)break;n=f.readLong();header=16};if(n==0L)n=end-at;if(n<header||n>end-at)break;out+=Atom(type,at,at+header,at+n);at+=n};return out}
 private fun children(f:RandomAccessFile,a:Atom)=atoms(f,a.data,a.end)
 fun read(file:File):JSONArray=runCatching{RandomAccessFile(file,"r").use{f->
  val moov=atoms(f,0,f.length()).firstOrNull{it.type=="moov"}?:return@use JSONArray();if(moov.end-moov.data>32L*1024*1024)return@use JSONArray()
  val top=children(f,moov);val nero=top.firstOrNull{it.type=="udta"}?.let{children(f,it).firstOrNull{a->a.type=="chpl"}}
  if(nero!=null){f.seek(nero.data);val version=f.readUnsignedByte();f.skipBytes(3);if(version==1)f.skipBytes(4);val count=f.readUnsignedByte();val result=JSONArray();var prior=-1L;repeat(count){require(f.filePointer+9<=nero.end);val ms=f.readLong()/10000;val length=f.readUnsignedByte();require(ms>=0&&ms>prior&&f.filePointer+length<=nero.end);prior=ms;val title=ByteArray(length).also{f.readFully(it)}.toString(Charsets.UTF_8);result.put(JSONObject().put("position_ms",ms).put("title",SdkAudioBooks.clean(title,200).ifEmpty{"Chapter ${it+1}"}))};return@use result}
  val tracks=top.filter{it.type=="trak"};val chapterIds=mutableSetOf<Long>();for(t in tracks){children(f,t).firstOrNull{it.type=="tref"}?.let{ref->children(f,ref).filter{it.type=="chap"}.forEach{a->f.seek(a.data);while(f.filePointer+4<=a.end)chapterIds+=f.readInt().toLong()and 0xffffffffL}}}
  for(t in tracks){val tk=children(f,t);val header=tk.firstOrNull{it.type=="tkhd"}?:continue;f.seek(header.data);val version=f.readUnsignedByte();f.seek(header.data+if(version==1)20 else 12);val tid=f.readInt().toLong()and 0xffffffffL;if(tid !in chapterIds)continue
   val mdia=tk.firstOrNull{it.type=="mdia"}?:continue;val md=children(f,mdia);val mh=md.firstOrNull{it.type=="mdhd"}?:continue;f.seek(mh.data);val mv=f.readUnsignedByte();f.seek(mh.data+if(mv==1)20 else 12);val scale=f.readInt().toLong()and 0xffffffffL;require(scale>0)
   val minf=md.firstOrNull{it.type=="minf"}?:continue;val stbl=children(f,minf).firstOrNull{it.type=="stbl"}?:continue;val st=children(f,stbl)
   fun table(name:String):Atom=st.first{it.type==name}.also{f.seek(it.data+4)}
   val sz=table("stsz");val uniform=f.readInt().toLong()and 0xffffffffL;val count=f.readInt();require(count in 1..2000);require(uniform>0||f.filePointer+count*4L<=sz.end);val sizes=LongArray(count){if(uniform>0)uniform else f.readInt().toLong()and 0xffffffffL};require(sizes.all{it in 2..65536})
   val ts=table("stts");val tn=f.readInt();require(tn in 1..2000&&f.filePointer+tn*8L<=ts.end);val times=LongArray(count);var sample=0;var time=0L;repeat(tn){val repeat=f.readInt();val delta=f.readInt().toLong()and 0xffffffffL;require(repeat in 0..count&&sample+repeat<=count);repeat(repeat){times[sample++]=time*1000/scale;time+=delta}};require(sample==count)
   val sc=table("stsc");val sn=f.readInt();require(sn in 1..2000&&f.filePointer+sn*12L<=sc.end);val chunks=List(sn){Triple(f.readInt(),f.readInt(),f.readInt())};require(chunks.first().first==1&&chunks.all{it.first>0&&it.second in 1..2000})
   val co=st.firstOrNull{it.type=="co64"}?:st.first{it.type=="stco"};f.seek(co.data+4);val cn=f.readInt();require(cn in 1..2000&&f.filePointer+cn*(if(co.type=="co64")8L else 4L)<=co.end);val offsets=LongArray(cn){if(co.type=="co64")f.readLong()else f.readInt().toLong()and 0xffffffffL};val result=JSONArray();sample=0
   for(i in offsets.indices){val chunk=chunks.last{it.first<=i+1};var at=offsets[i];repeat(chunk.second){if(sample<count){require(at>=0&&at+sizes[sample]<=f.length());f.seek(at);val length=f.readUnsignedShort();require(length<=sizes[sample]-2);val bytes=ByteArray(length).also{f.readFully(it)};val title=if(bytes.size>=2&&bytes[0]==0xfe.toByte()&&bytes[1]==0xff.toByte())bytes.toString(Charsets.UTF_16)else bytes.toString(Charsets.UTF_8);result.put(JSONObject().put("position_ms",times[sample]).put("title",SdkAudioBooks.clean(title,200).ifEmpty{"Chapter ${sample+1}"}));at+=sizes[sample++]} }};require(sample==count);return@use result
  };JSONArray()
 }}.getOrDefault(JSONArray())
 fun rows(b:JSONObject):List<JSONObject>{val out=mutableListOf<JSONObject>();val tracks=b.getJSONArray("tracks");for(i in 0 until tracks.length()){val t=tracks.getJSONObject(i);val marks=t.optJSONArray("markers")?:JSONArray();if(marks.length()==0||marks.getJSONObject(0).optLong("position_ms")>0)out+=JSONObject().put("index",i).put("position_ms",0).put("title",t.getString("title"));for(j in 0 until marks.length())out+=JSONObject(marks.getJSONObject(j).toString()).put("index",i)};return out}
 fun nextBoundary(c:Context,o:JSONObject,item:JSONObject,pos:Long):Long=rows(SdkAudioBooks.get(c,o,item.getString("audiobook_book"))).firstOrNull{it.getInt("index")==item.getInt("audiobook_index")&&it.getLong("position_ms")>pos+1000}?.getLong("position_ms")?:0
 fun jump(c:Context,o:JSONObject,item:JSONObject,pos:Long,next:Boolean):Pair<Int,Long>{val rows=rows(SdkAudioBooks.get(c,o,item.getString("audiobook_book")));val index=item.getInt("audiobook_index");val selected=if(next)rows.firstOrNull{it.getInt("index")>index||(it.getInt("index")==index&&it.getLong("position_ms")>pos+1000)}?:JSONObject().put("index",SdkAudioBooks.get(c,o,item.getString("audiobook_book")).getJSONArray("tracks").length()).put("position_ms",0)else rows.lastOrNull{it.getInt("index")<index||(it.getInt("index")==index&&it.getLong("position_ms")<pos-3000)}?:rows.first();return selected.getInt("index") to selected.getLong("position_ms")}
}
