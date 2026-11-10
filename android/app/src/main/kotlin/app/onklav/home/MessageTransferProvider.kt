package app.onklav.home

import android.content.*
import android.database.*
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Individual private media/PDU files; access outside the host requires a URI grant. */
class MessageTransferProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val ctx = requireNotNull(context)
        val name = uri.pathSegments.singleOrNull() ?: throw FileNotFoundException()
        if (
            uri.authority != ctx.packageName + ".message-files" ||
                !name.matches(Regex("[a-f0-9]{32}\\.(blob|pdu)"))
        )
            throw FileNotFoundException()
        val dir=File(ctx.filesDir,"mms");val file=File(dir,name)
        if(java.nio.file.Files.isSymbolicLink(dir.toPath()) || java.nio.file.Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile!=dir.canonicalFile || !file.isFile)throw FileNotFoundException("Message file unavailable")
        return file
    }

    override fun getType(uri: Uri): String {
        file(uri)
        return uri.getQueryParameter("mime")?.takeIf {
            it.matches(Regex("[a-z0-9.+-]+/[a-z0-9.+-]+"))
        } ?: "application/vnd.wap.mms-message"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val target = file(uri)
        val access =
            when (mode) {
                "r" -> ParcelFileDescriptor.MODE_READ_ONLY
                "w",
                "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
                "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
                else -> throw FileNotFoundException("Unsupported access")
            }
        return ParcelFileDescriptor.open(target, access)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val target = file(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME ->
                            uri.getQueryParameter("name")?.take(120) ?: target.name
                        OpenableColumns.SIZE -> target.length()
                        else -> null
                    }
                }
            )
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        fun id() = UUID.randomUUID().toString().replace("-", "")

        fun file(context: Context, id: String, ext: String = "blob"): File {
            require(id.matches(Regex("[a-f0-9]{32}")) && ext in setOf("blob", "pdu"))
            val folder = File(context.filesDir, "mms")
            check(folder.mkdirs() || folder.isDirectory)
            require(!java.nio.file.Files.isSymbolicLink(folder.toPath())) { "Message storage is unavailable" }
            val target=File(folder,"$id.$ext")
            require(!java.nio.file.Files.isSymbolicLink(target.toPath()) && target.canonicalFile.parentFile==folder.canonicalFile) { "Invalid message file" }
            return target
        }

        fun uri(
            context: Context,
            id: String,
            ext: String = "blob",
            mime: String = "application/octet-stream",
            name: String = "Attachment",
        ): Uri =
            Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".message-files")
                .appendPath(file(context, id, ext).name)
                .appendQueryParameter("mime", mime)
                .appendQueryParameter("name", name)
                .build()

        fun create(context: Context, mime: String, ext: String = "blob"): Uri {
            val id = id()
            file(context, id, ext).createNewFile()
            return uri(context, id, ext, mime)
        }
    }
}
