package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w800dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResponsiveCardsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun similarPhoneWidthsKeepTwoColumnsAndVeryDifferentWidthsReflow() {
        for (scale in listOf(1f, 1.2f, 1.25f)) {
            for (width in listOf(280, 284, 300, 304, 332, 336, 392, 396)) {
                assertEquals("width=$width scale=$scale", 2, responsiveColumns(width.dp, scale, 4, 12.dp))
            }
        }
        assertEquals(1, responsiveColumns(220.dp, 1f, 4, 12.dp))
        assertEquals(1, responsiveColumns(280.dp, 2f, 4, 12.dp))
        assertEquals(4, responsiveColumns(800.dp, 1f, 4, 12.dp))
    }

    @Test fun toolCardsShareRowHeightWhenTheirTextWraps() {
        val width = mutableStateOf(284.dp)
        val bounds = mutableMapOf<String, Rect>()
        val first = "Files & folders"
        val second = "Video"
        compose.runOnUiThread {
            compose.activity.setContent {
                OnKlavTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1.25f)) {
                        Box(Modifier.width(width.value)) {
                            AlignedCardGrid(listOf(first, second)) { label, modifier ->
                                FileTypeCard(
                                    label,
                                    if (label == first) "A longer description that needs several lines of text" else "MP4",
                                    app.onklav.shared.R.drawable.ref_app_files,
                                    modifier.onGloballyPositioned { bounds[label] = it.boundsInRoot() },
                                ) {}
                            }
                        }
                    }
                }
            }
        }
        for (size in listOf(284.dp, 288.dp, 320.dp)) {
            compose.runOnIdle { width.value = size }
            compose.waitForIdle()
            compose.runOnIdle {
                val left = bounds.getValue(first)
                val right = bounds.getValue(second)
                assertTrue(right.left >= left.right)
                assertEquals(left.top, right.top, 1f)
                assertEquals(left.bottom, right.bottom, 1f)
                assertEquals(left.width, right.width, 1f)
            }
        }
        compose.runOnIdle { width.value = 220.dp }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(bounds.getValue(second).top > bounds.getValue(first).bottom) }
    }
}
