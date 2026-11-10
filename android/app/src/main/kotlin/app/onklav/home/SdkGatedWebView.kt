package app.onklav.home

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.webkit.WebView

/** Native website editing menus obey the same grants as Compose fields. No JS bridge. */
internal class SdkGatedWebView(context:Context,private val granted:(String)->Boolean):WebView(context) {
    private fun access(cap:String)=runCatching { granted(cap) }.getOrDefault(false)
    private fun item(id:Int):Boolean=when(id) {
        android.R.id.copy,android.R.id.cut->access("clipboard.write")
        android.R.id.paste,android.R.id.pasteAsPlainText->access("clipboard.read")
        android.R.id.selectAll->true
        // External process-text/share/search actions must use explicit OnKlav services.
        else->false
    }
    private fun allowedAction(id:Int):Boolean=when(id) {
        AccessibilityNodeInfo.ACTION_COPY,AccessibilityNodeInfo.ACTION_CUT->access("clipboard.write")
        AccessibilityNodeInfo.ACTION_PASTE->access("clipboard.read")
        else->true
    }
    private fun wrapActionMode(delegate:ActionMode.Callback)=object:ActionMode.Callback2() {
        fun prune(menu:Menu) { for(i in menu.size()-1 downTo 0) { val entry=menu.getItem(i);if(!item(entry.itemId)) menu.removeItem(entry.itemId) } }
        override fun onCreateActionMode(mode:ActionMode,menu:Menu):Boolean { val result=delegate.onCreateActionMode(mode,menu);prune(menu);return result }
        override fun onPrepareActionMode(mode:ActionMode,menu:Menu):Boolean { val result=delegate.onPrepareActionMode(mode,menu);prune(menu);return result }
        override fun onActionItemClicked(mode:ActionMode,entry:MenuItem)=if(item(entry.itemId)) delegate.onActionItemClicked(mode,entry) else true
        override fun onDestroyActionMode(mode:ActionMode)=delegate.onDestroyActionMode(mode)
        override fun onGetContentRect(mode:ActionMode,view:View,out:Rect) { if(delegate is ActionMode.Callback2) delegate.onGetContentRect(mode,view,out) else super.onGetContentRect(mode,view,out) }
    }
    override fun startActionMode(callback:ActionMode.Callback):ActionMode?=super.startActionMode(wrapActionMode(callback))
    override fun startActionMode(callback:ActionMode.Callback,type:Int):ActionMode?=super.startActionMode(wrapActionMode(callback),type)
    override fun onKeyDown(code:Int,event:KeyEvent):Boolean {
        if(event.isCtrlPressed || event.isMetaPressed) {
            if(code in setOf(KeyEvent.KEYCODE_C,KeyEvent.KEYCODE_X) && !access("clipboard.write")) return true
            if(code==KeyEvent.KEYCODE_V && !access("clipboard.read")) return true
        }
        return super.onKeyDown(code,event)
    }
    override fun performAccessibilityAction(action:Int,args:Bundle?):Boolean=allowedAction(action) && super.performAccessibilityAction(action,args)
    override fun getAccessibilityNodeProvider():AccessibilityNodeProvider? {
        val provider=super.getAccessibilityNodeProvider() ?: return null
        return object:AccessibilityNodeProvider() {
            override fun createAccessibilityNodeInfo(id:Int)=provider.createAccessibilityNodeInfo(id)
            override fun findAccessibilityNodeInfosByText(text:String,id:Int)=provider.findAccessibilityNodeInfosByText(text,id)
            override fun findFocus(focus:Int)=provider.findFocus(focus)
            override fun performAction(id:Int,action:Int,args:Bundle?)=allowedAction(action) && provider.performAction(id,action,args)
            override fun addExtraDataToAccessibilityNodeInfo(id:Int,info:AccessibilityNodeInfo,key:String,args:Bundle?)=provider.addExtraDataToAccessibilityNodeInfo(id,info,key,args)
        }
    }
}
