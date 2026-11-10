package app.onklav.home

import android.text.Spanned
import android.util.Xml
import org.xmlpull.v1.XmlPullParser as XP
import java.io.File
import java.util.zip.ZipFile
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter

/** Shared, offline, read-only document model. Never follows document links or evaluates macros/formulas. */
internal data class ReadingSheet(val name:String,val rows:List<List<String>>)
internal data class ReadingDocument(val text:String="",val styled:Spanned?=null,val sheets:List<ReadingSheet> = emptyList(),val notice:String="")
internal object DocumentReading {
 const val MAX_BYTES=32L*1024*1024
 const val MAX_TEXT=500_000
 const val MAX_ROWS=10_000
 const val MAX_CELLS=100_000
 const val MAX_COLUMNS=256
 val extensions=setOf("pdf","odf","odt","txt","doc","docx","rtf","ods","xlsx","xls","csv","tsv")
 val mimes=arrayOf("application/pdf","text/plain","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/rtf","text/rtf","application/vnd.oasis.opendocument.text","application/vnd.oasis.opendocument.formula","application/vnd.oasis.opendocument.spreadsheet","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel","text/csv","text/tab-separated-values","application/csv")
 fun supports(name:String,mime:String)=name.substringAfterLast('.',"").lowercase() in extensions||mime in mimes
 private const val DOCUMENT_NOTICE="Reading view · complex layouts, images and embedded objects may be omitted. The original is unchanged."
 private const val SHEET_NOTICE="Values view · cached formula results; no recalculation, charts, macros or original styling."
 fun read(file:File,name:String):ReadingDocument {
  require(file.length() in 1..MAX_BYTES){"Choose a document up to 32 MB."}
  val ext=name.substringAfterLast('.',"").lowercase()
  val value=when(ext){
   "txt"->ReadingDocument(text=FormatSwapEngine.decodeText(file.readBytes()))
   "csv","tsv"->ReadingDocument(sheets=listOf(ReadingSheet("Sheet 1",csv(FormatSwapEngine.decodeText(file.readBytes()),if(ext=="tsv")'\t' else null))))
   "docx"->{var warnings=emptySet<String>();val doc=WriteExport.importDocx(file.readBytes()){warnings=it};ReadingDocument(doc.text,doc.spanned(),notice=DOCUMENT_NOTICE+(if(warnings.isNotEmpty())" Omitted: ${warnings.joinToString()}." else ""))}
   "rtf"->ReadingDocument(text=rtf(file.readBytes()),notice=DOCUMENT_NOTICE)
   "odt","odf","ods"->odf(file)
   "xlsx"->xlsx(file)
   "doc"->POIFSFileSystem(file,true).use{fs->HWPFDocument(fs).use{d->ReadingDocument(text=d.range.text().replace('\r','\n').replace('\u0007','\t').replace(Regex("[\\p{Cc}&&[^\\n\\t]]"),""),notice=DOCUMENT_NOTICE)}}
   "xls"->POIFSFileSystem(file,true).use{fs->HSSFWorkbook(fs).use{w->val fmt=DataFormatter();var cells=0;var chars=0
    require(w.numberOfSheets<=64){"Use a workbook with at most 64 sheets."}
    ReadingDocument(sheets=(0 until w.numberOfSheets).filter{!w.isSheetHidden(it)&&!w.isSheetVeryHidden(it)}.map{i->val s=w.getSheetAt(i);require(s.lastRowNum<MAX_ROWS){"Use sheets with at most $MAX_ROWS rows."};ReadingSheet(s.sheetName,(0..s.lastRowNum).map{ri->val row=s.getRow(ri);val n=(row?.lastCellNum?.toInt()?:0).coerceAtLeast(0);require(n<=MAX_COLUMNS){"Use sheets with at most $MAX_COLUMNS columns."};require(cells+n<=MAX_CELLS){"Workbook is too large for mobile reading."};cells+=n;(0 until n).map{ci->val c=row?.getCell(ci);val v=try{if(c==null)"" else if(c.cellType==org.apache.poi.ss.usermodel.CellType.FORMULA){when(c.cachedFormulaResultType){org.apache.poi.ss.usermodel.CellType.STRING->c.stringCellValue;org.apache.poi.ss.usermodel.CellType.BOOLEAN->c.booleanCellValue.toString();org.apache.poi.ss.usermodel.CellType.NUMERIC->fmt.formatRawCellContents(c.numericCellValue,c.cellStyle.dataFormat.toInt(),c.cellStyle.dataFormatString);else->"#ERROR"}}else fmt.formatCellValue(c)}catch(e:LinkageError){if(c==null)"" else when(if(c.cellType==org.apache.poi.ss.usermodel.CellType.FORMULA)c.cachedFormulaResultType else c.cellType){org.apache.poi.ss.usermodel.CellType.NUMERIC->java.math.BigDecimal.valueOf(c.numericCellValue).stripTrailingZeros().toPlainString();org.apache.poi.ss.usermodel.CellType.STRING->c.stringCellValue;org.apache.poi.ss.usermodel.CellType.BOOLEAN->c.booleanCellValue.toString();else->"#ERROR"}};chars+=v.length;require(chars<=MAX_TEXT){"Workbook text exceeds 500,000 characters."};v}})},notice=SHEET_NOTICE)
   }}
   else->error("This format cannot be read. Choose a supported document.")
  }
  require(value.text.length<=MAX_TEXT && value.sheets.sumOf{s->s.rows.sumOf{r->r.sumOf{it.length}}}<=MAX_TEXT){"Document exceeds 500,000 characters."}
  return value
 }
 private fun xml(source:String):XP {require(!source.contains("<!DOCTYPE",true)&&!source.contains("<!ENTITY",true)){"Document declarations are not supported."};return Xml.newPullParser().apply{setFeature(XP.FEATURE_PROCESS_NAMESPACES,true);setInput(source.reader())}}
 private fun XP.attr(name:String)=(0 until attributeCount).firstOrNull{getAttributeName(it)==name}?.let{getAttributeValue(it)}.orEmpty()
 private class Package(file:File):java.io.Closeable {
  val zip=ZipFile(file);var total=0L
  init{require(zip.size()<=2048){"Too many entries in this document."}}
  fun get(name:String):String? {require(!name.startsWith('/')&&!name.split('/').contains(".."));val e=zip.getEntry(name)?:return null;require(e.size<=24_000_000);return zip.getInputStream(e).use{input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(16384);while(true){val n=input.read(b);if(n<0)break;total+=n;require(total<=48_000_000&&out.size()+n<=24_000_000){"Expanded document is too large."};out.write(b,0,n)};FormatSwapEngine.decodeText(out.toByteArray())}}
  override fun close()=zip.close()
 }
 private fun odf(file:File)=Package(file).use{pkg->val p=xml(pkg.get("content.xml")?:error("No OpenDocument content found."));val text=StringBuilder();val sheets=mutableListOf<ReadingSheet>();var sheetName="";var rows=mutableListOf<List<String>>();var row=mutableListOf<String>();var cell=StringBuilder();var inCell=false;var fallbackCell="";var pendingEmpty=0;var repeatCell=1;var repeatRow=1;var pendingRows=0;var count=0;var chars=0;var spreadsheet=false;var formula=false
  fun append(s:String){val b=if(inCell)cell else text;b.append(s);require(b.length<=MAX_TEXT)}
  while(p.eventType!=XP.END_DOCUMENT){when(p.eventType){XP.START_TAG->when(p.name){"spreadsheet"->spreadsheet=true;"formula"->formula=true;"table"->if(spreadsheet){require(sheets.size<64);sheetName=p.attr("name");rows=mutableListOf();pendingRows=0};"table-row"->if(spreadsheet){row=mutableListOf();pendingEmpty=0;repeatRow=(p.attr("number-rows-repeated").toIntOrNull()?:1).coerceAtLeast(1)};"table-cell","covered-table-cell"->if(spreadsheet){inCell=true;cell=StringBuilder();fallbackCell=p.attr("string-value").ifEmpty{p.attr("date-value")}.ifEmpty{p.attr("time-value")}.ifEmpty{p.attr("boolean-value")}.ifEmpty{p.attr("value")};repeatCell=(p.attr("number-columns-repeated").toIntOrNull()?:1).coerceAtLeast(1)};"s"->append(" ".repeat((p.attr("c").toIntOrNull()?:1).coerceIn(1,1000)));"tab"->append("\t");"line-break"->append("\n");"annotation"->{var depth=1;while(depth>0){when(p.next()){XP.START_TAG->depth++;XP.END_TAG->depth--;XP.END_DOCUMENT->error("Damaged annotation")}}}}
   XP.TEXT->if(inCell||!spreadsheet)append(p.text)
   XP.END_TAG->when(p.name){"p","h"->append("\n");"table-cell","covered-table-cell"->if(inCell){val value=cell.toString().trimEnd().ifEmpty{fallbackCell};if(value.isEmpty()){require(pendingEmpty.toLong()+repeatCell<=1_048_576);pendingEmpty+=repeatCell}else{require(row.size.toLong()+pendingEmpty+repeatCell<=MAX_COLUMNS){"Sheet has more than $MAX_COLUMNS columns."};repeat(pendingEmpty){row.add("")};pendingEmpty=0;repeat(repeatCell){row.add(value)}};inCell=false};"table-row"->if(spreadsheet){val values=row.toList();if(values.isEmpty()){require(pendingRows.toLong()+repeatRow<=1_048_576);pendingRows+=repeatRow}else{require(rows.size.toLong()+pendingRows+repeatRow<=MAX_ROWS&&count.toLong()+values.size.toLong()*repeatRow<=MAX_CELLS){"Sheet is too large for mobile reading."};require(chars.toLong()+values.sumOf{it.length}.toLong()*repeatRow<=MAX_TEXT);count+=values.size*repeatRow;chars+=values.sumOf{it.length}*repeatRow;repeat(pendingRows){rows.add(emptyList())};pendingRows=0;repeat(repeatRow){rows.add(values)}}};"table"->if(spreadsheet)sheets.add(ReadingSheet(sheetName,rows.toList()))}
  };p.next()}
  if(spreadsheet)ReadingDocument(sheets=sheets,notice=SHEET_NOTICE) else ReadingDocument(text=text.toString().trim(),notice=if(formula)"Formula reading view · equation layout may differ." else DOCUMENT_NOTICE)
 }
 private fun xlsx(file:File)=Package(file).use{pkg->
  val strings=mutableListOf<String>();var sharedChars=0;pkg.get("xl/sharedStrings.xml")?.let{source->val p=xml(source);var b=StringBuilder();while(p.eventType!=XP.END_DOCUMENT){if(p.eventType==XP.START_TAG){when(p.name){"si"->b=StringBuilder();"t"->b.append(p.nextText())}}else if(p.eventType==XP.END_TAG&&p.name=="si"){sharedChars+=b.length;require(strings.size<MAX_CELLS&&sharedChars<=MAX_TEXT);strings.add(b.toString())};p.next()}}
  val rels=mutableMapOf<String,String>();val rp=xml(pkg.get("xl/_rels/workbook.xml.rels")?:error("No workbook relationships."));while(rp.eventType!=XP.END_DOCUMENT){if(rp.eventType==XP.START_TAG&&rp.name=="Relationship"&&rp.attr("TargetMode")!="External"){val target=rp.attr("Target");if(rp.attr("Type").endsWith("/worksheet"))rels[rp.attr("Id")]=if(target.startsWith("/xl/"))target.drop(1)else "xl/$target"};rp.next()}
  val names=mutableListOf<Pair<String,String>>();val wp=xml(pkg.get("xl/workbook.xml")?:error("No workbook found."));while(wp.eventType!=XP.END_DOCUMENT){if(wp.eventType==XP.START_TAG&&wp.name=="sheet"&&wp.attr("state")!in setOf("hidden","veryHidden")){require(names.size<64);names.add(wp.attr("name") to (rels[wp.attr("id")]?:error("Missing sheet.")))};wp.next()}
  var count=0;var chars=0
  ReadingDocument(sheets=names.map{(name,path)->val p=xml(pkg.get(path)?:error("Sheet is missing."));val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();var rowIndex=0;var col=0;var type="";var value=StringBuilder()
   while(p.eventType!=XP.END_DOCUMENT){when(p.eventType){XP.START_TAG->when(p.name){"row"->{rowIndex=(p.attr("r").toIntOrNull()?:rows.size+1)-1;require(rowIndex in rows.size until MAX_ROWS){"Sheet row limit exceeded."};row=mutableListOf()};"c"->{val ref=p.attr("r");col=if(ref.isBlank())row.size else column(ref);require(col in row.size until MAX_COLUMNS){"Sheet column limit exceeded."};type=p.attr("t");value=StringBuilder()};"v","t"->value.append(p.nextText())};XP.END_TAG->when(p.name){"c"->{while(row.size<col)row.add("");var v=value.toString();if(type=="s")v=strings.getOrNull(v.toIntOrNull()?:-1)?:error("Invalid shared string.");if(type=="b")v=if(v=="1")"TRUE" else "FALSE";chars+=v.length;require(chars<=MAX_TEXT);row.add(v);require(++count<=MAX_CELLS)};"row"->{require(count+rowIndex-rows.size<=MAX_CELLS);while(rows.size<rowIndex)rows.add(emptyList());rows.add(row.toList())}}};p.next()};ReadingSheet(name,rows)},notice="$SHEET_NOTICE Dates may appear as stored serial numbers.")
 }
 private fun column(ref:String):Int {var n=0;for(c in ref.takeWhile{it.isLetter()}){require(c.uppercaseChar() in 'A'..'Z');n=n*26+(c.uppercaseChar()-'A'+1);require(n<=MAX_COLUMNS)};return n-1}
 fun csv(text:String,delimiter:Char?=null):List<List<String>> {
  require(text.length<=MAX_TEXT){"Text exceeds 500,000 characters."};val d=delimiter?:run{val counts=linkedMapOf(',' to 0,';' to 0,'\t' to 0);var quoted=false;var at=0;while(at<minOf(text.length,8192)){val ch=text[at++];if(ch=='"'){if(quoted&&at<text.length&&text[at]=='"')at++ else quoted=!quoted}else if(!quoted&&ch in counts)counts[ch]=counts.getValue(ch)+1};counts.maxBy{it.value}.key};val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val cell=StringBuilder();var quoted=false;var i=0;var cells=0
  fun field(){require(++cells<=MAX_CELLS&&row.size<MAX_COLUMNS){"Table is too large for mobile reading."};row.add(cell.toString());cell.setLength(0)}
  fun line(){field();require(rows.size<MAX_ROWS);rows.add(row);row=mutableListOf()}
  while(i<text.length){val c=text[i++];when{c=='"'&&quoted->{if(i<text.length&&text[i]=='"'){cell.append('"');i++}else quoted=false};c=='"'&&cell.isEmpty()->quoted=true;quoted->cell.append(c);c==d->field();c=='\r'||c=='\n'->{if(c=='\r'&&i<text.length&&text[i]=='\n')i++;line()};else->cell.append(c)}};require(!quoted){"CSV has an unclosed quoted field."};if(cell.isNotEmpty()||row.isNotEmpty())line();return rows
 }
 fun rtf(bytes:ByteArray):String {
  val s=bytes.toString(Charsets.ISO_8859_1);require(s.startsWith("{\\rtf")){"This is not an RTF document."};data class State(var skip:Boolean=false,var uc:Int=1)
  val stack=java.util.ArrayDeque<State>();var state=State();val out=StringBuilder();var i=0;var fallback=0;var charset=charset("windows-1252")
  val ignored=setOf("fonttbl","colortbl","stylesheet","info","pict","object","filetbl","header","footer","fldinst","datastore","themedata","xmlnstbl","listtable","listoverridetable")
  fun add(c:Char){if(fallback>0)fallback-- else if(!state.skip){out.append(c);require(out.length<=MAX_TEXT)}}
  while(i<s.length){val c=s[i++];when(c){'{'->{require(stack.size<128);stack.push(state.copy())};'}'->{require(stack.isNotEmpty());state=stack.pop()};'\\'->{require(i<s.length);val k=s[i++];when{ k in "\\{}"->add(k);k=='*'->state.skip=true;k=='\''->{require(i+2<=s.length);val b=s.substring(i,i+2).toInt(16).toByte();i+=2;add(byteArrayOf(b).toString(charset)[0])};k=='~'->add('\u00a0');k=='_'->add('\u2011');k=='-'->Unit;k.isLetter()->{val start=i-1;while(i<s.length&&s[i].isLetter())i++;val word=s.substring(start,i);val numStart=i;if(i<s.length&&s[i]=='-')i++;while(i<s.length&&s[i].isDigit())i++;val num=s.substring(numStart,i).toIntOrNull();if(i<s.length&&s[i]==' ')i++;when{word in ignored->state.skip=true;word=="ansicpg"&&num!=null->charset=runCatching{charset("windows-$num")}.getOrDefault(charset);word=="uc"->state.uc=(num?:1).coerceIn(0,16);word=="u"&&num!=null->{fallback=0;add(num.toChar());fallback=state.uc};word=="bin"->{val n=num?:error("Invalid binary payload");require(n>=0&&n<=s.length-i);i+=n};word in setOf("par","line")->add('\n');word=="tab"->add('\t');word=="emdash"->add('—');word=="endash"->add('–');word=="bullet"->add('•');word=="lquote"->add('‘');word=="rquote"->add('’');word=="ldblquote"->add('“');word=="rdblquote"->add('”')}}}};'\r','\n'->Unit;else->add(c)}};require(stack.isEmpty()){ "Damaged RTF document." };return out.toString()
 }
}
