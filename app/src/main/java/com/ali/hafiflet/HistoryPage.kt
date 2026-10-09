package com.ali.hafiflet

import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Geçmiş sekmesi: yapılan değişiklikler ve geri alma. */
class HistoryPage(private val activity: MainActivity, root: View) {

    private val list: LinearLayout = root.findViewById(R.id.history_list)
    private val empty: View = root.findViewById(R.id.history_empty)
    private val undoAll: Button = root.findViewById(R.id.undo_all)
    private var busy = false

    init {
        undoAll.setOnClickListener { confirmUndoAll() }
    }

    fun render(state: Shell.State) = refresh()

    fun refresh() {
        val changes = ChangeLog.all(activity)
        val ready = Shell.state == Shell.State.READY && !busy
        list.removeAllViews()
        empty.show(changes.isEmpty())
        undoAll.isEnabled = ready && changes.any { it.canUndo }
        val now = System.currentTimeMillis()
        for (change in changes) {
            val row = activity.layoutInflater.inflate(R.layout.item_history, list, false)
            row.findViewById<TextView>(R.id.history_item_title).text = change.title
            row.findViewById<TextView>(R.id.history_item_time).text =
                DateUtils.getRelativeTimeSpanString(change.time, now, DateUtils.MINUTE_IN_MILLIS)
            val undo = row.findViewById<Button>(R.id.history_item_undo)
            undo.show(change.canUndo)
            undo.isEnabled = ready
            undo.setOnClickListener { undo(listOf(change)) }
            row.findViewById<View>(R.id.history_item_done).show(change.undone)
            list.addView(row)
        }
    }

    private fun confirmUndoAll() {
        val changes = ChangeLog.all(activity).filter { it.canUndo }
        if (changes.isEmpty()) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.history_undo_all)
            .setMessage(activity.getString(R.string.history_undo_all_confirm, changes.size))
            .setPositiveButton(R.string.history_undo_all) { _, _ -> undo(changes) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Değişiklikleri verilen sırayla (en yeniden eskiye) geri alır. */
    private fun undo(changes: List<Change>) {
        busy = true
        refresh()
        activity.background({
            changes.count { change ->
                val ok = Shell.exec(change.undo ?: return@count false).ok
                if (ok) ChangeLog.markUndone(activity, change.id)
                !ok
            }
        }) { failed ->
            busy = false
            refresh()
            if (changes.size > 1 || failed > 0) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.history_undo_all_result, changes.size - failed, failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
