package app.onklav.home

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MessageMedia {
    fun name(c: Context, u: Uri): String =
        c.contentResolver
            .query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?.take(120) ?: "Attachment"

    fun import(c: Context, u: Uri, budget: Int): JSONObject {
        val mime =
            app.onklav.shared.Mp3Codec.mime(c.contentResolver.getType(u)?.substringBefore(';')?.lowercase() ?: "application/octet-stream",name(c,u)).let {
                if (it == "text/vcard") "text/x-vcard" else it
            }
        require(budget > 0) { "This message is full. Remove an attachment or shorten its text." }
        val bytes =
            c.contentResolver.openInputStream(u)?.use {
                MessageRepository.readBounded(it, if (mime.startsWith("image/") && mime != "image/gif") 30 * 1024 * 1024 else budget)
            } ?: error("Could not open the attachment")
        val normalized =
            if (mime.startsWith("image/") && mime != "image/gif") image(bytes, budget) else bytes
        require(normalized.size <= budget) {
            "Choose a smaller attachment. This file exceeds your carrier's MMS limit."
        }
        val type = if (mime.startsWith("image/") && mime != "image/gif") "image/jpeg" else mime
        require(
            type in
                setOf(
                    "image/jpeg",
                    "image/png",
                    "image/gif",
                    "video/mp4",
                    "video/3gpp",
                    "audio/amr",
                    "audio/3gpp",
                    "audio/mpeg",
                    "audio/mp4",
                    "audio/ogg",
                    "text/x-vcard",
                )
        ) {
            "Choose a photo, video or audio clip. Use Share via Cloud Files for other files."
        }
        val id = MessageTransferProvider.id()
        val target = MessageTransferProvider.file(c,id)
        try {
            target.outputStream().use {
                it.write(normalized)
                it.fd.sync()
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return JSONObject()
            .put("file", id)
            .put("mime", type)
            .put("name", if (type == "image/jpeg") name(c,u).substringBeforeLast('.')+".jpg" else name(c,u))
            .put("bytes", normalized.size)
    }

    private fun image(bytes: ByteArray, budget: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(
            bounds.outWidth > 0 &&
                bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000
        ) {
            "Choose a still image up to 100 megapixels"
        }
        val decoded =
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
                ) { d, info, _ ->
                    val scale = minOf(1f, 1280f / maxOf(info.size.width, info.size.height))
                    d.setTargetSize(
                        maxOf(1, (info.size.width * scale).toInt()),
                        maxOf(1, (info.size.height * scale).toInt()),
                    )
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
        var bitmap = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        decoded.recycle()
        try {
            while (true) {
                for (q in listOf(85, 75, 65, 55, 45)) {
                    val out = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, q, out))
                    if (out.size() <= budget) return out.toByteArray()
                }
                require(maxOf(bitmap.width, bitmap.height) > 160) {
                    "This photo cannot fit your carrier's MMS limit"
                }
                val smaller =
                    Bitmap.createScaledBitmap(
                        bitmap,
                        maxOf(1, bitmap.width * 3 / 4),
                        maxOf(1, bitmap.height * 3 / 4),
                        true,
                    )
                bitmap.recycle()
                bitmap = smaller
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun part(c: Context, j: JSONObject) =
        MessagePart(
            MessageTransferProvider.uri(
                    c,
                    j.getString("file"),
                    "blob",
                    j.getString("mime"),
                    j.optString("name", "Attachment"),
                )
                .toString(),
            j.getString("mime"),
            j.optString("name", "Attachment"),
        )

    fun grantable(c: Context, p: MessagePart): Uri {
        val u = Uri.parse(p.uri)
        if (u.authority == c.packageName + ".message-files") return u
        val id = MessageTransferProvider.id()
        c.contentResolver.openInputStream(u)?.use { input ->
            MessageTransferProvider.file(c, id).outputStream().use { out ->
                out.write(MessageRepository.readBounded(input, 20 * 1024 * 1024))
                out.fd.sync()
            }
        } ?: error("Could not open the attachment")
        return MessageTransferProvider.uri(c, id, "blob", p.mime, p.name)
    }

    suspend fun save(c:Context,p:MessagePart)=AppSdk.staging.withLock {
        val metadata=FilesBroker.stage(c,Uri.parse(p.uri)){}
        try { AppSdk.call(c,JSONObject().put("action","message_import").put("name",FilesBroker.cleanName(p.name.ifBlank{metadata.getString("name")})).put("mime",p.mime)) }
        finally { java.io.File(c.filesDir,"content-import.tmp").delete() }
    }

    fun open(c: Context, p: MessagePart) {
        val uri = grantable(c, p)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, p.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    fun share(c: Context, p: MessagePart) {
        val uri = grantable(c, p)
        c.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(p.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .apply { clipData = android.content.ClipData.newRawUri(p.name, uri) },
                "Share Attachment",
            )
        )
    }
}

@Composable
fun MessageThumbnail(part: MessagePart, modifier: Modifier = Modifier, naturalHeight:Boolean = false, click: () -> Unit) {
    val context = LocalContext.current
    if(part.mime=="image/gif"){
        AnimatedMessageImage(part,modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick=click),fit=naturalHeight,naturalHeight=naturalHeight);return
    }
    val image by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, part.uri) {
            value = null
            value =
                withContext(Dispatchers.IO) {
                    MediaPreferences.decoders.withPermit {
                    ensureActive()
                    try {
                            if (part.mime.startsWith("image/")) {
                                val options =
                                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                context.contentResolver.openInputStream(Uri.parse(part.uri))?.use {
                                    BitmapFactory.decodeStream(it, null, options)
                                }
                                require(
                                    options.outWidth > 0 &&
                                        options.outHeight > 0 &&
                                        options.outWidth.toLong() * options.outHeight <= 100_000_000
                                )
                                options.inJustDecodeBounds = false
                                options.inSampleSize = 1
                                while (
                                    maxOf(options.outWidth, options.outHeight) /
                                        options.inSampleSize > 640
                                ) options.inSampleSize *= 2
                                context.contentResolver.openInputStream(Uri.parse(part.uri))?.use {
                                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                                }
                            } else if(part.mime.startsWith("video/")) {
                                android.media.MediaMetadataRetriever().use{retriever->retriever.setDataSource(context,Uri.parse(part.uri));retriever.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,640,640)?.asImageBitmap()}
                            } else null
                    } catch(e:CancellationException){throw e}
                      catch(_:Exception){null}
                      catch(_:OutOfMemoryError){null}
                    }
                }
        }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = click),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null)
            Image(image!!, part.name, if(naturalHeight) Modifier.fillMaxWidth().heightIn(max=640.dp).aspectRatio(image!!.width.toFloat()/image!!.height) else Modifier.fillMaxSize(), contentScale = if(naturalHeight) ContentScale.Fit else ContentScale.Crop)
        else
            Text(
                if (part.mime.startsWith("video/")) "▶  Video"
                else if (part.mime.startsWith("audio/")) "▶  Audio" else part.name,
                Modifier.padding(12.dp),
                fontSize = 14.sp,
            )
    }
}
