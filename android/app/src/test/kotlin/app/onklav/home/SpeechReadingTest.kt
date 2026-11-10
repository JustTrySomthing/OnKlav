package app.onklav.home
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SpeechReadingTest {
 @Test fun titlesExpandWithoutTurningTheirDotsIntoSentenceBreaks(){
  assertEquals("Mister Holmes met Doctor Watson. Missus Hudson thanked Professor James.",SpeechReading.prepare("Mr. Holmes met Dr. Watson. Mrs. Hudson thanked Prof. James.").text)
  assertEquals("Mister holmes",SpeechReading.prepare("MR. HOLMES").text)
  assertEquals("Mister Holmes",SpeechReading.prepare("Mr.\u00a0Holmes").text)
  assertEquals("Miz Marple and Reverend Smith",SpeechReading.prepare("Ms. Marple and Rev. Smith").text)
  assertEquals("St. Louis; Main Dr.; 3.14",SpeechReading.prepare("St. Louis; Main Dr.; 3.14").text)
 }
 @Test fun onlyStandaloneStructuralMarkersAreSilent(){
  val input="I.\nII>\nCHAPTER IV\nThe Adventure\n***\nHenry VIII met Charles II.\nI\nam here.\n12\nWorld War II ended."
  val text=SpeechReading.prepare(input).text
  assertEquals("\n\n\nThe Adventure\n\nHenry VIII met Charles II.\nI\nam here.\n12\nWorld War II ended.",text)
  assertEquals("mix things well.",SpeechReading.prepare("mix things well.").text)
 }
 @Test fun emojiSequencesAreSilentButNumbersAndMeaningfulSymbolsRemain(){
  val raw="Hello 😀👩🏽‍💻👨‍👩‍👧‍👦🇺🇸1️⃣❤️ there. 1 + 2 = 3; 50%; © 2026; A * B #tag!"
  val text=SpeechReading.prepare(raw).text
  assertEquals("Hello there. 1 + 2 = 3; 50%; © 2026; A * B #tag!",text)
  assertEquals("I am very satisfied.",SpeechReading.prepare("I am very satisfied.").text)
  assertEquals("Really‼",SpeechReading.prepare("Really‼").text)
  assertEquals("Word Copyright mark",SpeechReading.prepare("Word Copyright ©️ mark").text)
 }
 @Test fun commonInitialsNumbersAndInvisibleBookCharactersHaveConservativeRules(){
  assertEquals("J R R Tolkien wrote number 12. No. I disagree.",SpeechReading.prepare("J. R. R. Tolkien wrote No. 12. No. I disagree.").text)
  assertEquals("An example word and two words. Good work",SpeechReading.prepare("An exam\u00adple word\u2060 and two\u200bwords. Good work :-)").text.trim())
  assertEquals("I. Think carefully.",SpeechReading.prepare("I. Think carefully.").text)
 }
 @Test fun expandedAndRemovedTextMapsBackToOriginalUtf16Positions(){
  val raw="😀 Mr. Holmes met Dr. Watson.";val reading=SpeechReading.prepare(raw)
  for(word in listOf("Holmes","Watson"))assertEquals(raw.indexOf(word),reading.original(reading.text.indexOf(word)))
  assertEquals(raw.indexOf("Mr."),reading.original(reading.text.indexOf("Mister")))
  assertEquals(raw.length,reading.original(reading.text.length))
  var last=0;for(i in reading.text.indices){val original=reading.original(i);assertTrue(original>=last);assertEquals(original,SpeechPlan.boundary(raw,original));last=original}
 }
 @Test(timeout=10000) fun approvedProseAndExpandedMaximumRequestsRemainBounded(){
  val approved="Friends fell out often because life was changing so fast. The easiest thing in the world was to lose touch with someone."
  assertEquals(approved,SpeechReading.prepare(approved).text)
  val raw="Lt. Smith ".repeat(5000);val reading=SpeechReading.prepare(raw);assertTrue(reading.text.length>50000);assertTrue(reading.text.length<=SpeechReading.MAX_SPOKEN_LENGTH)
  assertEquals(raw.length,reading.original(reading.text.length));assertTrue(SpeechPieces.plan(reading.text).all{it.end-it.start<=220})
  assertEquals("q. ".repeat(10000),SpeechReading.prepare("Q. ".repeat(10000)).text)
 }
 @Test fun markerCleanupPreservesStandaloneUnitsInitialsAndRomanLookingWords(){
  val raw="I\nV\nX\nL\nC\nM\nMIX\nMIX.\n12 V\n20 °C\nII.\nXX>\nChapter C"
  assertEquals("I\nV\nX\nL\nC\nM\nMIX\nMIX.\n12 V\n20 °C\n\n\n",SpeechReading.prepare(raw).text)
 }
 @Test fun abbreviationAndInitialRulesNeverJoinSeparateLines(){
  val raw="Mr.\nHolmes\nNo.\n12\nJ.\nR.\nTolkien"
  assertEquals(raw,SpeechReading.prepare(raw).text)
  assertEquals("Mister Holmes",SpeechReading.prepare("Mr.\u00a0Holmes").text)
  assertEquals("J R R Tolkien",SpeechReading.prepare("J. R. R. Tolkien").text)
 }
 private class Fake:SpeechBackend {
  var text="";var starts=0;var range:((Int)->Unit)?=null;var done:((String?)->Unit)?=null
  override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit)=done(true,listOf(SpeechVoice("test","Test")))
  override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean{starts++;this.text=text;this.range=range;this.done=done;return true}
  override fun stop(){};override fun close(){}
 }
 @Test fun allSpeechClientsReceiveCleanupAndCallbacksStillUseOriginalText(){
  val c=ApplicationProvider.getApplicationContext<Context>();shadowOf(c.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
  val fake=Fake();val controller=SdkSpeechController(c,"test.sdk.developer",{},fake)
  val raw="Mr. Holmes 😀 met Dr. Watson.";controller.play(raw,"speech")
  assertEquals("Mister Holmes met Doctor Watson.",fake.text);fake.range!!(fake.text.indexOf("Watson"));assertEquals(raw.indexOf("Watson"),controller.position)
  controller.close()
 }
 @Test fun emojiAndMarkerOnlyMessagesCompleteSilentlyWithoutAPlaybackError(){
  val c=ApplicationProvider.getApplicationContext<Context>();shadowOf(c.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
  val fake=Fake();val controller=SdkSpeechController(c,"test.silent",{},fake);controller.play("😀\nII.\n***","speech")
  assertEquals(0,fake.starts);assertFalse(controller.playing);assertNull(controller.error);assertEquals(1,controller.completed);controller.close()
 }
}
