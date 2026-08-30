package com.cairn.launcher.notify

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Grants Cairn the message text, the reply intent, and progress for every notification.
 *
 * Access is granted by hand in Settings, Notifications, Device and app notifications. It is
 * revoked on reinstall, so the launcher checks and asks again after an update.
 */
class CairnNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val byPackage = HashMap<String, Notice>()

        active.forEach { sbn ->
            val n = sbn.notification ?: return@forEach
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@forEach
            val extras = n.extras ?: return@forEach
            val pkg = sbn.packageName

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

            val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val cur = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            val level = if (max > 0 && !indeterminate) (cur.toFloat() / max).coerceIn(0f, 1f) else null

            val reply = n.actions?.firstOrNull { a ->
                a.remoteInputs?.any { it.allowFreeFormInput } == true
            }

            val existing = byPackage[pkg]
            byPackage[pkg] = Notice(
                packageName = pkg,
                key = sbn.key,
                count = (existing?.count ?: 0) + 1,
                title = existing?.title ?: title,
                text = existing?.text ?: text,
                level = existing?.level ?: level,
                replyAction = existing?.replyAction ?: reply
            )
        }

        NotificationState.replaceAll(byPackage)
        refreshMedia()
    }

    private fun refreshMedia() {
        val msm = runCatching {
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        }.getOrNull() ?: return
        val component = ComponentName(this, CairnNotificationListener::class.java)
        val sessions = runCatching { msm.getActiveSessions(component) }.getOrNull().orEmpty()

        val map = HashMap<String, Float>()
        sessions.forEach { controller ->
            val state = controller.playbackState ?: return@forEach
            if (state.state != PlaybackState.STATE_PLAYING &&
                state.state != PlaybackState.STATE_PAUSED
            ) return@forEach
            val duration = controller.metadata
                ?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (duration <= 0L) return@forEach
            map[controller.packageName] =
                (state.position.toFloat() / duration).coerceIn(0f, 1f)
        }
        NotificationState.setMedia(map)
    }

    fun dismiss(key: String) = runCatching { cancelNotification(key) }

    fun snooze(key: String, millis: Long) = runCatching { snoozeNotification(key, millis) }

    companion object {
        @Volatile
        var instance: CairnNotificationListener? = null

        /** Cheap check for whether access is currently granted. */
        fun isConnected() = instance != null
    }
}
