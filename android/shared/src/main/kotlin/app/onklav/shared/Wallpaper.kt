package app.onklav.shared

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit

private val backgroundDecoders=kotlinx.coroutines.sync.Semaphore(2)

@Composable
fun OnKlavBackground(
    modifier: Modifier = Modifier,
    spec: AppearanceSpec = LocalAppearanceSpec.current,
    edge: Int = 1400,
) {
    val context = LocalContext.current
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, spec.background, edge) {
            value = null
            value =
                withContext(Dispatchers.IO) {
                    backgroundDecoders.withPermit {
                        ensureActive()
                        try { AppearanceStore(context).bitmap(spec.background, edge) }
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){null}
                        catch(_:OutOfMemoryError){null}
                    }
                }
        }
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                null,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = (if (spec.dark) spec.overlayDark else spec.overlayLight) / 100f
                        )
                    )
            )
        }
    }
}

@Composable
fun MessagePreview(
    spec: AppearanceSpec,
    compact: Boolean = false,
    backgroundPicker: Boolean = false,
) {
    OnKlavTheme(LocalAppearance.current, preview = spec) {
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = if (compact) 115.dp else if (backgroundPicker) 190.dp else 170.dp)
                .testTag(if (compact) "compact-message-preview" else "appearance-message-preview")
                .clip(RoundedCornerShape(14.dp))
        ) {
            OnKlavBackground(Modifier.matchParentSize(), spec, if (compact) 360 else 800)
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = if (backgroundPicker) 20.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (backgroundPicker) 14.dp else 12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        if (backgroundPicker) "A little more you." else "How does this look?",
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        fontSize = if (compact) 14.sp else spec.size.sp,
                        lineHeight = (if (compact) 17f else spec.size * 1.35f).sp,
                        letterSpacing = 0.sp,
                    )
                }
                Box(
                    Modifier.align(Alignment.End)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sentBrush(spec))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        if (backgroundPicker) "Make yourself at home."
                        else "Just the way I like it.",
                        color = sentInk(spec),
                        fontSize = if (compact) 14.sp else spec.size.sp,
                        lineHeight = (if (compact) 17f else spec.size * 1.35f).sp,
                        letterSpacing = 0.sp,
                    )
                }
            }
        }
    }
}
