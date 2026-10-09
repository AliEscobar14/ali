package com.ali.hafiflet

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Panel sekmesi: Shizuku durumu, canlı sistem durumu ve RAM kullanan işlemler. */
class PanelPage(private val activity: MainActivity, root: View) {

    private class Metric(root: View, label: Int) {
        val value: TextView = root.findViewById(R.id.metric_value)
        val bar: LinearProgressIndicator = root.findViewById(R.id.metric_bar)
        val hint: TextView = root.findViewById(R.id.metric_hint)

        init {
            root.findViewById<TextView>(R.id.metric_label).setText(label)
        }
    }

    private val shizukuIcon: ImageView = root.findViewById(R.id.shizuku_icon)
    private val shizukuTitle: TextView = root.findViewById(R.id.shizuku_title)
    private val shizukuText: TextView = root.findViewById(R.id.shizuku_text)
    private val shizukuAction: Button = root.findViewById(R.id.shizuku_action)
    private val ram = Metric(root.findViewById(R.id.metric_ram), R.string.ram)
    private val storage = Metric(root.findViewById(R.id.metric_storage), R.string.storage)
    private val batteryValue: TextView = root.findViewById(R.id.battery_value)
    private val uptimeValue: TextView = root.findViewById(R.id.uptime_value)
    private val processButton: Button = root.findViewById(R.id.process_button)
    private val processList: LinearLayout = root.findViewById(R.id.process_list)

    private val live = object : Runnable {
        override fun run() {
            refreshHealth()
            mainHandler.postDelayed(this, LIVE_INTERVAL_MS)
        }
    }

    init {
        shizukuAction.setOnClickListener { onShizukuAction() }
        root.findViewById<View>(R.id.refresh_health).setOnClickListener { refreshHealth() }
        processButton.setOnClickListener { loadProcesses() }
    }

    fun startLive() {
        mainHandler.removeCallbacks(live)
        live.run()
    }

    fun stopLive() = mainHandler.removeCallbacks(live)

    fun render(state: Shell.State) {
        val ready = state == Shell.State.READY
        shizukuIcon.setImageResource(if (ready) R.drawable.ic_check_circle else R.drawable.ic_warning)
        shizukuIcon.imageTintList = activity.getColorStateList(if (ready) R.color.tint_ok else R.color.tint_warn)
        shizukuAction.show(state == Shell.State.NOT_RUNNING || state == Shell.State.NO_PERMISSION)
        shizukuText.show(state != Shell.State.CONNECTING)
        when (state) {
            Shell.State.NOT_RUNNING -> {
                shizukuTitle.setText(R.string.shizuku_not_running_title)
                shizukuText.setText(R.string.shizuku_not_running)
                shizukuAction.setText(R.string.shizuku_open)
            }
            Shell.State.NO_PERMISSION -> {
                shizukuTitle.setText(R.string.shizuku_no_permission_title)
                shizukuText.setText(R.string.shizuku_no_permission)
                shizukuAction.setText(R.string.shizuku_grant)
            }
            Shell.State.CONNECTING -> shizukuTitle.setText(R.string.shizuku_connecting_title)
            Shell.State.READY -> {
                shizukuTitle.setText(R.string.shizuku_ready_title)
                shizukuText.setText(R.string.shizuku_ready)
            }
        }
        processButton.isEnabled = ready
    }

    private fun onShizukuAction() {
        if (Shell.state == Shell.State.NO_PERMISSION && Shell.requestPermission()) return
        if (Shell.state == Shell.State.NO_PERMISSION) {
            Toast.makeText(activity, R.string.shizuku_denied, Toast.LENGTH_LONG).show()
        }
        val launch = activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        activity.startActivity(launch)
    }

    private fun refreshHealth() {
        val h = Health.read(activity)

        val ramUsed = h.ramTotal - h.ramAvailable
        ram.value.text = activity.getString(R.string.usage_format, size(ramUsed), size(h.ramTotal))
        ram.bar.setProgressCompat(percent(ramUsed, h.ramTotal), true)
        ram.hint.text = if (h.lowMemory) activity.getString(R.string.ram_low)
        else activity.getString(R.string.free_format, size(h.ramAvailable))

        val storageUsed = h.storageTotal - h.storageFree
        storage.value.text = activity.getString(R.string.usage_format, size(storageUsed), size(h.storageTotal))
        storage.bar.setProgressCompat(percent(storageUsed, h.storageTotal), true)
        storage.hint.text = activity.getString(R.string.free_format, size(h.storageFree))

        batteryValue.text = activity.getString(
            R.string.battery_format,
            h.batteryLevel,
            h.batteryTempC,
            activity.getString(if (h.charging) R.string.charging else R.string.not_charging),
        )
        uptimeValue.text = DateUtils.formatElapsedTime(h.uptimeMs / 1000)
    }

    private fun loadProcesses() {
        processButton.isEnabled = false
        processButton.setText(R.string.process_loading)
        activity.background({
            parseMeminfo(Shell.exec("dumpsys meminfo | sed -n '/^Total PSS by process/,/^\$/p'").out).take(10)
        }) { processes ->
            processButton.isEnabled = Shell.state == Shell.State.READY
            processButton.setText(R.string.process_button)
            processList.removeAllViews()
            if (processes.isEmpty()) {
                Toast.makeText(activity, R.string.process_failed, Toast.LENGTH_SHORT).show()
                return@background
            }
            val max = processes.first().pssKb
            for (p in processes) {
                val row = activity.layoutInflater.inflate(R.layout.item_process, processList, false)
                row.findViewById<TextView>(R.id.process_name).text = label(p.name)
                row.findViewById<TextView>(R.id.process_size).text = size(p.pssKb * 1024)
                row.findViewById<LinearProgressIndicator>(R.id.process_bar).progress = percent(p.pssKb, max)
                processList.addView(row)
            }
        }
    }

    /** İşlem adından ("com.foo:remote") uygulama adını bulur; bulamazsa adı olduğu gibi döner. */
    private fun label(processName: String): String {
        val pm = activity.packageManager
        return try {
            pm.getApplicationInfo(processName.substringBefore(':'), 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            processName
        }
    }

    private fun size(bytes: Long) = Formatter.formatShortFileSize(activity, bytes)

    private fun percent(part: Long, total: Long) = if (total > 0) (part * 100 / total).toInt() else 0

    private companion object {
        const val LIVE_INTERVAL_MS = 5000L
    }
}
