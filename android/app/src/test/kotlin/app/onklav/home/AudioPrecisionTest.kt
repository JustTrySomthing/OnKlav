package app.onklav.home

import org.junit.Assert.*
import org.junit.Test

class AudioPrecisionTest {
    @Test fun invalidNumbersNeverBecomeEdits(){
        for(text in listOf(""," ","NaN","Infinity","1e2","1,2.3","12.001","-12.001","9999999999999999999999999999999999999999999999999"))assertNull(text,audioExactValue(text,-12f..12f))
        assertNull(audioExactValue("1",0f..1f,0f))
    }
    @Test fun displayedUnitsRoundTripWithoutChangingTheirMeaning(){
        assertEquals(-3.25f,audioExactValue(" −3,25 ",-12f..12f)!!,0f)
        assertEquals(1.25f,audioExactValue("125",.5f..2f,100f)!!,0f)
        assertEquals(.333f,audioExactValue("33.3",0f..1f,100f)!!,.000001f)
        assertEquals(-80f,audioExactValue("-80",-80f..-20f)!!,0f)
        assertNull(audioExactValue("49.9",.5f..2f,100f))
        assertNull(audioExactValue("100.01",0f..1f,100f))
    }
    @Test fun decimalPrefillIsLocaleStableAndIncludesFineAdjustments(){
        val before=java.util.Locale.getDefault()
        try{
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("125",audioExactText(1.25f,100f))
            assertEquals("-3.25",audioExactText(-3.25f))
            assertEquals("0.125",audioExactText(.125f))
            assertEquals("0",audioExactText(-0f))
        }finally{java.util.Locale.setDefault(before)}
    }
}
