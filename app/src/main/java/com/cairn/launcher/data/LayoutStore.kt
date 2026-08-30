package com.cairn.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reads and writes layout.json.
 *
 * Every write also drops a copy in files/backups/, which is what the GitHub sync picks up.
 * The layout is deliberately never repacked: a gap you left in the grid stays a gap.
 */
class LayoutStore(private val context: Context) {

    private val file = File(context.filesDir, "layout.json")
    private val backups = File(context.filesDir, "backups").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _layout = MutableStateFlow(Layout(listOf(Page(emptyList())), emptyList(), 0))
    val layout: StateFlow<Layout> = _layout

    fun load() {
        val text = runCatching { file.readText() }.getOrNull()
        val parsed = text?.let { layoutFromJson(it) }
        if (parsed != null) _layout.value = parsed
    }

    /** Builds a first-run layout: everything installed, alphabetical, four across. */
    fun seedIfEmpty(apps: List<AppInfo>) {
        if (file.exists() || apps.isEmpty()) return
        val perPage = GRID_COLS * GRID_ROWS
        val dockKeys = apps.take(5).map { it.key }
        val rest = apps.drop(5)
        val pages = rest.chunked(perPage).map { chunk ->
            Page(chunk.mapIndexed { i, app ->
                Placed(
                    col = i % GRID_COLS,
                    row = i / GRID_COLS,
                    spanX = 1,
                    spanY = 1,
                    slot = Slot.App(app.key)
                )
            })
        }.ifEmpty { listOf(Page(emptyList())) }
        update(Layout(pages, dockKeys.map { Slot.App(it) }, 0))
    }

    fun update(next: Layout) {
        _layout.value = next
        scope.launch {
            val json = next.toJson()
            runCatching { file.writeText(json) }
            runCatching { File(backups, "layout-latest.json").writeText(json) }
        }
    }

    fun addPage() {
        val l = _layout.value
        update(l.copy(pages = l.pages + Page(emptyList())))
    }

    fun removePage(index: Int) {
        val l = _layout.value
        if (l.pages.size <= 1) return
        val pages = l.pages.toMutableList().also { it.removeAt(index) }
        val home = l.homePage.coerceAtMost(pages.lastIndex)
        update(l.copy(pages = pages, homePage = home))
    }

    fun movePage(from: Int, to: Int) {
        val l = _layout.value
        if (from !in l.pages.indices || to !in l.pages.indices) return
        val pages = l.pages.toMutableList()
        pages.add(to, pages.removeAt(from))
        update(l.copy(pages = pages))
    }

    fun setHomePage(index: Int) {
        val l = _layout.value
        if (index in l.pages.indices) update(l.copy(homePage = index))
    }

    fun place(pageIndex: Int, placed: Placed) {
        val l = _layout.value
        val pages = l.pages.toMutableList()
        val page = pages.getOrNull(pageIndex) ?: return
        val cleared = page.items.filterNot { overlaps(it, placed) }
        pages[pageIndex] = Page(cleared + placed)
        update(l.copy(pages = pages))
    }

    /** The first cell where a spanX by spanY block fits without displacing anything. */
    fun firstFreeSlot(pageIndex: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val page = _layout.value.pages.getOrNull(pageIndex) ?: return null
        for (row in 0..(GRID_ROWS - spanY)) {
            for (col in 0..(GRID_COLS - spanX)) {
                val candidate = Placed(col, row, spanX, spanY, Slot.App(""))
                if (page.items.none { overlaps(it, candidate) }) return col to row
            }
        }
        return null
    }

    fun removeAt(pageIndex: Int, col: Int, row: Int) {
        val l = _layout.value
        val pages = l.pages.toMutableList()
        val page = pages.getOrNull(pageIndex) ?: return
        pages[pageIndex] = Page(page.items.filterNot { it.col == col && it.row == row })
        update(l.copy(pages = pages))
    }

    /** Trailing empty pages are dropped, but a gap inside a page is left alone. */
    fun pruneTrailingEmptyPages() {
        val l = _layout.value
        var pages = l.pages
        while (pages.size > 1 && pages.last().items.isEmpty()) pages = pages.dropLast(1)
        if (pages.size != l.pages.size) {
            update(l.copy(pages = pages, homePage = l.homePage.coerceAtMost(pages.lastIndex)))
        }
    }

    private fun overlaps(a: Placed, b: Placed): Boolean {
        val ax = a.col until (a.col + a.spanX)
        val ay = a.row until (a.row + a.spanY)
        val bx = b.col until (b.col + b.spanX)
        val by = b.row until (b.row + b.spanY)
        return ax.intersect(bx).isNotEmpty() && ay.intersect(by).isNotEmpty()
    }

    fun exportTo(target: File): Boolean =
        runCatching { target.writeText(_layout.value.toJson()); true }.getOrDefault(false)

    fun importFrom(target: File): Boolean {
        val parsed = runCatching { layoutFromJson(target.readText()) }.getOrNull() ?: return false
        update(parsed)
        return true
    }
}
