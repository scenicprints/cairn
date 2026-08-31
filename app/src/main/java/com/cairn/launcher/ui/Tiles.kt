package com.cairn.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.cairn.launcher.data.AppInfo
import com.cairn.launcher.notify.NotificationState

/**
 * One icon.
 *
 * The caption is the app's name until something is waiting, and then it is the sender's name.
 * A label that only exists when it carries information is the whole argument for having labels.
 *
 * The rule underneath is a length, not a badge. A badge says something exists; a length says
 * how much.
 *
 * [onSurface] picks the text palette. Icons drawn on the wallpaper take the wallpaper-derived
 * colour; icons drawn on one of Cairn's own dark panels must not, or they vanish.
 */
@Composable
fun IconTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onSurface: Boolean = false,
    iconSize: Dp = Cairn.IconSize,
    /** Swap the caption to the sender's name when something is waiting. */
    showCaption: Boolean = true,
    /** Draw the rule whose length is a media position or a download. */
    showLevel: Boolean = true
) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { iconSize.roundToPx() }
    val bitmap = remember(app.key, px) {
        runCatching {
            app.icon.toBitmap(px.coerceIn(48, 192), px.coerceIn(48, 192)).asImageBitmap()
        }.getOrNull()
    }

    val notices by NotificationState.notices.collectAsState()
    val media by NotificationState.media.collectAsState()
    val notice = notices[app.packageName]
    val level = if (showLevel) (notice?.level ?: media[app.packageName]) else null

    val primary: Color = if (onSurface) Cairn.OnSurface else wallpaperTextColor()
    val secondary: Color = if (onSurface) Cairn.OnSurfaceSecondary else secondaryTextColor()

    val sender = if (showCaption) notice?.title?.takeIf { it.isNotBlank() } else null
    val caption = sender ?: app.label
    val highlighted = sender != null

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(iconSize)
            )
        } else {
            Box(
                Modifier
                    .size(iconSize)
                    .background(secondary)
            )
        }

        Spacer(Modifier.height(3.dp))
        LevelRule(level = level, width = iconSize, color = primary)

        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = if (highlighted) primary else secondary,
                fontSize = Cairn.LabelSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** A one pixel rule whose length is the value. Absent when there is nothing to say. */
@Composable
fun LevelRule(level: Float?, width: Dp, color: Color) {
    Box(
        Modifier
            .width(width)
            .height(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (level != null) {
            Box(
                Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .height(1.dp)
                    .background(color.copy(alpha = 0.85f))
            )
        }
    }
}
