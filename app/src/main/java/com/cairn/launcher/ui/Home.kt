package com.cairn.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cairn.launcher.data.AppInfo
import com.cairn.launcher.data.GRID_COLS
import com.cairn.launcher.data.GRID_ROWS
import com.cairn.launcher.data.Page
import com.cairn.launcher.data.Placed
import com.cairn.launcher.data.Slot
import com.cairn.launcher.data.itemAt
import com.cairn.launcher.notify.Notice
import com.cairn.launcher.notify.NotificationState
import com.cairn.launcher.widgets.CairnWidgetHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.cairn.launcher.data.Layout as CairnLayout

/** Which item, on which page, is currently pulled open. */
data class PullTarget(val page: Int, val index: Int)

/**
 * An item currently in the air.
 *
 * [tileRoot] is where the tile sat when you picked it up and [grab] is where in it your finger
 * landed, so the point you grabbed stays under your finger instead of the tile's centre snapping
 * to the cursor.
 */
data class LiftState(
    val page: Int,
    val item: Placed,
    val fromDock: Int? = null,
    val fromFolder: Placed? = null,
    val folderKey: String? = null,
    val tileRoot: Offset = Offset.Zero,
    val grab: Offset = Offset.Zero,
    val moved: Offset = Offset.Zero,
    val size: Offset = Offset.Zero
) {
    val pointer: Offset get() = tileRoot + grab + moved
    val topLeft: Offset get() = tileRoot + moved
}

/** Where a lifted item would land if you let go now. */
sealed interface Drop {
    data object None : Drop
    data object Remove : Drop
    data class Dock(val index: Int) : Drop
    data class Cell(val col: Int, val row: Int, val onto: Placed?) : Drop
}

private val PANEL_HEIGHT = 132.dp

@Composable
fun Home(
    apps: List<AppInfo>,
    layout: CairnLayout,
    host: CairnWidgetHost,
    onLaunch: (AppInfo) -> Unit,
    onShortcuts: (String) -> List<android.content.pm.ShortcutInfo>,
    onStartShortcut: (android.content.pm.ShortcutInfo) -> Unit,
    onReply: (Notice, String) -> Unit,
    onDrawerDrag: (Float) -> Unit,
    onDrawerRelease: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOverview: () -> Unit,
    onDrop: (LiftState, Drop, Int) -> Unit,
    onRenameFolder: (Int, Placed, String) -> Unit,
    onResizeWidget: (Int, Placed, Int, Int) -> Unit,
    goToPage: Int?,
    onWentToPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val pageCount = layout.pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = layout.homePage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var pull by remember { mutableStateOf<PullTarget?>(null) }
    val pullProgress = remember { Animatable(0f) }
    var lift by remember { mutableStateOf<LiftState?>(null) }
    var resizing by remember { mutableStateOf<Placed?>(null) }

    var homeRoot by remember { mutableStateOf(Offset.Zero) }
    var gridRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var removeRect by remember { mutableStateOf(Rect.Zero) }
    val tileBounds = remember { mutableMapOf<String, Pair<Offset, Offset>>() }

    val cellW = if (gridRect.width > 0f) gridRect.width / GRID_COLS else 1f
    val cellH = if (gridRect.height > 0f) gridRect.height / GRID_ROWS else 1f

    fun resolve(point: Offset): Drop = when {
        removeRect.width > 0f && removeRect.contains(point) -> Drop.Remove
        dockRect.width > 0f && dockRect.contains(point) ->
            Drop.Dock(((point.x - dockRect.left) / (dockRect.width / 5f)).toInt().coerceIn(0, 4))
        gridRect.width > 0f && gridRect.contains(point) -> {
            val col = ((point.x - gridRect.left) / cellW).toInt().coerceIn(0, GRID_COLS - 1)
            val row = ((point.y - gridRect.top) / cellH).toInt().coerceIn(0, GRID_ROWS - 1)
            Drop.Cell(col, row, layout.pages.getOrNull(pagerState.currentPage)?.itemAt(col, row))
        }
        else -> Drop.None
    }

    fun release() {
        lift?.let { onDrop(it, resolve(it.pointer), pagerState.currentPage) }
        lift = null
    }

    // Carry something to the edge and wait, and you go to the next page, the way dragging a file
    // to the edge of a window does.
    val current = lift
    val edge = when {
        current == null || gridRect.width <= 0f -> 0
        current.pointer.x < gridRect.left + 36f -> -1
        current.pointer.x > gridRect.right - 36f -> 1
        else -> 0
    }
    LaunchedEffect(edge, pagerState.currentPage) {
        if (edge != 0) {
            delay(450)
            val next = pagerState.currentPage + edge
            if (next in 0 until pageCount) pagerState.animateScrollToPage(next)
        }
    }

    LaunchedEffect(goToPage) {
        val target = goToPage ?: return@LaunchedEffect
        if (target in 0 until pageCount) pagerState.animateScrollToPage(target)
        onWentToPage()
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { homeRoot = it.positionInRoot() }
    ) {
        Column(Modifier.fillMaxSize()) {

            ClockLine(
                modifier = Modifier
                    .padding(horizontal = Cairn.PagePadding)
                    .padding(top = 8.dp, bottom = 20.dp)
                    .clickableNoRipple(onOpenSettings)
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = lift == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pinchIn { onOpenOverview() }
                    // Swipe up anywhere on the page. Tiles consume downward drags for the pull
                    // and consume nothing else, so an upward drag reaches this untouched.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { onDrawerRelease() },
                            onDragCancel = { onDrawerRelease() }
                        ) { change, dragAmount ->
                            if (dragAmount < 0f) {
                                change.consume()
                                onDrawerDrag(-dragAmount)
                            }
                        }
                    }
            ) { pageIndex ->
                PageGrid(
                    page = layout.pages.getOrNull(pageIndex) ?: Page(emptyList()),
                    pageIndex = pageIndex,
                    isCurrent = pageIndex == pagerState.currentPage,
                    byKey = byKey,
                    host = host,
                    pull = pull?.takeIf { it.page == pageIndex },
                    pullProgress = pullProgress.value,
                    lift = lift,
                    resizing = resizing?.takeIf { pageIndex == pagerState.currentPage },
                    onBounds = { rect -> if (pageIndex == pagerState.currentPage) gridRect = rect },
                    onTileBounds = { index, pos, size ->
                        tileBounds["$pageIndex:$index"] = pos to size
                    },
                    onLaunch = onLaunch,
                    onShortcuts = onShortcuts,
                    onStartShortcut = onStartShortcut,
                    onReply = onReply,
                    onRenameFolder = { placed, name -> onRenameFolder(pageIndex, placed, name) },
                    onResize = { placed, x, y -> onResizeWidget(pageIndex, placed, x, y) },
                    onEndResize = { resizing = null },
                    onPullStart = { index ->
                        val item = layout.pages.getOrNull(pageIndex)?.items?.getOrNull(index)
                        if (item != null && item.slot is Slot.Widget) {
                            resizing = item
                        } else if (pull?.index != index || pull?.page != pageIndex) {
                            pull = PullTarget(pageIndex, index)
                            scope.launch { pullProgress.snapTo(0f) }
                        }
                    },
                    onPullDelta = { fraction ->
                        scope.launch { pullProgress.snapTo(fraction.coerceIn(0f, 1f)) }
                    },
                    onPullRelease = {
                        scope.launch {
                            val to = if (pullProgress.value > 0.45f) 1f else 0f
                            pullProgress.animateTo(to)
                            if (to == 0f) pull = null
                        }
                    },
                    onLiftStart = { index, grab ->
                        val item = layout.pages.getOrNull(pageIndex)?.items?.getOrNull(index)
                        val bounds = tileBounds["$pageIndex:$index"]
                        if (item != null && bounds != null) {
                            lift = LiftState(
                                page = pageIndex,
                                item = item,
                                tileRoot = bounds.first,
                                grab = grab,
                                size = bounds.second
                            )
                        }
                    },
                    onLiftMove = { moved -> lift = lift?.copy(moved = moved) },
                    onLiftDrop = { release() },
                    onFolderChildLift = { folderItem, key, grab, root, size ->
                        lift = LiftState(
                            page = pageIndex,
                            item = folderItem.copy(slot = Slot.App(key)),
                            fromFolder = folderItem,
                            folderKey = key,
                            tileRoot = root,
                            grab = grab,
                            size = size
                        )
                    }
                )
            }

            PageRule(
                pageCount = pageCount,
                offset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                modifier = Modifier
                    .padding(horizontal = Cairn.PagePadding)
                    .padding(bottom = 10.dp)
            )

            DrawerHandle(onDrag = onDrawerDrag, onRelease = onDrawerRelease)

            Dock(
                slots = layout.dock,
                byKey = byKey,
                onLaunch = onLaunch,
                onBounds = { dockRect = it },
                onLift = { index, slot, grab, root, size ->
                    lift = LiftState(
                        page = pagerState.currentPage,
                        item = Placed(0, 0, 1, 1, slot),
                        fromDock = index,
                        tileRoot = root,
                        grab = grab,
                        size = size
                    )
                },
                onLiftMove = { moved -> lift = lift?.copy(moved = moved) },
                onLiftDrop = { release() },
                modifier = Modifier.padding(
                    start = Cairn.PagePadding,
                    end = Cairn.PagePadding,
                    top = 6.dp,
                    bottom = 16.dp
                )
            )
        }

        // Only while something is in the air, and it is a word rather than a bin icon.
        if (current != null) {
            Text(
                text = "Remove",
                color = if (removeRect.contains(current.pointer)) Cairn.Accent
                else secondaryTextColor(),
                fontSize = Cairn.DateSize,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .onGloballyPositioned {
                        val p = it.positionInRoot()
                        removeRect = Rect(
                            p.x - 48f,
                            p.y - 24f,
                            p.x + it.size.width + 48f,
                            p.y + it.size.height + 24f
                        )
                    }
            )
        }

        // The item itself, drawn under your finger and above everything else.
        current?.let { l ->
            val app = (l.item.slot as? Slot.App)?.key?.let { byKey[it] }
            val local = l.topLeft - homeRoot
            Box(
                Modifier
                    .offset(
                        x = with(density) { local.x.toDp() },
                        y = with(density) { local.y.toDp() }
                    )
                    .size(
                        width = with(density) { l.size.x.coerceAtLeast(1f).toDp() },
                        height = with(density) { l.size.y.coerceAtLeast(1f).toDp() }
                    )
                    .graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                        alpha = 0.92f
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                when (val slot = l.item.slot) {
                    is Slot.Folder -> FolderTile(slot, byKey)
                    else -> if (app != null) IconTile(app)
                }
            }
        }
    }
}

/**
 * One page. Cells are placed absolutely rather than flowed, so a gap you left stays a gap and a
 * widget can span whatever it needs. Rows below an open panel are pushed down as you pull, so
 * you watch the room being made rather than finding it already made.
 */
@Composable
private fun PageGrid(
    page: Page,
    pageIndex: Int,
    isCurrent: Boolean,
    byKey: Map<String, AppInfo>,
    host: CairnWidgetHost,
    pull: PullTarget?,
    pullProgress: Float,
    lift: LiftState?,
    resizing: Placed?,
    onBounds: (Rect) -> Unit,
    onTileBounds: (Int, Offset, Offset) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onShortcuts: (String) -> List<android.content.pm.ShortcutInfo>,
    onStartShortcut: (android.content.pm.ShortcutInfo) -> Unit,
    onReply: (Notice, String) -> Unit,
    onRenameFolder: (Placed, String) -> Unit,
    onResize: (Placed, Int, Int) -> Unit,
    onEndResize: () -> Unit,
    onPullStart: (Int) -> Unit,
    onPullDelta: (Float) -> Unit,
    onPullRelease: () -> Unit,
    onLiftStart: (Int, Offset) -> Unit,
    onLiftMove: (Offset) -> Unit,
    onLiftDrop: () -> Unit,
    onFolderChildLift: (Placed, String, Offset, Offset, Offset) -> Unit
) {
    val density = LocalDensity.current
    val panelPx = with(density) { PANEL_HEIGHT.toPx() }
    val pulledItem = pull?.let { page.items.getOrNull(it.index) }
    val pulledRow = pulledItem?.row ?: -1

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Cairn.PagePadding)
    ) {
        // A row is a fixed height, not a share of the page. Dividing the page by the row count
        // left a 48dp icon floating in the middle of a 140dp cell, which is most of why this
        // looked nothing like the drawings.
        val cellWidth = maxWidth / GRID_COLS
        val cellHeight = Cairn.RowHeight
        val gridHeight = cellHeight * GRID_ROWS
        val gridTop = (maxHeight - gridHeight).coerceAtLeast(0.dp)
        val rowPx = with(density) { cellHeight.roundToPx() }

        Layout(
            content = {
                page.items.forEachIndexed { index, placed ->
                    Box(
                        Modifier
                            .layoutId(index)
                            .onGloballyPositioned {
                                onTileBounds(
                                    index,
                                    it.positionInRoot(),
                                    Offset(it.size.width.toFloat(), it.size.height.toFloat())
                                )
                            }
                    ) {
                        val inTheAir = lift != null &&
                            lift.page == pageIndex &&
                            lift.fromFolder == null &&
                            lift.fromDock == null &&
                            lift.item == placed
                        if (!inTheAir) {
                            SlotContent(
                                placed = placed,
                                byKey = byKey,
                                host = host,
                                onLaunch = onLaunch,
                                onPullStart = { onPullStart(index) },
                                onPullDelta = { travelled -> onPullDelta(travelled / panelPx) },
                                onPullRelease = onPullRelease,
                                onLiftStart = { grab -> onLiftStart(index, grab) },
                                onLiftMove = onLiftMove,
                                onLiftDrop = onLiftDrop
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .align(Alignment.BottomStart)
                .onGloballyPositioned {
                    val p = it.positionInRoot()
                    onBounds(Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height))
                }
        ) { measurables, constraints ->
            val w = if (GRID_COLS > 0) constraints.maxWidth / GRID_COLS else constraints.maxWidth
            val h = rowPx
            val shift = (pullProgress * panelPx).toInt()

            val measured = measurables.map { m ->
                val index = m.layoutId as? Int ?: 0
                val placed = page.items.getOrNull(index)
                val spanX = (placed?.spanX ?: 1).coerceAtLeast(1)
                val spanY = (placed?.spanY ?: 1).coerceAtLeast(1)
                placed to m.measure(Constraints.fixed(w * spanX, h * spanY))
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                measured.forEach { (placed, placeable) ->
                    if (placed == null) return@forEach
                    val extra = if (pulledRow >= 0 && placed.row > pulledRow) shift else 0
                    placeable.place(placed.col * w, placed.row * h + extra)
                }
            }
        }

        if (resizing != null) {
            ResizeHandles(
                item = resizing,
                topInset = gridTop,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                onResize = { x, y -> onResize(resizing, x, y) },
                onDone = onEndResize
            )
        }

        if (pulledItem != null && pullProgress > 0.01f) {
            val app = (pulledItem.slot as? Slot.App)?.key?.let { byKey[it] }
            val folder = pulledItem.slot as? Slot.Folder
            val notices by NotificationState.notices.collectAsState()
            val notice = app?.let { notices[it.packageName] }
            val shortcuts = remember(app?.packageName, notice == null) {
                if (app != null && notice == null) onShortcuts(app.packageName) else emptyList()
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = gridTop + cellHeight * (pulledRow + 1))
                    .height(PANEL_HEIGHT * pullProgress)
            ) {
                PullPanel(
                    app = app,
                    folder = folder,
                    notice = notice,
                    byKey = byKey,
                    shortcuts = shortcuts,
                    onLaunch = onLaunch,
                    onStartShortcut = onStartShortcut,
                    onReply = onReply,
                    onRename = { name -> onRenameFolder(pulledItem, name) },
                    onChildLift = { key, grab, root, size ->
                        onFolderChildLift(pulledItem, key, grab, root, size)
                    },
                    onChildLiftMove = onLiftMove,
                    onChildLiftDrop = onLiftDrop
                )
            }
        }
    }
}

/**
 * Two bars, one on the right edge and one on the bottom. Dragging either one moves the widget's
 * span by whole cells, and the widget is told its new box in dp so it re-lays itself out rather
 * than being stretched.
 */
@Composable
private fun ResizeHandles(
    item: Placed,
    topInset: Dp,
    cellWidth: Dp,
    cellHeight: Dp,
    onResize: (Int, Int) -> Unit,
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    var spanX by remember(item) { mutableStateOf(item.spanX) }
    var spanY by remember(item) { mutableStateOf(item.spanY) }

    Box(
        Modifier
            .offset(x = cellWidth * item.col, y = topInset + cellHeight * item.row)
            .width(cellWidth * spanX)
            .height(cellHeight * spanY)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(3.dp)
                .height(28.dp)
                .background(wallpaperTextColor())
                .pointerInput(item) {
                    var acc = 0f
                    val step = with(density) { cellWidth.toPx() }
                    detectHorizontalDragGestures(onDragEnd = { onDone() }) { change, amount ->
                        change.consume()
                        acc += amount
                        if (abs(acc) > step) {
                            spanX = (spanX + if (acc > 0) 1 else -1)
                                .coerceIn(1, GRID_COLS - item.col)
                            onResize(spanX, spanY)
                            acc = 0f
                        }
                    }
                }
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .height(3.dp)
                .width(28.dp)
                .background(wallpaperTextColor())
                .pointerInput(item) {
                    var acc = 0f
                    val step = with(density) { cellHeight.toPx() }
                    detectVerticalDragGestures(onDragEnd = { onDone() }) { change, amount ->
                        change.consume()
                        acc += amount
                        if (abs(acc) > step) {
                            spanY = (spanY + if (acc > 0) 1 else -1)
                                .coerceIn(1, GRID_ROWS - item.row)
                            onResize(spanX, spanY)
                            acc = 0f
                        }
                    }
                }
        )
    }
}

@Composable
private fun SlotContent(
    placed: Placed,
    byKey: Map<String, AppInfo>,
    host: CairnWidgetHost,
    onLaunch: (AppInfo) -> Unit,
    onPullStart: () -> Unit,
    onPullDelta: (Float) -> Unit,
    onPullRelease: () -> Unit,
    onLiftStart: (Offset) -> Unit,
    onLiftMove: (Offset) -> Unit,
    onLiftDrop: () -> Unit
) {
    when (val slot = placed.slot) {
        is Slot.App -> {
            val app = byKey[slot.key]
            if (app != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .tileGestures(
                            onTap = { onLaunch(app) },
                            onPullStart = onPullStart,
                            onPullDelta = onPullDelta,
                            onPullRelease = onPullRelease,
                            onLiftStart = onLiftStart,
                            onLiftMove = onLiftMove,
                            onLiftDrop = onLiftDrop
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    IconTile(app)
                }
            }
        }

        is Slot.Folder -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .tileGestures(
                        onTap = {
                            onPullStart()
                            onPullDelta(1f)
                            onPullRelease()
                        },
                        onPullStart = onPullStart,
                        onPullDelta = onPullDelta,
                        onPullRelease = onPullRelease,
                        onLiftStart = onLiftStart,
                        onLiftMove = onLiftMove,
                        onLiftDrop = onLiftDrop
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                FolderTile(slot, byKey)
            }
        }

        is Slot.Widget -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .tileGestures(
                        onTap = { },
                        onPullStart = onPullStart,
                        onPullDelta = { },
                        onPullRelease = { },
                        onLiftStart = onLiftStart,
                        onLiftMove = onLiftMove,
                        onLiftDrop = onLiftDrop
                    )
            ) {
                WidgetSlotView(host = host, widgetId = slot.widgetId)
            }
        }
    }
}

/**
 * One place for system truth, with a tiny fixed vocabulary. The mark is present or it is not,
 * and the absence of marks means everything is fine. Tapping here is the way in to settings,
 * because there is no long press anywhere in Cairn.
 */
@Composable
private fun ClockLine(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = Date()
        }
    }
    val time = remember(now) { SimpleDateFormat("H:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, color = wallpaperTextColor(), fontSize = Cairn.ClockSize)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .background(Cairn.Accent)
            )
        }
        Text(date, color = secondaryTextColor(), fontSize = Cairn.DateSize)
    }
}

/** A hairline with a moving segment. Dots stop being readable past about eight pages. */
@Composable
private fun PageRule(pageCount: Int, offset: Float, modifier: Modifier = Modifier) {
    if (pageCount <= 1) {
        Spacer(modifier.height(1.dp))
        return
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(hairlineColor().copy(alpha = 0.3f))
    ) {
        val segment = maxWidth / pageCount
        Box(
            Modifier
                .width(segment)
                .offset(x = segment * offset.coerceIn(0f, (pageCount - 1).toFloat()))
                .height(1.dp)
                .background(wallpaperTextColor())
        )
    }
}

@Composable
private fun Dock(
    slots: List<Slot>,
    byKey: Map<String, AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onBounds: (Rect) -> Unit,
    onLift: (Int, Slot, Offset, Offset, Offset) -> Unit,
    onLiftMove: (Offset) -> Unit,
    onLiftDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                val p = it.positionInRoot()
                onBounds(Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height))
            },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        slots.forEachIndexed { index, slot ->
            val app = (slot as? Slot.App)?.key?.let { byKey[it] }
            if (app != null) {
                var root by remember { mutableStateOf(Offset.Zero) }
                var size by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier
                        .onGloballyPositioned {
                            root = it.positionInRoot()
                            size = Offset(it.size.width.toFloat(), it.size.height.toFloat())
                        }
                        .tileGestures(
                            onTap = { onLaunch(app) },
                            onPullStart = { },
                            onPullDelta = { },
                            onPullRelease = { },
                            onLiftStart = { grab -> onLift(index, slot, grab, root, size) },
                            onLiftMove = onLiftMove,
                            onLiftDrop = onLiftDrop
                        )
                ) {
                    IconTile(app, showLabel = false)
                }
            }
        }
    }
}

/** Drag it and the drawer is wherever your finger is. Let go and it decides. */
@Composable
private fun DrawerHandle(onDrag: (Float) -> Unit, onRelease: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(-dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(hairlineColor())
        )
    }
}
