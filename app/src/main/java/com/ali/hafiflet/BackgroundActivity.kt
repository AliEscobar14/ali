package com.ali.hafiflet

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class BackgroundActivity : Activity() {

    private class AppItem(val pkg: String, val label: String, var restricted: Boolean)

    /** Kısıtlanırsa bildirimleri gecikebilecek uygulamalar. */
    private val messaging = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.facebook.mlite", "com.instagram.android", "com.discord",
        "com.viber.voip", "com.skype.raider", "com.microsoft.teams", "com.google.android.apps.messaging",
    )

    private lateinit var list: LinearLayout
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        list = findViewById(R.id.list)
        progress = findViewById(R.id.progress)
        findViewById<TextView>(R.id.note).setText(R.string.background_note)

        if (Shell.state != Shell.State.READY) {
            Toast.makeText(this, R.string.shizuku_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        progress.visibility = View.VISIBLE
        background({ loadApps() }) { apps ->
            progress.visibility = View.GONE
            apps.forEach { list.addView(row(it)) }
        }
    }

    private fun loadApps(): List<AppItem> {
        val packages = Shell.exec("pm list packages -3").out.lines()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it != packageName }
        // Tek seferde tüm paketlerin durumunu okumak, her paket için ayrı komut çalıştırmaktan çok daha hızlı.
        val dump = Shell.exec("dumpsys appops | grep -E '^ *(Uid|Package) |RUN_ANY_IN_BACKGROUND'").out
        val restricted = parseRestricted(dump)
        return packages
            .map { AppItem(it, label(it), it in restricted) }
            .sortedBy { it.label.lowercase() }
    }

    /** "Package x:" satırlarından sonra gelen "RUN_ANY_IN_BACKGROUND ... ignore" satırlarını eşleştirir. */
    private fun parseRestricted(dump: String): Set<String> {
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

    private fun label(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun row(item: AppItem): View {
        val view = layoutInflater.inflate(R.layout.row_app, list, false)
        val badge = view.findViewById<TextView>(R.id.badge)
        val check = view.findViewById<CheckBox>(R.id.check)
        view.findViewById<TextView>(R.id.title).text = item.label
        view.findViewById<TextView>(R.id.subtitle).text =
            if (item.pkg in messaging) getString(R.string.row_subtitle, getString(R.string.messaging_warning), item.pkg)
            else item.pkg

        fun bind() {
            badge.setText(if (item.restricted) R.string.state_restricted else R.string.state_free)
            check.setOnCheckedChangeListener(null)
            check.isChecked = item.restricted
            check.setOnCheckedChangeListener { _, isChecked -> setRestricted(item, isChecked, check, ::bind) }
        }
        bind()
        view.setOnClickListener { check.toggle() }
        return view
    }

    private fun setRestricted(item: AppItem, restrict: Boolean, check: CheckBox, rebind: () -> Unit) {
        val p = Shell.checkPackage(item.pkg)
        val mode = if (restrict) "ignore" else "allow"
        check.isEnabled = false
        background({
            Shell.exec("cmd appops set $p RUN_ANY_IN_BACKGROUND $mode && cmd appops set $p RUN_IN_BACKGROUND $mode")
        }) { r ->
            check.isEnabled = true
            if (r.ok) item.restricted = restrict
            else Toast.makeText(this, r.out.ifBlank { getString(R.string.failed) }, Toast.LENGTH_LONG).show()
            rebind()
        }
    }
}
