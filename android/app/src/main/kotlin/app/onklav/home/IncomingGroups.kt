package app.onklav.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal fun incomingGroupMembers(c:Context,address:String):List<String> {
    val own=c.getSharedPreferences("profile",0).getString("phone","").orEmpty()
    return address.split(';').map{it.trim()}.filter{it.isNotBlank()&&it!="insert-address-token"&&(own.isBlank()||phoneKey(it)!=phoneKey(own))}.distinctBy(::phoneKey)
}
internal fun conversationReadState(row:Conversation,state:JSONObject):Conversation {
    val flags=state.thread(row.id)
    return row.copy(unread=if(!flags.isNull("unread")&&flags.optLong("read_at")>=maxOf(row.date,flags.optLong("last_incoming_at")))flags.optBoolean("unread")else row.unread)
}
/** Discovery only registers identity. It must never refile history or replace a chosen name. */
internal fun ChatStore.registerIncomingGroup(c:Context,row:Conversation):JSONObject =groupIdentityLock {
    var state=requestSync(JSONObject().put("action","snapshot"))
    val members=incomingGroupMembers(c,row.address)
    if(members.size<2||state.savedGroups().any{it.thread==row.id})return@groupIdentityLock state
    require(members.size<=256){"This group exceeds 256 recipients"}
    val existing=state.savedGroups().firstOrNull{it.thread==0L&&sameRecipients(it.members,members)}
    val oldFlags=existing?.let{state.child("threads").child(it.key)}
    val id=existing?.id?:java.util.UUID.nameUUIDFromBytes("onklav-provider-group:${row.id}".toByteArray()).toString().replace("-","")
    val name=existing?.name?:row.name.replace(Regex("[\\p{Cntrl}]")," ").trim().take(60).let{if(it.lastOrNull()?.isHighSurrogate()==true)it.dropLast(1)else it}.ifBlank{"Group text"}
    state=requestSync(JSONObject().put("action","group_save").put("id",id).put("name",name).put("members",JSONArray(members)).put("thread",row.id).put("discovered",true))
    if(oldFlags!=null&&(oldFlags.optBoolean("pinned")||oldFlags.optBoolean("muted"))){
        val update=JSONObject().put("action","thread").put("key",row.id.toString())
        if(oldFlags.optBoolean("pinned"))update.put("pinned",true)
        if(oldFlags.optBoolean("muted"))update.put("muted",true)
        state=requestSync(update)
    }
    state
}
