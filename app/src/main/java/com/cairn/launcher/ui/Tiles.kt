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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.cairn.launcher.data.AppInfo
import com.cairn.launcher.notify.NotificationState
import androidx.compose.material3.Text

/**
 * One icon.
 *
 * The caption is the app's name until something is waiting, and then it is the sender's name.
 * A label that only exists when it carries information is the whole argument for having labels.
 *
 * The rule underneath is a length, not a badge. A badge says something exists; a length says
 * how much.
 */
@Composable
fun IconTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val bitmap = remember(app.key) {
        runCatching { app.icon.toBitmap(144, 144).asImageBitmap() }.getOrNull()
    }
    val notices by NotificationState.notices.collectAsState()
    val media by NotificationState.media.collectAsState()
    val notice = notices[app.packageName]
    val level = notice?.level ?: media[app.packageName]
    val caption = notice?.title?.takeIf { it.isNotBlank() } ?: app.label
    val highlighted = notice?.title?.isNotBlank() == true

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(Cairn.IconSize)
            )
        } else {
            Box(
                Modifier
                    .size(Cairn.IconSize)
                    .background(hairlineColor())
            )
        }

        Spacer(Modifier.height(3.dp))
        LevelRule(level)

        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                color = if (highlighted) wallpaperTextColor() else secondaryTextColor(),
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
fun LevelRule(level: Float?) {
    val width = Cairn.IconSize
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
                    .background(wallpaperTextColor().copy(alpha = 0.85f))
            )
        }
    }
}
