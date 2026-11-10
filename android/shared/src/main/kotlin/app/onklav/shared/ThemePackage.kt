package app.onklav.shared

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Data-only OnKlav themes; never extracts paths or loads scripts/native code. */
object ThemePackage {
    const val MAX_ARCHIVE = 8 * 1024 * 1024
    const val MAX_IMAGE = 6 * 1024 * 1024

    data class Contents(val name: String, val spec: AppearanceSpec, val image: ByteArray?)

    private val keys =
        setOf(
            "format",
            "version",
            "name",
            "dark",
            "palette",
            "font",
            "size",
            "gradient",
            "background",
            "overlayDark",
            "overlayLight",
        )

    fun bounded(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16384)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size().toLong() + count <= limit) { "This file is too large." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun settings(bytes: ByteArray): Pair<String, AppearanceSpec> {
        require(bytes.size <= 16384) { "Theme settings are too large." }
        val text =
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        val fields = linkedMapOf<String, String>()
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
                val split = line.indexOf('=')
                require(split > 0) { "Invalid theme setting." }
                val key = line.substring(0, split).trim()
                require(key in keys && key !in fields) { "Unknown or repeated theme setting: $key" }
                fields[key] = line.substring(split + 1).trim()
            }
        }
        require(fields.keys == keys) { "The theme has missing settings." }
        require(fields["format"] == "onklav-theme" && fields["version"] == "1") {
            "Unsupported theme format."
        }
        fun integer(key: String, range: IntRange): Int =
            requireNotNull(fields[key]?.toIntOrNull()?.takeIf { it in range }) { "Invalid $key." }
        fun boolean(key: String): Boolean =
            requireNotNull(fields[key]?.toBooleanStrictOrNull()) { "Invalid $key." }
        val name = fields.getValue("name")
        require(name.isNotBlank() && name.length <= 40 && name.none { it.isISOControl() }) {
            "Use a theme name up to 40 characters."
        }
        val bg = fields.getValue("background")
        require(bg in backgroundNames || bg == "image") { "Unknown background." }
        val size = integer("size", 16..26)
        require(size in fontSizes) { "Unsupported font size." }
        return name to
            AppearanceSpec(
                boolean("dark"),
                integer("palette", 0..14),
                integer("font", 0..5),
                size,
                boolean("gradient"),
                bg,
                integer("overlayDark", 0..100),
                integer("overlayLight", 0..100),
            )
    }

    fun read(bytes: ByteArray): Contents {
        require(bytes.size <= MAX_ARCHIVE) { "Theme file is too large." }
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(
                    !entry.isDirectory &&
                        entry.name in setOf("theme.txt", "background.jpg") &&
                        entry.name !in parts
                ) {
                    "Unexpected or repeated file in theme."
                }
                parts[entry.name] =
                    bounded(zip, if (entry.name == "theme.txt") 16384 else MAX_IMAGE)
                zip.closeEntry()
            }
        }
        val (name, spec) =
            settings(requireNotNull(parts["theme.txt"]) { "Theme settings are missing." })
        val image = parts["background.jpg"]
        require((spec.background == "image") == (image != null)) {
            "Theme background image does not match its settings."
        }
        return Contents(name, spec, image)
    }

    fun pack(name: String, value: AppearanceSpec, image: ByteArray? = null): ByteArray {
        val bg = if (image != null) "image" else value.background
        val text =
            "format=onklav-theme\nversion=1\nname=$name\ndark=${value.dark}\npalette=${value.palette}\nfont=${value.font}\nsize=${value.size}\ngradient=${value.gradient}\nbackground=$bg\noverlayDark=${value.overlayDark}\noverlayLight=${value.overlayLight}\n"
        val settings = text.toByteArray(Charsets.UTF_8)
        // Validate before writing; private background IDs cannot leak into a package.
        settings(settings)
        require(image == null || image.size <= MAX_IMAGE) { "Image is too large." }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOfNotNull("theme.txt" to settings, image?.let { "background.jpg" to it }).forEach {
                (key, data) ->
                zip.putNextEntry(ZipEntry(key).apply { time = 0 })
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray().also { require(it.size <= MAX_ARCHIVE) }
    }
}
