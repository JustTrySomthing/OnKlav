package app.onklav.home

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.*
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.*
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

class PhoneHistoryFixture:ContentProvider(){
 companion object{var selection:String?=null;var arguments:Array<out String>?=null;var sort:String?=null;var count=101}
 override fun onCreate()=true
 override fun query(uri:Uri,projection:Array<out String>?,where:String?,args:Array<out String>?,order:String?):android.database.Cursor{selection=where;arguments=args;sort=order;return MatrixCursor(arrayOf("_id","number","name","type","date","duration","presentation")).apply{repeat(PhoneHistoryFixture.count){i->addRow(arrayOf(101L-i,"5550100","Example",CallLog.Calls.MISSED_TYPE,200000L-i,40L,TelecomManager.PRESENTATION_ALLOWED))}}}
 override fun getType(uri:Uri)="vnd.android.cursor.dir/calls"
 override fun insert(uri:Uri,v:ContentValues?):Uri?=null
 override fun update(uri:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0
 override fun delete(uri:Uri,s:String?,a:Array<out String>?)=0
}
class PhoneContactFixture:ContentProvider(){
 companion object{var deleted:Uri?=null}
 override fun onCreate()=true
 override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?)=MatrixCursor(arrayOf("lookup")).apply{addRow(arrayOf("stable-key"))}
 override fun getType(u:Uri)="vnd.android.cursor.item/contact"
 override fun insert(u:Uri,v:ContentValues?):Uri?=null
 override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0
 override fun delete(u:Uri,s:String?,a:Array<out String>?):Int{deleted=u;return 1}
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class PhoneTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 @Test fun dialInputsNormalizeVanityNumbersAndPreservePostDialControls(){assertEquals("+18003569377,12;3#",PhoneRules.input("+1 (800) FLOWERS,12;3#"));assertFalse(PhoneRules.callable(""));assertFalse(PhoneRules.callable("+"));assertFalse(PhoneRules.callable("123",TelecomManager.PRESENTATION_RESTRICTED));assertTrue(PhoneRules.callable("*86"));assertEquals(80,PhoneRules.input("1".repeat(200)).length)}
 @Test fun externalLinksOnlyPrefillAndNeverTreatCallIntentsAsPermissionToDial(){val uri=Uri.fromParts("tel","+15550100",null);assertEquals("+15550100",PhoneRules.dialIntent(Intent(Intent.ACTION_DIAL,uri)));assertEquals("+15550100",PhoneRules.dialIntent(Intent(Intent.ACTION_VIEW,uri)));assertEquals("",PhoneRules.dialIntent(Intent(Intent.ACTION_CALL,uri)));assertEquals("",PhoneRules.dialIntent(Intent(Intent.ACTION_DIAL,Uri.parse("https://example.org/123"))))}
 @Test fun restrictedHistoryNeverExposesANameOrOffersRedial(){val e=PhoneEvent(1,"5550100","Private contact",CallLog.Calls.MISSED_TYPE,1,4,TelecomManager.PRESENTATION_RESTRICTED);assertEquals("Private number",e.title);assertFalse(e.callable);assertEquals("1:01:01",PhoneRules.duration(3661));assertEquals("0:00",PhoneRules.duration(-1))}
 @Test fun defaultRoleAndCallAccessArePartOfVersionedSetup(){shadowOf(c.getSystemService(RoleManager::class.java)).addAvailableRole(RoleManager.ROLE_DIALER);shadowOf(c.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage("other.app");assertFalse(platformSetupSteps(c).single{it.id=="phone"}.complete);assertFalse(Manifest.permission.READ_CALL_LOG in OnKlavPermissions.runtime(c));shadowOf(c.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage(c.packageName);shadowOf(c).denyPermissions(Manifest.permission.READ_CALL_LOG,Manifest.permission.CALL_PHONE,Manifest.permission.WRITE_CONTACTS);assertTrue(platformSetupSteps(c).single{it.id=="phone"}.complete);val pending=OnKlavPermissions.runtime(c);assertTrue(Manifest.permission.READ_CALL_LOG in pending);assertTrue(Manifest.permission.CALL_PHONE in pending);assertTrue(Manifest.permission.WRITE_CONTACTS in pending);assertTrue(OnKlavPermissions.SETUP_VERSION>=5)}
 @Test fun manifestProvidesDialerEligibilityAndProtectsCallActions(){val pm=c.packageManager;val service=pm.getServiceInfo(ComponentName(c,PhoneInCallService::class.java),PackageManager.GET_META_DATA);assertTrue(service.exported);assertEquals(Manifest.permission.BIND_INCALL_SERVICE,service.permission);assertTrue(service.metaData.getBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_UI));assertFalse(service.metaData.getBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_RINGING));assertFalse(pm.getActivityInfo(ComponentName(c,PhoneCallActivity::class.java),0).exported);assertFalse(pm.getReceiverInfo(ComponentName(c,PhoneCallActionReceiver::class.java),0).exported);assertNotNull(PhoneModel::class.java.getConstructor(Application::class.java))}
 @Test fun historyIsKeysetPagedAndSearchArgumentsAreBound(){shadowOf(c).grantPermissions(Manifest.permission.READ_CALL_LOG);shadowOf(c.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage(c.packageName);ShadowContentResolver.registerProviderInternal("call_log",Robolectric.buildContentProvider(PhoneHistoryFixture::class.java).create().get());PhoneHistoryFixture.count=101;val before=PhoneEvent(900,"", "",1,300000,0);val (rows,more)=PhoneRepository(c).events("50%_",true,before);assertEquals(100,rows.size);assertTrue(more);assertTrue(PhoneHistoryFixture.selection!!.contains("date < ?"));assertFalse(PhoneHistoryFixture.selection!!.contains("50%"));assertTrue(PhoneHistoryFixture.arguments!!.contains("%50\\%\\_%"));assertEquals("date DESC, _id DESC",PhoneHistoryFixture.sort);shadowOf(c.getSystemService(TelecomManager::class.java)).setDefaultDialerPackage("another.app");assertThrows(IllegalStateException::class.java){PhoneRepository(c).events("",false,null)}}
 @Test fun inCallServicePublishesLegacyAudioAndClearsItWhenDestroyed(){val controller=Robolectric.buildService(PhoneInCallService::class.java).create();val s=controller.get();assertNotNull(s.onBind(Intent(InCallService.SERVICE_INTERFACE)));s.onCallAudioStateChanged(CallAudioState(true,CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER));assertTrue(PhoneCalls.state.value.muted);assertEquals("Speaker",PhoneCalls.state.value.audio.single{it.selected}.name);assertTrue(PhoneCalls.state.value.audio.any{it.earpiece});controller.destroy();assertNull(PhoneCalls.service);assertTrue(PhoneCalls.state.value.calls.isEmpty())}
 @android.annotation.TargetApi(34) @Test @Config(sdk=[35]) fun modernAudioUsesFrameworkSuppliedEndpoints(){val controller=Robolectric.buildService(PhoneInCallService::class.java).create();val s=controller.get();val e=CallEndpoint("Speaker",CallEndpoint.TYPE_SPEAKER,android.os.ParcelUuid(java.util.UUID.randomUUID()));s.onAvailableCallEndpointsChanged(mutableListOf(e));s.onCallEndpointChanged(e);s.onMuteStateChanged(true);assertTrue(PhoneCalls.state.value.muted);assertEquals(e.identifier.toString(),PhoneCalls.state.value.audio.single{it.selected}.id);controller.destroy()}
 @Test fun completeNumbersUseFullFormattingAndServiceCodesStayUnchanged(){assertEquals("5550100",PhoneRules.input(PhoneRules.display("5550100")));assertFalse(PhoneRules.display("5550100").endsWith("-0"));assertEquals("*86",PhoneRules.display("*86"));assertEquals("+12025550123",PhoneRules.input(PhoneRules.display("+12025550123")))}

 @Test fun partialTypingFormatsWithoutChangingDialedDigits(){
  assertEquals("202-5",PhoneRules.typing("2025","US"));assertEquals("(202) 555-0123",PhoneRules.typing("2025550123","US"))
  for(n in listOf("2","202","2025550","+442079460123","*86","123,4;5#"))assertEquals(n,PhoneRules.input(PhoneRules.typing(n,"US")))
 }
 @Test fun speedDialPersistsOnlyExplicitValidAssignmentsAndRemovesCleanly(){
  c.getSharedPreferences("phone-speed-dial",0).edit().clear().commit()
  try{val store=PhoneSpeedDial(c);store.set(2,"2025550123");assertEquals("2025550123",PhoneSpeedDial(c).entries()[2]);assertThrows(IllegalArgumentException::class.java){store.set(1,"123")};assertThrows(IllegalArgumentException::class.java){store.set(3,"abc")};store.set(2,null);assertTrue(store.entries().isEmpty())}finally{c.getSharedPreferences("phone-speed-dial",0).edit().clear().commit()}
 }
 @Test fun contactDeletionUsesStableLookupAndChecksRevokedWriteAccess(){
  shadowOf(c).grantPermissions(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS)
  ShadowContentResolver.registerProviderInternal("com.android.contacts",Robolectric.buildContentProvider(PhoneContactFixture::class.java).create().get())
  val repo=PhoneRepository(c);val target=repo.removal(ContactRow(7,"Example",null,listOf("2025550123")))
  assertTrue(target.uri.path!!.contains("lookup/stable-key/7"));PhoneContactFixture.deleted=null
  shadowOf(c).denyPermissions(Manifest.permission.WRITE_CONTACTS);assertThrows(IllegalStateException::class.java){repo.deleteContact(target)};assertNull(PhoneContactFixture.deleted)
  shadowOf(c).grantPermissions(Manifest.permission.WRITE_CONTACTS);repo.deleteContact(target);assertEquals(target.uri,PhoneContactFixture.deleted)
 }

 @Test fun voicemailSettingsArePerAccountAndRejectInvalidNumbers(){
  val c=ApplicationProvider.getApplicationContext<Application>();c.getSharedPreferences("phone-ui",0).edit().clear().commit()
  val a=PhoneAccountHandle(ComponentName(c,PhoneInCallService::class.java),"SIM1")
  val b=PhoneAccountHandle(ComponentName(c,PhoneInCallService::class.java),"SIM2")
  PhoneLocalSettings.voicemail(c,a,"123");PhoneLocalSettings.voicemail(c,b,"456")
  assertEquals("123",PhoneLocalSettings.voicemail(c,a));assertEquals("456",PhoneLocalSettings.voicemail(c,b));assertEquals("",PhoneLocalSettings.voicemail(c,null))
  assertTrue(runCatching{PhoneLocalSettings.voicemail(c,a,"not a number")}.isFailure)
  PhoneLocalSettings.voicemail(c,a,"");assertEquals("",PhoneLocalSettings.voicemail(c,a));assertEquals("456",PhoneLocalSettings.voicemail(c,b))
  c.getSharedPreferences("phone-ui",0).edit().clear().commit()
 }

 @Test fun returningToACallTargetsOnlyThePrivateOnKlavCallActivity(){
  PhoneCalls.show(c);val intent=shadowOf(c).nextStartedActivity
  assertNotNull(intent);assertEquals(ComponentName(c,PhoneCallActivity::class.java),intent.component)
  assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
 }

}
