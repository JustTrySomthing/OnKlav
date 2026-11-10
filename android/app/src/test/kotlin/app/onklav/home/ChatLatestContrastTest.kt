package app.onklav.home

import app.onklav.shared.AppearanceSpec
import app.onklav.shared.appearanceColors
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import org.junit.Assert.*

class ChatLatestContrastTest {
 @Test fun latestArrowHasStrongContrastForEveryLightAndDarkPalette(){
  for(dark in listOf(false,true))for(palette in 0..14){
   val scheme=appearanceColors(AppearanceSpec(dark=dark,palette=palette));val c=chatLatestColors(scheme.primary)
   val a=c.fill.luminance();val b=c.ink.luminance();val contrast=(maxOf(a,b)+.05f)/(minOf(a,b)+.05f)
   assertTrue("dark=$dark palette=$palette",contrast>=4.5f)
  }
 }
}
