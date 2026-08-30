package com.cairn.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.cairn.launcher.data.AppRepository
import com.cairn.launcher.data.LayoutStore
import com.cairn.launcher.data.Placed
import com.cairn.launcher.data.Slot
import com.cairn.launcher.data.makeFolder
import com.cairn.launcher.data.moveBetweenPages
import com.cairn.launcher.data.removeFromFolder
import com.cairn.launcher.data.removeItem
import com.cairn.launcher.data.renameFolder
import com.cairn.launcher.data.resizeWidget
import com.cairn.launcher.data.setDock
import com.cairn.launcher.notify.NotificationState
import com.cairn.launcher.ui.Drop
import com.cairn.launcher.ui.Drawer
import com.cairn.launcher.ui.DrawerRow
import com.cairn.launcher.ui.Home
import com.cairn.launcher.ui.LiftState
import com.cairn.launcher.ui.PageOverview
import com.cairn.launcher.ui.SettingsSheet
import com.cairn.launcher.widgets.CairnWidgetHost
import com.cairn.launcher.widgets.REQ_BIND_WIDGET
import com.cairn.launcher.widgets.REQ_CONFIGURE_WIDGET
import com.cairn.launcher.widgets.REQ_PICK_WIDGET
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepository
    private lateinit var store: LayoutStore
    private lateinit var host: CairnWidgetHost

    /** Which page a widget being picked should land on. */
    private var pendingWidgetPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repo = AppRepository(this)
        store = LayoutStore(this)
        host = CairnWidgetHost(this)

        store.load()
        repo.start()

        setContent {
            val apps by repo.apps.collectAsState()
            val layout by store.layout.collectAsState()
            val scope = rememberCoroutineScope()
            val drawer = remember { Animatable(0f) }

            var rowMode by remember { mutableStateOf(DrawerRow.Recent) }
            var settingsOpen by remember { mutableStateOf(false) }
            var overviewOpen by remember { mutableStateOf(false) }
            var goToPage by remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(apps.size) {
                if (apps.isNotEmpty()) store.seedIfEmpty(apps)
            }

            val rowApps = remember(apps, rowMode) {
                when (rowMode) {
                    DrawerRow.Recent -> repo.recent()
                    DrawerRow.New -> repo.newlyInstalled()
                    DrawerRow.Frequent -> repo.frequent()
                }.ifEmpty { apps.take(6) }
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

                Home(
                    apps = apps,
                    layout = layout,
                    host = host,
                    onLaunch = { app -> repo.launch(app, null) },
                    onShortcuts = { pkg -> repo.shortcuts(pkg) },
                    onStartShortcut = { info -> repo.startShortcut(info) },
                    onReply = { notice, text ->
                        NotificationState.sendReply(this@MainActivity, notice, text)
                    },
                    onDrawerDrag = { delta ->
                        scope.launch {
                            drawer.snapTo((drawer.value + delta / heightPx).coerceIn(0f, 1f))
                        }
                    },
                    onDrawerRelease = {
                        scope.launch { drawer.animateTo(if (drawer.value > 0.35f) 1f else 0f) }
                    },
                    onOpenSettings = { settingsOpen = true },
                    onOpenOverview = { overviewOpen = true },
                    onDrop = { lift, drop, page -> handleDrop(lift, drop, page) },
                    onRenameFolder = { page, item, name -> store.renameFolder(page, item, name) },
                    onResizeWidget = { page, item, x, y ->
                        store.resizeWidget(page, item, x, y)
                        host.resize(
                            (item.slot as? Slot.Widget)?.widgetId ?: return@Home,
                            minWidthDp = x * 88,
                            minHeightDp = y * 108,
                            maxWidthDp = x * 96,
                            maxHeightDp = y * 120
                        )
                    },
                    goToPage = goToPage,
                    onWentToPage = { goToPage = null },
                    modifier = Modifier.offset(y = -(maxHeight * drawer.value * 0.06f))
                )

                if (drawer.value > 0.001f) {
                    Box(Modifier.offset(y = maxHeight * (1f - drawer.value))) {
                        Drawer(
                            apps = apps,
                            rowMode = rowMode,
                            rowApps = rowApps,
                            progress = drawer.value,
                            onRowModeChange = { rowMode = it },
                            onLaunch = { app ->
                                repo.launch(app, null)
                                scope.launch { drawer.animateTo(0f) }
                            }
                        )
                    }
                }

                if (overviewOpen) {
                    PageOverview(
                        layout = layout,
                        currentPage = goToPage ?: layout.homePage,
                        onJump = { index ->
                            goToPage = index
                            overviewOpen = false
                        },
                        onSetHome = { store.setHomePage(it) },
                        onDelete = { store.removePage(it) },
                        onMove = { from, to -> store.movePage(from, to) },
                        onAddPage = { store.addPage() },
                        onDismiss = { overviewOpen = false }
                    )
                }

                if (settingsOpen) {
                    SettingsSheet(
                        onDismiss = { settingsOpen = false },
                        onAddWidget = {
                            pendingWidgetPage = layout.homePage
                            host.pick(this@MainActivity)
                        },
                        onAddPage = { store.addPage() },
                        onNotificationAccess = { openNotificationAccess() },
                        onUsageAccess = { openUsageAccess() },
                        onSetDefaultLauncher = { openHomeSettings() }
                    )
                }

                BackHandler(
                    enabled = drawer.value > 0.01f || settingsOpen || overviewOpen
                ) {
                    settingsOpen = false
                    overviewOpen = false
                    scope.launch { drawer.animateTo(0f) }
                }
            }
        }
    }

    /**
     * Everything a drop can mean, in one place.
     *
     * The source is only removed once the destination has accepted, so a drop that lands
     * nowhere puts the item back where it was rather than eating it.
     */
    private fun handleDrop(lift: LiftState, drop: Drop, currentPage: Int) {
        when (drop) {
            Drop.None -> Unit

            Drop.Remove -> when {
                lift.fromDock != null ->
                    store.setDock(store.layout.value.dock.filterIndexed { i, _ -> i != lift.fromDock })

                lift.fromFolder != null && lift.folderKey != null ->
                    store.removeFromFolder(lift.page, lift.fromFolder, lift.folderKey)

                else -> store.removeItem(lift.page, lift.item)
            }

            is Drop.Dock -> {
                val dock = store.layout.value.dock.toMutableList()
                if (lift.fromDock != null) {
                    val moving = dock.removeAt(lift.fromDock.coerceIn(0, dock.lastIndex))
                    dock.add(drop.index.coerceIn(0, dock.size), moving)
                    store.setDock(dock)
                } else {
                    if (dock.size >= 5) dock[drop.index.coerceIn(0, dock.lastIndex)] = lift.item.slot
                    else dock.add(drop.index.coerceIn(0, dock.size), lift.item.slot)
                    store.setDock(dock)
                    detachSource(lift)
                }
            }

            is Drop.Cell -> {
                val onto = drop.onto
                val canFold = onto != null &&
                    onto != lift.item &&
                    onto.slot !is Slot.Widget &&
                    lift.item.slot !is Slot.Widget

                when {
                    lift.fromDock != null || lift.fromFolder != null -> {
                        detachSource(lift)
                        if (canFold && onto != null) {
                            store.makeFolder(currentPage, onto, lift.item.slot, null)
                        } else {
                            store.place(
                                currentPage,
                                Placed(drop.col, drop.row, 1, 1, lift.item.slot)
                            )
                        }
                    }

                    canFold && onto != null -> {
                        store.makeFolder(
                            currentPage,
                            onto,
                            lift.item.slot,
                            if (lift.page == currentPage) lift.item else null
                        )
                        if (lift.page != currentPage) store.removeItem(lift.page, lift.item)
                    }

                    else -> store.moveBetweenPages(
                        lift.page,
                        lift.item,
                        currentPage,
                        drop.col,
                        drop.row
                    )
                }
            }
        }
    }

    private fun detachSource(lift: LiftState) {
        when {
            lift.fromDock != null ->
                store.setDock(store.layout.value.dock.filterIndexed { i, _ -> i != lift.fromDock })

            lift.fromFolder != null && lift.folderKey != null ->
                store.removeFromFolder(lift.page, lift.fromFolder, lift.folderKey)
        }
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        repo.stop()
    }

    @Deprecated("Widget picking predates the Activity Result API and still uses this path.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1

        when (requestCode) {
            REQ_PICK_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && id >= 0) {
                    if (!host.configureIfNeeded(this, id)) placeWidget(id)
                } else if (id >= 0) {
                    host.deleteId(id)
                }
            }

            REQ_BIND_WIDGET, REQ_CONFIGURE_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && id >= 0) placeWidget(id)
                else if (id >= 0) host.deleteId(id)
            }
        }
    }

    private fun placeWidget(id: Int) {
        val (spanX, spanY) = host.defaultSpan(id, cellWidthDp = 90, cellHeightDp = 110)
        val free = store.firstFreeSlot(pendingWidgetPage, spanX, spanY)
        if (free == null) {
            // Nowhere on this page, so it gets a page of its own rather than displacing anything.
            store.addPage()
            val page = store.layout.value.pages.lastIndex
            store.place(page, Placed(0, 0, spanX, spanY, Slot.Widget(id)))
            return
        }
        store.place(
            pendingWidgetPage,
            Placed(free.first, free.second, spanX, spanY, Slot.Widget(id))
        )
    }

    private fun openNotificationAccess() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun openUsageAccess() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun openHomeSettings() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        }
    }
}
