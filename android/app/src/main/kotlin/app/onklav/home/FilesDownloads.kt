package app.onklav.home

import android.content.Context
import android.os.SystemClock
import android.webkit.MimeTypeMap
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal data class FileDownload(val url: String, val name: String, val mime: String, val bytes: Long)

/** Public HTTPS downloads use the signed Files broker. */
internal object FilesDownloads {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES).build()

    suspend fun save(
        context: Context, model: SdkAppModel, widget: String, request: FileDownload,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onFiling: () -> Unit = {},
        onSaved: (JSONObject) -> Unit = {},
    ) {
        val token = model.webDownloadSession()
        withContext(Dispatchers.IO) {
            require(request.bytes <= FilesBroker.MAX_FILE) { "Choose a download up to 256 MB" }
            val job = currentCoroutineContext()
            val check = { job.ensureActive(); model.checkWebDownload(token, widget) }
            val temp = File.createTempFile("file-download-", ".part", context.cacheDir)
            try {
                var url = SdkWebPolicy.external(request.url)
                var mime = request.mime
                var finished = false
                for (hop in 0..5) {
                    check()
                    val call = client.newCall(Request.Builder().url(url).build())
                    coroutineScope {
                        // Keep cancellation attached through the entire response body.
                        val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { call.cancel() }
                        }
                        try {
                            call.execute().use { response ->
                                if (response.code in 300..399) {
                                    check(hop < 5) { "This download redirected too many times" }
                                    val target = response.header("Location") ?: error("Download destination is missing")
                                    val next = response.request.url.resolve(target) ?: error("Download address is invalid")
                                    require(next.scheme == "https") { "Download redirects must use HTTPS" }
                                    url = SdkWebPolicy.external(next.toString())
                                } else {
                                    check(response.isSuccessful && response.code != 206) { "Download failed (HTTP ${response.code})" }
                                    val body = response.body ?: error("Download is empty")
                                    val total = body.contentLength()
                                    require(total <= FilesBroker.MAX_FILE) { "Choose a download up to 256 MB" }
                                    mime = body.contentType()?.toString()?.substringBefore(';') ?: mime
                                    onProgress(0, total)
                                    var updated = SystemClock.elapsedRealtime()
                                    body.byteStream().use { input ->
                                        temp.outputStream().use { output ->
                                            FilesBroker.copyBoundedLimit(input, output, FilesBroker.MAX_FILE, onProgress = { bytes ->
                                                val now = SystemClock.elapsedRealtime()
                                                if (now - updated >= 150) {
                                                    updated = now
                                                    onProgress(bytes, total)
                                                }
                                            }, authorize = check)
                                        }
                                    }
                                    val bytes = temp.length()
                                    check(total < 0 || bytes == total) { "Download ended early. Try again." }
                                    onProgress(bytes, total)
                                    finished = true
                                }
                            }
                        } finally {
                            cancellation.cancel()
                        }
                    }
                    if (finished) break
                }
                check(finished) { "Download did not complete" }
                AppSdk.staging.withLock {
                    check()
                    val staged = File(context.filesDir, "content-import.tmp")
                    try {
                        temp.inputStream().use { input ->
                            staged.outputStream().use { output -> FilesBroker.copyBounded(input, output, check) }
                        }
                        withContext(Dispatchers.Main) { onFiling() }
                        job.ensureActive()
                        val type = mime.takeUnless { it.isBlank() || it == "application/octet-stream" }
                            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(request.name.substringAfterLast('.', "").lowercase())
                            ?: "application/octet-stream"
                        // Once filing begins, retain its acknowledgement even if the UI closes.
                        withContext(NonCancellable) {
                            onSaved(model.saveWebFile(token, widget, FilesBroker.cleanName(request.name), type))
                        }
                    } finally { staged.delete() }
                }
            } finally { temp.delete() }
        }
    }
}
