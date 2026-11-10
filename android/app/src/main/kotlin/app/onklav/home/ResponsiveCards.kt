package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep the phone layout until two usable cells no longer fit. Extra columns need more room. */
internal fun responsiveColumns(width: Dp, fontScale: Float, maxColumns: Int, gap: Dp, minimum: Dp = 118.dp): Int {
    val textGrowth = 64.dp * (fontScale.coerceAtLeast(1f) - 1f)
    if (maxColumns == 1 || width < (minimum + textGrowth) * 2 + gap) return 1
    for (columns in maxColumns downTo 3) {
        if (width >= (184.dp + textGrowth) * columns + gap * (columns - 1)) return columns
    }
    return 2
}

@Composable
internal fun <T> AlignedCardGrid(
    items: List<T>,
    maxColumns: Int = 2,
    gap: Dp = 12.dp,
    card: @Composable (T, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fit = responsiveColumns(maxWidth, LocalDensity.current.fontScale, maxColumns.coerceAtMost(items.size).coerceAtLeast(1), gap)
        val columns = if (items.size == 4 && fit == 3) 2 else fit
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { item ->
                        key(item) { card(item, Modifier.weight(1f).fillMaxHeight()) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun FileTypeCard(title: String, detail: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 136.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, minLines = 2)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
