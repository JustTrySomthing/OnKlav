package app.onklav.shared

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeBridgeTest {
    @Test fun walkieRulesCrossJniWithoutAnyTransport() {
        val root=Files.createTempDirectory("onklav-walkie-rules-").toFile()
        try {
            fun call(value:JSONObject)=JSONObject(NativeCore.dispatch(root.absolutePath,value.put("op","walkie_rules").toString()))
            val id="a".repeat(32)
            val decoded=call(JSONObject().put("action","frame.decode").put("text","!ICW1|I|$id|0|wt1"))
            assertTrue(decoded.getBoolean("ok"));assertEquals("Invite",decoded.getJSONObject("value").getString("control"))
            val state=call(JSONObject().put("action","floor.transition").put("transition","guest.grant").put("token",3)).getJSONObject("value")
            assertTrue(state.getBoolean("changed"))
            val stale=call(JSONObject().put("action","floor.transition").put("transition","guest.release").put("token",0).put("floor",state.getJSONObject("floor"))).getJSONObject("value")
            assertFalse(stale.getBoolean("changed"))
            val cancelled=call(JSONObject().put("action","capture.finish").put("recorded",true).put("confirmed",false).put("millis",800).put("automatic",true)).getJSONObject("value")
            assertEquals("discard",cancelled.getString("decision"));assertEquals(0,root.listFiles()?.size ?: 0)
        } finally { root.deleteRecursively() }
    }

    @Test
    fun kotlinCallsRealRustAndReloadsUnicodeDocument() {
        val root = Files.createTempDirectory("onklav-jni-").toFile()
        try {
            fun call(request: JSONObject) =
                JSONObject(NativeCore.dispatch(root.absolutePath, request.toString()))
            val doc = call(JSONObject().put("op", "new")).getJSONObject("value")
            doc.put("title", "Family 🐈").put("text", "Kotlin → Rust\n保存されたメモ")
            assertTrue(call(JSONObject().put("op", "save").put("note", doc)).getBoolean("ok"))
            val loaded =
                call(JSONObject().put("op", "load").put("id", doc.getString("id")))
                    .getJSONObject("value")
            assertEquals(doc.getString("title"), loaded.getString("title"))
            assertEquals(doc.getString("text"), loaded.getString("text"))
            assertTrue(loaded.getLong("updated") > 0)
            assertFalse(
                call(JSONObject().put("op", "load").put("id", "../private")).getBoolean("ok")
            )
            doc.put("title", "").put("text", "")
            assertTrue(call(JSONObject().put("op", "save").put("note", doc)).getBoolean("ok"))
            val cleared =
                call(JSONObject().put("op", "load").put("id", doc.getString("id")))
                    .getJSONObject("value")
            assertEquals("", cleared.getString("title"))
            assertEquals("", cleared.getString("text"))
            assertTrue(
                call(JSONObject().put("op", "delete").put("id", doc.getString("id")))
                    .getBoolean("ok")
            )
            assertEquals(0, call(JSONObject().put("op", "list")).getJSONArray("value").length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun oversizedDocumentReturnsFailureAcrossJni() {
        val root = Files.createTempDirectory("onklav-limit-").toFile()
        try {
            val note =
                JSONObject(NativeCore.dispatch(root.absolutePath, "{\"op\":\"new\"}"))
                    .getJSONObject("value")
            note.put("text", "x".repeat(100001))
            val response =
                JSONObject(
                    NativeCore.dispatch(
                        root.absolutePath,
                        JSONObject().put("op", "save").put("note", note).toString(),
                    )
                )
            assertFalse(response.getBoolean("ok"))
        } finally {
            root.deleteRecursively()
        }
    }
}
