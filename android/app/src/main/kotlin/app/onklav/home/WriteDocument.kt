package app.onklav.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.*
import android.text.style.*
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.*

internal data class WriteMark(val start:Int,val end:Int,val kind:String,val value:String)
internal data class WriteParagraph(val start:Int,val end:Int,val text:String,val marks:List<WriteMark>)
internal fun writeParagraphs(doc:WriteDocument):Sequence<WriteParagraph> = sequence {
    val ordered=doc.marks.sortedBy{it.start};var next=0;var start=0;val active=mutableListOf<WriteMark>()
    while(start<=doc.text.length){val end=doc.text.indexOf('\n',start).let{if(it<0)doc.text.length else it}
        while(next<ordered.size&&ordered[next].start<=end){active.add(ordered[next]);next++}
        active.removeAll{it.end<start};yield(WriteParagraph(start,end,doc.text.substring(start,end),active.toList()))
        if(end==doc.text.length)break;start=end+1
    }
}
internal data class WriteDocument(val id:String=UUID.randomUUID().toString(),val title:String="Untitled",val text:String="",val marks:List<WriteMark> = emptyList(),val modified:Long=System.currentTimeMillis()) {
    fun json()=JSONObject().put("schema","onklav.write.v1").put("id",id).put("title",title).put("text",text).put("modified",modified).put("marks",JSONArray(marks.map{JSONObject().put("start",it.start).put("end",it.end).put("kind",it.kind).put("value",it.value)}))
    companion object {
        private fun validMark(m:WriteMark):Boolean=runCatching{when(m.kind){"rule"->m.value=="1";"image"->WriteImageSpan.valid(m.value);"style"->m.value.toInt() in 0..3;"color","highlight"->{m.value.toInt();true};"underline","strike"->m.value=="1";"size"->m.value.toInt() in 8..96;"relative"->m.value.toFloat() in .5f..4f;"font"->m.value.length<=80;"link"->{safeLink(m.value);true};"align"->m.value in setOf("ALIGN_NORMAL","ALIGN_CENTER","ALIGN_OPPOSITE");"indent"->m.value.toInt() in 0..240;"list"->m.value.matches(Regex("(bullet|number):[0-9]{1,7}:[0-8]"));else->false}}.getOrDefault(false)
        fun parse(j:JSONObject):WriteDocument {require(j.getString("schema")=="onklav.write.v1"){"This is not a Write document"};val text=j.getString("text");require(text.length<=500000){"Document exceeds 500,000 characters"};val a=j.getJSONArray("marks");require(a.length()<=50000){"Too much formatting in this document"};val marks=List(a.length()){val m=a.getJSONObject(it);WriteMark(m.getInt("start"),m.getInt("end"),m.getString("kind"),m.getString("value"))};require(marks.count{it.kind=="image"}<=12);require(marks.filter{it.kind in setOf("image","rule")}.all{it.end==it.start+1&&it.start>=0&&it.end<=text.length&&text[it.start]=='\uFFFC'});require(marks.all{it.start>=0&&it.end>=it.start&&it.end<=text.length&&validMark(it)}){"Unsupported or damaged formatting"};require(j.getString("id").matches(Regex("[a-f0-9-]{36}"))){"Invalid document identity"};return WriteDocument(j.getString("id"),j.optString("title","Untitled").take(120),text,marks,j.optLong("modified"))}
        fun capture(id:String,title:String,s:Spanned):WriteDocument {
            val marks=s.getSpans(0,s.length,Any::class.java).mapNotNull{span->val a=s.getSpanStart(span);val b=s.getSpanEnd(span);val pair=when(span){
                is WriteRuleSpan->"rule" to "1";is WriteImageSpan->"image" to span.encoded;is StyleSpan->"style" to span.style.toString();is UnderlineSpan->"underline" to "1";is StrikethroughSpan->"strike" to "1";is ForegroundColorSpan->"color" to span.foregroundColor.toString();is BackgroundColorSpan->"highlight" to span.backgroundColor.toString();is AbsoluteSizeSpan->"size" to span.size.toString();is RelativeSizeSpan->"relative" to span.sizeChange.toString();is TypefaceSpan->"font" to (span.family?:"sans-serif");is URLSpan->"link" to span.url;is AlignmentSpan->"align" to span.alignment.name;is WriteListSpan->"list" to "${span.kind}:${span.number}:${span.indent}";is LeadingMarginSpan.Standard->"indent" to span.getLeadingMargin(true).toString();is BulletSpan->"list" to "bullet:1:0";else->null
            };pair?.let{WriteMark(a,b,it.first,it.second)}}
            return WriteDocument(id,title,s.toString(),marks)
        }
    }
    fun spanned():SpannableStringBuilder=SpannableStringBuilder(text).also{b->marks.forEach{m->runCatching{
        val span:Any=when(m.kind){"rule"->WriteRuleSpan();"image"->WriteImageSpan(m.value);"style"->StyleSpan(m.value.toInt());"underline"->UnderlineSpan();"strike"->StrikethroughSpan();"color"->ForegroundColorSpan(m.value.toInt());"highlight"->BackgroundColorSpan(m.value.toInt());"size"->AbsoluteSizeSpan(m.value.toInt().coerceIn(8,96),true);"relative"->RelativeSizeSpan(m.value.toFloat().coerceIn(.5f,4f));"font"->TypefaceSpan(m.value);"link"->URLSpan(safeLink(m.value));"align"->AlignmentSpan.Standard(Layout.Alignment.valueOf(m.value));"indent"->LeadingMarginSpan.Standard(m.value.toInt().coerceIn(0,240));"list"->{val p=m.value.split(':');WriteListSpan(p[0],p.getOrNull(1)?.toIntOrNull()?:1,p.getOrNull(2)?.toIntOrNull()?:0)};else->return@runCatching}
        b.setSpan(span,m.start,m.end,if(m.kind in setOf("image","rule"))Spanned.SPAN_EXCLUSIVE_EXCLUSIVE else if(m.start==m.end||m.kind in setOf("list","align","indent"))Spanned.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }}}
    fun html():String {
        fun escape(s:String)=TextUtils.htmlEncode(s)
        val out=StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>${escape(title)}</title></head><body style=\"font-family:sans-serif;font-size:16pt;line-height:1.5;max-width:48em;margin:2em auto;padding:1em\">\n")
        var offset=0;var openList=""
        for(part in writeParagraphs(this)){
            offset=part.start;val line=part.text;val end=part.end;val relevant=part.marks;val para=relevant.filter{it.start<=offset&&it.end>offset};val list=para.firstOrNull{it.kind=="list"}?.value?.substringBefore(':')?:"";val tag=if(list=="number")"ol"else if(list=="bullet")"ul"else""
            if(tag!=openList){if(openList.isNotEmpty())out.append("</$openList>");if(tag.isNotEmpty())out.append("<$tag>");openList=tag}
            val align=para.firstOrNull{it.kind=="align"}?.value;val css=when(align){"ALIGN_CENTER"->"text-align:center;";"ALIGN_OPPOSITE"->"text-align:right;";else->""}+"margin-left:${para.firstOrNull{it.kind=="indent"}?.value?.toIntOrNull()?.coerceIn(0,240)?:0}px;"
            val p=if(tag.isEmpty())"p"else"li";out.append("<$p style=\"$css\">")
            val boundaries=(listOf(offset,end)+relevant.flatMap{listOf(it.start,it.end)}.filter{it>offset&&it<end}).distinct().sorted()
            boundaries.zipWithNext().forEach{(a,b)->val active=relevant.filter{it.start<=a&&it.end>=b};if(active.any{it.kind=="rule"}){out.append("<span role=\"separator\" style=\"display:inline-block;width:100%;border-top:1px solid #888\"></span>");return@forEach};val image=active.firstOrNull{it.kind=="image"};if(image!=null){out.append("<img alt=\"Embedded image\" style=\"max-width:100%;height:auto\" src=\"data:image/png;base64,${image.value}\">");return@forEach};val styles=StringBuilder();var bold=false;var italic=false;var under=false;var strike=false
                active.forEach{m->when(m.kind){"style"->{bold=bold||m.value.toInt()and Typeface.BOLD!=0;italic=italic||m.value.toInt()and Typeface.ITALIC!=0};"underline"->under=true;"strike"->strike=true;"color","highlight"->{val n=m.value.toInt();styles.append(if(m.kind=="color")"color:"else"background-color:").append("#%06X;".format(n and 0xffffff))};"size"->styles.append("font-size:${m.value.toInt().coerceIn(8,96)}pt;");"relative"->styles.append("font-size:${m.value.toFloat().coerceIn(.5f,4f)}em;");"font"->styles.append("font-family:${if(m.value in setOf("serif","monospace"))m.value else "sans-serif"};")}}
                if(bold)styles.append("font-weight:bold;");if(italic)styles.append("font-style:italic;");if(under||strike)styles.append("text-decoration:${if(under)"underline "else""}${if(strike)"line-through"else""};")
                val link=active.lastOrNull{it.kind=="link"}?.value?.let{runCatching{safeLink(it)}.getOrNull()};if(link!=null)out.append("<a href=\"${escape(link)}\">")
                out.append("<span style=\"$styles\">${escape(text.substring(a,b))}</span>");if(link!=null)out.append("</a>")
            }
            if(line.isEmpty())out.append("<br>");out.append("</$p>\n");offset=end+1
        }
        if(openList.isNotEmpty())out.append("</$openList>");return out.append("</body></html>").toString()
    }
}
internal fun safeLink(s:String):String {val uri=android.net.Uri.parse(s.trim());require(uri.scheme?.lowercase() in setOf("https","http","mailto")){"Use an https://, http:// or mailto: link"};return uri.toString().take(2048)}
/** Native horizontal rule; width follows the editor/PDF viewport, never a bitmap. */
internal class WriteRuleSpan:ReplacementSpan() {
    var widthPx=400
    var density=1f
    override fun getSize(p:Paint,text:CharSequence,start:Int,end:Int,fm:Paint.FontMetricsInt?):Int {
        fm?.apply{ascent=-(18*density).toInt();top=ascent;descent=(8*density).toInt();bottom=descent}
        return widthPx.coerceAtLeast(1)
    }
    override fun draw(c:Canvas,text:CharSequence,start:Int,end:Int,x:Float,top:Int,y:Int,bottom:Int,p:Paint) {
        val old=p.strokeWidth;p.strokeWidth=1.25f*density;c.drawLine(x,(top+bottom)/2f,x+widthPx.coerceAtLeast(1),(top+bottom)/2f,p);p.strokeWidth=old
    }
}
internal class WriteListSpan(val kind:String,val number:Int=1,val indent:Int=0):LeadingMarginSpan {
    var scale=1f
    override fun getLeadingMargin(first:Boolean)=((36+indent.coerceIn(0,8)*24)*scale).toInt()
    override fun drawLeadingMargin(c:Canvas,p:Paint,x:Int,dir:Int,top:Int,baseline:Int,bottom:Int,text:CharSequence,start:Int,end:Int,first:Boolean,layout:Layout){if(!first)return;val style=p.style;val align=p.textAlign;p.style=Paint.Style.FILL;p.textAlign=if(dir==1)Paint.Align.LEFT else Paint.Align.RIGHT;c.drawText(if(kind=="number")"$number."else"•",(x+dir*indent.coerceIn(0,8)*24*scale).toFloat(),baseline.toFloat(),p);p.style=style;p.textAlign=align}
}
internal object WriteFiles {
    fun allowed(c:Context):Boolean=runCatching{val rows=AppSdk.sync(c,JSONObject().put("action","list"))as JSONArray;(0 until rows.length()).map{rows.getJSONObject(it)}.any{r->r.optBoolean("installed")&&r.optJSONObject("manifest")?.optString("id")=="app.onklav.write"&&r.optJSONArray("grants")?.let{a->(0 until a.length()).any{a.optString(it)=="storage.private"}}==true}}.getOrDefault(false)
    fun authorize(c:Context){check(allowed(c)){"Write storage access is off. Restore it in Settings > Apps > Write."}}
    private fun dir(c:Context)=File(c.filesDir,"write/documents").apply{mkdirs()}
    private fun path(c:Context,id:String):File{require(id.matches(Regex("[a-f0-9-]{36}")));return File(dir(c),"$id.json")}
    fun list(c:Context):List<WriteDocument>{authorize(c);val hidden=AppSdk.folderVault(c,"visibility").getJSONArray("hidden").let{a->(0 until a.length()).map{a.getString(it)}.toSet()};return dir(c).listFiles().orEmpty().map{it.name.removeSuffix(".bak").removeSuffix(".new")}.filter{it.endsWith(".json")&&it.removeSuffix(".json") !in hidden}.distinct().mapNotNull{name->runCatching{read(c,name.removeSuffix(".json")).let{it.copy(text=it.text.take(180),marks=emptyList())}}.getOrNull()}.sortedByDescending{it.modified}}
    fun read(c:Context,id:String):WriteDocument=AppSdk.carrierEgress{authorize(c);val target=path(c,id);AppSdk.folderVault(c,"document",id);AtomicFile(target).openRead().use{WriteDocument.parse(JSONObject(it.readBytes().toString(Charsets.UTF_8)))}}
    fun save(c:Context,doc:WriteDocument)=AppSdk.carrierEgress{authorize(c);val target=path(c,doc.id);AppSdk.folderVault(c,"save_document",doc.id);require(doc.text.length<=500000);val bytes=doc.json().toString().toByteArray();require(bytes.size<=8_000_000){"Document has too much formatting"};val file=AtomicFile(target);val stream=file.startWrite();try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e};AppSdk.writeMigration(c,"sync",doc.id);check(c.getSharedPreferences("write",0).edit().putString("current",doc.id).commit())}
    fun delete(c:Context,id:String)=AppSdk.carrierEgress{authorize(c);val target=path(c,id);AppSdk.folderVault(c,"document",id);AppSdk.folderVault(c,"delete_document",id);AtomicFile(target).delete()}
}
internal object WriteExport {
    private fun xml(s:String)=TextUtils.htmlEncode(s)
    fun docx(doc:WriteDocument,out:OutputStream){ZipOutputStream(out).use{zip->fun entry(name:String,s:String){zip.putNextEntry(ZipEntry(name));zip.write(s.toByteArray());zip.closeEntry()}
        entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""")
        entry("_rels/.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
        val pictures=doc.marks.filter{it.kind=="image"}.map{it.value}.distinct().mapIndexed{i,v->v to "picture${i+1}"}.toMap()
        pictures.forEach{(data,id)->require(WriteImageSpan.valid(data));zip.putNextEntry(ZipEntry("word/media/$id.png"));zip.write(android.util.Base64.decode(data,android.util.Base64.NO_WRAP));zip.closeEntry()}
        val links=doc.marks.filter{it.kind=="link"}.mapNotNull{runCatching{safeLink(it.value)}.getOrNull()}.distinct().mapIndexed{i,url->url to "link${i+1}"}.toMap()
        entry("word/_rels/document.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>"""+links.entries.joinToString(""){(url,id)->"<Relationship Id=\"$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"${xml(url)}\" TargetMode=\"External\"/>"}+pictures.entries.joinToString(""){(_,id)->"<Relationship Id=\"$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$id.png\"/>"}+"</Relationships>")
        entry("word/numbering.xml","""<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl></w:abstractNum><w:abstractNum w:abstractNumId="2"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="1"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="2"/></w:num></w:numbering>""")
        zip.putNextEntry(ZipEntry("word/document.xml"));val b=java.io.OutputStreamWriter(zip,Charsets.UTF_8);b.append("""<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>""");var offset=0
        writeParagraphs(doc).forEach{part->offset=part.start;val line=part.text;val end=part.end;val relevant=part.marks;val para=relevant.filter{it.start<=offset&&it.end>offset};b.append("<w:p><w:pPr>");if(para.any{it.kind=="rule"})b.append("<w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"888888\"/></w:pBdr>");val align=para.lastOrNull{it.kind=="align"}?.value;b.append("<w:jc w:val=\"${when(align){"ALIGN_CENTER"->"center";"ALIGN_OPPOSITE"->"right";else->"left"}}\"/>");para.firstOrNull{it.kind=="list"}?.let{b.append("<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"${if(it.value.startsWith("number"))1 else 2}\"/></w:numPr>")};para.lastOrNull{it.kind=="indent"}?.let{b.append("<w:ind w:left=\"${(it.value.toIntOrNull()?:0)*15}\"/>")};b.append("</w:pPr>")
            val cuts=(listOf(offset,end)+relevant.flatMap{listOf(it.start,it.end)}.filter{it>offset&&it<end}).distinct().sorted()
            cuts.zipWithNext().forEach{(a,z)->val active=relevant.filter{it.start<=a&&it.end>=z};if(active.any{it.kind=="rule"})return@forEach;val picture=active.firstOrNull{it.kind=="image"};if(picture!=null){val span=WriteImageSpan(picture.value);val bounds=span.drawable.bounds;b.append("<w:r><w:pict><v:shape xmlns:v=\"urn:schemas-microsoft-com:vml\" id=\"${pictures[picture.value]}-$a\" style=\"width:${bounds.width()*.75}pt;height:${bounds.height()*.75}pt\" coordsize=\"21600,21600\" path=\"m0,0l21600,0,21600,21600,0,21600xe\" filled=\"f\" stroked=\"f\"><v:imagedata r:id=\"${pictures[picture.value]}\"/></v:shape></w:pict></w:r>");return@forEach};val href=active.lastOrNull{it.kind=="link"}?.value?.let{links[it]};if(href!=null)b.append("<w:hyperlink r:id=\"$href\">");val points=active.lastOrNull{it.kind=="size"}?.value?.toIntOrNull()?:active.lastOrNull{it.kind=="relative"}?.value?.toFloatOrNull()?.let{(it*16).roundToInt()}?:16;val family=when(active.lastOrNull{it.kind=="font"}?.value){"serif"->"Times New Roman";"monospace"->"Courier New";else->"Arial"};b.append("<w:r><w:rPr><w:sz w:val=\"${points*2}\"/><w:rFonts w:ascii=\"$family\" w:hAnsi=\"$family\"/>");active.forEach{m->when(m.kind){"style"->{if(m.value.toInt()and Typeface.BOLD!=0)b.append("<w:b/>");if(m.value.toInt()and Typeface.ITALIC!=0)b.append("<w:i/>")};"underline"->b.append("<w:u w:val=\"single\"/>");"strike"->b.append("<w:strike/>");"color"->b.append("<w:color w:val=\"%06X\"/>".format(m.value.toInt()and 0xffffff));"highlight"->b.append("<w:shd w:fill=\"%06X\"/>".format(m.value.toInt()and 0xffffff))}};b.append("</w:rPr><w:t xml:space=\"preserve\">${xml(doc.text.substring(a,z))}</w:t></w:r>");if(href!=null)b.append("</w:hyperlink>")};b.append("</w:p>");offset=end+1}
        b.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr></w:body></w:document>");b.flush();zip.closeEntry()
    }}
    fun pdfLayout(doc:WriteDocument):StaticLayout {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{textSize=16f;color=android.graphics.Color.BLACK;density=1f}
        val content=doc.spanned();content.getSpans(0,content.length,WriteRuleSpan::class.java).forEach{it.widthPx=499}
        return StaticLayout.Builder.obtain(content,0,content.length,paint,499).setLineSpacing(4f,1f).setIncludePad(true).build()
    }
    fun pdfPages(layout:StaticLayout):List<IntRange> {
        val pages=mutableListOf<IntRange>();var first=0
        do {require(pages.size<500){"PDF exceeds 500 pages. Export DOCX or split the document."};val y=layout.getLineTop(first);var last=first
            while(last+1<layout.lineCount&&layout.getLineBottom(last+1)-y<=730)last++
            pages.add(first..last);first=last+1
        }while(first<layout.lineCount)
        return pages
    }
    fun pdf(doc:WriteDocument,out:OutputStream){
        val layout=pdfLayout(doc);val pages=pdfPages(layout);val pdf=PdfDocument()
        try {pages.forEachIndexed{index,lines->
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,index+1).create())
            page.canvas.save();page.canvas.clipRect(48f,48f,547f,778f);page.canvas.translate(48f,48f-layout.getLineTop(lines.first));layout.draw(page.canvas);page.canvas.restore()
            val footer=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=10f;color=android.graphics.Color.GRAY};page.canvas.drawText((index+1).toString(),292f,810f,footer);pdf.finishPage(page)
        };pdf.writeTo(out)}finally{pdf.close()}
    }
    fun importDocx(bytes:ByteArray,onUnsupported:(Set<String>)->Unit={}):WriteDocument {
        val unsupported=mutableSetOf<String>()
        val files=mutableMapOf<String,String>();var total=0
        ZipInputStream(bytes.inputStream()).use{zip->var count=0;while(true){val e=zip.nextEntry?:break;if(e.name.matches(Regex("word/(header|footer)[0-9]*\\.xml")))unsupported+="headers/footers";require(++count<=512){"Too many items in this DOCX"};val data=java.io.ByteArrayOutputStream();val buf=ByteArray(16384);while(true){val n=zip.read(buf);if(n<0)break;total+=n;require(total<=24_000_000){"This DOCX is too large to import"};if(e.name in setOf("word/document.xml","word/numbering.xml","word/_rels/document.xml.rels"))data.write(buf,0,n)};if(data.size()>0)files[e.name]=data.toString("UTF-8")}}
        val source=files["word/document.xml"]?:error("This file does not contain a Word document")
        require(!source.contains("<!DOCTYPE",true)&&!source.contains("<!ENTITY",true)){"Unsupported document declarations"}
        val kinds=mutableMapOf<String,String>();val abstracts=mutableMapOf<String,String>();val numIds=mutableMapOf<String,String>();val hyperlinks=mutableMapOf<String,String>()
        fun scan(xml:String,action:(org.xmlpull.v1.XmlPullParser)->Unit){require(!xml.contains("<!DOCTYPE",true)&&!xml.contains("<!ENTITY",true));val p=android.util.Xml.newPullParser();p.setInput(xml.reader());while(p.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(p.eventType==org.xmlpull.v1.XmlPullParser.START_TAG)action(p);p.next()}}
        fun attribute(p:org.xmlpull.v1.XmlPullParser,k:String)=(0 until p.attributeCount).firstOrNull{p.getAttributeName(it)==k}?.let{p.getAttributeValue(it)}?:""
        var abstract="";var num=""
        scan(files["word/numbering.xml"]?:"<numbering/>"){p->when(p.name){"abstractNum"->{abstract=attribute(p,"abstractNumId");num=""};"numFmt"->abstracts[abstract]=if(attribute(p,"val")=="bullet")"bullet"else"number";"num"->{num=attribute(p,"numId");abstract=""};"abstractNumId"->if(num.isNotBlank())numIds[num]=attribute(p,"val")}}
        numIds.forEach{(n,a)->kinds[n]=abstracts[a]?:"number"}
        scan(files["word/_rels/document.xml.rels"]?:"<Relationships/>"){p->if(p.name=="Relationship"&&attribute(p,"Type").endsWith("/hyperlink")){runCatching{safeLink(attribute(p,"Target"))}.onSuccess{hyperlinks[attribute(p,"Id")]=it}}}
        var linkStart=0;var currentLink=""
        val parser=android.util.Xml.newPullParser();parser.setInput(source.reader());val text=StringBuilder();val marks=mutableListOf<WriteMark>();var runStart=0;var paraStart=0;val run=mutableMapOf<String,String>();var align="";var list="";var size=16;var heading="";var number=0;var indentation=0;var paragraphRule=false
        fun attr(k:String)=(0 until parser.attributeCount).firstOrNull{parser.getAttributeName(it)==k}?.let{parser.getAttributeValue(it)}?:""
        while(parser.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){val name=parser.name.orEmpty();when(parser.eventType){org.xmlpull.v1.XmlPullParser.START_TAG->when(name){"p"->{paragraphRule=false;paraStart=text.length;align="";list="";heading="";indentation=0};"r"->{runStart=text.length;run.clear()};"hyperlink"->{linkStart=text.length;currentLink=hyperlinks[attr("id")].orEmpty()};"pBdr"->paragraphRule=true;"tbl"->unsupported.add("table layout");"drawing","pict"->unsupported.add("images");"ind"->indentation=((attr("start").ifEmpty{attr("left")}.toIntOrNull()?:0)/15).coerceIn(0,240);"pStyle"->heading=attr("val");"b"->if(attr("val") !in setOf("0","false"))run["bold"]="1";"i"->if(attr("val") !in setOf("0","false"))run["italic"]="1";"u"->if(attr("val")!="none")run["underline"]="1";"strike"->if(attr("val") !in setOf("0","false"))run["strike"]="1";"color"->runCatching{run["color"]=(0xff000000.toInt() or attr("val").toInt(16)).toString()};"shd"->runCatching{run["highlight"]=(0xff000000.toInt() or attr("fill").toInt(16)).toString()};"sz"->runCatching{run["size"]=(attr("val").toInt()/2).coerceIn(8,96).toString()};"rFonts"->run["font"]=when(attr("ascii").lowercase()){"times new roman","serif","georgia"->"serif";"courier new","monospace","consolas"->"monospace";else->"sans-serif"};"jc"->align=when(attr("val")){"center"->"ALIGN_CENTER";"right","end"->"ALIGN_OPPOSITE";else->"ALIGN_NORMAL"};"numId"->{val id=attr("val");list=kinds[id]?:"number"};"t"->text.append(parser.nextText());"tab"->text.append('\t');"br"->text.append('\n')}
            org.xmlpull.v1.XmlPullParser.END_TAG->when(name){"hyperlink"->{if(currentLink.isNotBlank()&&linkStart<text.length)marks.add(WriteMark(linkStart,text.length,"link",currentLink));currentLink=""};"r"->{if(runStart<text.length){val flags=(if(run["bold"]=="1")Typeface.BOLD else 0)or(if(run["italic"]=="1")Typeface.ITALIC else 0);if(flags!=0)marks.add(WriteMark(runStart,text.length,"style",flags.toString()));run.filterKeys{it !in setOf("bold","italic")}.forEach{(k,v)->marks.add(WriteMark(runStart,text.length,k,v))}}};"p"->{if(paragraphRule&&text.length==paraStart){text.append('\uFFFC');marks.add(WriteMark(paraStart,paraStart+1,"rule","1"))};text.append('\n');if(align.isNotBlank())marks.add(WriteMark(paraStart,text.length,"align",align));if(indentation>0)marks.add(WriteMark(paraStart,text.length,"indent",indentation.toString()));if(list.isNotBlank()){number=if(list=="number")number+1 else 1;marks.add(WriteMark(paraStart,text.length,"list","$list:$number:0"))}else number=0;if(heading.startsWith("Heading",true)){size=when(heading.lastOrNull()){'1'->28;'2'->24;else->20};marks.add(WriteMark(paraStart,text.length,"size",size.toString()));marks.add(WriteMark(paraStart,text.length,"style",Typeface.BOLD.toString()))}}}
        };require(text.length<=500000){"Document exceeds 500,000 characters"};parser.next()}
        onUnsupported(unsupported.toSet());return WriteDocument(text=text.toString(),marks=marks)
    }
}
