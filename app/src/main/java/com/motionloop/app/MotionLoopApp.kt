package com.motionloop.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.StrictMode
import android.util.Log
import com.motionloop.ml.Segmenter
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MotionLoopApp : Application() {

    // Lazy keeps app start free of it. Creating it later is cheap: the models load on first use.
    @Inject lateinit var segmenter: Lazy<Segmenter>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Debug builds flag main-thread disk and network work and leaked closeables in the log,
        // so they show up while testing instead of as jank on a phone.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // In the background the segmentation models only make this process a likelier kill. They
        // load again on the next tap. Waits for a running selection instead of breaking it.
        if (level < ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) return
        scope.launch {
            // Freeing memory must never crash the app.
            try {
                segmenter.get().close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not free the segmentation models", e)
            }
        }
    }

    private companion object {
        const val TAG = "MotionLoopApp"
    }
}
