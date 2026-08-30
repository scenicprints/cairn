package com.cairn.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cairn.launcher.data.AppInfo

enum class DrawerRow { Recent, New, Frequent }

/**
 * The drawer has no search bar.
 *
 * The keyboard is already rising as you pull it open, and the keyboard is the place you type.
 * A bar with a magnifier and the word Search in it is a picture of a place to type, sitting
 * above the actual place to type. So: pull up, start typing, the list filters, and what you
 * typed appears as plain text with nothing drawn around it.
 */
@Composable
fun Drawer(
    apps: List<AppInfo>,
    rowMode: DrawerRow,
    rowApps: List<AppInfo>,
    progress: Float,
    onRowModeChange: (DrawerRow) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val open = progress > 0.98f

    LaunchedEffect(open) {
        if (open) runCatching { focus.requestFocus() } else query = ""
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { fuzzy(it.label, query) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color(0xFF14140F).copy(alpha = 0.94f * progress))
            .padding(horizontal = Cairn.PagePadding)
    ) {
        Spacer(Modifier.height(20.dp))

        // The one row Nova gets right: recent, new, frequent, across the top.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DrawerRow.entries.forEach { mode ->
                Text(
                    text = mode.name.lowercase(),
                    color = if (mode == rowMode) wallpaperTextColor() else secondaryTextColor(),
                    fontSize = Cairn.LabelSize,
                    modifier = Modifier.clickableNoRipple { onRowModeChange(mode) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            rowApps.take(6).forEach { app ->
                Box(
                    Modifier
                        .width(52.dp)
                        .clickableNoRipple { onLaunch(app) }
                ) {
                    IconTile(app, showLabel = false)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairlineColor())
        )

        if (query.isNotEmpty()) {
            Text(
                text = query,
                color = wallpaperTextColor(),
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.key }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clickableNoRipple { onLaunch(app) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(30.dp)) {
                        IconTile(app, showLabel = false)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = wallpaperTextColor(), fontSize = 15.sp)
                }
            }
        }

        // Invisible, and the only reason it exists is to own the keyboard.
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .focusRequester(focus)
        )
    }
}

/** Subsequence match, so "fw" finds FuelWise and "hmy" finds Homey. */
private fun fuzzy(label: String, query: String): Boolean {
    val l = label.lowercase()
    var i = 0
    for (c in query.lowercase()) {
        if (c == ' ') continue
        val at = l.indexOf(c, i)
        if (at < 0) return false
        i = at + 1
    }
    return true
}
