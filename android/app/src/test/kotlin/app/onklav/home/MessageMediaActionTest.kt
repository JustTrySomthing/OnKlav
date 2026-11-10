package app.onklav.home
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
class MessageMediaActionTest {
 @Test fun actionColorsContrastLightDarkAndColoredBubbles(){
  for(scheme in listOf(lightColorScheme(),darkColorScheme()))for(color in listOf(Color(0xff25152f),Color(0xfff8f0ff),Color(0xff4c246d),Color(0xff235350),Color(0xffeac76d),scheme.surface)){
   val pair=messageActionColors(color,scheme);assertTrue(mediaContrast(color,pair.first)>=3f);assertTrue(mediaContrast(pair.first,pair.second)>=4.5f)
  }
 }
}
