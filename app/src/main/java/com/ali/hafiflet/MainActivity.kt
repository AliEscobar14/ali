package com.ali.hafiflet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuAction: Button
    private lateinit var ramText: TextView
    private lateinit var ramBar: ProgressBar
    private lateinit var storageText: TextView
    private lateinit var storageBar: ProgressBar
    private lateinit var batteryText: TextView
    private lateinit var processButton: Button
    private lateinit var processList: TextView
    private lateinit var animText: TextView
    private lateinit var wifiScan: Switch
    private lateinit var bleScan: Switch

    /** Yalnızca Shizuku hazırken kullanılabilen kontroller. */
    private lateinit var shizukuViews: List<View>

    private val stateListener: (Shell.State) -> Unit = { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        shizukuStatus = findViewById(R.id.shizuku_status)
        shizukuAction = findViewById(R.id.shizuku_action)
        ramText = findViewById(R.id.ram_text)
        ramBar = findViewById(R.id.ram_bar)
        storageText = findViewById(R.id.storage_text)
        storageBar = findViewById(R.id.storage_bar)
        batteryText = findViewById(R.id.battery_text)
        processButton = findViewById(R.id.process_button)
        processList = findViewById(R.id.process_list)
        animText = findViewById(R.id.anim_text)
        wifiScan = findViewById(R.id.wifi_scan)
        bleScan = findViewById(R.id.ble_scan)

        val animOff = findViewById<Button>(R.id.anim_off)
        val animHalf = findViewById<Button>(R.id.anim_half)
        val animDefault = findViewById<Button>(R.id.anim_default)
        val bloatButton = findViewById<Button>(R.id.bloat_button)
        val backgroundButton = findViewById<Button>(R.id.background_button)
        val dexoptButton = findViewById<Button>(R.id.dexopt_button)

        shizukuViews = listOf(
            processButton, animOff, animHalf, animDefault, wifiScan, bleScan,
            bloatButton, backgroundButton, dexoptButton,
        )

        shizukuAction.setOnClickListener { onShizukuAction() }
        findViewById<Button>(R.id.refresh_health).setOnClickListener { refreshHealth() }
        processButton.setOnClickListener { loadProcesses() }
        animOff.setOnClickListener { setAnimationScale("0") }
        animHalf.setOnClickListener { setAnimationScale("0.5") }
        animDefault.setOnClickListener { setAnimationScale("1") }
        bloatButton.setOnClickListener { startActivity(Intent(this, BloatwareActivity::class.java)) }
        backgroundButton.setOnClickListener { startActivity(Intent(this, BackgroundActivity::class.java)) }
        dexoptButton.setOnClickListener { confirmDexopt() }
    }

    override fun onResume() {
        super.onResume()
        refreshHealth()
        Shell.addListener(stateListener)
        Shell.refresh()
    }

    override fun onPause() {
        super.onPause()
        Shell.removeListener(stateListener)
    }

    // --- Shizuku ---

    private fun render(state: Shell.State) {
        shizukuAction.visibility = View.VISIBLE
        shizukuAction.isEnabled = true
        when (state) {
            Shell.State.NOT_RUNNING -> {
                shizukuStatus.setText(R.string.shizuku_not_running)
                shizukuAction.setText(R.string.shizuku_open)
            }
            Shell.State.NO_PERMISSION -> {
                shizukuStatus.setText(R.string.shizuku_no_permission)
                shizukuAction.setText(R.string.shizuku_grant)
            }
            Shell.State.CONNECTING -> {
                shizukuStatus.setText(R.string.shizuku_connecting)
                shizukuAction.isEnabled = false
            }
            Shell.State.READY -> {
                shizukuStatus.setText(R.string.shizuku_ready)
                shizukuAction.visibility = View.GONE
                loadQuickSettings()
            }
        }
        shizukuViews.forEach { it.isEnabled = state == Shell.State.READY }
    }

    private fun onShizukuAction() {
        when (Shell.state) {
            Shell.State.NO_PERMISSION -> if (!Shell.requestPermission()) {
                Toast.makeText(this, R.string.shizuku_denied, Toast.LENGTH_LONG).show()
                openShizuku()
            }
            else -> openShizuku()
        }
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        startActivity(launch)
    }

    // --- Sağlık paneli ---

    private fun refreshHealth() {
        val h = Health.read(this)
        val ramUsed = h.ramTotal - h.ramAvailable
        ramText.text = getString(
            R.string.ram_format,
            size(ramUsed), size(h.ramTotal), size(h.ramAvailable),
        ) + if (h.lowMemory) getString(R.string.ram_low) else ""
        ramBar.progress = percent(ramUsed, h.ramTotal)

        val storageUsed = h.storageTotal - h.storageFree
        storageText.text = getString(R.string.storage_format, size(storageUsed), size(h.storageTotal), size(h.storageFree))
        storageBar.progress = percent(storageUsed, h.storageTotal)

        batteryText.text = getString(
            R.string.battery_format,
            h.batteryLevel,
            h.batteryTempC,
            getString(if (h.charging) R.string.charging else R.string.not_charging),
            DateUtils.formatElapsedTime(h.uptimeMs / 1000),
        )
    }

    private fun loadProcesses() {
        processButton.isEnabled = false
        processList.visibility = View.VISIBLE
        processList.setText(R.string.process_loading)
        background({
            parseMeminfo(Shell.exec("dumpsys meminfo | sed -n '/^Total PSS by process/,/^\$/p'").out)
        }) { processes ->
            processButton.isEnabled = Shell.state == Shell.State.READY
            processList.text = if (processes.isEmpty()) {
                getString(R.string.process_failed)
            } else {
                processes.take(10).mapIndexed { i, p ->
                    "${i + 1}. ${label(p.name)} — ${size(p.pssKb * 1024)}"
                }.joinToString("\n")
            }
        }
    }

    /** İşlem adından ("com.foo:remote") uygulama adını bulur; bulamazsa adı olduğu gibi döner. */
    private fun label(processName: String): String {
        val pkg = processName.substringBefore(':')
        return try {
            val name = packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
            if (name == pkg) processName else "$name ($processName)"
        } catch (e: PackageManager.NameNotFoundException) {
            processName
        }
    }

    // --- Hızlı ayarlar ---

    private fun loadQuickSettings() {
        background({
            Shell.exec(
                "settings get global window_animation_scale; " +
                    "settings get global wifi_scan_always_enabled; " +
                    "settings get global ble_scan_always_enabled"
            ).out.lines()
        }) { lines ->
            val anim = lines.getOrNull(0)?.trim()?.toFloatOrNull() ?: 1f
            animText.text = getString(R.string.anim_format, anim)
            bindToggle(wifiScan, lines.getOrNull(1)?.trim() == "1", "wifi_scan_always_enabled")
            bindToggle(bleScan, lines.getOrNull(2)?.trim() == "1", "ble_scan_always_enabled")
        }
    }

    private fun bindToggle(view: Switch, checked: Boolean, setting: String) {
        view.setOnCheckedChangeListener(null)
        view.isChecked = checked
        view.setOnCheckedChangeListener { _, isChecked ->
            val value = if (isChecked) 1 else 0
            background({ Shell.exec("settings put global $setting $value") }) { r ->
                if (!r.ok) showError(r.out)
                loadQuickSettings()
            }
        }
    }

    private fun setAnimationScale(scale: String) {
        background({
            Shell.exec(
                "settings put global window_animation_scale $scale && " +
                    "settings put global transition_animation_scale $scale && " +
                    "settings put global animator_duration_scale $scale"
            )
        }) { r ->
            if (!r.ok) showError(r.out)
            loadQuickSettings()
        }
    }

    // --- Araçlar ---

    private fun confirmDexopt() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dexopt)
            .setMessage(R.string.dexopt_confirm)
            .setPositiveButton(R.string.start) { _, _ -> runDexopt() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runDexopt() {
        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.dexopt)
            .setMessage(R.string.dexopt_running)
            .setCancelable(false)
            .show()
        background({ Shell.exec("cmd package bg-dexopt-job") }) { r ->
            progress.dismiss()
            AlertDialog.Builder(this)
                .setTitle(R.string.dexopt)
                .setMessage(if (r.ok) getString(R.string.dexopt_done) else r.out.ifBlank { getString(R.string.failed) })
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // --- Yardımcılar ---

    private fun showError(message: String) {
        Toast.makeText(this, message.ifBlank { getString(R.string.failed) }, Toast.LENGTH_LONG).show()
    }

    private fun size(bytes: Long) = Formatter.formatShortFileSize(this, bytes)

    private fun percent(part: Long, total: Long) = if (total > 0) (part * 100 / total).toInt() else 0
}
