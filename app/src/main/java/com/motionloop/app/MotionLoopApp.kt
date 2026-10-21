package com.motionloop.app

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MotionLoopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds flag main-thread disk and network work and leaked closeables in the log,
        // so they show up while testing instead of as jank on a phone.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
    }
}
