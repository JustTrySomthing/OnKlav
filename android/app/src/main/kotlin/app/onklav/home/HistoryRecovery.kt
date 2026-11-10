package app.onklav.home

import android.content.Context
import app.onklav.shared.NativeCore
import org.json.JSONArray
import org.json.JSONObject

/** Host-owned restore setup, recovery review and receipt compaction; no guest service. */
internal object HistoryRecovery {
    private fun call(c:Context,action:String,fields:JSONObject=JSONObject()):JSONObject {
        val result=JSONObject(NativeCore.dispatch(c.filesDir.absolutePath,fields.put("op","history_recovery").put("action",action).toString()))
        check(result.optBoolean("ok")){result.optString("error","Restore recovery unavailable")}
        return result.getJSONObject("value")
    }
    fun configuration(c:Context):List<String> {val a=call(c,"config_get").getJSONArray("identities");return (0 until a.length()).map{a.getString(it)}}
    fun save(c:Context,text:String):List<String> = HistoryRestore.exclusive {
        require(text.length<=1024){"Local phone identities exceed the text limit"}
        val identities=text.lineSequence().map{it.trim()}.filter{it.isNotEmpty()}.toList()
        val a=call(c,"config_save",JSONObject().put("identities",JSONArray(identities))).getJSONArray("identities")
        (0 until a.length()).map{a.getString(it)}
    }
    fun overview(c:Context,offset:Int=0):JSONObject=HistoryRestore.exclusive{HistoryRestore.recoverState(c);call(c,"overview",JSONObject().put("offset",offset))}
    fun compactCompleted(c:Context):JSONObject=HistoryRestore.exclusive{call(c,"compact_completed")}
    fun compact(c:Context):JSONObject=HistoryRestore.exclusive{call(c,"compact_journal")}
    fun detail(c:Context,id:String):JSONObject=HistoryRestore.exclusive{call(c,"map_detail",JSONObject().put("id",id))}
    fun checkpoint(c:Context,saved:HistoryResume.Binding):JSONObject=HistoryRestore.exclusive{call(c,"resume_checkpoint",JSONObject().put("id",saved.id).put("map_sha",saved.mapSha))}
    fun resumeInfo(c:Context,id:String):JSONObject=HistoryRestore.exclusive{call(c,"resume_info",JSONObject().put("id",id))}
    fun repair(c:Context,id:String):JSONObject=HistoryRestore.exclusive{call(c,"repair_ack",JSONObject().put("id",id))}
    fun confirmRemovedChecked(c:Context,id:String):JSONObject=HistoryRestore.exclusive{
        val stop=HistoryBackup.Stop();val provider=HistoryRestoreProvider(c,stop)
        provider.checkAccess();HistoryRestore.confirmRemoved(c,id,provider,stop)
    }
    fun cleanupChecked(c:Context,id:String):JSONObject=HistoryRestore.exclusive{
        val provider=HistoryRestoreProvider(c,HistoryBackup.Stop());provider.checkAccess();HistoryRestore.cleanup(c,id,provider)
    }
}
