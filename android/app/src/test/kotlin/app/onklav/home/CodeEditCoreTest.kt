package app.onklav.home
import org.junit.Test
import org.junit.Assert.*
class CodeEditCoreTest{
 @Test fun encodingsAndLineEndingsRoundTrip(){for(encoding in listOf("UTF-8","UTF-16LE","UTF-16BE"))for(ending in listOf("LF","CRLF","CR")){val bytes=CodeText.encode("fn café() {\n  println!(\"Hello 🌍\");\n}",encoding,true,ending);val d=CodeText.decode(bytes);assertEquals(encoding,d.encoding);assertEquals(ending,d.ending);assertArrayEquals(bytes,CodeText.encode(d.text,d.encoding,d.bom,d.ending))}}
 @Test fun binaryMalformedAndOversizedFilesAreRejected(){assertTrue(runCatching{CodeText.decode(byteArrayOf(0,1,2))}.isFailure);assertTrue(runCatching{CodeText.decode(byteArrayOf(-1,0,4))}.isFailure);assertTrue(runCatching{CodeText.decode(ByteArray(1_000_001))}.isFailure)}
 @Test fun detectsCommonSourceFormats(){for((ext,lang)in listOf("rs" to "Rust","kt" to "Kotlin","gd" to "GDScript","php" to "PHP","html" to "HTML","css" to "CSS","js" to "JavaScript","go" to "Go","py" to "Python","xml" to "XML","json" to "JSON"))assertEquals(lang,CodeText.language("test.$ext"))}
 @Test fun multilineIndentAndOutdentRespectTrailingLine(){val text="one\ntwo\nthree";val e=CodeText.indent(text,0,8,"  ");assertEquals("  one\n  two\nthree",e.text);assertEquals(text,CodeText.indent(e.text,e.start,e.end,"  ",true).text)}
 @Test fun commentsToggleWithoutChangingIndent(){val t="  let x = 1;\n  run();";val e=CodeText.comment(t,0,t.length,"Rust");assertTrue(e.text.startsWith("  // let"));assertEquals(t,CodeText.comment(e.text,e.start,e.end,"Rust").text)}
 @Test fun literalSearchWrapsAndReplacementIsBounded(){assertEquals(0,CodeText.find("One two ONE","one",11));assertEquals(8,CodeText.find("One two ONE","one",0,true));assertEquals("x two x",CodeText.replaceAll("One two ONE","one","x",false));assertTrue(runCatching{CodeText.replaceAll("a".repeat(10000),"a","x".repeat(10000),true)}.isFailure)}
 @Test fun lexerKeepsMultilineStateAndRecognizesPhpComments(){val t="/* multiline\n comment */\nfn x() { let s = \"/* string */\"; }";val tokens=CodeText.tokens(t,"Rust",10,t.length);assertTrue(tokens.any{it.kind==1&&it.start==0});assertTrue(tokens.any{it.kind==2});assertTrue(tokens.any{it.kind==4});assertEquals(1,CodeText.tokens("// comment","PHP").single().kind)}
 @Test fun jsonValidationIsStrict(){CodeText.validateJson("{\"x\":[true,null,-1.2e3]}");for(t in listOf("{x:1}","{\"x\":1,}","[1,]","01","true trailing","\"bad\nstring\""))assertTrue(t,runCatching{CodeText.validateJson(t)}.isFailure)}
 @Test fun replacementsAndSelectionsStayInsideDocument(){val e=CodeText.insert("abcdef",5,2,"X");assertEquals("abXf",e.text);assertEquals(3,e.start);assertEquals(0,CodeText.indent("",0,0,"  ",true).start)}
}
