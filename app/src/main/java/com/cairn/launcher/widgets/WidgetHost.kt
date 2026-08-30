package com.cairn.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle

const val WIDGET_HOST_ID = 0x43414952
const val REQ_PICK_WIDGET = 9001
const val REQ_BIND_WIDGET = 9002
const val REQ_CONFIGURE_WIDGET = 9003

/**
 * Hosting real widgets is the expensive half of a launcher, and it is not optional here: page one
 * is a Google Calendar widget.
 *
 * Android will not let an app bind a widget silently. [requestBind] raises the system dialog, and
 * the id is only usable once the user has said yes.
 */
class CairnWidgetHost(context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context.applicationContext, WIDGET_HOST_ID)

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun info(id: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    /** Opens the system widget picker. The result arrives in onActivityResult. */
    fun pick(activity: Activity) {
        val id = allocateId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())
        }
        activity.startActivityForResult(intent, REQ_PICK_WIDGET)
    }

    /** Raises the system's bind-permission dialog for a provider we did not pick through the picker. */
    fun requestBind(activity: Activity, id: Int, provider: android.content.ComponentName) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        activity.startActivityForResult(intent, REQ_BIND_WIDGET)
    }

    /** Some widgets insist on a configuration activity before they will render anything. */
    fun configureIfNeeded(activity: Activity, id: Int): Boolean {
        val info = info(id) ?: return false
        val configure = info.configure ?: return false
        return runCatching {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            activity.startActivityForResult(intent, REQ_CONFIGURE_WIDGET)
            true
        }.getOrDefault(false)
    }

    /**
     * Tells the widget what box it now lives in, in dp, so it re-lays itself out instead of
     * being scaled. This is the difference between a resizable widget that looks right and one
     * that looks stretched.
     */
    fun resize(id: Int, minWidthDp: Int, minHeightDp: Int, maxWidthDp: Int, maxHeightDp: Int) {
        runCatching {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeightDp)
            }
            manager.updateAppWidgetOptions(id, options)
        }
    }

    /** Grid cells a provider needs, so a dropped widget lands at a sensible size. */
    fun defaultSpan(id: Int, cellWidthDp: Int, cellHeightDp: Int): Pair<Int, Int> {
        val info = info(id) ?: return 2 to 2
        val w = (info.minWidth + cellWidthDp - 1) / cellWidthDp
        val h = (info.minHeight + cellHeightDp - 1) / cellHeightDp
        return w.coerceIn(1, 4) to h.coerceIn(1, 5)
    }
}
