package app.onklav.home

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Shared by Phone and Messages: visible key edges even when dialog/surface colors coincide. */
internal data class DialPadColors(val fill:Color,val outline:Color,val ink:Color,val secondary:Color)
internal fun dialPadColors(scheme:ColorScheme):DialPadColors {
 val fill=lerp(scheme.surface,scheme.primary,.06f)
 val a=scheme.primary.luminance();val b=fill.luminance()
 val contrast=(maxOf(a,b)+.05f)/(minOf(a,b)+.05f)
 return DialPadColors(fill,scheme.onSurface.copy(alpha=.60f).compositeOver(fill),if(contrast>=4.5f)scheme.primary else scheme.onSurface,scheme.onSurfaceVariant)
}
