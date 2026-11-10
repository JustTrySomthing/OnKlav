@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Html
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.*
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

internal enum class SwapKind(val title:String,val inputs:String,val outputs:String) {
    AUDIO("Audio","MP3 · WAV · AAC / M4A · FLAC · OGG","MP3 · WAV · M4A · AIFF"),
    VIDEO("Video","MP4 · MOV · MKV · WebM","MP4 · extract MP3 / WAV / M4A"),
    IMAGE("Image","JPG · PNG · WebP · GIF · HEIC / HEIF","JPG · PNG · WebP · PDF"),
    DOCUMENT("Document","DOCX · ODT · HTML · TXT · PDF","DOCX · PDF · HTML · TXT · RTF")
}
internal enum class SwapFormat(val label:String,val ext:String,val mime:String) {
    JPG("JPG","jpg","image/jpeg"), PNG("PNG","png","image/png"), WEBP("WebP","webp","image/webp"),
    PDF("PDF","pdf","application/pdf"), MP3("MP3","mp3","audio/mpeg"), WAV("WAV","wav","audio/wav"),
    M4A("M4A · AAC","m4a","audio/mp4"), AIFF("AIFF","aiff","audio/aiff"), MP4("MP4 · H.264","mp4","video/mp4"),
    DOCX("DOCX","docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    HTML("HTML","html","text/html"), TXT("TXT","txt","text/plain"), RTF("RTF","rtf","application/rtf")
}
internal data class SwapInput(val file:File,val name:String,val kind:SwapKind,val detail:String,val warning:String="",val document:WriteDocument?=null,val pages:Int=0,val hasAudio:Boolean=true) {
    val targets:List<SwapFormat> get()=when(kind){
        SwapKind.IMAGE->listOf(SwapFormat.JPG,SwapFormat.PNG,SwapFormat.WEBP,SwapFormat.PDF)
        SwapKind.AUDIO->listOf(SwapFormat.MP3,SwapFormat.WAV,SwapFormat.M4A,SwapFormat.AIFF)
        SwapKind.VIDEO->if(hasAudio)listOf(SwapFormat.MP4,SwapFormat.MP3,SwapFormat.WAV,SwapFormat.M4A)else listOf(SwapFormat.MP4)
        SwapKind.DOCUMENT->if(pages>0)listOf(SwapFormat.PNG,SwapFormat.JPG) else listOf(SwapFormat.DOCX,SwapFormat.PDF,SwapFormat.HTML,SwapFormat.TXT,SwapFormat.RTF)
    }
}
internal data class SwapOptions(val quality:Int=90,val maxEdge:Int=0,val kbps:Int=192,val page:Int=1)
internal object FormatSwapEngine {
    const val MAX_SOURCE=1_000_000_000L
    fun prepare(file:File,name:String,kind:SwapKind):SwapInput {
        val ext=name.substringAfterLast('.',"").lowercase()
        return when(kind){
            SwapKind.IMAGE->{var dims="";var animated=false
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)){d,i,_->
                    require(i.size.width.toLong()*i.size.height<=32_000_000){"Choose an image up to 32 megapixels."}
                    dims="${i.size.width} × ${i.size.height}";animated=i.isAnimated;d.setTargetSize(1,1)
                }
                SwapInput(file,name,kind,dims,if(animated)"Animated image: only the first frame will be converted." else "Image metadata, including location, is removed. HDR is saved as standard color.")
            }
            SwapKind.DOCUMENT->{require(file.length()<=16_000_000){"Choose a document under 16 MB."}
                if(ext=="pdf"){
                    val pages=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{it.pageCount}}
                    SwapInput(file,name,kind,"$pages pages","PDF pages can be saved as images. Editable Word conversion is not available.",pages=pages)
                }else{
                    val warnings=mutableSetOf<String>();val doc=when(ext){
                        "docx"->WriteExport.importDocx(file.readBytes()){warnings.addAll(it)}
                        "odt"->WriteDocument(title=name.substringBeforeLast('.'),text=odt(file)).also{warnings+="ODT formatting and embedded content"}
                        "txt","md","csv"->WriteDocument(title=name.substringBeforeLast('.'),text=decodeText(file.readBytes()))
                        "html","htm"->{val html=decodeText(file.readBytes());require(!html.contains("<!ENTITY",true));WriteDocument.capture(java.util.UUID.randomUUID().toString(),name.substringBeforeLast('.'),Html.fromHtml(html,Html.FROM_HTML_MODE_COMPACT)).also{warnings+="HTML layout, images and styles"}}
                        else->error("Choose DOCX, ODT, HTML or TXT. Legacy DOC and RTF input are not supported.")
                    };require(doc.text.length<=500000){"Choose a document under 500,000 characters."}
                    SwapInput(file,name,kind,"${doc.text.length} characters",if(warnings.isEmpty())"Document layout may change. Review the saved copy." else "Not preserved: ${warnings.sorted().joinToString()}. Review the saved copy.",doc)
                }
            }
            SwapKind.AUDIO,SwapKind.VIDEO->{
                val extractor=android.media.MediaExtractor()
                try {extractor.setDataSource(file.path);val formats=(0 until extractor.trackCount).map{extractor.getTrackFormat(it)}
                    val audio=formats.any{it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
                    val video=formats.any{it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/")==true}
                    require(if(kind==SwapKind.VIDEO)video else audio){"No ${kind.title.lowercase()} track found. Try another file."}
                    SwapInput(file,name,kind,if(kind==SwapKind.VIDEO)"Video${if(audio)" + audio" else " · no audio track"}" else "Audio track","Device codecs determine input support. Protected files cannot be converted. Metadata and subtitles are not copied.",hasAudio=audio)
                }finally{extractor.release()}
            }
        }
    }
    fun decodeText(bytes:ByteArray):String {
        val (charset,offset)=when{
            bytes.size>=2&&bytes[0]==0xff.toByte()&&bytes[1]==0xfe.toByte()->Charsets.UTF_16LE to 2
            bytes.size>=2&&bytes[0]==0xfe.toByte()&&bytes[1]==0xff.toByte()->Charsets.UTF_16BE to 2
            bytes.size>=3&&bytes[0]==0xef.toByte()&&bytes[1]==0xbb.toByte()&&bytes[2]==0xbf.toByte()->Charsets.UTF_8 to 3
            else->Charsets.UTF_8 to 0
        }
        return charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes,offset,bytes.size-offset)).toString().also{require(!it.contains('\u0000')){"This does not look like a text document."}}
    }
    private fun odt(file:File):String {
        var xml:String?=null;var total=0L;var count=0
        ZipInputStream(file.inputStream()).use{z->while(true){val entry=z.nextEntry?:break;require(++count<=512){"Too many entries in this ODT."};val out=ByteArrayOutputStream();val buf=ByteArray(16384)
            while(true){val n=z.read(buf);if(n<0)break;total+=n;require(total<=24_000_000){"This ODT expands beyond 24 MB."};if(entry.name=="content.xml")out.write(buf,0,n)}
            if(entry.name=="content.xml")xml=out.toString("UTF-8")
        }}
        val source=requireNotNull(xml){"ODT content is missing."};require(!source.contains("<!DOCTYPE",true)&&!source.contains("<!ENTITY",true)){"Unsupported document declarations."}
        val p=android.util.Xml.newPullParser();p.setInput(source.reader());val text=StringBuilder();var depth=0
        while(p.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){when(p.eventType){
            org.xmlpull.v1.XmlPullParser.START_TAG->when(p.name){"p","h"->depth++;"tab"->if(depth>0)text.append('\t');"line-break"->if(depth>0)text.append('\n');"s"->if(depth>0){val n=p.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:text:1.0","c")?.toIntOrNull()?:1;text.append(" ".repeat(n.coerceIn(1,1000)))}}
            org.xmlpull.v1.XmlPullParser.TEXT->if(depth>0)text.append(p.text)
            org.xmlpull.v1.XmlPullParser.END_TAG->if(p.name in setOf("p","h")){depth--;text.append('\n')}
        };require(text.length<=500000){"Document exceeds 500,000 characters."};p.next()};return text.toString().trimEnd('\n')
    }
    suspend fun convert(c:Context,input:SwapInput,target:SwapFormat,options:SwapOptions,out:File,progress:(Float)->Unit) {
        require(target in input.targets)
        when {
            input.kind==SwapKind.VIDEO&&target==SwapFormat.MP4->video(c,input.file,out,progress)
            input.kind in setOf(SwapKind.AUDIO,SwapKind.VIDEO)->withContext(Dispatchers.IO){audio(input,target,options,out,progress)}
            else->withContext(Dispatchers.IO){currentCoroutineContext().ensureActive()
                when(input.kind){
                    SwapKind.IMAGE->image(input,options,target,out)
                    SwapKind.DOCUMENT->if(input.pages>0)pdfImage(input,options,target,out)else document(requireNotNull(input.document),target,out)
                    else->error("Unsupported conversion")
                };currentCoroutineContext().ensureActive()
            }
        }
        require(out.length()>0){"The converter produced an empty file."}
    }
    private fun image(input:SwapInput,o:SwapOptions,target:SwapFormat,out:File) {
        val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(input.file)){d,i,_->
            require(i.size.width.toLong()*i.size.height<=32_000_000);d.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            d.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val edge=maxOf(i.size.width,i.size.height);if(o.maxEdge>0&&edge>o.maxEdge){val scale=o.maxEdge.toDouble()/edge;d.setTargetSize((i.size.width*scale).roundToInt().coerceAtLeast(1),(i.size.height*scale).roundToInt().coerceAtLeast(1))}
            val scaledEdge=if(o.maxEdge>0)minOf(edge,o.maxEdge)else edge;val scale=scaledEdge.toDouble()/edge
            require(i.size.width.toDouble()*i.size.height*scale*scale*8<Runtime.getRuntime().maxMemory()/2){"Image needs more memory. Choose 2560 px or 1280 px and try again."}
        }
        if(android.os.Build.VERSION.SDK_INT>=34)bitmap.setGainmap(null)
        try{if(target==SwapFormat.PDF){val pdf=android.graphics.pdf.PdfDocument();try{val w=595;val h=(bitmap.height.toDouble()/bitmap.width*w).roundToInt().coerceIn(72,14400);val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(w,h,1).create());page.canvas.drawColor(Color.WHITE);val fit=minOf(w.toFloat()/bitmap.width,h.toFloat()/bitmap.height);val bw=bitmap.width*fit;val bh=bitmap.height*fit;page.canvas.drawBitmap(bitmap,null,RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),Paint(Paint.FILTER_BITMAP_FLAG));pdf.finishPage(page);out.outputStream().use{pdf.writeTo(it)}}finally{pdf.close()}}else saveBitmap(bitmap,target,o.quality,out)}finally{bitmap.recycle()}
    }
    private fun saveBitmap(bitmap:Bitmap,target:SwapFormat,quality:Int,out:File){
        val flattened=if(target==SwapFormat.JPG&&bitmap.hasAlpha())Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also{val canvas=Canvas(it);canvas.drawColor(Color.WHITE);canvas.drawBitmap(bitmap,0f,0f,null)}else bitmap
        try{out.outputStream().use{require(flattened.compress(when(target){SwapFormat.JPG->Bitmap.CompressFormat.JPEG;SwapFormat.PNG->Bitmap.CompressFormat.PNG;else->Bitmap.CompressFormat.WEBP},quality,it)){"Image encoding failed."}}}finally{if(flattened!==bitmap)flattened.recycle()}
    }
    private fun pdfImage(input:SwapInput,o:SwapOptions,target:SwapFormat,out:File){
        require(o.page in 1..input.pages)
        ParcelFileDescriptor.open(input.file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{pdf->pdf.openPage(o.page-1).use{page->
            val scale=2000.0/maxOf(page.width,page.height);val bitmap=Bitmap.createBitmap((page.width*scale).roundToInt().coerceAtLeast(1),(page.height*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
            try{bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);saveBitmap(bitmap,target,o.quality,out)}finally{bitmap.recycle()}
        }}}
    }
    internal fun document(doc:WriteDocument,target:SwapFormat,out:File){out.outputStream().use{stream->when(target){
        SwapFormat.DOCX->WriteExport.docx(doc,stream)
        SwapFormat.PDF->WriteExport.pdf(doc,stream)
        SwapFormat.HTML->stream.write(doc.html().toByteArray())
        SwapFormat.TXT->stream.write(doc.text.toByteArray())
        SwapFormat.RTF->{val rtf=buildString{append("{\\rtf1\\ansi\\uc1 ");for(ch in doc.text){when(ch){'\\','{','}'->{append('\\');append(ch)};'\n'->append("\\par\n");'\r'->Unit;'\t'->append("\\tab ");else->if(ch.code in 32..126)append(ch)else append("\\u${ch.code.toShort()}?")}};append('}')};stream.write(rtf.toByteArray(Charsets.US_ASCII))}
        else->error("Unsupported document output")
    }}}
    private suspend fun audio(input:SwapInput,target:SwapFormat,o:SwapOptions,out:File,progress:(Float)->Unit){
        val work=File(out.parentFile,"audio-${java.util.UUID.randomUUID()}").apply{mkdirs()};val job=currentCoroutineContext();val check={job.ensureActive();require(work.usableSpace>32_000_000){"Not enough free storage."}}
        try{val source=AudioEditMedia.decode(input.file,File(work,"decoded.pcm"),input.name,check){progress(it*.5f)}
            val wav=File(work,"audio.wav");pcm(source,wav,false,check){progress(.5f+it*.3f)}
            when(target){SwapFormat.WAV->wav.copyTo(out,true);SwapFormat.AIFF->pcm(source,out,true,check){progress(.8f+it*.2f)};SwapFormat.MP3->app.onklav.shared.Mp3Codec.encode(wav,out,o.kbps,check){progress(.8f+it*.2f)};SwapFormat.M4A->AudioEditMedia.aac(wav,out,source.rate,source.channels,check){progress(.8f+it*.2f)};else->error("Unsupported audio output")}
        }finally{work.deleteRecursively()}
    }
    internal fun pcm(s:AudioSource,out:File,aiff:Boolean,check:()->Unit={},progress:(Float)->Unit={}) {
        val bytes=s.frames*s.channels*2;require(bytes+54<0xffffffffL){"Audio is too long for this format."}
        DataOutputStream(BufferedOutputStream(out.outputStream())).use{d->
            fun le32(n:Long)=d.writeInt(java.lang.Integer.reverseBytes(n.toInt()))
            fun le16(n:Int)=d.writeShort(java.lang.Short.reverseBytes(n.toShort()).toInt())
            if(aiff){d.writeBytes("FORM");d.writeInt((bytes+46).toInt());d.writeBytes("AIFFCOMM");d.writeInt(18);d.writeShort(s.channels);d.writeInt(s.frames.toInt());d.writeShort(16)
                // IEEE 80-bit extended sample rate (positive integer).
                val exponent=31-Integer.numberOfLeadingZeros(s.rate);d.writeShort(16383+exponent);d.writeLong(s.rate.toLong() shl (63-exponent));d.writeBytes("SSND");d.writeInt((bytes+8).toInt());d.writeLong(0)
            }else{d.writeBytes("RIFF");le32(bytes+36);d.writeBytes("WAVEfmt ");le32(16);le16(1);le16(s.channels);le32(s.rate.toLong());le32(s.rate.toLong()*s.channels*2);le16(s.channels*2);le16(16);d.writeBytes("data");le32(bytes)}
            AudioPcmReader(s.file,s.channels).use{r->for(f in 0 until s.frames){if(f%4096==0L){check();progress(f.toFloat()/s.frames)};repeat(s.channels){ch->val value=(r.get(f,ch).coerceIn(-1f,1f)*32768).roundToInt().coerceIn(-32768,32767);if(aiff)d.writeShort(value)else le16(value)}}}
        }
    }
    private suspend fun video(c:Context,source:File,out:File,progress:(Float)->Unit)=withContext(Dispatchers.Main.immediate){
        coroutineScope{val scope=this;suspendCancellableCoroutine<Unit>{cont->
            val transformer=Transformer.Builder(c).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(DefaultEncoderFactory.Builder(c).setEnableFallback(true).build())
                .addListener(object:Transformer.Listener{override fun onCompleted(composition:Composition,result:ExportResult){if(cont.isActive)cont.resumeWith(Result.success(Unit))};override fun onError(composition:Composition,result:ExportResult,e:ExportException){if(cont.isActive)cont.resumeWith(Result.failure(IllegalStateException("Video conversion failed (${e.errorCodeName}). Try another file.",e)))}}).build()
            cont.invokeOnCancellation{scope.launch(NonCancellable+Dispatchers.Main.immediate){transformer.cancel()}}
            try{transformer.start(MediaItem.fromUri(android.net.Uri.fromFile(source)),out.path)}catch(e:Exception){transformer.cancel();if(cont.isActive)cont.resumeWith(Result.failure(e))}
            scope.launch{val holder=ProgressHolder();while(cont.isActive){if(transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE)progress(holder.progress/100f);delay(250)}}
        }}
    }
}
