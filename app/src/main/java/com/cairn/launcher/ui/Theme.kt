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
 * alarm mark beside the clock.
 *
 * There are two text palettes here and confusing them is what made the drawer look broken.
 * Over the wallpaper, the right colour depends on the wallpaper. Over one of Cairn's own dark
 * panels, it never does, and asking the wallpaper produced black text on a black sheet.
 */
object Cairn {
    val Accent = Color(0xFFBA7517)

    /** Cairn's own panels: the drawer, the settings sheet, the page overview. */
    val Surface = Color(0xFF14140F)
    val OnSurface = Color(0xFFF4F3EF)
    val OnSurfaceSecondary = Color(0xFFF4F3EF).copy(alpha = 0.62f)
    val SurfaceHairline = Color(0xFFF4F3EF).copy(alpha = 0.22f)

    val PagePadding = 16.dp
    val IconSize = 48.dp

    /**
     * A grid row is the icon plus its label plus breathing room, and it is a fixed height.
     * Dividing the page height by the row count instead left icons floating in the middle of
     * enormous cells, which is most of why it looked nothing like the drawing.
     */
    val RowHeight = 78.dp

    val LabelSize = 11.sp
    val ClockSize = 20.sp
    val DateSize = 12.sp

    /** Anything you are meant to hit gets at least this, whatever the text inside it measures. */
    val MinTouch = 44.dp
}

/**
 * Black text or white text over the wallpaper, decided by the wallpaper rather than by a
 * setting. Android already works this out for its own chrome; we ask it the same question.
 *
 * Only for content drawn directly on the wallpaper. Anything on a Cairn panel uses
 * [Cairn.OnSurface] instead.
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
