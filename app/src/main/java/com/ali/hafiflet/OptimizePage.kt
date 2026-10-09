package com.ali.hafiflet

import android.content.Intent
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.abs

/** Optimize sekmesi: ekran, DNS, arka plan, depolama ve hız ayarları. */
class OptimizePage(private val activity: MainActivity, root: View) {

    private val banner: View = root.findViewById(R.id.shizuku_banner)
    private val animGroup: MaterialButtonToggleGroup = root.findViewById(R.id.anim_group)
    private val resGroup: MaterialButtonToggleGroup = root.findViewById(R.id.res_group)
    private val resCurrent: TextView = root.findViewById(R.id.res_current)
    private val dnsGroup: MaterialButtonToggleGroup = root.findViewById(R.id.dns_group)
    private val dnsCustom: View = root.findViewById(R.id.dns_custom)
    private val wifiScan: MaterialSwitch = root.findViewById(R.id.wifi_scan)
    private val bleScan: MaterialSwitch = root.findViewById(R.id.ble_scan)
    private val cacheButton: Button = root.findViewById(R.id.cache_button)
    private val cacheResult: TextView = root.findViewById(R.id.cache_result)
    private val dexoptButton: Button = root.findViewById(R.id.dexopt_button)
    private val backgroundRow: View = root.findViewById(R.id.background_row)
    private val bloatRow: View = root.findViewById(R.id.bloat_row)

    private val controls = listOf(animGroup, resGroup, dnsGroup, wifiScan, bleScan, cacheButton, dexoptButton, backgroundRow, bloatRow)

    /** Ekrandaki seçimleri koddan güncellerken dinleyicilerin tetiklenmemesi için. */
    private var binding = false
    private var state: Tweaks.State? = null

    init {
        setupNavRow(backgroundRow, R.string.background_title, R.string.background_hint, BackgroundActivity::class.java)
        setupNavRow(bloatRow, R.string.bloat_title, R.string.bloat_hint, BloatwareActivity::class.java)

        animGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || binding) return@addOnButtonCheckedListener
            val (scale, label) = when (id) {
                R.id.anim_off -> "0" to activity.getString(R.string.anim_off)
                R.id.anim_half -> "0.5" to activity.getString(R.string.anim_half)
                else -> "1" to activity.getString(R.string.anim_default)
            }
            change { Tweaks.setAnimationScale(activity, scale, label) }
        }

        resGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || binding) return@addOnButtonCheckedListener
            val display = state?.display ?: return@addOnButtonCheckedListener
            val (factor, label) = when (id) {
                R.id.res_90 -> 0.9f to activity.getString(R.string.res_90)
                R.id.res_80 -> 0.8f to activity.getString(R.string.res_80)
                else -> 1f to activity.getString(R.string.res_original)
            }
            if (abs(display.factor - factor) < 0.02f) return@addOnButtonCheckedListener
            setEnabled(false)
            activity.beginResolutionChange(activity.getString(R.string.res_log, label), Tweaks.displayRestoreCommand(display))
            activity.background({ Tweaks.applyResolution(display, factor) }) { ok ->
                activity.onResolutionResult(ok)
                if (!ok) {
                    Toast.makeText(activity, R.string.failed, Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }

        dnsGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || binding) return@addOnButtonCheckedListener
            val (dns, label) = when (id) {
                R.id.dns_adguard -> Tweaks.Dns.ADGUARD to activity.getString(R.string.dns_adguard)
                R.id.dns_family -> Tweaks.Dns.FAMILY to activity.getString(R.string.dns_family)
                else -> Tweaks.Dns.OFF to activity.getString(R.string.dns_off)
            }
            change { Tweaks.setDns(activity, dns, label) }
        }

        bindSwitch(wifiScan, "wifi_scan_always_enabled", R.string.wifi_scan_log)
        bindSwitch(bleScan, "ble_scan_always_enabled", R.string.ble_scan_log)

        cacheButton.setOnClickListener { trimCaches() }
        dexoptButton.setOnClickListener { confirmDexopt() }
    }

    fun render(shellState: Shell.State) {
        val ready = shellState == Shell.State.READY
        banner.show(shellState != Shell.State.READY && shellState != Shell.State.CONNECTING)
        setEnabled(ready)
        if (ready) refresh()
    }

    fun refresh() {
        if (Shell.state != Shell.State.READY) return
        activity.background({ Tweaks.read() }) { s ->
            state = s
            bind(s)
            setEnabled(true)
        }
    }

    private fun bind(s: Tweaks.State) {
        binding = true
        animGroup.check(
            when {
                s.animationScale < 0.25f -> R.id.anim_off
                s.animationScale < 0.75f -> R.id.anim_half
                else -> R.id.anim_default
            }
        )

        val d = s.display
        if (d == null) {
            resCurrent.setText(R.string.res_unknown)
        } else {
            resCurrent.text = activity.getString(R.string.res_current, d.currentWidth, d.currentHeight, d.currentDensity)
            val option = listOf(R.id.res_100 to 1f, R.id.res_90 to 0.9f, R.id.res_80 to 0.8f)
                .firstOrNull { abs(d.factor - it.second) < 0.02f }
            if (option != null) resGroup.check(option.first) else resGroup.clearChecked()
        }

        when (s.dns) {
            Tweaks.Dns.OFF -> dnsGroup.check(R.id.dns_off)
            Tweaks.Dns.ADGUARD -> dnsGroup.check(R.id.dns_adguard)
            Tweaks.Dns.FAMILY -> dnsGroup.check(R.id.dns_family)
            null -> dnsGroup.clearChecked()
        }
        dnsCustom.show(s.dns == null)

        wifiScan.isChecked = s.wifiScan
        bleScan.isChecked = s.bleScan
        binding = false
    }

    private fun bindSwitch(view: MaterialSwitch, key: String, logTitle: Int) {
        view.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            val title = activity.getString(logTitle, activity.getString(if (checked) R.string.on else R.string.off))
            change { Tweaks.setGlobalSwitch(activity, key, checked, title) }
        }
    }

    /** Değişikliği arka planda uygular, hata olursa bildirir ve ekranı gerçek durumla yeniler. */
    private fun change(work: () -> Shell.Result) {
        setEnabled(false)
        activity.background(work) { result ->
            if (!result.ok) {
                Toast.makeText(activity, result.out.ifBlank { activity.getString(R.string.failed) }, Toast.LENGTH_LONG).show()
            }
            refresh()
        }
    }

    private fun setEnabled(enabled: Boolean) {
        controls.forEach { it.isEnabled = enabled }
        // Toggle grubunu devre dışı bırakmak düğmelerini kapatmaz; tek tek ayarlanmalı.
        val hasDisplay = state?.display != null
        for ((group, allowed) in listOf(animGroup to true, resGroup to hasDisplay, dnsGroup to true)) {
            for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = enabled && allowed
        }
    }

    private fun setupNavRow(row: View, title: Int, subtitle: Int, target: Class<*>) {
        row.findViewById<TextView>(R.id.nav_title).setText(title)
        row.findViewById<TextView>(R.id.nav_subtitle).setText(subtitle)
        row.setOnClickListener { activity.startActivity(Intent(activity, target)) }
    }

    private fun trimCaches() {
        cacheButton.isEnabled = false
        cacheResult.setText(R.string.cache_running)
        activity.background({ Tweaks.trimCaches(activity) }) { freed ->
            cacheButton.isEnabled = Shell.state == Shell.State.READY
            cacheResult.text = when {
                freed == null -> activity.getString(R.string.failed)
                freed < 1024 * 1024 -> activity.getString(R.string.cache_nothing)
                else -> activity.getString(R.string.cache_freed, Formatter.formatShortFileSize(activity, freed))
            }
        }
    }

    private fun confirmDexopt() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dexopt)
            .setMessage(R.string.dexopt_confirm)
            .setPositiveButton(R.string.start) { _, _ -> runDexopt() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runDexopt() {
        val indicator = LinearProgressIndicator(activity).apply {
            isIndeterminate = true
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dexopt)
            .setMessage(R.string.dexopt_running)
            .setView(indicator)
            .setCancelable(false)
            .show()
        activity.background({ Tweaks.dexopt(activity) }) { result ->
            progress.dismiss()
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dexopt)
                .setMessage(
                    if (result.ok) activity.getString(R.string.dexopt_done)
                    else result.out.ifBlank { activity.getString(R.string.failed) }
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
