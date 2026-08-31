package com.cairn.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cairn.launcher.data.GRID_COLS
import com.cairn.launcher.data.GRID_ROWS
import com.cairn.launcher.data.Layout as CairnLayout
import com.cairn.launcher.data.Page
import com.cairn.launcher.data.Slot

/**
 * Pinch in and every page is on screen at once.
 *
 * With unlimited pages, swiping nine times to reach page nine is miserable, and a row of dots
 * stops being readable well before that. This is the answer to both. Pages are drawn as the
 * shape of what is on them rather than as thumbnails of it: at this size an icon is four pixels,
 * so a picture of the icon tells you nothing that its position does not.
 */
@Composable
fun PageOverview(
    layout: CairnLayout,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onSetHome: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddPage: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Cairn.Surface.copy(alpha = 0.97f))
            .clickableNoRipple { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth()) {

            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Cairn.PagePadding
                )
            ) {
                itemsIndexed(layout.pages) { index, page ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PageThumbnail(
                            page = page,
                            isHome = index == layout.homePage,
                            isCurrent = index == currentPage,
                            modifier = Modifier
                                .width(96.dp)
                                .clickableNoRipple { onJump(index) }
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (index > 0) {
                                Text(
                                    "left",
                                    color = Cairn.OnSurfaceSecondary,
                                    fontSize = Cairn.LabelSize,
                                    modifier = Modifier.clickableNoRipple { onMove(index, index - 1) }
                                )
                            }
                            if (index < layout.pages.lastIndex) {
                                Text(
                                    "right",
                                    color = Cairn.OnSurfaceSecondary,
                                    fontSize = Cairn.LabelSize,
                                    modifier = Modifier.clickableNoRipple { onMove(index, index + 1) }
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            if (index == layout.homePage) "home" else "set home",
                            color = if (index == layout.homePage) Cairn.OnSurface
                            else Cairn.OnSurfaceSecondary,
                            fontSize = Cairn.LabelSize,
                            modifier = Modifier.clickableNoRipple { onSetHome(index) }
                        )

                        if (layout.pages.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "remove",
                                color = Cairn.Accent,
                                fontSize = Cairn.LabelSize,
                                modifier = Modifier.clickableNoRipple { onDelete(index) }
                            )
                        }
                    }
                }

                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(96.dp)
                                .aspectRatio(GRID_COLS.toFloat() / GRID_ROWS)
                                .border(1.dp, Cairn.SurfaceHairline)
                                .clickableNoRipple { onAddPage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("add", color = Cairn.OnSurfaceSecondary, fontSize = Cairn.LabelSize)
                        }
                    }
                }
            }

            Text(
                text = "tap a page to go there",
                color = Cairn.OnSurfaceSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Cairn.PagePadding)
            )
        }
    }
}

/** The shape of a page: occupied cells filled, gaps left as gaps, widgets drawn at their span. */
@Composable
private fun PageThumbnail(
    page: Page,
    isHome: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .aspectRatio(GRID_COLS.toFloat() / GRID_ROWS)
            .border(
                width = if (isCurrent) 1.dp else 1.dp,
                color = if (isCurrent) Cairn.OnSurface else Cairn.SurfaceHairline
            )
            .padding(4.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until GRID_ROWS) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0 until GRID_COLS) {
                        val item = page.items.firstOrNull {
                            col in it.col until (it.col + it.spanX) &&
                                row in it.row until (it.row + it.spanY)
                        }
                        val fill = when (item?.slot) {
                            null -> Color.Transparent
                            is Slot.Widget -> Cairn.OnSurface.copy(alpha = 0.28f)
                            else -> Cairn.OnSurface.copy(alpha = 0.7f)
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(1.dp)
                                .background(fill)
                        )
                    }
                }
            }
        }
        if (isHome) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(4.dp)
                    .height(4.dp)
                    .background(Cairn.Accent)
            )
        }
    }
}
