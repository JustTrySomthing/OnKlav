package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class ToolbarHistoryHostTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
 private fun launch(id:String)=call(JSONObject().put("action","launch").put("id",id))
 private fun event(t:String,a:String,v:String="")=call(JSONObject().put("action","event").put("session",t).put("event",JSONObject().put("action",a).put("value",v)))
 private fun close(t:String){AppSdk.sync(c,JSONObject().put("action","close").put("session",t))}
 @Test fun browserHistorySurvivesReopenAndClearKeepsBookmarks(){
  runBlocking{AppSdk.bootstrap(c)};var app=launch("app.onklav.browser");var token=app.getString("session")
  val tab=app.child("frame").child("state").child("meta").getJSONArray("tabs").getJSONObject(0).getString("id")
  fun visit(url:String,title:String)=call(JSONObject().put("action","web_event").put("session",token).put("widget","browser.page").put("tab",tab).put("value",JSONObject().put("url",url).put("title",title).put("back",true).put("forward",false)))
  var page=visit("https://example.org/one","First page");assertFalse(page.has("pending"));event(token,"bookmark.add")
  visit("https://example.org/two","Second page");visit("https://example.org/one","First page updated")
  close(token);app=launch("app.onklav.browser");token=app.getString("session")
  var history=event(token,"history").child("frame");assertEquals("History",history.getString("drawer_title"));val entries=history.getJSONArray("drawer");assertEquals("Clear History",entries.getJSONObject(0).getString("text"));assertEquals("First page updated",entries.getJSONObject(1).getString("text"));assertEquals(2,history.child("state").getJSONArray("history").length())
  history=event(token,"history.clear").child("frame");assertEquals("Clear History?",history.child("confirm").getString("title"))
  history=event(token,"confirm.accept").child("frame");assertEquals(0,history.child("state").getJSONArray("history").length());assertEquals(1,history.child("state").child("meta").getInt("bookmarks"));val web=history.getJSONArray("body").let{a->(0 until a.length()).map{a.getJSONObject(it)}.single{it.optString("kind")=="web"}};assertEquals("find.close",web.getString("command"));assertTrue(web.getBoolean("clear_history"))
  close(token);app=launch("app.onklav.browser");token=app.getString("session");history=event(token,"history").child("frame");assertEquals(0,history.child("state").getJSONArray("history").length());assertEquals(1,history.child("state").child("meta").getInt("bookmarks"));close(token)
 }
 @Test fun filesOnlyOfferPencilsForMutablePersonalItems(){
  runBlocking{AppSdk.bootstrap(c)};val app=launch("app.onklav.files");val token=app.getString("session")
  fun rows(frame:JSONObject)=frame.getJSONArray("body").let{a->(0 until a.length()).map{a.getJSONObject(it)}.filter{it.optString("kind")=="record"}}
  val home=app.child("frame");assertTrue(rows(home).all{!it.has("trailing")});assertFalse(home.getJSONArray("actions").let{a->(0 until minOf(a.length(),home.optInt("header_actions",2))).map{a.getJSONObject(it)}}.toString().contains("Refresh"))
  var frame=event(token,"entry:system").child("frame");assertTrue(rows(frame).all{!it.has("trailing")});frame=event(token,"entry:system-apps").child("frame");assertTrue(rows(frame).isNotEmpty());assertTrue(rows(frame).all{!it.has("trailing")})
  event(token,"home");event(token,"entry:documents");event(token,"new.folder");event(token,"name","Editable folder");event(token,"name.save");frame=event(token,"refresh").child("frame");val editable=rows(frame).single{it.optString("text")=="Editable folder"};assertEquals("edit",editable.child("trailing").getString("icon"));assertTrue(rows(frame).filter{it.optString("text")=="Notes"}.all{!it.has("trailing")})
  event(token,"home");val pending=event(token,"entry:external").getJSONObject("pending")
  val items=org.json.JSONArray().put(JSONObject().put("id","read-only").put("name","Read only").put("folder",false).put("protected",false).put("editable",false)).put(JSONObject().put("id","writable").put("name","Writable").put("folder",false).put("protected",false).put("editable",true)).put(JSONObject().put("id","root").put("name","Connected root").put("folder",true).put("connected_root",true).put("editable",false))
  frame=call(JSONObject().put("action","reply").put("session",token).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",true).put("value",JSONObject().put("folder","external").put("parent","").put("title","External Files").put("external",true).put("items",items).put("total",3)))).child("frame")
  assertFalse(rows(frame).single{it.optString("text")=="Read only"}.has("trailing"));assertEquals("edit",rows(frame).single{it.optString("text")=="Writable"}.child("trailing").getString("icon"));assertEquals("settings",rows(frame).single{it.optString("text")=="Connected root"}.child("trailing").getString("icon"));close(token)
 }
}
