package com.ali.hafiflet

import android.os.Bundle
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BloatwareActivity : AppCompatActivity() {

    private enum class PkgState(val label: Int, val tone: AppRow.Tone) {
        ACTIVE(R.string.state_active, AppRow.Tone.NEUTRAL),
        DISABLED(R.string.state_disabled, AppRow.Tone.GOOD),
        REMOVED(R.string.state_removed, AppRow.Tone.GOOD),
    }

    private lateinit var list: LinearLayout
    private lateinit var progress: View
    private lateinit var removeButton: Button
    private lateinit var restoreButton: Button

    private var states = emptyMap<String, PkgState>()
    private var icons = emptyMap<String, Drawable?>()
    private val selected = mutableSetOf<String>()
    private val rows = mutableMapOf<String, AppRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.bloat_title)

        list = findViewById(R.id.list)
        progress = findViewById(R.id.progress)
        removeButton = findViewById(R.id.action_primary)
        restoreButton = findViewById(R.id.action_secondary)

        findViewById<TextView>(R.id.note).setText(R.string.bloat_note)
        findViewById<View>(R.id.actions).show(true)
        restoreButton.setText(R.string.bloat_restore)
        removeButton.setOnClickListener { confirmRemove() }
        restoreButton.setOnClickListener { restore() }
        updateButtons()

        if (Shell.state != Shell.State.READY) {
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        setBusy(true)
        background({
            val s = readStates()
            s to s.keys.associateWith { loadAppIcon(packageManager, it) }
        }) { (s, i) ->
            states = s
            icons = i
            selected.retainAll(states.keys)
            setBusy(false)
            render()
        }
    }

    /** Listedeki paketlerin bu kullanıcı için durumunu okur; cihazda hiç olmayanlar sonuçta yer almaz. */
    private fun readStates(): Map<String, PkgState> {
        val out = Shell.exec(
            "echo '#ALL'; pm list packages -u; echo '#INSTALLED'; pm list packages; echo '#DISABLED'; pm list packages -d"
        ).out
        val sections = mutableMapOf<String, MutableSet<String>>()
        var current = mutableSetOf<String>()
        for (line in out.lines().map { it.trim() }) {
            if (line.startsWith("#")) {
                current = sections.getOrPut(line) { mutableSetOf() }
            } else if (line.startsWith("package:")) {
                current += line.removePrefix("package:")
            }
        }
        val all = sections["#ALL"].orEmpty()
        val installed = sections["#INSTALLED"].orEmpty()
        val disabled = sections["#DISABLED"].orEmpty()
        return BLOATWARE.filter { it.pkg in all }.associate {
            it.pkg to when (it.pkg) {
                !in installed -> PkgState.REMOVED
                in disabled -> PkgState.DISABLED
                else -> PkgState.ACTIVE
            }
        }
    }

    private fun render() {
        list.removeAllViews()
        rows.clear()
        if (states.isEmpty()) {
            list.addView(TextView(this).apply { setText(R.string.bloat_none) })
            return
        }
        for ((safe, header) in listOf(true to R.string.bloat_safe, false to R.string.bloat_caution)) {
            val items = BLOATWARE.filter { it.safe == safe && it.pkg in states }
            if (items.isEmpty()) continue
            val active = items.filter { states[it.pkg] == PkgState.ACTIVE }.map { it.pkg }
            // "Tümünü seç" yalnızca güvenli grupta; dikkat gerektirenler tek tek seçilmeli.
            val selectAll = if (safe && active.isNotEmpty()) getString(R.string.bloat_select_all) else null
            list.addView(sectionHeader(this, list, getString(header), selectAll) {
                selected += active
                active.forEach { rows[it]?.check?.isChecked = true }
                updateButtons()
            })
            items.forEach { list.addView(row(it, states.getValue(it.pkg))) }
        }
    }

    private fun row(item: Bloat, state: PkgState): View {
        val row = AppRow(this, list)
        rows[item.pkg] = row
        row.title.text = item.name
        row.subtitle.text = item.note
        row.icon.setImageDrawable(icons[item.pkg])
        row.setBadge(state.label, state.tone)
        row.check.show(true)
        row.check.isChecked = item.pkg in selected
        row.view.setOnClickListener {
            row.check.toggle()
            if (row.check.isChecked) selected += item.pkg else selected -= item.pkg
            updateButtons()
        }
        return row.view
    }

    private fun removable() = selected.filter { states[it] == PkgState.ACTIVE }

    private fun restorable() = selected.filter { states[it] != PkgState.ACTIVE }

    private fun updateButtons() {
        val count = removable().size
        removeButton.text = if (count > 0) getString(R.string.bloat_remove_count, count) else getString(R.string.bloat_remove)
    }

    private fun confirmRemove() {
        val targets = removable()
        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.bloat_nothing_to_remove, Toast.LENGTH_SHORT).show()
            return
        }
        val names = BLOATWARE.filter { it.pkg in targets }.joinToString("\n") { "• ${it.name}" }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bloat_remove)
            .setMessage(getString(R.string.bloat_remove_confirm, names))
            .setPositiveButton(R.string.bloat_remove) { _, _ -> runAll(targets, ::remove) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restore() {
        val targets = restorable()
        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.bloat_nothing_to_restore, Toast.LENGTH_SHORT).show()
            return
        }
        val current = states
        runAll(targets) { restore(it, current.getValue(it)) }
    }

    private fun runAll(targets: List<String>, action: (String) -> Boolean) {
        setBusy(true)
        background({ targets.filterNot(action) }) { failed ->
            selected.clear()
            updateButtons()
            val ok = targets.size - failed.size
            val message = if (failed.isEmpty()) {
                getString(R.string.result_ok, ok)
            } else {
                getString(R.string.result_partial, ok, failed.size, failed.joinToString("\n") { nameOf(it) })
            }
            MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
            load()
        }
    }

    /** Önce devre dışı bırakmayı dener (MIUI bazı paketlerde engeller), olmazsa bu kullanıcı için kaldırır. */
    private fun remove(pkg: String): Boolean {
        val p = Shell.checkPackage(pkg)
        val title = getString(R.string.bloat_removed_log, nameOf(pkg))
        val disable = Shell.exec("pm disable-user --user 0 $p")
        if (disable.ok && "disabled-user" in disable.out) {
            ChangeLog.add(this, title, "pm enable --user 0 $p")
            return true
        }
        val uninstall = Shell.exec("pm uninstall -k --user 0 $p")
        if (uninstall.ok && "Success" in uninstall.out) {
            ChangeLog.add(this, title, "cmd package install-existing --user 0 $p")
            return true
        }
        return false
    }

    private fun restore(pkg: String, state: PkgState): Boolean {
        val p = Shell.checkPackage(pkg)
        val title = getString(R.string.bloat_restored_log, nameOf(pkg))
        val ok = if (state == PkgState.REMOVED) {
            val r = Shell.exec("cmd package install-existing --user 0 $p")
            r.ok && "installed" in r.out
        } else {
            val r = Shell.exec("pm enable --user 0 $p")
            r.ok && "enabled" in r.out
        }
        if (ok) {
            val undo = if (state == PkgState.REMOVED) "pm uninstall -k --user 0 $p" else "pm disable-user --user 0 $p"
            ChangeLog.add(this, title, undo)
        }
        return ok
    }

    private fun nameOf(pkg: String) = BLOATWARE.firstOrNull { it.pkg == pkg }?.name ?: pkg

    private fun setBusy(busy: Boolean) {
        progress.show(busy)
        removeButton.isEnabled = !busy
        restoreButton.isEnabled = !busy
    }
}
