package app.onklav.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** Native-style thin seek bar with a full-size touch target. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSlider(
    value: Float,
    change: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .25f else .1f)
    val fill = accent.copy(alpha = if (enabled) 1f else .38f)
    Slider(
        value,
        change,
        modifier.heightIn(min = 48.dp),
        enabled = enabled,
        valueRange = range,
        steps = steps,
        thumb = {
            Box(Modifier.size(32.dp, 48.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.dp).background(fill, CircleShape))
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val fraction =
                    ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val y = size.height / 2
                drawLine(track, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                drawLine(
                    fill,
                    Offset(0f, y),
                    Offset(size.width * fraction, y),
                    size.height,
                    StrokeCap.Round,
                )
            }
        },
    )
}

/** Visual switch only; its containing 52 dp row owns the toggle and accessibility state. */
@Composable
fun AppearanceSwitch(checked: Boolean, enabled: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(34.dp, 24.dp)) {
        val unit = size.height / 24f
        val opacity = if (enabled) 1f else .38f
        drawRoundRect(
            (if (checked) accent else muted).copy(alpha = .3f * opacity),
            topLeft = Offset(2 * unit, 5 * unit),
            size = androidx.compose.ui.geometry.Size(30 * unit, 14 * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7 * unit, 7 * unit),
        )
        drawCircle(
            (if (checked) accent else muted).copy(alpha = opacity),
            10 * unit,
            center = Offset((if (checked) 24f else 10f) * unit, 12 * unit),
        )
    }
}
