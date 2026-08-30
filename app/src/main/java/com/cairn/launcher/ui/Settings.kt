package com.cairn.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cairn.launcher.notify.CairnNotificationListener
import com.cairn.launcher.update.Updater
import kotlinx.coroutines.launch

/**
 * There is no long press in Cairn, so this is the way in to everything administrative.
 * It is a list of words, because a settings screen is the one place where a picture of a thing
 * is worse than the name of it.
 */
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onAddWidget: () -> Unit,
    onAddPage: () -> Unit,
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
            .background(Color(0xFF14140F).copy(alpha = 0.96f))
            .clickableNoRipple { onDismiss() },
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Cairn.PagePadding)
        ) {
            Line("Add widget", onAddWidget)
            Line("Add page", onAddPage)
            Line("Set Cairn as home", onSetDefaultLauncher)

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(hairlineColor())
            )
            Spacer(Modifier.height(12.dp))

            Line(
                if (CairnNotificationListener.isConnected())
                    "Notification access granted" else "Grant notification access",
                onNotificationAccess
            )
            Line("Grant usage access", onUsageAccess)

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(hairlineColor())
            )
            Spacer(Modifier.height(12.dp))

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
                Spacer(Modifier.height(8.dp))
                Text(status, color = secondaryTextColor(), fontSize = Cairn.DateSize)
            }

            Spacer(Modifier.height(20.dp))
            Line("Close", onDismiss)
        }
    }
}

@Composable
private fun Line(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = wallpaperTextColor(),
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 10.dp)
    )
}
