package app.onklav.home

import app.onklav.shared.AppearanceSpec
import app.onklav.shared.appearanceColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import org.junit.Assert.*

class DialPadContrastTest {
 private fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return(maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
 @Test fun bothKeypadsHaveVisibleBoundariesAndReadableDigitsInEveryPalette(){
  for(dark in listOf(false,true))for(palette in 0..14){
   val scheme=appearanceColors(AppearanceSpec(dark=dark,palette=palette));val keys=dialPadColors(scheme)
   val label="dark=$dark palette=$palette"
   assertTrue("Key border against fill: $label",contrast(keys.outline,keys.fill)>=3f)
   assertTrue("Key border against dialog: $label",contrast(keys.outline,scheme.surface)>=3f)
   assertTrue("Key border against Phone: $label",contrast(keys.outline,scheme.background)>=3f)
   assertTrue("Digits: $label",contrast(keys.ink,keys.fill)>=4.5f)
   assertTrue("Letter labels: $label",contrast(keys.secondary,keys.fill)>=4.5f)
  }
 }
}
