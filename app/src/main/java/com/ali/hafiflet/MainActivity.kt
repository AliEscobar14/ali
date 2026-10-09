package com.ali.hafiflet

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Onay bekleyen çözünürlük değişikliği. Activity yeniden oluşturulsa da kaybolmasın diye süreç düzeyinde tutulur. */
private class PendingResolution(val title: String, val restore: String, val deadline: Long)

private var pendingResolution: PendingResolution? = null

class MainActivity : AppCompatActivity() {

    private lateinit var panel: PanelPage
    private lateinit var optimize: OptimizePage
    private lateinit var history: HistoryPage
    private lateinit var pages: Map<Int, View>
    private var currentTab = R.id.nav_panel

    private var resolutionDialog: AlertDialog? = null
    private val resolutionTick = object : Runnable {
        override fun run() = updateResolutionDialog()
    }

    private val stateListener: (Shell.State) -> Unit = { state ->
        panel.render(state)
        optimize.render(state)
        history.render(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        panel = PanelPage(this, findViewById(R.id.page_panel))
        optimize = OptimizePage(this, findViewById(R.id.page_optimize))
        history = HistoryPage(this, findViewById(R.id.page_history))
        pages = mapOf(
            R.id.nav_panel to findViewById(R.id.page_panel),
            R.id.nav_optimize to findViewById(R.id.page_optimize),
            R.id.nav_history to findViewById(R.id.page_history),
        )

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener {
            showTab(it.itemId)
            true
        }
        currentTab = savedInstanceState?.getInt(KEY_TAB, R.id.nav_panel) ?: R.id.nav_panel
        nav.selectedItemId = currentTab
        showTab(currentTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    override fun onResume() {
        super.onResume()
        Shell.addListener(stateListener)
        Shell.refresh()
        showTab(currentTab)
        showResolutionDialog()
    }

    override fun onPause() {
        super.onPause()
        Shell.removeListener(stateListener)
        panel.stopLive()
        mainHandler.removeCallbacks(resolutionTick)
        resolutionDialog?.dismiss()
        resolutionDialog = null
    }

    private fun showTab(id: Int) {
        currentTab = id
        pages.forEach { (tab, page) -> page.show(tab == id) }
        if (id == R.id.nav_panel) panel.startLive() else panel.stopLive()
        if (id == R.id.nav_history) history.refresh()
    }

    // --- Çözünürlük onayı ---

    /**
     * Komut çalıştırılmadan önce çağrılır: çözünürlük değişince bu Activity yeniden oluşturulabilir ve
     * komutun sonucu ona hiç ulaşmayabilir. Onay penceresini yeni örnek [onResume]'da gösterir.
     */
    fun beginResolutionChange(title: String, restore: String) {
        pendingResolution = PendingResolution(
            title, restore, SystemClock.elapsedRealtime() + Tweaks.CONFIRM_SECONDS * 1000L,
        )
    }

    fun onResolutionResult(ok: Boolean) {
        if (!ok) {
            pendingResolution = null
            return
        }
        // Activity yeniden oluşturulmadıysa pencereyi bu örnek gösterir.
        mainHandler.postDelayed({ if (!isDestroyed) showResolutionDialog() }, 1500)
    }

    private fun showResolutionDialog() {
        if (pendingResolution == null || resolutionDialog != null || isFinishing) return
        resolutionDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.res_confirm_title)
            .setMessage(" ")
            .setCancelable(false)
            .setPositiveButton(R.string.res_keep) { _, _ -> resolveResolution(keep = true) }
            .setNegativeButton(R.string.res_revert) { _, _ -> resolveResolution(keep = false) }
            .show()
        updateResolutionDialog()
    }

    private fun updateResolutionDialog() {
        val pending = pendingResolution ?: return
        val dialog = resolutionDialog ?: return
        val remaining = ((pending.deadline - SystemClock.elapsedRealtime()) / 1000).toInt()
        if (remaining <= 0) {
            // Shell'deki zamanlayıcı eski çözünürlüğe döndürecek.
            pendingResolution = null
            resolutionDialog = null
            dialog.dismiss()
            Toast.makeText(this, R.string.res_reverted, Toast.LENGTH_LONG).show()
            return
        }
        dialog.setMessage(getString(R.string.res_confirm_message, remaining))
        mainHandler.postDelayed(resolutionTick, 250)
    }

    private fun resolveResolution(keep: Boolean) {
        val pending = pendingResolution ?: return
        pendingResolution = null
        resolutionDialog = null
        mainHandler.removeCallbacks(resolutionTick)
        background({
            if (keep) {
                Tweaks.keepResolution()
                ChangeLog.add(this, pending.title, pending.restore)
            } else {
                Tweaks.revertResolution(pending.restore)
            }
        }) { optimize.refresh() }
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
