package app.onklav.home

import android.content.Context
import java.util.Locale

internal data class WriteIssue(val start:Int,val end:Int,val message:String,val replacement:String,val kind:String="grammar",val suggestions:List<String> = if(replacement.isBlank()) emptyList() else listOf(replacement))
internal fun writeLocalChecks(text:String):List<WriteIssue>{
 val issues=mutableListOf<WriteIssue>()
 val protected=Regex("https?://\\S+|www\\.\\S+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]+|`[^`]*`",RegexOption.IGNORE_CASE).findAll(text).map{it.range}.toList()
 fun add(a:Int,b:Int,label:String,value:String){if(protected.none{a<=it.last&&b>it.first})issues+=WriteIssue(a,b,label,value)}
 Regex("(?i)\\b(the|a|an|and|or|to|of|in|is|are|was|were|with|for)\\s+\\1\\b").findAll(text).forEach{add(it.range.first,it.range.last+1,"Repeated word",it.groupValues[1])}
 Regex("(?<=\\S) {2,}(?=\\S)").findAll(text).forEach{add(it.range.first,it.range.last+1,"Extra spaces"," ")}
 Regex("\\bi\\b").findAll(text).forEach{add(it.range.first,it.range.last+1,"Capitalize I","I")}
 Regex("(?:^|[.!?]\\s+)([a-z][a-z']*)\\b").findAll(text).forEach{val g=it.groups[1]!!;if(g.value!="i")add(g.range.first,g.range.last+1,"Sentence capitalization",g.value.replaceFirstChar{c->c.uppercase()})}
 Regex("(?i)\\b(could|should|would|must|might) of\\b").findAll(text).forEach{add(it.range.first,it.range.last+1,"Use have after a modal verb",it.groupValues[1]+" have")}
 val agreement=mapOf("i is" to "I am","i are" to "I am","you is" to "you are","we is" to "we are","they is" to "they are","we was" to "we were","they was" to "they were","he are" to "he is","she are" to "she is","it are" to "it is","he have" to "he has","she have" to "she has","it have" to "it has","doesn't has" to "doesn't have","does not has" to "does not have","didn't went" to "didn't go","did not went" to "did not go")
 for((bad,good) in agreement)Regex("\\b"+Regex.escape(bad)+"\\b",RegexOption.IGNORE_CASE).findAll(text).forEach{val value=if(it.value.first().isUpperCase())good.replaceFirstChar{c->c.uppercase()}else good;add(it.range.first,it.range.last+1,"Subject and verb agreement",value)}
 Regex("(?i)\\b(a|an) ([a-z]+)\\b").findAll(text).forEach{m->val word=m.groupValues[2].lowercase(Locale.US);val article=m.groupValues[1];val vowel=when{word.startsWith("honest")||word.startsWith("honor")||word.startsWith("honour")||word.startsWith("hour")||word.startsWith("heir")->true;listOf("university","unicorn","uniform","unique","unilateral","unit","union","univers","utility","utensil","ubiquit","ewe","eulogy","euphem").any{word.startsWith(it)}||word.startsWith("use")||word.startsWith("user")||word.startsWith("one")||word.startsWith("euro")->false;else->word.first() in "aeiou"};if(m.groupValues[2].first().isLowerCase()&&word.length>1&&!word.startsWith("herb")&&word !in setOf("and","or","of","the","historic","herb")&&vowel!=(article.lowercase(Locale.US)=="an")){val wanted=if(vowel)"an"else"a";add(m.range.first,m.range.first+article.length,"Check a / an",if(article.first().isUpperCase())wanted.replaceFirstChar{it.uppercase()}else wanted)}}
 Regex(" +([,.;:!?])").findAll(text).forEach{add(it.range.first,it.range.last+1,"Space before punctuation",it.groupValues[1])}
 Regex("(?<=[A-Za-z])([,!?])(?=[A-Za-z])").findAll(text).forEach{add(it.range.first,it.range.last+1,"Space after punctuation",it.value+" ")}
 return issues.distinctBy{it.start to it.end}.sortedBy{it.start}.take(100)
}
/** Bundled SCOWL lexicon. No Android dictionary/service/network prerequisite. */
internal object WriteEnglish {
 private var cached:Map<String,Boolean>?=null
 @Synchronized private fun dictionary(c:Context):Map<String,Boolean>{cached?.let{return it};return c.assets.open("write/english.txt").bufferedReader().useLines{lines->lines.filter{it.isNotBlank()}.associate{it.removePrefix("!") to !it.startsWith("!")}}.also{cached=it}}
 private val common=mapOf("teh" to "the","recieve" to "receive","recieved" to "received","recieving" to "receiving","definately" to "definitely","seperate" to "separate","grammer" to "grammar","wierd" to "weird","occured" to "occurred","accomodate" to "accommodate","untill" to "until","adress" to "address","thier" to "their","becuase" to "because","alot" to "a lot","dont" to "don't","cant" to "can't","doesnt" to "doesn't","im" to "I'm","speling" to "spelling")
 fun contains(c:Context,word:String)=dictionary(c).containsKey(word.lowercase(Locale.US).replace('’','\''))
 fun check(c:Context,text:String,ignored:Set<String> = emptySet()):List<WriteIssue>{
  val words=dictionary(c);val issues=writeLocalChecks(text).toMutableList();val suggestions=mutableMapOf<String,List<String>>()
  val protected=Regex("https?://\\S+|www\\.\\S+|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]+|`[^`]*`",RegexOption.IGNORE_CASE).findAll(text).map{it.range}.toList()
  for(m in Regex("[A-Za-z]+(?:['’][A-Za-z]+)*").findAll(text)){
   if(issues.size>=100)break
   val original=m.value;val w=original.lowercase(Locale.US).replace('’','\'')
   if(w in ignored||original.length<2||original.length>32||original.all{it.isUpperCase()}||protected.any{m.range.first<=it.last&&m.range.last>=it.first})continue
   if(w !in common&&words.containsKey(w))continue
   // Do not flag pieces of identifiers, numbers or non-English words.
   if((m.range.first>0&&(text[m.range.first-1].isLetterOrDigit()||text[m.range.first-1]=='_'))||(m.range.last+1<text.length&&(text[m.range.last+1].isLetterOrDigit()||text[m.range.last+1]=='_')))continue
   val options=suggestions.getOrPut(w){
    val found=linkedSetOf<String>();common[w]?.let{found+=it}
    fun candidate(v:String){if(found.size<5&&words[v]==true)found+=v}
    for(i in w.indices){candidate(w.removeRange(i,i+1));if(i+1<w.length)candidate(w.substring(0,i)+w[i+1]+w[i]+w.substring(i+2));for(ch in 'a'..'z')candidate(w.substring(0,i)+ch+w.substring(i+1))}
    for(i in 0..w.length)for(ch in 'a'..'z')candidate(w.substring(0,i)+ch+w.substring(i))
    found.take(3)
   }.map{if(original.first().isUpperCase())it.replaceFirstChar{c->c.uppercase()}else it}
   issues+=WriteIssue(m.range.first,m.range.last+1,"Spelling",options.firstOrNull().orEmpty(),"spelling",options)
  }
  return issues.distinctBy{it.start to it.end}.sortedBy{it.start}.take(100)
 }
}
