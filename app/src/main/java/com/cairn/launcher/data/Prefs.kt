package com.cairn.launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Everything about Cairn that is a choice rather than a decision.
 *
 * The grid size lives here rather than as a compile-time constant, which means every place that
 * used to read GRID_COLS has to be handed the real value instead. That is the whole cost of
 * making the grid adjustable, and it is worth paying once.
 */
data class Prefs(
    val cols: Int = 4,
    val rows: Int = 5,
    val iconDp: Int = 48,
    val rowHeightDp: Int = 78,
    val showLabels: Boolean = true,
    val showDock: Boolean = true,
    val dockCount: Int = 5,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    /** Captions swap to the sender's name when something is waiting. */
    val notificationCaptions: Boolean = true,
    /** The rule under an icon whose length is a media position or a download. */
    val levelRules: Boolean = true,
    /** The home screen recedes slightly as the drawer comes up. */
    val dimOnDrawer: Boolean = true,
    /** How far the drawer travels per unit of finger movement. */
    val drawerSensitivity: Float = 2.2f
) {
    fun clamped() = copy(
        cols = cols.coerceIn(3, 6),
        rows = rows.coerceIn(3, 8),
        iconDp = iconDp.coerceIn(36, 72),
        rowHeightDp = rowHeightDp.coerceIn(56, 120),
        dockCount = dockCount.coerceIn(0, 6),
        drawerSensitivity = drawerSensitivity.coerceIn(1f, 4f)
    )
}

class PrefsStore(context: Context) {

    private val file = File(context.filesDir, "prefs.json")
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> = _prefs

    fun load() {
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        val d = Prefs()
        _prefs.value = Prefs(
            cols = o.optInt("cols", d.cols),
            rows = o.optInt("rows", d.rows),
            iconDp = o.optInt("iconDp", d.iconDp),
            rowHeightDp = o.optInt("rowHeightDp", d.rowHeightDp),
            showLabels = o.optBoolean("showLabels", d.showLabels),
            showDock = o.optBoolean("showDock", d.showDock),
            dockCount = o.optInt("dockCount", d.dockCount),
            showClock = o.optBoolean("showClock", d.showClock),
            showDate = o.optBoolean("showDate", d.showDate),
            notificationCaptions = o.optBoolean("notificationCaptions", d.notificationCaptions),
            levelRules = o.optBoolean("levelRules", d.levelRules),
            dimOnDrawer = o.optBoolean("dimOnDrawer", d.dimOnDrawer),
            drawerSensitivity = o.optDouble("drawerSensitivity", d.drawerSensitivity.toDouble())
                .toFloat()
        ).clamped()
    }

    fun update(block: (Prefs) -> Prefs) {
        val next = block(_prefs.value).clamped()
        _prefs.value = next
        scope.launch {
            val o = JSONObject()
                .put("cols", next.cols)
                .put("rows", next.rows)
                .put("iconDp", next.iconDp)
                .put("rowHeightDp", next.rowHeightDp)
                .put("showLabels", next.showLabels)
                .put("showDock", next.showDock)
                .put("dockCount", next.dockCount)
                .put("showClock", next.showClock)
                .put("showDate", next.showDate)
                .put("notificationCaptions", next.notificationCaptions)
                .put("levelRules", next.levelRules)
                .put("dimOnDrawer", next.dimOnDrawer)
                .put("drawerSensitivity", next.drawerSensitivity.toDouble())
            runCatching { file.writeText(o.toString(2)) }
        }
    }

    fun resetToDefaults() = update { Prefs() }
}
