package app.onklav.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun YoutubeHeaderMark(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Icon(painterResource(app.onklav.shared.R.drawable.ref_app_youtube), "YouTube",
            Modifier.size(32.dp).background(Color(0xff252525), RoundedCornerShape(8.dp)).padding(2.dp),
            tint = Color.White)
    }
}

@Composable
internal fun YoutubePreviewAction(enabled: Boolean, toggle: () -> Unit) {
    IconToggleButton(checked = enabled, onCheckedChange = { toggle() }) {
        Icon(
            painterResource(if (enabled) R.drawable.youtube_preview_on else R.drawable.youtube_preview_off),
            "Video previews",
            Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .68f),
        )
    }
}
