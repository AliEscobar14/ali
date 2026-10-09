package com.ali.hafiflet

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class BloatwareActivity : Activity() {

    private enum class PkgState(val label: Int, val color: Int) {
        ACTIVE(R.string.state_active, Color.rgb(0xD9, 0x82, 0x2B)),
        DISABLED(R.string.state_disabled, Color.rgb(0x2E, 0x9E, 0x5B)),
        REMOVED(R.string.state_removed, Color.rgb(0x2E, 0x9E, 0x5B)),
    }

    private lateinit var list: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var removeButton: Button
    private lateinit var restoreButton: Button

    private var states = emptyMap<String, PkgState>()
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        list = findViewById(R.id.list)
        progress = findViewById(R.id.progress)
        removeButton = findViewById(R.id.action_primary)
        restoreButton = findViewById(R.id.action_secondary)

        findViewById<TextView>(R.id.note).setText(R.string.bloat_note)
        findViewById<View>(R.id.actions).visibility = View.VISIBLE
        removeButton.setText(R.string.bloat_remove)
        restoreButton.setText(R.string.bloat_restore)
        removeButton.setOnClickListener { confirmRemove() }
        restoreButton.setOnClickListener { restore() }

        load()
    }

    private fun load() {
        if (Shell.state != Shell.State.READY) {
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setBusy(true)
        background({ readStates() }) {
            states = it
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
        if (states.isEmpty()) {
            list.addView(TextView(this).apply { setText(R.string.bloat_none) })
            return
        }
        for ((safe, header) in listOf(true to R.string.bloat_safe, false to R.string.bloat_caution)) {
            val items = BLOATWARE.filter { it.safe == safe && it.pkg in states }
            if (items.isEmpty()) continue
            list.addView(sectionHeader(this, getString(header)))
            items.forEach { list.addView(row(it, states.getValue(it.pkg))) }
        }
    }

    private fun row(item: Bloat, state: PkgState): View {
        val view = layoutInflater.inflate(R.layout.row_app, list, false)
        view.findViewById<TextView>(R.id.title).text = item.name
        view.findViewById<TextView>(R.id.subtitle).text = getString(R.string.row_subtitle, item.note, item.pkg)
        view.findViewById<TextView>(R.id.badge).apply {
            setText(state.label)
            setTextColor(state.color)
        }
        val check = view.findViewById<CheckBox>(R.id.check)
        check.isChecked = item.pkg in selected
        check.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected += item.pkg else selected -= item.pkg
        }
        view.setOnClickListener { check.toggle() }
        return view
    }

    private fun confirmRemove() {
        val targets = selected.filter { states[it] == PkgState.ACTIVE }
        if (targets.isEmpty()) {
            Toast.makeText(this, R.string.bloat_nothing_to_remove, Toast.LENGTH_SHORT).show()
            return
        }
        val names = BLOATWARE.filter { it.pkg in targets }.joinToString("\n") { "• ${it.name}" }
        AlertDialog.Builder(this)
            .setTitle(R.string.bloat_remove)
            .setMessage(getString(R.string.bloat_remove_confirm, names))
            .setPositiveButton(R.string.bloat_remove) { _, _ -> runAll(targets, ::remove) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restore() {
        val targets = selected.filter { states[it] != PkgState.ACTIVE }
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
            val ok = targets.size - failed.size
            val message = if (failed.isEmpty()) {
                getString(R.string.result_ok, ok)
            } else {
                getString(R.string.result_partial, ok, failed.size, failed.joinToString("\n"))
            }
            AlertDialog.Builder(this).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
            load()
        }
    }

    /** Önce devre dışı bırakmayı dener (MIUI bazı paketlerde engeller), olmazsa bu kullanıcı için kaldırır. */
    private fun remove(pkg: String): Boolean {
        val p = Shell.checkPackage(pkg)
        val disable = Shell.exec("pm disable-user --user 0 $p")
        if (disable.ok && "disabled-user" in disable.out) return true
        val uninstall = Shell.exec("pm uninstall -k --user 0 $p")
        return uninstall.ok && "Success" in uninstall.out
    }

    private fun restore(pkg: String, state: PkgState): Boolean {
        val p = Shell.checkPackage(pkg)
        return if (state == PkgState.REMOVED) {
            val r = Shell.exec("cmd package install-existing --user 0 $p")
            r.ok && "installed" in r.out
        } else {
            val r = Shell.exec("pm enable --user 0 $p")
            r.ok && "enabled" in r.out
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        removeButton.isEnabled = !busy
        restoreButton.isEnabled = !busy
    }
}
