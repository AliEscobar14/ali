package com.ali.hafiflet

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.init(this)
    }
}

private val worker = Executors.newCachedThreadPool()
val mainHandler = Handler(Looper.getMainLooper())

/** [work]'ü arka planda çalıştırır, sonucu Activity hâlâ açıksa ana iş parçacığında [done]'a verir. */
fun <T> Activity.background(work: () -> T, done: (T) -> Unit) {
    worker.execute {
        val result = work()
        mainHandler.post { if (!isDestroyed) done(result) }
    }
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
