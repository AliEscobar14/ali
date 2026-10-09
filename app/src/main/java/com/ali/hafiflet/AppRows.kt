package com.ali.hafiflet

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch

/** Uygulama listelerindeki ortak satır görünümü. */
class AppRow(activity: Activity, parent: ViewGroup) {
    val view: View = activity.layoutInflater.inflate(R.layout.item_app, parent, false)
    val icon: ImageView = view.findViewById(R.id.icon)
    val title: TextView = view.findViewById(R.id.title)
    val subtitle: TextView = view.findViewById(R.id.subtitle)
    val badge: TextView = view.findViewById(R.id.badge)
    val check: MaterialCheckBox = view.findViewById(R.id.check)
    val toggle: MaterialSwitch = view.findViewById(R.id.toggle)

    enum class Tone(val bg: Int, val fg: Int) {
        GOOD(R.color.badge_good_bg, R.color.badge_good_fg),
        NEUTRAL(R.color.badge_neutral_bg, R.color.badge_neutral_fg),
        WARN(R.color.badge_warn_bg, R.color.badge_warn_fg),
    }

    fun setBadge(text: Int?, tone: Tone = Tone.NEUTRAL) {
        badge.show(text != null)
        if (text == null) return
        badge.setText(text)
        badge.backgroundTintList = view.context.getColorStateList(tone.bg)
        badge.setTextColor(view.context.getColorStateList(tone.fg))
    }
}

fun sectionHeader(activity: Activity, parent: ViewGroup, title: String, action: String? = null, onAction: (() -> Unit)? = null): View {
    val view = activity.layoutInflater.inflate(R.layout.item_section, parent, false)
    view.findViewById<TextView>(R.id.section_title).text = title
    val button = view.findViewById<Button>(R.id.section_action)
    if (action != null && onAction != null) {
        button.text = action
        button.show(true)
        button.setOnClickListener { onAction() }
    }
    return view
}

/** Kapalı veya bu kullanıcı için kaldırılmış paketlerin de simgesini bulur. */
fun loadAppIcon(pm: PackageManager, pkg: String): Drawable? = try {
    @Suppress("DEPRECATION")
    val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
    pm.getApplicationInfo(pkg, flags).loadIcon(pm)
} catch (e: PackageManager.NameNotFoundException) {
    null
}
