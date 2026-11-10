package app.onklav.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Read-only, one-file exports. Other apps receive only an explicit temporary URI grant. */
class ExportProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri): String =
        when (checked(uri).extension) {
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg" -> "image/jpeg"
            else -> "application/octet-stream"
        }

    private fun checked(uri: Uri): File {
        val ctx = requireNotNull(context)
        val name = uri.pathSegments.singleOrNull() ?: throw FileNotFoundException()
        if (
            uri.authority != ctx.packageName + ".exports" ||
                !name.matches(Regex("[a-f0-9]{32}\\.(txt|png|jpg|onklav-t|onklav-a)"))
        )
            throw FileNotFoundException()
        val directory=File(ctx.cacheDir,"exports");val file=File(directory,name)
        if(java.nio.file.Files.isSymbolicLink(directory.toPath()) || java.nio.file.Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile!=directory.canonicalFile || !file.isFile)throw FileNotFoundException("Export unavailable")
        return file
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only export")
        return ParcelFileDescriptor.open(checked(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = checked(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME ->
                            uri.getQueryParameter("name")?.take(120) ?: file.name
                        OpenableColumns.SIZE -> file.length()
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
    ) = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    companion object {
        fun stage(context: Context, name: String, bytes: ByteArray, suffix: String = "txt"): Uri {
            require(suffix in setOf("txt", "png", "jpg", "onklav-t", "onklav-a") && bytes.size <= 20 * 1024 * 1024)
            val directory = File(context.cacheDir, "exports")
            check(directory.mkdirs() || directory.isDirectory)
            require(!java.nio.file.Files.isSymbolicLink(directory.toPath())) { "Export storage is unavailable" }
            directory
                .listFiles()
                ?.filter {
                    it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) && it.canonicalFile.parentFile==directory.canonicalFile && System.currentTimeMillis() - it.lastModified() > 4L * 24 * 60 * 60 * 1000
                }
                ?.forEach { it.delete() }
            val file = File(directory, UUID.randomUUID().toString().replace("-", "") + ".$suffix")
            try { file.outputStream().use {
                it.write(bytes)
                it.fd.sync()
            } }catch(e:Exception){file.delete();throw e}
            return Uri.Builder()
                .scheme("content")
                .authority(context.packageName + ".exports")
                .appendPath(file.name)
                .appendQueryParameter("name", name)
                .build()
        }
    }
}
