package com.ali.hafiflet

import android.content.Context
import android.widget.TextView

fun sectionHeader(context: Context, text: String) =
    TextView(context, null, 0, R.style.Section).apply { this.text = text }
