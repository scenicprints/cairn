package com.cairn.launcher.data

import org.json.JSONArray
import org.json.JSONObject

const val GRID_COLS = 4
const val GRID_ROWS = 5

sealed interface Slot {
    data class App(val key: String) : Slot
    data class Folder(val name: String, val keys: List<String>) : Slot
    data class Widget(val widgetId: Int) : Slot
}

/** A slot placed on a page. Icons are 1x1; widgets can span. */
data class Placed(
    val col: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int,
    val slot: Slot
)

data class Page(val items: List<Placed>)

data class Layout(
    val pages: List<Page>,
    val dock: List<Slot>,
    val homePage: Int
) {
    fun page(i: Int): Page? = pages.getOrNull(i)
}

/* ---------- JSON ---------- */

private fun Slot.toJson(): JSONObject = when (this) {
    is Slot.App -> JSONObject().put("type", "app").put("key", key)
    is Slot.Folder -> JSONObject().put("type", "folder").put("name", name)
        .put("keys", JSONArray(keys))
    is Slot.Widget -> JSONObject().put("type", "widget").put("id", widgetId)
}

private fun slotFromJson(o: JSONObject): Slot? = when (o.optString("type")) {
    "app" -> Slot.App(o.optString("key"))
    "folder" -> {
        val arr = o.optJSONArray("keys") ?: JSONArray()
        Slot.Folder(o.optString("name"), (0 until arr.length()).map { arr.getString(it) })
    }
    "widget" -> Slot.Widget(o.optInt("id", -1)).takeIf { it.widgetId >= 0 }
    else -> null
}

fun Layout.toJson(): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("homePage", homePage)
    root.put("dock", JSONArray().also { arr -> dock.forEach { arr.put(it.toJson()) } })
    val pagesArr = JSONArray()
    pages.forEach { page ->
        val items = JSONArray()
        page.items.forEach { p ->
            items.put(
                JSONObject()
                    .put("col", p.col).put("row", p.row)
                    .put("spanX", p.spanX).put("spanY", p.spanY)
                    .put("slot", p.slot.toJson())
            )
        }
        pagesArr.put(JSONObject().put("items", items))
    }
    root.put("pages", pagesArr)
    return root.toString(2)
}

fun layoutFromJson(text: String): Layout? = runCatching {
    val root = JSONObject(text)
    val dockArr = root.optJSONArray("dock") ?: JSONArray()
    val dock = (0 until dockArr.length()).mapNotNull { slotFromJson(dockArr.getJSONObject(it)) }
    val pagesArr = root.optJSONArray("pages") ?: JSONArray()
    val pages = (0 until pagesArr.length()).map { pi ->
        val itemsArr = pagesArr.getJSONObject(pi).optJSONArray("items") ?: JSONArray()
        Page((0 until itemsArr.length()).mapNotNull { ii ->
            val o = itemsArr.getJSONObject(ii)
            val slot = slotFromJson(o.getJSONObject("slot")) ?: return@mapNotNull null
            Placed(
                col = o.optInt("col"),
                row = o.optInt("row"),
                spanX = o.optInt("spanX", 1).coerceAtLeast(1),
                spanY = o.optInt("spanY", 1).coerceAtLeast(1),
                slot = slot
            )
        })
    }
    Layout(
        pages = pages.ifEmpty { listOf(Page(emptyList())) },
        dock = dock,
        homePage = root.optInt("homePage", 0)
    )
}.getOrNull()
