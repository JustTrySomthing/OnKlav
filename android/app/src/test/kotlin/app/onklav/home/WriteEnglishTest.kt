package app.onklav.home
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class WriteEnglishTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 @Test fun bundledDictionaryChecksInflectionsAndCommonMisspellingsWithoutAndroidService(){
  for(w in listOf("writing","documents","beautiful","children","running","can't","isn't","polished"))assertTrue(w,WriteEnglish.contains(c,w))
  val text="The speling in teh document is definately wierd."
  val issues=WriteEnglish.check(c,text)
  for((bad,good) in listOf("speling" to "spelling","teh" to "the","definately" to "definitely","wierd" to "weird"))assertTrue(bad,issues.any{text.substring(it.start,it.end)==bad&&good in it.suggestions})
  assertTrue(WriteEnglish.check(c,"The polished document is ready.").isEmpty())
 }
 @Test fun englishGrammarRetainsOffsetsAndLeavesUrlsCodeAndValidArticlesAlone(){
  val text="i is ready. They was here. She have a apple and an book. We should of waited."
  val issues=writeLocalChecks(text)
  assertTrue(issues.any{it.replacement=="I am"});assertTrue(issues.any{it.replacement=="They were"});assertTrue(issues.any{it.replacement=="She has"});assertTrue(issues.any{it.replacement=="an"});assertTrue(issues.any{it.replacement=="a"});assertTrue(issues.any{it.replacement=="should have"})
  assertTrue(issues.all{it.start>=0&&it.end<=text.length&&it.start<it.end})
  assertTrue(writeLocalChecks("A useful tool and an honest person visited a university for an hour. It is an honor to join a union with a unit of work and an herbal tea.").isEmpty())
  assertTrue(WriteEnglish.check(c,"Visit https://example.com/missspelled and `teh_code`.").isEmpty())
  assertTrue(WriteEnglish.check(c,"Filigreeword",setOf("filigreeword")).isEmpty())
 }
 @Test fun suggestionsNeverBecomeFormattingOrSilentlyRewriteDocument(){
  val text=android.text.SpannableStringBuilder("A speling issue")
  val before=WriteDocument.capture("","",text)
  val issue=WriteEnglish.check(c,text.toString()).first{it.kind=="spelling"}
  text.setSpan(android.text.style.SuggestionSpan(c,java.util.Locale.US,issue.suggestions.toTypedArray(),android.text.style.SuggestionSpan.FLAG_MISSPELLED,null),issue.start,issue.end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
  val after=WriteDocument.capture("","",text)
  assertEquals(before.text,after.text);assertEquals(before.marks,after.marks)
 }
}
