package com.ali.hafiflet

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import kotlin.math.roundToInt

/**
 * Sistem ayarlarını okuyan ve değiştiren işlemler. Hepsi engelleyicidir; arka planda çağrılmalı.
 * Başarılı her değişiklik, geri alma komutuyla birlikte [ChangeLog]'a yazılır.
 */
object Tweaks {

    private val ANIMATION_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    private const val KEEP_MARKER = "/data/local/tmp/hafiflet_keep"

    /** Çözünürlük değişikliği onaylanmazsa shell tarafında bu kadar saniye sonra geri alınır. */
    const val REVERT_SECONDS = 20

    /** Kullanıcıya gösterilen süre; shell'deki zamanlayıcıdan kısa tutulur ki "Koru" her zaman yetişsin. */
    const val CONFIRM_SECONDS = 15

    enum class Dns(val host: String?) {
        OFF(null),
        ADGUARD("dns.adguard-dns.com"),
        FAMILY("family.adguard-dns.com"),
    }

    class Display(
        val width: Int, val height: Int, val density: Int,
        val overrideWidth: Int?, val overrideHeight: Int?, val overrideDensity: Int?,
    ) {
        val currentWidth get() = overrideWidth ?: width
        val currentHeight get() = overrideHeight ?: height
        val currentDensity get() = overrideDensity ?: density
        val factor get() = currentWidth.toFloat() / width
    }

    class State(
        val animationScale: Float,
        val wifiScan: Boolean,
        val bleScan: Boolean,
        /** null: kullanıcı başka bir özel DNS ayarlamış. */
        val dns: Dns?,
        val display: Display?,
    )

    fun read(): State {
        val (anim, wifi, ble, dnsMode, dnsHost) = getGlobal(
            listOf(ANIMATION_KEYS[0], "wifi_scan_always_enabled", "ble_scan_always_enabled",
                "private_dns_mode", "private_dns_specifier")
        )
        val dns = when {
            dnsMode != "hostname" -> Dns.OFF
            else -> Dns.entries.firstOrNull { it.host == dnsHost }
        }
        return State(anim?.toFloatOrNull() ?: 1f, wifi == "1", ble == "1", dns, readDisplay())
    }

    // --- Ayarlar ---

    fun setAnimationScale(context: Context, scale: String, label: String): Shell.Result {
        val previous = getGlobal(ANIMATION_KEYS)
        val result = Shell.exec(ANIMATION_KEYS.joinToString(" && ") { "settings put global $it $scale" })
        if (result.ok) {
            ChangeLog.add(
                context,
                context.getString(R.string.anim_title) + ": " + label,
                ANIMATION_KEYS.zip(previous).joinToString("; ") { (key, value) -> restoreGlobal(key, value) },
            )
        }
        return result
    }

    fun setGlobalSwitch(context: Context, key: String, enabled: Boolean, title: String): Shell.Result {
        val previous = getGlobal(listOf(key))[0]
        val result = Shell.exec("settings put global $key ${if (enabled) 1 else 0}")
        if (result.ok) ChangeLog.add(context, title, restoreGlobal(key, previous))
        return result
    }

    fun setDns(context: Context, dns: Dns, label: String): Shell.Result {
        val (mode, host) = getGlobal(listOf("private_dns_mode", "private_dns_specifier"))
        val command = if (dns.host == null) {
            "settings put global private_dns_mode opportunistic"
        } else {
            "settings put global private_dns_specifier ${dns.host} && settings put global private_dns_mode hostname"
        }
        val result = Shell.exec(command)
        if (result.ok) {
            ChangeLog.add(
                context,
                context.getString(R.string.dns_log, label),
                restoreGlobal("private_dns_specifier", host) + "; " + restoreGlobal("private_dns_mode", mode),
            )
        }
        return result
    }

    // --- Çözünürlük ---

    private fun readDisplay(): Display? {
        val out = Shell.exec("wm size; wm density").out
        fun size(label: String) = Regex("$label size: (\\d+)x(\\d+)").find(out)?.groupValues
        fun density(label: String) = Regex("$label density: (\\d+)").find(out)?.groupValues?.get(1)?.toInt()
        val physical = size("Physical") ?: return null
        val physicalDensity = density("Physical") ?: return null
        val override = size("Override")
        return Display(
            physical[1].toInt(), physical[2].toInt(), physicalDensity,
            override?.get(1)?.toInt(), override?.get(2)?.toInt(), density("Override"),
        )
    }

    /** Ekranı mevcut haline döndüren komut. */
    fun displayRestoreCommand(d: Display): String {
        val size = if (d.overrideWidth != null) "wm size ${d.overrideWidth}x${d.overrideHeight}" else "wm size reset"
        val density = if (d.overrideDensity != null) "wm density ${d.overrideDensity}" else "wm density reset"
        return "$size; $density"
    }

    /**
     * Çözünürlüğü ve yoğunluğu aynı oranda küçültür; böylece arayüz öğelerinin boyutu değişmez.
     * Güvenlik için shell'de bir zamanlayıcı kurar: [keepResolution] çağrılmazsa eski haline döner.
     */
    fun applyResolution(d: Display, factor: Float): Boolean {
        val restore = displayRestoreCommand(d)
        val target = if (factor >= 0.99f) {
            "wm size reset; wm density reset"
        } else {
            val w = even(d.width * factor)
            val h = even(d.height * factor)
            "wm size ${w}x$h; wm density ${(d.density * factor).roundToInt()}"
        }
        val result = Shell.exec(
            "rm -f $KEEP_MARKER; $target; " +
                "(sleep $REVERT_SECONDS; [ -f $KEEP_MARKER ] || { $restore; }) >/dev/null 2>&1 &"
        )
        return result.ok
    }

    fun keepResolution() = Shell.exec("touch $KEEP_MARKER")

    fun revertResolution(restore: String) = Shell.exec("touch $KEEP_MARKER; $restore")

    private fun even(value: Float) = (value / 2).roundToInt() * 2

    // --- Depolama ve hız ---

    /** Tüm uygulama önbelleklerini temizler; boşalan bayt sayısını ya da hata durumunda null döner. */
    fun trimCaches(context: Context): Long? {
        val before = freeBytes()
        val result = Shell.exec("pm trim-caches 999G")
        if (!result.ok) return null
        val freed = (freeBytes() - before).coerceAtLeast(0)
        ChangeLog.add(context, context.getString(R.string.cache_log, Formatter.formatShortFileSize(context, freed)), null)
        return freed
    }

    fun dexopt(context: Context): Shell.Result {
        val result = Shell.exec("cmd package bg-dexopt-job")
        if (result.ok) ChangeLog.add(context, context.getString(R.string.dexopt_log), null)
        return result
    }

    private fun freeBytes() = StatFs(Environment.getDataDirectory().path).availableBytes

    // --- Yardımcılar ---

    /** "settings get global" değerlerini sırayla döner; ayarlanmamışsa null. */
    private fun getGlobal(keys: List<String>): List<String?> {
        val lines = Shell.exec(keys.joinToString("; ") { "settings get global $it" }).out.lines()
        return keys.indices.map { i -> lines.getOrNull(i)?.trim()?.takeUnless { it.isEmpty() || it == "null" } }
    }

    private fun restoreGlobal(key: String, value: String?): String {
        if (value == null) return "settings delete global $key"
        require(value.none { it == '\'' }) { "Geçersiz ayar değeri" }
        return "settings put global $key '$value'"
    }
}
