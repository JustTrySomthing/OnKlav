"""Bounded isolated Android service checks when unrelated app dependencies are unavailable.
Production service/UI sources are copied unchanged. Host controller/JNI entry points
are compile-only adapters that refuse execution; this is not integrated acceptance.
"""
from pathlib import Path
import os,shutil,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
work=Path(os.environ['ONKLAV_DEVICE_CHECK_DIR']) if os.environ.get('ONKLAV_DEVICE_CHECK_DIR') else Path(tempfile.mkdtemp(prefix='onklav-device-checks-'))
(work/'settings.gradle.kts').write_text((ROOT/'settings.gradle.kts').read_text().replace('include(":android:app", ":android:shared", ":android:tools", ":android:games")','include(":checks",":android:shared")\nproject(":android:shared").projectDir=file('+repr(str(ROOT/'android/shared')).replace("'",'"')+')'))
shutil.copyfile(ROOT/'build.gradle.kts',work/'build.gradle.kts');shutil.copyfile(ROOT/'gradle.properties',work/'gradle.properties')
main=work/'checks/src/main/kotlin/app/onklav/home';main.mkdir(parents=True,exist_ok=True)
for name in ['SdkReminderPolicy','SdkScheduledNotifications','SdkNotifications','SdkReminderActionReceiver','SdkCalendarHandoff','SdkConnectivity','SdkLocation','SdkTorch','SdkBle','SdkNearby','SdkDeviceServices','SdkHandoffs','SdkHandoffProvider','SdkHandoffEffects','SdkPdfEngine','SdkPdfService','SdkPdfClient','SdkPdfPositions','SdkPdfViewer','SdkMapTiles','SdkMapViewer','SdkHttpPayload','SdkHttpUpload']:
 shutil.copyfile(ROOT/f'android/app/src/main/kotlin/app/onklav/home/{name}.kt',main/f'{name}.kt')
(main/'HarnessAdapters.kt').write_text('''package app.onklav.home
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ColumnScope
import org.json.JSONObject
internal object AppSdk{
 private val lock=Any()
 fun <T> carrierEgress(action:()->T):T=synchronized(lock){action()}
 fun desktopId(id:String)="pkg_"+java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(16).joinToString(""){"%02x".format(it)}
 fun sync(c:Context,r:JSONObject):Any=error("JNI is deliberately unavailable in isolated service checks")
}
internal object ChatStore{val lastError=mutableStateOf<String?>(null)}
class MainActivity:android.app.Activity()
internal class SdkAppModel{
 var busy=false
 var sessionUnavailable=false
 var pending:JSONObject?=null
 fun mapViewport(id:String,width:Int,height:Int,operation:String="viewport",dx:Double=0.0,dy:Double=0.0):Unit=error("Compile-only map event")
 fun mapDocument(id:String):JSONObject=error("Compile-only map descriptor")
 fun pdfDocument(id:String):JSONObject=error("Compile-only PDF descriptor")
 fun clipboardAllowed(cap:String)=false
 fun launchPending(token:String):Boolean=error("Compile-only controller")
 fun handoffFileNow(p:JSONObject):JSONObject=error("Compile-only file resolver")
 fun authorizeNow(p:JSONObject):JSONObject=error("Compile-only controller")
 suspend fun reply(p:JSONObject,value:JSONObject=JSONObject(),failure:String?=null):Unit=error("Compile-only controller")
}
internal fun JSONObject.child(key:String)=optJSONObject(key)?:JSONObject()
''')
manifest=work/'checks/src/main/AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>')
tests=work/'checks/src/test/kotlin/app/onklav/home';tests.mkdir(parents=True,exist_ok=True)
for name in ['SdkDeviceServicesTest','SdkScheduledNotificationsTest','SdkHandoffTest','SdkPdfTest','SdkMapTest','SdkHttpPayloadTest','SdkHttpUploadTest']:
 shutil.copyfile(ROOT/f'android/app/src/test/kotlin/app/onklav/home/{name}.kt',tests/f'{name}.kt')
(work/'checks/build.gradle.kts').write_text('''plugins {id("com.android.library");id("org.jetbrains.kotlin.android");id("org.jetbrains.kotlin.plugin.compose")}
android{namespace="app.onklav.servicechecks";compileSdk=36;defaultConfig{minSdk=29};buildFeatures{compose=true};compileOptions{sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17};kotlinOptions{jvmTarget="17"};testOptions{unitTests.isIncludeAndroidResources=true;unitTests.all{it.systemProperty("robolectric.offline","true");it.systemProperty("robolectric.dependency.dir",System.getenv("ONKLAV_ROBOLECTRIC_JARS"));it.testLogging{events("passed","failed");exceptionFormat=org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL}}}}
dependencies{implementation(project(":android:shared"));testImplementation("junit:junit:4.13.2");testImplementation("org.robolectric:robolectric:4.14.1");testImplementation("androidx.test:core:1.6.1")}
''')
env=os.environ.copy();jar_dir=work/'robolectric-jars';jar_dir.mkdir(exist_ok=True)
for p in (Path(env['ANDROID_HOME']).parent/'m2').rglob('*.jar'):
 if 'android-all-instrumented' in p.name and not (jar_dir/p.name).exists():(jar_dir/p.name).symlink_to(p)
env['ONKLAV_ROBOLECTRIC_JARS']=str(jar_dir)
print('Isolated harness:',work,flush=True)
command=['bash',str(ROOT/'gradlew'),'-p',str(work),'--offline','--no-daemon','--max-workers=2','--console=plain','-Pandroid.aapt2FromMavenOverride='+str(Path(env['ANDROID_HOME'])/'build-tools/35.0.0/aapt2'),(':checks:compileDebugKotlin' if os.environ.get('ONKLAV_DEVICE_COMPILE_ONLY')=='1' else ':checks:testDebugUnitTest')]
import signal
process=subprocess.Popen(command+sum((["--tests",s] for s in os.environ.get('ONKLAV_DEVICE_TESTS','').split(',') if s),[]),env=env,start_new_session=True)
try:code=process.wait(timeout=int(os.environ.get('ONKLAV_DEVICE_CHECK_TIMEOUT','240')))
except subprocess.TimeoutExpired:
 os.killpg(process.pid,signal.SIGKILL);process.wait();raise SystemExit('Bounded service check timed out; stopped its process group')
evidence=ROOT/'docs/evidence/v0.70.0/group12-isolated-results';evidence.mkdir(parents=True,exist_ok=True)
for p in (work/'checks/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):shutil.copyfile(p,evidence/p.name)
raise SystemExit(code)
