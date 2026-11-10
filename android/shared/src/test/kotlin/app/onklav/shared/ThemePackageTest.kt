package app.onklav.shared

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ThemePackageTest {
    private fun archive(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun settings(): ByteArray {
        val bytes = ThemePackage.pack("My Theme", AppearanceSpec(background = ""))
        return java.util.zip.ZipInputStream(bytes.inputStream()).use {
            it.nextEntry
            it.readBytes()
        }
    }

    private fun rejected(action: () -> Unit) {
        try {
            action()
            fail("Invalid theme was accepted")
        } catch (expected: IllegalArgumentException) {}
    }

    @Test
    fun unicodeAndAllPresetsRoundTripWithoutExecutablePayload() {
        (presetThemes.map { it.second } +
                AppearanceSpec(
                    font = 5,
                    size = 26,
                    gradient = false,
                    overlayDark = 0,
                    overlayLight = 100,
                ))
            .forEach { spec ->
                val parsed = ThemePackage.read(ThemePackage.pack("夜の空 🌙", spec))
                assertEquals("夜の空 🌙", parsed.name)
                assertEquals(spec, parsed.spec)
                assertNull(parsed.image)
            }
    }

    @Test
    fun customImageIsEmbeddedAndPrivatePathCannotBeExported() {
        val image = byteArrayOf(1, 2, 3)
        val parsed =
            ThemePackage.read(
                ThemePackage.pack(
                    "Picture",
                    AppearanceSpec(background = "custom:" + "a".repeat(32)),
                    image,
                )
            )
        assertEquals("image", parsed.spec.background)
        assertArrayEquals(image, parsed.image)
        rejected {
            ThemePackage.pack("Private", AppearanceSpec(background = "custom:" + "a".repeat(32)))
        }
        rejected {
            ThemePackage.read(archive(listOf("theme.txt" to settings(), "background.jpg" to image)))
        }
    }

    @Test
    fun rejectsPathsUnknownFieldsDuplicateFieldsAndInvalidValues() {
        rejected { ThemePackage.read(archive(listOf("../theme.txt" to settings()))) }
        rejected {
            ThemePackage.read(
                archive(listOf("theme.txt" to settings(), "payload.gd" to byteArrayOf(1)))
            )
        }
        listOf(
                "palette=99",
                "size=18",
                "font=-1",
                "overlayDark=101",
                "dark=yes",
                "background=../../data",
            )
            .forEach { bad ->
                val key = bad.substringBefore('=')
                val text = settings().toString(Charsets.UTF_8).replace(Regex("(?m)^$key=.*$"), bad)
                rejected { ThemePackage.read(archive(listOf("theme.txt" to text.toByteArray()))) }
            }
        rejected {
            ThemePackage.read(
                archive(listOf("theme.txt" to (settings() + "palette=1\n".toByteArray())))
            )
        }
        rejected {
            ThemePackage.read(
                archive(listOf("theme.txt" to (settings() + "script=evil\n".toByteArray())))
            )
        }
    }

    @Test
    fun rejectsInvalidUtf8AndCompressedExpansion() {
        try {
            ThemePackage.read(archive(listOf("theme.txt" to byteArrayOf(0xc3.toByte(), 0x28))))
            fail("Invalid UTF8 accepted")
        } catch (expected: java.nio.charset.CharacterCodingException) {}
        rejected { ThemePackage.read(archive(listOf("theme.txt" to ByteArray(16385) { 65 }))) }
        rejected {
            ThemePackage.read(
                archive(
                    listOf(
                        "theme.txt" to settings(),
                        "background.jpg" to ByteArray(ThemePackage.MAX_IMAGE + 1),
                    )
                )
            )
        }
    }
}
