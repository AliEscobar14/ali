package com.ali.hafiflet

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class BackgroundActivity : AppCompatActivity() {

    private class AppItem(val pkg: String, val label: String, val icon: Drawable?, var restricted: Boolean)

    private lateinit var list: LinearLayout
    private lateinit var progress: View
    private val rows = mutableListOf<Pair<AppItem, View>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.background_title)

        list = findViewById(R.id.list)
        progress = findViewById(R.id.progress)
        findViewById<TextView>(R.id.note).setText(R.string.background_note)

        findViewById<View>(R.id.search_layout).show(true)
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
        })

        if (Shell.state != Shell.State.READY) {
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        progress.show(true)
        background({ loadApps() }) { apps ->
            progress.show(false)
            apps.forEach { item ->
                val view = row(item)
                rows += item to view
                list.addView(view)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        rows.forEach { (item, view) ->
            view.show(q.isEmpty() || q in item.label.lowercase() || q in item.pkg)
        }
    }

    private fun loadApps(): List<AppItem> {
        val restricted = Tweaks.restrictedPackages()
        return Tweaks.userPackages(this)
            .map { AppItem(it, label(it), loadAppIcon(packageManager, it), it in restricted) }
            .sortedBy { it.label.lowercase() }
    }

    private fun label(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun row(item: AppItem): View {
        val row = AppRow(this, list)
        row.title.text = item.label
        row.icon.setImageDrawable(item.icon)
        val isMessaging = item.pkg in MESSAGING_APPS
        row.subtitle.text = if (isMessaging) getString(R.string.messaging_warning) else item.pkg
        row.toggle.show(true)

        fun bind() {
            row.toggle.isChecked = item.restricted
            row.setBadge(if (item.restricted) R.string.state_restricted else null, AppRow.Tone.GOOD)
        }
        bind()
        row.view.setOnClickListener {
            row.view.isEnabled = false
            setRestricted(item, !item.restricted) {
                row.view.isEnabled = true
                bind()
            }
        }
        return row.view
    }

    private fun setRestricted(item: AppItem, restrict: Boolean, done: () -> Unit) {
        background({ Tweaks.setBackgroundRestricted(this, item.pkg, item.label, restrict) }) { r ->
            if (r.ok) item.restricted = restrict
            else Toast.makeText(this, r.out.ifBlank { getString(R.string.failed) }, Toast.LENGTH_LONG).show()
            done()
        }
    }
}
