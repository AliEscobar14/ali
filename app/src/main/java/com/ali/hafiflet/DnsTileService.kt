package com.ali.hafiflet

import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Bildirim panelinden reklam engelleyici DNS'i açıp kapatan kutucuk. */
class DnsTileService : TileService() {

    private var busy = false

    override fun onStartListening() {
        super.onStartListening()
        render(readDnsActive())
    }

    override fun onClick() {
        super.onClick()
        if (busy) return
        busy = true
        Shell.awaitReady(SHIZUKU_WAIT_MS) { ready ->
            if (!ready) {
                busy = false
                render(readDnsActive(), unavailable = true)
                return@awaitReady
            }
            runAsync({
                val enable = Tweaks.read().dns == Tweaks.Dns.OFF
                val target = if (enable) Tweaks.lastDns(this) else Tweaks.Dns.OFF
                Tweaks.setDns(this, target, Tweaks.dnsLabel(this, target))
                Tweaks.read().dns != Tweaks.Dns.OFF
            }) { active ->
                busy = false
                render(active)
            }
        }
    }

    /** Ayar doğrudan okunabiliyorsa okur; okunamıyorsa (yeni Android sürümleri) null döner. */
    private fun readDnsActive(): Boolean? = try {
        Settings.Global.getString(contentResolver, "private_dns_mode") == "hostname"
    } catch (e: SecurityException) {
        null
    }

    private fun render(active: Boolean?, unavailable: Boolean = false) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_dns)
        tile.state = when {
            active == true -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    unavailable -> R.string.tile_needs_shizuku
                    active == true -> R.string.on
                    else -> R.string.off
                }
            )
        }
        tile.updateTile()
    }

    private companion object {
        const val SHIZUKU_WAIT_MS = 4000L
    }
}
