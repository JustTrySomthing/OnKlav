package app.onklav.home

import android.app.Activity
import androidx.core.graphics.ColorUtils
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class WritePopupContrastTest {
 // These are indexes into the explicit attribute array below, not generated R.styleable IDs.
 @android.annotation.SuppressLint("ResourceType")
 @Test fun spellingPopupActionsRemainReadableOnTheLightPaper(){
  val activity=Robolectric.buildActivity(Activity::class.java).get();activity.setTheme(R.style.OnKlavTheme)
  val editor=WriteNativeEditor(activity)
  val attrs=editor.context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent,android.R.attr.textColorPrimary,android.R.attr.colorBackgroundFloating))
  try{val background=attrs.getColor(2,0);assertTrue(ColorUtils.calculateContrast(attrs.getColor(0,0),background)>=4.5);assertTrue(ColorUtils.calculateContrast(attrs.getColor(1,0),background)>=7.0)}finally{attrs.recycle()}
  assertTrue(editor.inputType and android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT!=0)
  assertEquals("Lossless ZIP",CompressKind.FILES.hint)
 }
}
