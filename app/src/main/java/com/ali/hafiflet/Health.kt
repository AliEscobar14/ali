package com.ali.hafiflet

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

class Health(
    val ramTotal: Long,
    val ramAvailable: Long,
    val lowMemory: Boolean,
    val storageTotal: Long,
    val storageFree: Long,
    val batteryLevel: Int,
    val batteryTempC: Float,
    val charging: Boolean,
    val uptimeMs: Long,
) {
    companion object {
        fun read(context: Context): Health {
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)

            val stat = StatFs(Environment.getDataDirectory().path)

            // Yapışkan yayın: alıcı kaydetmeden son pil durumunu verir.
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

            return Health(
                ramTotal = memory.totalMem,
                ramAvailable = memory.availMem,
                lowMemory = memory.lowMemory,
                storageTotal = stat.totalBytes,
                storageFree = stat.availableBytes,
                batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                batteryTempC = temp / 10f,
                charging = plugged != 0,
                uptimeMs = SystemClock.elapsedRealtime(),
            )
        }
    }
}
