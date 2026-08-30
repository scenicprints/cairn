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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cairn.launcher.data.AppInfo
import com.cairn.launcher.data.Slot
import com.cairn.launcher.notify.Notice
import com.cairn.launcher.widgets.CairnWidgetHost

/**
 * What appears when you pull an icon down.
 *
 * A folder gives its apps. A messaging app gives the message and a reply field. Anything else
 * gives its deep shortcuts. One gesture, one meaning, and the panel is drawn in the same grid,
 * at the same icon size, with the same label style as the page it opened on. It is the grid
 * making room, not a new surface arriving.
 */
@Composable
fun PullPanel(
    app: AppInfo?,
    folder: Slot.Folder?,
    notice: Notice?,
    byKey: Map<String, AppInfo>,
    shortcuts: List<android.content.pm.ShortcutInfo>,
    onLaunch: (AppInfo) -> Unit,
    onStartShortcut: (android.content.pm.ShortcutInfo) -> Unit,
    onReply: (Notice, String) -> Unit,
    onRename: (String) -> Unit = {},
    onChildLift: (String, Offset, Offset, Offset) -> Unit = { _, _, _, _ -> },
    onChildLiftMove: (Offset) -> Unit = {},
    onChildLiftDrop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(top = 12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairlineColor())
        )
        Spacer(Modifier.height(12.dp))

        when {
            folder != null -> {
                // The name is editable in place. A folder called Folder is a folder nobody named.
                FolderName(folder.name, onRename)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folder.keys.mapNotNull { byKey[it] }.take(5).forEach { child ->
                        var root by remember(child.key) { mutableStateOf(Offset.Zero) }
                        var size by remember(child.key) { mutableStateOf(Offset.Zero) }
                        Box(
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned {
                                    root = it.positionInRoot()
                                    size = Offset(
                                        it.size.width.toFloat(),
                                        it.size.height.toFloat()
                                    )
                                }
                                .tileGestures(
                                    onTap = { onLaunch(child) },
                                    onPullStart = { },
                                    onPullDelta = { },
                                    onPullRelease = { },
                                    onLiftStart = { grab ->
                                        onChildLift(child.key, grab, root, size)
                                    },
                                    onLiftMove = onChildLiftMove,
                                    onLiftDrop = onChildLiftDrop
                                ),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            IconTile(child)
                        }
                    }
                }
            }

            notice != null && notice.canReply -> {
                ReplyBlock(notice = notice, onReply = onReply)
            }

            notice != null -> {
                notice.title?.let {
                    Text(it, color = secondaryTextColor(), fontSize = Cairn.LabelSize)
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    notice.text.orEmpty(),
                    color = wallpaperTextColor(),
                    fontSize = Cairn.DateSize
                )
            }

            shortcuts.isNotEmpty() -> {
                shortcuts.take(4).forEach { s ->
                    val label = (s.shortLabel ?: s.longLabel)?.toString().orEmpty()
                    Text(
                        text = label,
                        color = wallpaperTextColor(),
                        fontSize = Cairn.DateSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { onStartShortcut(s) }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            else -> {
                Text(
                    text = app?.label.orEmpty(),
                    color = secondaryTextColor(),
                    fontSize = Cairn.DateSize
                )
            }
        }
    }
}

/**
 * The message goes out through the app's own reply intent, the same path Wear OS uses. We are
 * not pretending to be the app, we are firing the intent it published.
 */
@Composable
private fun ReplyBlock(notice: Notice, onReply: (Notice, String) -> Unit) {
    var draft by remember(notice.key) { mutableStateOf("") }
    var error by remember(notice.key) { mutableStateOf(false) }

    notice.title?.let {
        Text(it, color = secondaryTextColor(), fontSize = Cairn.LabelSize)
        Spacer(Modifier.height(2.dp))
    }
    Text(notice.text.orEmpty(), color = wallpaperTextColor(), fontSize = Cairn.DateSize)
    Spacer(Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(34.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it; error = false },
                singleLine = true,
                textStyle = TextStyle(
                    color = wallpaperTextColor(),
                    fontSize = Cairn.DateSize
                ),
                cursorBrush = SolidColor(wallpaperTextColor()),
                modifier = Modifier.fillMaxWidth()
            )
            if (draft.isEmpty()) {
                Text("Reply", color = secondaryTextColor(), fontSize = Cairn.DateSize)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Send",
            color = wallpaperTextColor(),
            fontSize = Cairn.DateSize,
            modifier = Modifier.clickableNoRipple {
                if (draft.isBlank()) error = true else {
                    onReply(notice, draft)
                    draft = ""
                }
            }
        )
    }
    if (error) {
        Spacer(Modifier.height(4.dp))
        Text("Enter a reply first", color = Cairn.Accent, fontSize = Cairn.LabelSize)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(hairlineColor())
    )
}

/** Editable in place, because a folder called Folder is a folder nobody bothered to name. */
@Composable
private fun FolderName(name: String, onRename: (String) -> Unit) {
    var draft by remember(name) { mutableStateOf(name) }
    BasicTextField(
        value = draft,
        onValueChange = {
            draft = it
            onRename(it)
        },
        singleLine = true,
        textStyle = TextStyle(color = wallpaperTextColor(), fontSize = Cairn.DateSize),
        cursorBrush = SolidColor(wallpaperTextColor()),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A folder's closed face. Four contents at a legible size, plus the word. */
@Composable
fun FolderTile(folder: Slot.Folder, byKey: Map<String, AppInfo>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(Cairn.IconSize)
                .background(wallpaperTextColor().copy(alpha = 0.10f))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                folder.keys.mapNotNull { byKey[it] }.take(4).chunked(2).forEach { pair ->
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        pair.forEach { child ->
                            Box(Modifier.weight(1f).fillMaxSize()) {
                                IconTile(child, showLabel = false)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            folder.name,
            color = secondaryTextColor(),
            fontSize = Cairn.LabelSize
        )
    }
}

/**
 * A real AppWidgetHostView, not a drawing of one. Your Google Calendar widget is the acceptance
 * test for this whole file.
 */
@Composable
fun WidgetSlotView(host: CairnWidgetHost, widgetId: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            host.createView(context, widgetId) ?: android.widget.FrameLayout(context)
        },
        update = { }
    )
}
