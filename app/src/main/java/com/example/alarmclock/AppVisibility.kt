package com.example.alarmclock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * App đang mở hay đã ẩn. Đếm onStart/onStop để không nhầm khi đổi Activity.
 */
object AppVisibility : Application.ActivityLifecycleCallbacks {

    @Volatile
    var foreground: Boolean = false

    private val started = AtomicInteger(0)
    private var resumed: WeakReference<Activity>? = null

    fun resumedActivity(): Activity? = resumed?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }

    fun isForeground(): Boolean = foreground && started.get() > 0

    fun isBackground(): Boolean = !isForeground()

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (started.incrementAndGet() > 0) foreground = true
    }

    override fun onActivityStopped(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
        if (started.decrementAndGet() <= 0) {
            started.set(0)
            foreground = false
            if (TimerDoneController.ringing) {
                TimerDoneController.promoteToFullScreen(activity.applicationContext)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        foreground = true
        resumed = WeakReference(activity)
    }

    override fun onActivityCreated(activity: Activity, b: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, b: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
