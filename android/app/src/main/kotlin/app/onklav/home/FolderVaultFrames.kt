package app.onklav.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A guest frame can outlive a lock. Remove stale names before displaying it. */
internal object FolderVaultFrames{
 fun visible(c:Context,id:String):Boolean=runCatching{val s=AppSdk.folderVault(c,"status",id);!s.optBoolean("locked")||s.optString("id")==id}.getOrDefault(false)
 fun sanitize(c:Context,input:JSONObject):JSONObject{
  val frame=JSONObject(input.toString());val state=frame.child("state");val folder=state.optString("folder")
  val locked=folder.isNotEmpty()&&runCatching{AppSdk.folderVault(c,"status",folder).optBoolean("locked")}.getOrDefault(true)
  val selected=state.child("selected").optString("id");val selectedHidden=selected.isNotEmpty()&&!visible(c,selected)
  val allowed=mutableMapOf<String,Boolean>()
  fun show(id:String)=allowed.getOrPut(id){visible(c,id)}
  if(locked){frame.put("title","Files").put("body",JSONArray());state.put("title","Encrypted folder").put("items",JSONArray()).put("query","")}
  else{
   state.put("items",JSONArray(state.nodes("items").filter{show(it.optString("id"))}))
   frame.put("body",JSONArray(frame.nodes("body").filter{val action=it.optString("id");!action.startsWith("entry:")||show(action.removePrefix("entry:"))}))
  }
  if(locked||selectedHidden){frame.put("menu_open",false).remove("confirm");state.put("selected",JSONObject.NULL).put("menu",false)}
  return frame
 }
}
