package app.onklav.home

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object CodeText {
 const val MAX_BYTES=1_000_000
 const val MAX_CHARS=500_000
 val languages=listOf("Plain text","Rust","Kotlin","GDScript","PHP","HTML","CSS","JavaScript","TypeScript","Go","Python","XML","JSON","YAML","TOML","Markdown","Shell","SQL","C","C++","Java","C#","Lua")
 fun language(name:String)=when(name.substringAfterLast('.',"").lowercase()){
  "rs"->"Rust";"kt","kts"->"Kotlin";"gd","gdshader","tscn","tres","godot","cfg","ini"->"GDScript";"php"->"PHP";"html","htm","vue","svelte"->"HTML";"css","scss","sass","less"->"CSS";"js","jsx","mjs","cjs"->"JavaScript";"ts","tsx"->"TypeScript";"go"->"Go";"py","pyw"->"Python";"xml","svg"->"XML";"json","jsonc","onklav-theme"->"JSON";"yaml","yml"->"YAML";"toml"->"TOML";"md","markdown"->"Markdown";"sh","bash","zsh"->"Shell";"sql"->"SQL";"c","h"->"C";"cpp","cc","hpp"->"C++";"java"->"Java";"cs"->"C#";"lua"->"Lua";else->"Plain text"
 }
 data class Decoded(val text:String,val encoding:String="UTF-8",val bom:Boolean=false,val ending:String="LF")
 fun decode(bytes:ByteArray):Decoded{
  require(bytes.size<=MAX_BYTES){"Choose a source file smaller than 1 MB."}
  val utf8=bytes.size>=3&&bytes[0]==0xEF.toByte()&&bytes[1]==0xBB.toByte()&&bytes[2]==0xBF.toByte()
  val le=bytes.size>=2&&bytes[0]==0xFF.toByte()&&bytes[1]==0xFE.toByte();val be=bytes.size>=2&&bytes[0]==0xFE.toByte()&&bytes[1]==0xFF.toByte()
  val encoding=if(le)"UTF-16LE"else if(be)"UTF-16BE"else"UTF-8";val skip=if(utf8)3 else if(le||be)2 else 0
  val raw=charset(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,skip,bytes.size-skip)).toString()
  require(raw.length<=MAX_CHARS&&!raw.contains('\u0000')&&raw.count{it<' '&&it !in "\n\r\t"}==0){"Choose a text source file (up to 500,000 characters). Binary files are not editable."}
  val ending=if(raw.contains("\r\n")&&!raw.replace("\r\n","").contains('\n')&&!raw.replace("\r\n","").contains('\r'))"CRLF"else if(raw.contains('\r')&&!raw.contains('\n'))"CR"else"LF"
  return Decoded(raw.replace("\r\n","\n").replace('\r','\n'),encoding,utf8||le||be,ending)
 }
 fun encode(text:String,encoding:String,bom:Boolean,ending:String):ByteArray{
  require(text.length<=MAX_CHARS);require(encoding in listOf("UTF-8","UTF-16LE","UTF-16BE")&&ending in listOf("LF","CRLF","CR"))
  val normalized=text.replace("\n",when(ending){"CRLF"->"\r\n";"CR"->"\r";else->"\n"});val buffer=charset(encoding).newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(normalized));val raw=ByteArray(buffer.remaining()).also{buffer.get(it)};val prefix=if(!bom)byteArrayOf()else when(encoding){"UTF-16LE"->byteArrayOf(-1,-2);"UTF-16BE"->byteArrayOf(-2,-1);else->byteArrayOf(-17,-69,-65)}
  require(raw.size+prefix.size<=MAX_BYTES){"Encoded file exceeds 1 MB. Split it into smaller files."};return prefix+raw
 }
 data class Edit(val text:String,val start:Int,val end:Int=start)
 fun insert(text:String,start:Int,end:Int,value:String):Edit{val a=minOf(start,end).coerceIn(0,text.length);val b=maxOf(start,end).coerceIn(a,text.length);val next=text.replaceRange(a,b,value);require(next.length<=MAX_CHARS);return Edit(next,a+value.length)}
 fun indent(text:String,start:Int,end:Int,unit:String,out:Boolean=false):Edit{
  val a=minOf(start,end).coerceIn(0,text.length);val b=maxOf(start,end).coerceIn(a,text.length);val first=text.lastIndexOf('\n',(a-1).coerceAtLeast(-1))+1
  val stop=if(b>a&&text.getOrNull(b-1)=='\n')b-1 else b;val last=text.indexOf('\n',stop).let{if(it<0)text.length else it}
  val block=text.substring(first,last);val changed=block.split('\n').joinToString("\n"){line->if(!out)unit+line else when{line.startsWith('\t')->line.drop(1);else->line.drop(line.takeWhile{it==' '}.length.coerceAtMost(unit.length))}}
  val next=text.replaceRange(first,last,changed);require(next.length<=MAX_CHARS);return Edit(next,first,first+changed.length)
 }
 fun comment(text:String,start:Int,end:Int,language:String):Edit{
  val prefix=when(language){"Python","GDScript","Shell","YAML","TOML"->"#";"SQL","Lua"->"--";"HTML","XML","CSS","JSON","Plain text","Markdown"->return Edit(text,start,end);else->"//"}
  val a=minOf(start,end);val b=maxOf(start,end);val first=text.lastIndexOf('\n',a-1)+1;val last=text.indexOf('\n',if(b>a&&text[b-1]=='\n')b-1 else b).let{if(it<0)text.length else it};val lines=text.substring(first,last).split('\n');val remove=lines.filter{it.isNotBlank()}.all{it.trimStart().startsWith(prefix)}
  val block=lines.joinToString("\n"){line->val n=line.takeWhile{it==' '||it=='\t'}.length;if(line.isBlank())line else if(remove)line.take(n)+line.drop(n+prefix.length).removePrefix(" ")else line.take(n)+prefix+" "+line.drop(n)}
  val next=text.replaceRange(first,last,block);require(next.length<=MAX_CHARS);return Edit(next,first,first+block.length)
 }
 fun find(text:String,query:String,from:Int,back:Boolean=false,case:Boolean=false):Int {if(query.isEmpty())return -1;return if(back)text.lastIndexOf(query,(from-1).coerceAtMost(text.length),!case).let{if(it<0)text.lastIndexOf(query,text.length,!case)else it}else text.indexOf(query,from.coerceIn(0,text.length),!case).let{if(it<0)text.indexOf(query,0,!case)else it}}
 fun replaceAll(text:String,query:String,replacement:String,case:Boolean):String{
  require(query.isNotEmpty());var at=0;var count=0
  while(true){val found=text.indexOf(query,at,!case);if(found<0)break;count++;require(text.length.toLong()+count.toLong()*(replacement.length-query.length)<=MAX_CHARS){"Replacement exceeds file limit"};at=found+query.length}
  return text.replace(query,replacement,!case)
 }
 /** Strict bounded JSON validation: org.json accepts non-JSON syntax, so it is not used here. */
 fun validateJson(text:String){
  var i=0
  val numberPattern=Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
  fun fail():Nothing=throw IllegalArgumentException("Invalid JSON at line ${text.take(i).count{it=='\n'}+1}, column ${i-(text.lastIndexOf('\n',i-1)+1)+1}")
  fun ws(){while(i<text.length&&text[i] in " \t\r\n")i++}
  fun string(){if(text.getOrNull(i)!='"')fail();i++;while(i<text.length){val ch=text[i++];if(ch=='"')return;if(ch<' ')fail();if(ch=='\\'){if(i>=text.length)fail();val e=text[i++];if(e=='u'){repeat(4){if(i>=text.length||text[i++].digitToIntOrNull(16)==null)fail()}}else if(e !in "\"\\/bfnrt")fail()}};fail()}
  fun value(depth:Int){
   if(depth>64)throw IllegalArgumentException("JSON nesting exceeds 64 levels");ws();when(text.getOrNull(i)){
    '{'->{i++;ws();if(text.getOrNull(i)=='}'){i++;return};while(true){ws();string();ws();if(text.getOrNull(i++)!=':')fail();value(depth+1);ws();when(text.getOrNull(i++)){'}'->return;','->{};else->fail()}}}
    '['->{i++;ws();if(text.getOrNull(i)==']'){i++;return};while(true){value(depth+1);ws();when(text.getOrNull(i++)){']'->return;','->{};else->fail()}}}
    '"'->string()
    else->{val literal=listOf("true","false","null").firstOrNull{text.startsWith(it,i)};if(literal!=null)i+=literal.length else{val match=numberPattern.find(text,i);if(match==null||match.range.first!=i)fail();i=match.range.last+1}}
   }
  }
  value(0);ws();if(i!=text.length)fail()
 }
 data class Token(val start:Int,val end:Int,val kind:Int)
 private val keywords=("fn let mut pub use mod impl trait struct enum match if else for while loop return break continue const static async await move self Self true false null nil None Some class fun val var object interface package import override private internal protected public when is in as try catch finally throw throws new this super extends implements void int float double bool boolean string String func type map range defer go chan select switch case default from def lambda pass with yield raise except elif and or not print signal extends export onready preload load get set await vararg local function end then do repeat until require echo namespace foreach include instanceof abstract final readonly record data sealed suspend where unsafe dyn ref macro_rules crate extern sizeof typedef union auto register signed unsigned volatile virtual template using operator delete synchronized instanceof elif nonlocal global assert del async await nulls last first select update insert into create table values where join group order by having limit distinct key primary default property constructor init companion as? is? @export @onready").split(Regex("\\s+")).toSet()
 /** Linear lexer; emits only the editing viewport's neighborhood, including multiline string/comment state. */
 fun tokens(text:String,language:String,from:Int=0,to:Int=text.length):List<Token>{
  if(language=="Plain text")return emptyList();val out=ArrayList<Token>();var i=0;val hash=language in listOf("Python","GDScript","Shell","YAML","TOML","PHP");val markup=language in listOf("HTML","XML");val dash=language in listOf("Lua","SQL")
  fun emit(a:Int,b:Int,k:Int){if(b>from&&a<to&&out.size<6000)out+=Token(a,b,k)}
  while(i<text.length&&i<to){val a=i;val c=text[i]
   when{
    text.startsWith("<!--",i)&&markup->{i=text.indexOf("-->",i+4).let{if(it<0)text.length else it+3};emit(a,i,1)}
    text.startsWith("/*",i)->{i=text.indexOf("*/",i+2).let{if(it<0)text.length else it+2};emit(a,i,1)}
    text.startsWith("//",i)&&(!hash||language=="PHP")||c=='#'&&hash||dash&&text.startsWith("--",i)->{i=text.indexOf('\n',i).let{if(it<0)text.length else it};emit(a,i,1)}
    language=="Rust"&&c=='\''&&text.getOrNull(i+1)?.let{it.isLetter()||it=='_'}==true&&text.getOrNull(i+2)!='\''->{i++;while(i<text.length&&(text[i].isLetterOrDigit()||text[i]=='_'))i++;emit(a,i,4)}
    c=='"'||c=='\''||c=='`'->{val triple= i+2<text.length&&text[i+1]==c&&text[i+2]==c;val marker=if(triple)"$c$c$c"else"$c";i+=marker.length;while(i<text.length){if(text[i]=='\\'){i=(i+2).coerceAtMost(text.length);continue};if(text.startsWith(marker,i)){i+=marker.length;break};i++};emit(a,i,2)}
    c.isDigit()->{i++;while(i<text.length&&(text[i].isLetterOrDigit()||text[i] in "._"))i++;emit(a,i,3)}
    c.isLetter()||c=='_'||c=='$'->{i++;while(i<text.length&&(text[i].isLetterOrDigit()||text[i] in "_$"))i++;val word=text.substring(a,i);val kind=if(word in keywords)4 else if(language in listOf("CSS","JSON","YAML","TOML")&&i<text.length&&text[i]==':')5 else if(markup&&a>0&&text[a-1] in "</")4 else if(i<text.length&&text[i]=='(')5 else 0;if(kind>0)emit(a,i,kind)}
    else->i++
   }
  };return out
 }
}
