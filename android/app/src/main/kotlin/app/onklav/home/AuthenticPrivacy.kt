package app.onklav.home

import android.app.*
import android.content.*
import android.os.*
import java.lang.ref.WeakReference
import java.util.*

internal object AuthenticClipboard{
 private var token:String?=null
 fun copy(c:Context,code:String,seconds:Int){val id=UUID.randomUUID().toString();token=id;val clip=ClipData.newPlainText("Verification code",code);clip.description.extras=PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true);putString("onklav.authentic",id)};(c.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(clip);Handler(Looper.getMainLooper()).postDelayed({if(token==id)clear(c)},seconds.coerceIn(1,30)*1000L)}
 fun clear(c:Context){val manager=c.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager;val id=token;token=null;if(id!=null&&manager.primaryClipDescription?.extras?.getString("onklav.authentic")==id)manager.clearPrimaryClip()}
}
/** Preserve only internal foreground picker handoffs. Any true application background locks all sessions. */
internal object AuthenticLifecycle{
 private val models=Collections.newSetFromMap(WeakHashMap<AuthenticModel,Boolean>())
 private val installed=Collections.newSetFromMap(WeakHashMap<Application,Boolean>())
 fun register(app:Application,m:AuthenticModel){models.add(m);if(!installed.add(app))return
  val visible=Collections.newSetFromMap(WeakHashMap<Activity,Boolean>());val handler=Handler(Looper.getMainLooper());val lock=Runnable{if(visible.isEmpty())models.toList().forEach{it.lock()}}
  // The host is already started when its mini-app is composed.
  app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks{
   override fun onActivityStarted(a:Activity){visible.add(a);handler.removeCallbacks(lock)}
   override fun onActivityStopped(a:Activity){visible.remove(a);if(!a.isChangingConfigurations)handler.postDelayed(lock,200)}
   override fun onActivityCreated(a:Activity,b:Bundle?){}
   override fun onActivityResumed(a:Activity){visible.add(a);handler.removeCallbacks(lock)}
   override fun onActivityPaused(a:Activity){}
   override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
   override fun onActivityDestroyed(a:Activity){visible.remove(a)}
  })
 }
 fun unregister(m:AuthenticModel){models.remove(m);m.lock()}
}
