package app.onklav.home

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.onklav.shared.DialogAction
import app.onklav.shared.ReferenceDialog
import kotlinx.coroutines.*

internal class BrowserDownloads(
    private val context: Context,
    private val model: SdkAppModel,
    private val scope: CoroutineScope,
) {
    enum class Status { Queued, Downloading, Cancelling, Saving, Complete, Failed, Cancelled }
    class Entry(val widget: String, val request: FileDownload) {
        var status by mutableStateOf(Status.Queued)
        var name by mutableStateOf(request.name)
        var progress by mutableStateOf(0L to request.bytes)
        var error by mutableStateOf("")
        val canRetry get() = status == Status.Failed || status == Status.Cancelled
        val canCancel get() = status == Status.Queued || status == Status.Downloading
        val active get() = canCancel || status == Status.Saving || status == Status.Cancelling
    }

    private val rows = mutableStateListOf<Entry>()
    private val jobs = mutableMapOf<Entry, Job>()
    val entries: List<Entry> get() = rows
    val active get() = rows.any { it.active }

    fun enqueue(widget: String, request: FileDownload) {
        require(rows.count { it.active } < 12) { "Wait for a download to finish or cancel one in Downloads." }
        if (rows.size >= 40) rows.lastOrNull { !it.active }?.let { rows.remove(it) }
        rows.add(0, Entry(widget, request))
        startNext()
    }

    fun retry(entry: Entry) {
        if (!entry.canRetry || jobs.containsKey(entry) || rows.count { it.active } >= 12) return
        entry.error = ""
        entry.progress = 0L to entry.request.bytes
        entry.status = Status.Queued
        startNext()
    }

    fun cancel(entry: Entry) {
        if (!entry.canCancel) return
        val job = jobs[entry]
        entry.status = if (job == null) Status.Cancelled else Status.Cancelling
        job?.cancel()
    }

    fun cancelTransfers() {
        rows.filter { it.canCancel }.forEach(::cancel)
    }

    fun clearFinished() {
        rows.removeAll { it.status == Status.Complete || (it.status == Status.Cancelled && it !in jobs) }
    }

    private fun startNext() {
        while (jobs.size < 3) {
            val entry = rows.lastOrNull { it.status == Status.Queued } ?: return
            entry.status = Status.Downloading
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    FilesDownloads.save(context, model, entry.widget, entry.request,
                        onProgress = { bytes, total -> entry.progress = bytes to total },
                        onFiling = { entry.status = Status.Saving },
                        onSaved = { result ->
                            entry.name = result.optString("name", entry.name)
                            entry.status = Status.Complete
                        },
                    )
                } catch (e: CancellationException) {
                    if (entry.status != Status.Complete) entry.status = Status.Cancelled
                    throw e
                } catch (e: Exception) {
                    if (entry.status == Status.Cancelling) entry.status = Status.Cancelled
                    else {
                        entry.error = if (e is java.io.IOException) "Connection or storage interrupted. Try again."
                        else e.message ?: "Download could not be saved."
                        entry.status = Status.Failed
                    }
                }
            }
            jobs[entry] = job
            job.invokeOnCompletion {
                scope.launch {
                    if (entry.status == Status.Cancelling) entry.status = Status.Cancelled
                    jobs.remove(entry)
                    startNext()
                }
            }
            job.start()
        }
    }
}

@Composable
internal fun BrowserDownloadsAction(active: Boolean, onClick: () -> Unit) {
    val offset = if (active) {
        val pulse = rememberInfiniteTransition(label = "Download activity")
        val position by pulse.animateFloat(0f, -3f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "Download arrow")
        position
    } else 0f
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.browser_download_arrow),
            if (active) "Downloads in progress" else "Downloads",
            Modifier.size(24.dp).graphicsLayer { translationY = offset.dp.toPx() },
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun BrowserDownloadsDialog(downloads: BrowserDownloads, dismiss: () -> Unit) {
    val context = LocalContext.current
    ReferenceDialog("Downloads", dismiss, large = true, footer = {
        if (downloads.entries.any { it.status == BrowserDownloads.Status.Complete || it.status == BrowserDownloads.Status.Cancelled }) {
            TextButton(onClick = downloads::clearFinished) { Text("Clear finished") }
        }
        DialogAction("Done", onClick = dismiss)
    }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saved to Files › Downloads", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            if (downloads.entries.isEmpty()) {
                Text("No browser downloads yet", Modifier.padding(vertical = 24.dp))
            }
            for (entry in downloads.entries) key(entry) {
                val (bytes, total) = entry.progress
                val fraction = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
                val received = Formatter.formatShortFileSize(context, bytes)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        if (entry.status == BrowserDownloads.Status.Downloading) {
                            if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val detail = when (entry.status) {
                                BrowserDownloads.Status.Queued -> "Queued"
                                BrowserDownloads.Status.Downloading -> if (fraction == null) "$received downloaded"
                                    else "$received of ${Formatter.formatShortFileSize(context, total)} · ${(fraction * 100).toInt()}%"
                                BrowserDownloads.Status.Cancelling -> "Cancelling…"
                                BrowserDownloads.Status.Saving -> "Saving to Downloads…"
                                BrowserDownloads.Status.Complete -> "Complete · $received"
                                BrowserDownloads.Status.Cancelled -> "Cancelled"
                                BrowserDownloads.Status.Failed -> entry.error
                            }
                            Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = if (entry.status == BrowserDownloads.Status.Failed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.canCancel) TextButton(onClick = { downloads.cancel(entry) }) { Text("Cancel") }
                            if (entry.canRetry) TextButton(onClick = { downloads.retry(entry) }) { Text("Retry") }
                        }
                    }
                }
            }
        }
    }
}
