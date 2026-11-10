package app.onklav.home
import app.onklav.shared.*
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test
class PhoneNumbersTest {
    @Test fun formatsNationalCountryCodeShortCodesAndInternationalWithoutLosingDigits(){
        assertEquals("(615) 555-0101",displayPhone("6155550101"))
        assertEquals("+1 (615) 555-0101",displayPhone("+16155550101"))
        assertEquals("35127",displayPhone("35127"))
        assertEquals("+442071838750",displayPhone("+442071838750"))
        assertEquals("+16155550101",phoneInput("+1 (615) 555-0101"))
        assertEquals("",phoneInput("letters"))
    }
    @Test fun partialInputFormatsWhilePreservingDialableNumbers(){
        assertEquals("(6",displayPhone("6",partial=true))
        assertEquals("(615)",displayPhone("615",partial=true))
        assertEquals("(615) 55",displayPhone("61555",partial=true))
        assertEquals("+1 (615) 555-01",displayPhone("+161555501",partial=true))
        assertEquals("+442071838750",displayPhone("+442071838750",partial=true))
    }
    @Test fun cursorMapsAcrossEveryPartialInputAndFormattedPaste(){
        for(raw in listOf("","+","6","615555","6155550101","+16155550101","16155550101","+1 (615) 555-0101","35127","+442071838750")){
            val transformed=PhoneVisualTransformation.filter(AnnotatedString(raw))
            val forward=(0..raw.length).map{transformed.offsetMapping.originalToTransformed(it)}
            val reverse=(0..transformed.text.length).map{transformed.offsetMapping.transformedToOriginal(it)}
            assertTrue(forward.all{it in 0..transformed.text.length});assertTrue(reverse.all{it in 0..raw.length})
            assertEquals(forward.sorted(),forward);assertEquals(reverse.sorted(),reverse)
            assertEquals(transformed.text.length,forward.last())
        }
    }
    @Test fun contactRowsHaveAlphabetInitialsAndNoInventedNumbers(){
        val row=ContactRow(1,"Alex Example",null,emptyList())
        assertEquals("A",row.letter);assertEquals("AE",row.initials);assertTrue(row.numbers.isEmpty())
        assertEquals("#",ContactRow(2,"123 Support",null,listOf("35127")).letter)
    }
}
