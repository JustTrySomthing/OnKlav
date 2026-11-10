package app.onklav.home

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Collections
import java.util.WeakHashMap

/** Lock on actual application backgrounding, preserving internal picker handoffs. */
internal object FolderVaultLifecycle{
 private val installed=Collections.newSetFromMap(WeakHashMap<Application,Boolean>())
 fun install(context:Context){val app=context.applicationContext as Application
  synchronized(installed){if(!installed.add(app))return}
  val visible=Collections.newSetFromMap(WeakHashMap<Activity,Boolean>())
  val handler=Handler(Looper.getMainLooper());val lock=Runnable{if(visible.isEmpty())FolderVaults.background(app)}
  app.registerActivityLifecycleCallbacks(object:Application.ActivityLifecycleCallbacks{
   override fun onActivityStarted(a:Activity){visible.add(a);handler.removeCallbacks(lock)}
   override fun onActivityStopped(a:Activity){visible.remove(a);if(!a.isChangingConfigurations)handler.postDelayed(lock,200)}
   override fun onActivityCreated(a:Activity,b:Bundle?){}
   override fun onActivityResumed(a:Activity){}
   override fun onActivityPaused(a:Activity){}
   override fun onActivitySaveInstanceState(a:Activity,b:Bundle){}
   override fun onActivityDestroyed(a:Activity){visible.remove(a)}
  })
 }
}
