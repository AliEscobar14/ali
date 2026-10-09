package com.ali.hafiflet

import kotlin.math.roundToInt

/*
 * Shell komutlarının çıktısını ayrıştıran saf fonksiyonlar. Android API'si kullanmazlar;
 * böylece bilgisayarda birim testleriyle doğrulanabilirler (bkz. ParsersTest).
 */

class DisplayInfo(
    val width: Int, val height: Int, val density: Int,
    val overrideWidth: Int?, val overrideHeight: Int?, val overrideDensity: Int?,
) {
    val currentWidth get() = overrideWidth ?: width
    val currentHeight get() = overrideHeight ?: height
    val currentDensity get() = overrideDensity ?: density
    val factor get() = currentWidth.toFloat() / width
}

/**
 * Çözünürlüğü ve yoğunluğu aynı oranda küçültür; dp cinsinden ekran boyutu (yani yazı ve simge
 * boyutları) değişmez. Kenarlar çift sayıya yuvarlanır.
 */
fun scaledDisplay(d: DisplayInfo, factor: Float): Triple<Int, Int, Int> = Triple(
    (d.width * factor / 2).roundToInt() * 2,
    (d.height * factor / 2).roundToInt() * 2,
    (d.density * factor).roundToInt(),
)

/** "wm size; wm density" çıktısı. */
fun parseDisplay(out: String): DisplayInfo? {
    fun size(label: String) = Regex("$label size: (\\d+)x(\\d+)").find(out)?.groupValues
    fun density(label: String) = Regex("$label density: (\\d+)").find(out)?.groupValues?.get(1)?.toInt()
    val physical = size("Physical") ?: return null
    val physicalDensity = density("Physical") ?: return null
    val override = size("Override")
    return DisplayInfo(
        physical[1].toInt(), physical[2].toInt(), physicalDensity,
        override?.get(1)?.toInt(), override?.get(2)?.toInt(), density("Override"),
    )
}

class ProcessMemory(val name: String, val pssKb: Long)

/** "dumpsys meminfo" çıktısındaki "Total PSS by process" bölümü (büyükten küçüğe sıralıdır). */
fun parseMeminfo(output: String): List<ProcessMemory> {
    val line = Regex("""^\s*([\d,]+)K: (\S+) \(pid \d+""")
    val result = mutableListOf<ProcessMemory>()
    var inSection = false
    for (raw in output.lines()) {
        if (raw.startsWith("Total PSS by process")) {
            inSection = true
            continue
        }
        if (!inSection) continue
        if (raw.isBlank()) break
        val match = line.find(raw) ?: continue
        val kb = match.groupValues[1].replace(",", "").toLongOrNull() ?: continue
        result += ProcessMemory(match.groupValues[2], kb)
    }
    return result
}

/** "pm list packages" çıktısındaki paket adları. */
fun parsePackageList(out: String): List<String> =
    out.lines().map { it.trim() }.filter { it.startsWith("package:") }.map { it.removePrefix("package:") }

/** "#BAŞLIK" satırlarıyla bölünmüş birden çok "pm list packages" çıktısı; başlık -> paketler. */
fun parsePackageSections(out: String): Map<String, Set<String>> {
    val sections = mutableMapOf<String, MutableSet<String>>()
    var current: MutableSet<String>? = null
    for (line in out.lines().map { it.trim() }) {
        when {
            line.startsWith("#") -> current = sections.getOrPut(line.removePrefix("#")) { mutableSetOf() }
            line.startsWith("package:") -> current?.add(line.removePrefix("package:"))
        }
    }
    return sections
}

/**
 * "dumpsys appops | grep" çıktısında RUN_ANY_IN_BACKGROUND'u "ignore" olan paketler.
 * Android 10+: "RUN_ANY_IN_BACKGROUND (ignore):", Android 9: "RUN_ANY_IN_BACKGROUND: mode=ignore".
 */
fun parseRestricted(dump: String): Set<String> {
    val result = mutableSetOf<String>()
    var current: String? = null
    for (raw in dump.lines()) {
        val line = raw.trim()
        when {
            line.startsWith("Uid ") -> current = null
            line.startsWith("Package ") -> current = line.removePrefix("Package ").removeSuffix(":")
            "RUN_ANY_IN_BACKGROUND" in line && "ignore" in line -> current?.let { result += it }
        }
    }
    return result
}

/** Bir paketin son 24 saatte arka planda tuttuğu bellek. */
class BackgroundHog(
    val pkg: String,
    /** Arka plan durumlarında geçen sürenin, en uzun çalışan işlemindeki oranı (0-100). */
    val backgroundPercent: Double,
    /** Gün boyunca arka planda ortalama tutulan bellek (KB) = Σ işlem (arka plan oranı × ortalama PSS). */
    val averageKb: Long,
)

/**
 * "dumpsys procstats --hours 24" çıktısının "Summary:" bölümü. Örnek:
 *
 *   * com.facebook.katana / u0a150 / v123:
 *            TOTAL: 40% (80MB-90MB-100MB/70MB-75MB-80MB/... over 10)
 *          Service: 30% (85MB-88MB-90MB/...)
 *
 * Yalnızca uygulama kullanıcılarına (u0aNN) ait işlemler sayılır. "Cached" ve "Top" gibi
 * durumlar sayılmaz: önbellekteki işlem gerektiğinde anında kapatılır, ön planda olan da kullanıcının açtığıdır.
 */
fun parseProcstats(out: String): List<BackgroundHog> {
    val header = Regex("""^\s*\* (\S+) / (\S+) / v\S*:$""")
    val total = Regex("""^\s*TOTAL: ([\d.]+)% \(([\d.]+[KMG]B?)-([\d.]+[KMG]B?)-""")
    val state = Regex("""^\s*(Imp Bg|Service|Service Rs|Receiver|Backup|Heavy Wgt): ([\d.]+)%""")

    class Proc(val pkg: String) {
        var avgKb = 0L
        var bgPercent = 0.0
    }

    val procs = mutableListOf<Proc>()
    var current: Proc? = null
    var inSummary = false
    for (line in out.lines()) {
        if (line.startsWith("Summary:")) {
            inSummary = true
            continue
        }
        if (!inSummary) continue
        if (line.isNotEmpty() && !line[0].isWhitespace()) break
        val h = header.find(line)
        if (h != null) {
            val isApp = h.groupValues[2].startsWith("u0a")
            current = if (isApp) Proc(h.groupValues[1].substringBefore(':')).also { procs += it } else null
            continue
        }
        val proc = current ?: continue
        total.find(line)?.let { proc.avgKb = sizeToKb(it.groupValues[3]) ?: 0 }
        state.find(line)?.let { proc.bgPercent += it.groupValues[2].toDouble() }
    }

    return procs.filter { it.bgPercent > 0 && it.avgKb > 0 }
        .groupBy { it.pkg }
        .map { (pkg, list) ->
            BackgroundHog(
                pkg,
                list.maxOf { it.bgPercent }.coerceAtMost(100.0),
                list.sumOf { it.avgKb * it.bgPercent / 100 }.toLong(),
            )
        }
        .sortedByDescending { it.averageKb }
}

/** "512K", "45MB", "45M", "1.2GB" gibi değerleri KB'a çevirir. */
fun sizeToKb(value: String): Long? {
    val m = Regex("""^([\d.]+)([KMG])B?$""").find(value) ?: return null
    val number = m.groupValues[1].toDoubleOrNull() ?: return null
    val multiplier = when (m.groupValues[2]) {
        "K" -> 1.0
        "M" -> 1024.0
        else -> 1024.0 * 1024.0
    }
    return (number * multiplier).toLong()
}
