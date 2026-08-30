package com.cairn.launcher.data

import android.graphics.drawable.Drawable

/** One launchable activity. [key] is what the layout file stores. */
data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable,
    val firstInstall: Long
) {
    val key: String get() = "$packageName/$className"
}

fun keyOf(pkg: String, cls: String) = "$pkg/$cls"
