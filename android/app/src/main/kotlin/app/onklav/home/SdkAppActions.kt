package app.onklav.home

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.json.JSONObject

/** One action policy for floating app panels and page toolbar overflow menus. */
@Composable internal fun SdkAppActionItems(actions:List<JSONObject>,blocked:Boolean,toolbarOverflow:Boolean=false,onAction:(String)->Unit){
 actions.forEachIndexed{index,action->
  val enabled=!blocked&&action.optBoolean("enabled",true)
  if(!toolbarOverflow||index>=2)DropdownMenuItem(text={Text(action.optString("text"))},enabled=enabled,onClick={onAction(action.getString("id"))})
  if(action.has("long_action"))DropdownMenuItem(text={Text(action.optString("long_label","More options for "+action.optString("text")))},enabled=enabled,onClick={onAction(action.getString("long_action"))})
 }
}
