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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a hold on an icon offers, before you have moved far enough for it to become a drag.
 *
 * Holding does two things at once here, deliberately: it opens this, and it picks the icon up.
 * Move your finger and the menu goes away and you are dragging. Lift without moving and the menu
 * stays. That is how Android has always behaved and it is worth matching, because it means one
 * hold serves both meanings without either needing its own gesture.
 */
@Composable
fun AppMenu(
    label: String,
    canRemove: Boolean,
    canUninstall: Boolean,
    onOpen: () -> Unit,
    onAppInfo: () -> Unit,
    onNotificationSettings: () -> Unit,
    onRemoveFromHome: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Cairn.Surface.copy(alpha = 0.92f))
            .clickableNoRipple { onDismiss() },
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Cairn.PagePadding)
        ) {
            Text(
                text = label,
                color = Cairn.OnSurfaceSecondary,
                fontSize = Cairn.DateSize,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Cairn.SurfaceHairline)
            )
            Spacer(Modifier.height(6.dp))

            MenuLine("Open") { onOpen(); onDismiss() }
            MenuLine("App info") { onAppInfo(); onDismiss() }
            MenuLine("Notification settings") { onNotificationSettings(); onDismiss() }
            if (canRemove) MenuLine("Remove from home") { onRemoveFromHome(); onDismiss() }
            if (canUninstall) MenuLine("Uninstall") { onUninstall(); onDismiss() }

            Spacer(Modifier.height(10.dp))
            MenuLine("Close", onDismiss)
        }
    }
}

@Composable
private fun MenuLine(text: String, onClick: () -> Unit) {
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
