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
import androidx.compose.foundation.layout.imePadding
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
 *
 * Everything in here draws on Cairn's own dark panel, so it uses [Cairn.OnSurface] and never
 * the wallpaper-derived colour. Getting that wrong is what made the whole drawer look dead.
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
            .background(Cairn.Surface.copy(alpha = (0.94f * progress).coerceIn(0f, 1f)))
            .imePadding()
            .padding(horizontal = Cairn.PagePadding)
    ) {
        Spacer(Modifier.height(16.dp))

        // The one row Nova gets right: recent, new, frequent, across the top. These are real
        // touch targets now rather than eleven-point text with a text-sized hit area.
        Row(Modifier.fillMaxWidth()) {
            DrawerRow.entries.forEach { mode ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(Cairn.MinTouch)
                        .clickableNoRipple { onRowModeChange(mode) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = mode.name.lowercase(),
                        color = if (mode == rowMode) Cairn.OnSurface
                        else Cairn.OnSurfaceSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            rowApps.take(6).forEach { app ->
                Box(
                    Modifier
                        .width(54.dp)
                        .clickableNoRipple { onLaunch(app) }
                ) {
                    IconTile(app, showLabel = false, onSurface = true)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Cairn.SurfaceHairline)
        )

        if (query.isNotEmpty()) {
            Text(
                text = query,
                color = Cairn.OnSurface,
                fontSize = 20.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.key }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(Cairn.MinTouch + 6.dp)
                        .clickableNoRipple { onLaunch(app) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(32.dp)) {
                        IconTile(app, showLabel = false, onSurface = true, iconSize = 32.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = Cairn.OnSurface, fontSize = 16.sp)
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
