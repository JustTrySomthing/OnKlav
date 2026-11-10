package app.onklav.home

import android.icu.lang.UCharacter
import android.icu.lang.UProperty

/** Speech-only projection. The document remains untouched; offsets are UTF-16 source positions. */
internal data class SpeechReading(val text:String,private val source:IntArray,private val sourceLength:Int){
 fun original(at:Int):Int=if(source.isEmpty()||at>=source.size)sourceLength else source[at.coerceAtLeast(0)]
 companion object {
  const val MAX_SPOKEN_LENGTH=200000
  private val titles=mapOf("mr" to "Mister","mrs" to "Missus","ms" to "Miz","dr" to "Doctor","prof" to "Professor","rev" to "Reverend","capt" to "Captain","lt" to "Lieutenant","sgt" to "Sergeant")
  private val title=Regex("(?i)(?<![\\p{L}\\p{N}_])(?:mr|mrs|ms|dr|prof|rev|capt|lt|sgt)\\.(?=[\\t\\p{Zs}]+\\p{L})")
  private val numbered=Regex("(?i)(?<![\\p{L}\\p{N}_])no\\.(?=[\\t\\p{Zs}]+\\p{Nd})")
  private val initials=Regex("(?<![\\p{L}\\p{N}_])(?:[A-Z]\\.[\\t\\p{Zs}]+){2,8}(?=[A-Z]\\p{Ll})")
  private val divider=Regex("[*#_=~•·─━═—-]{3,}")
  private val smiley=Regex("(?<!\\S)[:;][-^]?[)(DPp](?=\\s|$)")
  private val roman=Regex("(?=[MDCLXVI])M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")
  private val chapter=Regex("(?i)^(chapter|section|part|book)\\s+([IVXLCDM]+|[0-9]+)[.>:：]?$")
  private fun marker(line:String):Boolean {
   val value=line.trim();if(value.isEmpty())return false
   if(divider.matches(value.replace(" ","")))return true
   chapter.matchEntire(value)?.let{val n=it.groupValues[2];if(n.all{c->c.isDigit()}||roman.matches(n.uppercase(java.util.Locale.ROOT)))return true}
   val number=value.trimEnd('.',')','>','：',':').trimEnd()
   // Without a Chapter/Section label, retain units, initials and Roman-looking
   // words such as V, C, M and MIX. Prefer occasional over-reading to lost prose.
   val compact=if(number==value)number.length>1&&number.all{it in "IVX"}else number.all{it in "IVXL"}
   return compact&&roman.matches(number)
  }
  private fun emoji(cp:Int)=cp>127&&cp!=0x00a9&&cp!=0x00ae&&cp!=0x2122&&
   (UCharacter.hasBinaryProperty(cp,UProperty.EMOJI)||cp in 0x1f000..0x1faff)&&Character.getType(cp).let{it!=Character.OTHER_PUNCTUATION.toInt()&&it!=Character.MATH_SYMBOL.toInt()}
  private fun emojiEnd(text:String,start:Int):Int? {
   val cp=text.codePointAt(start);var at=start+Character.charCount(cp)
   // Digits, # and * are meaningful unless followed by a keycap combining mark.
   if(cp in 48..57||cp==35||cp==42){if(at<text.length&&text.codePointAt(at)==0xfe0f)at++;return if(at<text.length&&text.codePointAt(at)==0x20e3)at+1 else null}
   val styled=at<text.length&&text.codePointAt(at)==0xfe0f&&UCharacter.hasBinaryProperty(cp,UProperty.EMOJI)
   if(!emoji(cp)&&!styled)return null
   while(at<text.length){val next=text.codePointAt(at)
    when {
     next==0xfe0f||next==0xfe0e||next in 0x1f3fb..0x1f3ff||next in 0xe0020..0xe007f->at+=Character.charCount(next)
     next==0x200d&&at+1<text.length&&emoji(text.codePointAt(at+1))->at+=1+Character.charCount(text.codePointAt(at+1))
     else->return at
    }
   };return at
  }
  fun prepare(input:String):SpeechReading {
   require(input.length<=50000){"Speech input exceeds its limit"}
   val casing=speechText(input)
   val ignored=BooleanArray(input.length);var lineStart=0
   while(lineStart<input.length){val stop=input.indexOf('\n',lineStart).let{if(it<0)input.length else it};if(marker(input.substring(lineStart,stop)))for(i in lineStart until stop)ignored[i]=true;lineStart=stop+1}
   initials.findAll(input).forEach{m->for(i in m.range)if(input[i]=='.')ignored[i]=true}
   smiley.findAll(input).forEach{m->for(i in m.range)ignored[i]=true}
   val replacements=mutableMapOf<Int,Pair<Int,String>>()
   title.findAll(input).forEach{m->replacements[m.range.first]=(m.range.last+1) to titles.getValue(m.value.dropLast(1).lowercase(java.util.Locale.ROOT))}
   numbered.findAll(input).forEach{m->replacements[m.range.first]=(m.range.last+1) to "number"}
   val out=StringBuilder(input.length);val offsets=ArrayList<Int>(input.length)
   fun emit(c:Char,origin:Int){
    // Decorative runs should neither join words nor create long artificial pauses.
    if(c==' '&&out.isNotEmpty()&&out.last() in " \t")return
    out.append(c);offsets.add(origin)
   }
   var at=0
   while(at<input.length){
    if(ignored[at]){at++;continue}
    val raw=input.codePointAt(at)
    if(raw==32||raw>127&&Character.getType(raw)==Character.SPACE_SEPARATOR.toInt()){emit(' ',at);at+=Character.charCount(raw);continue}
    if(raw==0x00ad||raw==0xfeff||raw==0x2060){at+=Character.charCount(raw);continue}
    if(raw==0x200b){emit(' ',at);at++;continue}
    val end=emojiEnd(input,at)
    if(end!=null){emit(' ',at);at=end;continue}
    val match=replacements[at]
    if(match!=null){match.second.forEach{emit(it,at)};at=match.first;continue}
    // Keep surrogate pairs and combining characters verbatim when they are not emoji.
    val cp=input.codePointAt(at);val count=Character.charCount(cp);repeat(count){emit(casing[at+it],at)};at+=count
   }
   check(out.length<=MAX_SPOKEN_LENGTH);return SpeechReading(out.toString(),offsets.toIntArray(),input.length)
  }
 }
}
