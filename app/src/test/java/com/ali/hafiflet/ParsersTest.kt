package com.ali.hafiflet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    @Test
    fun display_withoutOverride() {
        val d = parseDisplay("Physical size: 720x1600\nPhysical density: 320\n")!!
        assertEquals(720, d.currentWidth)
        assertEquals(1600, d.currentHeight)
        assertEquals(320, d.currentDensity)
        assertNull(d.overrideWidth)
        assertEquals(1f, d.factor, 0.001f)
    }

    @Test
    fun display_withOverride() {
        val d = parseDisplay(
            "Physical size: 720x1600\nOverride size: 648x1440\nPhysical density: 320\nOverride density: 288\n"
        )!!
        assertEquals(648, d.currentWidth)
        assertEquals(1440, d.currentHeight)
        assertEquals(288, d.currentDensity)
        assertEquals(0.9f, d.factor, 0.001f)
    }

    @Test
    fun display_invalidOutput() {
        assertNull(parseDisplay("Error: Could not access the Window Manager"))
    }

    @Test
    fun scaledDisplay_keepsDpSizeForRedmi9A() {
        val d = DisplayInfo(720, 1600, 320, null, null, null)
        assertEquals(Triple(648, 1440, 288), scaledDisplay(d, 0.9f))
        assertEquals(Triple(576, 1280, 256), scaledDisplay(d, 0.8f))
        // dp genişliği = piksel / (dpi / 160): 720/2 = 360dp, 648/1.8 = 360dp, 576/1.6 = 360dp
        for (f in listOf(0.9f, 0.8f)) {
            val (w, _, dpi) = scaledDisplay(d, f)
            assertEquals(360f, w / (dpi / 160f), 1f)
        }
    }

    @Test
    fun scaledDisplay_alwaysEvenEdges() {
        val d = DisplayInfo(1080, 2340, 440, null, null, null)
        val (w, h, _) = scaledDisplay(d, 0.9f)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun meminfo() {
        val out = """
            Applications Memory Usage (in Kilobytes):
            Uptime: 123456 Realtime: 123456

            Total PSS by process:
                145,112K: com.android.systemui (pid 1234)
                 98,765K: com.miui.home (pid 2345 / activities)
                  1,024K: com.foo:remote (pid 3456)

            Total PSS by OOM adjustment:
                200,000K: Native
        """.trimIndent()
        val list = parseMeminfo(out)
        assertEquals(3, list.size)
        assertEquals("com.android.systemui", list[0].name)
        assertEquals(145112L, list[0].pssKb)
        assertEquals("com.miui.home", list[1].name)
        assertEquals("com.foo:remote", list[2].name)
    }

    @Test
    fun packageList() {
        assertEquals(
            listOf("com.a", "com.b"),
            parsePackageList("package:com.a\npackage:com.b\n\n"),
        )
    }

    @Test
    fun packageSections() {
        val out = "#ALL\npackage:a\npackage:b\npackage:c\n#INSTALLED\npackage:a\npackage:b\n#DISABLED\npackage:b\n"
        val s = parsePackageSections(out)
        assertEquals(setOf("a", "b", "c"), s["ALL"])
        assertEquals(setOf("a", "b"), s["INSTALLED"])
        assertEquals(setOf("b"), s["DISABLED"])
    }

    @Test
    fun packageSections_emptySection() {
        val s = parsePackageSections("#ALL\npackage:a\n#DISABLED\n")
        assertEquals(emptySet<String>(), s["DISABLED"])
    }

    @Test
    fun restricted_android10Format() {
        val dump = """
              Uid u0a120:
                Package com.restricted:
                  RUN_ANY_IN_BACKGROUND (ignore):
                Package com.allowed:
                  RUN_ANY_IN_BACKGROUND (allow):
              Uid u0a121:
                RUN_ANY_IN_BACKGROUND: mode=ignore
                Package com.uidlevel:
        """.trimIndent()
        assertEquals(setOf("com.restricted"), parseRestricted(dump))
    }

    @Test
    fun restricted_android9Format() {
        val dump = "  Uid 10120:\n    Package com.old:\n      RUN_ANY_IN_BACKGROUND: mode=ignore; time=+1h\n"
        assertEquals(setOf("com.old"), parseRestricted(dump))
    }

    @Test
    fun sizes() {
        assertEquals(512L, sizeToKb("512K"))
        assertEquals(45L * 1024, sizeToKb("45MB"))
        assertEquals(45L * 1024, sizeToKb("45M"))
        assertEquals((1.5 * 1024 * 1024).toLong(), sizeToKb("1.5GB"))
        assertNull(sizeToKb("abc"))
    }

    private val procstats = """
        AGGREGATED OVER LAST 24 HOURS:
        Summary:
          * com.android.systemui / u0a42 / v29:
                   TOTAL: 100% (98MB-104MB-112MB/92MB-97MB-105MB/141MB-148MB-157MB over 18)
              Persistent: 100% (98MB-104MB-112MB/92MB-97MB-105MB/141MB-148MB-157MB over 18)
          * com.facebook.katana / u0a150 / v123:
                   TOTAL: 40% (80MB-90MB-100MB/70MB-75MB-80MB/120MB-130MB-140MB over 10)
                     Top: 5.0% (80MB-82MB-85MB/70MB-72MB-74MB/120MB-121MB-122MB over 2)
                 Service: 30% (85MB-88MB-90MB/70MB-71MB-72MB/120MB-125MB-130MB over 6)
                (Cached): 5.0% (60MB-61MB-62MB/50MB-51MB-52MB/90MB-91MB-92MB over 2)
          * com.facebook.katana:videoplayer / u0a150 / v123:
                   TOTAL: 10% (40MB-45MB-50MB/30MB-35MB-40MB/60MB-65MB-70MB over 3)
                Receiver: 10% (40MB-45MB-50MB/30MB-35MB-40MB/60MB-65MB-70MB over 3)
          * com.whatsapp / u0a160 / v1:
                   TOTAL: 12% (50MB-55MB-60MB/40MB-45MB-50MB/70MB-75MB-80MB over 4)
                 Service: 12% (50MB-55MB-60MB/40MB-45MB-50MB/70MB-75MB-80MB over 4)
          * com.cached.only / u0a170 / v1:
                   TOTAL: 50% (10MB-20MB-30MB/5MB-10MB-15MB/20MB-30MB-40MB over 5)
                (Cached): 50% (10MB-20MB-30MB/5MB-10MB-15MB/20MB-30MB-40MB over 5)
          * system / 1000 / v29:
                   TOTAL: 100% (200MB-210MB-220MB/190MB-195MB-200MB/250MB-260MB-270MB over 18)
                 Service: 100% (200MB-210MB-220MB/190MB-195MB-200MB/250MB-260MB-270MB over 18)

        Run time Stats:
          SOff/Norm: +1h0m0s
    """.trimIndent()

    @Test
    fun procstats_ranksBackgroundMemory() {
        val hogs = parseProcstats(procstats)
        // systemui (yalnızca Persistent), sadece önbellekte duran ve sistem (uid 1000) sayılmaz.
        assertEquals(listOf("com.facebook.katana", "com.whatsapp"), hogs.map { it.pkg })

        val facebook = hogs[0]
        // Ana işlem: %30 × 90MB = 27MB; videoplayer: %10 × 45MB = 4.5MB  → 31.5MB
        assertEquals(31.5 * 1024, facebook.averageKb.toDouble(), 1.0)
        assertEquals(30.0, facebook.backgroundPercent, 0.01)

        val whatsapp = hogs[1]
        assertEquals(0.12 * 55 * 1024, whatsapp.averageKb.toDouble(), 1.0)
    }

    @Test
    fun procstats_missingSummary() {
        assertTrue(parseProcstats("Run time Stats:\n").isEmpty())
    }

    @Test
    fun procstats_totalWithoutMemoryIsIgnored() {
        val out = "Summary:\n  * com.x / u0a1 / v1:\n           TOTAL: 10%\n         Service: 10%\n"
        assertTrue(parseProcstats(out).isEmpty())
        assertNotNull(parseProcstats(out))
    }
}
