package app.onklav.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest

/** Keep the tail visible across insertions and keyboard/composer resizing, while respecting history browsing. */
@Composable internal fun MessageFeedAutoScroll(list: LazyListState, thread: Long, sent: Int, enabled: Boolean, preservePosition: Boolean = false) {
    LaunchedEffect(list, thread, sent, enabled) {
        if (!enabled) return@LaunchedEffect
        var follow = !preservePosition || !list.canScrollForward
        var count = -1
        var viewport = -1
        var tail = ""
        snapshotFlow {
            val layout = list.layoutInfo
            FeedLayout(layout.totalItemsCount, layout.viewportEndOffset - layout.viewportStartOffset,
                layout.visibleItemsInfo.lastOrNull()?.let { "${it.key}:${it.size}" }.orEmpty(),
                list.canScrollForward, list.isScrollInProgress)
        }.collectLatest { frame ->
            val resized = frame.count != count || frame.viewport != viewport || frame.tail != tail
            count = frame.count; viewport = frame.viewport; tail = frame.tail
            if (follow && resized && frame.count > 0 && !frame.scrolling) {
                list.scrollToItem(frame.count - 1, Int.MAX_VALUE)
            } else if (!resized || frame.scrolling) {
                follow = !frame.forward
            }
        }
    }
}

private data class FeedLayout(val count: Int, val viewport: Int, val tail: String, val forward: Boolean, val scrolling: Boolean)
