package app.onklav.shared

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun readCapped(input: InputStream, limit: Int): ByteArray {
    require(limit in 1..64 * 1024 * 1024)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        check(output.size() + count <= limit) { "File exceeds the size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
