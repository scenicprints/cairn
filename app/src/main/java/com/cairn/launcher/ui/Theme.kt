package com.cairn.launcher.ui

import android.app.WallpaperManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Achromatic by intent. The app icons are already a riot of colour they did not ask permission
 * for, so the launcher's own chrome stays grey, and the single accent is used once, for the
 * alarm mark next to the clock.
 */
object Cairn {
    val Accent = Color(0xFFBA7517)

    val GridCols = 4
    val GridRows = 5

    val Unit = 8.dp
    val PagePadding = 16.dp
    val IconSize = 48.dp
    val DockIconSize = 48.dp
    val LabelSize = 11.sp
    val ClockSize = 20.sp
    val DateSize = 11.sp
    val RowGap = 24.dp
}

/**
 * Black text or white text, decided by the wallpaper rather than by a setting. Android already
 * works this out for its own chrome; we ask it the same question.
 */
@Composable
fun wallpaperTextColor(): Color {
    val context = LocalContext.current
    return remember {
        val darkText = runCatching {
            val wm = WallpaperManager.getInstance(context)
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val hints = colors?.colorHints ?: 0
            hints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        }.getOrDefault(false)
        if (darkText) Color(0xFF1A1A19) else Color(0xFFF4F3EF)
    }
}

@Composable
fun secondaryTextColor(): Color = wallpaperTextColor().copy(alpha = 0.62f)

@Composable
fun hairlineColor(): Color = wallpaperTextColor().copy(alpha = 0.28f)
