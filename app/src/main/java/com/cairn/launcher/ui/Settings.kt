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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cairn.launcher.data.Prefs
import com.cairn.launcher.notify.CairnNotificationListener
import com.cairn.launcher.update.Updater
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.cairn.launcher.data.Layout as CairnLayout

/**
 * Everything adjustable, on one scrolling sheet.
 *
 * A list of words with their values on the right, because a settings screen is the one place
 * where the name of a thing beats a picture of it. Changes apply as you make them, so there is
 * no Apply button, because there is nothing to apply.
 */
@Composable
fun SettingsSheet(
    prefs: Prefs,
    layout: CairnLayout,
    onPrefs: ((Prefs) -> Prefs) -> Unit,
    onDismiss: () -> Unit,
    onAddWidget: () -> Unit,
    onAddPage: () -> Unit,
    onSetHomePage: (Int) -> Unit,
    onResetLayout: () -> Unit,
    onResetSettings: () -> Unit,
    onNotificationAccess: () -> Unit,
    onUsageAccess: () -> Unit,
    onSetDefaultLauncher: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cairn.Surface)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Cairn.PagePadding)
        ) {
            Spacer(Modifier.height(20.dp))
            Text("Settings", color = Cairn.OnSurface, fontSize = 22.sp)

            Heading("Grid")
            Stepper("Columns", prefs.cols, 3, 6) { v -> onPrefs { it.copy(cols = v) } }
            Stepper("Rows", prefs.rows, 3, 8) { v -> onPrefs { it.copy(rows = v) } }
            Stepper("Icon size", prefs.iconDp, 36, 72, step = 4, suffix = "dp") { v ->
                onPrefs { it.copy(iconDp = v) }
            }
            Stepper("Row height", prefs.rowHeightDp, 56, 120, step = 4, suffix = "dp") { v ->
                onPrefs { it.copy(rowHeightDp = v) }
            }
            Toggle("Icon labels", prefs.showLabels) { v -> onPrefs { it.copy(showLabels = v) } }

            Heading("Dock")
            Toggle("Show dock", prefs.showDock) { v -> onPrefs { it.copy(showDock = v) } }
            Stepper("Dock slots", prefs.dockCount, 0, 6) { v ->
                onPrefs { it.copy(dockCount = v) }
            }

            Heading("Clock")
            Toggle("Show clock", prefs.showClock) { v -> onPrefs { it.copy(showClock = v) } }
            Toggle("Show date", prefs.showDate) { v -> onPrefs { it.copy(showDate = v) } }

            Heading("Behaviour")
            Toggle("Sender name on icons", prefs.notificationCaptions) { v ->
                onPrefs { it.copy(notificationCaptions = v) }
            }
            Toggle("Level rules under icons", prefs.levelRules) { v ->
                onPrefs { it.copy(levelRules = v) }
            }
            Toggle("Home recedes for drawer", prefs.dimOnDrawer) { v ->
                onPrefs { it.copy(dimOnDrawer = v) }
            }
            Stepper(
                label = "Drawer sensitivity",
                value = (prefs.drawerSensitivity * 10).roundToInt(),
                min = 10,
                max = 40,
                step = 2,
                display = { "${it / 10f}x" }
            ) { v -> onPrefs { it.copy(drawerSensitivity = v / 10f) } }

            Heading("Pages")
            Row(
                Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                layout.pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(Cairn.MinTouch)
                            .background(
                                if (index == layout.homePage) Cairn.OnSurface.copy(alpha = 0.22f)
                                else Cairn.OnSurface.copy(alpha = 0.06f)
                            )
                            .clickableNoRipple { onSetHomePage(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (index == layout.homePage) Cairn.OnSurface
                            else Cairn.OnSurfaceSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            Text(
                text = "Tap a number to make it the home page. The home button goes there.",
                color = Cairn.OnSurfaceSecondary,
                fontSize = Cairn.DateSize
            )
            Line("Add page", onAddPage)
            Line("Add widget", onAddWidget)

            Heading("Permissions")
            Line(
                if (CairnNotificationListener.isConnected()) "Notification access granted"
                else "Grant notification access",
                onNotificationAccess
            )
            Line("Grant usage access", onUsageAccess)
            Line("Set Cairn as home", onSetDefaultLauncher)

            Heading("Updates")
            Line("Check for updates") {
                scope.launch {
                    status = "Checking"
                    val release = Updater.latest()
                    if (release == null) {
                        status = "No release found"
                        return@launch
                    }
                    if (release.versionCode <= Updater.currentVersionCode(context)) {
                        status = "Up to date, ${release.name}"
                        return@launch
                    }
                    if (Updater.needsInstallPermission(context)) {
                        status = "Allow Cairn to install apps first"
                        Updater.openInstallPermissionSettings(context)
                        return@launch
                    }
                    status = "Downloading ${release.name}"
                    val apk = Updater.download(context, release)
                    if (apk == null) {
                        status = "Download failed"
                    } else {
                        status = "Installing. The home screen will restart."
                        Updater.install(context, apk)
                    }
                }
            }
            if (status.isNotEmpty()) {
                Text(status, color = Cairn.OnSurfaceSecondary, fontSize = Cairn.DateSize)
            }

            Heading("Start over")
            Line("Reset home screen", onResetLayout)
            Line("Reset settings", onResetSettings)

            Spacer(Modifier.height(24.dp))
            Line("Close", onDismiss)
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(text, color = Cairn.OnSurfaceSecondary, fontSize = Cairn.DateSize)
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Cairn.SurfaceHairline)
    )
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun Line(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(Cairn.MinTouch)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, color = Cairn.OnSurface, fontSize = 16.sp)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Cairn.MinTouch)
            .clickableNoRipple { onChange(!value) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Cairn.OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            text = if (value) "on" else "off",
            color = if (value) Cairn.OnSurface else Cairn.OnSurfaceSecondary,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    suffix: String = "",
    display: ((Int) -> String)? = null,
    onChange: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Cairn.MinTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Cairn.OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))

        Box(
            Modifier
                .size(Cairn.MinTouch)
                .clickableNoRipple { if (value - step >= min) onChange(value - step) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = if (value - step >= min) Cairn.OnSurface else Cairn.OnSurfaceSecondary,
                fontSize = 20.sp
            )
        }

        Box(
            Modifier
                .width(56.dp)
                .height(Cairn.MinTouch),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = display?.invoke(value) ?: "$value$suffix",
                color = Cairn.OnSurface,
                fontSize = 15.sp
            )
        }

        Box(
            Modifier
                .size(Cairn.MinTouch)
                .clickableNoRipple { if (value + step <= max) onChange(value + step) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = if (value + step <= max) Cairn.OnSurface else Cairn.OnSurfaceSecondary,
                fontSize = 20.sp
            )
        }
    }
}
