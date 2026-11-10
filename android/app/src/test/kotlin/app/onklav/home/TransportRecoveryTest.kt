package app.onklav.home

import android.app.Activity
import android.app.role.RoleManager
import android.app.job.JobScheduler
import android.content.*
import android.database.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TransportRecoveryTest {
    @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
    private fun context():Context {
        val base=ApplicationProvider.getApplicationContext<Context>();val root=File(base.cacheDir,"transport-${java.util.UUID.randomUUID()}").apply{mkdirs()}
        return object:ContextWrapper(base){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
    }
    private fun result(id:String="a".repeat(32),kind:String="sms_sent",part:Int=0)=Intent("app.onklav.$kind").setData(Uri.parse("onklav-result://$kind/$id/$part")).putExtra("token",id).putExtra("kind",kind).putExtra("part",part)
    private fun stage(c:Context,id:String){ChatStore(c).requestSync(JSONObject().put("action","stage").put("id",id).put("address","+15555550101").put("text","Fixture").put("parts",1).put("at",System.currentTimeMillis()))}
    @Test fun callbacksRequireMatchingActionUriKindAndPart(){
        assertTrue(CarrierResults.valid(result()))
        assertFalse(CarrierResults.valid(result().setAction("wrong")))
        assertFalse(CarrierResults.valid(result().setData(Uri.parse("onklav-result://sms_sent/other/0"))))
        assertFalse(CarrierResults.valid(result(part=300)))
        assertFalse(CarrierResults.valid(result(kind="unknown")))
    }
    @Test fun receiptsSurviveRestartUntilCoreAcceptsTheOutcomeWithoutResending(){
        val c=context();val id="a".repeat(32)
        assertThrows(Exception::class.java){MessagingTransport.receiveResult(c,result(id),Activity.RESULT_OK)}
        assertEquals(1,CarrierResults(c).ids().size)
        stage(c,id);MessagingTransport.recoverResults(c)
        assertTrue(CarrierResults(c).ids().isEmpty())
        val state=ChatStore(c).requestSync(JSONObject().put("action","snapshot"))
        assertEquals("Sent",state.child("outgoing").child(id).getString("status"))
        MessagingTransport.receiveResult(c,result(id),Activity.RESULT_OK)
        assertTrue(CarrierResults(c).ids().isEmpty())
    }
    @Test fun walkieCallbackIsRetainedWhenPermissionGatePreventsReconciliation(){
        val c=context();val id="b".repeat(32);val dir=File(c.filesDir,"walkie-carrier-jobs").apply{mkdirs()}
        val file=File(dir,"$id.json").apply{writeText(JSONObject().put("kind","control").put("request",JSONObject().put("operation","result")).put("peer","+15555550101").put("sub",1).put("at",System.currentTimeMillis()).toString())}
        val intent=Intent("app.onklav.WALKIE_RESULT").setData(Uri.parse("onklav-walkie://result/$id")).putExtra("job",id)
        assertThrows(Exception::class.java){SdkWalkieTransport.result(c,intent,Activity.RESULT_OK)}
        assertTrue(file.exists());assertTrue(JSONObject(file.readText()).getBoolean("callback_ok"))
        assertFalse(MessagingTransport.MMS_READY)
        SdkWalkieTransport.packageRemoved(c);assertFalse(file.exists())
    }
    @Test fun recoverySchedulingCoalescesAndDoesNotEnableTransport(){
        val c=context();TransportRecovery.schedule(c,1000);TransportRecovery.schedule(c,10000)
        val jobs=c.getSystemService(JobScheduler::class.java).allPendingJobs
        assertEquals(1,jobs.count{it.id==4181});assertTrue(jobs.single{it.id==4181}.isPersisted)
        assertNull(TransportRecovery.run(c));assertFalse(MessagingTransport.MMS_READY)
    }
    @Test fun internalBrowserHandoffUsesTheSignedGuestNavigationAndRejectsUnsafeSchemes()=kotlinx.coroutines.runBlocking {
        val application=ApplicationProvider.getApplicationContext<android.app.Application>()
        val model=SdkAppModel(application)
        model.open("app.onklav.browser")
        assertNull(model.error)
        model.openWebLink("https://example.org/article?q=onklav")
        assertEquals("https://example.org/article?q=onklav",model.frame.child("state").getString("address"))
        try { model.openWebLink("javascript:alert(1)");fail("Unsafe link accepted") } catch(_:IllegalArgumentException) {}
        assertEquals("https://example.org/article?q=onklav",model.frame.child("state").getString("address"))
    }
    @Test fun committedCoreRemovalWithBlockedPrivateCleanupResumesBeforeAnotherLaunch()=kotlinx.coroutines.runBlocking {
        val c=context();AppSdk.bootstrap(c);val id="app.onklav.calc"
        val data=File(c.filesDir,"app-sdk/data/$id").apply{mkdirs()};val retained=File(c.filesDir,"retained-calculator");assertTrue(data.renameTo(retained))
        val foreign=File(c.filesDir,"foreign").apply{mkdirs()};File(foreign,"keep").writeText("untouched")
        java.nio.file.Files.createSymbolicLink(data.toPath(),foreign.toPath())
        val profile=SdkNativeData.profile(c,id)
        assertEquals(true,AppSdk.sync(c,JSONObject().put("action","remove").put("id",id).put("delete_data",true)))
        assertNotNull(SdkNativeData.record(c,id));assertEquals(profile,SdkNativeData.profile(c,id))
        assertThrows(Exception::class.java){AppSdk.sync(c,JSONObject().put("action","launch").put("id",id))}
        assertTrue(data.delete());assertTrue(retained.renameTo(data))
        AppSdk.sync(c,JSONObject().put("action","list"))
        assertNull(SdkNativeData.record(c,id));assertFalse(data.exists());assertNotEquals(profile,SdkNativeData.profile(c,id))
        assertEquals("untouched",File(foreign,"keep").readText())
    }
    class Provider:ContentProvider(){
        var rejectAddress=false;val inserted=mutableListOf<String>();val deleted=mutableListOf<String>()
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor=MatrixCursor(projection?:arrayOf("_id")).apply{if(selection?.startsWith("ct_l IN")!=true)addRow((projection?:arrayOf("_id")).map{if(it=="m_id")"fixture" else 7}.toTypedArray())}
        override fun insert(uri:Uri,v:ContentValues?):Uri? {inserted+=uri.toString();return if(uri.path.orEmpty().endsWith("/addr")&&rejectAddress)null else if(uri.path.orEmpty().endsWith("/addr"))Uri.withAppendedPath(uri,"1")else if(uri.path.orEmpty().endsWith("/part"))Uri.parse("content://mms/part/2")else Uri.parse("content://mms/91")}
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int{deleted+=uri.toString();return 1}
        override fun update(uri:Uri,v:ContentValues?,selection:String?,args:Array<out String>?)=1
        override fun getType(uri:Uri):String?=null
    }
    @Test fun failedMmsAddressInsertRollsBackOnlyTheNewParent(){
        val c=context();Shadows.shadowOf(c.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
        val p=Provider().apply{rejectAddress=true};ShadowContentResolver.registerProviderInternal("mms",p);ShadowContentResolver.registerProviderInternal("mms-sms",Provider())
        val pdu=JSONObject().put("from","+15555550101").put("to",JSONArray().put("+15555550102")).put("parts",JSONArray().put(JSONObject().put("mime","text/plain").put("text","Fixture")))
        assertThrows(Exception::class.java){MessagingTransport.persistMms(c,pdu,1,1)}
        assertEquals(listOf("content://mms/91"),p.deleted)
        assertEquals(2,p.inserted.size)
        val before=p.inserted.size
        assertThrows(Exception::class.java){MessagingTransport.persistMms(c,pdu,1,1,Uri.parse("content://mms/40"))}
        assertEquals(before,p.inserted.size)
    }
    private fun failedJobWrites(c:Context)=object:ContextWrapper(c){
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
            val prefs=super.getSharedPreferences(name,mode)
            if(name!="mms_transfers")return prefs
            return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
                if(method.name=="edit") {
                    val editor=prefs.edit()
                    java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,m,a->
                        when(m.name){"commit"->false;else->{val out=m.invoke(editor,*(a?:emptyArray()));if(out is SharedPreferences.Editor)proxy else out}}
                    } as SharedPreferences.Editor
                }else method.invoke(prefs,*(args?:emptyArray()))
            }as SharedPreferences
        }
    }
    @Test fun failedAttemptJournalStopsBeforeCallingTheCarrier(){
        val c=context();val id="c".repeat(32)
        Shadows.shadowOf(c.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
        c.getSharedPreferences("messaging_setup",0).edit().putBoolean("carrier_trial",true).commit()
        assertTrue(c.getSharedPreferences("mms_transfers",0).edit().putString("$id.location","https://carrier.example/mms").commit())
        val error=assertThrows(IllegalStateException::class.java){MessagingTransport.download(failedJobWrites(c),id)}
        assertTrue(error.message!!.contains("attempt could not be saved"))
    }
    @Test fun failedDownloadErrorJournalRetainsTheDurableCallback(){
        val c=context();val id="d".repeat(32)
        assertTrue(c.getSharedPreferences("mms_transfers",0).edit().putString("$id.location","https://carrier.example/mms").commit())
        assertThrows(IllegalStateException::class.java){MessagingTransport.receiveResult(failedJobWrites(c),result(id,kind="mms_download"),Activity.RESULT_CANCELED)}
        assertEquals(1,CarrierResults(c).ids().size)
    }

    @Test fun completedCallbackIsRetainedWhenDurabilityCannotBeConfirmed(){
        val c=context();val id="e".repeat(32)
        assertTrue(c.getSharedPreferences("mms_transfers",0).edit().putBoolean("$id.complete",true).putString("$id.location","https://carrier.example/mms").commit())
        assertThrows(IllegalStateException::class.java){MessagingTransport.receiveResult(failedJobWrites(c),result(id,kind="mms_download"),Activity.RESULT_OK)}
        assertEquals(1,CarrierResults(c).ids().size)
    }

}
