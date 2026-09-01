package com.cairn.launcher.ui

import androidx.compose.foundation.background
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

private enum class Section(val title: String) {
    Grid("Grid"),
    Dock("Dock"),
    Clock("Clock"),
    Behaviour("Behaviour"),
    Permissions("Permissions"),
    Updates("Updates"),
    Reset("Start over")
}

/**
 * An index you enter, not one endless scroll.
 *
 * The first version put every control on a single page, which meant finding anything required
 * reading everything. Seven names, each with its current value beside it, and you go into the
 * one you want. The value on the right is doing real work: most of the time you came here to
 * check a setting rather than change it, and the index alone answers that.
 */
@Composable
fun SettingsSheet(
    prefs: Prefs,
    onPrefs: ((Prefs) -> Prefs) -> Unit,
    onDismiss: () -> Unit,
    onResetLayout: () -> Unit,
    onResetSettings: () -> Unit,
    onNotificationAccess: () -> Unit,
    onUsageAccess: () -> Unit,
    onSetDefaultLauncher: () -> Unit
) {
    var section by remember { mutableStateOf<Section?>(null) }

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
            Spacer(Modifier.height(24.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Cairn.MinTouch)
                    .clickableNoRipple { if (section == null) onDismiss() else section = null },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (section == null) "Close" else "Settings",
                    color = Cairn.OnSurfaceSecondary,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = section?.title ?: "Settings",
                color = Cairn.OnSurface,
                fontSize = 24.sp
            )
            Spacer(Modifier.height(20.dp))

            when (section) {
                null -> Section.entries.forEach { s ->
                    IndexRow(s.title, summary(s, prefs)) { section = s }
                }

                Section.Grid -> {
                    Stepper("Columns", prefs.cols, 3, 6) { v -> onPrefs { it.copy(cols = v) } }
                    Stepper("Rows", prefs.rows, 3, 8) { v -> onPrefs { it.copy(rows = v) } }
                    Stepper("Icon size", prefs.iconDp, 36, 72, 4, "dp") { v ->
                        onPrefs { it.copy(iconDp = v) }
                    }
                    Stepper("Row height", prefs.rowHeightDp, 56, 120, 4, "dp") { v ->
                        onPrefs { it.copy(rowHeightDp = v) }
                    }
                    Toggle("Icon labels", prefs.showLabels) { v ->
                        onPrefs { it.copy(showLabels = v) }
                    }
                }

                Section.Dock -> {
                    Toggle("Show dock", prefs.showDock) { v -> onPrefs { it.copy(showDock = v) } }
                    Stepper("Slots", prefs.dockCount, 0, 6) { v ->
                        onPrefs { it.copy(dockCount = v) }
                    }
                }

                Section.Clock -> {
                    Toggle("Show clock", prefs.showClock) { v ->
                        onPrefs { it.copy(showClock = v) }
                    }
                    Toggle("Show date", prefs.showDate) { v -> onPrefs { it.copy(showDate = v) } }
                }

                Section.Behaviour -> {
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
                }

                Section.Permissions -> {
                    Line(
                        if (CairnNotificationListener.isConnected())
                            "Notification access granted" else "Grant notification access",
                        onNotificationAccess
                    )
                    Line("Grant usage access", onUsageAccess)
                    Line("Set Cairn as home", onSetDefaultLauncher)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Notification access is what gives icons a sender's name, a " +
                            "reply field, and the rule underneath. It is revoked on reinstall.",
                        color = Cairn.OnSurfaceSecondary,
                        fontSize = Cairn.DateSize
                    )
                }

                Section.Updates -> UpdateBlock()

                Section.Reset -> {
                    Line("Reset home screen", onResetLayout)
                    Line("Reset settings", onResetSettings)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Resetting the home screen rebuilds it from your installed apps. " +
                            "Resetting settings leaves your layout alone.",
                        color = Cairn.OnSurfaceSecondary,
                        fontSize = Cairn.DateSize
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun summary(section: Section, p: Prefs): String = when (section) {
    Section.Grid -> "${p.cols} by ${p.rows}"
    Section.Dock -> if (p.showDock) "${p.dockCount} slots" else "off"
    Section.Clock -> when {
        p.showClock && p.showDate -> "clock and date"
        p.showClock -> "clock"
        p.showDate -> "date"
        else -> "off"
    }
    Section.Behaviour -> "${p.drawerSensitivity}x drawer"
    Section.Permissions ->
        if (CairnNotificationListener.isConnected()) "notifications on" else "not granted"
    Section.Updates -> ""
    Section.Reset -> ""
}

@Composable
private fun UpdateBlock() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

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
            status = if (apk == null) {
                "Download failed"
            } else {
                Updater.install(context, apk)
                "Installing. The home screen will restart."
            }
        }
    }
    if (status.isNotEmpty()) {
        Text(status, color = Cairn.OnSurfaceSecondary, fontSize = Cairn.DateSize)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Build ${Updater.currentVersionCode(LocalContext.current)}.",
        color = Cairn.OnSurfaceSecondary,
        fontSize = Cairn.DateSize
    )
}

@Composable
private fun IndexRow(title: String, value: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickableNoRipple(onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Cairn.OnSurface, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(value, color = Cairn.OnSurfaceSecondary, fontSize = 15.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Cairn.SurfaceHairline)
        )
    }
}

@Composable
private fun Line(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(Cairn.MinTouch + 8.dp)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, color = Cairn.OnSurface, fontSize = 17.sp)
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Cairn.MinTouch + 8.dp)
            .clickableNoRipple { onChange(!value) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Cairn.OnSurface, fontSize = 17.sp, modifier = Modifier.weight(1f))
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
            .height(Cairn.MinTouch + 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Cairn.OnSurface, fontSize = 17.sp, modifier = Modifier.weight(1f))

        StepButton("−", value - step >= min) { onChange(value - step) }

        Box(
            Modifier
                .width(58.dp)
                .height(Cairn.MinTouch),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = display?.invoke(value) ?: "$value$suffix",
                color = Cairn.OnSurface,
                fontSize = 16.sp
            )
        }

        StepButton("+", value + step <= max) { onChange(value + step) }
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Cairn.MinTouch)
            .clickableNoRipple { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = if (enabled) Cairn.OnSurface else Cairn.OnSurface.copy(alpha = 0.25f),
            fontSize = 20.sp
        )
    }
}
