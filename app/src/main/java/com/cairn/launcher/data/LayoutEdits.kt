package com.cairn.launcher.data

/**
 * Every structural edit the launcher can make, in one place.
 *
 * Nothing here ever repacks a page. Moving an item out of a cell leaves that cell empty, and it
 * stays empty until you put something in it. A launcher that treats your arrangement as a
 * suggestion is the thing this is written against.
 */

private fun LayoutStore.mutate(pageIndex: Int, block: (List<Placed>) -> List<Placed>) {
    val current = layout.value
    val page = current.pages.getOrNull(pageIndex) ?: return
    val pages = current.pages.toMutableList()
    pages[pageIndex] = Page(block(page.items))
    update(current.copy(pages = pages))
}

fun LayoutStore.removeItem(pageIndex: Int, item: Placed) {
    mutate(pageIndex) { items -> items.filterNot { it == item } }
}

/** Moves an item to a cell on the same page. Anything already sitting there is displaced out. */
fun LayoutStore.moveWithinPage(pageIndex: Int, item: Placed, col: Int, row: Int) {
    val moved = item.copy(col = col, row = row)
    mutate(pageIndex) { items ->
        items.filterNot { it == item || occupies(it, moved) } + moved
    }
}

fun LayoutStore.moveBetweenPages(fromPage: Int, item: Placed, toPage: Int, col: Int, row: Int) {
    if (fromPage == toPage) {
        moveWithinPage(fromPage, item, col, row)
        return
    }
    val current = layout.value
    val pages = current.pages.toMutableList()
    val src = pages.getOrNull(fromPage) ?: return
    val dst = pages.getOrNull(toPage) ?: return
    val moved = item.copy(col = col, row = row)
    pages[fromPage] = Page(src.items.filterNot { it == item })
    pages[toPage] = Page(dst.items.filterNot { occupies(it, moved) } + moved)
    update(current.copy(pages = pages))
}

fun LayoutStore.setDock(slots: List<Slot>) {
    update(layout.value.copy(dock = slots.take(5)))
}

/**
 * Dropping one app onto another makes a folder of the two. The name starts as the word Folder
 * because guessing a category from two package names is the kind of cleverness that is wrong
 * often enough to be annoying.
 */
fun LayoutStore.makeFolder(pageIndex: Int, target: Placed, dragged: Slot, draggedFrom: Placed?) {
    val targetSlot = target.slot
    val keys = buildList {
        when (targetSlot) {
            is Slot.App -> add(targetSlot.key)
            is Slot.Folder -> addAll(targetSlot.keys)
            is Slot.Widget -> return
        }
        when (dragged) {
            is Slot.App -> add(dragged.key)
            is Slot.Folder -> addAll(dragged.keys)
            is Slot.Widget -> return
        }
    }.distinct()

    val name = (targetSlot as? Slot.Folder)?.name ?: "Folder"
    val folder = target.copy(slot = Slot.Folder(name, keys))
    mutate(pageIndex) { items ->
        items.filterNot { it == target || (draggedFrom != null && it == draggedFrom) } + folder
    }
}

fun LayoutStore.renameFolder(pageIndex: Int, item: Placed, name: String) {
    val folder = item.slot as? Slot.Folder ?: return
    val renamed = item.copy(slot = folder.copy(name = name.ifBlank { "Folder" }))
    mutate(pageIndex) { items -> items.map { if (it == item) renamed else it } }
}

/**
 * Taking the last app out of a folder leaves a folder of one, which is silly, so a folder that
 * drops to a single app becomes that app again.
 */
fun LayoutStore.removeFromFolder(pageIndex: Int, item: Placed, key: String) {
    val folder = item.slot as? Slot.Folder ?: return
    val keys = folder.keys.filterNot { it == key }
    val next = when {
        keys.isEmpty() -> null
        keys.size == 1 -> item.copy(slot = Slot.App(keys.first()))
        else -> item.copy(slot = folder.copy(keys = keys))
    }
    mutate(pageIndex) { items ->
        items.filterNot { it == item } + listOfNotNull(next)
    }
}

fun LayoutStore.resizeWidget(pageIndex: Int, item: Placed, spanX: Int, spanY: Int) {
    if (item.slot !is Slot.Widget) return
    val x = spanX.coerceIn(1, GRID_COLS - item.col)
    val y = spanY.coerceIn(1, GRID_ROWS - item.row)
    val resized = item.copy(spanX = x, spanY = y)
    mutate(pageIndex) { items ->
        items.filterNot { it == item || occupies(it, resized) } + resized
    }
}

/** True when b would land on top of a. */
private fun occupies(a: Placed, b: Placed): Boolean {
    val ax = a.col until (a.col + a.spanX)
    val ay = a.row until (a.row + a.spanY)
    val bx = b.col until (b.col + b.spanX)
    val by = b.row until (b.row + b.spanY)
    return ax.any { it in bx } && ay.any { it in by }
}

/** The item covering a cell, if any. */
fun Page.itemAt(col: Int, row: Int): Placed? = items.firstOrNull {
    col in it.col until (it.col + it.spanX) && row in it.row until (it.row + it.spanY)
}
